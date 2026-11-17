/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.frontend.source.Diagnostic;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

/**
 * The aliases every script sees, as Skript loads them: its default aliases (from the jar, or
 * {@code aliases-english.zip} in the plugin's folder), unless {@code load default aliases} is off, then the alias
 * files in the plugin's {@code aliases} folder, in the order of their paths. Scripts' own {@code aliases:} come first,
 * and NeoSkript's names for the items no alias names come after.
 */
public final class GlobalAliases {

    private GlobalAliases() {}

    /** Skript's default alias files, in the order Skript reads them. */
    private static final String[] DEFAULT_FILES = {"+global-variations.sk", "brewing.sk", "combat.sk", "decoration.sk",
        "foodstuffs.sk", "misc.sk", "redstone.sk", "transportation.sk"};

    /** The largest alias file read; a bigger one is left out with an error. */
    static final long MAX_FILE_BYTES = 16L << 20;
    /** The most alias files read, and how deep folders go, so a folder that links to itself can't loop. */
    static final int MAX_FILES = 10_000;
    static final int MAX_DEPTH = 32;

    private static volatile AliasTable table;
    /** The entity each material's item relates to, for the current table. */
    private static volatile Map<String, String> related = new ConcurrentHashMap<>();
    private static final String NONE = "";

    /** What loading alias files found. */
    public static final class Loaded {
        /** The aliases, or {@code null} if loading failed, which leaves those in use as they are. */
        final AliasTable table;
        private final List<AliasLoader.Problem> problems;
        private final int moreProblems;
        private final int moreErrors;
        private final int files;

        Loaded(AliasTable table, List<AliasLoader.Problem> problems, int moreProblems, int moreErrors, int files) {
            this.table = table;
            this.problems = problems;
            this.moreProblems = moreProblems;
            this.moreErrors = moreErrors;
            this.files = files;
        }

        /** How many errors there were past {@link AliasLoader#MAX_PROBLEMS}, which aren't listed. */
        public int unlistedErrors() {
            return moreErrors;
        }

        /** How many warnings there were past {@link AliasLoader#MAX_PROBLEMS}, which aren't listed. */
        public int unlistedWarnings() {
            return moreProblems - moreErrors;
        }

        /** The line saying how many problems weren't listed, or {@code null} if all were. */
        public String unlistedNote() {
            return moreProblems == 0 ? null : "... and " + moreProblems + " more "
                    + (moreProblems == 1 ? "problem" : "problems") + " in the alias files, not listed";
        }

        /** How many files were read from the aliases folder. */
        public int files() {
            return files;
        }

        /** How many names the alias files define, default aliases included. */
        public int aliases() {
            return table == null ? 0 : table.count();
        }

        /** Whether loading failed as a whole, so the aliases in use were kept. */
        public boolean failed() {
            return table == null;
        }

        /** How many errors there were, the unlisted ones included. */
        public int errors() {
            return (int) problems.stream().filter(AliasLoader.Problem::error).count() + moreErrors;
        }

        /**
         * Gives each problem, in order, with the line of code it's on ({@code ""} for none): line 0 is the file as a
         * whole, like Skript's {@code 'x.sk' is empty}. Those past {@link AliasLoader#MAX_PROBLEMS} aren't given:
         * {@link #unlistedErrors}, {@link #unlistedWarnings} and {@link #unlistedNote} say how many there were.
         */
        public void problems(BiConsumer<Diagnostic, String> into) {
            for (AliasLoader.Problem problem : problems) {
                into.accept(new Diagnostic(problem.error() ? co.xenastudios.neoskript.frontend.source.Severity.ERROR
                        : co.xenastudios.neoskript.frontend.source.Severity.WARNING, problem.file(), problem.line(), 0, 0,
                        problem.message()), problem.code());
            }
        }
    }

    /** The most bytes of alias files read in all; the rest are left out with an error. */
    static final long MAX_TOTAL_BYTES = 64L << 20;

