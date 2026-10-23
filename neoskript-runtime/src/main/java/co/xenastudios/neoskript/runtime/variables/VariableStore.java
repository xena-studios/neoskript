/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
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
 */
public final class VariableStore {

    /** How list indices sort: numbers by value, then text ignoring case. */
    public static final Comparator<String> INDEX_ORDER = (a, b) -> {
        Double x = number(a);
        Double y = number(b);
        if (x != null && y != null) {
            int byValue = Double.compare(x, y);
            if (byValue != 0) {
                return byValue;
            }
        } else if (x != null) {
            return -1;
        } else if (y != null) {
            return 1;
        }
        return String.CASE_INSENSITIVE_ORDER.compare(a, b);
    };

    private static final String SEPARATOR = "::";

    private static final class Node {
        final AtomicReference<Object> value = new AtomicReference<>();
        volatile ConcurrentSkipListMap<String, Node> children;

        ConcurrentSkipListMap<String, Node> children() {
            ConcurrentSkipListMap<String, Node> map = children;
            if (map == null) {
                synchronized (this) {
                    map = children;
                    if (map == null) {
                        map = new ConcurrentSkipListMap<>(INDEX_ORDER);
                        children = map;
                    }
                }
            }
            return map;
        }
    }

    private final Node root = new Node();
    private final AtomicLong modifications = new AtomicLong();

    /** A counter that goes up with every change, so storage can tell whether there's anything to save. */
    public long modifications() {
        return modifications.get();
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
            Object value = node.value.get();
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

    private void changed() {
        modifications.incrementAndGet();
    }

    /** The value of {@code {name}}, or {@code null} if it's not set. */
    public Object get(String name) {
        Node node = find(name);
        return node == null ? null : node.value.get();
    }

    /** Sets {@code {name}}; {@code null} deletes it. Values under it ({@code {name::*}}) are kept. */
    public void set(String name, Object value) {
        changed();
        if (value == null) {
            Node node = find(name);
            if (node != null) {
                node.value.set(null);
            }
            return;
        }
        create(name).value.set(Values.copy(value));
    }

    /** Atomically replaces {@code {name}} with {@code change} applied to its current value. */
    public void update(String name, UnaryOperator<Object> change) {
        changed();
        create(name).value.updateAndGet(change);
    }

    /** Adds {@code values} to the number in {@code {name}} (an unset variable counts as nothing yet). */
    public void add(String name, Object[] values) {
        // A value with its own way of being added to, like a queue, changes in place.
        Object current = get(name);
        if (current != null && Values.changeValue(current, values, Changeable.Mode.ADD)) {
            changed();
            return;
        }
        update(name, old -> {
            Object result = old;
            for (Object value : values) {
                result = result == null ? value : Arithmetic.add(result, value);
            }
            return result;
        });
    }

    /** Subtracts {@code values} from the number in {@code {name}}. */
    public void subtract(String name, Object[] values) {
        Object current = get(name);
        if (current != null && Values.changeValue(current, values, Changeable.Mode.REMOVE)) {
            changed();
            return;
        }
        update(name, old -> {
            Object result = old == null ? 0L : old;
            for (Object value : values) {
                result = Arithmetic.subtract(result, value);
            }
            return result;
        });
    }

    /** The values directly under {@code {prefix::*}}, in index order. */
    public Object[] list(String prefix) {
        List<Object> values = new ArrayList<>();
        for (Node child : children(prefix)) {
            Object value = child.value.get();
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
            if (child.value.get() != null || (child.children != null && !child.children.isEmpty())) {
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
        Node node = create(prefix);
        int index = 1;
        for (Object value : values) {
            if (value != null) {
                node.children().computeIfAbsent(Integer.toString(index++), k -> new Node()).value.set(Values.copy(value));
            }
        }
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
        Node node = create(prefix);
        for (int i = 0; i < keyed.values().length; i++) {
            if (keyed.values()[i] != null) {
                node.children().computeIfAbsent(keyed.keys()[i], k -> new Node()).value.set(Values.copy(keyed.values()[i]));
            }
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
            Object value = entry.getValue().value.get();
            if (value != null) {
                values.add(value);
                keys.add(entry.getKey());
            }
        }
        return new KeyedValues(values.toArray(), keys.toArray(String[]::new));
    }

    /** Adds {@code values} to {@code {prefix::*}}, each at the lowest free index from the list's size + 1. */
    public void addToList(String prefix, Object[] values) {
        changed();
        Node node = create(prefix);
        synchronized (node) {
            ConcurrentSkipListMap<String, Node> children = node.children();
            int index = children.size() + 1;
            for (Object value : values) {
                if (value == null) {
                    continue;
                }
                while (children.containsKey(Integer.toString(index)) && children.get(Integer.toString(index)).value.get() != null) {
                    index++;
                }
                children.computeIfAbsent(Integer.toString(index), k -> new Node()).value.set(Values.copy(value));
            }
        }
    }

    /** Removes {@code values} from {@code {prefix::*}}: the first match of each, or every match if {@code all}. */
    public void removeFromList(String prefix, Object[] values, boolean all) {
        changed();
        Node node = find(prefix);
        if (node == null || node.children == null) {
            return;
        }
        for (Object value : values) {
            for (Node child : node.children.values()) {
                Object current = child.value.get();
                if (current != null && co.xenastudios.neoskript.runtime.Comparison.equal(current, value)
                        && child.value.compareAndSet(current, null) && !all) {
                    break;
                }
            }
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

    private static void collect(Node node, List<Object> into) {
        for (Node child : node.children.values()) {
            Object value = child.value.get();
            if (value != null) {
                into.add(value);
            }
            if (child.children != null) {
                collect(child, into);
            }
        }
    }

    /** Replaces {@code {to::*}} with a copy of {@code {from::*}}, nested lists and indices included. */
    public void copyList(String from, String to) {
        changed();
        Node source = find(from);
        deleteList(to);
        if (source == null || source.children == null) {
            return;
        }
        copy(source, create(to));
    }

    private static void copy(Node from, Node to) {
        for (Map.Entry<String, Node> entry : from.children.entrySet()) {
            Node target = to.children().computeIfAbsent(entry.getKey(), k -> new Node());
            Object value = entry.getValue().value.get();
            target.value.set(Values.copy(value));
            if (entry.getValue().children != null) {
                copy(entry.getValue(), target);
            }
        }
    }

    public void deleteList(String prefix) {
        changed();
        Node node = find(prefix);
        if (node != null && node.children != null) {
            node.children.clear();
        }
    }

    private Iterable<Node> children(String prefix) {
        Node node = find(prefix);
        return node == null || node.children == null ? List.of() : node.children.values();
    }

    private Node find(String name) {
        Node node = root;
        for (String part : name.split(SEPARATOR, -1)) {
            ConcurrentSkipListMap<String, Node> children = node.children;
            node = children == null ? null : children.get(part);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    private Node create(String name) {
        Node node = root;
        for (String part : name.split(SEPARATOR, -1)) {
            node = node.children().computeIfAbsent(part, k -> new Node());
        }
        return node;
    }

    private static Double number(String text) {
        if (text.isEmpty() || !(Character.isDigit(text.charAt(0)) || text.charAt(0) == '-')) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
