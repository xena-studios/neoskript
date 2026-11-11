/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable.Mode;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Timespan;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;

/** Properties of blocks with state: furnaces, brewing stands and beehives. */
final class BlockStates {

    private BlockStates() {}

    static void register(Registrar registrar) {
        // Skript's patterns, two for each time: cook time, total cook time and fuel burn time. Its possessive
        // patterns have no space after the 's, so they're kept that way.
        String[] furnaceTimes = {"cook[ing] time", "total cook[ing] time", "fuel burn[ing] time"};
        String[] furnacePatterns = new String[furnaceTimes.length * 2];
        for (int i = 0; i < furnaceTimes.length; i++) {
            furnacePatterns[2 * i] = "[the] [furnace] " + furnaceTimes[i] + " [of %blocks%]";
            furnacePatterns[2 * i + 1] = "%blocks%'[s]" + furnaceTimes[i];
        }
        registrar.expression("ExprFurnaceTime", "timespan", info -> {
            boolean explicit = !info.isDefault(0);
            if (!explicit && !EventExpressions.isIn(info, org.bukkit.event.inventory.FurnaceBurnEvent.class,
                    org.bukkit.event.inventory.FurnaceStartSmeltEvent.class, org.bukkit.event.inventory.FurnaceExtractEvent.class,
                    org.bukkit.event.inventory.FurnaceSmeltEvent.class)) {
                throw new co.xenastudios.neoskript.api.element.SyntaxError(
                        "There's no furnace in a '" + info.eventName() + "' event.");
            }
            return new FurnaceTime(info.pattern() / 2, explicit);
        }, furnacePatterns);
        registrar.expression("ExprFurnaceSlot", "itemtype", info -> {
            int slot = info.hasTag("fuel") ? 1 : info.hasTag("result") ? 2 : 0;
            return slotOf(org.bukkit.block.Furnace.class, furnace -> furnace.getInventory(), slot);
        }, "[the] ((ore|input)|fuel:fuel|result:(result|output)) slot[s] [of %blocks%]",
                "%blocks%'[s] ((ore|input)|fuel:fuel|result:(result|output)) slot[s]");
        registrar.expression("ExprBrewingFuelLevel", "integer", info -> new SettableProperty<>(Block.class, Number.class,
                block -> state(block, org.bukkit.block.BrewingStand.class, stand -> (long) stand.getFuelLevel()),
                (block, level) -> change(block, org.bukkit.block.BrewingStand.class,
                        stand -> stand.setFuelLevel((int) Math.max(0, Math.min(Integer.MAX_VALUE, level.doubleValue()))))),
                "[the] brewing [stand] fuel (level|amount) [of %blocks%]", "%blocks%'[s] brewing [stand] fuel (level|amount)");
        // Skript's patterns, two for each slot in the brewer inventory's order: three bottles, the ingredient, the fuel.
        String[] brewingSlots = {"[brewing stand['s]] (first|1st) bottle", "[brewing stand['s]] (second|2nd) bottle",
            "[brewing stand['s]] (third|3rd) bottle", "brewing [stand] ingredient", "brewing [stand] fuel"};
        String[] brewingPatterns = new String[brewingSlots.length * 2];
        for (int i = 0; i < brewingSlots.length; i++) {
            brewingPatterns[2 * i] = "[the] " + brewingSlots[i] + " slot[s] [of %blocks%]";
            brewingPatterns[2 * i + 1] = "%blocks%'[s] " + brewingSlots[i] + " slot[s]";
        }
        registrar.expression("ExprBrewingSlot", "itemtype", info -> {
            int slot = info.pattern() / 2;
            SettableProperty<Block, ItemType> property = slotOf(org.bukkit.block.BrewingStand.class,
                    stand -> stand.getInventory(), slot);
            return slot == 4 ? new BrewingFuelSlot(property) : property;
        }, brewingPatterns);
        registrar.expression("ExprBeehiveHoneyLevel", "integer", info -> {
            boolean max = info.hasTag("max");
            return new SettableProperty<>(Block.class, Number.class, block -> block.getBlockData() instanceof org.bukkit.block.data.type.Beehive hive
                    ? (long) (max ? hive.getMaximumHoneyLevel() : hive.getHoneyLevel()) : null, (block, level) -> {
                        if (!max && block.getBlockData() instanceof org.bukkit.block.data.type.Beehive hive) {
                            hive.setHoneyLevel((int) Math.max(0, Math.min(hive.getMaximumHoneyLevel(), level.doubleValue())));
                            block.setBlockData(hive);
                        }
                    });
        }, "[the] [max:max[imum]] honey level of %blocks%", "%blocks%'[s] [max:max[imum]] honey level");
        registrar.expression("ExprBeehiveFlower", "block", info -> new SettableProperty<>(Block.class, Block.class,
                block -> state(block, org.bukkit.block.Beehive.class, hive -> {
                    Location flower = hive.getFlower();
                    return flower == null ? null : flower.getBlock();
                }), (block, flower) -> change(block, org.bukkit.block.Beehive.class, hive -> hive.setFlower(flower.getLocation())))
                .deleting(block -> change(block, org.bukkit.block.Beehive.class, hive -> hive.setFlower(null))),
                "[the] target flower of %blocks%", "%blocks%'[s] target flower");
    }

