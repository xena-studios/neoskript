/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api;

import co.xenastudios.neoskript.api.annotation.Internal;

/**
 * Where the running NeoSkript is kept for {@link NeoSkript#get()}. Only NeoSkript's own plugin can set it: the API's
 * classes are in NeoSkript's jar, so only its classes share their class loader.
 */
@Internal
public final class Installed {

    static volatile NeoSkript instance;

    private Installed() {}

    /** NeoSkript's plugin class, found by name, so the API needs no Bukkit. */
    private static final String PLUGIN_CLASS = "co.xenastudios.neoskript.plugin.NeoSkriptPlugin";

    /**
     * Whether this copy of the API is an addon's own, shaded into its jar: NeoSkript's plugin is reachable, but from
     * another class loader than this class's, so it never installs itself here.
     */
    static boolean shaded() {
        try {
            Class<?> neoskript = Class.forName(PLUGIN_CLASS, false, Installed.class.getClassLoader());
            return neoskript.getClassLoader() != Installed.class.getClassLoader();
        } catch (ClassNotFoundException | LinkageError notThere) {
            return false;
        }
    }

    /**
     * Makes {@code neoskript} what {@link NeoSkript#get()} returns, or none with {@code null}. For NeoSkript's plugin.
     *
     * @param neoskript the running NeoSkript, or {@code null} when it disables
     * @throws SecurityException if it's called from outside NeoSkript's own jar
     */
    public static void install(NeoSkript neoskript) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        if (caller.getClassLoader() != Installed.class.getClassLoader()) {
            throw new SecurityException("Only NeoSkript itself can install its API, not " + caller.getName());
        }
        instance = neoskript;
    }
}
