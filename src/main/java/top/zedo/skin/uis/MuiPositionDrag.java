package top.zedo.skin.uis;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Adds one device-pixel displacement while retaining a MUI position expression. */
public final class MuiPositionDrag {
    private MuiPositionDrag() { }

    public static String translate(String position, double dx, double dy) {
        if (position == null || !Double.isFinite(dx) || !Double.isFinite(dy)) {
            throw new IllegalArgumentException("无效的拖拽位移");
        }
        String[] axes = position.split(",", -1);
        if (axes.length != 2 || axes[0].isBlank() || axes[1].isBlank()
                || position.indexOf('$') >= 0 || position.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("当前 pos 表达式不支持直接拖拽: " + position);
        }
        // MUI's Y values are measured upward from the canvas bottom.
        return offset(axes[0].trim(), dx) + "," + offset(axes[1].trim(), -dy);
    }

    private static String offset(String expression, double pixels) {
        BigDecimal rounded = BigDecimal.valueOf(pixels).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (rounded.signum() == 0) return expression;
        String amount = rounded.abs().toPlainString();
        return expression + (rounded.signum() > 0 ? "+" : "-") + amount + "px";
    }
}
