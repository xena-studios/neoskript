/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.runtime.CompiledFunction;
import co.xenastudios.neoskript.runtime.FunctionHandle;
import co.xenastudios.neoskript.runtime.Trigger;
import java.util.List;

/**
 * The result of compiling a set of scripts.
 *
 * @param triggers the compiled triggers, in script order
 * @param functions the compiled functions. Calls only reach them once they're linked: {@link #linkFunctions}
 * @param diagnostics errors for the code that couldn't be compiled. Those triggers are left out.
 * @param unrunnableTests in test mode, the tests that couldn't be compiled, by name, with the reason
 */
public record CompiledScripts(List<CompiledTrigger> triggers, List<CompiledFunctionBody> functions,
        List<Diagnostic> diagnostics, java.util.Map<String, String> unrunnableTests) {

    public CompiledScripts {
        triggers = List.copyOf(triggers);
        functions = List.copyOf(functions);
        diagnostics = List.copyOf(diagnostics);
        unrunnableTests = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(unrunnableTests));
    }

    /** Makes calls to every compiled function run the new code. */
    public void linkFunctions() {
        linkFunctions(java.util.Set.copyOf(functions.stream().map(CompiledFunctionBody::script).toList()));
    }

    /** Makes calls to the functions of {@code scripts} run the new code. */
    public void linkFunctions(java.util.Set<String> scripts) {
        for (CompiledFunctionBody function : functions) {
            if (scripts.contains(function.script())) {
                function.handle().target(function.body());
            }
        }
    }

    /** A function's new code, and the handle calls go through. */
    public record CompiledFunctionBody(String script, FunctionHandle handle, CompiledFunction body) {}

    /**
     * A trigger and what runs it: an event, or for a test ({@code test "name":}) its name and a null event.
     */
    public record CompiledTrigger(String script, int line, ScriptEvent event, String testName, Trigger trigger) {}
}
