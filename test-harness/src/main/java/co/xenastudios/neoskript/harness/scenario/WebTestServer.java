/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

/**
 * A small HTTP/1.1 server for the web addon's scenario, on 127.0.0.1 and a free port. It's written on sockets, not
 * the JDK's {@code HttpServer}, so its answers are exactly what's written here, the same every time: header names as
 * servers usually write them ({@code Content-Type}), a fixed {@code Date}, the usual reason phrases, keep-alive, and
 * bodies that are slow or never end. Its paths:
 *
 * <ul>
 *   <li>{@code /json}: a JSON object with text, numbers, a boolean, a null, a list and a nested object;</li>
 *   <li>{@code /text}: two lines of text, the second without a line break at the end;</li>
 *   <li>{@code /status/<code>}: that status, with {@code status <code>} as its body;</li>
 *   <li>{@code /echo}: the method, body, query and {@code X-Test} and {@code Content-Type} headers it got, as JSON;</li>
 *   <li>{@code /redirect}: a 302 to {@code /text}; {@code /see-other}: a 303 to {@code /echo}; {@code /loop}: a
 *       redirect to itself;</li>
 *   <li>{@code /trickle}: headers, then a byte a second, forever;</li>
 *   <li>{@code /huge}: a body that never ends;</li>
 *   <li>{@code /bomb}: a small gzip body that unpacks to 64 MB;</li>
 *   <li>{@code /bad-json}: {@code {"broken": } as JSON; {@code /deep}: JSON nested 100,000 deep;</li>
 *   <li>{@code /gzip}: a gzip-compressed body, said so in {@code Content-Encoding};</li>
 *   <li>{@code /headers}: the extra header {@code X-Answer: 42}.</li>
 * </ul>
 *
 * <p>It counts its open connections, so the scenario can check none is left over after a client gives up. A second
 * port takes connections and never answers.
 */
final class WebTestServer implements AutoCloseable {

    private static final String DATE = "Tue, 17 Nov 2026 00:00:00 GMT";
    private static final Map<Integer, String> REASONS = Map.of(200, "OK", 302, "Found", 303, "See Other",
            404, "Not Found", 500, "Internal Server Error", 418, "I'm a teapot", 201, "Created", 204, "No Content");

    private final ServerSocket server;
    private final ServerSocket silent;
    private final Set<Socket> open = ConcurrentHashMap.newKeySet();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile boolean closed;

    WebTestServer() throws IOException {
        server = new ServerSocket(0, 128, InetAddress.getLoopbackAddress());
        silent = new ServerSocket(0, 128, InetAddress.getLoopbackAddress());
        accept(server, this::serve, "web-test-server");
        accept(silent, socket -> {
            try (InputStream in = socket.getInputStream()) {
                in.transferTo(OutputStream.nullOutputStream());
            }
        }, "web-test-silent");
    }

    private interface Handler {
        void handle(Socket socket) throws IOException;
    }

    private void accept(ServerSocket listening, Handler handler, String name) {
        Thread acceptor = new Thread(() -> {
            while (!closed) {
                Socket socket;
                try {
                    socket = listening.accept();
                } catch (IOException stopped) {
                    return;
                }
                open.add(socket);
                Thread worker = new Thread(() -> {
                    try (socket) {
                        handler.handle(socket);
                    } catch (IOException | RuntimeException gone) {
                        // The client went away.
                    } finally {
                        open.remove(socket);
                    }
                }, name + "-connection");
                worker.setDaemon(true);
                worker.start();
            }
        }, name);
        acceptor.setDaemon(true);
        acceptor.start();
    }

    int port() {
        return server.getLocalPort();
    }

    int silentPort() {
        return silent.getLocalPort();
    }

    /** How many requests it has answered or started to. */
    int requests() {
        return requests.get();
    }

    /** How many connections are open, on both ports. */
    int openConnections() {
        return open.size();
    }

    /** Serves requests on one connection until the client closes it. */
    private void serve(Socket socket) throws IOException {
        InputStream in = new BufferedInputStream(socket.getInputStream());
        OutputStream out = socket.getOutputStream();
        while (true) {
            String requestLine = line(in);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            requests.incrementAndGet();
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String target = parts.length > 1 ? parts[1] : "/";
            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            String header;
            while ((header = line(in)) != null && !header.isEmpty()) {
                int colon = header.indexOf(':');
                if (colon > 0) {
                    headers.put(header.substring(0, colon).trim(), header.substring(colon + 1).trim());
                }
            }
            int length = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
            byte[] body = in.readNBytes(length);
            int question = target.indexOf('?');
            String path = question < 0 ? target : target.substring(0, question);
            String query = question < 0 ? "" : target.substring(question + 1);
            if (!answer(out, method, path, query, headers, body)) {
                return;
            }
        }
    }

