/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** An addon's result as keyed values ({@link KeyedValues#result}): nested maps and lists, and ones that never end. */
class KeyedResultTest {

    @Test
    void nestedMapsAndListsArePaths() {
        Map<String, Object> home = new LinkedHashMap<>();
        home.put("x", 10L);
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("home", home);
        tree.put("tags", List.of("a", "b"));
        KeyedValues keyed = (KeyedValues) KeyedValues.result(tree);
        assertArrayEquals(new String[] {"home::x", "tags::1", "tags::2"}, keyed.keys());
        assertArrayEquals(new Object[] {10L, "a", "b"}, keyed.values());
    }

    @Test
    void theSameMapTwiceIsntItself() {
        Map<String, Object> shared = Map.of("v", 1L);
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("a", shared);
        tree.put("b", shared);
        assertEquals(2, ((KeyedValues) KeyedValues.result(tree)).keys().length);
    }

    @Test
    void aMapThatContainsItselfThrowsAtOnce() {
        Map<String, Object> loop = new HashMap<>();
        loop.put("a", "x");
        loop.put("self", loop);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> KeyedValues.result(loop));
        assertEquals("The result contains itself", thrown.getMessage());
        List<Object> list = new ArrayList<>();
        list.add(list);
        assertThrows(IllegalArgumentException.class, () -> KeyedValues.result(Map.of("l", list)));
    }

    @Test
    void aMapThatHoldsTheSameMapTwiceAtEveryLevelStopsQuickly() {
        // 60 levels, each holding the next twice: about 2^60 paths, but few maps.
        Map<String, Object> level = Map.of("v", 1L);
        for (int i = 0; i < 60; i++) {
            Map<String, Object> next = new LinkedHashMap<>();
            next.put("a", level);
            next.put("b", level);
            level = next;
        }
        Map<String, Object> top = level;
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> KeyedValues.result(top));
            assertEquals("The result has more than " + KeyedValues.MAX_NODES + " parts", thrown.getMessage());
        });
    }
}
