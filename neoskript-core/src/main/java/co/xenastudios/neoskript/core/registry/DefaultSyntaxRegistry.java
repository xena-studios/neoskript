package co.xenastudios.neoskript.core.registry;

import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.api.syntax.ConditionFactory;
import co.xenastudios.neoskript.api.syntax.EffectFactory;
import co.xenastudios.neoskript.api.syntax.ExpressionFactory;
import co.xenastudios.neoskript.core.parser.pattern.PatternCompiler;
import co.xenastudios.neoskript.core.parser.pattern.SyntaxPattern;

import java.util.ArrayList;
import java.util.List;

/**
 * The default in-memory {@link SyntaxRegistry}. Patterns are compiled to {@link SyntaxPattern} on
 * registration so the parser can match against them directly.
 *
 * <p>Phase 1 stores registrations in flat lists, matched in registration order. Phase 3 replaces
 * this with the indexed pattern-dispatch structure (bucketing candidates by leading literal token)
 * for fast, near-linear parsing.
 */
public final class DefaultSyntaxRegistry implements SyntaxRegistry {

    /** A registered effect: its compiled pattern and the factory that builds occurrences. */
    public record EffectEntry(SyntaxPattern pattern, EffectFactory factory) {
    }

    /** A registered condition: its compiled pattern and the factory that builds occurrences. */
    public record ConditionEntry(SyntaxPattern pattern, ConditionFactory factory) {
    }

    /** A registered expression: its compiled pattern, produced type, and factory. */
    public record ExpressionEntry(SyntaxPattern pattern, Class<?> returnType, ExpressionFactory<?> factory) {
    }

    private final List<EffectEntry> effects = new ArrayList<>();
    private final List<ConditionEntry> conditions = new ArrayList<>();
    private final List<ExpressionEntry> expressions = new ArrayList<>();

    @Override
    public void registerEffect(String pattern, EffectFactory factory) {
        effects.add(new EffectEntry(PatternCompiler.compile(pattern), factory));
    }

    @Override
    public void registerCondition(String pattern, ConditionFactory factory) {
        conditions.add(new ConditionEntry(PatternCompiler.compile(pattern), factory));
    }

    @Override
    public <T> void registerExpression(String pattern, Class<T> returnType, ExpressionFactory<T> factory) {
        expressions.add(new ExpressionEntry(PatternCompiler.compile(pattern), returnType, factory));
    }

    /** @return the registered effects, in registration order */
    public List<EffectEntry> effects() {
        return List.copyOf(effects);
    }

    /** @return the registered conditions, in registration order */
    public List<ConditionEntry> conditions() {
        return List.copyOf(conditions);
    }

    /** @return the registered expressions, in registration order */
    public List<ExpressionEntry> expressions() {
        return List.copyOf(expressions);
    }

    @Override
    public int size() {
        return effects.size() + conditions.size() + expressions.size();
    }
}
