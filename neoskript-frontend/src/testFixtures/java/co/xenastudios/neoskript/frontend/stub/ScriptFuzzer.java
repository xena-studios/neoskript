/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.frontend.stub;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Scripts for fuzz tests: the repo's scripts (the real-world corpus, the harness's scripts, the differential's
 * checks and the AI skill's examples, plus Skript's own tests when {@code fetchSkriptTests} has run), mutated at
 * random: lines dropped, repeated, swapped, cut short, re-indented or spliced together, tokens swapped between
 * scripts, stray characters, very long lines and deep nesting.
 *
 * <p>Seeded, so a failure names a seed that reproduces it. {@code -Pneoskript.fuzz.iterations=N} runs longer than CI
 * does, and {@code -Pneoskript.fuzz.seed=S} picks the first seed.
 */
public final class ScriptFuzzer {

    /** A generated script and the seed that makes it again. */
    public record Case(long seed, String text) {}

    private static final String[] FRAGMENTS = {"(", ")", "[", "]", "{", "}", "{_", "::", "::*", "%", "\"", "'", ":",
        ",", " and ", " or ", "\t", "    ", "-", "+", "*", "/", "^", "<", ">", "=", "#", "\\", "¦", "@", "$", "!",
        " if ", " else", " loop ", " while ", " wait ", " return ", " function ", " command /", " on ", " every ",
        " trigger:", " set ", " to ", " of ", " the ", " player", " arg-1", " loop-value", " 1", " -1", " 1.5",
        " 99999999999999999999", " 1e308", " NaN", " infinity", " 2 seconds", " true", "\u0000", "\u00e9", "\u4e2d",
        "\ud83d\ude00", "\ud800", "\r", "\r\n", "\u200b", "<none>", "%{_x}%", "{@x}", "options:", "variables:"};

    private final List<String> files;
    private final List<String> lines;
    private final List<String> tokens;

    private ScriptFuzzer(List<String> files) {
        this.files = files;
        this.lines = new ArrayList<>();
        this.tokens = new ArrayList<>();
        for (String file : files) {
            for (String line : file.split("\n", -1)) {
                if (!line.isBlank()) {
                    lines.add(line);
                    for (String token : line.strip().split("\\s+")) {
                        tokens.add(token);
                    }
                }
            }
        }
    }

