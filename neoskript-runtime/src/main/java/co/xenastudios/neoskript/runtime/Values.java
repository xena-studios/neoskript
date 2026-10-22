/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.util.ArrayList;
import java.util.List;

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

    /** How a value reads in text, as when embedded in a string with {@code %...%}. */
    public static String text(Object value) {
        if (value == null) {
            return "<none>";
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
        if (value instanceof Double d && d == Math.rint(d) && !Double.isInfinite(d)) {
            return Long.toString(d.longValue());
        }
        return value.toString();
    }
}
