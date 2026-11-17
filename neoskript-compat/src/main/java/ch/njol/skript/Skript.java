/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript;

import ch.njol.skript.util.EmptyStacktraceException;
import ch.njol.skript.util.Version;
import co.xenastudios.neoskript.addon.support.Originals;
import co.xenastudios.neoskript.api.NeoSkript;
import co.xenastudios.neoskript.compat.Compat;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NeoSkriptCompat: it stands in for Skript, so Skript addons load on NeoSkript. Addons find it as Skript (its
 * plugin.yml provides Skript) and register with it as with Skript; their syntax goes to NeoSkript.
 */
public final class Skript extends JavaPlugin implements Listener {

    /** The version of Skript whose API NeoSkriptCompat gives addons. */
    private static final Version VERSION = new Version(2, 17, 0);

    private static Skript instance;
    private static Version minecraftVersion;

    public Skript() throws IllegalStateException {
        if (instance != null) {
            throw new IllegalStateException("Skript is already running");
        }
        instance = this;
    }

    public static Skript getInstance() {
        Skript skript = instance;
        if (skript == null) {
            throw new IllegalStateException("NeoSkriptCompat isn't loaded");
        }
        return skript;
    }

    public static org.skriptlang.skript.Skript instance() {
        return Compat.get().skript();
    }

    @Override
    public void onEnable() {
        Plugin neoskript = getServer().getPluginManager().getPlugin("NeoSkript");
        if (!(neoskript instanceof co.xenastudios.neoskript.plugin.NeoSkriptPlugin plugin) || !neoskript.isEnabled()
                || plugin.library() == null) {
            getLogger().severe("NeoSkriptCompat needs NeoSkript, enabled: it's turning off");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Compat compat = Compat.start(plugin.library(), getLogger());
        NeoSkript.get().whenReady(compat::stopAccepting);
        warnIfSkriptInstalled();
    }

    /** Skript's own jar next to NeoSkriptCompat: Paper loads one of the two, which may not be the one wanted. */
    private void warnIfSkriptInstalled() {
        try {
            Path plugins = getDataFolder().toPath().toAbsolutePath().getParent();
            Map<String, Path> found = Originals.find(plugins, List.of("Skript"));
            Path skript = found.get("Skript");
            if (skript != null) {
                getLogger().warning("Skript's own jar is in the plugins folder too (" + skript.getFileName() + ")."
                        + " Paper loads only one plugin called Skript, and this time it's NeoSkriptCompat. Remove one"
                        + " of the two.");
            }
        } catch (RuntimeException ignored) {
            // Only a warning: nothing to warn about if the folder can't be read.
        }
    }

    @Override
    public void onDisable() {
        Compat.stop();
    }

    public static Version getVersion() {
        return VERSION;
    }

    public static Version getMinecraftVersion() {
        Version version = minecraftVersion;
        if (version == null) {
            Matcher matcher = Pattern.compile("\\d+(?:\\.\\d+){1,2}").matcher(Bukkit.getBukkitVersion());
            version = matcher.find() ? new Version(matcher.group()) : new Version(0, 0, 0);
            minecraftVersion = version;
        }
        return version;
    }

    public static boolean isRunningMinecraft(int major, int minor) {
        return isRunningMinecraft(new Version(major, minor, 0));
    }

    public static boolean isRunningMinecraft(int major, int minor, int revision) {
        return isRunningMinecraft(new Version(major, minor, revision));
    }

    public static boolean isRunningMinecraft(Version version) {
        return getMinecraftVersion().compareTo(version) >= 0;
    }

    public static ServerPlatform getServerPlatform() {
        return ServerPlatform.BUKKIT_PAPER;
    }

    public static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static boolean methodExists(Class<?> c, String methodName, Class<?>... parameterTypes) {
        try {
            c.getDeclaredMethod(methodName, parameterTypes);
            return true;
        } catch (NoSuchMethodException | SecurityException | LinkageError e) {
            return false;
        }
    }

    public static boolean methodExists(Class<?> c, String methodName, Class<?>[] parameterTypes, Class<?> returnType) {
        try {
            return c.getDeclaredMethod(methodName, parameterTypes).getReturnType() == returnType;
        } catch (NoSuchMethodException | SecurityException | LinkageError e) {
            return false;
        }
    }

    public static boolean fieldExists(Class<?> c, String fieldName) {
        try {
            c.getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException | SecurityException | LinkageError e) {
            return false;
        }
    }

    public static boolean isAcceptRegistrations() {
        return Compat.running() && Compat.get().accepting();
    }

    public static void checkAcceptRegistrations() {
        if (!isAcceptRegistrations()) {
            throw new SkriptAPIException("Registering is disabled after initialisation!");
        }
    }

    @Deprecated
    public static Collection<SkriptAddon> getAddons() {
        return List.of();
    }

    public SkriptUpdater getUpdater() {
        return null;
    }

    public static boolean logNormal() {
        return true;
    }

    public static boolean logHigh() {
        return false;
    }

    public static boolean logVeryHigh() {
        return false;
    }

    public static boolean debug() {
        return false;
    }

    public static boolean testing() {
        return false;
    }

    public static void debug(String info) {}

    public static void info(String info) {
        log(Level.INFO, info);
    }

    public static void warning(String warning) {
        if (Compat.running()) {
            Compat.get().warning(warning);
        } else {
            log(Level.WARNING, warning);
        }
    }

    public static void error(String error) {
        if (Compat.running()) {
            Compat.get().error(error);
        } else {
            log(Level.SEVERE, error);
        }
    }

    public static void error(String error, ch.njol.skript.log.ErrorQuality quality) {
        error(error);
    }

    public static EmptyStacktraceException exception(String... info) {
        return exception(null, info);
    }

    public static EmptyStacktraceException exception(Throwable cause, String... info) {
        if (Compat.running()) {
            Compat.get().exception(cause, info);
        } else {
            Bukkit.getLogger().log(Level.SEVERE, String.join(" ", info), cause);
        }
        return new EmptyStacktraceException();
    }

    private static void log(Level level, String message) {
        Skript skript = instance;
        (skript != null ? skript.getLogger() : Bukkit.getLogger()).log(level, message);
    }

    public static String getSkriptPrefix() {
        return "[Skript] ";
    }

    public static Thread newThread(Runnable r, String name) {
        Thread thread = new Thread(r, name);
        thread.setDaemon(true);
        return thread;
    }

    public static String toString(double n) {
        return ch.njol.util.StringUtils.toString(n, 2);
    }
}
