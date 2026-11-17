/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.skjson;

/** How skJson's request went: {@code OK} for a status below 500, {@code FAILED}, or {@code UNKNOWN} before it's sent. */
public enum RequestStatus {
    OK,
    FAILED,
    UNKNOWN
}
