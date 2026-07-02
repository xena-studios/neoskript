package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Condition;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.ArithmeticExpression;
import co.xenastudios.neoskript.core.expression.ComputedExpression;
import co.xenastudios.neoskript.core.expression.ComputedListExpression;
import co.xenastudios.neoskript.core.expression.FunctionCallExpression;
import co.xenastudios.neoskript.core.expression.ListExpression;
import co.xenastudios.neoskript.core.expression.NumberLiteral;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.expression.VariableString;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry.ConditionEntry;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry.ExpressionEntry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses an expression substring into an {@link Expression}.
 *
 * <p>Grammar (highest to lowest binding): primaries (literals, variables, function calls,
 * parenthesised groups, registered patterns) → {@code * /} → {@code + -}. Arithmetic operators are
 * left-associative. Quoted strings, {@code {...}} variables, and {@code (...)} groups are treated as
 * atomic when locating top-level operators.
 */
public final class ExpressionParser {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern FUNCTION_CALL = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*)\\)");

    private final DefaultSyntaxRegistry registry;
    private final FunctionRegistry functions;

    public ExpressionParser(DefaultSyntaxRegistry registry, FunctionRegistry functions) {
        this.registry = registry;
        this.functions = functions;
    }

    /**
     * Parses a single expression.
     *
     * @param input the expression text
     * @return the parsed expression
     * @throws ParseException if the text is not a recognised expression
     */
    public Expression<?> parse(String input) {
        String s = input.trim();
        if (s.isEmpty()) {
            throw new ParseException("Empty expression");
        }
        // Meta / list expressions (map/filter/ternary/whether) wrap a nested expression in `[...]` or
        // use `if`/`else`; detect them before operator and list splitting so operators inside the
        // nested part (e.g. the `*` in `[input * 2]`) are not mistaken for top-level operators.
        Expression<?> meta = parseMetaExpression(s);
        if (meta != null) {
            return meta;
        }
        List<String> items = splitList(s);
        if (items.size() == 1) {
            return parseAdditive(s);
        }
        // A registered expression whose own pattern contains a `,` or `and` (e.g. `vector %o%, %o%,
        // %o%` or `distance between A and B`) would otherwise be mis-split on that separator. When the
        // whole string matches such an expression, prefer the single match over list-splitting — the
        // same order Skript uses (try the complete expression before treating commas as a list).
        if (matchesRegisteredExpression(s)) {
            return parseAdditive(s);
        }
        List<Expression<?>> elements = new ArrayList<>(items.size());
        for (String item : items) {
            elements.add(parseAdditive(item.trim()));
        }
        return new ListExpression(elements);
    }

    /** @return true if a registered expression pattern matches the whole input. */
    private boolean matchesRegisteredExpression(String s) {
        for (ExpressionEntry entry : registry.expressionCandidates(s)) {
            if (entry.pattern().match(s).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** Splits an expression into list elements on top-level commas and {@code and}/{@code or}. */
    private static List<String> splitList(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inQuotes = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (inQuotes) {
                // skip
            } else if (c == '(' || c == '{') {
                depth++;
            } else if (c == ')' || c == '}') {
                depth--;
            } else if (depth == 0) {
                if (c == ',') {
                    parts.add(s.substring(start, i));
                    start = i + 1;
                } else if (s.regionMatches(true, i, " and ", 0, 5)) {
                    parts.add(s.substring(start, i));
                    start = i + 5;
                    i += 4;
                } else if (s.regionMatches(true, i, " or ", 0, 4)) {
                    parts.add(s.substring(start, i));
                    start = i + 4;
                    i += 3;
                }
            }
        }
        parts.add(s.substring(start));
        parts.removeIf(part -> part.isBlank());
        return parts;
    }

    private Expression<?> parseAdditive(String s) {
        int op = findTopLevelOperator(s, "+-");
        if (op >= 0) {
            Expression<?> left = parseAdditive(s.substring(0, op).trim());
            Expression<?> right = parseMultiplicative(s.substring(op + 1).trim());
            return arithmetic(left, s.charAt(op), right);
        }
        return parseMultiplicative(s);
    }

    private Expression<?> parseMultiplicative(String s) {
        int op = findTopLevelOperator(s, "*/");
        if (op >= 0) {
            Expression<?> left = parseMultiplicative(s.substring(0, op).trim());
            Expression<?> right = parsePower(s.substring(op + 1).trim());
            return arithmetic(left, s.charAt(op), right);
        }
        return parsePower(s);
    }

    /**
     * Parses exponentiation ({@code ^}), which binds tighter than {@code * /} and is right-associative
     * ({@code 2 ^ 3 ^ 2} is {@code 2 ^ (3 ^ 2)}), so we split at the <em>leftmost</em> top-level caret.
     */
    private Expression<?> parsePower(String s) {
        int op = findFirstTopLevelOperator(s, '^');
        if (op >= 0) {
            Expression<?> left = parsePrimary(s.substring(0, op).trim());
            Expression<?> right = parsePower(s.substring(op + 1).trim());
            return arithmetic(left, '^', right);
        }
        return parsePrimary(s);
    }

    /** Finds the leftmost top-level (depth-0, outside quotes) occurrence of {@code op}, or -1. */
    private static int findFirstTopLevelOperator(String s, char op) {
        int depth = 0;
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (inQuotes) {
                // skip
            } else if (c == '(' || c == '{') {
                depth++;
            } else if (c == ')' || c == '}') {
                depth--;
            } else if (depth == 0 && c == op) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Builds an arithmetic node, folding it to a constant when both operands are number literals
     * (the optimizer's constant-folding pass, applied during parsing).
     */
    private static Expression<?> arithmetic(Expression<?> left, char operator, Expression<?> right) {
        ArithmeticExpression node = new ArithmeticExpression(left, operator, right);
        if (left instanceof NumberLiteral && right instanceof NumberLiteral) {
            Double folded = node.getSingle(null); // literals ignore the context
            if (folded != null) {
                return new NumberLiteral(folded);
            }
        }
        return node;
    }

    private Expression<?> parsePrimary(String input) {
        String s = input.trim();
        if (s.isEmpty()) {
            throw new ParseException("Empty expression");
        }

        if (s.charAt(0) == '(' && isWrapped(s)) {
            return parseAdditive(s.substring(1, s.length() - 1).trim());
        }

        // Only treat as a string literal if the whole input is one quoted string (the first quote's
        // partner is the last character) — otherwise e.g. `"a" split at ","` is two strings.
        if (s.charAt(0) == '"') {
            int close = s.indexOf('"', 1);
            if (close == s.length() - 1) {
                return VariableString.parse(s.substring(1, close), this);
            }
        }

        if (NUMBER.matcher(s).matches()) {
            return new NumberLiteral(Double.parseDouble(s));
        }

        // Likewise, only a variable if the first '{' is closed exactly at the end.
        if (s.charAt(0) == '{' && matchingBraceAtEnd(s)) {
            return VariableExpression.parse(s.substring(1, s.length() - 1), this);
        }

        var functionMatch = FUNCTION_CALL.matcher(s);
        if (functionMatch.matches()) {
            String name = functionMatch.group(1);
            List<Expression<?>> args = parseArgumentList(functionMatch.group(2));
            return new FunctionCallExpression(name, args, functions);
        }

        for (ExpressionEntry entry : registry.expressionCandidates(s)) {
            Optional<List<String>> match = entry.pattern().match(s);
            if (match.isPresent()) {
                try {
                    return entry.factory().create(new SimpleArguments(
                            parseArguments(match.get(), entry.pattern().argTypes())));
                } catch (ParseException ignored) {
                    // Overlapping pattern whose arguments don't parse — try the next candidate.
                }
            }
        }

        // Boolean literals.
        if (s.equalsIgnoreCase("true")) {
            return new co.xenastudios.neoskript.core.expression.ComputedExpression(ctx -> Boolean.TRUE);
        }
        if (s.equalsIgnoreCase("false")) {
            return new co.xenastudios.neoskript.core.expression.ComputedExpression(ctx -> Boolean.FALSE);
        }

        // Typed loop variable: inside `loop all players:`, `loop-player` (or loop-<any type>) is the
        // current element, an alias for loop-value. loop-value/number/index are registered explicitly,
        // so they never reach here; this catches the typed forms Skript scripts actually use.
        if (s.regionMatches(true, 0, "loop-", 0, 5) && s.length() > 5
                && s.substring(5).chars().allMatch(c -> Character.isLetter(c) || c == '-')) {
            return new co.xenastudios.neoskript.core.expression.NamedLocalExpression("loop-value");
        }

        // Type literal: "hard" -> a difficulty, "survival" -> a gamemode, "creeper" -> an entity type.
        // Only reached once no registered expression matched, so it never shadows real syntax.
        co.xenastudios.neoskript.core.type.TypeRegistry typeRegistry =
                co.xenastudios.neoskript.core.runtime.Renderer.typeRegistry();
        if (typeRegistry != null) {
            Object literal = typeRegistry.parseLiteral(s);
            if (literal != null) {
                return new co.xenastudios.neoskript.core.expression.ComputedExpression(ctx -> literal);
            }
        }

        // Pattern nicety: tolerate a leading article ("the player", "a diamond", "an apple") by
        // stripping it and retrying. Only reached as a fallback once nothing else matched, so it
        // never shadows an expression that legitimately begins with one of these words.
        String withoutArticle = stripLeadingArticle(s);
        if (withoutArticle != null) {
            return parsePrimary(withoutArticle);
        }

        throw new ParseException("Don't understand the expression '" + input + "'");
    }

    /** Returns {@code s} without a leading {@code the}/{@code a}/{@code an} word, or null if none. */
    private static String stripLeadingArticle(String s) {
        for (String article : new String[] {"the ", "an ", "a "}) {
            if (s.length() > article.length() && s.regionMatches(true, 0, article, 0, article.length())) {
                return s.substring(article.length()).trim();
            }
        }
        return null;
    }

    /**
     * Parses each captured argument substring into an expression. {@code null} captures (absent
     * optional slots) stay {@code null}.
     *
     * @param captures the captured argument strings
     * @return the parsed argument expressions
     */
    public List<Expression<?>> parseArguments(List<String> captures) {
        return parseArguments(captures, null);
    }

    /**
     * Parses captured argument substrings into expressions, resolving type-reference slots specially.
     * A slot whose declared type is {@code classinfo} does not parse its capture as an expression;
     * instead the capture names a type ({@code "number"}, {@code "a player"}, {@code "integers"}),
     * which is resolved to a {@link co.xenastudios.neoskript.api.type.Type} and supplied as a constant
     * expression yielding that type. If the name resolves to no known type the argument fails to parse
     * (so the enclosing pattern falls through), mirroring Skript's {@code %classinfo%} handling.
     *
     * @param captures the captured argument strings
     * @param argTypes the declared base type name of each slot (may be {@code null})
     * @return the parsed argument expressions
     */
    public List<Expression<?>> parseArguments(List<String> captures, List<String> argTypes) {
        List<Expression<?>> args = new ArrayList<>(captures.size());
        for (int i = 0; i < captures.size(); i++) {
            String capture = captures.get(i);
            String slotType = argTypes != null && i < argTypes.size() ? argTypes.get(i) : null;
            if (capture == null) {
                args.add(null);
            } else if ("classinfo".equals(slotType)) {
                args.add(classInfoArgument(capture));
            } else {
                args.add(parse(capture));
            }
        }
        return args;
    }

    /** Resolves a {@code %classinfo%} capture (a type name) to a constant expression yielding its type. */
    private Expression<?> classInfoArgument(String capture) {
        co.xenastudios.neoskript.core.type.TypeRegistry types =
                co.xenastudios.neoskript.core.runtime.Renderer.typeRegistry();
        if (types == null) {
            throw new ParseException("No type registry available to resolve '" + capture + "'");
        }
        String name = capture.trim().toLowerCase(java.util.Locale.ROOT);
        String withoutArticle = stripLeadingArticle(name);
        if (withoutArticle != null) {
            name = withoutArticle;
        }
        co.xenastudios.neoskript.api.type.Type<?> type = types.byCodeName(name);
        if (type == null && name.endsWith("s")) {
            type = types.byCodeName(name.substring(0, name.length() - 1));
        }
        if (type == null) {
            throw new ParseException("There is no type named '" + capture + "'");
        }
        co.xenastudios.neoskript.api.type.Type<?> resolved = type;
        return new ComputedExpression(ctx -> resolved);
    }

    private static final Pattern META_MAP = Pattern.compile(
            "(?i)^(.+?)\\s+(?:transformed|mapped)\\s+(?:using|with)\\s*\\[(.+)]$");
    private static final Pattern META_FILTER = Pattern.compile(
            "(?i)^(.+?)\\s+(?:where|that match(?:es)?)\\s*\\[(.+)]$");
    private static final Pattern META_TERNARY = Pattern.compile(
            "(?i)^(.+?)\\s+if\\s+(.+?),?\\s+(?:otherwise|else)\\s+(.+)$");
    private static final Pattern META_REDUCE = Pattern.compile(
            "(?i)^(.+?)\\s+(?:reduced|folded)\\s+(?:to|with|by)\\s*\\[(.+)]$");
    private static final Pattern META_EXCEPT = Pattern.compile(
            "(?i)^(.+?)\\s+(?:except|excluding|not including)\\s+(.+)$");
    private static final Pattern META_INDICES = Pattern.compile(
            "(?i)^(?:all\\s+(?:of\\s+)?(?:the\\s+)?|the\\s+)?(?:indexes|indices)\\s+of\\s+(\\{.+})$");

    /**
     * Parses a list/meta expression that evaluates a nested condition or expression per element:
     * {@code %objects% transformed using [<expr>]}, {@code %objects% where [<cond>]},
     * {@code %a% if <cond> else %b%}, or {@code whether <cond>}. The current element is bound to the
     * {@code input} local while the nested part is evaluated. Returns {@code null} if none apply.
     */
    private Expression<?> parseMetaExpression(String s) {
        if (s.regionMatches(true, 0, "whether ", 0, 8)) {
            Condition condition = parseConditionText(s.substring(8).trim());
            return new ComputedExpression(condition::check);
        }
        Matcher map = META_MAP.matcher(s);
        if (map.matches()) {
            Expression<?> source = parse(map.group(1).trim());
            Expression<?> mapper = parse(map.group(2).trim());
            return new ComputedListExpression(ctx -> perElement(ctx, source.getAll(ctx),
                    () -> mapper.getSingle(ctx), true));
        }
        Matcher filter = META_FILTER.matcher(s);
        if (filter.matches()) {
            Expression<?> source = parse(filter.group(1).trim());
            Condition condition = parseConditionText(filter.group(2).trim());
            return new ComputedListExpression(ctx -> {
                Object[] items = source.getAll(ctx);
                Object previous = ctx.getLocal("input");
                List<Object> kept = new ArrayList<>();
                try {
                    for (Object item : items) {
                        ctx.setLocal("input", item);
                        if (condition.check(ctx)) {
                            kept.add(item);
                        }
                    }
                } finally {
                    ctx.setLocal("input", previous);
                }
                return kept.toArray();
            });
        }
        Matcher indices = META_INDICES.matcher(s);
        if (indices.matches()) {
            try {
                if (parse(indices.group(1).trim()) instanceof VariableExpression list && list.isList()) {
                    return new ComputedListExpression(list::listKeys);
                }
            } catch (ParseException ignored) {
                // The capture isn't a bare list variable (e.g. `indices of %value% in %list%`); fall
                // through so the registered index-of-value expression can handle it.
            }
        }
        Matcher reduce = META_REDUCE.matcher(s);
        if (reduce.matches()) {
            Expression<?> source = parse(reduce.group(1).trim());
            Expression<?> reducer = parse(reduce.group(2).trim());
            return new ComputedExpression(ctx -> {
                Object[] items = source.getAll(ctx);
                if (items.length == 0) {
                    return null;
                }
                Object previousInput = ctx.getLocal("input");
                Object previousAccumulator = ctx.getLocal("accumulator");
                try {
                    Object accumulator = items[0];
                    for (int i = 1; i < items.length; i++) {
                        ctx.setLocal("accumulator", accumulator);
                        ctx.setLocal("input", items[i]);
                        accumulator = reducer.getSingle(ctx);
                    }
                    return accumulator;
                } finally {
                    ctx.setLocal("input", previousInput);
                    ctx.setLocal("accumulator", previousAccumulator);
                }
            });
        }
        Matcher except = META_EXCEPT.matcher(s);
        if (except.matches()) {
            try {
                Expression<?> whole = parse(except.group(1).trim());
                Expression<?> removed = parse(except.group(2).trim());
                return new ComputedListExpression(ctx -> {
                    java.util.Set<Object> exclude =
                            new java.util.HashSet<>(java.util.Arrays.asList(removed.getAll(ctx)));
                    return java.util.Arrays.stream(whole.getAll(ctx)).filter(x -> !exclude.contains(x)).toArray();
                });
            } catch (ParseException ignored) {
                // not an `except` expression
            }
        }
        Matcher ternary = META_TERNARY.matcher(s);
        if (ternary.matches()) {
            try {
                Condition condition = parseConditionText(ternary.group(2).trim());
                Expression<?> ifTrue = parse(ternary.group(1).trim());
                Expression<?> ifFalse = parse(ternary.group(3).trim());
                return new ComputedExpression(ctx -> condition.check(ctx)
                        ? ifTrue.getSingle(ctx) : ifFalse.getSingle(ctx));
            } catch (ParseException ignored) {
                // not actually a ternary (the middle wasn't a condition) — fall through
            }
        }
        return null;
    }

    /** Evaluates {@code op} for each item with the item bound to {@code input}, collecting results. */
    private static Object[] perElement(co.xenastudios.neoskript.api.runtime.TriggerContext ctx,
                                       Object[] items, java.util.function.Supplier<Object> op, boolean skipNull) {
        Object previous = ctx.getLocal("input");
        List<Object> out = new ArrayList<>();
        try {
            for (Object item : items) {
                ctx.setLocal("input", item);
                Object result = op.get();
                if (!skipNull || result != null) {
                    out.add(result);
                }
            }
        } finally {
            ctx.setLocal("input", previous);
        }
        return out.toArray();
    }

    /**
     * Parses a condition from text (mirrors the script parser). Used by meta expressions/effects that
     * embed a nested {@code [<condition>]}.
     */
    public Condition parseConditionText(String content) {
        ParseException lastError = null;
        for (ConditionEntry entry : registry.conditionCandidates(content)) {
            Optional<List<String>> match = entry.pattern().match(content);
            if (match.isPresent()) {
                try {
                    return entry.factory().create(new SimpleArguments(
                            parseArguments(match.get(), entry.pattern().argTypes())));
                } catch (ParseException e) {
                    lastError = e;
                }
            }
        }
        throw lastError != null ? lastError
                : new ParseException("Don't understand the condition '" + content + "'");
    }

    private List<Expression<?>> parseArgumentList(String argsText) {
        List<Expression<?>> args = new ArrayList<>();
        for (String part : splitTopLevel(argsText, ',')) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                args.add(parse(trimmed));
            }
        }
        return args;
    }

    /** Finds the rightmost top-level (depth-0, outside quotes) operator from {@code ops}, or -1. */
    private static int findTopLevelOperator(String s, String ops) {
        int depth = 0;
        boolean inQuotes = false;
        int result = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (inQuotes) {
                // skip
            } else if (c == '(' || c == '{') {
                depth++;
            } else if (c == ')' || c == '}') {
                depth--;
            } else if (depth == 0 && ops.indexOf(c) >= 0) {
                if ((c == '-' || c == '+') && isUnaryPosition(s, i)) {
                    continue;
                }
                // A '-' glued directly after a letter/underscore is part of an identifier
                // (loop-number, event-block, arg-1), not subtraction.
                if (c == '-' && i > 0 && (Character.isLetter(s.charAt(i - 1)) || s.charAt(i - 1) == '_')) {
                    continue;
                }
                result = i;
            }
        }
        return result;
    }

    private static boolean isUnaryPosition(String s, int index) {
        for (int i = index - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return "+-*/(".indexOf(c) >= 0;
        }
        return true;
    }

    /** True if the first {@code &#123;} is matched by a {@code &#125;} at the very end of the string. */
    private static boolean matchingBraceAtEnd(String s) {
        if (s.charAt(s.length() - 1) != '}') {
            return false;
        }
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i == s.length() - 1;
                }
            }
        }
        return false;
    }

    /** True if the whole string is one parenthesised group, e.g. {@code (a + b)}. */
    private static boolean isWrapped(String s) {
        if (s.length() < 2 || s.charAt(0) != '(' || s.charAt(s.length() - 1) != ')') {
            return false;
        }
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && i != s.length() - 1) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    private static List<String> splitTopLevel(String s, char separator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inQuotes = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (inQuotes) {
                // skip
            } else if (c == '(' || c == '{') {
                depth++;
            } else if (c == ')' || c == '}') {
                depth--;
            } else if (c == separator && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }
}
