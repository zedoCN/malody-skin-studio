package top.zedo.skin.uis;

import javafx.geometry.Point2D;
import top.zedo.zxncore.ZXLogger;


/**
 * 表达式计算器
 */
public class ExpressionCalculator {

    UISPerspectiveTransform perspectiveTransform=new UISPerspectiveTransform();


    public Point2D transform(Point2D point) {
        return perspectiveTransform.transform(point);
    }



    public static void main(String[] args) {
        ExpressionCalculator expressionCalculator = new ExpressionCalculator();
        expressionCalculator.setUnitCanvasHeight(720);
        ExpressionVector expressionVector = new ExpressionVector(expressionCalculator, "100%,100%", 0);

        expressionCalculator.setCanvasSize(1920, 1080);
        expressionVector.setX("100%");
        System.out.println(expressionVector);

        expressionCalculator.setCanvasSize(1280, 720);
        expressionVector.setX("100%");
        System.out.println(expressionVector);
    }

    /**
     * 画布宽度
     */
    private double canvasWidth;
    /**
     * 画布高度
     */
    private double canvasHeight;
    /**
     * 像素倍率
     */
    private double pixelMagnification;
    /**
     * 单位画布高度
     */
    private double unitCanvasHeight;

    /**
     * 设置变换角度
     *
     * @param angle 角度
     */
    public void setAngle(double angle) {
        perspectiveTransform.setAngle(angle);
    }

    public ExpressionCalculator() {
        this(0, 0, MuiRules.DEFAULT_UNIT_HEIGHT);
    }

    public ExpressionCalculator(double canvasWidth, double canvasHeight, double unitCanvasHeight) {
        //this.canvasWidth = canvasWidth;
        //this.canvasHeight = canvasHeight;
        this.unitCanvasHeight = unitCanvasHeight;
        //pixelMagnification = canvasHeight / unitCanvasHeight;
        setCanvasSize(canvasWidth, canvasHeight);

    }

    @Override
    public String toString() {
        return "ExpressionCalculator{" +
                "canvasWidth=" + canvasWidth +
                ", canvasHeight=" + canvasHeight +
                ", pixelMagnification=" + pixelMagnification +
                ", unitCanvasHeight=" + unitCanvasHeight +
                '}';
    }

    /**
     * 获取像素倍率
     */
    public double getPixelMagnification() {
        return pixelMagnification;
    }


    public void setCanvasSize(double width, double height) {
        ZXLogger.info("setCanvasSize: " + width + "," + height);
        this.canvasWidth = width;
        this.canvasHeight = height;
        pixelMagnification = height / unitCanvasHeight;
        if (perspectiveTransform != null)
            perspectiveTransform.setSize(width, height);
    }

    public double getCanvasWidth() {
        return canvasWidth;
    }

    public double getCanvasHeight() {
        return canvasHeight;
    }

    public double getUnitCanvasHeight() {
        return unitCanvasHeight;
    }

    public void setUnitCanvasHeight(double unitCanvasHeight) {
        ZXLogger.info("设置单位画布高度为：" + unitCanvasHeight);
        this.unitCanvasHeight = unitCanvasHeight;
        pixelMagnification = canvasHeight / unitCanvasHeight;
    }

    /**
     * 计算X坐标
     *
     * @param expression 表达式
     * @return 值
     */
    protected double calculateX(String expression) {
        return calculateValue(expression, canvasWidth);
    }

    /**
     * 计算Y坐标
     *
     * @param expression 表达式
     * @return 值
     */
    protected double calculateY(String expression) {
        return calculateValue(expression, canvasHeight);
    }

    /**
     * 计算绝对像素值
     *
     * @param expression 表达式
     * @param maxPixel   最大像素值
     * @return 值
     */
    private double calculateValue(String expression, double maxPixel) {
        return MuiNumberExpression.evaluate(expression, pixelMagnification, maxPixel,
                canvasWidth / MuiRules.WIDTH_UNIT_BASE);
    }

    /** Evaluates a unitless UIS property such as toggle=1+2. */
    static double calculateScalar(String expression) {
        if (expression == null || expression.isBlank()) throw new IllegalArgumentException("空数值");
        return MuiNumberExpression.evaluate(expression, 1, 0, 0);
    }
}
