/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static co.xenastudios.neoskript.plugin.Messages.component;
import static co.xenastudios.neoskript.plugin.Messages.send;
import static co.xenastudios.neoskript.plugin.Messages.text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * {@code /skript} (also {@code /sk}, {@code /neoskript} and {@code /ns}), which NeoSkript takes over from Skript
 * (D10). It has Skript's subcommands and arguments (reload, enable, disable, list, info, update, restore, help,
 * and gen-docs and test), so habits and console automation keep working, with NeoSkript's own messages in the
 * theme of config.yml.
 *
 * <p>Where NeoSkript differs: a script with errors keeps running its last version when reloaded (D12), so a
 * reload never unloads first; {@code reload config} reloads {@code config.yml} (NeoSkript has no
 * {@code config.sk}); {@code reload aliases} also reloads the scripts the changed aliases may change, which keep
 * their last version if they now have errors; and {@code gen-docs} points to the syntax reference on neoskript.dev.
 */
final class SkriptCommand implements TabExecutor {

    /**
     * A subcommand: its names (the first is its name in help), its arguments for help and their descriptions,
     * and whether one must be given.
     */
    private record Sub(List<String> aliases, String description, Map<String, String> args, boolean required) {
        Sub(String name, String description) {
            this(List.of(name), description, Map.of(), false);
        }

        String name() {
            return aliases.getFirst();
        }

        String usage() {
            if (args.isEmpty()) {
                return "";
            }
            String choices = String.join("|", args.keySet()).replace("<script>", "script");
            return required ? " <" + choices + ">" : " [" + choices + "]";
        }
    }

