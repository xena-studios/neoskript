/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Timespan;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

/** World borders: a world's or a player's border, its size, center, damage and warnings, and resizing it. */
final class Borders {

    private Borders() {}

    static void register(Registrar registrar) {
        registrar.type("worldborder", "world border", WorldBorder.class);
        registrar.text(WorldBorder.class, border -> "world border of " + (border.getWorld() == null ? "<none>"
                : border.getWorld().getName()));

        // 'a worldborder': a new one, for players; its section sets it up.
        registrar.expression("ExprSecCreateWorldBorder", "worldborder", info -> (context, args) -> Bukkit.createWorldBorder(),
                "(a|[a] virtual) world[ ]border");
        registrar.sectionExpression("ExprSecCreateWorldBorder", "worldborder");
        registrar.expression("ExprWorldBorder", "worldborder", info -> (context, args) -> {
            List<Object> borders = new ArrayList<>();
            Object[] owners = args[0] == null ? new Object[] {Bukkit.getWorlds().getFirst()} : (Object[]) args[0];
            for (Object owner : owners) {
                if (owner instanceof World world) {
                    borders.add(world.getWorldBorder());
                } else if (owner instanceof Player player) {
                    borders.add(player.getWorldBorder() != null ? player.getWorldBorder()
                            : player.getWorld().getWorldBorder());
                }
            }
            return borders.size() == 1 ? borders.getFirst() : borders.toArray();
        }, "[the] world[ ]border [of %worlds/players%]", "%worlds/players%'[s] world[ ]border");
        // 'reset worldborder of world "world"': back to Minecraft's defaults.
        registrar.changer(org.bukkit.WorldBorder.class, co.xenastudios.neoskript.api.element.Changeable.Mode.RESET,
                (border, values) -> border.reset());

        // Each with the limits Minecraft keeps it in, what resetting it goes back to, and whether it can be infinite.
        property(registrar, "ExprWorldBorderSize", "number", "world[ ]border (size|diameter|radius:radius)", Number.class,
                border -> border.getSize(), (border, size) -> border.setSize(size.doubleValue()), true, 59999968.0, 1, 59999968,
                "size", true);
        property(registrar, "ExprWorldBorderCenter", "location", "world[ ]border (center|middle)", Location.class,
                WorldBorder::getCenter, (border, center) -> {
                    if (Double.isNaN(center.getX()) || Double.isNaN(center.getZ())) {
                        co.xenastudios.neoskript.runtime.RuntimeErrors.report(
                                "Your location can't have a NaN value as one of its components");
                        return;
                    }
                    border.setCenter(new Location(border.getWorld(), Math.max(-29999984, Math.min(29999984, center.getX())), 0,
                            Math.max(-29999984, Math.min(29999984, center.getZ()))));
                }, false, null, 0, 0, "center", true);
        property(registrar, "ExprWorldBorderDamageBuffer", "number", "world[ ]border damage buffer", Number.class,
                WorldBorder::getDamageBuffer, (border, buffer) -> border.setDamageBuffer(buffer.doubleValue()), false, 5.0,
                0, Double.POSITIVE_INFINITY, "damage buffer", true);
        property(registrar, "ExprWorldBorderDamageAmount", "number", "world[ ]border damage amount", Number.class,
                WorldBorder::getDamageAmount, (border, amount) -> border.setDamageAmount(amount.doubleValue()), false, 0.2,
                0, Double.POSITIVE_INFINITY, "damage amount", false);
        property(registrar, "ExprWorldBorderWarningDistance", "integer", "world[ ]border warning distance", Number.class,
                border -> (long) border.getWarningDistance(),
                (border, distance) -> border.setWarningDistance(co.xenastudios.neoskript.runtime.Values.toInt(distance.doubleValue())), false, 5L,
                0, Integer.MAX_VALUE, "warning distance", true);
        property(registrar, "ExprWorldBorderWarningTime", "timespan", "world[ ]border warning time", Timespan.class,
                Borders::warningTime, Borders::warningTime, false, new Timespan(15000), 0, Integer.MAX_VALUE * 50.0,
                "warning time", true);

        registrar.effect("EffWorldBorderExpand", info -> {
            boolean shrink = info.pattern() >= 2;
            boolean to = info.hasTag("to");
            boolean radius = info.hasTag("radius");
            return (context, args) -> {
                if (!(args[1] instanceof Number amount)) {
                    return;
                }
                if (Double.isNaN(amount.doubleValue())) {
                    co.xenastudios.neoskript.runtime.RuntimeErrors.report("You can't " + (shrink ? "shrink" : "grow")
                            + " a world border " + (to ? "to" : "by") + " NaN.");
                    return;
                }
                double change = amount.doubleValue() * (radius ? 2 : 1);
                long seconds = args[2] instanceof Timespan time ? time.millis() / 1000 : 0;
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof WorldBorder border) {
                        double size = to ? change : border.getSize() + (shrink ? -change : change);
                        // Kept within the sizes Minecraft allows, as infinite amounts would go past them.
                        border.setSize(Math.max(1, Math.min(59999968, size)), seconds);
                    }
                }
            };
        }, "(expand|grow) [[the] (diameter|radius:radius) of] %worldborders% (by|to:to) %number% [over [a period of] %timespan%]",
                "(expand|grow) %worldborders%['s (diameter|radius:radius)] (by|to:to) %number% [over [a period of] %timespan%]",
                "(contract|shrink) [[the] (diameter|radius:radius) of] %worldborders% (by|to:to) %number% [over [a period of] %timespan%]",
                "(contract|shrink) %worldborders%['s (diameter|radius:radius)] (by|to:to) %number% [over [a period of] %timespan%]");
    }

    /**
     * {@code [the] <property> [of %worldborders%]} and {@code %worldborders%'[s] <property>}, which can be set,
     * added to and taken from (kept between {@code min} and {@code max}), and reset to {@code reset}. With
     * {@code halves}, the {@code radius} form is half the value.
     */
    private static <V> void property(Registrar registrar, String id, String returnType, String property,
            Class<V> valueClass, Function<WorldBorder, Object> getter, BiConsumer<WorldBorder, V> setter, boolean halves,
            Object reset, double min, double max, String what, boolean infinite) {
        registrar.expression(id, returnType, info -> new SettableBorderProperty<>(valueClass, getter, setter,
                halves && info.hasTag("radius") ? 2 : 1, reset, min, max, what, infinite),
                "[the] " + property + " [of %worldborders%]", "%worldborders%'[s] " + property);
    }

    /** A world border property; without borders given, the first world's. */
    private static final class SettableBorderProperty<V> implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        private final Class<V> valueClass;
        private final Function<WorldBorder, Object> getter;
        private final BiConsumer<WorldBorder, V> setter;
        /** 2 for a radius, which is half the diameter stored. */
        private final int scale;
        private final Object reset;
        private final double min;
        private final double max;
        private final String what;
        private final boolean infinite;

        SettableBorderProperty(Class<V> valueClass, Function<WorldBorder, Object> getter, BiConsumer<WorldBorder, V> setter,
                int scale, Object reset, double min, double max, String what, boolean infinite) {
            this.what = what;
            this.infinite = infinite;
            this.valueClass = valueClass;
            this.getter = getter;
            this.setter = setter;
            this.scale = scale;
            this.reset = reset;
            this.min = min;
            this.max = max;
        }

        private static Object[] borders(Object[] arguments) {
            return arguments[0] == null ? new Object[] {Bukkit.getWorlds().getFirst().getWorldBorder()}
                    : arguments[0] instanceof Object[] array ? array : new Object[] {arguments[0]};
        }

        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object border : borders(arguments)) {
                if (border instanceof WorldBorder b) {
                    Object value = getter.apply(b);
                    values.add(scale != 1 && value instanceof Number n ? n.doubleValue() / scale : value);
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            boolean amount = Number.class.isAssignableFrom(valueClass) || valueClass == Timespan.class;
            return switch (mode) {
                case SET -> new Class<?>[] {valueClass};
                case ADD, REMOVE -> amount ? new Class<?>[] {valueClass} : null;
                case RESET -> reset != null || valueClass == Location.class ? new Class<?>[0] : null;
                default -> null;
            };
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments,
                Object[] delta, Mode mode) {
            for (Object border : borders(arguments)) {
                if (!(border instanceof WorldBorder b)) {
                    continue;
                }
                if (mode == Mode.RESET) {
                    if (valueClass == Location.class) {
                        setter.accept(b, valueClass.cast(new Location(b.getWorld(), 0, 0, 0)));
                    } else {
                        setter.accept(b, valueClass.cast(reset));
                    }
                    continue;
                }
                if (delta == null || delta.length == 0 || delta[0] == null) {
                    continue;
                }
                Object value = delta[0];
                if (value instanceof Number || value instanceof Timespan) {
                    double given = value instanceof Timespan time ? time.millis() : ((Number) value).doubleValue() * scale;
                    if (Double.isNaN(given)) {
                        co.xenastudios.neoskript.runtime.RuntimeErrors.report("NaN is not a valid world border " + what);
                        continue;
                    }
                    if (Double.isInfinite(given) && !infinite) {
                        co.xenastudios.neoskript.runtime.RuntimeErrors.report("World border " + what + " cannot be infinite");
                        continue;
                    }
                    Object current = getter.apply(b);
                    double now = current instanceof Timespan time ? time.millis() : current instanceof Number n ? n.doubleValue() : 0;
                    double next = switch (mode) {
                        case ADD -> now + given;
                        case REMOVE -> now - given;
                        default -> given;
                    };
                    next = Math.max(min, Math.min(max, next));
                    value = valueClass == Timespan.class ? new Timespan((long) next) : next;
                }
                setter.accept(b, valueClass.cast(value));
            }
        }
    }

    /** A border's warning time, in ticks where the server keeps it in ticks (1.21.11+), else in seconds. */
    private static Object warningTime(WorldBorder border) {
        try {
            Object ticks = WorldBorder.class.getMethod("getWarningTimeTicks").invoke(border);
            return new Timespan(((Number) ticks).longValue() * 50);
        } catch (ReflectiveOperationException olderServer) {
            return new Timespan(border.getWarningTime() * 1000L);
        }
    }

    private static void warningTime(WorldBorder border, Timespan time) {
        try {
            WorldBorder.class.getMethod("setWarningTimeTicks", int.class).invoke(border, (int) Math.min(Integer.MAX_VALUE, time.ticks()));
        } catch (ReflectiveOperationException olderServer) {
            // In seconds, which the server turns into ticks: kept small enough not to overflow.
            border.setWarningTime((int) Math.min(Integer.MAX_VALUE / 20, time.millis() / 1000));
        }
    }
}
