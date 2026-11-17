/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.plugin.config.ConfigLoader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkriptSettingsTest {

    @TempDir
    Path folder;

    private Map<String, Object> read(String configSk) throws Exception {
        Path file = folder.resolve("config.sk");
        Files.writeString(file, configSk);
        return SkriptSettings.read(file, folder.resolve("lang"));
    }

    /** Skript 2.17.0's config.sk as it ships. */
    private static String stockConfig() throws Exception {
        try (InputStream in = SkriptSettingsTest.class.getResourceAsStream("/skript-config.sk")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void readsConfigFilesAsSkriptDoes() {
        Map<String, String> entries = SkriptConfigFile.read("""
                # a comment
                Language: german # trailing
                effect command token: :
                safe tags: color, ##hash
                ###
                case sensitive: true
                ###
                disable hooks:
                \tvault: true
                \tregions:
                \t\tworldguard: yes
                \tafter: 1
                not an entry
                language: french
                """);
        assertEquals(Map.of("language", "french", "effect command token", ":", "safe tags", "color, #hash",
                "disable hooks.vault", "true", "disable hooks.regions.worldguard", "yes", "disable hooks.after", "1"),
                entries);
    }

    @Test
    void skriptsDefaultConfigCopiesAsNeoSkriptsDefaults() throws Exception {
        Map<String, Object> copied = read(stockConfig());
        Path file = folder.resolve("config.yml");
        Files.writeString(file, ConfigLoader.defaultText(NeoSkriptConfig.SPEC));
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(file.toFile());
        for (Map.Entry<String, Object> setting : copied.entrySet()) {
            assertEquals(defaults.get(setting.getKey()), setting.getValue(), setting.getKey());
        }
        // Every option is in Skript's default config.
        assertEquals(SkriptSettings.OPTIONS.size() + 1, copied.size(), copied.keySet().toString());
    }

    @Test
    void copiesChangedSettings() throws Exception {
        Map<String, Object> copied = read("""
                language: German
                date format: yyyy-MM-dd 'at' HH:mm
                number accuracy: 5
                case sensitive: true
                color codes reset formatting: false
                safe tags: color, bold
                parse links in chat messages: true
                maximum target block distance: 7
                player name regex pattern: [a-z]{3,10}
                player variable fix: off
                plugin priority: Lowest
                listen to cancelled events by default: yes
                runtime errors:
                \tframe duration: 2 seconds
                \ttotal errors per frame: 3
                """);
        assertEquals("german", copied.get("language"));
        assertEquals("yyyy-MM-dd 'at' HH:mm", copied.get("text.date-format"));
        assertEquals(5, copied.get("text.number-accuracy"));
        assertEquals(true, copied.get("text.case-sensitive"));
        assertEquals(false, copied.get("text.color-codes-reset-formatting"));
        assertEquals(List.of("color", "bold"), copied.get("text.safe-tags"));
        assertEquals("lenient", copied.get("text.parse-links"));
        assertEquals(7, copied.get("syntax.target-block-distance"));
        assertEquals("[a-z]{3,10}", copied.get("syntax.player-name-pattern"));
        assertEquals(false, copied.get("variables.player-variable-fix"));
        assertEquals("lowest", copied.get("events.priority"));
        assertEquals(true, copied.get("events.listen-to-cancelled"));
        assertEquals("2 seconds", copied.get("runtime-errors.frame-duration"));
        assertEquals(3, copied.get("runtime-errors.total-errors-per-frame"));
    }

    @Test
    void valuesSkriptCantReadAreCopiedAsWhatSkriptRunsWith() throws Exception {
        Map<String, Object> copied = read("""
                language: klingon
                date format: yyyy-qq
                number accuracy: two
                case sensitive: maybe
                parse links in chat messages: sometimes
                maximum target block distance: far
                player name regex pattern: [a-z
                plugin priority: hihg
                runtime errors:
                \tframe duration: soon
                \ttotal errors per frame: -4
                """);
        assertEquals("english", copied.get("language"), "Skript falls back to English");
        assertEquals("default", copied.get("text.date-format"));
        assertEquals(2, copied.get("text.number-accuracy"));
        assertEquals(false, copied.get("text.case-sensitive"));
        assertEquals("disabled", copied.get("text.parse-links"));
        assertEquals(100, copied.get("syntax.target-block-distance"));
        assertEquals("[a-zA-Z0-9_]{1,16}", copied.get("syntax.player-name-pattern"));
        // Skript's built-in default, not its default config's 'high'.
        assertEquals("normal", copied.get("events.priority"));
        assertEquals("1 second", copied.get("runtime-errors.frame-duration"));
        assertEquals(0, copied.get("runtime-errors.total-errors-per-frame"), "Skript shows none, as for 0");
    }

    @Test
    void numbersOutsideWhatConfigYmlTakesAreBroughtIn() throws Exception {
        Map<String, Object> copied = read("number accuracy: -1\nmaximum target block distance: 0\n");
        assertEquals(0, copied.get("text.number-accuracy"));
        assertEquals(100, copied.get("syntax.target-block-distance"));
        copied = read("number accuracy: 5000\nmaximum target block distance: 99999\n");
        assertEquals(100, copied.get("text.number-accuracy"));
        assertEquals(1000, copied.get("syntax.target-block-distance"));
    }

    @Test
    void aLanguageFromSkriptsFolderIsCopied() throws Exception {
        Files.createDirectories(folder.resolve("lang"));
        Files.writeString(folder.resolve("lang/pirate.lang"), "none: <naught>\n");
        assertEquals("pirate", read("language: Pirate\n").get("language"));
    }

    @Test
    void anEmptyOrMissingConfigCopiesNothing() throws Exception {
        assertEquals(Map.of(), read(""));
        assertEquals(Map.of(), SkriptSettings.read(folder.resolve("missing.sk"), null));
        // A token can't be empty in config.yml.
        assertNull(read("effect command token:\n").get("effect-commands.token"));
    }

    @Test
    void theCopiedSettingsAreValidInConfigYml() throws Exception {
        Map<String, Object> copied = read(stockConfig().replace("number accuracy: 2", "number accuracy: 3")
                .replace("date format: default", "date format: HH:mm"));
        Path file = folder.resolve("config.yml");
        Files.writeString(file, ConfigLoader.defaultText(NeoSkriptConfig.SPEC));
        ConfigLoader.Change change = ConfigLoader.set(NeoSkriptConfig.SPEC, file, copied);
        assertTrue(change.written());
        ConfigLoader.Result<NeoSkriptConfig> result = ConfigLoader.load(NeoSkriptConfig.SPEC, file);
        assertEquals(List.of(), result.warnings());
        assertEquals(3, result.config().text().numberAccuracy());
        assertEquals("HH:mm", result.config().text().dateFormat().text());
    }
}
