package co.xenastudios.neoskript.lang.event;

import co.xenastudios.neoskript.core.runtime.EventRegistry;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Registers NeoSkript's built-in event names. Phase 1 ships join and quit; Phase 2 expands this to
 * the bulk of Skript's event catalogue.
 */
public final class BuiltinEvents {

    private BuiltinEvents() {
    }

    /**
     * Populates the registry with built-in event aliases.
     *
     * @param events the registry to populate
     */
    public static void registerAll(EventRegistry events) {
        events.register(PlayerJoinEvent.class, "join", "player join", "player joins");
        events.register(PlayerQuitEvent.class, "quit", "disconnect", "player quit", "player quits");
    }
}
