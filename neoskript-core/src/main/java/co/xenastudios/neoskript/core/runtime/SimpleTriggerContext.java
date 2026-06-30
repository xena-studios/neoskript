package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import org.bukkit.event.Event;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A basic single-threaded {@link TriggerContext} backing local variables with a hash map.
 *
 * <p>Phase 3 will replace local-variable storage with parse-time-resolved indexed slots; this
 * implementation establishes the contract and is sufficient for the Phase 1 interpreter.
 */
public final class SimpleTriggerContext implements TriggerContext {

    private final Event event;
    private final Map<String, Object> locals = new HashMap<>();

    /**
     * @param event the firing event, or {@code null} for non-event triggers
     */
    public SimpleTriggerContext(Event event) {
        this.event = event;
    }

    @Override
    public Optional<Event> event() {
        return Optional.ofNullable(event);
    }

    @Override
    public Object getLocal(String name) {
        return locals.get(name);
    }

    @Override
    public void setLocal(String name, Object value) {
        if (value == null) {
            locals.remove(name);
        } else {
            locals.put(name, value);
        }
    }
}
