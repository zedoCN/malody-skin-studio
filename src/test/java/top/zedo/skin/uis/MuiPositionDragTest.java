package top.zedo.skin.uis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MuiPositionDragTest {
    @Test
    void keepsOriginalUnitsAndMovesInScreenCoordinates() {
        ExpressionCalculator calculator = new ExpressionCalculator(1280, 720, 720);
        String original = "50%+10,100";
        String moved = MuiPositionDrag.translate(original, 25.5, 12);
        ExpressionVector before = new ExpressionVector(calculator, original, 0);
        ExpressionVector after = new ExpressionVector(calculator, moved, 0);

        assertEquals("50%+10+25.5px,100-12px", moved);
        assertEquals(before.getX() + 25.5, after.getX(), 0.01);
        assertEquals(before.getY() + 12, after.getY(), 0.01);
        assertEquals("50%,40%", MuiPositionDrag.translate("50%,40%", 0, 0));
    }

    @Test
    void rejectsGroupedAndMalformedPositions() {
        assertThrows(IllegalArgumentException.class, () -> MuiPositionDrag.translate("20%$80,50%", 10, 0));
        assertThrows(IllegalArgumentException.class, () -> MuiPositionDrag.translate("10,20,30", 10, 0));
        assertThrows(IllegalArgumentException.class, () -> MuiPositionDrag.translate("10,20", Double.NaN, 0));
    }
}
