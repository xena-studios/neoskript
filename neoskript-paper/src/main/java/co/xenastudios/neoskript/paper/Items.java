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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Items and inventories: item types and stacks, names and lore, slots, menus, and giving items. */
final class Items {

    private Items() {}

    /**
     * Skript's {@code slot}: a slot of an inventory, or an entity's equipment slot (its helmet...). What's in
     * it can be read and set.
     */
    record Slot(Inventory inventory, int index, LivingEntity entity, EquipmentSlot equipment) {

        Slot(Inventory inventory, int index) {
            this(inventory, index, null, null);
        }

        static Slot of(LivingEntity entity, EquipmentSlot equipment) {
            return new Slot(null, -1, entity, equipment);
        }

        ItemStack item() {
            if (equipment != null) {
                EntityEquipment gear = entity.getEquipment();
                return gear == null ? null : gear.getItem(equipment);
            }
            return index >= 0 && index < inventory.getSize() ? inventory.getItem(index) : null;
        }

        void set(ItemStack item) {
            if (equipment != null) {
                EntityEquipment gear = entity.getEquipment();
                if (gear != null) {
                    gear.setItem(equipment, item);
                }
            } else if (index >= 0 && index < inventory.getSize()) {
                inventory.setItem(index, item);
            }
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
        // 'X is stone' holds for any stone, named or not; 'X is stone named "a"' only for one with that name.
        registrar.comparison(ItemType.class, ItemType.class, (item, pattern) -> item.material() == pattern.material()
                && (!pattern.stack().hasItemMeta() || item.stack().isSimilar(pattern.stack()))
                && (!pattern.amountGiven() || item.amount() == pattern.amount()));
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

        registerMore(registrar);

        // 'give X to player' and 'remove X from player': items go in and out of inventories.
        registrar.changer(InventoryHolder.class, Changeable.Mode.ADD, (holder, items) -> holder.getInventory()
                .addItem(stacks(items)));
        registrar.changer(Inventory.class, Changeable.Mode.ADD, (inventory, items) -> inventory.addItem(stacks(items)));
        registrar.changer(InventoryHolder.class, Changeable.Mode.REMOVE, (holder, items) -> holder.getInventory()
                .removeItem(stacks(items)));
        registrar.changer(Inventory.class, Changeable.Mode.REMOVE, (inventory, items) -> inventory.removeItem(stacks(items)));
    }

