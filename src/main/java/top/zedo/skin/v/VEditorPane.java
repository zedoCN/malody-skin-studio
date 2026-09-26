package top.zedo.skin.v;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
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
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Module and metadata editor for a Malody V skin. */
public final class VEditorPane extends BorderPane {
    private final MspSkinDocument document;
    private final VSkinEditModel draft;
    private final ListView<String> modules = new ListView<>();
    private final TextField title = new TextField();
    private final TextField creator = new TextField();
    private final TextArea description = new TextArea();
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
    private final Label sceneStatus = new Label();
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
        Integer firstLayer = null;
        int mostImages = -1;
        for (int layer : sceneLayer.getItems()) {
            int count = 0;
            for (SkinFile.Module module : draft.skin().getModulesList()) {
                if (module.getParam().getLayer() == layer && VSceneLayout.supports(module)) count++;
            }
            if (count > mostImages) { firstLayer = layer; mostImages = count; }
        }
        if (firstLayer != null) sceneLayer.getSelectionModel().select(firstLayer);
        Button refreshScene = new Button("应用属性并刷新");
        refreshScene.setOnAction(_ -> { if (applyModule()) refreshScene(); });
        HBox sceneTools = new HBox(8, new Label("图层"), sceneLayer, refreshScene);
        sceneTools.setAlignment(Pos.CENTER_LEFT);
        VBox sceneBox = new VBox(10, new Label("同层静态自定义图片布局概览 · 参考画布 16:9"), sceneTools,
                new ScrollPane(sceneCanvas), sceneStatus,
                new Label("依据 Emiria 基础位置、尺寸、pivot 和同层顺序；不等同游戏运行画面。"));
        sceneBox.setPadding(new Insets(12));
        Tab resourceTab = new Tab("单资源", imageBox);
        Tab sceneTab = new Tab("布局概览", sceneBox);
        resourceTab.setClosable(false);
        sceneTab.setClosable(false);
        TabPane previews = new TabPane(resourceTab, sceneTab);
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
    }

    private void refreshScene() {
        sceneCanvas.getChildren().clear();
        Integer layer = sceneLayer.getValue();
        if (layer == null) {
            sceneStatus.setText("此皮肤没有可显示的图层");
            return;
        }
        ArrayList<Integer> indices = new ArrayList<>();
        for (int i = 0; i < draft.moduleCount(); i++) {
            if (draft.module(i).getParam().getLayer() == layer) indices.add(i);
        }
        indices.sort(Comparator.comparingInt((Integer i) -> draft.module(i).getParam().getOrder())
                .thenComparingInt(i -> i));
        Map<String, Image> images = new HashMap<>();
        int shown = 0, unsupported = 0, missing = 0, capped = 0;
        for (int index : indices) {
            SkinFile.Module module = draft.module(index);
            if (!VSceneLayout.supports(module)) { unsupported++; continue; }
            if (shown >= 80) { capped++; continue; }
            String filename = module.getImage().getFile();
            Image image = images.get(filename);
            if (image == null) {
                try {
                    byte[] data = document.resource(filename);
                    if (data == null) { missing++; continue; }
                    image = new Image(new ByteArrayInputStream(data));
                    if (image.isError()) { missing++; continue; }
                    images.put(filename, image);
                } catch (IOException error) { missing++; continue; }
            }
            try {
                VSceneLayout.Placement at = VSceneLayout.project(module, 640, 360,
                        image.getWidth(), image.getHeight());
                ImageView node = new ImageView(image);
                node.setFitWidth(at.width());
                node.setFitHeight(at.height());
                node.setPreserveRatio(false);
                node.setLayoutX(at.left());
                node.setLayoutY(at.top());
                node.setOpacity(at.opacity());
                node.getTransforms().add(new Rotate(-at.rotate(),
                        at.width() * at.pivotX(), at.height() * (1 - at.pivotY())));
                sceneCanvas.getChildren().add(node);
                shown++;
            } catch (IllegalArgumentException error) { unsupported++; }
        }
        sceneStatus.setText("当前层 " + indices.size() + " 个模块；显示 " + shown + " 个静态图片，"
                + "跳过 " + unsupported + " 个动态/特殊模块，资源缺失或无法解码 " + missing + " 个。"
                + (capped > 0 ? "另有 " + capped + " 个超过 80 张显示上限。" : ""));
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

    private void save() {
        if (!applyModule()) return;
        draft.updateMetadata(title.getText(), creator.getText(), description.getText(), cover.getText());
        try {
            document.save(draft.skin());
            refreshScene();
            if (getScene() != null && getScene().getWindow() instanceof Stage stage) stage.setTitle("Malody V · " + title.getText());
            alert("已保存", document.path().toString());
        } catch (IOException error) {
            alert("保存失败", error.getMessage());
        }
    }

    private void alert(String heading, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(heading);
        alert.showAndWait();
    }
}
