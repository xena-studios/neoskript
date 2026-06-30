package co.xenastudios.neoskript.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilerTest {

    @Test
    void recordsNothingWhileDisabled() {
        Profiler profiler = new Profiler();
        profiler.record("a", 1000);
        assertTrue(profiler.top(10).isEmpty());
    }

    @Test
    void aggregatesAndRanksByTotalTime() {
        Profiler profiler = new Profiler();
        profiler.setEnabled(true);
        profiler.record("a", 100);
        profiler.record("a", 300);
        profiler.record("b", 50);

        List<Profiler.Sample> top = profiler.top(10);
        assertEquals("a", top.get(0).key());
        assertEquals(2, top.get(0).count());
        assertEquals(400, top.get(0).totalNanos());
        assertEquals(200, top.get(0).averageNanos());
        assertEquals("b", top.get(1).key());
    }

    @Test
    void resetClearsSamples() {
        Profiler profiler = new Profiler();
        profiler.setEnabled(true);
        profiler.record("a", 100);
        profiler.reset();
        assertTrue(profiler.top(10).isEmpty());
    }
}
