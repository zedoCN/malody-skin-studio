package top.zedo.skin.v;

import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
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
import javafx.scene.control.SplitPane;
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
import javafx.util.StringConverter;
import javafx.util.Duration;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Module and metadata editor for a Malody V skin. */
public final class VEditorPane extends BorderPane {
    private final MspSkinDocument document;
    private final VSkinEditModel draft;
    private final Label heading;
    private final Label saveStatus = new Label("已保存");
    private final ListView<String> modules = new ListView<>();
    private final TextField moduleSearch = new TextField();
    private final Label moduleSearchStatus = new Label();
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
    private final Label inspectorStatus = new Label();
    private final PauseTransition previewDebounce = new PauseTransition(Duration.millis(300));
    private final Label moduleKind = new Label();
    private final Label previewStatus = new Label();
    private final ImageView preview = new ImageView();
    private final Pane sceneCanvas = new Pane();
    private final Map<Integer, ImageView> sceneNodes = new HashMap<>();
    private final Rectangle selectedOutline = new Rectangle();
    private final ComboBox<Integer> sceneLayer = new ComboBox<>();
    private final ComboBox<VSceneLayout.Platform> scenePlatform = new ComboBox<>();
    private final Label sceneStatus = new Label();
    private final Label selectedSceneStatus = new Label();
    private final VRuntimeComparePane runtimeCompare;
    private int currentModule = -1;
    private boolean changingSelection;
    private boolean updatingFields;

