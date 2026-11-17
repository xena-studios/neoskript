/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.registration;

import co.xenastudios.neoskript.api.annotation.Experimental;

/**
 * What an addon's structure does when scripts load: see {@link Registrar#structure}.
 */
@Experimental
public interface StructureHandler {

    /**
     * Scripts are about to load (all of them: a reload of one script loads them all): forget what the last load gave.
     * Called on the thread that loads them, before any structure is given.
     */
    void reset();

    /**
     * One of the structure's uses in a script, while the script is parsed, before the structures after it in the
     * same script. Scripts are parsed at the same time on several threads, so this may be called for different
     * scripts at once.
     *
     * @param structure the structure as written
     */
    void load(ScriptStructure structure);
}
