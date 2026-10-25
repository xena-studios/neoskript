/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Boss bars, as Skript's boss bar module has them: making one (keyed ones are kept by the server), a boss's
 * bar, a bar by its key, its flags, and its title, progress, style, colour and viewers.
 */
final class BossBars {

    private BossBars() {}

    /** The colour Skript gives each bar colour, and rounds colours to. */
    private static final Map<BarColor, Colors.Named> COLOURS = Map.of(
            BarColor.PINK, Colors.Named.LIGHT_RED,
            BarColor.BLUE, Colors.Named.DARK_BLUE,
            BarColor.RED, Colors.Named.DARK_RED,
            BarColor.GREEN, Colors.Named.LIGHT_GREEN,
            BarColor.YELLOW, Colors.Named.YELLOW,
            BarColor.PURPLE, Colors.Named.DARK_PURPLE,
            BarColor.WHITE, Colors.Named.WHITE);

    /** In Skript's order, which picks between bar colours as close as each other. */
    private static final BarColor[] ORDER = {BarColor.PINK, BarColor.BLUE, BarColor.RED, BarColor.GREEN, BarColor.YELLOW,
            BarColor.PURPLE, BarColor.WHITE};

    /** The types, which come before colours, as the colour of a boss bar needs them. */
    static void registerTypes(Registrar registrar) {
        registrar.type("bossbar", "boss bar", BossBar.class);
        registrar.alias("bossbar", "bossbar", "bossbars");
        registrar.type("keyedbossbar", "keyed boss bar", KeyedBossBar.class);
        registrar.alias("keyedbossbar", "keyed bossbar", "keyed bossbars");
        registrar.subtype("keyedbossbar", "bossbar");
        registrar.text(BossBar.class, bar -> bar instanceof KeyedBossBar keyed
                ? "boss bar with id '" + keyed.getKey() + "'" + (bar.getTitle().isEmpty() ? "" : " titled '" + bar.getTitle() + "'")
                : bar.getTitle().isEmpty() ? "boss bar" : "boss bar titled '" + bar.getTitle() + "'");
        registrar.comparison(KeyedBossBar.class, KeyedBossBar.class, (a, b) -> a.getKey().equals(b.getKey()));
        // Deleting a bar takes it from everyone, and a keyed one from the server.
        registrar.changer(BossBar.class, Changeable.Mode.DELETE, (bar, values) -> {
            bar.removeAll();
            if (bar instanceof KeyedBossBar keyed) {
                Bukkit.removeBossBar(keyed.getKey());
            }
        });

        Map<String, BarStyle> styles = new LinkedHashMap<>();
        style(styles, BarStyle.SEGMENTED_6, "6", "six");
        style(styles, BarStyle.SEGMENTED_10, "10", "ten");
        style(styles, BarStyle.SEGMENTED_12, "12", "twelve");
        style(styles, BarStyle.SEGMENTED_20, "20", "twenty");
        styles.put("solid", BarStyle.SOLID);
        styles.put("no notches", BarStyle.SOLID);
        styles.put("progress", BarStyle.SOLID);
        registrar.namedType("bossbarstyle", "boss bar style", BarStyle.class, styles);
        registrar.alias("bossbarstyle", "bossbar style", "bossbar styles");
    }

    private static void style(Map<String, BarStyle> styles, BarStyle style, String digits, String word) {
        for (String count : new String[] {digits, word}) {
            styles.put(count + " segments", style);
        }
        for (String count : new String[] {digits, word}) {
            styles.put("notched with " + count + " segments", style);
        }
        for (String count : new String[] {digits, word}) {
            styles.put(count + " notches", style);
        }
    }

