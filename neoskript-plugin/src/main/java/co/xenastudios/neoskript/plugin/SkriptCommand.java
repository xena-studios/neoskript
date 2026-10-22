/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.ConsoleCommandSender;

/**
 * {@code /skript} (also {@code /sk} and {@code /neoskript}), which NeoSkript takes over from Skript (D10).
 * Replies use Skript's wording, so existing habits and tools keep working.
 */
final class SkriptCommand implements TabExecutor {

    private final ScriptManager scripts;
    private final String version;

    SkriptCommand(ScriptManager scripts, String version) {
        this.scripts = scripts;
        this.version = version;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender, args.length > 1 ? String.join(" ", List.of(args).subList(1, args.length)) : "all");
            case "info" -> sender.sendMessage(Component.text("NeoSkript " + version + ", a fast, drop-in replacement"
                    + " for Skript. https://github.com/xena-studios/neoskript", NamedTextColor.AQUA));
            default -> help(sender, label);
        }
        return true;
    }

    private void reload(CommandSender sender, String what) {
        String target = what.toLowerCase(Locale.ROOT);
        String only = null;
        String description;
        if (target.equals("all") || target.equals("scripts")) {
            description = "all scripts";
        } else {
            only = what.endsWith(".sk") ? what : what + ".sk";
            if (!scripts.exists(only)) {
                sender.sendMessage(Component.text("Can't find the script '" + what + "'", NamedTextColor.RED));
                return;
            }
            description = only;
        }
        sender.sendMessage(Component.text("Reloading " + description + "...", NamedTextColor.GRAY));
        boolean console = sender instanceof ConsoleCommandSender;
        try {
            ScriptManager.Outcome outcome = scripts.reload(only, problem -> {
                if (!console) {
                    sender.sendMessage(Component.text(problem, NamedTextColor.RED));
                }
            });
            if (outcome.errors() == 0) {
                sender.sendMessage(Component.text("Successfully reloaded " + description + ". (" + outcome.millis() + "ms)",
                        NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Encountered " + outcome.errors() + (outcome.errors() == 1 ? " error" : " errors")
                        + " while reloading " + description + "! (" + outcome.millis() + "ms)", NamedTextColor.RED));
            }
        } catch (IOException e) {
            sender.sendMessage(Component.text("Couldn't read the scripts: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private static void help(CommandSender sender, String label) {
        sender.sendMessage(Component.text("/" + label + " reload [all|scripts|<script>]", NamedTextColor.AQUA)
                .append(Component.text(" - reloads scripts. A script with errors keeps running its last version.",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " info", NamedTextColor.AQUA)
                .append(Component.text(" - shows NeoSkript's version", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "info", "help").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            return List.of("all", "scripts").stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
