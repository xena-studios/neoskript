/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.runtime.KeyedValues;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Deleted variables, and the lists left empty above them, are taken out of the tree, so they free their memory. */
class VariableStoreFreeTest {

    private static long usedAfterGc() {
        Runtime runtime = Runtime.getRuntime();
        long used = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            System.gc();
            used = Math.min(used, runtime.totalMemory() - runtime.freeMemory());
        }
        return used;
    }

    @Test
    void aMillionUniqueNamesSetAndDeletedLeaveNothingBehind() {
        VariableStore store = new VariableStore();
        store.set("kept::a", 1L);
        store.set("kept::b", "text");
        long nodesBefore = store.nodes();
        // A few thousand first, so what's measured after is only what the million leave.
        churn(store, 10_000);
        long before = usedAfterGc();
        churn(store, 1_000_000);
        long after = usedAfterGc();
        System.out.printf("1,000,000 unique names set and deleted: %,d nodes left, heap %,d KB before, %,d KB after%n",
                store.nodes(), before / 1024, after / 1024);
        assertEquals(nodesBefore, store.nodes());
        // Kept, the 3 million nodes would be hundreds of MB.
        assertTrue(after - before < 16L << 20, "heap grew by " + (after - before) / 1024 + " KB");
        assertEquals(1L, store.get("kept::a"));
        assertArrayEquals(new String[] {"a", "b"}, store.indices("kept"));
    }

    /** What a script that keeps state per event does: {temp::%uuid%::...} set, changed, then deleted. */
    private static void churn(VariableStore store, int count) {
        for (int i = 0; i < count; i++) {
            String id = "temp::" + UUID.randomUUID();
            store.set(id + "::start", (long) i);
            store.add(id + "::hits", new Object[] {1L});
            store.addToList(id + "::seen", new Object[] {"a", "b"});
            store.set("-cache::" + i, i);
            store.set(id + "::start", null);
            store.set(id + "::hits", null);
            store.deleteList(id + "::seen");
            store.set("-cache::" + i, null);
        }
    }

    @Test
    void deletingKeepsWhatsUnderAndAboveAndTheOrder() {
        VariableStore store = new VariableStore();
        store.set("x", 1L);
        store.set("x::b", 2L);
        store.set("x::10", 3L);
        store.set("x::2", 4L);
        store.set("x::a::deep", 5L);
        long nodes = store.nodes();
        // Deleting a variable keeps the list under it.
        store.set("x", null);
        assertNull(store.get("x"));
        assertEquals(2L, store.get("x::b"));
        assertEquals(nodes, store.nodes());
        // A list left with nothing in it goes; the list above with other things in it stays.
        store.set("x::a::deep", null);
        assertEquals(nodes - 2, store.nodes());
        assertArrayEquals(new String[] {"2", "10", "b"}, store.indices("x"));
        // Made again, it sorts where it belongs.
        store.set("x::A::deep", 6L);
        assertArrayEquals(new String[] {"2", "10", "a", "b"}, store.indices("x"));
        assertArrayEquals(new Object[] {4L, 3L, 2L}, store.list("x"));
        // Deleting the last value takes the whole branch out, the top included.
        store.deleteList("x");
        assertEquals(0, store.nodes());
        assertArrayEquals(new String[0], store.indices("x"));
        store.set("x", 7L);
        assertEquals(7L, store.get("x"));
        assertEquals(1, store.nodes());
    }

    @Test
    void addingFindsFreeIndicesAfterDeletes() {
        VariableStore store = new VariableStore();
        store.addToList("l", new Object[] {"a", "b", "c"});
        store.set("l::3", null);
        store.addToList("l", new Object[] {"d"});
        // As Skript's next open index: 3 is free again.
        assertArrayEquals(new String[] {"1", "2", "3"}, store.indices("l"));
        store.set("l::1", null);
        store.addToList("l", new Object[] {"e", "f"});
        // The lowest free index first, then the next free one.
        assertArrayEquals(new Object[] {"e", "b", "d", "f"}, store.list("l"));
        assertArrayEquals(new String[] {"1", "2", "3", "4"}, store.indices("l"));
        store.set("l::4", null);
        store.removeFromList("l", new Object[] {"b", "d", "e"}, true);
        assertEquals(0, store.nodes());
        store.setList("l", new Object[] {"x", new Object[] {null}, "y"});
        assertArrayEquals(new String[] {"1", "3"}, store.indices("l"));
        store.setList("l", new Object[0]);
        assertEquals(0, store.nodes());
        store.setListKeyed("l", new KeyedValues(new Object[] {1L, 2L}, new String[] {"a::b", "c"}));
        assertArrayEquals(new String[] {"a", "c"}, store.indices("l"));
        store.copyList("l", "m");
        store.set("l::a::b", null);
        store.set("l::c", null);
        assertEquals(1L, store.get("m::a::b"));
        assertNull(store.get("l::a::b"));
        // m, m::a, m::a::b and m::c.
        assertEquals(4, store.nodes());
    }

    @Test
    void loopingWhileDeletingSeesWhatWasThere() {
        VariableStore store = new VariableStore();
        store.setList("l", new Object[] {"a", "b", "c"});
        // As Skript 2.17's loop (Variable.keyedIterator): the values are taken when the loop starts, so what's
        // deleted inside it is still looped.
        KeyedValues looped = store.keyedList("l");
        List<Object> seen = new ArrayList<>();
        for (int i = 0; i < looped.keys().length; i++) {
            seen.add(looped.values()[i]);
            store.set("l::" + looped.keys()[i], null);
            store.set("l::3", null);
        }
        assertEquals(List.of("a", "b", "c"), seen);
        assertEquals(0, store.nodes());
    }

    @Test
    void aDeleteIsAChange() {
        VariableStore store = new VariableStore();
        store.set("a::b", 1L);
        long before = store.modifications();
        store.set("a::b", null);
        assertNotEquals(before, store.modifications());
        before = store.modifications();
        store.setList("c", new Object[] {1L});
        store.deleteList("c");
        assertNotEquals(before, store.modifications());
    }

    @Test
    void changesFromManyThreadsAreNeverLostToAListBeingTakenOut() throws InterruptedException {
        VariableStore store = new VariableStore();
        int threads = 8;
        int rounds = 100_000;
        AtomicReference<Throwable> failed = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> running = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < rounds; i++) {
                        // The list above is shared, and goes empty and is taken out all the time.
                        String name = "shared::" + id + "::" + (i & 7);
                        store.set(name, (long) i);
                        Object read = store.get(name);
                        if (!Long.valueOf(i).equals(read)) {
                            throw new AssertionError(name + " was " + read + ", not " + i);
                        }
                        store.add("counter::" + (id & 1) + "::n", new Object[] {1L});
                        store.addToList("shared::" + id + "::list", new Object[] {"x"});
                        store.deleteList("shared::" + id + "::list");
                        store.set(name, null);
                    }
                } catch (Throwable e) {
                    failed.compareAndSet(null, e);
                }
            });
            thread.start();
            running.add(thread);
        }
        start.countDown();
        for (Thread thread : running) {
            thread.join();
        }
        if (failed.get() != null) {
            throw new AssertionError(failed.get());
        }
        long total = ((Number) store.get("counter::0::n")).longValue() + ((Number) store.get("counter::1::n")).longValue();
        assertEquals((long) threads * rounds, total);
        // counter, counter::0, counter::0::n, counter::1 and counter::1::n: every shared::... is gone.
        assertEquals(5, store.nodes());
    }
}
