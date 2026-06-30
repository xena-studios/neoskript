package co.xenastudios.neoskript.platform.event;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BukkitEventBridgeTest {

    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void dispatchesMatchingEventsToTheHandler() {
        AtomicInteger count = new AtomicInteger();
        BukkitEventBridge.register(plugin, PlayerJoinEvent.class, event -> count.incrementAndGet());

        server.addPlayer();
        server.addPlayer();

        assertEquals(2, count.get());
    }

    @Test
    void unregisteringTheListenerStopsDispatch() {
        AtomicInteger count = new AtomicInteger();
        Listener listener = BukkitEventBridge.register(plugin, PlayerJoinEvent.class, event -> count.incrementAndGet());

        server.addPlayer();
        HandlerList.unregisterAll(listener);
        server.addPlayer();

        assertEquals(1, count.get(), "no further dispatch after unregister (mirrors reload)");
    }
}
