/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.List;

/** Skript's built-in math functions. */
final class MathFunctions {

    private MathFunctions() {}

    static void register(Registrar registrar) {
        List<Parameter> n = List.of(Parameter.of("n", "number"));
        registrar.function("floor", n, "integer", false, args -> (long) Math.floor(number(args[0])));
        registrar.function("ceil", n, "integer", false, args -> (long) Math.ceil(number(args[0])));
        registrar.function("ceiling", n, "integer", false, args -> (long) Math.ceil(number(args[0])));
        registrar.function("abs", n, "number", false, args -> args[0] instanceof Long l ? (Object) Math.abs(l)
                : (Object) Math.abs(number(args[0])));
        registrar.function("sqrt", n, "number", false, args -> Math.sqrt(number(args[0])));
        registrar.function("round", List.of(Parameter.of("n", "number"), new Parameter("d", "number", false, true)),
                "number", false, args -> {
                    double value = number(args[0]);
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
