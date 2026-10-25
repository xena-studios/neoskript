/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.plugin;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The scripts folder as {@code /skript} sees it, ported from Skript's ScriptCommandUtils and the file
 * lookups of its ScriptLoader: finding a script or folder from what was typed, renaming scripts with
 * the {@code -} prefix that disables them, and the names offered as tab completions.
 */
final class ScriptFiles {

    /** The prefix of a disabled script's file name. */
    static final String DISABLED_PREFIX = "-";

    private ScriptFiles() {}

    /** Skript's filter for loaded scripts and folders. */
    static final FileFilter LOADED = f -> f != null
            && (f.isDirectory() && !f.getName().startsWith(".") || !f.isDirectory() && endsWithSk(f.getName()))
            && !f.getName().startsWith(DISABLED_PREFIX) && !f.isHidden();

    /** Skript's filter for disabled scripts and folders. */
    static final FileFilter DISABLED = f -> f != null
            && (f.isDirectory() && !f.getName().startsWith(".") || !f.isDirectory() && endsWithSk(f.getName()))
            && f.getName().startsWith(DISABLED_PREFIX) && !f.isHidden();

    private static boolean endsWithSk(String name) {
        return name.toLowerCase(Locale.ENGLISH).endsWith(".sk");
    }

    /**
     * The script or folder {@code name} means, or {@code null} if there's none in {@code directory}, as
     * Skript's ScriptLoader.getScriptFileFromName: {@code .sk} may be left off, a disabled script is found
     * by its name without the {@code -}, and a name ending in {@code /} (or {@code \}) is a folder. The path
     * returned is {@code directory} resolved with the file's path within it.
     *
     * <p>Skript 2.17 adds {@code .sk} to folder names too, so no folder can be found; this keeps finding
     * them as Skript did before, which its command clearly means to.
     */
    static Path find(Path directory, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        // Always allow '/' and '\' regardless of OS
        boolean folder = name.endsWith("/") || name.endsWith("\\");
        name = name.replace('/', File.separatorChar).replace('\\', File.separatorChar);
        if (name.startsWith(DISABLED_PREFIX)) {
            name = name.substring(DISABLED_PREFIX.length());
        }
        if (!folder && !endsWithSk(name)) {
            // A different extension means this cannot be a script
            int dot = name.lastIndexOf('.');
            if (dot > 0 && !name.substring(dot + 1).isEmpty()) {
                return null;
            }
            name = name + ".sk";
        }
        File dir = directory.toFile();
        File file = new File(dir, name);
        if (!file.exists()) {
            file = new File(file.getParentFile(), DISABLED_PREFIX + file.getName());
            if (!file.exists()) {
                return null;
            }
        }
        try {
            Path absoluteDir = directory.toAbsolutePath().normalize();
            if (containsSymlink(file.toPath().toAbsolutePath().normalize(), absoluteDir)) {
                return directory.resolve(absoluteDir.relativize(file.toPath().toAbsolutePath().normalize()));
            }
            // Check the user is asking for a script in the scripts folder, not something outside it.
            File canonicalDir = dir.getCanonicalFile();
            File canonical = file.getCanonicalFile();
            if (!canonical.getPath().startsWith(canonicalDir.getPath() + File.separator)) {
                return null;
            }
            return directory.resolve(canonicalDir.toPath().relativize(canonical.toPath()));
        } catch (IOException e) {
            return null;
        }
    }

