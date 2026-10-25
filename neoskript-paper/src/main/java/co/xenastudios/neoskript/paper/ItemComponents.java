/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import co.xenastudios.neoskript.runtime.ScriptFeatures;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Skript's item components (its {@code itemcomponents} module): equippable components, which say how an item
 * is worn. They're experimental in Skript, so scripts need {@code using equippable components}.
 */
@SuppressWarnings("UnstableApiUsage")
final class ItemComponents {

    private ItemComponents() {}

    private static final String EXPERIMENT = "equippable components";

    /**
     * Skript's {@code ComponentWrapper}: a component of an item, or one on its own. One of an item reads and
     * changes the item it came from (an item type, an item stack or a slot), so changes to it are the item's.
     */
    abstract static class ItemComponent<T> {

        /** The item type, item stack or slot the component is of, or {@code null} for one on its own. */
        private final Object source;
        private T component;

        ItemComponent(Object source, T component) {
            this.source = source;
            this.component = component;
        }

        /** The component as it is now: the item's, if it's of an item. */
        T get() {
            ItemStack stack = stack();
            return stack == null ? component : read(stack);
        }

        private ItemStack stack() {
            return switch (source) {
                case ItemType item -> item.stack();
                case ItemStack stack -> stack;
                case Items.Slot slot -> slot.item();
                case null, default -> null;
            };
        }

        /** Makes {@code changed} the component, on the item too if it's of one. */
        void apply(T changed) {
            component = changed;
            switch (source) {
                case ItemType item -> item.editStacks(stack -> write(stack, changed));
                case ItemStack stack -> write(stack, changed);
                case Items.Slot slot -> {
                    ItemStack stack = slot.item();
                    if (stack != null) {
                        write(stack, changed);
                        slot.set(stack);
                    }
                }
                case null, default -> {}
            }
        }

        abstract T read(ItemStack stack);

        abstract void write(ItemStack stack, T component);

        /** A copy on its own, which changes nothing else when changed. */
        abstract ItemComponent<T> copy();
    }

    /** Skript's {@code EquippableWrapper}. */
    static final class EquippableComponent extends ItemComponent<Equippable> {

        EquippableComponent(Object source) {
            super(source, null);
        }

        EquippableComponent(Equippable component) {
            super(null, component);
        }

        @Override
        Equippable read(ItemStack stack) {
            Equippable equippable = stack.getData(DataComponentTypes.EQUIPPABLE);
            return equippable != null ? equippable : Equippable.equippable(EquipmentSlot.HEAD).build();
        }

        @Override
        void write(ItemStack stack, Equippable component) {
            stack.setData(DataComponentTypes.EQUIPPABLE, component);
        }

        @Override
        EquippableComponent copy() {
            Equippable base = get();
            return new EquippableComponent(builder(base, base.slot()).build());
        }

        /** Changes the component, as Skript's {@code editBuilder} does. */
        void edit(java.util.function.Consumer<Equippable.Builder> change) {
            Equippable base = get();
            Equippable.Builder builder = builder(base, base.slot());
            change.accept(builder);
            apply(builder.build());
        }

        @Override
        public String toString() {
            return "equippable component";
        }
    }

