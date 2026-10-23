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
        ServerEvents.register(registrar);
        Messages.register(registrar);
        Players.register(registrar);
        Commands.register(registrar);
        Texts.register(registrar);
        Time.register(registrar);
        Locations.register(registrar);
        Items.register(registrar);
        WorldSyntax.register(registrar);
        Borders.register(registrar);
        Potions.register(registrar);
        PersistentData.register(registrar);
        Logic.register(registrar);
        MathFunctions.register(registrar);
        StorageCodecs.register(registrar);
        ItemCodecs.register(registrar);
    }

    /** Syntax Skript's test environment provides, for running Skript's own tests: {@code test-location}... */
    public static void registerTesting(Registrar registrar) {
        WorldSyntax.registerTesting(registrar);
    }
}
