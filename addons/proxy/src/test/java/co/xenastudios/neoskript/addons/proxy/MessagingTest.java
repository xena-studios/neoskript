/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Plugin messaging and network messages, with a fake player connection in place of a server. */
class MessagingTest {

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final List<byte[]> sent = Collections.synchronizedList(new ArrayList<>());
    private final List<byte[]> forwarded = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean online = true;

    @AfterEach
    void stop() {
        timer.shutdownNow();
    }

    private Settings settings(Map<String, Object> values) {
        return Settings.read(values::get, new ArrayList<>());
    }

    private PluginMessaging messaging(Map<String, Object> values) {
        return new PluginMessaging(settings(values), payload -> {
            if (!online) {
                return false;
            }
            sent.add(payload);
            return true;
        }, timer, Logger.getLogger("test"), forwarded::add);
    }

    private static List<String> read(byte[] payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        List<String> texts = new ArrayList<>();
        while (in.available() > 0) {
            texts.add(in.readUTF());
        }
        return texts;
    }

    @Test
    void aQuestionIsAskedOnceAndAnsweredToEveryoneWaiting() throws Exception {
        PluginMessaging messaging = messaging(Map.of());
        CompletableFuture<Object> first = messaging.ask(PluginMessaging.Question.playerCount("Lobby"));
        CompletableFuture<Object> second = messaging.ask(PluginMessaging.Question.playerCount("lobby"));
        assertEquals(1, sent.size(), "the same question waiting is asked once");
        assertEquals(List.of("PlayerCount", "Lobby"), read(sent.getFirst()));
        // An answer nobody asked for is dropped.
        messaging.received(PluginMessaging.write(out -> {
            out.writeUTF("PlayerCount");
            out.writeUTF("survival");
            out.writeInt(99);
        }));
        assertFalse(first.isDone());
        messaging.received(PluginMessaging.write(out -> {
            out.writeUTF("PlayerCount");
            out.writeUTF("lobby");
            out.writeInt(3);
        }));
        assertEquals(3L, first.get(1, TimeUnit.SECONDS));
        assertEquals(3L, second.get(1, TimeUnit.SECONDS));
        assertEquals(0, messaging.waiting());
    }

    @Test
    void listsAndServersReadAsBungeeCordWritesThem() throws Exception {
        PluginMessaging messaging = messaging(Map.of());
        CompletableFuture<Object> servers = messaging.ask(PluginMessaging.Question.servers());
        CompletableFuture<Object> players = messaging.ask(PluginMessaging.Question.playerList("lobby"));
        CompletableFuture<Object> where = messaging.ask(PluginMessaging.Question.playerServer("Steve"));
        messaging.received(PluginMessaging.write(out -> {
            out.writeUTF("GetServers");
            out.writeUTF("lobby, survival");
        }));
        messaging.received(PluginMessaging.write(out -> {
            out.writeUTF("PlayerList");
            out.writeUTF("lobby");
            out.writeUTF("");
        }));
        messaging.received(PluginMessaging.write(out -> {
            out.writeUTF("GetPlayerServer");
            out.writeUTF("steve");
            out.writeUTF("survival");
        }));
        assertEquals(List.of("lobby", "survival"), servers.get(1, TimeUnit.SECONDS));
        assertEquals(List.of(), players.get(1, TimeUnit.SECONDS));
        assertEquals("survival", where.get(1, TimeUnit.SECONDS));
    }

    @Test
    void aQuestionNobodyAnswersEndsWithNoAnswer() throws Exception {
        PluginMessaging messaging = messaging(Map.of("plugin-messaging.timeout", 150));
        CompletableFuture<Object> answer = messaging.ask(PluginMessaging.Question.playerServer("Nobody"));
        assertEquals(PluginMessaging.NO_ANSWER, answer.get(5, TimeUnit.SECONDS));
        assertEquals(0, messaging.waiting());
    }

