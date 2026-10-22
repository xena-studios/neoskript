/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import net.kyori.adventure.audience.Audience;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** The basic types: text, numbers, booleans, who messages go to, players, worlds and entities. */
final class CoreTypes {

    private CoreTypes() {}

    static void register(Registrar registrar) {
        registrar.type("string", "text", String.class);
        registrar.type("number", "number", Number.class);
        registrar.type("integer", "integer", Long.class);
        registrar.type("boolean", "boolean", Boolean.class);
        registrar.subtype("integer", "number");
        // Numbers can be used where whole numbers are expected; they're rounded when they're used.
        registrar.subtype("number", "integer");
        registrar.alias("integer", "int", "ints");

        registrar.type("audience", "audience", Audience.class);
        registrar.type("commandsender", "command sender", CommandSender.class);
        registrar.type("offlineplayer", "offline player", OfflinePlayer.class);
        registrar.type("player", "player", Player.class);
        registrar.type("world", "world", World.class);
        registrar.type("entity", "entity", Entity.class);
        registrar.type("uuid", "uuid", UUID.class);
        registrar.subtype("commandsender", "audience");
        registrar.subtype("player", "commandsender");
        registrar.subtype("player", "offlineplayer");
        registrar.subtype("player", "entity");

        // How they read in text: players and worlds by name, the console as Skript writes it.
        registrar.text(OfflinePlayer.class, player -> String.valueOf(player.getName()));
        registrar.text(World.class, World::getName);
        registrar.text(CommandSender.class, sender -> sender instanceof org.bukkit.command.ConsoleCommandSender
                ? "the console" : sender.getName());
    }
}
