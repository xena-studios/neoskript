/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.registration;

import co.xenastudios.neoskript.api.annotation.Experimental;

/** Reads the syntax a script declares with one of an addon's structures: see {@link Registrar#scriptSyntax}. */
@Experimental
public interface ScriptSyntaxHandler {

    /**
     * What one use of the structure declares, read before any script's code is parsed, so every script can use it.
     * Called once per use on each load, on any thread. A structure that declares nothing usable reports why with
     * {@link ScriptStructure#error} and returns {@code null}.
     *
     * @param structure the structure as written
     * @return what it declares, or {@code null}
     */
    ScriptSyntax declare(ScriptStructure structure);
}
