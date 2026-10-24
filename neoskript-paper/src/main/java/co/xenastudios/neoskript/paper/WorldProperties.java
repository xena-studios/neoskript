/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import static co.xenastudios.neoskript.paper.PlayerSyntax.each;
import static co.xenastudios.neoskript.paper.PlayerSyntax.map;
import static co.xenastudios.neoskript.paper.PlayerSyntax.owners;
import static co.xenastudios.neoskript.paper.PlayerSyntax.property;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * More about worlds and places: difficulty, game rules, weather, the moon, seeds, view distances, loading and
 * saving worlds, explosions and lightning, light, biome climate, facings, and a few location and vector helpers.
 */
final class WorldProperties {

    private WorldProperties() {}

    /** Skript's weather types: a world's is clear, rain, or a thunderstorm. */
    enum Weather {
        CLEAR("clear", "sun", "sunny"),
        RAIN("rain", "rainy", "raining"),
        THUNDER("thunder", "thundering", "thunderstorm");

        final String[] names;

        Weather(String... names) {
            this.names = names;
        }

        static Weather of(World world) {
            // Sometimes it thunders without a storm.
            return world.isThundering() && world.hasStorm() ? THUNDER : world.hasStorm() ? RAIN : CLEAR;
        }

        static Weather of(Player player) {
            org.bukkit.WeatherType weather = player.getPlayerWeather();
            return weather == null ? null : weather == org.bukkit.WeatherType.DOWNFALL ? RAIN : CLEAR;
        }

        void apply(World world) {
            if (world.isThundering() != (this == THUNDER)) {
                world.setThundering(this == THUNDER);
            }
            if (world.hasStorm() == (this == CLEAR)) {
                world.setStorm(this != CLEAR);
            }
        }

        void apply(Player player) {
            player.setPlayerWeather(this == CLEAR ? org.bukkit.WeatherType.CLEAR : org.bukkit.WeatherType.DOWNFALL);
        }
    }

    /** A game rule's value in a world, Skript's {@code gamerule value}. */
    record GameruleValue(Object value) {
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    static void register(Registrar registrar) {
        registerTypes(registrar);
        registerWorlds(registrar);
        registerPlaces(registrar);
    }

