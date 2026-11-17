/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.api.NeoSkript;
import co.xenastudios.neoskript.api.Scripts;
import co.xenastudios.neoskript.api.Threads;
import co.xenastudios.neoskript.api.Variables;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.FunctionLookup;
import co.xenastudios.neoskript.runtime.FunctionRef;
import co.xenastudios.neoskript.runtime.ScriptCatalog;
import co.xenastudios.neoskript.runtime.ScriptRef;
import co.xenastudios.neoskript.runtime.region.RegionAccess;
import co.xenastudios.neoskript.runtime.region.Regions;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/** The addon API, as NeoSkript's plugin provides it: {@link NeoSkript#get()}. */
final class AddonApi implements NeoSkript {

    private static final String NOT_READY = "NeoSkript isn't ready yet: scripts and saved variables load on the"
            + " server's first tick. Use NeoSkript.get().whenReady(...) to wait for them.";

    private final Plugin plugin;
    private final Addons addons;
    private final Logger logger;
    private final ScriptsView scripts = new ScriptsView();
    private final VariablesView variables;
    private final ThreadsView threads = new ThreadsView();
    /** Whether NeoSkript is ready, and what waits for it until then. */
    private boolean ready;
    private final List<Runnable> waiting = new ArrayList<>();

    AddonApi(Plugin plugin, Addons addons, VariableStore globals, Logger logger) {
        this.plugin = plugin;
        this.addons = addons;
        this.logger = logger;
        this.variables = new VariablesView(globals);
    }

    @Override
    public String version() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public void addon(String name, String version, Consumer<? super Registrar> registration) {
        addons.add(name, version, registration);
    }

    @Override
    public List<String> addons() {
        return addons.names();
    }

    @Override
    public synchronized boolean isReady() {
        return ready;
    }

    @Override
    public void whenReady(Runnable task) {
        java.util.Objects.requireNonNull(task, "task");
        synchronized (this) {
            if (!ready) {
                waiting.add(task);
                return;
            }
        }
        task.run();
    }

    /** Marks NeoSkript ready, once scripts have loaded, and runs what waited for it. */
    void ready() {
        List<Runnable> tasks;
        synchronized (this) {
            ready = true;
            tasks = List.copyOf(waiting);
            waiting.clear();
        }
        for (Runnable task : tasks) {
            try {
                task.run();
            } catch (RuntimeException | LinkageError e) {
                logger.error("A task waiting for NeoSkript to be ready failed", e);
            }
        }
    }

    private void checkReady() {
        if (!isReady()) {
            throw new IllegalStateException(NOT_READY);
        }
    }

    @Override
    public Scripts scripts() {
        return scripts;
    }

    @Override
    public Variables variables() {
        return variables;
    }

    @Override
    public Threads threads() {
        return threads;
    }

    /** The scripts folder, through the catalog the plugin installs for {@code script} syntax. */
    private final class ScriptsView implements Scripts {

        @Override
        public List<String> loaded() {
            checkReady();
            ScriptCatalog catalog = ScriptCatalog.get();
            return catalog.scripts(true, false).stream().filter(catalog::loaded).map(ScriptRef::name).toList();
        }

        @Override
        public boolean isLoaded(String name) {
            checkReady();
            ScriptRef script = ScriptCatalog.get().find(name);
            return script != null && ScriptCatalog.get().loaded(script);
        }

        @Override
        public boolean load(String name) {
            checkReady();
            ScriptCatalog catalog = ScriptCatalog.get();
            ScriptRef script = catalog.find(name);
            ScriptRef loaded = script == null ? null : catalog.load(script);
            return loaded != null && catalog.loaded(loaded);
        }

        @Override
        public boolean reload(String name) {
            checkReady();
            ScriptCatalog catalog = ScriptCatalog.get();
            ScriptRef script = catalog.find(name);
            if (script == null || !catalog.scripts(true, false).contains(script)) {
                return false;
            }
            catalog.reload(script);
            return true;
        }

        @Override
        public boolean unload(String name) {
            checkReady();
            ScriptCatalog catalog = ScriptCatalog.get();
            ScriptRef script = catalog.find(name);
            if (script == null || !catalog.loaded(script)) {
                return false;
            }
            catalog.unload(script);
            return true;
        }

        @Override
        public Object call(String name, Object... arguments) {
            checkReady();
            FunctionRef function = FunctionLookup.find(name, null);
            if (function == null) {
                throw new IllegalArgumentException("There's no function called " + name);
            }
            return function.call(arguments == null ? new Object[0] : arguments);
        }
    }

