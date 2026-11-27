/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.util;

import co.xenastudios.neoskript.paper.CompatBridge;
import java.util.Objects;
import org.bukkit.enchantments.Enchantment;

/** Skript's enchantment type ({@code sharpness 5}), for addons: it holds NeoSkript's, which is what scripts get. */
public class EnchantmentType {

    private final Object value;

    private EnchantmentType(Object value) {
        this.value = value;
    }

    public EnchantmentType(Enchantment type) {
        this(type, -1);
    }

    public EnchantmentType(Enchantment type, int level) {
        this(CompatBridge.enchantmentType(type, level));
    }

    /** NeoSkript's enchantment type as Skript's: not one of Skript's methods. */
    public static EnchantmentType of(Object neoskript) {
        return new EnchantmentType(neoskript);
    }

    /** The NeoSkript enchantment type this holds: not one of Skript's methods. */
    public Object neoskript() {
        return value;
    }

    public Enchantment getType() {
        return CompatBridge.enchantment(value);
    }

    /** The level, 1 if it's any level. */
    public int getLevel() {
        int level = CompatBridge.level(value);
        return level == -1 ? 1 : level;
    }

    public int getInternalLevel() {
        return CompatBridge.level(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EnchantmentType type && Objects.equals(value, type.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
