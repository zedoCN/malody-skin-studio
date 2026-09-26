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
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class VModuleResourceTest {
    @TempDir Path directory;

    @Test
    void editsOnlyDisplayedFieldAndKeepsOtherData() {
        UnknownFieldSet unknown = UnknownFieldSet.newBuilder()
                .addField(100, UnknownFieldSet.Field.newBuilder().addVarint(7).build()).build();
        SkinFile.ModuleParamImage first = SkinFile.ModuleParamImage.newBuilder()
                .setFile("first.png").setWidth(33).setUnknownFields(unknown).build();
        SkinFile.ModuleParamImage second = SkinFile.ModuleParamImage.newBuilder()
                .setFile("second.png").setFrames(5).build();
        List<SkinFile.Module> modules = List.of(
                SkinFile.Module.newBuilder().setImage(first).setUnknownFields(unknown).build(),
                SkinFile.Module.newBuilder().setText(SkinFile.ModuleParamText.newBuilder()
                        .setText("old text").setFont(8).setUnknownFields(unknown)).build(),
                SkinFile.Module.newBuilder().setNumber(SkinFile.ModuleParamNumber.newBuilder()
                        .setFile("number-0.png").setPadding(3).setUnknownFields(unknown)).build(),
                SkinFile.Module.newBuilder().setSound(SkinFile.ModuleParamSound.newBuilder()
                        .setFile("sound.wav").setLoop(true).setUnknownFields(unknown)).build(),
                SkinFile.Module.newBuilder().setImages(SkinFile.ModuleParamImages.newBuilder()
                        .addItems(first).addItems(second).setUnknownFields(unknown)).build(),
                SkinFile.Module.newBuilder().setNote(SkinFile.ModuleParamNotes.newBuilder()
                        .addImages(first).addImages(second).setUnknownFields(unknown)).build());

        for (SkinFile.Module original : modules) {
            assertTrue(VModuleResource.canEdit(original));
            assertSame(original, VModuleResource.withValue(original, VModuleResource.value(original)));
            SkinFile.Module edited = VModuleResource.withValue(original, "replacement.png");
            assertEquals("replacement.png", VModuleResource.value(edited));
            assertEquals(original, VModuleResource.withValue(edited, VModuleResource.value(original)));
            assertEquals(original.getSubParamCase(), edited.getSubParamCase());
            assertEquals(original.getUnknownFields(), edited.getUnknownFields());
            switch (original.getSubParamCase()) {
                case IMAGE -> {
                    assertEquals(33, edited.getImage().getWidth());
                    assertEquals(unknown, edited.getImage().getUnknownFields());
                }
                case TEXT -> {
                    assertEquals(8, edited.getText().getFont());
                    assertEquals(unknown, edited.getText().getUnknownFields());
                }
                case NUMBER -> {
                    assertEquals(3, edited.getNumber().getPadding());
                    assertEquals(unknown, edited.getNumber().getUnknownFields());
                }
                case SOUND -> {
                    assertTrue(edited.getSound().getLoop());
                    assertEquals(unknown, edited.getSound().getUnknownFields());
                }
                case IMAGES -> {
                    assertEquals(2, edited.getImages().getItemsCount());
                    assertEquals(second, edited.getImages().getItems(1));
                    assertEquals(33, edited.getImages().getItems(0).getWidth());
                    assertEquals(unknown, edited.getImages().getItems(0).getUnknownFields());
                    assertEquals(unknown, edited.getImages().getUnknownFields());
                }
                case NOTE -> {
                    assertEquals(2, edited.getNote().getImagesCount());
                    assertEquals(second, edited.getNote().getImages(1));
                    assertEquals(33, edited.getNote().getImages(0).getWidth());
                    assertEquals(unknown, edited.getNote().getImages(0).getUnknownFields());
                    assertEquals(unknown, edited.getNote().getUnknownFields());
                }
                default -> fail("Unexpected module type");
            }
        }
    }

    @Test
    void emptyListsRemainEmpty() {
        for (SkinFile.Module module : List.of(
                SkinFile.Module.newBuilder().setImages(SkinFile.ModuleParamImages.getDefaultInstance()).build(),
                SkinFile.Module.newBuilder().setNote(SkinFile.ModuleParamNotes.getDefaultInstance()).build(),
                SkinFile.Module.getDefaultInstance())) {
            assertFalse(VModuleResource.canEdit(module));
            assertEquals("", VModuleResource.value(module));
            assertSame(module, VModuleResource.withValue(module, "new.png"));
        }
    }

    @Test
    void roundTripsRealNumberAndNoteModulesWhenProvided() throws IOException {
        String paths = System.getProperty("malody.v.samples", "");
        Assumptions.assumeFalse(paths.isBlank(), "Pass -Dmalody.v.samples=path1:path2");
        int verifiedNumber = 0;
        int verifiedNote = 0;
        int packageIndex = 0;
        for (String filename : paths.split(Pattern.quote(File.pathSeparator))) {
            Path source = Path.of(filename);
            Path copy = directory.resolve("resource-" + packageIndex++ + ".msp");
            Files.copy(source, copy);
            MspSkinDocument document = MspSkinDocument.open(copy);
            SkinFile original = document.skin();
            SkinFile.Builder updated = original.toBuilder();
            for (int i = 0; i < original.getModulesCount(); i++) {
                SkinFile.Module module = original.getModules(i);
                String replacement = module.hasNumber() ? "score-1.png"
                        : module.hasNote() && module.getNote().getImagesCount() > 0 ? "M.png" : null;
                if (replacement == null || replacement.equals(VModuleResource.value(module))) continue;
                if (document.resource(replacement) == null) continue;
                updated.setModules(i, VModuleResource.withValue(module, replacement));
                if (module.hasNumber()) verifiedNumber++;
                if (module.hasNote()) verifiedNote++;
            }
            document.save(updated.build());
            SkinFile reopened = MspSkinDocument.open(copy).skin();
            assertEquals(updated.build(), reopened);
            for (int i = 0; i < original.getModulesCount(); i++) {
                if (updated.getModules(i).equals(original.getModules(i))) {
                    assertEquals(original.getModules(i), reopened.getModules(i));
                }
            }
        }
        assertTrue(verifiedNumber > 0, "A real number module was required");
        assertTrue(verifiedNote > 0, "A real note module was required");
    }
}
