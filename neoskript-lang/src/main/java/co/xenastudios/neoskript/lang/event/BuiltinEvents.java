package co.xenastudios.neoskript.lang.event;

import co.xenastudios.neoskript.core.runtime.EventRegistry;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;

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

        events.register(PlayerInteractEvent.class, "interact", "click", "player interact");
        events.register(InventoryClickEvent.class, "inventory click");
        events.register(PlayerCommandPreprocessEvent.class, "command", "player command");
        events.register(PlayerRespawnEvent.class, "respawn", "player respawn");
        events.register(PlayerTeleportEvent.class, "teleport", "player teleport");
        events.register(PlayerMoveEvent.class, "move", "player move", "walk");
        events.register(PlayerDropItemEvent.class, "drop", "item drop", "drop item");
        events.register(PlayerToggleSneakEvent.class, "sneak toggle", "toggle sneak");
        events.register(PlayerGameModeChangeEvent.class, "gamemode change");
        events.register(EntityDamageEvent.class, "damage", "entity damage");
        events.register(EntityExplodeEvent.class, "explode", "explosion");
        events.register(FoodLevelChangeEvent.class, "food level change", "hunger change");
        events.register(SignChangeEvent.class, "sign change", "sign edit");
        events.register(WeatherChangeEvent.class, "weather change");
        events.register(WorldLoadEvent.class, "world load");
    }
}
