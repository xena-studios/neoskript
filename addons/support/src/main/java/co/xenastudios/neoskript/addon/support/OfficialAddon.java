/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addon.support;

import co.xenastudios.neoskript.api.NeoSkript;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * What an official addon checks when it starts, before it registers its syntax. Call {@link #start} first in
 * {@code onEnable}:
 *
 * <pre>{@code
 * if (!OfficialAddon.start(this)) {
 *     getServer().getPluginManager().disablePlugin(this);
 *     return;
 * }
 * }</pre>
 *
 * <p><b>The community addon it replaces</b> (its build file's {@code replaces}: {@code skript-db}, or every name
 * it's gone by, as {@code skript-reflect} and {@code skript-mirror}), which the build also makes a soft
 * dependency, so it loads first if it can load at all:
 * <ul>
 *   <li>if it's running, which it can only be through Skript's addon compatibility layer, this addon doesn't
 *       load, so the two don't register the same syntax, and the console says to remove one;</li>
 *   <li>if only its jar is in {@code plugins/}, it can't run (it needs Skript, and NeoSkript doesn't run next to
 *       Skript), so this addon loads, and the console says to remove that jar. That includes an addon Paper loaded
 *       that then disabled itself, as one that only soft-depends on Skript does.</li>
 * </ul>
 *
 * <p><b>NeoSkript's version:</b> an addon is released with NeoSkript, at the same version. On an older NeoSkript
 * it warns, as syntax it uses may be missing; on a newer one it loads quietly, as the API is stable.
 */
public final class OfficialAddon {

    private static final String PROPERTIES = "neoskript-addon.properties";

    private OfficialAddon() {}

    /**
     * Checks for the community addon this one replaces and NeoSkript's version, and says what it found in the
     * console. Never throws: if the checks themselves fail, the addon loads.
     *
     * @param plugin the addon
     * @return whether the addon should load: {@code false} if the community addon it replaces is running
     */
    public static boolean start(JavaPlugin plugin) {
        try {
            Properties properties = properties(plugin.getClass().getClassLoader());
            List<String> replaces = list(properties.getProperty("replaces", ""));
            // Running: loaded and enabled. It's a soft dependency, so it has enabled already if it can: one that's
            // loaded but disabled (skript-placeholders only soft-depends on Skript, so Paper loads it, and it
            // disables itself without Skript) has failed, and counts as only its jar.
            Map<String, String> running = new LinkedHashMap<>();
            for (String name : replaces) {
                Plugin original = plugin.getServer().getPluginManager().getPlugin(name);
                if (original != null && original != plugin && original.isEnabled()) {
                    running.put(name, original.getPluginMeta().getVersion());
                }
            }
            // Only if none is loaded, the jars in plugins/ are read, for one that couldn't load.
            Map<String, Path> jars = replaces.isEmpty() || !running.isEmpty() ? Map.of()
                    : Originals.find(plugin.getDataFolder().toPath().toAbsolutePath().getParent(), replaces);
            String neoskript;
            try {
                neoskript = NeoSkript.get().version();
            } catch (RuntimeException | LinkageError none) {
                neoskript = null;
            }
            Check check = check(plugin.getName(), running, jars, properties.getProperty("neoskript"), neoskript);
            for (String warning : check.warnings()) {
                plugin.getLogger().warning(warning);
            }
            return check.load();
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().warning("Couldn't check for the addon " + plugin.getName() + " replaces: " + e);
            return true;
        }
    }

    /**
     * What an addon does when it starts.
     *
     * @param load whether it loads
     * @param warnings what it says in the console
     */
    public record Check(boolean load, List<String> warnings) {

        public Check {
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Decides what addon {@code addon} does.
     *
     * @param addon the addon's name
     * @param running the community addons it replaces that are loaded, with their versions
     * @param jars the community addons it replaces whose jars are in {@code plugins/}, with the jars
     * @param builtFor the NeoSkript version it was built with, or {@code null}
     * @param neoskript the running NeoSkript's version, or {@code null} if unknown
     * @return what it does
     */
    public static Check check(String addon, Map<String, String> running, Map<String, Path> jars, String builtFor,
            String neoskript) {
        List<String> warnings = new ArrayList<>();
        if (!running.isEmpty()) {
            running.forEach((name, version) -> warnings.add(name + " " + version + " is installed and loaded, and " + addon
                    + " replaces it, so " + addon + " won't load next to it: the two would register the same syntax."
                    + " Remove " + name + " to use " + addon + ", or remove " + addon + "."));
            return new Check(false, warnings);
        }
        jars.forEach((name, jar) -> warnings.add("plugins/" + jar.getFileName() + " is " + name + ", which needs"
                + " Skript, so it doesn't load with NeoSkript. " + addon + " replaces it: scripts written for " + name
                + " should work unchanged (" + addon + "'s docs list any differences). Remove " + jar.getFileName()
                + "."));
        if (builtFor != null && neoskript != null && older(neoskript, builtFor)) {
            warnings.add(addon + " was built for NeoSkript " + builtFor + ", and this is NeoSkript " + neoskript
                    + ": some of its syntax may not work. Update NeoSkript to " + builtFor + " or newer.");
        }
        return new Check(true, warnings);
    }

    /**
     * Whether version {@code a} is older than {@code b}, by their numbers ({@code 1.2.10} after {@code 1.2.9}). A
     * pre-release ({@code 1.0.0-beta.1}) is older than its release, and versions that don't start with numbers
     * aren't compared.
     *
     * @param a a version
     * @param b another
     * @return whether {@code a} is older
     */
    public static boolean older(String a, String b) {
        long[] x = numbers(a);
        long[] y = numbers(b);
        if (x.length == 0 || y.length == 0) {
            return false;
        }
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length ? x[i] : 0;
            long q = i < y.length ? y[i] : 0;
            if (p != q) {
                return p < q;
            }
        }
        // The same numbers: a pre-release (or a snapshot) comes before the release.
        return a.contains("-") && !b.contains("-");
    }

    private static long[] numbers(String version) {
        String release = version.strip().split("[-+]", 2)[0];
        if (release.isEmpty()) {
            return new long[0];
        }
        try {
            return Arrays.stream(release.split("\\.")).mapToLong(Long::parseLong).toArray();
        } catch (NumberFormatException notNumbers) {
            return new long[0];
        }
    }

    private static Properties properties(ClassLoader loader) {
        Properties properties = new Properties();
        try (InputStream in = loader.getResourceAsStream(PROPERTIES)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException unreadable) {
            // Nothing to check against.
        }
        return properties;
    }

    private static List<String> list(String commaSeparated) {
        return Arrays.stream(commaSeparated.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