    private static void registerMore(Registrar registrar) {
        Map<String, EquipmentSlot> slots = new LinkedHashMap<>();
        slots.put("helmet", EquipmentSlot.HEAD);
        slots.put("head", EquipmentSlot.HEAD);
        slots.put("chestplate", EquipmentSlot.CHEST);
        // Not 'chest', which Skript reads as the chest item where both would do: 'set block at X to chest'.
        slots.put("leggings", EquipmentSlot.LEGS);
        slots.put("legs", EquipmentSlot.LEGS);
        slots.put("boots", EquipmentSlot.FEET);
        slots.put("shoes", EquipmentSlot.FEET);
        slots.put("feet", EquipmentSlot.FEET);
        slots.put("tool", EquipmentSlot.HAND);
        slots.put("main hand", EquipmentSlot.HAND);
        slots.put("off hand", EquipmentSlot.OFF_HAND);
        slots.put("offhand", EquipmentSlot.OFF_HAND);
        slots.put("body", EquipmentSlot.BODY);
        registrar.namedType("equipmentslot", "equipment slot", EquipmentSlot.class, slots);
        registrar.text(EquipmentSlot.class, slot -> slot.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
        registrar.expression("ExprArmorSlot", "slot", info -> {
            boolean armor = info.hasTag("armor");
            return new SlotsOfEntities(armor);
        }, "[the] (%*equipmentslots%|armor:[the] armo[u]r[s]) [item[s]] of %livingentities%",
                "%livingentities%'[s] (%*equipmentslots%|armor:[the] armo[u]r[s]) [item[s]]");

        // 'set helmet of player to diamond helmet', 'delete slot 3 of {_menu}'.
        registrar.changer(Slot.class, Changeable.Mode.SET, (slot, values) -> {
            ItemStack[] stacks = stacks(values);
            slot.set(stacks.length == 0 ? null : stacks[0]);
        });
        registrar.changer(Slot.class, Changeable.Mode.DELETE, (slot, values) -> slot.set(null));


        registrar.expression("ExprLore", "string", info -> new Lore(info.pattern() >= 2),
                "[the] lore of %itemtype%", "%itemtype%'[s] lore",
                "[the] line %number% of [the] lore of %itemtype%", "[the] line %number% of %itemtype%'[s] lore",
                "[the] %number%(st|nd|rd|th) line of [the] lore of %itemtype%", "[the] %number%(st|nd|rd|th) line of %itemtype%'[s] lore");

        SettableProperty.register(registrar, "ExprDurability", "integer", "(damage[s] [value[s]]|durabilit(y|ies))",
                "itemtypes/itemstacks/slots", Object.class, Number.class, owner -> {
                    ItemStack stack = stack(owner);
                    return stack != null && stack.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damage
                            ? (long) (stack.getType().getMaxDurability() - damage.getDamage()) : null;
                }, (owner, value) -> editStack(owner, stack -> stack.editMeta(meta -> {
                    if (meta instanceof org.bukkit.inventory.meta.Damageable damage) {
                        damage.setDamage(Math.max(0, stack.getType().getMaxDurability() - (int) Math.round(value.doubleValue())));
                    }
                })));
        SettableProperty.register(registrar, "ExprMaxDurability", "integer", "max[imum] (durabilit(y|ies)|damage)",
                "itemtypes/itemstacks/slots", Object.class, Number.class, owner -> {
                    ItemStack stack = stack(owner);
                    return stack == null ? null : (long) stack.getType().getMaxDurability();
                }, (owner, value) -> {});
        SettableProperty.register(registrar, "ExprMaxStack", "integer", "max[imum] stack[[ ]size]", "itemtypes/inventories",
                Object.class, Number.class, owner -> owner instanceof Inventory inventory ? (long) inventory.getMaxStackSize()
                        : stack(owner) == null ? null : (long) stack(owner).getMaxStackSize(),
                (owner, value) -> {
                    if (owner instanceof Inventory inventory) {
                        inventory.setMaxStackSize((int) Math.round(value.doubleValue()));
                    }
                });

        registrar.condition("CondIsOfType", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> {
                Object[] values = (Object[]) args[0];
                for (Object value : values) {
                    boolean any = false;
                    for (Object type : (Object[]) args[1]) {
                        any |= switch (value) {
                            case ItemStack stack -> type instanceof ItemType item && stack.getType() == item.material();
                            case org.bukkit.entity.Entity entity -> type instanceof EntityKind kind && kind.matches(entity);
                            default -> false;
                        };
                    }
                    if (any == negated) {
                        return false;
                    }
                }
                return values.length > 0;
            };
        }, "%itemstacks/entities% (is|are) of type[s] %itemtypes/entitytypes%",
                "%itemstacks/entities% (isn't|is not|aren't|are not) of type[s] %itemtypes/entitytypes%");

        // 'player has 5 diamonds': an inventory holds the items.
        registrar.condition("CondHasItems", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> {
                Object[] inventories = (Object[]) args[0];
                for (Object value : inventories) {
                    Inventory inventory = value instanceof Inventory i ? i
                            : value instanceof InventoryHolder holder ? holder.getInventory() : null;
                    if (inventory == null) {
                        return false;
                    }
                    boolean has = true;
                    for (ItemStack stack : stacks((Object[]) args[1])) {
                        has &= inventory.containsAtLeast(stack, stack.getAmount());
                    }
                    if (has == negated) {
                        return false;
                    }
                }
                return inventories.length > 0;
            };
        }, "%inventoryholders/inventories% (has|have) %itemtypes% [in [(the[ir]|his|her|its)] inventory]",
                "%inventoryholders/inventories% (doesn't|does not|do not|don't) have %itemtypes% [in [(the[ir]|his|her|its)] inventory]");
    }

