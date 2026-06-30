package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.core.parser.ParseException;
import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.core.runtime.Trigger;
import co.xenastudios.neoskript.platform.event.BukkitEventBridge;
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
 * Discovers {@code .sk} files, parses them (in parallel) into {@link Trigger}s, and registers one
 * Bukkit listener per event type to run them. Supports a full {@link #reload()} that unregisters the
 * previous listeners and functions before reloading; per-file incremental reload is a later
 * refinement.
 */
public final class ScriptLoader {

    private final Plugin plugin;
    private final ScriptParser parser;
    private final FunctionRegistry functions;
    private final Map<String, Object> globals;
    private final List<Listener> registeredListeners = new ArrayList<>();

    private Path scriptsDir;

    public ScriptLoader(Plugin plugin, ScriptParser parser, FunctionRegistry functions, Map<String, Object> globals) {
        this.plugin = plugin;
        this.parser = parser;
        this.functions = functions;
        this.globals = globals;
    }

    /** The outcome of a load: how many scripts and triggers were loaded, and how many failed. */
    public record Result(int scripts, int triggers, int failed) {
    }

    /**
     * Loads and registers all scripts found in {@code dir}, creating the directory (with a sample
     * script) if it does not exist.
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
     * Unregisters all previously loaded triggers and functions, then reloads from the same directory.
     *
     * @return a summary of the reload
     */
    public Result reload() {
        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
        functions.clear();
        return loadAll(scriptsDir);
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

    @SuppressWarnings("unchecked")
    private void registerTriggers(List<Trigger> triggers) {
        Map<Class<?>, List<Trigger>> byEvent = new LinkedHashMap<>();
        for (Trigger trigger : triggers) {
            byEvent.computeIfAbsent(trigger.eventClass(), key -> new ArrayList<>()).add(trigger);
        }
        for (Map.Entry<Class<?>, List<Trigger>> entry : byEvent.entrySet()) {
            Class<? extends Event> eventClass = (Class<? extends Event>) entry.getKey();
            List<Trigger> forEvent = entry.getValue();
            Listener listener = BukkitEventBridge.register(plugin, eventClass, event -> {
                for (Trigger trigger : forEvent) {
                    trigger.execute(new SimpleTriggerContext(event, globals));
                }
            });
            registeredListeners.add(listener);
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
                    on join:
                        add 1 to {joins}
                        broadcast "%player% joined the server!"
                        send "Welcome to the server!" to player
                        if {joins} is greater than 100:
                            broadcast "That's over 100 joins!"

                    on quit:
                        broadcast "%player% left the server."
                    """, StandardCharsets.UTF_8);
            plugin.getLogger().info("Created scripts directory with an example script.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not create scripts directory: " + dir, e);
        }
    }
}
