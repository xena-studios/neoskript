/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.aliases;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Skript's item type, for addons: it holds one of NeoSkript's item types, which is what scripts get. An addon's
 * item type given to a script is NeoSkript's, and NeoSkript's given to an addon is this.
 */
public class ItemType {

    private final co.xenastudios.neoskript.paper.ItemType value;

    private ItemType(co.xenastudios.neoskript.paper.ItemType value) {
        this.value = value;
    }

    public ItemType() {
        this(new ItemStack(Material.AIR));
    }

    public ItemType(Material material) {
        this(co.xenastudios.neoskript.paper.ItemType.ofMaterial(material));
    }

    public ItemType(Material material, int amount) {
        this(new ItemStack(material, Math.max(1, amount)));
    }

    public ItemType(ItemStack stack) {
        this(new co.xenastudios.neoskript.paper.ItemType(stack == null ? new ItemStack(Material.AIR) : stack, false));
    }

    public ItemType(BlockData data) {
        this(data.getMaterial());
    }

    public ItemType(Block block) {
        this(block.getType());
    }

    public ItemType(ItemType other) {
        this(other.value.copy());
    }

    /** NeoSkript's item type as Skript's: not one of Skript's methods. */
    public static ItemType of(co.xenastudios.neoskript.paper.ItemType value) {
        return new ItemType(value);
    }

    /** The NeoSkript item type this holds: not one of Skript's methods. */
    public co.xenastudios.neoskript.paper.ItemType neoskript() {
        return value;
    }

    public Material getMaterial() {
        return value.material();
    }

    public ItemStack getRandom() {
        return value.stack().clone();
    }

    public ItemType getItem() {
        return this;
    }

    public ItemMeta getItemMeta() {
        return value.meta();
    }

    public void setItemMeta(ItemMeta meta) {
        value.setMeta(meta);
    }

    public int getAmount() {
        return value.amount();
    }

    public void setAmount(int amount) {
        value.setAmount(amount);
    }

    public Iterable<ItemStack> getAll() {
        List<ItemStack> all = new ArrayList<>();
        for (co.xenastudios.neoskript.paper.ItemType each : value.alternatives()) {
            all.add(each.stack().clone());
        }
        return all;
    }

    public boolean isOfType(Block block) {
        return block != null && block.getType() == getMaterial();
    }

    public boolean isOfType(ItemStack item) {
        return item != null && item.getType() == getMaterial();
    }

    public boolean isOfType(Material material) {
        return material == getMaterial();
    }

    public boolean isSupertypeOf(ItemType other) {
        return other != null && other.getMaterial() == getMaterial();
    }

    public boolean addTo(Inventory inventory) {
        return inventory.addItem(getRandom()).isEmpty();
    }

    public boolean removeFrom(Inventory inventory) {
        return inventory.removeItem(getRandom()).isEmpty();
    }

    public boolean setBlock(Block block, boolean applyPhysics) {
        if (!getMaterial().isBlock()) {
            return false;
        }
        block.setType(getMaterial(), applyPhysics);
        return true;
    }

    public ItemType clone() {
        return new ItemType(value.copy());
    }

    @Override
    public String toString() {
        return value.toString();
    }

    public String toString(int flags) {
        return value.toString();
    }

    public static String toString(ItemStack stack) {
        return new ItemType(stack).toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemType item && Objects.equals(value, item.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
