/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.proxy;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLSocketFactory;

/**
 * One connection to Redis, speaking RESP2: a command is sent as an array of bulk strings, and a reply is read as a
 * {@code String} (simple or bulk), a {@code Long}, {@code null} or a {@code List} of those. Every reply is bounded: a
 * line, a bulk string and an array each have a most, past which the connection is dropped rather than the server
 * running out of memory. Not thread-safe: one thread uses a connection at a time.
 */
final class Resp implements Closeable {

    /** The longest bulk string read: a message, or a value. */
    static final int MAX_BULK = 4 * 1024 * 1024;
    private static final int MAX_LINE = 64 * 1024;
    private static final int MAX_ARRAY = 1_000_000;

    /** An error reply from Redis: {@code -ERR ...}, {@code -NOAUTH ...}. */
    static final class Error extends IOException {
        Error(String message) {
            super(message);
        }
    }

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    private Resp(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedInputStream(socket.getInputStream(), 16 * 1024);
        this.out = socket.getOutputStream();
    }

    /**
     * Connects, authenticates and selects the database.
     *
     * @param timeoutMillis how long connecting and each reply may take; 0 for replies with no limit
     */
    static Resp connect(String host, int port, boolean ssl, int connectMillis, int timeoutMillis, String user,
            String password, int database) throws IOException {
        Socket socket = ssl ? SSLSocketFactory.getDefault().createSocket() : new Socket();
        if (socket instanceof javax.net.ssl.SSLSocket tls) {
            // The certificate has to be for the host, as a browser checks it, and the host is named (SNI).
            javax.net.ssl.SSLParameters parameters = tls.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            if (!host.isEmpty() && !Character.isDigit(host.charAt(host.length() - 1)) && !host.contains(":")) {
                parameters.setServerNames(List.of(new javax.net.ssl.SNIHostName(host)));
            }
            tls.setSSLParameters(parameters);
        }
        try {
            socket.connect(new InetSocketAddress(host, port), connectMillis);
            socket.setSoTimeout(timeoutMillis);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            Resp resp = new Resp(socket);
            if (!password.isEmpty()) {
                if (user.isEmpty()) {
                    resp.call("AUTH", password);
                } else {
                    resp.call("AUTH", user, password);
                }
            }
            if (database != 0) {
                resp.call("SELECT", Integer.toString(database));
            }
            return resp;
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    /** Wraps a socket, for tests. */
    static Resp over(Socket socket) throws IOException {
        return new Resp(socket);
    }

    /** Sends a command and reads its reply. */
    Object call(String... arguments) throws IOException {
        send(arguments);
        return read();
    }

    /** Sends a command. */
    void send(String... arguments) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
        bytes.writeBytes(("*" + arguments.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
        for (String argument : arguments) {
            byte[] data = argument.getBytes(StandardCharsets.UTF_8);
            bytes.writeBytes(("$" + data.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
            bytes.writeBytes(data);
            bytes.writeBytes(new byte[] {'\r', '\n'});
        }
        synchronized (out) {
            out.write(bytes.toByteArray());
            out.flush();
        }
    }

    /** Reads one reply. */
    Object read() throws IOException {
        int type = in.read();
        if (type < 0) {
            throw new EOFException("Redis closed the connection");
        }
        String line = line();
        return switch (type) {
            case '+' -> line;
            case '-' -> throw new Error(line);
            case ':' -> parseLong(line);
            case '$' -> bulk(parseLong(line));
            case '*' -> array(parseLong(line));
            default -> throw new IOException("Redis sent something that isn't RESP: " + (char) type);
        };
    }

    private String bulk(long length) throws IOException {
        if (length < 0) {
            return null;
        }
        if (length > MAX_BULK) {
            throw new IOException("Redis sent " + length + " bytes, more than " + MAX_BULK);
        }
        byte[] data = in.readNBytes((int) length);
        if (data.length < length || in.read() != '\r' || in.read() != '\n') {
            throw new EOFException("Redis's reply was cut short");
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private List<Object> array(long length) throws IOException {
        if (length < 0) {
            return null;
        }
        if (length > MAX_ARRAY) {
            throw new IOException("Redis sent " + length + " values, more than " + MAX_ARRAY);
        }
        List<Object> values = new ArrayList<>((int) Math.min(length, 1024));
        for (long i = 0; i < length; i++) {
            values.add(read());
        }
        return values;
    }

    private String line() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(32);
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new EOFException("Redis's reply was cut short");
            }
            if (b == '\r') {
                if (in.read() != '\n') {
                    throw new IOException("Redis sent a line without its end");
                }
                return line.toString(StandardCharsets.UTF_8);
            }
            if (line.size() >= MAX_LINE) {
                throw new IOException("Redis sent a line longer than " + MAX_LINE + " bytes");
            }
            line.write(b);
        }
    }

    private static long parseLong(String line) throws IOException {
        try {
            return Long.parseLong(line);
        } catch (NumberFormatException e) {
            throw new IOException("Redis sent a number that isn't one: " + line);
        }
    }

    /** Sets how long a read may wait. */
    void timeout(int millis) throws IOException {
        socket.setSoTimeout(millis);
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closed.
        }
    }
}
