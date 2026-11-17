/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Comparison;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Nameable;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Team;

/**
 * Skript 2.17's syntax with {@code use type properties: false}: what it registers in place of its type properties
 * ({@code PropertiesModule}), as before Skript 2.13. The old {@code ExprName}, {@code ExprAmount}, {@code CondIsEmpty},
 * {@code CondContains}, {@code ExprValue}, {@code ExprCoordinate} and {@code ExprXYZComponent}, for the types each
 * names, and {@code DefaultConverters}' converters to its named and numbered things. Title, progress, style and
 * viewers aren't any type's then. With type properties on, as in Skript's default config, none of it is registered.
 */
final class LegacyProperties {

    private LegacyProperties() {}

    /** Skript's {@code AnyNamed}: something with a name, which may be changeable. */
    interface AnyNamed {
        Component name();

        default boolean renames() {
            return false;
        }

        default void rename(Component name) {}
    }

    /** Skript's {@code AnyAmount}: something with an amount or size, which may be changeable. */
    interface AnyAmount {
        Number amount();

        default boolean changesAmount() {
            return false;
        }

        default void amount(Number amount) {}
    }

    /** Skript's {@code AnyValued}: something with a value, as a config entry. */
    interface AnyValued {
        Configs.ConfigNode node();
    }

    static void register(Registrar registrar) {
        if (TypeProperties.enabled()) {
            return;
        }
        types(registrar);
        name(registrar);
        amount(registrar);
        empty(registrar);
        contains(registrar);
        value(registrar);
        coordinates(registrar);
    }

    // Types and DefaultConverters

    private static void types(Registrar registrar) {
        registrar.type("named", "named thing", AnyNamed.class);
        registrar.alias("named", "named thing", "named things");
        registrar.type("numbered", "numbered thing", AnyAmount.class);
        registrar.alias("numbered", "numbered thing", "numbered things");
        registrar.type("valued", "valued thing", AnyValued.class);
        registrar.alias("valued", "valued thing", "valued things");
        // What implements Skript's AnyNamed and AnyAmount itself.
        registrar.converter(Items.Slot.class, AnyNamed.class, slot -> named(Players.name(slot)));
        registrar.converter(Items.Slot.class, AnyAmount.class, SLOT_AMOUNT::apply);
        registrar.converter(ItemType.class, AnyNamed.class, item -> named(Players.name(item)));
        registrar.converter(ItemType.class, AnyAmount.class, LegacyProperties::numbered);
        registrar.converter(Collections.ScriptQueue.class, AnyAmount.class, queue -> queue::size);
        registrar.converter(co.xenastudios.neoskript.runtime.ScriptRef.class, AnyNamed.class,
                script -> named(Players.name(script)));
        registrar.converter(co.xenastudios.neoskript.runtime.FunctionRef.class, AnyNamed.class,
                function -> named(Players.name(function)));
        registrar.converter(Configs.ConfigNode.class, AnyNamed.class, node -> named(Players.name(node)));
        registrar.converter(Configs.ConfigNode.class, AnyValued.class, node -> () -> node);
        // DefaultConverters with 'use type properties' off: anything with a name, then with an amount.
        registrar.converter(OfflinePlayer.class, AnyNamed.class, player -> named(text(player.getName())));
        registrar.converter(World.class, AnyNamed.class, world -> named(text(world.getName())));
        registrar.converter(GameRule.class, AnyNamed.class, rule -> named(text(rule.getName())));
        registrar.converter(Server.class, AnyNamed.class, server -> named(text(server.getName())));
        registrar.converter(Plugin.class, AnyNamed.class, plugin -> named(text(plugin.getName())));
        registrar.converter(WorldType.class, AnyNamed.class, type -> named(text(type.getName())));
        registrar.converter(Team.class, AnyNamed.class, team -> named(text(team.getName())));
        registrar.converter(Objective.class, AnyNamed.class, objective -> named(text(objective.getName())));
        registrar.converter(Nameable.class, AnyNamed.class, nameable -> new AnyNamed() {
            @Override
            public Component name() {
                return nameable.customName();
            }

            @Override
            public boolean renames() {
                return true;
            }

            @Override
            public void rename(Component name) {
                nameable.customName(name);
            }
        });
        registrar.converter(Block.class, AnyNamed.class, block -> new AnyNamed() {
            @Override
            public Component name() {
                return block.getState() instanceof Nameable nameable ? nameable.customName() : null;
            }

            @Override
            public boolean renames() {
                return true;
            }

            @Override
            public void rename(Component name) {
                if (block.getState() instanceof Nameable nameable) {
                    nameable.customName(name);
                    ((org.bukkit.block.BlockState) nameable).update(true, false);
                }
            }
        });
        registrar.converter(CommandSender.class, AnyNamed.class, sender -> named(text(sender.getName())));
        registrar.converter(ItemStack.class, AnyAmount.class, STACK_AMOUNT::apply);
    }

