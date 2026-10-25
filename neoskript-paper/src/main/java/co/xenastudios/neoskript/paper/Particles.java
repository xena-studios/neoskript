/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Axis;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Vibration;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Skript's particle module: particles, game effects and entity effects, by Skript's names, the expressions that
 * make and change particles ({@code red dust particle}, {@code flame particle with offset ...}, {@code particle
 * count of ...}) and {@code draw}/{@code play}.
 */
final class Particles {

    private Particles() {}

    /**
     * Skript's game effect: a Bukkit {@link Effect}, with the data some need. Those that need data are made
     * by the expressions for them ({@code red potion break effect}), not by their names.
     */
    static final class GameEffect {

        final Effect effect;
        Object data;

        GameEffect(Effect effect) {
            this.effect = effect;
        }

        /** Sets the data, if it's of the kind the effect takes. The electric spark can do without. */
        boolean setData(Object data) {
            if (effect.getData() != null && effect.getData().isInstance(data)) {
                this.data = data;
                return true;
            } else if (effect.name().equals("ELECTRIC_SPARK") && data == null) {
                this.data = null;
                return true;
            }
            return false;
        }

        /** Plays it at {@code location}, for players within {@code radius} blocks, or the default distance. */
        void draw(Location location, Number radius) {
            World world = location.getWorld();
            if (effect.getData() != null && data == null || world == null) {
                return;
            }
            if (radius == null) {
                world.playEffect(location, effect, data);
            } else {
                world.playEffect(location, effect, data, radius.intValue());
            }
        }

        void drawFor(Location location, Player player) {
            player.playEffect(location, effect, data);
        }

        @Override
        public String toString() {
            return gameEffectName(effect);
        }
    }

    /** How a particle or game effect that takes data gets it from the values of its pattern's placeholders. */
    @FunctionalInterface
    private interface DataSupplier {
        Object get(Object[] args, ElementInfo info);
    }

    private record DataInfo<E>(E effect, String pattern, DataSupplier data) {}

    private static final Map<Particle, String> PARTICLE_NAMES = new java.util.EnumMap<>(Particle.class);
    private static final Map<Effect, String> GAME_EFFECT_NAMES = new java.util.EnumMap<>(Effect.class);
    private static final Map<EntityEffect, String> ENTITY_EFFECT_NAMES = new java.util.EnumMap<>(EntityEffect.class);
    /** Each particle without data by its names, singular and plural, for parsing particles with a count. */
    private static final Map<String, Particle> PARTICLES_BY_NAME = new java.util.HashMap<>();
    private static final Pattern LEADING_NUMBER = Pattern.compile("(\\d+) (.+)");

    static String particleName(Particle particle) {
        return PARTICLE_NAMES.getOrDefault(particle, particle.name());
    }

    static String gameEffectName(Effect effect) {
        return GAME_EFFECT_NAMES.getOrDefault(effect, effect.name());
    }

    static void register(Registrar registrar) {
        registerTypes(registrar);
        registerParticleData(registrar);
        registerGameEffectData(registrar);
        registerProperties(registrar);
        registerEffects(registrar);
    }

    private static void registerTypes(Registrar registrar) {
        // Game effects: those that need data can't be named, nor are they among 'all game effects'.
        Map<String, GameEffect> gameEffects = new LinkedHashMap<>();
        Map<Effect, GameEffect> gameEffectValues = new java.util.EnumMap<>(Effect.class);
        names(Effect.class, GAME_EFFECTS, "game effect", GAME_EFFECT_NAMES, (name, effect) -> {
            if (effect.getData() == null) {
                gameEffects.put(name, gameEffectValues.computeIfAbsent(effect, GameEffect::new));
            }
        });
        registrar.namedType("gameeffect", "game effect", GameEffect.class, gameEffects);
        registrar.text(GameEffect.class, GameEffect::toString);

        Map<String, EntityEffect> entityEffects = new LinkedHashMap<>();
        names(EntityEffect.class, ENTITY_EFFECTS, "entity effect", ENTITY_EFFECT_NAMES, entityEffects::put);
        registrar.namedType("entityeffect", "entity effect", EntityEffect.class, entityEffects);
        registrar.text(EntityEffect.class, effect -> ENTITY_EFFECT_NAMES.getOrDefault(effect, effect.name()));

        // Particles. 'all particle effects' are every particle, also those that need data, which can't be named:
        // those are only under their enum names, in upper case, which no script's words are looked up as.
        Map<Particle, ParticleEffect> particleValues = new java.util.EnumMap<>(Particle.class);
        Map<String, ParticleEffect> particles = new LinkedHashMap<>();
        Map<String, ParticleEffect.Directional> directional = new LinkedHashMap<>();
        Map<String, ParticleEffect.Converging> converging = new LinkedHashMap<>();
        Map<String, ParticleEffect.Scalable> scalable = new LinkedHashMap<>();
        for (Particle particle : Particle.values()) {
            ParticleEffect effect = ParticleEffect.of(particle);
            effect.shared = true;
            particleValues.put(particle, effect);
            particles.put(particle.name(), effect);
            switch (effect) {
                case ParticleEffect.Directional d -> directional.put(particle.name(), d);
                case ParticleEffect.Converging c -> converging.put(particle.name(), c);
                case ParticleEffect.Scalable s -> scalable.put(particle.name(), s);
                default -> {}
            }
        }
        names(Particle.class, PARTICLES, "particle", PARTICLE_NAMES, (name, particle) -> {
            if (particle.getDataType() == Void.class) {
                particles.put(name, particleValues.get(particle));
                PARTICLES_BY_NAME.put(name, particle);
            }
        });
        registrar.namedType("particle", "particle effect", ParticleEffect.class, particles);
        registrar.alias("particle", "particle", "particles");
        registrar.literalParser("particle", text -> parseParticle(text, ParticleEffect.class));
        registrar.namedType("convergingparticle", "converging particle effect", ParticleEffect.Converging.class, converging);
        registrar.alias("convergingparticle", "converging particle", "converging particles");
        registrar.literalParser("convergingparticle", text -> parseParticle(text, ParticleEffect.Converging.class));
        registrar.subtype("convergingparticle", "particle");
        registrar.namedType("directionalparticle", "directional particle effect", ParticleEffect.Directional.class,
                directional);
        registrar.alias("directionalparticle", "directional particle", "directional particles");
        registrar.literalParser("directionalparticle", text -> parseParticle(text, ParticleEffect.Directional.class));
        registrar.subtype("directionalparticle", "particle");
        registrar.namedType("scalableparticle", "scalable particle effect", ParticleEffect.Scalable.class, scalable);
        registrar.alias("scalableparticle", "scalable particle", "scalable particles");
        registrar.literalParser("scalableparticle", text -> parseParticle(text, ParticleEffect.Scalable.class));
        registrar.subtype("scalableparticle", "particle");
        // A particle in a variable is its own: changing it doesn't change the one it was set from.
        registrar.cloner(ParticleEffect.class, ParticleEffect::copy);
        registrar.text(ParticleEffect.class, effect -> particleName(effect.particle()));
        registrar.text(Particle.class, Particles::particleName);
    }

