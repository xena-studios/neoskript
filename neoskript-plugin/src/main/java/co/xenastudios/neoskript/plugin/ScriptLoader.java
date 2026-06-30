package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.core.parser.ParseException;
import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.runtime.ExecutionEngine;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.HotPathTracker;
import co.xenastudios.neoskript.core.runtime.InterpretedEngine;
import co.xenastudios.neoskript.core.runtime.Profiler;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.core.runtime.Trigger;
import co.xenastudios.neoskript.platform.event.BukkitEventBridge;
import co.xenastudios.neoskript.platform.scheduler.NeoScheduler;
import co.xenastudios.neoskript.platform.scheduler.TaskHandle;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Discovers {@code .sk} files, parses them (in parallel) into {@link Trigger}s, and wires them up:
 * event triggers to Bukkit listeners, periodic triggers to the scheduler, and load triggers run
 * once. Supports a full {@link #reload()} that detaches the previous listeners, periodic tasks, and
 * functions first.
 */
public final class ScriptLoader {

    /** Executions before a trigger is logged as hot (a candidate for the future bytecode engine). */
    private static final long HOT_THRESHOLD = 10_000L;

    private final Plugin plugin;
    private final ScriptParser parser;
    private final FunctionRegistry functions;
    private final Map<String, Object> globals;
    private final NeoScheduler scheduler;
    private final Profiler profiler;
    private final ExecutionEngine engine = new InterpretedEngine();
    private final HotPathTracker hotPaths = new HotPathTracker(HOT_THRESHOLD);

    private final List<Listener> listeners = new ArrayList<>();
    private final List<TaskHandle> periodicTasks = new ArrayList<>();

    private Path scriptsDir;

    public ScriptLoader(Plugin plugin, ScriptParser parser, FunctionRegistry functions,
                        Map<String, Object> globals, NeoScheduler scheduler, Profiler profiler) {
        this.plugin = plugin;
        this.parser = parser;
        this.functions = functions;
        this.globals = globals;
        this.scheduler = scheduler;
        this.profiler = profiler;
    }

    /** The outcome of a load. */
    public record Result(int scripts, int triggers, int failed) {
    }

    /**
     * Loads and registers all scripts in {@code dir}, creating it (with a sample script) if absent.
     *
     * @param dir the scripts directory
     * @return a summary of what was loaded
     */
    public Result loadAll(Path dir) {
        this.scriptsDir = dir;
        ensureScriptsDirectory(dir);

        List<Path> scriptFiles = discover(dir);
        AtomicInteger failed = new AtomicInteger();

        // Scripts parse independently; the syntax/event registries are read-only here and the
        // function registry is concurrent, so parsing fans out across files safely.
        List<Trigger> triggers = scriptFiles.parallelStream()
                .flatMap(file -> parseFile(file, dir, failed).stream())
                .toList();

        registerTriggers(triggers);
        return new Result(scriptFiles.size(), triggers.size(), failed.get());
    }

    /**
     * Detaches all previously loaded triggers and functions, then reloads from the same directory.
     *
     * @return a summary of the reload
     */
    public Result reload() {
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        periodicTasks.forEach(TaskHandle::cancel);
        periodicTasks.clear();
        functions.clear();
        hotPaths.reset();
        return loadAll(scriptsDir);
    }

    private void registerTriggers(List<Trigger> triggers) {
        Map<Class<?>, List<Trigger>> eventTriggers = new LinkedHashMap<>();
        for (Trigger trigger : triggers) {
            switch (trigger.kind()) {
                case EVENT -> eventTriggers.computeIfAbsent(trigger.eventClass(), k -> new ArrayList<>()).add(trigger);
                case PERIODIC -> schedulePeriodic(trigger);
                case LOAD -> run(trigger, null);
            }
        }
        registerEventListeners(eventTriggers);
    }

    @SuppressWarnings("unchecked")
    private void registerEventListeners(Map<Class<?>, List<Trigger>> eventTriggers) {
        for (Map.Entry<Class<?>, List<Trigger>> entry : eventTriggers.entrySet()) {
            Class<? extends Event> eventClass = (Class<? extends Event>) entry.getKey();
            List<Trigger> forEvent = entry.getValue();
            listeners.add(BukkitEventBridge.register(plugin, eventClass, event -> {
                for (Trigger trigger : forEvent) {
                    run(trigger, event);
                }
            }));
        }
    }

    private void schedulePeriodic(Trigger trigger) {
        long interval = trigger.intervalTicks();
        periodicTasks.add(scheduler.runRepeating(() -> run(trigger, null), interval, interval));
    }

    /** Runs a trigger with a fresh local scope, applying profiling and hot-path tracking. */
    private void run(Trigger trigger, Event event) {
        TriggerContext ctx = new SimpleTriggerContext(event, globals);
        if (profiler.isEnabled()) {
            long start = System.nanoTime();
            engine.run(trigger, ctx);
            profiler.record(trigger.eventName(), System.nanoTime() - start);
        } else {
            engine.run(trigger, ctx);
        }
        if (hotPaths.recordAndCheckHot(trigger)) {
            plugin.getLogger().fine("Trigger '" + trigger.eventName() + "' is hot (eligible for compilation).");
        }
    }

    private List<Trigger> parseFile(Path file, Path dir, AtomicInteger failed) {
        try {
            return parser.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (ParseException e) {
            failed.incrementAndGet();
            plugin.getLogger().warning("Failed to parse " + dir.relativize(file) + ": " + e.getMessage());
        } catch (IOException e) {
            failed.incrementAndGet();
            plugin.getLogger().log(Level.WARNING, "Failed to read " + file, e);
        }
        return List.of();
    }

    private static List<Path> discover(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sk"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan scripts directory: " + dir, e);
        }
    }

    private void ensureScriptsDirectory(Path dir) {
        if (Files.exists(dir)) {
            return;
        }
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("example.sk"), """
                    # NeoSkript example script
                    on load:
                        broadcast "NeoSkript example loaded!"

                    on join:
                        add 1 to {joins}
                        broadcast "%player% joined the server!"
                        send "Welcome to the server!" to player

                    every 5 minutes:
                        broadcast "The server has had %{joins}% joins so far."

                    on quit:
                        broadcast "%player% left the server."
                    """, StandardCharsets.UTF_8);
            plugin.getLogger().info("Created scripts directory with an example script.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not create scripts directory: " + dir, e);
        }
    }
}
