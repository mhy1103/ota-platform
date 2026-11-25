package com.ota.platform.installer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 读 APK 里编译过的 AndroidManifest.xml（二进制 XML）：包名、版本号、meta-data */
final class Axml {

    static final int TYPE_REFERENCE = 0x01, TYPE_STRING = 0x03, TYPE_INT_DEC = 0x10, TYPE_INT_HEX = 0x11, TYPE_BOOLEAN = 0x12;
    private static final int ATTR_NAME = 0x01010003, ATTR_VALUE = 0x01010024, ATTR_RESOURCE = 0x01010025,
            ATTR_VERSION_CODE = 0x0101021b, ATTR_VERSION_NAME = 0x0101021c,
            ATTR_USES_CLEARTEXT = 0x010104ec;

    /** 属性值：字面量（string 不为空）或者资源引用、数字、布尔（type + data） */
    record Value(int type, int data, String string) {
        boolean isReference() {
            return type == TYPE_REFERENCE;
        }
    }

    record Manifest(String packageName, Value versionCode, Value versionName, Map<String, Value> meta, Value usesCleartextTraffic) {}

    private Axml() {
    }

    static Manifest parse(byte[] bytes) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 8 || (b.getShort(0) & 0xffff) != 0x0003) throw new IOException("AndroidManifest.xml 不是编译过的二进制 XML");
        List<String> strings = List.of();
        int[] resIds = new int[0];
        String pkg = null;
        Value code = null, name = null, cleartext = null;
        Map<String, Value> meta = new LinkedHashMap<>();
        int pos = b.getShort(2) & 0xffff;
        while (pos + 8 <= bytes.length) {
            int type = b.getShort(pos) & 0xffff, headerSize = b.getShort(pos + 2) & 0xffff, size = b.getInt(pos + 4);
            if (size < 8 || pos + size > bytes.length) break;
            if (type == 0x0001) {
                strings = stringPool(b, pos);
            } else if (type == 0x0180) {
                resIds = new int[(size - headerSize) / 4];
                for (int i = 0; i < resIds.length; i++) resIds[i] = b.getInt(pos + headerSize + i * 4);
            } else if (type == 0x0102) {
                int ext = pos + headerSize;
                String tag = str(strings, b.getInt(ext + 4));
                int attrStart = b.getShort(ext + 8) & 0xffff, attrSize = b.getShort(ext + 10) & 0xffff, count = b.getShort(ext + 12) & 0xffff;
                Map<Object, Value> attrs = new HashMap<>();
                for (int i = 0; i < count; i++) {
                    int a = ext + attrStart + i * attrSize;
                    int nameIdx = b.getInt(a + 4), raw = b.getInt(a + 8), dataType = b.get(a + 15) & 0xff, data = b.getInt(a + 16);
                    String attrName = str(strings, nameIdx);
                    String literal = raw >= 0 ? str(strings, raw) : dataType == TYPE_STRING ? str(strings, data) : null;
                    Value v = new Value(dataType, data, literal);
                    if (attrName != null && !attrName.isEmpty()) attrs.put(attrName, v);
                    if (nameIdx >= 0 && nameIdx < resIds.length) attrs.put(resIds[nameIdx], v);
                }
                if ("manifest".equals(tag)) {
                    Value p = attrs.get("package");
                    pkg = p == null ? null : p.string();
                    code = pick(attrs, "versionCode", ATTR_VERSION_CODE);
                    name = pick(attrs, "versionName", ATTR_VERSION_NAME);
                } else if ("application".equals(tag)) {
                    cleartext = pick(attrs, "usesCleartextTraffic", ATTR_USES_CLEARTEXT);
                } else if ("meta-data".equals(tag)) {
                    Value n = pick(attrs, "name", ATTR_NAME);
                    Value v = pick(attrs, "value", ATTR_VALUE);
                    if (v == null) v = pick(attrs, "resource", ATTR_RESOURCE);
                    if (n != null && n.string() != null && v != null) meta.put(n.string(), v);
                }
            }
            pos += size;
        }
        return new Manifest(pkg, code, name, meta, cleartext);
    }

    private static Value pick(Map<Object, Value> attrs, String name, int id) {
        Value v = attrs.get(id);
        return v != null ? v : attrs.get(name);
    }

    private static String str(List<String> strings, int idx) {
        return idx >= 0 && idx < strings.size() ? strings.get(idx) : null;
    }

    /** ResStringPool：UTF-8 或 UTF-16 */
    static List<String> stringPool(ByteBuffer b, int pos) {
        int count = b.getInt(pos + 8), flags = b.getInt(pos + 16), stringsStart = b.getInt(pos + 20);
        int headerSize = b.getShort(pos + 2) & 0xffff;
        boolean utf8 = (flags & 0x100) != 0;
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int at = pos + stringsStart + b.getInt(pos + headerSize + i * 4);
            try {
                out.add(utf8 ? utf8(b, at) : utf16(b, at));
            } catch (RuntimeException e) {
                out.add("");
            }
        }
        return out;
    }

    private static String utf8(ByteBuffer b, int at) {
        int p = at;
        int first = b.get(p++) & 0xff;
        if ((first & 0x80) != 0) p++;
        int len = b.get(p++) & 0xff;
        if ((len & 0x80) != 0) len = ((len & 0x7f) << 8) | (b.get(p++) & 0xff);
        byte[] raw = new byte[len];
        for (int i = 0; i < len; i++) raw[i] = b.get(p + i);
        return new String(raw, StandardCharsets.UTF_8);
    }

    private static String utf16(ByteBuffer b, int at) {
        int p = at;
        int len = b.getShort(p) & 0xffff;
        p += 2;
        if ((len & 0x8000) != 0) {
            len = ((len & 0x7fff) << 16) | (b.getShort(p) & 0xffff);
            p += 2;
        }
        char[] chars = new char[len];
        for (int i = 0; i < len; i++) chars[i] = (char) (b.getShort(p + i * 2) & 0xffff);
        return new String(chars);
    }
}
