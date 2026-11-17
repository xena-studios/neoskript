/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/**
 * The addons registered through the API, and their registrations, run once when NeoSkript sets up its syntax.
 * Addons call {@link #add} from their {@code onEnable}, after NeoSkript's; the registrations run on the first tick,
 * with the standard library's, before scripts load.
 */
final class Addons {

    private record Addon(String name, String version, Consumer<? super Registrar> registration) {}

    private final Logger logger;
    private final List<Addon> addons = new ArrayList<>();
    /** The registrar, once the registrations have run: later addons register into it straight away. */
    private Registrar registered;
    /** Whether scripts have loaded, after which nothing more can be registered. */
    private java.util.function.BooleanSupplier frozen = () -> false;

    Addons(Logger logger) {
        this.logger = logger;
    }

    /** Whether some plugin depends or soft-depends on NeoSkript: an addon, whose syntax scripts have to wait for. */
    static boolean anyInstalled(Plugin neoskript) {
        for (Plugin plugin : neoskript.getServer().getPluginManager().getPlugins()) {
            if (isAddon(neoskript, plugin)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code plugin} depends or soft-depends on NeoSkript. */
    static boolean isAddon(Plugin neoskript, Plugin plugin) {
        String name = neoskript.getPluginMeta().getName();
        return plugin != neoskript && (plugin.getPluginMeta().getPluginDependencies().contains(name)
                || plugin.getPluginMeta().getPluginSoftDependencies().contains(name));
    }

    void frozenWhen(java.util.function.BooleanSupplier frozen) {
        this.frozen = frozen;
    }

    synchronized void add(String name, String version, Consumer<? super Registrar> registration) {
        java.util.Objects.requireNonNull(name, "name");
        java.util.Objects.requireNonNull(registration, "registration");
        if (frozen.getAsBoolean()) {
            throw new IllegalStateException("Scripts have loaded, so " + name + " can't register syntax now."
                    + " Register in onEnable, with 'depend: [NeoSkript]' in plugin.yml.");
        }
        for (Addon addon : addons) {
            if (addon.name().equalsIgnoreCase(name)) {
                throw new IllegalStateException("An addon called " + name + " is registered already");
            }
        }
        Addon addon = new Addon(name, version == null ? "" : version, registration);
        addons.add(addon);
        if (registered != null) {
            run(addon, registered);
        }
    }

    /** Runs every addon's registration, in the order they were added; later ones register straight away. */
    synchronized void registerAll(Registrar registrar) {
        registered = registrar;
        for (Addon addon : addons) {
            run(addon, registrar);
        }
    }

    private void run(Addon addon, Registrar registrar) {
        try {
            addon.registration().accept(registrar);
            logger.info("Registered the syntax of {} {}", addon.name(), addon.version());
        } catch (RuntimeException | LinkageError e) {
            logger.error("{} {} couldn't register all of its syntax", addon.name(), addon.version(), e);
        }
    }

    /** Each addon as {@code name version}, in the order added. */
    synchronized List<String> names() {
        return addons.stream().map(addon -> (addon.name() + " " + addon.version()).strip()).toList();
    }
}
