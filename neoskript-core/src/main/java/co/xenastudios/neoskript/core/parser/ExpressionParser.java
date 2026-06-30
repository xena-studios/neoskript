package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.NumberLiteral;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.expression.VariableString;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry.ExpressionEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses an expression substring into an {@link Expression}.
 *
 * <p>Resolution order: quoted strings, numeric literals, variables ({@code {...}}), then registered
 * expression patterns. Arguments captured from a registered pattern are parsed recursively.
 */
public final class ExpressionParser {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    private final DefaultSyntaxRegistry registry;

    public ExpressionParser(DefaultSyntaxRegistry registry) {
        this.registry = registry;
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

        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return VariableString.parse(s.substring(1, s.length() - 1), this);
        }

        if (NUMBER.matcher(s).matches()) {
            return new NumberLiteral(Double.parseDouble(s));
        }

        if (s.length() >= 2 && s.charAt(0) == '{' && s.charAt(s.length() - 1) == '}') {
            return VariableExpression.of(s.substring(1, s.length() - 1));
        }

        for (ExpressionEntry entry : registry.expressions()) {
            Optional<List<String>> match = entry.pattern().match(s);
            if (match.isPresent()) {
                return entry.factory().create(new SimpleArguments(parseArguments(match.get())));
            }
        }

        throw new ParseException("Don't understand the expression '" + input + "'");
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
}