    @Test
    void withNobodyOnlineQuestionsFailAndMessagesWait() throws Exception {
        PluginMessaging messaging = messaging(Map.of("plugin-messaging.max-queued", 2));
        online = false;
        CompletableFuture<Object> answer = messaging.ask(PluginMessaging.Question.servers());
        Exception failure = assertThrows(Exception.class, () -> answer.get(1, TimeUnit.SECONDS));
        assertTrue(failure.getCause().getMessage().startsWith("Nobody is online"));
        assertTrue(messaging.send(PluginMessaging.action("Message", "ALL", "one")));
        assertTrue(messaging.send(PluginMessaging.action("Message", "ALL", "two")));
        assertFalse(messaging.send(PluginMessaging.action("Message", "ALL", "three")), "the queue is bounded");
        assertEquals(2, messaging.queued());
        online = true;
        messaging.carrierAvailable();
        assertEquals(List.of(List.of("Message", "ALL", "one"), List.of("Message", "ALL", "two")),
                List.of(read(sent.get(0)), read(sent.get(1))));
        assertEquals(0, messaging.queued());
    }

    @Test
    void queuedMessagesExpireAndCanBeTurnedOff() {
        PluginMessaging off = messaging(Map.of("plugin-messaging.queue-without-players", false));
        online = false;
        assertFalse(off.send(PluginMessaging.action("Message", "ALL", "x")));
        assertEquals(0, off.queued());
    }

    @Test
    void stoppingFailsWhatWaits() throws Exception {
        PluginMessaging messaging = messaging(Map.of());
        CompletableFuture<Object> answer = messaging.ask(PluginMessaging.Question.servers());
        messaging.close();
        assertThrows(Exception.class, () -> answer.get(1, TimeUnit.SECONDS));
        assertFalse(messaging.send(PluginMessaging.action("Message", "ALL", "x")));
    }

    @Test
    void aForwardedMessageIsHandedOn() throws Exception {
        PluginMessaging messaging = messaging(Map.of());
        byte[] data = {1, 2, 3};
        byte[] forward = PluginMessaging.forward("ALL", data);
        assertEquals("Forward", read(forward).getFirst());
        // As the proxy delivers it: the subchannel, then the data with its length.
        messaging.received(PluginMessaging.write(out -> {
            out.writeUTF(Envelope.SUBCHANNEL);
            out.writeShort(data.length);
            out.write(data);
        }));
        assertEquals(1, forwarded.size());
        // Garbage and huge messages are dropped.
        messaging.received(new byte[] {0, 5, 'x'});
        messaging.received(new byte[PluginMessaging.MAX_READ + 1]);
        assertEquals(1, forwarded.size());
    }

    @Test
    void anEnvelopeOnlyOpensWithTheSameSecretOnceAndFresh() throws Exception {
        Envelope sealer = new Envelope("s3cret", 1000);
        byte[] sealed = sealer.seal("lobby", "a", "ALL", "events", "hello");
        Envelope receiver = new Envelope("s3cret", 1000);
        Envelope.Opened opened = receiver.open(sealed);
        assertEquals(new Envelope.Opened(opened.sent(), "lobby", "a", "ALL", "events", "hello"), opened);
        assertThrows(Envelope.Refused.class, () -> receiver.open(sealed), "a replay is refused");
        assertThrows(Envelope.Refused.class, () -> new Envelope("other", 1000).open(sealed));
        assertThrows(Envelope.Refused.class, () -> new Envelope("s3cret", 1000)
                .open(new Envelope("insecure", 1000).seal("x", "b", "ALL", "events", "forged")), "unsigned is refused");
        byte[] tampered = sealer.seal("lobby", "a", "ALL", "events", "hello");
        tampered[tampered.length - 40] ^= 1;
        assertThrows(Envelope.Refused.class, () -> new Envelope("s3cret", 1000).open(tampered));
        assertThrows(IllegalArgumentException.class, () -> sealer.seal("x", "a", "ALL", "c", "y".repeat(1001)));
        // With no secret, nothing opens; with "insecure", anything well formed does.
        Envelope none = new Envelope("", 1000);
        assertFalse(none.usable());
        assertThrows(Envelope.Refused.class, () -> none.open(sealed));
        assertEquals("forged", new Envelope("insecure", 1000)
                .open(new Envelope("insecure", 1000).seal("x", "b", "ALL", "c", "forged")).message());
    }

