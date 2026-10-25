/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;

/**
 * The types Skript 2.17.0 can't save in variables (those with neither a serializer nor a type they're saved as),
 * for its warning where a script sets a global variable to one: "an entity cannot be saved...". The names are
 * Skript's, from its {@code default.lang}, with their articles.
 */
final class UnsavableTypes {

    private UnsavableTypes() {}

    private static final String[][] TYPES = {
        {"audience", "an audience"},
        {"bannerpattern", "a banner pattern"},
        {"bossbar", "a boss bar"},
        {"bukkitparticle", "a bukkit particle"},
        {"cachedservericon", "a server icon"},
        {"color", "a color"},
        {"commandsender", "a command sender"},
        {"config", "a config"},
        {"damagesource", "a damage source"},
        {"display", "a display"},
        {"enchantmentoffer", "an enchantment offer"},
        {"entity", "an entity"},
        {"entitysnapshot", "an entity snapshot"},
        {"equippablecomponent", "an equippable component"},
        {"fireworkeffect", "a firework effect"},
        {"function", "a function"},
        {"gamerule", "a gamerule"},
        {"inventory", "an inventory"},
        {"inventoryholder", "an inventory holder"},
        {"itemcomponent", "an item component"},
        {"itementity", "a dropped item"},
        {"livingentity", "a living entity"},
        {"lootcontext", "a loot context"},
        {"metadataholder", "a metadata holder"},
        {"minecrafttag", "a minecraft tag"},
        {"money", "a money"},
        {"nameable", "a nameable thing"},
        {"node", "a node"},
        {"projectile", "a projectile"},
        {"quaternion", "a quaternion"},
        {"script", "a script"},
        {"vehicle", "a vehicle"},
        {"worldborder", "a worldborder"},
    };

    static void register(Registrar registrar) {
        for (String[] type : TYPES) {
            registrar.unsavable(type[0], type[1]);
        }
    }
}
