package top.zedo.skin.v;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import com.google.protobuf.UnknownFieldSet;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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

    @Test
    void rejectsExternalPackageChangeWithoutOverwritingIt() throws IOException {
        Path packageFile = directory.resolve("external.msp");
        writePackage(packageFile, "Original");
        MspSkinDocument document = MspSkinDocument.open(packageFile);
        writePackage(packageFile, "External");
        byte[] external = Files.readAllBytes(packageFile);

        IOException error = assertThrows(IOException.class, () -> document.save(document.skin().toBuilder()
                .setMeta(document.skin().getMeta().toBuilder().setTitle("Editor")).build()));
        assertTrue(error.getMessage().contains("其他程序修改"));
        assertArrayEquals(external, Files.readAllBytes(packageFile));
        assertEquals("External", MspSkinDocument.open(packageFile).skin().getMeta().getTitle());
    }

    @Test
    void rejectsExternalFolderChangeAndAllowsRepeatedOwnSaves() throws IOException {
        Path folder = Files.createDirectory(directory.resolve("external-folder"));
        Path asm = folder.resolve("info.asm");
        SkinFile original = SkinFile.newBuilder().setMeta(SkinFile.Meta.newBuilder().setTitle("Original")).build();
        Files.write(asm, original.toByteArray());
        boolean posix = Files.getFileStore(asm).supportsFileAttributeView("posix");
        Set<PosixFilePermission> permissions = posix ? Files.getPosixFilePermissions(asm) : null;
        MspSkinDocument document = MspSkinDocument.open(folder);
        document.save(original.toBuilder().setMeta(original.getMeta().toBuilder().setTitle("First")).build());
        if (posix) assertEquals(permissions, Files.getPosixFilePermissions(asm));
        document.save(document.skin().toBuilder()
                .setMeta(document.skin().getMeta().toBuilder().setTitle("Second")).build());
        assertEquals("Second", MspSkinDocument.open(folder).skin().getMeta().getTitle());

        SkinFile external = original.toBuilder().setMeta(original.getMeta().toBuilder()
                .setTitle("External")).build();
        Files.write(asm, external.toByteArray());
        assertThrows(IOException.class, () -> document.save(document.skin().toBuilder()
                .setMeta(document.skin().getMeta().toBuilder().setTitle("Third")).build()));
        assertArrayEquals(external.toByteArray(), Files.readAllBytes(asm));
    }

    private static void writePackage(Path file, String title) throws IOException {
        SkinFile skin = SkinFile.newBuilder().setMeta(SkinFile.Meta.newBuilder().setTitle(title)).build();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("info.asm"));
            skin.writeTo(zip);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("asset.png"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }
    }

    @Test
    void preservesUnknownFieldsAndZipEntryMetadata() throws IOException {
        Path packageFile = directory.resolve("unknown.msp");
        UnknownFieldSet unknown = UnknownFieldSet.newBuilder()
                .addField(100, UnknownFieldSet.Field.newBuilder().addVarint(12345).build()).build();
        SkinFile original = SkinFile.newBuilder()
                .setMeta(SkinFile.Meta.newBuilder().setTitle("Before").setUnknownFields(unknown))
                .addModules(SkinFile.Module.newBuilder().setMeta(SkinFile.ModuleMeta.newBuilder()
                        .setDesc("Keep").setUnknownFields(unknown)).setUnknownFields(unknown))
                .setUnknownFields(unknown).build();
        byte[] asset = {10, 20, 30};
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(packageFile))) {
            zip.setComment("archive comment");
            zip.putNextEntry(new ZipEntry("info.asm"));
            original.writeTo(zip);
            zip.closeEntry();
            ZipEntry stored = new ZipEntry("asset.bin");
            stored.setMethod(ZipEntry.STORED);
            stored.setSize(asset.length);
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(asset);
            stored.setCrc(crc.getValue());
            stored.setComment("asset comment");
            stored.setExtra(new byte[]{(byte) 0xfe, (byte) 0xca, 1, 0, 42});
            zip.putNextEntry(stored);
            zip.write(asset);
            zip.closeEntry();
        }
        MspSkinDocument document = MspSkinDocument.open(packageFile);
        byte[] beforeNoOp = Files.readAllBytes(packageFile);
        assertThrows(IOException.class, () -> document.save(document.skin().toBuilder()
                .setMeta(document.skin().getMeta().toBuilder().setTitle(" ")).build()));
        assertArrayEquals(beforeNoOp, Files.readAllBytes(packageFile));
        document.save(document.skin());
        assertArrayEquals(beforeNoOp, Files.readAllBytes(packageFile));
        document.save(document.skin().toBuilder()
                .setMeta(document.skin().getMeta().toBuilder().setTitle("After")).build());

        SkinFile saved = MspSkinDocument.open(packageFile).skin();
        assertEquals("After", saved.getMeta().getTitle());
        assertEquals(unknown, saved.getUnknownFields());
        assertEquals(unknown, saved.getMeta().getUnknownFields());
        assertEquals(unknown, saved.getModules(0).getUnknownFields());
        assertEquals(unknown, saved.getModules(0).getMeta().getUnknownFields());
        try (ZipFile zip = new ZipFile(packageFile.toFile())) {
            assertEquals("archive comment", zip.getComment());
            ZipEntry copied = zip.getEntry("asset.bin");
            assertEquals(ZipEntry.STORED, copied.getMethod());
            assertEquals("asset comment", copied.getComment());
            assertArrayEquals(new byte[]{(byte) 0xfe, (byte) 0xca, 1, 0, 42}, copied.getExtra());
            assertArrayEquals(asset, zip.getInputStream(copied).readAllBytes());
        }
    }

    @Test
    void roundTripsRealPackagesWhenProvided() throws IOException {
        String paths = System.getProperty("malody.v.samples", "");
        Assumptions.assumeFalse(paths.isBlank(), "Pass -Dmalody.v.samples=path1:path2 for local sample verification");
        int index = 0;
        for (String filename : paths.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path source = Path.of(filename);
            Path target = directory.resolve("sample-" + index++ + ".msp");
            Files.copy(source, target);
            MspSkinDocument document = MspSkinDocument.open(target);
            SkinFile original = document.skin();
            assertTrue(original.hasMeta());
            assertTrue(original.getModulesCount() > 0);
            byte[] unchanged = Files.readAllBytes(target);
            document.save(original);
            assertArrayEquals(unchanged, Files.readAllBytes(target));
            document.save(original.toBuilder().setMeta(original.getMeta().toBuilder()
                    .setTitle(original.getMeta().getTitle() + " (test)")).build());
            SkinFile saved = MspSkinDocument.open(target).skin();
            assertEquals(original.getMeta().getTitle() + " (test)", saved.getMeta().getTitle());
            assertEquals(original.getModulesList(), saved.getModulesList());
            assertEquals(original.getUnknownFields(), saved.getUnknownFields());
            assertEquals(original.getMeta().getUnknownFields(), saved.getMeta().getUnknownFields());
            try (ZipFile input = new ZipFile(source.toFile()); ZipFile output = new ZipFile(target.toFile())) {
                assertEquals(input.size(), output.size());
                var entries = input.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    ZipEntry copy = output.getEntry(entry.getName());
                    assertNotNull(copy, entry.getName());
                    if (!entry.getName().endsWith("info.asm")) {
                        assertTrue(Arrays.equals(input.getInputStream(entry).readAllBytes(),
                                output.getInputStream(copy).readAllBytes()), entry.getName());
                    }
                }
            }
        }
    }
}
