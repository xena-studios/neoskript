/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Helpers the generated code calls to convert values. */
public final class Values {

    private static final Object[] EMPTY = new Object[0];

    private Values() {}

    /** A value as a plural argument: an array as is, {@code null} as empty, anything else as one element. */
    public static Object[] array(Object value) {
        if (value instanceof Object[] array) {
            return array;
        }
        return value == null ? EMPTY : new Object[] {value};
    }

    /** A value as a singular argument: the first element of an array, or the value itself. */
    public static Object single(Object value) {
        if (value instanceof Object[] array) {
            return array.length == 0 ? null : array[0];
        }
        return value;
    }

    /**
     * {@code set event-item to bread}: sets the event's value with {@code setter} to the value given, converted to its
     * class as Skript's change effect converts it. If nothing given is one (none, or text where an item goes), nothing
     * is set, as in Skript. On Folia, an entity another region owns is an error, as reading it would be.
     */
    public static void setEventValue(java.util.function.BiConsumer<Object, Object> setter,
            co.xenastudios.neoskript.api.element.TriggerContext context, Object value, Class<?> valueClass,
            co.xenastudios.neoskript.runtime.errors.ErrorSites.Site site) {
        Object[] given = narrow(convert(value, valueClass), new Class<?>[] {valueClass});
        if (given.length == 0 || co.xenastudios.neoskript.runtime.region.Regions.refuseForeign(site, given[0])) {
            return;
        }
        setter.accept(context.event(), given[0]);
    }

    /**
     * The context the code in a library section runs in for one of its values, as Skript runs a spawn section's code:
     * in the section's own event, made of the value, on the same thread as {@code outer}.
     */
    public static co.xenastudios.neoskript.api.element.TriggerContext sectionContext(
            co.xenastudios.neoskript.api.element.TriggerContext outer, java.util.function.Function<Object, ?> event,
            Object value) {
        return new SimpleTriggerContext(event.apply(value), outer instanceof SimpleTriggerContext simple ? simple.owner() : null);
    }

    /** The values that are instances of one of {@code classes}: what a changed expression accepts. */
    public static Object[] narrow(Object value, Class<?>[] classes) {
        List<Object> values = new ArrayList<>();
        for (Object element : array(value)) {
            for (Class<?> type : classes) {
                if (type.isInstance(element)) {
                    values.add(element);
                    break;
                }
            }
        }
        return values.toArray();
    }

    /** Why the last {@code parsed as} failed, for {@code the last parse error}: shared, as Skript's is. */
    private static volatile String lastParseError;

    /**
     * {@code "5" parsed as integer}: the text through {@code parser}, or {@code null} if it isn't one, which
     * is then the last parse error ({@code what} it couldn't be parsed as: {@code an integer}).
     */
    @SuppressWarnings("unchecked")
    public static Object parse(Function<?, ?> parser, Object text, String what) {
        Object value = single(text);
        // Only text is parsed, as Skript's '%string% parsed as': what a converter turns into text (a UUID, a
        // component) is that text, and anything else, like an item, gives nothing.
        if (!(convertOne(value, String.class) instanceof String string)) {
            return null;
        }
        lastParseError = null;
        parserError = null;
        Object parsed = ((Function<String, ?>) parser).apply(string.strip());
        if (parsed == null) {
            lastParseError = parserError != null ? parserError : string + " could not be parsed as " + what;
        }
        parserError = null;
        return parsed;
    }

    /** What the running parser said was wrong, for {@link #parseError}; or {@code null}. */
    private static volatile String parserError;

    /**
     * Says why the parser running for {@code parsed as} turns its text down, as Skript's parsers log an error
     * ({@code 'any sword' represents multiple materials}): that's then the last parse error.
     */
    public static void parseError(String message) {
        parserError = message;
    }

    /** Skript's {@code the last parse error}: why the last {@code parsed as} failed, or {@code null}. */
    public static String lastParseError() {
        return lastParseError;
    }

    /** Keeps only values of {@code type}: a single value or {@code null}, or an array of the ones that are. */
    public static Object filter(Object value, Class<?> type) {
        if (value instanceof Object[] array) {
            for (Object element : array) {
                if (element != null && !type.isInstance(element)) {
                    return cast(array, type);
                }
            }
            return array;
        }
        return type.isInstance(value) ? value : null;
    }

