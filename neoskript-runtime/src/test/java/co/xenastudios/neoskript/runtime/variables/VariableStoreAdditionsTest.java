/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the store tells storage about changes that only add, which servers sharing variables merge. */
class VariableStoreAdditionsTest {

    private final List<String> heard = new ArrayList<>();

    private VariableStore watched() {
        VariableStore store = new VariableStore();
        store.watchAdditions(new VariableStore.Additions() {
            @Override
            public void adding(String name) {
                heard.add("adding " + name);
            }

            @Override
            public void added(String name, Number amount) {
                heard.add("added " + name + " " + amount);
            }

            @Override
            public void appending(String name) {
                heard.add("appending " + name);
            }
        });
        return store;
    }

    @Test
    void numbersAddedAndTakenAwayAreTold() {
        VariableStore store = watched();
        store.add("coins", new Object[] {5L});
        store.add("coins", new Object[] {1L, 2.5});
        store.subtract("coins", new Object[] {3L});
        store.subtract("debt", new Object[] {2L});
        assertEquals(List.of("adding coins", "added coins 5", "adding coins", "added coins 3.5", "adding coins",
                "added coins -3", "adding debt", "added debt -2"), heard);
        assertEquals(5.5, store.get("coins"));
    }

    @Test
    void anAdditionThatIsntOnlyNumbersIsToldWithoutAnAmount() {
        VariableStore store = watched();
        store.set("name", "steve");
        store.add("name", new Object[] {1L});
        // Always closed, so storage never waits for it.
        assertEquals(List.of("adding name", "added name null"), heard);
    }

    @Test
    void valuesAddedToAListAreToldByTheirIndexBeforeTheyreThere() {
        VariableStore store = watched();
        store.set("queue::1", "alice");
        store.addToList("queue", new Object[] {"bob", "carol"});
        assertEquals(List.of("appending queue::2", "appending queue::3"), heard);
        assertEquals("carol", store.get("queue::3"));
    }

    @Test
    void setsArentAdditions() {
        VariableStore store = watched();
        store.set("x", 1L);
        store.update("x", old -> 7L);
        store.setList("list", new Object[] {"a"});
        store.deleteList("list");
        assertTrue(heard.isEmpty(), heard.toString());
    }

    @Test
    void stoppingWatchingStopsTheCalls() {
        VariableStore store = new VariableStore();
        Runnable stop = store.watchAdditions(new VariableStore.Additions() {
            @Override
            public void adding(String name) {
                heard.add(name);
            }

            @Override
            public void added(String name, Number amount) {
                heard.add(name);
            }

            @Override
            public void appending(String name) {
                heard.add(name);
            }
        });
        stop.run();
        store.add("x", new Object[] {1L});
        store.addToList("l", new Object[] {"a"});
        assertTrue(heard.isEmpty());
    }
}
