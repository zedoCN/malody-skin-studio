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
    private final MuiSyntaxTree syntax;

    private MuiDocument(MuiTextFile.Decoded source, MuiSyntaxTree syntax) {
        this.source = source;
        this.syntax = syntax;
    }

    public static MuiDocument read(Path path) throws IOException {
        MuiTextFile.Decoded source = MuiTextFile.read(path);
        return new MuiDocument(source, MuiSyntaxTree.parse(source.text()));
    }

    public String text() {
        return syntax.text();
    }

    /** Ordered literal headers, not the effective components after includes/conditions. */
    public List<Section> sections() {
        List<Section> result = new ArrayList<>();
        int ordinal = 0;
        for (MuiSyntaxTree.Statement statement : syntax.statements()) {
            if (statement instanceof MuiSyntaxTree.Section section && section.literal()) {
                result.add(new Section(ordinal++, section.name(), section.lineNumber()));
            }
        }
        return List.copyOf(result);
    }

    /** Literal properties in source order; repeated names are intentionally retained. */
    public List<PropertyValue> properties(int sectionIndex) {
        List<Section> sections = sections();
        int start = sections.get(sectionIndex).lineNumber() - 1;
        int end = sectionIndex + 1 < sections.size()
                ? sections.get(sectionIndex + 1).lineNumber() - 1 : syntax.lines().size();
        List<PropertyValue> result = new ArrayList<>();
        for (int i = start + 1; i < end; i++) {
            if (syntax.statementAt(i) instanceof MuiSyntaxTree.Property property
                    && property.sectionLine() == sections.get(sectionIndex).lineNumber()
                    && !property.name().isEmpty()) {
                result.add(new PropertyValue(property.name(), property.value(), i + 1));
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
                ? sections.get(sectionIndex + 1).lineNumber() - 1 : syntax.lines().size();
        int lastProperty = -1;
        int target = -1;
        for (int i = start + 1; i < end; i++) {
            if (!(syntax.statementAt(i) instanceof MuiSyntaxTree.Property property)
                    || property.sectionLine() != section.lineNumber() || property.name().isEmpty()) continue;
            lastProperty = i;
            if (property.name().equals(name)) target = i;
        }
        List<MuiSyntaxTree.SourceLine> changed = new ArrayList<>(syntax.lines());
        if (target >= 0) {
            MuiSyntaxTree.Property property = (MuiSyntaxTree.Property) syntax.statementAt(target);
            changed.set(target, property.withValue(value));
        } else {
            int after = lastProperty >= 0 ? lastProperty : start;
            String indent = lastProperty >= 0
                    ? leadingWhitespace(syntax.lines().get(lastProperty).body()) : preferredIndent();
            String ending = !syntax.lines().get(after).ending().isEmpty()
                    ? syntax.lines().get(after).ending() : preferredEnding();
            if (syntax.lines().get(after).ending().isEmpty()) {
                MuiSyntaxTree.SourceLine previous = syntax.lines().get(after);
                changed.set(after, new MuiSyntaxTree.SourceLine(previous.body(), ending));
            }
            changed.add(after + 1, new MuiSyntaxTree.SourceLine(indent + name + "=" + value,
                    after + 1 < syntax.lines().size() ? ending : ""));
        }
        return new MuiDocument(source, MuiSyntaxTree.fromLines(changed));
    }

    /** Replace one exact source property, rejecting edits after its line has changed. */
    public MuiDocument replacePropertyAtLine(int lineNumber, String name, String expectedValue, String value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(expectedValue, "expectedValue");
        Objects.requireNonNull(value, "value");
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("MUI 属性值不能包含换行");
        }
        if (lineNumber < 1 || lineNumber > syntax.lines().size()) {
            throw new IllegalStateException("MUI 源属性行已变动: " + lineNumber);
        }
        if (!(syntax.statementAt(lineNumber - 1) instanceof MuiSyntaxTree.Property property)
                || !property.name().equals(name) || !property.value().equals(expectedValue)) {
            throw new IllegalStateException("MUI 源属性行已变动: " + lineNumber);
        }
        List<MuiSyntaxTree.SourceLine> changed = new ArrayList<>(syntax.lines());
        changed.set(lineNumber - 1, property.withValue(value));
        return new MuiDocument(source, MuiSyntaxTree.fromLines(changed));
    }

    /** Guard both the literal section and the property before editing an effective component. */
    public MuiDocument replacePropertyAt(MuiSourceLocation source, String expectedHeader,
                                         String name, String expectedValue, String value) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(expectedHeader, "expectedHeader");
        int sectionIndex = source.sectionLine() - 1;
        int propertyIndex = source.propertyLine() - 1;
        if (source.grouped() || sectionIndex < 0 || propertyIndex <= sectionIndex
                || propertyIndex >= syntax.lines().size()
                || !(syntax.statementAt(sectionIndex) instanceof MuiSyntaxTree.Section section)
                || !section.name().equals(expectedHeader)
                || !(syntax.statementAt(propertyIndex) instanceof MuiSyntaxTree.Property property)
                || property.sectionLine() != source.sectionLine()) {
            throw new IllegalStateException("MUI 组件段已变动: " + source.file() + ":" + source.sectionLine());
        }
        return replacePropertyAtLine(source.propertyLine(), name, expectedValue, value);
    }

    public void save(Path path) throws IOException {
        source.write(path, text());
    }

    private String preferredIndent() {
        for (MuiSyntaxTree.Statement statement : syntax.statements()) {
            if (statement instanceof MuiSyntaxTree.Property property && !property.name().isEmpty()) {
                return leadingWhitespace(property.line().body());
            }
        }
        return "\t";
    }

    private String preferredEnding() {
        for (MuiSyntaxTree.SourceLine line : syntax.lines()) {
            if (!line.ending().isEmpty()) return line.ending();
        }
        return System.lineSeparator();
    }

    private static String leadingWhitespace(String body) {
        int i = 0;
        while (i < body.length() && (body.charAt(i) == ' ' || body.charAt(i) == '\t')) i++;
        return body.substring(0, i);
    }

    public record Section(int index, String header, int lineNumber) { }
    public record PropertyValue(String name, String value, int lineNumber) { }
}
