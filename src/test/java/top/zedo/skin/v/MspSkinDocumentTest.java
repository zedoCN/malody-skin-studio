package top.zedo.skin.v;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MspSkinDocumentTest {
    @TempDir Path directory;

    @Test
    void editsPackageWithoutChangingOtherAssets() throws IOException {
        Path packageFile = directory.resolve("skin.msp");
        byte[] originalImage = {1, 2, 3, 4};
        SkinFile original = SkinFile.newBuilder()
                .setMeta(SkinFile.Meta.newBuilder().setTitle("Original"))
                .addModules(SkinFile.Module.newBuilder()
                        .setMeta(SkinFile.ModuleMeta.newBuilder().setDesc("Image"))
                        .setImage(SkinFile.ModuleParamImage.newBuilder().setFile("image.png")))
                .build();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(packageFile))) {
            zip.putNextEntry(new ZipEntry("skin/info.asm"));
            original.writeTo(zip);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("skin/image.png"));
            zip.write(originalImage);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("skin/script.lua"));
            zip.write("print('hi')".getBytes());
            zip.closeEntry();
        }

        MspSkinDocument document = MspSkinDocument.open(packageFile);
        assertEquals("Original", document.skin().getMeta().getTitle());
        assertArrayEquals(originalImage, document.resource("image.png"));
        assertThrows(IOException.class, () -> document.resource("../outside.png"));
        document.save(document.skin().toBuilder()
                .setMeta(document.skin().getMeta().toBuilder().setTitle("Updated"))
                .build());

        MspSkinDocument reopened = MspSkinDocument.open(packageFile);
        assertEquals("Updated", reopened.skin().getMeta().getTitle());
        assertEquals(original.getModules(0), reopened.skin().getModules(0));
        assertArrayEquals(originalImage, reopened.resource("image.png"));
        assertArrayEquals("print('hi')".getBytes(), reopened.resource("script.lua"));
    }

    @Test
    void editsFolderAndKeepsSidecarFiles() throws IOException {
        Path folder = Files.createDirectory(directory.resolve("skin"));
        SkinFile original = SkinFile.newBuilder().setMeta(SkinFile.Meta.newBuilder().setTitle("A")).build();
        Files.write(folder.resolve("info.asm"), original.toByteArray());
        Files.writeString(folder.resolve("info.json"), "derived cache");

        MspSkinDocument document = MspSkinDocument.open(folder.resolve("info.asm"));
        document.save(original.toBuilder().setMeta(original.getMeta().toBuilder().setTitle("B")).build());

        assertEquals("B", MspSkinDocument.open(folder).skin().getMeta().getTitle());
        assertEquals("derived cache", Files.readString(folder.resolve("info.json")));
    }
}
