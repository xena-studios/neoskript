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
    void copiesTheWarningLoggingAndHookSettings() throws Exception {
        Map<String, Object> copied = read("""
                verbosity: Very High
                disable variable will not be saved warnings: true
                disable variable missing and/or warnings: false
                disable starting a variable's name with an expression warnings: yes
                disable single colon in variable name warnings: on
                disable unreachable code warnings: true
                long parse time warning threshold: 500 milliseconds
                enable legacy tab completion event compatibility: false
                disable hooks:
                	vault: true
                	regions:
                		grief prevention: false
                		worldguard: true
                """);
        assertEquals("very-high", copied.get("verbosity"));
        assertEquals(false, copied.get("warnings.variable-not-saved"));
        assertEquals(true, copied.get("warnings.missing-and-or"));
        assertEquals(false, copied.get("warnings.variable-starting-with-expression"));
        assertEquals(false, copied.get("warnings.single-colon-in-variable-name"));
        assertEquals(false, copied.get("warnings.unreachable-code"));
        assertEquals("500 milliseconds", copied.get("warnings.long-parse-time"));
        assertEquals(false, copied.get("syntax.legacy-tab-completion"));
        assertEquals(false, copied.get("hooks.vault"));
        assertEquals(true, copied.get("hooks.grief-prevention"));
        assertEquals(false, copied.get("hooks.worldguard"));
        // As Skript: an unreadable verbosity is normal, and an unreadable threshold none.
        copied = read("verbosity: loud\nlong parse time warning threshold: a while\n");
        assertEquals("normal", copied.get("verbosity"));
        assertEquals("0 seconds", copied.get("warnings.long-parse-time"));
    }

    @Test
    void copiesCompressBackups() throws Exception {
        assertEquals(false, read("compress backups: false\n").get("variables.compress-backups"));
        // Skript's built-in default, though its default config.sk turns it on.
        assertEquals(false, read("compress backups: often\n").get("variables.compress-backups"));
    }

    @Test
    void saysWhichSettingsArentCopiedAndWhy() throws Exception {
        Path file = folder.resolve("config.sk");
        Files.writeString(file, stockConfig());
        assertEquals(List.of(), SkriptSettings.notCopied(file));
        Files.writeString(file, stockConfig().replace("use type properties: true", "use type properties: false")
                .replace("enable timings: false", "enable timings: true"));
        List<String> notes = SkriptSettings.notCopied(file);
        assertEquals(2, notes.size(), notes.toString());
        Files.writeString(file, stockConfig() + "execute functions with missing parameters: false\nsimplify syntax on parse: false\n");
        assertEquals(2, SkriptSettings.notCopied(file).size(), SkriptSettings.notCopied(file).toString());
        Files.writeString(file, stockConfig().replace("variable changes until save: 1000", "variable changes until save: 50"));
        assertTrue(SkriptSettings.notCopied(file).getFirst().startsWith("Skript's 'variable changes until save: 50'"));
        assertTrue(notes.getFirst().startsWith("Skript's 'use type properties: false' isn't copied"), notes.toString());
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
        assertEquals(-1, copied.get("text.number-accuracy"), "kept for 'numbers from', as Skript keeps it");
        assertEquals(0, copied.get("syntax.target-block-distance"), "0 is 0, as in Skript");
        copied = read("number accuracy: 5000\nmaximum target block distance: 99999\n");
        assertEquals(1000, copied.get("text.number-accuracy"));
        assertEquals(1000, copied.get("syntax.target-block-distance"));
        assertEquals(0, read("maximum target block distance: -5\n").get("syntax.target-block-distance"));
    }

    @Test
    void numbersAndYesOrNoAreReadAsSkriptReadsThem() throws Exception {
        assertEquals(10, read("maximum target block distance: 1_0\n").get("syntax.target-block-distance"));
        assertEquals(5, read("maximum target block distance: 5 degrees\n").get("syntax.target-block-distance"));
        assertEquals(100, read("maximum target block distance: 5e1\n").get("syntax.target-block-distance"));
        // Skript's yes and no match case: 'True' is no boolean to it, so it runs with its default.
        assertEquals(false, read("case sensitive: True\n").get("text.case-sensitive"));
        assertEquals(true, read("case sensitive: yes\n").get("text.case-sensitive"));
    }

    @Test
    void copiesLoadDefaultAliases() throws Exception {
        assertEquals(false, read("load default aliases: false\n").get("aliases.load-defaults"));
        assertEquals(true, read("load default aliases: maybe\n").get("aliases.load-defaults"), "Skript's default");
    }

    @Test
    void parseLinksIsMatchedExactlyAsSkriptMatchesIt() throws Exception {
        assertEquals("strict", read("parse links in chat messages: strict\n").get("text.parse-links"));
        assertEquals("lenient", read("parse links in chat messages: true\n").get("text.parse-links"));
        // Skript warns that it doesn't know 'Strict' and leaves links off.
        assertEquals("disabled", read("parse links in chat messages: Strict\n").get("text.parse-links"));
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
