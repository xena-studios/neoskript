package co.xenastudios.neoskript.platform.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoSchedulerTest {

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
    void runsAGlobalTaskOnTheNextTick() {
        NeoScheduler scheduler = NeoScheduler.create(plugin);
        AtomicBoolean ran = new AtomicBoolean();

        scheduler.runGlobal(() -> ran.set(true));
        server.getScheduler().performOneTick();

        assertTrue(ran.get());
    }

    @Test
    void repeatingTaskRunsThenStopsAfterCancel() {
        NeoScheduler scheduler = NeoScheduler.create(plugin);
        AtomicInteger count = new AtomicInteger();

        var handle = scheduler.runRepeating(count::incrementAndGet, 1, 1);
        server.getScheduler().performTicks(3);
        int afterRunning = count.get();
        handle.cancel();
        server.getScheduler().performTicks(3);

        assertTrue(afterRunning >= 1, "repeating task should have run at least once");
        assertEquals(afterRunning, count.get(), "no further runs after cancel");
    }
}
