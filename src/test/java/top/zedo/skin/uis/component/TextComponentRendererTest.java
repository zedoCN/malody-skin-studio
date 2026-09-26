package top.zedo.skin.uis.component;

import org.junit.jupiter.api.Test;
import top.zedo.skin.uis.ExpressionCalculator;
import top.zedo.skin.uis.MuiRules;
import top.zedo.skin.uis.UISComponent;
import top.zedo.skin.uis.UISSkin;

import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextComponentRendererTest {
    @Test
    void uses437VerticalFontUnitsAndInitialNumericConversions() {
        ExpressionCalculator calculator = new ExpressionCalculator(1280, 720, 900);
        UISSkin skin = new UISSkin(Path.of("skin.mui"), calculator);
        UISComponent component = new UISComponent("_label", new HashMap<>(), skin);
        component.putProperty("fsize", "25%", 0);
        component.putProperty("rotate", "12.9", 0);
        component.putProperty("opacity", "50.5", 0);
        TextComponentRenderer renderer = new TextComponentRenderer(component);

        assertEquals(180, renderer.fsize);
        assertEquals(12, renderer.rotate);
        assertEquals(128 / 255.0, renderer.opacity);
        assertEquals(1, MuiRules.opacityFromPercent(100));

        component.putProperty("fsize", "100", 0);
        assertEquals(80, component.getVerticalLength("fsize", 20));
        component.putProperty("fsize", "10px", 0);
        assertEquals(10, component.getVerticalLength("fsize", 20));
        component.putProperty("fsize", "15w", 0);
        assertEquals(15, component.getVerticalLength("fsize", 20));
        assertEquals(20, component.getVerticalLength("missing", 20));
    }
}
