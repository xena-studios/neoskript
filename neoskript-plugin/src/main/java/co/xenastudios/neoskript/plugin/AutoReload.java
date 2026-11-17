/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.frontend.parse.ParsedStructure;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.paper.TextComponentParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

/**
 * A loaded script's {@code auto reload} structure (Skript's StructAutoReload): every 2 seconds the script
 * manager checks whether its file changed since it was last loaded and, if so, reloads just that script.
 * What the reload says (that it's reloading, the script's errors and warnings, and how it went) goes to the
 * console and the recipients, or to the players with the permission if there are none, in the same report
 * {@code /sk reload} gives.
 */
final class AutoReload {

    /** How often the file is checked, in ticks: Skript's 2 seconds. */
    static final long PERIOD = 20 * 2;

    /**
     * Skript's {@code skript.prefix}, for what keeps Skript's look because tools and tutorials read it: script
     * problems at load and runtime errors.
     */
    static final String SKRIPT_PREFIX = "<gray>[<gold>Skript<gray>] <reset>";

    private final String script;
    /** The recipients' names or UUIDs, in lower case. */
    private final Set<String> recipients = new HashSet<>();
    private final String permission;
    /** The file's last modified time when the script was last (re)loaded, to compare with the file's. */
    private long lastReload;
    /** What stops the checks, once started. */
    private Runnable stop = () -> {};

    AutoReload(String script, ParsedStructure.AutoReload structure, long lastReload) {
        this.script = script;
        structure.recipients().forEach(recipient -> recipients.add(recipient.toLowerCase(Locale.ENGLISH)));
        this.permission = structure.permission();
        this.lastReload = lastReload;
    }

    long lastReload() {
        return lastReload;
    }

    void lastReload(long lastReload) {
        this.lastReload = lastReload;
    }

    void started(Runnable stop) {
        this.stop = stop;
    }

    void stop() {
        stop.run();
    }

    /**
     * Who hears about a reload, as Skript works it out: the console, and the online recipients, or the online
     * players with the permission if there are no recipients.
     */
    List<CommandSender> audience() {
        List<CommandSender> senders = new ArrayList<>();
        senders.add(Bukkit.getConsoleSender());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (recipients.isEmpty()
                    ? player.hasPermission(permission)
                    : recipients.contains(player.getName().toLowerCase(Locale.ENGLISH))
                            || recipients.contains(player.getUniqueId().toString())) {
                senders.add(player);
            }
        }
        return senders;
    }

    /** A problem the reload found, as Skript's log entries show one. */
    static String describe(Diagnostic problem, SourceFile file) {
        String code = "";
        if (file != null && problem.line() >= 1 && problem.line() <= file.lines().size()) {
            code = file.lines().get(problem.line() - 1).strip();
        }
        TextComponentParser parser = TextComponentParser.instance();
        boolean error = problem.severity() == Severity.ERROR;
        String lineInfo = error ? "<light red><bold>Line %s:<gray> (%s)<reset>\n" : "<gold><bold>Line %s:<gray> (%s)<reset>\n";
        String details = error ? "<light red>    %s<reset>\n" : "<yellow>    %s<reset>\n";
        return String.format(lineInfo, problem.line(), problem.file()) + String.format(details, parser.escape(problem.message()))
                + String.format("<gold>    Line: <gray>%s<reset>\n <reset>", parser.escape(code));
    }

    /** Sends a formatted message, a line at a time to the console, as Skript's log redirection does. */
    static void send(List<CommandSender> audience, String message) {
        TextComponentParser parser = TextComponentParser.instance();
        for (CommandSender sender : audience) {
            if (sender instanceof ConsoleCommandSender) {
                for (String line : message.split("\n")) {
                    sender.sendMessage(parser.parse(line));
                }
            } else {
                sender.sendMessage(parser.parse(message));
            }
        }
    }
}
