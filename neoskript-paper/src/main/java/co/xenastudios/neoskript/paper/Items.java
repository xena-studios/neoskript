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

        /** The item a player holds on their cursor, as a slot: Skript's {@code cursor slot}. */
        static Slot cursor(org.bukkit.entity.HumanEntity player) {
            return new Slot(null, -1, player, null);
        }

        ItemStack item() {
            if (inventory == null && equipment == null && entity instanceof org.bukkit.entity.HumanEntity human) {
                return human.getItemOnCursor();
            }
            if (equipment != null) {
                EntityEquipment gear = entity.getEquipment();
                return gear == null ? null : gear.getItem(equipment);
            }
            return index >= 0 && index < inventory.getSize() ? inventory.getItem(index) : null;
        }

        void set(ItemStack item) {
            if (inventory == null && equipment == null && entity instanceof org.bukkit.entity.HumanEntity human) {
                human.setItemOnCursor(item);
            } else if (equipment != null) {
                EntityEquipment gear = entity.getEquipment();
                if (gear != null) {
                    gear.setItem(equipment, item);
                }
            } else if (index >= 0 && index < inventory.getSize()) {
                inventory.setItem(index, item);
            }
        }
    }

    /** A type to check an item against: an item type, or a kind of entity that's also an item ({@code armor stand}). */
    private static ItemType asItem(Object type) {
        return type instanceof ItemType item ? item
                : type instanceof EntityKind kind ? ItemType.parse(kind.toString()) : null;
    }

    /**
     * Whether {@code stack} is of the type {@code type}, as Skript's 'is of type': the same kind, whatever its
     * amount, with what the type was written with ({@code plain} meaning nothing more).
     */
    private static boolean ofType(ItemStack stack, ItemType type) {
        for (ItemType each : type.alternatives()) {
            ItemStack wanted = each.alternatives().getFirst().stack();
            if (stack.getType() != wanted.getType()) {
                continue;
            }
            boolean bare = !stack.hasItemMeta() || stack.getItemMeta().equals(new ItemStack(stack.getType()).getItemMeta());
            if (each.plain() ? bare : !wanted.hasItemMeta() || stack.isSimilar(wanted.asQuantity(stack.getAmount()))) {
                return true;
            }
        }
        return false;
    }

    /** Whether one item is another: the same item, with the meta and amount the other was written with. */
    private static boolean isItem(ItemType item, ItemType pattern) {
        ItemStack wanted = pattern.alternatives().getFirst().stack();
        ItemStack actual = item.alternatives().getFirst().stack();
        if (pattern.plain() && actual.hasItemMeta() && !actual.getItemMeta().equals(new ItemStack(actual.getType()).getItemMeta())) {
            // 'plain diamond' is only a diamond with nothing more to it.
            return false;
        }
        return actual.getType() == wanted.getType() && (!wanted.hasItemMeta() || actual.isSimilar(wanted))
                && (!pattern.amountGiven() || actual.getAmount() == wanted.getAmount());
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
        // An entity is the item it comes from: a boat is a boat item, a dropped item the item it is.
        registrar.comparison(org.bukkit.entity.Entity.class, ItemType.class, (entity, item) -> {
            if (entity instanceof org.bukkit.entity.Item dropped) {
                return co.xenastudios.neoskript.runtime.Comparison.equal(new ItemType(dropped.getItemStack(), true), item);
            }
            return item.material() == org.bukkit.Material.matchMaterial(entity.getType().getKey().getKey());
        });
        // An item standing for several (an alias like 'any boots') is another if each of its items is.
        registrar.comparison(ItemType.class, ItemType.class, (item, pattern) -> item.alternatives().stream()
                .allMatch(each -> pattern.alternatives().stream().anyMatch(wanted -> isItem(each, wanted))));
        registrar.comparison(ItemStack.class, ItemType.class, (stack, item) -> item.alternatives().stream()
                .anyMatch(wanted -> isItem(new ItemType(stack, true), wanted)));
        co.xenastudios.neoskript.runtime.ScriptAliases.installChecker(ItemType::checkDefinition);
        registrar.converter(ItemType.class, ItemStack.class, ItemType::stack);
        registrar.converter(ItemStack.class, ItemType.class, stack -> new ItemType(stack, true));
        registrar.converter(Slot.class, ItemStack.class, Slot::item);
        registrar.converter(Slot.class, ItemType.class, slot -> slot.item() == null ? null : new ItemType(slot.item(), true));
        // A slot compares as the item in it, air if it's empty: 'slot 0 of {_inv} is a stone'.
        registrar.comparison(Slot.class, ItemType.class, (slot, item) -> co.xenastudios.neoskript.runtime.Comparison.equal(
                slot.item() == null ? new ItemType(new ItemStack(org.bukkit.Material.AIR), false) : new ItemType(slot.item(), true), item));
        registrar.comparison(Slot.class, ItemStack.class, (slot, stack) -> co.xenastudios.neoskript.runtime.Comparison.equal(
                slot.item() == null ? new ItemType(new ItemStack(org.bukkit.Material.AIR), false) : new ItemType(slot.item(), true),
                new ItemType(stack, true)));
        registrar.converter(InventoryHolder.class, Inventory.class, InventoryHolder::getInventory);
        registrar.text(ItemStack.class, stack -> ItemType.text(stack, true));
        registrar.text(Slot.class, slot -> slot.item() == null ? "<none>" : ItemType.text(slot.item(), true));
        registrar.text(Inventory.class, inventory -> "inventory of " + inventory.getType().name().toLowerCase(java.util.Locale.ROOT));

        // Before naming items, so 'chest inventory with 2 rows named "x"' is one chest inventory, not a named one.
        registerInventories(registrar);

        // 'items of type any boots': each item an item type stands for.
        registrar.expression("ExprItems", "itemtype", info -> (context, args) -> {
            List<Object> items = new ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof ItemType type) {
                    for (ItemType each : type.alternatives()) {
                        if (!info.hasTag("block") || each.material().isBlock()) {
                            items.add(each.copy());
                        }
                    }
                }
            }
            return items.toArray();
        }, "[all [[of] the]|the|every] item[s] of type[s] %itemtypes%",
                "[all [[of] the]|the|every] block:block[s] of type[s] %itemtypes%");
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
        registrar.changer(InventoryHolder.class, Changeable.Mode.REMOVE, (holder, items) -> remove(holder.getInventory(), items));
        registrar.changer(Inventory.class, Changeable.Mode.REMOVE, Items::remove);
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
        // 'add 3 diamonds to the tool of X': more of the same item, or the item if the slot is empty.
        registrar.changer(Slot.class, Changeable.Mode.ADD, (slot, values) -> {
            for (ItemStack added : stacks(values)) {
                ItemStack held = slot.item();
                if (added == null || added.getType().isAir()) {
                    continue;
                }
                if (held == null || held.getType().isAir()) {
                    slot.set(added.clone());
                } else if (held.isSimilar(added)) {
                    held.setAmount(Math.min(held.getMaxStackSize(), held.getAmount() + added.getAmount()));
                    slot.set(held);
                }
            }
        });
        // 'remove 2 stone from slot 0 of X': takes that many of the item out, if it's that item.
        registrar.changer(Slot.class, Changeable.Mode.REMOVE, (slot, values) -> {
            for (ItemStack removed : stacks(values)) {
                ItemStack held = slot.item();
                if (held == null || held.getType().isAir() || removed == null || !held.isSimilar(removed)) {
                    continue;
                }
                int left = held.getAmount() - removed.getAmount();
                if (left <= 0) {
                    slot.set(null);
                } else {
                    held.setAmount(left);
                    slot.set(held);
                }
            }
        });


        registrar.expression("ExprLore", "string", info -> new Lore(info.pattern() >= 2),
                "[the] lore of %itemtype/slot%", "%itemtype/slot%'[s] lore",
                "[the] line %number% of [the] lore of %itemtype/slot%", "[the] line %number% of %itemtype/slot%'[s] lore",
                "[the] %number%(st|nd|rd|th) line of [the] lore of %itemtype/slot%",
                "[the] %number%(st|nd|rd|th) line of %itemtype/slot%'[s] lore");

        // 'the damage value of X' is the damage taken; 'the durability of X' is what's left.
        registrar.expression("ExprDurability", "integer", info -> {
            boolean damage = info.hasTag("damage");
            return new SettableProperty<>(Object.class, Number.class, owner -> {
                ItemStack stack = stack(owner);
                if (stack == null || !(stack.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable meta)) {
                    return null;
                }
                return (long) (damage ? meta.getDamage() : maxDurability(stack, meta) - meta.getDamage());
            }, (owner, value) -> editStack(owner, stack -> stack.editMeta(meta -> {
                if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                    int amount = co.xenastudios.neoskript.runtime.Values.toInt(value.doubleValue());
                    damageable.setDamage(Math.max(0, damage ? amount : maxDurability(stack, damageable) - amount));
                }
            })));
        }, co.xenastudios.neoskript.api.registration.Patterns.property("(damage:damage[s] [value[s]]|durabilit(y|ies))",
                "itemtypes/itemstacks/slots"));
        // The player a head belongs to, on a placed head or a head item.
        registrar.expression("ExprSkullOwner", "offlineplayer", info -> new SettableProperty<>(Object.class,
                org.bukkit.OfflinePlayer.class, owner -> {
                    if (owner instanceof org.bukkit.block.Block block) {
                        return block.getState() instanceof org.bukkit.block.Skull skull ? skull.getOwningPlayer() : null;
                    }
                    ItemStack stack = stack(owner);
                    return stack != null && stack.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta meta
                            ? meta.getOwningPlayer() : null;
                }, (owner, player) -> {
                    if (owner instanceof org.bukkit.block.Block block) {
                        if (block.getState() instanceof org.bukkit.block.Skull skull) {
                            skull.setOwningPlayer(player);
                            skull.update(true, false);
                        }
                        return;
                    }
                    editStack(owner, stack -> stack.editMeta(meta -> {
                        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                            skull.setOwningPlayer(player);
                        }
                    }));
                }), co.xenastudios.neoskript.api.registration.Patterns.property("(head|skull) owner",
                "slots/itemtypes/itemstacks/blocks"));
        // An item's own maximum durability and stack size where it has one, else its material's; resetting
        // takes the item's own away.
        SettableProperty.register(registrar, "ExprMaxDurability", "integer", "max[imum] (durabilit(y|ies)|damage)",
                "itemtypes/itemstacks/slots", Object.class, Number.class, owner -> {
                    ItemStack stack = stack(owner);
                    if (stack == null) {
                        return null;
                    }
                    return stack.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable meta
                            ? (long) maxDurability(stack, meta) : (long) stack.getType().getMaxDurability();
                }, (owner, value) -> editStack(owner, stack -> stack.editMeta(meta -> {
                    if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                        damageable.setMaxDamage((int) Math.max(1, Math.round(value.doubleValue())));
                    }
                })), (java.util.function.Consumer<Object>) owner -> editStack(owner, stack -> stack.editMeta(meta -> {
                    if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                        damageable.setMaxDamage(null);
                    }
                })));
        SettableProperty.register(registrar, "ExprMaxStack", "integer", "max[imum] stack[[ ]size]", "itemtypes/inventories",
                Object.class, Number.class, owner -> owner instanceof Inventory inventory ? (long) inventory.getMaxStackSize()
                        : stack(owner) == null ? null : (long) stack(owner).getMaxStackSize(),
                (owner, value) -> {
                    int size = (int) Math.max(1, Math.min(99, Math.round(value.doubleValue())));
                    if (owner instanceof Inventory inventory) {
                        inventory.setMaxStackSize((int) Math.max(1, Math.round(value.doubleValue())));
                    } else {
                        editStack(owner, stack -> stack.editMeta(meta -> meta.setMaxStackSize(size)));
                    }
                }, (java.util.function.Consumer<Object>) owner -> {
                    if (owner instanceof Inventory inventory) {
                        inventory.setMaxStackSize(org.bukkit.Bukkit.createInventory(null, 9).getMaxStackSize());
                    } else {
                        editStack(owner, stack -> stack.editMeta(meta -> meta.setMaxStackSize(null)));
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
                            case ItemStack stack -> asItem(type) instanceof ItemType item && ofType(stack, item);
                            case ItemType item -> asItem(type) instanceof ItemType wanted && item.alternatives().stream()
                                    .allMatch(each -> ofType(each.stack(), wanted));
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
        }, "%itemtypes/itemstacks/entities% (is|are) of type[s] %itemtypes/entitytypes%",
                "%itemtypes/itemstacks/entities% (isn't|is not|aren't|are not) of type[s] %itemtypes/entitytypes%");

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
     * Takes {@code items} out of {@code inventory} as Skript does: a plain item ({@code stone}) takes any stacks of
     * it, named or not; one with a name or enchantments takes only stacks like it.
     */
    static void remove(Inventory inventory, Object[] items) {
        for (Object item : items) {
            ItemStack wanted = stack(item);
            if (wanted == null) {
                continue;
            }
            if (wanted.hasItemMeta()) {
                inventory.removeItem(wanted);
                continue;
            }
            int left = wanted.getAmount();
            ItemStack[] contents = inventory.getStorageContents();
            for (int slot = 0; slot < contents.length && left > 0; slot++) {
                ItemStack held = contents[slot];
                if (held == null || held.getType() != wanted.getType()) {
                    continue;
                }
                int taken = Math.min(left, held.getAmount());
                left -= taken;
                held.setAmount(held.getAmount() - taken);
                inventory.setItem(slot, held.getAmount() <= 0 ? null : held);
            }
        }
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

    /** How much durability an item has in all: its own maximum if it has one, else its material's. */
    private static int maxDurability(ItemStack stack, org.bukkit.inventory.meta.Damageable meta) {
        return meta.hasMaxDamage() ? meta.getMaxDamage() : stack.getType().getMaxDurability();
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

    /** Changes the meta of the item {@code owner} is or holds, in place. */
    static void editMeta(Object owner, java.util.function.Consumer<org.bukkit.inventory.meta.ItemMeta> change) {
        editStack(owner, stack -> stack.editMeta(change));
    }

    private static void editStack(Object owner, java.util.function.Consumer<ItemStack> change) {
        switch (owner) {
            case ItemType item -> item.editStacks(change);
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
            ItemStack stack = stack(line ? arguments[1] : arguments[0]);
            if (stack == null || stack.getItemMeta() == null || stack.getItemMeta().lore() == null) {
                return null;
            }
            List<String> lines = new ArrayList<>();
            for (Component component : stack.getItemMeta().lore()) {
                lines.add(Messages.tagged(component));
            }
            if (!line) {
                return String.join("\n", lines);
            }
            int n = arguments[0] instanceof Number number ? co.xenastudios.neoskript.runtime.Values.toInt(number.doubleValue()) : 0;
            return n >= 1 && n <= lines.size() ? lines.get(n - 1) : null;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET || mode == Mode.ADD || mode == Mode.DELETE ? new Class<?>[] {String.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            editStack(line ? arguments[1] : arguments[0], stack -> stack.editMeta(meta -> {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                String text = delta == null || delta.length == 0 ? null : Values.text(delta[0]);
                if (line) {
                    int n = arguments[0] instanceof Number number ? co.xenastudios.neoskript.runtime.Values.toInt(number.doubleValue()) : 0;
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
            }));
        }
    }

    /** Bukkit's inventory types, by name, so registering them doesn't load the enum. */
    private static final List<String> INVENTORY_KINDS = List.of("CHEST", "DISPENSER", "DROPPER", "FURNACE", "WORKBENCH",
            "CRAFTING", "ENCHANTING", "BREWING", "PLAYER", "CREATIVE", "MERCHANT", "ENDER_CHEST", "ANVIL", "SMITHING",
            "BEACON", "HOPPER", "SHULKER_BOX", "BARREL", "BLAST_FURNACE", "LECTERN", "SMOKER", "LOOM", "CARTOGRAPHY",
            "GRINDSTONE", "STONECUTTER", "COMPOSTER", "CHISELED_BOOKSHELF", "JUKEBOX", "DECORATED_POT", "CRAFTER");

    /** A kind of inventory, Skript's {@code inventory type}: {@code hopper inventory}. */
    /**
     * The inventory of a container item, like a chest or shulker box, or {@code null}: changing it changes
     * the item, as in Skript.
     */
    private static Inventory itemInventory(ItemType item) {
        if (!(item.stack().getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta meta)
                || !(meta.getBlockState() instanceof org.bukkit.block.Container state)) {
            return null;
        }
        Inventory inventory = state.getInventory();
        return (Inventory) java.lang.reflect.Proxy.newProxyInstance(Items.class.getClassLoader(),
                new Class<?>[] {Inventory.class}, (proxy, method, arguments) -> {
                    Object result;
                    try {
                        result = method.invoke(inventory, arguments);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (state.update()) {
                        item.editMeta(edited -> ((org.bukkit.inventory.meta.BlockStateMeta) edited).setBlockState(state));
                    }
                    return result;
                });
    }

    /** The kind of inventory {@code text} names, as {@code hopper inventory}, or {@code null}. */
    static InventoryKind inventoryKind(String text) {
        String name = text.strip().toLowerCase(java.util.Locale.ROOT);
        if (!name.endsWith(" inventory")) {
            return null;
        }
        name = name.substring(0, name.length() - " inventory".length());
        if (name.equals("crafting table")) {
            return new InventoryKind("WORKBENCH");
        }
        String upper = name.toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        return INVENTORY_KINDS.contains(upper) ? new InventoryKind(upper) : null;
    }

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
            int amount = args[0] instanceof Number n ? co.xenastudios.neoskript.runtime.Values.toInt(n.doubleValue()) : 1;
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
                    case EntityKind kind -> values.add(new EntityKind(amount + " " + EntityKind.withoutAmount(kind.name()),
                            kind.type(), kind.entityClass(), kind.variants(), amount));
                    // Anything else has no amount: 1 of 5 is nothing.
                    default -> {}
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }, "%number% of %itemstacks/itemtypes/entitytypes%");
    }

    private static void registerInventories(Registrar registrar) {
        registrar.expression("ExprChestInventory", "inventory", info -> {
            boolean nameFirst = info.pattern() == 0;
            return (context, args) -> {
                Object name = args[nameFirst ? 0 : 1];
                Object rows = args[nameFirst ? 1 : 0];
                int size = 9 * Math.max(1, Math.min(6, rows instanceof Number n ? co.xenastudios.neoskript.runtime.Values.toInt(n.doubleValue()) : 3));
                return name == null ? Bukkit.createInventory(null, size)
                        : Bukkit.createInventory(null, size, Messages.component(name));
            };
        }, "[a] [new] chest inventory (named|with name) %text% [with %number% row[s]]",
                "[a] [new] chest inventory with %number% row[s] [(named|with name) %text%]");
        registrar.expression("ExprAmountOfItems", "integer", info -> (context, args) -> {
            long count = 0;
            for (Object value : (Object[]) args[1]) {
                if (!(value instanceof Inventory inventory)) {
                    continue;
                }
                for (ItemStack stack : inventory.getContents()) {
                    if (stack == null) {
                        continue;
                    }
                    for (Object item : (Object[]) args[0]) {
                        if (item instanceof ItemType type && type.material() == stack.getType()
                                && (!type.stack().hasItemMeta() || type.stack().isSimilar(stack))) {
                            count += stack.getAmount();
                            break;
                        }
                    }
                }
            }
            return count;
        }, "[the] (amount|number) of %itemtypes% (in|of) %inventories%");
        // Item flags, by name: 'hide enchants'.
        Map<String, org.bukkit.inventory.ItemFlag> flags = new LinkedHashMap<>();
        for (org.bukkit.inventory.ItemFlag flag : org.bukkit.inventory.ItemFlag.values()) {
            flags.put(flag.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '), flag);
        }
        registrar.namedType("itemflag", "item flag", org.bukkit.inventory.ItemFlag.class, flags);
        registrar.expression("ExprItemFlags", "itemflag", info -> new Flags(), "[the] item flags of %itemtypes%",
                "%itemtypes%'[s] item flags");
        registrar.expression("ExprItemWithItemFlags", "itemtype", info -> {
            boolean all = info.pattern() == 1;
            return (context, args) -> mapItems(args[0], item -> item.withMeta(meta -> {
                if (all) {
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
                } else {
                    for (Object flag : (Object[]) args[1]) {
                        if (flag instanceof org.bukkit.inventory.ItemFlag f) {
                            meta.addItemFlags(f);
                        }
                    }
                }
            }));
        }, "%itemtypes% with item flag[s] %itemflags%", "%itemtypes% with all item flags");
        registrar.expression("ExprItemUnbreakable", "itemtype", info -> {
            boolean unbreakable = info.pattern() == 0;
            return (context, args) -> mapItems(args[0], item -> item.withMeta(meta -> meta.setUnbreakable(unbreakable)));
        }, "unbreakable %itemtypes%", "breakable %itemtypes%");
        registrar.expression("ExprCustomModelData", "integer", info -> new SettableProperty<>(ItemType.class, Number.class,
                item -> item.stack().getItemMeta() != null && item.stack().getItemMeta().hasCustomModelData()
                        ? (long) item.stack().getItemMeta().getCustomModelData() : 0L,
                (item, data) -> item.editMeta(meta -> meta.setCustomModelData((int) data.doubleValue())))
                .resettingTo((java.util.function.Consumer<Object>) item -> ((ItemType) item).editMeta(meta -> meta.setCustomModelData(null)))
                .deleting(item -> item.editMeta(meta -> meta.setCustomModelData(null))),
                "[the] [custom] model data of %itemtypes%", "%itemtypes%'[s] [custom] model data");
        EntitySyntax.conditionPatterns(registrar, "CondHasCustomModelData", ItemType.class,
                item -> item.stack().getItemMeta() != null && item.stack().getItemMeta().hasCustomModelData(), null,
                "%itemtypes% (has|have) [custom] model data",
                "%itemtypes% (doesn't|does not|do not|don't) have [custom] model data");
        registrar.expression("ExprItemWithCustomModelData", "itemtype", info -> (context, args) -> mapItems(args[0], item ->
                args[1] instanceof Number data ? item.withMeta(meta -> meta.setCustomModelData((int) data.doubleValue())) : item),
                "%itemtypes% with [custom] model data %number%");
        registrar.expression("ExprItemModel", "string", info -> new SettableProperty<>(Object.class, String.class, owner -> {
            ItemStack stack = stack(owner);
            return stack == null || stack.getItemMeta() == null || !stack.getItemMeta().hasItemModel() ? null
                    : stack.getItemMeta().getItemModel().toString();
        }, (owner, model) -> {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(model.toLowerCase(java.util.Locale.ROOT));
            if (key == null) {
                co.xenastudios.neoskript.runtime.RuntimeErrors.report("'" + model + "' is not a valid item model key");
                return;
            }
            editStack(owner, stack -> stack.editMeta(meta -> meta.setItemModel(key)));
        }).resettingTo((java.util.function.Consumer<Object>) owner -> editStack(owner, stack -> stack.editMeta(meta -> meta.setItemModel(null))))
                .deleting(owner -> editStack(owner, stack -> stack.editMeta(meta -> meta.setItemModel(null)))),
                "[the] item model of %itemtypes/slots%", "%itemtypes/slots%'[s] item model");
        registrar.expression("ExprItemWithItemModel", "itemtype", info -> (context, args) -> mapItems(args[0], item -> {
            if (args[1] == null) {
                return item;
            }
            String model = co.xenastudios.neoskript.runtime.Values.text(args[1]);
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(model.toLowerCase(java.util.Locale.ROOT));
            if (key == null) {
                co.xenastudios.neoskript.runtime.RuntimeErrors.report("'" + model + "' is not a valid item model key");
                return item;
            }
            return item.withMeta(meta -> meta.setItemModel(key));
        }), "%itemtypes% with [an] [item] model %text%");
        Map<String, org.bukkit.inventory.ItemRarity> rarities = new LinkedHashMap<>();
        for (org.bukkit.inventory.ItemRarity rarity : org.bukkit.inventory.ItemRarity.values()) {
            rarities.put(rarity.name().toLowerCase(java.util.Locale.ROOT), rarity);
        }
        registrar.namedType("itemrarity", "item rarity", org.bukkit.inventory.ItemRarity.class, rarities);
        registrar.expression("ExprItemRarity", "itemrarity", info -> new SettableProperty<>(Object.class,
                org.bukkit.inventory.ItemRarity.class, owner -> {
                    ItemStack stack = stack(owner);
                    if (stack == null) {
                        return null;
                    }
                    // Without one of its own, an item has its kind's.
                    return stack.getItemMeta() != null && stack.getItemMeta().hasRarity() ? stack.getItemMeta().getRarity()
                            : stack.getData(io.papermc.paper.datacomponent.DataComponentTypes.RARITY);
                }, (owner, rarity) -> editStack(owner, stack -> stack.editMeta(meta -> meta.setRarity(rarity))))
                .resettingTo((java.util.function.Consumer<Object>) owner -> editStack(owner, stack -> stack.editMeta(meta -> meta.setRarity(null)))),
                "[the] item rarity of %itemtypes/slots%", "%itemtypes/slots%'[s] item rarity");
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
        // The hand slots of entities: what they hold.
        registrar.expression("ExprTool", "slot", info -> {
            EquipmentSlot hand = info.hasTag("off") ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
            return (context, args) -> {
                List<Object> slots = new ArrayList<>();
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof LivingEntity entity) {
                        slots.add(Slot.of(entity, hand));
                    }
                }
                return slots.size() == 1 ? slots.getFirst() : slots.toArray();
            };
        }, "[the] (tool|held item|weapon|off:off[ ]hand (tool|item)) of %livingentities%",
                "%livingentities%'[s] (tool|held item|weapon|off:off[ ]hand (tool|item))");
        registrar.expression("ExprInventoryHolder", "inventoryholder", info -> (context, args) -> {
            List<Object> holders = new ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof Inventory inventory && inventory.getHolder() != null) {
                    holders.add(inventory.getHolder());
                }
            }
            return holders.size() == 1 ? holders.getFirst() : holders.toArray();
        }, "[the] holder[s] of %inventories%", "%inventories%'[s] holder[s]");
        // Where an inventory's holder is: a block's centre, as for blocks; a double chest's left half.
        registrar.converter(InventoryHolder.class, org.bukkit.Location.class, holder -> switch (holder) {
            case org.bukkit.block.BlockState state -> state.getLocation().add(0.5, 0.5, 0.5);
            case org.bukkit.block.DoubleChest chest when chest.getLeftSide() instanceof org.bukkit.block.BlockState left ->
                    left.getLocation().add(0.5, 0.5, 0.5);
            case org.bukkit.entity.Entity entity -> entity.getLocation();
            default -> null;
        });
        // The item a dropped item, item frame or item display is or shows.
        registrar.expression("ExprItemOfEntity", "itemtype", info -> new SettableProperty<>(org.bukkit.entity.Entity.class,
                ItemType.class, entity -> {
                    ItemStack stack = switch (entity) {
                        case org.bukkit.entity.Item dropped -> dropped.getItemStack();
                        case org.bukkit.entity.ItemFrame frame -> frame.getItem();
                        case org.bukkit.entity.ItemDisplay display -> display.getItemStack();
                        case org.bukkit.entity.ThrowableProjectile thrown -> thrown.getItem();
                        default -> null;
                    };
                    return stack == null ? null : new ItemType(stack, true);
                }, (entity, item) -> {
                    ItemStack stack = item == null ? null : item.stack();
                    switch (entity) {
                        case org.bukkit.entity.Item dropped -> {
                            if (stack != null) {
                                dropped.setItemStack(stack);
                            }
                        }
                        case org.bukkit.entity.ItemFrame frame -> frame.setItem(stack);
                        case org.bukkit.entity.ItemDisplay display -> display.setItemStack(stack);
                        case org.bukkit.entity.ThrowableProjectile thrown -> {
                            if (stack != null) {
                                thrown.setItem(stack);
                            }
                        }
                        default -> {}
                    }
                }), "[the] item of %entities%", "%entities%'[s] item");
        registrar.expression("ExprFirstEmptySlot", "slot", info -> (context, args) -> {
            List<Object> slots = new ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof Inventory inventory) {
                    int first = inventory.firstEmpty();
                    if (first >= 0) {
                        slots.add(new Slot(inventory, first));
                    }
                }
            }
            return slots.size() == 1 ? slots.getFirst() : slots.toArray();
        }, "[the] first empty slot[s] (in|of) %inventories%", "%inventories%'[s] first empty slot[s]");
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
                } else if (holder instanceof ItemType item && itemInventory(item) instanceof Inventory inventory) {
                    inventories.add(inventory);
                }
            }
            return inventories.size() == 1 ? inventories.getFirst() : inventories.toArray();
        }, "[the] inventor(y|ies) of %inventoryholders/blocks/itemtypes%",
                "%inventoryholders/blocks/itemtypes%'[s] inventor(y|ies)");

        registrar.effect("EffOpenInventory", info -> {
            int pattern = info.pattern();
            String literal = pattern == 2 ? info.argumentLiteral(0) : null;
            if (literal != null) {
                InventoryKind kind = inventoryKind(literal);
                org.bukkit.event.inventory.InventoryType type = kind == null ? null : kind.type();
                if (type != null && !type.isCreatable()) {
                    throw new co.xenastudios.neoskript.api.element.SyntaxError(
                            "Cannot create an inventory of type " + kind);
                }
            }
            String fixed = switch (pattern) {
                case 3 -> "WORKBENCH";
                case 4 -> "CHEST";
                case 5 -> "ANVIL";
                case 6 -> "HOPPER";
                case 7 -> "DROPPER";
                case 8 -> "DISPENSER";
                default -> null;
            };
            return (context, args) -> {
                Object[] players = (Object[]) args[pattern == 2 ? 1 : 0];
                for (Object player : players) {
                    if (!(player instanceof Player p)) {
                        continue;
                    }
                    if (pattern <= 1) {
                        p.closeInventory();
                        continue;
                    }
                    Object opened = fixed != null ? new InventoryKind(fixed) : Values.single(args[0]);
                    if (opened instanceof InventoryKind kind) {
                        org.bukkit.event.inventory.InventoryType type = kind.type();
                        opened = type == null || !type.isCreatable() ? null : Bukkit.createInventory(null, type);
                    }
                    if (opened instanceof Inventory inventory) {
                        p.openInventory(inventory);
                    }
                }
            };
        }, "close %players%'[s] inventory [view]", "close [the] inventory [view] (to|of|for) %players%",
                "open %inventory/inventorytype% (to|for) %players%",
                "open [a] (crafting table|workbench) (to|for) %players%", "open [a] chest (to|for) %players%",
                "open [a[n]] anvil (to|for) %players%", "open [a] hopper (to|for) %players%",
                "open [a] dropper (to|for) %players%", "open [a] dispenser (to|for) %players%");

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
            int by = co.xenastudios.neoskript.runtime.Values.toInt(number.doubleValue());
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
                        slots.add(new Slot(inventory, co.xenastudios.neoskript.runtime.Values.toInt(n.doubleValue())));
                    }
                }
            }
            return slots.size() == 1 ? slots.getFirst() : slots.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {ItemType.class, ItemStack.class};
                case DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(arguments[1] instanceof Inventory inventory)) {
                return;
            }
            ItemStack[] items = delta == null ? new ItemStack[0] : stacks(delta);
            ItemStack item = items.length == 0 ? null : items[0];
            for (Object index : (Object[]) arguments[0]) {
                if (!(index instanceof Number n)) {
                    continue;
                }
                int i = co.xenastudios.neoskript.runtime.Values.toInt(n.doubleValue());
                if (i < 0 || i >= inventory.getSize()) {
                    continue;
                }
                if (mode == Mode.SET || mode == Mode.DELETE) {
                    inventory.setItem(i, item);
                    continue;
                }
                // Adding or removing the same item changes how many there are.
                for (ItemStack change : items) {
                    ItemStack held = inventory.getItem(i);
                    if (change == null || change.getType().isAir()) {
                        continue;
                    }
                    if (held == null || held.getType().isAir()) {
                        if (mode == Mode.ADD) {
                            inventory.setItem(i, change.clone());
                        }
                    } else if (held.isSimilar(change)) {
                        int amount = held.getAmount() + (mode == Mode.ADD ? change.getAmount() : -change.getAmount());
                        if (amount <= 0) {
                            inventory.setItem(i, null);
                        } else {
                            held.setAmount(Math.min(amount, held.getMaxStackSize()));
                            inventory.setItem(i, held);
                        }
                    }
                }
            }
        }
    }

    /** Each item through {@code change}; one value alone if there's one. */
    private static Object mapItems(Object items, java.util.function.Function<ItemType, Object> change) {
        List<Object> changed = new ArrayList<>();
        for (Object value : items instanceof Object[] array ? array : new Object[] {items}) {
            if (value instanceof ItemType item) {
                changed.add(change.apply(item));
            }
        }
        return changed.size() == 1 ? changed.getFirst() : changed.toArray();
    }

    /** {@code the item flags of %itemtypes%}: settable, added to, taken from, and reset to none. */
    private static final class Flags implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> flags = new ArrayList<>();
            for (Object value : (Object[]) arguments[0]) {
                if (value instanceof ItemType item && item.stack().getItemMeta() != null) {
                    flags.addAll(item.stack().getItemMeta().getItemFlags());
                }
            }
            return flags.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {org.bukkit.inventory.ItemFlag.class};
                case RESET, DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            List<org.bukkit.inventory.ItemFlag> given = new ArrayList<>();
            for (Object value : delta == null ? new Object[0] : delta) {
                if (value instanceof org.bukkit.inventory.ItemFlag flag) {
                    given.add(flag);
                }
            }
            org.bukkit.inventory.ItemFlag[] flags = given.toArray(org.bukkit.inventory.ItemFlag[]::new);
            for (Object value : (Object[]) arguments[0]) {
                if (value instanceof ItemType item) {
                    item.editMeta(meta -> {
                        if (mode != Mode.ADD && mode != Mode.REMOVE) {
                            meta.removeItemFlags(org.bukkit.inventory.ItemFlag.values());
                        }
                        if (mode == Mode.REMOVE) {
                            meta.removeItemFlags(flags);
                        } else if (mode == Mode.SET || mode == Mode.ADD) {
                            meta.addItemFlags(flags);
                        }
                    });
                }
            }
        }
    }
}
