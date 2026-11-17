/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.frontend.stub.SkriptRegistry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WarningSettingsTest {

    private static final String SCRIPT = """
            on load:
            \tset {%now%::x} to 1
            \tset {a:b} to 2
            \tset {_list::*} to 1, 2, 3
            """;

    @AfterEach
    void defaults() {
        WarningSettings.disable(Set.of());
        WarningSettings.longParseTime(0, millis -> millis + " milliseconds");
        WarningSettings.clock = System::currentTimeMillis;
    }

    private static List<String> warnings(String text) {
        ParsedScript script = new ScriptLoader(SkriptRegistry.get()).load(List.of(new SourceFile("test.sk", text)))
                .getFirst();
        return script.diagnostics().stream().map(d -> d.line() + ": " + d.message().split("[.(]")[0]).toList();
    }

    @Test
    void warningsCanBeTurnedOffForEveryScript() {
        List<String> all = warnings(SCRIPT);
        assertEquals(3, all.size(), all.toString());
        WarningSettings.disable(Set.of(WarningSettings.SINGLE_COLON, WarningSettings.STARTS_WITH_EXPRESSION,
                WarningSettings.MISSING_AND_OR));
        assertEquals(List.of(), warnings(SCRIPT));
        WarningSettings.disable(Set.of(WarningSettings.SINGLE_COLON));
        assertEquals(2, warnings(SCRIPT).size());
    }

    @Test
    void aLineThatTakesLongToParseIsWarnedAbout() {
        long[] now = {0};
        // Each line takes 3 seconds by this clock.
        WarningSettings.clock = () -> now[0] += 3000;
        WarningSettings.longParseTime(2000, millis -> millis / 1000 + " seconds");
        assertEquals(List.of("2: The current line took a long time to parse "),
                warnings("on load:\n\tsend \"hi\" to console\n"));
        WarningSettings.longParseTime(5000, millis -> millis + "");
        assertEquals(List.of(), warnings("on load:\n\tsend \"hi\" to console\n"));
        // A line that doesn't parse gets its error, not the warning.
        WarningSettings.longParseTime(1, millis -> millis + "");
        assertEquals(List.of("2: Can't understand this condition/effect: frobnicate"),
                warnings("on load:\n\tfrobnicate\n"));
    }
}
