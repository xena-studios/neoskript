/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A loaded YAML file, as skript-yaml's {@code YAMLProcessor} keeps one: its values by dotted path, the paths that
 * exist, comments on root keys, a header, and whether it changed since it was loaded or saved.
 *
 * <p>Scripts on many threads (Folia's regions) use the same file, so every method is synchronized on it, and what it
 * gives out is a copy: a list read from it can't change under another thread.
 */
final class YamlFile {

    /** A comment above a root key, with {@code # } on each line, and whether a blank line goes above it. */
    record Comment(String text, boolean extraLine) {}

    private static final Pattern UUID_TEXT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private Map<String, Object> root = new LinkedHashMap<>();
    /** Every path, as {@code all yaml nodes} lists them: in the order they were added, each once. */
    private final Set<String> paths = new LinkedHashSet<>();
    private final Map<String, Comment> comments = new HashMap<>();
    private String header;
    private boolean extraHeaderLine;
    private boolean modified;
    private int indent = 4;
    private Path file;
    private boolean fileDeleted;

    YamlFile(Path file) {
        this.file = file;
    }

    // ---------------------------------------------------------------- the file

    synchronized Path file() {
        return file;
    }

    /** Points this at another file, as {@code change yaml "x" file path to ...}; it's no longer deleted. */
    synchronized void file(Path file) {
        this.file = file;
        this.fileDeleted = false;
    }

    synchronized boolean fileDeleted() {
        return fileDeleted;
    }

    synchronized void markFileDeleted() {
        fileDeleted = true;
    }

    synchronized boolean modified() {
        return modified;
    }

    synchronized void modified(boolean modified) {
        this.modified = modified;
    }

    synchronized int indent() {
        return indent;
    }

    /** The indentation saves use from now on, as skript-yaml keeps it: 1 to 10. */
    synchronized void indent(int indent) {
        if (indent < 1 || indent > 10) {
            throw new IllegalArgumentException("Indent must be at least 1 and at most 10");
        }
        this.indent = indent;
    }

    /** Replaces what's loaded with a file's contents, as read. */
    synchronized void replace(Map<String, Object> root, String header, Map<String, Comment> comments) {
        this.root = new LinkedHashMap<>(root);
        this.header = header;
        this.extraHeaderLine = false;
        this.comments.clear();
        this.comments.putAll(comments);
        paths.clear();
        for (Map.Entry<String, Object> entry : this.root.entrySet()) {
            // A root key with nothing in it exists too (skript-yaml leaves it out until it's set again).
            paths.add(entry.getKey());
            if (entry.getValue() instanceof Map<?, ?> map) {
                addPaths(entry.getKey(), map);
            }
        }
        modified = false;
    }

    private void addPaths(String path, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String child = path + "." + entry.getKey();
            paths.add(child);
            if (entry.getValue() instanceof Map<?, ?> inner) {
                addPaths(child, inner);
            }
        }
    }

    // ---------------------------------------------------------------- values

    /** The value at {@code path}, or {@code null}: a UUID written as text is a UUID, as skript-yaml reads it. */
    synchronized Object get(String path) {
        return copy(uuids(raw(path)));
    }

    /** The value at {@code path} as stored, without copying it: for checks only. */
    synchronized Object raw(String path) {
        if (!path.contains(".")) {
            return root.get(path);
        }
        String[] parts = path.split("\\.");
        Map<?, ?> node = root;
        for (int i = 0; i < parts.length; i++) {
            Object value = node.get(parts[i]);
            if (value == null) {
                return null;
            }
            if (i == parts.length - 1) {
                return value;
            }
            if (!(value instanceof Map<?, ?> map)) {
                return null;
            }
            node = map;
        }
        return null;
    }

    /** Sets {@code path}, making the nodes on the way, and replacing a value that's in the way with a node. */
    @SuppressWarnings("unchecked")
    synchronized void set(String path, Object value) {
        if (!path.contains(".")) {
            root.put(path, value);
            paths.add(path);
            modified = true;
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> node = root;
        for (int i = 0; i < parts.length; i++) {
            paths.add(String.join(".", Arrays.copyOf(parts, i + 1)));
            if (i == parts.length - 1) {
                node.put(parts[i], value);
                modified = true;
                return;
            }
            Object next = node.get(parts[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                node.put(parts[i], next);
            }
            node = (Map<String, Object>) next;
        }
    }

    /**
     * Removes {@code path} and what's under it.
     * @return a warning for the script's author, or {@code null}
     */
    @SuppressWarnings("unchecked")
    synchronized String remove(String path) {
        if (root.isEmpty()) {
            return null;
        }
        paths.remove(path);
        paths.removeIf(other -> other.startsWith(path + "."));
        if (!path.contains(".")) {
            root.remove(path);
            modified = true;
            return null;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> node = root;
        for (int i = 0; i < parts.length; i++) {
            if (i == parts.length - 1) {
                node.remove(parts[i]);
                modified = true;
                return null;
            }
            Object next = node.get(parts[i]);
            if (next == null) {
                return "The node '" + path + "' does not exist";
            }
            if (!(next instanceof Map)) {
                return "The node '" + parts[i] + "' likely contains a value therefore the node '" + path
                        + "' does not exist";
            }
            node = (Map<String, Object>) next;
        }
        return null;
    }

    /** The list at {@code path}, copied, or {@code null} if there's none. */
    synchronized List<Object> list(String path) {
        return raw(path) instanceof List<?> list ? new ArrayList<>(((List<?>) uuids(list))) : null;
    }

    /** The keys of the node at {@code path} (the root's for {@code ""}), or {@code null} if it isn't a node. */
    synchronized List<String> keys(String path) {
        if (path == null || path.isEmpty()) {
            return new ArrayList<>(root.keySet());
        }
        return raw(path) instanceof Map<?, ?> map ? map.keySet().stream().map(String::valueOf).toList() : null;
    }

    /** The full paths of the nodes in the node at {@code path} (the root's keys for {@code ""}), or {@code null}. */
    synchronized List<String> nodes(String path) {
        if (path.isEmpty()) {
            return new ArrayList<>(root.keySet());
        }
        if (!(raw(path) instanceof Map<?, ?> map)) {
            return null;
        }
        List<String> nodes = new ArrayList<>();
        for (Object key : map.keySet()) {
            nodes.add(path + "." + key);
        }
        return nodes;
    }

    synchronized List<String> paths() {
        return new ArrayList<>(paths);
    }

    synchronized boolean hasPath(String path) {
        return paths.contains(path);
    }

    synchronized boolean hasRootKey(String key) {
        return root.containsKey(key);
    }

    synchronized boolean isEmpty() {
        return paths.isEmpty();
    }

    // ---------------------------------------------------------------- comments and the header

    synchronized Comment comment(String key) {
        return comments.get(key);
    }

    /** Sets the comment above root key {@code key}, adding {@code # } to lines that don't start with it. */
    synchronized void comment(String key, boolean extraLine, String... lines) {
        if (lines == null || lines.length == 0) {
            comments.remove(key);
        } else {
            String[] commented = new String[lines.length];
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i] == null ? "" : lines[i];
                commented[i] = line.matches("^# ?") ? line : "# " + line;
            }
            comments.put(key, new Comment(YamlText.tabs(String.join(YamlText.LINE_BREAK, commented)), extraLine));
        }
        modified = true;
    }

    synchronized void removeComment(String key) {
        comments.remove(key);
        modified = true;
    }

    synchronized String header() {
        return header;
    }

    synchronized boolean extraHeaderLine() {
        return extraHeaderLine;
    }

    /** Sets the header's lines, each with {@code ## } in front unless it starts with {@code #}. */
    synchronized void header(boolean extraLine, String... lines) {
        header = YamlText.header(lines);
        extraHeaderLine = extraLine;
        modified = true;
    }

    synchronized void removeHeader() {
        header = null;
        extraHeaderLine = false;
        modified = true;
    }

    /** What a save writes, taken at once, so another thread's changes can't land in the middle of it. */
    synchronized Snapshot snapshot() {
        return new Snapshot(copyMap(root), header, extraHeaderLine, Map.copyOf(comments), indent, file);
    }

    /** {@link #snapshot()}, and no longer modified, in one step: a change made after it marks it modified again. */
    synchronized Snapshot saving() {
        modified = false;
        return snapshot();
    }

    /** A file's contents as of one moment. */
    record Snapshot(Map<String, Object> root, String header, boolean extraHeaderLine, Map<String, Comment> comments,
            int indent, Path file) {}

    // ---------------------------------------------------------------- copies

    /** A UUID written as text is a UUID, in lists and nodes too, as skript-yaml reads it. */
    private static Object uuids(Object value) {
        if (value instanceof String text && UUID_TEXT.matcher(text).matches()) {
            return UUID.fromString(text);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(uuids(item));
            }
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(key, uuids(item)));
            return copy;
        }
        return value;
    }

    /** Lists and nodes copied all the way down, so what's given out can't change the file, or change with it. */
    static Object copy(Object value) {
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(copy(item));
            }
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        return value;
    }

    /** A node copied all the way down. Keys stay as they are: a number key in a list's node is written as one. */
    @SuppressWarnings("unchecked")
    private static <K> Map<K, Object> copyMap(Map<K, ?> map) {
        Map<K, Object> copy = new LinkedHashMap<>();
        map.forEach((key, item) -> copy.put(key, copy(item)));
        return copy;
    }
}
