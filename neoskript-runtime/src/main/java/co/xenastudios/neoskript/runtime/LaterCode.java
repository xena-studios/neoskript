/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.api.element.SectionCode;

/**
 * The code under an addon's section that runs later ({@code make gui slot 0 with dirt:}): it carries on from the
 * section's first line, as after a wait, with its own event, and stops at the section's end.
 */
final class LaterCode implements SectionCode {

    private final Resumable code;
    private final Frame frame;
    private final String script;

    LaterCode(Resumable code, Frame frame, String script) {
        this.code = code;
        this.frame = frame;
        this.script = script;
    }

    @Override
    public void run(Object event, Object owner) {
        // A frame of its own each run, with the locals they share: what the code saves at a wait is that run's.
        Frame run = new Frame();
        run.state = frame.state;
        run.locals = frame.locals;
        run.parseLogs = frame.parseLogs;
        run.slots = frame.slots == null ? null : frame.slots.clone();
        try {
            code.resume(new SimpleTriggerContext(event, owner), run);
        } catch (ScriptRuntimeException e) {
            Waits.errors().accept(e);
        }
    }

    @Override
    public String script() {
        return script;
    }
}
