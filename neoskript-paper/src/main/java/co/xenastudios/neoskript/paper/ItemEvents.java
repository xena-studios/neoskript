/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.ElementFactory;
import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ComplexRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;

/**
 * Item events: dispensing, spawning, dropping, crafting, picking up, eating, despawning, merging, moving between
 * inventories, stonecutting, enchanting, anvils and dragging in inventories, with the values triggers get.
 */
final class ItemEvents {

    private ItemEvents() {}

    static void register(Registrar registrar) {
        String of = " [[of] %-itemtypes%]";
        registrar.event("EvtItem", List.of(BlockDispenseEvent.class), itemFilter(), "dispens(e|ing)" + of);
        registrar.event("EvtItemSpawn", List.of(ItemSpawnEvent.class), itemFilter(), "item spawn[ing]" + of);
        registrar.event("EvtItemDrop", List.of(PlayerDropItemEvent.class, EntityDropItemEvent.class), info -> {
            boolean entity = Events.mark(info) == 1;
            return Events.narrowed(List.of(entity ? EntityDropItemEvent.class : PlayerDropItemEvent.class),
                    itemFilter().create(info));
        }, "[player|1:entity] drop[ping]" + of);
        registrar.event("EvtPrepareCraft", List.of(PrepareItemCraftEvent.class), itemFilter(),
                "[player] (preparing|beginning) craft[ing]" + of);
        registrar.event("EvtCraft", List.of(CraftItemEvent.class), itemFilter(), "[player] craft[ing]" + of);
        registrar.event("EvtPickUp", List.of(PlayerPickupItemEvent.class, EntityPickupItemEvent.class), info -> {
            boolean entity = Events.mark(info) == 1;
            return Events.narrowed(List.of(entity ? EntityPickupItemEvent.class : PlayerPickupItemEvent.class),
                    itemFilter().create(info));
        }, "[(player|1¦entity)] (pick[ ]up|picking up)" + of);
        registrar.event("EvtConsume", List.of(PlayerItemConsumeEvent.class), itemFilter(),
                "[player] ((eat|drink)[ing]|consum(e|ing))" + of);
        registrar.event("EvtItemDespawn", List.of(ItemDespawnEvent.class), itemFilter(),
                "(item[ ][stack]|[item] %-itemtypes%) despawn[ing]", "[item[ ][stack]] despawn[ing]" + of);
        registrar.event("EvtItemMerge", List.of(ItemMergeEvent.class), itemFilter(),
                "(item[ ][stack]|[item] %-itemtypes%) merg(e|ing)", "item[ ][stack] merg(e|ing)" + of);
        registrar.event("EvtInventoryItemMove", List.of(InventoryMoveItemEvent.class), null,
                "inventory item (move|transport)", "inventory (mov(e|ing)|transport[ing]) [an] item");
        registrar.event("EvtStonecutting", List.of(io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent.class),
                itemFilter(), "stonecutting" + of);

        Events.simple(registrar, "EvtEnchantPrepare", PrepareItemEnchantEvent.class, "[item] enchant prepare");
        Events.simple(registrar, "EvtEnchant", EnchantItemEvent.class, "[item] enchant");
        Events.simple(registrar, "EvtInventoryPickup", InventoryPickupItemEvent.class, "inventory pick[ ]up");
        Events.simple(registrar, "EvtAnvilPrepare", PrepareAnvilEvent.class, "anvil prepar(e|ing)");
        Events.simple(registrar, "EvtAnvilDamage", com.destroystokyo.paper.event.block.AnvilDamagedEvent.class,
                "anvil damag(e|ing)");
        Events.simple(registrar, "EvtInventoryDrag", InventoryDragEvent.class, "inventory drag[ging]");
        Events.simple(registrar, "EvtLootGenerate", LootGenerateEvent.class, "loot generat(e|ing)");

        registerValues(registrar);
    }

    /** EvtItem's filter: the item the event is about is of one of the item types written, if any. */
    private static ElementFactory<Condition> itemFilter() {
        return info -> (context, args) -> {
            Object[] types = Events.values(args.length == 0 ? null : args[0]);
            if (types == null) {
                return true;
            }
            ItemStack item = switch (context.event()) {
                case BlockDispenseEvent dispense -> dispense.getItem();
                case ItemSpawnEvent spawn -> spawn.getEntity().getItemStack();
                case PlayerDropItemEvent drop -> drop.getItemDrop().getItemStack();
                case EntityDropItemEvent drop -> drop.getItemDrop().getItemStack();
                case CraftItemEvent craft -> craft.getRecipe() instanceof ComplexRecipe ? craft.getCurrentItem()
                        : craft.getRecipe().getResult();
                case PrepareItemCraftEvent craft -> craft.getRecipe() == null ? null : craft.getRecipe().getResult();
                case io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent select ->
                        select.getStonecuttingRecipe().getResult();
                case EntityPickupItemEvent pickup -> pickup.getItem().getItemStack();
                case PlayerPickupItemEvent pickup -> pickup.getItem().getItemStack();
                case PlayerItemConsumeEvent consume -> consume.getItem();
                case InventoryClickEvent click -> click.getCurrentItem();
                case ItemDespawnEvent despawn -> despawn.getEntity().getItemStack();
                case ItemMergeEvent merge -> merge.getTarget().getItemStack();
                case InventoryMoveItemEvent move -> move.getItem();
                default -> null;
            };
            return item != null && Events.check(info, 0, types, type -> Events.isItem(type, item));
        };
    }

