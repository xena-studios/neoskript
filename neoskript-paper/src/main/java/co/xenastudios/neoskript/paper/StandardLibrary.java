/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;

/** Registers NeoSkript's standard library. It grows milestone by milestone, most-used syntax first. */
public final class StandardLibrary {

    private StandardLibrary() {}

    public static void register(Registrar registrar) {
        CoreTypes.register(registrar);
        ScriptEvents.register(registrar);
        Messages.register(registrar);
    }
}
