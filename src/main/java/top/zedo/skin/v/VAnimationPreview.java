package top.zedo.skin.v;

import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic projection of Emiria's module animation pool, without Lua or trigger execution. */
final class VAnimationPreview {
    record Frame(VSceneLayout.Placement placement, double scaleX, double scaleY) { }

    private record Track(int type, double start, double end, double from, double to,
                         int repeat, int repeatType, double delay, int ease, int order) {
        double realEnd() { return start + (end - start) * (repeat + 1d) + repeat * delay; }
    }

    private VAnimationPreview() { }

    static boolean supports(SkinFile.Module module) {
        if (module == null || module.getTriggersCount() != 0) return false;
        for (SkinFile.ModuleAnimation animation : module.getAnimationsList()) {
            int type = animation.getType();
            if (type < 1 || type > 11 || animation.getRepeat() < 0
                    || animation.getRepeatType() < 0 || animation.getRepeatType() > 2
                    || animation.getEase() < 0 || animation.getEase() > 2
                    || !finite(animation.getStartTime(), animation.getEndTime(), animation.getDelay(),
                    animation.getFromVal0(), animation.getToVal0(),
                    animation.getFromVal1(), animation.getToVal1())
                    || animation.getStartTime() < 0 || animation.getEndTime() < animation.getStartTime()
                    || animation.getDelay() < 0) return false;
            double realEnd = animation.getStartTime()
                    + (animation.getEndTime() - animation.getStartTime()) * (animation.getRepeat() + 1d)
                    + animation.getRepeat() * (double) animation.getDelay();
            if (!Double.isFinite(realEnd)) return false;
        }
        return true;
    }

    static double durationMillis(SkinFile.Module module) {
        if (!supports(module)) throw new IllegalArgumentException("无法确定该模块的动画时间");
        double duration = 0;
        for (SkinFile.ModuleAnimation animation : module.getAnimationsList()) {
            double length = animation.getEndTime() - animation.getStartTime();
            duration = Math.max(duration, animation.getStartTime()
                    + length * (animation.getRepeat() + 1d)
                    + animation.getRepeat() * (double) animation.getDelay());
        }
        return duration;
    }

    static Frame frame(SkinFile.Module module, VSceneLayout.Placement base,
                       double canvasWidth, double canvasHeight, double timeMillis) {
        if (!supports(module) || base == null || !Double.isFinite(canvasWidth) || canvasWidth <= 0
                || !Double.isFinite(canvasHeight) || canvasHeight <= 0
                || !Double.isFinite(timeMillis)) throw new IllegalArgumentException("无法预览该模块动画");
        if (module.getAnimationsCount() == 0) return new Frame(base, 1, 1);

        double unit = canvasHeight / 1080d;
        SkinFile.ModuleParam param = module.getParam();
        double originalDx = param.getDxu() == SkinFile.ModuleParamUnit.Percent
                ? param.getDx() * canvasWidth / 100d : param.getDx() * unit;
        double originalDy = param.getDyu() == SkinFile.ModuleParamUnit.Percent
                ? param.getDy() * canvasHeight / 100d : param.getDy() * unit;
        double x = originalDx;
        double y = originalDy;
        double width = base.width();
        double height = base.height();
        double scaleX = 1;
        double scaleY = 1;
        double alpha = base.opacity();
        double rotate = base.rotate();
        double anchorX = base.left() + base.width() * base.pivotX();
        double anchorY = canvasHeight - base.top() - base.height() * (1 - base.pivotY());

        // The module pool writes tracks by start order on every update. During a repeat
        // delay a track does not write; another active track can therefore take over.
        // With no active writer, preserve the most recent value for random-access scrubbing.
        double[] currentValues = new double[12];
        double[] lastValues = new double[12];
        boolean[] hasCurrent = new boolean[12];
        int[] currentOrder = new int[12];
        double[] lastTime = new double[12];
        int[] lastOrder = new int[12];
        boolean[] hasLast = new boolean[12];
        int rank = 0;
        for (Track track : tracks(module)) {
            if (timeMillis < track.start()) break;
            rank++;
            int type = track.type();
            double current = value(track, timeMillis);
            if (!Double.isNaN(current) && timeMillis <= track.realEnd()) {
                if (!hasCurrent[type] || rank > currentOrder[type]) {
                    currentValues[type] = current;
                    currentOrder[type] = rank;
                    hasCurrent[type] = true;
                }
            } else {
                double previousTime = lastWriteTime(track, timeMillis);
                if (!hasLast[type] || previousTime > lastTime[type]
                        || previousTime == lastTime[type] && rank > lastOrder[type]) {
                    lastTime[type] = previousTime;
                    lastOrder[type] = rank;
                    // The previous write was at the end of a cycle, not inside its delay.
                    lastValues[type] = value(track, previousTime);
                    hasLast[type] = true;
                }
            }
        }
        for (int type = 1; type <= 11; type++) {
            if (!hasCurrent[type] && !hasLast[type]) continue;
            double value = hasCurrent[type] ? currentValues[type] : lastValues[type];
            switch (type) {
                case 1 -> x = value * unit;
                case 2 -> y = value * unit;
                case 4 -> width = value * unit;
                case 5 -> height = value * unit;
                case 7 -> scaleX = value;
                case 8 -> scaleY = value;
                case 10 -> alpha = Math.max(0, Math.min(1, (int) value / 100d));
                case 11 -> rotate = value;
                default -> throw new IllegalStateException("未展开的动画类型");
            }
        }
        VSceneLayout.Placement placement = new VSceneLayout.Placement(
                anchorX - originalDx + x - width * base.pivotX(),
                canvasHeight - (anchorY - originalDy + y) - height * (1 - base.pivotY()),
                width, height, base.pivotX(), base.pivotY(), rotate, alpha);
        return new Frame(placement, scaleX, scaleY);
    }

