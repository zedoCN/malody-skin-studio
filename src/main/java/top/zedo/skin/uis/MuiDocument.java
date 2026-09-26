package top.zedo.skin.uis;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lossless text document for editing a MUI file. The render model expands includes,
 * conditions and grouped names, so it must not be serialized back to source text.
 * Sections here refer to literal source headers, including repeated and grouped ones.
 */
public final class MuiDocument {
    private final MuiTextFile.Decoded source;
    private final List<Line> lines;

    private MuiDocument(MuiTextFile.Decoded source, List<Line> lines) {
        this.source = source;
        this.lines = List.copyOf(lines);
    }

    public static MuiDocument read(Path path) throws IOException {
        MuiTextFile.Decoded source = MuiTextFile.read(path);
        return new MuiDocument(source, splitLines(source.text()));
    }

    public String text() {
        StringBuilder result = new StringBuilder();
        for (Line line : lines) result.append(line.body).append(line.ending);
        return result.toString();
    }

    /** Ordered literal headers, not the effective components after includes/conditions. */
    public List<Section> sections() {
        List<Section> result = new ArrayList<>();
        int ordinal = 0;
        for (int i = 0; i < lines.size(); i++) {
            String body = lines.get(i).body;
            if (isHeader(body)) result.add(new Section(ordinal++, body.trim(), i + 1));
        }
        return List.copyOf(result);
    }

    /** Literal properties in source order; repeated names are intentionally retained. */
    public List<PropertyValue> properties(int sectionIndex) {
        List<Section> sections = sections();
        int start = sections.get(sectionIndex).lineNumber() - 1;
        int end = sectionIndex + 1 < sections.size()
                ? sections.get(sectionIndex + 1).lineNumber() - 1 : lines.size();
        List<PropertyValue> result = new ArrayList<>();
        for (int i = start + 1; i < end; i++) {
            Property property = parseProperty(lines.get(i).body);
            if (property != null) {
                result.add(new PropertyValue(property.name,
                        lines.get(i).body.substring(property.valueStart, property.valueEnd), i + 1));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Replace the last occurrence of a property in one literal section. Repeated
     * headers and grouped names stay distinct; callers must select a section index.
     * If absent, insert after that section's last property (or its header).
     */
    public MuiDocument withProperty(int sectionIndex, String name, String value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (name.isBlank() || !name.equals(name.trim()) || name.indexOf('=') >= 0
                || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("无效的 MUI 属性名: " + name);
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("MUI 属性值不能包含换行");
        }
        List<Section> sections = sections();
        Section section = sections.get(sectionIndex);
        int start = section.lineNumber() - 1;
        int end = sectionIndex + 1 < sections.size()
                ? sections.get(sectionIndex + 1).lineNumber() - 1 : lines.size();
        int lastProperty = -1;
        int target = -1;
        for (int i = start + 1; i < end; i++) {
            Property property = parseProperty(lines.get(i).body);
            if (property == null) continue;
            lastProperty = i;
            if (property.name.equals(name)) target = i;
        }
        List<Line> changed = new ArrayList<>(lines);
        if (target >= 0) {
            Line line = lines.get(target);
            Property property = parseProperty(line.body);
            String updated = line.body.substring(0, property.valueStart) + value
                    + line.body.substring(property.valueEnd);
            changed.set(target, new Line(updated, line.ending));
        } else {
            int after = lastProperty >= 0 ? lastProperty : start;
            String indent = lastProperty >= 0
                    ? leadingWhitespace(lines.get(lastProperty).body) : preferredIndent();
            String ending = !lines.get(after).ending.isEmpty()
                    ? lines.get(after).ending : preferredEnding();
            if (lines.get(after).ending.isEmpty()) {
                Line previous = lines.get(after);
                changed.set(after, new Line(previous.body, ending));
            }
            changed.add(after + 1, new Line(indent + name + "=" + value,
                    after + 1 < lines.size() ? ending : ""));
        }
        return new MuiDocument(source, changed);
    }

    public void save(Path path) throws IOException {
        source.write(path, text());
    }

    private String preferredIndent() {
        for (Line line : lines) {
            if (parseProperty(line.body) != null) return leadingWhitespace(line.body);
        }
        return "\t";
    }

    private String preferredEnding() {
        for (Line line : lines) {
            if (!line.ending.isEmpty()) return line.ending;
        }
        return System.lineSeparator();
    }

    private static String leadingWhitespace(String body) {
        int i = 0;
        while (i < body.length() && (body.charAt(i) == ' ' || body.charAt(i) == '\t')) i++;
        return body.substring(0, i);
    }

    private static boolean isHeader(String body) {
        String trimmed = body.trim();
        return !trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("@")
                && parseProperty(body) == null && !Character.isWhitespace(body.charAt(0));
    }

    private static Property parseProperty(String body) {
        if (!(body.startsWith("\t") || body.startsWith("  "))) return null;
        String trimmed = body.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("@")) return null;
        int equals = body.indexOf('=');
        if (equals < 0) return null;
        String name = body.substring(0, equals).trim();
        if (name.isEmpty()) return null;
        int valueStart = equals + 1;
        while (valueStart < body.length() && Character.isWhitespace(body.charAt(valueStart))) valueStart++;
        int valueEnd = body.length();
        while (valueEnd > valueStart && Character.isWhitespace(body.charAt(valueEnd - 1))) valueEnd--;
        return new Property(name, valueStart, valueEnd);
    }

    private static List<Line> splitLines(String text) {
        List<Line> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != '\r' && ch != '\n') continue;
            int end = i + 1;
            if (ch == '\r' && end < text.length() && text.charAt(end) == '\n') end++;
            result.add(new Line(text.substring(start, i), text.substring(i, end)));
            i = end - 1;
            start = end;
        }
        if (start < text.length()) result.add(new Line(text.substring(start), ""));
        return result;
    }

    public record Section(int index, String header, int lineNumber) { }
    public record PropertyValue(String name, String value, int lineNumber) { }
    private record Line(String body, String ending) { }
    private record Property(String name, int valueStart, int valueEnd) { }
}
