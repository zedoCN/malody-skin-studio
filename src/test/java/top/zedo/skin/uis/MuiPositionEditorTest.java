package top.zedo.skin.uis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class MuiPositionEditorTest {
    @TempDir Path directory;

    @Test
    void editsOnlyEffectiveIncludedLineAndRetainsOtherSections() throws IOException {
        Path root = directory.resolve("skin.mui");
        Path included = directory.resolve("extra.mui");
        Files.writeString(root, "@include extra.mui\n_item\n  size=10,10\n");
        Files.writeString(included, "_item\r\n  pos=10,20\r\n_item\r\n  tex=icon.png\r\n");
        UISSkin skin = new UISSkin(root, new ExpressionCalculator(1280, 720, 720));
        UISComponent component = new MuiSkinLoader(skin, new HashMap<>()).load(root).components().get("_item");

        MuiPositionEditor.Edit edit = MuiPositionEditor.prepare(component, 12, -3);
        assertEquals(included.toRealPath(), edit.file());
        edit.save();

        assertEquals("_item\r\n  pos=10+12px,20+3px\r\n_item\r\n  tex=icon.png\r\n",
                Files.readString(included));
        assertEquals("@include extra.mui\n_item\n  size=10,10\n", Files.readString(root));
    }

    @Test
    void rejectsStaleSectionAndGroupedSource() throws IOException {
        Path root = directory.resolve("skin.mui");
        Files.writeString(root, "_item\n  pos=10,20\n_group-[1-2]\n  pos=5,5\n");
        UISSkin skin = new UISSkin(root, new ExpressionCalculator(1280, 720, 720));
        var components = new MuiSkinLoader(skin, new HashMap<>()).load(root).components();
        assertThrows(IllegalArgumentException.class,
                () -> MuiPositionEditor.prepare(components.get("_group-1"), 1, 1));

        Files.writeString(root, "_other\n  pos=10,20\n_group-[1-2]\n  pos=5,5\n");
        assertThrows(IllegalStateException.class,
                () -> MuiPositionEditor.prepare(components.get("_item"), 1, 1));
    }
}