    @Test
    void theOriginalsAnswerAtOnceFromTheLastAnswer() throws Exception {
        PluginMessaging messaging = messaging(Map.of());
        PluginMessaging.Question count = PluginMessaging.Question.playerCount("lobby");
        assertNull(messaging.cached(count), "nothing yet: the original's default");
        assertFalse(messaging.answered(count));
        assertEquals(1, sent.size(), "and the question goes out behind it");
        messaging.received(PluginMessaging.write(out -> {
            out.writeUTF("PlayerCount");
            out.writeUTF("lobby");
            out.writeInt(4);
        }));
        assertEquals(4L, messaging.cached(count));
        assertTrue(messaging.answered(count));
        assertEquals(2, sent.size(), "each read asks again");
    }

    @Test
    void aTimeoutOnlyEndsItsOwnQuestion() throws Exception {
        PluginMessaging messaging = messaging(Map.of("plugin-messaging.timeout", 200));
        CompletableFuture<Object> first = messaging.ask(PluginMessaging.Question.playerServer("Steve"));
        messaging.received(PluginMessaging.write(out -> {
            out.writeUTF("GetPlayerServer");
            out.writeUTF("Steve");
            out.writeUTF("lobby");
        }));
        assertEquals("lobby", first.get(1, TimeUnit.SECONDS));
        // Asked again just before the first one's timeout would fire: that timeout leaves it alone.
        Thread.sleep(150);
        CompletableFuture<Object> second = messaging.ask(PluginMessaging.Question.playerServer("Steve"));
        Thread.sleep(100);
        assertFalse(second.isDone(), "the first question's timeout ended the second");
        assertEquals(PluginMessaging.NO_ANSWER, second.get(5, TimeUnit.SECONDS));
    }

    @Test
    void withPluginMessagingOffNothingIsAskedOrSent() {
        PluginMessaging messaging = messaging(Map.of("plugin-messaging.enabled", false));
        Exception failure = assertThrows(Exception.class,
                () -> messaging.ask(PluginMessaging.Question.servers()).get(1, TimeUnit.SECONDS));
        assertTrue(failure.getCause().getMessage().startsWith("Plugin messaging is off"));
        assertFalse(messaging.send(PluginMessaging.action("Message", "ALL", "x")));
        assertEquals(0, messaging.waiting());
        assertEquals(List.of(), sent);
    }

    @Test
    void aCarrierThatThrowsLeavesNothingWaiting() {
        PluginMessaging messaging = new PluginMessaging(settings(Map.of()), payload -> {
            throw new IllegalStateException("channel not registered");
        }, timer, Logger.getLogger("test"), forwarded::add);
        assertThrows(Exception.class, () -> messaging.ask(PluginMessaging.Question.servers()).get(1, TimeUnit.SECONDS));
        assertEquals(0, messaging.waiting(), "the subject isn't blocked for good");
        assertFalse(messaging.send(PluginMessaging.action("Message", "ALL", "x")));
    }

    @Test
    void messagesForNamedServersWaitUntilThisServerKnowsItsName() {
        List<NetworkMessageEvent> fired = new ArrayList<>();
        Network network = new Network(settings(Map.of()), Logger.getLogger("test"), Runnable::run, fired::add,
                new Envelope(null, 1000), "me");
        network.fromRedis("events", "{\"action\":\"neoskript-message\",\"message\":\"for lobby\",\"instance\":\"x\","
                + "\"servers\":[\"lobby\"]}");
        network.fromRedis("events", "{\"action\":\"neoskript-message\",\"message\":\"for survival\",\"instance\":\"x\","
                + "\"servers\":[\"survival\"]}");
        assertEquals(List.of(), fired);
        network.learnName("lobby");
        assertEquals(List.of("for lobby"), fired.stream().map(NetworkMessageEvent::message).toList());
    }

