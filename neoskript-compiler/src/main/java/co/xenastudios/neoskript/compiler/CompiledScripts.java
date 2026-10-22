/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.runtime.Trigger;
import java.util.List;

/**
 * The result of compiling a set of scripts.
 *
 * @param triggers the compiled triggers, in script order
 * @param diagnostics errors for the code that couldn't be compiled. Those triggers are left out.
 * @param unrunnableTests in test mode, the tests that couldn't be compiled, by name, with the reason
 */
public record CompiledScripts(List<CompiledTrigger> triggers, List<Diagnostic> diagnostics,
        java.util.Map<String, String> unrunnableTests) {

    public CompiledScripts {
        triggers = List.copyOf(triggers);
        diagnostics = List.copyOf(diagnostics);
        unrunnableTests = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(unrunnableTests));
    }

    /**
     * A trigger and what runs it: an event, or for a test ({@code test "name":}) its name and a null event.
     */
    public record CompiledTrigger(String script, int line, ScriptEvent event, String testName, Trigger trigger) {}
}
