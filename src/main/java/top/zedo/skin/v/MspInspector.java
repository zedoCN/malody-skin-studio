package top.zedo.skin.v;

import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.IOException;
import java.nio.file.Path;

/** Read-only command line probe for a V skin package or folder. */
public final class MspInspector {
    private MspInspector() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("用法: --inspect-v <skin.msp|info.asm|skin-directory>");
        MspSkinDocument document = MspSkinDocument.open(Path.of(args[0]));
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
