package co.xenastudios.neoskript.api;

import co.xenastudios.neoskript.api.registry.SyntaxRegistry;

/**
 * The extension point implemented by addons. Discovered via {@link java.util.ServiceLoader} and
 * driven through a simple lifecycle by the NeoSkript runtime.
 *
 * <p>Implementations must declare themselves in
 * {@code META-INF/services/co.xenastudios.neoskript.api.NeoSkriptAddon}.
 */
public interface NeoSkriptAddon {

    /**
     * @return the addon's display name, used in logs and error messages
     */
    String name();

    /**
     * Called once during load, before scripts are parsed. Implementations contribute their syntax
     * elements and types here.
     *
     * @param registry the registry to add syntax to
     */
    void registerSyntax(SyntaxRegistry registry);

    /**
     * Called after all syntax has been registered and scripts have loaded.
     */
    default void onEnable() {
    }

    /**
     * Called during shutdown or before a reload, to release resources.
     */
    default void onDisable() {
    }
}
