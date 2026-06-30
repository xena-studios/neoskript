package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.core.type.TypeRegistry;

/**
 * Renders runtime values to their display text, used by string interpolation and message effects.
 *
 * <p>Display is delegated to the registered {@link TypeRegistry} (so each {@code Type} controls how
 * its values render — e.g. players render as their name); core keeps type-agnostic fallbacks for
 * numbers and strings so rendering still works before/without a registry (e.g. in unit tests).
 */
public final class Renderer {

    private static volatile TypeRegistry typeRegistry;

    private Renderer() {
    }

    /**
     * Installs the type registry used for display. Set once at startup.
     *
     * @param registry the registry, or {@code null} to clear it
     */
    public static void setTypeRegistry(TypeRegistry registry) {
        typeRegistry = registry;
    }

    /**
     * Renders a value for display.
     *
     * @param value the value, possibly {@code null}
     * @return the display text
     */
    public static String toDisplay(Object value) {
        if (value == null) {
            return "<none>";
        }
        TypeRegistry registry = typeRegistry;
        if (registry != null) {
            String rendered = registry.display(value);
            if (rendered != null) {
                return rendered;
            }
        }
        if (value instanceof Number number) {
            return formatNumber(number);
        }
        if (value instanceof CharSequence text) {
            return text.toString();
        }
        return String.valueOf(value);
    }

    private static String formatNumber(Number number) {
        double d = number.doubleValue();
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }
}
