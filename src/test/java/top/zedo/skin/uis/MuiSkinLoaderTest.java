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
    void expandsMixedRangesAndConditionalIncludes() throws IOException {
        Path root = directory.resolve("skin.mui");
        Files.writeString(root, "@version 4.3.7\n@includex <1.5 narrow.mui\n@includex >1.5 wide.mui\n"
                + "_case-[1,3-4]\n  pos=10,20\n_case.2-[5-6]\n  pos=30,40\n");
        Files.writeString(directory.resolve("narrow.mui"), "narrow\n  type=0\n");
        Files.writeString(directory.resolve("wide.mui"), "wide\n  type=0\n");
        UISSkin skin = new UISSkin(root, new ExpressionCalculator(1280, 720, 720));

        MuiSkinLoader.Result result = new MuiSkinLoader(skin, new HashMap<>()).load(root);

        assertTrue(result.components().containsKey("wide"));
        assertFalse(result.components().containsKey("narrow"));
        for (String name : new String[]{"_case-1", "_case-3", "_case-4", "_case.2-5", "_case.2-6"}) {
            assertTrue(result.components().containsKey(name), name);
        }
        assertFalse(result.components().containsKey("_case-2"));
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
    void macMatchesWindowsCompatibilityAndMacConditions() throws IOException {
        Path root = directory.resolve("skin.mui");
        Files.writeString(root, "@if windows\nwin\n  type=0\n@endif\n@if mac\nmac\n  type=0\n@endif\n");
        UISSkin skin = new UISSkin(root, new ExpressionCalculator(1280, 720, 720));
        skin.setDeviceType(top.zedo.skin.DeviceType.MAC);

        MuiSkinLoader.Result result = new MuiSkinLoader(skin, new HashMap<>()).load(root);

        assertTrue(result.components().containsKey("win"));
        assertTrue(result.components().containsKey("mac"));
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
    void resolvesNestedNumericDefinesFromLegacySkins() throws IOException {
        Path root = directory.resolve("skin.mui");
        Files.writeString(root, "@define Width 66\n@define Half {Width}/2\n@define Full {Width}+{Half}\n"
                + "note\n  pos=50%+{Full},20\n  text={Full}\n");
        ExpressionCalculator calculator = new ExpressionCalculator(1280, 720, 720);
        UISSkin skin = new UISSkin(root, calculator);
        UISComponent note = new MuiSkinLoader(skin, new HashMap<>()).load(root).components().get("note");

        assertEquals(739, note.getExpressionVector("pos").getX());
        assertEquals("66+66/2", note.getString("text", ""));
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
