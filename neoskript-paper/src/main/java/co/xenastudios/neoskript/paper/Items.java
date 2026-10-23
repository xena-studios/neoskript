/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Items and inventories: item types and stacks, names and lore, slots, menus, and giving items. */
final class Items {

    private Items() {}

    /** A slot of an inventory, Skript's {@code slot}: what's in it can be read and set. */
    record Slot(Inventory inventory, int index) {

        ItemStack item() {
            return index >= 0 && index < inventory.getSize() ? inventory.getItem(index) : null;
        }
    }

    static void register(Registrar registrar) {
        registrar.type("itemtype", "item type", ItemType.class);
        registrar.type("itemstack", "item", ItemStack.class);
        registrar.alias("itemstack", "item", "items");
        registrar.type("inventory", "inventory", Inventory.class);
        registrar.type("inventoryholder", "inventory holder", InventoryHolder.class);
        registrar.type("slot", "slot", Slot.class);
        registrar.subtype("player", "inventoryholder");

        registrar.literalParser("itemtype", ItemType::parse);
        registrar.cloner(ItemType.class, ItemType::copy);
        registrar.cloner(ItemStack.class, ItemStack::clone);
        registrar.parser("itemtype", ItemType::parse);
        registrar.converter(ItemType.class, ItemStack.class, ItemType::stack);
        registrar.converter(ItemStack.class, ItemType.class, stack -> new ItemType(stack, true));
        registrar.converter(Slot.class, ItemStack.class, Slot::item);
        registrar.converter(Slot.class, ItemType.class, slot -> slot.item() == null ? null : new ItemType(slot.item(), true));
        registrar.converter(InventoryHolder.class, Inventory.class, InventoryHolder::getInventory);
        registrar.text(ItemStack.class, stack -> ItemType.text(stack, true));
        registrar.text(Slot.class, slot -> slot.item() == null ? "<none>" : ItemType.text(slot.item(), true));
        registrar.text(Inventory.class, inventory -> "inventory of " + inventory.getType().name().toLowerCase(java.util.Locale.ROOT));

        // Before naming items, so 'chest inventory with 2 rows named "x"' is one chest inventory, not a named one.
        registerInventories(registrar);

        registrar.expression("ExprNamed", "itemtype", info -> (context, args) -> args[0] instanceof ItemType item
                ? item.withMeta(meta -> meta.displayName(Messages.component(args[1]))) : null,
                "%itemtype% (named|with name[s]) %text%");
        registrar.expression("ExprItemWithLore", "itemtype", info -> (context, args) -> {
            if (!(args[0] instanceof ItemType item)) {
                return null;
            }
            List<Component> lore = new ArrayList<>();
            for (Object line : (Object[]) args[1]) {
                for (String part : Values.text(line).split("\n", -1)) {
                    lore.add(Messages.component(part));
                }
            }
            return item.withMeta(meta -> meta.lore(lore));
        }, "%itemtype% with [a|the] lore %texts%");
        registrar.expression("ExprItemAmount", "integer", info -> new ItemAmount(),
                "[the] item[[ ]stack] (amount|size|number) of %slots/itemtypes/itemstacks%",
                "%slots/itemtypes/itemstacks%'[s] item[[ ]stack] (amount|size|number)");
        registrar.condition("CondIsBlock", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> {
                Object[] items = (Object[]) args[0];
                for (Object item : items) {
                    if (!(item instanceof ItemType type) || type.material().isBlock() == negated) {
                        return false;
                    }
                }
                return items.length > 0;
            };
        }, "%itemtypes% (is|are) ([a] block|blocks)", "%itemtypes% (isn't|is not|aren't|are not) ([a] block|blocks)");
        // A block with an inventory, like a chest, is its inventory.
        registrar.converter(org.bukkit.block.Block.class, Inventory.class, block ->
                block.getState() instanceof InventoryHolder holder ? holder.getInventory() : null);

