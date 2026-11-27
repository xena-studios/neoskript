/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A type written as a value compares as Skript's comparator for a type does (issue #70). */
class TypeValueTest {

    private static final TypeValue NUMBER = new TypeValue("number", Number.class);
    private static final TypeValue INTEGER = new TypeValue("integer", Integer.class);

    @Test
    void aValueOfTheTypeIsIt() {
        assertTrue(Comparison.equal(5, NUMBER));
        assertTrue(Comparison.equal(NUMBER, 5));
        assertFalse(Comparison.equal("a", NUMBER));
    }

    @Test
    void theTypeIsItself() {
        // 'set {_a} to world' then '{_a} is world' in a function, where both are the type.
        assertTrue(Comparison.equal(NUMBER, new TypeValue("number", Number.class)));
        assertTrue(Comparison.equal(INTEGER, NUMBER));
        assertFalse(Comparison.equal(NUMBER, INTEGER));
        assertFalse(Comparison.equal(new TypeValue("text", String.class), NUMBER));
    }
}
