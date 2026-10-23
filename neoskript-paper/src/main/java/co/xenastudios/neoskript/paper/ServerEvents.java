/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.server.ServerListPingEvent;
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
        // Before the player is in the world; it can't be cancelled, but the player can be kicked.
        registrar.event("EvtConnect", List.of(org.bukkit.event.player.PlayerLoginEvent.class), null, "[player] connect[ing]");
        registrar.event("EvtSwimToggle", List.of(org.bukkit.event.entity.EntityToggleSwimEvent.class), null,
                "[entity] (swim toggl(e|ing)|toggl(e|ing) swim[ming])");
        registrar.event("EvtChat", List.of(AsyncChatEvent.class), null, "chat");
        // Commands typed by players or the console, before they run: 'on command "home"'.
        registrar.event("EvtCommand", List.of(PlayerCommandPreprocessEvent.class, ServerCommandEvent.class),
                info -> (context, args) -> args[0] == null || matchesCommand(context.event(), (Object[]) args[0]),
                "command [%texts%]");

        // Moving, not just turning around: the position changed.
        registrar.event("EvtMove", List.of(PlayerMoveEvent.class), info -> (context, args) -> {
            PlayerMoveEvent move = (PlayerMoveEvent) context.event();
            return move.hasChangedPosition();
        }, "[player] (move|walk|step)");
        registrar.event("EvtServerListPing", List.of(ServerListPingEvent.class), null, "server [list] ping");

        registrar.eventValue(PlayerEvent.class, "player", PlayerEvent::getPlayer);
        registrar.eventValue(PlayerEvent.class, "world", event -> event.getPlayer().getWorld());
        registrar.eventValue(ServerCommandEvent.class, "commandsender", ServerCommandEvent::getSender);
        for (String type : List.of("player", "offlineplayer", "commandsender", "audience", "world", "entity")) {
            registrar.eventDefault(type);
        }

        registerMessages(registrar);
        registrar.expression("ExprCommandSender", "commandsender", info -> (context, args) -> sender(context.event()),
                "[the] [command['s]] sender");
        registrar.effect("EffCancelEvent", info -> {
            boolean cancel = info.pattern() == 0;
            cancellable(info);
            return (context, args) -> {
                if (context.event() instanceof Cancellable cancellable) {
                    cancellable.setCancelled(cancel);
                }
            };
        }, "cancel [the] event", "uncancel [the] event");
    }

    /** Fails parsing where cancelling does nothing, with Skript's errors. */
    private static void cancellable(co.xenastudios.neoskript.api.element.ElementInfo info) {
        if (info.eventName() == null || info.eventClasses().isEmpty()) {
            return;
        }
        boolean swimming = info.eventClasses().stream().anyMatch(c -> c.getSimpleName().equals("EntityToggleSwimEvent"));
        if (swimming) {
            throw new co.xenastudios.neoskript.api.element.SyntaxError("Cancelling a toggle swim event has no effect");
        }
        if (info.eventClasses().stream().anyMatch(Cancellable.class::isAssignableFrom)) {
            return;
        }
        if (info.eventClasses().stream().anyMatch(c -> c.getSimpleName().equals("PlayerLoginEvent"))) {
            throw new co.xenastudios.neoskript.api.element.SyntaxError("A connect event cannot be cancelled, but the "
                    + "player may be kicked ('kick player by reason of \"...\"')");
        }
        throw new co.xenastudios.neoskript.api.element.SyntaxError("An on " + info.eventName() + " event cannot be cancelled");
    }

    /** The messages events show, which triggers can change: chat, join and quit messages, the MOTD. */
    static void registerMessages(Registrar registrar) {
        registrar.expression("ExprChatMessage", "string", info -> new EventMessage<>(AsyncChatEvent.class,
                event -> PLAIN.serialize(event.message()), (event, text) -> event.message(Messages.component(text))),
                "[the] [chat( |-)]message");
        registrar.expression("ExprChatFormat", "string", info -> new EventMessage<>(AsyncChatEvent.class,
                event -> "<[player]> [message]", (event, text) -> {
                    String format = Values.text(text);
                    event.renderer((source, displayName, message, viewer) -> Messages.component(format)
                            .replaceText(b -> b.matchLiteral("[player]").replacement(displayName))
                            .replaceText(b -> b.matchLiteral("[display name]").replacement(displayName))
                            .replaceText(b -> b.matchLiteral("[message]").replacement(message)));
                }), "[the] (message|chat) format[ting]");
        registrar.expression("ExprJoinMessage", "string", info -> new EventMessage<>(PlayerJoinEvent.class,
                event -> event.joinMessage() == null ? null : Messages.LEGACY_SECTION.serialize(event.joinMessage()),
                (event, text) -> event.joinMessage(text == null ? null : Messages.component(text))),
                "[the] (join|log[ ]in)( |-)message");
        registrar.expression("ExprQuitMessage", "string", info -> new EventMessage<>(PlayerQuitEvent.class,
                event -> event.quitMessage() == null ? null : Messages.LEGACY_SECTION.serialize(event.quitMessage()),
                (event, text) -> event.quitMessage(text == null ? null : Messages.component(text))),
                "[the] (quit|leave|log[ ]out)( |-)message");
        registrar.expression("ExprMOTD", "string", info -> new EventMessage<>(ServerListPingEvent.class,
                event -> Messages.LEGACY_SECTION.serialize(event.motd()),
                (event, text) -> event.motd(text == null ? Component.empty() : Messages.component(text))),
                "[the] [default|shown|displayed] (MOTD|message of [the] day)");
    }

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** An event's message, which {@code set} and {@code delete} change. */
    private record EventMessage<E>(Class<E> eventClass, java.util.function.Function<E, Object> getter,
            java.util.function.BiConsumer<E, Object> setter) implements Expression, Changeable {

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return eventClass.isInstance(context.event()) ? getter.apply(eventClass.cast(context.event())) : null;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {Object.class} : mode == Mode.DELETE ? new Class<?>[0] : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (eventClass.isInstance(context.event())) {
                setter.accept(eventClass.cast(context.event()), delta == null || delta.length == 0 ? null : delta[0]);
            }
        }
    }

    /** Who sent the event's command, or the player the event is about. */
    static CommandSender sender(Object event) {
        return switch (event) {
            case ServerCommandEvent command -> command.getSender();
            case co.xenastudios.neoskript.runtime.command.CommandEvent command -> (CommandSender) command.sender();
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
