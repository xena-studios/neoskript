package co.xenastudios.neoskript.lang.event;

import org.bukkit.event.Event;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves an "event-value" of a requested type from the current event by reflection — e.g. the
 * {@code block} of a block event, the {@code world} of a world event. It finds a no-argument getter
 * on the event whose return type matches the requested type, caching the lookup per
 * (event class, requested type).
 *
 * <p>Getters declared by {@link Event} itself (and {@link Object}) are ignored so generic accessors
 * like {@code getEventName()} don't shadow real event values.
 */
public final class EventValueResolver {

    private record Key(Class<?> eventClass, Class<?> type) {
    }

    private static final Method NONE;

    static {
        try {
            NONE = Object.class.getMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final ConcurrentMap<Key, Method> CACHE = new ConcurrentHashMap<>();

    private EventValueResolver() {
    }

    /**
     * Resolves a value of {@code type} from {@code event}.
     *
     * @param event the event (may be {@code null})
     * @param type  the requested value type
     * @return the value, or {@code null} if the event has no such value
     */
    public static Object resolve(Event event, Class<?> type) {
        if (event == null) {
            return null;
        }
        Method method = CACHE.computeIfAbsent(new Key(event.getClass(), type),
                key -> findGetter(key.eventClass(), key.type()));
        if (method == NONE) {
            return null;
        }
        try {
            Object value = method.invoke(event);
            return type.isInstance(value) ? value : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Method findGetter(Class<?> eventClass, Class<?> type) {
        Method best = null;
        for (Method method : eventClass.getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            Class<?> declaring = method.getDeclaringClass();
            if (declaring == Event.class || declaring == Object.class) {
                continue;
            }
            if (!type.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            // Prefer an exact-type getter over a wider one (e.g. getPlayer() over getEntity()).
            if (best == null || best.getReturnType().isAssignableFrom(method.getReturnType())) {
                best = method;
            }
        }
        return best == null ? NONE : best;
    }
}
