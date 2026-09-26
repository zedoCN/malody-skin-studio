package top.zedo.skin.uis.component;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import top.zedo.skin.basis.RenderRectangle;
import top.zedo.skin.uis.UISComponent;

public class ImageComponentRenderer extends AbstractComponentRenderer {
    protected BlendMode blend;

    public ImageComponentRenderer(UISComponent component) {
        super(component);
    }

    /** Hit testing is limited to untransformed, static custom images. */
    public boolean hitTestForPositionEdit(double x, double y) {
        if (!getName().startsWith("_") || type != 0 || hide || opacity <= 0 || tex == null
                || tex.isError() || is3DLayout() || rotate != 0 || flip != null
                || getMotion() != null || component.contains("motion")
                || (component.getRawProperty("anchor") != null
                    && component.getRawProperty("anchor").contains(","))
                || skew.getW() != 0 || skew.getH() != 0 || pos == null || size == null
                || component.getPropertySource("pos") == null
                || component.hasPositionParent()
                || component.getPropertySource("pos").grouped()) return false;
        double width = size.getW(), height = size.getH();
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) return false;
        double left = pos.getX() - (anchor.getHpos() == HPos.CENTER ? width / 2
                : anchor.getHpos() == HPos.RIGHT ? width : 0);
        double top = pos.getY() - (anchor.getVpos() == VPos.CENTER ? height / 2
                : anchor.getVpos() == VPos.BOTTOM ? height : 0);
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    @Override
    void reloadResComponent() {
        if (!getName().startsWith("_"))
            tex = component.getImageOrNull("tex");
    }

    @Override
    void reloadPosComponent() {
        //System.out.println(this.getName() + " pos: " + pos + "    size: " + size);
    }


    @Override
    public void initialize(Canvas canvas) {
        super.initialize(canvas);
    }

    @Override
    public void message(Object value) {
        super.message(value);
    }

    @Override
    void drawComponent(double width, double height, long time) {
        transform();
        //rr.drawImage(gc, tex);
        drawImage(tex);
    }

    @Override
    public String toString() {
        return "ImageComponentRender{" +
                "component=" + component +
                '}';
    }
}
