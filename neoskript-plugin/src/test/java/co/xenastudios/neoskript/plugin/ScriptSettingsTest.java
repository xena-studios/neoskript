/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.plugin.config.ConfigLoader;
import co.xenastudios.neoskript.runtime.Language;
import co.xenastudios.neoskript.runtime.Settings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScriptSettingsTest {

    @TempDir
    Path folder;

    @AfterEach
    void defaults() {
        ScriptSettings.apply(NeoSkriptConfig.DEFAULTS, folder);
    }

    private ConfigLoader.Result<NeoSkriptConfig> load(String from, String to) throws Exception {
        Path file = folder.resolve("config.yml");
        String text = ConfigLoader.defaultText(NeoSkriptConfig.SPEC);
        assertTrue(text.contains(from), from);
        Files.writeString(file, text.replace(from, to));
        return ConfigLoader.load(NeoSkriptConfig.SPEC, file);
    }

    @Test
    void appliesTheTextAndSyntaxSettings() throws Exception {
        NeoSkriptConfig config = load("number-accuracy: 2", "number-accuracy: 4").config();
        assertEquals(List.of(), ScriptSettings.apply(config, folder));
        assertEquals(4, Settings.numberAccuracy());
        config = load("player-name-pattern: \"[a-zA-Z0-9_]{1,16}\"", "player-name-pattern: \"[a-z]+\"").config();
        ScriptSettings.apply(config, folder);
        assertEquals("[a-z]+", Settings.playerNamePattern().pattern());
        assertEquals(2, Settings.numberAccuracy());
    }

    @Test
    void loadsSkriptsLanguages() throws Exception {
        for (String language : LanguageFiles.SHIPPED) {
            assertTrue(LanguageFiles.load(language, folder).containsKey("commands.cooldown message"), language);
        }
        ScriptSettings.apply(load("language: \"english\"", "language: \"German\"").config(), folder);
        assertEquals("Du nutzt diesen Befehl zu häufig, bitte versuche es später erneut",
                Language.get("commands.cooldown message"));
        ScriptSettings.apply(load("language: \"english\"", "language: turkish").config(), folder);
        assertEquals("<tanımsız>", Language.none());
        var problem = new co.xenastudios.neoskript.frontend.source.Diagnostic(
                co.xenastudios.neoskript.frontend.source.Severity.WARNING, "shop.sk", 5, 0, 1, "careful");
        assertTrue(AutoReload.describe(problem, (co.xenastudios.neoskript.frontend.source.SourceFile) null).startsWith("<gold><bold>Satır 5:<gray> (shop.sk)<reset>\n"),
                AutoReload.describe(problem, (co.xenastudios.neoskript.frontend.source.SourceFile) null));
    }

    @Test
    void aLanguageFileInTheFolderAddsToOrChangesALanguage() throws Exception {
        Files.createDirectories(folder.resolve("lang"));
        Files.writeString(folder.resolve("lang/german.lang"), "commands:\n\tcooldown message: Langsam!\n");
        Files.writeString(folder.resolve("lang/pirate.lang"), "none: <naught>\nskript command:\n\treload:\n"
                + "\t\tline details: %s %s %d broken\n");
        ScriptSettings.apply(load("language: \"english\"", "language: german").config(), folder.resolve("lang"));
        assertEquals("Langsam!", Language.get("commands.cooldown message"));
        assertEquals("<none>", Language.none());
        ScriptSettings.apply(load("language: \"english\"", "language: pirate").config(), folder.resolve("lang"));
        assertEquals("<naught>", Language.none());
        // A message whose format is broken is English.
        var problem = new co.xenastudios.neoskript.frontend.source.Diagnostic(
                co.xenastudios.neoskript.frontend.source.Severity.ERROR, "shop.sk", 5, 0, 1, "oops");
        assertTrue(AutoReload.describe(problem, (co.xenastudios.neoskript.frontend.source.SourceFile) null).endsWith("<gold>    Line: <gray><reset>\n <reset>"),
                AutoReload.describe(problem, (co.xenastudios.neoskript.frontend.source.SourceFile) null));
    }

    @Test
    void aLanguageWithNoFileIsEnglishWithAWarning() throws Exception {
        List<String> warnings = ScriptSettings.apply(load("language: \"english\"", "language: \"../../etc/x\"").config(),
                folder);
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains("no language file"), warnings.toString());
        assertEquals("<none>", Language.none());
    }

    @Test
    void anUnreadableLanguageFileIsEnglishWithAWarning() throws Exception {
        Files.createDirectories(folder.resolve("lang"));
        Files.write(folder.resolve("lang/broken.lang"), new byte[] {(byte) 0xff, (byte) 0xfe, 'x'});
        List<String> warnings = ScriptSettings.apply(load("language: \"english\"", "language: broken").config(),
                folder.resolve("lang"));
        assertEquals(1, warnings.size(), warnings.toString());
        assertEquals("<none>", Language.none());
    }

    @Test
    void badValuesFallBackToTheirDefaults() throws Exception {
        record Case(String from, String to) {}
        for (Case bad : List.of(
                new Case("date-format: \"default\"", "date-format: \"yyyy-qq\""),
                new Case("number-accuracy: 2", "number-accuracy: -5000"),
                new Case("number-accuracy: 2", "number-accuracy: 1000000"),
                new Case("number-accuracy: 2", "number-accuracy: lots"),
                new Case("case-sensitive: false", "case-sensitive: [1]"),
                new Case("parse-links: \"disabled\"", "parse-links: sometimes"),
                new Case("target-block-distance: 100", "target-block-distance: -1"),
                new Case("target-block-distance: 100", "target-block-distance: 5000"),
                new Case("player-name-pattern: \"[a-zA-Z0-9_]{1,16}\"", "player-name-pattern: \"[a-z\""),
                new Case("player-variable-fix: true", "player-variable-fix: 7"),
                new Case("language: \"english\"", "language: [a, b]"),
                new Case("verbosity: \"normal\"", "verbosity: shouty"),
                new Case("long-parse-time: \"2 seconds\"", "long-parse-time: forever"),
                new Case("unreachable-code: true", "unreachable-code: sometimes"),
                new Case("vault: true", "vault: 3"),
                new Case("legacy-tab-completion: true", "legacy-tab-completion: []"))) {
            ConfigLoader.Result<NeoSkriptConfig> result = load(bad.from(), bad.to());
            assertEquals(1, result.warnings().size(), bad + ": " + result.warnings());
            assertEquals(NeoSkriptConfig.DEFAULTS, result.config(), bad.toString());
            assertEquals(List.of(), ScriptSettings.apply(result.config(), folder), bad.toString());
        }
    }

    @Test
    void appliesTheWarningsAndVerbosity() throws Exception {
        NeoSkriptConfig config = load("unreachable-code: true", "unreachable-code: false").config();
        ScriptSettings.apply(config, folder);
        assertTrue(co.xenastudios.neoskript.frontend.parse.WarningSettings.disabled("unreachable"));
        assertTrue(!co.xenastudios.neoskript.frontend.parse.WarningSettings.disabled("colon"));
        config = load("verbosity: \"normal\"", "verbosity: very high").config();
        assertEquals(NeoSkriptConfig.Verbosity.VERY_HIGH, config.verbosity());
        ScriptSettings.apply(config, folder);
        assertTrue(Logging.events());
        assertTrue(!co.xenastudios.neoskript.frontend.parse.WarningSettings.disabled("unreachable"));
        ScriptSettings.apply(load("verbosity: \"normal\"", "verbosity: low").config(), folder);
        assertTrue(!Logging.logs(NeoSkriptConfig.Verbosity.NORMAL));
        assertTrue(!Logging.events());
    }

    @Test
    void zeroSecondsTurnsTheLongParseTimeWarningOff() throws Exception {
        NeoSkriptConfig config = load("long-parse-time: \"2 seconds\"", "long-parse-time: 0 seconds").config();
        assertEquals(0, config.warnings().longParseTime().millis());
    }

    @Test
    void parseLinksTakesSkriptsTrueAndFalse() throws Exception {
        assertEquals(NeoSkriptConfig.LinkParsing.LENIENT,
                load("parse-links: \"disabled\"", "parse-links: true").config().text().parseLinks());
        assertEquals(NeoSkriptConfig.LinkParsing.STRICT,
                load("parse-links: \"disabled\"", "parse-links: Strict").config().text().parseLinks());
    }

    @Test
    void oddSafeTagsAreKeptAndHarmless() throws Exception {
        NeoSkriptConfig config = load("safe-tags: [\"color\", \"decorations\", \"gradient\", \"rainbow\", \"reset\", "
                + "\"transition\", \"pride\", \"shadowColor\"]", "safe-tags: [\"\", \"../x\", \"getClass\", bold]").config();
        assertEquals(List.of("", "../x", "getClass", "bold"), config.text().safeTags());
        assertEquals(List.of(), ScriptSettings.apply(config, folder));
    }
}
