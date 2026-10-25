/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import java.util.Map;

/**
 * What {@code /skript} says: the {@code skript command} messages of Skript's english.lang, under the
 * same keys (without the {@code skript command.} start), so admins see what Skript shows them.
 */
final class SkriptCommandMessages {

    private SkriptCommandMessages() {}

    /** Skript's {@code skript.prefix}. */
    static final String PREFIX = "<gray>[<gold>Skript<gray>] <reset>";

    /** The message under {@code key}, or the key if there's none, as Skript's Language.get. */
    static String get(String key) {
        String value = MESSAGES.get(key);
        return value == null ? key : value;
    }

    /** Whether there's a message under {@code key}. */
    static boolean has(String key) {
        return MESSAGES.containsKey(key);
    }

    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("usage", "Usage:"),
            Map.entry("help.description", "Skript's main command"),
            Map.entry("help.help", "Prints this help message. Use '/skript reload/enable/disable/update' to get more info"),
            Map.entry("help.reload.description", "Reloads a specific script, all scripts, the config, or everything"),
            Map.entry("help.reload.all", "Reloads the config, all aliases configs and all scripts"),
            Map.entry("help.reload.config", "Reloads the main config"),
            Map.entry("help.reload.aliases", "Reloads the aliases configs (aliases-english.zip or plugin jar)"),
            Map.entry("help.reload.scripts", "Reloads all scripts"),
            Map.entry("help.reload.last", "Reloads the last script that was reloaded"),
            Map.entry("help.reload.<script>", "Reloads a specific script or a folder of scripts"),
            Map.entry("help.enable.description", "Enables all scripts or a specific one"),
            Map.entry("help.enable.all", "Enables all scripts"),
            Map.entry("help.enable.<script>", "Enables a specific script or a folder of scripts"),
            Map.entry("help.disable.description", "Disables all scripts or a specific one"),
            Map.entry("help.disable.all", "Disables all scripts"),
            Map.entry("help.disable.<script>", "Disables a specific script or a folder of scripts"),
            Map.entry("help.update.description", "Check for updates or read the changelog"),
            Map.entry("help.update.check", "Checks for a new version"),
            Map.entry("help.update.changes", "Lists all changes since the current version"),
            Map.entry("help.restore", "Stores loaded scripts back as files. This is useful for restoring loaded scripts that have had their files deleted."),
            Map.entry("help.list", "Lists all enabled and disabled scripts"),
            Map.entry("help.info", "Prints a message with links to Skript's aliases and documentation"),
            Map.entry("help.gen-docs", "Generates documentation using docs/templates in plugin folder"),
            Map.entry("help.test", "Used for running internal Skript tests"),

            Map.entry("invalid script", "Can't find the script <grey>'<gold>%s<grey>'<red> in the scripts folder!"),
            Map.entry("invalid folder", "Can't find the folder <grey>'<gold>%s<grey>'<red> in the scripts folder!"),

            Map.entry("reload.no last file", "<light red>No scripts have been reloaded yet. You must reload a script first."),
            Map.entry("reload.reloading", "Reloading <gold>%s<reset>..."),
            Map.entry("reload.reloaded", "<lime>Successfully reloaded <gold>%s<lime>. <gray>(<gold>%2$sms<gray>)"),
            Map.entry("reload.player reload", "<lime>%s <reset>is reloading <gold>%s<reset>..."),
            Map.entry("reload.error", "<light red>Encountered <gold>%2$s <light red>error¦¦s¦ while reloading <gold>%1$s<light red>! <gray>(<gold>%3$sms<gray>)"),
            Map.entry("reload.script disabled", "<gold>%s<reset> is currently disabled. Use <gray>/<gold>skript <cyan>enable <red>%s<reset> to enable it."),
            Map.entry("reload.warning details", "<yellow>    %s<reset>\n"),
            Map.entry("reload.config, aliases and scripts", "the config, aliases and all scripts"),
            Map.entry("reload.scripts", "all scripts"),
            Map.entry("reload.main config", "the main configuration"),
            Map.entry("reload.aliases", "the aliases"),
            Map.entry("reload.script", "<gold>%s<reset>"),
            Map.entry("reload.scripts in folder", "all scripts in <gold>%s<reset>"),
            Map.entry("reload.x scripts in folder success", "<gold>%2$s <lime>script¦¦s¦ in <gold>%1$s<reset>"),
            Map.entry("reload.x scripts in folder error", "<gold>%2$s <light red>script¦¦s¦ in <gold>%1$s<reset>"),
            Map.entry("reload.empty folder", "<gold>%s<reset> does not contain any enabled scripts."),

