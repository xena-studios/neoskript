/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ScriptEvent;

/** Events NeoSkript fires itself. */
final class ScriptEvents {

    private ScriptEvents() {}

    static void register(Registrar registrar) {
        registrar.event("ScriptLoad", ScriptEvent.LOAD, "[async] [script] (load|init|enable)");
    }
}
