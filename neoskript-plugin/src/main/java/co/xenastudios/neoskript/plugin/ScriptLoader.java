package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.core.parser.ParseException;
import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.core.runtime.Trigger;
import co.xenastudios.neoskript.platform.event.BukkitEventBridge;
import org.bukkit.event.Event;
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
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Discovers {@code .sk} files, parses them into {@link Trigger}s, and registers one Bukkit listener
 * per event type to run them.
 *
 * <p>Phase 1 loads every script at enable. Per-script and incremental reload arrive in Phase 3.
 */
public final class ScriptLoader {

    private final Plugin plugin;
    private final ScriptParser parser;
    private final Map<String, Object> globals;

    public ScriptLoader(Plugin plugin, ScriptParser parser, Map<String, Object> globals) {
        this.plugin = plugin;
        this.parser = parser;
        this.globals = globals;
    }

    /** The outcome of a load: how many scripts and triggers were loaded, and how many failed. */
    public record Result(int scripts, int triggers, int failed) {
    }

    /**
     * Loads and registers all scripts found in {@code scriptsDir}, creating the directory (with a
     * sample script) if it does not exist.
     *
     * @param scriptsDir the scripts directory
     * @return a summary of what was loaded
     */
    public Result loadAll(Path scriptsDir) {
        ensureScriptsDirectory(scriptsDir);

        List<Trigger> triggers = new ArrayList<>();
        int scripts = 0;
        int failed = 0;

        try (Stream<Path> files = Files.walk(scriptsDir)) {
            List<Path> scriptFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sk"))
                    .toList();
            for (Path file : scriptFiles) {
                scripts++;
                try {
                    String source = Files.readString(file, StandardCharsets.UTF_8);
                    triggers.addAll(parser.parse(source));
                } catch (ParseException e) {
                    failed++;
                    plugin.getLogger().warning("Failed to parse " + scriptsDir.relativize(file) + ": " + e.getMessage());
                } catch (IOException e) {
                    failed++;
                    plugin.getLogger().log(Level.WARNING, "Failed to read " + file, e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan scripts directory: " + scriptsDir, e);
        }

        registerTriggers(triggers);
        return new Result(scripts, triggers.size(), failed);
    }

    @SuppressWarnings("unchecked")
    private void registerTriggers(List<Trigger> triggers) {
        // Group triggers by event class so each event type gets a single listener.
        Map<Class<?>, List<Trigger>> byEvent = new LinkedHashMap<>();
        for (Trigger trigger : triggers) {
            byEvent.computeIfAbsent(trigger.eventClass(), key -> new ArrayList<>()).add(trigger);
        }

        for (Map.Entry<Class<?>, List<Trigger>> entry : byEvent.entrySet()) {
            Class<? extends Event> eventClass = (Class<? extends Event>) entry.getKey();
            List<Trigger> forEvent = entry.getValue();
            BukkitEventBridge.register(plugin, eventClass, event -> {
                for (Trigger trigger : forEvent) {
                    // Each trigger run gets its own local-variable scope.
                    trigger.execute(new SimpleTriggerContext(event, globals));
                }
            });
        }
    }

    private void ensureScriptsDirectory(Path scriptsDir) {
        if (Files.exists(scriptsDir)) {
            return;
        }
        try {
            Files.createDirectories(scriptsDir);
            Files.writeString(scriptsDir.resolve("example.sk"), """
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
            plugin.getLogger().log(Level.WARNING, "Could not create scripts directory: " + scriptsDir, e);
        }
    }
}
