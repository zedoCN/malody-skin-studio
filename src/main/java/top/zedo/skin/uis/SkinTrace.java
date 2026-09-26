package top.zedo.skin.uis;

import com.google.gson.GsonBuilder;
import javafx.application.Platform;
import top.zedo.skin.uis.component.AbstractComponentRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/** Reproducible effective-component trace for source and game renderer comparisons. */
public final class SkinTrace {
    private SkinTrace() { }

    public static void run(String[] args) {
        if (args.length < 3 || args.length > 5) {
            throw new IllegalArgumentException("用法: --trace-mui <皮肤.mui> <输出.json> [时间毫秒] [设备比例或 ANDROID:宽x高]");
        }
        Path skin = Path.of(args[1]);
        Path output = Path.of(args[2]);
        long time = args.length >= 4 ? Long.parseLong(args[3]) : 0;
        String profile = args.length == 5 ? args[4] : "PC";
        SkinRenderProfile renderProfile = SkinRenderProfile.parse(profile);
        if (!Files.isRegularFile(skin)) throw new IllegalArgumentException("找不到皮肤文件: " + skin);

        CompletableFuture<Trace> result = new CompletableFuture<>();
        Platform.startup(() -> {
            try {
                UISCanvas canvas = new UISCanvas();
                renderProfile.apply(canvas);
                canvas.loadSkin(skin);
                renderProfile.applyOutputHeight(canvas);
                canvas.pause();
                canvas.setTime(time);
                canvas.resize(canvas.expressionCalculator.getCanvasWidth(),
                        canvas.expressionCalculator.getCanvasHeight());
                canvas.applyCss();
                canvas.layout();
                canvas.draw();
                List<Component> components = new ArrayList<>();
                for (AbstractComponentRenderer renderer : canvas.componentRenders) {
                    UISComponent component = renderer.getComponent();
                    Map<String, Property> properties = new TreeMap<>();
                    for (String name : component.findProperties("")) {
                        MuiSourceLocation source = component.getPropertySource(name);
                        properties.put(name, new Property(component.getRawProperty(name),
                                source == null ? null : source.file().toString(),
                                source == null ? null : source.sectionLine(),
                                source == null ? null : source.propertyLine(),
                                source != null && source.grouped()));
                    }
                    components.add(new Component(component.getFullName(), renderer.getClass().getSimpleName(),
                            renderer.getZindex(), finite(renderer.pos.getX()), finite(renderer.pos.getY()),
                            finite(renderer.size.getW()), finite(renderer.size.getH()), properties));
                }
                result.complete(new Trace(skin.toAbsolutePath().normalize().toString(), profile, time,
                        canvas.expressionCalculator.getCanvasWidth(),
                        canvas.expressionCalculator.getCanvasHeight(), components));
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        try {
            Path parent = output.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(result.join()),
                    StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("写出组件追踪失败: " + output, error);
        } finally {
            Platform.exit();
        }
    }

    private static Double finite(double value) {
        return Double.isFinite(value) ? value : null;
    }

    private record Trace(String skin, String profile, long timeMillis, double canvasWidth,
                         double canvasHeight, List<Component> components) { }
    private record Component(String name, String renderer, int zindex, Double x, Double y,
                             Double width, Double height, Map<String, Property> properties) { }
    private record Property(String raw, String file, Integer sectionLine, Integer propertyLine,
                            boolean grouped) { }
}
