/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.region;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.runtime.KeyedValues;
import co.xenastudios.neoskript.runtime.RuntimeErrors;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.errors.ErrorSites;
import co.xenastudios.neoskript.runtime.errors.ErrorSource;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Region ownership on Folia (plan 3.3). Each Folia region has its own thread, and only that thread may touch
 * the entities and blocks in it. So, in code compiled for Folia:
 * <ul>
 *   <li>an expression or condition given an entity or a block another thread owns reports a runtime error naming
 *       the line, and gives nothing (a condition fails), as Skript's runtime errors do; the trigger carries on;
 *   <li>an effect or change on an entity, block or location another thread owns runs on that thread, soon,
 *       while the trigger carries on without waiting for it;
 *   <li>what Folia itself refuses (its thread checks) is reported the same way, not as a stack trace.
 * </ul>
 * Nothing is installed on Paper, where the compiler leaves all of this out of the code it makes.
 */
public final class Regions {

    /** Reads of a player or entity that are safe from any thread on Folia: they don't touch its region's state. */
    public static final Set<String> SAFE_READS = Set.of("ExprUUID", "PropExprName", "ExprRawName", "ExprIP",
            "ExprPermissions", "ExprWorld", "ExprDisplayName", "ExprOnlinePlayersCount", "CondPermission",
            "CondIsOnline", "CondIsOp", "CondIsBanned", "ExprPing", "CondCompare", "ExprAmount", "CondIsSet",
            "CondContains");

    /** Effects that only send something to a player, which Folia allows from any thread. */
    public static final Set<String> SENDING = Set.of("EffMessage", "EffBroadcast", "EffActionBar", "EffSendTitle",
            "EffLog", "EffPlaySound", "EffSendResourcePack", "EffKick");

    /**
     * What Folia lets only its global thread change: world time, weather, gamerules, difficulty and PvP. Changed
     * from a region's thread, they run on the global thread, soon, while the trigger carries on without waiting, as
     * changes to another region's entities do.
     */
    public static final Set<String> GLOBAL_CHANGES = Set.of("ExprTime", "ExprWeather", "ExprGameRule",
            "ExprDifficulty", "EffPvP", "CondPvP");

    /** The subject of an element that acts on what only the global thread may change ({@link #GLOBAL_CHANGES}). */
    public static final int GLOBAL_SUBJECT = -2;

    private static volatile RegionAccess access;
    private static volatile Consumer<ScriptRuntimeException> errors = e -> RuntimeErrors.logError(e.getMessage());

    private Regions() {}

    /** Turns region ownership on, as on Folia; errors in effects run on another thread go to {@code errors}. */
    public static void install(RegionAccess access, Consumer<ScriptRuntimeException> errors) {
        Regions.errors = errors;
        Regions.access = access;
    }

    /** Turns region ownership off again. */
    public static void uninstall() {
        access = null;
    }

    /** Whether region ownership is on: whether code compiled now checks it. */
    public static boolean enabled() {
        return access != null;
    }

    /** What knows the server's regions, or {@code null} when ownership is off. */
    public static RegionAccess access() {
        return access;
    }

    /** Whether the element {@code id} needs no checks: it only reads what's safe anywhere, or only sends. */
    /** Whether the element {@code id} changes what only Folia's global thread may ({@link #GLOBAL_CHANGES}). */
    public static boolean global(String id) {
        int dash = id.indexOf('-');
        return GLOBAL_CHANGES.contains(dash < 0 ? id : id.substring(0, dash));
    }

    /**
     * Runs {@code task} on Folia's global thread: here if this is it (or ownership is off, on Paper), else soon,
     * without waiting for it. For what only the global thread may do, like dispatching a console command.
     */
    public static void onGlobal(Runnable task) {
        RegionAccess current = access;
        if (current == null || current.owns(null)) {
            task.run();
        } else {
            dispatch(current, ErrorSites.current(), null, task);
        }
    }

    public static boolean exempt(String id) {
        int dash = id.indexOf('-');
        String base = dash < 0 ? id : id.substring(0, dash);
        return SAFE_READS.contains(base) || SENDING.contains(base);
    }

    // ------------------------------------------------------------------------------------------------- reads

    /**
     * The first entity or block in the arguments picked by {@code mask} (a bit per argument) that another
     * thread owns, or {@code null} if there's none.
     */
    static Object foreign(RegionAccess access, Object[] arguments, long mask) {
        for (int i = 0; i < arguments.length && i < 64; i++) {
            if ((mask & (1L << i)) == 0) {
                continue;
            }
            Object argument = KeyedValues.plain(arguments[i]);
            if (argument instanceof Object[] values) {
                for (Object value : values) {
                    if (foreign(access, value)) {
                        return value;
                    }
                }
            } else if (foreign(access, argument)) {
                return argument;
            }
        }
        return null;
    }

    private static boolean foreign(RegionAccess access, Object value) {
        if (value == null) {
            return false;
        }
        RegionAccess.Kind kind = access.kind(value);
        return (kind == RegionAccess.Kind.ENTITY || kind == RegionAccess.Kind.BLOCK) && !access.owns(value);
    }

    /** Reports that {@code value}, which another thread owns, can't be read here. */
    static void readError(RegionAccess access, ErrorSites.Site site, Object value) {
        report(site, capitalize(access.describe(value)) + " is in another region, which only that region's thread"
                + " can read on Folia. Use 'run on' to run code there.");
    }

