package top.zedo.skin.uis;

import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import top.zedo.skin.DeviceType;
import top.zedo.skin.ResolutionInfo;
import top.zedo.zxncore.ZXLogger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Repeatable single-frame preview for debugging and visual comparison. */
public final class SkinSnapshot {
    private SkinSnapshot() { }

    public static void run(String[] args) {
        if (args.length < 3 || args.length > 5) {
            throw new IllegalArgumentException("用法: --snapshot <皮肤.mui> <输出.png> [时间毫秒] [设备比例或 ANDROID:宽x高]");
        }
        Path skinPath = Path.of(args[1]);
        Path outputPath = Path.of(args[2]);
        long time = args.length >= 4 ? Long.parseLong(args[3]) : 0;
        String profile = args.length == 5 ? args[4].toUpperCase() : "PC";
        DeviceType device;
        double aspectRatio;
        double outputHeight;
        if (profile.startsWith("ANDROID:")) {
            String[] dimensions = profile.substring("ANDROID:".length()).split("X", -1);
            if (dimensions.length != 2) throw new IllegalArgumentException("设备尺寸应为 ANDROID:宽x高");
            double deviceWidth = Double.parseDouble(dimensions[0]);
            double deviceHeight = Double.parseDouble(dimensions[1]);
            if (!Double.isFinite(deviceWidth) || !Double.isFinite(deviceHeight)
                    || deviceWidth <= 0 || deviceHeight <= 0) {
                throw new IllegalArgumentException("设备宽高必须是正数");
            }
            device = DeviceType.ANDROID;
            aspectRatio = deviceWidth / deviceHeight;
            outputHeight = deviceHeight;
        } else {
            ResolutionInfo resolution = ResolutionInfo.valueOf(profile);
            device = resolution.getDevice();
            aspectRatio = resolution.getAspectRatio();
            outputHeight = 0;
        }
        if (!Files.isRegularFile(skinPath)) {
            throw new IllegalArgumentException("找不到皮肤文件: " + skinPath);
        }

        CompletableFuture<WritableImage> frame = new CompletableFuture<>();
        Platform.startup(() -> {
            try {
                UISCanvas canvas = new UISCanvas();
                canvas.setDeviceType(device);
                canvas.setAspectRatio(aspectRatio);
                canvas.loadSkin(skinPath);
                if (outputHeight > 0) canvas.setZoomRate(outputHeight / canvas.skin.unit);
                if (canvas.skin.getComponents().isEmpty()) {
                    throw new IllegalStateException("皮肤没有可渲染的组件: " + skinPath);
                }
                canvas.pause();
                canvas.setTime(time);
                double width = canvas.expressionCalculator.getCanvasWidth();
                double height = canvas.expressionCalculator.getCanvasHeight();
                canvas.resize(width, height);
                canvas.applyCss();
                canvas.layout();
                canvas.draw();
                frame.complete(canvas.snapshot(new SnapshotParameters(), null));
            } catch (Throwable error) {
                frame.completeExceptionally(error);
            }
        });

        try {
            writePng(frame.join(), outputPath);
            ZXLogger.info("已保存截图: " + outputPath.toAbsolutePath()
                    + " (" + profile + ", " + time + " ms)");
        } finally {
            Platform.exit();
        }
    }

    private static void writePng(WritableImage image, Path outputPath) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        if (width == 0 || height == 0) {
            throw new IllegalStateException("预览画布尺寸为零");
        }
        BufferedImage png = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        try {
            Path parent = outputPath.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            if (!ImageIO.write(png, "png", outputPath.toFile())) {
                throw new IOException("找不到 PNG 编码器");
            }
        } catch (IOException error) {
            throw new IllegalStateException("写出截图失败: " + outputPath, error);
        }
    }
}
