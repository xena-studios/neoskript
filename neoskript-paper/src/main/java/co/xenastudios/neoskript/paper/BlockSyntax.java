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
        registrar.parser("blockdata", BlockSyntax::blockData);
        registrar.text(BlockData.class, data -> data.getAsString().replaceFirst("^minecraft:", ""));
        registrar.converter(Block.class, BlockData.class, Block::getBlockData);
        registrar.converter(ItemType.class, BlockData.class, item -> item.material().isBlock()
                ? item.material().createBlockData() : null);
        registrar.comparison(BlockData.class, BlockData.class, (a, b) -> a.matches(b) || b.matches(a));

        registrar.expression("ExprBlockData", "blockdata", info -> new DataOf(),
                "[the] block[ ]data of %blocks/entities%", "%blocks/entities%'[s] block[ ]data");

        registrar.expression("ExprTypeOf", "object", info -> (context, args) -> {
            List<Object> types = new ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                Object type = switch (value) {
                    case ItemType item -> new ItemType(new org.bukkit.inventory.ItemStack(item.material()), false);
                    case BlockData data -> data.getMaterial().createBlockData();
                    case EntityKind kind -> kind.type() == null ? kind : EntityKind.of(kind.type());
                    case Entity entity -> EntityKind.of(entity.getType());
                    case org.bukkit.potion.PotionEffect effect -> effect.getType();
                    default -> null;
                };
                if (type != null) {
                    types.add(type);
                }
            }
            return types.size() == 1 ? types.getFirst() : types.toArray();
        }, "[the] type of %entitytypes/itemtypes/skriptpotioneffects/blockdatas/entities%",
                "%entitytypes/itemtypes/skriptpotioneffects/blockdatas/entities%'[s] type");

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
        String words = text.strip().toLowerCase(Locale.ROOT);
        if (words.isEmpty() || words.contains(" ") && !words.contains("[")) {
            return null;
        }
        int bracket = words.indexOf('[');
        String name = (bracket < 0 ? words : words.substring(0, bracket)).strip().replace(' ', '_');
        String states = bracket < 0 ? "" : words.substring(bracket);
        if (!states.isEmpty()) {
            states = states.replace(';', ',');
        }
        try {
            return Bukkit.createBlockData((name.contains(":") ? name : "minecraft:" + name) + states);
        } catch (IllegalArgumentException e) {
            return null;
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
