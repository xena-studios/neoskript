/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import co.xenastudios.neoskript.api.NeoSkript;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.skriptlang.skript.lang.converter.Converter;

/** Conversions addons register, on top of NeoSkript's own. */
public final class CompatConversions {

    /** A conversion an addon registered. */
    public record Conversion(Class<?> from, Class<?> to, Converter<Object, Object> converter, int flags) {}

    private final List<Conversion> conversions = new CopyOnWriteArrayList<>();

    CompatConversions() {}

    @SuppressWarnings("unchecked")
    public <F, T> void register(Class<F> from, Class<T> to, Converter<F, T> converter, int flags) {
        conversions.add(new Conversion(from, to, (Converter<Object, Object>) converter, flags));
    }

    public boolean exists(Class<?> from, Class<?> to, boolean exact) {
        for (Conversion conversion : conversions) {
            if (exact ? conversion.from() == from && conversion.to() == to
                    : conversion.from().isAssignableFrom(from) && to.isAssignableFrom(conversion.to())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public <T> T convert(Object value, Class<T> to) {
        if (value == null) {
            return null;
        }
        if (to.isInstance(value)) {
            return (T) value;
        }
        for (Conversion conversion : conversions) {
            if (conversion.from().isInstance(value) && to.isAssignableFrom(conversion.to())) {
                Object converted = conversion.converter().convert(value);
                if (converted != null) {
                    return (T) converted;
                }
            }
        }
        Object converted = NeoSkript.get().values().convert(value, to);
        return to.isInstance(converted) ? (T) converted : null;
    }

    List<Conversion> all() {
        return List.copyOf(conversions);
    }
}
