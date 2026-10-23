/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.pattern.CompiledPattern;
import co.xenastudios.neoskript.frontend.pattern.MatchResult;
import co.xenastudios.neoskript.frontend.pattern.PatternIndex;
import co.xenastudios.neoskript.frontend.pattern.PlaceholderResolver;
import co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.registry.RegexRole;
import co.xenastudios.neoskript.frontend.registry.SyntaxInfo;
import co.xenastudios.neoskript.frontend.registry.SyntaxKind;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.TypeInfo;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses one line: its statement, and every expression in it. Results are memoized by span and what's
 * expected there, so trying many patterns never parses the same sub-expression twice.
 *
 * <p>An expression is tried as, in order: a parenthesized expression, a literal (string, number, boolean,
 * timespan, time, or a closed-set literal of an expected type), a variable, a function call, a registered
 * expression, a list, and finally an unresolved word literal for types the frontend can't list.
 */
final class LineParser {

    private static final Object IN_PROGRESS = new Object();
    private static final Object FAILED = new Object();
    private static final Pattern FUNCTION_NAME = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*");

    private final ParserContext context;
    private final SyntaxRegistry registry;
    private final String input;
    private final String lowerInput;
    private final Map<Key, Object> memo = new HashMap<>();
    /** How many times a placeholder was refused for starting inside a token like {@code arg-1}. */
    private int tokenSplits;
    private String functionProblem;
    /** Per kind, the unindexed patterns whose required literals all occur somewhere in the line. */
    private final Map<SyntaxKind, List<PatternIndex.Entry<SyntaxRegistry.Candidate>>> viableUnindexed =
            new EnumMap<>(SyntaxKind.class);
    /** Per kind, whether each pattern (by index order) could match somewhere in the line; null = unknown. */
    private final Map<SyntaxKind, Boolean[]> viable = new EnumMap<>(SyntaxKind.class);

