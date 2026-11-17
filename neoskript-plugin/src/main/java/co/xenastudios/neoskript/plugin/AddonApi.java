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
import co.xenastudios.neoskript.runtime.scheduler.Scheduler;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;

/** The addon API, as NeoSkript's plugin provides it: {@link NeoSkript#get()}. */
final class AddonApi implements NeoSkript {

    private final Plugin plugin;
    private final Addons addons;
    private final ScriptsView scripts = new ScriptsView();
    private final VariablesView variables;
    private final ThreadsView threads;

    AddonApi(Plugin plugin, Addons addons, VariableStore globals, Scheduler scheduler, boolean folia) {
        this.plugin = plugin;
        this.addons = addons;
        this.variables = new VariablesView(globals);
        this.threads = new ThreadsView(scheduler, folia);
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
    private static final class ScriptsView implements Scripts {

        @Override
        public List<String> loaded() {
            ScriptCatalog catalog = ScriptCatalog.get();
            return catalog.scripts(true, false).stream().filter(catalog::loaded).map(ScriptRef::name).toList();
        }

        @Override
        public boolean isLoaded(String name) {
            ScriptRef script = ScriptCatalog.get().find(name);
            return script != null && ScriptCatalog.get().loaded(script);
        }

        @Override
        public boolean load(String name) {
            ScriptCatalog catalog = ScriptCatalog.get();
            ScriptRef script = catalog.find(name);
            ScriptRef loaded = script == null ? null : catalog.load(script);
            return loaded != null && catalog.loaded(loaded);
        }

        @Override
        public boolean reload(String name) {
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
            FunctionRef function = FunctionLookup.find(name, null);
            if (function == null) {
                throw new IllegalArgumentException("There's no function called " + name);
            }
            return function.call(arguments == null ? new Object[0] : arguments);
        }
    }

    /** The global variables scripts use. */
    private record VariablesView(VariableStore globals) implements Variables {

        @Override
        public Object get(String name) {
            return globals.get(name);
        }

        @Override
        public void set(String name, Object value) {
            globals.set(name, value);
        }

        @Override
        public Map<String, Object> list(String name) {
            co.xenastudios.neoskript.runtime.KeyedValues list = globals.keyedList(name);
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < list.values().length; i++) {
                values.put(list.keys()[i], list.values()[i]);
            }
            return java.util.Collections.unmodifiableMap(values);
        }

        @Override
        public void deleteList(String name) {
            globals.deleteList(name);
        }
    }

    /** The scheduler triggers run on, and on Folia, its regions. */
    private final class ThreadsView implements Threads {

        private final Scheduler scheduler;
        private final boolean folia;

        ThreadsView(Scheduler scheduler, boolean folia) {
            this.scheduler = scheduler;
            this.folia = folia;
        }

        @Override
        public boolean isFolia() {
            return folia;
        }

        @Override
        public boolean owns(Object owner) {
            RegionAccess access = Regions.access();
            return access != null ? access.owns(owner) : plugin.getServer().isPrimaryThread();
        }

        @Override
        public void runGlobal(Runnable task) {
            scheduler.runGlobal(task);
        }

        @Override
        public void runGlobalLater(long ticks, Runnable task) {
            scheduler.runGlobalLater(Math.max(ticks, 0), task);
        }

        @Override
        public Runnable runGlobalRepeating(long delay, long period, Runnable task) {
            return scheduler.runGlobalRepeating(Math.max(delay, 0), Math.max(period, 1), task);
        }

        @Override
        public void run(Object owner, Runnable task) {
            RegionAccess access = Regions.access();
            if (access != null) {
                access.execute(owner, task);
            } else {
                scheduler.runGlobal(task);
            }
        }

        @Override
        public void runLater(Object owner, long ticks, Runnable task) {
            scheduler.runLater(owner, Math.max(ticks, 0), task);
        }

        @Override
        public void runAsync(Runnable task) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, scheduled -> task.run());
        }
    }
}
