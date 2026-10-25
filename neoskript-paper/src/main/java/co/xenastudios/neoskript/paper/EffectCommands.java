/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.registration.Registrar;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.entity.Player;

/**
 * What an effect command's code knows, as in Skript: its sender ({@code command sender}, and {@code player}
 * when a player ran it), the sender's world or command block, and {@code me}.
 */
final class EffectCommands {

    private EffectCommands() {}

    static void register(Registrar registrar) {
        registrar.eventValue(EffectCommandEvent.class, "commandsender", EffectCommandEvent::getSender);
        registrar.eventValue(EffectCommandEvent.class, "player",
                event -> event.getSender() instanceof Player player ? player : null);
        registrar.eventValue(EffectCommandEvent.class, "world",
                event -> event.getSender() instanceof Player player ? player.getWorld() : null);
        registrar.eventValue(EffectCommandEvent.class, "block",
                event -> event.getSender() instanceof BlockCommandSender block ? block.getBlock() : null);

        // Skript's ExprMe: the player who ran the effect command, which is only one in an effect command.
        registrar.expression("ExprMe", "player", info -> {
            if (!EventExpressions.isIn(info, EffectCommandEvent.class)) {
                throw new SyntaxError("'me' can only be used in effect commands");
            }
            return (context, args) -> context.event() instanceof EffectCommandEvent event
                    && event.getSender() instanceof Player player ? player : null;
        }, "me", "my[self]");
    }
}
