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
        registrar.expression("ExprPersistentData", "object", info -> new Tag(),
                "[the] [persistent] data (value|tag) %text% of %" + holders + "%",
                "%" + holders + "%'[s] [persistent] data (value|tag) %text%");
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

    /** {@code persistent data tag "skript:x" of %holders%}, which can be set and deleted. */
    private static final class Tag implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            NamespacedKey key = key(arguments[0]);
            List<Object> values = new ArrayList<>();
            for (Object owner : owners(arguments[1])) {
                PersistentDataContainer container = read(owner);
                if (key == null || container == null) {
                    continue;
                }
                for (PersistentDataType<?, ?> type : TYPES) {
                    if (container.has(key, type)) {
                        values.add(container.get(key, type));
                        break;
                    }
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {Object.class} : mode == Mode.DELETE ? new Class<?>[0] : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            NamespacedKey key = key(arguments[0]);
            if (key == null) {
                return;
            }
            Object value = delta == null || delta.length == 0 ? null : delta[0];
            for (Object owner : owners(arguments[1])) {
                write(owner, container -> {
                    if (value == null) {
                        container.remove(key);
                    } else {
                        set(container, key, value);
                    }
                });
            }
        }
    }

    private static void set(PersistentDataContainer container, NamespacedKey key, Object value) {
        switch (value) {
            case Boolean b -> container.set(key, PersistentDataType.BOOLEAN, b);
            case Long l -> container.set(key, PersistentDataType.LONG, l);
            case Integer i -> container.set(key, PersistentDataType.LONG, (long) i);
            case Number n -> container.set(key, PersistentDataType.DOUBLE, n.doubleValue());
            default -> container.set(key, PersistentDataType.STRING, Values.text(value));
        }
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
