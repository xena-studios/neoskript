package co.xenastudios.neoskript.api.type;

import java.util.Optional;

/**
 * Describes a value type known to NeoSkript — the replacement for Skript's {@code ClassInfo}.
 *
 * <p>A {@code Type} knows how to parse itself from user input, render itself for display, and
 * (later phases) serialize itself for variable persistence. Types are registered into the type
 * registry and referenced by {@link #codeName()} in syntax patterns (e.g. {@code %player%}).
 *
 * @param <T> the Java type represented
 */
public interface Type<T> {

    /**
     * @return the Java class represented by this type
     */
    Class<T> typeClass();

    /**
     * @return the lowercase code name used in patterns and messages (e.g. {@code "player"})
     */
    String codeName();

    /**
     * Attempts to parse a value from raw user input.
     *
     * @param input the raw text
     * @return the parsed value, or {@link Optional#empty()} if it does not match
     */
    Optional<T> parse(String input);

    /**
     * Renders a value for display to users.
     *
     * @param value the value to render
     * @return a human-readable string
     */
    String toDisplayString(T value);
}
