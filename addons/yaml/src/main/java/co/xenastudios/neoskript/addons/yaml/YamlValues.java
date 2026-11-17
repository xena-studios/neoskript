/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import co.xenastudios.neoskript.api.Values;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.yaml.snakeyaml.nodes.Node;

/**
 * Script values in YAML files, both ways, as skript-yaml stores them: numbers, texts, booleans, lists and nodes as
 * YAML's own, locations, vectors, block data and Bukkit's serializable values (items) as Bukkit writes them, and
 * Skript's own types with skript-yaml's tags: {@code !skripttimespan}, {@code !skriptdate}, {@code !skripttime},
 * {@code !skriptcolor}, {@code !skriptweather}, and {@code !skriptclass} with the bytes {@code variables.csv} would
 * have for anything else.
 */
final class YamlValues {

    private YamlValues() {}

    /** A value written as a tagged scalar, as {@code !skripttimespan 5 minutes}. */
    record Tagged(String tag, String text) {}

    /** A value written as Skript saves it: {@code !skriptclass} with its type and its bytes in Base64. */
    record SkriptClass(String type, String data) {}

    /** A value read that couldn't be made into one (a location in a world that isn't loaded): written back as read. */
    record Kept(Node node) {}

    private static final Pattern BOOLEAN = Pattern.compile("true|false|yes|no|on|off");
    private static final Pattern WHOLE = Pattern.compile("(-)?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("(-)?\\d+(\\.\\d+)");

    // ---------------------------------------------------------------- scripts to files

    /**
     * What a value is stored as, as skript-yaml's {@code StringUtil.parseString} reads text set without
     * {@code without string checks}: {@code "true"} is a boolean, {@code "5"} a number, and colour codes ({@code §a})
     * go back to {@code &a}.
     */
    static Object fromScript(Object value, boolean checks) {
        if (value == null || checks || !(value instanceof String text)) {
            return value;
        }
        String s = colourCodesBack(text);
        if (BOOLEAN.matcher(s).matches()) {
            return s.equals("true") || s.equals("yes") || s.equals("on");
        }
        if (WHOLE.matcher(s).matches()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException tooBig) {
                // skript-yaml keeps the text as given then, with its colour codes.
                return value;
            }
        }
        if (DECIMAL.matcher(s).matches()) {
            return Double.parseDouble(s);
        }
        return s;
    }

    /** {@code §a} as {@code &a}, as skript-yaml's {@code translateColorCodesBack}. */
    static String colourCodesBack(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '§' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(chars[i + 1]) > -1) {
                chars[i] = '&';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    /** {@code &a} as {@code §a}, as Bukkit's {@code ChatColor.translateAlternateColorCodes('&', ...)}. */
    static String colourCodes(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) > -1) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    /**
     * A stored value as the representer writes it. Nothing stored changes: skript-yaml replaces values in memory
     * with what it writes, so a value read after a save could be its {@code SkriptClass}.
     */
    static Object toYaml(Object value, Values values) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number
                || value instanceof Character || value instanceof UUID || value instanceof byte[]
                || value instanceof java.util.Date || value instanceof Tagged || value instanceof SkriptClass
                || value instanceof Kept || value instanceof Vector || value instanceof BlockData
                || value instanceof ConfigurationSection) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(key, toYaml(item, values)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(toYaml(item, values));
            }
            return copy;
        }
        if (value instanceof Location location) {
            // skript-yaml fails on a location without a world; it's saved as Skript saves it instead.
            return location.getWorld() != null ? location : skriptClass(value, values);
        }
        if (value instanceof ConfigurationSerializable) {
            return value;
        }
        String type = values == null ? null : values.type(value);
        if (type == null) {
            return skriptClass(value, values);
        }
        return switch (type) {
            case "itemtype", "slot" -> values.convert(value, ItemStack.class);
            case "timespan" -> new Tagged("!skripttimespan", values.text(value));
            case "time" -> new Tagged("!skripttime", values.text(value));
            case "weathertype" -> new Tagged("!skriptweather", values.text(value).toLowerCase(Locale.ROOT));
            case "color" -> value instanceof Enum<?> ? new Tagged("!skriptcolor", values.text(value))
                    : skriptClass(value, values);
            case "date" -> values.toJava(value) instanceof java.time.Instant instant
                    ? new Tagged("!skriptdate", YamlText.isoDate(instant.toEpochMilli())) : skriptClass(value, values);
            default -> skriptClass(value, values);
        };
    }

    /** A value as {@code !skriptclass}, or {@code null} if Skript can't save it either. */
    private static Object skriptClass(Object value, Values values) {
        Values.Saved saved = values == null ? null : values.save(value);
        return saved == null ? null : new SkriptClass(saved.type(), Base64.getEncoder().encodeToString(saved.data()));
    }

    // ---------------------------------------------------------------- files to scripts

    /**
     * A stored value as scripts get it: whole numbers as {@code Long}s and decimals as {@code Double}s, as script
     * numbers are, and a value that couldn't be read as nothing.
     */
    static Object toScript(Object value) {
        return switch (value) {
            case Integer number -> number.longValue();
            case Short number -> number.longValue();
            case Byte number -> number.longValue();
            case Float number -> number.doubleValue();
            case BigInteger number -> number.bitLength() < 64 ? (Object) number.longValue() : number.doubleValue();
            case BigDecimal number -> number.doubleValue();
            case Kept kept -> null;
            case null, default -> value;
        };
    }

    /** Whether two stored values are the same, numbers by their value: {@code 5} read from a file is a script's 5. */
    static boolean same(Object a, Object b) {
        Object x = toScript(a);
        Object y = toScript(b);
        if (x instanceof Number m && y instanceof Number n) {
            return (m instanceof Long && n instanceof Long) ? m.longValue() == n.longValue()
                    : Double.compare(m.doubleValue(), n.doubleValue()) == 0;
        }
        return java.util.Objects.equals(x, y);
    }
}
