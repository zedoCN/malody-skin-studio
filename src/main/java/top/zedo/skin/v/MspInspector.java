package top.zedo.skin.v;

import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/** Read-only command line probe for a V skin package or folder. */
public final class MspInspector {
    private MspInspector() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) throw new IllegalArgumentException("用法: --inspect-v [--layout] <skin.msp|info.asm|skin-directory>");
        boolean layout = args[0].equals("--layout");
        if (layout && args.length == 1) throw new IllegalArgumentException("--layout 后缺少皮肤路径");
        // javafx:run splits -Djavafx.args at spaces, including spaces inside a path.
        String filename = String.join(" ", layout ? Arrays.copyOfRange(args, 1, args.length) : args);
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
        if (!layout) {
            for (int i = 0; i < skin.getModulesCount(); i++) {
                SkinFile.Module module = skin.getModules(i);
                System.out.printf("%d: usage=%d type=%d name=%s image=%s%n", i,
                        module.getUsage(), module.getType(), module.hasMeta() ? module.getMeta().getDesc() : "",
                        module.hasImage() ? module.getImage().getFile() : "");
            }
        }
        if (layout) printLayout(document);
    }

    private static void printLayout(MspSkinDocument document) throws IOException {
        int supported = 0, projectable = 0;
        System.out.println("静态自定义图片布局 · 参考画布 1920×1080 · 非游戏运行画面");
        for (int i = 0; i < document.skin().getModulesCount(); i++) {
            SkinFile.Module module = document.skin().getModules(i);
            if (!VSceneLayout.supports(module)) continue;
            supported++;
            String file = module.getImage().getFile();
            byte[] data;
            try { data = document.resource(file); }
            catch (IOException error) { System.out.printf("%d: %s: %s%n", i, file, error.getMessage()); continue; }
            if (data == null) { System.out.printf("%d: %s: 资源缺失%n", i, file); continue; }
            BufferedImage image;
            try { image = ImageIO.read(new ByteArrayInputStream(data)); }
            catch (IOException error) { System.out.printf("%d: %s: 解码失败: %s%n", i, file, error.getMessage()); continue; }
            if (image == null) { System.out.printf("%d: %s: 无法解码%n", i, file); continue; }
            try {
                VSceneLayout.Placement at = VSceneLayout.project(module, 1920, 1080,
                        image.getWidth(), image.getHeight());
                System.out.printf("%d: layer=%d order=%d file=%s left=%.2f top=%.2f width=%.2f height=%.2f%n",
                        i, module.getParam().getLayer(), module.getParam().getOrder(), file,
                        at.left(), at.top(), at.width(), at.height());
                projectable++;
            } catch (IllegalArgumentException error) {
                System.out.printf("%d: %s: %s%n", i, file, error.getMessage());
            }
        }
        System.out.printf("可分析静态图片 %d / %d，其他动态或特殊模块不参与布局。%n",
                projectable, document.skin().getModulesCount());
        if (supported != projectable) System.out.printf("其中 %d 个图片无法投影。%n", supported - projectable);
    }
}
