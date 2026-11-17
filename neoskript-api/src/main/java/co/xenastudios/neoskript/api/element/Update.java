/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

import java.util.function.BiFunction;

/**
 * An effect that works out new values for one of its placeholders, which is then set to them, as
 * {@code set} would: {@code replace "a" in {_x} with "b"} sets {@code {_x}}. The placeholder has to be
 * something that can be set, like a variable.
 *
 * <p>When the target is a list variable and the update gives a {@link java.util.Map}, the map's values go in at the
 * indices it names, and the list's other indices are left as they are: each key is an index, and a value that's a
 * map, a collection or an array makes a list under its index ({@code {"home": {"x": 10}}} sets
 * {@code {_data::home::x}}).
 */
public interface Update extends Effect {

    /**
     * The placeholder that's set.
     *
     * @return its index
     */
    int target();

    /**
     * The new values for the target.
     *
     * @param context the running trigger
     * @param arguments every placeholder's value, by index
     * @return one value, an {@code Object[]} of several, or {@code null} for none
     */
    Object update(TriggerContext context, Object[] arguments);

    /**
     * Updates are compiled into a {@code set}; this is only for running one directly.
     *
     * @param context the running trigger
     * @param arguments every placeholder's value, by index
     */
    @Override
    default void execute(TriggerContext context, Object[] arguments) {
        update(context, arguments);
    }

    /**
     * Whether the target has to be one that can be set. If not, the effect does its work on the values
     * themselves where it can't be ({@code zombify last spawned villager}), as Skript's do.
     *
     * @return whether it has to be settable
     */
    default boolean setRequired() {
        return true;
    }

    /**
     * An update whose target needn't be settable: see {@link #setRequired}.
     *
     * @param target the index of the placeholder that's set
     * @param update the new values for it, given every placeholder's value
     * @return the update
     */
    static Update inPlace(int target, BiFunction<TriggerContext, Object[], Object> update) {
        return new Update() {
            @Override
            public int target() {
                return target;
            }

            @Override
            public Object update(TriggerContext context, Object[] arguments) {
                return update.apply(context, arguments);
            }

            @Override
            public boolean setRequired() {
                return false;
            }
        };
    }

    /**
     * An update whose target has to be settable.
     *
     * @param target the index of the placeholder that's set
     * @param update the new values for it, given every placeholder's value
     * @return the update
     */
    static Update of(int target, BiFunction<TriggerContext, Object[], Object> update) {
        return new Update() {
            @Override
            public int target() {
                return target;
            }

            @Override
            public Object update(TriggerContext context, Object[] arguments) {
                return update.apply(context, arguments);
            }
        };
    }
}
