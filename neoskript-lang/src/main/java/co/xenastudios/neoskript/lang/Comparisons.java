package co.xenastudios.neoskript.lang;

import java.util.Objects;

/**
 * Value comparison helpers shared by the built-in conditions. Numbers compare by their double value;
 * everything else falls back to {@link Objects#equals}. Phase 3 generalises this into the pluggable
 * comparator graph described in the plan.
 */
public final class Comparisons {

    private Comparisons() {
    }

    /** @return {@code true} if the two values are equal (numerically for numbers) */
    public static boolean equal(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        return Objects.equals(a, b);
    }

    /**
     * Numerically compares two values.
     *
     * @return negative, zero, or positive like {@link Integer#compare}, or {@code null} if either
     *         value is not numeric (and therefore not orderable here)
     */
    public static Integer compare(Object a, Object b) {
        Double x = toNumber(a);
        Double y = toNumber(b);
        if (x == null || y == null) {
            return null;
        }
        return Double.compare(x, y);
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
