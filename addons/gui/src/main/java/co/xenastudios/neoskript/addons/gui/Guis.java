/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.gui;

import co.xenastudios.neoskript.api.Threads;
import co.xenastudios.neoskript.api.element.SectionCode;
import co.xenastudios.neoskript.api.element.TriggerContext;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;

/**
 * The GUIs: by their inventories, for clicks; by id, for global ones; and the last one each trigger run made or
 * edited, as skript-gui keeps it by event.
 *
 * <p>Nothing here keeps a player or a run: the last GUIs are held weakly by the run's event, players are only held
 * while one of them is being moved to a new inventory, and a GUI goes as soon as nobody has it open and it has no id.
 */
final class Guis {

    private final Map<Inventory, Gui> byInventory = new ConcurrentHashMap<>();
    /**
     * Inventories of GUIs that went away: a player who still has one open (on Folia, until their region's thread
     * closes it) can't take anything from it.
     */
    private final Set<Inventory> retired = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final WeakIdentityMap<Object, Gui> last = new WeakIdentityMap<>();
    private final Set<UUID> paused = ConcurrentHashMap.newKeySet();
    private final Supplier<Threads> threads;
    private final Logger logger;

    Guis(Supplier<Threads> threads, Logger logger) {
        this.threads = threads;
        this.logger = logger;
    }

    Logger logger() {
        return logger;
    }

    void register(Gui gui) {
        byInventory.put(gui.inventory(), gui);
    }

    /** Takes a GUI away: its viewers' screens close, and it's cleared. */
    void unregister(Gui gui) {
        Inventory inventory = gui.inventory();
        retired.add(inventory);
        byInventory.remove(inventory, gui);
        last.removeValue(gui);
        for (HumanEntity viewer : gui.viewers()) {
            onThreadOf(viewer, () -> {
                if (viewer.getOpenInventory().getTopInventory().equals(inventory)) {
                    viewer.closeInventory();
                }
            });
        }
        gui.clear();
    }

    /** Its inventory was swapped for another: clicks in the old one are refused from now on. */
    void moved(Gui gui, Inventory old, Inventory replacement) {
        byInventory.put(replacement, gui);
        byInventory.remove(old, gui);
        retired.add(old);
    }

    /** The GUI shown in {@code inventory}, or {@code null}. */
    Gui of(Inventory inventory) {
        return inventory == null ? null : byInventory.get(inventory);
    }

    /** Whether {@code inventory} was a GUI's, which went away. */
    boolean retired(Inventory inventory) {
        return inventory != null && retired.contains(inventory);
    }

    /** The GUIs with an id. */
    List<Gui> global() {
        List<Gui> global = new ArrayList<>();
        for (Gui gui : byInventory.values()) {
            if (gui.id() != null) {
                global.add(gui);
            }
        }
        return global;
    }

    /** The GUI with this id (case matters, as in skript-gui), or {@code null}. */
    Gui byId(String id) {
        for (Gui gui : byInventory.values()) {
            if (id.equals(gui.id())) {
                return gui;
            }
        }
        return null;
    }

    /** The GUI the code running for {@code context} made or edited last, or {@code null}. */
    Gui last(TriggerContext context) {
        return last.get(key(context));
    }

    void last(TriggerContext context, Gui gui) {
        last.put(key(context), gui);
    }

    /** The GUI of a GUI's event, whose code runs for it: clicks, opening and closing. */
    void last(Object event, Gui gui) {
        last.put(event, gui);
    }

    /** A run is its event, or where there's none (a function), its context: each call has its own. */
    private static Object key(TriggerContext context) {
        Object event = context.event();
        return event != null ? event : context;
    }

    // Players being moved to another inventory, whose events aren't handled meanwhile

    void pause(HumanEntity player) {
        paused.add(player.getUniqueId());
    }

    void resume(HumanEntity player) {
        paused.remove(player.getUniqueId());
    }

    boolean paused(HumanEntity player) {
        return paused.contains(player.getUniqueId());
    }

    void forget(HumanEntity player) {
        paused.remove(player.getUniqueId());
    }

    /**
     * Runs {@code task} on the thread that owns {@code entity}, on a later tick: for what can't happen during the
     * event being handled, as opening an inventory while one closes.
     */
    void later(HumanEntity entity, Runnable task) {
        Threads threads = this.threads.get();
        if (threads == null) {
            task.run();
        } else {
            threads.run(entity, task);
        }
    }

    /** Runs {@code task} on the thread that owns {@code entity}: now if it's this one. */
    void onThreadOf(HumanEntity entity, Runnable task) {
        Threads threads = this.threads.get();
        if (threads == null || threads.owns(entity)) {
            task.run();
        } else {
            threads.run(entity, task);
        }
    }

    /**
     * Scripts reloaded or unloaded: the GUIs with code from them close, as that code is the old version. Those
     * scripts' loads make them again.
     */
    void scriptsUnloaded(Set<String> scripts) {
        for (Gui gui : List.copyOf(byInventory.values())) {
            for (SectionCode code : gui.code()) {
                if (scripts.contains(code.script())) {
                    unregister(gui);
                    break;
                }
            }
        }
    }

    /** Every GUI goes, as the addon disables. */
    void closeAll() {
        for (Gui gui : List.copyOf(byInventory.values())) {
            unregister(gui);
        }
    }

    /** A map whose keys are held weakly and compared by identity. */
    static final class WeakIdentityMap<K, V> {

        private final Map<Key<K>, V> map = new ConcurrentHashMap<>();
        private final ReferenceQueue<K> queue = new ReferenceQueue<>();

        private static final class Key<K> extends WeakReference<K> {
            private final int hash;

            Key(K referent, ReferenceQueue<K> queue) {
                super(referent, queue);
                hash = System.identityHashCode(referent);
            }

            @Override
            public boolean equals(Object other) {
                return this == other || other instanceof Key<?> key && key.get() != null && key.get() == get();
            }

            @Override
            public int hashCode() {
                return hash;
            }
        }

        V get(K key) {
            expunge();
            return map.get(new Key<>(key, null));
        }

        void put(K key, V value) {
            expunge();
            map.put(new Key<>(key, queue), value);
        }

        void removeValue(V value) {
            map.values().removeIf(v -> v == value);
        }

        int size() {
            expunge();
            return map.size();
        }

        private void expunge() {
            java.lang.ref.Reference<? extends K> gone;
            while ((gone = queue.poll()) != null) {
                map.remove(gone);
            }
        }
    }
}