    /**
     * Reads the aliases: the defaults if {@code defaults}, then the {@code .sk} files in {@code folder} and its
     * folders (not those starting with {@code .}), as Skript reads {@code plugins/Skript/aliases}. Each file is read
     * and loaded in turn, so only one file's text is held at a time. A file that can't be read is reported and left
     * out. Nothing here throws: if loading fails as a whole, even for lack of memory, the result says so and
     * {@link #install} keeps the aliases in use.
     *
     * @param dataFolder the plugin's folder, where {@code aliases-english.zip} replaces the jar's defaults if it's
     *     there, or {@code null}
     */
    public static Loaded load(Path folder, Path dataFolder, boolean defaults) {
        AliasLoader loader = null;
        int[] files = {0};
        try {
            AliasLoader current = new AliasLoader(new BukkitServer());
            loader = current;
            long[] bytes = {0};
            if (defaults) {
                Path zip = dataFolder == null ? null : dataFolder.resolve("aliases-english.zip");
                if (zip != null && Files.isRegularFile(zip)) {
                    try (FileSystem zipFs = FileSystems.newFileSystem(zip)) {
                        readFolder(zipFs.getPath("/"), current, 0, new int[] {0}, bytes, MAX_TOTAL_BYTES);
                    } catch (IOException | RuntimeException e) {
                        current.fileProblem("aliases-english.zip", true, "Couldn't read aliases-english.zip: " + e.getMessage());
                    }
                } else {
                    for (String name : DEFAULT_FILES) {
                        try (InputStream in = GlobalAliases.class.getResourceAsStream("/aliases-english/" + name)) {
                            if (in != null) {
                                current.read(new AliasLoader.Input(name, text(in.readAllBytes())));
                            }
                        } catch (IOException e) {
                            // The jar's own files; never missing.
                        }
                    }
                }
            }
            if (folder != null && Files.isDirectory(folder)) {
                files[0] = readFolder(folder, current, 0, new int[] {0}, bytes, MAX_TOTAL_BYTES);
            }
            AliasTable table = current.finish();
            return new Loaded(table, List.copyOf(current.problems()), current.moreProblems(), current.moreErrors(),
                    files[0]);
        } catch (RuntimeException | StackOverflowError | OutOfMemoryError | LinkageError e) {
            // Never stops the plugin: the aliases in use stay. What was found before the failure is still reported.
            List<AliasLoader.Problem> problems = new ArrayList<>();
            int more = 0;
            int moreErrors = 0;
            if (loader != null) {
                problems.addAll(loader.problems());
                more = loader.moreProblems();
                moreErrors = loader.moreErrors();
            }
            loader = null;
            problems.add(new AliasLoader.Problem("aliases", 0, true, "Couldn't load the aliases, so the aliases in use"
                    + " are kept: " + e, ""));
            return new Loaded(null, problems, more, moreErrors, files[0]);
        }
    }

    /**
     * Skript's {@code Aliases.loadDirectory}: files and folders in the order of their names, each loaded as it's
     * read. Returns how many files were read.
     */
    static int readFolder(Path folder, AliasLoader loader, int depth, int[] count, long[] bytes, long maxTotalBytes) {
        if (depth > MAX_DEPTH) {
            return 0;
        }
        List<Path> entries;
        try (Stream<Path> list = Files.list(folder)) {
            entries = list.sorted().toList();
        } catch (IOException | RuntimeException e) {
            loader.fileProblem(String.valueOf(folder.getFileName()), true, "Couldn't read the folder: " + e.getMessage());
            return 0;
        }
        int read = 0;
        for (Path entry : entries) {
            String name = String.valueOf(entry.getFileName());
            if (name.endsWith("/")) {
                name = name.substring(0, name.length() - 1);
            }
            if (Files.isDirectory(entry)) {
                if (!name.startsWith(".")) {
                    read += readFolder(entry, loader, depth + 1, count, bytes, maxTotalBytes);
                }
                continue;
            }
            if (!name.endsWith(".sk")) {
                continue;
            }
            if (++count[0] > MAX_FILES) {
                if (count[0] == MAX_FILES + 1) {
                    loader.fileProblem(name, true, "There are more than " + MAX_FILES + " alias files, so the rest"
                            + " aren't read");
                }
                return read;
            }
            try {
                long size = Files.size(entry);
                if (size > MAX_FILE_BYTES) {
                    loader.fileProblem(name, true, "'" + name + "' is " + size / (1 << 20) + " MB, more than the "
                            + (MAX_FILE_BYTES >> 20) + " MB an alias file may be, so it isn't read");
                    continue;
                }
                if (bytes[0] + size > maxTotalBytes) {
                    loader.fileProblem(name, true, "The alias files are more than " + (maxTotalBytes >> 20)
                            + " MB in all, so '" + name + "' isn't read");
                    continue;
                }
                bytes[0] += size;
                loader.read(new AliasLoader.Input(name, text(Files.readAllBytes(entry))));
                read++;
            } catch (IOException | RuntimeException e) {
                loader.fileProblem(name, true, "Couldn't read '" + name + "': " + e.getMessage());
            }
        }
        return read;
    }

