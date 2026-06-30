package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;
import org.bukkit.Color;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The built-in {@code color} type, backed by {@link Color}. Parses common colour names (e.g.
 * {@code red}) and {@code #rrggbb} hex, and renders as {@code rgb r, g, b}.
 */
public final class ColourType implements Type<Color> {

    private static final Map<String, Color> NAMED = Map.ofEntries(
            Map.entry("white", Color.WHITE), Map.entry("silver", Color.SILVER),
            Map.entry("gray", Color.GRAY), Map.entry("grey", Color.GRAY),
            Map.entry("black", Color.BLACK), Map.entry("red", Color.RED),
            Map.entry("maroon", Color.MAROON), Map.entry("yellow", Color.YELLOW),
            Map.entry("olive", Color.OLIVE), Map.entry("lime", Color.LIME),
            Map.entry("green", Color.GREEN), Map.entry("aqua", Color.AQUA),
            Map.entry("cyan", Color.AQUA), Map.entry("teal", Color.TEAL),
            Map.entry("blue", Color.BLUE), Map.entry("navy", Color.NAVY),
            Map.entry("fuchsia", Color.FUCHSIA), Map.entry("magenta", Color.FUCHSIA),
            Map.entry("purple", Color.PURPLE), Map.entry("orange", Color.ORANGE));

    @Override
    public Class<Color> typeClass() {
        return Color.class;
    }

    @Override
    public String codeName() {
        return "color";
    }

    @Override
    public Optional<Color> parse(String input) {
        String value = input.trim().toLowerCase(Locale.ROOT);
        Color named = NAMED.get(value);
        if (named != null) {
            return Optional.of(named);
        }
        if (value.startsWith("#") && value.length() == 7) {
            try {
                return Optional.of(Color.fromRGB(Integer.parseInt(value.substring(1), 16)));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public String toDisplayString(Color value) {
        return "rgb " + value.getRed() + ", " + value.getGreen() + ", " + value.getBlue();
    }
}
