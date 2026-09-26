package top.zedo.skin.v;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** An integrity-checked read-only Game View capture with a frozen module export. */
final class VRuntimeCaptureBundle {
    private static final long MIB = 1024L * 1024;
    private static final String KIND = "malody-v-runtime-capture";
    private final VRuntimeSnapshot snapshot;
    private final Path imagePath;
    private final int sourceWidth;
    private final int sourceHeight;
    private final int imageWidth;
    private final int imageHeight;

    private VRuntimeCaptureBundle(VRuntimeSnapshot snapshot, Path imagePath,
                                  int sourceWidth, int sourceHeight, int imageWidth, int imageHeight) {
        this.snapshot = snapshot;
        this.imagePath = imagePath;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    static VRuntimeCaptureBundle load(Path manifestPath) throws IOException {
        Objects.requireNonNull(manifestPath);
        Path manifest = manifestPath.toAbsolutePath().normalize();
        if (!"bundle.json".equals(manifest.getFileName().toString()))
            throw invalid("Select bundle.json");
        checkRegularFile(manifest);
        JsonObject bundle = readJson(manifest, MIB);
        if (!KIND.equals(string(bundle, "kind")) || integer(bundle, "schemaVersion") != 1)
            throw invalid("Unsupported capture bundle");
        if (!bool(bundle, "captureStable")) throw invalid("Capture is not stable");
        double audioTimeMs = number(bundle, "audioTimeMs");
        int frozenUnityFrame = integer(bundle, "frozenUnityFrame");
        if (frozenUnityFrame < 0) throw invalid("Invalid frozenUnityFrame");

        JsonObject files = object(bundle, "files");
        Path directory = manifest.getParent();
        Path statusPath = verifiedFile(directory, files, "status", "status.json", MIB);
        Path modulesPath = verifiedFile(directory, files, "modules", "modules.json", 16 * MIB);
        Path capturePath = verifiedFile(directory, files, "capture", "capture.json", MIB);
        Path imagePath = verifiedFile(directory, files, "image", "game-view.png", 64 * MIB);

        JsonObject status = readJson(statusPath, MIB);
        if (!"chart-time-frozen".equals(string(status, "phase")) || !bool(status, "hasTime")
                || integer(status, "unityFrame") != frozenUnityFrame
                || !sameTime(number(status, "audioTimeMs"), audioTimeMs))
            throw invalid("Frozen status does not match bundle");
        VRuntimeSnapshot snapshot = VRuntimeSnapshot.load(modulesPath);
        for (String field : new String[]{"audioTimeMs", "chartTimeMs", "displayTimeMs"}) {
            double statusTime = number(status, field);
            double moduleTime = switch (field) {
                case "audioTimeMs" -> snapshot.audioTimeMs();
                case "chartTimeMs" -> snapshot.chartTimeMs();
                default -> snapshot.displayTimeMs();
            };
            if (!sameTime(statusTime, moduleTime))
                throw invalid("Frozen status and modules disagree on " + field);
        }

        JsonObject dimensions = object(bundle, "image");
        int sourceWidth = positiveInteger(dimensions, "sourceWidth");
        int sourceHeight = positiveInteger(dimensions, "sourceHeight");
        int imageWidth = positiveInteger(dimensions, "width");
        int imageHeight = positiveInteger(dimensions, "height");
        JsonObject capture = readJson(capturePath, MIB);
        if (!"game_view".equals(string(capture, "captureKind")))
            throw invalid("Capture is not a Game View image");
        JsonObject gameView = object(capture, "gameView");
        JsonObject artifact = object(capture, "artifact");
        if (positiveInteger(gameView, "sourceWidth") != sourceWidth
                || positiveInteger(gameView, "sourceHeight") != sourceHeight
                || positiveInteger(artifact, "width") != imageWidth
                || positiveInteger(artifact, "height") != imageHeight
                || !"image/png".equals(string(artifact, "mimeType"))
                || longInteger(artifact, "byteLength") != Files.size(imagePath)
                || !sha256(artifact, "sha256").equalsIgnoreCase(digest(imagePath)))
            throw invalid("Capture metadata does not match image");
        checkPngHeader(imagePath, imageWidth, imageHeight);
        BufferedImage image = ImageIO.read(imagePath.toFile());
        if (image == null || image.getWidth() != imageWidth || image.getHeight() != imageHeight)
            throw invalid("PNG dimensions do not match bundle");
        return new VRuntimeCaptureBundle(snapshot, imagePath, sourceWidth, sourceHeight,
                imageWidth, imageHeight);
    }

    VRuntimeSnapshot snapshot() { return snapshot; }
    Path imagePath() { return imagePath; }
    int sourceWidth() { return sourceWidth; }
    int sourceHeight() { return sourceHeight; }
    int imageWidth() { return imageWidth; }
    int imageHeight() { return imageHeight; }

    private static Path verifiedFile(Path directory, JsonObject files, String key,
                                     String expectedName, long limit) throws IOException {
        JsonObject entry = object(files, key);
        if (!expectedName.equals(string(entry, "path")))
            throw invalid("Unexpected " + key + " path");
        Path path = directory.resolve(expectedName);
        checkRegularFile(path);
        long size = Files.size(path);
        if (size >= limit || size != longInteger(entry, "bytes"))
            throw invalid(key + " byte count does not match or exceeds limit");
        if (!digest(path).equalsIgnoreCase(sha256(entry, "sha256")))
            throw invalid(key + " SHA-256 does not match");
        return path;
    }

    private static void checkPngHeader(Path path, int width, int height) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            if (in.readLong() != 0x89504e470d0a1a0aL || in.readInt() != 13
                    || in.readInt() != 0x49484452 || in.readInt() != width
                    || in.readInt() != height)
                throw invalid("PNG header dimensions do not match bundle");
        }
    }

    private static void checkRegularFile(Path path) {
        if (Files.isSymbolicLink(path) || Files.isSymbolicLink(path.getParent()))
            throw invalid("Symbolic links are not allowed");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw invalid("Missing regular file: " + path.getFileName());
    }

    private static JsonObject readJson(Path path, long limit) throws IOException {
        if (Files.size(path) >= limit) throw invalid(path.getFileName() + " exceeds size limit");
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException ex) {
            throw invalid("Invalid JSON in " + path.getFileName());
        }
    }

    private static String digest(Path path) throws IOException {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) sha.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean sameTime(double first, double second) {
        return Math.abs(first - second) < 0.01;
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) throw invalid(key + " must be an object");
        return value.getAsJsonObject();
    }

    private static JsonPrimitive primitive(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive()) throw invalid("Missing " + key);
        return value.getAsJsonPrimitive();
    }

    private static String string(JsonObject parent, String key) {
        JsonPrimitive value = primitive(parent, key);
        if (!value.isString()) throw invalid(key + " must be a string");
        return value.getAsString();
    }

    private static boolean bool(JsonObject parent, String key) {
        JsonPrimitive value = primitive(parent, key);
        if (!value.isBoolean()) throw invalid(key + " must be a boolean");
        return value.getAsBoolean();
    }

    private static long longInteger(JsonObject parent, String key) {
        JsonPrimitive value = primitive(parent, key);
        if (!value.isNumber()) throw invalid(key + " must be an integer");
        try {
            return new BigDecimal(value.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw invalid(key + " must be an integer");
        }
    }

    private static int integer(JsonObject parent, String key) {
        long value = longInteger(parent, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw invalid(key + " must be an integer");
        return (int) value;
    }

    private static int positiveInteger(JsonObject parent, String key) {
        int value = integer(parent, key);
        if (value <= 0 || value > 8192) throw invalid(key + " must be between 1 and 8192");
        return value;
    }

    private static double number(JsonObject parent, String key) {
        JsonPrimitive value = primitive(parent, key);
        if (!value.isNumber()) throw invalid(key + " must be a number");
        double result = value.getAsDouble();
        if (!Double.isFinite(result)) throw invalid(key + " must be finite");
        return result;
    }

    private static String sha256(JsonObject parent, String key) {
        String value = string(parent, key);
        if (!value.matches("[0-9a-fA-F]{64}")) throw invalid(key + " must be SHA-256 hex");
        return value;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
