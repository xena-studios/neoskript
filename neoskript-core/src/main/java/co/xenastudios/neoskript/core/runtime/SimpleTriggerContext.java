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
 * <p>List variables ({@code {name::index}}) are stored flat, keyed by their full resolved name; the
 * {@code listLocal}/{@code listGlobal} accessors return the direct children of a prefix. Phase 3 will
 * replace local-variable storage with parse-time-resolved indexed slots and globals with the
 * persistence-backed variable store.
 */
public final class SimpleTriggerContext implements TriggerContext, VariableScope {

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
        put(locals, name, value);
    }

    @Override
    public Object getGlobal(String name) {
        return globals.get(name);
    }

    @Override
    public void setGlobal(String name, Object value) {
        put(globals, name, value);
    }

    @Override
    public Map<String, Object> listLocal(String prefix) {
        return ListVariables.directChildren(locals, prefix);
    }

    @Override
    public Map<String, Object> listGlobal(String prefix) {
        return ListVariables.directChildren(globals, prefix);
    }

    @Override
    public void runAtomic(Runnable action) {
        // Serialize compound global mutations across concurrently-firing (Folia) handlers.
        synchronized (globals) {
            action.run();
        }
    }

    private static void put(Map<String, Object> map, String name, Object value) {
        if (value == null) {
            map.remove(name);
        } else {
            map.put(name, value);
        }
    }
}
