package top.zedo.skin.uis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MuiTextFileTest {
    @TempDir Path directory;

    @Test
    void preservesLegacyEncodingAcrossEdits() throws IOException {
        Path path = directory.resolve("skin.mui");
        Charset legacy = Charset.forName("GB18030");
        Files.write(path, "#中文注释\n@version 4.3.7\n".getBytes(legacy));

        MuiTextFile.Decoded source = MuiTextFile.read(path);
        assertEquals("#中文注释\n@version 4.3.7\n", source.text());
        assertEquals(legacy, source.charset());
        source.write(path, source.text() + "_image\n  tex=a.png\n");

        assertArrayEquals(("#中文注释\n@version 4.3.7\n_image\n  tex=a.png\n").getBytes(legacy),
                Files.readAllBytes(path));
        byte[] beforeUnsupportedEdit = Files.readAllBytes(path);
        assertThrows(IOException.class, () -> source.write(path, source.text() + "\ud800"));
        assertArrayEquals(beforeUnsupportedEdit, Files.readAllBytes(path));
    }

    @Test
    void keepsUniformCrLfWhenEditorUsesLf() throws IOException {
        Path path = directory.resolve("skin.mui");
        Files.writeString(path, "@version 4.3.7\r\n_image\r\n  pos=1,2\r\n");
        MuiTextFile.Decoded source = MuiTextFile.read(path);

        source.writeEditorText(path, "@version 4.3.7\n_image\n  pos=3,4\n");

        assertEquals("@version 4.3.7\r\n_image\r\n  pos=3,4\r\n", Files.readString(path));
    }

    @Test
    void rejectsExternalChangesWithoutOverwritingThem() throws IOException {
        Path path = directory.resolve("skin.mui");
        Files.writeString(path, "@unit 720\n");
        MuiTextFile.Decoded source = MuiTextFile.read(path);

        source.writeEditorText(path, "@unit 800\n");
        assertEquals("@unit 800\n", Files.readString(path));
        Files.writeString(path, "@unit 900\n");

        IOException error = assertThrows(IOException.class,
                () -> source.writeEditorText(path, "@unit 1000\n"));
        assertTrue(error.getMessage().contains("外部修改"));
        assertEquals("@unit 900\n", Files.readString(path));
    }
}
