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
        Map<String, String> unrunnableTests = new java.util.LinkedHashMap<>();
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
            Map<Integer, String> parseProblems = new HashMap<>();
            script.diagnostics().forEach(d -> parseProblems.putIfAbsent(d.line(), d.message()));
            for (ParsedStructure structure : script.structures()) {
                Line header = structure.structure().node().line();
                String testName = testName(structure);
                try {
                    switch (structure) {
                        case ParsedStructure.Event event -> {
                            if (!(library.binding(event.event().syntax()) instanceof ScriptEvent scriptEvent)) {
                                throw new CompileException(header.number(), "NeoSkript can't run this event yet: '"
                                        + header.key() + "'");
                            }
                            triggers.add(new CompiledScripts.CompiledTrigger(name, header.number(), scriptEvent, null,
                                    new TriggerCompiler(library, functions, globals, script).compileTrigger(name, event.body())));
                        }
                        case ParsedStructure.Function function -> functions.get(function.signature()).target(
                                new TriggerCompiler(library, functions, globals, script).compileFunction(name, function));
                        case ParsedStructure.Custom custom when testName != null -> triggers.add(
                                new CompiledScripts.CompiledTrigger(name, header.number(), null, testName,
                                        new TriggerCompiler(library, functions, globals, script).compileTest(name, custom)));
                        case ParsedStructure.Data data -> {}
                        case ParsedStructure.Parse parse -> parseResults(parse);
                        default -> throw new CompileException(header.number(),
                                "NeoSkript can't run this yet: '" + header.key() + "'");
                    }
                } catch (CompileException e) {
                    if (!e.alreadyReported()) {
                        diagnostics.add(new Diagnostic(Severity.ERROR, name, e.line(), 0, 0, e.getMessage()));
                    }
                    if (testName != null) {
                        String reason = e.alreadyReported() ? parseProblems.get(e.line()) : e.getMessage();
                        unrunnableTests.put(testName, "line " + e.line() + ": " + reason);
                    }
                }
            }
        }
        return new CompiledScripts(triggers, diagnostics, unrunnableTests);
    }

    /**
     * Skript's test-only top-level {@code parse:} block: the problems its code had are stored in the list
     * variable named by its {@code results:} entry, for tests to check.
     */
    private void parseResults(ParsedStructure.Parse parse) {
        for (co.xenastudios.neoskript.frontend.structure.Node child : parse.structure().section().children()) {
            String text = child.line().text().strip();
            if (text.toLowerCase(java.util.Locale.ROOT).startsWith("results:")) {
                String variable = text.substring("results:".length()).strip();
                if (variable.startsWith("{") && variable.endsWith("::*}")) {
                    globals.setList(variable.substring(1, variable.length() - 4),
                            parse.captured().stream().map(Diagnostic::message).toArray());
                }
            }
        }
    }

    /** The name of a {@code test "name":} structure, or {@code null} if it isn't one. */
    private String testName(ParsedStructure structure) {
        if (!(structure instanceof ParsedStructure.Custom custom)
                || !(library.binding(custom.syntax().syntax()) instanceof Library.Core core)
                || !core.id().equals(CoreSyntax.TEST)
                || !(custom.syntax().match().values().getFirst() instanceof co.xenastudios.neoskript.frontend.parse.Expr.Text text)) {
            return null;
        }
        String key = custom.structure().node().line().key();
        return key.substring(text.start() + 1, text.end() - 1).replace("\"\"", "\"");
    }
}
