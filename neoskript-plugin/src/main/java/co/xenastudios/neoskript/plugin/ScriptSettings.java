/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.paper.StandardLibrary;
import co.xenastudios.neoskript.paper.TextComponentParser;
import co.xenastudios.neoskript.runtime.Language;
import co.xenastudios.neoskript.runtime.Settings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hands config.yml's settings that change how scripts behave to the runtime and standard library: {@code text},
 * {@code syntax}, {@code language} and the player variable fix. They apply at once, when the server starts and on
 * {@code /sk reload config}.
 */
final class ScriptSettings {

    private ScriptSettings() {}

    /**
     * Applies {@code config}'s settings for scripts, reading language files from {@code langFolder}. Returns what
     * couldn't be used, such as a language with no file; those settings stay English or as they were.
     */
    static List<String> apply(NeoSkriptConfig config, Path langFolder) {
        List<String> warnings = new ArrayList<>();
        NeoSkriptConfig.Text text = config.text();
        Settings.numberAccuracy(text.numberAccuracy());
        Settings.caseSensitive(text.caseSensitive());
        // Checked when config.yml was read.
        Settings.dateFormat(text.dateFormat().text());
        TextComponentParser parser = TextComponentParser.instance();
        parser.colorsCauseReset(text.colorCodesResetFormatting());
        parser.safeTags(text.safeTags());
        parser.linkParseMode(text.parseLinks().mode());
        Settings.targetBlockDistance(config.syntax().targetBlockDistance());
        Settings.playerNamePattern(config.syntax().playerNamePattern().pattern());
        StandardLibrary.playerVariableFix(config.variables().playerVariableFix());
        language(config.language(), langFolder, warnings);
        return warnings;
    }

    private static void language(String name, Path folder, List<String> warnings) {
        try {
            Map<String, String> messages = LanguageFiles.load(name, folder);
            if (messages == null) {
                warnings.add("language: there's no language file for '%s', in NeoSkript or in plugins/NeoSkript/lang;"
                        .formatted(name) + " using english.");
                Language.use(english(folder));
                return;
            }
            Language.use(messages);
        } catch (IOException | RuntimeException e) {
            warnings.add("language: couldn't read the language file for '%s': %s; using english.".formatted(name, e));
            Language.use(Language.ENGLISH);
        }
    }

    private static Map<String, String> english(Path folder) throws IOException {
        Map<String, String> english = LanguageFiles.load("english", folder);
        return english == null ? Language.ENGLISH : english;
    }
}
