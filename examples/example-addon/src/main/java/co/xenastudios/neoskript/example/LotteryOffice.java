/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.example;

import co.xenastudios.neoskript.api.element.PausingEffect;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ThreadAffinity;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Syntax that pauses the trigger, as a database query or a web request would: the lottery office stands for a slow
 * remote service, which answers on its own thread a moment later. The trigger waits for it like {@code wait}, and
 * carries on, on its own thread, once the answer is there.
 */
final class LotteryOffice {

    /** How long the office takes to answer, and how long the slow one takes. */
    private static final long ANSWER_MILLIS = 250;
    private static final long SLOW_MILLIS = 3_000;

    private LotteryOffice() {}

    /** Runs a task after {@code millis}, off the server's threads. */
    private static Executor after(long millis) {
        return CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS);
    }

    static void register(Registrar registrar) {
        // A pausing expression: 'set {_jackpot} to the lottery jackpot'. The line waits for the value. The office
        // called "nowhere" doesn't answer, which is a runtime error on the line; "the slow office" takes 3 seconds.
        registrar.pausingExpression("NeoSkriptExampleExprJackpot", "integer", info -> (context, arguments) -> {
            String office = arguments[0] instanceof String name ? name : "the office";
            if (office.equals("nowhere")) {
                return CompletableFuture.failedFuture(new IOException("The lottery office at nowhere didn't answer"));
            }
            long millis = office.equals("the slow office") ? SLOW_MILLIS : ANSWER_MILLIS;
            return CompletableFuture.supplyAsync(() -> 1000L, after(millis));
        }, "[the] lottery jackpot [from %-text%]");
        // It reads nothing a region owns, so on Folia it needn't be checked.
        registrar.affinity("NeoSkriptExampleExprJackpot", ThreadAffinity.ANY);

        // A pausing effect that stores what it gets in a variable: 'register {_tickets::*} with the lottery office
        // and store the receipts in {_receipts::*}'. A map sets the list's indices, and a map in it a list under its
        // index: {_receipts::2::owner}.
        registrar.pausingEffect("NeoSkriptExampleEffRegister", info -> PausingEffect.storing(1, (context, arguments) -> {
            Object[] tickets = arguments[0] instanceof Object[] array ? array : new Object[] {arguments[0]};
            return CompletableFuture.supplyAsync(() -> {
                Map<String, Object> receipts = new LinkedHashMap<>();
                for (Object value : tickets) {
                    if (value instanceof Ticket ticket) {
                        receipts.put(Long.toString(ticket.number()),
                                Map.of("owner", ticket.owner(), "receipt", "R-" + ticket.number()));
                    }
                }
                return receipts;
            }, after(ANSWER_MILLIS));
        }), "register %tickets% with [the] lottery office [and store [the] receipts in %-objects%]");
        registrar.affinity("NeoSkriptExampleEffRegister", ThreadAffinity.ANY);

        // A pausing effect whose answer never comes: after its timeout, 2 seconds, it's a runtime error and the
        // trigger carries on.
        registrar.pausingEffect("NeoSkriptExampleEffAwaitOffice", info -> (context, arguments) -> new CompletableFuture<>(),
                "wait for [the] lottery office to answer");
        registrar.timeout("NeoSkriptExampleEffAwaitOffice", Duration.ofSeconds(2));
    }
}
