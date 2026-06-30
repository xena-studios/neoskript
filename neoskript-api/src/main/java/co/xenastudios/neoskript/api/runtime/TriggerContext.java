package co.xenastudios.neoskript.api.runtime;

import org.bukkit.event.Event;

import java.util.Map;
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

    /**
     * Reads a global variable ({@code {name}} in script source). Global variables are shared across
     * all triggers and (from Phase 2) persisted between restarts.
     *
     * @param name the global variable name, without delimiters
     * @return the stored value, or {@code null} if unset
     */
    Object getGlobal(String name);

    /**
     * Writes a global variable ({@code {name}} in script source).
     *
     * @param name  the global variable name, without delimiters
     * @param value the value to store; {@code null} clears the variable
     */
    void setGlobal(String name, Object value);

    /**
     * Returns the direct children of a local list variable ({@code {_name::*}}).
     *
     * @param prefix the list prefix, including the trailing {@code ::} (e.g. {@code "scores::"})
     * @return a map of full child name to value (e.g. {@code "scores::1" -> 10}); empty if none
     */
    Map<String, Object> listLocal(String prefix);

    /**
     * Returns the direct children of a global list variable ({@code {name::*}}).
     *
     * @param prefix the list prefix, including the trailing {@code ::}
     * @return a map of full child name to value; empty if none
     */
    Map<String, Object> listGlobal(String prefix);
}
