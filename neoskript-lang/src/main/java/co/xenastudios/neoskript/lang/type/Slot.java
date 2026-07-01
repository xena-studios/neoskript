package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.core.runtime.Renderer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * A live reference to one slot of an {@link Inventory} (its index). Reading yields the current
 * {@link ItemStack} at that index; writing replaces it. Mirrors Skript's {@code slot} type, which
 * behaves as its contents in value contexts and is settable in place.
 */
public final class Slot {

    private final Inventory inventory;
    private final int index;

    public Slot(Inventory inventory, int index) {
        this.inventory = inventory;
        this.index = index;
    }

    /** @return the slot's index within its inventory */
    public int index() {
        return index;
    }

    /** @return the item currently in the slot, or {@code null} if the index is out of range/empty */
    public ItemStack getItem() {
        if (index < 0 || index >= inventory.getSize()) {
            return null;
        }
        return inventory.getItem(index);
    }

    /** Places {@code item} in the slot ({@code null} clears it), ignoring out-of-range indices. */
    public void setItem(ItemStack item) {
        if (index >= 0 && index < inventory.getSize()) {
            inventory.setItem(index, item);
        }
    }

    @Override
    public String toString() {
        ItemStack item = getItem();
        return item == null || item.getType() == Material.AIR ? "air" : Renderer.toDisplay(item);
    }
}