    /** Whether a part of {@code path} below {@code root} is a symbolic link, as Skript's FileUtils.containsSymlink. */
    private static boolean containsSymlink(Path path, Path root) {
        if (!path.startsWith(root)) {
            return false;
        }
        for (Path p = path; p != null && !p.equals(root); p = p.getParent()) {
            if (Files.isSymbolicLink(p)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code file} is a disabled script or folder. */
    static boolean disabled(Path file) {
        return DISABLED.accept(file.toFile());
    }

    /** Enables or disables a script by renaming it, returning its new path. */
    static Path toggle(Path file, boolean enable) throws IOException {
        String name = file.getFileName().toString();
        return move(file, file.resolveSibling(enable ? name.substring(DISABLED_PREFIX.length()) : DISABLED_PREFIX + name));
    }

    /**
     * Enables or disables every script in {@code folder} and its folders (the folders keep their names),
     * returning the scripts' new paths.
     */
    static Set<Path> toggleAll(Path folder, boolean enable) throws IOException {
        FileFilter filter = enable ? DISABLED : LOADED;
        Set<Path> changed = new LinkedHashSet<>();
        File[] files = folder.toFile().listFiles();
        if (files == null) {
            return changed;
        }
        java.util.Arrays.sort(files);
        for (File file : files) {
            Path path = folder.resolve(file.getName());
            if (file.isDirectory()) {
                changed.addAll(toggleAll(path, enable));
            } else if (filter.accept(file)) {
                changed.add(toggle(path, enable));
            }
        }
        return changed;
    }

    /** Skript's FileUtils.move without replacing: fails if the target exists. */
    private static Path move(Path from, Path to) throws IOException {
        if (Files.exists(to)) {
            throw new IOException("Can't rename " + from.getFileName() + " to " + to.getFileName() + ": Target file already exists");
        }
        return Files.move(from, to);
    }

    /**
     * The disabled scripts, by their paths in {@code scripts}: disabled script files, and the scripts in
     * disabled folders, as {@code /skript list} shows them.
     */
    static List<String> disabledScripts(Path scripts) {
        Set<String> names = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(scripts)) {
            for (Path path : paths.sorted().toList()) {
                File file = path.toFile();
                if (!DISABLED.accept(file)) {
                    continue;
                }
                if (file.isDirectory()) {
                    for (Path sub : subFiles(path)) {
                        names.add(scripts.relativize(sub).toString());
                    }
                } else {
                    names.add(scripts.relativize(path).toString());
                }
            }
        } catch (IOException | RuntimeException e) {
            // As Skript, an unreadable folder just lists nothing more.
        }
        return List.copyOf(names);
    }

    /** The non-hidden scripts in a folder and its folders. */
    private static List<Path> subFiles(Path folder) {
        List<Path> files = new ArrayList<>();
        File[] list = folder.toFile().listFiles(f -> !f.isHidden());
        if (list == null) {
            return files;
        }
        java.util.Arrays.sort(list);
        for (File file : list) {
            Path path = folder.resolve(file.getName());
            if (file.isDirectory()) {
                files.addAll(subFiles(path));
            } else if (file.getName().endsWith(".sk")) {
                files.add(path);
            }
        }
        return files;
    }

    /**
     * Skript's tab completions for {@code /skript reload|enable|disable}: the scripts and folders in
     * {@code scripts} (disabled ones for {@code enable}) whose name or path starts with what was typed,
     * scripts in the scripts folder itself by name and others by their path from it, starting with a
     * separator, as do folders, which end with one.
     */
    static List<String> completions(Path scripts, String[] args) {
        List<String> options = new ArrayList<>();
        String scriptsPathString = scripts.toString();
        int scriptsPathLength = scriptsPathString.length();
        // support for scripts with spaces in them
        String scriptArg = String.join(" ", List.of(args).subList(1, args.length));
        String fs = File.separator;
        boolean enable = args[0].equalsIgnoreCase("enable");
        FileFilter fileFilter = enable ? DISABLED : LOADED;
        try (Stream<Path> files = Files.walk(scripts, FileVisitOption.FOLLOW_LINKS)) {
            for (Path path : files.sorted().toList()) {
                File file = path.toFile();
                if (!fileFilter.accept(file) || file.isHidden()) {
                    continue;
                }
                String fileString = file.toString().substring(scriptsPathLength);
                if (fileString.isEmpty()) {
                    continue;
                }
                if (file.isDirectory()) {
                    fileString = fileString + fs; // Add file separator at the end of directories
                } else if (file.getParentFile().toPath().toString().equals(scriptsPathString)) {
                    // Remove file separator from the beginning of files or directories in root only
                    fileString = fileString.substring(1);
                    if (fileString.isEmpty()) {
                        continue;
                    }
                }
                // Make sure the user's argument matches with the file's name or beginning of file path
                if (!scriptArg.isEmpty() && !file.getName().startsWith(scriptArg) && !fileString.startsWith(scriptArg)) {
                    continue;
                }
                // Trim off previous arguments if needed
                if (args.length > 2 && fileString.length() >= scriptArg.length()) {
                    fileString = fileString.substring(scriptArg.lastIndexOf(" ") + 1);
                }
                if (fileString.isEmpty()) {
                    continue;
                }
                options.add(fileString);
                if (fileString.startsWith(DISABLED_PREFIX)) {
                    options.add(fileString.substring(DISABLED_PREFIX.length()));
                }
            }
        } catch (IOException | RuntimeException e) {
            // As Skript, the completions are whatever was found.
        }
        // These will be added even if there are incomplete script arg
        if (args.length == 2) {
            options.add("all");
            if (args[0].equalsIgnoreCase("reload")) {
                options.add("config");
                options.add("aliases");
                options.add("scripts");
            }
        }
        return options;
    }
}
