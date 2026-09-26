package top.zedo.skin.uis.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Separate-process UI smoke for document reuse and disk-conflict behavior. */
public final class StudioFlowSmoke {
    private StudioFlowSmoke() {}

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("studio-flow-");
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                Path mui = directory.resolve("skin.mui");
                Files.writeString(mui, "_item\n  type=0\n  size=20,20\n");
                UISEditor editor = new UISEditor();
                SkinStudioWindow studio = new SkinStudioWindow(editor);
                new Stage().setScene(new Scene(studio, 1440, 900));
                studio.open(mui);
                studio.open(mui);
                require(editor.tabPane.getTabs().size() == 1, "同一 MUI 打开了多个编辑页");

                UISCodeArea code = (UISCodeArea) ((VirtualizedScrollPane<?>)
                        editor.tabPane.getTabs().getFirst().getContent()).getContent();
                code.replaceText("_item\n  type=0\n  size=30,30\n");
                code.saveNow();
                require(Files.readString(mui).contains("30,30"), "MUI 保存未写入磁盘");

                Files.writeString(mui, "_item\n  type=0\n  size=40,40\n");
                code.replaceText("_item\n  type=0\n  size=50,50\n");
                try {
                    code.saveNow();
                    throw new AssertionError("外部修改没有被拦截");
                } catch (java.io.IOException expected) {
                    require(expected.getMessage().contains("外部修改"), "冲突诊断未说明外部修改");
                }
                require(Files.readString(mui).contains("40,40"), "覆盖了外部文件内容");

                Path v = Files.createDirectory(directory.resolve("vskin"));
                SkinFile skin = SkinFile.newBuilder().setMeta(
                        SkinFile.Meta.newBuilder().setTitle("V test")).build();
                Files.write(v.resolve("info.asm"), skin.toByteArray());
                studio.open(v);
                require(studio.lookup(".v-editor") != null, "V 皮肤目录未打开");
                System.out.println("Studio flow PASS: MUI reuse, save conflict, V directory");
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        done.await();
        if (failure.get() != null) failure.get().printStackTrace();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
        System.exit(failure.get() == null ? 0 : 1);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
