package co.xenastudios.neoskript.lang.type;

import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registry of user-defined {@link CustomTag}s, grouped by tag-registry key (blocks/items/entity
 * types) — the store behind the {@code custom} tag source. Cleared on each script (re)load so removed
 * {@code register ... tag} effects don't leave stale tags behind.
 */
public final class CustomTags {

    private static final Map<String, Map<NamespacedKey, CustomTag>> BY_REGISTRY = new ConcurrentHashMap<>();

    private CustomTags() {
    }

    /** Registers (or replaces) a custom tag under the given tag-registry key. */
    public static void register(String registry, CustomTag tag) {
        BY_REGISTRY.computeIfAbsent(registry, ignored -> new LinkedHashMap<>()).put(tag.getKey(), tag);
    }

    /** @return all custom tags registered under {@code registry}, in registration order */
    public static List<CustomTag> all(String registry) {
        Map<NamespacedKey, CustomTag> tags = BY_REGISTRY.get(registry);
        return tags == null ? List.of() : new ArrayList<>(tags.values());
    }

    /** @return the custom tag with the given key under {@code registry}, or {@code null} */
    public static CustomTag get(String registry, NamespacedKey key) {
        Map<NamespacedKey, CustomTag> tags = BY_REGISTRY.get(registry);
        return tags == null ? null : tags.get(key);
    }

    /** Removes every registered custom tag (called when scripts reload). */
    public static void clear() {
        BY_REGISTRY.clear();
    }
}
