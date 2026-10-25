/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

/**
 * Skript's converters between Bukkit's classes that the other areas don't register, with Skript's chaining flags,
 * from its {@code DefaultConverters}, {@code LocationClassInfo} and {@code DisplayModule}. Skript looks event
 * values up through them: {@code event-block} in a command run by a command block is its block, and
 * {@code event-player} in a script's load event is the console if it were a player.
 */
final class DefaultConverters {

    private DefaultConverters() {}

    static void register(Registrar registrar) {
        registrar.converter(OfflinePlayer.class, Player.class, OfflinePlayer::getPlayer);
        registrar.converter(CommandSender.class, Player.class, sender -> sender instanceof Player player ? player : null);
        registrar.converter(BlockCommandSender.class, Block.class, BlockCommandSender::getBlock);
        registrar.converter(Entity.class, Player.class, entity -> entity instanceof Player player ? player : null);
        registrar.converter(Entity.class, LivingEntity.class,
                entity -> entity instanceof LivingEntity living ? living : null);
        // Skript's entity to entity data; NeoSkript's entity types are only that for event values.
        registrar.converter(Entity.class, EntityKind.class, entity -> EntityKind.of(entity.getType()),
                Registrar.NO_RIGHT_CHAINING | Registrar.EVENT_VALUES_ONLY);
        registrar.converter(Block.class, InventoryHolder.class,
                block -> block.getState() instanceof InventoryHolder holder ? holder : null, Registrar.NO_RIGHT_CHAINING);
        registrar.converter(InventoryHolder.class, Block.class, holder -> switch (holder) {
            case BlockState state -> state.getBlock();
            case DoubleChest chest -> chest.getInventory().getLocation() == null ? null
                    : chest.getInventory().getLocation().getBlock();
            default -> null;
        }, Registrar.NO_CHAINING);
        registrar.converter(InventoryHolder.class, Entity.class, holder -> holder instanceof Entity entity ? entity : null,
                Registrar.NO_CHAINING);
        registrar.converter(Entity.class, InventoryHolder.class,
                entity -> entity instanceof InventoryHolder holder ? holder : null, Registrar.NO_RIGHT_CHAINING);
        registrar.converter(OfflinePlayer.class, org.bukkit.Location.class, OfflinePlayer::getLocation);
        registrar.converter(Entity.class, Display.class, entity -> entity instanceof Display display ? display : null,
                Registrar.NO_RIGHT_CHAINING);
    }
}
