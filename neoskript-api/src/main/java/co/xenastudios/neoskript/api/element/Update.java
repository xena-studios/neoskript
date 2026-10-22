/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

import java.util.function.BiFunction;

/**
 * An effect that works out new values for one of its placeholders, which is then set to them, as
 * {@code set} would: {@code replace "a" in {_x} with "b"} sets {@code {_x}}. The placeholder has to be
 * something that can be set, like a variable.
 */
public interface Update extends Effect {

    /** The index of the placeholder that's set. */
    int target();

    /** The new values for the target, given every placeholder's value. */
    Object update(TriggerContext context, Object[] arguments);

    /** Updates are compiled into a {@code set}; this is only for running one directly. */
    @Override
    default void execute(TriggerContext context, Object[] arguments) {
        update(context, arguments);
    }

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
