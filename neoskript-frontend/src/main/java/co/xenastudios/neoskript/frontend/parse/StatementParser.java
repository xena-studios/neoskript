/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.registry.DelayState;
import co.xenastudios.neoskript.frontend.registry.EventValues;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.registry.SyntaxKind;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.ParseCheck;
import co.xenastudios.neoskript.frontend.registry.TypeInfo;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.Diagnostics;
import co.xenastudios.neoskript.frontend.structure.Line;
import co.xenastudios.neoskript.frontend.structure.Node;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the lines of a trigger, function or other code section into statements.
 *
 * <p>It also tracks whether the trigger may have waited by each line, as Skript does: each branch of an
 * {@code if} chain starts from the state before the chain, and the state after it is merged from every
 * branch (see {@link DelayState#merge}). Code that may run any number of times (a loop) can only make the
 * state "maybe".
 */
final class StatementParser {

    /** Skript's test-only section whose problems are captured for the test to check. */
    static final String PARSE_SECTION = "parse";

    /** {@code if any:}, {@code else if all:} and the like, whose lines are conditions. */
    private static final Pattern MULTILINE_CONDITIONAL =
            Pattern.compile("(else )?(parse )?if( (any|at least one( of)?|all))?", Pattern.CASE_INSENSITIVE);

    private static final Pattern PARSE_IF = Pattern.compile("(else )?parse if .*", Pattern.CASE_INSENSITIVE);

    private final ParserContext context;
    private final Diagnostics diagnostics;
    /** The delay state before each statement's line, for the compiler. */
    private final Map<Line, DelayState> delays = new IdentityHashMap<>();
    private DelayState delay = DelayState.NOT_DELAYED;
    /** The warnings the script suppresses so far, by the tag naming them ({@code unreachable}). */
    private final java.util.Set<String> suppressed = new java.util.HashSet<>();
    /** What was suppressed at each statement's and structure's line, for the compiler's warnings (by identity). */
    private final Map<Line, java.util.Set<String>> suppressions = new IdentityHashMap<>();

    /** The {@code if} chain being parsed in the current block. */
    private static final class Chain {
        final DelayState before;
        final List<DelayState> ends = new ArrayList<>();
        boolean hasElse;

        Chain(DelayState before) {
            this.before = before;
        }
    }

    StatementParser(ParserContext context, Diagnostics diagnostics) {
        this.context = context;
        this.diagnostics = diagnostics;
    }

    /** The delay state before each line parsed so far, by line. */
    Map<Line, DelayState> delays() {
        return delays;
    }

    /** The warnings suppressed at each line noted so far, by line. */
    Map<Line, java.util.Set<String>> suppressions() {
        return suppressions;
    }

    /** Notes what's suppressed at {@code line}: a statement's, or a structure's header. */
    void note(Line line) {
        suppressions.put(line, java.util.Set.copyOf(suppressed));
    }

    /** Parses a trigger's or function's code, which starts with no delay. */
    List<Statement> body(List<Node> nodes) {
        return body(nodes, Map.of());
    }

    /** {@link #body(List)}, with what types local variables start with: a function's parameters'. */
    List<Statement> body(List<Node> nodes, Map<String, java.util.Set<String>> hints) {
        delay = DelayState.NOT_DELAYED;
        context.hints().reset(hints);
        return statements(nodes);
    }

    private List<Statement> statements(List<Node> nodes) {
        List<Statement> statements = new ArrayList<>(nodes.size());
        Statement previous = null;
        Chain chain = null;
        boolean ended = false;
        for (Node node : nodes) {
            if (ended && !suppressed.contains("unreachable")) {
                diagnostics.warning(node.line().number(), node.line().column(), node.line().text().length(),
                        "Unreachable code. The previous statement stops further execution.");
            }
            String key = node.line().key().strip().toLowerCase(Locale.ROOT);
            boolean section = node instanceof Node.Section;
            boolean continuesChain = section && (key.startsWith("else") || key.startsWith("then"));
            if (chain != null && !continuesChain) {
                delay = endChain(chain);
                chain = null;
            }
            if (section && (key.startsWith("if") || key.startsWith("parse if"))) {
                chain = new Chain(delay);
            }
            Statement statement = section && chain != null && isConditional(key)
                    ? branch((Node.Section) node, previous, chain)
                    : line(node, previous);
            statements.add(statement);
            previous = statement;
            ended = statement instanceof Statement.Effect effect && context.registry().ends(effect.effect().syntax());
            if (statement instanceof Statement.Effect effect) {
                stopsHints(effect.effect(), false);
            }
        }
        if (chain != null) {
            delay = endChain(chain);
        }
        return statements;
    }

    private Statement line(Node node, Statement previous) {
        delays.put(node.line(), delay);
        note(node.line());
        EventValues.Scope scope = context.eventScope();
        if (scope == null || !(node instanceof Node.Section)) {
            // (A line on its own tries that itself.)
            return parse(node, previous);
        }
        int mark = diagnostics.mark();
        DelayState before = delay;
        Statement statement = parse(node, previous);
        if (!(statement instanceof Statement.Invalid)) {
            return statement;
        }
        // Nothing parses without event values the code can't have: parsed with them after all, the compiler
        // says which one there's none of, as Skript does when nothing else parses.
        diagnostics.takeSince(mark);
        delay = before;
        context.eventScope(null);
        try {
            return parse(node, previous);
        } finally {
            context.eventScope(scope);
        }
    }

    private Statement parse(Node node, Statement previous) {
        return switch (node) {
            case Node.Simple simple -> simple(simple.line());
            case Node.Section section -> section(section, previous);
        };
    }

    /**
     * A section's code, parsed by {@code body}. Unless {@code header} only keeps the event values of the code
     * around it (as {@code if} does), any may be used in it, as the section may give its own.
     */
    private List<Statement> sectionCode(ParsedSyntax header, java.util.function.Supplier<List<Statement>> body) {
        EventValues values = context.registry().eventValues();
        if (context.eventScope() == null || header != null && values != null && values.keepsEventValues(header.syntax())) {
            return body.get();
        }
        EventValues.Scope scope = context.eventScope(null);
        try {
            return body.get();
        } finally {
            context.eventScope(scope);
        }
    }

    private static boolean isConditional(String key) {
        return key.startsWith("if") || key.startsWith("else") || key.startsWith("then") || key.startsWith("parse if");
    }

    private static DelayState endChain(Chain chain) {
        List<DelayState> ends = new ArrayList<>(chain.ends);
        if (!chain.hasElse) {
            ends.add(chain.before);
        }
        return chain.before.merge(ends);
    }

    /** One section of an {@code if} chain: an {@code if}, {@code else if}, {@code else} or {@code then}. */
    private Statement branch(Node.Section section, Statement previous, Chain chain) {
        String key = section.line().key().strip().toLowerCase(Locale.ROOT);
        if (!key.startsWith("then")) {
            // Every branch is parsed from the state before the chain, including its condition.
            delay = chain.before;
        }
        Statement statement = line(section, previous);
        if (key.equals("else")) {
            chain.hasElse = true;
        }
        // A multi-line condition's code is in the 'then' that follows, so its branch ends there.
        if (!(statement instanceof Statement.Conditions)) {
            chain.ends.add(delay);
        }
        return statement;
    }

    private Statement simple(Line line) {
        if (line.text().strip().equalsIgnoreCase(PARSE_SECTION + ":")) {
            error(line, "A parse section must contain code");
            return new Statement.Invalid(line);
        }
        String invalid = invalidLine(line.text());
        if (invalid != null) {
            error(line, invalid);
            return new Statement.Invalid(line);
        }
        LineParser parser = new LineParser(context, line.text());
        int end = line.text().length();
        Object parsed = simple(parser, end);
        EventValues.Scope scope = context.eventScope();
        if (scope != null && parser.refusedEventValues() && (parsed == null || unresolved(parsed) > 0)) {
            // Without the event values the code can't have, nothing parses, or only by leaning on words nothing
            // knows: with them it may, for the compiler to say which there's none of, as Skript does when nothing
            // else parses ('event-number' isn't 'event - number').
            context.eventScope(null);
            try {
                LineParser loose = new LineParser(context, line.text());
                Object other = simple(loose, end);
                if (other != null && (parsed == null || unresolved(other) < unresolved(parsed))) {
                    parser = loose;
                    parsed = other;
                }
            } finally {
                context.eventScope(scope);
            }
        }
        if (parsed instanceof Expr.FunctionCall call) {
            return new Statement.Call(line, call);
        }
        ParsedSyntax effect = parsed instanceof ParsedSyntax syntax && syntax.syntax().kind() == SyntaxKind.EFFECT
                ? syntax : null;
        if (parsed != null) {
            parser.listWarnings(parsed);
        }
        warn(line, parser);
        if (effect != null && !sectionOnly(effect).isEmpty()) {
            error(line, "This expression requires a section.");
            return new Statement.Invalid(line);
        }
        if (effect != null) {
            check(line, effect);
            if (context.registry().suppressesWarnings(effect.syntax())) {
                suppress(line, effect);
            }
            hint(effect);
            if (context.registry().delays(effect.syntax())) {
                delay = DelayState.DELAYED;
            }
            return new Statement.Effect(line, effect);
        }
        if (parsed instanceof ParsedSyntax condition) {
            check(line, condition);
            return new Statement.Condition(line, condition);
        }
        String problem = context.hints().active() ? hintProblem(line) : null;
        if (problem == null) {
            problem = parser.functionProblem();
        }
        if (problem == null) {
            problem = changerProblem(line.text());
        }
        error(line, problem != null ? problem : "Can't understand this condition/effect: " + line.text());
        return new Statement.Invalid(line);
    }

    /** A one-line conditional's header, with its condition. */
    private static final Pattern IF_HEADER = Pattern.compile("(?i)(?:else )?(?:parse )?if (.+)");

    /** Skript's change effect's shapes, with what's changed and what it's changed by. */
    private static final Pattern CHANGE = Pattern.compile(
            "(?i)(?:(?<set>set) (?<t1>.+?) to (?<d1>.+)|(?:add|give) (?<d2>.+?) to (?<t2>.+)"
                    + "|(?:remove (?:all |every )?|subtract )(?<d3>.+?) from (?<t3>.+)|(?:increase|reduce) (?<t4>.+?) by (?<d4>.+))");

    /**
     * For a change effect whose target is understood but not what it's changed by, Skript's error for that
     * value: {@code set {_y} to 1 +} gives {@code Can't understand this expression: '1 +'}.
     */
    private String changerProblem(String text) {
        Matcher matcher = CHANGE.matcher(text.strip());
        if (!matcher.matches()) {
            return null;
        }
        int offset = text.indexOf(text.strip());
        for (int i = 1; i <= 4; i++) {
            if (matcher.group("t" + i) == null) {
                continue;
            }
            LineParser parser = new LineParser(context, text);
            int targetStart = offset + matcher.start("t" + i);
            int deltaStart = offset + matcher.start("d" + i);
            String delta = matcher.group("d" + i).toLowerCase(Locale.ROOT);
            if (delta.contains(",") || delta.contains(" and ") || delta.contains(" or ")) {
                // A list isn't one value Skript can say it doesn't understand.
                return null;
            }
            if (parser.isExpression(targetStart, offset + matcher.end("t" + i))
                    && !parser.isExpression(deltaStart, offset + matcher.end("d" + i))) {
                return "Can't understand this expression: '" + matcher.group("d" + i).strip() + "'";
            }
        }
        return null;
    }

    /**
     * Skript's check of a line before it's parsed ({@code SkriptParser.validateLine}): its error if the quotes
     * don't pair up, or a text, variable or bracket isn't closed, else {@code null}.
     */
    static String invalidLine(String line) {
        int quotes = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"') {
                quotes++;
            }
        }
        if (quotes % 2 != 0) {
            return "Invalid use of quotes (\"). If you want to use quotes in \"quoted text\", double them: \"\".";
        }
        for (int i = 0; i < line.length(); i = next(line, i)) {
            if (i == -1) {
                return "Invalid amount or placement of brackets. Please make sure that each opening bracket has a"
                        + " corresponding closing bracket.";
            }
        }
        return null;
    }

    /** Skript's {@code SkriptParser.next}: the index after the text, variable or bracket at {@code start}, or -1. */
    private static int next(String line, int start) {
        if (start >= line.length()) {
            return -1;
        }
        switch (line.charAt(start)) {
            case '"' -> {
                boolean inExpression = false;
                for (int i = start + 1; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == '"' && !inExpression) {
                        if (i == line.length() - 1 || line.charAt(i + 1) != '"') {
                            return i + 1;
                        }
                        i++;
                    } else if (c == '%') {
                        inExpression = !inExpression;
                    }
                }
                return -1;
            }
            case '{' -> {
                int depth = 0;
                for (int i = start + 1; i < line.length(); i++) {
                    if (line.charAt(i) == '}') {
                        if (depth == 0) {
                            return i + 1;
                        }
                        depth--;
                    } else if (line.charAt(i) == '{') {
                        depth++;
                    }
                }
                return -1;
            }
            case '(' -> {
                for (int i = start + 1; i >= 0 && i < line.length(); i = next(line, i)) {
                    if (line.charAt(i) == ')') {
                        return i + 1;
                    }
                }
                return -1;
            }
            default -> {
                return start + 1;
            }
        }
    }

    /** A line as a function call, else an effect, else a condition, or {@code null}. */
    private static Object simple(LineParser parser, int end) {
        Expr.FunctionCall call = parser.call(0, end);
        if (call != null) {
            return call;
        }
        ParsedSyntax effect = parser.effect(0, end);
        return effect != null ? effect : parser.condition(0, end);
    }

    /** How much of a parsed line is left to words nothing parses. */
    private static int unresolved(Object parsed) {
        return parsed instanceof ParsedSyntax syntax ? LineParser.countUnresolved(syntax)
                : LineParser.countUnresolved((Expr) parsed);
    }

    private Statement section(Node.Section section, Statement previous) {
        Line line = section.line();
        String key = line.key();
        String invalid = invalidLine(key);
        if (invalid != null) {
            error(line, invalid);
            return new Statement.Invalid(line);
        }
        if (key.equalsIgnoreCase(PARSE_SECTION) && section.children().isEmpty()) {
            error(line, "A parse section must contain code");
            return new Statement.Invalid(line);
        }
        if (key.equalsIgnoreCase(PARSE_SECTION)) {
            // Skript's parse section: its code counts towards the trigger (including any delay), but
            // its problems are captured for the test.
            int mark = diagnostics.mark();
            List<Statement> body = statements(section.children());
            List<Diagnostic> captured = diagnostics.takeSince(mark);
            return new Statement.Parse(line, body, captured);
        }
        LineParser parser = new LineParser(context, key);
        ParsedSyntax header = parser.section(0, key.length());
        if (header == null) {
            // An effect whose expression takes a section: 'set {_bar} to a new boss bar:'.
            ParsedSyntax effect = parser.effect(0, key.length());
            List<Expr.Syntax> claimants = sectionOnly(effect);
            if (claimants.size() > 1) {
                error(line, claimError(key, claimants.get(1), "'" + text(key, claimants.getFirst()) + "'"));
                return new Statement.Invalid(line);
            }
            if (effect != null && hasSectionExpression(effect)) {
                parser.listWarnings(effect);
                warn(line, parser);
                return new Statement.Section(line, effect, sectionCode(null, () -> repeatable(section.children())));
            }
            Expr.FunctionCall call = parser.call(0, key.length());
            if (call != null && call.arguments().stream().anyMatch(a -> a != null && hasSectionExpression(a))) {
                return new Statement.Section(line, null, sectionCode(null, () -> repeatable(section.children())), call);
            }
            if (call != null && effect == null) {
                error(line, "The line '" + key + "' is a valid function call but cannot function as a section (:) because"
                        + " there is no parameter to manage it.");
                return new Statement.Invalid(line);
            }
            if (effect != null || call != null || parser.condition(0, key.length()) != null) {
                error(line, "The line '" + key + "' is a valid statement but cannot function as a section (:) because"
                        + " there is no syntax in the line to manage it.");
                return new Statement.Invalid(line);
            }
            Matcher conditional = IF_HEADER.matcher(key);
            if (conditional.matches()) {
                error(line, "Can't understand this condition: '" + conditional.group(1) + "'");
            } else if (key.regionMatches(true, 0, "loop ", 0, 5)) {
                error(line, "Can't understand this loop: '" + key.substring(5) + "'");
            } else {
                error(line, "Can't understand this section: " + key);
            }
            return new Statement.Invalid(line);
        }
        // What takes the section may refuse it, as Skript's section expressions, then its effect sections, do.
        String refused = argumentText(key, header, sectionRefusal(header));
        if (refused != null) {
            error(line, refused.isEmpty() ? "The line '" + key + "' is a valid statement but cannot function as a section (:)"
                    + " because there is no syntax in the line to manage it." : refused);
            return new Statement.Invalid(line);
        }
        List<Expr.Syntax> claimants = sectionOnly(header);
        if (!claimants.isEmpty()) {
            error(line, claimError(key, claimants.getFirst(), "'" + key + "'"));
            return new Statement.Invalid(line);
        }
        check(line, header);
        parser.listWarnings(header);
        warn(line, parser);
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("else") && !continuesConditional(previous)) {
            error(line, Statement.orphanElse(lower));
            return new Statement.Invalid(line);
        }
        if (lower.startsWith("then") && !(previous instanceof Statement.Conditions)) {
            // Skript's wording, typo included.
            error(line, "'then' has to placed just after a multiline 'if' or 'else if' section");
            return new Statement.Invalid(line);
        }
        if (MULTILINE_CONDITIONAL.matcher(key).matches() && !header.syntax().patterns().get(header.pattern())
                .source().endsWith("<.+>")) {
            List<Line> lines = new ArrayList<>();
            List<ParsedSyntax> conditions = conditions(section.children(), lines);
            if (conditions.size() < 2 && lines.size() == section.children().size()) {
                error(line, (any(lower) ? "'if any'" : "'if all'") + " sections must contain at least two conditions");
                return new Statement.Invalid(line);
            }
            return new Statement.Conditions(line, header, conditions, lines);
        }
        if (PARSE_IF.matcher(key).matches()) {
            // 'parse if' only parses its code when the condition is true while loading, which can't be
            // known here: parse it, but keep its problems out of the report.
            int mark = diagnostics.mark();
            List<Statement> body = sectionBody(section.children());
            diagnostics.takeSince(mark);
            return new Statement.Section(line, header, body);
        }
        if (isConditional(lower)) {
            return new Statement.Section(line, header, sectionBody(section.children()));
        }
        if (header.syntax().id().equals(SUPPRESS_HINTS)) {
            boolean before = context.hints().suppressed();
            context.hints().suppress(!header.match().tags().contains("stop"));
            List<Statement> body = repeatable(section.children());
            context.hints().suppress(before);
            return new Statement.Section(line, header, body);
        }
        if (context.registry().suppressesWarnings(header.syntax())) {
            // Only for the section's code.
            java.util.Set<String> before = java.util.Set.copyOf(suppressed);
            suppress(line, header);
            List<Statement> body = repeatable(section.children());
            suppressed.clear();
            suppressed.addAll(before);
            return new Statement.Section(line, header, body);
        }
        return new Statement.Section(line, header, sectionCode(header, () -> repeatable(section.children(),
                header.syntax().id().equals("SecFor") ? header : null)));
    }

    /** Whether a multiline conditional is an {@code if any} one. */
    private static boolean any(String lower) {
        return lower.contains(" any") || lower.contains("at least one");
    }

    /** A {@code for} loop's code: its value variable may also be any of what the list holds. */
    private List<Statement> loopBody(List<Node> nodes, ParsedSyntax loop) {
        DelayState before = delay;
        context.hints().enter();
        List<Statement> body;
        try {
            List<Object> values = loop.match().values();
            Object looped = values.getLast();
            Object value = values.get(loop.pattern() == 0 ? 0 : 1);
            if (value instanceof Expr.Variable variable && simple(variable) && looped instanceof Expr list) {
                java.util.Set<String> types = new java.util.LinkedHashSet<>(types(list));
                if (!types.isEmpty()) {
                    types.addAll(context.hints().get(variable.name()));
                    context.hints().set(variable.name(), types);
                }
            }
            body = statements(nodes);
        } finally {
            context.hints().exit();
        }
        if (stops(body)) {
            delay = before;
        }
        return body;
    }

    /**
     * A section's code. If it always ends the trigger ({@code stop}, {@code return}), a delay in it doesn't
     * carry past the section, as Skript's execution intents have it.
     */
    private List<Statement> sectionBody(List<Node> nodes) {
        DelayState before = delay;
        context.hints().enter();
        List<Statement> body;
        try {
            body = statements(nodes);
        } finally {
            context.hints().exit();
        }
        if (stops(body)) {
            delay = before;
        }
        return body;
    }

    /**
     * Why a line that didn't parse fails by its type hints, as Skript says: the line is parsed again without
     * them, and the first variable whose hints don't fit where it was put is the reason. {@code null} if
     * the line doesn't parse that way either, or every variable fits.
     */
    private String hintProblem(Line line) {
        LineParser loose = new LineParser(context, line.text(), false);
        int end = line.text().length();
        Object parsed = loose.call(0, end);
        if (parsed == null) {
            parsed = loose.effect(0, end);
        }
        if (parsed == null) {
            parsed = loose.condition(0, end);
        }
        if (parsed == null) {
            return null;
        }
        LineParser strict = new LineParser(context, line.text());
        return hintProblem(parsed, strict);
    }

    private String hintProblem(Object node, LineParser strict) {
        switch (node) {
            case ParsedSyntax syntax -> {
                co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec[] specs = specs(syntax);
                List<Object> values = syntax.match().values();
                for (int i = 0; i < values.size(); i++) {
                    String problem = i < specs.length && specs[i] != null
                            ? hintProblem(values.get(i), context.expected(specs[i]), strict) : null;
                    if (problem != null) {
                        return problem;
                    }
                }
                for (Object value : syntax.match().regexValues()) {
                    String problem = hintProblem(value, strict);
                    if (problem != null) {
                        return problem;
                    }
                }
            }
            case Expr.FunctionCall call -> {
                for (int i = 0; i < call.arguments().size() && i < call.function().parameters().size(); i++) {
                    FunctionInfo.Parameter parameter = call.function().parameters().get(i);
                    TypeRef ref = context.registry().resolveType(parameter.type());
                    if (ref != null && call.arguments().get(i) != null) {
                        String problem = hintProblem(call.arguments().get(i),
                                context.expected(new TypeRef(ref.type(), parameter.plural())), strict);
                        if (problem != null) {
                            return problem;
                        }
                    }
                }
            }
            case Expr.Syntax syntax -> {
                return hintProblem(syntax.syntax(), strict);
            }
            default -> {}
        }
        return null;
    }

    private String hintProblem(Object value, Expected expected, LineParser strict) {
        if (value instanceof Expr.Variable variable) {
            return strict.hintFits(variable, expected) ? null : strict.hintProblem();
        }
        if (value instanceof Expr.ListOf list) {
            for (Expr item : list.items()) {
                String problem = hintProblem(item, expected, strict);
                if (problem != null) {
                    return problem;
                }
            }
            return null;
        }
        return hintProblem(value, strict);
    }

    private static co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec[] specs(ParsedSyntax syntax) {
        co.xenastudios.neoskript.frontend.pattern.CompiledPattern pattern = syntax.syntax().patterns().get(syntax.pattern());
        co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec[] specs =
                new co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec[pattern.placeholderCount()];
        collect(pattern.root(), specs);
        return specs;
    }

    private static void collect(co.xenastudios.neoskript.frontend.pattern.PatternElement element,
            co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec[] specs) {
        switch (element) {
            case co.xenastudios.neoskript.frontend.pattern.PatternElement.Placeholder placeholder ->
                    specs[placeholder.index()] = placeholder.spec();
            case co.xenastudios.neoskript.frontend.pattern.PatternElement.Sequence sequence ->
                    sequence.elements().forEach(e -> collect(e, specs));
            case co.xenastudios.neoskript.frontend.pattern.PatternElement.Choice choice ->
                    choice.alternatives().forEach(a -> collect(a.body(), specs));
            case co.xenastudios.neoskript.frontend.pattern.PatternElement.Optional optional ->
                    collect(optional.choice(), specs);
            default -> {}
        }
    }

    /** Skript's {@code suppress local variable type hints}, alone or as a section. */
    private static final String SUPPRESS_HINTS = "EffSecSuppressTypeHints";

    /** What an effect tells the type hints: {@code set {_x} to 10}, {@code delete {_x}}, {@code copy}... */
    private void hint(ParsedSyntax effect) {
        TypeHints hints = context.hints();
        List<Object> values = effect.match().values();
        switch (effect.syntax().id()) {
            case SUPPRESS_HINTS -> hints.suppress(!effect.match().tags().contains("stop"));
            case "EffChange" -> {
                switch (effect.pattern()) {
                    case 0 -> forEachVariable(values.get(0), v -> hints.set(v.name(), types((Expr) values.get(1))));
                    case 1 -> forEachVariable(values.get(1), v -> hints.add(v.name(), types((Expr) values.get(0))));
                    case 6 -> forEachVariable(values.get(0), v -> hints.delete(v.name()));
                    default -> {}
                }
            }
            case "EffCopy" -> forEachVariable(values.get(1), v -> hints.set(v.name(),
                    values.get(0) instanceof Expr source ? types(source) : java.util.Set.of()));
            case "EffTransform" -> {
                if (!effect.match().regexValues().isEmpty() && effect.match().regexValues().getFirst() instanceof Expr mapping) {
                    forEachVariable(values.get(0), v -> hints.set(v.name(), types(mapping)));
                }
            }
            default -> {}
        }
    }

    /** How far an effect stops execution, for the type hints: {@code stop}, {@code exit 2 sections}. */
    private void stopsHints(ParsedSyntax effect, boolean conditional) {
        switch (effect.syntax().id()) {
            case "EffExit" -> {
                int levels = -1;
                if (effect.pattern() == 1) {
                    levels = 1;
                } else if (effect.pattern() == 2 && effect.match().regexValues().getFirst() instanceof java.util.regex.MatchResult n) {
                    levels = Integer.parseInt(n.group());
                }
                context.hints().stop(levels, conditional);
            }
            case "EffReturn" -> context.hints().stop(-1, conditional);
            case "EffContinue" -> context.hints().stop(1, conditional);
            case "EffDoIf" -> {
                if (effect.match().regexValues().getFirst() instanceof ParsedSyntax inner) {
                    stopsHints(inner, true);
                }
            }
            default -> {}
        }
    }

    private static void forEachVariable(Object target, java.util.function.Consumer<Expr.Variable> action) {
        if (target instanceof Expr.Variable variable && simple(variable)) {
            action.accept(variable);
        } else if (target instanceof Expr.ListOf list) {
            list.items().forEach(item -> forEachVariable(item, action));
        }
    }

    /** A local variable whose name is all written out: only those have hints, as in Skript. */
    static boolean simple(Expr.Variable variable) {
        return variable.local() && variable.embedded().isEmpty();
    }

    /** The types {@code expr}'s values may be, as far as is known while parsing; none if unknown. */
    private java.util.Set<String> types(Expr expr) {
        return switch (expr) {
            case Expr.Variable variable when simple(variable) -> context.hints().get(variable.name());
            case Expr.ListOf list -> {
                java.util.Set<String> all = new java.util.LinkedHashSet<>();
                for (Expr item : list.items()) {
                    java.util.Set<String> types = types(item);
                    if (types.isEmpty()) {
                        yield java.util.Set.of();
                    }
                    all.addAll(types);
                }
                yield all;
            }
            case Expr.Variable variable -> java.util.Set.of();
            case Expr.Unresolved unresolved -> java.util.Set.of();
            default -> expr.type() == null || expr.type().equals(SyntaxRegistry.OBJECT) ? java.util.Set.of()
                    : java.util.Set.of(expr.type());
        };
    }

    /** {@code suppress unreachable code warnings}, or {@code unsuppress}: changes what's suppressed from here on. */
    private void suppress(Line line, ParsedSyntax syntax) {
        if (syntax.match().tags().contains("conflict")) {
            diagnostics.warning(line.number(), line.column(), line.text().length(),
                    "Variable conflict warnings no longer need suppression, as they have been removed altogether");
        }
        boolean allow = syntax.match().tags().contains("stop");
        for (String tag : syntax.match().tags()) {
            if (!tag.equals("stop")) {
                if (allow) {
                    suppressed.remove(tag);
                } else {
                    suppressed.add(tag);
                }
            }
        }
    }

    /** Reports the warnings the line's parser found ({@code tag:message}), unless they're suppressed. */
    private void warn(Line line, LineParser parser) {
        warn(line, parser.warnings());
    }

    /** Reports {@code warnings} ({@code tag:message}) at {@code line}, unless they're suppressed, and clears them. */
    void warn(Line line, java.util.Set<String> warnings) {
        for (String warning : warnings) {
            int colon = warning.indexOf(':');
            if (!suppressed.contains(warning.substring(0, colon))) {
                diagnostics.warning(line.number(), line.column(), line.text().length(), warning.substring(colon + 1));
            }
        }
        warnings.clear();
    }

    private boolean stops(List<Statement> body) {
        for (Statement statement : body) {
            if (statement instanceof Statement.Effect effect
                    && context.registry().stops(effect.effect().syntax(), effect.effect().pattern())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Code in a section that may run any number of times, like a loop. Afterwards the trigger is delayed
     * only if it already was; if the code may wait, it's maybe delayed.
     */
    private List<Statement> repeatable(List<Node> nodes) {
        return repeatable(nodes, null);
    }

    /** {@link #repeatable(List)}, with a loop's variables hinted first ({@code for {_value} in X}). */
    private List<Statement> repeatable(List<Node> nodes, ParsedSyntax loop) {
        DelayState before = delay;
        List<Statement> body = loop == null ? sectionBody(nodes) : loopBody(nodes, loop);
        delay = before.merge(List.of(before, delay));
        return body;
    }

    private List<ParsedSyntax> conditions(List<Node> nodes, List<Line> lines) {
        List<ParsedSyntax> conditions = new ArrayList<>();
        for (Node node : nodes) {
            Line line = node.line();
            delays.put(line, delay);
            note(line);
            LineParser parser = new LineParser(context, line.text());
            ParsedSyntax condition = node instanceof Node.Simple ? parser.condition(0, line.text().length()) : null;
            if (node instanceof Node.Simple && context.eventScope() != null && parser.refusedEventValues()
                    && (condition == null || unresolved(condition) > 0)) {
                // With event values the code can't have, for the compiler to say which, as for other lines.
                EventValues.Scope scope = context.eventScope(null);
                try {
                    ParsedSyntax other = new LineParser(context, line.text()).condition(0, line.text().length());
                    if (other != null && (condition == null || unresolved(other) < unresolved(condition))) {
                        condition = other;
                    }
                } finally {
                    context.eventScope(scope);
                }
            }
            if (condition == null) {
                error(line, "Can't understand this condition: " + line.text());
            } else {
                check(line, condition);
                parser.listWarnings(condition);
                warn(line, parser);
                conditions.add(condition);
                lines.add(line);
            }
        }
        return conditions;
    }

    /** Runs the parse-time checks of every element in a parsed line. */
    private void check(Line line, ParsedSyntax syntax) {
        ParseCheck check = context.registry().check(syntax.syntax());
        if (check != null) {
            String problem = check.check(syntax.match(), delay);
            if (problem != null) {
                error(line, problem);
            }
        }
        for (Object value : syntax.match().regexValues()) {
            if (value instanceof ParsedSyntax inner) {
                check(line, inner);
            }
        }
        for (Object value : syntax.match().values()) {
            if (value instanceof Expr.Syntax inner) {
                check(line, inner.syntax());
            }
        }
    }

    /** {@code problem}, or for {@link ParseCheck#ARGUMENT_TEXT} and an index, the text written for that argument. */
    private static String argumentText(String key, ParsedSyntax syntax, String problem) {
        if (problem == null || !problem.startsWith(ParseCheck.ARGUMENT_TEXT)) {
            return problem;
        }
        int index = Integer.parseInt(problem.substring(ParseCheck.ARGUMENT_TEXT.length()));
        Object value = index < syntax.match().values().size() ? syntax.match().values().get(index) : null;
        if (!(value instanceof Expr.Text text)) {
            return "";
        }
        return key.substring(text.start() + 1, text.end() - 1).replace("\"\"", "\"");
    }

    /** Why the line's syntax won't take the section under it, or {@code null} if it does. */
    private String sectionRefusal(ParsedSyntax header) {
        for (Object value : header.match().values()) {
            if (value instanceof Expr.Syntax inner) {
                ParseCheck check = context.registry().sectionCheck(inner.syntax().syntax());
                String problem = check == null ? null : check.check(inner.syntax().match(), delay);
                if (problem != null) {
                    return problem;
                }
            }
        }
        ParseCheck check = context.registry().sectionCheck(header.syntax());
        return check == null ? null : check.check(header.match(), delay);
    }

    /** The expressions in a parsed line that can only be used with a section, in order. */
    private List<Expr.Syntax> sectionOnly(ParsedSyntax syntax) {
        List<Expr.Syntax> found = new ArrayList<>();
        if (syntax != null) {
            sectionOnly(syntax, found);
        }
        return found;
    }

    private void sectionOnly(ParsedSyntax syntax, List<Expr.Syntax> found) {
        for (Object value : syntax.match().values()) {
            if (value instanceof Expr expr) {
                sectionOnly(expr, found);
            }
        }
        // The condition of 'if X:' and the like.
        for (Object value : syntax.match().regexValues()) {
            if (value instanceof ParsedSyntax inner) {
                sectionOnly(inner, found);
            }
        }
    }

    private void sectionOnly(Expr expr, List<Expr.Syntax> found) {
        switch (expr) {
            case Expr.Syntax syntax -> {
                if (context.registry().sectionOnly(syntax.syntax().syntax())) {
                    found.add(syntax);
                }
                sectionOnly(syntax.syntax(), found);
            }
            case Expr.ListOf list -> list.items().forEach(item -> sectionOnly(item, found));
            default -> {}
        }
    }

    /** Skript's error for a second syntax claiming a line's section. */
    private static String claimError(String key, Expr.Syntax claimant, String claimedBy) {
        return "The syntax '" + text(key, claimant) + "' tried to claim the current section, but it was already claimed by "
                + claimedBy + ". You cannot have two section-starters in the same line.";
    }

    private static String text(String key, Expr.Syntax syntax) {
        return key.substring(syntax.start(), syntax.end()).strip();
    }

    /** Whether a parsed effect contains an expression that takes a section (an {@code ExprSec} element). */
    private static boolean hasSectionExpression(ParsedSyntax syntax) {
        if (syntax.syntax().id().startsWith("ExprSec")) {
            return true;
        }
        for (Object value : syntax.match().values()) {
            if (value instanceof Expr expr && hasSectionExpression(expr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSectionExpression(Expr expr) {
        return switch (expr) {
            case Expr.Syntax syntax -> hasSectionExpression(syntax.syntax());
            case Expr.ListOf list -> list.items().stream().anyMatch(StatementParser::hasSectionExpression);
            default -> false;
        };
    }

    private static boolean continuesConditional(Statement previous) {
        if (previous instanceof Statement.Conditions) {
            return true;
        }
        if (previous instanceof Statement.Section section) {
            String key = section.line().key().toLowerCase(Locale.ROOT);
            if (key.startsWith("then")) {
                return true;
            }
            return !key.equals("else") && (key.contains("if ") || key.startsWith("if") || isImplicitCondition(section));
        }
        return false;
    }

    private static boolean isImplicitCondition(Statement.Section section) {
        return section.header() != null
                && section.header().syntax().patterns().get(section.header().pattern()).source().startsWith("implicit:");
    }

    /** Parses {@code text} on its own as a value of {@code type}, reporting it at {@code line} if it doesn't. */
    Expr expression(Line line, String text, String type, boolean plural) {
        TypeInfo info = context.registry().type(type);
        Expected expected = info == null ? Expected.OBJECTS : context.expected(new TypeRef(info, plural));
        // Values given on their own, like defaults, may be any event value: the compiler says if there's none.
        EventValues.Scope scope = context.eventScope(null);
        Expr expr;
        try {
            expr = new LineParser(context, text).expression(0, text.length(), expected);
        } finally {
            context.eventScope(scope);
        }
        if (expr == null) {
            error(line, "Can't understand this expression: '" + text + "'");
        }
        return expr;
    }

    private void error(Line line, String message) {
        diagnostics.error(line.number(), line.column(), line.text().length(), message);
    }
}
