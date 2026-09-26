package top.zedo.skin.v;

import com.google.protobuf.InvalidProtocolBufferException;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** A Malody V skin folder or MSP package. Unedited protobuf fields and package files survive a save. */
public final class MspSkinDocument {
    private static final int MAX_ASM_BYTES = 16 * 1024 * 1024;
    private static final int MAX_PREVIEW_BYTES = 64 * 1024 * 1024;
    static final int MAX_LUA_BYTES = 1024 * 1024;

    private final Path path;
    private final boolean archive;
    private final String entryPrefix;
    private SkinFile skin;
    private byte[] sourceDigest;

    private MspSkinDocument(Path path, boolean archive, String entryPrefix, SkinFile skin,
                            byte[] sourceDigest) {
        this.path = path;
        this.archive = archive;
        this.entryPrefix = entryPrefix;
        this.skin = skin;
        this.sourceDigest = sourceDigest;
    }

    public static MspSkinDocument open(Path selected) throws IOException {
        Path path = selected.toAbsolutePath().normalize();
        if (path.getFileName().toString().equalsIgnoreCase("info.asm")) path = path.getParent();
        path = path.toRealPath();
        if (Files.isDirectory(path)) {
            Path source = path.resolve("info.asm");
            byte[] before = digest(source);
            byte[] data = readBounded(Files.newInputStream(source), MAX_ASM_BYTES);
            SkinFile skin = parse(data);
            if (!Arrays.equals(before, digest(source))) throw new IOException("读取期间 info.asm 已改变: " + source);
            return new MspSkinDocument(path, false, "", skin, before);
        }
        byte[] before = digest(path);
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry asm = null;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry candidate = entries.nextElement();
                if (candidate.getName().equals("info.asm") || candidate.getName().endsWith("/info.asm")) {
                    if (asm != null) throw new IOException("MSP 中包含多个 info.asm: " + path);
                    asm = candidate;
                }
            }
            if (asm == null) throw new IOException("MSP 中缺少 info.asm: " + path);
            byte[] data = readBounded(zip.getInputStream(asm), MAX_ASM_BYTES);
            String prefix = asm.getName().substring(0, asm.getName().length() - "info.asm".length());
            SkinFile skin = parse(data);
            if (!Arrays.equals(before, digest(path))) throw new IOException("读取期间 MSP 已改变: " + path);
            return new MspSkinDocument(path, true, prefix, skin, before);
        }
    }

    private static SkinFile parse(byte[] data) throws IOException {
        if (data.length > MAX_ASM_BYTES) throw new IOException("info.asm 过大");
        try {
            SkinFile result = SkinFile.parseFrom(data);
            if (!result.hasMeta()) throw new IOException("info.asm 缺少皮肤元数据");
            return result;
        } catch (InvalidProtocolBufferException error) {
            throw new IOException("info.asm 不是有效的 Malody V 皮肤", error);
        }
    }

    public Path path() { return path; }
    public SkinFile skin() { return skin; }

    public byte[] resource(String filename) throws IOException {
        return resource(filename, MAX_PREVIEW_BYTES);
    }

    byte[] resource(String filename, int maxBytes) throws IOException {
        String name = safeResourceName(filename);
        if (archive) {
            try (ZipFile zip = new ZipFile(path.toFile())) {
                ZipEntry entry = zip.getEntry(entryPrefix + name);
                if (entry == null || entry.isDirectory()) return null;
                return readBounded(zip.getInputStream(entry), maxBytes);
            }
        }
        Path file = path.resolve(name).normalize();
        if (!file.startsWith(path) || !Files.isRegularFile(file)) return null;
        if (!file.toRealPath().startsWith(path.toRealPath())) throw new IOException("资源路径越界: " + filename);
        if (Files.size(file) > maxBytes) throw new IOException("资源过大: " + name);
        return Files.readAllBytes(file);
    }

    private static String safeResourceName(String filename) throws IOException {
        if (filename == null || filename.isBlank()) throw new IOException("资源名为空");
        String normalized = filename.replace('\\', '/');
        try {
            Path relative = Path.of(normalized).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")
                    || normalized.matches("^[A-Za-z]:/.*")) {
                throw new IOException("资源路径越界: " + filename);
            }
            return relative.toString().replace('\\', '/');
        } catch (InvalidPathException error) {
            throw new IOException("资源路径无效: " + filename, error);
        }
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        try (input) {
            byte[] data = input.readNBytes(maxBytes + 1);
            if (data.length > maxBytes) throw new IOException("皮肤文件过大");
            return data;
        }
    }

    public void save(SkinFile updated) throws IOException {
        save(updated, null, null, null);
    }

    void save(SkinFile updated, String scriptPath, byte[] originalScript, byte[] replacementScript)
            throws IOException {
        Objects.requireNonNull(updated);
        if (!updated.hasMeta()) throw new IOException("不能保存缺少元数据的皮肤");
        if (updated.getMeta().getTitle().isBlank()) throw new IOException("皮肤标题不能为空");
        String scriptEntry = null;
        boolean scriptChanged = false;
        if (scriptPath != null) {
            if (scriptPath.isBlank()) throw new IOException("皮肤未引用 Lua 文件");
            if (!scriptPath.equals(skin.getMeta().getScript())
                    || !scriptPath.equals(updated.getMeta().getScript())) {
                throw new IOException("Lua 引用路径已改变，请重新打开皮肤");
            }
            scriptEntry = safeResourceName(scriptPath);
            if (!scriptPath.replace('\\', '/').equals(scriptEntry)) {
                throw new IOException("Lua 路径不是规范的相对路径: " + scriptPath);
            }
            if (scriptEntry.equals("info.asm")) {
                throw new IOException("Lua 路径与 info.asm 冲突");
            }
            if (originalScript == null || replacementScript == null) {
                throw new IOException("Lua 文件缺失或无法读取: " + scriptPath);
            }
            if (replacementScript.length > MAX_LUA_BYTES) {
                throw new IOException("Lua 文件超过 1 MiB 限制: " + scriptPath);
            }
            if (!archive && Files.isSymbolicLink(path.resolve(scriptEntry))) {
                throw new IOException("Lua 文件是符号链接，请先转换为皮肤目录内的普通文件: " + scriptPath);
            }
            scriptChanged = !Arrays.equals(originalScript, replacementScript);
        }
        Path destination = archive ? path : path.resolve("info.asm");
        requireUnchanged(destination);
        if (scriptEntry != null) requireScriptUnchanged(scriptEntry, originalScript);
        boolean asmChanged = !updated.equals(skin);
        if (!asmChanged && !scriptChanged) return;

        Path temporary = null;
        Path scriptTemporary = null;
        Path scriptBackup = null;
        boolean preserveBackup = false;
        try {
            if (archive) {
                temporary = Files.createTempFile(path.getParent(), ".malody-skin-", ".msp");
                try (ZipFile source = new ZipFile(path.toFile());
                     OutputStream output = Files.newOutputStream(temporary);
                     ZipOutputStream target = new ZipOutputStream(output)) {
                    if (source.getComment() != null) target.setComment(source.getComment());
                    Enumeration<? extends ZipEntry> entries = source.entries();
                    boolean wroteAsm = false;
                    boolean wroteScript = false;
                    while (entries.hasMoreElements()) {
                        ZipEntry old = entries.nextElement();
                        boolean isAsm = old.getName().equals(entryPrefix + "info.asm");
                        boolean isScript = scriptEntry != null && old.getName().equals(entryPrefix + scriptEntry);
                        if (isScript && wroteScript) throw new IOException("MSP 中包含多个 Lua 文件: " + scriptPath);
                        ZipEntry copy = copyEntry(old, (isAsm && asmChanged) || (isScript && scriptChanged));
                        target.putNextEntry(copy);
                        if (isAsm && asmChanged) {
                            updated.writeTo(target);
                        } else if (isScript && scriptChanged) {
                            target.write(replacementScript);
                        } else if (!old.isDirectory()) {
                            try (InputStream input = source.getInputStream(old)) { input.transferTo(target); }
                        }
                        if (isAsm) wroteAsm = true;
                        if (isScript) wroteScript = true;
                        target.closeEntry();
                    }
                    if (!wroteAsm) throw new IOException("MSP 中的 info.asm 已消失");
                    if (scriptEntry != null && !wroteScript) throw new IOException("引用的 Lua 文件不存在: " + scriptPath);
                }
            } else {
                if (asmChanged) {
                    temporary = Files.createTempFile(destination.getParent(), ".malody-skin-", ".asm");
                    Files.write(temporary, updated.toByteArray());
                    copyPermissions(destination, temporary);
                }
                if (scriptChanged) {
                    Path scriptFile = path.resolve(scriptEntry).normalize();
                    scriptTemporary = Files.createTempFile(scriptFile.getParent(), ".malody-lua-", ".lua");
                    Files.write(scriptTemporary, replacementScript);
                    copyPermissions(scriptFile, scriptTemporary);
                }
            }
            requireUnchanged(destination);
            if (scriptEntry != null) requireScriptUnchanged(scriptEntry, originalScript);
            byte[] replacementDigest = temporary == null ? sourceDigest : digest(temporary);
            if (archive) {
                copyPermissions(destination, temporary);
                moveReplacing(temporary, destination);
            } else {
                Path scriptFile = scriptChanged ? path.resolve(scriptEntry).normalize() : null;
                if (scriptChanged && asmChanged) {
                    scriptBackup = Files.createTempFile(scriptFile.getParent(), ".malody-lua-backup-", ".lua");
                    Files.write(scriptBackup, originalScript);
                    copyPermissions(scriptFile, scriptBackup);
                }
                if (scriptChanged) moveReplacing(scriptTemporary, scriptFile);
                if (asmChanged) {
                    try {
                        moveReplacing(temporary, destination);
                    } catch (IOException saveError) {
                        if (scriptBackup != null) {
                            try {
                                moveReplacing(scriptBackup, scriptFile);
                            } catch (IOException rollbackError) {
                                preserveBackup = true;
                                saveError.addSuppressed(rollbackError);
                                throw new IOException("保存 info.asm 失败，Lua 回滚也失败；备份保留在 " + scriptBackup,
                                        saveError);
                            }
                        }
                        throw saveError;
                    }
                }
            }
            skin = updated;
            sourceDigest = archive || asmChanged ? replacementDigest : sourceDigest;
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
            if (scriptTemporary != null) Files.deleteIfExists(scriptTemporary);
            if (scriptBackup != null && !preserveBackup) Files.deleteIfExists(scriptBackup);
        }
    }

    private void requireScriptUnchanged(String scriptEntry, byte[] expected) throws IOException {
        byte[] current;
        try {
            current = resource(scriptEntry, expected.length);
        } catch (IOException error) {
            throw new IOException("Lua 文件已被其他程序修改或无法读取，请重新打开: " + scriptEntry, error);
        }
        if (current == null) throw new IOException("引用的 Lua 文件不存在: " + scriptEntry);
        if (!Arrays.equals(expected, current)) {
            throw new IOException("Lua 文件已被其他程序修改，请重新打开: " + scriptEntry);
        }
    }

    private static void copyPermissions(Path source, Path target) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(source);
            Files.setPosixFilePermissions(target, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Filesystems without POSIX permissions retain their default replacement behavior.
        }
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void requireUnchanged(Path source) throws IOException {
        if (!Arrays.equals(sourceDigest, digest(source))) {
            throw new IOException("皮肤已被其他程序修改，请重新打开: " + source);
        }
    }

    private static byte[] digest(Path file) throws IOException {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) sha.update(buffer, 0, count);
            }
            return sha.digest();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JDK 缺少 SHA-256", error);
        }
    }

    private static ZipEntry copyEntry(ZipEntry old, boolean replaced) {
        ZipEntry copy = new ZipEntry(old.getName());
        if (old.getTime() >= 0) copy.setTime(old.getTime());
        if (old.getLastAccessTime() != null) copy.setLastAccessTime(old.getLastAccessTime());
        if (old.getCreationTime() != null) copy.setCreationTime(old.getCreationTime());
        if (old.getComment() != null) copy.setComment(old.getComment());
        if (!replaced) {
            if (old.getExtra() != null) copy.setExtra(old.getExtra());
            copy.setMethod(old.getMethod());
            if (old.getMethod() == ZipEntry.STORED) {
                copy.setSize(old.getSize());
                copy.setCrc(old.getCrc());
            }
        }
        return copy;
    }
}
