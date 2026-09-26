package top.zedo.skin.v;

import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.util.Objects;

/** The editable V skin draft, without JavaFX controls or package I/O. */
final class VSkinEditModel {
    private final SkinFile.Builder draft;

    VSkinEditModel(SkinFile skin) {
        draft = Objects.requireNonNull(skin).toBuilder();
    }

    SkinFile skin() {
        return draft.build();
    }

    void restore(SkinFile skin) {
        draft.clear().mergeFrom(Objects.requireNonNull(skin));
    }

    SkinFile.Meta metadata() {
        return draft.getMeta();
    }

    int moduleCount() {
        return draft.getModulesCount();
    }

    SkinFile.Module module(int index) {
        return draft.getModules(index);
    }

    ModuleFields fields(int index) {
        return ModuleFields.from(module(index));
    }

    void updateModule(int index, ModuleFields fields) {
        Objects.requireNonNull(fields);
        SkinFile.Module original = draft.getModules(index);
        // Parsing and validation finish before the draft changes.
        float x = finite(fields.x(), "X");
        float y = finite(fields.y(), "Y");
        float dx = finite(fields.dx(), "偏移 X");
        float dy = finite(fields.dy(), "偏移 Y");
        int alpha = integer(fields.alpha(), "透明度");
        int rotate = integer(fields.rotate(), "旋转");
        float width = original.hasImage() ? finite(fields.width(), "宽度") : 0;
        float height = original.hasImage() ? finite(fields.height(), "高度") : 0;

        SkinFile.Module.Builder updated = original.toBuilder();
        if (!fields.name().equals(original.getMeta().getDesc())) {
            updated.getMetaBuilder().setDesc(fields.name());
        }
        SkinFile.ModuleParam param = original.getParam();
        if (x != param.getX() || y != param.getY() || dx != param.getDx() || dy != param.getDy()
                || alpha != param.getAlpha() || rotate != param.getRotate()) {
            updated.getParamBuilder().setX(x).setY(y).setDx(dx).setDy(dy)
                    .setAlpha(alpha).setRotate(rotate);
        }
        if (original.hasImage() && (width != original.getImage().getWidth()
                || height != original.getImage().getHeight())) {
            updated.getImageBuilder().setWidth(width).setHeight(height);
        }
        draft.setModules(index, VModuleResource.withValue(updated.build(), fields.resource()));
    }

    void updateMetadata(String title, String creator, String description, String cover) {
        draft.getMetaBuilder().setTitle(Objects.requireNonNull(title))
                .setCreator(Objects.requireNonNull(creator))
                .setDesc(Objects.requireNonNull(description))
                .setCover(Objects.requireNonNull(cover));
    }

    private static float finite(String text, String label) {
        try {
            float value = Float.parseFloat(text.trim());
            if (Float.isFinite(value)) return value;
        } catch (NumberFormatException ignored) {
            // Report the same field for malformed and non-finite values.
        }
        throw new NumberFormatException(label + ": " + text);
    }

    private static int integer(String text, String label) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException error) {
            throw new NumberFormatException(label + ": " + text);
        }
    }

    record ModuleFields(String name, String resource, String x, String y, String dx, String dy,
                        String width, String height, String alpha, String rotate) {
        ModuleFields {
            Objects.requireNonNull(name);
            Objects.requireNonNull(resource);
            Objects.requireNonNull(x);
            Objects.requireNonNull(y);
            Objects.requireNonNull(dx);
            Objects.requireNonNull(dy);
            Objects.requireNonNull(width);
            Objects.requireNonNull(height);
            Objects.requireNonNull(alpha);
            Objects.requireNonNull(rotate);
        }

        static ModuleFields from(SkinFile.Module module) {
            SkinFile.ModuleParam param = module.getParam();
            return new ModuleFields(module.getMeta().getDesc(), VModuleResource.value(module),
                    Float.toString(param.getX()), Float.toString(param.getY()),
                    Float.toString(param.getDx()), Float.toString(param.getDy()),
                    module.hasImage() ? Float.toString(module.getImage().getWidth()) : "",
                    module.hasImage() ? Float.toString(module.getImage().getHeight()) : "",
                    Integer.toString(param.getAlpha()), Integer.toString(param.getRotate()));
        }

        ModuleFields withOffsets(float dx, float dy) {
            return new ModuleFields(name, resource, x, y, Float.toString(dx),
                    Float.toString(dy), width, height, alpha, rotate);
        }
    }
}
