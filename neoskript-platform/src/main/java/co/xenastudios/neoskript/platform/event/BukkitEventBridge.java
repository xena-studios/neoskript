package co.xenastudios.neoskript.platform.event;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * Bridges a Bukkit event class to a NeoSkript handler by dynamically registering a single listener
 * for that event type. One bridge per event class dispatches to all triggers for that event.
 */
public final class BukkitEventBridge {

    private BukkitEventBridge() {
    }

    /**
     * Registers a listener for {@code eventClass} that invokes {@code handler} on each firing.
     *
     * @param plugin     the owning plugin
     * @param eventClass the Bukkit event class to listen for
     * @param handler    invoked with each fired event instance
     * @return the registered listener, so callers can unregister it on reload
     */
    public static Listener register(Plugin plugin, Class<? extends Event> eventClass, Consumer<Event> handler) {
        Listener listener = new Listener() {
        };
        plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                listener,
                EventPriority.NORMAL,
                (registered, event) -> {
                    if (eventClass.isInstance(event)) {
                        handler.accept(event);
                    }
                },
                plugin);
        return listener;
    }
}
