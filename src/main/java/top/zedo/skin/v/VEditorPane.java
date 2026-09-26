package top.zedo.skin.v;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;

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
        HBox content = new HBox(propertyScroll, imageBox);
        HBox.setHgrow(imageBox, Priority.ALWAYS);
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
