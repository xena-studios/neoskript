/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.util.slot;

import co.xenastudios.neoskript.paper.CompatBridge;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

/** Skript's slot: a place an item is. NeoSkript's slots are given to addons as one of these. */
public abstract class Slot implements ch.njol.skript.lang.Debuggable {

    protected Slot() {}

    /** NeoSkript's slot as Skript's: not one of Skript's methods. */
    public static Slot of(Object neoskript) {
        org.bukkit.inventory.Inventory inventory = CompatBridge.slotInventory(neoskript);
        return inventory != null ? new InventorySlot(inventory, CompatBridge.slotIndex(neoskript), neoskript)
                : new NeoSkriptSlot(neoskript);
    }

    /** The NeoSkript slot this is, or null if an addon made it: not one of Skript's methods. */
    public Object neoskript() {
        return null;
    }

    public abstract ItemStack getItem();

    public abstract void setItem(ItemStack item);

    public abstract int getAmount();

    public abstract void setAmount(int amount);

    public abstract boolean isSameSlot(Slot other);

    @Override
    public final String toString() {
        return toString(null, false);
    }

    /** One of NeoSkript's slots. */
    private static final class NeoSkriptSlot extends Slot {

        private final Object slot;

        NeoSkriptSlot(Object slot) {
            this.slot = slot;
        }

        @Override
        public Object neoskript() {
            return slot;
        }

        @Override
        public ItemStack getItem() {
            ItemStack item = CompatBridge.slotItem(slot);
            return item == null ? null : item.clone();
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
            return other != null && slot.equals(other.neoskript());
        }

        @Override
        public String toString(Event event, boolean debug) {
            return slot.toString();
        }
    }
}
