"""模拟慢的静态文件服务器（像一台带宽小的 nginx）：python3 slowcdn.py 端口 目录
每个连接限速 RATE 字节/秒、首字节前等 DELAY 秒；支持 HTTP/1.1 长连接。GET /__cdn 看新建了多少个连接"""
import os
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT, ROOT = int(sys.argv[1]), sys.argv[2]
RATE = int(os.environ.get("RATE", "40000"))
DELAY = float(os.environ.get("DELAY", "0.4"))
conns = set()
lock = threading.Lock()


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def do_GET(self):
        with lock:
            conns.add(self.client_address)
        if self.path.startswith("/__cdn"):
            body = f'{{"connections": {len(conns)}}}'.encode()
            self.send_response(200)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        p = os.path.normpath(os.path.join(ROOT, self.path.split("?")[0].lstrip("/")))
        if not p.startswith(ROOT) or not os.path.isfile(p):
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        data = open(p, "rb").read()
        time.sleep(DELAY)
        self.send_response(200)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        step = 8192
        for i in range(0, len(data), step):
            self.wfile.write(data[i:i + step])
            time.sleep(step / RATE)


ThreadingHTTPServer.request_queue_size = 128
srv = ThreadingHTTPServer(("0.0.0.0", PORT), H)
srv.daemon_threads = True
srv.serve_forever()
