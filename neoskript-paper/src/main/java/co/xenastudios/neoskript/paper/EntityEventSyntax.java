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
import java.util.function.Function;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;

/**
 * Syntax for entity events that only works in them: the tamer, what hatches from a thrown egg,
 * whether a leash drops, where a mob is pathfinding to, and how hard a bow was drawn.
 */
final class EntityEventSyntax {

    private EntityEventSyntax() {}

    static void register(Registrar registrar) {
        registrar.expression("ExprTamer", "player", info -> {
            restrict(info, "the tamer", "the tame event", EntityTameEvent.class);
            return (context, args) -> context.event() instanceof EntityTameEvent event
                    && event.getOwner() instanceof Player player ? player : null;
        }, "[the] tamer");
        registrar.expression("ExprProjectileForce", "number", info -> {
            restrict(info, "the projectile force", "the entity shoot bow event", EntityShootBowEvent.class);
            return (context, args) -> context.event() instanceof EntityShootBowEvent event ? (Object) (double) event.getForce()
                    : null;
        }, "[the] projectile force");
        registrar.expression("ExprPathfindingLocation", "location", info -> {
            restrict(info, "the pathfinding target location", "the pathfind event",
                    com.destroystokyo.paper.event.entity.EntityPathfindEvent.class);
            return (context, args) -> context.event() instanceof com.destroystokyo.paper.event.entity.EntityPathfindEvent event
                    ? event.getLoc() : null;
        }, "[the] path[ ]finding target location");
        registrar.expression("ExprPathfindingTarget", "entity", info -> {
            restrict(info, "the pathfinding target", "the pathfind event",
                    com.destroystokyo.paper.event.entity.EntityPathfindEvent.class);
            return (context, args) -> context.event() instanceof com.destroystokyo.paper.event.entity.EntityPathfindEvent event
                    ? event.getTargetEntity() : null;
        }, "[the] path[ ]finding target [entity]");
        registerLeashes(registrar);
        registerEggs(registrar);
    }

    /**
     * Fails parsing outside the events of {@code eventClasses}, as Skript does for syntax that only works in
     * some events: {@code 'the tamer' can only be used in the tame event}.
     */
    private static void restrict(ElementInfo info, String written, String events, Class<?>... eventClasses) {
        for (Class<?> eventClass : info.eventClasses()) {
            for (Class<?> wanted : eventClasses) {
                if (wanted.isAssignableFrom(eventClass)) {
                    return;
                }
            }
        }
        throw new SyntaxError("'" + written + "' can only be used in " + events);
    }

    private static void registerLeashes(Registrar registrar) {
        registrar.condition("CondLeashWillDrop", info -> {
            restrict(info, "the leash will drop", "the leash / unleash event", EntityUnleashEvent.class);
            boolean negated = info.hasTag("not");
            return new SettableCondition() {
                @Override
                public boolean check(TriggerContext context, Object[] args) {
                    return context.event() instanceof EntityUnleashEvent event && event.isDropLeash() != negated;
                }

                @Override
                public void set(TriggerContext context, Object[] args, boolean value) {
                    if (context.event() instanceof EntityUnleashEvent event) {
                        event.setDropLeash(value);
                    }
                }
            };
        }, "[the] (lead|leash) [item] (will|not:(won't|will not)) (drop|be dropped)");
        registrar.effect("EffDropLeash", info -> {
            restrict(info, "allow the leash to drop", "the leash / unleash event", EntityUnleashEvent.class);
            boolean drop = info.pattern() == 0;
            return (context, args) -> {
                if (context.event() instanceof EntityUnleashEvent event) {
                    event.setDropLeash(drop);
                }
            };
        }, "(force|allow) [the] (lead|leash) [item] to drop", "(block|disallow|prevent) [the] (lead|leash) [item] from dropping");
    }

