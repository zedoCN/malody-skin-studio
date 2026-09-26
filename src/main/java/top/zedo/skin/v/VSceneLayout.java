package top.zedo.skin.v;

import top.zedo.skin.v.proto.SkinVProto.SkinFile;

/** Static image projection based on Emiria's SkinInstanceFactory.ApplyBasicParam/ApplyImageSize. */
final class VSceneLayout {
    static final int CUSTOM_IMAGE = 5000;

    record Placement(double left, double top, double width, double height,
                     double pivotX, double pivotY, int rotate, double opacity) { }

    private VSceneLayout() { }

    static boolean supports(SkinFile.Module module) {
        if (module.getType() != CUSTOM_IMAGE || module.getUsage() != 99
                || !module.hasImage() || module.getMeta().getDisabled()
                || module.getScenesCount() != 0 || module.getTriggersCount() != 0
                || module.getAnimationsCount() != 0 || module.getParam().getAnchorNote()
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

    static Placement project(SkinFile.Module module, double canvasWidth, double canvasHeight,
                             double imageWidth, double imageHeight) {
        if (!supports(module) || canvasWidth <= 0 || canvasHeight <= 0
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
