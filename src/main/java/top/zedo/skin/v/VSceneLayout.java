package top.zedo.skin.v;

import top.zedo.skin.v.proto.SkinVProto.SkinFile;

/** Static image projection based on Emiria's SkinInstanceFactory.ApplyBasicParam/ApplyImageSize. */
final class VSceneLayout {
    static final int CUSTOM_IMAGE = 5000;
    static final SceneContext REFERENCE = new SceneContext(1920, 1080, Platform.WINDOWS);

    enum Platform {
        WINDOWS(1, "Windows"), IOS(2, "iOS"), ANDROID(3, "Android");

        final int nativeValue;
        final String label;

        Platform(int nativeValue, String label) {
            this.nativeValue = nativeValue;
            this.label = label;
        }

        @Override public String toString() { return label; }
    }

    record SceneContext(int width, int height, Platform platform) {
        SceneContext {
            if (width <= 0 || height <= 0 || platform == null)
                throw new IllegalArgumentException("场景视口或平台无效");
        }

        SceneContext withPlatform(Platform selected) { return new SceneContext(width, height, selected); }
    }

    enum SceneMatch { MATCH, MISMATCH, UNKNOWN }

    record Placement(double left, double top, double width, double height,
                     double pivotX, double pivotY, double rotate, double opacity) { }
    record Offsets(float dx, float dy) { }

    private VSceneLayout() { }

    static boolean supports(SkinFile.Module module) {
        return supports(module, REFERENCE);
    }

    static boolean supports(SkinFile.Module module, SceneContext context) {
        return isFullScreenLayer(module.getParam().getLayer())
                && isStaticImage(module) && sceneMatch(module, context) == SceneMatch.MATCH;
    }

    static boolean isFullScreenLayer(int layer) {
        return layer == 1 || layer == 4;
    }

    static boolean isStaticImage(SkinFile.Module module) {
        return isPreviewImageCandidate(module) && module.getAnimationsCount() == 0
                && module.getParam().getAlpha() > 0;
    }

    /** Image geometry we can project before applying any supported module animation. */
    static boolean isPreviewImageCandidate(SkinFile.Module module) {
        if (module.getType() != CUSTOM_IMAGE || module.getUsage() != 99
                || !module.hasImage() || module.getMeta().getDisabled()
                || module.getTriggersCount() != 0 || module.getParam().getAnchorNote()) return false;
        SkinFile.ModuleParamImage image = module.getImage();
        SkinFile.ModuleParam param = module.getParam();
        return !image.getFile().isBlank() && image.getRes() == 0 && image.getColor().isBlank()
                && image.getFrames() <= 1 && image.getFilebase().isBlank() && image.getSliceCount() == 0
                && !image.getFlipx() && !image.getFlipy()
                && image.getBlend() == SkinFile.ModuleBlend.None
                && (image.getWidth() != 0 || image.getHeight() != 0)
                && supportedUnit(param.getXu()) && supportedUnit(param.getYu())
                && supportedUnit(param.getDxu()) && supportedUnit(param.getDyu())
                && supportedUnit(image.getWu()) && supportedUnit(image.getHu());
    }

    /** Only viewport and platform conditions have all their inputs in a static preview. */
    static SceneMatch sceneMatch(SkinFile.Module module, SceneContext context) {
        for (SkinFile.ModuleCondition condition : module.getScenesList()) {
            double actual;
            double expected;
            boolean integer;
            switch (condition.getSource()) {
                case 3 -> { actual = context.width(); integer = true; }
                case 4 -> { actual = context.height(); integer = true; }
                case 5 -> { actual = (double) context.width() / context.height(); integer = false; }
                case 6 -> { actual = context.platform().nativeValue; integer = true; }
                default -> { return SceneMatch.UNKNOWN; }
            }
            if (integer) {
                if (condition.getValueCase() != SkinFile.ModuleCondition.ValueCase.VALINT)
                    return SceneMatch.UNKNOWN;
                expected = condition.getValint();
            } else {
                if (condition.getValueCase() != SkinFile.ModuleCondition.ValueCase.VALDBL)
                    return SceneMatch.UNKNOWN;
                expected = condition.getValdbl();
            }
            boolean matches;
            switch (condition.getFlag()) {
                case Equal -> matches = integer ? actual == expected : Math.abs(actual - expected) < 1e-4;
                case NotEqual -> matches = integer ? actual != expected : Math.abs(actual - expected) > 1e-4;
                case Large -> matches = actual > expected;
                case Less -> matches = actual < expected;
                case Occur -> matches = integer;
                default -> { return SceneMatch.UNKNOWN; }
            }
            if (!matches) return SceneMatch.MISMATCH;
        }
        return SceneMatch.MATCH;
    }

