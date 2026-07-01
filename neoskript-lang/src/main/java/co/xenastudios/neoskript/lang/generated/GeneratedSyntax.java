package co.xenastudios.neoskript.lang.generated;

import co.xenastudios.neoskript.api.registry.SyntaxRegistry;

/** Registers every generated syntax batch. Invoked once from {@code BuiltinModule}. */
public final class GeneratedSyntax {
    private GeneratedSyntax() {}

    public static void registerAll(SyntaxRegistry registry) {
        GeneratedExpressions.register(registry);
        GeneratedConditions.register(registry);
        GeneratedEffects.register(registry);
    }
}
