/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api;

import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.List;
import java.util.function.Consumer;

/**
 * NeoSkript, as an addon sees it: where addons register their syntax, and how they reach scripts, variables and
 * the server's threads.
 *
 * <p>An addon is a plugin with {@code depend: [NeoSkript]} in its {@code plugin.yml} (a soft dependency isn't enough:
 * NeoSkript only waits for the plugins that depend on it). In {@code onEnable} it calls {@link #addon} once, and
 * NeoSkript runs the registration when it sets up its syntax, before it loads any script. Scripts and variables are
 * there once NeoSkript is ready, which {@link #whenReady} waits for:
 * <pre>{@code
 * NeoSkript.get().addon("MyAddon", getPluginMeta().getVersion(), registrar -> {
 *     registrar.effect("MyAddonEffWave", info -> (context, arguments) -> wave(arguments[0]), "wave at %players%");
 * });
 * NeoSkript.get().whenReady(() -> getLogger().info("Scripts loaded: " + NeoSkript.get().scripts().loaded()));
 * }</pre>
 *
 * <p><b>Stability.</b> Everything public in {@code co.xenastudios.neoskript.api} is stable unless it's marked
 * {@link co.xenastudios.neoskript.api.annotation.Experimental @Experimental} or
 * {@link co.xenastudios.neoskript.api.annotation.Internal @Internal}. From 1.0, a stable part only changes in a way
 * that breaks addons in a major release, and an addon built against 1.x runs on every later 1.x. New methods may be
 * added to interfaces addons call, like this one; interfaces addons implement, like
 * {@link co.xenastudios.neoskript.api.element.Effect}, only get new methods with a default.
 *
 * <p>The API is NeoSkript's own, not Skript's {@code ch.njol.skript} classes: Skript addons don't load on NeoSkript
 * yet. It has no Bukkit types in it, so values are passed as {@code Object}, as scripts see them.
 *
 * <p>Addons call this; they don't implement it.
 */
public interface NeoSkript {

    /**
     * NeoSkript, while it's enabled.
     *
     * @return the running NeoSkript
     * @throws IllegalStateException if NeoSkript isn't enabled, as when an addon doesn't have
     *     {@code depend: [NeoSkript]}, so it enables first
     */
    static NeoSkript get() {
        NeoSkript current = Installed.instance;
        if (current == null) {
            if (Installed.shaded()) {
                throw new IllegalStateException("This addon has its own copy of NeoSkript's API in its jar, which"
                        + " NeoSkript can't reach. Don't shade neoskript-api: depend on it with compileOnly.");
            }
            throw new IllegalStateException("NeoSkript isn't enabled. Add 'depend: [NeoSkript]' to the addon's"
                    + " plugin.yml, so it enables after NeoSkript.");
        }
        return current;
    }

    /**
     * Whether NeoSkript is enabled, so {@link #get()} returns it. For an addon that only soft-depends on NeoSkript.
     *
     * @return whether NeoSkript is enabled
     */
    static boolean isEnabled() {
        return Installed.instance != null;
    }

    /**
     * NeoSkript's version, as in its {@code plugin.yml}: {@code 1.0.0}, {@code 0.1.0-beta.1}.
     *
     * @return the version
     */
    String version();

    /**
     * Registers an addon's syntax. {@code registration} runs once, on the server's first tick, after NeoSkript's own
     * types and syntax are registered and before any script loads, so patterns can use NeoSkript's types
     * ({@code %player%}) and saved variables of the addon's types load. It runs on the global thread. If it throws,
     * the error is logged and the server carries on with what it registered before that.
     *
     * <p>Call it from the addon's {@code onEnable}. Syntax can't be added once scripts have loaded: {@code /sk reload}
     * reuses what's registered. So enabling an addon while the server runs (PlugMan and the like) isn't supported, and
     * this throws "Scripts have loaded": restart instead. An addon disabled while the server runs keeps its syntax
     * registered until a restart; the variables its codecs save are kept as their saved bytes.
     *
     * @param name the addon's name, as {@code /sk info} lists it
     * @param version the addon's version, as {@code /sk info} lists it
     * @param registration what registers the addon's types and syntax
     * @throws IllegalStateException if scripts have already loaded, or an addon of that name is registered
     */
    void addon(String name, String version, Consumer<? super Registrar> registration);

    /**
     * The addons registered, by name, in the order they were.
     *
     * @return each addon's name and version, as {@code "name version"}
     */
    List<String> addons();

    /**
     * Whether NeoSkript is ready: its syntax and every addon's is registered, saved variables have loaded, and scripts
     * have loaded. Until then, {@link #scripts()} and {@link #variables()} throw {@link IllegalStateException}. With
     * addons installed, that's on the server's first tick, after every plugin has enabled.
     *
     * @return whether it's ready
     */
    boolean isReady();

    /**
     * Runs {@code task} once NeoSkript is ready ({@link #isReady()}): now, on this thread, if it is; else on the
     * global thread, right after scripts load. Their {@code on load} triggers run after that, so what they set in
     * variables isn't there yet; saved variables are. An addon's {@code onEnable} is too
     * early to use scripts or variables, so it does that here.
     *
     * @param task what to run
     */
    void whenReady(Runnable task);

    /**
     * The script files and their functions. Their methods throw {@link IllegalStateException} until NeoSkript is
     * ready ({@link #whenReady}).
     *
     * @return the scripts
     */
    Scripts scripts();

    /**
     * Scripts' global variables, as {@code {name}} in a script. Their methods throw {@link IllegalStateException}
     * until NeoSkript is ready ({@link #whenReady}), as saved variables haven't loaded.
     *
     * @return the variables
     */
    Variables variables();

    /**
     * Values as scripts have them: their types, their text, and how Skript saves them. For an addon that writes
     * values to a file or a database the way Skript would.
     * @return the values
     */
    Values values();

    /**
     * Where code runs: the main thread on Paper, and on Folia, each region's own thread or the global one.
     *
     * @return the threads
     */
    Threads threads();

    /**
     * The script being parsed or set up on this thread, while scripts load: so an addon's literal parsers, structure
     * handlers and element factories can tell scripts apart, as skript-reflect's imports are each script's own.
     *
     * @return the script's name, as {@code quests/main.sk}, or {@code null} outside of loading scripts
     */
    @co.xenastudios.neoskript.api.annotation.Experimental
    default String scriptBeingLoaded() {
        return null;
    }

    /**
     * Reports a runtime warning, as Skript's syntax does when something goes wrong while a trigger runs: printed with
     * the script, line and element, within Skript's limits per frame, and the trigger carries on.
     *
     * @param message the warning
     */
    @co.xenastudios.neoskript.api.annotation.Experimental
    default void runtimeWarning(String message) {}

    /**
     * Reports a runtime error, as {@link #runtimeWarning}: printed, and the trigger carries on.
     *
     * @param message the error
     */
    @co.xenastudios.neoskript.api.annotation.Experimental
    default void runtimeError(String message) {}
}
