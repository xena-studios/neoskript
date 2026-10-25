/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.command.CommandEvent;
import co.xenastudios.neoskript.runtime.command.ScriptCommandInfo;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/** Running commands, and what script command triggers know: the command, its cooldown, and other commands. */
final class Commands {

    private Commands() {}

    static void register(Registrar registrar) {
        // A command's 'suggestions:' entry: a text component suggests its plain text, its hover text the tooltip.
        co.xenastudios.neoskript.runtime.command.CommandSuggestionEvent.toSuggestion = value -> {
            if (value instanceof net.kyori.adventure.text.Component component) {
                net.kyori.adventure.text.event.HoverEvent<?> hover = component.hoverEvent();
                return new co.xenastudios.neoskript.runtime.command.CommandSuggestionEvent.Suggestion(
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(component),
                        hover != null && hover.value() instanceof net.kyori.adventure.text.Component tooltip
                                ? tooltip : null);
            }
            return new co.xenastudios.neoskript.runtime.command.CommandSuggestionEvent.Suggestion(
                    value instanceof String text ? text : Values.text(value), null);
        };
        co.xenastudios.neoskript.runtime.command.CommandSuggestionEvent.fromSuggestion = suggestion -> {
            net.kyori.adventure.text.Component component = net.kyori.adventure.text.Component.text(suggestion.text());
            return suggestion.tooltip() instanceof net.kyori.adventure.text.Component tooltip
                    ? component.hoverEvent(tooltip) : component;
        };
        // Skript's: its player is the sender, if a player, as event values of a superclass are cast down.
        registrar.eventValue(CommandEvent.class, "string", String[].class, 0, event -> {
            // Its arguments as typed, split at spaces, as Bukkit gives them.
            String full = event.fullCommand();
            int space = full.indexOf(' ');
            return space < 0 ? new String[0] : full.substring(space + 1).split(" ", -1);
        });
        registrar.eventValue(CommandEvent.class, "commandsender", CommandEvent::sender);
        registrar.eventValue(CommandEvent.class, "world",
                event -> event.sender() instanceof Player p ? p.getWorld() : null);

        registrar.effect("EffCommand", info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                Object[] commands = (Object[]) args[pattern == 0 ? 0 : 1];
                Object senders = args[pattern == 0 ? 1 : 0];
                Object[] who = senders == null ? new Object[] {Bukkit.getConsoleSender()} : (Object[]) senders;
                for (Object sender : who) {
                    if (!(sender instanceof CommandSender s)) {
                        continue;
                    }
                    for (Object command : commands) {
                        String line = Values.text(command);
                        ScriptCommandInfo.FAILED.remove();
                        dispatch(s, line.startsWith("/") ? line.substring(1) : line);
                        String failed = ScriptCommandInfo.FAILED.get();
                        if (failed != null) {
                            ScriptCommandInfo.FAILED.remove();
                            co.xenastudios.neoskript.runtime.RuntimeErrors.report("Failed to execute command: " + line
                                    + " (" + failed + ")");
                        }
                    }
                }
            };
        }, "execute [the] [bungee[cord]] command[s] %texts% [by %-commandsenders%]",
                "execute [the] %commandsenders% [bungee[cord]] command[s] %texts%",
                "(let|make) %commandsenders% execute [[the] [bungee[cord]] command[s]] %texts%");

        registrar.expression("ExprCommand", "string", info -> {
            boolean full = info.pattern() == 0;
            return (context, args) -> {
                String line = switch (context.event()) {
                    case CommandEvent command -> full ? command.fullCommand() : command.label();
                    case PlayerCommandPreprocessEvent player -> player.getMessage().substring(1);
                    case ServerCommandEvent server -> server.getCommand();
                    case null, default -> null;
                };
                if (line == null || full || context.event() instanceof CommandEvent) {
                    return line;
                }
                int space = line.indexOf(' ');
                return space < 0 ? line : line.substring(0, space);
            };
        }, "[the] (full|complete|whole) command", "[the] command [label|alias]");

        registrar.expression("ExprCmdCooldownInfo", "object", info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                if (!(context.event() instanceof CommandEvent command)) {
                    return null;
                }
                return switch (pattern) {
                    case 0 -> command.cooldown();
                    case 1 -> command.cooldownBypass();
                    case 2 -> command.remaining();
                    default -> command.elapsed();
                };
            };
        }, "[the] ((cooldown|wait) time|[wait] time of [the] (cooldown|wait) [(of|for) [the] [current] command])",
                "[the] [cooldown] bypass perm[ission] [of [the] (cooldown|wait) [(of|for) [the] [current] command]]",
                "[the] remaining [time] [of [the] (cooldown|wait) [(of|for) [the] [current] command]]",
                "[the] elapsed [time] [of [the] (cooldown|wait) [(of|for) [the] [current] command]]");

        registrar.effect("EffCancelCooldown", info -> {
            boolean cancel = !info.hasTag("un");
            return (context, args) -> {
                if (context.event() instanceof CommandEvent command) {
                    command.cancelCooldown(cancel);
                }
            };
        }, "[un:un](cancel|ignore) [the] [current] [command] cooldown");

        // Ends the command's trigger, showing its sender the message as the command's error.
        registrar.effect("EffFailExecution", info -> {
            String message = info.argumentLiteral(0);
            EventExpressions.restrict(info, "fail " + (info.hasTag("the") ? "the " : "") + "command execution with "
                    + (info.hasTag("the2") ? "the " : "") + "error " + (info.hasTag("message") ? "message " : "")
                    + (message == null ? "..." : "\"" + message + "\""), "", CommandEvent.class);
            return (context, args) -> {
                if (context.event() instanceof CommandEvent command && Values.single(args[0]) != null) {
                    command.fail(TextComponents.from(Values.single(args[0])));
                }
            };
        }, "fail [the:the] command execution with [the2:the] error [message:message] %textcomponent%");

        commandInfo(registrar);

        registrar.condition("CondIsScriptCommand", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> negated != (command(Values.text(args[0])) instanceof ScriptCommandInfo);
        }, Patterns.isProperty("[a] s(c|k)ript (command|cmd)", "text"));

        registrar.expression("ExprAllCommands", "string", info -> {
            boolean script = info.hasTag("script");
            return (context, args) -> {
                Set<String> names = new LinkedHashSet<>();
                for (var entry : Bukkit.getCommandMap().getKnownCommands().entrySet()) {
                    if (!script || entry.getValue() instanceof ScriptCommandInfo) {
                        names.add(entry.getKey());
                    }
                }
                return names.toArray();
            };
        }, "[all [[of] the]|the] [registered] [script:script] commands");
    }

    /**
     * Runs {@code command} as {@code sender} would type it, as Skript does: the command events fire first, so
     * {@code on command} triggers see it and can cancel or rewrite it.
     */
    private static void dispatch(CommandSender sender, String command) {
        try {
            if (sender instanceof Player player) {
                PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/" + command);
                Bukkit.getPluginManager().callEvent(event);
                if (!event.isCancelled() && event.getMessage().startsWith("/")) {
                    Bukkit.dispatchCommand(event.getPlayer(), event.getMessage().substring(1));
                }
            } else {
                ServerCommandEvent event = new ServerCommandEvent(sender, command);
                Bukkit.getPluginManager().callEvent(event);
                if (!event.isCancelled() && !event.getCommand().isEmpty()) {
                    Bukkit.dispatchCommand(event.getSender(), event.getCommand());
                }
            }
        } catch (org.bukkit.command.CommandException e) {
            co.xenastudios.neoskript.runtime.RuntimeErrors.report("Failed to execute command: "
                    + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
        }
    }

    /** {@code the description of command "x"}, its label, usage, aliases, permission... */
    private static void commandInfo(Registrar registrar) {
        List<Function<Command, Object>> getters = List.of(
                Command::getName,
                Command::getDescription,
                Command::getLabel,
                Command::getUsage,
                command -> command.getAliases().toArray(),
                Command::getPermission,
                command -> command instanceof ScriptCommandInfo script ? script.scriptPermissionMessage() : null,
                // Script commands are Skript's, as their 'skript:' fallback prefix says.
                command -> command instanceof ScriptCommandInfo ? "Skript"
                        : command instanceof PluginIdentifiableCommand owned ? owned.getPlugin().getName() : null);
        List<String> properties = List.of("main command [label|name]", "description", "label", "usage", "aliases",
                "permission", "permission message", "plugin [owner]");
        List<String> patterns = new ArrayList<>();
        for (String property : properties) {
            String prefix = property.equals("aliases") ? "[all [[of] the]|the] " : "[the] ";
            patterns.add(prefix + property + " [of [[the] command[s]] %texts%]");
            patterns.add("command[s] %texts%'[s] " + property);
        }
        registrar.expression("ExprCommandInfo", "string", info -> {
            Function<Command, Object> getter = getters.get(info.pattern() / 2);
            if (info.pattern() / 2 == 6) {
                co.xenastudios.neoskript.api.element.Warnings.deprecated("Permission messages are deprecated for player"
                        + " executed commands, as clients are not aware of commands that they cannot execute.");
            }
            return (context, args) -> {
                Object[] names = args[0] == null ? new Object[] {currentLabel(context.event())} : (Object[]) args[0];
                List<Object> values = new ArrayList<>();
                for (Object name : names) {
                    Command command = name == null ? null : command(Values.text(name));
                    Object value = command == null ? null : getter.apply(command);
                    if (value instanceof Object[] array) {
                        values.addAll(List.of(array));
                    } else if (value != null) {
                        values.add(value);
                    }
                }
                return values.size() == 1 ? values.getFirst() : values.toArray();
            };
        }, patterns.toArray(String[]::new));
    }

    private static String currentLabel(Object event) {
        return event instanceof CommandEvent command ? command.label() : null;
    }

    /** The server command named {@code name} (with or without a slash), or {@code null}. */
    static Command command(String name) {
        String label = name.startsWith("/") ? name.substring(1) : name;
        return Bukkit.getCommandMap().getCommand(label);
    }
}
