/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.config;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;

/** Adds settings to an existing file through Bukkit, keeping the owner's values and comments. */
final class Writer {

    static final String VERSION_KEY = "config-version";

    private Writer() {}

    /** Adds one setting, with its comment, to an existing file. */
    static void add(ConfigSpec<?> spec, ConfigurationSection parent, String key, RecordComponent component, Object value) {
        write(spec, parent, key, component, value, depth(parent));
    }

    private static void write(ConfigSpec<?> spec, ConfigurationSection target, String key, RecordComponent component,
            Object value, int depth) {
        SectionCodec<?> owned = spec.sectionCodec(component.getType());
        boolean section = spec.isSection(component.getType()) || owned != null;
        if (owned != null) {
            target.set(key, plain(owned, value));
        } else if (section) {
            ConfigurationSection child = target.createSection(key);
            for (RecordComponent nested : value.getClass().getRecordComponents()) {
                write(spec, child, Names.key(nested), nested, get(nested, (Record) value), depth + 1);
            }
        } else if (value instanceof List<?> list) {
            target.set(key, list.stream().map(item -> scalar(spec, item)).toList());
        } else {
            target.set(key, scalar(spec, value));
        }
        Comment comment = component.getAnnotation(Comment.class);
        List<String> lines = new ArrayList<>();
        // A blank line above each top-level setting and each commented section.
        if (depth == 0 || (section && comment != null)) {
            lines.add(null);
        }
        if (comment != null) {
            lines.addAll(Arrays.asList(comment.value()));
        }
        if (!lines.isEmpty()) {
            target.setComments(key, lines);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object plain(SectionCodec<?> codec, Object value) {
        return ((SectionCodec<Object>) codec).plain(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object scalar(ConfigSpec<?> spec, Object value) {
        Codec codec = spec.codec(value.getClass());
        if (codec != null) {
            return codec.encode(value);
        }
        if (value instanceof Enum<?> constant) {
            return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
        return value;
    }

    private static Object get(RecordComponent component, Record from) {
        try {
            return component.getAccessor().invoke(from);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int depth(ConfigurationSection section) {
        String path = section.getCurrentPath();
        return path == null || path.isEmpty() ? 0 : path.split("\\.").length;
    }

    static String enumNames(Class<?> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(constant -> ((Enum<?>) constant).name().toLowerCase(Locale.ROOT).replace('_', '-'))
                .collect(Collectors.joining(", "));
    }

    static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
