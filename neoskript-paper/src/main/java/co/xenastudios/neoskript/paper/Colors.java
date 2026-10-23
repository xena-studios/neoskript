/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Skript's colours: the sixteen named ones (dye colours), and any other from {@code rgb(...)}. */
final class Colors {

    private Colors() {}

    /** A colour, named or not. */
    sealed interface Colour permits Named, Rgb {
        int red();

        int green();

        int blue();

        int alpha();

        /** As a hex code without the {@code #}: {@code 1D1D21}. */
        default String hex() {
            return String.format(Locale.ROOT, "%02X%02X%02X", red(), green(), blue());
        }
    }

    /** The named colours, with the dye colour each is (as its red, green and blue) and its chat code. */
    enum Named implements Colour {
        BLACK(0x1D1D21, '0', "black"),
        DARK_GREY(0x474F52, '8', "dark grey", "dark gray"),
        LIGHT_GREY(0x9D9D97, '7', "grey", "gray", "light grey", "light gray", "silver"),
        WHITE(0xF9FFFE, 'f', "white"),
        DARK_BLUE(0x3C44AA, '1', "blue", "dark blue"),
        BROWN(0x835432, '6', "brown"),
        DARK_CYAN(0x169C9C, '3', "cyan", "dark cyan", "dark aqua"),
        LIGHT_CYAN(0x3AB3DA, 'b', "light cyan", "aqua", "light aqua", "light blue"),
        DARK_GREEN(0x5E7C16, '2', "green", "dark green"),
        LIGHT_GREEN(0x80C71F, 'a', "light green", "lime", "lime green"),
        YELLOW(0xFED83D, 'e', "yellow"),
        ORANGE(0xF9801D, '6', "orange", "gold", "dark yellow"),
        DARK_RED(0xB02E26, '4', "red", "dark red"),
        LIGHT_RED(0xF38BAA, 'c', "pink", "light red"),
        DARK_PURPLE(0x8932B8, '5', "purple", "dark purple"),
        LIGHT_PURPLE(0xC74EBD, 'd', "magenta", "light purple");

        private final int rgb;
        final char code;
        final String[] names;

        Named(int rgb, char code, String... names) {
            this.rgb = rgb;
            this.code = code;
            this.names = names;
        }

        @Override
        public int red() {
            return rgb >> 16 & 0xFF;
        }

        @Override
        public int green() {
            return rgb >> 8 & 0xFF;
        }

        @Override
        public int blue() {
            return rgb & 0xFF;
        }

        @Override
        public int alpha() {
            return 255;
        }

        @Override
        public String toString() {
            return names[0];
        }
    }

    /** Any colour, from {@code rgb(r, g, b[, a])}. */
    record Rgb(int red, int green, int blue, int alpha) implements Colour {
        @Override
        public String toString() {
            return "rgb " + red + ", " + green + ", " + blue;
        }
    }

