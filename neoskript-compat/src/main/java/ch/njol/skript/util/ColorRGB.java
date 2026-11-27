/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.DyeColor;

/** Any colour by its red, green, blue and alpha, for addons, as Skript's. */
public class ColorRGB implements Color {

    private static final Pattern RGB_PATTERN = Pattern.compile("(?>rgb|RGB) (\\d+), (\\d+), (\\d+)");

    private final org.bukkit.Color bukkit;
    private final DyeColor dye;

    @Deprecated
    public ColorRGB(int red, int green, int blue) {
        this(org.bukkit.Color.fromRGB(fit(red), fit(green), fit(blue)));
    }

    @Deprecated
    public ColorRGB(org.bukkit.Color bukkit) {
        this.dye = DyeColor.getByColor(bukkit);
        this.bukkit = bukkit;
    }

    private static int fit(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static ColorRGB fromRGBA(int red, int green, int blue, int alpha) {
        return new ColorRGB(org.bukkit.Color.fromARGB(alpha, red, green, blue));
    }

    public static ColorRGB fromRGB(int red, int green, int blue) {
        return new ColorRGB(red, green, blue);
    }

    public static ColorRGB fromBukkitColor(org.bukkit.Color bukkit) {
        return new ColorRGB(bukkit);
    }

    @Override
    public int getAlpha() {
        return bukkit.getAlpha();
    }

    @Override
    public int getRed() {
        return bukkit.getRed();
    }

    @Override
    public int getGreen() {
        return bukkit.getGreen();
    }

    @Override
    public int getBlue() {
        return bukkit.getBlue();
    }

    @Override
    public org.bukkit.Color asBukkitColor() {
        return bukkit;
    }

    @Override
    public DyeColor asDyeColor() {
        return dye;
    }

    @Override
    public String getName() {
        String rgb = bukkit.getRed() + ", " + bukkit.getGreen() + ", " + bukkit.getBlue();
        if (bukkit.getAlpha() != 255) {
            return "argb " + bukkit.getAlpha() + ", " + rgb;
        }
        return "rgb " + rgb;
    }

    public static ColorRGB fromString(String string) {
        Matcher matcher = RGB_PATTERN.matcher(string);
        if (!matcher.matches()) {
            return null;
        }
        return new ColorRGB(toInt(matcher.group(1)), toInt(matcher.group(2)), toInt(matcher.group(3)));
    }

    private static int toInt(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static ColorRGB fromHexString(String hex) {
        if (hex.length() != 6 && hex.length() != 8) {
            return null;
        }
        if (hex.length() == 6) {
            hex = "FF" + hex;
        }
        try {
            int argb = Integer.parseUnsignedInt(hex, 16);
            return fromRGBA(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >> 24 & 255);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ColorRGB color && bukkit.equals(color.bukkit);
    }

    @Override
    public int hashCode() {
        return bukkit.hashCode();
    }

    @Override
    public String toString() {
        return getName();
    }
}
