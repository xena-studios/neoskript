/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import co.xenastudios.neoskript.api.element.TriggerContext;
import org.junit.jupiter.api.Test;

class SimpleTriggerContextTest {

    @Test
    void aFunctionsOutermostRunIsTheRunThatCalledIt() {
        TriggerContext run = SimpleTriggerContext.run(new SimpleTriggerContext("event", "owner"));
        TriggerContext call = SimpleTriggerContext.call(run);
        TriggerContext nested = SimpleTriggerContext.call(call);
        assertSame(run, run.outermostRun());
        assertSame(run, call.outermostRun());
        assertSame(run, nested.outermostRun(), "however many functions deep");
        // Another run of the same event is another run.
        assertNotSame(run, SimpleTriggerContext.run(new SimpleTriggerContext("event", "owner")).outermostRun());
    }

    @Test
    void aCallFromNowhereIsARunOfItsOwn() {
        // Not linked to the one context every call from nowhere shares, so nothing kept by run outlives the call.
        TriggerContext call = SimpleTriggerContext.call(SimpleTriggerContext.NOWHERE);
        assertSame(call, call.outermostRun());
        assertNotSame(SimpleTriggerContext.NOWHERE, call.outermostRun());
    }
}
