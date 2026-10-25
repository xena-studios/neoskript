/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.compiler.CommandCompletion;
import co.xenastudios.neoskript.compiler.CompiledCommand;
import co.xenastudios.neoskript.frontend.parse.CommandPattern;
import co.xenastudios.neoskript.paper.Messages;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.Trigger;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.command.CommandEvent;
import co.xenastudios.neoskript.runtime.command.CommandSuggestionEvent;
import co.xenastudios.neoskript.runtime.command.ScriptCommandInfo;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/**
 * A command a script defines, registered with the server. It finds the first route (subcommand, then the
 * command itself) that what was typed fits, checks who's running it, its permission and its cooldown, and
 * runs the route's trigger.
 */
final class ScriptCommand extends Command implements PluginIdentifiableCommand, ScriptCommandInfo {

    private static final String DEFAULT_PERMISSION_MESSAGE =
            "You don't have the required permission to use this command";
    private static final String DEFAULT_COOLDOWN_MESSAGE =
            "You are using this command too often, please try again later";

    private final Plugin plugin;
    private final Logger logger;
    private final CompiledCommand command;
    /** When each player last used the command, kept across reloads. */
    private final Map<UUID, Long> lastUses;

    ScriptCommand(Plugin plugin, Logger logger, CompiledCommand command, Map<UUID, Long> lastUses) {
        super(command.name(), command.description() == null ? "" : command.description(), usage(command),
                command.aliases());
        this.plugin = plugin;
        this.logger = logger;
        this.command = command;
        this.lastUses = lastUses;
        setPermission(command.permission());
    }

    private static String usage(CompiledCommand command) {
        if (command.usage() != null) {
            return command.usage();
        }
        Set<String> usages = new LinkedHashSet<>();
        for (CompiledCommand.Route route : command.routes()) {
            String pattern = CommandPattern.usage(route.pattern());
            usages.add("/" + command.name() + (pattern.isEmpty() ? "" : " " + pattern));
        }
        return String.join("\n", usages);
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public String scriptPermissionMessage() {
        return message(command.permissionMessage(),
                new CommandEvent(null, command.name(), command.name(), new Object[0], new String[0]));
    }

    @Override
    public boolean execute(CommandSender sender, String typedLabel, String[] args) {
        // 'skript:home' is run as 'home'.
        String label = typedLabel.substring(typedLabel.indexOf(':') + 1);
        String input = String.join(" ", args);
        String fullCommand = label + (input.isEmpty() ? "" : " " + input);
        if (command.permission() != null && !sender.hasPermission(command.permission())) {
            denyPermission(sender, label, fullCommand);
            return true;
        }
        boolean wrongExecutor = false;
        boolean noPermission = false;
        for (CompiledCommand.Route route : command.routes()) {
            CompiledCommand.Matcher.Match match = route.match(input);
            if (match == null) {
                continue;
            }
            if (!canExecute(route.executors(), sender)) {
                wrongExecutor = true;
                continue;
            }
            if (route.permission() != null && !sender.hasPermission(route.permission())) {
                noPermission = true;
                continue;
            }
            run(sender, route, new CommandEvent(sender, label, fullCommand, match.arguments(), match.choices()));
            return true;
        }
        if (noPermission) {
            denyPermission(sender, label, fullCommand);
        } else if (wrongExecutor) {
            send(sender, "<red>This command can't be used by " + (sender instanceof Player ? "players" : "the console"));
        } else {
            sender.sendMessage(Messages.component("Usage: " + getUsage().replace("\n", "\n       ")));
        }
        // 'execute command' reports this as a runtime error.
        ScriptCommandInfo.FAILED.set(wrongExecutor ? "it can't be used by this sender"
                : noPermission ? "no permission" : "invalid arguments");
        return true;
    }

    private void run(CommandSender sender, CompiledCommand.Route route, CommandEvent event) {
        Long previous = null;
        boolean cooling = false;
        if (command.cooldown() != null && sender instanceof Player player
                && (command.cooldownBypass() == null || !player.hasPermission(command.cooldownBypass()))) {
            long now = System.currentTimeMillis();
            previous = lastUses.get(player.getUniqueId());
            if (previous != null && now - previous < command.cooldown().millis()) {
                event.cooldown(command.cooldown(), new Timespan(now - previous), command.cooldownBypass());
                String message = message(command.cooldownMessage(), event);
                send(sender, message == null ? DEFAULT_COOLDOWN_MESSAGE : message);
                return;
            }
            event.cooldown(command.cooldown(), previous == null ? null : new Timespan(now - previous),
                    command.cooldownBypass());
            lastUses.put(player.getUniqueId(), now);
            cooling = true;
        }
        try {
            route.trigger().execute(new SimpleTriggerContext(event, sender instanceof Player ? sender : null));
        } catch (ScriptRuntimeException e) {
            if (!(e.getCause() instanceof CommandEvent.Failed)) {
                logger.error("Error in {}, line {}: {}", e.script(), e.line(), e.getCause().toString(), e.getCause());
            }
        }
        if (event.failure() instanceof net.kyori.adventure.text.Component failure) {
            // Shown as the server shows a command's error, in red.
            sender.sendMessage(failure.colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.RED));
        }
        if (cooling && event.cooldownCancelled()) {
            UUID id = ((Player) sender).getUniqueId();
            if (previous == null) {
                lastUses.remove(id);
            } else {
                lastUses.put(id, previous);
            }
        }
    }

