/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * A property expression whose changes are spelled out mode by mode, as Skript's own property expressions
 * do: {@code getter} for each owner, and {@code changer} with each owner, the values given and the mode.
 * Made {@link #defaulting()}, its owners can be left out ({@code [of %livingentities%]}) for the event's entity or
 * block.
 */
final class Property<T> implements Expression, Changeable {

    /** Changes one owner. {@code delta} is {@code null} for {@code delete} and {@code reset}. */
    @FunctionalInterface
    interface Changer<T> {
        void change(T owner, Object[] delta, Mode mode);
    }

    private final Class<T> ownerClass;
    private final Function<? super T, ?> getter;
    private final Class<?> valueClass;
    private final Set<Mode> modes;
    private final Changer<T> changer;
    private int ownerArgument;
    private boolean defaulting;

    /** A property that can't be changed. */
    Property(Class<T> ownerClass, Function<? super T, ?> getter) {
        this(ownerClass, getter, null, EnumSet.noneOf(Mode.class), null);
    }

    /**
     * @param valueClass the class of the values {@code set}, {@code add}... take
     * @param modes the modes it can be changed in
     */
    Property(Class<T> ownerClass, Function<? super T, ?> getter, Class<?> valueClass, Set<Mode> modes,
            Changer<T> changer) {
        this.ownerClass = ownerClass;
        this.getter = getter;
        this.valueClass = valueClass;
        this.modes = modes;
        this.changer = changer;
    }

    /** This property, whose owners, where they're left out, are the event's entity or block. */
    Property<T> defaulting() {
        this.defaulting = true;
        return this;
    }

    /** This property, with its owners in argument {@code index}. */
    Property<T> ownersAt(int index) {
        this.ownerArgument = index;
        return this;
    }

    @Override
    public Object get(TriggerContext context, Object[] arguments) {
        List<Object> values = new ArrayList<>();
        for (Object owner : owners(context, arguments)) {
            if (ownerClass.isInstance(owner)) {
                Object value = getter.apply(ownerClass.cast(owner));
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return single(values);
    }

    @Override
    public Class<?>[] acceptChange(Mode mode) {
        if (!modes.contains(mode)) {
            return null;
        }
        return mode == Mode.DELETE || mode == Mode.RESET ? new Class<?>[0] : new Class<?>[] {valueClass};
    }

    @Override
    public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
        if (delta != null && delta.length == 0) {
            delta = null;
        }
        if (delta == null && mode != Mode.DELETE && mode != Mode.RESET) {
            return;
        }
        for (Object owner : owners(context, arguments)) {
            if (ownerClass.isInstance(owner)) {
                changer.change(ownerClass.cast(owner), delta, mode);
            }
        }
    }

    private Object[] owners(TriggerContext context, Object[] arguments) {
        Object owners = ownerArgument < arguments.length ? arguments[ownerArgument] : null;
        if (owners == null && defaulting) {
            Object value = eventValue(context);
            return value == null ? new Object[0] : new Object[] {value};
        }
        return of(owners);
    }

    /**
     * The event's entity, player or block, which property expressions whose owners can be left out use, as
     * Skript's default expressions do.
     */
    static Object eventValue(TriggerContext context) {
        return switch (context.event()) {
            case org.bukkit.event.entity.EntityEvent event -> event.getEntity();
            case org.bukkit.event.player.PlayerEvent event -> event.getPlayer();
            case org.bukkit.event.block.BlockEvent event -> event.getBlock();
            case null, default -> null;
        };
    }

    /** {@code value} as an array: its values if it's one, nothing for {@code null}. */
    static Object[] of(Object value) {
        return value instanceof Object[] array ? array : value == null ? new Object[0] : new Object[] {value};
    }

    /** {@code values} as an expression returns them: the one value alone, else an array. */
    static Object single(List<Object> values) {
        return values.size() == 1 ? values.getFirst() : values.toArray();
    }

    /** The modes {@code modes}, for {@link #Property(Class, Function, Class, Set, Changer)}. */
    static Set<Mode> modes(Mode... modes) {
        return modes.length == 0 ? EnumSet.noneOf(Mode.class) : EnumSet.of(modes[0], modes);
    }

    /** Skript's property patterns whose owners can be left out: {@code [the] <property> [of %owners%]}. */
    static String[] defaultPatterns(String property, String owners) {
        return new String[] {"[the] " + property + " [of %" + owners + "%]", "%" + owners + "%'[s] " + property};
    }

    /** {@code value} in ticks, for a {@code timespan}. */
    static co.xenastudios.neoskript.runtime.Timespan ticks(long ticks) {
        return new co.xenastudios.neoskript.runtime.Timespan(ticks * 50);
    }

    /** {@code value}, a number given to a change, as an int, as Java casts it. */
    static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /** {@code value} kept between {@code min} and {@code max}, as Skript's {@code Math2.fit} does. */
    static long fit(long min, long value, long max) {
        return Math.max(min, Math.min(value, max));
    }
}
