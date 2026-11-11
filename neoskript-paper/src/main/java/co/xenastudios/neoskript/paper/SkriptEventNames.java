/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.registration.Registrar;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Skript's events as it names them in errors, from {@code skript-events.tsv}: all of them, whether NeoSkript has
 * them or not, as Skript's event-restricted syntax lists every event it can be used in ({@code 'the drops' can
 * only be used in the on death event or the on harvest block event}). Events whose classes this server doesn't
 * have are left out, as Skript doesn't register them.
 */
final class SkriptEventNames {

    /** One of Skript's events: its name ({@code On Break / Mine}), event classes and patterns. */
    record Event(String name, List<Class<?>> classes, List<String> patterns) {}

    /** Skript's own event classes, as NeoSkript has them. */
    private static final Map<String, Class<?>> OWN = Map.of(
            "ch.njol.skript.events.bukkit.ExperienceSpawnEvent", EventBridges.ExperienceSpawnEvent.class,
            "ch.njol.skript.events.bukkit.SkriptStartEvent", EventBridges.SkriptStartEvent.class,
            "ch.njol.skript.events.bukkit.SkriptStopEvent", EventBridges.SkriptStopEvent.class,
            "ch.njol.skript.events.bukkit.ScheduledEvent", EventBridges.ScheduledEvent.class,
            "ch.njol.skript.events.bukkit.ScheduledNoWorldEvent", co.xenastudios.neoskript.runtime.PeriodicalEvent.class,
            "ch.njol.skript.events.EvtRealTime$RealTimeEvent", EventBridges.RealTimeEvent.class,
            "ch.njol.skript.events.bukkit.ScriptEvent", co.xenastudios.neoskript.runtime.ScriptLoadEvent.class);

    private static volatile List<Event> events;

    private SkriptEventNames() {}

    /** Hands Skript's events to the compiler, for the names of triggers' events and of the command events. */
    static void register(Registrar registrar) {
        for (Event event : events()) {
            registrar.eventName(event.name(), event.classes(), event.patterns().toArray(String[]::new));
        }
    }

    /** Skript's events, in its order. */
    static List<Event> events() {
        List<Event> loaded = events;
        if (loaded == null) {
            loaded = load();
            events = loaded;
        }
        return loaded;
    }

    private static List<Event> load() {
        List<Event> loaded = new ArrayList<>();
        try (InputStream in = SkriptEventNames.class.getResourceAsStream("/skript-events.tsv");
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t");
                List<Class<?>> classes = new ArrayList<>();
                for (String name : parts[1].split(",")) {
                    if (OWN.containsKey(name)) {
                        classes.add(OWN.get(name));
                        continue;
                    }
                    try {
                        classes.add(Class.forName(name, false, SkriptEventNames.class.getClassLoader()));
                    } catch (ClassNotFoundException | LinkageError e) {
                        // Not on this server: Skript leaves the event out.
                        classes = null;
                        break;
                    }
                }
                if (classes != null) {
                    loaded.add(new Event(parts[0], List.copyOf(classes),
                            List.of(Arrays.copyOfRange(parts, 2, parts.length))));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(loaded);
    }

    /**
     * Skript's {@code supportedEventsNames}: each event one of {@code supported} is (or is a subclass of) an event
     * class of, as {@code the on death event}, once for each such pair, joined with commas and a last {@code or}.
     */
    static String names(Class<?>... supported) {
        List<String> names = new ArrayList<>();
        for (Event event : events()) {
            for (Class<?> wanted : supported) {
                for (Class<?> eventClass : event.classes()) {
                    if (eventClass.isAssignableFrom(wanted)) {
                        names.add("the " + event.name().toLowerCase(Locale.ENGLISH) + " event");
                    }
                }
            }
        }
        return join(names);
    }

    /** Skript's {@code StringUtils.join(names, ", ", " or ")}. */
    static String join(List<String> names) {
        if (names.isEmpty()) {
            return "";
        }
        if (names.size() == 1) {
            return names.getFirst();
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.getLast();
    }

    /** Skript's {@code Utils.a}: {@code an on join}, {@code a command}. */
    static String a(String text) {
        return ("aeiouAEIOU".indexOf(text.charAt(0)) >= 0 ? "an " : "a ") + text;
    }

    /** The element's event as Skript's errors call it: {@code an on join event}, {@code a command event}. */
    static String event(ElementInfo info) {
        return info.eventName() == null ? "this event" : a(info.eventName()) + " event";
    }

    /**
     * Skript's error for an event value expression where the event has no such value:
     * {@code There's no spawn reason in an on join event.}
     */
    static SyntaxError noValue(ElementInfo info, String typeName) {
        return new SyntaxError("There's no " + typeName + " in " + event(info) + ".");
    }

    /**
     * Whether the element's event can have a value only events of {@code classes} have, as Skript resolves event
     * values: the event is one of them, or its subclasses are (an {@code on spawn} can be a creature's spawn).
     */
    static boolean related(ElementInfo info, Class<?>... classes) {
        for (Class<?> current : info.eventClasses()) {
            for (Class<?> wanted : classes) {
                if (wanted.isAssignableFrom(current) || current.isAssignableFrom(wanted)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Fails outside events of {@code supported}, as Skript's event-restricted syntax:
     * {@code 'clear drops' can only be used in the on break / mine event, ...}, quoting the element as written.
     */
    static void restrict(ElementInfo info, String written, Class<?>... supported) {
        if (!info.isEvent(supported)) {
            throw new SyntaxError("'" + written + "' can only be used in " + names(supported));
        }
    }
}
