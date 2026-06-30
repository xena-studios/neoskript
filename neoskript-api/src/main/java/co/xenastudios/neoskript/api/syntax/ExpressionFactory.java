package co.xenastudios.neoskript.api.syntax;

/**
 * Builds an {@link Expression} occurrence from the arguments parsed out of its pattern.
 *
 * @param <T> the element type the produced expression yields
 */
@FunctionalInterface
public interface ExpressionFactory<T> {

    /**
     * @param arguments the parsed pattern arguments
     * @return a new expression bound to those arguments
     */
    Expression<T> create(Arguments arguments);
}
