/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Date;
import co.xenastudios.neoskript.runtime.Timespan;

/** Dates and timespans: now, differences, Unix timestamps. */
final class Time {

    private Time() {}

    static void register(Registrar registrar) {
        registrar.expression("ExprNow", "date", info -> (context, args) -> Date.now(), "now");
        registrar.expression("ExprTime", "time", info -> new WorldTime(), "[the] time[s] [([with]in|of) %worlds%]",
                "%worlds%'[s] time[s]");

        registrar.expression("ExprDifference", "object", info -> (context, args) -> switch (args[0]) {
            case Date a when args[1] instanceof Date b -> a.difference(b);
            case Timespan a when args[1] instanceof Timespan b -> new Timespan(Math.abs(a.millis() - b.millis()));
            case Long a when args[1] instanceof Long b -> Math.abs(a - b);
            case Number a when args[1] instanceof Number b -> Math.abs(a.doubleValue() - b.doubleValue());
            case org.bukkit.util.Vector a when args[1] instanceof org.bukkit.util.Vector b -> new org.bukkit.util.Vector(
                    Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ()));
            case null, default -> null;
        }, "difference (between|of) %object% and %object%");

        registrar.expression("ExprDateAgoLater", "date", info -> {
            boolean later = info.pattern() == 1;
            return (context, args) -> {
                if (!(args[0] instanceof Timespan span)) {
                    return null;
                }
                long from = args[1] instanceof Date date ? date.millis() : System.currentTimeMillis();
                return new Date(later ? from + span.millis() : from - span.millis());
            };
        }, "%timespan% (ago|in the past|before [the] [date] %date%)", "%timespan% (later|(from|after) [the] [date] %date%)");

