"""像 App 一样请求更新清单，看下载地址最终落在本机还是对象存储：python3 prod_manifest_check.py 地址 运行时版本,逗号分隔 App标识
不带 eas-client-id、Expo-Requested-Update-ID，不计入设备统计和下载统计；只用 HEAD 追踪下载地址，不下载文件内容"""
import collections
import email
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "https://app.td866181.com:8813"
RUNTIMES = sys.argv[2].split(",") if len(sys.argv) > 2 else ["1.0.0"]
if len(sys.argv) < 4:
    sys.exit("用法：python3 prod_manifest_check.py 地址 运行时版本,逗号分隔 App标识")
APP = sys.argv[3]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


op = urllib.request.build_opener(NoRedirect())


def call(method, url, headers=None):
    req = urllib.request.Request(url, method=method, headers=headers or {})
    try:
        with op.open(req, timeout=30) as r:
            return r.status, r.headers, (b"" if method == "HEAD" else r.read())
    except urllib.error.HTTPError as e:
        return e.code, e.headers, e.read()
    except Exception as e:
        return 0, {}, str(e).encode()


def manifest(platform, runtime, updated):
    h = {"expo-platform": platform, "expo-runtime-version": runtime, "expo-protocol-version": "1", "accept": "multipart/mixed"}
    if updated:
        h["expo-current-update-id"] = str(uuid.uuid4())
        h["expo-embedded-update-id"] = str(uuid.uuid4())
    st, hd, body = call("GET", BASE + "/m/" + urllib.parse.quote(APP), h)
    if st != 200:
        return st, None
    msg = email.message_from_bytes(b"Content-Type: " + hd.get("Content-Type", "").encode() + b"\r\n\r\n" + body)
    for part in msg.walk():
        disp = part.get("Content-Disposition") or ""
        if 'name="manifest"' in disp:
            return st, json.loads(part.get_payload(decode=True))
        if 'name="directive"' in disp:
            return st, {"directive": json.loads(part.get_payload(decode=True))}
    return st, None


def where(netloc, server, cos):
    base = urllib.parse.urlsplit(BASE).netloc
    if cos or netloc.endswith(".myqcloud.com") or "tencent-cos" in server.lower():
        return "腾讯云 COS"
    if netloc.endswith(".aliyuncs.com") or server.lower() == "aliyunoss":
        return "阿里云 OSS"
    if netloc == base:
        return "本服务器"
    return "其他服务器"


def trace(url):
    hops = []
    for _ in range(6):
        st, hd, _ = call("HEAD", url)
        keys = {k.lower() for k in hd.keys()} if hd else set()
        server = hd.get("Server", "") if hd else ""
        u = urllib.parse.urlsplit(url)
        hops.append(f"{st} {where(u.netloc, server, 'x-cos-request-id' in keys)}（{u.netloc}{u.path[:48]}{'…' if len(u.path) > 48 else ''}）"
                    + (f" 长度 {hd.get('Content-Length')}" if hd and hd.get("Content-Length") and st == 200 else ""))
        loc = hd.get("Location") if hd else None
        if st in (301, 302, 303, 307, 308) and loc:
            url = urllib.parse.urljoin(url, loc)
            continue
        break
    return hops


for runtime in RUNTIMES:
    for platform in ("ios", "android"):
        for updated, who in ((False, "新装 / 还在内置版本的设备"), (True, "已经更新过的设备")):
            st, m = manifest(platform, runtime, updated)
            print(f"\n== {platform} · 运行时 {runtime} · {who} ==")
            if m is None:
                print(f"  清单请求返回 {st}")
                continue
            if "directive" in m:
                print(f"  没有更新：{m['directive']}")
                continue
            ota = (m.get("extra") or {}).get("ota") or {}
            print(f"  版本 {m['id'][:8]}（{ota.get('note', '')}）创建于 {m['createdAt']}")
            launch = m["launchAsset"]["url"]
            assets = [a["url"] for a in m.get("assets", [])]
            hosts = collections.Counter(urllib.parse.urlsplit(u).netloc for u in assets)
            print(f"  启动包地址：{launch}")
            print(f"  其他 {len(assets)} 个文件的地址所在：" + ("、".join(f"{h} × {n}" for h, n in hosts.items()) or "无"))
            print("  启动包追踪：" + " → ".join(trace(launch)))
            for u in assets[:2]:
                print("  文件追踪：" + " → ".join(trace(u)))
