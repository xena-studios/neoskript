/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.registration.Registrar;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

/**
 * Picking with the pick block key, as Skript's EvtPlayerPickItem and ExprPickedItem: Paper's pick block and pick
 * entity events, which only servers from 1.21.5 have. As in Skript, the syntax is only there on those servers; their
 * classes are looked up by name, as the API this is built against is older.
 */
final class PickItems {

    private PickItems() {}

    private static final String PICK_BLOCK = "io.papermc.paper.event.player.PlayerPickBlockEvent";
    private static final String PICK_ENTITY = "io.papermc.paper.event.player.PlayerPickEntityEvent";

    static void register(Registrar registrar) {
        Class<?> pickBlock;
        Class<?> pickEntity;
        Method getBlock;
        Method getEntity;
        Method getPickItemStack;
        try {
            pickBlock = Class.forName(PICK_BLOCK);
            pickEntity = Class.forName(PICK_ENTITY);
            getBlock = pickBlock.getMethod("getBlock");
            getEntity = pickEntity.getMethod("getEntity");
            getPickItemStack = Entity.class.getMethod("getPickItemStack");
        } catch (ReflectiveOperationException | LinkageError older) {
            return;
        }
        Picks picks = new Picks(pickBlock, pickEntity, getBlock, getEntity, getPickItemStack);

        registrar.event("EvtPlayerPickItem", List.of(pickBlock, pickEntity), info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                Object event = context.event();
                return switch (pattern) {
                    case 0 -> true;
                    case 1 -> pickBlock.isInstance(event);
                    case 2 -> pickEntity.isInstance(event);
                    default -> {
                        Block block = picks.block(event);
                        Entity entity = picks.entity(event);
                        yield Events.check(info, 0, Events.values(args[0]), type -> switch (type) {
                            case EntityKind kind when entity != null -> kind.matches(entity);
                            case ItemType item when entity != null -> Events.isItem(item, picks.item(entity));
                            case ItemType item -> block != null && item.material() == block.getType();
                            case BlockData data when block != null -> block.getBlockData().matches(data);
                            default -> false;
                        });
                    }
                };
            };
        }, "[player] pick[ing] [of] [an|any] item", "[player] pick[ing] [of] [a|any] block",
                "[player] pick[ing] [of] [an|any] entity", "[player] pick[ing] [of] %entitytype/itemtype/blockdata%");

        // The slot the picked item comes from (none if it isn't in the inventory), and the hotbar slot it goes to.
        registrar.eventValue(PlayerPickItemEvent.class, "slot", -1, event -> event.getSourceSlot() == -1 ? null
                : new Items.Slot(event.getPlayer().getInventory(), event.getSourceSlot()));
        registrar.eventValue(PlayerPickItemEvent.class, "slot",
                event -> new Items.Slot(event.getPlayer().getInventory(), event.getTargetSlot()));

        registrar.expression("ExprPickedItem", "object", info -> {
            int mark = Events.mark(info);
            restrict(info, mark);
            return (context, args) -> {
                Object event = context.event();
                return switch (mark) {
                    case 1 -> picks.block(event);
                    case 2 -> picks.entity(event);
                    default -> {
                        Block block = picks.block(event);
                        if (block != null) {
                            yield block.getType().isItem() ? new ItemType(new ItemStack(block.getType()), false) : null;
                        }
                        Entity entity = picks.entity(event);
                        ItemStack stack = entity == null ? null : picks.item(entity);
                        yield stack == null || stack.getType().isAir() ? null : new ItemType(stack, false);
                    }
                };
            };
        }, "[the] picked (item|1:block|2:entity)");
    }

    /**
     * Fails parsing outside the pick events. Skript names the events syntax can be used in, but none of its events
     * is registered for the pick item event class itself, so it names none.
     */
    private static void restrict(ElementInfo info, int mark) {
        for (Class<?> eventClass : info.eventClasses()) {
            if (PlayerPickItemEvent.class.isAssignableFrom(eventClass)) {
                return;
            }
        }
        String what = switch (mark) {
            case 1 -> "block";
            case 2 -> "entity";
            default -> "item";
        };
        throw new SyntaxError("'the picked " + what + "' can only be used in ");
    }

    /** Reads the newer events' values. */
    private record Picks(Class<?> pickBlock, Class<?> pickEntity, Method getBlock, Method getEntity,
            Method getPickItemStack) {

        Block block(Object event) {
            return pickBlock.isInstance(event) ? (Block) invoke(getBlock, event) : null;
        }

        Entity entity(Object event) {
            return pickEntity.isInstance(event) ? (Entity) invoke(getEntity, event) : null;
        }

        /** The item picking {@code entity} gives, as a spawn egg for a pig. */
        ItemStack item(Entity entity) {
            return (ItemStack) invoke(getPickItemStack, entity);
        }

        private static Object invoke(Method method, Object target) {
            try {
                return method.invoke(target);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
