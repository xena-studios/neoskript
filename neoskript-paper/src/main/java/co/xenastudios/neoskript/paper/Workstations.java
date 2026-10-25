/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import static co.xenastudios.neoskript.paper.EventExpressions.isIn;
import static co.xenastudios.neoskript.paper.EventExpressions.restrict;

import co.xenastudios.neoskript.api.element.Changeable.Mode;
import co.xenastudios.neoskript.api.element.SettableCondition;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Timespan;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Brewing stands, anvils, enchanting tables and furnaces, as Skript has them: what brewing makes and how long it
 * takes, whether fuel is used up, repair costs and rename text, enchantment offers and their costs, what was
 * enchanted, and the items of furnace events.
 */
@SuppressWarnings("removal") // Anvil inventories' repair costs and text, as Skript uses them.
final class Workstations {

    private Workstations() {}

    static void register(Registrar registrar) {
        registerBrewing(registrar);
        registerAnvils(registrar);
        registerEnchanting(registrar);
        registerFurnaces(registrar);
    }

    // ---------------------------------------------------------------- brewing

    private static void registerBrewing(Registrar registrar) {
        registrar.condition("CondBrewingConsume", info -> {
            boolean negated = info.pattern() == 1;
            restrict(info, "the brewing stand will" + (negated ? " not" : "") + " consume the fuel",
                    "the brewing fuel event", BrewingStandFuelEvent.class);
            return new SettableCondition() {
                @Override
                public boolean check(TriggerContext context, Object[] arguments) {
                    // As in Skript, whose check leaves out the negation: 'won't consume' is true while it will.
                    return context.event() instanceof BrewingStandFuelEvent event && event.isConsuming();
                }

                @Override
                public void set(TriggerContext context, Object[] arguments, boolean value) {
                    if (context.event() instanceof BrewingStandFuelEvent event) {
                        event.setConsuming(value != negated);
                    }
                }
            };
        }, "[the] brewing stand will consume [the] fuel", "[the] brewing stand (will not|won't) consume [the] fuel");
        registrar.effect("EffBrewingConsume", info -> {
            boolean consume = info.pattern() == 0;
            restrict(info, consume ? "make the brewing stand consume the fuel"
                    : "prevent the brewing stand from consuming the fuel", "the brewing fuel event", BrewingStandFuelEvent.class);
            return (context, args) -> {
                if (context.event() instanceof BrewingStandFuelEvent event) {
                    event.setConsuming(consume);
                }
            };
        }, "make [the] brewing stand consume [its|the] fuel", "prevent [the] brewing stand from consuming [its|the] fuel");
        registrar.expression("ExprBrewingResults", "itemstack", info -> {
            restrict(info, "the brewing results", "the brewing complete event", BrewEvent.class);
            return new EventChangeable<>(BrewEvent.class, event -> event.getResults().toArray(),
                    ItemType[].class, Property.modes(Mode.SET, Mode.DELETE, Mode.ADD, Mode.REMOVE), (event, delta, mode) -> {
                List<ItemStack> results = event.getResults();
                List<ItemType> items = new ArrayList<>();
                for (Object value : delta == null ? new Object[0] : delta) {
                    if (value instanceof ItemType item) {
                        items.add(item);
                    }
                }
                switch (mode) {
                    case SET -> {
                        results.clear();
                        items.forEach(item -> results.add(item.stack()));
                    }
                    case DELETE -> results.clear();
                    case ADD -> items.forEach(item -> results.add(item.stack()));
                    case REMOVE -> {
                        for (ItemStack stack : new ArrayList<>(results)) {
                            for (ItemType item : items) {
                                if (Events.isItem(item, stack)) {
                                    results.remove(stack);
                                }
                            }
                        }
                    }
                    default -> {}
                }
            });
        }, "[the] brewing results");
        registrar.expression("ExprBrewingTime", "timespan", info -> new BrewingTime(isIn(info, BrewingStartEvent.class)),
                Property.defaultPatterns("[current|remaining] brewing time", "blocks"));
    }

    /**
     * The brewing time left of brewing stands. In a brewing start event, the event's stand's is the event's, which
     * is what it will be.
     */
    private record BrewingTime(boolean inEvent) implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> times = new ArrayList<>();
            List<Object> blocks = owners(context, arguments);
            if (inEvent && context.event() instanceof BrewingStartEvent event && blocks.remove(event.getBlock())) {
                times.add(Property.ticks(event.getBrewingTime()));
            }
            for (Object block : blocks) {
                if (block instanceof Block b && b.getState() instanceof BrewingStand stand) {
                    times.add(Property.ticks(stand.getBrewingTime()));
                }
            }
            return Property.single(times);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET || mode == Mode.ADD || mode == Mode.REMOVE ? new Class<?>[] {Timespan.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            int given = delta == null || delta.length == 0 ? 0 : EventChangeable.ticks(delta[0]);
            List<Object> blocks = owners(context, arguments);
            if (context.event() instanceof BrewingStartEvent event && blocks.remove(event.getBlock())) {
                change(given, mode, event::getBrewingTime, event::setBrewingTime);
            }
            for (Object block : blocks) {
                if (block instanceof Block b && b.getState() instanceof BrewingStand stand) {
                    change(given, mode, stand::getBrewingTime, stand::setBrewingTime);
                    stand.update();
                }
            }
        }

