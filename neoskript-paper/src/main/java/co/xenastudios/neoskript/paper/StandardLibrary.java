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
        register(registrar, hooks -> {});
    }

    /**
     * Registers the standard library with the syntax of plugin hooks ({@link Hooks#register}), which goes before
     * comparisons and {@code contains}, as Skript tries those last: {@code player is the owner of region X} is the
     * region condition, not a comparison.
     */
    public static void register(Registrar registrar, java.util.function.Consumer<Registrar> hooks) {
        CoreTypes.register(registrar);
        TextComponents.register(registrar);
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
        ItemComponents.register(registrar);
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
        DamageSources.register(registrar);
        Displays.register(registrar);
        Quaternions.register(registrar);
        BlockSyntax.register(registrar);
        Biomes.register(registrar);
        LootTables.register(registrar);
        BossBars.register(registrar);
        Particles.register(registrar);
        ServerEvents.registerLate(registrar);
        BlockProperties.register(registrar);
        Scripts.register(registrar);
        WorldProperties.register(registrar);
        ServerSyntax.register(registrar);
        PlayerSyntax.register(registrar);
        Sounds.register(registrar);
        EventTypes.register(registrar);
        EnumTypes.register(registrar);
        BlockEvents.register(registrar);
        EntityEvents.register(registrar);
        PlayerEvents.register(registrar);
        PickItems.register(registrar);
        InputAndAttackEvents.register(registrar);
        ItemEvents.register(registrar);
        WorldEvents.register(registrar);
        EventExpressions.register(registrar);
        Workstations.register(registrar);
        Fishing.register(registrar);
        Fireworks.register(registrar);
        EventSyntax.register(registrar);
        Collections.register(registrar);
        hooks.accept(registrar);
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
        ParticleCodecs.register(registrar);
    }

    /** Syntax Skript's test environment provides, for running Skript's own tests: {@code test-location}... */
    /**
     * Whether players read as their UUIDs in variable names ({@code {money::%player%}}), as Skript's
     * {@code use player UUIDs in variable names}, which its default config turns on. Otherwise as their names.
     */
    public static void usePlayerUuidsInVariableNames(boolean use) {
        Players.uuidsInVariableNames = use;
    }

    public static void registerTesting(Registrar registrar) {
        WorldSyntax.registerTesting(registrar);
        Scripts.registerTesting(registrar);
    }
}
