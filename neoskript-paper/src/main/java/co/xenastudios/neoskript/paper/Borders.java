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

        property(registrar, "ExprWorldBorderSize", "number", "world[ ]border (size|diameter|radius:radius)", Number.class,
                border -> border.getSize(), (border, size) -> border.setSize(size.doubleValue()), true);
        property(registrar, "ExprWorldBorderCenter", "location", "world[ ]border (center|middle)", Location.class,
                WorldBorder::getCenter, WorldBorder::setCenter, false);
        property(registrar, "ExprWorldBorderDamageBuffer", "number", "world[ ]border damage buffer", Number.class,
                WorldBorder::getDamageBuffer, (border, buffer) -> border.setDamageBuffer(buffer.doubleValue()), false);
        property(registrar, "ExprWorldBorderDamageAmount", "number", "world[ ]border damage amount", Number.class,
                WorldBorder::getDamageAmount, (border, amount) -> border.setDamageAmount(amount.doubleValue()), false);
        property(registrar, "ExprWorldBorderWarningDistance", "integer", "world[ ]border warning distance", Number.class,
                border -> (long) border.getWarningDistance(),
                (border, distance) -> border.setWarningDistance((int) Math.round(distance.doubleValue())), false);
        property(registrar, "ExprWorldBorderWarningTime", "timespan", "world[ ]border warning time", Timespan.class,
                border -> new Timespan(border.getWarningTime() * 1000L),
                (border, time) -> border.setWarningTime((int) (time.millis() / 1000)), false);

        registrar.effect("EffWorldBorderExpand", info -> {
            boolean shrink = info.pattern() >= 2;
            boolean to = info.hasTag("to");
            boolean radius = info.hasTag("radius");
            return (context, args) -> {
                if (!(args[1] instanceof Number amount)) {
                    return;
                }
                double change = amount.doubleValue() * (radius ? 2 : 1);
                long seconds = args[2] instanceof Timespan time ? time.millis() / 1000 : 0;
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof WorldBorder border) {
                        double size = to ? change : border.getSize() + (shrink ? -change : change);
                        border.setSize(Math.max(1, size), seconds);
                    }
                }
            };
        }, "(expand|grow) [[the] (diameter|radius:radius) of] %worldborders% (by|to:to) %number% [over [a period of] %timespan%]",
                "(expand|grow) %worldborders%['s (diameter|radius:radius)] (by|to:to) %number% [over [a period of] %timespan%]",
                "(contract|shrink) [[the] (diameter|radius:radius) of] %worldborders% (by|to:to) %number% [over [a period of] %timespan%]",
                "(contract|shrink) %worldborders%['s (diameter|radius:radius)] (by|to:to) %number% [over [a period of] %timespan%]");
    }

    /**
     * {@code [the] <property> [of %worldborders%]} and {@code %worldborders%'[s] <property>}, which can be set.
     * With {@code halves}, the {@code radius} form is half the value.
     */
    private static <V> void property(Registrar registrar, String id, String returnType, String property,
            Class<V> valueClass, Function<WorldBorder, Object> getter, BiConsumer<WorldBorder, V> setter, boolean halves) {
        registrar.expression(id, returnType, info -> {
            boolean half = halves && info.hasTag("radius");
            return new SettableBorderProperty<>(valueClass, border -> {
                Object value = getter.apply(border);
                return half && value instanceof Number n ? n.doubleValue() / 2 : value;
            }, (border, value) -> {
                if (half && value instanceof Number n) {
                    setter.accept(border, valueClass.cast(n.doubleValue() * 2));
                } else {
                    setter.accept(border, value);
                }
            });
        }, "[the] " + property + " [of %worldborders%]", "%worldborders%'[s] " + property);
    }

    /** A world border property; without borders given, the first world's. */
    private static final class SettableBorderProperty<V> implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        private final Class<V> valueClass;
        private final Function<WorldBorder, Object> getter;
        private final BiConsumer<WorldBorder, V> setter;

        SettableBorderProperty(Class<V> valueClass, Function<WorldBorder, Object> getter, BiConsumer<WorldBorder, V> setter) {
            this.valueClass = valueClass;
            this.getter = getter;
            this.setter = setter;
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
                    values.add(getter.apply(b));
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {valueClass} : null;
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments,
                Object[] delta, Mode mode) {
            if (delta == null || delta.length == 0) {
                return;
            }
            for (Object border : borders(arguments)) {
                if (border instanceof WorldBorder b) {
                    setter.accept(b, valueClass.cast(delta[0]));
                }
            }
        }
    }
}