    private static void registerTypes(Registrar registrar) {
        Map<String, Difficulty> difficulties = new LinkedHashMap<>();
        PlayerSyntax.names(difficulties, Difficulty.EASY, "easy");
        PlayerSyntax.names(difficulties, Difficulty.NORMAL, "medium", "normal");
        PlayerSyntax.names(difficulties, Difficulty.HARD, "hard");
        PlayerSyntax.names(difficulties, Difficulty.PEACEFUL, "peaceful");
        registrar.namedType("difficulty", "difficulty", Difficulty.class, difficulties);
        registrar.text(Difficulty.class, difficulty -> PlayerSyntax.firstName(difficulties, difficulty));

        Map<String, Weather> weathers = new LinkedHashMap<>();
        for (Weather weather : Weather.values()) {
            PlayerSyntax.names(weathers, weather, weather.names);
        }
        registrar.namedType("weathertype", "weather type", Weather.class, weathers);
        registrar.text(Weather.class, weather -> weather.names[0]);

        Map<String, io.papermc.paper.world.MoonPhase> phases = new LinkedHashMap<>();
        for (io.papermc.paper.world.MoonPhase phase : io.papermc.paper.world.MoonPhase.values()) {
            phases.put(phase.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '), phase);
        }
        registrar.namedType("moonphase", "moon phase", io.papermc.paper.world.MoonPhase.class, phases);
        registrar.text(io.papermc.paper.world.MoonPhase.class, phase -> phase.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));

        Map<String, SoundCategory> categories = new LinkedHashMap<>();
        PlayerSyntax.names(categories, SoundCategory.AMBIENT, "ambient category", "environment category");
        PlayerSyntax.names(categories, SoundCategory.BLOCKS, "block category", "blocks category");
        PlayerSyntax.names(categories, SoundCategory.HOSTILE, "hostile category", "hostile creature category",
                "hostile creatures category", "hostile mob category", "hostile mobs category");
        PlayerSyntax.names(categories, SoundCategory.MASTER, "master category", "master volume category");
        PlayerSyntax.names(categories, SoundCategory.MUSIC, "music category");
        PlayerSyntax.names(categories, SoundCategory.NEUTRAL, "neutral category", "friendly creature category",
                "friendly creatures category", "friendly mob category", "friendly mobs category");
        PlayerSyntax.names(categories, SoundCategory.PLAYERS, "player category", "players category");
        PlayerSyntax.names(categories, SoundCategory.RECORDS, "record category", "records category", "jukebox category",
                "jukeboxes category", "noteblock category", "noteblocks category", "note block category", "note blocks category");
        PlayerSyntax.names(categories, SoundCategory.VOICE, "voice category", "speech category");
        PlayerSyntax.names(categories, SoundCategory.WEATHER, "weather category");
        registrar.namedType("soundcategory", "sound category", SoundCategory.class, categories);
        registrar.text(SoundCategory.class, category -> PlayerSyntax.firstName(categories, category));

        // Game rules are looked up when scripts parse, not when the library is registered.
        registrar.type("gamerule", "gamerule", GameRule.class);
        registrar.literalParser("gamerule", WorldProperties::gameRule);
        registrar.parser("gamerule", WorldProperties::gameRule);
        registrar.text(GameRule.class, GameRule::getName);
        registrar.type("gamerulevalue", "gamerule value", GameruleValue.class);
        registrar.text(GameruleValue.class, GameruleValue::toString);
        registrar.comparison(GameruleValue.class, GameruleValue.class, (a, b) -> a.value().equals(b.value()));
        registrar.comparison(GameruleValue.class, Number.class, (rule, number) -> rule.value() instanceof Number value
                && value.doubleValue() == number.doubleValue());
        registrar.comparison(GameruleValue.class, Boolean.class, (rule, bool) -> bool.equals(rule.value()));

        registrar.type("metadataholder", "metadata holder", org.bukkit.metadata.Metadatable.class);
        registrar.subtype("entity", "metadataholder");
        registrar.subtype("block", "metadataholder");
        registrar.subtype("world", "metadataholder");
    }

    /** The game rule named {@code text}, ignoring case. */
    private static GameRule<?> gameRule(String text) {
        String name = text.strip();
        try {
            GameRule<?> exact = GameRule.getByName(name);
            if (exact != null) {
                return exact;
            }
        } catch (Throwable e) {
            // Not a name this server knows, or no server to look it up on.
        }
        // Newer servers name them in snake case ('keep_inventory'): either way of writing one will do.
        String loose = loose(name);
        for (GameRule<?> rule : gameRules()) {
            if (loose(rule.getName()).equals(loose) || (Object) rule instanceof org.bukkit.Keyed keyed && loose(keyed.getKey().getKey()).equals(loose)) {
                return rule;
            }
        }
        return null;
    }

