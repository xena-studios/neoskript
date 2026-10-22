/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusRunnerTest {

    @Test
    void findsScriptsInOrder(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("warps.sk"), "");
        Files.writeString(dir.resolve("join-leave.sk"), "");
        Files.writeString(dir.resolve("notes.txt"), "");
        List<String> suites = CorpusRunner.scripts(dir).stream().map(CorpusRunner::suiteName).toList();
        assertEquals(List.of("join-leave", "warps"), suites);
    }

    @Test
    void generatesOneSelftestCallAndReportPerScript() {
        String runner = CorpusRunner.generate(List.of("join-leave", "warps"));
        assertTrue(runner.contains("\t\tselftest_join_leave()\n\t\tcorpus_report(\"join-leave\")\n"));
        assertTrue(runner.contains("\t\tselftest_warps()\n\t\tcorpus_report(\"warps\")\n"));
        assertTrue(runner.endsWith("\t\tsend \"[corpus] done\" to console\n"));
    }
}
