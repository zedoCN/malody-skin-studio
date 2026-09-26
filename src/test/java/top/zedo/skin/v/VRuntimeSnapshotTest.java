package top.zedo.skin.v;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VRuntimeSnapshotTest {
    @TempDir Path directory;

    @Test
    void loadsTypedRuntimeAndMatchesSourceWithoutUsingRowIndex() throws IOException {
        JsonObject root = fixture();
        root.getAsJsonArray("modules").add(row("other", 8, 50));
        root.getAsJsonArray("modules").add(row("target", 1, 160));
        root.addProperty("moduleCount", 2);
        VRuntimeSnapshot snapshot = VRuntimeSnapshot.load(write(root));

        assertEquals(2, snapshot.moduleCount());
        assertEquals("chart.mc", snapshot.chartFile());
        assertEquals(1234.5, snapshot.audioTimeMs());
        assertEquals("abc", snapshot.luaHash());
        VRuntimeSnapshot.Module matched = snapshot.match(module("target", 1)).orElseThrow();
        assertEquals(160, matched.runtime().width());
        assertEquals(175, matched.runtime().alpha());
        assertEquals(1920, matched.runtime().parentCanvasSize().orElseThrow().x());
        assertEquals(64, matched.source().imageWidth());
        assertTrue(snapshot.match(module("missing", 1)).isEmpty());
        assertTrue(snapshot.match(module("target", 2)).isEmpty());
        SkinFile.Module.Builder changedImage = module("target", 1).toBuilder();
        changedImage.getImageBuilder().setWidth(65);
        assertTrue(snapshot.match(changedImage.build()).isEmpty());
    }

    @Test
    void reportsOnlyComparableRuntimeChanges() throws IOException {
        JsonObject root = fixture();
        JsonObject changed = row("target", 1, 160);
        root.getAsJsonArray("modules").add(changed);
        root.addProperty("moduleCount", 1);
        VRuntimeSnapshot snapshot = VRuntimeSnapshot.load(write(root));
        VRuntimeSnapshot.Module module = snapshot.match(module("target", 1)).orElseThrow();

        var changes = VRuntimeModuleChanges.between(module);
        assertEquals(2, changes.size());
        assertEquals("宽 64.00 → 160.00 Unit (×2.500)", changes.get(0).description());
        assertEquals("透明度 200 → 175", changes.get(1).description());

        changed.getAsJsonObject("source").addProperty("imageWidthUnit", "PX");
        changed.getAsJsonObject("runtime").addProperty("alpha", 200);
        assertTrue(VRuntimeModuleChanges.between(VRuntimeSnapshot.load(write(root)).modules().getFirst())
                .isEmpty(), "PX dimensions have no directly comparable runtime unit");
    }

    @Test
    void duplicateNameCanMatchOnlyByFullSourceAndIdenticalSourceIsAmbiguous() throws IOException {
        JsonObject root = fixture();
        root.getAsJsonArray("modules").add(row("same", 1, 100));
        root.getAsJsonArray("modules").add(row("same", 2, 200));
        root.addProperty("moduleCount", 2);
        VRuntimeSnapshot distinct = VRuntimeSnapshot.load(write(root));
        assertEquals(200, distinct.match(module("same", 2)).orElseThrow().runtime().width());

        root.getAsJsonArray("modules").add(row("same", 1, 300));
        root.addProperty("moduleCount", 3);
        VRuntimeSnapshot ambiguous = VRuntimeSnapshot.load(write(root));
        assertThrows(IllegalStateException.class, () -> ambiguous.match(module("same", 1)));
    }

    @Test
    void rejectsIdenticalModulesInSkinEvenWithOneExportRow() throws IOException {
        JsonObject root = fixture();
        root.getAsJsonArray("modules").add(row("same", 1, 100));
        root.addProperty("moduleCount", 1);
        VRuntimeSnapshot snapshot = VRuntimeSnapshot.load(write(root));
        SkinFile skin = SkinFile.newBuilder()
                .addModules(module("same", 1)).addModules(module("same", 1)).build();
        assertThrows(IllegalStateException.class, () -> snapshot.match(skin, 0));
    }

    @Test
    void retainsReadableModulesWhenOneCaptureFailed() throws IOException {
        JsonObject root = fixture();
        JsonObject failed = new JsonObject();
        failed.addProperty("error", "CaptureException");
        root.getAsJsonArray("modules").add(failed);
        root.getAsJsonArray("modules").add(row("target", 1, 160));
        root.addProperty("moduleCount", 2);
        VRuntimeSnapshot snapshot = VRuntimeSnapshot.load(write(root));
        assertEquals(2, snapshot.moduleCount());
        assertEquals(1, snapshot.captureErrors().size());
        assertTrue(snapshot.match(module("target", 1)).isPresent());
    }

    @Test
    void realSampleMapsTrackBackgroundWhenProvided() throws IOException {
        String mspPath = System.getProperty("malody.v.sample.msp", "");
        String snapshotPath = System.getProperty("malody.v.sample.snapshot", "");
        Assumptions.assumeTrue(!mspPath.isBlank() && !snapshotPath.isBlank(),
                "Pass both -Dmalody.v.sample.msp and -Dmalody.v.sample.snapshot");
        MspSkinDocument document = MspSkinDocument.open(Path.of(mspPath));
        SkinFile skin = document.skin();
        VRuntimeSnapshot snapshot = VRuntimeSnapshot.load(Path.of(snapshotPath));
        assertEquals(document.asmSha256(), snapshot.asmSha256());
        assertEquals(document.luaHash(), snapshot.luaHash());
        int matched = 0;
        int changed = 0;
        int ambiguous = 0;
        for (int i = 0; i < skin.getModulesCount(); i++) {
            VRuntimeSnapshot.Module candidate;
            try {
                candidate = snapshot.match(skin, i).orElse(null);
            } catch (IllegalStateException error) {
                ambiguous++;
                continue;
            }
            if (candidate != null && !VRuntimeModuleChanges.between(candidate).isEmpty()) changed++;
            if (!skin.getModules(i).getMeta().getDesc().equals("trackbg")) continue;
            VRuntimeSnapshot.Module row = candidate;
            if (row == null) continue;
            matched++;
            assertEquals(1110f, row.runtime().height());
        }
        assertEquals(1, matched, "trackbg must map to one source module");
        assertTrue(ambiguous > 0, "duplicate runtime source configurations remain intentionally unmatched");
        assertEquals(28, changed, "real capture has 28 directly comparable changed modules");
    }

    @Test
    void rejectsMalformedOrOversizedExports() throws IOException {
        JsonObject root = fixture();
        root.getAsJsonArray("modules").add(row("test", 1, 160));
        assertThrows(IllegalArgumentException.class, () -> VRuntimeSnapshot.load(write(root)));

        root.addProperty("moduleCount", 1);
        root.getAsJsonArray("modules").get(0).getAsJsonObject()
                .getAsJsonObject("runtime").remove("width");
        assertThrows(IllegalArgumentException.class, () -> VRuntimeSnapshot.load(write(root)));

        root.getAsJsonArray("modules").get(0).getAsJsonObject()
                .getAsJsonObject("runtime").addProperty("width", 1e100);
        assertThrows(IllegalArgumentException.class, () -> VRuntimeSnapshot.load(write(root)));

        Path malformed = directory.resolve("malformed.json");
        Files.writeString(malformed, "{ invalid");
        assertThrows(IllegalArgumentException.class, () -> VRuntimeSnapshot.load(malformed));

        Path huge = directory.resolve("huge.json");
        try (RandomAccessFile file = new RandomAccessFile(huge.toFile(), "rw")) {
            file.setLength(16L * 1024 * 1024 + 1);
        }
        assertThrows(IllegalArgumentException.class, () -> VRuntimeSnapshot.load(huge));
    }

    private Path write(JsonObject root) throws IOException {
        Path path = directory.resolve("modules.json");
        Files.writeString(path, root.toString());
        return path;
    }

    private static JsonObject fixture() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("utc", "2026-09-26T00:00:00Z");
        root.addProperty("unityReportedScreenWidth", 1920);
        root.addProperty("unityReportedScreenHeight", 1080);
        root.addProperty("audioTimeMs", 1234.5);
        root.addProperty("chartTimeMs", 1230);
        root.addProperty("displayTimeMs", 1220);
        root.addProperty("skinTitle", "Skin");
        root.addProperty("skinScriptFile", "main.lua");
        root.addProperty("asmSha256", "a".repeat(64));
        root.addProperty("luaHash", "abc");
        root.addProperty("chartFile", "chart.mc");
        root.addProperty("factoryCount", 1);
        root.addProperty("moduleCount", 0);
        root.add("modules", new JsonArray());
        return root;
    }

    private static JsonObject row(String name, int order, int runtimeWidth) {
        JsonObject row = new JsonObject();
        row.addProperty("name", name);
        row.addProperty("factoryLayer", 1);
        row.addProperty("factoryName", "Background");
        JsonObject source = new JsonObject();
        source.addProperty("layer", 1);
        source.addProperty("order", order);
        source.addProperty("type", 3);
        source.addProperty("usage", 4);
        source.addProperty("x", 10);
        source.addProperty("y", 20);
        source.addProperty("dx", 2);
        source.addProperty("dy", 3);
        source.addProperty("xUnit", "Unit");
        source.addProperty("yUnit", "Percent");
        source.addProperty("dxUnit", "PX");
        source.addProperty("dyUnit", "Unit");
        source.addProperty("alpha", 200);
        source.addProperty("hasImage", true);
        source.addProperty("imageFile", "background.png");
        source.addProperty("imageWidth", 64);
        source.addProperty("imageHeight", 32);
        source.addProperty("imageWidthUnit", "Unit");
        source.addProperty("imageHeightUnit", "PX");
        row.add("source", source);
        JsonObject runtime = new JsonObject();
        runtime.addProperty("x", 11);
        runtime.addProperty("y", 21);
        runtime.addProperty("width", runtimeWidth);
        runtime.addProperty("height", 80);
        runtime.addProperty("alpha", 175);
        runtime.addProperty("scale", 1);
        runtime.addProperty("rotate", 0);
        runtime.addProperty("hasRectTransform", false);
        runtime.addProperty("hasParentCanvas", true);
        runtime.add("parentCanvasSize", vec2(1920, 1080));
        runtime.addProperty("hasCanvasReferenceResolution", false);
        row.add("runtime", runtime);
        return row;
    }

    private static JsonObject vec2(int x, int y) {
        JsonObject vec = new JsonObject();
        vec.addProperty("x", x);
        vec.addProperty("y", y);
        return vec;
    }

    private static SkinFile.Module module(String name, int order) {
        return SkinFile.Module.newBuilder()
                .setMeta(SkinFile.ModuleMeta.newBuilder().setDesc(name))
                .setType(3).setUsage(4)
                .setParam(SkinFile.ModuleParam.newBuilder().setLayer(1).setOrder(order)
                        .setX(10).setY(20).setDx(2).setDy(3).setAlpha(200)
                        .setXu(SkinFile.ModuleParamUnit.Unit)
                        .setYu(SkinFile.ModuleParamUnit.Percent)
                        .setDxu(SkinFile.ModuleParamUnit.PX)
                        .setDyu(SkinFile.ModuleParamUnit.Unit))
                .setImage(SkinFile.ModuleParamImage.newBuilder().setFile("images/background.png")
                        .setWidth(64).setHeight(32)
                        .setWu(SkinFile.ModuleParamUnit.Unit)
                        .setHu(SkinFile.ModuleParamUnit.PX))
                .build();
    }
}
