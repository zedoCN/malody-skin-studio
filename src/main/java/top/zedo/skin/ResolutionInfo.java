package top.zedo.skin;

public enum ResolutionInfo {
    IPAD(1.333333333333333, "ipad mini", DeviceType.IOS),
    IPAD2(1.333984375, "ipad 12.9英寸", DeviceType.IOS),
    IPAD3(1.431654676258993, "ipad 11英寸", DeviceType.IOS),
    IPAD4(1.6, "ipad mini6", DeviceType.IOS),
    PHONE(1.706666666666667, "手机", DeviceType.ANDROID),
    PHONE_LONG(2.22222222222222, "超长手机", DeviceType.ANDROID),
    PC(1.777777777777778, "电脑", DeviceType.WINDOWS);
    final double aspectRatio;
    final String name;
    final DeviceType deviceType;

    ResolutionInfo(double aspectRatio, String name, DeviceType deviceType) {
        this.aspectRatio = aspectRatio;
        this.name = name;
        this.deviceType = deviceType;
    }

    public double getAspectRatio() {
        return aspectRatio;
    }

    public String getName() {
        return name;
    }

    public DeviceType getDevice() {
        return deviceType;
    }

    @Override
    public String toString() {
        return switch (this) {
            case IPAD -> "iPad mini · 4:3";
            case IPAD2 -> "iPad 12.9″ · 4:3";
            case IPAD3 -> "iPad 11″ · 1.43:1";
            case IPAD4 -> "iPad mini 6 · 16:10";
            case PHONE -> "手机 · 1.71:1";
            case PHONE_LONG -> "长屏手机 · 20:9";
            case PC -> "电脑 · 16:9";
        };
    }
}
