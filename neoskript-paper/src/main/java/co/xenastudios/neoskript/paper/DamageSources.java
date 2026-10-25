/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import co.xenastudios.neoskript.runtime.Values;
import io.papermc.paper.registry.RegistryKey;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Skript's damage source module: damage sources and damage types, making a custom damage source in a section,
 * the parts of one, and damaging with one ({@code EffHealth}, which is here as its patterns name damage causes
 * and sources).
 */
final class DamageSources {

    private DamageSources() {}

    private static final String OUTSIDE_SECTION =
            "You cannot change the attributes of a damage source outside a 'custom damage source' section.";
    private static final String NOT_THIS_SOURCE =
            "You can only change the attributes of the damage source being created in this section.";

    static void register(Registrar registrar) {
        registrar.type("damagesource", "damage source", DamageSource.class);
        registrar.eventDefault("damagesource");
        EnumTypes.registryType(registrar, "damagetype", "damage type", DamageType.class, RegistryKey.DAMAGE_TYPE,
                "damage types",
                "cramming", "cramming, entity cramming",
                "drown", "drown, drowning",
                "in_wall", "in wall, suffocation",
                "lightning_bolt", "lightning, lightning bolt",
                "out_of_world", "out of world, void",
                "spit", "spit, llama spit",
                "sulfur_cube_hot", "sulfur cube hot, hot sulfur cube, magma sulfur cube");
        // A damage type is saved by its key, as Skript's registry serializer does.
        registrar.storage("damagetype", DamageType.class, new co.xenastudios.neoskript.api.storage.VariableCodec<>() {
            @Override
            public void encode(DamageType type, co.xenastudios.neoskript.api.storage.BinaryWriter out) {
                out.beginObject(1);
                out.stringField("name", type.getKey().toString());
            }

            @Override
            public DamageType decode(co.xenastudios.neoskript.api.storage.BinaryReader in) {
                in.beginObject();
                org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(in.stringField("name"));
                return key == null ? null : io.papermc.paper.registry.RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.DAMAGE_TYPE).get(key);
            }
        });
        registrar.eventValue(EntityDamageEvent.class, "damagesource", EntityDamageEvent::getDamageSource);
        registrar.eventValue(EntityDeathEvent.class, "damagesource", EntityDeathEvent::getDamageSource);

        // 'a custom damage source:': its section sets it up; stored, it becomes the damage source it describes.
        registrar.expression("ExprSecDamageSource", "damagesource", info -> (context, args) -> {
            CustomDamageSource source = new CustomDamageSource();
            if (Values.single(args[0]) instanceof DamageType type) {
                source.damageType = type;
            }
            CREATED.set(source);
            return source;
        }, "[a] custom damage source [(with|using) [the|a] [damage type [of]] %damagetype%]");
        registrar.sectionExpression("ExprSecDamageSource", "damagesource");
        registerFinisher(registrar);
        registrar.expression("ExprCreatedDamageSource", "damagesource", info -> {
            if (!"damagesource".equals(info.sectionType())) {
                throw new SyntaxError("'" + (info.hasTag("the") ? "the " : "") + "created damage source' can only be used in ");
            }
            return (context, args) -> CREATED.get();
        }, "[the:the] created damage source");

        attribute(registrar, "ExprDamageType", "damagetype", "damage type", DamageType.class, false,
                DamageSource::getDamageType, (source, value) -> source.damageType = (DamageType) value);
        attribute(registrar, "ExprCausingEntity", "entity", "(causing|responsible) entity", Entity.class, true,
                DamageSource::getCausingEntity, (source, value) -> source.causingEntity = (Entity) value);
        attribute(registrar, "ExprDirectEntity", "entity", "direct entity", Entity.class, true,
                DamageSource::getDirectEntity, (source, value) -> source.directEntity = (Entity) value);
        attribute(registrar, "ExprDamageLocation", "location", "damage location", Location.class, true,
                DamageSource::getDamageLocation, (source, value) -> source.damageLocation = (Location) value);
        registrar.expression("ExprFoodExhaustion", "number", info -> (context, args) ->
                map(args[0], source -> (double) source.getFoodExhaustion()),
                defaultProperty("food exhaustion"));
        registrar.expression("ExprSourceLocation", "location", info -> (context, args) ->
                map(args[0], DamageSource::getSourceLocation), defaultProperty("source location"));

        registrar.condition("CondScalesWithDifficulty", info -> {
            boolean negated = info.pattern() % 2 == 1;
            return (context, args) -> co.xenastudios.neoskript.api.element.Condition.check(Values.array(args[0]),
                    info.and(0), value -> value instanceof DamageSource source && source.scalesWithDifficulty() != negated);
        }, "%damagesources% ((does|do) scale|scales) damage with difficulty",
                "%damagesources% (do not|don't|does not|doesn't) scale damage with difficulty",
                "%damagesources%'[s] damage ((does|do) scale|scales) with difficulty",
                "%damagesources%'[s] damage (do not|don't|does not|doesn't) scale with difficulty");
        registrar.condition("CondWasIndirect", info -> {
            boolean negated = info.pattern() == 1;
            boolean indirect = info.hasTag("in");
            return (context, args) -> co.xenastudios.neoskript.api.element.Condition.check(Values.array(args[0]),
                    info.and(0), value -> value instanceof DamageSource source && (source.isIndirect() == indirect) != negated);
        }, "%damagesources% (was|were) ([:in]directly caused|caused [:in]directly)",
                "%damagesources% (was not|wasn't|were not|weren't) ([:in]directly caused|caused [:in]directly)");

