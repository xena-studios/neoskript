package co.xenastudios.neoskript.core.runtime;

import co.xenastudios.neoskript.api.runtime.TriggerContext;

import java.util.List;

/**
 * A parsed custom command ({@code command /name:}) — its name, metadata, and trigger body. The
 * platform layer registers it with the server; this class stays Bukkit-free and just runs the body.
 */
public final class CommandDefinition {

    private final String name;
    private final String permission;
    private final String description;
    private final String usage;
    private final List<String> aliases;
    private final List<Statement> body;

    public CommandDefinition(String name, String permission, String description, String usage,
                             List<String> aliases, List<Statement> body) {
        this.name = name;
        this.permission = permission;
        this.description = description;
        this.usage = usage;
        this.aliases = List.copyOf(aliases);
        this.body = List.copyOf(body);
    }

    /** @return the command name (without the leading slash) */
    public String name() {
        return name;
    }

    /** @return the required permission, or {@code null} if none */
    public String permission() {
        return permission;
    }

    /** @return the description, or {@code null} */
    public String description() {
        return description;
    }

    /** @return the usage message, or {@code null} */
    public String usage() {
        return usage;
    }

    /** @return the command aliases */
    public List<String> aliases() {
        return aliases;
    }

    /** @return the trigger statements */
    public List<Statement> body() {
        return body;
    }

    /**
     * Runs the command's trigger, honouring {@code stop}.
     *
     * @param ctx the execution context (with the sender and arguments set as locals)
     */
    public void run(TriggerContext ctx) {
        try {
            IfSection.runAll(body, ctx);
        } catch (StopSignal ignored) {
            // `stop` aborts the rest of the command.
        }
    }

    /**
     * Runs the command's trigger on the iterative interpreter so that {@code wait} delays (including
     * inside {@code if}/{@code while}/{@code loop}) suspend and resume via {@code scheduler}.
     *
     * @param ctx       the execution context (with the sender and arguments set as locals)
     * @param scheduler schedules delayed continuations
     */
    public void run(TriggerContext ctx, DelayScheduler scheduler) {
        new Interpreter(ctx, scheduler).run(body);
    }
}
