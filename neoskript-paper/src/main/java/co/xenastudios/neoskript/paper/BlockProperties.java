/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Changeable.Mode;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SettableCondition;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import co.xenastudios.neoskript.runtime.Timespan;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundGroup;
import org.bukkit.block.Beacon;
import org.bukkit.block.Bell;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.EntityBlockStorage;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

/**
 * Block properties and actions: sign text and glowing text, block sounds and hardness, preferred tools,
 * breaking blocks and bone meal, beacons, bells, respawn anchors, sea pickles, redstone power and beehives.
 */
final class BlockProperties {

    private BlockProperties() {}

    static void register(Registrar registrar) {
        registerConditions(registrar);
        registerEffects(registrar);
        registerExpressions(registrar);
        registerSigns(registrar);
        registerEntityStorage(registrar);
    }

    private static void registerConditions(Registrar registrar) {
        EntitySyntax.conditionPatterns(registrar, "CondIsPassable", Block.class, Block::isPassable, null,
                Patterns.isProperty("passable", "blocks"));
        EntitySyntax.conditionPatterns(registrar, "CondIsOccluding", ItemType.class, item -> item.material().isOccluding(),
                null, Patterns.isProperty("occluding", "itemtypes"));
        EntitySyntax.conditionPatterns(registrar, "CondIsInteractable", ItemType.class,
                item -> item.material().isInteractable(), null, Patterns.isProperty("interactable", "itemtypes"));
        EntitySyntax.conditionPatterns(registrar, "CondIsSedated", Block.class,
                block -> block.getState() instanceof org.bukkit.block.Beehive beehive && beehive.isSedated(), null,
                Patterns.isProperty("sedated", "blocks"));
        EntitySyntax.conditionPatterns(registrar, "CondIsResonating", Block.class,
                block -> block.getState(false) instanceof Bell bell && bell.isResonating(), null,
                Patterns.isProperty("resonating", "blocks"));
        EntitySyntax.conditionPatterns(registrar, "CondIsRinging", Block.class,
                block -> block.getState(false) instanceof Bell bell && bell.isShaking(), null,
                Patterns.isProperty("ringing", "blocks"));
        registrar.condition("CondIsBlockRedstonePowered", info -> {
            boolean indirectly = info.pattern() % 2 == 1;
            boolean negated = info.pattern() > 1;
            boolean and = info.and(0);
            return (context, args) -> {
                Object[] blocks = Property.of(args[0]);
                if (blocks.length == 0) {
                    return negated;
                }
                return Condition.check(blocks, and, value -> (value instanceof Block block
                        && (indirectly ? block.isBlockIndirectlyPowered() : block.isBlockPowered())) != negated);
            };
        }, "%blocks% (is|are) redstone powered", "%blocks% (is|are) indirectly redstone powered",
                "%blocks% (is|are)(n't| not) redstone powered", "%blocks% (is|are)(n't| not) indirectly redstone powered");
        registrar.condition("CondAnchorWorks", info -> {
            boolean negated = info.mark() == 1;
            boolean and = info.and(0);
            return (context, args) -> {
                Object[] worlds = Property.of(args[0]);
                if (worlds.length == 0) {
                    return negated;
                }
                return Condition.check(worlds, and,
                        value -> (value instanceof org.bukkit.World world && world.isRespawnAnchorWorks()) != negated);
            };
        }, "respawn anchors [do[1¦(n't| not)]] work in %worlds%");
        registrar.condition("CondIsPreferredTool", info -> {
            boolean negated = info.pattern() >= 2;
            boolean itemsAnd = info.and(0);
            boolean blocksAnd = info.and(1);
            return (context, args) -> {
                Object[] blocks = Property.of(args[1]);
                if (blocks.length == 0) {
                    return negated;
                }
                Object[] items = Property.of(args[0]);
                return Condition.check(blocks, blocksAnd, block -> Condition.check(items, itemsAnd, item -> {
                    ItemStack stack = item instanceof ItemType type ? type.stack() : null;
                    if (stack == null) {
                        return false;
                    }
                    return block instanceof Block b ? b.isPreferredTool(stack)
                            : block instanceof BlockData data && data.isPreferredTool(stack);
                }) != negated);
            };
        }, "%itemtypes% (is|are) %blocks/blockdatas%'s preferred tool[s]",
                "%itemtypes% (is|are) [the|a] preferred tool[s] (for|of) %blocks/blockdatas%",
                "%itemtypes% (is|are)(n't| not) %blocks/blockdatas%'s preferred tool[s]",
                "%itemtypes% (is|are)(n't| not) [the|a] preferred tool[s] (for|of) %blocks/blockdatas%");
    }

