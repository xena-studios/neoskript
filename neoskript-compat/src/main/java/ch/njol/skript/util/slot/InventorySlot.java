/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.util.slot;

import co.xenastudios.neoskript.paper.CompatBridge;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** A slot of an inventory, by index: NeoSkript's, for addons, as Skript's. */
public class InventorySlot extends SlotWithIndex {

    private final Inventory inventory;
    private final int index;
    private final int rawIndex;
    private final Object slot;

    public InventorySlot(Inventory inventory, int index, int rawIndex) {
        this(inventory, index, rawIndex, CompatBridge.inventorySlot(inventory, index));
    }

    public InventorySlot(Inventory inventory, int index) {
        this(inventory, index, index);
    }

    InventorySlot(Inventory inventory, int index, Object slot) {
        this(inventory, index, index, slot);
    }

    private InventorySlot(Inventory inventory, int index, int rawIndex, Object slot) {
        this.inventory = inventory;
        this.index = index;
        this.rawIndex = rawIndex;
        this.slot = slot;
    }

    @Override
    public Object neoskript() {
        return slot;
    }

    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public int getRawIndex() {
        return rawIndex;
    }

    @Override
    public ItemStack getItem() {
        ItemStack item = CompatBridge.slotItem(slot);
        return item == null ? new ItemStack(org.bukkit.Material.AIR) : item.clone();
    }

    @Override
    public void setItem(ItemStack item) {
        CompatBridge.setSlotItem(slot, item);
    }

    @Override
    public int getAmount() {
        ItemStack item = CompatBridge.slotItem(slot);
        return item == null ? 0 : item.getAmount();
    }

    @Override
    public void setAmount(int amount) {
        ItemStack item = CompatBridge.slotItem(slot);
        if (item != null) {
            item.setAmount(amount);
            CompatBridge.setSlotItem(slot, item);
        }
    }

    @Override
    public boolean isSameSlot(Slot other) {
        return other instanceof InventorySlot inventorySlot && inventorySlot.inventory.equals(inventory)
                && inventorySlot.index == index;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return slot.toString();
    }
}
