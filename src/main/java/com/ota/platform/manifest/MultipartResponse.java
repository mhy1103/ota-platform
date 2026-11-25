package com.ota.platform.manifest;

import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Expo Updates 协议的 multipart/mixed 响应。每段带 Content-Disposition，需要时带 expo-signature。 */
final class MultipartResponse {

    record Part(String name, String body, String signature, String keyId) {}

    private MultipartResponse() {}

    static void send(HttpServletResponse res, int protocolVersion, List<Part> parts) throws IOException {
        String boundary = "----ExpoUpdates" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Part part : parts) {
            write(body, "--" + boundary + "\r\n");
            write(body, "Content-Disposition: form-data; name=\"" + part.name() + "\"\r\n");
            write(body, "Content-Type: application/json; charset=utf-8\r\n");
            if (part.signature() != null) {
                write(body, "expo-signature: sig=\"" + part.signature() + "\", keyid=\"" + part.keyId() + "\"\r\n");
            }
            write(body, "\r\n");
            write(body, part.body());
            write(body, "\r\n");
        }
        write(body, "--" + boundary + "--\r\n");
        byte[] bytes = body.toByteArray();
        res.setStatus(200);
        res.setHeader("expo-protocol-version", String.valueOf(protocolVersion));
        res.setHeader("expo-sfv-version", "0");
        res.setHeader("cache-control", "private, max-age=0");
        res.setHeader("Content-Type", "multipart/mixed; boundary=" + boundary);
        res.setContentLength(bytes.length);
        res.getOutputStream().write(bytes);
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }
}
