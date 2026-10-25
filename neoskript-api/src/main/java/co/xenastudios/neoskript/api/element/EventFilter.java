/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

import java.util.List;

/**
 * An event's filter that also narrows which of the event's classes its trigger listens to, as Skript's events
 * choose their classes from what's written: {@code on player move} listens only to player moves, while
 * {@code on zombie move} listens to entity moves. Code in the trigger is checked against those classes.
 */
public interface EventFilter extends Condition {

    /** The classes to listen to: some of the event's classes. */
    List<Class<?>> eventClasses();

    /** A filter listening to {@code classes} that passes when {@code check} does. */
    static EventFilter of(List<Class<?>> classes, Condition check) {
        List<Class<?>> copy = List.copyOf(classes);
        return new EventFilter() {
            @Override
            public List<Class<?>> eventClasses() {
                return copy;
            }

            @Override
            public boolean check(TriggerContext context, Object[] arguments) {
                return check.check(context, arguments);
            }
        };
    }
}
