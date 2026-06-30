package co.xenastudios.neoskript.lang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparisonsTest {

    @Test
    void equalityComparesNumbersByValueAndOthersByEquals() {
        assertTrue(Comparisons.equal(5.0, 5));        // 5.0 == 5 numerically
        assertTrue(Comparisons.equal("a", "a"));
        assertFalse(Comparisons.equal("a", "b"));
        assertFalse(Comparisons.equal(1.0, 2.0));
        assertTrue(Comparisons.equal(null, null));
        assertFalse(Comparisons.equal("1", 1));       // string vs number: not equal
    }

    @Test
    void numericComparisonOrders() {
        assertTrue(Comparisons.compare(1, 2) < 0);
        assertTrue(Comparisons.compare(3.0, 2.0) > 0);
        assertEquals(0, Comparisons.compare(2, 2.0));
    }

    @Test
    void numericComparisonParsesNumericStrings() {
        assertTrue(Comparisons.compare("3", "10") < 0);
    }

    @Test
    void nonNumericComparisonIsNull() {
        assertNull(Comparisons.compare("abc", 5));
        assertNull(Comparisons.compare(new Object(), new Object()));
    }
}
