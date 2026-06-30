package co.xenastudios.neoskript.api.syntax;

import java.util.List;

/**
 * The parsed arguments captured from a syntax pattern, handed to a factory when it builds a
 * {@link SyntaxElement} occurrence.
 *
 * <p>For a pattern such as {@code send %string% [to %player%]}, index {@code 0} is the message
 * expression and index {@code 1} is the optional target — which is {@code null} when the optional
 * group was not present in the script.
 */
public interface Arguments {

    /**
     * @return the number of argument slots in the matched pattern (including optional ones)
     */
    int count();

    /**
     * @param index the zero-based argument position
     * @return the parsed expression at {@code index}, or {@code null} if an optional argument was
     *         not supplied or the index is out of range
     */
    Expression<?> get(int index);

    /**
     * @param index the zero-based argument position
     * @return {@code true} if an expression was supplied at {@code index}
     */
    boolean isPresent(int index);

    /**
     * @return all argument slots in order; absent optional arguments are {@code null} entries
     */
    List<Expression<?>> all();
}
