/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A list in brackets is read as Skript 2.17 reads it: as one expression where one sets up, and split where the
 * reading as one fails to set up, as 'indices of' a list that isn't a list variable does (issues #33 and #44). Each
 * expected value is Skript's, from the differential's splits probe.
 */
class ListSplitsTest {

    private List<String> run(String... lines) {
        List<String> said = new ArrayList<>();
        Library library = new Library();
        StandardLibrary.register(library);
        library.effect("TestSay", info -> (context, args) -> said.add(Values.text(args[0])), "say %objects%");
        StringBuilder script = new StringBuilder("""
                on load:
                \tset {_n::*} to "a" and "b"
                \tset {_a::*} to 1 and 2
                \tset {_b::*} to 3 and 4
                \tset {_t} to "abc"
                \tset {_o::*} to "x" and "y"
                """);
        for (String line : lines) {
            script.append("\tsay ").append(line).append('\n');
        }
        List<ParsedScript> parsed = new ScriptLoader(library.registry())
                .load(List.of(new SourceFile("test.sk", script.toString())));
        assertEquals(List.of(), errors(parsed.getFirst().diagnostics()), "parse problems");
        CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        compiled.linkFunctions();
        assertEquals(List.of(), errors(compiled.diagnostics()), "compile problems");
        compiled.triggers().forEach(t -> t.trigger().execute(new SimpleTriggerContext(null)));
        return said;
    }

    private static List<String> errors(List<Diagnostic> diagnostics) {
        return diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).map(Diagnostic::message).toList();
    }

    @Test
    void indicesThatCantBeOfTheWholeListSplitIt() {
        assertEquals(List.of("1, 2 and 2", "4", "1, 2, 3 and 4", "1, 2 and 5", "1, 2, 3 and 4"), run(
                "(indices of {_n::*}, size of {_n::*})",
                "(size of {_n::*}, indices of {_n::*})",
                "(indices of {_a::*} and {_b::*})",
                "(indices of {_a::*} and 5)",
                "indices of {_a::*} and {_b::*}"));
    }

    @Test
    void aRecursiveSizeThatCantBeOfTheWholeListSplitsIt() {
        assertEquals(List.of("2 and 2"), run("(recursive size of {_a::*}, size of {_b::*})"));
    }

    @Test
    void aPropertyOfAListWithAnyTypedValuesIsOneExpression() {
        assertEquals(List.of("3 and 1", "3", "x", "3 and 2"), run(
                "(length of {_t} and first element of {_o::*})",
                "(size of {_a::*} and last element of {_b::*})",
                "(first element of {_o::*} and length of {_t})",
                "(length of {_t} and \"de\")"));
    }
}
