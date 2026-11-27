/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.VariableDatabase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Telling other servers about writes to the network variables' database, over plugin messaging. */
class NetworkVariablesTest {

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final List<String> logged = Collections.synchronizedList(new ArrayList<>());

    @AfterEach
    void stop() {
        timer.shutdownNow();
    }

    /** A database as NeoSkript gives it to addons. */
    private static final class Database implements VariableDatabase {
        final String name;
        final String pattern;
        final boolean shared;
        final List<Runnable> listeners = new ArrayList<>();
        final AtomicInteger checks = new AtomicInteger();

        Database(String name, String pattern, boolean shared) {
            this.name = name;
            this.pattern = pattern;
            this.shared = shared;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String pattern() {
            return pattern;
        }

        @Override
        public boolean shared() {
            return shared;
        }

        @Override
        public boolean available() {
            return shared;
        }

        @Override
        public String state() {
            return "MySQL, connected";
        }

        @Override
        public String id() {
            return shared ? "0123456789abcdef" : null;
        }

        @Override
        public Runnable whenWritten(Runnable listener) {
            listeners.add(listener);
            return () -> listeners.remove(listener);
        }

        @Override
        public void checkForChanges() {
            checks.incrementAndGet();
        }

        void write() {
            List.copyOf(listeners).forEach(Runnable::run);
        }
    }

    /** One server: its plugin messaging, network and network variables, sending through {@code out}. */
    private final class Server {
        final List<NetworkMessageEvent> fired = Collections.synchronizedList(new ArrayList<>());
        final PluginMessaging messaging;
        final Network network;
        final NetworkVariables variables;

        Server(String name, List<byte[]> out, Map<String, Object> config) {
            Map<String, Object> values = new java.util.HashMap<>(config);
            values.put("server-name", name);
            Settings settings = Settings.read(values::get, new ArrayList<>());
            Logger logger = Logger.getAnonymousLogger();
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    logged.add(record.getLevel() + " " + record.getMessage());
                }

                @Override
                public void flush() {}

                @Override
                public void close() {}
            });
            Envelope envelope = new Envelope(settings.secret(), settings.maxMessageBytes());
            network = new Network(settings, logger, Runnable::run, fired::add, envelope, name + "-instance");
            messaging = new PluginMessaging(settings, payload -> out.add(payload), timer, logger,
                    network::fromForward);
            network.use(messaging, null);
            variables = new NetworkVariables(settings, logger, network, timer);
            network.notices(variables::heard);
        }

        /** What the proxy hands this server for a Forward another server sent. */
        void receive(byte[] forward) throws IOException {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(forward));
            assertEquals("Forward", in.readUTF());
            assertEquals("ALL", in.readUTF());
            String subchannel = in.readUTF();
            byte[] data = in.readNBytes(in.readUnsignedShort());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream message = new DataOutputStream(bytes);
            message.writeUTF(subchannel);
            message.writeShort(data.length);
            message.write(data);
            messaging.received(bytes.toByteArray());
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long until = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > until) {
                throw new AssertionError("timed out");
            }
            Thread.sleep(10);
        }
    }

    @Test
    void aWriteOnOneServerMakesTheOthersReadAtOnceAndScriptsNeverSeeIt() throws Exception {
        List<byte[]> fromLobby = Collections.synchronizedList(new ArrayList<>());
        Server lobby = new Server("lobby", fromLobby, Map.of("secret", "s3cret"));
        Server survival = new Server("survival", new ArrayList<>(), Map.of("secret", "s3cret"));
        Database lobbyDb = new Database("network", "network::.*", true);
        Database survivalDb = new Database("network", "network::.*", true);
        lobby.variables.start(List.of(lobbyDb));
        survival.variables.start(List.of(survivalDb));
        assertTrue(lobby.variables.available());
        // Many writes close together are a few notices, not one each.
        for (int i = 0; i < 50; i++) {
            lobbyDb.write();
        }
        waitFor(() -> !fromLobby.isEmpty());
        Thread.sleep(300);
        assertTrue(fromLobby.size() <= 3, fromLobby.size() + " notices");
        for (byte[] forward : List.copyOf(fromLobby)) {
            survival.receive(forward);
        }
        assertTrue(survivalDb.checks.get() >= 1);
        assertEquals(List.of(), survival.fired, "a notice never reaches scripts");
        // A server's own notice, back from the proxy, isn't read.
        lobby.receive(fromLobby.getFirst());
        assertEquals(0, lobbyDb.checks.get());
    }

    @Test
    void aNoticeForAnotherDatabaseIsIgnored() throws Exception {
        Server survival = new Server("survival", new ArrayList<>(), Map.of("secret", "s3cret"));
        Database db = new Database("network", "network::.*", true);
        survival.variables.start(List.of(db));
        survival.variables.heard("ffffffffffffffff");
        assertEquals(0, db.checks.get());
        survival.variables.heard("0123456789ABCDEF");
        assertEquals(1, db.checks.get());
    }

    @Test
    void withoutASharedDatabaseTheConsoleSaysWhatToAdd() {
        Server lobby = new Server("lobby", new ArrayList<>(), Map.of("secret", "s3cret"));
        lobby.variables.start(List.of(new Database("default", ".*", false)));
        assertFalse(lobby.variables.shared());
        assertTrue(logged.stream().anyMatch(line -> line.contains("no database named 'network'")), logged.toString());
        Server survival = new Server("survival", new ArrayList<>(), Map.of("secret", "s3cret"));
        Database plain = new Database("network", "network::.*", false);
        survival.variables.start(List.of(plain));
        assertFalse(survival.variables.shared());
        assertTrue(logged.stream().anyMatch(line -> line.contains("isn't a MySQL database with monitor-changes")));
        assertTrue(plain.listeners.isEmpty());
        Server other = new Server("other", new ArrayList<>(), Map.of("secret", "s3cret"));
        other.variables.start(List.of(new Database("network", "synced_.*", true)));
        assertTrue(logged.stream().anyMatch(line -> line.contains("doesn't take {network::...} variables")));
    }

    @Test
    void noticesAreNeverQueuedWhileNobodyCanCarryThem() throws Exception {
        List<byte[]> out = Collections.synchronizedList(new ArrayList<>());
        Server lobby = new Server("lobby", out, Map.of("secret", "s3cret"));
        Database db = new Database("network", "network::.*", true);
        lobby.variables.start(List.of(db));
        // Nobody online: the carrier refuses, and nothing waits for a player.
        PluginMessaging empty = new PluginMessaging(Settings.read(Map.<String, Object>of("secret", "s3cret")::get,
                new ArrayList<>()), payload -> false, timer, Logger.getAnonymousLogger(), data -> {});
        assertFalse(empty.sendNow(new byte[] {1}));
        assertEquals(0, empty.queued());
        lobby.variables.stop();
        db.write();
        Thread.sleep(200);
        assertTrue(out.isEmpty(), "stopped: nothing is sent");
        assertTrue(db.listeners.isEmpty());
    }

    @Test
    void theNetworkPatternIsChecked() {
        assertTrue(NetworkVariables.takesNetworkNames("network::.*"));
        assertTrue(NetworkVariables.takesNetworkNames(".*"));
        assertFalse(NetworkVariables.takesNetworkNames("synced_.*"));
        assertFalse(NetworkVariables.takesNetworkNames("(broken"));
    }
}
