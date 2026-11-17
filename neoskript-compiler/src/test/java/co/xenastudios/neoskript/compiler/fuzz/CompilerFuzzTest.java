/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler.fuzz;

import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.frontend.stub.CompileFuzz;
import co.xenastudios.neoskript.frontend.stub.ScriptFuzzer;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Random and mutated scripts through the parser and the compiler, with the compiler's own syntax (variables,
 * arithmetic, conditions, loops, functions, waits) and a few effects: compiling never throws, never hangs, never
 * reports a bug of its own, and every class it makes passes the JVM's verifier, which runs as it's defined. Triggers
 * that surely end are run too, and nothing but a {@link ScriptRuntimeException} may come out of them.
 */
class CompilerFuzzTest {

    private static Library library() {
        Library library = new Library();
        library.type("object", "object", Object.class);
        library.type("string", "text", String.class);
        library.type("number", "number", Number.class);
        library.type("integer", "integer", Long.class);
        library.type("boolean", "boolean", Boolean.class);
        library.type("timespan", "timespan", co.xenastudios.neoskript.runtime.Timespan.class);
        library.subtype("integer", "number");
        library.event("ScriptLoad", ScriptEvent.LOAD, "[script] load");
        library.event("ScriptUnload", ScriptEvent.UNLOAD, "[script] unload");
        library.effect("Send", info -> (context, args) -> {}, "send %objects% [to %objects%]", "broadcast %objects%");
        library.effect("Fail", info -> (context, args) -> {
            throw new IllegalStateException("boom");
        }, "fail");
        library.effect("Overflow", info -> (context, args) -> {
            throw new StackOverflowError();
        }, "overflow");
        library.expression("Null", "object", info -> (context, args) -> {
            throw new NullPointerException();
        }, "a null pointer");
        library.expression("Cast", "number", info -> (context, args) -> "not a number", "a wrong type");
        return library;
    }

    @Test
    void compilingNeverThrowsHangsOrFailsVerification() {
        ScriptFuzzer fuzzer = ScriptFuzzer.fromRepository();
        Library library = library();
        long first = ScriptFuzzer.firstSeed();
        for (long seed = first; seed < first + ScriptFuzzer.iterations(250); seed++) {
            ScriptFuzzer.Case fuzzCase = fuzzer.generate(seed);
            ScriptFuzzer.within(fuzzCase, 90, () -> compileAndRun(library, fuzzCase));
        }
    }

    @Test
    void theCorpusCompilesWithoutInternalErrors() {
        Library library = library();
        List<String> originals = ScriptFuzzer.fromRepository().originals();
        for (int i = 0; i < originals.size(); i++) {
            ScriptFuzzer.Case fuzzCase = new ScriptFuzzer.Case(-i, originals.get(i));
            ScriptFuzzer.within(fuzzCase, 90, () -> compileAndRun(library, fuzzCase));
        }
    }

    @Test
    void elementsThatThrowBecomeRuntimeErrors() {
        Library library = library();
        for (String line : List.of("fail", "overflow", "set {_x} to a null pointer", "set {_x} to a wrong type + 1",
                "send a null pointer")) {
            ScriptFuzzer.Case fuzzCase = new ScriptFuzzer.Case(0, "on load:\n\t" + line + "\n");
            ScriptFuzzer.within(fuzzCase, 90, () -> compileAndRun(library, fuzzCase));
        }
    }

    private static void compileAndRun(Library library, ScriptFuzzer.Case fuzzCase) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("fuzz.sk",
                fuzzCase.text())));
        CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        List<Diagnostic> diagnostics = new ArrayList<>(parsed.getFirst().diagnostics());
        diagnostics.addAll(compiled.diagnostics());
        CompileFuzz.assertNoInternalErrors(fuzzCase, diagnostics);
        compiled.linkFunctions();
        if (CompileFuzz.mightNotEnd(fuzzCase.text())) {
            return;
        }
        for (CompiledScripts.CompiledTrigger trigger : compiled.triggers()) {
            try {
                trigger.trigger().execute(new SimpleTriggerContext(null));
            } catch (ScriptRuntimeException expected) {
                // A runtime error, at a line of the script.
            }
        }
    }
}
