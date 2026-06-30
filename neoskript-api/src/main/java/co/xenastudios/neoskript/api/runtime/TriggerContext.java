package co.xenastudios.neoskript.api.runtime;

import org.bukkit.event.Event;

import java.util.Optional;

/**
 * The runtime context passed to every {@link co.xenastudios.neoskript.api.syntax.SyntaxElement}
 * during execution of a trigger.
 *
 * <p>A context carries the Bukkit event that fired the trigger (if any) and the trigger's local
 * variables. Implementations are <strong>not</strong> required to be thread-safe; a context belongs
 * to a single executing trigger on a single thread (or region task, under Folia).
 */
public interface TriggerContext {

    /**
     * The event that triggered execution, if this trigger was fired by an event.
     *
     * @return the firing event, or {@link Optional#empty()} for command/function/periodic triggers
     */
    Optional<Event> event();

    /**
     * Reads a trigger-local variable ({@code {_name}} in script source).
     *
     * @param name the local variable name, without delimiters
     * @return the stored value, or {@code null} if unset
     */
    Object getLocal(String name);

    /**
     * Writes a trigger-local variable ({@code {_name}} in script source).
     *
     * @param name  the local variable name, without delimiters
     * @param value the value to store; {@code null} clears the variable
     */
    void setLocal(String name, Object value);
}
