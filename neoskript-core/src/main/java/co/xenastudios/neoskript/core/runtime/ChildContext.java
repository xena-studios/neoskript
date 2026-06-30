package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import org.bukkit.event.Event;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A nested execution scope with its own local variables that delegates global-variable access to a
 * parent context. Used for function bodies, which get a fresh local scope but share globals.
 */
public final class ChildContext implements TriggerContext, VariableScope {

    private final TriggerContext parent;
    private final Map<String, Object> locals = new HashMap<>();

    public ChildContext(TriggerContext parent) {
        this.parent = parent;
    }

    @Override
    public Optional<Event> event() {
        return Optional.empty();
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
        return parent.getGlobal(name);
    }

    @Override
    public void setGlobal(String name, Object value) {
        parent.setGlobal(name, value);
    }

    @Override
    public Map<String, Object> listLocal(String prefix) {
        return ListVariables.directChildren(locals, prefix);
    }

    @Override
    public Map<String, Object> listGlobal(String prefix) {
        return parent.listGlobal(prefix);
    }

    @Override
    public void runAtomic(Runnable action) {
        // Delegate to the owning scope so global mutations share one lock.
        if (parent instanceof VariableScope scope) {
            scope.runAtomic(action);
        } else {
            action.run();
        }
    }
}