    /** A memo key. {@code what} is a kind or an interned {@link Expected}, so it's compared by identity. */
    private record Key(int start, int end, Object what) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && key.start == start && key.end == end && key.what == what;
        }

        @Override
        public int hashCode() {
            return (start * 31 + end) * 31 + System.identityHashCode(what);
        }
    }

    LineParser(ParserContext context, String input) {
        this.context = context;
        this.registry = context.registry();
        this.input = input;
        this.lowerInput = input.toLowerCase(Locale.ROOT);
    }

    String input() {
        return input;
    }

    // ---------------------------------------------------------------- statements and other syntax

    ParsedSyntax effect(int start, int end) {
        return syntax(SyntaxKind.EFFECT, start, end, false);
    }

    ParsedSyntax condition(int start, int end) {
        return syntax(SyntaxKind.CONDITION, start, end, false);
    }

    ParsedSyntax section(int start, int end) {
        return syntax(SyntaxKind.SECTION, start, end, false);
    }

    /** Matches an event header. Everything in it has to be a literal, as in Skript. */
    ParsedSyntax event(int start, int end) {
        return syntax(SyntaxKind.EVENT, start, end, true);
    }

    ParsedSyntax structure(int start, int end) {
        return syntax(SyntaxKind.STRUCTURE, start, end, false);
    }

    /** The whole of {@code [start, end)} as a call to a known function, or {@code null}. */
    Expr.FunctionCall call(int start, int end) {
        int[] span = trim(start, end);
        Object result = memoized(span[0], span[1], "call", () -> functionCall(span[0], span[1], false));
        return result instanceof Expr.FunctionCall call ? call : null;
    }

    private ParsedSyntax syntax(SyntaxKind kind, int start, int end, boolean literalOnly) {
        int[] span = trim(start, end);
        Object result = memoized(span[0], span[1], kind, () -> matchSyntax(kind, span[0], span[1], literalOnly, null));
        return result instanceof ParsedSyntax syntax ? syntax : null;
    }

    /**
     * Matches the text as one of {@code kind}. Placeholders can't start inside a token like {@code arg-1}
     * ({@code arg-1 * arg-2} isn't {@code arg - (1 * arg-2)}), unless nothing parses otherwise.
     */
    private Object matchSyntax(SyntaxKind kind, int start, int end, boolean literalOnly, Expected expected) {
        if (start >= end) {
            return null;
        }
        int rejected = tokenSplits;
        Object result = matchSyntax(kind, start, end, literalOnly, expected, false);
        return result != null || tokenSplits == rejected ? result
                : matchSyntax(kind, start, end, literalOnly, expected, true);
    }

    private Object matchSyntax(SyntaxKind kind, int start, int end, boolean literalOnly, Expected expected,
            boolean splitTokens) {
        String text = input.substring(start, end);
        String lower = lowerInput.substring(start, end);
        PatternIndex<SyntaxRegistry.Candidate> index = registry.index(kind);
        Object fallback = null;
        List<PatternIndex.Entry<SyntaxRegistry.Candidate>> indexed = new ArrayList<>();
        for (PatternIndex.Entry<SyntaxRegistry.Candidate> entry : index.indexed(text)) {
            if (isViable(kind, entry)) {
                indexed.add(entry);
            }
        }
        for (PatternIndex.Entry<SyntaxRegistry.Candidate> entry : PatternIndex.merge(indexed, viableUnindexed(kind))) {
            SyntaxRegistry.Candidate candidate = entry.owner();
            SyntaxInfo syntax = candidate.syntax();
            if (expected != null && !returnTypeFits(syntax, expected)) {
                continue;
            }
            CompiledPattern pattern = syntax.patterns().get(candidate.pattern());
            if (!pattern.couldMatch(lower)) {
                continue;
            }
            MatchResult match = pattern.match(input, start, end, resolver(syntax, literalOnly, splitTokens));
            if (match == null) {
                continue;
            }
            ParsedSyntax parsed = new ParsedSyntax(syntax, candidate.pattern(), match);
            if (syntax.id().equals("ExprArithmetic") && !arithmeticOperands(parsed)) {
                continue;
            }
            Object result = parsed;
            if (expected != null) {
                String type = returnType(parsed);
                if (!expected.accepts(type, registry)) {
                    continue;
                }
                result = new Expr.Syntax(start, end, parsed, type);
            }
            // A parse that leans on unresolved words only wins if nothing parses without them, as Skript
            // only accepts real literals: 'loot context at test-location' isn't '(...test) - location'.
            if (!hasUnresolved(parsed)) {
                return result;
            }
            if (fallback == null) {
                fallback = result;
            }
        }
        return fallback;
    }

    /**
     * Whether {@code position} is just after a letter and a hyphen, as the {@code 1} of {@code arg-1}: there
     * the hyphen is part of a word, not a minus sign.
     */
    private boolean splitsToken(int position) {
        return position >= 2 && position < input.length() && Character.isDigit(input.charAt(position))
                && input.charAt(position - 1) == '-' && Character.isLetter(input.charAt(position - 2));
    }

    /** Types arithmetic works on; an operand of another known type rules arithmetic out, as in Skript. */
    private static final java.util.Set<String> ARITHMETIC_TYPES = java.util.Set.of(SyntaxRegistry.OBJECT, "number",
            "integer", "long", "timespan", "date", "time", "timeperiod", "string", "vector", "location", "experience");

    /** Whether arithmetic's operands could be added: {@code the script named X + "y"} isn't arithmetic. */
    private boolean arithmeticOperands(ParsedSyntax parsed) {
        for (Object value : parsed.match().values()) {
            if (value instanceof Expr expr && expr.type() != null && !ARITHMETIC_TYPES.contains(expr.type())
                    && registry.type(expr.type()) != null) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasUnresolved(ParsedSyntax parsed) {
        for (Object value : parsed.match().values()) {
            if (value instanceof Expr expr && hasUnresolved(expr)) {
                return true;
            }
        }
        for (Object value : parsed.match().regexValues()) {
            if ((value instanceof Expr expr && hasUnresolved(expr))
                    || (value instanceof ParsedSyntax inner && hasUnresolved(inner))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnresolved(Expr expr) {
        return switch (expr) {
            case Expr.Unresolved unresolved -> true;
            case Expr.Syntax syntax -> hasUnresolved(syntax.syntax());
            case Expr.ListOf list -> list.items().stream().anyMatch(LineParser::hasUnresolved);
            case Expr.FunctionCall call ->
                    call.arguments().stream().anyMatch(a -> a != null && hasUnresolved(a));
            case Expr.Text text -> text.embedded().stream().anyMatch(LineParser::hasUnresolved);
            case Expr.Variable variable -> variable.embedded().stream().anyMatch(LineParser::hasUnresolved);
            case Expr.Literal literal -> false;
        };
    }

    private boolean isViable(SyntaxKind kind, PatternIndex.Entry<SyntaxRegistry.Candidate> entry) {
        Boolean[] known = viable.computeIfAbsent(kind, k -> new Boolean[registry.index(k).size()]);
        Boolean result = known[entry.order()];
        if (result == null) {
            result = entry.pattern().couldMatchWithin(lowerInput);
            known[entry.order()] = result;
        }
        return result;
    }

    private List<PatternIndex.Entry<SyntaxRegistry.Candidate>> viableUnindexed(SyntaxKind kind) {
        return viableUnindexed.computeIfAbsent(kind, k -> registry.index(k).unindexed().stream()
                .filter(e -> isViable(k, e))
                .toList());
    }

    private static final java.util.regex.Pattern LOOP_VALUE_NAME =
            java.util.regex.Pattern.compile("(.+?)(?:-\\d+)?", java.util.regex.Pattern.CASE_INSENSITIVE);

    private boolean isLoopValueName(String text) {
        java.util.regex.Matcher name = LOOP_VALUE_NAME.matcher(text.strip());
        if (!name.matches()) {
            return false;
        }
        String word = name.group(1).toLowerCase(java.util.Locale.ROOT);
        return word.equals("value") || word.equals("index") || word.equals("counter") || word.equals("iteration")
                || registry.resolveType(word) != null;
    }

    private boolean returnTypeFits(SyntaxInfo syntax, Expected expected) {
        // Expressions whose type comes from a regex (event-<type>) are checked after matching.
        return syntax.returnType() == null || hasTypeRole(syntax) || expected.accepts(syntax.returnType(), registry);
    }

    private static boolean hasTypeRole(SyntaxInfo syntax) {
        return syntax.regexRoles().contains(RegexRole.TYPE) || syntax.regexRoles().contains(RegexRole.TYPE_OR_ENTITY);
    }

    private static String returnType(ParsedSyntax parsed) {
        for (Object value : parsed.match().regexValues()) {
            if (value instanceof TypeRef ref) {
                return ref.type().id();
            }
        }
        String type = parsed.syntax().returnType();
        return type == null ? SyntaxRegistry.OBJECT : type;
    }

    private PlaceholderResolver resolver(SyntaxInfo syntax, boolean literalOnly, boolean splitTokens) {
        return new PlaceholderResolver() {
            @Override
            public Object resolve(String text, int start, int end, PlaceholderSpec spec) {
                if (!splitTokens && splitsToken(start)) {
                    tokenSplits++;
                    return null;
                }
                Expected expected = context.expected(spec);
                return expression(start, end, literalOnly ? context.intern(expected.asLiteralOnly()) : expected);
            }

            @Override
            public Object resolveRegex(String text, int start, int end, int index, java.util.regex.MatchResult match) {
                return switch (syntax.regexRole(index)) {
                    case ANY -> match;
                    case CONDITION -> condition(start, end);
                    case EFFECT -> {
                        ParsedSyntax effect = effect(start, end);
                        yield effect != null ? effect : call(start, end);
                    }
                    case EXPRESSION -> expression(start, end, Expected.OBJECTS);
                    case TYPE -> typeName(start, end);
                    case TYPE_OR_ENTITY -> {
                        TypeRef ref = typeName(start, end);
                        TypeInfo entity = registry.type("entity");
                        yield ref != null || entity == null || !registry.isEntityTypeLiteral(input.substring(start, end))
                                ? ref : new TypeRef(entity, false);
                    }
                    case LOOP_VALUE -> isLoopValueName(text.substring(start, end)) ? match : null;
                    case CODE -> {
                        Object code = condition(start, end);
                        if (code == null) {
                            code = effect(start, end);
                        }
                        yield code != null ? code : expression(start, end, Expected.OBJECTS);
                    }
                };
            }
        };
    }

    /** A type name or entity name, as in {@code event-player} or {@code the zombie}. */
    private TypeRef typeName(int start, int end) {
        String name = Literals.normalize(input.substring(start, end));
        TypeRef ref = registry.resolveType(name);
        if (ref == null && registry.isEntityName(name)) {
            TypeInfo entity = registry.type("entity");
            return entity == null ? null : new TypeRef(entity, false);
        }
        return ref;
    }

    // ---------------------------------------------------------------- expressions

    /** Parses {@code [start, end)} as what's expected there, or returns {@code null}. */
    Expr expression(int start, int end, Expected expected) {
        int[] span = trim(start, end);
        Object result = memoized(span[0], span[1], expected, () -> parseExpression(span[0], span[1], expected));
        return result instanceof Expr expr ? expr : null;
    }

    private Expr parseExpression(int start, int end, Expected expected) {
        if (start >= end) {
            return null;
        }
        Expr expr = single(start, end, expected);
        if (expr == null && expected.plural()) {
            expr = list(start, end, expected);
        }
        if (expr == null) {
            expr = unresolved(start, end, expected);
        }
        if (expr != null && expected.changeable() && !isChangeable(expr)) {
            return null;
        }
        return expr;
    }

    private Expr single(int start, int end, Expected expected) {
        char first = input.charAt(start);
        String text = input.substring(start, end);

        if (first == '(' && Scanner.parenEnd(input, start, end) == end) {
            Expr inner = expression(start + 1, end - 1, expected);
            if (inner != null) {
                return inner;
            }
        }
        if (first == '"') {
            Expr string = text(start, end, expected);
            if (string != null) {
                return string;
            }
        }
        Expr literal = literal(start, end, text, expected);
        if (literal != null) {
            return literal;
        }
        if (expected.literalOnly()) {
            return null;
        }
        if (first == '{' && Scanner.braceEnd(input, start, end, null) == end) {
            return variable(start, end);
        }
        Expr call = functionCall(start, end, true);
        if (call != null) {
            return expected.accepts(call.type(), registry) ? call : null;
        }
        Object syntax = matchSyntax(SyntaxKind.EXPRESSION, start, end, false, expected);
        return syntax instanceof Expr expr ? expr : null;
    }

    private Expr text(int start, int end, Expected expected) {
        List<int[]> embedded = new ArrayList<>();
        if (Scanner.stringEnd(input, start, end, embedded) != end) {
            return null;
        }
        // Skript also reads world names from strings: world "world".
        if (!expected.accepts("string", registry) && !expected.hasType("world")) {
            return null;
        }
        if (expected.literalOnly() && !embedded.isEmpty()) {
            return null;
        }
        List<Expr> parts = new ArrayList<>();
        for (int[] part : embedded) {
            Expr expr = expression(part[0], part[1], Expected.OBJECTS);
            if (expr == null) {
                return null;
            }
            parts.add(expr);
        }
        return new Expr.Text(start, end, parts);
    }

    private Expr literal(int start, int end, String text, Expected expected) {
        String number = Literals.numberType(text);
        if (number != null) {
            return expected.accepts(number, registry) ? new Expr.Literal(start, end, number, text) : null;
        }
        if (Literals.isBoolean(text) && expected.accepts("boolean", registry)) {
            return new Expr.Literal(start, end, "boolean", text);
        }
        if (Literals.isTimespan(text) && expected.accepts("timespan", registry)) {
            return new Expr.Literal(start, end, "timespan", text);
        }
        if (Literals.isTime(text) && expected.accepts("time", registry)) {
            return new Expr.Literal(start, end, "time", text);
        }
        String normalized = Literals.normalize(text);
        for (TypeInfo type : expected.types()) {
            if (type.literals().contains(normalized)) {
                return new Expr.Literal(start, end, type.id(), text);
            }
        }
        return specifiedLiteral(start, end, text, expected);
    }

    /** A literal with its type given in parentheses, like {@code black (wolf variant)}. */
    private Expr specifiedLiteral(int start, int end, String text, Expected expected) {
        java.util.regex.Matcher matcher = Literals.SPECIFIED.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        TypeRef ref = registry.resolveType(Literals.normalize(matcher.group(2)));
        if (ref == null || !expected.accepts(ref.type().id(), registry)) {
            return null;
        }
        String value = Literals.normalize(matcher.group(1));
        TypeInfo type = ref.type();
        boolean valid = type.literals().contains(value) || ((type.openLiterals() || type.literals().isEmpty())
                && Literals.isWords(value));
        return valid ? new Expr.Literal(start, end, type.id(), text) : null;
    }

    private Expr variable(int start, int end) {
        List<int[]> embedded = new ArrayList<>();
        Scanner.braceEnd(input, start, end, embedded);
        String name = input.substring(start + 1, end - 1);
        if (name.isBlank()) {
            return null;
        }
        List<Expr> parts = new ArrayList<>();
        for (int[] part : embedded) {
            Expr expr = expression(part[0], part[1], Expected.OBJECTS);
            if (expr == null) {
                return null;
            }
            parts.add(expr);
        }
        return new Expr.Variable(start, end, name, name.startsWith("_"), name.endsWith("::*"), parts);
    }

    /**
     * {@code name(arguments)} for a known function. Each comma-separated argument goes to the next
     * parameter, or to the one it names ({@code name: value}). If exactly one overload takes the arguments
     * as given, it's called; if several do, the call is ambiguous. Only if none do can an overload whose
     * last parameter takes a list take the extra values. Problems are recorded in Skript's wording, for
     * the line's error.
     */
    private Expr.FunctionCall functionCall(int start, int end, boolean needsValue) {
        int open = input.indexOf('(', start);
        if (open <= start || open >= end || !FUNCTION_NAME.matcher(input.substring(start, open)).matches()
                || Scanner.parenEnd(input, open, end) != end) {
            return null;
        }
        String name = input.substring(start, open);
        List<FunctionInfo> overloads = context.functions(name);
        if (overloads.isEmpty()) {
            return null;
        }
        int[] inner = trim(open + 1, end - 1);
        List<int[]> pieces = inner[0] >= inner[1] ? List.of() : Scanner.split(input, inner[0], inner[1], ',');
        List<Expr.FunctionCall> matches = new ArrayList<>();
        String problem = null;
        for (FunctionInfo function : overloads) {
            if (needsValue && function.returnType() == null) {
                continue;
            }
            Object result = arguments(function, pieces, inner, false);
            if (result instanceof List<?> arguments) {
                matches.add(call(start, end, function, arguments));
            } else if (result instanceof String error && (problem == null
                    || function.parameters().size() == pieces.size())) {
                problem = error;
            }
        }
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        if (matches.size() > 1) {
            functionProblem("Cannot determine which function named '" + name + "' to call");
            return null;
        }
        for (FunctionInfo function : overloads) {
            if (!needsValue || function.returnType() != null) {
                if (arguments(function, pieces, inner, true) instanceof List<?> arguments) {
                    return call(start, end, function, arguments);
                }
            }
        }
        boolean onlyUnknownNames = pieces.stream().anyMatch(piece -> argumentName(piece) != null)
                && pieces.stream().map(this::argumentName).noneMatch(n -> n != null
                        && overloads.stream().anyMatch(f -> parameterIndex(f.parameters(), n) >= 0));
        functionProblem(problem != null && !onlyUnknownNames
                ? problem
                : "The function " + name + "(" + describe(pieces) + ") does not exist");
        return null;
    }

    @SuppressWarnings("unchecked")
    private Expr.FunctionCall call(int start, int end, FunctionInfo function, List<?> arguments) {
        String type = function.returnType() == null ? SyntaxRegistry.OBJECT : function.returnType();
        return new Expr.FunctionCall(start, end, function, (List<Expr>) arguments, type);
    }

    /**
     * Maps the argument pieces to {@code function}'s parameters. Returns the arguments by parameter, an
     * error message, or {@code null} if the function just doesn't take that many arguments. With
     * {@code absorb}, a list parameter at the end takes all the remaining arguments.
     */
    private Object arguments(FunctionInfo function, List<int[]> pieces, int[] inner, boolean absorb) {
        List<FunctionInfo.Parameter> parameters = function.parameters();
        boolean anyNamed = pieces.stream().anyMatch(piece -> argumentName(piece) != null);
        if (absorb) {
            // Only plain values are absorbed into a list parameter, never named arguments.
            if (anyNamed || parameters.isEmpty() || !parameters.getLast().plural() || pieces.size() <= parameters.size()) {
                return null;
            }
            int[] last = {pieces.get(parameters.size() - 1)[0], inner[1]};
            pieces = new ArrayList<>(pieces.subList(0, parameters.size() - 1));
            pieces.add(last);
        }
        if (mixesNamedAndUnnamed(pieces, parameters)) {
            return "Mixing named and unnamed arguments is not allowed";
        }
        Expr[] byParameter = new Expr[parameters.size()];
        int next = 0;
        for (int p = 0; p < pieces.size(); p++) {
            int[] piece = pieces.get(p);
            int index = next;
            int valueStart = piece[0];
            String named = argumentName(piece);
            if (named != null) {
                int found = parameterIndex(parameters, named);
                if (found >= 0) {
                    index = found;
                    valueStart = input.indexOf(':', piece[0]) + 1;
                } else if (pieces.subList(p + 1, pieces.size()).stream().anyMatch(q -> argumentName(q) != null)) {
                    return "The argument named '" + named + "' is unexpected";
                }
            }
            if (index >= parameters.size()) {
                return null;
            }
            FunctionInfo.Parameter parameter = parameters.get(index);
            if (byParameter[index] != null) {
                return "A value has already been assigned to parameter '" + parameter.name() + "'";
            }
            TypeRef ref = registry.resolveType(parameter.type());
            // Skript only checks the number of values when the function runs, so lists are accepted here.
            Expected expected = ref == null ? Expected.OBJECTS : context.expected(new TypeRef(ref.type(), true));
            Expr argument = expression(valueStart, piece[1], expected);
            if (argument == null) {
                Expr anything = expression(valueStart, piece[1], Expected.OBJECTS);
                if (anything != null && !(anything instanceof Expr.Unresolved)) {
                    // It's a value, just not of this parameter's type: no such overload.
                    return null;
                }
                String typeName = ref == null ? parameter.type() : ref.type().name().toLowerCase(java.util.Locale.ROOT);
                return "Can't understand the argument for the parameter '" + parameter.name() + "' with type '"
                        + typeName + "': " + input.substring(valueStart, piece[1]).strip() + ".";
            }
            byParameter[index] = argument;
            next = index + 1;
        }
        for (int i = 0; i < parameters.size(); i++) {
            if (byParameter[i] == null && !parameters.get(i).optional()) {
                return null;
            }
        }
        return java.util.Arrays.asList(byParameter);
    }

    /**
     * Whether an unnamed argument follows a named one that's out of order: named arguments then have to be
     * in parameter order, each at or after its own position.
     */
    private boolean mixesNamedAndUnnamed(List<int[]> pieces, List<FunctionInfo.Parameter> parameters) {
        boolean namedSeen = false;
        boolean unnamedAfterNamed = false;
        for (int[] piece : pieces) {
            boolean isNamed = argumentName(piece) != null;
            unnamedAfterNamed |= namedSeen && !isNamed;
            namedSeen |= isNamed;
        }
        if (!unnamedAfterNamed) {
            return false;
        }
        int last = -1;
        for (int p = 0; p < pieces.size(); p++) {
            String named = argumentName(pieces.get(p));
            int index = named == null ? -1 : parameterIndex(parameters, named);
            if (index < 0) {
                continue;
            }
            if (index < p || index < last) {
                return true;
            }
            last = index;
        }
        return false;
    }

    private static int parameterIndex(List<FunctionInfo.Parameter> parameters, String name) {
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /** The name in a {@code name: value} argument, or {@code null}. */
    private String argumentName(int[] piece) {
        int colon = input.indexOf(':', piece[0]);
        if (colon < 0 || colon >= piece[1]) {
            return null;
        }
        String name = input.substring(piece[0], colon).strip();
        return FUNCTION_NAME.matcher(name).matches() ? name : null;
    }

    /** The arguments' types as Skript describes them in errors: {@code integer, n: integers, ?}. */
    private String describe(List<int[]> pieces) {
        List<String> types = new ArrayList<>();
        for (int[] piece : pieces) {
            String named = argumentName(piece);
            int valueStart = named == null ? piece[0] : input.indexOf(':', piece[0]) + 1;
            Expr value = expression(valueStart, piece[1], Expected.OBJECTS);
            String type;
            if (value == null) {
                type = "?";
            } else {
                co.xenastudios.neoskript.frontend.registry.TypeInfo info = registry.type(value.type());
                String typeName = info == null ? value.type() : info.name().toLowerCase(java.util.Locale.ROOT);
                type = value instanceof Expr.ListOf ? SyntaxRegistry.plural(typeName) : typeName;
            }
            types.add(named == null ? type : named + ": " + type);
        }
        return String.join(", ", types);
    }

    /** The first problem found with a function call in this line, in Skript's wording, or {@code null}. */
    String functionProblem() {
        return functionProblem;
    }

    private void functionProblem(String problem) {
        if (functionProblem == null) {
            functionProblem = problem;
        }
    }

    /**
     * A list: items separated by {@code ,}, {@code and}, {@code or} or {@code nor}. Not every separator has
     * to split the list ({@code characters between "a" and "f", 1} has an {@code and} inside an item), so
     * this finds a choice of separators where every item parses, preferring more items.
     */
    private Expr list(int start, int end, Expected expected) {
        boolean[] and = {true};
        List<int[]> separators = Scanner.listSeparators(input, start, end, and);
        if (separators.isEmpty()) {
            return null;
        }
        Expected item = context.intern(expected.single());
        int n = separators.size();
        // reach[k]: the items that cover [start, separator k's start), or null if none parse. k == n is the end.
        List<List<Expr>> reach = new ArrayList<>(java.util.Collections.nCopies(n + 1, (List<Expr>) null));
        for (int k = 0; k <= n; k++) {
            int itemEnd = k == n ? end : separators.get(k)[0];
            Expr first = expression(start, itemEnd, item);
            if (first != null && k < n) {
                reach.set(k, List.of(first));
            }
            for (int j = 0; j < k && (reach.get(k) == null || k == n); j++) {
                if (reach.get(j) == null) {
                    continue;
                }
                Expr next = expression(separators.get(j)[1], itemEnd, item);
                if (next != null && (reach.get(k) == null || reach.get(j).size() + 1 > reach.get(k).size())) {
                    List<Expr> items = new ArrayList<>(reach.get(j));
                    items.add(next);
                    reach.set(k, items);
                }
            }
        }
        List<Expr> items = reach.get(n);
        if (items == null) {
            return null;
        }
        String type = items.stream().map(Expr::type).distinct().count() == 1 ? items.getFirst().type() : SyntaxRegistry.OBJECT;
        return new Expr.ListOf(start, end, items, and[0], type);
    }

    private Expr unresolved(int start, int end, Expected expected) {
        String text = input.substring(start, end);
        if (!Literals.isWords(text)) {
            return null;
        }
        boolean open = expected.anything() || expected.types().stream()
                .anyMatch(type -> type.openLiterals() && registry.mightBeLiteral(type, text))
                || registry.isConvertibleLiteral(text, id -> expected.types().stream()
                        .anyMatch(type -> !type.id().equals(id) && registry.isConvertible(id, type.id())));
        return open ? new Expr.Unresolved(start, end, text) : null;
    }

    private static boolean isChangeable(Expr expr) {
        return switch (expr) {
            case Expr.Literal literal -> false;
            case Expr.Text text -> false;
            case Expr.Unresolved unresolved -> false;
            case Expr.ListOf list -> list.items().stream().allMatch(LineParser::isChangeable);
            default -> true;
        };
    }

    // ---------------------------------------------------------------- helpers

    private Object memoized(int start, int end, Object what, java.util.function.Supplier<Object> compute) {
        Key key = new Key(start, end, what);
        Object cached = memo.get(key);
        if (cached != null) {
            return cached == IN_PROGRESS || cached == FAILED ? null : cached;
        }
        memo.put(key, IN_PROGRESS);
        Object result = compute.get();
        memo.put(key, result == null ? FAILED : result);
        return result;
    }

    private int[] trim(int start, int end) {
        while (start < end && Character.isWhitespace(input.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(input.charAt(end - 1))) {
            end--;
        }
        return new int[] {start, end};
    }
}
