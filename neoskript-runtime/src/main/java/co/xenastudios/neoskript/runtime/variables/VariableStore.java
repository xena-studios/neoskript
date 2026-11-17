/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.runtime.Arithmetic;
import co.xenastudios.neoskript.runtime.KeyedValues;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

/**
 * Variables, global or local. Names are split on {@code ::} into a tree, so {@code {x::*}} is the
 * values of the nodes directly under {@code x}. As in Skript, names ignore case, and list indices sort
 * numbers by value ({@code 2} before {@code 10}) and everything else alphabetically.
 *
 * <p>Safe to use from several threads. Each single operation is atomic, including {@link #update} (so
 * {@code add 1 to {x}} never loses an increment); sequences of operations aren't.
 *
 * <p>A variable that's deleted is taken out of the tree, and so is each list above it left with nothing in it: a
 * script that sets and deletes {@code {temp::%uuid%::x}} for every event uses no more memory after a million of them
 * than after one. Skript keeps an emptied list's map, which only {@code add} can tell, by skipping its index, so an
 * emptied list at a positive integer index is kept as Skript keeps it ({@link Node#freeable}). Values change without locks. Adding a node to a node's
 * children, or taking one out, holds that node's lock, and taking one out holds its own too (always the parent's
 * first). A node that's taken out is marked {@link #DEAD} first, under both locks and only if it has no value and
 * nothing under it, so a change that found it just before then sees the mark and starts again from the top.
 */
public final class VariableStore {

    /**
     * The order of a list's indices, as Skript sorts them: runs of digits as numbers (so 2 comes before 10,
     * and after a {@code -} in reverse, so -2 comes before -1), decimals digit by digit, more leading zeros
     * after fewer, and anything else character by character, ignoring case.
     */
    public static final Comparator<String> INDEX_ORDER = VariableStore::compareIndices;

