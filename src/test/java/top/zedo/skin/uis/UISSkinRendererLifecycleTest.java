package top.zedo.skin.uis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.zedo.skin.uis.component.ImageComponentRenderer;
import top.zedo.skin.uis.component.TextComponentRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UISSkinRendererLifecycleTest {
    @TempDir Path directory;

    @Test
    void replacesRendererWhenCustomComponentTypeChanges() throws IOException {
        Path script = directory.resolve("skin.mui");
        Files.writeString(script, "_item\n  type=0\n  size=20,20\n");
        UISCanvas canvas = new UISCanvas();
        canvas.setAspectRatio(16.0 / 9);
        canvas.loadSkin(script);
        assertEquals(1, canvas.componentRenders.size());
        assertInstanceOf(ImageComponentRenderer.class, canvas.componentRenders.getFirst());

        Files.writeString(script, "_item\n  type=1\n  text=hello\n  size=20,20\n");
        canvas.updateSkin();
        assertEquals(1, canvas.componentRenders.size());
        assertInstanceOf(TextComponentRenderer.class, canvas.componentRenders.getFirst());
        assertEquals(1, canvas.componentMap.size());

        Files.writeString(script, "_item\n  type=99\n");
        canvas.updateSkin();
        assertTrue(canvas.componentRenders.isEmpty());
        assertTrue(canvas.componentMap.isEmpty());
    }

    @Test
    void reportsParseFailureWithoutReplacingExistingRenderers() throws IOException {
        Path script = directory.resolve("skin.mui");
        Files.writeString(script, "_item\n  type=0\n  size=20,20\n");
        UISCanvas canvas = new UISCanvas();
        canvas.setAspectRatio(16.0 / 9);
        canvas.loadSkin(script);
        var original = canvas.componentRenders.getFirst();

        Files.writeString(script, "_item\n  type=0\n  broken-property\n");
        assertThrows(IOException.class, canvas::updateSkin);
        assertSame(original, canvas.componentRenders.getFirst());
    }

    @Test
    void picksOnlyStaticImagesWithSupportedPositionSource() throws IOException {
        Path script = directory.resolve("skin.mui");
        Files.writeString(script, "_item\n  type=0\n  tex=missing.png\n  pos=50px,50px\n  size=20px,20px\n  anchor=4\n");
        UISCanvas canvas = new UISCanvas();
        canvas.setAspectRatio(16.0 / 9);
        canvas.loadSkin(script);
        assertNotNull(canvas.pickEditableImage(45, 665));
        assertNull(canvas.pickEditableImage(25, 665));

        Files.writeString(script, "_item\n  type=0\n  tex=missing.png\n  pos=50px,50px\n  size=20px,20px\n  anchor=10,10\n");
        canvas.updateSkin();
        assertNull(canvas.pickEditableImage(45, 665));
    }
}
