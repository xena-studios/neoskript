/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compat;

import co.xenastudios.neoskript.compiler.Library;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NeoSkriptCompat while it runs: Skript's registries as addons see them (types, conversions, comparisons, event
 * values, functions), and what turns what addons register into NeoSkript's syntax.
 */
public final class Compat {

    private static volatile Compat current;

    private final Library library;
    private final Logger logger;
    private final CompatTypes types;
    private final CompatValues values = new CompatValues();
    private final CompatConversions conversions = new CompatConversions();
    private final CompatComparisons comparisons = new CompatComparisons();
    private final CompatFunctions functions = new CompatFunctions();
    private final CompatEventValues eventValues = new CompatEventValues();
    private final CompatSkript skript;
    private volatile boolean accepting = true;

    /** The errors an addon's element gives while it's set up, on this thread: null when none is. */
    private static final ThreadLocal<List<String>> SETUP_ERRORS = new ThreadLocal<>();

    private Compat(Library library, Logger logger) {
        this.library = library;
        this.logger = logger;
        this.types = new CompatTypes(library);
        this.skript = new CompatSkript(this);
    }

    /** NeoSkriptCompat, while it's enabled. */
    public static Compat get() {
        Compat compat = current;
        if (compat == null) {
            throw new IllegalStateException("NeoSkriptCompat isn't enabled");
        }
        return compat;
    }

    /** Whether NeoSkriptCompat is enabled. */
    public static boolean running() {
        return current != null;
    }

    /** Starts NeoSkriptCompat on NeoSkript's library. */
    public static Compat start(Library library, Logger logger) {
        Compat compat = new Compat(library, logger);
        current = compat;
        return compat;
    }

    /** Stops NeoSkriptCompat: what addons registered goes, with it the last references to their classes. */
    public static void stop() {
        current = null;
    }

    public Library library() {
        return library;
    }

    public Logger logger() {
        return logger;
    }

    public CompatTypes types() {
        return types;
    }

    public CompatValues values() {
        return values;
    }

    public CompatConversions conversions() {
        return conversions;
    }

    public CompatComparisons comparisons() {
        return comparisons;
    }

    public CompatFunctions functions() {
        return functions;
    }

    public CompatEventValues eventValues() {
        return eventValues;
    }

    public CompatSkript skript() {
        return skript;
    }

    /** Whether addons can still register: until NeoSkript has loaded scripts, as Skript until it has. */
    public boolean accepting() {
        return accepting;
    }

    public void stopAccepting() {
        accepting = false;
    }

    /** NeoSkript's id for values of {@code javaClass}: its type, or the nearest one it's a kind of. */
    public String typeId(Class<?> javaClass) {
        if (javaClass == null) {
            return "object";
        }
        Class<?> component = CompatAdapters.neoskriptClass(javaClass.isArray() ? javaClass.getComponentType() : javaClass);
        String id = library.typeOf(component);
        return id == null ? "object" : id;
    }

    /** Runs {@code setup} catching the errors it gives with {@code Skript.error}: the first is returned, or null. */
    static String withSetupErrors(Runnable setup) {
        List<String> outer = SETUP_ERRORS.get();
        List<String> errors = new ArrayList<>();
        SETUP_ERRORS.set(errors);
        try {
            setup.run();
        } finally {
            SETUP_ERRORS.set(outer);
        }
        return errors.isEmpty() ? null : errors.getFirst();
    }

    /** An error from an addon: kept for the line being set up, or logged when there's none. */
    public void error(String message) {
        List<String> errors = SETUP_ERRORS.get();
        if (errors != null) {
            errors.add(message);
        } else {
            logger.severe(message);
        }
    }

    /** A warning from an addon: logged. Warnings while a line is set up are dropped, as NeoSkript gives its own. */
    public void warning(String message) {
        if (SETUP_ERRORS.get() == null) {
            logger.warning(message);
        }
    }

    public void exception(Throwable cause, String... info) {
        logger.log(Level.SEVERE, String.join(" ", info), cause);
    }
}
