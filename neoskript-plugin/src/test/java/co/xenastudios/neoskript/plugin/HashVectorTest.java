package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour tests for hashing and vector construction (hash, vector from yaw/pitch, from direction). */
class HashVectorTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void hashAndVectors() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("hv.sk"), """
                command /hv:
                    trigger:
                        set {_s} to "hello"
                        send "MD5:%{_s} hashed with MD5%" to player
                        send "SHA:%{_s} hashed with SHA-256%" to player
                        set {_yp} to vector from yaw 0 and pitch 0
                        send "YPZ:%vector z of {_yp}%" to player
                        set {_yp2} to vector from yaw 90 and pitch 0
                        send "YP2X:%vector x of {_yp2}%" to player
                        set {_pool::1} to 7
                        set {_pool::2} to 7
                        set {_pick} to any of {_pool::*}
                        send "ANY:%{_pick}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "hv");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("MD5:5d41402abc4b2a76b9719d911017c592"),
                "MD5 of \"hello\" (lowercase hex); got " + seen);
        assertTrue(seen.contains("SHA:2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"),
                "SHA-256 of \"hello\" (lowercase hex)");
        assertTrue(seen.contains("YPZ:1"), "vector from yaw 0 pitch 0 points south (+z)");
        assertTrue(seen.contains("YP2X:-1"), "vector from yaw 90 pitch 0 points west (-x)");
        assertTrue(seen.contains("ANY:7"), "'any of' yields one element of the list");
    }
}
