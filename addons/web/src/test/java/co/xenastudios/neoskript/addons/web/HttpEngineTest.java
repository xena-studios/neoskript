/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.addons.web.http.Addresses;
import co.xenastudios.neoskript.addons.web.http.HttpEngine;
import co.xenastudios.neoskript.addons.web.http.WebException;
import co.xenastudios.neoskript.addons.web.http.WebRequest;
import co.xenastudios.neoskript.addons.web.http.WebResponse;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The engine on its own, against a local server: limits, redirects, the private-address block, compressed answers,
 * and stopping with requests running, without leaving threads behind.
 */
class HttpEngineTest {

    private HttpServer server;
    private String base;
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final List<HttpEngine> engines = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", exchange -> answer(exchange, 200, "ok"));
        server.createContext("/auth", exchange -> {
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            answer(exchange, 200, "seen");
        });
        server.createContext("/to-other-host", exchange -> {
            // 'localhost' is another host name for the same server.
            exchange.getResponseHeaders().set("Location", "http://localhost:" + server.getAddress().getPort() + "/auth");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/to-http", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://example.invalid/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/big", exchange -> answer(exchange, 200, "x".repeat(4096)));
        server.createContext("/hang", exchange -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        engines.forEach(HttpEngine::close);
        server.stop(0);
    }

    private static void answer(com.sun.net.httpserver.HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private HttpEngine engine(WebConfig config) {
        HttpEngine engine = new HttpEngine(config);
        engines.add(engine);
        return engine;
    }

    private static WebConfig config(int timeoutSeconds, long maxBytes, int inFlight, int perMinute, boolean block) {
        return config(timeoutSeconds, maxBytes, inFlight, perMinute, block, 64L * 1024 * 1024);
    }

    private static WebConfig config(int timeoutSeconds, long maxBytes, int inFlight, int perMinute, boolean block,
            long memory) {
        return new WebConfig(Duration.ofSeconds(timeoutSeconds), Duration.ofSeconds(2), maxBytes, 5, inFlight, perMinute,
                block, memory);
    }

    private static WebRequest get(String url) {
        return new WebRequest("GET", url, List.of(), null);
    }

    private static String failure(CompletableFuture<?> future) {
        try {
            future.get(20, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            assertInstanceOf(WebException.class, e.getCause());
            return e.getCause().getMessage();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("It didn't fail");
    }

    @Test
    void answersAreReadInFull() {
        WebResponse response = engine(WebConfig.DEFAULTS).send(get(base + "/ok")).join();
        assertEquals(200, response.status());
        assertEquals("ok", response.text(StandardCharsets.UTF_8));
    }

    @Test
    void aBiggerAnswerThanTheLimitFails() {
        String message = failure(engine(config(5, 1024, 8, 100, false)).send(get(base + "/big")));
        assertTrue(message.contains("bigger than 1 KB"), message);
    }

    @Test
    void aRequestThatTakesTooLongFailsWithTheTimeout() {
        long start = System.nanoTime();
        String message = failure(engine(config(1, 1024, 8, 100, false)).send(get(base + "/hang")));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 5);
        assertTrue(message.contains("1 seconds") || message.contains("within 1 seconds"), message);
    }

    @Test
    void privateAddressesAreRefusedWhenBlocked() {
        String message = failure(engine(config(5, 1024, 8, 100, true)).send(get(base + "/ok")));
        assertTrue(message.contains("private address"), message);
        assertTrue(Addresses.isPrivate(InetAddress.getLoopbackAddress()));
    }

    @Test
    void privateRangesAreRecognised() throws IOException {
        for (String address : List.of("10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1",
                "0.0.0.0", "::1", "fd00::1", "fe80::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1", "64:ff9b::a00:1",
                "64:ff9b::7f00:1", "64:ff9b:1::808:808", "2002:a00:1::", "2002:7f00:1::1", "2002:a9fe:a9fe::")) {
            assertTrue(Addresses.isPrivate(InetAddress.getByName(address)), address);
        }
        for (String address : List.of("1.1.1.1", "8.8.8.8", "172.32.0.1", "2606:4700:4700::1111", "64:ff9b::808:808",
                "2002:808:808::")) {
            assertFalse(Addresses.isPrivate(InetAddress.getByName(address)), address);
        }
    }

    @Test
    void onlyWebAddressesAreSent() {
        HttpEngine engine = engine(WebConfig.DEFAULTS);
        assertTrue(failure(engine.send(get("file:///etc/passwd"))).contains("http:// or https://"));
        assertTrue(failure(engine.send(get("not a url"))).contains("isn't a valid web address"));
        assertTrue(failure(engine.send(get("http://"))).contains("valid web address")
                || failure(engine.send(get("http://"))).contains("no host"));
    }

    @Test
    void theRateLimitRefusesRequestsPastIt() {
        HttpEngine engine = engine(config(5, 1024, 8, 3, false));
        for (int i = 0; i < 3; i++) {
            engine.send(get(base + "/ok")).join();
        }
        assertTrue(failure(engine.send(get(base + "/ok"))).contains("max-requests-per-minute"));
    }

    @Test
    void theInFlightLimitRefusesRequestsPastIt() {
        HttpEngine engine = engine(config(5, 1024, 2, 100, false));
        CompletableFuture<WebResponse> first = engine.send(get(base + "/hang"));
        CompletableFuture<WebResponse> second = engine.send(get(base + "/hang"));
        assertTrue(failure(engine.send(get(base + "/ok"))).contains("max-requests-in-flight"));
        assertEquals(2, engine.inFlight());
        first.cancel(true);
        second.cancel(true);
    }

    @Test
    void credentialsArentSentOnToAnotherHost() {
        HttpEngine engine = engine(WebConfig.DEFAULTS);
        engine.send(new WebRequest("GET", base + "/auth", List.of(Map.entry("Authorization", "secret")), null)).join();
        assertEquals("secret", lastAuthorization.get());
        engine.send(new WebRequest("GET", base + "/to-other-host", List.of(Map.entry("Authorization", "secret")), null))
                .join();
        assertEquals(null, lastAuthorization.get());
    }

    @Test
    void closingFailsWhatsRunningAndStopsItsThreads() throws InterruptedException {
        HttpEngine engine = new HttpEngine(config(30, 1024, 8, 100, false));
        CompletableFuture<WebResponse> running = engine.send(get(base + "/hang"));
        Thread.sleep(200);
        engine.close();
        CompletionException failed = org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class,
                running::join);
        assertTrue(failed.getCause().getMessage().contains("stopped"), failed.getCause().getMessage());
        assertTrue(failure(engine.send(get(base + "/ok"))).contains("has stopped"));
        Thread.sleep(500);
        assertTrue(Thread.getAllStackTraces().keySet().stream()
                .noneMatch(t -> t.isAlive() && t.getName().startsWith("NeoSkriptWeb-")),
                "the engine's threads, its timeouts' included, stopped");
    }

    @Test
    void answersInFlightShareABudget() {
        HttpEngine engine = engine(config(5, 8192, 8, 100, false, 2048));
        String message = failure(engine.send(get(base + "/big")));
        assertTrue(message.contains("max-response-memory-megabytes"), message);
        assertReleased(engine, "what it held is released");
        HttpEngine roomy = engine(config(5, 8192, 8, 100, false, 1024 * 1024));
        assertEquals(4096, roomy.send(get(base + "/big")).join().length());
        assertReleased(roomy, "what it held is released once it's handed over");
    }

    /** The budget is given back as the request finishes, which can be a moment after its result is handed over. */
    private static void assertReleased(HttpEngine engine, String message) {
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (engine.bufferedBytes() != 0 && System.nanoTime() < until) {
            Thread.onSpinWait();
        }
        assertEquals(0, engine.bufferedBytes(), message);
    }

    @Test
    void credentialsArentSentOnToAnotherPort() throws IOException {
        HttpServer other = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        AtomicReference<String> seen = new AtomicReference<>("unset");
        other.createContext("/auth", exchange -> {
            seen.set(exchange.getRequestHeaders().getFirst("Authorization"));
            answer(exchange, 200, "seen");
        });
        other.start();
        server.createContext("/to-other-port", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + other.getAddress().getPort() + "/auth");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        try {
            engine(WebConfig.DEFAULTS).send(new WebRequest("GET", base + "/to-other-port",
                    List.of(Map.entry("Authorization", "secret")), null)).join();
            assertEquals(null, seen.get());
        } finally {
            other.stop(0);
        }
    }

    @Test
    void aRequestTheInFlightLimitRefusesDoesntUseUpTheMinute() {
        HttpEngine engine = engine(config(5, 1024, 1, 2, false));
        CompletableFuture<WebResponse> hanging = engine.send(get(base + "/hang"));
        for (int i = 0; i < 5; i++) {
            assertTrue(failure(engine.send(get(base + "/ok"))).contains("max-requests-in-flight"));
        }
        hanging.cancel(true);
        assertEquals(200, engine.send(get(base + "/ok")).join().status());
    }
}
