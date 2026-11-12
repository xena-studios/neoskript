/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.compiler;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.IADD;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ARRAYLENGTH;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.I2L;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_M1;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.IF_ICMPGE;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INSTANCEOF;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.LADD;
import static org.objectweb.asm.Opcodes.LCONST_1;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V21;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Effect;
import co.xenastudios.neoskript.api.element.ElementFactory;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.KeyAware;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.Function;
import co.xenastudios.neoskript.api.element.SettableCondition;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.element.Update;
import co.xenastudios.neoskript.frontend.parse.CommandPattern;
import co.xenastudios.neoskript.frontend.parse.Expr;
import co.xenastudios.neoskript.frontend.parse.ParsedScript;
import co.xenastudios.neoskript.frontend.parse.ParsedStructure;
import co.xenastudios.neoskript.frontend.parse.ParsedSyntax;
import co.xenastudios.neoskript.frontend.parse.Statement;
import co.xenastudios.neoskript.frontend.pattern.CompiledPattern;
import co.xenastudios.neoskript.frontend.pattern.PatternElement;
import co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec;
import co.xenastudios.neoskript.frontend.registry.FunctionInfo;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import co.xenastudios.neoskript.runtime.Arithmetic;
import co.xenastudios.neoskript.runtime.CompiledFunction;
import co.xenastudios.neoskript.runtime.Frame;
import co.xenastudios.neoskript.runtime.FunctionHandle;
import co.xenastudios.neoskript.runtime.Resumable;
import co.xenastudios.neoskript.runtime.KeyedValues;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.Waits;
import co.xenastudios.neoskript.runtime.region.RegionAccess;
import co.xenastudios.neoskript.runtime.region.RegionalElements;
import co.xenastudios.neoskript.runtime.region.Regions;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.Trigger;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.command.CommandEvent;
import co.xenastudios.neoskript.runtime.generated.TriggerHost;
import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Compiles a trigger or a function body into a hidden class.
 *
 * <p>Elements are called through: the generated code evaluates their placeholders and calls the element
 * with the values. Each element instance is a constant of the class (loaded with
 * {@link MethodHandles#classDataAt}), so every call site has exactly one target and the JIT can inline it.
 * Control flow, variables, loop values and arithmetic ({@link CoreSyntax}) become bytecode directly.
 *
 * <p>The JVM leaves hidden classes out of stack traces, so the generated code keeps the current script
 * line in a local variable and rethrows anything thrown as a {@link ScriptRuntimeException} with that
 * line. Storing an int per statement costs next to nothing.
 */
final class TriggerCompiler {

    private static final String HOST = Type.getInternalName(TriggerHost.class);
    private static final String PACKAGE = HOST.substring(0, HOST.lastIndexOf('/') + 1);
    private static final String CONTEXT = Type.getDescriptor(TriggerContext.class);
    private static final String VALUES = Type.getInternalName(Values.class);
    /** {@code keyed %objects%}: values with their indices, where something keeps them. */
    private static final String KEYED = "ExprKeyed";
    private static final String COMMAND_EVENT = Type.getInternalName(CommandEvent.class);
    private static final String SUGGESTION_EVENT =
            Type.getInternalName(co.xenastudios.neoskript.runtime.command.CommandSuggestionEvent.class);
    private static final String STORE = Type.getInternalName(VariableStore.class);
    private static final String OBJECT = "java/lang/Object";
    private static final String OBJECT_DESC = "Ljava/lang/Object;";
    private static final String OBJECTS = "[Ljava/lang/Object;";
    private static final String STRING_DESC = "Ljava/lang/String;";
    private static final String TEST_RUN = "co/xenastudios/neoskript/runtime/testing/TestRun";
    private static final String FRAME_DESC = Type.getDescriptor(Frame.class);
    private static final String STORE_DESC = Type.getDescriptor(VariableStore.class);
    private static final Handle CLASS_DATA_AT = new Handle(H_INVOKESTATIC, "java/lang/invoke/MethodHandles",
            "classDataAt", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;I)Ljava/lang/Object;",
            false);
    private static final Pattern LOOP_VALUE = Pattern.compile("(.+?)(?:-(\\d+))?");
    private static final Pattern SPECIFIED_LITERAL = Pattern.compile("(.+?)\\s+\\(\\s*[\\p{L} ]+?\\s*\\)");

    /** Local variable slots: 0 is this, 1 the trigger context, 2 a function's arguments. */
    private static final int CONTEXT_SLOT = 1;
    private static final int ARGUMENTS_SLOT = 2;
    private static final int LINE_SLOT = 3;
    private static final int EXCEPTION_SLOT = 4;
    private static final int LOCALS_SLOT = 5;
    /** The problems captured by the last {@code parse:} section, for {@code last parse logs}. */
    private static final int PARSE_LOGS_SLOT = 6;

    private enum BlockKind {
        CONDITIONAL,
        LOOP,
        WHILE,
        /** A library section's code, like {@code spawn a pig:}: run once for each value the section returns. */
        SECTION,
        /** Code whose {@code return} gives values to the section, not the function: Skript's returnable sections. */
        RETURNABLE,
        RETURNABLE_PLURAL
    }

    /**
     * A section being compiled, for exit, continue and loop values.
     *
     * @param valueType for a library section, the type of the values its code runs for
     * @param queueSlot for a loop, the queue its values are taken from as it goes, or -1
     */
    /** Loops over values that can't be peeked at with {@code next loop-value}, with how their expression reads. */
    private final Map<Block, String> unpeekable = new java.util.IdentityHashMap<>();

    /** What each loop loops, for which loop a {@code loop-<name>} is of. */
    private final Map<Block, Expr> looped = new java.util.IdentityHashMap<>();

    private record Block(BlockKind kind, Label end, Label next, int valuesSlot, int indicesSlot, int indexSlot,
            String valueType, int queueSlot) {

        Block(BlockKind kind, Label end, Label next, int valuesSlot, int indicesSlot, int indexSlot) {
            this(kind, end, next, valuesSlot, indicesSlot, indexSlot, null);
        }

        Block(BlockKind kind, Label end, Label next, int valuesSlot, int indicesSlot, int indexSlot,
                String valueType) {
            this(kind, end, next, valuesSlot, indicesSlot, indexSlot, valueType, -1);
        }
    }

    /**
     * A {@code run on X:} being compiled for Folia: the slot X is kept in, and the sections around it, which its
     * code can't jump to as the rest of the trigger runs on another thread. With {@code implicit}, it's a library
     * section that runs where its entity or location is ({@link #librarySection}), whose code can jump out: it goes
     * back to the right thread first. {@code carried} are more slots it keeps across going there, of arrays.
     */
    private record RunOn(int slot, java.util.Set<Block> outside, boolean implicit, int[] carried) {

        RunOn(int slot, java.util.Set<Block> outside) {
            this(slot, outside, false, new int[0]);
        }
    }

    /** The {@code run on} sections around the code being compiled for Folia, innermost first. */
    private final Deque<RunOn> runOns = new ArrayDeque<>();

    /** A {@code wait}: where the trigger carries on, and the loop state it saves. */
    private record WaitPoint(Label resume, int line, List<int[]> savedSlots) {}

    /** Kinds of saved local variable slots, as {@code {slot, kind}}. */
    private static final int SAVED_VALUES = 0;
    private static final int SAVED_INDICES = 1;
    private static final int SAVED_INDEX = 2;
    private static final int SAVED_QUEUE = 3;
    private static final int SAVED_OBJECT = 4;

    private final ParsedScript script;
    private final List<WaitPoint> waits = new ArrayList<>();
    private co.xenastudios.neoskript.frontend.structure.Line currentLine;
    private final Library library;
    private final SyntaxRegistry registry;
    private final Map<FunctionInfo, FunctionHandle> functions;
    private final VariableStore globals;
    private final List<Object> constants = new ArrayList<>();
    private final Deque<Block> blocks = new ArrayDeque<>();
    private MethodVisitor method;
    private int nextSlot = PARSE_LOGS_SLOT + 1;
    private FunctionInfo function;
    /** The text expression positions refer to: the current line, a section header's key, or a default value. */
    private String source;
    private int line;
    /** The name of the script being compiled, for test failures. */
    private String constantsScript;
    /** The classes of the event a trigger runs for, or {@code null} outside server events. */
    private List<Class<?>> eventClasses;
    /** The calls into syntax elements so far, with their lines, for runtime errors (see {@link #site}). */
    private final List<co.xenastudios.neoskript.runtime.errors.ErrorSites.Site> sites = new ArrayList<>();

    /** The classes the last server event trigger compiled listens to: its event's, or those its filter chose. */
    List<Class<?>> eventClasses() {
        return eventClasses;
    }

    /**
     * An inline function's loop over a list ({@code where [...]}): the slots of the current value, its
     * position, the list's indices (or -1), and the reduced value so far (or -1).
     */
    private record InputFrame(int valueSlot, int indexSlot, int indicesSlot, int reducedSlot) {}

    private final Deque<InputFrame> inputs = new ArrayDeque<>();
    /** Expressions already worked out into a slot, as an expression section's value is before its effect runs. */
    private final Map<Expr, Integer> precomputed = new java.util.IdentityHashMap<>();
    /** The text of the statement's argument being compiled, for errors about words in it nothing parses. */
    private String outerArgument;
    /** Values left-out placeholders of a type get, from Skript's custom default test sections, innermost first. */
    private final java.util.Deque<Map.Entry<String, Expr>> customDefaults = new java.util.ArrayDeque<>();
    /** For code compiled apart, the types of the sections around it, whose values it may use (as nothing). */
    private final java.util.Set<String> outerSections = new java.util.HashSet<>();
    /** Warnings from the last code compiled apart, for a {@code parse:} section's logs. */
    private final List<String> apartWarnings = new ArrayList<>();
    /** The errors found in the statements so far, and how deep in statements compiling is. */
    private final List<CompileException> errors = new ArrayList<>();
    private int depth;

    /** Where warnings found while compiling go: the line and the message. */
    private java.util.function.BiConsumer<Integer, String> warnings = (line, message) -> {};

    /** Sends the warnings this compiler finds to {@code sink}. */
    TriggerCompiler warnings(java.util.function.BiConsumer<Integer, String> sink) {
        this.warnings = sink;
        return this;
    }

    /** Where the lines of sections whose header fails go: Skript doesn't parse them, so their problems aren't. */
    private java.util.function.IntConsumer unparsed = line -> { };

    TriggerCompiler unparsed(java.util.function.IntConsumer sink) {
        this.unparsed = sink;
        return this;
    }

    /** Tells {@link #unparsed} the lines of {@code body}, and of what's in it. */
    private void unparsed(List<Statement> body) {
        for (Statement statement : body) {
            unparsed.accept(statement.line().number());
            if (statement instanceof Statement.Section section && section.body() != null) {
                unparsed(section.body());
            }
        }
    }

    /** The warnings given so far, as line and message, each given once though elements may be set up again. */
    private final java.util.Set<String> warned = new java.util.HashSet<>();
    /** The header of the structure being compiled, for warnings given before its code. */
    private co.xenastudios.neoskript.frontend.structure.Line headerLine;

    /**
     * A warning at the current line, unless the script suppresses warnings tagged {@code tag} there (a
     * {@code null} tag can't be suppressed).
     */
    private void warn(String tag, String message) {
        if (inArguments > 0 || skriptOrder || tag != null && suppresses(tag)) {
            return;
        }
        if ("constant".equals(tag) && library.testing()) {
            return; // Skript's test runs don't warn of constant conditions
        }
        if (warned.add(line + ":" + message)) {
            warnings.accept(line, message);
        }
    }

    /** The element last set up, and the text its positions are in, for the text of its arguments in warnings. */
    private ParsedSyntax settingUp;
    private String settingUpSource;

    /** Argument {@code index} of the element being set up, as written, or {@code null}. */
    private String argumentText(int index) {
        if (settingUp == null || index < 0 || index >= settingUp.match().values().size()
                || !(settingUp.match().values().get(index) instanceof Expr expr) || settingUpSource == null
                || expr.end() > settingUpSource.length() || expr.start() > expr.end()) {
            return null;
        }
        return settingUpSource.substring(expr.start(), expr.end()).strip();
    }

    /** Whether the script suppresses warnings tagged {@code tag} at the line being compiled. */
    private boolean suppresses(String tag) {
        return script.suppresses(currentLine != null ? currentLine : headerLine, tag);
    }

    /** How many lists the expression being compiled is in. */
    private int inList;

    /** The types of the placeholder whose value is being compiled, which words are first tried as. */
    private List<String> literalTypes = List.of();
    /**
     * What Skript calls the code's event in errors, as its current event name: {@code on join}, {@code command};
     * {@code null} in a function.
     */
    private String eventLabel;
    /** A command trigger's arguments, by number, or {@code null} outside commands. */
    private List<CommandPattern.Argument> commandArguments;
    /** How many choices a command trigger's pattern has. */
    private int commandChoices;
    /**
     * In a command's {@code suggestions:} entry, the numbers of the arguments whose suggestions its code
     * changes; else {@code null}.
     */
    private java.util.Set<Integer> suggestingArguments;

    TriggerCompiler(Library library, Map<FunctionInfo, FunctionHandle> functions, VariableStore globals,
            ParsedScript script) {
        this.script = script;
        this.library = library;
        this.registry = library.registry();
        this.functions = functions;
        this.globals = globals;
    }

    Trigger compileTrigger(String scriptName, List<Statement> body) throws CompileException {
        return (Trigger) define(generate(scriptName, () -> statements(body)));
    }

    /**
     * A line of code on its own, run for an event of one of {@code classes}: Skript's effect commands, whose
     * event is called {@code eventName} in errors.
     */
    Trigger compileLine(String scriptName, Statement statement, List<Class<?>> classes, String eventName)
            throws CompileException {
        eventClasses = List.copyOf(classes);
        eventLabel = eventName;
        return compileTrigger(scriptName, List.of(statement));
    }

    /** A script's {@code on load} or {@code on unload} trigger, whose event is a {@link co.xenastudios.neoskript.runtime.ScriptLoadEvent}. */
    Trigger compileScriptTrigger(String scriptName, List<Statement> body) throws CompileException {
        eventClasses = List.of(co.xenastudios.neoskript.runtime.ScriptLoadEvent.class);
        eventLabel = "on script load/unload";
        return compileTrigger(scriptName, body);
    }

    /** A periodical trigger ({@code every 5 seconds}), whose event is a {@link co.xenastudios.neoskript.runtime.PeriodicalEvent}. */
    Trigger compilePeriodical(String scriptName, List<Statement> body) throws CompileException {
        eventClasses = List.of(co.xenastudios.neoskript.runtime.PeriodicalEvent.class);
        eventLabel = "periodical";
        return compileTrigger(scriptName, body);
    }

    /**
     * A trigger for a server event. If the event has a filter ({@code on break of stone}), the trigger
     * stops straight away when it fails.
     */
    Trigger compileTrigger(String scriptName, ParsedStructure.Event event, Library.ServerEvent binding)
            throws CompileException {
        eventClasses = binding.classes();
        headerLine = event.structure().node().line();
        String key = event.structure().node().line().key();
        // Skript's name for the event, as its errors call it: 'on death' for 'on death of a zombie'.
        String skriptName = library.eventName(event.event().syntax().id());
        eventLabel = skriptName != null ? skriptName
                : "on " + (key.regionMatches(true, 0, "on ", 0, 3) ? key.substring(3).strip() : key.strip());
        return (Trigger) define(generate(scriptName, () -> {
            if (binding.filter() != null) {
                line = event.structure().node().line().number();
                source = key;
                ParsedSyntax syntax = event.event();
                Condition filter = binding.filter().create(info(syntax));
                if (filter instanceof co.xenastudios.neoskript.api.element.EventFilter narrowing) {
                    // 'on player move' listens to player moves only; its code is checked against those.
                    eventClasses = List.copyOf(narrowing.eventClasses());
                }
                site(syntax, filter.getClass());
                constant(filter, Condition.class);
                method.visitVarInsn(ALOAD, CONTEXT_SLOT);
                arguments(syntax);
                method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Condition.class), "check",
                        "(" + CONTEXT + OBJECTS + ")Z", true);
                Label run = new Label();
                method.visitJumpInsn(IFNE, run);
                method.visitInsn(RETURN);
                method.visitLabel(run);
            }
            statements(event.body());
        }));
    }

    /**
     * A command's (or subcommand's) trigger. Left-out arguments get their default values first, then
     * named arguments become local variables: {@code <x: number>} is {@code {_x}}.
     */
    Trigger compileCommand(String scriptName, int headerLine, String header, List<CommandPattern.Argument> arguments,
            int choices, List<Statement> body) throws CompileException {
        commandArguments = arguments;
        commandChoices = choices;
        eventClasses = List.of(CommandEvent.class);
        eventLabel = "command";
        return (Trigger) define(generate(scriptName, () -> {
            line = headerLine;
            for (int i = 0; i < arguments.size(); i++) {
                CommandPattern.Argument argument = arguments.get(i);
                if (argument.defaultValue() != null) {
                    source = argument.defaultText();
                    method.visitVarInsn(ALOAD, CONTEXT_SLOT);
                    pushInt(i);
                    expression(argument.defaultValue());
                    convert(argument.plural());
                    method.visitMethodInsn(INVOKESTATIC, COMMAND_EVENT, "defaultArgument",
                            "(" + CONTEXT + "I" + OBJECT_DESC + ")V", false);
                }
            }
            source = header;
            for (int i = 0; i < arguments.size(); i++) {
                CommandPattern.Argument argument = arguments.get(i);
                if (argument.name() == null) {
                    continue;
                }
                method.visitVarInsn(ALOAD, LOCALS_SLOT);
                method.visitLdcInsn("_" + argument.name());
                commandArgument(i);
                if (argument.plural()) {
                    convert(true);
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "setList", "(" + STRING_DESC + OBJECTS + ")V", false);
                } else {
                    convert(false);
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "set", "(" + STRING_DESC + OBJECT_DESC + ")V", false);
                }
            }
            statements(body);
        }));
    }

    /**
     * A command's {@code suggestions:} entry: code run as the command is typed, which works out what's suggested
     * for its arguments.
     *
     * @param suggesting gets the numbers of the arguments whose suggestions the code changes
     */
    Trigger compileSuggestions(String scriptName, int entryLine, String entry, List<CommandPattern.Argument> arguments,
            int choices, List<Statement> body, java.util.Set<Integer> suggesting) throws CompileException {
        commandArguments = arguments;
        commandChoices = choices;
        suggestingArguments = suggesting;
        eventClasses = List.of(co.xenastudios.neoskript.runtime.command.CommandSuggestionEvent.class);
        eventLabel = "command suggestions";
        return (Trigger) define(generate(scriptName, () -> {
            line = entryLine;
            source = entry;
            statements(body);
        }));
    }

    /**
     * A command's message, such as its {@code cooldown message}: a trigger that stores the text in the
     * {@link CommandEvent}'s result.
     */
    Trigger compileCommandMessage(String scriptName, int entryLine, String text, Expr message) throws CompileException {
        eventClasses = List.of(CommandEvent.class);
        eventLabel = "command";
        return (Trigger) define(generate(scriptName, () -> {
            line = entryLine;
            source = text;
            method.visitVarInsn(ALOAD, CONTEXT_SLOT);
            expression(message);
            method.visitMethodInsn(INVOKESTATIC, COMMAND_EVENT, "result", "(" + CONTEXT + OBJECT_DESC + ")V", false);
        }));
    }

    /**
     * A command's {@code cooldown storage} entry: a trigger whose result is the name of the variable, as Skript's
     * variable-name text gives it ({@code {cooldown::%player%}}, with a player's UUID).
     */
    Trigger compileCooldownStorage(String scriptName, int entryLine, String text, Expr name) throws CompileException {
        eventClasses = List.of(CommandEvent.class);
        eventLabel = "command";
        return (Trigger) define(generate(scriptName, () -> {
            line = entryLine;
            source = text;
            method.visitVarInsn(ALOAD, CONTEXT_SLOT);
            if (name instanceof Expr.Text written) {
                text(written.start(), written.end(), written.embedded(), true);
            } else {
                expression(name);
            }
            method.visitMethodInsn(INVOKESTATIC, COMMAND_EVENT, "result", "(" + CONTEXT + OBJECT_DESC + ")V", false);
        }));
    }

    /** A Skript test ({@code test "name" [when <condition>]:}): skipped if its condition is false. */
    Trigger compileTest(String scriptName, ParsedStructure.Custom test) throws CompileException {
        eventClasses = List.of(co.xenastudios.neoskript.runtime.testing.TestEvent.class);
        eventLabel = "on test case";
        headerLine = test.structure().node().line();
        byte[] bytes = generate(scriptName, () -> {
            if (test.syntax().match().regexValues().getFirst() instanceof ParsedSyntax condition) {
                line = test.structure().node().line().number();
                source = test.structure().node().line().key();
                condition(condition);
                Label run = new Label();
                method.visitJumpInsn(IFNE, run);
                method.visitMethodInsn(INVOKESTATIC, TEST_RUN, "skip", "()V", false);
                method.visitInsn(RETURN);
                method.visitLabel(run);
            }
            statements(test.body());
        });
        return (Trigger) define(bytes);
    }

    CompiledFunction compileFunction(String scriptName, ParsedStructure.Function parsed) throws CompileException {
        function = parsed.signature();
        headerLine = parsed.structure().node().line();
        byte[] bytes = generate(scriptName, () -> {
                    parameters(parsed);
                    statements(parsed.body());
                });
        return (CompiledFunction) define(bytes);
    }

    @FunctionalInterface
    private interface Body {
        void emit() throws CompileException;
    }

    /**
     * Generates the class. A function gets {@code call(context, arguments)}. A trigger gets {@code execute}
     * and {@code resume}, which both run a private {@code body(context, frame)}: with no frame it starts at
     * the top; with one, it restores the frame and jumps to just after the {@code wait} it stopped at.
     */
    private byte[] generate(String scriptName, Body body) throws CompileException {
        constantsScript = scriptName;
        sites.clear();
        boolean trigger = function == null;
        String className = PACKAGE + (trigger ? "Trigger" : "Function");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String first, String second) {
                return OBJECT;
            }
        };
        String[] interfaces = trigger
                ? new String[] {Type.getInternalName(Trigger.class), Type.getInternalName(Resumable.class)}
                : new String[] {Type.getInternalName(CompiledFunction.class), Type.getInternalName(Resumable.class)};
        writer.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, className, null, OBJECT, interfaces);
        writer.visitSource(scriptName, null);

        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, OBJECT, "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        String bodyDescriptor = trigger ? "(" + CONTEXT + FRAME_DESC + ")V"
                : "(" + CONTEXT + OBJECT_DESC + ")" + OBJECT_DESC;
        if (trigger) {
            for (boolean resume : new boolean[] {false, true}) {
                MethodVisitor entry = writer.visitMethod(ACC_PUBLIC, resume ? "resume" : "execute",
                        resume ? bodyDescriptor : "(" + CONTEXT + ")V", null, null);
                entry.visitCode();
                entry.visitVarInsn(ALOAD, 0);
                entry.visitVarInsn(ALOAD, CONTEXT_SLOT);
                if (resume) {
                    entry.visitVarInsn(ALOAD, ARGUMENTS_SLOT);
                } else {
                    entry.visitInsn(ACONST_NULL);
                }
                entry.visitMethodInsn(INVOKESPECIAL, className, "body", bodyDescriptor, false);
                entry.visitInsn(RETURN);
                entry.visitMaxs(0, 0);
                entry.visitEnd();
            }
            method = writer.visitMethod(ACC_PRIVATE, "body", bodyDescriptor, null, null);
        } else {
            // A function's body takes its arguments, or the frame of a wait to carry on from. Carrying on
            // returns nothing to anyone: the caller went on when the function started waiting, as in Skript.
            for (boolean resume : new boolean[] {false, true}) {
                MethodVisitor entry = writer.visitMethod(ACC_PUBLIC, resume ? "resume" : "call",
                        resume ? "(" + CONTEXT + FRAME_DESC + ")V" : "(" + CONTEXT + OBJECTS + ")" + OBJECT_DESC,
                        null, null);
                entry.visitCode();
                entry.visitVarInsn(ALOAD, 0);
                entry.visitVarInsn(ALOAD, CONTEXT_SLOT);
                entry.visitVarInsn(ALOAD, ARGUMENTS_SLOT);
                entry.visitMethodInsn(INVOKESPECIAL, className, "body", bodyDescriptor, false);
                if (resume) {
                    entry.visitInsn(POP);
                    entry.visitInsn(RETURN);
                } else {
                    entry.visitInsn(ARETURN);
                }
                entry.visitMaxs(0, 0);
                entry.visitEnd();
            }
            method = writer.visitMethod(ACC_PRIVATE, "body", bodyDescriptor, null, null);
        }
        method.visitCode();
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        Label dispatch = new Label();
        method.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
        pushInt(0);
        method.visitVarInsn(ISTORE, LINE_SLOT);
        if (trigger) {
            method.visitVarInsn(ALOAD, ARGUMENTS_SLOT);
            method.visitJumpInsn(IFNONNULL, dispatch);
        } else {
            method.visitVarInsn(ALOAD, ARGUMENTS_SLOT);
            method.visitTypeInsn(INSTANCEOF, Type.getInternalName(Frame.class));
            method.visitJumpInsn(IFNE, dispatch);
            method.visitVarInsn(ALOAD, ARGUMENTS_SLOT);
            method.visitTypeInsn(CHECKCAST, OBJECTS);
            method.visitVarInsn(ASTORE, ARGUMENTS_SLOT);
        }
        method.visitTypeInsn(NEW, STORE);
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, STORE, "<init>", "()V", false);
        method.visitVarInsn(ASTORE, LOCALS_SLOT);
        method.visitInsn(ICONST_0);
        method.visitTypeInsn(ANEWARRAY, OBJECT);
        method.visitVarInsn(ASTORE, PARSE_LOGS_SLOT);
        method.visitLabel(start);
        // Elements set up while compiling give their warnings here, as Skript's do from init.
        co.xenastudios.neoskript.api.element.Warnings.Sink outer =
                co.xenastudios.neoskript.api.element.Warnings.install(new co.xenastudios.neoskript.api.element.Warnings.Sink() {
                    @Override
                    public void warn(String tag, String message) {
                        TriggerCompiler.this.warn(tag, message);
                    }

                    @Override
                    public boolean suppresses(String tag) {
                        return TriggerCompiler.this.suppresses(tag);
                    }

                    @Override
                    public String argument(int index) {
                        return argumentText(index);
                    }

                    @Override
                    public String line() {
                        return settingUpSource;
                    }

                    @Override
                    public boolean delayed() {
                        return currentLine != null && script.delayBefore(currentLine)
                                != co.xenastudios.neoskript.frontend.registry.DelayState.NOT_DELAYED;
                    }

                    @Override
                    public boolean surelyDelayed() {
                        return currentLine != null && script.delayBefore(currentLine)
                                == co.xenastudios.neoskript.frontend.registry.DelayState.DELAYED;
                    }
                });
        try {
            body.emit();
        } finally {
            co.xenastudios.neoskript.api.element.Warnings.install(outer);
        }
        exitTrigger();
        method.visitLabel(end);
        // catch (Throwable t) { throw t instanceof ScriptRuntimeException ? t : new ScriptRuntimeException(...); }
        // Errors from a function called here already say where they happened, so they pass through as is.
        method.visitLabel(handler);
        method.visitVarInsn(ASTORE, EXCEPTION_SLOT);
        String exception = Type.getInternalName(ScriptRuntimeException.class);
        Label wrap = new Label();
        method.visitVarInsn(ALOAD, EXCEPTION_SLOT);
        method.visitTypeInsn(INSTANCEOF, exception);
        method.visitJumpInsn(IFEQ, wrap);
        method.visitVarInsn(ALOAD, EXCEPTION_SLOT);
        method.visitInsn(ATHROW);
        method.visitLabel(wrap);
        method.visitTypeInsn(NEW, exception);
        method.visitInsn(DUP);
        method.visitLdcInsn(scriptName);
        method.visitVarInsn(ILOAD, LINE_SLOT);
        method.visitVarInsn(ALOAD, EXCEPTION_SLOT);
        method.visitMethodInsn(INVOKESPECIAL, exception, "<init>", "(Ljava/lang/String;ILjava/lang/Throwable;)V", false);
        method.visitInsn(ATHROW);
        method.visitLabel(dispatch);
        if (!trigger) {
            method.visitVarInsn(ALOAD, ARGUMENTS_SLOT);
            method.visitTypeInsn(CHECKCAST, Type.getInternalName(Frame.class));
            method.visitVarInsn(ASTORE, ARGUMENTS_SLOT);
        }
        dispatch();
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /**
     * Records a call into {@code syntax}'s element, of class {@code element}, on the current line, so a
     * runtime error it reports can say where it came from. Nothing is added to the code that runs.
     */
    private co.xenastudios.neoskript.runtime.errors.ErrorSites.Site site(ParsedSyntax syntax, Class<?> element) {
        String code = currentLine != null ? currentLine.text() : source;
        co.xenastudios.neoskript.runtime.errors.ErrorSites.Site site = new co.xenastudios.neoskript.runtime.errors.ErrorSites.Site(
                kindName(syntax.syntax().kind()), syntax.syntax().id(), element, constantsScript, line, code);
        sites.add(site);
        return site;
    }

    /**
     * {@code element}, to be called as {@code type}, as code compiled for Folia calls it: wrapped to check the
     * region ownership of the entities, blocks and locations its placeholders take ({@link RegionalElements}).
     * Without region checks (on Paper), or for elements that only read what's safe anywhere, it's the element.
     */
    private Object regional(Object element, Class<?> type, ParsedSyntax syntax,
            co.xenastudios.neoskript.runtime.errors.ErrorSites.Site site) {
        RegionAccess access = Regions.access();
        if (access == null || Regions.exempt(syntax.syntax().id())) {
            return element;
        }
        PlaceholderSpec[] specs = placeholderSpecs(syntax.syntax().patterns().get(syntax.pattern()));
        long reads = 0;
        for (int i = 0; i < specs.length && i < 64; i++) {
            for (String typeName : specs[i].types()) {
                RegionAccess.Kind kind = regionKind(access, typeName);
                if (kind == RegionAccess.Kind.ENTITY || kind == RegionAccess.Kind.BLOCK) {
                    reads |= 1L << i;
                }
            }
        }
        int subject = Regions.global(syntax.syntax().id()) ? Regions.GLOBAL_SUBJECT : subject(syntax);
        return RegionalElements.wrap(element, type, site, reads, subject);
    }

    /**
     * The argument an element acts on, for region ownership: the first that takes entities, else blocks or
     * locations, or -1.
     */
    private int subject(ParsedSyntax syntax) {
        RegionAccess access = Regions.access();
        if (access == null) {
            return -1;
        }
        PlaceholderSpec[] specs = placeholderSpecs(syntax.syntax().patterns().get(syntax.pattern()));
        int place = -1;
        for (int i = 0; i < specs.length; i++) {
            for (String typeName : specs[i].types()) {
                RegionAccess.Kind kind = regionKind(access, typeName);
                if (kind == RegionAccess.Kind.ENTITY) {
                    return i;
                }
                if (kind != RegionAccess.Kind.NONE && place < 0) {
                    place = i;
                }
            }
        }
        return place;
    }

    private RegionAccess.Kind regionKind(RegionAccess access, String typeName) {
        TypeRef ref = registry.resolveType(typeName);
        return ref == null ? RegionAccess.Kind.NONE : access.kindOf(library.javaClass(ref.type().id()));
    }

    /** Skript's {@code getSyntaxTypeName}: events are structures. */
    private static String kindName(co.xenastudios.neoskript.frontend.registry.SyntaxKind kind) {
        return switch (kind) {
            case STRUCTURE, EVENT -> "structure";
            case SECTION -> "section";
            case EFFECT -> "effect";
            case CONDITION -> "condition";
            case EXPRESSION -> "expression";
        };
    }

    /** Records the class's element calls, now that it's defined. */
    private void registerSites(Class<?> compiled) {
        co.xenastudios.neoskript.runtime.errors.ErrorSites.register(compiled,
                sites.toArray(co.xenastudios.neoskript.runtime.errors.ErrorSites.Site[]::new));
        sites.clear();
    }

    /** Restores a waiting trigger's frame and jumps to just after its {@code wait}. */
    private void dispatch() {
        String frame = Type.getInternalName(Frame.class);
        method.visitVarInsn(ALOAD, ARGUMENTS_SLOT);
        method.visitFieldInsn(GETFIELD, frame, "locals", STORE_DESC);
        method.visitVarInsn(ASTORE, LOCALS_SLOT);
        method.visitVarInsn(ALOAD, ARGUMENTS_SLOT);
        method.visitFieldInsn(GETFIELD, frame, "parseLogs", OBJECT_DESC);
        method.visitVarInsn(ASTORE, PARSE_LOGS_SLOT);
        Label done = new Label();
        if (waits.isEmpty()) {
            exitTrigger();
            return;
        }
        Label[] cases = new Label[waits.size()];
        for (int i = 0; i < cases.length; i++) {
            cases[i] = new Label();
        }
        method.visitVarInsn(ALOAD, ARGUMENTS_SLOT);
        method.visitFieldInsn(GETFIELD, frame, "state", "I");
        method.visitTableSwitchInsn(0, cases.length - 1, done, cases);
        for (int i = 0; i < cases.length; i++) {
            WaitPoint wait = waits.get(i);
            method.visitLabel(cases[i]);
            List<int[]> saved = wait.savedSlots();
            for (int j = 0; j < saved.size(); j++) {
                int slot = saved.get(j)[0];
                method.visitVarInsn(ALOAD, ARGUMENTS_SLOT);
                method.visitFieldInsn(GETFIELD, frame, "slots", OBJECTS);
                pushInt(j);
                method.visitInsn(AALOAD);
                switch (saved.get(j)[1]) {
                    case SAVED_VALUES -> {
                        method.visitTypeInsn(CHECKCAST, OBJECTS);
                        method.visitVarInsn(ASTORE, slot);
                    }
                    case SAVED_INDICES -> {
                        method.visitTypeInsn(CHECKCAST, "[Ljava/lang/String;");
                        method.visitVarInsn(ASTORE, slot);
                    }
                    case SAVED_QUEUE -> {
                        method.visitTypeInsn(CHECKCAST, "java/util/Queue");
                        method.visitVarInsn(ASTORE, slot);
                    }
                    case SAVED_OBJECT -> method.visitVarInsn(ASTORE, slot);
                    default -> {
                        method.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                        method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                        method.visitVarInsn(ISTORE, slot);
                    }
                }
            }
            pushInt(wait.line());
            method.visitVarInsn(ISTORE, LINE_SLOT);
            method.visitJumpInsn(GOTO, wait.resume());
        }
        method.visitLabel(done);
        exitTrigger();
    }

    /**
     * Pushes a new frame to carry on from here (the state number, local variables and the loops around it),
     * and returns the point it carries on from, whose label the caller places.
     */
    private WaitPoint saveFrame() {
        String frame = Type.getInternalName(Frame.class);
        List<int[]> saved = new ArrayList<>();
        for (Block block : loops()) {
            if (block.kind() == BlockKind.LOOP || block.kind() == BlockKind.SECTION) {
                saved.add(new int[] {block.valuesSlot(), SAVED_VALUES});
                if (block.indicesSlot() >= 0) {
                    saved.add(new int[] {block.indicesSlot(), SAVED_INDICES});
                }
                saved.add(new int[] {block.indexSlot(), SAVED_INDEX});
                if (block.queueSlot() >= 0) {
                    saved.add(new int[] {block.queueSlot(), SAVED_QUEUE});
                }
            } else if (block.kind() == BlockKind.WHILE) {
                saved.add(new int[] {block.indexSlot(), SAVED_INDEX});
            }
        }
        for (RunOn runOn : runOns) {
            saved.add(new int[] {runOn.slot(), SAVED_OBJECT});
            for (int carried : runOn.carried()) {
                saved.add(new int[] {carried, SAVED_VALUES});
            }
        }
        WaitPoint wait = new WaitPoint(new Label(), line, saved);
        int state = waits.size();
        waits.add(wait);

        method.visitTypeInsn(NEW, frame);
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, frame, "<init>", "()V", false);
        method.visitInsn(DUP);
        pushInt(state);
        method.visitFieldInsn(PUTFIELD, frame, "state", "I");
        method.visitInsn(DUP);
        method.visitVarInsn(ALOAD, LOCALS_SLOT);
        method.visitFieldInsn(PUTFIELD, frame, "locals", STORE_DESC);
        method.visitInsn(DUP);
        method.visitVarInsn(ALOAD, PARSE_LOGS_SLOT);
        method.visitFieldInsn(PUTFIELD, frame, "parseLogs", OBJECT_DESC);
        method.visitInsn(DUP);
        pushInt(saved.size());
        method.visitTypeInsn(ANEWARRAY, OBJECT);
        for (int i = 0; i < saved.size(); i++) {
            method.visitInsn(DUP);
            pushInt(i);
            if (saved.get(i)[1] == SAVED_INDEX) {
                method.visitVarInsn(ILOAD, saved.get(i)[0]);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            } else {
                method.visitVarInsn(ALOAD, saved.get(i)[0]);
            }
            method.visitInsn(AASTORE);
        }
        method.visitFieldInsn(PUTFIELD, frame, "slots", OBJECTS);
        if (!runOns.isEmpty()) {
            // In 'run on X:', a wait carries on on X's thread.
            method.visitInsn(DUP);
            method.visitVarInsn(ALOAD, runOns.peek().slot());
            method.visitFieldInsn(PUTFIELD, frame, "owner", OBJECT_DESC);
        }
        return wait;
    }

    /**
     * {@code wait %timespan%}: saves the frame (the state number, local variables and the loops around
     * it), schedules the rest of the trigger, and returns. The rest starts at the label after it.
     */
    private void waitFor(ParsedSyntax delay) throws CompileException {
        checkDelay(delay);
        waitBody(delay);
    }

    /** Skript's checks of a delay written out, as its Delay.init. */
    private void checkDelay(ParsedSyntax delay) throws CompileException {
        Object duration = delay.match().values().getFirst();
        if (duration instanceof Expr.Syntax eternity && eternity.syntax().syntax().id().equals("LitEternity")) {
            throw new CompileException(line, "Delaying for an eternity is not allowed. Use the 'stop' effect instead.");
        }
        if (duration instanceof Expr.Literal literal && "timespan".equals(literal.type())) {
            co.xenastudios.neoskript.runtime.Timespan timespan = co.xenastudios.neoskript.runtime.Timespan.parse(literal.text());
            if (timespan != null && timespan.millis() < 50) {
                warn(null, "Delays less than one tick are not possible, defaulting to one tick.");
            }
        }
    }

    private void waitBody(ParsedSyntax delay) throws CompileException {
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        WaitPoint wait = saveFrame();
        expression((Expr) delay.match().values().getFirst());
        method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Waits.class), "ticks", "(" + OBJECT_DESC + ")J", false);
        method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Waits.class), "schedule",
                "(" + Type.getDescriptor(Resumable.class) + CONTEXT + FRAME_DESC + "J)V", false);
        exitTrigger();
        method.visitLabel(wait.resume());
    }

    /**
     * {@code wait 3 seconds:}, Skript's delayed section: its code runs once the delay is over, with a copy of the
     * local variables as they are now, while the code after it carries on straight away.
     */
    private void delayedSection(Statement.Section section) throws CompileException {
        Label after = new Label();
        checkDelay(section.header());
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        WaitPoint start = saveFrame();
        method.visitInsn(DUP);
        method.visitVarInsn(ALOAD, LOCALS_SLOT);
        method.visitMethodInsn(INVOKEVIRTUAL, STORE, "copy", "()" + STORE_DESC, false);
        method.visitFieldInsn(PUTFIELD, Type.getInternalName(Frame.class), "locals", STORE_DESC);
        expression((Expr) section.header().match().values().getFirst());
        method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Waits.class), "ticks", "(" + OBJECT_DESC + ")J", false);
        method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Waits.class), "schedule",
                "(" + Type.getDescriptor(Resumable.class) + CONTEXT + FRAME_DESC + "J)V", false);
        method.visitJumpInsn(GOTO, after);
        method.visitLabel(start.resume());
        statements(section.body());
        exitTrigger();
        method.visitLabel(after);
    }

    private Object define(byte[] bytes) {
        try {
            MethodHandles.Lookup hidden =
                    TriggerHost.lookup().defineHiddenClassWithClassData(bytes, List.copyOf(constants), true);
            registerSites(hidden.lookupClass());
            return hidden.findConstructor(hidden.lookupClass(), MethodType.methodType(void.class)).invoke();
        } catch (Throwable e) {
            throw new IllegalStateException("Couldn't load compiled code", e);
        }
    }

    /** Ends the trigger or function: {@code return} (a function returns nothing). */
    private void exitTrigger() {
        if (function != null) {
            method.visitInsn(ACONST_NULL);
            method.visitInsn(ARETURN);
        } else {
            method.visitInsn(RETURN);
        }
    }

    /** Copies a function's arguments into its local variables, filling in default values. */
    private void parameters(ParsedStructure.Function parsed) throws CompileException {
        List<FunctionInfo.Parameter> parameters = parsed.signature().parameters();
        line = parsed.structure().node().line().number();
        for (int i = 0; i < parameters.size(); i++) {
            FunctionInfo.Parameter parameter = parameters.get(i);
            int slot = nextSlot++;
            method.visitVarInsn(ALOAD, ARGUMENTS_SLOT);
            pushInt(i);
            method.visitInsn(AALOAD);
            method.visitVarInsn(ASTORE, slot);
            Expr defaultValue = parsed.defaults().get(i);
            if (defaultValue != null) {
                Label given = new Label();
                method.visitVarInsn(ALOAD, slot);
                method.visitJumpInsn(IFNONNULL, given);
                source = parsed.defaultTexts().get(i);
                expression(defaultValue);
                method.visitVarInsn(ASTORE, slot);
                method.visitLabel(given);
            }
            method.visitVarInsn(ALOAD, LOCALS_SLOT);
            // Parameters are local variables: {_name}.
            method.visitLdcInsn("_" + parameter.name());
            method.visitVarInsn(ALOAD, slot);
            Class<?> only = strictClass(parameter.type());
            if (only != null) {
                // As in Skript, values of other types never reach a text, number or boolean parameter.
                constant(only, Class.class);
                method.visitMethodInsn(INVOKESTATIC, VALUES, "filterKeyed",
                        "(" + OBJECT_DESC + "Ljava/lang/Class;)" + OBJECT_DESC, false);
            }
            if (parameter.plural()) {
                // Keyed arguments keep their indices: 'f(keyed {_a::*})'.
                method.visitMethodInsn(INVOKEVIRTUAL, STORE, "setListKeyed", "(" + STRING_DESC + OBJECT_DESC + ")V", false);
            } else {
                convert(false);
                method.visitMethodInsn(INVOKEVIRTUAL, STORE, "set", "(" + STRING_DESC + OBJECT_DESC + ")V", false);
            }
        }
    }

    // ---------------------------------------------------------------- statements

    /**
     * Compiles each statement. As in Skript, one that fails is reported and left out and the rest are still
     * compiled, so every error is found; once the outermost statements are done, the errors are thrown, the
     * first with the others suppressed in it.
     */
    private void statements(List<Statement> statements) throws CompileException {
        depth++;
        try {
            for (int i = 0; i < statements.size(); i++) {
                Statement statement = statements.get(i);
                int open = blocks.size();
                try {
                    if (startsConditional(statement)) {
                        i = conditional(statements, i) - 1;
                    } else {
                        statement(statement);
                    }
                } catch (co.xenastudios.neoskript.api.element.SyntaxError error) {
                    // An element that matched but can't be used as written.
                    String message = error.getMessage();
                    if (statement instanceof Statement.Section section && section.body() != null
                            && line == section.line().number()) {
                        unparsed(section.body());
                        // Skript then reads the line as a statement, whose error it keeps if it has one.
                        message = script.statementError(section.line()) != null ? script.statementError(section.line()) : message;
                    }
                    failed(new CompileException(line, message), open);
                } catch (CompileException e) {
                    // A section whose header fails isn't one: as in Skript, what's in it isn't read.
                    if (statement instanceof Statement.Section section && section.body() != null
                            && e.line() == section.line().number()) {
                        unparsed(section.body());
                        if (script.statementError(section.line()) != null && !e.alreadyReported()) {
                            // Skript then reads the line as a statement, whose error it keeps if it has one.
                            e = new CompileException(e.line(), script.statementError(section.line()));
                        }
                    }
                    failed(e, open);
                } catch (RuntimeException e) {
                    if (errors.isEmpty()) {
                        throw e;
                    }
                    // What's left after an earlier error may be half-built: the error is the problem.
                    failed(null, open);
                }
            }
        } finally {
            depth--;
        }
        if (depth == 0 && !errors.isEmpty()) {
            CompileException first = errors.getFirst();
            for (int i = 1; i < errors.size(); i++) {
                first.addSuppressed(errors.get(i));
            }
            errors.clear();
            throw first;
        }
    }

    /** Notes {@code error} (if any) and closes the blocks the failed statement left open. */
    private void failed(CompileException error, int open) {
        if (error != null) {
            errors.add(error);
        }
        while (blocks.size() > open) {
            blocks.pop();
        }
    }

    /**
     * The first error compiling {@code statements} on their own gives, if it's the script's fault, for a
     * {@code parse:} section: the code is compiled apart and thrown away.
     */
    private String compileError(List<Statement> statements) {
        if (statements.isEmpty() || parsedHasInvalid(statements)) {
            return null;
        }
        try {
            TriggerCompiler apart = new TriggerCompiler(library, functions, globals, script);
            apart.eventClasses = eventClasses;
            apart.eventLabel = eventLabel;
            apart.warnings = (at, message) -> apartWarnings.add(message);
            for (Block block : blocks) {
                if (block.kind() == BlockKind.SECTION && block.valueType() != null) {
                    apart.outerSections.add(block.valueType());
                }
            }
            apart.outerSections.addAll(outerSections);
            apart.compileTrigger(constantsScript, statements);
            return null;
        } catch (CompileException e) {
            return e.alreadyReported() || e.getMessage() == null || e.getMessage().startsWith("NeoSkript can't")
                    ? null : e.getMessage();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean parsedHasInvalid(List<Statement> statements) {
        for (Statement statement : statements) {
            if (statement instanceof Statement.Invalid
                    || statement instanceof Statement.Section section && parsedHasInvalid(section.body())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The errors elements in {@code statements} give as they're set up, for a {@code parse:} section, whose
     * code is never compiled.
     */
    private List<String> setupErrors(List<Statement> statements) {
        List<String> errors = new ArrayList<>();
        String outer = source;
        Statement outerStatement = currentStatement;
        for (Statement statement : statements) {
            // Positions in what was parsed are in the statement's own line.
            source = statement instanceof Statement.Section section ? section.line().key() : statement.line().text();
            currentStatement = statement;
            switch (statement) {
                case Statement.Effect effect -> setupErrors(effect.effect(), errors);
                case Statement.Condition condition -> setupErrors(condition.condition(), errors);
                case Statement.Call call -> call.call().arguments().forEach(a -> setupErrors(a, errors));
                case Statement.Section section -> {
                    setupErrors(section.header(), errors);
                    errors.addAll(setupErrors(section.body()));
                }
                case Statement.Conditions conditions -> conditions.conditions().forEach(c -> setupErrors(c, errors));
                case Statement.Parse parse -> errors.addAll(setupErrors(parse.body()));
                case Statement.Invalid invalid -> {}
            }
        }
        source = outer;
        currentStatement = outerStatement;
        return errors;
    }

    private void setupErrors(ParsedSyntax syntax, List<String> errors) {
        if (syntax == null) {
            return;
        }
        syntax.match().values().forEach(value -> setupErrors(value, errors));
        for (Object value : syntax.match().regexValues()) {
            if (value instanceof ParsedSyntax inner) {
                setupErrors(inner, errors);
            } else {
                setupErrors(value, errors);
            }
        }
        if (library.binding(syntax.syntax()) instanceof ElementFactory<?> factory) {
            try {
                factory.create(info(syntax));
            } catch (co.xenastudios.neoskript.api.element.SyntaxError error) {
                errors.add(error.getMessage());
            } catch (RuntimeException ignored) {
                // Only syntax errors are reported here; anything else shows when the code is compiled.
            }
        }
    }

    private void setupErrors(Object value, List<String> errors) {
        switch (value) {
            case Expr.Syntax syntax -> setupErrors(syntax.syntax(), errors);
            case Expr.ListOf list -> list.items().forEach(item -> setupErrors(item, errors));
            case Expr.FunctionCall call -> call.arguments().forEach(a -> setupErrors(a, errors));
            case null, default -> {}
        }
    }

    /** The statement being compiled, whose text {@link #source} is. */
    private Statement currentStatement;

    /**
     * {@code target} as written in the statement being compiled, as Skript's errors quote an element: the whole
     * statement for its effect or condition, else the part it was matched from; {@code null} if not known.
     */
    private String textOf(ParsedSyntax target) {
        if (currentStatement == null || source == null) {
            return null;
        }
        List<ParsedSyntax> roots = switch (currentStatement) {
            case Statement.Effect effect -> List.of(effect.effect());
            case Statement.Condition condition -> List.of(condition.condition());
            case Statement.Section section -> section.header() == null ? List.of() : List.of(section.header());
            case Statement.Conditions conditions -> conditions.conditions();
            default -> List.of();
        };
        for (ParsedSyntax root : roots) {
            if (root == target) {
                return source.strip();
            }
            String found = textIn(root, target);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String textIn(ParsedSyntax in, ParsedSyntax target) {
        for (Object value : in.match().values()) {
            String found = textIn(value, target);
            if (found != null) {
                return found;
            }
        }
        for (Object value : in.match().regexValues()) {
            String found = value instanceof ParsedSyntax inner ? textIn(inner, target) : textIn(value, target);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String textIn(Object value, ParsedSyntax target) {
        switch (value) {
            case Expr.Syntax syntax -> {
                if (syntax.syntax() == target) {
                    return syntax.start() >= 0 && syntax.end() <= source.length() && syntax.start() <= syntax.end()
                            ? source.substring(syntax.start(), syntax.end()) : null;
                }
                return textIn(syntax.syntax(), target);
            }
            case Expr.ListOf list -> {
                for (Expr item : list.items()) {
                    String found = textIn(item, target);
                    if (found != null) {
                        return found;
                    }
                }
            }
            case Expr.FunctionCall call -> {
                for (Expr argument : call.arguments()) {
                    String found = textIn(argument, target);
                    if (found != null) {
                        return found;
                    }
                }
            }
            // The code in a text's '%...%': 'send "%enchantment bonus%"'.
            case Expr.Text text -> {
                for (Expr part : text.embedded()) {
                    String found = textIn(part, target);
                    if (found != null) {
                        return found;
                    }
                }
            }
            case null, default -> {}
        }
        return null;
    }

    private void lineOf(Statement statement, String text) {
        currentStatement = statement;
        currentLine = statement.line();
        line = statement.line().number();
        source = text;
        Label label = new Label();
        method.visitLabel(label);
        method.visitLineNumber(line, label);
        pushInt(line);
        method.visitVarInsn(ISTORE, LINE_SLOT);
    }

    private void statement(Statement statement) throws CompileException {
        lineOf(statement, statement instanceof Statement.Section s ? s.line().key() : statement.line().text());
        switch (statement) {
            case Statement.Effect effect -> effect(effect.effect());
            case Statement.Condition condition -> {
                condition(condition.condition());
                Label pass = new Label();
                method.visitJumpInsn(IFNE, pass);
                exitTrigger();
                method.visitLabel(pass);
            }
            case Statement.Call call -> {
                call(call.call());
                method.visitInsn(POP);
            }
            case Statement.Section section when isCore(section.header(), CoreSyntax.LOOP) -> loop(section);
            case Statement.Section section when isCore(section.header(), CoreSyntax.FOR) -> forLoop(section);
            case Statement.Section section when isCore(section.header(), CoreSyntax.WHILE) -> whileLoop(section);
            case Statement.Section section when isCore(section.header(), CoreSyntax.DELAY) -> delayedSection(section);
            case Statement.Section section when isCore(section.header(), CoreSyntax.CUSTOM_DEFAULT) -> {
                // Left-out values of the type are this one in the code under it, as in Skript's test section.
                Expr value = (Expr) section.header().match().values().getFirst();
                String type = returnableType(new ParsedSyntax(section.header().syntax(), section.header().pattern(),
                        new co.xenastudios.neoskript.frontend.pattern.MatchResult(0, java.util.Set.of(),
                                List.of(section.header().match().values().get(1)), List.of(), List.of(), List.of())));
                customDefaults.push(Map.entry(type, value));
                Label end = new Label();
                try {
                    block(BlockKind.CONDITIONAL, end, section.body());
                } finally {
                    customDefaults.pop();
                }
                method.visitLabel(end);
            }
            case Statement.Section section when isCore(section.header(), CoreSyntax.RETURNABLE) -> {
                Label end = new Label();
                String type = returnableType(section.header());
                blocks.push(new Block(section.header().match().hasTag("plural") ? BlockKind.RETURNABLE_PLURAL
                        : BlockKind.RETURNABLE, end, null, -1, -1, -1, type));
                statements(section.body());
                blocks.pop();
                method.visitLabel(end);
            }
            case Statement.Section section when isCore(section.header(), CoreSyntax.FILTER_SECTION) -> filterSection(section);
            case Statement.Section section when isCore(section.header(), CoreSyntax.RUN_ON) -> runOn(section);
            case Statement.Section section when isCore(section.header(), CoreSyntax.CATCH_ERRORS) -> {
                String errors = Type.getInternalName(co.xenastudios.neoskript.runtime.RuntimeErrors.class);
                Label end = new Label();
                method.visitMethodInsn(INVOKESTATIC, errors, "begin", "()V", false);
                block(BlockKind.CONDITIONAL, end, section.body());
                method.visitLabel(end);
                method.visitMethodInsn(INVOKESTATIC, errors, "end", "()V", false);
            }
            // Suppressing warnings is done while parsing; its code runs as it is.
            case Statement.Section section when isCore(section.header(), CoreSyntax.SUPPRESS_WARNINGS)
                    || isCore(section.header(), CoreSyntax.SUPPRESS_HINTS) -> {
                Label end = new Label();
                block(BlockKind.CONDITIONAL, end, section.body());
                method.visitLabel(end);
            }
            case Statement.Section section when library.sectionType(section.header() == null ? null
                    : section.header().syntax().id()) != null -> librarySection(section);
            case Statement.Section section when sectionExpression(section) != null -> expressionSection(section);
            case Statement.Section section when section.header() == null && section.call() != null
                    && callSectionExpression(section.call()) != null -> {
                // 'f(a worldborder):': the argument's section runs for its value, then the function is called.
                Expr.Syntax made = callSectionExpression(section.call());
                expressionSection(section, made, () -> {
                    call(section.call());
                    method.visitInsn(POP);
                });
            }
            case Statement.Section section when section.line().key().strip().toLowerCase(Locale.ROOT).startsWith("else") ->
                // The 'if' before it failed, so it isn't there.
                throw new CompileException(line, Statement.orphanElse(
                        section.line().key().strip().toLowerCase(Locale.ROOT)));
            case Statement.Section section -> throw unsupported("sections like '" + section.line().key() + "'");
            case Statement.Conditions conditions -> throw unsupported("'" + conditions.line().key() + "' here");
            case Statement.Parse parse -> {
                // Skript's test-only parse section: its code is only parsed, never run. What it reported is
                // kept for 'last parse logs'.
                List<String> logs = new ArrayList<>(parse.captured().stream().map(d -> d.message()).toList());
                logs.addAll(setupErrors(parse.body()));
                // Compiling the code on its own finds what the parser couldn't, without running it.
                apartWarnings.clear();
                String compiled = compileError(parse.body());
                for (String warning : apartWarnings) {
                    if (!logs.contains(warning)) {
                        logs.add(warning);
                    }
                }
                if (compiled != null && !logs.contains(compiled)) {
                    logs.add(compiled);
                }
                constant(logs.toArray(String[]::new), String[].class);
                method.visitVarInsn(ASTORE, PARSE_LOGS_SLOT);
            }
            // The parser reported it; the trigger is left out without a second error.
            case Statement.Invalid invalid -> throw new CompileException(line, null);
        }
    }

    private boolean isCore(ParsedSyntax syntax, String id) {
        return syntax != null && library.binding(syntax.syntax()) instanceof Library.Core core && core.id().equals(id);
    }

    private static String patternSource(ParsedSyntax syntax) {
        return syntax.syntax().patterns().get(syntax.pattern()).source();
    }

    // ---------------------------------------------------------------- conditionals

    private boolean startsConditional(Statement statement) {
        return switch (statement) {
            case Statement.Conditions conditions -> !isElse(conditions.line().key());
            case Statement.Section section ->
                    isCore(section.header(), CoreSyntax.CONDITIONAL) && !isElse(section.line().key())
                            && !section.line().key().toLowerCase(Locale.ROOT).startsWith("then");
            default -> false;
        };
    }

    private static boolean isElse(String key) {
        return key.strip().toLowerCase(Locale.ROOT).startsWith("else");
    }

    /**
     * Compiles an {@code if} and the {@code else if}/{@code else} sections after it. Returns the index of
     * the first statement after them.
     */
    private int conditional(List<Statement> statements, int i) throws CompileException {
        Label chainEnd = new Label();
        boolean first = true;
        while (i < statements.size()) {
            Statement statement = statements.get(i);
            String key = statement.line().key().strip().toLowerCase(Locale.ROOT);
            if (!first && !isElse(key)) {
                break;
            }
            first = false;
            Label next = new Label();
            if (statement instanceof Statement.Conditions conditions) {
                lineOf(statement, statement.line().key());
                Label then = new Label();
                boolean any = key.contains(" any") || key.contains("at least one");
                // As Skript, checked before the conditions are read.
                if (i + 1 >= statements.size() || !(statements.get(i + 1) instanceof Statement.Section following)
                        || !following.line().key().strip().toLowerCase(Locale.ROOT).startsWith("then")) {
                    throw new CompileException(statement.line().number(), (any ? "'if any'" : "'if all'")
                            + " has to be placed just before a 'then' section");
                }
                for (int c = 0; c < conditions.conditions().size(); c++) {
                    // Each condition's positions are in its own line.
                    co.xenastudios.neoskript.frontend.structure.Line conditionLine = conditions.conditionLines().get(c);
                    line = conditionLine.number();
                    source = conditionLine.text();
                    conditionLines = true;
                    try {
                        condition(conditions.conditions().get(c));
                    } finally {
                        conditionLines = false;
                    }
                    method.visitJumpInsn(any ? IFNE : IFEQ, any ? then : next);
                }
                if (any) {
                    method.visitJumpInsn(GOTO, next);
                }
                method.visitLabel(then);
                i++;
                Statement.Section body = (Statement.Section) statements.get(i);
                block(BlockKind.CONDITIONAL, chainEnd, body.body());
            } else if (statement instanceof Statement.Section section && key.equals("else")) {
                lineOf(statement, section.line().key());
                block(BlockKind.CONDITIONAL, chainEnd, section.body());
                i++;
                break;
            } else if (statement instanceof Statement.Section section && key.startsWith("parse if")
                    && (containsInvalid(section.body()) || !compilesApart(section.body()))) {
                // 'parse if' code that doesn't parse: Skript only parses it if the condition holds while
                // loading, so here it can't have, and the code is left out.
                lineOf(statement, section.line().key());
            } else if (statement instanceof Statement.Section section) {
                lineOf(statement, section.line().key());
                if (!(section.header().match().regexValues().getFirst() instanceof ParsedSyntax condition)) {
                    throw unsupported("'" + section.line().key() + "'");
                }
                condition(condition);
                method.visitJumpInsn(IFEQ, next);
                block(BlockKind.CONDITIONAL, chainEnd, section.body());
            } else {
                break;
            }
            method.visitJumpInsn(GOTO, chainEnd);
            method.visitLabel(next);
            i++;
        }
        method.visitLabel(chainEnd);
        return i;
    }

    /** Whether {@code statements} compile on their own, as {@code parse if} code has to for its condition to hold. */
    private boolean compilesApart(List<Statement> statements) {
        try {
            TriggerCompiler apart = new TriggerCompiler(library, functions, globals, script);
            apart.eventClasses = eventClasses;
            apart.eventLabel = eventLabel;
            apart.function = function;
            apart.compileTrigger(constantsScript, statements);
            return true;
        } catch (CompileException e) {
            return false;
        } catch (RuntimeException e) {
            // Compiled, though not into a trigger (a function's code).
            return true;
        }
    }

    private static boolean containsInvalid(List<Statement> statements) {
        for (Statement statement : statements) {
            if (statement instanceof Statement.Invalid
                    || statement instanceof Statement.Section s && containsInvalid(s.body())) {
                return true;
            }
        }
        return false;
    }

    private void block(BlockKind kind, Label end, List<Statement> body) throws CompileException {
        blocks.push(new Block(kind, end, null, -1, -1, -1));
        statements(body);
        blocks.pop();
    }

    // ---------------------------------------------------------------- loops

    /** {@code loop %objects%:} over a list variable (with indices) or any values. */
    /** {@code for {_key}, {_value} in X}: a loop over X's values (with their indices), set into the variables. */
    private void forLoop(Statement.Section section) throws CompileException {
        List<Object> values = section.header().match().values();
        Expr looped = (Expr) values.getLast();
        int pattern = section.header().pattern();
        Expr.Variable key = pattern == 0 ? null : forVariable(values.get(0));
        Expr.Variable value = pattern == 1 ? null : forVariable(values.get(pattern == 0 ? 0 : 1));
        loop(section, looped, key, value);
    }

    private Expr.Variable forVariable(Object placeholder) throws CompileException {
        if (placeholder instanceof Expr.Variable variable && !variable.list()) {
            return variable;
        }
        throw new CompileException(line, "A 'for' loop puts its values into single variables, like {_value}");
    }

    private void loop(Statement.Section section) throws CompileException {
        loop(section, (Expr) section.header().match().values().getFirst(), null, null);
    }

    /** A loop over {@code looped}; with {@code key} and {@code value}, each index and value is put in them first. */
    private void loop(Statement.Section section, Expr looped, Expr.Variable key, Expr.Variable value)
            throws CompileException {
        // As Skript's loops, what's certainly one value can't be looped. A variable may hold a queue, which can.
        if (!(looped instanceof Expr.Variable) && !"queue".equals(looped.type()) && surelySingle(looped)) {
            throw new CompileException(line, "Can't loop '" + skriptText(looped) + "' because it's only a single value");
        }
        int valuesSlot = nextSlot++;
        int indicesSlot = -1;
        int indexSlot = nextSlot++;
        int queueSlot = -1;
        if (isKeyed(looped, true)) {
            // A list variable, or anything keeping indices: loop-index is each value's index.
            indicesSlot = nextSlot++;
            keyed(looped, true);
            method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(KeyedValues.class), "of",
                    "(" + OBJECT_DESC + ")" + Type.getDescriptor(KeyedValues.class), false);
            method.visitInsn(DUP);
            method.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(KeyedValues.class), "values", "()" + OBJECTS, false);
            method.visitVarInsn(ASTORE, valuesSlot);
            method.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(KeyedValues.class), "keys",
                    "()[Ljava/lang/String;", false);
            method.visitVarInsn(ASTORE, indicesSlot);
        } else {
            // A queue is taken from as the loop goes, so what the loop adds to it is looped too.
            queueSlot = nextSlot++;
            expression(looped);
            convert(true);
            method.visitInsn(DUP);
            method.visitMethodInsn(INVOKESTATIC, VALUES, "loopedQueue", "(" + OBJECTS + ")Ljava/util/Queue;", false);
            method.visitVarInsn(ASTORE, queueSlot);
            method.visitMethodInsn(INVOKESTATIC, VALUES, "loopValues", "(" + OBJECTS + ")" + OBJECTS, false);
            method.visitVarInsn(ASTORE, valuesSlot);
        }
        method.visitInsn(ICONST_0);
        method.visitVarInsn(ISTORE, indexSlot);

        Label top = new Label();
        Label next = new Label();
        Label end = new Label();
        method.visitLabel(top);
        if (queueSlot >= 0) {
            method.visitVarInsn(ALOAD, valuesSlot);
            method.visitVarInsn(ILOAD, indexSlot);
            method.visitVarInsn(ALOAD, queueSlot);
            method.visitMethodInsn(INVOKESTATIC, VALUES, "takeFromQueue", "(" + OBJECTS + "ILjava/util/Queue;)" + OBJECTS, false);
            method.visitVarInsn(ASTORE, valuesSlot);
        }
        method.visitVarInsn(ILOAD, indexSlot);
        method.visitVarInsn(ALOAD, valuesSlot);
        method.visitInsn(ARRAYLENGTH);
        method.visitJumpInsn(IF_ICMPGE, end);
        Block block = new Block(BlockKind.LOOP, end, next, valuesSlot, indicesSlot, indexSlot, null, queueSlot);
        if (looped instanceof Expr.Syntax syntax && syntax.syntax().syntax().id().equals("ExprTestLoopPeeking")
                && syntax.syntax().pattern() == 0) {
            // Skript's test expression whose values can't be peeked at.
            unpeekable.put(block, "test loop peeking");
        }
        this.looped.put(block, looped);
        blocks.push(block);
        if (value != null) {
            variableStore(value);
            variableName(value);
            element(valuesSlot, indexSlot, 0);
            method.visitMethodInsn(INVOKEVIRTUAL, STORE, "set", "(" + STRING_DESC + OBJECT_DESC + ")V", false);
        }
        if (key != null) {
            variableStore(key);
            variableName(key);
            if (indicesSlot >= 0) {
                element(indicesSlot, indexSlot, 0);
            } else {
                // Values that don't come from a list are indexed 1, 2, 3...
                method.visitVarInsn(ILOAD, indexSlot);
                pushInt(1);
                method.visitInsn(IADD);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)" + STRING_DESC, false);
            }
            method.visitMethodInsn(INVOKEVIRTUAL, STORE, "set", "(" + STRING_DESC + OBJECT_DESC + ")V", false);
        }
        statements(section.body());
        blocks.pop();
        method.visitLabel(next);
        method.visitIincInsn(indexSlot, 1);
        method.visitJumpInsn(GOTO, top);
        method.visitLabel(end);
    }

    /**
     * A library section, like {@code spawn a pig at test-location:}: the section runs, then its code runs
     * once for each value it returned, as that section's event value.
     */
    private void librarySection(Statement.Section section) throws CompileException {
        ParsedSyntax header = section.header();
        int valuesSlot = nextSlot++;
        int indexSlot = nextSlot++;
        // On Folia, a section given an entity or location runs on the thread that owns it, as in 'run on', since
        // its code goes on with what it made there ('spawn a pig at X:' from an 'every' trigger, on the global
        // thread). Getting there and back are like waits, and only happen when this thread doesn't own it.
        int place = Regions.enabled() && !Regions.exempt(header.syntax().id()) ? subject(header) : -1;
        RunOn outer = runOns.peek();
        if (place >= 0) {
            int argumentsSlot = nextSlot++;
            int ownerSlot = nextSlot++;
            if (!(library.binding(header.syntax()) instanceof ElementFactory<?> factory)) {
                throw unsupported(header.syntax().id());
            }
            Object element = factory.create(info(header));
            co.xenastudios.neoskript.runtime.errors.ErrorSites.Site site = site(header, element.getClass());
            arguments(header);
            method.visitVarInsn(ASTORE, argumentsSlot);
            method.visitVarInsn(ALOAD, argumentsSlot);
            pushInt(place);
            method.visitInsn(AALOAD);
            method.visitMethodInsn(INVOKESTATIC, VALUES, "single", "(" + OBJECT_DESC + ")" + OBJECT_DESC, false);
            method.visitVarInsn(ASTORE, ownerSlot);
            RunOn there = new RunOn(ownerSlot, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()),
                    true, new int[] {argumentsSlot});
            there.outside().addAll(blocks);
            runOns.push(there);
            hop(ownerSlot);
            constant(regional(element, co.xenastudios.neoskript.api.element.Section.class, header, site),
                    co.xenastudios.neoskript.api.element.Section.class);
            method.visitVarInsn(ALOAD, CONTEXT_SLOT);
            method.visitVarInsn(ALOAD, argumentsSlot);
        } else {
            callThrough(header, co.xenastudios.neoskript.api.element.Section.class);
        }
        method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(co.xenastudios.neoskript.api.element.Section.class),
                "run", "(" + CONTEXT + OBJECTS + ")" + OBJECT_DESC, true);
        convert(true);
        method.visitVarInsn(ASTORE, valuesSlot);
        method.visitInsn(ICONST_0);
        method.visitVarInsn(ISTORE, indexSlot);

        Label top = new Label();
        Label next = new Label();
        Label end = new Label();
        method.visitLabel(top);
        method.visitVarInsn(ILOAD, indexSlot);
        method.visitVarInsn(ALOAD, valuesSlot);
        method.visitInsn(ARRAYLENGTH);
        method.visitJumpInsn(IF_ICMPGE, end);
        blocks.push(new Block(BlockKind.SECTION, end, next, valuesSlot, -1, indexSlot,
                library.sectionType(header.syntax().id())));
        try {
            statements(section.body());
        } finally {
            blocks.pop();
            if (place >= 0) {
                runOns.pop();
            }
        }
        method.visitLabel(next);
        method.visitIincInsn(indexSlot, 1);
        method.visitJumpInsn(GOTO, top);
        method.visitLabel(end);
        if (place >= 0) {
            // Back to the thread of the 'run on' around this one, or the trigger's own.
            hop(outer == null ? -1 : outer.slot());
        }
    }

    /** The expression in an effect's header that takes the section under it ({@code set X to a worldborder:}). */
    private Expr.Syntax sectionExpression(Statement.Section section) {
        if (section.header() == null || section.header().syntax().kind() != co.xenastudios.neoskript.frontend.registry.SyntaxKind.EFFECT) {
            return null;
        }
        for (Object value : section.header().match().values()) {
            if (value instanceof Expr.Syntax syntax && library.sectionExpressionType(syntax.syntax().syntax().id()) != null) {
                return syntax;
            }
        }
        return null;
    }

    /**
     * An effect whose expression takes a section: the expression is worked out, the section runs for its
     * value (which is the section's value, as {@code event-<type>}), then the effect runs with that value.
     */
    private void expressionSection(Statement.Section section) throws CompileException {
        Expr.Syntax made = sectionExpression(section);
        if (library.deferredSection(made.syntax().syntax().id())) {
            deferredSection(section, made);
            return;
        }
        expressionSection(section, made, () -> effect(section.header()));
    }

    /** The argument of a function call that takes the section under it, or {@code null}. */
    private Expr.Syntax callSectionExpression(Expr.FunctionCall call) {
        for (Expr argument : call.arguments()) {
            if (argument instanceof Expr.Syntax syntax && library.sectionExpressionType(syntax.syntax().syntax().id()) != null) {
                return syntax;
            }
        }
        return null;
    }

    /** The section of expression {@code made} runs for each of its values, then {@code then} uses them. */
    private void expressionSection(Statement.Section section, Expr.Syntax made, Body then) throws CompileException {
        int valuesSlot = nextSlot++;
        int indexSlot = nextSlot++;
        expression(made);
        convert(true);
        method.visitVarInsn(ASTORE, valuesSlot);
        method.visitInsn(ICONST_0);
        method.visitVarInsn(ISTORE, indexSlot);
        Label top = new Label();
        Label next = new Label();
        Label end = new Label();
        method.visitLabel(top);
        method.visitVarInsn(ILOAD, indexSlot);
        method.visitVarInsn(ALOAD, valuesSlot);
        method.visitInsn(ARRAYLENGTH);
        method.visitJumpInsn(IF_ICMPGE, end);
        blocks.push(new Block(BlockKind.SECTION, end, next, valuesSlot, -1, indexSlot,
                library.sectionExpressionType(made.syntax().syntax().id())));
        // The section's code is in an event of its own, as Skript loads it: the trigger's values aren't there.
        String sectionEvent = library.sectionEvent(made.syntax().syntax().id());
        List<Class<?>> outerClasses = eventClasses;
        String outerLabel = eventLabel;
        if (sectionEvent != null) {
            eventClasses = List.of(co.xenastudios.neoskript.runtime.SectionEvent.class);
            eventLabel = sectionEvent;
        }
        try {
            statements(section.body());
        } finally {
            eventClasses = outerClasses;
            eventLabel = outerLabel;
        }
        blocks.pop();
        method.visitLabel(next);
        method.visitIincInsn(indexSlot, 1);
        method.visitJumpInsn(GOTO, top);
        method.visitLabel(end);
        lineOf(section, section.line().key());
        precomputed.put(made, valuesSlot);
        try {
            then.emit();
        } finally {
            precomputed.remove(made);
        }
    }

    /**
     * An effect whose expression's section runs later ({@code set {_r} to a new runnable:}): the expression's
     * value is a {@link Runnable} that carries on from the section's first line with this trigger's frame, as
     * a wait does, and stops at the section's end.
     */
    private void deferredSection(Statement.Section section, Expr.Syntax made) throws CompileException {
        int valuesSlot = nextSlot++;
        Label after = new Label();
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        WaitPoint start = saveFrame();
        method.visitMethodInsn(INVOKESTATIC, VALUES, "section",
                "(" + Type.getDescriptor(Resumable.class) + CONTEXT + FRAME_DESC + ")" + OBJECT_DESC, false);
        method.visitVarInsn(ASTORE, valuesSlot);
        pushInt(1);
        method.visitTypeInsn(ANEWARRAY, OBJECT);
        method.visitInsn(DUP);
        method.visitInsn(ICONST_0);
        method.visitVarInsn(ALOAD, valuesSlot);
        method.visitInsn(AASTORE);
        method.visitVarInsn(ASTORE, valuesSlot);
        method.visitJumpInsn(GOTO, after);
        method.visitLabel(start.resume());
        statements(section.body());
        exitTrigger();
        method.visitLabel(after);
        lineOf(section, section.line().key());
        precomputed.put(made, valuesSlot);
        try {
            effect(section.header());
        } finally {
            precomputed.remove(made);
        }
    }

    /** The type a returnable section returns: {@code string} in {@code returnable string section}. */
    private String returnableType(ParsedSyntax header) throws CompileException {
        Object written = header.match().values().getFirst();
        String text = switch (written) {
            case Expr.Literal literal -> literal.text();
            case Expr.Unresolved unresolved -> unresolved.text();
            case null, default -> null;
        };
        TypeRef ref = text == null ? null : registry.resolveType(text.strip());
        if (ref == null) {
            throw unsupported("'" + header.match() + "'");
        }
        return ref.type().id();
    }

    /** The innermost returnable section {@code return} gives its values to, or {@code null} for the function's. */
    private Block returnHandler() {
        for (Block block : blocks) {
            if (block.kind() == BlockKind.RETURNABLE || block.kind() == BlockKind.RETURNABLE_PLURAL) {
                return block;
            }
        }
        return null;
    }

    /** {@code return X} in a returnable section: checked as Skript checks it, kept, and the section left. */
    private void returnFromSection(Block handler, Expr returned) throws CompileException {
        boolean plural = handler.kind() == BlockKind.RETURNABLE_PLURAL;
        co.xenastudios.neoskript.frontend.registry.TypeInfo type = registry.type(handler.valueType());
        String name = type == null ? handler.valueType() : type.name().toLowerCase(java.util.Locale.ROOT);
        String section = "returnable " + (plural ? "plural " : "") + name + " section";
        String text = returned.end() <= source.length() ? source.substring(returned.start(), returned.end()).strip() : "";
        if (returned.type() != null && !returned.type().equals(SyntaxRegistry.OBJECT)
                && !registry.isAssignable(returned.type(), handler.valueType())) {
            throw new CompileException(line, section + " is declared to return " + article(name) + " " + name + ", but "
                    + text + " is not of that type.");
        }
        if (!plural && co.xenastudios.neoskript.frontend.parse.Plurality.plural(returned, registry)) {
            throw new CompileException(line, section + " is defined to only return a single " + name
                    + ", but this return statement can return multiple values.");
        }
        expression(returned);
        Class<?> target = library.javaClass(handler.valueType());
        if (target == Long.class) {
            // As Skript's number to integer converter: 2.5 is 2.
            method.visitMethodInsn(INVOKESTATIC, VALUES, "wholeNumbers", "(" + OBJECT_DESC + ")" + OBJECT_DESC, false);
        } else if (target != null && Values.convertsTo(target)) {
            constant(target, Class.class);
            method.visitMethodInsn(INVOKESTATIC, VALUES, "convert", "(" + OBJECT_DESC + "Ljava/lang/Class;)" + OBJECT_DESC, false);
        }
        convert(plural);
        method.visitMethodInsn(INVOKESTATIC, VALUES, "returned", "(" + OBJECT_DESC + ")V", false);
        method.visitJumpInsn(GOTO, handler.end());
    }

    /** The innermost section whose value a left-out placeholder of these types takes, or {@code null}. */
    private Block sectionDefault(PlaceholderSpec spec) {
        for (String type : spec.types()) {
            TypeRef ref = registry.resolveType(type);
            Block section = ref == null ? null : sectionFor(ref.type().id());
            if (section != null && !ref.type().id().equals(SyntaxRegistry.OBJECT)) {
                return section;
            }
        }
        for (String type : spec.types()) {
            // In a section for entities, a narrower kind of entity is the section's, as Skript casts its entity
            // down: the shooter of what 'shoot a pig' shot.
            TypeRef ref = registry.resolveType(type);
            Block wider = ref == null || ref.type().id().equals(SyntaxRegistry.OBJECT) ? null : widerSection(ref.type().id());
            if (wider != null && wider.valueType().equals("entity")) {
                return wider;
            }
        }
        return null;
    }

    /**
     * Skript's warning for words with several types read where nothing says which: {@code set {_x} to unknown}
     * ('unknown' is a heal reason, a damage cause and more).
     */
    /**
     * How many function calls' arguments are being compiled. Only words that are an element's argument
     * themselves, not in a list or a function's arguments, get Skript's warning for words of several types, as
     * only they reach its elements unparsed.
     */
    private int callArguments;

    private void multipleTypesWarning(String words) {
        if (skriptOrder) {
            return;
        }
        for (String name : literalTypes) {
            TypeRef ref = registry.resolveType(name);
            if (ref != null && !ref.type().id().equals(SyntaxRegistry.OBJECT)) {
                return;
            }
        }
        List<String> types = library.namedTypesOf(words);
        if (types.size() <= 1) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (String id : types) {
            co.xenastudios.neoskript.frontend.registry.TypeInfo info = registry.type(id);
            names.add(info == null ? id : info.name().toLowerCase(java.util.Locale.ROOT));
        }
        String joined = String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
        warnings.accept(line, "'" + words + "' has multiple types (" + joined + "). Consider specifying which type to use: '"
                + words + " (" + names.getFirst() + ")'");
    }

    /** Whether code compiled apart is in a section around it whose values are of type {@code typeId}. */
    private boolean inOuterSection(String typeId) {
        for (String type : outerSections) {
            if (type.equals(typeId) || registry.isSubtype(type, typeId) || registry.isSubtype(typeId, type)) {
                return true;
            }
        }
        return false;
    }

    /** The innermost library section whose values are of a type {@code typeId} is a kind of, or {@code null}. */
    private Block widerSection(String typeId) {
        for (Block block : blocks) {
            if (block.kind() == BlockKind.SECTION && block.valueType() != null && !block.valueType().equals(SyntaxRegistry.OBJECT)
                    && registry.isSubtype(typeId, block.valueType())) {
                return block;
            }
        }
        return null;
    }

    /** The innermost library section whose values are of type {@code typeId}, or {@code null}. */
    private Block sectionFor(String typeId) {
        for (Block block : blocks) {
            if (block.kind() == BlockKind.SECTION
                    && (block.valueType().equals(typeId) || registry.isSubtype(block.valueType(), typeId))) {
                return block;
            }
        }
        return null;
    }

    /** {@code [do] while <condition>:}. */
    private void whileLoop(Statement.Section section) throws CompileException {
        if (!(section.header().match().regexValues().getFirst() instanceof ParsedSyntax condition)) {
            throw unsupported("'" + section.line().key() + "'");
        }
        boolean doWhile = section.header().match().hasTag("do");
        // How many times the body has run, less one, for loop-iteration: Skript's while loops count too.
        int counterSlot = nextSlot++;
        method.visitInsn(ICONST_M1);
        method.visitVarInsn(ISTORE, counterSlot);
        Label top = new Label();
        Label next = new Label();
        Label end = new Label();
        if (doWhile) {
            Label body = new Label();
            method.visitJumpInsn(GOTO, body);
            method.visitLabel(next);
            condition(condition);
            method.visitJumpInsn(IFEQ, end);
            method.visitLabel(body);
        } else {
            method.visitLabel(next);
            condition(condition);
            method.visitJumpInsn(IFEQ, end);
        }
        method.visitLabel(top);
        method.visitIincInsn(counterSlot, 1);
        blocks.push(new Block(BlockKind.WHILE, end, next, -1, -1, counterSlot));
        statements(section.body());
        blocks.pop();
        method.visitJumpInsn(GOTO, next);
        method.visitLabel(end);
    }

    // ---------------------------------------------------------------- effects

    private void effect(ParsedSyntax effect) throws CompileException {
        if (library.binding(effect.syntax()) instanceof ElementFactory<?> factory
                && factory.create(info(effect))
                        instanceof Update update) {
            update(effect, update);
            return;
        }
        if (!(library.binding(effect.syntax()) instanceof Library.Core core)) {
            callThrough(effect, Effect.class);
            method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Effect.class), "execute",
                    "(" + CONTEXT + OBJECTS + ")V", true);
            return;
        }
        switch (core.id()) {
            case CoreSyntax.EXIT -> exit(effect);
            case CoreSyntax.CONTINUE -> {
                Block loop = effect.pattern() == 0 ? innermostLoop() : loopNumber(regexNumber(effect));
                leaving(loop);
                method.visitJumpInsn(GOTO, loop.next());
            }
            case CoreSyntax.RETURN -> {
                Block handler = returnHandler();
                if (handler != null) {
                    returnFromSection(handler, (Expr) effect.match().values().getFirst());
                    return;
                }
                if (function == null || function.returnType() == null) {
                    throw new CompileException(line, function == null
                            ? "The return statement cannot be used here"
                            : "function " + function.name() + " (simple event) doesn't return any value. Please use"
                                    + " 'stop' or 'exit' if you want to stop the trigger.");
                }
                Expr returned = (Expr) effect.match().values().getFirst();
                List<String> outerLiteralTypes = literalTypes;
                // Words returned are read as the function's return type: 'return unknown' is a damage cause there.
                literalTypes = List.of(function.returnType());
                try {
                    if (function.returnsPlural() && isKeyed(returned, true)) {
                        // A list keeps its indices, for 'keyed f()'; other callers get the values alone.
                        keyed(returned, true);
                    } else {
                        expression(returned);
                        convert(function.returnsPlural());
                    }
                } finally {
                    literalTypes = outerLiteralTypes;
                }
                method.visitInsn(ARETURN);
            }
            case CoreSyntax.CHANGE -> change(effect);
            case CoreSyntax.SUGGESTION_FILTERING -> {
                suggestionsOnly(effect);
                if (!(effect.match().values().getFirst() instanceof Expr.Syntax suggestions)
                        || !isCore(suggestions.syntax(), CoreSyntax.COMMAND_SUGGESTIONS)) {
                    throw new CompileException(line, "It is only possible to change the filtering mode of suggestions!"
                            + " Is the suggestions expression written correctly?");
                }
                int argument = suggestionsArgument(suggestions);
                method.visitVarInsn(ALOAD, CONTEXT_SLOT);
                pushInt(argument);
                pushInt(effect.pattern());
                method.visitMethodInsn(INVOKESTATIC, SUGGESTION_EVENT, "filtering", "(" + CONTEXT + "II)V", false);
            }
            case CoreSyntax.SORT -> {
                Expr.Variable list = listVariable(effect, "Only list variables can be sorted");
                Object key = effect.match().regexValues().isEmpty() ? null : effect.match().regexValues().getFirst();
                if (key instanceof Expr keyExpr) {
                    // 'sort {_x::*} by {_levels::%input%}': by each value's key.
                    boolean descending = effect.match().hasTag("descending");
                    setTo(list, () -> sortedByKeys(list, keyExpr, descending));
                } else {
                    variableStore(list);
                    variableName(list);
                    method.visitInsn(effect.match().hasTag("descending") ? ICONST_1 : ICONST_0);
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "sortList", "(" + STRING_DESC + "Z)V", false);
                }
            }
            case CoreSyntax.COPY -> {
                Expr from = (Expr) effect.match().values().get(0);
                Expr to = (Expr) effect.match().values().get(1);
                if (from instanceof Expr.Variable source && source.list() && to instanceof Expr.Variable target && target.list()
                        && source.local() == target.local()) {
                    // Within one store: the whole list, nested lists and indices included.
                    variableStore(target);
                    variableName(source);
                    variableName(target);
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "copyList", "(" + STRING_DESC + STRING_DESC + ")V", false);
                } else {
                    setTo(to, () -> expression(from));
                }
            }
            case CoreSyntax.TRANSFORM_EFFECT -> {
                Expr target = (Expr) effect.match().values().getFirst();
                Expr mapping = (Expr) effect.match().regexValues().getFirst();
                if (target instanceof Expr.Variable variable && variable.list()) {
                    // Each value to one keeps the indices; to several, they're numbered again.
                    int[] list = listValues(target);
                    int results = newList();
                    forEachInput(list, 0, -1, (frame, next) -> {
                        method.visitVarInsn(ALOAD, results);
                        expression(mapping);
                        convert(true);
                        method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(" + OBJECT_DESC + ")Z", true);
                        method.visitInsn(POP);
                    });
                    variableStore(variable);
                    variableName(variable);
                    method.visitVarInsn(ALOAD, results);
                    if (list[1] >= 0) {
                        method.visitVarInsn(ALOAD, list[1]);
                    } else {
                        method.visitInsn(ACONST_NULL);
                    }
                    method.visitMethodInsn(INVOKESTATIC, VALUES, "transformed", "(Ljava/util/List;[" + STRING_DESC + ")"
                            + OBJECT_DESC, false);
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "setListKeyed", "(" + STRING_DESC + OBJECT_DESC + ")V", false);
                    break;
                }
                setTo(target, () -> transform(target, mapping));
            }
            case CoreSyntax.DO_IF -> {
                if (!(effect.match().regexValues().get(1) instanceof ParsedSyntax condition)) {
                    throw unsupported("'" + source + "'");
                }
                Label skip = new Label();
                try {
                    condition(condition);
                } catch (CompileException e) {
                    // Skript's 'do if' sets its condition up on its own: words in it nothing reads are about it.
                    int[] span = effect.match().regexSpans().get(1);
                    if (e.getMessage() != null && e.getMessage().equals("Can't understand this condition/effect: "
                            + source.strip()) && span[1] <= source.length()) {
                        throw new CompileException(line, "Can't understand this condition: "
                                + source.substring(span[0], span[1]).strip());
                    }
                    throw e;
                }
                method.visitJumpInsn(IFEQ, skip);
                Object inner = effect.match().regexValues().getFirst();
                if (inner instanceof ParsedSyntax innerEffect) {
                    effect(innerEffect);
                } else if (inner instanceof Expr.FunctionCall call) {
                    call(call);
                    method.visitInsn(POP);
                } else {
                    throw unsupported("'" + source + "'");
                }
                method.visitLabel(skip);
            }
            case CoreSyntax.ASSERT -> assertion(effect);
            case CoreSyntax.ASSERT_UNPARSED -> {}
            case CoreSyntax.DELAY -> waitFor(effect);
            case CoreSyntax.SUPPRESS_WARNINGS, CoreSyntax.SUPPRESS_HINTS -> {}
            default -> throw unsupported(core.id());
        }
    }

    /** Skript's test {@code assert}: records a failure if the condition isn't as expected. */
    private void assertion(ParsedSyntax effect) throws CompileException {
        Object checked = effect.match().regexValues().getFirst();
        if (!(checked instanceof ParsedSyntax condition)
                || condition.syntax().kind() != co.xenastudios.neoskript.frontend.registry.SyntaxKind.CONDITION) {
            throw unsupported("asserting that code other than a condition fails");
        }
        boolean expected = !effect.match().hasTag("fail");
        if (!expected && !compiles(condition)) {
            // As in Skript, a condition expected to fail that can't even be set up has failed.
            return;
        }
        Label passed = new Label();
        condition(condition);
        method.visitJumpInsn(expected ? IFNE : IFEQ, passed);
        method.visitLdcInsn(constantsScript);
        pushInt(line);
        if (effect.match().values().getFirst() instanceof Expr message) {
            expression(message);
            method.visitMethodInsn(INVOKESTATIC, VALUES, "text", "(" + OBJECT_DESC + ")" + STRING_DESC, false);
        } else {
            method.visitLdcInsn("Assertion failed: " + source);
        }
        method.visitMethodInsn(INVOKESTATIC, TEST_RUN, "fail", "(" + STRING_DESC + "I" + STRING_DESC + ")V", false);
        method.visitLabel(passed);
    }

    /** Whether {@code condition} compiles, tried without writing anything. */
    private boolean compiles(ParsedSyntax condition) {
        org.objectweb.asm.MethodVisitor real = method;
        int slots = nextSlot;
        method = new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {};
        try {
            condition(condition);
            return true;
        } catch (CompileException | co.xenastudios.neoskript.api.element.SyntaxError e) {
            return false;
        } finally {
            method = real;
            nextSlot = slots;
        }
    }

    private void exit(ParsedSyntax effect) throws CompileException {
        if (effect.pattern() == 0) {
            exitTrigger();
            return;
        }
        BlockKind kind = effect.match().hasTag("loop") ? BlockKind.LOOP
                : effect.match().hasTag("conditional") ? BlockKind.CONDITIONAL : null;
        String names = kind == BlockKind.LOOP ? "loops" : kind == BlockKind.CONDITIONAL ? "conditionals" : "sections";
        List<Block> matching = new ArrayList<>();
        for (Block block : blocks) { // innermost first
            if (kind == null || block.kind() == kind || (kind == BlockKind.LOOP && block.kind() == BlockKind.WHILE)) {
                matching.add(block);
            }
        }
        int count = effect.pattern() == 1 ? 1 : effect.pattern() == 3 ? matching.size()
                : Integer.parseInt(((java.util.regex.MatchResult) effect.match().regexValues().getFirst()).group()
                        .replace("_", ""));
        if (matching.isEmpty()) {
            throw new CompileException(line, "Can't stop any " + names + " as there are no " + names + " present");
        }
        if (count > matching.size()) {
            throw new CompileException(line, "Can't stop " + count + " " + names + " as there are only "
                    + matching.size() + " " + names + " present");
        }
        leaving(matching.get(count - 1));
        method.visitJumpInsn(GOTO, matching.get(count - 1).end());
    }

    /**
     * Fails if jumping to {@code target} would leave a {@code run on} section, whose end must run. Leaving only
     * library sections that run where their entity or location is goes back to the thread {@code target} runs on
     * first.
     */
    private void leaving(Block target) throws CompileException {
        boolean left = false;
        int thread = -1;
        for (RunOn runOn : runOns) {
            if (!runOn.outside().contains(target)) {
                thread = runOn.slot();
                break;
            }
            if (!runOn.implicit()) {
                throw new CompileException(line, "Can't leave a 'run on' section this way, as the rest of the trigger"
                        + " runs on another thread. Use 'exit section' to leave it.");
            }
            left = true;
        }
        if (left) {
            hop(thread);
        }
    }

    /**
     * {@code run on %entity/location%:} (NeoSkript only): runs its code on the thread that owns the entity or
     * location, then the trigger carries on on its own thread after it. On Paper, where one thread owns everything,
     * the code just runs. On Folia, getting there and back are like waits: the trigger stops, and carries on on
     * the other thread, at once if this thread already owns it, else on the next tick. With nothing to run on, the
     * code doesn't run.
     */
    private void runOn(Statement.Section section) throws CompileException {
        int slot = nextSlot++;
        expression((Expr) section.header().match().values().getFirst());
        method.visitMethodInsn(INVOKESTATIC, VALUES, "single", "(" + OBJECT_DESC + ")" + OBJECT_DESC, false);
        method.visitVarInsn(ASTORE, slot);
        Label skip = new Label();
        method.visitVarInsn(ALOAD, slot);
        method.visitJumpInsn(IFNULL, skip);
        Label end = new Label();
        // Paper keeps the same rules, so a script that loads there loads on Folia too.
        boolean hops = Regions.enabled();
        RunOn outer = runOns.peek();
        runOns.push(new RunOn(slot, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())));
        runOns.peek().outside().addAll(blocks);
        try {
            if (hops) {
                hop(slot);
            }
            block(BlockKind.CONDITIONAL, end, section.body());
            method.visitLabel(end);
        } finally {
            runOns.pop();
        }
        if (hops) {
            // Back to the thread of the 'run on' around this one, or the trigger's own.
            hop(outer == null ? -1 : outer.slot());
        }
        method.visitLabel(skip);
    }

    /** Carries on on the thread that owns what's in {@code slot} (or with -1, the trigger's own): a wait, on Folia. */
    private void hop(int slot) {
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        WaitPoint wait = saveFrame();
        if (slot < 0) {
            method.visitInsn(ACONST_NULL);
        } else {
            method.visitVarInsn(ALOAD, slot);
        }
        method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Waits.class), "hop",
                "(" + Type.getDescriptor(Resumable.class) + CONTEXT + FRAME_DESC + OBJECT_DESC + ")Z", false);
        // Not scheduled: this thread owns it already, so the trigger carries straight on.
        method.visitJumpInsn(IFEQ, wait.resume());
        exitTrigger();
        method.visitLabel(wait.resume());
    }

    private static int regexNumber(ParsedSyntax syntax) {
        return Integer.parseInt(((java.util.regex.MatchResult) syntax.match().regexValues().getFirst()).group());
    }

    private Block innermostLoop() throws CompileException {
        for (Block block : blocks) {
            if (block.kind() != BlockKind.CONDITIONAL) {
                return block;
            }
        }
        throw new CompileException(line, "There's no loop here");
    }

    /** The {@code n}th loop, counting from the outermost. */
    private Block loopNumber(int n) throws CompileException {
        List<Block> loops = loops();
        if (n < 1 || n > loops.size()) {
            throw new CompileException(line, "There's no loop number " + n + " here");
        }
        return loops.get(n - 1);
    }

    /** The loops around the current statement, outermost first. */
    private List<Block> loops() {
        List<Block> loops = new ArrayList<>();
        blocks.descendingIterator().forEachRemaining(block -> {
            if (block.kind() != BlockKind.CONDITIONAL) {
                loops.add(block);
            }
        });
        return loops;
    }

    /** {@code set}, {@code add}, {@code remove} and {@code delete} on variables. */
    private void change(ParsedSyntax effect) throws CompileException {
        CoreSyntax.ChangeMode mode = CoreSyntax.ChangeMode.values()[effect.pattern()];
        Expr outerChanger = changer;
        changer = mode.value >= 0 && effect.match().values().get(mode.value) instanceof Expr value ? value : null;
        try {
            // Skript's parser sets up every value before the change effect checks what's given: an event value that
            // isn't there is the line's error first, the last written first ('set ... of player's tool to 10').
            List<Object> values = effect.match().values();
            for (int i = values.size() - 1; i >= 0; i--) {
                checkEventValues(values.get(i));
            }
            changeAll(effect, mode);
        } finally {
            changer = outerChanger;
        }
    }

    /** Fails as Skript does if {@code value} has an event value the trigger's event doesn't have. */
    private void checkEventValues(Object value) throws CompileException {
        switch (value) {
            case Expr.Syntax syntax when (isCore(syntax.syntax(), CoreSyntax.EVENT_VALUE)
                    || isCore(syntax.syntax(), CoreSyntax.EVENT_VALUE_ENTITY))
                    && syntax.syntax().match().regexValues().getFirst() instanceof TypeRef ref -> {
                String type = ref.type().id();
                if (sectionFor(type) == null && !inOuterSection(type) && widerSection(type) == null) {
                    lookup(syntax, ref, 0);
                }
            }
            case Expr.Syntax syntax -> {
                List<Object> inner = syntax.syntax().match().values();
                for (int i = inner.size() - 1; i >= 0; i--) {
                    checkEventValues(inner.get(i));
                }
            }
            case Expr.ListOf list -> {
                for (Expr item : list.items()) {
                    checkEventValues(item);
                }
            }
            case null, default -> { }
        }
    }

    private void changeAll(ParsedSyntax effect, CoreSyntax.ChangeMode mode) throws CompileException {
        Object target = effect.match().values().get(mode.target);
        // 'add 1 to {_a::*} and {_b::*}': each variable is changed.
        if (target instanceof Expr.ListOf list && list.items().stream().allMatch(Expr.Variable.class::isInstance)) {
            if (!list.and() && list.items().size() > 1) {
                // 'set {_a} or {_b} to 1': one of them, chosen at random, as in Skript.
                Label end = new Label();
                Label[] cases = new Label[list.items().size()];
                for (int i = 0; i < cases.length; i++) {
                    cases[i] = new Label();
                }
                pushInt(cases.length);
                method.visitMethodInsn(INVOKESTATIC, VALUES, "randomIndex", "(I)I", false);
                method.visitTableSwitchInsn(0, cases.length - 1, end, cases);
                for (int i = 0; i < cases.length; i++) {
                    method.visitLabel(cases[i]);
                    change(effect, mode, list.items().get(i));
                    method.visitJumpInsn(GOTO, end);
                }
                method.visitLabel(end);
                return;
            }
            for (Expr item : list.items()) {
                change(effect, mode, item);
            }
            return;
        }
        change(effect, mode, target);
    }

    private void change(ParsedSyntax effect, CoreSyntax.ChangeMode mode, Object target) throws CompileException {
        if (mode == CoreSyntax.ChangeMode.SET && target instanceof Expr.Variable variable && variable.list()) {
            // 'set {_x::*} to "1, 2" parsed as "%integers%"': each placeholder's values stay a sublist, as in Skript.
            Object changer = effect.match().values().get(mode.value);
            for (Object each : changer instanceof Expr.ListOf list ? list.items() : List.of(changer)) {
                if (each instanceof Expr.Syntax parse && isCore(parse.syntax(), CoreSyntax.PARSE_PATTERN)) {
                    groupedParses.add(parse);
                }
            }
        }
        if (mode == CoreSyntax.ChangeMode.SET && target instanceof Expr.Syntax whether
                && isCore(whether.syntax(), CoreSyntax.WHETHER)) {
            setCondition(effect, whether);
            return;
        }
        if (target instanceof Expr.Syntax syntax && (!(library.binding(syntax.syntax().syntax()) instanceof Library.Core)
                || isCore(syntax.syntax(), CoreSyntax.TIME_STATE) && syntax.syntax().match().values().getFirst() instanceof Expr.Syntax inner
                        && !(library.binding(inner.syntax().syntax()) instanceof Library.Core))) {
            changeExpression(effect, mode, syntax);
            return;
        }
        if (target instanceof Expr.Syntax syntax && isCore(syntax.syntax(), CoreSyntax.COMMAND_SUGGESTIONS)) {
            changeSuggestions(effect, mode, syntax);
            return;
        }
        if (!(target instanceof Expr.Variable variable)) {
            if (target instanceof Expr expr && changeTypeValues(effect, mode, expr, true)) {
                return;
            }
            throw unsupported("changing anything but variables");
        }
        saveWarning(effect, mode, variable);
        variableStore(variable);
        variableName(variable);
        if (mode == CoreSyntax.ChangeMode.SET && variable.list() && isKeyed((Expr) effect.match().values().get(mode.value))) {
            // 'set {_b::*} to keyed {_a::*}': the values keep their indices.
            keyed((Expr) effect.match().values().get(mode.value));
            method.visitMethodInsn(INVOKEVIRTUAL, STORE, "setListKeyed", "(" + STRING_DESC + OBJECT_DESC + ")V", false);
            return;
        }
        if (mode != CoreSyntax.ChangeMode.DELETE) {
            expression((Expr) effect.match().values().get(mode.value));
        }
        String nameValue = "(" + STRING_DESC + OBJECT_DESC + ")V";
        String nameValues = "(" + STRING_DESC + OBJECTS + ")V";
        boolean list = variable.list();
        switch (mode) {
            case SET -> {
                convert(list);
                method.visitMethodInsn(INVOKEVIRTUAL, STORE, list ? "setList" : "set", list ? nameValues : nameValue, false);
            }
            case ADD, INCREASE, GIVE -> {
                convert(true);
                method.visitMethodInsn(INVOKEVIRTUAL, STORE, list ? "addToList" : "add", nameValues, false);
            }
            case REMOVE, REMOVE_ALL, REDUCE -> {
                convert(true);
                if (list) {
                    method.visitInsn(mode == CoreSyntax.ChangeMode.REMOVE_ALL ? ICONST_1 : ICONST_0);
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "removeFromList", "(" + STRING_DESC + OBJECTS + "Z)V",
                            false);
                } else {
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE,
                            mode == CoreSyntax.ChangeMode.REMOVE_ALL ? "subtractAll" : "subtract", nameValues, false);
                }
            }
            case DELETE -> {
                if (list) {
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "deleteList", "(" + STRING_DESC + ")V", false);
                } else {
                    method.visitInsn(ACONST_NULL);
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "set", nameValue, false);
                }
            }
        }
    }

    /**
     * Skript's warning where a global variable is set (or a global list added) to values of a type it can't save,
     * as its {@code EffChange} gives it.
     */
    private void saveWarning(ParsedSyntax effect, CoreSyntax.ChangeMode mode, Expr.Variable variable) {
        if (!(mode == CoreSyntax.ChangeMode.SET || variable.list() && (mode == CoreSyntax.ChangeMode.ADD || mode == CoreSyntax.ChangeMode.GIVE))
                || variable.local() || variable.name().startsWith("-")
                || effect.match().values().get(mode.target) != variable
                || !(effect.match().values().get(mode.value) instanceof Expr changer)
                || changer instanceof Expr.Variable) {
            return;
        }
        String type = changer.type();
        if (changer instanceof Expr.Unresolved words) {
            // Words like 'red' are the value they read as: a color.
            Object value = library.literal(words.text());
            type = value == null ? type : library.superType(value.getClass());
        }
        String named = library.unsavable(type);
        if (named != null) {
            warn("save", named + " cannot be saved. That is, the contents of the variable "
                    + source.substring(variable.start(), variable.end()) + " will be lost when the server stops.");
        }
    }

    /** An {@link Update}: sets its target placeholder to what it works out. */
    private void update(ParsedSyntax effect, Update update) throws CompileException {
        if (!(effect.match().values().get(update.target()) instanceof Expr target)) {
            throw unsupported("'" + source + "'");
        }
        if (target instanceof Expr.Variable list && list.list()) {
            // The results go back at the indices they came from: 'replace "a" in {_x::*} with "b"' keeps them.
            int keys = nextSlot++;
            variableStore(list);
            variableName(list);
            method.visitMethodInsn(INVOKEVIRTUAL, STORE, "indices", "(" + STRING_DESC + ")[" + STRING_DESC, false);
            method.visitVarInsn(ASTORE, keys);
            variableStore(list);
            variableName(list);
            method.visitVarInsn(ALOAD, keys);
            site(effect, update.getClass());
            constant(update, Update.class);
            method.visitVarInsn(ALOAD, CONTEXT_SLOT);
            arguments(effect);
            method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Update.class), "update",
                    "(" + CONTEXT + OBJECTS + ")" + OBJECT_DESC, true);
            PlaceholderSpec spec = placeholderSpecs(effect.syntax().patterns().get(effect.pattern()))[update.target()];
            Class<?> only = strictClass(spec);
            if (only == null) {
                method.visitInsn(ACONST_NULL);
            } else {
                constant(only, Class.class);
            }
            method.visitMethodInsn(INVOKEVIRTUAL, STORE, "updateList",
                    "(" + STRING_DESC + "[" + STRING_DESC + OBJECT_DESC + "Ljava/lang/Class;)V", false);
            return;
        }
        if (target instanceof Expr.ListOf list
                && list.items().stream().allMatch(item -> item instanceof Expr.Variable variable && !variable.list())) {
            // 'replace "1" in {_a}, {_b} and {_c} with "x"': each result goes back to the variable it came from.
            int results = nextSlot++;
            site(effect, update.getClass());
            constant(update, Update.class);
            method.visitVarInsn(ALOAD, CONTEXT_SLOT);
            arguments(effect);
            method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Update.class), "update",
                    "(" + CONTEXT + OBJECTS + ")" + OBJECT_DESC, true);
            method.visitMethodInsn(INVOKESTATIC, VALUES, "array", "(" + OBJECT_DESC + ")" + OBJECTS, false);
            method.visitVarInsn(ASTORE, results);
            for (int i = 0; i < list.items().size(); i++) {
                Expr.Variable variable = (Expr.Variable) list.items().get(i);
                variableStore(variable);
                variableName(variable);
                method.visitVarInsn(ALOAD, results);
                pushInt(i);
                pushInt(0);
                method.visitMethodInsn(INVOKESTATIC, VALUES, "element", "(" + OBJECTS + "II)" + OBJECT_DESC, false);
                method.visitMethodInsn(INVOKEVIRTUAL, STORE, "set", "(" + STRING_DESC + OBJECT_DESC + ")V", false);
            }
            return;
        }
        Body updated = () -> {
            site(effect, update.getClass());
            constant(update, Update.class);
            method.visitVarInsn(ALOAD, CONTEXT_SLOT);
            arguments(effect);
            method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Update.class), "update",
                    "(" + CONTEXT + OBJECTS + ")" + OBJECT_DESC, true);
        };
        if (target instanceof Expr.Syntax whether && isCore(whether.syntax(), CoreSyntax.WHETHER)) {
            // 'toggle whether the player can fly': the condition is made what the update works out.
            setCondition(whether, updated);
            return;
        }
        if (!update.setRequired() && !settable(target)) {
            // The effect works on the values themselves where its target can't be set: 'zombify X'.
            updated.emit();
            method.visitInsn(POP);
            return;
        }
        setTo(target, updated);
    }

    /** Whether {@code target} can be set: variables, or an expression itself or through its values' type. */
    private boolean settable(Expr target) {
        if (target instanceof Expr.Variable) {
            return true;
        }
        if (target instanceof Expr.ListOf list) {
            return list.items().stream().allMatch(item -> item instanceof Expr.Variable variable && !variable.list());
        }
        if (!(target instanceof Expr.Syntax syntax)
                || !(library.binding(syntax.syntax().syntax()) instanceof ElementFactory<?> factory)) {
            return false;
        }
        Object element = factory.create(info(syntax.syntax()));
        if (changesItself(element)) {
            return ((Changeable) element).acceptChange(Changeable.Mode.SET) != null;
        }
        TypeChange typed = typeChange(target, Changeable.Mode.SET);
        return typed != null && typed.accepted() != null;
    }

    /** Sets {@code target}, a variable or a {@link Changeable} expression, to the value {@code value} pushes. */
    private void setTo(Expr target, Body value) throws CompileException {
        if (target instanceof Expr.Variable variable) {
            variableStore(variable);
            variableName(variable);
            value.emit();
            convert(variable.list());
            method.visitMethodInsn(INVOKEVIRTUAL, STORE, variable.list() ? "setList" : "set",
                    "(" + STRING_DESC + (variable.list() ? OBJECTS : OBJECT_DESC) + ")V", false);
            return;
        }
        if (!(target instanceof Expr.Syntax syntax)
                || !(library.binding(syntax.syntax().syntax()) instanceof ElementFactory<?> factory)) {
            throw new CompileException(line, source.substring(target.start(), target.end()) + " can't be set to anything");
        }
        ParsedSyntax parsed = syntax.syntax();
        Object element = factory.create(info(parsed));
        Class<?>[] accepted = element instanceof Changeable changeable ? changeable.acceptChange(Changeable.Mode.SET) : null;
        if (!changesItself(element)) {
            // Its values are set through their type's changer, each to what it became: 'enchant the player's tool'.
            TypeChange typed = typeChange(target, Changeable.Mode.SET);
            if (typed == null || typed.accepted() == null) {
                throw new CompileException(line, cannotChange(typed == null
                        ? source.substring(target.start(), target.end()) : typed.what(), Changeable.Mode.SET));
            }
            expression(target);
            value.emit();
            method.visitMethodInsn(INVOKESTATIC, VALUES, "setEach", "(" + OBJECT_DESC + OBJECT_DESC + ")V", false);
            return;
        }
        if (accepted == null) {
            throw new CompileException(line, source.substring(target.start(), target.end()) + " can't be set to anything");
        }
        constant(regional(element, Changeable.class, parsed, site(parsed, element.getClass())), Changeable.class);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        arguments(parsed);
        value.emit();
        constant(accepted, Class[].class);
        method.visitMethodInsn(INVOKESTATIC, VALUES, "narrow", "(" + OBJECT_DESC + "[Ljava/lang/Class;)" + OBJECTS, false);
        constant(Changeable.Mode.SET, Changeable.Mode.class);
        method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Changeable.class), "change",
                "(" + CONTEXT + OBJECTS + OBJECTS + Type.getDescriptor(Changeable.Mode.class) + ")V", true);
    }

    /** {@code set whether {_pig} can breed to true}: makes a {@link SettableCondition} true or false. */
    private void setCondition(ParsedSyntax effect, Expr.Syntax whether) throws CompileException {
        setCondition(whether, () -> expression((Expr) effect.match().values().get(1)));
    }

    /** Makes a {@link SettableCondition} what {@code value} pushes. */
    private void setCondition(Expr.Syntax whether, Body value) throws CompileException {
        if (!(whether.syntax().match().regexValues().getFirst() instanceof ParsedSyntax condition)
                || !(library.binding(condition.syntax()) instanceof ElementFactory<?> factory)) {
            throw new CompileException(line, "'" + source.substring(whether.start(), whether.end()) + "' can't be set");
        }
        Object element = factory.create(info(condition));
        if (!(element instanceof SettableCondition)) {
            throw new CompileException(line, "'" + source.substring(whether.start(), whether.end()) + "' can't be set");
        }
        constant(regional(element, SettableCondition.class, condition, site(condition, element.getClass())),
                SettableCondition.class);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        arguments(condition);
        value.emit();
        method.visitMethodInsn(INVOKESTATIC, VALUES, "isTrue", "(" + OBJECT_DESC + ")Z", false);
        method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(SettableCondition.class), "set",
                "(" + CONTEXT + OBJECTS + "Z)V", true);
    }

    private static Changeable.Mode changeMode(ParsedSyntax effect, CoreSyntax.ChangeMode mode) {
        return switch (mode) {
            case SET -> Changeable.Mode.SET;
            case ADD, INCREASE, GIVE -> Changeable.Mode.ADD;
            case REMOVE, REDUCE -> Changeable.Mode.REMOVE;
            case REMOVE_ALL -> Changeable.Mode.REMOVE_ALL;
            case DELETE -> effect.match().hasTag("reset") ? Changeable.Mode.RESET : Changeable.Mode.DELETE;
        };
    }

    /**
     * Whether {@code element} changes itself, as Skript's expressions that override {@code acceptChange}. One
     * that can't be changed in any way is changed through its values' type, as Skript's others are.
     */
    private static boolean changesItself(Object element) {
        if (!(element instanceof Changeable changeable)) {
            return false;
        }
        for (Changeable.Mode mode : Changeable.Mode.values()) {
            if (changeable.acceptChange(mode) != null) {
                return true;
            }
        }
        return false;
    }

    /** Skript's error for an expression that can't be changed in {@code mode}, written as {@code what}. */
    private static String cannotChange(String what, Changeable.Mode mode) {
        return what + switch (mode) {
            case SET -> " can't be set to anything";
            case ADD -> " can't have anything added to it";
            case REMOVE, REMOVE_ALL -> " can't have anything removed from it";
            case DELETE -> " can't be deleted";
            case RESET -> " can't be reset";
        };
    }

    /**
     * What the type of {@code target}'s values takes for {@code mode} through its changer, as Skript's
     * expressions that don't change themselves do (a block, an entity, a slot), with how its errors name it.
     * {@code null} if no type above it says what its values take.
     */
    private TypeChange typeChange(Expr target, Changeable.Mode mode) {
        String type = valueType(target);
        if (type == null || type.equals(SyntaxRegistry.OBJECT)) {
            return null;
        }
        Class<?> owner = library.changerOwner(library.javaClass(type));
        if (owner == null) {
            return null;
        }
        TypeRef ref = registry.resolveType(type);
        String name = ref == null ? type : ref.type().name();
        return new TypeChange(library.changerAccepts(owner, mode), article(name) + " " + name);
    }

    /** The type of {@code expr}'s values, as far as is known before it runs: {@code loop-block} is a block. */
    private String valueType(Expr expr) {
        if (expr instanceof Expr.Syntax state && isCore(state.syntax(), CoreSyntax.TIME_STATE)
                && state.syntax().match().values().getFirst() instanceof Expr inner) {
            // 'past tool': what's in that state is of its type.
            return valueType(inner);
        }
        if (expr instanceof Expr.Syntax loop && isCore(loop.syntax(), CoreSyntax.LOOP_VALUE)
                && loop.syntax().match().regexValues().getFirst() instanceof java.util.regex.MatchResult named) {
            Matcher matcher = LOOP_VALUE.matcher(named.group().strip().toLowerCase(Locale.ROOT));
            TypeRef ref = matcher.matches() ? registry.resolveType(matcher.group(1)) : null;
            if (ref != null) {
                return ref.type().id();
            }
        }
        return expr.type();
    }

    /** What a type's changer takes, {@code null} if it can't be changed that way, and its values as errors name them. */
    private record TypeChange(Class<?>[] accepted, String what) {}

    /**
     * Changes {@code target}'s values themselves, if its type's changer can, checking what's given as Skript
     * does: {@code remove 2 pickaxes from the victim}. Whether it was compiled; {@code false} leaves it to the
     * caller, when nothing changes values of its type. Where no type says what its values take, with
     * {@code lax} they're changed if anything might change them, and checked when it runs; an element that
     * says it can't be changed at all isn't.
     */
    private boolean changeTypeValues(ParsedSyntax effect, CoreSyntax.ChangeMode mode, Expr target, boolean lax)
            throws CompileException {
        Changeable.Mode changeMode = changeMode(effect, mode);
        TypeChange typed = typeChange(target, changeMode);
        if (typed == null) {
            // No type says what its values take: they're checked when it runs, if anything might change them.
            if (!lax || !Values.hasChanger(changeMode)) {
                return false;
            }
            changeValues(effect, mode, target);
            return true;
        }
        if (typed.accepted() == null) {
            throw new CompileException(line, cannotChange(typed.what(), changeMode));
        }
        if (mode.value >= 0) {
            checkChanger((Expr) effect.match().values().get(mode.value), typed.accepted(), changeMode, typed.what());
        }
        changeValues(effect, mode, target);
        return true;
    }

    /**
     * {@code give a diamond to player}: changes the target's values themselves, with the changers
     * registered for their classes.
     */
    private void changeValues(ParsedSyntax effect, CoreSyntax.ChangeMode mode, Expr target) throws CompileException {
        expression(target);
        if (mode.value >= 0) {
            expression((Expr) effect.match().values().get(mode.value));
        } else {
            method.visitInsn(ACONST_NULL);
        }
        constant(changeMode(effect, mode), Changeable.Mode.class);
        // On Folia, each value is changed on the thread that owns it.
        method.visitMethodInsn(INVOKESTATIC, Regions.enabled() ? Type.getInternalName(Regions.class) : VALUES, "changeValues",
                "(" + OBJECT_DESC + OBJECT_DESC + Type.getDescriptor(Changeable.Mode.class) + ")V", false);
    }

    /** {@code set name of player to "x"}: changes an expression that implements {@link Changeable}. */
    private void changeExpression(ParsedSyntax effect, CoreSyntax.ChangeMode mode, Expr.Syntax target)
            throws CompileException {
        Changeable.Mode changeMode = changeMode(effect, mode);
        ParsedSyntax syntax = target.syntax();
        if (isCore(syntax, CoreSyntax.TIME_STATE) && syntax.match().values().getFirst() instanceof Expr.Syntax inner) {
            // 'set past level of player to 3': the expression in that state is changed, as Skript's wrapper.
            int time = syntax.pattern() < 2 ? -1 : 1;
            if (currentLine != null && script.delayBefore(currentLine) == co.xenastudios.neoskript.frontend.registry.DelayState.DELAYED) {
                throw new CompileException(line, "Cannot use time states after the event has already passed");
            }
            if (!hasTimeState(inner, time)) {
                timed(inner, time, target);
            }
            ParsedSyntax outer = timedSyntax;
            int outerTime = timedTime;
            timedSyntax = inner.syntax();
            timedTime = time;
            changeShown = target;
            try {
                changeExpression(effect, mode, inner);
            } finally {
                timedSyntax = outer;
                timedTime = outerTime;
                changeShown = null;
            }
            return;
        }
        Expr shown = changeShown != null ? changeShown : target;
        changeShown = null;
        if (mode.value >= 0 && mode.value < effect.match().values().size()
                && effect.match().values().get(mode.value) instanceof Expr.FunctionCall call
                && call.function().returnType() == null) {
            // A function that returns nothing can't give anything a value.
            TypeRef ref = target.type() == null ? null : registry.resolveType(target.type());
            String name = ref == null ? "value" : ref.type().name();
            throw new CompileException(line, ("aeiou".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name + " can't be "
                    + (mode == CoreSyntax.ChangeMode.SET ? "set to " : mode == CoreSyntax.ChangeMode.ADD || mode == CoreSyntax.ChangeMode.GIVE ? "added to " : "changed by ")
                    + source.substring(call.start(), call.end()) + " because the function doesn't return anything");
        }
        Object element = library.binding(syntax.syntax()) instanceof ElementFactory<?> factory
                ? factory.create(info(syntax))
                : null;
        Class<?>[] accepted = element instanceof Changeable changeable ? changeable.acceptChange(changeMode) : null;
        boolean typeChanges = accepted == null && element instanceof Changeable changeable
                && changeable.typeChanges(changeMode);
        if ((!changesItself(element) || typeChanges)
                && changeTypeValues(effect, mode, target, !(element instanceof Changeable))) {
            // An expression that can't be changed itself, but whose values can be: 'give X to all players'.
            return;
        }
        if (accepted == null) {
            throw new CompileException(line, cannotChange(shown == target ? skriptText(target)
                    : source.substring(shown.start(), shown.end()), changeMode));
        }
        if (mode.value >= 0) {
            Expr given = (Expr) effect.match().values().get(mode.value);
            checkChanger(given, accepted, changeMode, skriptText(target));
            if (((Changeable) element).takesOne(changeMode)
                    && co.xenastudios.neoskript.frontend.parse.Plurality.plural(given, registry)) {
                // As Skript's change effect, which names the classes as Java writes them.
                String types = java.util.Arrays.stream(accepted).map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(", "));
                throw new CompileException(line, switch (changeMode) {
                    case ADD -> "Only one " + types + " can be added to " + skriptText(target) + ", not more";
                    case SET -> skriptText(target) + " can only be set to one " + types + ", not more";
                    default -> "Only one " + types + " can be removed from " + skriptText(target) + ", not more";
                });
            }
        }
        constant(regional(element, Changeable.class, syntax, site(syntax, element.getClass())), Changeable.class);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        arguments(syntax);
        if (mode.value >= 0) {
            // Words are first tried as what the target takes: 'set item transform of X to fixed' is the transform.
            List<String> outerLiteralTypes = literalTypes;
            literalTypes = library.typesOf(accepted);
            try {
                expression((Expr) effect.match().values().get(mode.value));
            } finally {
                literalTypes = outerLiteralTypes;
            }
            constant(accepted, Class[].class);
            method.visitMethodInsn(INVOKESTATIC, VALUES, "narrow",
                    "(" + OBJECT_DESC + "[Ljava/lang/Class;)" + OBJECTS, false);
        } else {
            method.visitInsn(ACONST_NULL);
        }
        constant(changeMode, Changeable.Mode.class);
        method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Changeable.class), "change",
                "(" + CONTEXT + OBJECTS + OBJECTS + Type.getDescriptor(Changeable.Mode.class) + ")V", true);
    }

    /**
     * Fails as Skript's change effect does when what's given is known to be of a type the target can't take:
     * {@code "hello" can't be added to date data tag "date" of {_item} because the former is not a time span}.
     * Values whose type is only known when it runs (variables, unresolved words) are let through, as are
     * targets that take anything.
     */
    private void checkChanger(Expr changer, Class<?>[] accepted, Changeable.Mode mode, String what)
            throws CompileException {
        if (accepted.length == 0 || java.util.Arrays.asList(accepted).contains(Object.class)) {
            return;
        }
        List<String> acceptedTypes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Class<?> type : accepted) {
            List<String> ids = library.typesOf(new Class<?>[] {type});
            if (ids.isEmpty()) {
                // A class scripts have no type for: nothing to tell the user, so nothing to check.
                return;
            }
            acceptedTypes.addAll(ids);
            // Named as the type that is that class, or else the first one under it.
            String named = ids.stream().filter(id -> library.javaClass(id) == type).findFirst().orElse(ids.getFirst());
            TypeRef ref = registry.resolveType(named);
            names.add(ref == null ? named : ref.type().name());
        }
        // A value with words nothing reads in it is one of Skript's unparsed literals, as a whole.
        boolean unparsed = changer instanceof Expr.Syntax && unreadable(changer);
        if (unparsed ? acceptedTypes.contains("string") || wordsAs(unbracketed(changer), acceptedTypes) != null
                : changerFits(changer, acceptedTypes)) {
            return;
        }
        StringBuilder not = new StringBuilder("is ");
        if (names.size() == 1) {
            not.append("not ").append(article(names.getFirst())).append(' ').append(names.getFirst());
        } else {
            not.append("neither ");
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    not.append(i == names.size() - 1 ? " nor " : ", ");
                }
                not.append(article(names.get(i))).append(' ').append(names.get(i));
            }
        }
        // Values written as words are Skript's unparsed literals, which its errors quote.
        String given = unparsed || changer instanceof Expr.Literal || changer instanceof Expr.Unresolved
                ? "'" + unbracketed(changer) + "'" : skriptText(changer);
        throw new CompileException(line, switch (mode) {
            case ADD -> given + " can't be added to " + what + " because the former " + not;
            case SET -> what + " can't be set to " + given + " because the latter " + not;
            default -> given + " can't be removed from " + what + " because the former " + not;
        });
    }

    /** Whether {@code value} has words in it that no type's parser reads. */
    private boolean unreadable(Object value) {
        return switch (value) {
            case Expr.Unresolved words -> library.literal(words.text()) == null && library.literalError(words.text()) == null;
            case Expr.ListOf list -> list.items().stream().anyMatch(this::unreadable);
            case Expr.Syntax syntax -> syntax.syntax().match().values().stream().anyMatch(this::unreadable);
            case null, default -> false;
        };
    }

    /** An expression's text, without brackets around it, as Skript's unparsed literals have it. */
    private String unbracketed(Expr expr) {
        String text = source.substring(expr.start(), expr.end()).strip();
        while (text.startsWith("(") && closes(text)) {
            text = text.substring(1, text.length() - 1).strip();
        }
        return text;
    }

    /** Whether the bracket {@code text} starts with is closed by its last character. */
    private static boolean closes(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            depth += c == '(' ? 1 : c == ')' ? -1 : 0;
            if (depth == 0) {
                return i == text.length() - 1;
            }
        }
        return false;
    }

    /** Whether {@code changer} could give values of one of {@code types}, as far as is known before it runs. */
    private boolean changerFits(Expr changer, List<String> types) {
        if (changer instanceof Expr.ListOf list) {
            for (Expr item : list.items()) {
                if (!changerFits(item, types)) {
                    return false;
                }
            }
            return true;
        }
        if (changer instanceof Expr.Unresolved words) {
            // Words are read as what the target takes, or they don't fit, as Skript's unparsed literals. Text
            // can hold any words, so it's left to when it runs.
            return types.contains("string") || wordsAs(words.text(), types) != null;
        }
        if (!(changer instanceof Expr.Text || changer instanceof Expr.Literal || changer instanceof Expr.Syntax)) {
            return true;
        }
        String type = changer.type();
        if (type == null || type.equals(SyntaxRegistry.OBJECT)) {
            return true;
        }
        for (String wanted : types) {
            // Its type, one above it or it converts to, or one under it, which it might be when it runs, as
            // Skript's converted expressions. Types aren't otherwise taken as each other here.
            if (registry.isSubtype(type, wanted) || registry.isConvertible(type, wanted) || registry.isSubtype(wanted, type)
                    || registry.isAssignable(type, wanted) && !(changer instanceof Expr.Syntax)) {
                return true;
            }
        }
        return false;
    }

    /**
     * An expression as Skript's {@code toString} writes it in its errors: as its element describes itself
     * ({@code cook time of {_block}}), the event's block, location or entity as {@code the block}, else as
     * {@link #describe} has it.
     */
    /** Literals Skript writes as they're written, not as their value's name. */
    private static final java.util.Set<String> NUMERIC_LITERALS = java.util.Set.of("integer", "number", "boolean");

    private String skriptText(Expr expr) {
        if (expr instanceof Expr.Syntax syntax) {
            ParsedSyntax parsed = syntax.syntax();
            var describer = library.describer(parsed.syntax().id());
            if (describer != null) {
                List<Object> values = parsed.match().values();
                PlaceholderSpec[] specs = placeholderSpecs(parsed.syntax().patterns().get(parsed.pattern()));
                List<String> arguments = new ArrayList<>(values.size());
                for (int i = 0; i < values.size(); i++) {
                    Object written = values.get(i) instanceof Expr.Unresolved words && i < specs.length
                            ? wordsAs(words.text(), specs[i].types())
                            : values.get(i) instanceof Expr.Literal literal && i < specs.length
                                    && !NUMERIC_LITERALS.contains(literal.type())
                                    ? wordsAs(literal.text(), specs[i].types()) : null;
                    // A literal is written as its value: 'pigs' is 'pig'.
                    arguments.add(written != null ? Values.text(written)
                            : values.get(i) instanceof Expr value ? skriptText(value) : null);
                }
                String described = describer.describe(info(parsed), arguments);
                if (described != null) {
                    return described;
                }
            }
            if (isCore(parsed, CoreSyntax.ARITHMETIC) && parsed.match().values().size() == 2
                    && parsed.match().values().get(0) instanceof Expr left
                    && parsed.match().values().get(1) instanceof Expr right && syntax.end() <= source.length()) {
                // As Skript's ExprArithmetic: its operands as Skript writes them, in brackets if written in them.
                String one = skriptText(left);
                String two = skriptText(right);
                if (bracketed(left, syntax)) {
                    one = "(" + one + ")";
                }
                if (bracketed(right, syntax)) {
                    two = "(" + two + ")";
                }
                return one + " " + CoreSyntax.OPERATORS.get(parsed.pattern()).sign() + " " + two;
            }
            if (isCore(parsed, CoreSyntax.EVENT_VALUE_ENTITY) && syntax.end() <= source.length()) {
                // Skript's ExprEntity: 'the zombie'.
                return "the " + source.substring(syntax.start(), syntax.end()).strip()
                        .replaceFirst("(?i)^the ", "").replaceFirst("(?i)^event-", "");
            }
            if (isCore(parsed, CoreSyntax.EVENT_VALUE) && parsed.match().regexValues().getFirst() instanceof TypeRef ref) {
                String type = ref.type().id();
                String written = syntax.end() <= source.length()
                        ? source.substring(syntax.start(), syntax.end()).strip().toLowerCase(Locale.ROOT) : "";
                if (type.equals("block") || type.equals("location")
                        || registry.isSubtype(type, "entity") && !written.startsWith("event-")) {
                    // Skript's ExprBlock, ExprLocation and ExprEntity: 'the block', 'the player'. An entity
                    // written as 'event-entity' is Skript's event value, written so.
                    return "the " + ref.type().name().toLowerCase(Locale.ROOT);
                }
            }
        }
        return describe(expr);
    }

    /** Whether {@code operand} is written in brackets within {@code whole}: the {@code (2 + 3)} of {@code (2 + 3) * 4}. */
    private boolean bracketed(Expr operand, Expr whole) {
        int before = operand.start() - 1;
        while (before >= whole.start() && Character.isWhitespace(source.charAt(before))) {
            before--;
        }
        int after = operand.end();
        while (after < whole.end() && Character.isWhitespace(source.charAt(after))) {
            after++;
        }
        return before >= whole.start() && after < whole.end() && source.charAt(before) == '('
                && source.charAt(after) == ')';
    }

    /**
     * An expression as Skript's errors write it: its text, with variables saying what they hold when testing,
     * as Skript's debug text does.
     */
    private String describe(Expr expr) {
        String text = source.substring(expr.start(), expr.end());
        if (!library.testing()) {
            return text;
        }
        List<Integer> ends = new ArrayList<>();
        variableEnds(expr, ends);
        StringBuilder described = new StringBuilder(text);
        ends.sort(java.util.Comparator.reverseOrder());
        for (int end : ends) {
            described.insert(end - expr.start(), " (as java.lang.Object)");
        }
        return described.toString();
    }

    /**
     * {@code value} as Skript's {@code toString} writes it in messages: numbers and booleans as their values,
     * arithmetic with its operators spaced out, lists joined with their conjunction, and syntax as its library
     * describes it ({@link co.xenastudios.neoskript.api.registration.Registrar#describe}); the rest as written.
     */
    private String skriptText(Object value) {
        switch (value) {
            case null -> {
                return null;
            }
            case Expr.Literal literal when literal.type().equals("integer") || literal.type().equals("number") -> {
                String digits = literal.text().replace("_", "");
                try {
                    return literal.type().equals("integer") ? Long.toString(Long.parseLong(digits.replaceFirst("^\\+", "")))
                            : Values.text(digits.endsWith("%") ? Double.parseDouble(digits.substring(0, digits.length() - 1)) / 100
                                    : Double.parseDouble(digits));
                } catch (NumberFormatException e) {
                    return literal.text().strip();
                }
            }
            case Expr.Literal literal when literal.type().equals("boolean") ->  {
                return Boolean.toString(switch (literal.text().strip().toLowerCase(Locale.ROOT)) {
                    case "true", "yes", "on" -> true;
                    default -> false;
                });
            }
            case Expr.Unresolved unresolved when library.literal(unresolved.text()) != null -> {
                // Words read as a value are written as that value: 'an arrow' is "arrow".
                return Values.text(library.literal(unresolved.text()));
            }
            case Expr.ListOf list -> {
                StringBuilder joined = new StringBuilder();
                for (int i = 0; i < list.items().size(); i++) {
                    if (i > 0) {
                        joined.append(i == list.items().size() - 1 ? (list.and() ? " and " : " or ") : ", ");
                    }
                    joined.append(skriptText(list.items().get(i)));
                }
                return joined.toString();
            }
            case Expr.Syntax syntax when isArithmetic(syntax) -> {
                ParsedSyntax parsed = syntax.syntax();
                String[] sides = new String[2];
                for (int side = 0; side < 2; side++) {
                    Expr operand = (Expr) parsed.match().values().get(side);
                    sides[side] = skriptText(operand);
                    // As Skript's ExprArithmetic: an operand in brackets keeps them.
                    if (parsed.match().spans().get(side)[0] != operand.start()) {
                        sides[side] = "(" + sides[side] + ")";
                    }
                }
                CoreSyntax.Operator operator = (isCore(parsed, CoreSyntax.ARITHMETIC) ? CoreSyntax.OPERATORS
                        : CoreSyntax.CUSTOM_OPERATORS).get(parsed.pattern());
                return sides[0] + " " + operator.sign() + " " + sides[1];
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.TYPE_VALUE) -> {
                // 'a player' is written as what it reads as, without its article: "player".
                String words = writtenText(syntax).replaceFirst("(?i)^an? ", "");
                Object literal = library.literal(words);
                return literal != null ? Values.text(literal) : words;
            }
            case Expr.Syntax syntax when eventLiteral(syntax) != null -> {
                Object literal = eventLiteral(syntax);
                return literal instanceof co.xenastudios.neoskript.runtime.TypeValue
                        ? registry.type(syntax.type()) == null ? writtenText(syntax) : registry.type(syntax.type()).name()
                        : Values.text(literal);
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.EVENT_VALUE)
                    || isCore(syntax.syntax(), CoreSyntax.EVENT_VALUE_ENTITY) -> {
                // Skript's ExprEntity writes 'the <entity>'; its other event values are 'event-<type>'.
                String written = writtenText(syntax).replaceFirst("(?i)^(the |event-)", "");
                boolean entity = isCore(syntax.syntax(), CoreSyntax.EVENT_VALUE_ENTITY)
                        || syntax.type() != null && registry.isSubtype(syntax.type(), "entity");
                return (entity ? "the " : "event-") + written;
            }
            case Expr.Syntax syntax when library.describer(syntax.syntax().syntax().id()) != null -> {
                List<String> arguments = new ArrayList<>();
                for (Object argument : syntax.syntax().match().values()) {
                    arguments.add(skriptText(argument));
                }
                ParsedSyntax outerSettingUp = settingUp;
                String outerSource = settingUpSource;
                String described;
                try {
                    described = library.describer(syntax.syntax().syntax().id()).describe(info(syntax.syntax()), arguments);
                } finally {
                    settingUp = outerSettingUp;
                    settingUpSource = outerSource;
                }
                return described != null ? described : writtenText(syntax);
            }
            case Expr expr -> {
                return writtenText(expr);
            }
            default -> {
                return String.valueOf(value);
            }
        }
    }

    /** {@code expr} as written in the code being compiled. */
    private String writtenText(Expr expr) {
        if (source != null && expr.start() >= 0 && expr.end() <= source.length() && expr.start() <= expr.end()) {
            return source.substring(expr.start(), expr.end()).strip();
        }
        return expr.toString();
    }

    private static void variableEnds(Object value, List<Integer> ends) {
        switch (value) {
            case Expr.Variable variable -> ends.add(variable.end());
            case Expr.ListOf list -> list.items().forEach(item -> variableEnds(item, ends));
            case Expr.FunctionCall call -> call.arguments().forEach(argument -> variableEnds(argument, ends));
            case Expr.Syntax syntax -> syntax.syntax().match().values().forEach(inner -> variableEnds(inner, ends));
            case null, default -> { }
        }
    }

    // ---------------------------------------------------------------- elements

    private void condition(ParsedSyntax condition) throws CompileException {
        List<Object> values = condition.match().values();
        if (condition.syntax().id().equals("CondCompare") && values.stream().anyMatch(v -> v instanceof Expr.Unresolved)) {
            try {
                conditionChecked(condition, values);
            } catch (CompileException e) {
                if (!e.unknownWords() || textPart != null) {
                    throw e;
                }
                // A literal is Skript's unparsed literal too, of no type yet: 'if X > 0.5' isn't understood.
                boolean typed = values.stream().anyMatch(v -> v instanceof Expr expr && !(expr instanceof Expr.Unresolved)
                        && !(expr instanceof Expr.Literal) && valueType(expr) != null && !valueType(expr).equals(SyntaxRegistry.OBJECT));
                for (Object value : values) {
                    if (value instanceof Expr.Unresolved words && words.problem() != null) {
                        // Skript's comparison first tries to read its unparsed literal, which gives the error it kept.
                        throw new CompileException(line, words.problem());
                    }
                }
                if (typed || values.size() > 2) {
                    // Words nothing reads compared with something of a type: Skript's comparison says so.
                    String first = compared(values.get(0));
                    java.util.regex.Matcher conditional = java.util.regex.Pattern.compile("(?i)^((?:else )?if )")
                            .matcher(source.strip());
                    if (values.get(0) instanceof Expr.Unresolved && first.startsWith("'") && inSectionHeader()
                            && conditional.lookingAt()) {
                        // Skript then reads the whole line as a condition, whose words start with the 'if'.
                        first = "'" + conditional.group(1) + first.substring(1);
                    }
                    StringBuilder message = new StringBuilder("Can't compare ").append(first)
                            .append(" with ").append(compared(values.get(1)));
                    if (values.size() > 2) {
                        message.append(" and ").append(compared(values.get(2)));
                    }
                    throw new CompileException(line, message.toString());
                }
                // Otherwise Skript's comparison quietly fails to initialise, so the line isn't understood.
                String text = source.strip();
                java.util.regex.Matcher header = java.util.regex.Pattern.compile("(?i)^(?:else )?if (.+?):?$").matcher(text);
                throw new CompileException(line, header.matches() && inSectionHeader()
                        ? "Can't understand this condition: '" + header.group(1) + "'"
                        : "Can't understand this condition/effect: " + text);
            }
            return;
        }
        conditionChecked(condition, values);
    }

    /** Words that are a function's argument themselves, being compiled. */
    private Expr argumentWords;

    /** What's being compiled: a change effect's value, the code in a text's {@code %...%}, an 'if any:' line. */
    private Expr changer;
    private String textPart;
    private boolean conditionLines;

    private static final java.util.regex.Pattern IF_LINE = java.util.regex.Pattern.compile("(?i)^(?:else )?(?:parse )?if (.+?):?$");

    /**
     * Skript's error for words nothing understands, which depends on where they are: in a change effect's
     * value, the value isn't understood; in a text's {@code %...%}, that code; in a condition or a loop, that;
     * anywhere else, the whole line.
     */
    private String unknownWords(Expr.Unresolved unresolved) {
        if (textPart != null) {
            return "Can't understand this expression: " + textPart;
        }
        if (unresolved.problem() != null && (textPart == null)) {
            // Skript's unparsed literal gives the error it kept from reading its words when nothing can use them.
            return unresolved.problem();
        }
        if (unresolved == argumentWords) {
            // A function's argument: Skript's function call can't read its unparsed literal.
            return "Can't understand this expression: '" + unresolved.text().strip() + "'";
        }
        String text = source == null ? unresolved.text().strip() : source.strip();
        if (changer != null && unresolved.start() >= changer.start() && unresolved.end() <= changer.end()) {
            String value = outerArgument != null ? outerArgument : unresolved.text().strip();
            if (!(changer instanceof Expr.ListOf)) {
                // Skript's unparsed literal gives the error it kept from reading its words, if it has one.
                return unresolved.problem() != null && unresolved.start() == changer.start()
                        && unresolved.end() == changer.end() ? unresolved.problem()
                        : "Can't understand this expression: '" + value + "'";
            }
        }
        if (conditionLines) {
            return "Can't understand this condition: '" + text + "'";
        }
        java.util.regex.Matcher header = IF_LINE.matcher(text);
        if (header.matches() && inSectionHeader()) {
            return "Can't understand this condition: '" + header.group(1) + "'";
        }
        if (text.regionMatches(true, 0, "loop ", 0, 5)) {
            return "Can't understand this loop: '" + text.substring(5).replaceFirst(":$", "") + "'";
        }
        return "Can't understand this condition/effect: " + text;
    }

    /**
     * Whether what's compiled is a section's header. Only there is {@code if} Skript's conditional: a line
     * {@code if <condition>} without a colon is a condition or effect like any other, and not understood as one.
     */
    private boolean inSectionHeader() {
        return currentLine == null || currentLine.opensSection();
    }

    /** A side of a comparison as Skript's errors name it: its type with an article, or as written if it has none. */
    private String compared(Object value) {
        if (value instanceof Expr.Unresolved unresolved) {
            return "'" + unresolved.text().strip() + "'";
        }
        if (value instanceof Expr expr && valueType(expr) != null && !valueType(expr).equals(SyntaxRegistry.OBJECT)) {
            TypeRef ref = registry.resolveType(valueType(expr));
            if (ref != null) {
                return article(ref.type().name()) + " " + ref.type().name();
            }
        }
        return value instanceof Expr expr ? describe(expr) : String.valueOf(value);
    }

    private void conditionChecked(ParsedSyntax condition, List<Object> values) throws CompileException {
        if (!values.isEmpty() && values.getFirst() instanceof Expr.ListOf list && list.items().size() > 1
                && ITEM_CONDITIONS.contains(baseId(condition.syntax().id()))) {
            // A list of what's checked is checked item by item, as Skript's expression lists are: '"" or {_x} is
            // not empty' holds if either item isn't, and 'X and Y are not set' if neither is.
            Label decided = new Label();
            Label end = new Label();
            for (Expr item : list.items()) {
                List<Object> one = new ArrayList<>(values);
                one.set(0, item);
                co.xenastudios.neoskript.frontend.pattern.MatchResult match = condition.match();
                checkOne(new ParsedSyntax(condition.syntax(), condition.pattern(),
                        new co.xenastudios.neoskript.frontend.pattern.MatchResult(match.mark(), match.tags(), one, match.spans(), match.regexValues(), match.regexSpans())));
                method.visitJumpInsn(list.and() ? IFEQ : IFNE, decided);
            }
            method.visitInsn(list.and() ? ICONST_1 : ICONST_0);
            method.visitJumpInsn(GOTO, end);
            method.visitLabel(decided);
            method.visitInsn(list.and() ? ICONST_0 : ICONST_1);
            method.visitLabel(end);
            return;
        }
        checkOne(condition);
    }

    /** Conditions that check a list of what they're about item by item, as Skript's property conditions do. */
    private static final java.util.Set<String> ITEM_CONDITIONS = loadIds("item-conditions.txt");

    private static java.util.Set<String> loadIds(String resource) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        try (java.io.InputStream in = TriggerCompiler.class.getResourceAsStream(resource)) {
            if (in != null) {
                for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                    if (!line.isBlank() && !line.startsWith("#")) {
                        ids.add(line.strip());
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return java.util.Set.copyOf(ids);
    }

    /** An element's id without the suffix that tells its registrations apart ({@code ExprSets-itemtypes}). */
    private static String baseId(String id) {
        int dash = id.indexOf('-');
        return dash < 0 ? id : id.substring(0, dash);
    }

    private void checkOne(ParsedSyntax condition) throws CompileException {
        callThrough(condition, Condition.class);
        method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Condition.class), "check",
                "(" + CONTEXT + OBJECTS + ")Z", true);
        constantCondition(condition);
    }

    // ---------------------------------------------------------------- constants

    /** Skript's literals that are syntax elements: {@code pi}, {@code infinity}, {@code console}... */
    private static final java.util.Set<String> LITERAL_ELEMENTS = loadIds("literal-elements.txt");
    /**
     * Elements Skript works out while parsing when all their arguments are literals, which makes them literals
     * too: Skript's {@code simplify()} returning a {@code SimplifiedLiteral} or {@code SimplifiedCondition}.
     */
    private static final java.util.Set<String> SIMPLIFIED = loadIds("simplified-elements.txt");
    /** Of {@link #SIMPLIFIED}, those whose arguments may be left out and still be worked out. */
    private static final java.util.Set<String> SIMPLIFIED_OPTIONAL = java.util.Set.of("ExprSubstring", "ExprJoinSplit",
            "ExprFormatDate");

    /** Whether this compiler is only working out a condition while compiling, for {@link #constantCondition}. */
    private boolean probing;

    /**
     * Whether {@code value} is a literal as Skript's parser leaves it: a number, text or other value written out,
     * a list of them, or an element Skript works out from literals ({@link #SIMPLIFIED}).
     */
    private boolean isLiteral(Object value) {
        return switch (value) {
            case Expr.Literal literal -> true;
            case Expr.Text text -> text.embedded().isEmpty();
            case Expr.Unresolved unresolved -> true;
            case Expr.ListOf list -> list.items().stream().allMatch(this::isLiteral);
            case Expr.Syntax syntax when isArithmetic(syntax) ->
                    syntax.syntax().match().values().stream().allMatch(this::isLiteral);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.TYPE_VALUE) -> true;
            case Expr.Syntax syntax when eventLiteral(syntax) != null -> true;
            case Expr.Syntax syntax -> LITERAL_ELEMENTS.contains(baseId(syntax.syntax().syntax().id()))
                    || simplified(syntax.syntax());
            case null, default -> false;
        };
    }

    /** Whether Skript works out {@code syntax} while parsing, as all its arguments are literals. */
    private boolean simplified(ParsedSyntax syntax) {
        String id = baseId(syntax.syntax().id());
        if (!SIMPLIFIED.contains(id)) {
            return false;
        }
        for (Object value : syntax.match().values()) {
            if (value == null ? !SIMPLIFIED_OPTIONAL.contains(id) : !isLiteral(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Skript's warning for a condition it works out while parsing, as all it checks are literals
     * ({@code SimplifiedCondition}): "The condition '1 is equal to 1' will always be true." The condition is
     * compiled on its own and run, with no event, as Skript checks it.
     */
    private void constantCondition(ParsedSyntax condition) {
        if (probing || !simplified(condition)) {
            return;
        }
        Boolean result = probe(condition);
        if (result == null) {
            return;
        }
        String text;
        co.xenastudios.neoskript.api.registration.Registrar.Describer describer = library.describer(condition.syntax().id());
        if (describer != null) {
            List<String> arguments = new ArrayList<>();
            for (Object argument : condition.match().values()) {
                arguments.add(skriptText(argument));
            }
            ParsedSyntax outerSettingUp = settingUp;
            String outerSource = settingUpSource;
            try {
                text = describer.describe(info(condition), arguments);
            } finally {
                settingUp = outerSettingUp;
                settingUpSource = outerSource;
            }
        } else {
            text = textOf(condition);
        }
        if (text != null) {
            warn("constant", "The condition '" + text + "' will always be " + result + ".");
        }
    }

    /** {@code condition} worked out now, with no event; {@code null} if it can't be, or reports a problem. */
    private Boolean probe(ParsedSyntax condition) {
        VariableStore result = new VariableStore();
        TriggerCompiler probe = new TriggerCompiler(library, functions, result, script);
        probe.probing = true;
        probe.eventClasses = eventClasses;
        probe.eventLabel = eventLabel;
        probe.commandArguments = commandArguments;
        probe.commandChoices = commandChoices;
        probe.headerLine = headerLine;
        probe.currentLine = currentLine;
        probe.currentStatement = currentStatement;
        try {
            co.xenastudios.neoskript.runtime.Trigger trigger = (co.xenastudios.neoskript.runtime.Trigger) probe.define(
                    probe.generate(constantsScript, () -> {
                        probe.line = line;
                        probe.source = source;
                        probe.constant(result, VariableStore.class);
                        probe.method.visitLdcInsn("result");
                        probe.checkOne(condition);
                        probe.method.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf",
                                "(Z)Ljava/lang/Boolean;", false);
                        probe.method.visitMethodInsn(INVOKEVIRTUAL, STORE, "set", "(" + STRING_DESC + OBJECT_DESC + ")V",
                                false);
                    }));
            co.xenastudios.neoskript.runtime.RuntimeErrors.begin();
            try {
                trigger.execute(new co.xenastudios.neoskript.runtime.SimpleTriggerContext(null));
            } finally {
                co.xenastudios.neoskript.runtime.RuntimeErrors.end();
            }
            if (co.xenastudios.neoskript.runtime.RuntimeErrors.lastCaught().length > 0) {
                return null;
            }
            return result.get("result") instanceof Boolean checked ? checked : null;
        } catch (Throwable notConstant) {
            return null;
        }
    }

    /** How the element was matched, for its factory: its pattern, marks, tags and which arguments are lists. */
    private ElementInfo info(ParsedSyntax syntax) {
        settingUp = syntax;
        settingUpSource = source;
        long or = 0;
        long lists = 0;
        List<Object> values = syntax.match().values();
        for (int i = 0; i < values.size() && i < 64; i++) {
            if (values.get(i) instanceof Expr.ListOf list) {
                lists |= 1L << i;
                if (!list.and()) {
                    or |= 1L << i;
                }
            } else if (values.get(i) instanceof Expr.Variable variable && variable.list()) {
                lists |= 1L << i;
            } else if (values.get(i) instanceof Expr.Syntax inner && isCore(inner.syntax(), CoreSyntax.ANY_OF)) {
                lists |= 1L << i;
                or |= 1L << i;
            }
        }
        List<ElementInfo.TypeArgument> types = new ArrayList<>();
        for (Object value : syntax.match().regexValues()) {
            if (value instanceof TypeRef ref) {
                types.add(new ElementInfo.TypeArgument(ref.type().id(), ref.type().name(), ref.plural(),
                        library.javaClass(ref.type().id())));
            }
        }
        List<String> argumentTypes = new ArrayList<>(values.size());
        List<String> literals = new ArrayList<>(values.size());
        long single = 0;
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            String type = value instanceof Expr expr && !(expr instanceof Expr.Variable) ? expr.type() : null;
            argumentTypes.add(type == null || type.equals(SyntaxRegistry.OBJECT) ? null : type);
            String literal = null;
            boolean one = false;
            switch (value) {
                case Expr.Literal written -> {
                    literal = written.text();
                    one = true;
                }
                case Expr.Text text -> {
                    one = true;
                    if (text.embedded().isEmpty() && text.end() <= source.length()) {
                        literal = unescape(source.substring(text.start() + 1, text.end() - 1));
                    }
                }
                case Expr.Variable variable -> one = !variable.list();
                // An element that's always one value, or one when its arguments are, as Skript's isSingle.
                case Expr.Syntax inner -> one = co.xenastudios.neoskript.frontend.parse.Plurality.single(inner, registry);
                case Expr.FunctionCall call -> one = co.xenastudios.neoskript.frontend.parse.Plurality.single(call, registry);
                case null, default -> {}
            }
            literals.add(literal);
            if (one && i < 64) {
                single |= 1L << i;
            }
        }
        long defaults = 0;
        for (int i = 0; i < values.size() && i < 64; i++) {
            Object value = values.get(i);
            if (value == null || (value instanceof Expr.Syntax inner && (isCore(inner.syntax(), CoreSyntax.EVENT_VALUE)
                    || isCore(inner.syntax(), CoreSyntax.EVENT_VALUE_ENTITY)
                    || library.isEventValueExpression(inner.syntax().syntax().id())))) {
                defaults |= 1L << i;
            }
        }
        String sectionType = null;
        for (Block block : blocks) {
            if (block.kind() == BlockKind.SECTION) {
                sectionType = block.valueType();
                break;
            }
        }
        return new ElementInfo(syntax.pattern(), syntax.match().mark(), syntax.match().tags(), or, lists, types,
                argumentTypes, single, literals, eventLabel != null ? eventLabel : "function",
                eventClasses == null ? List.of() : eventClasses,
                sectionType, defaults, syntax == timedSyntax ? timedTime : 0, textOf(syntax));
    }

    /** What a change's errors quote instead of its target: the time state around it ({@code past level of player}). */
    private Expr changeShown;

    /** The library expression being compiled with a time state ({@code past tool}), and that state, or null. */
    private ParsedSyntax timedSyntax;
    private int timedTime;

    /** Pushes the element, the trigger context and the element's arguments, ready to call it. */
    private void callThrough(ParsedSyntax syntax, Class<?> type) throws CompileException {
        callThrough(syntax, type, false, false);
    }

    /**
     * {@link #callThrough(ParsedSyntax, Class)}; with {@code keyed}, arguments with indices are passed with
     * them ({@link KeyedValues}), and with {@code implicit} too, list variables are.
     */
    private void callThrough(ParsedSyntax syntax, Class<?> type, boolean keyed, boolean implicit) throws CompileException {
        if (!(library.binding(syntax.syntax()) instanceof ElementFactory<?> factory)) {
            throw unsupported(syntax.syntax().id());
        }
        Object element = factory.create(info(syntax));
        co.xenastudios.neoskript.runtime.errors.ErrorSites.Site site = site(syntax, element.getClass());
        constant(regional(element, type, syntax, site), type);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        arguments(syntax, keyed, implicit);
    }

    /** Pushes {@code value} as a constant of the class, typed as {@code type}. */
    private void constant(Object value, Class<?> type) {
        constants.add(value);
        method.visitLdcInsn(new ConstantDynamic("_", Type.getDescriptor(type), CLASS_DATA_AT, constants.size() - 1));
    }

    /**
     * The types words in argument {@code i} are first tried as: its placeholder's, or in a comparison, what the
     * other side gives, as Skript reads them: 'bill-boarding of X is fixed' is the billboard.
     */
    private List<String> literalTypesFor(ParsedSyntax syntax, List<Object> values, int i, PlaceholderSpec spec) {
        if (syntax.syntax().id().equals("CondCompare") && values.size() >= 2) {
            List<String> compared = comparedLiteralTypes(values, i);
            if (compared != null) {
                return compared;
            }
        }
        if (spec.types().size() < 2) {
            return spec.types();
        }
        // Words that could be several of the types are the one registered first, as Skript tries its types in
        // order: 'armor stand' is the entity type in '%itemtypes/entitytypes%'.
        List<String> ordered = new ArrayList<>(spec.types());
        ordered.sort(java.util.Comparator.comparingInt(name -> {
            TypeRef ref = registry.resolveType(name);
            return ref == null ? Integer.MAX_VALUE : registry.typeOrder(ref.type().id());
        }));
        return ordered;
    }

    /**
     * The types words on side {@code i} of a comparison are read as, as Skript's comparison reads them
     * ({@link #comparedWordTypes}), then any object, or {@code null} if that side isn't words.
     */
    private List<String> comparedLiteralTypes(List<Object> values, int i) {
        String[] types = comparedWordTypes(values);
        return types == null || types[i] == null ? null : List.of(types[i], SyntaxRegistry.OBJECT);
    }

    /**
     * The words a side of a comparison is: words nothing reads, or an element that can't be set up there and
     * is read as its words ({@link #eventLiteral}), or {@code null}.
     */
    private String comparisonWords(Object side) {
        if (side instanceof Expr.Unresolved words) {
            return words.text();
        }
        if (side instanceof Expr.Syntax syntax && eventLiteral(syntax) != null) {
            return source.substring(syntax.start(), syntax.end());
        }
        return null;
    }

    /**
     * For a comparison with words on a side, the type each side's words are read as ({@code null} for a side
     * that isn't words, or words nothing reads), as Skript's comparison reads them ({@code CondCompare.init}).
     * The first side is read against the second, and the others against the first
     * ({@code attemptReconstruction}): words are the first of Skript's types, in its order, that the other side
     * can be compared with, else the other side's type, else, as words compared with words are, any type in
     * Skript's order. Then if the two can't be compared, the second side's words are read as the first's type,
     * or the first's as the second's. So 'projectile is an arrow' compares the entity type with the arrow item.
     * {@code null} if no side is words.
     */
    private String[] comparedWordTypes(List<Object> values) {
        String[] words = new String[values.size()];
        String[] typed = new String[values.size()];
        boolean any = false;
        for (int i = 0; i < values.size(); i++) {
            words[i] = comparisonWords(values.get(i));
            any |= words[i] != null;
            if (words[i] == null && values.get(i) instanceof Expr expr && expr.type() != null
                    && !expr.type().equals(SyntaxRegistry.OBJECT) && !(expr instanceof Expr.Variable)) {
                TypeRef ref = registry.resolveType(expr.type());
                typed[i] = ref == null ? null : ref.type().id();
            }
        }
        if (!any) {
            return null;
        }
        String[] types = new String[values.size()];
        // Words compared with a value of any type are read as any type, as everywhere else (with Skript's warning
        // when they're several types' words); only words compared with words or with a type are read here.
        if (words[0] != null && (typed[1] != null || words[1] != null)) {
            types[0] = reconstructed(words[0], typed[1]);
        }
        String first = types[0] != null ? types[0] : typed[0];
        for (int i = 1; i < values.size(); i++) {
            if (words[i] != null && first != null) {
                types[i] = reconstructed(words[i], first);
                if (words[0] != null && wordsAs(words[i], List.of(types[i])) == null) {
                    // Words compared with words they can't be read like are read as any type, in Skript's order.
                    String anyType = wordsType(words[i], skriptTypeOrder());
                    if (anyType != null) {
                        types[i] = anyType;
                    }
                }
            }
        }
        if (values.size() == 2 && types[0] != null && types[1] != null && !comparable(types[0], types[1])) {
            if (wordsAs(words[1], List.of(types[0])) != null) {
                types[1] = types[0];
            } else if (wordsAs(words[0], List.of(types[1])) != null) {
                types[0] = types[1];
            }
        }
        return types;
    }

    /** The type {@code words} compared with a value of type {@code other} ({@code null}: words too) are read as. */
    private String reconstructed(String words, String other) {
        if (other != null) {
            Class<?> otherClass = library.javaClass(other);
            if (otherClass != null) {
                for (String type : skriptTypeOrder()) {
                    Class<?> c = library.javaClass(type);
                    if (c != null && co.xenastudios.neoskript.runtime.Comparison.hasEquality(otherClass, c)
                            && wordsAs(words, List.of(type)) != null) {
                        return type;
                    }
                }
            }
            // Else as the other side's type, then as any value, as words are read elsewhere.
            return other;
        }
        return wordsType(words, skriptTypeOrder());
    }

    /** Whether values of types {@code a} and {@code b} can be compared, as Skript has a comparator for them. */
    private boolean comparable(String a, String b) {
        Class<?> x = library.javaClass(a);
        Class<?> y = library.javaClass(b);
        return x != null && y != null && (x.equals(y) || co.xenastudios.neoskript.runtime.Comparison.hasEquality(x, y));
    }

    /** The first of {@code types} whose parser reads {@code words}, or {@code null}. */
    private String wordsType(String words, List<String> types) {
        for (String type : types) {
            if (wordsAs(words, List.of(type)) != null) {
                return type;
            }
        }
        return null;
    }

    /** The registry's types in the order Skript tries its own, then those Skript doesn't have. */
    private List<String> skriptTypeOrder() {
        List<String> known = skriptTypeOrder;
        if (known == null) {
            java.util.LinkedHashSet<String> order = new java.util.LinkedHashSet<>();
            for (String id : SKRIPT_TYPE_ORDER) {
                if (registry.type(id) != null) {
                    order.add(id);
                }
            }
            for (co.xenastudios.neoskript.frontend.registry.TypeInfo type : registry.types()) {
                order.add(type.id());
            }
            known = List.copyOf(order);
            skriptTypeOrder = known;
        }
        return known;
    }

    private List<String> skriptTypeOrder;

    /** Skript's types in the order it tries them, by NeoSkript's ids. */
    private static final List<String> SKRIPT_TYPE_ORDER = loadLines("skript-type-order.txt");

    private static List<String> loadLines(String resource) {
        List<String> lines = new ArrayList<>();
        try (java.io.InputStream in = TriggerCompiler.class.getResourceAsStream(resource)) {
            if (in != null) {
                for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                    if (!line.isBlank() && !line.startsWith("#")) {
                        lines.add(line.strip());
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return List.copyOf(lines);
    }

    private void arguments(ParsedSyntax syntax) throws CompileException {
        arguments(syntax, false, false);
    }

    private void arguments(ParsedSyntax syntax, boolean keyed, boolean implicit) throws CompileException {
        List<Object> values = syntax.match().values();
        if (!skriptOrder && values.stream().filter(Expr.class::isInstance).count() > 1) {
            // Skript matches the rest of a pattern before what comes first, so a later argument's error is the one
            // it gives: 'damage the player-argument by the number-argument' is about the number. Tried that way,
            // writing nothing, then for real.
            org.objectweb.asm.MethodVisitor real = method;
            int slots = nextSlot;
            method = new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {};
            skriptOrder = true;
            try {
                argumentsIn(syntax, keyed, implicit);
            } finally {
                method = real;
                nextSlot = slots;
                skriptOrder = false;
            }
        }
        argumentsIn(syntax, keyed, implicit);
    }

    /** Whether arguments are being tried in the order Skript parses them, last first, without writing anything. */
    private boolean skriptOrder;

    private void argumentsIn(ParsedSyntax syntax, boolean keyed, boolean implicit) throws CompileException {
        CompiledPattern pattern = syntax.syntax().patterns().get(syntax.pattern());
        List<Object> values = syntax.match().values();
        PlaceholderSpec[] specs = placeholderSpecs(pattern);
        missingDefaultsFirst(values, specs);
        pushInt(values.size());
        method.visitTypeInsn(ANEWARRAY, OBJECT);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            order.add(i);
        }
        if (skriptOrder) {
            // What's written, last first, then what's left out, as Skript fills that in after matching.
            order.sort(java.util.Comparator.comparing((Integer i) -> !(values.get(i) instanceof Expr))
                    .thenComparing(java.util.Comparator.reverseOrder()));
        }
        for (int i : order) {
            if (!(values.get(i) instanceof Expr expr)) {
                Block section = sectionDefault(specs[i]);
                if (section != null) {
                    // Left out inside a section for one: 'set worldborder size to 15' in 'a worldborder:'.
                    method.visitInsn(DUP);
                    pushInt(i);
                    element(section.valuesSlot(), section.indexSlot(), 0);
                    convert(isPlural(specs[i]));
                    method.visitInsn(AASTORE);
                    continue;
                }
                if (specs[i].types().stream().map(registry::resolveType).anyMatch(r -> r != null && inOuterSection(r.type().id()))) {
                    continue;
                }
                if (specs[i].types().contains("script")) {
                    // A script left out is the one running: 'function "heal"' finds this script's functions.
                    method.visitInsn(DUP);
                    pushInt(i);
                    constant(new co.xenastudios.neoskript.runtime.ScriptRef(constantsScript), Object.class);
                    method.visitInsn(AASTORE);
                    continue;
                }
                Expr custom = customDefault(specs[i]);
                if (custom != null) {
                    method.visitInsn(DUP);
                    pushInt(i);
                    expression(custom);
                    convert(isPlural(specs[i]));
                    method.visitInsn(AASTORE);
                    continue;
                }
                java.util.function.Function<Object, Object> fallback = eventDefault(specs[i], true);
                Object literalDefault = fallback == null ? literalDefault(specs[i]) : null;
                if (literalDefault != null) {
                    method.visitInsn(DUP);
                    pushInt(i);
                    constant(literalDefault, Object.class);
                    convert(isPlural(specs[i]));
                    method.visitInsn(AASTORE);
                    continue;
                }
                if (fallback != null) {
                    method.visitInsn(DUP);
                    pushInt(i);
                    eventValue(fallback);
                    convert(isPlural(specs[i]));
                    method.visitInsn(AASTORE);
                } else {
                    missingDefault(specs[i]);
                }
                continue;
            }
            method.visitInsn(DUP);
            pushInt(i);
            if (keyed && isKeyed(expr, implicit)) {
                keyed(expr, implicit);
                method.visitInsn(AASTORE);
                continue;
            }
            List<String> outerLiteralTypes = literalTypes;
            literalTypes = literalTypesFor(syntax, values, i, specs[i]);
            try {
                if (specs[i].time() != 0) {
                    timedArgument(expr, specs[i].time());
                } else {
                    expression(expr);
                }
            } finally {
                literalTypes = outerLiteralTypes;
            }
            Class<?> target = conversionTarget(specs[i]);
            if (target != null) {
                constant(target, Class.class);
                method.visitMethodInsn(INVOKESTATIC, VALUES, "convert",
                        "(" + OBJECT_DESC + "Ljava/lang/Class;)" + OBJECT_DESC, false);
            }
            Class<?> only = strictClass(specs[i]);
            if (only != null) {
                // As in Skript, values of other types never reach a text, number or boolean placeholder.
                constant(only, Class.class);
                method.visitMethodInsn(INVOKESTATIC, VALUES, "filter",
                        "(" + OBJECT_DESC + "Ljava/lang/Class;)" + OBJECT_DESC, false);
            }
            convert(isPlural(specs[i]));
            method.visitInsn(AASTORE);
        }
    }

    /** For a placeholder of one strict type (text, numbers, booleans, timespans, dates), its values' class. */
    private Class<?> strictClass(PlaceholderSpec spec) {
        return spec.types().size() == 1 ? strictClass(spec.types().getFirst()) : null;
    }

    /** The class values of a type must already be, for a text, number, boolean, timespan or date, else {@code null}. */
    private Class<?> strictClass(String type) {
        TypeRef ref = registry.resolveType(type);
        if (ref == null) {
            return null;
        }
        return switch (ref.type().id()) {
            case "string" -> String.class;
            case "number", "integer", "long" -> Number.class;
            case "boolean" -> Boolean.class;
            case "timespan" -> Timespan.class;
            case "date" -> co.xenastudios.neoskript.runtime.Date.class;
            default -> null;
        };
    }

    /**
     * The class values for a placeholder are converted to, when it has one type and something converts to
     * it ({@code %location%} given a player), else {@code null}.
     */
    private Class<?> conversionTarget(PlaceholderSpec spec) {
        if (spec.types().size() != 1) {
            return null;
        }
        TypeRef ref = registry.resolveType(spec.types().getFirst());
        Class<?> target = ref == null ? null : library.javaClass(ref.type().id());
        return target != null && Values.convertsTo(target) ? target : null;
    }

    /**
     * For a placeholder in an optional part that wasn't used: how to get the event's value of its type,
     * if the type defaults to it and the event has one, else {@code null}. With {@code report}, as Skript tries
     * each type's default in turn and keeps what failing ones logged, the types before the one used that the
     * event has no value of are errors: {@code weather} in a weather change event has no player.
     */
    private java.util.function.Function<Object, Object> eventDefault(PlaceholderSpec spec, boolean report) {
        if (eventClasses == null || spec.nullable()) {
            return null;
        }
        List<String> missing = new ArrayList<>();
        for (String name : spec.types()) {
            TypeRef ref = registry.resolveType(name);
            if (ref != null && library.hasEventDefault(ref.type().id())) {
                java.util.function.Function<Object, Object> getter = library.eventValue(eventClasses, ref.type().id());
                if (getter != null) {
                    if (report && !skriptOrder && inArguments == 0) {
                        for (String type : missing) {
                            String error = library.eventDefault(eventClasses, type, eventDescription()).error();
                            if (error != null && warned.add(line + ":" + error)) {
                                errors.add(new CompileException(line, error));
                            }
                        }
                    }
                    return getter;
                }
                missing.add(ref.type().id());
            }
        }
        return null;
    }

    /**
     * Where a text with code in it goes to a placeholder of any type, Skript holds it as an unparsed literal, whose
     * errors come when the element is set up: after its left-out placeholders' event values are looked up. So
     * those fail first: {@code send "%last attacker of event-entity%"} in a damage event is about its command sender.
     */
    private void missingDefaultsFirst(List<Object> values, PlaceholderSpec[] specs) throws CompileException {
        boolean deferred = false;
        for (int i = 0; i < values.size() && i < specs.length; i++) {
            if (values.get(i) instanceof Expr.Text text && !text.embedded().isEmpty() && specs[i] != null
                    && specs[i].types().stream().allMatch(name -> registry.resolveType(name) != null
                            && registry.resolveType(name).type().id().equals(SyntaxRegistry.OBJECT))) {
                deferred = true;
            }
        }
        if (!deferred) {
            return;
        }
        for (int i = 0; i < values.size() && i < specs.length; i++) {
            if (values.get(i) == null && specs[i] != null && sectionDefault(specs[i]) == null
                    && customDefault(specs[i]) == null && !specs[i].types().contains("script")
                    && specs[i].types().stream().map(registry::resolveType)
                            .noneMatch(r -> r != null && inOuterSection(r.type().id()))
                    && eventDefault(specs[i], false) == null && literalDefault(specs[i]) == null) {
                try {
                    missingDefault(specs[i]);
                } catch (CompileException missing) {
                    // The texts were still read first, asking for what's in them, though their errors come later.
                    readQuietly(values);
                    throw missing;
                }
            }
        }
    }

    /** Compiles the texts among {@code values}, writing nothing and dropping their errors. */
    private void readQuietly(List<Object> values) {
        org.objectweb.asm.MethodVisitor real = method;
        int slots = nextSlot;
        boolean order = skriptOrder;
        method = new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {};
        skriptOrder = true;
        try {
            for (Object value : values) {
                if (value instanceof Expr.Text text) {
                    try {
                        expression(text);
                    } catch (CompileException | RuntimeException ignored) {
                        // Reported, if at all, after the left-out value's error.
                    }
                }
            }
        } finally {
            method = real;
            nextSlot = slots;
            skriptOrder = order;
        }
    }

    /** A custom default value for the placeholder's type, from a section around this code, or {@code null}. */
    private Expr customDefault(PlaceholderSpec spec) {
        if (spec.nullable() || customDefaults.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Expr> custom : customDefaults) {
            for (String name : spec.types()) {
                TypeRef ref = registry.resolveType(name);
                if (ref != null && ref.type().id().equals(custom.getKey())) {
                    return custom.getValue();
                }
            }
        }
        return null;
    }

    /** The literal a left-out placeholder of its type is, as Skript's default expressions (a number is 1). */
    private Object literalDefault(PlaceholderSpec spec) {
        if (spec.nullable() || spec.types().size() != 1) {
            return null;
        }
        TypeRef ref = registry.resolveType(spec.types().getFirst());
        return ref == null ? null : library.defaultValue(ref.type().id());
    }

    /**
     * Fails when a placeholder left out has only the event's value to stand for it and the event has none, as
     * Skript's event value default expressions do ({@code hide the particles} outside a potion effect's section).
     */
    private void missingDefault(PlaceholderSpec spec) throws CompileException {
        if (spec.nullable()) {
            return;
        }
        for (String name : spec.types()) {
            TypeRef ref = registry.resolveType(name);
            if (ref == null || !library.hasEventDefault(ref.type().id())) {
                return;
            }
        }
        TypeRef first = registry.resolveType(spec.types().getFirst());
        if (first != null && !spec.types().isEmpty()) {
            // Skript's error from the first type's default expression, as it's the first logged.
            String type = first.type().id();
            throw new CompileException(line, library.eventDefault(eventClasses == null ? List.of() : eventClasses, type,
                    eventDescription()).error());
        }
    }

    /** The trigger's event as Skript's errors name it: {@code an on test case event}. */
    private String eventDescription() {
        String name = eventLabel != null ? eventLabel : "function";
        return article(name) + " " + name + " event";
    }

    /** Pushes the value {@code getter} gets from the trigger's event. */
    private void eventValue(java.util.function.Function<Object, Object> getter) {
        constant(getter, java.util.function.Function.class);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(TriggerContext.class), "event",
                "()" + OBJECT_DESC, true);
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/function/Function", "apply",
                "(" + OBJECT_DESC + ")" + OBJECT_DESC, true);
    }

    /**
     * {@code past event-block}, {@code event-item after the event}: an event value as it was before the event,
     * or will be after it; {@code past tool}: a library expression that takes that state
     * ({@code Registrar.timeStates}). Nothing else can be written so, nor anything after the event has passed.
     */
    private void timeState(Expr.Syntax syntax) throws CompileException {
        if (currentLine != null && script.delayBefore(currentLine) == co.xenastudios.neoskript.frontend.registry.DelayState.DELAYED) {
            throw new CompileException(line, "Cannot use time states after the event has already passed");
        }
        timed(syntax.syntax().match().values().getFirst(), syntax.syntax().pattern() < 2 ? -1 : 1, syntax);
    }

    /**
     * A placeholder with a time state, {@code %objects@-1%} in {@code X was Y}: as Skript's type pattern
     * elements apply it, a literal can't have one, nor anything after the event has passed.
     */
    private void timedArgument(Expr expr, int time) throws CompileException {
        if (expr instanceof Expr.Literal) {
            throw new CompileException(line, "Can't understand this condition/effect: " + source.strip());
        }
        if (currentLine != null && script.delayBefore(currentLine) == co.xenastudios.neoskript.frontend.registry.DelayState.DELAYED) {
            throw new CompileException(line, "Cannot use time states after the event has already passed");
        }
        if (expr instanceof Expr.Syntax state && isCore(state.syntax(), CoreSyntax.TIME_STATE)
                && (state.syntax().pattern() < 2 ? -1 : 1) == time) {
            // 'past spectator target was a zombie': already in that state, as Skript's time state has it.
            expression(expr);
            return;
        }
        timed(expr, time, expr);
    }

    private void timed(Object inner, int time, Expr whole) throws CompileException {
        if (hasTimeState(inner, time)) {
            timedValue((Expr.Syntax) inner, time);
            return;
        }
        // A list is in that state where its values have one, as in Skript: 'past event-block and event-player'.
        if (inner instanceof Expr.ListOf list && list.items().stream().anyMatch(item -> hasTimeState(item, time))) {
            pushInt(list.items().size());
            method.visitTypeInsn(ANEWARRAY, OBJECT);
            for (int i = 0; i < list.items().size(); i++) {
                method.visitInsn(DUP);
                pushInt(i);
                Expr item = list.items().get(i);
                if (hasTimeState(item, time)) {
                    timedValue((Expr.Syntax) item, time);
                } else {
                    expression(item);
                }
                method.visitInsn(AASTORE);
            }
            method.visitMethodInsn(INVOKESTATIC, VALUES, "list", "(" + OBJECTS + ")" + OBJECTS, false);
            return;
        }
        String text = inner instanceof Expr expr && expr.end() <= source.length()
                ? source.substring(expr.start(), expr.end()) : source.substring(whole.start(), whole.end());
        throw new CompileException(line, text + " does not have a " + (time < 0 ? "past" : "future") + " state");
    }

    /**
     * Whether {@code expr} has a state at {@code time} here: an event value with a past or future state in this
     * trigger's event, or a library expression that takes that time state here ({@code past tool}).
     */
    private boolean hasTimeState(Object expr, int time) {
        return hasTimeState(expr) || expr instanceof Expr.Syntax value
                && library.acceptsTime(value.syntax().syntax().id(), info(value.syntax()).withTime(time));
    }

    /** {@code expr}, which {@link #hasTimeState(Object, int)}, at {@code time}. */
    private void timedValue(Expr.Syntax expr, int time) throws CompileException {
        if (hasTimeState(expr)) {
            eventValue(expr, time);
            return;
        }
        ParsedSyntax outer = timedSyntax;
        int outerTime = timedTime;
        timedSyntax = expr.syntax();
        timedTime = time;
        try {
            expression(expr);
        } finally {
            timedSyntax = outer;
            timedTime = outerTime;
        }
    }

    /** Whether {@code expr} is an event value that has a past or future state in this trigger's event. */
    private boolean hasTimeState(Object expr) {
        return expr instanceof Expr.Syntax value && (isCore(value.syntax(), CoreSyntax.EVENT_VALUE)
                || isCore(value.syntax(), CoreSyntax.EVENT_VALUE_ENTITY))
                && value.syntax().match().regexValues().getFirst() instanceof TypeRef ref
                && eventClasses != null && library.hasTimeStates(eventClasses, request(value, ref).valueClass(),
                        request(value, ref).type())
                && !(writtenText(value).toLowerCase(Locale.ROOT).contains("event-")
                        && library.bareTimeStatesOnly(eventClasses, ref.type().id()));
    }

    /** What an event value asks for: the class of the named type, kind of entity, or for a plural, an array. */
    private Library.EventValueRequest request(Expr.Syntax syntax, TypeRef ref) {
        return library.request(ref, syntax.end() > source.length() ? null : source.substring(syntax.start(), syntax.end()));
    }

    /** {@code player}, {@code event-world}: the event's value of the named type. */
    private void eventValue(Expr.Syntax syntax) throws CompileException {
        eventValue(syntax, 0);
    }

    /** The event's value of the named type at {@code time}: -1 before the event, 1 after, 0 now. */
    private void eventValue(Expr.Syntax syntax, int time) throws CompileException {
        if (!(syntax.syntax().match().regexValues().getFirst() instanceof TypeRef ref)) {
            throw unsupported("'" + source.substring(syntax.start(), syntax.end()) + "'");
        }
        if (time != 0) {
            eventValue(lookup(syntax, ref, time));
            return;
        }
        Block section = sectionFor(ref.type().id());
        if (section != null) {
            // 'entity' in 'spawn a pig:' is the pig.
            element(section.valuesSlot(), section.indexSlot(), 0);
            return;
        }
        if (inOuterSection(ref.type().id())) {
            method.visitInsn(ACONST_NULL);
            return;
        }
        Block wider = widerSection(ref.type().id());
        if (wider != null) {
            // 'event-display' in 'spawn a text display:' is the entity, if it's a display.
            element(wider.valuesSlot(), wider.indexSlot(), 0);
            constant(library.javaClass(ref.type().id()), Class.class);
            method.visitMethodInsn(INVOKESTATIC, VALUES, "filter", "(" + OBJECT_DESC + "Ljava/lang/Class;)" + OBJECT_DESC,
                    false);
            return;
        }
        java.util.function.Function<Object, Object> getter;
        try {
            getter = lookup(syntax, ref, 0);
        } catch (CompileException none) {
            Object literal = objectsPlaceholder() ? eventLiteral(syntax) : null;
            if (literal == null) {
                throw none;
            }
            constant(literal, Object.class);
            return;
        }
        eventValue(getter);
    }

    /** Whether the value being compiled is for a placeholder that takes any object, like a comparison's. */
    private boolean objectsPlaceholder() {
        return literalTypes.stream().map(registry::resolveType)
                .anyMatch(ref -> ref != null && ref.type().id().equals(SyntaxRegistry.OBJECT));
    }

    /**
     * What an element that can't be set up where it is ({@code inventory action} in a join event) is instead,
     * where any object is wanted: its words as a value, as Skript reads words no expression takes. {@code
     * entity} in a death event is the entity type; {@code inventory action} in a join event the type.
     * {@code null} if they're no value, or the element can be set up.
     */
    private Object eventLiteral(Expr.Syntax syntax) {
        if (source == null || syntax.end() > source.length() || syntax.start() < 0) {
            return null;
        }
        String words = source.substring(syntax.start(), syntax.end()).strip();
        String lower = words.toLowerCase(Locale.ROOT);
        if (lower.startsWith("the ") || lower.startsWith("event-")) {
            return null;
        }
        if (isCore(syntax.syntax(), CoreSyntax.EVENT_VALUE)) {
            if (!(syntax.syntax().match().regexValues().getFirst() instanceof TypeRef ref)) {
                return null;
            }
            try {
                lookup(syntax, ref, 0);
                return null;
            } catch (CompileException none) {
                // not in the event
            }
        } else if (!syntax.syntax().match().values().isEmpty()
                || !(library.binding(syntax.syntax().syntax()) instanceof ElementFactory<?> factory) || setsUp(factory, syntax)) {
            return null;
        }
        // An event value's words may be another value ('entity' is the entity type); an element's only its type.
        Object value = null;
        if (isCore(syntax.syntax(), CoreSyntax.EVENT_VALUE)) {
            // As the types its words are first tried as, where there are any: in a comparison, what Skript reads.
            value = literalTypes == null ? null : wordsAs(words, literalTypes);
            if (value == null) {
                value = library.literal(words);
            }
        }
        if (value != null) {
            return value;
        }
        // A type's name alone is the type, as Skript's type literals.
        TypeRef type = registry.resolveType(words);
        Class<?> javaClass = type == null || type.plural() ? null : library.javaClass(type.type().id());
        return javaClass == null ? null : new co.xenastudios.neoskript.runtime.TypeValue(type.type().id(), javaClass);
    }

    /** Whether {@code factory} sets {@code syntax}'s element up where it is, its warnings dropped. */
    private boolean setsUp(ElementFactory<?> factory, Expr.Syntax syntax) {
        co.xenastudios.neoskript.api.element.Warnings.Sink outer = co.xenastudios.neoskript.api.element.Warnings.install(null);
        ParsedSyntax outerSettingUp = settingUp;
        String outerSource = settingUpSource;
        try {
            factory.create(info(syntax.syntax()));
            return true;
        } catch (RuntimeException refused) {
            return false;
        } finally {
            co.xenastudios.neoskript.api.element.Warnings.install(outer);
            settingUp = outerSettingUp;
            settingUpSource = outerSource;
        }
    }

    /** How to get the event value {@code syntax} names at {@code time}, as Skript resolves it, or its error. */
    private java.util.function.Function<Object, Object> lookup(Expr.Syntax syntax, TypeRef ref, int time)
            throws CompileException {
        Library.EventValueRequest request = request(syntax, ref);
        Library.EventValueLookup lookup = library.eventValue(eventClasses == null ? List.of() : eventClasses,
                request.valueClass(), request.type(), time, eventDescription());
        if (lookup.getter() == null && time == 0 && inSectionHeader()) {
            // An 'if' that fails is read again as a whole line, as Skript does, which asks for the value again unless
            // it's what the line starts with ('if player's tool' is no value): then the error is the second ask's.
            java.util.regex.Matcher conditional = java.util.regex.Pattern.compile("(?i)^\\s*(?:else )?if ").matcher(source);
            if (conditional.lookingAt() && syntax.start() > conditional.end()) {
                lookup = library.eventValue(eventClasses == null ? List.of() : eventClasses, request.valueClass(),
                        request.type(), time, eventDescription());
            }
        }
        if (lookup.getter() == null) {
            String asked = time == 0 ? library.takeParserAsked(eventClasses == null ? List.of() : eventClasses,
                    request.valueClass(), request.type()) : null;
            if (asked != null) {
                throw new CompileException(line, asked);
            }
            // Skript tries 'event-zombie' as a value named so first, whose error is kept.
            throw new CompileException(line, request.words() != null
                    ? "There's no " + request.words() + " in " + eventDescription() + "." : lookup.error());
        }
        return lookup.getter();
    }

    // ---------------------------------------------------------------- inline functions

    /**
     * Evaluates a list for an inline function into a values slot, and for a list variable its indices into
     * another (else -1). Returns {@code {valuesSlot, indicesSlot}}.
     */
    private int[] listValues(Expr list) throws CompileException {
        int valuesSlot = nextSlot++;
        int indicesSlot = -1;
        if (isKeyed(list, true)) {
            // Values with their indices, lined up: a list variable's, or a sorted one's.
            indicesSlot = nextSlot++;
            keyed(list, true);
            method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(KeyedValues.class), "of",
                    "(" + OBJECT_DESC + ")" + Type.getDescriptor(KeyedValues.class), false);
            method.visitInsn(DUP);
            method.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(KeyedValues.class), "values", "()" + OBJECTS, false);
            method.visitVarInsn(ASTORE, valuesSlot);
            method.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(KeyedValues.class), "keys",
                    "()[Ljava/lang/String;", false);
            method.visitVarInsn(ASTORE, indicesSlot);
        } else {
            expression(list);
            convert(true);
            method.visitVarInsn(ASTORE, valuesSlot);
        }
        return new int[] {valuesSlot, indicesSlot};
    }

    @FunctionalInterface
    private interface PerInput {
        void emit(InputFrame frame, Label next) throws CompileException;
    }

    /** Loops over a list's values from {@code start}, with each as {@code input}, running {@code body}. */
    private void forEachInput(int[] list, int start, int reducedSlot, PerInput body) throws CompileException {
        int indexSlot = nextSlot++;
        int inputSlot = nextSlot++;
        pushInt(start);
        method.visitVarInsn(ISTORE, indexSlot);
        Label top = new Label();
        Label next = new Label();
        Label end = new Label();
        method.visitLabel(top);
        method.visitVarInsn(ILOAD, indexSlot);
        method.visitVarInsn(ALOAD, list[0]);
        method.visitInsn(ARRAYLENGTH);
        method.visitJumpInsn(IF_ICMPGE, end);
        method.visitVarInsn(ALOAD, list[0]);
        method.visitVarInsn(ILOAD, indexSlot);
        method.visitInsn(AALOAD);
        method.visitVarInsn(ASTORE, inputSlot);
        InputFrame frame = new InputFrame(inputSlot, indexSlot, list[1], reducedSlot);
        inputs.push(frame);
        try {
            body.emit(frame, next);
        } finally {
            inputs.pop();
        }
        method.visitLabel(next);
        method.visitIincInsn(indexSlot, 1);
        method.visitJumpInsn(GOTO, top);
        method.visitLabel(end);
    }

    private int newList() {
        int slot = nextSlot++;
        method.visitTypeInsn(NEW, "java/util/ArrayList");
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        method.visitVarInsn(ASTORE, slot);
        return slot;
    }

    private void listToArray(int slot) {
        method.visitVarInsn(ALOAD, slot);
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "toArray", "()" + OBJECTS, true);
    }

    /** {@code %objects% where [condition]}: the values the condition holds for. */
    private void filter(ParsedSyntax syntax) throws CompileException {
        if (!(syntax.match().regexValues().getFirst() instanceof ParsedSyntax condition)) {
            throw unsupported("'" + source + "'");
        }
        int[] list = listValues((Expr) syntax.match().values().getFirst());
        int result = newList();
        forEachInput(list, 0, -1, (frame, next) -> {
            condition(condition);
            method.visitJumpInsn(IFEQ, next);
            method.visitVarInsn(ALOAD, result);
            method.visitVarInsn(ALOAD, frame.valueSlot());
            method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(" + OBJECT_DESC + ")Z", true);
            method.visitInsn(POP);
        });
        listToArray(result);
    }

    /** {@code %objects% transformed using [expression]}: every value the expression gives, for each value. */
    private void transform(Expr values, Expr mapping) throws CompileException {
        int[] list = listValues(values);
        int result = newList();
        forEachInput(list, 0, -1, (frame, next) -> {
            method.visitVarInsn(ALOAD, result);
            expression(mapping);
            method.visitMethodInsn(INVOKESTATIC, VALUES, "addAll", "(Ljava/util/List;" + OBJECT_DESC + ")V", false);
        });
        listToArray(result);
    }

    /** {@code %objects% reduced with [expression]}: the values folded into one, starting from the first. */
    private void reduce(ParsedSyntax syntax) throws CompileException {
        Expr folding = (Expr) syntax.match().regexValues().getFirst();
        int[] list = listValues((Expr) syntax.match().values().getFirst());
        int reduced = nextSlot++;
        method.visitVarInsn(ALOAD, list[0]);
        pushInt(0);
        pushInt(0);
        method.visitMethodInsn(INVOKESTATIC, VALUES, "element", "(" + OBJECTS + "II)" + OBJECT_DESC, false);
        method.visitVarInsn(ASTORE, reduced);
        // A step giving nothing is skipped, and reported once at the end, as in Skript.
        int skipped = nextSlot++;
        method.visitInsn(ICONST_0);
        method.visitVarInsn(ISTORE, skipped);
        forEachInput(list, 1, reduced, (frame, next) -> {
            expression(folding);
            convert(false);
            Label nothing = new Label();
            Label done = new Label();
            method.visitInsn(DUP);
            method.visitJumpInsn(IFNULL, nothing);
            method.visitVarInsn(ASTORE, reduced);
            method.visitJumpInsn(GOTO, done);
            method.visitLabel(nothing);
            method.visitInsn(POP);
            method.visitInsn(ICONST_1);
            method.visitVarInsn(ISTORE, skipped);
            method.visitLabel(done);
        });
        Label fine = new Label();
        method.visitVarInsn(ILOAD, skipped);
        method.visitJumpInsn(IFEQ, fine);
        // A warning in Skript, from the reduce expression.
        method.visitLdcInsn("The reduce expression returned null for one or more elements, which were skipped.");
        site(syntax, co.xenastudios.neoskript.runtime.RuntimeErrors.class);
        method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(co.xenastudios.neoskript.runtime.RuntimeErrors.class),
                "warning", "(" + STRING_DESC + ")V", false);
        method.visitLabel(fine);
        method.visitVarInsn(ALOAD, reduced);
    }

    /** The values of a list variable sorted by a key worked out for each: {@code sort {_x::*} by [...]}. */
    private void sortedByKeys(Expr.Variable list, Expr key, boolean descending) throws CompileException {
        int[] values = listValues(list);
        int keys = nextSlot++;
        method.visitVarInsn(ALOAD, values[0]);
        method.visitInsn(ARRAYLENGTH);
        method.visitTypeInsn(ANEWARRAY, OBJECT);
        method.visitVarInsn(ASTORE, keys);
        forEachInput(values, 0, -1, (frame, next) -> {
            method.visitVarInsn(ALOAD, keys);
            method.visitVarInsn(ILOAD, frame.indexSlot());
            expression(key);
            convert(false);
            method.visitInsn(AASTORE);
        });
        method.visitVarInsn(ALOAD, values[0]);
        method.visitVarInsn(ALOAD, keys);
        method.visitInsn(descending ? ICONST_1 : ICONST_0);
        method.visitMethodInsn(INVOKESTATIC, VALUES, "sortByKeys", "(" + OBJECTS + OBJECTS + "Z)" + OBJECTS, false);
    }

    /** {@code filter %~objects% to match [any|all]:} with conditions: keeps the values they hold for. */
    private void filterSection(Statement.Section section) throws CompileException {
        Expr target = (Expr) section.header().match().values().getFirst();
        boolean any = section.header().match().hasTag("any");
        List<ParsedSyntax> conditions = new ArrayList<>();
        for (Statement statement : section.body()) {
            if (!(statement instanceof Statement.Condition condition)) {
                throw new CompileException(statement.line().number(), "A filter section can only contain conditions");
            }
            conditions.add(condition.condition());
        }
        setTo(target, () -> {
            int[] list = listValues(target);
            int result = newList();
            forEachInput(list, 0, -1, (frame, next) -> {
                Label keep = new Label();
                for (ParsedSyntax condition : conditions) {
                    condition(condition);
                    method.visitJumpInsn(any ? IFNE : IFEQ, any ? keep : next);
                }
                if (any) {
                    method.visitJumpInsn(GOTO, next);
                }
                method.visitLabel(keep);
                method.visitVarInsn(ALOAD, result);
                method.visitVarInsn(ALOAD, frame.valueSlot());
                method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "add", "(" + OBJECT_DESC + ")Z", true);
                method.visitInsn(POP);
            });
            listToArray(result);
        });
    }

    /** {@code input}, {@code string input}, {@code input index}: the value an inline function is looking at. */
    private void input(Expr.Syntax syntax) throws CompileException {
        InputFrame frame = inputs.peek();
        if (frame == null) {
            throw new CompileException(line, "'" + source.substring(syntax.start(), syntax.end())
                    + "' can only be used in 'where [...]', 'transformed using [...]' and the like");
        }
        switch (syntax.syntax().pattern()) {
            case 2 -> {
                if (frame.indicesSlot() >= 0) {
                    element(frame.indicesSlot(), frame.indexSlot(), 0);
                } else {
                    method.visitVarInsn(ILOAD, frame.indexSlot());
                    method.visitInsn(I2L);
                    method.visitInsn(LCONST_1);
                    method.visitInsn(LADD);
                    method.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(J)Ljava/lang/String;", false);
                }
            }
            case 1 -> {
                method.visitVarInsn(ALOAD, frame.valueSlot());
                if (syntax.syntax().match().regexValues().getFirst() instanceof TypeRef ref) {
                    constant(library.javaClass(ref.type().id()), Class.class);
                    method.visitMethodInsn(INVOKESTATIC, VALUES, "filter", "(" + OBJECT_DESC + "Ljava/lang/Class;)" + OBJECT_DESC, false);
                }
            }
            default -> method.visitVarInsn(ALOAD, frame.valueSlot());
        }
    }

    /** The list variable a syntax element works on, such as the {@code {x::*}} in {@code sort {x::*}}. */
    private Expr.Variable listVariable(ParsedSyntax syntax, String problem) throws CompileException {
        Object value = syntax.match().values().getFirst();
        // 'recursive keyed {_x::*}' is the list itself, with its indices.
        if (value instanceof Expr.Syntax keyed && keyed.syntax().syntax().id().equals(KEYED)) {
            value = keyed.syntax().match().values().getFirst();
        }
        if (value instanceof Expr.Variable variable && variable.list()) {
            return variable;
        }
        throw new CompileException(line, problem + ": '" + source + "'");
    }

    /**
     * For {@code recursive indices of {_x::*}}, the indices expression. Skript's indices pass {@code recursive}
     * on to their list, but have already decided by then to give only the first part of each index, so it's
     * the same as {@code indices of {_x::*}}.
     */
    private Expr recursiveIndices(ParsedSyntax syntax) {
        return isCore(syntax, CoreSyntax.RECURSIVE) && syntax.match().values().getFirst() instanceof Expr.Syntax inner
                && isCore(inner.syntax(), CoreSyntax.INDICES) ? inner : null;
    }

    /** Whether {@code syntax} is {@code recursive} written around {@code keyed}: {@code recursive keyed {_x::*}}. */
    private static boolean recursiveOfKeyed(ParsedSyntax syntax) {
        return syntax.match().values().getFirst() instanceof Expr.Syntax keyed
                && keyed.syntax().syntax().id().equals(KEYED);
    }

    // ---------------------------------------------------------------- command arguments

    private void commandArgument(int index) {
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        pushInt(index);
        method.visitMethodInsn(INVOKESTATIC, COMMAND_EVENT, "argument", "(" + CONTEXT + "I)" + OBJECT_DESC, false);
    }

    /** The Bukkit events command arguments can be used in, besides script commands, as Skript's ExprArgument. */
    private static final String[] COMMAND_EVENTS = {"org.bukkit.event.player.PlayerCommandPreprocessEvent",
            "org.bukkit.event.server.ServerCommandEvent", "org.bukkit.event.command.UnknownCommandEvent"};

    private List<CommandPattern.Argument> commandArguments(Expr.Syntax syntax) throws CompileException {
        if (commandArguments == null) {
            // Skript's ExprArgument works in script commands and their suggestions, and in command events.
            String events = library.eventNames(COMMAND_EVENTS);
            throw new CompileException(line, "'" + source.substring(syntax.start(), syntax.end())
                    + "' can only be used in " + (events.isEmpty() ? "a command" : events));
        }
        return commandArguments;
    }

    /** The number in {@code 2nd} or {@code 12}, or 1 to 3 for {@code first} to {@code third}. */
    private static int ordinal(ParsedSyntax syntax) {
        if (syntax.match().hasTag("first")) {
            return 1;
        } else if (syntax.match().hasTag("second")) {
            return 2;
        } else if (syntax.match().hasTag("third")) {
            return 3;
        }
        String text = ((java.util.regex.MatchResult) syntax.match().regexValues().getFirst()).group();
        return Integer.parseInt(text.replaceAll("\\D", ""));
    }

    private static String ordinalText(int n) {
        String suffix = (n % 100 >= 11 && n % 100 <= 13) ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }

    /** {@code arg-1}, {@code the second argument}, {@code the last argument}, {@code the arguments}. */
    private void argument(Expr.Syntax expr) throws CompileException {
        if (commandArguments == null && inCommandEvent()) {
            ParsedSyntax syntax = expr.syntax();
            method.visitVarInsn(ALOAD, CONTEXT_SLOT);
            pushInt(switch (syntax.pattern()) {
                case 0 -> CommandEvent.LAST;
                case 1, 2 -> CommandEvent.ORDINAL;
                default -> syntax.match().hasTag("all") ? CommandEvent.ALL : CommandEvent.SINGLE;
            });
            pushInt(syntax.pattern() == 1 || syntax.pattern() == 2 ? ordinal(syntax) : -1);
            method.visitMethodInsn(INVOKESTATIC, COMMAND_EVENT, "eventArgument", "(" + CONTEXT + "II)" + OBJECT_DESC,
                    false);
            return;
        }
        commandArgument(argumentIndex(expr));
    }

    /**
     * Whether this is a command event, whose arguments are the words typed after the command, as Skript's
     * ExprArgument takes them outside script commands.
     */
    private boolean inCommandEvent() {
        if (eventClasses == null) {
            return false;
        }
        for (Class<?> eventClass : eventClasses) {
            for (Class<?> c = eventClass; c != null; c = c.getSuperclass()) {
                if (List.of(COMMAND_EVENTS).contains(c.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The number (from 0) of the argument {@code expr} ({@code arg-1}, {@code the text argument}) is, or -1 for
     * {@code the argument} in a command with several: all of them.
     */
    private int argumentIndex(Expr.Syntax expr) throws CompileException {
        List<CommandPattern.Argument> arguments = commandArguments(expr);
        ParsedSyntax syntax = expr.syntax();
        if (isCore(syntax, CoreSyntax.TYPED_ARGUMENT)) {
            return typedArgumentIndex(expr, arguments);
        }
        int n = switch (syntax.pattern()) {
            case 0 -> arguments.size();
            case 1, 2 -> ordinal(syntax);
            default -> -1;
        };
        if (n == -1) {
            if (arguments.isEmpty()) {
                throw new CompileException(line, "This command doesn't have any arguments");
            }
            if (syntax.match().hasTag("all")) {
                throw new CompileException(line, "'arguments' cannot be used for script commands. Use 'argument 1',"
                        + " 'argument 2', etc. instead");
            }
            if (arguments.size() > 1) {
                throw new CompileException(line, "This command has multiple arguments, meaning it is not possible to"
                        + " get the 'argument'. Use 'argument 1', 'argument 2', etc. instead");
            }
            return 0;
        }
        if (n < 1 || n > arguments.size()) {
            throw new CompileException(line, arguments.isEmpty() ? "This command doesn't have any arguments"
                    : "This command doesn't have a " + ordinalText(n) + " argument");
        }
        return n - 1;
    }

    // ---------------------------------------------------------------- command suggestions

    /**
     * Fails, as Skript's event-restricted syntax does, unless this is a command's {@code suggestions:} entry.
     * Skript names the events the syntax can be used in, and as its suggestions event isn't one, names none.
     */
    private void suggestionsOnly(Expr.Syntax syntax) throws CompileException {
        if (suggestingArguments == null) {
            throw new CompileException(line, "'" + source.substring(syntax.start(), syntax.end())
                    + "' can only be used in ");
        }
    }

    private void suggestionsOnly(ParsedSyntax effect) throws CompileException {
        if (suggestingArguments == null) {
            throw new CompileException(line, "'" + source.strip() + "' can only be used in ");
        }
    }

    /** The number of the argument {@code the suggestions for arg-1} is about, checked as Skript does. */
    private int suggestionsArgument(Expr.Syntax suggestions) throws CompileException {
        suggestionsOnly(suggestions);
        Object of = suggestions.syntax().match().values().getFirst();
        if (!(of instanceof Expr.Syntax argument) || !(isCore(argument.syntax(), CoreSyntax.ARGUMENT)
                || isCore(argument.syntax(), CoreSyntax.TYPED_ARGUMENT))) {
            throw new CompileException(line, "It is only possible to obtain or change command suggestions of an"
                    + " argument! Is the argument expression written correctly?");
        }
        return argumentIndex(argument);
    }

    /** {@code set the suggestions for arg-1 to "a" and "b"}, and adding, removing, deleting and resetting them. */
    private void changeSuggestions(ParsedSyntax effect, CoreSyntax.ChangeMode mode, Expr.Syntax target)
            throws CompileException {
        int argument = suggestionsArgument(target);
        Changeable.Mode changeMode = changeMode(effect, mode);
        if (changeMode == Changeable.Mode.REMOVE_ALL) {
            throw new CompileException(line, source.substring(target.start(), target.end())
                    + " can't have anything removed from it");
        }
        suggestingArguments.add(argument);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        pushInt(argument);
        method.visitLdcInsn(changeMode.name().toLowerCase(java.util.Locale.ROOT));
        if (mode.value >= 0) {
            expression((Expr) effect.match().values().get(mode.value));
            convert(true);
        } else {
            method.visitInsn(ACONST_NULL);
        }
        method.visitMethodInsn(INVOKESTATIC, SUGGESTION_EVENT, "changeSuggestions",
                "(" + CONTEXT + "I" + STRING_DESC + OBJECTS + ")V", false);
    }

    /** {@code player-arg}, {@code the text argument 2}: the Nth argument of that type. */
    private void typedArgument(Expr.Syntax expr) throws CompileException {
        if (commandArguments == null && inCommandEvent()) {
            throw new CompileException(line, "Command event arguments are strings, meaning type specification is useless");
        }
        commandArgument(argumentIndex(expr));
    }

    private int typedArgumentIndex(Expr.Syntax expr, List<CommandPattern.Argument> arguments) throws CompileException {
        List<Object> regexes = expr.syntax().match().regexValues();
        if (!(regexes.getFirst() instanceof TypeRef ref)) {
            throw unsupported("'" + source.substring(expr.start(), expr.end()) + "'");
        }
        // As Skript's ExprArgument, whose errors name the kind of argument expression (CLASSINFO), and which
        // takes the second of two arguments of the type when no number is given.
        int ordinal = regexes.size() > 1 && regexes.get(1) instanceof java.util.regex.MatchResult number
                ? Integer.parseInt(number.group()) : -1;
        if (ordinal > arguments.size()) {
            throw new CompileException(line, "This command doesn't have a " + ordinalText(ordinal) + " "
                    + ref.type().name() + " argument");
        }
        int found = -1;
        int amount = 0;
        for (int i = 0; i < arguments.size(); i++) {
            String type = arguments.get(i).type();
            if (!type.equals(ref.type().id()) && !registry.isSubtype(type, ref.type().id())) {
                continue;
            }
            if (ordinal == -1 && amount == 2) {
                throw new CompileException(line, "There are multiple CLASSINFO arguments in this command");
            }
            found = i;
            if (++amount == ordinal) {
                break;
            }
        }
        if (amount == 0) {
            throw new CompileException(line, "There is no CLASSINFO argument in this command");
        } else if (ordinal > amount) {
            throw new CompileException(line, amount == 1 ? "There is only one CLASSINFO argument in this command"
                    : "There are only " + amount + " CLASSINFO arguments in this command");
        }
        return found;
    }

    /** {@code the first choice argument}: the text typed for a choice such as {@code (give|take)}. */
    private void choiceArgument(Expr.Syntax expr) throws CompileException {
        commandArguments(expr);
        ParsedSyntax syntax = expr.syntax();
        int n = syntax.pattern() == 2 ? commandChoices : syntax.pattern() == 0
                ? Integer.parseInt(((java.util.regex.MatchResult) syntax.match().regexValues().getFirst()).group())
                : ordinal(syntax);
        if (n < 1 || n > commandChoices) {
            throw new CompileException(line, commandChoices == 0 ? "This command doesn't have any choices"
                    : "This command doesn't have a " + ordinalText(n) + " choice");
        }
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        pushInt(n - 1);
        method.visitMethodInsn(INVOKESTATIC, COMMAND_EVENT, "choice", "(" + CONTEXT + "I)" + OBJECT_DESC, false);
    }

    static String article(String word) {
        String own = ARTICLES.get(word);
        if (own != null) {
            return own;
        }
        return !word.isEmpty() && "aeiou".indexOf(Character.toLowerCase(word.charAt(0))) >= 0 ? "an" : "a";
    }

    /** The types Skript's {@code default.lang} gives an article other than the usual one: {@code a uuid}. */
    private static final Map<String, String> ARTICLES = Map.of("uuid", "a", "attribute type", "a", "potion cause", "an",
            "potion action", "an");

    /** Converts the value on the stack to a plural ({@code Object[]}) or singular argument. */
    private void convert(boolean plural) {
        method.visitMethodInsn(INVOKESTATIC, VALUES, plural ? "array" : "single",
                "(" + OBJECT_DESC + ")" + (plural ? OBJECTS : OBJECT_DESC), false);
    }

    private boolean isPlural(PlaceholderSpec spec) {
        for (String name : spec.types()) {
            TypeRef ref = registry.resolveType(name);
            if (ref != null ? ref.plural() : name.strip().endsWith("s")) {
                return true;
            }
        }
        return false;
    }

    private static PlaceholderSpec[] placeholderSpecs(CompiledPattern pattern) {
        PlaceholderSpec[] specs = new PlaceholderSpec[pattern.placeholderCount()];
        collect(pattern.root(), specs);
        return specs;
    }

    private static void collect(PatternElement element, PlaceholderSpec[] specs) {
        switch (element) {
            case PatternElement.Placeholder placeholder -> specs[placeholder.index()] = placeholder.spec();
            case PatternElement.Sequence sequence -> sequence.elements().forEach(e -> collect(e, specs));
            case PatternElement.Choice choice -> choice.alternatives().forEach(a -> collect(a.body(), specs));
            case PatternElement.Optional optional -> collect(optional.choice(), specs);
            default -> {}
        }
    }

    // ---------------------------------------------------------------- expressions

    /** Pushes the expression's value as an {@code Object} (an {@code Object[]} for several values). */
    private void expression(Expr expr) throws CompileException {
        Integer worked = precomputed.get(expr);
        if (worked != null) {
            method.visitVarInsn(ALOAD, worked);
            return;
        }
        if (outerArgument == null && source != null && expr.start() >= 0 && expr.end() <= source.length()
                && expr.start() < expr.end()) {
            // The outermost expression: errors about words in it nothing parses name all of it, as Skript's do.
            outerArgument = source.substring(expr.start(), expr.end()).strip();
            try {
                compileExpression(expr);
            } finally {
                outerArgument = null;
            }
            return;
        }
        compileExpression(expr);
    }

    private void compileExpression(Expr expr) throws CompileException {
        switch (expr) {
            case Expr.Literal literal -> literal(literal);
            case Expr.Text text -> text(text.start(), text.end(), text.embedded());
            case Expr.ListOf list -> {
                pushInt(list.items().size());
                method.visitTypeInsn(ANEWARRAY, OBJECT);
                inList++;
                try {
                    for (int i = 0; i < list.items().size(); i++) {
                        method.visitInsn(DUP);
                        pushInt(i);
                        expression(list.items().get(i));
                        method.visitInsn(AASTORE);
                    }
                } finally {
                    inList--;
                }
                method.visitMethodInsn(INVOKESTATIC, VALUES, "list", "(" + OBJECTS + ")" + OBJECTS, false);
            }
            case Expr.Variable variable -> {
                variableStore(variable);
                variableName(variable);
                if (variable.list()) {
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "list", "(" + STRING_DESC + ")" + OBJECTS, false);
                } else {
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "get", "(" + STRING_DESC + ")" + OBJECT_DESC, false);
                }
            }
            case Expr.FunctionCall call -> call(call);
            case Expr.Syntax syntax when isArithmetic(syntax) -> arithmetic(syntax);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.LOOP_VALUE) -> loopValue(syntax.syntax());
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.EVENT_VALUE)
                    || isCore(syntax.syntax(), CoreSyntax.EVENT_VALUE_ENTITY)
                    || isCore(syntax.syntax(), CoreSyntax.EVENT_ENTITY_BARE) -> eventValue(syntax);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.TIME_STATE) -> timeState(syntax);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.ARGUMENT) -> argument(syntax);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.COMMAND_SUGGESTIONS) -> {
                int argument = suggestionsArgument(syntax);
                method.visitVarInsn(ALOAD, CONTEXT_SLOT);
                pushInt(argument);
                method.visitMethodInsn(INVOKESTATIC, SUGGESTION_EVENT, "suggestions", "(" + CONTEXT + "I)" + OBJECTS,
                        false);
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.COMMAND_INPUT) -> {
                suggestionsOnly(syntax);
                method.visitVarInsn(ALOAD, CONTEXT_SLOT);
                pushInt(syntax.syntax().pattern());
                method.visitMethodInsn(INVOKESTATIC, SUGGESTION_EVENT, "input", "(" + CONTEXT + "I)" + OBJECT_DESC,
                        false);
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.FILTER) -> filter(syntax.syntax());
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.ANY_OF) ->
                    expression((Expr) syntax.syntax().match().values().getFirst());
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.RECURSIVE_SIZE) -> {
                Expr.Variable list = listVariable(syntax.syntax(), "Only list variables have a recursive size");
                variableStore(list);
                variableName(list);
                method.visitMethodInsn(INVOKEVIRTUAL, STORE, "recursiveList", "(" + STRING_DESC + ")" + OBJECTS, false);
                method.visitInsn(ARRAYLENGTH);
                method.visitInsn(I2L);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            }
            case Expr.Syntax syntax when recursiveIndices(syntax.syntax()) != null ->
                    expression(recursiveIndices(syntax.syntax()));
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.RECURSIVE) -> {
                Expr.Variable list = listVariable(syntax.syntax(), "Only list variables can be recursive");
                variableStore(list);
                variableName(list);
                method.visitMethodInsn(INVOKEVIRTUAL, STORE, "recursiveList", "(" + STRING_DESC + ")" + OBJECTS, false);
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.CURRENT_SCRIPT) ->
                    constant(new co.xenastudios.neoskript.runtime.ScriptRef(constantsScript), Object.class);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.TRANSFORM) -> transform(
                    (Expr) syntax.syntax().match().values().getFirst(), (Expr) syntax.syntax().match().regexValues().getFirst());
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.REDUCE) -> reduce(syntax.syntax());
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.INPUT) -> input(syntax);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.REDUCED_VALUE) -> {
                InputFrame frame = inputs.peek();
                if (frame == null || frame.reducedSlot() < 0) {
                    throw new CompileException(line, "'reduced value' can only be used in 'reduced with [...]'");
                }
                method.visitVarInsn(ALOAD, frame.reducedSlot());
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.CAUGHT_ERRORS) -> method.visitMethodInsn(
                    INVOKESTATIC, Type.getInternalName(co.xenastudios.neoskript.runtime.RuntimeErrors.class), "lastCaught",
                    "()" + OBJECTS, false);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.WHETHER) -> {
                if (!(syntax.syntax().match().regexValues().getFirst() instanceof ParsedSyntax condition)) {
                    throw unsupported("'" + source.substring(syntax.start(), syntax.end()) + "'");
                }
                condition(condition);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.PARSE_PATTERN) -> parsePattern(syntax);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.PARSE) -> {
                if (!(syntax.syntax().match().regexValues().getFirst() instanceof TypeRef ref)) {
                    throw unsupported("'" + source.substring(syntax.start(), syntax.end()) + "'");
                }
                java.util.function.Function<String, ?> parser = library.parser(ref.type().id());
                if (parser == null) {
                    throw new CompileException(line, "Text can't be parsed as " + article(ref.type().name()) + " "
                            + ref.type().name());
                }
                constant(parser, java.util.function.Function.class);
                expression((Expr) syntax.syntax().match().values().getFirst());
                method.visitLdcInsn(article(ref.type().name()) + " " + ref.type().name());
                method.visitMethodInsn(INVOKESTATIC, VALUES, "parse",
                        "(Ljava/util/function/Function;" + OBJECT_DESC + STRING_DESC + ")" + OBJECT_DESC, false);
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.INDICES) -> {
                Expr.Variable list = listVariable(syntax.syntax(), "Only list variables have indices");
                variableStore(list);
                variableName(list);
                if (syntax.syntax().pattern() < 2) {
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "indices", "(" + STRING_DESC + ")[" + STRING_DESC, false);
                } else {
                    method.visitInsn(syntax.syntax().match().hasTag("descending") ? ICONST_1 : ICONST_0);
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "sortedIndices",
                            "(" + STRING_DESC + "Z)[" + STRING_DESC, false);
                }
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.TYPED_ARGUMENT) -> typedArgument(syntax);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.CHOICE_ARGUMENT) -> choiceArgument(syntax);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.TERNARY) -> {
                if (!(syntax.syntax().match().regexValues().getFirst() instanceof ParsedSyntax condition)) {
                    throw unsupported("'" + source + "'");
                }
                Label otherwise = new Label();
                Label done = new Label();
                condition(condition);
                method.visitJumpInsn(IFEQ, otherwise);
                expression((Expr) syntax.syntax().match().values().get(0));
                method.visitJumpInsn(GOTO, done);
                method.visitLabel(otherwise);
                expression((Expr) syntax.syntax().match().values().get(1));
                method.visitLabel(done);
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.TYPE_VALUE) -> {
                Class<?> type = library.javaClass(syntax.type());
                if (type == null) {
                    throw unsupported("the type " + syntax.type());
                }
                constant(new co.xenastudios.neoskript.runtime.TypeValue(syntax.type(), type), Object.class);
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.SETS) -> {
                List<Object> all = library.namedValues(syntax.type());
                java.util.function.Supplier<Object[]> listed = all == null ? library.allValues(syntax.type()) : null;
                if (listed != null) {
                    // Listed when it runs, as the server's registries say.
                    constant(listed, java.util.function.Supplier.class);
                    method.visitMethodInsn(INVOKEINTERFACE, "java/util/function/Supplier", "get", "()" + OBJECT_DESC, true);
                    method.visitTypeInsn(CHECKCAST, OBJECTS);
                    break;
                }
                if (all == null) {
                    throw unsupported("all " + syntax.type() + "s");
                }
                // A copy each time, so nothing can change the type's values.
                constant(all.toArray(), Object[].class);
                method.visitMethodInsn(INVOKEVIRTUAL, OBJECTS, "clone", "()" + OBJECT_DESC, false);
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.RANDOM) -> {
                expression((Expr) syntax.syntax().match().values().getFirst());
                Class<?> type = library.javaClass(syntax.type());
                constant(type == null ? Object.class : type, Class.class);
                // Values that convert to the type count too, as in Skript: 'random item out of any boots'.
                method.visitMethodInsn(INVOKESTATIC, VALUES, "castConverting", "(" + OBJECT_DESC + "Ljava/lang/Class;)"
                        + OBJECTS, false);
                method.visitMethodInsn(INVOKESTATIC, VALUES, "random", "(" + OBJECTS + ")" + OBJECT_DESC, false);
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.CAST) -> {
                Expr within = (Expr) syntax.syntax().match().values().getFirst();
                // As Skript's ExprValueWithin: one value from what may hold more, or several from one, is refused.
                Object named = syntax.syntax().match().regexValues().isEmpty() ? null
                        : syntax.syntax().match().regexValues().getFirst();
                boolean plural = named instanceof TypeRef ref ? ref.plural() : syntax.syntax().match().hasTag("s");
                boolean single = !co.xenastudios.neoskript.frontend.parse.Plurality.plural(within, registry);
                if (plural == single) {
                    throw new CompileException(line, plural ? "You cannot get multiple elements of a single value"
                            : describe(within) + " may contain more than one "
                                    + (named instanceof TypeRef ref ? ref.type().name() : "value"));
                }
                expression(within);
                constant(library.javaClass(syntax.type()), Class.class);
                method.visitMethodInsn(INVOKESTATIC, VALUES, "cast", "(" + OBJECT_DESC + "Ljava/lang/Class;)" + OBJECTS,
                        false);
            }
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.PARSE_LOGS) ->
                    method.visitVarInsn(ALOAD, PARSE_LOGS_SLOT);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.HAS_DELAY) -> {
                // Known while compiling: whether the trigger may have waited before this line.
                String state = CoreSyntax.kleenean(script.delayBefore(currentLine));
                if (state.equals("unknown")) {
                    method.visitLdcInsn(state);
                } else {
                    method.visitFieldInsn(GETSTATIC, "java/lang/Boolean", state.toUpperCase(Locale.ROOT),
                            "Ljava/lang/Boolean;");
                }
            }
            case Expr.Syntax syntax when objectsPlaceholder() && eventLiteral(syntax) != null ->
                    constant(eventLiteral(syntax), Object.class);
            case Expr.Syntax syntax -> {
                boolean keyAware = keyAware(syntax);
                callThrough(syntax.syntax(), Expression.class, keyAware, keyAware && implicitKeys(syntax));
                method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Expression.class), "get",
                        "(" + CONTEXT + OBJECTS + ")" + OBJECT_DESC, true);
                if (keyAware) {
                    method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(KeyedValues.class), "plain",
                            "(" + OBJECT_DESC + ")" + OBJECT_DESC, false);
                }
            }
            case Expr.Unresolved unresolved -> {
                // Words are first tried as the placeholder's own types: 'armor stand' is an entity type in
                // 'spawn an armor stand', though it names an item too.
                Object value = wordsAs(unresolved.text(), literalTypes);
                if (value == null) {
                    value = library.literal(unresolved.text());
                    if (value != null) {
                        if (inList == 0 && callArguments == 0) {
                            multipleTypesWarning(unresolved.text().strip());
                        }
                    }
                }
                if (value == null && library.literalError(unresolved.text()) != null) {
                    // Words Skript has a clearer error for: 'dust particle' needs its colour.
                    throw new CompileException(line, library.literalError(unresolved.text()));
                }
                if (value == null) {
                    throw new CompileException(line, unknownWords(unresolved), true);
                }
                constant(value, Object.class);
            }
        }
    }

    /** What {@code words} are as the first of {@code types} whose parser reads them, or {@code null}. */
    private Object wordsAs(String words, List<String> types) {
        for (String name : types) {
            TypeRef ref = registry.resolveType(name);
            java.util.function.Function<String, ?> parser = ref == null ? null : library.literalParser(ref.type().id());
            if (parser == null && ref != null && library.namedValues(ref.type().id()) != null) {
                // A named type's words: 'normal' is the environment where an environment is compared.
                parser = library.parser(ref.type().id());
            }
            Object value = parser == null ? null : parser.apply(words.strip());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private void literal(Expr.Literal literal) throws CompileException {
        String text = literal.text().replace("_", "");
        switch (literal.type()) {
            case "integer" -> {
                String digits = text.startsWith("+") ? text.substring(1) : text;
                try {
                    method.visitLdcInsn(Long.parseLong(digits));
                    method.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                } catch (NumberFormatException e) {
                    // Too big for a whole number: a decimal one, as in Skript.
                    method.visitLdcInsn(Double.parseDouble(digits));
                    method.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                }
            }
            case "number" -> {
                double value = text.endsWith("%")
                        ? Double.parseDouble(text.substring(0, text.length() - 1)) / 100
                        : Double.parseDouble(text);
                method.visitLdcInsn(value);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
            }
            case "boolean" -> {
                boolean value = switch (text.toLowerCase(Locale.ROOT)) {
                    case "true", "yes", "on" -> true;
                    default -> false;
                };
                method.visitFieldInsn(GETSTATIC, "java/lang/Boolean", value ? "TRUE" : "FALSE", "Ljava/lang/Boolean;");
            }
            case "time" -> {
                co.xenastudios.neoskript.runtime.TimeOfDay time = co.xenastudios.neoskript.runtime.TimeOfDay.parse(literal.text());
                if (time == null) {
                    throw unsupported("the time '" + literal.text() + "'");
                }
                constant(time, Object.class);
            }
            case "timespan" -> {
                Timespan timespan = Timespan.parse(literal.text());
                if (timespan == null) {
                    throw unsupported("the timespan '" + literal.text() + "'");
                }
                constant(timespan, Timespan.class);
            }
            default -> {
                // 'diamond (item type)': the type's own literal parser, given the words before the bracket.
                java.util.function.Function<String, ?> parser = library.literalParser(literal.type());
                if (parser == null) {
                    // Named types ('boots', 'creative') parse their words with their parser.
                    parser = library.parser(literal.type());
                }
                java.util.regex.Matcher specified = SPECIFIED_LITERAL.matcher(literal.text());
                Object value = parser == null ? null
                        : parser.apply(specified.matches() ? specified.group(1).strip() : literal.text().strip());
                if (value == null) {
                    if (parser == null && specified.matches()) {
                        // 'oak log (block)': a type that can't be written as a literal at all.
                        TypeRef ref = registry.resolveType(literal.type());
                        String name = ref == null ? literal.type() : ref.type().name();
                        throw new CompileException(line, ("aeiou".indexOf(Character.toLowerCase(name.charAt(0))) >= 0 ? "An "
                                : "A ") + name + " cannot be parsed.");
                    }
                    throw unsupported(literal.type() + " literals");
                }
                constant(value, Object.class);
            }
        }
    }

    /**
     * Text with embedded expressions: the source between {@code start} and {@code end} (a string's quotes
     * or a variable's braces), with each embedded {@code %expression%} converted to text and joined in.
     */
    private void text(int start, int end, List<Expr> embedded) throws CompileException {
        text(start, end, embedded, false);
    }

    /** {@link #text}, or with {@code name}, a variable's name: values there read as in variable names. */
    private void text(int start, int end, List<Expr> embedded, boolean name) throws CompileException {
        List<String> pieces = new ArrayList<>();
        int pos = start + 1;
        for (Expr part : embedded) {
            pieces.add(unescape(source.substring(pos, part.start() - 1)));
            pos = part.end() + 1;
        }
        pieces.add(unescape(source.substring(pos, end - 1)));
        if (!name && embedded.size() == 1 && embedded.getFirst().start() == start + 2
                && embedded.getFirst().end() == end - 2) {
            alreadyText(embedded.getFirst());
        }
        if (embedded.isEmpty()) {
            method.visitLdcInsn(pieces.getFirst());
            return;
        }
        String builder = "java/lang/StringBuilder";
        method.visitTypeInsn(NEW, builder);
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, builder, "<init>", "()V", false);
        for (int i = 0; i < pieces.size(); i++) {
            if (!pieces.get(i).isEmpty()) {
                method.visitLdcInsn(pieces.get(i));
                method.visitMethodInsn(INVOKEVIRTUAL, builder, "append", "(" + STRING_DESC + ")Ljava/lang/StringBuilder;",
                        false);
            }
            if (i < embedded.size()) {
                String outerTextPart = textPart;
                textPart = source.substring(embedded.get(i).start(), embedded.get(i).end());
                try {
                    expression(embedded.get(i));
                } finally {
                    textPart = outerTextPart;
                }
                method.visitMethodInsn(INVOKESTATIC, VALUES, name ? "nameText" : "text", "(" + OBJECT_DESC + ")"
                        + STRING_DESC, false);
                method.visitMethodInsn(INVOKEVIRTUAL, builder, "append", "(" + STRING_DESC + ")Ljava/lang/StringBuilder;",
                        false);
            }
        }
        method.visitMethodInsn(INVOKEVIRTUAL, builder, "toString", "()" + STRING_DESC, false);
    }

    /**
     * Skript's warning for a text that's only one expression which is already a text, as its
     * {@code VariableString} gives it: {@code "%arg-1%"} for a text argument. Only command arguments are checked,
     * whose text Skript writes in a known way ({@code the 1st argument}).
     */
    private void alreadyText(Expr part) {
        if (!(part instanceof Expr.Syntax syntax)) {
            return;
        }
        String text = alreadyTextOf(syntax);
        if (text != null) {
            warn(null, text + " is already a text, so you should not put it in one (e.g. " + text + " instead of \"%"
                    + text.replace("\"", "\"\"") + "%\")");
            return;
        }
        if (!isCore(syntax.syntax(), CoreSyntax.ARGUMENT) || syntax.syntax().match().hasTag("all")) {
            return;
        }
        if (commandArguments == null) {
            // In a command event, each argument is one text.
            if (inCommandEvent()) {
                String written = switch (syntax.syntax().pattern()) {
                    case 0 -> "the last argument";
                    case 1, 2 -> "the " + ordinalText(ordinal(syntax.syntax())) + " argument";
                    default -> "the argument";
                };
                warn(null, written + " is already a text, so you should not put it in one (e.g. " + written
                        + " instead of \"%" + written + "%\")");
            }
            return;
        }
        int index;
        try {
            index = argumentIndex(syntax);
        } catch (CompileException e) {
            return; // the error comes when it's compiled
        }
        if (index < 0 || index >= commandArguments.size()) {
            return;
        }
        CommandPattern.Argument argument = commandArguments.get(index);
        if (argument.plural() || !"string".equals(argument.type())) {
            return;
        }
        String written = switch (syntax.syntax().pattern()) {
            case 0 -> "the last argument";
            case 1, 2 -> "the " + ordinalText(ordinal(syntax.syntax())) + " argument";
            default -> "the argument";
        };
        warn(null, written + " is already a text, so you should not put it in one (e.g. " + written + " instead of \"%"
                + written.replace("\"", "\"\"") + "%\")");
    }

    /**
     * How Skript writes {@code syntax} if it's certainly one text, as Skript's warning needs it to be: a loop value
     * of a loop over texts, a loop index, or one text from an element whose text in messages is known.
     */
    private String alreadyTextOf(Expr.Syntax syntax) {
        if (isCore(syntax.syntax(), CoreSyntax.LOOP_VALUE)) {
            if (syntax.syntax().pattern() != 0) {
                return null;
            }
            String text = ((java.util.regex.MatchResult) syntax.syntax().match().regexValues().getFirst()).group();
            Matcher matcher = LOOP_VALUE.matcher(text.strip().toLowerCase(Locale.ROOT));
            if (!matcher.matches() || matcher.group(1).equals("counter") || matcher.group(1).equals("iteration")) {
                return null;
            }
            Block loop;
            try {
                loop = matchingLoop(matcher.group(1), matcher.group(2) == null ? -1 : Integer.parseInt(matcher.group(2)));
            } catch (CompileException e) {
                return null; // the error comes when it's compiled
            }
            Expr over = looped.get(loop);
            boolean texts = matcher.group(1).equals("index") ? loop.indicesSlot() >= 0
                    : over != null && "string".equals(over.type());
            return texts ? writtenText(syntax) : null;
        }
        if (!"string".equals(syntax.type()) || library.describer(syntax.syntax().syntax().id()) == null
                || !surelySingle(syntax)) {
            return null;
        }
        return skriptText(syntax);
    }

    /**
     * Whether {@code expr} is certainly one value, as Skript's {@code isSingle}: like {@link
     * co.xenastudios.neoskript.frontend.parse.Plurality#single}, but an element that's one value when its arguments
     * are only counts if they certainly are too.
     */
    private boolean surelySingle(Expr expr) {
        if (expr instanceof Expr.Syntax syntax && (isCore(syntax.syntax(), CoreSyntax.EVENT_VALUE)
                || isCore(syntax.syntax(), CoreSyntax.EVENT_VALUE_ENTITY))) {
            return !co.xenastudios.neoskript.frontend.parse.Plurality.plural(syntax, registry);
        }
        if (isArithmetic(expr)) {
            return ((Expr.Syntax) expr).syntax().match().values().stream()
                    .allMatch(operand -> operand instanceof Expr inner && surelySingle(inner));
        }
        if (!co.xenastudios.neoskript.frontend.parse.Plurality.single(expr, registry)) {
            return false;
        }
        if (expr instanceof Expr.Syntax syntax) {
            for (Object argument : syntax.syntax().match().values()) {
                if (argument instanceof Expr inner && !surelySingle(inner)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String unescape(String text) {
        return text.replace("\"\"", "\"").replace("%%", "%");
    }

    // ---------------------------------------------------------------- variables

    private void variableStore(Expr.Variable variable) {
        if (variable.local()) {
            method.visitVarInsn(ALOAD, LOCALS_SLOT);
        } else {
            constant(globals, VariableStore.class);
        }
    }

    /** Pushes the variable's name, without the {@code ::*} of a list. */
    private void variableName(Expr.Variable variable) throws CompileException {
        int end = variable.list() ? variable.end() - 3 : variable.end();
        text(variable.start(), end, variable.embedded(), true);
    }

    // ---------------------------------------------------------------- loop values

    /** {@code loop-value}, {@code loop-index}, {@code loop-counter} and {@code loop-<type>}, with {@code -N}. */
    private void loopValue(ParsedSyntax syntax) throws CompileException {
        String text = ((java.util.regex.MatchResult) syntax.match().regexValues().getFirst()).group();
        Matcher matcher = LOOP_VALUE.matcher(text.strip().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw unsupported("loop-" + text);
        }
        if (matcher.group(1).equals("counter") || matcher.group(1).equals("iteration")) {
            if (syntax.pattern() == 1 || syntax.pattern() == 2) {
                // Skript's loop values leave these to ExprLoopIteration, which has no next or previous one.
                throw new CompileException(line, "Can't understand this expression: '"
                        + (syntax.pattern() == 1 ? "next" : "previous") + " loop-" + text + "'");
            }
            loopIteration(matcher.group(2));
            return;
        }
        Block loop = matchingLoop(matcher.group(1), matcher.group(2) == null ? -1 : Integer.parseInt(matcher.group(2)));
        int offset = syntax.pattern() == 1 ? 1 : syntax.pattern() == 2 ? -1 : 0;
        if (offset == 1 && unpeekable.containsKey(loop)) {
            throw new CompileException(line, "The expression '" + unpeekable.get(loop)
                    + "' does not allow the usage of 'next loop-" + text + "'.");
        }
        switch (matcher.group(1)) {
            case "index" -> {
                if (loop.indicesSlot() >= 0) {
                    element(loop.indicesSlot(), loop.indexSlot(), offset);
                } else {
                    // Values that don't come from a list variable, like a function's, are indexed 1, 2, 3...
                    method.visitVarInsn(ILOAD, loop.indexSlot());
                    method.visitInsn(I2L);
                    method.visitLdcInsn((long) 1 + offset);
                    method.visitInsn(LADD);
                    method.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(J)Ljava/lang/String;", false);
                }
            }
            default -> element(loop.valuesSlot(), loop.indexSlot(), offset);
        }
    }

    /**
     * {@code loop-iteration}, {@code loop-counter-2}: how many times a loop (a while loop too) has run, as
     * Skript's ExprLoopIteration picks the loop and refuses one that isn't clear.
     */
    /** How surely a loop is the one a {@code loop-<name>} means. */
    private enum LoopMatch { NO, MAYBE, YES }

    /**
     * The loop {@code loop-<name>} (or {@code loop-<name>-<depth>}) is of, as Skript's {@code ExprLoopValue}
     * finds it: of the loops around it, outermost first, those whose values are of that type, or that say they
     * are the loop of that name ({@code loop-value}, {@code loop-index} of a list, {@code loop-argument}); the
     * {@code depth}th of them, else the only one.
     *
     * <p>Not every expression's type is known as exactly as Skript knows it, so a loop that may be of the type
     * isn't ruled out: then the innermost such loop is taken, as Skript would if the types say so.
     */
    private Block matchingLoop(String name, int depth) throws CompileException {
        List<Block> sure = new ArrayList<>();
        List<Block> candidates = new ArrayList<>();
        for (Block block : loops()) {
            if (block.kind() != BlockKind.LOOP) {
                continue;
            }
            LoopMatch match = loopMatch(block, name);
            if (match == LoopMatch.YES) {
                sure.add(block);
            }
            if (match != LoopMatch.NO) {
                candidates.add(block);
            }
        }
        if (depth >= 1 && depth <= candidates.size()) {
            return candidates.get(depth - 1);
        }
        if (depth < 0 && sure.size() > 1) {
            throw new CompileException(line, "There are multiple loops that match loop-" + name + ". Use loop-" + name
                    + "-1/2/3/etc. to specify which loop's value you want.");
        }
        if (depth >= 1 || candidates.isEmpty()) {
            throw new CompileException(line, "There's no loop that matches 'loop-" + name + "'");
        }
        return sure.size() == 1 ? sure.getFirst() : candidates.getLast();
    }

    private LoopMatch loopMatch(Block block, String name) {
        Expr expr = looped.get(block);
        if (name.equals("value") || isLoopOf(expr, name)) {
            return LoopMatch.YES;
        }
        if (name.equals("index")) {
            // Only loops with indices have a loop-index; values from a function are indexed 1, 2, 3...
            return block.indicesSlot() >= 0 || expr instanceof Expr.FunctionCall ? LoopMatch.YES : LoopMatch.NO;
        }
        if (anyType(expr)) {
            // Values Skript knows only as objects are no loop-<type>'s.
            return LoopMatch.NO;
        }
        co.xenastudios.neoskript.frontend.registry.TypeRef type = library.registry().resolveType(name);
        String valueType = expr == null ? null : expr.type();
        if (type == null || valueType == null || valueType.equals(SyntaxRegistry.OBJECT)) {
            // A type this doesn't know, or values whose type may only be known as Skript sets the loop up.
            return LoopMatch.MAYBE;
        }
        if (library.registry().isSubtype(valueType, type.type().id())) {
            return LoopMatch.YES;
        }
        return library.registry().isAssignable(valueType, type.type().id()) ? LoopMatch.MAYBE : LoopMatch.NO;
    }

    /**
     * Whether Skript certainly knows {@code expr}'s values only as objects: the values of a tag whose kind isn't
     * named ({@code tag values of tag "logs"}), which may be items or entity types, or a list with such values.
     */
    private boolean anyType(Expr expr) {
        return switch (expr) {
            case Expr.ListOf list -> list.items().stream().anyMatch(this::anyType);
            case Expr.Syntax syntax when syntax.syntax().syntax().id().equals("ExprTagContents") -> {
                Object tag = syntax.syntax().match().values().getFirst();
                yield tag instanceof Expr.Syntax inner && inner.syntax().syntax().id().equals("ExprTag")
                        && java.util.stream.Stream.of("item", "block", "entity").noneMatch(inner.syntax().match()::hasTag);
            }
            case null, default -> false;
        };
    }

    /** Skript's {@code isLoopOf}: whether {@code expr} says a loop over it is a {@code loop-<name>}. */
    private boolean isLoopOf(Expr expr, String name) {
        return switch (expr) {
            case Expr.Variable variable -> name.equals("var") || name.equals("variable");
            case Expr.ListOf list -> list.items().stream().anyMatch(item -> isLoopOf(item, name));
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.ARGUMENT)
                    || isCore(syntax.syntax(), CoreSyntax.TYPED_ARGUMENT) -> name.equals("argument");
            case null, default -> false;
        };
    }

    private void loopIteration(String number) throws CompileException {
        int loopNumber = number == null ? -1 : Integer.parseInt(number);
        Block loop = null;
        int i = 1;
        for (Block block : loops()) {
            if (block.kind() != BlockKind.LOOP && block.kind() != BlockKind.WHILE) {
                continue;
            }
            if (i < loopNumber) {
                i++;
                continue;
            }
            if (loop != null) {
                throw new CompileException(line, "There are multiple loops. Use loop-iteration-1/2/3/etc. to specify "
                        + "which loop-iteration you want.");
            }
            loop = block;
            if (i == loopNumber) {
                break;
            }
        }
        if (loop == null) {
            throw new CompileException(line, "The loop iteration expression must be used in a loop");
        }
        method.visitVarInsn(ILOAD, loop.indexSlot());
        method.visitInsn(I2L);
        method.visitInsn(LCONST_1);
        method.visitInsn(LADD);
        method.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
    }

    /** Pushes {@code array[index + offset]}, or {@code null} if that's outside the array. */
    private void element(int arraySlot, int indexSlot, int offset) {
        method.visitVarInsn(ALOAD, arraySlot);
        method.visitVarInsn(ILOAD, indexSlot);
        pushInt(offset);
        method.visitMethodInsn(INVOKESTATIC, VALUES, "element", "(" + OBJECTS + "II)" + OBJECT_DESC, false);
    }

    // ---------------------------------------------------------------- arithmetic

    /**
     * Arithmetic, with Skript's precedence whatever way the parser grouped the operators: the chain is
     * flattened (keeping parenthesized parts whole) and rebuilt with {@code ^} first (right to left), then
     * {@code *} and {@code /}, then {@code +} and {@code -} (left to right).
     */
    private void arithmetic(Expr.Syntax syntax) throws CompileException {
        List<Expr> operands = new ArrayList<>();
        List<CoreSyntax.Operator> operators = new ArrayList<>();
        flatten(syntax, operands, operators);
        emitArithmetic(operands, operators, 0, operands.size() - 1);
    }

    private boolean isArithmetic(Expr expr) {
        return expr instanceof Expr.Syntax syntax && (isCore(syntax.syntax(), CoreSyntax.ARITHMETIC)
                || isCore(syntax.syntax(), CoreSyntax.CUSTOM_ARITHMETIC));
    }

    private void flatten(Expr expr, List<Expr> operands, List<CoreSyntax.Operator> operators) {
        if (expr instanceof Expr.Syntax syntax && isArithmetic(syntax)) {
            ParsedSyntax parsed = syntax.syntax();
            for (int side = 0; side < 2; side++) {
                Expr operand = (Expr) parsed.match().values().get(side);
                int[] span = parsed.match().spans().get(side);
                // An operand narrower than its placeholder was in parentheses: keep it whole.
                boolean parenthesized = span[0] != operand.start();
                if (parenthesized) {
                    operands.add(operand);
                } else {
                    flatten(operand, operands, operators);
                }
                if (side == 0) {
                    operators.add((isCore(parsed, CoreSyntax.ARITHMETIC) ? CoreSyntax.OPERATORS : CoreSyntax.CUSTOM_OPERATORS)
                            .get(parsed.pattern()));
                }
            }
            return;
        }
        operands.add(expr);
    }

    /** Emits operands {@code [from, to]} with the operators between them, by precedence. */
    private void emitArithmetic(List<Expr> operands, List<CoreSyntax.Operator> operators, int from, int to)
            throws CompileException {
        if (from == to) {
            expression(operands.get(from));
            return;
        }
        // Split at the operator that binds loosest: its last one, or its first if it groups from the right.
        int loosest = Integer.MAX_VALUE;
        for (int i = from; i < to; i++) {
            loosest = Math.min(loosest, operators.get(i).priority());
        }
        int split = -1;
        for (int i = from; i < to; i++) {
            CoreSyntax.Operator operator = operators.get(i);
            if (operator.priority() == loosest && (split < 0 || !operator.fromRight())) {
                split = i;
            }
        }
        emitArithmetic(operands, operators, from, split);
        emitArithmetic(operands, operators, split + 1, to);
        String operation = switch (operators.get(split).sign()) {
            case "+" -> "add";
            case "-" -> "subtract";
            case "*" -> "multiply";
            case "/" -> "divide";
            case "^" -> "power";
            default -> null;
        };
        if (operation == null) {
            method.visitLdcInsn(operators.get(split).sign());
            method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Arithmetic.class), "custom",
                    "(" + OBJECT_DESC + OBJECT_DESC + STRING_DESC + ")" + OBJECT_DESC, false);
            return;
        }
        method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Arithmetic.class), operation,
                "(" + OBJECT_DESC + OBJECT_DESC + ")" + OBJECT_DESC, false);
    }

    /** {@code X parsed as "pattern"} that are set into a list variable: their values stay grouped by placeholder. */
    private final java.util.Set<Expr> groupedParses = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /**
     * {@code X parsed as "%integer%, %text%"}: the text matched against the pattern, its placeholders parsed
     * as their types (a plural one as a list). Its values come one after another, or grouped by placeholder
     * when set into a list variable, as Skript's ExprParse does.
     */
    private void parsePattern(Expr.Syntax syntax) throws CompileException {
        Object regex = syntax.syntax().match().regexValues().getFirst();
        String text = regex instanceof java.util.regex.MatchResult match ? match.group() : String.valueOf(regex);
        co.xenastudios.neoskript.frontend.pattern.CompiledPattern pattern;
        try {
            pattern = co.xenastudios.neoskript.frontend.pattern.PatternCompiler.compile(text);
        } catch (RuntimeException e) {
            throw new CompileException(line, "Malformed pattern: " + e.getMessage());
        }
        PlaceholderSpec[] specs = placeholderSpecs(pattern);
        // As Skript's validatePattern, every type must exist before any is checked for being parseable.
        for (PlaceholderSpec spec : specs) {
            for (String type : spec.types()) {
                if (registry.resolveType(type) == null) {
                    String singular = type.endsWith("s") && !type.endsWith("ss") ? type.substring(0, type.length() - 1) : type;
                    throw new CompileException(line, "Invalid pattern: The type '" + singular + "' could not be found."
                            + " Please check your spelling or escape the percent signs if you want to match literal %s:"
                            + " \"\\%not an expression\\%\"");
                }
            }
        }
        for (PlaceholderSpec spec : specs) {
            for (String type : spec.types()) {
                TypeRef ref = registry.resolveType(type);
                if (ref == null || library.parser(ref.type().id()) == null) {
                    String name = ref == null ? type : ref.type().name();
                    throw new CompileException(line, "Text cannot be parsed as " + article(name) + " " + name);
                }
            }
        }
        boolean grouped = groupedParses.contains(syntax);
        SyntaxRegistry types = registry;
        Library parsers = library;
        java.util.function.Function<String, Object> parse = input -> {
            co.xenastudios.neoskript.frontend.pattern.MatchResult match = pattern.match(input, (in, start, end, spec) ->
                    parsePlaceholder(in.substring(start, end), spec, types, parsers));
            if (match == null) {
                return null;
            }
            List<Object> values = new ArrayList<>();
            for (Object value : match.values()) {
                if (value == null) {
                    continue;
                }
                if (!grouped && value instanceof Object[] many) {
                    values.addAll(java.util.Arrays.asList(many));
                } else {
                    values.add(value);
                }
            }
            return values.toArray();
        };
        constant(parse, java.util.function.Function.class);
        expression((Expr) syntax.syntax().match().values().getFirst());
        method.visitLdcInsn("\"" + text + "\"");
        method.visitMethodInsn(INVOKESTATIC, VALUES, "parse",
                "(Ljava/util/function/Function;" + OBJECT_DESC + STRING_DESC + ")" + OBJECT_DESC, false);
    }

    /** A placeholder's text parsed as one of its types, a plural one as a list ({@code 1, 2 and 3}). */
    private static Object parsePlaceholder(String text, PlaceholderSpec spec, SyntaxRegistry types, Library parsers) {
        for (String type : spec.types()) {
            TypeRef ref = types.resolveType(type);
            java.util.function.Function<String, ?> parser = ref == null ? null : parsers.parser(ref.type().id());
            if (parser == null) {
                continue;
            }
            if (!ref.plural()) {
                Object value = parser.apply(text.strip());
                if (value != null) {
                    return value;
                }
                continue;
            }
            String[] pieces = text.split("\\s*,\\s*(?:(?:and|or|n?or)\\s+)?|\\s+(?:and|n?or)\\s+");
            Object[] values = new Object[pieces.length];
            boolean all = true;
            for (int i = 0; i < pieces.length && all; i++) {
                values[i] = parser.apply(pieces[i].strip());
                all = values[i] != null;
            }
            if (all) {
                return values;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- functions

    /** Calls a function, leaving what it returns (or {@code null}) on the stack. */
    private void call(Expr.FunctionCall call) throws CompileException {
        call(call, false);
    }

    /** Calls a function; {@code keyed} keeps the indices of a list it returns ({@link KeyedValues}). */
    private void call(Expr.FunctionCall call, boolean keyed) throws CompileException {
        // As in Skript, which parses function arguments with their warnings thrown away.
        inArguments++;
        try {
            callArguments(call, keyed);
        } finally {
            inArguments--;
        }
    }

    /** How many function calls' arguments are being compiled, whose elements' warnings are dropped. */
    private int inArguments;

    private void callArguments(Expr.FunctionCall call, boolean keyed) throws CompileException {
        FunctionInfo info = call.function();
        Function builtIn = library.builtIn(info);
        if (builtIn != null) {
            constant(builtIn, Function.class);
        } else {
            FunctionHandle handle = functions.get(info);
            if (handle == null) {
                throw unsupported("the function " + info.name());
            }
            constant(handle, FunctionHandle.class);
            // The caller's context, so a function that waits carries on where its caller runs.
            method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        }
        List<FunctionInfo.Parameter> parameters = info.parameters();
        pushInt(parameters.size());
        method.visitTypeInsn(ANEWARRAY, OBJECT);
        for (int i = 0; i < parameters.size(); i++) {
            Expr argument = i < call.arguments().size() ? call.arguments().get(i) : null;
            if (argument == null) {
                java.util.function.Supplier<?> fallback = builtIn == null ? null : library.functionDefault(info, i);
                if (fallback != null) {
                    // Left out, so the built-in's own default, worked out when called.
                    method.visitInsn(DUP);
                    pushInt(i);
                    constant(fallback, java.util.function.Supplier.class);
                    method.visitMethodInsn(INVOKEINTERFACE, "java/util/function/Supplier", "get", "()" + OBJECT_DESC, true);
                    method.visitInsn(AASTORE);
                }
                continue;
            }
            method.visitInsn(DUP);
            pushInt(i);
            // Words are first tried as the parameter's type: 'chicken' is an entity type for 'data: entity type'.
            List<String> outerLiteralTypes = literalTypes;
            Expr outerArgumentWords = argumentWords;
            literalTypes = List.of(parameters.get(i).type());
            argumentWords = argument instanceof Expr.Unresolved ? argument : null;
            callArguments++;
            try {
                if (parameters.get(i).plural() && keyedList(argument) != null) {
                    // A list passed keyed takes everything under it along, as Skript copies it into the parameter.
                    Expr.Variable list = keyedList(argument);
                    variableStore(list);
                    variableName(list);
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "recursiveKeyedList",
                            "(" + STRING_DESC + ")" + Type.getDescriptor(KeyedValues.class), false);
                } else if (parameters.get(i).plural() && isKeyed(argument)) {
                    keyed(argument);
                } else {
                    expression(argument);
                    convert(parameters.get(i).plural());
                }
            } finally {
                literalTypes = outerLiteralTypes;
                argumentWords = outerArgumentWords;
                callArguments--;
            }
            method.visitInsn(AASTORE);
        }
        if (builtIn != null) {
            method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Function.class), "call",
                    "(" + OBJECTS + ")" + OBJECT_DESC, true);
            if (!keyed) {
                method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(KeyedValues.class), "plain",
                        "(" + OBJECT_DESC + ")" + OBJECT_DESC, false);
            }
        } else {
            method.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(FunctionHandle.class), keyed ? "callKeyed" : "call",
                    "(" + CONTEXT + OBJECTS + ")" + OBJECT_DESC, false);
        }
    }

    // ---------------------------------------------------------------- keyed values

    /** Whether {@code expr} gives values with their indices: {@code keyed X}, or a list with such a part. */
    private boolean isKeyed(Expr expr) {
        return isKeyed(expr, false);
    }

    /**
     * Whether {@code expr} gives values with their indices: {@code keyed X}, a list with such a part, or a
     * key-aware expression of one. With {@code implicit}, as inside {@code keyed} or a loop, list variables and
     * the lists functions return count too.
     */
    private boolean isKeyed(Expr expr, boolean implicit) {
        return switch (expr) {
            case Expr.Syntax syntax when syntax.syntax().syntax().id().equals(KEYED) -> true;
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.RECURSIVE) -> implicit || recursiveOfKeyed(syntax.syntax());
            case Expr.Syntax syntax when keyAware(syntax) && keysRecommended(syntax) -> true;
            case Expr.Syntax syntax when keyAware(syntax) -> syntax.syntax().match().values().stream()
                    .anyMatch(value -> value instanceof Expr argument && isKeyed(argument, implicit));
            case Expr.ListOf list -> list.items().stream().anyMatch(item -> isKeyed(item, implicit));
            case Expr.Variable variable -> implicit && variable.list();
            case Expr.FunctionCall call -> implicit && library.builtIn(call.function()) == null
                    && call.function().returnsPlural();
            default -> false;
        };
    }

    /** The list variable in {@code keyed {_x::*}}, or {@code null} if {@code expr} isn't that. */
    private static Expr.Variable keyedList(Expr expr) {
        return expr instanceof Expr.Syntax syntax && syntax.syntax().syntax().id().equals(KEYED)
                && syntax.syntax().match().values().getFirst() instanceof Expr.Variable list && list.list() ? list : null;
    }

    /** Whether the key-aware element wants list variables' indices even when they aren't written keyed. */
    private boolean implicitKeys(Expr.Syntax syntax) {
        return library.binding(syntax.syntax().syntax()) instanceof ElementFactory<?> factory
                && factory.create(info(syntax.syntax())) instanceof KeyAware aware && aware.implicitKeys();
    }

    /** Whether the key-aware element's indices are kept even where it isn't written keyed. */
    private boolean keysRecommended(Expr.Syntax syntax) {
        return library.binding(syntax.syntax().syntax()) instanceof ElementFactory<?> factory
                && factory.create(info(syntax.syntax())) instanceof KeyAware aware && aware.keysRecommended();
    }

    /** Whether the expression's element keeps its values' indices ({@link KeyAware}). */
    private boolean keyAware(Expr.Syntax syntax) {
        return !(library.binding(syntax.syntax().syntax()) instanceof Library.Core)
                && library.binding(syntax.syntax().syntax()) instanceof ElementFactory<?> factory
                && factory.create(info(syntax.syntax())) instanceof KeyAware;
    }

    private void keyed(Expr expr) throws CompileException {
        keyed(expr, false);
    }

    /**
     * Pushes {@code expr}'s values with their indices where it has them ({@link KeyedValues}): a list
     * variable's, a function's returned list's, a key-aware expression's, or each keyed part of a list's;
     * else its plain values. {@code implicit} is as for {@link #isKeyed(Expr, boolean)}.
     */
    private void keyed(Expr expr, boolean implicit) throws CompileException {
        switch (expr) {
            case Expr.Syntax syntax when syntax.syntax().syntax().id().equals(KEYED) ->
                keyed((Expr) syntax.syntax().match().values().getFirst(), true);
            case Expr.Syntax syntax when recursiveIndices(syntax.syntax()) != null ->
                    keyed(recursiveIndices(syntax.syntax()), implicit);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.RECURSIVE) -> {
                // Every value under the list, each with its path: {_x::a::b} is at 'a::b'.
                Expr.Variable list = listVariable(syntax.syntax(), "Only list variables can be recursive");
                variableStore(list);
                variableName(list);
                method.visitMethodInsn(INVOKEVIRTUAL, STORE, "recursiveKeyedList",
                        "(" + STRING_DESC + ")" + Type.getDescriptor(KeyedValues.class), false);
            }
            case Expr.Syntax syntax when keyAware(syntax) -> {
                callThrough(syntax.syntax(), Expression.class, true, implicit || implicitKeys(syntax));
                method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Expression.class), "get",
                        "(" + CONTEXT + OBJECTS + ")" + OBJECT_DESC, true);
            }
            case Expr.Variable variable when variable.list() -> {
                variableStore(variable);
                variableName(variable);
                method.visitMethodInsn(INVOKEVIRTUAL, STORE, "keyedList",
                        "(" + STRING_DESC + ")" + Type.getDescriptor(KeyedValues.class), false);
            }
            case Expr.FunctionCall call -> call(call, true);
            case Expr.ListOf list -> {
                pushInt(list.items().size());
                method.visitTypeInsn(ANEWARRAY, OBJECT);
                for (int i = 0; i < list.items().size(); i++) {
                    method.visitInsn(DUP);
                    pushInt(i);
                    Expr item = list.items().get(i);
                    if (isKeyed(item, implicit)) {
                        keyed(item, implicit);
                    } else {
                        expression(item);
                    }
                    method.visitInsn(AASTORE);
                }
                method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(KeyedValues.class), "merge",
                        "(" + OBJECTS + ")" + Type.getDescriptor(KeyedValues.class), false);
            }
            default -> expression(expr);
        }
    }

    private void pushInt(int value) {
        method.visitLdcInsn(value);
    }

    private CompileException unsupported(String what) {
        return new CompileException(line, "NeoSkript can't run this yet: " + what);
    }
}
