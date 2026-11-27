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
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Where no other reading of a line works, 'indices of' something that isn't a list variable is still an error. */
class ListSplitsErrorTest {

    @Test
    void indicesOfAValueIsStillAnError() {
        Library library = new Library();
        StandardLibrary.register(library);
        library.effect("TestSay", info -> (context, args) -> Values.text(args[0]), "say %objects%");
        String line = "set {_i::*} to indices of {_x}";
        List<ParsedScript> parsed = new ScriptLoader(library.registry())
                .load(List.of(new SourceFile("test.sk", "on load:\n\t" + line + "\n")));
        List<String> errors = new ArrayList<>(errors(parsed.getFirst().diagnostics()));
        if (errors.isEmpty()) {
            errors.addAll(errors(new ScriptCompiler(library, new VariableStore()).compile(parsed).diagnostics()));
        }
        assertEquals(List.of("The indices expression may only be used with keyed expressions"), errors);
    }

    private static List<String> errors(List<Diagnostic> diagnostics) {
        return diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).map(Diagnostic::message).toList();
    }
}
