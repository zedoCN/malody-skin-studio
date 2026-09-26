package top.zedo.skin.uis;

import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.scene.canvas.GraphicsContext;
import top.zedo.skin.DeviceType;
import top.zedo.skin.uis.component.AbstractComponentRenderer;
import top.zedo.skin.uis.component.AnimationComponentRenderer;
import top.zedo.skin.uis.component.MeasuringRulerRenderer;
import top.zedo.ui.component.LayerCanvasPane;
import top.zedo.zxncore.ZXLogger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

public class UISCanvas extends LayerCanvasPane {
    //暂停状态
    public boolean isPaused = false;
    public LongProperty time = new SimpleLongProperty();
    UISSkin skin;
    //UISPerspectiveTransform perspective = new UISPerspectiveTransform();
    ExpressionCalculator expressionCalculator = new ExpressionCalculator();
    public MeasuringRulerRenderer measureRuler = new MeasuringRulerRenderer(expressionCalculator);
    ArrayList<AbstractComponentRenderer> componentRenders = new ArrayList<>();
    Map<UISComponent, AbstractComponentRenderer> componentMap = new IdentityHashMap<>();
    DeviceType deviceType;
    double aspectRatio = 1;
    double zoomRate = 1;
    //开始播放时的时间戳
    private long startTime = 0;
    //暂停时的时间位置
    private long pauseTime = 0;

    public UISCanvas() {
        /*createCanvas("bottom");
        createCanvas("3d");//.setEffect(perspective.getEffect());
        createCanvas("top");*/
        createCanvas("view");
        measureRuler.initialize(createCanvas("mark"));
    }


    public void draw() {
        double width = expressionCalculator.getCanvasWidth();
        double height = expressionCalculator.getCanvasHeight();
        long currentTime = (isPaused ? pauseTime : System.currentTimeMillis() - startTime);
        time.setValue(currentTime);
        /*if (!isPaused) {
            if (!timeLineSlider.isValueChanging())
                timeLineSlider.setValue(currentTime / 1000.);
        }
        timeLabel.setText(currentTime + " ms");*/

        clearRect();

        /*GraphicsContext gc3d = getGraphicsContext2D("3d");
        gc3d.save();
        gc3d.beginPath();
        gc3d.rect(-width, 0, width * 4, height);
        gc3d.clip();*/

        for (AbstractComponentRenderer cr : componentRenders) {
            GraphicsContext gc = getGraphicsContext2D(cr.getLayoutName());
            gc.save();
            try {
                AbstractComponentRenderer anim = componentMap.get(cr.getMotion());
                if (anim instanceof AnimationComponentRenderer renderer) {
                    renderer.update(gc, width, height, cr, currentTime);
                }
                cr.draw(gc, width, height, currentTime);
            } catch (Exception e) {
                ZXLogger.warning("绘制组件: " + cr.getComponent() + " 发生异常: " + e.getMessage());
                cr.hide = true;
            } finally {
                gc.restore();
            }
        }

        {
            GraphicsContext gc = getGraphicsContext2D("mark");
            measureRuler.draw(gc, width, height, currentTime);
        }
        /*gc3d.restore();
        gc3d.restore();
        gc3d.restore();
        gc3d.restore();*/
    }

    public void play() {
        if (isPaused) {
            isPaused = false;
            startTime = System.currentTimeMillis() - pauseTime;
        }
    }

    public void pause() {
        if (!isPaused) {
            isPaused = true;
            pauseTime = System.currentTimeMillis() - startTime;
        }
    }

    public void setTime(long currentTime) {
        if (isPaused) {
            pauseTime = currentTime;
        } else {
            startTime = System.currentTimeMillis() - currentTime;
        }
    }

    public void updateSkin() {
        ZXLogger.info("更新皮肤");
        if (skin == null)
            return;

        // Includes and @if depend on the target aspect ratio during parsing.
        configureCanvasSize();

        /*updateCanvasSize();
        skin.setDeviceType(deviceTypeChoiceBox.getValue());*/
        //updateCanvasSize();


        skin.setDeviceType(deviceType);

        skin.updateRenderer(componentRenders, componentMap, this);
        //UISSkin.sortRenders(componentRenders);

        expressionCalculator.setAngle(skin.getAngle());

        /*if (autoReplayCheckBox.isSelected() & skin.updateRenderer(componentRenders, componentMap, this))
            resetTime();*/

        configureCanvasSize();


        for (AbstractComponentRenderer component : componentMap.values()) {
            component.reloadPos();
            component.reloadStyle();
        }
        UISSkin.sortRenders(componentRenders);

        //resetTime();
    }

    private void configureCanvasSize() {
        double unitHeight = skin.unit;
        double width = unitHeight * aspectRatio * zoomRate;
        double height = unitHeight * zoomRate;
        setMinSize(width, height);
        setMaxSize(width, height);
        expressionCalculator.setCanvasSize(width, height);
        expressionCalculator.setUnitCanvasHeight(unitHeight);
    }

    public void setDeviceType(DeviceType deviceType) {
        ZXLogger.info("设置设备类型: " + deviceType);
        if (this.deviceType == deviceType) return;
        this.deviceType = deviceType;
        if (skin != null) updateSkin();
    }

    public void loadSkin(Path uisPath) {
        ZXLogger.info("加载皮肤: " + uisPath);
        componentRenders.clear();
        componentMap.clear();

        skin = new UISSkin(uisPath, expressionCalculator);


        /*for (UISComponent component : skin.getComponents()) {
            AbstractComponentRenderer render = AbstractComponentRenderer.toRenderer(component, this);
            if (render != null) {
                componentRenders.add(render);
                componentMap.put(component, render);
            }

        }*/

        updateSkin();

        resetTime();
        //updateCanvasSize();
    }

    /**
     * 设置纵横比
     *
     * @param aspectRatio 纵横比
     */
    public void setAspectRatio(double aspectRatio) {
        ZXLogger.info("设置纵横比: " + aspectRatio);
        this.aspectRatio = aspectRatio;
        updateSkin();
    }

    /**
     * 设置缩放率
     *
     * @param zoomRate 缩放率
     */
    public void setZoomRate(double zoomRate) {
        ZXLogger.info("设置缩放率: " + zoomRate);
        this.zoomRate = zoomRate;
        //expressionCalculator.setUnitCanvasHeight(skin.unit * zoomRate);
        ZXLogger.info("设置缩放率后, 单位画布高度: " + expressionCalculator.getUnitCanvasHeight());
        updateSkin();
    }


    public void resetTime() {
        /*if (isPaused) {
            pauseTime = -3500;
        } else {
            startTime = System.currentTimeMillis() + 3500;
        }*/
        setTime(-3500);
    }
}
