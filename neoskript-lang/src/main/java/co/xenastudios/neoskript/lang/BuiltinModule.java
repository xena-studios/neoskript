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
import co.xenastudios.neoskript.core.runtime.BreakSignal;
import co.xenastudios.neoskript.core.runtime.ContinueSignal;
import co.xenastudios.neoskript.core.runtime.Renderer;
import co.xenastudios.neoskript.core.runtime.ReturnSignal;
import co.xenastudios.neoskript.core.runtime.StopSignal;
import co.xenastudios.neoskript.core.runtime.VariableScope;
import co.xenastudios.neoskript.core.type.TypeRegistry;
import co.xenastudios.neoskript.lang.expression.EventPlayerExpression;
import co.xenastudios.neoskript.lang.expression.EventValueExpression;
import co.xenastudios.neoskript.lang.type.BooleanType;
import co.xenastudios.neoskript.lang.type.GameModeType;
import co.xenastudios.neoskript.lang.type.ItemType;
import co.xenastudios.neoskript.lang.type.LocationType;
import co.xenastudios.neoskript.lang.type.NumberType;
import co.xenastudios.neoskript.lang.type.PlayerType;
import co.xenastudios.neoskript.lang.type.StringType;
import co.xenastudios.neoskript.lang.type.VectorType;
import co.xenastudios.neoskript.lang.type.WorldType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.util.Vector;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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
        types.register(new WorldType());
        types.register(new GameModeType());
        types.register(new LocationType());
        types.register(new VectorType());
        types.register(new ItemType());
        types.register(new co.xenastudios.neoskript.lang.type.ColourType());
        types.register(new co.xenastudios.neoskript.lang.type.DateType());
        // Generic enum-backed types (reflective, version-robust). Code names follow Skript's.
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "entitytype", org.bukkit.entity.EntityType.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "damagecause", org.bukkit.event.entity.EntityDamageEvent.DamageCause.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "clicktype", org.bukkit.event.inventory.ClickType.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "inventoryaction", org.bukkit.event.inventory.InventoryAction.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "weathertype", org.bukkit.WeatherType.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "difficulty", org.bukkit.Difficulty.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "blockface", org.bukkit.block.BlockFace.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "equipmentslot", org.bukkit.inventory.EquipmentSlot.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "spawnreason", org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "environment", org.bukkit.World.Environment.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "entityeffect", org.bukkit.EntityEffect.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "healreason", org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "teleportcause", org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "inventorytype", org.bukkit.event.inventory.InventoryType.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "soundcategory", org.bukkit.SoundCategory.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "fireworktype", org.bukkit.FireworkEffect.Type.class));
        types.register(new co.xenastudios.neoskript.lang.type.EnumType<>(
                "material", org.bukkit.Material.class));
        // Registry-backed types (lazy: the Registry is only touched at parse time, never at registration).
        types.register(new co.xenastudios.neoskript.lang.type.RegistryType<>(
                "biome", org.bukkit.block.Biome.class, () -> org.bukkit.Registry.BIOME));
        types.register(new co.xenastudios.neoskript.lang.type.RegistryType<>(
                "enchantment", org.bukkit.enchantments.Enchantment.class, () -> org.bukkit.Registry.ENCHANTMENT));
        types.register(new co.xenastudios.neoskript.lang.type.RegistryType<>(
                "attributetype", org.bukkit.attribute.Attribute.class, () -> org.bukkit.Registry.ATTRIBUTE));
        types.register(new co.xenastudios.neoskript.lang.type.RegistryType<>(
                "villagerprofession", org.bukkit.entity.Villager.Profession.class,
                () -> org.bukkit.Registry.VILLAGER_PROFESSION));
        types.register(new co.xenastudios.neoskript.lang.type.RegistryType<>(
                "villagertype", org.bukkit.entity.Villager.Type.class, () -> org.bukkit.Registry.VILLAGER_TYPE));
        Renderer.setTypeRegistry(types);
        BuiltinSerializers.registerAll();
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
            return new ComputedExpression(ctx -> {
                Object[] all = source.getAll(ctx);
                // For a single item stack, "amount of" means its stack size, not the element count.
                if (all.length == 1 && all[0] instanceof ItemStack item) {
                    return (double) item.getAmount();
                }
                return (double) all.length;
            });
        });

        registry.registerExpression("random number (from|between) %number% (to|and) %number%", Object.class,
                arguments -> random(arguments.get(0), arguments.get(1), false));
        registry.registerExpression("random integer (from|between) %number% (to|and) %number%", Object.class,
                arguments -> random(arguments.get(0), arguments.get(1), true));

        registerStringAndListExpressions(registry);
        registerCommandExpressions(registry);
        registerEntityExpressions(registry);
    }

    private static void registerEntityExpressions(SyntaxRegistry registry) {
        playerProperty(registry, "health", LivingEntity::getHealth);
        playerProperty(registry, "(max health|maximum health)", LivingEntity::getMaxHealth);
        playerProperty(registry, "(food level|hunger|food)", player -> (double) ((Player) player).getFoodLevel());
        playerProperty(registry, "level", player -> (double) ((Player) player).getLevel());
        playerProperty(registry, "(uuid|unique id)", player -> player.getUniqueId().toString());
        playerProperty(registry, "(gamemode|game mode)", player -> ((Player) player).getGameMode());
        playerProperty(registry, "world", LivingEntity::getWorld);
        playerProperty(registry, "location", LivingEntity::getLocation);
        playerProperty(registry, "name", player -> player.getName());
        playerProperty(registry, "(display name|displayname)", player -> ((Player) player).getName());

        registry.registerExpression("(all players|online players|all online players)", Object.class,
                arguments -> new ComputedListExpression(ctx -> Bukkit.getOnlinePlayers().toArray()));

        registry.registerExpression("x[-coordinate] of %location%", Object.class,
                arguments -> coordinate(arguments.get(0), Location::getX, Vector::getX));
        registry.registerExpression("y[-coordinate] of %location%", Object.class,
                arguments -> coordinate(arguments.get(0), Location::getY, Vector::getY));
        registry.registerExpression("z[-coordinate] of %location%", Object.class,
                arguments -> coordinate(arguments.get(0), Location::getZ, Vector::getZ));
        registry.registerExpression("time of %world%", Object.class, arguments -> {
            Expression<?> world = arguments.get(0);
            return new ComputedExpression(ctx ->
                    world.getSingle(ctx) instanceof World w ? (double) w.getTime() : null);
        });
        registry.registerExpression("now", Object.class,
                arguments -> new ComputedExpression(ctx -> (double) System.currentTimeMillis()));
        registry.registerExpression("velocity of %entity%", Object.class, arguments -> {
            Expression<?> source = arguments.get(0);
            return new ComputedExpression(ctx -> source.getSingle(ctx) instanceof Entity e ? e.getVelocity() : null);
        });
        registry.registerExpression("type of %object%", Object.class, arguments -> {
            Expression<?> source = arguments.get(0);
            return new ComputedExpression(ctx -> {
                Object value = source.getSingle(ctx);
                if (value instanceof ItemStack item) {
                    return item.getType().name().toLowerCase(Locale.ROOT);
                }
                if (value instanceof Entity entity) {
                    return entity.getType().name().toLowerCase(Locale.ROOT);
                }
                return null;
            });
        });

        registry.registerExpression("distance between %location% and %location%", Object.class, arguments -> {
            Expression<?> a = arguments.get(0);
            Expression<?> b = arguments.get(1);
            return new ComputedExpression(ctx -> {
                Location first = toLocation(a.getSingle(ctx));
                Location second = toLocation(b.getSingle(ctx));
                if (first == null || second == null || first.getWorld() == null
                        || !first.getWorld().equals(second.getWorld())) {
                    return null;
                }
                return first.distance(second);
            });
        });

        gameMode(registry, "survival", GameMode.SURVIVAL);
        gameMode(registry, "creative", GameMode.CREATIVE);
        gameMode(registry, "adventure", GameMode.ADVENTURE);
        gameMode(registry, "spectator", GameMode.SPECTATOR);
    }

    /** Registers {@code <prop> of %player%} and {@code %player%'s <prop>} for a player accessor. */
    private static void playerProperty(SyntaxRegistry registry, String name, Function<LivingEntity, Object> accessor) {
        registry.registerExpression(name + " of %player%", Object.class,
                arguments -> playerValue(arguments.get(0), accessor));
        registry.registerExpression("%player%'s " + name, Object.class,
                arguments -> playerValue(arguments.get(0), accessor));
    }

    private static ComputedExpression playerValue(Expression<?> target, Function<LivingEntity, Object> accessor) {
        return new ComputedExpression(ctx ->
                target.getSingle(ctx) instanceof LivingEntity entity ? accessor.apply(entity) : null);
    }

    private static ComputedExpression coordinate(Expression<?> target,
                                                 java.util.function.ToDoubleFunction<Location> locationFn,
                                                 java.util.function.ToDoubleFunction<Vector> vectorFn) {
        return new ComputedExpression(ctx -> {
            Object value = target.getSingle(ctx);
            if (value instanceof Vector vector) {
                return vectorFn.applyAsDouble(vector);
            }
            Location location = toLocation(value);
            return location == null ? null : locationFn.applyAsDouble(location);
        });
    }

    /** Resolves a potion effect type by name (e.g. {@code speed}, {@code instant_health}), or null. */
    @SuppressWarnings("deprecation")
    private static PotionEffectType potionEffect(String name) {
        if (name == null) {
            return null;
        }
        return PotionEffectType.getByName(name.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
    }

    /** Coerces a value to a Location: a Location directly, or an entity's current location. */
    private static Location toLocation(Object value) {
        if (value instanceof Location location) {
            return location;
        }
        if (value instanceof Entity entity) {
            return entity.getLocation();
        }
        return null;
    }

    private static void gameMode(SyntaxRegistry registry, String name, GameMode mode) {
        registry.registerExpression(name + " [mode]", Object.class, arguments -> new ComputedExpression(ctx -> mode));
    }

    private static void registerCommandExpressions(SyntaxRegistry registry) {
        registry.registerExpression("(sender|command sender|executor)", Object.class,
                arguments -> new NamedLocalExpression("command-sender"));
        registry.registerExpression("arg-%number%", Object.class, arguments -> argument(arguments.get(0)));
        registry.registerExpression("arg %number%", Object.class, arguments -> argument(arguments.get(0)));
        registry.registerExpression("argument %number%", Object.class, arguments -> argument(arguments.get(0)));
        registry.registerExpression("(args|arguments|all arguments)", Object.class, arguments ->
                new ComputedListExpression(ctx -> {
                    int count = (int) orZero(toNumber(ctx.getLocal("arg-count")));
                    Object[] values = new Object[Math.max(0, count)];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = ctx.getLocal("arg-" + (i + 1));
                    }
                    return values;
                }));
    }

    private static ComputedExpression argument(Expression<?> indexExpr) {
        return new ComputedExpression(ctx -> {
            int index = (int) orZero(toNumber(indexExpr.getSingle(ctx)));
            return ctx.getLocal("arg-" + index);
        });
    }

    private static void registerStringAndListExpressions(SyntaxRegistry registry) {
        registry.registerExpression("(uppercase|upper case) [of] %string%", Object.class,
                arguments -> mapString(arguments.get(0), s -> s.toUpperCase(Locale.ROOT)));
        registry.registerExpression("(lowercase|lower case) [of] %string%", Object.class,
                arguments -> mapString(arguments.get(0), s -> s.toLowerCase(Locale.ROOT)));
        registry.registerExpression("capitalized %string%", Object.class,
                arguments -> mapString(arguments.get(0), BuiltinModule::capitalize));
        registry.registerExpression("length of %string%", Object.class, arguments -> {
            Expression<?> s = arguments.get(0);
            return new ComputedExpression(ctx -> {
                Object value = s.getSingle(ctx);
                // A vector's "length" is its magnitude; otherwise it's the string length.
                if (value instanceof Vector vector) {
                    return vector.length();
                }
                return (double) Renderer.toDisplay(value).length();
            });
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

    /** Capitalises the first letter of each whitespace-separated word. */
    private static String capitalize(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean startOfWord = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            result.append(startOfWord ? Character.toTitleCase(c) : c);
            startOfWord = Character.isWhitespace(c);
        }
        return result.toString();
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

        // Player-state conditions — registered before the generic equality they would otherwise shadow.
        registerEntityConditions(registry);

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
        registry.registerCondition("chance of %number%[ percent]", arguments -> {
            Expression<?> chance = arguments.get(0);
            return ctx -> ThreadLocalRandom.current().nextDouble() * 100.0 < orZero(toNumber(chance.getSingle(ctx)));
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

    private static void registerEntityConditions(SyntaxRegistry registry) {
        registry.registerCondition("%player% (is op|is an op|is operator)", a -> playerIs(a.get(0), Player::isOp, true));
        registry.registerCondition("%player% (is not|isn't) op", a -> playerIs(a.get(0), Player::isOp, false));
        registry.registerCondition("%player% is online", a -> playerIs(a.get(0), Player::isOnline, true));
        registry.registerCondition("%player% is offline", a -> playerIs(a.get(0), Player::isOnline, false));
        registry.registerCondition("%player% is alive", a -> playerIs(a.get(0), player -> player.getHealth() > 0, true));
        registry.registerCondition("%player% is dead", a -> playerIs(a.get(0), player -> player.getHealth() <= 0, true));
        registry.registerCondition("%player% is sneaking", a -> playerIs(a.get(0), Player::isSneaking, true));
        registry.registerCondition("%player% is sprinting", a -> playerIs(a.get(0), Player::isSprinting, true));
        registry.registerCondition("%player% is flying", a -> playerIs(a.get(0), Player::isFlying, true));
        registry.registerCondition("%player% can fly", a -> playerIs(a.get(0), Player::getAllowFlight, true));
        registry.registerCondition("%player% (can't fly|cannot fly|can not fly)",
                a -> playerIs(a.get(0), Player::getAllowFlight, false));
        registry.registerCondition("%player% is blocking", a -> playerIs(a.get(0), Player::isBlocking, true));
        registry.registerCondition("%player% is gliding", a -> playerIs(a.get(0), Player::isGliding, true));
        registry.registerCondition("%player% is glowing", a -> playerIs(a.get(0), Player::isGlowing, true));
        registry.registerCondition("%player% is sleeping", a -> playerIs(a.get(0), Player::isSleeping, true));
        registry.registerCondition("%player% is swimming", a -> playerIs(a.get(0), Player::isSwimming, true));
        registry.registerCondition("%player% is (on the ground|on ground)",
                a -> playerIs(a.get(0), Player::isOnGround, true));
        registry.registerCondition("%player% (is burning|is on fire)",
                a -> playerIs(a.get(0), player -> player.getFireTicks() > 0, true));
        registry.registerCondition("%player% is whitelisted", a -> playerIs(a.get(0), Player::isWhitelisted, true));
        registry.registerCondition("%player% is banned", a -> playerIs(a.get(0), Player::isBanned, true));
        registry.registerCondition("%player% is (in a vehicle|inside a vehicle)",
                a -> playerIs(a.get(0), Player::isInsideVehicle, true));
        registry.registerCondition("%player% has permission %string%", a -> hasPermission(a, true));
        registry.registerCondition("%player% (doesn't have|does not have|lacks) permission %string%",
                a -> hasPermission(a, false));
        registry.registerCondition("%player% (has|have) %object%", a -> hasItem(a, true));
        registry.registerCondition("%player% (doesn't have|does not have|hasn't|haven't) %object%",
                a -> hasItem(a, false));
        registry.registerCondition("%player% is holding %object%", a -> {
            Expression<?> target = a.get(0);
            Expression<?> item = a.get(1);
            return ctx -> target.getSingle(ctx) instanceof Player player
                    && item.getSingle(ctx) instanceof ItemStack stack
                    && player.getInventory().getItemInMainHand().getType() == stack.getType();
        });
        registry.registerCondition("%world% is raining", a -> worldIs(a.get(0), true));
        registry.registerCondition("%world% (is not raining|isn't raining|is clear)", a -> worldIs(a.get(0), false));
        registry.registerCondition("%world% is thundering", a -> {
            Expression<?> target = a.get(0);
            return ctx -> target.getSingle(ctx) instanceof World w && w.isThundering();
        });
        registry.registerCondition("%player% is in water", a -> playerIs(a.get(0), Player::isInWater, true));
        registry.registerCondition("%player% is in lava", a -> playerIs(a.get(0), Player::isInLava, true));
        registry.registerCondition("%player% (can build|is allowed to build)",
                a -> playerIs(a.get(0), player -> player.getGameMode() != org.bukkit.GameMode.ADVENTURE
                        && player.getGameMode() != org.bukkit.GameMode.SPECTATOR, true));
        registry.registerCondition("%player% has played before", a -> playerIs(a.get(0), Player::hasPlayedBefore, true));
        registry.registerCondition("%object% is tamed", a -> {
            Expression<?> target = a.get(0);
            return ctx -> target.getSingle(ctx) instanceof Tameable tameable && tameable.isTamed();
        });
        registry.registerCondition("%object% is leashed", a -> {
            Expression<?> target = a.get(0);
            return ctx -> target.getSingle(ctx) instanceof LivingEntity entity && entity.isLeashed();
        });
        registry.registerCondition("%player% is wearing %object%", a -> {
            Expression<?> target = a.get(0);
            Expression<?> item = a.get(1);
            return ctx -> {
                if (!(target.getSingle(ctx) instanceof Player player)
                        || !(item.getSingle(ctx) instanceof ItemStack stack)) {
                    return false;
                }
                for (ItemStack armor : player.getInventory().getArmorContents()) {
                    if (armor != null && armor.getType() == stack.getType()) {
                        return true;
                    }
                }
                return false;
            };
        });
    }

    private static Condition worldIs(Expression<?> target, boolean raining) {
        return ctx -> target.getSingle(ctx) instanceof World world && world.hasStorm() == raining;
    }

    private static Condition hasItem(Arguments arguments, boolean expected) {
        Expression<?> target = arguments.get(0);
        Expression<?> item = arguments.get(1);
        return ctx -> {
            boolean has = target.getSingle(ctx) instanceof Player player
                    && item.getSingle(ctx) instanceof ItemStack stack
                    && player.getInventory().containsAtLeast(stack, stack.getAmount());
            return has == expected;
        };
    }

    private static Condition playerIs(Expression<?> target, Predicate<Player> test, boolean expected) {
        return ctx -> target.getSingle(ctx) instanceof Player player && test.test(player) == expected;
    }

    private static Condition hasPermission(Arguments arguments, boolean expected) {
        Expression<?> target = arguments.get(0);
        Expression<?> permission = arguments.get(1);
        return ctx -> target.getSingle(ctx) instanceof Player player
                && player.hasPermission(Renderer.toDisplay(permission.getSingle(ctx))) == expected;
    }

    private static void registerEntityEffects(SyntaxRegistry registry) {
        registry.registerEffect("set health of %player% to %number%", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> value = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof LivingEntity entity) {
                    double health = Math.max(0, Math.min(orZero(toNumber(value.getSingle(ctx))), entity.getMaxHealth()));
                    entity.setHealth(health);
                }
            };
        });
        registry.registerEffect("set (food level|hunger) of %player% to %number%", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> value = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Player player) {
                    player.setFoodLevel((int) orZero(toNumber(value.getSingle(ctx))));
                }
            };
        });
        registry.registerEffect("set (gamemode|game mode) of %player% to %gamemode%", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> value = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Player player && value.getSingle(ctx) instanceof GameMode mode) {
                    player.setGameMode(mode);
                }
            };
        });

        registry.registerEffect("kill %entity%", arguments -> {
            Expression<?> target = arguments.get(0);
            return ctx -> {
                Object value = target.getSingle(ctx);
                if (value instanceof LivingEntity entity) {
                    entity.setHealth(0);
                } else if (value instanceof Entity entity) {
                    entity.remove();
                }
            };
        });
        registry.registerEffect("heal %player%", arguments ->
                playerEffect(arguments.get(0), player -> player.setHealth(player.getMaxHealth())));
        registry.registerEffect("feed %player%", arguments ->
                playerEffect(arguments.get(0), player -> player.setFoodLevel(20)));
        registry.registerEffect("op %player%", arguments ->
                playerEffect(arguments.get(0), player -> player.setOp(true)));
        registry.registerEffect("deop %player%", arguments ->
                playerEffect(arguments.get(0), player -> player.setOp(false)));
        registry.registerEffect("kick %player% [(due to|because of) %string%]", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> reason = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Player player) {
                    String text = reason == null ? "Kicked from the server" : Renderer.toDisplay(reason.getSingle(ctx));
                    player.kick(colored(text));
                }
            };
        });
        registry.registerEffect("teleport %player% to %object%", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> destination = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Player player) {
                    Location to = toLocation(destination.getSingle(ctx));
                    if (to != null) {
                        player.teleport(to);
                    }
                }
            };
        });

        registry.registerEffect("play sound %string% (to|for) %player% [with volume %-number%] [with pitch %-number%]",
                arguments -> {
                    Expression<?> sound = arguments.get(0);
                    Expression<?> target = arguments.get(1);
                    Expression<?> volume = arguments.get(2);
                    Expression<?> pitch = arguments.get(3);
                    return ctx -> {
                        if (target.getSingle(ctx) instanceof Player player) {
                            String name = Renderer.toDisplay(sound.getSingle(ctx)).toLowerCase(Locale.ROOT);
                            float vol = volume == null ? 1f : (float) orZero(toNumber(volume.getSingle(ctx)));
                            float pit = pitch == null ? 1f : (float) orZero(toNumber(pitch.getSingle(ctx)));
                            player.playSound(player.getLocation(), name, vol, pit);
                        }
                    };
                });

        registry.registerEffect(
                "apply %string% to %player% [for %-number% seconds] [(at|of) (tier|level|amplifier) %-number%]",
                arguments -> {
                    Expression<?> effect = arguments.get(0);
                    Expression<?> target = arguments.get(1);
                    Expression<?> seconds = arguments.get(2);
                    Expression<?> amplifier = arguments.get(3);
                    return ctx -> {
                        if (!(target.getSingle(ctx) instanceof Player player)) {
                            return;
                        }
                        PotionEffectType type = potionEffect(Renderer.toDisplay(effect.getSingle(ctx)));
                        if (type == null) {
                            return;
                        }
                        int ticks = seconds == null ? 30 * 20 : (int) (orZero(toNumber(seconds.getSingle(ctx))) * 20);
                        int level = amplifier == null ? 0 : Math.max(0, (int) orZero(toNumber(amplifier.getSingle(ctx))) - 1);
                        player.addPotionEffect(new PotionEffect(type, ticks, level));
                    };
                });
        registry.registerEffect("(clear|remove all) [active] potion effects (from|of) %player%", arguments -> {
            Expression<?> target = arguments.get(0);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Player player) {
                    player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
                }
            };
        });

        registry.registerEffect("set (walk speed|walkspeed) of %player% to %number%", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> value = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Player player) {
                    player.setWalkSpeed((float) Math.max(-1, Math.min(1, orZero(toNumber(value.getSingle(ctx))))));
                }
            };
        });
        registry.registerEffect("(allow|enable) (flight|flying) for %player%", arguments ->
                playerEffect(arguments.get(0), player -> player.setAllowFlight(true)));
        registry.registerEffect("(disallow|disable) (flight|flying) for %player%", arguments ->
                playerEffect(arguments.get(0), player -> player.setAllowFlight(false)));
        registry.registerEffect("set (display name|displayname) of %player% to %string%", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> name = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Player player) {
                    player.displayName(colored(Renderer.toDisplay(name.getSingle(ctx))));
                }
            };
        });
        registry.registerEffect("clear [the] inventory of %player%", arguments ->
                playerEffect(arguments.get(0), player -> player.getInventory().clear()));
        registry.registerEffect("clear %player%'s inventory", arguments ->
                playerEffect(arguments.get(0), player -> player.getInventory().clear()));

        registry.registerEffect("set time of %world% to %number%", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> value = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof World world) {
                    world.setTime((long) orZero(toNumber(value.getSingle(ctx))));
                }
            };
        });
        registry.registerEffect("make %world% (sunny|clear)", arguments ->
                worldEffect(arguments.get(0), world -> world.setStorm(false)));
        registry.registerEffect("make %world% (rainy|stormy)", arguments ->
                worldEffect(arguments.get(0), world -> world.setStorm(true)));
        registry.registerEffect("make %world% thunder[ing]", arguments ->
                worldEffect(arguments.get(0), world -> world.setThundering(true)));
        registry.registerEffect("set (weather|storm) duration of %world% to %number% seconds", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> value = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof World world) {
                    world.setWeatherDuration((int) (orZero(toNumber(value.getSingle(ctx))) * 20));
                }
            };
        });
        registry.registerEffect("set thunder duration of %world% to %number% seconds", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> value = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof World world) {
                    world.setThunderDuration((int) (orZero(toNumber(value.getSingle(ctx))) * 20));
                }
            };
        });

        registry.registerEffect("set (max health|maximum health) of %player% to %number%", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> value = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof LivingEntity entity) {
                    entity.setMaxHealth(Math.max(1, orZero(toNumber(value.getSingle(ctx)))));
                }
            };
        });
        registry.registerEffect("ignite %entity% [for %number% seconds]", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> seconds = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Entity entity) {
                    int ticks = seconds == null ? 100 : (int) (orZero(toNumber(seconds.getSingle(ctx))) * 20);
                    entity.setFireTicks(ticks);
                }
            };
        });
        registry.registerEffect("extinguish %entity%", arguments -> {
            Expression<?> target = arguments.get(0);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Entity entity) {
                    entity.setFireTicks(0);
                }
            };
        });
        registry.registerEffect("set velocity of %entity% to %vector%", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> velocity = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Entity entity && velocity.getSingle(ctx) instanceof Vector vec) {
                    entity.setVelocity(vec);
                }
            };
        });
        registry.registerEffect("set [the] block at %location% to %item%", arguments -> {
            Expression<?> location = arguments.get(0);
            Expression<?> item = arguments.get(1);
            return ctx -> {
                Location loc = toLocation(location.getSingle(ctx));
                if (loc != null && item.getSingle(ctx) instanceof ItemStack stack) {
                    loc.getBlock().setType(stack.getType());
                }
            };
        });

        registry.registerEffect("give %object% to %player%", arguments -> {
            Expression<?> item = arguments.get(0);
            Expression<?> target = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Player player && item.getSingle(ctx) instanceof ItemStack stack) {
                    player.getInventory().addItem(stack);
                }
            };
        });
        registry.registerEffect("take %object% from %player%", arguments -> {
            Expression<?> item = arguments.get(0);
            Expression<?> target = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Player player && item.getSingle(ctx) instanceof ItemStack stack) {
                    player.getInventory().removeItem(stack);
                }
            };
        });
    }

    private static co.xenastudios.neoskript.api.syntax.Effect worldEffect(Expression<?> target,
                                                                          Consumer<World> action) {
        return ctx -> {
            if (target.getSingle(ctx) instanceof World world) {
                action.accept(world);
            }
        };
    }

    private static co.xenastudios.neoskript.api.syntax.Effect playerEffect(Expression<?> target,
                                                                           Consumer<Player> action) {
        return ctx -> {
            if (target.getSingle(ctx) instanceof Player player) {
                action.accept(player);
            }
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
        // Registered first so specific "set <property> of ..." effects win over the generic set.
        registerEntityEffects(registry);

        registry.registerEffect("broadcast %string%", arguments -> {
            Expression<?> message = arguments.get(0);
            return ctx -> Bukkit.broadcast(colored(Renderer.toDisplay(message.getSingle(ctx))));
        });

        registry.registerEffect("(message|send) %string% [to %player%]", arguments -> {
            Expression<?> message = arguments.get(0);
            Expression<?> target = arguments.get(1);
            return ctx -> {
                CommandSender receiver = resolveReceiver(target, ctx);
                if (receiver != null) {
                    receiver.sendMessage(colored(Renderer.toDisplay(message.getSingle(ctx))));
                }
            };
        });

        registry.registerEffect("send (actionbar|action bar) %string% [to %player%]", arguments -> {
            Expression<?> message = arguments.get(0);
            Expression<?> target = arguments.get(1);
            return ctx -> {
                CommandSender receiver = resolveReceiver(target, ctx);
                if (receiver != null) {
                    receiver.sendActionBar(colored(Renderer.toDisplay(message.getSingle(ctx))));
                }
            };
        });

        registry.registerEffect("send title %string% [to %player%]", arguments -> {
            Expression<?> message = arguments.get(0);
            Expression<?> target = arguments.get(1);
            return ctx -> {
                if (resolveReceiver(target, ctx) instanceof Player player) {
                    player.showTitle(Title.title(colored(Renderer.toDisplay(message.getSingle(ctx))), Component.empty()));
                }
            };
        });

        registry.registerEffect("(make|let|force) %object% [to] (execute|run|perform) [command] %string%", arguments -> {
            Expression<?> who = arguments.get(0);
            Expression<?> command = arguments.get(1);
            return ctx -> {
                if (who.getSingle(ctx) instanceof CommandSender sender) {
                    Bukkit.dispatchCommand(sender, stripSlash(Renderer.toDisplay(command.getSingle(ctx))));
                }
            };
        });
        registry.registerEffect("execute console command %string%", arguments -> {
            Expression<?> command = arguments.get(0);
            return ctx -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    stripSlash(Renderer.toDisplay(command.getSingle(ctx))));
        });

        registry.registerEffect("set %object% to %object%", arguments -> {
            VariableExpression variable = requireVariable(arguments.get(0));
            Expression<?> value = arguments.get(1);
            return ctx -> variable.set(ctx, value.getSingle(ctx));
        });

        registry.registerEffect("replace [all] %string% with %string% in %object%", arguments -> {
            Expression<?> from = arguments.get(0);
            Expression<?> to = arguments.get(1);
            VariableExpression variable = requireVariable(arguments.get(2));
            return ctx -> {
                String text = Renderer.toDisplay(variable.getSingle(ctx));
                variable.set(ctx, text.replace(Renderer.toDisplay(from.getSingle(ctx)),
                        Renderer.toDisplay(to.getSingle(ctx))));
            };
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

        registry.registerEffect("(exit|stop) [the] [current] loop", arguments -> ctx -> {
            throw BreakSignal.INSTANCE;
        });
        registry.registerEffect("(continue|next loop iteration)", arguments -> ctx -> {
            throw ContinueSignal.INSTANCE;
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

    /**
     * Builds a coloured component from text: MiniMessage ({@code <red>}…) when it contains tags,
     * otherwise legacy {@code &} colour codes.
     */
    private static Component colored(String text) {
        if (text.indexOf('<') >= 0 && text.indexOf('>') >= 0) {
            try {
                return MiniMessage.miniMessage().deserialize(text);
            } catch (RuntimeException ignored) {
                // not valid MiniMessage — fall back to legacy
            }
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
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