        registerHealth(registrar);
    }

    /** The custom damage source last made on this thread, whose section is running: {@code created damage source}. */
    private static final ThreadLocal<CustomDamageSource> CREATED = new ThreadLocal<>();

    /** Stored in a variable, a custom damage source becomes the damage source it describes, as in Skript. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerFinisher(Registrar registrar) {
        registrar.cloner((Class) CustomDamageSource.class,
                (java.util.function.UnaryOperator) value -> ((CustomDamageSource) value).finish());
    }

    /** {@code [the] <property> [of %damagesources%]} and {@code %damagesources%'[s] <property>}. */
    private static String[] defaultProperty(String property) {
        return new String[] {"[the] " + property + " [of %damagesources%]", "%damagesources%'[s] " + property};
    }

    /**
     * A part of a damage source, which can be set (and with {@code deletable}, deleted) only for the one a
     * {@code custom damage source} section is making, as written there: left out, or {@code event-damage source}.
     */
    private static void attribute(Registrar registrar, String id, String returnType, String property, Class<?> valueClass,
            boolean deletable, Function<DamageSource, Object> getter,
            java.util.function.BiConsumer<CustomDamageSource, Object> setter) {
        registrar.expression(id, returnType, info -> new Part(info, valueClass, deletable, getter, setter),
                defaultProperty(property));
    }

    private record Part(ElementInfo info, Class<?> valueClass, boolean deletable, Function<DamageSource, Object> getter,
            java.util.function.BiConsumer<CustomDamageSource, Object> setter) implements Expression, Changeable {

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return map(arguments[0], getter);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            if (!"damagesource".equals(info.sectionType())) {
                throw new SyntaxError(OUTSIDE_SECTION);
            }
            if (info.isList(0) || !info.isDefault(0)) {
                throw new SyntaxError(NOT_THIS_SOURCE);
            }
            if (mode == Mode.SET || (deletable && mode == Mode.DELETE)) {
                return mode == Mode.DELETE ? new Class<?>[0] : new Class<?>[] {valueClass};
            }
            return null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (Values.single(arguments[0]) instanceof CustomDamageSource source) {
                setter.accept(source, delta == null || delta.length == 0 ? null : delta[0]);
            }
        }
    }

    private static Object map(Object owners, Function<DamageSource, Object> getter) {
        Object[] values = Values.array(owners);
        java.util.List<Object> results = new java.util.ArrayList<>(values.length);
        for (Object value : values) {
            if (value instanceof DamageSource source) {
                Object result = getter.apply(source);
                if (result != null) {
                    results.add(result);
                }
            }
        }
        return results.size() == 1 ? results.getFirst() : results.toArray();
    }

    /**
     * A damage source being made by a {@code custom damage source} section, whose parts can still be set. Until
     * then it reads as the damage source it would make; {@link #finish} makes it.
     */
    static final class CustomDamageSource implements DamageSource {
        DamageType damageType = DamageType.GENERIC;
        Entity causingEntity;
        Entity directEntity;
        Location damageLocation;

        /** The damage source these parts make, or {@code null}, with an error, if they don't make one. */
        DamageSource finish() {
            if (causingEntity != null && directEntity == null) {
                RuntimeErrors.report("You must set a 'direct entity' when setting a 'causing entity'.");
                return null;
            }
            return build();
        }

        private DamageSource build() {
            DamageSource.Builder builder = DamageSource.builder(damageType);
            if (damageLocation != null) {
                builder = builder.withDamageLocation(damageLocation.clone());
            }
            if (causingEntity != null) {
                builder = builder.withCausingEntity(causingEntity);
            }
            if (directEntity != null) {
                builder = builder.withDirectEntity(directEntity);
            }
            return builder.build();
        }

        /** The damage source made so far, if the parts set so far make one. */
        private DamageSource built() {
            return causingEntity != null && directEntity == null ? null : build();
        }

        @Override
        public DamageType getDamageType() {
            return damageType;
        }

        @Override
        public Entity getCausingEntity() {
            DamageSource built = built();
            return built == null ? causingEntity : built.getCausingEntity();
        }

        @Override
        public Entity getDirectEntity() {
            DamageSource built = built();
            return built == null ? directEntity : built.getDirectEntity();
        }

        @Override
        public Location getDamageLocation() {
            return damageLocation == null ? null : damageLocation.clone();
        }

        @Override
        public Location getSourceLocation() {
            DamageSource built = built();
            return built == null ? getDamageLocation() : built.getSourceLocation();
        }

        @Override
        public boolean isIndirect() {
            DamageSource built = built();
            return built == null ? causingEntity != directEntity : built.isIndirect();
        }

        @Override
        public float getFoodExhaustion() {
            return damageType.getExhaustion();
        }

        @Override
        public boolean scalesWithDifficulty() {
            DamageSource built = built();
            return built != null && built.scalesWithDifficulty();
        }
    }

    /** A damage source for a damage cause, as Skript picks one for {@code damage X by 2 with fake damage cause}. */
    private static DamageSource fromCause(DamageCause cause) {
        return DamageSource.builder(switch (cause.name()) {
            case "KILL", "SUICIDE" -> DamageType.GENERIC_KILL;
            case "WORLD_BORDER", "VOID" -> DamageType.OUT_OF_WORLD;
            case "CONTACT" -> DamageType.CACTUS;
            case "SUFFOCATION" -> DamageType.IN_WALL;
            case "FALL" -> DamageType.FALL;
            case "FIRE" -> DamageType.ON_FIRE;
            case "FIRE_TICK" -> DamageType.IN_FIRE;
            case "LAVA" -> DamageType.LAVA;
            case "DROWNING" -> DamageType.DROWN;
            case "BLOCK_EXPLOSION", "ENTITY_EXPLOSION" -> DamageType.EXPLOSION;
            case "LIGHTNING" -> DamageType.LIGHTNING_BOLT;
            case "STARVATION" -> DamageType.STARVE;
            case "MAGIC", "POISON" -> DamageType.MAGIC;
            case "WITHER" -> DamageType.WITHER;
            case "FALLING_BLOCK" -> DamageType.FALLING_BLOCK;
            case "THORNS" -> DamageType.THORNS;
            case "DRAGON_BREATH" -> DamageType.DRAGON_BREATH;
            case "FLY_INTO_WALL" -> DamageType.FLY_INTO_WALL;
            case "HOT_FLOOR" -> DamageType.HOT_FLOOR;
            case "CAMPFIRE" -> DamageType.CAMPFIRE;
            case "CRAMMING" -> DamageType.CRAMMING;
            case "DRYOUT" -> DamageType.DRY_OUT;
            case "FREEZE" -> DamageType.FREEZE;
            case "SONIC_BOOM" -> DamageType.SONIC_BOOM;
            default -> DamageType.GENERIC;
        }).build();
    }

    /** 'damage X by 2', 'heal X', 'repair X by 5': entities by hearts, items by durability. */
    private static void registerHealth(Registrar registrar) {
        registrar.effect("EffHealth", info -> {
            // Patterns 3 and 4 damage with a damage cause or source.
            int mode = info.pattern() >= 3 ? 0 : info.pattern();
            int sourceAt = info.pattern() >= 3 ? 2 : -1;
            return (context, args) -> {
                double amount = args[1] instanceof Number n ? n.doubleValue() : Double.NaN;
                DamageSource source = null;
                if (sourceAt >= 0) {
                    Object given = Values.single(args[sourceAt]);
                    source = switch (given) {
                        case CustomDamageSource custom -> custom.finish();
                        case DamageSource real -> real;
                        case DamageCause cause -> fromCause(cause);
                        case null, default -> null;
                    };
                }
                for (Object value : (Object[]) args[0]) {
                    switch (value) {
                        case LivingEntity living when mode == 0 -> {
                            if (Double.isNaN(amount)) {
                                continue;
                            }
                            if (amount < 0) {
                                heal(living, -amount);
                            } else if (source != null) {
                                living.damage(amount * 2, source);
                            } else {
                                living.damage(amount * 2);
                            }
                        }
                        case LivingEntity living when mode == 1 -> {
                            AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
                            double limit = max == null ? 20 : max.getValue();
                            double health = Double.isNaN(amount) ? limit : living.getHealth() + amount * 2;
                            living.setHealth(Math.max(0, Math.min(limit, health)));
                        }
                        case ItemType item when mode != 1 -> item.editMeta(meta -> {
                            if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                                int by = Double.isNaN(amount) ? (mode == 2 ? damageable.getDamage() : 0) : (int) amount;
                                damageable.setDamage(Math.max(0, damageable.getDamage() + (mode == 0 ? by : -by)));
                            }
                        });
                        default -> {}
                    }
                }
            };
        }, "damage %livingentities/itemtypes% by %number% [heart[s]]", "heal %livingentities% [by %-number% [heart[s]]]",
                "repair %itemtypes% [by %-number%]",
                "damage %livingentities% by %number% [heart[s]] with [fake] [damage] cause %damagecause%",
                "damage %livingentities% by %number% [heart[s]] (using|with) %damagesource% [as the source]");
    }

    private static void heal(LivingEntity living, double hearts) {
        AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
        double limit = max == null ? 20 : max.getValue();
        living.setHealth(Math.max(0, Math.min(limit, living.getHealth() + hearts * 2)));
    }
}