        registrar.condition("CondPastFuture", info -> {
            boolean future = info.hasTag("future");
            boolean negated = info.pattern() % 2 == 1;
            return (context, args) -> {
                Object[] dates = (Object[]) args[0];
                if (dates.length == 0) {
                    return negated;
                }
                long now = System.currentTimeMillis();
                for (Object value : dates) {
                    // A date within a few milliseconds of now is now, not past or future: 'now is not in the past'.
                    boolean holds = value instanceof Date date
                            && (future ? date.millis() > now + 5 : date.millis() < now - 5);
                    if (holds == negated) {
                        return false;
                    }
                }
                return true;
            };
        }, "%dates% (is|are) in the (past|future:future)", "%dates% (isn't|is not|aren't|are not) in the (past|future:future)",
                "%dates% ha(s|ve) passed", "%dates% ha(s|ve)(n't| not) passed");
        registrar.expression("ExprFormatDate", "string", info -> (context, args) -> {
            String pattern = args[1] instanceof String given ? given : "yyyy-MM-dd HH:mm:ss z";
            java.text.SimpleDateFormat format;
            try {
                format = new java.text.SimpleDateFormat(pattern);
            } catch (IllegalArgumentException invalid) {
                return null;
            }
            java.util.List<Object> texts = new java.util.ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof Date date) {
                    texts.add(format.format(new java.util.Date(date.millis())));
                }
            }
            return texts.size() == 1 ? texts.getFirst() : texts.toArray();
        }, "%dates% formatted [human-readable] [(with|as) %text%]",
                "[human-readable] formatted %dates% [(with|as) %text%]");
        registrar.expression("LitEternity", "timespan", info -> (context, args) -> Timespan.FOREVER,
                "[an] eternity", "forever", "[an] ([out] of|outside [of]) time", "[an] (infinite|indefinite) (duration|timespan)");
        registrar.expression("ExprTimeSince", "timespan", info -> {
            boolean until = info.hasTag("until");
            return (context, args) -> {
                java.util.List<Object> times = new java.util.ArrayList<>();
                long now = System.currentTimeMillis();
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof Date date) {
                        // A date the other side of now is no time at all.
                        times.add(new Timespan(Math.max(0, until ? date.millis() - now : now - date.millis())));
                    }
                }
                return times.size() == 1 ? times.getFirst() : times.toArray();
            };
        }, "[the] time (since|until:until) %dates%", "[the] (elapsed time|time left:until) (since|until:until) %dates%");
        registrar.condition("CondIsInfinite", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> {
                Object[] values = (Object[]) args[0];
                for (Object value : values) {
                    // Potion effects in variables reach here too: 'assert {_potion} is infinite'.
                    boolean infinite = value instanceof Timespan span ? span.isInfinite()
                            : value instanceof Potions.Effect effect && effect.infinite;
                    if (!(value instanceof Timespan || value instanceof Potions.Effect) || infinite == negated) {
                        return false;
                    }
                }
                return values.length > 0 || negated;
            };
        }, "%timespans/skriptpotioneffects% (is|are) infinite",
                "%timespans/skriptpotioneffects% (isn't|is not|aren't|are not) infinite");
        registrar.expression("ExprTimespanDetails", "number", info -> {
            long unit = switch (info.tags().stream().filter(t -> t.startsWith("u_")).findFirst().orElse("u_ticks")) {
                case "u_seconds" -> 1000;
                case "u_minutes" -> 60_000;
                case "u_hours" -> 3_600_000;
                case "u_days" -> 86_400_000L;
                case "u_weeks" -> 604_800_000L;
                case "u_months" -> 2_592_000_000L;
                case "u_years" -> 31_536_000_000L;
                default -> 50;
            };
            return (context, args) -> {
                java.util.List<Object> amounts = new java.util.ArrayList<>();
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof Timespan span) {
                        amounts.add(unit == 50 ? (Object) (span.millis() / 50) : (Object) ((double) span.millis() / unit));
                    }
                }
                return amounts.size() == 1 ? amounts.getFirst() : amounts.toArray();
            };
        }, "[the] (u_ticks:ticks|u_seconds:seconds|u_minutes:minutes|u_hours:hours|u_days:days|u_weeks:weeks|u_months:months|u_years:years) of %timespans%");
        registrar.expression("ExprUnixDate", "date", info -> (context, args) -> {
            java.util.List<Object> dates = new java.util.ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof Number seconds) {
                    dates.add(new Date(Math.round(seconds.doubleValue() * 1000)));
                }
            }
            return dates.size() == 1 ? dates.getFirst() : dates.toArray();
        }, "[the] unix date of %numbers%", "%numbers%'[s] unix date");
        registrar.expression("ExprUnixTicks", "number", info -> (context, args) -> {
            Object[] dates = (Object[]) args[0];
            Object[] seconds = new Object[dates.length];
            for (int i = 0; i < dates.length; i++) {
                seconds[i] = dates[i] instanceof Date date ? date.millis() / 1000.0 : null;
            }
            return seconds.length == 1 ? seconds[0] : seconds;
        }, "[the] unix timestamp of %dates%", "%dates%'[s] unix timestamp");
    }

    /** {@code the time in %worlds%}: the time of day there, which can be set, added to and taken from. */
    private static final class WorldTime implements co.xenastudios.neoskript.api.element.Expression,
            co.xenastudios.neoskript.api.element.Changeable {
        @Override
        public Object get(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments) {
            java.util.List<Object> times = new java.util.ArrayList<>();
            for (Object value : worlds(arguments)) {
                if (value instanceof org.bukkit.World world) {
                    times.add(co.xenastudios.neoskript.runtime.TimeOfDay.ofWorldTicks(world.getTime()));
                }
            }
            return times.size() == 1 ? times.getFirst() : times.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET -> new Class<?>[] {co.xenastudios.neoskript.runtime.TimeOfDay.class};
                case ADD, REMOVE -> new Class<?>[] {co.xenastudios.neoskript.runtime.TimeOfDay.class, Timespan.class};
                default -> null;
            };
        }

        @Override
        public void change(co.xenastudios.neoskript.api.element.TriggerContext context, Object[] arguments, Object[] delta,
                Mode mode) {
            if (delta == null || delta.length == 0) {
                return;
            }
            long ticks = switch (delta[0]) {
                case co.xenastudios.neoskript.runtime.TimeOfDay time -> mode == Mode.SET ? time.ticks() : time.ticksAfterMidnight();
                case Timespan span -> span.ticks();
                default -> 0;
            };
            for (Object value : worlds(arguments)) {
                if (value instanceof org.bukkit.World world) {
                    long now = world.getTime();
                    world.setTime(Math.floorMod(switch (mode) {
                        case ADD -> now + ticks;
                        case REMOVE -> now - ticks;
                        default -> ticks;
                    }, 24000L));
                }
            }
        }

        private static Object[] worlds(Object[] arguments) {
            Object worlds = arguments.length == 0 ? null : arguments[0];
            return worlds instanceof Object[] array ? array : worlds == null ? new Object[0] : new Object[] {worlds};
        }
    }
}
