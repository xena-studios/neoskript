/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.compiler.CompiledScripts;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
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
final class EventDispatcher implements Listener {

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

    /** Listens for the events of {@code triggers}, and only those, replacing what it listened for before. */
    void update(Collection<CompiledScripts.CompiledTrigger> triggers) {
        Map<Class<? extends Event>, List<Trigger>> byClass = new LinkedHashMap<>();
        for (CompiledScripts.CompiledTrigger trigger : triggers) {
            if (trigger.eventClasses() == null) {
                continue;
            }
            for (Class<?> eventClass : trigger.eventClasses()) {
                byClass.computeIfAbsent(eventClass.asSubclass(Event.class), k -> new ArrayList<>()).add(trigger.trigger());
            }
        }
        HandlerList.unregisterAll(this);
        for (Map.Entry<Class<? extends Event>, List<Trigger>> entry : byClass.entrySet()) {
            Class<? extends Event> eventClass = entry.getKey();
            Trigger[] listeners = entry.getValue().toArray(Trigger[]::new);
            plugin.getServer().getPluginManager().registerEvent(eventClass, this, priority, (listener, event) -> {
                // Bukkit also passes subclasses that share the handler list.
                if (eventClass.isInstance(event)) {
                    fire(listeners, event);
                }
            }, plugin, !listenToCancelled);
        }
    }

    private void fire(Trigger[] triggers, Event event) {
        SimpleTriggerContext context = new SimpleTriggerContext(event, owner(event));
        for (Trigger trigger : triggers) {
            try {
                trigger.execute(context);
            } catch (ScriptRuntimeException e) {
                logger.error("Error in {}, line {}: {}", e.script(), e.line(), e.getCause().toString(), e.getCause());
            }
        }
    }

    /** What an event happened to, whose thread a trigger that waits carries on on (Folia). */
    private static Object owner(Event event) {
        return switch (event) {
            case PlayerEvent player -> player.getPlayer();
            case EntityEvent entity -> entity.getEntity();
            case BlockEvent block -> block.getBlock().getLocation();
            default -> null;
        };
    }
}
