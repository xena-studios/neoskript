/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** With 'use type properties: false', Skript 2.17's pre-2.13 property syntax in place of its type properties. */
class LegacyPropertiesTest {

    private static Library library;

    @BeforeAll
    static void register() {
        TypeProperties.enabled(false);
        library = new Library();
        StandardLibrary.register(library);
    }

    @AfterAll
    static void reset() {
        TypeProperties.enabled(true);
    }

    private static List<String> errors(String code) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry())
                .load(List.of(new SourceFile("test.sk", "on load:\n" + code.indent(4).replace("    ", "\t"))));
        List<String> errors = new ArrayList<>(errors(parsed.getFirst().diagnostics()));
        if (errors.isEmpty()) {
            errors.addAll(errors(new ScriptCompiler(library, new VariableStore()).compile(parsed).diagnostics()));
        }
        return errors;
    }

    private static List<String> errors(List<Diagnostic> diagnostics) {
        return diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).map(Diagnostic::message).toList();
    }

    private static String parsed(String code) {
        return new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", "on load:\n\t" + code + "\n")))
                .getFirst().structures().toString();
    }

    @Test
    void theOldElementsAreRegisteredInsteadOfTheProperties() {
        assertTrue(parsed("set {_x} to name of player").contains("EXPRESSION ExprName,"));
        assertTrue(parsed("set {_x} to x coordinate of {_loc}").contains("EXPRESSION ExprCoordinate,"));
        assertTrue(parsed("set {_x} to text value of {_node}").contains("EXPRESSION ExprValue,"));
        assertTrue(parsed("set {_x} to title of {_bar}").contains("Invalid["));
    }

    @Test
    void theAmountOfOneValueIsAnError() {
        assertEquals(List.of("'(5)' can only ever have one value at most, thus the 'amount of ...' expression is"
                + " useless. Use '... exists' instead to find out whether the expression has a value."),
                errors("set {_x} to amount of 5"));
    }
}
