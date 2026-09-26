package top.zedo.skin.v;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** Module and metadata editor for a Malody V skin. */
public final class VEditorPane extends BorderPane {
    private final MspSkinDocument document;
    private final VSkinEditModel draft;
    private final ListView<String> modules = new ListView<>();
    private final TextField title = new TextField();
    private final TextField creator = new TextField();
    private final TextArea description = new TextArea();
    private final TextArea luaSource = new TextArea();
    private final Label luaStatus = new Label();
    private VLuaSource.Result luaBaseline;
    private final TextField cover = new TextField();
    private final TextField name = new TextField();
    private final TextField resource = new TextField();
    private final TextField x = new TextField();
    private final TextField y = new TextField();
    private final TextField dx = new TextField();
    private final TextField dy = new TextField();
    private final TextField width = new TextField();
    private final TextField height = new TextField();
    private final TextField alpha = new TextField();
    private final TextField rotate = new TextField();
    private final Label moduleKind = new Label();
    private final Label previewStatus = new Label();
    private final ImageView preview = new ImageView();
    private final Pane sceneCanvas = new Pane();
    private final ComboBox<Integer> sceneLayer = new ComboBox<>();
    private final ComboBox<VSceneLayout.Platform> scenePlatform = new ComboBox<>();
    private final Label sceneStatus = new Label();
    private final Label runtimeStatus = new Label("未加载运行态快照");
    private final TextArea runtimeDetails = new TextArea();
    private VRuntimeSnapshot runtimeSnapshot;
    private Path runtimePath;
    private boolean runtimeFingerprintsMatch;
    private int currentModule = -1;
    private boolean changingSelection;

