package top.zedo.skin.uis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Comparator;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

final class MszWorkspaceTest {
    @TempDir Path temp;

    @Test
    void extractsResourcesAndSyncsOnlySelectedScript() throws Exception {
        Path archive = archive("skin.msz", Map.of(
                "script.mui", "@include sub.mui\n".getBytes(StandardCharsets.UTF_8),
                "sub.mui", "_image\n\tpos=0,0\n".getBytes(StandardCharsets.UTF_8),
                "images/key.png", new byte[]{1, 2, 3}));
        Path root;
        try (MszWorkspace workspace = MszWorkspace.open(archive)) {
            root = workspace.directory();
            assertEquals(2, workspace.scriptEntries().size());
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(root.resolve("images/key.png")));
            Path included = workspace.scriptPath("sub.mui");
            Path main = workspace.scriptPath("script.mui");
            UISSkin skin = new UISSkin(main, new ExpressionCalculator(1280, 720, 720));
            UISComponent component = new MuiSkinLoader(skin, new HashMap<>()).load(main).components().get("_image");
            MuiPositionEditor.Edit edit = MuiPositionEditor.prepare(component, 10, 0);
            assertEquals(included, edit.file());
            edit.save();
            workspace.syncScript(edit.file());
            byte[] changed = Files.readAllBytes(included);
            assertArrayEquals(changed, MszSkinPackage.open(archive).readScript("sub.mui"));
            Files.writeString(main, "@include sub.mui\n@unit 720\n");
            workspace.syncScript(main);
            assertEquals("@include sub.mui\n@unit 720\n",
                    new String(MszSkinPackage.open(archive).readScript("script.mui"), StandardCharsets.UTF_8));
            assertArrayEquals(changed, MszSkinPackage.open(archive).readScript("sub.mui"));
            assertThrows(IOException.class, () -> workspace.syncScript(root.resolve("images/key.png")));
            assertThrows(IOException.class, () -> workspace.scriptPath("images/key.png"));
        }
        assertFalse(Files.exists(root));
    }

    @Test
    void conflictLeavesRecoverableWorkspaceAndCloseRetries() throws Exception {
        Path archive = archive("conflict.msz", Map.of("script.mui", new byte[]{1}));
        MszWorkspace workspace = MszWorkspace.open(archive);
        Path script = workspace.scriptPath("script.mui");
        Files.write(script, new byte[]{2});
        Files.write(archive, new byte[]{3});
        IOException error = assertThrows(IOException.class, () -> workspace.syncScript(script));
        assertTrue(error.getMessage().contains(script.toString()));
        assertThrows(IOException.class, workspace::close);
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(script));
        assertTrue(Files.exists(workspace.directory()));
        try (var files = Files.walk(workspace.directory())) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }

    @Test
    void realPackageCanBePreviewedWhenAvailable() throws Exception {
        Path sample = Path.of("/tmp/malody-437a/Thirteen-Malody-4k-5.msz");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(sample));
        Path archive = temp.resolve("real.msz");
        Files.copy(sample, archive);
        byte[] before = Files.readAllBytes(archive);
        try (MszWorkspace workspace = MszWorkspace.open(archive)) {
            assertEquals("script-key-4K.mui", workspace.scriptEntries().getFirst());
            assertTrue(Files.isRegularFile(workspace.scriptPath("script-key-4K.mui")));
            try (var files = Files.walk(workspace.directory())) {
                assertEquals(MszSkinPackage.open(archive).entries().stream().filter(name -> !name.endsWith("/")).count(),
                        files.filter(Files::isRegularFile).count());
            }
            workspace.syncScript(workspace.scriptPath("script-key-4K.mui"));
        }
        assertArrayEquals(before, Files.readAllBytes(archive));
    }

    private Path archive(String filename, Map<String, byte[]> entries) throws IOException {
        Path path = temp.resolve(filename);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> item : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(item.getKey()));
                zip.write(item.getValue());
                zip.closeEntry();
            }
        }
        return path;
    }
}
