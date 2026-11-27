/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.ScriptEvent;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ScriptLoader;
import co.xenastudios.neoskript.frontend.source.SourceFile;
import co.xenastudios.neoskript.runtime.SimpleTriggerContext;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Server events: event values, filters, types' default values, changing expressions, and properties. */
class EventsTest {

    static final class Person {
        String name;
        final boolean shouting;

        Person(String name, boolean shouting) {
            this.name = name;
            this.shouting = shouting;
        }
    }

    static class Greeting {
        final Person person;

        Greeting(Person person) {
            this.person = person;
        }
    }

    static final class Farewell extends Greeting {
        Farewell(Person person) {
            super(person);
        }
    }

    /** {@code name of %persons%}, which can be set. */
    static final class Name implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return Arrays.stream(arguments[0] instanceof Object[] a ? a : new Object[] {arguments[0]})
                    .map(p -> ((Person) p).name).toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {String.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            for (Object person : (Object[]) arguments[0]) {
                ((Person) person).name = (String) delta[0];
            }
        }
    }

    private final List<String> said = new ArrayList<>();
    private Library library;

    @BeforeEach
    void library() {
        library = new Library();
        library.type("string", "text", String.class);
        library.type("person", "person", Person.class);
        library.type("number", "number", Number.class);
        library.event("ScriptLoad", ScriptEvent.LOAD, "[script] load");
        library.event("Greet", List.of(Greeting.class),
                info -> (context, args) -> args[0] == null || ((Greeting) context.event()).person.name.equals(args[0]),
                "greet[ing] [of %string%]");
        library.event("Farewell", List.of(Farewell.class), null, "farewell");
        library.eventValue(Greeting.class, "person", greeting -> greeting.person);
        library.eventValue(Farewell.class, "string", farewell -> "bye");
        library.eventDefault("person");
        library.effect("Say", info -> (context, args) -> said.add(Arrays.toString((Object[]) args[0])),
                "say %objects%");
        library.effect("Wave", info -> (context, args) -> said.add("wave at "
                + (args[0] == null ? "nobody" : ((Person) args[0]).name)), "wave [at %person%]");
        library.expression("Name", "string", info -> new Name(), Patterns.property("name", "persons"));
        library.propertyCondition("Shouting", "shouting", "persons", Person.class, person -> person.shouting);
    }

    private CompiledScripts compile(String text) {
        List<ParsedScript> parsed = new ScriptLoader(library.registry()).load(List.of(new SourceFile("test.sk", text)));
        assertEquals(List.of(), parsed.getFirst().diagnostics());
        return new ScriptCompiler(library, new VariableStore()).compile(parsed);
    }

    /** Compiles the script, then fires {@code event} at every trigger that runs for its class. */
    private void fire(String script, Object event) {
        CompiledScripts compiled = compile(script);
        assertEquals(List.of(), compiled.diagnostics());
        for (CompiledScripts.CompiledTrigger trigger : compiled.triggers()) {
            if (trigger.eventClasses().stream().anyMatch(c -> c.isInstance(event))) {
                trigger.trigger().execute(new SimpleTriggerContext(event));
            }
        }
    }

    private String error(String script) {
        CompiledScripts compiled = compile(script);
        assertEquals(1, compiled.diagnostics().size(), compiled.diagnostics().toString());
        return compiled.diagnostics().getFirst().message();
    }

    @Test
    void eventValuesByTypeNameAndEventPrefix() {
        fire("on greet:\n\tsay name of person\n\tsay event-person's name\n", new Greeting(new Person("Alice", false)));
        assertEquals(List.of("[Alice]", "[Alice]"), said);
    }

    @Test
    void theMostSpecificEventClassWins() {
        fire("on farewell:\n\tsay event-text\n\tsay name of person\n", new Farewell(new Person("Bob", false)));
        assertEquals(List.of("[bye]", "[Bob]"), said);
    }

    @Test
    void filtersStopTriggersForOtherEvents() {
        String script = "on greeting of \"Bob\":\n\tsay \"bob\"\non greet:\n\tsay \"anyone\"\n";
        fire(script, new Greeting(new Person("Alice", false)));
        fire(script, new Greeting(new Person("Bob", false)));
        assertEquals(List.of("[anyone]", "[bob]", "[anyone]"), said);
    }

    @Test
    void unusedOptionalPlaceholdersDefaultToTheEventValue() {
        fire("on greet:\n\twave\n\twave at person\n", new Greeting(new Person("Alice", false)));
        assertEquals(List.of("wave at Alice", "wave at Alice"), said);
    }

    @Test
    void outsideEventsThereIsNoDefault() {
        // As Skript's default expressions, which fail where the event has no such value.
        assertTrue(error("on load:\n\twave\n").startsWith("There's no person in an on script load/unload event"));
        assertTrue(error("function f():\n\twave\n").startsWith("There's no person in a function event"));
    }

    @Test
    void changesExpressionsThatAcceptIt() {
        Person alice = new Person("Alice", false);
        fire("on greet:\n\tset name of person to \"Carol\"\n\tsay person's name\n", new Greeting(alice));
        assertEquals(List.of("[Carol]"), said);
        assertEquals("Carol", alice.name);
    }

    @Test
    void propertyConditionsAndTheirNegation() {
        String script = "on greet:\n\tperson is shouting\n\tsay \"loud\"\non greet:\n\tperson isn't shouting\n"
                + "\tsay \"quiet\"\n";
        fire(script, new Greeting(new Person("Alice", true)));
        fire(script, new Greeting(new Person("Bob", false)));
        assertEquals(List.of("[loud]", "[quiet]"), said);
    }

    @Test
    void anEventWithoutTheValueIsAnError() {
        assertEquals("There's no number in an on greet event.", error("on greet:\n\tsay event-number\n"));
        assertTrue(error("on load:\n\tsay event-person\n").startsWith("There's no person in"));
        // Where any object is wanted, a type's name alone is the type, as Skript reads words no expression takes.
        assertEquals(List.of(), compile("on load:\n\tsay person\n").diagnostics());
    }

    @Test
    void aNarrowerEventsValueIsTheValueWhenTheEventIsOne() {
        // As in Skript: a farewell is a greeting, so a greeting's trigger can ask for a farewell's text.
        String script = "on greet:\n\tsay event-text\n";
        fire(script, new Greeting(new Person("Alice", false)));
        fire(script, new Farewell(new Person("Bob", false)));
        assertEquals(List.of("[]", "[bye]"), said);
    }

    @Test
    void expressionsThatCantBeChangedThatWayAreAnError() {
        assertEquals("name of person can't be deleted", error("on greet:\n\tdelete name of person\n"));
    }

    /** A filter that remembers what was written in the event's line. */
    record Topic(String topic) implements co.xenastudios.neoskript.api.element.Condition {
        @Override
        public boolean check(TriggerContext context, Object[] arguments) {
            return true;
        }
    }

    @Test
    void aServerEventsTriggerHasItsEventsIdAndTheFilterMadeForIt() {
        library.event("Ask", List.of(Greeting.class), info -> new Topic(info.argumentLiteral(0)), "ask about %*string%");
        CompiledScripts compiled = compile("ask about \"cake\":\n\tsay \"yes\"\n");
        assertEquals(List.of(), compiled.diagnostics());
        CompiledScripts.CompiledTrigger trigger = compiled.triggers().getFirst();
        assertEquals("Ask", trigger.eventId());
        assertEquals(new Topic("cake"), trigger.filter());
    }

    @Test
    void aSyntaxErrorFromAnEventsFilterIsTheLinesError() {
        library.event("Strict", List.of(Greeting.class), info -> {
            throw new co.xenastudios.neoskript.api.element.SyntaxError("Strictly no " + info.argumentLiteral(0));
        }, "strict %*string%");
        assertEquals("Strictly no x", error("strict \"x\":\n\tsay \"hi\"\n"));
    }

    @Test
    void addonsCanBeToldWhichTriggersOfTheirEventsAreLoaded() {
        java.util.function.Consumer<List<co.xenastudios.neoskript.api.registration.LoadedTrigger>> watcher = triggers -> {};
        library.eventTriggers("Greet", watcher);
        assertEquals(List.of(watcher), library.triggerWatchers().get("Greet"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> library.eventTriggers("ScriptLoad", watcher));
    }

    @Test
    void syntaxForOtherEventsIsTriedAfterSyntaxThatWorksHere() {
        // Registered first, so without its events it would be read first everywhere, as 'the result' was.
        library.expression("FarewellMood", "string", info -> {
            if (!info.isEvent(Farewell.class)) {
                throw new co.xenastudios.neoskript.api.element.SyntaxError("'the mood' is only for farewells");
            }
            return (context, args) -> "sad";
        }, "[the] mood");
        library.onlyInEvents("FarewellMood", -1, Farewell.class);
        library.expression("AnyMood", "string", info -> (context, args) -> "fine", "[the] mood");
        library.expression("FarewellTone", "string", info -> {
            if (!info.isEvent(Farewell.class)) {
                throw new co.xenastudios.neoskript.api.element.SyntaxError("'the tone' is only for farewells");
            }
            return (context, args) -> "low";
        }, "[the] tone");
        library.onlyInEvents("FarewellTone", -1, Farewell.class);

        fire("on farewell:\n\tsay the mood\n\tsay the tone\n", new Farewell(new Person("Ann", false)));
        fire("on greeting:\n\tsay the mood\n", new Greeting(new Person("Ann", false)));
        assertEquals(List.of("[sad]", "[low]", "[fine]"), said);
        // With nothing else to read it, it's still read, and its error is the line's.
        assertEquals("'the tone' is only for farewells", error("on greeting:\n\tsay the tone\n"));
    }

    @Test
    void aLaterSectionKeepsItsCodeForItsOwnEventWithOneCopyOfTheLocals() {
        List<co.xenastudios.neoskript.api.element.SectionCode> kept = new ArrayList<>();
        library.laterSection("KeepCode", "farewell", List.of(Farewell.class), info -> (context, args, code) -> {
            if (code != null) {
                kept.add(code);
            }
        }, "keep code");
        fire("on greeting:\n\tset {_x} to 5\n\tkeep code:\n\t\tsay {_x} and name of person\n\t\tadd 1 to {_x}\n"
                + "\tset {_x} to 100\n\tsay \"after\"\n\tkeep code\n", new Greeting(new Person("Ann", false)));
        assertEquals(List.of("[after]"), said);
        assertEquals(1, kept.size());
        assertEquals("test.sk", kept.getFirst().script());
        kept.getFirst().run(new Farewell(new Person("Bo", false)), null);
        kept.getFirst().run(new Farewell(new Person("Cy", false)), null);
        // The locals as they were at the section, shared by every run; the event is each run's.
        assertEquals(List.of("[after]", "[5, Bo]", "[6, Cy]"), said);
    }

    @Test
    void everyLineOfALaterSectionRuns() {
        List<co.xenastudios.neoskript.api.element.SectionCode> kept = new ArrayList<>();
        library.laterSection("KeepCode2", "farewell", List.of(Farewell.class), info -> (context, args, code) -> {
            if (code != null) {
                kept.add(code);
            }
        }, "keep code");
        fire("on greeting:\n\tkeep code:\n\t\tsay \"one\"\n\t\twave at person\n\t\tsay \"end\"\n",
                new Greeting(new Person("Ann", false)));
        kept.getFirst().run(new Farewell(new Person("Bo", false)), null);
        assertEquals(List.of("[one]", "wave at Bo", "[end]"), said);
    }
}
