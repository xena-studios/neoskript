/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
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
 * What the reload says (Skript's "reloading" and "reloaded" messages, and the script's errors and
 * warnings) goes to the console and the recipients, or to the players with the permission if there are
 * none, in Skript's wording and colours.
 */
final class AutoReload {

    /** How often the file is checked, in ticks: Skript's 2 seconds. */
    static final long PERIOD = 20 * 2;

    private static final String PREFIX = "<gray>[<gold>Skript<gray>] <reset>";

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

    /** The message before the reload. */
    String reloading() {
        return PREFIX + fixCapitalization("Automatically reloading <gold>" + what() + "<reset>...");
    }

    /** The message after the reload, which had {@code errors} errors and took {@code millis}. */
    String reloaded(int errors, long millis) {
        if (errors == 0) {
            return PREFIX + fixCapitalization("<lime>Successfully automatically reloaded <gold>" + what() + "<lime>. <gray>(<gold>"
                    + millis + "ms<gray>)");
        }
        String message = pluralize("<light red>Encountered <gold>" + errors + " <light red>error¦¦s¦ while automatically"
                + " reloading <gold>" + what() + "<light red>! <gray>(<gold>" + millis + "ms<gray>)");
        // As an error with no line, Skript's error details wrap it.
        return "<light red>    " + PREFIX + fixCapitalization(message) + "<reset>\n";
    }

    /** {@code log.auto reload.script}: the script's name. */
    private String what() {
        return pluralize("<gold>" + script + "<reset>");
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

    /** Skript's PluralizingArgsMessage.format: {@code ¦singular¦plural¦}, by the number before it. */
    static String pluralize(String s) {
        StringBuilder b = new StringBuilder();
        int last = 0;
        boolean plural = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ('0' <= c && c <= '9') {
                if (Math.abs(numberAfter(s, i)) != 1) {
                    plural = true;
                }
            } else if (c == '¦') {
                int c1 = s.indexOf('¦', i + 1);
                if (c1 == -1) {
                    break;
                }
                int c2 = s.indexOf('¦', c1 + 1);
                if (c2 == -1) {
                    break;
                }
                b.append(s, last, i);
                b.append(plural ? s.substring(c1 + 1, c2) : s.substring(i + 1, c1));
                i = c2;
                last = c2 + 1;
                plural = false;
            }
        }
        if (last == 0) {
            return s;
        }
        b.append(s.substring(last));
        return b.toString();
    }

    /**
     * Skript's StringUtils.numberAfter: the number starting at {@code index} (after any whitespace) and
     * followed by whitespace or the end, or -1 if there's none.
     */
    private static double numberAfter(String s, int index) {
        boolean stillWhitespace = true;
        boolean hasDot = false;
        int d1 = -1;
        int d2 = -1;
        for (int i = index; i < s.length(); i++) {
            char c = s.charAt(i);
            if (('0' <= c && c <= '9') || c == '.') {
                if (c == '.') {
                    if (hasDot) {
                        break;
                    }
                    hasDot = true;
                }
                if (d1 == -1) {
                    d1 = d2 = i;
                } else {
                    d1++;
                }
                stillWhitespace = false;
            } else if (Character.isWhitespace(c) && stillWhitespace) {
                continue;
            } else {
                break;
            }
        }
        if (d1 == -1 || s.charAt(Math.min(d1, d2)) == '.') {
            return -1;
        }
        if (d1 + 1 > 0 && d1 + 1 < s.length() && !Character.isWhitespace(s.charAt(d1 + 1))) {
            return -1;
        }
        return Double.parseDouble(s.substring(Math.min(d1, d2), Math.max(d1, d2) + 1));
    }

    /** Skript's StringUtils.fixCapitalization: capitalises the start of each sentence. */
    static String fixCapitalization(String string) {
        char[] s = string.toCharArray();
        int c = 0;
        while (c != -1) {
            while (c < s.length && (s[c] == '.' || s[c] == '!' || s[c] == '?' || Character.isWhitespace(s[c]))) {
                c++;
            }
            if (c == s.length) {
                return new String(s);
            }
            if (c == 0 || Character.isWhitespace(s[c - 1])) {
                s[c] = Character.toUpperCase(s[c]);
            }
            c = indexOf(s, c + 1);
        }
        return new String(s);
    }

    private static int indexOf(char[] s, int start) {
        for (int i = start; i < s.length; i++) {
            if (s[i] == '.' || s[i] == '!' || s[i] == '?') {
                return i;
            }
        }
        return -1;
    }
}
