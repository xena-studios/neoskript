package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.lang.BuiltinModule;
import co.xenastudios.neoskript.platform.PlatformInfo;
import co.xenastudios.neoskript.platform.scheduler.NeoScheduler;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NeoSkript plugin entry point.
 *
 * <p>Phase 0 boots the core services — platform detection, the Folia-safe scheduler, and the syntax
 * registry seeded with built-ins — and reports load timing. Script discovery, parsing, and trigger
 * registration land in Phase 1.
 */
public final class NeoSkriptPlugin extends JavaPlugin {

    private PlatformInfo platform;
    private NeoScheduler scheduler;
    private DefaultSyntaxRegistry registry;

    @Override
    public void onEnable() {
        long startNanos = System.nanoTime();

        this.platform = PlatformInfo.detect();
        this.scheduler = NeoScheduler.create(this);
        this.registry = new DefaultSyntaxRegistry();

        BuiltinModule.registerAll(registry);
        // Phase 1: discover ServiceLoader addons and call NeoSkriptAddon#registerSyntax here.

        double elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        getLogger().info("Running on " + platform.describe());
        getLogger().info(String.format(
                "NeoSkript enabled in %.2f ms — %d built-in syntaxes registered (Folia: %s)",
                elapsedMs, registry.size(), platform.isFolia()));
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
