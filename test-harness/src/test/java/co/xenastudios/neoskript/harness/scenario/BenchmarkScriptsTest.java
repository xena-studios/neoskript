/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BenchmarkScriptsTest {

    @Test
    void generatesTheSameScriptsEveryTime() {
        Map<String, String> scripts = BenchmarkScripts.generate();
        assertEquals(BenchmarkScripts.FILES, scripts.size());
        assertEquals(scripts, BenchmarkScripts.generate());
        scripts.values().forEach(script -> assertFalse(script.contains("@F@") || script.contains("@N@")));
    }

    @Test
    void median() {
        assertEquals(3, SkriptLoadBenchmark.median(List.of(5L, 1L, 3L)));
        assertEquals(2, SkriptLoadBenchmark.median(List.of(4L, 1L, 3L, 1L)));
    }
}
