"""本地模拟 S3 兼容对象存储，严格校验 AWS Signature V4：python3 s3mock.py 端口 AK SK 存储桶
GET /__s3?public=0|1&head403=0|1&region=xxx 改行为：
  public   匿名 GET 能不能下载（存储桶公共读）
  head403  HEAD 不存在的对象回 403（像 AWS 没给 ListBucket 权限）
  region   只认这个区域（空 = 任何区域都行，像 OSS / COS）
  gone     存储桶被删了：所有请求都回 NoSuchBucket"""
import hashlib
import hmac
import json
import re
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT, AK, SK, BUCKET = int(sys.argv[1]), sys.argv[2], sys.argv[3], sys.argv[4]
mode = {"public": 1, "head403": 0, "region": "", "gone": 0}
objects = {}
log = []
lock = threading.Lock()


def err(code, msg):
    return f'<?xml version="1.0" encoding="UTF-8"?><Error><Code>{code}</Code><Message>{msg}</Message><RequestId>mock</RequestId></Error>'.encode()


def sign(key, msg):
    return hmac.new(key, msg.encode(), hashlib.sha256).digest()


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def reply(self, status, body=b"", ctype="application/xml", head=False, extra=None):
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if not head and body:
            self.wfile.write(body)

    def target(self):
        host = (self.headers.get("Host") or "").lower()
        path = self.path.split("?")[0]
        if host.startswith(BUCKET + "."):
            return BUCKET, path[1:], "virtual"
        m = re.match(r"^/([^/]+)/?(.*)$", path)
        return (m.group(1), m.group(2), "path") if m else ("", "", "path")

    def verify(self, body):
        auth = self.headers.get("Authorization") or ""
        m = re.match(r"AWS4-HMAC-SHA256 Credential=([^/]+)/(\d{8})/([^/]+)/s3/aws4_request, ?SignedHeaders=([^,]+), ?Signature=([0-9a-f]{64})$", auth)
        if not m:
            return 400, "AuthorizationHeaderMalformed", "Authorization 头格式不对：" + auth[:80]
        ak, date, region, signed, sig = m.groups()
        if ak != AK:
            return 403, "InvalidAccessKeyId", "The Access Key Id you provided does not exist in our records."
        if mode["region"] and region != mode["region"]:
            return 400, "AuthorizationHeaderMalformed", f"The authorization header is malformed; the region '{region}' is wrong; expecting '{mode['region']}'"
        payload = self.headers.get("x-amz-content-sha256") or ""
        if self.command == "PUT" and hashlib.sha256(body).hexdigest() != payload:
            return 400, "XAmzContentSHA256Mismatch", "The provided 'x-amz-content-sha256' header does not match what was computed."
        canon_headers = "".join(f"{h}:{(self.headers.get(h) or '').strip()}\n" for h in signed.split(";"))
        canon = "\n".join([self.command, self.path.split("?")[0], "", canon_headers, signed, payload])
        amz_date = self.headers.get("x-amz-date") or ""
        scope = f"{date}/{region}/s3/aws4_request"
        to_sign = "\n".join(["AWS4-HMAC-SHA256", amz_date, scope, hashlib.sha256(canon.encode()).hexdigest()])
        k = sign(sign(sign(sign(("AWS4" + SK).encode(), date), region), "s3"), "aws4_request")
        if hmac.new(k, to_sign.encode(), hashlib.sha256).hexdigest() != sig:
            return 403, "SignatureDoesNotMatch", "The request signature we calculated does not match the signature you provided. Check your key and signing method."
        return None

    def handle_any(self):
        if self.path.startswith("/__s3"):
            from urllib.parse import parse_qs, urlparse
            q = parse_qs(urlparse(self.path).query, keep_blank_values=True)
            with lock:
                for k in mode:
                    if k in q:
                        mode[k] = q[k][0] if k == "region" else int(q[k][0])
                out = json.dumps({"mode": mode, "objects": sorted(objects), "log": log[-30:]}).encode()
            return self.reply(200, out, "application/json")
        n = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(n) if n else b""
        bucket, key, style = self.target()
        head = self.command == "HEAD"
        anon = "Authorization" not in self.headers
        with lock:
            log.append(f"{self.command} {style} {key[:40]} {'anon' if anon else 'signed'} host={self.headers.get('Host')}")
        if bucket != BUCKET or mode["gone"]:
            return self.reply(404, err("NoSuchBucket", "The specified bucket does not exist"), head=head)
        if anon:
            if self.command == "GET" and mode["public"]:
                if key in objects:
                    return self.reply(200, objects[key], "application/octet-stream")
                return self.reply(404, err("NoSuchKey", "The specified key does not exist."))
            return self.reply(403, err("AccessDenied", "Access Denied"), head=head)
        bad = self.verify(body)
        if bad:
            return self.reply(bad[0], err(bad[1], bad[2]), head=head)
        if self.command == "PUT":
            objects[key] = body
            return self.reply(200, b"", extra={"ETag": '"' + hashlib.md5(body).hexdigest() + '"'})
        if self.command in ("GET", "HEAD"):
            if key in objects:
                if head:
                    self.send_response(200)
                    self.send_header("Content-Length", str(len(objects[key])))
                    self.end_headers()
                    return
                return self.reply(200, objects[key], "application/octet-stream")
            if head and mode["head403"]:
                return self.reply(403, b"", head=True)
            return self.reply(404, err("NoSuchKey", "The specified key does not exist."), head=head)
        if self.command == "DELETE":
            objects.pop(key, None)
            return self.reply(204)
        return self.reply(405, err("MethodNotAllowed", self.command))

    do_GET = do_PUT = do_HEAD = do_DELETE = do_POST = handle_any


srv = ThreadingHTTPServer(("0.0.0.0", PORT), H)
srv.daemon_threads = True
srv.serve_forever()