    /** A file's text as UTF-8, with anything that isn't replaced and a byte order mark left out. */
    private static String text(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, StandardCharsets.UTF_8);
        }
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    /** Uses {@code loaded} from now on: for scripts compiled after, and items written after. */
    public static void install(Loaded loaded) {
        if (loaded.table == null) {
            return;
        }
        table = loaded.table;
        related = new ConcurrentHashMap<>();
    }

    /** The aliases in use: Skript's defaults alone until {@link #install} is called. */
    static AliasTable current() {
        AliasTable known = table;
        if (known == null) {
            synchronized (GlobalAliases.class) {
                known = table;
                if (known == null) {
                    known = load(null, null, true).table;
                    known = known == null ? AliasTable.EMPTY : known;
                    table = known;
                }
            }
        }
        return known;
    }

    /** The item names each script looked up while it compiled, while {@link #recordUses} records them. */
    private static volatile Map<String, Set<String>> uses;

    /**
     * Starts recording the item names the scripts compiled from now on look up, by script: names from alias files,
     * NeoSkript's own names, and names that didn't resolve, after options are put in.
     */
    public static void recordUses() {
        uses = new ConcurrentHashMap<>();
    }

    /** Stops recording, and gives what was recorded, by script. */
    public static Map<String, Set<String>> recordedUses() {
        Map<String, Set<String>> recorded = uses;
        uses = null;
        return recorded == null ? Map.of() : recorded;
    }

    /** Records that the script being compiled looked up the item name {@code name}. */
    static void used(String name) {
        Map<String, Set<String>> recording = uses;
        if (recording == null) {
            return;
        }
        String script = co.xenastudios.neoskript.runtime.ScriptFeatures.compilingScript();
        if (script != null) {
            recording.computeIfAbsent(script, key -> ConcurrentHashMap.newKeySet()).add(name);
        }
    }

    /** The alias called {@code name} (in lower case), or {@code null}. */
    static AliasTable.Type alias(String name) {
        return current().get(name);
    }

    /** Whether NeoSkript's own names for {@code material} are left out, as an alias file names it. */
    static boolean namedByFiles(Material material) {
        return current().namedByFiles(material.getKey().toString());
    }

    /**
     * The names whose meaning differs between the aliases in use before and {@code after}: names an alias file
     * defines differently, and NeoSkript's own names for the materials that are or aren't named by files now. With
     * more than {@code limit} of them, {@code null}.
     */
    public static Set<String> changes(Loaded before, Loaded after, int limit) {
        if (after.table == null) {
            // Loading failed, so the aliases in use stay and nothing changed.
            return Set.of();
        }
        return changes(before == null || before.table == null ? current() : before.table, after.table, limit);
    }

    /** The table in use, as a {@link Loaded} with no problems, to compare with a reload. */
    public static Loaded inUse() {
        return new Loaded(current(), List.of(), 0, 0, 0);
    }

    static Set<String> changes(AliasTable before, AliasTable after, int limit) {
        Set<String> changed = new HashSet<>();
        Set<String> names = new HashSet<>(before.names());
        names.addAll(after.names());
        for (String name : names) {
            if (!AliasTable.same(before.get(name), after.get(name))) {
                changed.add(name);
                if (changed.size() > limit) {
                    return null;
                }
            }
        }
        Set<String> materials = new HashSet<>(before.namedMaterials());
        Set<String> now = after.namedMaterials();
        Set<String> differ = new HashSet<>(materials);
        differ.addAll(now);
        materials.retainAll(now);
        differ.removeAll(materials);
        for (String key : differ) {
            Material material = Material.matchMaterial(key);
            if (material != null) {
                changed.addAll(ItemType.ownNames(material));
                if (changed.size() > limit) {
                    return null;
                }
            }
        }
        return changed;
    }

    // --- names ---

    /** Skript's {@code MatchQuality}, best first. */
    private static final int EXACT = 0;
    private static final int SAME_ITEM = 1;
    private static final int SAME_MATERIAL = 2;

    /**
     * What an alias file calls an item, as Skript's {@code AliasesMap.matchAlias}: the first of its material's aliases
     * that is the same item or better (an alias's item with a name or enchantments added is still that alias), else
     * the plain item's alias, else the first that's the same material. {@code null} if no alias names its material.
     *
     * @param stack the item, or {@code null} for a block that has no item
     * @param blockData its block's data, or {@code null} for none
     * @param itemForm whether it's an item stack, such as one in an inventory, rather than an item type: as in Skript,
     *     an item stack without block data isn't a block alias with states of its own
     */
    static AliasTable.Named named(Material material, ItemStack stack, BlockData blockData, boolean itemForm) {
        AliasTable.Names names = current().names(material.getKey().toString());
        if (names == null) {
            return null;
        }
        int best = SAME_MATERIAL + 1;
        AliasTable.Named bestMatch = null;
        for (AliasTable.Named named : names.items) {
            int quality = quality(stack, blockData, itemForm, named.item());
            if (quality < best) {
                best = quality;
                bestMatch = named;
            }
        }
        if (best < SAME_MATERIAL) {
            return bestMatch;
        }
        return names.defaultItem != null ? names.defaultItem : bestMatch;
    }

    /** Skript's {@code ItemData.matchAlias}: how well an item and its block data match an alias's item. */
    private static int quality(ItemStack stack, BlockData blockData, boolean itemForm, AliasTable.Item alias) {
        int quality = EXACT;
        if (alias.blockData() instanceof BlockData theirs) {
            if (blockData == null) {
                // An item stack, which has no block states, isn't an alias with states of its own.
                if (itemForm && !alias.states().isEmpty()) {
                    return SAME_MATERIAL;
                }
            } else if (!blockData.equals(theirs)) {
                quality = blockData.matches(theirs) ? SAME_ITEM : SAME_MATERIAL;
            }
        }
        if (stack != null && alias.stack() instanceof ItemStack proto
                && (quality <= SAME_ITEM && stack.hasItemMeta() || proto.hasItemMeta())) {
            int meta = compareMetas(stack.getItemMeta(), proto.getItemMeta());
            if (meta == SAME_MATERIAL && alias.components() == null) {
                // An alias with nothing applied doesn't care about the item's meta.
                quality = SAME_ITEM;
            } else if (quality < meta) {
                quality = meta;
            }
        }
        return quality;
    }

    /** Skript's {@code ItemData.compareItemMetas}: ours has more than theirs, or something else. */
    private static int compareMetas(org.bukkit.inventory.meta.ItemMeta ours, org.bukkit.inventory.meta.ItemMeta theirs) {
        if (ours == null || theirs == null) {
            return ours == theirs ? EXACT : SAME_MATERIAL;
        }
        int quality = EXACT;
        quality = Math.max(quality, part(ours.hasDisplayName() ? ours.displayName() : null,
                theirs.hasDisplayName() ? theirs.displayName() : null));
        quality = Math.max(quality, part(ours.hasLore() ? ours.lore() : null, theirs.hasLore() ? theirs.lore() : null));
        Map<org.bukkit.enchantments.Enchantment, Integer> ourEnchants = ours.getEnchants();
        Map<org.bukkit.enchantments.Enchantment, Integer> theirEnchants = theirs.getEnchants();
        quality = Math.max(quality, part(ourEnchants.isEmpty() ? null : ourEnchants,
                theirEnchants.isEmpty() ? null : theirEnchants));
        Set<org.bukkit.inventory.ItemFlag> ourFlags = ours.getItemFlags();
        Set<org.bukkit.inventory.ItemFlag> theirFlags = theirs.getItemFlags();
        quality = Math.max(quality, part(ourFlags.isEmpty() ? null : ourFlags, theirFlags.isEmpty() ? null : theirFlags));
        org.bukkit.inventory.meta.ItemMeta first = ours.clone();
        org.bukkit.inventory.meta.ItemMeta second = theirs.clone();
        for (org.bukkit.inventory.meta.ItemMeta meta : List.of(first, second)) {
            meta.displayName(null);
            meta.lore(null);
            for (org.bukkit.enchantments.Enchantment enchantment : List.copyOf(meta.getEnchants().keySet())) {
                meta.removeEnchant(enchantment);
            }
            meta.removeItemFlags(meta.getItemFlags().toArray(new org.bukkit.inventory.ItemFlag[0]));
        }
        return first.equals(second) ? quality : SAME_MATERIAL;
    }

    /** One part of two metas: the same, ours has it and theirs doesn't, or they differ. */
    private static int part(Object ours, Object theirs) {
        if (java.util.Objects.equals(ours, theirs)) {
            return EXACT;
        }
        return ours != null && theirs == null ? SAME_ITEM : SAME_MATERIAL;
    }

    /**
     * The entity an alias relates the material with key {@code key} (without {@code minecraft:}) to, like
     * {@code primed tnt} for tnt, as Skript's {@code Aliases.getRelatedEntity}, or {@code null}.
     */
    static String relatedEntity(String key) {
        Map<String, String> known = related;
        String found = known.get(key);
        if (found == null) {
            found = NONE;
            Material material = Material.matchMaterial(key);
            if (material != null) {
                AliasTable.Named named = named(material, material.isItem() ? new ItemStack(material) : null, null, false);
                if (named != null && named.item().relatedEntity() != null) {
                    found = named.item().relatedEntity().strip();
                }
            }
            known.put(key, found);
        }
        return found.isEmpty() ? null : found;
    }

    // --- the server ---

    /** {@link AliasLoader.Server} on the running server. */
    private static final class BukkitServer implements AliasLoader.Server {

        private final Map<String, Material> materials = new HashMap<>();

        private Material of(String key) {
            return materials.computeIfAbsent(key, Material::matchMaterial);
        }

        @Override
        public String material(String id) {
            Material material;
            try {
                material = Material.matchMaterial(id.toLowerCase(java.util.Locale.ROOT).startsWith("minecraft:") ? id
                        : id.replace(":", "_"));
            } catch (RuntimeException e) {
                return null;
            }
            if (material == null || material.isLegacy()) {
                return null;
            }
            String key = material.getKey().toString();
            materials.putIfAbsent(key, material);
            return key;
        }

        @Override
        public boolean isItem(String material) {
            Material found = of(material);
            return found != null && found.isItem();
        }

        @Override
        public boolean isBlock(String material) {
            Material found = of(material);
            return found != null && found.isBlock();
        }

        @Override
        public AliasLoader.Made item(String material, String components) {
            Material found = of(material);
            if (components == null) {
                return new AliasLoader.Made(new ItemStack(found), "");
            }
            ItemStack stack;
            try {
                stack = Bukkit.getItemFactory().createItemStack(material + components);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            if (stack.getType() != found) {
                throw new IllegalArgumentException("the components changed the item");
            }
            String key;
            try {
                key = stack.getItemMeta().getAsComponentString();
            } catch (RuntimeException e) {
                key = components;
            }
            return new AliasLoader.Made(stack, key);
        }

        @Override
        public AliasLoader.Made blockData(String material, String states) {
            Material found = of(material);
            if (found == null || !found.isBlock()) {
                throw new IllegalArgumentException("not a block");
            }
            BlockData data;
            try {
                data = Bukkit.createBlockData(found, states);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            return new AliasLoader.Made(data, data.getAsString(false));
        }

        @Override
        public int[] version() {
            try {
                return AliasLoader.parseVersion(Bukkit.getMinecraftVersion());
            } catch (IllegalArgumentException e) {
                return new int[3];
            }
        }

        @Override
        public List<String> materials() {
            List<String> keys = new ArrayList<>();
            for (Material material : Material.values()) {
                if (!material.isLegacy()) {
                    keys.add(material.getKey().toString());
                }
            }
            return keys;
        }
    }
}
