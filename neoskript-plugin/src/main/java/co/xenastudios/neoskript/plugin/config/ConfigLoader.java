/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Loads a config file against its {@link ConfigSpec}.
 *
 * <p>Reading and writing fail differently. A file that can't be read, or isn't valid YAML, is an error: there
 * are no settings to use, and the file is left as it is. A file that was read but can't be written, such as a
 * read-only mount, is only a warning: its settings are used as read, and the changes the loader wanted to make
 * (new settings, carrying it forward) wait until it can write.
 */
public final class ConfigLoader {

    private static final Pattern BLANK_WITH_SPACES = Pattern.compile("(?m)^[ \\t]+$");
    /** {@link Reader#BLANK} as Bukkit writes it, in any quotes, before the end of the line or a comment. */
    private static final Pattern BLANK_VALUE =
            Pattern.compile(":[ \\t]+([\"']?)" + Pattern.quote(Reader.BLANK) + "\\1(?=[ \\t]|$)", Pattern.MULTILINE);

    private ConfigLoader() {}

    /**
     * What loading found.
     *
     * @param config the settings to use
     * @param warnings settings that couldn't be used and fell back to their defaults, and changes to the file that
     *     couldn't be written
     * @param notices what the loader changed in the file, such as adding new settings
     * @param carriedFrom the config version the file was carried forward from, or 0 if it wasn't
     */
    public record Result<C>(C config, List<String> warnings, List<String> notices, int carriedFrom) {}

    /**
     * What {@link #set} did.
     *
     * @param written whether the settings were written to the file
     */
    public record Change(boolean written, List<String> warnings, List<String> notices) {}

    /** The default config file, as written on first start. */
    public static String defaultText(ConfigSpec<?> spec) {
        return Emitter.render(spec);
    }

    /**
     * Reads the file, writing the defaults first if it doesn't exist. An old file is backed up and carried
     * forward in the current layout, keeping every value the owner set and any keys that aren't settings. A
     * current file gets any new settings added through Bukkit, which keeps the owner's comments. A file from a
     * newer version is never written.
     *
     * @throws ConfigException if the file can't be read or isn't valid YAML; it's left untouched
     */
    public static <C extends Record> Result<C> load(ConfigSpec<C> spec, Path file) throws ConfigException {
        List<String> notices = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (Files.notExists(file)) {
            try {
                write(file, Emitter.render(spec));
                notices.add("Wrote the default " + file.getFileName() + ".");
            } catch (IOException e) {
                warnings.add("Couldn't write the default " + file.getFileName() + ": " + e.getMessage()
                        + "; using the defaults.");
            }
            return new Result<>(spec.defaults, List.copyOf(warnings), List.copyOf(notices), 0);
        }

        Parsed parsed = read(file);
        YamlConfiguration yaml = parsed.yaml();
        int version = version(spec, yaml, warnings);
        boolean newer = version > spec.version;
        boolean older = version < spec.version;
        if (newer) {
            warnings.add(("%s is from a newer NeoSkript (config version %d; this one reads %d). Settings it doesn't"
                    + " know are ignored, and the file is left as it is.").formatted(file.getFileName(), version,
                    spec.version));
        } else if (older) {
            migrate(spec, version, parsed);
        }

        Reader reader = new Reader(spec);
        C config = reader.readRoot(spec.type, yaml, spec.defaults);
        warnings.addAll(reader.warnings);
        List<String> added = new ArrayList<>();
        for (Reader.Missing missing : reader.missing) {
            String path = missing.parent().getCurrentPath();
            added.add((path == null || path.isEmpty() ? "" : path + ".") + missing.key());
        }

        if (older) {
            try {
                Path backup = backup(file, version);
                write(file, Emitter.render(spec, yaml));
                notices.add("Carried %s forward from config version %d to %d, keeping your settings. The old file is %s."
                        .formatted(file.getFileName(), version, spec.version, backup.getFileName()));
                added(notices, added);
                kept(notices, reader.unknown, backup);
            } catch (IOException e) {
                warnings.add(couldNotUpdate(file, e));
            }
            return new Result<>(config, List.copyOf(warnings), List.copyOf(notices), version);
        }
        if (!newer) {
            boolean changed = false;
            if (!yaml.contains(Writer.VERSION_KEY)) {
                yaml.set(Writer.VERSION_KEY, spec.version);
                yaml.setComments(Writer.VERSION_KEY, List.of(Emitter.VERSION_COMMENT));
                changed = true;
            }
            for (Reader.Missing missing : reader.missing) {
                Writer.add(spec, missing.parent(), missing.key(), missing.component(), missing.value());
                changed = true;
            }
            if (changed) {
                try {
                    write(file, save(yaml));
                    added(notices, added);
                } catch (IOException e) {
                    warnings.add(couldNotUpdate(file, e));
                }
            }
        }
        return new Result<>(config, List.copyOf(warnings), List.copyOf(notices), 0);
    }

