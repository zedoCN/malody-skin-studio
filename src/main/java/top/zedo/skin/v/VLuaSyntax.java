package top.zedo.skin.v;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Lua tokens for editor coloring. Scans strings and comments before classifying code. */
final class VLuaSyntax {
    private static final Set<String> KEYWORDS = Set.of("and", "break", "do", "else", "elseif", "end",
            "for", "function", "goto", "if", "in", "local", "not", "or", "repeat", "return",
            "then", "until", "while");
    private static final Set<String> LITERALS = Set.of("false", "nil", "true");
    private static final Set<String> OPERATOR_PAIRS = Set.of("..", "//", "<<", ">>",
            "==", "~=", "<=", ">=");

    record Token(int start, int end, String style) { }

    static List<Token> scan(String text) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        boolean functionName = false;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) { i++; continue; }
            int start = i;
            if (i + 1 < text.length() && ch == '-' && text.charAt(i + 1) == '-') {
                int level = longBracketLevel(text, i + 2);
                i = level >= 0 ? longEnd(text, i + 2, level) : lineEnd(text, i + 2);
                tokens.add(new Token(start, i, "lua-comment"));
                continue;
            }
            if (ch == '#' && i == 0 && text.startsWith("#!")) {
                i = lineEnd(text, i);
                tokens.add(new Token(start, i, "lua-comment"));
                continue;
            }
            if (ch == '\'' || ch == '"') {
                i = quotedEnd(text, i, ch);
                tokens.add(new Token(start, i, "lua-string"));
                continue;
            }
            int level = ch == '[' ? longBracketLevel(text, i) : -1;
            if (level >= 0) {
                i = longEnd(text, i, level);
                tokens.add(new Token(start, i, "lua-string"));
                continue;
            }
            if (identifierStart(ch)) {
                i++;
                while (i < text.length() && identifierPart(text.charAt(i))) i++;
                String name = text.substring(start, i);
                String style;
                if (LITERALS.contains(name)) style = "lua-literal";
                else if (KEYWORDS.contains(name)) style = "lua-keyword";
                else if (functionName || nextNonWhitespace(text, i) == '(') style = "lua-function";
                else style = null;
                if (style != null) tokens.add(new Token(start, i, style));
                functionName = name.equals("function");
                continue;
            }
            if (asciiDigit(ch) || (ch == '.' && i + 1 < text.length()
                    && asciiDigit(text.charAt(i + 1)) && (i == 0 || text.charAt(i - 1) != '.'))) {
                i = numberEnd(text, i);
                tokens.add(new Token(start, i, "lua-number"));
                functionName = false;
                continue;
            }
            if (ch == '(') functionName = false;
            else if (functionName && ch != '.' && ch != ':') functionName = false;
            if ("+-*/%^#&~|<>=.".indexOf(ch) >= 0) {
                String pair = i + 1 < text.length() ? text.substring(i, i + 2) : "";
                i += text.startsWith("...", i) ? 3 : OPERATOR_PAIRS.contains(pair) ? 2 : 1;
                tokens.add(new Token(start, i, "lua-operator"));
            } else if ("(){}[];:,".indexOf(ch) >= 0) {
                i++;
                tokens.add(new Token(start, i, "lua-punctuation"));
            } else i++;
        }
        return tokens;
    }

    private static int longBracketLevel(String text, int start) {
        if (start >= text.length() || text.charAt(start) != '[') return -1;
        int i = start + 1;
        while (i < text.length() && text.charAt(i) == '=') i++;
        return i < text.length() && text.charAt(i) == '[' ? i - start - 1 : -1;
    }

    private static int longEnd(String text, int start, int level) {
        String close = "]" + "=".repeat(level) + "]";
        int end = text.indexOf(close, start + level + 2);
        return end < 0 ? text.length() : end + close.length();
    }

    private static int lineEnd(String text, int start) {
        int i = start;
        while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') i++;
        return i;
    }

    private static int quotedEnd(String text, int start, char quote) {
        int i = start + 1;
        while (i < text.length()) {
            char ch = text.charAt(i++);
            if (ch == '\\' && i < text.length()) { i++; continue; }
            if (ch == quote || ch == '\n' || ch == '\r') break;
        }
        return i;
    }

    private static int numberEnd(String text, int start) {
        int i = start;
        boolean hex = i + 1 < text.length() && text.charAt(i) == '0'
                && (text.charAt(i + 1) == 'x' || text.charAt(i + 1) == 'X');
        if (hex) i += 2;
        while (i < text.length() && digit(text.charAt(i), hex)) i++;
        if (i < text.length() && text.charAt(i) == '.'
                && (i + 1 >= text.length() || text.charAt(i + 1) != '.')) {
            i++;
            while (i < text.length() && digit(text.charAt(i), hex)) i++;
        }
        if (i < text.length() && (hex ? text.charAt(i) == 'p' || text.charAt(i) == 'P'
                : text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
            int exponent = i++;
            if (i < text.length() && (text.charAt(i) == '+' || text.charAt(i) == '-')) i++;
            int digits = i;
            while (i < text.length() && asciiDigit(text.charAt(i))) i++;
            if (digits == i) i = exponent;
        }
        return i;
    }

    private static char nextNonWhitespace(String text, int start) {
        int i = start;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return i < text.length() ? text.charAt(i) : '\0';
    }

    private static boolean identifierStart(char ch) {
        return ch == '_' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z';
    }

    private static boolean identifierPart(char ch) { return identifierStart(ch) || asciiDigit(ch); }
    private static boolean asciiDigit(char ch) { return ch >= '0' && ch <= '9'; }
    private static boolean digit(char ch, boolean hex) {
        return asciiDigit(ch) || hex && (ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <= 'F');
    }

    private VLuaSyntax() { }
}
