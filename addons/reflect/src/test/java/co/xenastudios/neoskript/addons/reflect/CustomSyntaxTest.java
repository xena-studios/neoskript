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

    @Test
    void reloadingTheDeclaringScriptAloneLetsGoOfItsOldSyntax() {
        String use = "on load:\n\tshout \"hi\"\n";
        assertEquals(List.of(), harness.load("defs.sk", "effect shout %string%:\n\ttrigger:\n\t\tsay \"one %expr-1%\"\n",
                "use.sk", use));
        List<co.xenastudios.neoskript.compiler.CompiledScripts.CompiledTrigger> oldUse = harness.triggers().stream()
                .filter(trigger -> trigger.script().equals("use.sk")).toList();
        // Only defs.sk switches, its effect renamed: use.sk keeps its old code, as after '/sk reload defs.sk'.
        harness.switching = java.util.Set.of("defs.sk");
        harness.load("defs.sk", "effect yell %string%:\n\ttrigger:\n\t\tsay \"two %expr-1%\"\n", "use.sk", use);
        assertEquals(List.of("use.sk still uses the custom syntax 'effect shout %string% (defs.sk)', which defs.sk no"
                + " longer declares: reload it"), harness.switchWarnings);
        harness.run(oldUse);
        // The old code is gone: the use says so rather than running it.
        assertEquals(List.of(), harness.said);
        org.junit.jupiter.api.Assertions.assertTrue(harness.warnings.stream().anyMatch(w -> w.contains(
                "Couldn't resolve call for 'custom syntax: effect shout %string% (defs.sk)'")), harness.warnings.toString());
    }

    /** The event of {@code apart values:}'s code, as a spawn section's code is in a spawn event. */
    record ApartEvent(Object value) {}

    @Test
    void continueInASectionLoadedApartIsSkriptsNotTheDeclaredSyntaxs() {
        // A section whose code is in an event of its own, as Skript loads a spawn section's (one needs a server's
        // registries here), so 'continue' there isn't skript-reflect's: with no loop in that code, it's Skript's error.
        harness.library.section("TestSecApart", "string", info -> (context, args) -> new Object[] {"v"}, "apart values");
        harness.library.sectionEvent("TestSecApart", "apart", ApartEvent.class, ApartEvent::new);
        List<String> problems = harness.load("defs.sk", """
                effect frob:
                \ttrigger:
                \t\tapart values:
                \t\t\tcontinue

                on load:
                \tfrob
                """);
        assertEquals(List.of("ERROR 4: The 'continue' effect may only be used in loops"), problems);
    }

    @Test
    void aDeclarationWithoutItsCodeDeclaresNothing() {
        List<String> problems = harness.load("defs.sk", """
                effect nobody here:
                \treturn type: string

                on load:
                \tnobody here
                """);
        assertEquals(List.of("ERROR 1: Required entry 'trigger' is missing",
                "ERROR 5: Can't understand this condition/effect: nobody here"), problems);
    }

    @Test
    void aTypeNoOneKnowsIsAnyValueWithSkriptReflectsWarning() {
        List<String> problems = harness.load("defs.sk", """
                effect frob %nosuchtype%:
                \ttrigger:
                \t\tsay "frob %expr-1%"

                on load:
                \tfrob "x"
                """);
        assertEquals(List.of("WARNING 1: 'nosuchtype' is not a valid Skript type. Using 'object' instead."), problems);
        harness.run();
        assertEquals(List.of("frob x"), harness.said);
    }

    @Test
    void customSyntaxValuesOnlyInCustomSyntax() {
        List<String> problems = harness.load("plain.sk", """
                on load:
                \tsay matched pattern
                \tsay parser mark
                \tsay expression-1
                \tsay parse tags
                """);
        assertEquals(List.of("ERROR 2: The matched pattern may only be used in custom syntax.",
                "ERROR 3: The parse mark may only be used in custom syntax",
                "ERROR 4: The expression 'expression' may only be used in a custom syntax structure",
                "ERROR 5: The parse tags may only be used in custom syntax"), problems);
    }

    @Test
    void declaredSyntaxWorksInEffectCommands() {
        assertEquals(List.of(), harness.load("defs.sk", "effect shout %string%:\n\ttrigger:\n\t\tsay \"shout %expr-1%\"\n"));
        assertEquals(List.of(), harness.effectCommand("shout \"hi\""));
        assertEquals(List.of("shout hi"), harness.said);
    }
}
