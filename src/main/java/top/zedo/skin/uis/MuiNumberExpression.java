package top.zedo.skin.uis;

/** Arithmetic used by legacy MUI position and scalar fields. */
final class MuiNumberExpression {
    private final String input;
    private final double unitScale;
    private final double percentScale;
    private final double widthScale;
    private int offset;

    private MuiNumberExpression(String input, double unitScale, double percentScale, double widthScale) {
        this.input = input;
        this.unitScale = unitScale;
        this.percentScale = percentScale;
        this.widthScale = widthScale;
    }

    static double evaluate(String input, double unitScale, double percentScale, double widthScale) {
        if (input == null || input.isBlank()) return 0;
        MuiNumberExpression parser = new MuiNumberExpression(input, unitScale, percentScale, widthScale);
        double value = parser.sum();
        parser.skipSpaces();
        if (parser.offset != input.length() || !Double.isFinite(value)) {
            throw new IllegalArgumentException("无效的 UIS 数值表达式: " + input);
        }
        return value;
    }

    private double sum() {
        double value = product();
        while (true) {
            skipSpaces();
            if (eat('+')) value += product();
            else if (eat('-')) value -= product();
            else return value;
        }
    }

    private double product() {
        double value = unary();
        while (true) {
            skipSpaces();
            if (eat('*')) value = value * unary() / unitScale;
            else if (eat('/')) value = value / (unary() / unitScale);
            else return value;
        }
    }

    private double unary() {
        skipSpaces();
        if (eat('+')) return unary();
        if (eat('-')) return -unary();
        if (eat('(')) {
            double value = sum();
            skipSpaces();
            if (!eat(')')) throw new IllegalArgumentException("括号不匹配: " + input);
            return value;
        }
        int start = offset;
        while (offset < input.length() && (Character.isDigit(input.charAt(offset)) || input.charAt(offset) == '.')) offset++;
        if (start == offset) throw new IllegalArgumentException("缺少数值: " + input);
        double value;
        try {
            value = Double.parseDouble(input.substring(start, offset));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("无效数值: " + input, error);
        }
        if (eat('%')) return value * percentScale / 100.0;
        if (offset + 1 < input.length() && input.startsWith("px", offset)) {
            offset += 2;
            return value;
        }
        if (eat('w')) return value * widthScale;
        return value * unitScale;
    }

    private boolean eat(char token) {
        if (offset < input.length() && input.charAt(offset) == token) {
            offset++;
            return true;
        }
        return false;
    }

    private void skipSpaces() {
        while (offset < input.length() && Character.isWhitespace(input.charAt(offset))) offset++;
    }
}