    private static String loose(String name) {
        int colon = name.indexOf(':');
        return name.substring(colon + 1).replace("_", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** The server's game rules: from its registry on servers that have one, else Bukkit's list. */
    private static List<GameRule<?>> gameRules() {
        List<GameRule<?>> rules = new ArrayList<>();
        try {
            Object registry = org.bukkit.Registry.class.getField("GAME_RULE").get(null);
            for (Object rule : (Iterable<?>) registry) {
                rules.add((GameRule<?>) rule);
            }
            return rules;
        } catch (Throwable e) {
            // An older server: no registry of them.
        }
        try {
            rules.addAll(List.of(GameRule.values()));
        } catch (Throwable e) {
            // No server to look them up on.
        }
        return rules;
    }

    private static void registerWorlds(Registrar registrar) {
        property(registrar, "ExprDifficulty", "difficulty", Patterns.property("difficult(y|ies)", "worlds"), World.class,
                World::getDifficulty, mode -> mode == Changeable.Mode.SET ? new Class<?>[] {Difficulty.class} : null,
                (world, delta, mode) -> {
                    Difficulty difficulty = (Difficulty) delta[0];
                    world.setDifficulty(difficulty);
                    if (difficulty != Difficulty.PEACEFUL) {
                        // Changing the difficulty doesn't let monsters spawn again by itself.
                        world.setSpawnFlags(true, world.getAllowAnimals());
                    }
                });
        registrar.expression("ExprGameRule", "gamerulevalue", info -> new GameRuleOf(), "[the] gamerule %gamerule% of %worlds%");
        property(registrar, "ExprMoonPhase", "moonphase", Patterns.property("(lunar|moon) phase[s]", "worlds"), World.class,
                World::getMoonPhase, null, null);
        registrar.expression("ExprSeed", "integer", info -> (context, args) -> map(args[0], World.class, World::getSeed),
                "[the] seed[s] (from|of) %worlds%", "%worlds%'[s] seed[s]");
        property(registrar, "ExprSeaLevel", "integer", Patterns.property("sea level", "worlds"), World.class,
                world -> (long) world.getSeaLevel(), null, null);
        registrar.expression("ExprViewDistance", "integer", info -> new Distance(false),
                Patterns.property("view distance[s]", "players/worlds"));
        registrar.expression("ExprSimulationDistance", "integer", info -> new Distance(true),
                Patterns.property("simulation distance[s]", "worlds/players"));
        registrar.expression("ExprWeather", "weathertype", info -> new WeatherOf(),
                "[the] weather [(in|of) %players/worlds%]", "[the] (custom|client) weather [of %players%]",
                "%players/worlds%'[s] weather", "%players%'[s] (custom|client) weather");

        registrar.effect("EffWorldLoad", info -> {
            boolean load = info.pattern() == 0;
            boolean save = !info.hasTag("without saving");
            return (context, args) -> {
                World.Environment environment = load && Values.single(args[1]) instanceof World.Environment e ? e : null;
                for (Object world : owners(args[0])) {
                    if (load && world instanceof String name) {
                        WorldCreator creator = new WorldCreator(name);
                        if (environment != null) {
                            creator.environment(environment);
                        }
                        creator.createWorld();
                    } else if (!load && world instanceof World loaded) {
                        Bukkit.unloadWorld(loaded, save);
                    }
                }
            };
        }, "load [the] world[s] %strings% [with environment %-environment%]", "unload [[the] world[s]] %worlds% [:without saving]");
        registrar.effect("EffWorldSave", info -> (context, args) -> each(args[0], World.class, World::save),
                "save [[the] world[s]] %worlds%");

        EntitySyntax.conditionPatterns(registrar, "CondIsSlimeChunk", org.bukkit.Chunk.class, org.bukkit.Chunk::isSlimeChunk, null,
                Patterns.isProperty("([a] slime chunk|slime chunks|slimey)", "chunk"));
        registrar.condition("CondHasMetadata", info -> {
            boolean negated = info.pattern() == 1;
            boolean and = info.and(0);
            boolean keysAnd = info.and(1);
            return (context, args) -> {
                Object[] keys = owners(args[1]);
                return negated != Condition.check(owners(args[0]), and, holder -> holder instanceof org.bukkit.metadata.Metadatable m
                        && Condition.check(keys, keysAnd, key -> m.hasMetadata(Values.text(key))));
            };
        }, "%metadataholders% (has|have) metadata [(value|tag)[s]] %strings%",
                "%metadataholders% (doesn't|does not|do not|don't) have metadata [(value|tag)[s]] %strings%");
    }

    private static void registerPlaces(Registrar registrar) {
        registrar.effect("EffExplosion", info -> {
            int pattern = info.pattern();
            boolean blockDamage = pattern != 1;
            boolean fire = info.mark() == 1;
            return (context, args) -> {
                Object force = pattern <= 1 ? Values.single(args[0]) : 0;
                if (!(force instanceof Number power)) {
                    return;
                }
                int at = pattern <= 1 ? 1 : 0;
                for (Location location : locations(args[at], args[at + 1])) {
                    if (location.getWorld() == null) {
                        continue;
                    }
                    if (blockDamage) {
                        location.getWorld().createExplosion(location, power.floatValue(), fire);
                    } else {
                        location.getWorld().createExplosion(location.getX(), location.getY(), location.getZ(), power.floatValue(),
                                false, false);
                    }
                }
            };
        }, "[(create|make)] [an] explosion (of|with) (force|strength|power) %number% [%directions% %locations%] [(1¦with fire)]",
                "[(create|make)] [a] safe explosion (of|with) (force|strength|power) %number% [%directions% %locations%]",
                "[(create|make)] [a] fake explosion [%directions% %locations%]",
                "[(create|make)] [an] explosion[ ]effect [%directions% %locations%]");
        registrar.effect("EffLightning", info -> {
            boolean effect = info.mark() == 1;
            return (context, args) -> {
                for (Location location : locations(args[0], args[1])) {
                    if (location.getWorld() == null) {
                        continue;
                    }
                    if (effect) {
                        location.getWorld().strikeLightningEffect(location);
                    } else {
                        location.getWorld().strikeLightning(location);
                    }
                }
            };
        }, "(create|strike) lightning(1¦[ ]effect|) %directions% %locations%");

        property(registrar, "ExprAltitude", "number", Patterns.property("altitude[s]", "locations"), Location.class,
                Location::getY, null, null);
        property(registrar, "ExprMiddleOfLocation", "location", Patterns.property("(middle|center) [point]", "location"),
                Location.class, location -> new Location(location.getWorld(), location.getBlockX() + 0.5, location.getBlockY(),
                        location.getBlockZ() + 0.5), null, null);
        property(registrar, "ExprHumidity", "number", Patterns.property("humidit(y|ies)", "blocks"), Block.class,
                Block::getHumidity, null, null);
        property(registrar, "ExprTemperature", "number", Patterns.property("temperature[s]", "blocks"), Block.class,
                Block::getTemperature, null, null);
        registrar.expression("ExprFacing", "direction", info -> new Facing(info.mark() == 1),
                Patterns.property("(1¦horizontal|) facing", "livingentities/blocks"));
        registrar.expression("ExprLightLevel", "integer", info -> {
            int light = info.mark() == 0 ? 3 : info.mark();
            return (context, args) -> {
                Object[] places = args[1] == null ? new Object[] {eventLocation(context.event())}
                        : args[0] == null ? owners(args[1]) : locations(args[0], args[1]).toArray();
                return map(places, Location.class, location -> {
                    Block block = location.getBlock();
                    return (long) (light == 3 ? block.getLightLevel() : light == 2 ? block.getLightFromBlocks() : block.getLightFromSky());
                });
            };
        }, "[(1¦sky|1¦sun|2¦block)[ ]]light[ ]level [(of|%direction%) %location%]");
        registrar.expression("ExprLocationAt", "location", info -> (context, args) -> {
            if (!(Values.single(args[3]) instanceof World world) || !(Values.single(args[0]) instanceof Number x)
                    || !(Values.single(args[1]) instanceof Number y) || !(Values.single(args[2]) instanceof Number z)) {
                return null;
            }
            return new Location(world, x.doubleValue(), y.doubleValue(), z.doubleValue());
        }, "[the] (location|position) [at] [\\(][x[ ][=[ ]]]%number%, [y[ ][=[ ]]]%number%, [and] [z[ ][=[ ]]]%number%[\\)] "
                + "[[(in|of) [[the] world]] %world%]");
        registrar.expression("ExprWithYawPitch", "location", info -> {
            boolean both = info.pattern() == 1;
            boolean yaw = both || info.hasTag("yaw");
            boolean pitch = both || info.hasTag("pitch");
            return (context, args) -> {
                Object first = Values.single(args[1]);
                Object second = both ? Values.single(args[2]) : first;
                return map(args[0], Location.class, location -> {
                    Location turned = location.clone();
                    if (yaw && first instanceof Number n) {
                        turned.setYaw(n.floatValue());
                    }
                    if (pitch && second instanceof Number n) {
                        turned.setPitch(n.floatValue());
                    }
                    return turned;
                });
            };
        }, "%locations% with [a] (:yaw|:pitch) [of] %number%", "%locations% with [a] yaw [of] %number% and [a] pitch [of] %number%");
        registrar.expression("ExprVectorFromDirection", "vector", info -> {
            if (info.pattern() == 1 && info.argumentType(0) == null) {
                throw new SyntaxError("The direction in '%directions% vector[s]' can not be a variable. Use the direction "
                        + "expression instead: 'northwards vector'.");
            }
            // Relative directions are relative to the origin, facing south: 'forwards' is (0, 0, 1).
            Location origin = new Location(null, 0, 0, 0);
            return (context, args) -> map(args[0], WorldSyntax.Direction.class, direction -> direction.from(origin));
        }, "vector[s] [from] %directions%", "%directions% vector[s]");
        registrar.expression("ExprVectorProjection", "vector", info -> (context, args) -> {
            Vector left = Values.single(args[0]) instanceof Vector v ? v : new Vector();
            Vector right = Values.single(args[1]) instanceof Vector v ? v : new Vector();
            return right.clone().multiply(left.dot(right) / right.lengthSquared());
        }, "[vector] projection [of] %vector% on[to] %vector%");
    }

    /** Skript's {@code at}: the direction that goes nowhere, as in {@code strike lightning at the player}. */
    static void registerLate(Registrar registrar) {
        registrar.expression("LitAt", "direction", info -> (context, args) -> new WorldSyntax.Direction(new Vector(), false), "at");
    }

    /** Each location moved by each direction in turn, as Skript combines {@code %directions% %locations%}. */
    static List<Location> locations(Object directions, Object locations) {
        List<Location> result = new ArrayList<>();
        for (Object place : owners(locations)) {
            if (!(place instanceof Location location)) {
                continue;
            }
            Location moved = location.clone();
            for (Object direction : owners(directions)) {
                if (direction instanceof WorldSyntax.Direction d) {
                    moved.add(d.from(moved));
                }
            }
            result.add(moved);
        }
        return result;
    }

    /** Where the event happened, for syntax whose location is left out: its entity's, block's or player's. */
    static Location eventLocation(Object event) {
        return switch (event) {
            case org.bukkit.event.player.PlayerEvent player -> player.getPlayer().getLocation();
            case org.bukkit.event.entity.EntityEvent entity -> entity.getEntity().getLocation();
            case org.bukkit.event.block.BlockEvent block -> block.getBlock().getLocation();
            case null, default -> null;
        };
    }

    /** A game rule's value in worlds; it can be set to a value of the rule's kind. */
    private static final class GameRuleOf implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            if (!(Values.single(arguments[0]) instanceof GameRule<?> rule)) {
                return null;
            }
            return map(arguments[1], World.class, world -> {
                Object value = world.getGameRuleValue(rule);
                return value == null ? null : new GameruleValue(value instanceof Integer i ? (Object) i.longValue() : value);
            });
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {Boolean.class, Number.class} : null;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(Values.single(arguments[0]) instanceof GameRule rule) || delta == null || delta.length == 0) {
                return;
            }
            Object value = delta[0];
            if (rule.getType() == Integer.class && value instanceof Number number) {
                value = number.intValue();
            } else if (!rule.getType().isInstance(value)) {
                String target = rule.getType() == Boolean.class ? "a boolean" : "an integer";
                String given = value instanceof Boolean ? "a boolean" : value instanceof Long ? "an integer" : "a number";
                co.xenastudios.neoskript.runtime.RuntimeErrors.report("The " + rule.getName() + " gamerule can only be set to "
                        + target + ", not " + given + ".");
                return;
            }
            for (Object world : owners(arguments[1])) {
                if (world instanceof World w) {
                    w.setGameRule(rule, value);
                }
            }
        }
    }

