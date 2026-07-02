package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.VariableExpression;

import java.util.List;

/**
 * A section expression assignment: {@code set {var} to <value>:} with an indented body. The base
 * value is evaluated and stashed in the reserved section-value local ({@link
 * VariableExpression#SECTION_VALUE}); the body then runs and may mutate it through {@code created ...}
 * expressions (which alias that same local); finally the resulting value is stored in the target.
 *
 * <p>Mirrors Skript's section expressions, where a section builds a value and its body customises it
 * (e.g. {@code set {_p} to a potion effect of speed for 10 minutes:} then {@code hide the icon of the
 * created potion effect}). The reserved local is saved and restored around the body so nested
 * sections do not clobber each other.
 */
public final class SectionValueStatement implements Statement {

    private final VariableExpression target;
    private final Expression<?> base;
    private final List<Statement> body;

    public SectionValueStatement(VariableExpression target, Expression<?> base, List<Statement> body) {
        this.target = target;
        this.base = base;
        this.body = List.copyOf(body);
    }

    @Override
    public void run(TriggerContext ctx) {
        Object previous = ctx.getLocal(VariableExpression.SECTION_VALUE);
        ctx.setLocal(VariableExpression.SECTION_VALUE, base.getSingle(ctx));
        for (Statement statement : body) {
            statement.run(ctx);
        }
        Object result = ctx.getLocal(VariableExpression.SECTION_VALUE);
        ctx.setLocal(VariableExpression.SECTION_VALUE, previous);
        target.set(ctx, result);
    }
}
