/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.api.registration.SyntaxPriority;
import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * An addon's syntax with a priority is tried where Skript tries an addon's of that priority: after Skript's own of the
 * same level, before those of a later one. Skript's {@code [namespace[d]] key[s] of %minecrafttags%} is a property; an
 * addon's simple {@code keys of %objects%} comes before it, as skJson's does on Skript.
 */
class SyntaxPriorityTest {

    private List<String> run(SyntaxPriority priority) {
        List<String> said = new ArrayList<>();
        Library library = new Library();
        StandardLibrary.register(library);
        library.effect("TestSay", info -> (context, args) -> said.add(Values.text(args[0])), "say %objects%");
        library.expression("TestExprKeys", "string", info -> (context, args) -> "the addon's", "[all] keys of %objects%");
        if (priority != null) {
            library.priority("TestExprKeys", priority);
        }
        List<ParsedScript> parsed = new ScriptLoader(library.registry())
                .load(List.of(new SourceFile("test.sk", "on load:\n\tset {_x} to 1\n\tsay keys of {_x}\n")));
        CompiledScripts compiled = new ScriptCompiler(library, new VariableStore()).compile(parsed);
        compiled.linkFunctions();
        compiled.triggers().forEach(t -> t.trigger().execute(new SimpleTriggerContext(null)));
        return said;
    }

    @Test
    void anAddonsSimpleExpressionComesBeforeABuiltInProperty() {
        assertEquals(List.of("the addon's"), run(SyntaxPriority.SIMPLE));
    }

    @Test
    void anAddonsPropertyComesAfterTheBuiltInOnes() {
        assertEquals(List.of("<none>"), run(SyntaxPriority.PROPERTY));
    }
}