    /** {@link #filter}, keeping the indices of keyed values ({@link KeyedValues}). */
    public static Object filterKeyed(Object value, Class<?> type) {
        return value instanceof KeyedValues keyed ? keyed.only(type) : filter(value, type);
    }

    /**
     * How many times {@code N times} counts, as Skript's ExprTimes: {@code N} plus Skript's epsilon ({@code 1e-10}),
     * rounded down, so floating point error doesn't lose one ({@code 10 - 1.05 / 0.15 times} is 3 times) and
     * {@code 2.5 times} is 2. Not a number, zero, negative or NaN counts nothing. Skript counts with
     * {@code LongStream.range(1, N + 1)}, which is empty when {@code N} is {@link Long#MAX_VALUE} (any number from
     * 2^63 up, and infinity), so those count nothing too. Above {@link Integer#MAX_VALUE} it's capped there: a loop
     * can't get that far in Skript either without the server's watchdog stopping it.
     */
    public static int timesCount(Object amount) {
        if (!(amount instanceof Number number)) {
            return 0;
        }
        long fixed = (long) (number.doubleValue() + 1e-10);
        if (fixed <= 0 || fixed == Long.MAX_VALUE) {
            return 0;
        }
        return (int) Math.min(fixed, Integer.MAX_VALUE);
    }

    /**
     * The most numbers {@code N times} makes when it isn't looped: a list of them all at once, as Skript's
     * {@code get()} makes, which for a billion runs out of memory in Skript. Past this it's a runtime error and nothing,
     * a deliberate difference. {@code loop N times} and {@code for} count as they go and have no such limit.
     */
    public static final int TIMES_LIST_LIMIT = 1_000_000;

    /** The numbers 1 to {@code count} for {@code N times} outside a loop, or none, with a runtime error, past the limit. */
    public static Object[] timesList(int count) {
        if (count > TIMES_LIST_LIMIT) {
            RuntimeErrors.report("'" + count + " times' would make a list of " + count
                    + " numbers, more than the " + TIMES_LIST_LIMIT + " allowed. Loop it ('loop " + count
                    + " times:') to count that far");
            return new Object[0];
        }
        Object[] numbers = new Object[count];
        for (int i = 0; i < count; i++) {
            numbers[i] = (long) i + 1;
        }
        return numbers;
    }

    /**
     * The value of a {@code loop N times} at {@code index + offset}, worked out instead of kept in an array: that
     * number plus one, or {@code null} outside the loop's {@code count}, as {@link #element}.
     */
    public static Object timesElement(int index, int offset, int count) {
        long i = (long) index + offset;
        return i >= 0 && i < count ? Long.valueOf(i + 1) : null;
    }

    private record Container(Class<?> type, java.util.function.Function<Object, Object[]> contents) {}

    private static final java.util.List<Container> CONTAINERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Registers what looping a value of {@code type} written as words goes through, as Skript's loop does for its
     * containers: an item type's items.
     */
    @SuppressWarnings("unchecked")
    public static <T> void registerContainer(Class<T> type, java.util.function.Function<? super T, Object[]> contents) {
        CONTAINERS.add(new Container(type, (java.util.function.Function<Object, Object[]>) contents));
    }

    /** What a loop over {@code values}, written as words, goes through: a container's contents, else them. */
    public static Object[] containerValues(Object[] values) {
        if (values.length == 1 && values[0] != null) {
            for (Container container : CONTAINERS) {
                if (container.type().isInstance(values[0])) {
                    return container.contents().apply(values[0]);
                }
            }
        }
        return values;
    }

    /** What a loop goes through: the values, or a queue's elements, which looping takes out of it. */
    public static Object[] loopValues(Object[] values) {
        return loopedQueue(values) != null ? new Object[0] : values;
    }

    /** The queue a loop over {@code values} takes from as it goes, or {@code null} if they aren't one. */
    public static java.util.Queue<?> loopedQueue(Object[] values) {
        return values.length == 1 && values[0] instanceof java.util.Queue<?> queue ? queue : null;
    }