    /** Answers one request; {@code false} if the connection is done. */
    private boolean answer(OutputStream out, String method, String path, String query, Map<String, String> headers,
            byte[] body) throws IOException {
        switch (path) {
            case "/json" -> send(out, method, 200, "application/json", Map.of(),
                    "{\"name\":\"Steve\",\"level\":5,\"ratio\":1.5,\"admin\":false,\"none\":null,"
                            + "\"tags\":[\"a\",\"b\",3],\"home\":{\"world\":\"world\",\"x\":10,\"y\":64}}");
            case "/text" -> send(out, method, 200, "text/plain", Map.of(), "hello\r\nworld");
            case "/echo" -> {
                Map<String, String> fields = new TreeMap<>();
                fields.put("method", method);
                fields.put("body", new String(body, StandardCharsets.UTF_8));
                fields.put("query", URLDecoder.decode(query, StandardCharsets.UTF_8));
                fields.put("x-test", headers.getOrDefault("X-Test", ""));
                fields.put("content-type", headers.getOrDefault("Content-Type", ""));
                StringBuilder json = new StringBuilder("{");
                fields.forEach((key, value) -> json.append(json.length() > 1 ? "," : "").append('"').append(key)
                        .append("\":\"").append(escape(value)).append('"'));
                send(out, method, 200, "application/json", Map.of(), json.append('}').toString());
            }
            case "/redirect" -> send(out, method, 302, "text/plain", Map.of("Location", "/text"), "");
            case "/see-other" -> send(out, method, 303, "text/plain", Map.of("Location", "/echo"), "");
            case "/loop" -> send(out, method, 302, "text/plain", Map.of("Location", "/loop"), "");
            case "/bad-json" -> send(out, method, 200, "application/json", Map.of(), "{\"broken\": ");
            case "/deep" -> send(out, method, 200, "application/json", Map.of(),
                    "[".repeat(100_000) + "]".repeat(100_000));
            case "/headers" -> send(out, method, 200, "text/plain", Map.of("X-Answer", "42"), "with headers");
            case "/gzip" -> sendBytes(out, 200, "text/plain", Map.of("Content-Encoding", "gzip"),
                    gzip("compressed text".getBytes(StandardCharsets.UTF_8)));
            case "/bomb" -> sendBytes(out, 200, "text/plain", Map.of("Content-Encoding", "gzip"),
                    gzip(new byte[64 * 1024 * 1024]));
            case "/trickle" -> {
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nDate: " + DATE
                        + "\r\nTransfer-Encoding: chunked\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                while (!closed) {
                    out.write("1\r\nx\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    sleep(1000);
                }
                return false;
            }
            case "/huge" -> {
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nDate: " + DATE
                        + "\r\nTransfer-Encoding: chunked\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                byte[] chunk = ("10000\r\n" + "0123456789abcdef".repeat(4096) + "\r\n").getBytes(StandardCharsets.US_ASCII);
                while (!closed) {
                    out.write(chunk);
                }
                return false;
            }
            default -> {
                if (path.startsWith("/status/")) {
                    int code = Integer.parseInt(path.substring("/status/".length()));
                    send(out, method, code, "text/plain", Map.of(), "status " + code);
                } else {
                    send(out, method, 404, "text/plain", Map.of(), "not found");
                }
            }
        }
        return true;
    }

    private static void send(OutputStream out, String method, int code, String type, Map<String, String> extra,
            String body) throws IOException {
        // 204 and 304 have no body, and HEAD gets the headers only.
        boolean bodiless = code == 204 || code == 304;
        String content = bodiless ? "" : body;
        byte[] bytes = method.equals("HEAD") ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>(extra);
        if (!bodiless) {
            headers.put("Content-Length", Integer.toString(content.getBytes(StandardCharsets.UTF_8).length));
        }
        write(out, code, type, headers, bytes);
    }

    private static void sendBytes(OutputStream out, int code, String type, Map<String, String> extra, byte[] body)
            throws IOException {
        Map<String, String> headers = new LinkedHashMap<>(extra);
        headers.put("Content-Length", Integer.toString(body.length));
        write(out, code, type, headers, body);
    }

    private static void write(OutputStream out, int code, String type, Map<String, String> headers, byte[] body)
            throws IOException {
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(code).append(' ')
                .append(REASONS.getOrDefault(code, "Unknown")).append("\r\n");
        head.append("Content-Type: ").append(type).append("; charset=utf-8\r\n");
        head.append("Date: ").append(DATE).append("\r\n");
        headers.forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static byte[] gzip(byte[] content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(content);
        }
        return bytes.toByteArray();
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** A line, without its line break, or {@code null} at the end. */
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                byte[] bytes = line.toByteArray();
                int end = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, end, StandardCharsets.ISO_8859_1);
            }
            line.write(c);
            if (line.size() > 65_536) {
                throw new IOException("Line too long");
            }
        }
        return line.size() == 0 ? null : line.toString(StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            server.close();
            silent.close();
        } catch (IOException ignored) {
            // Closing.
        }
        for (Socket socket : open) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Closing.
            }
        }
    }
}
