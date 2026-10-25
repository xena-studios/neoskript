/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.errors.ErrorFrame;
import co.xenastudios.neoskript.runtime.errors.ErrorSource;
import co.xenastudios.neoskript.runtime.errors.RuntimeError;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runtime errors say where they came from: the script, the line and its code, and the element. */
class RuntimeErrorSourceTest {

    private final List<RuntimeError> printed = new ArrayList<>();
    private final List<String> said = new ArrayList<>();
    private Library library;

    @BeforeEach
    void setUp() {
        library = new Library();
        library.type("string", "text", String.class);
        library.type("number", "number", Number.class);
        library.type("integer", "integer", Long.class);
        library.subtype("integer", "number");
        library.event("ScriptLoad", co.xenastudios.neoskript.api.registration.ScriptEvent.LOAD, "[script] load");
        library.effect("Say", info -> (context, args) -> said.add(Arrays.toString((Object[]) args[0])), "say %objects%");
        // Ids as Skript's classes, so they go by Skript's names for them.
        library.expression("ExprSignText", "string", info -> (context, args) -> {
            RuntimeErrors.report("Signs only have lines from 1 to 4, but tried to obtain line 5");
            return "x";
        }, "broken line");
        library.effect("EffWakeupSleep", info -> (context, args) -> RuntimeErrors.warning("careful"), "warn me");
        library.condition("Unnamed", info -> (context, args) -> {
            RuntimeErrors.report("bad check", "check");
            return true;
        }, "bad check");
        RuntimeErrors.install(new RuntimeErrors.Consumer() {
            @Override
            public void printError(RuntimeError error) {
                printed.add(error);
            }

            @Override
            public void printFrameOutput(ErrorFrame.Output output, RuntimeError.Level level) {}
        }, ErrorFrame.Limits.DEFAULT, ErrorFrame.Limits.DEFAULT);
    }

    @AfterEach
    void tearDown() {
        RuntimeErrors.install(null, ErrorFrame.Limits.DEFAULT, ErrorFrame.Limits.DEFAULT);
    }

    private void run(String text) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("errors.sk", text)));
        assertEquals(List.of(), parsed.getFirst().diagnostics());
        CompiledScripts compiled = new ScriptCompiler(library, new co.xenastudios.neoskript.runtime.variables.VariableStore())
                .compile(parsed);
        assertEquals(List.of(), compiled.diagnostics());
        compiled.linkFunctions();
        for (CompiledScripts.CompiledTrigger trigger : compiled.triggers()) {
            trigger.trigger().execute(new SimpleTriggerContext(null));
        }
    }

    @Test
    void errorsSayTheirScriptLineAndElement() {
        run("on load:\n\tsay \"fine\"\n\tsay broken line # a comment\n\twarn me\n\tbad check\n");
        assertEquals(3, printed.size(), printed.toString());
        assertEquals(new ErrorSource("expression", "Sign Text", 3, "say broken line", "errors.sk"), printed.get(0).source());
        assertEquals(RuntimeError.Level.ERROR, printed.get(0).level());
        assertEquals(new ErrorSource("effect", "Wake And Sleep", 4, "warn me", "errors.sk"), printed.get(1).source());
        assertEquals(RuntimeError.Level.WARNING, printed.get(1).level());
        assertEquals(new ErrorSource("condition", "Unnamed", 5, "bad check", "errors.sk"), printed.get(2).source());
        assertEquals("check", printed.get(2).toHighlight());
    }

    @Test
    void errorsInLoopsAndFunctionsComeFromTheirLines() {
        run("""
                function f():
                \tsay broken line
                on load:
                \tloop "a" and "b":
                \t\tsay broken line
                \tf()
                """);
        assertEquals(List.of(5, 5, 2), printed.stream().map(e -> e.source().lineNumber()).toList());
    }

    @Test
    void caughtErrorsArentPrinted() {
        run("on load:\n\tcatch runtime errors:\n\t\tsay broken line\n\t\twarn me\n\tsay last caught runtime errors\n");
        assertEquals(List.of(), printed);
        assertEquals(List.of("[Signs only have lines from 1 to 4, but tried to obtain line 5, careful]"),
                said.subList(said.size() - 1, said.size()));
    }
}
