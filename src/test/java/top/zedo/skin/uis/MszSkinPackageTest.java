package top.zedo.skin.uis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

final class MszSkinPackageTest {
    @TempDir Path temp;

    @Test
    void savePreservesResourcesAndRepresentableMetadata() throws Exception {
        Path archive = temp.resolve("skin.msz");
        byte[] image = {0, 1, 2, 3, -1};
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            out.setComment("package comment");
            write(out, "script.mui", "@version 4.3.7\n".getBytes(StandardCharsets.UTF_8), ZipEntry.DEFLATED);
            write(out, "images/", new byte[0], ZipEntry.STORED);
            write(out, "images/key.png", image, ZipEntry.STORED);
        }
        byte[] original = Files.readAllBytes(archive);
        MszSkinPackage packageFile = MszSkinPackage.open(archive);
        assertEquals("script.mui", packageFile.mainScript());
        assertEquals(3, packageFile.entries().size());
        assertArrayEquals(image, packageFile.readResource("images/key.png"));
        assertNull(packageFile.readResource("missing.png"));
        packageFile.saveScript("script.mui", packageFile.readScript("script.mui"));
        assertArrayEquals(original, Files.readAllBytes(archive));

        byte[] edited = "@version 4.3.7\n@unit 720\n".getBytes(StandardCharsets.UTF_8);
        packageFile.saveScript("script.mui", edited);
        assertArrayEquals(edited, MszSkinPackage.open(archive).readScript("script.mui"));
        try (ZipFile before = new ZipFile(writeCopy(original).toFile());
             ZipFile after = new ZipFile(archive.toFile())) {
            assertEquals(before.getComment(), after.getComment());
            Enumeration<? extends ZipEntry> oldEntries = before.entries();
            Enumeration<? extends ZipEntry> newEntries = after.entries();
            while (oldEntries.hasMoreElements()) {
                ZipEntry old = oldEntries.nextElement();
                ZipEntry now = newEntries.nextElement();
                assertEquals(old.getName(), now.getName());
                assertEquals(old.getMethod(), now.getMethod());
                assertEquals(old.getTime(), now.getTime());
                assertEquals(old.getComment(), now.getComment());
                assertArrayEquals(old.getExtra(), now.getExtra());
                if (!old.getName().endsWith(".mui")) {
                    assertArrayEquals(before.getInputStream(old).readAllBytes(),
                            after.getInputStream(now).readAllBytes());
                }
            }
            assertFalse(newEntries.hasMoreElements());
        }
    }

    @Test
    void rejectsAmbiguousAndUnsafeNames() throws Exception {
        for (String unsafe : new String[]{"../escape.mui", "/absolute.mui", "a\\b.mui",
                "a/./b.mui", "C:/script.mui"}) {
            Path archive = temp.resolve("unsafe-" + Math.abs(unsafe.hashCode()) + ".msz");
            archive(archive, Map.of(unsafe, new byte[]{1}));
            assertThrows(IOException.class, () -> MszSkinPackage.open(archive), unsafe);
        }
        Path duplicate = temp.resolve("duplicate.msz");
        archive(duplicate, Map.of("Script.mui", new byte[]{1}, "script.mui", new byte[]{2}));
        assertThrows(IOException.class, () -> MszSkinPackage.open(duplicate));
        Path collision = temp.resolve("collision.msz");
        archive(collision, Map.of("script.mui", new byte[]{1}, "foo", new byte[]{2}, "foo/bar", new byte[]{3}));
        assertThrows(IOException.class, () -> MszSkinPackage.open(collision));
        Path oversized = temp.resolve("oversized.msz");
        archive(oversized, Map.of("script.mui", new byte[16 * 1024 * 1024 + 1]));
        assertThrows(IOException.class, () -> MszSkinPackage.open(oversized));
    }

    @Test
    void multipleScriptsRequireExplicitSelectionAndStaleSourceCannotBeSaved() throws Exception {
        Path archive = temp.resolve("several.msz");
        archive(archive, Map.of("a.mui", new byte[]{1}, "b.mui", new byte[]{2}));
        MszSkinPackage packageFile = MszSkinPackage.open(archive);
        assertThrows(IOException.class, packageFile::mainScript);
        packageFile.saveScript("a.mui", new byte[]{3});
        assertArrayEquals(new byte[]{2}, packageFile.readScript("b.mui"));
        Files.write(archive, new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> packageFile.saveScript("a.mui", new byte[]{4}));
    }

    @Test
    void real437PackageRoundTripWhenSampleIsAvailable() throws Exception {
        Path sample = Path.of("/tmp/malody-437a/Thirteen-Malody-4k-5.msz");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(sample));
        Path copy = temp.resolve("real.msz");
        Files.copy(sample, copy);
        MszSkinPackage packageFile = MszSkinPackage.open(copy);
        assertEquals("script-key-4K.mui", packageFile.mainScript());
        byte[] original = Files.readAllBytes(copy);
        byte[] script = packageFile.readScript(packageFile.mainScript());
        packageFile.saveScript(packageFile.mainScript(), script);
        assertArrayEquals(original, Files.readAllBytes(copy));
        byte[] edited = Arrays.copyOf(script, script.length + 1);
        edited[edited.length - 1] = '\n';
        packageFile.saveScript(packageFile.mainScript(), edited);
        try (ZipFile before = new ZipFile(sample.toFile()); ZipFile after = new ZipFile(copy.toFile())) {
            Enumeration<? extends ZipEntry> oldEntries = before.entries();
            Enumeration<? extends ZipEntry> newEntries = after.entries();
            while (oldEntries.hasMoreElements()) {
                ZipEntry old = oldEntries.nextElement();
                ZipEntry now = newEntries.nextElement();
                assertEquals(old.getName(), now.getName());
                assertEquals(old.getTime(), now.getTime());
                assertEquals(old.getMethod(), now.getMethod());
                assertArrayEquals(old.getExtra(), now.getExtra());
                if (!old.getName().endsWith(".mui")) {
                    assertArrayEquals(before.getInputStream(old).readAllBytes(),
                            after.getInputStream(now).readAllBytes());
                }
            }
        }
        assertArrayEquals(edited, MszSkinPackage.open(copy).readScript(packageFile.mainScript()));
    }

    private Path writeCopy(byte[] original) throws IOException {
        Path path = temp.resolve("before.msz");
        Files.write(path, original);
        return path;
    }

    private static void archive(Path path, Map<String, byte[]> files) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                write(out, entry.getKey(), entry.getValue(), ZipEntry.DEFLATED);
            }
        }
    }

    private static void write(ZipOutputStream out, String name, byte[] bytes, int method) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(method);
        entry.setTime(1_700_000_000_000L);
        entry.setComment("entry comment");
        entry.setExtra(new byte[]{0x34, 0x12, 0x02, 0x00, 0x05, 0x06});
        if (method == ZipEntry.STORED) {
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(bytes);
            entry.setSize(bytes.length);
            entry.setCrc(crc.getValue());
        }
        out.putNextEntry(entry);
        out.write(bytes);
        out.closeEntry();
    }
}
