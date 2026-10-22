/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ARRAYLENGTH;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.DUP;
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
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V21;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Effect;
import co.xenastudios.neoskript.api.element.ElementFactory;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.Function;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.frontend.parse.Expr;
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
import co.xenastudios.neoskript.runtime.FunctionHandle;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.Trigger;
import co.xenastudios.neoskript.runtime.Values;
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
    private static final String STORE = Type.getInternalName(VariableStore.class);
    private static final String OBJECT = "java/lang/Object";
    private static final String OBJECT_DESC = "Ljava/lang/Object;";
    private static final String OBJECTS = "[Ljava/lang/Object;";
    private static final String STRING_DESC = "Ljava/lang/String;";
    private static final Handle CLASS_DATA_AT = new Handle(H_INVOKESTATIC, "java/lang/invoke/MethodHandles",
            "classDataAt", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;I)Ljava/lang/Object;",
            false);
    private static final Pattern LOOP_VALUE = Pattern.compile("(.+?)(?:-(\\d+))?");

    /** Local variable slots: 0 is this, 1 the trigger context, 2 a function's arguments. */
    private static final int CONTEXT_SLOT = 1;
    private static final int ARGUMENTS_SLOT = 2;
    private static final int LINE_SLOT = 3;
    private static final int EXCEPTION_SLOT = 4;
    private static final int LOCALS_SLOT = 5;

    private enum BlockKind {
        CONDITIONAL,
        LOOP,
        WHILE
    }

    /** A section being compiled, for exit, continue and loop values. */
    private record Block(BlockKind kind, Label end, Label next, int valuesSlot, int indicesSlot, int indexSlot) {}

    private final Library library;
    private final SyntaxRegistry registry;
    private final Map<FunctionInfo, FunctionHandle> functions;
    private final VariableStore globals;
    private final List<Object> constants = new ArrayList<>();
    private final Deque<Block> blocks = new ArrayDeque<>();
    private MethodVisitor method;
    private int nextSlot = LOCALS_SLOT + 1;
    private FunctionInfo function;
    /** The text expression positions refer to: the current line, a section header's key, or a default value. */
    private String source;
    private int line;

    TriggerCompiler(Library library, Map<FunctionInfo, FunctionHandle> functions, VariableStore globals) {
        this.library = library;
        this.registry = library.registry();
        this.functions = functions;
        this.globals = globals;
    }

    Trigger compileTrigger(String scriptName, List<Statement> body) throws CompileException {
        byte[] bytes = generate(scriptName, Trigger.class, "execute", "(" + CONTEXT + ")V", () -> statements(body));
        return (Trigger) define(bytes);
    }

    CompiledFunction compileFunction(String scriptName, ParsedStructure.Function parsed) throws CompileException {
        function = parsed.signature();
        byte[] bytes = generate(scriptName, CompiledFunction.class, "call", "(" + CONTEXT + OBJECTS + ")" + OBJECT_DESC,
                () -> {
                    parameters(parsed);
                    statements(parsed.body());
                });
        return (CompiledFunction) define(bytes);
    }

    @FunctionalInterface
    private interface Body {
        void emit() throws CompileException;
    }

    private byte[] generate(String scriptName, Class<?> type, String name, String descriptor, Body body)
            throws CompileException {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String first, String second) {
                return OBJECT;
            }
        };
        writer.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, PACKAGE + (function == null ? "Trigger" : "Function"),
                null, OBJECT, new String[] {Type.getInternalName(type)});
        writer.visitSource(scriptName, null);

        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, OBJECT, "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        method = writer.visitMethod(ACC_PUBLIC, name, descriptor, null, null);
        method.visitCode();
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        method.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
        pushInt(0);
        method.visitVarInsn(ISTORE, LINE_SLOT);
        method.visitTypeInsn(NEW, STORE);
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, STORE, "<init>", "()V", false);
        method.visitVarInsn(ASTORE, LOCALS_SLOT);
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
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
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
            case Statement.Section section -> throw unsupported("sections like '" + section.line().key() + "'");
            case Statement.Conditions conditions -> throw unsupported("'" + conditions.line().key() + "' here");
            case Statement.Parse parse -> throw unsupported("parse sections");
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
                for (ParsedSyntax condition : conditions.conditions()) {
                    condition(condition);
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
            default -> throw unsupported(core.id());
        }
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
        if (!(target instanceof Expr.Variable variable)) {
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
                continue;
            }
            method.visitInsn(DUP);
            pushInt(i);
            expression(expr);
            convert(isPlural(specs[i]));
            method.visitInsn(AASTORE);
        }
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
            case Expr.Syntax syntax -> {
                callThrough(syntax.syntax(), Expression.class);
                method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Expression.class), "get",
                        "(" + CONTEXT + OBJECTS + ")" + OBJECT_DESC, true);
            }
            case Expr.Unresolved unresolved -> throw unsupported("'" + unresolved.text() + "'");
        }
    }

    private void literal(Expr.Literal literal) throws CompileException {
        String text = literal.text().replace("_", "");
        switch (literal.type()) {
            case "integer" -> {
                method.visitLdcInsn(Long.parseLong(text.startsWith("+") ? text.substring(1) : text));
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
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
            default -> throw unsupported(literal.type() + " literals");
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
        switch (matcher.group(1)) {
            case "index" -> {
                if (loop.indicesSlot() < 0) {
                    throw new CompileException(line, "'loop-index' only works when looping a list variable");
                }
                method.visitVarInsn(ALOAD, loop.indicesSlot());
                method.visitVarInsn(ILOAD, loop.indexSlot());
                method.visitInsn(AALOAD);
            }
            case "counter", "iteration" -> {
                method.visitVarInsn(ILOAD, loop.indexSlot());
                method.visitInsn(I2L);
                method.visitInsn(LCONST_1);
                method.visitInsn(LADD);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            }
            default -> {
                method.visitVarInsn(ALOAD, loop.valuesSlot());
                method.visitVarInsn(ILOAD, loop.indexSlot());
                method.visitInsn(AALOAD);
            }
        }
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
            expression(argument);
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
