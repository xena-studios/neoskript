package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;

import java.util.List;

/**
 * A parsed, executable handler — the linker's output for one top-level block. A trigger is one of:
 * <ul>
 *   <li>{@link Kind#EVENT} — fired by a Bukkit event;</li>
 *   <li>{@link Kind#PERIODIC} — run every {@code intervalTicks} on the scheduler;</li>
 *   <li>{@link Kind#LOAD} — run once when scripts load.</li>
 * </ul>
 *
 * <p>The event class is kept as a raw {@link Class} so core stays free of hard Bukkit imports; the
 * platform layer casts it when registering the listener.
 */
public final class Trigger {

    /** The kind of trigger. */
    public enum Kind {
        EVENT, PERIODIC, LOAD
    }

    private final Kind kind;
    private final String eventName;
    private final Class<?> eventClass;
    private final long intervalTicks;
    private final List<Statement> statements;

    private Trigger(Kind kind, String eventName, Class<?> eventClass, long intervalTicks, List<Statement> statements) {
        this.kind = kind;
        this.eventName = eventName;
        this.eventClass = eventClass;
        this.intervalTicks = intervalTicks;
        this.statements = List.copyOf(statements);
    }

    /** Creates an event trigger. */
    public Trigger(String eventName, Class<?> eventClass, List<Statement> statements) {
        this(Kind.EVENT, eventName, eventClass, 0L, statements);
    }

    /** Creates a periodic trigger running every {@code intervalTicks}. */
    public static Trigger periodic(long intervalTicks, List<Statement> statements) {
        return new Trigger(Kind.PERIODIC, "every " + intervalTicks + " ticks", null, intervalTicks, statements);
    }

    /** Creates a load trigger, run once when scripts load. */
    public static Trigger onLoad(List<Statement> statements) {
        return new Trigger(Kind.LOAD, "load", null, 0L, statements);
    }

    /** @return the trigger kind */
    public Kind kind() {
        return kind;
    }

    /** @return a descriptive name (the event name, or a synthetic name for periodic/load) */
    public String eventName() {
        return eventName;
    }

    /** @return the Bukkit event class (only for {@link Kind#EVENT}), otherwise {@code null} */
    public Class<?> eventClass() {
        return eventClass;
    }

    /** @return the interval in ticks (only for {@link Kind#PERIODIC}) */
    public long intervalTicks() {
        return intervalTicks;
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

    /**
     * Runs the trigger, honouring top-level {@code wait}s by scheduling the remainder via
     * {@code scheduler} (which may run it on a later tick).
     *
     * @param ctx       the execution context
     * @param scheduler schedules delayed continuations
     */
    public void execute(TriggerContext ctx, DelayScheduler scheduler) {
        // The iterative interpreter supports `wait` anywhere (including inside sections), suspending
        // and resuming via the scheduler.
        new Interpreter(ctx, scheduler).run(statements);
    }
}
