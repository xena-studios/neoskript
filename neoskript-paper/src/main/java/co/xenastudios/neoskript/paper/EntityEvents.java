/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PigZapEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.SheepRegrowWoolEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.entity.BatToggleSleepEvent;
import org.bukkit.event.entity.HorseJumpEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleCollisionEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.inventory.EntityEquipment;

/**
 * Entity events: damage and death, spawning, healing, targeting, shooting, transforming, leashing, breeding,
 * potion effects, projectiles and vehicles, with the values triggers get from them.
 */
final class EntityEvents {

    private EntityEvents() {}

    private static final String DAMAGE_DEATH = "Use 'attacker' and/or 'victim' in damage/death events";

    static void register(Registrar registrar) {
        // 'event-projectile', 'event-dropped item': the event's entity of that kind.
        registrar.kindClass("entitytype", kind -> ((EntityKind) kind).entityClass());
        registerEntityValues(registrar);
        registerDamage(registrar);
        registerSpawnAndDeath(registrar);
        registerChanges(registrar);
        registerSimple(registrar);
        registerProjectiles(registrar);
        registerVehicles(registrar);
    }

    /** What every entity event has, and what damage and death events leave out. */
    private static void registerEntityValues(Registrar registrar) {
        registrar.eventValue(EntityEvent.class, "entity", EntityEvent::getEntity);
        registrar.eventValue(EntityEvent.class, "commandsender", EntityEvent::getEntity);
        registrar.eventValue(EntityEvent.class, "world", event -> event.getEntity().getWorld());
        registrar.eventValue(EntityEvent.class, "location", event -> event.getEntity().getLocation());
        registrar.eventValue(EntityEvent.class, "entitytype", event -> EntityKind.of(event.getEntity().getType()));
        for (Class<?> excluded : List.of(EntityDamageEvent.class, EntityDeathEvent.class)) {
            registrar.eventValueExcluded(excluded, "entity", DAMAGE_DEATH);
            registrar.eventValueExcluded(excluded, "commandsender", DAMAGE_DEATH);
            registrar.eventValueExcluded(excluded, "entitytype", "Use 'type of attacker/victim' in damage/death events.");
        }
    }

