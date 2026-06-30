package co.xenastudios.neoskript.core.runtime;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps script event names (and aliases) to Bukkit event classes, e.g. {@code "join"} and
 * {@code "player join"} to {@code PlayerJoinEvent}.
 *
 * <p>Event classes are stored as raw {@link Class} objects so core stays free of hard Bukkit
 * imports; the built-in event definitions (which reference the concrete Bukkit classes) live in the
 * language module and populate this registry at load time.
 */
public final class EventRegistry {

    private final Map<String, Class<?>> byName = new HashMap<>();

    /**
     * Registers one or more aliases for an event class.
     *
     * @param eventClass the Bukkit event class
     * @param aliases    the names that match it in scripts (case-insensitive)
     */
    public void register(Class<?> eventClass, String... aliases) {
        for (String alias : aliases) {
            byName.put(normalize(alias), eventClass);
        }
    }

    /**
     * Resolves an event name to its class.
     *
     * @param name the event name from the script
     * @return the event class, or {@link Optional#empty()} if unknown
     */
    public Optional<Class<?>> resolve(String name) {
        return Optional.ofNullable(byName.get(normalize(name)));
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
