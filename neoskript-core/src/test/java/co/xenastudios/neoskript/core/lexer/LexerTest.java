package co.xenastudios.neoskript.core.lexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LexerTest {

    private final Lexer lexer = new Lexer();

    @Test
    void tokenizesWordsStringsAndPositions() {
        List<Token> tokens = lexer.tokenizeLine("send \"hi there\" to player", 1);

        assertEquals(TokenType.WORD, tokens.get(0).type());
        assertEquals("send", tokens.get(0).text());
        assertEquals(1, tokens.get(0).column());

        assertEquals(TokenType.STRING, tokens.get(1).type());
        assertEquals("hi there", tokens.get(1).text());

        assertEquals(TokenType.WORD, tokens.get(2).type());
        assertEquals("to", tokens.get(2).text());

        assertEquals(TokenType.WORD, tokens.get(3).type());
        assertEquals("player", tokens.get(3).text());

        assertEquals(TokenType.EOF, tokens.get(tokens.size() - 1).type());
    }

    @Test
    void tokenizesNumbersAndSymbols() {
        List<Token> tokens = lexer.tokenizeLine("set {_x} to 3.5", 7);

        assertEquals(TokenType.WORD, tokens.get(0).type());   // set
        assertEquals(TokenType.SYMBOL, tokens.get(1).type()); // {
        assertEquals(TokenType.WORD, tokens.get(2).type());   // _x
        assertEquals(TokenType.SYMBOL, tokens.get(3).type()); // }
        assertEquals(TokenType.WORD, tokens.get(4).type());   // to

        Token number = tokens.get(5);
        assertEquals(TokenType.NUMBER, number.type());
        assertEquals("3.5", number.text());
        assertEquals(7, number.line());
    }
}
