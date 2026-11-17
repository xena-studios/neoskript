/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.bukkitutil;

import ch.njol.skript.Skript;
import java.util.Map;
import org.bukkit.Registry;
import org.bukkit.inventory.EquipmentSlot;

/** Skript's Bukkit helpers that addons use. */
public class BukkitUtils {

    private static final Map<EquipmentSlot, Integer> SLOTS = Map.of(EquipmentSlot.FEET, 36, EquipmentSlot.LEGS, 37,
            EquipmentSlot.CHEST, 38, EquipmentSlot.HEAD, 39, EquipmentSlot.OFF_HAND, 40);

    public BukkitUtils() {}

    public static boolean registryExists(String registry) {
        return Skript.fieldExists(Registry.class, registry);
    }

    public static Integer getEquipmentSlotIndex(EquipmentSlot slot) {
        return SLOTS.get(slot);
    }

    public static EquipmentSlot getEquipmentSlotFromIndex(int index) {
        for (Map.Entry<EquipmentSlot, Integer> entry : SLOTS.entrySet()) {
            if (entry.getValue() == index) {
                return entry.getKey();
            }
        }
        return null;
    }
}
