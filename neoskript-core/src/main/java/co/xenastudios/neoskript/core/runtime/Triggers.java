package co.xenastudios.neoskript.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers for organizing parsed {@link Trigger}s. Kept in core (free of Bukkit) so the grouping logic
 * is unit-testable without a server.
 */
public final class Triggers {

    private Triggers() {
    }

    /**
     * Groups {@link Trigger.Kind#EVENT} triggers by their event class, preserving encounter order so
     * one listener can be registered per event type.
     *
     * @param triggers all parsed triggers (non-event kinds are ignored)
     * @return event class to its triggers, in insertion order
     */
    public static Map<Class<?>, List<Trigger>> groupEventTriggers(List<Trigger> triggers) {
        Map<Class<?>, List<Trigger>> grouped = new LinkedHashMap<>();
        for (Trigger trigger : triggers) {
            if (trigger.kind() == Trigger.Kind.EVENT) {
                grouped.computeIfAbsent(trigger.eventClass(), key -> new ArrayList<>()).add(trigger);
            }
        }
        return grouped;
    }
}
