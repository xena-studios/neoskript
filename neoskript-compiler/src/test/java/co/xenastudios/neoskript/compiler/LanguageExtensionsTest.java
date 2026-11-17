/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The opt-in for NeoSkript's language extensions ({@code using neoskript <name>}), with an extension made up for the
 * test: a script without the opt-in is parsed and run exactly as before, as on Skript.
 */
class LanguageExtensionsTest {

    private final List<String> said = new ArrayList<>();
    private Library library;

    @BeforeEach
    void library() {
        library = new Library();
        library.type("string", "text", String.class);
        library.type("number", "number", Number.class);
        library.event("ScriptLoad", ScriptEvent.LOAD, "[script] load");
        library.effect("Say", info -> (context, args) -> said.add(Arrays.toString((Object[]) args[0])), "say %objects%");
        // An extension's syntax: an element registered as experimental with the experiment 'neoskript <name>'.
        library.effect("Shout", info -> (context, args) -> said.add("SHOUT " + Arrays.toString((Object[]) args[0])),
                "shout %objects%");
        library.experimental("Shout", "neoskript test shouting", false);
    }

    private ParsedScript parse(String text) {
        return new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", text))).getFirst();
    }

    private static List<String> problems(List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(d -> d.line() + ": " + d.message()).toList();
    }

    private CompiledScripts compile(ParsedScript script) {
        return new ScriptCompiler(library, new co.xenastudios.neoskript.runtime.variables.VariableStore())
                .compile(List.of(script));
    }

    @Test
    void theRegistryKnowsTheExtensionsItsSyntaxNeeds() {
        assertEquals(Set.of("test shouting"), library.registry().languageExtensions());
    }

    @Test
    void aScriptWithoutTheOptInIsUnaffected() {
        // The extension's syntax isn't there, and the error is Skript's own: nothing names the extension.
        ParsedScript script = parse("on load:\n\tsay \"a\"\n\tshout \"b\"\n\tsay \"c\"\n");
        assertEquals(List.of("3: Can't understand this condition/effect: shout \"b\""), problems(script.diagnostics()));
        assertEquals(Set.of(), script.extensions());
        CompiledScripts compiled = compile(script);
        assertEquals(List.of(), compiled.diagnostics());
        compiled.triggers().getFirst().trigger().execute(new SimpleTriggerContext(null));
        assertEquals(List.of("[a]", "[c]"), said);
    }

    @Test
    void aScriptThatAsksForTheExtensionGetsItsSyntax() {
        ParsedScript script = parse("using neoskript test shouting\non load:\n\tshout \"b\"\n");
        assertEquals(List.of(), problems(script.diagnostics()));
        assertEquals(Set.of("test shouting"), script.extensions());
        CompiledScripts compiled = compile(script);
        assertEquals(List.of(), compiled.diagnostics(), "not Skript's warning that the experiment wasn't found");
        compiled.triggers().getFirst().trigger().execute(new SimpleTriggerContext(null));
        assertEquals(List.of("SHOUT [b]"), said);
    }

    @Test
    void anExtensionThatDoesntExistIsAnError() {
        ParsedScript script = parse("using neoskript  Nothing  Here\non load:\n\tsay \"a\"\n");
        assertEquals(List.of("1: NeoSkript has no language extension called 'nothing here'"),
                problems(script.diagnostics()));
        assertEquals(Set.of("nothing here"), script.extensions());
    }

    @Test
    void skriptsOwnExperimentsArentExtensions() {
        ParsedScript script = parse("using script reflection\non load:\n\tsay \"a\"\n");
        assertEquals(List.of(), problems(script.diagnostics()));
        assertEquals(Set.of(), script.extensions());
        assertEquals(List.of(), compile(script).diagnostics());
    }
}
