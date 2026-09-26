package top.zedo.skin.uis;

import java.io.IOException;
import java.nio.file.Path;

/** Prepares a position edit against the exact literal MUI line used by the preview. */
public final class MuiPositionEditor {
    private MuiPositionEditor() { }

    public static Edit prepare(UISComponent component, double dx, double dy) throws IOException {
        MuiSourceLocation source = component.getPropertySource("pos");
        String original = component.getRawProperty("pos");
        if (source == null || original == null || source.grouped() || component.hasPositionParent()) {
            throw new IllegalArgumentException("此组件的 pos 不能单独写回源文件: " + component.getFullName());
        }
        MuiDocument document = MuiDocument.read(source.file());
        String translated = MuiPositionDrag.translate(original, dx, dy);
        MuiDocument updated = document.replacePropertyAt(source, component.getFullName(),
                "pos", original, translated);
        return new Edit(source.file(), updated);
    }

    public record Edit(Path file, MuiDocument document) {
        public void save() throws IOException {
            document.save(file);
        }
    }
}
