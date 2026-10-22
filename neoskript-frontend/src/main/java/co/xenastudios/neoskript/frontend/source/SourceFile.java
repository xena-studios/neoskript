/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.source;

import java.util.List;
import java.util.Objects;

/**
 * A script's text and the name it's reported under, e.g. {@code homes.sk} or {@code subfolder/homes.sk}.
 */
public record SourceFile(String name, String text) {

    private static final char BYTE_ORDER_MARK = '﻿';

    public SourceFile {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(text, "text");
    }

    /** The file's lines, split on LF, CRLF or CR, without the line breaks or a leading byte order mark. */
    public List<String> lines() {
        String body = !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
        return List.of(body.split("\r\n|\r|\n", -1));
    }
}
