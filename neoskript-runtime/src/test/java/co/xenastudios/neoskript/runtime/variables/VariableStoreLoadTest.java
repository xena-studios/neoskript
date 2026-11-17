/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Loading many variables at once in any order: as fast as setting each in turn, or faster. Names that were first
 * saved at different times, like {@code {firstjoin::%uuid%}}, come in the order they were first saved until a
 * compaction sorts them, which for keys written once may be never.
 */
class VariableStoreLoadTest {

    private static final int KEYS = 600_000;

    @Test
    void randomOrderUuidKeysLoadAtLeastAsFastAsSettingEach() {
        Random random = new Random(42);
        List<String> names = new ArrayList<>(KEYS);
        List<Object> values = new ArrayList<>(KEYS);
        for (int i = 0; i < KEYS; i++) {
            names.add("firstjoin::" + new UUID(random.nextLong(), random.nextLong()));
            values.add((long) i);
        }
        // Best of 3 each, after a warm-up, so the JIT doesn't decide it.
        long load = Long.MAX_VALUE;
        long set = Long.MAX_VALUE;
        VariableStore loaded = null;
        VariableStore each = null;
        for (int round = 0; round < 4; round++) {
            long start = System.nanoTime();
            loaded = new VariableStore();
            loaded.load(names, values);
            long middle = System.nanoTime();
            each = new VariableStore();
            for (int i = 0; i < KEYS; i++) {
                each.set(names.get(i), values.get(i));
            }
            long end = System.nanoTime();
            if (round > 0) {
                load = Math.min(load, middle - start);
                set = Math.min(set, end - middle);
            }
        }
        System.out.printf("%,d random-order UUID keys: load %d ms, set one by one %d ms%n", KEYS, load / 1_000_000,
                set / 1_000_000);
        assertArrayEquals(each.indices("firstjoin"), loaded.indices("firstjoin"));
        assertEquals(KEYS, loaded.list("firstjoin").length);
        assertEquals(values.get(12_345), loaded.get(names.get(12_345)));
        assertTrue(load <= set * 1.5, "load " + load / 1_000_000 + " ms, set " + set / 1_000_000 + " ms");
    }
}
