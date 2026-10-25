/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

/**
 * Loot tables, as Skript's loot table module has them: a block's or entity's loot table and its seed, loot
 * contexts and what they're made of, the loot a table gives, and the {@code loot generate} event.
 */
final class LootTables {

    private LootTables() {}

    /**
     * A loot context, as Skript's {@code LootContextWrapper} makes one: its location, looter, looted entity
     * and luck. Only the one a {@code a loot context at X:} section is setting up can be changed; one stored
     * in a variable, or an event's, is a fixed copy.
     */
    static final class Context {
        private Location location;
        private Player killer;
        private Entity entity;
        private float luck;
        private final boolean open;
        private LootContext built;

        Context(Location location, Player killer, Entity entity, float luck, boolean open) {
            this.location = location;
            this.killer = killer;
            this.entity = entity;
            this.luck = luck;
            this.open = open;
        }

        /** An event's loot context, which can't be changed. */
        static Context of(LootContext context) {
            Context made = new Context(context.getLocation(), context.getKiller() instanceof Player player ? player : null,
                    context.getLootedEntity(), context.getLuck(), false);
            made.built = context;
            return made;
        }

        /** The Bukkit loot context. */
        LootContext context() {
            if (built == null) {
                built = new LootContext.Builder(location).killer(killer).lootedEntity(entity).luck(luck).build();
            }
            return built;
        }

        /** A fixed copy, as a variable stores it. */
        Context copy() {
            Context copy = new Context(location, killer, entity, luck, false);
            copy.built = built;
            return copy;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("loot context at ").append(co.xenastudios.neoskript.runtime.Values.text(location));
            if (entity != null) {
                builder.append(" with entity ").append(co.xenastudios.neoskript.runtime.Values.text(entity));
            }
            if (killer != null) {
                builder.append(" with killer ").append(co.xenastudios.neoskript.runtime.Values.text(killer));
            }
            if (luck != 0) {
                builder.append(" with luck ").append(luck);
            }
            return builder.toString();
        }
    }

