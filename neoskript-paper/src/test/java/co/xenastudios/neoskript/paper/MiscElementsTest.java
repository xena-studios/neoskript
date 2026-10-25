/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Smaller elements of Skript's that have no test of its own: number limits, loop iterations, parse errors... */
class MiscElementsTest {

    private final List<String> said = new ArrayList<>();

    /** Runs the script's triggers, returning what they said, or its errors if it has any. */
    private List<String> run(String script) {
        Library library = new Library();
        StandardLibrary.register(library);
        library.effect("TestSay", info -> (context, args) -> said.add(Values.text(args[0])), "say %objects%");
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", script)));
        List<String> errors = new ArrayList<>(parsed.getFirst().diagnostics().stream().map(Diagnostic::message).toList());
        if (!errors.isEmpty()) {
            return errors;
        }
        CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        if (!compiled.diagnostics().isEmpty()) {
            return compiled.diagnostics().stream().map(Diagnostic::message).toList();
        }
        compiled.linkFunctions();
        compiled.triggers().forEach(t -> t.trigger().execute(new SimpleTriggerContext(null)));
        return said;
    }

    @Test
    void numberLimits() {
        assertEquals(List.of("true", "true", "true", "true", "true", "true", "true"), run("""
                on load:
                \tsay whether max integer value + 1 is 2147483648
                \tsay whether minimum integer value is -2147483648
                \tsay whether the max long value is 9223372036854775807
                \tsay whether min long value < 0
                \tsay whether min double value > 0
                \tsay whether min float value > 0
                \tsay whether max float value < max double value
                """));
    }

    @Test
    void loopIterations() {
        assertEquals(List.of("1", "2", "1:1", "1:2", "2:1", "2:2", "3"), run("""
                on load:
                \tloop 2 times:
                \t\tsay loop iteration
                \tloop 2 times:
                \t\tloop 2 times:
                \t\t\tsay "%loop-counter-1%:%loop-iteration-2%"
                \tset {_i} to 0
                \twhile {_i} < 3:
                \t\tadd 1 to {_i}
                \t\tif {_i} is 3:
                \t\t\tsay the loop-iteration
                """));
    }

    @Test
    void loopIterationErrors() {
        assertEquals(List.of("There are multiple loops. Use loop-iteration-1/2/3/etc. to specify which loop-iteration you want."),
                run("""
                        on load:
                        \tloop 2 times:
                        \t\tloop 2 times:
                        \t\t\tsay loop-iteration
                        """));
    }

    @Test
    void loopIterationOutsideALoop() {
        assertEquals(List.of("The loop iteration expression must be used in a loop"), run("""
                on load:
                \tloop 2 times:
                \t\tsay loop-counter-2
                """));
    }

    @Test
    void parseErrors() {
        assertEquals(List.of("<none>", "x could not be parsed as an integer", "<none>",
                "a could not be parsed as \"%integer%\""), run("""
                on load:
                \tsay last parse error
                \tset {_x} to "x" parsed as integer
                \tsay the last parse error
                \tset {_x} to "5" parsed as integer
                \tsay parse error
                \tset {_x} to "a" parsed as "%integer%"
                \tsay error
                """));
    }

    @Test
    void debugInfo() {
        assertEquals(List.of("1 (integer), \"a\" (text) and 0.5 (number)"), run("""
                on load:
                \tsay debug info of 1, "a" and 0.5
                """));
    }

    @Test
    void valueWithin() {
        assertEquals(List.of("1337", "2"), run("""
                on load:
                \tset {_n} to 1337
                \tsay number within {_n}
                \tset {_v::*} to "a", 10 and "b"
                \tsay size of (strings within {_v::*})
                """));
        said.clear();
        assertEquals(List.of("You cannot get multiple elements of a single value"), run("""
                on load:
                \tsay numbers within {_n}
                """));
        said.clear();
        assertEquals(List.of("{_v::*} may contain more than one number"), run("""
                on load:
                \tsay number within {_v::*}
                """));
    }

    @Test
    void amounts() {
        assertEquals(List.of("3", "2", "4"), run("""
                on load:
                \tsay size of (1, 2 and 3)
                \tset {_q} to a queue of "a" and "b"
                \tsay size of {_q}
                \tset {_l::*} to 1, 2, 3 and 4
                \tsay amount of {_l::*}
                """));
    }

    @Test
    void contains() {
        assertEquals(List.of("true", "false", "true", "true", "false"), run("""
                on load:
                \tset {_t::*} to "hello" and "world"
                \tsay whether {_t::*} contain "o"
                \tsay whether {_t::*} contains "ell"
                \tsay whether {_t::*} contains "world"
                \tsay whether contents of "hello" contain "ell"
                \tsay whether contents of (1 and 2) contain 1
                """));
    }
}
