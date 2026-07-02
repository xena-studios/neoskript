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

    /** @return the installed type registry, or {@code null} if none is set */
    public static TypeRegistry typeRegistry() {
        return typeRegistry;
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

    /**
     * Renders a multi-value expression result the way Skript joins lists in strings: single values as
     * themselves, and several as {@code "a, b and c"} (comma-separated with {@code " and "} before the
     * last). An empty result renders as {@code "<none>"}.
     *
     * @param values the values to join
     * @return the joined display text
     */
    public static String toDisplayList(Object[] values) {
        if (values.length == 0) {
            return "<none>";
        }
        if (values.length == 1) {
            return toDisplay(values[0]);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(i == values.length - 1 ? " and " : ", ");
            }
            sb.append(toDisplay(values[i]));
        }
        return sb.toString();
    }

    private static String formatNumber(Number number) {
        double d = number.doubleValue();
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }
}
