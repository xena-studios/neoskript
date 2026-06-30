package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.core.runtime.Renderer;

import java.util.Objects;

/**
 * Type-aware value comparison shared by the built-in conditions. Numbers compare by their double
 * value; values of the same {@link Comparable} type compare by natural order; a string compared
 * against a non-string value matches that value's display form (so {@code if gamemode is "survival"}
 * works). This is the practical form of the comparator graph from the plan.
 */
public final class Comparisons {

    private Comparisons() {
    }

    /** @return {@code true} if the two values are equal (numerically for numbers, textually mixed) */
    public static boolean equal(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        if (Objects.equals(a, b)) {
            return true;
        }
        // A string compared to a typed (non-number) value matches that value's display form, so e.g.
        // `gamemode is "survival"` holds. Numbers stay distinct from numeric strings (Skript semantics).
        if (a instanceof String s && b != null && !(b instanceof String) && !(b instanceof Number)) {
            return s.equalsIgnoreCase(text(b));
        }
        if (b instanceof String s && a != null && !(a instanceof String) && !(a instanceof Number)) {
            return s.equalsIgnoreCase(text(a));
        }
        return false;
    }

    /**
     * Compares two values.
     *
     * @return negative, zero, or positive like {@link Integer#compare}, or {@code null} if the values
     *         are not orderable (neither numeric nor a shared {@link Comparable} type)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Integer compare(Object a, Object b) {
        Double x = toNumber(a);
        Double y = toNumber(b);
        if (x != null && y != null) {
            return Double.compare(x, y);
        }
        if (a instanceof Comparable && b != null && a.getClass() == b.getClass()) {
            return Integer.signum(((Comparable) a).compareTo(b));
        }
        return null;
    }

    /** Renders a value's textual form for mixed comparison (display form, else {@code toString}). */
    private static String text(Object value) {
        String display = Renderer.toDisplay(value);
        return display != null ? display : String.valueOf(value);
    }

    private static Double toNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