    private void denyPermission(CommandSender sender, String label, String fullCommand) {
        CommandEvent event = new CommandEvent(sender, label, fullCommand, new Object[0], new String[0]);
        String message = message(command.permissionMessage(), event);
        send(sender, message == null ? "<red>" + DEFAULT_PERMISSION_MESSAGE : message);
    }

    /** Works out a message entry, or returns {@code null} if the command doesn't have it. */
    private String message(Trigger message, CommandEvent event) {
        if (message == null) {
            return null;
        }
        try {
            message.execute(new SimpleTriggerContext(event));
            return Values.text(event.result());
        } catch (ScriptRuntimeException e) {
            logger.error("Error in {}, line {}: {}", e.script(), e.line(), e.getCause().toString(), e.getCause());
            return null;
        }
    }

    private static void send(CommandSender sender, String message) {
        if (message.startsWith("<red>")) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(message.substring(5),
                    net.kyori.adventure.text.format.NamedTextColor.RED));
        } else {
            sender.sendMessage(Messages.component(message));
        }
    }

    private static boolean canExecute(Set<String> executors, CommandSender sender) {
        if (executors.isEmpty()) {
            return true;
        }
        for (String executor : executors) {
            boolean allowed = switch (executor) {
                case "players" -> sender instanceof Player;
                case "console" -> sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
                case "operators" -> sender.isOp();
                case "command blocks" -> sender instanceof BlockCommandSender;
                default -> sender instanceof Entity && !(sender instanceof Player);
            };
            if (allowed) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- tab completion

    /**
     * Tooltips of the suggestions last given to each player, for {@link #addTooltips}: Bukkit's tab completion
     * only takes text.
     */
    private static final Map<UUID, Map<String, net.kyori.adventure.text.Component>> TOOLTIPS =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        String label = alias.substring(alias.indexOf(':') + 1);
        List<CommandSuggestionEvent.Suggestion> suggestions;
        try {
            suggestions = CommandCompletion.complete(command, sender, sender instanceof Player ? sender : null, label,
                    args, route -> canExecute(route.executors(), sender)
                            && (route.permission() == null || sender.hasPermission(route.permission())),
                    ScriptCommand::suggestions);
        } catch (ScriptRuntimeException e) {
            logger.error("Error in {}, line {}: {}", e.script(), e.line(), e.getCause().toString(), e.getCause());
            return List.of();
        }
        Map<String, net.kyori.adventure.text.Component> tooltips = new java.util.HashMap<>();
        List<String> texts = new ArrayList<>();
        for (CommandSuggestionEvent.Suggestion suggestion : suggestions) {
            texts.add(suggestion.text());
            if (suggestion.tooltip() instanceof net.kyori.adventure.text.Component tooltip) {
                tooltips.put(suggestion.text(), tooltip);
            }
        }
        if (sender instanceof Player player) {
            if (tooltips.isEmpty()) {
                TOOLTIPS.remove(player.getUniqueId());
            } else {
                TOOLTIPS.put(player.getUniqueId(), tooltips);
            }
        }
        return texts;
    }

    /**
     * Gives the suggestions about to be sent to a player the tooltips a {@code suggestions:} entry gave them, as
     * Skript's do.
     */
    static void addTooltips(com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent event) {
        Map<String, net.kyori.adventure.text.Component> tooltips = TOOLTIPS.remove(event.getPlayer().getUniqueId());
        if (tooltips == null) {
            return;
        }
        com.mojang.brigadier.suggestion.Suggestions suggestions = event.getSuggestions();
        List<com.mojang.brigadier.suggestion.Suggestion> list = new ArrayList<>();
        boolean changed = false;
        for (com.mojang.brigadier.suggestion.Suggestion suggestion : suggestions.getList()) {
            net.kyori.adventure.text.Component tooltip = tooltips.get(suggestion.getText());
            if (tooltip != null && suggestion.getTooltip() == null) {
                suggestion = new com.mojang.brigadier.suggestion.Suggestion(suggestion.getRange(), suggestion.getText(),
                        io.papermc.paper.command.brigadier.MessageComponentSerializer.message().serialize(tooltip));
                changed = true;
            }
            list.add(suggestion);
        }
        if (changed) {
            event.setSuggestions(new com.mojang.brigadier.suggestion.Suggestions(suggestions.getRange(), list));
        }
    }

    private static List<String> suggestions(String type) {
        return switch (type) {
            case "player", "offlineplayer", "commandsender", "entity" ->
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            case "world" -> Bukkit.getWorlds().stream().map(World::getName).toList();
            case "gamemode" -> java.util.Arrays.stream(GameMode.values())
                    .map(mode -> mode.name().toLowerCase(Locale.ROOT)).toList();
            case "boolean" -> List.of("true", "false");
            default -> List.of();
        };
    }
}
