/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.reader.ReaderException;
import org.yaml.snakeyaml.reader.StreamReader;
import org.yaml.snakeyaml.scanner.Constant;

/**
 * SnakeYAML's reader, over text that's all there already. SnakeYAML's own reads 1,024 characters at a time and copies
 * everything it hasn't moved past each time, so one long value (a megabyte of text with no line break) costs time that
 * grows with its square: 8 MB took 15 seconds, 29 MB four minutes, on the thread that loads the file. This one reads
 * the text where it is, in time that grows with its length.
 *
 * <p>It behaves as {@link StreamReader} does: the same positions, lines and columns, {@code \0} past the end, and the
 * same error for a character YAML doesn't allow, which it checks before reading.
 */
final class WholeTextReader extends StreamReader {

    private static final int[] NO_TEXT = new int[0];
    /** The text, when it has no surrogate pairs: each char is a code point. */
    private final String text;
    /** The text's code points, when it has surrogate pairs. */
    private final int[] codePoints;
    private final int length;
    private int pointer;
    private int index;
    private int documentIndex;
    private int line;
    private int column;

    WholeTextReader(String text) {
        super("");
        boolean pairs = false;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isSurrogate(text.charAt(i))) {
                pairs = true;
                break;
            }
        }
        this.text = pairs ? null : text;
        this.codePoints = pairs ? text.codePoints().toArray() : null;
        this.length = pairs ? codePoints.length : text.length();
        for (int i = 0; i < length; i++) {
            int codePoint = at(i);
            if (!isPrintable(codePoint)) {
                throw new ReaderException("'reader'", i, codePoint, "special characters are not allowed");
            }
        }
    }

    private int at(int i) {
        return text != null ? text.charAt(i) : codePoints[i];
    }

    /**
     * Where the reader is, for errors: line and column, without the text around it. Every node keeps two marks, so a
     * copy of the text in each would take more memory than the file; errors still say where the problem is.
     */
    @Override
    public Mark getMark() {
        return new Mark("'reader'", index, line, column, NO_TEXT, 0);
    }

    @Override
    public void forward() {
        forward(1);
    }

    @Override
    public void forward(int count) {
        for (int i = 0; i < count && pointer < length; i++) {
            int c = at(pointer++);
            index++;
            documentIndex++;
            if (Constant.LINEBR.has(c) || (c == '\r' && pointer < length && at(pointer) != '\n')) {
                line++;
                column = 0;
            } else if (c != 0xFEFF) {
                column++;
            }
        }
    }

    @Override
    public int peek() {
        return pointer < length ? at(pointer) : '\0';
    }

    @Override
    public int peek(int ahead) {
        return pointer + ahead < length ? at(pointer + ahead) : '\0';
    }

    @Override
    public String prefix(int count) {
        if (count == 0) {
            return "";
        }
        int end = Math.min(pointer + count, length);
        if (text != null) {
            return text.substring(pointer, end);
        }
        return new String(codePoints, pointer, end - pointer);
    }

    @Override
    public String prefixForward(int count) {
        String prefix = prefix(count);
        pointer += count;
        index += count;
        documentIndex += count;
        // As SnakeYAML's: a prefix never has a line break in it.
        column += count;
        return prefix;
    }

    @Override
    public int getColumn() {
        return column;
    }

    @Override
    public int getDocumentIndex() {
        return documentIndex;
    }

    @Override
    public void resetDocumentIndex() {
        documentIndex = 0;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public int getLine() {
        return line;
    }
}
