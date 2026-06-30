package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Condition;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.ComputedExpression;
import co.xenastudios.neoskript.core.expression.NamedLocalExpression;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.parser.ParseException;
import co.xenastudios.neoskript.core.runtime.Renderer;
import co.xenastudios.neoskript.core.runtime.ReturnSignal;
import co.xenastudios.neoskript.core.runtime.StopSignal;
import co.xenastudios.neoskript.lang.expression.EventPlayerExpression;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Registers NeoSkript's built-in syntax: expressions, conditions, and effects.
 *
 * <p>Phase 2 ships a broad, representative slice of Skript's language — control-flow-friendly
 * conditions, list/number-aware variable effects, message effects, and common expressions. It is not
 * yet the full Skript catalogue; coverage continues to grow against the conformance corpus.
 */
public final class BuiltinModule {

    private BuiltinModule() {
    }

    /**
     * Registers all built-in syntax into the given registry and installs the value renderer.
     *
     * @param registry the target registry
     */
    public static void registerAll(SyntaxRegistry registry) {
        Renderer.setPlatformRenderer(value -> value instanceof CommandSender sender ? sender.getName() : null);
        registerExpressions(registry);
        registerConditions(registry);
        registerEffects(registry);
    }

    private static void registerExpressions(SyntaxRegistry registry) {
        registry.registerExpression("player", Player.class, arguments -> new EventPlayerExpression());
        registry.registerExpression("console", Object.class,
                arguments -> new ComputedExpression(ctx -> Bukkit.getConsoleSender()));

        registry.registerExpression("loop-value", Object.class, arguments -> new NamedLocalExpression("loop-value"));
        registry.registerExpression("loop-number", Object.class, arguments -> new NamedLocalExpression("loop-index"));
        registry.registerExpression("loop-index", Object.class, arguments -> new NamedLocalExpression("loop-index"));

        registry.registerExpression("name of %player%", Object.class, arguments -> nameOf(arguments.get(0)));
        registry.registerExpression("%player%'s name", Object.class, arguments -> nameOf(arguments.get(0)));

        registry.registerExpression("(size|amount|number) of %objects%", Object.class, arguments -> {
            Expression<?> source = arguments.get(0);
            return new ComputedExpression(ctx -> (double) source.getAll(ctx).length);
        });

        registry.registerExpression("random number (from|between) %number% (to|and) %number%", Object.class,
                arguments -> random(arguments.get(0), arguments.get(1), false));
        registry.registerExpression("random integer (from|between) %number% (to|and) %number%", Object.class,
                arguments -> random(arguments.get(0), arguments.get(1), true));
    }

    private static ComputedExpression random(Expression<?> minExpr, Expression<?> maxExpr, boolean integer) {
        return new ComputedExpression(ctx -> {
            double lo = orZero(toNumber(minExpr.getSingle(ctx)));
            double hi = orZero(toNumber(maxExpr.getSingle(ctx)));
            if (lo > hi) {
                double tmp = lo;
                lo = hi;
                hi = tmp;
            }
            if (integer) {
                long low = Math.round(lo);
                long high = Math.round(hi);
                return (double) (high <= low ? low : ThreadLocalRandom.current().nextLong(low, high + 1));
            }
            return hi <= lo ? lo : ThreadLocalRandom.current().nextDouble(lo, hi);
        });
    }

    private static double orZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private static ComputedExpression nameOf(Expression<?> target) {
        return new ComputedExpression(ctx ->
                target.getSingle(ctx) instanceof CommandSender sender ? sender.getName() : null);
    }

    private static void registerConditions(SyntaxRegistry registry) {
        // Order matters: more specific patterns are registered (and matched) first.
        registry.registerCondition("%object% (is not|isn't|is not) set", arguments -> isSet(arguments.get(0), false));
        registry.registerCondition("%object% (is|are) set", arguments -> isSet(arguments.get(0), true));

        registry.registerCondition("%object% (is greater than or equal to|is at least|>=) %object%",
                arguments -> numeric(arguments, cmp -> cmp >= 0));
        registry.registerCondition("%object% (is less than or equal to|is at most|<=) %object%",
                arguments -> numeric(arguments, cmp -> cmp <= 0));
        registry.registerCondition("%object% (is greater than|is more than|>) %object%",
                arguments -> numeric(arguments, cmp -> cmp > 0));
        registry.registerCondition("%object% (is less than|<) %object%",
                arguments -> numeric(arguments, cmp -> cmp < 0));

        registry.registerCondition("%object% (is not|isn't|is not equal to|!=) %object%",
                arguments -> equality(arguments, false));
        registry.registerCondition("%object% (is|are|is equal to|=) %object%",
                arguments -> equality(arguments, true));
    }

