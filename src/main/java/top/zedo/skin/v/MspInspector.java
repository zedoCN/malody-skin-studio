package top.zedo.skin.v;

import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.IOException;
import java.nio.file.Path;

/** Read-only command line probe for a V skin package or folder. */
public final class MspInspector {
    private MspInspector() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) throw new IllegalArgumentException("用法: --inspect-v <skin.msp|info.asm|skin-directory>");
        // javafx:run splits -Djavafx.args at spaces, including spaces inside a path.
        String filename = String.join(" ", args);
        if (filename.length() >= 2 && filename.startsWith("\"") && filename.endsWith("\"")) {
            filename = filename.substring(1, filename.length() - 1);
        }
        MspSkinDocument document = MspSkinDocument.open(Path.of(filename));
        SkinFile skin = document.skin();
        System.out.println("文件: " + document.path());
        System.out.println("标题: " + skin.getMeta().getTitle());
        System.out.println("作者: " + skin.getMeta().getCreator());
        System.out.println("模式: " + skin.getMeta().getMode());
        System.out.println("组件: " + skin.getModulesCount());
        for (int i = 0; i < skin.getModulesCount(); i++) {
            SkinFile.Module module = skin.getModules(i);
            System.out.printf("%d: usage=%d type=%d name=%s image=%s%n", i,
                    module.getUsage(), module.getType(), module.hasMeta() ? module.getMeta().getDesc() : "",
                    module.hasImage() ? module.getImage().getFile() : "");
        }
    }
}
