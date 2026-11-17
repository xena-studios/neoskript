/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang.parser;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.skriptlang.skript.registration.SyntaxInfo;

/**
 * The syntax around the element being set up, innermost on top, as Skript's parser has it: NeoSkriptCompat fills it
 * from what NeoSkript is compiling around the element, an addon's syntax with its info and NeoSkript's own as an
 * element of no addon's.
 */
public class ParsingStack implements Iterable<ParsingStack.Element> {

    private final List<Element> stack = new ArrayList<>();

    public ParsingStack() {}

    public ParsingStack(ParsingStack other) {
        stack.addAll(other.stack);
    }

    public Element pop() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Stack is empty");
        }
        return stack.removeLast();
    }

    public Element peek(int index) {
        if (index < 0 || index >= stack.size()) {
            throw new IndexOutOfBoundsException(index);
        }
        return stack.get(stack.size() - 1 - index);
    }

    public Element peek() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Stack is empty");
        }
        return stack.getLast();
    }

    public void push(Element element) {
        stack.add(element);
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public int size() {
        return stack.size();
    }

    @Override
    public Iterator<Element> iterator() {
        return List.copyOf(stack).reversed().iterator();
    }

    /** One element of the stack: its syntax and which of its patterns. */
    public record Element(SyntaxInfo<?> syntaxInfo, int patternIndex) {

        public SyntaxInfo<?> syntaxElementInfo() {
            return syntaxInfo;
        }

        public Class<?> getSyntaxElementClass() {
            return syntaxInfo.type();
        }

        public String getPattern() {
            return syntaxInfo.patterns().stream().skip(patternIndex).findFirst().orElse(null);
        }

        public String[] getPatterns() {
            return syntaxInfo.patterns().toArray(String[]::new);
        }
    }
}
