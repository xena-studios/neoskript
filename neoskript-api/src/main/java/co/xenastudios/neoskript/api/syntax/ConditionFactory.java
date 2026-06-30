package co.xenastudios.neoskript.api.syntax;

/**
 * Builds a {@link Condition} occurrence from the arguments parsed out of its pattern.
 */
@FunctionalInterface
public interface ConditionFactory {

    /**
     * @param arguments the parsed pattern arguments
     * @return a new condition bound to those arguments
     */
    Condition create(Arguments arguments);
}
