/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.harness.bot;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitTask;

/**
 * {@code bot stats <label>}: forces a full GC and logs what a plugin holds in the server, for the reload soak:
 * {@code [bot] stats <label> classes=.. heapKb=.. listeners=.. commands=.. brigadier=.. tasks=.. threads=..
 * nonDaemon=.. pluginThreads=.. bossBars=..}.
 */
final class ServerStats {

    private ServerStats() {}

    /**
     * Weak references to each class loader the plugin has had, kept in the system properties, which outlive this
     * plugin's own class loader across {@code /reload}.
     */
    @SuppressWarnings("unchecked")
    private static java.util.List<java.lang.ref.WeakReference<ClassLoader>> loaders(String key) {
        return (java.util.List<java.lang.ref.WeakReference<ClassLoader>>) System.getProperties().computeIfAbsent(
                "neoskript.harness.loaders." + key, k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()));
    }

    private static long alive(String key, ClassLoader current) {
        java.util.List<java.lang.ref.WeakReference<ClassLoader>> loaders = loaders(key);
        if (current != null && loaders.stream().noneMatch(ref -> ref.get() == current)) {
            loaders.add(new java.lang.ref.WeakReference<>(current));
        }
        return loaders.stream().filter(ref -> ref.get() != null).count();
    }

    static String sample(String pluginName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        alive(pluginName, plugin == null ? null : plugin.getClass().getClassLoader());
        alive("bot", ServerStats.class.getClassLoader());
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        long loadersAlive = alive(pluginName, null);
        Bukkit.getLogger().info("[bot] bot loaders alive: " + alive("bot", null));
        var classes = ManagementFactory.getClassLoadingMXBean();
        long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        int listeners = 0;
        for (HandlerList list : HandlerList.getHandlerLists()) {
            for (RegisteredListener listener : list.getRegisteredListeners()) {
                if (listener.getPlugin() == plugin) {
                    listeners++;
                }
            }
        }
        int commands = 0;
        int brigadier = 0;
        for (var node : MinecraftServer.getServer().getCommands().getDispatcher().getRoot().getChildren()) {
            brigadier++;
            if (node.getName().startsWith("skript:")) {
                commands++;
            }
        }
        int threads = 0;
        int nonDaemon = 0;
        int pluginThreads = 0;
        ClassLoader loader = plugin == null ? null : plugin.getClass().getClassLoader();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            threads++;
            if (!thread.isDaemon()) {
                nonDaemon++;
            }
            // Threads NeoSkript started: its own classes, or its class loader as their context.
            if (loader != null && (thread.getClass().getClassLoader() == loader || thread.getContextClassLoader() == loader
                    || thread.getName().toLowerCase(Locale.ROOT).contains("neoskript"))) {
                pluginThreads++;
            }
        }
        int bossBars = 0;
        for (var it = Bukkit.getBossBars(); it.hasNext(); it.next()) {
            bossBars++;
        }
        return String.format(Locale.ROOT,
                "loaders=%d classes=%d heapKb=%d listeners=%d commands=%d brigadier=%d tasks=%d threads=%d nonDaemon=%d pluginThreads=%d bossBars=%d",
                loadersAlive, classes.getLoadedClassCount(), heap / 1024, listeners, commands, brigadier, tasks(plugin), threads,
                nonDaemon, pluginThreads, bossBars);
    }

    /**
     * The plugin's scheduled tasks that haven't been cancelled: Bukkit's on Paper, and the global region's and the
     * async scheduler's on both, found in the schedulers' fields as the server doesn't list them.
     */
    private static int tasks(Plugin plugin) {
        int count = 0;
        try {
            for (BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
                if (plugin != null && task.getOwner().getName().equals(plugin.getName()) && !task.isCancelled()) {
                    count++;
                }
            }
        } catch (UnsupportedOperationException folia) {
            // Folia has no Bukkit scheduler.
        }
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> found = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(Bukkit.getGlobalRegionScheduler(), plugin, 4, seen, found);
        collect(Bukkit.getAsyncScheduler(), plugin, 4, seen, found);
        return count + found.size();
    }

    private static void collect(Object value, Plugin plugin, int depth, Set<Object> seen, Set<Object> found) {
        if (value == null || depth < 0 || !seen.add(value)) {
            return;
        }
        if (value instanceof ScheduledTask task) {
            // Any instance of the plugin: an old one's tasks after /reload count too.
            if (plugin != null && task.getOwningPlugin().getName().equals(plugin.getName()) && !task.isCancelled()) {
                found.add(task);
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object entry : map.values().toArray()) {
                collect(entry, plugin, depth - 1, seen, found);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object entry : collection.toArray()) {
                collect(entry, plugin, depth - 1, seen, found);
            }
            return;
        }
        if (value instanceof Object[] array) {
            for (Object entry : array) {
                collect(entry, plugin, depth - 1, seen, found);
            }
            return;
        }
        String name = value.getClass().getName();
        if (!name.startsWith("io.papermc.") && !name.startsWith("ca.spottedleaf.")) {
            return;
        }
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    collect(field.get(value), plugin, depth - 1, seen, found);
                } catch (ReflectiveOperationException | RuntimeException unreadable) {
                    // Not a field the count needs.
                }
            }
        }
    }
}
