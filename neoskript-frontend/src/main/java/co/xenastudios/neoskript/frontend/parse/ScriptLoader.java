/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Diagnostics;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.frontend.structure.Line;
import co.xenastudios.neoskript.frontend.structure.Node;
import co.xenastudios.neoskript.frontend.structure.ScriptParser;
import co.xenastudios.neoskript.frontend.structure.Structure;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a set of scripts that load together. Function signatures are collected from every script first,
 * so scripts can call each other's functions whatever order they're in. The scripts are then parsed in
 * parallel.
 */
public final class ScriptLoader {

    private static final Pattern FUNCTION_HEADER = Pattern.compile(
            "(local\\s+)?function\\s+([\\p{L}_][\\p{L}\\p{N}_]*)\\s*\\((.*)\\)\\s*(?:(?:::|->|returns)\\s*(.+))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAMETER =
            Pattern.compile("\\s*([\\p{L}_][\\p{L}\\p{N}_]*)\\s*:\\s*(.+?)(?:\\s*=\\s*(.+))?\\s*");
    /** Skript's test-only top-level {@code parse:} block, whose {@code code:} holds structures. */
    private static final String PARSE_STRUCTURE = "parse";
    private static final Pattern EVENT_MODIFIERS = Pattern.compile(
            "(?:on\\s+)?(?:(?:uncancelled|cancelled|any|all)\\s+)?(.*?)"
                    + "(?:\\s+with priority\\s+(?:lowest|low|normal|high|highest|monitor))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ON_PREFIX = Pattern.compile("(?:on\\s+)?(.*?)", Pattern.CASE_INSENSITIVE);

    private final SyntaxRegistry registry;
    /** Function headers whose signature another function already has, with their script. */
    private Map<Line, String> duplicates = Map.of();
    /** Function headers registered while collecting signatures (those outside parse blocks). */
    private Map<Line, Boolean> registered = Map.of();
    private Map<String, List<FunctionInfo>> functions = Map.of();

    public ScriptLoader(SyntaxRegistry registry) {
        this.registry = registry;
    }

    /**
     * Parses one expression on its own, as {@code type} (a type name, or {@code objects} for anything).
     * Returns {@code null} if it doesn't parse. For tools and tests.
     */
    public Expr parseExpression(String text, String type) {
        ParserContext context = new ParserContext(registry, Map.of());
        TypeRef ref = registry.resolveType(type);
        Expected expected = ref == null ? Expected.OBJECTS : context.expected(ref);
        return new LineParser(context, text).expression(0, text.length(), expected);
    }

    public List<ParsedScript> load(List<SourceFile> files) {
        List<ScriptParser.Result> results = files.parallelStream().map(ScriptParser::parse).toList();
        Map<String, List<FunctionInfo>> functions = new HashMap<>();
        Map<String, Map<String, List<FunctionInfo>>> locals = new HashMap<>();
        Map<Line, String> duplicates = new java.util.IdentityHashMap<>();
        Map<Line, Boolean> registered = new java.util.IdentityHashMap<>();
        for (ScriptParser.Result result : results) {
            String script = result.file().name();
            for (Structure structure : result.structures()) {
                if (structure.kind() == Structure.Kind.FUNCTION) {
                    Line line = structure.section().line();
                    FunctionInfo signature = signature(line, null, script);
                    if (signature == null) {
                        continue;
                    }
                    Map<String, List<FunctionInfo>> scope = signature.localTo() == null
                            ? functions
                            : locals.computeIfAbsent(script, k -> new HashMap<>());
                    List<FunctionInfo> overloads = scope.computeIfAbsent(signature.name(), k -> new ArrayList<>());
                    if (overloads.stream().anyMatch(f -> sameParameters(f, signature))) {
                        duplicates.put(line, script);
                    } else {
                        overloads.add(signature);
                        registered.put(line, Boolean.TRUE);
                    }
                }
            }
        }
        this.duplicates = duplicates;
        this.registered = registered;
        this.functions = functions;
        return results.parallelStream()
                .map(result -> parse(result, new ParserContext(registry, functions,
                        locals.getOrDefault(result.file().name(), Map.of()))))
                .toList();
    }

    private ParsedScript parse(ScriptParser.Result result, ParserContext context) {
        Diagnostics diagnostics = new Diagnostics(result.file());
        for (Diagnostic diagnostic : result.diagnostics()) {
            diagnostics.add(diagnostic);
        }
        java.util.Set<String> aliases = new java.util.HashSet<>();
        for (Structure structure : result.structures()) {
            if (structure.kind() == Structure.Kind.ALIASES) {
                aliases.addAll(AliasNames.of(structure.section()));
            }
        }
        context.itemAliases(aliases);
        StatementParser statements = new StatementParser(context, diagnostics);
        List<ParsedStructure> parsed = new ArrayList<>();
        for (Structure structure : result.structures()) {
            ParsedStructure p = structure(structure, context, statements, diagnostics);
            if (p != null) {
                parsed.add(p);
            }
        }
        return new ParsedScript(result.file(), parsed, diagnostics.all(), statements.delays());
    }

    private ParsedStructure structure(Structure structure, ParserContext context, StatementParser statements,
            Diagnostics diagnostics) {
        return switch (structure.kind()) {
            case OPTIONS, VARIABLES, ALIASES, USING -> new ParsedStructure.Data(structure);
            case FUNCTION -> function(structure, statements, diagnostics, diagnostics.file().name());
            case COMMAND -> new CommandParser(registry, statements, diagnostics).parse(structure);
            case EVENT -> structure.section().line().key().equalsIgnoreCase(PARSE_STRUCTURE)
                    ? parseBlock(structure, context, statements, diagnostics)
                    : event(structure, context, statements, diagnostics);
        };
    }

    /** Parses the structures in a top-level {@code parse:} block's {@code code:}, capturing their problems. */
    private ParsedStructure parseBlock(Structure structure, ParserContext context, StatementParser statements,
            Diagnostics diagnostics) {
        int mark = diagnostics.mark();
        List<ParsedStructure> code = new ArrayList<>();
        for (Node child : structure.section().children()) {
            if (child instanceof Node.Section section && section.line().key().equalsIgnoreCase("code")) {
                for (Node node : section.children()) {
                    if (node instanceof Node.Section inner) {
                        ParsedStructure p = structure(new Structure(Structure.kindOf(inner.line()), inner), context,
                                statements, diagnostics);
                        if (p != null) {
                            code.add(p);
                        }
                    }
                }
            }
        }
        return new ParsedStructure.Parse(structure, code, diagnostics.takeSince(mark));
    }

    private ParsedStructure event(Structure structure, ParserContext context, StatementParser statements,
            Diagnostics diagnostics) {
        Node.Section section = structure.section();
        Line line = section.line();
        String key = line.key();
        LineParser parser = new LineParser(context, key);
        ParsedSyntax custom = parser.structure(0, key.length());
        if (custom != null) {
            return new ParsedStructure.Custom(structure, custom, statements.body(section.children()));
        }
        ParsedSyntax event = eventHeader(context, key, ON_PREFIX);
        if (event == null) {
            event = eventHeader(context, key, EVENT_MODIFIERS);
        }
        if (event == null) {
            diagnostics.error(line.number(), line.column(), key.length(), "Can't understand this event: '" + key + "'");
            return null;
        }
        return new ParsedStructure.Event(structure, event, statements.body(section.children()));
    }

    private static ParsedSyntax eventHeader(ParserContext context, String key, Pattern modifiers) {
        Matcher matcher = modifiers.matcher(key);
        if (!matcher.matches() || matcher.group(1).isBlank()) {
            return null;
        }
        LineParser parser = new LineParser(context, key);
        return parser.event(matcher.start(1), matcher.end(1));
    }

    private ParsedStructure function(Structure structure, StatementParser statements, Diagnostics diagnostics,
            String scriptName) {
        Line line = structure.section().line();
        FunctionInfo signature = signature(line, diagnostics, scriptName);
        if (signature == null) {
            return null;
        }
        String duplicateIn = duplicates.get(line);
        boolean inParseBlock = !registered.containsKey(line) && duplicateIn == null;
        if (duplicateIn != null || inParseBlock && functions.getOrDefault(signature.name(), List.of()).stream()
                .anyMatch(f -> sameParameters(f, signature))) {
            report(diagnostics, line, "Function '" + signature.name() + "' with the same argument types already exists"
                    + " in script" + (duplicateIn == null ? "" : " '" + duplicateIn + "'"));
            return null;
        }
        boolean local = line.key().strip().toLowerCase(java.util.Locale.ROOT).startsWith("local");
        List<String> defaultTexts = defaultTexts(line);
        List<Expr> defaults = new ArrayList<>();
        for (int i = 0; i < signature.parameters().size(); i++) {
            FunctionInfo.Parameter parameter = signature.parameters().get(i);
            String text = defaultTexts.get(i);
            defaults.add(text == null ? null : statements.expression(line, text, parameter.type(), parameter.plural()));
        }
        return new ParsedStructure.Function(structure, signature, local, defaults, defaultTexts,
                statements.body(structure.section().children()));
    }

    /** The text of each parameter's default value in a function header, or null where there's none. */
    private static List<String> defaultTexts(Line line) {
        Matcher header = FUNCTION_HEADER.matcher(line.key().strip());
        List<String> texts = new ArrayList<>();
        if (!header.matches() || header.group(3).isBlank()) {
            return texts;
        }
        String list = header.group(3).strip();
        for (int[] piece : Scanner.split(list, 0, list.length(), ',')) {
            Matcher parameter = PARAMETER.matcher(list.substring(piece[0], piece[1]));
            texts.add(parameter.matches() && parameter.group(3) != null ? parameter.group(3).strip() : null);
        }
        return texts;
    }

    /** Parses a function header. Reports problems if {@code diagnostics} isn't null. */
    private FunctionInfo signature(Line line, Diagnostics diagnostics, String scriptName) {
        Matcher header = FUNCTION_HEADER.matcher(line.key().strip());
        if (!header.matches()) {
            report(diagnostics, line, "Invalid function header: " + line.key());
            return null;
        }
        List<FunctionInfo.Parameter> parameters = new ArrayList<>();
        String list = header.group(3).strip();
        if (!list.isEmpty()) {
            for (int[] piece : Scanner.split(list, 0, list.length(), ',')) {
                String text = list.substring(piece[0], piece[1]);
                Matcher parameter = PARAMETER.matcher(text);
                TypeRef type = parameter.matches() ? registry.resolveType(parameter.group(2)) : null;
                if (type == null) {
                    report(diagnostics, line, "Invalid function parameter: '" + text.strip() + "'");
                    return null;
                }
                parameters.add(new FunctionInfo.Parameter(parameter.group(1), type.type().id(), type.plural(),
                        parameter.group(3) != null));
            }
        }
        String returnType = null;
        boolean returnsPlural = false;
        if (header.group(4) != null) {
            TypeRef type = registry.resolveType(header.group(4));
            if (type == null) {
                report(diagnostics, line, "Invalid function return type: '" + header.group(4).strip() + "'");
                return null;
            }
            returnType = type.type().id();
            returnsPlural = type.plural();
        }
        return new FunctionInfo(header.group(2), parameters, returnType, returnsPlural,
                header.group(1) != null ? scriptName : null);
    }

    /** Whether two overloads take the same types, so a call couldn't tell them apart. */
    private static boolean sameParameters(FunctionInfo a, FunctionInfo b) {
        if (a.parameters().size() != b.parameters().size()) {
            return false;
        }
        for (int i = 0; i < a.parameters().size(); i++) {
            FunctionInfo.Parameter x = a.parameters().get(i);
            FunctionInfo.Parameter y = b.parameters().get(i);
            if (!x.type().equals(y.type()) || x.plural() != y.plural()) {
                return false;
            }
        }
        return true;
    }

    private static void report(Diagnostics diagnostics, Line line, String message) {
        if (diagnostics != null) {
            diagnostics.error(line.number(), line.column(), line.text().length(), message);
        }
    }
}
