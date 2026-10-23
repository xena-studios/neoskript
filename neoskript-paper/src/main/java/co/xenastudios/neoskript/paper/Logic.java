/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Comparison;
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
            return (context, args) -> negated != holds((Object[]) args[0], (Object[]) args[1], relation);
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
            return (context, args) -> negated != contains((Object[]) args[0], (Object[]) args[1]);
        }, "%objects% contain[s] %objects%", "%objects% (not:(doesn't|does not|do not|don't) contain) %objects%");

        registrar.expression("ExprElement", "object", info -> (context, args) -> {
            Object[] values = (Object[]) args[0];
            if (values.length == 0) {
                return null;
            }
            return info.hasTag("last") ? values[values.length - 1]
                    : info.hasTag("random") ? values[java.util.concurrent.ThreadLocalRandom.current().nextInt(values.length)]
                    : values[0];
        }, "[the] (first|last:last|random:random) element [out] of %objects%");

        // 'arg-1 ? "home"': the first values, or the others if there are none.
        registrar.expression("ExprDefaultValue", "object", info -> (context, args) ->
                ((Object[]) args[0]).length > 0 ? args[0] : args[1], "%objects% (otherwise|?) %objects%");
        registrar.expression("LitInfinity", "number", info -> (context, args) -> Double.POSITIVE_INFINITY,
                "positive (infinity|\u221e) [value]", "\u221e [value]", "infinity value", "value of [positive] (infinity|\u221e)");
        registrar.expression("LitNegativeInfinity", "number", info -> (context, args) -> Double.NEGATIVE_INFINITY,
                "negative (infinity|\u221e) [value]", "-\u221e [value]", "value of negative (infinity|\u221e)");
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
            return (context, args) -> {
                Object[] texts = (Object[]) args[0];
                Object[] affixes = (Object[]) args[1];
                if (texts.length == 0 || affixes.length == 0) {
                    return false;
                }
                boolean all = true;
                for (Object text : texts) {
                    boolean any = false;
                    for (Object affix : affixes) {
                        String t = Values.text(text);
                        String a = Values.text(affix);
                        any |= ends ? t.endsWith(a) : t.startsWith(a);
                    }
                    all &= any;
                }
                return all != negated;
            };
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
        registrar.expression("ExprRandom", "object", info -> (context, args) -> {
            Object[] values = (Object[]) args[0];
            return values.length == 0 ? null : values[java.util.concurrent.ThreadLocalRandom.current().nextInt(values.length)];
        }, "[a] random <[\\p{L} ]+> [out] of %objects%");
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
        registrar.expression("ExprSortedList", "object", info -> (context, args) -> {
            Object[] values = ((Object[]) args[0]).clone();
            java.util.Arrays.sort(values, (a, b) -> {
                Integer order = Comparison.order(a, b);
                return order == null ? 0 : order;
            });
            return values;
        }, "sorted %objects%");
        // 'keyed {_list::*}': the list's values, which keep their indices where that matters.
        registrar.expression("ExprKeyed", "object", info -> (context, args) -> args[0], "keyed %~objects%");
        registrar.expression("ExprAmount", "integer", info -> (context, args) -> (long) ((Object[]) args[0]).length,
                "[the] (amount|number|size) of %objects%");
    }

    /**
     * Texts contain text (ignoring case); anything else contains a value when one of its values equals it.
     * Every value on the right has to be contained.
     */
    private static boolean contains(Object[] containers, Object[] values) {
        if (containers.length == 0 || values.length == 0) {
            return false;
        }
        boolean texts = java.util.Arrays.stream(containers).allMatch(String.class::isInstance);
        for (Object value : values) {
            boolean found = false;
            for (Object container : containers) {
                if (texts && value instanceof String text
                        ? ((String) container).toLowerCase(java.util.Locale.ROOT).contains(text.toLowerCase(java.util.Locale.ROOT))
                        : Comparison.equal(container, value)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
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
    private static boolean holds(Object[] left, Object[] right, Relation relation) {
        if (left.length == 0 || right.length == 0) {
            return false;
        }
        for (Object a : left) {
            boolean any = false;
            for (Object b : right) {
                if (holds(a, b, relation)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
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
}
