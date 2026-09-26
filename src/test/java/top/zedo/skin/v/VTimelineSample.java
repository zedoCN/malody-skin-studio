package top.zedo.skin.v;

import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regenerates the tiny, synthetic ASM timeline sample. */
public final class VTimelineSample {
    private VTimelineSample() { }

    public static void main(String[] args) throws Exception {
        Path output = args.length == 0 ? Path.of("examples/asm/timeline-demo") : Path.of(args[0]);
        Files.createDirectories(output);
        SkinFile.ModuleParam.Builder base = SkinFile.ModuleParam.newBuilder()
                .setLayer(1).setAlpha(100).setPivot(SkinFile.ModuleParamAnchor.LeftBottom)
                .setXu(SkinFile.ModuleParamUnit.Percent).setYu(SkinFile.ModuleParamUnit.Percent)
                .setX(15).setY(62);
        SkinFile.Module moving = SkinFile.Module.newBuilder()
                .setType(VSceneLayout.CUSTOM_IMAGE).setUsage(99)
                .setMeta(SkinFile.ModuleMeta.newBuilder().setDesc("移动的光点"))
                .setParam(base)
                .setImage(SkinFile.ModuleParamImage.newBuilder().setFile("orb.png")
                        .setWu(SkinFile.ModuleParamUnit.Percent)
                        .setHu(SkinFile.ModuleParamUnit.Percent)
                        .setWidth(12).setHeight(22))
                .addAnimations(SkinFile.ModuleAnimation.newBuilder().setType(1)
                        .setStartTime(0).setEndTime(2000).setFromVal0(0).setToVal0(950)
                        .setRepeat(1).setDelay(400).setRepeatType(2).setEase(1))
                .addAnimations(SkinFile.ModuleAnimation.newBuilder().setType(10)
                        .setStartTime(0).setEndTime(2000).setFromVal0(35).setToVal0(100)
                        .setRepeat(1).setDelay(400).setRepeatType(2))
                .build();
        SkinFile skin = SkinFile.newBuilder().setMeta(SkinFile.Meta.newBuilder()
                .setTitle("ASM 时间轴演示").setCreator("Malody Skin Studio"))
                .addModules(moving).build();
        Files.write(output.resolve("info.asm"), skin.toByteArray());

        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(33, 218, 230, 55));
        graphics.fillOval(0, 0, 127, 127);
        graphics.setColor(new Color(37, 220, 233, 190));
        graphics.fillOval(22, 22, 84, 84);
        graphics.setColor(new Color(217, 255, 255, 255));
        graphics.fillOval(48, 48, 32, 32);
        graphics.dispose();
        ImageIO.write(image, "png", output.resolve("orb.png").toFile());
    }
}