    private static void registerEggs(Registrar registrar) {
        registrar.condition("CondWillHatch", info -> {
            restrict(info, "the egg will hatch", "the egg throw event", PlayerEggThrowEvent.class);
            boolean negated = !info.hasTag("will");
            return new SettableCondition() {
                @Override
                public boolean check(TriggerContext context, Object[] args) {
                    return context.event() instanceof PlayerEggThrowEvent event && event.isHatching() != negated;
                }

                @Override
                public void set(TriggerContext context, Object[] args, boolean value) {
                    if (context.event() instanceof PlayerEggThrowEvent event) {
                        event.setHatching(value);
                    }
                }
            };
        }, "[the] egg (:will|will not|won't) hatch");
        registrar.effect("EffMakeEggHatch", info -> {
            restrict(info, "make the egg hatch", "the egg throw event", PlayerEggThrowEvent.class);
            boolean not = info.hasTag("not");
            return (context, args) -> {
                if (context.event() instanceof PlayerEggThrowEvent event) {
                    event.setHatching(!not);
                    // Hatching makes something hatch.
                    if (!not && event.getNumHatches() == 0) {
                        event.setNumHatches((byte) 1);
                    }
                }
            };
        }, "make [the] egg [:not] hatch");
        registrar.expression("ExprHatchingNumber", "integer", info -> {
            restrict(info, "the hatching number", "the egg throw event", PlayerEggThrowEvent.class);
            return new EggValue(event -> (long) event.getNumHatches(), Number.class, (event, delta, mode) -> {
                int value = ((Number) delta[0]).intValue();
                if (mode != Changeable.Mode.SET) {
                    value = event.getNumHatches() + (mode == Changeable.Mode.REMOVE ? -value : value);
                }
                event.setNumHatches((byte) Math.min(Math.max(0, value), Byte.MAX_VALUE));
            }, Changeable.Mode.SET, Changeable.Mode.ADD, Changeable.Mode.REMOVE);
        }, "[the] hatching number");
        registrar.expression("ExprHatchingType", "entitytype", info -> {
            restrict(info, "the hatching entity type", "the egg throw event", PlayerEggThrowEvent.class);
            return new EggValue(event -> EntityKind.of(event.getHatchingType()), EntityKind.class, (event, delta, mode) -> {
                // Resetting hatches chickens again; only types that can be spawned hatch.
                org.bukkit.entity.EntityType type = delta == null ? org.bukkit.entity.EntityType.CHICKEN
                        : ((EntityKind) delta[0]).type();
                if (type != null && type.isSpawnable()) {
                    event.setHatchingType(type);
                }
            }, Changeable.Mode.SET, Changeable.Mode.RESET);
        }, "[the] hatching entity [type]");
    }

    /** A value of an egg throw event, changeable in {@code modes}. */
    private static final class EggValue implements Expression, Changeable {

        /** Changes the event with the values given, {@code null} for {@code reset}. */
        interface Change {
            void apply(PlayerEggThrowEvent event, Object[] delta, Mode mode);
        }

        private final Function<PlayerEggThrowEvent, Object> getter;
        private final Class<?> valueClass;
        private final Change change;
        private final java.util.Set<Mode> modes;

        EggValue(Function<PlayerEggThrowEvent, Object> getter, Class<?> valueClass, Change change, Mode... modes) {
            this.getter = getter;
            this.valueClass = valueClass;
            this.change = change;
            this.modes = java.util.Set.of(modes);
        }

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return context.event() instanceof PlayerEggThrowEvent event ? getter.apply(event) : null;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            if (!modes.contains(mode)) {
                return null;
            }
            return mode == Mode.RESET || mode == Mode.DELETE ? new Class<?>[0] : new Class<?>[] {valueClass};
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (delta != null && delta.length == 0) {
                delta = null;
            }
            if (delta == null && mode != Mode.RESET || !(context.event() instanceof PlayerEggThrowEvent event)) {
                return;
            }
            change.apply(event, delta, mode);
        }
    }
}
