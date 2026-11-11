/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Unmodifiable copies, for the registry's and the parser's lookup tables, in place of {@code Map.copyOf}
 * and {@code Set.copyOf}. Like those they reject nulls; unlike TeaVM's versions of those (which build the
 * browser parser), a lookup of something missing doesn't compare it with every entry.
 */
public final class Frozen {

    private Frozen() {}

    public static <K, V> Map<K, V> map(Map<? extends K, ? extends V> map) {
        Map<K, V> copy = new HashMap<>(map.size() * 4 / 3 + 1);
        map.forEach((key, value) -> copy.put(Objects.requireNonNull(key), Objects.requireNonNull(value)));
        return Collections.unmodifiableMap(copy);
    }

    public static <T> Set<T> set(Collection<? extends T> elements) {
        Set<T> copy = new HashSet<>(elements.size() * 4 / 3 + 1);
        for (T element : elements) {
            copy.add(Objects.requireNonNull(element));
        }
        return Collections.unmodifiableSet(copy);
    }
}
