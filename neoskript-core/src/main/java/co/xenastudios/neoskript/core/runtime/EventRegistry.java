package co.xenastudios.neoskript.core.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

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
    private final List<Function<String, Optional<FilteredEvent>>> filterResolvers = new ArrayList<>();

    /**
     * An event class paired with an optional runtime filter — e.g. {@code on break of diamond ore}
     * resolves to {@code BlockBreakEvent} plus a predicate that the broken block is diamond ore.
     * A {@code null} filter means "run for every occurrence of the event".
     *
     * @param eventClass the Bukkit event class
     * @param filter     a predicate over the event object, or {@code null} for no filter
     */
    public record FilteredEvent(Class<?> eventClass, Predicate<Object> filter) {
    }

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

    /**
     * Registers a resolver for filtered event names like {@code death of %entitytype%} or
     * {@code break of %itemtype%}. Consulted only when a name does not resolve to a plain alias.
     *
     * @param resolver maps an event name to a {@link FilteredEvent}, or empty if it does not apply
     */
    public void registerFilter(Function<String, Optional<FilteredEvent>> resolver) {
        filterResolvers.add(resolver);
    }

    /**
     * Resolves an event name to its class and optional runtime filter. A plain alias resolves with a
     * {@code null} filter; otherwise the registered filter resolvers are tried in order.
     *
     * @param name the event name from the script
     * @return the resolved event, or empty if unknown
     */
    public Optional<FilteredEvent> resolveFiltered(String name) {
        Class<?> direct = byName.get(normalize(name));
        if (direct != null) {
            return Optional.of(new FilteredEvent(direct, null));
        }
        for (Function<String, Optional<FilteredEvent>> resolver : filterResolvers) {
            Optional<FilteredEvent> result = resolver.apply(name.trim());
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    /** @return all registered aliases mapped to their event class (a copy) */
    public Map<String, Class<?>> aliases() {
        return Map.copyOf(byName);
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