    private static Condition isSet(Expression<?> target, boolean shouldBeSet) {
        return ctx -> (target.getSingle(ctx) != null) == shouldBeSet;
    }

    private static Condition equality(co.xenastudios.neoskript.api.syntax.Arguments arguments, boolean expected) {
        Expression<?> left = arguments.get(0);
        Expression<?> right = arguments.get(1);
        return ctx -> Comparisons.equal(left.getSingle(ctx), right.getSingle(ctx)) == expected;
    }

    private static Condition numeric(co.xenastudios.neoskript.api.syntax.Arguments arguments,
                                     java.util.function.IntPredicate test) {
        Expression<?> left = arguments.get(0);
        Expression<?> right = arguments.get(1);
        return ctx -> {
            Integer cmp = Comparisons.compare(left.getSingle(ctx), right.getSingle(ctx));
            return cmp != null && test.test(cmp);
        };
    }

    private static void registerEffects(SyntaxRegistry registry) {
        registry.registerEffect("broadcast %string%", arguments -> {
            Expression<?> message = arguments.get(0);
            return ctx -> Bukkit.broadcast(Component.text(Renderer.toDisplay(message.getSingle(ctx))));
        });

        registry.registerEffect("(message|send) %string% [to %player%]", arguments -> {
            Expression<?> message = arguments.get(0);
            Expression<?> target = arguments.get(1);
            return ctx -> {
                CommandSender receiver = resolveReceiver(target, ctx);
                if (receiver != null) {
                    receiver.sendMessage(Component.text(Renderer.toDisplay(message.getSingle(ctx))));
                }
            };
        });

        registry.registerEffect("set %object% to %object%", arguments -> {
            VariableExpression variable = requireVariable(arguments.get(0));
            Expression<?> value = arguments.get(1);
            return ctx -> variable.set(ctx, value.getSingle(ctx));
        });

        registry.registerEffect("add %object% to %object%", arguments -> {
            Expression<?> value = arguments.get(0);
            VariableExpression variable = requireVariable(arguments.get(1));
            return ctx -> {
                Object item = value.getSingle(ctx);
                if (variable.isList()) {
                    variable.addToList(ctx, item);
                } else {
                    Double sum = add(variable.getSingle(ctx), item);
                    if (sum != null) {
                        variable.set(ctx, sum);
                    }
                }
            };
        });

        registry.registerEffect("remove %object% from %object%", arguments -> {
            Expression<?> value = arguments.get(0);
            VariableExpression variable = requireVariable(arguments.get(1));
            return ctx -> {
                Object item = value.getSingle(ctx);
                if (variable.isList()) {
                    variable.removeFromList(ctx, item);
                } else {
                    Double difference = subtract(variable.getSingle(ctx), item);
                    if (difference != null) {
                        variable.set(ctx, difference);
                    }
                }
            };
        });

        registry.registerEffect("(delete|clear|reset) %object%", arguments -> {
            VariableExpression variable = requireVariable(arguments.get(0));
            return variable::delete;
        });

        registry.registerEffect("return %object%", arguments -> {
            Expression<?> value = arguments.get(0);
            return ctx -> {
                throw new ReturnSignal(value.getSingle(ctx));
            };
        });

        registry.registerEffect("(stop|exit) [trigger]", arguments -> ctx -> {
            throw StopSignal.INSTANCE;
        });
    }

    private static VariableExpression requireVariable(Expression<?> expression) {
        if (expression instanceof VariableExpression variable) {
            return variable;
        }
        throw new ParseException("Expected a variable, got: " + expression);
    }

    private static CommandSender resolveReceiver(Expression<?> target, TriggerContext ctx) {
        if (target != null) {
            return target.getSingle(ctx) instanceof CommandSender sender ? sender : null;
        }
        return ctx.event()
                .filter(PlayerEvent.class::isInstance)
                .map(event -> (CommandSender) ((PlayerEvent) event).getPlayer())
                .orElse(null);
    }

    private static Double add(Object current, Object value) {
        Double a = toNumber(current == null ? 0.0 : current);
        Double b = toNumber(value);
        return (a == null || b == null) ? null : a + b;
    }

    private static Double subtract(Object current, Object value) {
        Double a = toNumber(current == null ? 0.0 : current);
        Double b = toNumber(value);
        return (a == null || b == null) ? null : a - b;
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
