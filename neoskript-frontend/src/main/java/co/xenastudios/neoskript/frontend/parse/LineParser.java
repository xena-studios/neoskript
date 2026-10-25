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
    /** The event values the line can use, or {@code null} for any. Fixed for the line, so the memo holds. */
    private final co.xenastudios.neoskript.frontend.registry.EventValues.Scope eventScope;
    private boolean refusedEventValues;
    private final String input;
    private final String lowerInput;
    private final Map<Key, Object> memo = new HashMap<>();
    /** What was parsed while code had to stand without unresolved words, kept apart from the rest. */
    private final Map<Key, Object> strictMemo = new HashMap<>();
    /** Whether an outer parse is in its first, strict pass: nested ones then stay strict too. */
    private boolean strictOuter;
    /** How many times a placeholder was refused for starting inside a token like {@code arg-1}. */
    private int tokenSplits;
    /** How many times code in a regex part was turned down for leaning on unresolved words. */
    private int unresolvedCode;
    private String functionProblem;
    private String problem;
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

    /** Whether variables are held to their type hints; off to find which one is wrong after a line fails. */
    private boolean checkHints = true;

    LineParser(ParserContext context, String input, boolean checkHints) {
        this(context, input);
        this.checkHints = checkHints;
    }

    LineParser(ParserContext context, String input) {
        this.context = context;
        this.registry = context.registry();
        this.eventScope = context.eventScope();
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
        int leaning = unresolvedCode;
        // First, conditions and code inside the syntax may not lean on unresolved words, so 'assert X is 2 to
        // fail' is the assertion of 'X is 2' failing, not of 'X is <2 to fail>'; only if nothing else works may they.
        // Inside an outer strict pass, nested syntax stays strict, so a split like 'set whether X is going to explode
        // to true' isn't taken with its code leaning on unresolved words when another split needs none.
        boolean outer = strictOuter;
        strictOuter = true;
        Object result;
        try {
            result = matchSyntax(kind, start, end, literalOnly, expected, false, true);
        } finally {
            strictOuter = outer;
        }
        if (result == null && unresolvedCode != leaning && !outer) {
            result = matchSyntax(kind, start, end, literalOnly, expected, false, false);
        }
        return result != null || tokenSplits == rejected ? result
                : matchSyntax(kind, start, end, literalOnly, expected, true, false);
    }

    private Object matchSyntax(SyntaxKind kind, int start, int end, boolean literalOnly, Expected expected,
            boolean splitTokens, boolean strictCode) {
        String text = input.substring(start, end);
        String lower = lowerInput.substring(start, end);
        PatternIndex<SyntaxRegistry.Candidate> index = registry.index(kind);
        Object fallback = null;
        int fallbackUnresolved = Integer.MAX_VALUE;
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
            // A kind of entity alone is the event's entity only where an entity type can't go.
            // ('delete pig' too: a literal can't be changed.)
            if (syntax.regexRoles().contains(RegexRole.BARE_ENTITY) && !bareEntityFits(expected)) {
                continue;
            }
            CompiledPattern pattern = syntax.patterns().get(candidate.pattern());
            if (!pattern.couldMatch(lower)) {
                continue;
            }
            // First without placeholders that lean on unresolved words, so a split that needs none wins over an
            // earlier one that does: 'draw X over 5 seconds at test-location' isn't 'draw X <over (5 seconds at
            // test) - location>'. Only if a placeholder was turned down for them is it tried again with them.
            boolean[] leaned = new boolean[1];
            MatchResult match = pattern.match(input, start, end,
                    resolver(syntax, literalOnly, splitTokens, strictCode, leaned));
            if (match == null && leaned[0]) {
                match = pattern.match(input, start, end, resolver(syntax, literalOnly, splitTokens, strictCode, null));
            }
            if (match == null) {
                continue;
            }
            ParsedSyntax parsed = new ParsedSyntax(syntax, candidate.pattern(), match);
            // An event value the code can't have isn't one, so another reading is tried, as Skript's fails to
            // initialize: 'amount of slots of {_inv}' in 'on load' is the inventory's number of slots.
            if (eventScope != null && (hasTypeRole(syntax) && !eventValueAllowed(parsed, text)
                    || !leftOutAllowed(syntax, pattern, match))) {
                refusedEventValues = true;
                continue;
            }
            if (syntax.id().equals("ExprArithmetic") && !arithmeticOperands(parsed)) {
                continue;
            }
            // 'all colours' only for types whose values can be listed; 'all players' is something else's.
            if (syntax.id().equals("ExprSets") && !parsed.match().regexValues().isEmpty()
                    && !(parsed.match().regexValues().getFirst() instanceof TypeRef ref && registry.isEnumerable(ref.type().id()))) {
                continue;
            }
            // Filtering one value isn't filtering, as Skript's filter refuses: '"1" parsed as "%integer%" where [...]'.
            if (syntax.id().equals("ExprFilter") && parsed.match().values().getFirst() instanceof Expr filtered
                    && Plurality.single(filtered, registry)) {
                continue;
            }
            Object result = parsed;
            if (expected != null) {
                String type = returnType(parsed);
                if (!expected.accepts(type, registry)) {
                    continue;
                }
                result = new Expr.Syntax(start, end, parsed, type);
                // Several values can't go where one does: 'test location' isn't the entities called 'test'.
                if (expected.onlyOne() && Plurality.plural((Expr) result, registry)) {
                    if (problem == null) {
                        problem = "'" + text.strip() + "' can only be a single " + typeNames(expected) + ", not more.";
                    }
                    continue;
                }
            }
            // A parse that leans on unresolved words only wins if nothing parses without them, as Skript
            // only accepts real literals: 'loot context at test-location' isn't '(...test) - location'.
            if (!hasUnresolved(parsed)) {
                return result;
            }
            // Of those, the one leaning on the least text: 'last spawned firework is set' isn't a comparison with 'set'.
            int unresolved = countUnresolved(parsed);
            if (unresolved < fallbackUnresolved) {
                fallback = result;
                fallbackUnresolved = unresolved;
            }
        }
        return fallback;
    }

    /**
     * Whether {@code position} is just after a letter and a hyphen, as the {@code 1} of {@code arg-1}: there
     * the hyphen is part of a word, not a minus sign.
     */
    private boolean splitsToken(int position) {
        // ...or of a hyphenated word, as the 'value' of 'loop-value': only if nothing else parses is it split there.
        return position >= 2 && position < input.length() && Character.isLetterOrDigit(input.charAt(position))
                && input.charAt(position - 1) == '-' && Character.isLetter(input.charAt(position - 2));
    }

    /** Types arithmetic works on; an operand of another known type rules arithmetic out, as in Skript. */
    private static final java.util.Set<String> ARITHMETIC_TYPES = java.util.Set.of(SyntaxRegistry.OBJECT, "number",
            "integer", "long", "timespan", "date", "time", "timeperiod", "string", "vector", "location", "experience",
            // Vault's money, when it's hooked.
            "money");

    /** Whether arithmetic's operands could be added: {@code the script named X + "y"} isn't arithmetic. */
    private boolean arithmeticOperands(ParsedSyntax parsed) {
        for (Object value : parsed.match().values()) {
            if (value instanceof Expr expr && expr.type() != null && !ARITHMETIC_TYPES.contains(expr.type())
                    && registry.type(expr.type()) != null) {
                return false;
            }
            // A word only types without arithmetic have, like 'red': 'red-blue kob' isn't a subtraction.
            if (value instanceof Expr.Unresolved unresolved && onlyNonArithmetic(Literals.normalize(unresolved.text()))) {
                return false;
            }
        }
        return true;
    }

    private boolean onlyNonArithmetic(String word) {
        boolean named = false;
        for (TypeInfo type : registry.types()) {
            if (type.literals().contains(word)) {
                if (ARITHMETIC_TYPES.contains(type.id())) {
                    return false;
                }
                named = true;
            }
        }
        return named;
    }

    /** {@code code}, unless {@code strict} and it leans on unresolved words; counts each one turned down. */
    private ParsedSyntax strict(ParsedSyntax code, boolean strict) {
        // Words a type's parser took, like 'wooden hoe', are as good as resolved.
        if (strict && code != null && countUnresolved(code) > 0) {
            unresolvedCode++;
            return null;
        }
        return code;
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

    /** How much of the line {@code parsed} leaves to unresolved words. */
    static int countUnresolved(ParsedSyntax parsed) {
        int count = 0;
        for (Object value : parsed.match().values()) {
            if (value instanceof Expr expr) {
                count += countUnresolved(expr);
            }
        }
        for (Object value : parsed.match().regexValues()) {
            if (value instanceof Expr expr) {
                count += countUnresolved(expr);
            } else if (value instanceof ParsedSyntax inner) {
                count += countUnresolved(inner);
            }
        }
        return count;
    }

    /** Skript's warnings about the lists in what was parsed of this line, added to the line's warnings. */
    void listWarnings(Object parsed) {
        listWarnings(input, parsed, warnings);
    }

    /**
     * Skript's warnings about the lists in {@code parsed}, whose positions are in {@code input}: a list of more
     * than one value joined only by commas, which is an 'and' list, and one with both 'and' and 'or'.
     */
    static void listWarnings(String input, Object parsed, java.util.Set<String> into) {
        switch (parsed) {
            case ParsedSyntax syntax -> {
                syntax.match().values().forEach(value -> listWarnings(input, value, into));
                syntax.match().regexValues().forEach(value -> listWarnings(input, value, into));
            }
            case Expr.Syntax syntax -> listWarnings(input, syntax.syntax(), into);
            case Expr.ListOf list -> {
                listWarning(input, list, into);
                list.items().forEach(item -> listWarnings(input, item, into));
            }
            // Skript throws away the warnings parsing function arguments gives.
            case Expr.FunctionCall call -> {}
            case Expr.Text text -> text.embedded().forEach(part -> listWarnings(input, part, into));
            case Expr.Variable variable -> variable.embedded().forEach(part -> listWarnings(input, part, into));
            case null, default -> {}
        }
    }

    /** Skript's {@code parseExpressionList} warnings for one list, from the words between its items. */
    private static void listWarning(String input, Expr.ListOf list, java.util.Set<String> into) {
        List<Expr> items = list.items();
        if (items.size() < 2 || list.end() > input.length()) {
            return;
        }
        String text = input.substring(list.start(), list.end()).strip();
        Boolean and = null;
        for (int i = 1; i < items.size(); i++) {
            int from = items.get(i - 1).end();
            int to = items.get(i).start();
            if (from > to || to > input.length()) {
                return;
            }
            // Brackets around an item aren't part of the delimiter.
            String delimiter = input.substring(from, to).replaceAll("^[\\s)]+|[\\s(]+$", "").toLowerCase(Locale.ROOT);
            if (delimiter.equals(",")) {
                continue;
            }
            boolean or = !delimiter.endsWith("nor") && delimiter.endsWith("or");
            if (and == null) {
                and = !or;
            } else if (and != !or) {
                into.add(":List has multiple 'and' or 'or', will default to 'and'. Use brackets if you want to define"
                        + " multiple lists. List: " + text);
                and = true;
            }
        }
        if (and == null) {
            into.add("conjunction:List is missing 'and' or 'or', defaulting to 'and': " + text);
        }
    }

    static int countUnresolved(Expr expr) {
        return switch (expr) {
            // Words a type's parser has accepted are as good as resolved.
            case Expr.Unresolved unresolved -> unresolved.verified() ? 0 : unresolved.end() - unresolved.start();
            case Expr.Syntax syntax -> countUnresolved(syntax.syntax());
            case Expr.ListOf list -> list.items().stream().mapToInt(LineParser::countUnresolved).sum();
            case Expr.FunctionCall call -> call.arguments().stream().filter(a -> a != null)
                    .mapToInt(LineParser::countUnresolved).sum();
            case Expr.Text text -> text.embedded().stream().mapToInt(LineParser::countUnresolved).sum();
            case Expr.Variable variable -> variable.embedded().stream().mapToInt(LineParser::countUnresolved).sum();
            case Expr.Literal literal -> 0;
        };
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

    /** Whether an event value the line can't use was turned down: only then could parsing with any differ. */
    boolean refusedEventValues() {
        return refusedEventValues;
    }

    /** Whether the placeholders {@code match} left out can be, where the event's value may have to stand for them. */
    private boolean leftOutAllowed(SyntaxInfo syntax, CompiledPattern pattern, MatchResult match) {
        List<Object> values = match.values();
        PlaceholderSpec[] specs = null;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == null) {
                specs = specs != null ? specs : context.specs(pattern);
                if (i < specs.length && specs[i] != null && !eventScope.allowsLeftOut(syntax, specs[i])) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether {@code parsed}, if it's an event value, is one the line can use. */
    private boolean eventValueAllowed(ParsedSyntax parsed, String text) {
        for (Object value : parsed.match().regexValues()) {
            if (value instanceof TypeRef ref) {
                return eventScope.allows(parsed.syntax(), ref, text.strip());
            }
        }
        return true;
    }

    private boolean bareEntityFits(Expected expected) {
        if (expected == null) {
            return false;
        }
        if (expected.anything()) {
            return expected.changeable();
        }
        return !expected.accepts("entitytype", registry) && expected.types().stream().anyMatch(t ->
                registry.isSubtype("entity", t.id()) || registry.isSubtype(t.id(), "entity")
                        || registry.isConvertible("entity", t.id()));
    }

    private boolean returnTypeFits(SyntaxInfo syntax, Expected expected) {
        // Expressions whose type comes from a regex (event-<type>) are checked after matching.
        return syntax.returnType() == null || hasTypeRole(syntax) || expected.accepts(syntax.returnType(), registry);
    }

    private static boolean hasTypeRole(SyntaxInfo syntax) {
        return syntax.regexRoles().contains(RegexRole.TYPE) || syntax.regexRoles().contains(RegexRole.TYPE_OR_ENTITY)
                || syntax.regexRoles().contains(RegexRole.BARE_ENTITY);
    }

    private static String returnType(ParsedSyntax parsed) {
        for (Object value : parsed.match().regexValues()) {
            if (value instanceof TypeRef ref) {
                return ref.type().id();
            }
        }
        String type = parsed.syntax().returnType();
        if (CHOOSING.contains(parsed.syntax().id())) {
            // 'X if C otherwise Y', 'X otherwise Y': one of its values, so their type when they share one.
            String shared = null;
            for (Object value : parsed.match().values()) {
                String own = value instanceof Expr expr && !(expr instanceof Expr.Variable) ? expr.type() : null;
                if (own == null || own.equals(SyntaxRegistry.OBJECT) || shared != null && !shared.equals(own)) {
                    return SyntaxRegistry.OBJECT;
                }
                shared = own;
            }
            return shared == null ? SyntaxRegistry.OBJECT : shared;
        }
        return type == null ? SyntaxRegistry.OBJECT : type;
    }

    /** Expressions that give one of their arguments' values. */
    private static final java.util.Set<String> CHOOSING = java.util.Set.of("ExprTernary", "ExprDefaultValue");

    /**
     * Resolves a pattern's placeholders and regexes. With {@code leaned} given, placeholders whose expression leans
     * on unresolved words are turned down, and {@code leaned[0]} is set when one is.
     */
    private PlaceholderResolver resolver(SyntaxInfo syntax, boolean literalOnly, boolean splitTokens, boolean strictCode,
            boolean[] leaned) {
        return new PlaceholderResolver() {
            @Override
            public Object resolve(String text, int start, int end, PlaceholderSpec spec) {
                if (!splitTokens && splitsToken(start)) {
                    tokenSplits++;
                    return null;
                }
                Expected expected = context.expected(spec);
                Expr value = expression(start, end, literalOnly ? context.intern(expected.asLiteralOnly()) : expected);
                if (leaned != null && value != null && countUnresolved(value) > 0) {
                    leaned[0] = true;
                    return null;
                }
                return value;
            }

            @Override
            public Object resolveRegex(String text, int start, int end, int index, java.util.regex.MatchResult match) {
                return switch (syntax.regexRole(index)) {
                    case ANY -> match;
                    case CONDITION -> strict(condition(start, end), strictCode);
                    case EFFECT -> {
                        ParsedSyntax effect = effect(start, end);
                        yield effect != null ? strict(effect, strictCode) : call(start, end);
                    }
                    case EXPRESSION -> expression(start, end, Expected.OBJECTS);
                    case TYPE -> typeName(start, end);
                    case TYPE_OR_ENTITY -> {
                        TypeRef ref = typeName(start, end);
                        TypeInfo entity = registry.type("entity");
                        yield ref != null || entity == null || !registry.isEntityTypeLiteral(input.substring(start, end))
                                ? ref : new TypeRef(entity, false);
                    }
                    case BARE_ENTITY -> {
                        String words = input.substring(start, end);
                        TypeInfo entity = registry.type("entity");
                        if (words.regionMatches(true, 0, "a ", 0, 2) || words.regionMatches(true, 0, "an ", 0, 3)) {
                            yield null;
                        }
                        yield entity != null && registry.isEntityTypeLiteral(words) && !registry.isItemTypeLiteral(words)
                                ? new TypeRef(entity, false) : null;
                    }
                    case LOOP_VALUE -> isLoopValueName(text.substring(start, end)) ? match : null;
                    case NOT_CONDITION -> condition(start, end) == null ? match : null;
                    case CODE -> {
                        Object code = condition(start, end);
                        if (code == null) {
                            code = effect(start, end);
                        }
                        yield code != null ? strict((ParsedSyntax) code, strictCode) : expression(start, end, Expected.OBJECTS);
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
        char first = input.charAt(start);
        char last = input.charAt(end - 1);
        if (first == '\u201C' || first == '\u201D' || last == '\u201C' || last == '\u201D') {
            // As Skript's parser refuses them where an expression starts or ends.
            problem("Pretty quotes are not allowed, change to regular quotes (\")");
            return null;
        }
        Expr expr = single(start, end, expected);
        if (expr != null && !(expr instanceof Expr.Unresolved) && hasUnresolved(expr)) {
            // Words a type's parser takes whole are that literal, not syntax leaning on unresolved words, as
            // Skript's syntax can't take words it can't parse: 'baby normal-normal panda' isn't a subtraction.
            Expr whole = verifiedLiteral(start, end, expected);
            if (whole != null) {
                expr = whole;
            }
        }
        if (expr != null && expected.plural() && hasUnresolved(expr)) {
            // Leaning on unresolved words loses to a list that doesn't: '1 or 0 or -1' isn't '(1 or 0 or) - 1'.
            // Nor to one whose words are known literals: 'the block at the event-location and an oak sign'
            // isn't '(the block at the event) - (location and an oak sign)'.
            Expr list = list(start, end, expected);
            if (list != null && (!hasUnresolved(list) || countUnresolved(list) < countUnresolved(expr))) {
                expr = list;
            }
        }
        if (expr == null && expected.plural()) {
            // A literal that has 'and' in it, like 'diamond sword of unbreaking 3 and sharpness 10', is one value
            // if a type's parser takes all of it, as in Skript.
            expr = wholeLiteral(start, end, expected);
        }
        if (expr == null && expected.plural()) {
            expr = list(start, end, expected);
        }
        if (expr == null) {
            expr = unresolved(start, end, expected);
        }
        if (expr != null && expected.changeable() && !isChangeable(expr)) {
            return null;
        }
        if (expr != null && expected.onlyOne() && Plurality.plural(expr, registry)) {
            // Several values where only one goes, as Skript's parser checks. A word that may yet be a value of
            // the type ('dirt', not the entities called that) is still one.
            Expr word = unresolved(start, end, expected);
            if (word != null) {
                return word;
            }
            problem("'" + input.substring(start, end) + "' can only be a single " + typeNames(expected) + ", not more.");
            return null;
        }
        return expr;
    }

    /** The types expected, as Skript lists them in errors: {@code number, text or player}. */
    private static String typeNames(Expected expected) {
        List<TypeInfo> types = expected.types();
        if (types.isEmpty()) {
            return "object";
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                names.append(i == types.size() - 1 ? " or " : ", ");
            }
            names.append(types.get(i).name().toLowerCase(java.util.Locale.ROOT));
        }
        return names.toString();
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
            Expr variable = variable(start, end);
            return variable instanceof Expr.Variable v && !hintFits(v, expected) ? null : variable;
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
            Expr expr = embedded(part[0], part[1]);
            if (expr == null) {
                return null;
            }
            parts.add(expr);
        }
        return new Expr.Text(start, end, parts);
    }

    /**
     * The code in a text's or variable name's {@code %...%}. As Skript parses it as expressions only, a literal
     * there isn't understood ({@code "%1%"}, {@code "%stone%"}), nor is a list with one, with Skript's errors.
     */
    private Expr embedded(int start, int end) {
        Expr expr = expression(start, end, Expected.OBJECTS);
        String code = input.substring(start, end);
        if (expr == null) {
            problem("Can't understand this expression: " + code);
            return null;
        }
        if (isLiteral(expr)) {
            problem("Can't understand this expression: " + code);
            return null;
        }
        if (expr instanceof Expr.ListOf list && list.items().stream().anyMatch(LineParser::isLiteral)) {
            Expr first = list.items().getFirst();
            String firstText = input.substring(first.start(), first.end()).strip();
            boolean commas = input.substring(first.end(), end).stripLeading().startsWith(",");
            problem(!commas ? "Can't understand this expression: " + code
                    : isLiteral(first) ? "Invalid brackets/variables/text in '" + firstText + ",'"
                    : first instanceof Expr.Variable ? "Variables cannot be used here."
                    : "Can't understand this expression: " + code);
            return null;
        }
        return expr;
    }

    /** Whether an expression is a literal: written values, or words nothing but a type's parser reads. */
    private static boolean isLiteral(Expr expr) {
        return expr instanceof Expr.Literal || expr instanceof Expr.Unresolved;
    }

    private Expr literal(int start, int end, String text, Expected expected) {
        String number = Literals.numberType(text);
        if (number != null) {
            return expected.accepts(number, registry) ? new Expr.Literal(start, end, number, text) : null;
        }
        if (Literals.isBoolean(text) && expected.accepts("boolean", registry)) {
            return new Expr.Literal(start, end, "boolean", text);
        }
        if (Literals.isTimespan(text) && expected.accepts("timespan", registry)
                && (!Literals.isBareUnit(text) || expected.hasType("timespan"))) {
            return new Expr.Literal(start, end, "timespan", text);
        }
        if (Literals.isTime(text) && expected.accepts("time", registry)) {
            return new Expr.Literal(start, end, "time", text);
        }
        String normalized = Literals.normalize(text);
        TypeInfo named = null;
        for (TypeInfo type : expected.types()) {
            if (type.literals().contains(normalized)) {
                if (named != null) {
                    // A word several types have, like 'normal' (an environment and a panda gene): what it is
                    // depends on what it's used with, so the compiler decides.
                    return null;
                }
                named = type;
            }
        }
        if (named != null) {
            return new Expr.Literal(start, end, named.id(), text);
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
        // Open types check the words with the type's own parser: 'size of all chickens (entity type)' isn't one.
        boolean valid = type.literals().contains(value) || ((type.openLiterals() || type.literals().isEmpty())
                && Literals.isWords(value) && registry.mightBeLiteral(type, value)) || registry.parses(type, value);
        return valid ? new Expr.Literal(start, end, type.id(), text) : null;
    }

    /** Why a variable didn't fit, by its type hints, for the line's error; or {@code null}. */
    private String hintProblem;

    String hintProblem() {
        return hintProblem;
    }

    /**
     * Whether a variable may be what's expected by its type hints, as in Skript: {@code {_x}} holding an
     * integer can't be a text. If not, the reason is kept for the line's error.
     */
    boolean hintFits(Expr.Variable variable, Expected expected) {
        TypeHints hints = context.hints();
        if (!checkHints || !hints.active() || expected.anything() || !StatementParser.simple(variable)) {
            return true;
        }
        java.util.Set<String> types = hints.get(variable.name());
        if (types.isEmpty()) {
            return true;
        }
        for (TypeInfo wanted : expected.types()) {
            for (String type : types) {
                if (registry.isAssignable(type, wanted.id())) {
                    return true;
                }
            }
        }
        hintProblem = "Expected variable '{" + variable.name() + "}' to be "
                + typeNames(expected.types().stream().map(TypeInfo::id).toList()) + ", but it is " + typeNames(types);
        return false;
    }

    /** Types as Skript lists them in hints: {@code an integer or boolean (yes/no)}. */
    private String typeNames(java.util.Collection<String> ids) {
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            TypeInfo info = registry.type(id);
            String name = info == null ? id : info.name().toLowerCase(java.util.Locale.ROOT);
            names.add(name.equals("boolean") ? "boolean (yes/no)" : name);
        }
        String joined = names.size() == 1 ? names.getFirst()
                : String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.getLast();
        return ("aeiou".indexOf(joined.charAt(0)) >= 0 ? "an " : "a ") + joined;
    }

    private Expr variable(int start, int end) {
        List<int[]> embedded = new ArrayList<>();
        Scanner.braceEnd(input, start, end, embedded);
        String name = input.substring(start + 1, end - 1);
        if (name.isBlank() || !validVariableName(name)) {
            return null;
        }
        List<Expr> parts = new ArrayList<>();
        for (int[] part : embedded) {
            Expr expr = embedded(part[0], part[1]);
            if (expr == null) {
                return null;
            }
            parts.add(expr);
        }
        variableWarnings(name.strip());
        return new Expr.Variable(start, end, name, name.startsWith("_"), name.endsWith("::*"), parts);
    }

    /** The characters Skript reserves at the start of variables' names. */
    private static final String RESERVED_TOKENS = "~.+$!&^*";

    /** Skript's warnings about a variable's name as it makes the variable ({@code Variable.newInstance}). */
    private void variableWarnings(String name) {
        if (name.isEmpty()) {
            return;
        }
        char first = name.charAt(0);
        if (RESERVED_TOKENS.indexOf(first) >= 0) {
            warnings.add(":The character '" + first + "' is reserved at the start of variable names, "
                    + "and may be restricted in future versions");
        }
        String stripped = name.startsWith("_") || name.startsWith("-") ? name.substring(1) : name;
        if (stripped.startsWith("%")) {
            String script = context.scriptName();
            // The script's name without '.sk', as Skript cuts it.
            String prefix = script.length() >= 3 ? script.substring(0, script.length() - 3) : "";
            warnings.add("starting:Starting a variable's name with an expression is discouraged ({" + name + "}). "
                    + "You could prefix it with the script's name: {" + prefix + "::" + name + "}");
        }
    }

    /** Warnings found while parsing the line, as Skript gives them, for the statement parser to report. */
    private final java.util.Set<String> warnings = new java.util.LinkedHashSet<>();

    java.util.Set<String> warnings() {
        return warnings;
    }

    /**
     * Whether a variable's name is one, as Skript's parser checks: no braces outside its expressions, and
     * the separator {@code ::} and list asterisk where they can go (else the problem is kept for the line's
     * error). A single {@code :} is only warned about.
     */
    private boolean validVariableName(String raw) {
        // Text outside the expressions in it ('%...%'), where braces aren't allowed.
        StringBuilder outside = new StringBuilder();
        boolean inExpression = false;
        int depth = 0;
        for (char c : raw.toCharArray()) {
            if (c == '%' && depth == 0) {
                inExpression = !inExpression;
                continue;
            }
            if (inExpression) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
            } else if (c == '{' || c == '}') {
                return false;
            } else {
                outside.append(c);
            }
        }
        String name = raw.startsWith("_") ? raw.substring(1).strip() : raw.strip();
        if (name.startsWith("::") || name.endsWith("::")) {
            problem("A variable's name must neither start nor end with the separator '::' (error in variable {" + name + "})");
            return false;
        }
        String plain = outside.toString();
        int asterisks = (int) plain.chars().filter(c -> c == '*').count();
        if (asterisks > 0 && !(asterisks == 1 && name.endsWith("::*"))) {
            problem("A variable's name must not contain any asterisks except at the end after '::' to denote a list"
                    + " variable, e.g. {variable::*} (error in variable {" + name + "})");
            return false;
        }
        if (name.contains("::::")) {
            problem("A variable's name must not contain the separator '::' multiple times in a row (error in variable {"
                    + name + "})");
            return false;
        }
        if (name.replace("::", "").contains(":")) {
            warnings.add("colon:If you meant to make the variable {" + name + "} a list, its name should contain '::'."
                    + " Having a single ':' does nothing!");
        }
        return true;
    }

    /**
     * {@code name(arguments)} for a known function. Each comma-separated argument goes to the next
     * parameter, or to the one it names ({@code name: value}). If exactly one overload takes the arguments
     * as given, it's called; if several do, the call is ambiguous. Only if none do can an overload whose
     * last parameter takes a list take the extra values. Problems are recorded in Skript's wording, for
     * the line's error.
     */
    private Expr.FunctionCall functionCall(int start, int end, boolean needsValue) {
        // As in Skript, which parses function arguments with their warnings thrown away.
        java.util.Set<String> before = new java.util.HashSet<>(warnings);
        try {
            return parseFunctionCall(start, end, needsValue);
        } finally {
            warnings.retainAll(before);
        }
    }

    private Expr.FunctionCall parseFunctionCall(int start, int end, boolean needsValue) {
        int open = input.indexOf('(', start);
        if (open <= start || open >= end || !FUNCTION_NAME.matcher(input.substring(start, open)).matches()
                || Scanner.parenEnd(input, open, end) != end) {
            return null;
        }
        String name = input.substring(start, open);
        List<FunctionInfo> overloads = context.functions(name);
        int[] inner = trim(open + 1, end - 1);
        List<int[]> pieces = inner[0] >= inner[1] ? List.of() : Scanner.split(input, inner[0], inner[1], ',');
        if (overloads.isEmpty()) {
            functionProblem("The function " + name + "(" + describe(pieces) + ") does not exist.");
            return null;
        }
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
        if (needsValue && matches.isEmpty()) {
            // A function that returns nothing, used for a value: what uses it says why that can't work, as in
            // Skript ('a slot can't be set to foo()').
            for (FunctionInfo function : overloads) {
                if (function.returnType() == null && arguments(function, pieces, inner, false) instanceof List<?> arguments) {
                    return call(start, end, function, arguments);
                }
            }
        }
        boolean onlyUnknownNames = pieces.stream().anyMatch(piece -> argumentName(piece) != null)
                && pieces.stream().map(this::argumentName).noneMatch(n -> n != null
                        && overloads.stream().anyMatch(f -> parameterIndex(f.parameters(), n) >= 0));
        functionProblem(problem != null && !onlyUnknownNames
                ? problem
                : "The function " + name + "(" + describe(pieces) + ") does not exist." + intended(overloads, pieces));
        return null;
    }

    /**
     * Skript's guess at the function meant, for its error: of those whose parameters take every argument whose
     * type is known, the one whose number of parameters is closest to the number of arguments.
     */
    private String intended(List<FunctionInfo> overloads, List<int[]> pieces) {
        List<String> known = new ArrayList<>();
        for (int[] piece : pieces) {
            String named = argumentName(piece);
            int valueStart = named == null ? piece[0] : input.indexOf(':', piece[0]) + 1;
            Expr value = expression(valueStart, piece[1], Expected.OBJECTS);
            if (value != null && countUnresolved(value) == 0 && value.type() != null && !value.type().equals(SyntaxRegistry.OBJECT)) {
                known.add(value.type());
            }
        }
        FunctionInfo best = null;
        for (FunctionInfo function : overloads) {
            List<String> left = new ArrayList<>(known);
            for (FunctionInfo.Parameter parameter : function.parameters()) {
                left.stream().filter(type -> registry.isAssignable(type, parameter.type())).findFirst().ifPresent(left::remove);
            }
            if (left.isEmpty() && (best == null || Math.abs(pieces.size() - function.parameters().size())
                    < Math.abs(pieces.size() - best.parameters().size()))) {
                best = function;
            }
        }
        if (best == null) {
            return "";
        }
        List<String> parameters = new ArrayList<>();
        for (FunctionInfo.Parameter parameter : best.parameters()) {
            co.xenastudios.neoskript.frontend.registry.TypeInfo info = registry.type(parameter.type());
            String type = info == null ? parameter.type() : info.name().toLowerCase(java.util.Locale.ROOT);
            parameters.add(parameter.name() + ": " + (parameter.plural() ? SyntaxRegistry.plural(type) : type));
        }
        return " Did you mean to use the function '" + (best.localTo() != null ? "local " : "") + best.name() + "("
                + String.join(", ", parameters) + ")'?";
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
        java.util.Set<String> names = new java.util.HashSet<>();
        for (int[] piece : pieces) {
            String named = argumentName(piece);
            if (named != null && !names.add(named)) {
                // Checked first, as Skript does, whichever function it would be.
                return "A value has already been assigned to parameter '" + named + "'";
            }
        }
        if (pieces.size() > parameters.size()) {
            // More arguments than it takes: not this function, as Skript skips it before parsing any.
            return null;
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
                String written = input.substring(valueStart, piece[1]).strip();
                if (written.startsWith("(") && written.indexOf('(', 1) > 0) {
                    // A list with a list in it: no function takes that, so there's no such function.
                    return null;
                }
                String typeName = ref == null ? parameter.type() : ref.type().name().toLowerCase(java.util.Locale.ROOT);
                return "Can't understand the argument for the parameter '" + parameter.name() + "' with type '"
                        + typeName + "': " + input.substring(valueStart, piece[1]).strip() + ".";
            }
            if (argument instanceof Expr.ListOf list && list.items().stream().anyMatch(Expr.ListOf.class::isInstance)) {
                // A list of lists isn't an argument: 'f(ns: (1, (2, 3)))'.
                return null;
            }
            if (!parameter.plural() && Plurality.plural(argument, registry) && ref != null) {
                // Read as one value where that's what the parameter takes: 'axolotl' is the entity type there.
                Expr one = expression(valueStart, piece[1], context.expected(new TypeRef(ref.type(), false)));
                if (one != null) {
                    argument = one;
                }
            }
            if (!parameter.plural() && Plurality.plural(argument, registry)) {
                // Several values for a parameter that takes one, as Skript checks when it parses the call.
                String expectedName = ref == null ? parameter.type() : ref.type().name().toLowerCase(java.util.Locale.ROOT);
                TypeInfo given = argument.type() == null ? null : registry.type(argument.type());
                String givenName = given == null ? "objects" : SyntaxRegistry.plural(given.name().toLowerCase(java.util.Locale.ROOT));
                return "Expected type " + expectedName + " for argument '" + parameter.name() + "', but "
                        + input.substring(valueStart, piece[1]).strip() + " is of type " + givenName + ".";
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
            // An unnamed argument after the last parameter, named: 'location(..., pitch: 4, 5)'.
            if (index != p && index == parameters.size() - 1 && p + 1 < pieces.size()
                    && argumentName(pieces.get(p + 1)) == null) {
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
            if (value == null || countUnresolved(value) > 0) {
                // What can't be read, as Skript writes it.
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
        return functionProblem != null ? functionProblem : problem;
    }

    /** Whether {@code [start, end)} of the line is an expression of any type. */
    boolean isExpression(int start, int end) {
        return expression(start, end, Expected.OBJECTS) != null;
    }

    /** A problem found while parsing, reported if the line doesn't parse at all. */
    private void problem(String found) {
        if (problem == null) {
            problem = found;
        }
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
            Expr first = listItem(start, itemEnd, item, expected);
            if (first != null && k < n) {
                reach.set(k, List.of(first));
            }
            for (int j = 0; j < k; j++) {
                if (reach.get(j) == null) {
                    continue;
                }
                Expr next = listItem(separators.get(j)[1], itemEnd, item, expected);
                if (next == null) {
                    continue;
                }
                List<Expr> items = new ArrayList<>(reach.get(j));
                items.add(next);
                // Fewer items leaning on unresolved words, then more items: '1 or 0 or -1' is three numbers.
                List<Expr> current = reach.get(k);
                if (current == null || unresolvedItems(items) < unresolvedItems(current)
                        || unresolvedItems(items) == unresolvedItems(current) && items.size() > current.size()) {
                    reach.set(k, items);
                }
            }
        }
        List<Expr> items = reach.get(n);
        if (items == null) {
            return null;
        }
        if (and[0] && items.size() > 1 && items.stream().anyMatch(each -> !expected.acceptsMany(each.type(), registry))) {
            return null;
        }
        String type = items.stream().map(Expr::type).distinct().count() == 1 ? items.getFirst().type() : SyntaxRegistry.OBJECT;
        return new Expr.ListOf(start, end, items, and[0], type);
    }

    /** How many items are syntax leaning on unresolved words; words alone may still be a literal. */
    private static int unresolvedItems(List<Expr> items) {
        int count = 0;
        for (Expr item : items) {
            if (item instanceof Expr.Syntax && hasUnresolved(item)) {
                count++;
            }
        }
        return count;
    }

    /** An item of a list: one value, or, in brackets, a list of its own, as in {@code 1, (2, 3)}. */
    private Expr listItem(int start, int end, Expected item, Expected list) {
        int[] span = trim(start, end);
        if (span[0] < span[1] && input.charAt(span[0]) == '(' && Scanner.parenEnd(input, span[0], span[1]) == span[1]) {
            Expr inner = expression(span[0], span[1], list);
            if (inner != null) {
                return inner;
            }
        }
        return expression(start, end, item);
    }

    /** {@code [start, end)} as one verified literal of an open type, if it looks like a list but a parser takes it whole. */
    private Expr wholeLiteral(int start, int end, Expected expected) {
        String text = input.substring(start, end);
        String lower = text.toLowerCase(Locale.ROOT);
        if (!Literals.isWords(text) || (!lower.contains(" and ") && !lower.contains(",") && !lower.contains(" or "))) {
            return null;
        }
        Iterable<TypeInfo> types = expected.anything() ? registry.types() : expected.types();
        for (TypeInfo type : types) {
            if (type.openLiterals() && registry.isLiteral(type, text)) {
                return new Expr.Unresolved(start, end, text, true);
            }
        }
        return null;
    }

    /** {@code [start, end)} as words an expected open type's parser takes, or {@code null}. */
    private Expr verifiedLiteral(int start, int end, Expected expected) {
        String text = input.substring(start, end);
        if (expected.anything() || !Literals.isWords(text)) {
            return null;
        }
        for (TypeInfo type : expected.types()) {
            if (type.openLiterals() && registry.isLiteral(type, text)) {
                return new Expr.Unresolved(start, end, text, true);
            }
        }
        return null;
    }

    private Expr unresolved(int start, int end, Expected expected) {
        String text = input.substring(start, end);
        if (!Literals.isWords(text)) {
            return null;
        }
        if (context.isItemAlias(text) && (expected.anything() || expected.types().stream().anyMatch(type ->
                type.id().equals("itemtype") || registry.isConvertible("itemtype", type.id())))) {
            return new Expr.Unresolved(start, end, text, true);
        }
        boolean open = expected.anything() || expected.types().stream()
                .anyMatch(type -> type.openLiterals() && registry.mightBeLiteral(type, text))
                || registry.isConvertibleLiteral(text, id -> expected.types().stream()
                        .anyMatch(type -> !type.id().equals(id) && registry.isConvertible(id, type.id())));
        if (!open) {
            return null;
        }
        boolean verified = expected.types().stream().anyMatch(type -> type.openLiterals() && registry.isLiteral(type, text));
        return new Expr.Unresolved(start, end, text, verified);
    }

    private static boolean isChangeable(Expr expr) {
        return switch (expr) {
            case Expr.Literal literal -> false;
            // Text is an expression in Skript, not a literal: 'assert "x" is set' parses.
            case Expr.Text text -> true;
            case Expr.Unresolved unresolved -> false;
            case Expr.ListOf list -> list.items().stream().allMatch(LineParser::isChangeable);
            default -> true;
        };
    }

    // ---------------------------------------------------------------- helpers

    private Object memoized(int start, int end, Object what, java.util.function.Supplier<Object> compute) {
        Map<Key, Object> memo = strictOuter ? strictMemo : this.memo;
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
