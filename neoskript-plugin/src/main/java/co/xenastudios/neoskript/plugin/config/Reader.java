/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Reads YAML into a config record. A value that's missing or can't be used falls back to its default; an
 * unusable one is also reported as a warning.
 */
final class Reader {

    /** A setting the file doesn't have, to add with its default. */
    record Missing(ConfigurationSection parent, String key, RecordComponent component, Object value) {}

    /**
     * What stands for a key with nothing after it in {@link #blankTree}. Bukkit drops such keys, so this is how
     * "prefix:" (meaning blank) is told apart from a setting that isn't there.
     */
    static final String BLANK = "\u0000blank";

    private final ConfigSpec<?> spec;
    // The file with BLANK for keys with no value, migrated as the file is.
    private final ConfigurationSection blankTree;
    final List<String> warnings = new ArrayList<>();
    final List<Missing> missing = new ArrayList<>();
    /** The paths of text settings left empty that may be. */
    final Set<String> blanks = new LinkedHashSet<>();
    /** The paths of keys that aren't settings, in the order they're found. */
    final List<String> unknown = new ArrayList<>();

    Reader(ConfigSpec<?> spec, ConfigurationSection blankTree) {
        this.spec = spec;
        this.blankTree = blankTree;
    }

    <R extends Record> R readRoot(Class<R> type, ConfigurationSection root, R defaults) {
        return readRecord(type, root, defaults, "");
    }

    private <R extends Record> R readRecord(Class<R> type, Object raw, R fallback, String path) {
        if (!(raw instanceof ConfigurationSection section)) {
            warn(path, raw, "should be a section of settings");
            return fallback;
        }
        RecordComponent[] components = type.getRecordComponents();
        Object[] values = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            types[i] = component.getType();
            String key = Names.key(component);
            Object defaultValue = get(component, fallback);
            if (!section.contains(key)) {
                boolean empty = isBlank(join(path, key));
                if (empty && component.getType() == String.class && component.isAnnotationPresent(AllowBlank.class)) {
                    values[i] = "";
                    blanks.add(join(path, key));
                    continue;
                }
                values[i] = defaultValue;
                if (empty) {
                    // Often a colour like "highlight: #FF4D9D", which YAML reads as a comment.
                    warn(join(path, key), null, "is empty; quote values that start with #");
                    continue;
                }
                missing.add(new Missing(section, key, component, defaultValue));
                continue;
            }
            values[i] = read(component.getGenericType(), component, section.get(key), defaultValue, join(path, key));
        }
        Set<String> known = new java.util.HashSet<>();
        for (RecordComponent component : components) {
            known.add(Names.key(component));
        }
        for (String key : section.getKeys(false)) {
            if (!known.contains(key) && !(path.isEmpty() && key.equals(Writer.VERSION_KEY))) {
                unknown.add(join(path, key));
            }
        }
        try {
            return type.getDeclaredConstructor(types).newInstance(values);
        } catch (InvocationTargetException e) {
            // The record rejected the combination.
            if (e.getCause() instanceof IllegalArgumentException reason) {
                warnings.add("%s: %s; using the defaults for this section.".formatted(display(path), reason.getMessage()));
                return fallback;
            }
            throw new IllegalStateException("couldn't build " + type.getName(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("couldn't build " + type.getName(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object read(Type type, RecordComponent component, Object raw, Object fallback, String path) {
        if (raw == null) {
            warn(path, null, "is empty");
            return fallback;
        }
        if (type instanceof Class<?> c && spec.isSection(c)) {
            return readRecord((Class) c, raw, (Record) fallback, path);
        }
        if (type instanceof ParameterizedType p && p.getRawType() == List.class) {
            return readList((Class<?>) p.getActualTypeArguments()[0], raw, path);
        }
        try {
            Object value = scalar((Class<?>) type, raw);
            if (value instanceof String text && text.isEmpty() && (component == null
                    || !component.isAnnotationPresent(AllowBlank.class))) {
                throw new IllegalArgumentException("is empty");
            }
            Range range = component == null ? null : component.getAnnotation(Range.class);
            if (range != null && value instanceof Number number) {
                double d = number.doubleValue();
                if (d < range.min() || d > range.max()) {
                    throw new IllegalArgumentException("must be " + bounds(range));
                }
            }
            return value;
        } catch (IllegalArgumentException e) {
            warn(path, raw, e.getMessage());
            return fallback;
        }
    }

    private Object readList(Class<?> element, Object raw, String path) {
        // A single value where a list belongs is a list of one.
        List<?> items = raw instanceof List<?> list ? list : List.of(raw);
        List<Object> values = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            try {
                if (items.get(i) == null) {
                    throw new IllegalArgumentException("is empty");
                }
                values.add(scalar(element, items.get(i)));
            } catch (IllegalArgumentException e) {
                // Drop just the bad item.
                warn(path + "[" + i + "]", items.get(i), e.getMessage() + ", so it's skipped");
            }
        }
        return List.copyOf(values);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object scalar(Class<?> type, Object raw) {
        if (raw instanceof ConfigurationSection || raw instanceof List<?> || raw instanceof Map<?, ?>) {
            throw new IllegalArgumentException("should be a single value");
        }
        Codec<?> codec = spec.codec(type);
        if (codec != null) {
            return codec.decode(raw);
        }
        if (type == String.class) {
            return raw.toString();
        }
        if (type == boolean.class) {
            if (raw instanceof Boolean b) {
                return b;
            }
            return switch (raw.toString().toLowerCase(Locale.ROOT)) {
                case "true", "yes", "on" -> true;
                case "false", "no", "off" -> false;
                default -> throw new IllegalArgumentException("should be true or false");
            };
        }
        if (type == int.class || type == long.class) {
            long value = wholeNumber(raw);
            if (type == int.class && (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)) {
                throw new IllegalArgumentException("is too large");
            }
            return type == int.class ? (Object) (int) value : (Object) value;
        }
        if (type == double.class) {
            if (raw instanceof Number n) {
                return n.doubleValue();
            }
            try {
                return Double.parseDouble(raw.toString().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("should be a number");
            }
        }
        if (type.isEnum()) {
            String name = raw.toString().trim().replace('-', '_').toUpperCase(Locale.ROOT);
            for (Object constant : type.getEnumConstants()) {
                if (((Enum) constant).name().equals(name)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("should be one of " + Writer.enumNames(type));
        }
        throw new IllegalStateException("no reader for " + type);
    }

    private static long wholeNumber(Object raw) {
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte) {
            return ((Number) raw).longValue();
        }
        if (raw instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return n.longValue();
        }
        try {
            return Long.parseLong(raw.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("should be a whole number");
        }
    }

    private void warn(String path, Object raw, String reason) {
        String value = raw == null ? "" : " (\"" + raw + "\")";
        warnings.add("%s%s %s; using the default.".formatted(display(path), value, reason));
    }

    private static String bounds(Range range) {
        if (range.min() == Double.NEGATIVE_INFINITY) {
            return "at most " + Writer.number(range.max());
        }
        if (range.max() == Double.POSITIVE_INFINITY) {
            return "at least " + Writer.number(range.min());
        }
        return "from " + Writer.number(range.min()) + " to " + Writer.number(range.max());
    }

    /** Whether the file has this key with nothing after it. */
    private boolean isBlank(String path) {
        return blankTree != null && BLANK.equals(blankTree.get(path));
    }

    private static Object get(RecordComponent component, Record from) {
        try {
            return component.getAccessor().invoke(from);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String join(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    private static String display(String path) {
        return path.isEmpty() ? "The config" : path;
    }
}
