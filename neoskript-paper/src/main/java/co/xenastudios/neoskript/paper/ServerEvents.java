/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;

/** Server events, the values triggers get from them, and cancelling them. */
final class ServerEvents {

    private ServerEvents() {}

    static void register(Registrar registrar) {
        registrar.event("EvtJoin", List.of(PlayerJoinEvent.class), null, "[player] (login|logging in|join[ing])");
        registrar.event("EvtFirstJoin", List.of(PlayerJoinEvent.class),
                info -> (context, args) -> !((PlayerJoinEvent) context.event()).getPlayer().hasPlayedBefore(),
                "first (join|login)");
        registrar.event("EvtQuit", List.of(PlayerQuitEvent.class), null,
                "(quit[ting]|disconnect[ing]|log[ ]out|logging out|leav(e|ing))");
        registrar.event("EvtChat", List.of(AsyncChatEvent.class), null, "chat");
        // Commands typed by players or the console, before they run: 'on command "home"'.
        registrar.event("EvtCommand", List.of(PlayerCommandPreprocessEvent.class, ServerCommandEvent.class),
                info -> (context, args) -> args[0] == null || matchesCommand(context.event(), (Object[]) args[0]),
                "command [%texts%]");

        registrar.eventValue(PlayerEvent.class, "player", PlayerEvent::getPlayer);
        registrar.eventValue(PlayerEvent.class, "world", event -> event.getPlayer().getWorld());
        registrar.eventValue(ServerCommandEvent.class, "commandsender", ServerCommandEvent::getSender);
        for (String type : List.of("player", "offlineplayer", "commandsender", "audience", "world", "entity")) {
            registrar.eventDefault(type);
        }

        registrar.expression("ExprCommandSender", "commandsender", info -> (context, args) -> sender(context.event()),
                "[the] [command['s]] sender");
        registrar.effect("EffCancelEvent", info -> {
            boolean cancel = info.pattern() == 0;
            return (context, args) -> {
                if (context.event() instanceof Cancellable cancellable) {
                    cancellable.setCancelled(cancel);
                }
            };
        }, "cancel [the] event", "uncancel [the] event");
    }

    /** Who sent the event's command, or the player the event is about. */
    static CommandSender sender(Object event) {
        return switch (event) {
            case ServerCommandEvent command -> command.getSender();
            case PlayerEvent player -> player.getPlayer();
            case null, default -> null;
        };
    }

    /** Whether the event's command is one of {@code labels}, ignoring a leading slash and case. */
    private static boolean matchesCommand(Object event, Object[] labels) {
        String line = switch (event) {
            case PlayerCommandPreprocessEvent player -> player.getMessage();
            case ServerCommandEvent server -> server.getCommand();
            default -> "";
        };
        String command = line.startsWith("/") ? line.substring(1) : line;
        int space = command.indexOf(' ');
        String label = (space < 0 ? command : command.substring(0, space)).toLowerCase(Locale.ROOT);
        for (Object wanted : labels) {
            String text = String.valueOf(wanted).toLowerCase(Locale.ROOT);
            if (label.equals(text.startsWith("/") ? text.substring(1) : text)) {
                return true;
            }
        }
        return false;
    }
}
