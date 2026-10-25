/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import static co.xenastudios.neoskript.plugin.SkriptCommandMessages.PREFIX;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.paper.TextComponentParser;
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
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * {@code /skript} (also {@code /sk} and {@code /neoskript}), which NeoSkript takes over from Skript (D10):
 * Skript's subcommands (reload, enable, disable, list, info, update, restore and help) with its messages,
 * colours and tab completions, so existing habits and tools keep working.
 *
 * <p>Where NeoSkript differs: a script with errors keeps running its last version when reloaded (D12),
 * so a reload never unloads first; {@code reload config} reloads {@code config.yml} (NeoSkript has no
 * {@code config.sk}), {@code reload aliases} has nothing to reload as NeoSkript's aliases are built in,
 * and {@code update} and {@code gen-docs} say NeoSkript can't do them yet.
 */
final class SkriptCommand implements TabExecutor {

    /** Skript's {@code skript.no scripts}. */
    private static final String NO_SCRIPTS = "No scripts were found, maybe you should write some ;)";

    /** A subcommand: its names (the first is its name in help) and the arguments its help lists. */
    private record Sub(List<String> aliases, List<String> args) {
        Sub(String... aliases) {
            this(List.of(aliases), null);
        }

        String name() {
            return aliases.getFirst();
        }
    }

    // In Skript's order of registration (its help lists them in a set's order, which varies).
    private static final List<Sub> SUBS = List.of(
            new Sub("help"),
            new Sub(List.of("reload"), List.of("all", "scripts", "config", "aliases", "last", "<script>")),
            // Skript fails with an internal error for these without an argument; NeoSkript shows their help.
            new Sub(List.of("enable"), List.of("all", "<script>")),
            new Sub(List.of("disable"), List.of("all", "<script>")),
            new Sub("list", "show"),
            new Sub("info"),
            new Sub("update"),
            new Sub("restore", "dump"));

    /** Skript only has {@code gen-docs} with a docs template and {@code test} in development; neither is listed. */
    private static final Sub GEN_DOCS = new Sub("gen-docs");

    private final Plugin plugin;
    private final ScriptManager scripts;
    private final Runnable reloadConfig;
    /** What {@code /skript reload last} reloads. */
    private String lastReloaded;

    /**
     * @param reloadConfig reloads {@code config.yml} and applies what can change while the server runs
     */
    SkriptCommand(Plugin plugin, ScriptManager scripts, Runnable reloadConfig) {
        this.plugin = plugin;
        this.scripts = scripts;
        this.reloadConfig = reloadConfig;
    }

