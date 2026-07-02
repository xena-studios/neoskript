package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.core.parser.ParseException;
import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.runtime.CommandDefinition;
import co.xenastudios.neoskript.core.runtime.CommandRegistry;
import co.xenastudios.neoskript.core.runtime.DelayScheduler;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.HotPathTracker;
import co.xenastudios.neoskript.core.runtime.Profiler;
import co.xenastudios.neoskript.core.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.core.runtime.Trigger;
import co.xenastudios.neoskript.core.runtime.Triggers;
import co.xenastudios.neoskript.platform.event.BukkitEventBridge;
import co.xenastudios.neoskript.platform.scheduler.NeoScheduler;
import co.xenastudios.neoskript.platform.scheduler.TaskHandle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
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
    private final CommandRegistry commands;
    private final Map<String, Object> globals;
    private final NeoScheduler scheduler;
    private final Profiler profiler;
    private final DelayScheduler delays;
    private final HotPathTracker hotPaths = new HotPathTracker(HOT_THRESHOLD);

    private final List<Listener> listeners = new ArrayList<>();
    private final List<TaskHandle> periodicTasks = new ArrayList<>();
    private final List<Command> registeredCommands = new ArrayList<>();

    private Path scriptsDir;

    public ScriptLoader(Plugin plugin, ScriptParser parser, FunctionRegistry functions, CommandRegistry commands,
                        Map<String, Object> globals, NeoScheduler scheduler, Profiler profiler) {
        this.plugin = plugin;
        this.parser = parser;
        this.functions = functions;
        this.commands = commands;
        this.globals = globals;
        this.scheduler = scheduler;
        this.profiler = profiler;
        this.delays = scheduler::runGlobalLater;
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
        co.xenastudios.neoskript.core.runtime.LoadedScripts.set(
                scriptFiles.stream().map(file -> dir.relativize(file).toString()).toList());
        AtomicInteger failed = new AtomicInteger();

        // Parse sequentially: the shared ScriptParser accumulates per-file parse errors in a single
        // mutable list, so fanning out across files would race on (and misattribute) those errors.
        // Parsing is a one-time load/reload step, so the sequential cost is negligible.
        List<Trigger> triggers = scriptFiles.stream()
                .flatMap(file -> parseFile(file, dir, failed).stream())
                .toList();

        registerTriggers(triggers);
        registerCommands();
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
        CommandMap map = plugin.getServer().getCommandMap();
        registeredCommands.forEach(command -> command.unregister(map));
        registeredCommands.clear();
        functions.clear();
        commands.clear();
        co.xenastudios.neoskript.core.alias.AliasRegistry.clear();
        hotPaths.reset();
        return loadAll(scriptsDir);
    }

    private void registerCommands() {
        CommandMap map = plugin.getServer().getCommandMap();
        java.util.Set<String> commandNames = new java.util.HashSet<>();
        for (CommandDefinition definition : commands.commands()) {
            commandNames.add(definition.name());
            commandNames.addAll(definition.aliases());
        }
        co.xenastudios.neoskript.core.runtime.LoadedCommands.set(commandNames);
        for (CommandDefinition definition : commands.commands()) {
            Command command = new Command(
                    definition.name(),
                    definition.description() == null ? "" : definition.description(),
                    definition.usage() == null ? "/" + definition.name() : definition.usage(),
                    definition.aliases()) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    if (definition.permission() != null && !definition.permission().isBlank()
                            && !sender.hasPermission(definition.permission())) {
                        sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
                        return true;
                    }
                    SimpleTriggerContext ctx = new SimpleTriggerContext(null, globals);
                    ctx.setLocal("command-sender", sender);
                    for (int i = 0; i < args.length; i++) {
                        ctx.setLocal("arg-" + (i + 1), args[i]);
                    }
                    ctx.setLocal("arg-count", (double) args.length);
                    try {
                        definition.run(ctx, delays);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING, "Error in command /" + definition.name(), e);
                    }
                    return true;
                }
            };
            map.register("neoskript", command);
            registeredCommands.add(command);
        }
    }

    private void registerTriggers(List<Trigger> triggers) {
        for (Trigger trigger : triggers) {
            switch (trigger.kind()) {
                case PERIODIC -> schedulePeriodic(trigger);
                case TIMED -> scheduleTimed(trigger);
                case LOAD -> run(trigger, null);
                case EVENT -> { /* registered together below */ }
            }
        }
        registerEventListeners(Triggers.groupEventTriggers(triggers));
    }

    @SuppressWarnings("unchecked")
    private void registerEventListeners(Map<Class<?>, List<Trigger>> eventTriggers) {
        for (Map.Entry<Class<?>, List<Trigger>> entry : eventTriggers.entrySet()) {
            Class<? extends Event> eventClass = (Class<? extends Event>) entry.getKey();
            List<Trigger> forEvent = entry.getValue();
            listeners.add(BukkitEventBridge.register(plugin, eventClass, event -> {
                for (Trigger trigger : forEvent) {
                    // A filtered event (e.g. `on break of diamond ore`) only runs when its predicate
                    // accepts this occurrence; unfiltered triggers always run.
                    if (trigger.filter() == null || trigger.filter().test(event)) {
                        run(trigger, event);
                    }
                }
            }));
        }
    }

    private void schedulePeriodic(Trigger trigger) {
        long interval = trigger.intervalTicks();
        periodicTasks.add(scheduler.runRepeating(() -> run(trigger, null), interval, interval));
    }

    /**
     * Schedules a time-conditional trigger: for world time, checks every tick and fires once each time
     * a target world crosses the target tick; for real time, checks about once a second and fires once
     * per day at the target minute.
     */
    private void scheduleTimed(Trigger trigger) {
        if (trigger.realTime()) {
            long targetHour = trigger.intervalTicks() / 3600;
            long targetMinute = (trigger.intervalTicks() % 3600) / 60;
            long[] lastFiredDay = {Long.MIN_VALUE};
            periodicTasks.add(scheduler.runRepeating(() -> {
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                long day = now.toLocalDate().toEpochDay();
                if (now.getHour() == targetHour && now.getMinute() == targetMinute
                        && lastFiredDay[0] != day) {
                    lastFiredDay[0] = day;
                    run(trigger, null);
                }
            }, 20L, 20L));
        } else {
            // Fire when a world crosses the target tick (robust to time advancing several ticks at
            // once, e.g. under lag), tracking each world's previous time between checks.
            java.util.Map<java.util.UUID, Long> previous = new java.util.HashMap<>();
            long target = trigger.intervalTicks();
            periodicTasks.add(scheduler.runRepeating(() -> {
                for (org.bukkit.World world : timedWorlds(trigger)) {
                    long current = world.getTime();
                    Long prior = previous.put(world.getUID(), current);
                    if (prior != null && prior != current) {
                        long span = Math.floorMod(current - prior, 24000L);
                        long toTarget = Math.floorMod(target - prior, 24000L);
                        if (toTarget > 0 && toTarget <= span) {
                            run(trigger, null);
                        }
                    }
                }
            }, 1L, 1L));
        }
    }

    /** The worlds a timed trigger applies to: the named ones, or all worlds when none are named. */
    private java.util.List<org.bukkit.World> timedWorlds(Trigger trigger) {
        if (trigger.worldNames().isEmpty()) {
            return new ArrayList<>(plugin.getServer().getWorlds());
        }
        java.util.List<org.bukkit.World> worlds = new ArrayList<>();
        for (String name : trigger.worldNames()) {
            org.bukkit.World world = plugin.getServer().getWorld(name);
            if (world != null) {
                worlds.add(world);
            }
        }
        return worlds;
    }

    /** Runs a trigger with a fresh local scope, applying profiling and hot-path tracking. */
    private void run(Trigger trigger, Event event) {
        TriggerContext ctx = new SimpleTriggerContext(event, globals);
        try {
            if (profiler.isEnabled()) {
                long start = System.nanoTime();
                trigger.execute(ctx, delays);
                profiler.record(trigger.eventName(), System.nanoTime() - start);
            } else {
                trigger.execute(ctx, delays);
            }
        } catch (RuntimeException e) {
            // One misbehaving trigger shouldn't break the event for other triggers or plugins.
            plugin.getLogger().log(Level.WARNING, "Error while running '" + trigger.eventName() + "'", e);
        }
        if (hotPaths.recordAndCheckHot(trigger)) {
            plugin.getLogger().fine("Trigger '" + trigger.eventName() + "' is hot (eligible for compilation).");
        }
    }

    private List<Trigger> parseFile(Path file, Path dir, AtomicInteger failed) {
        try {
            List<Trigger> triggers = parser.parse(Files.readString(file, StandardCharsets.UTF_8));
            // A parse error in one structure disables only that structure; log each and keep loading
            // the rest of the file, exactly as Skript does.
            for (ParseException e : parser.errors()) {
                failed.incrementAndGet();
                plugin.getLogger().warning("Failed to parse " + dir.relativize(file) + ": " + e.getMessage());
            }
            return triggers;
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
