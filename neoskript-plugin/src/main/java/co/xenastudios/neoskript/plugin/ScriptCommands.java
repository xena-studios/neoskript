/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.compiler.CompiledCommand;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/**
 * Registers the loaded scripts' commands with the server, replacing the previous ones on reload.
 *
 * <p>They go in the server's command map, under the {@code skript} namespace as in Skript, which Paper
 * turns into Brigadier commands. Unlike Paper's command lifecycle, the command map can change while the
 * server runs, so reloading a script updates its commands; players are then sent the new command list.
 */
final class ScriptCommands {

    private static final String NAMESPACE = "skript";

    private final Plugin plugin;
    private final Logger logger;
    private final Scheduler scheduler;
    private final List<ScriptCommand> registered = new ArrayList<>();
    /** When each player last used each command, by command name, kept across reloads. */
    private final Map<String, Map<UUID, Long>> lastUses = new ConcurrentHashMap<>();

    ScriptCommands(Plugin plugin, Logger logger, Scheduler scheduler) {
        this.plugin = plugin;
        this.logger = logger;
        this.scheduler = scheduler;
        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            void suggestions(com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent event) {
                ScriptCommand.addTooltips(event);
            }
        }, plugin);
    }

    /** Registers {@code commands}, and only those, in place of the ones registered before. */
    void update(Collection<CompiledCommand> commands) {
        CommandMap map = plugin.getServer().getCommandMap();
        unregister(map);
        for (CompiledCommand compiled : commands) {
            Map<UUID, Long> uses = lastUses.computeIfAbsent(compiled.name().toLowerCase(Locale.ROOT),
                    k -> new ConcurrentHashMap<>());
            ScriptCommand command = new ScriptCommand(plugin, logger, compiled, uses);
            map.register(compiled.name(), NAMESPACE, command);
            registered.add(command);
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            scheduler.runLater(player, 1, player::updateCommands);
        }
    }

    /** Removes every script command, as when the plugin is disabled. */
    void unregister(CommandMap map) {
        Map<String, Command> known = map.getKnownCommands();
        for (ScriptCommand command : registered) {
            command.unregister(map);
            // Paper's map of known commands can't be iterated, so each label is looked up.
            List<String> labels = new ArrayList<>();
            labels.add(command.getName());
            labels.addAll(command.getAliases());
            for (String label : labels) {
                for (String key : List.of(label.toLowerCase(Locale.ROOT), NAMESPACE + ":" + label.toLowerCase(Locale.ROOT))) {
                    if (known.get(key) == command) {
                        known.remove(key);
                    }
                }
            }
        }
        registered.clear();
    }
}
