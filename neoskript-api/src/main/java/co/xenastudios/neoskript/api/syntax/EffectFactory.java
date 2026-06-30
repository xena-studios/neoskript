package co.xenastudios.neoskript.api.syntax;

/**
 * Builds an {@link Effect} occurrence from the arguments parsed out of its pattern.
 */
@FunctionalInterface
public interface EffectFactory {

    /**
     * @param arguments the parsed pattern arguments
     * @return a new effect bound to those arguments
     */
    Effect create(Arguments arguments);
}
