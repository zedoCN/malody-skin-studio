package top.zedo.skin.v;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import com.google.protobuf.UnknownFieldSet;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class VLuaSourceTest {
    @TempDir Path directory;

    @Test
    void preservesOriginalLineEndingsWhenRichTextEditorNormalizesThem() {
        byte[] original = "local x = 1\r\nreturn x\r\n".getBytes(StandardCharsets.UTF_8);
        VLuaSource.Result loaded = new VLuaSource.Result("script.lua",
                "local x = 1\r\nreturn x\r\n", "", original, false);
        assertEquals("local x = 1\nreturn x\n", VLuaSource.editorText(loaded));
        assertArrayEquals(original, VLuaSource.editedBytes(loaded, VLuaSource.editorText(loaded)));
        assertEquals("local x = 2\r\nreturn x\r\n",
                new String(VLuaSource.editedBytes(loaded, "local x = 2\nreturn x\n"), StandardCharsets.UTF_8));
    }

    @Test
    void reportsScriptContentAndReadFailuresWithoutChangingPackage() throws IOException {
        byte[] script = "\ufefferror('must not run')".getBytes(StandardCharsets.UTF_8);
        Path valid = packageWithScript("valid", "script.lua", script);
        byte[] original = Files.readAllBytes(valid);
        VLuaSource.Result loaded = VLuaSource.load(MspSkinDocument.open(valid));
        assertEquals("script.lua", loaded.path());
        assertEquals("error('must not run')", loaded.source());
        assertTrue(loaded.diagnostic().contains("不执行"));
        assertArrayEquals(original, Files.readAllBytes(valid));

        VLuaSource.Result missing = VLuaSource.load(MspSkinDocument.open(
                packageWithScript("missing", "absent.lua", null)));
        assertTrue(missing.diagnostic().contains("不存在"));
        assertEquals("", missing.source());

        VLuaSource.Result traversal = VLuaSource.load(MspSkinDocument.open(
                packageWithScript("traversal", "../outside.lua", null)));
        assertTrue(traversal.diagnostic().contains("路径越界"));
        assertEquals("", traversal.source());

        VLuaSource.Result windowsAbsolute = VLuaSource.load(MspSkinDocument.open(
                packageWithScript("absolute", "C:\\outside.lua", null)));
        assertTrue(windowsAbsolute.diagnostic().contains("路径越界"));

        VLuaSource.Result malformed = VLuaSource.load(MspSkinDocument.open(
                packageWithScript("malformed", "script.lua", new byte[]{(byte) 0xc3, 0x28})));
        assertTrue(malformed.diagnostic().contains("UTF-8"));
        assertEquals("", malformed.source());
    }

    @Test
    void savesLuaAndMetadataInMspWithoutChangingOtherEntries() throws IOException {
        Path file = directory.resolve("editable.msp");
        byte[] originalAsset = {1, 2, 3};
        UnknownFieldSet unknown = UnknownFieldSet.newBuilder().addField(100,
                UnknownFieldSet.Field.newBuilder().addVarint(42).build()).build();
        SkinFile original = SkinFile.newBuilder().setMeta(SkinFile.Meta.newBuilder()
                .setTitle("Before").setScript("script.lua").setUnknownFields(unknown))
                .setUnknownFields(unknown).build();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("skin/info.asm"));
            original.writeTo(zip);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("skin/script.lua"));
            zip.write("\ufefflocal title = 'old'\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("skin/image.png"));
            zip.write(originalAsset);
            zip.closeEntry();
        }
        MspSkinDocument document = MspSkinDocument.open(file);
        VLuaSource.Result loaded = VLuaSource.load(document);
        SkinFile updated = document.skin().toBuilder().setMeta(document.skin().getMeta().toBuilder()
                .setTitle("After")).build();
        document.save(updated, loaded.path(), loaded.originalBytes(),
                VLuaSource.editedBytes(loaded, "local title = 'new'\n"));

        MspSkinDocument reopened = MspSkinDocument.open(file);
        assertEquals("After", reopened.skin().getMeta().getTitle());
        assertEquals(unknown, reopened.skin().getUnknownFields());
        assertEquals(unknown, reopened.skin().getMeta().getUnknownFields());
        assertArrayEquals(originalAsset, reopened.resource("image.png"));
        assertEquals("local title = 'new'\n", VLuaSource.load(reopened).source());
        assertArrayEquals("\ufefflocal title = 'new'\n".getBytes(StandardCharsets.UTF_8),
                reopened.resource("script.lua"));
    }

    @Test
    void savesFolderLuaAndRejectsExternalChangesOrMissingReference() throws IOException {
        Path folder = Files.createDirectory(directory.resolve("folder"));
        SkinFile original = SkinFile.newBuilder().setMeta(SkinFile.Meta.newBuilder()
                .setTitle("Before").setScript("script.lua")).build();
        Files.write(folder.resolve("info.asm"), original.toByteArray());
        Files.writeString(folder.resolve("script.lua"), "old");
        Files.writeString(folder.resolve("image.png"), "asset");
        MspSkinDocument document = MspSkinDocument.open(folder);
        VLuaSource.Result loaded = VLuaSource.load(document);
        SkinFile updated = original.toBuilder().setMeta(original.getMeta().toBuilder().setTitle("After")).build();
        document.save(updated, loaded.path(), loaded.originalBytes(), VLuaSource.editedBytes(loaded, "new"));
        assertEquals("After", MspSkinDocument.open(folder).skin().getMeta().getTitle());
        assertEquals("new", Files.readString(folder.resolve("script.lua")));
        assertEquals("asset", Files.readString(folder.resolve("image.png")));

        VLuaSource.Result saved = VLuaSource.load(document);
        Files.writeString(folder.resolve("script.lua"), "external");
        IOException conflict = assertThrows(IOException.class, () -> document.save(updated, saved.path(),
                saved.originalBytes(), VLuaSource.editedBytes(saved, "editor")));
        assertTrue(conflict.getMessage().contains("其他程序修改"));
        assertEquals("external", Files.readString(folder.resolve("script.lua")));

        Path missing = packageWithScript("missing-save", "absent.lua", null);
        MspSkinDocument missingDocument = MspSkinDocument.open(missing);
        VLuaSource.Result missingSource = VLuaSource.load(missingDocument);
        IOException missingError = assertThrows(IOException.class, () -> missingDocument.save(
                missingDocument.skin(), missingSource.path(), missingSource.originalBytes(), null));
        assertTrue(missingError.getMessage().contains("缺失"));

        Path traversal = packageWithScript("traversal-save", "../outside.lua", null);
        MspSkinDocument traversalDocument = MspSkinDocument.open(traversal);
        IOException traversalError = assertThrows(IOException.class, () -> traversalDocument.save(
                traversalDocument.skin(), "../outside.lua", new byte[0], new byte[]{1}));
        assertTrue(traversalError.getMessage().contains("路径越界"));
    }

    @Test
    void rejectsExternalMspChangeBeforeSavingLua() throws IOException {
        Path file = packageWithScript("external-save", "script.lua", "old".getBytes(StandardCharsets.UTF_8));
        MspSkinDocument document = MspSkinDocument.open(file);
        VLuaSource.Result loaded = VLuaSource.load(document);
        packageWithScript("external-save", "script.lua", "external".getBytes(StandardCharsets.UTF_8));
        byte[] external = Files.readAllBytes(file);
        IOException conflict = assertThrows(IOException.class, () -> document.save(document.skin(),
                loaded.path(), loaded.originalBytes(), VLuaSource.editedBytes(loaded, "editor")));
        assertTrue(conflict.getMessage().contains("其他程序修改"));
        assertArrayEquals(external, Files.readAllBytes(file));
    }

    @Test
    void roundTripsProvidedMspScriptOnTemporaryCopy() throws IOException {
        String sample = System.getProperty("malody.v.lua.sample", "");
        Assumptions.assumeFalse(sample.isBlank(), "Pass -Dmalody.v.lua.sample=/path/to/skin.msp");
        Path source = Path.of(sample);
        byte[] originalPackage = Files.readAllBytes(source);
        Path copy = directory.resolve("lua-sample-copy.msp");
        Files.copy(source, copy);
        MspSkinDocument document = MspSkinDocument.open(copy);
        VLuaSource.Result loaded = VLuaSource.load(document);
        assertFalse(loaded.path().isBlank(), "sample must reference a Lua script");
        assertNotNull(loaded.originalBytes(), loaded.diagnostic());
        SkinFile updated = document.skin().toBuilder().setMeta(document.skin().getMeta().toBuilder()
                .setTitle(document.skin().getMeta().getTitle() + " (test)")).build();
        document.save(updated, loaded.path(), loaded.originalBytes(),
                VLuaSource.editedBytes(loaded, loaded.source() + "\n-- save test\n"));

        assertArrayEquals(originalPackage, Files.readAllBytes(source));
        MspSkinDocument reopened = MspSkinDocument.open(copy);
        assertEquals(updated, reopened.skin());
        assertTrue(VLuaSource.load(reopened).source().endsWith("\n-- save test\n"));
        try (ZipFile before = new ZipFile(source.toFile()); ZipFile after = new ZipFile(copy.toFile())) {
            assertEquals(before.size(), after.size());
            var entries = before.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                ZipEntry saved = after.getEntry(entry.getName());
                assertNotNull(saved, entry.getName());
                if (!entry.getName().endsWith("info.asm") && !entry.getName().endsWith(loaded.path())) {
                    assertArrayEquals(before.getInputStream(entry).readAllBytes(),
                            after.getInputStream(saved).readAllBytes(), entry.getName());
                }
            }
        }
    }

    private Path packageWithScript(String name, String scriptPath, byte[] script) throws IOException {
        Path file = directory.resolve(name + ".msp");
        SkinFile skin = SkinFile.newBuilder().setMeta(SkinFile.Meta.newBuilder()
                .setTitle(name).setScript(scriptPath)).build();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("skin/info.asm"));
            skin.writeTo(zip);
            zip.closeEntry();
            if (script != null) {
                zip.putNextEntry(new ZipEntry("skin/script.lua"));
                zip.write(script);
                zip.closeEntry();
            }
        }
        return file;
    }
}
