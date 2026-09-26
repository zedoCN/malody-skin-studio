package top.zedo.skin.v;

import com.google.protobuf.UnknownFieldSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class VSkinEditModelTest {
    @TempDir Path directory;

    @Test
    void editsDisplayedFieldsAndPreservesOtherProtobufData() {
        UnknownFieldSet unknown = UnknownFieldSet.newBuilder()
                .addField(100, UnknownFieldSet.Field.newBuilder().addVarint(7).build()).build();
        SkinFile.Module originalModule = SkinFile.Module.newBuilder()
                .setMeta(SkinFile.ModuleMeta.newBuilder().setDesc("Old").setCreator("Keep")
                        .setUnknownFields(unknown))
                .setParam(SkinFile.ModuleParam.newBuilder().setX(5).setY(6).setLayer(17)
                        .setUnknownFields(unknown))
                .setImage(SkinFile.ModuleParamImage.newBuilder().setFile("old.png").setWidth(20)
                        .setHeight(30).setFrames(9).setUnknownFields(unknown))
                .addAnimations(SkinFile.ModuleAnimation.newBuilder().setType(3))
                .setUnknownFields(unknown).build();
        SkinFile original = SkinFile.newBuilder()
                .setMeta(SkinFile.Meta.newBuilder().setTitle("Skin").setMode(5)
                        .setUnknownFields(unknown))
                .addModules(originalModule)
                .addModules(SkinFile.Module.newBuilder().setSound(
                        SkinFile.ModuleParamSound.newBuilder().setFile("other.wav")))
                .setUnknownFields(unknown).build();
        VSkinEditModel model = new VSkinEditModel(original);

        assertEquals(original, model.skin());
        VSkinEditModel.ModuleFields fields = model.fields(0);
        model.updateModule(0, new VSkinEditModel.ModuleFields("New", "new.png", " 7.5 ", fields.y(),
                fields.dx(), fields.dy(), "22", fields.height(), fields.alpha(), fields.rotate()));
        model.updateMetadata("New Skin", "Author", "Description", "cover.png");

        SkinFile edited = model.skin();
        assertEquals("New Skin", edited.getMeta().getTitle());
        assertEquals(5, edited.getMeta().getMode());
        assertEquals(unknown, edited.getMeta().getUnknownFields());
        assertEquals(unknown, edited.getUnknownFields());
        assertEquals(original.getModules(1), edited.getModules(1));
        SkinFile.Module changed = edited.getModules(0);
        assertEquals("New", changed.getMeta().getDesc());
        assertEquals("Keep", changed.getMeta().getCreator());
        assertEquals("new.png", changed.getImage().getFile());
        assertEquals(22, changed.getImage().getWidth());
        assertEquals(30, changed.getImage().getHeight());
        assertEquals(9, changed.getImage().getFrames());
        assertEquals(7.5f, changed.getParam().getX());
        assertEquals(6, changed.getParam().getY());
        assertEquals(17, changed.getParam().getLayer());
        assertEquals(originalModule.getAnimationsList(), changed.getAnimationsList());
        assertEquals(unknown, changed.getUnknownFields());
        assertEquals(unknown, changed.getMeta().getUnknownFields());
        assertEquals(unknown, changed.getParam().getUnknownFields());
        assertEquals(unknown, changed.getImage().getUnknownFields());
    }

    @Test
    void rejectsInvalidNumbersWithoutPartiallyUpdatingDraft() {
        SkinFile.Module module = SkinFile.Module.newBuilder()
                .setMeta(SkinFile.ModuleMeta.newBuilder().setDesc("Original"))
                .setImage(SkinFile.ModuleParamImage.newBuilder().setFile("original.png").setWidth(10))
                .build();
        VSkinEditModel model = new VSkinEditModel(SkinFile.newBuilder().addModules(module).build());
        VSkinEditModel.ModuleFields fields = model.fields(0);
        for (String bad : new String[]{"NaN", "Infinity", "1e1000", "bad"}) {
            assertThrows(NumberFormatException.class, () -> model.updateModule(0,
                    new VSkinEditModel.ModuleFields("Changed", "changed.png", "1", fields.y(),
                            fields.dx(), fields.dy(), bad, fields.height(), fields.alpha(), fields.rotate())));
            assertEquals(module, model.module(0));
        }
        assertThrows(NumberFormatException.class, () -> model.updateModule(0,
                new VSkinEditModel.ModuleFields("Changed", "changed.png", "1", fields.y(),
                        fields.dx(), fields.dy(), fields.width(), fields.height(), "2.5", fields.rotate())));
        assertEquals(module, model.module(0));
    }

    @Test
    void nonImageModuleKeepsAbsentImageAndIgnoresHiddenDimensions() {
        SkinFile.Module module = SkinFile.Module.newBuilder()
                .setText(SkinFile.ModuleParamText.newBuilder().setText("old").setFont(4)).build();
        VSkinEditModel model = new VSkinEditModel(SkinFile.newBuilder().addModules(module).build());
        VSkinEditModel.ModuleFields fields = model.fields(0);
        model.updateModule(0, new VSkinEditModel.ModuleFields(fields.name(), "new", fields.x(), fields.y(),
                fields.dx(), fields.dy(), "not a number", "", fields.alpha(), fields.rotate()));
        assertEquals("new", model.module(0).getText().getText());
        assertEquals(4, model.module(0).getText().getFont());
        assertFalse(model.module(0).hasImage());
        assertFalse(model.module(0).hasParam());
        assertFalse(model.module(0).hasMeta());
    }

    @Test
    void realPackagesRetainUneditedModulesAndFieldsWhenProvided() throws IOException {
        String paths = System.getProperty("malody.v.samples", "");
        Assumptions.assumeFalse(paths.isBlank(), "Pass -Dmalody.v.samples=path1:path2");
        int index = 0;
        for (String filename : paths.split(Pattern.quote(File.pathSeparator))) {
            Path copy = directory.resolve("edit-" + index++ + ".msp");
            Files.copy(Path.of(filename), copy);
            MspSkinDocument document = MspSkinDocument.open(copy);
            SkinFile original = document.skin();
            VSkinEditModel model = new VSkinEditModel(original);
            assertEquals(original, model.skin());
            int imageIndex = -1;
            for (int i = 0; i < model.moduleCount(); i++) {
                if (model.module(i).hasImage()) {
                    imageIndex = i;
                    break;
                }
            }
            assertTrue(imageIndex >= 0, filename + " has no image module");
            SkinFile.Module before = model.module(imageIndex);
            VSkinEditModel.ModuleFields fields = model.fields(imageIndex);
            float changedX = before.getParam().getX() == 0 ? 1 : 0;
            float changedWidth = before.getImage().getWidth() == 0 ? 1 : 0;
            model.updateModule(imageIndex, new VSkinEditModel.ModuleFields(fields.name() + " (test)",
                    fields.resource(), Float.toString(changedX), fields.y(), fields.dx(), fields.dy(),
                    Float.toString(changedWidth),
                    fields.height(), fields.alpha(), fields.rotate()));
            SkinFile.Module expected = before.toBuilder()
                    .setMeta(before.getMeta().toBuilder().setDesc(fields.name() + " (test)"))
                    .setParam(before.getParam().toBuilder().setX(changedX))
                    .setImage(before.getImage().toBuilder().setWidth(changedWidth)).build();
            assertEquals(expected, model.module(imageIndex));
            document.save(model.skin());
            SkinFile reopened = MspSkinDocument.open(copy).skin();
            assertEquals(model.skin(), reopened);
            for (int i = 0; i < original.getModulesCount(); i++) {
                if (i != imageIndex) assertEquals(original.getModules(i), reopened.getModules(i));
            }
        }
        assertTrue(index > 0);
    }
}
