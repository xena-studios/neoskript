package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.Profiler;
import co.xenastudios.neoskript.core.variable.FlatFileVariableStore;
import co.xenastudios.neoskript.lang.BuiltinModule;
import co.xenastudios.neoskript.lang.event.BuiltinEvents;
import co.xenastudios.neoskript.platform.PlatformInfo;
import co.xenastudios.neoskript.platform.scheduler.NeoScheduler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * NeoSkript plugin entry point.
 *
 * <p>Boots the core services, registers built-in syntax/events, loads persisted global variables,
 * then discovers and loads {@code .sk} scripts (registering their triggers and functions). Persisted
 * variables are saved on disable.
 */
public final class NeoSkriptPlugin extends JavaPlugin {

    private PlatformInfo platform;
    private NeoScheduler scheduler;
    private DefaultSyntaxRegistry registry;
    private EventRegistry events;
    private FunctionRegistry functions;
    private final Profiler profiler = new Profiler();
    private final Map<String, Object> globalVariables = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        long startNanos = System.nanoTime();

        this.platform = PlatformInfo.detect();
        this.scheduler = NeoScheduler.create(this);

        this.registry = new DefaultSyntaxRegistry();
        BuiltinModule.registerAll(registry);

        this.events = new EventRegistry();
        BuiltinEvents.registerAll(events);

        this.functions = new FunctionRegistry();
        // Phase 5: discover ServiceLoader addons and call NeoSkriptAddon#registerSyntax here.

        loadVariables();
        getLogger().info("Running on " + platform.describe());

        ScriptParser parser = new ScriptParser(registry, events, functions);
        ScriptLoader loader =
                new ScriptLoader(this, parser, functions, globalVariables, scheduler, profiler);
        ScriptLoader.Result result = loader.loadAll(getDataFolder().toPath().resolve("scripts"));

        var command = getCommand("neoskript");
        if (command != null) {
            command.setExecutor(new NeoSkriptCommand(this, loader, profiler));
        }

        double elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        getLogger().info(String.format(
                "NeoSkript enabled in %.2f ms — %d syntaxes, %d triggers, %d functions from %d script(s)%s (Folia: %s)",
                elapsedMs, registry.size(), result.triggers(), functions.size(), result.scripts(),
                result.failed() > 0 ? " (" + result.failed() + " failed)" : "",
                platform.isFolia()));
    }

    @Override
    public void onDisable() {
        saveVariables();
        getLogger().info("NeoSkript disabled.");
    }

    private void loadVariables() {
        try {
            globalVariables.putAll(FlatFileVariableStore.load(variablesFile()));
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to load persisted variables", e);
        }
    }

    private void saveVariables() {
        try {
            FlatFileVariableStore.save(variablesFile(), globalVariables);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to save persisted variables", e);
        }
    }

    private Path variablesFile() {
        return getDataFolder().toPath().resolve("variables.csv");
    }

    /** @return the detected platform information */
    public PlatformInfo platform() {
        return platform;
    }

    /** @return the Folia-safe scheduler */
    public NeoScheduler scheduler() {
        return scheduler;
    }

    /** @return the syntax registry */
    public DefaultSyntaxRegistry registry() {
        return registry;
    }
}
