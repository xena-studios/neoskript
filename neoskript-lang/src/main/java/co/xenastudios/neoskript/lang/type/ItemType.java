package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;

/**
 * The built-in {@code item} type, backed by {@link ItemStack}. Parses a material name (e.g.
 * {@code diamond}) and renders as {@code amount material}.
 */
public final class ItemType implements Type<ItemStack> {

    @Override
    public Class<ItemStack> typeClass() {
        return ItemStack.class;
    }

    @Override
    public String codeName() {
        return "item";
    }

    @Override
    public Optional<ItemStack> parse(String input) {
        Material material = Material.matchMaterial(input.trim());
        return material == null ? Optional.empty() : Optional.of(new ItemStack(material));
    }

    @Override
    public String toDisplayString(ItemStack value) {
        String name = value.getType().name().toLowerCase(Locale.ROOT);
        return value.getAmount() + " " + name;
    }
}
