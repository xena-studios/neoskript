package co.xenastudios.neoskript.core.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds custom commands parsed from scripts, for the platform layer to register with the server.
 * Thread-safe so scripts can be parsed in parallel.
 */
public final class CommandRegistry {

    private final List<CommandDefinition> commands = Collections.synchronizedList(new ArrayList<>());

    /**
     * Registers a parsed command.
     *
     * @param command the command definition
     */
    public void register(CommandDefinition command) {
        commands.add(command);
    }

    /** @return the registered commands, in definition order */
    public List<CommandDefinition> commands() {
        return List.copyOf(commands);
    }

    /** @return the number of registered commands */
    public int size() {
        return commands.size();
    }

    /** Removes all commands (used when reloading scripts). */
    public void clear() {
        commands.clear();
    }
}
