package co.xenastudios.neoskript.core.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;

/**
 * A variable reference: {@code {name}} (global) or {@code {_name}} (trigger-local). Settable, so the
 * {@code set} effect can assign into it.
 *
 * <p>Phase 1 supports scalar variables. List variables ({@code {list::*}}) and persistence arrive in
 * Phase 2.
 */
public final class VariableExpression implements Expression<Object> {

    private final boolean local;
    private final String name;

    private VariableExpression(boolean local, String name) {
        this.local = local;
        this.name = name;
    }

    /**
     * Parses a variable from its inner name (the text between {@code {} and {@code }}).
     *
     * @param rawName the inner name, e.g. {@code "_count"} or {@code "score"}
     * @return the variable expression
     */
    public static VariableExpression of(String rawName) {
        String trimmed = rawName.trim();
        if (trimmed.startsWith("_")) {
            return new VariableExpression(true, trimmed.substring(1));
        }
        return new VariableExpression(false, trimmed);
    }

    /** @return {@code true} if this is a trigger-local variable */
    public boolean isLocal() {
        return local;
    }

    /** @return the variable name, without delimiters or the local underscore */
    public String name() {
        return name;
    }

    /**
     * Assigns a value to this variable.
     *
     * @param ctx   the execution context
     * @param value the value to store ({@code null} clears it)
     */
    public void set(TriggerContext ctx, Object value) {
        if (local) {
            ctx.setLocal(name, value);
        } else {
            ctx.setGlobal(name, value);
        }
    }

    @Override
    public Object[] getAll(TriggerContext ctx) {
        Object value = getSingle(ctx);
        return value == null ? new Object[0] : new Object[]{value};
    }

    @Override
    public Object getSingle(TriggerContext ctx) {
        return local ? ctx.getLocal(name) : ctx.getGlobal(name);
    }

    @Override
    public Class<Object> returnType() {
        return Object.class;
    }

    @Override
    public boolean isSingle() {
        return true;
    }
}
