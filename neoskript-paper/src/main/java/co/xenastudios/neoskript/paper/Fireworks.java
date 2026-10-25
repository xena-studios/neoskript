/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.inventory.meta.FireworkMeta;

/**
 * Firework effects, as Skript has them: made from a firework type and colours, and launched at locations;
 * {@code the last launched firework} is the one launched last.
 */
final class Fireworks {

    private Fireworks() {}

    /** The firework launched last on each thread, for {@code the last launched firework}. */
    static final ThreadLocal<Entity> LAST_LAUNCHED = new ThreadLocal<>();

    static void register(Registrar registrar) {
        registrar.type("fireworkeffect", "firework effect", FireworkEffect.class);
        registrar.text(FireworkEffect.class, effect -> "Firework effect " + effect);
        registrar.variableNameText(FireworkEffect.class, effect -> "firework effect " + effect);
        // An exploding firework's first effect, which is what a firework effect left out is there.
        registrar.eventValue(FireworkExplodeEvent.class, "fireworkeffect", event -> {
            List<FireworkEffect> effects = event.getEntity().getFireworkMeta().getEffects();
            return effects.isEmpty() ? null : effects.getFirst();
        });
        registrar.eventDefault("fireworkeffect");

        registrar.expression("ExprFireworkEffect", "fireworkeffect", info -> {
            int mark = info.mark();
            boolean flicker = mark == 2 || mark > 3;
            boolean trail = mark >= 3;
            boolean fade = info.pattern() == 1;
            return (context, args) -> {
                if (!(args[0] instanceof FireworkEffect.Type type)) {
                    return null;
                }
                FireworkEffect.Builder builder = FireworkEffect.builder().with(type);
                for (Object colour : Property.of(args[1])) {
                    if (colour instanceof Colors.Colour c) {
                        builder.withColor(WorldEvents.firework(c));
                    }
                }
                if (fade) {
                    for (Object colour : Property.of(args[2])) {
                        if (colour instanceof Colors.Colour c) {
                            builder.withFade(WorldEvents.firework(c));
                        }
                    }
                }
                return builder.flicker(flicker).trail(trail).build();
            };
        }, "(1¦|2¦flickering|3¦trailing|4¦flickering trailing|5¦trailing flickering) %fireworktype% [firework [effect]] colo[u]red %colors%",
                "(1¦|2¦flickering|3¦trailing|4¦flickering trailing|5¦trailing flickering) %fireworktype% [firework [effect]] colo[u]red %colors% fad(e|ing) [to] %colors%");
        registrar.effect("EffFireworkLaunch", info -> (context, args) -> {
            List<FireworkEffect> effects = new ArrayList<>();
            for (Object effect : Property.of(args[0])) {
                if (effect instanceof FireworkEffect e) {
                    effects.add(e);
                }
            }
            int power = args[2] instanceof Number number ? number.intValue() : 1;
            int fitted = Math.min(127, Math.max(0, power));
            for (Object place : Property.of(args[1])) {
                if (!(place instanceof Location location) || location.getWorld() == null) {
                    continue;
                }
                Firework firework = location.getWorld().spawn(location, Firework.class, spawned -> {
                    FireworkMeta meta = spawned.getFireworkMeta();
                    meta.addEffects(effects);
                    meta.setPower(fitted);
                    spawned.setFireworkMeta(meta);
                });
                LAST_LAUNCHED.set(firework);
            }
        }, "(launch|deploy) [[a] firework [with effect[s]]] %fireworkeffects% at %locations% [([with] (duration|power)|timed) %number%]");
    }
}