            Map.entry("enable.all.enabling", "Enabling all disabled scripts..."),
            Map.entry("enable.all.enabled", "Successfully enabled & parsed all previously disabled scripts."),
            Map.entry("enable.all.error", "<light red>Encountered <gold>%s <light red>error¦¦s¦ while parsing disabled scripts!"),
            Map.entry("enable.all.io error", "<light red>Could not load one or more scripts - some scripts might have been renamed already and will be enabled when the server restarts: <gold>%s"),
            Map.entry("enable.single.already enabled", "<gold>%s<reset> is already enabled! Use <gray>/<gold>skript <cyan>reload <red>%s<reset> to reload it if it was changed."),
            Map.entry("enable.single.enabling", "Enabling <gold>%s<reset>..."),
            Map.entry("enable.single.enabled", "Successfully enabled & parsed <gold>%s<reset>."),
            Map.entry("enable.single.error", "<light red>Encountered <gold>%2$s <light red>error¦¦s¦ while parsing <gold>%1$s<light red>!"),
            Map.entry("enable.single.io error", "<light red>Could not enable <gold>%s<light red>: <gold>%s"),
            Map.entry("enable.folder.empty", "<gold>%s<reset> does not contain any disabled scripts."),
            Map.entry("enable.folder.enabling", "Enabling <gold>%2$s <reset>script¦¦s¦ in <gold>%1$s<reset>..."),
            Map.entry("enable.folder.enabled", "Successfully enabled & parsed <gold>%2$s<reset> previously disabled scripts in <gold>%1$s<reset>."),
            Map.entry("enable.folder.error", "<light red>Encountered <gold>%2$s <light red>error¦¦s¦ while parsing scripts in <gold>%1$s<light red>!"),
            Map.entry("enable.folder.io error", "<light red>Error while enabling one or more scripts in <gold>%s <light red>(some scripts might get enabled when the server restarts): <gold>%s"),

            Map.entry("disable.all.disabled", "Successfully disabled all scripts."),
            Map.entry("disable.all.io error", "<light red>Could not rename one or more scripts - some scripts might have been renamed already and will be disabled when the server restarts: <gold>%s"),
            Map.entry("disable.single.already disabled", "<gold>%s<reset> is already disabled!"),
            Map.entry("disable.single.disabled", "Successfully disabled <gold>%s<reset>."),
            Map.entry("disable.single.io error", "<light red>Could not rename <gold>%s<light red>, it will be enabled again when you restart the server: <gold>%s"),
            Map.entry("disable.folder.empty", "<gold>%s<reset> does not contain any enabled scripts."),
            Map.entry("disable.folder.disabled", "Successfully disabled <gold>%2$s<reset> script(s) in <gold>%1$s<reset>."),
            Map.entry("disable.folder.io error", "<light red>Could not disable one or more scripts in <gold>%s <light red>(some scripts might get disabled when the server restarts): <gold>%s"),

            Map.entry("list.enabled.header", "<white>Enabled scripts:"),
            Map.entry("list.enabled.element", "<lime green>  %s"),
            Map.entry("list.disabled.header", "<white>Disabled scripts:"),
            Map.entry("list.disabled.element", "<light red>  %s"),

            Map.entry("info.aliases", "Skript's aliases can be found here: <aqua>https://github.com/SkriptLang/skript-aliases"),
            Map.entry("info.documentation", "Skript's documentation can be found here: <aqua>https://docs.skriptlang.org/"),
            Map.entry("info.tutorials", "Skript's tutorials can be found here: <aqua>https://docs.skriptlang.org/tutorials"),
            // NeoSkript's own: its version, where Skript shows Skript's.
            Map.entry("info.version", "NeoSkript Version: <aqua>%s"),
            Map.entry("info.server", "Server Version: <aqua>%s"),
            Map.entry("info.addons", "Installed Skript Addons: <aqua>%s"),
            Map.entry("info.dependencies", "Installed dependencies: <aqua>%s"),

            Map.entry("restore.restoring", "<white>Restoring scripts..."),
            Map.entry("restore.restored", "<white>Restored all scripts and saved them inside the <gold>%s <white>directory"),
            Map.entry("restore.io error", "<light red>Could not restore scripts: %s"),

            // NeoSkript's own, for what it can't do yet.
            Map.entry("update.unavailable", "NeoSkript can't check for updates yet. New versions are released at <aqua>https://github.com/xena-studios/neoskript/releases"),
            Map.entry("gen-docs.unavailable", "NeoSkript can't generate documentation yet."));
}
