package top.zedo.skin.uis;

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

    @Test
    void preservesLegacyProjectionCalibration() {
        UISPerspectiveTransform transform = new UISPerspectiveTransform();
        transform.setSize(1280, 720);
        double[][] expectedTopLeft = {
                {0, 0},
                {10, 116.55977},
                {25, 270.76712},
                {40, 418.10936},
                {45, 469.37821}
        };
        for (double[] sample : expectedTopLeft) {
            transform.setAngle(sample[0]);
            assertEquals(sample[1], transform.transform(new Point2D(0, 0)).getX(), 0.02);
            assertEquals(1280 - sample[1], transform.transform(new Point2D(1280, 0)).getX(), 0.02);
            assertEquals(0, transform.transform(new Point2D(0, 720)).getX(), 0.02);
        }
    }
}
