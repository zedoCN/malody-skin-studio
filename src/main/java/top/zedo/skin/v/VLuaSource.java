package top.zedo.skin.v;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Loads the Lua file referenced by a V skin's metadata for editing. */
final class VLuaSource {

    record Result(String path, String source, String diagnostic, byte[] originalBytes, boolean bom) {
        Result {
            if (originalBytes != null) originalBytes = originalBytes.clone();
        }

        @Override public byte[] originalBytes() {
            return originalBytes == null ? null : originalBytes.clone();
        }
    }

    static Result load(MspSkinDocument document) {
        String path = document.skin().getMeta().getScript();
        if (path.isBlank()) return new Result("", "", "此皮肤未引用 Lua 脚本。", null, false);

        byte[] bytes;
        try {
            bytes = document.resource(path, MspSkinDocument.MAX_LUA_BYTES);
        } catch (IOException error) {
            return new Result(path, "", "无法读取 Lua 文件：" + error.getMessage(), null, false);
        }
        if (bytes == null) return new Result(path, "", "引用的 Lua 文件不存在：" + path, null, false);

        int offset = bytes.length >= 3 && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf ? 3 : 0;
        try {
            String source = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
            return new Result(path, source, "可编辑源码 · UTF-8 · 不执行 Lua", bytes, offset == 3);
        } catch (CharacterCodingException error) {
            return new Result(path, "", "Lua 文件不是有效的 UTF-8 文本：" + path, null, false);
        }
    }

    static String editorText(Result loaded) {
        return normalizeLineEndings(loaded.source());
    }

    static byte[] editedBytes(Result loaded, String source) {
        source = normalizeLineEndings(source);
        if (source.equals(editorText(loaded))) return loaded.originalBytes();
        String ending = uniformLineEnding(loaded.source());
        byte[] content = source.replace("\n", ending).getBytes(StandardCharsets.UTF_8);
        if (!loaded.bom()) return content;
        byte[] withBom = new byte[content.length + 3];
        withBom[0] = (byte) 0xef;
        withBom[1] = (byte) 0xbb;
        withBom[2] = (byte) 0xbf;
        System.arraycopy(content, 0, withBom, 3, content.length);
        return withBom;
    }

    private static String normalizeLineEndings(String source) {
        return source.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String uniformLineEnding(String source) {
        boolean crlf = false, cr = false, lf = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '\r') {
                if (i + 1 < source.length() && source.charAt(i + 1) == '\n') { crlf = true; i++; }
                else cr = true;
            } else if (ch == '\n') lf = true;
        }
        if (crlf && !cr && !lf) return "\r\n";
        if (cr && !crlf && !lf) return "\r";
        return "\n";
    }

    private VLuaSource() { }
}
