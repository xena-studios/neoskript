/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
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
    private static final String COMMAND_EVENT = Type.getInternalName(CommandEvent.class);
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
        SECTION
    }

    /**
     * A section being compiled, for exit, continue and loop values.
     *
     * @param valueType for a library section, the type of the values its code runs for
     */
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
    /**
     * An inline function's loop over a list ({@code where [...]}): the slots of the current value, its
     * position, the list's indices (or -1), and the reduced value so far (or -1).
     */
    private record InputFrame(int valueSlot, int indexSlot, int indicesSlot, int reducedSlot) {}

    private final Deque<InputFrame> inputs = new ArrayDeque<>();

    /** The types of the placeholder whose value is being compiled, which words are first tried as. */
    private List<String> literalTypes = List.of();
    /** The event's name, for errors: {@code join} in {@code on join:}. */
    private String eventName;
    /** A command trigger's arguments, by number, or {@code null} outside commands. */
    private List<CommandPattern.Argument> commandArguments;
    /** How many choices a command trigger's pattern has. */
    private int commandChoices;

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
     * A trigger for a server event. If the event has a filter ({@code on break of stone}), the trigger
     * stops straight away when it fails.
     */
    Trigger compileTrigger(String scriptName, ParsedStructure.Event event, Library.ServerEvent binding)
            throws CompileException {
        eventClasses = binding.classes();
        String key = event.structure().node().line().key();
        eventName = key.regionMatches(true, 0, "on ", 0, 3) ? key.substring(3).strip() : key.strip();
        return (Trigger) define(generate(scriptName, () -> {
            if (binding.filter() != null) {
                line = event.structure().node().line().number();
                source = key;
                ParsedSyntax syntax = event.event();
                Condition filter = binding.filter().create(
                        new ElementInfo(syntax.pattern(), syntax.match().mark(), syntax.match().tags()));
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
     * A command's message, such as its {@code cooldown message}: a trigger that stores the text in the
     * {@link CommandEvent}'s result.
     */
    Trigger compileCommandMessage(String scriptName, int entryLine, String text, Expr message) throws CompileException {
        eventClasses = List.of(CommandEvent.class);
        eventName = "command";
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
                : new String[] {Type.getInternalName(CompiledFunction.class)};
        writer.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, className, null, OBJECT, interfaces);
        writer.visitSource(scriptName, null);

        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, OBJECT, "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        String bodyDescriptor = "(" + CONTEXT + FRAME_DESC + ")V";
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
            method = writer.visitMethod(ACC_PUBLIC, "call", "(" + CONTEXT + OBJECTS + ")" + OBJECT_DESC, null, null);
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
        }
        method.visitTypeInsn(NEW, STORE);
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, STORE, "<init>", "()V", false);
        method.visitVarInsn(ASTORE, LOCALS_SLOT);
        method.visitInsn(ICONST_0);
        method.visitTypeInsn(ANEWARRAY, OBJECT);
        method.visitVarInsn(ASTORE, PARSE_LOGS_SLOT);
        method.visitLabel(start);
        body.emit();
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
        if (trigger) {
            method.visitLabel(dispatch);
            dispatch();
        }
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
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
            method.visitInsn(RETURN);
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
        method.visitInsn(RETURN);
    }

    /**
     * {@code wait %timespan%}: saves the frame (the state number, local variables and the loops around
     * it), schedules the rest of the trigger, and returns. The rest starts at the label after it.
     */
    private void waitFor(ParsedSyntax delay) throws CompileException {
        if (function != null) {
            throw unsupported("waiting in a function");
        }
        String frame = Type.getInternalName(Frame.class);
        List<int[]> saved = new ArrayList<>();
        for (Block block : loops()) {
            if (block.kind() == BlockKind.LOOP || block.kind() == BlockKind.SECTION) {
                saved.add(new int[] {block.valuesSlot(), SAVED_VALUES});
                if (block.indicesSlot() >= 0) {
                    saved.add(new int[] {block.indicesSlot(), SAVED_INDICES});
                }
                saved.add(new int[] {block.indexSlot(), SAVED_INDEX});
            }
        }
        WaitPoint wait = new WaitPoint(new Label(), line, saved);
        int state = waits.size();
        waits.add(wait);

        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
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
        expression((Expr) delay.match().values().getFirst());
        method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Waits.class), "ticks", "(" + OBJECT_DESC + ")J", false);
        method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Waits.class), "schedule",
                "(" + Type.getDescriptor(Resumable.class) + CONTEXT + FRAME_DESC + "J)V", false);
        method.visitInsn(RETURN);
        method.visitLabel(wait.resume());
    }

    private Object define(byte[] bytes) {
        try {
            MethodHandles.Lookup hidden =
                    TriggerHost.lookup().defineHiddenClassWithClassData(bytes, List.copyOf(constants), true);
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
            if (parameter.plural()) {
                convert(true);
                method.visitMethodInsn(INVOKEVIRTUAL, STORE, "setList", "(" + STRING_DESC + OBJECTS + ")V", false);
            } else {
                convert(false);
                method.visitMethodInsn(INVOKEVIRTUAL, STORE, "set", "(" + STRING_DESC + OBJECT_DESC + ")V", false);
            }
        }
    }

    // ---------------------------------------------------------------- statements

    private void statements(List<Statement> statements) throws CompileException {
        for (int i = 0; i < statements.size(); i++) {
            Statement statement = statements.get(i);
            if (startsConditional(statement)) {
                i = conditional(statements, i) - 1;
            } else {
                statement(statement);
            }
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
            case Statement.Section section when isCore(section.header(), CoreSyntax.WHILE) -> whileLoop(section);
            case Statement.Section section when isCore(section.header(), CoreSyntax.FILTER_SECTION) -> filterSection(section);
            case Statement.Section section when isCore(section.header(), CoreSyntax.CATCH_ERRORS) -> {
                String errors = Type.getInternalName(co.xenastudios.neoskript.runtime.RuntimeErrors.class);
                Label end = new Label();
                method.visitMethodInsn(INVOKESTATIC, errors, "begin", "()V", false);
                block(BlockKind.CONDITIONAL, end, section.body());
                method.visitLabel(end);
                method.visitMethodInsn(INVOKESTATIC, errors, "end", "()V", false);
            }
            case Statement.Section section when library.sectionType(section.header() == null ? null
                    : section.header().syntax().id()) != null -> librarySection(section);
            case Statement.Section section -> throw unsupported("sections like '" + section.line().key() + "'");
            case Statement.Conditions conditions -> throw unsupported("'" + conditions.line().key() + "' here");
            case Statement.Parse parse -> {
                // Skript's test-only parse section: its code is only parsed, never run. What it reported is
                // kept for 'last parse logs'.
                constant(parse.captured().stream().map(d -> d.message()).toArray(String[]::new), String[].class);
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
                    condition(conditions.conditions().get(c));
                    method.visitJumpInsn(any ? IFNE : IFEQ, any ? then : next);
                }
                if (any) {
                    method.visitJumpInsn(GOTO, next);
                }
                method.visitLabel(then);
                i++;
                if (i >= statements.size() || !(statements.get(i) instanceof Statement.Section body)
                        || !body.line().key().strip().toLowerCase(Locale.ROOT).startsWith("then")) {
                    throw new CompileException(statement.line().number(),
                            "'" + statement.line().key() + "' has to be followed by a 'then' section");
                }
                block(BlockKind.CONDITIONAL, chainEnd, body.body());
            } else if (statement instanceof Statement.Section section && key.equals("else")) {
                lineOf(statement, section.line().key());
                block(BlockKind.CONDITIONAL, chainEnd, section.body());
                i++;
                break;
            } else if (statement instanceof Statement.Section section && key.startsWith("parse if")
                    && containsInvalid(section.body())) {
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
    private void loop(Statement.Section section) throws CompileException {
        Expr looped = (Expr) section.header().match().values().getFirst();
        int valuesSlot = nextSlot++;
        int indicesSlot = -1;
        int indexSlot = nextSlot++;
        if (looped instanceof Expr.Variable variable && variable.list()) {
            indicesSlot = nextSlot++;
            variableStore(variable);
            variableName(variable);
            method.visitMethodInsn(INVOKEVIRTUAL, STORE, "list", "(" + STRING_DESC + ")" + OBJECTS, false);
            method.visitVarInsn(ASTORE, valuesSlot);
            variableStore(variable);
            variableName(variable);
            method.visitMethodInsn(INVOKEVIRTUAL, STORE, "indices", "(" + STRING_DESC + ")[Ljava/lang/String;", false);
            method.visitVarInsn(ASTORE, indicesSlot);
        } else {
            expression(looped);
            convert(true);
            method.visitVarInsn(ASTORE, valuesSlot);
        }
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
        blocks.push(new Block(BlockKind.LOOP, end, next, valuesSlot, indicesSlot, indexSlot));
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
        blocks.push(new Block(BlockKind.WHILE, end, next, -1, -1, -1));
        statements(section.body());
        blocks.pop();
        method.visitJumpInsn(GOTO, next);
        method.visitLabel(end);
    }

    // ---------------------------------------------------------------- effects

    private void effect(ParsedSyntax effect) throws CompileException {
        if (library.binding(effect.syntax()) instanceof ElementFactory<?> factory
                && factory.create(new ElementInfo(effect.pattern(), effect.match().mark(), effect.match().tags()))
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
                if (function == null || function.returnType() == null) {
                    throw new CompileException(line, function == null
                            ? "'return' can only be used in a function"
                            : "The function " + function.name() + " doesn't return anything");
                }
                expression((Expr) effect.match().values().getFirst());
                convert(function.returnsPlural());
                method.visitInsn(ARETURN);
            }
            case CoreSyntax.CHANGE -> change(effect);
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
            case CoreSyntax.DELAY -> waitFor(effect);
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

    private void exit(ParsedSyntax effect) throws CompileException {
        if (effect.pattern() == 0) {
            exitTrigger();
            return;
        }
        int count = effect.pattern() == 1 ? 1 : regexNumber(effect);
        BlockKind kind = effect.match().hasTag("loop") ? BlockKind.LOOP
                : effect.match().hasTag("conditional") ? BlockKind.CONDITIONAL : null;
        int seen = 0;
        for (Block block : blocks) {
            boolean matches = kind == null || block.kind() == kind
                    || (kind == BlockKind.LOOP && block.kind() == BlockKind.WHILE);
            if (matches && ++seen == count) {
                method.visitJumpInsn(GOTO, block.end());
                return;
            }
        }
        throw new CompileException(line, "There aren't " + count + " sections like that to exit");
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
        Object target = effect.match().values().get(mode.target);
        if (mode == CoreSyntax.ChangeMode.SET && target instanceof Expr.Syntax whether
                && isCore(whether.syntax(), CoreSyntax.WHETHER)) {
            setCondition(effect, whether);
            return;
        }
        if (target instanceof Expr.Syntax syntax && !(library.binding(syntax.syntax().syntax()) instanceof Library.Core)) {
            changeExpression(effect, mode, syntax);
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
                    method.visitMethodInsn(INVOKEVIRTUAL, STORE, "subtract", nameValues, false);
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
        setTo(target, () -> {
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
        Object element = factory.create(new ElementInfo(parsed.pattern(), parsed.match().mark(), parsed.match().tags()));
        Class<?>[] accepted = element instanceof Changeable changeable ? changeable.acceptChange(Changeable.Mode.SET) : null;
        if (accepted == null) {
            throw new CompileException(line, source.substring(target.start(), target.end()) + " can't be set to anything");
        }
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
        Object element = factory.create(new ElementInfo(condition.pattern(), condition.match().mark(), condition.match().tags()));
        if (!(element instanceof SettableCondition)) {
            throw new CompileException(line, "'" + source.substring(whether.start(), whether.end()) + "' can't be set");
        }
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
        Object element = library.binding(syntax.syntax()) instanceof ElementFactory<?> factory
                ? factory.create(new ElementInfo(syntax.pattern(), syntax.match().mark(), syntax.match().tags()))
                : null;
        Class<?>[] accepted = element instanceof Changeable changeable ? changeable.acceptChange(changeMode) : null;
        if (accepted == null && !(element instanceof Changeable) && Values.hasChanger(changeMode)) {
            // An expression that can't be changed itself, but whose values can be: 'give X to all players'.
            changeValues(effect, mode, target);
            return;
        }
        if (accepted == null) {
            String text = source.substring(target.start(), target.end());
            throw new CompileException(line, text + switch (changeMode) {
                case SET -> " can't be set to anything";
                case ADD -> " can't have anything added to it";
                case REMOVE, REMOVE_ALL -> " can't have anything removed from it";
                case DELETE -> " can't be deleted";
                case RESET -> " can't be reset";
            });
        }
        constant(element, Changeable.class);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        arguments(syntax);
        if (mode.value >= 0) {
            expression((Expr) effect.match().values().get(mode.value));
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

    // ---------------------------------------------------------------- elements

    private void condition(ParsedSyntax condition) throws CompileException {
        callThrough(condition, Condition.class);
        method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Condition.class), "check",
                "(" + CONTEXT + OBJECTS + ")Z", true);
    }

    /** Pushes the element, the trigger context and the element's arguments, ready to call it. */
    private void callThrough(ParsedSyntax syntax, Class<?> type) throws CompileException {
        if (!(library.binding(syntax.syntax()) instanceof ElementFactory<?> factory)) {
            throw unsupported(syntax.syntax().id());
        }
        Object element = factory.create(
                new ElementInfo(syntax.pattern(), syntax.match().mark(), syntax.match().tags()));
        constant(element, type);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        arguments(syntax);
    }

    /** Pushes {@code value} as a constant of the class, typed as {@code type}. */
    private void constant(Object value, Class<?> type) {
        constants.add(value);
        method.visitLdcInsn(new ConstantDynamic("_", Type.getDescriptor(type), CLASS_DATA_AT, constants.size() - 1));
    }

    private void arguments(ParsedSyntax syntax) throws CompileException {
        CompiledPattern pattern = syntax.syntax().patterns().get(syntax.pattern());
        List<Object> values = syntax.match().values();
        PlaceholderSpec[] specs = placeholderSpecs(pattern);
        pushInt(values.size());
        method.visitTypeInsn(ANEWARRAY, OBJECT);
        for (int i = 0; i < values.size(); i++) {
            if (!(values.get(i) instanceof Expr expr)) {
                java.util.function.Function<Object, Object> fallback = eventDefault(specs[i]);
                if (fallback != null) {
                    method.visitInsn(DUP);
                    pushInt(i);
                    eventValue(fallback);
                    convert(isPlural(specs[i]));
                    method.visitInsn(AASTORE);
                }
                continue;
            }
            method.visitInsn(DUP);
            pushInt(i);
            List<String> outerLiteralTypes = literalTypes;
            literalTypes = specs[i].types();
            try {
                expression(expr);
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
        if (spec.types().size() != 1) {
            return null;
        }
        TypeRef ref = registry.resolveType(spec.types().getFirst());
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

    /** Pushes the value {@code getter} gets from the trigger's event. */
    private void eventValue(java.util.function.Function<Object, Object> getter) {
        constant(getter, java.util.function.Function.class);
        method.visitVarInsn(ALOAD, CONTEXT_SLOT);
        method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(TriggerContext.class), "event",
                "()" + OBJECT_DESC, true);
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/function/Function", "apply",
                "(" + OBJECT_DESC + ")" + OBJECT_DESC, true);
    }

    /** {@code player}, {@code event-world}: the event's value of the named type. */
    private void eventValue(Expr.Syntax syntax) throws CompileException {
        if (!(syntax.syntax().match().regexValues().getFirst() instanceof TypeRef ref)) {
            throw unsupported("'" + source.substring(syntax.start(), syntax.end()) + "'");
        }
        Block section = sectionFor(ref.type().id());
        if (section != null) {
            // 'entity' in 'spawn a pig:' is the pig.
            element(section.valuesSlot(), section.indexSlot(), 0);
            return;
        }
        java.util.function.Function<Object, Object> getter =
                eventClasses == null ? null : library.eventValue(eventClasses, ref.type().id());
        if (getter == null) {
            String where = eventClasses == null ? (function != null ? "a function" : "this event")
                    : article(eventName) + " " + eventName + " event";
            throw new CompileException(line, "There's no " + ref.type().name() + " in " + where);
        }
        eventValue(getter);
    }

    // ---------------------------------------------------------------- inline functions

    /**
     * Evaluates a list for an inline function into a values slot, and for a list variable its indices into
     * another (else -1). Returns {@code {valuesSlot, indicesSlot}}.
     */
    private int[] listValues(Expr list) throws CompileException {
        int valuesSlot = nextSlot++;
        int indicesSlot = -1;
        expression(list);
        convert(true);
        method.visitVarInsn(ASTORE, valuesSlot);
        if (list instanceof Expr.Variable variable && variable.list()) {
            indicesSlot = nextSlot++;
            variableStore(variable);
            variableName(variable);
            method.visitMethodInsn(INVOKEVIRTUAL, STORE, "indices", "(" + STRING_DESC + ")[" + STRING_DESC, false);
            method.visitVarInsn(ASTORE, indicesSlot);
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
        forEachInput(list, 1, reduced, (frame, next) -> {
            expression(folding);
            convert(false);
            method.visitVarInsn(ASTORE, reduced);
        });
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
        if (syntax.match().values().getFirst() instanceof Expr.Variable variable && variable.list()) {
            return variable;
        }
        throw new CompileException(line, problem + ": '" + source + "'");
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
        List<CommandPattern.Argument> arguments = commandArguments(expr);
        ParsedSyntax syntax = expr.syntax();
        int n = switch (syntax.pattern()) {
            case 0 -> arguments.size();
            case 1, 2 -> ordinal(syntax);
            default -> -1;
        };
        if (n == -1) {
            // 'the argument' is the only one; with several, it's all of them.
            if (arguments.size() == 1) {
                commandArgument(0);
            } else {
                method.visitVarInsn(ALOAD, CONTEXT_SLOT);
                method.visitMethodInsn(INVOKESTATIC, COMMAND_EVENT, "arguments", "(" + CONTEXT + ")" + OBJECTS, false);
            }
            return;
        }
        if (n < 1 || n > arguments.size()) {
            throw new CompileException(line, arguments.isEmpty() ? "This command doesn't have any arguments"
                    : "This command doesn't have a " + ordinalText(n) + " argument");
        }
        commandArgument(n - 1);
    }

    /** {@code player-arg}, {@code the text argument 2}: the Nth argument of that type. */
    private void typedArgument(Expr.Syntax expr) throws CompileException {
        List<CommandPattern.Argument> arguments = commandArguments(expr);
        List<Object> regexes = expr.syntax().match().regexValues();
        if (!(regexes.getFirst() instanceof TypeRef ref)) {
            throw unsupported("'" + source.substring(expr.start(), expr.end()) + "'");
        }
        int n = regexes.size() > 1 && regexes.get(1) instanceof java.util.regex.MatchResult number
                ? Integer.parseInt(number.group()) : 1;
        int seen = 0;
        for (int i = 0; i < arguments.size(); i++) {
            String type = arguments.get(i).type();
            if ((type.equals(ref.type().id()) || registry.isSubtype(type, ref.type().id())) && ++seen == n) {
                commandArgument(i);
                return;
            }
        }
        throw new CompileException(line, "This command doesn't have " + (n == 1 ? "a" : "a " + ordinalText(n)) + " "
                + ref.type().name() + " argument");
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
        switch (expr) {
            case Expr.Literal literal -> literal(literal);
            case Expr.Text text -> text(text.start(), text.end(), text.embedded());
            case Expr.ListOf list -> {
                pushInt(list.items().size());
                method.visitTypeInsn(ANEWARRAY, OBJECT);
                for (int i = 0; i < list.items().size(); i++) {
                    method.visitInsn(DUP);
                    pushInt(i);
                    expression(list.items().get(i));
                    method.visitInsn(AASTORE);
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
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.ARITHMETIC) -> arithmetic(syntax);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.LOOP_VALUE) -> loopValue(syntax.syntax());
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.EVENT_VALUE)
                    || isCore(syntax.syntax(), CoreSyntax.EVENT_VALUE_ENTITY) -> eventValue(syntax);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.ARGUMENT) -> argument(syntax);
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.FILTER) -> filter(syntax.syntax());
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
                method.visitMethodInsn(INVOKESTATIC, VALUES, "parse",
                        "(Ljava/util/function/Function;" + OBJECT_DESC + ")" + OBJECT_DESC, false);
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
            case Expr.Syntax syntax when isCore(syntax.syntax(), CoreSyntax.CAST) -> {
                expression((Expr) syntax.syntax().match().values().getFirst());
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
                callThrough(syntax.syntax(), Expression.class);
                method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Expression.class), "get",
                        "(" + CONTEXT + OBJECTS + ")" + OBJECT_DESC, true);
            }
            case Expr.Unresolved unresolved -> {
                // Words are first tried as the placeholder's own types: 'armor stand' is an entity type in
                // 'spawn an armor stand', though it names an item too.
                Object value = null;
                for (String name : literalTypes) {
                    TypeRef ref = registry.resolveType(name);
                    java.util.function.Function<String, ?> parser = ref == null ? null : library.literalParser(ref.type().id());
                    value = parser == null ? null : parser.apply(unresolved.text().strip());
                    if (value != null) {
                        break;
                    }
                }
                if (value == null) {
                    value = library.literal(unresolved.text());
                }
                if (value == null) {
                    throw unsupported("'" + unresolved.text() + "'");
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
                expression(embedded.get(i));
                method.visitMethodInsn(INVOKESTATIC, VALUES, "text", "(" + OBJECT_DESC + ")" + STRING_DESC, false);
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
        text(variable.start(), end, variable.embedded());
    }

    // ---------------------------------------------------------------- loop values

    /** {@code loop-value}, {@code loop-index}, {@code loop-counter} and {@code loop-<type>}, with {@code -N}. */
    private void loopValue(ParsedSyntax syntax) throws CompileException {
        String text = ((java.util.regex.MatchResult) syntax.match().regexValues().getFirst()).group();
        Matcher matcher = LOOP_VALUE.matcher(text.strip().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw unsupported("loop-" + text);
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
            case "counter", "iteration" -> {
                method.visitVarInsn(ILOAD, loop.indexSlot());
                method.visitInsn(I2L);
                method.visitLdcInsn((long) 1 + offset);
                method.visitInsn(LADD);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            }
            default -> element(loop.valuesSlot(), loop.indexSlot(), offset);
        }
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
        List<Character> operators = new ArrayList<>();
        flatten(syntax, operands, operators);
        emitArithmetic(operands, operators, 0, operands.size() - 1);
    }

    private void flatten(Expr expr, List<Expr> operands, List<Character> operators) {
        if (expr instanceof Expr.Syntax syntax && isCore(syntax.syntax(), CoreSyntax.ARITHMETIC)) {
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
                    operators.add(CoreSyntax.OPERATORS[parsed.pattern()]);
                }
            }
            return;
        }
        operands.add(expr);
    }

    /** Emits operands {@code [from, to]} with the operators between them, by precedence. */
    private void emitArithmetic(List<Expr> operands, List<Character> operators, int from, int to)
            throws CompileException {
        if (from == to) {
            expression(operands.get(from));
            return;
        }
        // Split at the operator that binds loosest: the last + or -, else the last * or /, else the first ^.
        int split = -1;
        for (String group : new String[] {"+-", "*/"}) {
            for (int i = to - 1; i >= from && split < 0; i--) {
                if (group.indexOf(operators.get(i)) >= 0) {
                    split = i;
                }
            }
        }
        if (split < 0) {
            split = from;
        }
        emitArithmetic(operands, operators, from, split);
        emitArithmetic(operands, operators, split + 1, to);
        String operation = switch (operators.get(split)) {
            case '+' -> "add";
            case '-' -> "subtract";
            case '*' -> "multiply";
            case '/' -> "divide";
            default -> "power";
        };
        method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Arithmetic.class), operation,
                "(" + OBJECT_DESC + OBJECT_DESC + ")" + OBJECT_DESC, false);
    }

    // ---------------------------------------------------------------- functions

    /** Calls a function, leaving what it returns (or {@code null}) on the stack. */
    private void call(Expr.FunctionCall call) throws CompileException {
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
        }
        List<FunctionInfo.Parameter> parameters = info.parameters();
        pushInt(parameters.size());
        method.visitTypeInsn(ANEWARRAY, OBJECT);
        for (int i = 0; i < parameters.size(); i++) {
            Expr argument = i < call.arguments().size() ? call.arguments().get(i) : null;
            if (argument == null) {
                continue;
            }
            method.visitInsn(DUP);
            pushInt(i);
            // Words are first tried as the parameter's type: 'chicken' is an entity type for 'data: entity type'.
            List<String> outerLiteralTypes = literalTypes;
            literalTypes = List.of(parameters.get(i).type());
            try {
                expression(argument);
            } finally {
                literalTypes = outerLiteralTypes;
            }
            convert(parameters.get(i).plural());
            method.visitInsn(AASTORE);
        }
        if (builtIn != null) {
            method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Function.class), "call",
                    "(" + OBJECTS + ")" + OBJECT_DESC, true);
        } else {
            method.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(FunctionHandle.class), "call",
                    "(" + OBJECTS + ")" + OBJECT_DESC, false);
        }
    }

    private void pushInt(int value) {
        method.visitLdcInsn(value);
    }

    private CompileException unsupported(String what) {
        return new CompileException(line, "NeoSkript can't run this yet: " + what);
    }
}
