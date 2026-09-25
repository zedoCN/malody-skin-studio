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
    private SkinFile.Builder draft;
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
        draft = document.skin().toBuilder();

        Label heading = new Label("Malody V · " + document.path().getFileName());
        Button save = new Button("保存皮肤");
        save.setOnAction(_ -> save());
        HBox toolbar = new HBox(12, heading, save);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10));
        setTop(toolbar);

        VBox metadata = new VBox(7,
                new Label("皮肤信息"), row("标题", title), row("作者", creator), new Label("描述"), description,
                row("封面资源", cover), new Label("组件 (" + draft.getModulesCount() + ")"), modules);
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

        title.setText(draft.getMeta().getTitle());
        creator.setText(draft.getMeta().getCreator());
        description.setText(draft.getMeta().getDesc());
        cover.setText(draft.getMeta().getCover());
        for (int i = 0; i < draft.getModulesCount(); i++) modules.getItems().add(moduleLabel(i));
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
        SkinFile.Module module = draft.getModules(index);
        String text = module.hasMeta() ? module.getMeta().getDesc() : "";
        return (index + 1) + ". " + (text.isBlank() ? "(未命名)" : text) + " · " + module.getType();
    }

    private void showModule(int index) {
        boolean selected = index >= 0 && index < draft.getModulesCount();
        for (TextField field : new TextField[]{name, resource, x, y, dx, dy, width, height, alpha, rotate}) field.setDisable(!selected);
        if (!selected) {
            moduleKind.setText("");
            preview.setImage(null);
            previewStatus.setText("");
            return;
        }
        SkinFile.Module module = draft.getModules(index);
        SkinFile.ModuleParam param = module.getParam();
        moduleKind.setText("用途 " + module.getUsage() + " · 类型 " + module.getType()
                + " · 位置单位 " + param.getXu() + "/" + param.getYu());
        name.setText(module.getMeta().getDesc());
        resource.setText(resourceValue(module));
        x.setText(Float.toString(param.getX()));
        y.setText(Float.toString(param.getY()));
        dx.setText(Float.toString(param.getDx()));
        dy.setText(Float.toString(param.getDy()));
        width.setText(module.hasImage() ? Float.toString(module.getImage().getWidth()) : "");
        height.setText(module.hasImage() ? Float.toString(module.getImage().getHeight()) : "");
        alpha.setText(Integer.toString(param.getAlpha()));
        rotate.setText(Integer.toString(param.getRotate()));
        width.setDisable(!module.hasImage());
        height.setDisable(!module.hasImage());
        resource.setDisable(!(module.hasImage() || module.hasText()));
        refreshPreview(module);
    }

    private static String resourceValue(SkinFile.Module module) {
        if (module.hasImage()) return module.getImage().getFile();
        if (module.hasText()) return module.getText().getText();
        if (module.hasNumber()) return module.getNumber().getFile();
        if (module.hasNote() && module.getNote().getImagesCount() > 0) return module.getNote().getImages(0).getFile();
        if (module.hasImages() && module.getImages().getItemsCount() > 0) return module.getImages().getItems(0).getFile();
        return "";
    }

    private void refreshPreview(SkinFile.Module module) {
        preview.setImage(null);
        if (module.hasText()) {
            previewStatus.setText("文字模块：" + module.getText().getText());
            return;
        }
        String filename = resourceValue(module);
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
            Image image = new Image(new ByteArrayInputStream(data));
            if (image.isError()) throw new IOException("不是可预览的图片");
            preview.setImage(image);
            previewStatus.setText(filename + " · " + (int) image.getWidth() + "×" + (int) image.getHeight());
        } catch (IOException error) {
            previewStatus.setText("资源读取失败：" + error.getMessage());
        }
    }

    private boolean applyModule() {
        if (currentModule < 0 || currentModule >= draft.getModulesCount()) return true;
        try {
            SkinFile.Module.Builder module = draft.getModules(currentModule).toBuilder();
            if (!name.getText().equals(module.getMeta().getDesc())) module.getMetaBuilder().setDesc(name.getText());
            float newX = parseFinite(x), newY = parseFinite(y), newDx = parseFinite(dx), newDy = parseFinite(dy);
            int newAlpha = Integer.parseInt(alpha.getText().trim());
            int newRotate = Integer.parseInt(rotate.getText().trim());
            SkinFile.ModuleParam original = module.getParam();
            if (newX != original.getX() || newY != original.getY() || newDx != original.getDx()
                    || newDy != original.getDy() || newAlpha != original.getAlpha()
                    || newRotate != original.getRotate()) {
                module.getParamBuilder().setX(newX).setY(newY).setDx(newDx).setDy(newDy)
                        .setAlpha(newAlpha).setRotate(newRotate);
            }
            if (module.hasImage()) {
                float newWidth = parseFinite(width), newHeight = parseFinite(height);
                if (!resource.getText().equals(module.getImage().getFile())
                        || newWidth != module.getImage().getWidth() || newHeight != module.getImage().getHeight()) {
                    module.getImageBuilder().setFile(resource.getText()).setWidth(newWidth).setHeight(newHeight);
                }
            } else if (module.hasText()) {
                if (!resource.getText().equals(module.getText().getText())) module.getTextBuilder().setText(resource.getText());
            }
            draft.setModules(currentModule, module);
            modules.getItems().set(currentModule, moduleLabel(currentModule));
            return true;
        } catch (NumberFormatException error) {
            alert("组件属性应为有效数字", error.getMessage());
            return false;
        }
    }

    private static float parseFinite(TextField field) {
        float value = Float.parseFloat(field.getText().trim());
        if (!Float.isFinite(value)) throw new NumberFormatException(field.getText());
        return value;
    }

    private void save() {
        if (!applyModule()) return;
        draft.getMetaBuilder().setTitle(title.getText()).setCreator(creator.getText())
                .setDesc(description.getText()).setCover(cover.getText());
        try {
            document.save(draft.build());
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
