package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Condition;
import co.xenastudios.neoskript.core.expression.VariableExpression;

import java.util.List;
import java.util.Map;

/**
 * A {@code filter %~objects% to match [any|all]:} section (Skript 2.10). Each element of a list
 * variable is tested against the body conditions with the element bound to the {@code input} local;
 * elements that do not match are removed from the list in place. In {@code all} mode (the default)
 * every condition must hold; in {@code any} mode at least one must.
 *
 * <p>The source must be a list variable ({@code {list::*}}) — the only modifiable multi-value target
 * this engine can rewrite entry-by-entry.
 */
public final class FilterSection implements Statement {

    private final VariableExpression list;
    private final List<Condition> conditions;
    private final boolean matchAny;

    public FilterSection(VariableExpression list, List<Condition> conditions, boolean matchAny) {
        this.list = list;
        this.conditions = List.copyOf(conditions);
        this.matchAny = matchAny;
    }

    @Override
    public void run(TriggerContext ctx) {
        String prefix = list.listPrefix(ctx);
        Map<String, Object> children = list.isLocal() ? ctx.listLocal(prefix) : ctx.listGlobal(prefix);
        Object previousInput = ctx.getLocal("input");
        try {
            for (Map.Entry<String, Object> entry : children.entrySet()) {
                ctx.setLocal("input", entry.getValue());
                if (!keep(ctx)) {
                    if (list.isLocal()) {
                        ctx.setLocal(entry.getKey(), null);
                    } else {
                        ctx.setGlobal(entry.getKey(), null);
                    }
                }
            }
        } finally {
            ctx.setLocal("input", previousInput);
        }
    }

    /** Whether the current element (bound to {@code input}) survives the filter. */
    private boolean keep(TriggerContext ctx) {
        if (conditions.isEmpty()) {
            return true;
        }
        if (matchAny) {
            for (Condition condition : conditions) {
                if (condition.check(ctx)) {
                    return true;
                }
            }
            return false;
        }
        for (Condition condition : conditions) {
            if (!condition.check(ctx)) {
                return false;
            }
        }
        return true;
    }
}
