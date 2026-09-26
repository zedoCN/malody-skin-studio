package top.zedo.skin.uis;

import top.zedo.skin.DeviceType;
import top.zedo.skin.ResolutionInfo;

/** One reproducible viewport shared by snapshot and component trace commands. */
record SkinRenderProfile(DeviceType device, double aspectRatio, double outputHeight) {
    static SkinRenderProfile parse(String value) {
        String profile = value.toUpperCase(java.util.Locale.ROOT);
        if (profile.startsWith("ANDROID:")) {
            String[] dimensions = profile.substring("ANDROID:".length()).split("X", -1);
            if (dimensions.length != 2) throw new IllegalArgumentException("设备尺寸应为 ANDROID:宽x高");
            double width = Double.parseDouble(dimensions[0]);
            double height = Double.parseDouble(dimensions[1]);
            if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) {
                throw new IllegalArgumentException("设备宽高必须是正数");
            }
            return new SkinRenderProfile(DeviceType.ANDROID, width / height, height);
        }
        ResolutionInfo resolution = ResolutionInfo.valueOf(profile);
        return new SkinRenderProfile(resolution.getDevice(), resolution.getAspectRatio(), 0);
    }

    void apply(UISCanvas canvas) throws java.io.IOException {
        canvas.setDeviceType(device);
        canvas.setAspectRatio(aspectRatio);
    }

    void applyOutputHeight(UISCanvas canvas) throws java.io.IOException {
        if (outputHeight > 0) canvas.setZoomRate(outputHeight / canvas.skin.unit);
    }
}
