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
import java.util.ArrayDeque;
import org.bukkit.inventory.Inventory;

/** Skript's queues, and whether things are empty. */
final class Collections {

    private Collections() {}

    /** Skript's {@code queue}: values in order, taken from either end. */
    static final class ScriptQueue extends ArrayDeque<Object> {

        @Override
        public String toString() {
            return Values.text(toArray());
        }
    }

    static void register(Registrar registrar) {
        registrar.type("queue", "queue", ScriptQueue.class);
        registrar.cloner(ScriptQueue.class, queue -> queue);
        registrar.expression("ExprQueue", "queue", info -> (context, args) -> {
            ScriptQueue queue = new ScriptQueue();
            for (Object value : Values.array(args[0])) {
                if (value != null) {
                    queue.add(value);
                }
            }
            return queue;
        }, "[a] [new] queue [(of|with) %objects%]");
        registrar.expression("ExprDequeuedQueue", "object", info -> (context, args) -> {
            if (!(args[0] instanceof ScriptQueue queue)) {
                return null;
            }
            // Its elements in order, left in it: taking them out is what 'first element of' does.
            return queue.toArray();
        }, "(de|un)queued %queue%", "unrolled %queue%");
        registrar.expression("ExprQueueStartEnd", "object", info -> new StartEnd(info.hasTag("end")),
                "[the] (start|end:end) of %queue%", "%queue%'[s] (start|end:end)");
        registrar.changer(ScriptQueue.class, Changeable.Mode.ADD, (queue, values) -> {
            for (Object value : values) {
                queue.addLast(value);
            }
        });
        registrar.changer(ScriptQueue.class, Changeable.Mode.REMOVE, (queue, values) -> {
            for (Object value : values) {
                queue.remove(value);
            }
        });
        registrar.changer(ScriptQueue.class, Changeable.Mode.DELETE, (queue, values) -> queue.clear());

        registrar.condition("CondIsEmpty", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> {
                Object[] values = (Object[]) args[0];
                if (values.length == 0) {
                    // Nothing to check holds only when negated, as Skript's checks have it.
                    return negated;
                }
                for (Object value : values) {
                    boolean empty = switch (value) {
                        case String text -> text.isEmpty();
                        case java.util.Collection<?> collection -> collection.isEmpty();
                        case Inventory inventory -> inventory.isEmpty();
                        case Items.Slot slot -> slot.item() == null || slot.item().getType().isAir();
                        // Anything with an amount is empty without any, as Skript's numbered things.
                        case ItemType item -> item.amount() == 0;
                        default -> false;
                    };
                    if (empty == negated) {
                        return false;
                    }
                }
                return true;
            };
        }, "%objects% (is|are) empty", "%objects% (isn't|is not|aren't|are not) empty");
    }

    /** {@code the start of %queue%}: its first (or last) element, which stays; add to it to put values there. */
    private record StartEnd(boolean end) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return arguments[0] instanceof ScriptQueue queue ? (end ? queue.peekLast() : queue.peekFirst()) : null;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.ADD ? new Class<?>[] {Object.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(arguments[0] instanceof ScriptQueue queue) || delta == null) {
                return;
            }
            if (end) {
                for (Object value : delta) {
                    queue.addLast(value);
                }
            } else {
                for (int i = delta.length - 1; i >= 0; i--) {
                    queue.addFirst(delta[i]);
                }
            }
        }
    }
}
