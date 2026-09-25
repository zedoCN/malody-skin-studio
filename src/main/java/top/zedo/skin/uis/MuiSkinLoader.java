package top.zedo.skin.uis;

import top.zedo.skin.plist.PlistParser;
import top.zedo.zxncore.ZXLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads a MUI file and its includes into one fresh skin snapshot. */
final class MuiSkinLoader {
    private final UISSkin skin;
    private final Map<String, Path> imagePaths;
    private final Set<Path> scannedDirectories = new HashSet<>();
    private final Set<Path> activeIncludes = new HashSet<>();
    private final HashMap<String, UISComponent> components = new HashMap<>();
    private int angle;
    private int unit = MuiRules.DEFAULT_UNIT_HEIGHT;

    MuiSkinLoader(UISSkin skin, Map<String, Path> imagePaths) {
        this.skin = skin;
        this.imagePaths = imagePaths;
    }

    Result load(Path path) throws IOException {
        try {
            parse(path);
        } catch (RuntimeException error) {
            throw new IOException("解析 MUI 文件失败: " + path, error);
        }
        return new Result(components, angle, unit);
    }

    private void parse(Path path) throws IOException {
        Path normalized = path.toRealPath();
        if (!activeIncludes.add(normalized)) {
            throw new IOException("循环引用 MUI 文件: " + normalized);
        }
        try {
            Path directory = normalized.getParent();
            if (scannedDirectories.add(directory)) {
                indexImages(directory);
            }
            try (BufferedReader reader = new BufferedReader(new StringReader(MuiTextFile.read(normalized).text()))) {
                List<UISComponent> currentComponents = new ArrayList<>();
                Deque<Boolean> conditions = new ArrayDeque<>();
                boolean skip = false;
                boolean animation = false;
                String line;
                while ((line = reader.readLine()) != null) {
                    boolean property = line.startsWith("\t") || line.startsWith("  ");
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (line.startsWith("@")) {
                        String[] args = line.split(" +", 3);
                        if (args[0].equals("@if")) {
                            conditions.push(!skip && args.length >= 2 && conditionMatches(args[1]));
                            skip = conditions.contains(false);
                        } else if (args[0].equals("@endif")) {
                            if (!conditions.isEmpty()) conditions.pop();
                            skip = conditions.contains(false);
                        } else if (!skip) {
                            command(args, directory);
                        }
                        continue;
                    }
                    if (skip) continue;
                    if (property) {
                        int delimiter = line.indexOf('=');
                        if (delimiter < 0 || currentComponents.isEmpty()) {
                            throw new IOException("无效的 MUI 属性: " + normalized + ": " + line);
                        }
                        String name = line.substring(0, delimiter);
                        String value = line.substring(delimiter + 1);
                        if (animation) {
                            for (UISComponent component : currentComponents) component.putAnimation(value);
                        } else {
                            boolean embedded = name.equals("motion") && value.startsWith(":name=");
                            String embeddedName = "ea_" + currentComponents.getFirst().getFullName();
                            for (int i = 0; i < currentComponents.size(); i++) {
                                currentComponents.get(i).putProperty(name, embedded ? embeddedName : value, i);
                            }
                            if (embedded) {
                                UISComponent component = new UISComponent(":" + embeddedName, imagePaths, skin);
                                component.putAnimation(value.substring(6));
                                components.put(component.getFullName(), component);
                            }
                        }
                    } else {
                        currentComponents.clear();
                        for (String name : parseComponentNames(line)) {
                            currentComponents.add(components.computeIfAbsent(name,
                                    key -> new UISComponent(key, imagePaths, skin)));
                        }
                        animation = line.startsWith(":");
                    }
                }
            }
        } finally {
            activeIncludes.remove(normalized);
        }
    }

