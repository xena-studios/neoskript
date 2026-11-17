/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.config;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Writes the config the way a person would: strings in double quotes, short lists inline, a comment above each
 * setting and a blank line between sections. (Bukkit's writer leaves strings bare and puts every list item on
 * its own line.)
 *
 * <p>Given an existing file, it writes that file's values in the current layout: what the owner set is kept,
 * even a value that isn't valid (it's warned about when read), and what's missing gets its default. Keys that
 * aren't settings, such as one a newer NeoSkript added, are kept as they were, with their comments, at the end
 * of the section they were in.
 */
final class Emitter {

    static final String VERSION_COMMENT = "Managed by NeoSkript, to carry your settings forward on updates. Don't edit it.";

    private static final Pattern PLAIN_KEY = Pattern.compile("[A-Za-z0-9_-]+");
    private static final String INDENT = "  ";

    private final ConfigSpec<?> spec;
    private final StringBuilder out = new StringBuilder();

    private Emitter(ConfigSpec<?> spec) {
        this.spec = spec;
    }

    /** The default config. */
    static String render(ConfigSpec<?> spec) {
        return render(spec, null);
    }

    /**
     * The config with the values in {@code existing} where it has them, or the defaults if it's {@code null}. A
     * text setting that may be empty and was left empty ({@code prefix:}) is written as {@code ""}; any other
     * setting left empty gets its default.
     */
    static String render(ConfigSpec<?> spec, ConfigurationSection existing) {
        Emitter emitter = new Emitter(spec);
        for (String line : spec.header) {
            emitter.out.append(line.isEmpty() ? "#" : "# " + line).append('\n');
        }
        if (!spec.header.isEmpty()) {
            emitter.out.append('\n');
        }
        emitter.comment(0, VERSION_COMMENT);
        emitter.out.append(Writer.VERSION_KEY).append(": ").append(spec.version).append('\n');
        emitter.record(spec.defaults, existing, "", 0);
        return emitter.out.toString();
    }

    private void record(Record value, ConfigurationSection existing, String path, int depth) {
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            Object child = get(component, value);
            String key = Names.key(component);
            String at = path.isEmpty() ? key : path + "." + key;
            boolean section = spec.isSection(component.getType());
            Comment comment = component.getAnnotation(Comment.class);
            // A blank line above each top-level setting and each commented section.
            if (depth == 0 || (section && comment != null)) {
                out.append('\n');
            }
            if (comment != null) {
                for (String line : comment.value()) {
                    comment(depth, line);
                }
            }
            if (section) {
                indent(depth).append(key(key)).append(":\n");
                ConfigurationSection nested = existing == null ? null : existing.getConfigurationSection(key);
                record((Record) child, nested, at, depth + 1);
                continue;
            }
            Object raw = existing == null ? null : existing.get(key);
            String text;
            if (Reader.BLANK.equals(raw)) {
                text = component.isAnnotationPresent(AllowBlank.class) ? "\"\"" : null;
            } else {
                text = raw == null ? null : raw(raw);
            }
            indent(depth).append(key(key)).append(": ").append(text != null ? text : scalarOrList(child)).append('\n');
        }
        if (existing == null) {
            return;
        }
        java.util.Set<String> known = new java.util.HashSet<>();
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            known.add(Names.key(component));
        }
        for (String key : existing.getKeys(false)) {
            if (!known.contains(key) && !(depth == 0 && key.equals(Writer.VERSION_KEY))) {
                if (depth == 0) {
                    out.append('\n');
                }
                unknown(existing, key, depth);
            }
        }
    }

    /** A key that isn't a setting, with its comments, as it was. */
    private void unknown(ConfigurationSection parent, String key, int depth) {
        for (String line : parent.getComments(key)) {
            if (line == null) {
                out.append('\n');
            } else {
                comment(depth, line.strip());
            }
        }
        Object value = parent.get(key);
        if (value instanceof ConfigurationSection section) {
            indent(depth).append(key(key)).append(":\n");
            for (String child : section.getKeys(false)) {
                unknown(section, child, depth + 1);
            }
            return;
        }
        List<String> inline = parent.getInlineComments(key);
        String after = inline.isEmpty() || inline.getFirst() == null ? "" : " # " + inline.getFirst().strip();
        if (value == null || Reader.BLANK.equals(value)) {
            // "foo:" or "foo: ~", kept empty.
            indent(depth).append(key(key)).append(":").append(after).append('\n');
            return;
        }
        String text = raw(value);
        if (text != null) {
            indent(depth).append(key(key)).append(": ").append(text).append(after).append('\n');
            return;
        }
        // A list of lists or of maps: as YAML writes it, under the key.
        org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
        options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        String dumped = new org.yaml.snakeyaml.Yaml(options).dump(plain(value));
        indent(depth).append(key(key)).append(":\n");
        for (String line : dumped.split("\n")) {
            indent(depth + 1).append(line).append('\n');
        }
    }

    /** {@code value} with Bukkit's sections as plain maps, for SnakeYAML. */
    private static Object plain(Object value) {
        if (value instanceof ConfigurationSection section) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                map.put(key, plain(section.get(key)));
            }
            return map;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            java.util.Map<Object, Object> copy = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(k, plain(v)));
            return copy;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(Emitter::plain).toList();
        }
        return value;
    }

    /** An owner's value as written, or {@code null} if it isn't one value or a list of them. */
    private static String raw(Object raw) {
        if (raw instanceof List<?> list) {
            StringBuilder flow = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                String item = list.get(i) == null ? null : raw(list.get(i));
                if (item == null || list.get(i) instanceof List<?>) {
                    return null;
                }
                flow.append(i == 0 ? "" : ", ").append(item);
            }
            return flow.append(']').toString();
        }
        if (raw instanceof Boolean || raw instanceof Integer || raw instanceof Long) {
            return raw.toString();
        }
        if (raw instanceof Double d) {
            return Writer.number(d);
        }
        if (raw instanceof String s) {
            return quote(s);
        }
        return null;
    }

    private String scalarOrList(Object value) {
        if (value instanceof List<?> list) {
            StringBuilder flow = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                flow.append(i == 0 ? "" : ", ").append(scalar(list.get(i)));
            }
            return flow.append(']').toString();
        }
        return scalar(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String scalar(Object value) {
        if (value == null) {
            throw new IllegalStateException("a default is null");
        }
        Codec codec = spec.codec(value.getClass());
        if (codec != null) {
            value = codec.encode(value);
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            return value.toString();
        }
        if (value instanceof Double d) {
            return Writer.number(d);
        }
        if (value instanceof Enum<?> constant) {
            return quote(constant.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        return quote(value.toString());
    }

    private void comment(int depth, String line) {
        indent(depth).append(line.isEmpty() ? "#" : "# " + line).append('\n');
    }

    private StringBuilder indent(int depth) {
        return out.append(INDENT.repeat(depth));
    }

    private static String key(String key) {
        return PLAIN_KEY.matcher(key).matches() ? key : quote(key);
    }

    static String quote(String text) {
        StringBuilder quoted = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (c < 0x20) {
                        quoted.append(String.format("\\x%02x", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    private static Object get(RecordComponent component, Record from) {
        try {
            return component.getAccessor().invoke(from);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
