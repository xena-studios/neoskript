package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.ArithmeticExpression;
import co.xenastudios.neoskript.core.expression.NumberLiteral;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OptimizerTest {

    private final ExpressionParser parser =
            new ExpressionParser(new DefaultSyntaxRegistry(), new FunctionRegistry());

    @Test
    void foldsConstantArithmeticToALiteral() {
        Expression<?> folded = parser.parse("2 + 3 * 4");
        assertInstanceOf(NumberLiteral.class, folded, "constant arithmetic should fold at parse time");
        assertEquals(14.0, folded.getSingle(null));
    }

    @Test
    void foldsNestedConstantArithmetic() {
        Expression<?> folded = parser.parse("(2 + 3) * (10 - 4)");
        assertInstanceOf(NumberLiteral.class, folded);
        assertEquals(30.0, folded.getSingle(null));
    }

    @Test
    void doesNotFoldExpressionsWithVariables() {
        Expression<?> notFolded = parser.parse("{_x} + 1");
        assertFalse(notFolded instanceof NumberLiteral);
        assertInstanceOf(ArithmeticExpression.class, notFolded);
    }
}
