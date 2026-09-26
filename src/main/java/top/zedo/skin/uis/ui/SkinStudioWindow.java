package top.zedo.skin.uis.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
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
    private final Tab muiTab;
    private final Map<Path, Tab> vTabs = new HashMap<>();

    SkinStudioWindow(UISEditor muiEditor) {
        this.muiEditor = muiEditor;
        getStyleClass().add("skin-studio-window");
        muiTab = new Tab("4.x · UIS", muiEditor);
        muiTab.setClosable(false);
        documents.getStyleClass().add("studio-documents");
        documents.getTabs().add(muiTab);
        setCenter(documents);

        MenuItem open = new MenuItem("打开皮肤…");
        open.setOnAction(_ -> chooseAndOpen());
        MenuItem save = new MenuItem("保存当前皮肤");
        save.setOnAction(_ -> saveActive());
        Menu file = new Menu("文件");
        file.getItems().addAll(open, save);
        setTop(new MenuBar(file));

        muiEditor.setOpenFileRequest(this::chooseAndOpen);
        muiEditor.setVOpenRequest(this::openV);
    }

    void open(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".msp") || name.equals("info.asm")) openV(path);
        else {
            documents.getSelectionModel().select(muiTab);
            muiEditor.openFIle(path);
        }
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

    void saveActive() {
        Tab selected = documents.getSelectionModel().getSelectedItem();
        if (selected == null || selected == muiTab) muiEditor.saveActiveMui();
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
            tab.setOnClosed(_ -> vTabs.remove(key));
            vTabs.put(key, tab);
            documents.getTabs().add(tab);
            documents.getSelectionModel().select(tab);
        } catch (IOException error) {
            new Alert(Alert.AlertType.ERROR, "无法打开 V 皮肤: " + error.getMessage()).showAndWait();
        }
    }

}
