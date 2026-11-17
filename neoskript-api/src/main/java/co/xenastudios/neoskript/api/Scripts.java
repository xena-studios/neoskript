/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api;

import java.util.List;

/**
 * The script files in {@code plugins/NeoSkript/scripts}, by name: a script's path relative to that folder
 * ({@code quests/main.sk}), with or without {@code .sk}. Loading, reloading and unloading run their load and unload
 * events, as {@code /sk reload} does; call them from the global thread ({@link Threads#runGlobal}). Until NeoSkript
 * is ready ({@link NeoSkript#whenReady}), every method throws {@link IllegalStateException}.
 *
 * <p>Addons call this; they don't implement it.
 */
public interface Scripts {

    /**
     * The scripts that are loaded, in the folder's order.
     *
     * @return their names, as {@code quests/main.sk}
     */
    List<String> loaded();

    /**
     * Whether a script is loaded.
     *
     * @param name the script's name
     * @return whether it's loaded
     */
    boolean isLoaded(String name);

    /**
     * Loads a script that isn't loaded, enabling its file first if it's disabled ({@code -name.sk}).
     *
     * @param name the script's name
     * @return whether it was loaded now: not if there's no such file or it was loaded already. Its errors are
     *     logged as {@code /sk reload} logs them, and it loads without what has them, as in Skript
     */
    boolean load(String name);

    /**
     * Loads a script again from its file, as {@code /sk reload} does: if the new version has errors, they're
     * logged and it loads without what has them, as in Skript.
     *
     * @param name the script's name
     * @return whether there's such a script, enabled
     */
    boolean reload(String name);

    /**
     * Stops a script, leaving its file as it is.
     *
     * @param name the script's name
     * @return whether it was loaded
     */
    boolean unload(String name);

    /**
     * Calls a function: one of a loaded script's global functions, or a built-in one. Arguments go to the
     * function's parameters in order; a last parameter that takes several values takes the rest. Call it on a thread
     * that may touch what the function does, as a script would (see {@link Threads}).
     *
     * @param name the function's name, as {@code heal} for {@code function heal(p: player)}
     * @param arguments the values, as scripts have them: a {@code Long} or {@code Double} for a number, a
     *     {@code String} for a text
     * @return what the function returns: one value, an {@code Object[]} for several, or {@code null} for none
     * @throws IllegalArgumentException if there's no such function
     */
    Object call(String name, Object... arguments);
}
