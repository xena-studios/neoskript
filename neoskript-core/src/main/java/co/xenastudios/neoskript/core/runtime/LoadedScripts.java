package co.xenastudios.neoskript.core.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * A snapshot of the currently loaded script names (relative to the scripts directory), published by
 * the script loader and read by the {@code all scripts} / {@code script ... is loaded} syntax. Held
 * as an immutable volatile reference so reads never see a partially-updated list.
 */
public final class LoadedScripts {

    private static volatile List<String> names = List.of();

    private LoadedScripts() {
    }

    /** Replaces the set of loaded script names (called on each load/reload). */
    public static void set(Collection<String> scriptNames) {
        names = List.copyOf(scriptNames);
    }

    /** @return the loaded script names (relative paths, e.g. {@code "foo.sk"}) */
    public static List<String> names() {
        return names;
    }

    /** @return whether a script matching {@code name} is loaded (with or without the {@code .sk} suffix) */
    public static boolean isLoaded(String name) {
        String query = name.trim();
        for (String loaded : names) {
            if (loaded.equalsIgnoreCase(query) || loaded.equalsIgnoreCase(query + ".sk")) {
                return true;
            }
            if (loaded.toLowerCase(Locale.ROOT).endsWith(".sk")
                    && loaded.substring(0, loaded.length() - 3).equalsIgnoreCase(query)) {
                return true;
            }
        }
        return false;
    }
}
