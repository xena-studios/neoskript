/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.parse.CommandPattern;
import co.xenastudios.neoskript.frontend.parse.Expr;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ParsedStructure;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Severity;
import co.xenastudios.neoskript.frontend.structure.Line;
import co.xenastudios.neoskript.runtime.FunctionHandle;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.Trigger;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles parsed scripts into triggers and functions. Calls to script functions go through a
 * {@link FunctionHandle} per function, so scripts can call each other whatever order they compile in.
 */
public final class ScriptCompiler {

    private final Library library;
    private final VariableStore globals;
    private final Map<FunctionInfo, FunctionHandle> handles;

    /** @param globals where global variables live; generated code refers to it directly */
    public ScriptCompiler(Library library, VariableStore globals) {
        this(library, globals, new java.util.concurrent.ConcurrentHashMap<>());
    }

    /**
     * @param handles the function handles to call functions through, kept from one load to the next so
     *     scripts that aren't reloaded keep calling the current version. Missing ones are added.
     */
    public ScriptCompiler(Library library, VariableStore globals, Map<FunctionInfo, FunctionHandle> handles) {
        this.library = library;
        this.globals = globals;
        this.handles = handles;
    }

    public CompiledScripts compile(List<ParsedScript> scripts) {
        List<CompiledScripts.CompiledTrigger> triggers = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<String, String> unrunnableTests = new java.util.LinkedHashMap<>();
        Map<FunctionInfo, FunctionHandle> functions = handles;
        List<CompiledScripts.CompiledFunctionBody> bodies = new ArrayList<>();
        List<CompiledCommand> commands = new ArrayList<>();
        for (ParsedScript script : scripts) {
            for (ParsedStructure structure : script.structures()) {
                if (structure instanceof ParsedStructure.Function function) {
                    functions.computeIfAbsent(function.signature(), f -> new FunctionHandle(f.name()));
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
                            Object binding = library.binding(event.event().syntax());
                            if (binding instanceof ScriptEvent scriptEvent) {
                                triggers.add(new CompiledScripts.CompiledTrigger(name, header.number(), scriptEvent, null,
                                        null, new TriggerCompiler(library, functions, globals, script)
                                                .compileTrigger(name, event.body())));
                            } else if (binding instanceof Library.ServerEvent serverEvent) {
                                triggers.add(new CompiledScripts.CompiledTrigger(name, header.number(), null,
                                        serverEvent.classes(), null, new TriggerCompiler(library, functions, globals,
                                                script).compileTrigger(name, event, serverEvent)));
                            } else {
                                throw new CompileException(header.number(), "NeoSkript can't run this event yet: '"
                                        + header.key() + "'");
                            }
                        }
                        case ParsedStructure.Function function -> bodies.add(new CompiledScripts.CompiledFunctionBody(name,
                                functions.get(function.signature()),
                                new TriggerCompiler(library, functions, globals, script).compileFunction(name, function)));
                        case ParsedStructure.Custom custom when testName != null -> triggers.add(
                                new CompiledScripts.CompiledTrigger(name, header.number(), null, null, testName,
                                        new TriggerCompiler(library, functions, globals, script).compileTest(name, custom)));
                        case ParsedStructure.Command command -> commands.add(command(name, script, functions, command));
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
        return new CompiledScripts(triggers, bodies, diagnostics, unrunnableTests, commands);
    }

    /** Compiles a command: a trigger for each route, and its messages. */
    private CompiledCommand command(String name, ParsedScript script, Map<FunctionInfo, FunctionHandle> functions,
            ParsedStructure.Command command) throws CompileException {
        ParsedStructure.CommandNode root = command.root();
        Map<String, String> entries = root.entries();
        int line = command.structure().node().line().number();
        if (entries.containsKey("cooldown storage")) {
            throw new CompileException(line, "NeoSkript can't store cooldowns in variables yet ('cooldown storage')");
        }
        Timespan cooldown = null;
        if (entries.containsKey("cooldown")) {
            cooldown = Timespan.parse(entries.get("cooldown"));
            if (cooldown == null) {
                throw new CompileException(line, "'" + entries.get("cooldown") + "' is not a timespan");
            }
        }
        List<String> aliases = new ArrayList<>();
        if (entries.containsKey("aliases")) {
            for (String alias : ALIAS_SEPARATOR.split(entries.get("aliases").strip())) {
                String a = alias.strip();
                if (a.startsWith("/")) {
                    a = a.substring(1);
                }
                if (!a.isEmpty()) {
                    aliases.add(a);
                }
            }
        }
        List<CompiledCommand.Route> routes = new ArrayList<>();
        routes(name, script, functions, root, List.of(), Set.of(), entries.get("permission"),
                command.structure().node().line().key(), routes);
        return new CompiledCommand(name, line, command.name(), aliases, entries.get("description"), entries.get("usage"),
                entries.get("permission"), message(name, script, functions, root, "permission message"), cooldown,
                entries.get("cooldown bypass"), message(name, script, functions, root, "cooldown message"),
                entries.get("prefix"), routes);
    }

    private static final java.util.regex.Pattern ALIAS_SEPARATOR =
            java.util.regex.Pattern.compile("\\s*,\\s*(?:and\\s+)?|\\s+and\\s+");

    /** Adds the routes of {@code node} and its subcommands, subcommands first. */
    private void routes(String name, ParsedScript script, Map<FunctionInfo, FunctionHandle> functions,
            ParsedStructure.CommandNode node, List<CommandPattern.Part> prefix, Set<String> executors, String permission,
            String header, List<CompiledCommand.Route> routes) throws CompileException {
        List<CommandPattern.Part> pattern = new ArrayList<>(prefix);
        pattern.addAll(node.pattern());
        if (node.entries().containsKey("executable by")) {
            executors = executors(node.entries().get("executable by"));
        }
        if (node.entries().containsKey("permission")) {
            permission = node.entries().get("permission");
        }
        for (ParsedStructure.CommandNode subcommand : node.subcommands()) {
            routes(name, script, functions, subcommand, pattern, executors, permission,
                    subcommand.group() ? header : subcommand.line().key(), routes);
        }
        if (node.trigger() != null) {
            List<CommandPattern.Argument> arguments = new ArrayList<>();
            CommandPattern.arguments(pattern, arguments);
            List<CommandPattern.Choice> choices = new ArrayList<>();
            CommandPattern.choices(pattern, choices);
            Trigger trigger = new TriggerCompiler(library, functions, globals, script)
                    .compileCommand(name, node.line().number(), header, arguments, choices.size(), node.trigger());
            routes.add(new CompiledCommand.Route(pattern, executors, permission, trigger,
                    new CompiledCommand.Matcher(pattern, arguments, choices, library::parser)));
        }
    }

    private static Set<String> executors(String entry) {
        Set<String> executors = new java.util.LinkedHashSet<>();
        for (String word : entry.strip().toLowerCase(java.util.Locale.ROOT)
                .split("\\s*(?:,\\s*(?:and\\s+|or\\s+)?|\\s+and\\s+|\\s+or\\s+)\\s*")) {
            executors.add(switch (word) {
                case "player", "players" -> "players";
                case "console", "the console" -> "console";
                case "op", "ops", "operator", "operators" -> "operators";
                case "block", "blocks", "command block", "command blocks" -> "command blocks";
                default -> "entities";
            });
        }
        return executors;
    }

    /** A trigger working out one of the command's messages, or {@code null} if it doesn't have it. */
    private Trigger message(String name, ParsedScript script, Map<FunctionInfo, FunctionHandle> functions,
            ParsedStructure.CommandNode root, String entry) throws CompileException {
        Expr message = root.messages().get(entry);
        if (message == null) {
            return null;
        }
        int line = root.line().number();
        String value = root.entries().get(entry);
        return new TriggerCompiler(library, functions, globals, script).compileCommandMessage(name, line,
                '"' + value.replace("\"", "\"\"") + '"', message);
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
