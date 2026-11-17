/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** A value per thread that the threads themselves don't hold, so NeoSkript's classes don't outlive the plugin. */
class PerThreadTest {

    @Test
    void eachThreadHasItsOwn() throws Exception {
        PerThread<String> values = new PerThread<>();
        values.set("main");
        AtomicReference<String> seen = new AtomicReference<>("unset");
        Thread other = new Thread(() -> seen.set(values.get()));
        other.start();
        other.join();
        assertNull(seen.get());
        assertEquals("main", values.get());
        values.set(null);
        assertNull(values.get());
    }
}
