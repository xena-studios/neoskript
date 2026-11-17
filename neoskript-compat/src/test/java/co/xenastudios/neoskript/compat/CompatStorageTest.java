/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Serializer;
import ch.njol.yggdrasil.Fields;
import co.xenastudios.neoskript.runtime.variables.storage.SkriptBinaryReader;
import co.xenastudios.neoskript.runtime.variables.storage.SkriptBinaryWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.StreamCorruptedException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Addon types saved in variables as Skript saves them: bytes Skript 2.17.0 with SkBee 3.26.0 wrote
 * ({@code skbee-variables.csv}) read and written again byte for byte, through serializers that do what SkBee's do.
 */
class CompatStorageTest {

    /** A value with named text fields, as SkBee's bounds, armor trims, keys and NBT compounds are saved. */
    record Thing(Map<String, String> fields) {}

    /** SkBee's serializers: they put their fields in this order, and read them back by the same names. */
    static final class ThingSerializer extends Serializer<Thing> {

        private final String[] names;

        ThingSerializer(String... names) {
            this.names = names;
        }

        @Override
        public Fields serialize(Thing thing) throws NotSerializableException {
            if (thing.fields().isEmpty()) {
                throw new NotSerializableException("nothing to save");
            }
            Fields fields = new Fields();
            for (String name : names) {
                fields.putObject(name, thing.fields().get(name));
            }
            return fields;
        }

        @Override
        protected Thing deserialize(Fields fields) throws StreamCorruptedException {
            Map<String, String> read = new LinkedHashMap<>();
            for (String name : names) {
                read.put(name, fields.getObject(name, String.class));
            }
            return new Thing(read);
        }

        @Override
        public boolean mustSyncDeserialization() {
            return false;
        }

        @Override
        protected boolean canBeInstantiated() {
            return false;
        }
    }

    private static Map<String, String[]> fixture() throws IOException {
        Map<String, String[]> rows = new LinkedHashMap<>();
        try (InputStream in = CompatStorageTest.class.getResourceAsStream("/skbee-variables.csv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split(", ");
                rows.put(columns[0], new String[] {columns[1], columns[2]});
            }
        }
        return rows;
    }

    private static CompatStorage<Thing> storage(String type, String... names) {
        ClassInfo<Thing> info = new ClassInfo<>(Thing.class, type);
        ThingSerializer serializer = new ThingSerializer(names);
        info.serializer(serializer);
        return new CompatStorage<>(info, serializer);
    }

    /** Skript's bytes read, then written again: each field as Skript saved it, and the same bytes. */
    private static Thing roundTrip(String variable, CompatStorage<Thing> storage) throws IOException {
        String[] row = fixture().get(variable);
        byte[] saved = HexFormat.of().parseHex(row[1]);
        Thing thing = storage.decode(new SkriptBinaryReader(row[0], saved));
        SkriptBinaryWriter out = new SkriptBinaryWriter(row[0]);
        storage.encode(thing, out);
        assertArrayEquals(saved, out.toByteArray(), variable + " written again");
        return thing;
    }

    @Test
    void boundsFieldNameIsLowercasedAsSkripts() throws IOException {
        // SkBee puts and gets "boundID"; Skript saves it as "boundid".
        Thing bound = roundTrip("fx::bound", storage("bound", "boundID"));
        assertEquals(Map.of("boundID", "fixture"), bound.fields());
    }

    @Test
    void armorTrimsFieldsAreInSkriptsOrder() throws IOException {
        // SkBee puts the pattern first; Skript writes the material first, in its hash map's order.
        Thing trim = roundTrip("fx::trim", storage("armortrim", "pattern", "material"));
        assertEquals(Map.of("pattern", "minecraft:bolt", "material", "minecraft:amethyst"), trim.fields());
        roundTrip("fx::trim2", storage("armortrim", "pattern", "material"));
    }

    @Test
    void keysAndNbtCompounds() throws IOException {
        assertEquals(Map.of("key", "minecraft:stone"), roundTrip("fx::key", storage("namespacedkey", "key")).fields());
        assertEquals(Map.of("nbt", "{a:1}"), roundTrip("fx::nbt", storage("nbtcompound", "nbt")).fields());
    }

    @Test
    void aValueTheSerializerRefusesIsntSaved() {
        // As Skript: the variable isn't saved, rather than keeping what was saved before.
        assertThrows(UnsupportedOperationException.class,
                () -> storage("bound", "boundID").encode(new Thing(Map.of()), new SkriptBinaryWriter("bound")));
    }
}
