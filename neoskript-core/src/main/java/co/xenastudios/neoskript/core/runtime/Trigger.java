package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Effect;

import java.util.List;

/**
 * A parsed, executable event handler: the linker's output for one {@code on <event>:} block. Holds
 * the Bukkit event class it listens for and the flat list of effects to run when it fires.
 *
 * <p>The event class is kept as a raw {@link Class} so core stays free of hard Bukkit imports; the
 * platform layer casts it when registering the listener.
 */
public final class Trigger {

    private final String eventName;
    private final Class<?> eventClass;
    private final List<Effect> effects;

    public Trigger(String eventName, Class<?> eventClass, List<Effect> effects) {
        this.eventName = eventName;
        this.eventClass = eventClass;
        this.effects = List.copyOf(effects);
    }

    /** @return the event name as written in the script (e.g. {@code "join"}) */
    public String eventName() {
        return eventName;
    }

    /** @return the Bukkit event class this trigger listens for */
    public Class<?> eventClass() {
        return eventClass;
    }

    /** @return the effects to run, in order */
    public List<Effect> effects() {
        return effects;
    }

    /**
     * Runs this trigger's effects against the given context.
     *
     * @param ctx the execution context
     */
    public void execute(TriggerContext ctx) {
        for (Effect effect : effects) {
            effect.execute(ctx);
        }
    }
}