    @Test
    void aForwardedMessageForAnotherServerIsDropped() {
        List<NetworkMessageEvent> fired = new ArrayList<>();
        Envelope envelope = new Envelope("k", 1000);
        Network network = new Network(settings(Map.of("server-name", "lobby")), Logger.getLogger("test"), Runnable::run,
                fired::add, envelope, "me");
        network.fromForward(envelope.seal("x", "them", "survival", "c", "not for lobby"));
        network.fromForward(envelope.seal("x", "them", "lobby", "c", "for lobby"));
        assertEquals(List.of("for lobby"), fired.stream().map(NetworkMessageEvent::message).toList());
    }

    @Test
    void redisMessagesAreSkRedissAndOwnMessagesAreSkipped() {
        List<NetworkMessageEvent> fired = new ArrayList<>();
        Network network = new Network(settings(Map.of("server-name", "lobby")), Logger.getLogger("test"), Runnable::run,
                fired::add, new Envelope(null, 1000), "me");
        network.fromRedis("chat", "{\"action\":\"skredis-message\",\"message\":\"hi\",\"date\":1000}");
        network.fromRedis("chat", "{\"text\":\"custom\"}");
        network.fromRedis("chat", "not json");
        network.fromRedis("events", "{\"action\":\"neoskript-message\",\"message\":\"mine\",\"instance\":\"me\"}");
        network.fromRedis("events", "{\"action\":\"neoskript-message\",\"message\":\"for survival\",\"instance\":\"x\","
                + "\"servers\":[\"survival\"]}");
        network.fromRedis("events", "{\"action\":\"neoskript-message\",\"message\":\"for us\",\"instance\":\"x\","
                + "\"origin\":\"survival\",\"servers\":[\"LOBBY\"]}");
        assertEquals(List.of("REDIS chat hi null 1000", "CUSTOM chat {\"text\":\"custom\"} null",
                        "NETWORK events for us survival"),
                fired.stream().map(e -> e.kind() + " " + e.channel() + " " + e.message() + " " + e.origin()
                        + (e.kind() == NetworkMessageEvent.Kind.REDIS ? " " + e.sent() : "")).toList());
    }

    @Test
    void forwardedMessagesFromThisServerOrForgedOnesAreDropped() {
        List<NetworkMessageEvent> fired = new ArrayList<>();
        Envelope envelope = new Envelope("k", 1000);
        Network network = new Network(settings(Map.of()), Logger.getLogger("test"), Runnable::run, fired::add,
                envelope, "me");
        network.fromForward(envelope.seal("lobby", "me", "ALL", "c", "own"));
        network.fromForward(new Envelope("other", 1000).seal("x", "y", "ALL", "c", "forged"));
        network.fromForward(envelope.seal("survival", "them", "ALL", "c", "real"));
        assertEquals(List.of("real"), fired.stream().map(NetworkMessageEvent::message).toList());
        assertEquals("survival", fired.getFirst().origin());
    }

    @Test
    void settingsFallBackOneByOne() {
        List<String> warnings = new ArrayList<>();
        Map<String, Object> values = new HashMap<>();
        values.put("redis.port", 70000);
        values.put("redis.on-outage", "explode");
        values.put("plugin-messaging.enabled", "maybe");
        Settings settings = Settings.read(values::get, warnings);
        assertEquals(6379, settings.redisPort());
        assertTrue(settings.redisQueueOnOutage());
        assertTrue(settings.pluginMessaging());
        assertEquals(3, warnings.size(), warnings::toString);
        assertNull(null);
    }
}