    /** A fuzzer over the repo's scripts. */
    public static ScriptFuzzer fromRepository() {
        Path root = repositoryRoot();
        List<String> files = new ArrayList<>();
        for (String dir : List.of("test-harness/corpus", "test-harness/scripts", "plugins/skript",
                "test-harness/build/corpus/skript")) {
            Path path = root.resolve(dir);
            if (!Files.isDirectory(path)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(path)) {
                for (Path file : paths.filter(p -> p.toString().endsWith(".sk")).sorted().toList()) {
                    files.add(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("No scripts found under " + root);
        }
        return new ScriptFuzzer(files);
    }

    /** The original scripts, unchanged. */
    public List<String> originals() {
        return List.copyOf(files);
    }

    /** How many cases to run: {@code neoskript.fuzz.iterations}, or {@code ciDefault}. */
    public static int iterations(int ciDefault) {
        String value = System.getProperty("neoskript.fuzz.iterations");
        return value == null || value.isBlank() ? ciDefault : Integer.parseInt(value.strip());
    }

    /** The first seed: {@code neoskript.fuzz.seed}, or a fixed one, so CI runs the same cases every time. */
    public static long firstSeed() {
        String value = System.getProperty("neoskript.fuzz.seed");
        return value == null || value.isBlank() ? 0x5EED_2026L : Long.parseLong(value.strip());
    }

    /** The case for {@code seed}. */
    public Case generate(long seed) {
        Random random = new Random(seed);
        String text = switch (random.nextInt(12)) {
            case 0 -> deepNesting(random);
            case 1 -> randomLines(random);
            default -> mutate(files.get(random.nextInt(files.size())), random);
        };
        return new Case(seed, text);
    }

    private String mutate(String file, Random random) {
        List<String> out = new ArrayList<>(List.of(file.split("\n", -1)));
        int mutations = 1 + random.nextInt(8);
        for (int i = 0; i < mutations; i++) {
            if (out.isEmpty()) {
                out.add(randomLine(random));
            }
            int at = random.nextInt(out.size());
            String line = out.get(at);
            switch (random.nextInt(14)) {
                case 0 -> out.remove(at);
                case 1 -> out.add(at, line);
                case 2 -> {
                    int other = random.nextInt(out.size());
                    out.set(at, out.get(other));
                    out.set(other, line);
                }
                case 3 -> out.set(at, line.substring(0, random.nextInt(line.length() + 1)));
                case 4 -> out.set(at, (random.nextBoolean() ? "\t" : "") + (random.nextBoolean() ? line.stripLeading() : "  " + line));
                case 5 -> out.add(at, lines.get(random.nextInt(lines.size())));
                case 6 -> out.set(at, insert(line, FRAGMENTS[random.nextInt(FRAGMENTS.length)], random));
                case 7 -> out.set(at, insert(line, tokens.get(random.nextInt(tokens.size())), random));
                case 8 -> out.set(at, line + " " + lines.get(random.nextInt(lines.size())).strip());
                case 9 -> out.set(at, line.replaceFirst("\\S+", tokens.get(random.nextInt(tokens.size()))
                        .replace("\\", "\\\\").replace("$", "\\$")));
                // Very long, but not without end: repeated again in the same case, a line would grow 61-fold each time.
                case 10 -> out.set(at, line + " " + line.strip().repeat(1 + random.nextInt(
                        Math.max(1, Math.min(60, 20_000 / Math.max(1, line.length()))))));
                case 11 -> out.set(at, insert(line, deepNesting(random).strip(), random));
                case 12 -> out.set(at, new StringBuilder(line).reverse().toString());
                default -> out.set(at, insert(line, Character.toString(random.nextInt(0x20, 0x2FFF)), random));
            }
        }
        return String.join(random.nextInt(20) == 0 ? "\r\n" : "\n", out);
    }

    private static String insert(String line, String fragment, Random random) {
        int at = random.nextInt(line.length() + 1);
        return line.substring(0, at) + fragment + line.substring(at);
    }

    private String randomLines(Random random) {
        StringBuilder out = new StringBuilder();
        int count = 1 + random.nextInt(30);
        for (int i = 0; i < count; i++) {
            out.append("\t".repeat(random.nextInt(4))).append(randomLine(random)).append('\n');
        }
        return out.toString();
    }

    private String randomLine(Random random) {
        StringBuilder line = new StringBuilder();
        int parts = 1 + random.nextInt(12);
        for (int i = 0; i < parts; i++) {
            line.append(switch (random.nextInt(3)) {
                case 0 -> FRAGMENTS[random.nextInt(FRAGMENTS.length)];
                case 1 -> tokens.get(random.nextInt(tokens.size()));
                default -> " ";
            });
        }
        return line.toString();
    }

    /** A trigger whose one line nests something very deeply: brackets, list indices, text, functions or sections. */
    private static String deepNesting(Random random) {
        int depth = 50 + random.nextInt(random.nextBoolean() ? 400 : 4000);
        String line = switch (random.nextInt(7)) {
            case 0 -> "set {_x} to " + "(".repeat(depth) + "1" + ")".repeat(depth);
            case 1 -> "set {_x} to {_" + "a::{_".repeat(depth) + "b" + "}".repeat(depth + 1);
            case 2 -> "send \"" + "%\"".repeat(depth) + "\"";
            case 3 -> "set {_x} to " + "f(".repeat(depth) + "1" + ")".repeat(depth);
            case 4 -> "set {_x} to 1" + " + 1".repeat(depth * 4);
            case 5 -> "set {_x} to " + "not ".repeat(depth) + "true";
            default -> {
                StringBuilder nested = new StringBuilder();
                for (int i = 0; i < Math.min(depth, 300); i++) {
                    nested.append("\t".repeat(i + 1)).append("if 1 is 1:\n");
                }
                nested.append("\t".repeat(Math.min(depth, 300) + 1)).append("set {_x} to 1");
                yield nested.toString().substring(1);
            }
        };
        return "function f(n: number) :: number:\n\treturn {_n}\non load:\n\t" + line + "\n";
    }

    /**
     * Runs {@code task} on a fresh thread with the default stack size, as server threads have, and fails if it takes longer than
     * {@code seconds} or throws anything. The thread is abandoned if it hangs.
     */
    public static void within(Case fuzzCase, long seconds, ThrowingRunnable task) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(null, runnable, "fuzz-" + fuzzCase.seed(), 0);
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<?> future = executor.submit(() -> {
                task.run();
                return null;
            });
            future.get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("Seed " + fuzzCase.seed() + " took over " + seconds + " s:\n" + preview(fuzzCase.text()));
        } catch (ExecutionException e) {
            throw new AssertionError("Seed " + fuzzCase.seed() + " threw " + e.getCause() + ":\n" + preview(fuzzCase.text()),
                    e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } finally {
            executor.shutdownNow();
        }
    }

    /** The start of a script, for failure messages. */
    public static String preview(String text) {
        return text.length() <= 2000 ? text : text.substring(0, 2000) + "\n... (" + text.length() + " characters)";
    }

    /** Code that may throw anything. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static Path repositoryRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("settings.gradle.kts"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Not inside the repository");
        }
        return dir;
    }
}
