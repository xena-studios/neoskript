/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.harness.scenario.DifferentialScenario.Answer;
import co.xenastudios.neoskript.harness.scenario.DifferentialScenario.Variation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The answers recorded for units that vary in Skript: they count only for the script they were recorded for. */
class DifferentialVariationsTest {

    private static final String SCRIPT = "on join:\n\tset the highest solid block at the player's location to x\n";
    private static final Answer ERROR = new Answer(List.of("2 INFO: x can't be set"), null);
    private static final Answer NONE = new Answer(List.of(), null);

    @Test
    void aRecordedAnswerCountsForTheSameScript() {
        Variation variation = new Variation(DifferentialScenario.scriptHash(SCRIPT), List.of(ERROR, NONE));
        List<Answer> answers = DifferentialScenario.recordedAnswers(variation, DifferentialScenario.scriptHash(SCRIPT));
        assertTrue(DifferentialScenario.matchesAny(answers, List.of(), null));
        assertTrue(DifferentialScenario.matchesAny(answers, List.of("2 INFO: x can't be set"), null));
        assertFalse(DifferentialScenario.matchesAny(answers, List.of("2 INFO: something else"), null));
    }

    @Test
    void aCaptureCutShortIsNeverSkriptsAnswer() {
        Answer cut = new Answer(List.of("2 INFO: "), null);
        assertFalse(DifferentialScenario.recordable(cut));
        Variation variation = new Variation(DifferentialScenario.scriptHash(SCRIPT), List.of(ERROR, cut));
        assertEquals(List.of(ERROR), DifferentialScenario.recordedAnswers(variation, DifferentialScenario.scriptHash(SCRIPT)));
        assertTrue(DifferentialScenario.recordable(NONE));
    }

    @Test
    void notOnceTheScriptChanges() {
        Variation variation = new Variation(DifferentialScenario.scriptHash(SCRIPT), List.of(ERROR, NONE));
        assertEquals(List.of(), DifferentialScenario.recordedAnswers(variation,
                DifferentialScenario.scriptHash(SCRIPT + "\tbroadcast \"x\"\n")));
    }

    @Test
    void norForUnitsNeverSeenToVary() {
        assertEquals(List.of(), DifferentialScenario.recordedAnswers(null, DifferentialScenario.scriptHash(SCRIPT)));
    }

    @Test
    void aLineFromAnotherThreadBeforeAMessageIsSkipped() {
        List<String> console = List.of(
                "[10:00:00 INFO]: [Skript] Line 2: (examples/Example-1.sk)",
                "[10:00:00 WARN]: Something else the server said",
                "[10:00:00 INFO]:     Can't understand this section: on unknown command execution",
                "[10:00:00 INFO]:     Line: on unknown command execution:");
        assertEquals(List.of("2 INFO: Can't understand this section: on unknown command execution"),
                DifferentialScenario.messages(console).get("examples/Example-1"));
    }
}
