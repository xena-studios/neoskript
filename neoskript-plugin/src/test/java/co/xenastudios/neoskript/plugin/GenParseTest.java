package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parse-conformance for the generated property expressions and boolean conditions: the command body
 * is parsed at reload but never dispatched (their getters call Bukkit APIs MockBukkit doesn't
 * implement). If any pattern failed to parse the whole file is rejected and the join marker won't fire.
 */
class GenParseTest {

    private ServerMock server;
    private NeoSkriptPlugin plugin;

    @BeforeEach
    void setUp() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void generatedPatternsParse() throws IOException {
        Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("genparse.sk"), """
                on join:
                    send "JOINED" to player

                command /genparse:
                    trigger:
                        set {_x} to ai of player
                        set {_x} to gliding of player
                        set {_x} to head of player
                        set {_x} to lunar phase of player
                        set {_x} to vector length of player
                        set {_x} to resource pack response of player
                        set {_x} to environment of player
                        set {_x} to active item of player
                        set {_x} to ai of player
                        set {_x} to altitude of player
                        set {_x} to anvil text input of player
                        set {_x} to arrow knockback strength of player
                        set {_x} to arrow pierce level of player
                        set {_x} to attack cooldown of player
                        set {_x} to bed location of player
                        set {_x} to billboard of player
                        set {_x} to causing entity of player
                        set {_x} to client view distance of player
                        set {_x} to compass target of player
                        set {_x} to damage location of player
                        set {_x} to damage type of player
                        set {_x} to difficulty of player
                        set {_x} to difficulty of player
                        set {_x} to direct entity of player
                        set {_x} to enchantment cost of player
                        set {_x} to ender chest of player
                        set {_x} to enderchest of player
                        set {_x} to entity snapshot of player
                        set {_x} to environment of player
                        set {_x} to exhaustion of player
                        set {_x} to experience pickup cooldown of player
                        set {_x} to eye location of player
                        set {_x} to fall distance of player
                        set {_x} to fly mode of player
                        set {_x} to flight mode of player
                        set {_x} to food exhaustion of player
                        set {_x} to freeze time of player
                        set {_x} to gliding state of player
                        set {_x} to glowing of player
                        set {_x} to gravity of player
                        set {_x} to humidity of player
                        set {_x} to humidity of player
                        set {_x} to item display transform of player
                        set {_x} to language of player
                        set {_x} to last death location of player
                        set {_x} to level of player
                        set {_x} to level progress of player
                        set {_x} to line width of player
                        set {_x} to loot location of player
                        set {_x} to loot luck value of player
                        set {_x} to looted entity of player
                        set {_x} to max freeze time of player
                        set {_x} to max item use time of player
                        set {_x} to max minecart speed of player
                        set {_x} to moon phase of player
                        set {_x} to namespaced key of player
                        set {_x} to no damage time of player
                        set {_x} to pickup delay of player
                        set {_x} to player list name of player
                        set {_x} to portal cooldown of player
                        set {_x} to potion effect type category of player
                        set {_x} to protocol version of player
                        set {_x} to redstone power of player
                        set {_x} to resource pack response of player
                        set {_x} to saturation of player
                        set {_x} to sea level of player
                        set {_x} to source location of player
                        set {_x} to spawn of player
                        set {_x} to spectator target of player
                        set {_x} to squared length of player
                        set {_x} to squared length of player
                        set {_x} to teleport duration of player
                        set {_x} to temperature of player
                        set {_x} to temperature of player
                        set {_x} to text alignment of player
                        set {_x} to text of player
                        set {_x} to uuid of dropped item owner of player
                        set {_x} to uuid of dropped item thrower of player
                        set {_x} to vector length of player
                        set {_x} to view range of player
                        set {_x} to world border center of player
                        set {_x} to world border damage amount of player
                        set {_x} to world border damage buffer of player
                        set {_x} to world border warning distance of player
                        if player is blocking:
                            set {_y} to 1
                        if player is invisible:
                            set {_y} to 1
                        if player is op:
                            set {_y} to 1
                        if player is climbing:
                            set {_y} to 1
                        if player is flying:
                            set {_y} to 1
                        if player is frozen:
                            set {_y} to 1
                        if player is gliding:
                            set {_y} to 1
                        if player is jumping:
                            set {_y} to 1
                        if player is normalized:
                            set {_y} to 1
                        if player is on ground:
                            set {_y} to 1
                        if player is passable:
                            set {_y} to 1
                        if player is riptiding:
                            set {_y} to 1
                        if player is silent:
                            set {_y} to 1
                        if player is sleeping:
                            set {_y} to 1
                        if player is sneaking:
                            set {_y} to 1
                        if player is sprinting:
                            set {_y} to 1
                        if player is swimming:
                            set {_y} to 1
                        if player is ticking:
                            set {_y} to 1
                        if player is wet:
                            set {_y} to 1
                        if player can breed:
                            set {_y} to 1
                        if player can despawn when far away:
                            set {_y} to 1
                        if player can duplicate:
                            set {_y} to 1
                        if player can pick up items:
                            set {_y} to 1
                        if player has AI:
                            set {_y} to 1
                        if player has a resource pack loaded:
                            set {_y} to 1
                        if player is a slime chunk:
                            set {_y} to 1
                        if player is climbing:
                            set {_y} to 1
                        if player is dashing:
                            set {_y} to 1
                        if player is from a mob spawner:
                            set {_y} to 1
                        if player is instant:
                            set {_y} to 1
                        if player is jumping:
                            set {_y} to 1
                        if player is normalized:
                            set {_y} to 1
                        if player is on its back:
                            set {_y} to 1
                        if player is passable:
                            set {_y} to 1
                        if player is riptiding:
                            set {_y} to 1
                        if player is rolling:
                            set {_y} to 1
                        if player is scared:
                            set {_y} to 1
                        if player is sneezing:
                            set {_y} to 1
                        if player is ticking:
                            set {_y} to 1
                        if player is wet:
                            set {_y} to 1
                        if player scales damage with difficulty:
                            set {_y} to 1
                        if player will despawn naturally:
                            set {_y} to 1
                        if player's custom name is visible:
                            set {_y} to 1
                        if respawn anchors work in player:
                            set {_y} to 1
                        allow player to age
                        allow player to duplicate
                        allow player to pick up items
                        apply drop shadow to player
                        cancel usage of player's active item
                        clear the title of player
                        forbid player from picking up items
                        hide the custom name of player
                        lock age of player
                        make player a baby
                        make player an adult
                        make player breedable
                        make player despawn on chunk unload
                        make player duplicate
                        make player start shivering
                        make player start sprinting
                        make player stop shivering
                        make player stop sprinting
                        make player swing their main hand
                        make player swing their off hand
                        make player teleport randomly
                        make player visible through blocks
                        prevent player from being visible through blocks
                        prevent player from despawning
                        prevent player from duplicating
                        prevent player from naturally despawning
                        remove drop shadow from player
                        reset the title of player
                        save player
                        show the custom name of player
                        sterilize player
                        tame player
                        unleash player
                        untame player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock player = server.addPlayer();
        boolean joined = false;
        String m;
        while ((m = player.nextMessage()) != null) {
            if (m.contains("JOINED")) joined = true;
        }
        assertTrue(joined, "all generated patterns must parse (else file rejected, join never fires)");
    }
}
