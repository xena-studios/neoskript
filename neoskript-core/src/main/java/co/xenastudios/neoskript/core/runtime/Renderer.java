package co.xenastudios.neoskript.core.runtime;

import java.util.function.Function;

/**
 * Renders runtime values to their display text, used by string interpolation and message effects.
 *
 * <p>Core handles the type-agnostic cases (null, numbers, strings) without depending on Bukkit. The
 * platform layer installs a {@linkplain #setPlatformRenderer(Function) fallback} that knows how to
 * render server types (players, etc.) by their friendly name. Phase 2 replaces this with per-type
 * {@code toDisplayString} lookups via the type registry.
 */
public final class Renderer {

    private static volatile Function<Object, String> platformRenderer;

    private Renderer() {
    }

    /**
     * Installs a renderer for platform-specific types. Returns {@code null} to fall back to
     * {@link String#valueOf(Object)}.
     *
     * @param renderer the platform renderer, or {@code null} to clear it
     */
    public static void setPlatformRenderer(Function<Object, String> renderer) {
        platformRenderer = renderer;
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
        if (value instanceof Number number) {
            return formatNumber(number);
        }
        if (value instanceof CharSequence text) {
            return text.toString();
        }
        Function<Object, String> renderer = platformRenderer;
        if (renderer != null) {
            String rendered = renderer.apply(value);
            if (rendered != null) {
                return rendered;
            }
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
