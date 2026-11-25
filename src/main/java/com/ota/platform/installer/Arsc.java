package com.ota.platform.installer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 读 APK 的 resources.arsc，只为了解析 AndroidManifest.xml 里 @string/xxx 这类引用
 * （expo-updates 把运行版本写在 @string/expo_runtime_version 里）。同一个资源有多套配置时取第一套（默认配置）
 */
final class Arsc {

    private final List<String> strings;
    /** 资源 ID → [类型, 数据] */
    private final Map<Integer, int[]> values = new HashMap<>();

    private Arsc(List<String> strings) {
        this.strings = strings;
    }

    static Arsc parse(byte[] bytes) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 12 || (b.getShort(0) & 0xffff) != 0x0002) throw new IOException("不是 resources.arsc");
        int pos = b.getShort(2) & 0xffff;
        Arsc res = null;
        while (pos + 8 <= bytes.length) {
            int type = b.getShort(pos) & 0xffff, size = b.getInt(pos + 4);
            if (size < 8 || pos + size > bytes.length) break;
            if (type == 0x0001 && res == null) res = new Arsc(Axml.stringPool(b, pos));
            else if (type == 0x0200 && res != null) res.pkg(b, pos, size);
            pos += size;
        }
        if (res == null) throw new IOException("resources.arsc 里没有字符串表");
        return res;
    }

    private void pkg(ByteBuffer b, int start, int size) {
        int id = b.getInt(start + 8);
        int pos = start + (b.getShort(start + 2) & 0xffff);
        int end = start + size;
        while (pos + 8 <= end) {
            int type = b.getShort(pos) & 0xffff, headerSize = b.getShort(pos + 2) & 0xffff, chunk = b.getInt(pos + 4);
            if (chunk < 8 || pos + chunk > end) break;
            if (type == 0x0201) type(b, pos, headerSize, id);
            pos += chunk;
        }
    }

    private void type(ByteBuffer b, int start, int headerSize, int pkg) {
        int typeId = b.get(start + 8) & 0xff, flags = b.get(start + 9) & 0xff;
        int count = b.getInt(start + 12), entriesStart = b.getInt(start + 16);
        boolean sparse = (flags & 0x01) != 0;
        for (int i = 0; i < count; i++) {
            int index, offset;
            if (sparse) {
                index = b.getShort(start + headerSize + i * 4) & 0xffff;
                offset = (b.getShort(start + headerSize + i * 4 + 2) & 0xffff) * 4;
            } else {
                index = i;
                offset = b.getInt(start + headerSize + i * 4);
                if (offset == -1) continue;
            }
            int entry = start + entriesStart + offset;
            int entrySize = b.getShort(entry) & 0xffff, entryFlags = b.getShort(entry + 2) & 0xffff;
            int resId = (pkg << 24) | (typeId << 16) | index;
            if ((entryFlags & 0x0008) != 0) {
                // 紧凑格式（Android 14 以后的 aapt2）：值直接写在条目里，类型在 flags 的高 8 位
                values.putIfAbsent(resId, new int[]{(entryFlags >> 8) & 0xff, b.getInt(entry + 4)});
                continue;
            }
            if ((entryFlags & 0x0001) != 0) continue;
            int value = entry + entrySize;
            values.putIfAbsent(resId, new int[]{b.get(value + 3) & 0xff, b.getInt(value + 4)});
        }
    }

    /** 解析引用，最多跟 5 层；不是字符串时按类型转成文字 */
    String resolve(int resId) {
        int id = resId;
        for (int depth = 0; depth < 5; depth++) {
            int[] v = values.get(id);
            if (v == null) return null;
            switch (v[0]) {
                case Axml.TYPE_STRING -> {
                    return v[1] >= 0 && v[1] < strings.size() ? strings.get(v[1]) : null;
                }
                case Axml.TYPE_REFERENCE -> id = v[1];
                case Axml.TYPE_BOOLEAN -> {
                    return v[1] != 0 ? "true" : "false";
                }
                case Axml.TYPE_INT_DEC -> {
                    return String.valueOf(v[1]);
                }
                default -> {
                    return null;
                }
            }
        }
        return null;
    }
}
