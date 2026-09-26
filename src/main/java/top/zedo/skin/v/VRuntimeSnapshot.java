package top.zedo.skin.v;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import top.zedo.skin.v.proto.SkinVProto.SkinFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A read-only, explicitly loaded Emiria module export. This does not evaluate skin scripts. */
final class VRuntimeSnapshot {
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    private final String utc;
    private final String skinTitle;
    private final String skinScriptFile;
    private final String asmSha256;
    private final String luaHash;
    private final String chartFile;
    private final double audioTimeMs;
    private final double chartTimeMs;
    private final double displayTimeMs;
    private final int unityReportedScreenWidth;
    private final int unityReportedScreenHeight;
    private final int factoryCount;
    private final int moduleCount;
    private final List<String> captureErrors;
    private final List<Module> modules;

    private VRuntimeSnapshot(JsonObject root) {
        if (integer(root, "schemaVersion") != 1)
            throw invalid("Unsupported schemaVersion");
        utc = string(root, "utc");
        skinTitle = string(root, "skinTitle");
        skinScriptFile = string(root, "skinScriptFile");
        asmSha256 = sha256(root, "asmSha256");
        luaHash = string(root, "luaHash");
        chartFile = string(root, "chartFile");
        audioTimeMs = number(root, "audioTimeMs");
        chartTimeMs = number(root, "chartTimeMs");
        displayTimeMs = number(root, "displayTimeMs");
        unityReportedScreenWidth = integer(root, "unityReportedScreenWidth");
        unityReportedScreenHeight = integer(root, "unityReportedScreenHeight");
        factoryCount = integer(root, "factoryCount");
        if (factoryCount < 0 || unityReportedScreenWidth < 0 || unityReportedScreenHeight < 0)
            throw invalid("Negative factory count or screen size");

        JsonArray array = array(root, "modules");
        int declared = integer(root, "moduleCount");
        if (declared < 0 || declared != array.size())
            throw invalid("moduleCount does not match modules length");
        moduleCount = declared;
        List<Module> parsed = new ArrayList<>(array.size());
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject row = object(array.get(i), "modules[" + i + "]");
            if (row.has("error") && !row.get("error").isJsonNull()
                    && !string(row, "error").isEmpty()) {
                errors.add("#" + (i + 1) + " " + string(row, "error"));
                continue;
            }
            parsed.add(parseModule(row, i));
        }
        captureErrors = List.copyOf(errors);
        modules = List.copyOf(parsed);
    }

    static VRuntimeSnapshot load(Path path) throws IOException {
        Objects.requireNonNull(path);
        if (Files.size(path) > MAX_BYTES) throw invalid("Module export exceeds 16 MiB");
        byte[] bytes;
        try (InputStream in = Files.newInputStream(path)) {
            bytes = in.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw invalid("Module export exceeds 16 MiB");
        try {
            return new VRuntimeSnapshot(object(JsonParser.parseString(
                    new String(bytes, StandardCharsets.UTF_8)), "root"));
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException
                 | NumberFormatException ex) {
            throw invalid("Invalid module export JSON: " + ex.getMessage());
        }
    }

    String utc() { return utc; }
    String skinTitle() { return skinTitle; }
    String skinScriptFile() { return skinScriptFile; }
    String asmSha256() { return asmSha256; }
    String luaHash() { return luaHash; }
    String chartFile() { return chartFile; }
    double audioTimeMs() { return audioTimeMs; }
    double chartTimeMs() { return chartTimeMs; }
    double displayTimeMs() { return displayTimeMs; }
    int unityReportedScreenWidth() { return unityReportedScreenWidth; }
    int unityReportedScreenHeight() { return unityReportedScreenHeight; }
    int factoryCount() { return factoryCount; }
    int moduleCount() { return moduleCount; }
    List<String> captureErrors() { return captureErrors; }
    List<Module> modules() { return modules; }

    /** Finds a unique source configuration; row order is never used as identity. */
    Optional<Module> match(SkinFile.Module module) {
        Objects.requireNonNull(module);
        Module found = null;
        for (Module candidate : modules) {
            if (!candidate.source().matches(module, candidate.name())) continue;
            if (found != null)
                throw new IllegalStateException("Ambiguous runtime module: " + candidate.name()
                        + " has multiple matching source configurations");
            found = candidate;
        }
        return Optional.ofNullable(found);
    }

    /** Also rejects a source configuration shared by multiple modules in the skin. */
    Optional<Module> match(SkinFile skin, int moduleIndex) {
        Objects.requireNonNull(skin);
        SkinFile.Module selected = skin.getModules(moduleIndex);
        Optional<Module> result = match(selected);
        if (result.isEmpty()) return result;
        Module row = result.orElseThrow();
        for (int i = 0; i < skin.getModulesCount(); i++) {
            if (i != moduleIndex && row.source().matches(skin.getModules(i), row.name()))
                throw new IllegalStateException("Ambiguous skin module: " + row.name()
                        + " has multiple matching source configurations");
        }
        return result;
    }

    private static Module parseModule(JsonObject row, int index) {
        String prefix = "modules[" + index + "]";
        String name = string(row, "name");
        int factoryLayer = integer(row, "factoryLayer");
        String factoryName = string(row, "factoryName");
        JsonObject rawSource = object(member(row, "source"), prefix + ".source");
        JsonObject rawRuntime = object(member(row, "runtime"), prefix + ".runtime");
        boolean hasImage = bool(rawSource, "hasImage");
        Source source = new Source(integer(rawSource, "layer"), integer(rawSource, "order"),
                integer(rawSource, "type"), integer(rawSource, "usage"),
                finiteFloat(rawSource, "x"), finiteFloat(rawSource, "y"),
                finiteFloat(rawSource, "dx"), finiteFloat(rawSource, "dy"),
                string(rawSource, "xUnit"), string(rawSource, "yUnit"),
                string(rawSource, "dxUnit"), string(rawSource, "dyUnit"),
                integer(rawSource, "alpha"), hasImage,
                hasImage ? string(rawSource, "imageFile") : "",
                finiteFloat(rawSource, "imageWidth"), finiteFloat(rawSource, "imageHeight"),
                hasImage ? string(rawSource, "imageWidthUnit") : "",
                hasImage ? string(rawSource, "imageHeightUnit") : "");
        boolean hasRectTransform = bool(rawRuntime, "hasRectTransform");
        boolean hasParentCanvas = bool(rawRuntime, "hasParentCanvas");
        boolean hasCanvasReferenceResolution = bool(rawRuntime, "hasCanvasReferenceResolution");
        Runtime runtime = new Runtime(finiteFloat(rawRuntime, "x"), finiteFloat(rawRuntime, "y"),
                finiteFloat(rawRuntime, "width"), finiteFloat(rawRuntime, "height"),
                integer(rawRuntime, "alpha"), finiteFloat(rawRuntime, "scale"),
                finiteFloat(rawRuntime, "rotate"),
                optionalVec2(rawRuntime, "rectSize", hasRectTransform),
                optionalVec2(rawRuntime, "anchoredPosition", hasRectTransform),
                optionalVec2(rawRuntime, "pivot", hasRectTransform),
                optionalVec3(rawRuntime, "localScale", hasRectTransform),
                optionalVec2(rawRuntime, "parentCanvasSize", hasParentCanvas),
                optionalVec2(rawRuntime, "canvasReferenceResolution", hasCanvasReferenceResolution));
        return new Module(name, factoryLayer, factoryName, source, runtime);
    }

    record Module(String name, int factoryLayer, String factoryName, Source source, Runtime runtime) { }

    record Source(int layer, int order, int type, int usage,
                  float x, float y, float dx, float dy,
                  String xUnit, String yUnit, String dxUnit, String dyUnit,
                  int alpha, boolean hasImage, String imageFile,
                  float imageWidth, float imageHeight, String imageWidthUnit, String imageHeightUnit) {
        boolean matches(SkinFile.Module module, String name) {
            SkinFile.ModuleParam param = module.getParam();
            if (!name.equals(module.getMeta().getDesc()) || layer != param.getLayer()
                    || order != param.getOrder() || type != module.getType()
                    || usage != module.getUsage() || x != param.getX() || y != param.getY()
                    || dx != param.getDx() || dy != param.getDy() || alpha != param.getAlpha()
                    || !xUnit.equals(param.getXu().name()) || !yUnit.equals(param.getYu().name())
                    || !dxUnit.equals(param.getDxu().name()) || !dyUnit.equals(param.getDyu().name())
                    || hasImage != module.hasImage()) return false;
            if (!hasImage) return true;
            SkinFile.ModuleParamImage image = module.getImage();
            return imageFile.equals(fileNameOnly(image.getFile()))
                    && imageWidth == image.getWidth() && imageHeight == image.getHeight()
                    && imageWidthUnit.equals(image.getWu().name())
                    && imageHeightUnit.equals(image.getHu().name());
        }
    }

    record Runtime(float x, float y, float width, float height, int alpha, float scale, float rotate,
                   Optional<Vec2> rectSize, Optional<Vec2> anchoredPosition, Optional<Vec2> pivot,
                   Optional<Vec3> localScale, Optional<Vec2> parentCanvasSize,
                   Optional<Vec2> canvasReferenceResolution) { }

    record Vec2(float x, float y) { }
    record Vec3(float x, float y, float z) { }

    private static Vec2 vec2(JsonObject parent, String key) {
        JsonObject value = object(member(parent, key), key);
        return new Vec2(finiteFloat(value, "x"), finiteFloat(value, "y"));
    }

    private static Optional<Vec2> optionalVec2(JsonObject parent, String key, boolean active) {
        if (!parent.has(key) && !active) return Optional.empty();
        Vec2 value = vec2(parent, key);
        return active ? Optional.of(value) : Optional.empty();
    }

    private static Optional<Vec3> optionalVec3(JsonObject parent, String key, boolean active) {
        if (!parent.has(key) && !active) return Optional.empty();
        Vec3 value = vec3(parent, key);
        return active ? Optional.of(value) : Optional.empty();
    }

    private static Vec3 vec3(JsonObject parent, String key) {
        JsonObject value = object(member(parent, key), key);
        return new Vec3(finiteFloat(value, "x"), finiteFloat(value, "y"), finiteFloat(value, "z"));
    }

    private static JsonElement member(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) throw invalid("Missing " + key);
        return value;
    }

    private static JsonObject object(JsonElement value, String label) {
        if (!value.isJsonObject()) throw invalid(label + " must be an object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = member(parent, key);
        if (!value.isJsonArray()) throw invalid(key + " must be an array");
        return value.getAsJsonArray();
    }

    private static JsonPrimitive primitive(JsonObject parent, String key) {
        JsonElement value = member(parent, key);
        if (!value.isJsonPrimitive()) throw invalid(key + " must be a primitive");
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

    private static int integer(JsonObject parent, String key) {
        JsonPrimitive value = primitive(parent, key);
        if (!value.isNumber()) throw invalid(key + " must be an integer");
        try {
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            throw invalid(key + " must be an integer");
        }
    }

    private static double number(JsonObject parent, String key) {
        JsonPrimitive value = primitive(parent, key);
        if (!value.isNumber()) throw invalid(key + " must be a number");
        double result = value.getAsDouble();
        if (!Double.isFinite(result)) throw invalid(key + " must be finite");
        return result;
    }

    private static float finiteFloat(JsonObject parent, String key) {
        double value = number(parent, key);
        float result = (float) value;
        if (!Float.isFinite(result)) throw invalid(key + " exceeds float range");
        return result;
    }

    private static String sha256(JsonObject parent, String key) {
        String value = string(parent, key);
        if (!value.matches("[0-9a-fA-F]{64}")) throw invalid(key + " must be a SHA-256 hex digest");
        return value;
    }

    private static String fileNameOnly(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(slash + 1);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
