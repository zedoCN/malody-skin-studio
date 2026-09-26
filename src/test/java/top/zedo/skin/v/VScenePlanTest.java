package top.zedo.skin.v;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VScenePlanTest {
    @TempDir Path directory;

    @Test void planSortsOverlappingPixelsAndProjectsAtRequestedSize() throws Exception {
        Path skinDirectory = Files.createDirectory(directory.resolve("skin copy"));
        // Source order is blue, red; explicit order must paint red first and blue last.
        SkinFile skin = SkinFile.newBuilder().setMeta(SkinFile.Meta.newBuilder().setTitle("scene"))
                .addModules(image("blue.png", 20))
                .addModules(image("red.png", 10))
                .addModules(image("missing.png", 30))
                .addModules(image("red.png", 40).toBuilder()
                        .addAnimations(SkinFile.ModuleAnimation.getDefaultInstance()))
                .build();
        Files.write(skinDirectory.resolve("info.asm"), skin.toByteArray());
        writeColor(skinDirectory, "blue.png", 0xff0000ff);
        writeColor(skinDirectory, "red.png", 0xffff0000);
        MspSkinDocument document = MspSkinDocument.open(skinDirectory);
        VScenePlan plan = VScenePlan.build(document, skin, 1,
                new VSceneLayout.SceneContext(200, 100, VSceneLayout.Platform.WINDOWS), 200, 100);

        assertEquals(2, plan.items().size());
        assertEquals(1, plan.items().get(0).index());
        assertEquals(0, plan.items().get(1).index());
        assertEquals(0xffff0000, plan.items().get(0).image().getPixelReader().getArgb(0, 0));
        assertEquals(0xff0000ff, plan.items().get(1).image().getPixelReader().getArgb(0, 0));
        for (VScenePlan.Item item : plan.items()) {
            VSceneLayout.Placement placement = item.placement();
            assertEquals(0, placement.left());
            assertEquals(0, placement.top());
            assertEquals(100, placement.width());
            assertEquals(100, placement.height());
        }
        assertTrue(plan.status().contains("显示 2 个静态图片"));
        assertTrue(plan.status().contains("跳过 1 个动态/特殊模块"));
        assertTrue(plan.status().contains("资源缺失或无法解码 1 个"));

        Path output = directory.resolve("scene output.png");
        assertTrue(skinDirectory.toUri().toString().contains("%20"));
        VSkinSnapshot.run(new String[]{"--snapshot-v", skinDirectory.toUri().toString(),
                output.toUri().toString(), "1", "windows", "200x100"});
        BufferedImage png = ImageIO.read(output.toFile());
        assertEquals(200, png.getWidth());
        assertEquals(100, png.getHeight());
        assertEquals(0xff0000ff, png.getRGB(50, 50), "较高 order 的蓝图应覆盖红图");
        assertEquals(0, png.getRGB(150, 50), "未覆盖区域应透明");
    }

    private static SkinFile.Module image(String file, int order) {
        return SkinFile.Module.newBuilder().setType(VSceneLayout.CUSTOM_IMAGE).setUsage(99)
                .setParam(SkinFile.ModuleParam.newBuilder().setLayer(1).setOrder(order).setAlpha(100)
                        .setPivot(SkinFile.ModuleParamAnchor.LeftTop)
                        .setXu(SkinFile.ModuleParamUnit.Percent).setYu(SkinFile.ModuleParamUnit.Percent)
                        .setX(0).setY(100))
                .setImage(SkinFile.ModuleParamImage.newBuilder().setFile(file)
                        .setWu(SkinFile.ModuleParamUnit.Percent).setHu(SkinFile.ModuleParamUnit.Percent)
                        .setWidth(50).setHeight(100))
                .build();
    }

    private void writeColor(Path skinDirectory, String filename, int argb) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) image.setRGB(x, y, argb);
        ImageIO.write(image, "png", skinDirectory.resolve(filename).toFile());
    }
}