    /**
     * The values a loop over a queue has taken, with the queue's next value added once the loop reaches
     * {@code index}: it sees what was added to the queue while looping, as in Skript.
     */
    public static Object[] takeFromQueue(Object[] taken, int index, java.util.Queue<?> queue) {
        if (queue == null || index < taken.length) {
            return taken;
        }
        Object next = queue.poll();
        if (next == null) {
            return taken;
        }
        Object[] more = java.util.Arrays.copyOf(taken, index + 1);
        more[index] = next;
        return more;
    }

    /** One of {@code values} at random, or {@code null} if there are none. */
    public static Object random(Object[] values) {
        return values.length == 0 ? null : values[java.util.concurrent.ThreadLocalRandom.current().nextInt(values.length)];
    }

    /** Whether a value counts as true for {@code set whether X to ...}: the boolean true. */
    public static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(single(value));
    }

    /** Adds a value, or every value of an array, to {@code list}: what an inline function collects. */
    public static void addAll(List<Object> list, Object value) {
        if (value instanceof Object[] array) {
            for (Object element : array) {
                if (element != null) {
                    list.add(element);
                }
            }
        } else if (value != null) {
            list.add(value);
        }
    }

    /** {@code values} sorted by {@code keys} (one per value), keeping equal keys in order. */
    public static Object[] sortByKeys(Object[] values, Object[] keys, boolean descending) {
        Integer[] order = new Integer[values.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (x, y) -> {
            Integer c = Comparison.order(keys[x], keys[y]);
            int result = c == null ? 0 : c;
            return descending ? -result : result;
        });
        Object[] sorted = new Object[values.length];
        for (int i = 0; i < order.length; i++) {
            sorted[i] = values[order[i]];
        }
        return sorted;
    }

    /** The values that are instances of {@code type}, for {@code number within {_x}}. */
    public static Object[] cast(Object value, Class<?> type) {
        List<Object> values = new ArrayList<>();
        for (Object element : array(value)) {
            if (type.isInstance(element)) {
                values.add(element);
            }
        }
        return values.toArray();
    }

    /** {@link #cast}, keeping too the values that convert to {@code type}, converted. */
    public static Object[] castConverting(Object value, Class<?> type) {
        return cast(convert(array(value), type), type);
    }

    /** {@code array[index + offset]}, or {@code null} if that's outside the array: for next/previous loop values. */
    public static Object element(Object[] array, int index, int offset) {
        int i = index + offset;
        return i >= 0 && i < array.length ? array[i] : null;
    }

    /**
     * What {@code transform} gives a list: if each value became exactly one, those at the same indices;
     * otherwise all of them, numbered from 1.
     *
     * @param results the values each value became, in order
     * @param keys the list's indices, or {@code null}
     */
    public static Object transformed(java.util.List<Object[]> results, String[] keys) {
        boolean oneEach = keys != null && keys.length == results.size();
        java.util.List<Object> all = new java.util.ArrayList<>();
        for (Object[] result : results) {
            oneEach &= result.length == 1;
            java.util.Collections.addAll(all, result);
        }
        return oneEach ? new KeyedValues(all.toArray(), keys) : all.toArray();
    }

    /**
     * {@code value} rounded to a whole number, kept within an {@code int}: infinity is the largest, and
     * not-a-number is 0, where a plain cast would wrap round to nonsense.
     */
    public static int toInt(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(value)));
    }

    /** A random index below {@code size}: which of an or-list is meant. */
    public static int randomIndex(int size) {
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(size);
    }

    /** The values of a list expression, with nested lists flattened. */
    public static Object[] list(Object[] items) {
        List<Object> values = new ArrayList<>(items.length);
        for (Object item : items) {
            if (item instanceof Object[] array) {
                for (Object element : array) {
                    if (element != null) {
                        values.add(element);
                    }
                }
            } else if (item != null) {
                values.add(item);
            }
        }
        return values.toArray();
    }

    /**
     * A text's template ({@link co.xenastudios.neoskript.api.element.TextTemplate}) with its holes' values: those
     * the trigger worked out, in order, for a {@link co.xenastudios.neoskript.api.element.TemplateAware} element.
     */
    public static co.xenastudios.neoskript.api.element.TextTemplate template(
            co.xenastudios.neoskript.api.element.TextTemplate shape, Object[] values) {
        List<co.xenastudios.neoskript.api.element.TextTemplate.Hole> holes = new java.util.ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            co.xenastudios.neoskript.api.element.TextTemplate.Hole hole = shape.holes().get(i);
            List<Object> each = new java.util.ArrayList<>();
            List<String> texts = new java.util.ArrayList<>();
            for (Object value : array(values[i])) {
                if (value != null) {
                    each.add(value);
                    texts.add(text(value));
                }
            }
            holes.add(new co.xenastudios.neoskript.api.element.TextTemplate.Hole(hole.element(), hole.written(), each,
                    texts));
        }
        return new co.xenastudios.neoskript.api.element.TextTemplate(shape.pieces(), holes);
    }

    /** How a value reads in text, as when embedded in a string with {@code %...%}. */
    public static String text(Object value) {
        if (value == null || (value instanceof Object[] empty && empty.length == 0)) {
            return Language.none();
        }
        if (value instanceof Object[] array) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) {
                    text.append(i == array.length - 1 ? " and " : ", ");
                }
                text.append(text(array[i]));
            }
            return text.toString();
        }
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Double || value instanceof Float) {
            return decimal(((Number) value).doubleValue());
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        Function<Object, String> converter = converter(value.getClass());
        return converter != null ? converter.apply(value) : value.toString();
    }

    /**
     * Decimals as Skript shows them: at most the {@linkplain Settings#numberAccuracy number accuracy} of decimal
     * places (two by default), without trailing zeros.
     */
    public static String decimal(double value) {
        return decimal(value, Settings.numberAccuracy());
    }

    /**
     * Skript's {@code StringUtils.toString}: {@code value} with at most {@code accuracy} decimal places, without
     * trailing zeros, so with two -0.004 is -0 and 1.005 is 1.01. An accuracy of 0 rounds to a whole number.
     */
    public static String decimal(double value, int accuracy) {
        if (accuracy <= 0) {
            return Long.toString(Math.round(value));
        }
        String text = String.format(java.util.Locale.ENGLISH,
                accuracy < DECIMAL_FORMATS.length ? DECIMAL_FORMATS[accuracy] : "%." + accuracy + "f", value);
        int end = text.length() - 1;
        while (text.charAt(end) == '0') {
            end--;
        }
        if (text.charAt(end) == '.') {
            end--;
        }
        return text.substring(0, end + 1);
    }

    /** {@code %.0f} to {@code %.16f}, built once: decimals are written in triggers' hot paths. */
    private static final String[] DECIMAL_FORMATS = new String[17];

    static {
        for (int i = 0; i < DECIMAL_FORMATS.length; i++) {
            DECIMAL_FORMATS[i] = "%." + i + "f";
        }
    }

    /** As Skript writes decimals in variable names: up to 8 decimal places, whatever the number accuracy. */
    private static final int NAME_ACCURACY = 8;

    private record Conversion(Class<?> from, Function<Object, Object> converter) {}

    private static final Map<Class<?>, List<Conversion>> CONVERSIONS = new ConcurrentHashMap<>();

    /** Registers how values of {@code from} (and its subtypes) become a {@code to}. */
    @SuppressWarnings("unchecked")
    public static <F, T> void registerConverter(Class<F> from, Class<T> to, Function<? super F, ? extends T> converter) {
        CONVERSIONS.computeIfAbsent(to, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(new Conversion(from, value -> ((Function<Object, Object>) converter).apply(value)));
    }

    /** Whether anything converts to {@code to}, so placeholders of that type need {@link #convert}. */
    public static boolean convertsTo(Class<?> to) {
        return CONVERSIONS.containsKey(to);
    }

    /**
     * Converts what isn't already a {@code to} into one where a converter can, as when a player is used
     * where a location is expected. Values nothing converts are kept, for the element to deal with.
     */
    public static Object convert(Object value, Class<?> to) {
        if (value instanceof Object[] array) {
            Object[] converted = null;
            for (int i = 0; i < array.length; i++) {
                Object element = convertOne(array[i], to);
                if (element != array[i]) {
                    if (converted == null) {
                        converted = array.clone();
                    }
                    converted[i] = element;
                }
            }
            return converted == null ? array : converted;
        }
        return convertOne(value, to);
    }

    private static Object convertOne(Object value, Class<?> to) {
        if (value == null || to.isInstance(value)) {
            return value;
        }
        for (Conversion conversion : CONVERSIONS.getOrDefault(to, List.of())) {
            if (conversion.from().isInstance(value)) {
                Object converted = conversion.converter().apply(value);
                if (converted != null) {
                    return converted;
                }
            }
        }
        return value;
    }

    private static final Map<Class<?>, java.util.function.UnaryOperator<Object>> CLONERS = new ConcurrentHashMap<>();

    /** Registers how values of {@code type} are copied when they're stored in a variable. */
    @SuppressWarnings("unchecked")
    public static <T> void registerCloner(Class<T> type, java.util.function.UnaryOperator<T> cloner) {
        CLONERS.put(type, value -> ((java.util.function.UnaryOperator<Object>) (Object) cloner).apply(value));
    }

    /**
     * The value to store in a variable: a copy of values that can be changed in place, like items, so
     * changing a variable's item doesn't change the item it was set from, as in Skript.
     */
    public static Object copy(Object value) {
        if (value == null || CLONERS.isEmpty()) {
            return value;
        }
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            java.util.function.UnaryOperator<Object> cloner = CLONERS.get(type);
            if (cloner != null) {
                return cloner.apply(value);
            }
        }
        return value;
    }

    private record Changer(Class<?> owner, co.xenastudios.neoskript.api.element.Changeable.Mode mode,
            java.util.function.BiConsumer<Object, Object[]> change) {}

    private static final List<Changer> CHANGERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Registers how {@code mode} changes a value of {@code owner} that's itself the target. */
    @SuppressWarnings("unchecked")
    public static <T> void registerChanger(Class<T> owner, co.xenastudios.neoskript.api.element.Changeable.Mode mode,
            java.util.function.BiConsumer<? super T, Object[]> change) {
        CHANGERS.add(new Changer(owner, mode, (java.util.function.BiConsumer<Object, Object[]>) change));
    }

    /** Whether anything can change values this way, so {@code give X to player} compiles. */
    public static boolean hasChanger(co.xenastudios.neoskript.api.element.Changeable.Mode mode) {
        return CHANGERS.stream().anyMatch(c -> c.mode() == mode);
    }

    /** Changes each target value with the first changer registered for its class and {@code mode}. */
    public static void changeValues(Object targets, Object delta, co.xenastudios.neoskript.api.element.Changeable.Mode mode) {
        Object[] values = array(delta);
        for (Object target : array(targets)) {
            for (Changer changer : CHANGERS) {
                if (changer.mode() == mode && changer.owner().isInstance(target)) {
                    changer.change().accept(target, values);
                    break;
                }
            }
        }
    }

    /**
     * Sets each of {@code targets} that an update changed to what it became, with its type's changer, as
     * Skript's changes in place: {@code enchant the player's tool} sets the slot to the enchanted item.
     */
    public static void setEach(Object targets, Object results) {
        Object[] before = array(targets);
        Object[] after = array(results);
        for (int i = 0; i < before.length && i < after.length; i++) {
            if (after[i] != before[i] && after[i] != null) {
                changeValue(before[i], new Object[] {after[i]}, co.xenastudios.neoskript.api.element.Changeable.Mode.SET);
            }
        }
    }

    /**
     * Changes {@code target} in place with the first changer registered for its class and {@code mode}, as
     * {@code add "x" to {queue}} changes the queue in the variable. Whether there was one.
     */
    public static boolean changeValue(Object target, Object[] values, co.xenastudios.neoskript.api.element.Changeable.Mode mode) {
        for (Changer changer : CHANGERS) {
            if (changer.mode() == mode && changer.owner().isInstance(target)) {
                changer.change().accept(target, values);
                return true;
            }
        }
        return false;
    }

    private static final Map<Class<?>, Function<Object, String>> NAME_CONVERTERS = new ConcurrentHashMap<>();

    /**
     * Registers how values of {@code type} (and its subtypes) read in variable names, where that isn't
     * their text, as Skript's {@code toVariableNameString}: a block is its world and coordinates there.
     */
    @SuppressWarnings("unchecked")
    public static <T> void registerNameText(Class<T> type, Function<? super T, String> toText) {
        NAME_CONVERTERS.put(type, value -> ((Function<Object, String>) toText).apply(value));
    }

    /** How a value reads in a variable's name, as in {@code {_x::%loop-block%}}. */
    public static String nameText(Object value) {
        if (value instanceof Object[] array && array.length > 0) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) {
                    text.append(i == array.length - 1 ? " and " : ", ");
                }
                text.append(nameText(array[i]));
            }
            return text.toString();
        }
        if (value instanceof Double || value instanceof Float) {
            return decimal(((Number) value).doubleValue(), NAME_ACCURACY);
        }
        if (value != null && !NAME_CONVERTERS.isEmpty()) {
            for (Map.Entry<Class<?>, Function<Object, String>> named : NAME_CONVERTERS.entrySet()) {
                if (named.getKey().isInstance(value)) {
                    return named.getValue().apply(value);
                }
            }
        }
        return text(value);
    }

    private static final Map<Class<?>, Function<Object, String>> CONVERTERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Function<Object, String>>> RESOLVED = new ConcurrentHashMap<>();

    /** Registers how values of {@code type} (and its subtypes) read as text. */
    @SuppressWarnings("unchecked")
    public static <T> void registerText(Class<T> type, Function<? super T, String> toText) {
        CONVERTERS.put(type, value -> ((Function<Object, String>) toText).apply(value));
        RESOLVED.clear();
    }

    private static Function<Object, String> converter(Class<?> type) {
        return RESOLVED.computeIfAbsent(type, t -> {
            for (Class<?> c = t; c != null; c = c.getSuperclass()) {
                if (CONVERTERS.containsKey(c)) {
                    return Optional.of(CONVERTERS.get(c));
                }
                for (Class<?> i : c.getInterfaces()) {
                    Function<Object, String> found = interfaceConverter(i);
                    if (found != null) {
                        return Optional.of(found);
                    }
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    private static Function<Object, String> interfaceConverter(Class<?> type) {
        if (CONVERTERS.containsKey(type)) {
            return CONVERTERS.get(type);
        }
        for (Class<?> parent : type.getInterfaces()) {
            Function<Object, String> found = interfaceConverter(parent);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** A section that runs later, as a {@link Runnable}: it carries on from {@code frame} in {@code code}. */
    public static Object section(Resumable code, co.xenastudios.neoskript.api.element.TriggerContext context, Frame frame) {
        return (Runnable) () -> code.resume(context, frame);
    }

    /**
     * The code under an addon's section that runs later, from {@code frame}: with a copy of the trigger's local
     * variables as they are now, which every run shares, as Skript's {@code copyLocalVariables} once.
     */
    public static Object laterSection(Resumable code, Frame frame, String script, Object version) {
        frame.locals = frame.locals == null ? null : frame.locals.copy();
        return new LaterCode(code, frame, script, version);
    }

    /** Not a thread local: what's kept may be one of NeoSkript's values, which would keep the plugin after it's gone. */
    private static final PerThread<Object> RETURNED = new PerThread<>();

    /** Keeps what a returnable section returned, for Skript's test expression {@code the last return values}. */
    public static void returned(Object values) {
        RETURNED.set(values);
    }

    /** What the last returnable section returned, once: reading it forgets it, as in Skript. */
    public static Object takeReturned() {
        Object values = RETURNED.get();
        RETURNED.set(null);
        return values;
    }

    /** Numbers as whole numbers, cut short as Skript's number to integer converter does: 2.5 is 2. */
    public static Object wholeNumbers(Object value) {
        if (value instanceof Object[] array) {
            Object[] whole = array.clone();
            for (int i = 0; i < whole.length; i++) {
                whole[i] = wholeNumbers(whole[i]);
            }
            return whole;
        }
        return value instanceof Number number && !(value instanceof Long) ? (Object) number.longValue() : value;
    }
}
