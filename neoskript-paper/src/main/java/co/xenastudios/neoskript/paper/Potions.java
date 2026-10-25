/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Timespan;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Skript's potion module: potion effects that can be changed, entities' and potions' effects, active and hidden
 * (the weaker effects Minecraft keeps underneath a stronger one until it wears off).
 */
final class Potions {

    /** How long a potion effect lasts when no time is given, as in Skript. */
    private static final int DEFAULT_TICKS = 15 * 20;

    private static final String HIDDEN_ERROR = "Hidden potion effects cannot be changed";

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

    /**
     * Skript's potion effect, which can be changed. One that's an entity's active effect changes the entity's
     * when it's changed. What was written for it matters when matching: {@code ambient potion effect of speed}
     * matches ambient speed of any tier and length.
     */
    static final class Effect {
        PotionEffectType type;
        /** From 0, as Minecraft counts; Skript shows it from 1. */
        int amplifier;
        int ticks = DEFAULT_TICKS;
        boolean ambient;
        boolean particles = true;
        boolean icon = true;
        boolean infinite;
        boolean amplifierGiven;
        boolean durationGiven;
        boolean ambientGiven;
        boolean particlesGiven;
        boolean iconGiven;
        /** The entity this is the active effect of, or {@code null}. */
        LivingEntity owner;

        Effect(PotionEffectType type) {
            this.type = type;
        }

        static Effect of(PotionEffect effect, LivingEntity owner) {
            Effect made = new Effect(effect.getType());
            made.amplifier = effect.getAmplifier();
            made.infinite = effect.isInfinite();
            made.ticks = made.infinite ? 0 : effect.getDuration();
            made.ambient = effect.isAmbient();
            made.particles = effect.hasParticles();
            made.icon = effect.hasIcon();
            made.amplifierGiven = made.durationGiven = made.ambientGiven = made.particlesGiven = made.iconGiven = true;
            made.owner = owner;
            return made;
        }

        Effect copy() {
            Effect copy = new Effect(type);
            copy.amplifier = amplifier;
            copy.ticks = ticks;
            copy.ambient = ambient;
            copy.particles = particles;
            copy.icon = icon;
            copy.infinite = infinite;
            copy.amplifierGiven = amplifierGiven;
            copy.durationGiven = durationGiven;
            copy.ambientGiven = ambientGiven;
            copy.particlesGiven = particlesGiven;
            copy.iconGiven = iconGiven;
            return copy;
        }

        PotionEffect bukkit() {
            return new PotionEffect(type, infinite ? PotionEffect.INFINITE_DURATION : Math.max(0, ticks), Math.max(0, amplifier),
                    ambient, particles, icon);
        }

        /** Whether {@code actual} is what this describes: of its type, and as it says for what it says. */
        boolean matches(Effect actual) {
            return type.equals(actual.type) && (!amplifierGiven || amplifier == actual.amplifier)
                    && (!durationGiven || infinite == actual.infinite && (infinite || ticks == actual.ticks))
                    && (!ambientGiven || ambient == actual.ambient) && (!particlesGiven || particles == actual.particles)
                    && (!iconGiven || icon == actual.icon);
        }

        /** After a change: an entity's effect is replaced with this. */
        void changed() {
            if (owner != null && owner.isValid()) {
                replaceActive(owner, this);
            }
        }

        @Override
        public String toString() {
            return name(type) + " of tier " + (amplifier + 1) + (infinite ? " forever" : " for " + new Timespan(ticks * 50L));
        }
    }

    /** Which of an entity's effects: the active ones, the hidden ones, or both. */
    private enum Which { ACTIVE, HIDDEN, BOTH }

