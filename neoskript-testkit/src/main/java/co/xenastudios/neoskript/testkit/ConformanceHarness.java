package co.xenastudios.neoskript.testkit;

import co.xenastudios.neoskript.core.lexer.Lexer;
import co.xenastudios.neoskript.core.lexer.Token;
import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.core.runtime.Trigger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Script conformance harness: loads {@code .sk} fixtures and runs them through the full pipeline
 * (parse → execute), so behaviour can be asserted as the language grows.
 *
 * <p>The caller supplies a {@link DefaultSyntaxRegistry} and {@link EventRegistry} with the syntax
 * under test. Bukkit-touching built-ins (e.g. {@code broadcast}) need a server, but anything
 * server-independent (variables, control flow, functions, arithmetic) runs here directly.
 */
public final class ConformanceHarness {

    private final Lexer lexer = new Lexer();

    /**
     * Tokenizes every line of a classpath script resource.
     *
     * @param resource the classpath resource path (e.g. {@code "scripts/hello.sk"})
     * @return the tokens for all lines, in order
     */
    public List<Token> tokenize(String resource) {
        List<Token> all = new ArrayList<>();
        int lineNumber = 1;
        for (String line : readResource(resource).split("\n", -1)) {
            all.addAll(lexer.tokenizeLine(line, lineNumber++));
        }
        return all;
    }

    /**
     * Parses and executes a script source against the given syntax/event registries, returning the
     * resulting execution context (so tests can assert on variables).
     *
     * @param registry the syntax registry (caller registers the effects/conditions under test)
     * @param events   the event registry
     * @param source   the script source
     * @return the context after running every trigger in order
     */
    public SimpleTriggerContext run(DefaultSyntaxRegistry registry, EventRegistry events, String source) {
        ScriptParser parser = new ScriptParser(registry, events, new FunctionRegistry());
        List<Trigger> triggers = parser.parse(source);
        SimpleTriggerContext ctx = new SimpleTriggerContext(null, new HashMap<>());
        for (Trigger trigger : triggers) {
            trigger.execute(ctx);
        }
        return ctx;
    }

    /**
     * Reads a classpath resource as a UTF-8 string.
     *
     * @param resource the classpath resource path
     * @return its contents
     */
    public String readResource(String resource) {
        try (InputStream in = ConformanceHarness.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Script resource not found: " + resource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read script resource: " + resource, e);
        }
    }
}
