package co.xenastudios.neoskript.testkit;

import co.xenastudios.neoskript.core.lexer.Lexer;
import co.xenastudios.neoskript.core.lexer.Token;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Skeleton of the script conformance harness. It loads {@code .sk} fixtures and runs them through
 * the pipeline so that, as stages come online, we can assert NeoSkript reproduces Skript's
 * behaviour exactly.
 *
 * <p>Phase 0 exercises only the lexer. As the parser, resolver, and runtime arrive, this harness
 * grows to load a real corpus and compare observed effects against expected fixtures.
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
        try (InputStream in = ConformanceHarness.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Script resource not found: " + resource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    all.addAll(lexer.tokenizeLine(line, lineNumber++));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read script resource: " + resource, e);
        }
        return all;
    }
}
