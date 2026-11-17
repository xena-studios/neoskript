/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Where scripts' YAML paths point, as skript-yaml reads them: relative to the server's folder (the one the server
 * was started in), or with {@code non-relative}, from the file system's root.
 *
 * <p>What's allowed: a relative path must stay inside the server's folder once {@code ..} is resolved, so text from
 * a player in a path can't reach other files on the machine; skript-yaml allows any. A {@code non-relative} path may
 * point anywhere, as the script asks for that by name, and scripts are as trusted as plugins.
 */
final class ServerPaths {

    /** A path that isn't allowed, or isn't one. */
    static final class Refused extends Exception {
        Refused(String message) {
            super(message, null, false, false);
        }
    }

    private final Path server;

    ServerPaths(Path server) {
        this.server = server.toAbsolutePath().normalize();
    }

    /** The server's folder. */
    Path server() {
        return server;
    }

    /**
     * The file (or folder) {@code path} names.
     * @param path the path as the script gives it
     * @param nonRelative whether it's from the file system's root
     * @throws Refused if it points outside the server's folder when relative, or isn't a path
     */
    Path resolve(String path, boolean nonRelative) throws Refused {
        if (path == null) {
            throw new Refused("A path can't be nothing");
        }
        String text = path.replace("/", File.separator);
        try {
            if (nonRelative) {
                return Path.of(withRoot(text)).toAbsolutePath().normalize();
            }
            // skript-yaml puts the server's folder in front, so a leading / is still inside it.
            String relative = text;
            while (relative.startsWith(File.separator)) {
                relative = relative.substring(File.separator.length());
            }
            Path resolved = server.resolve(relative).normalize();
            if (!resolved.startsWith(server)) {
                throw new Refused("The path '" + path + "' is outside the server's folder. Relative paths stay inside"
                        + " it; use 'non-relative' for a file elsewhere");
            }
            return resolved;
        } catch (InvalidPathException e) {
            throw new Refused("'" + path + "' isn't a path: " + e.getReason());
        }
    }

    /** A path from the root: as given if it starts with one of the file system's roots, else from the server's root. */
    private String withRoot(String path) {
        for (File root : File.listRoots()) {
            if (path.toLowerCase(Locale.ROOT).startsWith(root.getPath().toLowerCase(Locale.ROOT))) {
                return path;
            }
        }
        Path root = server.getRoot();
        return root != null ? root + path : File.separator + path;
    }

    /** The id {@code load yaml "plugins/x/config.yml"} gives: the file's name without its extension. */
    static String id(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** A folder's name as written with a separator after it, as skript-yaml's ids for files loaded from a folder. */
    static String withSeparator(String folder) {
        if (folder.contains("/")) {
            return folder.endsWith("/") ? folder : folder + "/";
        }
        if (folder.contains("\\")) {
            return folder.endsWith("\\") ? folder : folder + "\\";
        }
        return folder + File.separator;
    }
}
