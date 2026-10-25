/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.KeyedValues;
import java.util.List;

/** Skript's built-in math functions. */
final class MathFunctions {

    private MathFunctions() {}

    static void register(Registrar registrar) {
        List<Parameter> n = List.of(Parameter.of("n", "number"));
        // Allowing for floating point error, as Skript does: 100 lots of 0.1 floor to 10.
        registrar.function("floor", n, "integer", false, args -> Logic.round(number(args[0]), -1));
        registrar.function("ceil", n, "integer", false, args -> Logic.round(number(args[0]), 1));
        registrar.function("ceiling", n, "integer", false, args -> Logic.round(number(args[0]), 1));
        registrar.function("abs", n, "number", false, args -> args[0] instanceof Long l ? (Object) Math.abs(l)
                : (Object) Math.abs(number(args[0])));
        registrar.function("sqrt", n, "number", false, args -> Math.sqrt(number(args[0])));
        registrar.function("round", List.of(Parameter.of("n", "number"), new Parameter("d", "number", false, true)),
                "number", false, args -> {
                    double value = number(args[0]);
                    double placesValue = args[1] == null ? 0 : number(args[1]);
                    if (!Double.isFinite(placesValue) || Math.abs(placesValue) > Integer.MAX_VALUE) {
                        // No such place to round to, as in Skript.
                        return Double.NaN;
                    }
                    int places = args[1] == null ? 0 : (int) number(args[1]);
                    if (Double.isNaN(value) || Double.isInfinite(value)) {
                        return value;
                    }
                    // Halves round away from zero, as in Skript: round(-11.535, 2) is -11.54.
                    java.math.BigDecimal rounded = java.math.BigDecimal.valueOf(value);
                    if (places < 0) {
                        // To a whole number first, then to tens or hundreds, as Skript does: 1044.5 to 1045 to 1050.
                        rounded = rounded.setScale(0, java.math.RoundingMode.HALF_UP);
                    }
                    rounded = rounded.setScale(places, java.math.RoundingMode.HALF_UP);
                    return places <= 0 ? (Object) rounded.longValue() : (Object) rounded.doubleValue();
                });
        registrar.function("mod", List.of(Parameter.of("d", "number"), Parameter.of("m", "number")), "number", false,
                args -> {
                    if (args[0] instanceof Long d && args[1] instanceof Long m && m != 0) {
                        return Math.floorMod(d, m);
                    }
                    double m = number(args[1]);
                    double result = number(args[0]) % m;
                    return result < 0 ? result + m : result;
                });
        List<Parameter> ns = List.of(new Parameter("ns", "number", true, false));
        registrar.function("min", ns, "number", false, args -> extreme((Object[]) args[0], -1));
        registrar.function("max", ns, "number", false, args -> extreme((Object[]) args[0], 1));
        // Trigonometry in degrees, as Skript's.
        registrar.function("sin", n, "number", false, args -> Math.sin(Math.toRadians(number(args[0]))));
        registrar.function("cos", n, "number", false, args -> Math.cos(Math.toRadians(number(args[0]))));
        registrar.function("tan", n, "number", false, args -> Math.tan(Math.toRadians(number(args[0]))));
        registrar.function("asin", n, "number", false, args -> Math.toDegrees(Math.asin(number(args[0]))));
        registrar.function("acos", n, "number", false, args -> Math.toDegrees(Math.acos(number(args[0]))));
        registrar.function("atan", n, "number", false, args -> Math.toDegrees(Math.atan(number(args[0]))));
        registrar.function("atan2", List.of(Parameter.of("x", "number"), Parameter.of("y", "number")), "number", false,
                args -> Math.toDegrees(Math.atan2(number(args[1]), number(args[0]))));
        registrar.function("ln", n, "number", false, args -> Math.log(number(args[0])));
        registrar.function("log", List.of(Parameter.of("n", "number"), Parameter.defaulting("base", "number", () -> 10L)),
                "number", false, args -> Math.log10(number(args[0])) / Math.log10(number(args[1])));
        registrar.function("exp", n, "number", false, args -> Math.exp(number(args[0])));
        registrar.function("sum", ns, "number", false, args -> {
            Object[] values = (Object[]) args[0];
            if (values.length == 0) {
                return null;
            }
            double sum = number(values[0]);
            for (int i = 1; i < values.length; i++) {
                sum += number(values[i]);
            }
            return sum;
        });
        registrar.function("product", ns, "number", false, args -> {
            Object[] values = (Object[]) args[0];
            if (values.length == 0) {
                return null;
            }
            double product = number(values[0]);
            for (int i = 1; i < values.length; i++) {
                product *= number(values[i]);
            }
            return product;
        });
        // The experience needed to reach a level from none.
        registrar.function("calcExperience", List.of(Parameter.of("level", "integer")), "integer", false, args -> {
            long level = (long) number(args[0]);
            if (level <= 0) {
                return 0L;
            } else if (level <= 15) {
                return level * level + 6 * level;
            } else if (level <= 30) {
                return (long) (int) (2.5 * level * level - 40.5 * level + 360);
            }
            return (long) (int) (4.5 * level * level - 162.5 * level + 2220);
        });
        registrar.function("isNaN", n, "boolean", false, args -> args[0] instanceof Number x && Double.isNaN(x.doubleValue()));
        registrar.function("root", List.of(Parameter.of("n", "number"), Parameter.of("x", "number")), "number", false,
                args -> {
                    double root = number(args[0]);
                    return root == 0 || Double.isNaN(root) ? null : Math.pow(number(args[1]), 1 / root);
                });
        registrar.function("factorial", n, "number", false, args -> {
            double value = number(args[0]);
            return Double.isNaN(value) || value < 0 ? null : (Object) descending((long) value, 1);
        });
        List<Parameter> choose = List.of(Parameter.of("options", "number"), Parameter.of("selected", "number"));
        registrar.function("permutations", choose, "number", false, args -> {
            long options = (long) number(args[0]);
            long selected = (long) number(args[1]);
            return selected < 0 || selected > options ? null : (Object) descending(options, options - selected + 1);
        });
        registrar.function("combinations", choose, "number", false, args -> {
            long options = (long) number(args[0]);
            long selected = (long) number(args[1]);
            if (selected < 0 || selected > options) {
                return null;
            }
            double arrangements = descending(options, options - selected + 1);
            // Too many arrangements to count is infinitely many combinations, as in Skript: combinations(500, 500).
            return Double.isInfinite(arrangements) ? arrangements : arrangements / descending(selected, 1);
        });
        registrar.function("mean", ns, "number", false, args -> {
            double sum = 0;
            int count = 0;
            for (Object value : (Object[]) args[0]) {
                double x = number(value);
                if (!Double.isFinite(x)) {
                    return null;
                }
                sum += x;
                count++;
            }
            return count == 0 ? null : sum / count;
        });
        registrar.function("median", ns, "number", false, args -> {
            double[] sorted = java.util.Arrays.stream((Object[]) args[0]).mapToDouble(MathFunctions::number).sorted().toArray();
            if (sorted.length == 0 || java.util.Arrays.stream(sorted).anyMatch(Double::isNaN)) {
                return null;
            }
            int middle = sorted.length / 2;
            return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
        });
        registrar.function("clamp", List.of(new Parameter("values", "number", true, false), Parameter.of("min", "number"),
                Parameter.of("max", "number")), "number", true, args -> {
                    if (!(args[1] instanceof Number a) || !(args[2] instanceof Number b)) {
                        return null;
                    }
                    double low = Math.min(a.doubleValue(), b.doubleValue());
                    double high = Math.max(a.doubleValue(), b.doubleValue());
                    boolean nan = Double.isNaN(a.doubleValue()) || Double.isNaN(b.doubleValue());
                    java.util.function.UnaryOperator<Object> clamp = value -> {
                        if (!(value instanceof Number x)) {
                            return null;
                        }
                        double d = x.doubleValue();
                        if (nan || Double.isNaN(d)) {
                            return Double.NaN;
                        }
                        if (d < low) {
                            return a.doubleValue() <= b.doubleValue() ? a : b;
                        }
                        if (d > high) {
                            return a.doubleValue() <= b.doubleValue() ? b : a;
                        }
                        return x;
                    };
                    // Keyed numbers keep their indices: 'keyed clamp(keyed {_n::*}, 0, 2)'.
                    if (args[0] instanceof KeyedValues keyed) {
                        Object[] clamped = new Object[keyed.values().length];
                        for (int i = 0; i < clamped.length; i++) {
                            clamped[i] = clamp.apply(keyed.values()[i]);
                        }
                        return new KeyedValues(clamped, keyed.keys());
                    }
                    Object[] values = (Object[]) args[0];
                    Object[] clamped = new Object[values.length];
                    for (int i = 0; i < values.length; i++) {
                        clamped[i] = clamp.apply(values[i]);
                    }
                    return clamped;
                });
        registrar.function("toBase", List.of(Parameter.of("n", "number"), Parameter.of("base", "number")), "string", false,
                args -> {
                    if (!(args[0] instanceof Number value) || !(args[1] instanceof Number base)
                            || base.intValue() < Character.MIN_RADIX || base.intValue() > Character.MAX_RADIX) {
                        return null;
                    }
                    return Long.toString(value.longValue(), base.intValue());
                });
        registrar.function("fromBase", List.of(Parameter.of("string", "string"), Parameter.of("base", "number")), "integer",
                false, args -> {
                    if (!(args[0] instanceof String text) || !(args[1] instanceof Number base)
                            || base.intValue() < Character.MIN_RADIX || base.intValue() > Character.MAX_RADIX) {
                        return null;
                    }
                    try {
                        return Long.parseLong(text, base.intValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                });
        registrar.function("formatNumber", List.of(Parameter.of("number", "number"),
                new Parameter("format", "string", false, true)), "string", false, args -> {
                    java.text.DecimalFormat format;
                    try {
                        format = new java.text.DecimalFormat(args[1] instanceof String pattern ? pattern : "#,##0.##",
                                java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ENGLISH));
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                    return args[0] instanceof Number number ? format.format(number) : null;
                });
    }

    /**
     * The product of the whole numbers from {@code high} down to {@code low}, in decimals, as Skript works it out
     * (so the results round the same); it stops once it's infinite.
     */
    private static double descending(long high, long low) {
        double product = 1;
        for (long i = high; i >= Math.max(low, 1) && !Double.isInfinite(product); i--) {
            product *= i;
        }
        return product;
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static Object extreme(Object[] values, int sign) {
        Object best = null;
        for (Object value : values) {
            if (value instanceof Number number && (best == null
                    || Double.compare(number.doubleValue(), ((Number) best).doubleValue()) * sign > 0)) {
                best = number;
            }
        }
        return best;
    }
}