        // 'give X to player' and 'remove X from player': items go in and out of inventories.
        registrar.changer(InventoryHolder.class, Changeable.Mode.ADD, (holder, items) -> holder.getInventory()
                .addItem(stacks(items)));
        registrar.changer(Inventory.class, Changeable.Mode.ADD, (inventory, items) -> inventory.addItem(stacks(items)));
        registrar.changer(InventoryHolder.class, Changeable.Mode.REMOVE, (holder, items) -> holder.getInventory()
                .removeItem(stacks(items)));
        registrar.changer(Inventory.class, Changeable.Mode.REMOVE, (inventory, items) -> inventory.removeItem(stacks(items)));
    }

    private static void registerInventories(Registrar registrar) {
        registrar.expression("ExprChestInventory", "inventory", info -> {
            boolean nameFirst = info.pattern() == 0;
            return (context, args) -> {
                Object name = args[nameFirst ? 0 : 1];
                Object rows = args[nameFirst ? 1 : 0];
                int size = 9 * Math.max(1, Math.min(6, rows instanceof Number n ? (int) Math.round(n.doubleValue()) : 3));
                return name == null ? Bukkit.createInventory(null, size)
                        : Bukkit.createInventory(null, size, Messages.component(name));
            };
        }, "[a] [new] chest inventory (named|with name) %text% [with %number% row[s]]",
                "[a] [new] chest inventory with %number% row[s] [(named|with name) %text%]");
        registrar.expression("ExprInventorySlot", "slot", info -> new SlotsOf(),
                "[the] slot[s] %numbers% of %inventory%", "%inventory%'[s] slot[s] %numbers%");
        registrar.expression("ExprSlotIndex", "integer", info -> (context, args) -> {
            List<Object> indices = new ArrayList<>();
            for (Object slot : (Object[]) args[0]) {
                if (slot instanceof Slot s) {
                    indices.add((long) s.index());
                }
            }
            return indices.size() == 1 ? indices.getFirst() : indices.toArray();
        }, "[the] [(raw|unique)] index of %slots%", "%slots%'[s] [(raw|unique)] index");
        registrar.expression("ExprInventory", "inventory", info -> (context, args) -> {
            List<Object> inventories = new ArrayList<>();
            for (Object holder : (Object[]) args[0]) {
                if (holder instanceof InventoryHolder h) {
                    inventories.add(h.getInventory());
                } else if (holder instanceof org.bukkit.block.Block block
                        && block.getState() instanceof InventoryHolder h) {
                    inventories.add(h.getInventory());
                }
            }
            return inventories.size() == 1 ? inventories.getFirst() : inventories.toArray();
        }, "[the] inventor(y|ies) of %inventoryholders/blocks%", "%inventoryholders/blocks%'[s] inventor(y|ies)");

        registrar.effect("EffOpenInventory", info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                Object[] players = (Object[]) args[pattern <= 1 ? 0 : 1];
                for (Object player : players) {
                    if (!(player instanceof Player p)) {
                        continue;
                    }
                    if (pattern <= 1) {
                        p.closeInventory();
                    } else if (args[0] instanceof Inventory inventory) {
                        p.openInventory(inventory);
                    }
                }
            };
        }, "close %players%'[s] inventory [view]", "close [the] inventory [view] (to|of|for) %players%",
                "open %inventory% (to|for) %players%");

        registrar.event("EvtInventoryClick", List.of(InventoryClickEvent.class), info -> (context, args) -> {
            if (args[0] == null) {
                return true;
            }
            ItemStack clicked = ((InventoryClickEvent) context.event()).getCurrentItem();
            for (Object item : (Object[]) args[0]) {
                if (item instanceof ItemType type && clicked != null && clicked.getType() == type.material()) {
                    return true;
                }
            }
            return false;
        }, "[player] inventory(-| )click[ing] [[at] %itemtypes%]");
        registrar.event("EvtInventoryClose", List.of(InventoryCloseEvent.class), null, "inventory clos(ing|e[d])");
        registrar.event("EvtInventoryOpen", List.of(InventoryOpenEvent.class), null, "inventory open[ed]");
        registrar.eventValue(InventoryEvent.class, "player", event -> player(event.getView().getPlayer()));
        registrar.eventValue(InventoryEvent.class, "inventory", InventoryEvent::getInventory);
        registrar.eventValue(InventoryClickEvent.class, "slot", event -> event.getClickedInventory() == null ? null
                : new Slot(event.getClickedInventory(), event.getSlot()));
        registrar.eventValue(InventoryClickEvent.class, "itemstack", InventoryClickEvent::getCurrentItem);
    }

    private static Player player(HumanEntity entity) {
        return entity instanceof Player player ? player : null;
    }

    /** The items among {@code values}, as stacks to put somewhere. */
    static ItemStack[] stacks(Object[] values) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Object value : values) {
            switch (value) {
                case ItemType item -> stacks.add(item.stack());
                case ItemStack stack -> stacks.add(stack.clone());
                case Slot slot when slot.item() != null -> stacks.add(slot.item().clone());
                default -> {}
            }
        }
        return stacks.toArray(ItemStack[]::new);
    }

    /** {@code the item amount of %items%}, which can be set, added to and removed from. */
    private static final class ItemAmount implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> amounts = new ArrayList<>();
            for (Object value : (Object[]) arguments[0]) {
                ItemStack stack = switch (value) {
                    case ItemType item -> item.stack();
                    case ItemStack s -> s;
                    case Slot slot -> slot.item();
                    default -> null;
                };
                amounts.add(stack == null ? 0L : (long) stack.getAmount());
            }
            return amounts.size() == 1 ? amounts.getFirst() : amounts.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET || mode == Mode.ADD || mode == Mode.REMOVE ? new Class<?>[] {Number.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (delta == null || delta.length == 0 || !(delta[0] instanceof Number number)) {
                return;
            }
            int by = (int) Math.round(number.doubleValue());
            for (Object value : (Object[]) arguments[0]) {
                switch (value) {
                    case ItemType item -> item.setAmount(amount(item.amount(), by, mode));
                    case ItemStack stack -> stack.setAmount(amount(stack.getAmount(), by, mode));
                    case Slot slot when slot.item() != null -> {
                        ItemStack stack = slot.item();
                        stack.setAmount(amount(stack.getAmount(), by, mode));
                        slot.inventory().setItem(slot.index(), stack.getAmount() <= 0 ? null : stack);
                    }
                    default -> {}
                }
            }
        }

        private static int amount(int current, int by, Mode mode) {
            return Math.max(0, switch (mode) {
                case ADD -> current + by;
                case REMOVE -> current - by;
                default -> by;
            });
        }
    }

    /** {@code slot 3 of {_menu}}, which can be set to an item. */
    private static final class SlotsOf implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> slots = new ArrayList<>();
            if (arguments[1] instanceof Inventory inventory) {
                for (Object index : (Object[]) arguments[0]) {
                    if (index instanceof Number n) {
                        slots.add(new Slot(inventory, (int) Math.round(n.doubleValue())));
                    }
                }
            }
            return slots.size() == 1 ? slots.getFirst() : slots.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {ItemType.class, ItemStack.class}
                    : mode == Mode.DELETE ? new Class<?>[0] : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(arguments[1] instanceof Inventory inventory)) {
                return;
            }
            ItemStack[] items = delta == null ? new ItemStack[0] : stacks(delta);
            ItemStack item = items.length == 0 ? null : items[0];
            for (Object index : (Object[]) arguments[0]) {
                if (index instanceof Number n) {
                    int i = (int) Math.round(n.doubleValue());
                    if (i >= 0 && i < inventory.getSize()) {
                        inventory.setItem(i, item);
                    }
                }
            }
        }
    }
}