    /**
     * Whether {@code thrown} is Folia (or Paper) refusing something done on the wrong thread: its thread checks
     * throw an {@link IllegalStateException} from {@code TickThread} or {@code AsyncCatcher}.
     */
    public static boolean violation(Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof IllegalStateException || t instanceof UnsupportedOperationException) {
                StackTraceElement[] trace = t.getStackTrace();
                for (int i = 0; i < trace.length && i < 4; i++) {
                    String name = trace[i].getClassName();
                    if (name.endsWith(".TickThread") || name.endsWith(".AsyncCatcher")) {
                        return true;
                    }
                }
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /** Reports Folia refusing what an element did on this thread. */
    static void violationError(ErrorSites.Site site, Throwable thrown) {
        Throwable reason = thrown;
        while (reason.getCause() != null && reason.getCause() != reason && !(reason instanceof IllegalStateException)) {
            reason = reason.getCause();
        }
        String message = reason.getMessage();
        report(site, "This touches another region, which only that region's thread can do on Folia"
                + (message == null || message.isBlank() ? "" : " (" + message.strip() + ")")
                + ". Use 'run on' to run code there.");
    }

    private static void report(ErrorSites.Site site, String message) {
        ErrorSource source = site == null ? null : site.source();
        if (source == null) {
            RuntimeErrors.report(message);
        } else {
            RuntimeErrors.report(source, message);
        }
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    // ----------------------------------------------------------------------------------------------- effects

    /**
     * Runs an effect whose argument {@code subject} is what it acts on: here, if this thread owns it (or it
     * isn't in a region), else on the thread that does. For several values, the effect runs here for those
     * this thread owns, and once on each other's thread for each of the others.
     */
    static void run(RegionAccess access, ErrorSites.Site site, Object[] arguments, int subject,
            Consumer<Object[]> effect) {
        if (subject == GLOBAL_SUBJECT) {
            if (access.owns(null)) {
                guarded(site, () -> effect.accept(arguments));
            } else {
                dispatch(access, site, null, () -> effect.accept(arguments));
            }
            return;
        }
        Object argument = subject < 0 || subject >= arguments.length ? null : arguments[subject];
        if (argument instanceof Object[] values) {
            List<Object> here = new ArrayList<>(values.length);
            List<Object> elsewhere = null;
            for (Object value : values) {
                if (value == null || access.kind(value) == RegionAccess.Kind.NONE || access.owns(value)) {
                    here.add(value);
                } else {
                    if (elsewhere == null) {
                        elsewhere = new ArrayList<>();
                    }
                    elsewhere.add(value);
                }
            }
            if (elsewhere == null) {
                guarded(site, () -> effect.accept(arguments));
                return;
            }
            if (!here.isEmpty()) {
                Object[] mine = arguments.clone();
                mine[subject] = narrowed(values, here);
                guarded(site, () -> effect.accept(mine));
            }
            for (Object value : elsewhere) {
                Object[] theirs = arguments.clone();
                theirs[subject] = narrowed(values, List.of(value));
                dispatch(access, site, value, () -> effect.accept(theirs));
            }
            return;
        }
        if (argument == null || argument instanceof KeyedValues || access.kind(argument) == RegionAccess.Kind.NONE
                || access.owns(argument)) {
            guarded(site, () -> effect.accept(arguments));
        } else {
            dispatch(access, site, argument, () -> effect.accept(arguments));
        }
    }

    /**
     * {@code give X to all players}: changes values with their changers ({@link Values#changeValues}), each on
     * the thread that owns it.
     */
    public static void changeValues(Object targets, Object delta, Changeable.Mode mode) {
        RegionAccess current = access;
        if (current == null) {
            Values.changeValues(targets, delta, mode);
            return;
        }
        ErrorSites.Site site = ErrorSites.current();
        List<Object> here = new ArrayList<>();
        for (Object target : Values.array(targets)) {
            if (target == null || current.kind(target) == RegionAccess.Kind.NONE || current.owns(target)) {
                here.add(target);
            } else {
                dispatch(current, site, target, () -> Values.changeValues(target, delta, mode));
            }
        }
        if (!here.isEmpty()) {
            guarded(site, () -> Values.changeValues(here.toArray(), delta, mode));
        }
    }

    /** Runs {@code task} here, reporting a thread check Folia fails as a runtime error. */
    private static void guarded(ErrorSites.Site site, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            if (!violation(e)) {
                throw e;
            }
            violationError(site, e);
        }
    }

    /** Runs {@code task} on {@code owner}'s thread, where its errors are reported for the line it came from. */
    private static void dispatch(RegionAccess access, ErrorSites.Site site, Object owner, Runnable task) {
        access.execute(owner, () -> ErrorSites.within(site, () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                if (violation(e)) {
                    violationError(site, e);
                } else if (e instanceof ScriptRuntimeException script) {
                    errors.accept(script);
                } else {
                    ErrorSource source = site == null ? null : site.source();
                    errors.accept(new ScriptRuntimeException(source == null ? "-unknown-" : source.script(),
                            source == null ? 0 : source.lineNumber(), e));
                }
            }
        }));
    }

    /** {@code values}' array type, holding only {@code kept}. */
    private static Object[] narrowed(Object[] values, List<Object> kept) {
        Object[] array = (Object[]) Array.newInstance(values.getClass().getComponentType(), kept.size());
        return kept.toArray(array);
    }
}
