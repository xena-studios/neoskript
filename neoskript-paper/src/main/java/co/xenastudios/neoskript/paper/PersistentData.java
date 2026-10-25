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
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.variables.storage.VariableCodecs;
import co.xenastudios.neoskript.runtime.Arithmetic;
import co.xenastudios.neoskript.runtime.Comparison;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

/**
 * Persistent data tags on items, entities, blocks with a block entity, worlds and chunks: reading,
 * setting and deleting them by key ({@code skript:test}).
 */
final class PersistentData {

    /** The types values are stored as, tried in this order when reading. */
    private static final List<PersistentDataType<?, ?>> TYPES = List.of(PersistentDataType.STRING,
            PersistentDataType.LONG, PersistentDataType.DOUBLE, PersistentDataType.BOOLEAN, PersistentDataType.INTEGER,
            PersistentDataType.FLOAT, PersistentDataType.BYTE, PersistentDataType.SHORT);

    private PersistentData() {}

    static void register(Registrar registrar) {
        String holders = "chunks/worlds/entities/blocks/itemtypes/offlineplayers";
        // 'persistent data tag "x" of Y', 'string data tag', 'strings data tag', 'list data tag': a value, or a list,
        // stored on Y, read as a type if one is named.
        registrar.typedExpression("ExprPersistentData", "object", info -> new Tag(info.types().isEmpty() ? null
                        : info.types().getFirst(), info.hasTag("list") || !info.types().isEmpty() && info.types().getFirst().plural()),
                "[the] [persistent] [<.+> ][list:list ]data (value|tag) %text% of %" + holders + "%",
                "%" + holders + "%'[s] [persistent] [<.+> ][list:list ]data (value|tag) %text%");
        registrar.condition("CondHasPersistentDataTag", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> {
                Object[] owners = (Object[]) args[0];
                Object[] keys = (Object[]) args[1];
                if (owners.length == 0 || keys.length == 0) {
                    return false;
                }
                for (Object owner : owners) {
                    boolean hasAll = true;
                    for (Object key : keys) {
                        NamespacedKey namespaced = key(key);
                        PersistentDataContainer container = read(owner);
                        if (namespaced == null || container == null || !container.has(namespaced)) {
                            hasAll = false;
                        }
                    }
                    if (hasAll == negated) {
                        return false;
                    }
                }
                return true;
            };
        }, Patterns.hasProperty("[persistent] data tag[s] %texts%", "objects"));
        registrar.expression("ExprAllPersistentDataKeys", "string", info -> (context, args) -> {
            List<Object> keys = new ArrayList<>();
            for (Object owner : (Object[]) args[0]) {
                PersistentDataContainer container = read(owner);
                if (container != null) {
                    container.getKeys().forEach(key -> keys.add(key.toString()));
                }
            }
            return keys.toArray();
        }, "[all [[of] the]] [persistent] data [tag] keys of %objects%", "[all of] %objects%'[s] [persistent] data [tag] keys");
    }

    private static final NamespacedKey TYPE = new NamespacedKey("neoskript", "type");
    private static final NamespacedKey DATA = new NamespacedKey("neoskript", "data");
    private static final NamespacedKey VALUE = new NamespacedKey("neoskript", "value");
    private static final PersistentDataType<java.util.List<PersistentDataContainer>, java.util.List<PersistentDataContainer>> LIST =
            PersistentDataType.LIST.dataContainers();

    /**
     * {@code [type] [list] data tag "skript:x" of %holders%}: a single value, or with {@code list} (or a plural
     * type) a list, which can be set, added to, taken from and deleted. Values other than texts, numbers and
     * booleans are stored with their storage codec.
     */
    private record Tag(co.xenastudios.neoskript.api.element.ElementInfo.TypeArgument type, boolean list)
            implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            NamespacedKey key = key(arguments[0]);
            List<Object> values = new ArrayList<>();
            for (Object owner : owners(arguments[1])) {
                PersistentDataContainer container = read(owner);
                if (key == null || container == null || !container.has(key)) {
                    continue;
                }
                boolean storedList = container.has(key, LIST);
                if (storedList != list) {
                    RuntimeErrors.report(mismatch(key, storedList));
                    continue;
                }
                if (list) {
                    // As Skript: elements of other types are skipped, with a warning naming their types.
                    java.util.Map<Class<?>, String> mismatches = new java.util.LinkedHashMap<>();
                    for (PersistentDataContainer element : container.get(key, LIST)) {
                        Object value = decode(element, VALUE);
                        if (value != null && (type == null || matches(value))) {
                            values.add(value);
                        } else if (value != null) {
                            mismatches.putIfAbsent(value.getClass(), typeName(value));
                        }
                    }
                    if (!mismatches.isEmpty()) {
                        List<String> names = new ArrayList<>(mismatches.values());
                        String joined = names.size() == 1 ? names.getFirst()
                                : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
                        RuntimeErrors.warning(mismatches.size() + " element(s) in tag '" + key + "' were of type(s) "
                                + joined + ", not the expected type " + type.name() + ". Skipping.");
                    }
                    continue;
                }
                Object value = decode(container, key);
                if (value != null && type != null && !matches(value)) {
                    RuntimeErrors.report("The data in tag '" + key + "' was of type " + typeName(value)
                            + ", not the expected type " + type.name() + ".");
                    continue;
                }
                if (value != null) {
                    values.add(value);
                }
            }
            return values.size() == 1 && !list ? values.getFirst() : values.toArray();
        }

        private boolean matches(Object value) {
            Class<?> wanted = type.javaClass();
            return wanted.isInstance(value) || Number.class.isAssignableFrom(wanted) && value instanceof Number
                    && (!type.id().equals("integer") || value instanceof Long || value instanceof Integer);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            if ((mode == Mode.ADD || mode == Mode.REMOVE) && type != null && !list) {
                // Added to a single value of a type by arithmetic, as in Skript: only timespans go with a date.
                List<Class<?>> right = co.xenastudios.neoskript.runtime.Arithmetic.rightTypes(
                        mode == Mode.ADD ? '+' : '-', type.javaClass());
                if (!right.isEmpty()) {
                    return right.toArray(Class<?>[]::new);
                }
            }
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {Object.class};
                case DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            NamespacedKey key = key(arguments[0]);
            if (key == null) {
                return;
            }
            Object[] given = delta == null ? new Object[0] : delta;
            for (Object value : given) {
                if (!storable(value)) {
                    RuntimeErrors.report("Skript cannot serialize " + plural(value) + " as persistent data!");
                    return;
                }
            }
            for (Object owner : owners(arguments[1])) {
                PersistentDataContainer current = read(owner);
                write(owner, container -> change(container, current, key, given, mode));
            }
        }

        private void change(PersistentDataContainer container, PersistentDataContainer current, NamespacedKey key,
                Object[] given, Mode mode) {
            if (mode == Mode.DELETE) {
                container.remove(key);
                return;
            }
            boolean exists = current != null && current.has(key);
            boolean storedList = exists && current.has(key, LIST);
            if (mode == Mode.SET) {
                if (list) {
                    setList(container, key, java.util.Arrays.asList(given));
                } else if (given.length > 0) {
                    encode(container, key, given[0]);
                }
                return;
            }
            if (exists && storedList != list) {
                RuntimeErrors.report(mismatch(key, storedList));
                return;
            }
            if (list) {
                List<Object> values = new ArrayList<>();
                if (exists) {
                    for (PersistentDataContainer element : current.get(key, LIST)) {
                        Object value = decode(element, VALUE);
                        if (value != null) {
                            values.add(value);
                        }
                    }
                }
                for (Object value : given) {
                    if (mode == Mode.ADD) {
                        values.add(value);
                    } else {
                        values.removeIf(stored -> Comparison.equal(stored, value));
                    }
                }
                setList(container, key, values);
                return;
            }
            Object value = exists ? decode(current, key) : null;
            for (Object by : given) {
                // Only what arithmetic allows: a number plus a number, a date plus a timespan.
                Object next = mode == Mode.ADD ? Arithmetic.add(value, by) : Arithmetic.subtract(value, by);
                if (next != null) {
                    value = next;
                }
            }
            if (value != null) {
                encode(container, key, value);
            }
        }

        private static void setList(PersistentDataContainer container, NamespacedKey key, List<Object> values) {
            List<PersistentDataContainer> elements = new ArrayList<>();
            for (Object value : values) {
                PersistentDataContainer element = container.getAdapterContext().newPersistentDataContainer();
                encode(element, VALUE, value);
                elements.add(element);
            }
            container.set(key, LIST, elements);
        }

        private static String mismatch(NamespacedKey key, boolean storedList) {
            return storedList
                    ? "The data in tag '" + key + "' is a list, not a single value. Use 'list data tag' instead of 'data tag'."
                    : "The data in tag '" + key + "' is a single value, not a list. Use 'data tag' instead of 'list data tag'.";
        }
    }

    /** Stores {@code value} under {@code key}: texts, numbers and booleans as such, anything else with its codec. */
    private static void encode(PersistentDataContainer container, NamespacedKey key, Object value) {
        switch (value) {
            case Boolean b -> container.set(key, PersistentDataType.BOOLEAN, b);
            case Long l -> container.set(key, PersistentDataType.LONG, l);
            case Integer i -> container.set(key, PersistentDataType.LONG, (long) i);
            case Number n -> container.set(key, PersistentDataType.DOUBLE, n.doubleValue());
            case String text -> container.set(key, PersistentDataType.STRING, text);
            default -> {
                VariableCodecs.Encoded encoded = VariableCodecs.current().encode(value);
                PersistentDataContainer compound = container.getAdapterContext().newPersistentDataContainer();
                compound.set(TYPE, PersistentDataType.STRING, encoded.type());
                compound.set(DATA, PersistentDataType.BYTE_ARRAY, encoded.bytes());
                container.set(key, PersistentDataType.TAG_CONTAINER, compound);
            }
        }
    }

    /** The value stored under {@code key}, or {@code null}. */
    private static Object decode(PersistentDataContainer container, NamespacedKey key) {
        if (container.has(key, PersistentDataType.TAG_CONTAINER)) {
            PersistentDataContainer compound = container.get(key, PersistentDataType.TAG_CONTAINER);
            String type = compound.get(TYPE, PersistentDataType.STRING);
            byte[] data = compound.get(DATA, PersistentDataType.BYTE_ARRAY);
            return type == null || data == null ? null : VariableCodecs.current().decode(type, data);
        }
        for (PersistentDataType<?, ?> type : TYPES) {
            if (container.has(key, type)) {
                return container.get(key, type);
            }
        }
        return null;
    }

    /** Whether {@code value} can be stored: a text, number or boolean, or something with a storage codec. */
    private static boolean storable(Object value) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return true;
        }
        try {
            return VariableCodecs.current() != null && VariableCodecs.current().encode(value) != null;
        } catch (RuntimeException unknown) {
            return false;
        }
    }

    /** How Skript names a value's type in messages: text, integer, number, boolean, or its storage name. */
    private static String typeName(Object value) {
        return switch (value) {
            case String text -> "text";
            case Long l -> "integer";
            case Integer i -> "integer";
            case Number n -> "number";
            case Boolean b -> "boolean";
            default -> {
                try {
                    yield VariableCodecs.current().encode(value).type();
                } catch (RuntimeException unknown) {
                    yield value.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
                }
            }
        };
    }

    /** A value's type in the plural, for messages: inventories. */
    private static String plural(Object value) {
        if (value instanceof org.bukkit.inventory.Inventory) {
            return "inventories";
        }
        String name = value.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).replaceFirst("^craft", "");
        return name.endsWith("y") ? name.substring(0, name.length() - 1) + "ies" : name + "s";
    }

    private static Object[] owners(Object value) {
        return value instanceof Object[] array ? array : value == null ? new Object[0] : new Object[] {value};
    }

    static NamespacedKey key(Object text) {
        return text == null ? null : NamespacedKey.fromString(Values.text(text).toLowerCase(java.util.Locale.ROOT));
    }

    /** The data of {@code owner} to read, or {@code null} if it has none. */
    private static PersistentDataContainer read(Object owner) {
        return switch (owner) {
            case ItemType item -> item.stack().getItemMeta() == null ? null
                    : item.stack().getItemMeta().getPersistentDataContainer();
            case ItemStack stack -> stack.getItemMeta() == null ? null : stack.getItemMeta().getPersistentDataContainer();
            case Block block -> block.getState() instanceof TileState state ? state.getPersistentDataContainer() : null;
            case PersistentDataHolder holder -> holder.getPersistentDataContainer();
            case null, default -> null;
        };
    }

    /** Changes the data of {@code owner}, saving it back where that's needed (items, block entities). */
    private static void write(Object owner, Consumer<PersistentDataContainer> change) {
        switch (owner) {
            case ItemType item -> item.editMeta(meta -> change.accept(meta.getPersistentDataContainer()));
            case ItemStack stack -> stack.editMeta(meta -> change.accept(meta.getPersistentDataContainer()));
            case Block block -> {
                BlockState state = block.getState();
                if (state instanceof TileState tile) {
                    change.accept(tile.getPersistentDataContainer());
                    tile.update();
                }
            }
            case Entity entity -> change.accept(entity.getPersistentDataContainer());
            case World world -> change.accept(world.getPersistentDataContainer());
            case Chunk chunk -> change.accept(chunk.getPersistentDataContainer());
            case PersistentDataHolder holder -> change.accept(holder.getPersistentDataContainer());
            case null, default -> {}
        }
    }
}
