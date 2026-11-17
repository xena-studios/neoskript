/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.Trigger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/**
 * Runs triggers when server events fire. Each event class the loaded scripts use gets one Bukkit
 * listener, rebuilt whenever scripts load. As in Skript, triggers run at {@code high} priority and skip
 * cancelled events unless the config says otherwise.
 *
 * <p>Triggers run on the thread the event fires on: the main thread on Paper, the owning region's thread
 * on Folia, or an async thread for async events like chat.
 */
class EventDispatcher implements Listener {

    private final Plugin plugin;
    private final Logger logger;
    private final EventPriority priority;
    private final boolean listenToCancelled;

    EventDispatcher(Plugin plugin, Logger logger, EventPriority priority, boolean listenToCancelled) {
        this.plugin = plugin;
        this.logger = logger;
        this.priority = priority;
        this.listenToCancelled = listenToCancelled;
    }

    /** The triggers' names, for {@link #fireLogged}, by the array {@link #update} registers them in. */
    private final Map<Trigger[], String[]> triggerNames = new java.util.IdentityHashMap<>();

    /** Listens for the events of {@code triggers}, and only those, replacing what it listened for before. */
    void update(Collection<CompiledScripts.CompiledTrigger> triggers) {
        Map<Class<? extends Event>, List<Trigger>> byClass = new LinkedHashMap<>();
        Map<Class<? extends Event>, java.util.Set<String>> scripts = new LinkedHashMap<>();
        // For config.yml's 'verbosity: very high', which logs each trigger an event runs.
        Map<Trigger, String> names = new java.util.IdentityHashMap<>();
        for (CompiledScripts.CompiledTrigger trigger : triggers) {
            if (trigger.eventClasses() == null) {
                continue;
            }
            names.put(trigger.trigger(), trigger.script() + ", line " + trigger.line());
            for (Class<?> eventClass : trigger.eventClasses()) {
                Class<? extends Event> type = eventClass.asSubclass(Event.class);
                byClass.computeIfAbsent(type, k -> new ArrayList<>()).add(trigger.trigger());
                scripts.computeIfAbsent(type, k -> new java.util.TreeSet<>()).add(trigger.script());
            }
        }
        HandlerList.unregisterAll(this);
        triggerNames.clear();
        for (Map.Entry<Class<? extends Event>, List<Trigger>> entry : byClass.entrySet()) {
            Class<? extends Event> eventClass = entry.getKey();
            Trigger[] listeners = entry.getValue().toArray(Trigger[]::new);
            triggerNames.put(listeners, entry.getValue().stream().map(names::get).toArray(String[]::new));
            try {
                register(eventClass, listeners);
            } catch (RuntimeException | LinkageError e) {
                // One event the server won't listen to doesn't stop the others being listened to.
                logger.error("Couldn't listen for {}, so its triggers in {} won't run: {}", eventClass.getName(),
                        String.join(", ", scripts.get(eventClass)), e.toString(), e);
            }
        }
    }

    /** Listens for {@code eventClass}, running {@code listeners}. */
    void register(Class<? extends Event> eventClass, Trigger[] listeners) {
        String[] names = triggerNames.getOrDefault(listeners, new String[listeners.length]);
        plugin.getServer().getPluginManager().registerEvent(eventClass, this, priority, (listener, event) -> {
            // Bukkit also passes subclasses that share the handler list.
            if (eventClass.isInstance(event)) {
                if (Logging.events()) {
                    fireLogged(listeners, names, event);
                } else {
                    fire(listeners, event);
                }
            }
        }, plugin, !listenToCancelled);
    }

    /**
     * As Skript logs events at config.yml's {@code verbosity: very high}: the event, then each trigger it runs and how
     * long it took, then how long the event took.
     */
    private void fireLogged(Trigger[] triggers, String[] names, Event event) {
        long start = System.nanoTime();
        logger.info("");
        logger.info("== {} with priority {}{} ==", event.getClass().getName(), priority,
                event instanceof org.bukkit.event.Cancellable cancellable && cancellable.isCancelled() ? " (cancelled)" : "");
        SimpleTriggerContext context = new SimpleTriggerContext(event, owner(event));
        for (int i = 0; i < triggers.length; i++) {
            long started = System.nanoTime();
            logger.info("# {}", names[i]);
            try {
                triggers[i].execute(context);
            } catch (Throwable e) {
                TriggerErrors.report(logger, e);
            }
            logger.info("# {} took {} milliseconds", names[i], (System.nanoTime() - started) / 1_000_000.0);
        }
        logger.info("== took {} milliseconds ==", (System.nanoTime() - start) / 1_000_000.0);
    }

    void fire(Trigger[] triggers, Event event) {
        SimpleTriggerContext context = new SimpleTriggerContext(event, owner(event));
        for (Trigger trigger : triggers) {
            try {
                trigger.execute(context);
            } catch (Throwable e) {
                // One trigger's failure doesn't stop the event's others, nor reach Bukkit.
                TriggerErrors.report(logger, e);
            }
        }
    }

    /** What an event happened to, whose thread a trigger that waits carries on on (Folia). */
    private static Object owner(Event event) {
        return switch (event) {
            case PlayerEvent player -> player.getPlayer();
            case EntityEvent entity -> entity.getEntity();
            case BlockEvent block -> block.getBlock().getLocation();
            case InventoryEvent inventory -> inventory.getView().getPlayer();
            default -> null;
        };
    }
}
