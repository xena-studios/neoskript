package co.xenastudios.neoskript.core.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers for list-variable storage. Keeps list iteration deterministic by ordering direct children
 * by their index — numerically when the index is an integer (so {@code ::1, ::2, ::10} sort naturally),
 * otherwise lexically.
 */
public final class ListVariables {

    private ListVariables() {
    }

    /**
     * Returns the direct children of a list prefix (full-name keyed), ordered by index.
     *
     * @param map    the variable map
     * @param prefix the list prefix including the trailing {@code ::}
     * @return an ordered map of full child name to value
     */
    public static Map<String, Object> directChildren(Map<String, Object> map, String prefix) {
        List<Map.Entry<String, Object>> matches = new ArrayList<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix) && key.indexOf("::", prefix.length()) < 0) {
                matches.add(entry);
            }
        }
        matches.sort(Comparator.comparing(e -> e.getKey().substring(prefix.length()), ListVariables::compareIndex));

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : matches) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Returns <em>all</em> descendants of a list prefix at every depth (full-name keyed), ordered by
     * their full relative path. Unlike {@link #directChildren}, this includes entries nested under
     * sub-lists ({@code a::b::c} is a descendant of {@code a::}) — the basis for recursive list size.
     *
     * @param map    the variable map
     * @param prefix the list prefix including the trailing {@code ::}
     * @return an ordered map of full descendant name to value
     */
    public static Map<String, Object> recursiveChildren(Map<String, Object> map, String prefix) {
        List<Map.Entry<String, Object>> matches = new ArrayList<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                matches.add(entry);
            }
        }
        matches.sort(Comparator.comparing(e -> e.getKey().substring(prefix.length()), ListVariables::compareIndex));

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : matches) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static int compareIndex(String a, String b) {
        try {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }
}
