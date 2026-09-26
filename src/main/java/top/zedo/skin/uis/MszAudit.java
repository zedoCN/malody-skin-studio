package top.zedo.skin.uis;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;

/** Read-only package and script probe for 4.x MSZ samples. */
public final class MszAudit {
    private MszAudit() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) throw new IllegalArgumentException("用法: --audit-msz <skin.msz>");
        // javafx:run splits -Djavafx.args at spaces, including spaces inside a path.
        String filename = String.join(" ", args);
        if (filename.length() >= 2 && filename.startsWith("\"") && filename.endsWith("\"")) {
            filename = filename.substring(1, filename.length() - 1);
        }
        try (MszWorkspace workspace = MszWorkspace.open(Path.of(filename))) {
            System.out.println("MSZ: " + workspace.archive() + ", " + workspace.entryCount()
                    + " 条目, " + workspace.scriptEntries().size() + " 个 MUI 脚本");
            int failed = 0;
            for (String script : workspace.scriptEntries()) {
                Path path = workspace.scriptPath(script);
                try {
                    UISSkin skin = new UISSkin(path, new ExpressionCalculator(1280, 720, 720));
                    MuiSkinLoader.Result result = new MuiSkinLoader(skin, new HashMap<>()).load(path);
                    System.out.println("  " + script + ": " + result.components().size() + " 组件");
                } catch (IOException | RuntimeException error) {
                    failed++;
                    System.out.println("  " + script + ": 解析失败: " + error.getMessage());
                }
            }
            if (failed > 0) throw new IOException("有 " + failed + " 个 MSZ 内 MUI 脚本解析失败");
        }
    }
}
