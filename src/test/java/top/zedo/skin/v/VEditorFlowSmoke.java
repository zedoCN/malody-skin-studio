package top.zedo.skin.v;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Separate-process UI smoke for draft preview, save, and validation. */
public final class VEditorFlowSmoke {
    private VEditorFlowSmoke() {}

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("studio-v-flow-");
        SkinFile.Module module = SkinFile.Module.newBuilder().setType(5000).setUsage(99)
                .setParam(SkinFile.ModuleParam.newBuilder().setLayer(1).setAlpha(100)
                        .setXu(SkinFile.ModuleParamUnit.Percent).setYu(SkinFile.ModuleParamUnit.Percent)
                        .setX(0).setY(100))
                .setImage(SkinFile.ModuleParamImage.newBuilder().setFile("red.png")
                        .setWu(SkinFile.ModuleParamUnit.Percent).setHu(SkinFile.ModuleParamUnit.Percent)
                        .setWidth(20).setHeight(20)).build();
        SkinFile.Module second = module.toBuilder()
                .setParam(module.getParam().toBuilder().setLayer(4))
                .setImage(module.getImage().toBuilder().setFile("blue.png"))
                .build();
        Path asm = directory.resolve("info.asm");
        Files.write(asm, SkinFile.newBuilder().setMeta(
                SkinFile.Meta.newBuilder().setTitle("probe")).addModules(module).addModules(second)
                .build().toByteArray());
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) image.setRGB(x, y, 0xffff0000);
        ImageIO.write(image, "png", directory.resolve("red.png").toFile());
        byte[] original = Files.readAllBytes(asm);

        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch refreshed = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<VEditorPane> paneRef = new AtomicReference<>();
        AtomicReference<Double> originalX = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                VEditorPane pane = new VEditorPane(directory);
                paneRef.set(pane);
                new Stage().setScene(new Scene(pane, 1440, 900));
                originalX.set(nodes(pane).get(0).getLayoutX());
                Pane canvas = (Pane) field(pane, "sceneCanvas");
                canvas.getChildren().addListener((ListChangeListener<Node>) _ -> refreshed.countDown());
                ((TextField) field(pane, "x")).setText("20");
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                ready.countDown();
            }
        });
        ready.await();
        if (failure.get() == null && !refreshed.await(5, TimeUnit.SECONDS))
            failure.set(new AssertionError("修改属性后预览未自动更新"));
        if (failure.get() == null) {
            Platform.runLater(() -> {
                try {
                    VEditorPane pane = paneRef.get();
                    require(nodes(pane).get(0).getLayoutX() > originalX.get(), "图片未移动");
                    require((int) field(pane, "currentModule") == 0, "更新预览丢失了组件选中状态");
                    require(Arrays.equals(original, Files.readAllBytes(asm)), "预览提前写入文件");
                    TextField search = (TextField) field(pane, "moduleSearch");
                    ListView<?> list = (ListView<?>) field(pane, "modules");
                    search.setText("blue.png");
                    require(list.getItems().size() == 1, "资源名筛选没有缩小组件列表");
                    require((int) field(pane, "currentModule") == 0, "筛选切换了当前草稿");
                    require(((TextField) field(pane, "x")).getText().equals("20"), "筛选丢失了未保存属性");
                    search.setText("not-found");
                    require(list.getItems().isEmpty(), "无匹配时列表未清空");
                    require((int) field(pane, "currentModule") == 0, "无匹配时丢失了组件选中状态");
                    search.clear();
                    require(list.getItems().size() == 2 && list.getSelectionModel().getSelectedIndex() == 0,
                            "清除筛选没有恢复列表和选中项");
                    require(pane.saveNow(), "保存失败");
                    ((TextField) field(pane, "name")).setText("renamed");
                    require(pane.saveNow(), "名称修改保存失败");
                    require((int) field(pane, "currentModule") == 0, "名称更新丢失了组件选中状态");
                    require(((ListView<?>) field(pane, "modules")).getItems().getFirst().toString().contains("renamed"),
                            "列表未显示新名称");
                    byte[] saved = Files.readAllBytes(asm);
                    require(!Arrays.equals(original, saved), "保存没有写入文件");
                    ((TextField) field(pane, "x")).setText("invalid");
                    require(!pane.saveNow(), "无效数字仍被保存");
                    require(Arrays.equals(saved, Files.readAllBytes(asm)), "无效数字覆盖了文件");
                    require(((Label) field(pane, "inspectorStatus")).getText().contains("有效数字"),
                            "无效数字没有就地提示");
                    ((TextField) field(pane, "x")).setText("20");
                    search.setText("blue.png");
                    search.getOnAction().handle(new javafx.event.ActionEvent());
                    require((int) field(pane, "currentModule") == 1, "回车没有选中筛选结果");
                    search.setText("red.png");
                    search.getOnAction().handle(new javafx.event.ActionEvent());
                    require((int) field(pane, "currentModule") == 0, "筛选结果无法切回原组件");
                    require(Arrays.equals(saved, Files.readAllBytes(asm)), "筛选与切换提前写入文件");
                    System.out.println("V flow PASS: live preview, draft save, inline validation, module filter");
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    done.countDown();
                }
            });
            done.await();
        }
        if (failure.get() != null) failure.get().printStackTrace();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
        System.exit(failure.get() == null ? 0 : 1);
    }

    private static Object field(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, ImageView> nodes(VEditorPane pane) throws Exception {
        return (Map<Integer, ImageView>) field(pane, "sceneNodes");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
