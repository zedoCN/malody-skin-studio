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
}
