package top.zedo.skin.v;

import org.junit.jupiter.api.Test;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import static org.junit.jupiter.api.Assertions.*;

class VAnimationPreviewTest {
    private static final double EPS = .01;
    private static final VSceneLayout.Placement BASE =
            new VSceneLayout.Placement(100, 50, 200, 100, .5, .5, 5, .8);

    private static SkinFile.Module.Builder module() {
        return SkinFile.Module.newBuilder().setParam(SkinFile.ModuleParam.newBuilder()
                .setDxu(SkinFile.ModuleParamUnit.Unit).setDyu(SkinFile.ModuleParamUnit.Unit)
                .setDx(30).setDy(15).setAlpha(80).setRotate(5));
    }

    private static SkinFile.ModuleAnimation.Builder animation(int type, float from, float to) {
        return SkinFile.ModuleAnimation.newBuilder().setType(type).setStartTime(100)
                .setEndTime(300).setFromVal0(from).setToVal0(to);
    }

    private static VAnimationPreview.Frame at(SkinFile.Module module, double time) {
        return VAnimationPreview.frame(module, BASE, 640, 360, time);
    }

    @Test void staticFrameAndUnsupportedAnimations() {
        SkinFile.Module staticModule = module().build();
        assertTrue(VAnimationPreview.supports(staticModule));
        assertEquals(0, VAnimationPreview.durationMillis(staticModule));
        assertEquals(BASE, at(staticModule, 0).placement());
        assertEquals(1, at(staticModule, 0).scaleX());
        assertEquals(1, at(staticModule, 0).scaleY());
        assertFalse(VAnimationPreview.supports(module().addAnimations(animation(12, 0, 1)).build()));
        assertFalse(VAnimationPreview.supports(module().addAnimations(animation(1, 0, 1).setEase(100)).build()));
        assertFalse(VAnimationPreview.supports(module().addAnimations(animation(1, 0, 1).setRepeatType(3)).build()));
        assertFalse(VAnimationPreview.supports(module().addAnimations(animation(1, 0, 1).setEndTime(50)).build()));
        assertFalse(VAnimationPreview.supports(module().addAnimations(animation(1, 0, 1).setDelay(-1)).build()));
        assertFalse(VAnimationPreview.supports(module().addTriggers(SkinFile.ModuleCondition.getDefaultInstance()).build()));
    }

    @Test void moveUsesAnchoredPositionUnitsAndOriginalPercentOffset() {
        SkinFile.Module animated = module().addAnimations(animation(3, 30, 60)
                .setFromVal1(15).setToVal1(45)).build();
        VSceneLayout.Placement middle = at(animated, 200).placement();
        assertEquals(105, middle.left(), EPS);
        assertEquals(45, middle.top(), EPS);
        assertEquals(110, at(animated, 500).placement().left(), EPS);

        SkinFile.Module percent = module().setParam(SkinFile.ModuleParam.newBuilder()
                .setDxu(SkinFile.ModuleParamUnit.Percent).setDx(10)
                .setDyu(SkinFile.ModuleParamUnit.Percent).setDy(5))
                .addAnimations(animation(3, 192, 384).setFromVal1(54).setToVal1(108)).build();
        VSceneLayout.Placement percentBase = new VSceneLayout.Placement(100, 50, 200, 100, .5, .5, 0, 1);
        VSceneLayout.Placement end = VAnimationPreview.frame(percent, percentBase, 640, 360, 300).placement();
        assertEquals(100 - 64 + 128, end.left(), EPS);
        assertEquals(50 + 18 - 36, end.top(), EPS);
    }

