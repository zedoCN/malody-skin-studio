package top.zedo.skin.v;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.transform.Rotate;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The static full-screen image scene shared by the editor and PNG snapshot. */
final class VScenePlan {
    record Item(int index, SkinFile.Module module, Image image, VSceneLayout.Placement placement) { }

    private final List<Item> items;
    private final int total, unsupported, sceneMismatch, sceneUnknown, missing, capped;

    private VScenePlan(List<Item> items, int total, int unsupported, int sceneMismatch,
                       int sceneUnknown, int missing, int capped) {
        this.items = List.copyOf(items);
        this.total = total;
        this.unsupported = unsupported;
        this.sceneMismatch = sceneMismatch;
        this.sceneUnknown = sceneUnknown;
        this.missing = missing;
        this.capped = capped;
    }

    static VScenePlan build(MspSkinDocument document, SkinFile skin, int layer,
                            VSceneLayout.SceneContext context, int width, int height) {
        if (!VSceneLayout.isFullScreenLayer(layer))
            throw new IllegalArgumentException("仅支持全屏图层 1 或 4");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("画布尺寸无效");
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < skin.getModulesCount(); i++) {
            if (skin.getModules(i).getParam().getLayer() == layer) indices.add(i);
        }
        indices.sort(Comparator.comparingInt((Integer i) -> skin.getModules(i).getParam().getOrder())
                .thenComparingInt(i -> i));

        Map<String, Image> images = new HashMap<>();
        List<Item> items = new ArrayList<>();
        int unsupported = 0, mismatch = 0, unknown = 0, missing = 0, capped = 0;
        for (int index : indices) {
            SkinFile.Module module = skin.getModules(index);
            if (!VSceneLayout.isStaticImage(module)) { unsupported++; continue; }
            VSceneLayout.SceneMatch match = VSceneLayout.sceneMatch(module, context);
            if (match == VSceneLayout.SceneMatch.MISMATCH) { mismatch++; continue; }
            if (match == VSceneLayout.SceneMatch.UNKNOWN) { unknown++; continue; }
            if (items.size() >= 80) { capped++; continue; }
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
                VSceneLayout.Placement placement = VSceneLayout.project(module, context,
                        width, height, image.getWidth(), image.getHeight());
                items.add(new Item(index, module, image, placement));
            } catch (IllegalArgumentException error) { unsupported++; }
        }
        return new VScenePlan(items, indices.size(), unsupported, mismatch, unknown, missing, capped);
    }

    List<Item> items() { return items; }

    String status() {
        return "当前层 " + total + " 个模块；显示 " + items.size() + " 个静态图片，"
                + "跳过 " + unsupported + " 个动态/特殊模块，"
                + sceneMismatch + " 个场景条件不匹配，" + sceneUnknown + " 个条件无法静态判定，"
                + "资源缺失或无法解码 " + missing + " 个。"
                + (capped > 0 ? "另有 " + capped + " 个超过 80 张显示上限。" : "");
    }

    static ImageView imageView(Item item) {
        VSceneLayout.Placement at = item.placement();
        ImageView node = new ImageView(item.image());
        node.setFitWidth(at.width());
        node.setFitHeight(at.height());
        node.setPreserveRatio(false);
        node.setLayoutX(at.left());
        node.setLayoutY(at.top());
        node.setOpacity(at.opacity());
        node.getTransforms().add(new Rotate(-at.rotate(),
                at.width() * at.pivotX(), at.height() * (1 - at.pivotY())));
        return node;
    }
}
