/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ParsedStructure;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.structure.Line;
import co.xenastudios.neoskript.runtime.FunctionHandle;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles parsed scripts into triggers and functions. Calls to script functions go through a
 * {@link FunctionHandle} per function, so scripts can call each other whatever order they compile in.
 */
public final class ScriptCompiler {

    private final Library library;
    private final VariableStore globals;

    /** @param globals where global variables live; generated code refers to it directly */
    public ScriptCompiler(Library library, VariableStore globals) {
        this.library = library;
        this.globals = globals;
    }

    public CompiledScripts compile(List<ParsedScript> scripts) {
        List<CompiledScripts.CompiledTrigger> triggers = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<FunctionInfo, FunctionHandle> functions = new HashMap<>();
        for (ParsedScript script : scripts) {
            for (ParsedStructure structure : script.structures()) {
                if (structure instanceof ParsedStructure.Function function) {
                    functions.putIfAbsent(function.signature(), new FunctionHandle(function.signature().name()));
                }
            }
        }
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
                                    new TriggerCompiler(library, functions, globals).compileTrigger(name, event.body())));
                        }
                        case ParsedStructure.Function function -> functions.get(function.signature()).target(
                                new TriggerCompiler(library, functions, globals).compileFunction(name, function));
                        case ParsedStructure.Data data -> {}
                        default -> throw new CompileException(header.number(),
                                "NeoSkript can't run this yet: '" + header.key() + "'");
                    }
                } catch (CompileException e) {
                    if (!e.alreadyReported()) {
                        diagnostics.add(new Diagnostic(Severity.ERROR, name, e.line(), 0, 0, e.getMessage()));
                    }
                }
            }
        }
        return new CompiledScripts(triggers, diagnostics);
    }
}
