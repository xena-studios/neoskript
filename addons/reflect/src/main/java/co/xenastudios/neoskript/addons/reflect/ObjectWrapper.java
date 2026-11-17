/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import java.lang.reflect.Array;
import java.util.Objects;
import java.util.function.Function;

/**
 * A Java array as one value, skript-reflect's {@code javaobject}: a method that returns an array gives one value, the
 * array, as in skript-reflect, rather than each of its elements; {@code ...} spreads it.
 *
 * @param array the array, of objects (a primitive array is boxed first)
 */
public record ObjectWrapper(Object array) {

    /** Wraps an array, boxing a primitive one, as skript-reflect does. */
    static ObjectWrapper of(Object array) {
        return new ObjectWrapper(Coercion.boxPrimitiveArray(array));
    }

    /** The array's elements, written with {@code text} and joined as {@code [a, b]}. */
    String text(Function<Object, String> text) {
        int length = Array.getLength(array);
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(text.apply(Array.get(array, i)));
        }
        return out.append(']').toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ObjectWrapper that && Objects.deepEquals(array, that.array);
    }

    @Override
    public int hashCode() {
        return array instanceof Object[] objects ? java.util.Arrays.deepHashCode(objects) : Objects.hashCode(array);
    }

    @Override
    public String toString() {
        return text(String::valueOf);
    }
}
