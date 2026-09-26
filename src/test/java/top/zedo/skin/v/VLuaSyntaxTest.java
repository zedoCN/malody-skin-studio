package top.zedo.skin.v;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VLuaSyntaxTest {
    @Test
    void quotedCommentMarkersAndFunctionNamesAreClassifiedInContext() {
        String source = "local function loadchart()\n"
                + "  local text = \"-- not a comment\"\n"
                + "  Chart:ChartInfo('Title') + -- real comment\n"
                + "  return true\nend";
        List<VLuaSyntax.Token> tokens = VLuaSyntax.scan(source);
        assertEquals("lua-keyword", styleAt(tokens, source.indexOf("local")));
        assertEquals("lua-function", styleAt(tokens, source.indexOf("loadchart")));
        assertEquals("lua-string", styleAt(tokens, source.indexOf("-- not")));
        assertEquals("lua-function", styleAt(tokens, source.indexOf("ChartInfo")));
        assertEquals("lua-comment", styleAt(tokens, source.indexOf("-- real")));
        assertEquals("lua-literal", styleAt(tokens, source.indexOf("true")));
    }

    @Test
    void longBracketLevelsAndOperatorsDoNotBleedAcrossTokens() {
        String source = "--[=[ if 'x' ]==] still comment ]=]\n"
                + "local s = [==[ -- not comment ]=] string ]==]\n"
                + "local n = 0x1.fp10 + 34e-2 + 1..2 + .5 // 2\n"
                + "x+-- actual comment\n"
                + "endif = 1";
        List<VLuaSyntax.Token> tokens = VLuaSyntax.scan(source);
        assertEquals("lua-comment", styleAt(tokens, source.indexOf("still comment")));
        assertEquals("lua-string", styleAt(tokens, source.indexOf("-- not comment")));
        assertEquals("lua-string", styleAt(tokens, source.indexOf("string ]")));
        assertEquals("lua-number", styleAt(tokens, source.indexOf("0x1.fp10")));
        assertEquals("lua-number", styleAt(tokens, source.indexOf("34e-2")));
        assertEquals("lua-operator", styleAt(tokens, source.indexOf("..2")));
        assertEquals("lua-number", styleAt(tokens, source.indexOf(".5")));
        assertEquals("lua-operator", styleAt(tokens, source.indexOf("//")));
        assertEquals("lua-comment", styleAt(tokens, source.indexOf("-- actual")));
        assertNull(styleAt(tokens, source.indexOf("endif")));
    }

    private static String styleAt(List<VLuaSyntax.Token> tokens, int offset) {
        return tokens.stream().filter(token -> token.start() <= offset && offset < token.end())
                .map(VLuaSyntax.Token::style).findFirst().orElse(null);
    }
}
