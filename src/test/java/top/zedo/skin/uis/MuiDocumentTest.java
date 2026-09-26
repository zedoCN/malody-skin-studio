package top.zedo.skin.uis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MuiDocumentTest {
    @TempDir Path directory;

    @Test
    void realSampleRoundTripsWithoutChangingBytesAndEditsOneLiteralSection() throws IOException {
        for (String name : new String[] {"动画", "背景移动", "基本", "斜切"}) {
            Path sample = Path.of("examples", "uis", name + ".mui");
            MuiDocument.read(sample).save(directory.resolve(name + ".mui"));
            assertArrayEquals(Files.readAllBytes(sample),
                    Files.readAllBytes(directory.resolve(name + ".mui")), name);
        }
        Path sample = Path.of("examples/uis/基本.mui");
        byte[] original = Files.readAllBytes(sample);
        MuiDocument document = MuiDocument.read(sample);
        Path copy = directory.resolve("copy.mui");
        document.save(copy);
        assertArrayEquals(original, Files.readAllBytes(copy));

        assertEquals("_bar", document.sections().getFirst().header());
        assertEquals("50%,50%", document.properties(0).stream()
                .filter(property -> property.name().equals("pos")).findFirst().orElseThrow().value());
        MuiDocument edited = document.withProperty(0, "pos", "40%,60%");
        assertEquals(document.text().replace("    pos=50%,50%", "    pos=40%,60%"), edited.text());
        edited.save(copy);
        assertEquals(edited.text(), MuiDocument.read(copy).text());
        assertArrayEquals(original, Files.readAllBytes(sample));
    }

    @Test
    void preservesEncodingBomLineEndingsCommentsAndUnknownProperties() throws IOException {
        String input = "\ufeff# 注释\r\n@version 4.3.7\r\nitem-[1-2]\r\n  unknown = old  \r\n  pos = 1,2  \r\n# tail\r\nitem-1\r\n  pos=3,4";
        Path path = directory.resolve("skin.mui");
        Files.writeString(path, input);
        MuiDocument document = MuiDocument.read(path);
        assertEquals(2, document.sections().size());
        assertEquals(input.substring(1), document.text()); // BOM belongs to the file encoding.

        document.withProperty(0, "pos", "5,6").save(path);
        assertEquals(input.replace("  pos = 1,2  ", "  pos = 5,6  "), Files.readString(path));

        MuiDocument.read(path).withProperty(1, "anchor", "4").save(path);
        assertTrue(Files.readString(path).endsWith("item-1\r\n  pos=3,4\r\n  anchor=4"));
    }

    @Test
    void changesOnlyTheRecordedLineAndRejectsStaleSourceLocations() throws IOException {
        Path path = directory.resolve("repeat.mui");
        Files.writeString(path, "_item\r\n  pos = 1,2  \r\n_item\r\n  pos=3,4\r\n");
        MuiDocument document = MuiDocument.read(path);

        MuiDocument edited = document.replacePropertyAtLine(4, "pos", "3,4", "5,6");

        assertEquals("_item\r\n  pos = 1,2  \r\n_item\r\n  pos=5,6\r\n", edited.text());
        assertThrows(IllegalStateException.class,
                () -> edited.replacePropertyAtLine(4, "pos", "3,4", "7,8"));
        assertThrows(IllegalStateException.class,
                () -> document.replacePropertyAtLine(2, "pos", "3,4", "7,8"));
    }

    @Test
    void doesNotEditPropertiesBelongingToAnotherRuntimeSection() throws IOException {
        Path path = directory.resolve("sections.mui");
        Files.writeString(path, "_item\n  pos=1,2\n _other\n  pos=3,4\n");
        MuiDocument document = MuiDocument.read(path);

        assertEquals(1, document.sections().size());
        assertEquals(1, document.properties(0).size());
        assertEquals("1,2", document.properties(0).getFirst().value());
        assertEquals("_item\n  pos=5,6\n _other\n  pos=3,4\n",
                document.withProperty(0, "pos", "5,6").text());
        assertEquals("_item\n  pos=1,2\n  size=7,8\n _other\n  pos=3,4\n",
                document.withProperty(0, "size", "7,8").text());
    }

    @Test
    void keepsGb18030AndRejectsUnrepresentableValueWithoutTouchingFile() throws IOException {
        Charset legacy = Charset.forName("GB18030");
        Path path = directory.resolve("legacy.mui");
        String input = "#中文\n_item\n\ttext=旧文字\n";
        Files.write(path, input.getBytes(legacy));
        MuiDocument document = MuiDocument.read(path);
        document.withProperty(0, "text", "新文字").save(path);
        assertArrayEquals(input.replace("旧文字", "新文字").getBytes(legacy), Files.readAllBytes(path));

        byte[] before = Files.readAllBytes(path);
        assertThrows(IllegalArgumentException.class,
                () -> document.withProperty(0, "text", "bad\nvalue"));
        assertThrows(IOException.class,
                () -> document.withProperty(0, "text", "\ud800").save(path));
        assertArrayEquals(before, Files.readAllBytes(path));
    }
}
