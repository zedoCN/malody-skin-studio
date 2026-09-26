package top.zedo.skin.uis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Legacy comma anchor: pixel coordinates in the unscaled, bottom-origin content. */
public record MuiPixelAnchor(int x, int y) {
    private static final Pattern INTEGER_PREFIX = Pattern.compile("^\\s*([+-]?\\d+)");

    public static MuiPixelAnchor parse(String value) {
        if (value == null || !value.contains(",")) return null;
        String[] coordinates = value.split(",", -1);
        if (coordinates.length != 2) return null;
        try {
            return new MuiPixelAnchor(atoi(coordinates[0]), atoi(coordinates[1]));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static int atoi(String value) {
        Matcher match = INTEGER_PREFIX.matcher(value);
        return match.find() ? Integer.parseInt(match.group(1)) : 0;
    }

    public double offsetX(double renderedWidth, double contentWidth) {
        return x * renderedWidth / contentWidth;
    }

    public double offsetY(double renderedHeight, double contentHeight) {
        return (contentHeight - y) * renderedHeight / contentHeight;
    }
}
