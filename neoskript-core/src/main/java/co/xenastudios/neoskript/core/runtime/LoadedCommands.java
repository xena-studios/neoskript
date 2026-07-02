package co.xenastudios.neoskript.core.runtime;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A snapshot of the command names (and aliases) registered by scripts, published by the script loader
 * and read by the {@code %string% is a skript command} condition. Held as an immutable volatile set.
 */
public final class LoadedCommands {

    private static volatile Set<String> names = Set.of();

    private LoadedCommands() {
    }

    /** Replaces the set of script command names (lowercased), called on each load/reload. */
    public static void set(Collection<String> commandNames) {
        names = commandNames.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** @return whether {@code name} (with or without a leading slash) is a registered script command */
    public static boolean isCommand(String name) {
        String query = name.trim().toLowerCase(Locale.ROOT);
        if (query.startsWith("/")) {
            query = query.substring(1);
        }
        return names.contains(query);
    }
}
