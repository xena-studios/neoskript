/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

/** A condition. Arguments are passed as for {@link Effect}. */
@FunctionalInterface
public interface Condition {

    boolean check(TriggerContext context, Object[] arguments);

    /**
     * Whether {@code test} holds for all of {@code values} ({@code and}) or any of them, as Skript checks a
     * list; never for no values. Negate the result, not the test, for {@code doesn't}: {@code "hey" doesn't
     * start with "hey" and "e"} is true.
     */
    static boolean check(Object[] values, boolean and, java.util.function.Predicate<Object> test) {
        boolean any = false;
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            any = true;
            if (test.test(value) != and) {
                return !and;
            }
        }
        return any && and;
    }
}
