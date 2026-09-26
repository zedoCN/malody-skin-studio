package top.zedo.skin.uis;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import top.zedo.zxncore.ZXLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UISComponent {
    private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z_][A-Za-z_0-9]*)}");
    public static final Image UNKNOWN;

    static {
        ZXLogger.info("载入基本资源");
        try (InputStream unknownStream = UISComponent.class.getResourceAsStream("/base/unknown.png");) {
            assert unknownStream != null;
            UNKNOWN = new Image(unknownStream);
        } catch (IOException e) {
            ZXLogger.severe("载入基本资源发生异常");
            throw new RuntimeException(e);
        }
    }

    public void setChanged() {
        this.changed = true;
    }

    public final ExpressionCalculator expressionCalculator;
    public final UISSkin uisSkin;
    /**
     * 资源映射表
     */
    private final Map<String, Path> imageMap;
    /**
     * 属性索引
     */
    private final HashMap<String, Integer> propertiesIndex = new HashMap<>();
    private String name;
    private final String fullName;
    private final HashMap<String, String> properties = new HashMap<>();
    private final HashMap<String, MuiSourceLocation> propertySources = new HashMap<>();
    private final Set<String> animations = new HashSet<>();
    private int index = 0;
    private boolean changed = false;
    private boolean isAnimation = false;

    /**
     * 获取动画
     *
     * @return 动画
     */
    public Set<String> getAnimations() {
        return animations;
    }

    public void copyFrom(UISComponent component) {
        if (component == null) {
            ZXLogger.warning("尝试从null复制: " + this);
            return;
        }
        index = component.index;
        properties.clear();
        animations.clear();
        propertiesIndex.clear();
        propertySources.clear();
        properties.putAll(component.properties);
        animations.addAll(component.animations);
        propertiesIndex.putAll(component.propertiesIndex);
        propertySources.putAll(component.propertySources);
        changed = true;
    }

    public boolean isAnimation() {
        return isAnimation;
    }

    public UISComponent(String fullName, Map<String, Path> imageMap, UISSkin uisSkin) {
        this.imageMap = imageMap;

        this.fullName = fullName;
        this.name = this.fullName;

        this.uisSkin = uisSkin;
        this.expressionCalculator = uisSkin.getExpressionCalculator();
        try {
            index = Integer.parseInt(fullName.substring(fullName.indexOf("-") + 1)) - 1;
            this.name = fullName.substring(0, fullName.indexOf("-"));
        } catch (NumberFormatException ignored) {
        }
        isAnimation = fullName.startsWith(":");

    }

    @Override
    public String toString() {
        return "UISComponent{" +
                "name='" + name + '\'' +
                "fullName='" + fullName + '\'' +
                ", index=" + index +
                (isAnimation ? ", animations=" + animations : ", properties=" + properties) +
                '}';
    }

    /**
     * 寻找属性
     *
     * @param name 属性名 (匹配开头)
     * @return 找到的属性名
     */
    public List<String> findProperties(String name) {
        return properties.keySet().stream().filter(s -> s.startsWith(name)).collect(Collectors.toList());
    }


    /**
     * 设置属性
     *
     * @param name  属性名
     * @param value 属性值 如果是null为移除属性
     */
    public void putProperty(String name, String value, int index) {
        putProperty(name, value, index, null);
    }

    public void putProperty(String name, String value, int index, MuiSourceLocation source) {
        properties.put(name, value.trim());
        propertiesIndex.put(name, index);
        if (source == null) propertySources.remove(name);
        else propertySources.put(name, source);
        changed = true;
    }

    public MuiSourceLocation getPropertySource(String name) {
        return propertySources.get(name);
    }

    public String getRawProperty(String name) {
        return properties.get(name);
    }

    public void putAnimation(String value) {
        animations.add(value);
        changed = true;
    }


    /**
     * 包含属性
     *
     * @param name 属性名
     * @return 是否包含
     */
    public boolean contains(String name) {
        return properties.containsKey(name);
    }

    /**
     * 如果属性被修改过，则会返回true并复位。
     *
     * @return 是否被修改过
     */
    public boolean isChanged() {
        if (changed) {
            changed = false;
            return true;
        }
        return false;
    }

    public String getString(String name, String defaultValue) {
        String value = properties.getOrDefault(name, defaultValue);
        if (value == null)
            return null;
        boolean numeric = name.startsWith("pos") || name.startsWith("size") || name.startsWith("array")
                || Set.of("rect", "fsize", "part", "rotate", "skew", "scale", "toggle", "opacity", "zindex")
                .contains(name);
        return resolveVariables(value, new HashSet<>(), numeric);
    }

    private String resolveVariables(String value, Set<String> resolving, boolean numeric) {
        Matcher matcher = VARIABLE.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = uisSkin.variable.get(variableName);
            if (replacement == null) {
                replacement = "(!未定义的变量: " + variableName + "!)";
            } else if (!resolving.add(variableName)) {
                replacement = "(!循环变量: " + variableName + "!)";
            } else {
                replacement = resolveVariables(replacement, resolving, numeric);
                resolving.remove(variableName);
                if (numeric) replacement = "(" + replacement + ")";
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public UISFrame getFrame(String name) {
        return new UISFrame(this, name);
    }

    /**
     * 获取整数
     *
     * @param name         属性名
     * @param defaultValue 属性值
     * @return 整数
     */
    public int getInt(String name, int defaultValue) {
        try {
            double value = ExpressionCalculator.calculateScalar(getString(name, ""));
            if (!Double.isFinite(value) || value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                return defaultValue;
            }
            return (int) value;
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    /**
     * 获取方向
     *
     * @param name 属性名
     * @return 方向
     */
    public Orientation getOrientation(String name) {
        return switch (getInt(name, -1)) {
            case 0 -> Orientation.HORIZONTAL;
            case 1 -> Orientation.VERTICAL;
            default -> null;
        };
    }

    /**
     * 获取皮肤对象
     *
     * @return 皮肤对象
     */
    public UISSkin getSkin() {
        return uisSkin;
    }

    /**
     * 获取双精度值
     *
     * @param name         属性名
     * @param defaultValue 默认值
     * @return 双精度值小数
     */
    public double getDouble(String name, double defaultValue) {
        try {
            return ExpressionCalculator.calculateScalar(getString(name, ""));
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    /** Legacy text fsize uses the vertical size expression, then truncates to an integer. */
    public int getVerticalLength(String name, int defaultValue) {
        String value = getString(name, null);
        if (value == null) return defaultValue;
        try {
            double pixels = expressionCalculator.calculateY(value);
            return Double.isFinite(pixels) && pixels >= Integer.MIN_VALUE && pixels <= Integer.MAX_VALUE
                    ? (int) pixels : defaultValue;
        } catch (IllegalArgumentException error) {
            return defaultValue;
        }
    }

    /**
     * 获取路径
     *
     * @param path 路径
     * @return 路径 可能有null
     */
    public Path getPath(String path) {
        return imageMap.get(path);
    }

    /**
     * 获取图片
     *
     * @param name 属性名
     * @return 图片 没找到返回UNKNOWN
     */
    public Image getImage(String name) {
        String str = getString(name, null);
        if (str == null) {
            ZXLogger.warning(getFullName() + " 没有属性 " + name);
            return UNKNOWN;
        }
        return getImageFromPath(str);
    }

    /**
     * 获取图片 可以是null
     *
     * @param name 属性名
     * @return 图片 没找到返回null
     */
    public Image getImageOrNull(String name) {
        String str = getString(name, null);
        if (str == null) {
            return null;
        }
        return getImageFromPath(str);
    }

    /**
     * 获取图片
     *
     * @param str 路径名
     * @return 图片 没找到返回UNKNOWN
     */
    public Image getImageFromPath(String str) {
        Path path = getPath(str);
        if (path == null) {
            ZXLogger.warning(getFullName() + " 未找到资源 " + str);
            return UNKNOWN;
        }
        try (InputStream stream = Files.newInputStream(path)) {
            return new Image(stream);
        } catch (IOException e) {
            ZXLogger.warning(getFullName() + " 无法载入图片 " + path);
        }
        return UNKNOWN;
    }

    /**
     * 获取锚点位置 默认为 TOP_LEFT
     *
     * @param name 属性名
     * @return 锚点位置
     */
    public Pos getAnchorPos(String name) {
        return switch (getInt(name, 0)) {
            case 1 -> Pos.TOP_CENTER;
            case 2 -> Pos.TOP_RIGHT;
            case 3 -> Pos.CENTER_LEFT;
            case 4 -> Pos.CENTER;
            case 5 -> Pos.CENTER_RIGHT;
            case 6 -> Pos.BOTTOM_LEFT;
            case 7 -> Pos.BOTTOM_CENTER;
            case 8 -> Pos.BOTTOM_RIGHT;
            default -> Pos.TOP_LEFT;
        };
    }

    public MuiPixelAnchor getPixelAnchor() {
        return MuiPixelAnchor.parse(getRawProperty("anchor"));
    }

    /**
     * 获取图片序列
     *
     * @param name 属性名
     * @return 图片序列
     */
    public List<Image> getImageList(String name) {
        String frameInfo = getString(name, null);
        if (frameInfo == null)
            return new ArrayList<>();

        int delimiter = frameInfo.lastIndexOf("/");
        if (delimiter <= 0) {
            ZXLogger.warning("Invalid image list: " + frameInfo + "  --  " + this);
            return new ArrayList<>();
        }

        String path = frameInfo.substring(0, delimiter);
        String[] indexes = frameInfo.substring(delimiter + 1).split("-");
        int from = Integer.parseInt(indexes[0]);
        int to = Integer.parseInt(indexes[1]);
        List<Image> images = new ArrayList<>(to - from + 1);
        for (int i = from; i < to + 1; i++) {
            images.add(getImageFromPath(path + "-" + i + ".png"));
        }
        return images;
    }

    /**
     * 获取表达式向量
     *
     * @param name 属性名
     * @return 表达式向量
     */
    public ExpressionVector getExpressionVector(String name) {
        String value = getString(name, "0,0");
        //检查父组件位置
        int index = value.indexOf(" ");
        if (index != -1) {
            String parentName = value.substring(0, value.indexOf(" "));
            UISComponent parent = uisSkin.getComponent(parentName);
            if (parent != null)
                return new ExpressionVector(expressionCalculator, value.substring(value.indexOf(" ") + 1), propertiesIndex.getOrDefault(name, 0), parent.getExpressionVector(name));
        }
        return new ExpressionVector(expressionCalculator, value, propertiesIndex.getOrDefault(name, 0));
    }

    public boolean hasPositionParent() {
        String value = getString("pos", "");
        int separator = value.indexOf(' ');
        return separator > 0 && uisSkin.getComponent(value.substring(0, separator)) != null;
    }

    /** A UIS rect is x, y, width, height; its axes use the same units as pos/size. */
    public double[] getRectangle(String name) {
        String value = getString(name, null);
        if (value == null) return null;
        String[] parts = value.split(",", -1);
        if (parts.length != 4) throw new IllegalArgumentException("无效的 UIS 矩形: " + value);
        return new double[]{
                expressionCalculator.calculateX(parts[0].trim()),
                expressionCalculator.calculateY(parts[1].trim()),
                expressionCalculator.calculateX(parts[2].trim()),
                expressionCalculator.calculateY(parts[3].trim())
        };
    }
    /**
     * 获取表达式向量
     *
     * @param name 属性名
     * @return 表达式向量
     */
    public ExpressionVector getExpressionVector(String name,String defaultValue) {
        String value = getString(name, defaultValue);
        //检查父组件位置
        int index = value.indexOf(" ");
        if (index != -1) {
            String parentName = value.substring(0, value.indexOf(" "));
            UISComponent parent = uisSkin.getComponent(parentName);
            if (parent != null)
                return new ExpressionVector(expressionCalculator, value.substring(value.indexOf(" ") + 1), propertiesIndex.getOrDefault(name, 0), parent.getExpressionVector(name));
        }
        return new ExpressionVector(expressionCalculator, value, propertiesIndex.getOrDefault(name, 0));
    }


    /**
     * 获取原件名
     *
     * @return 原件名 如'_sprite'
     */
    public String getName() {
        return name;
    }

    /**
     * 获取原件全名
     *
     * @return 原件名 如'_sprite-1'
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * 获取原件索引
     *
     * @return 索引 如'_sprite-1' 索引为 0
     */
    public int getIndex() {
        return index;
    }


    boolean hasSameContent(UISComponent other) {
        return other != null
                && index == other.index
                && Objects.equals(name, other.name)
                && Objects.equals(fullName, other.fullName)
                && properties.equals(other.properties)
                && propertiesIndex.equals(other.propertiesIndex)
                && propertySources.equals(other.propertySources)
                && animations.equals(other.animations);
    }
}
