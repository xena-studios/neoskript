package co.xenastudios.neoskript.core.type;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of {@link Converter}s with breadth-first chaining: given a value and a target type, it
 * finds the shortest path of registered converters (e.g. {@code player → location → vector}) and
 * applies them in order. Resolved paths are cached per (source class, target type).
 */
public final class ConverterRegistry {

    private record Edge(Class<?> to, Converter<Object, Object> converter) {
    }

    private static final Object NO_PATH = new Object();

    private final Map<Class<?>, List<Edge>> edges = new HashMap<>();
    private final Map<String, Object> pathCache = new ConcurrentHashMap<>();

    /**
     * Registers a converter edge.
     *
     * @param from      the source class
     * @param to        the target class
     * @param converter the converter
     * @param <F>       source type
     * @param <T>       target type
     */
    @SuppressWarnings("unchecked")
    public <F, T> void register(Class<F> from, Class<T> to, Converter<F, T> converter) {
        edges.computeIfAbsent(from, k -> new ArrayList<>())
                .add(new Edge(to, (Converter<Object, Object>) converter));
    }

    /**
     * Converts a value to the target type, chaining converters as needed.
     *
     * @param value  the value (may be {@code null})
     * @param target the desired type
     * @param <T>    the target type
     * @return the converted value, or {@code null} if no conversion path exists
     */
    @SuppressWarnings("unchecked")
    public <T> T convert(Object value, Class<T> target) {
        if (value == null) {
            return null;
        }
        if (target.isInstance(value)) {
            return (T) value;
        }
        List<Edge> path = resolvePath(value.getClass(), target);
        if (path == null) {
            return null;
        }
        Object current = value;
        for (Edge edge : path) {
            current = edge.converter().convert(current);
            if (current == null) {
                return null;
            }
        }
        return target.isInstance(current) ? (T) current : null;
    }

    /** @return {@code true} if a conversion path exists from {@code from} to {@code to} */
    public boolean canConvert(Class<?> from, Class<?> to) {
        return to.isAssignableFrom(from) || resolvePath(from, to) != null;
    }

    @SuppressWarnings("unchecked")
    private List<Edge> resolvePath(Class<?> from, Class<?> to) {
        String key = from.getName() + "->" + to.getName();
        Object cached = pathCache.get(key);
        if (cached != null) {
            return cached == NO_PATH ? null : (List<Edge>) cached;
        }
        List<Edge> path = breadthFirst(from, to);
        pathCache.put(key, path == null ? NO_PATH : path);
        return path;
    }

    private List<Edge> breadthFirst(Class<?> from, Class<?> to) {
        Deque<Class<?>> queue = new ArrayDeque<>();
        Map<Class<?>, Edge> cameFrom = new HashMap<>();
        Set<Class<?>> visited = new HashSet<>();
        queue.add(from);
        visited.add(from);

        while (!queue.isEmpty()) {
            Class<?> node = queue.poll();
            for (Edge edge : edges.getOrDefault(node, List.of())) {
                if (!visited.add(edge.to())) {
                    continue;
                }
                // cameFrom maps a node to (predecessor, converter predecessor→node).
                cameFrom.put(edge.to(), new Edge(node, edge.converter()));
                if (to.isAssignableFrom(edge.to())) {
                    return reconstruct(cameFrom, from, edge.to());
                }
                queue.add(edge.to());
            }
        }
        return null;
    }

    /** Walks back from the target node to {@code from}, producing converters in apply order. */
    private List<Edge> reconstruct(Map<Class<?>, Edge> cameFrom, Class<?> from, Class<?> targetNode) {
        List<Edge> path = new ArrayList<>();
        Class<?> node = targetNode;
        while (node != from) {
            Edge step = cameFrom.get(node);
            path.add(0, new Edge(node, step.converter())); // (result type, converter to apply)
            node = step.to(); // predecessor
        }
        return path;
    }
}
