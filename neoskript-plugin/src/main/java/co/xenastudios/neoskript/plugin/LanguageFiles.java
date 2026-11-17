/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Skript's language files, for config.yml's {@code language} (Skript's {@code language}): {@code english},
 * {@code german} and the others Skript ships, in NeoSkript's jar (GPL, from Skript 2.17.0), and any in
 * {@code plugins/NeoSkript/lang}, whose entries replace the jar's, as Skript's {@code plugins/Skript/lang} does.
 */
final class LanguageFiles {

    /** The languages Skript 2.17.0 ships, which NeoSkript's jar has too. */
    static final List<String> SHIPPED = List.of("catalan", "dutch", "english", "french", "german", "japanese",
            "korean", "polish", "russian", "simplifiedchinese", "spanish", "turkish");

    private LanguageFiles() {}

    /**
     * The entries of language {@code name}, by their path ({@code commands.cooldown message}), or {@code null} if
     * there's no such language, in the jar or in {@code folder}.
     */
    static Map<String, String> load(String name, Path folder) throws IOException {
        String file = name.toLowerCase(Locale.ENGLISH) + ".lang";
        Map<String, String> entries = new HashMap<>();
        boolean found = false;
        if (SHIPPED.contains(name.toLowerCase(Locale.ENGLISH))) {
            try (InputStream in = LanguageFiles.class.getResourceAsStream("/lang/" + file)) {
                if (in != null) {
                    entries.putAll(SkriptConfigFile.read(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                    found = true;
                }
            }
        }
        // A name like '../x' can't reach outside the folder.
        Path own = folder == null || !file.matches("[\\w.-]+") ? null : folder.resolve(file);
        if (own != null && Files.isRegularFile(own)) {
            entries.putAll(SkriptConfigFile.read(SkriptConfigFile.text(own)));
            found = true;
        }
        return found && !entries.isEmpty() ? entries : null;
    }
}
