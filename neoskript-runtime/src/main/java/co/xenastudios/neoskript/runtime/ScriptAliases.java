/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Item aliases a script defines in its {@code aliases:} structure ({@code any boots = diamond boots, iron
 * boots}), by script, with what each stands for as written. Only that script sees them, as in Skript.
 */
public final class ScriptAliases {

    private static final Map<String, Map<String, String>> ALIASES = new ConcurrentHashMap<>();

    /** Checks what an alias stands for, giving Skript's error for it or {@code null}; installed by the library. */
    private static volatile Function<String, String> checker = definition -> null;

    private ScriptAliases() {}

    public static void installChecker(Function<String, String> check) {
        checker = check;
    }

    /** Skript's error for an alias standing for {@code definition}, or {@code null} if it's fine. */
    public static String check(String definition) {
        return checker.apply(definition);
    }

    /** Records {@code script}'s aliases, by name in lower case, replacing those it had before. */
    public static void set(String script, Map<String, String> aliases) {
        if (aliases.isEmpty()) {
            ALIASES.remove(script);
        } else {
            ALIASES.put(script, Map.copyOf(aliases));
        }
    }

    /** What {@code name} stands for in {@code script}, or {@code null} if the script has no such alias. */
    public static String definition(String script, String name) {
        Map<String, String> aliases = script == null ? null : ALIASES.get(script);
        return aliases == null ? null : aliases.get(name.strip().toLowerCase(Locale.ROOT));
    }
}
