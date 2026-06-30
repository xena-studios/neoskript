package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import org.bukkit.event.Event;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A basic single-threaded {@link TriggerContext}. Local variables live in a per-execution map;
 * global variables are read from and written to a shared map owned by the runtime.
 *
 * <p>Phase 3 will replace local-variable storage with parse-time-resolved indexed slots and globals
 * with the persistence-backed variable store; this implementation establishes the contract for the
 * Phase 1 interpreter.
 */
public final class SimpleTriggerContext implements TriggerContext {

    private final Event event;
    private final Map<String, Object> globals;
    private final Map<String, Object> locals = new HashMap<>();

    /**
     * @param event   the firing event, or {@code null} for non-event triggers
     * @param globals the shared global-variable map (never {@code null})
     */
    public SimpleTriggerContext(Event event, Map<String, Object> globals) {
        this.event = event;
        this.globals = globals;
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

    @Override
    public Object getGlobal(String name) {
        return globals.get(name);
    }

    @Override
    public void setGlobal(String name, Object value) {
        if (value == null) {
            globals.remove(name);
        } else {
            globals.put(name, value);
        }
    }
}
