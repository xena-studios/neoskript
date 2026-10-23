/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.element.Condition;
import org.junit.jupiter.api.Test;

class ValuesArithmeticTest {

    private static long millis(String text) {
        return Timespan.parse(text).millis();
    }

    @Test
    void parsesTimespansAsSkriptDoes() {
        assertEquals(30 * 60_000 + 20_000, millis("30m and 20s"));
        assertEquals(36 * 3_600_000L, millis("1.5d"));
        assertEquals(2_592_000_000L + 2 * 604_800_000L + 3 * 86_400_000L, millis("1mo 2w 3d"));
        assertEquals(90_000, millis("1:30"));
        assertEquals(50 * 60_000 + 45_000, millis("50 minutes 45 seconds"));
        assertEquals(1000, millis("a second"));
        assertEquals(9_999_999_999L * 86_400_000L, millis("9999999999d"));
        assertNull(Timespan.parse("invalidinput"));
        assertNull(Timespan.parse("and 5 seconds"));
    }

    @Test
    void timespanArithmeticSaturates() {
        Timespan year = new Timespan(31_536_000_000L);
        assertEquals(new Timespan(Long.MAX_VALUE), Arithmetic.add(new Timespan(Long.MAX_VALUE - 1), year));
        assertEquals(new Timespan(0), Arithmetic.subtract(new Timespan(1000), new Timespan(2000)));
        assertEquals(new Timespan(500), Arithmetic.divide(new Timespan(1000), 2L));
        assertEquals(0.5, Arithmetic.divide(new Timespan(1000), new Timespan(2000)));
        assertEquals(new Timespan(2000), Arithmetic.multiply(2L, new Timespan(1000)));
        assertNull(Arithmetic.divide(2L, new Timespan(1000)));
        assertNull(Arithmetic.add(new Timespan(1000), 2L));
        assertEquals(new Date(86_400_000L), Arithmetic.add(new Date(0), new Timespan(86_400_000L)));
    }

    @Test
    void mergesKeyedValuesAroundTakenIndices() {
        KeyedValues keyed = new KeyedValues(new Object[] {"first", "dup"}, new String[] {"1.foo", "2"});
        KeyedValues merged = KeyedValues.merge(new Object[] {keyed, "fourth", new Object[] {"fifth", "sixth"}});
        assertArrayEquals(new String[] {"1.foo", "2", "1", "3", "4"}, merged.keys());
        assertArrayEquals(new Object[] {"first", "dup", "fourth", "fifth", "sixth"}, merged.values());
        KeyedValues mixed = new KeyedValues(new Object[] {"first", 100L}, new String[] {"1.foo", "number"});
        assertArrayEquals(new String[] {"1.foo"}, mixed.only(String.class).keys());
    }

    @Test
    void checksListsAsSkriptDoes() {
        Object[] hey = {"hey"};
        assertFalse(Condition.check(new Object[] {"he", "hello"}, false, "hey"::equals));
        assertTrue(Condition.check(new Object[] {"hey", "h"}, true, prefix -> "hey".startsWith((String) prefix)));
        assertFalse(Condition.check(new Object[] {"hey", "e"}, true, prefix -> "hey".startsWith((String) prefix)));
        assertTrue(Condition.check(new Object[] {"hey", "e"}, false, prefix -> "hey".startsWith((String) prefix)));
        assertFalse(Condition.check(new Object[0], true, value -> true));
        assertTrue(Condition.check(hey, true, value -> true));
    }
}