    private static void registerValues(Registrar registrar) {
        registrar.eventValue(PlayerDropItemEvent.class, "player", PlayerDropItemEvent::getPlayer);
        registrar.eventValue(PlayerDropItemEvent.class, "entity", Item.class, 0, PlayerDropItemEvent::getItemDrop);
        registrar.eventValue(PlayerDropItemEvent.class, "itemstack", event -> event.getItemDrop().getItemStack());
        registrar.eventValue(PlayerDropItemEvent.class, "entity", PlayerDropItemEvent::getPlayer);
        registrar.eventValue(EntityDropItemEvent.class, "entity", Item.class, 0, EntityDropItemEvent::getItemDrop);
        registrar.eventValue(EntityDropItemEvent.class, "itemstack", event -> event.getItemDrop().getItemStack());
        registrar.eventValue(PlayerPickupItemEvent.class, "player", PlayerPickupItemEvent::getPlayer);
        registrar.eventValue(PlayerPickupItemEvent.class, "entity", Item.class, 0, PlayerPickupItemEvent::getItem);
        registrar.eventValue(PlayerPickupItemEvent.class, "itemstack", event -> event.getItem().getItemStack());
        registrar.eventValue(PlayerPickupItemEvent.class, "itemtype", event -> new ItemType(event.getItem().getItemStack(), true));
        registrar.eventValue(PlayerPickupItemEvent.class, "entity", PlayerPickupItemEvent::getPlayer);
        registrar.eventValue(PlayerPickupItemEvent.class, "location", event -> event.getItem().getLocation());
        registrar.eventValue(EntityPickupItemEvent.class, "entity", EntityPickupItemEvent::getEntity);
        registrar.eventValue(EntityPickupItemEvent.class, "entity", Item.class, 0, EntityPickupItemEvent::getItem);
        registrar.eventValue(EntityPickupItemEvent.class, "itemtype", event -> new ItemType(event.getItem().getItemStack(), true));
        registrar.eventValue(EntityPickupItemEvent.class, "location", event -> event.getItem().getLocation());
        registrar.eventValue(PlayerItemConsumeEvent.class, "itemstack", PlayerItemConsumeEvent::getItem);

        registrar.eventValue(InventoryClickEvent.class, "player", event -> player(event.getWhoClicked()));
        registrar.eventValue(InventoryClickEvent.class, "world", event -> event.getWhoClicked().getWorld());
        registrar.eventValue(InventoryClickEvent.class, "inventoryaction", InventoryClickEvent::getAction);
        registrar.eventValue(InventoryClickEvent.class, "clicktype", InventoryClickEvent::getClick);
        registrar.eventValue(InventoryClickEvent.class, "inventory", InventoryClickEvent::getClickedInventory);
        registrar.eventValue(InventoryDragEvent.class, "player", event -> player(event.getWhoClicked()));
        registrar.eventValue(InventoryDragEvent.class, "world", event -> event.getWhoClicked().getWorld());
        registrar.eventValue(InventoryDragEvent.class, "itemstack", -1, InventoryDragEvent::getOldCursor);
        registrar.eventValue(InventoryDragEvent.class, "itemstack", InventoryDragEvent::getCursor);
        registrar.eventValue(InventoryDragEvent.class, "itemstack", ItemStack[].class, 0,
                event -> event.getNewItems().values().toArray(new ItemStack[0]));
        registrar.eventValue(InventoryDragEvent.class, "slot", Items.Slot[].class, 0, ItemEvents::dragSlots);
        registrar.eventValue(InventoryDragEvent.class, "clicktype", event -> event.getType() == DragType.EVEN
                ? ClickType.LEFT : ClickType.RIGHT);
        registrar.eventValue(InventoryDragEvent.class, "inventory", Inventory[].class, 0, event -> {
            Set<Inventory> inventories = new LinkedHashSet<>();
            for (int raw : event.getRawSlots()) {
                Inventory inventory = event.getView().getInventory(raw);
                if (inventory != null) {
                    inventories.add(inventory);
                }
            }
            return inventories.toArray(new Inventory[0]);
        });
        registrar.eventValue(InventoryCloseEvent.class, "inventoryclosereason", InventoryCloseEvent::getReason);
        registrar.eventValue(InventoryPickupItemEvent.class, "inventory", InventoryPickupItemEvent::getInventory);
        registrar.eventValue(InventoryPickupItemEvent.class, "entity", Item.class, 0, InventoryPickupItemEvent::getItem);
        registrar.eventValue(InventoryPickupItemEvent.class, "itemstack", event -> event.getItem().getItemStack());
        registrar.eventValue(PrepareAnvilEvent.class, "itemstack", PrepareAnvilEvent::getResult);

        registrar.eventValue(PrepareItemCraftEvent.class, "slot", event -> new Items.Slot(event.getInventory(), 0));
        registrar.eventValue(PrepareItemCraftEvent.class, "itemstack", event -> {
            ItemStack item = event.getInventory().getResult();
            return item != null ? item : new ItemStack(Material.AIR);
        });
        registrar.eventValue(PrepareItemCraftEvent.class, "player", event -> {
            List<HumanEntity> viewers = event.getInventory().getViewers();
            return viewers.isEmpty() ? null : player(viewers.getFirst());
        });
        registrar.eventValue(PrepareItemCraftEvent.class, "string", event -> key(event.getRecipe()));
        registrar.eventValue(CraftItemEvent.class, "string", event -> key(event.getRecipe()));
        registrar.eventValue(CraftItemEvent.class, "itemstack", event -> event.getRecipe() instanceof ComplexRecipe
                ? event.getCurrentItem() : event.getRecipe().getResult());

        registrar.eventValue(ItemDespawnEvent.class, "itemstack", event -> event.getEntity().getItemStack());
        registrar.eventValue(ItemMergeEvent.class, "entity", 1, ItemMergeEvent::getTarget);
        registrar.eventValue(ItemMergeEvent.class, "itemstack", event -> event.getEntity().getItemStack());
        registrar.eventValue(InventoryMoveItemEvent.class, "inventory", InventoryMoveItemEvent::getSource);
        registrar.eventValue(InventoryMoveItemEvent.class, "inventory", 1, InventoryMoveItemEvent::getDestination);
        registrar.eventValue(InventoryMoveItemEvent.class, "block", event -> event.getSource().getLocation() == null
                ? null : event.getSource().getLocation().getBlock());
        registrar.eventValue(InventoryMoveItemEvent.class, "block", 1, event -> event.getDestination().getLocation() == null
                ? null : event.getDestination().getLocation().getBlock());
        registrar.eventValue(InventoryMoveItemEvent.class, "itemstack", InventoryMoveItemEvent::getItem);
        registrar.eventValue(io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent.class, "itemstack",
                event -> event.getStonecuttingRecipe().getResult());

        registrar.eventValue(PrepareItemEnchantEvent.class, "player", PrepareItemEnchantEvent::getEnchanter);
        registrar.eventValue(PrepareItemEnchantEvent.class, "itemstack", PrepareItemEnchantEvent::getItem);
        registrar.eventValue(PrepareItemEnchantEvent.class, "block", PrepareItemEnchantEvent::getEnchantBlock);
        registrar.eventValue(EnchantItemEvent.class, "player", EnchantItemEvent::getEnchanter);
        registrar.eventValue(EnchantItemEvent.class, "itemstack", EnchantItemEvent::getItem);
        registrar.eventValue(EnchantItemEvent.class, "enchantmenttype", Enchantments.Levelled[].class, 0,
                event -> event.getEnchantsToAdd().entrySet().stream()
                        .map(entry -> new Enchantments.Levelled(entry.getKey(), entry.getValue()))
                        .toArray(Enchantments.Levelled[]::new));
        registrar.eventValue(EnchantItemEvent.class, "block", EnchantItemEvent::getEnchantBlock);
        registrar.eventValue(LootGenerateEvent.class, "entity", LootGenerateEvent::getEntity);
        registrar.eventValue(LootGenerateEvent.class, "location", event -> event.getLootContext().getLocation());
    }

    private static Player player(HumanEntity entity) {
        return entity instanceof Player player ? player : null;
    }

    /** A recipe's key, as text, or {@code null}. */
    private static String key(Recipe recipe) {
        return recipe instanceof Keyed keyed ? keyed.getKey().toString() : null;
    }

    /** The slots dragged over: the equipment slots of a player's inventory as such. */
    private static Items.Slot[] dragSlots(InventoryDragEvent event) {
        List<Items.Slot> slots = new ArrayList<>();
        InventoryView view = event.getView();
        for (int raw : event.getRawSlots()) {
            Inventory inventory = view.getInventory(raw);
            if (inventory == null) {
                continue;
            }
            int slot = view.convertSlot(raw);
            if (inventory instanceof PlayerInventory && slot >= 36 && view.getBottomInventory().getHolder() instanceof Player player) {
                slots.add(PlayerEvents.slot(player, slot));
            } else {
                slots.add(new Items.Slot(inventory, slot));
            }
        }
        return slots.toArray(new Items.Slot[0]);
    }
}
