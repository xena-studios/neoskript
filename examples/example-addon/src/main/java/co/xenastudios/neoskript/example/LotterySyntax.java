/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.example;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ThreadAffinity;
import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;

/** The example addon's types and syntax. Ids start with the addon's name, so they can't clash with others'. */
final class LotterySyntax {

    /** The number the next ticket gets. */
    private static final AtomicLong NEXT = new AtomicLong(1);

    private LotterySyntax() {}

    static void register(Registrar registrar) {
        // A type: 'ticket' in patterns and scripts, read as text through its toString.
        registrar.type("ticket", "ticket", Ticket.class);
        registrar.text(Ticket.class, Ticket::toString);
        // Saved in variables.csv, as an object with two fields, under a name Skript doesn't use.
        registrar.storage("neoskriptexample:ticket", Ticket.class, new VariableCodec<>() {
            @Override
            public void encode(Ticket value, BinaryWriter out) {
                out.beginObject(2);
                out.stringField("owner", value.owner());
                out.longField("number", value.number());
            }

            @Override
            public Ticket decode(BinaryReader in) {
                in.beginObject();
                return new Ticket(in.stringField("owner"), in.longField("number"));
            }
        });

        // An expression: a new ticket, numbered in order.
        registrar.expression("NeoSkriptExampleExprNewTicket", "ticket",
                info -> (context, arguments) -> arguments[0] instanceof String owner
                        ? new Ticket(owner, NEXT.getAndIncrement()) : null,
                "[a] new ticket for %text%");

        // A condition with its negation as pattern 1, true for tickets with even numbers. A list is checked
        // as Skript checks one: all of 'a and b', any of 'a or b'.
        registrar.condition("NeoSkriptExampleCondWinning", info -> (context, arguments) -> {
            Object[] tickets = arguments[0] instanceof Object[] array ? array : new Object[] {arguments[0]};
            boolean holds = Condition.check(tickets, info.and(0), ticket -> ((Ticket) ticket).number() % 2 == 0);
            return info.pattern() == 1 ? !holds : holds;
        }, "%tickets% (is|are) winning", "%tickets% (isn't|is not|aren't|are not) winning");
        // It only reads the tickets, which belong to no region: on Folia it needn't be checked.
        registrar.affinity("NeoSkriptExampleCondWinning", ThreadAffinity.ANY);

        // An effect that fires the addon's event, with the ticket with the highest number as the winner.
        registrar.effect("NeoSkriptExampleEffDraw", info -> (context, arguments) -> {
            Object[] tickets = arguments[0] instanceof Object[] array ? array : new Object[] {arguments[0]};
            Arrays.stream(tickets).filter(Objects::nonNull).map(Ticket.class::cast)
                    .max(Comparator.comparingLong(Ticket::number))
                    .ifPresent(winner -> Bukkit.getPluginManager().callEvent(new LotteryDrawEvent(winner)));
        }, "draw [the] lottery with %tickets%");

        // An event, 'on lottery draw', whose triggers get the winner as 'event-ticket' (or just 'ticket').
        registrar.event("NeoSkriptExampleEvtDraw", List.of(LotteryDrawEvent.class), null, "lottery draw");
        registrar.eventValue(LotteryDrawEvent.class, "ticket", LotteryDrawEvent::winner);

        // A function: lotteryPrize(3) is 300.
        registrar.function("lotteryPrize", List.of(Parameter.of("tickets", "integer")), "integer", false,
                arguments -> arguments[0] instanceof Number tickets ? tickets.longValue() * 100 : null);

        // Syntax that pauses the trigger until a slow service answers.
        LotteryOffice.register(registrar);
    }
}