    /** {@code on damage of player by zombie}. */
    private static void registerDamage(Registrar registrar) {
        registrar.event("EvtDamage", List.of(EntityDamageEvent.class), info -> (context, args) -> {
            EntityDamageEvent event = (EntityDamageEvent) context.event();
            Object[] of = Events.values(args[0]);
            Object[] by = Events.values(args[1]);
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                if (by != null && !Events.any(by, kind -> Events.isKind(kind, byEntity.getDamager()))) {
                    return false;
                }
            } else if (by != null) {
                return false;
            }
            if (of != null && !Events.any(of, kind -> Events.isKind(kind, event.getEntity()))) {
                return false;
            }
            if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof EnderDragon
                    && byEntity.getEntity() instanceof EnderDragon) {
                return false;
            }
            // Already dead: the damage can't count.
            return !(event.getEntity() instanceof LivingEntity living) || living.getHealth() > 0;
        }, "damag(e|ing) [of %-entitytype%] [by %-entitytype%]");
        registrar.eventValue(EntityDamageEvent.class, "damagecause", EntityDamageEvent::getCause);
        registrar.eventValue(EntityDamageByEntityEvent.class, "entity", Projectile.class, 0,
                event -> event.getDamager() instanceof Projectile projectile ? projectile : null);
    }

    /** {@code on death of a zombie}, {@code on spawn of a creeper}. */
    private static void registerSpawnAndDeath(Registrar registrar) {
        registrar.event("EvtEntity", List.of(EntityDeathEvent.class), info -> {
            checkKinds(info, kind -> !LivingEntity.class.isAssignableFrom(kind.entityClass())
                    && !kind.entityClass().isAssignableFrom(LivingEntity.class), "The death event only works for living entities");
            return Events.optional(0, (event, kinds) -> Events.any(kinds,
                    kind -> Events.isKind(kind, ((EntityDeathEvent) event).getEntity()))).create(info);
        }, "death [of %-entitytypes%]");
        registrar.event("EvtEntitySpawn", List.of(EntitySpawnEvent.class), info -> {
            checkKinds(info, kind -> HumanEntity.class.isAssignableFrom(kind.entityClass()),
                    "The spawn event does not work for human entities");
            return Events.optional(0, (event, kinds) -> Events.any(kinds,
                    kind -> Events.isKind(kind, ((EntitySpawnEvent) event).getEntity()))).create(info);
        }, "spawn[ing] [of %-entitytypes%]");

        registrar.eventValue(EntityDeathEvent.class, "itemstack", org.bukkit.inventory.ItemStack[].class, 0,
                event -> event.getDrops().toArray(new org.bukkit.inventory.ItemStack[0]));
        registrar.eventValue(EntityDeathEvent.class, "entity", Projectile.class, 0, event ->
                event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent byEntity
                        && byEntity.getDamager() instanceof Projectile projectile ? projectile : null);
        registrar.eventValue(EntityDeathEvent.class, "damagecause", event -> event.getEntity().getLastDamageCause() == null
                ? null : event.getEntity().getLastDamageCause().getCause());
        registrar.eventValue(CreatureSpawnEvent.class, "spawnreason", CreatureSpawnEvent::getSpawnReason);
        registrar.eventValue(ItemSpawnEvent.class, "itemstack", event -> event.getEntity().getItemStack());
    }

    /** Fails parsing if an entity type written as the event's argument is one {@code wrong} says it can't be. */
    private static void checkKinds(ElementInfo info, Predicate<EntityKind> wrong, String message) {
        String written = info.argumentLiteral(0);
        if (written == null) {
            return;
        }
        for (String part : written.split("\\s*,\\s*|\\s+(?:and|or|n?or)\\s+")) {
            EntityKind kind;
            try {
                kind = EntityKind.parse(part);
            } catch (RuntimeException notOne) {
                continue;
            }
            if (kind != null && wrong.test(kind)) {
                throw Events.error(message);
            }
        }
    }

    /** Healing, targeting, shooting bows, transforming, leashing, breeding, block changes and potion effects. */
    private static void registerChanges(Registrar registrar) {
        registrar.event("EvtHealing", List.of(EntityRegainHealthEvent.class), info -> (context, args) -> {
            EntityRegainHealthEvent event = (EntityRegainHealthEvent) context.event();
            Object[] kinds = Events.values(args[0]);
            Object[] reasons = Events.values(args[1]);
            return (kinds == null || Events.any(kinds, kind -> Events.isKind(kind, event.getEntity())))
                    && (reasons == null || Events.any(reasons, reason -> reason == event.getRegainReason()));
        }, "heal[ing] [of %-entitytypes%] [(from|due to|by) %-healreasons%]",
                "%entitytypes% heal[ing] [(from|due to|by) %-healreasons%]");
        registrar.eventValue(EntityRegainHealthEvent.class, "healreason", EntityRegainHealthEvent::getRegainReason);

        registrar.event("EvtEntityTarget", List.of(EntityTargetEvent.class), info -> {
            boolean target = info.pattern() == 0;
            return (context, args) -> (((EntityTargetEvent) context.event()).getTarget() == null) ^ target;
        }, "[entity] target", "[entity] un[-]target");

        registrar.event("EvtEntityShootBow", List.of(EntityShootBowEvent.class), info -> (context, args) -> Events.any(
                Events.values(args[0]), kind -> Events.isKind(kind, ((EntityShootBowEvent) context.event()).getEntity())),
                "%entitytypes% shoot[ing] (bow|projectile)");
        registrar.eventValue(EntityShootBowEvent.class, "itemstack", EntityShootBowEvent::getBow);
        // The projectile shot, not the shooter.
        registrar.eventValue(EntityShootBowEvent.class, "entity", EntityShootBowEvent::getProjectile);
        registrar.eventValue(EntityShootBowEvent.class, "slot", event -> {
            EntityEquipment equipment = event.getEntity().getEquipment();
            return equipment == null ? null : Items.Slot.of(event.getEntity(), event.getHand());
        });

        registrar.event("EvtEntityTransform", List.of(EntityTransformEvent.class), info -> (context, args) -> {
            EntityTransformEvent event = (EntityTransformEvent) context.event();
            Object[] kinds = Events.values(args[0]);
            Object[] reasons = Events.values(args[1]);
            return (reasons == null || Events.check(info, 1, reasons, reason -> reason == event.getTransformReason()))
                    && (kinds == null || Events.check(info, 0, kinds, kind -> Events.isKind(kind, event.getEntity())));
        }, "(entit(y|ies)|%*-entitytypes%) transform[ing] [due to %-transformreasons%]");
        registrar.eventValue(EntityTransformEvent.class, "entity", Entity[].class, 0,
                event -> event.getTransformedEntities().toArray(Entity[]::new));
        registrar.eventValue(EntityTransformEvent.class, "transformreason", EntityTransformEvent::getTransformReason);

        registerBlockChange(registrar);

        // Leashing, unleashing, and players unleashing.
        registrar.event("EvtLeash", List.of(PlayerLeashEntityEvent.class, EntityUnleashEvent.class), info -> {
            boolean unleash = info.hasTag("un");
            boolean player = unleash && info.hasTag("player");
            List<Class<?>> classes = List.of(!unleash ? PlayerLeashEntityEvent.class
                    : player ? PlayerUnleashEntityEvent.class : EntityUnleashEvent.class);
            return Events.narrowed(classes, (context, args) -> {
                Entity leashed = switch (context.event()) {
                    case PlayerLeashEntityEvent leash when !unleash -> leash.getEntity();
                    case PlayerUnleashEntityEvent playerUnleash when player -> playerUnleash.getEntity();
                    case EntityUnleashEvent entityUnleash when unleash && !player -> entityUnleash.getEntity();
                    default -> null;
                };
                Object[] kinds = Events.values(args[0]);
                return leashed != null && (kinds == null || Events.any(kinds, kind -> Events.isKind(kind, leashed)));
            });
        }, "[:player] [:un]leash[ing] [of %-entitytypes%]");
        registrar.eventValue(PlayerLeashEntityEvent.class, "player", PlayerLeashEntityEvent::getPlayer);
        registrar.eventValue(PlayerLeashEntityEvent.class, "entity", PlayerLeashEntityEvent::getEntity);
        registrar.eventValue(EntityUnleashEvent.class, "unleashreason", EntityUnleashEvent::getReason);
        registrar.eventValue(PlayerUnleashEntityEvent.class, "player", PlayerUnleashEntityEvent::getPlayer);

        registrar.event("EvtBreed", List.of(EntityBreedEvent.class), Events.optional(0, (event, kinds) ->
                Events.any(kinds, kind -> Events.isKind(kind, ((EntityBreedEvent) event).getEntity()))),
                "[entity] breed[ing] [of %-entitytypes%]");
        registrar.eventValue(EntityBreedEvent.class, "itemstack", EntityBreedEvent::getBredWith);
        Events.simple(registrar, "EvtLoveModeEnter", EntityEnterLoveModeEvent.class, "[entity] enter[s] love mode",
                "[entity] love mode [enter]");
        registrar.eventValue(EntityEnterLoveModeEvent.class, "livingentity", EntityEnterLoveModeEvent::getEntity);
        registrar.eventValue(EntityEnterLoveModeEvent.class, "player", event ->
                event.getHumanEntity() instanceof org.bukkit.entity.Player player ? player : null);

        registrar.event("EvtEntityPotion", List.of(EntityPotionEffectEvent.class), info -> (context, args) -> {
            EntityPotionEffectEvent event = (EntityPotionEffectEvent) context.event();
            Object[] types = Events.values(args[0]);
            Object[] actions = Events.values(args[1]);
            Object[] causes = Events.values(args[2]);
            if (actions != null && !Events.any(actions, action -> action == event.getAction())) {
                return false;
            }
            if (types != null) {
                Object oldType = event.getOldEffect() == null ? null : event.getOldEffect().getType();
                Object newType = event.getNewEffect() == null ? null : event.getNewEffect().getType();
                if (!Events.check(info, 0, types, type -> type.equals(oldType) || type.equals(newType))) {
                    return false;
                }
            }
            return causes == null || Events.any(causes, cause -> cause == event.getCause());
        }, "entity potion effect [modif[y|ication]] [[of] %-potioneffecttypes%] [%-potionactions%] [due to %-potioncauses%]");
        registrar.eventValue(EntityPotionEffectEvent.class, "skriptpotioneffect", -1, event -> event.getOldEffect() == null
                ? null : Potions.Effect.of(event.getOldEffect(), null));
        registrar.eventValue(EntityPotionEffectEvent.class, "skriptpotioneffect", event -> event.getNewEffect() == null
                ? null : Potions.Effect.of(event.getNewEffect(), null));
        registrar.eventValue(EntityPotionEffectEvent.class, "potioneffecttype", EntityPotionEffectEvent::getModifiedType);
        registrar.eventValue(EntityPotionEffectEvent.class, "potioncause", EntityPotionEffectEvent::getCause);
        registrar.eventValue(EntityPotionEffectEvent.class, "potionaction", EntityPotionEffectEvent::getAction);
    }

    /** Endermen, sheep, silverfish and falling blocks changing blocks. */
    private static void registerBlockChange(Registrar registrar) {
        List<Predicate<EntityChangeBlockEvent>> checks = List.of(
                event -> event.getEntity() instanceof org.bukkit.entity.Enderman && !event.getTo().isAir(),
                event -> event.getEntity() instanceof org.bukkit.entity.Enderman && event.getTo().isAir(),
                event -> event.getEntity() instanceof org.bukkit.entity.Sheep,
                event -> event.getEntity() instanceof org.bukkit.entity.Silverfish && !event.getTo().isAir(),
                event -> event.getEntity() instanceof org.bukkit.entity.Silverfish && event.getTo().isAir(),
                event -> event.getEntity() instanceof org.bukkit.entity.FallingBlock && event.getTo().isAir(),
                event -> event.getEntity() instanceof org.bukkit.entity.FallingBlock && !event.getTo().isAir());
        registrar.event("EvtEntityBlockChange", List.of(EntityChangeBlockEvent.class), info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                EntityChangeBlockEvent event = (EntityChangeBlockEvent) context.event();
                if (pattern == checks.size()) {
                    Object[] kinds = Events.values(args[0]);
                    return kinds == null || Events.check(info, 0, kinds, kind -> Events.isKind(kind, event.getEntity()));
                }
                return checks.get(pattern).test(event);
            };
        }, "enderman place", "enderman pickup", "sheep eat", "silverfish enter", "silverfish exit",
                "falling block fall[ing]", "falling block land[ing]", "(entity|%*-entitytypes%) chang(e|ing) block[s]");
        registrar.eventValue(EntityChangeBlockEvent.class, "block", -1, EntityChangeBlockEvent::getBlock);
        registrar.eventValue(EntityChangeBlockEvent.class, "block", EntityChangeBlockEvent::getBlock);
        registrar.eventValue(EntityChangeBlockEvent.class, "blockdata", EntityChangeBlockEvent::getBlockData);
        registrar.eventValue(EntityChangeBlockEvent.class, "blockdata", 1, EntityChangeBlockEvent::getBlockData);
    }

    /** Skript's simple entity events, which have no filter. */
    private static void registerSimple(Registrar registrar) {
        Events.simple(registrar, "EvtCreeperPower", CreeperPowerEvent.class, "creeper power");
        Events.simple(registrar, "EvtZombieBreakDoor", EntityBreakDoorEvent.class, "zombie break[ing] [a] [wood[en]] door");
        Events.simple(registrar, "EvtCombust", EntityCombustEvent.class, "combust[ing]");
        Events.simple(registrar, "EvtPortalEnter", EntityPortalEnterEvent.class, "portal enter[ing]", "entering [a] portal");
        Events.simple(registrar, "EvtTame", EntityTameEvent.class, "[entity] tam(e|ing)");
        Events.simple(registrar, "EvtExplosionPrime", ExplosionPrimeEvent.class, "explosion prime");
        Events.simple(registrar, "EvtHungerMeterChange", FoodLevelChangeEvent.class,
                "(food|hunger) (level|met(er|re)|bar) chang(e|ing)");
        Events.simple(registrar, "EvtLightningStrike", LightningStrikeEvent.class, "lightning [strike]");
        Events.simple(registrar, "EvtPigZap", PigZapEvent.class, "pig[ ]zap");
        Events.simple(registrar, "EvtEntityMount", org.bukkit.event.entity.EntityMountEvent.class, "mount[ing]");
        Events.simple(registrar, "EvtEntityDismount", org.bukkit.event.entity.EntityDismountEvent.class, "dismount[ing]");
        Events.simple(registrar, "EvtGlidingStateChange", EntityToggleGlideEvent.class,
                "(gliding state change|toggl(e|ing) gliding)");
        Events.simple(registrar, "EvtAoECloudEffect", AreaEffectCloudApplyEvent.class, "(area|AoE) [cloud] effect");
        Events.simple(registrar, "EvtSheepRegrowWool", SheepRegrowWoolEvent.class, "sheep [re]grow[ing] wool");
        Events.simple(registrar, "EvtSlimeSplit", SlimeSplitEvent.class, "slime split[ting]");
        Events.simple(registrar, "EvtResurrectAttempt", EntityResurrectEvent.class, "[entity] resurrect[ion] [attempt]");
        Events.simple(registrar, "EvtHorseJump", HorseJumpEvent.class, "horse jump");
        Events.simple(registrar, "EvtEntityJump", com.destroystokyo.paper.event.entity.EntityJumpEvent.class,
                "entity jump[ing]");
        Events.simple(registrar, "EvtPiglinBarter", PiglinBarterEvent.class, "piglin (barter[ing]|trad(e|ing))");
        Events.simple(registrar, "EvtEndermanEnrage", com.destroystokyo.paper.event.entity.EndermanAttackPlayerEvent.class,
                "enderman (enrage|anger)");
        Events.simple(registrar, "EvtBatToggleSleep", BatToggleSleepEvent.class, "bat toggle sleep");
        Events.simple(registrar, "EvtVillagerCareerChange", VillagerCareerChangeEvent.class, "villager career chang(e[d]|ing)");
        Events.simple(registrar, "EvtPathfind", com.destroystokyo.paper.event.entity.EntityPathfindEvent.class,
                "[entity] [start[s]] pathfind[ing]");

        registrar.eventValue(EntityTameEvent.class, "entity", EntityTameEvent::getEntity);
        registrar.eventValue(LightningStrikeEvent.class, "entity", LightningStrikeEvent::getLightning);
        registrar.eventValue(com.destroystokyo.paper.event.entity.EndermanAttackPlayerEvent.class, "player",
                com.destroystokyo.paper.event.entity.EndermanAttackPlayerEvent::getPlayer);
        registrar.eventValue(AreaEffectCloudApplyEvent.class, "livingentity", LivingEntity[].class, 0,
                event -> event.getAffectedEntities().toArray(new LivingEntity[0]));
        registrar.eventValue(AreaEffectCloudApplyEvent.class, "potioneffecttype", event -> {
            org.bukkit.potion.PotionType base = event.getEntity().getBasePotionType();
            return base == null ? null : base.getEffectType();
        });
        registrar.eventValue(EntityResurrectEvent.class, "slot", event -> event.getHand() == null
                || event.getEntity().getEquipment() == null ? null : Items.Slot.of(event.getEntity(), event.getHand()));
        registrar.eventValue(HorseJumpEvent.class, "entity", HorseJumpEvent::getEntity);
        registrar.eventValue(VillagerCareerChangeEvent.class, "villagercareerchangereason", VillagerCareerChangeEvent::getReason);
        registrar.eventValue(VillagerCareerChangeEvent.class, "villagerprofession", VillagerCareerChangeEvent::getProfession);
        registrar.eventValue(VillagerCareerChangeEvent.class, "villagerprofession", -1,
                event -> event.getEntity().getProfession());
        var pathfind = com.destroystokyo.paper.event.entity.EntityPathfindEvent.class;
        registrar.eventValue(pathfind, "location", event -> event.getEntity().getLocation());
    }

    /** Projectiles hitting, colliding and being shot. */
    private static void registerProjectiles(Registrar registrar) {
        Events.simple(registrar, "EvtProjectileHit", ProjectileHitEvent.class, "projectile hit");
        Events.simple(registrar, "EvtProjectileCollide", com.destroystokyo.paper.event.entity.ProjectileCollideEvent.class,
                "projectile collide");
        Events.simple(registrar, "EvtShoot", ProjectileLaunchEvent.class, "[projectile] (shoot|launch)");

        registrar.eventValue(ProjectileHitEvent.class, "block", ProjectileHitEvent::getHitBlock);
        registrar.eventValueExcluded(ProjectileHitEvent.class, "entity", "Use 'projectile' and/or 'shooter' in projectile hit events");
        registrar.eventValue(ProjectileHitEvent.class, "entity", Projectile.class, 0, ProjectileHitEvent::getEntity);
        registrar.eventValue(ProjectileHitEvent.class, "direction", event -> event.getHitBlockFace() == null ? null
                : new WorldSyntax.Direction(new org.bukkit.util.Vector(event.getHitBlockFace().getModX(),
                        event.getHitBlockFace().getModY(), event.getHitBlockFace().getModZ()), false));
        registrar.eventValueExcluded(ProjectileLaunchEvent.class, "entity", "Use 'projectile' and/or 'shooter' in shoot events");
        registrar.eventValue(ProjectileLaunchEvent.class, "entity", Projectile.class, 0, ProjectileLaunchEvent::getEntity);
        var collide = com.destroystokyo.paper.event.entity.ProjectileCollideEvent.class;
        registrar.eventValue(collide, "entity", Projectile.class, 0,
                com.destroystokyo.paper.event.entity.ProjectileCollideEvent::getEntity);
        registrar.eventValue(collide, "entity", com.destroystokyo.paper.event.entity.ProjectileCollideEvent::getCollidedWith);
    }

    /** Vehicles: creating, damaging, destroying, entering, exiting, moving and colliding. */
    private static void registerVehicles(Registrar registrar) {
        registrar.event("EvtVehicleCreate", List.of(VehicleCreateEvent.class), null, "vehicle create", "creat(e|ing|ion of) [a] vehicle");
        registrar.event("EvtVehicleDamage", List.of(VehicleDamageEvent.class), null, "vehicle damage", "damag(e|ing) [a] vehicle");
        registrar.event("EvtVehicleDestroy", List.of(VehicleDestroyEvent.class), null, "vehicle destroy",
                "destr(oy[ing]|uction of) [a] vehicle");
        registrar.event("EvtVehicleEnter", List.of(VehicleEnterEvent.class), null, "vehicle enter", "enter[ing] [a] vehicle");
        registrar.event("EvtVehicleExit", List.of(VehicleExitEvent.class), null, "vehicle exit", "exit[ing] [a] vehicle");
        Events.simple(registrar, "EvtVehicleMove", VehicleMoveEvent.class, "vehicle move");
        registrar.event("EvtVehicleCollision", List.of(VehicleBlockCollisionEvent.class, VehicleEntityCollisionEvent.class),
                info -> {
                    int pattern = info.pattern();
                    List<Class<?>> classes = pattern == 1 ? List.of(VehicleBlockCollisionEvent.class)
                            : pattern == 2 ? List.of(VehicleEntityCollisionEvent.class)
                            : List.of(VehicleBlockCollisionEvent.class, VehicleEntityCollisionEvent.class);
                    return Events.narrowed(classes, (context, args) -> {
                        Object[] types = Events.values(args[0]);
                        if (!(context.event() instanceof VehicleCollisionEvent)) {
                            return false;
                        }
                        if (types == null) {
                            return true;
                        }
                        return switch (context.event()) {
                            case VehicleBlockCollisionEvent block -> Events.any(types,
                                    type -> !(type instanceof EntityKind) && Events.isBlock(type, block.getBlock().getBlockData()));
                            case VehicleEntityCollisionEvent entity -> Events.any(types,
                                    type -> Events.isKind(type, entity.getEntity()));
                            default -> false;
                        };
                    });
                }, "vehicle collision [(with|of) [a[n]] %-itemtypes/blockdatas/entitytypes%]",
                "vehicle block collision [(with|of) [a[n]] %-itemtypes/blockdatas%]",
                "vehicle entity collision [(with|of) [a[n]] %-entitytypes%]");

        registrar.eventValue(VehicleEvent.class, "entity", Vehicle.class, 0, VehicleEvent::getVehicle);
        registrar.eventValue(VehicleEvent.class, "world", event -> event.getVehicle().getWorld());
        registrar.eventValue(VehicleExitEvent.class, "livingentity", VehicleExitEvent::getExited);
        registrar.eventValue(VehicleEnterEvent.class, "entity", VehicleEnterEvent::getEntered);
        registrar.eventValue(VehicleDamageEvent.class, "entity", VehicleDamageEvent::getAttacker);
        registrar.eventValue(VehicleDestroyEvent.class, "entity", VehicleDestroyEvent::getAttacker);
        registrar.eventValue(VehicleEvent.class, "entity", event -> event.getVehicle().getPassengers().isEmpty() ? null
                : event.getVehicle().getPassengers().getFirst());
        registrar.eventValue(VehicleMoveEvent.class, "location", VehicleMoveEvent::getTo);
        registrar.eventValue(VehicleMoveEvent.class, "location", -1, VehicleMoveEvent::getFrom);
        registrar.eventValue(VehicleBlockCollisionEvent.class, "block", VehicleBlockCollisionEvent::getBlock);
        registrar.eventValue(VehicleEntityCollisionEvent.class, "entity", VehicleEntityCollisionEvent::getEntity);
    }
}
