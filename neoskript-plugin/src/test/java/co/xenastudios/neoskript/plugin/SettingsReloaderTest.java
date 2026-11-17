/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.plugin.config.ConfigLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsReloaderTest {

    @TempDir
    Path folder;

    private Path file;
    private final List<String> applied = new ArrayList<>();
    private SettingsReloader reloader;

    /** Writes down what a reload does to the running server. */
    private final SettingsReloader.Effects effects = new SettingsReloader.Effects() {
        @Override
        public void accept(ConfigLoader.Result<NeoSkriptConfig> result) {
            applied.add("settings");
        }

        @Override
        public void playerUuids(boolean uuids) {
            applied.add("player-uuids " + uuids);
        }

        @Override
        public void scripts(NeoSkriptConfig settings) {
            applied.add("scripts " + settings.text().numberAccuracy());
        }

        @Override
        public void effectCommands(EffectCommands.Settings settings) {
            applied.add("effect-commands " + settings.enabled() + " " + settings.token());
        }

        @Override
        public void runtimeErrors(NeoSkriptConfig.RuntimeErrors limits) {
            applied.add("runtime-errors " + limits.totalErrorsPerFrame());
        }

        @Override
        public void saveInterval(NeoSkriptConfig.Interval interval) {
            applied.add("save-interval " + interval);
        }

        @Override
        public void updateChecker(boolean enabled, boolean turnedOn) {
            applied.add("update-checker " + enabled + (turnedOn ? " check now" : ""));
        }

        @Override
        public void failed(String message) {
            applied.add("failed");
        }
    };

    @BeforeEach
    void start() throws Exception {
        file = folder.resolve("config.yml");
        Files.writeString(file, ConfigLoader.defaultText(NeoSkriptConfig.SPEC).replace("update-checker: true", "update-checker: false"));
        reloader = new SettingsReloader(file, ConfigLoader.load(NeoSkriptConfig.SPEC, file).config(), effects);
    }

    private void edit(String from, String to) throws Exception {
        String text = Files.readString(file);
        assertTrue(text.contains(from), from);
        Files.writeString(file, text.replace(from, to));
    }

    @Test
    void anUnchangedFileReappliesOnlyWhatsCheap() {
        SettingsReloader.Outcome outcome = reloader.reload();
        assertEquals(new SettingsReloader.Outcome(null, 0, List.of()), outcome);
        assertEquals(List.of("settings", "player-uuids true", "scripts 2", "effect-commands false !", "update-checker false"), applied);
    }

    @Test
    void appliesWhatChangedWhileTheServerRuns() throws Exception {
        edit("save-interval: \"5 minutes\"", "save-interval: \"1 minute\"");
        edit("total-errors-per-frame: 8", "total-errors-per-frame: 3");
        edit("update-checker: false", "update-checker: true");
        edit("player-uuids-in-names: true", "player-uuids-in-names: false");
        edit("  enabled: false\n  # Skript's 'effect command token'", "  enabled: true\n  # Skript's 'effect command token'");
        SettingsReloader.Outcome outcome = reloader.reload();
        assertEquals(List.of(), outcome.restartOnly());
        assertEquals(List.of("settings", "player-uuids false", "scripts 2", "effect-commands true !", "runtime-errors 3",
                "save-interval 1 minute", "update-checker true check now"), applied);

        // A second reload with nothing new doesn't restart the frames or the saves, or check again.
        applied.clear();
        reloader.reload();
        assertEquals(List.of("settings", "player-uuids false", "scripts 2", "effect-commands true !", "update-checker true"), applied);
    }

    @Test
    void settingsThatWaitForARestartAreReportedUntilItHappens() throws Exception {
        edit("metrics: true", "metrics: false");
        assertEquals(List.of("metrics"), reloader.reload().restartOnly());
        // Still different from what's running, though not from the last reload.
        edit("priority: \"high\"", "priority: \"low\"");
        assertEquals(List.of("metrics", "events.priority"), reloader.reload().restartOnly());
        // Changed back, it's no longer waiting.
        edit("metrics: false", "metrics: true");
        assertEquals(List.of("events.priority"), reloader.reload().restartOnly());
    }

    @Test
    void variableStorageAndRecoveryWaitForARestart() throws Exception {
        edit("storage: \"csv\"", "storage: \"native\"");
        edit("recover: false", "recover: true");
        assertEquals(List.of("variables.storage", "variables.recover"), reloader.reload().restartOnly());
    }

    @Test
    void aFileThatIsNotValidYamlKeepsTheRunningSettings() throws Exception {
        Files.writeString(file, "metrics: [oops\n");
        SettingsReloader.Outcome outcome = reloader.reload();
        assertTrue(outcome.failure().startsWith("config.yml isn't valid YAML: "), outcome.failure());
        assertEquals(List.of("failed"), applied);
        assertEquals("metrics: [oops\n", Files.readString(file));

        // Fixed, the next reload compares with the settings that were running, not the broken file.
        Files.writeString(file, ConfigLoader.defaultText(NeoSkriptConfig.SPEC).replace("update-checker: true", "update-checker: false")
                .replace("save-interval: \"5 minutes\"", "save-interval: \"2 minutes\""));
        applied.clear();
        reloader.reload();
        assertTrue(applied.contains("save-interval 2 minutes"), applied.toString());
    }

    @Test
    void badValuesAreCounted() throws Exception {
        edit("metrics: true", "metrics: maybe");
        assertEquals(1, reloader.reload().warnings());
    }
    @Test
    void aFailureWhileApplyingPutsBackTheRunningSettings() throws Exception {
        List<String> applied = new ArrayList<>();
        boolean[] fail = {true};
        SettingsReloader.Effects failing = new SettingsReloader.Effects() {
            @Override
            public void accept(ConfigLoader.Result<NeoSkriptConfig> result) {
                applied.add("settings " + result.config().variables().saveInterval());
            }

            @Override
            public void playerUuids(boolean uuids) {}

            @Override
            public void scripts(NeoSkriptConfig settings) {}

            @Override
            public void effectCommands(EffectCommands.Settings settings) {}

            @Override
            public void runtimeErrors(NeoSkriptConfig.RuntimeErrors limits) {
                if (fail[0] && limits.totalErrorsPerFrame() == 3) {
                    throw new StackOverflowError();
                }
                applied.add("runtime-errors " + limits.totalErrorsPerFrame());
            }

            @Override
            public void saveInterval(NeoSkriptConfig.Interval interval) {
                applied.add("save-interval " + interval);
            }

            @Override
            public void updateChecker(boolean enabled, boolean turnedOn) {}

            @Override
            public void failed(String message) {
                applied.add("failed: " + message);
            }
        };
        SettingsReloader reloader = new SettingsReloader(file, ConfigLoader.load(NeoSkriptConfig.SPEC, file).config(), failing);
        edit("save-interval: \"5 minutes\"", "save-interval: \"1 minute\"");
        edit("total-errors-per-frame: 8", "total-errors-per-frame: 3");
        SettingsReloader.Outcome outcome = reloader.reload();
        assertEquals("Couldn't apply config.yml: java.lang.StackOverflowError", outcome.failure());
        // The new settings, up to the failure, then all the running ones put back.
        assertEquals(List.of("settings 1 minute", "settings 5 minutes", "runtime-errors 8", "save-interval 5 minutes",
                "failed: Couldn't apply config.yml: java.lang.StackOverflowError"), applied);

        // The running settings are still what the next reload compares with.
        fail[0] = false;
        applied.clear();
        reloader.reload();
        assertEquals(List.of("settings 1 minute", "runtime-errors 3", "save-interval 1 minute"), applied);
    }

    @Test
    void aReloadOfAFileWithManyAliasesWorks() throws Exception {
        StringBuilder text = new StringBuilder(Files.readString(file)).append("base: &base [1, 2]\nmany:\n");
        for (int i = 0; i < 80; i++) {
            text.append("- *base\n");
        }
        Files.writeString(file, text.toString());
        assertEquals(null, reloader.reload().failure());
    }
}