    static void register(Registrar registrar) {
        // 'a red boss bar titled "x":', whose section sets the new bar up.
        registrar.expression("ExprSecCreateBossBar", "bossbar", info -> {
            boolean keyed = info.pattern() == 1;
            return (context, args) -> {
                BarColor colour = args[0] instanceof Colors.Colour given && nearest(given) != null ? nearest(given) : BarColor.WHITE;
                int titleAt = keyed ? 2 : 1;
                String title = args[titleAt] == null ? null : Messages.LEGACY_SECTION.serialize(Messages.component(args[titleAt]));
                if (keyed) {
                    if (!(args[1] instanceof String text)) {
                        return null;
                    }
                    NamespacedKey key = key(text);
                    if (key == null) {
                        return null;
                    }
                    Bukkit.createBossBar(key, title, colour, BarStyle.SOLID);
                    return Bukkit.getBossBar(key);
                }
                return Bukkit.createBossBar(title, colour, BarStyle.SOLID);
            };
        }, "[a] [new] [%-color%] boss[ ]bar [(with title|titled) %-text%]",
                "[a] [new] keyed [%-color%] boss[ ]bar with (id|key) %string% [(with title|titled) %-text%]");
        registrar.sectionExpression("ExprSecCreateBossBar", "bossbar");

        registrar.expression("ExprBossBarFromKey", "keyedbossbar", info -> (context, args) -> {
            List<Object> bars = new ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                NamespacedKey key = value instanceof String text ? key(text) : null;
                KeyedBossBar bar = key == null ? null : Bukkit.getBossBar(key);
                if (bar != null) {
                    bars.add(bar);
                }
            }
            return bars.size() == 1 ? bars.getFirst() : bars.toArray();
        }, "[the] boss[ ]bar[s] (from|with) [the] (id|key)[s] %strings%");
        registrar.property("ExprBossBarFromEntity", "bossbar", "boss[ ]bar", "entities", Entity.class,
                entity -> entity instanceof Boss boss ? boss.getBossBar() : null);
        registrar.property("ExprKeyOfBossBar", "string", "boss[ ]bar (key|id)", "bossbars", BossBar.class,
                bar -> bar instanceof KeyedBossBar keyed ? keyed.getKey().toString() : null);
        // 'all keyed boss bars': those the server keeps.
        registrar.expression("ExprSets-keyedbossbar", "keyedbossbar", info -> (context, args) -> {
            List<Object> bars = new ArrayList<>();
            for (Iterator<KeyedBossBar> it = Bukkit.getBossBars(); it.hasNext(); ) {
                bars.add(it.next());
            }
            return bars.toArray();
        }, "(all [[of] the]|every) keyed boss[ ]bars");

        BarFlag[] flags = {BarFlag.DARKEN_SKY, BarFlag.DARKEN_SKY, BarFlag.CREATE_FOG, BarFlag.CREATE_FOG,
                BarFlag.PLAY_BOSS_MUSIC, BarFlag.PLAY_BOSS_MUSIC};
        registrar.effect("EffBossBarFlags", info -> {
            BarFlag flag = flags[info.pattern()];
            boolean remove = info.hasTag("remove") || info.pattern() % 2 == 1;
            return (context, args) -> {
                for (Object value : array(args[0])) {
                    if (value instanceof BossBar bar) {
                        if (remove) {
                            bar.removeFlag(flag);
                        } else {
                            bar.addFlag(flag);
                        }
                    }
                }
            };
        }, "make %bossbars% [remove:(not|no longer)] darken the sky", "stop %bossbar% from darkening the sky",
                "make %bossbars% [remove:(not|no longer)] create fog", "stop %bossbar% from creating fog",
                "make %bossbars% [remove:(not|no longer)] play boss music", "stop %bossbar% from playing [the] boss music");
        registrar.condition("CondHasBossBarFlag", info -> {
            BarFlag flag = flags[info.pattern()];
            boolean negated = info.pattern() % 2 != 0;
            boolean and = info.and(0);
            return (context, args) -> negated != Condition.check(array(args[0]), and,
                    value -> value instanceof BossBar bar && bar.hasFlag(flag));
        }, "%bossbars% [do[es]] darken[s] the sky", "%bossbars% (doesn't|does not|do not|don't) darken the sky",
                "%bossbars% [do[es]] create[s] fog", "%bossbars% (doesn't|does not|do not|don't) create fog",
                "%bossbars% [do[es]] play[s] boss music", "%bossbars% (doesn't|does not|do not|don't) play boss music");

