"""模拟宝塔 Nginx / WAF 的反向代理：python3 proxy.py 监听端口 上游端口
GET /__proxy?limit=字节&waf=0|1&hang=次数&empty=次数 改行为（只影响上传分片 /admin/settings/backup/restore/<id>?offset=）：
  limit  单个请求体超过就回 413（像 client_max_body_size）
  waf    分片一律回「网站防火墙」网页（200）
  hang   接下来几个分片收完请求体后不回应（像卡住的 WAF），一直挂着
  empty  接下来几个分片把请求体丢掉再转给上游（服务器收到空分片）"""
import http.client
import json
import re
import sys
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LISTEN, UP = int(sys.argv[1]), int(sys.argv[2])
mode = {"limit": 0, "waf": 0, "hang": 0, "empty": 0}
log = []
lock = threading.Lock()
CHUNK = re.compile(r"^/admin/settings/backup/restore/[0-9a-f-]{36}\?offset=\d+$")
HOP = {"connection", "keep-alive", "transfer-encoding", "te", "trailer", "upgrade", "proxy-connection", "content-length"}


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def reply(self, status, body, ctype="text/html; charset=utf-8"):
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def handle_any(self):
        if self.path.startswith("/__proxy"):
            q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            with lock:
                for k in mode:
                    if k in q:
                        mode[k] = int(q[k][0])
                out = json.dumps({"mode": mode, "log": log[-40:]}).encode()
            return self.reply(200, out, "application/json")
        n = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(n) if n else None
        chunk = self.command == "POST" and CHUNK.match(self.path)
        if chunk:
            with lock:
                log.append({"t": round(time.time(), 1), "path": self.path.split("/")[-1], "len": n})
                limit, waf = mode["limit"], mode["waf"]
            if limit and n > limit:
                return self.reply(413, b"<html><head><title>413 Request Entity Too Large</title></head><body><center><h1>413 Request Entity Too Large</h1></center><hr><center>nginx</center></body></html>")
            if waf:
                return self.reply(200, "<html><head><title>网站防火墙</title></head><body><h1>网站防火墙</h1><p>您的请求带有不合法参数，已被网站管理员设置拦截！</p></body></html>".encode())
            with lock:
                hang = mode["hang"] > 0 and int(self.path.split("=")[-1]) > 0
                if hang:
                    mode["hang"] -= 1
                empty = not hang and mode["empty"] > 0 and int(self.path.split("=")[-1]) > 0
                if empty:
                    mode["empty"] -= 1
            if hang:
                time.sleep(600)
                return
            if empty:
                body = b""
        c = http.client.HTTPConnection("127.0.0.1", UP, timeout=300)
        headers = {k: v for k, v in self.headers.items() if k.lower() not in HOP}
        headers["Content-Length"] = str(len(body or b""))
        headers["X-Forwarded-For"] = "127.0.0.1"
        c.request(self.command, self.path, body=body, headers=headers)
        r = c.getresponse()
        data = r.read()
        self.send_response(r.status)
        for k, v in r.getheaders():
            if k.lower() not in HOP:
                self.send_header(k, v)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)
        c.close()

    do_GET = do_POST = do_HEAD = handle_any


srv = ThreadingHTTPServer(("0.0.0.0", LISTEN), H)
srv.daemon_threads = True
srv.serve_forever()
