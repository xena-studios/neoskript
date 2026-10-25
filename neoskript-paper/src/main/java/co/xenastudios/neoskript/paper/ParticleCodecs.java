/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

/**
 * How particles are saved in variables, as Skript's ParticleSerializer saves them: the particle's name, count,
 * offset, speed ({@code extra}), whether it's forced, and its data, with the serializers Skript registers for the
 * data classes, with each object's fields (their names in lower case) in the order Skript writes them.
 *
 * <p>Data Skript can't save (a vibration, whose destination it has no serializer for) leaves the particle out of
 * the file, as in Skript. Game effects aren't saved at all: Skript's game effect serializer writes the effect's
 * name in a way its writer fails on, so Skript can't save them either.
 */
final class ParticleCodecs {

    private ParticleCodecs() {}

    private static final String COLOR = "particle.color";
    private static final String DUST_OPTIONS = "particle.dustoptions";
    private static final String DUST_TRANSITION = "particle.dusttransition";
    private static final String TRAIL = "particle.trail";
    private static final String SPELL = "particle.spell";

    static void register(Registrar registrar) {
        VariableCodec<ParticleEffect> codec = new VariableCodec<>() {
            @Override
            public void encode(ParticleEffect effect, BinaryWriter out) {
                out.beginObject(8);
                out.doubleField("offsetx", effect.offsetX());
                out.doubleField("offsetz", effect.offsetZ());
                out.doubleField("offsety", effect.offsetY());
                writeData(effect.data(), out);
                out.doubleField("extra", effect.extra());
                out.intField("count", effect.count());
                out.stringField("name", effect.particle().name());
                out.booleanField("force", effect.force());
            }

            @Override
            public ParticleEffect decode(BinaryReader in) {
                in.beginObject();
                double x = in.doubleField("offsetx");
                double z = in.doubleField("offsetz");
                double y = in.doubleField("offsety");
                Object data = readData(in);
                double extra = in.doubleField("extra");
                int count = in.intField("count");
                String name = in.stringField("name");
                boolean force = in.booleanField("force");
                Particle particle;
                try {
                    particle = Particle.valueOf(name);
                } catch (IllegalArgumentException unknown) {
                    return null;
                }
                ParticleEffect effect = ParticleEffect.of(particle);
                effect.count(count);
                effect.offset(x, y, z);
                effect.extra(extra);
                effect.force(force);
                effect.data(data);
                return effect;
            }
        };
        registrar.storage("particle", ParticleEffect.class, codec);
        registrar.storage("convergingparticle", ParticleEffect.Converging.class, narrow(codec));
        registrar.storage("directionalparticle", ParticleEffect.Directional.class, narrow(codec));
        registrar.storage("scalableparticle", ParticleEffect.Scalable.class, narrow(codec));
    }

    /** The particle codec for one kind of particle, as Skript has a type for each. */
    @SuppressWarnings("unchecked")
    private static <T extends ParticleEffect> VariableCodec<T> narrow(VariableCodec<ParticleEffect> codec) {
        return new VariableCodec<>() {
            @Override
            public void encode(T value, BinaryWriter out) {
                codec.encode(value, out);
            }

            @Override
            public T decode(BinaryReader in) {
                return (T) codec.decode(in);
            }
        };
    }

