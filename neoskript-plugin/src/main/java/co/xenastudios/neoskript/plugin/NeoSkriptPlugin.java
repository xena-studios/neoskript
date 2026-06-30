package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.lang.BuiltinModule;
import co.xenastudios.neoskript.lang.event.BuiltinEvents;
import co.xenastudios.neoskript.platform.PlatformInfo;
import co.xenastudios.neoskript.platform.scheduler.NeoScheduler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeoSkript plugin entry point.
 *
 * <p>Phase 1 boots the core services, registers built-in syntax and events, then discovers and
 * loads {@code .sk} scripts from the data folder, wiring their triggers to Bukkit events. Per-script
 * reload, sections, and the wider language land in later phases.
 */
public final class NeoSkriptPlugin extends JavaPlugin {

    private PlatformInfo platform;
    private NeoScheduler scheduler;
    private DefaultSyntaxRegistry registry;
    private EventRegistry events;
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
        // Phase 2: discover ServiceLoader addons and call NeoSkriptAddon#registerSyntax here.

        getLogger().info("Running on " + platform.describe());

        ScriptParser parser = new ScriptParser(registry, events);
        ScriptLoader loader = new ScriptLoader(this, parser, globalVariables);
        ScriptLoader.Result result = loader.loadAll(getDataFolder().toPath().resolve("scripts"));

        double elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        getLogger().info(String.format(
                "NeoSkript enabled in %.2f ms — %d syntaxes, %d triggers from %d script(s)%s (Folia: %s)",
                elapsedMs, registry.size(), result.triggers(), result.scripts(),
                result.failed() > 0 ? " (" + result.failed() + " failed)" : "",
                platform.isFolia()));
    }

    @Override
    public void onDisable() {
        getLogger().info("NeoSkript disabled.");
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
