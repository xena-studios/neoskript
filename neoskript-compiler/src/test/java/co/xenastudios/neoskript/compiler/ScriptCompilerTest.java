/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScriptCompilerTest {

    private final List<String> said = new ArrayList<>();
    private Library library;

    @BeforeEach
    void library() {
        library = new Library();
        library.type("string", "text", String.class);
        library.type("number", "number", Number.class);
        library.type("integer", "integer", Long.class);
        library.type("boolean", "boolean", Boolean.class);
        library.subtype("integer", "number");
        library.event("ScriptLoad", ScriptEvent.LOAD, "[script] load");
        library.effect("Say", info -> (context, args) -> said.add(Arrays.toString((Object[]) args[0])
                + (args[1] == null ? "" : " to " + args[1])), "say %objects% [to %string%]");
        library.effect("Fail", info -> (context, args) -> {
            throw new IllegalStateException("boom");
        }, "fail");
        library.effect("Crash", info -> {
            throw new IllegalStateException("a bug");
        }, "crash");
        library.condition("Truth", info -> (context, args) -> info.hasTag("yes"), "(yes:always|never)");
        library.expression("Double", "number", info -> (context, args) -> ((Number) args[0]).doubleValue() * 2,
                "double %number%");
        library.expression("Mark", "integer", info -> (context, args) -> (long) info.mark(), "(1¦one|2¦two)");
    }

    private CompiledScripts compile(String text) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", text)));
        assertEquals(List.of(), parsed.getFirst().diagnostics());
        return new ScriptCompiler(library, new co.xenastudios.neoskript.runtime.variables.VariableStore()).compile(parsed);
    }

    private void run(String text) {
        CompiledScripts compiled = compile(text);
        assertEquals(List.of(), compiled.diagnostics());
        for (CompiledScripts.CompiledTrigger trigger : compiled.triggers()) {
            trigger.trigger().execute(new SimpleTriggerContext(null));
        }
    }

    @Test
    void runsEffectsWithLiteralsTextAndExpressions() {
        run("""
                on load:
                \tsay "hello"
                \tsay 1, 2.5 and true to "you"
                \tsay "%double 21% is ""the answer"", 100%% sure"
                \tsay double one and two
                """);
        assertEquals(List.of("[hello]", "[1, 2.5, true] to you", "[42 is \"the answer\", 100% sure]", "[2.0, 2]"),
                said);
    }

    @Test
    void conditionsStopTheTriggerWhenFalse() {
        run("on load:\n\tsay \"a\"\n\talways\n\tsay \"b\"\n\tnever\n\tsay \"c\"\n");
        assertEquals(List.of("[a]", "[b]"), said);
    }

    @Test
    void runtimeErrorsSayWhichScriptLineFailed() {
        CompiledScripts compiled = compile("on load:\n\tsay \"a\"\n\tfail\n");
        ScriptRuntimeException e = assertThrows(ScriptRuntimeException.class,
                () -> compiled.triggers().getFirst().trigger().execute(new SimpleTriggerContext(null)));
        assertEquals("test.sk", e.script());
        assertEquals(3, e.line());
        assertEquals("boom", e.getCause().getMessage());
    }

    @Test
    void reportsWordsItCantUnderstand() {
        CompiledScripts compiled = compile("on load:\n\tsay \"a\"\n\tsay diamond sword\n\tsay \"b\"\n");
        assertEquals(1, compiled.diagnostics().size());
        assertTrue(compiled.diagnostics().getFirst().message().equals("Can't understand this condition/effect: say diamond sword"),
                compiled.diagnostics().toString());
        // As in Skript, the trigger loads without the line.
        compiled.triggers().getFirst().trigger().execute(new SimpleTriggerContext(null));
        assertEquals(List.of("[a]", "[b]"), said);
    }

    @Test
    void leavesOutLinesThatFailAndRunsTheRest() {
        CompiledScripts compiled = compile("""
                on load:
                	set {_x} to 1
                	if always:
                		say "a"
                		say diamond sword
                		set {_y} to 2
                		say "b"
                	set {_z} to 3
                	say {_z} to diamond sword
                	say "c", {_x}, {_y} and {_z}
                	loop 1 and 2:
                		say sword
                		say loop-value
                	say "d"
                """);
        assertEquals(3, compiled.diagnostics().size(), compiled.diagnostics().toString());
        assertEquals(List.of(5, 9, 12), compiled.diagnostics().stream().map(d -> d.line()).toList());
        compiled.triggers().getFirst().trigger().execute(new SimpleTriggerContext(null));
        assertEquals(List.of("[a]", "[b]", "[c, 1, 2, 3]", "[1]", "[2]", "[d]"), said);
    }

    @Test
    void whatNeoSkriptFailsToCompileIsACrashNotTheScriptsError() {
        // A reload keeps the version of such a script that was running (D12).
        CompiledScripts compiled = compile("on load:\n\tsay \"a\"\n\tcrash\n");
        assertEquals(java.util.Set.of("test.sk"), compiled.crashed());
        assertTrue(compiled.diagnostics().getFirst().message().startsWith("NeoSkript couldn't compile this ("),
                compiled.diagnostics().toString());
        assertEquals(java.util.Set.of(), compile("on load:\n\tsay \"a\"\n\tsay diamond sword\n").crashed());
    }

    @Test
    void aLocalSetOnlyInALineThatFailsIsUnset() {
        CompiledScripts compiled = compile("on load:\n\tset {_y} to diamond sword\n\tsay \"y\" and {_y}\n");
        assertEquals(1, compiled.diagnostics().size(), compiled.diagnostics().toString());
        compiled.triggers().getFirst().trigger().execute(new SimpleTriggerContext(null));
        assertEquals(List.of("[y]"), said);
    }

    @Test
    void aFunctionLoadsWithoutItsLinesThatFail() {
        CompiledScripts compiled = compile("""
                function f(n: number) :: number:
                	say diamond sword
                	return {_n} + 1

                on load:
                	say f(1)
                """);
        assertEquals(1, compiled.diagnostics().size(), compiled.diagnostics().toString());
        compiled.linkFunctions(java.util.Set.of("test.sk"));
        compiled.triggers().getFirst().trigger().execute(new SimpleTriggerContext(null));
        assertEquals(List.of("[2]"), said);
    }

    @Test
    void compilesAWaitInALoopOverValues() {
        // The loop's queue is saved with its values, so the loop carries on from it after the wait.
        library.type("timespan", "timespan", co.xenastudios.neoskript.runtime.Timespan.class);
        CompiledScripts compiled = compile("on load:\n\tloop 1, 2 and 3:\n\t\tsay \"%11 - loop-number%\"\n\t\twait 1 second\n");
        assertEquals(List.of(), compiled.diagnostics());
    }

    @Test
    void addonsGiveLoopsOverTheirExpressionsLoopValuesOfTheirOwn() {
        // 'keys of "x"' gives "a" and "b"; in a loop over it, loop-path is "x.a" from the looped expression's argument.
        library.expression("Keys", "string", info -> (context, args) -> new Object[] {"a", "b"}, "keys of %string%");
        library.loopValue("Keys", "path", info -> {
            if (info.mark() != 0) {
                throw new co.xenastudios.neoskript.api.element.SyntaxError("not here");
            }
            return (context, args, current) -> args[0] + "." + current;
        });
        run("on load:\n\tloop keys of \"x\":\n\t\tsay loop-path and loop-value\n\tloop 1 and 2:\n\t\tloop keys of \"y\":"
                + "\n\t\t\tsay loop-path-1\n");
        assertEquals(List.of("[x.a, a]", "[x.b, b]", "[y.a]", "[y.b]", "[y.a]", "[y.b]"), said);
        // Only loops over that expression have it.
        CompiledScripts other = compile("on load:\n\tloop 1 and 2:\n\t\tsay loop-path\n");
        assertEquals(List.of("There's no loop that matches 'loop-path'"),
                other.diagnostics().stream().map(d -> d.message()).toList());
    }

    @Test
    void codeTooBigForAMethodIsAnErrorNotACrash() throws Throwable {
        // 2,000 nested calls parse where the stack is deep enough (a warm parser's is), but their bytecode doesn't
        // fit in one method: ASM threw where a script error is due, and the rest of the script still loads.
        String deep = "function f(x: object) :: object:\n\treturn {_x}\n\non load:\n\tsay "
                + "f(".repeat(2000) + "1" + ")".repeat(2000) + "\n\tsay \"after\"\n";
        Throwable[] failed = {null};
        Thread thread = new Thread(null, () -> {
            try {
                List<ParsedScript> parsed = new ScriptLoader(library.registry())
                        .load(List.of(new SourceFile("deep.sk", deep)));
                assertEquals(List.of(), parsed.getFirst().diagnostics());
                CompiledScripts compiled = new ScriptCompiler(library,
                        new co.xenastudios.neoskript.runtime.variables.VariableStore()).compile(parsed);
                assertEquals(List.of("5: " + ScriptCompiler.TOO_BIG),
                        compiled.diagnostics().stream().map(d -> d.line() + ": " + d.message()).toList());
                compiled.linkFunctions();
                for (CompiledScripts.CompiledTrigger trigger : compiled.triggers()) {
                    trigger.trigger().execute(new SimpleTriggerContext(null));
                }
            } catch (Throwable e) {
                failed[0] = e;
            }
        }, "deep", 512L * 1024 * 1024);
        thread.start();
        thread.join();
        if (failed[0] != null) {
            throw failed[0];
        }
        assertEquals(List.of("[after]"), said);
    }
}
