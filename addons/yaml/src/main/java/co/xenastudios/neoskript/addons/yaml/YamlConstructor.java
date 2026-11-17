/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import co.xenastudios.neoskript.api.Values;
import java.time.Instant;
import java.util.Base64;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.util.Vector;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Reads values as skript-yaml's {@code SkriptYamlConstructor} does: its tags, and Bukkit's serializable values (a
 * node with {@code ==}). A tagged value that can't be made (a location in a world that isn't loaded, a type no
 * plugin has, a tag nobody knows) is kept as it was written, reads as nothing, and is written back unchanged.
 * skript-yaml makes those nothing, so its next save loses them, or fails to load the file at all.
 */
final class YamlConstructor extends SafeConstructor {

    private static final Pattern TIMESTAMP = Pattern.compile("^([0-9][0-9][0-9][0-9])-([0-9][0-9]?)-([0-9][0-9]?)"
            + "(?:(?:[Tt]|[ \t]+)([0-9][0-9]?):([0-9][0-9]):([0-9][0-9])(?:\\.([0-9]*))?(?:[ \t]*(?:Z|([-+][0-9][0-9]?)"
            + "(?::([0-9][0-9])?)?))?)?$");
    private static final Pattern YEAR_MONTH_DAY = Pattern.compile("^([0-9][0-9][0-9][0-9])-([0-9][0-9]?)-([0-9][0-9]?)$");

    private final Values values;

    YamlConstructor(LoaderOptions options, Values values) {
        super(options);
        this.values = values;
        this.yamlConstructors.put(new Tag("!vector"), kept(node -> {
            Map<Object, Object> map = constructMapping((MappingNode) node);
            Double x = number(map.get("x"));
            Double y = number(map.get("y"));
            Double z = number(map.get("z"));
            return x == null || y == null || z == null ? null : new Vector(x, y, z);
        }));
        this.yamlConstructors.put(new Tag("!location"), kept(node -> {
            Map<Object, Object> map = constructMapping((MappingNode) node);
            Double x = number(map.get("x"));
            Double y = number(map.get("y"));
            Double z = number(map.get("z"));
            Double yaw = number(map.get("yaw"));
            Double pitch = number(map.get("pitch"));
            if (!(map.get("world") instanceof String name) || x == null || y == null || z == null || yaw == null
                    || pitch == null) {
                return null;
            }
            World world = Bukkit.getWorld(name);
            return world == null ? null : new Location(world, x, y, z, yaw.floatValue(), pitch.floatValue());
        }));
        this.yamlConstructors.put(new Tag("!blockdata"), kept(node -> Bukkit.createBlockData(scalar(node))));
        this.yamlConstructors.put(new Tag("!skriptclass"), kept(node -> {
            Map<Object, Object> map = constructMapping((MappingNode) node);
            if (values == null || !(map.get("type") instanceof String type) || !(map.get("data") instanceof String data)) {
                return null;
            }
            return values.load(type, Base64.getDecoder().decode(data));
        }));
        this.yamlConstructors.put(new Tag("!skriptdate"),
                kept(node -> values == null ? null : values.fromJava(Instant.ofEpochMilli(date(scalar(node))))));
        this.yamlConstructors.put(new Tag("!skripttime"), parsed("time"));
        this.yamlConstructors.put(new Tag("!skripttimespan"), parsed("timespan"));
        this.yamlConstructors.put(new Tag("!skriptcolor"), parsed("color"));
        this.yamlConstructors.put(new Tag("!skriptweather"), parsed("weathertype"));
        this.yamlConstructors.put(Tag.MAP, new ConstructNode());
        // A tag nobody knows is kept, not an error that stops the file loading.
        this.yamlConstructors.put(null, kept(node -> null));
    }

    private static Double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static String scalar(Node node) {
        return ((ScalarNode) node).getValue();
    }

    /** Makes a value with {@code make}, or keeps the node as written if it can't. */
    private static AbstractConstruct kept(Function<Node, Object> make) {
        return new AbstractConstruct() {
            @Override
            public Object construct(Node node) {
                Object value;
                try {
                    value = make.apply(node);
                } catch (RuntimeException | LinkageError unmade) {
                    value = null;
                }
                return value != null ? value : new YamlValues.Kept(node);
            }
        };
    }

    private AbstractConstruct parsed(String type) {
        return kept(node -> values == null ? null : values.parse(scalar(node), type));
    }

    /** A date's milliseconds, read as skript-yaml's {@code ConstructSkriptDate} reads it. */
    static long date(String text) {
        Matcher match = YEAR_MONTH_DAY.matcher(text);
        if (match.matches()) {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.clear();
            calendar.set(Calendar.YEAR, Integer.parseInt(match.group(1)));
            calendar.set(Calendar.MONTH, Integer.parseInt(match.group(2)) - 1);
            calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(match.group(3)));
            return calendar.getTime().getTime();
        }
        match = TIMESTAMP.matcher(text);
        if (!match.matches()) {
            throw new YAMLException("Unexpected timestamp: " + text);
        }
        String seconds = match.group(7) != null ? match.group(6) + "." + match.group(7) : match.group(6);
        double fractions = Double.parseDouble(seconds);
        int wholeSeconds = (int) Math.round(Math.floor(fractions));
        int millis = (int) Math.round((fractions - wholeSeconds) * 1000);
        TimeZone zone = match.group(8) != null
                ? TimeZone.getTimeZone("GMT" + match.group(8) + (match.group(9) != null ? ":" + match.group(9) : "00"))
                : TimeZone.getTimeZone("UTC");
        Calendar calendar = Calendar.getInstance(zone);
        calendar.set(Calendar.YEAR, Integer.parseInt(match.group(1)));
        calendar.set(Calendar.MONTH, Integer.parseInt(match.group(2)) - 1);
        calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(match.group(3)));
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(match.group(4)));
        calendar.set(Calendar.MINUTE, Integer.parseInt(match.group(5)));
        calendar.set(Calendar.SECOND, wholeSeconds);
        calendar.set(Calendar.MILLISECOND, millis);
        return calendar.getTime().getTime();
    }

    /** A node, or one of Bukkit's serializable values if it has {@code ==} (kept as written if it can't be made). */
    private final class ConstructNode extends ConstructYamlMap {
        @Override
        public Object construct(Node node) {
            if (node.isTwoStepsConstruction()) {
                throw new YAMLException("a node that holds itself isn't supported");
            }
            Map<?, ?> raw = (Map<?, ?>) super.construct(node);
            if (!raw.containsKey(ConfigurationSerialization.SERIALIZED_TYPE_KEY)) {
                return raw;
            }
            Map<String, Object> typed = new LinkedHashMap<>(raw.size());
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                typed.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            try {
                Object value = ConfigurationSerialization.deserializeObject(typed);
                return value != null ? value : new YamlValues.Kept(node);
            } catch (RuntimeException | LinkageError unmade) {
                return new YamlValues.Kept(node);
            }
        }

        @Override
        public void construct2ndStep(Node node, Object object) {
            throw new YAMLException("a node that holds itself isn't supported");
        }
    }
}
