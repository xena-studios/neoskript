/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
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
 * @param crashed the scripts NeoSkript itself failed to compile something in (a bug, not the script's mistake):
 *     a reload keeps the version of them that was running
 * @param unreachableAfterFailure the lines, as {@code file:line}, warned to be unreachable after a statement that then
 *     failed to compile: that doesn't stop the code, so as in Skript there's no warning
 */
public record CompiledScripts(List<CompiledTrigger> triggers, List<CompiledFunctionBody> functions,
        List<Diagnostic> diagnostics, java.util.Map<String, String> unrunnableTests, List<CompiledCommand> commands,
        java.util.Set<String> unparsedLines, java.util.Set<String> crashed, java.util.Set<String> unreachableAfterFailure,
        java.util.Set<FunctionInfo> declaredSyntax,
        java.util.Map<co.xenastudios.neoskript.runtime.FunctionHandle, java.util.Set<String>> syntaxUses) {

    public CompiledScripts(List<CompiledTrigger> triggers, List<CompiledFunctionBody> functions,
            List<Diagnostic> diagnostics, java.util.Map<String, String> unrunnableTests, List<CompiledCommand> commands,
            java.util.Set<String> unparsedLines, java.util.Set<String> crashed,
            java.util.Set<String> unreachableAfterFailure) {
        this(triggers, functions, diagnostics, unrunnableTests, commands, unparsedLines, crashed,
                unreachableAfterFailure, java.util.Set.of(), java.util.Map.of());
    }

    /** These, with the syntax scripts declared and which scripts use it: see {@link #switchSyntax}. */
    CompiledScripts withDeclaredSyntax(java.util.Set<FunctionInfo> declared,
            java.util.Map<co.xenastudios.neoskript.runtime.FunctionHandle, java.util.Set<String>> uses) {
        return new CompiledScripts(triggers, functions, diagnostics, unrunnableTests, commands, unparsedLines, crashed,
                unreachableAfterFailure, declared, uses);
    }

    /**
     * Once {@code switching} have switched to their new code ({@link #linkFunctions}): the code of syntax they declared
     * before but don't any more is let go of. Its handle is dropped from {@code handles} and cleared, so code still
     * using it (a script that wasn't reloaded) gets an error rather than running the old code, which can be freed. A
     * script that's {@code present} but not switching keeps its syntax. Returns a warning for each piece of syntax
     * that goes while loaded scripts still use it, naming them.
     */
    public List<String> switchSyntax(java.util.Set<String> switching,
            java.util.Map<FunctionInfo, co.xenastudios.neoskript.runtime.FunctionHandle> handles,
            java.util.Set<String> present) {
        for (java.util.Map.Entry<FunctionInfo, co.xenastudios.neoskript.runtime.FunctionHandle> entry : handles.entrySet()) {
            if (entry.getKey().name().startsWith(
                    co.xenastudios.neoskript.frontend.registry.ScriptSyntaxDecl.FUNCTION_PREFIX)) {
                entry.getValue().users().removeAll(switching);
            }
        }
        syntaxUses.forEach((handle, users) -> users.stream().filter(switching::contains).forEach(handle.users()::add));
        List<String> warnings = new java.util.ArrayList<>();
        handles.entrySet().removeIf(entry -> {
            FunctionInfo info = entry.getKey();
            co.xenastudios.neoskript.runtime.FunctionHandle handle = entry.getValue();
            if (!info.name().startsWith(co.xenastudios.neoskript.frontend.registry.ScriptSyntaxDecl.FUNCTION_PREFIX)
                    || declaredSyntax.contains(info)) {
                return false;
            }
            String owner = handle.script();
            if (owner != null && present.contains(owner) && !switching.contains(owner)) {
                return false;
            }
            handle.target(null);
            if (!handle.users().isEmpty()) {
                String header = info.name().substring(
                        co.xenastudios.neoskript.frontend.registry.ScriptSyntaxDecl.FUNCTION_PREFIX.length());
                warnings.add(String.join(", ", new java.util.TreeSet<>(handle.users())) + " still use"
                        + (handle.users().size() == 1 ? "s" : "") + " the custom syntax '" + header + "', which "
                        + (owner == null ? "a script" : owner) + " no longer declares: reload "
                        + (handle.users().size() == 1 ? "it" : "them"));
            }
            return true;
        });
        return warnings;
    }

    public CompiledScripts(List<CompiledTrigger> triggers, List<CompiledFunctionBody> functions,
            List<Diagnostic> diagnostics, java.util.Map<String, String> unrunnableTests, List<CompiledCommand> commands) {
        this(triggers, functions, diagnostics, unrunnableTests, commands, java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of());
    }

    /**
     * Whether a problem found parsing is in a section whose header failed to compile: Skript doesn't read what's in
     * such a section, so it isn't reported.
     */
    public boolean unparsed(Diagnostic parsed) {
        return unparsedLines.contains(parsed.file() + ":" + parsed.line())
                || parsed.message().startsWith(UNREACHABLE) && unreachableAfterFailure.contains(parsed.file() + ":"
                        + parsed.line());
    }

    private static final String UNREACHABLE = "Unreachable code.";

    public CompiledScripts {
        unparsedLines = java.util.Set.copyOf(unparsedLines);
        crashed = java.util.Set.copyOf(crashed);
        unreachableAfterFailure = java.util.Set.copyOf(unreachableAfterFailure);
        commands = List.copyOf(commands);
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
     * A trigger and what runs it: an event NeoSkript fires itself, the classes of the server events it runs
     * for, how often it runs ({@code every 5 minutes}), or for a test ({@code test "name":}) its name. The
     * others are {@code null}. A periodical trigger may run in {@code worlds}, by name
     * ({@code every 5 seconds in "world"}); else they're {@code null}. A server event's trigger has the event's
     * syntax id, and the filter its factory made, if it has one.
     */
    public record CompiledTrigger(String script, int line, ScriptEvent event, java.util.List<Class<?>> eventClasses,
            co.xenastudios.neoskript.runtime.Timespan period, String testName, Trigger trigger,
            java.util.List<String> worlds, String eventId, co.xenastudios.neoskript.api.element.Condition filter) {

        public CompiledTrigger(String script, int line, ScriptEvent event, java.util.List<Class<?>> eventClasses,
                co.xenastudios.neoskript.runtime.Timespan period, String testName, Trigger trigger,
                java.util.List<String> worlds) {
            this(script, line, event, eventClasses, period, testName, trigger, worlds, null, null);
        }

        public CompiledTrigger(String script, int line, ScriptEvent event, java.util.List<Class<?>> eventClasses,
                co.xenastudios.neoskript.runtime.Timespan period, String testName, Trigger trigger) {
            this(script, line, event, eventClasses, period, testName, trigger, null);
        }
    }
}
