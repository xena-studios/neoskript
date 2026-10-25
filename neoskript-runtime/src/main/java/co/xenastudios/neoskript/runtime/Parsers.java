/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** How text becomes a value of each type, by type id, for syntax that reads values from text at run time. */
public final class Parsers {

    private static final Map<String, Function<String, ?>> PARSERS = new ConcurrentHashMap<>();

    private Parsers() {}

    public static void register(String typeId, Function<String, ?> parser) {
        PARSERS.put(typeId, parser);
    }

    /** {@code text} as a value of {@code typeId}, or {@code null} if it isn't one or the type can't be parsed. */
    public static Object parse(String typeId, String text) {
        Function<String, ?> parser = PARSERS.get(typeId);
        try {
            return parser == null ? null : parser.apply(text);
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
