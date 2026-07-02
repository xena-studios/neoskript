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

    /**
     * The script currently being parsed on this thread, if any. Set by the loader around each file's
     * parse so that the {@code [the] [current] script} expression can capture its source at build time
     * (an expression factory runs while the script that contains it is being parsed).
     */
    private static final ThreadLocal<String> PARSING = new ThreadLocal<>();

    private LoadedScripts() {
    }

    /** Records (or clears, with {@code null}) the script name being parsed on the current thread. */
    public static void setParsing(String name) {
        if (name == null) {
            PARSING.remove();
        } else {
            PARSING.set(name);
        }
    }

    /** @return the name of the script being parsed on this thread, or {@code null} if none */
    public static String parsing() {
        return PARSING.get();
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
