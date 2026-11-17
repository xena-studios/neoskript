/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.plugin.config.ConfigException;
import co.xenastudios.neoskript.plugin.config.ConfigLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NeoSkriptConfigTest {

    @TempDir
    Path folder;

    private Path file() {
        return folder.resolve("config.yml");
    }

    /** config.yml as NeoSkript 0.1.0-beta.1 wrote it, before it had a version. */
    private static String version1() throws IOException {
        try (InputStream in = NeoSkriptConfigTest.class.getResourceAsStream("/config-v1.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ConfigLoader.Result<NeoSkriptConfig> load() throws ConfigException {
        return ConfigLoader.load(NeoSkriptConfig.SPEC, file());
    }

    @Test
    void writesTheDefaultsFromTheSchema() throws Exception {
        ConfigLoader.Result<NeoSkriptConfig> first = load();
        assertEquals(List.of("Wrote the default config.yml."), first.notices());
        assertEquals(NeoSkriptConfig.DEFAULTS, first.config());
        String text = Files.readString(file());
        assertEquals(ConfigLoader.defaultText(NeoSkriptConfig.SPEC), text);
        assertTrue(text.startsWith("# NeoSkript's config. Docs: https://neoskript.dev/docs/configuration/\n"), text);
        // The version and then the theme come first, as in Xena Studios' other plugins.
        int version = text.indexOf("\nconfig-version: 2\n");
        int theme = text.indexOf("\ntheme:\n");
        assertTrue(version > 0 && theme > version && theme < text.indexOf("\nmetrics:"), text);
        assertTrue(text.contains("  highlight: \"<#FF4D9D>\"\n"), text);
        assertTrue(text.contains("  token: \"!\"\n"), text);
        assertTrue(text.contains("  priority: \"high\"\n"), text);
        assertTrue(text.contains("  save-interval: \"5 minutes\"\n"), text);

        // Read back, it's the defaults, with nothing to say and nothing written.
        ConfigLoader.Result<NeoSkriptConfig> again = load();
        assertEquals(NeoSkriptConfig.DEFAULTS, again.config());
        assertEquals(List.of(), again.warnings());
        assertEquals(List.of(), again.notices());
        assertEquals(text, Files.readString(file()));
    }

    @Test
    void carriesVersion1ForwardKeepingEveryValue() throws Exception {
        String old = version1()
                .replace("metrics: true", "metrics: false")
                .replace("file: variables.csv", "file: data/vars.csv")
                .replace("save-interval: 5 minutes", "save-interval: 30 seconds")
                .replace("player-uuids-in-names: true", "player-uuids-in-names: false")
                .replace("priority: high", "priority: lowest")
                .replace("listen-to-cancelled: false", "listen-to-cancelled: true")
                .replace("total-errors-per-frame: 8", "total-errors-per-frame: 20")
                .replace("warning-timeout-length: 10", "warning-timeout-length: 3")
                .replace("enabled: false", "enabled: true")
                .replace("token: \"!\"", "token: \"#!\"")
                .replace("allow-ops: false", "allow-ops: true")
                .replace("log: false", "log: true");
        Files.writeString(file(), old);

        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals(1, result.carriedFrom());
        assertEquals(List.of(), result.warnings());
        assertEquals(List.of(
                "Carried config.yml forward from config version 1 to 2, keeping your settings. The old file is config.yml.bak-v1.",
                "Added theme with its default."), result.notices());
        assertEquals(old, Files.readString(folder.resolve("config.yml.bak-v1")));

        NeoSkriptConfig config = result.config();
        assertFalse(config.metrics());
        assertTrue(config.updateChecker());
        assertEquals("data/vars.csv", config.variables().file());
        assertEquals(30_000, config.variables().saveInterval().millis());
        assertFalse(config.variables().playerUuidsInNames());
        assertEquals(NeoSkriptConfig.Priority.LOWEST, config.events().priority());
        assertTrue(config.events().listenToCancelled());
        assertEquals(20, config.runtimeErrors().totalErrorsPerFrame());
        assertEquals(3, config.runtimeErrors().warningTimeoutLength());
        assertEquals(new NeoSkriptConfig.EffectCommands(true, "#!", true, true), config.effectCommands());
        assertEquals(NeoSkriptConfig.DEFAULTS.theme(), config.theme());

        // The new file is in the current layout, with the values as the owner wrote them.
        String text = Files.readString(file());
        assertTrue(text.contains("\nconfig-version: 2\n"), text);
        assertTrue(text.indexOf("\ntheme:\n") < text.indexOf("\nmetrics: false\n"), text);
        assertTrue(text.contains("  save-interval: \"30 seconds\"\n"), text);
        assertTrue(text.contains("  token: \"#!\"\n"), text);
        assertTrue(text.contains("  priority: \"lowest\"\n"), text);

        // Loading it again changes nothing.
        ConfigLoader.Result<NeoSkriptConfig> again = load();
        assertEquals(config, again.config());
        assertEquals(List.of(), again.notices());
        assertEquals(text, Files.readString(file()));
    }

    @Test
    void carriesForwardAPartialFile() throws Exception {
        // As the harness writes one: only what it changes.
        Files.writeString(file(), "effect-commands:\n  enabled: true\n");
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals(1, result.carriedFrom());
        assertTrue(result.config().effectCommands().enabled());
        assertEquals("!", result.config().effectCommands().token());
        assertEquals(NeoSkriptConfig.DEFAULTS.runtimeErrors(), result.config().runtimeErrors());
        assertTrue(Files.readString(file()).contains("effect-commands:\n"));
    }

    @Test
    void keepsAValueThatIsNotValidAndWarnsAboutIt() throws Exception {
        Files.writeString(file(), version1()
                .replace("priority: high", "priority: urgent")
                .replace("save-interval: 5 minutes", "save-interval: soon")
                .replace("total-errors-per-frame: 8", "total-errors-per-frame: -1")
                .replace("metrics: true", "metrics: maybe"));
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals(List.of(
                "metrics (\"maybe\") should be true or false; using the default.",
                "variables.save-interval (\"soon\") isn't a length of time, such as 5 minutes or 1 second; using the default.",
                "events.priority (\"urgent\") should be one of lowest, low, normal, high, highest, monitor; using the default.",
                "runtime-errors.total-errors-per-frame (\"-1\") must be at least 0; using the default."), result.warnings());
        assertEquals(NeoSkriptConfig.Priority.HIGH, result.config().events().priority());
        // What the owner wrote stays in the file, to fix.
        String text = Files.readString(file());
        assertTrue(text.contains("  priority: \"urgent\"\n"), text);
        assertTrue(text.contains("  save-interval: \"soon\"\n"), text);
        assertTrue(text.contains("\nmetrics: \"maybe\"\n"), text);
    }

    @Test
    void neverOverwritesAFileThatIsNotValidYaml() throws Exception {
        String broken = "metrics: true\nvariables:\n  file: [oops\n";
        Files.writeString(file(), broken);
        ConfigException e = assertThrows(ConfigException.class, this::load);
        assertTrue(e.getMessage().startsWith("config.yml isn't valid YAML: "), e.getMessage());
        assertEquals(broken, Files.readString(file()));
        assertFalse(Files.exists(folder.resolve("config.yml.bak-v1")));
    }

    @Test
    void addsNewSettingsToACurrentFileKeepingItsComments() throws Exception {
        load();
        String text = Files.readString(file())
                .replace("metrics: true\n", "# Mine.\nmetrics: false\n")
                .replaceAll("(?s)\neffect-commands:.*", "\n");
        Files.writeString(file(), text);
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals(List.of("Added effect-commands with its default."), result.notices());
        String after = Files.readString(file());
        assertTrue(after.contains("# Mine.\nmetrics: false\n"), after);
        assertTrue(after.contains("effect-commands:\n"), after);
        assertFalse(result.config().metrics());
    }

    @Test
    void aBlankPrefixMeansNone() throws Exception {
        load();
        Files.writeString(file(), Files.readString(file()).replaceFirst("  prefix: \".*\"\n", "  prefix:\n"));
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals("", result.config().theme().prefix());
        assertEquals(List.of(), result.warnings());
    }

    @Test
    void aStyleThatIsNotOneFallsBack() throws Exception {
        load();
        Files.writeString(file(), Files.readString(file()).replace("  highlight: \"<#FF4D9D>\"", "  highlight: \"<rainbow>\""));
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals(List.of("theme.highlight (\"<rainbow>\") can't be a gradient or rainbow; put those in the prefix"
                + " instead; using the default."), result.warnings());
        assertEquals(NeoSkriptConfig.DEFAULTS.theme().highlight(), result.config().theme().highlight());
    }

    @Test
    void setsCopiedSettingsInTheCurrentLayout() throws Exception {
        load();
        ConfigLoader.set(NeoSkriptConfig.SPEC, file(), Map.of("effect-commands.enabled", true, "effect-commands.token", "?"));
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals(new NeoSkriptConfig.EffectCommands(true, "?", false, false), result.config().effectCommands());
        assertEquals(List.of(), result.notices());
        assertTrue(Files.readString(file()).contains("  token: \"?\"\n"));
    }

    @Test
    void theDocsShowTheDefaultConfig() throws IOException {
        Path page = Path.of("../website/src/content/docs/docs/configuration.md");
        String docs = Files.readString(page);
        String config = ConfigLoader.defaultText(NeoSkriptConfig.SPEC);
        assertTrue(docs.contains("```yaml title=\"plugins/NeoSkript/config.yml\"\n" + config + "```"),
                "The full default config in " + page + " is out of date. It should be:\n" + config);
    }
}
