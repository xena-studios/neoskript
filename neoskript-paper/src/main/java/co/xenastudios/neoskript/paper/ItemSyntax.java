/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Comparison;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

/** Items: holding them, room for them, damage, stored enchantments, cooldowns and what they're made of. */
final class ItemSyntax {

    private ItemSyntax() {}

    static void register(Registrar registrar) {
        // A head's texture: its profile's 'textures' property. Setting it gives the head a new profile with it.
        java.util.function.Consumer<Object> clearTexture = owner -> ((ItemType) owner).editMeta(meta -> {
            if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                skull.setPlayerProfile(null);
            }
        });
        registrar.expression("ExprSkullTexture", "string", info -> new SettableProperty<>(ItemType.class, String.class,
                item -> item.stack().getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta meta
                        && meta.getPlayerProfile() != null ? meta.getPlayerProfile().getProperties().stream()
                        .filter(property -> property.getName().equals("textures")).findFirst()
                        .map(com.destroystokyo.paper.profile.ProfileProperty::getValue).orElse(null) : null,
                (item, texture) -> {
                    com.destroystokyo.paper.profile.PlayerProfile profile = org.bukkit.Bukkit.createProfile(java.util.UUID.randomUUID());
                    profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", texture));
                    item.editMeta(meta -> {
                        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                            skull.setPlayerProfile(profile);
                        }
                    });
                }).resettingTo(clearTexture).deleting(clearTexture::accept),
                Patterns.property("(skull|head) texture", "itemtypes"));
        // 'player is holding a diamond sword': the item in a hand is the item, as 'is' has it.
        registrar.condition("CondItemInHand", info -> {
            boolean off = info.pattern() % 4 >= 2;
            boolean negated = info.pattern() >= 4;
            return (context, args) -> {
                Object[] entities = Values.array(args[0]);
                Object[] items = Values.array(args[1]);
                if (entities.length == 0 || items.length == 0) {
                    return negated;
                }
                boolean all = check(entities, info.and(0), entity -> check(items, info.and(1), item -> {
                    EntityEquipment equipment = entity instanceof LivingEntity living ? living.getEquipment() : null;
                    if (equipment == null) {
                        return false;
                    }
                    ItemStack hand = off ? equipment.getItemInOffHand() : equipment.getItemInMainHand();
                    return Comparison.equal(new ItemType(hand, true), item);
                }));
                return all != negated;
            };
        }, "[%livingentities%] ha(s|ve) %itemtypes% in [main] hand", "[%livingentities%] (is|are) holding %itemtypes% [in main hand]",
                "[%livingentities%] ha(s|ve) %itemtypes% in off[(-| )]hand",
                "[%livingentities%] (is|are) holding %itemtypes% in off[(-| )]hand",
                "[%livingentities%] (ha(s|ve) not|do[es]n't have) %itemtypes% in [main] hand",
                "[%livingentities%] (is not|isn't) holding %itemtypes% [in main hand]",
                "[%livingentities%] (ha(s|ve) not|do[es]n't have) %itemtypes% in off[(-| )]hand",
                "[%livingentities%] (is not|isn't) holding %itemtypes% in off[(-| )]hand");

        // 'player can hold 64 dirt': an and-list has to fit all together, an or-list any one of them.
        registrar.condition("CondCanHold", info -> {
            boolean negated = info.pattern() == 1;
            boolean together = info.and(1);
            return (context, args) -> {
                Object[] inventories = Values.array(args[0]);
                Object[] items = Values.array(args[1]);
                boolean holds = check(inventories, info.and(0), value -> {
                    if (!(value instanceof Inventory inventory)) {
                        return false;
                    }
                    if (!together) {
                        return check(items, false, item -> item instanceof ItemType type && fits(inventory, List.of(type)));
                    }
                    List<ItemType> all = new ArrayList<>();
                    for (Object item : items) {
                        if (item instanceof ItemType type) {
                            all.add(type);
                        }
                    }
                    return fits(inventory, all);
                });
                return holds != negated;
            };
        }, "%inventories% (can hold|ha(s|ve) [enough] space (for|to hold)) %itemtypes%",
                "%inventories% (can(no|')t hold|(ha(s|ve) not|ha(s|ve)n't|do[es]n't have) [enough] space (for|to hold)) %itemtypes%");

        // 'diamond sword with damage 100', 'diamond sword damaged by 500'.
        registrar.expression("ExprDamagedItem", "itemtype", info -> (context, args) -> {
            if (!(Values.single(args[1]) instanceof Number damage)) {
                return args[0];
            }
            return map(args[0], item -> item.withMeta(meta -> {
                if (meta instanceof Damageable damageable) {
                    damageable.setDamage(Math.max(0, damage.intValue()));
                }
            }));
        }, "%itemtype% with (damage|data) [value] %number%", "%itemtype% damaged by %number%");
        // 'plain diamond sword': the item with nothing else to it.
        registrar.expression("ExprPlain", "itemtype", info -> (context, args) ->
                map(args[0], ItemType::plainCopy),
                "[a[n]] (plain|unmodified) %itemtype%");
        registrar.expression("ExprStoredEnchantments", "enchantmenttype", info -> new StoredEnchantments(),
                Patterns.property("stored enchant[ment]s", "itemtypes"));
        // The item a block drops when picked with its contents: a chest with its items.
        registrar.expression("ExprExactItem", "itemstack", info -> (context, args) -> {
            List<Object> items = new ArrayList<>();
            for (Object value : Values.array(args[0])) {
                if (value instanceof Block block && block.getType().isItem()) {
                    ItemStack stack = new ItemStack(block.getType());
                    if (stack.getItemMeta() instanceof BlockStateMeta meta) {
                        meta.setBlockState(block.getState());
                        stack.setItemMeta(meta);
                    }
                    items.add(stack);
                }
            }
            return items.size() == 1 ? items.getFirst() : items.toArray();
        }, Patterns.property("exact item[s]", "blocks"));
        EntitySyntax.conditionPatterns(registrar, "CondIsStackable", Object.class,
                value -> stack(value) != null && stack(value).getMaxStackSize() > 1, null,
                "%itemstacks/itemtypes% (is|are) stackable", "%itemstacks/itemtypes% (isn't|is not|aren't|are not) stackable");
        EntitySyntax.conditionPatterns(registrar, "CondIsFuel", ItemType.class, item -> item.material().isFuel(), null,
                "%itemtypes% (is|are) [furnace] fuel", "%itemtypes% (isn't|is not|aren't|are not) [furnace] fuel");
        // How long a player has to wait to use an item again.
        registrar.expression("ExprItemCooldown", "timespan", info -> new ItemCooldown(info.pattern() == 1),
                "[the] [item] cooldown of %itemtypes% for %players%", "%players%'[s] [item] cooldown for %itemtypes%");
        registrar.condition("CondHasItemCooldown", info -> {
            boolean negated = info.pattern() == 2;
            return (context, args) -> {
                Object[] players = Values.array(args[0]);
                Object[] items = Values.array(args[1]);
                boolean has = check(players, info.and(0), player -> check(items, info.and(1), item ->
                        player instanceof Player p && item instanceof ItemType type && p.hasCooldown(type.material())));
                return has != negated;
            };
        }, "%players% (has|have) [([an] item|a)] cooldown (on|for) %itemtypes%",
                "%players% (has|have) %itemtypes% on [(item|a)] cooldown",
                "%players% (doesn't|does not|do not|don't) have [([an] item|a)] cooldown (on|for) %itemtypes%");
        // 'all item types': every item there is.
        registrar.expression("ExprSets-itemtypes", "itemtype", info -> (context, args) -> {
            List<Object> all = new ArrayList<>();
            for (Material material : Material.values()) {
                if (!material.isLegacy() && material.isItem() && !material.isAir()) {
                    all.add(new ItemType(new ItemStack(material), false));
                }
            }
            return all.toArray();
        }, "[all [[of] the]|the|every] item[ ]types");
    }

    /** Whether all of {@code values}, or with {@code all} false any of them, pass {@code test}. */
    private static boolean check(Object[] values, boolean all, java.util.function.Predicate<Object> test) {
        for (Object value : values) {
            if (test.test(value) != all) {
                return !all;
            }
        }
        return all;
    }

    /** Whether {@code items} fit into {@code inventory}'s storage, all together. */
    private static boolean fits(Inventory inventory, List<ItemType> items) {
        ItemStack[] contents = inventory.getStorageContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        for (ItemType item : items) {
            ItemStack stack = item.alternatives().getFirst().stack();
            int left = stack.getAmount();
            int max = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());
            for (int i = 0; i < copy.length && left > 0; i++) {
                if (copy[i] != null && copy[i].isSimilar(stack) && copy[i].getAmount() < max) {
                    int added = Math.min(left, max - copy[i].getAmount());
                    copy[i].setAmount(copy[i].getAmount() + added);
                    left -= added;
                }
            }
            for (int i = 0; i < copy.length && left > 0; i++) {
                if (copy[i] == null || copy[i].getType().isAir()) {
                    int added = Math.min(left, max);
                    copy[i] = stack.asQuantity(added);
                    left -= added;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack stack(Object value) {
        return value instanceof ItemStack stack ? stack : value instanceof ItemType item ? item.stack() : null;
    }

    private static Object map(Object items, java.util.function.Function<ItemType, Object> change) {
        Object[] values = Values.array(items);
        List<Object> changed = new ArrayList<>(values.length);
        for (Object value : values) {
            if (value instanceof ItemType item) {
                changed.add(change.apply(item));
            }
        }
        return changed.size() == 1 ? changed.getFirst() : changed.toArray();
    }

    /** {@code the stored enchantments of {_book}}: an enchanted book's, which can be changed. */
    private static final class StoredEnchantments implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> found = new ArrayList<>();
            for (Object value : Values.array(arguments[0])) {
                if (value instanceof ItemType item && item.meta() instanceof EnchantmentStorageMeta meta) {
                    meta.getStoredEnchants().forEach((enchantment, level) -> found.add(new Enchantments.Levelled(enchantment, level)));
                }
            }
            return found.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case ADD, SET, REMOVE, REMOVE_ALL -> new Class<?>[] {Enchantments.Levelled.class};
                case DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            for (Object value : Values.array(arguments[0])) {
                if (!(value instanceof ItemType item)) {
                    continue;
                }
                item.editMeta(raw -> {
                    if (!(raw instanceof EnchantmentStorageMeta meta)) {
                        return;
                    }
                    Map<Enchantment, Integer> adjusted = new HashMap<>(meta.getStoredEnchants());
                    if (mode == Mode.SET || mode == Mode.DELETE) {
                        adjusted.clear();
                    }
                    for (Object change : delta == null ? new Object[0] : delta) {
                        Enchantments.Levelled levelled = change instanceof Enchantments.Levelled l ? l
                                : change instanceof Enchantment e ? new Enchantments.Levelled(e, -1) : null;
                        if (levelled == null) {
                            continue;
                        }
                        int level = levelled.level() < 0 ? 1 : levelled.level();
                        if (mode == Mode.ADD || mode == Mode.SET) {
                            adjusted.put(levelled.enchantment(), level);
                        } else if (levelled.level() < 0) {
                            adjusted.remove(levelled.enchantment());
                        } else {
                            adjusted.remove(levelled.enchantment(), level);
                        }
                    }
                    for (Enchantment existing : List.copyOf(meta.getStoredEnchants().keySet())) {
                        meta.removeStoredEnchant(existing);
                    }
                    adjusted.forEach((enchantment, level) -> meta.addStoredEnchant(enchantment, level, true));
                });
            }
        }
    }

    /** {@code the item cooldown of a shield for the player}: settable, in ticks as timespans. */
    private record ItemCooldown(boolean playersFirst) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> cooldowns = new ArrayList<>();
            for (Object player : players(arguments)) {
                for (Object item : items(arguments)) {
                    if (player instanceof Player p && item instanceof ItemType type) {
                        cooldowns.add(new Timespan(p.getCooldown(type.material()) * 50L));
                    }
                }
            }
            return cooldowns.size() == 1 ? cooldowns.getFirst() : cooldowns.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {Timespan.class};
                case RESET, DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            long ticks = delta != null && delta.length > 0 && delta[0] instanceof Timespan t ? t.ticks() : 0;
            for (Object player : players(arguments)) {
                for (Object item : items(arguments)) {
                    if (!(player instanceof Player p) || !(item instanceof ItemType type)) {
                        continue;
                    }
                    long current = p.getCooldown(type.material());
                    long next = switch (mode) {
                        case ADD -> current + ticks;
                        case REMOVE -> current - ticks;
                        case SET -> ticks;
                        default -> 0;
                    };
                    p.setCooldown(type.material(), (int) Math.max(0, Math.min(Integer.MAX_VALUE, next)));
                }
            }
        }

        private Object[] players(Object[] arguments) {
            return Values.array(arguments[playersFirst ? 0 : 1]);
        }

        private Object[] items(Object[] arguments) {
            return Values.array(arguments[playersFirst ? 1 : 0]);
        }
    }
}