    // Parts of the component servers before 1.21.5 and 1.21.6 don't have, found when there.
    private static final Method EQUIP_ON_INTERACT = method(Equippable.class, "equipOnInteract");
    private static final Method SET_EQUIP_ON_INTERACT = method(Equippable.Builder.class, "equipOnInteract", boolean.class);
    private static final Method CAN_BE_SHEARED = method(Equippable.class, "canBeSheared");
    private static final Method SET_CAN_BE_SHEARED = method(Equippable.Builder.class, "canBeSheared", boolean.class);
    private static final Method SHEAR_SOUND = method(Equippable.class, "shearSound");
    private static final Method SET_SHEAR_SOUND = method(Equippable.Builder.class, "shearSound", Key.class);

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Object call(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A builder of a copy of {@code base} with its slot {@code slot}, as Skript's {@code clone(slot)}. */
    private static Equippable.Builder builder(Equippable base, EquipmentSlot slot) {
        Equippable.Builder builder = Equippable.equippable(slot)
                .allowedEntities(base.allowedEntities())
                .cameraOverlay(base.cameraOverlay())
                .damageOnHurt(base.damageOnHurt())
                .dispensable(base.dispensable())
                .equipSound(base.equipSound())
                .swappable(base.swappable())
                .assetId(base.assetId());
        if (EQUIP_ON_INTERACT != null && SET_EQUIP_ON_INTERACT != null) {
            call(SET_EQUIP_ON_INTERACT, builder, call(EQUIP_ON_INTERACT, base));
        }
        if (CAN_BE_SHEARED != null && SET_CAN_BE_SHEARED != null) {
            call(SET_CAN_BE_SHEARED, builder, call(CAN_BE_SHEARED, base));
        }
        if (SHEAR_SOUND != null && SET_SHEAR_SOUND != null) {
            call(SET_SHEAR_SOUND, builder, call(SHEAR_SOUND, base));
        }
        return builder;
    }

    /** The equippable component of an item, slot, or component; {@code null} for anything else. */
    private static EquippableComponent of(Object value) {
        return switch (value) {
            case EquippableComponent component -> component;
            case ItemType item -> new EquippableComponent(item);
            case ItemStack stack -> new EquippableComponent(stack);
            case Items.Slot slot when slot.item() != null -> new EquippableComponent(slot);
            case null, default -> null;
        };
    }

    private static List<EquippableComponent> components(Object values) {
        List<EquippableComponent> components = new ArrayList<>();
        for (Object value : Property.of(values)) {
            EquippableComponent component = of(value);
            if (component != null) {
                components.add(component);
            }
        }
        return components;
    }

    /** Fails to parse, as Skript's experimental syntax does, if the script doesn't use the experiment. */
    private static void experimental() {
        if (!ScriptFeatures.compilingUses(EXPERIMENT)) {
            throw new SyntaxError("This element is experimental. To use this, enable '" + EXPERIMENT + "'.");
        }
    }

    static void register(Registrar registrar) {
        registrar.type("itemcomponent", "item component", ItemComponent.class);
        registrar.type("equippablecomponent", "equippable component", EquippableComponent.class);
        registrar.subtype("equippablecomponent", "itemcomponent");
        registrar.text(ItemComponent.class, component -> "item component");
        registrar.text(EquippableComponent.class, component -> "equippable component");
        registrar.converter(ItemStack.class, EquippableComponent.class, EquippableComponent::new);
        registrar.converter(ItemType.class, EquippableComponent.class, EquippableComponent::new);
        registrar.converter(Items.Slot.class, EquippableComponent.class, slot -> slot.item() == null ? null
                : new EquippableComponent(slot));

        registrar.expression("ExprItemCompCopy", "itemcomponent", info -> (context, args) -> {
            List<Object> copies = new ArrayList<>();
            for (Object value : Property.of(args[0])) {
                if (value instanceof ItemComponent<?> component) {
                    copies.add(component.copy());
                }
            }
            return Property.single(copies);
        }, "[the|a[n]] [item] component copy of %itemcomponents%", "[the] [item] component copies of %itemcomponents%");

        registerExpressions(registrar);
        registerConditions(registrar);
        registerEffects(registrar);
    }

    private static void registerExpressions(Registrar registrar) {
        // 'a blank equippable component': a new one, which its section can set up.
        registrar.expression("ExprSecBlankEquipComp", "equippablecomponent", info -> {
            experimental();
            return (context, args) -> new EquippableComponent(Equippable.equippable(EquipmentSlot.HEAD).build());
        }, "a (blank|empty) equippable component");
        registrar.sectionExpression("ExprSecBlankEquipComp", "equippablecomponent");

        registrar.expression("ExprEquippableComponent", "equippablecomponent", info -> {
            experimental();
            return new EquippableOfItems();
            // Item stacks too: an item stack in a variable is its own, not a copy made an item type, as in Skript.
        }, Patterns.property("equippable component[s]", "slots/itemtypes/itemstacks"));

        keyProperty(registrar, "ExprEquipCompCameraOverlay", "camera overlay", Equippable::cameraOverlay,
                (component, key) -> component.edit(builder -> builder.cameraOverlay(key)));
        keyProperty(registrar, "ExprEquipCompModel", "equipped (model|asset) (key|id)", Equippable::assetId,
                (component, key) -> component.edit(builder -> builder.assetId(key)));
        soundProperty(registrar, "ExprEquipCompEquipSound", "equip sound", Equippable::equipSound,
                (component, key) -> component.edit(builder -> builder.equipSound(key)));
        if (SHEAR_SOUND != null && SET_SHEAR_SOUND != null) {
            soundProperty(registrar, "ExprEquipCompShearSound", "shear[ed [off]] sound",
                    equippable -> (Key) call(SHEAR_SOUND, equippable),
                    (component, key) -> component.edit(builder -> call(SET_SHEAR_SOUND, builder, key)));
        }

        registrar.expression("ExprEquipCompSlot", "equipmentslot", info -> {
            experimental();
            return new ComponentProperty(component -> component.get().slot(), EquipmentSlot.class, false,
                    (component, values) -> {
                        if (values.length > 0 && values[0] instanceof EquipmentSlot slot) {
                            component.apply(builder(component.get(), slot).build());
                        }
                    });
        }, Property.defaultPatterns("equipment slot", "equippablecomponents"));

        registrar.expression("ExprEquipCompEntities", "entitytype", info -> {
            experimental();
            return new AllowedEntities();
        }, Property.defaultPatterns("allowed entities", "equippablecomponents"));
    }

    /** A property of components which is a namespaced key, as text: set and deleted. */
    private static void keyProperty(Registrar registrar, String id, String property, Function<Equippable, Key> getter,
            BiConsumer<EquippableComponent, Key> setter) {
        registrar.expression(id, "string", info -> {
            experimental();
            return new ComponentProperty(component -> {
                Key key = getter.apply(component.get());
                return key == null ? null : key.toString();
            }, String.class, true, (component, values) -> {
                NamespacedKey key = null;
                if (values.length > 0 && values[0] instanceof String text) {
                    key = namespacedKey(text);
                    if (key == null) {
                        return;
                    }
                }
                setter.accept(component, key);
            });
        }, Property.defaultPatterns(property, "equippablecomponents"));
    }

    /** A property of components which is a sound, by its key: set and deleted. */
    private static void soundProperty(Registrar registrar, String id, String property, Function<Equippable, Key> getter,
            BiConsumer<EquippableComponent, Key> setter) {
        registrar.expression(id, "string", info -> {
            experimental();
            return new ComponentProperty(component -> {
                Key key = getter.apply(component.get());
                return key == null ? null : key.toString();
            }, String.class, true, (component, values) -> {
                Key key = null;
                if (values.length > 0 && values[0] instanceof String text) {
                    key = sound(text);
                    if (key == null) {
                        RuntimeErrors.report("Could not find a sound with the id '" + text + "'.");
                        return;
                    }
                }
                setter.accept(component, key);
            });
        }, Property.defaultPatterns(property, "equippablecomponents"));
    }

    /** The key of the sound {@code text} names, by its enum name or its key, as Skript's {@code SoundUtils}. */
    @SuppressWarnings({"removal", "deprecation"})
    private static Key sound(String text) {
        NamespacedKey key = null;
        try {
            key = org.bukkit.Sound.valueOf(text.toUpperCase(java.util.Locale.ENGLISH)).getKey();
        } catch (RuntimeException ignored) {
            key = NamespacedKey.fromString(text.toLowerCase(java.util.Locale.ENGLISH));
        }
        if (key == null) {
            return null;
        }
        org.bukkit.Sound sound = Registry.SOUNDS.get(key);
        return sound == null ? null : Registry.SOUNDS.getKey(sound);
    }

    /** {@code text} as a namespaced key, or {@code null} with Skript's error if it can't be one. */
    static NamespacedKey namespacedKey(String text) {
        String format = "A namespaced key can be formatted as 'namespace:id' or 'id'. It can only contain one ':' to "
                + "separate the namespace and the id. Only alphanumeric characters, periods, underscores, and dashes "
                + "can be used.";
        String problem = null;
        String[] split = text.split(":");
        String key = split.length == 2 ? split[1] : split.length == 1 ? split[0] : "";
        if (text.length() > Short.MAX_VALUE) {
            problem = "A namespaced key can not be longer than " + Short.MAX_VALUE + " characters.";
        } else if (split.length > 2) {
            problem = "A namespaced key can not have more than one ':'.";
        } else if (key.isEmpty()) {
            problem = "The key cannot be empty.";
        } else {
            for (char character : key.toCharArray()) {
                if (!validNamespaceChar(character) && character != '/') {
                    problem = "Invalid character '" + character + "'.";
                    break;
                }
            }
            if (problem == null && split.length == 2) {
                for (char character : split[0].toCharArray()) {
                    if (!validNamespaceChar(character)) {
                        problem = "Invalid character '" + character + "'.";
                        break;
                    }
                }
            }
        }
        if (problem != null) {
            RuntimeErrors.report(problem + ". " + format);
            return null;
        }
        return split.length == 2 && !split[0].isEmpty() ? new NamespacedKey(split[0], key) : NamespacedKey.minecraft(key);
    }

    private static boolean validNamespaceChar(char character) {
        return character >= 'a' && character <= 'z' || character >= '0' && character <= '9' || character == '.'
                || character == '_' || character == '-';
    }

    /** A property of equippable components, changed with {@code set} (and {@code delete} if it can be). */
    private record ComponentProperty(Function<EquippableComponent, Object> getter, Class<?> valueClass, boolean deletable,
            BiConsumer<EquippableComponent, Object[]> setter) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (EquippableComponent component : components(arguments[0])) {
                Object value = getter.apply(component);
                if (value != null) {
                    values.add(value);
                }
            }
            return Property.single(values);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET || deletable && mode == Mode.DELETE ? new Class<?>[] {valueClass} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Object[] values = delta == null ? new Object[0] : delta;
            if (mode == Mode.SET && values.length == 0 && !deletable) {
                return;
            }
            for (EquippableComponent component : components(arguments[0])) {
                setter.accept(component, values);
            }
        }
    }

    /** {@code the allowed entities of %equippablecomponents%}: set, added to, taken from and cleared. */
    private static final class AllowedEntities implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> kinds = new ArrayList<>();
            for (EquippableComponent component : components(arguments[0])) {
                for (EntityType type : allowed(component.get())) {
                    kinds.add(EntityKind.of(type));
                }
            }
            return kinds.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, DELETE, REMOVE, ADD -> new Class<?>[] {EntityKind.class};
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            List<EntityType> given = new ArrayList<>();
            for (Object value : delta == null ? new Object[0] : delta) {
                if (value instanceof EntityKind kind && kind.type() != null) {
                    given.add(kind.type());
                }
            }
            for (EquippableComponent component : components(arguments[0])) {
                List<EntityType> current = new ArrayList<>(allowed(component.get()));
                switch (mode) {
                    case SET -> {
                        current.clear();
                        current.addAll(given);
                    }
                    case ADD -> current.addAll(given);
                    case REMOVE -> current.removeAll(given);
                    default -> current.clear();
                }
                RegistryKeySet<EntityType> set = RegistrySet.keySetFromValues(RegistryKey.ENTITY_TYPE, current);
                component.edit(builder -> builder.allowedEntities(set));
            }
        }

        private static java.util.Collection<EntityType> allowed(Equippable equippable) {
            RegistryKeySet<EntityType> set = equippable.allowedEntities();
            return set == null || set.isEmpty() ? List.of() : set.resolve(Registry.ENTITY_TYPE);
        }
    }

    /** {@code the equippable component of %slots/itemtypes%}: the items' own, which can be set, deleted and reset. */
    private static final class EquippableOfItems implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> components = new ArrayList<>();
            for (Object value : Property.of(arguments[0])) {
                if (value instanceof ItemType || value instanceof ItemStack || value instanceof Items.Slot) {
                    EquippableComponent component = of(value);
                    if (component != null) {
                        components.add(component);
                    }
                }
            }
            return Property.single(components);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, DELETE, RESET -> new Class<?>[] {EquippableComponent.class};
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Equippable equippable = delta != null && delta.length > 0 && delta[0] instanceof EquippableComponent component
                    ? component.get() : null;
            java.util.function.Consumer<ItemStack> change = stack -> {
                switch (mode) {
                    case SET -> {
                        if (equippable != null) {
                            stack.setData(DataComponentTypes.EQUIPPABLE, equippable);
                        }
                    }
                    case DELETE -> stack.unsetData(DataComponentTypes.EQUIPPABLE);
                    default -> stack.resetData(DataComponentTypes.EQUIPPABLE);
                }
            };
            for (Object value : Property.of(arguments[0])) {
                if (value instanceof ItemType item) {
                    item.editStacks(change);
                } else if (value instanceof ItemStack stack) {
                    change.accept(stack);
                } else if (value instanceof Items.Slot slot && slot.item() != null) {
                    ItemStack stack = slot.item();
                    change.accept(stack);
                    slot.set(stack);
                }
            }
        }
    }

    private static void registerConditions(Registrar registrar) {
        String hurt = "(lose durability|be damaged) (on [wearer['s]] injury|when [[the] wearer [is]] (hurt|injured|damaged))";
        condition(registrar, "CondEquipCompDamage", Equippable::damageOnHurt,
                "%equippablecomponents% will " + hurt, "%equippablecomponents% (will not|won't) " + hurt);
        condition(registrar, "CondEquipCompDispensable", Equippable::dispensable,
                "%equippablecomponents% can be dispensed", "%equippablecomponents% (can't|cannot|can not) be dispensed",
                "%equippablecomponents% (is|are) (able to be dispensed|dispensable)",
                "%equippablecomponents% (isn't|is not|aren't|are not) (able to be dispensed|dispensable)");
        if (EQUIP_ON_INTERACT != null) {
            condition(registrar, "CondEquipCompInteract", equippable -> (boolean) call(EQUIP_ON_INTERACT, equippable),
                    "%equippablecomponents% can be (equipped|put) on[to] entities",
                    "%equippablecomponents% (can't|cannot|can not) be (equipped|put) on[to] entities");
        }
        if (CAN_BE_SHEARED != null) {
            condition(registrar, "CondEquipCompShearable", equippable -> (boolean) call(CAN_BE_SHEARED, equippable),
                    "%equippablecomponents% can be sheared off [of entities]",
                    "%equippablecomponents% (can't|cannot|can not) be sheared off [of entities]");
        }
        String swap = "swap equipment [on right click|when right clicked]";
        condition(registrar, "CondEquipCompSwapEquipment", Equippable::swappable,
                "%equippablecomponents% can " + swap, "%equippablecomponents% (can't|cannot|can not) " + swap);
    }

    /** A condition on components, whose odd patterns are negated. */
    private static void condition(Registrar registrar, String id, Predicate<Equippable> test, String... patterns) {
        registrar.condition(id, info -> {
            experimental();
            boolean negated = info.pattern() % 2 == 1;
            boolean and = info.and(0);
            return (context, args) -> {
                List<EquippableComponent> components = components(args[0]);
                if (components.isEmpty()) {
                    return negated;
                }
                return Condition.check(components.toArray(), and,
                        value -> test.test(((EquippableComponent) value).get()) != negated);
            };
        }, patterns);
    }

    private static void registerEffects(Registrar registrar) {
        String hurt = "(on [wearer['s]] injury|when [[the] wearer [is]] (hurt|injured|damaged))";
        effect(registrar, "EffEquipCompDamageable", pattern -> pattern <= 1, (builder, value) -> builder.damageOnHurt(value),
                "(make|let) %equippablecomponents% (lose durability|be damaged) " + hurt,
                "(allow|force) %equippablecomponents% to (lose durability|be damaged) " + hurt,
                "make %equippablecomponents% not (lose durability|be damaged) " + hurt,
                "(disallow|prevent) %equippablecomponents% from (lose durability|being damaged) " + hurt);
        effect(registrar, "EffEquipCompDispensable", pattern -> pattern < 3, (builder, value) -> builder.dispensable(value),
                "allow %equippablecomponents% to be dispensed", "make %equippablecomponents% dispensable",
                "let %equippablecomponents% be dispensed", "(block|prevent|disallow) %equippablecomponents% from being dispensed",
                "make %equippablecomponents% not dispensable");
        if (SET_EQUIP_ON_INTERACT != null) {
            effect(registrar, "EffEquipCompInteract", pattern -> pattern < 3,
                    (builder, value) -> call(SET_EQUIP_ON_INTERACT, builder, value),
                    "allow %equippablecomponents% to be equipped on[to] entities",
                    "make %equippablecomponents% equippable on[to] entities",
                    "let %equippablecomponents% be equipped on[to] entities",
                    "(block|prevent|disallow) %equippablecomponents% from being equipped on[to] entities",
                    "make %equippablecomponents% not equippable on[to] entities");
        }
        if (SET_CAN_BE_SHEARED != null) {
            effect(registrar, "EffEquipCompShearable", pattern -> pattern == 0,
                    (builder, value) -> call(SET_CAN_BE_SHEARED, builder, value),
                    "allow %equippablecomponents% to be sheared off [of entities]",
                    "(disallow|prevent) %equippablecomponents% from being sheared off [of entities]");
        }
        String swap = "swap equipment [on right click|when right clicked]";
        effect(registrar, "EffEquipCompSwapEquipment", pattern -> pattern < 2, (builder, value) -> builder.swappable(value),
                "(allow|force) %equippablecomponents% to " + swap, "(make|let) %equippablecomponents% " + swap,
                "(block|prevent|disallow) %equippablecomponents% from swapping equipment [on right click|when right clicked]",
                "make %equippablecomponents% not " + swap);
    }

    /** An effect turning a flag of components on or off, on by the patterns {@code on} accepts. */
    private static void effect(Registrar registrar, String id, java.util.function.IntPredicate on,
            BiConsumer<Equippable.Builder, Boolean> set, String... patterns) {
        registrar.effect(id, info -> {
            experimental();
            boolean value = on.test(info.pattern());
            return (context, args) -> {
                for (EquippableComponent component : components(args[0])) {
                    component.edit(builder -> set.accept(builder, value));
                }
            };
        }, patterns);
    }
}
