/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Skript gives an excluded event value's error ({@code Use 'attacker' and/or 'victim'...}) only the first time any
 * script asks for it. Scripts are parsed in parallel, but it's always the first of them in order that gets it.
 */
class OnceOnlyErrorTest {

    @Test
    void theFirstScriptInOrderGetsItEveryTime() {
        List<SourceFile> files = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            files.add(new SourceFile("s" + (char) ('a' + i) + ".sk", "on damage:\n\tshow the victim's inventory to the player\n"));
        }
        for (int run = 0; run < 20; run++) {
            Library library = new Library();
            StandardLibrary.register(library);
            List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(files);
            List<String> messages = new ArrayList<>();
            parsed.forEach(script -> script.diagnostics().stream().map(Diagnostic::message).forEach(messages::add));
            new ScriptCompiler(library, new VariableStore()).compile(parsed).diagnostics().stream()
                    .sorted(java.util.Comparator.comparing(Diagnostic::file)).map(Diagnostic::message).forEach(messages::add);
            assertEquals("Use 'attacker' and/or 'victim' in damage/death events", messages.getFirst(), messages.toString());
            assertEquals(1, messages.stream().filter(m -> m.startsWith("Use 'attacker'")).count(), messages.toString());
        }
    }
}