        private static void change(int given, Mode mode, IntSupplier getter, IntConsumer setter) {
            long by = mode == Mode.REMOVE ? -(long) given : given;
            setter.accept((int) switch (mode) {
                case SET -> Property.fit(0, given, Integer.MAX_VALUE);
                case ADD, REMOVE -> Property.fit(0, getter.getAsInt() + by, Integer.MAX_VALUE);
                default -> 0;
            });
        }

        private static List<Object> owners(TriggerContext context, Object[] arguments) {
            Object owners = arguments.length == 0 ? null : arguments[0];
            if (owners == null) {
                Object value = Property.eventValue(context);
                return value == null ? new ArrayList<>() : new ArrayList<>(List.of(value));
            }
            return new ArrayList<>(Arrays.asList(Property.of(owners)));
        }
    }

    // ---------------------------------------------------------------- anvils

    private static void registerAnvils(Registrar registrar) {
        registrar.expression("ExprAnvilRepairCost", "integer", info -> new AnvilRepairCost(info.hasTag("max")),
                Property.defaultPatterns("[anvil] [item] [:max[imum]] repair cost", "inventories"));
        registrar.property("ExprAnvilText", "string", "anvil [inventory] (rename|text) input", "inventories",
                Inventory.class, inventory -> inventory instanceof AnvilInventory anvil ? anvil.getRenameText() : null);
    }

    /** The repair cost of anvils, or their maximum repair cost, which can be changed. */
    private record AnvilRepairCost(boolean max) implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> costs = new ArrayList<>();
            for (Object inventory : inventories(context, arguments)) {
                if (inventory instanceof AnvilInventory anvil) {
                    costs.add((long) (max ? anvil.getMaximumRepairCost() : anvil.getRepairCost()));
                }
            }
            return Property.single(costs);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.ADD || mode == Mode.REMOVE || mode == Mode.SET ? new Class<?>[] {Number.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (delta == null || delta.length == 0) {
                return;
            }
            int value = ((Number) delta[0]).intValue() * (mode == Mode.REMOVE ? -1 : 1);
            for (Object inventory : inventories(context, arguments)) {
                if (inventory instanceof AnvilInventory anvil) {
                    int current = mode == Mode.SET ? 0 : max ? anvil.getMaximumRepairCost() : anvil.getRepairCost();
                    int changed = Math.max(current + value, 0);
                    if (max) {
                        anvil.setMaximumRepairCost(changed);
                    } else {
                        anvil.setRepairCost(changed);
                    }
                }
            }
        }
    }

    /** The inventories given, or where they're left out, the event's. */
    static Object[] inventories(TriggerContext context, Object[] arguments) {
        Object owners = arguments.length == 0 ? null : arguments[0];
        if (owners == null) {
            return context.event() instanceof InventoryEvent event ? new Object[] {event.getInventory()} : new Object[0];
        }
        return Property.of(owners);
    }

    // ---------------------------------------------------------------- enchanting

    private static void registerEnchanting(Registrar registrar) {
        registrar.type("enchantmentoffer", "enchantment offer", EnchantmentOffer.class);
        registrar.alias("enchantmentoffer", "enchant offer", "enchant offers");
        registrar.alias("enchantmentoffer", "enchantoffer", "enchantoffers");
        registrar.text(EnchantmentOffer.class, offer -> Enchantments.name(offer.getEnchantment()) + " "
                + offer.getEnchantmentLevel());
        registrar.variableNameText(EnchantmentOffer.class, offer -> "offer:" + Enchantments.name(offer.getEnchantment())
                + "=" + offer.getEnchantmentLevel());
        registrar.converter(EnchantmentOffer.class, Enchantments.Levelled.class,
                offer -> new Enchantments.Levelled(offer.getEnchantment(), offer.getEnchantmentLevel()));
        registrar.comparison(EnchantmentOffer.class, Enchantments.Levelled.class, (offer, type) ->
                offer.getEnchantment() == type.enchantment() && offer.getEnchantmentLevel() == level(type));
        registrar.comparison(EnchantmentOffer.class, Experience.class, (offer, xp) -> offer.getCost() == xp.xp());

        registrar.expression("ExprEnchantingExpCost", "integer", info -> {
            restrict(info, "the displayed enchanting cost", "the enchant event", EnchantItemEvent.class);
            return new EventChangeable<>(EnchantItemEvent.class, event -> (long) event.getExpLevelCost(), Number.class,
                    Property.modes(Mode.SET, Mode.ADD, Mode.REMOVE), (event, delta, mode) -> {
                if (delta == null) {
                    return;
                }
                long cost = ((Number) delta[0]).longValue();
                long value = switch (mode) {
                    case ADD -> saturatedAdd(event.getExpLevelCost(), cost);
                    case REMOVE -> saturatedAdd(event.getExpLevelCost(), -cost);
                    default -> cost;
                };
                event.setExpLevelCost((int) Property.fit(1, value, Integer.MAX_VALUE));
            });
        }, "[the] [displayed] ([e]xp[erience]|enchanting) cost");
        registrar.expression("ExprEnchantmentBonus", "integer", info -> {
            restrict(info, "the enchantment bonus", "the enchant prepare event", PrepareItemEnchantEvent.class);
            return new EventChangeable<>(PrepareItemEnchantEvent.class, event -> (long) event.getEnchantmentBonus());
        }, "[the] enchant[ment] bonus");
        registrar.expression("ExprEnchantmentHint", "enchantment", info -> {
            restrict(info, "the enchantment hint", "the enchant event", EnchantItemEvent.class);
            return new EventChangeable<>(EnchantItemEvent.class, EnchantItemEvent::getEnchantmentHint);
        }, "[the] enchant[ment] hint");
        registrar.expression("ExprEnchantmentOffer", "enchantmentoffer", info -> {
            boolean all = info.pattern() == 0;
            restrict(info, all ? "the enchantment offers" : "enchantment offer", "the enchant prepare event",
                    PrepareItemEnchantEvent.class);
            return new EnchantmentOffers(all);
        }, "[all [[of] the]|the] enchant[ment] offers", "enchant[ment] offer[s] %integers%",
                "[the] %integer%(st|nd|rd|th) enchant[ment] offer");
        registrar.expression("ExprEnchantmentOfferCost", "integer", info -> new Property<>(EnchantmentOffer.class,
                offer -> (long) offer.getCost(), Number.class, Property.modes(Mode.SET, Mode.ADD, Mode.DELETE),
                (offer, delta, mode) -> {
                    if (delta == null) {
                        return;
                    }
                    long cost = ((Number) delta[0]).longValue();
                    if (cost < 1) {
                        return;
                    }
                    long value = mode == Mode.ADD ? saturatedAdd(offer.getCost(), cost) : cost;
                    if (value >= 1) {
                        offer.setCost((int) Property.fit(Integer.MIN_VALUE, value, Integer.MAX_VALUE));
                    }
                }), co.xenastudios.neoskript.api.registration.Patterns.property("[enchant[ment]] cost", "enchantmentoffers"));
        registrar.expression("ExprAppliedEnchantments", "enchantmenttype", info -> {
            restrict(info, "the applied enchantments", "the enchant event", EnchantItemEvent.class);
            return new EventChangeable<>(EnchantItemEvent.class, event -> event.getEnchantsToAdd().entrySet().stream()
                    .map(entry -> new Enchantments.Levelled(entry.getKey(), entry.getValue())).toArray(),
                    Enchantments.Levelled[].class, Property.modes(Mode.ADD, Mode.SET, Mode.DELETE, Mode.REMOVE),
                    (event, delta, mode) -> {
                Map<Enchantment, Integer> enchants = event.getEnchantsToAdd();
                if (mode == Mode.SET || mode == Mode.DELETE) {
                    enchants.clear();
                }
                for (Object value : delta == null ? new Object[0] : delta) {
                    if (value instanceof Enchantments.Levelled levelled) {
                        if (mode == Mode.REMOVE) {
                            enchants.remove(levelled.enchantment(), level(levelled));
                        } else if (mode != Mode.DELETE) {
                            enchants.put(levelled.enchantment(), level(levelled));
                        }
                    }
                }
            });
        }, "[the] applied enchant[ment]s");
        registrar.expression("ExprMinMaxEnchantmentLevel", "integer", info -> {
            boolean max = info.hasTag("max");
            return new Property<>(Enchantment.class, enchantment -> (long) (max ? enchantment.getMaxLevel()
                    : enchantment.getStartLevel()));
        }, Property.defaultPatterns("((:max|min)[imum]|starting) enchant[ment] level", "enchantments"));
    }

    /** An enchantment type's level, as Skript's {@code getLevel}: 1 where none was given. */
    private static int level(Enchantments.Levelled type) {
        return type.level() < 0 ? 1 : type.level();
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? (a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE) : sum;
    }

    /** The enchantment offers of an enchant prepare event, all or by number, which can be set to enchantments. */
    private record EnchantmentOffers(boolean all) implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            if (!(context.event() instanceof PrepareItemEnchantEvent event)) {
                return null;
            }
            EnchantmentOffer[] offers = event.getOffers();
            List<Object> found = new ArrayList<>();
            if (all) {
                for (EnchantmentOffer offer : offers) {
                    if (offer != null) {
                        found.add(offer);
                    }
                }
                return found.toArray();
            }
            for (Object index : Property.of(arguments[0])) {
                if (index instanceof Number number && number.longValue() >= 1 && number.longValue() <= offers.length
                        && offers[number.intValue() - 1] != null) {
                    found.add(offers[number.intValue() - 1]);
                }
            }
            return Property.single(found);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET || mode == Mode.DELETE ? new Class<?>[] {Enchantments.Levelled.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(context.event() instanceof PrepareItemEnchantEvent event)) {
                return;
            }
            EnchantmentOffer[] offers = event.getOffers();
            List<Integer> slots = new ArrayList<>();
            if (all) {
                for (int slot = 0; slot < 3; slot++) {
                    slots.add(slot);
                }
            } else {
                for (Object index : Property.of(arguments[0])) {
                    if (index instanceof Number number) {
                        slots.add(number.intValue() - 1);
                    }
                }
            }
            if (mode == Mode.DELETE) {
                if (all) {
                    Arrays.fill(offers, null);
                    return;
                }
                for (int slot : slots) {
                    if (slot >= 0 && slot < offers.length) {
                        offers[slot] = null;
                    }
                }
                return;
            }
            if (delta == null || delta.length == 0 || !(delta[0] instanceof Enchantments.Levelled type)) {
                return;
            }
            for (int slot : slots) {
                if (slot < 0 || slot >= offers.length) {
                    continue;
                }
                if (offers[slot] == null) {
                    offers[slot] = new EnchantmentOffer(type.enchantment(), level(type),
                            cost(slot + 1, event.getEnchantmentBonus()));
                } else {
                    offers[slot].setEnchantment(type.enchantment());
                    offers[slot].setEnchantmentLevel(level(type));
                }
            }
        }

        /** A new offer's cost, as Minecraft works it out, as in Skript. */
        private static int cost(int slot, int bookshelves) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int base = (random.nextInt(8) + 1) + (bookshelves / 2) + random.nextInt(bookshelves + 1);
            return switch (slot) {
                case 1 -> Math.max(base / 3, 1);
                case 2 -> (base * 2) / 3 + 1;
                case 3 -> Math.max(base, bookshelves * 2);
                default -> 1;
            };
        }
    }

    // ---------------------------------------------------------------- furnaces

    private static void registerFurnaces(Registrar registrar) {
        registrar.expression("ExprFurnaceEventItems", "itemstack", info -> {
            int which = info.pattern();
            Class<?> event = switch (which) {
                case 0 -> FurnaceSmeltEvent.class;
                case 1 -> FurnaceExtractEvent.class;
                case 2 -> FurnaceStartSmeltEvent.class;
                default -> FurnaceBurnEvent.class;
            };
            if (!isIn(info, event)) {
                throw new SyntaxError(switch (which) {
                    case 0 -> "'smelted item' can only be used in a smelting event.";
                    case 1 -> "'extracted item' can only be used in a furnace extract event.";
                    case 2 -> "'smelting item' can only be used in a start smelting event";
                    default -> "'burned fuel' can only be used in a fuel burning event.";
                });
            }
            if (which != 0) {
                return new EventChangeable<>(Object.class, value -> switch (value) {
                    case FurnaceExtractEvent extract -> new ItemStack(extract.getItemType(), extract.getItemAmount());
                    case FurnaceStartSmeltEvent start -> start.getSource();
                    case FurnaceBurnEvent burn -> burn.getFuel();
                    default -> null;
                });
            }
            return new EventChangeable<>(FurnaceSmeltEvent.class, FurnaceSmeltEvent::getResult, ItemStack.class,
                    Property.modes(Mode.SET, Mode.DELETE), (smelt, delta, mode) -> {
                if (mode == Mode.DELETE) {
                    smelt.setResult(ItemStack.of(Material.AIR));
                } else if (delta != null && delta[0] instanceof ItemStack stack) {
                    smelt.setResult(stack);
                }
            });
        }, "[the] (smelted item|result[ item])", "[the] extracted item[s]", "[the] smelting item", "[the] burned (fuel|item)");
    }
}
