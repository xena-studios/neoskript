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

/** What the store tells storage about each change, which servers sharing variables need to merge exactly. */
class VariableStoreAdditionsTest {

    private final List<String> heard = new ArrayList<>();

    private VariableStore watched() {
        VariableStore store = new VariableStore();
        store.watch(new VariableStore.Changes() {
            @Override
            public void changing(String name, boolean adding) {
                heard.add((adding ? "adding " : "changing ") + name);
            }

            @Override
            public void changed(String name, boolean adding, Number amount) {
                heard.add((adding ? "added " : "changed ") + name + (adding ? " " + amount : ""));
            }

            @Override
            public void appending(String name) {
                heard.add("appending " + name);
            }
        });
        return store;
    }

    @Test
    void numbersAddedAndTakenAwayAreToldWithTheirAmount() {
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
        heard.clear();
        store.add("name", new Object[] {1L});
        // Always closed, so storage never waits for it.
        assertEquals(List.of("adding name", "added name null"), heard);
    }

    @Test
    void setsAndDeletesAreToldBeforeAndAfter() {
        VariableStore store = watched();
        store.set("x", 1L);
        store.update("x", old -> 7L);
        store.set("x", null);
        assertEquals(List.of("changing x", "changed x", "changing x", "changed x", "changing x", "changed x"), heard);
    }

    @Test
    void changesToAWholeListAreToldForTheList() {
        VariableStore store = watched();
        store.setList("list", new Object[] {"b", "a"});
        heard.clear();
        store.sortList("list", false);
        store.removeFromList("list", new Object[] {"a"}, false);
        store.copyList("list", "other");
        store.deleteList("list");
        assertTrue(heard.stream().allMatch(line -> line.endsWith("list::*") || line.endsWith("other::*")), heard
                .toString());
        assertEquals("changing list::*", heard.getFirst());
        assertEquals("changed list::*", heard.getLast());
    }

    @Test
    void valuesAddedToAListAreToldByTheirIndexBeforeTheyreThere() {
        VariableStore store = watched();
        store.set("queue::1", "alice");
        heard.clear();
        store.addToList("queue", new Object[] {"bob", "carol"});
        assertEquals(List.of("appending queue::2", "appending queue::3"), heard);
        assertEquals("carol", store.get("queue::3"));
    }

    @Test
    void stoppingWatchingStopsTheCalls() {
        VariableStore store = new VariableStore();
        Runnable stop = store.watch(new VariableStore.Changes() {
            @Override
            public void changing(String name, boolean adding) {
                heard.add(name);
            }

            @Override
            public void changed(String name, boolean adding, Number amount) {
                heard.add(name);
            }

            @Override
            public void appending(String name) {
                heard.add(name);
            }
        });
        stop.run();
        store.add("x", new Object[] {1L});
        store.set("y", 1L);
        store.addToList("l", new Object[] {"a"});
        assertTrue(heard.isEmpty());
    }
}
