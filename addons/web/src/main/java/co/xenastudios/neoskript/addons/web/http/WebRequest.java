/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.http;

import java.util.List;
import java.util.Map;

/**
 * A request to send.
 *
 * @param method the method, in capitals: {@code GET}
 * @param url the address, with {@code http://} or {@code https://}
 * @param headers the headers to send, in order, names as written
 * @param body the body, or {@code null} for none
 */
public record WebRequest(String method, String url, List<Map.Entry<String, String>> headers, byte[] body) {

    public WebRequest {
        headers = List.copyOf(headers);
    }
}
