package top.zedo.skin.v;

import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** PNG of the same static V scene shown in the editor's layout overview. */
public final class VSkinSnapshot {
    private VSkinSnapshot() { }

    public static void run(String[] args) {
        if (args.length != 6) throw new IllegalArgumentException(
                "用法: --snapshot-v <皮肤.msp|目录|info.asm> <输出.png> <图层:1|4> <平台:windows|ios|android> <宽>x<高>");
        Path skinPath = pathArgument(args[1]);
        Path outputPath = pathArgument(args[2]);
        int layer;
        try { layer = Integer.parseInt(args[3]); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("图层必须为 1 或 4", error); }
        if (!VSceneLayout.isFullScreenLayer(layer)) throw new IllegalArgumentException("图层必须为 1 或 4");
        VSceneLayout.Platform platform;
        try { platform = VSceneLayout.Platform.valueOf(args[4].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("平台必须为 windows、ios 或 android", error); }
        String[] dimensions = args[5].toLowerCase(Locale.ROOT).split("x", -1);
        int width, height;
        try {
            if (dimensions.length != 2) throw new NumberFormatException();
            width = Integer.parseInt(dimensions[0]);
            height = Integer.parseInt(dimensions[1]);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("尺寸格式应为 宽x高，例如 1920x1080", error);
        }
        if (width < 1 || height < 1 || width > 8192 || height > 8192
                || (long) width * height > 32_000_000)
            throw new IllegalArgumentException("尺寸必须在 1–8192 像素内，且总像素不超过 3200 万");

        MspSkinDocument document;
        try { document = MspSkinDocument.open(skinPath); }
        catch (IOException error) { throw new IllegalArgumentException("无法读取 V 皮肤: " + skinPath, error); }
        VSceneLayout.SceneContext context = new VSceneLayout.SceneContext(width, height, platform);
        CompletableFuture<Result> frame = new CompletableFuture<>();
        Platform.startup(() -> {
            try {
                VScenePlan plan = VScenePlan.build(document, document.skin(), layer, context, width, height);
                Pane canvas = new Pane();
                canvas.setPrefSize(width, height);
                canvas.setMinSize(width, height);
                canvas.setMaxSize(width, height);
                canvas.setClip(new Rectangle(width, height));
                for (VScenePlan.Item item : plan.items()) canvas.getChildren().add(VScenePlan.imageView(item));
                canvas.applyCss();
                canvas.layout();
                SnapshotParameters parameters = new SnapshotParameters();
                parameters.setFill(Color.TRANSPARENT);
                frame.complete(new Result(canvas.snapshot(parameters, new WritableImage(width, height)), plan.status()));
            } catch (Throwable error) {
                frame.completeExceptionally(error);
            }
        });
        try {
            Result result = frame.join();
            writePng(result.image(), outputPath);
            System.out.println("已保存 V 静态层 PNG: " + outputPath.toAbsolutePath());
            System.out.println(result.status());
            System.out.println("仅包含可投影的静态自定义图片；Lua、动画、动态条件和赛道层未包含。");
        } finally {
            Platform.exit();
        }
    }

    private record Result(WritableImage image, String status) { }

    private static Path pathArgument(String argument) {
        return argument.startsWith("file:") ? Path.of(URI.create(argument)) : Path.of(argument);
    }

    private static void writePng(WritableImage image, Path path) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage png = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            if (!ImageIO.write(png, "png", path.toFile())) throw new IOException("找不到 PNG 编码器");
        } catch (IOException error) {
            throw new IllegalStateException("写出 V 静态层 PNG 失败: " + path, error);
        }
    }
}
