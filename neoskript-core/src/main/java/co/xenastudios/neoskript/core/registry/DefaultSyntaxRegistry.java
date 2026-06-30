package co.xenastudios.neoskript.core.registry;

import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.api.syntax.ConditionFactory;
import co.xenastudios.neoskript.api.syntax.EffectFactory;
import co.xenastudios.neoskript.api.syntax.ExpressionFactory;
import co.xenastudios.neoskript.core.parser.pattern.PatternCompiler;
import co.xenastudios.neoskript.core.parser.pattern.SyntaxPattern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The default in-memory {@link SyntaxRegistry}. Patterns are compiled to {@link SyntaxPattern} on
 * registration and indexed by their leading literal word, so that at parse time a line is only
 * matched against candidate syntaxes whose first word can match (plus those that begin with an
 * argument). This gives the near-linear parsing described in the plan, replacing Phase 1's
 * match-everything scan.
 *
 * <p>Candidates are returned in registration order so the first match still wins.
 */
public final class DefaultSyntaxRegistry implements SyntaxRegistry {

    /** A registered effect: registration order, compiled pattern, and the factory. */
    public record EffectEntry(int order, SyntaxPattern pattern, EffectFactory factory) {
    }

    /** A registered condition. */
    public record ConditionEntry(int order, SyntaxPattern pattern, ConditionFactory factory) {
    }

    /** A registered expression, with its produced type. */
    public record ExpressionEntry(int order, SyntaxPattern pattern, Class<?> returnType, ExpressionFactory<?> factory) {
    }

    private int counter;

    private final Index<EffectEntry> effects = new Index<>();
    private final Index<ConditionEntry> conditions = new Index<>();
    private final Index<ExpressionEntry> expressions = new Index<>();

    @Override
    public void registerEffect(String pattern, EffectFactory factory) {
        SyntaxPattern compiled = PatternCompiler.compile(pattern);
        effects.add(new EffectEntry(counter++, compiled, factory), compiled.firstLiteral());
    }

    @Override
    public void registerCondition(String pattern, ConditionFactory factory) {
        SyntaxPattern compiled = PatternCompiler.compile(pattern);
        conditions.add(new ConditionEntry(counter++, compiled, factory), compiled.firstLiteral());
    }

    @Override
    public <T> void registerExpression(String pattern, Class<T> returnType, ExpressionFactory<T> factory) {
        SyntaxPattern compiled = PatternCompiler.compile(pattern);
        expressions.add(new ExpressionEntry(counter++, compiled, returnType, factory), compiled.firstLiteral());
    }

    /** @return effect candidates for {@code input}, in registration order */
    public List<EffectEntry> effectCandidates(String input) {
        return effects.candidates(firstWord(input));
    }

    /** @return condition candidates for {@code input}, in registration order */
    public List<ConditionEntry> conditionCandidates(String input) {
        return conditions.candidates(firstWord(input));
    }

    /** @return expression candidates for {@code input}, in registration order */
    public List<ExpressionEntry> expressionCandidates(String input) {
        return expressions.candidates(firstWord(input));
    }

    /** @return all registered effects, in registration order (for documentation/inspection) */
    public List<EffectEntry> allEffects() {
        return List.copyOf(effects.all);
    }

    /** @return all registered conditions, in registration order */
    public List<ConditionEntry> allConditions() {
        return List.copyOf(conditions.all);
    }

    /** @return all registered expressions, in registration order */
    public List<ExpressionEntry> allExpressions() {
        return List.copyOf(expressions.all);
    }

    @Override
    public int size() {
        return counter;
    }

    /** Extracts the lowercased leading word of an input line, or {@code ""} if it starts otherwise. */
    static String firstWord(String input) {
        String s = input.strip();
        int i = 0;
        while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) {
            i++;
        }
        return s.substring(0, i).toLowerCase(Locale.ROOT);
    }

    /**
     * Buckets entries by their leading literal word, with a separate list for entries that begin with
     * an argument (and therefore match regardless of the first word).
     *
     * @param <E> the entry type
     */
    private static final class Index<E> {
        private final Map<String, List<E>> byWord = new HashMap<>();
        private final List<E> wildcard = new ArrayList<>();
        private final List<E> all = new ArrayList<>();
        private final Comparator<E> byOrder;
        private boolean hasWildcard;

        Index() {
            this.byOrder = Comparator.comparingInt(Index::orderOf);
        }

        void add(E entry, String firstLiteral) {
            all.add(entry);
            if (firstLiteral == null) {
                wildcard.add(entry);
                hasWildcard = true;
            } else {
                byWord.computeIfAbsent(firstLiteral, k -> new ArrayList<>()).add(entry);
            }
        }

        List<E> candidates(String firstWord) {
            List<E> matchingWord = byWord.get(firstWord);
            if (!hasWildcard) {
                return matchingWord == null ? List.of() : matchingWord;
            }
            if (matchingWord == null) {
                return wildcard;
            }
            List<E> merged = new ArrayList<>(matchingWord.size() + wildcard.size());
            merged.addAll(matchingWord);
            merged.addAll(wildcard);
            merged.sort(byOrder);
            return merged;
        }

        private static int orderOf(Object entry) {
            return switch (entry) {
                case EffectEntry e -> e.order();
                case ConditionEntry c -> c.order();
                case ExpressionEntry x -> x.order();
                default -> 0;
            };
        }
    }
}
