/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The core language, run through the real standard library and compiler without a server. */
class LanguageTest {

    private final List<String> said = new ArrayList<>();
    private final VariableStore globals = new VariableStore();

    private List<String> run(String script) {
        Library library = new Library();
        StandardLibrary.register(library);
        library.effect("TestSay", info -> (context, args) -> said.add(Values.text(args[0])), "say %objects%");
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", script)));
        assertEquals(List.of(), parsed.getFirst().diagnostics(), "parse problems");
        CompiledScripts compiled = new ScriptCompiler(library, globals).compile(parsed);
        assertEquals(List.of(), compiled.diagnostics(), "compile problems");
        compiled.triggers().forEach(t -> t.trigger().execute(new SimpleTriggerContext(null)));
        return said;
    }

    @Test
    void arithmeticFollowsPrecedence() {
        assertEquals(List.of("7", "5", "9", "512", "2.5", "ab", "-3", "4.5"), run("""
                on load:
                \tsay 1 + 2 * 3
                \tsay 8 - 2 - 1
                \tsay (1 + 2) * 3
                \tsay 2 ^ 3 ^ 2
                \tsay 10 / 4
                \tsay "a" + "b"
                \tsay 2 - 5
                \tsay 1.5 * 3
                """));
    }

    @Test
    void variables() {
        assertEquals(List.of("7", "6", "4", "<none>", "x and y"), run("""
                on load:
                \tset {_x} to 1 + 2 * 3
                \tsay {_x}
                \tset {count} to 5
                \tadd 2 to {count}
                \tremove 1 from {count}
                \tsay {count}
                \tset {_name} to "count"
                \tsay {%{_name}%} - 2
                \tdelete {count}
                \tsay {count}
                \tset {_parts::*} to "x" and "y"
                \tsay {_parts::*}
                """));
    }

    @Test
    void listsAndLoops() {
        // List indices sort numbers by value: 2 comes before 10.
        assertEquals(List.of("4", "1=c", "2=a", "3=b", "4=d", "c, b and d", "1:1", "2:2", "3:3", "2=b", "10=a"),
                run("""
                        on load:
                        \tset {_l::*} to "c", "a" and "b"
                        \tadd "d" to {_l::*}
                        \tsay size of {_l::*}
                        \tloop {_l::*}:
                        \t\tsay "%loop-index%=%loop-value%"
                        \tremove "a" from {_l::*}
                        \tsay {_l::*}
                        \tloop 3 times:
                        \t\tsay "%loop-counter%:%loop-value%"
                        \tset {_m::10} to "a"
                        \tset {_m::2} to "b"
                        \tloop {_m::*}:
                        \t\tsay "%loop-index%=%loop-value%"
                        """));
    }

    @Test
    void conditionals() {
        assertEquals(List.of("seven", "medium", "any", "not all", "implicit"), run("""
                on load:
                \tset {_x} to 7
                \tif {_x} is 7:
                \t\tsay "seven"
                \telse:
                \t\tsay "other"
                \tif {_x} > 10:
                \t\tsay "big"
                \telse if {_x} >= 7:
                \t\tsay "medium"
                \telse:
                \t\tsay "small"
                \tif any:
                \t\t1 is 2
                \t\t2 is 2
                \tthen:
                \t\tsay "any"
                \tif all:
                \t\t1 is 2
                \t\t2 is 2
                \tthen:
                \t\tsay "all"
                \telse:
                \t\tsay "not all"
                \t{_x} is not 3:
                \t\tsay "implicit"
                """));
    }

    @Test
    void whileExitAndContinue() {
        assertEquals(List.of("1", "2", "4", "5", "done"), run("""
                on load:
                \tset {_i} to 0
                \twhile {_i} < 10:
                \t\tadd 1 to {_i}
                \t\tif {_i} is 3:
                \t\t\tcontinue
                \t\tif {_i} is 6:
                \t\t\texit loop
                \t\tsay {_i}
                \tsay "done"
                \tstop
                \tsay "unreachable"
                """));
    }

    @Test
    void conditionsStopTheTrigger() {
        assertEquals(List.of("a"), run("on load:\n\tsay \"a\"\n\t1 is 2\n\tsay \"b\"\n"));
    }

    @Test
    void functions() {
        assertEquals(List.of("55", "Hi A!", "Hi B?", "2", "9", "1", "3"), run("""
                function fib(n: number) :: number:
                \tif {_n} <= 1:
                \t\treturn {_n}
                \treturn fib({_n} - 1) + fib({_n} - 2)

                function greet(name: text, punctuation: text = "!") :: text:
                \treturn "Hi %{_name}%%{_punctuation}%"

                function count(xs: objects) :: number:
                \treturn size of {_xs::*}

                on load:
                \tsay fib(10)
                \tsay greet("A")
                \tsay greet("B", "?")
                \tsay floor(2.7)
                \tsay max(3, 9, 4)
                \tsay mod(-5, 3)
                \tsay count(1, 2 and 3)
                """));
    }

    @Test
    void runawayRecursionFailsAtTheScriptLine() {
        ScriptRuntimeException e = assertThrows(ScriptRuntimeException.class, () -> run("""
                function forever(n: number) :: number:
                \treturn forever({_n} + 1)
                on load:
                \tsay forever(1)
                """));
        assertEquals(2, e.line());
        assertInstanceOf(StackOverflowError.class, e.getCause());
    }
}
