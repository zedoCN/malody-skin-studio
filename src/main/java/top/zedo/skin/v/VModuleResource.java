package top.zedo.skin.v;

import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.util.Objects;

/** Edits the single resource shown by the V module property panel. */
final class VModuleResource {
    private VModuleResource() { }

    static boolean canEdit(SkinFile.Module module) {
        return switch (module.getSubParamCase()) {
            case IMAGE, TEXT, NUMBER, SOUND -> true;
            case IMAGES -> module.getImages().getItemsCount() > 0;
            case NOTE -> module.getNote().getImagesCount() > 0;
            default -> false;
        };
    }

    static String value(SkinFile.Module module) {
        return switch (module.getSubParamCase()) {
            case IMAGE -> module.getImage().getFile();
            case TEXT -> module.getText().getText();
            case NUMBER -> module.getNumber().getFile();
            case SOUND -> module.getSound().getFile();
            case IMAGES -> module.getImages().getItemsCount() > 0
                    ? module.getImages().getItems(0).getFile() : "";
            case NOTE -> module.getNote().getImagesCount() > 0
                    ? module.getNote().getImages(0).getFile() : "";
            default -> "";
        };
    }

    static SkinFile.Module withValue(SkinFile.Module module, String value) {
        Objects.requireNonNull(module);
        Objects.requireNonNull(value);
        if (!canEdit(module) || value.equals(value(module))) return module;
        SkinFile.Module.Builder updated = module.toBuilder();
        switch (module.getSubParamCase()) {
            case IMAGE -> updated.getImageBuilder().setFile(value);
            case TEXT -> updated.getTextBuilder().setText(value);
            case NUMBER -> updated.getNumberBuilder().setFile(value);
            case SOUND -> updated.getSoundBuilder().setFile(value);
            case IMAGES -> updated.getImagesBuilder().getItemsBuilder(0).setFile(value);
            case NOTE -> updated.getNoteBuilder().getImagesBuilder(0).setFile(value);
            default -> throw new IllegalStateException("Unsupported resource type");
        }
        return updated.build();
    }
}