    static Placement project(SkinFile.Module module, double canvasWidth, double canvasHeight,
                             double imageWidth, double imageHeight) {
        return project(module, REFERENCE, canvasWidth, canvasHeight, imageWidth, imageHeight);
    }

    static Placement project(SkinFile.Module module, SceneContext context,
                             double canvasWidth, double canvasHeight, double imageWidth, double imageHeight) {
        if (!supports(module, context) || canvasWidth <= 0 || canvasHeight <= 0
                || imageWidth <= 0 || imageHeight <= 0) throw new IllegalArgumentException("不支持该静态图片布局");
        return projectGeometry(module, canvasWidth, canvasHeight, imageWidth, imageHeight);
    }

    static Placement projectPreview(SkinFile.Module module, SceneContext context,
                                    double canvasWidth, double canvasHeight, double imageWidth, double imageHeight) {
        if (!isFullScreenLayer(module.getParam().getLayer()) || !isPreviewImageCandidate(module)
                || sceneMatch(module, context) != SceneMatch.MATCH || canvasWidth <= 0 || canvasHeight <= 0
                || imageWidth <= 0 || imageHeight <= 0)
            throw new IllegalArgumentException("不支持该图片动画布局");
        return projectGeometry(module, canvasWidth, canvasHeight, imageWidth, imageHeight);
    }

    private static Placement projectGeometry(SkinFile.Module module, double canvasWidth, double canvasHeight,
                                             double imageWidth, double imageHeight) {
        SkinFile.ModuleParam param = module.getParam();
        SkinFile.ModuleParamImage image = module.getImage();
        double unit = canvasHeight / 1080d;
        double x = convert(param.getX(), param.getXu(), canvasWidth, unit)
                + convert(param.getDx(), param.getDxu(), canvasWidth, unit);
        double y = convert(param.getY(), param.getYu(), canvasHeight, unit)
                + convert(param.getDy(), param.getDyu(), canvasHeight, unit);
        double width = convert(image.getWidth(), image.getWu(), canvasWidth, unit);
        double height = convert(image.getHeight(), image.getHu(), canvasHeight, unit);
        if (image.getWidth() == 0) width = height * imageWidth / imageHeight;
        if (image.getHeight() == 0) height = width * imageHeight / imageWidth;
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0)
            throw new IllegalArgumentException("图片尺寸无效");
        double pivotX = switch (param.getPivot()) {
            case LeftTop, Left, LeftBottom -> 0;
            case RightTop, Right, RightBottom -> 1;
            default -> .5;
        };
        double pivotY = switch (param.getPivot()) {
            case LeftBottom, Bottom, RightBottom -> 0;
            case LeftTop, Top, RightTop -> 1;
            default -> .5;
        };
        return new Placement(x - width * pivotX,
                canvasHeight - y - height * (1 - pivotY), width, height,
                pivotX, pivotY, param.getRotate(), Math.max(0, Math.min(1, param.getAlpha() / 100d)));
    }

    /** Translate a drag on the preview canvas back to the module's offset units. */
    static Offsets movedOffsets(SkinFile.Module module, SceneContext context,
                                double canvasWidth, double canvasHeight, double deltaX, double deltaY) {
        if (!supports(module, context) || !Double.isFinite(deltaX) || !Double.isFinite(deltaY)
                || canvasWidth <= 0 || canvasHeight <= 0)
            throw new IllegalArgumentException("不支持该图片拖拽");
        SkinFile.ModuleParam param = module.getParam();
        double unit = canvasHeight / 1080d;
        double dx = param.getDx() + (param.getDxu() == SkinFile.ModuleParamUnit.Percent
                ? deltaX * 100 / canvasWidth : deltaX / unit);
        double dy = param.getDy() - (param.getDyu() == SkinFile.ModuleParamUnit.Percent
                ? deltaY * 100 / canvasHeight : deltaY / unit);
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || Math.abs(dx) > Float.MAX_VALUE
                || Math.abs(dy) > Float.MAX_VALUE) throw new IllegalArgumentException("拖动后偏移量超出范围");
        return new Offsets((float) dx, (float) dy);
    }

    private static boolean supportedUnit(SkinFile.ModuleParamUnit unit) {
        return unit == SkinFile.ModuleParamUnit.Percent || unit == SkinFile.ModuleParamUnit.Unit;
    }

    private static double convert(float value, SkinFile.ModuleParamUnit unit, double parent, double scale) {
        return unit == SkinFile.ModuleParamUnit.Percent ? value * parent / 100d : value * scale;
    }
}
