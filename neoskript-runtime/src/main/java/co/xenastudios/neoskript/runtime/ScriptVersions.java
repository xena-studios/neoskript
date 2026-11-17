/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which compile of each script is loaded: code that outlives a reload (an addon's section code, a trigger resumed after
 * a wait) can tell whether it's the loaded version by the version it was compiled with, whatever the timing.
 */
public final class ScriptVersions {

    private static final Map<String, Object> LOADED = new ConcurrentHashMap<>();

    private ScriptVersions() {}

    /** {@code version} of {@code script} is now loaded. */
    public static void load(String script, Object version) {
        if (version == null) {
            LOADED.remove(script);
        } else {
            LOADED.put(script, version);
        }
    }

    /** {@code script} isn't loaded any more. */
    public static void unloaded(String script) {
        LOADED.remove(script);
    }

    /** Whether {@code version} is the loaded version of {@code script}. */
    public static boolean loaded(String script, Object version) {
        return version != null && LOADED.get(script) == version;
    }
}
