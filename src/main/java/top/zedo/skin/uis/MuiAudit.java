package top.zedo.skin.uis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

/** Batch parser probe for real MUI skin collections. */
public final class MuiAudit {
    private MuiAudit() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) throw new IllegalArgumentException("用法: --audit-mui <skin.mui|directory>");
        String filename = String.join(" ", args);
        if (filename.length() >= 2 && filename.startsWith("\"") && filename.endsWith("\"")) {
            filename = filename.substring(1, filename.length() - 1);
        }
        Path input = Path.of(filename);
        List<Path> files;
        if (Files.isDirectory(input)) {
            try (Stream<Path> paths = Files.walk(input)) {
                files = paths.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".mui"))
                        .filter(path -> !path.getFileName().toString().startsWith("._"))
                        .sorted().toList();
            }
        } else {
            files = List.of(input);
        }
        int failed = 0;
        int components = 0;
        for (Path file : files) {
            try {
                UISSkin skin = new UISSkin(file, new ExpressionCalculator(1280, 720, 720));
                MuiSkinLoader.Result result = new MuiSkinLoader(skin, new HashMap<>()).load(file);
                components += result.components().size();
            } catch (IOException error) {
                failed++;
                System.out.println("失败: " + file + " - " + error.getMessage());
            }
        }
        System.out.println("MUI 检查: " + files.size() + " 文件, " + (files.size() - failed)
                + " 成功, " + failed + " 失败, " + components + " 组件");
        if (failed > 0) throw new IOException("有 " + failed + " 个 MUI 文件解析失败");
    }
}
