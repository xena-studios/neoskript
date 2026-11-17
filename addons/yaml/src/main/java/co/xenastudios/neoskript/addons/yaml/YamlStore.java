/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import co.xenastudios.neoskript.api.Values;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The loaded YAML files, by id, shared by every script on every thread, as skript-yaml's {@code YAML_STORE}. Files
 * stay loaded across script reloads until a script unloads them, as in skript-yaml.
 */
final class YamlStore implements AutoCloseable {

    // A ConcurrentHashMap, as skript-yaml's, so 'the loaded yaml' lists ids in the same order it does.
    private final Map<String, YamlFile> files = new ConcurrentHashMap<>();
    private final ServerPaths paths;
    private final Values values;
    private final Saver saver;
    private final Consumer<String> warn;

    YamlStore(ServerPaths paths, Values values, Consumer<String> warn) {
        this.paths = paths;
        this.values = values;
        this.warn = warn;
        this.saver = new Saver((file, error) -> warn.accept("Couldn't save " + file + ": " + error.getMessage()));
    }

    ServerPaths paths() {
        return paths;
    }

    void warn(String message) {
        warn.accept(message);
    }

    // ---------------------------------------------------------------- ids

    YamlFile get(String id) {
        return id == null ? null : files.get(id);
    }

    /** The file loaded as {@code id}, or {@code null} with skript-yaml's warning. */
    YamlFile require(String id, String where) {
        YamlFile file = get(id);
        if (file == null) {
            warn.accept("No yaml by the name '" + id + "' has been loaded" + where);
        }
        return file;
    }

    boolean isLoaded(String id) {
        return id != null && files.containsKey(id);
    }

    List<String> ids() {
        return new ArrayList<>(files.keySet());
    }

    Map<String, YamlFile> all() {
        return Map.copyOf(files);
    }

    void unload(String id) {
        if (id != null) {
            files.remove(id);
        }
    }

    /** Unloads the files in {@code folder}. */
    void unloadFolder(Path folder) {
        files.values().removeIf(file -> folder.equals(file.file().getParent()));
    }

    /** Gives {@code from}'s file the id {@code to}, unless {@code from} isn't loaded or {@code to} is taken. */
    boolean rename(String from, String to, String where) {
        YamlFile file = files.get(from);
        if (file == null) {
            warn.accept("No yaml by the name '" + from + "' has been loaded" + where);
            return false;
        }
        if (files.putIfAbsent(to, file) != null) {
            warn.accept("A yaml with the name '" + to + "' is already loaded" + where);
            return false;
        }
        files.remove(from, file);
        return true;
    }

    // ---------------------------------------------------------------- files

    /**
     * Loads {@code file} as {@code id}, making it (and its folders) if it doesn't exist, as skript-yaml does. A file
     * that can't be read isn't loaded, and an id already loaded keeps what it had.
     * @return whether it loaded
     */
    boolean load(String id, Path file, String where) {
        if (id == null) {
            return false;
        }
        saver.await(file);
        try {
            if (!Files.exists(file)) {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                Files.createFile(file);
            }
            if (Files.size(file) > YamlText.MAX_CHARACTERS) {
                // Refused before it's read: no file over the limit is held in memory.
                refused(id, file, YamlText.tooBig().getMessage(), where);
                return false;
            }
        } catch (IOException | RuntimeException e) {
            warn.accept("[Load Yaml] " + e + " (" + file + ")" + where);
            return false;
        }
        YamlText.Read read;
        try (java.io.InputStream bytes = Files.newInputStream(file)) {
            read = YamlText.read(bytes, values);
        } catch (YamlText.Unreadable e) {
            refused(id, file, e.getMessage(), where);
            return false;
        } catch (IOException | RuntimeException | StackOverflowError e) {
            refused(id, file, e.toString(), where);
            return false;
        }
        YamlFile loaded = new YamlFile(file);
        loaded.replace(read.root(), read.header(), read.comments());
        files.put(id, loaded);
        return true;
    }

    /**
     * A file that couldn't be read, left as it is. An id already loaded keeps what it had, but its saves to that file
     * are refused until it loads, so the next save doesn't write the old contents over the file someone is fixing.
     */
    private void refused(String id, Path file, String why, String where) {
        boolean kept = false;
        for (Map.Entry<String, YamlFile> entry : files.entrySet()) {
            if (file.equals(entry.getValue().file())) {
                entry.getValue().refused(why);
                kept |= entry.getKey().equals(id);
            }
        }
        warn.accept("Couldn't load '" + id + "' from " + file + ": " + why + ". The file is left as it is"
                + (kept ? ", and '" + id + "' keeps what it had loaded, but isn't saved to that file until it loads"
                : ", and isn't loaded") + where);
    }

