package top.zedo.skin.v;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VSceneLayoutTest {
    private static SkinFile.Module.Builder image() {
        return SkinFile.Module.newBuilder().setType(VSceneLayout.CUSTOM_IMAGE).setUsage(99)
                .setParam(SkinFile.ModuleParam.newBuilder().setAlpha(100)
                        .setLayer(4).setPivot(SkinFile.ModuleParamAnchor.Middle))
                .setImage(SkinFile.ModuleParamImage.newBuilder().setFile("a.png")
                        .setWidth(20).setHeight(10));
    }

    private static SkinFile.Module.Builder imageOnLayer(int layer) {
        SkinFile.Module.Builder result = image();
        result.getParamBuilder().setLayer(layer);
        return result;
    }

    @Test void percentPositionAndPivotUseBottomLeftCoordinates() {
        SkinFile.Module module = image().setParam(SkinFile.ModuleParam.newBuilder()
                .setLayer(4).setAlpha(100).setPivot(SkinFile.ModuleParamAnchor.Middle)
                .setX(50).setY(50).setDx(10).setDy(-10)).build();
        VSceneLayout.Placement p = VSceneLayout.project(module, 640, 360, 400, 200);
        assertEquals(320 + 64 - 64, p.left(), 0.001);
        assertEquals(180 + 36 - 18, p.top(), 0.001);
        assertEquals(128, p.width(), 0.001);
        assertEquals(36, p.height(), 0.001);
    }

    @Test void unitPositionAndSingleDimensionPreserveImageRatio() {
        SkinFile.Module module = image().setParam(SkinFile.ModuleParam.newBuilder()
                .setLayer(4).setAlpha(75).setPivot(SkinFile.ModuleParamAnchor.LeftTop)
                .setXu(SkinFile.ModuleParamUnit.Unit).setYu(SkinFile.ModuleParamUnit.Unit)
                .setDxu(SkinFile.ModuleParamUnit.Unit).setDyu(SkinFile.ModuleParamUnit.Unit)
                .setX(540).setY(270).setDx(10).setDy(5))
                .setImage(SkinFile.ModuleParamImage.newBuilder().setFile("a.png")
                        .setWidth(0).setHeight(100).setHu(SkinFile.ModuleParamUnit.Unit)).build();
        VSceneLayout.Placement p = VSceneLayout.project(module, 640, 360, 200, 100);
        assertEquals(550d / 3, p.left(), 0.001);
        assertEquals(360 - 275d / 3, p.top(), 0.001);
        assertEquals(200d / 3, p.width(), 0.001);
        assertEquals(100d / 3, p.height(), 0.001);
        assertEquals(.75, p.opacity(), 0.001);
    }

    @Test void unsupportedDynamicAndSpecialModulesAreExcluded() {
        assertFalse(VSceneLayout.supports(image().addAnimations(SkinFile.ModuleAnimation.getDefaultInstance()).build()));
        assertFalse(VSceneLayout.supports(image().setType(5002).build()));
        assertFalse(VSceneLayout.supports(image().setImage(SkinFile.ModuleParamImage.newBuilder()
                .setFile("a.png").setWidth(20).setHeight(10).setFlipx(true)).build()));
        assertFalse(VSceneLayout.supports(image().setParam(SkinFile.ModuleParam.newBuilder()
                .setXu(SkinFile.ModuleParamUnit.PX)).build()));
        assertFalse(VSceneLayout.supports(imageOnLayer(2).build()));
    }

    @Test void onlyFullScreenLayersUseReferenceCanvas() {
        assertTrue(VSceneLayout.supports(imageOnLayer(1).build()));
        assertTrue(VSceneLayout.supports(imageOnLayer(4).build()));
        assertFalse(VSceneLayout.supports(imageOnLayer(3).build()));
    }

    @Test void viewportAndPlatformScenesFollowEmiriaConditions() {
        SkinFile.Module module = image()
                .addScenes(SkinFile.ModuleCondition.newBuilder().setSource(3).setValint(1920))
                .addScenes(SkinFile.ModuleCondition.newBuilder().setSource(5).setValdbl(16d / 9))
                .addScenes(SkinFile.ModuleCondition.newBuilder().setSource(6).setValint(3))
                .build();
        VSceneLayout.SceneContext android = VSceneLayout.REFERENCE.withPlatform(VSceneLayout.Platform.ANDROID);
        assertEquals(VSceneLayout.SceneMatch.MATCH, VSceneLayout.sceneMatch(module, android));
        assertTrue(VSceneLayout.supports(module, android));
        assertEquals(VSceneLayout.SceneMatch.MISMATCH,
                VSceneLayout.sceneMatch(module, VSceneLayout.REFERENCE));
        assertFalse(VSceneLayout.supports(module));
        assertEquals(VSceneLayout.SceneMatch.MISMATCH,
                VSceneLayout.sceneMatch(module, new VSceneLayout.SceneContext(1080, 1920,
                        VSceneLayout.Platform.ANDROID)));
        assertEquals(VSceneLayout.SceneMatch.UNKNOWN, VSceneLayout.sceneMatch(
                image().addScenes(SkinFile.ModuleCondition.newBuilder().setSource(8).setValint(30)).build(), android));
    }

    @Test void sceneFlagAndValueTypeDoNotSilentlyMakeModulesVisible() {
        SkinFile.Module largerViewport = image().addScenes(SkinFile.ModuleCondition.newBuilder()
                .setSource(3).setFlag(SkinFile.ModuleCondFlag.Large).setValint(1919)).build();
        assertEquals(VSceneLayout.SceneMatch.MATCH,
                VSceneLayout.sceneMatch(largerViewport, VSceneLayout.REFERENCE));
        SkinFile.Module wrongValueType = image().addScenes(SkinFile.ModuleCondition.newBuilder()
                .setSource(3).setValdbl(1920)).build();
        assertEquals(VSceneLayout.SceneMatch.UNKNOWN,
                VSceneLayout.sceneMatch(wrongValueType, VSceneLayout.REFERENCE));
        SkinFile.Module ratioOccur = image().addScenes(SkinFile.ModuleCondition.newBuilder()
                .setSource(5).setFlag(SkinFile.ModuleCondFlag.Occur).setValdbl(16d / 9)).build();
        assertEquals(VSceneLayout.SceneMatch.MISMATCH,
                VSceneLayout.sceneMatch(ratioOccur, VSceneLayout.REFERENCE));
    }

    @Test void draggingPreservesAnchorAndMovesProjectedImageByThePointerDelta() {
        for (SkinFile.ModuleParamUnit unit : new SkinFile.ModuleParamUnit[]{
                SkinFile.ModuleParamUnit.Percent, SkinFile.ModuleParamUnit.Unit}) {
            SkinFile.Module original = image().setParam(SkinFile.ModuleParam.newBuilder()
                    .setLayer(4).setAlpha(100).setPivot(SkinFile.ModuleParamAnchor.Middle)
                    .setX(50).setY(50).setDx(10).setDy(5).setDxu(unit).setDyu(unit)).build();
            VSkinEditModel model = new VSkinEditModel(SkinFile.newBuilder().addModules(original).build());
            VSceneLayout.Placement before = VSceneLayout.project(original, 640, 360, 200, 100);
            VSceneLayout.Offsets moved = VSceneLayout.movedOffsets(original, VSceneLayout.REFERENCE,
                    640, 360, 64, -36);
            model.updateModule(0, model.fields(0).withOffsets(moved.dx(), moved.dy()));
            SkinFile.Module afterModule = model.module(0);
            VSceneLayout.Placement after = VSceneLayout.project(afterModule, 640, 360, 200, 100);
            assertEquals(64, after.left() - before.left(), 0.001);
            assertEquals(-36, after.top() - before.top(), 0.001);
            assertEquals(original.getParam().getX(), afterModule.getParam().getX());
            assertEquals(original.getParam().getY(), afterModule.getParam().getY());
            assertEquals(original.getImage(), afterModule.getImage());
        }
    }

    @Test void realPackagesHaveProjectableStaticImages() throws Exception {
        String paths = System.getProperty("malody.v.samples", "");
        Assumptions.assumeFalse(paths.isBlank(), "Pass -Dmalody.v.samples=path1:path2");
        int projected = 0;
        for (String path : paths.split(":")) {
            MspSkinDocument skin = MspSkinDocument.open(Path.of(path));
            int supported = 0, visible = 0;
            for (SkinFile.Module module : skin.skin().getModulesList()) {
                if (!VSceneLayout.supports(module)) continue;
                supported++;
                byte[] data = skin.resource(module.getImage().getFile());
                if (data == null) continue;
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                if (image == null) continue;
                VSceneLayout.Placement placement = VSceneLayout.project(module, 640, 360,
                        image.getWidth(), image.getHeight());
                assertTrue(Double.isFinite(placement.left()));
                assertTrue(Double.isFinite(placement.top()));
                visible++;
                projected++;
            }
            System.out.printf("V 静态布局样本 %s: 模块=%d 支持=%d 可解码=%d%n",
                    skin.path().getFileName(), skin.skin().getModulesCount(), supported, visible);
        }
        assertTrue(projected > 0, "真实样本应包含可投影静态图片");
    }
}