    private static int compareIndices(String s1, String s2) {
        int i = 0;
        int j = 0;
        boolean lastNegative = false;
        boolean afterDecimal = false;
        while (i < s1.length() && j < s2.length()) {
            char c1 = s1.charAt(i);
            char c2 = s2.charAt(j);
            if (isDigit(c1) && isDigit(c2)) {
                int end1 = digitsEnd(s1, i);
                int end2 = digitsEnd(s2, j);
                int zeros1 = 0;
                int zeros2 = 0;
                if (!afterDecimal) {
                    while (i < end1 - 1 && s1.charAt(i) == '0') {
                        i++;
                        zeros1++;
                    }
                    while (j < end2 - 1 && s2.charAt(j) == '0') {
                        j++;
                        zeros2++;
                    }
                }
                boolean previousNegative = lastNegative;
                lastNegative = i - zeros1 > 0 && s1.charAt(i - zeros1 - 1) == '-';
                int sign = lastNegative || previousNegative ? -1 : 1;
                if (!afterDecimal && end1 - i != end2 - j) {
                    return ((end1 - i) - (end2 - j)) * sign;
                }
                while (i < end1 && j < end2) {
                    char d1 = s1.charAt(i);
                    char d2 = s2.charAt(j);
                    if (d1 != d2) {
                        return (d1 - d2) * sign;
                    }
                    i++;
                    j++;
                }
                if (afterDecimal && end1 - i != end2 - j) {
                    return ((end1 - i) - (end2 - j)) * sign;
                }
                if (zeros1 != zeros2) {
                    return (zeros1 - zeros2) * sign;
                }
                afterDecimal = false;
            } else {
                char l1 = Character.toLowerCase(c1);
                char l2 = Character.toLowerCase(c2);
                if (l1 != l2) {
                    return l1 - l2;
                }
                afterDecimal = c1 == '.';
                i++;
                j++;
            }
        }
        if (i < s1.length()) {
            return lastNegative ? -1 : 1;
        }
        if (j < s2.length()) {
            return lastNegative ? 1 : -1;
        }
        return 0;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int digitsEnd(String s, int from) {
        int i = from;
        while (i < s.length() && isDigit(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static final String SEPARATOR = "::";

    /**
     * A node's children, in Skript's order of indices ({@link #INDEX_ORDER}) for going through them, with a hash
     * index beside it for finding one: comparing long indices like UUIDs in that order made a lookup most of what a
     * trigger using variables cost. Two indices are the same in that order when they're the same ignoring case, so
     * the index is keyed by the lower-case index. Children are added ({@link Node#child}) and taken out when they're
     * empty ({@link #removeChild}), both of which keep the index, with the lock of the node they're the children of
     * held. {@code delete {x::*}} drops the whole map.
     */
    private static final class Children extends ConcurrentSkipListMap<String, Node> {

        private final java.util.concurrent.ConcurrentHashMap<String, Node> byIndex =
                new java.util.concurrent.ConcurrentHashMap<>();

        Children() {
            super(INDEX_ORDER);
        }

        /** The children in {@code sorted}, which are in {@link #INDEX_ORDER} already, linked in one pass. */
        Children(SortedChildren sorted) {
            super(sorted);
            for (Pending child : sorted.children) {
                byIndex.put(fold(child.key), child.node);
            }
        }

        /** The key {@link #byIndex} has for {@code index}: lower case, character by character, as the order compares. */
        static String fold(String index) {
            for (int i = 0; i < index.length(); i++) {
                char c = index.charAt(i);
                // Most indices are ASCII: only A to Z change there.
                if (c < 0x80 ? c >= 'A' && c <= 'Z' : Character.toLowerCase(c) != c) {
                    char[] chars = index.toCharArray();
                    for (int j = i; j < chars.length; j++) {
                        chars[j] = Character.toLowerCase(chars[j]);
                    }
                    return new String(chars);
                }
            }
            return index;
        }

        @Override
        public Node get(Object key) {
            return key instanceof String index ? byIndex.get(fold(index)) : null;
        }

        @Override
        public boolean containsKey(Object key) {
            return get(key) != null;
        }

        /** In constant time, where the skip list counts its entries. */
        @Override
        public int size() {
            return byIndex.size();
        }

        @Override
        public boolean isEmpty() {
            return byIndex.isEmpty();
        }

        /**
         * Skript's next open index ({@code IndexTrackingTreeMap.nextOpenIndex}): the smallest positive integer that
         * isn't an index here, where {@code add} puts a value. 0 until it's first needed. With the owner's lock held.
         */
        private int nextOpen;

        /** Adds {@code node} at {@code key}, which it doesn't have yet. With the owner's lock held. */
        void addChild(String key, Node node) {
            super.put(key, node);
            byIndex.put(fold(key), node);
            if (nextOpen != 0 && positiveIndex(key) == nextOpen) {
                do {
                    nextOpen++;
                } while (byIndex.containsKey(Integer.toString(nextOpen)));
            }
        }

        /** Takes {@code node} out, if it's the one at {@code key}. With the owner's lock held. */
        void removeChild(String key, Node node) {
            if (byIndex.remove(fold(key), node)) {
                super.remove(key, node);
                int index = positiveIndex(key);
                if (nextOpen != 0 && index > 0 && index < nextOpen) {
                    nextOpen = index;
                }
            }
        }

        /** The index {@code add} puts its next value at. With the owner's lock held. */
        int nextOpen() {
            if (nextOpen == 0) {
                int index = 1;
                while (byIndex.containsKey(Integer.toString(index))) {
                    index++;
                }
                nextOpen = index;
            }
            return nextOpen;
        }

        /**
         * {@code key} as a positive integer, as Skript counts one: digits only, no leading zero, and no more than
         * {@link Integer#MAX_VALUE}. Else -1.
         */
        static int positiveIndex(String key) {
            if (key.isEmpty() || key.charAt(0) == '0' || key.length() > 10) {
                return -1;
            }
            long value = 0;
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                if (c < '0' || c > '9') {
                    return -1;
                }
                value = value * 10 + (c - '0');
            }
            return value > Integer.MAX_VALUE ? -1 : (int) value;
        }

        /** With the owner's lock held. */
        @Override
        public void clear() {
            super.clear();
            byIndex.clear();
            nextOpen = 1;
        }

        @Override
        public Node put(String key, Node value) {
            throw new UnsupportedOperationException("children are added with Node.child");
        }

        @Override
        public Node computeIfAbsent(String key, java.util.function.Function<? super String, ? extends Node> make) {
            throw new UnsupportedOperationException("children are added with Node.child");
        }

        @Override
        public Node remove(Object key) {
            throw new UnsupportedOperationException("children are taken out with removeChild");
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    /** A node {@link #load} is building, with its children in order. */
    private static final class Pending {
        final String key;
        final Node node = new Node();
        final List<Pending> children = new ArrayList<>(0);
        /** The children by index, made the first time one comes out of order. */
        java.util.HashMap<String, Pending> byKey;
        /** Whether a child came out of order, so the children are sorted once before linking. */
        boolean unsorted;

        Pending(String key) {
            this.key = key;
        }

        /** The child at {@code key}: added at the end if it's new. */
        Pending child(String index) {
            if (children.isEmpty() || INDEX_ORDER.compare(children.getLast().key, index) < 0) {
                Pending child = new Pending(index);
                children.add(child);
                if (byKey != null) {
                    byKey.put(index, child);
                }
                return child;
            }
            if (byKey == null) {
                byKey = new java.util.HashMap<>(children.size() * 2);
                for (Pending child : children) {
                    byKey.put(child.key, child);
                }
            }
            Pending found = byKey.get(index);
            if (found == null) {
                found = new Pending(index);
                children.add(found);
                byKey.put(index, found);
                unsorted = true;
            }
            return found;
        }

        /**
         * Gives the node its children, and theirs, in one pass each, sorting them once first if any came out of
         * order. False if two of them are the same in Skript's order but not equal, which only {@link #set} handles.
         */
        boolean link() {
            if (children.isEmpty()) {
                return true;
            }
            if (unsorted) {
                children.sort((a, b) -> INDEX_ORDER.compare(a.key, b.key));
                for (int i = 1; i < children.size(); i++) {
                    if (INDEX_ORDER.compare(children.get(i - 1).key, children.get(i).key) == 0) {
                        return false;
                    }
                }
            }
            byKey = null;
            for (Pending child : children) {
                if (!child.link()) {
                    return false;
                }
            }
            node.children = new Children(new SortedChildren(children));
            return true;
        }
    }

    /** Children already in order, as the sorted map a skip list is built from in one pass. */
    private static final class SortedChildren extends java.util.AbstractMap<String, Node>
            implements java.util.SortedMap<String, Node> {

        final List<Pending> children;

        SortedChildren(List<Pending> children) {
            this.children = children;
        }

        @Override
        public Comparator<? super String> comparator() {
            return INDEX_ORDER;
        }

        @Override
        public java.util.Set<Map.Entry<String, Node>> entrySet() {
            return new java.util.AbstractSet<>() {
                @Override
                public java.util.Iterator<Map.Entry<String, Node>> iterator() {
                    java.util.Iterator<Pending> each = children.iterator();
                    return new java.util.Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return each.hasNext();
                        }

                        @Override
                        public Map.Entry<String, Node> next() {
                            Pending child = each.next();
                            return Map.entry(child.key, child.node);
                        }
                    };
                }

                @Override
                public int size() {
                    return children.size();
                }
            };
        }

        @Override
        public java.util.SortedMap<String, Node> subMap(String from, String to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.SortedMap<String, Node> headMap(String to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.SortedMap<String, Node> tailMap(String from) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String firstKey() {
            return children.getFirst().key;
        }

        @Override
        public String lastKey() {
            return children.getLast().key;
        }
    }

    /**
     * The value of a node that's been taken out of the tree: nothing can be set in it or added under it, so a
     * change that finds it starts again from the top, where a new node is made.
     */
    private static final Object DEAD = new Object();

    private static final class Node {
        /** Its value, {@code null} if it has none, or {@link #DEAD}. */
        final AtomicReference<Object> value = new AtomicReference<>();
        volatile Children children;

        /** Its value, or {@code null}. */
        Object value() {
            Object current = value.get();
            return current == DEAD ? null : current;
        }

        boolean dead() {
            return value.get() == DEAD;
        }

        /**
         * Whether it can be taken out from under {@code key}: it's {@link #empty}, and it isn't an emptied list at a
         * positive integer index. Skript keeps such a list's map when what's in it is deleted one by one, and
         * {@code add} skips its index, so NeoSkript keeps the node, which is what Skript's map is (its
         * {@code children} map is made when it first gets a child). {@code delete {x::1::*}} drops it, as Skript
         * drops the map ({@link #deleteList}). Lists at other indices, like {@code {temp::%uuid%}}, are taken out:
         * nothing can tell.
         */
        boolean freeable(String key) {
            Children map = children;
            if (value.get() != null) {
                return false;
            }
            return map == null || map.isEmpty() && Children.positiveIndex(key) < 0;
        }

        /**
         * The child at {@code key} (in lower case), made if there's none, or {@code null} if this node has been taken
         * out, so the caller starts again from the top.
         */
        Node child(String key) {
            Children map = children;
            if (map != null) {
                Node found = map.get(key);
                if (found != null && !found.dead()) {
                    return found;
                }
            }
            synchronized (this) {
                if (dead()) {
                    return null;
                }
                map = children;
                if (map == null) {
                    map = new Children();
                    children = map;
                }
                // A dead child is taken out with this lock held, so there isn't one here.
                Node found = map.get(key);
                if (found == null) {
                    found = new Node();
                    map.addChild(key, found);
                }
                return found;
            }
        }

        /**
         * {@link #child} for a node whose lock the caller holds, and which it's checked isn't dead: never
         * {@code null}, and the child can't be taken out until the lock is let go.
         */
        Node lockedChild(String key) {
            return child(key);
        }

        /** Sets the value, unless it's been taken out: whether it was set. */
        boolean set(Object newValue) {
            while (true) {
                Object current = value.get();
                if (current == DEAD) {
                    return false;
                }
                if (value.compareAndSet(current, newValue)) {
                    return true;
                }
            }
        }
    }

    private final Node root = new Node();
    private final AtomicLong modifications = new AtomicLong();

    /** A counter that goes up with every change, so storage can tell whether there's anything to save. */
    public long modifications() {
        return modifications.get();
    }

    /**
     * Told of the changes that only add to something: a number added to or taken from, or a value added to a list at
     * its next free index. Storage shared between servers needs these to merge two servers' changes to one variable
     * rather than keep only one of them. Called on the thread that makes the change; each must be quick.
     */
    public interface Additions {

        /** {@code add} or {@code remove} is about to change {@code {name}}: {@link #added} follows, always. */
        void adding(String name);

        /**
         * The change {@link #adding} announced is done. {@code amount} is what it added to the number in
         * {@code {name}}, negative for a removal, if it only added plain numbers ({@code Long} or {@code Double}) to a
         * plain number or to nothing; otherwise {@code null}.
         */
        void added(String name, Number amount);

        /**
         * {@code {name}} is about to be made by adding a value to its list at the list's next free index: told before
         * the value is there, so whoever sees the value can see this too.
         */
        void appending(String name);
    }

    private final java.util.concurrent.CopyOnWriteArrayList<Additions> additions =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Tells {@code watcher} of every change that only adds ({@link Additions}) from now on.
     *
     * @return what stops it
     */
    public Runnable watchAdditions(Additions watcher) {
        additions.add(watcher);
        return () -> additions.remove(watcher);
    }

    private static boolean plainNumber(Object value) {
        return value instanceof Long || value instanceof Double;
    }

    /**
     * Visits every variable that has a value, depth first in index order: each variable, then the ones
     * under it. That's the order Skript saves them in.
     */
    public void forEach(BiConsumer<String, Object> visitor) {
        visit(root, null, visitor);
    }

    private static void visit(Node node, String name, BiConsumer<String, Object> visitor) {
        if (name != null) {
            Object value = node.value();
            if (value != null) {
                visitor.accept(name, value);
            }
        }
        ConcurrentSkipListMap<String, Node> children = node.children;
        if (children != null) {
            for (Map.Entry<String, Node> child : children.entrySet()) {
                visit(child.getValue(), name == null ? child.getKey() : name + SEPARATOR + child.getKey(), visitor);
            }
        }
    }

    /** How many nodes the tree has, the top one aside: what deleting frees. For tests. */
    long nodes() {
        return countNodes(root) - 1;
    }

    private static long countNodes(Node node) {
        long count = 1;
        Children children = node.children;
        if (children != null) {
            for (Node child : children.values()) {
                count += countNodes(child);
            }
        }
        return count;
    }

    /**
     * Sets many variables in a store that has none yet, as loading saved variables does: the same as {@link #set}
     * for each in turn, but each list is linked in one pass instead of having its indices sorted in one at a time,
     * which was most of the time loading took. Names in the order {@link #forEach} gives them, the order a compacted
     * file has, need no sorting; a list whose names come in any other order is sorted once, so it's never slower than
     * {@link #set} one by one. If the store isn't empty, each is {@link #set}. Not for a store other threads are using
     * yet.
     */
    public void load(List<String> names, List<Object> values) {
        ConcurrentSkipListMap<String, Node> existing = root.children;
        Pending top = existing != null && !existing.isEmpty() ? null : tree(names, values);
        if (top == null || !top.link()) {
            for (int i = 0; i < names.size(); i++) {
                set(names.get(i), values.get(i));
            }
            return;
        }
        changed();
        root.children = top.node.children;
    }

    /** The tree of {@code names}, not yet linked. */
    private static Pending tree(List<String> names, List<Object> values) {
        Pending top = new Pending(null);
        List<Pending> path = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int depth = 0;
            int start = 0;
            while (true) {
                int end = name.indexOf(SEPARATOR, start);
                String part = (end < 0 ? name.substring(start) : name.substring(start, end))
                        .toLowerCase(java.util.Locale.ROOT);
                if (depth >= path.size() || !path.get(depth).key.equals(part)) {
                    while (path.size() > depth) {
                        path.removeLast();
                    }
                    path.add((depth == 0 ? top : path.get(depth - 1)).child(part));
                }
                depth++;
                if (end < 0) {
                    break;
                }
                start = end + SEPARATOR.length();
            }
            Object value = values.get(i);
            path.get(depth - 1).node.value.set(value == null ? null : Values.copy(value));
        }
        return top;
    }

    private void changed() {
        modifications.incrementAndGet();
    }

    /** The value of {@code {name}}, or {@code null} if it's not set. */
    public Object get(String name) {
        Node node = find(name);
        return node == null ? null : node.value();
    }

    /** Sets {@code {name}}; {@code null} deletes it. Values under it ({@code {name::*}}) are kept. */
    public void set(String name, Object value) {
        changed();
        if (value == null) {
            // One walk down the name, whose nodes are then taken out bottom up if they're left empty.
            Path path = walk(name);
            if (path != null && path.last().value() != null) {
                path.last().set(null);
                path.prune();
            }
            return;
        }
        Object copy = Values.copy(value);
        while (!create(name).set(copy)) {
            // Taken out just after it was found: made again.
        }
    }

    /** Atomically replaces {@code {name}} with {@code change} applied to its current value. */
    public void update(String name, UnaryOperator<Object> change) {
        changed();
        while (true) {
            Node node = create(name);
            Object current = node.value.get();
            if (current == DEAD) {
                continue;
            }
            Object result = change.apply(current);
            if (node.value.compareAndSet(current, result)) {
                if (result == null) {
                    prune(name);
                }
                return;
            }
        }
    }

    /** Adds {@code values} to the number in {@code {name}} (an unset variable counts as nothing yet). */
    public void add(String name, Object[] values) {
        // A value with its own way of being added to, like a queue, changes in place.
        Object current = get(name);
        if (current != null && Values.changeValue(current, values, Changeable.Mode.ADD)) {
            changed();
            return;
        }
        boolean watched = !additions.isEmpty();
        Object[] amount = watched ? new Object[1] : null;
        if (watched) {
            for (Additions watcher : additions) {
                watcher.adding(name);
            }
        }
        try {
            update(name, old -> {
                Object result = old;
                // What was added, while it's only plain numbers to a plain number (or to nothing).
                Object added = watched && (old == null || plainNumber(old)) ? 0L : null;
                for (Object value : values) {
                    if (Arithmetic.unchangedBy('+', old, value)) {
                        continue; // 'add 1 to {_money}', as in Skript
                    }
                    if (result == null) {
                        result = value;
                        added = added != null && plainNumber(value) ? Arithmetic.add(added, value) : null;
                        continue;
                    }
                    // As Skript, a value the current one can't be added to is skipped: 'add 1 to {_text}' leaves it.
                    Object sum = Arithmetic.add(result, value);
                    if (sum != null) {
                        added = added != null && plainNumber(value) ? Arithmetic.add(added, value) : null;
                    }
                    result = sum == null ? result : sum;
                }
                if (watched) {
                    amount[0] = added;
                }
                return result;
            });
        } finally {
            if (watched) {
                Number number = amount[0] instanceof Number n ? n : null;
                for (Additions watcher : additions) {
                    watcher.added(name, number);
                }
            }
        }
    }

    /** Subtracts {@code values} from the number in {@code {name}}. */
    /**
     * {@code remove all X from {name}}: all of them out of the value with its class's changer ('remove all
     * diamonds from {_inventory}'), or else as {@link #subtract}.
     */
    public void subtractAll(String name, Object[] values) {
        Object current = get(name);
        if (current != null && Values.changeValue(current, values, Changeable.Mode.REMOVE_ALL)) {
            changed();
            return;
        }
        subtract(name, values);
    }

    public void subtract(String name, Object[] values) {
        Object current = get(name);
        if (current != null && Values.changeValue(current, values, Changeable.Mode.REMOVE)) {
            changed();
            return;
        }
        boolean watched = !additions.isEmpty();
        Object[] amount = watched ? new Object[1] : null;
        if (watched) {
            for (Additions watcher : additions) {
                watcher.adding(name);
            }
        }
        try {
            update(name, old -> {
                Object result = old == null ? 0L : old;
                Object taken = watched && (old == null || plainNumber(old)) ? 0L : null;
                for (Object value : values) {
                    if (Arithmetic.unchangedBy('-', old, value)) {
                        continue;
                    }
                    Object difference = Arithmetic.subtract(result, value);
                    if (difference != null) {
                        taken = taken != null && plainNumber(value) ? Arithmetic.subtract(taken, value) : null;
                    }
                    result = difference == null ? result : difference;
                }
                if (watched) {
                    amount[0] = taken;
                }
                return result;
            });
        } finally {
            if (watched) {
                Number number = amount[0] instanceof Number n ? n : null;
                for (Additions watcher : additions) {
                    watcher.added(name, number);
                }
            }
        }
    }

    /** The values directly under {@code {prefix::*}}, in index order. */
    public Object[] list(String prefix) {
        List<Object> values = new ArrayList<>();
        for (Node child : children(prefix)) {
            Object value = child.value();
            if (value != null) {
                values.add(value);
            }
        }
        return values.toArray();
    }

    /** The indices of the values directly under {@code {prefix::*}}, in the same order as {@link #list}. */
    public String[] indices(String prefix) {
        Node node = find(prefix);
        if (node == null || node.children == null) {
            return new String[0];
        }
        List<String> indices = new ArrayList<>();
        // Indices with a value or a list under them, as in Skript: {_list::c::e} makes c an index of {_list::*}.
        for (Map.Entry<String, Node> entry : node.children.entrySet()) {
            Node child = entry.getValue();
            if (child.value() != null || (child.children != null && !child.children.isEmpty())) {
                indices.add(entry.getKey());
            }
        }
        return indices.toArray(String[]::new);
    }

    /**
     * The indices of the values directly under {@code {prefix::*}}, ordered by their values:
     * {@code sorted indices of {x::*} in descending order}.
     */
    public String[] sortedIndices(String prefix, boolean descending) {
        // Only indices with a value of their own are sorted, as in Skript: a list under an index has none.
        List<String> valued = new ArrayList<>();
        List<Object> found = new ArrayList<>();
        for (String index : indices(prefix)) {
            Object value = get(prefix + "::" + index);
            if (value != null) {
                valued.add(index);
                found.add(value);
            }
        }
        String[] indices = valued.toArray(String[]::new);
        Object[] values = found.toArray();
        Integer[] order = new Integer[indices.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (x, y) -> {
            Integer c = co.xenastudios.neoskript.runtime.Comparison.order(values[x], values[y]);
            int result = c == null ? 0 : c;
            return descending ? -result : result;
        });
        String[] sorted = new String[indices.length];
        for (int i = 0; i < order.length; i++) {
            sorted[i] = indices[order[i]];
        }
        return sorted;
    }

    /** Sorts the values directly under {@code {prefix::*}}, which end up at indices 1, 2, 3... */
    public void sortList(String prefix, boolean descending) {
        Object[] values = list(prefix);
        java.util.Arrays.sort(values, (x, y) -> {
            Integer c = co.xenastudios.neoskript.runtime.Comparison.order(x, y);
            int result = c == null ? 0 : c;
            return descending ? -result : result;
        });
        setList(prefix, values);
    }

    /** Replaces {@code {prefix::*}} with {@code values}, at indices 1, 2, 3... */
    public void setList(String prefix, Object[] values) {
        changed();
        deleteList(prefix);
        if (!hasValue(values)) {
            return;
        }
        while (true) {
            Node node = create(prefix);
            synchronized (node) {
                if (node.dead()) {
                    continue;
                }
                int index = 1;
                for (Object value : values) {
                    if (value instanceof Object[] group) {
                        // Several values in one place are a list there, as Skript sets {x::1::1}, {x::1::2}...
                        if (!hasValue(group)) {
                            // Nothing there, as Skript sets nothing: no empty list left behind.
                            index++;
                            continue;
                        }
                        Node sublist = node.lockedChild(Integer.toString(index++));
                        synchronized (sublist) {
                            int inner = 1;
                            for (Object each : group) {
                                if (each != null) {
                                    sublist.lockedChild(Integer.toString(inner++)).value.set(Values.copy(each));
                                }
                            }
                        }
                    } else if (value != null) {
                        node.lockedChild(Integer.toString(index++)).value.set(Values.copy(value));
                    }
                }
                return;
            }
        }
    }

    private static boolean hasValue(Object[] values) {
        for (Object value : values) {
            if (value instanceof Object[] group ? hasValue(group) : value != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets {@code {prefix::*}} to {@code values}: keyed values ({@link KeyedValues}) at their own indices,
     * anything else numbered from 1 as {@link #setList} does.
     */
    public void setListKeyed(String prefix, Object values) {
        if (!(values instanceof KeyedValues keyed)) {
            setList(prefix, values instanceof Object[] array ? array : values == null ? new Object[0] : new Object[] {values});
            return;
        }
        changed();
        deleteList(prefix);
        if (!hasValue(keyed.values())) {
            return;
        }
        while (true) {
            Node node = create(prefix);
            synchronized (node) {
                if (node.dead()) {
                    continue;
                }
                for (int i = 0; i < keyed.values().length; i++) {
                    if (keyed.values()[i] == null) {
                        continue;
                    }
                    String key = keyed.keys()[i].toLowerCase(java.util.Locale.ROOT);
                    Object value = Values.copy(keyed.values()[i]);
                    if (!key.contains(SEPARATOR)) {
                        node.lockedChild(key).value.set(value);
                        continue;
                    }
                    // A path ('a::b::c', from a recursive list) goes into the lists under the index. Nothing under
                    // this node can be taken out while its lock is held, but deeper nodes can, so it's set as set does.
                    while (!createUnder(node, key).set(value)) {
                        // Taken out just after it was found: made again.
                    }
                }
                return;
            }
        }
    }

    /**
     * Puts an update's results back into {@code {prefix::*}} at the indices they came from, as Skript does:
     * {@code replace "a" in {_list::*} with "b"} keeps the list's indices. {@code keys} are the list's indices
     * when it was read; values that weren't given to the update (not of type {@code only}) stay as they are.
     * If the results don't line up with the values given, the list is set to them, numbered from 1. A
     * {@link Map} sets the indices it names ({@link KeyedValues#result}) and leaves the others.
     */
    public void updateList(String prefix, String[] keys, Object result, Class<?> only) {
        if (result instanceof Map<?, ?> map) {
            // An addon's map: its values go in at the indices it names, and the rest of the list stays.
            KeyedValues keyed = (KeyedValues) KeyedValues.result(map);
            for (int i = 0; i < keyed.values().length; i++) {
                set(prefix + SEPARATOR + keyed.keys()[i].toLowerCase(java.util.Locale.ROOT), keyed.values()[i]);
            }
            return;
        }
        Object[] results = result instanceof Object[] array ? array : result == null ? new Object[0] : new Object[] {result};
        List<String> given = new ArrayList<>(keys.length);
        for (String key : keys) {
            Object value = get(prefix + SEPARATOR + key);
            if (value != null && (only == null || only.isInstance(value))) {
                given.add(key);
            }
        }
        if (given.size() != results.length) {
            setList(prefix, results);
            return;
        }
        changed();
        for (int i = 0; i < results.length; i++) {
            set(prefix + SEPARATOR + given.get(i), results[i]);
        }
    }

    /** The values directly under {@code {prefix::*}} with their indices, in index order. */
    public KeyedValues keyedList(String prefix) {
        Node node = find(prefix);
        if (node == null || node.children == null) {
            return new KeyedValues(new Object[0], new String[0]);
        }
        List<Object> values = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Node> entry : node.children.entrySet()) {
            Object value = entry.getValue().value();
            if (value != null) {
                values.add(value);
                keys.add(entry.getKey());
            }
        }
        return new KeyedValues(values.toArray(), keys.toArray(String[]::new));
    }

    /**
     * Adds {@code values} to {@code {prefix::*}}, each at the smallest positive integer index the list doesn't have,
     * as Skript 2.17 adds (its {@code IndexTrackingTreeMap}): after deleting {@code {x::1}}, the next is added there.
     */
    public void addToList(String prefix, Object[] values) {
        changed();
        if (!hasValue(values)) {
            return;
        }
        while (true) {
            Node node = create(prefix);
            synchronized (node) {
                if (node.dead()) {
                    continue;
                }
                boolean watched = !additions.isEmpty();
                for (Object value : values) {
                    if (value == null) {
                        continue;
                    }
                    Children children = node.children;
                    int index = children == null ? 1 : children.nextOpen();
                    if (watched) {
                        String made = prefix + SEPARATOR + index;
                        for (Additions watcher : additions) {
                            watcher.appending(made);
                        }
                    }
                    node.lockedChild(Integer.toString(index)).value.set(Values.copy(value));
                }
                return;
            }
        }
    }

    /** Removes {@code values} from {@code {prefix::*}}: the first match of each, or every match if {@code all}. */
    public void removeFromList(String prefix, Object[] values, boolean all) {
        changed();
        Path path = walk(prefix);
        Node node = path == null ? null : path.last();
        if (node == null || node.children == null) {
            return;
        }
        boolean removed = false;
        for (Object value : values) {
            for (Map.Entry<String, Node> entry : node.children.entrySet()) {
                Node child = entry.getValue();
                Object current = child.value.get();
                if (current != null && current != DEAD
                        && co.xenastudios.neoskript.runtime.Comparison.equal(current, value)
                        && child.value.compareAndSet(current, null)) {
                    // Taken out as it's emptied, so the list isn't gone through again.
                    removeIfEmpty(node, entry.getKey(), child);
                    removed = true;
                    if (!all) {
                        break;
                    }
                }
            }
        }
        if (removed) {
            path.prune();
        }
    }

    /** Deletes everything under {@code {prefix::*}}. */
    /** Every value under {@code {prefix::*}}, nested lists included, in index order: {@code recursive {x::*}}. */
    public Object[] recursiveList(String prefix) {
        List<Object> values = new ArrayList<>();
        Node node = find(prefix);
        if (node != null && node.children != null) {
            collect(node, values);
        }
        return values.toArray();
    }

    /** Every value under {@code node}: each child's own list before the child's value, as Skript lists them. */
    private static void collect(Node node, List<Object> into) {
        for (Node child : node.children.values()) {
            if (child.children != null) {
                collect(child, into);
            }
            Object value = child.value();
            if (value != null) {
                into.add(value);
            }
        }
    }

    /** Every value under {@code {prefix::*}} with its path below it ({@code a::b::c}), in {@link #recursiveList} order. */
    public KeyedValues recursiveKeyedList(String prefix) {
        List<Object> values = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        Node node = find(prefix);
        if (node != null && node.children != null) {
            collectKeyed(node, "", values, keys);
        }
        return new KeyedValues(values.toArray(), keys.toArray(String[]::new));
    }

    private static void collectKeyed(Node node, String path, List<Object> values, List<String> keys) {
        for (Map.Entry<String, Node> entry : node.children.entrySet()) {
            String key = path.isEmpty() ? entry.getKey() : path + SEPARATOR + entry.getKey();
            Node child = entry.getValue();
            if (child.children != null) {
                collectKeyed(child, key, values, keys);
            }
            Object value = child.value();
            if (value != null) {
                values.add(value);
                keys.add(key);
            }
        }
    }

    /** Replaces {@code {to::*}} with a copy of {@code {from::*}}, nested lists and indices included. */
    public void copyList(String from, String to) {
        changed();
        Node source = find(from);
        deleteList(to);
        if (source == null || source.children == null || source.children.isEmpty()) {
            return;
        }
        while (true) {
            Node target = create(to);
            synchronized (target) {
                if (target.dead()) {
                    continue;
                }
                copy(source, target);
                return;
            }
        }
    }

    /**
     * A copy of every variable, as Skript's {@code copyLocalVariables}: what a delayed section runs with, so
     * changes after it's scheduled don't reach it.
     */
    public VariableStore copy() {
        VariableStore copy = new VariableStore();
        copy.root.value.set(Values.copy(root.value()));
        if (root.children != null) {
            synchronized (copy.root) {
                copy(root, copy.root);
            }
        }
        return copy;
    }

    /**
     * Copies what's under {@code from} to under {@code to}, whose lock the caller holds. Nodes with nothing in them
     * or under them, which are about to be taken out, aren't copied.
     */
    private static void copy(Node from, Node to) {
        for (Map.Entry<String, Node> entry : from.children.entrySet()) {
            Node source = entry.getValue();
            if (source.freeable(entry.getKey())) {
                continue;
            }
            Object value = source.value();
            Children below = source.children;
            Node target = to.lockedChild(entry.getKey());
            target.value.set(Values.copy(value));
            if (below != null) {
                synchronized (target) {
                    if (target.children == null) {
                        // An emptied list Skript keeps the map of (Node#freeable): kept in the copy too.
                        target.children = new Children();
                    }
                    copy(source, target);
                }
            }
        }
    }

    /**
     * Deletes everything under {@code {prefix::*}}. The list's map goes too, as Skript drops it: an index that's no
     * longer a list is free for {@code add} again once it has no value ({@link Node#freeable}).
     */
    public void deleteList(String prefix) {
        changed();
        Path path = walk(prefix);
        if (path == null || path.last().children == null) {
            return;
        }
        Node node = path.last();
        synchronized (node) {
            // Changes that found a child before this still change it, out of the tree, as a clear did.
            node.children = null;
        }
        path.prune();
    }

    private Iterable<Node> children(String prefix) {
        Node node = find(prefix);
        return node == null || node.children == null ? List.of() : node.children.values();
    }

    private Node find(String name) {
        Node node = root;
        // Split on '::' by hand: String.split compiles a regex for a separator longer than a character, which
        // was half of what a trigger using list variables cost.
        int start = 0;
        while (true) {
            int end = name.indexOf(SEPARATOR, start);
            String part = end < 0 ? name.substring(start) : name.substring(start, end);
            Children children = node.children;
            node = children == null ? null : children.get(part);
            if (node == null || end < 0) {
                return node;
            }
            start = end + SEPARATOR.length();
        }
    }

    /**
     * The node of {@code {name}}, made if there's none (with the lists above it). It may be taken out again as soon
     * as it's returned, if it's empty: a change to it that finds it {@link #DEAD} calls this again.
     */
    private Node create(String name) {
        while (true) {
            Node node = createUnder(root, name);
            if (node != null) {
                return node;
            }
            // A list on the way was taken out as it was gone through: from the top again.
        }
    }

    /** The node at {@code path} under {@code node}, made if there's none, or {@code null} if one on the way is dead. */
    private static Node createUnder(Node node, String path) {
        int start = 0;
        while (node != null) {
            int end = path.indexOf(SEPARATOR, start);
            String part = end < 0 ? path.substring(start) : path.substring(start, end);
            // Names are kept in lower case, as Skript keeps them: {_list::Hello} is at index "hello".
            node = node.child(part.toLowerCase(java.util.Locale.ROOT));
            if (end < 0) {
                return node;
            }
            start = end + SEPARATOR.length();
        }
        return null;
    }

    /** The nodes along a name, from the top one's child down to the name's own. */
    private final class Path {
        Node[] nodes = new Node[4];
        String[] keys = new String[4];
        int depth;

        Node last() {
            return nodes[depth - 1];
        }

        void add(Node node, String key) {
            if (depth == nodes.length) {
                nodes = java.util.Arrays.copyOf(nodes, depth * 2);
                keys = java.util.Arrays.copyOf(keys, depth * 2);
            }
            nodes[depth] = node;
            keys[depth] = key;
            depth++;
        }

        /**
         * Takes the name's node out of the tree if it has no value and nothing under it, then each list above it
         * left with nothing in it, bottom up, so deleted variables don't keep using memory.
         */
        void prune() {
            for (int i = depth - 1; i >= 0; i--) {
                if (!removeIfEmpty(i == 0 ? root : nodes[i - 1], keys[i], nodes[i])) {
                    return;
                }
            }
        }
    }

    /** The nodes along {@code name}, as {@link #find} goes down it, or {@code null} if it isn't there. */
    private Path walk(String name) {
        Path path = new Path();
        Node node = root;
        int start = 0;
        while (true) {
            int end = name.indexOf(SEPARATOR, start);
            String part = end < 0 ? name.substring(start) : name.substring(start, end);
            Children children = node.children;
            node = children == null ? null : children.get(part);
            if (node == null) {
                return null;
            }
            path.add(node, part);
            if (end < 0) {
                return path;
            }
            start = end + SEPARATOR.length();
        }
    }

    /** {@link Path#prune} for {@code name}. */
    private void prune(String name) {
        Path path = walk(name);
        if (path != null) {
            path.prune();
        }
    }

    /**
     * Takes {@code child} out of {@code parent}'s children at {@code key} if it's still there, has no value and
     * nothing under it: marked {@link #DEAD} first, with both locks held. Whether it was taken out.
     */
    private static boolean removeIfEmpty(Node parent, String key, Node child) {
        if (!child.freeable(key)) {
            return false;
        }
        synchronized (parent) {
            Children children = parent.children;
            if (children == null || children.get(key) != child) {
                return false;
            }
            synchronized (child) {
                if (!child.freeable(key) || !child.value.compareAndSet(null, DEAD)) {
                    return false;
                }
            }
            children.removeChild(key, child);
            return true;
        }
    }
}
