/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.registration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** How {@link Registrar#property} and {@link Registrar#propertyCondition} evaluate. */
final class Properties {

    private Properties() {}

    static <T> Object map(Object owners, Class<T> ownerClass, Function<? super T, ?> getter) {
        List<Object> values = new ArrayList<>();
        for (Object owner : array(owners)) {
            if (ownerClass.isInstance(owner)) {
                Object value = getter.apply(ownerClass.cast(owner));
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return values.size() == 1 ? values.getFirst() : values.toArray();
    }

    static <T> boolean test(Object owners, Class<T> ownerClass, Predicate<? super T> test, boolean negated) {
        Object[] array = array(owners);
        if (array.length == 0) {
            return false;
        }
        for (Object owner : array) {
            boolean holds = ownerClass.isInstance(owner) && test.test(ownerClass.cast(owner));
            if (holds == negated) {
                return false;
            }
        }
        return true;
    }

    private static Object[] array(Object value) {
        if (value instanceof Object[] array) {
            return array;
        }
        return value == null ? new Object[0] : new Object[] {value};
    }
}
