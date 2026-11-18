/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * NeoSkript's own values that NeoSkriptCompat gives Skript addons as Skript's classes (D24): enchantment types and
 * slots. For NeoSkriptCompat only; not part of the addon API.
 */
public final class CompatBridge {

    private CompatBridge() {}

    /** NeoSkript's enchantment type: {@code sharpness 5}, or with level -1, any level of it. */
    public static Object enchantmentType(Enchantment enchantment, int level) {
        return new Enchantments.Levelled(enchantment, level);
    }

    public static boolean isEnchantmentType(Object value) {
        return value instanceof Enchantments.Levelled;
    }

    public static Enchantment enchantment(Object enchantmentType) {
        return ((Enchantments.Levelled) enchantmentType).enchantment();
    }

    /** The level, or -1 for any. */
    public static int level(Object enchantmentType) {
        return ((Enchantments.Levelled) enchantmentType).level();
    }

    public static boolean isSlot(Object value) {
        return value instanceof Items.Slot;
    }

    /** NeoSkript's slot of an inventory, by index. */
    public static Object inventorySlot(org.bukkit.inventory.Inventory inventory, int index) {
        return new Items.Slot(inventory, index);
    }

    /** The inventory a slot is in, or null if it isn't one of an inventory's (an entity's armour, a cursor). */
    public static org.bukkit.inventory.Inventory slotInventory(Object slot) {
        Items.Slot s = (Items.Slot) slot;
        return s.holder() == null && s.equipment() == null && s.index() >= 0 ? s.inventory() : null;
    }

    public static int slotIndex(Object slot) {
        return ((Items.Slot) slot).index();
    }

    public static ItemStack slotItem(Object slot) {
        return ((Items.Slot) slot).item();
    }

    public static void setSlotItem(Object slot, ItemStack item) {
        ((Items.Slot) slot).set(item);
    }

    /** The class of NeoSkript's slots, and of its enchantment types. */
    public static Class<?> slotClass() {
        return Items.Slot.class;
    }

    public static Class<?> enchantmentTypeClass() {
        return Enchantments.Levelled.class;
    }

    /** NeoSkript's named colour with this enum name ({@code LIGHT_GREY}) or script name ({@code silver}), or null. */
    public static Object namedColour(String name) {
        for (Colors.Named colour : Colors.Named.values()) {
            if (colour.name().equals(name)) {
                return colour;
            }
            for (String written : colour.names) {
                if (written.equalsIgnoreCase(name)) {
                    return colour;
                }
            }
        }
        return null;
    }

    /** The enum name of a named colour ({@code LIGHT_GREY}), or null for another colour. */
    public static String colourName(Object colour) {
        return colour instanceof Colors.Named named ? named.name() : null;
    }

    /** A colour's red, green, blue and alpha. */
    public static int[] colourRgba(Object colour) {
        Colors.Colour c = (Colors.Colour) colour;
        return new int[] {c.red(), c.green(), c.blue(), c.alpha()};
    }

    /** NeoSkript's colour of this red, green, blue and alpha, not a named one. */
    public static Object rgbColour(int red, int green, int blue, int alpha) {
        return new Colors.Rgb(red, green, blue, alpha);
    }

    /** The class of NeoSkript's colours, of its named colours and of its others. */
    public static Class<?> colourClass() {
        return Colors.Colour.class;
    }

    public static Class<?> namedColourClass() {
        return Colors.Named.class;
    }

    public static Class<?> rgbColourClass() {
        return Colors.Rgb.class;
    }
}
