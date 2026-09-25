package top.zedo.skin.uis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class MuiSkinLoaderTest {
    @TempDir Path directory;

    @Test
    void loadsIncludesComponentsAndImageIndex() throws IOException {
        Path root = directory.resolve("skin.mui");
        Files.writeString(root, "@unit 900\n@angle 15\n@define caption Hello world\n@include extra.mui\n_sprite-[1-2]\n  type=0\n  tex=cover.png\n");
        Files.writeString(directory.resolve("extra.mui"), "note\n  pos=10,20\n");
        Files.write(directory.resolve("cover.png"), new byte[0]);
        Files.createDirectory(directory.resolve("cache"));
        Files.write(directory.resolve("cache").resolve("frame.png"), new byte[0]);

        UISSkin skin = new UISSkin(root, new ExpressionCalculator());
        HashMap<String, Path> images = new HashMap<>();
        MuiSkinLoader.Result result = new MuiSkinLoader(skin, images).load(root);

        assertEquals(900, result.unit());
        assertEquals(15, result.angle());
        assertEquals("Hello world", skin.variable.get("caption"));
        assertTrue(result.components().containsKey("note"));
        assertTrue(result.components().containsKey("_sprite-1"));
        assertTrue(result.components().containsKey("_sprite-2"));
        assertEquals(directory.resolve("cover.png").toRealPath(), images.get("cover.png"));
        assertEquals(directory.resolve("cache/frame.png").toRealPath(), images.get("frame.png"));
    }

    @Test
    void skippedSectionDoesNotExecuteIncludes() throws IOException {
        Path root = directory.resolve("skin.mui");
        Files.writeString(root, "@if false\n@if true\n@include missing.mui\n@unit 999\nignored\n  type=0\n@endif\n@endif\nnote\n  pos=0,0\n");
        UISSkin skin = new UISSkin(root, new ExpressionCalculator());

        MuiSkinLoader.Result result = new MuiSkinLoader(skin, new HashMap<>()).load(root);

        assertEquals(720, result.unit());
        assertFalse(result.components().containsKey("ignored"));
        assertTrue(result.components().containsKey("note"));
    }

    @Test
    void rejectsCyclicIncludes() throws IOException {
        Path root = directory.resolve("skin.mui");
        Files.writeString(root, "@include extra.mui\n");
        Files.writeString(directory.resolve("extra.mui"), "@include skin.mui\n");
        UISSkin skin = new UISSkin(root, new ExpressionCalculator());

        IOException error = assertThrows(IOException.class,
                () -> new MuiSkinLoader(skin, new HashMap<>()).load(root));
        assertTrue(error.getMessage().contains("循环引用"));
    }

    @Test
    void loadsBundledSampleSkins() throws IOException {
        for (String name : new String[]{"基本", "动画", "斜切"}) {
            Path path = Path.of("examples", name + ".mui");
            UISSkin skin = new UISSkin(path, new ExpressionCalculator(1280, 720, 720));
            MuiSkinLoader.Result result = new MuiSkinLoader(skin, new HashMap<>()).load(path);
            assertFalse(result.components().isEmpty(), name);
        }
    }

    @Test
    void mutableComponentsKeepIdentityAsMapKeys() {
        UISSkin skin = new UISSkin(directory.resolve("skin.mui"), new ExpressionCalculator());
        UISComponent original = new UISComponent("_sprite-1", new HashMap<>(), skin);
        UISComponent updated = new UISComponent("_sprite-1", new HashMap<>(), skin);
        HashMap<UISComponent, String> renderers = new HashMap<>();
        renderers.put(original, "renderer");

        assertTrue(original.hasSameContent(updated));
        updated.putProperty("pos", "10,20", 0);
        assertFalse(original.hasSameContent(updated));
        original.copyFrom(updated);
        assertTrue(original.hasSameContent(updated));
        assertEquals("renderer", renderers.get(original));
        assertFalse(renderers.containsKey(updated));
    }
}