    private static final java.util.function.Function<Items.Slot, AnyAmount> SLOT_AMOUNT = slot -> new AnyAmount() {
        @Override
        public Number amount() {
            ItemStack item = slot.item();
            return item == null ? 0 : item.getAmount();
        }

        @Override
        public boolean changesAmount() {
            return true;
        }

        @Override
        public void amount(Number amount) {
            ItemStack item = slot.item();
            if (item != null) {
                item.setAmount(amount == null ? 0 : amount.intValue());
                slot.set(item);
            }
        }
    };

    private static final java.util.function.Function<ItemStack, AnyAmount> STACK_AMOUNT = item -> new AnyAmount() {
        @Override
        public Number amount() {
            return item.getAmount();
        }

        @Override
        public boolean changesAmount() {
            return true;
        }

        @Override
        public void amount(Number amount) {
            item.setAmount(amount == null ? 0 : amount.intValue());
        }
    };

    private static AnyNamed named(Component name) {
        return () -> name;
    }

    private static Component text(String text) {
        return text == null ? null : Component.text(text);
    }

    // ExprName

    private static void name(Registrar registrar) {
        List<String> patterns = new ArrayList<>(List.of(Patterns.property("name[s]",
                "offlineplayers/entities/nameds/inventories")));
        patterns.addAll(List.of(Patterns.property("(display|nick|chat|custom)[ ]name[s]",
                "offlineplayers/entities/nameds/inventories")));
        // As Skript writes it in errors: 'name of the current script'.
        registrar.describe("ExprName", (info, args) -> args.get(0) == null ? null
                : (info.pattern() / 2 == 0 ? "name of " : "display name of ") + args.get(0));
        registrar.expression("ExprName", "textcomponent", info -> new Name(info.pattern() / 2 + 1, info.argumentType(0)),
                patterns.toArray(String[]::new));
    }

