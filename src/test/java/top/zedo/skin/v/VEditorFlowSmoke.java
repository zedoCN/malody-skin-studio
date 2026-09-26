package top.zedo.skin.v;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
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
        Path lua = directory.resolve("skin.lua");
        Files.write(asm, SkinFile.newBuilder().setMeta(
                SkinFile.Meta.newBuilder().setTitle("probe").setScript("skin.lua"))
                .addModules(module).addModules(second)
                .build().toByteArray());
        Files.writeString(lua, "return 1\r\n");
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
                require(((VLuaCodeArea) field(pane, "luaSource")).getText().equals("return 1\n"),
                        "Lua 编辑器未正确显示 CRLF 脚本");
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
                    require(!((Button) field(pane, "undoButton")).isDisabled(), "编辑后撤销按钮不可用");
                    pane.fireEvent(shortcut(KeyCode.Z, false));
                    require(nodes(pane).get(0).getLayoutX() == originalX.get(), "撤销未恢复画布");
                    require(((TextField) field(pane, "x")).getText().equals("0.0"), "撤销未恢复属性");
                    require(((Button) field(pane, "undoButton")).isDisabled(), "撤销到初始状态后按钮仍可用");
                    pane.fireEvent(shortcut(KeyCode.Z, true));
                    require(nodes(pane).get(0).getLayoutX() > originalX.get(), "重做未恢复画布");
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
                    require(Files.readString(lua).equals("return 1\r\n"), "仅保存 ASM 时改写了 Lua 换行");
                    ((TextField) field(pane, "name")).setText("renamed");
                    require(pane.saveNow(), "名称修改保存失败");
                    require((int) field(pane, "currentModule") == 0, "名称更新丢失了组件选中状态");
                    require(((ListView<?>) field(pane, "modules")).getItems().getFirst().toString().contains("renamed"),
                            "列表未显示新名称");
                    VLuaCodeArea luaEditor = (VLuaCodeArea) field(pane, "luaSource");
                    luaEditor.setSourceText("return 2\n");
                    require(luaEditor.getStyleOfChar(0).contains("lua-keyword"), "Lua 关键字未高亮");
                    require(luaEditor.getStyleOfChar(7).contains("lua-number"), "Lua 数字未高亮");
                    pane.undoEdit();
                    require(luaEditor.getText().equals("return 1\n"), "撤销未恢复 Lua");
                    pane.redoEdit();
                    require(luaEditor.getText().equals("return 2\n"), "重做未恢复 Lua");
                    require(pane.saveNow(), "Lua 保存失败");
                    require(Files.readString(lua).equals("return 2\r\n"), "Lua 保存内容或 CRLF 换行不正确");
                    pane.undoEdit();
                    require(luaEditor.getText().equals("return 1\n"), "保存后不能撤销 Lua");
                    pane.redoEdit();
                    require(((Label) field(pane, "saveStatus")).getText().equals("已保存"),
                            "重做到保存版本后状态不正确");
                    ImageView moved = nodes(pane).get(0);
                    String beforeDrag = ((TextField) field(pane, "dx")).getText();
                    moved.getOnMousePressed().handle(mouse(MouseEvent.MOUSE_PRESSED, 100, 100, moved));
                    moved.getOnMouseDragged().handle(mouse(MouseEvent.MOUSE_DRAGGED, 112, 100, moved));
                    moved.getOnMouseReleased().handle(mouse(MouseEvent.MOUSE_RELEASED, 112, 100, moved));
                    String afterDrag = ((TextField) field(pane, "dx")).getText();
                    require(!afterDrag.equals(beforeDrag), "拖动未更新偏移");
                    pane.undoEdit();
                    require(((TextField) field(pane, "dx")).getText().equals(beforeDrag), "拖动不能一步撤销");
                    pane.redoEdit();
                    require(((TextField) field(pane, "dx")).getText().equals(afterDrag), "拖动不能重做");
                    double beforeFastRedo = nodes(pane).get(0).getLayoutX();
                    ((TextField) field(pane, "x")).setText("25");
                    pane.undoEdit();
                    require(nodes(pane).get(0).getLayoutX() == beforeFastRedo, "快速撤销未恢复画布");
                    pane.redoEdit();
                    require(nodes(pane).get(0).getLayoutX() > beforeFastRedo, "自动预览前重做未更新画布");
                    pane.undoEdit();
                    byte[] saved = Files.readAllBytes(asm);
                    require(!Arrays.equals(original, saved), "保存没有写入文件");
                    String beforeInvalid = ((TextField) field(pane, "x")).getText();
                    ((TextField) field(pane, "x")).setText("invalid");
                    require(!pane.saveNow(), "无效数字仍被保存");
                    require(Arrays.equals(saved, Files.readAllBytes(asm)), "无效数字覆盖了文件");
                    require(((Label) field(pane, "inspectorStatus")).getText().contains("有效数字"),
                            "无效数字没有就地提示");
                    pane.undoEdit();
                    require(((TextField) field(pane, "x")).getText().equals(beforeInvalid), "无效输入不能撤销");
                    search.setText("blue.png");
                    search.getOnAction().handle(new javafx.event.ActionEvent());
                    require((int) field(pane, "currentModule") == 1, "回车没有选中筛选结果");
                    search.setText("red.png");
                    search.getOnAction().handle(new javafx.event.ActionEvent());
                    require((int) field(pane, "currentModule") == 0, "筛选结果无法切回原组件");
                    require(Arrays.equals(saved, Files.readAllBytes(asm)), "筛选与切换提前写入文件");
                    System.out.println("V flow PASS: live preview, undo/redo, draft save, inline validation, module filter");
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

    private static KeyEvent shortcut(KeyCode code, boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, true, false, true);
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double x, double y, Node node) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false,
                false, false, false, new PickResult(node, x, y));
    }
}