    /**
     * Sets some settings in the file, by their paths, as for settings copied from another plugin. A current file
     * is changed through Bukkit, which keeps its comments and other keys. An older one is backed up and carried
     * forward with the settings. A file from a newer NeoSkript is left as it is, and nothing is set.
     *
     * @throws ConfigException if the file can't be read or isn't valid YAML; it's left untouched
     */
    public static <C extends Record> Change set(ConfigSpec<C> spec, Path file, Map<String, Object> values)
            throws ConfigException {
        Parsed parsed = read(file);
        YamlConfiguration yaml = parsed.yaml();
        List<String> warnings = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        int version = version(spec, yaml, warnings);
        if (version > spec.version) {
            warnings.add(("%s is from a newer NeoSkript (config version %d; this one reads %d), so it's left as it is,"
                    + " without %s.").formatted(file.getFileName(), version, spec.version, String.join(", ", values.keySet())));
            return new Change(false, List.copyOf(warnings), List.of());
        }
        boolean older = version < spec.version;
        if (older) {
            migrate(spec, version, parsed);
        }
        values.forEach(yaml::set);
        Reader reader = new Reader(spec);
        reader.readRoot(spec.type, yaml, spec.defaults);
        try {
            if (older) {
                Path backup = backup(file, version);
                write(file, Emitter.render(spec, yaml));
                notices.add("Carried %s forward from config version %d to %d, keeping your settings. The old file is %s."
                        .formatted(file.getFileName(), version, spec.version, backup.getFileName()));
                kept(notices, reader.unknown, backup);
            } else {
                write(file, save(yaml));
            }
        } catch (IOException e) {
            warnings.add(couldNotUpdate(file, e));
            return new Change(false, List.copyOf(warnings), List.copyOf(notices));
        }
        return new Change(true, List.copyOf(warnings), List.copyOf(notices));
    }

    /** The file as Bukkit reads it, with {@link Reader#BLANK} for keys that have no value. */
    private record Parsed(YamlConfiguration yaml) {}

