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
                     double pivotX, double pivotY, int rotate, double opacity) { }

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
        if (module.getType() != CUSTOM_IMAGE || module.getUsage() != 99
                || !module.hasImage() || module.getMeta().getDisabled()
                || module.getTriggersCount() != 0 || module.getAnimationsCount() != 0
                || module.getParam().getAnchorNote()
                || module.getParam().getAlpha() <= 0) return false;
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

    private static boolean supportedUnit(SkinFile.ModuleParamUnit unit) {
        return unit == SkinFile.ModuleParamUnit.Percent || unit == SkinFile.ModuleParamUnit.Unit;
    }

    private static double convert(float value, SkinFile.ModuleParamUnit unit, double parent, double scale) {
        return unit == SkinFile.ModuleParamUnit.Percent ? value * parent / 100d : value * scale;
    }
}
