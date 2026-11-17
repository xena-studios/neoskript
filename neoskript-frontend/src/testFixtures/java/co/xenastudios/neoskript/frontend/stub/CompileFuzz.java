/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.stub;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import java.util.List;
import java.util.Locale;

/** What the compiler fuzz tests check of a script's diagnostics: none is a NeoSkript bug rather than the script's. */
public final class CompileFuzz {

    private CompileFuzz() {}

    /** Fails if a diagnostic says NeoSkript couldn't compile something: a bug, or code that failed verification. */
    public static void assertNoInternalErrors(ScriptFuzzer.Case fuzzCase, List<Diagnostic> diagnostics) {
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.message().contains("NeoSkript couldn't compile this")) {
                throw new AssertionError("Seed " + fuzzCase.seed() + ", line " + diagnostic.line() + ": "
                        + diagnostic.message() + "\n" + ScriptFuzzer.preview(fuzzCase.text()));
            }
        }
    }

    /**
     * Whether running the script's triggers could take forever: loops, waits and timers. Those are compiled, not
     * run, as an infinite loop would keep a thread busy for the rest of the tests.
     */
    public static boolean mightNotEnd(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("loop") || lower.contains("while") || lower.contains("wait") || lower.contains("every")
                || lower.contains("times") || lower.contains("for each") || lower.contains("repeat");
    }
}
