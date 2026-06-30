package co.xenastudios.neoskript.plugin;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** All GenEvents2 (full registerEvent catalogue) names resolve and register. */
class GenEvents2Test {
    private ServerMock server; private NeoSkriptPlugin plugin;
    @BeforeEach void s(){ server=MockBukkit.mock(); plugin=MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void t(){ MockBukkit.unmock(); }
    @Test void resolve() throws IOException {
        Path d=plugin.getDataFolder().toPath().resolve("scripts"); Files.createDirectories(d);
        Files.writeString(d.resolve("ge2.sk"), """
                on join:
                    send "JOINED" to player
                on (eat|consume) ]:
                    set {_x} to 1
                on (ready arrow|arrow choose):
                    set {_x} to 1
                on ] tool change:
                    set {_x} to 1
                on anvil damage:
                    set {_x} to 1
                on anvil prepare:
                    set {_x} to 1
                on area effect:
                    set {_x} to 1
                on arm swing:
                    set {_x} to 1
                on armor change:
                    set {_x} to 1
                on attack attempt:
                    set {_x} to 1
                on bat toggle sleep:
                    set {_x} to 1
                on beacon change effect:
                    set {_x} to 1
                on beacon effect:
                    set {_x} to 1
                on bed enter:
                    set {_x} to 1
                on bed leave:
                    set {_x} to 1
                on bell resonate:
                    set {_x} to 1
                on block damaging:
                    set {_x} to 1
                on block drop ]:
                    set {_x} to 1
                on book sign:
                    set {_x} to 1
                on broadcast:
                    set {_x} to 1
                on bucket empty:
                    set {_x} to 1
                on bucket fill:
                    set {_x} to 1
                on burn ]:
                    set {_x} to 1
                on can build check:
                    set {_x} to 1
                on chunk generate:
                    set {_x} to 1
                on chunk load:
                    set {_x} to 1
                on chunk unload:
                    set {_x} to 1
                on combust:
                    set {_x} to 1
                on connect:
                    set {_x} to 1
                on craft ]:
                    set {_x} to 1
                on creeper power:
                    set {_x} to 1
                on damage:
                    set {_x} to 1
                on death:
                    set {_x} to 1
                on deep sleep:
                    set {_x} to 1
                on dispense ]:
                    set {_x} to 1
                on elytra boost:
                    set {_x} to 1
                on enchant:
                    set {_x} to 1
                on enchant prepare:
                    set {_x} to 1
                on enter chunk:
                    set {_x} to 1
                on entit(y|) transform:
                    set {_x} to 1
                on entity jump:
                    set {_x} to 1
                on explod(e|sion):
                    set {_x} to 1
                on explosion prime:
                    set {_x} to 1
                on fade ]:
                    set {_x} to 1
                on fertilize:
                    set {_x} to 1
                on firework explod(e|sion) red ]:
                    set {_x} to 1
                on flight toggle:
                    set {_x} to 1
                on flow:
                    set {_x} to 1
                on food level|bar) change:
                    set {_x} to 1
                on form ]:
                    set {_x} to 1
                on gamemode change:
                    set {_x} to 1
                on gliding state change gliding):
                    set {_x} to 1
                on harvest:
                    set {_x} to 1
                on heal:
                    set {_x} to 1
                on horse jump:
                    set {_x} to 1
                on ignite:
                    set {_x} to 1
                on inventory closing:
                    set {_x} to 1
                on inventory drag:
                    set {_x} to 1
                on inventory item move:
                    set {_x} to 1
                on inventory open:
                    set {_x} to 1
                on inventory pickup:
                    set {_x} to 1
                on inventory slot change:
                    set {_x} to 1
                on inventory-click ]:
                    set {_x} to 1
                on item damage:
                    set {_x} to 1
                on item despawn:
                    set {_x} to 1
                on item mend:
                    set {_x} to 1
                on item merge:
                    set {_x} to 1
                on item spawn ]:
                    set {_x} to 1
                on jump:
                    set {_x} to 1
                on kick:
                    set {_x} to 1
                on language change:
                    set {_x} to 1
                on leaves decay:
                    set {_x} to 1
                on level change:
                    set {_x} to 1
                on level progress change:
                    set {_x} to 1
                on lightning:
                    set {_x} to 1
                on login:
                    set {_x} to 1
                on on] plate:
                    set {_x} to 1
                on physics:
                    set {_x} to 1
                on pickup arrow:
                    set {_x} to 1
                on piglin barter):
                    set {_x} to 1
                on pigzap:
                    set {_x} to 1
                on piston extend:
                    set {_x} to 1
                on piston retract:
                    set {_x} to 1
                on plant grow ]:
                    set {_x} to 1
                on player experience cooldown change:
                    set {_x} to 1
                on player trade:
                    set {_x} to 1
                on portal create:
                    set {_x} to 1
                on portal enter:
                    set {_x} to 1
                on preparing craft ]:
                    set {_x} to 1
                on projectile collide:
                    set {_x} to 1
                on projectile hit:
                    set {_x} to 1
                on quit):
                    set {_x} to 1
                on redstone:
                    set {_x} to 1
                on resource pack response:
                    set {_x} to 1
                on respawn:
                    set {_x} to 1
                on resurrect:
                    set {_x} to 1
                on riptide ]:
                    set {_x} to 1
                on send ] command list:
                    set {_x} to 1
                on sheep grow wool:
                    set {_x} to 1
                on shoot:
                    set {_x} to 1
                on shoot bow:
                    set {_x} to 1
                on sign chang:
                    set {_x} to 1
                on slime split:
                    set {_x} to 1
                on spawn:
                    set {_x} to 1
                on spawn change:
                    set {_x} to 1
                on sponge absorb:
                    set {_x} to 1
                on spread:
                    set {_x} to 1
                on stonecutting ]:
                    set {_x} to 1
                on stop using item:
                    set {_x} to 1
                on swap item:
                    set {_x} to 1
                on tame:
                    set {_x} to 1
                on target:
                    set {_x} to 1
                on throw egg:
                    set {_x} to 1
                on toggle sneak:
                    set {_x} to 1
                on toggle sprint:
                    set {_x} to 1
                on toggle swim:
                    set {_x} to 1
                on tool break:
                    set {_x} to 1
                on vault display item:
                    set {_x} to 1
                on vehicle create:
                    set {_x} to 1
                on vehicle damage:
                    set {_x} to 1
                on vehicle destroy:
                    set {_x} to 1
                on vehicle enter:
                    set {_x} to 1
                on vehicle exit:
                    set {_x} to 1
                on vehicle move:
                    set {_x} to 1
                on villager career change:
                    set {_x} to 1
                on world changing:
                    set {_x} to 1
                on world init:
                    set {_x} to 1
                on world load:
                    set {_x} to 1
                on world save:
                    set {_x} to 1
                on world unload:
                    set {_x} to 1
                on worldborder center change:
                    set {_x} to 1
                on worldborder change:
                    set {_x} to 1
                on worldborder finish change:
                    set {_x} to 1
                on zombie break ] door:
                    set {_x} to 1
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p=server.addPlayer(); boolean j=false; String m;
        while((m=p.nextMessage())!=null) if(m.contains("JOINED")) j=true;
        assertTrue(j, "all GenEvents2 names must resolve");
    }
}
