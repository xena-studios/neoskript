/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The RESP client and the Redis link, against a small Redis of the test's own: PING, PUBLISH, PSUBSCRIBE. */
class RedisTest {

    /** Just enough Redis: PING, AUTH, SELECT, PUBLISH to every PSUBSCRIBE *. */
    static final class FakeRedis implements AutoCloseable {
        final ServerSocket server;
        final List<OutputStream> subscribers = new CopyOnWriteArrayList<>();
        final List<Socket> sockets = new CopyOnWriteArrayList<>();
        /** What was published to it: channel and message. */
        final List<String> published = new CopyOnWriteArrayList<>();
        final Thread thread;

        FakeRedis(int port) throws IOException {
            server = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
            thread = new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket socket = server.accept();
                        sockets.add(socket);
                        Thread.ofVirtual().start(() -> serve(socket));
                    } catch (IOException closed) {
                        return;
                    }
                }
            });
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        private void serve(Socket socket) {
            try (socket) {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                OutputStream out = socket.getOutputStream();
                while (true) {
                    String header = in.readLine();
                    if (header == null) {
                        return;
                    }
                    int count = Integer.parseInt(header.substring(1));
                    List<String> args = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        in.readLine();
                        args.add(in.readLine());
                    }
                    String command = args.getFirst().toUpperCase();
                    switch (command) {
                        case "PING" -> write(out, "+PONG\r\n");
                        case "AUTH", "SELECT" -> write(out, "+OK\r\n");
                        case "PSUBSCRIBE" -> {
                            subscribers.add(out);
                            write(out, "*3\r\n$10\r\npsubscribe\r\n$1\r\n*\r\n:1\r\n");
                        }
                        case "PUBLISH" -> {
                            published.add(args.get(1) + " " + args.get(2));
                            String message = "*4\r\n$8\r\npmessage\r\n$1\r\n*\r\n" + bulk(args.get(1)) + bulk(args.get(2));
                            for (OutputStream subscriber : subscribers) {
                                write(subscriber, message);
                            }
                            write(out, ":" + subscribers.size() + "\r\n");
                        }
                        default -> write(out, "-ERR unknown\r\n");
                    }
                }
            } catch (IOException | RuntimeException gone) {
                // The client left.
            }
        }

        private static String bulk(String text) {
            return "$" + text.getBytes(StandardCharsets.UTF_8).length + "\r\n" + text + "\r\n";
        }

        private static void write(OutputStream out, String text) throws IOException {
            synchronized (out) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }

        @Override
        public void close() throws IOException {
            server.close();
            for (Socket socket : sockets) {
                socket.close();
            }
            subscribers.clear();
        }
    }

    private final List<AutoCloseable> closing = new ArrayList<>();

    @AfterEach
    void stop() throws Exception {
        for (AutoCloseable c : closing) {
            c.close();
        }
    }

    @Test
    void repliesAreReadAndBounded() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread.ofVirtual().start(() -> {
                try (Socket socket = server.accept()) {
                    socket.getOutputStream().write(("+OK\r\n:42\r\n$5\r\nhello\r\n$-1\r\n*2\r\n$1\r\na\r\n:1\r\n"
                            + "-ERR wrong\r\n$99999999\r\n").getBytes(StandardCharsets.UTF_8));
                    Thread.sleep(2_000);
                } catch (IOException | InterruptedException ignored) {
                    // Done.
                }
            });
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
                    Resp resp = Resp.over(socket)) {
                assertEquals("OK", resp.read());
                assertEquals(42L, resp.read());
                assertEquals("hello", resp.read());
                assertEquals(null, resp.read());
                assertEquals(List.of("a", 1L), resp.read());
                assertEquals("ERR wrong", assertThrows(Resp.Error.class, resp::read).getMessage());
                assertTrue(assertThrows(IOException.class, resp::read).getMessage().contains("more than"));
            }
        }
    }

    private static Settings settings(int port) {
        Map<String, Object> values = new HashMap<>();
        values.put("redis.enabled", true);
        values.put("redis.host", "127.0.0.1");
        values.put("redis.port", port);
        values.put("redis.timeout", 1000);
        return Settings.read(values::get, new ArrayList<>());
    }

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "timed out");
    }

    @Test
    void messagesGoRoundAndWaitThroughAnOutage() throws Exception {
        FakeRedis redis = new FakeRedis(0);
        int port = redis.port();
        List<String> got = Collections.synchronizedList(new ArrayList<>());
        RedisLink link = new RedisLink(settings(port), Logger.getLogger("test"), (channel, text) -> got.add(channel + " " + text));
        closing.add(() -> link.close(0));
        link.start();
        await(link::up);
        link.publish("events", "one");
        await(() -> got.contains("events one"));
        // Redis goes away: what's published waits, and reaches Redis once it's back on the same port. (Whether a
        // subscriber hears it depends on who reconnects first: pub/sub keeps nothing for late listeners.)
        redis.close();
        await(() -> !link.up());
        link.publish("events", "two");
        FakeRedis back = new FakeRedis(port);
        closing.add(back);
        await(link::up);
        await(() -> back.published.contains("events two"));
        link.close(1000);
        assertEquals(0, link.queued());
    }

    @Test
    void closingEndsItsThreads() throws Exception {
        FakeRedis redis = new FakeRedis(0);
        closing.add(redis);
        RedisLink link = new RedisLink(settings(redis.port()), Logger.getLogger("test"), (channel, text) -> {});
        link.start();
        await(link::up);
        link.close(500);
        await(() -> Thread.getAllStackTraces().keySet().stream()
                .noneMatch(thread -> thread.isAlive() && thread.getName().startsWith("NeoSkriptProxy-redis")));
    }
}
