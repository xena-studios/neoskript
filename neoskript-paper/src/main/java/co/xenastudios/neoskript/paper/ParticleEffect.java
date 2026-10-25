/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import com.destroystokyo.paper.ParticleBuilder;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.util.Vector;

/**
 * Skript's particle effect: a particle with its count, offset, speed and data, ready to draw. As in Skript,
 * what the offset means depends on the count: with a count above 0 it's the spread of a normal distribution
 * around the point drawn at; with a count of 0 some particles take it as their velocity
 * ({@link Directional}) or their size ({@link Scalable}).
 */
class ParticleEffect extends ParticleBuilder {

    /** Particles whose offset is a velocity at a count of 0, from Paper's documentation, as Skript lists them. */
    private static final Set<Particle> DIRECTIONAL = particles("BLOCK", "BUBBLE", "BUBBLE_COLUMN_UP", "BUBBLE_POP",
            "CAMPFIRE_COSY_SMOKE", "CAMPFIRE_SIGNAL_SMOKE", "CLOUD", "CRIT", "DAMAGE_INDICATOR", "DRAGON_BREATH", "DUST",
            "DUST_COLOR_TRANSITION", "DUST_PLUME", "ELECTRIC_SPARK", "ENCHANTED_HIT", "END_ROD", "FIREWORK", "FISHING",
            "FLAME", "FLASH", "GLOW_SQUID_INK", "ITEM", "LARGE_SMOKE", "POOF", "REVERSE_PORTAL", "SCRAPE", "SCULK_CHARGE",
            "SCULK_CHARGE_POP", "SCULK_SOUL", "SMALL_FLAME", "SMOKE", "SNEEZE", "SNOWFLAKE", "SOUL", "SOUL_FIRE_FLAME",
            "SPIT", "SQUID_INK", "TOTEM_OF_UNDYING", "TRIAL_SPAWNER_DETECTION", "TRIAL_SPAWNER_DETECTION_OMINOUS",
            "WAX_OFF", "WAX_ON", "WHITE_SMOKE");
    /** Particles that start away from the point and move towards it. */
    private static final Set<Particle> CONVERGING = particles("ENCHANT", "NAUTILUS", "OMINOUS_SPAWNING", "PORTAL",
            "VAULT_CONNECTION");
    /** Directional particles with an upward motion of their own. */
    private static final Set<Particle> RISING = particles("EFFECT", "ENTITY_EFFECT", "GLOW", "INFESTED", "INSTANT_EFFECT",
            "RAID_OMEN", "TRIAL_OMEN", "WITCH");
    /** Particles whose offset is their size at a count of 0. */
    private static final Set<Particle> SCALABLE = particles("SWEEP_ATTACK", "EXPLOSION");

    /** The most particles Skript draws at once: more is likely a mistake, and can crash players' games. */
    static final int MAX_COUNT = 16_384;

    /**
     * Whether it's a literal's, or one of {@code all particle effects}, which are shared: changing it would change
     * them all, so the particle expressions don't. Copies, as in variables, aren't.
     */
    boolean shared;

    protected ParticleEffect(Particle particle) {
        super(particle);
    }

    /** The effect for {@code particle}, of the kind its particle is, as Skript makes them. */
    static ParticleEffect of(Particle particle) {
        if (CONVERGING.contains(particle)) {
            return new Converging(particle);
        } else if (DIRECTIONAL.contains(particle) || RISING.contains(particle)) {
            return new Directional(particle);
        } else if (SCALABLE.contains(particle)) {
            return new Scalable(particle);
        }
        return new ParticleEffect(particle);
    }

    static Set<Particle> directional() {
        Set<Particle> all = EnumSet.noneOf(Particle.class);
        all.addAll(DIRECTIONAL);
        all.addAll(RISING);
        return all;
    }

    static Set<Particle> converging() {
        return CONVERGING;
    }

    static Set<Particle> scalable() {
        return SCALABLE;
    }

    private static Set<Particle> particles(String... names) {
        Set<Particle> particles = EnumSet.noneOf(Particle.class);
        for (String name : names) {
            try {
                particles.add(Particle.valueOf(name));
            } catch (IllegalArgumentException missing) {
                // Not on this version.
            }
        }
        return particles;
    }

    /** Draws it at {@code location}, unless its particle needs data it doesn't have, or there's no world. */
    void spawn(Location location) {
        Class<?> type = particle().getDataType();
        if (type != Void.class && !type.isInstance(data()) || location.getWorld() == null) {
            return;
        }
        location(location);
        spawn();
    }

    Vector offset() {
        return new Vector(offsetX(), offsetY(), offsetZ());
    }

    void offset(Vector offset) {
        offset(offset.getX(), offset.getY(), offset.getZ());
    }

    /** Whether the offset is a normal distribution: the count isn't 0. */
    boolean isUsingNormalDistribution() {
        return count() != 0;
    }

    /** Sets the offset as a distribution, making the count 1 if it was 0. */
    void distribution(Vector distribution) {
        if (!isUsingNormalDistribution()) {
            count(1);
        }
        offset(distribution);
    }

    @Override
    public <T> ParticleBuilder data(T data) {
        if (data != null && !particle().getDataType().isInstance(data)) {
            return this; // data of the wrong kind is ignored, as in Skript
        }
        return super.data(data);
    }

    ParticleEffect copy() {
        ParticleEffect copy = (ParticleEffect) clone();
        copy.shared = false;
        return copy;
    }

    /** A particle that moves towards the point it's drawn at. */
    static final class Converging extends ParticleEffect {
        Converging(Particle particle) {
            super(particle);
        }
    }

    /** A particle that, at a count of 0, takes its offset as its velocity. */
    static final class Directional extends ParticleEffect {

        Directional(Particle particle) {
            super(particle);
        }

        boolean hasVelocity() {
            return count() == 0;
        }

        /** Sets the velocity, making the count 0 so it's used. */
        void velocity(Vector velocity) {
            count(0);
            offset(velocity);
        }
    }

    /** A particle that, at a count of 0, takes its offset as its size: sweep attacks and explosions. */
    static final class Scalable extends ParticleEffect {

        private double scale = 1;

        Scalable(Particle particle) {
            super(particle);
        }

        boolean hasScale() {
            return count() == 0;
        }

        double scale() {
            return scale;
        }

        /** Sets the size, making the count 0 so it's used. The client reads the size from the x offset. */
        void scale(double scale) {
            this.scale = scale;
            count(0);
            double x = particle() == Particle.SWEEP_ATTACK ? 2 - 2 * scale : 2 - scale;
            offset(x, 0, 0);
        }
    }
}
