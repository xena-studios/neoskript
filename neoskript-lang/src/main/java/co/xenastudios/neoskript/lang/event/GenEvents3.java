package co.xenastudios.neoskript.lang.event;

import co.xenastudios.neoskript.core.runtime.EventRegistry;

/** Additional standard Bukkit/Paper event triggers (source-grounded). One register per line for compile-drop. */
public final class GenEvents3 {
    private GenEvents3() {}

    public static void register(EventRegistry events) {
        events.register(org.bukkit.event.player.PlayerEditBookEvent.class, "book edit", "book change", "book write");
        events.register(org.bukkit.event.entity.EntityMountEvent.class, "mount", "mounting");
        events.register(org.bukkit.event.entity.EntityDismountEvent.class, "dismount", "dismounting");
        events.register(org.bukkit.event.entity.PlayerLeashEntityEvent.class, "leash", "leashing", "player leash");
        events.register(org.bukkit.event.entity.EntityUnleashEvent.class, "unleash", "unleashing");
        events.register(org.bukkit.event.world.LootGenerateEvent.class, "loot generate", "loot generating");
        events.register(org.bukkit.event.server.ServerListPingEvent.class, "server list ping", "server ping");
        events.register(org.bukkit.event.vehicle.VehicleBlockCollisionEvent.class, "vehicle collision");
        events.register(org.bukkit.event.server.ServerLoadEvent.class, "server start", "server load", "skript start", "skript load");
        events.register(org.bukkit.event.entity.EntityChangeBlockEvent.class, "enderman place", "entity change block");
        events.register(com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent.class, "start spectating", "spectate");

    }
}
