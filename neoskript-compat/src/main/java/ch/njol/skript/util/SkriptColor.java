/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.util;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;

/** Skript's sixteen named colours, for addons, with the dye and chat colour of each, as Skript's. */
public enum SkriptColor implements Color {

    BLACK(DyeColor.BLACK, ChatColor.BLACK),
    DARK_GREY(DyeColor.GRAY, ChatColor.DARK_GRAY),
    LIGHT_GREY(DyeColor.LIGHT_GRAY, ChatColor.GRAY),
    WHITE(DyeColor.WHITE, ChatColor.WHITE),
    DARK_BLUE(DyeColor.BLUE, ChatColor.DARK_BLUE),
    BROWN(DyeColor.BROWN, ChatColor.BLUE),
    DARK_CYAN(DyeColor.CYAN, ChatColor.DARK_AQUA),
    LIGHT_CYAN(DyeColor.LIGHT_BLUE, ChatColor.AQUA),
    DARK_GREEN(DyeColor.GREEN, ChatColor.DARK_GREEN),
    LIGHT_GREEN(DyeColor.LIME, ChatColor.GREEN),
    YELLOW(DyeColor.YELLOW, ChatColor.YELLOW),
    ORANGE(DyeColor.ORANGE, ChatColor.GOLD),
    DARK_RED(DyeColor.RED, ChatColor.DARK_RED),
    LIGHT_RED(DyeColor.PINK, ChatColor.RED),
    DARK_PURPLE(DyeColor.PURPLE, ChatColor.DARK_PURPLE),
    LIGHT_PURPLE(DyeColor.MAGENTA, ChatColor.LIGHT_PURPLE);

    private static final Map<Character, SkriptColor> BY_CHAR = new HashMap<>();

    static {
        for (SkriptColor color : values()) {
            BY_CHAR.put(color.chat.getChar(), color);
        }
    }

    private final DyeColor dye;
    private final ChatColor chat;

    SkriptColor(DyeColor dye, ChatColor chat) {
        this.dye = dye;
        this.chat = chat;
    }

    @Override
    public org.bukkit.Color asBukkitColor() {
        return dye.getColor();
    }

    @Override
    public int getAlpha() {
        return dye.getColor().getAlpha();
    }

    @Override
    public int getRed() {
        return dye.getColor().getRed();
    }

    @Override
    public int getGreen() {
        return dye.getColor().getGreen();
    }

    @Override
    public int getBlue() {
        return dye.getColor().getBlue();
    }

    @Override
    public DyeColor asDyeColor() {
        return dye;
    }

    /** Its name in scripts, as NeoSkript's colour gives it ({@code light grey}). */
    @Override
    public String getName() {
        return co.xenastudios.neoskript.paper.CompatBridge.namedColour(name()).toString();
    }

    public String getFormattedChat() {
        return "" + chat;
    }

    public ChatColor asChatColor() {
        return chat;
    }

    @Deprecated
    public byte getWoolData() {
        return dye.getWoolData();
    }

    @Deprecated
    public byte getDyeData() {
        return (byte) (15 - dye.getWoolData());
    }

    /** The colour with this name in scripts ({@code light grey}, {@code silver}), or null. */
    public static SkriptColor fromName(String name) {
        Object colour = co.xenastudios.neoskript.paper.CompatBridge.namedColour(name);
        String named = colour == null ? null : co.xenastudios.neoskript.paper.CompatBridge.colourName(colour);
        return named == null ? null : valueOf(named);
    }

    public static SkriptColor fromDyeColor(DyeColor dye) {
        for (SkriptColor color : values()) {
            if (color.dye == dye) {
                return color;
            }
        }
        return null;
    }

    public static SkriptColor fromBukkitColor(org.bukkit.Color color) {
        for (SkriptColor c : values()) {
            if (c.asBukkitColor().equals(color) || c.dye.getFireworkColor().equals(color)) {
                return c;
            }
        }
        return null;
    }

    @Deprecated
    public static SkriptColor fromDyeData(short data) {
        if (data < 0 || data >= 16) {
            return null;
        }
        for (SkriptColor color : values()) {
            if (color.dye.getDyeData() == data) {
                return color;
            }
        }
        return null;
    }

    @Deprecated
    public static SkriptColor fromWoolData(short data) {
        if (data < 0 || data >= 16) {
            return null;
        }
        for (SkriptColor color : values()) {
            if (color.dye.getWoolData() == data) {
                return color;
            }
        }
        return null;
    }

    public static String replaceColorChar(String s) {
        return s.replace('§', '&');
    }

    public static SkriptColor fromColorChar(char character) {
        return BY_CHAR.get(character);
    }

    @Override
    public String toString() {
        return getName();
    }
}
