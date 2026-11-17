/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.util.Vector;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Writes values as skript-yaml's {@code SkriptYamlRepresenter} does on Minecraft 1.13 and later. (On Minecraft 26,
 * skript-yaml mistakes the version for one before 1.13, and fails on locations, vectors and texts with {@code &}; this
 * writes them as it does on 1.21.)
 */
final class YamlRepresenter extends Representer {

    YamlRepresenter() {
        super(new DumperOptions());
        this.nullRepresenter = data -> representScalar(Tag.NULL, "");
        this.representers.put(String.class, data -> {
            String text = data.toString();
            // skript-yaml quotes texts with & so their colour codes read back the same.
            return text.contains("&") ? representScalar(Tag.STR, text, DumperOptions.ScalarStyle.DOUBLE_QUOTED)
                    : representScalar(Tag.STR, text);
        });
        this.representers.put(UUID.class, data -> representScalar(Tag.STR, data.toString(),
                DumperOptions.ScalarStyle.PLAIN));
        this.representers.put(YamlValues.SkriptClass.class, data -> {
            YamlValues.SkriptClass value = (YamlValues.SkriptClass) data;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("type", value.type());
            out.put("data", value.data());
            return representMapping(new Tag("!skriptclass"), out, DumperOptions.FlowStyle.BLOCK);
        });
        this.representers.put(YamlValues.Tagged.class, data -> {
            YamlValues.Tagged value = (YamlValues.Tagged) data;
            return representScalar(new Tag(value.tag()), value.text());
        });
        this.representers.put(YamlValues.Kept.class, data -> ((YamlValues.Kept) data).node());
        this.representers.put(Vector.class, data -> {
            Vector vector = (Vector) data;
            Map<String, Double> out = new LinkedHashMap<>();
            out.put("x", vector.getX());
            out.put("y", vector.getY());
            out.put("z", vector.getZ());
            return representMapping(new Tag("!vector"), out, DumperOptions.FlowStyle.BLOCK);
        });
        this.representers.put(Location.class, data -> {
            Location location = (Location) data;
            World world = location.getWorld();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("world", world == null ? null : world.getName());
            out.put("x", location.getX());
            out.put("y", location.getY());
            out.put("z", location.getZ());
            out.put("yaw", (double) location.getYaw());
            out.put("pitch", (double) location.getPitch());
            return representMapping(new Tag("!location"), out, DumperOptions.FlowStyle.BLOCK);
        });
        this.multiRepresenters.put(BlockData.class, data -> representScalar(new Tag("!blockdata"),
                ((BlockData) data).getAsString()));
        this.multiRepresenters.put(ConfigurationSection.class, new RepresentMap() {
            @Override
            public Node representData(Object data) {
                return super.representData(((ConfigurationSection) data).getValues(false));
            }
        });
        this.multiRepresenters.put(ConfigurationSerializable.class, new RepresentSerializable());
    }

    /** Bukkit's serializable values, as Bukkit's own YAML writes them: with {@code ==} naming the class. */
    private final class RepresentSerializable extends RepresentMap implements Represent {
        @Override
        public Node representData(Object data) {
            ConfigurationSerializable serializable = (ConfigurationSerializable) data;
            Map<String, Object> values = new LinkedHashMap<>();
            values.put(ConfigurationSerialization.SERIALIZED_TYPE_KEY,
                    ConfigurationSerialization.getAlias(serializable.getClass()));
            values.putAll(serializable.serialize());
            return super.representData(values);
        }
    }
}
