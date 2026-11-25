"""e2e 测试的公共函数：服务地址和管理令牌从环境变量 OTA_BASE / OTA_ADMIN_TOKEN 读，check / section / done 统计结果"""
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("OTA_BASE", "http://127.0.0.1:18830").rstrip("/")
TOKEN = os.environ.get("OTA_ADMIN_TOKEN", "test-admin-token")
_passed = 0
_failed = []


def section(name):
    print(f"== {name} ==", flush=True)


def check(name, ok, detail=None):
    global _passed
    if ok:
        _passed += 1
        print(f"  ✓ {name}", flush=True)
    else:
        _failed.append(name)
        extra = ("  → " + repr(detail)[:800]) if detail is not None else ""
        print(f"  ✗ {name}{extra}", flush=True)


def done():
    print()
    if _failed:
        print(f"通过 {_passed} 项，失败 {len(_failed)} 项：" + "；".join(_failed))
        sys.exit(1)
    print(f"通过 {_passed} 项，全部通过")


def sha256(b):
    return hashlib.sha256(b).hexdigest()


class Resp:
    def __init__(self, status, headers, body):
        self.status = status
        self.headers = headers
        self.body = body

    def header(self, name):
        return self.headers.get(name) if self.headers is not None else None

    def json(self):
        return json.loads(self.body.decode("utf-8")) if self.body else None

    @property
    def text(self):
        return self.body.decode("utf-8", "replace")


def _open(req):
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return Resp(r.status, r.headers, r.read())
    except urllib.error.HTTPError as e:
        return Resp(e.code, e.headers, e.read())


def api(method, path, body=None, token=None, raw=None, headers=None):
    h = {"Authorization": "Bearer " + (token or TOKEN)}
    data = None
    if raw is not None:
        data = raw
        h["Content-Type"] = "application/octet-stream"
    elif body is not None:
        data = json.dumps(body).encode()
        h["Content-Type"] = "application/json"
    if headers:
        h.update(headers)
    return _open(urllib.request.Request(BASE + path, data=data, method=method, headers=h))


def plain(method, path, headers=None, data=None):
    return _open(urllib.request.Request(BASE + path, data=data, method=method, headers=headers or {}))


def parse_multipart(resp):
    """清单响应拆成 {名字: (头, 内容)}；不是 multipart（204 等）时返回空"""
    ct = resp.header("Content-Type") or ""
    if resp.status != 200 or "multipart" not in ct:
        return {}
    boundary = re.search(r'boundary="?([^";]+)"?', ct).group(1).encode()
    out = {}
    for chunk in resp.body.split(b"--" + boundary):
        chunk = chunk.strip(b"\r\n")
        if not chunk or chunk == b"--":
            continue
        head, _, body = chunk.partition(b"\r\n\r\n")
        hs = {}
        for line in head.decode("utf-8", "replace").split("\r\n"):
            if ":" in line:
                k, v = line.split(":", 1)
                hs[k.strip().lower()] = v.strip()
        m = re.search(r'name="([^"]+)"', hs.get("content-disposition", ""))
        if m:
            out[m.group(1)] = (hs, body.decode("utf-8", "replace"))
    return out


def make_export(root, bundle_text="bundle {p}", runtime="1.0.0", platforms=("ios", "android"), asset=b"PNG-shared-asset"):
    """造一个 npx expo export 的目录：每个平台一个启动包，共用一个资源文件"""
    os.makedirs(f"{root}/assets", exist_ok=True)
    a_sha = sha256(asset)
    with open(f"{root}/assets/{a_sha}", "wb") as f:
        f.write(asset)
    meta = {"version": 0, "bundler": "metro", "fileMetadata": {}}
    for p in platforms:
        content = bundle_text.format(p=p).encode()
        rel = f"_expo/static/js/{p}/index-{sha256(content)[:16]}.hbc"
        os.makedirs(os.path.dirname(f"{root}/{rel}"), exist_ok=True)
        with open(f"{root}/{rel}", "wb") as f:
            f.write(content)
        meta["fileMetadata"][p] = {"bundle": rel, "assets": [{"path": f"assets/{a_sha}", "ext": "png"}]}
    with open(f"{root}/metadata.json", "w") as f:
        json.dump(meta, f)
    with open(f"{root}/expoConfig.json", "w") as f:
        json.dump({"name": "e2e", "slug": "e2e", "version": "1.0.0", "runtimeVersion": runtime}, f)