    /** The view or simulation distance of players and worlds, between 2 and 32 chunks. */
    private record Distance(boolean simulation) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return map(arguments[0], Object.class, owner -> switch (owner) {
                case Player player -> (long) (simulation ? player.getSimulationDistance() : player.getViewDistance());
                case World world -> (long) (simulation ? world.getSimulationDistance() : world.getViewDistance());
                default -> null;
            });
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, DELETE, RESET, ADD, REMOVE -> new Class<?>[] {Number.class};
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            int value = mode == Mode.RESET ? Bukkit.getViewDistance()
                    : delta != null && delta.length > 0 ? ((Number) delta[0]).intValue() : 2;
            if (mode == Mode.REMOVE) {
                value = -value;
            }
            for (Object owner : owners(arguments[0])) {
                switch (owner) {
                    case Player player -> {
                        int current = simulation ? player.getSimulationDistance() : player.getViewDistance();
                        int distance = fit(mode, value, current);
                        if (simulation) {
                            player.setSimulationDistance(distance);
                        } else {
                            player.setViewDistance(distance);
                        }
                    }
                    case World world -> {
                        int current = simulation ? world.getSimulationDistance() : world.getViewDistance();
                        int distance = fit(mode, value, current);
                        if (simulation) {
                            world.setSimulationDistance(distance);
                        } else {
                            world.setViewDistance(distance);
                        }
                    }
                    default -> {}
                }
            }
        }

        private static int fit(Mode mode, int value, int current) {
            long distance = mode == Mode.ADD || mode == Mode.REMOVE ? (long) current + value : value;
            return (int) Math.max(2, Math.min(32, distance));
        }
    }

    /** The weather of players and worlds. Clearing a player's makes it the world's; a world's, clear. */
    private static final class WeatherOf implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return map(arguments[0], Object.class, owner -> switch (owner) {
                case Player player -> Weather.of(player);
                case World world -> Weather.of(world);
                default -> null;
            });
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.DELETE || mode == Mode.SET || mode == Mode.RESET ? new Class<?>[] {Weather.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Weather weather = delta != null && delta.length > 0 ? (Weather) delta[0] : null;
            for (Object owner : owners(arguments[0])) {
                if (owner instanceof Player player) {
                    if (weather == null) {
                        player.resetPlayerWeather();
                    } else {
                        weather.apply(player);
                    }
                } else if (owner instanceof World world) {
                    (weather == null ? Weather.CLEAR : weather).apply(world);
                }
            }
        }
    }

    /** Where living entities and directional blocks face; a block's facing can be set. */
    private record Facing(boolean horizontal) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return map(arguments[0], Object.class, owner -> switch (owner) {
                case Block block -> block.getBlockData() instanceof org.bukkit.block.data.Directional directional
                        ? direction(directional.getFacing()) : null;
                case LivingEntity living -> direction(facing(living.getLocation(), horizontal));
                default -> null;
            });
        }

        private static WorldSyntax.Direction direction(BlockFace face) {
            return new WorldSyntax.Direction(new Vector(face.getModX(), face.getModY(), face.getModZ()), false);
        }

        /** Skript's {@code Direction.getFacing}: the axis direction nearest where a location looks. */
        private static BlockFace facing(Location location, boolean horizontal) {
            double yaw = (Math.toRadians(location.getYaw() + 90) + 2 * Math.PI) % (2 * Math.PI);
            double pitch = horizontal ? 0 : Math.toRadians(-location.getPitch());
            if (-Math.PI / 4 < pitch && pitch < Math.PI / 4) {
                if (yaw < Math.PI / 4 || yaw >= Math.PI * 7 / 4) {
                    return BlockFace.EAST;
                }
                if (yaw < Math.PI * 3 / 4) {
                    return BlockFace.SOUTH;
                }
                if (yaw < Math.PI * 5 / 4) {
                    return BlockFace.WEST;
                }
                return BlockFace.NORTH;
            }
            return pitch > 0 ? BlockFace.UP : BlockFace.DOWN;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {WorldSyntax.Direction.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(Values.single(arguments[0]) instanceof Block block) || delta == null || delta.length == 0
                    || !(block.getBlockData() instanceof org.bukkit.block.data.Directional directional)) {
                return;
            }
            Vector towards = ((WorldSyntax.Direction) delta[0]).from(block.getLocation());
            BlockFace nearest = null;
            double best = Double.MAX_VALUE;
            for (BlockFace face : BlockFace.values()) {
                double distance = Math.pow(face.getModX() - towards.getX(), 2) + Math.pow(face.getModY() - towards.getY(), 2)
                        + Math.pow(face.getModZ() - towards.getZ(), 2);
                if (distance < best) {
                    best = distance;
                    nearest = face;
                }
            }
            if (nearest != null && directional.getFaces().contains(nearest)) {
                directional.setFacing(nearest);
                block.setBlockData(directional, false);
            }
        }
    }
}
