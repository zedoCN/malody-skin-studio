package top.zedo.skin.uis.ui;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import top.zedo.skin.SkinConfig;
import top.zedo.skin.v.VEditorPane;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** One application window for the existing 4.x and V format editors. */
final class SkinStudioWindow extends BorderPane {
    private final UISEditor muiEditor;
    private final TabPane documents = new TabPane();
    private final Tab startTab;
    private final Tab muiTab;
    private final Map<Path, Tab> vTabs = new HashMap<>();

    SkinStudioWindow(UISEditor muiEditor) {
        this.muiEditor = muiEditor;
        getStyleClass().add("skin-studio-window");
        startTab = new Tab("开始", startPage());
        startTab.setClosable(false);
        muiTab = new Tab("4.x · UIS", muiEditor);
        muiTab.setClosable(false);
        documents.getStyleClass().add("studio-documents");
        documents.getTabs().add(startTab);
        documents.getSelectionModel().select(startTab);
        setCenter(documents);

        MenuItem open = new MenuItem("打开皮肤…");
        open.setOnAction(_ -> chooseAndOpen());
        MenuItem openDirectory = new MenuItem("打开 V 皮肤目录…");
        openDirectory.setOnAction(_ -> chooseAndOpenDirectory());
        MenuItem save = new MenuItem("保存当前皮肤");
        save.setOnAction(_ -> saveActive());
        save.disableProperty().bind(documents.getSelectionModel().selectedItemProperty().isEqualTo(startTab));
        Menu file = new Menu("文件");
        file.getItems().addAll(open, openDirectory, save);
        setTop(new MenuBar(file));

        muiEditor.setOpenFileRequest(this::chooseAndOpen);
        muiEditor.setVOpenRequest(this::openV);
        muiEditor.tabPane.getTabs().addListener((ListChangeListener<Tab>) _ -> {
            if (!muiEditor.tabPane.getTabs().isEmpty()) return;
            documents.getTabs().remove(muiTab);
            if (documents.getTabs().isEmpty()) documents.getTabs().add(startTab);
        });
    }

    void open(Path path) {
        Path filename = path.getFileName();
        String name = filename == null ? "" : filename.toString().toLowerCase(java.util.Locale.ROOT);
        if (Files.isDirectory(path) || name.endsWith(".msp") || name.equals("info.asm")) openV(path);
        else if (name.endsWith(".mui") || name.endsWith(".msz")) {
            Tab previouslySelected = documents.getSelectionModel().getSelectedItem();
            if (!documents.getTabs().contains(muiTab)) documents.getTabs().add(muiTab);
            documents.getSelectionModel().select(muiTab);
            muiEditor.openFIle(path);
            if (muiEditor.tabPane.getTabs().isEmpty()) {
                documents.getTabs().remove(muiTab);
                if (previouslySelected != null && documents.getTabs().contains(previouslySelected))
                    documents.getSelectionModel().select(previouslySelected);
                else {
                    if (!documents.getTabs().contains(startTab)) documents.getTabs().add(startTab);
                    documents.getSelectionModel().select(startTab);
                }
            } else {
                documents.getTabs().remove(startTab);
            }
        } else {
            new Alert(Alert.AlertType.ERROR, "不支持的皮肤文件: " + path).showAndWait();
        }
    }

