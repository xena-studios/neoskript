/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HarnessMainTest {

    @Test
    void parsesOptionsAndDropsEmptyValues() {
        Map<String, String> options = HarnessMain.parseOptions(
                new String[] {"--env", "paper-26.3", "--group", "baseline", "--scenario", ""});
        assertEquals(Map.of("env", "paper-26.3", "group", "baseline"), options);
    }

    @Test
    void rejectsStrayArguments() {
        assertThrows(IllegalArgumentException.class, () -> HarnessMain.parseOptions(new String[] {"paper"}));
    }

    @Test
    void scenarioNamesAreUnique() {
        assertEquals(
                HarnessMain.SCENARIOS.size(),
                HarnessMain.SCENARIOS.stream().map(Scenario::name).distinct().count());
    }
}
