package top.zedo.skin.uis;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void projectedMarkerCentersMatchDocumented437DeviceMeasurements() {
        UISPerspectiveTransform transform = new UISPerspectiveTransform();
        transform.setSize(2376, 1152);

        // docs/4.3.7-audit.md:31-38: left-column marker centers, top to bottom.
        // The script uses pos=20%,85% / 50% / 15%, with initial native positions
        // rounded to x=475 and top-origin y=173 / 576 / 979 on this canvas.
        // @angle 0 with @apply 3d is resolved to 30 degrees by MuiSkinLoader.
        double[][] deviceX = {
                {1, 485.5, 480.5, 475.5},
                {28, 780.5, 693, 557.5},
                {30, 802, 711.5, 567},
                {40, 913, 820, 631}
        };
        double[] markerY = {173, 576, 979};
        for (double[] sample : deviceX) {
            transform.setAngle(sample[0]);
            for (int row = 0; row < markerY.length; row++) {
                double projectedX = transform.transform(new Point2D(475, markerY[row])).getX();
                assertEquals(sample[row + 1], projectedX, 2.0,
                        "device marker x at angle " + sample[0] + ", row " + row);
            }
        }
    }

    @Test
    void nineMarkerPositionsRemainFiniteAndReversibleAcrossObservedAngles() {
        UISPerspectiveTransform transform = new UISPerspectiveTransform();
        transform.setSize(2376, 1152);

        // 55 degrees appears in the local skin corpus; no device-pixel claim is made for it.
        for (double angle : new double[]{1, 28, 30, 40, 45, 55}) {
            transform.setAngle(angle);
            for (double xPercent : new double[]{20, 50, 80}) {
                for (double yPercent : new double[]{15, 50, 85}) {
                    Point2D source = new Point2D(2376 * xPercent / 100, 1152 * yPercent / 100);
                    Point2D projected = transform.transform(source);
                    assertTrue(Double.isFinite(projected.getX()) && Double.isFinite(projected.getY()),
                            "finite projection at angle " + angle + ", point " + source);
                    Point2D restored = transform.untransform(projected);
                    assertTrue(Double.isFinite(restored.getX()) && Double.isFinite(restored.getY()),
                            "finite inverse at angle " + angle + ", point " + source);
                    assertEquals(source.getX(), restored.getX(), 0.1, "x at angle " + angle);
                    assertEquals(source.getY(), restored.getY(), 0.1, "y at angle " + angle);
                }
            }
        }
    }
}
