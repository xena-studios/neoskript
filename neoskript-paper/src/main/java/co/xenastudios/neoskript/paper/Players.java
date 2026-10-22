/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import net.kyori.adventure.text.Component;
import org.bukkit.Nameable;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Players and other named things: names, UUIDs, being online, permissions, kicking. */
final class Players {

    private Players() {}

    static void register(Registrar registrar) {
        registrar.property("ExprUUID", "uuid", "uuid[s]", "offlineplayers/worlds/entities", Object.class,
                owner -> switch (owner) {
                    case OfflinePlayer player -> player.getUniqueId();
                    case World world -> world.getUID();
                    case Entity entity -> entity.getUniqueId();
                    default -> null;
                });
        registrar.property("PropExprName", "string", "name[s]", "objects", Object.class, Players::name);

        registrar.condition("CondIsOnline", info -> {
            // 'offline' means not online; the negated pattern flips it again.
            boolean online = !info.hasTag("offline") ^ info.pattern() == 1;
            return (context, args) -> {
                Object[] players = (Object[]) args[0];
                for (Object player : players) {
                    if (!(player instanceof OfflinePlayer offline) || offline.isOnline() != online) {
                        return false;
                    }
                }
                return players.length > 0;
            };
        }, "%offlineplayers% (is|are) (online|offline:offline|connected)",
                "%offlineplayers% (isn't|is not|aren't|are not) (online|offline:offline|connected)");

        registrar.condition("CondPermission", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> {
                Object[] senders = (Object[]) args[0];
                for (Object sender : senders) {
                    for (Object permission : (Object[]) args[1]) {
                        boolean has = sender instanceof CommandSender s && s.hasPermission(Values.text(permission));
                        if (has == negated) {
                            return false;
                        }
                    }
                }
                return senders.length > 0;
            };
        }, Patterns.hasProperty("[the] permission[s] %texts%", "commandsenders"));

        registrar.effect("EffKick", info -> (context, args) -> {
            Component reason = args[1] == null ? null : Messages.component(args[1]);
            for (Object player : (Object[]) args[0]) {
                if (player instanceof Player online) {
                    if (reason == null) {
                        online.kick();
                    } else {
                        online.kick(reason);
                    }
                }
            }
        }, "kick %players% [(by reason of|because [of]|on account of|due to) %text%]");
    }

    /** The name of a player, world, entity or anything else with one. */
    static String name(Object owner) {
        return switch (owner) {
            case OfflinePlayer player -> player.getName();
            case World world -> world.getName();
            case CommandSender sender -> sender.getName();
            case Nameable nameable when nameable.customName() != null ->
                    Messages.LEGACY_SECTION.serialize(nameable.customName());
            default -> null;
        };
    }
}
