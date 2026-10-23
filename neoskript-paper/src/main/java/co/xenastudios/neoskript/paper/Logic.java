/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.KeyAware;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import org.bukkit.inventory.Inventory;
import co.xenastudios.neoskript.runtime.Comparison;
import co.xenastudios.neoskript.runtime.KeyedValues;
import co.xenastudios.neoskript.runtime.Values;
import java.util.List;

/** Comparisons, whether something is set, and counting. */
final class Logic {

    private enum Relation {
        EQUAL,
        NOT_EQUAL,
        GREATER,
        GREATER_OR_EQUAL,
        LESS,
        LESS_OR_EQUAL
    }

    private Logic() {}

    private static int number(Object value) {
        return value instanceof Number n ? (int) Math.round(n.doubleValue()) : 0;
    }

    /** Compares versions like {@code 1.21.4} and {@code 26.3} part by part. */
    static int compareVersions(String a, String b) {
        String[] x = a.split("[.\\-]");
        String[] y = b.split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? number(x[i]) : 0;
            int q = i < y.length ? number(y[i]) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }

    private static int number(String part) {
        try {
            return Integer.parseInt(part.replaceAll("\\D.*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static void register(Registrar registrar) {
        registrar.condition("CondCompare", info -> {
            Relation relation = relation(info.tags());
            boolean negated = info.hasTag("not");
            boolean leftAnd = info.and(0);
            return (context, args) -> negated != holds((Object[]) args[0], leftAnd, (Object[]) args[1], relation);
        }, "%objects% (is|are)[(not:n't|not: not)] (ge:(greater|more|higher|bigger|larger) than or (equal to|the same as)"
                + "|gt:(greater|more|higher|bigger|larger) than|gt:above"
                + "|le:(less|smaller|lower) than or (equal to|the same as)|lt:(less|smaller|lower) than|lt:below"
                + "|eq:[equal to|the same as]) %objects%",
                "%objects% (ge:\\>=|gt:\\>|le:\\<=|ne:\\<\\>|lt:\\<|eq:=|ne:!=) %objects%");

        registrar.condition("CondIsSet", info -> {
            boolean negated = info.hasTag("not");
            return (context, args) -> negated == (((Object[]) args[0]).length == 0);
        }, "%~objects% (exist[s]|(is|are) set)",
                "%~objects% (not:do[es](n't| not) exist|not:(is|are)(n't| not) set)");

        registrar.expression("ExprTimes", "integer", info -> (context, args) -> {
            long count = Math.max(0, ((Number) args[0]).longValue());
            Object[] numbers = new Object[(int) Math.min(count, Integer.MAX_VALUE)];
            for (int i = 0; i < numbers.length; i++) {
                numbers[i] = (long) i + 1;
            }
            return numbers;
        }, "%number% times");

        registrar.expression("ExprNumbers", "number", info -> (context, args) ->
                range(((Number) args[0]).doubleValue(), ((Number) args[1]).doubleValue(), info.hasTag("integers")),
                "[(all [[of] the]|the)] (numbers|integers:integers) (between|from) %number% (and|to) %number%");

        registrar.condition("CondPluginEnabled", info -> {
            boolean negated = info.hasTag("not");
            return (context, args) -> {
                for (Object name : (Object[]) args[0]) {
                    if (negated == org.bukkit.Bukkit.getPluginManager().isPluginEnabled(String.valueOf(name))) {
                        return false;
                    }
                }
                return true;
            };
        }, "plugin[s] %strings% (is|are) enabled", "plugin[s] %strings% (not:(is|are)(n't| not) enabled)");

        registrar.condition("CondContains", info -> {
            boolean negated = info.hasTag("not");
            boolean containersAnd = info.and(0);
            boolean valuesAnd = info.and(1);
            return (context, args) -> negated != contains((Object[]) args[0], containersAnd, (Object[]) args[1], valuesAnd);
        }, "%objects% contain[s] %objects%", "%objects% (not:(doesn't|does not|do not|don't) contain) %objects%");

        // Before elements, as in Skript: 'size of 10 random elements of X' is the size of the elements.
        registrar.expression("ExprAmount", "integer", info -> {
            boolean list = info.isList(0);
            return (context, args) -> {
                Object[] values = (Object[]) args[0];
                // The size of a queue is how many elements it has; the amount of one item, how many of it.
                if (values.length == 1 && values[0] instanceof java.util.Collection<?> collection) {
                    return (long) collection.size();
                }
                if (!list && values.length == 1 && values[0] instanceof ItemType item) {
                    return (long) item.amount();
                }
                return (long) values.length;
            };
        },
                "[the] (amount|number|size) of %objects%");
        registrar.expression("ExprElement", "object", info -> new Element(info.pattern(), info.hasTag("last"),
                info.hasTag("tolast"), info.hasTag("distinct")),
                "[the] (first|last:last) element [out] (of|in) %objects%",
                "[the] (first|last:last) %integer% elements [out] (of|in) %objects%",
                "[a] random element [out] (of|in) %objects%",
                "%integer% random [distinct:distinct] elements [out] (of|in) %objects%",
                "[the] %integer%(st|nd|rd|th) [tolast:[to] last] element [out] (of|in) %objects%",
                "[the] elements (from|between) %integer% (to|and) %integer% [out] (of|in) %objects%");

        // 'arg-1 ? "home"': the first values, or the others if there are none.
        registrar.expression("ExprDefaultValue", "object", info -> (context, args) ->
                ((Object[]) args[0]).length > 0 ? args[0] : args[1], "%objects% (otherwise|?) %objects%");
        registrar.expression("LitInfinity", "number", info -> (context, args) -> Double.POSITIVE_INFINITY,
                "positive (infinity|\u221e) [value]", "\u221e [value]", "infinity value", "value of [positive] (infinity|\u221e)");
        registrar.expression("LitNegativeInfinity", "number", info -> (context, args) -> Double.NEGATIVE_INFINITY,
                "negative (infinity|\u221e) [value]", "-(infinity|\u221e) [value]", "value of negative (infinity|\u221e)");
        registrar.expression("LitNaN", "number", info -> (context, args) -> Double.NaN, "NaN [value]", "value of NaN");
        registrar.expression("ExprRandomNumber", "number", info -> {
            boolean integers = info.hasTag("integer");
            return (context, args) -> {
                if (!(args[1] instanceof Number a) || !(args[2] instanceof Number b)) {
                    return null;
                }
                int count = args[0] instanceof Number n ? (int) Math.max(0, Math.round(n.doubleValue())) : 1;
                double low = Math.min(a.doubleValue(), b.doubleValue());
                double high = Math.max(a.doubleValue(), b.doubleValue());
                java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                Object[] numbers = new Object[count];
                for (int i = 0; i < count; i++) {
                    if (integers) {
                        long from = (long) Math.ceil(low);
                        long to = (long) Math.floor(high);
                        if (from > to) {
                            // No whole number between them, like between 1.5 and 1.6.
                            return null;
                        }
                        numbers[i] = from + random.nextLong(to - from + 1);
                    } else {
                        numbers[i] = low + random.nextDouble() * (high - low);
                    }
                }
                return numbers.length == 1 ? numbers[0] : numbers;
            };
        }, "[a|%integer%] random (integer:integer|number)[s] (from|between) %number% (to|and) %number%");
        registrar.condition("CondMinecraftVersion", info -> {
            boolean below = info.hasTag("below");
            return (context, args) -> {
                int order = compareVersions(org.bukkit.Bukkit.getMinecraftVersion(), Values.text(args[0]));
                return below ? order < 0 : order >= 0;
            };
        }, "running [below:below] minecraft %text%");
        registrar.condition("CondStartsEndsWith", info -> {
            boolean negated = info.pattern() == 1;
            boolean ends = info.hasTag("end");
            boolean textsAnd = info.and(0);
            boolean affixesAnd = info.and(1);
            return (context, args) -> negated != Condition.check((Object[]) args[0], textsAnd, text ->
                    Condition.check((Object[]) args[1], affixesAnd, affix -> ends
                            ? Values.text(text).endsWith(Values.text(affix))
                            : Values.text(text).startsWith(Values.text(affix))));
        }, "%texts% (start|end:end)[s] with %texts%", "%texts% (doesn't|does not|do not|don't) (start|end:end) with %texts%");
        registrar.condition("CondIsDivisibleBy", info -> {
            boolean negated = info.pattern() % 2 == 1;
            return (context, args) -> {
                Object[] numbers = (Object[]) args[0];
                if (numbers.length == 0 || !(args[1] instanceof Number divisor)) {
                    return false;
                }
                double tolerance = args[2] instanceof Number t ? t.doubleValue() : 1e-10;
                for (Object number : numbers) {
                    if (!(number instanceof Number n)) {
                        return false;
                    }
                    double remainder = Math.abs(Math.IEEEremainder(n.doubleValue(), divisor.doubleValue()));
                    if ((remainder <= tolerance) == negated) {
                        return false;
                    }
                }
                return true;
            };
        }, "%numbers% (is|are) evenly divisible by %number% [with [a] tolerance [of] %number%]",
                "%numbers% (isn't|is not|aren't|are not) evenly divisible by %number% [with [a] tolerance [of] %number%]",
                "%numbers% can be evenly divided by %number% [with [a] tolerance [of] %number%]",
                "%numbers% (can't|can[ ]not) be evenly divided by %number% [with [a] tolerance [of] %number%]");
        registrar.expression("ExprExcept", "object", info -> (context, args) -> {
            List<Object> kept = new java.util.ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                boolean excluded = false;
                for (Object other : (Object[]) args[1]) {
                    excluded |= Comparison.equal(value, other);
                }
                if (!excluded) {
                    kept.add(value);
                }
            }
            return kept.toArray();
        }, "%objects% (except|excluding|not including) %objects%");
        registrar.expression("ExprAngle", "number", info -> {
            boolean radians = info.hasTag("rad");
            return (context, args) -> {
                List<Object> angles = new java.util.ArrayList<>();
                for (Object value : Values.array(args[0])) {
                    if (value instanceof Number n) {
                        angles.add(radians ? Math.toDegrees(n.doubleValue()) : n.doubleValue());
                    }
                }
                return angles.size() == 1 ? angles.getFirst() : angles.toArray();
            };
        }, "%number% [in] deg[ree][s]", "%number% [in] rad:rad[ian][s]", "%numbers% in deg[ree][s]", "%numbers% in rad:rad[ian][s]");
        registrar.expression("ExprSortedList", "object", info -> new SortedList(), "sorted %objects%");
        // 'keyed {_list::*}': the list's values, which keep their indices where that matters.
        registrar.expression("ExprKeyed", "object", info -> (context, args) -> args[0], "keyed %~objects%");
    }

    /**
     * Whether the containers contain the values, as Skript checks it: inventories hold the items; texts
     * contain the texts (or, failing that, the values are among them); anything else, the values are among
     * the containers' values.
     */
    private static boolean contains(Object[] containers, boolean containersAnd, Object[] values, boolean valuesAnd) {
        if (containers.length == 0) {
            return false;
        }
        if (java.util.Arrays.stream(containers).allMatch(Inventory.class::isInstance)) {
            return Condition.check(containers, containersAnd, inventory -> Condition.check(values, valuesAnd,
                    item -> Items.contains((Inventory) inventory, item)));
        }
        if (java.util.Arrays.stream(containers).allMatch(String.class::isInstance)
                && Condition.check(containers, containersAnd, container -> Condition.check(values, valuesAnd,
                        value -> value instanceof String text && ((String) container).toLowerCase(java.util.Locale.ROOT)
                                .contains(text.toLowerCase(java.util.Locale.ROOT))))) {
            return true;
        }
        return Condition.check(values, valuesAnd, value -> {
            for (Object container : containers) {
                if (Comparison.equal(value, container)) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * The numbers from {@code from} to {@code to}, counting up or down by one. Integers are rounded inward
     * first; otherwise whole bounds give whole numbers.
     */
    private static Object[] range(double from, double to, boolean integers) {
        boolean down = from > to;
        double start = integers ? (down ? Math.floor(from) : Math.ceil(from)) : from;
        double end = integers ? (down ? Math.ceil(to) : Math.floor(to)) : to;
        boolean whole = start == Math.rint(start);
        int count = (int) Math.min(Math.floor(Math.abs(end - start)) + 1, 1_000_000);
        Object[] numbers = new Object[Math.max(count, 0)];
        for (int i = 0; i < numbers.length; i++) {
            double value = down ? start - i : start + i;
            numbers[i] = whole ? (Object) (long) value : (Object) value;
        }
        return numbers;
    }

    private static Relation relation(java.util.Set<String> tags) {
        if (tags.contains("ge")) {
            return Relation.GREATER_OR_EQUAL;
        } else if (tags.contains("gt")) {
            return Relation.GREATER;
        } else if (tags.contains("le")) {
            return Relation.LESS_OR_EQUAL;
        } else if (tags.contains("lt")) {
            return Relation.LESS;
        } else if (tags.contains("ne")) {
            return Relation.NOT_EQUAL;
        }
        return Relation.EQUAL;
    }

    /** Every value on the left relates to at least one on the right. Nothing on either side is false. */
    private static boolean holds(Object[] left, boolean leftAnd, Object[] right, Relation relation) {
        if (right.length == 0) {
            return false;
        }
        return Condition.check(left, leftAnd, a -> {
            for (Object b : right) {
                if (holds(a, b, relation)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static boolean holds(Object a, Object b, Relation relation) {
        if (relation == Relation.EQUAL) {
            return Comparison.equal(a, b);
        }
        if (relation == Relation.NOT_EQUAL) {
            return !Comparison.equal(a, b);
        }
        Integer order = Comparison.order(a, b);
        if (order == null) {
            return false;
        }
        return switch (relation) {
            case GREATER -> order > 0;
            case GREATER_OR_EQUAL -> order >= 0;
            case LESS -> order < 0;
            default -> order <= 0;
        };
    }

    /**
     * {@code first 2 elements of %objects%} and the like. Elements of a queue are taken out of it; elements of
     * keyed values keep their indices.
     */
    private record Element(int pattern, boolean last, boolean toLast, boolean distinct) implements Expression, KeyAware {
        @Override
        public Object get(TriggerContext context, Object[] args) {
            Object source = args[args.length - 1];
            KeyedValues keyed = source instanceof KeyedValues k ? k : null;
            Object[] values = keyed != null ? keyed.values() : Values.array(source);
            // A queue gives its elements up: the first element of a queue is taken out of it.
            Collections.ScriptQueue queue = values.length == 1 && values[0] instanceof Collections.ScriptQueue q ? q : null;
            if (queue != null) {
                values = queue.toArray();
            }
            int count = values.length;
            java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
            switch (pattern) {
                case 0 -> {
                    if (count == 0) {
                        return null;
                    }
                    if (queue != null) {
                        return last ? queue.pollLast() : queue.pollFirst();
                    }
                    return last ? values[count - 1] : values[0];
                }
                case 1 -> {
                    int n = Math.min(count, Math.max(0, number(args[0])));
                    if (queue != null) {
                        for (int i = 0; i < n; i++) {
                            if (last) {
                                queue.pollLast();
                            } else {
                                queue.pollFirst();
                            }
                        }
                    }
                    return last ? range(keyed, values, count - n, count) : range(keyed, values, 0, n);
                }
                case 2 -> {
                    return count == 0 ? null : values[random.nextInt(count)];
                }
                case 3 -> {
                    int n = Math.max(0, number(args[0]));
                    if (count == 0) {
                        return new Object[0];
                    }
                    if (!distinct) {
                        // Any element each time, so the same one can come up more than once.
                        Object[] picked = new Object[n];
                        for (int i = 0; i < n; i++) {
                            picked[i] = values[random.nextInt(count)];
                        }
                        return picked;
                    }
                    List<Integer> order = new java.util.ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        order.add(i);
                    }
                    java.util.Collections.shuffle(order, random);
                    List<Integer> chosen = order.subList(0, Math.min(count, n));
                    Object[] picked = new Object[chosen.size()];
                    String[] keys = new String[chosen.size()];
                    for (int i = 0; i < picked.length; i++) {
                        picked[i] = values[chosen.get(i)];
                        keys[i] = keyed == null ? null : keyed.keys()[chosen.get(i)];
                    }
                    return keyed == null ? picked : new KeyedValues(picked, keys);
                }
                case 4 -> {
                    int n = number(args[0]);
                    int index = toLast ? count - n : n - 1;
                    return index >= 0 && index < count ? values[index] : null;
                }
                default -> {
                    // 'elements from 2 to 1' are those from 1 to 2.
                    int a = number(args[0]);
                    int b = number(args[1]);
                    int from = Math.max(1, Math.min(a, b));
                    int to = Math.min(count, Math.max(a, b));
                    return from > to ? new Object[0] : range(keyed, values, from - 1, to);
                }
            }
        }

        private static Object range(KeyedValues keyed, Object[] values, int from, int to) {
            Object[] picked = java.util.Arrays.copyOfRange(values, from, to);
            return keyed == null ? picked : new KeyedValues(picked, java.util.Arrays.copyOfRange(keyed.keys(), from, to));
        }
    }

    /** {@code sorted %objects%}: in order, keeping indices; nothing if they can't all be ordered. */
    private static final class SortedList implements Expression, KeyAware {
        @Override
        public Object get(TriggerContext context, Object[] args) {
            KeyedValues keyed = args[0] instanceof KeyedValues k ? k : null;
            Object[] values = keyed != null ? keyed.values() : (Object[]) args[0];
            Integer[] order = new Integer[values.length];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            boolean[] incomparable = new boolean[1];
            java.util.Arrays.sort(order, (x, y) -> {
                Integer result = Comparison.order(values[x], values[y]);
                if (result == null) {
                    incomparable[0] = true;
                    return 0;
                }
                return result;
            });
            if (incomparable[0]) {
                return null;
            }
            Object[] sorted = new Object[values.length];
            String[] keys = new String[values.length];
            for (int i = 0; i < order.length; i++) {
                sorted[i] = values[order[i]];
                keys[i] = keyed == null ? null : keyed.keys()[order[i]];
            }
            return keyed == null ? sorted : new KeyedValues(sorted, keys);
        }
    }
}