    static void register(Registrar registrar) {
        registrar.type("potioneffecttype", "potion effect type", PotionEffectType.class);
        registrar.type("skriptpotioneffect", "potion effect", Effect.class);
        registrar.eventDefault("skriptpotioneffect");
        // Hidden effects can be looked at, not changed.
        registrar.type("hiddenpotioneffect", "hidden potion effect", Effect.class);
        registrar.subtype("hiddenpotioneffect", "skriptpotioneffect");
        registrar.literalParser("potioneffecttype", Potions::type);
        registrar.parser("potioneffecttype", Potions::type);
        registrar.text(PotionEffectType.class, Potions::name);
        registrar.text(Effect.class, Effect::toString);
        registrar.cloner(Effect.class, Effect::copy);
        // 'apply speed to player': a type alone is its effect at tier 1, for the default time.
        registrar.converter(PotionEffectType.class, Effect.class, Effect::new);
        registrar.comparison(Effect.class, Effect.class, (a, b) -> a.matches(b) && b.matches(a));
        registrar.comparison(PotionEffectType.class, Effect.class, (type, effect) -> type.equals(effect.type));

        Map<String, org.bukkit.potion.PotionEffectTypeCategory> categories = new java.util.LinkedHashMap<>();
        for (org.bukkit.potion.PotionEffectTypeCategory category : org.bukkit.potion.PotionEffectTypeCategory.values()) {
            categories.put(category.name().toLowerCase(Locale.ROOT), category);
        }
        registrar.namedType("potioneffecttypecategory", "potion effect type category",
                org.bukkit.potion.PotionEffectTypeCategory.class, categories);
        registrar.text(org.bukkit.potion.PotionEffectTypeCategory.class, category -> category.name().toLowerCase(Locale.ROOT));
        // 'weakness is harmful'.
        registrar.comparison(PotionEffectType.class, org.bukkit.potion.PotionEffectTypeCategory.class,
                (type, category) -> type.getCategory() == category);
        registrar.expression("ExprPotionEffectTypeCategory", "potioneffecttypecategory", info -> (context, args) -> {
            List<Object> found = new ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof PotionEffectType type) {
                    found.add(type.getCategory());
                }
            }
            return found.size() == 1 ? found.getFirst() : found.toArray();
        }, "[the] potion [effect [type]] categor(y|ies) of %potioneffecttypes%",
                "%potioneffecttypes%'[s] potion [effect [type]] categor(y|ies)");

        registerCreation(registrar);
        registerHolders(registrar);
        registerProperties(registrar);
    }

    private static void registerCreation(Registrar registrar) {
        registrar.expression("ExprSecPotionEffect", "skriptpotioneffect", info -> {
            int pattern = info.pattern();
            boolean ambient = info.hasTag("ambient");
            return (context, args) -> {
                if (pattern == 3) {
                    if (!(args[1] instanceof Effect base)) {
                        return null;
                    }
                    Effect made = base.copy();
                    if (args[0] instanceof PotionEffectType type) {
                        made.type = type;
                    }
                    return made;
                }
                if (!(args[0] instanceof PotionEffectType type)) {
                    return null;
                }
                Effect made = new Effect(type);
                if (args[1] instanceof Number tier) {
                    made.amplifier = Math.max(0, co.xenastudios.neoskript.runtime.Values.toInt(tier.doubleValue()) - 1);
                    made.amplifierGiven = true;
                }
                if (pattern == 0) {
                    if (args[2] instanceof Timespan time) {
                        // 'for an eternity' is forever.
                        made.infinite = time.isInfinite();
                        made.ticks = made.infinite ? 0 : (int) Math.min(Integer.MAX_VALUE, time.ticks());
                        made.durationGiven = true;
                    }
                } else {
                    made.infinite = true;
                    made.durationGiven = true;
                }
                if (ambient) {
                    made.ambient = true;
                    made.ambientGiven = true;
                }
                return made;
            };
        }, "[a[n]] [ambient:ambient] potion effect of %potioneffecttype% [[of tier] %-number%] [for %-timespan%]",
                "[an] (infinite|permanent) [ambient:ambient] potion effect of %potioneffecttype% [[of tier] %-number%]",
                "[an] (infinite|permanent) [ambient:ambient] %potioneffecttype% [[of tier] %-number%] [potion [effect]]",
                "[a] potion effect [of %-potioneffecttype%] (from|using|based on) %skriptpotioneffect%");
        registrar.sectionExpression("ExprSecPotionEffect", "skriptpotioneffect");
        // 'speed 5': an effect of that tier, for the default time.
        registrar.expression("ExprSecPotionEffect-2", "skriptpotioneffect", info -> (context, args) -> {
            if (!(args[0] instanceof PotionEffectType type) || !(args[1] instanceof Number tier)) {
                return null;
            }
            Effect made = new Effect(type);
            made.amplifier = Math.max(0, co.xenastudios.neoskript.runtime.Values.toInt(tier.doubleValue()) - 1);
            made.amplifierGiven = true;
            return made;
        }, "%*potioneffecttype% %*integer%");

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
                        Effect effect = effect(value);
                        if (effect == null) {
                            continue;
                        }
                        if (args[2] instanceof Timespan time) {
                            effect = effect.copy();
                            effect.ticks = (int) Math.min(Integer.MAX_VALUE, time.ticks());
                            effect.infinite = false;
                        }
                        living.addPotionEffect(effect.bukkit());
                    }
                }
            };
        }, "(apply|grant) %skriptpotioneffects% to %livingentities% [for %-timespan%]",
                "(affect|afflict) %livingentities% with %skriptpotioneffects% [for %-timespan%]");
    }

    private static void registerHolders(Registrar registrar) {
        String holders = "livingentities/itemtypes/slots";
        registrar.expression("ExprPotionEffects", "skriptpotioneffect", info -> new Effects(info.hasTag("both") ? Which.BOTH
                : info.hasTag("active") ? Which.ACTIVE : null, null, 0),
                "[the] [active:active|both:active and hidden] potion effects of %" + holders + "%",
                "%" + holders + "%'[s] [active:active|both:active and hidden] potion effects");
        registrar.expression("ExprPotionEffects-hidden", "hiddenpotioneffect", info -> {
            livingOnly(info, 0);
            return new Effects(Which.HIDDEN, null, 0);
        }, "[the] hidden potion effects of %" + holders + "%", "%" + holders + "%'[s] hidden potion effects");
        // The types come first in 'the speed effect of X', last in 'X's speed effect'.
        registrar.expression("ExprPotionEffect", "skriptpotioneffect", info -> new Effects(info.hasTag("both") ? Which.BOTH
                : info.hasTag("active") ? Which.ACTIVE : null, info.pattern() == 0 ? 0 : 1, info.pattern() == 0 ? 1 : 0),
                "[the] [active:active|both:active and hidden] %potioneffecttypes% [potion] effect[s] of %" + holders + "%",
                "%" + holders + "%'[s] [active:active|both:active and hidden] %potioneffecttypes% [potion] effect[s]");
        registrar.expression("ExprPotionEffect-hidden", "hiddenpotioneffect", info -> {
            livingOnly(info, info.pattern() == 0 ? 1 : 0);
            return new Effects(Which.HIDDEN, info.pattern() == 0 ? 0 : 1, info.pattern() == 0 ? 1 : 0);
        }, "[the] hidden %potioneffecttypes% [potion] effect[s] of %" + holders + "%",
                "%" + holders + "%'[s] hidden %potioneffecttypes% [potion] effect[s]");

        registrar.condition("CondHasPotion", info -> {
            int pattern = info.pattern();
            boolean negated = pattern % 2 == 1;
            boolean and = info.and(0);
            return (context, args) -> {
                Object[] entities = (Object[]) args[0];
                if (entities.length == 0) {
                    return negated;
                }
                return Condition.check(entities, and, entity -> {
                    if (!(entity instanceof LivingEntity living)) {
                        return negated;
                    }
                    boolean has;
                    if (pattern < 2) {
                        has = !living.getActivePotionEffects().isEmpty();
                    } else {
                        has = true;
                        for (Object value : (Object[]) args[1]) {
                            Effect wanted = effect(value);
                            boolean found = false;
                            if (wanted != null) {
                                for (PotionEffect active : living.getActivePotionEffects()) {
                                    found |= wanted.matches(Effect.of(active, null));
                                }
                            }
                            has &= found;
                        }
                    }
                    return has != negated;
                });
            };
        }, "%livingentities% (has|have) ([any|a[n]] [active] potion effect[s]|[any|a] potion effect[s] active)",
                "%livingentities% (doesn't|does not|do not|don't) have ([any|a[n]] [active] potion effect[s]|[any|a] potion effect[s] active)",
                "%livingentities% (has|have) %skriptpotioneffects% [active]",
                "%livingentities% (doesn't|does not|do not|don't) have %skriptpotioneffects% [active]");
    }

    private static void registerProperties(Registrar registrar) {
        registrar.expression("ExprPotionAmplifier", "integer", info -> {
            changeable(info, 0);
            return new Property(false);
        }, "[the] ([potion] amplifier|potion tier|potion level)[s] of %skriptpotioneffects%",
                "%skriptpotioneffects%'[s] ([potion] amplifier|potion tier|potion level)[s]");
        registrar.expression("ExprPotionDuration", "timespan", info -> {
            changeable(info, 0);
            return new Property(true);
        }, "[the] ([potion] duration|potion length)[s] of %skriptpotioneffects%",
                "%skriptpotioneffects%'[s] ([potion] duration|potion length)[s]");

        flag(registrar, "Ambient", "ambient", effect -> effect.ambient, (effect, on) -> {
            effect.ambient = on;
            effect.ambientGiven = true;
        });
        flag(registrar, "Infinite", "infinite", effect -> effect.infinite, (effect, on) -> {
            effect.infinite = on;
            if (!on && effect.ticks <= 0) {
                effect.ticks = DEFAULT_TICKS;
            }
            effect.durationGiven = true;
        });
        shown(registrar, "Particles", "particles", effect -> effect.particles, (effect, on) -> {
            effect.particles = on;
            effect.particlesGiven = true;
        });
        shown(registrar, "Icon", "icon", effect -> effect.icon, (effect, on) -> {
            effect.icon = on;
            effect.iconGiven = true;
        });

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

    /** {@code X is ambient}, {@code make X [not] ambient}: a property effects have or don't. */
    private static void flag(Registrar registrar, String id, String word, Predicate<Effect> test,
            java.util.function.BiConsumer<Effect, Boolean> set) {
        registrar.condition("CondIsPotion" + id, info -> {
            boolean negated = info.pattern() == 1;
            boolean and = info.and(0);
            return (context, args) -> {
                Object[] effects = (Object[]) args[0];
                return effects.length == 0 ? negated
                        : Condition.check(effects, and, value -> value instanceof Effect effect && test.test(effect) != negated);
            };
        }, "%skriptpotioneffects% (is|are) " + word, "%skriptpotioneffects% (isn't|is not|aren't|are not) " + word);
        registrar.effect("EffPotion" + id, info -> {
            changeable(info, 0);
            boolean on = !info.hasTag("not");
            return (context, args) -> {
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof Effect effect) {
                        set.accept(effect, on);
                        effect.changed();
                    }
                }
            };
        }, "make %skriptpotioneffects% [not:not] " + word);
    }

    /** {@code X has particles}, {@code hide the particles [for X]}: something effects show or hide. */
    private static void shown(Registrar registrar, String id, String word, Predicate<Effect> test,
            java.util.function.BiConsumer<Effect, Boolean> set) {
        registrar.condition("CondPotionHas" + id, info -> {
            boolean negated = info.pattern() == 1;
            boolean and = info.and(0);
            return (context, args) -> {
                Object[] effects = (Object[]) args[0];
                return effects.length == 0 ? negated
                        : Condition.check(effects, and, value -> value instanceof Effect effect && test.test(effect) != negated);
            };
        }, "%skriptpotioneffects% (has|have) [an|the] " + word,
                "%skriptpotioneffects% (doesn't|does not|do not|don't) have [an|the] " + word);
        // In a potion effect's section, 'hide the particles' is about that effect.
        registrar.effect("EffPotion" + id, info -> {
            changeable(info, 0);
            boolean show = info.hasTag("show");
            return (context, args) -> {
                for (Object value : args[0] instanceof Object[] values ? values : new Object[] {args[0]}) {
                    if (value instanceof Effect effect) {
                        set.accept(effect, show);
                        effect.changed();
                    }
                }
            };
        }, "(show:show|hide) [the] [potion|effect['s]|potion's] " + word + " [(for|of) %skriptpotioneffects%]",
                "(show:show|hide) %skriptpotioneffects%'[s] " + word);
    }

    /** Fails parsing if argument {@code index} is hidden effects, which can't be changed. */
    private static void changeable(ElementInfo info, int index) {
        if ("hiddenpotioneffect".equals(info.argumentType(index))) {
            throw new SyntaxError(HIDDEN_ERROR);
        }
    }

    /** Fails parsing if argument {@code index} certainly isn't living entities: only they have hidden effects. */
    private static void livingOnly(ElementInfo info, int index) {
        String type = info.argumentType(index);
        if (type != null && !type.equals("livingentity") && !type.equals("entity") && !type.equals("player")) {
            throw new SyntaxError("Only living entities have hidden effects");
        }
    }

    private static Effect effect(Object value) {
        return value instanceof Effect effect ? effect : value instanceof PotionEffectType type ? new Effect(type) : null;
    }

    /** The entity's effects of {@code type}: the active one, then the hidden ones, strongest first. */
    private static List<PotionEffect> chain(LivingEntity entity, PotionEffectType type) {
        List<PotionEffect> chain = new ArrayList<>();
        for (PotionEffect effect = entity.getPotionEffect(type); effect != null; effect = effect.getHiddenPotionEffect()) {
            chain.add(effect);
        }
        return chain;
    }

    private static PotionEffect alone(PotionEffect effect) {
        return new PotionEffect(effect.getType(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(),
                effect.hasParticles(), effect.hasIcon());
    }

    /**
     * Gives the entity {@code kept} of its effects of a type again, weakest first, so Minecraft hides them under
     * each other as before.
     */
    private static void rebuild(LivingEntity entity, PotionEffectType type, List<PotionEffect> kept) {
        entity.removePotionEffect(type);
        for (int i = kept.size() - 1; i >= 0; i--) {
            entity.addPotionEffect(alone(kept.get(i)));
        }
    }

    /** Replaces the entity's active effect of the effect's type with it, keeping the hidden ones under it. */
    private static void replaceActive(LivingEntity entity, Effect effect) {
        List<PotionEffect> chain = chain(entity, effect.type);
        List<PotionEffect> kept = new ArrayList<>();
        // An infinite effect never wears off, so nothing under it would ever show: the hidden ones go.
        kept.add(effect.bukkit());
        if (!effect.infinite) {
            kept.addAll(chain.subList(Math.min(1, chain.size()), chain.size()));
        }
        // The new active one is added last, as Minecraft would have it.
        entity.removePotionEffect(effect.type);
        for (int i = kept.size() - 1; i >= 1; i--) {
            entity.addPotionEffect(alone(kept.get(i)));
        }
        entity.addPotionEffect(kept.getFirst());
    }

    /**
     * {@code the [active|hidden|active and hidden] potion effects of X}, or those of some types. Plain, they're the
     * active ones, but clearing or removing reaches the hidden ones too.
     *
     * @param which which ones, or {@code null} for plain
     * @param types the argument with the types, or {@code null} for all
     * @param holder the argument with the entities and items
     */
    private record Effects(Which which, Integer types, int holder) implements Expression, Changeable {
        private Object[] holders(Object[] args) {
            return (Object[]) args[holder];
        }

        private boolean wanted(Object[] args, PotionEffectType type) {
            if (types == null) {
                return true;
            }
            for (Object value : (Object[]) args[types]) {
                if (type.equals(value)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Object get(TriggerContext context, Object[] args) {
            List<Object> found = new ArrayList<>();
            Which reading = which == null ? Which.ACTIVE : which;
            for (Object holder : holders(args)) {
                if (holder instanceof LivingEntity living) {
                    for (PotionEffect active : living.getActivePotionEffects()) {
                        if (!wanted(args, active.getType())) {
                            continue;
                        }
                        List<PotionEffect> chain = chain(living, active.getType());
                        if (reading != Which.HIDDEN && !chain.isEmpty()) {
                            found.add(Effect.of(chain.getFirst(), living));
                        }
                        if (reading != Which.ACTIVE) {
                            for (PotionEffect hidden : chain.subList(Math.min(1, chain.size()), chain.size())) {
                                found.add(Effect.of(hidden, null));
                            }
                        }
                    }
                } else if (reading != Which.HIDDEN && meta(holder) instanceof PotionMeta potion) {
                    for (PotionEffect effect : potion.getCustomEffects()) {
                        if (wanted(args, effect.getType())) {
                            found.add(Effect.of(effect, null));
                        }
                    }
                }
            }
            return types != null && found.size() == 1 ? found.getFirst() : found.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            if (which == Which.HIDDEN) {
                return mode == Mode.DELETE || mode == Mode.RESET ? new Class<?>[0] : null;
            }
            return switch (mode) {
                case SET, ADD, REMOVE, REMOVE_ALL -> types != null && mode != Mode.SET
                        ? new Class<?>[] {Timespan.class, Effect.class, PotionEffectType.class}
                        : new Class<?>[] {Effect.class, PotionEffectType.class};
                case DELETE, RESET -> new Class<?>[0];
            };
        }

        @Override
        public void change(TriggerContext context, Object[] args, Object[] delta, Mode mode) {
            Object[] given = delta == null ? new Object[0] : delta;
            for (Object holder : holders(args)) {
                if (holder instanceof LivingEntity living) {
                    changeEntity(living, args, given, mode);
                } else {
                    changeItem(holder, args, given, mode);
                }
            }
        }

        private void changeEntity(LivingEntity living, Object[] args, Object[] given, Mode mode) {
            // A length added to or taken from an effect: 'add 15 seconds to {_e}'s speed effect'.
            if ((mode == Mode.ADD || mode == Mode.REMOVE) && given.length > 0 && given[0] instanceof Timespan time) {
                long by = mode == Mode.ADD ? time.ticks() : -time.ticks();
                for (PotionEffect active : List.copyOf(living.getActivePotionEffects())) {
                    if (wanted(args, active.getType()) && !active.isInfinite()) {
                        Effect changed = Effect.of(active, living);
                        changed.ticks = (int) Math.max(0, Math.min(Integer.MAX_VALUE, changed.ticks + by));
                        changed.changed();
                    }
                }
                return;
            }
            switch (mode) {
                case DELETE, RESET, SET -> {
                    for (PotionEffect active : List.copyOf(living.getActivePotionEffects())) {
                        if (!wanted(args, active.getType())) {
                            continue;
                        }
                        List<PotionEffect> chain = chain(living, active.getType());
                        List<PotionEffect> kept = switch (which == null ? Which.BOTH : which) {
                            case ACTIVE -> chain.subList(1, chain.size());
                            case HIDDEN -> chain.subList(0, 1);
                            case BOTH -> List.of();
                        };
                        rebuild(living, active.getType(), new ArrayList<>(kept));
                    }
                    if (mode == Mode.SET) {
                        for (Object value : given) {
                            Effect effect = effect(value);
                            if (effect != null) {
                                living.addPotionEffect(effect.bukkit());
                            }
                        }
                    }
                }
                case ADD -> {
                    for (Object value : given) {
                        Effect effect = effect(value);
                        if (effect != null) {
                            living.addPotionEffect(effect.bukkit());
                        }
                    }
                }
                default -> {
                    // Removing takes away the effects matching what's given, active or hidden as asked.
                    for (Object value : given) {
                        Effect pattern = effect(value);
                        if (pattern == null || !wanted(args, pattern.type)) {
                            continue;
                        }
                        List<PotionEffect> chain = chain(living, pattern.type);
                        List<PotionEffect> kept = new ArrayList<>();
                        for (int i = 0; i < chain.size(); i++) {
                            boolean reachable = which == null || which == Which.BOTH || (which == Which.ACTIVE) == (i == 0);
                            if (!reachable || !pattern.matches(Effect.of(chain.get(i), null))) {
                                kept.add(chain.get(i));
                            }
                        }
                        if (kept.size() != chain.size()) {
                            rebuild(living, pattern.type, kept);
                        }
                    }
                }
            }
        }

        private void changeItem(Object holder, Object[] args, Object[] given, Mode mode) {
            Items.editMeta(holder, meta -> {
                if (!(meta instanceof PotionMeta potion)) {
                    return;
                }
                if (mode == Mode.SET || mode == Mode.DELETE || mode == Mode.RESET) {
                    for (PotionEffect effect : List.copyOf(potion.getCustomEffects())) {
                        if (wanted(args, effect.getType())) {
                            potion.removeCustomEffect(effect.getType());
                        }
                    }
                }
                for (Object value : given) {
                    Effect effect = effect(value);
                    if (effect == null) {
                        continue;
                    }
                    if (mode == Mode.SET || mode == Mode.ADD) {
                        potion.addCustomEffect(effect.bukkit(), true);
                    } else if (mode == Mode.REMOVE || mode == Mode.REMOVE_ALL) {
                        for (PotionEffect existing : List.copyOf(potion.getCustomEffects())) {
                            if (effect.matches(Effect.of(existing, null))) {
                                potion.removeCustomEffect(existing.getType());
                            }
                        }
                    }
                }
            });
        }
    }

    /** The item meta of an item or slot, or {@code null}. */
    private static org.bukkit.inventory.meta.ItemMeta meta(Object holder) {
        return switch (holder) {
            case ItemType item -> item.stack().getItemMeta();
            case Items.Slot slot when slot.item() != null -> slot.item().getItemMeta();
            default -> null;
        };
    }

    /** {@code the amplifier of X} (from 1) or {@code the duration of X}, which change entities' effects too. */
    private record Property(boolean duration) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] args) {
            List<Object> values = new ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof Effect effect) {
                    values.add(duration ? (effect.infinite ? Timespan.FOREVER : new Timespan(effect.ticks * 50L))
                            : (Object) (long) (effect.amplifier + 1));
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {duration ? Timespan.class : Number.class};
                case DELETE, RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] args, Object[] delta, Mode mode) {
            Object given = delta == null || delta.length == 0 ? null : delta[0];
            for (Object value : (Object[]) args[0]) {
                if (!(value instanceof Effect effect)) {
                    continue;
                }
                if (duration) {
                    long by = given instanceof Timespan time ? time.ticks() : 0;
                    long ticks = switch (mode) {
                        case ADD -> effect.ticks + by;
                        case REMOVE -> effect.ticks - by;
                        case DELETE, RESET -> DEFAULT_TICKS;
                        default -> by;
                    };
                    effect.ticks = (int) Math.max(0, Math.min(Integer.MAX_VALUE, ticks));
                    effect.infinite = false;
                    effect.durationGiven = true;
                } else {
                    long by = given instanceof Number number ? Math.round(number.doubleValue()) : 0;
                    long tier = switch (mode) {
                        case ADD -> effect.amplifier + 1 + by;
                        case REMOVE -> effect.amplifier + 1 - by;
                        case DELETE, RESET -> 1;
                        default -> by;
                    };
                    effect.amplifier = (int) Math.max(0, Math.min(255, tier - 1));
                    effect.amplifierGiven = true;
                }
                effect.changed();
            }
        }
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
