package top.zedo.skin.uis.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Captures the complete editor workspace for repeatable visual inspection. */
final class StudioUiSnapshot {
    private StudioUiSnapshot() {}

    static void run(String[] args) {
        if (args.length != 3) throw new IllegalArgumentException(
                "用法: --snapshot-ui <皮肤.mui|.msp> <输出.png>");
        Path skin = args[1].startsWith("file:") ? Path.of(URI.create(args[1])) : Path.of(args[1]);
        Path output = args[2].startsWith("file:") ? Path.of(URI.create(args[2])) : Path.of(args[2]);
        if (!Files.isRegularFile(skin)) throw new IllegalArgumentException("找不到皮肤文件: " + skin);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                UISEditor muiEditor = new UISEditor();
                SkinStudioWindow workspace = new SkinStudioWindow(muiEditor);
                Scene scene = new Scene(workspace, 1440, 900);
                scene.getStylesheets().addAll("resources/baseExpansionPack/color/style.css",
                        "resources/baseExpansionPack/color/dark.css",
                        "resources/baseExpansionPack/color/studio.css");
                Stage stage = new Stage();
                stage.setScene(scene);
                workspace.open(skin);
                workspace.applyCss();
                workspace.layout();
                WritableImage snapshot = scene.snapshot(null);
                BufferedImage png = new BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(),
                        BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < png.getHeight(); y++) {
                    for (int x = 0; x < png.getWidth(); x++) {
                        png.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                    }
                }
                Path parent = output.toAbsolutePath().getParent();
                if (parent != null) Files.createDirectories(parent);
                if (!ImageIO.write(png, "png", output.toFile())) throw new IOException("找不到 PNG 编码器");
                System.out.println("已保存工作区截图: " + output);
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        try {
            done.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("截图中断", error);
        }
        Platform.exit();
        if (failure.get() != null) throw new IllegalStateException("工作区截图失败", failure.get());
    }
}
