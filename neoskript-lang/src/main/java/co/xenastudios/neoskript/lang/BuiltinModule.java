package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.registry.SyntaxRegistry;

/**
 * Registers NeoSkript's built-in syntax elements and types.
 *
 * <p>Phase 0 wires a single placeholder effect to prove the registry/API path compiles end-to-end.
 * Phase 2 fills this out with the bulk of Skript's built-in language.
 */
public final class BuiltinModule {

    private BuiltinModule() {
    }

    /**
     * Registers all built-in syntax into the given registry.
     *
     * @param registry the target registry
     */
    public static void registerAll(SyntaxRegistry registry) {
        // Placeholder built-in: a no-op "broadcast" effect, replaced by the real implementation in Phase 2.
        registry.registerEffect("broadcast %text%", () -> ctx -> {
            // Phase 2: resolve the %text% argument and broadcast it to the server.
        });
    }
}