    private static double lastWriteTime(Track track, double time) {
        if (time >= track.realEnd() || track.end() == track.start()) return track.realEnd();
        double period = track.end() - track.start() + track.delay();
        int cycle = (int) Math.floor((time - track.start()) / period);
        return track.start() + cycle * period + (track.end() - track.start());
    }

    private static List<Track> tracks(SkinFile.Module module) {
        List<Track> tracks = new ArrayList<>();
        int order = 0;
        for (SkinFile.ModuleAnimation ani : module.getAnimationsList()) {
            int type = ani.getType();
            tracks.add(new Track(type == 3 ? 1 : type == 6 ? 4 : type == 9 ? 7 : type,
                    ani.getStartTime(), ani.getEndTime(), ani.getFromVal0(), ani.getToVal0(),
                    ani.getRepeat(), ani.getRepeatType(), ani.getDelay(), ani.getEase(), order++));
            if (type == 3 || type == 6 || type == 9)
                tracks.add(new Track(type == 3 ? 2 : type == 6 ? 5 : 8,
                        ani.getStartTime(), ani.getEndTime(), ani.getFromVal1(), ani.getToVal1(),
                        ani.getRepeat(), ani.getRepeatType(), ani.getDelay(), ani.getEase(), order++));
        }
        tracks.sort(Comparator.comparingDouble(Track::start).thenComparingInt(Track::order));
        return tracks;
    }

    private static double value(Track track, double time) {
        double length = track.end() - track.start();
        if (length == 0) return track.to();
        if (time > track.realEnd() && track.repeat() == 0) return track.to();
        double period = length + track.delay();
        int cycle = (int) Math.min(track.repeat(), Math.floor((time - track.start()) / period));
        double effectiveTime = time > track.realEnd() ? track.realEnd() : time;
        double elapsed = effectiveTime - track.start() - period * cycle;
        if (track.delay() > 0 && elapsed > length) return Double.NaN;
        double progress = Math.min(1, elapsed / length);
        if (track.repeatType() == 2 && cycle % 2 == 1) progress = 1 - progress;
        progress = ease(progress, track.ease(), length);
        return track.from() + progress * (track.to() - track.from());
    }

    private static double ease(double progress, int ease, double length) {
        if (ease == 0) return progress;
        double p1x = ease == 1 ? 0.333333 : 0;
        double p1y = ease == 1 ? 0 : 0.33333;
        double p2x = ease == 1 ? 1 : 0.666666;
        double p2y = ease == 1 ? 0.66666 : 1;
        double cx = 3 * p1x, bx = 3 * (p2x - p1x) - cx, ax = 1 - cx - bx;
        double cy = 3 * p1y, by = 3 * (p2y - p1y) - cy, ay = 1 - cy - by;
        double epsilon = 1 / length;
        double t = progress;
        for (int i = 0; i < 8; i++) {
            double error = ((ax * t + bx) * t + cx) * t - progress;
            if (Math.abs(error) < epsilon) return ((ay * t + by) * t + cy) * t;
            double derivative = (3 * ax * t + 2 * bx) * t + cx;
            if (Math.abs(derivative) < 1e-6) break;
            t -= error / derivative;
        }
        double low = 0, high = 1;
        t = progress;
        while (low < high) {
            double x = ((ax * t + bx) * t + cx) * t;
            if (Math.abs(x - progress) < epsilon) break;
            if (progress > x) low = t; else high = t;
            double next = (high - low) * .5 + low;
            if (next == t) break;
            t = next;
        }
        return ((ay * t + by) * t + cy) * t;
    }

    private static boolean finite(float... values) {
        for (float value : values) if (!Float.isFinite(value)) return false;
        return true;
    }
}
