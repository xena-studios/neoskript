/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper.fuzz;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.frontend.stub.CompileFuzz;
import co.xenastudios.neoskript.frontend.stub.ScriptFuzzer;
import co.xenastudios.neoskript.paper.StandardLibrary;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Random and mutated scripts through the parser and the compiler with the whole standard library, without a server:
 * compiling never throws, hangs, reports a bug of its own or makes a class the verifier refuses (apart from what
 * needs Paper's registries, which only a server has). Triggers that surely
 * end are run too: with no server, most elements fail, and every failure must come out as a
 * {@link ScriptRuntimeException}, as a runtime error at the script's line.
 */
class StandardLibraryFuzzTest {

    private static Library library;

    @BeforeAll
    static void library() {
        library = new Library();
        StandardLibrary.register(library);
    }

    @Test
    void compilingNeverThrowsHangsOrFailsVerification() {
        ScriptFuzzer fuzzer = ScriptFuzzer.fromRepository();
        long first = ScriptFuzzer.firstSeed();
        for (long seed = first; seed < first + ScriptFuzzer.iterations(150); seed++) {
            ScriptFuzzer.Case fuzzCase = fuzzer.generate(seed);
            ScriptFuzzer.within(fuzzCase, 30, () -> compileAndRun(fuzzCase));
        }
    }

    @Test
    void theCorpusCompilesWithoutInternalErrors() {
        List<String> originals = ScriptFuzzer.fromRepository().originals();
        for (int i = 0; i < originals.size(); i++) {
            ScriptFuzzer.Case fuzzCase = new ScriptFuzzer.Case(-i, originals.get(i));
            ScriptFuzzer.within(fuzzCase, 30, () -> compileAndRun(fuzzCase));
        }
    }

    private static void compileAndRun(ScriptFuzzer.Case fuzzCase) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("fuzz.sk",
                fuzzCase.text())));
        CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        List<Diagnostic> diagnostics = new ArrayList<>(parsed.getFirst().diagnostics());
        diagnostics.addAll(compiled.diagnostics());
        // With no server, Paper's registries can't start, so what needs them fails here; on a server it doesn't.
        diagnostics.removeIf(d -> d.message().contains("org.bukkit.Registry") || d.message().contains("RegistryAccess"));
        CompileFuzz.assertNoInternalErrors(fuzzCase, diagnostics);
        compiled.linkFunctions();
        if (CompileFuzz.mightNotEnd(fuzzCase.text())) {
            return;
        }
        for (CompiledScripts.CompiledTrigger trigger : compiled.triggers()) {
            if (trigger.eventClasses() != null || trigger.period() != null) {
                // Server events' triggers expect their event.
                continue;
            }
            try {
                trigger.trigger().execute(new SimpleTriggerContext(null));
            } catch (ScriptRuntimeException expected) {
                // A runtime error, at a line of the script.
            }
        }
    }
}