    /**
     * Whether {@code inventory} holds at least as many of {@code item} as it has, as Skript counts them: a plain
     * item ({@code stone}) counts stacks of it whatever their name; one with a name or enchantments counts only
     * stacks like it.
     */
    static boolean contains(Inventory inventory, Object item) {
        ItemStack wanted = stack(item);
        if (wanted == null) {
            return false;
        }
        boolean plain = !wanted.hasItemMeta();
        int count = 0;
        for (ItemStack held : inventory.getStorageContents()) {
            if (held != null && held.getType() == wanted.getType() && (plain || held.isSimilar(wanted))) {
                count += held.getAmount();
                if (count >= wanted.getAmount()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The item stack {@code owner} is or holds, or {@code null}. */
    private static ItemStack stack(Object owner) {
        return switch (owner) {
            case ItemType item -> item.stack();
            case ItemStack stack -> stack;
            case Slot slot -> slot.item();
            default -> null;
        };
    }

    /** Changes the item {@code owner} is or holds, in place. */
    private static void editStack(Object owner, java.util.function.Consumer<ItemStack> change) {
        switch (owner) {
            case ItemType item -> {
                ItemStack stack = item.stack();
                change.accept(stack);
                item.replace(stack);
            }
            case ItemStack stack -> change.accept(stack);
            case Slot slot when slot.item() != null -> {
                ItemStack stack = slot.item();
                change.accept(stack);
                slot.set(stack);
            }
            default -> {}
        }
    }

    /** {@code the helmet of %livingentities%}, {@code the armor of ...}: their equipment slots. */
    private record SlotsOfEntities(boolean armor) implements Expression {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> slots = new ArrayList<>();
            Object[] entities = (Object[]) arguments[1];
            for (Object entity : entities) {
                if (!(entity instanceof LivingEntity living)) {
                    continue;
                }
                if (armor) {
                    for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                        slots.add(Slot.of(living, slot));
                    }
                } else if (arguments[0] != null) {
                    for (Object slot : (Object[]) arguments[0]) {
                        if (slot instanceof EquipmentSlot equipment) {
                            slots.add(Slot.of(living, equipment));
                        }
                    }
                }
            }
            return slots.size() == 1 ? slots.getFirst() : slots.toArray();
        }
    }

    /** {@code the lore of %itemtype%} and its lines, which can be set. */
    private record Lore(boolean line) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            ItemType item = (ItemType) (line ? arguments[1] : arguments[0]);
            if (item == null || item.stack().getItemMeta() == null || item.stack().getItemMeta().lore() == null) {
                return null;
            }
            List<String> lines = new ArrayList<>();
            for (Component component : item.stack().getItemMeta().lore()) {
                lines.add(Messages.LEGACY_SECTION.serialize(component));
            }
            if (!line) {
                return String.join("\n", lines);
            }
            int n = arguments[0] instanceof Number number ? (int) Math.round(number.doubleValue()) : 0;
            return n >= 1 && n <= lines.size() ? lines.get(n - 1) : null;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET || mode == Mode.ADD || mode == Mode.DELETE ? new Class<?>[] {String.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!((line ? arguments[1] : arguments[0]) instanceof ItemType item)) {
                return;
            }
            item.editMeta(meta -> {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                String text = delta == null || delta.length == 0 ? null : Values.text(delta[0]);
                if (line) {
                    int n = arguments[0] instanceof Number number ? (int) Math.round(number.doubleValue()) : 0;
                    while (lore.size() < n) {
                        lore.add(Component.empty());
                    }
                    if (n >= 1) {
                        lore.set(n - 1, text == null ? Component.empty() : Messages.component(text));
                    }
                } else if (mode == Mode.DELETE) {
                    lore.clear();
                } else if (mode == Mode.ADD && text != null) {
                    lore.add(Messages.component(text));
                } else if (text != null) {
                    lore.clear();
                    for (String part : text.split("\n", -1)) {
                        lore.add(Messages.component(part));
                    }
                }
                meta.lore(lore.isEmpty() ? null : lore);
            });
        }
    }

    /** Bukkit's inventory types, by name, so registering them doesn't load the enum. */
    private static final List<String> INVENTORY_KINDS = List.of("CHEST", "DISPENSER", "DROPPER", "FURNACE", "WORKBENCH",
            "CRAFTING", "ENCHANTING", "BREWING", "PLAYER", "CREATIVE", "MERCHANT", "ENDER_CHEST", "ANVIL", "SMITHING",
            "BEACON", "HOPPER", "SHULKER_BOX", "BARREL", "BLAST_FURNACE", "LECTERN", "SMOKER", "LOOM", "CARTOGRAPHY",
            "GRINDSTONE", "STONECUTTER", "COMPOSTER", "CHISELED_BOOKSHELF", "JUKEBOX", "DECORATED_POT", "CRAFTER");

