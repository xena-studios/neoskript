package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.ArithmeticExpression;
import co.xenastudios.neoskript.core.expression.FunctionCallExpression;
import co.xenastudios.neoskript.core.expression.ListExpression;
import co.xenastudios.neoskript.core.expression.NumberLiteral;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.expression.VariableString;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry.ExpressionEntry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        List<String> items = splitList(s);
        if (items.size() == 1) {
            return parseAdditive(s);
        }
        List<Expression<?>> elements = new ArrayList<>(items.size());
        for (String item : items) {
            elements.add(parseAdditive(item.trim()));
        }
        return new ListExpression(elements);
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
            Expression<?> right = parsePrimary(s.substring(op + 1).trim());
            return arithmetic(left, s.charAt(op), right);
        }
        return parsePrimary(s);
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
                return entry.factory().create(new SimpleArguments(parseArguments(match.get())));
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
        List<Expression<?>> args = new ArrayList<>(captures.size());
        for (String capture : captures) {
            args.add(capture == null ? null : parse(capture));
        }
        return args;
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
