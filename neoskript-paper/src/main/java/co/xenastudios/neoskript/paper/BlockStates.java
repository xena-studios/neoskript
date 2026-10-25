/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

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
        registrar.expression("ExprFurnaceTime", "timespan", info -> {
            int which = info.hasTag("total") ? 1 : info.hasTag("burn") ? 2 : 0;
            return new SettableProperty<>(Block.class, Timespan.class, block -> state(block, org.bukkit.block.Furnace.class,
                    furnace -> new Timespan(50L * switch (which) {
                        case 1 -> furnace.getCookTimeTotal();
                        case 2 -> furnace.getBurnTime();
                        default -> furnace.getCookTime();
                    })), (block, time) -> change(block, org.bukkit.block.Furnace.class, furnace -> {
                        int ticks = (int) Math.max(0, Math.min(which == 1 ? Integer.MAX_VALUE : Short.MAX_VALUE, time.ticks()));
                        switch (which) {
                            case 1 -> furnace.setCookTimeTotal(ticks);
                            case 2 -> furnace.setBurnTime((short) ticks);
                            default -> furnace.setCookTime((short) ticks);
                        }
                    }));
        }, "[the] (total:total cook[ing] time|burn:fuel burn[ing] time|cook[ing] time) of %blocks%",
                "%blocks%'[s] (total:total cook[ing] time|burn:fuel burn[ing] time|cook[ing] time)");
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
        registrar.expression("ExprBrewingSlot", "itemtype", info -> {
            int slot = info.hasTag("second") ? 1 : info.hasTag("third") ? 2 : info.hasTag("ingredient") ? 3
                    : info.hasTag("fuel") ? 4 : 0;
            return slotOf(org.bukkit.block.BrewingStand.class, stand -> stand.getInventory(), slot);
        }, "[the] brewing [stand] (first [bottle]|second:second [bottle]|third:third [bottle]|fuel:fuel|ingredient:ingredient) slot[s] of %blocks%",
                "%blocks%'[s] brewing [stand] (first [bottle]|second:second [bottle]|third:third [bottle]|fuel:fuel|ingredient:ingredient) slot[s]");
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

    /** {@code getter} applied to the block's state, if it's that kind, else {@code null}. */
    private static <S extends BlockState> Object state(Block block, Class<S> kind, Function<S, Object> getter) {
        BlockState state = block.getState();
        return kind.isInstance(state) ? getter.apply(kind.cast(state)) : null;
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
