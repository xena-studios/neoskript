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
