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
    /** Each block's default block data, as text: the same for every table. */
    private static final Map<Material, String> DEFAULT_BLOCKS = new ConcurrentHashMap<>();

    /** What loading alias files found. */
    public static final class Loaded {
        final AliasTable table;
        private final List<AliasLoader.Problem> problems;
        private final int files;

        Loaded(AliasTable table, List<AliasLoader.Problem> problems, int files) {
            this.table = table;
            this.problems = problems;
            this.files = files;
        }

        /** How many files were read from the aliases folder. */
        public int files() {
            return files;
        }

        /** How many names the alias files define, default aliases included. */
        public int aliases() {
            return table.count();
        }

        public int errors() {
            return (int) problems.stream().filter(AliasLoader.Problem::error).count();
        }

        /**
         * Gives each problem, in order, as a script problem would be given: line 0 is the file as a whole, like
         * Skript's {@code 'x.sk' is empty}.
         */
        public void problems(BiConsumer<Diagnostic, SourceFile> into) {
            for (AliasLoader.Problem problem : problems) {
                into.accept(new Diagnostic(problem.error() ? co.xenastudios.neoskript.frontend.source.Severity.ERROR : co.xenastudios.neoskript.frontend.source.Severity.WARNING,
                        problem.file().name(), problem.line(), 0, 0, problem.message()), problem.file());
            }
        }
    }

    /**
     * Reads the aliases: the defaults if {@code defaults}, then the {@code .sk} files in {@code folder} and its
     * folders (not those starting with {@code .}), as Skript reads {@code plugins/Skript/aliases}. A file that can't
     * be read is reported and left out; nothing here throws for what's in the files. Call {@link #install} to use them.
     *
     * @param dataFolder the plugin's folder, where {@code aliases-english.zip} replaces the jar's defaults if it's
     *     there, or {@code null}
     */
    public static Loaded load(Path folder, Path dataFolder, boolean defaults) {
        List<AliasLoader.Input> inputs = new ArrayList<>();
        List<AliasLoader.Problem> problems = new ArrayList<>();
        if (defaults) {
            Path zip = dataFolder == null ? null : dataFolder.resolve("aliases-english.zip");
            if (zip != null && Files.isRegularFile(zip)) {
                try (FileSystem zipFs = FileSystems.newFileSystem(zip)) {
                    readFolder(zipFs.getPath("/"), inputs, problems, 0, new int[] {0});
                } catch (IOException | RuntimeException e) {
                    problems.add(new AliasLoader.Problem(new SourceFile("aliases-english.zip", ""), 0, true,
                            "Couldn't read aliases-english.zip: " + e.getMessage()));
                }
            } else {
                for (String name : DEFAULT_FILES) {
                    try (InputStream in = GlobalAliases.class.getResourceAsStream("/aliases-english/" + name)) {
                        if (in != null) {
                            inputs.add(new AliasLoader.Input(name, text(in.readAllBytes())));
                        }
                    } catch (IOException e) {
                        // The jar's own files; never missing.
                    }
                }
            }
        }
        int defaultFiles = inputs.size();
        if (folder != null && Files.isDirectory(folder)) {
            readFolder(folder, inputs, problems, 0, new int[] {0});
        }
        AliasTable loaded;
        try {
            loaded = AliasLoader.load(new BukkitServer(), inputs, problems);
        } catch (RuntimeException | StackOverflowError | OutOfMemoryError e) {
            // Never stops the plugin: without aliases, the items' own names still work.
            problems.add(new AliasLoader.Problem(new SourceFile("aliases", ""), 0, true, "Couldn't load the aliases: " + e));
            loaded = AliasTable.EMPTY;
        }
        return new Loaded(loaded, problems, inputs.size() - defaultFiles);
    }

    /** Skript's {@code Aliases.loadDirectory}: files and folders in the order of their names. */
    private static void readFolder(Path folder, List<AliasLoader.Input> inputs, List<AliasLoader.Problem> problems,
            int depth, int[] count) {
        if (depth > MAX_DEPTH) {
            return;
        }
        List<Path> entries;
        try (Stream<Path> list = Files.list(folder)) {
            entries = list.sorted().toList();
        } catch (IOException | RuntimeException e) {
            problems.add(new AliasLoader.Problem(new SourceFile(String.valueOf(folder.getFileName()), ""), 0, true,
                    "Couldn't read the folder: " + e.getMessage()));
            return;
        }
        for (Path entry : entries) {
            String name = String.valueOf(entry.getFileName());
            if (name.endsWith("/")) {
                name = name.substring(0, name.length() - 1);
            }
            if (Files.isDirectory(entry)) {
                if (!name.startsWith(".")) {
                    readFolder(entry, inputs, problems, depth + 1, count);
                }
                continue;
            }
            if (!name.endsWith(".sk")) {
                continue;
            }
            if (++count[0] > MAX_FILES) {
                if (count[0] == MAX_FILES + 1) {
                    problems.add(new AliasLoader.Problem(new SourceFile(name, ""), 0, true,
                            "There are more than " + MAX_FILES + " alias files, so the rest aren't read"));
                }
                return;
            }
            try {
                long size = Files.size(entry);
                if (size > MAX_FILE_BYTES) {
                    problems.add(new AliasLoader.Problem(new SourceFile(name, ""), 0, true, "'" + name + "' is "
                            + size / (1 << 20) + " MB, more than the " + (MAX_FILE_BYTES >> 20) + " MB an alias file may be,"
                            + " so it isn't read"));
                    continue;
                }
                inputs.add(new AliasLoader.Input(name, text(Files.readAllBytes(entry))));
            } catch (IOException | RuntimeException e) {
                problems.add(new AliasLoader.Problem(new SourceFile(name, ""), 0, true,
                        "Couldn't read '" + name + "': " + e.getMessage()));
            }
        }
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
                    table = known;
                }
            }
        }
        return known;
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
        return changes(before == null ? current() : before.table, after.table, limit);
    }

    /** The table in use, as a {@link Loaded} with no problems, to compare with a reload. */
    public static Loaded inUse() {
        return new Loaded(current(), List.of(), 0);
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

    /**
     * What an alias file calls an item, as Skript's {@code AliasesMap.matchAlias}: the alias for exactly this item,
     * else the plain item's, else the first of its material's. {@code null} if no alias names its material.
     *
     * @param stack the item, or {@code null} for a block that has no item
     * @param blockData its block's data, or {@code null} for the default
     */
    static AliasTable.Named named(Material material, ItemStack stack, BlockData blockData) {
        AliasTable.Names names = current().names(material.getKey().toString());
        if (names == null) {
            return null;
        }
        String blockKey = null;
        if (material.isBlock()) {
            try {
                blockKey = blockData != null ? blockData.getAsString(false)
                        : DEFAULT_BLOCKS.computeIfAbsent(material, each -> each.createBlockData().getAsString(false));
            } catch (RuntimeException e) {
                blockKey = null;
            }
        }
        for (AliasTable.Named named : names.items) {
            AliasTable.Item item = named.item();
            boolean sameItem = stack == null || !(item.stack() instanceof ItemStack proto) || proto.isSimilar(stack);
            boolean sameBlock = blockKey == null || item.blockKey() == null || blockKey.equals(item.blockKey());
            if (sameItem && sameBlock) {
                return named;
            }
        }
        if (names.defaultItem != null) {
            return names.defaultItem;
        }
        return names.items.isEmpty() ? null : names.items.getFirst();
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
                AliasTable.Named named = named(material, material.isItem() ? new ItemStack(material) : null, null);
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
