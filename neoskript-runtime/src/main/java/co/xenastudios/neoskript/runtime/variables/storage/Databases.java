/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Several databases, as Skript's {@code databases:} section: each variable is saved in the first database, from the
 * top, whose {@code pattern} matches its whole name, and in no other. Variables whose names start with {@code -} are
 * never saved, and a variable no pattern matches isn't either (a file drops it; an SQL database keeps the row it has,
 * as in Skript).
 *
 * <p>Loading reads every database, from the top. A variable found in two is kept from the lower one, with Skript's
 * warning. One found in a database that isn't the one its name now belongs to (after a pattern changed, say) is
 * moved: the database it belongs to saves it, and only once that has gone through does the other let go of it.
 *
 * <p>A database that fails to load keeps its pattern: the variables that match it aren't saved anywhere else, so its
 * data is never shadowed by another database, and nothing is written to it. An SQL database that can't be reached is
 * tried again in the background and loads when it answers; a file that can't be read needs fixing and a restart. A
 * database that already has a variable that now belongs to one that failed keeps saving it.
 */
public final class Databases implements VariableBackend {

    /** Skript's limit on load conflicts it names one by one. */
    private static final int MAX_CONFLICT_WARNINGS = 50;

    /** Opens one database's storage, given how it fits in. */
    @FunctionalInterface
    public interface Opener {
        VariableBackend open(Routing routing) throws IOException;
    }

    /**
     * One database from config.yml.
     *
     * @param name its name in config.yml
     * @param type its type as written, for the console
     * @param pattern Skript's pattern: a Java regular expression the whole name must match
     */
    public record Definition(String name, String type, String pattern, Opener opener) {}

    /** One database, opened or not. */
    public static final class Database {
        final String name;
        final String type;
        /** {@code null} for {@code .*} and {@code .+}, which match every name, as in Skript. */
        final Pattern pattern;
        final String patternText;
        VariableBackend backend;
        /** Where it loads before the merge, when there are several. */
        VariableStore loadInto;
        volatile String failure;
        /** Whether it has saved since loading, so variables that moved to it can be let go of elsewhere. */
        volatile boolean saved;
        /** Variables it loaded that belong to another database, until that one has saved them. */
        final Set<String> movedOut = ConcurrentHashMap.newKeySet();
        volatile boolean releasing;

        Database(String name, String type, Pattern pattern, String patternText) {
            this.name = name;
            this.type = type;
            this.pattern = pattern;
            this.patternText = patternText;
        }

        public String name() {
            return name;
        }

        public String type() {
            return type;
        }

        public String pattern() {
            return patternText;
        }

        /** Its storage, or {@code null} if it couldn't be opened. */
        public VariableBackend backend() {
            return backend;
        }

        /** Why it couldn't be opened or loaded, or {@code null}. */
        public String failure() {
            return failure;
        }

        /** Where it stands, for {@code /sk info}: its type, and whether it's in use, down or failed, and why. */
        public String state() {
            if (failure != null) {
                return type + ", not used: " + failure;
            }
            if (backend instanceof SqlVariableStorage sql) {
                SqlVariableStorage.Status status = sql.status();
                String monitor = sql.settings().monitor() ? ", watching for changes" : "";
                String since = java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(
                        status.since().atZone(java.time.ZoneId.systemDefault()));
                return switch (status.state()) {
                    case CONNECTED -> type + ", connected, " + status.variables() + " variables" + monitor;
                    case DOWN -> type + ", down since " + since + " (" + status.problem() + "), " + status.waiting()
                            + " changes waiting";
                    case CONNECTING -> type + ", not reached yet (" + status.problem() + "): its variables aren't loaded";
                    case CLOSED -> type + ", closed";
                };
            }
            if (backend == null || !backend.loaded()) {
                return type + ", not loaded";
            }
            return type + ", " + backend.describe();
        }

        boolean matches(String name) {
            return pattern == null || pattern.matcher(name).matches();
        }

        /** Whether it holds its variables: opened, loaded and not failed. */
        boolean available() {
            return failure == null && backend != null && backend.loaded();
        }
    }

