/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import static co.xenastudios.neoskript.paper.EventExpressions.isIn;

import co.xenastudios.neoskript.api.element.Changeable.Mode;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.SettableCondition;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Timespan;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * The fishing event's hook, as Skript has it: what it hooked, how long it waits and how long a fish takes to bite,
 * the angle fish come from, and whether the lure enchantment counts.
 */
final class Fishing {

    private Fishing() {}

    /** Minecraft's wait times before lure, in ticks: 5 to 30 seconds. */
    private static final int MIN_WAIT = 5 * 20;
    private static final int MAX_WAIT = 30 * 20;

    static void register(Registrar registrar) {
        registrar.condition("CondFishingLure", info -> {
            fishing(info, "The 'lure enchantment' condition can only be used in a fishing event.");
            boolean negated = info.pattern() == 1;
            return new SettableCondition() {
                @Override
                public boolean check(TriggerContext context, Object[] arguments) {
                    return context.event() instanceof PlayerFishEvent event && event.getHook().getApplyLure() != negated;
                }

                @Override
                public void set(TriggerContext context, Object[] arguments, boolean value) {
                    if (context.event() instanceof PlayerFishEvent event) {
                        event.getHook().setApplyLure(value != negated);
                    }
                }
            };
        }, "lure enchantment bonus is (applied|active)", "lure enchantment bonus is(n't| not) (applied|active)");
        // Skript 2.17's 'pull in hooked entity' (EffPullHookedEntity) registers this effect's class with its own
        // pattern, so it's this effect at its first pattern: it applies the lure bonus rather than pulling.
        registrar.effect("EffFishingLure", info -> {
            fishing(info, "The 'fishing hook lure' effect can only be used in a fishing event.");
            boolean apply = info.pattern() != 1;
            return (context, args) -> {
                if (context.event() instanceof PlayerFishEvent event) {
                    event.getHook().setApplyLure(apply);
                }
            };
        }, "apply [the] lure enchantment bonus", "remove [the] lure enchantment bonus",
                "(reel|pull) in [the] hook[ed] entity");
        registrar.expression("ExprFishingApproachAngle", "number", info -> {
            fishing(info, "The 'fishing approach angle' expression can only be used in a fishing event.");
            boolean min = info.hasTag("min");
            return new EventChangeable<>(PlayerFishEvent.class, event -> (double) (min ? event.getHook().getMinLureAngle()
                    : event.getHook().getMaxLureAngle()), Number.class, Property.modes(Mode.SET, Mode.RESET, Mode.ADD, Mode.REMOVE),
                    (event, delta, mode) -> {
                FishHook hook = event.getHook();
                float angle = mode == Mode.RESET ? (min ? 0 : 360) : delta == null ? 0 : ((Number) delta[0]).floatValue();
                float current = min ? hook.getMinLureAngle() : hook.getMaxLureAngle();
                float changed = clamp(switch (mode) {
                    case ADD -> current + angle;
                    case REMOVE -> current - angle;
                    default -> angle;
                });
                if (min) {
                    hook.setMinLureAngle(changed);
                } else {
                    hook.setMaxLureAngle(changed);
                }
            });
        }, "(min:min[imum]|max[imum]) fish[ing] approach[ing] angle");
        registrar.expression("ExprFishingBiteTime", "timespan", info -> {
            fishing(info, "The 'fishing bite time' expression can only be used in a fishing event.");
            return new EventChangeable<>(PlayerFishEvent.class, event -> Property.ticks(event.getHook().getTimeUntilBite()),
                    Timespan.class, Property.modes(Mode.ADD, Mode.REMOVE, Mode.SET), (event, delta, mode) -> {
                if (delta == null) {
                    return;
                }
                FishHook hook = event.getHook();
                long ticks = EventChangeable.ticks(delta[0]);
                long changed = switch (mode) {
                    case ADD -> hook.getTimeUntilBite() + ticks;
                    case REMOVE -> hook.getTimeUntilBite() - ticks;
                    default -> ticks;
                };
                hook.setTimeUntilBite((int) Property.fit(1, changed, Integer.MAX_VALUE));
            });
        }, "fish[ing] bit(e|ing) [wait] time");
        registrar.expression("ExprFishingHook", "entity", info -> {
            if (!isIn(info, PlayerFishEvent.class)) {
                String event = info.eventName();
                throw new SyntaxError("There's no entity in " + (event == null ? "this event"
                        : ("aeiou".indexOf(Character.toLowerCase(event.charAt(0))) >= 0 ? "an " : "a ") + event + " event"));
            }
            return new EventChangeable<>(PlayerFishEvent.class, PlayerFishEvent::getHook);
        }, "[the] fish[ing] (hook|bobber)");
        registrar.expression("ExprFishingHookEntity", "entity", info -> {
            fishing(info, "The 'hooked entity' expression can only be used in the fishing event.");
            return new EventChangeable<>(PlayerFishEvent.class, event -> event.getHook().getHookedEntity(), Entity.class,
                    Property.modes(Mode.SET, Mode.DELETE), (event, delta, mode) -> {
                FishHook hook = event.getHook();
                if (mode == Mode.SET) {
                    hook.setHookedEntity(delta == null ? null : (Entity) delta[0]);
                } else if (hook.getHookedEntity() != null && !(hook.getHookedEntity() instanceof Player)) {
                    hook.getHookedEntity().remove();
                }
            });
        }, "[the] hook[ed] entity");
        registrar.expression("ExprFishingWaitTime", "timespan", info -> {
            fishing(info, "The 'fishing wait time' expression can only be used in a fishing event.");
            boolean min = info.hasTag("min");
            return new EventChangeable<>(PlayerFishEvent.class, event -> Property.ticks(min ? event.getHook().getMinWaitTime()
                    : event.getHook().getMaxWaitTime()), Timespan.class, Property.modes(Mode.ADD, Mode.REMOVE, Mode.SET, Mode.RESET),
                    (event, delta, mode) -> {
                FishHook hook = event.getHook();
                long ticks = mode == Mode.RESET ? (min ? MIN_WAIT : MAX_WAIT) : delta == null ? 0 : EventChangeable.ticks(delta[0]);
                long current = min ? hook.getMinWaitTime() : hook.getMaxWaitTime();
                int changed = (int) Property.fit(0, switch (mode) {
                    case ADD -> current + ticks;
                    case REMOVE -> current - ticks;
                    default -> ticks;
                }, Integer.MAX_VALUE);
                if (min) {
                    hook.setMinWaitTime(changed);
                } else {
                    hook.setMaxWaitTime(changed);
                }
            });
        }, "(min:min[imum]|max[imum]) fish[ing] wait[ing] time");
    }

    /** Fails parsing with {@code error} outside a fishing event. */
    private static void fishing(ElementInfo info, String error) {
        if (!isIn(info, PlayerFishEvent.class)) {
            throw new SyntaxError(error);
        }
    }

    private static float clamp(float angle) {
        return Math.min(Math.max(angle, 0f), 360f);
    }
}
