package top.zedo.skin.uis;

import java.util.ArrayList;
import java.util.List;

/**
 * Source-preserving syntax for one MUI file. Includes and conditions are kept as
 * literal directives; resolving them belongs to {@link MuiSkinLoader}.
 */
final class MuiSyntaxTree {
    private final List<SourceLine> lines;
    private final List<Statement> statements;

    private MuiSyntaxTree(List<SourceLine> lines) {
        this.lines = List.copyOf(lines);
        List<Statement> parsed = new ArrayList<>(lines.size());
        int sectionLine = 0;
        boolean animation = false;
        for (int i = 0; i < lines.size(); i++) {
            Statement statement = parseLine(lines.get(i), i + 1);
            if (statement instanceof Section section) {
                sectionLine = section.lineNumber();
                animation = section.name().startsWith(":");
            } else if (statement instanceof Property property) {
                statement = property.inSection(sectionLine, animation);
            }
            parsed.add(statement);
        }
        statements = List.copyOf(parsed);
    }

    static MuiSyntaxTree parse(String text) {
        List<SourceLine> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != '\r' && ch != '\n') continue;
            int end = i + 1;
            if (ch == '\r' && end < text.length() && text.charAt(end) == '\n') end++;
            lines.add(new SourceLine(text.substring(start, i), text.substring(i, end)));
            i = end - 1;
            start = end;
        }
        if (start < text.length()) lines.add(new SourceLine(text.substring(start), ""));
        return fromLines(lines);
    }

    static MuiSyntaxTree fromLines(List<SourceLine> lines) {
        return new MuiSyntaxTree(lines);
    }

    List<SourceLine> lines() { return lines; }
    List<Statement> statements() { return statements; }
    Statement statementAt(int lineIndex) { return statements.get(lineIndex); }

    String text() {
        StringBuilder result = new StringBuilder();
        for (SourceLine line : lines) result.append(line.body()).append(line.ending());
        return result.toString();
    }

    private static Statement parseLine(SourceLine line, int number) {
        String body = line.body();
        String trimmed = body.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return new Trivia(number, line);
        if (trimmed.startsWith("@")) {
            return new Directive(number, line, List.of(trimmed.split(" +", 3)));
        }
        if (body.startsWith("\t") || body.startsWith("  ")) {
            int equals = body.indexOf('=');
            if (equals < 0) return new MalformedProperty(number, line);
            int trimmedEquals = trimmed.indexOf('=');
            String runtimeName = trimmed.substring(0, trimmedEquals);
            String runtimeValue = trimmed.substring(trimmedEquals + 1);
            int valueStart = equals + 1;
            while (valueStart < body.length() && Character.isWhitespace(body.charAt(valueStart))) valueStart++;
            int valueEnd = body.length();
            while (valueEnd > valueStart && Character.isWhitespace(body.charAt(valueEnd - 1))) valueEnd--;
            return new Property(number, line, body.substring(0, equals).trim(), runtimeName,
                    runtimeValue, valueStart, valueEnd, 0, false);
        }
        return new Section(number, line, trimmed, !Character.isWhitespace(body.charAt(0)));
    }

    record SourceLine(String body, String ending) { }

    sealed interface Statement permits Trivia, Directive, Section, Property, MalformedProperty {
        int lineNumber();
        SourceLine line();
    }

    record Trivia(int lineNumber, SourceLine line) implements Statement { }
    record Directive(int lineNumber, SourceLine line, List<String> arguments) implements Statement { }
    record Section(int lineNumber, SourceLine line, String name, boolean literal) implements Statement { }
    record MalformedProperty(int lineNumber, SourceLine line) implements Statement { }

    record Property(int lineNumber, SourceLine line, String name, String runtimeName,
                    String runtimeValue, int valueStart, int valueEnd,
                    int sectionLine, boolean animation) implements Statement {
        Property inSection(int sectionLine, boolean animation) {
            return new Property(lineNumber, line, name, runtimeName, runtimeValue,
                    valueStart, valueEnd, sectionLine, animation);
        }

        String value() { return line.body().substring(valueStart, valueEnd); }

        SourceLine withValue(String value) {
            return new SourceLine(line.body().substring(0, valueStart) + value
                    + line.body().substring(valueEnd), line.ending());
        }
    }
}
