/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Load messages as Skript 2.17.0 gives them, from probes of a real Skript server (issue #37). */
class LoadMessagesTest {

    /** The script's errors, as {@code line: message}, by line. */
    private static List<String> errors(String script) {
        Library library = new Library();
        StandardLibrary.register(library);
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", script)));
        var compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        List<Diagnostic> all = new ArrayList<>(parsed.getFirst().diagnostics());
        all.removeIf(compiled::unparsed);
        all.addAll(compiled.diagnostics());
        return all.stream().filter(d -> d.severity() == Severity.ERROR).sorted(Comparator.comparingInt(Diagnostic::line))
                .map(d -> d.line() + ": " + d.message()).toList();
    }

    @Test
    void wordsForAnEventValueThatIsntThereAreTheTypeTheyName() {
        // Where words are taken as they are, Skript's unparsed literal reads them as the type; in a text, or where
        // only what changes goes, the element's error stands.
        assertEquals(List.of("6: 'executor' can only be used in the on unknown command execution event",
                "7: There's no damage cause in an on script load/unload event.",
                "11: 'executor' can only be used in the on unknown command execution event"), errors("""
                function show(v: objects):
                \tsend "%{_v::*}%" to console
                on load:
                \tset {_c} to executor
                \tset {_d} to damage cause
                \tsend "%executor%" to console
                \tbroadcast "%damage cause%"
                \tshow(executor)
                \tshow(damage cause)
                \tbroadcast damage cause
                \tif executor is set:
                \t\tbroadcast "set"
                """));
    }

    @Test
    void deletingAnEventValueWithNoChangerIsRefused() {
        assertEquals(List.of("2: event-item stack can't be deleted", "5: event-item stack can't be deleted"), errors("""
                on consume:
                \tdelete event-item

                on drop:
                \tdelete event-item
                """));
    }

    @Test
    void aJoinIsOneValueAndCantBeLooped() {
        assertEquals(List.of("3: Can't loop 'join {_l::*} with \",\"' because it's only a single value",
                "5: Can't loop 'join {_l::*}' because it's only a single value"), errors("""
                on load:
                \tset {_l::*} to "a" and "b"
                \tloop join {_l::*} with ",":
                \t\tbroadcast loop-value
                \tloop join {_l::*}:
                \t\tbroadcast loop-value
                \tloop split "a,b" at ",":
                \t\tbroadcast loop-value
                """));
    }

    @Test
    void wordsInsideATextsCodeAreTakenAsTheyAre() {
        // Only what a text's %...% is keeps the element's error; words inside it are read as they are elsewhere.
        // (The differential's bare-words checks the others, '%{_v} ? block%' and the like, with a server.)
        assertEquals(List.of("4: 'executor' can only be used in the on unknown command execution event"), errors("""
                function f():
                \tset {_v} to "a"
                \tsend "%{_v} ? executor%" to console
                \tsend "%executor%" to console
                """));
    }

    @Test
    void theWorldWithNothingGivenIsNothing() {
        // In 'on load', Skript's world expression with nothing given: no error.
        assertEquals(List.of(), errors("""
                on load:
                \tset {_w} to world
                \tset {_x} to the world
                \tbroadcast "%world%"
                """));
    }

    @Test
    void anEventLocationNamesItselfTheLocation() {
        assertEquals(List.of("2: the location can't be deleted", "5: event-world can't be deleted"), errors("""
                on join:
                \tdelete event-location

                on join:
                \tdelete event-world
                """));
    }

    @Test
    void wordsForAnEventValueThatIsntThereKeepTheirErrorAgainstAValueOfAnotherType() {
        // In a function, words for an event value no type compared with the other side reads are Skript's unparsed
        // literal, which can't be made a value: its error is the one kept, not the comparison's (issue #92's review).
        assertEquals(List.of("2: 'message' can only be used in the on chat event",
                "4: 'victim' can only be used in the on vehicle destroy event, the on projectile hit event, the on"
                        + " vehicle damage event, the on attempt attack event, the on death event or the on damage event",
                "6: Can't compare 'if message' with a text"), errors("""
                function f():
                \tif message is 1:
                \t\tbroadcast "a"
                \tif victim is 1:
                \t\tbroadcast "b"
                \tif message is "a":
                \t\tbroadcast "c"
                """));
    }
}