    /** The global variables scripts use. */
    private final class VariablesView implements Variables {

        private final VariableStore globals;

        VariablesView(VariableStore globals) {
            this.globals = globals;
        }

        @Override
        public Object get(String name) {
            checkReady();
            return globals.get(name);
        }

        @Override
        public void set(String name, Object value) {
            checkReady();
            globals.set(name, value);
        }

        @Override
        public Map<String, Object> list(String name) {
            checkReady();
            co.xenastudios.neoskript.runtime.KeyedValues list = globals.keyedList(name);
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < list.values().length; i++) {
                values.put(list.keys()[i], list.values()[i]);
            }
            return java.util.Collections.unmodifiableMap(values);
        }

        @Override
        public void deleteList(String name) {
            checkReady();
            globals.deleteList(name);
        }
    }

    /**
     * Paper's region, entity, global and async schedulers, which Paper runs on its main thread and Folia on each
     * region's. Tasks belong to the plugin whose class scheduled them, so they end when it disables.
     */
    private final class ThreadsView implements Threads {

        private final StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

        /** The plugin whose class called the API, or NeoSkript if it's none's. */
        private Plugin caller() {
            ClassLoader loader = walker.walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                    .filter(type -> type != ThreadsView.class).findFirst().map(Class::getClassLoader).orElse(null));
            for (Plugin candidate : plugin.getServer().getPluginManager().getPlugins()) {
                if (candidate.getClass().getClassLoader() == loader) {
                    // A disabled plugin can't schedule: Paper says so, as for its own tasks.
                    return candidate;
                }
            }
            return plugin;
        }

        private Server server() {
            return plugin.getServer();
        }

        @Override
        public boolean isFolia() {
            return co.xenastudios.neoskript.runtime.platform.Platform.detect()
                    == co.xenastudios.neoskript.runtime.platform.Platform.FOLIA;
        }

        @Override
        public boolean owns(Object owner) {
            RegionAccess access = Regions.access();
            return access != null ? access.owns(owner) : server().isPrimaryThread();
        }

        @Override
        public void runGlobal(Runnable task) {
            server().getGlobalRegionScheduler().execute(caller(), task);
        }

        @Override
        public void runGlobalLater(long ticks, Runnable task) {
            Plugin owner = caller();
            if (ticks < 1) {
                server().getGlobalRegionScheduler().execute(owner, task);
            } else {
                server().getGlobalRegionScheduler().runDelayed(owner, scheduled -> task.run(), ticks);
            }
        }

        @Override
        public Runnable runGlobalRepeating(long delay, long period, Runnable task) {
            return server().getGlobalRegionScheduler().runAtFixedRate(caller(), scheduled -> task.run(),
                    Math.max(delay, 1), Math.max(period, 1))::cancel;
        }

        @Override
        public void run(Object owner, Runnable task) {
            Plugin by = caller();
            switch (owner) {
                // Not run for an entity that's gone: the scheduler retires it, and the check covers one that died.
                case Entity entity -> entity.getScheduler().run(by, scheduled -> {
                    if (entity.isValid()) {
                        task.run();
                    }
                }, null);
                case Block block -> server().getRegionScheduler().execute(by, block.getLocation(), task);
                case Location location when location.getWorld() != null ->
                        server().getRegionScheduler().execute(by, location, task);
                case null, default -> server().getGlobalRegionScheduler().execute(by, task);
            }
        }

        @Override
        public void runLater(Object owner, long ticks, Runnable task) {
            Plugin by = caller();
            long delay = Math.max(ticks, 1);
            switch (owner) {
                case Entity entity -> entity.getScheduler().runDelayed(by, scheduled -> {
                    if (entity.isValid()) {
                        task.run();
                    }
                }, null, delay);
                case Block block -> server().getRegionScheduler().runDelayed(by, block.getLocation(),
                        scheduled -> task.run(), delay);
                case Location location when location.getWorld() != null ->
                        server().getRegionScheduler().runDelayed(by, location, scheduled -> task.run(), delay);
                case null, default -> server().getGlobalRegionScheduler().runDelayed(by, scheduled -> task.run(), delay);
            }
        }

        @Override
        public void runAsync(Runnable task) {
            server().getAsyncScheduler().runNow(caller(), scheduled -> task.run());
        }
    }
}
