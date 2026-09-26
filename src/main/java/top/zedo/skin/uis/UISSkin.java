package top.zedo.skin.uis;

import top.zedo.skin.DeviceType;
import top.zedo.skin.uis.component.AbstractComponentRenderer;
import top.zedo.ui.component.LayerCanvasPane;
import top.zedo.zxncore.ZXLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UISSkin {
    /**
     * 变量映射表 引用会是'{test}'
     * <p>
     * 默认包含以下值
     * {title}: 表示歌曲名
     * {artist}: 表示歌曲艺术家
     * {creator}: 表示谱面作者名
     * {version}: 谱面难度名
     */
    protected final HashMap<String, String> variable = new HashMap<>();
    private final ExpressionCalculator expressionCalculator;
    /**
     * 资源映射表
     */
    private final HashMap<String, Path> imagePathMap = new HashMap<>();


    /**
     * 组件表
     */
    private final HashMap<String, UISComponent> componentMap = new HashMap<>();
    /**
     * 基本路径
     */
    private final Path basePath;
    /**
     * 主mui路径
     */
    private final Path muiPath;
    /**
     * 3d角度
     */
    int angle = 0;
    /**
     * 单位 默认720
     */
    int unit = MuiRules.DEFAULT_UNIT_HEIGHT;

    DeviceType deviceType = DeviceType.WINDOWS;


    /**
     * 通过mui文件 解析到皮肤
     *
     * @param muiPath mui文件
     */
    public UISSkin(Path muiPath, ExpressionCalculator expressionCalculator) {
        this.expressionCalculator = expressionCalculator;
        resetVariables();

        this.muiPath = muiPath;
        basePath = muiPath.getParent();
    }

    /**
     * 排序渲染器 按照zindex排序
     *
     * @param renderers 渲染器
     */
    public static void sortRenders(List<AbstractComponentRenderer> renderers) {
        renderers.sort(Comparator.comparingInt(AbstractComponentRenderer::getZindex));
    }

    public int getAngle() {
        return angle;
    }

    /**
     * 设置设备类型 会影响到include判断
     *
     * @param deviceType 设备类型
     */
    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }

    @Override
    public String toString() {


        return "UISSkin {" +
                "calculator=" + expressionCalculator +
                ", imageMap=" + imagePathMap.size() +
                ", componentMap=" + componentMap.size() +
                ", angle=" + angle +
                ", unit=" + unit +
                ", variable=" + variable +
                ", basePath=" + basePath +
                '}';
    }

    /**
     * 获取表达式计算器
     *
     * @return 表达式计算器
     */
    public ExpressionCalculator getExpressionCalculator() {
        return expressionCalculator;
    }

    /**
     * 获取主MUI路径
     *
     * @return MUI路径
     */
    public Path getMuiPath() {
        return muiPath;
    }

    /**
     * 更新渲染器
     */
    public boolean updateRenderer(List<AbstractComponentRenderer> renderers, Map<UISComponent, AbstractComponentRenderer> currentComponentMap, LayerCanvasPane layerCanvasPane) throws IOException {
        boolean isChanged = false;
        MuiSkinLoader.Result loaded;
        HashMap<String, String> previousVariables = new HashMap<>(variable);
        HashMap<String, Path> previousImages = new HashMap<>(imagePathMap);
        try {
            resetVariables();
            imagePathMap.clear();
            loaded = new MuiSkinLoader(this, imagePathMap).load(muiPath);
            angle = loaded.angle();
            unit = loaded.unit();
            expressionCalculator.setUnitCanvasHeight(unit);
            ZXLogger.info("更新渲染器");
        } catch (IOException e) {
            variable.clear();
            variable.putAll(previousVariables);
            imagePathMap.clear();
            imagePathMap.putAll(previousImages);
            throw e;
        }
        HashMap<String, UISComponent> newComponentMap = loaded.components();
        expressionCalculator.setAngle(angle);
        //检查多余的组件
        List<String> needRemoval = new ArrayList<>();
        for (String name : componentMap.keySet()) {
            if (!newComponentMap.containsKey(name)) {
                needRemoval.add(name);
                ZXLogger.info("移除组件：" + name);
            }
        }
        //移除多余组件
        for (String name : needRemoval) {
            UISComponent component = componentMap.get(name);
            AbstractComponentRenderer renderer = currentComponentMap.get(component);
            renderers.remove(renderer);
            currentComponentMap.remove(component);
            componentMap.remove(name);
            if (component.isAnimation())
                isChanged = true;
        }

        // A custom component's type selects its renderer class at construction.
        // Replacing only its properties would leave the old class drawing the new type.
        for (UISComponent component : newComponentMap.values()) {
            UISComponent previous = componentMap.get(component.getFullName());
            if (previous != null && !rendererKindChanged(previous, component)) continue;
            if (previous != null) {
                renderers.remove(currentComponentMap.remove(previous));
                componentMap.remove(previous.getFullName());
            }
            AbstractComponentRenderer renderer = AbstractComponentRenderer.toRenderer(component, layerCanvasPane);
            if (renderer == null) {
                ZXLogger.warning("不支持的组件: " + component.getFullName() + "   " + component);
                continue;
            }
            renderers.add(renderer);
            currentComponentMap.put(component, renderer);
            componentMap.put(component.getFullName(), component);
            if (component.isAnimation()) isChanged = true;
        }

        //更新其余组件属性
        for (UISComponent component : componentMap.values()) {
            UISComponent newComponent = newComponentMap.get(component.getFullName());
            if (!component.hasSameContent(newComponent)) {
                component.copyFrom(newComponent);
                if (component.isAnimation())
                    isChanged = true;
            }
        }

        return isChanged;
    }

    private static boolean rendererKindChanged(UISComponent previous, UISComponent current) {
        return previous.getName().startsWith("_")
                && previous.getInt("type", 0) != current.getInt("type", 0);
    }

    private void resetVariables() {
        variable.clear();
        variable.put("title", "{ 歌曲名 }");
        variable.put("artist", "{ 歌曲艺术家 }");
        variable.put("creator", "{ 谱面作者名 }");
        variable.put("version", "{ 谱面难度名 }");
        variable.put("player", "{ 玩家ID }");
        variable.put("bpm", "{ BPM }");
    }

    /**
     * 获取组件
     *
     * @param name 组件名
     * @return 组件
     */
    public UISComponent getComponent(String name) {
        return componentMap.get(name);
    }

    /**
     * 获取所有组件
     *
     * @return 所有组件
     */
    public Collection<UISComponent> getComponents() {
        return componentMap.values();
    }


}
