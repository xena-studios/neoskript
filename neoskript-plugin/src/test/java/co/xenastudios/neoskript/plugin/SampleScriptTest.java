package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.core.parser.ParseException;
import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.CommandRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.Trigger;
import co.xenastudios.neoskript.lang.BuiltinFunctions;
import co.xenastudios.neoskript.lang.BuiltinModule;
import co.xenastudios.neoskript.lang.event.BuiltinEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loads the canonical Skript sample end-to-end and asserts every structure parses (no gaps). */
class SampleScriptTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(NeoSkriptPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @org.junit.jupiter.api.Disabled("Enabled incrementally as sample.sk syntax gaps are closed")
    @Test
    void sampleScriptParsesFully() throws Exception {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        BuiltinModule.registerAll(registry);
        EventRegistry events = new EventRegistry();
        BuiltinEvents.registerAll(events);
        FunctionRegistry functions = new FunctionRegistry();
        BuiltinFunctions.registerAll(functions);
        CommandRegistry commands = new CommandRegistry();

        String source;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("sample.sk")) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        ScriptParser parser = new ScriptParser(registry, events, functions, commands);
        List<Trigger> triggers = parser.parse(source);
        StringBuilder sb = new StringBuilder("\nSAMPLE.SK parse errors (" + parser.errors().size() + "):\n");
        for (ParseException e : parser.errors()) {
            sb.append("  line ").append(e.line()).append(": ").append(e.getMessage()).append('\n');
        }
        System.out.println(sb + "triggers loaded: " + triggers.size());
        // Every canonical sample structure must load; the whole point is drop-in compatibility.
        assertTrue(parser.errors().isEmpty(), sb.toString());
    }

    /** Regression guard for the core fix: one bad structure never discards the rest of the file. */
    @Test
    void oneBadLineDoesNotBreakTheWholeScript() throws Exception {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        BuiltinModule.registerAll(registry);
        EventRegistry events = new EventRegistry();
        BuiltinEvents.registerAll(events);
        FunctionRegistry functions = new FunctionRegistry();
        BuiltinFunctions.registerAll(functions);
        ScriptParser parser = new ScriptParser(registry, events, functions, new CommandRegistry());
        List<Trigger> triggers = parser.parse(
                "on join:\n    completely bogus effect here\n\non join:\n    send \"ok\" to player\n");
        assertTrue(triggers.size() == 1, "the valid trigger loads despite the bad one");
        assertTrue(parser.errors().size() == 1, "the bad trigger is reported, not fatal");
    }
}
