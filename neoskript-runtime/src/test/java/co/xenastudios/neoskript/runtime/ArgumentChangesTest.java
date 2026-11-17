/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.xenastudios.neoskript.api.element.Effect;
import org.junit.jupiter.api.Test;

class ArgumentChangesTest {

    private static Object[] run(Effect effect, int count) {
        return ArgumentChanges.execute(effect, new SimpleTriggerContext(null), new Object[count], count);
    }

    @Test
    void anEffectRunInsideAnotherKeepsTheOuterChanges() {
        // As releasing a bee fires a trigger that runs an addon effect before the release sets its variable.
        Object[][] inner = new Object[1][];
        Object[] outer = run((context, arguments) -> {
            ArgumentChanges.put(0, "before");
            inner[0] = run((c, a) -> ArgumentChanges.put(0, "inner"), 1);
            ArgumentChanges.put(1, "after");
        }, 2);
        assertEquals("before", outer[0]);
        assertEquals("after", outer[1]);
        assertEquals("inner", inner[0][0]);
        // Nothing's kept for the thread once it's run.
        ArgumentChanges.put(0, "stray");
        assertSame(ArgumentChanges.NONE, run((context, arguments) -> {}, 1)[0]);
    }

    @Test
    void changesBeforeAThrowAreKeptAndItIsThrownAfter() {
        IllegalStateException boom = new IllegalStateException("boom");
        Object[] changes = run((context, arguments) -> {
            ArgumentChanges.put(0, "set");
            throw boom;
        }, 2);
        assertEquals("set", changes[0]);
        assertSame(ArgumentChanges.NONE, changes[1]);
        assertSame(boom, assertThrows(IllegalStateException.class, () -> ArgumentChanges.rethrow(changes)));
        ArgumentChanges.rethrow(run((context, arguments) -> {}, 0));
    }
}
