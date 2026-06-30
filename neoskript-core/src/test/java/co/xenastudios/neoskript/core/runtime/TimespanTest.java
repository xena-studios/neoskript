package co.xenastudios.neoskript.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimespanTest {

    @Test
    void parsesCommonUnitsToTicks() {
        assertEquals(1L, Timespan.parseTicks("1 tick").getAsLong());
        assertEquals(20L, Timespan.parseTicks("1 second").getAsLong());
        assertEquals(100L, Timespan.parseTicks("5 seconds").getAsLong());
        assertEquals(1200L, Timespan.parseTicks("1 minute").getAsLong());
        assertEquals(72000L, Timespan.parseTicks("1 hour").getAsLong());
    }

    @Test
    void supportsFractionalAndAbbreviatedUnits() {
        assertEquals(10L, Timespan.parseTicks("0.5 seconds").getAsLong());
        assertEquals(600L, Timespan.parseTicks("30 secs").getAsLong());
        assertEquals(2400L, Timespan.parseTicks("2 mins").getAsLong());
    }

    @Test
    void rejectsUnknownTimespans() {
        assertTrue(Timespan.parseTicks("banana").isEmpty());
        assertTrue(Timespan.parseTicks("5 lightyears").isEmpty());
    }
}
