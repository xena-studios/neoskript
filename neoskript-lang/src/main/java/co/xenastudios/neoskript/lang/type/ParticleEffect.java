package co.xenastudios.neoskript.lang.type;

import org.bukkit.Particle;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Set;

/**
 * A particle configuration value — a {@link Particle} together with the fields Bukkit's
 * {@code spawnParticle} takes: {@code count}, an {@code offset} vector, and an {@code extra} ("speed")
 * value, plus optional particle-specific {@code data}.
 *
 * <p>Modelled after Skript's {@code ParticleEffect} wrapper. The {@code offset} and {@code
 * distribution} are the <em>same</em> field; their meaning switches on {@code count}:
 * <ul>
 *   <li>{@code count != 0} ("normal distribution") — the offset is the Gaussian spread radius of the
 *       cloud (Bukkit's {@code offsetX/Y/Z} spawn semantics).</li>
 *   <li>{@code count == 0} — the offset is reinterpreted by Bukkit as a velocity/direction, and {@code
 *       extra} becomes the speed multiplier. This is how directional particles travel.</li>
 * </ul>
 *
 * <p>Bukkit defaults are mirrored: {@code count = 1}, {@code offset = (0, 0, 0)}, {@code extra = 0}.
 * Category membership (directional / converging / scalable) is resolved by constant name so it stays
 * correct across the Bukkit versions that rename or add particles.
 */
public final class ParticleEffect implements Cloneable {

    /** Particles whose offset is interpreted as a travel velocity when {@code count == 0}. */
    private static final Set<String> DIRECTIONAL = Set.of(
            "BLOCK", "BUBBLE", "BUBBLE_COLUMN_UP", "BUBBLE_POP", "CAMPFIRE_COSY_SMOKE",
            "CAMPFIRE_SIGNAL_SMOKE", "CLOUD", "CRIT", "DAMAGE_INDICATOR", "DRAGON_BREATH", "DUST",
            "DUST_COLOR_TRANSITION", "DUST_PLUME", "ELECTRIC_SPARK", "ENCHANTED_HIT", "END_ROD",
            "FIREWORK", "FISHING", "FLAME", "FLASH", "GLOW_SQUID_INK", "ITEM", "LARGE_SMOKE", "POOF",
            "REVERSE_PORTAL", "SCRAPE", "SCULK_CHARGE", "SCULK_CHARGE_POP", "SCULK_SOUL", "SMALL_FLAME",
            "SMOKE", "SNEEZE", "SNOWFLAKE", "SOUL", "SOUL_FIRE_FLAME", "SPIT", "SQUID_INK",
            "TOTEM_OF_UNDYING", "TRIAL_SPAWNER_DETECTION", "TRIAL_SPAWNER_DETECTION_OMINOUS", "WAX_OFF",
            "WAX_ON", "WHITE_SMOKE");

    /** Particles that converge towards the spawn point. */
    private static final Set<String> CONVERGING = Set.of(
            "ENCHANT", "NAUTILUS", "OMINOUS_SPAWNING", "PORTAL", "VAULT_CONNECTION");

    /** Particles whose offset encodes a scale (sweep-attack / explosion). */
    private static final Set<String> SCALABLE = Set.of("SWEEP_ATTACK", "EXPLOSION");

    private final Particle particle;
    private int count = 1;
    private Vector offset = new Vector(0, 0, 0);
    private double extra = 0;
    private Object data;

    public ParticleEffect(Particle particle) {
        this.particle = particle;
    }

    public Particle particle() {
        return particle;
    }

    public int count() {
        return count;
    }

    /** Sets the count. Callers that mirror Skript clamp before calling; the field itself is raw. */
    public ParticleEffect count(int count) {
        this.count = count;
        return this;
    }

    /** @return a defensive copy of the offset vector */
    public Vector offset() {
        return offset.clone();
    }

    public ParticleEffect offset(Vector offset) {
        this.offset = offset == null ? new Vector(0, 0, 0) : offset.clone();
        return this;
    }

    public double extra() {
        return extra;
    }

    public ParticleEffect extra(double extra) {
        this.extra = extra;
        return this;
    }

    public Object data() {
        return data;
    }

    public ParticleEffect data(Object data) {
        this.data = data;
        return this;
    }

    /** @return {@code true} while the offset acts as a Gaussian spread rather than a velocity/scale */
    public boolean isUsingNormalDistribution() {
        return count != 0;
    }

    /** @return the distribution (== offset) while using normal distribution, else {@code null} */
    public Vector distribution() {
        return isUsingNormalDistribution() ? offset() : null;
    }

    /** Sets the distribution, forcing {@code count} back to 1 if it had been zeroed for a velocity. */
    public ParticleEffect distribution(Vector distribution) {
        if (!isUsingNormalDistribution()) {
            count = 1;
        }
        return offset(distribution);
    }

    /** @return {@code true} if this particle's offset is interpreted as a travel velocity */
    public boolean isDirectional() {
        return DIRECTIONAL.contains(particle.name());
    }

    public boolean isConverging() {
        return CONVERGING.contains(particle.name());
    }

    public boolean isScalable() {
        return SCALABLE.contains(particle.name());
    }

    /** @return the velocity (== offset); only meaningful when {@code count == 0} */
    public Vector velocity() {
        return offset();
    }

    /** Sets a travel velocity: zeroes {@code count} so Bukkit reads the offset as a direction. */
    public ParticleEffect velocity(Vector velocity) {
        this.count = 0;
        return offset(velocity);
    }

    /** @return an independent copy carrying the same particle, count, offset, extra, and data */
    public ParticleEffect copy() {
        ParticleEffect copy = new ParticleEffect(particle);
        copy.count = count;
        copy.offset = offset.clone();
        copy.extra = extra;
        copy.data = data;
        return copy;
    }

    @Override
    public ParticleEffect clone() {
        return copy();
    }

    /** @return the display name of a particle constant: {@code SMALL_FLAME} → {@code "small flame"} */
    public static String displayName(Particle particle) {
        return particle.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    @Override
    public String toString() {
        return displayName(particle);
    }
}
