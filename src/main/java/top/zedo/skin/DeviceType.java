package top.zedo.skin;

public enum DeviceType {
    MAC("macOS"), WINDOWS("Windows"), IOS("iOS"), ANDROID("Android");
    final String name;

    DeviceType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
