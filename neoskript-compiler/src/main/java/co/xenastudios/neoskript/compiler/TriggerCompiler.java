/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
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
     */
    /** Loops over values that can't be peeked at with {@code next loop-value}, with how their expression reads. */
    private final Map<Block, String> unpeekable = new java.util.IdentityHashMap<>();

    private record Block(BlockKind kind, Label end, Label next, int valuesSlot, int indicesSlot, int indexSlot,
            String valueType) {

        Block(BlockKind kind, Label end, Label next, int valuesSlot, int indicesSlot, int indexSlot) {
            this(kind, end, next, valuesSlot, indicesSlot, indexSlot, null);
        }
    }

    /** A {@code wait}: where the trigger carries on, and the loop state it saves. */
    private record WaitPoint(Label resume, int line, List<int[]> savedSlots) {}

    /** Kinds of saved local variable slots, as {@code {slot, kind}}. */
    private static final int SAVED_VALUES = 0;
    private static final int SAVED_INDICES = 1;
    private static final int SAVED_INDEX = 2;

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

    /** The warnings given so far, as line and message, each given once though elements may be set up again. */
    private final java.util.Set<String> warned = new java.util.HashSet<>();
    /** The header of the structure being compiled, for warnings given before its code. */
    private co.xenastudios.neoskript.frontend.structure.Line headerLine;

    /**
     * A warning at the current line, unless the script suppresses warnings tagged {@code tag} there (a
     * {@code null} tag can't be suppressed).
     */
    private void warn(String tag, String message) {
        if (inArguments > 0 || tag != null && suppresses(tag)) {
            return;
        }
        if (warned.add(line + ":" + message)) {
            warnings.accept(line, message);
        }
    }

    /** Whether the script suppresses warnings tagged {@code tag} at the line being compiled. */
    private boolean suppresses(String tag) {
        return script.suppresses(currentLine != null ? currentLine : headerLine, tag);
    }

    /** How many lists the expression being compiled is in. */
    private int inList;

    /** The types of the placeholder whose value is being compiled, which words are first tried as. */
    private List<String> literalTypes = List.of();
    /** The event's name, for errors: {@code join} in {@code on join:}. */
    private String eventName;
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
        this.eventName = eventName;
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
        eventName = "periodical";
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
        eventName = key.regionMatches(true, 0, "on ", 0, 3) ? key.substring(3).strip() : key.strip();
        eventLabel = "on " + eventName;
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
        eventName = "command";
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
        eventName = "command suggestions";
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
        eventName = "command";
        eventLabel = "command";
        return (Trigger) define(generate(scriptName, () -> {
            line = entryLine;
            source = text;
            method.visitVarInsn(ALOAD, CONTEXT_SLOT);
            expression(message);
            method.visitMethodInsn(INVOKESTATIC, COMMAND_EVENT, "result", "(" + CONTEXT + OBJECT_DESC + ")V", false);
        }));
    }

    /** A Skript test ({@code test "name" [when <condition>]:}): skipped if its condition is false. */
    Trigger compileTest(String scriptName, ParsedStructure.Custom test) throws CompileException {
        eventClasses = List.of(co.xenastudios.neoskript.runtime.testing.TestEvent.class);
        eventName = "test";
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
    private void site(ParsedSyntax syntax, Class<?> element) {
        String code = currentLine != null ? currentLine.text() : source;
        sites.add(new co.xenastudios.neoskript.runtime.errors.ErrorSites.Site(kindName(syntax.syntax().kind()),
                syntax.syntax().id(), element, constantsScript, line, code));
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
            } else if (block.kind() == BlockKind.WHILE) {
                saved.add(new int[] {block.indexSlot(), SAVED_INDEX});
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
        return wait;
    }

    /**
     * {@code wait %timespan%}: saves the frame (the state number, local variables and the loops around
     * it), schedules the rest of the trigger, and returns. The rest starts at the label after it.
     */
    private void waitFor(ParsedSyntax delay) throws CompileException {
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
                    failed(new CompileException(line, error.getMessage()), open);
                } catch (CompileException e) {
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
            apart.eventName = eventName;
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
        for (Statement statement : statements) {
            // Positions in what was parsed are in the statement's own line.
            source = statement instanceof Statement.Section section ? section.line().key() : statement.line().text();
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

    private void lineOf(Statement statement, String text) {
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
                if (i >= statements.size() || !(statements.get(i) instanceof Statement.Section body)
                        || !body.line().key().strip().toLowerCase(Locale.ROOT).startsWith("then")) {
                    throw new CompileException(statement.line().number(), (any ? "'if any'" : "'if all'")
                            + " has to be placed just before a 'then' section");
                }
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
            apart.eventName = eventName;
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
        Expr.Variable key = section.header().pattern() == 0 ? null : forVariable(values.get(0));
        Expr.Variable value = forVariable(values.get(section.header().pattern() == 0 ? 0 : 1));
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
        Block block = new Block(BlockKind.LOOP, end, next, valuesSlot, indicesSlot, indexSlot);
        if (looped instanceof Expr.Syntax syntax && syntax.syntax().syntax().id().equals("ExprTestLoopPeeking")
                && syntax.syntax().pattern() == 0) {
            // Skript's test expression whose values can't be peeked at.
            unpeekable.put(block, "test loop peeking");
        }
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
        callThrough(header, co.xenastudios.neoskript.api.element.Section.class);
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
        statements(section.body());
        blocks.pop();
        method.visitLabel(next);
        method.visitIincInsn(indexSlot, 1);
        method.visitJumpInsn(GOTO, top);
        method.visitLabel(end);
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
        statements(section.body());
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
        return null;
    }

    /**
     * Skript's warning for words with several types read where nothing says which: {@code set {_x} to unknown}
     * ('unknown' is a heal reason, a damage cause and more).
     */
    private void multipleTypesWarning(String words) {
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
                condition(condition);
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
        method.visitJumpInsn(GOTO, matching.get(count - 1).end());
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
            changeAll(effect, mode);
        } finally {
            changer = outerChanger;
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
            if (target instanceof Expr expr && Values.hasChanger(changeMode(effect, mode))) {
                changeValues(effect, mode, expr);
                return;
            }
            throw unsupported("changing anything but variables");
        }
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
            case ADD, INCREASE -> {
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
        setTo(target, () -> {
            site(effect, update.getClass());
            constant(update, Update.class);
            method.visitVarInsn(ALOAD, CONTEXT_SLOT);
            arguments(effect);
            method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Update.class), "update",
                    "(" + CONTEXT + OBJECTS + ")" + OBJECT_DESC, true);
        });
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
        if (accepted == null) {
            throw new CompileException(line, source.substring(target.start(), target.end()) + " can't be set to anything");
        }
        site(parsed, element.getClass());
        constant(element, Changeable.class);
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
        if (!(whether.syntax().match().regexValues().getFirst() instanceof ParsedSyntax condition)
                || !(library.binding(condition.syntax()) instanceof ElementFactory<?> factory)) {
            throw new CompileException(line, "'" + source.substring(whether.start(), whether.end()) + "' can't be set");
        }
        Object element = factory.create(info(condition));
        if (!(element instanceof SettableCondition)) {
            throw new CompileException(line, "'" + source.substring(whether.start(), whether.end()) + "' can't be set");
        }
        site(condition, element.getClass());
        constant(element, SettableCondition.class);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        arguments(condition);
        expression((Expr) effect.match().values().get(1));
        method.visitMethodInsn(INVOKESTATIC, VALUES, "isTrue", "(" + OBJECT_DESC + ")Z", false);
        method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(SettableCondition.class), "set",
                "(" + CONTEXT + OBJECTS + "Z)V", true);
    }

    private static Changeable.Mode changeMode(ParsedSyntax effect, CoreSyntax.ChangeMode mode) {
        return switch (mode) {
            case SET -> Changeable.Mode.SET;
            case ADD, INCREASE -> Changeable.Mode.ADD;
            case REMOVE, REDUCE -> Changeable.Mode.REMOVE;
            case REMOVE_ALL -> Changeable.Mode.REMOVE_ALL;
            case DELETE -> effect.match().hasTag("reset") ? Changeable.Mode.RESET : Changeable.Mode.DELETE;
        };
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
        method.visitMethodInsn(INVOKESTATIC, VALUES, "changeValues",
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
                    + (mode == CoreSyntax.ChangeMode.SET ? "set to " : mode == CoreSyntax.ChangeMode.ADD ? "added to " : "changed by ")
                    + source.substring(call.start(), call.end()) + " because the function doesn't return anything");
        }
        Object element = library.binding(syntax.syntax()) instanceof ElementFactory<?> factory
                ? factory.create(info(syntax))
                : null;
        Class<?>[] accepted = element instanceof Changeable changeable ? changeable.acceptChange(changeMode) : null;
        if (accepted == null && !(element instanceof Changeable) && Values.hasChanger(changeMode)) {
            // An expression that can't be changed itself, but whose values can be: 'give X to all players'.
            changeValues(effect, mode, target);
            return;
        }
        if (accepted == null) {
            String text = source.substring(shown.start(), shown.end());
            throw new CompileException(line, text + switch (changeMode) {
                case SET -> " can't be set to anything";
                case ADD -> " can't have anything added to it";
                case REMOVE, REMOVE_ALL -> " can't have anything removed from it";
                case DELETE -> " can't be deleted";
                case RESET -> " can't be reset";
            });
        }
        if (mode.value >= 0) {
            checkChanger((Expr) effect.match().values().get(mode.value), accepted, changeMode, target);
        }
        site(syntax, element.getClass());
        constant(element, Changeable.class);
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
    private void checkChanger(Expr changer, Class<?>[] accepted, Changeable.Mode mode, Expr target)
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
        if (changerFits(changer, acceptedTypes)) {
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
        String what = describe(target);
        String given = describe(changer);
        throw new CompileException(line, switch (mode) {
            case ADD -> given + " can't be added to " + what + " because the former " + not;
            case SET -> what + " can't be set to " + given + " because the latter " + not;
            default -> given + " can't be removed from " + what + " because the former " + not;
        });
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
        if (!(changer instanceof Expr.Text || changer instanceof Expr.Literal || changer instanceof Expr.Syntax)) {
            return true;
        }
        String type = changer.type();
        if (type == null || type.equals(SyntaxRegistry.OBJECT)) {
            return true;
        }
        for (String wanted : types) {
            // Its type, one it converts to, or one under it, which it might be when it runs.
            if (registry.isAssignable(type, wanted) || registry.isSubtype(wanted, type)) {
                return true;
            }
        }
        return false;
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
                boolean typed = values.stream().anyMatch(v -> v instanceof Expr expr && !(expr instanceof Expr.Unresolved)
                        && expr.type() != null && !expr.type().equals(SyntaxRegistry.OBJECT));
                if (typed || values.size() > 2) {
                    // Words nothing reads compared with something of a type: Skript's comparison says so.
                    StringBuilder message = new StringBuilder("Can't compare ").append(compared(values.get(0)))
                            .append(" with ").append(compared(values.get(1)));
                    if (values.size() > 2) {
                        message.append(" and ").append(compared(values.get(2)));
                    }
                    throw new CompileException(line, message.toString());
                }
                // Otherwise Skript's comparison quietly fails to initialise, so the line isn't understood.
                String text = source.strip();
                java.util.regex.Matcher header = java.util.regex.Pattern.compile("(?i)^(?:else )?if (.+?):?$").matcher(text);
                throw new CompileException(line, header.matches()
                        ? "Can't understand this condition: '" + header.group(1) + "'"
                        : "Can't understand this condition/effect: " + text);
            }
            return;
        }
        conditionChecked(condition, values);
    }

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
        String text = source == null ? unresolved.text().strip() : source.strip();
        if (changer != null && unresolved.start() >= changer.start() && unresolved.end() <= changer.end()) {
            String value = outerArgument != null ? outerArgument : unresolved.text().strip();
            if (!(changer instanceof Expr.ListOf)) {
                return "Can't understand this expression: '" + value + "'";
            }
        }
        if (conditionLines) {
            return "Can't understand this condition: '" + text + "'";
        }
        java.util.regex.Matcher header = IF_LINE.matcher(text);
        if (header.matches()) {
            return "Can't understand this condition: '" + header.group(1) + "'";
        }
        if (text.regionMatches(true, 0, "loop ", 0, 5)) {
            return "Can't understand this loop: '" + text.substring(5).replaceFirst(":$", "") + "'";
        }
        return "Can't understand this condition/effect: " + text;
    }

    /** A side of a comparison as Skript's errors name it: its type with an article, or as written if it has none. */
    private String compared(Object value) {
        if (value instanceof Expr.Unresolved unresolved) {
            return "'" + unresolved.text().strip() + "'";
        }
        if (value instanceof Expr expr && expr.type() != null && !expr.type().equals(SyntaxRegistry.OBJECT)) {
            TypeRef ref = registry.resolveType(expr.type());
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
    }

    /** How the element was matched, for its factory: its pattern, marks, tags and which arguments are lists. */
    private ElementInfo info(ParsedSyntax syntax) {
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
                    || isCore(inner.syntax(), CoreSyntax.EVENT_VALUE_ENTITY)))) {
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
                argumentTypes, single, literals, eventName, eventClasses == null ? List.of() : eventClasses,
                sectionType, defaults, syntax == timedSyntax ? timedTime : 0);
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
        site(syntax, element.getClass());
        constant(element, type);
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
        if (syntax.syntax().id().equals("CondCompare") && values.size() >= 2 && values.get(i) instanceof Expr.Unresolved
                && values.get(1 - Math.min(i, 1)) instanceof Expr other && !(other instanceof Expr.Unresolved)
                && other.type() != null
                && !other.type().equals(SyntaxRegistry.OBJECT)) {
            return List.of(other.type());
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

    private void arguments(ParsedSyntax syntax) throws CompileException {
        arguments(syntax, false, false);
    }

    private void arguments(ParsedSyntax syntax, boolean keyed, boolean implicit) throws CompileException {
        CompiledPattern pattern = syntax.syntax().patterns().get(syntax.pattern());
        List<Object> values = syntax.match().values();
        PlaceholderSpec[] specs = placeholderSpecs(pattern);
        pushInt(values.size());
        method.visitTypeInsn(ANEWARRAY, OBJECT);
        for (int i = 0; i < values.size(); i++) {
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
                java.util.function.Function<Object, Object> fallback = eventDefault(specs[i]);
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
     * if the type defaults to it and the event has one, else {@code null}.
     */
    private java.util.function.Function<Object, Object> eventDefault(PlaceholderSpec spec) {
        if (eventClasses == null || spec.nullable()) {
            return null;
        }
        for (String name : spec.types()) {
            TypeRef ref = registry.resolveType(name);
            if (ref != null && library.hasEventDefault(ref.type().id())) {
                java.util.function.Function<Object, Object> getter = library.eventValue(eventClasses, ref.type().id());
                if (getter != null) {
                    return getter;
                }
            }
        }
        return null;
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
                        request(value, ref).type());
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
        eventValue(lookup(syntax, ref, 0));
    }

    /** How to get the event value {@code syntax} names at {@code time}, as Skript resolves it, or its error. */
    private java.util.function.Function<Object, Object> lookup(Expr.Syntax syntax, TypeRef ref, int time)
            throws CompileException {
        Library.EventValueRequest request = request(syntax, ref);
        Library.EventValueLookup lookup = library.eventValue(eventClasses == null ? List.of() : eventClasses,
                request.valueClass(), request.type(), time, eventDescription());
        if (lookup.getter() == null) {
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

    private List<CommandPattern.Argument> commandArguments(Expr.Syntax syntax) throws CompileException {
        if (commandArguments == null) {
            throw new CompileException(line, "'" + source.substring(syntax.start(), syntax.end())
                    + "' can only be used in a command");
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
        commandArgument(argumentIndex(expr));
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

    private static String article(String word) {
        return !word.isEmpty() && "aeiou".indexOf(Character.toLowerCase(word.charAt(0))) >= 0 ? "an" : "a";
    }

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
                Object value = null;
                for (String name : literalTypes) {
                    TypeRef ref = registry.resolveType(name);
                    java.util.function.Function<String, ?> parser = ref == null ? null : library.literalParser(ref.type().id());
                    if (parser == null && ref != null && library.namedValues(ref.type().id()) != null) {
                        // A named type's words: 'normal' is the environment where an environment is compared.
                        parser = library.parser(ref.type().id());
                    }
                    value = parser == null ? null : parser.apply(unresolved.text().strip());
                    if (value != null) {
                        break;
                    }
                }
                if (value == null) {
                    value = library.literal(unresolved.text());
                    if (value != null) {
                        multipleTypesWarning(unresolved.text().strip());
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
        List<Block> loops = loops().stream().filter(b -> b.kind() == BlockKind.LOOP).toList();
        if (loops.isEmpty()) {
            throw new CompileException(line, "There's no loop for 'loop-" + text + "'");
        }
        Block loop;
        if (matcher.group(2) != null) {
            int n = Integer.parseInt(matcher.group(2));
            if (n < 1 || n > loops.size()) {
                throw new CompileException(line, "There's no loop number " + n + " for 'loop-" + text + "'");
            }
            loop = loops.get(n - 1);
        } else {
            loop = loops.getLast();
        }
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
            literalTypes = List.of(parameters.get(i).type());
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
