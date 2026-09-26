package top.zedo.skin.uis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MuiSyntaxTreeTest {
    @Test
    void keepsLiteralSourceAndAssociatesPropertiesWithTheirSections() {
        String text = "# comment\r\n@define speed 1+2\n_item-[1-2]\r\n"
                + "  pos = 10%,20%  \r\n  pos=30,40\n:move\r\n\tframe=1,2";

        MuiSyntaxTree syntax = MuiSyntaxTree.parse(text);

        assertEquals(text, syntax.text());
        assertEquals(7, syntax.statements().size());
        assertInstanceOf(MuiSyntaxTree.Trivia.class, syntax.statementAt(0));
        MuiSyntaxTree.Directive directive = assertInstanceOf(MuiSyntaxTree.Directive.class, syntax.statementAt(1));
        assertEquals(java.util.List.of("@define", "speed", "1+2"), directive.arguments());
        MuiSyntaxTree.Section grouped = assertInstanceOf(MuiSyntaxTree.Section.class, syntax.statementAt(2));
        assertEquals("_item-[1-2]", grouped.name());

        MuiSyntaxTree.Property position = assertInstanceOf(MuiSyntaxTree.Property.class, syntax.statementAt(3));
        assertEquals(4, position.lineNumber());
        assertEquals(3, position.sectionLine());
        assertFalse(position.animation());
        assertEquals("pos", position.name());
        assertEquals("pos ", position.runtimeName());
        assertEquals("10%,20%", position.value());
        assertEquals("  pos = 12%,24%  \r\n", position.withValue("12%,24%").body()
                + position.withValue("12%,24%").ending());

        MuiSyntaxTree.Property repeated = assertInstanceOf(MuiSyntaxTree.Property.class, syntax.statementAt(4));
        assertEquals("30,40", repeated.value());
        MuiSyntaxTree.Property frame = assertInstanceOf(MuiSyntaxTree.Property.class, syntax.statementAt(6));
        assertEquals(6, frame.sectionLine());
        assertTrue(frame.animation());
    }
}