    static void register(Registrar registrar) {
        Map<String, Colour> names = new LinkedHashMap<>();
        for (Named colour : Named.values()) {
            for (String name : colour.names) {
                names.put(name, colour);
            }
        }
        registrar.namedType("color", "colour", Colour.class, names);
        registrar.alias("color", "color", "colors");
        registrar.alias("color", "colour", "colours");
        // Colours are equal if they're the same colour, however they were made: 'colour of hex code "8932B8" is purple'.
        registrar.comparison(Colour.class, Colour.class, (a, b) -> a.red() == b.red() && a.green() == b.green()
                && a.blue() == b.blue() && a.alpha() == b.alpha());
        registrar.function("rgb", List.of(Parameter.of("red", "number"), Parameter.of("green", "number"),
                Parameter.of("blue", "number"), new Parameter("alpha", "number", false, true)), "color", false,
                args -> new Rgb(channel(args[0]), channel(args[1]), channel(args[2]), args[3] == null ? 255 : channel(args[3])));
        registrar.expression("ExprARGB", "integer", info -> {
            int which = info.hasTag("red") ? 0 : info.hasTag("green") ? 1 : info.hasTag("blue") ? 2 : 3;
            return (context, args) -> {
                List<Object> values = new ArrayList<>();
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof Colour colour) {
                        values.add((long) switch (which) {
                            case 0 -> colour.red();
                            case 1 -> colour.green();
                            case 2 -> colour.blue();
                            default -> colour.alpha();
                        });
                    }
                }
                return values.size() == 1 ? values.getFirst() : values.toArray();
            };
        }, "[the] (red:red|green:green|blue:blue|alpha) (value|component)[s] of %colors%",
                "%colors%'[s] (red:red|green:green|blue:blue|alpha) (value|component)[s]");
        registrar.expression("ExprHexCode", "string", info -> (context, args) -> {
            List<Object> codes = new ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof Colour colour) {
                    codes.add(colour.hex());
                }
            }
            return codes.size() == 1 ? codes.getFirst() : codes.toArray();
        }, "[the] hex[adecimal] code[s] of %colors%", "%colors%'[s] hex[adecimal] code[s]");
        // 'the string colours of X': the colours its codes set, or with 'codes', the codes as written.
        registrar.expression("ExprStringColor", "object", info -> {
            boolean code = info.hasTag("code");
            int which = info.hasTag("first") ? 1 : info.hasTag("last") ? 2 : 0;
            return (context, args) -> {
                List<Object> found = new ArrayList<>();
                for (Object value : (Object[]) args[0]) {
                    if (!(value instanceof String text)) {
                        continue;
                    }
                    List<Code> codes = codes(text);
                    if (codes.isEmpty()) {
                        continue;
                    }
                    List<Code> picked = which == 1 ? List.of(codes.getFirst()) : which == 2 ? List.of(codes.getLast()) : codes;
                    for (Code c : picked) {
                        found.add(code ? c.text() : c.colour());
                    }
                }
                return found.size() == 1 ? found.getFirst() : found.toArray();
            };
        }, "[all [[of] the]|the] string colo[u]r[s] [code:code[s]] of %strings%",
                "[the] (first:first|last:last) string colo[u]r [code:code] of %strings%");
        // The colour of a text display's background, a sheep or shulker, or a dyed or tinted item.
        registrar.expression("ExprColorOf", "color", info -> new SettableProperty<>(Object.class, Colour.class,
                Colors::colourOf, Colors::setColour).resettingTo((java.util.function.Consumer<Object>) owner -> {
                    // A text display goes back to Minecraft's translucent black.
                    if (owner instanceof org.bukkit.entity.TextDisplay display) {
                        display.setBackgroundColor(org.bukkit.Color.fromARGB(64, 0, 0, 0));
                    }
                }), "[the] colo[u]r[s] of %itemtypes/entities/potioneffecttypes%",
                "%itemtypes/entities/potioneffecttypes%'[s] colo[u]r[s]");
        registrar.expression("ExprColorFromHexCode", "color", info -> (context, args) -> {
            List<Object> colours = new ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                Colour colour = value instanceof String text ? fromHex(text) : null;
                if (colour == null) {
                    RuntimeErrors.report("Could not parse '" + value + "' as a hex code!");
                } else {
                    colours.add(colour);
                }
            }
            return colours.size() == 1 ? colours.getFirst() : colours.toArray();
        }, "[the] colo[u]r[s] (from|of) hex[adecimal] code[s] %strings%");
    }

    /** A colour code in text, as written there ({@code &a}, {@code <#ff0000>}, {@code <light red>}), and its colour. */
    private record Code(String text, Colour colour) {}

    private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile(
            "[&\u00a7]([0-9a-fA-F])|[&\u00a7]#([0-9a-fA-F]{6})|<#([0-9a-fA-F]{6})>|<([a-zA-Z_ ]+)>");

    /** The colour codes in {@code text}, in order. */
    private static List<Code> codes(String text) {
        List<Code> codes = new ArrayList<>();
        java.util.regex.Matcher matcher = CODE.matcher(text);
        while (matcher.find()) {
            Colour colour = null;
            if (matcher.group(1) != null) {
                char code = Character.toLowerCase(matcher.group(1).charAt(0));
                for (Named named : Named.values()) {
                    if (named.code == code && named != Named.BROWN) {
                        colour = named;
                        break;
                    }
                }
            } else if (matcher.group(2) != null || matcher.group(3) != null) {
                colour = fromHex(matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
            } else {
                String name = matcher.group(4).toLowerCase(Locale.ROOT).replace('_', ' ');
                for (Named named : Named.values()) {
                    if (java.util.List.of(named.names).contains(name)) {
                        colour = named;
                        break;
                    }
                }
            }
            if (colour != null) {
                codes.add(new Code(matcher.group(), colour));
            }
        }
        return codes;
    }

    private static int channel(Object value) {
        return value instanceof Number number ? (int) Math.max(0, Math.min(255, Math.round(number.doubleValue()))) : 0;
    }

    /** The colour a hex code like {@code #8932B8} or {@code 8932B8} is, or {@code null}. */
    static Colour fromHex(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (!hex.matches("[0-9a-fA-F]{6}")) {
            return null;
        }
        int rgb = Integer.parseInt(hex, 16);
        return new Rgb(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255);
    }

    /** The colour {@code owner} has, or {@code null}. */
    private static Colour colourOf(Object owner) {
        return switch (owner) {
            case org.bukkit.entity.TextDisplay display -> display.getBackgroundColor() == null ? null
                    : Displays.colour(display.getBackgroundColor());
            case org.bukkit.potion.PotionEffectType type -> Displays.colour(type.getColor());
            case org.bukkit.material.Colorable colorable when colorable.getColor() != null -> named(colorable.getColor());
            case ItemType item -> switch (item.stack().getItemMeta()) {
                case org.bukkit.inventory.meta.LeatherArmorMeta leather -> Displays.colour(leather.getColor());
                case org.bukkit.inventory.meta.PotionMeta potion when potion.hasColor() -> Displays.colour(potion.getColor());
                case null, default -> null;
            };
            default -> null;
        };
    }

    private static void setColour(Object owner, Colour colour) {
        org.bukkit.Color color = org.bukkit.Color.fromARGB(colour.alpha(), colour.red(), colour.green(), colour.blue());
        switch (owner) {
            case org.bukkit.entity.TextDisplay display -> display.setBackgroundColor(color);
            case org.bukkit.material.Colorable colorable -> {
                org.bukkit.DyeColor dye = org.bukkit.DyeColor.getByColor(org.bukkit.Color.fromRGB(colour.red(), colour.green(),
                        colour.blue()));
                if (dye != null) {
                    colorable.setColor(dye);
                }
            }
            case ItemType item -> item.editMeta(meta -> {
                if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta leather) {
                    leather.setColor(color);
                } else if (meta instanceof org.bukkit.inventory.meta.PotionMeta potion) {
                    potion.setColor(color);
                }
            });
            default -> {}
        }
    }

    /** The named colour a dye colour is. */
    private static Colour named(org.bukkit.DyeColor dye) {
        int rgb = dye.getColor().asRGB();
        for (Named named : Named.values()) {
            if (named.red() << 16 == (rgb & 0xFF0000) && named.green() << 8 == (rgb & 0xFF00) && named.blue() == (rgb & 0xFF)) {
                return named;
            }
        }
        return Displays.colour(dye.getColor());
    }
}
