package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;

import java.util.List;

/**
 * A parsed, executable event handler: the linker's output for one {@code on <event>:} block. Holds
 * the Bukkit event class it listens for and the tree of statements to run when it fires.
 *
 * <p>The event class is kept as a raw {@link Class} so core stays free of hard Bukkit imports; the
 * platform layer casts it when registering the listener.
 */
public final class Trigger {

    private final String eventName;
    private final Class<?> eventClass;
    private final List<Statement> statements;

    public Trigger(String eventName, Class<?> eventClass, List<Statement> statements) {
        this.eventName = eventName;
        this.eventClass = eventClass;
        this.statements = List.copyOf(statements);
    }

    /** @return the event name as written in the script (e.g. {@code "join"}) */
    public String eventName() {
        return eventName;
    }

    /** @return the Bukkit event class this trigger listens for */
    public Class<?> eventClass() {
        return eventClass;
    }

    /** @return the top-level statements, in order */
    public List<Statement> statements() {
        return statements;
    }

    /**
     * Runs this trigger's statements against the given context, honouring {@code stop}.
     *
     * @param ctx the execution context
     */
    public void execute(TriggerContext ctx) {
        try {
            IfSection.runAll(statements, ctx);
        } catch (StopSignal ignored) {
            // `stop` aborts the remainder of the trigger.
        }
    }
}