    /**
     * A particle by its name, with a count before it or not: {@code 5 flame particles}, as Skript parses them. The
     * count is at most {@value ParticleEffect#MAX_COUNT}.
     */
    private static Object parseParticle(String text, Class<? extends ParticleEffect> kind) {
        String lower = text.strip().toLowerCase(Locale.ROOT);
        int count = 1;
        Matcher matcher = LEADING_NUMBER.matcher(lower);
        if (matcher.matches()) {
            try {
                count = Math.clamp(Integer.parseInt(matcher.group(1)), 0, ParticleEffect.MAX_COUNT);
            } catch (NumberFormatException e) {
                return null;
            }
            lower = matcher.group(2);
        }
        for (String article : new String[] {"the ", "an ", "a "}) {
            if (lower.startsWith(article)) {
                lower = lower.substring(article.length());
                break;
            }
        }
        Particle particle = PARTICLES_BY_NAME.get(lower);
        if (particle == null) {
            return null;
        }
        ParticleEffect effect = ParticleEffect.of(particle);
        if (!kind.isInstance(effect)) {
            return null;
        }
        effect.count(count);
        effect.shared = true;
        return effect;
    }

    /**
     * Reads Skript's names for {@code type}'s constants from {@code table}, its lines from {@code default.lang}
     * ({@code flame: flame particle¦s @a}): each name, singular and plural, goes to {@code each}, and the first
     * singular is the constant's name in {@code names}. Constants without names are named as Skript names them,
     * {@code <constant> <node>}.
     */
    private static <E extends Enum<E>> void names(Class<E> type, String table, String node, Map<E, String> names,
            BiConsumer<String, E> each) {
        Map<String, String> lines = new java.util.HashMap<>();
        for (String line : table.split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                lines.put(line.substring(0, colon).strip().toUpperCase(Locale.ROOT), line.substring(colon + 1).strip());
            }
        }
        for (E constant : type.getEnumConstants()) {
            String options = lines.get(constant.name());
            if (options == null) {
                String name = constant.name().replace('_', ' ').toLowerCase(Locale.ROOT) + " " + node;
                names.putIfAbsent(constant, name);
                each.accept(name, constant);
                continue;
            }
            for (String option : options.split(",")) {
                String name = option.strip().toLowerCase(Locale.ROOT).replaceAll("\\s*@\\S+$", "");
                String singular = name;
                String plural = "";
                int bar = name.indexOf('¦');
                if (bar >= 0) {
                    int space = name.indexOf(' ', bar);
                    String rest = space < 0 ? "" : name.substring(space);
                    String[] parts = (space < 0 ? name : name.substring(0, space)).split("¦", -1);
                    singular = parts[0] + (parts.length > 2 ? parts[1] : "") + rest;
                    plural = parts[0] + parts[parts.length - 1] + rest;
                }
                names.putIfAbsent(constant, singular);
                each.accept(singular, constant);
                if (!plural.isEmpty()) {
                    each.accept(plural, constant);
                }
            }
        }
    }

    // Particles and game effects with data

    private static void registerParticleData(Registrar registrar) {
        List<DataInfo<Particle>> infos = new ArrayList<>();
        Particle effect = particle("EFFECT");
        Particle instantEffect = particle("INSTANT_EFFECT");
        if (effect != null && effect.getDataType().getSimpleName().equals("Spell")) {
            // 1.21.9: a colour and a power.
            DataSupplier spell = (args, info) -> {
                Color color = color(args[0]);
                Number power = args[1] instanceof Number n ? n : 1.0;
                try {
                    return effect.getDataType().getConstructor(Color.class, float.class)
                            .newInstance(color == null ? Color.WHITE : color, power.floatValue());
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            };
            infos.add(new DataInfo<>(effect, "%color% effect particle[s] (of|with) power %number%", spell));
            if (instantEffect != null) {
                infos.add(new DataInfo<>(instantEffect, "%color% instant effect particle[s] (of|with) power %number%",
                        spell));
            }
        }
        colored(infos, "FLASH", "%color% flash particle[s]");
        colored(infos, "ENTITY_EFFECT", "%color% (potion|entity) effect particle[s]");
        colored(infos, "TINTED_LEAVES", "%color% tinted leaves particle[s]");
        infos.add(new DataInfo<>(Particle.DUST, "%color% dust particle[s] [of size %number%]",
                (args, info) -> new Particle.DustOptions(colorOrWhite(args[0]), size(args[1]))));
        infos.add(new DataInfo<>(Particle.DUST_COLOR_TRANSITION,
                "%color% dust particle[s] [of size %number%] that transitions to %color%",
                (args, info) -> new Particle.DustTransition(colorOrWhite(args[0]), colorOrWhite(args[2]), size(args[1]))));
        infos.add(new DataInfo<>(Particle.BLOCK, "%itemtype/blockdata% block particle[s]", (args, info) -> blockData(args[0])));
        Particle crumble = particle("BLOCK_CRUMBLE");
        if (crumble != null) {
            infos.add(new DataInfo<>(crumble, "%itemtype/blockdata% [block] crumble particle[s]",
                    (args, info) -> blockData(args[0])));
        }
        infos.add(new DataInfo<>(Particle.BLOCK_MARKER, "%itemtype/blockdata% [block] marker particle[s]",
                (args, info) -> blockData(args[0])));
        infos.add(new DataInfo<>(Particle.DUST_PILLAR, "%itemtype/blockdata% dust pillar particle[s]",
                (args, info) -> blockData(args[0])));
        infos.add(new DataInfo<>(Particle.FALLING_DUST, "falling %itemtype/blockdata% dust particle[s]",
                (args, info) -> blockData(args[0])));
        infos.add(new DataInfo<>(Particle.ITEM, "%itemtype% item particle[s]", (args, info) -> args[0] instanceof ItemType item
                ? item.stack().clone() : new ItemStack(Material.AIR)));
        infos.add(new DataInfo<>(Particle.SCULK_CHARGE, "sculk charge particle[s] [with [a] roll angle [of] %-number%]",
                (args, info) -> args[0] instanceof Number angle ? (float) Math.toRadians(angle.floatValue()) : 0.0f));
        infos.add(new DataInfo<>(Particle.SHRIEK, "shriek particle[s] [delayed by %-timespan%]",
                (args, info) -> args[0] instanceof Timespan delay ? (int) (delay.millis() / 50) : 0));
        infos.add(new DataInfo<>(Particle.VIBRATION,
                "vibration particle[s] moving to[wards] %entity/location% [over [a duration of] %-timespan%]",
                (args, info) -> {
                    Vibration.Destination destination = switch (args[0]) {
                        case Location location -> new Vibration.Destination.BlockDestination(location);
                        case Entity entity -> new Vibration.Destination.EntityDestination(entity);
                        case null, default -> null;
                    };
                    if (destination == null) {
                        return null;
                    }
                    return new Vibration(destination, args[1] instanceof Timespan time ? (int) (time.millis() / 50) : 20);
                }));
        infos.add(new DataInfo<>(Particle.TRAIL,
                "%color% trail particle[s] moving to[wards] %location% [over [a duration of] %-timespan%]",
                (args, info) -> args[1] instanceof Location target ? new Particle.Trail(target, colorOrWhite(args[0]),
                        args[2] instanceof Timespan time ? (int) (time.millis() / 50) : 20) : null));
        Particle breath = particle("DRAGON_BREATH");
        if (breath != null && breath.getDataType() == Float.class) {
            // 1.21.9: a power, 0.5 by default.
            infos.add(new DataInfo<>(breath, "dragon breath particle[s] [of power %-number%]",
                    (args, info) -> args[0] instanceof Number power ? power.floatValue() : 0.5f));
        }

        String[] patterns = new String[infos.size()];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = "[%-*number%|a[n]] " + infos.get(i).pattern();
        }
        registrar.expression("ExprParticleWithData", "particle", info -> {
            DataInfo<Particle> data = infos.get(info.pattern());
            return (context, args) -> {
                Object value = data.data().get(java.util.Arrays.copyOfRange(args, 1, args.length), info);
                if (value == null) {
                    RuntimeErrors.report("Could not obtain required data for " + particleName(data.effect()));
                    return null;
                }
                ParticleEffect particle = ParticleEffect.of(data.effect());
                particle.data(value);
                if (args[0] instanceof Number count) {
                    particle.count(Math.clamp(count.intValue(), 0, ParticleEffect.MAX_COUNT));
                }
                return particle;
            };
        }, patterns);
    }

    /** A particle whose data is a colour, white by default, if it's on this version and takes one. */
    private static void colored(List<DataInfo<Particle>> infos, String name, String pattern) {
        Particle particle = particle(name);
        if (particle != null && particle.getDataType() == Color.class) {
            infos.add(new DataInfo<>(particle, pattern, (args, info) -> colorOrWhite(args[0])));
        }
    }

    private static void registerGameEffectData(Registrar registrar) {
        List<DataInfo<Effect>> infos = new ArrayList<>();
        BiConsumer<String[], Object[]> add = (names, rest) -> {
            Effect effect = null;
            for (String name : names) {
                effect = gameEffect(name);
                if (effect != null) {
                    break;
                }
            }
            if (effect != null) {
                infos.add(new DataInfo<>(effect, (String) rest[0], (DataSupplier) rest[1]));
            }
        };
        DataSupplier single = (args, info) -> args[0] instanceof Long number ? (Object) number.intValue() : args[0];
        add.accept(new String[] {"RECORD_PLAY"}, new Object[] {"[record] song (of|using) %itemtype%",
                (DataSupplier) (args, info) -> args[0] instanceof ItemType item
                        && com.destroystokyo.paper.MaterialTags.MUSIC_DISCS.isTagged(item.material()) ? item.material() : null});
        add.accept(new String[] {"SMOKE_SHOOT", "SMOKE"}, new Object[] {
                "[dispenser] black smoke effect [(in|with|using) [the] direction] %direction%",
                (DataSupplier) (args, info) -> args[0] instanceof WorldSyntax.Direction d ? nearestFace(d, false) : null});
        add.accept(new String[] {"WHITE_SMOKE_SHOOT", "SHOOT_WHITE_SMOKE"}, new Object[] {
                "[dispenser] white smoke effect [(in|with|using) [the] direction] %direction%",
                (DataSupplier) (args, info) -> args[0] instanceof WorldSyntax.Direction d ? nearestFace(d, true) : null});
        DataSupplier block = (args, info) -> blockData(args[0]);
        add.accept(new String[] {"STEP_SOUND"}, new Object[] {"%itemtype/blockdata% [foot]step[s] sound [effect]", block});
        add.accept(new String[] {"DESTROY_BLOCK"}, new Object[] {"%itemtype/blockdata% (break|destroy) effect", block});
        add.accept(new String[] {"DESTROY_BLOCK_WITH_SOUND"}, new Object[] {
                "%itemtype/blockdata% (break|destroy) effect with sound", block});
        DataSupplier cartesian = (args, info) -> args[0] instanceof WorldSyntax.Direction d ? nearestFace(d, true) : null;
        add.accept(new String[] {"DESTROY_PROGRESS"}, new Object[] {
                "destroy progress effect on the %direction% [side|face]", cartesian});
        add.accept(new String[] {"DESTROY_PROGRESS_WITH_SOUND"}, new Object[] {
                "destroy progress effect on the %direction% [side|face] with sound", cartesian});
        DataSupplier color = (args, info) -> color(args[0]);
        add.accept(new String[] {"POTION_BREAK"}, new Object[] {"%color% [splash] potion break effect", color});
        add.accept(new String[] {"INSTANT_POTION_BREAK"}, new Object[] {"%color% instant [splash] potion break effect", color});
        add.accept(new String[] {"COMPOSTER_FILL_ATTEMPT"}, new Object[] {
                "compost[er] [fill[ing]] (succe(ss|ed)|1:fail[ure|ed]) sound [effect]",
                (DataSupplier) (args, info) -> info.mark() == 0});
        DataSupplier ten = (args, info) -> args[0] instanceof Number n ? n.intValue() : 10;
        DataSupplier one = (args, info) -> args[0] instanceof Number n ? n.intValue() : 1;
        DataSupplier ominous = (args, info) -> info.hasTag("ominous");
        add.accept(new String[] {"VILLAGER_PLANT_GROW"}, new Object[] {
                "villager plant grow[th] effect [(with|using) %-number% particles]", ten});
        add.accept(new String[] {"BONE_MEAL_USE"}, new Object[] {"[fake] bone meal effect [(with|using) %-number% particles]", ten});
        add.accept(new String[] {"ELECTRIC_SPARK"}, new Object[] {
                "(electric|lightning[ rod]|copper) spark effect [(in|using|along) the (1:x|2:y|3:z) axis]",
                (DataSupplier) (args, info) -> info.mark() == 0 ? null : Axis.values()[info.mark() - 1]});
        add.accept(new String[] {"SCULK_CHARGE", "PARTICLES_SCULK_CHARGE"}, new Object[] {
                "sculk (charge|spread) effect [(with|using) data %integer%]", single});
        add.accept(new String[] {"ENDER_DRAGON_EGG_TELEPORT"}, new Object[] {
                "ender dragon egg teleport[ing] [to %-vector%] effect", packedOffset(16, 8, 16)});
        add.accept(new String[] {"SHULKER_TELEPORT"}, new Object[] {"shulker teleport[ing] [to %-vector%] effect",
                packedOffset(8, 8, 8)});
        add.accept(new String[] {"CONSUME_EFFECT_TELEPORT"}, new Object[] {"consumable teleport[ing] [to %-vector%] effect",
                packedOffset(127, 127, 127)});
        add.accept(new String[] {"ENDERMAN_TELEPORT"}, new Object[] {"enderman teleport[ing] [to %-vector%] effect",
                packedOffset(127, 127, 127)});
        add.accept(new String[] {"BRUSH_BLOCK_COMPLETE", "PARTICLES_AND_SOUND_BRUSH_BLOCK_COMPLETE"}, new Object[] {
                "[finish] brush[ing] %itemtype/blockdata% effect", block});
        add.accept(new String[] {"TRIAL_SPAWNER_DETECT_PLAYER"}, new Object[] {
                "trial spawner detect[ing|s] [%-number%] player[s] effect", one});
        add.accept(new String[] {"TRIAL_SPAWNER_DETECT_PLAYER_OMINOUS"}, new Object[] {
                "ominous trial spawner detect[ing|s] [%-number%] player[s] effect", one});
        add.accept(new String[] {"TRIAL_SPAWNER_SPAWN"}, new Object[] {"[:ominous] trial spawner spawn[ing] [mob] effect", ominous});
        add.accept(new String[] {"TRIAL_SPAWNER_SPAWN_MOB_AT"}, new Object[] {
                "[:ominous] trial spawner spawn[ing] [mob] effect with sound", ominous});
        add.accept(new String[] {"BEE_GROWTH"}, new Object[] {"bee growth effect [(with|using) %-number% particles]", ten});
        add.accept(new String[] {"VAULT_ACTIVATE"}, new Object[] {"[:ominous] [trial] vault activate effect", ominous});
        add.accept(new String[] {"VAULT_DEACTIVATE"}, new Object[] {"[:ominous] [trial] vault deactivate effect", ominous});
        add.accept(new String[] {"TRIAL_SPAWNER_BECOME_OMINOUS"}, new Object[] {
                "trial spawner become[ing] [:not] ominous effect", (DataSupplier) (args, info) -> !info.hasTag("not")});
        add.accept(new String[] {"TRIAL_SPAWNER_SPAWN_ITEM"}, new Object[] {"[:ominous] trial spawner spawn[ing] item effect",
                ominous});
        add.accept(new String[] {"TURTLE_EGG_PLACEMENT"}, new Object[] {
                "place turtle egg effect [(with|using) %-number% particles]", ten});
        add.accept(new String[] {"SMASH_ATTACK"}, new Object[] {"[mace] smash attack effect [(with|using) %-number% particles]",
                ten});

        String[] patterns = infos.stream().map(DataInfo::pattern).toArray(String[]::new);
        registrar.expression("ExprGameEffectWithData", "gameeffect", info -> {
            DataInfo<Effect> data = infos.get(info.pattern());
            return (context, args) -> {
                GameEffect effect = new GameEffect(data.effect());
                Object value = data.data().get(args, info);
                if (value == null && !data.effect().name().equals("ELECTRIC_SPARK") || !effect.setData(value)) {
                    RuntimeErrors.report("Could not obtain required data for " + effect);
                    return null;
                }
                return effect;
            };
        }, patterns);
    }

    /**
     * The packed offset the teleport trail effects take: three unsigned bytes, {@code XXYYZZ}, each the offset
     * shifted by the radius the client expects, and clamped to it.
     */
    private static DataSupplier packedOffset(int xRadius, int yRadius, int zRadius) {
        return (args, info) -> {
            Vector offset = args[0] instanceof Vector vector ? vector : new Vector();
            return pack(offset.getX(), xRadius) << 16 | pack(offset.getY(), yRadius) << 8 | pack(offset.getZ(), zRadius);
        };
    }

    private static int pack(double offset, int radius) {
        return Math.clamp((long) Math.floor(offset), -radius, radius) + radius;
    }

    /** The block face nearest a direction, of all of them or only the six straight ones, as Skript finds it. */
    private static BlockFace nearestFace(WorldSyntax.Direction direction, boolean cartesian) {
        Vector vector = direction.from(null);
        BlockFace[] faces = cartesian ? new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN} : BlockFace.values();
        double max = -1;
        BlockFace nearest = cartesian ? null : BlockFace.NORTH;
        for (BlockFace face : faces) {
            double dot = face.getDirection().dot(vector);
            if (dot > max) {
                max = dot;
                nearest = face;
            }
        }
        return nearest;
    }

    private static Particle particle(String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException missing) {
            return null;
        }
    }

    private static Effect gameEffect(String name) {
        try {
            return Effect.valueOf(name);
        } catch (IllegalArgumentException missing) {
            return null;
        }
    }

    private static Color color(Object value) {
        return value instanceof Colors.Colour colour
                ? Color.fromARGB(colour.alpha(), colour.red(), colour.green(), colour.blue()) : null;
    }

    private static Color colorOrWhite(Object value) {
        Color color = color(value);
        return color == null ? Color.WHITE : color;
    }

    /** A dust particle's size: 1 unless a size above 0 is given. */
    private static float size(Object value) {
        return value instanceof Number size && size.doubleValue() > 0 ? size.floatValue() : 1;
    }

    private static BlockData blockData(Object value) {
        if (value instanceof ItemType item) {
            try {
                return item.material().createBlockData();
            } catch (IllegalArgumentException notBlock) {
                return null;
            }
        }
        return value instanceof BlockData data ? data : null;
    }

    // Properties

    private static void registerProperties(Registrar registrar) {
        registrar.expression("ExprParticleCount", "number", info -> new Property<>(ParticleEffect.class, Number.class,
                effect -> (long) effect.count(), (effect, delta, mode) -> {
                    int by = delta == null ? 0 : ((Number) delta).intValue();
                    switch (mode) {
                        case ADD, REMOVE -> effect.count(Math.clamp(effect.count() + (mode == Changeable.Mode.REMOVE ? -by : by),
                                0, 1000));
                        default -> effect.count(Math.clamp(by, 0, 1000)); // at most 1000, so as not to crash anyone
                    }
                }), Patterns.property("particle count", "particles"));
        registrar.expression("ExprParticleDistribution", "vector", info -> new Property<>(ParticleEffect.class, Vector.class,
                effect -> effect.isUsingNormalDistribution() ? effect.offset() : null, (effect, delta, mode) -> {
                    Vector by = delta == null ? new Vector() : ((Vector) delta).clone();
                    switch (mode) {
                        case ADD -> effect.distribution(by.add(effect.offset()));
                        case REMOVE -> effect.distribution(by.multiply(-1).add(effect.offset()));
                        default -> effect.distribution(by);
                    }
                }), Patterns.property("particle distribution", "particles"));
        registrar.expression("ExprParticleOffset", "vector", info -> new Property<>(ParticleEffect.class, Vector.class,
                ParticleEffect::offset, (effect, delta, mode) -> {
                    Vector by = delta == null ? new Vector() : ((Vector) delta).clone();
                    switch (mode) {
                        case ADD -> effect.offset(by.add(effect.offset()));
                        case REMOVE -> effect.offset(by.multiply(-1).add(effect.offset()));
                        default -> effect.offset(by);
                    }
                }), Patterns.property("particle offset", "particles"));
        registrar.expression("ExprParticleSpeed", "number", info -> new Property<>(ParticleEffect.class, Number.class,
                ParticleEffect::extra, (effect, delta, mode) -> {
                    double by = delta == null ? 0 : ((Number) delta).doubleValue();
                    switch (mode) {
                        case ADD -> effect.extra(effect.extra() + by);
                        case REMOVE -> effect.extra(effect.extra() - by);
                        default -> effect.extra(by);
                    }
                }), Patterns.property("(particle speed [value]|extra value)", "particles"));
        // Skript's scale property: only particles have it here.
        registrar.expression("PropExprScale", "number", info -> new Property<>(ParticleEffect.Scalable.class, Number.class,
                effect -> effect.hasScale() ? effect.scale() : null, (effect, delta, mode) -> {
                    double by = delta == null ? 1 : ((Number) delta).doubleValue();
                    switch (mode) {
                        case ADD, REMOVE -> {
                            if (effect.hasScale()) { // only one that has a scale
                                effect.scale(effect.scale() + (mode == Changeable.Mode.REMOVE ? -by : by));
                            }
                        }
                        case SET -> effect.scale(by);
                        default -> {
                            if (effect.hasScale()) {
                                effect.scale(by);
                            }
                        }
                    }
                }), Patterns.property("scale[s]", "scalableparticles"));
        // Entities' velocity, and directional particles' at a count of 0.
        registrar.expression("ExprVelocity", "vector", info -> new Property<>(Object.class, Vector.class, owner -> switch (owner) {
            case Entity entity -> entity.getVelocity();
            case ParticleEffect.Directional effect when effect.hasVelocity() -> effect.offset();
            default -> null;
        }, (owner, delta, mode) -> {
            Vector by = (Vector) delta;
            if (owner instanceof Entity entity) {
                switch (mode) {
                    case ADD -> entity.setVelocity(entity.getVelocity().add(by));
                    case REMOVE -> entity.setVelocity(entity.getVelocity().subtract(by));
                    case DELETE, RESET -> entity.setVelocity(new Vector());
                    default -> entity.setVelocity(by);
                }
            } else if (owner instanceof ParticleEffect.Directional effect) {
                // Without a velocity, only setting gives it one.
                switch (mode) {
                    case ADD -> {
                        if (effect.hasVelocity()) {
                            effect.velocity(effect.offset().add(by));
                        }
                    }
                    case REMOVE -> {
                        if (effect.hasVelocity()) {
                            effect.velocity(effect.offset().subtract(by));
                        }
                    }
                    case DELETE, RESET -> {
                        if (effect.hasVelocity()) {
                            effect.velocity(new Vector());
                        }
                    }
                    default -> effect.velocity(by);
                }
            }
        }).deletable(), Patterns.property("velocit(y|ies)", "entities/directionalparticles"));

        // Copies of particles with an offset, distribution, velocity or speed.
        registrar.expression("ExprParticleWithOffset", "particle", info -> {
            int mode = info.pattern();
            return (context, args) -> {
                if (!(args[1] instanceof Vector offset)) {
                    return new Object[0];
                }
                List<Object> results = new ArrayList<>();
                for (Object value : Values.array(args[0])) {
                    if (value instanceof ParticleEffect effect) {
                        ParticleEffect copy = effect.copy();
                        switch (mode) {
                            case 0 -> copy.offset(offset);
                            case 1 -> copy.distribution(offset);
                            default -> {
                                if (!(copy instanceof ParticleEffect.Directional directional)) {
                                    continue;
                                }
                                directional.velocity(offset);
                            }
                        }
                        results.add(copy);
                    }
                }
                return results.toArray();
            };
        }, "%particles% with [an] offset [of] %vector%", "%particles% with [a] distribution [of] %vector%",
                "%directionalparticles% with [a] velocity [of] %vector%");
        registrar.expression("ExprParticleWithSpeed", "particle", info -> (context, args) -> {
            if (!(args[1] instanceof Number speed)) {
                return new Object[0];
            }
            List<Object> results = new ArrayList<>();
            for (Object value : Values.array(args[0])) {
                if (value instanceof ParticleEffect effect) {
                    ParticleEffect copy = effect.copy();
                    copy.extra(speed.doubleValue());
                    results.add(copy);
                }
            }
            return results.toArray();
        }, "%particles% with ([a] particle speed [value]|[an] extra value) [of] %number%");
    }

    /** How a property is changed: with the first value given, or {@code null} for reset and delete. */
    @FunctionalInterface
    private interface Change<T> {
        void change(T owner, Object delta, Changeable.Mode mode);
    }

    /**
     * A property of particles that set, add, remove and reset change, as Skript's do. Particles that are
     * literals, or among {@code all particle effects}, are shared, so they aren't changed.
     */
    private static final class Property<T> implements Expression, Changeable {

        private final Class<T> ownerClass;
        private final Class<?> deltaClass;
        private final Function<? super T, ?> getter;
        private final Change<? super T> change;
        private boolean deletable;

        Property(Class<T> ownerClass, Class<?> deltaClass, Function<? super T, ?> getter, Change<? super T> change) {
            this.ownerClass = ownerClass;
            this.deltaClass = deltaClass;
            this.getter = getter;
            this.change = change;
        }

        Property<T> deletable() {
            deletable = true;
            return this;
        }

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object owner : Values.array(arguments[0])) {
                if (ownerClass.isInstance(owner)) {
                    Object value = getter.apply(ownerClass.cast(owner));
                    if (value != null) {
                        values.add(value);
                    }
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {deltaClass};
                case RESET -> new Class<?>[0];
                case DELETE -> deletable ? new Class<?>[0] : null;
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Object by = delta == null || delta.length == 0 ? null : delta[0];
            if (by == null && mode != Mode.RESET && mode != Mode.DELETE) {
                return;
            }
            for (Object owner : Values.array(arguments[0])) {
                if (ownerClass.isInstance(owner) && !(owner instanceof ParticleEffect effect && effect.shared)) {
                    change.change(ownerClass.cast(owner), by, mode);
                }
            }
        }
    }

    // Drawing

    private static void registerEffects(Registrar registrar) {
        registrar.effect("EffPlayEffect", info -> {
            int pattern = info.pattern();
            boolean force = info.hasTag("force");
            return (context, args) -> {
                if (pattern == 3) {
                    drawEntityEffects(Values.array(args[0]), Values.array(args[1]));
                    return;
                }
                if (args[1] == null || args[2] == null) {
                    return;
                }
                Location[] locations = locations(Values.array(args[1]), Values.array(args[2]));
                Number radius = pattern == 2 && args[3] instanceof Number n ? n : null;
                Object[] players = pattern == 1 ? Values.array(args[3]) : null;
                Player as = pattern == 0 ? player(args[3]) : pattern == 1 ? player(args[4]) : null;
                for (Object draw : Values.array(args[0])) {
                    if (draw instanceof GameEffect effect) {
                        if (players == null) {
                            for (Location location : locations) {
                                effect.draw(location, radius);
                            }
                        } else {
                            for (Object player : players) {
                                if (player instanceof Player p) {
                                    for (Location location : locations) {
                                        effect.drawFor(location, p);
                                    }
                                }
                            }
                        }
                    } else if (draw instanceof ParticleEffect particle) {
                        ParticleEffect effect = particle.copy();
                        if (as != null) {
                            effect.source(as);
                        }
                        effect.force(force);
                        if (players != null) {
                            List<Player> receivers = new ArrayList<>();
                            for (Object player : players) {
                                if (player instanceof Player p) {
                                    receivers.add(p);
                                }
                            }
                            effect.receivers(receivers);
                        }
                        for (Location location : locations) {
                            effect.spawn(location);
                        }
                    }
                }
            };
        }, "[:force] (play|show|draw) %gameeffects/particles% [%-directions% %locations%] [as %-player%]",
                "[:force] (play|show|draw) %gameeffects/particles% [%-directions% %locations%] (for|to) %-players% "
                        + "[as %-player%]",
                "(play|show|draw) %gameeffects% [%-directions% %locations%] (in|with) [a] [view] (radius|range) [of] %number%",
                "(play|show|draw) %entityeffects% on %entities%");
    }

    private static Player player(Object value) {
        return Values.single(value) instanceof Player player ? player : null;
    }

    /** The locations, each moved by every direction in turn, as Skript combines them. */
    private static Location[] locations(Object[] directions, Object[] locations) {
        List<Location> result = new ArrayList<>();
        for (Object value : locations) {
            Location location = value instanceof Location l ? l.clone() : value instanceof Entity e ? e.getLocation() : null;
            if (location == null) {
                continue;
            }
            for (Object direction : directions) {
                if (direction instanceof WorldSyntax.Direction d) {
                    location.add(d.from(location));
                }
            }
            result.add(location);
        }
        return result.toArray(Location[]::new);
    }

    private static void drawEntityEffects(Object[] effects, Object[] entities) {
        for (Object value : effects) {
            if (!(value instanceof EntityEffect effect)) {
                continue;
            }
            for (Object entity : entities) {
                if (entity instanceof Entity e && effect.isApplicableTo(e)) {
                    e.playEffect(effect);
                }
            }
        }
    }

    // Skript's names, from its default.lang.

    private static final String PARTICLES = """
            angry_villager: angry villager particle¦s @an
            ash: ash particle¦s @an
            block: block particle¦s @a
            block_crumble: block crumble particle¦s @a
            block_marker: block marker particle¦s @a
            bubble: bubble particle¦s @a
            bubble_column_up: bubble column particle¦s @a
            bubble_pop: bubble pop particle¦s @a
            campfire_cosy_smoke: campfire cosy smoke particle¦s @a
            campfire_signal_smoke: campfire signal smoke particle¦s @a
            cherry_leaves: cherry leaves particle¦s @a
            cloud: cloud particle¦s @a
            composter: composter particle¦s @a
            copper_fire_flame: copper flame particle¦s @a
            crimson_spore: crimson spore particle¦s @a
            crit: crit particle¦s @a
            current_down: downward current particle¦s @a
            damage_indicator: damage indicator particle¦s @a
            dolphin: dolphin particle¦s @a
            dragon_breath: dragon breath particle¦s @a
            dripping_dripstone_lava: dripstone dripping lava particle¦s @a
            dripping_dripstone_water: dripstone dripping water particle¦s @a
            dripping_honey: dripping honey particle¦s @a
            dripping_lava: dripping lava particle¦s @a
            dripping_obsidian_tear: dripping obsidian tear particle¦s @a
            dripping_water: dripping water particle¦s @a
            dust: dust particle¦s @a
            dust_color_transition: dust color transition particle¦s @a
            dust_pillar: dust pillar particle¦s @a
            dust_plume: dust plume particle¦s @a
            effect: potion effect particle¦s @an
            egg_crack: egg crack particle¦s @an
            elder_guardian: elder guardian particle¦s @an
            electric_spark: electric spark particle¦s @an
            enchant: enchanting particle¦s @an
            enchanted_hit: enchanted hit particle¦s @an
            end_rod: end rod particle¦s @an
            entity_effect: entity effect particle¦s @an
            explosion: explosion particle¦s @an
            explosion_emitter: explosion emitter particle¦s @an
            falling_dripstone_lava: falling dripstone lava particle¦s @a
            falling_dripstone_water: falling dripstone water particle¦s @a
            falling_dust: falling dust particle¦s @a
            falling_honey: falling honey particle¦s @a
            falling_lava: falling lava particle¦s @a
            falling_nectar: falling nectar particle¦s @a
            falling_obsidian_tear: falling obsidian tear particle¦s @a
            falling_spore_blossom: falling spore blossom particle¦s @a
            falling_water: falling water particle¦s @a
            firefly: firefly particle¦s @a
            firework: firework particle¦s @a
            fishing: fishing particle¦s @a
            flame: flame particle¦s @a
            flash: flash particle¦s @a
            glow: glow particle¦s @a
            glow_squid_ink: glow squid ink particle¦s @a
            gust: gust particle¦s @a
            gust_emitter_large: large gust emitter particle¦s @a
            gust_emitter_small: small gust emitter particle¦s @a
            happy_villager: happy villager particle¦s @a
            heart: heart particle¦s @a
            infested: infested particle¦s @an
            instant_effect: instant effect particle¦s @an
            item: item particle¦s @an
            item_cobweb: cobweb item particle¦s @an
            item_slime: slime item particle¦s @an
            item_snowball: snowball item particle¦s @an
            landing_honey: landing honey particle¦s @a
            landing_lava: landing lava particle¦s @a
            landing_obsidian_tear: landing obsidian tear particle¦s @a
            large_smoke: large smoke particle¦s @a
            lava: lava particle¦s @a
            mycelium: mycelium particle¦s @a
            nautilus: nautilus particle¦s @a
            note: note particle¦s @a
            ominous_spawning: ominous spawning particle¦s @an
            pale_oak_leaves: pale oak leaves particle¦s @a
            pause_mob_growth: pause mob growth particle¦s @a
            poof: poof particle¦s @a
            portal: portal particle¦s @a
            raid_omen: raid omen particle¦s @a
            rain: rain particle¦s @a
            reset_mob_growth: reset mob growth particle¦s @a
            reverse_portal: reverse portal particle¦s @a
            scrape: scrape particle¦s @a
            sculk_charge: sculk charge particle¦s @a
            sculk_charge_pop: sculk charge pop particle¦s @a
            sculk_soul: sculk soul particle¦s @a
            shriek: shriek particle¦s @a
            small_flame: small flame particle¦s @a
            small_gust: small gust particle¦s @a
            smoke: smoke particle¦s @a
            sneeze: sneeze particle¦s @a
            snowflake: snowflake particle¦s @a
            sonic_boom: sonic boom particle¦s @a
            soul: soul particle¦s @a
            soul_fire_flame: soul fire flame particle¦s @a
            spit: spit particle¦s @a
            splash: splash particle¦s @a
            spore_blossom_air: spore blossom air particle¦s @a
            squid_ink: squid ink particle¦s @a
            sweep_attack: sweep attack particle¦s @a
            tinted_leaves: tinted leaves particle¦s @a
            totem_of_undying: totem of undying particle¦s @a
            trail: trail particle¦s @a
            trial_omen: trial omen particle¦s @a
            trial_spawner_detection: trial spawner detection particle¦s @a
            trial_spawner_detection_ominous: ominous trial spawner detection particle¦s @a
            underwater: underwater particle¦s @a
            vault_connection: vault connection particle¦s @a
            vibration: vibration particle¦s @a
            warped_spore: warped spore particle¦s @a
            wax_off: wax off particle¦s @a
            wax_on: wax on particle¦s @a
            white_ash: white ash particle¦s @a
            white_smoke: white smoke particle¦s @a
            witch: witch particle¦s @a
            noxious_gas: noxious gas particle¦s @a, nausea gas particle¦s @a
            noxious_gas_cloud: noxious gas cloud¦s @a, nausea gas cloud particle¦s @a
            sulfur_cube_goo: sulfur cube goo particle¦s @a, sulfur cube jump particle¦s @a, sulfur cube jumping particle¦s @a
            sulfur_bubbles: sulfur bubbles particle¦s @a, sulfur bubble column particle¦s @a
            geyser: geyser particle¦s @a
            geyser_base: geyser base particle¦s @a
            geyser_plume: geyser plume particle¦s @a
            geyser_poof: geyser poof particle¦s @a
            red_poplar_leaves: red poplar leaves particle¦s @a
            orange_poplar_leaves: orange poplar leaves particle¦s @a
            yellow_poplar_leaves: yellow poplar leaves particle¦s @a
            """;

    private static final String GAME_EFFECTS = """
            click1: dispenser click sound effect
            click2: menu click sound effect, gui click sound effect
            bow_fire: fire bow sound effect, shoot bow sound effect
            extinguish: extinguish fire sound effect, extinguish sound effect
            ghast_shriek: ghast shriek sound effect
            ghast_shoot: ghast shoot sound effect
            blaze_shoot: blaze shoot sound effect
            zombie_chew_wooden_door: zombie chewing on wooden door sound effect, zombie attacking wooden door sound effect
            zombie_chew_iron_door: zombie chewing on iron door sound effect, zombie attacking iron door sound effect
            zombie_destroy_door: zombie destroy door sound effect, zombie destroying door sound effect
            ender_signal: ender eye break effect, eye of ender break effect
            mobspawner_flames: mob spawner flames effect, spawner flames effect
            brewing_stand_brew: brewing sound effect
            chorus_flower_grow: chorus flower growing sound effect
            chorus_flower_death: chorus flower death sound effect
            portal_travel: nether portal travel sound effect
            endereye_launch: ender eye launch sound effect, eye of ender launch sound effect
            firework_shoot: dispenser shoot sound effect
            dragon_breath: dragon breath effect, dragon's breath effect
            anvil_break: break anvil sound effect
            anvil_use: use anvil sound effect
            anvil_land: anvil land sound effect
            enderdragon_shoot: ender dragon shoot sound effect, dragon shoot sound effect
            enderdragon_growl: ender dragon growl sound effect, dragon growl sound effect
            wither_break_block: wither breaking block sound effect
            wither_shoot: wither shoot sound effect
            zombie_infect: zombie infection sound effect
            zombie_converted_villager: zombie converting villager sound effect
            zombie_converted_to_drowned: zombie converting to drowned sound effect
            zombie_converts_to_drowned: zombie converting to drowned sound effect
            husk_converted_to_zombie: husk converting to zombie sound effect
            husk_converts_to_zombie: husk converting to zombie sound effect
            skeleton_converted_to_stray: skeleton converting to stray sound effect
            bat_takeoff: bat take off sound effect
            end_gateway_spawn: end gateway spawn effect
            phantom_bite: phantom bite sound effect
            phantom_bites: phantom bites sound effect
            grindstone_use: use grindstone sound effect
            grindstone_used: used grindstone sound effect
            book_page_turn: turn page sound effect
            book_page_turned: turned page sound effect
            smithing_table_use: use smithing table sound effect
            pointed_dripstone_drip_lava_into_cauldron: lava dripping into cauldron sound effect
            pointed_dripstone_drip_water_into_cauldron: water dripping into cauldron sound effect
            dripping_dripstone: water dripping sound effect, lava dripping sound effect
            lava_interact: lava interaction effect
            lava_converts_block: lava converts block effect
            redstone_torch_burnout: redstone torch burnout effect
            redstone_torch_burns_out: redstone torch burns out effect
            end_portal_frame_fill: fill end portal frame effect
            ender_eye_placed: ender eye placed effect, eye of ender placed effect
            ender_dragon_destroy_block: ender dragon breaking block effect, dragon breaking block effect
            ender_dragon_destroys_block: ender dragon destroys block effect, dragon destroys block effect
            sponge_dry: sponge dry out effect
            copper_wax_on: apply wax effect
            copper_wax_off: remove wax effect
            oxidised_copper_scrape: scrape oxidised copper effect
            wither_spawned: spawn wither sound effect
            ender_dragon_death: ender dragon death sound effect, dragon death sound effect
            end_portal_created_in_overworld: complete end portal sound effect, create end portal sound effect
            composter_composts: composter compost effect
            pointed_dripstone_land: pointed dripstone land effect
            sound_stop_jukebox_song: stop jukebox song sound effect
            crafter_craft: crafter craft sound effect
            crafter_fail: crafter fail sound effect
            particles_egg_crack: egg crack effect
            gust_dust: gust dust effect
            trial_spawner_eject_item: trial spawner eject item effect
            vault_eject_item: vault eject item effect
            spawn_cobweb: spawn cobweb effect
            sound_with_charge_shot: charge shot sound effect
            door_toggle: toggle door sound effect
            iron_door_toggle: toggle iron door sound effect
            trapdoor_toggle: toggle trapdoor sound effect
            iron_trapdoor_toggle: toggle iron trapdoor sound effect
            fence_gate_toggle: toggle fence gate sound effect
            door_close: close door sound effect
            iron_door_close: close iron door sound effect
            trapdoor_close: close trapdoor sound effect
            iron_trapdoor_close: close iron trapdoor sound effect
            fence_gate_close: close fence gate sound effect
            record_play: play record effect
            smoke: dispenser black smoke effect
            step_sound: footstep sound effect
            potion_break: splash potion break effect
            instant_potion_break: instant splash potion break effect
            villager_plant_grow: villager grow plant effect
            composter_fill_attempt: composting fill effect
            bone_meal_use: bone meal use effect
            electric_spark: electric spark effect
            shoot_white_smoke: dispenser white smoke effect
            bee_growth: bee grow plant effect
            turtle_egg_placement: place turtle egg effect
            smash_attack: mace smash attack effect
            particles_sculk_charge: sculk charge effect
            particles_sculk_shriek: sculk shriek effect
            particles_and_sound_brush_block_complete: brush block effect
            trial_spawner_spawn: trial spawner spawn effect
            trial_spawner_spawn_mob_at: trial spawner spawn mob effect
            trial_spawner_detect_player: trial spawner detect player effect
            vault_activate: vault activate effect
            vault_deactivate: vault deactivate effect
            trial_spawner_detect_player_ominous: ominous trial spawner detect player effect
            trial_spawner_become_ominous: trial spawner become ominous effect
            trial_spawner_spawn_item: trial spawner spawn item effect
            dispenser_dispense: dispenser dispense effect
            dispenser_fail: dispenser fail effect, dispenser fail to dispense effect, dispenser failure to dispense effect
            dispenser_projectile_launch: dispenser projectile launch effect
            record_stop: record stop effect, music disc stop effect
            ender_dragon_shoot: ender dragon shoot effect, ender dragon shoots fireball effect
            zombie_converted_to_villager: zombie converted to villager effect, zombie villager cured effect
            wind_charge_shoot: wind charge shoot effect
            sulfur_spike_land: sulfur spike land effect
            smoke_shoot: smoke shoot effect, smoke eruption effect
            destroy_block: destroy block effect, block destruction effect
            ender_dragon_breath: ender dragon breath effect, ender dragon breathing effect
            white_smoke_shoot: white smoke shoot effect, white smoke eruption effect
            ender_dragon_growl: ender dragon growl effect, ender dragon growling effect
            sculk_charge: sculk charge effect
            sculk_shriek: sculk shriek effect
            brush_block_complete: brush block complete effect, finish brushing block effect, brush block effect
            egg_crack: egg crack effect
            spell_potion_splash: spell potion splash sound effect
            instant_potion_splash: instant potion splash sound effect
            destroy_block_with_sound: destroy block with sound effect
            ender_dragon_egg_teleport: ender dragon egg teleport effect
            shulker_teleport: shulker teleport effect
            consume_effect_teleport: consume teleport effect
            enderman_teleport: enderman teleport effect
            destroy_progress: destroy progress effect
            destroy_progress_with_sound: destroy progress with sound effect
            """;

    private static final String ENTITY_EFFECTS = """
            arrow_particles: tipped arrow particles effect
            rabbit_jump: rabbit jump animation
            reset_spawner_minecart_delay: reset spawner minecart delay effect
            death: death animation
            entity_death: death animation
            egg_break: egg break effect
            snowball_break: snowball break effect
            projectile_crack: projectile crack effect
            fang_attack: evoker fang attack animation
            hoglin_attack: hoglin attack animation
            iron_golen_attack: iron golem attack animation
            ravager_attack: ravager attack animation
            ravager_roared: ravager roared effect
            warden_attack: warden attack animation
            zoglin_attack: zoglin attack animation
            entity_attack: entity attack animation
            wolf_smoke: taming failed effect
            wolf_hearts: taming succeeded effect
            taming_failed: taming failed effect
            taming_succeeded: taming succeeded effect
            trusting_failed: ocelot distrust effect
            trusting_succeeded: ocelot trust effect
            wolf_shake: wolf shake animation
            wolf_shake_stop: stop wolf shake animation
            tnt_minecart_ignite: ignite tnt minecart animation
            iron_golem_rose: iron golem rose animation
            iron_golem_sheath: iron golem sheath animation
            villager_heart: villager heart effect
            villager_angry: villager angry effect
            villager_happy: villager happy effect
            witch_magic: witch magic effect
            zombie_transform: zombie transform sound effect
            firework_explode: firework explode animation
            love_hearts: love hearts effect
            squid_rotate: squid rotation reset
            entity_poof: entity poof effect
            shield_block: shield block sound effect
            shield_break: shield break sound effect
            armor_stand_hit: armor stand hit effect
            dolphin_fed: fed dolphin effect
            ravager_stunned: stunned ravager effect
            villager_splash: villager sweat effect
            player_bad_omen_raid: player bad omen raid effect
            fox_chew: fox chew effect
            teleport_ender: ender teleport effect
            break_equipment_main_hand: break main hand effect
            break_equipment_off_hand: break off hand effect
            break_equipment_helmet: break helmet effect
            break_equipment_chestplate: break chestplate effect
            break_equipment_leggings: break leggings effect
            break_equipment_boots: break boots effect
            break_equipment_saddle: break saddle effect
            body_break: break body armor effect, break body armour effect
            break_equipment_body: break body armor effect, break body armour effect
            honey_block_slide_particles: honey block slide effect
            honey_block_fall_particles: honey block fall effect
            swap_hand_items: swap hands effect
            goat_lower_head: goat lowering head animation
            goat_raise_head: goat raising head animation
            spawn_death_smoke: spawn in smoke effect
            warden_tendril_shake: warden tendril shake animation
            warden_sonic_attack: warden sonic attack animation
            sniffer_dig: sniffer dig sound effect
            armadillo_peek: armadillo peek animation
            shake: creaking shake animation
            hit: kinetic weapon hit effect
            hurt_drown: drown damage effect
            drown_particles: drowning damage effect
            sheep_eat: sheep eating grass animation
            sheep_eat_grass: sheep eating grass animation
            totem_resurrect: protected from death effect
            protected_from_death: protected from death effect, totem resurrect effect, totem resurrection effect
            guardian_target: guardian target sound effect (non-functional)
            thorns_hurt: thorns hurt effect (non-functional)
            hurt: damage animation (non-functional)
            hurt_explosion: explosion damage effect (non-functional)
            hurt_berry_bush: hurt berry bush effect (non-functional)
            cat_tame_fail: cat tame fail effect (non-functional)
            cat_tame_success: cat tame success effect (non-functional)
            boat_sink: boat sink animation, boat sinking animation
            boat_launch: boat launch animation, boat launching animation
            """;
}
