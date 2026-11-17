/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.skjson;

/** skJson's request methods. {@code MOCK} answers at once, without the network. */
public enum RequestMethod {
    GET,
    POST,
    PUT,
    DELETE,
    MOCK,
    HEAD,
    PATCH
}
