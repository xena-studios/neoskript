/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Timespan;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Potion effects: their types, making them, applying them, and reading an entity's. */
final class Potions {

    /** How long a potion effect lasts when no time is given, as in Skript. */
    private static final int DEFAULT_TICKS = 15 * 20;

    /** Names Skript accepts besides the registry's, from before Minecraft renamed them. */
    private static final Map<String, String> OLD_NAMES = Map.ofEntries(
            Map.entry("confusion", "nausea"), Map.entry("damage resistance", "resistance"),
            Map.entry("fast digging", "haste"), Map.entry("fast mining", "haste"),
            Map.entry("slow digging", "mining_fatigue"), Map.entry("slow mining", "mining_fatigue"),
            Map.entry("harm", "instant_damage"), Map.entry("damage", "instant_damage"),
            Map.entry("heal", "instant_health"), Map.entry("healing", "instant_health"),
            Map.entry("jump", "jump_boost"), Map.entry("slow", "slowness"), Map.entry("increase damage", "strength"),
            Map.entry("bad luck", "unluck"), Map.entry("dolphin's grace", "dolphins_grace"));

    private static volatile Map<String, PotionEffectType> names;

    private Potions() {}

    static void register(Registrar registrar) {
        registrar.type("potioneffecttype", "potion effect type", PotionEffectType.class);
        registrar.type("skriptpotioneffect", "potion effect", PotionEffect.class);
        registrar.literalParser("potioneffecttype", Potions::type);
        // 'apply speed to player': a type alone is its effect at tier 1, for the default time.
        registrar.converter(PotionEffectType.class, PotionEffect.class, type -> new PotionEffect(type, DEFAULT_TICKS, 0));
        registrar.parser("potioneffecttype", Potions::type);
        registrar.text(PotionEffectType.class, Potions::name);
        registrar.text(PotionEffect.class, effect -> name(effect.getType()) + " of tier " + (effect.getAmplifier() + 1)
                + (effect.isInfinite() ? "" : " for " + new Timespan(effect.getDuration() * 50L)));

        registrar.expression("ExprSecPotionEffect", "skriptpotioneffect", info -> {
            int pattern = info.pattern();
            boolean ambient = info.hasTag("ambient");
            return (context, args) -> {
                if (pattern == 3) {
                    if (!(args[1] instanceof PotionEffect base)) {
                        return null;
                    }
                    PotionEffectType type = args[0] instanceof PotionEffectType t ? t : base.getType();
                    return new PotionEffect(type, base.getDuration(), base.getAmplifier(), base.isAmbient(),
                            base.hasParticles(), base.hasIcon());
                }
                if (!(args[0] instanceof PotionEffectType type)) {
                    return null;
                }
                int amplifier = args[1] instanceof Number tier ? Math.max(0, (int) Math.round(tier.doubleValue()) - 1) : 0;
                int ticks = pattern == 0
                        ? args[2] instanceof Timespan time ? (int) time.ticks() : DEFAULT_TICKS
                        : PotionEffect.INFINITE_DURATION;
                return new PotionEffect(type, ticks, amplifier, ambient);
            };
        }, "[a[n]] [ambient:ambient] potion effect of %potioneffecttype% [[of tier] %number%] [for %timespan%]",
                "[an] (infinite|permanent) [ambient:ambient] potion effect of %potioneffecttype% [[of tier] %number%]",
                "[an] (infinite|permanent) [ambient:ambient] %potioneffecttype% [[of tier] %number%] [potion [effect]]",
                "[a] potion effect [of %potioneffecttype%] (from|using|based on) %skriptpotioneffect%");

        // 'speed 5': an effect of that tier, for the default time.
        registrar.expression("ExprSecPotionEffect-2", "skriptpotioneffect", info -> (context, args) ->
                args[0] instanceof PotionEffectType type && args[1] instanceof Number tier
                        ? new PotionEffect(type, DEFAULT_TICKS, Math.max(0, (int) Math.round(tier.doubleValue()) - 1)) : null,
                "%*potioneffecttype% %*integer%");

        registrar.effect("EffApplyPotionEffect", info -> {
            boolean entitiesFirst = info.pattern() == 1;
            return (context, args) -> {
                Object[] effects = (Object[]) args[entitiesFirst ? 1 : 0];
                Object[] entities = (Object[]) args[entitiesFirst ? 0 : 1];
                for (Object entity : entities) {
                    if (!(entity instanceof LivingEntity living)) {
                        continue;
                    }
                    for (Object value : effects) {
                        if (value instanceof PotionEffect effect) {
                            living.addPotionEffect(args[2] instanceof Timespan time
                                    ? effect.withDuration((int) time.ticks()) : effect);
                        }
                    }
                }
            };
        }, "(apply|grant) %skriptpotioneffects% to %livingentities% [for %timespan%]",
                "(affect|afflict) %livingentities% with %skriptpotioneffects% [for %timespan%]");

        registrar.expression("ExprPotionEffects", "skriptpotioneffect", info -> (context, args) -> {
            List<Object> effects = new ArrayList<>();
            for (Object entity : (Object[]) args[0]) {
                if (entity instanceof LivingEntity living) {
                    effects.addAll(living.getActivePotionEffects());
                }
            }
            return effects.toArray();
        }, "[the] [active] potion effects of %livingentities%", "%livingentities%'[s] [active] potion effects");
        registrar.expression("ExprPotionEffect", "skriptpotioneffect", info -> (context, args) -> {
            List<Object> effects = new ArrayList<>();
            for (Object entity : (Object[]) args[1]) {
                if (entity instanceof LivingEntity living) {
                    for (Object type : (Object[]) args[0]) {
                        PotionEffect effect = type instanceof PotionEffectType t ? living.getPotionEffect(t) : null;
                        if (effect != null) {
                            effects.add(effect);
                        }
                    }
                }
            }
            return effects.size() == 1 ? effects.getFirst() : effects.toArray();
        }, "[the] [active] %potioneffecttypes% [potion] effect[s] of %livingentities%",
                "%livingentities%'[s] [active] %potioneffecttypes% [potion] effect[s]");

        registrar.expression("ExprPotionAmplifier", "integer", info -> (context, args) -> map(args[0],
                effect -> (long) effect.getAmplifier() + 1),
                "[the] ([potion] amplifier|potion tier|potion level)[s] [of %skriptpotioneffects%]",
                "%skriptpotioneffects%'[s] ([potion] amplifier|potion tier|potion level)[s]");
        registrar.expression("ExprPotionDuration", "timespan", info -> (context, args) -> map(args[0],
                effect -> effect.isInfinite() ? null : new Timespan(effect.getDuration() * 50L)),
                "[the] ([potion] duration|potion length)[s] [of %skriptpotioneffects%]",
                "%skriptpotioneffects%'[s] ([potion] duration|potion length)[s]");

        registrar.condition("CondHasPotion", info -> {
            int pattern = info.pattern();
            boolean negated = pattern % 2 == 1;
            return (context, args) -> {
                Object[] entities = (Object[]) args[0];
                if (entities.length == 0) {
                    return false;
                }
                for (Object entity : entities) {
                    if (!(entity instanceof LivingEntity living)) {
                        return false;
                    }
                    boolean has;
                    if (pattern < 2) {
                        has = !living.getActivePotionEffects().isEmpty();
                    } else {
                        has = true;
                        for (Object value : (Object[]) args[1]) {
                            if (value instanceof PotionEffect effect && !living.hasPotionEffect(effect.getType())) {
                                has = false;
                            }
                        }
                    }
                    if (has == negated) {
                        return false;
                    }
                }
                return true;
            };
        }, "%livingentities% (has|have) ([any|a[n]] [active] potion effect[s]|[any|a] potion effect[s] active)",
                "%livingentities% (doesn't|does not|do not|don't) have ([any|a[n]] [active] potion effect[s]|[any|a] potion effect[s] active)",
                "%livingentities% (has|have) %skriptpotioneffects% [active]",
                "%livingentities% (doesn't|does not|do not|don't) have %skriptpotioneffects% [active]");
        registrar.condition("CondIsPotionInstant", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> {
                Object[] types = (Object[]) args[0];
                for (Object type : types) {
                    if (!(type instanceof PotionEffectType t) || t.isInstant() == negated) {
                        return false;
                    }
                }
                return types.length > 0;
            };
        }, "%potioneffecttypes% (is|are) instant", "%potioneffecttypes% (isn't|is not|aren't|are not) instant");
    }

    private static Object map(Object effects, java.util.function.Function<PotionEffect, Object> getter) {
        List<Object> values = new ArrayList<>();
        for (Object value : effects instanceof Object[] array ? array : new Object[] {effects}) {
            if (value instanceof PotionEffect effect) {
                Object result = getter.apply(effect);
                if (result != null) {
                    values.add(result);
                }
            }
        }
        return values.size() == 1 ? values.getFirst() : values.toArray();
    }

    /** The potion effect type {@code text} names, by its name or an old one, or {@code null}. */
    static PotionEffectType type(String text) {
        Map<String, PotionEffectType> known = names;
        if (known == null) {
            known = new HashMap<>();
            for (PotionEffectType type : RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT)) {
                known.put(name(type), type);
            }
            for (Map.Entry<String, String> old : OLD_NAMES.entrySet()) {
                PotionEffectType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT)
                        .get(NamespacedKey.minecraft(old.getValue()));
                if (type != null) {
                    known.putIfAbsent(old.getKey(), type);
                }
            }
            names = known;
        }
        return known.get(text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
    }

    static String name(PotionEffectType type) {
        return type.getKey().getKey().replace('_', ' ');
    }
}
