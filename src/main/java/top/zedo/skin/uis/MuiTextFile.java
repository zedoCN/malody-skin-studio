package top.zedo.skin.uis;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
            Files.write(path, bytes);
        }
    }
}
