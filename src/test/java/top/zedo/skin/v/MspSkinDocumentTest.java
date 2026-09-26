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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MspSkinDocumentTest {
    @TempDir Path directory;

    @Test
    void hashesRawAsmAndAllLuaInFolderAndPackage() throws IOException {
        SkinFile skin = SkinFile.newBuilder().setMeta(SkinFile.Meta.newBuilder().setTitle("Hash")).build();
        byte[] asm = skin.toByteArray();
        String asmHash = sha256(asm);
        Path folder = Files.createDirectory(directory.resolve("skin-folder"));
        Files.write(folder.resolve("info.asm"), asm);
        assertEquals(asmHash, MspSkinDocument.open(folder).asmSha256());
        assertEquals("None", MspSkinDocument.open(folder).luaHash());

        Files.writeString(folder.resolve("z.lua"), "beta");
        assertEquals("987bcab01b929eb2c07877b224215c92", MspSkinDocument.open(folder).luaHash());
        Path nested = Files.createDirectory(folder.resolve("sub"));
        Files.writeString(nested.resolve("a.lua"), "alpha");
        MspSkinDocument folderDocument = MspSkinDocument.open(folder);
        assertEquals("258acfe6378df63d0bb2c39de25b2e04", folderDocument.luaHash());

        Path archive = directory.resolve("skin.msp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            writeEntry(zip, "skin/z.lua", "beta".getBytes(StandardCharsets.UTF_8));
            writeEntry(zip, "skin/info.asm", asm);
            writeEntry(zip, "skin/sub/a.lua", "alpha".getBytes(StandardCharsets.UTF_8));
            writeEntry(zip, "other/ignored.lua", "ignored".getBytes(StandardCharsets.UTF_8));
        }
        MspSkinDocument packageDocument = MspSkinDocument.open(archive);
        assertEquals(asmHash, packageDocument.asmSha256());
        assertNotEquals(sha256(Files.readAllBytes(archive)), packageDocument.asmSha256());
        assertEquals(folderDocument.luaHash(), packageDocument.luaHash());

        Files.writeString(nested.resolve("a.lua"), "changed");
        assertEquals(asmHash, folderDocument.asmSha256());
        assertNotEquals(packageDocument.luaHash(), folderDocument.luaHash());
        Files.writeString(folder.resolve("info.asm"), "changed");
        assertThrows(IOException.class, folderDocument::asmSha256);
        assertThrows(IOException.class, folderDocument::luaHash);
        Files.write(archive, new byte[]{1, 2, 3});
        assertThrows(IOException.class, packageDocument::asmSha256);
        assertThrows(IOException.class, packageDocument::luaHash);
    }

    @Test
    void rejectsLuaSymlinkOutsideFolderAndOversizedArchiveEntry() throws IOException {
        SkinFile skin = SkinFile.newBuilder().setMeta(SkinFile.Meta.newBuilder().setTitle("Hash")).build();
        Path folder = Files.createDirectory(directory.resolve("folder"));
        Files.write(folder.resolve("info.asm"), skin.toByteArray());
        Path external = Files.writeString(directory.resolve("external.lua"), "outside");
        Files.createSymbolicLink(folder.resolve("outside.lua"), external);
        assertThrows(IOException.class, () -> MspSkinDocument.open(folder).luaHash());

        Path archive = directory.resolve("large.msp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            writeEntry(zip, "info.asm", skin.toByteArray());
            writeEntry(zip, "large.lua", new byte[MspSkinDocument.MAX_LUA_BYTES + 1]);
        }
        assertThrows(IOException.class, () -> MspSkinDocument.open(archive).luaHash());
    }

    @Test
    void rejectsDuplicateLuaArchiveEntries() throws IOException {
        SkinFile skin = SkinFile.newBuilder().setMeta(SkinFile.Meta.newBuilder().setTitle("Hash")).build();
        Path archive = directory.resolve("duplicates.msp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            writeEntry(zip, "info.asm", skin.toByteArray());
            writeEntry(zip, "a.lua", "alpha".getBytes(StandardCharsets.UTF_8));
            writeEntry(zip, "b.lua", "beta".getBytes(StandardCharsets.UTF_8));
        }
        byte[] bytes = Files.readAllBytes(archive);
        byte[] oldName = "b.lua".getBytes(StandardCharsets.US_ASCII);
        byte[] newName = "a.lua".getBytes(StandardCharsets.US_ASCII);
        int replaced = 0;
        for (int i = 0; i <= bytes.length - oldName.length; i++) {
            if (!Arrays.equals(Arrays.copyOfRange(bytes, i, i + oldName.length), oldName)) continue;
            System.arraycopy(newName, 0, bytes, i, newName.length);
            replaced++;
        }
        assertEquals(2, replaced); // Local and central directory names.
        Files.write(archive, bytes);
        assertThrows(IOException.class, () -> MspSkinDocument.open(archive).luaHash());
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }

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
