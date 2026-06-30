package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.ScriptFunction;

import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * Registers NeoSkript's built-in (native) functions — the mathematical and utility functions callable
 * from scripts, e.g. {@code abs(x)}, {@code sqrt(x)}, {@code max(a, b, c)}.
 */
public final class BuiltinFunctions {

    private BuiltinFunctions() {
    }

    /**
     * Registers all built-in functions into the given registry.
     *
     * @param registry the function registry
     */
    public static void registerAll(FunctionRegistry registry) {
        registry.register("abs", unary(Math::abs));
        registry.register("round", unary(d -> (double) Math.round(d)));
        registry.register("floor", unary(Math::floor));
        registry.register("ceil", unary(Math::ceil));
        registry.register("sqrt", unary(Math::sqrt));
        registry.register("exp", unary(Math::exp));
        registry.register("ln", unary(Math::log));
        registry.register("log", unary(Math::log10));
        registry.register("sin", unary(Math::sin));
        registry.register("cos", unary(Math::cos));
        registry.register("tan", unary(Math::tan));
        registry.register("asin", unary(Math::asin));
        registry.register("acos", unary(Math::acos));
        registry.register("atan", unary(Math::atan));

        registry.register("mod", binary((a, b) -> b == 0 ? 0 : ((a % b) + b) % b));
        registry.register("atan2", binary(Math::atan2));

        registry.register("min", reduce(Math::min));
        registry.register("max", reduce(Math::max));
        registry.register("sum", reduce(Double::sum));
        registry.register("product", reduce((a, b) -> a * b));
    }

    private static ScriptFunction unary(DoubleUnaryOperator op) {
        return (args, ctx) -> {
            Double a = number(args, 0);
            return a == null ? null : op.applyAsDouble(a);
        };
    }

    private static ScriptFunction binary(DoubleBinaryOperator op) {
        return (args, ctx) -> {
            Double a = number(args, 0);
            Double b = number(args, 1);
            return (a == null || b == null) ? null : op.applyAsDouble(a, b);
        };
    }

    private static ScriptFunction reduce(DoubleBinaryOperator op) {
        return (args, ctx) -> {
            Double acc = null;
            for (Object arg : args) {
                Double v = toNumber(arg);
                if (v != null) {
                    acc = (acc == null) ? v : op.applyAsDouble(acc, v);
                }
            }
            return acc;
        };
    }

    private static Double number(List<Object> args, int index) {
        return index < args.size() ? toNumber(args.get(index)) : null;
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
