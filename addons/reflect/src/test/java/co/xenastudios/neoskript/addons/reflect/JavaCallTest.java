/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** skript-reflect's Java calls, compiled and run as on a server. */
class JavaCallTest {

    private final ScriptHarness harness = new ScriptHarness();

    @Test
    void callsStaticAndInstanceMethodsAndFields() {
        List<String> said = harness.run("""
                import:
                \tjava.lang.Math
                \tjava.util.ArrayList
                \tjava.lang.Integer

                on load:
                \tsay Math.max(3, 7)
                \tsay Math.max[int, int](0, 4)
                \tset {_list} to new ArrayList()
                \t{_list}.add("a")
                \t{_list}.add("b")
                \tsay {_list}.size()
                \tsay {_list}.get(1)
                \tsay {_list}.size() + 1
                \tsay Integer.MAX_VALUE
                \tif {_list}.contains("a"):
                \t\tsay "contains"
                \tif {_list}.isEmpty():
                \t\tsay "empty"
                """);
        assertEquals(List.of("7", "4", "2", "b", "3", "2147483647", "contains"), said);
        assertEquals(List.of(), harness.warnings);
    }

    @Test
    void aCallThatFailsIsSkriptReflectsWarningAndTheTriggerCarriesOn() {
        List<String> said = harness.run("""
                import:
                \tjava.util.ArrayList

                on load:
                \tset {_list} to new ArrayList()
                \t{_list}.sise()
                \tsay {_list}.get(5)
                \tsay "still here"
                \ttry {_list}.get(5)
                \tsay "%the last java error%"
                """);
        // The failed call has no value.
        assertEquals(List.of("<none>", "still here"), said.subList(0, 2));
        assertTrue(said.get(2).contains("IndexOutOfBoundsException"), said.toString());
        assertEquals(2, harness.warnings.size(), harness.warnings.toString());
        assertTrue(harness.warnings.get(0).startsWith("No matching non-static method: ArrayList#sise called without "
                + "arguments\nDid you misspell the method? You may have meant to type one of the following:\n"
                + "* %ArrayList%.size"), harness.warnings.get(0));
        assertTrue(harness.warnings.get(1).startsWith("method ArrayList#get called with (5 (Long)) threw a "
                + "IndexOutOfBoundsException: "), harness.warnings.get(1));
    }

    @Test
    void importsAreEachScriptsOwn() {
        List<String> problems = harness.load(
                "a.sk", "import:\n\tjava.lang.Math\n\non load:\n\tsay Math.abs(-2)\n",
                "b.sk", "on load:\n\tsay Math.abs(-2)\n");
        // a.sk's call compiles; b.sk, which imports nothing, can't read Math.
        assertTrue(problems.stream().noneMatch(p -> p.startsWith("ERROR 4:") || p.startsWith("ERROR 3:")),
                problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("ERROR 1:") || p.startsWith("ERROR 2:")),
                problems.toString());
    }

    @Test
    void importLinesHaveSkriptReflectsMessages() {
        List<String> problems = harness.load("test.sk", """
                import:
                \tjava.lang.Nope
                \tjava.lang.String as JavaString
                \tjava.lang.Math as Math
                \tnot valid!
                """);
        assertEquals(List.of(
                "ERROR 2: java.lang.Nope refers to a non-existent class.",
                "WARNING 4: java.lang.Math doesn't need the alias Math, as it will already be imported under that name",
                "ERROR 5: not valid! is an invalid import statement."), problems);
    }

    @Test
    void arraysCollectSpreadAndInstanceOf() {
        List<String> said = harness.run("""
                import:
                \tjava.util.ArrayList
                \tjava.util.List

                on load:
                \tset {_a} to new int[3]
                \tset {_a}[1] to 5
                \tsay {_a}[1]
                \tsay {_a}
                \tset {_list} to new ArrayList()
                \t{_list}.add("x")
                \t{_list}.add("y")
                \tsay ...{_list}
                \tsay [1, 2, 3]
                \tif {_list} is an instance of List:
                \t\tsay "a list"
                \tsay null
                """);
        assertEquals(List.of("5", "[0, 5, 0]", "x and y", "[1, 2, 3]", "a list", "null"), said);
    }

    @Test
    void theCallSiteKeepsWhatItChose() throws Throwable {
        CallSite site = new CallSite(CallSite.Kind.METHOD);
        Descriptor max = new Descriptor(Math.class, "max", null);
        CallSite.Target first = site.find(max, Math.class, true, new Object[] {1L, 2L}, false);
        CallSite.Target again = site.find(max, Math.class, true, new Object[] {5L, 9L}, false);
        assertTrue(first == again, "the same arguments' classes find the same handle");
        assertEquals(9L, CallSite.call(again, null, true, new Object[] {5L, 9L}));
    }

    @Test
    void callsInTextsAndNoListWarnings() {
        List<String> problems = harness.load("test.sk", """
                import:
                \tjava.lang.Math

                on load:
                \tsay "%Math.abs(-2)%"
                \tsay Math.max(3, 7)
                \tsay "max int: %Math.max[int, int](3, 7)%"
                """);
        assertEquals(List.of(), problems);
        harness.run();
        assertEquals(List.of("2", "7", "max int: 7"), harness.said);
    }

    @Test
    void aClassNoOtherTypeKnowsIsStillACallBeforeTo() {
        // 'Math' is a word only an import makes sense of: the line is the call and 'to', not one unparsed literal.
        List<String> problems = harness.load("test.sk", """
                import:
                \tjava.lang.Math

                on load:
                \tsay "pi: %Math.PI%" to "console"
                \tsay Math.abs(-2) to "console"
                """);
        assertEquals(List.of(), problems);
        harness.run();
        assertEquals(List.of("pi: 3.14 to console", "2 to console"), harness.said);
    }

    @Test
    void neoSkriptsOwnValuesConvertWhereNothingElseFits() {
        List<String> said = harness.run("""
                import:
                \tjava.time.Duration

                on load:
                \tsay Duration.ofSeconds(1).plus(5 seconds).getSeconds()
                \tsay Duration.ofSeconds(1).plus(Duration.ofSeconds(2)).getSeconds()
                """);
        assertEquals(List.of("6", "3"), said);
    }

    @Test
    void aClassNamedLikeASkriptTypeIsTheImport() {
        // 'Location' is also Skript's location type and the event's location: where a class goes, it's the import.
        List<String> said = harness.run("""
                import:
                \torg.bukkit.Location

                on load:
                \tset {_location} to new Location(null, 1, 2, 3)
                \tsay {_location}.getY()
                """);
        assertEquals(List.of("2"), said);
    }

    @Test
    void aJavaEventIsReadFromItsHeader() {
        List<String> problems = harness.load("test.sk", """
                import:
                \torg.bukkit.event.server.ServerCommandEvent
                \torg.bukkit.event.player.PlayerEvent
                \tjava.util.ArrayList

                on ServerCommandEvent:
                \tsay event.getCommand()

                on ArrayList:
                \tsay 1

                on PlayerEvent:
                \tsay 1
                """);
        assertEquals(List.of("ERROR 9: ArrayList is not an event class", "ERROR 12: PlayerEvent can't be listened to, as it"
                + " has no handler list: use one of its subclasses"), problems);
    }

    @Test
    void importsCountForTheWholeScriptWhereverTheyAre() {
        List<String> said = harness.run("""
                on load:
                \tsay Math.abs(-4)

                import:
                \tjava.lang.Math
                """);
        assertEquals(List.of("4"), said);
    }
}
