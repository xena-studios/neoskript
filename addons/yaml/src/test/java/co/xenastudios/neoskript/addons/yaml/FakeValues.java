/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import co.xenastudios.neoskript.api.Values;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** NeoSkript's values, as far as the tests need: timespans are Durations, other values are saved as their text. */
final class FakeValues implements Values {

    /** A value no type has, saved by Skript as its text. */
    record Thing(String name) {}

    @Override
    public String type(Object value) {
        return switch (value) {
            case Duration duration -> "timespan";
            case Instant instant -> "date";
            case Thing thing -> "thing";
            default -> null;
        };
    }

    @Override
    public String text(Object value) {
        return value instanceof Duration duration ? duration.toSeconds() + " seconds" : String.valueOf(value);
    }

    @Override
    public Object parse(String text, String typeId) {
        if (typeId.equals("timespan") && text.endsWith(" seconds")) {
            return Duration.ofSeconds(Long.parseLong(text.substring(0, text.indexOf(' '))));
        }
        return null;
    }

    @Override
    public Object convert(Object value, Class<?> to) {
        return to.isInstance(value) ? value : null;
    }

    @Override
    public Object toJava(Object value) {
        return value;
    }

    @Override
    public Object fromJava(Object value) {
        return value;
    }

    @Override
    public Saved save(Object value) {
        return value instanceof Thing thing ? new Saved("thing", thing.name().getBytes(StandardCharsets.UTF_8)) : null;
    }

    @Override
    public Object load(String type, byte[] data) {
        return type.equals("thing") ? new Thing(new String(data, StandardCharsets.UTF_8)) : null;
    }
}
