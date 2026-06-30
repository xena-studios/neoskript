package co.xenastudios.neoskript.core.registry;

import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.api.syntax.Condition;
import co.xenastudios.neoskript.api.syntax.Effect;
import co.xenastudios.neoskript.api.syntax.Expression;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The default in-memory {@link SyntaxRegistry}.
 *
 * <p>Phase 0 stores registrations in flat lists. Phase 1 replaces this with the indexed
 * pattern-dispatch structure (bucketing candidates by leading literal token) that gives NeoSkript
 * its fast, near-linear parsing.
 */
public final class DefaultSyntaxRegistry implements SyntaxRegistry {

    /**
     * One registered syntax element: its pattern and the factory that builds occurrences.
     *
     * @param pattern    the syntax pattern
     * @param returnType the produced type for expressions, otherwise {@code null}
     * @param factory    produces a fresh syntax-element instance
     */
    public record Entry(String pattern, Class<?> returnType, Supplier<?> factory) {
    }

    private final List<Entry> effects = new ArrayList<>();
    private final List<Entry> conditions = new ArrayList<>();
    private final List<Entry> expressions = new ArrayList<>();

    @Override
    public void registerEffect(String pattern, Supplier<? extends Effect> factory) {
        effects.add(new Entry(pattern, null, factory));
    }

    @Override
    public void registerCondition(String pattern, Supplier<? extends Condition> factory) {
        conditions.add(new Entry(pattern, null, factory));
    }

    @Override
    public <T> void registerExpression(String pattern, Class<T> returnType, Supplier<? extends Expression<T>> factory) {
        expressions.add(new Entry(pattern, returnType, factory));
    }

    /** @return the registered effects, in registration order */
    public List<Entry> effects() {
        return List.copyOf(effects);
    }

    /** @return the registered conditions, in registration order */
    public List<Entry> conditions() {
        return List.copyOf(conditions);
    }

    /** @return the registered expressions, in registration order */
    public List<Entry> expressions() {
        return List.copyOf(expressions);
    }

    @Override
    public int size() {
        return effects.size() + conditions.size() + expressions.size();
    }
}