    /**
     * The fuel slot of brewing stands, as Skript's: in a brewing fuel event, until the trigger waits, the event's
     * stand's is the fuel the event is about. Set, it's the stand's slot that changes.
     */
    private record BrewingFuelSlot(SettableProperty<Block, ItemType> slot) implements
            co.xenastudios.neoskript.api.element.Expression, co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            if (!(context.event() instanceof org.bukkit.event.inventory.BrewingStandFuelEvent event)
                    || co.xenastudios.neoskript.runtime.Waits.delayed(event)) {
                return slot.get(context, arguments);
            }
            java.util.List<Object> slots = new java.util.ArrayList<>();
            for (Object owner : Property.of(arguments[0])) {
                if (owner.equals(event.getBlock()) && owner instanceof Block block
                        && block.getState() instanceof org.bukkit.block.BrewingStand) {
                    slots.add(new ItemType(event.getFuel().clone(), true));
                } else {
                    slots.addAll(java.util.Arrays.asList(Property.of(slot.get(context, new Object[] {owner}))));
                }
            }
            return Property.single(slots);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return slot.acceptChange(mode);
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments,
                Object[] delta, Mode mode) {
            slot.change(context, arguments, delta, mode);
        }
    }

    /** {@code getter} applied to the block's state, if it's that kind, else {@code null}. */
    private static <S extends BlockState> Object state(Block block, Class<S> kind, Function<S, Object> getter) {
        BlockState state = block.getState();
        return kind.isInstance(state) ? getter.apply(kind.cast(state)) : null;
    }

    /**
     * A furnace's cook time (0), total cook time (1) or fuel burn time (2), as Skript's. Left without its
     * furnace, in a furnace's start smelt or burn event, the total cook time or burn time is the event's.
     */
    private record FurnaceTime(int which, boolean explicit) implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {

        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            java.util.List<Object> times = new java.util.ArrayList<>();
            for (Object owner : Property.of(arguments[0])) {
                if (!(owner instanceof Block block) || !(block.getState() instanceof org.bukkit.block.Furnace furnace)) {
                    continue;
                }
                times.add(Property.ticks(switch (which) {
                    case 0 -> furnace.getCookTime();
                    case 1 -> context.event() instanceof org.bukkit.event.inventory.FurnaceStartSmeltEvent start
                            && block.equals(start.getBlock()) ? start.getTotalCookTime() : furnace.getCookTimeTotal();
                    default -> context.event() instanceof org.bukkit.event.inventory.FurnaceBurnEvent burn
                            && block.equals(burn.getBlock()) ? burn.getBurnTime() : furnace.getBurnTime();
                }));
            }
            return Property.single(times);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {Timespan.class};
                case DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments, Object[] delta,
                Mode mode) {
            long given = delta != null && delta.length > 0 && delta[0] instanceof Timespan time ? time.ticks() : 0;
            java.util.function.LongBinaryOperator changed = (time, max) -> Math.max(0, Math.min(max, switch (mode) {
                case ADD -> time + given;
                case REMOVE -> time - given;
                case DELETE -> 0;
                default -> given;
            }));
            if (!explicit && which == 1 && context.event() instanceof org.bukkit.event.inventory.FurnaceStartSmeltEvent start) {
                start.setTotalCookTime((int) changed.applyAsLong(start.getTotalCookTime(), Integer.MAX_VALUE));
                return;
            }
            if (!explicit && which == 2 && context.event() instanceof org.bukkit.event.inventory.FurnaceBurnEvent burn) {
                burn.setBurnTime((int) changed.applyAsLong(burn.getBurnTime(), Integer.MAX_VALUE));
                return;
            }
            for (Object owner : Property.of(arguments[0])) {
                if (owner instanceof Block block) {
                    BlockStates.change(block, org.bukkit.block.Furnace.class, furnace -> {
                        switch (which) {
                            case 0 -> furnace.setCookTime((short) changed.applyAsLong(furnace.getCookTime(), Short.MAX_VALUE));
                            case 1 -> furnace.setCookTimeTotal((int) changed.applyAsLong(furnace.getCookTimeTotal(),
                                    Integer.MAX_VALUE));
                            default -> furnace.setBurnTime((short) changed.applyAsLong(furnace.getBurnTime(), Short.MAX_VALUE));
                        }
                    });
                }
            }
        }
    }

    /** Changes the block's state, if it's that kind, and saves it. */
    private static <S extends BlockState> void change(Block block, Class<S> kind, java.util.function.Consumer<S> change) {
        BlockState state = block.getState();
        if (kind.isInstance(state)) {
            change.accept(kind.cast(state));
            state.update(true, false);
        }
    }

    /** A slot of a block's inventory, read as an item (air if empty) and set or cleared. */
    private static <S extends BlockState> SettableProperty<Block, ItemType> slotOf(Class<S> kind,
            Function<S, org.bukkit.inventory.Inventory> inventory, int slot) {
        BiConsumer<Block, ItemType> setter = (block, item) -> {
            BlockState state = block.getState();
            if (kind.isInstance(state)) {
                inventory.apply(kind.cast(state)).setItem(slot, item == null ? null : item.stack());
            }
        };
        return new SettableProperty<>(Block.class, ItemType.class, block -> {
            Object found = state(block, kind, s -> {
                ItemStack stack = inventory.apply(s).getItem(slot);
                return stack == null ? new ItemType(new ItemStack(org.bukkit.Material.AIR), false) : new ItemType(stack, true);
            });
            return found;
        }, setter).deleting(block -> setter.accept(block, null));
    }
}