    /** A kind of inventory, Skript's {@code inventory type}: {@code hopper inventory}. */
    record InventoryKind(String name) {
        org.bukkit.event.inventory.InventoryType type() {
            try {
                return org.bukkit.event.inventory.InventoryType.valueOf(name);
            } catch (IllegalArgumentException missing) {
                return null;
            }
        }

        @Override
        public String toString() {
            return name.toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + " inventory";
        }
    }

    /**
     * Item syntax tried after the rest of the library: {@code 3 of stone}, which would otherwise take
     * {@code y coordinate of spawn of {_world}} as {@code (y coordinate of spawn) of {_world}}.
     */
    static void registerLate(Registrar registrar) {
        registrar.expression("ExprXOf", "object", info -> (context, args) -> {
            int amount = args[0] instanceof Number n ? (int) Math.round(n.doubleValue()) : 1;
            List<Object> values = new ArrayList<>();
            for (Object value : (Object[]) args[1]) {
                switch (value) {
                    case ItemType item -> {
                        ItemType copy = item.copy();
                        copy.setAmount(amount);
                        values.add(copy);
                    }
                    case ItemStack stack -> {
                        ItemStack copy = stack.clone();
                        copy.setAmount(amount);
                        values.add(copy);
                    }
                    default -> values.add(value);
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }, "%number% of %itemstacks/itemtypes%");
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
        // 'items in {_inventory}': each stack in it, in slot order; 'all stone in X' only those of the items given.
        registrar.expression("ExprItemsIn", "itemtype", info -> {
            boolean filtered = info.pattern() == 1;
            return (context, args) -> {
                List<Object> found = new ArrayList<>();
                Object[] wanted = filtered ? (Object[]) args[0] : null;
                for (Object value : (Object[]) args[filtered ? 1 : 0]) {
                    if (!(value instanceof Inventory inventory)) {
                        continue;
                    }
                    for (ItemStack stack : inventory.getContents()) {
                        if (stack == null || stack.getType().isAir()) {
                            continue;
                        }
                        if (wanted != null && java.util.Arrays.stream(wanted).noneMatch(item ->
                                item instanceof ItemType type && type.material() == stack.getType()
                                        && (!type.stack().hasItemMeta() || type.stack().isSimilar(stack)))) {
                            continue;
                        }
                        found.add(new ItemType(stack, true));
                    }
                }
                return found.toArray();
            };
        }, "[all [[of] the]] items ([inside]|in [[the] inventory of]) %inventories%",
                "all [[of] the] %itemtypes% ([inside]|in [[the] inventory of]) %inventories%");
        // 'a hopper inventory named "x"': any kind of inventory that can be made on its own. The kinds are
        // looked up when used, as Bukkit's inventory types touch the server's registries.
        Map<String, InventoryKind> kinds = new LinkedHashMap<>();
        for (String kind : INVENTORY_KINDS) {
            kinds.put(kind.toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + " inventory", new InventoryKind(kind));
        }
        kinds.put("crafting table inventory", new InventoryKind("WORKBENCH"));
        registrar.namedType("inventorytype", "inventory type", InventoryKind.class, kinds);
        registrar.expression("ExprInventoryOfType", "inventory", info -> (context, args) -> {
            org.bukkit.event.inventory.InventoryType kind = args[0] instanceof InventoryKind k ? k.type() : null;
            if (kind == null || !kind.isCreatable()) {
                return null;
            }
            try {
                return args.length < 2 || args[1] == null ? Bukkit.createInventory(null, kind)
                        : Bukkit.createInventory(null, kind, Messages.component(args[1]));
            } catch (IllegalArgumentException cannotMake) {
                return null;
            }
            // A name or 'new' is needed, as for chest inventories: 'hopper inventory' alone is the kind.
        }, "[a] [new] %*inventorytype% (named|with name) %text%", "[a] new %*inventorytype%");
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
                        slot.set(stack.getAmount() <= 0 ? null : stack);
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
