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
        return value instanceof Number n ? co.xenastudios.neoskript.runtime.Values.toInt(n.doubleValue()) : 0;
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
            constantComparison(info, relation, negated);
            boolean leftAnd = info.and(0);
            return (context, args) -> negated != holds((Object[]) args[0], leftAnd, (Object[]) args[1], relation);
        }, "%objects% (is|are)[(not:n't|not: not)] (ge:(greater|more|higher|bigger|larger) than or (equal to|the same as)"
                + "|gt:(greater|more|higher|bigger|larger) than|gt:above"
                + "|le:(less|smaller|lower) than or (equal to|the same as)|lt:(less|smaller|lower) than|lt:below"
                + "|eq:[equal to|the same as]) %objects%",
                "%objects% (ge:\\>=|gt:\\>|le:\\<=|ne:\\<\\>|lt:\\<|eq:=|ne:!=) %objects%",
                // Before and after the event, as Skript's: 'player's tool was a diamond sword'.
                "%objects@-1% (was|were)[(not:n't|not: not)] (ge:(greater|more|higher|bigger|larger|above) [than] or (equal to|the same as)"
                + "|gt:(greater|more|higher|bigger|larger) than|gt:above"
                + "|le:(less|smaller|lower|below) [than] or (equal to|the same as)|lt:(less|smaller|lower) than|lt:below"
                + "|eq:[equal to|the same as]) %objects%",
                "%objects@1% (will be|not:will not be|not:won't be) (ge:(greater|more|higher|bigger|larger|above) [than] or (equal to|the same as)"
                + "|gt:(greater|more|higher|bigger|larger) than|gt:above"
                + "|le:(less|smaller|lower|below) [than] or (equal to|the same as)|lt:(less|smaller|lower) than|lt:below"
                + "|eq:[equal to|the same as]) %objects%",
                "%objects@1% ((is|are)|not:(isn't|aren't|is not|are not)) (turning|changing) [in]to %objects%");

        registrar.condition("CondIsSet", info -> {
            boolean negated = info.hasTag("not");
            return (context, args) -> negated == (((Object[]) args[0]).length == 0);
        }, "%~objects% (exist[s]|(is|are) set)",
                "%~objects% (not:do[es](n't| not) exist|not:(is|are)(n't| not) set)");


        registrar.expression("ExprNumbers", "number", info -> (context, args) ->
                range(((Number) args[0]).doubleValue(), ((Number) args[1]).doubleValue(), info.hasTag("integers")),
                "[(all [[of] the]|the)] (numbers|integers:integers) (between|from) %number% (and|to) %number%");

        // As Skript 2.17's PropCondContains: what's in each container (an inventory, a text...), falling back to
        // whether the values are among them; 'contains' after several only the latter, 'contents of' only the former.
        registrar.condition("CondContains", info -> {
            boolean negated = info.hasTag("not");
            boolean containersAnd = info.and(0);
            boolean valuesAnd = info.and(1);
            // Only containers written as several ('a and b', a list variable) are taken as several: an 'or' list
            // is one of them, as in Skript.
            boolean containment = info.pattern() >= 2 || !info.hasTag("s") || !(info.isList(0) && containersAnd);
            boolean direct = info.pattern() < 2;
            return (context, args) -> {
                Object[] containers = (Object[]) args[0];
                if (containers.length == 0) {
                    return negated;
                }
                return negated != contains(containers, containersAnd, (Object[]) args[1], valuesAnd, containment, direct);
            };
        }, "%objects% contain[s:s] %objects%", "%objects% (not:(s:doesn't|s:does not|do not|don't) contain) %objects%",
                "contents of %objects% contain %objects%", "contents of %objects% (not:(do not|don't)) contain %objects%");

        // Before elements, as in Skript: 'size of 10 random elements of X' is the size of the elements.
        // Skript 2.17's PropExprAmount, PropExprSize and PropExprNumber: of one value (or written 'amounts'),
        // each value's amount; of several, how many there are.
        registrar.expression("ExprAmount", "integer", info -> new Amount(info.hasTag("s") || info.isSingle(0),
                !info.isList(0), !info.hasTag("size") && !info.hasTag("number")),
                "[the] (amount|number:number|size:size)[s:s] of %objects%",
                "%objects%'[s] (amount|number:number|size:size)[s:s]");
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
            boolean counted = info.pattern() == 1;
            return (context, args) -> {
                Object lowArg = args[counted ? 1 : 0];
                Object highArg = args[counted ? 2 : 1];
                if (!(lowArg instanceof Number a) || !(highArg instanceof Number b)
                        || !Double.isFinite(a.doubleValue()) || !Double.isFinite(b.doubleValue())) {
                    return new Object[0];
                }
                // An amount that's written but nothing, or not above 0, gives nothing, as in Skript.
                if (counted && !(args[0] instanceof Number)) {
                    return new Object[0];
                }
                long wanted = counted ? Values.toInt(((Number) args[0]).doubleValue()) : 1;
                if (wanted <= 0) {
                    return new Object[0];
                }
                int count = (int) wanted;
                double low = Math.min(a.doubleValue(), b.doubleValue());
                double high = Math.max(a.doubleValue(), b.doubleValue());
                java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                Object[] numbers = new Object[count];
                if (integers) {
                    long to = (long) Math.floor(high);
                    long from = (long) Math.ceil(low);
                    if (high - low < 1 && from - to <= 1) {
                        // At most one whole number between them, like between 0 and 0.5, or none, like 1.5 and 1.6.
                        Long only = to == from || low == from ? Long.valueOf(from) : high == to ? Long.valueOf(to) : null;
                        if (only == null) {
                            return new Object[0];
                        }
                        java.util.Arrays.fill(numbers, only);
                        return numbers.length == 1 ? numbers[0] : numbers;
                    }
                    for (int i = 0; i < count; i++) {
                        numbers[i] = from + Math.floorMod(random.nextLong(), to - from + 1);
                    }
                } else {
                    for (int i = 0; i < count; i++) {
                        numbers[i] = Math.min(low + random.nextDouble() * (high - low), high);
                    }
                }
                return numbers.length == 1 ? numbers[0] : numbers;
            };
        }, "[a] random (integer:integer|number)[s] (from|between) %number% (to|and) %number%",
                "%integer% random (integer:integer|number)[s] (from|between) %number% (to|and) %number%");
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
            boolean and = info.and(0);
            return (context, args) -> {
                if (!(args[1] instanceof Number divisor)) {
                    return false;
                }
                double tolerance = args[2] instanceof Number t ? t.doubleValue() : 1e-10;
                // Past half the divisor, a tolerance lets everything through, so nothing is divisible.
                boolean meaningful = Math.abs(divisor.doubleValue()) >= 2 * tolerance;
                return Condition.check((Object[]) args[0], and, number -> number instanceof Number n
                        && (meaningful && Math.abs(Math.IEEEremainder(n.doubleValue(), divisor.doubleValue())) <= tolerance)
                                != negated);
            };
        }, "%numbers% (is|are) evenly divisible by %number% [with [a] tolerance [of] %-number%]",
                "%numbers% (isn't|is not|aren't|are not) evenly divisible by %number% [with [a] tolerance [of] %-number%]",
                "%numbers% can be [evenly] divided by %number% [with [a] tolerance [of] %-number%]",
                "%numbers% (can't|cannot|can not) be [evenly] divided by %number% [with [a] tolerance [of] %-number%]",
                "%numbers% can be evenly divided by %number% [with [a] tolerance [of] %-number%]",
                "%numbers% (can't|can[ ]not) be evenly divided by %number% [with [a] tolerance [of] %-number%]");
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
        }, "%number% [in] deg[ree][s]", "%number% [in] (rad:rad[ian][s])", "%numbers% in deg[ree][s]",
                "%numbers% in (rad:rad[ian][s])");
        registrar.expression("ExprSortedList", "object", info -> new SortedList(), "sorted %objects%");
        registrar.expression("ExprAlphabetList", "string", info -> new SortedList(true), "alphabetically sorted %strings%");
        registrar.expression("ExprReversedList", "object", info -> {
            if (info.isSingle(0)) {
                throw new co.xenastudios.neoskript.api.element.SyntaxError("A single object cannot be reversed.");
            }
            return new Reordered(false);
        }, "reversed %objects%");
        // After the lists, as in Skript: 'reversed 5 times' is 5 times, reversed.
        registrar.expression("ExprTimes", "integer", info -> {
            timesChecks(info);
            return (context, args) -> {
                // Rounded, so floating point error doesn't lose one: 10 - 1.05 / 0.15 times is 3 times.
                long count = info.hasTag("once") ? 1 : info.hasTag("twice") ? 2 : info.hasTag("thrice") ? 3
                        : args[0] instanceof Number n ? Math.max(0, Math.round(n.doubleValue())) : 0;
                Object[] numbers = new Object[(int) Math.min(count, Integer.MAX_VALUE)];
                for (int i = 0; i < numbers.length; i++) {
                    numbers[i] = (long) i + 1;
                }
                return numbers;
            };
        }, "%number% time[s]", "(once:once|twice:twice|thrice:thrice)");
        registrar.expression("ExprPi", "number", info -> (context, args) -> Math.PI, "(pi|\u03c0)");
        // Why the last 'X parsed as Y' failed, as Skript's ExprParseError.
        registrar.expression("ExprParseError", "string", info -> (context, args) -> Values.lastParseError(),
                "[the] [last] [parse] error");
        // 'debug info of 1, "a"': each value as text with its type, "1 (integer)" and ""a" (text)".
        registrar.property("ExprDebugInfo", "string", "debug info[rmation]", "objects", Object.class, value ->
                (value instanceof String ? "\"" + value + "\"" : Values.text(value)) + " ("
                        + co.xenastudios.neoskript.runtime.TypeNames.of(value) + ")");
        // The limits of Java's number types, as Skript's literals: integers as longs, floats as doubles. The
        // minimum double and float are the smallest positive ones, as Java's MIN_VALUE is.
        registrar.expression("LitIntMaxValue", "integer", info -> (context, args) -> (long) Integer.MAX_VALUE,
                "[the] max[imum] integer value");
        registrar.expression("LitIntMinValue", "integer", info -> (context, args) -> (long) Integer.MIN_VALUE,
                "[the] min[imum] integer value");
        registrar.expression("LitLongMaxValue", "integer", info -> (context, args) -> Long.MAX_VALUE,
                "[the] max[imum] long value");
        registrar.expression("LitLongMinValue", "integer", info -> (context, args) -> Long.MIN_VALUE,
                "[the] min[imum] long value");
        registrar.expression("LitDoubleMaxValue", "number", info -> (context, args) -> Double.MAX_VALUE,
                "[the] max[imum] double value");
        registrar.expression("LitDoubleMinValue", "number", info -> (context, args) -> Double.MIN_VALUE,
                "[the] min[imum] double value");
        registrar.expression("LitFloatMaxValue", "number", info -> (context, args) -> (double) Float.MAX_VALUE,
                "[the] max[imum] float value");
        registrar.expression("LitFloatMinValue", "number", info -> (context, args) -> (double) Float.MIN_VALUE,
                "[the] min[imum] float value");
        registrar.expression("ExprRound", "integer", info -> {
            int mode = info.hasTag("down") ? -1 : info.hasTag("up") ? 1 : 0;
            return (context, args) -> {
                List<Object> rounded = new java.util.ArrayList<>();
                for (Object value : Values.array(args[0])) {
                    if (value instanceof Number number) {
                        rounded.add(round(number.doubleValue(), mode));
                    }
                }
                return rounded.size() == 1 ? rounded.getFirst() : rounded.toArray();
            };
        }, "(a|the|) round[ed] (down:down|up:up) %numbers%", "(a|the|) round[ed] %numbers%",
                "(down:floor[ed]|up:ceil[ed]|up:ceiling) %numbers%", "%numbers% (down:floor[ed]|rounded|up:ceil[ed])");
        registrar.expression("ExprIndicesOfValue", "object", info -> new IndicesOfValue(info.pattern(),
                info.hasTag("first") ? 1 : info.hasTag("last") ? 2 : info.hasTag("all") ? 0
                        : info.hasTag("plural") ? 0 : 1),
                "[the] [first:first|last:last|all:all] (position|plural:positions) of [[the] value] %strings% in %string%",
                "[the] [first:first|last:last|all:all] (position|plural:positions) of [[the] value] %objects% in %~objects%",
                "[the] [first:first|last:last|all:all] (index|plural:indices|plural:indexes) of [[the] value] %objects% in %~objects%");

        // 'toggle {_flag}': booleans flip, and blocks that open (doors, trapdoors...) open or close; others stay.
        registrar.effect("EffToggle", info -> {
            int mode = info.hasTag("open") ? 1 : info.hasTag("close") ? 2 : 0;
            return co.xenastudios.neoskript.api.element.Update.of(0, (context, args) -> {
                Object[] values = Values.array(args[0]);
                Object[] toggled = new Object[values.length];
                for (int i = 0; i < values.length; i++) {
                    Object value = values[i];
                    if (value instanceof Boolean flag && mode == 0) {
                        toggled[i] = !flag;
                        continue;
                    }
                    if (value instanceof org.bukkit.block.Block block
                            && block.getBlockData() instanceof org.bukkit.block.data.Openable openable) {
                        openable.setOpen(mode == 0 ? !openable.isOpen() : mode == 1);
                        block.setBlockData(openable);
                    }
                    toggled[i] = value;
                }
                return toggled;
            });
        }, "(open:open|close:close|toggle) %~objects%");
        registrar.expression("ExprInverse", "boolean", info -> (context, args) -> {
            Object[] values = Values.array(args[0]);
            Object[] inverse = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                inverse[i] = values[i] instanceof Boolean b ? !b : null;
            }
            return inverse.length == 1 ? inverse[0] : inverse;
        }, "[the] (inverse|opposite)[s] of %booleans%");
        registrar.expression("ExprShuffledList", "object", info -> new Reordered(true), "shuffled %objects%");
        registrar.expression("ExprPercent", "number", info -> (context, args) -> {
            if (!(args[0] instanceof Number percent)) {
                return null;
            }
            List<Object> results = new java.util.ArrayList<>();
            for (Object value : (Object[]) args[1]) {
                if (value instanceof Number number) {
                    results.add(percent.doubleValue() / 100 * number.doubleValue());
                }
            }
            return results.size() == 1 ? results.getFirst() : results.toArray();
        }, "%number%(\\%| percent) of %numbers%");

        // 'keyed {_list::*}': the list's values, which keep their indices where that matters.
        registrar.expression("ExprKeyed", "object", info -> (context, args) -> args[0], "keyed %~objects%");
    }

    /**
     * Whether the containers contain the values, as Skript checks it: inventories hold the items; texts
     * contain the texts (or, failing that, the values are among them); anything else, the values are among
     * the containers' values.
     */
    /**
     * {@code the amount of X}: with {@code properties}, each value's amount, as Skript's amount property has
     * it (an item's or a slot's, which can be set, or how many elements a queue has); else how many values
     * there are. {@code stacks}: whether item stacks have one, as they do for 'amount' but not 'size'.
     *
     * @param maybe whether a value not known to be one (as an event value isn't) may be one: then a lone value
     *     with an amount is taken as one
     */
    private record Amount(boolean properties, boolean maybe, boolean stacks)
            implements Expression, co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            Object[] values = Values.array(arguments[0]);
            if (!properties && !(maybe && values.length == 1 && amount(values[0]) != null)) {
                return (long) values.length;
            }
            List<Object> amounts = new java.util.ArrayList<>();
            for (Object value : values) {
                Long amount = amount(value);
                if (amount != null) {
                    amounts.add(amount);
                }
            }
            return amounts.size() == 1 ? amounts.getFirst() : amounts.toArray();
        }

        /** The value's amount, or {@code null} if it has none. */
        private Long amount(Object value) {
            return switch (value) {
                case ItemType item -> (long) item.amount();
                case org.bukkit.inventory.ItemStack stack when stacks -> (long) stack.getAmount();
                case Items.Slot slot -> slot.item() == null ? 0L : (long) slot.item().getAmount();
                case java.util.Collection<?> queue -> (long) queue.size();
                case null, default -> null;
            };
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return (properties || maybe) && stacks && mode == Mode.SET ? new Class<?>[] {Number.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (delta == null || delta.length == 0 || !(delta[0] instanceof Number number)) {
                return;
            }
            int amount = Values.toInt(number.doubleValue());
            for (Object value : Values.array(arguments[0])) {
                switch (value) {
                    case ItemType item -> item.setAmount(amount);
                    case org.bukkit.inventory.ItemStack stack -> stack.setAmount(amount);
                    case Items.Slot slot -> {
                        org.bukkit.inventory.ItemStack item = slot.item();
                        if (item != null) {
                            item.setAmount(amount);
                            slot.set(item);
                        }
                    }
                    case null, default -> {}
                }
            }
        }
    }

    /**
     * Skript's checks for {@code X times} with a literal number, as its {@code ExprTimes.init}: warnings for looping
     * zero times or once, errors for a negative amount.
     */
    private static void timesChecks(co.xenastudios.neoskript.api.element.ElementInfo info) {
        long amount;
        if (info.pattern() == 1) {
            amount = info.hasTag("once") ? 1 : info.hasTag("twice") ? 2 : 3;
        } else {
            String literal = info.argumentLiteral(0);
            if (literal == null || "string".equals(info.argumentType(0))) {
                return;
            }
            try {
                amount = (long) Double.parseDouble(literal.replace("_", ""));
            } catch (NumberFormatException notNumber) {
                return;
            }
        }
        String line = co.xenastudios.neoskript.api.element.Warnings.line();
        // As Skript tells a loop from the line's key.
        boolean inLoop = line != null && line.strip().toLowerCase(java.util.Locale.ROOT).startsWith("loop ");
        if (amount == 0 && inLoop) {
            co.xenastudios.neoskript.api.element.Warnings.warn("Looping zero times makes the code inside of the loop useless");
        } else if (amount == 1 && inLoop) {
            co.xenastudios.neoskript.api.element.Warnings.warn(
                    "Since you're looping exactly one time, you could simply remove the loop instead");
        } else if (amount < 0) {
            throw new co.xenastudios.neoskript.api.element.SyntaxError(inLoop
                    ? "Looping a negative amount of times is impossible"
                    : "The times expression only supports positive numbers");
        }
    }

    private static boolean contains(Object[] containers, boolean containersAnd, Object[] values, boolean valuesAnd,
            boolean containment, boolean direct) {
        if (containers.length == 0) {
            return false;
        }
        if (!containment) {
            return among(containers, values, valuesAnd);
        }
        if (java.util.Arrays.stream(containers).allMatch(Inventory.class::isInstance)) {
            return Condition.check(containers, containersAnd, inventory -> Condition.check(values, valuesAnd,
                    item -> Items.contains((Inventory) inventory, item)));
        }
        if (java.util.Arrays.stream(containers).allMatch(net.kyori.adventure.text.Component.class::isInstance)) {
            // A component contains what's in it as it's written: 'line 1 of lore of X contains "<red>Hello"'.
            TextComponentParser parser = TextComponentParser.instance();
            return Condition.check(containers, containersAnd, container -> Condition.check(values, valuesAnd, value -> {
                String needle = value instanceof net.kyori.adventure.text.Component component ? parser.toString(component)
                        : value instanceof String text ? parser.toString(parser.parse(text)) : null;
                return needle != null && parser.toString((net.kyori.adventure.text.Component) container)
                        .toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT));
            }));
        }
        if (java.util.Arrays.stream(containers).allMatch(String.class::isInstance)
                && Condition.check(containers, containersAnd, container -> Condition.check(values, valuesAnd,
                        value -> value instanceof String text && ((String) container).toLowerCase(java.util.Locale.ROOT)
                                .contains(text.toLowerCase(java.util.Locale.ROOT))))) {
            return true;
        }
        // Not all of them hold things: whether the values are among them, unless only what's in them counts.
        return direct && among(containers, values, valuesAnd);
    }

    /** Whether the values (all, or any) are among {@code containers}, as Skript compares them. */
    private static boolean among(Object[] containers, Object[] values, boolean valuesAnd) {
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

    /**
     * Skript's warning for a comparison of two literals, which it simplifies to its result
     * ({@code SimplifiedCondition}): "The condition '1 is equal to 1' will always be true."
     */
    private static void constantComparison(co.xenastudios.neoskript.api.element.ElementInfo info, Relation relation,
            boolean negated) {
        Object left = literalValue(info, 0);
        Object right = literalValue(info, 1);
        if (left == null || right == null) {
            return;
        }
        boolean result = negated != holds(new Object[] {left}, true, new Object[] {right}, relation);
        String relationName = switch (relation) {
            case EQUAL -> "equal to";
            case NOT_EQUAL -> "not equal to";
            case GREATER -> "greater than";
            case GREATER_OR_EQUAL -> "greater than or equal to";
            case LESS -> "smaller than";
            case LESS_OR_EQUAL -> "smaller than or equal to";
        };
        // As Skript writes the literals: booleans as their value, the rest as written.
        String first = left instanceof Boolean ? left.toString() : co.xenastudios.neoskript.api.element.Warnings.argument(0);
        String second = right instanceof Boolean ? right.toString() : co.xenastudios.neoskript.api.element.Warnings.argument(1);
        co.xenastudios.neoskript.api.element.Warnings.warn("constant", "The condition '" + first + " is "
                + (negated ? "not " : "") + relationName + " " + second + "' will always be "
                + result + ".");
    }

    /** Argument {@code index}'s value, if it's a number, boolean or text written out, else {@code null}. */
    private static Object literalValue(co.xenastudios.neoskript.api.element.ElementInfo info, int index) {
        String literal = info.argumentLiteral(index);
        String type = info.argumentType(index);
        if (literal == null || type == null) {
            return null;
        }
        try {
            return switch (type) {
                case "string" -> literal;
                case "boolean" -> literal.equalsIgnoreCase("true") || literal.equalsIgnoreCase("yes")
                        || literal.equalsIgnoreCase("on");
                case "integer" -> Long.parseLong(literal.replace("_", ""));
                case "number" -> Double.parseDouble(literal.replace("_", ""));
                default -> null;
            };
        } catch (NumberFormatException notPlain) {
            return null;
        }
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
        /** Whether it's {@code alphabetically sorted}: texts by their characters, case and all. */
        private final boolean alphabetical;

        SortedList() {
            this(false);
        }

        SortedList(boolean alphabetical) {
            this.alphabetical = alphabetical;
        }

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
                Integer result = alphabetical ? (Integer) Values.text(values[x]).compareTo(Values.text(values[y]))
                        : Comparison.order(values[x], values[y]);
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

    /** {@code reversed %objects%} or {@code shuffled %objects%}, keeping indices. */
    private record Reordered(boolean shuffle) implements Expression, KeyAware {
        @Override
        public Object get(TriggerContext context, Object[] args) {
            KeyedValues keyed = args[0] instanceof KeyedValues k ? k : null;
            Object[] values = keyed != null ? keyed.values() : (Object[]) args[0];
            List<Integer> order = new java.util.ArrayList<>(values.length);
            for (int i = 0; i < values.length; i++) {
                order.add(i);
            }
            if (shuffle) {
                java.util.Collections.shuffle(order, java.util.concurrent.ThreadLocalRandom.current());
            } else {
                java.util.Collections.reverse(order);
            }
            Object[] reordered = new Object[values.length];
            String[] keys = new String[values.length];
            for (int i = 0; i < reordered.length; i++) {
                reordered[i] = values[order.get(i)];
                keys[i] = keyed == null ? null : keyed.keys()[order.get(i)];
            }
            return keyed == null ? reordered : new KeyedValues(reordered, keys);
        }
    }

    /** Rounds as Skript does, allowing for floating point error: 100 lots of 0.1 floor to 10. */
    static long round(double value, int mode) {
        return switch (mode) {
            case -1 -> (long) Math.floor(value + 1e-10);
            case 1 -> (long) Math.ceil(value - 1e-10);
            default -> (long) Math.floor(value + 0.5);
        };
    }

    /**
     * {@code [first|last|all] indices of the value X in {_list::*}}: where values are in a list (its indices, or
     * positions counting from 1), or where texts are in a text (positions counting from 1). {@code which} is all
     * (0), the first (1) or the last (2) for each value looked for.
     */
    private record IndicesOfValue(int pattern, int which) implements Expression, KeyAware {
        @Override
        public boolean implicitKeys() {
            return true;
        }

        @Override
        public Object get(TriggerContext context, Object[] args) {
            List<Object> found = new java.util.ArrayList<>();
            Object[] wanted = Values.array(KeyedValues.plain(args[0]));
            if (pattern == 0) {
                if (!(args[1] instanceof String text)) {
                    return null;
                }
                for (Object value : wanted) {
                    if (!(value instanceof String part) || part.isEmpty()) {
                        continue;
                    }
                    List<Long> positions = new java.util.ArrayList<>();
                    for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
                        positions.add((long) at + 1);
                    }
                    pick(positions, found);
                }
                return found.size() == 1 && which != 0 ? found.getFirst() : found.toArray();
            }
            KeyedValues list = KeyedValues.of(args[1]);
            for (Object value : wanted) {
                List<Object> matches = new java.util.ArrayList<>();
                for (int i = 0; i < list.values().length; i++) {
                    if (Comparison.equal(list.values()[i], value)) {
                        matches.add(pattern == 1 ? (Object) (long) (i + 1) : list.keys()[i]);
                    }
                }
                pick(matches, found);
            }
            return found.size() == 1 && which != 0 ? found.getFirst() : found.toArray();
        }

        private void pick(List<?> matches, List<Object> into) {
            if (matches.isEmpty()) {
                return;
            }
            switch (which) {
                case 1 -> into.add(matches.getFirst());
                case 2 -> into.add(matches.getLast());
                default -> into.addAll(matches);
            }
        }
    }
}