    static void register(Registrar registrar) {
        registrar.type("loottable", "loot table", LootTable.class);
        registrar.alias("loottable", "loottable", "loottables");
        registrar.text(LootTable.class, table -> "loot table \"" + table.getKey() + '"');
        registrar.comparison(LootTable.class, LootTable.class, (a, b) -> a.getKey().equals(b.getKey()));
        registrar.parser("loottable", LootTables::lootTable);

        registrar.type("lootcontext", "loot context", Context.class);
        registrar.alias("lootcontext", "lootcontext", "lootcontexts");
        registrar.text(Context.class, Context::toString);
        registrar.cloner(Context.class, Context::copy);
        registrar.eventDefault("lootcontext");

        registrar.event("EvtLootGenerate", List.of(LootGenerateEvent.class), null, "loot generat(e|ing)");
        registrar.eventValue(LootGenerateEvent.class, "entity", LootGenerateEvent::getEntity);
        registrar.eventValue(LootGenerateEvent.class, "location", event -> event.getLootContext().getLocation());
        registrar.eventValue(LootGenerateEvent.class, "loottable", LootGenerateEvent::getLootTable);
        registrar.eventValue(LootGenerateEvent.class, "lootcontext", event -> Context.of(event.getLootContext()));

        registrar.condition("CondHasLootTable", info -> {
            boolean negated = info.pattern() == 1;
            boolean and = info.and(0);
            return (context, args) -> negated != co.xenastudios.neoskript.api.element.Condition.check((Object[]) args[0], and,
                    owner -> lootable(owner) instanceof Lootable lootable && lootable.getLootTable() != null);
        }, Patterns.hasProperty("[a] loot[ ]table", "blocks/entities"));

        registrar.effect("EffGenerateLoot", info -> (context, args) -> {
            LootContext lootContext;
            if (args[1] != null) {
                if (!(args[1] instanceof Context given)) {
                    return;
                }
                lootContext = given.context();
            } else {
                lootContext = defaultContext();
            }
            if (!(args[0] instanceof LootTable table)) {
                return;
            }
            Random random = ThreadLocalRandom.current();
            for (Object value : (Object[]) args[2]) {
                if (value instanceof Inventory inventory) {
                    try {
                        table.fillInventory(inventory, random, lootContext);
                    } catch (IllegalArgumentException ignored) {
                        // The context lacks what the table needs, as Skript ignores.
                    }
                }
            }
        }, "generate [the] loot (of|using) %loottable% [(with|using) %-lootcontext%] in %inventories%");

        registrar.expression("ExprLoot", "itemstack", info -> {
            if (info.eventClasses().stream().noneMatch(LootGenerateEvent.class::isAssignableFrom)) {
                throw new SyntaxError("The 'loot' expression can only be used in a 'loot generate' event");
            }
            return new Loot();
        }, "[the] loot");

        registrar.expression("ExprLootItems", "itemstack", info -> (context, args) -> {
            LootContext lootContext;
            if (args[1] != null) {
                if (!(args[1] instanceof Context given)) {
                    return new Object[0];
                }
                lootContext = given.context();
            } else {
                lootContext = defaultContext();
            }
            List<Object> items = new ArrayList<>();
            Random random = ThreadLocalRandom.current();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof LootTable table) {
                    try {
                        items.addAll(table.populateLoot(random, lootContext));
                    } catch (IllegalArgumentException ignored) {
                        // The context lacks what the table needs, as Skript ignores.
                    }
                }
            }
            return items.toArray();
        }, "[the] loot of %loottables% [(with|using) [[loot] context] %-lootcontext%]",
                "%loottables%'[s] loot [(with|using) [[loot] context] %-lootcontext%]");

        registrar.expression("ExprLootTable", "loottable", info -> new LootableProperty<>(LootTable.class,
                Lootable::getLootTable, Lootable::setLootTable, true), Patterns.property("loot[ ]table[s]", "entities/blocks"));
        registrar.expression("ExprLootTableFromString", "loottable", info -> (context, args) -> {
            List<Object> tables = new ArrayList<>();
            for (Object key : (Object[]) args[0]) {
                LootTable table = key instanceof String text ? lootTable(text) : null;
                if (table != null) {
                    tables.add(table);
                }
            }
            return tables.size() == 1 ? tables.getFirst() : tables.toArray();
        }, "[the] loot[ ]table[s] %strings%");
        // Any number: NaN is 0, and too big a number the biggest seed, as Java makes it a long.
        registrar.expression("ExprLootTableSeed", "integer", info -> new LootableProperty<>(Number.class,
                Lootable::getSeed, (lootable, seed) -> lootable.setSeed(seed.longValue()), false),
                Patterns.property("loot[[ ]table] seed[s]", "entities/blocks"));

        // 'a loot context at X:': its section sets the context up, which can't be changed afterwards.
        registrar.expression("ExprSecCreateLootContext", "lootcontext", info -> (context, args) -> {
            if (!(args[0] instanceof WorldSyntax.Direction direction) || !(args[1] instanceof Location location)) {
                return null;
            }
            return new Context(location.clone().add(direction.from(location)), null, null, 0, true);
        }, "[a] loot context %direction% %location%");
        registrar.sectionExpression("ExprSecCreateLootContext", "lootcontext");

        contextProperty(registrar, "ExprLootContextEntity", "entity", "looted entity", Entity.class,
                context -> context.entity, (context, entity) -> context.entity = entity, true, false,
                "You cannot set the looted entity of an existing loot context.");
        contextProperty(registrar, "ExprLootContextLocation", "location", "loot[ing] [context] location", Location.class,
                context -> context.location, (context, location) -> context.location = location, false, false,
                "You cannot set the loot context location of an existing loot context.");
        contextProperty(registrar, "ExprLootContextLooter", "player", "(looter|looting player)", Player.class,
                context -> context.killer, (context, player) -> context.killer = player, true, false,
                "You cannot set the looting player of an existing loot context.");
        // As the decimal written: 3.3, not the float's 3.299999952...
        contextProperty(registrar, "ExprLootContextLuck", "number", "loot[ing] [context] luck [value|factor]", Number.class,
                context -> Double.parseDouble(Float.toString(context.luck)),
                (context, luck) -> context.luck = luck == null ? 0 : luck.floatValue(), true, true,
                "You cannot set the loot context luck of an existing loot context.");
    }

    /** The loot table with this key, or {@code null}. */
    private static LootTable lootTable(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        return namespacedKey == null ? null : Bukkit.getLootTable(namespacedKey);
    }

    /** A loot context at the first world's spawn, as Skript uses when none is given. */
    private static LootContext defaultContext() {
        return new Context(Bukkit.getWorlds().getFirst().getSpawnLocation(), null, null, 0, false).context();
    }

    /** What a block's state or an entity is as a lootable, or {@code null}. */
    private static Lootable lootable(Object owner) {
        Object value = owner instanceof Block block ? block.getState() : owner;
        return value instanceof Lootable lootable ? lootable : null;
    }

    /** Saves a block's lootable state, which changing the loot table or seed of doesn't do on its own. */
    private static void update(Lootable lootable) {
        if (lootable instanceof BlockState state) {
            state.update(true, false);
        }
    }

    /**
     * {@code [the] <property> [of %lootcontexts%]}, which only a context being set up can change, set (and
     * cleared or reset, if {@code clears}; added to and taken from, if {@code adds}).
     */
    private static <V> void contextProperty(Registrar registrar, String id, String returnType, String property,
            Class<V> valueClass, Function<Context, Object> getter, BiConsumer<Context, V> setter, boolean clears,
            boolean adds, String fixed) {
        registrar.expression(id, returnType, info -> new ContextProperty<>(valueClass, getter, setter, clears, adds, fixed),
                "[the] " + property + " [of %lootcontexts%]", "%lootcontexts%'[s] " + property);
    }

    private record ContextProperty<V>(Class<V> valueClass, Function<Context, Object> getter, BiConsumer<Context, V> setter,
            boolean clears, boolean adds, String fixed) implements Expression, Changeable {

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object owner : array(arguments[0])) {
                if (owner instanceof Context loot) {
                    Object value = getter.apply(loot);
                    if (value != null) {
                        values.add(value);
                    }
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET -> new Class<?>[] {valueClass};
                case DELETE, RESET -> clears ? new Class<?>[0] : null;
                case ADD, REMOVE -> adds ? new Class<?>[] {valueClass} : null;
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Object given = delta == null || delta.length == 0 ? null : delta[0];
            if (given == null && mode != Mode.DELETE && mode != Mode.RESET && !(mode == Mode.SET && clears)) {
                return;
            }
            for (Object owner : array(arguments[0])) {
                if (!(owner instanceof Context loot)) {
                    continue;
                }
                if (!loot.open) {
                    // Skript says this when the script loads; here it's said when it runs.
                    RuntimeErrors.report(fixed);
                    continue;
                }
                if (mode == Mode.ADD || mode == Mode.REMOVE) {
                    float by = ((Number) given).floatValue();
                    loot.luck = mode == Mode.ADD ? loot.luck + by : loot.luck - by;
                } else {
                    setter.accept(loot, valueClass.cast(given));
                }
                loot.built = null;
            }
        }
    }

    /** A block's or entity's loot table or seed, which is saved to the block when changed. */
    private record LootableProperty<V>(Class<V> valueClass, Function<Lootable, Object> getter,
            BiConsumer<Lootable, V> setter, boolean clears) implements Expression, Changeable {

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object owner : array(arguments[0])) {
                Lootable lootable = lootable(owner);
                Object value = lootable == null ? null : getter.apply(lootable);
                if (value != null) {
                    values.add(value);
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET -> new Class<?>[] {valueClass};
                case DELETE, RESET -> clears ? new Class<?>[0] : null;
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Object given = delta == null || delta.length == 0 ? null : delta[0];
            if (given == null && !clears) {
                return;
            }
            for (Object owner : array(arguments[0])) {
                Lootable lootable = lootable(owner);
                if (lootable != null) {
                    // Deleting an entity's loot table gives it back its default one.
                    setter.accept(lootable, valueClass.cast(given));
                    update(lootable);
                }
            }
        }
    }

    /** The loot of a {@code loot generate} event, which can be changed. */
    private static final class Loot implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return context.event() instanceof LootGenerateEvent event ? event.getLoot().toArray() : new Object[0];
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case DELETE -> new Class<?>[0];
                case ADD, REMOVE, SET -> new Class<?>[] {ItemStack.class};
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(context.event() instanceof LootGenerateEvent event)) {
                return;
            }
            List<ItemStack> items = new ArrayList<>();
            if (delta != null) {
                for (Object item : delta) {
                    if (item instanceof ItemStack stack) {
                        items.add(stack);
                    }
                }
            }
            switch (mode) {
                case ADD -> event.getLoot().addAll(items);
                case REMOVE -> event.getLoot().removeAll(items);
                case SET -> event.setLoot(items);
                case DELETE -> event.getLoot().clear();
                default -> {}
            }
        }
    }

    private static Object[] array(Object value) {
        return value instanceof Object[] array ? array : value == null ? new Object[0] : new Object[] {value};
    }
}