    /**
     * Skript's old {@code ExprName}: 1 its name, 2 its display name, of values of {@code ownerType} (its id, or
     * {@code null} if that's known only when it runs).
     */
    private record Name(int mark, String ownerType) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> names = new ArrayList<>();
            for (Object owner : Values.array(arguments[0])) {
                Object name = name(owner);
                if (name != null) {
                    names.add(name);
                }
            }
            return names.size() == 1 ? names.getFirst() : names.toArray();
        }

        private Object name(Object owner) {
            if (owner instanceof OfflinePlayer offline && !(owner instanceof Player)) {
                if (offline.isOnline()) {
                    owner = offline.getPlayer();
                } else {
                    return mark != 1 || offline.getName() == null ? null : Component.text(offline.getName());
                }
            }
            return switch (owner) {
                case Player player -> mark == 1 ? player.name() : player.displayName();
                case Nameable nameable when mark == 1 && nameable instanceof CommandSender sender -> sender.name();
                case Nameable nameable -> nameable.customName();
                case Inventory inventory -> {
                    if (inventory.getViewers().isEmpty()) {
                        yield null;
                    }
                    InventoryView view = inventory.getViewers().getFirst().getOpenInventory();
                    yield view.title();
                }
                case AnyNamed named -> named.name();
                case null -> null;
                // Item types, slots, scripts, functions and config entries are named things themselves.
                default -> Players.name(owner);
            };
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            if (mode != Mode.SET && mode != Mode.RESET) {
                return null;
            }
            // As Skript, by the type the values are known to be: a player's name is its own, and scripts' and
            // functions' can't change. (A world is a named thing here, whose name doesn't change when set.)
            if (mark == 1 && ownerType != null) {
                switch (ownerType) {
                    case "player" -> throw new SyntaxError("Can't change the Minecraft name of a player. Change the"
                            + " 'display name' or 'tab list name' instead.");
                    case "script", "function" -> {
                        return null;
                    }
                    default -> {}
                }
            }
            return new Class<?>[] {String.class, Component.class};
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Component name = delta != null && delta.length > 0 ? Messages.component(delta[0]) : null;
            for (Object owner : Values.array(arguments[0])) {
                switch (owner) {
                    case Player player -> {
                        if (mark == 2) {
                            player.displayName(name);
                        }
                    }
                    case Entity entity -> {
                        entity.customName(name);
                        if (mark == 2 || mode == Mode.RESET) {
                            entity.setCustomNameVisible(name != null);
                        }
                        if (entity instanceof LivingEntity living) {
                            living.setRemoveWhenFarAway(name == null);
                        }
                    }
                    // An item's name is its display name.
                    case ItemType item -> item.editMeta(meta -> meta.displayName(name));
                    case ItemStack stack -> stack.editMeta(meta -> meta.displayName(name));
                    case Items.Slot slot -> {
                        ItemStack item = slot.item();
                        if (item != null && !item.getType().isAir()) {
                            item.editMeta(meta -> meta.displayName(name));
                            slot.set(item);
                        }
                    }
                    case AnyNamed named -> {
                        if (named.renames()) {
                            named.rename(name);
                        }
                    }
                    case Inventory inventory -> {
                        if (!inventory.getViewers().isEmpty() && inventory.getType().isCreatable()) {
                            Players.retitle(inventory, name);
                        }
                    }
                    // Anything else with a name, an addon's GUI among them.
                    case Nameable nameable -> nameable.customName(name);
                    case null, default -> {}
                }
            }
        }
    }

    // ExprAmount

    private static void amount(Registrar registrar) {
        registrar.expression("ExprAmount-old", "number", info -> {
            if (info.pattern() == 0) {
                return new Amount();
            }
            if (info.isSingle(0)) {
                String text = info.textOr("amount of it").replaceFirst("(?i)^(the )?(amount|number|size) of ", "");
                throw new SyntaxError("'(" + text + ")' can only ever have one value at most, thus the 'amount of ...'"
                        + " expression is useless. Use '... exists' instead to find out whether the expression has a"
                        + " value.");
            }
            return (context, args) -> (long) Values.array(args[0]).length;
        }, "[the] (amount|number|size) of %numbered%", "[the] (amount|number|size) of %objects%");
    }

    /** The amount of Skript's numbered things: set, added to and so on, where it can change. */
    private static final class Amount implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            AnyAmount amount = numbered(Values.single(arguments[0]));
            return amount == null ? 0L : amount.amount();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, RESET, DELETE, REMOVE -> new Class<?>[] {Number.class};
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            double by = delta != null && delta.length > 0 && delta[0] instanceof Number n ? n.doubleValue() : 1;
            for (Object value : Values.array(arguments[0])) {
                AnyAmount amount = numbered(value);
                if (amount == null || !amount.changesAmount()) {
                    continue;
                }
                switch (mode) {
                    case ADD -> amount.amount(amount.amount().doubleValue() + by);
                    case REMOVE -> amount.amount(amount.amount().doubleValue() - by);
                    default -> amount.amount(by);
                }
            }
        }
    }

    /** A numbered thing as Skript has it: item types, slots and queues are numbered themselves. */
    private static AnyAmount numbered(Object value) {
        return switch (value) {
            case AnyAmount amount -> amount;
            case ItemType item -> new AnyAmount() {
                @Override
                public Number amount() {
                    return item.amount();
                }

                @Override
                public boolean changesAmount() {
                    return true;
                }

                @Override
                public void amount(Number amount) {
                    item.setAmount(amount == null ? 0 : amount.intValue());
                }
            };
            case Items.Slot slot -> SLOT_AMOUNT.apply(slot);
            case ItemStack stack -> STACK_AMOUNT.apply(stack);
            case Collections.ScriptQueue queue -> queue::size;
            case null, default -> null;
        };
    }

    // CondIsEmpty

    private static void empty(Registrar registrar) {
        registrar.condition("CondIsEmpty-old", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> negated != Condition.check(Values.array(args[0]), info.and(0),
                    LegacyProperties::empty);
        }, Patterns.isProperty("empty", "inventories/slots/strings/numbered"));
    }

    private static boolean empty(Object value) {
        return switch (value) {
            case String text -> text.isEmpty();
            case Collections.ScriptQueue queue -> queue.isEmpty();
            case Inventory inventory -> inventory.isEmpty();
            case Items.Slot slot -> slot.item() == null || slot.item().getType().isAir();
            case AnyAmount amount -> amount.amount().doubleValue() == 0;
            default -> false;
        };
    }

    // CondContains

    private static void contains(Registrar registrar) {
        registrar.describe("CondContains-old", (info, args) -> args.get(0)
                + (info.pattern() % 2 == 1 ? " doesn't contain " : " contains ") + args.get(1));
        registrar.condition("CondContains-old", info -> {
            boolean negated = info.pattern() % 2 == 1;
            boolean inventories = info.pattern() <= 1;
            boolean explicitSingle = info.pattern() == 2 && (info.mark() & 1) == 0 || info.isSingle(0);
            boolean containersAnd = info.and(0);
            boolean itemsAnd = info.and(1);
            return (context, args) -> {
                Object[] containers = Values.array(args[0]);
                Object[] items = Values.array(args[1]);
                if (containers.length == 0) {
                    return negated;
                }
                boolean allInventories = inventories || java.util.Arrays.stream(containers).allMatch(Inventory.class::isInstance);
                if (allInventories) {
                    return negated != Condition.check(containers, containersAnd, container -> Condition.check(items,
                            itemsAnd, item -> Items.contains((Inventory) container, item)));
                }
                if (explicitSingle && java.util.Arrays.stream(containers).allMatch(String.class::isInstance)) {
                    return negated != Condition.check(containers, containersAnd, container -> Condition.check(items,
                            itemsAnd, item -> item instanceof String text && Logic.contains((String) container, text)));
                }
                // Whether the items are among the values.
                return negated != Condition.check(items, itemsAnd, item -> {
                    for (Object container : containers) {
                        if (Comparison.equal(item, container)) {
                            return true;
                        }
                    }
                    return false;
                });
            };
        }, "%inventories% (has|have) %itemtypes% [in [(the[ir]|his|her|its)] inventory]",
                "%inventories% (doesn't|does not|do not|don't) have %itemtypes% [in [(the[ir]|his|her|its)] inventory]",
                "%inventories/strings/objects% contain[(1¦s)] %itemtypes/strings/objects%",
                "%inventories/strings/objects% (doesn't|does not|do not|don't) contain %itemtypes/strings/objects%");
    }

    // ExprValue

    private static void value(Registrar registrar) {
        registrar.typedExpression("ExprValue", "object", info -> {
            ElementInfo.TypeArgument type = info.types().isEmpty() ? null : info.types().getFirst();
            return (context, args) -> {
                List<Object> values = new ArrayList<>();
                for (Object value : Values.array(args[0])) {
                    if (value instanceof AnyValued valued) {
                        Object converted = Configs.valueAs(valued.node(), type);
                        if (converted != null) {
                            values.add(converted);
                        }
                    }
                }
                return values.size() == 1 ? values.getFirst() : values.toArray();
            };
        }, "[the] <.+> value of %valued%", "[the] <.+> values of %valueds%", "%valued%'s <.+> value",
                "%valueds%'[s] <.+> values");
    }

    // ExprCoordinate and ExprXYZComponent

    private static void coordinates(Registrar registrar) {
        registrar.expression("ExprCoordinate", "number", info -> new Only(new WorldSyntax.Axis(info.hasTag("x") ? 0
                : info.hasTag("y") ? 1 : 2), org.bukkit.Location.class),
                Patterns.property("(x:x|y:y|z:z)(-| )(coord[inate]|pos[ition]|loc[ation])[s]", "locations"));
        registrar.expression("ExprXYZComponent", "number", info -> new Only(new WorldSyntax.Axis(info.hasTag("x") ? 0
                : info.hasTag("y") ? 1 : info.hasTag("z") ? 2 : 3), org.bukkit.util.Vector.class,
                org.joml.Quaternionf.class),
                Patterns.property("[vector|quaternion] (w:w|x:x|y:y|z:z) [component[s]]", "vectors/quaternions"));
    }

    /**
     * An axis of only some types' values, as each old element reads only its own: a vector held in a variable
     * has no x coordinate, and a location no x component.
     */
    private record Only(WorldSyntax.Axis axis, Class<?>... types) implements Expression, Changeable {
        private Object[] only(Object[] arguments) {
            return new Object[] {java.util.Arrays.stream(Values.array(arguments[0]))
                    .filter(value -> java.util.Arrays.stream(types).anyMatch(type -> type.isInstance(value)))
                    .toArray()};
        }

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return axis.get(context, only(arguments));
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return axis.acceptChange(mode);
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            axis.change(context, only(arguments), delta, mode);
        }
    }
}
