package co.xenastudios.neoskript.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotPathTrackerTest {

    @Test
    void signalsExactlyOnceWhenCrossingThreshold() {
        HotPathTracker tracker = new HotPathTracker(3);
        Object key = new Object();

        assertFalse(tracker.recordAndCheckHot(key)); // 1
        assertFalse(tracker.recordAndCheckHot(key)); // 2
        assertTrue(tracker.recordAndCheckHot(key));  // 3 — crosses
        assertFalse(tracker.recordAndCheckHot(key)); // 4 — already hot

        assertEquals(4, tracker.count(key));
    }

    @Test
    void tracksKeysIndependently() {
        HotPathTracker tracker = new HotPathTracker(2);
        Object a = new Object();
        Object b = new Object();
        tracker.recordAndCheckHot(a);
        assertEquals(1, tracker.count(a));
        assertEquals(0, tracker.count(b));
    }
}
