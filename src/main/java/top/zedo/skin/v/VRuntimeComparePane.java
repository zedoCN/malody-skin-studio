package top.zedo.skin.v;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Read-only comparison between a saved V skin and an explicitly loaded Unity snapshot. */
final class VRuntimeComparePane extends VBox {
    private final MspSkinDocument document;
    private final Label runtimeStatus = new Label("未加载运行态快照");
    private final TextArea runtimeDetails = new TextArea();
    private final Label captureStatus = new Label();
    private final ImageView captureImage = new ImageView();
    private VRuntimeSnapshot runtimeSnapshot;
    private VRuntimeCaptureBundle runtimeCapture;
    private Path runtimePath;
    private boolean runtimeFingerprintsMatch;
    private int currentModule = -1;

    VRuntimeComparePane(MspSkinDocument document) {
        super(10);
        this.document = document;
        Button loadRuntime = new Button("加载模块 JSON…");
        loadRuntime.setOnAction(_ -> loadRuntimeSnapshot());
        Button loadCapture = new Button("加载截图包…");
        loadCapture.setOnAction(_ -> loadCaptureBundle());
        runtimeStatus.setWrapText(true);
        runtimeDetails.setEditable(false);
        runtimeDetails.setWrapText(true);
        runtimeDetails.setStyle("-fx-font-family: monospace;");
        Label runtimeCaveat = new Label("只读对照保存版参数；仅核对 info.asm 和 Lua，素材未核对。运行值属于导出时的皮肤副本、谱面和配置。");
        runtimeCaveat.setWrapText(true);
        captureStatus.setWrapText(true);
        captureImage.setPreserveRatio(true);
        captureImage.setFitHeight(300);
        captureImage.fitWidthProperty().bind(widthProperty().subtract(32));
        captureImage.setVisible(false);
        captureImage.setManaged(false);
        captureStatus.setVisible(false);
        captureStatus.setManaged(false);
        getChildren().addAll(new javafx.scene.layout.HBox(8, loadRuntime, loadCapture), runtimeStatus,
                runtimeCaveat, captureStatus, captureImage, runtimeDetails);
        setPadding(new Insets(12));
        VBox.setVgrow(runtimeDetails, Priority.ALWAYS);
    }

    void selectModule(int index) {
        currentModule = index;
        showRuntimeModule();
    }

    void refreshAfterSave() {
        verifyRuntimeSnapshot();
    }

    private void loadRuntimeSnapshot() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择 Emiria 冻结帧模块快照");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON 快照", "*.json"));
        Path initial = runtimePath == null ? document.path().getParent() : runtimePath.getParent();
        if (initial != null && Files.isDirectory(initial)) chooser.setInitialDirectory(initial.toFile());
        java.io.File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (selected == null) return;
        try {
            VRuntimeSnapshot loaded = VRuntimeSnapshot.load(selected.toPath());
            runtimeSnapshot = loaded;
            runtimeCapture = null;
            clearCapture();
            runtimePath = selected.toPath();
            verifyRuntimeSnapshot();
        } catch (IOException | IllegalArgumentException error) {
            runtimeSnapshot = null;
            runtimeCapture = null;
            clearCapture();
            runtimeFingerprintsMatch = false;
            runtimeStatus.setText("无法加载运行态快照：" + error.getMessage());
            runtimeDetails.clear();
        }
    }

    private void loadCaptureBundle() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择 Unity 冻结帧截图包的 bundle.json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("截图包 JSON", "*.json"));
        Path initial = runtimePath == null ? document.path().getParent() : runtimePath.getParent();
        if (initial != null && Files.isDirectory(initial)) chooser.setInitialDirectory(initial.toFile());
        java.io.File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (selected == null) return;
        try {
            VRuntimeCaptureBundle loaded = VRuntimeCaptureBundle.load(selected.toPath());
            Image image = new Image(loaded.imagePath().toUri().toString(), false);
            if (image.isError()) throw new IOException("截图 PNG 无法在 JavaFX 中显示");
            runtimeSnapshot = loaded.snapshot();
            runtimeCapture = loaded;
            runtimePath = selected.toPath();
            captureImage.setImage(image);
            captureStatus.setText("Unity Game View 源视口 " + loaded.sourceWidth() + "×" + loaded.sourceHeight()
                    + " · PNG " + loaded.imageWidth() + "×" + loaded.imageHeight()
                    + " · 打包时两张截图字节一致；请确认来自同一次 PlayMode");
            verifyRuntimeSnapshot();
        } catch (IOException | IllegalArgumentException error) {
            runtimeSnapshot = null;
            runtimeCapture = null;
            clearCapture();
            runtimeFingerprintsMatch = false;
            runtimeStatus.setText("无法加载截图包：" + error.getMessage());
            runtimeDetails.clear();
        }
    }

    private void clearCapture() {
        captureImage.setImage(null);
        captureImage.setVisible(false);
        captureImage.setManaged(false);
        captureStatus.setText("");
        captureStatus.setVisible(false);
        captureStatus.setManaged(false);
    }

    private void showVerifiedCapture() {
        boolean show = runtimeFingerprintsMatch && runtimeCapture != null;
        captureImage.setVisible(show);
        captureImage.setManaged(show);
        captureStatus.setVisible(show);
        captureStatus.setManaged(show);
    }

    private void verifyRuntimeSnapshot() {
        if (runtimeSnapshot == null) return;
        try {
            boolean asmMatches = runtimeSnapshot.asmSha256().equalsIgnoreCase(document.asmSha256());
            boolean luaMatches = runtimeSnapshot.luaHash().equalsIgnoreCase(document.luaHash());
            runtimeFingerprintsMatch = asmMatches && luaMatches;
            showVerifiedCapture();
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
            showVerifiedCapture();
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
                        + " 在这次冻结帧中没有对应项。它可能被场景条件过滤，或不在当前游玩场景中。");
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
}
