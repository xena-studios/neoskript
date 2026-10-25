/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.paper.EffectCommandEvent;
import co.xenastudios.neoskript.paper.TextComponentParser;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.slf4j.Logger;

/**
 * Skript's effect commands: with {@code effect-commands.enabled}, a console command or chat message starting
 * with the token ({@code !}) is an effect, run straight away, as Skript's Commands.handleEffectCommand.
 * Players need the permission {@code skript.effectcommands}, or to be an op if ops are allowed; the console
 * can always use them. The command or chat message is cancelled, and the sender is told what ran, or its errors.
 *
 * <p>Chat is async, so a player's effect runs on the player's thread (their region's on Folia); the console's
 * runs on the global thread, where its commands come.
 */
final class EffectCommands implements Listener {

    /** Skript's permission for effect commands. */
    static final String PERMISSION = "skript.effectcommands";

    /**
     * The settings, as Skript's {@code enable effect commands}, {@code effect command token},
     * {@code allow ops to use effect commands} and {@code log effect commands}.
     */
    record Settings(boolean enabled, String token, boolean allowOps, boolean log) {

        static final Settings DEFAULT = new Settings(false, "!", false, false);

        /** The {@code effect-commands} section of config.yml, or the defaults if there's none. */
        static Settings read(ConfigurationSection config) {
            if (config == null) {
                return DEFAULT;
            }
            String token = config.getString("token", DEFAULT.token());
            return new Settings(config.getBoolean("enabled", DEFAULT.enabled()),
                    token == null || token.isEmpty() ? DEFAULT.token() : token,
                    config.getBoolean("allow-ops", DEFAULT.allowOps()), config.getBoolean("log", DEFAULT.log()));
        }
    }

    private final ScriptManager scripts;
    private final Scheduler scheduler;
    private final Logger logger;
    /** As in Skript's test mode, effect commands are always on, for everyone. */
    private final boolean testing;
    private volatile Settings settings;

    EffectCommands(ScriptManager scripts, Scheduler scheduler, Logger logger, boolean testing, Settings settings) {
        this.scripts = scripts;
        this.scheduler = scheduler;
        this.logger = logger;
        this.testing = testing;
        this.settings = settings;
    }

    /** Uses new settings, for {@code /skript reload config}. */
    void settings(Settings settings) {
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerCommand(ServerCommandEvent event) {
        if (event.getCommand().isEmpty() || event.isCancelled()) {
            return;
        }
        Settings current = settings;
        if ((testing || current.enabled()) && event.getCommand().startsWith(current.token())
                && permitted(event.getSender(), current)) {
            event.setCancelled(true);
            CommandSender sender = event.getSender();
            String command = event.getCommand();
            if (Bukkit.isGlobalTickThread()) {
                handle(sender, command, current);
            } else {
                scheduler.runGlobal(() -> handle(sender, command, current));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        Settings current = settings;
        String message = event.signedMessage().message();
        if ((!current.enabled() && !testing) || !message.startsWith(current.token())) {
            return;
        }
        Player player = event.getPlayer();
        if (!permitted(player, current)) {
            return;
        }
        event.setCancelled(true);
        if (!event.isAsynchronous()) {
            handle(player, message, current);
        } else {
            // On the player's thread, as Skript runs it on the main thread.
            scheduler.runLater(player, 0, () -> handle(player, message, current));
        }
    }

    /** Whether {@code sender} can use effect commands, as Skript checks. */
    private boolean permitted(CommandSender sender, Settings current) {
        return testing || sender instanceof ConsoleCommandSender || sender.hasPermission(PERMISSION)
                || current.allowOps() && sender.isOp();
    }

    /** Parses and runs an effect command: {@code line} is what was typed, starting with the token. */
    private void handle(CommandSender sender, String line, Settings current) {
        TextComponentParser parser = TextComponentParser.instance();
        String command = line.substring(current.token().length()).trim();
        try {
            // Called on the Bukkit API for other plugins, which can change the effect or cancel it.
            EffectCommandEvent event = new EffectCommandEvent(sender, command);
            Bukkit.getPluginManager().callEvent(event);
            command = event.getCommand();
            ScriptCompiler.CompiledLine effect = scripts.compileEffect(command, EffectCommandEvent.class);
            if (effect.trigger() == null) {
                if (sender == Bukkit.getConsoleSender()) {
                    logger.error("Error in: {}", command);
                } else {
                    sender.sendMessage(parser.parse("<red>Error in: <gray>" + parser.escape(command)));
                }
                // Skript's log entries without a line: the error as 'error details' show it.
                List<String> errors = effect.errors().stream().map(error -> "<light red>    " + error + "<reset>\n")
                        .toList();
                for (String error : errors.isEmpty() ? List.of("(No specific information is available)") : errors) {
                    AutoReload.send(List.of(sender), error);
                }
                return;
            }
            if (event.isCancelled()) {
                sender.sendMessage(parser.parse("<red>Your effect command '" + parser.escape(command) + "' was cancelled."));
                return;
            }
            sender.sendMessage(parser.parse("<gray>Executing '" + parser.escape(command) + "'"));
            if (current.log() && !(sender instanceof ConsoleCommandSender)) {
                logger.info("{} issued effect command: {}", sender.getName(), command);
            }
            // A wait carries on on the sender's thread.
            try {
                effect.trigger().execute(new SimpleTriggerContext(event, sender instanceof Entity entity ? entity : null));
            } catch (ScriptRuntimeException e) {
                logger.error("Error in {}, line {}: {}", e.script(), e.line(), e.getCause().toString(), e.getCause());
            }
        } catch (RuntimeException e) {
            logger.error("Unexpected error while executing effect command '{}' by '{}'", command,
                    sender.getName(), e);
            sender.sendRichMessage("<red>An internal error occurred while executing this effect. "
                    + "Please refer to the server log for details.");
        }
    }
}
