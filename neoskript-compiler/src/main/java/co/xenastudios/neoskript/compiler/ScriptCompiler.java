/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ParsedStructure;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.structure.Line;
import java.util.ArrayList;
import java.util.List;

/** Compiles parsed scripts into triggers. */
public final class ScriptCompiler {

    private final Library library;

    public ScriptCompiler(Library library) {
        this.library = library;
    }

    public CompiledScripts compile(List<ParsedScript> scripts) {
        List<CompiledScripts.CompiledTrigger> triggers = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ParsedScript script : scripts) {
            String name = script.file().name();
            for (ParsedStructure structure : script.structures()) {
                Line header = structure.structure().node().line();
                try {
                    switch (structure) {
                        case ParsedStructure.Event event -> {
                            if (!(library.binding(event.event().syntax()) instanceof ScriptEvent scriptEvent)) {
                                throw new CompileException(header.number(), "NeoSkript can't run this event yet: '"
                                        + header.key() + "'");
                            }
                            triggers.add(new CompiledScripts.CompiledTrigger(name, header.number(), scriptEvent,
                                    new TriggerCompiler(library).compile(name, event.body())));
                        }
                        case ParsedStructure.Data data -> {}
                        default -> throw new CompileException(header.number(),
                                "NeoSkript can't run this yet: '" + header.key() + "'");
                    }
                } catch (CompileException e) {
                    diagnostics.add(new Diagnostic(Severity.ERROR, name, e.line(), 0, 0, e.getMessage()));
                }
            }
        }
        return new CompiledScripts(triggers, diagnostics);
    }
}
