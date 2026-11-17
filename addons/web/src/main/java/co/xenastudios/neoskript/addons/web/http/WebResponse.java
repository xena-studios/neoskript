/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.http;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An answer, read in full.
 *
 * @param status the status code
 * @param uri where it came from, after redirects
 * @param headers its headers, names in lower case, sorted by name, each with its values in the order sent
 * @param body its body, unpacked if it was compressed
 */
public record WebResponse(int status, URI uri, Map<String, List<String>> headers, byte[] body) {

    /**
     * The first value of a header.
     *
     * @param name its name, in any case
     * @return its value, or {@code null}
     */
    public String header(String name) {
        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    /**
     * The charset the {@code Content-Type} header names, or UTF-8.
     *
     * @return the charset
     */
    public Charset charset() {
        String type = header("content-type");
        if (type != null) {
            for (String part : type.split(";")) {
                String[] pair = part.strip().split("=", 2);
                if (pair.length == 2 && pair[0].strip().equalsIgnoreCase("charset")) {
                    try {
                        return Charset.forName(pair[1].strip().replace("\"", ""));
                    } catch (IllegalCharsetNameException | UnsupportedCharsetException unknown) {
                        return StandardCharsets.UTF_8;
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
