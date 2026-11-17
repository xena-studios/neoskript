/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Serializer;
import ch.njol.yggdrasil.Fields;
import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import co.xenastudios.neoskript.runtime.variables.storage.SkriptBinaryReader;
import java.io.NotSerializableException;
import java.io.StreamCorruptedException;
import java.util.Map;

/**
 * An addon's type saved in variables with its own serializer, in Skript's format: its fields by name, as Skript's
 * {@code variables.csv} has them, so either can read what the other saved. Fields of text, numbers, booleans and
 * nothing, which is what SkBee's serializers write.
 */
final class CompatStorage<T> implements VariableCodec<T> {

    private final ClassInfo<T> info;
    private final Serializer<? super T> serializer;

    CompatStorage(ClassInfo<T> info, Serializer<? super T> serializer) {
        this.info = info;
        this.serializer = serializer;
    }

    @Override
    public void encode(T value, BinaryWriter out) {
        Fields fields;
        try {
            fields = serializer.serialize(value);
        } catch (NotSerializableException e) {
            throw new IllegalArgumentException(info.getCodeName() + " can't be saved: " + e.getMessage(), e);
        }
        out.beginObject(fields.size());
        for (Map.Entry<String, Object> field : fields.values().entrySet()) {
            String name = field.getKey();
            boolean primitive = fields.isPrimitive(name);
            switch (field.getValue()) {
                case null -> out.nullField(name);
                case String text -> out.stringField(name, text);
                case Integer number when primitive -> out.intField(name, number);
                case Long number when primitive -> out.longField(name, number);
                case Double number when primitive -> out.doubleField(name, number);
                case Float number when primitive -> out.floatField(name, number);
                case Byte number when primitive -> out.byteField(name, number);
                case Boolean flag when primitive -> out.booleanField(name, flag);
                case Number number -> out.boxedField(name, number);
                case Boolean flag -> out.boxedField(name, flag);
                case Character letter -> out.boxedField(name, letter);
                default -> throw new IllegalArgumentException(info.getCodeName() + " can't be saved: its field '"
                        + name + "' is a " + field.getValue().getClass().getName()
                        + ", which NeoSkriptCompat doesn't save yet");
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T decode(BinaryReader in) {
        if (!(in instanceof SkriptBinaryReader reader)) {
            throw new IllegalStateException("Not NeoSkript's variable reader");
        }
        Fields fields = new Fields();
        for (int i = reader.beginObject(); i > 0; i--) {
            Map.Entry<String, Object> field = reader.nextField();
            if (field.getValue() instanceof BinaryReader.ObjectType) {
                // A field holding another object, which SkBee doesn't write: the value can't be read here.
                return null;
            }
            fields.putObject(field.getKey(), field.getValue());
        }
        try {
            return (T) serializer.deserialize((Class<T>) info.getC(), fields);
        } catch (StreamCorruptedException | NotSerializableException e) {
            return null;
        }
    }
}
