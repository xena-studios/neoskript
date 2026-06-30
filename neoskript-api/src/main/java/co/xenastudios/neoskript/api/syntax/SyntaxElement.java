package co.xenastudios.neoskript.api.syntax;

/**
 * Marker for every parsed, executable piece of a script: {@link Expression}, {@link Effect},
 * and {@link Condition}.
 *
 * <p>A syntax element instance represents one <em>occurrence</em> in a script (with its parsed
 * arguments already bound), produced by the factory registered for its pattern. Instances are
 * created once at load time and reused for the lifetime of the loaded script.
 */
public interface SyntaxElement {
}
