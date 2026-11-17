/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AliasesNextToTest {

    @TempDir
    Path plugins;

    @Test
    void findsTheAliasesBesideTheScriptsFolderFromAnywhereInIt() throws Exception {
        Path skript = plugins.resolve("Skript");
        Files.createDirectories(skript.resolve("scripts/games/sub"));
        Files.createDirectories(skript.resolve("aliases"));
        Path script = Files.writeString(skript.resolve("scripts/games/sub/x.sk"), "on load:\n\tstop\n");
        assertEquals(skript.resolve("aliases"), NeoSkriptCli.aliasesNextTo(skript.resolve("scripts")));
        assertEquals(skript.resolve("aliases"), NeoSkriptCli.aliasesNextTo(skript.resolve("scripts/games")));
        assertEquals(skript.resolve("aliases"), NeoSkriptCli.aliasesNextTo(script));
    }

    @Test
    void findsNothingWithoutOne() throws Exception {
        Path folder = plugins.resolve("loose");
        Files.createDirectories(folder);
        assertNull(NeoSkriptCli.aliasesNextTo(Files.writeString(folder.resolve("x.sk"), "")));
    }

    @Test
    void readsLoadDefaultAliasesFromConfigSk() throws Exception {
        Path config = plugins.resolve("config.sk");
        assertTrue(NeoSkriptCli.defaultAliases(config));
        Files.writeString(config, "load default aliases: false # our own\n");
        assertFalse(NeoSkriptCli.defaultAliases(config));
        Files.writeString(config, "load default aliases: true\n");
        assertTrue(NeoSkriptCli.defaultAliases(config));
    }
}
