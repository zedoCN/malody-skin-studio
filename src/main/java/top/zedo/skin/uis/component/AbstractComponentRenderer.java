package top.zedo.skin.uis.component;

import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.PerspectiveTransform;
import javafx.scene.effect.Shadow;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;
import top.zedo.skin.basis.RenderInterface;
import top.zedo.skin.basis.RenderRectangle;
import top.zedo.skin.uis.ExpressionCalculator;
import top.zedo.skin.uis.ExpressionVector;
import top.zedo.skin.uis.UISComponent;
import top.zedo.skin.uis.MuiRules;
import top.zedo.ui.component.LayerCanvasPane;

/**
 * 组件属性数据
 */
public abstract class AbstractComponentRenderer implements RenderInterface {


    /**
     * 位置
     */
    public ExpressionVector pos;
    /**
     * 尺寸
     */
    public ExpressionVector size;
    /**
     * 基于锚点的旋转角 数值表示元件逆时针的转角,单位角度
     */
    public double rotate;
    /**
     * 透明度 范围 0.-1., 1.表示不透明, 0.表示完全透明
     */
    public double opacity = 1;
    /**
     * 缩放
     */
    public ExpressionVector scale;
    /**
     * 是否隐藏
     */
    public boolean hide;
    /**
     * 倾斜
     */
    public ExpressionVector skew;
    protected Affine affine = new Affine();
    protected ExpressionCalculator ec;
    /**
     * 父元件
     */
    protected AbstractComponentRenderer parent;
    /**
     * 元件名
     */
    protected String name;
    /**
     * 纹理图片
     */
    protected Image tex;
    /**
     * 锚点
     */
    protected Pos anchor;
    /**
     * 填充颜色
     */
    protected Color color;
    /**
     * 图层深度
     * 0为最底层, 10为note所在层, 大于100的元件将不受是否开启3D的影响
     */
    protected int zindex;
    /**
     * 元件类型
     */
    protected int type;
    /**
     * 元件索引值 如'_sprite-4'为 3
     */
    protected int index;
    /**
     * 原始组件
     */
    protected UISComponent component;
    /**
     * 翻转
     */
    protected Orientation flip;
    /**
     * 材质尺寸
     */
    protected ExpressionVector texSize;
    /**
     * 像素倍率
     */
    protected double pixelMagnification;
    PerspectiveTransform pt = new PerspectiveTransform();
    /**
     * 混合模式 1=additive 2=screen
     */
    int blend;
    /**
     * 渲染上下文
     */
    GraphicsContext gc;
    /**
     * 渲染矩形
     */
    //private final RenderRectangle rr = new RenderRectangle();
    private Shadow shadow;
    /**
     * 动画组件
     */
    private UISComponent motion;

    public AbstractComponentRenderer(UISComponent component) {
        this.component = component;
        ec = component.expressionCalculator;
        reloadResComponent_();
    }

    /**
     * 组件转到渲染器
     *
     * @param component name
     */
    public static AbstractComponentRenderer toRenderer(UISComponent component, LayerCanvasPane layerCanvasPane) {
        AbstractComponentRenderer r;
        if (component.getName().startsWith("_")) {
            r = switch (component.getInt("type", 0)) {
                case 0 -> new ImageComponentRenderer(component);
                case 1 -> new TextComponentRenderer(component);
                case 2 -> new RectangleComponentRenderer(component);
                case 3 -> new FrameAnimationComponentRenderer(component);
                case 4 -> new Scale3ComponentRender(component);
                default -> null;
            };

        } else if (component.getName().startsWith(":")) {
            r = new AnimationComponentRenderer(component);
        } else {
            r = switch (component.getName()) {
                case "hit-fast", "hit-slow" -> new FrameAnimationComponentRenderer(component);
                case "note" -> new NoteComponentRenderer(component);
                case "key" -> new KeyComponentRenderer(component);
                case "hit" -> new HitComponentRenderer(component);
                case "press" -> new PressComponentRenderer(component);
                case "judge" -> new JudgeComponentRenderer(component);
                case "pause" -> new ImageComponentRenderer(component);
                case "bar" -> new BarComponentRender(component);
                case "touch" -> new TouchComponentRenderer(component);
                case "score-combo", "score-score", "score-acc", "score-maxcombo" ->
                        new ScoreComponentRenderer(component);
                case "score-hp", "progress" -> new ProgressComponentRenderer(component);
                default -> null;
            };
        }
        if (r != null) {
            r.initialize(layerCanvasPane.getCanvas(r.getLayoutName()));
        }
        return r;
    }

    /**
     * top 3d bottom
     *
     * @return
     */
    /*public String getLayoutName() {
        return (getZindex() < 1 ? "bottom" : (getZindex() < 99 ? "3d" : "top"));
    }*/
    public final UISComponent getComponent() {
        return component;
    }

