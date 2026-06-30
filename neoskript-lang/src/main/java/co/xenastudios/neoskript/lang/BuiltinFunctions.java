package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.ScriptFunction;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

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

        registry.register("vector", (args, ctx) ->
                new Vector(coord(args, 0), coord(args, 1), coord(args, 2)));
        registry.register("location", (args, ctx) -> {
            World world = args.size() > 3 && args.get(3) instanceof World w ? w : null;
            return new Location(world, coord(args, 0), coord(args, 1), coord(args, 2));
        });
        registry.register("item", (args, ctx) -> {
            if (args.isEmpty()) {
                return null;
            }
            String itemName = String.valueOf(args.get(0));
            String mapped = co.xenastudios.neoskript.core.alias.AliasRegistry.resolve(itemName);
            Material material = Material.matchMaterial(mapped != null ? mapped : itemName);
            if (material == null) {
                return null;
            }
            int amount = args.size() > 1 && args.get(1) instanceof Number n ? n.intValue() : 1;
            return new ItemStack(material, Math.max(1, amount));
        });
        registry.register("world", (args, ctx) -> args.isEmpty() ? null : Bukkit.getWorld(String.valueOf(args.get(0))));
        registry.register("player", (args, ctx) ->
                args.isEmpty() ? null : Bukkit.getPlayerExact(String.valueOf(args.get(0))));
        registry.register("date", (args, ctx) -> {
            Double year = number(args, 0);
            Double month = number(args, 1);
            Double day = number(args, 2);
            if (year == null || month == null || day == null) {
                return null;
            }
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.clear();
            calendar.set((int) (double) year, (int) (double) month - 1, (int) (double) day,
                    intArg(args, 3), intArg(args, 4), intArg(args, 5));
            return calendar.getTime();
        });
        registry.register("rgb", (args, ctx) -> {
            Double r = number(args, 0);
            Double g = number(args, 1);
            Double b = number(args, 2);
            if (r == null || g == null || b == null) {
                return null;
            }
            return Color.fromRGB(clampColor(r), clampColor(g), clampColor(b));
        });

        // --- additional Skript-parity math/utility functions ---
        registry.register("ceiling", unary(Math::ceil)); // alias of ceil
        registry.register("clamp", (args, ctx) -> {
            Double v = number(args, 0);
            Double lo = number(args, 1);
            Double hi = number(args, 2);
            return (v == null || lo == null || hi == null) ? null : Math.max(lo, Math.min(hi, v));
        });
        registry.register("root", (args, ctx) -> {
            Double n = number(args, 0);
            Double degree = number(args, 1);
            return (n == null || degree == null || degree == 0) ? null : Math.pow(n, 1.0 / degree);
        });
        registry.register("factorial", (args, ctx) -> {
            Double n = number(args, 0);
            if (n == null || n < 0) {
                return null;
            }
            double result = 1;
            for (int i = 2; i <= (int) (double) n; i++) {
                result *= i;
            }
            return result;
        });
        registry.register("combinations", (args, ctx) -> binomial(number(args, 0), number(args, 1), false));
        registry.register("permutations", (args, ctx) -> binomial(number(args, 0), number(args, 1), true));
        registry.register("isNaN", (args, ctx) -> {
            Double n = number(args, 0);
            return n == null || n.isNaN();
        });
        registry.register("mean", (args, ctx) -> {
            double sum = 0;
            int count = 0;
            for (Object arg : args) {
                Double v = toNumber(arg);
                if (v != null) {
                    sum += v;
                    count++;
                }
            }
            return count == 0 ? null : sum / count;
        });
        registry.register("median", (args, ctx) -> {
            java.util.List<Double> nums = new java.util.ArrayList<>();
            for (Object arg : args) {
                Double v = toNumber(arg);
                if (v != null) {
                    nums.add(v);
                }
            }
            if (nums.isEmpty()) {
                return null;
            }
            java.util.Collections.sort(nums);
            int mid = nums.size() / 2;
            return nums.size() % 2 == 1 ? nums.get(mid) : (nums.get(mid - 1) + nums.get(mid)) / 2.0;
        });
        registry.register("fromBase", (args, ctx) -> {
            Double base = number(args, 1);
            if (args.isEmpty() || base == null) {
                return null;
            }
            try {
                return (double) Long.parseLong(String.valueOf(args.get(0)).trim(), (int) (double) base);
            } catch (NumberFormatException e) {
                return null;
            }
        });
        registry.register("toBase", (args, ctx) -> {
            Double n = number(args, 0);
            Double base = number(args, 1);
            return (n == null || base == null) ? null : Long.toString((long) (double) n, (int) (double) base);
        });
        registry.register("concat", (args, ctx) -> {
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                sb.append(arg == null ? "" : String.valueOf(arg));
            }
            return sb.toString();
        });
        registry.register("case_equals", (args, ctx) -> {
            if (args.size() < 2) {
                return true;
            }
            String first = String.valueOf(args.get(0));
            for (int i = 1; i < args.size(); i++) {
                if (!first.equals(String.valueOf(args.get(i)))) {
                    return false;
                }
            }
            return true;
        });
        registry.register("formatNumber", (args, ctx) -> {
            Double n = number(args, 0);
            if (n == null) {
                return null;
            }
            String pattern = args.size() > 1 ? String.valueOf(args.get(1)) : "#,##0.##";
            java.text.DecimalFormat format = new java.text.DecimalFormat(
                    pattern, new java.text.DecimalFormatSymbols(java.util.Locale.US));
            return format.format(n);
        });
        registry.register("file_separator", (args, ctx) -> java.io.File.separator);
        registry.register("line_separator", (args, ctx) -> System.lineSeparator());
    }

    /** Binomial helper: permutations (ordered) or combinations of n things taken k at a time. */
    private static Double binomial(Double n, Double k, boolean ordered) {
        if (n == null || k == null || n < 0 || k < 0 || k > n) {
            return null;
        }
        long nn = (long) (double) n;
        long kk = (long) (double) k;
        double numerator = 1;
        for (long i = nn; i > nn - kk; i--) {
            numerator *= i;
        }
        if (ordered) {
            return numerator;
        }
        double denominator = 1;
        for (long i = 2; i <= kk; i++) {
            denominator *= i;
        }
        return numerator / denominator;
    }

    /** Clamps a colour channel to the 0–255 range. */
    private static int clampColor(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private static double coord(List<Object> args, int index) {
        Double value = number(args, index);
        return value == null ? 0 : value;
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

    /** A positional numeric argument as an int, defaulting to 0 when absent. */
    private static int intArg(List<Object> args, int index) {
        Double value = number(args, index);
        return value == null ? 0 : (int) (double) value;
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
