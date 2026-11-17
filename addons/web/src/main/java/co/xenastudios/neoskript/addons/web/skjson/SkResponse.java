/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.skjson;

import com.google.gson.JsonElement;

/**
 * skJson's {@code response}: what a request got.
 *
 * @param statusCode the status code, or {@code null} if it got nothing
 * @param content the body: JSON if it parses, else text; {@code null} if it got nothing
 * @param headers the headers as JSON, each name (in lower case) with a list of its values; {@code null} if it got
 *     nothing
 * @param status {@code OK} for a status below 500, else {@code FAILED}; {@code UNKNOWN} if it got nothing
 */
public record SkResponse(Integer statusCode, Object content, JsonElement headers, RequestStatus status) {

    /** What a request has before it's sent, and after it fails. */
    public static final SkResponse EMPTY = new SkResponse(null, null, null, RequestStatus.UNKNOWN);

    @Override
    public String toString() {
        return "Response{statusCode=" + statusCode + ", content=" + shorten(content) + ", headers=" + shorten(headers)
                + ", status=" + status + '}';
    }

    private static String shorten(Object value) {
        if (value == null) {
            return "null";
        }
        String text = value.toString();
        return text.length() > 8 ? text.substring(0, 8) + "..." : text;
    }
}
