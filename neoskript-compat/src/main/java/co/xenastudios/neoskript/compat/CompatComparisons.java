/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.skriptlang.skript.lang.comparator.Comparator;
import org.skriptlang.skript.lang.comparator.Relation;

/** Comparisons addons register. */
public final class CompatComparisons {

    /** A comparison an addon registered. */
    public record Comparison(Class<?> first, Class<?> second, Comparator<Object, Object> comparator) {}

    private final List<Comparison> comparisons = new CopyOnWriteArrayList<>();

    CompatComparisons() {}

    @SuppressWarnings("unchecked")
    public <A, B> void register(Class<A> first, Class<B> second, Comparator<A, B> comparator) {
        comparisons.add(new Comparison(first, second, (Comparator<Object, Object>) comparator));
    }

    public boolean exists(Class<?> first, Class<?> second, boolean exact) {
        for (Comparison comparison : comparisons) {
            if (exact ? comparison.first() == first && comparison.second() == second
                    : comparison.first().isAssignableFrom(first) && comparison.second().isAssignableFrom(second)) {
                return true;
            }
        }
        return false;
    }

    public Relation compare(Object first, Object second) {
        for (Comparison comparison : comparisons) {
            if (comparison.first().isInstance(first) && comparison.second().isInstance(second)) {
                return comparison.comparator().compare(first, second);
            }
            if (comparison.first().isInstance(second) && comparison.second().isInstance(first)) {
                return comparison.comparator().compare(second, first).getSwitched();
            }
        }
        return Relation.get(Objects.equals(first, second));
    }

    List<Comparison> all() {
        return List.copyOf(comparisons);
    }
}
