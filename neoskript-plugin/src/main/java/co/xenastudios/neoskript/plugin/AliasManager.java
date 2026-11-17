/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.paper.GlobalAliases;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.slf4j.Logger;

/**
 * The alias files in {@code plugins/NeoSkript/aliases}, read with Skript's default aliases (unless
 * {@code aliases.load-defaults} is off) when the server starts and on {@code /sk reload aliases}. A bad file never
 * stops anything: its problems are reported as Skript reports them, and the rest of the aliases load.
 */
final class AliasManager {

    /** With more changed names than this, a reload of the aliases reloads every script. */
    static final int CHANGE_LIMIT = 2000;

    private final Path dataFolder;
    private final Logger logger;

    AliasManager(Path dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    Path folder() {
        return dataFolder.resolve("aliases");
    }

    /**
     * Reads the aliases when the server starts, with their problems in the console. Nothing from the alias files can
     * stop the plugin: if this fails, Skript's default aliases are used.
     */
    void load(boolean defaults) {
        try {
            long start = System.nanoTime();
            GlobalAliases.Loaded loaded = GlobalAliases.load(folder(), dataFolder, defaults);
            loaded.problems(AliasManager::toConsole);
            GlobalAliases.install(loaded);
            if (loaded.failed()) {
                logger.error("Couldn't load the alias files, so only Skript's default aliases are used");
            } else if (loaded.files() > 0 || !defaults) {
                logger.info("Loaded {} aliases from {} {}{} in {} ms", loaded.aliases(), loaded.files(),
                        loaded.files() == 1 ? "alias file" : "alias files", defaults ? " and the defaults" : "",
                        (System.nanoTime() - start) / 1_000_000);
            }
        } catch (RuntimeException | StackOverflowError | OutOfMemoryError | LinkageError e) {
            logger.error("Couldn't load the alias files, so only Skript's default aliases are used", e);
        }
    }

    /**
     * Reads the aliases again and uses them, with their problems going to {@code problems} with their lines of code.
     * Returns the names whose meaning changed, for the scripts that use them to be reloaded, or {@code null} if so
     * many changed that every script should be. If loading fails, the aliases in use stay and nothing changed.
     */
    Set<String> reload(boolean defaults, BiConsumer<Diagnostic, String> problems) {
        GlobalAliases.Loaded before = GlobalAliases.inUse();
        GlobalAliases.Loaded loaded = GlobalAliases.load(folder(), dataFolder, defaults);
        loaded.problems(problems);
        Set<String> changed = GlobalAliases.changes(before, loaded, CHANGE_LIMIT);
        GlobalAliases.install(loaded);
        return changed;
    }

    /** A problem as Skript logs one: like a script's, or for a whole file, the message alone. */
    static void toConsole(Diagnostic problem, String code) {
        if (problem.line() >= 1) {
            AutoReload.send(List.of(Bukkit.getConsoleSender()), AutoReload.SKRIPT_PREFIX + AutoReload.describe(problem, code));
            return;
        }
        String colour = problem.severity() == Severity.ERROR ? "<light red>" : "<yellow>";
        AutoReload.send(List.of(Bukkit.getConsoleSender()), AutoReload.SKRIPT_PREFIX + colour + "    "
                + co.xenastudios.neoskript.paper.TextComponentParser.instance().escape(problem.message()) + "<reset>");
    }
}