    public VEditorPane(Path path) throws IOException {
        getStyleClass().add("v-editor");
        document = MspSkinDocument.open(path);
        draft = new VSkinEditModel(document.skin());
        runtimeCompare = new VRuntimeComparePane(document);

        heading = new Label("Malody V · " + document.path().getFileName());
        heading.setMaxWidth(Double.MAX_VALUE);
        Button save = new Button("保存皮肤");
        save.setOnAction(_ -> save());
        save.getStyleClass().add("v-primary-action");
        saveStatus.getStyleClass().addAll("v-save-status", "v-status-saved");
        HBox toolbar = new HBox(12, heading, saveStatus, save);
        toolbar.getStyleClass().add("v-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10));
        HBox.setHgrow(heading, Priority.ALWAYS);
        setTop(toolbar);

        Label moduleHeading = sectionTitle("组件 · " + draft.moduleCount());
        moduleSearch.setPromptText("名称或资源名，回车定位");
        moduleSearch.setOnAction(_ -> locateModule());
        moduleSearchStatus.setText("选择组件后在画布上查看位置与属性");
        moduleSearchStatus.setWrapText(true);
        VBox navigator = new VBox(10, moduleHeading, moduleSearch, moduleSearchStatus, modules);
        navigator.getStyleClass().add("v-sidebar");
        navigator.setPadding(new Insets(12));
        navigator.setPrefWidth(270);
        navigator.setMinWidth(220);
        VBox.setVgrow(modules, Priority.ALWAYS);

        VBox metadata = new VBox(10, sectionTitle("皮肤信息"), row("标题", title), row("作者", creator),
                new Label("描述"), description, row("封面资源", cover));
        metadata.setPadding(new Insets(12));
        description.setPrefRowCount(3);

        Button previewChanges = new Button("更新预览（未保存）");
        Label previewHint = new Label("有效属性会自动更新预览；“保存皮肤”才写入文件。");
        previewHint.setWrapText(true);
        inspectorStatus.setWrapText(true);
        inspectorStatus.getStyleClass().add("v-inspector-error");
        inspectorStatus.visibleProperty().bind(inspectorStatus.textProperty().isNotEmpty());
        inspectorStatus.managedProperty().bind(inspectorStatus.visibleProperty());
        moduleKind.setWrapText(true);
        VBox properties = new VBox(8, sectionTitle("组件属性"), moduleKind, row("名称", name), row("资源或文字", resource),
                row("位置 X", x), row("位置 Y", y), row("偏移 X", dx), row("偏移 Y", dy),
                row("宽度", width), row("高度", height), row("透明度", alpha), row("旋转", rotate),
                inspectorStatus, previewChanges, previewHint);
        properties.getStyleClass().add("v-inspector");
        properties.setPadding(new Insets(12));
        properties.setPrefWidth(300);
        ScrollPane propertyScroll = new ScrollPane(properties);
        propertyScroll.setFitToWidth(true);
        propertyScroll.setPrefWidth(315);
        propertyScroll.setMinWidth(255);

        preview.setPreserveRatio(true);
        preview.setFitWidth(440);
        preview.setFitHeight(500);
        VBox imageBox = new VBox(10, sectionTitle("选中组件资源"), preview, previewStatus);
        imageBox.getStyleClass().add("v-preview-panel");
        imageBox.setAlignment(Pos.TOP_CENTER);
        imageBox.setPadding(new Insets(12));
        sceneCanvas.setPrefSize(640, 360);
        sceneCanvas.setMinSize(640, 360);
        sceneCanvas.setMaxSize(640, 360);
        sceneCanvas.setClip(new Rectangle(640, 360));
        sceneCanvas.setStyle("-fx-background-color: #20232a;");
        selectedOutline.getStyleClass().add("v-selection-outline");
        selectedOutline.setMouseTransparent(true);
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
        HBox sceneTools = new HBox(8, new Label("图层"), sceneLayer,
                new Label("平台"), scenePlatform);
        sceneTools.setAlignment(Pos.CENTER_LEFT);
        selectedSceneStatus.setWrapText(true);
        selectedSceneStatus.getStyleClass().add("v-selected-status");
        VBox sceneBox = new VBox(10, sectionTitle("布局概览"),
                new Label("原始参数 · 参考视口 1920×1080 · 不执行 Lua"), sceneTools,
                new ScrollPane(sceneCanvas), selectedSceneStatus, sceneStatus,
                new Label("可拖动图片调整偏移量；“保存皮肤”才写入文件。此画布不执行 Lua。"));
        sceneBox.getStyleClass().add("v-scene-panel");
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
        VBox luaBox = new VBox(8, sectionTitle("Lua 源码"), luaPath, luaStatus, luaSource);
        luaBox.getStyleClass().add("v-lua-panel");
        luaBox.setPadding(new Insets(12));
        VBox.setVgrow(luaSource, Priority.ALWAYS);
        Tab luaTab = new Tab("Lua 源码", luaBox);
        Tab runtimeTab = new Tab("运行态对照", runtimeCompare);
        Tab metadataTab = new Tab("皮肤信息", metadata);
        resourceTab.setClosable(false);
        sceneTab.setClosable(false);
        luaTab.setClosable(false);
        runtimeTab.setClosable(false);
        metadataTab.setClosable(false);
        TabPane previews = new TabPane(sceneTab, resourceTab, luaTab, runtimeTab, metadataTab);
        previews.getStyleClass().add("v-preview-tabs");
        previews.getSelectionModel().select(sceneTab);
        previewChanges.setOnAction(_ -> {
            previewDebounce.stop();
            if (applyModule()) {
                refreshScene();
                if (currentModule >= 0) refreshPreview(draft.module(currentModule));
                previews.getSelectionModel().select(sceneTab);
            }
        });
        for (TextField field : new TextField[]{name, resource, x, y, dx, dy, width, height, alpha, rotate}) {
            field.setOnAction(_ -> previewChanges.fire());
        }
        SplitPane workspace = new SplitPane(navigator, previews, propertyScroll);
        workspace.setDividerPositions(0.21, 0.75);
        setCenter(workspace);

        title.setText(draft.metadata().getTitle());
        creator.setText(draft.metadata().getCreator());
        description.setText(draft.metadata().getDesc());
        cover.setText(draft.metadata().getCover());
        for (int i = 0; i < draft.moduleCount(); i++) modules.getItems().add(moduleLabel(i));
        modules.getSelectionModel().selectedIndexProperty().addListener((_, oldIndex, newIndex) -> {
            if (changingSelection) return;
            previewDebounce.stop();
            int next = newIndex.intValue();
            if (next < 0 && oldIndex.intValue() >= 0) {
                changingSelection = true;
                try {
                    modules.getSelectionModel().select(oldIndex.intValue());
                } finally {
                    changingSelection = false;
                }
                return;
            }
            SkinFile.Module previous = currentModule >= 0 ? draft.module(currentModule) : null;
            if (!applyModule()) {
                changingSelection = true;
                modules.getSelectionModel().select(oldIndex.intValue());
                changingSelection = false;
                return;
            }
            currentModule = next;
            showModule(next);
            if (previous != null && !previous.equals(draft.module(oldIndex.intValue()))) refreshScene();
        });
        if (!modules.getItems().isEmpty()) modules.getSelectionModel().selectFirst();
        refreshScene();
        watchEdits();
        previewDebounce.setOnFinished(_ -> {
            if (!applyModule(false)) return;
            refreshScene();
            if (currentModule >= 0) refreshPreview(draft.module(currentModule));
        });
    }

    private static HBox row(String label, TextField field) {
        Label caption = new Label(label);
        caption.setMinWidth(78);
        HBox row = new HBox(7, caption, field);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    private static Label sectionTitle(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("v-section-title");
        return label;
    }

    private void watchEdits() {
        for (TextField field : new TextField[]{title, creator, cover, name, resource,
                x, y, dx, dy, width, height, alpha, rotate}) {
            field.textProperty().addListener((_, _, _) -> {
                field.getStyleClass().remove("v-invalid");
                markDirty();
                if (!updatingFields && field != title && field != creator && field != cover)
                    previewDebounce.playFromStart();
            });
        }
        description.textProperty().addListener((_, _, _) -> markDirty());
        luaSource.textProperty().addListener((_, _, _) -> markDirty());
    }

    private void markDirty() {
        if (updatingFields) return;
        saveStatus.setText("未保存的更改");
        saveStatus.getStyleClass().removeAll("v-status-saved", "v-status-error");
        if (!saveStatus.getStyleClass().contains("v-status-dirty"))
            saveStatus.getStyleClass().add("v-status-dirty");
    }

    private void markSaved() {
        saveStatus.setText("已保存");
        saveStatus.getStyleClass().removeAll("v-status-dirty", "v-status-error");
        if (!saveStatus.getStyleClass().contains("v-status-saved"))
            saveStatus.getStyleClass().add("v-status-saved");
    }

    private void markSaveError() {
        saveStatus.setText("保存失败");
        saveStatus.getStyleClass().removeAll("v-status-dirty", "v-status-saved");
        if (!saveStatus.getStyleClass().contains("v-status-error"))
            saveStatus.getStyleClass().add("v-status-error");
    }

    private void locateModule() {
        String query = moduleSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
        if (query.isEmpty()) {
            moduleSearchStatus.setText("输入名称或资源关键字后按回车");
            return;
        }
        for (int i = 0; i < draft.moduleCount(); i++) {
            String label = moduleLabel(i).toLowerCase(java.util.Locale.ROOT);
            String resourceName = VModuleResource.value(draft.module(i)).toLowerCase(java.util.Locale.ROOT);
            if (!label.contains(query) && !resourceName.contains(query)) continue;
            modules.getSelectionModel().select(i);
            if (modules.getSelectionModel().getSelectedIndex() != i) {
                moduleSearchStatus.setText("先修正右侧组件属性，再定位其他组件");
                return;
            }
            modules.scrollTo(i);
            moduleSearchStatus.setText("已定位组件 #" + (i + 1));
            return;
        }
        moduleSearchStatus.setText("没有找到“" + moduleSearch.getText().trim() + "”");
    }

    private String moduleLabel(int index) {
        SkinFile.Module module = draft.module(index);
        String text = module.hasMeta() ? module.getMeta().getDesc() : "";
        return (index + 1) + ". " + (text.isBlank() ? "(未命名)" : text) + " · " + moduleTypeName(module.getType());
    }

    /** Names follow Emiria's SkinModuleSubType; unknown IDs remain visible. */
    private static String moduleTypeName(int type) {
        return switch (type) {
            case 4900 -> "填充图片";
            case 5000 -> "图片";
            case 5001 -> "文字";
            case 5002 -> "帧动画";
            case 5003 -> "数字";
            case 5004 -> "颜色";
            case 5005 -> "视频";
            case 5006 -> "声音";
            default -> "类型 " + type;
        };
    }

    private static String unitName(SkinFile.ModuleParamUnit unit) {
        return switch (unit) {
            case Percent -> "%";
            case Unit -> "游戏单位";
            case PX -> "像素";
            case UNRECOGNIZED -> "未知";
        };
    }

    private void showModule(int index) {
        boolean selected = index >= 0 && index < draft.moduleCount();
        for (TextField field : new TextField[]{name, resource, x, y, dx, dy, width, height, alpha, rotate}) field.setDisable(!selected);
        if (!selected) {
            moduleKind.setText("");
            preview.setImage(null);
            previewStatus.setText("");
            selectedSceneStatus.setText("请选择组件，查看它能否出现在参考画布中。");
            runtimeCompare.selectModule(index);
            return;
        }
        SkinFile.Module module = draft.module(index);
        SkinFile.ModuleParam param = module.getParam();
        VSkinEditModel.ModuleFields fields = draft.fields(index);
        moduleKind.setText("用途 " + module.getUsage() + " · " + moduleTypeName(module.getType())
                + " (" + module.getType() + ")\n位置单位 X: " + unitName(param.getXu())
                + " · Y: " + unitName(param.getYu()));
        updatingFields = true;
        try {
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
        } finally {
            updatingFields = false;
        }
        width.setDisable(!module.hasImage());
        height.setDisable(!module.hasImage());
        resource.setDisable(!VModuleResource.canEdit(module));
        refreshPreview(module);
        runtimeCompare.selectModule(index);
        int layer = param.getLayer();
        if (VSceneLayout.isFullScreenLayer(layer) && !Integer.valueOf(layer).equals(sceneLayer.getValue())) {
            sceneLayer.getSelectionModel().select(Integer.valueOf(layer));
            refreshScene();
        } else {
            highlightSceneSelection();
            updateSelectedSceneStatus();
        }
    }

    private void refreshScene() {
        sceneCanvas.getChildren().clear();
        sceneNodes.clear();
        Integer layer = sceneLayer.getValue();
        if (layer == null) {
            sceneStatus.setText("此皮肤没有可显示的图层");
            updateSelectedSceneStatus();
            return;
        }
        if (!VSceneLayout.isFullScreenLayer(layer)) {
            sceneStatus.setText(layer == 2 || layer == 3
                    ? "游玩区层使用独立的赛道容器和变换；当前参考画布无法准确投影。"
                    : "该层没有已核实的全屏容器；暂不投影。");
            updateSelectedSceneStatus();
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
                selectedOutline.setVisible(false);
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
                    highlightSceneSelection();
                    return;
                }
                String failure = null;
                try {
                    VSceneLayout.Offsets offsets = VSceneLayout.movedOffsets(draft.module(index), context,
                            640, 360, deltaX, deltaY);
                    draft.updateModule(index, draft.fields(index).withOffsets(offsets.dx(), offsets.dy()));
                    markDirty();
                    if (currentModule == index) showModule(index);
                } catch (IllegalArgumentException error) {
                    failure = error.getMessage();
                }
                refreshScene();
                if (failure != null) sceneStatus.setText("拖动失败：" + failure);
                event.consume();
            });
            sceneCanvas.getChildren().add(node);
            sceneNodes.put(index, node);
        }
        sceneStatus.setText(plan.status());
        highlightSceneSelection();
        updateSelectedSceneStatus();
    }

    private void updateSelectedSceneStatus() {
        if (currentModule < 0 || currentModule >= draft.moduleCount()) return;
        SkinFile.Module module = draft.module(currentModule);
        int layer = module.getParam().getLayer();
        String reason;
        if (!VSceneLayout.isFullScreenLayer(layer)) {
            reason = layer == 2 || layer == 3
                    ? "游玩区层使用独立的赛道容器，参考画布无法准确投影。"
                    : "该层没有已核实的全屏容器，暂不投影。";
        } else if (!Integer.valueOf(layer).equals(sceneLayer.getValue())) {
            reason = "组件在图层 " + layer + "，当前查看图层 " + sceneLayer.getValue() + "。";
        } else if (sceneNodes.containsKey(currentModule)) {
            Bounds bounds = sceneNodes.get(currentModule).getBoundsInParent();
            reason = bounds.getMaxX() <= 0 || bounds.getMinX() >= 640
                    || bounds.getMaxY() <= 0 || bounds.getMinY() >= 360
                    ? "已投影，但位于参考画布外；可检查位置与偏移量。"
                    : "已在参考画布中描边；可拖动图片或修改右侧属性。";
        } else if (module.getType() != VSceneLayout.CUSTOM_IMAGE || module.getUsage() != 99
                || !module.hasImage()) {
            reason = "当前只投影静态自定义图片；此组件可在右侧编辑，如有独立资源可在“单资源”页查看。";
        } else if (module.getMeta().getDisabled()) {
            reason = "组件已停用，静态画布不显示。";
        } else if (module.getTriggersCount() > 0 || module.getAnimationsCount() > 0) {
            reason = "组件带触发器或动画，静态画布不执行这些行为。";
        } else if (module.getParam().getAnchorNote()) {
            reason = "组件使用音符锚点，参考画布无法投影。";
        } else if (module.getParam().getAlpha() <= 0) {
            reason = "透明度不大于 0，静态画布不显示。";
        } else if (module.getImage().getFile().isBlank()) {
            reason = "图片资源名为空，静态画布无法读取图片。";
        } else if (!VSceneLayout.isStaticImage(module)) {
            reason = "图片使用了参考画布尚未支持的资源、绘制、尺寸或位置参数。";
        } else {
            VSceneLayout.Platform platform = scenePlatform.getValue();
            VSceneLayout.SceneContext context = VSceneLayout.REFERENCE.withPlatform(
                    platform == null ? VSceneLayout.Platform.WINDOWS : platform);
            VSceneLayout.SceneMatch match = VSceneLayout.sceneMatch(module, context);
            reason = switch (match) {
                case MISMATCH -> "场景条件与当前参考视口或平台不匹配。";
                case UNKNOWN -> "场景条件无法由当前参考视口和平台可靠判定。";
                case MATCH -> unprojectedImageReason(module, context);
            };
        }
        selectedSceneStatus.setText("选中组件 #" + (currentModule + 1) + "：" + reason);
    }

    private String unprojectedImageReason(SkinFile.Module module, VSceneLayout.SceneContext context) {
        try {
            byte[] bytes = document.resource(module.getImage().getFile());
            if (bytes == null) return "找不到图片资源；可在“单资源”页核对路径。";
            Image image = new Image(new ByteArrayInputStream(bytes));
            if (image.isError()) return "图片资源无法解码；可在“单资源”页核对文件。";
            try {
                VSceneLayout.project(module, context, 640, 360, image.getWidth(), image.getHeight());
            } catch (IllegalArgumentException error) {
                return "图片尺寸无法投影：" + error.getMessage();
            }
            return "当前层最多显示 80 张图片，此组件超过显示上限。";
        } catch (IOException error) {
            return "图片资源读取失败：" + error.getMessage();
        }
    }

    private void highlightSceneSelection() {
        sceneCanvas.getChildren().remove(selectedOutline);
        ImageView selected = sceneNodes.get(currentModule);
        if (selected == null) return;
        Bounds bounds = selected.getBoundsInParent();
        selectedOutline.setX(bounds.getMinX() - 2);
        selectedOutline.setY(bounds.getMinY() - 2);
        selectedOutline.setWidth(bounds.getWidth() + 4);
        selectedOutline.setHeight(bounds.getHeight() + 4);
        selectedOutline.setVisible(true);
        sceneCanvas.getChildren().add(selectedOutline);
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
        return applyModule(true);
    }

    private boolean applyModule(boolean reportErrors) {
        if (currentModule < 0 || currentModule >= draft.moduleCount()) return true;
        try {
            draft.updateModule(currentModule, new VSkinEditModel.ModuleFields(name.getText(), resource.getText(),
                    x.getText(), y.getText(), dx.getText(), dy.getText(), width.getText(), height.getText(),
                    alpha.getText(), rotate.getText()));
            String label = moduleLabel(currentModule);
            if (!label.equals(modules.getItems().get(currentModule))) {
                changingSelection = true;
                try {
                    modules.getItems().set(currentModule, label);
                    modules.getSelectionModel().select(currentModule);
                } finally {
                    changingSelection = false;
                }
            }
            inspectorStatus.setText("");
            return true;
        } catch (NumberFormatException error) {
            if (!reportErrors) return false;
            String detail = error.getMessage();
            String fieldName = detail == null ? "" : detail.split(":", 2)[0];
            TextField invalid = switch (fieldName) {
                case "X" -> x;
                case "Y" -> y;
                case "偏移 X" -> dx;
                case "偏移 Y" -> dy;
                case "宽度" -> width;
                case "高度" -> height;
                case "透明度" -> alpha;
                case "旋转" -> rotate;
                default -> null;
            };
            if (invalid != null) {
                if (!invalid.getStyleClass().contains("v-invalid")) invalid.getStyleClass().add("v-invalid");
                invalid.requestFocus();
            }
            inspectorStatus.setText("请输入有效数字：" + (detail == null ? "组件属性" : detail));
            return false;
        }
    }

    /** Preserve edits when this V editor tab or the application is closed. */
    public boolean canClose() {
        previewDebounce.stop();
        if (!applyModule()) {
            ButtonType discardInvalid = new ButtonType("不保存，关闭");
            Alert confirmInvalid = new Alert(Alert.AlertType.CONFIRMATION,
                    "组件属性中有无效数字。关闭将丢弃未保存的修改。", discardInvalid, ButtonType.CANCEL);
            confirmInvalid.setHeaderText("无法保存当前修改");
            if (getScene() != null) confirmInvalid.initOwner(getScene().getWindow());
            return confirmInvalid.showAndWait().orElse(ButtonType.CANCEL) == discardInvalid;
        }
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

    public boolean saveNow() { return save(); }

    private boolean save() {
        previewDebounce.stop();
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
            updatingFields = true;
            try {
                luaSource.setText(luaBaseline.source());
            } finally {
                updatingFields = false;
            }
            luaSource.setEditable(luaBaseline.originalBytes() != null);
            luaStatus.setText(luaBaseline.diagnostic());
            refreshScene();
            runtimeCompare.refreshAfterSave();
            heading.setText("Malody V · " + title.getText());
            markSaved();
            return true;
        } catch (IOException error) {
            markSaveError();
            showError("保存失败", error.getMessage());
            return false;
        }
    }

    private void showError(String heading, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(heading);
        if (getScene() != null) alert.initOwner(getScene().getWindow());
        alert.showAndWait();
    }
}
