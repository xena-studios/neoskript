/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;

/**
 * Skript 2.17's type properties ({@code org.skriptlang.skript.lang.properties}): which types have each, as its
 * types declare them (and the types its {@code AnyNamed} and {@code AnyAmount} take, by what they implement or
 * convert from), and which syntax is a property's. A property of values whose type can't have it doesn't parse,
 * with Skript's error: "The expression '5' returns the following types that do not have the name property:
 * integer".
 */
final class TypeProperties {

    private TypeProperties() {}

    /** The types Skript's {@code AnyAmount} takes: its numbered things. */
    private static final String[] NUMBERED = {"numbered", "queue", "slot", "itemtype", "itemstack"};

    static void register(Registrar registrar) {
        registrar.typeProperty("name", "named", "script", "inventory", "entity", "offlineplayer", "slot", "block",
                "nameable", "player", "itemtype", "bossbar", "commandsender", "config", "node", "world", "gamerule",
                "function");
        registrar.typeProperty("display name", "inventory", "entity", "slot", "nameable", "player", "itemtype");
        registrar.typeProperty("amount", NUMBERED);
        registrar.typeProperty("size", NUMBERED);
        registrar.typeProperty("number", NUMBERED);
        registrar.typeProperty("empty", "numbered", "queue", "slot", "itemtype", "itemstack", "inventory", "string");
        registrar.typeProperty("scale", "display", "scalableparticle");
        registrar.typeProperty("title", "bossbar", "itemtype");
        registrar.typeProperty("progress", "bossbar");
        registrar.typeProperty("style", "bossbar");
        registrar.typeProperty("viewers", "bossbar", "inventory");
        // The WXYZ property; the syntax is each axis's, by its letter, as Skript's errors name it, for the types
        // whose handlers support the axis. (A one-letter property is an axis: the parser has its own error.)
        registrar.typeProperty("wxyz component", "quaternion", "chunk", "location", "vector");
        registrar.typeProperty("W", "quaternion");
        registrar.typeProperty("X", "quaternion", "chunk", "location", "vector");
        registrar.typeProperty("Y", "quaternion", "location", "vector");
        registrar.typeProperty("Z", "quaternion", "chunk", "location", "vector");

        registrar.propertySyntax("PropExprName", 0, tags -> "name", false);
        registrar.propertySyntax("PropExprName-display", 0, tags -> "display name", false);
        registrar.propertySyntax("ExprAmount", 0, tags -> tags.contains("size") ? "size"
                : tags.contains("number") ? "number" : "amount", true);
        registrar.propertySyntax("CondIsEmpty", 0, tags -> "empty", false);
        registrar.propertySyntax("PropExprScale", 0, tags -> "scale", false);
        registrar.propertySyntax("PropExprTitle", 0, tags -> "title", false);
        registrar.propertySyntax("PropExprProgress", 0, tags -> "progress", false);
        registrar.propertySyntax("PropExprStyle", 0, tags -> "style", false);
        registrar.propertySyntax("PropExprViewers", 0, tags -> "viewers", false);
        registrar.propertySyntax("PropExprWXYZ", 0, tags -> tags.contains("w") ? "W" : tags.contains("x") ? "X"
                : tags.contains("y") ? "Y" : "Z", false);
    }
}
