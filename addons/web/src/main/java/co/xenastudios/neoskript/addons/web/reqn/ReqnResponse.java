/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.reqn;

import co.xenastudios.neoskript.addons.web.http.Reasons;
import co.xenastudios.neoskript.addons.web.http.WebResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reqn's {@code httpresponse}: the status, the headers (each name's first value) and the body as text, as Reqn
 * 1.2.3 kept them.
 *
 * @param code the status code
 * @param message the reason phrase, or {@code null}
 * @param statusLine the status line, {@code HTTP/1.1 200 OK}
 * @param headers each header's first value, by name
 * @param body the body, each line ending in a line break, as Reqn read it
 */
public record ReqnResponse(int code, String message, String statusLine, Map<String, String> headers, String body) {

    /**
     * Reqn's view of an answer. The body is read as UTF-8, line by line, each line ending in {@code \n}, as Reqn's
     * {@code readLine} loop did. Header names are written in the usual way ({@code Content-Type}), as the JDK's
     * client doesn't keep the server's; Reqn's map, a {@link HashMap}, is kept, so they list in the same order.
     *
     * @param response the answer
     * @return Reqn's response
     */
    public static ReqnResponse of(WebResponse response) {
        String phrase = Reasons.phrase(response.status());
        String statusLine = "HTTP/1.1 " + response.status() + (phrase == null ? "" : " " + phrase);
        Map<String, String> headers = new HashMap<>();
        response.headers().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(capitalised(name), values.getFirst());
            }
        });
        return new ReqnResponse(response.status(), phrase, statusLine, headers, lines(response.text(StandardCharsets.UTF_8)));
    }

    /** {@code content-type} as {@code Content-Type}. */
    static String capitalised(String name) {
        StringBuilder out = new StringBuilder(name.length());
        boolean start = true;
        for (char c : name.toCharArray()) {
            out.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
            start = c == '-';
        }
        return out.toString();
    }

    private static String lines(String body) {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new StringReader(body))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return out.toString();
    }

    /**
     * A header's value, whatever its name's case.
     *
     * @param name the name
     * @return the value, or {@code null}
     */
    public String header(String name) {
        String exact = headers.get(name);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * The headers as Reqn listed them: {@code Name: value}.
     *
     * @return each header
     */
    public List<String> lines() {
        return headers.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).toList();
    }

    @Override
    public String toString() {
        return statusLine + "\n" + headers.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n", "", "\n\n")) + body;
    }
}