    private static void registerEffects(Registrar registrar) {
        registrar.effect("EffBreakNaturally", info -> (context, args) -> {
            ItemStack tool = args[1] instanceof ItemType item ? item.stack().clone() : null;
            for (Object value : Property.of(args[0])) {
                if (value instanceof Block block) {
                    if (tool != null) {
                        block.breakNaturally(tool);
                    } else {
                        block.breakNaturally();
                    }
                }
            }
        }, "break %blocks% [naturally] [using %-itemtype%]");
        registrar.effect("EffApplyBoneMeal", info -> {
            boolean amount = info.hasTag("amount");
            return (context, args) -> {
                int times = amount ? args[0] instanceof Number number ? number.intValue() : 0 : 1;
                for (Object value : Property.of(args[1] == null ? Property.eventValue(context) : args[1])) {
                    if (value instanceof Block block) {
                        for (int i = 0; i < times; i++) {
                            block.applyBoneMeal(BlockFace.UP);
                        }
                    }
                }
            };
        }, "apply [amount:%-number%] bone[ ]meal[s] [to %blocks%]");
        registrar.effect("EffRing", info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                Entity entity = pattern == 1 && args[0] instanceof Entity given ? given : null;
                BlockFace face = args[pattern + 1] instanceof Direction direction
                        ? Direction.getFacing(direction.getDirection(), true) : null;
                for (Object value : Property.of(args[pattern])) {
                    if (value instanceof Block block && block.getState(false) instanceof Bell bell) {
                        bell.ring(entity, face);
                    }
                }
            };
        }, "ring %blocks% [from [the]] [%-direction%]", "(make|let) %entity% ring %blocks% [from [the]] [%-direction%]");
    }

    private static void registerExpressions(Registrar registrar) {
        registrar.expression("ExprBlockHardness", "number", info -> new Property<>(ItemType.class,
                item -> item.material().isBlock() ? (Object) (double) item.material().getHardness() : null),
                Patterns.property("[block] hardness", "itemtypes"));
        registrar.expression("ExprRedstoneBlockPower", "integer", info -> new Property<>(Block.class,
                block -> (long) block.getBlockPower()), Patterns.property("redstone power", "blocks"));
        registrar.expression("ExprBlockSound", "string", info -> {
            int kind = info.mark();
            return (context, args) -> {
                Set<String> sounds = new LinkedHashSet<>();
                for (Object value : Property.of(args[0])) {
                    SoundGroup group = switch (value) {
                        case Block block -> block.getBlockData().getSoundGroup();
                        case BlockData data -> data.getSoundGroup();
                        case ItemType item when item.material().isBlock() -> item.material().createBlockData().getSoundGroup();
                        case null, default -> null;
                    };
                    if (group == null) {
                        continue;
                    }
                    Sound sound = switch (kind) {
                        case 1 -> group.getBreakSound();
                        case 2 -> group.getFallSound();
                        case 3 -> group.getHitSound();
                        case 4 -> group.getPlaceSound();
                        default -> group.getStepSound();
                    };
                    if (sound != null) {
                        sounds.add(EntityProperties.soundKey(sound));
                    }
                }
                return Property.single(new ArrayList<>(sounds));
            };
        }, Patterns.property("(1¦break|2¦fall|3¦hit|4¦place|5¦step) sound[s]", "blocks/blockdatas/itemtypes"));
        registrar.expression("ExprBreakSpeed", "number", info -> (context, args) -> {
            List<Object> speeds = new ArrayList<>();
            for (Object block : Property.of(args[0] == null ? Property.eventValue(context) : args[0])) {
                for (Object player : Property.of(args[1])) {
                    if (block instanceof Block b && player instanceof Player p) {
                        speeds.add((double) b.getBreakSpeed(p));
                    }
                }
            }
            return Property.single(speeds);
        }, "[the] break speed[s] [of %blocks%] [for %players%]", "%block%'[s] break speed[s] [for %players%]");
        registrar.expression("ExprDropsOfBlock", "itemtype", info -> (context, args) -> {
            List<Object> drops = new ArrayList<>();
            ItemStack tool = args[1] instanceof ItemType item ? item.stack().clone() : null;
            Entity entity = args[2] instanceof Entity given ? given : null;
            for (Object value : Property.of(args[0])) {
                if (!(value instanceof Block block)) {
                    continue;
                }
                java.util.Collection<ItemStack> stacks = tool == null ? block.getDrops()
                        : entity == null ? block.getDrops(tool) : block.getDrops(tool, entity);
                for (ItemStack stack : stacks) {
                    drops.add(new ItemType(stack, true));
                }
            }
            return drops.toArray();
        }, "[(all|the|all [of] the)] drops of %blocks% [(using|with) %-itemtype% [(1¦as %-entity%)]]",
                "%blocks%'s drops [(using|with) %-itemtype% [(1¦as %-entity%)]]");
        registrar.expression("ExprCharges", "integer", info -> {
            boolean max = info.hasTag("max");
            return new Property<>(Block.class, block -> block.getBlockData() instanceof org.bukkit.block.data.type.RespawnAnchor anchor
                    ? (long) (max ? anchor.getMaximumCharges() : anchor.getCharges()) : null, Number.class,
                    Property.modes(Mode.REMOVE, Mode.ADD, Mode.SET, Mode.RESET, Mode.DELETE), (block, delta, mode) -> {
                        if (!(block.getBlockData() instanceof org.bukkit.block.data.type.RespawnAnchor anchor)) {
                            return;
                        }
                        int by = delta == null ? 0 : Property.intOf(delta[0]);
                        int charges = switch (mode) {
                            case REMOVE -> anchor.getCharges() - by;
                            case ADD -> anchor.getCharges() + by;
                            case SET -> by;
                            default -> 0;
                        };
                        anchor.setCharges(Math.min(Math.max(charges, 0), 4));
                        block.setBlockData(anchor);
                    });
        }, Patterns.property("[:max[imum]] charge[s]", "blocks"));
        registrar.expression("ExprSeaPickles", "integer", info -> {
            boolean min = info.hasTag("min");
            boolean max = info.hasTag("max");
            return new Property<>(Block.class, block -> block.getBlockData() instanceof org.bukkit.block.data.type.SeaPickle pickle
                    ? (long) (max ? pickle.getMaximumPickles() : min ? pickle.getMinimumPickles() : pickle.getPickles()) : null,
                    Number.class, min || max ? Property.modes()
                            : Property.modes(Mode.SET, Mode.ADD, Mode.REMOVE, Mode.RESET, Mode.DELETE), BlockProperties::changePickles);
        }, Patterns.property("[(min:min|max:max)[imum]] [sea] pickle(s| (count|amount))", "blocks"));
        registrar.expression("ExprResonatingTime", "timespan", info -> new Property<>(Block.class, block -> {
            int ticks = block.getState(false) instanceof Bell bell ? bell.getResonatingTicks() : 0;
            return ticks == 0 ? null : Property.ticks(ticks);
        }), Patterns.property("resonat(e|ing) time", "block"));
        registrar.expression("ExprRingingTime", "timespan", info -> new Property<>(Block.class, block -> {
            int ticks = block.getState(false) instanceof Bell bell ? bell.getShakingTicks() : 0;
            return ticks == 0 ? null : Property.ticks(ticks);
        }), Patterns.property("ring[ing] time", "block"));
        registerBeacons(registrar);
    }

    /** Sets, adds to or takes from the sea pickles of {@code block}; none left leaves water or air. */
    private static void changePickles(Block block, Object[] delta, Mode mode) {
        if (!(block.getBlockData() instanceof org.bukkit.block.data.type.SeaPickle pickle)) {
            return;
        }
        int change = delta == null ? 0 : Property.intOf(delta[0]);
        if (mode == Mode.REMOVE) {
            change = -change;
        }
        int pickles = change;
        switch (mode) {
            case ADD, REMOVE, SET -> {
                if (mode != Mode.SET) {
                    pickles += pickle.getPickles();
                }
                if (pickles != 0) {
                    pickles = Math.min(pickle.getMaximumPickles(), Math.max(pickle.getMinimumPickles(), pickles));
                }
            }
            case RESET -> pickles = pickle.getMinimumPickles();
            default -> {}
        }
        if (pickles != 0) {
            pickle.setPickles(pickles);
            block.setBlockData(pickle);
        } else {
            block.setType(pickle.isWaterlogged() ? Material.WATER : Material.AIR);
        }
    }

    private static void registerBeacons(Registrar registrar) {
        registrar.expression("ExprBeaconRange", "number", info -> new Property<>(Block.class,
                block -> block.getState() instanceof Beacon beacon ? beacon.getEffectRange() : null, Number.class,
                Property.modes(Mode.SET, Mode.ADD, Mode.REMOVE, Mode.RESET), (block, delta, mode) -> {
                    if (!(block.getState() instanceof Beacon beacon)) {
                        return;
                    }
                    if (mode == Mode.RESET) {
                        beacon.resetEffectRange();
                    } else {
                        double range = ((Number) delta[0]).doubleValue();
                        double value = switch (mode) {
                            case ADD -> beacon.getEffectRange() + range;
                            case REMOVE -> beacon.getEffectRange() - range;
                            default -> range;
                        };
                        beacon.setEffectRange(Math.max(0, Math.min(value, Double.MAX_VALUE)));
                    }
                    beacon.update(true);
                }), Patterns.property("beacon [effect] range", "blocks"));
        registrar.expression("ExprBeaconTier", "integer", info -> new Property<>(Block.class,
                block -> block.getState() instanceof Beacon beacon ? (long) beacon.getTier() : null),
                Patterns.property("beacon tier", "blocks"));
        registrar.expression("ExprBeaconEffects", "potioneffecttype", info -> {
            boolean primary = info.hasTag("primary");
            return new BeaconEffect(primary);
        }, Property.defaultPatterns("(:primary|secondary) [beacon] effect", "blocks"));
    }

    /** A beacon's primary or secondary effect; in a beacon change event, the one chosen. Settable. */
    private record BeaconEffect(boolean primary) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] args) {
            List<Object> effects = new ArrayList<>();
            for (Object value : owners(context, args)) {
                if (!(value instanceof Block block) || !(block.getState() instanceof Beacon beacon)) {
                    continue;
                }
                Object type;
                if (context.event() instanceof io.papermc.paper.event.player.PlayerChangeBeaconEffectEvent change
                        && block.equals(change.getBeacon())) {
                    type = primary ? change.getPrimary() : change.getSecondary();
                } else {
                    org.bukkit.potion.PotionEffect effect = primary ? beacon.getPrimaryEffect() : beacon.getSecondaryEffect();
                    type = effect == null ? null : effect.getType();
                }
                if (type != null) {
                    effects.add(type);
                }
            }
            return Property.single(effects);
        }

        private static Object[] owners(TriggerContext context, Object[] args) {
            if (args[0] == null) {
                Object block = Property.eventValue(context);
                return block instanceof Block ? new Object[] {block} : new Object[0];
            }
            return Property.of(args[0]);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET -> new Class<?>[] {org.bukkit.potion.PotionEffectType.class};
                case RESET, DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] args, Object[] delta, Mode mode) {
            org.bukkit.potion.PotionEffectType type = delta == null || delta.length == 0 ? null
                    : (org.bukkit.potion.PotionEffectType) delta[0];
            for (Object value : owners(context, args)) {
                if (value instanceof Block block && block.getState() instanceof Beacon beacon) {
                    if (primary) {
                        beacon.setPrimaryEffect(type);
                    } else {
                        beacon.setSecondaryEffect(type);
                    }
                    beacon.update(true);
                }
            }
        }
    }

    /** Signs: their lines on either side, and whether those glow. */
    private static void registerSigns(Registrar registrar) {
        registrar.expression("ExprSignText", "string", info -> {
            int pattern = info.pattern();
            int line = pattern == 1 ? (info.hasTag("1") ? 1 : info.hasTag("2") ? 2 : info.hasTag("3") ? 3 : 4) : 0;
            Side side = info.hasTag("front") ? Side.FRONT : info.hasTag("back") ? Side.BACK : null;
            // The text being written applies to the event's sign, left out or written as event-block, but not
            // before the event ('past line 1').
            boolean written = info.time() >= 0 && info.isDefault(pattern == 0 ? 1 : 0);
            return new SignText(pattern, line, side, info.hasTag("block"), written);
        }, "line %integer% [of [the] (:front|:back) [side]] [block:of %block%]",
                "[the] (1:1st|1:first|2:2nd|2:second|3:3rd|3:third|4:4th|4:fourth) line [of [the] (:front|:back) [side]] [block:of %block%]",
                "[all [[of] the]|the] lines [of [the] (:front|:back) [side]] [block:of %block%]");
        registrar.timeStates("ExprSignText", info -> info.isDefault(info.pattern() == 0 ? 1 : 0)
                && info.isEvent(org.bukkit.event.block.SignChangeEvent.class));
        registrar.condition("CondGlowingText", info -> {
            Side side = info.hasTag("back") ? Side.BACK : Side.FRONT;
            return new GlowingText(info.pattern() == 1, info.and(0), side);
        }, "[[the] (front|:back) [side[s]] of] %blocks/itemtypes% (has|have) glowing text",
                "[[the] (front|:back) [side[s]] of] %blocks/itemtypes% (doesn't|does not|do not|don't) have glowing text");
        registrar.effect("EffGlowingText", info -> {
            Side side = info.hasTag("back") ? Side.BACK : Side.FRONT;
            boolean glowing = info.pattern() == 0;
            return (context, args) -> {
                for (Object value : Property.of(args[0])) {
                    setGlowing(value, side, glowing);
                }
            };
        }, "make [[the] (front|:back) [side[s]] of] %blocks/itemtypes% have glowing text",
                "make [[the] (front|:back) [side[s]] of] %blocks/itemtypes% have (normal|non[-| ]glowing) text");
    }

    /** The sign a block is, or a sign item holds. */
    private static Sign sign(Object value) {
        BlockState state = switch (value) {
            case Block block -> block.getState();
            case ItemType item when item.stack().getItemMeta() instanceof BlockStateMeta meta -> meta.getBlockState();
            case null, default -> null;
        };
        return state instanceof Sign sign ? sign : null;
    }

    private static void setGlowing(Object value, Side side, boolean glowing) {
        if (value instanceof Block block) {
            if (block.getState() instanceof Sign sign) {
                sign.getSide(side).setGlowingText(glowing);
                sign.update();
            }
        } else if (value instanceof ItemType item && item.stack().getItemMeta() instanceof BlockStateMeta meta
                && meta.getBlockState() instanceof Sign sign) {
            sign.getSide(side).setGlowingText(glowing);
            item.editMeta(edited -> ((BlockStateMeta) edited).setBlockState(sign));
        }
    }

    /** Whether signs' text glows, on one side; settable. */
    private record GlowingText(boolean negated, boolean and, Side side) implements SettableCondition {
        @Override
        public boolean check(TriggerContext context, Object[] args) {
            Object[] values = Property.of(args[0]);
            if (values.length == 0) {
                return negated;
            }
            return Condition.check(values, and, value -> {
                Sign sign = sign(value);
                return (sign != null && sign.getSide(side).isGlowingText()) != negated;
            });
        }

        @Override
        public void set(TriggerContext context, Object[] args, boolean value) {
            for (Object owner : Property.of(args[0])) {
                setGlowing(owner, side, value != negated);
            }
        }
    }

    /**
     * {@code line 2 of {_sign}}, {@code the lines of the back side of {_sign}}: a sign's text, or in a sign
     * change event, the text being written. One line can be set or deleted at a time.
     */
    private record SignText(int pattern, int fixedLine, Side side, boolean blockGiven, boolean written)
            implements Expression, Changeable {

        /** The line, from 0, -1 for none, or {@code ALL} for every line. */
        private int line(Object[] args) {
            if (pattern == 2) {
                return ALL;
            }
            Object given = pattern == 0 ? args[0] : (Object) (long) fixedLine;
            if (!(given instanceof Number number)) {
                return -1;
            }
            long line = number.longValue();
            if (line < 1 || line > 4) {
                RuntimeErrors.report("Signs only have lines from 1 to 4, but tried to obtain line " + line);
                return -1;
            }
            return (int) line - 1;
        }

        private static final int ALL = -2;

        private Object block(Object[] args) {
            return args[pattern == 0 ? 1 : 0];
        }

        @Override
        public Object get(TriggerContext context, Object[] args) {
            int line = line(args);
            if (line == -1) {
                return new Object[0];
            }
            Object blockArg = block(args);
            if (written && context.event() instanceof org.bukkit.event.block.SignChangeEvent event
                    && (side == null || side == event.getSide())) {
                if (line == ALL) {
                    return event.lines().stream().map(SignText::text).toArray();
                }
                return text(event.line(line));
            }
            Object value = blockGiven ? blockArg : Property.eventValue(context);
            if (!(value instanceof Block block) || !(block.getState() instanceof Sign sign)) {
                return new Object[0];
            }
            org.bukkit.block.sign.SignSide text = sign.getSide(side == null ? Side.FRONT : side);
            if (line == ALL) {
                return text.lines().stream().map(SignText::text).toArray();
            }
            return text(text.line(line));
        }

        private static String text(net.kyori.adventure.text.Component component) {
            return component == null ? "" : Messages.LEGACY_SECTION.serialize(component);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            if (pattern == 2) {
                return null;
            }
            return switch (mode) {
                case SET -> new Class<?>[] {String.class};
                case DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] args, Object[] delta, Mode mode) {
            int line = line(args);
            if (line < 0) {
                return;
            }
            net.kyori.adventure.text.Component text = delta == null || delta.length == 0 ? null
                    : Messages.component(delta[0]);
            Object blockArg = block(args);
            if (written && context.event() instanceof org.bukkit.event.block.SignChangeEvent event
                    && (side == null || side == event.getSide())) {
                event.line(line, text);
                return;
            }
            Object value = blockGiven ? blockArg : Property.eventValue(context);
            if (!(value instanceof Block block) || !(block.getState() instanceof Sign sign)) {
                return;
            }
            sign.getSide(side == null ? Side.FRONT : side).line(line, text == null ? net.kyori.adventure.text.Component.empty() : text);
            sign.update(false, false);
        }
    }

    /** Beehives and other blocks entities live in. */
    private static void registerEntityStorage(Registrar registrar) {
        registrar.expression("ExprEntityStorageEntityCount", "integer", info -> {
            boolean max = info.hasTag("max");
            return new Property<>(Block.class, block -> block.getState() instanceof EntityBlockStorage<?> storage
                    ? (long) (max ? storage.getMaxEntities() : storage.getEntityCount()) : null, Number.class,
                    max ? Property.modes(Mode.SET, Mode.DELETE, Mode.RESET, Mode.ADD, Mode.REMOVE) : Property.modes(),
                    (block, delta, mode) -> {
                        if (!(block.getState() instanceof EntityBlockStorage<?> storage)) {
                            return;
                        }
                        int value = delta == null ? 0 : Property.intOf(delta[0]);
                        switch (mode) {
                            case RESET -> storage.setMaxEntities(storage instanceof org.bukkit.block.Beehive ? 3 : 1);
                            case ADD -> storage.setMaxEntities(storage.getMaxEntities() + value);
                            case REMOVE -> storage.setMaxEntities(storage.getMaxEntities() - value);
                            default -> storage.setMaxEntities(value);
                        }
                        storage.update(true, false);
                    }).defaulting();
        }, Property.defaultPatterns("[max:max[imum]] [stored] entity count", "blocks"));
        registrar.effect("EffClearEntityStorage", info -> (context, args) -> {
            for (Object value : Property.of(args[0])) {
                if (value instanceof Block block && block.getState() instanceof EntityBlockStorage<?> storage) {
                    storage.clearEntities();
                    storage.update(true, false);
                }
            }
        }, "(clear|empty) the (stored entities|entity storage) of %blocks%");
        registrar.effect("EffInsertEntityStorage", info -> (context, args) -> {
            // Only bees go into beehives (and bee nests).
            if (!(args[1] instanceof Block block) || !(block.getState() instanceof org.bukkit.block.Beehive hive)) {
                return;
            }
            for (Object value : Property.of(args[0])) {
                if (!(value instanceof org.bukkit.entity.Bee bee)) {
                    continue;
                }
                if (hive.getEntityCount() >= hive.getMaxEntities()) {
                    break;
                }
                hive.addEntity(bee);
            }
            hive.update(true, false);
        }, "(add|insert) %livingentities% [in[ ]]to [the] (stored entities|entity storage) of %block%");
        registrar.effect("EffReleaseEntityStorage", info -> (context, args) -> {
            Integer ticks = args[1] instanceof Timespan time ? (int) time.ticks() : null;
            for (Object value : Property.of(args[0])) {
                if (value instanceof Block block && block.getState() instanceof EntityBlockStorage<?> storage) {
                    List<? extends Entity> released = storage.releaseEntities();
                    if (ticks != null) {
                        for (Entity entity : released) {
                            if (entity instanceof org.bukkit.entity.Bee bee) {
                                bee.setCannotEnterHiveTicks(ticks);
                            }
                        }
                    }
                }
            }
        }, "(release|evict) [the] (stored entities|entity storage) of %blocks% [for %-timespan%]");
    }
}
