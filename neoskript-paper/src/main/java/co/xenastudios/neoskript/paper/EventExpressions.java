/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SettableCondition;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerExpCooldownChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The expressions, conditions and effects of events, as Skript's: the damage, attacker and victim, drops,
 * dropped experience, death and kick messages, heal amounts, the reasons and causes of events, what was clicked,
 * whether the event is cancelled...
 */
final class EventExpressions {

    private EventExpressions() {}

    static void register(Registrar registrar) {
        registerDamage(registrar);
        registerDrops(registrar);
        registerMessages(registrar);
        registerReasons(registrar);
        registerClicked(registrar);
        registerBlocks(registrar);
        registerOther(registrar);
    }

    /**
     * Fails parsing outside the events of {@code eventClasses}, as Skript does for syntax that only works in
     * some: {@code 'the damage' can only be used in the damage event}.
     */
    static void restrict(ElementInfo info, String written, String events, Class<?>... eventClasses) {
        if (!isIn(info, eventClasses)) {
            throw new SyntaxError("'" + written + "' can only be used in " + events);
        }
    }

    /** Whether the code is in an event of one of {@code eventClasses}. */
    static boolean isIn(ElementInfo info, Class<?>... eventClasses) {
        for (Class<?> eventClass : info.eventClasses()) {
            for (Class<?> wanted : eventClasses) {
                if (wanted.isAssignableFrom(eventClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code [the] <written>}, as written: what restriction errors quote. */
    private static String the(ElementInfo info, String written) {
        return info.hasTag("the") ? "the " + written : written;
    }

    /**
     * An expression of an event's value, as Skript's EventValueExpression: the value {@code getter} gives in
     * events of {@code eventClasses}, and else Skript's error that the event has none.
     */
    private static void eventValue(Registrar registrar, String id, String type, String typeName,
            Function<Object, Object> getter, List<Class<?>> eventClasses, String... patterns) {
        String[] withThe = new String[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            withThe[i] = patterns[i].startsWith("[the] ") ? patterns[i] : "[the] " + patterns[i];
        }
        registrar.expression(id, type, info -> {
            if (!isIn(info, eventClasses.toArray(Class<?>[]::new))) {
                String event = info.eventName();
                throw new SyntaxError("There's no " + typeName + " in " + (event == null ? "this event"
                        : ("aeiou".indexOf(Character.toLowerCase(event.charAt(0))) >= 0 ? "an " : "a ") + event + " event"));
            }
            return (context, args) -> {
                for (Class<?> eventClass : eventClasses) {
                    if (eventClass.isInstance(context.event())) {
                        return getter.apply(context.event());
                    }
                }
                return null;
            };
        }, withThe);
    }

    // ---------------------------------------------------------------- damage

    private static void registerDamage(Registrar registrar) {
        registrar.expression("ExprDamage", "number", info -> {
            restrict(info, the(info, "damage"), "the damage event, the vehicle damage event or the item damage event",
                    EntityDamageEvent.class, VehicleDamageEvent.class, PlayerItemDamageEvent.class);
            return new EventNumber(event -> switch (event) {
                case VehicleDamageEvent vehicle -> vehicle.getDamage();
                case PlayerItemDamageEvent item -> (double) item.getDamage();
                case EntityDamageEvent damage -> damage.getDamage() / 2;
                default -> null;
            }, (event, value) -> {
                switch (event) {
                    case VehicleDamageEvent vehicle -> vehicle.setDamage(value);
                    case PlayerItemDamageEvent item -> item.setDamage((int) (double) value);
                    case EntityDamageEvent damage -> {
                        damage.setDamage(value * 2);
                        if (damage.getEntity() instanceof LivingEntity living) {
                            living.setLastDamage(value * 2);
                        }
                    }
                    default -> {}
                }
            }, false);
        }, "[the:the] damage");
        registrar.expression("ExprFinalDamage", "number", info -> {
            restrict(info, the(info, "final damage"), "the damage event", EntityDamageEvent.class);
            return (context, args) -> context.event() instanceof EntityDamageEvent event ? event.getFinalDamage() / 2 : null;
        }, "[the:the] final damage");
        eventValue(registrar, "ExprDamageCause", "damagecause", "damage cause", event -> switch (event) {
            case EntityDamageEvent damage -> damage.getCause();
            case EntityDeathEvent death -> death.getEntity().getLastDamageCause() == null ? null
                    : death.getEntity().getLastDamageCause().getCause();
            default -> null;
        }, List.of(EntityDamageEvent.class, EntityDeathEvent.class), "damage cause");
        // 'past damage cause' is the same cause: Skript lets it have a past state, not a future one.
        registrar.timeStates("ExprDamageCause", info -> info.time() < 0);
        registrar.condition("CondDamageCause", info -> {
            if (!isIn(info, EntityDamageEvent.class, EntityDeathEvent.class)) {
                String event = info.eventName();
                throw new SyntaxError("There's no damage cause in " + (event == null ? "this event"
                        : ("aeiou".indexOf(Character.toLowerCase(event.charAt(0))) >= 0 ? "an " : "a ") + event + " event"));
            }
            boolean negated = Events.mark(info) == 1;
            return (context, args) -> {
                Object cause = switch (context.event()) {
                    case EntityDamageEvent damage -> damage.getCause();
                    case EntityDeathEvent death -> death.getEntity().getLastDamageCause() == null ? null
                            : death.getEntity().getLastDamageCause().getCause();
                    default -> null;
                };
                if (cause == null) {
                    return false;
                }
                return co.xenastudios.neoskript.api.element.Condition.check(Events.values(args[0]), info.and(0),
                        expected -> expected == cause) != negated;
            };
        }, "[the] damage (was|is|has)(0¦|1¦n('|o)t) [been] (caused|done|made) by %damagecauses%");

        registrar.expression("ExprAttacker", "entity", info -> {
            restrict(info, "the attacker", "the damage event, the death event, the vehicle damage event, the vehicle "
                    + "destroy event or the attempt attack event", EntityDamageEvent.class, EntityDeathEvent.class,
                    VehicleDamageEvent.class, VehicleDestroyEvent.class, io.papermc.paper.event.player.PrePlayerAttackEntityEvent.class);
            return (context, args) -> attacker(context.event());
        }, "[the] (attacker|damager)");
        registrar.expression("ExprAttacked", "entity", info -> {
            restrict(info, "the victim", "the damage event, the death event, the vehicle damage event, the vehicle "
                    + "destroy event, the projectile hit event or the attempt attack event", EntityDamageEvent.class,
                    EntityDeathEvent.class, VehicleDamageEvent.class, VehicleDestroyEvent.class, ProjectileHitEvent.class,
                    io.papermc.paper.event.player.PrePlayerAttackEntityEvent.class);
            return (context, args) -> {
                Entity entity = switch (context.event()) {
                    case ProjectileHitEvent hit -> hit.getHitEntity();
                    case EntityEvent event -> event.getEntity();
                    case VehicleEvent vehicle -> vehicle.getVehicle();
                    case io.papermc.paper.event.player.PrePlayerAttackEntityEvent attack -> attack.getAttacked();
                    default -> null;
                };
                return entity != null && (args[0] == null || Events.isKind(args[0], entity)) ? entity : null;
            };
        }, "[the] (attacked|damaged|victim) [%-*entitytype%]");
    }

    /** Skript's ExprAttacker: who did it, the shooter for a projectile. */
    static Entity attacker(Object event) {
        return switch (event) {
            case EntityDamageByEntityEvent damage -> damage.getDamager() instanceof Projectile projectile
                    ? projectile.getShooter() instanceof Entity shooter ? shooter : null : damage.getDamager();
            case EntityDeathEvent death -> attacker(death.getEntity().getLastDamageCause());
            case VehicleDamageEvent vehicle -> vehicle.getAttacker();
            case VehicleDestroyEvent vehicle -> vehicle.getAttacker();
            case io.papermc.paper.event.player.PrePlayerAttackEntityEvent attack -> attack.getPlayer();
            case null, default -> null;
        };
    }

    /** A number of the event that {@code set}, {@code add}, {@code remove} and {@code delete} change. */
    private record EventNumber(Function<Object, Double> getter, BiConsumer<Object, Double> setter, boolean reset,
            boolean whole) implements Expression, Changeable {

        EventNumber(Function<Object, Double> getter, BiConsumer<Object, Double> setter, boolean reset) {
            this(getter, setter, reset, false);
        }

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            Double value = getter.apply(context.event());
            return value == null ? null : whole ? (Object) value.longValue() : value;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE, DELETE -> new Class<?>[] {Number.class};
                case RESET -> reset ? new Class<?>[0] : null;
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Double current = getter.apply(context.event());
            if (current == null) {
                return;
            }
            double value = delta == null || delta.length == 0 ? 0 : ((Number) delta[0]).doubleValue();
            setter.accept(context.event(), switch (mode) {
                case ADD -> current + value;
                case REMOVE -> current - value;
                case RESET -> Double.NaN;
                default -> value;
            });
        }
    }

    // ---------------------------------------------------------------- drops and experience

    private static void registerDrops(Registrar registrar) {
        registrar.expression("ExprDrops", "itemtype", info -> {
            restrict(info, the(info, "drops"), "the death event or the harvest block event", EntityDeathEvent.class,
                    PlayerHarvestBlockEvent.class);
            return new Drops(isIn(info, EntityDeathEvent.class));
        }, "[the:the] drops");
        registrar.effect("EffCancelDrops", info -> {
            restrict(info, "cancel the drops", "the death event, the break event, the block drop event or the harvest "
                    + "block event", EntityDeathEvent.class, BlockBreakEvent.class, BlockDropItemEvent.class,
                    PlayerHarvestBlockEvent.class);
            boolean items = !info.hasTag("xp");
            boolean experience = !info.hasTag("items");
            return (context, args) -> {
                switch (context.event()) {
                    case EntityDeathEvent death -> {
                        if (items) {
                            death.getDrops().clear();
                        }
                        if (experience) {
                            death.setDroppedExp(0);
                        }
                    }
                    case BlockBreakEvent breaking -> {
                        if (items) {
                            breaking.setDropItems(false);
                        }
                        if (experience) {
                            breaking.setExpToDrop(0);
                        }
                    }
                    case BlockDropItemEvent drop -> drop.getItems().forEach(Entity::remove);
                    case PlayerHarvestBlockEvent harvest -> harvest.getItemsHarvested().clear();
                    default -> {}
                }
            };
        }, "(cancel|clear|delete) [the] drops [of (items:items|xp:[e]xp[erience][s])]",
                "(cancel|clear|delete) [the] (items:item|xp:[e]xp[erience]) drops");

        registrar.expression("ExprExperience", "experience", info -> {
            restrict(info, "the experience", "the experience spawn event, the break event, the experience change event, "
                    + "the breed event or the fishing event", EventBridges.ExperienceSpawnEvent.class, BlockBreakEvent.class,
                    PlayerExpChangeEvent.class, EntityBreedEvent.class, PlayerFishEvent.class);
            return new DroppedExperience();
        }, "[the] (spawned|dropped|) [e]xp[erience] [orb[s]]");
    }

    /** {@code the drops} of a death or harvest, which can be set, added to and removed from. */
    private record Drops(boolean death) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<ItemStack> drops = drops(context.event());
            return drops == null ? new Object[0] : drops.stream().filter(java.util.Objects::nonNull)
                    .map(stack -> new ItemType(stack, true)).toArray();
        }

        private static List<ItemStack> drops(Object event) {
            return switch (event) {
                case EntityDeathEvent death -> death.getDrops();
                case PlayerHarvestBlockEvent harvest -> harvest.getItemsHarvested();
                default -> null;
            };
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE, REMOVE_ALL -> death ? new Class<?>[] {ItemType.class, Inventory.class, Experience.class}
                        : new Class<?>[] {ItemType.class, Inventory.class};
                // 'clear drops' is Skript's EffCancelDrops, which NeoSkript reads as clearing this.
                case DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            List<ItemStack> drops = drops(context.event());
            if (mode == Mode.DELETE && drops != null) {
                drops.clear();
                if (context.event() instanceof EntityDeathEvent death) {
                    death.setDroppedExp(0);
                }
                return;
            }
            if (drops == null || delta == null) {
                return;
            }
            int experience = -1;
            List<ItemStack> items = new ArrayList<>();
            for (Object value : delta) {
                switch (value) {
                    case Experience xp -> experience = Math.max(experience, 0) + xp.xp();
                    case Inventory inventory -> {
                        for (ItemStack item : inventory.getContents()) {
                            if (item != null) {
                                items.add(item.clone());
                            }
                        }
                    }
                    case ItemType item -> items.add(item.stack());
                    default -> {}
                }
            }
            if (experience > -1 && context.event() instanceof EntityDeathEvent death) {
                switch (mode) {
                    case SET -> death.setDroppedExp(experience);
                    case ADD -> death.setDroppedExp(death.getDroppedExp() + experience);
                    case REMOVE -> death.setDroppedExp(Math.max(0, death.getDroppedExp() - experience));
                    case REMOVE_ALL -> death.setDroppedExp(0);
                    default -> {}
                }
            }
            if (items.isEmpty()) {
                return;
            }
            switch (mode) {
                case SET -> {
                    drops.clear();
                    drops.addAll(items);
                }
                case ADD -> drops.addAll(items);
                case REMOVE -> {
                    for (ItemStack item : items) {
                        remove(drops, item, item.getAmount());
                    }
                }
                case REMOVE_ALL -> {
                    for (ItemStack item : items) {
                        drops.removeIf(drop -> drop != null && drop.isSimilar(item));
                    }
                }
                default -> {}
            }
        }

        /** Takes {@code amount} of {@code item} out of the stacks. */
        private static void remove(List<ItemStack> drops, ItemStack item, int amount) {
            for (var iterator = drops.listIterator(); iterator.hasNext() && amount > 0;) {
                ItemStack drop = iterator.next();
                if (drop == null || !drop.isSimilar(item)) {
                    continue;
                }
                int taken = Math.min(amount, drop.getAmount());
                amount -= taken;
                if (taken == drop.getAmount()) {
                    iterator.remove();
                } else {
                    drop.setAmount(drop.getAmount() - taken);
                }
            }
        }
    }

    /** {@code the dropped experience}: what spawns, drops or is gained in the event. */
    private static final class DroppedExperience implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            Integer xp = experience(context.event());
            return xp == null ? null : new Experience(xp);
        }