    /**
     * 是否是3d图层
     *
     * @return 是否受到3d
     */
    public boolean is3DLayout() {
        return getZindex() >= MuiRules.PERSPECTIVE_LAYER_FIRST
                && getZindex() < MuiRules.PERSPECTIVE_LAYER_END;
    }

    public String getLayoutName() {
        return "view";
    }

    public UISComponent getMotion() {
        return motion;
    }

    public final String getName() {
        return name;
    }

    public final int getZindex() {
        return zindex;
    }

    private void reloadResComponent_() {
        blend = component.getInt("blend", 0);
        texSize = component.getExpressionVector("___texSize");
        name = component.getName();
        tex = component.getImageOrNull("tex");
        if (tex != null) {
            texSize.setW(tex.getWidth());
            texSize.setH(tex.getHeight());
        }

        if (component.contains("color")) {
            color = Color.web(component.getString("color", "#00000000"));
            shadow = new Shadow(BlurType.ONE_PASS_BOX, color, 0);
            shadow.setColor(color);
        }
        reloadStyle();

        type = component.getInt("type", 0);
        index = component.getIndex();

        //rr.setFlip(flip);
        motion = component.getSkin().getComponent(":" + component.getString("motion", "notfound"));
        //parent=component.
        reloadStyle();
        reloadPosComponent_();
        reloadResComponent();
    }

    abstract void reloadResComponent();

    public void reloadPos() {
        reloadPosComponent_();
    }

    public final void reloadStyle() {
        pixelMagnification = component.expressionCalculator.getPixelMagnification();
       /* pos = component.getExpressionVector("pos");
        size = component.getExpressionVector("size");*/

        rotate = component.getDouble("rotate", 0);
        opacity = component.getInt("opacity", MuiRules.FULL_OPACITY) / (double) MuiRules.FULL_OPACITY;
        scale = component.getExpressionVector("scale", "1px,1px");
        skew = component.getExpressionVector("skew");

        zindex = component.getInt("zindex", zindex);
        hide = false;
    }

    private void reloadPosComponent_() {
        pixelMagnification = component.expressionCalculator.getPixelMagnification();
        pos = component.getExpressionVector("pos");
        size = component.getExpressionVector("size");

        flip = component.getOrientation("flip");
        anchor = component.getAnchorPos("anchor");

        double proportionalWidth = size.getW();
        double proportionalHeight = size.getH();


        // 根据 高度\宽度 计算比例尺寸
        if (proportionalWidth == 0) {
            proportionalWidth = proportionalHeight * (texSize.getW() / texSize.getH());
        } else if (proportionalHeight == 0) {
            proportionalHeight = proportionalWidth * (texSize.getH() / texSize.getW());
        }

        proportionalWidth *= scale.getW();
        proportionalHeight *= scale.getH();
        size.setW(proportionalWidth);
        size.setH(proportionalHeight);

        reloadPosComponent();
    }

    abstract void reloadPosComponent();

    @Override
    final public void draw(GraphicsContext gc, double width, double height, long time) {
        //检查是否被改动过，如果被改动过，重新加载元件信息
        if (component.isChanged()) {
            reloadResComponent_();
        }
        this.gc = gc;


        /*rr.setSize(Pos.TOP_LEFT, proportionalWidth, proportionalHeight);
        rr.setPos(Pos.TOP_LEFT, pos.getX(), pos.getY());*/

        gc.save();

        if (color != null) {
            gc.setEffect(shadow);
        }

        switch (blend) {
            case 0 -> {
                gc.setGlobalBlendMode(BlendMode.SRC_OVER);
            }
            case 1 -> {
                gc.setGlobalBlendMode(BlendMode.ADD);
            }
            case 2 -> {
                gc.setGlobalBlendMode(BlendMode.SCREEN);
            }
        }

        gc.setGlobalAlpha(opacity);


        //transform();
        if (!hide)
            drawComponent(width, height, time);
        if (color != null) {
            gc.setEffect(null);
        }

        gc.restore();
    }

    protected void drawImage(Image tex) {
        drawImage(tex, pos.getX(), pos.getY(), size.getW(), size.getH());
    }

    protected void drawImage(Image tex, RenderRectangle rr) {
        drawImage(tex, rr.getLeft(), rr.getTop(), rr.getWidth(), rr.getHeight());
    }

