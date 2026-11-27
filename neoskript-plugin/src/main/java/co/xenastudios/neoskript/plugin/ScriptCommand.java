/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.compiler.CommandCompletion;
import co.xenastudios.neoskript.compiler.CompiledCommand;
import co.xenastudios.neoskript.frontend.parse.CommandPattern;
import co.xenastudios.neoskript.paper.Messages;
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
        if (command.cooldown() != null && sender instanceof Player player && command.cooldownBypass() != null
                && player.hasPermission(command.cooldownBypass())) {
            // As Skript's, a cooldown that's bypassed is cleared, in case one was set.
            setLastUse(event, player.getUniqueId(), null);
        } else if (command.cooldown() != null && sender instanceof Player player) {
            long now = System.currentTimeMillis();
            UUID id = player.getUniqueId();
            previous = lastUse(event, id);
            if (previous != null && now - previous < command.cooldown().millis()) {
                event.cooldown(command.cooldown(), new Timespan(now - previous), command.cooldownBypass());
                String message = message(command.cooldownMessage(), event);
                send(sender, message == null
                        ? co.xenastudios.neoskript.runtime.Language.get("commands.cooldown message") : message);
                return;
            }
            event.cooldown(command.cooldown(), previous == null ? null : new Timespan(now - previous),
                    command.cooldownBypass());
            setLastUse(event, id, now);
            cooling = true;
            event.afterWait(cancel -> setLastUse(event, id, cancel ? null : now));
        }
        try {
            route.trigger().execute(new SimpleTriggerContext(event, sender instanceof Player ? sender : null));
        } catch (Throwable e) {
            if (!(e.getCause() instanceof CommandEvent.Failed)) {
                TriggerErrors.report(logger, e);
            }
        }
        if (event.failure() instanceof net.kyori.adventure.text.Component failure) {
            // Shown as the server shows a command's error, in red.
            sender.sendMessage(failure.colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.RED));
        }
        if (cooling && event.cooldownCancelled()) {
            setLastUse(event, ((Player) sender).getUniqueId(), previous);
        }
    }

    /** When a player last used the command, in milliseconds, from its cooldown storage if it has one. */
    private Long lastUse(CommandEvent event, UUID id) {
        CompiledCommand.CooldownStorage storage = command.cooldownStorage();
        if (storage == null) {
            return lastUses.get(id);
        }
        String name = storageName(storage, event);
        if (name == null) {
            return null;
        }
        Object value = storage.globals().get(name);
        if (value != null && !(value instanceof co.xenastudios.neoskript.runtime.Date)) {
            logger.warn("Variable {{}} was not a date! You may be using this variable elsewhere. This warning is"
                    + " letting you know that this variable is now overridden for the command storage.", name);
            return null;
        }
        return value == null ? null : ((co.xenastudios.neoskript.runtime.Date) value).millis();
    }

    /** Sets when a player last used the command, or with {@code null}, that they haven't. */
    private void setLastUse(CommandEvent event, UUID id, Long millis) {
        CompiledCommand.CooldownStorage storage = command.cooldownStorage();
        if (storage == null) {
            if (millis == null) {
                lastUses.remove(id);
            } else {
                lastUses.put(id, millis);
            }
            return;
        }
        String name = storageName(storage, event);
        if (name != null) {
            storage.globals().set(name, millis == null ? null : new co.xenastudios.neoskript.runtime.Date(millis));
        }
    }

    /** The name of the cooldown storage variable, as Skript takes it: without the brackets it's written in. */
    private String storageName(CompiledCommand.CooldownStorage storage, CommandEvent event) {
        String name = message(storage.name(), event);
        if (name == null) {
            logger.error("The cooldown storage variable defined is invalid! Cooldowns for this command will not work.");
            return null;
        }
        return name.startsWith("{") && name.length() >= 2 ? name.substring(1, name.length() - 1) : name;
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
        } catch (Throwable e) {
            TriggerErrors.report(logger, e);
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

    /** Skript's {@code enable legacy tab completion event compatibility}: on unless config.yml turns it off. */
    private static volatile boolean legacyTabCompletion = true;
    /**
     * The completions a script command just gave, on the thread asking, for {@link #keepOwnCompletions}: Bukkit
     * fires its TabCompleteEvent on that thread with them, after asking the command. Only JDK values, as
     * {@code [sender, what was typed, when, completions]}, so a value left behind (another plugin asking the command
     * map without an event) never holds NeoSkript's classes or a player after a reload, and it's only used for the same
     * sender and words within a second.
     */
    private static final ThreadLocal<Object[]> GIVEN = new ThreadLocal<>();

    /** How long the completions a command gave count for the event that follows. */
    private static final long GIVEN_FOR = 1_000_000_000L;

    static void legacyTabCompletion(boolean on) {
        legacyTabCompletion = on;
    }

    /**
     * With Skript's legacy tab completion compatibility off, other plugins' changes to a script command's
     * completions through Bukkit's TabCompleteEvent don't count, as in Skript, where script commands are Brigadier
     * commands that fire it only with the compatibility on. Runs after them, at the highest priority.
     */
    static void keepOwnCompletions(org.bukkit.event.server.TabCompleteEvent event) {
        Object[] given = GIVEN.get();
        GIVEN.remove();
        if (given == null || legacyTabCompletion || System.nanoTime() - (long) given[2] > GIVEN_FOR
                || !given[0].equals(senderKey(event.getSender()))) {
            return;
        }
        // Only for what the command was asked about: Bukkit may ask a command without firing the event.
        String buffer = event.getBuffer().startsWith("/") ? event.getBuffer().substring(1) : event.getBuffer();
        if (!buffer.equalsIgnoreCase((String) given[1])) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<String> completions = (List<String>) given[3];
        event.setCancelled(false);
        event.setCompletions(new ArrayList<>(completions));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        List<String> texts = completions(sender, alias, args);
        given(sender, alias, args, texts);
        return texts;
    }

    /** Notes the completions a script command gave on this thread, for {@link #keepOwnCompletions}. */
    static void given(CommandSender sender, String alias, String[] args, List<String> completions) {
        GIVEN.set(new Object[] {senderKey(sender), alias + " " + String.join(" ", args), System.nanoTime(),
                List.copyOf(completions)});
    }

    /** A sender, as text, so no sender is held: a player's UUID, else its name. */
    private static String senderKey(CommandSender sender) {
        return sender instanceof org.bukkit.entity.Entity entity ? entity.getUniqueId().toString() : sender.getName();
    }

    /** Whether completions are noted on this thread: for tests. */
    static boolean holdsCompletions() {
        return GIVEN.get() != null;
    }

    private List<String> completions(CommandSender sender, String alias, String[] args) {
        String label = alias.substring(alias.indexOf(':') + 1);
        List<CommandSuggestionEvent.Suggestion> suggestions;
        try {
            suggestions = CommandCompletion.complete(command, sender, sender instanceof Player ? sender : null, label,
                    args, route -> canExecute(route.executors(), sender)
                            && (route.permission() == null || sender.hasPermission(route.permission())),
                    ScriptCommand::suggestions);
        } catch (Throwable e) {
            TriggerErrors.report(logger, e);
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
