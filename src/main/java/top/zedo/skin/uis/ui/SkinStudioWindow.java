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
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
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
        MenuItem quickStart = new MenuItem("快速上手…");
        quickStart.setOnAction(_ -> showQuickStart());
        Menu help = new Menu("帮助");
        help.getItems().add(quickStart);
        setTop(new MenuBar(file, help));

        muiEditor.setOpenFileRequest(this::chooseAndOpen);
        muiEditor.setVOpenRequest(this::openV);
        muiEditor.tabPane.getTabs().addListener((ListChangeListener<Tab>) _ -> {
            if (!muiEditor.tabPane.getTabs().isEmpty()) return;
            documents.getTabs().remove(muiTab);
            if (documents.getTabs().isEmpty()) documents.getTabs().add(startTab);
        });
        addEventFilter(DragEvent.DRAG_OVER, event -> {
            if (event.getDragboard().hasFiles()
                    && event.getDragboard().getFiles().stream().anyMatch(SkinStudioWindow::supportsDrop)) {
                event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            }
        });
        addEventFilter(DragEvent.DRAG_DROPPED, event -> {
            if (!event.getDragboard().hasFiles()) return;
            var files = event.getDragboard().getFiles().stream().filter(SkinStudioWindow::supportsDrop).toList();
            if (files.isEmpty()) return;
            event.setDropCompleted(true);
            event.consume();
            Platform.runLater(() -> {
                for (File droppedFile : files) {
                    open(droppedFile.toPath());
                    Path parent = droppedFile.toPath().toAbsolutePath().getParent();
                    if (parent != null) SkinConfig.data.lastOpenDir = parent.toString();
                }
            });
        });
    }

    private static boolean supportsDrop(File file) {
        Path path = file.toPath();
        if (Files.isDirectory(path)) return Files.isRegularFile(path.resolve("info.asm"));
        if (!Files.isRegularFile(path)) return false;
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".mui") || name.endsWith(".msz")
                || name.endsWith(".msp") || name.equals("info.asm");
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
        Label shortcut = new Label("也可拖入皮肤文件，或按 ⌘/Ctrl+O");
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

    private void showQuickStart() {
        Alert guide = new Alert(Alert.AlertType.INFORMATION);
        guide.setTitle("Malody Skin Studio · 快速上手");
        guide.setHeaderText("打开皮肤后，左边编辑，右边预览");
        guide.setContentText("4.x UIS（.mui / .msz）\n"
                + "• 可将皮肤文件直接拖入窗口打开。\n"
                + "• 修改左侧脚本会自动写回原文件或皮肤包；也可按 ⌘/Ctrl+S 保存。\n"
                + "• 用“适应预览”看完整画面；缩放滑块放大后可在预览区滚动查看。\n"
                + "• 顶部切换设备和屏幕比例；底部播放、暂停或重放动画。\n"
                + "• 预览中部分普通图片可以直接拖动位置。\n\n"
                + "Malody V（.msp / info.asm / 皮肤目录）\n"
                + "• 左侧输入名称或资源名即可筛选组件；Esc 清除筛选。\n"
                + "• 修改组件属性或 Lua 源码后，点击“保存皮肤”写回文件。\n"
                + "• 用顶部按钮或 ⌘/Ctrl+Z、⌘/Ctrl+Shift+Z 撤销和重做；画布拖动也可撤销。\n\n"
                + "建议先复制一份皮肤，再尝试修改。");
        guide.showAndWait();
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
