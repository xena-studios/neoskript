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
