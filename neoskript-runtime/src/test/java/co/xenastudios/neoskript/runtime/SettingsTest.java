/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SettingsTest {

    @AfterEach
    void defaults() {
        Settings.numberAccuracy(2);
        Settings.caseSensitive(false);
        Settings.dateFormat("default");
        Language.use(Language.ENGLISH);
        VariableStore.refreshOnRead(null);
    }

    @Test
    void numbersAreWrittenWithTheNumberAccuracy() {
        assertEquals("0.33", Values.text(1.0 / 3));
        assertEquals("1.01", Values.text(1.005));
        assertEquals("-0", Values.text(-0.004));
        Settings.numberAccuracy(4);
        assertEquals("0.3333", Values.text(1.0 / 3));
        assertEquals("2.5", Values.text(2.5));
        Settings.numberAccuracy(0);
        assertEquals("3", Values.text(2.5));
        assertEquals("0", Values.text(0.4));
        Settings.numberAccuracy(-3);
        assertEquals(0, Settings.numberAccuracy(), "a negative accuracy rounds, as in Skript");
        // Whole numbers never have decimals.
        assertEquals("7", Values.text(7L));
    }

    @Test
    void variableNamesKeepEightDecimalPlaces() {
        assertEquals("0.33333333", Values.nameText(1.0 / 3));
        Settings.numberAccuracy(1);
        assertEquals("1.23456", Values.nameText(1.23456));
        assertEquals("1.2", Values.text(1.23456));
    }

    @Test
    void textComparesWithCaseOnlyWhenCaseSensitive() {
        assertTrue(Comparison.equal("Hello", "hello"));
        Settings.caseSensitive(true);
        assertFalse(Comparison.equal("Hello", "hello"));
        assertTrue(Comparison.equal("Hello", "Hello"));
    }

    @Test
    void datesAreWrittenInTheDateFormat() {
        TimeZone zone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Settings.dateFormat("yyyy-MM-dd HH:mm");
            assertEquals("2024-01-15 10:30", new Date(1_705_314_600_000L).toString());
            Settings.dateFormat("DEFAULT");
            assertEquals(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                    .format(new java.util.Date(1_705_314_600_000L)), new Date(1_705_314_600_000L).toString());
        } finally {
            TimeZone.setDefault(zone);
        }
        assertThrows(IllegalArgumentException.class, () -> Settings.dateFormat("yyyy-qq"));
    }

    @Test
    void theLanguageChangesWhatNothingIs() {
        assertEquals("<none>", Values.text(null));
        Language.use(Map.of("none", "<tanımsız>", "skript.prefix", "ignored"));
        assertEquals("<tanımsız>", Values.text(null));
        assertEquals("<tanımsız>", Values.text(new Object[0]));
        // Keys the language doesn't have stay English.
        assertEquals("You are using this command too often, please try again later",
                Language.get("commands.cooldown message"));
    }

    @Test
    void readingAVariableCanRefreshItsValue() {
        VariableStore store = new VariableStore();
        Object stale = new Object();
        Object fresh = new Object();
        store.set("p", stale);
        store.set("list::1", stale);
        assertSame(stale, store.get("p"));
        VariableStore.refreshOnRead(value -> value == stale ? fresh : value);
        assertSame(fresh, store.get("p"));
        assertSame(fresh, store.list("list")[0]);
        VariableStore.refreshOnRead(null);
        assertSame(fresh, store.get("p"), "the fresh value is stored");
        assertSame(fresh, store.keyedList("list").values()[0]);
    }
}
