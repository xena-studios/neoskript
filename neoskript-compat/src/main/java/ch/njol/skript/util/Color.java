/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.util;

import org.bukkit.DyeColor;

/**
 * Skript's colour, for addons: one of the sixteen named ones ({@link SkriptColor}) or any other ({@link ColorRGB}).
 * Scripts get NeoSkript's colour of the same red, green, blue and alpha.
 */
public interface Color {

    org.bukkit.Color asBukkitColor();

    int getAlpha();

    int getRed();

    int getGreen();

    int getBlue();

    DyeColor asDyeColor();

    String getName();

    default int asARGB() {
        return asBukkitColor().asARGB();
    }

    default String toHexString() {
        return String.format("%02X%02X%02X", getRed(), getGreen(), getBlue());
    }

    /** NeoSkript's colour as Skript's: not one of Skript's methods. */
    static Color of(Object neoskript) {
        String named = co.xenastudios.neoskript.paper.CompatBridge.colourName(neoskript);
        if (named != null) {
            return SkriptColor.valueOf(named);
        }
        int[] rgba = co.xenastudios.neoskript.paper.CompatBridge.colourRgba(neoskript);
        return ColorRGB.fromRGBA(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    /** This as NeoSkript's colour: not one of Skript's methods. */
    static Object neoskript(Color color) {
        if (color instanceof SkriptColor named) {
            return co.xenastudios.neoskript.paper.CompatBridge.namedColour(named.name());
        }
        return co.xenastudios.neoskript.paper.CompatBridge.rgbColour(color.getRed(), color.getGreen(), color.getBlue(),
                color.getAlpha());
    }
}
