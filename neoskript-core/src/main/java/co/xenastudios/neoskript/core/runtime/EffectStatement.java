package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Effect;

/**
 * Adapts an {@link Effect} (a leaf action) into a {@link Statement}.
 */
public final class EffectStatement implements Statement {

    private final Effect effect;

    public EffectStatement(Effect effect) {
        this.effect = effect;
    }

    @Override
    public void run(TriggerContext ctx) {
        effect.execute(ctx);
    }
}
