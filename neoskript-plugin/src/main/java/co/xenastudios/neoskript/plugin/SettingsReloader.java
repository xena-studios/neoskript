/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.plugin.config.ConfigException;
import co.xenastudios.neoskript.plugin.config.ConfigLoader;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code /sk reload config}: reads config.yml again and applies what can change while the server runs: the
 * theme, the update checker, how players are named in variables, how often variables are saved, the runtime
 * error limits and effect commands. If the file can't be read, the running settings stay.
 *
 * <p>The settings that wait for a restart are compared with those the server started with, so a change stays
 * reported on every reload until the server restarts, and changing one back stops it being reported.
 */
final class SettingsReloader {

    /** What applying settings does to the running server. */
    interface Effects {
        /** Uses what loading found: the settings, the theme, and logging its notices and warnings. */
        void accept(ConfigLoader.Result<NeoSkriptConfig> result);

        void playerUuids(boolean uuids);

        void effectCommands(EffectCommands.Settings settings);

        /** The runtime error limits changed: restarts the frames with them. */
        void runtimeErrors(NeoSkriptConfig.RuntimeErrors limits);

        /** The save interval changed: saves on the new one. */
        void saveInterval(NeoSkriptConfig.Interval interval);

        /** Turns the update checker on or off; {@code turnedOn} if it was off, so it checks now. */
        void updateChecker(boolean enabled, boolean turnedOn);

        /** config.yml couldn't be read. */
        void failed(String message);
    }

    /**
     * What a reload found: why config.yml couldn't be read ({@code null} if it was), how many settings fell back
     * to their defaults, and the settings that differ from the running ones but only apply when the server
     * restarts.
     */
    record Outcome(String failure, int warnings, List<String> restartOnly) {}

    private final Path file;
    private final NeoSkriptConfig startup;
    private final Effects effects;
    private NeoSkriptConfig current;

    /**
     * @param startup the settings the server started with
     */
    SettingsReloader(Path file, NeoSkriptConfig startup, Effects effects) {
        this.file = file;
        this.startup = startup;
        this.current = startup;
        this.effects = effects;
    }

    synchronized Outcome reload() {
        ConfigLoader.Result<NeoSkriptConfig> result;
        try {
            result = ConfigLoader.load(NeoSkriptConfig.SPEC, file);
        } catch (ConfigException e) {
            effects.failed(e.getMessage());
            return new Outcome(e.getMessage(), 0, List.of());
        } catch (RuntimeException | StackOverflowError e) {
            String message = "Couldn't read config.yml: " + e;
            effects.failed(message);
            return new Outcome(message, 0, List.of());
        }
        NeoSkriptConfig before = current;
        NeoSkriptConfig now = result.config();
        try {
            apply(result, before);
        } catch (RuntimeException | StackOverflowError e) {
            // Put back what was running, so nothing is left half applied.
            try {
                apply(new ConfigLoader.Result<>(before, List.of(), List.of(), 0), now);
            } catch (RuntimeException | StackOverflowError again) {
                e.addSuppressed(again);
            }
            String message = "Couldn't apply config.yml: " + e;
            effects.failed(message);
            return new Outcome(message, 0, List.of());
        }
        current = now;
        return new Outcome(null, result.warnings().size(), now.restartOnlyChanges(startup));
    }

    /** Applies {@code result}'s settings, where {@code from} is what's running. */
    private void apply(ConfigLoader.Result<NeoSkriptConfig> result, NeoSkriptConfig from) {
        NeoSkriptConfig to = result.config();
        effects.accept(result);
        effects.playerUuids(to.variables().playerUuidsInNames());
        effects.effectCommands(EffectCommands.Settings.of(to.effectCommands()));
        if (!to.runtimeErrors().equals(from.runtimeErrors())) {
            effects.runtimeErrors(to.runtimeErrors());
        }
        if (!to.variables().saveInterval().equals(from.variables().saveInterval())) {
            effects.saveInterval(to.variables().saveInterval());
        }
        effects.updateChecker(to.updateChecker(), to.updateChecker() && !from.updateChecker());
    }
}