        private static Integer experience(Object event) {
            return switch (event) {
                case EventBridges.ExperienceSpawnEvent spawn -> spawn.getSpawnedXP();
                case BlockBreakEvent breaking -> breaking.getExpToDrop();
                case PlayerExpChangeEvent change -> change.getAmount();
                case EntityBreedEvent breed -> breed.getExperience();
                case PlayerFishEvent fish -> fish.getExpToDrop();
                default -> null;
            };
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, DELETE, ADD, REMOVE -> new Class<?>[] {Experience.class, Number.class};
                case RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Integer current = experience(context.event());
            if (current == null) {
                return;
            }
            int xp = current;
            if (delta != null) {
                for (Object value : delta) {
                    int amount = value instanceof Experience experience ? experience.xp() : ((Number) value).intValue();
                    switch (mode) {
                        case ADD -> xp += amount;
                        case SET -> xp = amount;
                        case REMOVE, REMOVE_ALL -> xp -= amount;
                        default -> {}
                    }
                }
            } else {
                xp = 0;
            }
            xp = Math.max(0, xp);
            switch (context.event()) {
                case EventBridges.ExperienceSpawnEvent spawn -> spawn.setSpawnedXP(xp);
                case BlockBreakEvent breaking -> breaking.setExpToDrop(xp);
                case PlayerExpChangeEvent change -> change.setAmount(xp);
                case EntityBreedEvent breed -> breed.setExperience(xp);
                case PlayerFishEvent fish -> fish.setExpToDrop(xp);
                default -> {}
            }
        }
    }

    // ---------------------------------------------------------------- messages

    private static void registerMessages(Registrar registrar) {
        message(registrar, "ExprDeathMessage", "the death message", "the death event", EntityDeathEvent.class,
                event -> event instanceof PlayerDeathEvent death ? death.deathMessage() : null,
                (event, message) -> {
                    if (event instanceof PlayerDeathEvent death) {
                        death.deathMessage(message);
                    }
                }, "[the] death( |-)message");
        message(registrar, "ExprKickMessage", "the kick message", "the kick event", PlayerKickEvent.class,
                event -> ((PlayerKickEvent) event).leaveMessage(),
                (event, message) -> ((PlayerKickEvent) event).leaveMessage(message), "[the] kick( |-)message");
        message(registrar, "ExprBroadcastMessage", "the broadcast message", "the broadcast event", BroadcastMessageEvent.class,
                event -> ((BroadcastMessageEvent) event).message(),
                (event, message) -> ((BroadcastMessageEvent) event).message(message), "[the] broadcast(-|[ed] )message");
        message(registrar, "ExprUnknownCommandMessage", "the unknown command message", "the unknown command execution event",
                org.bukkit.event.command.UnknownCommandEvent.class,
                event -> ((org.bukkit.event.command.UnknownCommandEvent) event).message(),
                (event, message) -> ((org.bukkit.event.command.UnknownCommandEvent) event).message(message),
                "[the] (unknown|non-existent) command message");
    }

    /** An event's message, as text, which {@code set} and {@code delete} change. */
    private static void message(Registrar registrar, String id, String written, String events, Class<?> eventClass,
            Function<Object, net.kyori.adventure.text.Component> getter,
            BiConsumer<Object, net.kyori.adventure.text.Component> setter, String pattern) {
        registrar.expression(id, "string", info -> {
            restrict(info, written, events, eventClass);
            return new EventMessage(eventClass, getter, setter);
        }, pattern);
    }

    /** An event's message as text, which {@code set} and {@code delete} (to nothing) change. */
    private record EventMessage(Class<?> eventClass, Function<Object, net.kyori.adventure.text.Component> getter,
            BiConsumer<Object, net.kyori.adventure.text.Component> setter) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            if (!eventClass.isInstance(context.event())) {
                return null;
            }
            net.kyori.adventure.text.Component message = getter.apply(context.event());
            return message == null ? null : Messages.LEGACY_SECTION.serialize(message);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {Object.class} : mode == Mode.DELETE ? new Class<?>[0] : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (eventClass.isInstance(context.event())) {
                setter.accept(context.event(), delta == null || delta.length == 0 ? net.kyori.adventure.text.Component.empty()
                        : Messages.component(delta[0]));
            }
        }
    }

    // ---------------------------------------------------------------- reasons and causes

    private static void registerReasons(Registrar registrar) {
        eventValue(registrar, "ExprHealReason", "healreason", "heal reason",
                event -> ((EntityRegainHealthEvent) event).getRegainReason(), List.of(EntityRegainHealthEvent.class),
                "(regen|health regain|heal[ing]) (reason|cause)");
        eventValue(registrar, "ExprTeleportCause", "teleportcause", "teleport cause",
                event -> ((PlayerTeleportEvent) event).getCause(), List.of(PlayerTeleportEvent.class),
                "teleport (cause|reason|type)");
        eventValue(registrar, "ExprSpawnReason", "spawnreason", "spawn reason",
                event -> ((CreatureSpawnEvent) event).getSpawnReason(), List.of(CreatureSpawnEvent.class),
                "spawn[ing] reason");
        eventValue(registrar, "ExprTransformReason", "transformreason", "transform reason",
                event -> ((EntityTransformEvent) event).getTransformReason(), List.of(EntityTransformEvent.class),
                "[the] transform[ing] (cause|reason|type)");
        eventValue(registrar, "ExprUnleashReason", "unleashreason", "unleash reason",
                event -> ((EntityUnleashEvent) event).getReason(), List.of(EntityUnleashEvent.class), "[the] unleash[ing] reason");
        eventValue(registrar, "ExprRespawnReason", "respawnreason", "respawn reason",
                event -> ((PlayerRespawnEvent) event).getRespawnReason(), List.of(PlayerRespawnEvent.class),
                "respawn[ing] reason");
        eventValue(registrar, "ExprInventoryCloseReason", "inventoryclosereason", "inventory close reason",
                event -> ((InventoryCloseEvent) event).getReason(), List.of(InventoryCloseEvent.class),
                "[the] inventory clos(e|ing) (reason|cause)");
        eventValue(registrar, "ExprExperienceCooldownChangeReason", "experiencecooldownchangereason",
                "experience cooldown change reason", event -> ((PlayerExpCooldownChangeEvent) event).getReason(),
                List.of(PlayerExpCooldownChangeEvent.class), "(experience|[e]xp) cooldown change (reason|cause|type)");
        eventValue(registrar, "ExprQuitReason", "quitreason", "quit reason", event -> ((PlayerQuitEvent) event).getReason(),
                List.of(PlayerQuitEvent.class), "(quit|disconnect) (cause|reason)");
        // As the damage cause, a past state that's the same.
        registrar.timeStates("ExprQuitReason", info -> info.time() < 0);
        eventValue(registrar, "ExprInventoryAction", "inventoryaction", "inventory action",
                event -> ((InventoryClickEvent) event).getAction(), List.of(InventoryClickEvent.class), "inventory action");
    }

    // ---------------------------------------------------------------- clicked

    /** {@code the clicked block}, {@code the clicked entity}, {@code the clicked slot}... */
    private static void registerClicked(Registrar registrar) {
        registrar.expression("ExprClicked", "object", info -> {
            int clicked = Events.mark(info);
            switch (clicked) {
                case 2 -> {
                    String type = info.argumentType(0);
                    if (type != null && type.contains("entitytype")) {
                        if (!isIn(info, PlayerInteractEntityEvent.class, PlayerInteractAtEntityEvent.class)) {
                            throw new SyntaxError("The expression 'clicked entity' may only be used in a click event");
                        }
                    } else if (!isIn(info, PlayerInteractEvent.class)) {
                        throw new SyntaxError("The expression 'clicked block' may only be used in a click event");
                    }
                }
                case 1 -> {
                    if (!isIn(info, EnchantItemEvent.class)) {
                        throw new SyntaxError("The expression 'clicked enchantment button' is only usable in an enchant event.");
                    }
                }
                default -> {
                    if (!isIn(info, InventoryClickEvent.class)) {
                        String name = switch (clicked) {
                            case 3 -> "clicked slot";
                            case 4 -> "clicked inventory";
                            case 5 -> "click type";
                            default -> "inventory action";
                        };
                        throw new SyntaxError("The expression '" + name + "' may only be used in an inventory click event");
                    }
                }
            }
            return (context, args) -> clicked(context.event(), clicked, args.length == 0 ? null : args[0]);
        }, "[the] (1¦clicked [enchant[ment]] (button|option)|2¦clicked (block|%-*itemtype/entitytype%)|3¦clicked slot"
                + "|4¦clicked inventory|5¦click (type|action)|6¦inventory action)");
    }

    private static Object clicked(Object event, int clicked, Object type) {
        if (clicked == 2) {
            if (event instanceof PlayerInteractEvent interact) {
                Block block = interact.getClickedBlock();
                if (type instanceof EntityKind) {
                    return null;
                }
                return block != null && (type == null || type instanceof ItemType item && item.material() == block.getType())
                        ? block : null;
            } else if (event instanceof PlayerInteractEntityEvent interact) {
                Entity entity = interact.getRightClicked();
                return type instanceof EntityKind && Events.isKind(type, entity) ? entity : null;
            }
            return null;
        }
        if (clicked == 1) {
            return event instanceof EnchantItemEvent enchant ? (long) (enchant.whichButton() + 1) : null;
        }
        if (!(event instanceof InventoryClickEvent click)) {
            return null;
        }
        return switch (clicked) {
            case 3 -> click.getClickedInventory() == null ? null : new Items.Slot(click.getClickedInventory(), click.getSlot());
            case 4 -> click.getClickedInventory();
            case 5 -> click.getClick();
            default -> click.getAction();
        };
    }

    // ---------------------------------------------------------------- blocks

    private static void registerBlocks(Registrar registrar) {
        registrar.expression("ExprPushedBlocks", "block", info -> {
            restrict(info, "the moved blocks", "the piston extend event or the piston retract event",
                    BlockPistonExtendEvent.class, BlockPistonRetractEvent.class);
            return (context, args) -> switch (context.event()) {
                case BlockPistonExtendEvent extend -> extend.getBlocks().toArray();
                case BlockPistonRetractEvent retract -> retract.getBlocks().toArray();
                default -> null;
            };
        }, "[the] moved blocks");
        registrar.expression("ExprAbsorbedBlocks", "block", info -> {
            restrict(info, "the absorbed blocks", "the sponge absorb event", SpongeAbsorbEvent.class);
            return (context, args) -> context.event() instanceof SpongeAbsorbEvent event
                    ? event.getBlocks().stream().map(Events::stateBlock).toArray() : null;
        }, "[the] absorbed blocks");
        registrar.expression("ExprFertilizedBlocks", "block", info -> {
            restrict(info, "the fertilized blocks", "the block fertilize event", BlockFertilizeEvent.class);
            return (context, args) -> context.event() instanceof BlockFertilizeEvent event
                    ? event.getBlocks().stream().map(Events::stateBlock).toArray() : null;
        }, "[all] [the] fertilized blocks");
        registrar.expression("ExprSourceBlock", "block", info -> {
            restrict(info, "the source block", "the spread event", BlockSpreadEvent.class);
            return (context, args) -> context.event() instanceof BlockSpreadEvent event ? event.getSource() : null;
        }, "[the] source block");
        registrar.expression("ExprExplosionYield", "number", info -> {
            restrict(info, "the explosion yield", "the explosion prime event", ExplosionPrimeEvent.class);
            return new EventNumber(event -> event instanceof ExplosionPrimeEvent prime ? (double) prime.getRadius() : null,
                    (event, value) -> {
                        if (value >= 0) {
                            ((ExplosionPrimeEvent) event).setRadius(value.floatValue());
                        }
                    }, false);
        }, "[the] explosion (yield|radius|size)", "[the] (yield|radius|size) of [the] explosion");
        registrar.expression("ExprExplosionBlockYield", "number", info -> {
            restrict(info, "the explosion block yield", "the explode event", EntityExplodeEvent.class);
            return new EventNumber(event -> event instanceof EntityExplodeEvent explode ? (double) explode.getYield() : null,
                    (event, value) -> {
                        if (value >= 0) {
                            ((EntityExplodeEvent) event).setYield(value.floatValue());
                        }
                    }, false);
        }, "[the] [explosion['s]] block (yield|amount)", "[the] percentage of blocks dropped");
    }

    // ---------------------------------------------------------------- other

    private static void registerOther(Registrar registrar) {
        registrar.condition("CondCancelled", info -> new Cancelled(info.pattern() == 1),
                "[the] event is cancel[l]ed", "[the] event (is not|isn't) cancel[l]ed");

        registrar.expression("ExprRespawnLocation", "location", info -> {
            restrict(info, "the respawn location", "the respawn event", PlayerRespawnEvent.class);
            return new EventProperty(event -> event instanceof PlayerRespawnEvent respawn ? respawn.getRespawnLocation() : null,
                    Location.class, (event, value) -> {
                        Location location = (Location) value;
                        if (location.getWorld() == null) {
                            location = location.clone();
                            location.setWorld(Bukkit.getWorlds().getFirst());
                        }
                        ((PlayerRespawnEvent) event).setRespawnLocation(location);
                    });
        }, "[the] respawn location");
        registrar.expression("ExprHealAmount", "number", info -> {
            restrict(info, "the heal amount", "the heal event", EntityRegainHealthEvent.class);
            return new EventNumber(event -> event instanceof EntityRegainHealthEvent heal ? heal.getAmount() : null,
                    (event, value) -> ((EntityRegainHealthEvent) event).setAmount(value), false);
        }, "[the] heal[ing] amount");
        // 'past heal amount' and 'future heal amount' in a heal event are the amount, as in Skript.
        registrar.timeStates("ExprHealAmount", -1, EntityRegainHealthEvent.class);
        registrar.expression("ExprMendingRepairAmount", "integer", info -> {
            restrict(info, "the mending repair amount", "the item mend event", PlayerItemMendEvent.class);
            return new EventNumber(event -> event instanceof PlayerItemMendEvent mend ? (double) mend.getRepairAmount() : null,
                    (event, value) -> {
                        PlayerItemMendEvent mend = (PlayerItemMendEvent) event;
                        if (value.isNaN()) {
                            int repair = mend.getExperienceOrb().getExperience() * 2;
                            int damage = mend.getItem().getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable
                                    ? damageable.getDamage() : 0;
                            mend.setRepairAmount(Math.min(damage, repair));
                        } else {
                            mend.setRepairAmount(value.intValue());
                        }
                    }, true, true);
        }, "[the] [mending] repair amount");
        registrar.expression("ExprAffectedEntities", "livingentity", info -> {
            restrict(info, "the affected entities", "the area cloud effect event", AreaEffectCloudApplyEvent.class);
            return new AffectedEntities();
        }, "[the] affected entities");
        registrar.expression("ExprArmorChangeItem", "itemstack", info -> {
            restrict(info, info.pattern() == 0 ? "old armor item" : "new armor item", "the armor change event",
                    com.destroystokyo.paper.event.player.PlayerArmorChangeEvent.class);
            boolean old = info.pattern() == 0;
            return (context, args) -> context.event() instanceof com.destroystokyo.paper.event.player.PlayerArmorChangeEvent event
                    ? old ? event.getOldItem() : event.getNewItem() : null;
        }, "[the] (old|unequipped) armo[u]r item", "[the] (new|equipped) armo[u]r item");
        registrar.expression("ExprSentCommands", "string", info -> {
            if (!isIn(info, org.bukkit.event.player.PlayerCommandSendEvent.class)) {
                throw new SyntaxError("The 'command list' expression can only be used in a 'send command list' event");
            }
            return new SentCommands();
        }, "[the] [sent] [server] command[s] list");
        registrar.expression("ExprBreedingFamily", "livingentity", info -> {
            if (!isIn(info, EntityBreedEvent.class)) {
                throw new SyntaxError("The 'breeding family' expression can only be used in an breed event.");
            }
            int which = info.pattern();
            return (context, args) -> context.event() instanceof EntityBreedEvent breed ? switch (which) {
                case 0 -> breed.getMother();
                case 1 -> breed.getFather();
                case 2 -> breed.getEntity();
                default -> breed.getBreeder();
            } : null;
        }, "[the] breeding mother", "[the] breeding father", "[the] [bred] (offspring|child)", "[the] breeder");
        registrar.expression("ExprConsumedItem", "itemstack", info -> {
            restrict(info, "the consumed item", "the entity shoot bow event or the consume event", EntityShootBowEvent.class,
                    PlayerItemConsumeEvent.class);
            return new EventProperty(event -> switch (event) {
                case EntityShootBowEvent shoot -> shoot.getConsumable();
                case PlayerItemConsumeEvent consume -> consume.getItem();
                default -> null;
            }, ItemStack.class, (event, value) -> {
                if (event instanceof PlayerItemConsumeEvent consume) {
                    consume.setItem((ItemStack) value);
                }
            });
        }, "[the] consumed item");
        registrar.expression("ExprHanging", "entity", info -> {
            restrict(info, info.hasTag("remover") ? "the hanging remover" : "the hanging entity",
                    "the break event or the place event", HangingBreakEvent.class, HangingPlaceEvent.class);
            boolean remover = info.hasTag("remover");
            if (remover && !isIn(info, HangingBreakEvent.class)) {
                throw new SyntaxError("The expression 'hanging remover' can only be used in break event");
            }
            return (context, args) -> {
                if (!remover) {
                    return context.event() instanceof org.bukkit.event.hanging.HangingEvent hanging ? hanging.getEntity() : null;
                }
                return context.event() instanceof HangingBreakByEntityEvent breaking ? breaking.getRemover() : null;
            };
        }, "[the] hanging (entity|:remover)");
        registrar.expression("ExprEvtInitiator", "inventory", info -> {
            restrict(info, "the initiator inventory", "the inventory item move event", InventoryMoveItemEvent.class);
            return (context, args) -> context.event() instanceof InventoryMoveItemEvent move ? move.getInitiator() : null;
        }, "[the] [event-]initiator[( |-)inventory]");
        registrar.expression("ExprAppliedEffect", "potioneffecttype", info -> {
            restrict(info, "the applied effect", "the beacon effect event",
                    com.destroystokyo.paper.event.block.BeaconEffectEvent.class);
            return (context, args) -> context.event() instanceof com.destroystokyo.paper.event.block.BeaconEffectEvent event
                    ? event.getEffect().getType() : null;
        }, "[the] applied [beacon] effect");
        registrar.expression("ExprReadiedArrow", "itemstack", info -> {
            restrict(info, "the readied arrow", "the ready arrow event", com.destroystokyo.paper.event.player.PlayerReadyArrowEvent.class);
            boolean arrow = info.hasTag("arrow");
            return (context, args) -> context.event() instanceof com.destroystokyo.paper.event.player.PlayerReadyArrowEvent event
                    ? arrow ? event.getArrow() : event.getBow() : null;
        }, "[the] (readied|selected|drawn) (:arrow|bow)");
        registrar.expression("ExprBarterInput", "itemtype", info -> {
            restrict(info, "the barter input", "the piglin barter event", PiglinBarterEvent.class);
            return (context, args) -> context.event() instanceof PiglinBarterEvent barter
                    ? new ItemType(barter.getInput(), true) : null;
        }, "[the] [piglin] barter[ing] input");
        registrar.expression("ExprBarterDrops", "itemtype", info -> {
            restrict(info, "the barter drops", "the piglin barter event", PiglinBarterEvent.class);
            return new BarterDrops();
        }, "[the] [piglin] barter[ing] drops");
        registrar.expression("ExprEnchantItem", "itemtype", info -> {
            restrict(info, "the enchanted item", "the enchant event or the enchant prepare event", EnchantItemEvent.class,
                    PrepareItemEnchantEvent.class);
            if (!info.hasTag("ed")) {
                co.xenastudios.neoskript.api.element.Warnings.deprecated("The 'enchant item' form of the enchanted item"
                        + " expression is deprecated, please use 'enchanted item'!");
            }
            return new EventProperty(event -> switch (event) {
                case EnchantItemEvent enchant -> new ItemType(enchant.getItem(), true);
                case PrepareItemEnchantEvent prepare -> new ItemType(prepare.getItem(), true);
                default -> null;
            }, ItemType.class, (event, value) -> {
                ItemStack stack = ((ItemType) value).stack();
                switch (event) {
                    case EnchantItemEvent enchant -> enchant.getItem().setType(stack.getType());
                    case PrepareItemEnchantEvent prepare -> prepare.getItem().setType(stack.getType());
                    default -> {}
                }
            });
        }, "[the] enchant[:ed] item");
        registrar.condition("CondElytraBoostConsume", info -> {
            restrict(info, "the boosting firework will be consumed", "the elytra boost event",
                    com.destroystokyo.paper.event.player.PlayerElytraBoostEvent.class);
            boolean negated = info.pattern() == 1;
            return (context, args) -> context.event() instanceof com.destroystokyo.paper.event.player.PlayerElytraBoostEvent boost
                    && boost.shouldConsume() != negated;
        }, "[the] (boosting|used) firework will be consumed", "[the] (boosting|used) firework (will not|won't) be consumed");
        registrar.effect("EffElytraBoostConsume", info -> {
            restrict(info, "consume the firework", "the elytra boost event",
                    com.destroystokyo.paper.event.player.PlayerElytraBoostEvent.class);
            boolean consume = info.pattern() == 1;
            return (context, args) -> {
                if (context.event() instanceof com.destroystokyo.paper.event.player.PlayerElytraBoostEvent boost) {
                    boost.setShouldConsume(consume);
                }
            };
        }, "(prevent|disallow) [the] (boosting|used) firework from being consumed",
                "allow [the] (boosting|used) firework to be consumed");
    }

    /** {@code the event is cancelled}, which {@code set} changes. */
    private record Cancelled(boolean negated) implements SettableCondition {
        @Override
        public boolean check(TriggerContext context, Object[] arguments) {
            return (context.event() instanceof Cancellable cancellable && cancellable.isCancelled()) != negated;
        }

        @Override
        public void set(TriggerContext context, Object[] arguments, boolean value) {
            if (context.event() instanceof Cancellable cancellable) {
                cancellable.setCancelled(value != negated);
            }
        }
    }

    /** A value of the event that {@code set} changes. */
    private record EventProperty(Function<Object, Object> getter, Class<?> valueClass, BiConsumer<Object, Object> setter)
            implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return getter.apply(context.event());
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {valueClass} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (delta != null && delta.length > 0 && getter.apply(context.event()) != null) {
                setter.accept(context.event(), delta[0]);
            }
        }
    }

    /** The entities an area effect cloud applies to, which can be changed. */
    private static final class AffectedEntities implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return context.event() instanceof AreaEffectCloudApplyEvent event ? event.getAffectedEntities().toArray() : null;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case ADD, SET, DELETE, REMOVE, RESET -> new Class<?>[] {LivingEntity.class};
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(context.event() instanceof AreaEffectCloudApplyEvent event)) {
                return;
            }
            List<LivingEntity> affected = event.getAffectedEntities();
            switch (mode) {
                case REMOVE -> {
                    for (Object entity : delta) {
                        affected.remove((LivingEntity) entity);
                    }
                }
                case SET, ADD -> {
                    if (mode == Mode.SET) {
                        affected.clear();
                    }
                    for (Object entity : delta) {
                        affected.add((LivingEntity) entity);
                    }
                }
                default -> affected.clear();
            }
        }
    }

    /** The command list sent to a player, which can be set (to some of the commands), shrunk and reset. */
    private static final class SentCommands implements Expression, Changeable {
        /** The commands each event started with, for resetting. */
        private final java.util.Map<Object, List<String>> originals = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            if (!(context.event() instanceof org.bukkit.event.player.PlayerCommandSendEvent event)) {
                return null;
            }
            original(event);
            return event.getCommands().toArray();
        }

        private List<String> original(org.bukkit.event.player.PlayerCommandSendEvent event) {
            return originals.computeIfAbsent(event, e -> new ArrayList<>(event.getCommands()));
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case REMOVE, DELETE, SET, RESET -> new Class<?>[] {String.class};
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(context.event() instanceof org.bukkit.event.player.PlayerCommandSendEvent event)) {
                return;
            }
            List<String> original = original(event);
            java.util.Collection<String> commands = event.getCommands();
            List<String> given = new ArrayList<>();
            if (delta != null) {
                for (Object value : delta) {
                    given.add(String.valueOf(value));
                }
            }
            switch (mode) {
                case DELETE -> commands.clear();
                case REMOVE -> commands.removeAll(given);
                case SET -> {
                    // Only commands the player was sent can be: new ones are left out.
                    given.retainAll(original);
                    commands.clear();
                    commands.addAll(given);
                }
                case RESET -> {
                    commands.clear();
                    commands.addAll(original);
                }
                default -> {}
            }
        }
    }

    /** What a piglin gives for bartering, which can be changed. */
    private static final class BarterDrops implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return context.event() instanceof PiglinBarterEvent event
                    ? event.getOutcome().stream().map(stack -> new ItemType(stack, true)).toArray() : null;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE, REMOVE_ALL, DELETE -> new Class<?>[] {ItemType.class};
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(context.event() instanceof PiglinBarterEvent event)) {
                return;
            }
            List<ItemStack> outcome = event.getOutcome();
            List<ItemStack> given = new ArrayList<>();
            if (delta != null) {
                for (Object value : delta) {
                    if (value instanceof ItemType item) {
                        given.add(item.stack());
                    }
                }
            }
            switch (mode) {
                case SET -> {
                    outcome.clear();
                    outcome.addAll(given);
                }
                case ADD -> outcome.addAll(given);
                case REMOVE, REMOVE_ALL -> {
                    for (ItemStack item : given) {
                        outcome.removeIf(stack -> stack.isSimilar(item));
                    }
                }
                case DELETE -> outcome.clear();
                default -> {}
            }
        }
    }
}
