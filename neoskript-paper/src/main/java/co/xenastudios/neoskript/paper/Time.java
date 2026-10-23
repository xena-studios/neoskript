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

        registrar.expression("ExprDifference", "object", info -> (context, args) -> switch (args[0]) {
            case Date a when args[1] instanceof Date b -> a.difference(b);
            case Timespan a when args[1] instanceof Timespan b -> new Timespan(Math.abs(a.millis() - b.millis()));
            case Long a when args[1] instanceof Long b -> Math.abs(a - b);
            case Number a when args[1] instanceof Number b -> Math.abs(a.doubleValue() - b.doubleValue());
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

        registrar.expression("ExprUnixTicks", "number", info -> (context, args) -> {
            Object[] dates = (Object[]) args[0];
            Object[] seconds = new Object[dates.length];
            for (int i = 0; i < dates.length; i++) {
                seconds[i] = dates[i] instanceof Date date ? date.millis() / 1000.0 : null;
            }
            return seconds.length == 1 ? seconds[0] : seconds;
        }, "[the] unix timestamp of %dates%", "%dates%'[s] unix timestamp");
    }
}
