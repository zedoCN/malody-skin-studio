package top.zedo.skin.uis.ui;

import javafx.application.Platform;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import top.zedo.skin.DeviceType;
import top.zedo.skin.ResolutionInfo;
import top.zedo.skin.SkinConfig;
import top.zedo.skin.uis.UISCanvas;
import top.zedo.skin.uis.SkinSnapshot;
import top.zedo.skin.uis.SkinTrace;
import top.zedo.skin.uis.MuiAudit;
import top.zedo.skin.uis.MszAudit;
import top.zedo.skin.uis.MuiPositionEditor;
import top.zedo.skin.uis.MszWorkspace;
import top.zedo.skin.uis.component.ImageComponentRenderer;
import top.zedo.skin.v.MspInspector;
import top.zedo.skin.v.VEditorPane;
import top.zedo.zxncore.ZXLogger;
import top.zedo.zxncore.ZXVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UISEditor extends HBox {

    public static final ZXVersion VERSION = new ZXVersion(1, 1, 0, ZXVersion.ReleaseStatus.BETA);
    private DragSession dragSession;
    private boolean previewValid = true;
    private final Map<Tab, MszWorkspace> packageTabs = new HashMap<>();
    Label previewStatus = new Label();
    UISCanvas uisCanvas = new UISCanvas() {
        {
            setBorder(new Border(new BorderStroke(Color.WHITE, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(1), new Insets(0))));
        }
    };
    TabPane tabPane = new TabPane() {
        {
            getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null)
                    if (newValue.getContent() instanceof VirtualizedScrollPane code) {
                        if (code.getContent() instanceof UISCodeArea codeArea) {
                            try {
                                uisCanvas.loadSkin(codeArea.getFile());
                                previewValid = true;
                                previewStatus.setText("");
                            } catch (IOException error) {
                                showPreviewError(error, false);
                            }
                        }
                    }
            });
            VBox.setVgrow(this, Priority.ALWAYS);
            setPrefWidth(640);
            setMinWidth(640);
            setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
        }
    };
    //private File lastDirectory = new File(System.getProperty("user.dir")); // 记录上一次选择的目录
    Button openFileButton = new Button("打开皮肤") {
        {
            setOnAction(event -> {
                Path file = Path.of(SkinConfig.data.lastOpenDir);
                // 创建文件选择器
                FileChooser fileChooser = new FileChooser();
                // 设置初始目录为上一次选择的目录
                if (Files.exists(file) & Files.isDirectory(file)) {
                    fileChooser.setInitialDirectory(file.toAbsolutePath().toFile());
                }
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Malody 皮肤", "*.mui", "*.msz", "*.msp", "info.asm"));

                // 显示文件选择对话框
                File selectedFile = fileChooser.showOpenDialog(null);

                if (selectedFile != null) {
                    System.out.println("Selected File: " + selectedFile.getAbsolutePath());

                    openFIle(selectedFile.toPath());
                    // 记录本次选择的目录
                    SkinConfig.data.lastOpenDir = selectedFile.getParentFile().toString();
                } else {
                    System.out.println("No file selected.");
                }

            });
        }
    };
    Button saveFileButton = new Button("保存") {
        {
            setOnAction(_ -> saveActiveMui());
        }
    };
    /**
     * 设备类型选择框
     */
    ChoiceBox<DeviceType> deviceTypeChoiceBox = new ChoiceBox<>() {
        {
            getItems().addAll(DeviceType.values());
            setPrefWidth(60);
            getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                try {
                    uisCanvas.setDeviceType(newValue);
                    previewValid = true;
                    previewStatus.setText("");
                } catch (IOException error) {
                    showPreviewError(error, false);
                }
            });
            //getSelectionModel().selectLast();
        }
    };
    /**
     * 屏幕比例选择框
     */
    ChoiceBox<ResolutionInfo> resolutionChoiceBox = new ChoiceBox<>() {
        {
            getItems().addAll(ResolutionInfo.values());
            setPrefWidth(100);
            getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                deviceTypeChoiceBox.setValue(newValue.getDevice());
                try {
                    uisCanvas.setAspectRatio(newValue.getAspectRatio());
                    previewValid = true;
                    previewStatus.setText("");
                } catch (IOException error) {
                    showPreviewError(error, false);
                }
            });
            getSelectionModel().selectLast();
        }
    };
    /**
     * 单位选择框
     */
    ChoiceBox<UnitInfo> unitChoiceBox = new ChoiceBox<>() {
        {
            getItems().addAll(
                    new UnitInfo("像素", "px", 1),
                    new UnitInfo("游戏像素", "gpx", 0),
                    new UnitInfo("百分比", "%", 2)
            );
            valueProperty().addListener((observable, oldValue, newValue) -> {
                uisCanvas.measureRuler.unit = newValue;
            });
            getSelectionModel().selectFirst();
            setPrefWidth(80);
        }
    };
    Label scalingFactorLabel = new Label("缩放: 100%") {
        {
            setPrefWidth(70);
        }
    };
    /**
     * 缩放因子滑块
     */
    Slider scalingFactorSlider = new Slider() {
        {
            setMin(0.2);
            setMax(4.0);
            setBlockIncrement(0.2);
            setMajorTickUnit(0.4);
            //setShowTickMarks(true);
            setShowTickLabels(true);
            setMinorTickCount(1);
            setValue(1);
            setPrefWidth(100);
            setSnapToTicks(true);
            setSnapToPixel(true);
            valueProperty().addListener((observable, oldValue, newValue) -> {
                if (!isValueChanging()) {
                    scalingFactorLabel.setText("缩放: " + (int) (newValue.doubleValue() * 100) + "%");
                    try {
                        uisCanvas.setZoomRate(newValue.doubleValue());
                        previewValid = true;
                        previewStatus.setText("");
                    } catch (IOException error) {
                        showPreviewError(error, false);
                    }
                }
            });
        }
    };
    /**
     * 顶部工具栏
     */
    HBox topToolbar = new HBox(resolutionChoiceBox, deviceTypeChoiceBox, unitChoiceBox, scalingFactorLabel, scalingFactorSlider, openFileButton, saveFileButton, previewStatus) {
        {
            setMinHeight(40);
            setBorder(new Border(new BorderStroke(Color.WHITE, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(0, 0, 1, 0), new Insets(0))));
            setAlignment(Pos.CENTER_LEFT);
            setSpacing(8);
            setPadding(new Insets(0, 8, 0, 8));
        }
    };
    CheckBox autoReplayCheckBox = new CheckBox("自动重播") {
        {
            setSelected(true);
        }
    };
    Button replayButton = new Button("重放") {
        {
            setOnAction(event -> uisCanvas.resetTime());
        }
    };
    Button playButton = new Button("播放") {
        {
            setOnAction(_ -> uisCanvas.play());
        }
    };
    Button pauseButton = new Button("暂停") {
        {
            setOnAction(_ -> uisCanvas.pause());
        }
    };
    Label timeLabel = new Label() {
        {
            setPrefWidth(80);
            setAlignment(Pos.CENTER_RIGHT);
        }
    };
    Slider timeLineSlider = new Slider(-3.5, 30, -3) {
        {
            uisCanvas.time.addListener((observable, oldValue, newValue) -> {
                if (!uisCanvas.isPaused)
                    valueProperty().set(newValue.doubleValue() / 1000.);
            });


            HBox.setHgrow(this, Priority.ALWAYS);
            setPrefWidth(240);

            //设置块增量
            setBlockIncrement(0.01);
            //设置主要刻度单位
            setMajorTickUnit(0.5);
            setShowTickLabels(true);
            setMinorTickCount(1);
            setSnapToTicks(true);
            setSnapToPixel(true);
            valueProperty().addListener((observable, oldValue, newValue) -> {
                if (isValueChanging()) {
                    uisCanvas.setTime((long) (newValue.doubleValue() * 1000));
                }
            });
        }
    };
    HBox bottomToolbar = new HBox(autoReplayCheckBox, timeLabel, timeLineSlider, playButton, pauseButton, replayButton) {
        {
            setMinHeight(40);
            setBorder(new Border(new BorderStroke(Color.WHITE, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(1, 0, 0, 0), new Insets(0))));
            setAlignment(Pos.CENTER_LEFT);
            setSpacing(8);
            setPadding(new Insets(0, 8, 0, 8));
        }
    };
    VBox sideVBox = new VBox(topToolbar, tabPane, bottomToolbar) {
        {
            setHgrow(this, Priority.ALWAYS);
            setPrefWidth(260);
            setBackground(Background.fill(Color.BLACK));
            setBorder(new Border(new BorderStroke(Color.WHITE, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(0, 1, 0, 0), new Insets(0))));
        }
    };

    public UISEditor() {


        setAlignment(Pos.CENTER_LEFT);
        uisCanvas.addEventFilter(MouseEvent.MOUSE_PRESSED, this::beginPositionDrag);
        uisCanvas.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::previewPositionDrag);
        uisCanvas.addEventFilter(MouseEvent.MOUSE_RELEASED, this::finishPositionDrag);


        uisCanvas.minWidthProperty().addListener((observable, oldValue, newValue) -> {
            this.setMinWidth(newValue.doubleValue() + tabPane.getMinWidth());
        });
        uisCanvas.minHeightProperty().addListener((observable, oldValue, newValue) -> {
            this.setMinHeight(newValue.doubleValue() + 40);
        });


        /*pane.widthProperty().addListener(observable -> {
            canvas.setWidth(pane.getWidth());
        });
        pane.heightProperty().addListener(observable ->{
            canvas.setHeight(pane.getHeight());
        });*/
        setBackground(Background.fill(Color.BLACK));





        /*canvas.setOnMousePressed(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY)) {
                measuringRulerRenderer.setPos = 1;
            } else {
                measuringRulerRenderer.setPos = 2;
            }
        });
        canvas.setOnMouseDragged(event -> {
            if (event.isShiftDown()) {
                measuringRulerRenderer.state = 1;
            } else if (event.isAltDown()) {
                measuringRulerRenderer.state = 2;
            } else {
                measuringRulerRenderer.state = 0;
            }
            switch (measuringRulerRenderer.setPos) {
                case 1 -> measuringRulerRenderer.setPos1(event.getX(), event.getY());
                case 2 -> measuringRulerRenderer.setPos2(event.getX(), event.getY());
            }
        });
        canvas.setOnMouseReleased(event -> measuringRulerRenderer.setPos = 0);*/
        //画布更新线程常驻
        AnimationTimer animationTimer = new AnimationTimer() {
            @Override
            public void handle(long l) {
                uisCanvas.draw();
            }
        };


        animationTimer.start();
        HBox.setHgrow(uisCanvas, Priority.ALWAYS);
        getChildren().addAll(sideVBox, uisCanvas);
    }    /*Button reloadButton = new Button("重载") {
        {
            setOnAction(event -> uisCanvas.updateSkin());
        }
    };*/

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--snapshot")) {
            SkinSnapshot.run(args);
            return;
        }
        if (args.length > 0 && args[0].equals("--trace-mui")) {
            SkinTrace.run(args);
            return;
        }
        if (args.length > 0 && args[0].equals("--inspect-v")) {
            try {
                MspInspector.main(java.util.Arrays.copyOfRange(args, 1, args.length));
            } catch (IOException error) {
                throw new RuntimeException(error);
            }
            return;
        }
        if (args.length > 0 && args[0].equals("--audit-mui")) {
            try {
                MuiAudit.main(java.util.Arrays.copyOfRange(args, 1, args.length));
            } catch (IOException error) {
                throw new RuntimeException(error);
            }
            return;
        }
        if (args.length > 0 && args[0].equals("--audit-msz")) {
            try {
                MszAudit.main(java.util.Arrays.copyOfRange(args, 1, args.length));
            } catch (IOException error) {
                throw new RuntimeException(error);
            }
            return;
        }
        /*if (args.length == 1 & args[0].equals("DEBUG"))
            DEBUG = true;*/
        ZXLogger.info("===== > Malody Skin Studio < =====");
        ZXLogger.info("Version: " + VERSION + " Code: " + VERSION.getVersionCode());
        switch (VERSION.status()) {
            case RC -> {
                ZXLogger.warning("当前为 [内部测试版本] 请不要泄漏软件到外部");
            }
            case BETA -> {
                ZXLogger.warning("当前为 [提前预览版本] 如有问题请联系开发者");
            }
            case ALPHA -> {
                ZXLogger.warning("当前为 [早期开发版本] 请谨慎测试软件功能");
            }
            case STABLE -> {
                ZXLogger.info("当前为 [稳定发布版本] 请尽情使用");
            }
        }

        ZXLogger.info("Malody Skin Studio 启动");


        ZXLogger.info("初始化图形系统");

        Platform.startup(() -> {
            //初始化 (载入配置 使用资源)
            ZXLogger.info("初始化配置");
            UISEditor uISEditor = new UISEditor();
            Scene scene = new Scene(uISEditor);
            scene.getAccelerators().put(KeyCombination.keyCombination("Shortcut+S"), uISEditor::saveActiveMui);
            scene.getStylesheets().addAll("resources/baseExpansionPack/color/style.css");
            scene.getStylesheets().addAll("resources/baseExpansionPack/color/dark.css");
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.setTitle("Malody Skin Studio " + VERSION);
            stage.show();
            if (args.length > 0) {
                String filename = String.join(" ", args);
                if (filename.length() >= 2 && filename.startsWith("\"") && filename.endsWith("\"")) {
                    filename = filename.substring(1, filename.length() - 1);
                }
                Path initialFile = Path.of(filename);
                if (Files.isRegularFile(initialFile)) {
                    uISEditor.openFIle(initialFile);
                } else {
                    ZXLogger.warning("找不到皮肤文件: " + initialFile);
                }
            }
            stage.setOnCloseRequest(event -> {
                for (Tab tab : uISEditor.tabPane.getTabs()) {
                    if (tab.getContent() instanceof VirtualizedScrollPane<?> pane
                            && pane.getContent() instanceof UISCodeArea codeArea) {
                        try {
                            codeArea.saveNow();
                        } catch (IOException error) {
                            showSaveError(codeArea, error);
                            event.consume();
                            return;
                        }
                    }
                }
                for (MszWorkspace workspace : uISEditor.packageTabs.values()) {
                    try {
                        workspace.syncPending();
                    } catch (IOException error) {
                        new Alert(Alert.AlertType.ERROR, "无法同步 MSZ: " + error.getMessage()).showAndWait();
                        event.consume();
                        return;
                    }
                }
                for (MszWorkspace workspace : uISEditor.packageTabs.values()) {
                    try {
                        workspace.close();
                    } catch (IOException error) {
                        new Alert(Alert.AlertType.ERROR, "无法清理 MSZ 临时目录: " + error.getMessage()).showAndWait();
                        event.consume();
                        return;
                    }
                }
                for (Tab tab : uISEditor.tabPane.getTabs()) {
                    if (tab.getContent() instanceof VirtualizedScrollPane<?> pane
                            && pane.getContent() instanceof UISCodeArea codeArea) {
                        try {
                            codeArea.closeSafely();
                        } catch (IOException error) {
                            showSaveError(codeArea, error);
                            event.consume();
                            return;
                        }
                    }
                }
                SkinConfig.save();
                System.exit(0);
            });
            uISEditor.minWidthProperty().addListener((observable, oldValue, newValue) -> {
                stage.setMinWidth(newValue.doubleValue());
            });
            uISEditor.minHeightProperty().addListener((observable, oldValue, newValue) -> {
                stage.setMinHeight(newValue.doubleValue());
            });
        });


    }

    public void openFIle(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        if (filename.endsWith(".msp") || filename.equals("info.asm")) {
            try {
                VEditorPane editor = new VEditorPane(path);
                Stage window = new Stage();
                window.setScene(new Scene(editor, 1190, 760));
                window.setTitle("Malody V · " + path.getFileName());
                window.show();
            } catch (IOException error) {
                new Alert(Alert.AlertType.ERROR, "无法打开 V 皮肤: " + error.getMessage()).showAndWait();
            }
            return;
        }
        if (filename.endsWith(".msz")) {
            openMsz(path);
            return;
        }
        try {
            openMuiTab(path, null, path.getFileName().toString());
        } catch (IOException error) {
            new Alert(Alert.AlertType.ERROR, "无法打开 MUI 皮肤: " + error.getMessage()).showAndWait();
        }
    }

    private void openMsz(Path path) {
        try {
            Path archive = path.toRealPath();
            for (Map.Entry<Tab, MszWorkspace> entry : packageTabs.entrySet()) {
                if (entry.getValue().archive().equals(archive)) {
                    tabPane.getSelectionModel().select(entry.getKey());
                    previewStatus.setText("此 MSZ 已打开；关闭标签页后可选择另一脚本");
                    return;
                }
            }
            MszWorkspace workspace = MszWorkspace.open(path);
            String selected;
            if (workspace.scriptEntries().size() == 1) {
                selected = workspace.scriptEntries().getFirst();
            } else {
                ChoiceDialog<String> choice = new ChoiceDialog<>(workspace.scriptEntries().getFirst(), workspace.scriptEntries());
                choice.setTitle("选择 MSZ 脚本");
                choice.setHeaderText(path.getFileName() + " 包含多个 .mui 脚本");
                choice.setContentText("编辑脚本：");
                Optional<String> answer = choice.showAndWait();
                if (answer.isEmpty()) {
                    workspace.close();
                    return;
                }
                selected = answer.get();
            }
            try {
                openMuiTab(workspace.scriptPath(selected), workspace,
                        path.getFileName() + " / " + selected);
            } catch (IOException | RuntimeException error) {
                workspace.close();
                throw error;
            }
        } catch (IOException | RuntimeException error) {
            new Alert(Alert.AlertType.ERROR, "无法打开 MSZ 皮肤: " + error.getMessage()).showAndWait();
        }
    }

    private void openMuiTab(Path path, MszWorkspace workspace, String title) throws IOException {
        Tab tab = new Tab();
        tab.setText(title);
        UISCodeArea uisCodeArea = new UISCodeArea(path, () -> {
            if (workspace != null) {
                try {
                    workspace.syncScript(path);
                } catch (IOException error) {
                    previewStatus.setText("MSZ 保存失败");
                    previewStatus.setTooltip(new Tooltip(error.getMessage()));
                    throw error;
                }
            }
            refreshPreview(true);
        });
        uisCodeArea.setAutoHeight(true);
        uisCodeArea.setAutoScrollOnDragDesired(true);
        VirtualizedScrollPane<UISCodeArea> vsPane = new VirtualizedScrollPane<>(uisCodeArea);
        VBox.setVgrow(vsPane, Priority.ALWAYS);
        tab.setContent(vsPane);
        tab.setOnCloseRequest(event -> {
            try {
                uisCodeArea.saveNow();
                if (workspace != null) workspace.close();
                uisCodeArea.closeSafely();
                packageTabs.remove(tab);
            } catch (IOException error) {
                showSaveError(uisCodeArea, error);
                event.consume();
            }
        });
        if (workspace != null) packageTabs.put(tab, workspace);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        //uisCanvas.loadSkin(path);
    }

    private void saveActiveMui() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null || !(tab.getContent() instanceof VirtualizedScrollPane<?> pane)
                || !(pane.getContent() instanceof UISCodeArea codeArea)) return;
        try {
            codeArea.saveNow();
            MszWorkspace workspace = packageTabs.get(tab);
            if (workspace != null) {
                workspace.syncPending();
                refreshPreview(true);
            }
        } catch (IOException error) {
            showSaveError(codeArea, error);
        }
    }

    private void refreshPreview(boolean saved) {
        try {
            uisCanvas.updateSkin();
            previewValid = true;
            previewStatus.setText(saved ? "已保存" : "");
            previewStatus.setTooltip(null);
        } catch (IOException error) {
            showPreviewError(error, saved);
        }
    }

    private void showPreviewError(IOException error, boolean saved) {
        previewValid = false;
        previewStatus.setText(saved ? "已保存，预览失败" : "预览失败");
        previewStatus.setTooltip(new Tooltip(error.getMessage()));
        ZXLogger.warning("预览 MUI 失败: " + error.getMessage());
    }

    private void beginPositionDrag(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) return;
        try {
            for (Tab tab : tabPane.getTabs()) {
                UISCodeArea area = codeArea(tab);
                if (area != null) area.saveNow();
            }
        } catch (IOException error) {
            previewStatus.setText("保存失败，无法拖拽");
            previewStatus.setTooltip(new Tooltip(error.getMessage()));
            return;
        }
        if (!previewValid) return;
        Point2D point = uisCanvas.sceneToLocal(event.getSceneX(), event.getSceneY());
        ImageComponentRenderer image = uisCanvas.pickEditableImage(point.getX(), point.getY());
        if (image == null) return;
        dragSession = new DragSession(image, point.getX(), point.getY(),
                image.pos.getX(), image.pos.getY());
        previewStatus.setText("拖动 " + image.getComponent().getFullName());
        event.consume();
    }

    private void previewPositionDrag(MouseEvent event) {
        if (dragSession == null) return;
        Point2D point = uisCanvas.sceneToLocal(event.getSceneX(), event.getSceneY());
        dragSession.image().pos.setX(dragSession.originalX() + point.getX() - dragSession.mouseX());
        dragSession.image().pos.setY(dragSession.originalY() + point.getY() - dragSession.mouseY());
        event.consume();
    }

    private void finishPositionDrag(MouseEvent event) {
        DragSession session = dragSession;
        if (session == null) return;
        dragSession = null;
        event.consume();
        Point2D point = uisCanvas.sceneToLocal(event.getSceneX(), event.getSceneY());
        double dx = point.getX() - session.mouseX();
        double dy = point.getY() - session.mouseY();
        if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) {
            session.restore();
            previewStatus.setText("");
            return;
        }
        boolean sourceUpdated = false;
        try {
            MuiPositionEditor.Edit edit = MuiPositionEditor.prepare(session.image().getComponent(), dx, dy);
            UISCodeArea open = findOpenCodeArea(edit.file());
            if (open == null) {
                edit.save();
                sourceUpdated = true;
                MszWorkspace workspace = workspaceFor(edit.file());
                if (workspace != null) workspace.syncScript(edit.file());
                refreshPreview(true);
            } else {
                open.replaceText(edit.document().text());
                sourceUpdated = true;
                open.saveNow();
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            session.restore();
            if (sourceUpdated) previewValid = false;
            previewStatus.setText("拖拽未保存");
            previewStatus.setTooltip(new Tooltip(error.getMessage()));
            ZXLogger.warning("拖拽 MUI 组件失败: " + error.getMessage());
        }
    }

    private UISCodeArea findOpenCodeArea(Path file) throws IOException {
        for (Tab tab : tabPane.getTabs()) {
            UISCodeArea area = codeArea(tab);
            if (area != null && area.getFile().toRealPath().equals(file.toRealPath())) return area;
        }
        return null;
    }

    private MszWorkspace workspaceFor(Path file) {
        for (MszWorkspace workspace : packageTabs.values()) {
            if (workspace.owns(file)) return workspace;
        }
        return null;
    }

    private static UISCodeArea codeArea(Tab tab) {
        if (tab.getContent() instanceof VirtualizedScrollPane<?> pane
                && pane.getContent() instanceof UISCodeArea area) return area;
        return null;
    }

    private record DragSession(ImageComponentRenderer image, double mouseX, double mouseY,
                               double originalX, double originalY) {
        private void restore() {
            image.pos.setX(originalX);
            image.pos.setY(originalY);
        }
    }

    private static void showSaveError(UISCodeArea codeArea, IOException error) {
        new Alert(Alert.AlertType.ERROR,
                "无法保存 MUI 文件 " + codeArea.getFile() + ": " + error.getMessage()).showAndWait();
    }

    public record UnitInfo(String name, String unit, int id) {
        @Override
        public String toString() {
            return name + "(" + unit + ")";
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String unit() {
            return unit;
        }

        @Override
        public int id() {
            return id;
        }
    }


}
