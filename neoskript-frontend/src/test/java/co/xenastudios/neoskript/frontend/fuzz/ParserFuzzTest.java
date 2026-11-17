/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.fuzz;

import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.frontend.stub.ScriptFuzzer;
import co.xenastudios.neoskript.frontend.stub.SkriptRegistry;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Random and mutated scripts through the parser, against the stub registry of Skript's syntax: whatever the script,
 * parsing ends in time with diagnostics, never an exception. {@code -Pneoskript.fuzz.iterations=N} runs it longer.
 */
class ParserFuzzTest {

    private static final ScriptLoader LOADER = new ScriptLoader(SkriptRegistry.get());

    @Test
    void parsingNeverThrowsOrHangs() {
        ScriptFuzzer fuzzer = ScriptFuzzer.fromRepository();
        long first = ScriptFuzzer.firstSeed();
        int iterations = ScriptFuzzer.iterations(250);
        for (long seed = first; seed < first + iterations; seed++) {
            ScriptFuzzer.Case fuzzCase = fuzzer.generate(seed);
            ScriptFuzzer.within(fuzzCase, 90, () -> LOADER.load(List.of(new SourceFile("fuzz.sk", fuzzCase.text()))));
        }
    }

    @Test
    void scriptsCanBeParsedTogether() {
        // Calls between scripts resolve across files, so a broken file mustn't break the parse of the others.
        ScriptFuzzer fuzzer = ScriptFuzzer.fromRepository();
        long first = ScriptFuzzer.firstSeed();
        for (long seed = first; seed < first + ScriptFuzzer.iterations(250) / 20; seed++) {
            ScriptFuzzer.Case a = fuzzer.generate(seed);
            ScriptFuzzer.Case b = fuzzer.generate(seed + 1_000_000);
            ScriptFuzzer.Case both = new ScriptFuzzer.Case(seed, a.text() + "\n# --- b.sk, seed " + b.seed() + "\n" + b.text());
            ScriptFuzzer.within(both, 90, () -> LOADER.load(List.of(new SourceFile("a.sk", a.text()),
                    new SourceFile("b.sk", b.text()))));
        }
    }

    @Test
    void deepNestingIsAnErrorNotACrash() {
        // Thousands of brackets ran the parser out of stack, and the StackOverflowError stopped the whole reload.
        String deep = "on load:\n\tset {_x} to " + "(".repeat(5000) + "1" + ")".repeat(5000) + "\n\tset {_y} to 1\n";
        ScriptFuzzer.Case fuzzCase = new ScriptFuzzer.Case(0, deep);
        ScriptFuzzer.within(fuzzCase, 90, () -> {
            var script = LOADER.load(List.of(new SourceFile("deep.sk", deep))).getFirst();
            assertEquals(List.of("2: This line is nested too deeply to be understood"),
                    script.diagnostics().stream().map(d -> d.line() + ": " + d.message()).toList());
        });
    }
}
