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
}
