package top.zedo.skin.v;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class VRuntimeCaptureBundleTest {
    @TempDir Path directory;

    @Test
    void loadsMatchingFrozenCapture() throws IOException {
        Path manifest = fixture();
        VRuntimeCaptureBundle bundle = VRuntimeCaptureBundle.load(manifest);
        assertEquals(1200, bundle.snapshot().audioTimeMs());
        assertEquals(directory.resolve("game-view.png"), bundle.imagePath());
        assertEquals(4, bundle.sourceWidth());
        assertEquals(3, bundle.sourceHeight());
        assertEquals(4, bundle.imageWidth());
        assertEquals(3, bundle.imageHeight());
    }

    @Test
    void rejectsChangedPng() throws IOException {
        Path manifest = fixture();
        byte[] png = Files.readAllBytes(directory.resolve("game-view.png"));
        png[png.length - 1] ^= 1;
        Files.write(directory.resolve("game-view.png"), png);
        assertThrows(IllegalArgumentException.class, () -> VRuntimeCaptureBundle.load(manifest));
    }

    @Test
    void rejectsTruncatedPngEvenWhenItsHashesAreUpdated() throws IOException {
        Path manifest = fixture();
        Path image = directory.resolve("game-view.png");
        Files.write(image, Arrays.copyOf(Files.readAllBytes(image), 24));
        JsonObject capture = read(directory.resolve("capture.json"));
        capture.getAsJsonObject("artifact").addProperty("byteLength", Files.size(image));
        capture.getAsJsonObject("artifact").addProperty("sha256", digest(image));
        write(directory.resolve("capture.json"), capture);
        JsonObject bundle = read(manifest);
        bundle.getAsJsonObject("files").add("capture", entry("capture.json"));
        bundle.getAsJsonObject("files").add("image", entry("game-view.png"));
        write(manifest, bundle);
        Exception error = assertThrows(Exception.class, () -> VRuntimeCaptureBundle.load(manifest));
        assertTrue(error instanceof IOException || error instanceof IllegalArgumentException);
    }

    @Test
    void rejectsTraversalAndSymlink() throws IOException {
        Path manifest = fixture();
        JsonObject bundle = read(manifest);
        bundle.getAsJsonObject("files").getAsJsonObject("image")
                .addProperty("path", "../game-view.png");
        write(manifest, bundle);
        assertThrows(IllegalArgumentException.class, () -> VRuntimeCaptureBundle.load(manifest));

        fixture();
        Path image = directory.resolve("game-view.png");
        Path elsewhere = directory.resolve("elsewhere.png");
        Files.move(image, elsewhere);
        Files.createSymbolicLink(image, elsewhere.getFileName());
        assertThrows(IllegalArgumentException.class, () -> VRuntimeCaptureBundle.load(manifest));
    }

    @Test
    void rejectsMismatchedFreezeTime() throws IOException {
        Path manifest = fixture();
        JsonObject status = read(directory.resolve("status.json"));
        status.addProperty("chartTimeMs", 1201);
        write(directory.resolve("status.json"), status);
        JsonObject bundle = read(manifest);
        bundle.getAsJsonObject("files").add("status", entry("status.json"));
        write(manifest, bundle);
        assertThrows(IllegalArgumentException.class, () -> VRuntimeCaptureBundle.load(manifest));
    }

    @Test
    void opensRealFrozenFrameWhenProvided() throws IOException {
        String bundlePath = System.getProperty("malody.v.sample.bundle", "");
        String mspPath = System.getProperty("malody.v.sample.msp", "");
        Assumptions.assumeTrue(!bundlePath.isBlank() && !mspPath.isBlank(),
                "Pass both -Dmalody.v.sample.bundle and -Dmalody.v.sample.msp");
        VRuntimeCaptureBundle bundle = VRuntimeCaptureBundle.load(Path.of(bundlePath));
        MspSkinDocument skin = MspSkinDocument.open(Path.of(mspPath));
        assertEquals(skin.asmSha256(), bundle.snapshot().asmSha256());
        assertEquals(skin.luaHash(), bundle.snapshot().luaHash());
        assertEquals(1752, bundle.sourceWidth());
        assertEquals(986, bundle.sourceHeight());
        assertEquals(1752, bundle.imageWidth());
        assertEquals(986, bundle.imageHeight());
        assertEquals(97, bundle.snapshot().moduleCount());
        int matches = 0;
        for (int index = 0; index < skin.skin().getModulesCount(); index++) {
            if (!"trackbg".equals(skin.skin().getModules(index).getMeta().getDesc())) continue;
            var module = bundle.snapshot().match(skin.skin(), index);
            if (module.isEmpty()) continue;
            assertEquals(1110f, module.orElseThrow().runtime().height());
            matches++;
        }
        assertEquals(1, matches);
    }

    private Path fixture() throws IOException {
        Path image = directory.resolve("game-view.png");
        assertTrue(ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB),
                "png", image.toFile()));
        JsonObject status = new JsonObject();
        status.addProperty("phase", "chart-time-frozen");
        status.addProperty("hasTime", true);
        status.addProperty("unityFrame", 42);
        status.addProperty("audioTimeMs", 1200);
        status.addProperty("chartTimeMs", 1190);
        status.addProperty("displayTimeMs", 1180);
        write(directory.resolve("status.json"), status);

        JsonObject modules = new JsonObject();
        modules.addProperty("schemaVersion", 1);
        modules.addProperty("utc", "2026-09-26T00:00:00Z");
        modules.addProperty("skinTitle", "Skin");
        modules.addProperty("skinScriptFile", "main.lua");
        modules.addProperty("asmSha256", "a".repeat(64));
        modules.addProperty("luaHash", "abc");
        modules.addProperty("chartFile", "chart.mc");
        modules.addProperty("audioTimeMs", 1200);
        modules.addProperty("chartTimeMs", 1190);
        modules.addProperty("displayTimeMs", 1180);
        modules.addProperty("unityReportedScreenWidth", 4);
        modules.addProperty("unityReportedScreenHeight", 3);
        modules.addProperty("factoryCount", 0);
        modules.addProperty("moduleCount", 0);
        modules.add("modules", new JsonArray());
        write(directory.resolve("modules.json"), modules);

        JsonObject capture = new JsonObject();
        capture.addProperty("captureKind", "game_view");
        JsonObject gameView = new JsonObject();
        gameView.addProperty("sourceWidth", 4);
        gameView.addProperty("sourceHeight", 3);
        capture.add("gameView", gameView);
        JsonObject artifact = new JsonObject();
        artifact.addProperty("mimeType", "image/png");
        artifact.addProperty("width", 4);
        artifact.addProperty("height", 3);
        artifact.addProperty("byteLength", Files.size(image));
        artifact.addProperty("sha256", digest(image));
        capture.add("artifact", artifact);
        write(directory.resolve("capture.json"), capture);

        JsonObject bundle = new JsonObject();
        bundle.addProperty("kind", "malody-v-runtime-capture");
        bundle.addProperty("schemaVersion", 1);
        bundle.addProperty("audioTimeMs", 1200);
        bundle.addProperty("frozenUnityFrame", 42);
        bundle.addProperty("captureStable", true);
        JsonObject files = new JsonObject();
        for (String name : new String[]{"status", "modules", "capture", "image"})
            files.add(name, entry(name.equals("image") ? "game-view.png" : name + ".json"));
        bundle.add("files", files);
        JsonObject dimensions = new JsonObject();
        dimensions.addProperty("sourceWidth", 4);
        dimensions.addProperty("sourceHeight", 3);
        dimensions.addProperty("width", 4);
        dimensions.addProperty("height", 3);
        bundle.add("image", dimensions);
        Path manifest = directory.resolve("bundle.json");
        write(manifest, bundle);
        return manifest;
    }

    private JsonObject entry(String fileName) throws IOException {
        Path path = directory.resolve(fileName);
        JsonObject entry = new JsonObject();
        entry.addProperty("path", fileName);
        entry.addProperty("bytes", Files.size(path));
        entry.addProperty("sha256", digest(path));
        return entry;
    }

    private static JsonObject read(Path path) throws IOException {
        return com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void write(Path path, JsonObject json) throws IOException {
        Files.writeString(path, json.toString());
    }

    private static String digest(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
