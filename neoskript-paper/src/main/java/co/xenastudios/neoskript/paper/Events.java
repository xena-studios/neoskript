/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.ElementFactory;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.EventFilter;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

/** What the event files share: filters by the kinds of things written in an event, and blocks as they will be. */
final class Events {

    private Events() {}

    /** Registers an event without a filter. */
    static void simple(Registrar registrar, String id, Class<?> eventClass, String... patterns) {
        registrar.event(id, List.of(eventClass), null, patterns);
    }

    /** An event filter that only checks what it's given when it's written: {@code args[index]}. */
    static ElementFactory<Condition> optional(int index, java.util.function.BiPredicate<Object, Object[]> test) {
        return info -> (context, args) -> args[index] == null || test.test(context.event(), values(args[index]));
    }

    /** The parse mark matched, counting tags that are numbers ({@code 1:right}) as marks, as Skript does. */
    static int mark(ElementInfo info) {
        int mark = info.mark();
        for (String tag : info.tags()) {
            if (!tag.isEmpty() && tag.chars().allMatch(Character::isDigit)) {
                mark ^= Integer.parseInt(tag);
            }
        }
        return mark;
    }

    /** The values of an event's argument, or {@code null} if it wasn't written. */
    static Object[] values(Object argument) {
        return argument == null ? null : Values.array(argument);
    }

    /**
     * Whether {@code test} holds for the values as Skript checks a literal list: all of an and-list, any of an
     * or-list.
     */
    static boolean check(ElementInfo info, int index, Object[] values, Predicate<Object> test) {
        return Condition.check(values, info.and(index), test);
    }

    /** Whether {@code test} holds for any of the values, as events that read 'and' as 'or' check them. */
    static boolean any(Object[] values, Predicate<Object> test) {
        for (Object value : values) {
            if (value != null && test.test(value)) {
                return true;
            }
        }
        return false;
    }

    /** Whether an entity type written in an event is of {@code entity}. */
    static boolean isKind(Object kind, Entity entity) {
        return entity != null && kind instanceof EntityKind entityKind && entityKind.matches(entity);
    }

    /** Whether an item type written in an event is of {@code stack}, ignoring the amount, as Skript's isOfType. */
    static boolean isItem(Object type, ItemStack stack) {
        if (!(type instanceof ItemType item) || stack == null) {
            return false;
        }
        ItemStack pattern = item.stack();
        return stack.getType() == item.material() && (!pattern.hasItemMeta() || pattern.isSimilar(stack));
    }

    /** Whether an item type or block data written in an event is of a block that is {@code data}. */
    static boolean isBlock(Object type, BlockData data) {
        return switch (type) {
            case ItemType item -> data != null && data.getMaterial() == item.material();
            case BlockData pattern -> data != null && data.matches(pattern);
            default -> false;
        };
    }

    /** An event's filter that also chooses which of its classes it listens to (see {@link EventFilter}). */
    static EventFilter narrowed(List<Class<?>> classes, Condition check) {
        return EventFilter.of(classes, check);
    }

    /** Fails parsing with Skript's {@code message}. */
    static SyntaxError error(String message) {
        return new SyntaxError(message);
    }

    /**
     * {@code state} as a block, as Skript's block state blocks: what's there or will be, though it isn't yet.
     * Its type, data and state are the state's; everything else is the block's.
     */
    static Block stateBlock(BlockState state) {
        if (state == null) {
            return null;
        }
        Block block = state.getBlock();
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getType" -> state.getType();
            case "getBlockData" -> state.getBlockData();
            case "getState" -> args == null || args.length == 0 ? state : invoke(block, method, args);
            case "equals" -> args[0] == proxy || block.equals(args[0]);
            case "hashCode" -> block.hashCode();
            case "toString" -> "StateBlock{" + state + "}";
            default -> invoke(block, method, args);
        };
        return (Block) Proxy.newProxyInstance(Events.class.getClassLoader(), new Class<?>[] {Block.class}, handler);
    }

    /** {@code state} as a block (see {@link #stateBlock}) with its type changed to {@code type}. */
    static Block stateBlock(Block block, org.bukkit.Material type) {
        BlockState state = block.getState();
        state.setType(type);
        return stateBlock(state);
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
