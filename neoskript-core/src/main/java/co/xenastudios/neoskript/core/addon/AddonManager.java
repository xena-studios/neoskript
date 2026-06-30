package co.xenastudios.neoskript.core.addon;

import co.xenastudios.neoskript.api.NeoSkriptAddon;
import co.xenastudios.neoskript.api.registry.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discovers and drives the lifecycle of {@link NeoSkriptAddon}s. Addons are found via
 * {@link ServiceLoader} (declared in {@code META-INF/services}), have their syntax registered, and
 * are enabled/disabled in order. A misbehaving addon is logged and skipped rather than aborting load.
 */
public final class AddonManager {

    private final Logger logger;

    public AddonManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Discovers addons on the given class loader.
     *
     * @param classLoader the loader to search
     * @return the discovered addons
     */
    public static List<NeoSkriptAddon> discover(ClassLoader classLoader) {
        List<NeoSkriptAddon> addons = new ArrayList<>();
        ServiceLoader.load(NeoSkriptAddon.class, classLoader).forEach(addons::add);
        return addons;
    }

    /**
     * Registers each addon's syntax and enables it.
     *
     * @param addons   the addons to enable
     * @param registry the registry to register syntax into
     * @return the addons that enabled successfully
     */
    public List<NeoSkriptAddon> enable(Iterable<NeoSkriptAddon> addons, SyntaxRegistry registry) {
        List<NeoSkriptAddon> enabled = new ArrayList<>();
        for (NeoSkriptAddon addon : addons) {
            try {
                addon.registerSyntax(registry);
                addon.onEnable();
                enabled.add(addon);
                logger.info("Enabled NeoSkript addon: " + addon.name());
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Failed to enable addon " + safeName(addon), e);
            }
        }
        return enabled;
    }

    /**
     * Disables addons (in reverse order).
     *
     * @param addons the addons to disable
     */
    public void disable(List<NeoSkriptAddon> addons) {
        for (int i = addons.size() - 1; i >= 0; i--) {
            NeoSkriptAddon addon = addons.get(i);
            try {
                addon.onDisable();
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Failed to disable addon " + safeName(addon), e);
            }
        }
    }

    private static String safeName(NeoSkriptAddon addon) {
        try {
            return addon.name();
        } catch (RuntimeException e) {
            return addon.getClass().getName();
        }
    }
}
