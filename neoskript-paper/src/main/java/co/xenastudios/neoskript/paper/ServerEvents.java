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
import org.bukkit.block.Block;
import org.bukkit.event.entity.EntityExplodeEvent;

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

        // Moving is in PlayerEvents, as Skript's EvtMove: 'on player move', 'on zombie move'.
        // Paper's own ping event, as Skript listens to, which syntax only for it can be used in.
        registrar.event("EvtServerListPing", List.of(com.destroystokyo.paper.event.server.PaperServerListPingEvent.class),
                null, "server [list] ping");

        registrar.eventValue(PlayerEvent.class, "player", PlayerEvent::getPlayer);
        registrar.eventValue(PlayerEvent.class, "world", event -> event.getPlayer().getWorld());
        registrar.eventValue(ServerCommandEvent.class, "commandsender", ServerCommandEvent::getSender);
        // Skript's types whose default expression is the event's value of them.
        // As Skript: a left-out audience is the event's command sender, as there may be several audiences.
        registrar.eventDefault("audience", org.bukkit.command.CommandSender.class);
        for (String type : List.of("player", "offlineplayer", "commandsender", "world", "entity",
                "livingentity", "projectile", "block", "location", "inventory", "inventoryholder", "slot", "itemstack",
                "nameable", "vector", "display", "time", "gameeffect", "particle", "convergingparticle",
                "directionalparticle", "scalableparticle", "equippablecomponent", "worldborder", "quaternion")) {
            registrar.eventDefault(type);
        }

        registerMessages(registrar);
        // The server's max players, or in a server list ping, the count shown.
        registrar.expression("ExprMaxPlayers", "integer", info -> {
            boolean ping = info.eventClasses().stream().anyMatch(ServerListPingEvent.class::isAssignableFrom);
            if (info.hasTag("fake") && !ping) {
                throw new co.xenastudios.neoskript.api.element.SyntaxError(
                        "The 'shown' max players count expression can't be used outside of a server list ping event");
            }
            return new MaxPlayers(info.hasTag("real") || !info.hasTag("fake") && !ping);
        }, "[the] [real:(real|default)|fake:(fake|shown|displayed)] max[imum] player[s] [count|amount|number|size]",
                "[the] [real:(real|default)|fake:(fake|shown|displayed)] max[imum] (count|amount|number|size) of players");
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
            case EffectCommandEvent command -> command.getSender();
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
        String command = (line.startsWith("/") ? line.substring(1) : line).toLowerCase(Locale.ROOT);
        for (Object wanted : labels) {
            String text = String.valueOf(wanted).toLowerCase(Locale.ROOT);
            String label = text.startsWith("/") ? text.substring(1) : text;
            // As in Skript: 'home' matches 'home' and 'home x'; 'home set' matches anything starting with it.
            if (command.startsWith(label) && (label.contains(" ") || command.length() == label.length()
                    || Character.isWhitespace(command.charAt(label.length())))) {
                return true;
            }
        }
        return false;
    }

    /** Events whose values need the world types: explosions. */
    static void registerLate(Registrar registrar) {
        registrar.event("EvtExplode", List.of(EntityExplodeEvent.class), null, "explo(d(e|ing)|sion)");
        registrar.eventValue(EntityExplodeEvent.class, "entity", EntityExplodeEvent::getEntity);
        registrar.eventValue(EntityExplodeEvent.class, "location", EntityExplodeEvent::getLocation);
        registrar.eventValue(EntityExplodeEvent.class, "world", event -> event.getLocation().getWorld());
        registrar.expression("ExprExplodedBlocks", "block", info -> {
            if (info.eventClasses().stream().noneMatch(EntityExplodeEvent.class::isAssignableFrom)) {
                throw new co.xenastudios.neoskript.api.element.SyntaxError("'" + (info.hasTag("the") ? "the " : "")
                        + "exploded blocks' can only be used in the on explode event");
            }
            return new ExplodedBlocks();
        }, "[the:the] exploded blocks");
    }

    /** {@code the max players}: the server's, or the count a server list ping shows. */
    private record MaxPlayers(boolean real) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            if (real) {
                return (long) org.bukkit.Bukkit.getMaxPlayers();
            }
            return context.event() instanceof ServerListPingEvent ping ? (long) ping.getMaxPlayers() : null;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {Number.class};
                case RESET, DELETE -> real ? null : new Class<?>[] {Number.class};
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            int amount = delta != null && delta.length > 0 && delta[0] instanceof Number n ? n.intValue() : 0;
            if (real) {
                int current = org.bukkit.Bukkit.getMaxPlayers();
                org.bukkit.Bukkit.setMaxPlayers(mode == Mode.ADD ? current + amount : mode == Mode.REMOVE ? current - amount : amount);
            } else if (context.event() instanceof ServerListPingEvent ping) {
                int current = ping.getMaxPlayers();
                ping.setMaxPlayers(mode == Mode.ADD ? current + amount : mode == Mode.REMOVE ? current - amount : amount);
            }
        }
    }

    /** {@code the exploded blocks}: the blocks an explosion destroys, which can be changed. */
    private static final class ExplodedBlocks implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return context.event() instanceof EntityExplodeEvent event ? event.blockList().toArray() : new Object[0];
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case ADD, REMOVE, SET -> new Class<?>[] {Block.class};
                case DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(context.event() instanceof EntityExplodeEvent event)) {
                return;
            }
            List<Block> blocks = event.blockList();
            if (mode == Mode.DELETE || mode == Mode.SET) {
                blocks.clear();
            }
            for (Object value : delta == null ? new Object[0] : delta) {
                if (value instanceof Block block) {
                    if (mode == Mode.REMOVE) {
                        blocks.remove(block);
                    } else {
                        blocks.add(block);
                    }
                }
            }
        }
    }
}
