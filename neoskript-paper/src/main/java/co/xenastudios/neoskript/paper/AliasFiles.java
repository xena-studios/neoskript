/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.frontend.parse.AliasNames;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Skript's default alias files ({@code aliases-english}, GPL-3.0, from Skript's jar): names like
 * {@code speed potion} or {@code flight 3 firework rocket} for items with components. Read the first time an
 * item is written, as they build items with the server.
 *
 * <p>A file has sections of {@code name = items} lines, each item a Minecraft id or another alias. A section
 * may start with {@code minecraft version = ...}, and may define variations ({@code {potion types}:}), whose
 * entries ({@code (long|extended) poison = - {components}}) fill in names that use them.
 */
final class AliasFiles {

    private AliasFiles() {}

    private static final String[] FILES = {"+global-variations.sk", "brewing.sk", "combat.sk", "decoration.sk",
        "foodstuffs.sk", "misc.sk", "redstone.sk", "transportation.sk"};

    /** A variation's entry: what it adds to a name, and the components it adds to the item. */
    private record Entry(String name, String components) {}

    /** What an alias stands for: Minecraft ids (with components) and other aliases' names. */
    record Definition(List<String> items) {}

    private static volatile Map<String, Definition> aliases;

    /** What the default aliases call {@code name}, or {@code null}. */
    static Definition get(String name) {
        Map<String, Definition> known = aliases;
        if (known == null) {
            synchronized (AliasFiles.class) {
                known = aliases;
                if (known == null) {
                    known = load();
                    aliases = known;
                }
            }
        }
        return known.get(name);
    }

    /** The item a Minecraft id with components stands for, or {@code null}: {@code minecraft:potion[...]}. */
    static ItemStack item(String id) {
        try {
            return Bukkit.getItemFactory().createItemStack(id);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static Map<String, Definition> load() {
        Map<String, Map<String, Entry>> variations = new HashMap<>();
        Map<String, Definition> loaded = new HashMap<>();
        for (String file : FILES) {
            try (InputStream in = AliasFiles.class.getResourceAsStream("/aliases-english/" + file)) {
                if (in != null) {
                    read(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), variations, loaded);
                }
            } catch (IOException | RuntimeException e) {
                // A broken file leaves its aliases out; the items' own names still work.
            }
        }
        return loaded;
    }

    private static void read(BufferedReader reader, Map<String, Map<String, Entry>> variations,
            Map<String, Definition> into) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            lines.add(line);
        }
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (blank(line) || indent(line) > 0) {
                i++;
                continue;
            }
            // A section: its lines are indented below it.
            int end = i + 1;
            while (end < lines.size() && (blank(lines.get(end)) || indent(lines.get(end)) > 0)) {
                end++;
            }
            section(lines.subList(i + 1, end), variations, into);
            i = end;
        }
    }

    private static void section(List<String> lines, Map<String, Map<String, Entry>> variations,
            Map<String, Definition> into) {
        int i = 0;
        while (i < lines.size()) {
            String line = strip(lines.get(i));
            if (line.isEmpty()) {
                i++;
                continue;
            }
            if (line.startsWith("minecraft version")) {
                if (!versionFits(line.substring(line.indexOf('=') + 1).strip())) {
                    return;
                }
                i++;
                continue;
            }
            if (line.startsWith("{") && line.endsWith(":")) {
                // A variation: its entries are indented further.
                int base = indent(lines.get(i));
                Map<String, Entry> entries = new LinkedHashMap<>();
                int j = i + 1;
                while (j < lines.size() && (blank(lines.get(j)) || indent(lines.get(j)) > base)) {
                    String entry = strip(lines.get(j));
                    int equals = entry.indexOf('=');
                    if (equals > 0) {
                        String value = entry.substring(equals + 1).strip();
                        Matcher tags = TAGS.matcher(value);
                        entries.put(entry.substring(0, equals).strip(), new Entry(entry.substring(0, equals).strip(),
                                tags.find() ? tags.group(1) : null));
                    }
                    j++;
                }
                variations.put(line.substring(0, line.length() - 1).strip().toLowerCase(Locale.ROOT), entries);
                i = j;
                continue;
            }
            int equals = line.indexOf('=');
            if (equals > 0) {
                alias(line.substring(0, equals).strip().toLowerCase(Locale.ROOT), line.substring(equals + 1).strip(),
                        variations, into);
            }
            i++;
        }
    }

    /** The components a variation entry adds: {@code {minecraft:potion_contents={...}}}. */
    private static final Pattern TAGS = Pattern.compile("\\{(.*)\\}");
    private static final Pattern VARIATION = Pattern.compile("\\{[^}]+\\}");
    private static final Pattern EXTRA = Pattern.compile("\\[(?:relatedEntity|deduplicate)=[^\\]]*\\]");

    private static void alias(String pattern, String value, Map<String, Map<String, Entry>> variations,
            Map<String, Definition> into) {
        List<String> items = new ArrayList<>();
        for (String item : EXTRA.matcher(value).replaceAll("").split("\\s*,\\s*")) {
            if (!item.isBlank()) {
                items.add(item.strip());
            }
        }
        // Names first, so each one has at most one variation in it: '(potion¦s of {potion types}|...)'.
        for (String form : AliasNames.forms(pattern)) {
            Matcher variation = VARIATION.matcher(form);
            if (!variation.find()) {
                into.put(form, new Definition(items));
                continue;
            }
            Map<String, Entry> entries = variations.get(variation.group().toLowerCase(Locale.ROOT));
            if (entries == null) {
                continue;
            }
            for (Entry entry : entries.values()) {
                String part = entry.name().equals("{default}") ? "" : entry.name();
                List<String> withTags = new ArrayList<>();
                for (String item : items) {
                    withTags.add(entry.components() == null || !item.contains(":") ? item
                            : item + "[" + entry.components() + "]");
                }
                String named = form.substring(0, variation.start()) + part + form.substring(variation.end());
                for (String name : AliasNames.forms(named.toLowerCase(Locale.ROOT))) {
                    into.putIfAbsent(name, new Definition(withTags));
                }
            }
        }
    }

    /** {@code 1.20.5 or newer}, {@code 1.20.4 or older}, {@code 1.19 to 1.20.4}: whether this server fits. */
    private static boolean versionFits(String condition) {
        int[] server = version(Bukkit.getMinecraftVersion());
        String[] parts = condition.split("\\s+");
        if (parts.length == 3 && parts[1].equals("or")) {
            int compare = compare(server, version(parts[0]));
            return parts[2].equals("newer") ? compare >= 0 : compare <= 0;
        }
        if (parts.length == 3 && parts[1].equals("to")) {
            return compare(server, version(parts[0])) >= 0 && compare(server, version(parts[2])) <= 0;
        }
        return compare(server, version(parts[0])) == 0;
    }

    private static int[] version(String text) {
        String[] parts = text.split("\\.");
        int[] version = new int[3];
        for (int i = 0; i < parts.length && i < 3; i++) {
            try {
                version[i] = Integer.parseInt(parts[i].replaceAll("[^0-9].*", ""));
            } catch (NumberFormatException e) {
                version[i] = 0;
            }
        }
        return version;
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    private static boolean blank(String line) {
        return strip(line).isEmpty();
    }

    /** A line without its comment ({@code #}, outside the components' braces). */
    private static String strip(String line) {
        int depth = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (c == '#' && depth == 0) {
                return line.substring(0, i).strip();
            }
        }
        return line.strip();
    }

    private static int indent(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == '\t' || line.charAt(i) == ' ')) {
            i++;
        }
        return i;
    }
}