    private static Sub find(String alias) {
        for (Sub sub : SUBS) {
            for (String name : sub.aliases()) {
                if (name.equalsIgnoreCase(alias)) {
                    return sub;
                }
            }
        }
        if (GEN_DOCS.name().equalsIgnoreCase(alias)) {
            return GEN_DOCS;
        }
        return SUBS.getFirst();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Sub sub = find(args.length > 0 ? args[0] : "help");
        // sub command requires args but none was given
        if (args.length == 1 && sub.args() != null) {
            sendHelp(sender, sub);
            return true;
        }
        switch (sub.name()) {
            case "reload" -> onGlobalThread(() -> reload(sender, args));
            case "enable" -> onGlobalThread(() -> enable(sender, args));
            case "disable" -> onGlobalThread(() -> disable(sender, args));
            case "list" -> list(sender);
            case "info" -> info(sender);
            case "update" -> info(sender, "update.unavailable");
            case "restore" -> restore(sender);
            case "gen-docs" -> Skriptlike.info(sender, SkriptCommandMessages.get("gen-docs.unavailable"));
            default -> help(sender);
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

    private static void help(CommandSender sender) {
        TextComponentParser parser = TextComponentParser.instance();
        sender.sendMessage(parser.parse(SkriptCommandMessages.get("usage") + " <gold>/skript <red>..."));
        for (Sub sub : SUBS) {
            String description = SkriptCommandMessages.has("help." + sub.name() + ".description")
                    ? SkriptCommandMessages.get("help." + sub.name() + ".description")
                    : SkriptCommandMessages.get("help." + sub.name());
            sender.sendMessage(parser.parse("   <yellow>" + String.join("/", sub.aliases()) + " <dark_gray>- <white>" + description));
        }
    }

    private static void sendHelp(CommandSender sender, Sub sub) {
        TextComponentParser parser = TextComponentParser.instance();
        sender.sendMessage(parser.parseSafe(SkriptCommandMessages.get("usage") + " <gold>/skript <yellow>" + sub.name() + " <red>..."));
        for (String arg : sub.args()) {
            String description = SkriptCommandMessages.get("help." + sub.name() + "." + arg);
            sender.sendMessage(parser.parseSafe("   <yellow>" + arg + " <gray>- <white>" + description));
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

    private void reload(CommandSender sender, String[] args) {
        long start = System.nanoTime();
        Reload reload = new Reload(sender, reloadRecipients(sender), start);
        switch (args[1].toLowerCase(Locale.ENGLISH)) {
            case "all" -> {
                reload.reloading("config, aliases and scripts");
                reloadConfig.run();
                reload.scripts(null, true);
                reload.reloaded("config, aliases and scripts");
            }
            case "scripts" -> {
                reload.reloading("scripts");
                reload.scripts(null, true);
                reload.reloaded("scripts");
            }
            case "config" -> {
                reload.reloading("main config");
                reloadConfig.run();
                reload.reloaded("main config");
            }
            case "aliases" -> {
                // NeoSkript's aliases are built in, so there's nothing to read again.
                reload.reloading("aliases");
                reload.reloaded("aliases");
            }
            default -> {
                String scriptName;
                if (args[1].equalsIgnoreCase("last")) {
                    if (lastReloaded == null) {
                        info(sender, "reload.no last file");
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
                String fileName = file.getFileName().toString();
                if (Files.isDirectory(file)) {
                    reload.reloading("scripts in folder", fileName);
                    ScriptManager.Outcome outcome = reload.scripts(inFolder(file), false);
                    if (outcome == null) {
                        return;
                    }
                    if (outcome.scripts() == 0) {
                        info(sender, "reload.empty folder", fileName);
                        return;
                    }
                    reload.reloaded(reload.errors == 0 ? "x scripts in folder success" : "x scripts in folder error",
                            fileName, outcome.scripts());
                } else {
                    if (ScriptFiles.disabled(file)) {
                        info(sender, "reload.script disabled", fileName.substring(ScriptFiles.DISABLED_PREFIX.length()), join(args));
                        return;
                    }
                    reload.reloading("script", fileName);
                    String name = scripts.name(file);
                    if (reload.scripts(name::equals, false) != null) {
                        reload.reloaded("script", fileName);
                    }
                }
            }
        }
    }

    /** A reload's messages, which go to its recipients as Skript's RedirectingLogHandler sends them. */
    private final class Reload {
        private final CommandSender sender;
        private final List<CommandSender> recipients;
        private final long start;
        int errors;

        Reload(CommandSender sender, List<CommandSender> recipients, long start) {
            this.sender = sender;
            this.recipients = recipients;
            this.start = start;
        }

        void reloading(String what, Object... args) {
            what = args.length == 0 ? SkriptCommandMessages.get("reload." + what) : format("reload." + what, false, args);
            Skriptlike.info(sender, Skriptlike.fixCapitalization(String.format(SkriptCommandMessages.get("reload.reloading"), what)));
            // Others hear who is reloading.
            String text = String.format(SkriptCommandMessages.get("reload.player reload"), sender.getName(), what);
            for (CommandSender recipient : recipients) {
                if (recipient != sender) {
                    AutoReload.send(List.of(recipient), text);
                }
            }
        }

        /** Reloads the scripts {@code scope} accepts (all if {@code null}), or returns {@code null} if they couldn't be read. */
        ScriptManager.Outcome scripts(Predicate<String> scope, boolean warnIfNone) {
            BiConsumer<Diagnostic, SourceFile> problems = (problem, file) -> {
                if (problem.severity() == Severity.ERROR) {
                    errors++;
                }
                AutoReload.send(recipients, AutoReload.describe(problem, file));
            };
            try {
                ScriptManager.Outcome outcome = scripts.reload(scope, problems);
                if (warnIfNone && outcome.scripts() == 0) {
                    AutoReload.send(recipients, String.format(SkriptCommandMessages.get("reload.warning details"), NO_SCRIPTS));
                }
                return outcome;
            } catch (IOException | RuntimeException e) {
                plugin.getSLF4JLogger().error("Couldn't reload the scripts", e);
                Skriptlike.error(sender, "Couldn't read the scripts: " + e.getMessage());
                return null;
            }
        }

        void reloaded(String what, Object... args) {
            what = args.length == 0 ? SkriptCommandMessages.get("reload." + what) : format("reload." + what, true, args);
            String time = String.valueOf((System.nanoTime() - start) / 1_000_000);
            String message = errors == 0
                    ? PREFIX + String.format(SkriptCommandMessages.get("reload.reloaded"), what, time)
                    : PREFIX + String.format(SkriptCommandMessages.get("reload.error"), what, errors, time);
            AutoReload.send(recipients, Skriptlike.fixCapitalization(AutoReload.pluralize(message)));
        }
    }

    // --- enable and disable ---

    private void enable(CommandSender sender, String[] args) {
        Path folder = scripts.scriptsFolder();
        Counter errors = new Counter(sender);
        if (args[1].equalsIgnoreCase("all")) {
            try {
                info(sender, "enable.all.enabling");
                Set<Path> files = ScriptFiles.toggleAll(folder, true);
                if (load(sender, names(files)::contains, errors) == null) {
                    return;
                }
                if (errors.count == 0) {
                    info(sender, "enable.all.enabled");
                } else {
                    error(sender, "enable.all.error", errors.count);
                }
            } catch (IOException e) {
                error(sender, "enable.all.io error", e.toString());
            }
            return;
        }
        Path file = find(sender, join(args));
        if (file == null) {
            return;
        }
        if (Files.isDirectory(file)) {
            Set<Path> files;
            try {
                files = ScriptFiles.toggleAll(file, true);
            } catch (IOException e) {
                error(sender, "enable.folder.io error", file.getFileName(), e.toString());
                return;
            }
            String fileName = file.getFileName().toString();
            if (files.isEmpty()) {
                info(sender, "enable.folder.empty", fileName);
                return;
            }
            info(sender, "enable.folder.enabling", fileName, files.size());
            ScriptManager.Outcome outcome = load(sender, names(files)::contains, errors);
            if (outcome == null) {
                return;
            }
            if (errors.count == 0) {
                info(sender, "enable.folder.enabled", fileName, outcome.scripts());
            } else {
                error(sender, "enable.folder.error", fileName, errors.count);
            }
            return;
        }
        if (ScriptFiles.LOADED.accept(file.toFile())) {
            info(sender, "enable.single.already enabled", file.getFileName(), join(args));
            return;
        }
        try {
            file = ScriptFiles.toggle(file, true);
        } catch (IOException e) {
            error(sender, "enable.single.io error", file.getFileName().toString().substring(ScriptFiles.DISABLED_PREFIX.length()),
                    e.toString());
            return;
        }
        String fileName = file.getFileName().toString();
        info(sender, "enable.single.enabling", fileName);
        String name = scripts.name(file);
        if (load(sender, name::equals, errors) == null) {
            return;
        }
        if (errors.count == 0) {
            info(sender, "enable.single.enabled", fileName);
        } else {
            error(sender, "enable.single.error", fileName, errors.count);
        }
    }

    /** Counts the errors of loading scripts for enable, and sends their problems to whoever enabled them. */
    private static final class Counter implements BiConsumer<Diagnostic, SourceFile> {
        private final CommandSender sender;
        int count;

        Counter(CommandSender sender) {
            this.sender = sender;
        }

        @Override
        public void accept(Diagnostic problem, SourceFile file) {
            if (problem.severity() == Severity.ERROR) {
                count++;
            }
            AutoReload.send(List.of(sender), AutoReload.describe(problem, file));
        }
    }

    private ScriptManager.Outcome load(CommandSender sender, Predicate<String> scope, Counter errors) {
        try {
            return scripts.reload(scope, errors);
        } catch (IOException | RuntimeException e) {
            plugin.getSLF4JLogger().error("Couldn't load the scripts", e);
            Skriptlike.error(sender, "Couldn't read the scripts: " + e.getMessage());
            return null;
        }
    }

    private void disable(CommandSender sender, String[] args) {
        Path folder = scripts.scriptsFolder();
        if (args[1].equalsIgnoreCase("all")) {
            scripts.unload(name -> true);
            try {
                ScriptFiles.toggleAll(folder, false);
                info(sender, "disable.all.disabled");
            } catch (IOException e) {
                error(sender, "disable.all.io error", e.toString());
            }
            // Skript then goes on to look for a script called 'all'; that's left out.
            return;
        }
        Path file = find(sender, join(args));
        if (file == null) {
            return;
        }
        String fileName = file.getFileName().toString();
        if (Files.isDirectory(file)) {
            scripts.unload(inFolder(file));
            Set<Path> files;
            try {
                files = ScriptFiles.toggleAll(file, false);
            } catch (IOException e) {
                error(sender, "disable.folder.io error", fileName, e.toString());
                return;
            }
            if (files.isEmpty()) {
                info(sender, "disable.folder.empty", fileName);
                return;
            }
            info(sender, "disable.folder.disabled", fileName, files.size());
            return;
        }
        if (ScriptFiles.disabled(file)) {
            info(sender, "disable.single.already disabled", fileName.substring(ScriptFiles.DISABLED_PREFIX.length()));
            return;
        }
        String name = scripts.name(file);
        scripts.unload(name::equals);
        try {
            ScriptFiles.toggle(file, false);
        } catch (IOException e) {
            error(sender, "disable.single.io error", fileName, e.toString());
            return;
        }
        info(sender, "disable.single.disabled", fileName);
    }

    // --- list, info, restore ---

    private void list(CommandSender sender) {
        info(sender, "list.enabled.header");
        for (String name : scripts.loadedScripts()) {
            info(sender, "list.enabled.element", name);
        }
        info(sender, "list.disabled.header");
        for (String path : ScriptFiles.disabledScripts(scripts.scriptsFolder())) {
            info(sender, "list.disabled.element", path);
        }
    }

    private void info(CommandSender sender) {
        info(sender, "info.aliases");
        info(sender, "info.documentation");
        info(sender, "info.tutorials");
        info(sender, "info.server", serverVersion());
        info(sender, "info.version", plugin.getPluginMeta().getVersion());
        // NeoSkript has no addons yet.
        info(sender, "info.addons", "None");
        List<String> dependencies = new ArrayList<>();
        for (String name : plugin.getPluginMeta().getPluginSoftDependencies()) {
            Plugin dependency = Bukkit.getPluginManager().getPlugin(name);
            if (dependency != null) {
                dependencies.add(" - " + dependency.getPluginMeta().getDisplayName());
            }
        }
        if (dependencies.isEmpty()) {
            info(sender, "info.dependencies", "None");
        } else {
            info(sender, "info.dependencies", "");
            dependencies.forEach(line -> sender.sendMessage(TextComponentParser.instance().parse(line)));
        }
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
        // Create a unique folder for each dump, don't want to overwrite old dumps in a restore command
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy HH-mm-ss");
        Path target = plugin.getDataFolder().toPath().resolve("dump").resolve(LocalDateTime.now().format(formatter));
        Map<String, String> sources = scripts.loadedSources();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            info(sender, "restore.restoring");
            try {
                Files.createDirectories(target);
            } catch (IOException e) {
                info(sender, "restore.io error", e.getMessage());
                plugin.getSLF4JLogger().error("Error while restoring scripts.", e);
            }
            for (Map.Entry<String, String> script : sources.entrySet()) {
                Path file = target.resolve(script.getKey());
                try {
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, script.getValue(), java.nio.file.StandardOpenOption.CREATE_NEW);
                } catch (IOException e) {
                    info(sender, "restore.io error", e.getMessage());
                    plugin.getSLF4JLogger().error("Error while restoring scripts.", e);
                }
            }
            info(sender, "restore.restored", target);
        });
    }

    // --- shared ---

    /** The script or folder {@code name} means, or {@code null} after telling the sender there's none. */
    private Path find(CommandSender sender, String name) {
        Path file = ScriptFiles.find(scripts.scriptsFolder(), name);
        if (file == null) {
            boolean folder = name.endsWith("/") || name.endsWith("\\") || name.endsWith(java.io.File.separator);
            Skriptlike.error(sender, String.format(SkriptCommandMessages.get(folder ? "invalid folder" : "invalid script"), name));
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

    /** The arguments after the subcommand, as one name: script names can have spaces. */
    private static String join(String[] args) {
        return String.join(" ", List.of(args).subList(1, args.length));
    }

    /** Skript's Language.format, then PluralizingArgsMessage.format if {@code pluralize}. */
    private static String format(String key, boolean pluralize, Object... args) {
        String formatted = String.format(SkriptCommandMessages.get(key), args);
        return pluralize ? AutoReload.pluralize(formatted) : formatted;
    }

    /** Skript's SkriptCommand.info: the message, with its arguments, capitalised, after Skript's prefix. */
    private static void info(CommandSender sender, String key, Object... args) {
        String what = args.length == 0 ? SkriptCommandMessages.get(key) : format(key, true, args);
        Skriptlike.info(sender, Skriptlike.fixCapitalization(what));
    }

    /** Skript's SkriptCommand.error: as {@link #info}, in dark red. */
    private static void error(CommandSender sender, String key, Object... args) {
        String what = args.length == 0 ? SkriptCommandMessages.get(key) : format(key, true, args);
        Skriptlike.error(sender, Skriptlike.fixCapitalization(what));
    }

    /** Skript's Skript.info and Skript.error. */
    private static final class Skriptlike {
        static void info(CommandSender sender, String message) {
            sender.sendMessage(TextComponentParser.instance().parseSafe(PREFIX + message));
        }

        static void error(CommandSender sender, String message) {
            sender.sendMessage(TextComponentParser.instance().parseSafe(PREFIX + "<dark_red>" + message));
        }

        static String fixCapitalization(String s) {
            return AutoReload.fixCapitalization(s);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> aliases = new ArrayList<>();
        SUBS.forEach(sub -> aliases.addAll(sub.aliases()));
        if (args.length == 0 || args[0].isEmpty()) {
            return aliases;
        }
        if (args.length == 1) {
            return aliases.stream().filter(alias -> alias.startsWith(args[0])).toList();
        }
        return switch (find(args[0]).name()) {
            case "reload", "enable", "disable" -> ScriptFiles.completions(scripts.scriptsFolder(), args);
            default -> List.of();
        };
    }
}
