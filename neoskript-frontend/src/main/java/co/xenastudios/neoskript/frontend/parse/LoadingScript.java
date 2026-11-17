/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.parse;

/**
 * The script being parsed or compiled on this thread, while scripts load: scripts are parsed on several threads at
 * once, so it's a thread's own. For addons that keep something per script, like skript-reflect's imports.
 */
public final class LoadingScript {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private LoadingScript() {}

    /** The script's name, or {@code null} outside of loading scripts. */
    public static String current() {
        return CURRENT.get();
    }

    /** Sets the script being loaded on this thread, and returns the one before, to put back after. */
    public static String set(String script) {
        String before = CURRENT.get();
        if (script == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(script);
        }
        return before;
    }
}
