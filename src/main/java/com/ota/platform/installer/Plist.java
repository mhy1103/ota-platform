package com.ota.platform.installer;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 读 iOS 的 plist（Info.plist、Expo.plist），二进制格式和 XML 格式都行。字典是 Map，数组是 List */
final class Plist {

    private Plist() {
    }

    static Object parse(byte[] bytes) throws IOException {
        if (bytes.length >= 8 && new String(bytes, 0, 8, StandardCharsets.US_ASCII).equals("bplist00")) return new Binary(bytes).top();
        return xml(bytes);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> dict(byte[] bytes) throws IOException {
        Object o = parse(bytes);
        if (!(o instanceof Map)) throw new IOException("plist 的根不是字典");
        return (Map<String, Object>) o;
    }

    // ---------- XML ----------

    private static Object xml(byte[] bytes) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setExpandEntityReferences(false);
            f.setXIncludeAware(false);
            Element root = f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            Element first = firstElement(root);
            return first == null ? null : value(first);
        } catch (Exception e) {
            throw new IOException("plist 读不懂：" + e.getMessage());
        }
    }

    private static Element firstElement(Node n) {
        for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) if (c instanceof Element e) return e;
        return null;
    }

    private static Object value(Element e) {
        switch (e.getTagName()) {
            case "dict" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                String key = null;
                for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
                    if (!(c instanceof Element el)) continue;
                    if (el.getTagName().equals("key")) key = el.getTextContent();
                    else if (key != null) {
                        m.put(key, value(el));
                        key = null;
                    }
                }
                return m;
            }
            case "array" -> {
                List<Object> l = new ArrayList<>();
                for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) if (c instanceof Element el) l.add(value(el));
                return l;
            }
            case "true" -> {
                return Boolean.TRUE;
            }
            case "false" -> {
                return Boolean.FALSE;
            }
            case "integer" -> {
                try {
                    return Long.parseLong(e.getTextContent().trim());
                } catch (NumberFormatException ex) {
                    return e.getTextContent().trim();
                }
            }
            case "real" -> {
                try {
                    return Double.parseDouble(e.getTextContent().trim());
                } catch (NumberFormatException ex) {
                    return e.getTextContent().trim();
                }
            }
            case "data" -> {
                return Base64.getMimeDecoder().decode(e.getTextContent().trim());
            }
            default -> {
                return e.getTextContent();
            }
        }
    }

    // ---------- 二进制（bplist00） ----------

    private static final class Binary {
        private final ByteBuffer b;
        private final int offsetSize, refSize;
        private final long[] offsets;
        private final long top;
        private int depth;

        Binary(byte[] bytes) throws IOException {
            if (bytes.length < 40) throw new IOException("二进制 plist 太短");
            b = ByteBuffer.wrap(bytes);
            int t = bytes.length - 32;
            offsetSize = b.get(t + 6) & 0xff;
            refSize = b.get(t + 7) & 0xff;
            long count = b.getLong(t + 8);
            top = b.getLong(t + 16);
            long table = b.getLong(t + 24);
            if (count <= 0 || count > 1_000_000 || offsetSize < 1 || offsetSize > 8 || refSize < 1 || refSize > 8
                    || table < 8 || table + count * offsetSize > t) throw new IOException("二进制 plist 结构不对");
            offsets = new long[(int) count];
            for (int i = 0; i < count; i++) offsets[i] = uint((int) (table + (long) i * offsetSize), offsetSize);
        }

        Object top() throws IOException {
            return object(top);
        }

        private long uint(int at, int size) {
            long v = 0;
            for (int i = 0; i < size; i++) v = (v << 8) | (b.get(at + i) & 0xff);
            return v;
        }

        private Object object(long ref) throws IOException {
            if (ref < 0 || ref >= offsets.length) throw new IOException("二进制 plist 引用越界");
            if (++depth > 64) throw new IOException("二进制 plist 嵌套太深");
            try {
                int at = (int) offsets[(int) ref];
                int marker = b.get(at) & 0xff, kind = marker >> 4, info = marker & 0x0f;
                switch (kind) {
                    case 0x0 -> {
                        return info == 0x9 ? Boolean.TRUE : info == 0x8 ? Boolean.FALSE : null;
                    }
                    case 0x1 -> {
                        int n = 1 << info;
                        return n == 8 ? b.getLong(at + 1) : uint(at + 1, n);
                    }
                    case 0x2 -> {
                        return info == 2 ? (double) b.getFloat(at + 1) : b.getDouble(at + 1);
                    }
                    case 0x3 -> {
                        return String.valueOf(b.getDouble(at + 1));
                    }
                    case 0x4, 0x5, 0x6, 0xA, 0xD -> {
                        int[] len = length(at, info);
                        int start = len[1];
                        int n = len[0];
                        if (kind == 0x4) {
                            byte[] d = new byte[n];
                            for (int i = 0; i < n; i++) d[i] = b.get(start + i);
                            return d;
                        }
                        if (kind == 0x5) {
                            byte[] d = new byte[n];
                            for (int i = 0; i < n; i++) d[i] = b.get(start + i);
                            return new String(d, StandardCharsets.US_ASCII);
                        }
                        if (kind == 0x6) {
                            char[] c = new char[n];
                            for (int i = 0; i < n; i++) c[i] = b.getChar(start + i * 2);
                            return new String(c);
                        }
                        if (kind == 0xA) {
                            List<Object> l = new ArrayList<>(n);
                            for (int i = 0; i < n; i++) l.add(object(uint(start + i * refSize, refSize)));
                            return l;
                        }
                        Map<String, Object> m = new LinkedHashMap<>();
                        for (int i = 0; i < n; i++) {
                            Object k = object(uint(start + i * refSize, refSize));
                            Object v = object(uint(start + (n + i) * refSize, refSize));
                            m.put(String.valueOf(k), v);
                        }
                        return m;
                    }
                    case 0x8 -> {
                        return uint(at + 1, info + 1);
                    }
                    default -> {
                        return null;
                    }
                }
            } finally {
                depth--;
            }
        }

        /** 长度和内容开始的位置：低 4 位是 0xF 时，后面跟一个整数对象表示长度 */
        private int[] length(int at, int info) {
            if (info != 0x0f) return new int[]{info, at + 1};
            int m = b.get(at + 1) & 0xff;
            int n = 1 << (m & 0x0f);
            return new int[]{(int) uint(at + 2, n), at + 2 + n};
        }
    }
}