        // Skript's title, progress, style and viewers properties, for the things that have them.
        registrar.expression("PropExprTitle", "string", info -> new Property(BossBars::title, BossBars::setTitle,
                new Class<?>[] {Object.class}, Changeable.Mode.SET, Changeable.Mode.DELETE, Changeable.Mode.RESET),
                Patterns.property("title[s]", "objects"));
        registrar.expression("PropExprProgress", "number", info -> new Property(
                owner -> owner instanceof BossBar bar ? bar.getProgress() : null, BossBars::setProgress,
                new Class<?>[] {Number.class}, Changeable.Mode.SET, Changeable.Mode.RESET, Changeable.Mode.ADD,
                Changeable.Mode.REMOVE), Patterns.property("progress", "objects"));
        registrar.expression("PropExprStyle", "bossbarstyle", info -> new Property(
                owner -> owner instanceof BossBar bar ? bar.getStyle() : null, (owner, delta, mode) -> {
                    if (owner instanceof BossBar bar) {
                        bar.setStyle(delta == null || delta.length == 0 ? BarStyle.SOLID : (BarStyle) delta[0]);
                    }
                }, new Class<?>[] {BarStyle.class}, Changeable.Mode.SET, Changeable.Mode.RESET),
                Patterns.property("style[s]", "objects"));
        registrar.expression("PropExprViewers", "player", info -> new Property(BossBars::viewers, BossBars::setViewers,
                new Class<?>[] {Player.class}, Changeable.Mode.SET, Changeable.Mode.ADD, Changeable.Mode.REMOVE,
                Changeable.Mode.DELETE, Changeable.Mode.RESET), Patterns.property("viewer[s]", "objects"));
    }

    private static Object title(Object owner) {
        return switch (owner) {
            case BossBar bar -> bar.getTitle();
            // A signed book's title.
            case ItemType item when item.stack().getItemMeta() instanceof BookMeta book && book.hasTitle() ->
                    Messages.LEGACY_SECTION.serialize(book.title());
            default -> null;
        };
    }

    private static void setTitle(Object owner, Object[] delta, Changeable.Mode mode) {
        net.kyori.adventure.text.Component title = delta == null || delta.length == 0 || delta[0] == null ? null
                : Messages.component(delta[0]);
        switch (owner) {
            case BossBar bar -> bar.setTitle(title == null ? null : Messages.LEGACY_SECTION.serialize(title));
            case ItemType item -> item.editMeta(meta -> {
                if (meta instanceof BookMeta book) {
                    book.title(title);
                }
            });
            default -> {}
        }
    }

    private static void setProgress(Object owner, Object[] delta, Changeable.Mode mode) {
        if (!(owner instanceof BossBar bar)) {
            return;
        }
        double progress = delta == null || delta.length == 0 ? 0.0 : ((Number) delta[0]).doubleValue();
        if (mode == Changeable.Mode.ADD) {
            progress += bar.getProgress();
        } else if (mode == Changeable.Mode.REMOVE) {
            // As Skript has it: the amount less the progress.
            progress -= bar.getProgress();
        }
        bar.setProgress(Math.clamp(progress, 0.0, 1.0));
    }

    private static Object viewers(Object owner) {
        return switch (owner) {
            case BossBar bar -> bar.getPlayers().toArray();
            case Inventory inventory -> inventory.getViewers().stream().filter(viewer -> viewer instanceof Player).toArray();
            default -> null;
        };
    }

    private static void setViewers(Object owner, Object[] delta, Changeable.Mode mode) {
        if (!(owner instanceof BossBar bar)) {
            return;
        }
        if (mode == Changeable.Mode.SET || mode == Changeable.Mode.DELETE || mode == Changeable.Mode.RESET) {
            bar.removeAll();
        }
        if (delta == null || mode == Changeable.Mode.DELETE || mode == Changeable.Mode.RESET) {
            return;
        }
        for (Object value : delta) {
            if (value instanceof Player player) {
                if (mode == Changeable.Mode.REMOVE) {
                    bar.removePlayer(player);
                } else {
                    bar.addPlayer(player);
                }
            }
        }
    }

    /** The colour Skript gives a bar colour. */
    static Colors.Colour colour(BarColor colour) {
        return COLOURS.get(colour);
    }

    /** The bar colour nearest {@code colour}, or {@code null} if none is near enough, as Skript rounds it. */
    static BarColor nearest(Colors.Colour colour) {
        double[] lab = lab(colour.red(), colour.green(), colour.blue());
        BarColor best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BarColor bar : ORDER) {
            Colors.Colour candidate = COLOURS.get(bar);
            double distance = distanceSquared(lab, lab(candidate.red(), candidate.green(), candidate.blue()));
            if (distance < bestDistance) {
                best = bar;
                bestDistance = distance;
            }
        }
        return bestDistance < 2500 ? best : null;
    }

    /** Sets a boss bar's colour to the nearest bar colour, as {@code set colour of} does; an error if none is near. */
    static void setColour(BossBar bar, Colors.Colour colour) {
        BarColor nearest = nearest(colour);
        if (nearest == null) {
            RuntimeErrors.report("Could not round to a color that is similar to " + co.xenastudios.neoskript.runtime.Values.text(colour));
        } else {
            bar.setColor(nearest);
        }
    }

    /** A colour in CIE L*a*b*, as Skript's {@code LabColor} works it out. */
    private static double[] lab(int red, int green, int blue) {
        double r = gamma(red / 255.0);
        double g = gamma(green / 255.0);
        double b = gamma(blue / 255.0);
        double x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b;
        double fy = f(y);
        return new double[] {116 * fy - 16, 500 * (f(x / 0.95047) - fy), 200 * (fy - f(z / 1.08883))};
    }

    private static double gamma(double value) {
        return value > 0.04045 ? Math.pow((value + 0.055) / 1.055, 2.4) : value / 12.92;
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116.0;
    }

    private static double distanceSquared(double[] a, double[] b) {
        double l = b[0] - a[0];
        double x = b[1] - a[1];
        double y = b[2] - a[2];
        return l * l + x * x + y * y;
    }

    /** A key as Skript checks it, with its error if it isn't one. */
    private static NamespacedKey key(String text) {
        String error = null;
        String[] split = text.split(":");
        if (text.length() > Short.MAX_VALUE) {
            error = "A namespaced key can not be longer than " + Short.MAX_VALUE + " characters.";
        } else if (split.length > 2) {
            error = "A namespaced key can not have more than one ':'.";
        } else {
            String key = split.length == 2 ? split[1] : split[0];
            String namespace = split.length == 2 ? split[0] : "";
            if (key.isEmpty()) {
                error = "The key cannot be empty.";
            }
            for (char character : key.toCharArray()) {
                if (error == null && !(validNamespace(character) || character == '/')) {
                    error = "Invalid character '" + character + "'.";
                }
            }
            for (char character : namespace.toCharArray()) {
                if (error == null && !validNamespace(character)) {
                    error = "Invalid character '" + character + "'.";
                }
            }
            if (error == null) {
                return namespace.isEmpty() ? NamespacedKey.minecraft(key) : new NamespacedKey(namespace, key);
            }
        }
        RuntimeErrors.report(error + ". A namespaced key can be formatted as 'namespace:id' or 'id'. It can only contain one ':' "
                + "to separate the namespace and the id. Only alphanumeric characters, periods, underscores, and dashes can be used.");
        return null;
    }

    private static boolean validNamespace(char character) {
        return character >= 'a' && character <= 'z' || character >= '0' && character <= '9' || character == '.'
                || character == '_' || character == '-';
    }

    /** How a property changes one owner. */
    @FunctionalInterface
    private interface Change {
        void change(Object owner, Object[] delta, Changeable.Mode mode);
    }

    /** A property of any owner that has it, changed in the modes given with values of {@code accepted}. */
    private static final class Property implements Expression, Changeable {
        private final java.util.function.Function<Object, Object> getter;
        private final Change change;
        private final Class<?>[] accepted;
        private final java.util.Set<Mode> modes;

        Property(java.util.function.Function<Object, Object> getter, Change change, Class<?>[] accepted, Mode... modes) {
            this.getter = getter;
            this.change = change;
            this.accepted = accepted;
            this.modes = java.util.EnumSet.copyOf(List.of(modes));
        }

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object owner : array(arguments[0])) {
                Object value = getter.apply(owner);
                if (value instanceof Object[] many) {
                    values.addAll(List.of(many));
                } else if (value != null) {
                    values.add(value);
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            if (!modes.contains(mode)) {
                return null;
            }
            return mode == Mode.DELETE || mode == Mode.RESET ? new Class<?>[0] : accepted;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            for (Object owner : array(arguments[0])) {
                change.change(owner, delta, mode);
            }
        }
    }

    private static Object[] array(Object value) {
        return value instanceof Object[] array ? array : value == null ? new Object[0] : new Object[] {value};
    }
}
