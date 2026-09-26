package top.zedo.skin.uis.component;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;
import top.zedo.skin.uis.ExpressionCalculator;
import top.zedo.skin.uis.MuiPixelAnchor;
import top.zedo.skin.uis.UISComponent;
import top.zedo.skin.uis.UISSkin;

import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class ImageComponentRendererTest {
    @Test
    void initialPositionRoundsNativeAxesBeforeInvertingY() {
        ExpressionCalculator calculator = new ExpressionCalculator(1280, 720.5, 720);
        UISSkin skin = new UISSkin(Path.of("skin.mui"), calculator);
        UISComponent component = new UISComponent("_image", new HashMap<>(), skin);
        component.putProperty("pos", "2.5px,-2.5px", 0);
        ImageComponentRenderer renderer = new ImageComponentRenderer(component);

        assertEquals(3, renderer.pos.getX());
        assertEquals(723.5, renderer.pos.getY());
    }

    @Test
    void pixelTupleAnchorUsesOriginalBottomOriginContentSize() {
        ExpressionCalculator calculator = new ExpressionCalculator(1280, 720, 720);
        UISSkin skin = new UISSkin(Path.of("skin.mui"), calculator);
        UISComponent component = new UISComponent("_image", new HashMap<>(), skin);
        component.putProperty("tex", "missing.png", 0);
        component.putProperty("pos", "100px,100px", 0);
        component.putProperty("size", "200px,100px", 0);
        component.putProperty("anchor", "0,0", 0);
        ImageComponentRenderer renderer = new ImageComponentRenderer(component);

        assertEquals(new MuiPixelAnchor(0, 0), component.getPixelAnchor());
        renderer.transform();
        Point2D topLeft = renderer.affine.transform(renderer.pos.getX(), renderer.pos.getY());
        assertEquals(100, topLeft.getX(), 0.001);
        assertEquals(520, topLeft.getY(), 0.001);

        component.putProperty("anchor", "0", 0);
        renderer.reloadPos();
        renderer.transform();
        topLeft = renderer.affine.transform(renderer.pos.getX(), renderer.pos.getY());
        assertEquals(620, topLeft.getY(), 0.001);
        assertNull(component.getPixelAnchor());
    }
}
