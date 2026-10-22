/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A running test server. Its console output is captured line by line and written to a log file. */
public final class ServerProcess implements AutoCloseable {

    /** Paper and Folia print this once the server has finished starting. */
    public static final Pattern DONE = Pattern.compile("Done \\([0-9.,]+s\\)! For help, type");

    /** A plugin failed to load or enable. Waiting any longer is pointless, so awaits fail right away. */
    static final Pattern PLUGIN_FAILED =
            Pattern.compile("(Error occurred while enabling|Could not load '?plugins/)\\s*(\\S+)");

    private final Process process;
    private final Path logFile;
    private final Writer console;
    private final List<String> lines = new ArrayList<>();
    private final Thread reader;

    private ServerProcess(Process process, Path logFile, boolean echo) throws IOException {
        this.process = process;
        this.logFile = logFile;
        this.console = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        Writer log = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8);
        this.reader = Thread.ofPlatform().name("server-output").daemon().start(() -> pump(log, echo));
    }

    static ServerProcess start(List<String> command, Path workDir, boolean echo) throws IOException {
        Process process = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start();
        return new ServerProcess(process, workDir.resolve("harness-console.log"), echo);
    }

    private void pump(Writer log, boolean echo) {
        try (log;
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                log.write(line);
                log.write('\n');
                log.flush();
                if (echo) {
                    System.out.println("  | " + line);
                }
                synchronized (lines) {
                    lines.add(line);
                    lines.notifyAll();
                }
            }
        } catch (IOException e) {
            // The process went away; waiters notice through isAlive().
        } finally {
            synchronized (lines) {
                lines.notifyAll();
            }
        }
    }

    /** The number of lines seen so far. Pass it to {@link #await(Pattern, int, Duration)} to only match newer lines. */
    public int mark() {
        synchronized (lines) {
            return lines.size();
        }
    }

    public Matcher await(Pattern pattern, Duration timeout) throws InterruptedException {
        return await(pattern, 0, timeout);
    }

    /**
     * Waits for a console line at or after {@code fromLine} that contains a match for {@code pattern}.
     *
     * @throws HarnessFailure if the timeout passes or the server exits first
     */
    public Matcher await(Pattern pattern, int fromLine, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        int next = fromLine;
        synchronized (lines) {
            while (true) {
                for (; next < lines.size(); next++) {
                    String line = lines.get(next);
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        return matcher;
                    }
                    if (PLUGIN_FAILED.matcher(line).find()) {
                        throw new HarnessFailure("A plugin failed while waiting for /" + pattern + "/: " + line
                                + ". See " + logFile);
                    }
                }
                if (!reader.isAlive()) {
                    throw new HarnessFailure("Server exited (code " + exitCodeOrUnknown()
                            + ") before printing a line matching /" + pattern + "/. See " + logFile);
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new HarnessFailure("Timed out after " + timeout.toSeconds()
                            + "s waiting for a line matching /" + pattern + "/. See " + logFile);
                }
                TimeUnit.NANOSECONDS.timedWait(lines, remaining);
            }
        }
    }

    /** Returns the console lines seen so far that contain a match for {@code pattern}. */
    public List<String> linesMatching(Pattern pattern) {
        synchronized (lines) {
            return lines.stream().filter(line -> pattern.matcher(line).find()).toList();
        }
    }

    /** Runs a console command, without the leading slash. */
    public void command(String command) throws IOException {
        console.write(command);
        console.write('\n');
        console.flush();
    }

    /** Waits for the server to exit by itself. */
    public int awaitExit(Duration timeout) throws InterruptedException {
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new HarnessFailure("Server didn't exit within " + timeout.toMinutes() + " minutes. See " + logFile);
        }
        reader.join(Duration.ofSeconds(10));
        return process.exitValue();
    }

    /** Sends {@code stop} and waits for a clean exit, killing the server if it hangs. */
    public int stop() throws IOException, InterruptedException {
        if (process.isAlive()) {
            try {
                command("stop");
            } catch (IOException e) {
                // stdin is already closed, so the server is on its way down.
            }
            if (!process.waitFor(90, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor();
                throw new HarnessFailure("Server didn't stop within 90 seconds and was killed. See " + logFile);
            }
        }
        reader.join(Duration.ofSeconds(10));
        return process.exitValue();
    }

    public Path logFile() {
        return logFile;
    }

    private String exitCodeOrUnknown() {
        return process.isAlive() ? "unknown" : String.valueOf(process.exitValue());
    }

    @Override
    public void close() {
        if (process.isAlive()) {
            try {
                process.destroyForcibly().waitFor(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
