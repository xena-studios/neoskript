package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Arguments;
import co.xenastudios.neoskript.api.syntax.Condition;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.ComputedExpression;
import co.xenastudios.neoskript.core.expression.ComputedListExpression;
import co.xenastudios.neoskript.core.expression.NamedLocalExpression;
import co.xenastudios.neoskript.core.expression.VariableExpression;
import co.xenastudios.neoskript.core.parser.ParseException;
import co.xenastudios.neoskript.core.runtime.Renderer;
import co.xenastudios.neoskript.core.runtime.ReturnSignal;
import co.xenastudios.neoskript.core.runtime.StopSignal;
import co.xenastudios.neoskript.core.runtime.VariableScope;
import co.xenastudios.neoskript.core.type.TypeRegistry;
import co.xenastudios.neoskript.lang.expression.EventPlayerExpression;
import co.xenastudios.neoskript.lang.expression.EventValueExpression;
import co.xenastudios.neoskript.lang.type.BooleanType;
import co.xenastudios.neoskript.lang.type.NumberType;
import co.xenastudios.neoskript.lang.type.PlayerType;
import co.xenastudios.neoskript.lang.type.StringType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerEvent;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.PatternSyntaxException;

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
        registerTypes();
        registerExpressions(registry);
        registerConditions(registry);
        registerEffects(registry);
    }

    private static void registerTypes() {
        TypeRegistry types = new TypeRegistry();
        types.register(new NumberType());
        types.register(new StringType());
        types.register(new BooleanType());
        types.register(new PlayerType());
        Renderer.setTypeRegistry(types);
    }

    private static void registerExpressions(SyntaxRegistry registry) {
        registry.registerExpression("player", Player.class, arguments -> new EventPlayerExpression());
        registry.registerExpression("[the] event-player", Player.class,
                arguments -> new EventValueExpression<>(Player.class));
        registry.registerExpression("[the] (event-block|block)", Block.class,
                arguments -> new EventValueExpression<>(Block.class));
        registry.registerExpression("[the] (event-world|world)", World.class,
                arguments -> new EventValueExpression<>(World.class));
        registry.registerExpression("[the] (event-entity|entity)", Entity.class,
                arguments -> new EventValueExpression<>(Entity.class));
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

        registerStringAndListExpressions(registry);
    }

    private static void registerStringAndListExpressions(SyntaxRegistry registry) {
        registry.registerExpression("(uppercase|upper case) [of] %string%", Object.class,
                arguments -> mapString(arguments.get(0), s -> s.toUpperCase(Locale.ROOT)));
        registry.registerExpression("(lowercase|lower case) [of] %string%", Object.class,
                arguments -> mapString(arguments.get(0), s -> s.toLowerCase(Locale.ROOT)));
        registry.registerExpression("length of %string%", Object.class, arguments -> {
            Expression<?> s = arguments.get(0);
            return new ComputedExpression(ctx -> (double) Renderer.toDisplay(s.getSingle(ctx)).length());
        });
        registry.registerExpression("%string% split at %string%", Object.class, arguments -> {
            Expression<?> source = arguments.get(0);
            Expression<?> delimiter = arguments.get(1);
            return new ComputedListExpression(ctx -> {
                String text = Renderer.toDisplay(source.getSingle(ctx));
                String at = Renderer.toDisplay(delimiter.getSingle(ctx));
                return at.isEmpty() ? new Object[]{text} : text.split(java.util.regex.Pattern.quote(at), -1);
            });
        });
        registry.registerExpression("join %objects% with %string%", Object.class, arguments -> {
            Expression<?> source = arguments.get(0);
            Expression<?> delimiter = arguments.get(1);
            return new ComputedExpression(ctx -> {
                StringBuilder sb = new StringBuilder();
                String at = Renderer.toDisplay(delimiter.getSingle(ctx));
                Object[] values = source.getAll(ctx);
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) {
                        sb.append(at);
                    }
                    sb.append(Renderer.toDisplay(values[i]));
                }
                return sb.toString();
            });
        });

        registry.registerExpression("first element [out] of %objects%", Object.class,
                arguments -> element(arguments.get(0), Element.FIRST));
        registry.registerExpression("last element [out] of %objects%", Object.class,
                arguments -> element(arguments.get(0), Element.LAST));
        registry.registerExpression("random element [out] of %objects%", Object.class,
                arguments -> element(arguments.get(0), Element.RANDOM));

        registry.registerExpression("reversed %objects%", Object.class, arguments -> {
            Expression<?> source = arguments.get(0);
            return new ComputedListExpression(ctx -> {
                Object[] values = source.getAll(ctx).clone();
                for (int i = 0, j = values.length - 1; i < j; i++, j--) {
                    Object tmp = values[i];
                    values[i] = values[j];
                    values[j] = tmp;
                }
                return values;
            });
        });
        registry.registerExpression("sorted %objects%", Object.class, arguments -> {
            Expression<?> source = arguments.get(0);
            return new ComputedListExpression(ctx -> {
                Object[] values = source.getAll(ctx).clone();
                java.util.Arrays.sort(values, BuiltinModule::compareForSort);
                return values;
            });
        });
    }

    private enum Element {
        FIRST, LAST, RANDOM
    }

    private static ComputedExpression element(Expression<?> source, Element which) {
        return new ComputedExpression(ctx -> {
            Object[] values = source.getAll(ctx);
            if (values.length == 0) {
                return null;
            }
            return switch (which) {
                case FIRST -> values[0];
                case LAST -> values[values.length - 1];
                case RANDOM -> values[ThreadLocalRandom.current().nextInt(values.length)];
            };
        });
    }

    private static ComputedExpression mapString(Expression<?> source, java.util.function.UnaryOperator<String> op) {
        return new ComputedExpression(ctx -> op.apply(Renderer.toDisplay(source.getSingle(ctx))));
    }

    private static int compareForSort(Object a, Object b) {
        Integer numeric = Comparisons.compare(a, b);
        return numeric != null ? numeric : Renderer.toDisplay(a).compareTo(Renderer.toDisplay(b));
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

        // String/collection conditions — registered before the generic comparisons they could shadow.
        registry.registerCondition("%object% (is between|between) %object% and %object%", arguments -> {
            Expression<?> value = arguments.get(0);
            Expression<?> low = arguments.get(1);
            Expression<?> high = arguments.get(2);
            return ctx -> {
                Integer lo = Comparisons.compare(value.getSingle(ctx), low.getSingle(ctx));
                Integer hi = Comparisons.compare(value.getSingle(ctx), high.getSingle(ctx));
                return lo != null && hi != null && lo >= 0 && hi <= 0;
            };
        });
        registry.registerCondition("%object% (doesn't contain|does not contain|don't contain) %object%",
                arguments -> contains(arguments, false));
        registry.registerCondition("%object% contains %object%", arguments -> contains(arguments, true));
        registry.registerCondition("%string% starts with %string%", arguments -> affix(arguments, true));
        registry.registerCondition("%string% ends with %string%", arguments -> affix(arguments, false));
        registry.registerCondition("%string% matches %string%", arguments -> {
            Expression<?> text = arguments.get(0);
            Expression<?> regex = arguments.get(1);
            return ctx -> {
                try {
                    return Renderer.toDisplay(text.getSingle(ctx)).matches(Renderer.toDisplay(regex.getSingle(ctx)));
                } catch (PatternSyntaxException e) {
                    return false;
                }
            };
        });

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

    private static Condition contains(Arguments arguments, boolean expected) {
        Expression<?> container = arguments.get(0);
        Expression<?> item = arguments.get(1);
        return ctx -> {
            Object single = container.getSingle(ctx);
            Object needle = item.getSingle(ctx);
            boolean result;
            if (container.isSingle() && single instanceof String text) {
                result = text.contains(Renderer.toDisplay(needle));
            } else {
                result = false;
                for (Object value : container.getAll(ctx)) {
                    if (Comparisons.equal(value, needle)) {
                        result = true;
                        break;
                    }
                }
            }
            return result == expected;
        };
    }

    private static Condition affix(Arguments arguments, boolean prefix) {
        Expression<?> text = arguments.get(0);
        Expression<?> affix = arguments.get(1);
        return ctx -> {
            String s = Renderer.toDisplay(text.getSingle(ctx));
            String a = Renderer.toDisplay(affix.getSingle(ctx));
            return prefix ? s.startsWith(a) : s.endsWith(a);
        };
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
            return ctx -> mutate(ctx, variable, () -> {
                Object item = value.getSingle(ctx);
                if (variable.isList()) {
                    variable.addToList(ctx, item);
                } else {
                    Double sum = add(variable.getSingle(ctx), item);
                    if (sum != null) {
                        variable.set(ctx, sum);
                    }
                }
            });
        });

        registry.registerEffect("remove %object% from %object%", arguments -> {
            Expression<?> value = arguments.get(0);
            VariableExpression variable = requireVariable(arguments.get(1));
            return ctx -> mutate(ctx, variable, () -> {
                Object item = value.getSingle(ctx);
                if (variable.isList()) {
                    variable.removeFromList(ctx, item);
                } else {
                    Double difference = subtract(variable.getSingle(ctx), item);
                    if (difference != null) {
                        variable.set(ctx, difference);
                    }
                }
            });
        });

        registry.registerEffect("(delete|clear|reset) %object%", arguments -> {
            VariableExpression variable = requireVariable(arguments.get(0));
            return ctx -> mutate(ctx, variable, () -> variable.delete(ctx));
        });

        registry.registerEffect("return %object%", arguments -> {
            Expression<?> value = arguments.get(0);
            return ctx -> {
                throw new ReturnSignal(value.getSingle(ctx));
            };
        });

        registry.registerEffect("return", arguments -> ctx -> {
            throw new ReturnSignal(null);
        });

        registry.registerEffect("(stop|exit) [trigger]", arguments -> ctx -> {
            throw StopSignal.INSTANCE;
        });

        registry.registerEffect("cancel [the] event", arguments -> ctx -> setCancelled(ctx, true));
        registry.registerEffect("uncancel [the] event", arguments -> ctx -> setCancelled(ctx, false));
    }

    private static void setCancelled(TriggerContext ctx, boolean cancelled) {
        ctx.event()
                .filter(Cancellable.class::isInstance)
                .ifPresent(event -> ((Cancellable) event).setCancelled(cancelled));
    }

    private static VariableExpression requireVariable(Expression<?> expression) {
        if (expression instanceof VariableExpression variable) {
            return variable;
        }
        throw new ParseException("Expected a variable, got: " + expression);
    }

    /**
     * Runs a read-modify-write on a variable, serializing it across concurrent (Folia) handlers when
     * the target is a shared global. Local variables are per-execution, so they need no locking.
     */
    private static void mutate(TriggerContext ctx, VariableExpression variable, Runnable op) {
        if (!variable.isLocal() && ctx instanceof VariableScope scope) {
            scope.runAtomic(op);
        } else {
            op.run();
        }
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
