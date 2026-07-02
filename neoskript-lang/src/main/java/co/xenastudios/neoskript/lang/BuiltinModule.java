package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Arguments;
import co.xenastudios.neoskript.api.syntax.ChangeMode;
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
import co.xenastudios.neoskript.lang.type.ParticleEffect;
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

import java.util.ArrayList;
import java.util.List;
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
        registerParticleSyntax(registry);
        registerHashAndVectorSyntax(registry);
        registerSlotSyntax(registry);
        registerClassInfoSyntax(registry);
        registerPotionSyntax(registry);
        registerEquipSyntax(registry);
        registerBlocksSyntax(registry);
        registerVisibilitySyntax(registry);
        registerLogSyntax(registry);
        registerEntitiesSyntax(registry);
        registerAnvilAndNamedSyntax(registry);
        registerPersistentDataSyntax(registry);
        // Machine-generated syntax batches (see the co.xenastudios.neoskript.lang.generated package).
        co.xenastudios.neoskript.lang.generated.GeneratedSyntax.registerAll(registry);
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
        types.register(new co.xenastudios.neoskript.lang.type.RegistryType<>(
                "damagetype", org.bukkit.damage.DamageType.class, () -> org.bukkit.Registry.DAMAGE_TYPE));
        types.register(new co.xenastudios.neoskript.lang.type.RegistryType<>(
                "potiontype", org.bukkit.potion.PotionType.class, () -> org.bukkit.Registry.POTION));
        types.register(new co.xenastudios.neoskript.lang.type.RegistryType<>(
                "structuretype", org.bukkit.generator.structure.StructureType.class,
                () -> org.bukkit.Registry.STRUCTURE_TYPE));
        types.register(new co.xenastudios.neoskript.lang.type.RegistryType<>(
                "musicinstrument", org.bukkit.MusicInstrument.class, () -> org.bukkit.Registry.INSTRUMENT));
        types.register(new co.xenastudios.neoskript.lang.type.RegistryType<>(
                "cattype", org.bukkit.entity.Cat.Type.class, () -> org.bukkit.Registry.CAT_VARIANT));
        types.register(new co.xenastudios.neoskript.lang.type.RegistryType<>(
                "frogvariant", org.bukkit.entity.Frog.Variant.class, () -> org.bukkit.Registry.FROG_VARIANT));
        co.xenastudios.neoskript.lang.type.GeneratedTypes.register(types);

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
        // The current element inside filter/map/sort/transform (`%objects% transformed using [input * 2]`).
        registry.registerExpression("input", Object.class, arguments -> new NamedLocalExpression("input"));
        // The running accumulator inside `%objects% reduced with [accumulator + input]`.
        registry.registerExpression("accumulator", Object.class, arguments -> new NamedLocalExpression("accumulator"));
        registry.registerExpression("[the] reduced value", Object.class, arguments -> new NamedLocalExpression("accumulator"));
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

        registry.registerExpression("[a] random number (from|between) %number% (to|and) %number%", Object.class,
                arguments -> random(arguments.get(0), arguments.get(1), false));
        registry.registerExpression("[a] random integer (from|between) %number% (to|and) %number%", Object.class,
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

        // Changeable numeric properties (gettable + set/add/remove/reset via the generic changer).
        numericProperty(registry, "saturation", Player.class,
                Player::getSaturation, (p, v) -> p.setSaturation((float) v), 5);
        numericProperty(registry, "exhaustion", Player.class,
                Player::getExhaustion, (p, v) -> p.setExhaustion((float) v), 0);
        numericProperty(registry, "(fly speed|flight speed)", Player.class,
                Player::getFlySpeed, (p, v) -> p.setFlySpeed((float) Math.max(-1, Math.min(1, v))), 0.1);
        numericProperty(registry, "absorption", LivingEntity.class,
                LivingEntity::getAbsorptionAmount, (e, v) -> e.setAbsorptionAmount(Math.max(0, v)), 0);
        numericProperty(registry, "(no damage ticks|invulnerability ticks)", LivingEntity.class,
                e -> (double) e.getNoDamageTicks(), (e, v) -> e.setNoDamageTicks((int) v), 0);
        numericProperty(registry, "fall distance", org.bukkit.entity.Entity.class,
                e -> (double) e.getFallDistance(), (e, v) -> e.setFallDistance((float) v), 0);
        numericProperty(registry, "fire ticks", org.bukkit.entity.Entity.class,
                e -> (double) e.getFireTicks(), (e, v) -> e.setFireTicks((int) v), 0);
        numericProperty(registry, "(remaining air|air level)", LivingEntity.class,
                e -> (double) e.getRemainingAir(), (e, v) -> e.setRemainingAir((int) v), 300);
        numericProperty(registry, "(maximum air|max air)", LivingEntity.class,
                e -> (double) e.getMaximumAir(), (e, v) -> e.setMaximumAir((int) v), 300);
        numericProperty(registry, "total (experience|exp|xp)", Player.class,
                p -> (double) p.getTotalExperience(), (p, v) -> p.setTotalExperience((int) Math.max(0, v)), 0);

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
        registry.registerExpression("(midpoint (of|between)|middle of) %location% and %location%", Object.class,
                arguments -> {
                    Expression<?> a = arguments.get(0);
                    Expression<?> b = arguments.get(1);
                    return new ComputedExpression(ctx -> {
                        Location first = toLocation(a.getSingle(ctx));
                        Location second = toLocation(b.getSingle(ctx));
                        return (first == null || second == null) ? null
                                : first.clone().add(second).multiply(0.5);
                    });
                });
        registry.registerExpression("vector (from|between) %location% (to|and) %location%", Object.class,
                arguments -> {
                    Expression<?> a = arguments.get(0);
                    Expression<?> b = arguments.get(1);
                    return new ComputedExpression(ctx -> {
                        Location first = toLocation(a.getSingle(ctx));
                        Location second = toLocation(b.getSingle(ctx));
                        return (first == null || second == null) ? null
                                : second.toVector().subtract(first.toVector());
                    });
                });
        registry.registerExpression("difficulty of %world%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof World w ? w.getDifficulty() : null);
        });
        registry.registerExpression("[world] spawn [(point|location)] of %world%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof World w ? w.getSpawnLocation() : null);
        });
        registry.registerExpression("[world] seed of %world%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof World w ? (double) w.getSeed() : null);
        });
        // --- event values: read the current Bukkit event, null outside the matching event ---
        eventValue(registry, "[the] damage", ctx ->
                event(ctx) instanceof org.bukkit.event.entity.EntityDamageEvent e ? e.getDamage() : null);
        eventValue(registry, "[the] final damage", ctx ->
                event(ctx) instanceof org.bukkit.event.entity.EntityDamageEvent e ? e.getFinalDamage() : null);
        eventValue(registry, "[the] damage cause", ctx ->
                event(ctx) instanceof org.bukkit.event.entity.EntityDamageEvent e ? e.getCause() : null);
        eventValue(registry, "[the] (attacker|damager)", ctx ->
                event(ctx) instanceof org.bukkit.event.entity.EntityDamageByEntityEvent e ? e.getDamager() : null);
        eventValue(registry, "[the] (victim|damaged entity)", ctx ->
                event(ctx) instanceof org.bukkit.event.entity.EntityDamageEvent e ? e.getEntity() : null);
        eventValue(registry, "[the] death message", ctx ->
                event(ctx) instanceof org.bukkit.event.entity.PlayerDeathEvent e ? e.getDeathMessage() : null);
        settableEventValue(registry, "[the] join message",
                ctx -> event(ctx) instanceof org.bukkit.event.player.PlayerJoinEvent e ? e.getJoinMessage() : null,
                (ev, v) -> {
                    if (ev instanceof org.bukkit.event.player.PlayerJoinEvent e) {
                        e.setJoinMessage(v == null ? null
                                : org.bukkit.ChatColor.translateAlternateColorCodes('&', String.valueOf(v)));
                    }
                });
        settableEventValue(registry, "[the] (quit|leave) message",
                ctx -> event(ctx) instanceof org.bukkit.event.player.PlayerQuitEvent e ? e.getQuitMessage() : null,
                (ev, v) -> {
                    if (ev instanceof org.bukkit.event.player.PlayerQuitEvent e) {
                        e.setQuitMessage(v == null ? null
                                : org.bukkit.ChatColor.translateAlternateColorCodes('&', String.valueOf(v)));
                    }
                });
        eventValue(registry, "[the] spawn[ing] reason", ctx ->
                event(ctx) instanceof org.bukkit.event.entity.CreatureSpawnEvent e ? e.getSpawnReason() : null);
        eventValue(registry, "[the] (clicked|interacted) block", ctx ->
                event(ctx) instanceof org.bukkit.event.player.PlayerInteractEvent e ? e.getClickedBlock() : null);
        eventValue(registry, "[the] dropped (exp|experience|xp)", ctx ->
                event(ctx) instanceof org.bukkit.event.entity.EntityDeathEvent e ? (double) e.getDroppedExp() : null);
        eventValue(registry, "[the] (level|new level)", ctx ->
                event(ctx) instanceof org.bukkit.event.player.PlayerLevelChangeEvent e ? (double) e.getNewLevel() : null);
        eventValue(registry, "[the] (new|future) food level", ctx ->
                event(ctx) instanceof org.bukkit.event.entity.FoodLevelChangeEvent e ? (double) e.getFoodLevel() : null);
        eventValue(registry, "[the] (breeder|breeding mother|bred mother)", ctx ->
                event(ctx) instanceof org.bukkit.event.entity.EntityBreedEvent e ? e.getBreeder() : null);
        eventValue(registry, "[the] (sign|written) line[s]", ctx ->
                event(ctx) instanceof org.bukkit.event.block.SignChangeEvent e ? e.getLines() : null);
        eventValue(registry, "[the] hatching (number|amount)", ctx ->
                event(ctx) instanceof org.bukkit.event.player.PlayerEggThrowEvent e ? (double) e.getNumHatches() : null);
        eventValue(registry, "[the] respawn[ing] reason", ctx ->
                event(ctx) instanceof org.bukkit.event.player.PlayerRespawnEvent e ? e.getRespawnReason() : null);
        eventValue(registry, "[the] regrow[n] [health] amount", ctx ->
                event(ctx) instanceof org.bukkit.event.entity.EntityRegainHealthEvent e ? e.getAmount() : null);
        eventValue(registry, "[the] (xp|experience) [orb] amount", ctx ->
                event(ctx) instanceof org.bukkit.event.player.PlayerExpChangeEvent e ? (double) e.getAmount() : null);

        registry.registerExpression("[the] ping of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof Player p ? p.getPing() : null);
        });
        registry.registerExpression("%object%'[s] ping", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof Player p ? p.getPing() : null);
        });
        // Directional locations: absolute (north/above/…) and facing-relative (in front of/…).
        registerDirection(registry, "above|over|on top of", 0, 1, 0, false);
        registerDirection(registry, "below|under[neath]|beneath", 0, -1, 0, false);
        registerDirection(registry, "north[ward[s]]|to the north", 0, 0, -1, false);
        registerDirection(registry, "south[ward[s]]|to the south", 0, 0, 1, false);
        registerDirection(registry, "east[ward[s]]|to the east", 1, 0, 0, false);
        registerDirection(registry, "west[ward[s]]|to the west", -1, 0, 0, false);
        registerDirection(registry, "in front of|forward[s]|ahead of", 1, 0, 0, true);
        registerDirection(registry, "behind|in back of", -1, 0, 0, true);
        registerDirection(registry, "to the left of|left of", 0, 0, 1, true);
        registerDirection(registry, "to the right of|right of", 0, 0, -1, true);
        registry.registerExpression("%number% (exp|xp|experience) [point[s]]", Object.class, arguments -> {
            Expression<?> amount = arguments.get(0);
            return new ComputedExpression(ctx -> amount.getSingle(ctx) instanceof Number n
                    ? new co.xenastudios.neoskript.lang.type.Experience(n.intValue()) : null);
        });
        registry.registerExpression("[the] time of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof World w
                    ? co.xenastudios.neoskript.lang.type.WorldTime.ofTicks(w.getTime()) : null);
        });
        registry.registerExpression("[the] weather (in|of) %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                World w = o instanceof World world ? world : (o instanceof Player p ? p.getWorld() : null);
                return w == null ? null : (w.isThundering() ? org.bukkit.WeatherType.DOWNFALL
                        : w.hasStorm() ? org.bukkit.WeatherType.DOWNFALL : org.bukkit.WeatherType.CLEAR);
            });
        });
        registry.registerExpression("[the] hotbar slot [(index|number)] of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof Player p
                    ? p.getInventory().getHeldItemSlot() : null);
        });
        // The item on a player's cursor — readable and settable.
        registry.registerExpression("[the] cursor slot of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new Expression<Object>() {
                @Override
                public Object[] getAll(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                    Object v = getSingle(ctx);
                    return v == null ? new Object[0] : new Object[]{v};
                }

                @Override
                public Object getSingle(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                    return src.getSingle(ctx) instanceof Player p ? p.getItemOnCursor() : null;
                }

                @Override
                public Class<Object> returnType() {
                    return Object.class;
                }

                @Override
                public boolean isSingle() {
                    return true;
                }

                @Override
                public Class<?>[] acceptChange(ChangeMode mode) {
                    return mode == ChangeMode.SET ? new Class<?>[]{Object.class} : null;
                }

                @Override
                public void change(co.xenastudios.neoskript.api.runtime.TriggerContext ctx, Object[] delta, ChangeMode mode) {
                    if (mode == ChangeMode.SET && src.getSingle(ctx) instanceof Player p
                            && delta != null && delta.length > 0 && delta[0] instanceof ItemStack item) {
                        p.setItemOnCursor(item);
                    }
                }
            };
        });
        registry.registerExpression("[the] (tool|held item|weapon) of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof LivingEntity le
                    && le.getEquipment() != null ? le.getEquipment().getItemInMainHand() : null);
        });
        registry.registerExpression("[the] target[ed entity] of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof org.bukkit.entity.Mob mob
                    ? mob.getTarget() : null);
        });
        registry.registerExpression("[all [of the]] items (in|of|contained in) %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.inventory.Inventory inv)) {
                    return new Object[0];
                }
                return java.util.Arrays.stream(inv.getContents())
                        .filter(java.util.Objects::nonNull).toArray();
            });
        });
        registry.registerExpression("[the] colo[u]r (from|of) hex[adecimal] [code] %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> {
                if (!(src.getSingle(ctx) instanceof String hex)) {
                    return null;
                }
                try {
                    return org.bukkit.Color.fromRGB(Integer.parseInt(hex.trim().replace("#", ""), 16));
                } catch (NumberFormatException e) {
                    return null;
                }
            });
        });
        registry.registerExpression("[the] (colo[u]red|formatted) %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof String str
                    ? org.bukkit.ChatColor.translateAlternateColorCodes('&', str) : null);
        });
        registry.registerExpression("[the] (uncolo[u]red|unformatted|plain) %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof String str
                    ? org.bukkit.ChatColor.stripColor(
                            org.bukkit.ChatColor.translateAlternateColorCodes('&', str)) : null);
        });
        registry.registerExpression("[the] (nearest|closest) %object% (relative to|to) %object%", Object.class, arguments -> {
            Expression<?> type = arguments.get(0);
            Expression<?> center = arguments.get(1);
            return new ComputedExpression(ctx -> {
                Object c = center.getSingle(ctx);
                org.bukkit.Location loc = c instanceof org.bukkit.Location l ? l
                        : (c instanceof Entity e ? e.getLocation() : null);
                if (loc == null || loc.getWorld() == null
                        || !(type.getSingle(ctx) instanceof org.bukkit.entity.EntityType et)) {
                    return null;
                }
                Entity best = null;
                double bestDistance = Double.MAX_VALUE;
                for (Entity entity : loc.getWorld().getEntities()) {
                    if (entity.getType() != et) {
                        continue;
                    }
                    double distance = entity.getLocation().distanceSquared(loc);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = entity;
                    }
                }
                return best;
            });
        });
        registry.registerExpression("[all [of the]] string colo[u]r[s]", Object.class, arguments ->
                new ComputedListExpression(ctx -> java.util.Arrays.stream(org.bukkit.ChatColor.values())
                        .map(c -> "&" + c.getChar()).toArray()));
        // Date/time arithmetic. Dates are millisecond timestamps (matching `now`); durations are timespans.
        registry.registerExpression("[the] time since %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> {
                Long ms = dateMillis(src.getSingle(ctx));
                return ms == null ? null : co.xenastudios.neoskript.core.runtime.Timespan
                        .ofMillis(Math.max(0, System.currentTimeMillis() - ms));
            });
        });
        registry.registerExpression("[the] time until %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> {
                Long ms = dateMillis(src.getSingle(ctx));
                return ms == null ? null : co.xenastudios.neoskript.core.runtime.Timespan
                        .ofMillis(Math.max(0, ms - System.currentTimeMillis()));
            });
        });
        registry.registerExpression("%object% (ago|in the past)", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx)
                    instanceof co.xenastudios.neoskript.core.runtime.Timespan t
                    ? (double) (System.currentTimeMillis() - t.millis()) : null);
        });
        registry.registerExpression("%object% (later|from now|in the future)", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx)
                    instanceof co.xenastudios.neoskript.core.runtime.Timespan t
                    ? (double) (System.currentTimeMillis() + t.millis()) : null);
        });
        registry.registerExpression("unix date of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof Number n
                    ? (double) (long) (n.doubleValue() * 1000) : null);
        });
        registry.registerExpression("[the] last login [time] of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof org.bukkit.OfflinePlayer p
                    ? (double) p.getLastLogin() : null);
        });
        registry.registerExpression("[the] first login [time] of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof org.bukkit.OfflinePlayer p
                    ? (double) p.getFirstPlayed() : null);
        });
        // Timespan-valued expressions.
        registry.registerExpression("(time played|play[ ]time) of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof Player p
                    ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(
                            p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE))
                    : null);
        });
        registry.registerExpression("[the] [item] cooldown of %object% for %object%", Object.class, arguments -> {
            Expression<?> item = arguments.get(0);
            Expression<?> who = arguments.get(1);
            return new ComputedExpression(ctx -> {
                org.bukkit.Material mat = material(item.getSingle(ctx));
                return mat != null && who.getSingle(ctx) instanceof Player p
                        ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(p.getCooldown(mat))
                        : null;
            });
        });
        registry.registerExpression("[the] fire burn[ing] (time|duration) of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof Entity e
                    ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(e.getFireTicks()) : null);
        });
        registry.registerExpression("[the] (potion duration|potion length) of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof org.bukkit.potion.PotionEffect pe
                    ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(pe.getDuration()) : null);
        });
        registry.registerExpression("(display|nick|chat|custom)[ ]name[s] of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof Player p) {
                    return p.getDisplayName();
                }
                if (o instanceof Entity e) {
                    return e.getCustomName();
                }
                return o instanceof ItemStack it && it.getItemMeta() != null && it.getItemMeta().hasDisplayName()
                        ? it.getItemMeta().getDisplayName() : null;
            });
        });
        registry.registerExpression("[the] loot[ ]table[s] of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.loot.Lootable lootable) {
                    return lootable.getLootTable();
                }
                return o instanceof org.bukkit.block.Block b && b.getState() instanceof org.bukkit.loot.Lootable l
                        ? l.getLootTable() : null;
            });
        });
        registry.registerExpression("[the] [enchant[ment]] level of %object% (on|of) %object%", Object.class, arguments -> {
            Expression<?> ench = arguments.get(0);
            Expression<?> item = arguments.get(1);
            return new ComputedExpression(ctx -> ench.getSingle(ctx) instanceof org.bukkit.enchantments.Enchantment en
                    && item.getSingle(ctx) instanceof ItemStack it ? it.getEnchantmentLevel(en) : null);
        });
        registry.registerExpression("[a] random uuid", Object.class,
                arguments -> new ComputedExpression(ctx -> java.util.UUID.randomUUID()));
        registry.registerExpression("normalize[d] %vector%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof Vector v ? v.clone().normalize() : null);
        });
        registry.registerExpression("world[ ]border (size|diameter) of %world%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof World w ? w.getWorldBorder().getSize() : null);
        });
        registry.registerExpression("(max[imum] players|maximum player count)", Object.class,
                arguments -> new ComputedExpression(ctx -> (double) Bukkit.getMaxPlayers()));
        registry.registerExpression("(online player[s] count|number of online players|online player amount)",
                Object.class, arguments -> new ComputedExpression(ctx -> (double) Bukkit.getOnlinePlayers().size()));
        registry.registerExpression("inventor(y|ies) of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof org.bukkit.inventory.InventoryHolder h ? h.getInventory() : null);
        });
        registry.registerExpression("[the] (ip|IP)[(-| )address[es]] of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> {
                if (src.getSingle(ctx) instanceof Player p && p.getAddress() != null) {
                    return p.getAddress().getAddress().getHostAddress();
                }
                return null;
            });
        });
        registry.registerExpression("[the] enchantments of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedListExpression(ctx ->
                    src.getSingle(ctx) instanceof ItemStack item ? item.getEnchantments().keySet().toArray() : new Object[0]);
        });
        registry.registerExpression("[the] item flags of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedListExpression(ctx -> {
                if (src.getSingle(ctx) instanceof ItemStack item && item.hasItemMeta()) {
                    return item.getItemMeta().getItemFlags().toArray();
                }
                return new Object[0];
            });
        });
        registry.registerExpression("[the] (inverse|opposite) of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof Boolean b ? !b : null);
        });
        registry.registerExpression("[the] item [inside] of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof org.bukkit.entity.Item item ? item.getItemStack() : null);
        });
        registry.registerExpression("(attached|hit) block of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof org.bukkit.entity.AbstractArrow ar ? ar.getAttachedBlock() : null);
        });
        registry.registerExpression("[the] [block] hardness of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    material(src.getSingle(ctx)) instanceof org.bukkit.Material m ? (double) m.getHardness() : null);
        });
        registry.registerExpression("explosive (yield|radius|size|power) of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof org.bukkit.entity.Explosive e ? (double) e.getYield() : null);
        });
        registry.registerExpression("[custom] model data of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> {
                if (src.getSingle(ctx) instanceof ItemStack item && item.hasItemMeta()
                        && item.getItemMeta().hasCustomModelData()) {
                    return (double) item.getItemMeta().getCustomModelData();
                }
                return null;
            });
        });
        registry.registerExpression("[the] facing of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof Entity e ? e.getFacing() : null);
        });
        registry.registerExpression("hex[adecimal] code of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof org.bukkit.Color c
                            ? String.format("#%06x", c.asRGB()) : null);
        });
        registry.registerExpression("first empty slot[s] of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof org.bukkit.inventory.Inventory inv ? (double) inv.firstEmpty() : null);
        });
        registry.registerExpression("age of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof org.bukkit.entity.Ageable a ? (double) a.getAge() : null);
        });
        registry.registerExpression("(damage value|durability) of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> {
                if (src.getSingle(ctx) instanceof ItemStack item && item.hasItemMeta()
                        && item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg) {
                    return (double) dmg.getDamage();
                }
                return null;
            });
        });
        registry.registerExpression("colo[u]r of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof org.bukkit.material.Colorable c ? c.getColor() : null);
        });
        registry.registerExpression("[the] item[[ ]stack] (amount|size) of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof ItemStack item ? (double) item.getAmount() : null);
        });
        registry.registerExpression("yaw of %location%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> {
                Location l = toLocation(src.getSingle(ctx));
                return l == null ? null : (double) l.getYaw();
            });
        });
        registry.registerExpression("pitch of %location%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> {
                Location l = toLocation(src.getSingle(ctx));
                return l == null ? null : (double) l.getPitch();
            });
        });
        registry.registerExpression("[the] lore of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedListExpression(ctx -> {
                if (src.getSingle(ctx) instanceof ItemStack item && item.hasItemMeta()
                        && item.getItemMeta().hasLore()) {
                    return item.getItemMeta().getLore().toArray();
                }
                return new Object[0];
            });
        });
        registry.registerExpression("[the] (owner|tamer) of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof Tameable t ? t.getOwner() : null);
        });
        registry.registerExpression("[the] target[ed] block of %player%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx ->
                    src.getSingle(ctx) instanceof Player p ? p.getTargetBlockExact(100) : null);
        });
        registry.registerExpression("max[imum] durability of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof ItemStack item) {
                    return (double) item.getType().getMaxDurability();
                }
                return o instanceof org.bukkit.Material mat ? (double) mat.getMaxDurability() : null;
            });
        });

        gameMode(registry, "survival", GameMode.SURVIVAL);
        gameMode(registry, "creative", GameMode.CREATIVE);
        gameMode(registry, "adventure", GameMode.ADVENTURE);
        gameMode(registry, "spectator", GameMode.SPECTATOR);
    }

    /**
     * The particle-configuration syntax: the count / offset / distribution / speed properties of a
     * particle, and the {@code %particles% with an offset/distribution/velocity/speed} builders. Each
     * property is settable and mutates the underlying {@link ParticleEffect} in place; each builder
     * returns fresh copies, leaving the source untouched (mirroring Skript).
     */
    private static void registerParticleSyntax(SyntaxRegistry registry) {
        // particle count — clamped 0..1000; count == 0 flips offset/extra to velocity/speed semantics.
        particleProperty(registry, "particle count of %particles%", "%particles%'[s] particle count",
                p -> (double) p.count(),
                (p, delta, mode) -> {
                    double d = delta.length > 0 && delta[0] instanceof Number n ? n.doubleValue() : 0;
                    int next = switch (mode) {
                        case SET -> (int) d;
                        case ADD -> p.count() + (int) d;
                        case REMOVE -> p.count() - (int) d;
                        default -> 1; // RESET → Bukkit default
                    };
                    p.count(Math.max(0, Math.min(1000, next)));
                });
        // particle offset — the raw offset X/Y/Z vector.
        particleProperty(registry, "particle offset of %particles%", "%particles%'[s] particle offset",
                ParticleEffect::offset,
                (p, delta, mode) -> p.offset(mode == ChangeMode.SET && delta.length > 0
                        && delta[0] instanceof Vector v ? v : new Vector(0, 0, 0)));
        // particle distribution — the same field as offset, but null while count == 0, and setting it
        // forces count back to 1 so the offset is read as a spread again.
        particleProperty(registry, "particle distribution of %particles%",
                "%particles%'[s] particle distribution",
                ParticleEffect::distribution,
                (p, delta, mode) -> p.distribution(mode == ChangeMode.SET && delta.length > 0
                        && delta[0] instanceof Vector v ? v : new Vector(0, 0, 0)));
        // particle speed / extra value.
        particleProperty(registry, "(particle speed [value]|extra value) of %particles%",
                "%particles%'[s] (particle speed [value]|extra value)",
                ParticleEffect::extra,
                (p, delta, mode) -> {
                    double d = delta.length > 0 && delta[0] instanceof Number n ? n.doubleValue() : 0;
                    p.extra(switch (mode) {
                        case SET -> d;
                        case ADD -> p.extra() + d;
                        case REMOVE -> p.extra() - d;
                        default -> 0; // RESET
                    });
                });

        // Builders — copy each source particle, apply one field, return the copies.
        particleBuilder(registry, "%particles% with [an] offset [of] %vector%",
                (copy, value) -> { if (value instanceof Vector v) copy.offset(v); });
        particleBuilder(registry, "%particles% with [a] distribution [of] %vector%",
                (copy, value) -> { if (value instanceof Vector v) copy.distribution(v); });
        particleBuilder(registry, "%directionalparticles% with [a] velocity [of] %vector%",
                (copy, value) -> { if (value instanceof Vector v) copy.velocity(v); });
        particleBuilder(registry,
                "%particles% with ([a] particle speed [value]|[an] extra value) [of] %number%",
                (copy, value) -> { if (value instanceof Number n) copy.extra(n.doubleValue()); });
    }

    /**
     * String hashing and vector-construction expressions:
     * {@code %strings% hashed with <algorithm>}, {@code vector from yaw %n% and pitch %n%}, and
     * {@code vector from %directions%}. All are pure value transforms (faithful to Skript's formulas).
     */
    private static void registerHashAndVectorSyntax(SyntaxRegistry registry) {
        // %strings% hashed with MD5/SHA-256/SHA-384/SHA-512 — lowercase hex of the UTF-8 bytes.
        for (String algorithm : new String[]{"MD5", "SHA-256", "SHA-384", "SHA-512"}) {
            registry.registerExpression("%strings% hash[ed] with " + algorithm, Object.class, arguments -> {
                Expression<?> source = arguments.get(0);
                return new ComputedListExpression(ctx -> {
                    java.security.MessageDigest digest;
                    try {
                        digest = java.security.MessageDigest.getInstance(algorithm);
                    } catch (java.security.NoSuchAlgorithmException impossible) {
                        return new Object[0];
                    }
                    List<Object> out = new ArrayList<>();
                    for (Object value : source.getAll(ctx)) {
                        byte[] bytes = Renderer.toDisplay(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        out.add(java.util.HexFormat.of().withLowerCase().formatHex(digest.digest(bytes)));
                    }
                    return out.toArray();
                });
            });
        }

        // vector from yaw %number% and pitch %number% — Skript-convention yaw/pitch (ExprYawPitch).
        registry.registerExpression("[a] [new] vector (from|with) yaw %number% and pitch %number%",
                Object.class, arguments -> {
                    Expression<?> yaw = arguments.get(0);
                    Expression<?> pitch = arguments.get(1);
                    return new ComputedExpression(ctx -> {
                        Object y = yaw.getSingle(ctx);
                        Object p = pitch.getSingle(ctx);
                        if (!(y instanceof Number yn) || !(p instanceof Number pn)) {
                            return null;
                        }
                        return vectorFromSkriptYawPitch(yn.floatValue(), pn.floatValue());
                    });
                });
        // Note: `vector from %directions%` is intentionally NOT registered — its pattern is ambiguous
        // with both `%number% blocks <direction>` and the comma form `vector from %o%, %o%, %o%`, and
        // without Skript's value-type filtering on the argument slot it would shadow those. Left as a
        // future item pending type-aware argument matching.
    }

    /**
     * The {@code slot %numbers% of %inventory%} expression — a settable reference to one or more
     * inventory slots. Reading yields each slot ({@link co.xenastudios.neoskript.lang.type.Slot},
     * which renders as its contents); {@code SET}/{@code DELETE} write the item into the inventory.
     */
    private static void registerSlotSyntax(SyntaxRegistry registry) {
        // Slot expressions register BEFORE the possessive inventory expression below: the latter is a
        // greedy wildcard (`%objects%'s inventory`) that would otherwise capture "slot N of player"
        // out of "slot N of player's inventory". Lower registration order = higher match priority.
        registry.registerExpression("[the] slot[s] %numbers% of %inventory%", Object.class,
                arguments -> slotExpression(arguments.get(1), arguments.get(0)));
        registry.registerExpression("%inventory%'[s] slot[s] %numbers%", Object.class,
                arguments -> slotExpression(arguments.get(0), arguments.get(1)));

        // inventory of %inventoryholders% — a holder's (player/block) inventory.
        registry.registerExpression("inventor(y|ies) of %objects%", Object.class,
                arguments -> inventoryExpression(arguments.get(0)));
        registry.registerExpression("%objects%'[s] inventor(y|ies)", Object.class,
                arguments -> inventoryExpression(arguments.get(0)));

        // indices/positions of a value within a list — registered BEFORE `index of %slots%` so the
        // "... in %objects%" form wins over the slot form (which would greedily capture "value in list").
        registry.registerExpression("[the] [first] (index|position) of %object% in %objects%", Object.class,
                arguments -> indicesOfValue(arguments.get(0), arguments.get(1), 0));
        registry.registerExpression("[the] last (index|position) of %object% in %objects%", Object.class,
                arguments -> indicesOfValue(arguments.get(0), arguments.get(1), 1));
        registry.registerExpression("[the] [all] (indices|positions) of %object% in %objects%", Object.class,
                arguments -> indicesOfValue(arguments.get(0), arguments.get(1), 2));

        // furnace ore/input slot(s) of the given furnace block(s), settable.
        registry.registerExpression("[the] (ore|input) slot[s] of %blocks%", Object.class,
                arguments -> blockInventorySlot(arguments.get(0), 0));
        // brewing stand first bottle slot(s), settable.
        registry.registerExpression("[the] [brewing stand['s]] (first|1st) bottle slot[s] of %blocks%",
                Object.class, arguments -> blockInventorySlot(arguments.get(0), 0));

        // index of %slots% — the slot's position within its inventory.
        registry.registerExpression("[(raw|unique)] index of %slots%", Object.class,
                arguments -> slotIndexExpression(arguments.get(0)));
        registry.registerExpression("%slots%'[s] [(raw|unique)] index", Object.class,
                arguments -> slotIndexExpression(arguments.get(0)));
    }

    /**
     * {@code index of %value% in %list%} — the list key(s) whose value equals the target.
     * {@code mode}: 0 = first match, 1 = last match, 2 = all matches. The list must be a list variable.
     */
    private static Expression<Object> indicesOfValue(Expression<?> value, Expression<?> listExpr, int mode) {
        return new ComputedListExpression(ctx -> {
            if (!(listExpr instanceof VariableExpression list) || !list.isList()) {
                return new Object[0];
            }
            Object target = value.getSingle(ctx);
            Object[] keys = list.listKeys(ctx);
            Object[] values = list.getAll(ctx);
            List<Object> matches = new ArrayList<>();
            for (int i = 0; i < Math.min(keys.length, values.length); i++) {
                if (valuesEqual(values[i], target)) {
                    matches.add(keys[i]);
                }
            }
            if (matches.isEmpty()) {
                return new Object[0];
            }
            return switch (mode) {
                case 0 -> new Object[]{matches.get(0)};
                case 1 -> new Object[]{matches.get(matches.size() - 1)};
                default -> matches.toArray();
            };
        });
    }

    /** Equality that treats numeric values by their double value (so 1 and 1.0 match). */
    private static boolean valuesEqual(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        return java.util.Objects.equals(a, b);
    }

    /** A settable slot at a fixed index of each block's container inventory (e.g. a furnace ore slot). */
    private static Expression<Object> blockInventorySlot(Expression<?> blocks, int index) {
        return new Expression<Object>() {
            private List<co.xenastudios.neoskript.lang.type.Slot> slots(
                    co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                List<co.xenastudios.neoskript.lang.type.Slot> out = new ArrayList<>();
                for (Object value : blocks.getAll(ctx)) {
                    if (value instanceof org.bukkit.block.Block block
                            && block.getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
                        out.add(new co.xenastudios.neoskript.lang.type.Slot(holder.getInventory(), index));
                    }
                }
                return out;
            }

            @Override
            public Object[] getAll(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                return slots(ctx).toArray();
            }

            @Override
            public Object getSingle(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                List<co.xenastudios.neoskript.lang.type.Slot> slots = slots(ctx);
                return slots.isEmpty() ? null : slots.get(0);
            }

            @Override
            public Class<Object> returnType() {
                return Object.class;
            }

            @Override
            public boolean isSingle() {
                return blocks.isSingle();
            }

            @Override
            public Class<?>[] acceptChange(ChangeMode mode) {
                return switch (mode) {
                    case SET, DELETE, RESET -> new Class<?>[]{Object.class};
                    default -> null;
                };
            }

            @Override
            public void change(co.xenastudios.neoskript.api.runtime.TriggerContext ctx,
                               Object[] delta, ChangeMode mode) {
                ItemStack item = mode == ChangeMode.SET
                        ? toItemStack(delta != null && delta.length > 0 ? delta[0] : null) : null;
                for (co.xenastudios.neoskript.lang.type.Slot slot : slots(ctx)) {
                    slot.setItem(item);
                }
            }
        };
    }

    /** {@code index of %slots%} — each slot's inventory index as a number. */
    private static Expression<Object> slotIndexExpression(Expression<?> source) {
        return new ComputedListExpression(ctx -> {
            List<Object> out = new ArrayList<>();
            for (Object value : source.getAll(ctx)) {
                if (value instanceof co.xenastudios.neoskript.lang.type.Slot slot) {
                    out.add((double) slot.index());
                }
            }
            return out.toArray();
        });
    }

    /**
     * The {@code [persistent] data value %string% of %objects%} expression — reads/writes a custom
     * value in the holder's {@link org.bukkit.persistence.PersistentDataContainer}. The PDC type is
     * inferred from the value on set (string/number/boolean) and probed on read.
     */
    private static void registerPersistentDataSyntax(SyntaxRegistry registry) {
        registry.registerExpression("[persistent] data (value|tag) %string% of %objects%", Object.class,
                arguments -> {
                    Expression<?> key = arguments.get(0);
                    Expression<?> holders = arguments.get(1);
                    return new Expression<Object>() {
                        private org.bukkit.NamespacedKey key(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                            org.bukkit.plugin.Plugin owner = Bukkit.getPluginManager().getPlugin("NeoSkript");
                            Object raw = key.getSingle(ctx);
                            if (owner == null || raw == null) {
                                return null;
                            }
                            String name = Renderer.toDisplay(raw).toLowerCase(Locale.ROOT)
                                    .replaceAll("[^a-z0-9._-]", "_");
                            return name.isEmpty() ? null : new org.bukkit.NamespacedKey(owner, name);
                        }

                        private java.util.List<org.bukkit.persistence.PersistentDataHolder> holders(
                                co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                            java.util.List<org.bukkit.persistence.PersistentDataHolder> out = new ArrayList<>();
                            for (Object value : holders.getAll(ctx)) {
                                if (value instanceof org.bukkit.persistence.PersistentDataHolder holder) {
                                    out.add(holder);
                                } else if (value instanceof org.bukkit.block.Block block
                                        && block.getState() instanceof org.bukkit.persistence.PersistentDataHolder held) {
                                    out.add(held);
                                }
                            }
                            return out;
                        }

                        @Override
                        public Object[] getAll(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                            org.bukkit.NamespacedKey namespaced = key(ctx);
                            if (namespaced == null) {
                                return new Object[0];
                            }
                            List<Object> out = new ArrayList<>();
                            for (org.bukkit.persistence.PersistentDataHolder holder : holders(ctx)) {
                                Object read = readPersistent(holder.getPersistentDataContainer(), namespaced);
                                if (read != null) {
                                    out.add(read);
                                }
                            }
                            return out.toArray();
                        }

                        @Override
                        public Object getSingle(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                            Object[] all = getAll(ctx);
                            return all.length > 0 ? all[0] : null;
                        }

                        @Override
                        public Class<Object> returnType() {
                            return Object.class;
                        }

                        @Override
                        public boolean isSingle() {
                            return holders.isSingle();
                        }

                        @Override
                        public Class<?>[] acceptChange(ChangeMode mode) {
                            return switch (mode) {
                                case SET, DELETE, RESET -> new Class<?>[]{Object.class};
                                default -> null;
                            };
                        }

                        @Override
                        public void change(co.xenastudios.neoskript.api.runtime.TriggerContext ctx,
                                           Object[] delta, ChangeMode mode) {
                            org.bukkit.NamespacedKey namespaced = key(ctx);
                            if (namespaced == null) {
                                return;
                            }
                            Object value = mode == ChangeMode.SET && delta != null && delta.length > 0
                                    ? delta[0] : null;
                            for (org.bukkit.persistence.PersistentDataHolder holder : holders(ctx)) {
                                org.bukkit.persistence.PersistentDataContainer pdc =
                                        holder.getPersistentDataContainer();
                                if (value == null) {
                                    pdc.remove(namespaced);
                                } else {
                                    writePersistent(pdc, namespaced, value);
                                }
                                if (holder instanceof org.bukkit.block.BlockState state) {
                                    state.update();
                                }
                            }
                        }
                    };
                });
    }

    /** Reads a persistent value, probing the common PDC types in turn. */
    private static Object readPersistent(org.bukkit.persistence.PersistentDataContainer pdc,
                                         org.bukkit.NamespacedKey key) {
        if (pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
            return pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
        }
        if (pdc.has(key, org.bukkit.persistence.PersistentDataType.DOUBLE)) {
            return pdc.get(key, org.bukkit.persistence.PersistentDataType.DOUBLE);
        }
        if (pdc.has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
            return pdc.get(key, org.bukkit.persistence.PersistentDataType.BYTE) != 0;
        }
        return null;
    }

    /** Writes a persistent value, choosing the PDC type from the value (string/number/boolean). */
    private static void writePersistent(org.bukkit.persistence.PersistentDataContainer pdc,
                                        org.bukkit.NamespacedKey key, Object value) {
        if (value instanceof Number number) {
            pdc.set(key, org.bukkit.persistence.PersistentDataType.DOUBLE, number.doubleValue());
        } else if (value instanceof Boolean bool) {
            pdc.set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) (bool ? 1 : 0));
        } else {
            pdc.set(key, org.bukkit.persistence.PersistentDataType.STRING, Renderer.toDisplay(value));
        }
    }

    /**
     * The anvil repair-cost expression (settable) and the {@code %itemtype% named %textcomponent%}
     * item builder.
     */
    private static void registerAnvilAndNamedSyntax(SyntaxRegistry registry) {
        registry.registerExpression("[anvil] [item] repair cost of %inventories%", Object.class,
                arguments -> anvilRepairCost(arguments.get(0), false));
        registry.registerExpression("[anvil] [item] max[imum] repair cost of %inventories%", Object.class,
                arguments -> anvilRepairCost(arguments.get(0), true));

        registry.registerExpression("%itemtype% (named|with name[s]) %textcomponent%", Object.class,
                arguments -> {
                    Expression<?> item = arguments.get(0);
                    Expression<?> name = arguments.get(1);
                    return new ComputedExpression(ctx -> {
                        ItemStack base = toItemStack(item.getSingle(ctx));
                        if (base == null) {
                            return null;
                        }
                        Object nameValue = name.getSingle(ctx);
                        net.kyori.adventure.text.Component component =
                                nameValue instanceof net.kyori.adventure.text.Component c
                                        ? c : net.kyori.adventure.text.Component.text(Renderer.toDisplay(nameValue));
                        ItemStack copy = base.clone();
                        copy.editMeta(meta -> meta.displayName(component));
                        return copy;
                    });
                });
    }

    /** A settable anvil repair-cost (or maximum repair-cost) over the given anvil inventories. */
    private static Expression<Object> anvilRepairCost(Expression<?> inventories, boolean maximum) {
        return new Expression<Object>() {
            @Override
            public Object[] getAll(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                List<Object> out = new ArrayList<>();
                for (Object value : inventories.getAll(ctx)) {
                    if (value instanceof org.bukkit.inventory.AnvilInventory anvil) {
                        out.add((double) (maximum ? anvil.getMaximumRepairCost() : anvil.getRepairCost()));
                    }
                }
                return out.toArray();
            }

            @Override
            public Object getSingle(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                Object[] all = getAll(ctx);
                return all.length > 0 ? all[0] : null;
            }

            @Override
            public Class<Object> returnType() {
                return Object.class;
            }

            @Override
            public boolean isSingle() {
                return inventories.isSingle();
            }

            @Override
            public Class<?>[] acceptChange(ChangeMode mode) {
                return mode == ChangeMode.SET ? new Class<?>[]{Object.class} : null;
            }

            @Override
            public void change(co.xenastudios.neoskript.api.runtime.TriggerContext ctx,
                               Object[] delta, ChangeMode mode) {
                if (mode != ChangeMode.SET || delta == null || delta.length == 0
                        || !(delta[0] instanceof Number number)) {
                    return;
                }
                for (Object value : inventories.getAll(ctx)) {
                    if (value instanceof org.bukkit.inventory.AnvilInventory anvil) {
                        if (maximum) {
                            anvil.setMaximumRepairCost(number.intValue());
                        } else {
                            anvil.setRepairCost(number.intValue());
                        }
                    }
                }
            }
        };
    }

    /** The most recent entity spawned by the {@code spawn}/{@code summon} effect (Bukkit is single-threaded). */
    private static volatile Entity lastSpawnedEntity;

    /**
     * The {@code entities of type[s] %entitydatas% [in world[s] %worlds%]} expression — every loaded
     * entity of the given type(s), across the named worlds (all worlds when none are given).
     */
    private static void registerEntitiesSyntax(SyntaxRegistry registry) {
        registry.registerExpression(
                "[(all [[of] the]|the)] entities of type[s] %entitydatas% [(in|of) [world[s]] %worlds%]",
                Object.class, arguments -> {
                    Expression<?> types = arguments.get(0);
                    Expression<?> worlds = arguments.get(1);
                    return new ComputedListExpression(ctx -> {
                        java.util.Set<org.bukkit.entity.EntityType> wanted = new java.util.HashSet<>();
                        for (Object value : types.getAll(ctx)) {
                            if (value instanceof org.bukkit.entity.EntityType type) {
                                wanted.add(type);
                            }
                        }
                        if (wanted.isEmpty()) {
                            return new Object[0];
                        }
                        List<World> scope = new ArrayList<>();
                        if (worlds != null) {
                            for (Object value : worlds.getAll(ctx)) {
                                if (value instanceof World world) {
                                    scope.add(world);
                                }
                            }
                        } else {
                            scope.addAll(Bukkit.getWorlds());
                        }
                        List<Object> out = new ArrayList<>();
                        for (World world : scope) {
                            for (Entity entity : world.getEntities()) {
                                if (wanted.contains(entity.getType())) {
                                    out.add(entity);
                                }
                            }
                        }
                        return out.toArray();
                    });
                });

        // the last entity spawned by the spawn/summon effect, when it matches the requested type(s).
        registry.registerExpression("[the] [last[ly]] spawned %entitydatas%", Object.class, arguments -> {
            Expression<?> types = arguments.get(0);
            return new ComputedExpression(ctx -> {
                Entity spawned = lastSpawnedEntity;
                if (spawned == null || !spawned.isValid()) {
                    return null;
                }
                for (Object value : types.getAll(ctx)) {
                    if (value instanceof org.bukkit.entity.EntityType type && spawned.getType() == type) {
                        return spawned;
                    }
                }
                return null;
            });
        });
    }

    /**
     * The {@code log %strings% [(to|in) [file[s]] %strings%]} effect: writes each message to the
     * plugin's console log, or appends it (one line each) to the named file(s) under the plugin's
     * {@code logs/} directory (a {@code .log} extension is added when the name has none).
     */
    private static void registerLogSyntax(SyntaxRegistry registry) {
        registry.registerEffect("log %strings%", arguments -> {
            Expression<?> messages = arguments.get(0);
            return ctx -> {
                org.bukkit.plugin.Plugin owner = Bukkit.getPluginManager().getPlugin("NeoSkript");
                if (owner == null) {
                    return;
                }
                for (Object message : messages.getAll(ctx)) {
                    owner.getLogger().info(Renderer.toDisplay(message));
                }
            };
        });
        registry.registerEffect("log %strings% (to|in) [file[s]] %strings%", arguments -> {
            Expression<?> messages = arguments.get(0);
            Expression<?> files = arguments.get(1);
            return ctx -> {
                org.bukkit.plugin.Plugin owner = Bukkit.getPluginManager().getPlugin("NeoSkript");
                if (owner == null) {
                    return;
                }
                for (Object fileValue : files.getAll(ctx)) {
                    String name = Renderer.toDisplay(fileValue);
                    if (!name.contains(".")) {
                        name += ".log";
                    }
                    java.io.File file = new java.io.File(new java.io.File(owner.getDataFolder(), "logs"), name);
                    try {
                        java.io.File parent = file.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }
                        try (java.io.FileWriter writer = new java.io.FileWriter(file, true)) {
                            for (Object message : messages.getAll(ctx)) {
                                writer.write(Renderer.toDisplay(message) + System.lineSeparator());
                            }
                        }
                    } catch (java.io.IOException ignored) {
                        // best-effort logging; a failed file write should not abort the trigger
                    }
                }
            };
        });
    }

    /**
     * Entity visibility: {@code hide/reveal %entities% [(from|for) %players%]} toggles whether each
     * entity is visible to the given viewers (all online players when none are named), via Bukkit's
     * per-player {@code hideEntity}/{@code showEntity}.
     */
    private static void registerVisibilitySyntax(SyntaxRegistry registry) {
        registry.registerEffect("hide %entities% [(from|for) %-players%]",
                a -> entityVisibility(a.get(0), a.get(1), false));
        registry.registerEffect("reveal %entities% [(to|for|from) %-players%]",
                a -> entityVisibility(a.get(0), a.get(1), true));
    }

    private static co.xenastudios.neoskript.api.syntax.Effect entityVisibility(
            Expression<?> entities, Expression<?> viewersExpr, boolean reveal) {
        return ctx -> {
            org.bukkit.plugin.Plugin owner = Bukkit.getPluginManager().getPlugin("NeoSkript");
            if (owner == null) {
                return;
            }
            List<Player> viewers = new ArrayList<>();
            if (viewersExpr != null) {
                for (Object value : viewersExpr.getAll(ctx)) {
                    if (value instanceof Player player) {
                        viewers.add(player);
                    }
                }
            } else {
                viewers.addAll(Bukkit.getOnlinePlayers());
            }
            for (Object value : entities.getAll(ctx)) {
                if (value instanceof Entity entity) {
                    for (Player viewer : viewers) {
                        if (reveal) {
                            viewer.showEntity(owner, entity);
                        } else {
                            viewer.hideEntity(owner, entity);
                        }
                    }
                }
            }
        };
    }

    /**
     * The {@code blocks between %location% and %location%} family — every block in the cuboid spanned
     * by two corner locations (inclusive). The two locations must share a world.
     */
    private static void registerBlocksSyntax(SyntaxRegistry registry) {
        registry.registerExpression("[(all [[of] the]|the)] blocks between %location% and %location%",
                Object.class, arguments -> blocksBetween(arguments.get(0), arguments.get(1)));
        registry.registerExpression("[(all [[of] the]|the)] blocks within %location% and %location%",
                Object.class, arguments -> blocksBetween(arguments.get(0), arguments.get(1)));
        registry.registerExpression("[(all [[of] the]|the)] blocks from %location% to %location%",
                Object.class, arguments -> blocksBetween(arguments.get(0), arguments.get(1)));

        registry.registerExpression("[(all [[of] the]|the)] blocks in radius %number% (of|around) %location%",
                Object.class, arguments -> blocksInRadius(arguments.get(1), arguments.get(0)));
        registry.registerExpression("[(all [[of] the]|the)] blocks around %location% in radius %number%",
                Object.class, arguments -> blocksInRadius(arguments.get(0), arguments.get(1)));
    }

    /** Every block whose integer offset from the centre is within {@code radius} (Skript's sphere). */
    private static Expression<Object> blocksInRadius(Expression<?> center, Expression<?> radiusExpr) {
        return new ComputedListExpression(ctx -> {
            Location origin = toLocation(center.getSingle(ctx));
            if (origin == null || origin.getWorld() == null
                    || !(radiusExpr.getSingle(ctx) instanceof Number number)) {
                return new Object[0];
            }
            double radius = number.doubleValue();
            double radiusSquared = radius * radius;
            int bound = (int) Math.ceil(radius);
            World world = origin.getWorld();
            int cx = origin.getBlockX();
            int cy = origin.getBlockY();
            int cz = origin.getBlockZ();
            List<Object> out = new ArrayList<>();
            for (int x = -bound; x <= bound; x++) {
                for (int y = -bound; y <= bound; y++) {
                    for (int z = -bound; z <= bound; z++) {
                        if (x * x + y * y + z * z <= radiusSquared) {
                            out.add(world.getBlockAt(cx + x, cy + y, cz + z));
                        }
                    }
                }
            }
            return out.toArray();
        });
    }

    /** Every block in the axis-aligned cuboid between two corner locations (inclusive). */
    private static Expression<Object> blocksBetween(Expression<?> first, Expression<?> second) {
        return new ComputedListExpression(ctx -> {
            Location a = toLocation(first.getSingle(ctx));
            Location b = toLocation(second.getSingle(ctx));
            if (a == null || b == null || a.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
                return new Object[0];
            }
            World world = a.getWorld();
            int minX = Math.min(a.getBlockX(), b.getBlockX());
            int maxX = Math.max(a.getBlockX(), b.getBlockX());
            int minY = Math.min(a.getBlockY(), b.getBlockY());
            int maxY = Math.max(a.getBlockY(), b.getBlockY());
            int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
            int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
            List<Object> out = new ArrayList<>();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        out.add(world.getBlockAt(x, y, z));
                    }
                }
            }
            return out.toArray();
        });
    }

    /**
     * The {@code equip [%livingentities%] with %itemtypes%} effect: places each item in the entity's
     * matching equipment slot (helmet/chestplate/leggings/boots by material, shield in the off hand,
     * anything else in the main hand). With no explicit entity it targets the event's player.
     */
    private static void registerEquipSyntax(SyntaxRegistry registry) {
        // armour items worn by an entity (the non-empty armour slots).
        registry.registerExpression("[the] armo[u]r[s] [item[s]] of %livingentities%", Object.class,
                arguments -> {
                    Expression<?> entities = arguments.get(0);
                    return new ComputedListExpression(ctx -> {
                        List<Object> out = new ArrayList<>();
                        for (Object value : entities.getAll(ctx)) {
                            if (value instanceof LivingEntity living && living.getEquipment() != null) {
                                for (ItemStack piece : living.getEquipment().getArmorContents()) {
                                    if (piece != null && piece.getType() != org.bukkit.Material.AIR) {
                                        out.add(piece);
                                    }
                                }
                            }
                        }
                        return out.toArray();
                    });
                });

        registry.registerEffect("equip [%livingentities%] with %itemtypes%", arguments -> {
            Expression<?> entities = arguments.get(0);
            Expression<?> items = arguments.get(1);
            return ctx -> {
                List<LivingEntity> targets = new ArrayList<>();
                if (entities != null) {
                    for (Object value : entities.getAll(ctx)) {
                        if (value instanceof LivingEntity living) {
                            targets.add(living);
                        }
                    }
                } else if (event(ctx) instanceof org.bukkit.event.player.PlayerEvent playerEvent) {
                    targets.add(playerEvent.getPlayer());
                }
                for (LivingEntity target : targets) {
                    org.bukkit.inventory.EntityEquipment equipment = target.getEquipment();
                    if (equipment == null) {
                        continue;
                    }
                    for (Object value : items.getAll(ctx)) {
                        ItemStack item = toItemStack(value);
                        if (item != null) {
                            equipment.setItem(equipmentSlotFor(item.getType()), item);
                        }
                    }
                }
            };
        });
    }

    /** Maps an item's material to the equipment slot Skript's {@code equip} places it in. */
    private static org.bukkit.inventory.EquipmentSlot equipmentSlotFor(org.bukkit.Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET") || name.endsWith("_HEAD") || name.endsWith("SKULL")
                || name.equals("CARVED_PUMPKIN")) {
            return org.bukkit.inventory.EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) {
            return org.bukkit.inventory.EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return org.bukkit.inventory.EquipmentSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return org.bukkit.inventory.EquipmentSlot.FEET;
        }
        if (name.equals("SHIELD")) {
            return org.bukkit.inventory.EquipmentSlot.OFF_HAND;
        }
        return org.bukkit.inventory.EquipmentSlot.HAND;
    }

    /**
     * Potion-effect modifier effects: {@code make %potioneffects% [not] ambient/infinite} and
     * {@code show/hide [potion] icon[s]/particles of %potioneffects%}. Bukkit's {@link PotionEffect} is
     * immutable, so each rebuilds the effect with one field changed and reassigns it in the source
     * variable (mirroring Skript's mutable {@code SkriptPotionEffect} wrapper). "Not infinite" resets
     * the duration to Skript's default of 600 ticks, matching the wrapper.
     */
    private static void registerPotionSyntax(SyntaxRegistry registry) {
        // The value being built inside a `set {_p} to a potion effect ...:` section. It aliases the
        // reserved section-value local, so the potion modifiers (which need a variable) work on it.
        registry.registerExpression("[the] [created] [potion] effect", Object.class,
                a -> co.xenastudios.neoskript.core.expression.VariableExpression.local(
                        co.xenastudios.neoskript.core.expression.VariableExpression.SECTION_VALUE));
        // The damage source being built inside a `set {_d} to a ... damage source:` section.
        registry.registerExpression("[the] created damage source", Object.class,
                a -> co.xenastudios.neoskript.core.expression.VariableExpression.local(
                        co.xenastudios.neoskript.core.expression.VariableExpression.SECTION_VALUE));

        registry.registerEffect("make %skriptpotioneffects% ambient",
                a -> modifyPotionEffects(a.get(0), pe -> withAmbient(pe, true)));
        registry.registerEffect("make %skriptpotioneffects% not ambient",
                a -> modifyPotionEffects(a.get(0), pe -> withAmbient(pe, false)));
        registry.registerEffect("make %skriptpotioneffects% (infinite|permanent)",
                a -> modifyPotionEffects(a.get(0), pe -> withInfinite(pe, true)));
        registry.registerEffect("make %skriptpotioneffects% not (infinite|permanent)",
                a -> modifyPotionEffects(a.get(0), pe -> withInfinite(pe, false)));
        registry.registerEffect("show [the] [potion] icon[s] (of|for) %skriptpotioneffects%",
                a -> modifyPotionEffects(a.get(0), pe -> withIcon(pe, true)));
        registry.registerEffect("hide [the] [potion] icon[s] (of|for) %skriptpotioneffects%",
                a -> modifyPotionEffects(a.get(0), pe -> withIcon(pe, false)));
        registry.registerEffect("show [the] [potion] particles (of|for) %skriptpotioneffects%",
                a -> modifyPotionEffects(a.get(0), pe -> withParticles(pe, true)));
        registry.registerEffect("hide [the] [potion] particles (of|for) %skriptpotioneffects%",
                a -> modifyPotionEffects(a.get(0), pe -> withParticles(pe, false)));
    }

    /** Rebuilds and reassigns each potion effect in a variable source through {@code transform}. */
    private static co.xenastudios.neoskript.api.syntax.Effect modifyPotionEffects(
            Expression<?> source, java.util.function.UnaryOperator<PotionEffect> transform) {
        VariableExpression variable = requireVariable(source);
        return ctx -> {
            if (variable.isList()) {
                Object[] values = variable.getAll(ctx);
                variable.delete(ctx);
                for (Object value : values) {
                    variable.addToList(ctx, value instanceof PotionEffect pe ? transform.apply(pe) : value);
                }
            } else if (variable.getSingle(ctx) instanceof PotionEffect pe) {
                variable.set(ctx, transform.apply(pe));
            }
        };
    }

    private static PotionEffect withAmbient(PotionEffect pe, boolean value) {
        return new PotionEffect(pe.getType(), pe.getDuration(), pe.getAmplifier(),
                value, pe.hasParticles(), pe.hasIcon());
    }

    private static PotionEffect withParticles(PotionEffect pe, boolean value) {
        return new PotionEffect(pe.getType(), pe.getDuration(), pe.getAmplifier(),
                pe.isAmbient(), value, pe.hasIcon());
    }

    private static PotionEffect withIcon(PotionEffect pe, boolean value) {
        return new PotionEffect(pe.getType(), pe.getDuration(), pe.getAmplifier(),
                pe.isAmbient(), pe.hasParticles(), value);
    }

    private static PotionEffect withInfinite(PotionEffect pe, boolean infinite) {
        return new PotionEffect(pe.getType(), infinite ? PotionEffect.INFINITE_DURATION : 600,
                pe.getAmplifier(), pe.isAmbient(), pe.hasParticles(), pe.hasIcon());
    }

    /**
     * Expressions that consume a {@code %classinfo%} type-reference argument (resolved by the parser to
     * a {@link co.xenastudios.neoskript.api.type.Type}): {@code %string% parsed as %type%} and
     * {@code %type% value of %objects%}. Parsing runs the type's own parser on the string; the value
     * form keeps objects already of the type and otherwise re-parses their display form.
     */
    private static void registerClassInfoSyntax(SyntaxRegistry registry) {
        registry.registerExpression("%string% parsed as %classinfo%", Object.class, arguments -> {
            Expression<?> text = arguments.get(0);
            Expression<?> typeArg = arguments.get(1);
            return new ComputedExpression(ctx -> {
                Object value = text.getSingle(ctx);
                if (value == null
                        || !(typeArg.getSingle(ctx) instanceof co.xenastudios.neoskript.api.type.Type<?> type)) {
                    return null;
                }
                return type.parse(Renderer.toDisplay(value)).orElse(null);
            });
        });
        registry.registerExpression("[the] %classinfo% value of %objects%", Object.class,
                arguments -> valueExpression(arguments.get(0), arguments.get(1)));
        registry.registerExpression("%objects%'[s] %classinfo% value", Object.class,
                arguments -> valueExpression(arguments.get(1), arguments.get(0)));
    }

    /** {@code %type% value of %objects%} — each object as the target type (kept if already so, else re-parsed). */
    private static Expression<Object> valueExpression(Expression<?> typeArg, Expression<?> objects) {
        return new ComputedListExpression(ctx -> {
            if (!(typeArg.getSingle(ctx) instanceof co.xenastudios.neoskript.api.type.Type<?> type)) {
                return new Object[0];
            }
            List<Object> out = new ArrayList<>();
            for (Object value : objects.getAll(ctx)) {
                if (value == null) {
                    continue;
                }
                if (type.typeClass().isInstance(value)) {
                    out.add(value);
                } else {
                    type.parse(Renderer.toDisplay(value)).ifPresent(out::add);
                }
            }
            return out.toArray();
        });
    }

    /** {@code inventory of %inventoryholders%} — resolves each holder to its {@link org.bukkit.inventory.Inventory}. */
    private static Expression<Object> inventoryExpression(Expression<?> source) {
        return new ComputedListExpression(ctx -> {
            List<Object> out = new ArrayList<>();
            for (Object value : source.getAll(ctx)) {
                if (value instanceof org.bukkit.inventory.InventoryHolder holder) {
                    out.add(holder.getInventory());
                } else if (value instanceof org.bukkit.block.Block block
                        && block.getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
                    out.add(holder.getInventory());
                }
            }
            return out.toArray();
        });
    }

    /** Builds the settable slot expression from an inventory source and a set of slot indices. */
    private static Expression<Object> slotExpression(Expression<?> inventoryExpr, Expression<?> indexExpr) {
        return new Expression<Object>() {
            private List<co.xenastudios.neoskript.lang.type.Slot> slots(
                    co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                List<co.xenastudios.neoskript.lang.type.Slot> out = new ArrayList<>();
                if (!(inventoryExpr.getSingle(ctx) instanceof org.bukkit.inventory.Inventory inventory)) {
                    return out;
                }
                for (Object index : indexExpr.getAll(ctx)) {
                    if (index instanceof Number number) {
                        out.add(new co.xenastudios.neoskript.lang.type.Slot(inventory, number.intValue()));
                    }
                }
                return out;
            }

            @Override
            public Object[] getAll(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                return slots(ctx).toArray();
            }

            @Override
            public Object getSingle(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                List<co.xenastudios.neoskript.lang.type.Slot> slots = slots(ctx);
                return slots.isEmpty() ? null : slots.get(0);
            }

            @Override
            public Class<Object> returnType() {
                return Object.class;
            }

            @Override
            public boolean isSingle() {
                return indexExpr.isSingle();
            }

            @Override
            public Class<?>[] acceptChange(ChangeMode mode) {
                return switch (mode) {
                    case SET, DELETE, RESET -> new Class<?>[]{Object.class};
                    default -> null;
                };
            }

            @Override
            public void change(co.xenastudios.neoskript.api.runtime.TriggerContext ctx,
                               Object[] delta, ChangeMode mode) {
                ItemStack item = mode == ChangeMode.SET
                        ? toItemStack(delta != null && delta.length > 0 ? delta[0] : null) : null;
                for (co.xenastudios.neoskript.lang.type.Slot slot : slots(ctx)) {
                    slot.setItem(item);
                }
            }
        };
    }

    /** Coerces a value to an {@link ItemStack}: an item directly, or a Material as a single item. */
    private static ItemStack toItemStack(Object value) {
        if (value instanceof ItemStack item) {
            return item;
        }
        return value instanceof org.bukkit.Material material ? new ItemStack(material) : null;
    }

    private static final double DEG_TO_RAD = Math.PI / 180;

    /** Normalises a degree angle to {@code (-180, 180]} (Skript's {@code wrapAngleDeg}). */
    private static float wrapAngleDeg(float angle) {
        angle %= 360f;
        if (angle <= -180) {
            return angle + 360;
        }
        return angle > 180 ? angle - 360 : angle;
    }

    /**
     * Builds a unit-ish vector from a Skript-convention yaw and pitch, mirroring Skript's
     * {@code ExprVectorFromYawAndPitch}: convert Skript yaw/pitch to Bukkit yaw/pitch (a +90° yaw
     * offset and a negated pitch), then apply {@code ExprYawPitch.fromYawAndPitch}.
     */
    private static Vector vectorFromSkriptYawPitch(float skriptYaw, float skriptPitch) {
        float wrappedYaw = wrapAngleDeg(skriptYaw);
        float bukkitYaw = wrappedYaw > 270 ? wrappedYaw - 270 : wrappedYaw + 90;
        float bukkitPitch = -wrapAngleDeg(skriptPitch);
        double y = Math.sin(bukkitPitch * DEG_TO_RAD);
        double div = Math.cos(bukkitPitch * DEG_TO_RAD);
        double x = Math.cos(bukkitYaw * DEG_TO_RAD) * div;
        double z = Math.sin(bukkitYaw * DEG_TO_RAD) * div;
        return new Vector(x, y, z);
    }

    /** Applies a change to a {@link ParticleEffect} in place, given the delta values and mode. */
    @FunctionalInterface
    private interface ParticleChange {
        void apply(ParticleEffect effect, Object[] delta, ChangeMode mode);
    }

    /** Coerces a value to a {@link ParticleEffect}: a raw {@link org.bukkit.Particle} gets defaults. */
    private static ParticleEffect asParticleEffect(Object value) {
        if (value instanceof ParticleEffect effect) {
            return effect;
        }
        if (value instanceof org.bukkit.Particle raw) {
            return new ParticleEffect(raw);
        }
        return null;
    }

    /**
     * Registers a settable particle property under both a {@code <prop> of %particles%} and a
     * {@code %particles%'s <prop>} phrasing. Reading maps each source particle through {@code getter}
     * (dropping {@code null}s); {@code SET}/{@code ADD}/{@code REMOVE}/{@code RESET} route through
     * {@code changer}, mutating the referenced particles.
     */
    private static void particleProperty(SyntaxRegistry registry, String ofPattern, String possessivePattern,
                                         Function<ParticleEffect, Object> getter, ParticleChange changer) {
        co.xenastudios.neoskript.api.syntax.ExpressionFactory<Object> factory = arguments -> {
            Expression<?> source = arguments.get(0);
            return new Expression<Object>() {
                @Override
                public Object[] getAll(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                    List<Object> out = new ArrayList<>();
                    for (Object o : source.getAll(ctx)) {
                        ParticleEffect p = asParticleEffect(o);
                        if (p != null) {
                            Object v = getter.apply(p);
                            if (v != null) {
                                out.add(v);
                            }
                        }
                    }
                    return out.toArray();
                }

                @Override
                public Object getSingle(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                    Object[] all = getAll(ctx);
                    return all.length > 0 ? all[0] : null;
                }

                @Override
                public Class<Object> returnType() {
                    return Object.class;
                }

                @Override
                public boolean isSingle() {
                    return source.isSingle();
                }

                @Override
                public Class<?>[] acceptChange(ChangeMode mode) {
                    return switch (mode) {
                        case SET, ADD, REMOVE, RESET -> new Class<?>[]{Object.class};
                        default -> null;
                    };
                }

                @Override
                public void change(co.xenastudios.neoskript.api.runtime.TriggerContext ctx,
                                   Object[] delta, ChangeMode mode) {
                    Object[] d = delta == null ? new Object[0] : delta;
                    for (Object o : source.getAll(ctx)) {
                        if (o instanceof ParticleEffect p) {
                            changer.apply(p, d, mode);
                        }
                    }
                }
            };
        };
        registry.registerExpression(ofPattern, Object.class, factory);
        registry.registerExpression(possessivePattern, Object.class, factory);
    }

    /**
     * Registers a {@code %particles% with ...} builder: copies each source particle, applies
     * {@code apply} with the trailing value, and returns the copies (the source is left unchanged).
     */
    private static void particleBuilder(SyntaxRegistry registry, String pattern,
                                        java.util.function.BiConsumer<ParticleEffect, Object> apply) {
        registry.registerExpression(pattern, Object.class, arguments -> {
            Expression<?> source = arguments.get(0);
            Expression<?> value = arguments.get(1);
            return new Expression<Object>() {
                @Override
                public Object[] getAll(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                    Object v = value.getSingle(ctx);
                    List<Object> out = new ArrayList<>();
                    for (Object o : source.getAll(ctx)) {
                        ParticleEffect p = asParticleEffect(o);
                        if (p != null) {
                            ParticleEffect copy = p.copy();
                            apply.accept(copy, v);
                            out.add(copy);
                        }
                    }
                    return out.toArray();
                }

                @Override
                public Object getSingle(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                    Object[] all = getAll(ctx);
                    return all.length > 0 ? all[0] : null;
                }

                @Override
                public Class<Object> returnType() {
                    return Object.class;
                }

                @Override
                public boolean isSingle() {
                    return source.isSingle();
                }
            };
        });
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
        // `any of {list::*}` / `one of {list::*}` — a single random element.
        registry.registerExpression("(any [one]|one) of %objects%", Object.class,
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
        registry.registerExpression("alphabetically sorted %objects%", Object.class, arguments -> {
            Expression<?> source = arguments.get(0);
            return new ComputedListExpression(ctx -> {
                Object[] values = source.getAll(ctx).clone();
                java.util.Arrays.sort(values, (a, b) ->
                        Renderer.toDisplay(a).compareToIgnoreCase(Renderer.toDisplay(b)));
                return values;
            });
        });
        registry.registerExpression("shuffled %objects%", Object.class, arguments -> {
            Expression<?> source = arguments.get(0);
            return new ComputedListExpression(ctx -> {
                java.util.List<Object> values = new java.util.ArrayList<>(java.util.Arrays.asList(source.getAll(ctx)));
                java.util.Collections.shuffle(values);
                return values.toArray();
            });
        });
        registry.registerExpression("(nl|newline|new line)", Object.class,
                arguments -> new ComputedExpression(ctx -> "\n"));
        registry.registerExpression("%string% repeated %number% [time[s]]", Object.class, arguments -> {
            Expression<?> text = arguments.get(0);
            Expression<?> count = arguments.get(1);
            return new ComputedExpression(ctx -> {
                int times = (int) orZero(toNumber(count.getSingle(ctx)));
                return times <= 0 ? "" : Renderer.toDisplay(text.getSingle(ctx)).repeat(times);
            });
        });
        registry.registerExpression("substring of %string% (from|between) %number% (to|and) %number%", Object.class,
                arguments -> {
                    Expression<?> text = arguments.get(0);
                    Expression<?> from = arguments.get(1);
                    Expression<?> to = arguments.get(2);
                    return new ComputedExpression(ctx -> {
                        String s = Renderer.toDisplay(text.getSingle(ctx));
                        int a = Math.max(1, (int) orZero(toNumber(from.getSingle(ctx))));
                        int b = Math.min(s.length(), (int) orZero(toNumber(to.getSingle(ctx))));
                        return a > b ? "" : s.substring(a - 1, b); // Skript is 1-indexed, inclusive
                    });
                });
        registry.registerExpression("difference between %number% and %number%", Object.class, arguments -> {
            Expression<?> a = arguments.get(0);
            Expression<?> b = arguments.get(1);
            return new ComputedExpression(ctx -> {
                Double x = toNumber(a.getSingle(ctx));
                Double y = toNumber(b.getSingle(ctx));
                return (x == null || y == null) ? null : Math.abs(x - y);
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
        registry.registerCondition("%object% ((is|are) set|exist[s])", arguments -> isSet(arguments.get(0), true));

        // Date comparisons relative to the current moment.
        registry.registerCondition("%dates% (is|are) in the past", a -> datesVsNow(a.get(0), true, true));
        registry.registerCondition("%dates% (is|are)(n't| not) in the past", a -> datesVsNow(a.get(0), true, false));
        registry.registerCondition("%dates% (is|are) in the future", a -> datesVsNow(a.get(0), false, true));
        registry.registerCondition("%dates% (is|are)(n't| not) in the future", a -> datesVsNow(a.get(0), false, false));
        registry.registerCondition("%date% (was|were) more than %timespan% [ago]",
                a -> dateElapsed(a.get(0), a.get(1), true));
        registry.registerCondition("%date% (was|were) less than %timespan% [ago]",
                a -> dateElapsed(a.get(0), a.get(1), false));

        // Inventory capacity.
        registry.registerCondition(
                "%inventories% (can hold|ha(s|ve) [enough] space (for|to hold)) %itemtypes%",
                a -> canHold(a.get(0), a.get(1), true));
        registry.registerCondition(
                "%inventories% (can(no|')t hold|(ha(s|ve) not|ha(s|ve)n't|do[es]n't have) "
                        + "[enough] space (for|to hold)) %itemtypes%",
                a -> canHold(a.get(0), a.get(1), false));

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
        registry.registerCondition("%object% contain[s] %object%", arguments -> contains(arguments, true));
        registry.registerCondition("%string% start[s] with %string%", arguments -> affix(arguments, true));
        registry.registerCondition("%string% end[s] with %string%", arguments -> affix(arguments, false));
        registry.registerCondition("%string% match[es] %string%", arguments -> {
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
        registry.registerCondition("%player% is (in a vehicle|inside a vehicle|riding)",
                a -> playerIs(a.get(0), Player::isInsideVehicle, true));
        registry.registerCondition("%player% has permission %string%", a -> hasPermission(a, true));
        registry.registerCondition("%player% (doesn't have|does not have|lacks) permission %string%",
                a -> hasPermission(a, false));
        registry.registerCondition("%player% (has|have) %object%", a -> hasItem(a, true));
        registry.registerCondition("%player% (doesn't have|does not have|hasn't|haven't) %object%",
                a -> hasItem(a, false));
        co.xenastudios.neoskript.api.syntax.ConditionFactory holding = a -> {
            Expression<?> target = a.get(0);
            Expression<?> item = a.get(1);
            return ctx -> target.getSingle(ctx) instanceof Player player
                    && item.getSingle(ctx) instanceof ItemStack stack
                    && player.getInventory().getItemInMainHand().getType() == stack.getType();
        };
        registry.registerCondition("%player% is holding %object%", holding);
        registry.registerCondition("%player% (has|have) %object% in [main] hand", holding);
        registry.registerCondition("%world% is raining", a -> worldIs(a.get(0), true));
        registry.registerCondition("%world% (is not raining|isn't raining|is clear)", a -> worldIs(a.get(0), false));
        registry.registerCondition("%world% is thundering", a -> {
            Expression<?> target = a.get(0);
            return ctx -> target.getSingle(ctx) instanceof World w && w.isThundering();
        });
        registry.registerCondition("%object% (is|are) interactable", a -> {
            Expression<?> src = a.get(0);
            return ctx -> material(src.getSingle(ctx)) instanceof org.bukkit.Material m && m.isInteractable();
        });
        registry.registerCondition("%object% (is|are) occluding", a -> {
            Expression<?> src = a.get(0);
            return ctx -> material(src.getSingle(ctx)) instanceof org.bukkit.Material m && m.isOccluding();
        });
        registry.registerCondition("%object% (is|are) persistent", a -> {
            Expression<?> src = a.get(0);
            return ctx -> src.getSingle(ctx) instanceof Entity e && e.isPersistent();
        });
        registry.registerCondition("%object% (is|are) [not] poisoned", a -> {
            Expression<?> src = a.get(0);
            return ctx -> src.getSingle(ctx) instanceof LivingEntity le
                    && le.hasPotionEffect(org.bukkit.potion.PotionEffectType.POISON);
        });
        registry.registerCondition("%object% (is|are) [properly] saddled", a -> {
            Expression<?> src = a.get(0);
            return ctx -> src.getSingle(ctx) instanceof org.bukkit.entity.Steerable s && s.hasSaddle();
        });
        registry.registerCondition("plugin[s] %string% (is|are) enabled", a -> {
            Expression<?> src = a.get(0);
            return ctx -> Bukkit.getPluginManager().isPluginEnabled(Renderer.toDisplay(src.getSingle(ctx)));
        });
        registry.registerCondition("%object% (is|are) edible", a -> {
            Expression<?> src = a.get(0);
            return ctx -> material(src.getSingle(ctx)) instanceof org.bukkit.Material m && m.isEdible();
        });
        registry.registerCondition("%object% (is|are) flammable", a -> {
            Expression<?> src = a.get(0);
            return ctx -> material(src.getSingle(ctx)) instanceof org.bukkit.Material m && m.isFlammable();
        });
        registry.registerCondition("%object% (is|are) fuel", a -> {
            Expression<?> src = a.get(0);
            return ctx -> material(src.getSingle(ctx)) instanceof org.bukkit.Material m && m.isFuel();
        });
        registry.registerCondition("%object% (is|are) (charged|powered)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> src.getSingle(ctx) instanceof org.bukkit.entity.Creeper c && c.isPowered();
        });
        registry.registerCondition("%object% (is|are) redstone powered", a -> {
            Expression<?> src = a.get(0);
            return ctx -> src.getSingle(ctx) instanceof org.bukkit.block.Block b && b.isBlockPowered();
        });
        registry.registerCondition("running minecraft %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                if (!(src.getSingle(ctx) instanceof String version)) {
                    return false;
                }
                String actual = org.bukkit.Bukkit.getServer().getMinecraftVersion();
                return actual.equals(version) || actual.startsWith(version + ".");
            };
        });
        registry.registerCondition("%object% (is|are) spawnable", a -> {
            Expression<?> src = a.get(0);
            return ctx -> src.getSingle(ctx) instanceof org.bukkit.entity.EntityType et && et.isSpawnable();
        });
        registry.registerCondition("%object% (is|are) enchanted", a -> {
            Expression<?> src = a.get(0);
            return ctx -> src.getSingle(ctx) instanceof ItemStack it && it.getItemMeta() != null
                    && it.getItemMeta().hasEnchants();
        });
        registry.registerCondition("%object% (is|are) empty", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof org.bukkit.inventory.Inventory inv) {
                    return inv.isEmpty();
                }
                if (o instanceof String str) {
                    return str.isEmpty();
                }
                return o instanceof ItemStack it && it.getType().isAir();
            };
        });
        registry.registerCondition("%object% can see %object%", a -> {
            Expression<?> viewer = a.get(0);
            Expression<?> target = a.get(1);
            return ctx -> viewer.getSingle(ctx) instanceof Player p
                    && target.getSingle(ctx) instanceof Entity e && p.canSee(e);
        });
        registry.registerCondition("%object% (has|have) metadata %string%", a -> {
            Expression<?> holder = a.get(0);
            Expression<?> key = a.get(1);
            return ctx -> holder.getSingle(ctx) instanceof org.bukkit.metadata.Metadatable md
                    && key.getSingle(ctx) instanceof String k && md.hasMetadata(k);
        });
        registry.registerCondition("%object% (has|have) [item] cooldown [for] %object%", a -> {
            Expression<?> who = a.get(0);
            Expression<?> item = a.get(1);
            return ctx -> {
                org.bukkit.Material mat = material(item.getSingle(ctx));
                return who.getSingle(ctx) instanceof Player p && mat != null && p.hasCooldown(mat);
            };
        });
        registry.registerCondition("%object% can (age|grow (up|old[er]))", a -> {
            Expression<?> src = a.get(0);
            return ctx -> src.getSingle(ctx) instanceof org.bukkit.entity.Breedable br && !br.getAgeLock();
        });
        registry.registerCondition("%object% (is|are) alphanumeric", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o != null && Renderer.toDisplay(o).matches("[A-Za-z0-9]+");
            };
        });
        registry.registerCondition("%object% (is|are) [a] block", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                if (o instanceof ItemStack item) {
                    return item.getType().isBlock();
                }
                return o instanceof org.bukkit.Material mat && mat.isBlock();
            };
        });
        registry.registerCondition("%object% (has|have) [a[n]] [active] potion effect %string%", a -> {
            Expression<?> target = a.get(0);
            Expression<?> type = a.get(1);
            return ctx -> {
                if (!(target.getSingle(ctx) instanceof LivingEntity le)) {
                    return false;
                }
                org.bukkit.potion.PotionEffectType pet = potionEffect(Renderer.toDisplay(type.getSingle(ctx)));
                return pet != null && le.hasPotionEffect(pet);
            };
        });
        registry.registerCondition("%object% (has|have) [a] [direct] line of sight to %object%", a -> {
            Expression<?> target = a.get(0);
            Expression<?> other = a.get(1);
            return ctx -> target.getSingle(ctx) instanceof LivingEntity le
                    && other.getSingle(ctx) instanceof Entity e && le.hasLineOfSight(e);
        });
        registry.registerCondition("%object% (is|are) (sheared|shorn)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> src.getSingle(ctx) instanceof org.bukkit.entity.Shearable sh && sh.isSheared();
        });
        registry.registerCondition("%entity% is invisible", a -> entityIs(a.get(0), Entity::isInvisible, true));
        registry.registerCondition("%entity% is (visible|not invisible)",
                a -> entityIs(a.get(0), Entity::isInvisible, false));
        registry.registerCondition("%entity% is invulnerable", a -> entityIs(a.get(0), Entity::isInvulnerable, true));
        registry.registerCondition("%entity% is silent", a -> entityIs(a.get(0), Entity::isSilent, true));
        registry.registerCondition("%entity% is frozen", a -> entityIs(a.get(0), e -> e.getFreezeTicks() > 0, true));
        registry.registerCondition("%entity% (is a baby|is a child)",
                a -> entityIs(a.get(0), e -> e instanceof org.bukkit.entity.Ageable age && !age.isAdult(), true));
        registry.registerCondition("%entity% is [an] adult",
                a -> entityIs(a.get(0), e -> e instanceof org.bukkit.entity.Ageable age && age.isAdult(), true));
        registry.registerCondition("%entity% (has|have) [an] (ai|AI)",
                a -> entityIs(a.get(0), e -> e instanceof LivingEntity le && le.hasAI(), true));
        registry.registerCondition("%entity% can pick[ ]up items",
                a -> entityIs(a.get(0), e -> e instanceof LivingEntity le && le.getCanPickupItems(), true));
        registry.registerCondition("%entity% has scoreboard tag %string%", a -> {
            Expression<?> target = a.get(0);
            Expression<?> tag = a.get(1);
            return ctx -> target.getSingle(ctx) instanceof Entity entity
                    && entity.getScoreboardTags().contains(Renderer.toDisplay(tag.getSingle(ctx)));
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

    private static Condition entityIs(Expression<?> target, Predicate<Entity> test, boolean expected) {
        return ctx -> target.getSingle(ctx) instanceof Entity entity && test.test(entity) == expected;
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
        registry.registerEffect("play sound %string% at %location% [with volume %-number%] [with pitch %-number%]",
                arguments -> {
                    Expression<?> sound = arguments.get(0);
                    Expression<?> loc = arguments.get(1);
                    Expression<?> volume = arguments.get(2);
                    Expression<?> pitch = arguments.get(3);
                    return ctx -> {
                        Location at = toLocation(loc.getSingle(ctx));
                        if (at != null && at.getWorld() != null) {
                            String name = Renderer.toDisplay(sound.getSingle(ctx)).toLowerCase(Locale.ROOT);
                            float vol = volume == null ? 1f : (float) orZero(toNumber(volume.getSingle(ctx)));
                            float pit = pitch == null ? 1f : (float) orZero(toNumber(pitch.getSingle(ctx)));
                            at.getWorld().playSound(at, name, vol, pit);
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
        registry.registerEffect("make %entity% invisible", arguments ->
                entityEffect(arguments.get(0), e -> e.setInvisible(true)));
        registry.registerEffect("make %entity% visible", arguments ->
                entityEffect(arguments.get(0), e -> e.setInvisible(false)));
        registry.registerEffect("make %entity% invulnerable", arguments ->
                entityEffect(arguments.get(0), e -> e.setInvulnerable(true)));
        registry.registerEffect("make %entity% vulnerable", arguments ->
                entityEffect(arguments.get(0), e -> e.setInvulnerable(false)));
        registry.registerEffect("(silence %entity%|make %entity% silent)", arguments -> {
            Expression<?> a = arguments.get(0);
            Expression<?> b = arguments.get(1);
            Expression<?> target = a != null ? a : b;
            return entityEffect(target, e -> e.setSilent(true));
        });
        registry.registerEffect("(unsilence %entity%|make %entity% not silent)", arguments -> {
            Expression<?> a = arguments.get(0);
            Expression<?> b = arguments.get(1);
            Expression<?> target = a != null ? a : b;
            return entityEffect(target, e -> e.setSilent(false));
        });
        registry.registerEffect("(disable|remove) gravity for %entity%", arguments ->
                entityEffect(arguments.get(0), e -> e.setGravity(false)));
        registry.registerEffect("(enable|restore) gravity for %entity%", arguments ->
                entityEffect(arguments.get(0), e -> e.setGravity(true)));
        registry.registerEffect("push %entity% [(with|at|by)] [(force|velocity)] %vector%", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> velocity = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Entity entity && velocity.getSingle(ctx) instanceof Vector v) {
                    entity.setVelocity(entity.getVelocity().add(v));
                }
            };
        });
        // Directional push: `push %entity% upwards [at speed %number%]`, etc. Each direction is a fixed
        // unit vector scaled by the optional speed (default 1), added to the entity's velocity.
        pushDirection(registry, "up|upward[s]", 0, 1, 0);
        pushDirection(registry, "down|downward[s]", 0, -1, 0);
        pushDirection(registry, "north", 0, 0, -1);
        pushDirection(registry, "south", 0, 0, 1);
        pushDirection(registry, "east", 1, 0, 0);
        pushDirection(registry, "west", -1, 0, 0);
        registry.registerEffect("stop [all] sound[s] [(for|to) %player%]", arguments ->
                playerOrEventEffect(arguments.get(0), Player::stopAllSounds));
        registry.registerEffect("(clear|reset) [the] title[s] [(of|for|from) %player%]", arguments ->
                playerOrEventEffect(arguments.get(0), Player::resetTitle));
        registry.registerEffect("(force %player% to (say|chat)|make %player% (say|chat)) %string%", arguments -> {
            Expression<?> a = arguments.get(0);
            Expression<?> b = arguments.get(1);
            Expression<?> message = arguments.get(2);
            return ctx -> {
                Object target = a != null ? a.getSingle(ctx) : (b != null ? b.getSingle(ctx) : null);
                if (target instanceof Player player) {
                    player.chat(Renderer.toDisplay(message.getSingle(ctx)));
                }
            };
        });
        registry.registerEffect("(despawn|remove) %entity%", arguments ->
                entityEffect(arguments.get(0), Entity::remove));
        registry.registerEffect("make %entity% [a[n]] adult", arguments ->
                entityEffect(arguments.get(0), e -> {
                    if (e instanceof org.bukkit.entity.Ageable a) {
                        a.setAdult();
                    }
                }));
        registry.registerEffect("make %entity% [a] (baby|child)", arguments ->
                entityEffect(arguments.get(0), e -> {
                    if (e instanceof org.bukkit.entity.Ageable a) {
                        a.setBaby();
                    }
                }));
        registry.registerEffect("make %entity% incendiary", arguments ->
                entityEffect(arguments.get(0), e -> {
                    if (e instanceof org.bukkit.entity.Explosive ex) {
                        ex.setIsIncendiary(true);
                    }
                }));
        registry.registerEffect("make %entity% not incendiary", arguments ->
                entityEffect(arguments.get(0), e -> {
                    if (e instanceof org.bukkit.entity.Explosive ex) {
                        ex.setIsIncendiary(false);
                    }
                }));
        registry.registerEffect("make %entity% persistent", arguments ->
                entityEffect(arguments.get(0), e -> e.setPersistent(true)));
        registry.registerEffect("make %entity% not persist[ent]", arguments ->
                entityEffect(arguments.get(0), e -> e.setPersistent(false)));
        registry.registerEffect("enforce [the] [server] white[ ]list", arguments ->
                ctx -> Bukkit.setWhitelistEnforced(true));
        registry.registerEffect("unenforce [the] [server] white[ ]list", arguments ->
                ctx -> Bukkit.setWhitelistEnforced(false));
        registry.registerEffect("make %entity% (charged|powered)", arguments ->
                entityEffect(arguments.get(0), e -> {
                    if (e instanceof org.bukkit.entity.Creeper c) {
                        c.setPowered(true);
                    }
                }));
        registry.registerEffect("make %entity% (uncharged|unpowered|not charged|not powered)", arguments ->
                entityEffect(arguments.get(0), e -> {
                    if (e instanceof org.bukkit.entity.Creeper c) {
                        c.setPowered(false);
                    }
                }));
        registry.registerEffect("force %player% to respawn", arguments ->
                playerEffect(arguments.get(0), p -> p.spigot().respawn()));
        registry.registerEffect("make %object% left[(-| )]handed", arguments -> {
            Expression<?> src = arguments.get(0);
            return ctx -> {
                if (src.getSingle(ctx) instanceof org.bukkit.entity.Mob mob) {
                    mob.setLeftHanded(true);
                }
            };
        });
        registry.registerEffect("make %object% right[(-| )]handed", arguments -> {
            Expression<?> src = arguments.get(0);
            return ctx -> {
                if (src.getSingle(ctx) instanceof org.bukkit.entity.Mob mob) {
                    mob.setLeftHanded(false);
                }
            };
        });
        registry.registerEffect("make %object% attack %object%", arguments -> {
            Expression<?> attacker = arguments.get(0);
            Expression<?> victim = arguments.get(1);
            return ctx -> {
                if (attacker.getSingle(ctx) instanceof org.bukkit.entity.Mob mob
                        && victim.getSingle(ctx) instanceof Entity target) {
                    mob.attack(target);
                }
            };
        });
        registry.registerEffect("(leash|lead) %object% to %object%", arguments -> {
            Expression<?> ent = arguments.get(0);
            Expression<?> holder = arguments.get(1);
            return ctx -> {
                if (ent.getSingle(ctx) instanceof LivingEntity le && holder.getSingle(ctx) instanceof Entity h) {
                    try {
                        le.setLeashHolder(h);
                    } catch (IllegalArgumentException ignored) {
                        // not leashable
                    }
                }
            };
        });
        registry.registerEffect("break %object% [naturally]", arguments -> {
            Expression<?> src = arguments.get(0);
            return ctx -> {
                if (src.getSingle(ctx) instanceof org.bukkit.block.Block b) {
                    b.breakNaturally();
                }
            };
        });
        registry.registerEffect("(detonate|instantly explode) %entity%", arguments ->
                entityEffect(arguments.get(0), e -> {
                    if (e instanceof org.bukkit.entity.Creeper c) {
                        c.explode();
                    } else if (e instanceof org.bukkit.entity.Explosive ex) {
                        ex.getWorld().createExplosion(ex.getLocation(), ex.getYield());
                    }
                }));
        registry.registerEffect("close [the] inventory [(of|for)] %player%", arguments ->
                playerEffect(arguments.get(0), Player::closeInventory));
        registry.registerEffect("close %player%'s inventory", arguments ->
                playerEffect(arguments.get(0), Player::closeInventory));
        registry.registerEffect("make %player% wake up", arguments ->
                playerEffect(arguments.get(0), p -> p.wakeup(true)));
        registry.registerEffect("make %entity% swing [its] [main] hand", arguments ->
                entityEffect(arguments.get(0), e -> {
                    if (e instanceof LivingEntity le) {
                        le.swingMainHand();
                    }
                }));
        registry.registerEffect("make %entity% swing [its] off[ ]hand", arguments ->
                entityEffect(arguments.get(0), e -> {
                    if (e instanceof LivingEntity le) {
                        le.swingOffHand();
                    }
                }));
        registry.registerEffect("drop %object% at %location%", arguments -> {
            Expression<?> item = arguments.get(0);
            Expression<?> loc = arguments.get(1);
            return ctx -> {
                Location where = toLocation(loc.getSingle(ctx));
                Object what = item.getSingle(ctx);
                if (where != null && where.getWorld() != null && what instanceof ItemStack stack) {
                    where.getWorld().dropItem(where, stack);
                }
            };
        });
        registry.registerEffect("(make|force) %player% [to] (fly|start flying)", arguments ->
                playerEffect(arguments.get(0), player -> {
                    player.setAllowFlight(true);
                    player.setFlying(true);
                }));
        registry.registerEffect("make %player% stop flying", arguments ->
                playerEffect(arguments.get(0), player -> player.setFlying(false)));
        registry.registerEffect("(allow|enable) (flight|flying|fly) for %player%", arguments ->
                playerEffect(arguments.get(0), player -> player.setAllowFlight(true)));
        registry.registerEffect("(disallow|disable) (flight|flying|fly) for %player%", arguments ->
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
        registry.registerEffect("(strike|create) [a] lightning [bolt] at %location%", arguments -> {
            Expression<?> loc = arguments.get(0);
            return ctx -> {
                Location l = toLocation(loc.getSingle(ctx));
                if (l != null && l.getWorld() != null) {
                    l.getWorld().strikeLightning(l);
                }
            };
        });
        registry.registerEffect("set [world] spawn [(point|location)] of %world% to %location%", arguments -> {
            Expression<?> world = arguments.get(0);
            Expression<?> loc = arguments.get(1);
            return ctx -> {
                Location l = toLocation(loc.getSingle(ctx));
                if (world.getSingle(ctx) instanceof World w && l != null) {
                    w.setSpawnLocation(l);
                }
            };
        });
        registry.registerEffect("set difficulty of %world% to %object%", arguments -> {
            Expression<?> world = arguments.get(0);
            Expression<?> diff = arguments.get(1);
            return ctx -> {
                if (world.getSingle(ctx) instanceof World w
                        && diff.getSingle(ctx) instanceof org.bukkit.Difficulty d) {
                    w.setDifficulty(d);
                }
            };
        });
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
        // Shoot a projectile/entity of a type from a living entity in its facing direction.
        registry.registerEffect("shoot %object% from %object% at [speed] %number%", arguments -> {
            Expression<?> type = arguments.get(0);
            Expression<?> shooter = arguments.get(1);
            Expression<?> speed = arguments.get(2);
            return ctx -> shoot(type.getSingle(ctx), shooter.getSingle(ctx),
                    speed.getSingle(ctx) instanceof Number n ? n.doubleValue() : 2.0);
        });
        registry.registerEffect("shoot %object% from %object%", arguments -> {
            Expression<?> type = arguments.get(0);
            Expression<?> shooter = arguments.get(1);
            return ctx -> shoot(type.getSingle(ctx), shooter.getSingle(ctx), 2.0);
        });
        registry.registerEffect("shoot %object%", arguments -> {
            Expression<?> type = arguments.get(0);
            return ctx -> shoot(type.getSingle(ctx),
                    event(ctx) instanceof org.bukkit.event.player.PlayerEvent pe ? pe.getPlayer() : null, 2.0);
        });
        // Spawn an entity of a type at a location.
        registry.registerEffect("(spawn|summon) %object% at %object%", arguments -> {
            Expression<?> type = arguments.get(0);
            Expression<?> where = arguments.get(1);
            return ctx -> {
                if (where.getSingle(ctx) instanceof org.bukkit.Location loc && loc.getWorld() != null
                        && type.getSingle(ctx) instanceof org.bukkit.entity.EntityType et) {
                    lastSpawnedEntity = loc.getWorld().spawnEntity(loc, et);
                }
            };
        });
        // Face an entity toward a target location or entity.
        registry.registerEffect("(force|make) %object% [to] (face [toward[s]]|look at) %object%", arguments -> {
            Expression<?> who = arguments.get(0);
            Expression<?> target = arguments.get(1);
            return ctx -> {
                Object o = who.getSingle(ctx);
                Object t = target.getSingle(ctx);
                org.bukkit.Location to = t instanceof org.bukkit.Location l ? l
                        : (t instanceof Entity te ? te.getLocation() : null);
                if (o instanceof Entity ent && to != null && ent.getWorld().equals(to.getWorld())) {
                    org.bukkit.Location from = ent.getLocation();
                    from.setDirection(to.toVector().subtract(from.toVector()));
                    ent.teleport(from);
                }
            };
        });
        // Grow a tree at a location.
        registry.registerEffect("(grow|create|generate) tree of type %object% at %object%", arguments -> {
            Expression<?> type = arguments.get(0);
            Expression<?> where = arguments.get(1);
            return ctx -> {
                if (where.getSingle(ctx) instanceof org.bukkit.Location loc && loc.getWorld() != null
                        && type.getSingle(ctx) instanceof org.bukkit.TreeType tt) {
                    loc.getWorld().generateTree(loc, tt);
                }
            };
        });
        registry.registerEffect("(grow|create|generate) tree at %object%", arguments -> {
            Expression<?> where = arguments.get(0);
            return ctx -> {
                if (where.getSingle(ctx) instanceof org.bukkit.Location loc && loc.getWorld() != null) {
                    loc.getWorld().generateTree(loc, org.bukkit.TreeType.TREE);
                }
            };
        });
        // Player-first order: `give %player% %item%` (equivalent to `give %item% to %player%`).
        registry.registerEffect("give %player% %object%", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> item = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Player player && item.getSingle(ctx) instanceof ItemStack stack) {
                    player.getInventory().addItem(stack);
                }
            };
        });
        // Item builders: a copy of the item with a modified meta field.
        registry.registerExpression("%object% with lore %object%", Object.class, arguments ->
                itemWith(arguments, (meta, value) -> meta.setLore(java.util.List.of(
                        org.bukkit.ChatColor.translateAlternateColorCodes('&', String.valueOf(value))))));
        registry.registerExpression("%object% with [custom] model data %object%", Object.class, arguments ->
                itemWith(arguments, (meta, value) -> {
                    if (value instanceof Number n) {
                        meta.setCustomModelData(n.intValue());
                    }
                }));
        registry.registerExpression("%object% with [the] item flag[s] %object%", Object.class, arguments ->
                itemWith(arguments, (meta, value) -> {
                    if (value instanceof org.bukkit.inventory.ItemFlag flag) {
                        meta.addItemFlags(flag);
                    }
                }));
        registry.registerExpression("[the] first empty slot[s] of %object%", Object.class, arguments -> {
            Expression<?> src = arguments.get(0);
            return new ComputedExpression(ctx -> src.getSingle(ctx) instanceof org.bukkit.inventory.Inventory inv
                    ? inv.firstEmpty() : null);
        });
        registry.registerExpression("[the] exact %object%", Object.class, arguments -> {
            Expression<?> item = arguments.get(0);
            return new ComputedExpression(ctx -> item.getSingle(ctx) instanceof ItemStack it ? it : null);
        });
        registry.registerExpression("%object% with [shown] tooltip", Object.class, arguments ->
                itemWithTooltip(arguments.get(0), false));
        registry.registerExpression("%object% with(out| hidden) tooltip", Object.class, arguments ->
                itemWithTooltip(arguments.get(0), true));
        registry.registerExpression("%object% with [enchant[ment]] glint", Object.class, arguments -> {
            Expression<?> item = arguments.get(0);
            return new ComputedExpression(ctx -> {
                Object o = item.getSingle(ctx);
                org.bukkit.Material mat = material(o);
                if (mat == null) {
                    return null;
                }
                ItemStack stack = o instanceof ItemStack existing ? existing.clone() : new ItemStack(mat);
                org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.setEnchantmentGlintOverride(true);
                    stack.setItemMeta(meta);
                }
                return stack;
            });
        });
        // Named item: `a diamond named "&bStarter Gift"` — a copy of the item with a display name.
        registry.registerExpression("%object% (named|with name) %string%", Object.class, arguments -> {
            Expression<?> item = arguments.get(0);
            Expression<?> name = arguments.get(1);
            return new ComputedExpression(ctx -> {
                Object base = item.getSingle(ctx);
                org.bukkit.Material mat = material(base);
                if (mat == null) {
                    return null;
                }
                ItemStack stack = base instanceof ItemStack existing ? existing.clone() : new ItemStack(mat);
                Object rawName = name.getSingle(ctx);
                org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
                if (meta != null && rawName != null) {
                    meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', String.valueOf(rawName)));
                    stack.setItemMeta(meta);
                }
                return stack;
            });
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

    private static <H> void numericProperty(SyntaxRegistry registry, String name, Class<H> holder,
                                            java.util.function.ToDoubleFunction<H> getter,
                                            java.util.function.ObjDoubleConsumer<H> setter, double reset) {
        registry.registerExpression(name + " of %object%", Object.class, a ->
                new co.xenastudios.neoskript.lang.expression.NumericPropertyExpression<>(
                        a.get(0), holder, getter, setter, reset));
        registry.registerExpression("%object%'s " + name, Object.class, a ->
                new co.xenastudios.neoskript.lang.expression.NumericPropertyExpression<>(
                        a.get(0), holder, getter, setter, reset));
    }

    /** Coerces a value to a Material: a Material directly, or an item stack's type. */
    private static org.bukkit.Material material(Object value) {
        if (value instanceof org.bukkit.Material m) {
            return m;
        }
        return value instanceof ItemStack item ? item.getType() : null;
    }

    /** The current Bukkit event, or null outside an event context. */
    private static org.bukkit.event.Event event(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
        return ctx.event().orElse(null);
    }

    /** Registers a read-only event-value expression backed by a function of the context. */
    private static void eventValue(SyntaxRegistry registry, String pattern,
                                   Function<co.xenastudios.neoskript.api.runtime.TriggerContext, Object> getter) {
        registry.registerExpression(pattern, Object.class, a -> new ComputedExpression(getter::apply));
    }

    /** A read+writable event value: {@code set join message to ...} routes through {@link Expression#change}. */
    private static void settableEventValue(SyntaxRegistry registry, String pattern,
            Function<co.xenastudios.neoskript.api.runtime.TriggerContext, Object> getter,
            java.util.function.BiConsumer<org.bukkit.event.Event, Object> setter) {
        registry.registerExpression(pattern, Object.class, a -> new Expression<Object>() {
            @Override
            public Object[] getAll(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                Object v = getter.apply(ctx);
                return v == null ? new Object[0] : new Object[]{v};
            }

            @Override
            public Object getSingle(co.xenastudios.neoskript.api.runtime.TriggerContext ctx) {
                return getter.apply(ctx);
            }

            @Override
            public Class<Object> returnType() {
                return Object.class;
            }

            @Override
            public boolean isSingle() {
                return true;
            }

            @Override
            public Class<?>[] acceptChange(ChangeMode mode) {
                return mode == ChangeMode.SET ? new Class<?>[]{Object.class} : null;
            }

            @Override
            public void change(co.xenastudios.neoskript.api.runtime.TriggerContext ctx, Object[] delta, ChangeMode mode) {
                if (mode != ChangeMode.SET) {
                    return;
                }
                org.bukkit.event.Event ev = event(ctx);
                if (ev != null) {
                    setter.accept(ev, delta != null && delta.length > 0 ? delta[0] : null);
                }
            }
        });
    }

    /** Applies an action to the target player, or the event's player when the target is omitted. */
    private static co.xenastudios.neoskript.api.syntax.Effect playerOrEventEffect(Expression<?> target,
                                                                                  Consumer<Player> action) {
        return ctx -> {
            Player player = null;
            if (target != null && target.getSingle(ctx) instanceof Player p) {
                player = p;
            } else if (event(ctx) instanceof org.bukkit.event.player.PlayerEvent pe) {
                player = pe.getPlayer();
            }
            if (player != null) {
                action.accept(player);
            }
        };
    }

    /**
     * Registers directional-location expressions for one direction: {@code %number% blocks <dir> [of]
     * %object%} and {@code [the] <dir> [of] %object%} both return the offset location; the base-less
     * numeric form returns a {@link co.xenastudios.neoskript.lang.type.Direction} value.
     */
    private static void registerDirection(SyntaxRegistry registry, String dir,
                                          double fwd, double up, double left, boolean relative) {
        co.xenastudios.neoskript.lang.type.Direction base =
                new co.xenastudios.neoskript.lang.type.Direction(fwd, up, left, relative);
        registry.registerExpression("%number% [(block[s]|met(er|re)[s])] (" + dir + ") [(of|from)] %object%",
                Object.class, a -> {
            Expression<?> len = a.get(0);
            Expression<?> target = a.get(1);
            return new ComputedExpression(ctx -> {
                double n = len.getSingle(ctx) instanceof Number num ? num.doubleValue() : 1;
                return base.scaled(n).relativeTo(target.getSingle(ctx));
            });
        });
        registry.registerExpression("[the] (" + dir + ") [(of|from)] %object%", Object.class, a -> {
            Expression<?> target = a.get(0);
            return new ComputedExpression(ctx -> base.relativeTo(target.getSingle(ctx)));
        });
        registry.registerExpression("%number% [(block[s]|met(er|re)[s])] (" + dir + ")", Object.class, a -> {
            Expression<?> len = a.get(0);
            return new ComputedExpression(ctx ->
                    len.getSingle(ctx) instanceof Number num ? base.scaled(num.doubleValue()) : base);
        });
    }

    /** Extracts a millisecond timestamp from a date value (a millis {@link Number} or a {@link java.util.Date}). */
    /** Whether every inventory can hold all the given items (simulated on a copy of the contents). */
    private static Condition canHold(Expression<?> inventories, Expression<?> itemtypes, boolean expected) {
        return ctx -> {
            Object[] invs = inventories.getAll(ctx);
            if (invs.length == 0) {
                return false;
            }
            List<ItemStack> items = new ArrayList<>();
            for (Object value : itemtypes.getAll(ctx)) {
                ItemStack item = toItemStack(value);
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    items.add(item);
                }
            }
            for (Object value : invs) {
                if (!(value instanceof org.bukkit.inventory.Inventory inventory)
                        || !fits(inventory, items)) {
                    return !expected;
                }
            }
            return expected;
        };
    }

    /** Simulates adding every item to a clone of the inventory's storage; true if all fit. */
    private static boolean fits(org.bukkit.inventory.Inventory inventory, List<ItemStack> items) {
        ItemStack[] source = inventory.getStorageContents();
        ItemStack[] slots = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            slots[i] = source[i] == null ? null : source[i].clone();
        }
        for (ItemStack item : items) {
            int remaining = item.getAmount();
            int max = item.getMaxStackSize();
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                if (slots[i] != null && slots[i].isSimilar(item) && slots[i].getAmount() < max) {
                    int add = Math.min(remaining, max - slots[i].getAmount());
                    slots[i].setAmount(slots[i].getAmount() + add);
                    remaining -= add;
                }
            }
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                if (slots[i] == null || slots[i].getType() == org.bukkit.Material.AIR) {
                    ItemStack placed = item.clone();
                    int add = Math.min(remaining, max);
                    placed.setAmount(add);
                    slots[i] = placed;
                    remaining -= add;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    /** Tests whether every date lies before ({@code past}) or after "now"; result compared to {@code expected}. */
    private static Condition datesVsNow(Expression<?> dates, boolean past, boolean expected) {
        return ctx -> {
            Object[] values = dates.getAll(ctx);
            if (values.length == 0) {
                return false;
            }
            long now = System.currentTimeMillis();
            for (Object value : values) {
                Long ms = dateMillis(value);
                boolean matches = ms != null && (past ? ms < now : ms > now);
                if (!matches) {
                    return !expected;
                }
            }
            return expected;
        };
    }

    /** Tests whether the time elapsed since {@code date} is more (or less) than {@code timespan}. */
    private static Condition dateElapsed(Expression<?> date, Expression<?> timespan, boolean moreThan) {
        return ctx -> {
            Long ms = dateMillis(date.getSingle(ctx));
            if (ms == null
                    || !(timespan.getSingle(ctx) instanceof co.xenastudios.neoskript.core.runtime.Timespan span)) {
                return false;
            }
            long elapsed = System.currentTimeMillis() - ms;
            return moreThan ? elapsed > span.millis() : elapsed < span.millis();
        };
    }

    private static Long dateMillis(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return value instanceof java.util.Date d ? d.getTime() : null;
    }

    /** A copy of the item with its tooltip hidden or shown. */
    private static Expression<Object> itemWithTooltip(Expression<?> item, boolean hidden) {
        return new ComputedExpression(ctx -> {
            Object o = item.getSingle(ctx);
            org.bukkit.Material mat = material(o);
            if (mat == null) {
                return null;
            }
            ItemStack stack = o instanceof ItemStack existing ? existing.clone() : new ItemStack(mat);
            org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setHideTooltip(hidden);
                stack.setItemMeta(meta);
            }
            return stack;
        });
    }

    /** A copy of the first argument (an item) with its {@link org.bukkit.inventory.meta.ItemMeta} mutated. */
    private static Expression<Object> itemWith(co.xenastudios.neoskript.api.syntax.Arguments arguments,
            java.util.function.BiConsumer<org.bukkit.inventory.meta.ItemMeta, Object> mutator) {
        Expression<?> item = arguments.get(0);
        Expression<?> value = arguments.get(1);
        return new ComputedExpression(ctx -> {
            Object o = item.getSingle(ctx);
            org.bukkit.Material mat = material(o);
            if (mat == null) {
                return null;
            }
            ItemStack stack = o instanceof ItemStack existing ? existing.clone() : new ItemStack(mat);
            org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                mutator.accept(meta, value.getSingle(ctx));
                stack.setItemMeta(meta);
            }
            return stack;
        });
    }

    /** Shoots an entity of {@code type} from {@code shooter} in its facing direction at {@code speed}. */
    private static void shoot(Object type, Object shooter, double speed) {
        if (!(shooter instanceof org.bukkit.entity.LivingEntity le)
                || !(type instanceof org.bukkit.entity.EntityType et)) {
            return;
        }
        org.bukkit.Location from = le.getEyeLocation();
        if (from.getWorld() == null) {
            return;
        }
        org.bukkit.entity.Entity spawned = from.getWorld().spawnEntity(from, et);
        spawned.setVelocity(from.getDirection().multiply(speed));
        if (spawned instanceof org.bukkit.entity.Projectile projectile) {
            projectile.setShooter(le);
        }
    }

    /** Registers `push %entity% <dir> [at speed %number%]` (with and without an explicit speed). */
    private static void pushDirection(SyntaxRegistry registry, String dir, double dx, double dy, double dz) {
        registry.registerEffect("push %entity% (" + dir + ") at [speed] %number%", arguments -> {
            Expression<?> target = arguments.get(0);
            Expression<?> speed = arguments.get(1);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Entity entity) {
                    double s = speed.getSingle(ctx) instanceof Number n ? n.doubleValue() : 1;
                    entity.setVelocity(entity.getVelocity().add(new Vector(dx * s, dy * s, dz * s)));
                }
            };
        });
        registry.registerEffect("push %entity% (" + dir + ")", arguments -> {
            Expression<?> target = arguments.get(0);
            return ctx -> {
                if (target.getSingle(ctx) instanceof Entity entity) {
                    entity.setVelocity(entity.getVelocity().add(new Vector(dx, dy, dz)));
                }
            };
        });
    }

    private static co.xenastudios.neoskript.api.syntax.Effect entityEffect(Expression<?> target,
                                                                           Consumer<Entity> action) {
        return ctx -> {
            if (target.getSingle(ctx) instanceof Entity entity) {
                action.accept(entity);
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
            Expression<?> lhs = arguments.get(0);
            Expression<?> value = arguments.get(1);
            if (lhs instanceof VariableExpression variable) {
                if (variable.isList()) {
                    // `set {list::*} to <values>` replaces the whole list with every value.
                    return ctx -> {
                        Object[] values = value.getAll(ctx);
                        variable.delete(ctx);
                        for (Object v : values) {
                            variable.addToList(ctx, v);
                        }
                    };
                }
                return ctx -> variable.set(ctx, value.getSingle(ctx));
            }
            requireChangeable(lhs, ChangeMode.SET);
            return ctx -> lhs.change(ctx, new Object[]{value.getSingle(ctx)}, ChangeMode.SET);
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
        // Canonical Skript word order: replace X in Y with Z.
        registry.registerEffect("replace [all] %string% in %object% with %string%", arguments -> {
            Expression<?> from = arguments.get(0);
            VariableExpression variable = requireVariable(arguments.get(1));
            Expression<?> to = arguments.get(2);
            return ctx -> {
                String text = Renderer.toDisplay(variable.getSingle(ctx));
                variable.set(ctx, text.replace(Renderer.toDisplay(from.getSingle(ctx)),
                        Renderer.toDisplay(to.getSingle(ctx))));
            };
        });

        registry.registerEffect("add %object% to %object%", arguments -> {
            Expression<?> value = arguments.get(0);
            Expression<?> lhs = arguments.get(1);
            if (lhs instanceof VariableExpression variable) {
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
            }
            requireChangeable(lhs, ChangeMode.ADD);
            return ctx -> lhs.change(ctx, new Object[]{value.getSingle(ctx)}, ChangeMode.ADD);
        });

        registry.registerEffect("remove %object% from %object%", arguments -> {
            Expression<?> value = arguments.get(0);
            Expression<?> lhs = arguments.get(1);
            if (lhs instanceof VariableExpression variable) {
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
            }
            requireChangeable(lhs, ChangeMode.REMOVE);
            return ctx -> lhs.change(ctx, new Object[]{value.getSingle(ctx)}, ChangeMode.REMOVE);
        });

        registry.registerEffect("(delete|clear|reset) %object%", arguments -> {
            Expression<?> lhs = arguments.get(0);
            if (lhs instanceof VariableExpression variable) {
                return ctx -> mutate(ctx, variable, () -> variable.delete(ctx));
            }
            requireChangeable(lhs, ChangeMode.RESET);
            return ctx -> lhs.change(ctx, null, ChangeMode.RESET);
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

        // copy %objects% into %objects% — deep-copies the source values into a target variable.
        registry.registerEffect("copy %objects% [in]to %objects%", arguments -> {
            Expression<?> source = arguments.get(0);
            VariableExpression target = requireVariable(arguments.get(1));
            return ctx -> {
                Object[] values = source.getAll(ctx);
                target.delete(ctx);
                if (target.isList()) {
                    for (Object value : values) {
                        target.addToList(ctx, deepCopy(value));
                    }
                } else {
                    target.set(ctx, values.length > 0 ? deepCopy(values[0]) : null);
                }
            };
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

    /** Deep-copies a value where it is mutable (item/location/vector), else returns it unchanged. */
    private static Object deepCopy(Object value) {
        if (value instanceof ItemStack item) {
            return item.clone();
        }
        if (value instanceof Location location) {
            return location.clone();
        }
        return value instanceof Vector vector ? vector.clone() : value;
    }

    /** Ensures an expression accepts the given change mode (else this candidate fails, allowing fall-through). */
    private static void requireChangeable(Expression<?> expression, ChangeMode mode) {
        if (expression == null || expression.acceptChange(mode) == null) {
            throw new ParseException("Cannot " + mode.name().toLowerCase(Locale.ROOT) + ": " + expression);
        }
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
        if (target != null && target.getSingle(ctx) instanceof CommandSender sender) {
            return sender;
        }
        // No explicit `to %player%`: default to the event's player, else the command sender — so bare
        // `send "..."` reaches the player in both `on <event>:` and command triggers, as in Skript.
        CommandSender fromEvent = ctx.event()
                .filter(PlayerEvent.class::isInstance)
                .map(event -> (CommandSender) ((PlayerEvent) event).getPlayer())
                .orElse(null);
        if (fromEvent != null) {
            return fromEvent;
        }
        return ctx.getLocal("command-sender") instanceof CommandSender sender ? sender : null;
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