    private StackPane startPage() {
        Label eyebrow = new Label("MALODY SKIN STUDIO");
        eyebrow.getStyleClass().add("studio-start-eyebrow");
        Label title = new Label("打开皮肤，开始编辑");
        title.getStyleClass().add("studio-start-title");
        Label description = new Label("在一个工作区编辑 4.3.7 UIS 和 Malody V 皮肤。");
        description.getStyleClass().add("studio-start-description");
        Button open = new Button("打开皮肤…");
        open.getStyleClass().add("studio-start-open");
        open.setOnAction(_ -> chooseAndOpen());
        Button openDirectory = new Button("打开 V 目录…");
        openDirectory.getStyleClass().add("studio-start-secondary");
        openDirectory.setOnAction(_ -> chooseAndOpenDirectory());
        Label shortcut = new Label("也可按 ⌘/Ctrl+O");
        shortcut.getStyleClass().add("studio-start-description");
        HBox actions = new HBox(12, open, openDirectory, shortcut);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox mui = formatCard("4.3.7 · UIS", ".mui  /  .msz",
                "编辑脚本、预览组件和动画。脚本修改自动保存。");
        VBox v = formatCard("Malody V", ".msp  /  info.asm",
                "查看静态布局、编辑组件和 Lua 源码。保存时写回皮肤。");
        HBox formats = new HBox(16, mui, v);
        HBox.setHgrow(mui, Priority.ALWAYS);
        HBox.setHgrow(v, Priority.ALWAYS);
        formats.setMaxWidth(760);

        VBox content = new VBox(18, eyebrow, title, description, actions, formats);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxWidth(760);
        StackPane page = new StackPane(content);
        page.getStyleClass().add("studio-start");
        page.setPadding(new Insets(48));
        return page;
    }

    private static VBox formatCard(String title, String extensions, String description) {
        Label name = new Label(title);
        name.getStyleClass().add("studio-format-title");
        Label formats = new Label(extensions);
        formats.getStyleClass().add("studio-format-extensions");
        Label detail = new Label(description);
        detail.setWrapText(true);
        detail.getStyleClass().add("studio-format-description");
        VBox card = new VBox(10, name, formats, detail);
        card.getStyleClass().add("studio-format-card");
        card.setPadding(new Insets(20));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinWidth(260);
        return card;
    }

    void chooseAndOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("打开 Malody 皮肤");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Malody 皮肤", "*.mui", "*.msz", "*.msp", "info.asm"));
        Path previous = Path.of(SkinConfig.data.lastOpenDir);
        if (Files.isDirectory(previous)) chooser.setInitialDirectory(previous.toAbsolutePath().toFile());
        File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (selected == null) return;
        open(selected.toPath());
        SkinConfig.data.lastOpenDir = selected.getParentFile().toString();
    }

    private void chooseAndOpenDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("打开 Malody V 皮肤目录");
        Path previous = Path.of(SkinConfig.data.lastOpenDir);
        if (Files.isDirectory(previous)) chooser.setInitialDirectory(previous.toAbsolutePath().toFile());
        File selected = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
        if (selected == null) return;
        open(selected.toPath());
        SkinConfig.data.lastOpenDir = selected.getParentFile().toString();
    }

    void saveActive() {
        Tab selected = documents.getSelectionModel().getSelectedItem();
        if (selected == null || selected == startTab) return;
        if (selected == muiTab) muiEditor.saveActiveMui();
        else if (selected.getContent() instanceof VEditorPane editor) editor.saveNow();
    }

    boolean canCloseAll() {
        for (Tab tab : documents.getTabs()) {
            if (tab.getContent() instanceof VEditorPane editor && !editor.canClose()) return false;
        }
        return muiEditor.canCloseAll();
    }

    private void openV(Path path) {
        try {
            Path key = path.getFileName().toString().equalsIgnoreCase("info.asm")
                    ? path.toRealPath().getParent() : path.toRealPath();
            Tab existing = vTabs.get(key);
            if (existing != null) {
                documents.getSelectionModel().select(existing);
                return;
            }
            VEditorPane editor = new VEditorPane(path);
            Tab tab = new Tab("V · " + key.getFileName(), editor);
            tab.setOnCloseRequest(event -> {
                if (!editor.canClose()) event.consume();
            });
            tab.setOnClosed(_ -> {
                vTabs.remove(key);
                Platform.runLater(() -> {
                    if (documents.getTabs().isEmpty()) documents.getTabs().add(startTab);
                });
            });
            vTabs.put(key, tab);
            documents.getTabs().add(tab);
            documents.getTabs().remove(startTab);
            documents.getSelectionModel().select(tab);
        } catch (IOException error) {
            new Alert(Alert.AlertType.ERROR, "无法打开 V 皮肤: " + error.getMessage()).showAndWait();
        }
    }

}