    private final VariableStore store;
    private final VariableCodecs codecs;
    private final Console console;
    private final List<Database> databases = new ArrayList<>();
    private final Map<String, Integer> owners = new ConcurrentHashMap<>();
    private final List<String> notes = new ArrayList<>();
    private volatile boolean loaded;

    private Databases(VariableStore store, VariableCodecs codecs, Console console) {
        this.store = store;
        this.codecs = codecs;
        this.console = console;
    }

    /**
     * Opens each database, in order. One that can't be opened (a bad pattern, a file that can't be read) is kept as
     * failed, still owning its pattern, and the console hears why.
     */
    public static Databases open(VariableStore store, VariableCodecs codecs, List<Definition> definitions,
            Console console) {
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("no databases");
        }
        Databases all = new Databases(store, codecs, console);
        boolean several = definitions.size() > 1;
        for (Definition definition : definitions) {
            Pattern pattern = null;
            String failure = null;
            String text = definition.pattern();
            if (!text.equals(".*") && !text.equals(".+")) {
                try {
                    pattern = Pattern.compile(text);
                } catch (PatternSyntaxException e) {
                    // Skript's message. Nothing matches it, so no variable is saved anywhere by mistake... but it can't
                    // own anything either: every variable goes on to the next database.
                    failure = "Invalid pattern '" + text + "': " + e.getDescription();
                    pattern = Pattern.compile("(?!)");
                }
            }
            Database database = new Database(definition.name(), definition.type(), pattern, text);
            database.failure = failure;
            int index = all.databases.size();
            all.databases.add(database);
            if (failure != null) {
                continue;
            }
            database.loadInto = several ? new VariableStore() : null;
            Routing routing = new Routing(name -> all.owns(index, name), name -> all.movedAway(index, name),
                    name -> all.loadedElsewhere(index, name), database.loadInto);
            try {
                database.backend = definition.opener().open(routing);
            } catch (IOException | RuntimeException e) {
                database.failure = e.getMessage() == null ? e.toString() : e.getMessage();
            }
        }
        return all;
    }

    public List<Database> databases() {
        return List.copyOf(databases);
    }

    // ---- Routing -------------------------------------------------------------------------------------------------

    /** The index of the database {@code name} belongs to, or -1 if none. */
    int owner(String name) {
        if (databases.size() == 1) {
            return databases.getFirst().matches(name) ? 0 : -1;
        }
        Integer cached = owners.get(name);
        if (cached != null) {
            return cached;
        }
        int found = -1;
        for (int i = 0; i < databases.size(); i++) {
            if (databases.get(i).matches(name)) {
                found = i;
                break;
            }
        }
        if (owners.size() > 200_000) {
            owners.clear();
        }
        owners.put(name, found);
        return found;
    }

    private boolean owns(int index, String variable) {
        String name = variable.toLowerCase(Locale.ROOT);
        int owner = owner(name);
        if (owner == index) {
            return true;
        }
        // Loaded here but belongs elsewhere: kept until the database it belongs to has it.
        return owner >= 0 && databases.get(index).movedOut.contains(name);
    }

    private boolean movedAway(int index, String variable) {
        String name = variable.toLowerCase(Locale.ROOT);
        int owner = owner(name);
        return owner >= 0 && owner != index && hasSaved(databases.get(owner));
    }

    /** Whether a database other than {@code index} loaded {@code variable} at startup and holds it still. */
    private boolean loadedElsewhere(int index, String variable) {
        String name = variable.toLowerCase(Locale.ROOT);
        for (int i = 0; i < databases.size(); i++) {
            if (i != index && databases.get(i).movedOut.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a database has saved since it loaded, so what belongs to it is safe there. */
    private static boolean hasSaved(Database database) {
        if (!database.available()) {
            return false;
        }
        return database.backend instanceof SqlVariableStorage sql ? sql.written() : database.saved;
    }

    // ---- VariableBackend -----------------------------------------------------------------------------------------

    /** The first database's file, for messages; see {@link #describe}. */
    @Override
    public Path file() {
        for (Database database : databases) {
            if (database.backend != null && database.backend.file() != null) {
                return database.backend.file();
            }
        }
        return null;
    }

    @Override
    public String describe() {
        if (databases.size() == 1 && databases.getFirst().backend != null) {
            return databases.getFirst().backend.describe();
        }
        return "the " + databases.size() + " databases";
    }

    /**
     * Loads every database that opened. One that fails is kept as failed, owning its pattern, and the console hears
     * why. Throws only if none loaded and none can load later, so scripts would run with nothing saved.
     */
    @Override
    public synchronized VariableStorage.Result load() throws IOException {
        int variables = 0;
        int raw = 0;
        int leftovers = 0;
        Path backup = null;
        List<VariableFile.Problem> skipped = new ArrayList<>();
        boolean any = false;
        for (Database database : databases) {
            if (database.failure != null) {
                console.error("Database '" + database.name + "' isn't used: " + database.failure + ". The variables its "
                        + "pattern matches aren't saved until it's fixed and the server restarts.");
                continue;
            }
            try {
                VariableStorage.Result result = database.backend.load();
                variables += result.variables();
                raw += result.raw();
                leftovers += result.leftovers();
                skipped.addAll(result.skipped());
                if (backup == null) {
                    backup = result.backup();
                }
                any = true;
            } catch (IOException | RuntimeException | LinkageError e) {
                database.failure = "couldn't load " + database.backend.describe() + ": "
                        + (e.getMessage() == null ? e.toString() : e.getMessage());
                console.error("Database '" + database.name + "' isn't used: " + database.failure + ". The variables "
                        + "its pattern matches aren't saved until it's fixed and the server restarts, and it's left as "
                        + "it was.");
                try {
                    database.backend.close();
                } catch (IOException closing) {
                    e.addSuppressed(closing);
                }
            }
            for (String line : database.backend.notes()) {
                note(databases.size() == 1 ? line : "Database '" + database.name + "': " + line);
            }
        }
        if (!any) {
            throw new IOException("none of the databases could be loaded");
        }
        if (databases.size() > 1) {
            variables = merge();
        }
        loaded = true;
        return new VariableStorage.Result(variables, raw, 0, List.copyOf(skipped), backup, leftovers);
    }

    /**
     * Puts what each database loaded into the store, the lower database winning a name both have, as in Skript, and
     * notes which variables each holds that belong elsewhere.
     */
    private int merge() {
        Map<String, Object[]> merged = new LinkedHashMap<>();
        int conflicts = 0;
        for (int i = 0; i < databases.size(); i++) {
            Database database = databases.get(i);
            if (database.backend == null || database.failure != null) {
                continue;
            }
            VariableStore loadedInto = database.loadInto;
            database.loadInto = null;
            if (loadedInto == null) {
                continue;
            }
            int index = i;
            int[] count = {conflicts};
            loadedInto.forEach((name, value) -> {
                String key = name.toLowerCase(Locale.ROOT);
                Object[] before = merged.remove(key);
                if (before != null && (int) before[2] != index) {
                    count[0]++;
                    if (count[0] <= MAX_CONFLICT_WARNINGS) {
                        // Skript's message.
                        console.warn("The variable {" + name + "} was loaded twice from different databases ("
                                + databases.get((int) before[2]).name + " and " + database.name + "), only the one from "
                                + database.name + " will be kept.");
                    } else if (count[0] == MAX_CONFLICT_WARNINGS + 1) {
                        console.warn("[!] More than " + MAX_CONFLICT_WARNINGS + " variables were loaded more than "
                                + "once from different databases, no more warnings will be printed.");
                    }
                    databases.get((int) before[2]).movedOut.add(key);
                }
                merged.put(key, new Object[] {name, value, index});
                int owner = owner(key);
                if (owner != index) {
                    database.movedOut.add(key);
                }
            });
            conflicts = count[0];
        }
        if (conflicts > MAX_CONFLICT_WARNINGS) {
            console.warn("A total of " + conflicts + " variables were loaded more than once from different databases");
        }
        List<String> names = new ArrayList<>(merged.size());
        List<Object> values = new ArrayList<>(merged.size());
        int unowned = 0;
        for (Object[] entry : merged.values()) {
            names.add((String) entry[0]);
            values.add(entry[1]);
            if (owner(((String) entry[0]).toLowerCase(Locale.ROOT)) < 0) {
                unowned++;
            }
        }
        store.load(names, values);
        if (unowned > 0) {
            // Skript's message.
            console.warn(unowned + " variables were possibly discarded due to not belonging to any database (SQL "
                    + "databases keep such variables and will continue to generate this warning, while CSV discards "
                    + "them).");
        }
        return merged.size();
    }

    @Override
    public boolean loaded() {
        return loaded;
    }

    @Override
    public boolean changed() {
        if (!loaded) {
            return false;
        }
        for (Database database : databases) {
            if (database.available() && (database.releasing || database.backend.changed())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Saves every database that's loaded. One that fails doesn't stop the others; at the end this throws, naming
     * those that failed. Variables that moved to a database are let go of by the others once it has saved them.
     */
    @Override
    public synchronized VariableStorage.Result save() throws IOException {
        if (!loaded) {
            throw new IllegalStateException("The variables weren't loaded, so they aren't saved");
        }
        List<String> failed = new ArrayList<>();
        int[] totals = new int[3];
        List<String> kept = new ArrayList<>();
        List<String> notSaved = new ArrayList<>();
        saveAll(failed, totals, kept, notSaved);
        if (release()) {
            saveAll(failed, totals, kept, notSaved);
        }
        if (!failed.isEmpty()) {
            throw new IOException(String.join("; ", failed));
        }
        return new VariableStorage.Result(totals[0], totals[1], totals[2], List.of(), null, 0, List.copyOf(kept),
                List.copyOf(notSaved));
    }

    private void saveAll(List<String> failed, int[] totals, List<String> kept, List<String> notSaved) {
        for (Database database : databases) {
            if (!database.available()) {
                continue;
            }
            if (database.backend instanceof SqlVariableStorage) {
                // Writes on its own thread within a second or so, and once more when it closes.
                continue;
            }
            if (!database.releasing && !database.backend.changed()) {
                continue;
            }
            try {
                VariableStorage.Result result = database.backend.save();
                totals[0] += result.variables();
                totals[1] += result.raw();
                totals[2] += result.unsaved();
                kept.addAll(result.keptPrevious());
                notSaved.addAll(result.notSaved());
                database.saved = true;
                database.releasing = false;
            } catch (IOException | RuntimeException | LinkageError e) {
                failed.add("database '" + database.name + "' (" + database.backend.describe() + "): "
                        + (e.getMessage() == null ? e.toString() : e.getMessage()));
            }
            for (String line : database.backend.notes()) {
                note(databases.size() == 1 ? line : "Database '" + database.name + "': " + line);
            }
        }
    }

    /**
     * Lets each database drop the variables it loaded that belong to another, once that one has saved them. Returns
     * whether any did, so they save again.
     */
    private boolean release() {
        boolean any = false;
        for (Database database : databases) {
            if (database.movedOut.isEmpty() || !database.available()) {
                continue;
            }
            boolean released = database.movedOut.removeIf(name -> {
                int owner = owner(name);
                if (owner < 0) {
                    return false;
                }
                return hasSaved(databases.get(owner));
            });
            if (released && !(database.backend instanceof SqlVariableStorage)) {
                database.releasing = true;
                any = true;
            }
        }
        return any;
    }

    /**
     * Replaces each value whose saving needs code {@code loader} loaded with its saved bytes, using what the database
     * it belongs to last had for one whose codec fails now. See {@link VariableStorage#detach}.
     */
    @Override
    public VariableStorage.Detached detach(ClassLoader loader) {
        // Each database's lookup is made once, so a file is read once however many variables need it.
        Map<Integer, Function<String, VariableFile.Entry>> lookups = new HashMap<>();
        return detach(store, codecs, loader, name -> {
            int owner = owner(name.toLowerCase(Locale.ROOT));
            for (int i = 0; i < databases.size(); i++) {
                Database database = databases.get(i);
                if (database.backend == null || (owner >= 0 && i != owner)) {
                    continue;
                }
                VariableFile.Entry before = lookups.computeIfAbsent(i, n -> database.backend.previousLookup())
                        .apply(name);
                if (before != null) {
                    return before;
                }
            }
            return null;
        });
    }

    /** {@link VariableStorage#detach}, with what each variable last had from {@code previous}. */
    static VariableStorage.Detached detach(VariableStore store, VariableCodecs codecs, ClassLoader loader,
            Function<String, VariableFile.Entry> previous) {
        Map<String, Object> found = new LinkedHashMap<>();
        store.forEach((name, value) -> {
            if (codecs.usesCodeOf(value, loader)) {
                found.put(name, value);
            }
        });
        List<String> kept = new ArrayList<>();
        List<String> notSaved = new ArrayList<>();
        for (Map.Entry<String, Object> entry : found.entrySet()) {
            VariableCodecs.Encoded encoded;
            try {
                encoded = codecs.encode(entry.getValue());
            } catch (RuntimeException | LinkageError e) {
                encoded = null;
            }
            if (encoded == null) {
                VariableFile.Entry before = previous.apply(entry.getKey());
                if (before == null) {
                    notSaved.add(entry.getKey());
                    continue;
                }
                encoded = new VariableCodecs.Encoded(before.type(), before.bytes());
            }
            store.set(entry.getKey(), new RawValue(encoded.type(), encoded.bytes()));
            kept.add(entry.getKey());
        }
        codecs.forget(loader);
        return new VariableStorage.Detached(List.copyOf(kept), List.copyOf(notSaved));
    }

    @Override
    public VariableFile.Entry previous(String name) {
        int owner = owner(name.toLowerCase(Locale.ROOT));
        if (owner < 0 || databases.get(owner).backend == null) {
            return null;
        }
        return databases.get(owner).backend.previous(name);
    }

    @Override
    public synchronized List<String> notes() {
        List<String> drained = List.copyOf(notes);
        notes.clear();
        return drained;
    }

    private synchronized void note(String line) {
        notes.add(line);
    }

    @Override
    public void cancelBackup() {
        for (Database database : databases) {
            if (database.backend != null) {
                database.backend.cancelBackup();
            }
        }
    }

    /**
     * Closes every database at once, the SQL ones writing what's left first, and waits for them all together, at most
     * a little longer than one SQL database may take. One still going then is named in the console; it keeps what it
     * couldn't write for the next start itself.
     */
    @Override
    public void close() throws IOException {
        List<IOException> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Thread> closing = new ArrayList<>();
        Map<Thread, Database> which = new HashMap<>();
        for (Database database : databases) {
            if (database.backend == null) {
                continue;
            }
            Thread thread = new Thread(() -> {
                try {
                    database.backend.close();
                } catch (IOException e) {
                    failures.add(e);
                }
            }, "NeoSkript closing database '" + database.name + "'");
            thread.setDaemon(true);
            thread.start();
            closing.add(thread);
            which.put(thread, database);
        }
        long until = System.currentTimeMillis() + SqlVariableStorage.closeTimeoutMillis + 5_000;
        for (Thread thread : closing) {
            try {
                thread.join(Math.max(1, until - System.currentTimeMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (thread.isAlive()) {
                console.error("Database '" + which.get(thread).name + "' was still closing when the server stopped; "
                        + "what it couldn't write is kept for the next start if it got that far.");
            }
        }
        if (!failures.isEmpty()) {
            IOException first = failures.getFirst();
            failures.stream().skip(1).forEach(first::addSuppressed);
            throw first;
        }
    }

    /** For tests: the databases by name. */
    Map<String, Database> byName() {
        Map<String, Database> map = new HashMap<>();
        for (Database database : databases) {
            map.put(database.name, database);
        }
        return map;
    }
}
