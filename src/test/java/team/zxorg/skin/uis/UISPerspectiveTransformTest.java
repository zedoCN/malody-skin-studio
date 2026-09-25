package team.zxorg.skin.uis;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UISPerspectiveTransformTest {
    @Test
    void transformCanBeReversedAtTypicalAngles() {
        UISPerspectiveTransform transform = new UISPerspectiveTransform();
        transform.setSize(1280, 720);

        for (double angle : new double[]{0, 10, 25}) {
            transform.setAngle(angle);
            for (Point2D point : new Point2D[]{
                    new Point2D(0, 0),
                    new Point2D(320, 180),
                    new Point2D(640, 360),
                    new Point2D(960, 540)
            }) {
                Point2D restored = transform.untransform(transform.transform(point));
                assertEquals(point.getX(), restored.getX(), 0.05, "x at angle " + angle);
                assertEquals(point.getY(), restored.getY(), 0.05, "y at angle " + angle);
            }
        }
    }
}
