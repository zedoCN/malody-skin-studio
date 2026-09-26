package top.zedo.skin.uis.component;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import top.zedo.skin.uis.ExpressionVector;
import top.zedo.skin.uis.UISComponent;
import top.zedo.zxncore.ZXLogger;

/** Previews the 3×3 sliced sprite selected by UIS type=5 and rect=x,y,width,height. */
public final class Scale9ComponentRenderer extends AbstractComponentRenderer {
    private Image[][] slices;
    private int[] sourceX;
    private int[] sourceY;
    private boolean sliced;
    private double borderScale;
    private boolean scaledBorders;

    public Scale9ComponentRenderer(UISComponent component) {
        super(component);
    }

    @Override
    void reloadResComponent() {
        sliced = false;
        slices = new Image[3][3];
        sourceX = new int[4];
        sourceY = new int[4];
        if (tex == null || tex.isError() || tex.getPixelReader() == null) return;
        double[] rect;
        try {
            rect = component.getRectangle("rect");
        } catch (IllegalArgumentException error) {
            ZXLogger.warning(component.getFullName() + " 九宫格区域无效: " + error.getMessage());
            return;
        }
        if (rect == null) return;
        int imageWidth = (int) Math.round(tex.getWidth());
        int imageHeight = (int) Math.round(tex.getHeight());
        int left = (int) Math.round(rect[0]);
        int top = (int) Math.round(rect[1]);
        int centerWidth = (int) Math.round(rect[2]);
        int centerHeight = (int) Math.round(rect[3]);
        if (left < 0 || top < 0 || centerWidth <= 0 || centerHeight <= 0
                || left + centerWidth > imageWidth || top + centerHeight > imageHeight) {
            ZXLogger.warning(component.getFullName() + " 九宫格区域超出纹理");
            return;
        }
        sourceX[0] = 0;
        sourceX[1] = left;
        sourceX[2] = left + centerWidth;
        sourceX[3] = imageWidth;
        sourceY[0] = 0;
        sourceY[1] = top;
        sourceY[2] = top + centerHeight;
        sourceY[3] = imageHeight;
        PixelReader reader = tex.getPixelReader();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int width = sourceX[column + 1] - sourceX[column];
                int height = sourceY[row + 1] - sourceY[row];
                slices[row][column] = width > 0 && height > 0
                        ? new WritableImage(reader, sourceX[column], sourceY[row], width, height) : null;
            }
        }
        sliced = true;
    }

    @Override
    void reloadPosComponent() {
        scaledBorders = false;
        borderScale = 1;
        if (component.contains("size2")) {
            ExpressionVector size2 = component.getExpressionVector("size2");
            if (size2.getH() > 0) {
                // 4.3.7 scales both axes' source borders by the canvas-height ratio.
                // size2.x did not affect the measured 9-slice on the Android client.
                borderScale = ec.getCanvasHeight() / size2.getH();
                scaledBorders = true;
            } else {
                ZXLogger.warning(component.getFullName() + " 的 type=5 size2 高度须大于 0");
            }
        }
    }

    @Override
    void drawComponent(double width, double height, long time) {
        double targetWidth = size.getW();
        double targetHeight = size.getH();
        if (!sliced || targetWidth <= 0 || targetHeight <= 0) {
            transform();
            drawImage(tex);
            return;
        }
        double[] destinationX = destinationCuts(sourceX, targetWidth, borderScale, !scaledBorders);
        double[] destinationY = destinationCuts(sourceY, targetHeight, borderScale, !scaledBorders);
        transform();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                Image slice = slices[row][column];
                if (slice == null) continue;
                double drawWidth = destinationX[column + 1] - destinationX[column];
                double drawHeight = destinationY[row + 1] - destinationY[row];
                if (drawWidth > 0 && drawHeight > 0) {
                    drawImage(slice, pos.getX() + destinationX[column], pos.getY() + destinationY[row],
                            drawWidth, drawHeight);
                }
            }
        }
    }

    static double[] destinationCuts(int[] source, double targetSize) {
        // 4.3.7 keeps the texture's border pixels at their native size when size2 is absent.
        return destinationCuts(source, targetSize, 1, true);
    }

    static double[] destinationCuts(int[] source, double targetSize, double borderScale, boolean fitBorders) {
        double left = source[1] * borderScale;
        double right = (source[3] - source[2]) * borderScale;
        double borders = left + right;
        if (fitBorders && borders > targetSize) {
            double shrink = targetSize / borders;
            left *= shrink;
            right *= shrink;
        }
        return new double[]{0, left, targetSize - right, targetSize};
    }
}
