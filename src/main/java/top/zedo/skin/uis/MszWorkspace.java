package top.zedo.skin.uis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Extracts a checked MSZ into an isolated directory for the existing MUI loader. */
public final class MszWorkspace implements AutoCloseable {
    private final MszSkinPackage skin;
    private final Path directory;
    private final Set<String> pending = new LinkedHashSet<>();
    private boolean closed;

    private MszWorkspace(MszSkinPackage skin, Path directory) {
        this.skin = skin;
        this.directory = directory;
    }

    public static MszWorkspace open(Path archive) throws IOException {
        MszSkinPackage skin = MszSkinPackage.open(archive);
        Path directory = Files.createTempDirectory("malody-msz-").toRealPath();
        try {
            for (String name : skin.entries()) {
                Path destination = directory.resolve(name).normalize();
                if (!destination.startsWith(directory)) throw new IOException("MSZ 条目路径越界: " + name);
                if (name.endsWith("/")) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.write(destination, skin.readResource(name));
                }
            }
            return new MszWorkspace(skin, directory);
        } catch (IOException | RuntimeException error) {
            deleteTree(directory);
            throw error;
        }
    }

    public Path archive() { return skin.path(); }
    public Path directory() { return directory; }
    public int entryCount() { return skin.entries().size(); }
    public List<String> scriptEntries() { return skin.scriptEntries(); }

    public Path scriptPath(String entry) throws IOException {
        if (!skin.scriptEntries().contains(entry)) throw new IOException("MSZ 中没有脚本: " + entry);
        return directory.resolve(entry);
    }

    public boolean owns(Path path) {
        return path.toAbsolutePath().normalize().startsWith(directory);
    }

    /** Syncs only scripts; resources in the extracted preview are never silently written to the archive. */
    public synchronized void syncScript(Path path) throws IOException {
        if (closed) throw new IOException("MSZ 工作目录已关闭");
        Path normalized = path.toRealPath();
        if (!normalized.startsWith(directory)) throw new IOException("文件不属于 MSZ 工作目录: " + path);
        String name = directory.relativize(normalized).toString().replace('\\', '/');
        if (!skin.scriptEntries().contains(name)) throw new IOException("当前只支持保存包内 .mui 脚本: " + name);
        pending.add(name);
        try {
            skin.saveScript(name, Files.readAllBytes(normalized));
            pending.remove(name);
        } catch (IOException error) {
            throw new IOException(error.getMessage() + "；未保存脚本保留于 " + normalized, error);
        }
    }

    public synchronized void syncPending() throws IOException {
        for (String name : List.copyOf(pending)) syncScript(directory.resolve(name));
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        syncPending();
        deleteTree(directory);
        closed = true;
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }
}
