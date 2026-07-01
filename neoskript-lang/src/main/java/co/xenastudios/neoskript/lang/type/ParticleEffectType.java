package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;
import org.bukkit.Particle;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The {@code particle} type and its category subtypes ({@code directionalparticle},
 * {@code convergingparticle}, {@code scalableparticle}).
 *
 * <p>Parsing accepts a {@link Particle} constant name — case-insensitively, with spaces or hyphens for
 * underscores ({@code "small flame"} → {@code SMALL_FLAME}) — and wraps it in a {@link ParticleEffect}
 * carrying Bukkit's default count/offset/extra. A subtype restricts parsing to particles in its
 * category, so a {@code scalableparticle} literal only matches {@code sweep attack} or {@code
 * explosion}. The {@code Particle} enum is resolved reflectively so this works whether or not Bukkit
 * still declares it an {@code enum} on the running server.
 */
public final class ParticleEffectType implements Type<ParticleEffect> {

    private final String codeName;
    private final Predicate<ParticleEffect> category;
    private Particle[] constants;
    private boolean resolved;

    /** The base {@code particle} type: accepts any particle. */
    public static ParticleEffectType particle() {
        return new ParticleEffectType("particle", null);
    }

    public static ParticleEffectType directional() {
        return new ParticleEffectType("directionalparticle", ParticleEffect::isDirectional);
    }

    public static ParticleEffectType converging() {
        return new ParticleEffectType("convergingparticle", ParticleEffect::isConverging);
    }

    public static ParticleEffectType scalable() {
        return new ParticleEffectType("scalableparticle", ParticleEffect::isScalable);
    }

    private ParticleEffectType(String codeName, Predicate<ParticleEffect> category) {
        this.codeName = codeName;
        this.category = category;
    }

    private Particle[] constants() {
        if (!resolved) {
            try {
                constants = Particle.class.getEnumConstants();
            } catch (Throwable noServer) {
                constants = null;
            }
            resolved = true;
        }
        return constants;
    }

    @Override
    public Class<ParticleEffect> typeClass() {
        return ParticleEffect.class;
    }

    @Override
    public String codeName() {
        return codeName;
    }

    @Override
    public Optional<ParticleEffect> parse(String input) {
        Particle[] values = constants();
        if (values == null) {
            return Optional.empty();
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (Particle constant : values) {
            if (constant.name().equals(normalized)) {
                ParticleEffect effect = new ParticleEffect(constant);
                if (category != null && !category.test(effect)) {
                    return Optional.empty();
                }
                return Optional.of(effect);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toDisplayString(ParticleEffect value) {
        return value.toString();
    }
}
