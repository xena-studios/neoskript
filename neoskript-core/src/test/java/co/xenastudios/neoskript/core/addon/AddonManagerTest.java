package co.xenastudios.neoskript.core.addon;

import co.xenastudios.neoskript.api.NeoSkriptAddon;
import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonManagerTest {

    private final AddonManager manager = new AddonManager(Logger.getAnonymousLogger());

    @Test
    void enablesAddonsAndRegistersTheirSyntax() {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        AtomicBoolean enabled = new AtomicBoolean();
        NeoSkriptAddon addon = new NeoSkriptAddon() {
            @Override
            public String name() {
                return "Test";
            }

            @Override
            public void registerSyntax(SyntaxRegistry r) {
                r.registerEffect("noop", arguments -> ctx -> {
                });
            }

            @Override
            public void onEnable() {
                enabled.set(true);
            }
        };

        List<NeoSkriptAddon> on = manager.enable(List.of(addon), registry);

        assertEquals(1, on.size());
        assertTrue(enabled.get());
        assertEquals(1, registry.size());
    }

    @Test
    void skipsAddonsThatThrowOnEnable() {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        NeoSkriptAddon broken = new NeoSkriptAddon() {
            @Override
            public String name() {
                return "Broken";
            }

            @Override
            public void registerSyntax(SyntaxRegistry r) {
                throw new IllegalStateException("boom");
            }
        };

        List<NeoSkriptAddon> on = manager.enable(List.of(broken), registry);
        assertTrue(on.isEmpty());
    }

    @Test
    void disablingDoesNotThrowWhenAddonMisbehaves() {
        NeoSkriptAddon broken = new NeoSkriptAddon() {
            @Override
            public String name() {
                return "Broken";
            }

            @Override
            public void registerSyntax(SyntaxRegistry r) {
            }

            @Override
            public void onDisable() {
                throw new IllegalStateException("boom");
            }
        };
        manager.disable(List.of(broken)); // must not propagate
        assertFalse(broken.name().isEmpty());
    }
}
