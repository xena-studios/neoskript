/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.util.List;

/** The script files the plugin knows: which exist, which are loaded. Installed by the plugin. */
public interface ScriptCatalog {

    /** The script called {@code name} (with or without {@code .sk}), or {@code null} if there's no such file. */
    ScriptRef find(String name);

    /** Whether {@code script} is loaded. */
    boolean loaded(ScriptRef script);

    /** Every script file: loaded ones, disabled ones, or both. */
    List<ScriptRef> scripts(boolean loaded, boolean disabled);

    /**
     * Loads {@code script}, enabling it first if it's disabled, and runs its load events now. Returns the
     * script as it's now named, or {@code null} if it was already loaded or couldn't be.
     */
    default ScriptRef load(ScriptRef script) {
        return null;
    }

    /** Loads {@code script} again, running its load events now. */
    default void reload(ScriptRef script) {}

    /** Stops {@code script}, leaving its file as it is. */
    default void unload(ScriptRef script) {}

    /** Stops {@code script} and disables its file (a {@code -} before its name). Returns it as now named. */
    default ScriptRef disable(ScriptRef script) {
        return null;
    }

    /** The catalog the plugin installed; an empty one until then. */
    static ScriptCatalog get() {
        return Holder.catalog;
    }

    static void install(ScriptCatalog catalog) {
        Holder.catalog = catalog;
    }

    /** Where the installed catalog is kept. */
    final class Holder {
        private static volatile ScriptCatalog catalog = new ScriptCatalog() {
            @Override
            public ScriptRef find(String name) {
                return null;
            }

            @Override
            public boolean loaded(ScriptRef script) {
                return false;
            }

            @Override
            public List<ScriptRef> scripts(boolean loaded, boolean disabled) {
                return List.of();
            }
        };

        private Holder() {}
    }
}
