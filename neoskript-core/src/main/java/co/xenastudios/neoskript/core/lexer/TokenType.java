package co.xenastudios.neoskript.core.lexer;

/**
 * The lexical categories produced by the {@link Lexer}.
 */
public enum TokenType {
    /** An identifier or keyword word, e.g. {@code send}, {@code player}. */
    WORD,
    /** A double-quoted string literal (text excludes the surrounding quotes). */
    STRING,
    /** A numeric literal, e.g. {@code 42} or {@code 3.5}. */
    NUMBER,
    /** A single punctuation/operator character, e.g. {@code %}, {@code (}, {@code :}. */
    SYMBOL,
    /** End of the tokenized input. */
    EOF
}
