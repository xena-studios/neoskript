/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Ageable;

/** Block data ({@code campfire[lit=false]}), the type of things, versions, and a few entity conditions. */
final class BlockSyntax {

    private BlockSyntax() {}

    static void register(Registrar registrar) {
        registrar.type("blockdata", "block data", BlockData.class);
        registrar.literalParser("blockdata", BlockSyntax::blockData);
        // How far suspicious sand or gravel has been brushed, from 0 to its maximum.
        registrar.expression("ExprDustedStage", "integer", info -> {
            boolean max = info.hasTag("max");
            return new SettableProperty<>(Object.class, Number.class, owner -> {
                BlockData data = owner instanceof Block block ? block.getBlockData() : owner instanceof BlockData given ? given : null;
                return data instanceof org.bukkit.block.data.Brushable brushable
                        ? (Object) (long) (max ? brushable.getMaximumDusted() : brushable.getDusted()) : null;
            }, (owner, stage) -> {
                if (max) {
                    return;
                }
                BlockData data = owner instanceof Block block ? block.getBlockData() : owner instanceof BlockData given ? given : null;
                if (data instanceof org.bukkit.block.data.Brushable brushable) {
                    brushable.setDusted((int) Math.max(0, Math.min(brushable.getMaximumDusted(), stage.doubleValue())));
                    if (owner instanceof Block block) {
                        block.setBlockData(brushable);
                    }
                }
            }).resettingTo(0L);
        }, "[the] [max:max[imum]] (dusted stage|dusting progress) of %blocks/blockdatas%",
                "%blocks/blockdatas%'[s] [max:max[imum]] (dusted stage|dusting progress)");
        // The item buried in suspicious sand or gravel; air if there's none.
        registrar.expression("ExprBrushableItem", "itemtype", info -> new SettableProperty<>(Block.class, ItemType.class,
                block -> block.getState() instanceof org.bukkit.block.BrushableBlock brushable
                        ? new ItemType(brushable.getItem().getType().isAir() ? new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR)
                                : brushable.getItem(), !brushable.getItem().getType().isAir()) : null,
                (block, item) -> {
                    if (block.getState() instanceof org.bukkit.block.BrushableBlock brushable) {
                        brushable.setItem(item == null || item.material().isAir() ? null : item.stack());
                        brushable.update(true, false);
                    }
                }), "[the] brushable item of %blocks%", "%blocks%'[s] brushable item");
        // Chests, ender chests, barrels and shulker boxes, opened and closed as a player would.
        registrar.effect("EffLidState", info -> {
            boolean open = info.hasTag("open");
            return (context, args) -> {
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof Block block && block.getState() instanceof org.bukkit.block.Lidded lidded) {
                        if (open) {
                            lidded.open();
                        } else {
                            lidded.close();
                        }
                    }
                }
            };
        }, "(open:open|close) [the] lid[s] (of|for) %blocks%", "(open:open|close) %blocks%'[s] lid[s]");
        EntitySyntax.conditionPatterns(registrar, "CondLidState", Block.class,
                block -> block.getState() instanceof org.bukkit.block.Lidded lidded && lidded.isOpen(), null,
                "[the] lid[s] of %blocks% (is|are) (open[ed]|flip:closed)",
                "%blocks%'[s] lid[s] (is|are) (open[ed]|flip:closed)",
                "[the] lid[s] of %blocks% (isn't|is not|aren't|are not) (open[ed]|flip:closed)",
                "%blocks%'[s] lid[s] (isn't|is not|aren't|are not) (open[ed]|flip:closed)");
        // The block an enderman holds; an item that isn't a block makes it hold nothing.
        registrar.expression("ExprCarryingBlockData", "blockdata", info -> new SettableProperty<>(
                org.bukkit.entity.Enderman.class, Object.class, org.bukkit.entity.Enderman::getCarriedBlock, (enderman, block) -> {
                    org.bukkit.block.data.BlockData data = block instanceof org.bukkit.block.data.BlockData given ? given
                            : block instanceof ItemType item && item.material().isBlock() ? item.material().createBlockData()
                            : null;
                    enderman.setCarriedBlock(data);
                }).deleting(enderman -> enderman.setCarriedBlock(null)),
                "[the] carr(ied|ying) block[ ]data of %livingentities%", "%livingentities%'[s] carr(ied|ying) block[ ]data");
        registrar.parser("blockdata", BlockSyntax::blockData);
        registrar.text(BlockData.class, data -> data.getAsString().replaceFirst("^minecraft:", ""));
        registrar.converter(Block.class, BlockData.class, Block::getBlockData);
        registrar.converter(ItemType.class, BlockData.class, item -> item.material().isBlock()
                ? item.material().createBlockData() : null);
        registrar.comparison(BlockData.class, BlockData.class, (a, b) -> a.matches(b) || b.matches(a));
        registrar.comparison(BlockData.class, ItemType.class, (data, item) -> item.alternatives().stream()
                .anyMatch(each -> data.getMaterial() == each.material()));
        // 'block at X is stone': a block is an item if it's that item's block.
        registrar.comparison(org.bukkit.block.Block.class, ItemType.class, (block, item) -> item.alternatives().stream()
                .anyMatch(each -> block.getType() == each.material()));
        registrar.comparison(org.bukkit.block.Block.class, BlockData.class, (block, data) -> block.getBlockData().matches(data));

        registrar.expression("ExprBlockData", "blockdata", info -> new DataOf(),
                "[the] block[ ]data of %blocks/entities%", "%blocks/entities%'[s] block[ ]data");

        registrar.expression("ExprTypeOf", "object", info -> (context, args) -> {
            List<Object> types = new ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                Object type = switch (value) {
                    case ItemType item -> new ItemType(new org.bukkit.inventory.ItemStack(item.material()), false);
                    case BlockData data -> data.getMaterial().createBlockData();
                    // A block's type is its item, or its block data if it has no item, like water.
                    case org.bukkit.block.Block block -> block.getType().isItem()
                            ? new ItemType(new org.bukkit.inventory.ItemStack(block.getType()), false)
                            : block.getType().createBlockData();
                    case EntityKind kind -> kind.type() == null ? kind : EntityKind.of(kind.type());
                    case Entity entity -> EntityKind.of(entity.getType());
                    case org.bukkit.entity.EntitySnapshot snapshot -> EntityKind.of(snapshot.getEntityType());
                    case Potions.Effect effect -> effect.type;
                    case org.bukkit.inventory.Inventory inventory -> new Items.InventoryKind(inventory.getType().name());
                    case Enchantments.Levelled levelled -> levelled.enchantment();
                    default -> null;
                };
                if (type != null) {
                    types.add(type);
                }
            }
            return types.size() == 1 ? types.getFirst() : types.toArray();
        }, "[the] type of %entitytypes/itemtypes/skriptpotioneffects/blockdatas/entities/blocks%",
                "%entitytypes/itemtypes/skriptpotioneffects/blockdatas/entities/blocks%'[s] type");

        registrar.expression("ExprVersion", "string", info -> {
            boolean skript = info.hasTag("skript");
            boolean minecraft = info.hasTag("minecraft");
            return (context, args) -> skript ? "2.17.0" : minecraft ? Bukkit.getMinecraftVersion() : Bukkit.getVersion();
        }, "(bukkit:[craft]bukkit|minecraft:minecraft|skript:skript)( |-)version");


        registrar.effect("EffLeash", info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                Object holder = pattern == 0 ? args[1] : pattern == 1 ? args[0] : null;
                Object[] entities = (Object[]) args[pattern == 1 ? 1 : 0];
                for (Object entity : entities) {
                    if (entity instanceof LivingEntity living) {
                        living.setLeashHolder(holder instanceof Entity e ? e : null);
                    }
                }
            };
        }, "(leash|lead) %entities% to %entity%", "make %entity% (leash|lead) %entities%",
                "un(leash|lead) [holder of] %entities%");
    }

    /** Block data written as {@code campfire[lit=false]} or {@code minecraft:stone[]}, or {@code null}. */
    static BlockData blockData(String text) {
        String words = text.strip().toLowerCase(Locale.ROOT).replaceFirst("^(?:an?|the) ", "");
        if (words.isEmpty() || words.contains(" ") && !words.contains("[")) {
            return null;
        }
        int bracket = words.indexOf('[');
        String written = (bracket < 0 ? words : words.substring(0, bracket)).strip();
        String name = written.replace(' ', '_');
        String states = bracket < 0 ? "" : words.substring(bracket);
        if (!states.isEmpty()) {
            states = states.replace(';', ',');
        }
        try {
            return Bukkit.createBlockData((name.contains(":") ? name : "minecraft:" + name) + states);
        } catch (IllegalArgumentException e) {
            // An item's name, like 'oak log[]', for its block.
            ItemType named = ItemType.parse(written);
            org.bukkit.Material material = named == null ? null : named.material();
            if (material == null || !material.isBlock()) {
                return null;
            }
            try {
                return Bukkit.createBlockData(material, states.isEmpty() ? null : states);
            } catch (IllegalArgumentException invalid) {
                return null;
            }
        }
    }

    /** {@code the block data of %blocks/entities%}, which can be set for blocks. */
    private static final class DataOf implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> data = new ArrayList<>();
            for (Object owner : (Object[]) arguments[0]) {
                switch (owner) {
                    case Block block -> data.add(block.getBlockData());
                    case FallingBlock falling -> data.add(falling.getBlockData());
                    case BlockDisplay display -> data.add(display.getBlock());
                    default -> {}
                }
            }
            return data.size() == 1 ? data.getFirst() : data.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {BlockData.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (delta == null || delta.length == 0 || !(delta[0] instanceof BlockData data)) {
                return;
            }
            for (Object owner : (Object[]) arguments[0]) {
                switch (owner) {
                    case Block block -> block.setBlockData(data);
                    case FallingBlock falling -> falling.setBlockData(data);
                    case BlockDisplay display -> display.setBlock(data);
                    default -> {}
                }
            }
        }
    }
}