    private void command(String[] args, Path directory) throws IOException {
        if (args.length < 2) return;
        switch (args[0]) {
            case "@texpack" -> {
                Path plist = directory.resolve(args[1] + ".plist");
                if (!Files.exists(plist)) {
                    ZXLogger.warning("引用 纹理包: " + plist.getFileName() + " 文件不存在");
                    return;
                }
                Path cache = directory.resolve("cache");
                Files.createDirectories(cache);
                ZXLogger.info("拆分 纹理包: " + args[1]);
                for (Path image : PlistParser.parser(plist, cache, true)) {
                    imagePaths.put(key(cache, image), image);
                }
            }
            case "@include", "@includex" -> {
                if (args[0].equals("@includex") && args.length != 3) {
                    throw new IOException("@includex 缺少条件或文件名");
                }
                String include = args.length == 2 ? args[1] : conditionMatches(args[1]) ? args[2] : null;
                if (include == null) return;
                Path included = directory.resolve(include);
                if (Files.exists(included)) {
                    ZXLogger.info("引用 mui: " + included.getFileName());
                    parse(included);
                } else {
                    ZXLogger.warning("引用 mui: " + included.getFileName() + " 文件不存在");
                }
            }
            case "@angle" -> angle = Integer.parseInt(args[1]);
            case "@unit" -> unit = Integer.parseInt(args[1]);
            case "@define" -> {
                if (args.length == 3) skin.variable.put(args[1], args[2]);
            }
            default -> { }
        }
    }

    private boolean conditionMatches(String condition) {
        String value = condition.toLowerCase();
        if (value.equals("true")) {
            return true;
        }
        if (value.equals("false")) return false;
        if (skin.deviceType != null) {
            if (value.equals(skin.deviceType.name().toLowerCase())) return true;
            if (value.equals("windows") && skin.deviceType == top.zedo.skin.DeviceType.WINDOWS) return true;
            if (value.equals("touch") && (skin.deviceType == top.zedo.skin.DeviceType.IOS
                    || skin.deviceType == top.zedo.skin.DeviceType.ANDROID)) return true;
            if (value.equals("phone") && skin.deviceType == top.zedo.skin.DeviceType.ANDROID) return true;
        }
        double proportion = skin.getExpressionCalculator().getCanvasWidth() / skin.getExpressionCalculator().getCanvasHeight();
        if (!Double.isFinite(proportion)) return false;
        boolean less = condition.startsWith("<");
        try {
            double threshold = Double.parseDouble(condition.replaceAll("[<>]", ""));
            return less ? proportion <= threshold : proportion >= threshold;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void indexImages(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return dir.getFileName().toString().equals("cache") ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isImage(file)) imagePaths.put(key(directory, file), file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException error) {
                return FileVisitResult.CONTINUE;
            }
        });
        Path cache = directory.resolve("cache");
        if (Files.isDirectory(cache)) {
            try (var files = Files.list(cache)) {
                files.filter(Files::isRegularFile).filter(MuiSkinLoader::isImage)
                        .forEach(file -> imagePaths.put(key(cache, file), file));
            }
        }
    }

    private static String key(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static boolean isImage(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".bmp");
    }

    private static List<String> parseComponentNames(String line) {
        line = line.trim();
        if (!line.contains("[") && !line.contains("]")) return List.of(line);
        int open = line.indexOf('[');
        if (open <= 0 || !line.endsWith("]") || line.indexOf(']', open) != line.length() - 1) {
            throw new IllegalArgumentException("无效的批量组件名: " + line);
        }
        String prefix = line.substring(0, open);
        String sequence = line.substring(open + 1, line.length() - 1);
        List<String> names = new ArrayList<>();
        for (String part : sequence.split(",")) {
            String[] range = part.trim().split("-", -1);
            if (range.length == 1) {
                names.add(prefix + Integer.parseInt(range[0].trim()));
            } else if (range.length == 2) {
                int start = Integer.parseInt(range[0].trim());
                int end = Integer.parseInt(range[1].trim());
                if (start > end || end - start > 10000) throw new IllegalArgumentException("无效的批量范围: " + part);
                for (int i = start; i <= end; i++) names.add(prefix + i);
            } else {
                throw new IllegalArgumentException("无效的批量范围: " + part);
            }
        }
        return names;
    }

    record Result(HashMap<String, UISComponent> components, int angle, int unit) { }
}
