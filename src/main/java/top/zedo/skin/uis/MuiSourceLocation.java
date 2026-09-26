package top.zedo.skin.uis;

import java.nio.file.Path;

/** Physical source of an effective property after includes and repeated sections. */
public record MuiSourceLocation(Path file, int sectionLine, int propertyLine, boolean grouped) { }
