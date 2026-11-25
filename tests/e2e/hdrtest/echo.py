"""打印收到的请求头：python3 echo.py 端口"""
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class H(BaseHTTPRequestHandler):
    def do_GET(self):
        got = {k: v for k, v in self.headers.items() if k.lower().startswith(("expo-", "x-"))}
        print(self.path, got, flush=True)
        body = repr(got).encode()
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *a):
        pass


ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), H).serve_forever()
