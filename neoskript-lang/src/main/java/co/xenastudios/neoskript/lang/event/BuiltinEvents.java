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
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;

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

        events.register(EntityDeathEvent.class, "entity death", "mob death");
        events.register(EntityPickupItemEvent.class, "pickup", "item pickup");
        events.register(EntityRegainHealthEvent.class, "regen", "heal", "health regen");
        events.register(EntityTameEvent.class, "tame", "taming");
        events.register(CreatureSpawnEvent.class, "spawn", "mob spawn", "creature spawn");
        events.register(ProjectileHitEvent.class, "projectile hit");
        events.register(ProjectileLaunchEvent.class, "projectile launch", "shoot");
        events.register(PlayerLevelChangeEvent.class, "level change");
        events.register(PlayerExpChangeEvent.class, "exp change", "experience change");
        events.register(PlayerBedEnterEvent.class, "bed enter");
        events.register(PlayerFishEvent.class, "fish", "fishing");
        events.register(PlayerItemConsumeEvent.class, "consume", "eat", "drink");
        events.register(PlayerChangedWorldEvent.class, "world change", "world switch");
        events.register(PlayerToggleSprintEvent.class, "sprint toggle", "toggle sprint");
        events.register(PlayerToggleFlightEvent.class, "flight toggle", "toggle flight");
        events.register(InventoryOpenEvent.class, "inventory open");
        events.register(InventoryCloseEvent.class, "inventory close");
        events.register(CraftItemEvent.class, "craft", "crafting");
        events.register(FurnaceSmeltEvent.class, "smelt", "smelting");
        events.register(VehicleEnterEvent.class, "vehicle enter");
        events.register(VehicleExitEvent.class, "vehicle exit");
        events.register(ChunkLoadEvent.class, "chunk load");
        events.register(WorldSaveEvent.class, "world save");
        events.register(LightningStrikeEvent.class, "lightning", "lightning strike");

        events.register(EnchantItemEvent.class, "enchant", "item enchant");
        events.register(PlayerPortalEvent.class, "portal");
        events.register(PlayerBucketEmptyEvent.class, "bucket empty", "empty bucket");
        events.register(PlayerBucketFillEvent.class, "bucket fill", "fill bucket");
        events.register(BlockRedstoneEvent.class, "redstone", "redstone change");
        events.register(BlockIgniteEvent.class, "block ignite");
        events.register(BlockBurnEvent.class, "block burn");
        events.register(BlockGrowEvent.class, "block grow", "grow");
        events.register(ChunkUnloadEvent.class, "chunk unload");
        events.register(WorldInitEvent.class, "world init");
        events.register(EntityShootBowEvent.class, "shoot bow", "bow shoot");
        events.register(EntityBreedEvent.class, "breed", "breeding");
        events.register(EntityTargetEvent.class, "target", "entity target");
        events.register(ServerLoadEvent.class, "server load", "server start");
        events.register(InventoryDragEvent.class, "inventory drag");
    }
}
