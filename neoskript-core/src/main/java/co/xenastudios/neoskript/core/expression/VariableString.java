package co.xenastudios.neoskript.core.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.parser.ExpressionParser;
import co.xenastudios.neoskript.core.parser.ParseException;
import co.xenastudios.neoskript.core.runtime.Renderer;

import java.util.ArrayList;
import java.util.List;

/**
 * A string literal that may interpolate expressions: {@code "Welcome, %player%!"}. The {@code %...%}
 * segments are parsed into expressions at load time and rendered via {@link Renderer} at runtime.
 *
 * <p>{@code %%} is a literal percent sign. Phase 1 renders each interpolated expression's single
 * value; multi-value interpolation joins arrive in Phase 2.
 */
public final class VariableString implements Expression<String> {

    /** Parts are either {@link String} literals or {@link Expression} interpolations. */
    private final List<Object> parts;

    private VariableString(List<Object> parts) {
        this.parts = parts;
    }

    /**
     * Parses the inner content of a quoted string (the text between the quotes).
     *
     * @param content the inner text, e.g. {@code Welcome, %player%!}
     * @param parser  the expression parser used to parse interpolated segments
     * @return the variable string
     */
    public static VariableString parse(String content, ExpressionParser parser) {
        List<Object> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        int n = content.length();

        while (i < n) {
            char c = content.charAt(i);
            if (c == '%') {
                int end = content.indexOf('%', i + 1);
                if (end < 0) {
                    throw new ParseException("Unbalanced '%' in string: \"" + content + "\"");
                }
                String inner = content.substring(i + 1, end);
                if (inner.isEmpty()) {
                    literal.append('%'); // "%%" -> literal percent
                } else {
                    if (literal.length() > 0) {
                        parts.add(literal.toString());
                        literal.setLength(0);
                    }
                    parts.add(parser.parse(inner));
                }
                i = end + 1;
            } else {
                literal.append(c);
                i++;
            }
        }
        if (literal.length() > 0) {
            parts.add(literal.toString());
        }
        return new VariableString(parts);
    }

    @Override
    public String[] getAll(TriggerContext ctx) {
        return new String[]{getSingle(ctx)};
    }

    @Override
    public String getSingle(TriggerContext ctx) {
        if (parts.size() == 1 && parts.get(0) instanceof String only) {
            return only;
        }
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof String literal) {
                sb.append(literal);
            } else {
                Expression<?> expression = (Expression<?>) part;
                sb.append(Renderer.toDisplay(expression.getSingle(ctx)));
            }
        }
        return sb.toString();
    }

    @Override
    public Class<String> returnType() {
        return String.class;
    }

    @Override
    public boolean isSingle() {
        return true;
    }
}