    public VEditorPane(Path path) throws IOException {
        document = MspSkinDocument.open(path);
        draft = new VSkinEditModel(document.skin());

        Label heading = new Label("Malody V · " + document.path().getFileName());
        Button save = new Button("保存皮肤");
        save.setOnAction(_ -> save());
        HBox toolbar = new HBox(12, heading, save);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10));
        setTop(toolbar);

        VBox metadata = new VBox(7,
                new Label("皮肤信息"), row("标题", title), row("作者", creator), new Label("描述"), description,
                row("封面资源", cover), new Label("组件 (" + draft.moduleCount() + ")"), modules);
        metadata.setPadding(new Insets(12));
        metadata.setPrefWidth(360);
        description.setPrefRowCount(3);
        VBox.setVgrow(modules, Priority.ALWAYS);
        setLeft(metadata);

        VBox properties = new VBox(8, new Label("组件属性"), moduleKind, row("名称", name), row("资源 / 文字", resource),
                row("X", x), row("Y", y), row("偏移 X", dx), row("偏移 Y", dy),
                row("宽度", width), row("高度", height), row("透明度", alpha), row("旋转", rotate));
        properties.setPadding(new Insets(12));
        properties.setPrefWidth(330);
        ScrollPane propertyScroll = new ScrollPane(properties);
        propertyScroll.setFitToWidth(true);

        preview.setPreserveRatio(true);
        preview.setFitWidth(440);
        preview.setFitHeight(500);
        VBox imageBox = new VBox(10, new Label("选中组件资源预览"), preview, previewStatus);
        imageBox.setAlignment(Pos.TOP_CENTER);
        imageBox.setPadding(new Insets(12));
        sceneCanvas.setPrefSize(640, 360);
        sceneCanvas.setMinSize(640, 360);
        sceneCanvas.setMaxSize(640, 360);
        sceneCanvas.setClip(new Rectangle(640, 360));
        sceneCanvas.setStyle("-fx-background-color: #20232a;");
        scenePlatform.getItems().setAll(VSceneLayout.Platform.values());
        scenePlatform.getSelectionModel().select(VSceneLayout.Platform.WINDOWS);
        scenePlatform.setOnAction(_ -> refreshScene());
        sceneLayer.setConverter(new StringConverter<>() {
            @Override public String toString(Integer layer) {
                if (layer == null) return "";
                return switch (layer) {
                    case 1 -> "1 · 背景";
                    case 2 -> "2 · 游玩区下层";
                    case 3 -> "3 · 游玩区上层";
                    case 4 -> "4 · 顶层";
                    default -> layer + " · 其他";
                };
            }
            @Override public Integer fromString(String text) { return null; }
        });
        for (SkinFile.Module module : draft.skin().getModulesList()) {
            int layer = module.getParam().getLayer();
            if (!sceneLayer.getItems().contains(layer)) sceneLayer.getItems().add(layer);
        }
        sceneLayer.getItems().sort(Integer::compareTo);
        sceneLayer.setOnAction(_ -> refreshScene());
        Integer firstLayer = sceneLayer.getItems().isEmpty() ? null : sceneLayer.getItems().getFirst();
        int mostImages = -1;
        for (int layer : sceneLayer.getItems()) {
            if (!VSceneLayout.isFullScreenLayer(layer)) continue;
            int count = 0;
            for (SkinFile.Module module : draft.skin().getModulesList()) {
                if (module.getParam().getLayer() == layer && VSceneLayout.isStaticImage(module)) count++;
            }
            if (count > mostImages) { firstLayer = layer; mostImages = count; }
        }
        if (firstLayer != null) sceneLayer.getSelectionModel().select(firstLayer);
        Button refreshScene = new Button("应用属性并刷新");
        refreshScene.setOnAction(_ -> { if (applyModule()) refreshScene(); });
        HBox sceneTools = new HBox(8, new Label("图层"), sceneLayer,
                new Label("平台"), scenePlatform, refreshScene);
        sceneTools.setAlignment(Pos.CENTER_LEFT);
        VBox sceneBox = new VBox(10, new Label("原始参数布局概览 · 参考视口 1920×1080 · 不执行 Lua"), sceneTools,
                new ScrollPane(sceneCanvas), sceneStatus,
                new Label("可拖动图片调整偏移量，点击“保存皮肤”写回。Lua 仍可能在游戏中改写这些图片。"));
        sceneBox.setPadding(new Insets(12));
        Tab resourceTab = new Tab("单资源", imageBox);
        Tab sceneTab = new Tab("布局概览", sceneBox);
        luaBaseline = VLuaSource.load(document);
        Label luaPath = new Label("info.asm 脚本路径：" + (luaBaseline.path().isBlank() ? "(未设置)" : luaBaseline.path()));
        luaPath.setWrapText(true);
        luaSource.setText(luaBaseline.source());
        luaSource.setEditable(luaBaseline.originalBytes() != null);
        luaSource.setWrapText(false);
        luaSource.setStyle("-fx-font-family: monospace;");
        luaStatus.setText(luaBaseline.diagnostic());
        VBox luaBox = new VBox(8, luaPath, luaStatus, luaSource);
        luaBox.setPadding(new Insets(12));
        VBox.setVgrow(luaSource, Priority.ALWAYS);
        Tab luaTab = new Tab("Lua 源码", luaBox);
        Button loadRuntime = new Button("加载 Unity 冻结帧快照…");
        loadRuntime.setOnAction(_ -> loadRuntimeSnapshot());
        runtimeStatus.setWrapText(true);
        runtimeDetails.setEditable(false);
        runtimeDetails.setWrapText(true);
        runtimeDetails.setStyle("-fx-font-family: monospace;");
        Label runtimeCaveat = new Label("只读对照保存版参数；仅核对 info.asm 和 Lua，素材未核对。运行值属于导出时的皮肤副本、谱面和配置。");
        runtimeCaveat.setWrapText(true);
        VBox runtimeBox = new VBox(10, loadRuntime, runtimeStatus, runtimeCaveat, runtimeDetails);
        runtimeBox.setPadding(new Insets(12));
        VBox.setVgrow(runtimeDetails, Priority.ALWAYS);
        Tab runtimeTab = new Tab("运行态对照", runtimeBox);
        resourceTab.setClosable(false);
        sceneTab.setClosable(false);
        luaTab.setClosable(false);
        runtimeTab.setClosable(false);
        TabPane previews = new TabPane(resourceTab, sceneTab, luaTab, runtimeTab);
        HBox content = new HBox(propertyScroll, previews);
        HBox.setHgrow(previews, Priority.ALWAYS);
        setCenter(content);

        title.setText(draft.metadata().getTitle());
        creator.setText(draft.metadata().getCreator());
        description.setText(draft.metadata().getDesc());
        cover.setText(draft.metadata().getCover());
        for (int i = 0; i < draft.moduleCount(); i++) modules.getItems().add(moduleLabel(i));
        modules.getSelectionModel().selectedIndexProperty().addListener((_, oldIndex, newIndex) -> {
            if (changingSelection) return;
            int next = newIndex.intValue();
            if (!applyModule()) {
                changingSelection = true;
                modules.getSelectionModel().select(oldIndex.intValue());
                changingSelection = false;
                return;
            }
            currentModule = next;
            showModule(next);
        });
        if (!modules.getItems().isEmpty()) modules.getSelectionModel().selectFirst();
        refreshScene();
    }

    private static HBox row(String label, TextField field) {
        Label caption = new Label(label);
        caption.setMinWidth(78);
        HBox row = new HBox(7, caption, field);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    private String moduleLabel(int index) {
        SkinFile.Module module = draft.module(index);
        String text = module.hasMeta() ? module.getMeta().getDesc() : "";
        return (index + 1) + ". " + (text.isBlank() ? "(未命名)" : text) + " · " + module.getType();
    }

    private void showModule(int index) {
        boolean selected = index >= 0 && index < draft.moduleCount();
        for (TextField field : new TextField[]{name, resource, x, y, dx, dy, width, height, alpha, rotate}) field.setDisable(!selected);
        if (!selected) {
            moduleKind.setText("");
            preview.setImage(null);
            previewStatus.setText("");
            showRuntimeModule();
            return;
        }
        SkinFile.Module module = draft.module(index);
        SkinFile.ModuleParam param = module.getParam();
        VSkinEditModel.ModuleFields fields = draft.fields(index);
        moduleKind.setText("用途 " + module.getUsage() + " · 类型 " + module.getType()
                + " · 位置单位 " + param.getXu() + "/" + param.getYu());
        name.setText(fields.name());
        resource.setText(fields.resource());
        x.setText(fields.x());
        y.setText(fields.y());
        dx.setText(fields.dx());
        dy.setText(fields.dy());
        width.setText(fields.width());
        height.setText(fields.height());
        alpha.setText(fields.alpha());
        rotate.setText(fields.rotate());
        width.setDisable(!module.hasImage());
        height.setDisable(!module.hasImage());
        resource.setDisable(!VModuleResource.canEdit(module));
        refreshPreview(module);
        showRuntimeModule();
    }

    private void refreshScene() {
        sceneCanvas.getChildren().clear();
        Integer layer = sceneLayer.getValue();
        if (layer == null) {
            sceneStatus.setText("此皮肤没有可显示的图层");
            return;
        }
        if (!VSceneLayout.isFullScreenLayer(layer)) {
            sceneStatus.setText(layer == 2 || layer == 3
                    ? "游玩区层使用独立的赛道容器和变换；当前参考画布无法准确投影。"
                    : "该层没有已核实的全屏容器；暂不投影。");
            return;
        }
        VSceneLayout.Platform platform = scenePlatform.getValue();
        VSceneLayout.SceneContext context = VSceneLayout.REFERENCE.withPlatform(
                platform == null ? VSceneLayout.Platform.WINDOWS : platform);
        VScenePlan plan = VScenePlan.build(document, draft.skin(), layer, context, 640, 360);
        for (VScenePlan.Item item : plan.items()) {
            int index = item.index();
            SkinFile.Module module = item.module();
            ImageView node = VScenePlan.imageView(item);
            node.setCursor(Cursor.MOVE);
            node.setPickOnBounds(true);
            double[] drag = new double[4];
            boolean[] dragging = {false};
            node.setOnMousePressed(event -> {
                if (!applyModule()) return;
                modules.getSelectionModel().select(index);
                if (!draft.module(index).equals(module)) { refreshScene(); return; }
                drag[0] = event.getSceneX();
                drag[1] = event.getSceneY();
                drag[2] = node.getLayoutX();
                drag[3] = node.getLayoutY();
                dragging[0] = true;
                event.consume();
            });
            node.setOnMouseDragged(event -> {
                if (!dragging[0]) return;
                node.setLayoutX(drag[2] + event.getSceneX() - drag[0]);
                node.setLayoutY(drag[3] + event.getSceneY() - drag[1]);
                event.consume();
            });
            node.setOnMouseReleased(event -> {
                if (!dragging[0]) return;
                dragging[0] = false;
                double deltaX = event.getSceneX() - drag[0];
                double deltaY = event.getSceneY() - drag[1];
                if (Math.abs(deltaX) < .5 && Math.abs(deltaY) < .5) {
                    node.setLayoutX(drag[2]);
                    node.setLayoutY(drag[3]);
                    return;
                }
                String failure = null;
                try {
                    VSceneLayout.Offsets offsets = VSceneLayout.movedOffsets(draft.module(index), context,
                            640, 360, deltaX, deltaY);
                    draft.updateModule(index, draft.fields(index).withOffsets(offsets.dx(), offsets.dy()));
                    if (currentModule == index) showModule(index);
                } catch (IllegalArgumentException error) {
                    failure = error.getMessage();
                }
                refreshScene();
                if (failure != null) sceneStatus.setText("拖动失败：" + failure);
                event.consume();
            });
            sceneCanvas.getChildren().add(node);
        }
        sceneStatus.setText(plan.status());
    }

    private void loadRuntimeSnapshot() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择 Emiria 冻结帧模块快照");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON 快照", "*.json"));
        Path initial = runtimePath == null ? document.path().getParent() : runtimePath.getParent();
        if (initial != null && java.nio.file.Files.isDirectory(initial)) chooser.setInitialDirectory(initial.toFile());
        java.io.File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (selected == null) return;
        try {
            VRuntimeSnapshot loaded = VRuntimeSnapshot.load(selected.toPath());
            runtimeSnapshot = loaded;
            runtimePath = selected.toPath();
            verifyRuntimeSnapshot();
        } catch (IOException | IllegalArgumentException error) {
            runtimeSnapshot = null;
            runtimeFingerprintsMatch = false;
            runtimeStatus.setText("无法加载运行态快照：" + error.getMessage());
            runtimeDetails.clear();
        }
    }

    private void verifyRuntimeSnapshot() {
        if (runtimeSnapshot == null) return;
        try {
            boolean asmMatches = runtimeSnapshot.asmSha256().equalsIgnoreCase(document.asmSha256());
            boolean luaMatches = runtimeSnapshot.luaHash().equalsIgnoreCase(document.luaHash());
            runtimeFingerprintsMatch = asmMatches && luaMatches;
            if (!runtimeFingerprintsMatch) {
                runtimeStatus.setText("快照与当前保存版皮肤不匹配："
                        + (!asmMatches ? " info.asm SHA-256 不同；" : "")
                        + (!luaMatches ? " Lua hash 不同。" : ""));
                runtimeDetails.clear();
                return;
            }
            runtimeStatus.setText("info.asm 与 Lua 指纹一致（素材未核对） · " + runtimeSnapshot.moduleCount() + " 个模块 / "
                    + runtimeSnapshot.factoryCount() + " 个工厂 · 谱面 " + runtimeSnapshot.chartFile()
                    + " · 音频 " + runtimeSnapshot.audioTimeMs() + " ms · " + runtimeSnapshot.utc()
                    + (runtimeSnapshot.captureErrors().isEmpty() ? ""
                    : " · 采集错误 " + runtimeSnapshot.captureErrors().size() + "："
                    + runtimeSnapshot.captureErrors().getFirst()));
            showRuntimeModule();
        } catch (IOException error) {
            runtimeFingerprintsMatch = false;
            runtimeStatus.setText("无法校验当前保存版皮肤：" + error.getMessage());
            runtimeDetails.clear();
        }
    }

    private void showRuntimeModule() {
        if (!runtimeFingerprintsMatch || runtimeSnapshot == null) {
            runtimeDetails.clear();
            return;
        }
        if (currentModule < 0 || currentModule >= document.skin().getModulesCount()) {
            runtimeDetails.setText("从左侧选择组件，查看这次冻结帧的运行值。");
            return;
        }
        try {
            Optional<VRuntimeSnapshot.Module> match = runtimeSnapshot.match(document.skin(), currentModule);
            if (match.isEmpty()) {
                runtimeDetails.setText("保存版组件 #" + (currentModule + 1)
                        + " 在这次冻结帧中没有唯一对应项。它可能不属于背景/顶层，或被场景条件过滤。");
                return;
            }
            VRuntimeSnapshot.Module module = match.get();
            VRuntimeSnapshot.Source source = module.source();
            VRuntimeSnapshot.Runtime runtime = module.runtime();
            StringBuilder result = new StringBuilder();
            result.append("组件：").append(module.name()).append("\n工厂：")
                    .append(module.factoryName()).append(" · 图层 ").append(module.factoryLayer())
                    .append("\n\n原始 info.asm 参数\n")
                    .append("X / Y：").append(source.x()).append(' ').append(source.xUnit())
                    .append(" / ").append(source.y()).append(' ').append(source.yUnit())
                    .append("\n偏移 X / Y：").append(source.dx()).append(' ').append(source.dxUnit())
                    .append(" / ").append(source.dy()).append(' ').append(source.dyUnit())
                    .append("\n透明度：").append(source.alpha());
            if (source.hasImage()) result.append("\n图片：").append(source.imageFile())
                    .append("\n图片宽 / 高：").append(source.imageWidth()).append(' ')
                    .append(source.imageWidthUnit()).append(" / ").append(source.imageHeight())
                    .append(' ').append(source.imageHeightUnit());
            result.append("\n\n游戏运行时模块属性\nX / Y：").append(runtime.x()).append(" / ")
                    .append(runtime.y()).append("\n宽 / 高：").append(runtime.width()).append(" / ")
                    .append(runtime.height()).append("\n透明度：").append(runtime.alpha())
                    .append("\n缩放：").append(runtime.scale()).append(" · 旋转：").append(runtime.rotate());
            runtime.rectSize().ifPresent(value -> result.append("\nRect 尺寸：")
                    .append(value.x()).append(" × ").append(value.y()));
            runtime.anchoredPosition().ifPresent(value -> result.append("\nRect 锚点位置：")
                    .append(value.x()).append(" / ").append(value.y()));
            runtimeDetails.setText(result.toString());
        } catch (IllegalStateException error) {
            runtimeDetails.setText("无法唯一匹配保存版组件 #" + (currentModule + 1) + "：" + error.getMessage());
        }
    }

    private void refreshPreview(SkinFile.Module module) {
        preview.setImage(null);
        if (module.hasText()) {
            previewStatus.setText("文字模块：" + module.getText().getText());
            return;
        }
        String filename = VModuleResource.value(module);
        if (filename.isBlank()) {
            previewStatus.setText("此组件没有独立图片资源");
            return;
        }
        try {
            byte[] data = document.resource(filename);
            if (data == null) {
                previewStatus.setText("找不到资源：" + filename);
                return;
            }
            if (module.hasSound()) {
                previewStatus.setText(filename + " · 音频资源 · " + data.length + " 字节");
                return;
            }
            Image image = new Image(new ByteArrayInputStream(data));
            if (image.isError()) throw new IOException("不是可预览的图片");
            preview.setImage(image);
            previewStatus.setText(filename + " · " + (int) image.getWidth() + "×" + (int) image.getHeight());
        } catch (IOException error) {
            previewStatus.setText("资源读取失败：" + error.getMessage());
        }
    }

    private boolean applyModule() {
        if (currentModule < 0 || currentModule >= draft.moduleCount()) return true;
        try {
            draft.updateModule(currentModule, new VSkinEditModel.ModuleFields(name.getText(), resource.getText(),
                    x.getText(), y.getText(), dx.getText(), dy.getText(), width.getText(), height.getText(),
                    alpha.getText(), rotate.getText()));
            modules.getItems().set(currentModule, moduleLabel(currentModule));
            return true;
        } catch (NumberFormatException error) {
            alert("组件属性应为有效数字", error.getMessage());
            return false;
        }
    }

    /** Preserve edits when the separate V editor window is closed. */
    public boolean canClose() {
        if (!applyModule()) return false;
        draft.updateMetadata(title.getText(), creator.getText(), description.getText(), cover.getText());
        if (draft.skin().equals(document.skin()) && luaSource.getText().equals(luaBaseline.source())) return true;
        ButtonType saveChoice = new ButtonType("保存");
        ButtonType discardChoice = new ButtonType("不保存");
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "V 皮肤有未保存的修改。",
                saveChoice, discardChoice, ButtonType.CANCEL);
        confirm.setHeaderText("关闭皮肤编辑器");
        if (getScene() != null) confirm.initOwner(getScene().getWindow());
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) return false;
        return choice.get() == discardChoice || save();
    }

    private boolean save() {
        if (!applyModule()) return false;
        draft.updateMetadata(title.getText(), creator.getText(), description.getText(), cover.getText());
        try {
            if (luaSource.getText().equals(luaBaseline.source())) {
                document.save(draft.skin());
            } else {
                if (luaBaseline.originalBytes() == null) throw new IOException("Lua 文件不可编辑，请重新打开皮肤");
                document.save(draft.skin(), luaBaseline.path(), luaBaseline.originalBytes(),
                        VLuaSource.editedBytes(luaBaseline, luaSource.getText()));
            }
            luaBaseline = VLuaSource.load(document);
            luaSource.setText(luaBaseline.source());
            luaSource.setEditable(luaBaseline.originalBytes() != null);
            luaStatus.setText(luaBaseline.diagnostic());
            refreshScene();
            verifyRuntimeSnapshot();
            if (getScene() != null && getScene().getWindow() instanceof Stage stage) stage.setTitle("Malody V · " + title.getText());
            alert("已保存", document.path().toString());
            return true;
        } catch (IOException error) {
            alert("保存失败", error.getMessage());
            return false;
        }
    }

    private void alert(String heading, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(heading);
        alert.showAndWait();
    }
}
