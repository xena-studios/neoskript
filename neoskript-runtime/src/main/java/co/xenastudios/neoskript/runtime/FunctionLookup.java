/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.util.List;

/** Finds functions by name while scripts run, for {@code function "name"}. The plugin installs the source. */
public final class FunctionLookup {

    /** Where functions are found. */
    public interface Source {
        /** The function named {@code name} seen from {@code script}: one local to it, else a global one, or {@code null}. */
        FunctionRef find(String name, String script);

        /** The functions {@code script} defines, global and local. */
        List<FunctionRef> definedIn(String script);
    }

    private static volatile Source source;

    private FunctionLookup() {}

    public static void install(Source installed) {
        source = installed;
    }

    public static FunctionRef find(String name, String script) {
        Source current = source;
        return current == null ? null : current.find(name, script);
    }

    public static List<FunctionRef> definedIn(String script) {
        Source current = source;
        return current == null ? List.of() : current.definedIn(script);
    }
}
