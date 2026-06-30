package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Condition;

import java.util.List;

/**
 * An {@code if [...] else} section: runs the {@code then} branch when the condition holds, otherwise
 * the optional {@code else} branch ({@code else if} is represented as an {@code IfSection} nested in
 * an else branch).
 */
public final class IfSection implements Statement {

    private final Condition condition;
    private final List<Statement> thenBranch;
    private final List<Statement> elseBranch;

    public IfSection(Condition condition, List<Statement> thenBranch, List<Statement> elseBranch) {
        this.condition = condition;
        this.thenBranch = List.copyOf(thenBranch);
        this.elseBranch = elseBranch == null ? null : List.copyOf(elseBranch);
    }

    @Override
    public void run(TriggerContext ctx) {
        if (condition.check(ctx)) {
            runAll(thenBranch, ctx);
        } else if (elseBranch != null) {
            runAll(elseBranch, ctx);
        }
    }

    static void runAll(List<Statement> statements, TriggerContext ctx) {
        for (Statement statement : statements) {
            statement.run(ctx);
        }
    }
}
