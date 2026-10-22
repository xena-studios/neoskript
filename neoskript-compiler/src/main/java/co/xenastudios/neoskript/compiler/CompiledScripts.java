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
 */
public record CompiledScripts(List<CompiledTrigger> triggers, List<Diagnostic> diagnostics) {

    public CompiledScripts {
        triggers = List.copyOf(triggers);
        diagnostics = List.copyOf(diagnostics);
    }

    /** A trigger and what runs it. */
    public record CompiledTrigger(String script, int line, ScriptEvent event, Trigger trigger) {}
}
