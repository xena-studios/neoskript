package co.xenastudios.neoskript.lang.type;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A user-defined {@link Tag} created by the {@code register ... tag} effect (Skript 2.10). Backs the
 * {@code custom} tag source: unlike Minecraft's built-in tags it lives entirely in NeoSkript, keyed
 * under the {@code skript} namespace and holding an explicit set of members.
 */
public final class CustomTag implements Tag<Keyed> {

    private final NamespacedKey key;
    private final Set<Keyed> values;

    public CustomTag(NamespacedKey key, Set<Keyed> values) {
        this.key = key;
        this.values = new LinkedHashSet<>(values);
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    @Override
    public Set<Keyed> getValues() {
        return java.util.Collections.unmodifiableSet(values);
    }

    @Override
    public boolean isTagged(Keyed item) {
        return values.contains(item);
    }
}
