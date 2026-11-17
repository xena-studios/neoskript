/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.Warnings;

/**
 * Skript's refusal to change what an event does once a wait has let it pass: {@code cancel event}, {@code set damage
 * to 5} or {@code set join message to "hi"} after {@code wait 1 tick}. Each element refuses with its own error,
 * where Skript's does: in its {@code init}, or in {@code acceptChange} for a change.
 */
final class EventPassed {

    private EventPassed() {}

    /** Fails with {@code error} if the code has certainly waited by now, as Skript's {@code getHasDelayBefore().isTrue()}. */
    static void refuse(String error) {
        if (Warnings.surelyDelayed()) {
            throw new SyntaxError(error);
        }
    }

    /** Fails with {@code error} if the code may have waited by now, as Skript's {@code !isDelayed.isFalse()}. */
    static void refuseMaybe(String error) {
        if (Warnings.delayed()) {
            throw new SyntaxError(error);
        }
    }
}
