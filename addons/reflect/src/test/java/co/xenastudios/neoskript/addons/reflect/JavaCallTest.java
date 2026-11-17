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
                + "arguments\n    Did you misspell the method? You may have meant to type one of the following:\n"
                + "    * %ArrayList%.size"), harness.warnings.get(0));
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

    @Test
    void aMemberChosenForOneValueIsCheckedAgainstTheNext() {
        // A text is a char only if it's one letter: "ab" has the same class as "5", but no isLetter takes it.
        List<String> said = harness.run("""
                import:
                \tjava.lang.Character

                on load:
                \tloop "5", "ab", "x":
                \t\tsay Character.isLetter(loop-value)
                """.replace("\\t", "\t"));
        assertEquals(List.of("false", "<none>", "true"), said);
        assertTrue(harness.warnings.stream().anyMatch(w -> w.contains("No matching static method: Character.isLetter")),
                harness.warnings.toString());
    }

    @Test
    void wholeNumbersFromJavaKeepTheirType() {
        // skript-reflect gives Java's Integer back as it is, so a set of integers contains a list's size.
        List<String> said = harness.run("""
                import:
                \tjava.util.ArrayList
                \tjava.util.stream.IntStream
                \tjava.util.stream.Collectors

                on load:
                \tset {_l} to new ArrayList()
                \t{_l}.add("a")
                \t{_l}.add("b")
                \tset {_s} to IntStream.range(0, 3).boxed().collect(Collectors.toSet())
                \tsay {_s}.contains({_l}.size())
                \tsay {_l}.size()'s class
                \tsay {_l}.size() + 1
                \tif {_l}.size() is 2:
                \t\tsay "compares"
                \tsay "%{_l}.size()% items"
                """);
        assertEquals(List.of("true", "java.lang.Integer", "3", "compares", "2 items"), said);
    }

    @Test
    void aCallOnManyClassesLooksEachUpOnce() {
        List<String> said = harness.run("""
                import:
                \tjava.util.ArrayList
                \tjava.util.LinkedList
                \tjava.util.HashSet
                \tjava.util.TreeSet
                \tjava.util.ArrayDeque
                \tjava.util.LinkedHashSet

                on load:
                \tset {_all::*} to new ArrayList(), new LinkedList(), new HashSet(), new TreeSet(), new ArrayDeque() and new LinkedHashSet()
                \tset {_total} to 0
                \tloop 50 times:
                \t\tloop {_all::*}:
                \t\t\tadd loop-value-2.size() to {_total}
                \tsay {_total}
                """);
        assertEquals(List.of("0"), said);
        long before = CallSite.LOOK_UPS.get();
        harness.run();
        // Six classes, more than a call site keeps itself: each class's size() is still only looked up once.
        assertEquals(before, CallSite.LOOK_UPS.get(), "nothing looked up again");
    }

    @Test
    void aClassWhoseMembersCantBeLookedAtIsAWarningNotAStop() {
        List<String> said = harness.run("""
                on load:
                \tset {_c} to the broken class
                \tsay {_c}.ok()
                \tsay fields of {_c}
                \tsay "after"
                """);
        assertEquals(List.of("<none>", "<none>", "after"), said);
        assertTrue(harness.warnings.stream().anyMatch(w -> w.contains("couldn't be looked up: NoClassDefFoundError")),
                harness.warnings.toString());
    }

    @Test
    void severalLinesOfAWarningAreAllIndented() {
        harness.run("""
                import:
                \tjava.util.ArrayList

                on load:
                \tset {_l} to new ArrayList()
                \ttry {_l}.sise()
                \t{_l}.sise()
                """);
        String warning = harness.warnings.stream().filter(w -> w.contains("sise")).findFirst().orElseThrow();
        assertTrue(warning.contains("\n    Did you misspell the method?"), warning);
        assertTrue(warning.contains("\n    * %ArrayList%.size"), warning);
        assertEquals(1, harness.warnings.stream().filter(w -> w.contains("sise")).count(), "a try says nothing");
    }

    @Test
    void theLastErrorIsEachRunsOwnAndGoesWithIt() throws Exception {
        harness.load("test.sk", """
                import:
                \tjava.util.ArrayList

                on load:
                \tsay last java error
                \tset {_l} to new ArrayList()
                \ttry {_l}.get(3)
                \tsay last java error
                """);
        harness.run();
        harness.run();
        // The second run doesn't see the first's error, and neither keeps it once it's over.
        assertEquals("<none>", harness.said.get(0));
        assertTrue(harness.said.get(1).startsWith("java.lang.IndexOutOfBoundsException"), harness.said.toString());
        assertEquals("<none>", harness.said.get(2));
        long until = System.currentTimeMillis() + 10_000;
        while (LastError.size() > 0 && System.currentTimeMillis() < until) {
            System.gc();
            Thread.sleep(20);
        }
        assertEquals(0, LastError.size());
    }

    @Test
    void aTriggerFromBeforeAReloadKeepsItsImports() {
        harness.load("a.sk", """
                import:
                \tjava.lang.Math

                on load:
                \tsay Math..("[Math]abs")(-3)
                """);
        List<co.xenastudios.neoskript.compiler.CompiledScripts.CompiledTrigger> before = harness.triggers();
        // Scripts load again, and a.sk's imports are read again from the start; the old trigger still runs meanwhile.
        harness.load("b.sk", "on load:\n\tsay 1\n");
        harness.run(before);
        assertEquals("3", harness.said.get(0));
        assertTrue(harness.warnings.stream().noneMatch(w -> w.contains("could not be found")), harness.warnings.toString());
    }
}