    @Test void sizeScaleAlphaAndRotationFollowEmiriaPropertyValues() {
        SkinFile.Module animated = module()
                .addAnimations(animation(6, 600, 900).setFromVal1(300).setToVal1(600))
                .addAnimations(animation(9, 1, 2).setFromVal1(1).setToVal1(3))
                .addAnimations(animation(10, 0, 99))
                .addAnimations(animation(11, 0, 90)).build();
        VAnimationPreview.Frame frame = at(animated, 200);
        assertEquals(250, frame.placement().width(), EPS);
        assertEquals(150, frame.placement().height(), EPS);
        assertEquals(75, frame.placement().left(), EPS);
        assertEquals(25, frame.placement().top(), EPS);
        assertEquals(1.5, frame.scaleX(), EPS);
        assertEquals(2, frame.scaleY(), EPS);
        assertEquals(.49, frame.placement().opacity(), EPS);
        assertEquals(45, frame.placement().rotate());
    }

    @Test void repeatDelayRoundAndZeroLengthAreSeekable() {
        SkinFile.Module round = module().addAnimations(animation(11, 0, 90)
                .setStartTime(100).setEndTime(200).setRepeat(1)
                .setDelay(50).setRepeatType(2)).build();
        assertEquals(350, VAnimationPreview.durationMillis(round));
        assertEquals(45, at(round, 150).placement().rotate());
        assertEquals(90, at(round, 225).placement().rotate()); // held during the repeat delay
        assertEquals(45, at(round, 300).placement().rotate());
        assertEquals(0, at(round, 350).placement().rotate());
        assertEquals(0, at(round, 500).placement().rotate());

        SkinFile.Module instant = module().addAnimations(animation(10, 0, 33)
                .setStartTime(200).setEndTime(200).setRepeat(2).setDelay(50)).build();
        assertEquals(300, VAnimationPreview.durationMillis(instant));
        assertEquals(.8, at(instant, 199).placement().opacity(), EPS);
        assertEquals(.33, at(instant, 200).placement().opacity(), EPS);
        assertEquals(.33, at(instant, 225).placement().opacity(), EPS);
    }

    @Test void overlapUsesCurrentWritersThenMostRecentCompletedValue() {
        SkinFile.Module animated = module()
                .addAnimations(animation(11, 0, 100).setStartTime(0).setEndTime(500))
                .addAnimations(animation(11, 20, 40).setStartTime(100).setEndTime(200)
                        .setRepeat(1).setDelay(100)).build();
        assertEquals(30, at(animated, 150).placement().rotate()); // later writer wins
        assertEquals(50, at(animated, 250).placement().rotate()); // later track is in delay
        assertEquals(30, at(animated, 350).placement().rotate()); // later track writes again
        assertEquals(90, at(animated, 450).placement().rotate()); // later track has finished
    }

    @Test void equalStartTimesPreserveModuleOrder() {
        SkinFile.Module animated = module()
                .addAnimations(animation(11, 0, 100).setStartTime(0).setEndTime(200))
                .addAnimations(animation(11, 50, 70).setStartTime(0).setEndTime(200)).build();
        assertEquals(60, at(animated, 100).placement().rotate(), EPS);
    }

    @Test void laterStartWritesLastEvenWhenListedFirst() {
        SkinFile.Module animated = module()
                .addAnimations(animation(11, 50, 70).setStartTime(100).setEndTime(300))
                .addAnimations(animation(11, 0, 100).setStartTime(0).setEndTime(300)).build();
        assertEquals(60, at(animated, 200).placement().rotate(), EPS);
    }

    @Test void easingUsesEmiriaBezierCurves() {
        SkinFile.Module easeIn = module().addAnimations(animation(11, 0, 100)
                .setStartTime(0).setEndTime(1000).setEase(1)).build();
        SkinFile.Module easeOut = module().addAnimations(animation(11, 0, 100)
                .setStartTime(0).setEndTime(1000).setEase(2)).build();
        assertTrue(at(easeIn, 250).placement().rotate() < 25);
        assertTrue(at(easeOut, 250).placement().rotate() > 25);
        assertEquals(100, at(easeIn, 1000).placement().rotate());
        assertEquals(100, at(easeOut, 1000).placement().rotate());
    }
}
