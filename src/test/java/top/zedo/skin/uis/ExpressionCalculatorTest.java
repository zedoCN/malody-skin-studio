package top.zedo.skin.uis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionCalculatorTest {
    @Test
    void evaluatesMuiUnitsAndRejectsMalformedExpressions() {
        ExpressionCalculator calculator = new ExpressionCalculator(1600, 900, 720);
        assertEquals(800, calculator.calculateX("50%"));
        assertEquals(450, calculator.calculateY("50%"));
        assertEquals(125, calculator.calculateX("100"));
        assertEquals(10, calculator.calculateX("10px"));
        assertEquals(31.25, calculator.calculateX("25w"));
        assertEquals(825, calculator.calculateX("50%+25w-6.25px"));
        assertEquals(25, calculator.calculateX("10*2"));
        assertEquals(100, calculator.calculateX("(40+40)/1"));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateX("10/0"));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateX("10bad"));
    }

    @Test
    void verticalBatchDeltaUsesVerticalAxis() {
        ExpressionCalculator calculator = new ExpressionCalculator(1600, 900, 720);
        ExpressionVector vector = new ExpressionVector(calculator, "10,10%$10%", 2);
        assertEquals(630, vector.getY());
        assertEquals(3, ExpressionCalculator.calculateScalar("1+2"));
    }
}