    /** The YAML files ({@code .yml}, {@code .yaml}) in a folder, by name; {@code null} with a warning if it isn't one. */
    List<Path> filesIn(Path folder, String name, String action, String where) {
        if (!Files.exists(folder)) {
            warn.accept("[" + action + " Yaml] " + name + " does not exist!" + where);
            return null;
        }
        if (!Files.isDirectory(folder)) {
            warn.accept("[" + action + " Yaml] " + name + " is not a directory!" + where);
            return null;
        }
        List<Path> found = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
            for (Path entry : entries) {
                String file = entry.getFileName().toString();
                if ((file.endsWith(".yml") || file.endsWith(".yaml")) && Files.isRegularFile(entry)) {
                    found.add(entry);
                }
            }
        } catch (IOException | RuntimeException e) {
            warn.accept("[" + action + " Yaml] " + name + " can't be read: " + e.getMessage() + where);
            return null;
        }
        // In order of name, so a folder loads the same way everywhere.
        found.sort(null);
        return found;
    }

    /** The YAML files in a folder that no loaded file is, by file name, any case of {@code .yml}. */
    List<String> unloadedIn(Path folder) {
        List<String> loaded = new ArrayList<>();
        for (YamlFile file : files.values()) {
            if (folder.equals(file.file().getParent())) {
                loaded.add(file.file().getFileName().toString());
            }
        }
        List<String> unloaded = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return unloaded;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if ((lower.endsWith(".yml") || lower.endsWith(".yaml")) && !loaded.contains(name)
                        && !unloaded.contains(name)) {
                    unloaded.add(name);
                }
            }
        } catch (IOException | RuntimeException e) {
            warn.accept("Couldn't read " + folder + ": " + e.getMessage());
        }
        unloaded.sort(null);
        return unloaded;
    }

    /**
     * Saves a file: what it holds now is written off this thread, whole, in place of the file.
     * @param target where to write it, or {@code null} for its own file
     */
    boolean save(YamlFile file, Path target, boolean extraLines, String id, String where) {
        Path to = target != null ? target : file.file();
        String refused = file.refused();
        if (refused != null && to.equals(file.file())) {
            warn.accept("Not saving '" + id + "' to " + to + ": the file couldn't be read when it was last loaded ("
                    + refused + "), and saving would overwrite it with what was loaded before. Fix the file and load"
                    + " it again, or save it to another file" + where);
            return false;
        }
        // The contents are copied and numbered at once, on the script's thread: the dump below can't be moved off it,
        // as values like items and locations are the scripts' own, which only their thread may read. The number
        // makes sure the newest copy is the one written, whichever thread finishes its dump first.
        YamlFile.Snapshot snapshot = file.saving();
        YamlText.Written written = YamlText.write(snapshot, extraLines, values);
        // The limits loading has, so a file saved can always be loaded again: a save past them is refused, and the
        // file on disk kept.
        long bytes = utf8Length(written.text());
        if (bytes > YamlText.MAX_CHARACTERS || written.values() > YamlText.MAX_VALUES) {
            file.modified(true);
            warn.accept("Not saving '" + id + "' to " + to + ": it would be " + (bytes > YamlText.MAX_CHARACTERS
                    ? (bytes / 1024 / 1024) + " MB, more than the " + (YamlText.MAX_CHARACTERS / 1024 / 1024) + " MB"
                    : written.values() + " values, more than the " + YamlText.MAX_VALUES) + " a file can have to be"
                    + " loaded again. The file is left as it was" + where);
            return false;
        }
        String content = afterDump.apply(written.text());
        saver.save(to, content, snapshot.sequence(), () -> file.modified(true));
        return true;
    }

    /** How many bytes text is in UTF-8, without encoding it. */
    static long utf8Length(String text) {
        long bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) {
                bytes++;
            } else if (c < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    /** For tests: runs between turning a snapshot into text and handing it to the saver. */
    java.util.function.UnaryOperator<String> afterDump = java.util.function.UnaryOperator.identity();

    /** Deletes a file from disk, once its saves are written. */
    boolean delete(Path file) {
        saver.await(file);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException | RuntimeException e) {
            warn.accept("Couldn't delete " + file + ": " + e.getMessage());
            return false;
        }
    }

    /** Waits for {@code file}'s saves, before reading it. */
    void await(Path file) {
        saver.await(file);
    }

    /** Writes every save still pending. Saves after this are written straight away. */
    @Override
    public void close() {
        saver.close();
    }
}
