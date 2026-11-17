/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CustomSyntaxTest {

    private final ScriptHarness harness = new ScriptHarness();

    @Test
    void effectsConditionsAndExpressionsDeclaredInAScript() {
        List<String> problems = harness.load("defs.sk", """
                effect shout %string%:
                \ttrigger:
                \t\tsay "shout: %expression-1%"

                condition %number% clears the bar:
                \tcheck:
                \t\tif expression-1 > 10:
                \t\t\tcontinue

                expression doubled %number%:
                \tget:
                \t\treturn expression-1 * 2

                effect:
                \tpatterns:
                \t\tgreet %string%
                \t\twave at %string%
                \ttrigger:
                \t\tsay "pattern %matched pattern% %expr-1%"

                numbers property twice:
                \tget:
                \t\treturn expression-1 * 2
                """, "uses.sk", """
                on load:
                \tshout "hi"
                \tif 20 clears the bar:
                \t\tsay "big"
                \tif 5 clears the bar:
                \t\tsay "wrong"
                \tsay doubled 4
                \tgreet "a"
                \twave at "b"
                \tsay twice of 3
                \tsay (1 and 2)'s twice
                """);
        assertEquals(List.of(), problems);
        harness.run();
        assertEquals(List.of("shout: hi", "big", "8", "pattern 1 a", "pattern 2 b", "6", "2 and 4"), harness.said);
    }

    @Test
    void localSyntaxIsOnlyItsScripts() {
        List<String> problems = harness.load("a.sk", """
                local effect ping:
                \ttrigger:
                \t\tsay "pong"

                on load:
                \tping
                """, "b.sk", """
                on load:
                \tping
                """);
        assertEquals(1, problems.size(), problems.toString());
        harness.run();
        assertEquals(List.of("pong"), harness.said);
    }

    @Test
    void scriptsWithoutCustomSyntaxParseAsBefore() {
        List<String> problems = harness.load("plain.sk", """
                on load:
                \tsay "plain"
                """);
        assertEquals(List.of(), problems);
        harness.run();
        assertEquals(List.of("plain"), harness.said);
    }

    @Test
    void aScriptsSyntaxGoesAndChangesWithIt() {
        String use = "on load:\n\tshout \"hi\"\n";
        assertEquals(List.of(), harness.load("defs.sk", "effect shout %string%:\n\ttrigger:\n\t\tsay \"one %expr-1%\"\n",
                "use.sk", use));
        harness.run();
        // Changed: the new code runs.
        assertEquals(List.of(), harness.load("defs.sk", "effect shout %string%:\n\ttrigger:\n\t\tsay \"two %expr-1%\"\n",
                "use.sk", use));
        harness.run();
        // Gone: nothing knows the line any more.
        List<String> problems = harness.load("use.sk", use);
        assertEquals(List.of("ERROR 2: Can't understand this condition/effect: shout \"hi\""), problems);
        assertEquals(List.of("one hi", "two hi"), harness.said);
    }

    @Test
    void skriptReflectsMessagesForPatterns() {
        List<String> problems = harness.load("defs.sk", """
                effect:
                \ttrigger:
                \t\tsay "x"

                effect wave:
                \tpatterns:
                \t\twave twice
                \ttrigger:
                \t\tsay "x"
                """);
        assertEquals(List.of("ERROR 1: A custom effect without an inline pattern must have a 'patterns' entry",
                "ERROR 5: A custom effect with an inline pattern cannot have a 'patterns' entry too"), problems);
    }
}