    private static void writeData(Object data, BinaryWriter out) {
        switch (data) {
            case null -> out.nullField("data");
            case Integer number -> out.boxedField("data", number);
            case Float number -> out.boxedField("data", number);
            case Color color -> {
                out.objectField("data", COLOR);
                writeColor(color, out);
            }
            case Particle.DustTransition transition -> {
                out.objectField("data", DUST_TRANSITION);
                out.beginObject(3);
                out.floatField("size", transition.getSize());
                out.objectField("tocolor", COLOR);
                writeColor(transition.getToColor(), out);
                out.objectField("fromcolor", COLOR);
                writeColor(transition.getColor(), out);
            }
            case Particle.DustOptions dust -> {
                out.objectField("data", DUST_OPTIONS);
                out.beginObject(2);
                out.floatField("size", dust.getSize());
                out.objectField("color", COLOR);
                writeColor(dust.getColor(), out);
            }
            case Particle.Trail trail -> {
                out.objectField("data", TRAIL);
                out.beginObject(3);
                out.intField("duration", trail.getDuration());
                out.objectField("color", COLOR);
                writeColor(trail.getColor(), out);
                out.objectField("target", "location");
                StorageCodecs.LOCATION.encode(trail.getTarget(), out);
            }
            case BlockData block -> {
                out.objectField("data", "blockdata");
                out.beginObject(1);
                out.stringField("blockdata", block.getAsString());
            }
            case ItemStack item -> {
                out.objectField("data", "itemstack");
                ItemCodecs.ITEM_STACK.encode(item, out);
            }
            default -> {
                if (Spell.CLASS != null && Spell.CLASS.isInstance(data)) {
                    out.objectField("data", SPELL);
                    out.beginObject(2);
                    out.objectField("color", COLOR);
                    writeColor((Color) Spell.invoke(Spell.COLOR, data), out);
                    out.floatField("power", (Float) Spell.invoke(Spell.POWER, data));
                    return;
                }
                throw new UnsupportedOperationException("Can't save particle data " + data);
            }
        }
    }

    private static Object readData(BinaryReader in) {
        Object field = in.anyField("data");
        if (!(field instanceof BinaryReader.ObjectType(String type))) {
            return field;
        }
        return switch (type) {
            case COLOR -> readColor(in);
            case DUST_TRANSITION -> {
                in.beginObject();
                float size = in.floatField("size");
                Color to = color(in, "tocolor");
                Color from = color(in, "fromcolor");
                yield new Particle.DustTransition(from, to, size);
            }
            case DUST_OPTIONS -> {
                in.beginObject();
                float size = in.floatField("size");
                yield new Particle.DustOptions(color(in, "color"), size);
            }
            case TRAIL -> {
                in.beginObject();
                int duration = in.intField("duration");
                Color color = color(in, "color");
                if (!in.objectField("target").equals("location")) {
                    throw new IllegalStateException("Expected a location");
                }
                Location target = StorageCodecs.LOCATION.decode(in);
                yield target == null ? null : new Particle.Trail(target, color, duration);
            }
            case "blockdata" -> {
                in.beginObject();
                yield Bukkit.createBlockData(in.stringField("blockdata"));
            }
            case "itemstack" -> ItemCodecs.ITEM_STACK.decode(in);
            case SPELL -> {
                in.beginObject();
                Color color = color(in, "color");
                float power = in.floatField("power");
                yield Spell.CLASS == null ? null : Spell.create(color, power);
            }
            default -> throw new IllegalStateException("Unknown particle data " + type);
        };
    }

    /** Bukkit's color, which Skript saves as Bukkit's YAML of it, as it does items. */
    private static void writeColor(Color color, BinaryWriter out) {
        out.beginObject(1);
        out.stringField("value", ItemCodecs.yaml(color));
    }

    private static Color readColor(BinaryReader in) {
        in.beginObject();
        if (!(ItemCodecs.load(in.stringField("value")) instanceof Color color)) {
            throw new IllegalStateException("Expected a color");
        }
        return color;
    }

    private static Color color(BinaryReader in, String field) {
        if (!in.objectField(field).equals(COLOR)) {
            throw new IllegalStateException("Expected a color");
        }
        return readColor(in);
    }

    /** Paper's spell particle data, which servers from 1.21.9 have. */
    private static final class Spell {

        static final Class<?> CLASS;
        static final Method COLOR;
        static final Method POWER;
        static final Constructor<?> CONSTRUCTOR;

        static {
            Class<?> type = null;
            Method color = null;
            Method power = null;
            Constructor<?> constructor = null;
            try {
                type = Class.forName("org.bukkit.Particle$Spell");
                color = type.getMethod("getColor");
                power = type.getMethod("getPower");
                constructor = type.getConstructor(Color.class, float.class);
            } catch (ReflectiveOperationException older) {
                type = null;
            }
            CLASS = type;
            COLOR = color;
            POWER = power;
            CONSTRUCTOR = constructor;
        }

        static Object invoke(Method method, Object target) {
            try {
                return method.invoke(target);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        static Object create(Color color, float power) {
            try {
                return CONSTRUCTOR.newInstance(color, power);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
