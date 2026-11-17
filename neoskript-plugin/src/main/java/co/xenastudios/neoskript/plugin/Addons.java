/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ThreadAffinity;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

/**
 * The addons registered through the API, and their registrations, run once when NeoSkript sets up its syntax.
 * Addons call {@link #add} from their {@code onEnable}, after NeoSkript's; the registrations run on the first tick,
 * with the standard library's, before scripts load.
 */
final class Addons {

    private record Addon(String name, String version, Consumer<? super Registrar> registration) {}

    /** The registrar methods whose first argument is the id of an element they register. */
    private static final Set<String> REGISTERS_ELEMENT = Set.of("effect", "condition", "section", "expression",
            "typedExpression", "property", "propertyCondition", "event");

    private final Logger logger;
    private final List<Addon> addons = new ArrayList<>();
    /** The registrar, once the registrations have run: later addons register into it straight away. */
    private Registrar registered;
    /** Whether an element is registered as an id, or a variant of it: NeoSkript's own, before addons register. */
    private Predicate<String> known = id -> false;
    /** Whether scripts have loaded, after which nothing more can be registered. */
    private java.util.function.BooleanSupplier frozen = () -> false;

    Addons(Logger logger) {
        this.logger = logger;
    }

    /**
     * Whether some plugin depends on NeoSkript: an addon, whose syntax scripts have to wait for. A soft dependency
     * doesn't count, so plugins that only use NeoSkript when it's there don't hold up its start.
     */
    static boolean anyInstalled(Plugin neoskript) {
        String name = neoskript.getPluginMeta().getName();
        for (Plugin plugin : neoskript.getServer().getPluginManager().getPlugins()) {
            if (plugin != neoskript && plugin.getPluginMeta().getPluginDependencies().contains(name)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code plugin} depends or soft-depends on NeoSkript, so it may have used its API. */
    static boolean isAddon(Plugin neoskript, Plugin plugin) {
        String name = neoskript.getPluginMeta().getName();
        return plugin != neoskript && (plugin.getPluginMeta().getPluginDependencies().contains(name)
                || plugin.getPluginMeta().getPluginSoftDependencies().contains(name));
    }

    void frozenWhen(java.util.function.BooleanSupplier frozen) {
        this.frozen = frozen;
    }

    /** Says which ids are taken, by NeoSkript or another addon, so an addon can't change their thread affinity. */
    void knownWhen(Predicate<String> known) {
        this.known = known;
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
            addon.registration().accept(forAddon(addon, registrar));
            logger.info("Registered the syntax of {} {}", addon.name(), addon.version());
        } catch (RuntimeException | LinkageError e) {
            logger.error("{} {} couldn't register all of its syntax", addon.name(), addon.version(), e);
        }
    }

    /**
     * The registrar as one addon sees it: everything goes to {@code registrar}, but {@link Registrar#affinity} only
     * takes the ids of elements this addon registered itself, and none that NeoSkript or another addon has.
     */
    private Registrar forAddon(Addon addon, Registrar registrar) {
        Set<String> own = new HashSet<>();
        Set<String> taken = new HashSet<>();
        return (Registrar) Proxy.newProxyInstance(Registrar.class.getClassLoader(), new Class<?>[] {Registrar.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "Registrar of " + addon.name();
                            case "hashCode" -> System.identityHashCode(proxy);
                            default -> proxy == args[0];
                        };
                    }
                    if (method.getName().equals("affinity") && args.length == 2 && args[1] instanceof ThreadAffinity) {
                        checkAffinity(addon, (String) args[0], own, taken);
                    } else if (REGISTERS_ELEMENT.contains(method.getName()) && args.length > 0
                            && args[0] instanceof String id && !own.contains(id)) {
                        if (known.test(id)) {
                            taken.add(id);
                        }
                        own.add(id);
                    }
                    return invoke(method, registrar, args);
                });
    }

    private static void checkAffinity(Addon addon, String id, Set<String> own, Set<String> taken) {
        if (id.indexOf('-') >= 0) {
            throw new IllegalArgumentException("An element's id can't contain '-': " + id);
        }
        if (!own.contains(id)) {
            throw new IllegalArgumentException(addon.name() + " hasn't registered an element called " + id
                    + ": register it first, and give only your own elements' ids");
        }
        if (taken.contains(id)) {
            throw new IllegalArgumentException(id + " is also an id NeoSkript or another addon uses, so its affinity"
                    + " can't be set: start your ids with your addon's name");
        }
    }

    private static Object invoke(Method method, Registrar registrar, Object[] args) throws Throwable {
        try {
            return method.invoke(registrar, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** Each addon as {@code name version}, in the order added. */
    synchronized List<String> names() {
        return addons.stream().map(addon -> (addon.name() + " " + addon.version()).strip()).toList();
    }
}