    private static Map<String, String> args(String... pairs) {
        Map<String, String> args = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            args.put(pairs[i], pairs[i + 1]);
        }
        return args;
    }

    private static final String SCRIPT_ARG = "A script, or a folder ending in /";

    // In Skript's order.
    private static final List<Sub> SUBS = List.of(
            new Sub("help", "Shows this list"),
            new Sub(List.of("reload"), "Reloads scripts, the config, or both", args(
                    "all", "The config, the aliases and every script",
                    "scripts", "Every script",
                    "config", "config.yml",
                    "aliases", "The alias files, and the scripts that use them",
                    "last", "The script you reloaded last",
                    "<script>", SCRIPT_ARG), true),
            // Skript fails with an internal error for these without an argument; NeoSkript shows their help.
            new Sub(List.of("enable"), "Enables disabled scripts", args(
                    "all", "Every disabled script",
                    "<script>", SCRIPT_ARG), true),
            new Sub(List.of("disable"), "Disables scripts, until they're enabled", args(
                    "all", "Every script",
                    "<script>", SCRIPT_ARG), true),
            new Sub(List.of("list", "show"), "Lists the enabled and disabled scripts", Map.of(), false),
            new Sub("info", "Shows the version, the server, addons and links"),
            new Sub(List.of("update"), "Checks Modrinth for a newer NeoSkript", args(
                    "check", "Checks for a newer version",
                    "changes", "Links to what changed in each version"), false),
            new Sub(List.of("restore", "dump"), "Writes the loaded scripts back to files, for scripts whose files were deleted",
                    Map.of(), false));

    /** Skript has {@code gen-docs} and {@code test} too, but doesn't list them. */
    private static final List<Sub> HIDDEN = List.of(
            new Sub("gen-docs", "Points to the syntax reference"),
            new Sub("test", "Says how to run tests"));

    private final NeoSkriptPlugin plugin;
    private final ScriptManager scripts;
    /** What {@code /skript reload last} reloads. */
    private String lastReloaded;

    SkriptCommand(NeoSkriptPlugin plugin, ScriptManager scripts) {
        this.plugin = plugin;
        this.scripts = scripts;
    }

    private static Sub find(String alias) {
        for (List<Sub> subs : List.of(SUBS, HIDDEN)) {
            for (Sub sub : subs) {
                for (String name : sub.aliases()) {
                    if (name.equalsIgnoreCase(alias)) {
                        return sub;
                    }
                }
            }
        }
        return SUBS.getFirst();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // "/neoskript:sk" is "/sk" in what it suggests.
        String name = label.substring(label.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        Sub sub = find(args.length > 0 ? args[0] : "help");
        if (args.length == 1 && sub.required()) {
            usage(sender, name, sub);
            return true;
        }
        switch (sub.name()) {
            case "reload" -> onGlobalThread(() -> reload(sender, name, args));
            case "enable" -> onGlobalThread(() -> enable(sender, name, args));
            case "disable" -> onGlobalThread(() -> disable(sender, args));
            case "list" -> list(sender, name);
            case "info" -> info(sender);
            case "update" -> update(sender, args.length > 1 ? args[1] : "check");
            case "restore" -> restore(sender);
            case "gen-docs" -> send(sender, Messages.GEN_DOCS, component("link", Messages.link(sender, "neoskript.dev/syntax", Messages.SYNTAX)));
            case "test" -> send(sender, Messages.TEST);
            default -> help(sender, name);
        }
        return true;
    }

    /**
     * Runs {@code task} on the global region's thread, where scripts load: straight away from the console
     * (and on Paper's main thread), or on the next tick from elsewhere, such as a Folia player's region.
     */
    private void onGlobalThread(Runnable task) {
        if (Bukkit.isGlobalTickThread()) {
            task.run();
        } else {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        }
    }

    // --- help ---

    private void help(CommandSender sender, String label) {
        send(sender, Messages.HELP_HEADER, text("version", plugin.getPluginMeta().getVersion()),
                component("hint", Messages.chat(sender) ? Messages.HELP_HINT_CHAT.render() : Component.empty()));
        for (Sub sub : SUBS) {
            String typed = "/" + label + " " + sub.name();
            Component command = Messages.command(typed, typed + " ")
                    .append(Component.text(sub.usage() + (sub.aliases().size() > 1
                            ? " (or " + String.join(", ", sub.aliases().subList(1, sub.aliases().size())) + ")" : "")));
            send(sender, Messages.HELP_LINE, component("command", command), text("description", sub.description()));
        }
    }

    /** A subcommand's arguments, when it needs one and none was given. */
    private static void usage(CommandSender sender, String label, Sub sub) {
        String typed = "/" + label + " " + sub.name();
        send(sender, Messages.USAGE, component("command", Messages.command(typed + sub.usage(), typed + " ")));
        for (Map.Entry<String, String> arg : sub.args().entrySet()) {
            send(sender, Messages.USAGE_LINE, text("arg", arg.getKey()), text("description", arg.getValue()));
        }
    }

    // --- reload ---

    /** Who hears about a reload: whoever reloaded, and the players with {@code skript.reloadnotify}. */
    private static List<CommandSender> reloadRecipients(CommandSender sender) {
        Set<CommandSender> recipients = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("skript.reloadnotify")) {
                recipients.add(player);
            }
        }
        recipients.add(sender);
        return List.copyOf(recipients);
    }

    private void reload(CommandSender sender, String label, String[] args) {
        long start = System.nanoTime();
        List<CommandSender> recipients = reloadRecipients(sender);
        switch (args[1].toLowerCase(Locale.ENGLISH)) {
            case "all" -> {
                reloading(sender, recipients, Component.text("the config, aliases and all scripts"));
                boolean config = reloadConfig(recipients);
                ScriptReport report = new ScriptReport(label);
                boolean aliases = reloadAliases(report);
                ScriptManager.Outcome outcome = load(sender, recipients, null, report);
                if (outcome != null) {
                    Component what = Component.text(config && aliases ? "the config, aliases and "
                                    : config ? "the config and " : aliases ? "the aliases and " : "")
                            .append(countOf(outcome.scripts(), "script"));
                    report.send(recipients, "Reloaded", what, millis(start));
                }
            }
            case "scripts" -> {
                reloading(sender, recipients, Component.text("all scripts"));
                ScriptReport report = new ScriptReport(label);
                ScriptManager.Outcome outcome = load(sender, recipients, null, report);
                if (outcome != null) {
                    report.send(recipients, "Reloaded", countOf(outcome.scripts(), "script"), millis(start));
                }
            }
            case "config" -> {
                reloading(sender, recipients, Component.text("the config"));
                if (reloadConfig(recipients)) {
                    send(recipients, Messages.DONE, text("done", "Reloaded"), text("what", "the config"),
                            text("ms", millis(start)));
                }
            }
            case "aliases" -> {
                reloading(sender, recipients, Component.text("the aliases"));
                ScriptReport report = new ScriptReport(label);
                Set<String> changed;
                try {
                    changed = plugin.aliases().reload(plugin.settings().aliases().loadDefaults(), report.aliases());
                } catch (RuntimeException e) {
                    plugin.getSLF4JLogger().error("Couldn't reload the aliases", e);
                    send(sender, Messages.READ_FAILED, text("reason", String.valueOf(e.getMessage())));
                    return;
                }
                // Then the scripts that use the aliases that changed, or that had errors the new aliases may fix.
                // A script that now has errors keeps running its last version (D12).
                Predicate<String> affected;
                try {
                    affected = scripts.affectedBy(changed);
                } catch (IOException e) {
                    affected = null;
                }
                ScriptManager.Outcome outcome = load(sender, recipients, affected, report);
                if (outcome != null) {
                    Component what = Component.text("the aliases");
                    if (outcome.scripts() > 0) {
                        what = what.append(Component.text(" and ")).append(countOf(outcome.scripts(), "script"));
                    }
                    report.send(recipients, "Reloaded", what, millis(start));
                }
            }
            default -> {
                String scriptName;
                if (args[1].equalsIgnoreCase("last")) {
                    if (lastReloaded == null) {
                        send(sender, Messages.NO_LAST);
                        return;
                    }
                    scriptName = lastReloaded;
                } else {
                    scriptName = join(args);
                    lastReloaded = scriptName;
                }
                Path file = find(sender, scriptName);
                if (file == null) {
                    return;
                }
                if (Files.isDirectory(file)) {
                    String folder = scripts.name(file) + "/";
                    reloading(sender, recipients, Component.text("the scripts in ").append(Messages.highlight(folder)));
                    ScriptReport report = new ScriptReport(label);
                    ScriptManager.Outcome outcome = load(sender, recipients, inFolder(file), report);
                    if (outcome == null) {
                        return;
                    }
                    if (outcome.scripts() == 0) {
                        send(sender, Messages.FOLDER_EMPTY, text("name", folder));
                        return;
                    }
                    report.send(recipients, "Reloaded", countOf(outcome.scripts(), "script")
                            .append(Component.text(" in ")).append(Messages.highlight(folder)), millis(start));
                } else {
                    String name = scripts.name(file);
                    if (ScriptFiles.disabled(file)) {
                        String enable = "/" + label + " enable " + join(args);
                        send(sender, Messages.RELOAD_DISABLED, text("name", enabledName(name)),
                                component("command", Messages.command(enable)));
                        return;
                    }
                    reloading(sender, recipients, Messages.highlight(name));
                    ScriptReport report = new ScriptReport(label);
                    if (load(sender, recipients, name::equals, report) != null) {
                        report.send(recipients, "Reloaded", Messages.highlight(name), millis(start));
                    }
                }
            }
        }
    }

    /** Tells whoever reloaded what's reloading, and the others who's reloading it. */
    private static void reloading(CommandSender sender, List<CommandSender> recipients, Component what) {
        send(sender, Messages.RELOADING, text("doing", "Reloading"), component("what", what));
        for (CommandSender recipient : recipients) {
            if (recipient != sender) {
                send(recipient, Messages.OTHER_RELOADING, text("player", sender.getName()), component("what", what));
            }
        }
    }

    /** Reloads config.yml and tells the recipients what went wrong, if anything. Returns whether it was read. */
    private boolean reloadConfig(List<CommandSender> recipients) {
        SettingsReloader.Outcome result = plugin.reloadSettings();
        if (result.failure() != null) {
            send(recipients, Messages.CONFIG_FAILED, text("reason", result.failure()));
            return false;
        }
        if (result.warnings() > 0) {
            send(recipients, Messages.CONFIG_WARNINGS, text("settings", Messages.count(result.warnings(), "setting")));
        }
        if (!result.restartOnly().isEmpty()) {
            send(recipients, Messages.CONFIG_RESTART, text("settings", String.join(", ", result.restartOnly())),
                    text("verb", result.restartOnly().size() == 1 ? "applies" : "apply"));
        }
        return true;
    }

    /** Reloads the aliases for {@code /sk reload all}, with their problems in {@code report}. Returns whether it could. */
    private boolean reloadAliases(ScriptReport report) {
        try {
            plugin.aliases().reload(plugin.settings().aliases().loadDefaults(), report.aliases());
            return true;
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().error("Couldn't reload the aliases", e);
            return false;
        }
    }

    /** Loads the scripts {@code scope} accepts (all if {@code null}), or returns {@code null} if they couldn't be read. */
    private ScriptManager.Outcome load(CommandSender sender, List<CommandSender> recipients, Predicate<String> scope,
            ScriptReport report) {
        try {
            ScriptManager.Outcome outcome = scripts.reload(scope, report);
            if (scope == null && outcome.scripts() == 0) {
                send(recipients, Messages.NO_SCRIPTS);
            }
            return outcome;
        } catch (IOException | RuntimeException e) {
            plugin.getSLF4JLogger().error("Couldn't load the scripts", e);
            send(sender, Messages.READ_FAILED, text("reason", String.valueOf(e.getMessage())));
            return null;
        }
    }

    // --- enable and disable ---

    private void enable(CommandSender sender, String label, String[] args) {
        long start = System.nanoTime();
        List<CommandSender> self = List.of(sender);
        if (args[1].equalsIgnoreCase("all")) {
            Set<Path> files;
            try {
                files = ScriptFiles.toggleAll(scripts.scriptsFolder(), true);
            } catch (IOException e) {
                send(sender, Messages.ENABLE_SOME_FAILED, text("reason", e.getMessage()));
                return;
            }
            if (files.isEmpty()) {
                send(sender, Messages.NOTHING_DISABLED);
                return;
            }
            send(sender, Messages.RELOADING, text("doing", "Enabling"), text("what", "all disabled scripts"));
            ScriptReport report = new ScriptReport(label);
            if (load(sender, self, names(files)::contains, report) != null) {
                report.send(self, "Enabled", countOf(files.size(), "script"), millis(start));
            }
            return;
        }
        Path file = find(sender, join(args));
        if (file == null) {
            return;
        }
        if (Files.isDirectory(file)) {
            String folder = scripts.name(file) + "/";
            Set<Path> files;
            try {
                files = ScriptFiles.toggleAll(file, true);
            } catch (IOException e) {
                send(sender, Messages.ENABLE_SOME_FAILED, text("reason", e.getMessage()));
                return;
            }
            if (files.isEmpty()) {
                send(sender, Messages.FOLDER_NOTHING_DISABLED, text("name", folder));
                return;
            }
            Component what = countOf(files.size(), "script").append(Component.text(" in ")).append(Messages.highlight(folder));
            send(sender, Messages.RELOADING, text("doing", "Enabling"), component("what", what));
            ScriptReport report = new ScriptReport(label);
            if (load(sender, self, names(files)::contains, report) != null) {
                report.send(self, "Enabled", what, millis(start));
            }
            return;
        }
        if (ScriptFiles.LOADED.accept(file.toFile())) {
            String reload = "/" + label + " reload " + join(args);
            send(sender, Messages.ALREADY_ENABLED, text("name", scripts.name(file)), component("command", Messages.command(reload)));
            return;
        }
        String name = enabledName(scripts.name(file));
        try {
            file = ScriptFiles.toggle(file, true);
        } catch (IOException e) {
            send(sender, Messages.ENABLE_FAILED, text("name", name), text("reason", e.getMessage()));
            return;
        }
        send(sender, Messages.RELOADING, text("doing", "Enabling"), component("what", Messages.highlight(name)));
        ScriptReport report = new ScriptReport(label);
        String loaded = scripts.name(file);
        if (load(sender, self, loaded::equals, report) != null) {
            report.send(self, "Enabled", Messages.highlight(name), millis(start));
        }
    }

    private void disable(CommandSender sender, String[] args) {
        if (args[1].equalsIgnoreCase("all")) {
            scripts.unload(name -> true);
            try {
                Set<Path> files = ScriptFiles.toggleAll(scripts.scriptsFolder(), false);
                send(sender, Messages.DISABLED, component("what", countOf(files.size(), "script")));
            } catch (IOException e) {
                send(sender, Messages.DISABLE_SOME_FAILED, text("reason", e.getMessage()));
            }
            // Skript then goes on to look for a script called 'all'; that's left out.
            return;
        }
        Path file = find(sender, join(args));
        if (file == null) {
            return;
        }
        if (Files.isDirectory(file)) {
            String folder = scripts.name(file) + "/";
            scripts.unload(inFolder(file));
            Set<Path> files;
            try {
                files = ScriptFiles.toggleAll(file, false);
            } catch (IOException e) {
                send(sender, Messages.DISABLE_SOME_FAILED, text("reason", e.getMessage()));
                return;
            }
            if (files.isEmpty()) {
                send(sender, Messages.FOLDER_NOTHING_ENABLED, text("name", folder));
                return;
            }
            send(sender, Messages.DISABLED, component("what", countOf(files.size(), "script")
                    .append(Component.text(" in ")).append(Messages.highlight(folder))));
            return;
        }
        String name = scripts.name(file);
        if (ScriptFiles.disabled(file)) {
            send(sender, Messages.ALREADY_DISABLED, text("name", enabledName(name)));
            return;
        }
        scripts.unload(name::equals);
        try {
            ScriptFiles.toggle(file, false);
        } catch (IOException e) {
            send(sender, Messages.DISABLE_FAILED, text("name", name), text("reason", e.getMessage()));
            return;
        }
        send(sender, Messages.DISABLED, component("what", Messages.highlight(name)));
    }

    // --- list, info, update, restore ---

    private void list(CommandSender sender, String label) {
        List<String> enabled = scripts.loadedScripts().stream().sorted().toList();
        List<String> disabled = ScriptFiles.disabledScripts(scripts.scriptsFolder()).stream()
                .map(path -> path.replace(java.io.File.separatorChar, '/')).toList();
        if (enabled.isEmpty() && disabled.isEmpty()) {
            send(sender, Messages.NO_SCRIPTS);
            return;
        }
        send(sender, Messages.LIST_HEADER, text("enabled", enabled.size()),
                text("scripts", Messages.plural(enabled.size(), "script")), text("disabled", disabled.size()));
        group(sender, "Enabled", enabled, path -> "/" + label + " reload " + path);
        group(sender, "Disabled", disabled, path -> enableCommand(label, path));
    }

    /**
     * The command that enables the disabled script at {@code path}, or {@code null} for a script in a disabled
     * folder ({@code -games/shop.sk}): {@code /sk enable} doesn't rename folders, as in Skript, so the folder has
     * to be renamed.
     */
    static String enableCommand(String label, String path) {
        int slash = path.lastIndexOf('/');
        if (slash > 0) {
            for (String folder : path.substring(0, slash).split("/")) {
                if (folder.startsWith(ScriptFiles.DISABLED_PREFIX)) {
                    return null;
                }
            }
        }
        return "/" + label + " enable " + enabledName(path);
    }

    private static final String IN_DISABLED_FOLDER = "In a disabled folder: rename the folder without its - to enable it.";

    /**
     * One group of {@code /sk list}: the scripts in the scripts folder on one line, then each folder's. Clicking a
     * script types {@code command}'s command for it, if there is one.
     */
    private static void group(CommandSender sender, String label, List<String> paths,
            java.util.function.Function<String, String> command) {
        if (paths.isEmpty()) {
            return;
        }
        Map<String, List<Component>> folders = new TreeMap<>();
        for (String path : paths) {
            int slash = path.lastIndexOf('/');
            String folder = slash < 0 ? "" : path.substring(0, slash + 1);
            String file = path.substring(slash + 1);
            String typed = command.apply(path);
            Component entry;
            if (!Messages.chat(sender)) {
                entry = Component.text(file);
            } else if (typed != null) {
                entry = Messages.command(file, typed);
            } else {
                entry = Messages.highlight(file).hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        co.xenastudios.neoskript.plugin.text.Message.base(Component.text(IN_DISABLED_FOLDER))));
            }
            folders.computeIfAbsent(folder, f -> new ArrayList<>()).add(entry);
        }
        List<Component> top = folders.remove("");
        send(sender, Messages.LIST_GROUP, text("label", label), component("scripts",
                top == null ? Component.text("(none here; see the folders below)") : Messages.joined(top)));
        folders.forEach((folder, files) -> send(sender, Messages.LIST_FOLDER, text("folder", folder),
                component("scripts", Messages.joined(files))));
    }

    private void info(CommandSender sender) {
        send(sender, Messages.INFO_HEADER, text("version", plugin.getPluginMeta().getVersion()), text("server", serverVersion()));
        int enabled = scripts.loadedScripts().size();
        int disabled = ScriptFiles.disabledScripts(scripts.scriptsFolder()).size();
        line(sender, "Scripts", Component.text(enabled + " enabled, " + disabled + " disabled"));
        line(sender, "Addons", names(addons()));
        int paused = co.xenastudios.neoskript.runtime.Pauses.pending();
        if (paused > 0) {
            // Triggers waiting for an addon's slow work (a query, a request): a number that keeps growing is a script
            // starting work faster than it finishes.
            line(sender, "Waiting", Component.text(paused + (paused == 1 ? " trigger" : " triggers")
                    + " for an addon's result"));
        }
        List<String> hooks = new ArrayList<>();
        for (String name : plugin.getPluginMeta().getPluginSoftDependencies()) {
            Plugin hooked = Bukkit.getPluginManager().getPlugin(name);
            if (hooked != null) {
                hooks.add(hooked.getPluginMeta().getDisplayName());
            }
        }
        line(sender, "Hooks", names(hooks));
        line(sender, "Updates", updateState(sender));
        if (Messages.chat(sender)) {
            line(sender, "Links", Messages.joined(List.of(
                    Messages.link(sender, "Docs", Messages.DOCS),
                    Messages.link(sender, "Syntax", Messages.SYNTAX),
                    Messages.link(sender, "Download", Messages.DOWNLOAD),
                    Messages.link(sender, "Issues", Messages.ISSUES))));
        } else {
            line(sender, "Docs", Component.text(Messages.DOCS));
            line(sender, "Syntax", Component.text(Messages.SYNTAX));
            line(sender, "Issues", Component.text(Messages.ISSUES));
        }
    }

    private static void line(CommandSender sender, String label, Component value) {
        send(sender, Messages.INFO_LINE, text("label", label), component("value", value));
    }

    private static Component names(List<String> names) {
        return Component.text(names.isEmpty() ? "none" : String.join(", ", names));
    }

    /**
     * NeoSkript's addons: those registered through its API, as {@code name version}, then the other plugins that
     * depend on NeoSkript.
     */
    private List<String> addons() {
        List<String> addons = new ArrayList<>(plugin.registeredAddons());
        // A plugin that registered through the API is listed by that, not again by its plugin name.
        java.util.Set<String> registered = plugin.addonPlugins();
        for (Plugin other : Bukkit.getPluginManager().getPlugins()) {
            var meta = other.getPluginMeta();
            if (other.isEnabled() && (meta.getPluginDependencies().contains("NeoSkript") || meta.getPluginSoftDependencies().contains("NeoSkript"))
                    && !registered.contains(meta.getName())) {
                addons.add(meta.getDisplayName());
            }
        }
        return addons;
    }

    /** What the update checker knows, for {@code /sk info}. */
    private Component updateState(CommandSender sender) {
        String current = plugin.getPluginMeta().getVersion();
        UpdateNotices updates = plugin.updates();
        if (!UpdateChecker.checks(current)) {
            return Component.text("a development build, which doesn't check");
        }
        if (updates == null) {
            return Component.text("off on test servers");
        }
        if (!plugin.settings().updateChecker()) {
            return Component.text("off in config.yml");
        }
        UpdateChecker.Update found = updates.found();
        if (found != null) {
            return Messages.highlight(found.version()).append(Component.text(" is out: "))
                    .append(Messages.link(sender, "download it", found.url()));
        }
        return Component.text(updates.checked() ? "up to date" : "not checked yet");
    }

    private void update(CommandSender sender, String what) {
        if (what.equalsIgnoreCase("changes")) {
            send(sender, Messages.UPDATE_CHANGES, component("link", Messages.link(sender, "GitHub Releases", Messages.RELEASES)));
            return;
        }
        String current = plugin.getPluginMeta().getVersion();
        UpdateNotices updates = plugin.updates();
        if (!UpdateChecker.checks(current)) {
            send(sender, Messages.UPDATE_DEVELOPMENT, text("current", current),
                    component("link", Messages.link(sender, "neoskript.dev/download", Messages.DOWNLOAD)));
            return;
        }
        if (updates == null) {
            send(sender, Messages.UPDATE_TESTING);
            return;
        }
        if (!plugin.settings().updateChecker()) {
            send(sender, Messages.UPDATE_OFF);
            return;
        }
        send(sender, Messages.UPDATE_CHECKING);
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Optional<UpdateChecker.Update> found;
            try {
                found = updates.checkNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                send(sender, Messages.UPDATE_FAILED, text("reason", e.getMessage() == null ? e.toString() : e.getMessage()));
                return;
            }
            if (found.isEmpty()) {
                send(sender, Messages.UPDATE_NEWEST, text("current", current));
            } else {
                send(sender, Messages.UPDATE_OUT, text("latest", found.get().version()), text("current", current),
                        component("link", Messages.link(sender, found.get().url(), found.get().url())));
            }
        });
    }

    /** As Skript shows it: the brand and Minecraft version, then the build number and commit if known. */
    private static String serverVersion() {
        io.papermc.paper.ServerBuildInfo buildInfo = io.papermc.paper.ServerBuildInfo.buildInfo();
        String version = buildInfo.brandName() + " " + buildInfo.minecraftVersionName();
        if (buildInfo.buildNumber().isPresent()) {
            version += " #" + buildInfo.buildNumber().getAsInt();
        }
        if (buildInfo.gitCommit().isPresent()) {
            version += " (" + buildInfo.gitCommit().get() + ")";
        }
        return version;
    }

    /**
     * Writes the loaded scripts, as they were when they loaded, to a new folder in {@code dump}, for when
     * their files were deleted, as Skript's RestoreCommand.
     */
    private void restore(CommandSender sender) {
        // A new folder for each dump, so a restore never overwrites an old one.
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy HH-mm-ss");
        Path target = plugin.getDataFolder().toPath().resolve("dump").resolve(LocalDateTime.now().format(formatter));
        Map<String, String> sources = scripts.loadedSources();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            send(sender, Messages.RESTORING);
            try {
                Files.createDirectories(target);
            } catch (IOException e) {
                send(sender, Messages.RESTORE_FAILED, text("reason", e.getMessage()));
                plugin.getSLF4JLogger().error("Couldn't restore the scripts", e);
                return;
            }
            int restored = 0;
            for (Map.Entry<String, String> script : sources.entrySet()) {
                Path file = target.resolve(script.getKey());
                try {
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, script.getValue(), java.nio.file.StandardOpenOption.CREATE_NEW);
                    restored++;
                } catch (IOException e) {
                    send(sender, Messages.RESTORE_FAILED, text("reason", e.getMessage()));
                    plugin.getSLF4JLogger().error("Couldn't restore the scripts", e);
                }
            }
            send(sender, Messages.RESTORED, text("count", restored), text("scripts", Messages.plural(restored, "script")),
                    text("folder", plugin.getDataFolder().toPath().relativize(target).toString().replace('\\', '/') + "/"));
        });
    }

    // --- shared ---

    /** The script or folder {@code name} means, or {@code null} after telling the sender there's none. */
    private Path find(CommandSender sender, String name) {
        Path file = ScriptFiles.find(scripts.scriptsFolder(), name);
        if (file == null) {
            boolean folder = name.endsWith("/") || name.endsWith("\\") || name.endsWith(java.io.File.separator);
            send(sender, folder ? Messages.NO_FOLDER : Messages.NO_SCRIPT, text("name", name));
        }
        return file;
    }

    /** Whether a script's name is in {@code folder} or a folder in it. */
    private Predicate<String> inFolder(Path folder) {
        String prefix = scripts.name(folder) + "/";
        return name -> name.startsWith(prefix);
    }

    private Set<String> names(Set<Path> files) {
        Set<String> names = new LinkedHashSet<>();
        files.forEach(file -> names.add(scripts.name(file)));
        return names;
    }

    /** A script's name without the {@code -} that disables it: {@code sub/-c.sk} is {@code sub/c.sk}. */
    static String enabledName(String name) {
        int slash = name.lastIndexOf('/');
        String file = name.substring(slash + 1);
        return file.startsWith(ScriptFiles.DISABLED_PREFIX)
                ? name.substring(0, slash + 1) + file.substring(ScriptFiles.DISABLED_PREFIX.length())
                : name;
    }

    /** "4 scripts", with the number highlighted. */
    private static Component countOf(int count, String noun) {
        return Messages.highlight(String.valueOf(count)).append(Component.text(" " + Messages.plural(count, noun)));
    }

    private static long millis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    /** The arguments after the subcommand, as one name: script names can have spaces. */
    private static String join(String[] args) {
        return String.join(" ", List.of(args).subList(1, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> aliases = new ArrayList<>();
        SUBS.forEach(sub -> aliases.addAll(sub.aliases()));
        if (args.length <= 1) {
            String typed = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return aliases.stream().filter(alias -> alias.startsWith(typed)).toList();
        }
        Sub sub = find(args[0]);
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        return switch (sub.name()) {
            case "reload", "enable", "disable" -> {
                List<String> options = new ArrayList<>();
                Set<String> keywords = new LinkedHashSet<>(sub.args().keySet());
                keywords.remove("<script>");
                for (String option : ScriptFiles.completions(scripts.scriptsFolder(), args)) {
                    // The words are offered only while they match what's typed; scripts as Skript offers them.
                    if (!keywords.contains(option) || args.length == 2 && option.startsWith(typed)) {
                        options.add(option);
                    }
                }
                if (sub.name().equals("reload") && args.length == 2 && "last".startsWith(typed) && lastReloaded != null) {
                    options.add("last");
                }
                yield options;
            }
            case "update" -> args.length == 2
                    ? sub.args().keySet().stream().filter(arg -> arg.startsWith(typed)).toList()
                    : List.of();
            default -> List.of();
        };
    }
}