    /**
     * Reads the file. Keys with no value ({@code prefix:}, {@code foo: ~}) are given {@link Reader#BLANK} before
     * Bukkit reads it, as Bukkit would drop them with their comments; {@link #save} writes them back empty, in
     * place. Any failure to read or parse it is a {@link ConfigException}.
     */
    private static Parsed read(Path file) throws ConfigException {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            throw new ConfigException("Couldn't read " + file.getFileName() + ": " + e.getMessage(), e);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(marked(text));
        } catch (InvalidConfigurationException | RuntimeException | StackOverflowError e) {
            throw new ConfigException(file.getFileName() + " isn't valid YAML: " + firstLine(e), e);
        }
        return new Parsed(yaml);
    }

    /** The YAML with {@link Reader#BLANK} as the value of each key that has none, comments kept. */
    private static String marked(String text) {
        // As Bukkit reads YAML: any number of aliases, any length.
        LoaderOptions loader = new LoaderOptions();
        loader.setMaxAliasesForCollections(Integer.MAX_VALUE);
        loader.setCodePointLimit(Integer.MAX_VALUE);
        loader.setProcessComments(true);
        DumperOptions dumper = new DumperOptions();
        dumper.setProcessComments(true);
        dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumper.setSplitLines(false);
        Yaml yaml = new Yaml(new SafeConstructor(loader), new Representer(dumper), dumper, loader);
        Node root = yaml.compose(new java.io.StringReader(text));
        if (root == null || !mark(root, new java.util.IdentityHashMap<>())) {
            return text;
        }
        java.io.StringWriter out = new java.io.StringWriter();
        yaml.serialize(root, out);
        return out.toString();
    }

    /** Puts {@link Reader#BLANK} in place of the null values in mappings. Returns whether there were any. */
    private static boolean mark(Node node, java.util.IdentityHashMap<Node, Boolean> seen) {
        if (seen.put(node, Boolean.TRUE) != null) {
            return false;
        }
        boolean marked = false;
        if (node instanceof MappingNode mapping) {
            List<NodeTuple> tuples = mapping.getValue();
            for (int i = 0; i < tuples.size(); i++) {
                NodeTuple tuple = tuples.get(i);
                Node value = tuple.getValueNode();
                if (value instanceof ScalarNode scalar && Tag.NULL.equals(scalar.getTag())) {
                    ScalarNode blank = new ScalarNode(Tag.STR, Reader.BLANK, scalar.getStartMark(), scalar.getEndMark(),
                            DumperOptions.ScalarStyle.PLAIN);
                    blank.setBlockComments(scalar.getBlockComments());
                    blank.setInLineComments(scalar.getInLineComments());
                    blank.setEndComments(scalar.getEndComments());
                    tuples.set(i, new NodeTuple(tuple.getKeyNode(), blank));
                    marked = true;
                } else {
                    marked |= mark(value, seen);
                }
            }
        } else if (node instanceof SequenceNode sequence) {
            for (Node item : sequence.getValue()) {
                marked |= mark(item, seen);
            }
        }
        return marked;
    }

    /**
     * The file's version: its {@code config-version}, or for a file without one, the spec's unversioned version
     * unless it has a key only the current version has.
     */
    private static int version(ConfigSpec<?> spec, YamlConfiguration yaml, List<String> warnings) {
        Object raw = yaml.get(Writer.VERSION_KEY);
        if (raw instanceof Integer v) {
            return v;
        }
        if (raw != null) {
            warnings.add(Writer.VERSION_KEY + " isn't a whole number; treating the config as current.");
            return spec.version;
        }
        if (spec.unversioned > 0 && spec.currentKeys.stream().noneMatch(yaml::contains)) {
            return spec.unversioned;
        }
        return spec.version;
    }

    private static void migrate(ConfigSpec<?> spec, int version, Parsed parsed) {
        for (Map.Entry<Integer, Migration> step : spec.migrations.tailMap(Math.max(version, 1)).entrySet()) {
            step.getValue().apply(parsed.yaml());
        }
    }

    /** The file as Bukkit writes it, with the keys that had no value written back empty, in place. */
    private static String save(YamlConfiguration yaml) {
        String text = BLANK_VALUE.matcher(yaml.saveToString()).replaceAll(":");
        // Bukkit indents the blank lines it writes; trim them.
        return BLANK_WITH_SPACES.matcher(text).replaceAll("");
    }

    private static String couldNotUpdate(Path file, IOException e) {
        return "Couldn't update " + file.getFileName() + ": " + e.getMessage() + "; using its settings as read.";
    }

    private static void added(List<String> notices, List<String> added) {
        if (added.size() == 1) {
            notices.add("Added " + added.getFirst() + " with its default.");
        } else if (!added.isEmpty()) {
            notices.add("Added " + added.size() + " new settings with their defaults: " + String.join(", ", added) + ".");
        }
    }

    private static void kept(List<String> notices, List<String> unknown, Path backup) {
        if (!unknown.isEmpty()) {
            notices.add("Kept %s, which %s NeoSkript's, as %s. Comments on NeoSkript's settings are its own now; yours are in %s."
                    .formatted(String.join(", ", unknown), unknown.size() == 1 ? "isn't" : "aren't",
                            unknown.size() == 1 ? "it was" : "they were", backup.getFileName()));
        }
    }

    // config.yml -> config.yml.bak-v1, or config.yml.bak-v1-2 if that exists.
    private static Path backup(Path file, int version) throws IOException {
        Path backup = file.resolveSibling(file.getFileName() + ".bak-v" + version);
        for (int n = 2; Files.exists(backup); n++) {
            backup = file.resolveSibling(file.getFileName() + ".bak-v" + version + "-" + n);
        }
        Files.copy(file, backup);
        return backup;
    }

    // Writes beside the file and swaps it in, so a crash never leaves half a config.
    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private static String firstLine(Throwable e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String message = String.valueOf(cause.getMessage()).strip();
        return message.lines().limit(4).map(String::strip).reduce((a, b) -> a + " " + b).orElse(message);
    }
}
