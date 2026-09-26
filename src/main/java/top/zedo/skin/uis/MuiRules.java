package top.zedo.skin.uis;

/** Numeric conventions used by the legacy MUI preview. */
public final class MuiRules {
    private MuiRules() { }

    // @unit defaults to 720 in Emiria's UISPassNumber and in the sampled 4.3.7 skins.
    public static final int DEFAULT_UNIT_HEIGHT = 720;
    // 4.3.7 device: @apply 3d with @angle 0 matches the editor at 30 degrees.
    public static final int DEFAULT_3D_ANGLE = 30;
    // The historical UIS width unit "w" uses Screen.width / 1280.
    public static final int WIDTH_UNIT_BASE = 1280;
    // The sampled 4.x skin corpus uses 0..100 for opacity; 5.0's prototype uses 0..255.
    public static final int FULL_OPACITY = 100;
    /** 4.3.7 converts percentage opacity to an 8-bit channel before drawing. */
    public static double opacityFromPercent(double percent) {
        return (((int) (float) (percent * 2.55)) & 0xff) / 255.0;
    }
    // Existing editor preview convention; 4.3.7 runtime equivalence still needs visual proof.
    public static final int PERSPECTIVE_LAYER_FIRST = 1;
    public static final int PERSPECTIVE_LAYER_END = 99;
}
