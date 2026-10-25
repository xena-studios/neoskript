/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.compiler.Library;
import co.xenastudios.neoskript.compiler.ScriptCompiler;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.FunctionHandle;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/** Skript's effect commands: one effect compiled on its own, with the loaded scripts' functions and variables. */
class EffectCommandsTest {

    private final List<String> said = new ArrayList<>();
    private final Library library = new Library();
    private final VariableStore globals = new VariableStore();
    private final Map<FunctionInfo, FunctionHandle> functions = new ConcurrentHashMap<>();

    EffectCommandsTest() {
        StandardLibrary.register(library);
        library.effect("TestSay", info -> (context, args) -> said.add(Values.text(args[0])), "say %objects%");
    }

    /** Loads a script, as the plugin does, so its functions can be called. */
    private void load(String script) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("a.sk", script)));
        assertEquals(List.of(), parsed.getFirst().diagnostics());
        CompiledScripts compiled = new ScriptCompiler(library, globals, functions).compile(parsed);
        assertEquals(List.of(), compiled.diagnostics());
        compiled.linkFunctions();
    }

    private ScriptCompiler.CompiledLine compile(String effect) {
        return new ScriptCompiler(library, globals, functions).compileLine("effect command", effect,
                EffectCommandEvent.class, "effect command");
    }

    private void run(String effect) {
        ScriptCompiler.CompiledLine compiled = compile(effect);
        assertEquals(List.of(), compiled.errors());
        assertNotNull(compiled.trigger());
        compiled.trigger().execute(new SimpleTriggerContext(new EffectCommandEvent(null, effect)));
    }

    @Test
    void runsEffectsWithTheGlobalVariablesAndFunctions() {
        load("""
                function double(n: number) :: number:
                \treturn {_n} * 2
                local function hidden():
                \tsay "hidden"
                """);
        run("set {effcmd} to double(21)");
        assertEquals(42L, globals.get("effcmd"));
        run("say {effcmd} + 1");
        assertEquals(List.of("43"), said);
        // Local functions belong to their script, which an effect command isn't.
        assertNull(compile("hidden()").trigger());
    }

    @Test
    void conditionsAndUnknownLinesHaveNoSpecificError() {
        ScriptCompiler.CompiledLine condition = compile("1 is 1");
        assertNull(condition.trigger());
        assertEquals(List.of(), condition.errors());
        ScriptCompiler.CompiledLine unknown = compile("this is not an effect at all");
        assertNull(unknown.trigger());
        assertEquals(List.of(), unknown.errors());
    }

    @Test
    void theSenderIsMeAndThePlayer() {
        // Without a player, they're nothing.
        run("say \"%me%|%player%|%command sender%|%myself%\"");
        assertEquals(List.of("<none>|<none>|<none>|<none>"), said);
    }

    @Test
    void meIsOnlyInEffectCommands() {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("a.sk", """
                on load:
                \tset {_x} to me
                """)));
        CompiledScripts compiled = new ScriptCompiler(library, globals, functions).compile(parsed);
        assertTrue(compiled.diagnostics().stream().anyMatch(d -> d.message().contains("'me'")),
                compiled.diagnostics().toString());
    }
}
