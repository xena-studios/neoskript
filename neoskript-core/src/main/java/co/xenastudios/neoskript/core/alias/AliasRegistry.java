package co.xenastudios.neoskript.core.alias;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom item aliases declared by an {@code aliases:} structure (e.g. {@code blade = diamond sword}).
 * The mapping is global and consulted when an item literal is parsed, before falling back to the
 * vanilla material lookup. Reloading a script clears and repopulates it via {@link #clear()}.
 */
public final class AliasRegistry {

    private static final Map<String, String> ALIASES = new ConcurrentHashMap<>();

    private AliasRegistry() {
    }

    /**
     * Registers an alias.
     *
     * @param alias    the alias name (case-insensitive)
     * @param material the material id (or another alias) it expands to
     */
    public static void register(String alias, String material) {
        ALIASES.put(normalize(alias), material.trim());
    }

    /**
     * Resolves an alias to its material id, following one level of indirection.
     *
     * @param alias the alias to resolve
     * @return the mapped material id, or {@code null} if not an alias
     */
    public static String resolve(String alias) {
        return ALIASES.get(normalize(alias));
    }

    /** Removes all registered aliases. */
    public static void clear() {
        ALIASES.clear();
    }

    private static String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }
}
