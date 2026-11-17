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
import co.xenastudios.neoskript.plugin.config.AllowBlank;
import co.xenastudios.neoskript.plugin.config.ConfigLoader;
import co.xenastudios.neoskript.plugin.config.ConfigSpec;
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
        int version = text.indexOf("\nconfig-version: 3\n");
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
                "Carried config.yml forward from config version 1 to 3, keeping your settings. The old file is config.yml.bak-v1.",
                "Added 7 new settings with their defaults: theme, aliases, language, text, variables.recover,"
                        + " variables.player-variable-fix, syntax."), result.notices());
        assertEquals(old, Files.readString(folder.resolve("config.yml.bak-v1")));

        NeoSkriptConfig config = result.config();
        assertFalse(config.metrics());
        assertTrue(config.updateChecker());
        assertEquals("data/vars.csv", defaultFile(config));
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
        assertTrue(text.contains("\nconfig-version: 3\n"), text);
        assertTrue(text.contains("\ndatabases:\n  default:\n    type: \"csv\"\n    pattern: \".*\"\n    file: \"data/vars.csv\"\n"),
                text);
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
    void setsCopiedSettings() throws Exception {
        load();
        ConfigLoader.set(NeoSkriptConfig.SPEC, file(), Map.of("effect-commands.enabled", true, "effect-commands.token", "?"));
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals(new NeoSkriptConfig.EffectCommands(true, "?", false, false), result.config().effectCommands());
        assertEquals(List.of(), result.notices());
        assertEquals(List.of(), result.warnings());
    }

    // --- Files that can't be written: their settings are used as read ---

    /** Runs {@code body} with the folder read-only, as a read-only mount is. */
    private void readOnly(ThrowingRunnable body) throws Exception {
        assertTrue(folder.toFile().setWritable(false));
        try {
            body.run();
        } finally {
            folder.toFile().setWritable(true);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    void aVersion1FileThatCannotBeWrittenKeepsItsSettings() throws Exception {
        String old = version1()
                .replace("player-uuids-in-names: true", "player-uuids-in-names: false")
                .replace("file: variables.csv", "file: data/vars.csv")
                .replace("enabled: false", "enabled: true");
        Files.writeString(file(), old);
        readOnly(() -> {
            ConfigLoader.Result<NeoSkriptConfig> result = load();
            assertFalse(result.config().variables().playerUuidsInNames());
            assertEquals("data/vars.csv", defaultFile(result.config()));
            assertTrue(result.config().effectCommands().enabled());
            assertEquals(1, result.warnings().size(), result.warnings().toString());
            assertTrue(result.warnings().getFirst().startsWith("Couldn't update config.yml: "), result.warnings().toString());
            assertTrue(result.warnings().getFirst().endsWith("; using its settings as read."), result.warnings().toString());
        });
        assertEquals(old, Files.readString(file()));
        assertFalse(Files.exists(folder.resolve("config.yml.bak-v1")));
    }

    @Test
    void aCurrentFileMissingASettingThatCannotBeWrittenKeepsItsSettings() throws Exception {
        load();
        String text = Files.readString(file()).replace("metrics: true", "metrics: false")
                .replaceAll("(?s)\neffect-commands:.*", "\n");
        Files.writeString(file(), text);
        readOnly(() -> {
            ConfigLoader.Result<NeoSkriptConfig> result = load();
            assertFalse(result.config().metrics());
            assertEquals(NeoSkriptConfig.DEFAULTS.effectCommands(), result.config().effectCommands());
            assertEquals(List.of(), result.notices());
            assertTrue(result.warnings().getFirst().startsWith("Couldn't update config.yml: "));
        });
        assertEquals(text, Files.readString(file()));
    }

    @Test
    void noFileAndNoWayToWriteOneRunsOnTheDefaults() throws Exception {
        readOnly(() -> {
            ConfigLoader.Result<NeoSkriptConfig> result = load();
            assertEquals(NeoSkriptConfig.DEFAULTS, result.config());
            assertTrue(result.warnings().getFirst().startsWith("Couldn't write the default config.yml: "));
        });
        assertFalse(Files.exists(file()));
    }

    // --- Versions ---

    @Test
    void aFileFromANewerNeoSkriptIsNeverWritten() throws Exception {
        String newer = "# Mine.\nconfig-version: 4\nmetrics: false\nsomething-new: 4\n";
        Files.writeString(file(), newer);
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertFalse(result.config().metrics());
        assertTrue(result.warnings().getFirst().startsWith("config.yml is from a newer NeoSkript (config version 4;"));
        assertEquals(newer, Files.readString(file()));

        ConfigLoader.Change change = ConfigLoader.set(NeoSkriptConfig.SPEC, file(), Map.of("effect-commands.enabled", true));
        assertFalse(change.written());
        assertTrue(change.warnings().getFirst().contains("so it's left as it is, without effect-commands.enabled."));
        assertEquals(newer, Files.readString(file()));
        assertEquals(List.of("config.yml"), names());
    }

    private List<String> names() throws IOException {
        try (var files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void aCurrentFileWithoutConfigVersionIsNotCarriedForward() throws Exception {
        load();
        String text = Files.readString(file())
                .replaceFirst("# Managed by NeoSkript.*\nconfig-version: 3\n", "")
                .replace("metrics: true\n", "# Mine.\nmetrics: false\nmine: 1\n");
        Files.writeString(file(), text);
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals(0, result.carriedFrom());
        assertFalse(result.config().metrics());
        assertEquals(List.of("config.yml"), names());
        String after = Files.readString(file());
        assertTrue(after.contains("config-version: 3\n"), after);
        assertTrue(after.contains("# Mine.\nmetrics: false\nmine: 1\n"), after);
    }

    // --- Keys that aren't settings ---

    @Test
    void carryingForwardKeepsKeysThatAreNotSettings() throws Exception {
        String old = version1()
                .replace("  file: variables.csv\n", "  file: variables.csv\n  # A newer NeoSkript's.\n  compression: zstd\n")
                + "\n# Mine.\ncustom: [a, b]\nnested:\n  deeper:\n    value: 1\nlists:\n- [1, 2]\n- {x: y}\n";
        Files.writeString(file(), old);
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals("Kept variables.compression, custom, nested, lists, which aren't NeoSkript's, as they were. Comments on"
                + " NeoSkript's settings are its own now; yours are in config.yml.bak-v1.", result.notices().getLast());
        String text = Files.readString(file());
        assertTrue(text.contains("  player-variable-fix: true\n  # A newer NeoSkript's.\n  compression: \"zstd\"\n"), text);
        assertTrue(text.contains("# Mine.\ncustom: [\"a\", \"b\"]\n"), text);
        assertTrue(text.contains("nested:\n  deeper:\n    value: 1\n"), text);

        // Read back, the keys are all there, and nothing changes.
        org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file().toFile());
        assertEquals("zstd", yaml.getString("variables.compression"));
        assertEquals(List.of("a", "b"), yaml.getStringList("custom"));
        assertEquals(1, yaml.getInt("nested.deeper.value"));
        assertEquals(List.of(List.of(1, 2), Map.of("x", "y")), yaml.getList("lists"));
        ConfigLoader.Result<NeoSkriptConfig> again = load();
        assertEquals(List.of(), again.notices());
        assertEquals(text, Files.readString(file()));
    }

    // --- Empty settings ---

    @Test
    void onlyThePrefixMayBeLeftEmpty() throws Exception {
        load();
        Files.writeString(file(), Files.readString(file())
                .replace("  token: \"!\"", "  token: \"\"")
                .replace("  base: \"<gray>\"", "  base:"));
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals("!", result.config().effectCommands().token());
        assertEquals(NeoSkriptConfig.DEFAULTS.theme().base(), result.config().theme().base());
        assertEquals(List.of(
                "theme.base is empty; quote values that start with #; using the default.",
                "effect-commands.token (\"\") is empty; using the default."), result.warnings());
    }

    @Test
    void anEmptyFileIsNotCarriedForward() throws Exception {
        Files.writeString(file(), version1().replace("  file: variables.csv", "  file:"));
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals("variables.csv", defaultFile(result.config()));
        assertTrue(Files.readString(file()).contains("  file: \"variables.csv\"\n"));
    }

    @Test
    void anEmptySettingIsFoundAfterAMigrationMovesIt() throws Exception {
        // Version 1 called the setting "name"; version 2 calls it "title", which may be empty.
        ConfigSpec<Renamed> spec = ConfigSpec.builder(Renamed.class, new Renamed("Untitled", 1))
                .version(2)
                .unversioned(1)
                .migrate(1, root -> {
                    if (root.contains("name")) {
                        root.set("title", root.get("name"));
                        root.set("name", null);
                    }
                })
                .build();
        Files.writeString(file(), "name:\ncount: 3\n");
        ConfigLoader.Result<Renamed> result = ConfigLoader.load(spec, file());
        assertEquals(new Renamed("", 3), result.config());
        assertEquals(List.of(), result.warnings());
        assertTrue(Files.readString(file()).contains("title: \"\"\n"), Files.readString(file()));
    }

    public record Renamed(@AllowBlank String title, int count) {}

    /** The file of the first database. */
    private static String defaultFile(NeoSkriptConfig config) {
        return config.databases().all().getFirst().file();
    }

    // --- Setting values copied from Skript ---

    @Test
    void settingValuesInACurrentFileKeepsItsCommentsAndOtherKeys() throws Exception {
        load();
        Files.writeString(file(), Files.readString(file()).replace("metrics: true\n", "# Mine.\nmetrics: true\nmine: 1\n"));
        ConfigLoader.Change change = ConfigLoader.set(NeoSkriptConfig.SPEC, file(), Map.of("effect-commands.enabled", true));
        assertTrue(change.written());
        String text = Files.readString(file());
        assertTrue(text.contains("# Mine.\nmetrics: true\nmine: 1\n"), text);
        assertTrue(load().config().effectCommands().enabled());
        assertEquals(List.of("config.yml"), names());
    }

    @Test
    void settingValuesInAnOldFileBacksItUpFirst() throws Exception {
        Files.writeString(file(), version1() + "mine: 1\n");
        ConfigLoader.Change change = ConfigLoader.set(NeoSkriptConfig.SPEC, file(), Map.of("effect-commands.token", "?"));
        assertTrue(change.written());
        assertTrue(Files.exists(folder.resolve("config.yml.bak-v1")));
        assertEquals("?", load().config().effectCommands().token());
        assertTrue(Files.readString(file()).contains("\nmine: 1\n"));
    }

    // --- What changed meaning, on purpose ---

    @Test
    void aQuotedFalseTurnsMetricsOff() throws Exception {
        // Bukkit's getBoolean read "false" in quotes as not a boolean, so as the default: on.
        Files.writeString(file(), version1().replace("metrics: true", "metrics: \"false\""));
        assertFalse(load().config().metrics());
    }

    @Test
    void negativeErrorLimitsFallBackToTheDefaults() throws Exception {
        Files.writeString(file(), version1().replace("error-timeout-length: 10", "error-timeout-length: -5"));
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals(10, result.config().runtimeErrors().errorTimeoutLength());
        assertEquals(List.of("runtime-errors.error-timeout-length (\"-5\") must be at least 0; using the default."),
                result.warnings());
    }

    // --- Review 2: what mustn't crash or lose lines ---

    @Test
    void aFileWithManyAliasesLoadsAsBukkitReadsIt() throws Exception {
        load();
        StringBuilder text = new StringBuilder(Files.readString(file())).append("base: &base {k: 1}\nmany:\n");
        for (int i = 0; i < 80; i++) {
            text.append("- *base\n");
        }
        Files.writeString(file(), text.toString());
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals(NeoSkriptConfig.DEFAULTS, result.config());
        assertEquals(List.of(), result.warnings());
    }

    @Test
    void anyParseFailureIsAConfigException() throws Exception {
        // Deeper than YAML's nesting limit, which Bukkit keeps.
        String deep = "x: " + "[".repeat(500) + "]".repeat(500) + "\n";
        Files.writeString(file(), deep);
        ConfigException e = assertThrows(ConfigException.class, this::load);
        assertTrue(e.getMessage().startsWith("config.yml isn't valid YAML: "), e.getMessage());
        assertEquals(deep, Files.readString(file()));
    }

    @Test
    void aStyleThatCannotBeParsedFallsBack() throws Exception {
        load();
        String nested = "<bold>".repeat(20_000);
        Files.writeString(file(), Files.readString(file()).replace("  highlight: \"<#FF4D9D>\"", "  highlight: \"" + nested + "\""));
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals(NeoSkriptConfig.DEFAULTS.theme().highlight(), result.config().theme().highlight());
        assertTrue(result.warnings().getFirst().startsWith("theme.highlight (\"<bold><bold>"), result.warnings().toString());
    }

    @Test
    void aPrefixThatCannotBeParsedOrSentFallsBack() throws Exception {
        load();
        String original = Files.readString(file());
        for (String prefix : new String[] {"<bold>".repeat(20_000) + "x", "<red>x<blue>x".repeat(20)}) {
            // A stack overflow while parsing, and tags nested 40 deep.
            Files.writeString(file(), original.replaceFirst("  prefix: \".*\"\n", "  prefix: \"" + prefix + "\"\n"));
            ConfigLoader.Result<NeoSkriptConfig> result = load();
            assertEquals(NeoSkriptConfig.DEFAULTS.theme(), result.config().theme());
            assertEquals(1, result.warnings().size(), result.warnings().toString());
            assertTrue(result.warnings().getFirst().startsWith("theme: the prefix "), result.warnings().toString());
        }
    }

    @Test
    void emptyKeysKeepTheirLinesWhenTheFileIsRewritten() throws Exception {
        load();
        String text = Files.readString(file())
                .replace("  highlight: \"<#FF4D9D>\"", "  highlight: #FF4D9D")
                .replaceFirst("  prefix: \".*\"\n", "  prefix:\n")
                .replace("metrics: true\n", "metrics: true\nmine: ~\n")
                .replaceAll("(?s)\neffect-commands:.*", "\n");
        Files.writeString(file(), text);
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertEquals(List.of("Added effect-commands with its default."), result.notices());
        assertEquals("", result.config().theme().prefix());
        String after = Files.readString(file());
        // In place, with the comment YAML read the colour as.
        assertTrue(after.contains("  # <highlight>: names and values, such as a script or a number.\n  highlight: # FF4D9D\n"),
                after);
        assertTrue(after.contains("\n  prefix:\n"), after);
        assertTrue(after.contains("\nmetrics: true\nmine:\n"), after);
        assertFalse(after.contains(Reader_BLANK), after);

        // Setting a value keeps them too.
        ConfigLoader.set(NeoSkriptConfig.SPEC, file(), Map.of("metrics", false));
        String set = Files.readString(file());
        assertTrue(set.contains("  highlight: # FF4D9D\n"), set);
        assertTrue(set.contains("\n  prefix:\n"), set);
        assertTrue(set.contains("\nmetrics: false\nmine:\n"), set);
    }

    private static final String Reader_BLANK = "neoskript-blank";

    @Test
    void carryingForwardKeepsEmptyKeysThatAreNotSettings() throws Exception {
        Files.writeString(file(), version1() + "foo:\nchar: ~ # mine\n");
        ConfigLoader.Result<NeoSkriptConfig> result = load();
        assertTrue(result.notices().getLast().startsWith("Kept foo, char, which aren't NeoSkript's"), result.notices().toString());
        String text = Files.readString(file());
        assertTrue(text.contains("\nfoo:\n"), text);
        assertTrue(text.contains("\nchar: # mine\n"), text);
        assertFalse(text.contains(Reader_BLANK), text);
        assertEquals(List.of(), load().notices());
    }

    @Test
    void carryingForwardKeepsEmptyKeysInListsOfMaps() throws Exception {
        Files.writeString(file(), version1() + "stuff:\n- a:\n  b: 1\n");
        load();
        String text = Files.readString(file());
        assertFalse(text.contains(Reader_BLANK), text);
        assertTrue(text.contains("a: null") || text.contains("a:\n") || text.contains("a: ~"), text);
        assertTrue(text.contains("b: 1"), text);
    }

    @Test
    void recoverTurnsItselfOffKeepingTheFileAndLeavesANewerFileAlone() throws Exception {
        String text = ConfigLoader.defaultText(NeoSkriptConfig.SPEC)
                .replace("  recover: false\n", "  # Mine: turned on after the disk failed.\n  recover: true\n")
                .replace("metrics: true", "metrics: false");
        Files.writeString(file(), text);
        assertTrue(load().config().variables().recover());
        ConfigLoader.Change change = NeoSkriptConfig.turnOffRecover(file());
        assertTrue(change.written());
        String after = Files.readString(file());
        assertTrue(after.contains("  # Mine: turned on after the disk failed.\n  recover: false\n"), after);
        NeoSkriptConfig config = load().config();
        assertFalse(config.variables().recover());
        assertFalse(config.metrics(), "the other settings are kept");

        String newer = "config-version: 4\nvariables:\n  recover: true\n";
        Files.writeString(file(), newer);
        assertFalse(NeoSkriptConfig.turnOffRecover(file()).written());
        assertEquals(newer, Files.readString(file()));
    }

    @Test
    void theDocsShowTheDefaultConfig() throws IOException {
        Path page = Path.of("../website/src/content/docs/docs/server/configuration.mdx");
        String docs = Files.readString(page);
        String config = ConfigLoader.defaultText(NeoSkriptConfig.SPEC);
        assertTrue(docs.contains("```yaml title=\"plugins/NeoSkript/config.yml\"\n" + config + "```"),
                "The full default config in " + page + " is out of date. It should be:\n" + config);
    }
}
