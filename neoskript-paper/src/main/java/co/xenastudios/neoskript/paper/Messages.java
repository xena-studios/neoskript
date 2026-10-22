/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;

/** Sending and broadcasting messages, and the console. */
final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Messages() {}

    static void register(Registrar registrar) {
        registrar.effect("EffMessage", info -> (context, args) -> {
            // TODO: without 'to', Skript sends to the event's player or command sender; that needs event values.
            Object[] recipients = args[1] == null ? new Object[] {Bukkit.getConsoleSender()} : (Object[]) args[1];
            for (Object message : (Object[]) args[0]) {
                Component component = component(message);
                for (Object recipient : recipients) {
                    if (recipient instanceof Audience audience) {
                        audience.sendMessage(component);
                    }
                }
            }
        }, "(message|send [message[s]]) %objects% [to %audiences%]");

        registrar.effect("EffBroadcast", info -> (context, args) -> {
            for (Object message : (Object[]) args[0]) {
                Component component = component(message);
                if (args[1] == null) {
                    Bukkit.getServer().broadcast(component);
                    continue;
                }
                for (Object world : (Object[]) args[1]) {
                    if (world instanceof World w) {
                        w.getPlayers().forEach(player -> player.sendMessage(component));
                    }
                }
            }
        }, "broadcast %objects% [(to|in) %worlds%]");

        registrar.expression("LitConsole", "commandsender", info -> (context, args) -> Bukkit.getConsoleSender(),
                "[the] (console|server)");
    }

    /** A message as a text component. {@code &} color codes are applied to text. */
    static Component component(Object message) {
        return message instanceof Component component ? component : LEGACY.deserialize(Values.text(message));
    }
}
