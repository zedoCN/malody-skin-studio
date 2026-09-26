package top.zedo.skin.uis;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.text.Normalizer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.zip.CRC32;

/** A bounded, lossless-at-rest editor for the MUI scripts inside a 4.3.7 MSZ package. */
public final class MszSkinPackage {
    private static final long MAX_ARCHIVE_BYTES = 512L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 64 * 1024 * 1024;
    private static final int MAX_SCRIPT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ENTRIES = 4096;

    private final Path path;
    private final List<String> entries;
    private final List<String> scripts;
    private byte[] sourceDigest;

    private MszSkinPackage(Path path, List<String> entries, List<String> scripts, byte[] sourceDigest) {
        this.path = path;
        this.entries = List.copyOf(entries);
        this.scripts = List.copyOf(scripts);
        this.sourceDigest = sourceDigest;
    }

    public static MszSkinPackage open(Path selected) throws IOException {
        Objects.requireNonNull(selected, "selected");
        Path path = selected.toRealPath();
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_ARCHIVE_BYTES) {
            throw new IOException("MSZ 文件过大或不是普通文件: " + path);
        }
        List<String> names = new ArrayList<>();
        List<String> scripts = new ArrayList<>();
        Map<String, Boolean> canonical = new HashMap<>();
        long total = 0;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> items = zip.entries();
            while (items.hasMoreElements()) {
                ZipEntry entry = items.nextElement();
                if (names.size() >= MAX_ENTRIES) throw new IOException("MSZ 条目过多");
                String name = entry.getName();
                validateName(name, entry.isDirectory());
                validateExtra(entry.getExtra());
                String key = canonicalName(name);
                if (canonical.putIfAbsent(key, entry.isDirectory()) != null) {
                    throw new IOException("MSZ 条目名称重复或大小写冲突: " + name);
                }
                if (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED) {
                    throw new IOException("MSZ 使用不支持的压缩方法: " + name);
                }
                long size = entry.getSize();
                if (size < 0 || entry.getCrc() < 0 || size > MAX_ENTRY_BYTES
                        || total > MAX_TOTAL_BYTES - size) {
                    throw new IOException("MSZ 条目解压大小超限: " + name);
                }
                if (entry.isDirectory() && size != 0) throw new IOException("MSZ 目录包含内容: " + name);
                total += size;
                names.add(name);
                if (!entry.isDirectory() && name.toLowerCase(Locale.ROOT).endsWith(".mui")) {
                    if (size > MAX_SCRIPT_BYTES) throw new IOException("MUI 脚本过大: " + name);
                    scripts.add(name);
                }
            }
        }
        for (Map.Entry<String, Boolean> item : canonical.entrySet()) {
            String name = item.getKey();
            int slash = name.indexOf('/');
            while (slash >= 0) {
                if (Boolean.FALSE.equals(canonical.get(name.substring(0, slash)))) {
                    throw new IOException("MSZ 文件与目录路径冲突: " + name);
                }
                slash = name.indexOf('/', slash + 1);
            }
        }
        if (scripts.isEmpty()) throw new IOException("MSZ 中没有 .mui 脚本: " + path);
        return new MszSkinPackage(path, names, scripts, digest(path));
    }

    public Path path() { return path; }
    public List<String> entries() { return entries; }
    public List<String> scriptEntries() { return scripts; }

    /** A package with several scripts requires an explicit selection. */
    public String mainScript() throws IOException {
        if (scripts.size() != 1) throw new IOException("MSZ 包含多个 .mui 脚本，请指定条目");
        return scripts.getFirst();
    }

    public synchronized byte[] readScript(String name) throws IOException {
        if (!scripts.contains(name)) throw new IOException("MSZ 中没有脚本: " + name);
        return read(name, MAX_SCRIPT_BYTES);
    }

    /** Returns null when the safe name does not exist; callers may also read an included MUI here. */
    public synchronized byte[] readResource(String name) throws IOException {
        validateName(name, false);
        if (!entries.contains(name)) return null;
        return read(name, MAX_ENTRY_BYTES);
    }

    private byte[] read(String name, int limit) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null || entry.isDirectory()) throw new IOException("MSZ 条目已消失: " + name);
            try (InputStream input = zip.getInputStream(entry)) {
                byte[] contents = readBounded(input, limit);
                CRC32 crc = new CRC32();
                crc.update(contents);
                verifyContents(entry, contents.length, crc.getValue());
                return contents;
            }
        }
    }

    /** Atomically replaces one selected script; identical bytes leave the package untouched. */
    public synchronized void saveScript(String name, byte[] updated) throws IOException {
        if (!scripts.contains(name)) throw new IOException("MSZ 中没有脚本: " + name);
        Objects.requireNonNull(updated, "updated");
        if (updated.length > MAX_SCRIPT_BYTES) throw new IOException("MUI 脚本过大: " + name);
        if (!Arrays.equals(sourceDigest, digest(path))) {
            throw new IOException("MSZ 已被其他程序修改，请重新打开: " + path);
        }
        if (Arrays.equals(readScript(name), updated)) return;
        Path temporary = Files.createTempFile(path.getParent(), ".msz-save-", ".tmp");
        try {
            try (ZipFile source = new ZipFile(path.toFile());
                 OutputStream output = Files.newOutputStream(temporary);
                 ZipOutputStream target = new ZipOutputStream(output)) {
                if (source.getComment() != null) target.setComment(source.getComment());
                Enumeration<? extends ZipEntry> items = source.entries();
                boolean replaced = false;
                while (items.hasMoreElements()) {
                    ZipEntry old = items.nextElement();
                    boolean editing = old.getName().equals(name);
                    ZipEntry copy = copyEntry(old, editing ? updated : null);
                    target.putNextEntry(copy);
                    if (editing) {
                        target.write(updated);
                        replaced = true;
                    } else if (!old.isDirectory()) {
                        try (InputStream input = source.getInputStream(old)) {
                            copyBounded(input, target, old, MAX_ENTRY_BYTES);
                        }
                    }
                    target.closeEntry();
                }
                if (!replaced) throw new IOException("MSZ 脚本已消失: " + name);
            }
            if (!Arrays.equals(sourceDigest, digest(path))) {
                throw new IOException("MSZ 保存期间被其他程序修改，请重新打开: " + path);
            }
            try {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
                Files.setPosixFilePermissions(temporary, permissions);
            } catch (UnsupportedOperationException ignored) {
                // Filesystems without POSIX permissions retain their default replacement behavior.
            }
            byte[] replacementDigest = digest(temporary);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            sourceDigest = replacementDigest;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static ZipEntry copyEntry(ZipEntry old, byte[] updated) throws IOException {
        ZipEntry copy = new ZipEntry(old.getName());
        copy.setMethod(old.getMethod());
        if (old.getTime() >= 0) copy.setTime(old.getTime());
        if (old.getComment() != null) copy.setComment(old.getComment());
        if (old.getExtra() != null) copy.setExtra(old.getExtra());
        if (old.getMethod() == ZipEntry.STORED) {
            if (updated == null) {
                copy.setSize(old.getSize());
                copy.setCrc(old.getCrc());
            } else {
                CRC32 crc = new CRC32();
                crc.update(updated);
                copy.setSize(updated.length);
                copy.setCrc(crc.getValue());
            }
        }
        return copy;
    }

    private static void validateExtra(byte[] extra) throws IOException {
        if (extra == null) return;
        for (int i = 0; i < extra.length;) {
            if (i + 4 > extra.length) throw new IOException("无效的 ZIP 扩展字段");
            int id = (extra[i] & 255) | ((extra[i + 1] & 255) << 8);
            int size = (extra[i + 2] & 255) | ((extra[i + 3] & 255) << 8);
            if (i + 4 + size > extra.length) throw new IOException("无效的 ZIP 扩展字段");
            if (id == 1) throw new IOException("暂不支持 ZIP64 扩展字段");
            i += 4 + size;
        }
    }

    private static void validateName(String name, boolean directory) throws IOException {
        if (name == null || name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0 || name.indexOf(':') >= 0
                || name.indexOf('\uFFFD') >= 0 || directory != name.endsWith("/")) {
            throw new IOException("MSZ 条目路径无效: " + name);
        }
        String body = directory ? name.substring(0, name.length() - 1) : name;
        if (body.isEmpty()) throw new IOException("MSZ 条目路径无效: " + name);
        for (String segment : body.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("MSZ 条目路径无效: " + name);
            }
            for (int i = 0; i < segment.length(); i++) {
                if (Character.isISOControl(segment.charAt(i))) {
                    throw new IOException("MSZ 条目路径无效: " + name);
                }
            }
        }
    }

    private static String canonicalName(String name) {
        String body = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        return Normalizer.normalize(body, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private static byte[] readBounded(InputStream input, int limit) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit) throw new IOException("MSZ 条目解压大小超限");
        return bytes;
    }

    private static void copyBounded(InputStream input, OutputStream output, ZipEntry entry, int limit)
            throws IOException {
        byte[] buffer = new byte[8192];
        long count = 0;
        CRC32 crc = new CRC32();
        int read;
        while ((read = input.read(buffer)) != -1) {
            count += read;
            if (count > limit) throw new IOException("MSZ 条目解压大小超限");
            crc.update(buffer, 0, read);
            output.write(buffer, 0, read);
        }
        verifyContents(entry, count, crc.getValue());
    }

    private static void verifyContents(ZipEntry entry, long size, long crc) throws IOException {
        if (entry.getSize() != size || entry.getCrc() != crc) {
            throw new IOException("MSZ 条目大小或 CRC 校验失败: " + entry.getName());
        }
    }

    private static byte[] digest(Path path) throws IOException {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) sha256.update(buffer, 0, read);
            }
            return sha256.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
