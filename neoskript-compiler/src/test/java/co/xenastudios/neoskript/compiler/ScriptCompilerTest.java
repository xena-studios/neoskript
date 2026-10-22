/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
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
        library.condition("Truth", info -> (context, args) -> info.hasTag("yes"), "(yes:always|never)");
        library.expression("Double", "number", info -> (context, args) -> ((Number) args[0]).doubleValue() * 2,
                "double %number%");
        library.expression("Mark", "integer", info -> (context, args) -> (long) info.mark(), "(1¦one|2¦two)");
    }

    private CompiledScripts compile(String text) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", text)));
        assertEquals(List.of(), parsed.getFirst().diagnostics());
        return new ScriptCompiler(library).compile(parsed);
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
    void reportsWhatItCantCompileYet() {
        CompiledScripts compiled = compile("on load:\n\tsay \"a\"\n\tsay {_x}\n");
        assertEquals(List.of(), compiled.triggers());
        assertEquals(1, compiled.diagnostics().size());
        assertTrue(compiled.diagnostics().getFirst().message().startsWith("NeoSkript can't run this yet"),
                compiled.diagnostics().toString());
    }
}