    /**
     * @param tex 材质
     * @param ulx 左上角X
     * @param uly 左上角Y
     * @param urx 右上角X
     * @param ury 右上角Y
     * @param lrx 右下角X
     * @param lry 右下角X
     * @param llx 左下角X
     * @param lly 左下角Y
     */
    protected void drawImage(Image tex, double ulx, double uly, double urx, double ury, double lrx, double lry, double llx, double lly) {

        //进行基本变换
        Point2D ul = affine.transform(ulx, uly);
        Point2D ur = affine.transform(urx, ury);
        Point2D lr = affine.transform(lrx, lry);
        Point2D ll = affine.transform(llx, lly);


        //进行3d变换
        if (is3DLayout()) {
            ul = ec.transform(ul);
            ur = ec.transform(ur);
            lr = ec.transform(lr);
            ll = ec.transform(ll);
        }

        // 添加坐标检查
        if (Double.isNaN(ul.getX()) || Double.isNaN(ul.getY()) ||
                Double.isNaN(ur.getX()) || Double.isNaN(ur.getY()) ||
                Double.isNaN(lr.getX()) || Double.isNaN(lr.getY()) ||
                Double.isNaN(ll.getX()) || Double.isNaN(ll.getY())) {
            return;
        }


        //将坐标应用到变换
        pt.setUlx(ul.getX());
        pt.setUly(ul.getY());
        pt.setUrx(ur.getX());
        pt.setUry(ur.getY());
        pt.setLrx(lr.getX());
        pt.setLry(lr.getY());
        pt.setLlx(ll.getX());
        pt.setLly(ll.getY());


        gc.save();
        gc.setEffect(pt);
        gc.drawImage(tex, 0, 0, tex.getWidth(), tex.getHeight());
        //gc.setEffect(null);
        gc.restore();
    }

    protected void drawImageA(Image tex, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh) {
        if (tex == null || tex.isError())
            return;

        // 创建一个剪辑区域
        Rectangle clipRect = new Rectangle(dx, dy, dw, dh);
        gc.save();
        gc.beginPath();
        gc.rect(clipRect.getX(), clipRect.getY(), clipRect.getWidth(), clipRect.getHeight());
        gc.clip();

        // 计算缩放比例
        double scaleX = dw / sw;
        double scaleY = dh / sh;

        // 计算目标位置
        double targetX = dx - sx * scaleX;
        double targetY = dy - sy * scaleY;

        // 绘制图像
        gc.drawImage(tex, targetX, targetY, tex.getWidth() * scaleX, tex.getHeight() * scaleY);

        // 恢复到之前的状态
        gc.restore();
    }

    protected void drawImage(Image tex, double x, double y, double w, double h) {
        if (tex == null || tex.isError())
            return;

        drawImage(tex, x, y, x + w, y, x + w, y + h, x, y + h);
    }

    public void transform() {
        affine = new Affine();
        double anchorX = switch (anchor.getHpos()) {
            case LEFT -> 0;
            case CENTER -> size.getW() / 2;
            case RIGHT -> size.getW();
        };

        double anchorY = switch (anchor.getVpos()) {
            case TOP -> 0;
            case CENTER, BASELINE -> size.getH() / 2;
            case BOTTOM -> size.getH();
        };


        // 旋转变换
        if (rotate != 0) {
            //affine.translate(pos.getX(), pos.getY());
            affine.appendRotation(rotate, pos.getX(), pos.getY());
            //affine.translate(-pos.getX(), -pos.getY());
        }

        //斜切变换
        if (skew.getW() != 0 || skew.getH() != 0) {
            //计算比例
            double texWidth = texSize.getW();
            double texHeight = texSize.getH();
            double renderWidth = size.getW();
            double renderHeight = size.getH();

            // 计算宽高比
            double textureAspectRatio = texWidth / texHeight;
            double renderAspectRatio = renderWidth / renderHeight;

            // 斜切角度 shear是倾斜度数
            double shearX = skew.getH();
            double shearY = skew.getW();

            {
                // 斜切换算
                shearX = Math.tan(-Math.toRadians(shearX));
                shearY = Math.tan(-Math.toRadians(shearY));

                // 根据宽高比调整斜切
                shearX *= textureAspectRatio / renderAspectRatio;
                shearY *= renderAspectRatio / textureAspectRatio;

                //补偿
                double x = pos.getY() * shearY;
                double y = pos.getX() * shearX;

                affine.appendShear(shearY, shearX, new Point2D(pos.getX(), pos.getY()));
            }
        }

        //gc.setFill(Color.LIGHTGREEN);
        //gc.fillRect(pos.getX() - 1, pos.getY() - 1, 3, 3);

        //锚点
        //affine.translate(-anchorX, -anchorY);
        affine.appendTranslation(-anchorX, -anchorY);

        //翻转
        if (flip == Orientation.VERTICAL) {
            affine.appendScale(1, -1);  // 垂直翻转
            affine.appendTranslation(0, -pos.getY() * 2 - size.getH());
        } else if (flip == Orientation.HORIZONTAL) {
            affine.appendScale(-1, 1);  // 水平翻转
            affine.appendTranslation(-pos.getX() * 2 - size.getW(), 0);
        }
    }

    /**
     * 绘制组件
     *
     * @param width  画布宽度
     * @param height 画布高度
     */
    abstract void drawComponent(double width, double height, long time);


}
