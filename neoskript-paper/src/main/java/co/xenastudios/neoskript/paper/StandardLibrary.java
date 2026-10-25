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
        BossBars.registerTypes(registrar);
        Colors.register(registrar);
        Time.register(registrar);
        Locations.register(registrar);
        WorldSyntax.registerEntityTypes(registrar);
        Items.register(registrar);
        WorldSyntax.registerCountedEntityTypes(registrar);
        Enchantments.register(registrar);
        Tags.register(registrar);
        Books.register(registrar);
        EntityProperties.registerTypes(registrar);
        WorldSyntax.register(registrar);
        BlockStates.register(registrar);
        Experience.register(registrar);
        Borders.register(registrar);
        Potions.register(registrar);
        PersistentData.register(registrar);
        EntitySyntax.register(registrar);
        EntityProperties.register(registrar);
        EntityEventSyntax.register(registrar);
        Displays.register(registrar);
        Quaternions.register(registrar);
        BlockSyntax.register(registrar);
        Biomes.register(registrar);
        LootTables.register(registrar);
        BossBars.register(registrar);
        ServerEvents.registerLate(registrar);
        BlockProperties.register(registrar);
        Scripts.register(registrar);
        WorldProperties.register(registrar);
        ServerSyntax.register(registrar);
        PlayerSyntax.register(registrar);
        EventTypes.register(registrar);
        BlockEvents.register(registrar);
        EntityEvents.register(registrar);
        PlayerEvents.register(registrar);
        ItemEvents.register(registrar);
        WorldEvents.register(registrar);
        EventExpressions.register(registrar);
        Collections.register(registrar);
        Logic.register(registrar);
        MathFunctions.register(registrar);
        Items.registerLate(registrar);
        ItemSyntax.register(registrar);
        Banners.register(registrar);
        Configs.register(registrar);
        WorldSyntax.registerLate(registrar);
        WorldProperties.registerLate(registrar);
        StorageCodecs.register(registrar);
        ItemCodecs.register(registrar);
    }

    /** Syntax Skript's test environment provides, for running Skript's own tests: {@code test-location}... */
    public static void registerTesting(Registrar registrar) {
        WorldSyntax.registerTesting(registrar);
        Scripts.registerTesting(registrar);
    }
}
