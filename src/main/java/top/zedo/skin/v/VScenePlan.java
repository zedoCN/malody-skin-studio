package top.zedo.skin.v;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.transform.Scale;
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
    record Item(int index, SkinFile.Module module, Image image, VSceneLayout.Placement basePlacement,
                VAnimationPreview.Frame frame) {
        VSceneLayout.Placement placement() { return frame.placement(); }
        boolean animated() { return module.getAnimationsCount() > 0; }
    }

    private final List<Item> items;
    private final int total, animated, unsupported, sceneMismatch, sceneUnknown, missing, capped;

    private VScenePlan(List<Item> items, int total, int animated, int unsupported, int sceneMismatch,
                       int sceneUnknown, int missing, int capped) {
        this.items = List.copyOf(items);
        this.total = total;
        this.animated = animated;
        this.unsupported = unsupported;
        this.sceneMismatch = sceneMismatch;
        this.sceneUnknown = sceneUnknown;
        this.missing = missing;
        this.capped = capped;
    }

    static VScenePlan build(MspSkinDocument document, SkinFile skin, int layer,
                            VSceneLayout.SceneContext context, int width, int height) {
        return build(document, skin, layer, context, width, height, 0, false);
    }

    static VScenePlan buildPreview(MspSkinDocument document, SkinFile skin, int layer,
                                   VSceneLayout.SceneContext context, int width, int height, double timeMillis) {
        return build(document, skin, layer, context, width, height, timeMillis, true);
    }

    private static VScenePlan build(MspSkinDocument document, SkinFile skin, int layer,
                                    VSceneLayout.SceneContext context, int width, int height,
                                    double timeMillis, boolean previewAnimations) {
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
        int animated = 0, unsupported = 0, mismatch = 0, unknown = 0, missing = 0, capped = 0;
        for (int index : indices) {
            SkinFile.Module module = skin.getModules(index);
            boolean hasAnimation = module.getAnimationsCount() > 0;
            boolean supported = previewAnimations
                    ? VSceneLayout.isPreviewImageCandidate(module)
                        && (hasAnimation ? VAnimationPreview.supports(module) : module.getParam().getAlpha() > 0)
                    : VSceneLayout.isStaticImage(module);
            if (!supported) { unsupported++; continue; }
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
                VSceneLayout.Placement base = previewAnimations
                        ? VSceneLayout.projectPreview(module, context, width, height,
                            image.getWidth(), image.getHeight())
                        : VSceneLayout.project(module, context, width, height,
                            image.getWidth(), image.getHeight());
                VAnimationPreview.Frame frame = previewAnimations
                        ? VAnimationPreview.frame(module, base, width, height, timeMillis)
                        : new VAnimationPreview.Frame(base, 1, 1);
                items.add(new Item(index, module, image, base, frame));
                if (hasAnimation) animated++;
            } catch (IllegalArgumentException error) { unsupported++; }
        }
        return new VScenePlan(items, indices.size(), animated, unsupported, mismatch, unknown, missing, capped);
    }

    List<Item> items() { return items; }

    String status() {
        return "当前层 " + total + " 个模块；显示 " + items.size() + " 个"
                + (animated > 0 ? "图片（含 " + animated + " 个 ASM 动画）" : "静态图片") + "，"
                + "跳过 " + unsupported + " 个动态/特殊模块，"
                + sceneMismatch + " 个场景条件不匹配，" + sceneUnknown + " 个条件无法静态判定，"
                + "资源缺失或无法解码 " + missing + " 个。"
                + (capped > 0 ? "另有 " + capped + " 个超过 80 张显示上限。" : "");
    }

    static ImageView imageView(Item item) {
        ImageView node = new ImageView(item.image());
        applyFrame(node, item.frame());
        return node;
    }

    static void applyFrame(ImageView node, VAnimationPreview.Frame frame) {
        VSceneLayout.Placement at = frame.placement();
        node.setFitWidth(at.width());
        node.setFitHeight(at.height());
        node.setPreserveRatio(false);
        node.setLayoutX(at.left());
        node.setLayoutY(at.top());
        node.setOpacity(at.opacity());
        double pivotX = at.width() * at.pivotX();
        double pivotY = at.height() * (1 - at.pivotY());
        node.getTransforms().setAll(new Rotate(-at.rotate(), pivotX, pivotY),
                new Scale(frame.scaleX(), frame.scaleY(), pivotX, pivotY));
    }
}
