package co.xenastudios.neoskript.lang.event;

import co.xenastudios.neoskript.core.runtime.EventRegistry;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Registers NeoSkript's built-in event names. Phase 2 covers the common player/block events; the
 * full catalogue continues to grow against the conformance corpus.
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
        events.register(AsyncChatEvent.class, "chat", "player chat", "player chats");
        events.register(PlayerDeathEvent.class, "death", "player death", "player dies");
        events.register(BlockBreakEvent.class, "break", "block break", "mine", "mining");
        events.register(BlockPlaceEvent.class, "place", "block place", "block placing");
    }
}
