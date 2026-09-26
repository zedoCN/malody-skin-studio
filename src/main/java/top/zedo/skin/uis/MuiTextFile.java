package top.zedo.skin.uis;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Old MUI skins in the wild use both UTF-8 and GB18030. */
public final class MuiTextFile {
    private static final Charset LEGACY = Charset.forName("GB18030");
    private MuiTextFile() { }

    public static Decoded read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        boolean bom = bytes.length >= 3 && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf;
        int offset = bom ? 3 : 0;
        try {
            return new Decoded(decode(bytes, offset, StandardCharsets.UTF_8), StandardCharsets.UTF_8, bom);
        } catch (CharacterCodingException ignored) {
            try {
                return new Decoded(decode(bytes, 0, LEGACY), LEGACY, false);
            } catch (CharacterCodingException error) {
                throw new IOException("无法识别 MUI 文本编码: " + path, error);
            }
        }
    }

    private static String decode(byte[] bytes, int offset, Charset charset) throws CharacterCodingException {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
    }

    public record Decoded(String text, Charset charset, boolean utf8Bom) {
        /** RichTextFX edits use LF internally; keep a source file's uniform line ending. */
        public void writeEditorText(Path path, String contents) throws IOException {
            String ending = uniformLineEnding(text);
            if (ending != null && !ending.equals("\n")) {
                contents = contents.replace("\r\n", "\n").replace('\r', '\n')
                        .replace("\n", ending);
            }
            write(path, contents);
        }

        public void write(Path path, String contents) throws IOException {
            byte[] bytes;
            try {
                ByteBuffer encoded = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(contents));
                bytes = new byte[encoded.remaining()];
                encoded.get(bytes);
            } catch (CharacterCodingException error) {
                throw new IOException("皮肤编码 " + charset.name() + " 无法保存新增字符", error);
            }
            if (utf8Bom) {
                byte[] withBom = new byte[bytes.length + 3];
                withBom[0] = (byte) 0xef;
                withBom[1] = (byte) 0xbb;
                withBom[2] = (byte) 0xbf;
                System.arraycopy(bytes, 0, withBom, 3, bytes.length);
                bytes = withBom;
            }
            Path target = Files.isSymbolicLink(path) ? path.toRealPath() : path;
            Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), ".mui-save-", ".tmp");
            try {
                Files.write(temporary, bytes);
                if (Files.exists(target)) {
                    try {
                        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(target);
                        Files.setPosixFilePermissions(temporary, permissions);
                    } catch (UnsupportedOperationException ignored) {
                        // Non-POSIX file systems still support replacing the content.
                    }
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }

        private static String uniformLineEnding(String text) {
            String ending = null;
            for (int i = 0; i < text.length(); i++) {
                char character = text.charAt(i);
                if (character != '\r' && character != '\n') continue;
                String next = character == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n'
                        ? "\r\n" : String.valueOf(character);
                if (ending != null && !ending.equals(next)) return null;
                ending = next;
                if (next.equals("\r\n")) i++;
            }
            return ending;
        }
    }
}
