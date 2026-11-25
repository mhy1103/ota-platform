"""外部链接：填好地址就在后台读，页面轮询进度；读完再点添加马上好。
慢链接用 slowcdn.py 模拟（每个连接限速），dist 和 npx expo export 一样：资源和启动包按内容 MD5 命名"""
import hashlib
import http.cookiejar
import random
import shutil
import sys

from lib import *

CDN = os.environ.get("CDN_BASE", "http://127.0.0.1:18871")
ROOT = "/tmp/ota-e2e/cdn"


def make_dist(name, bundle_kb=600, assets=20, missing=False):
    d = f"{ROOT}/{name}"
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(f"{d}/assets")
    rnd = random.Random(name)
    meta = {"version": 0, "bundler": "metro", "fileMetadata": {}}
    paths = []
    for i in range(assets):
        data = bytes(rnd.getrandbits(8) for _ in range(rnd.randint(10, 60) * 1024))
        md5 = hashlib.md5(data).hexdigest()
        open(f"{d}/assets/{md5}", "wb").write(data)
        paths.append({"path": f"assets/{md5}", "ext": "png"})
    if missing:
        paths.append({"path": "assets/" + "0" * 32, "ext": "png"})
    for plat in ("ios", "android"):
        data = bytes(rnd.getrandbits(8) for _ in range(bundle_kb * 1024 if plat == "ios" else bundle_kb * 800))
        rel = f"_expo/static/js/{plat}/index-{hashlib.md5(data).hexdigest()}.hbc"
        os.makedirs(os.path.dirname(f"{d}/{rel}"), exist_ok=True)
        open(f"{d}/{rel}", "wb").write(data)
        meta["fileMetadata"][plat] = {"bundle": rel, "assets": paths}
    json.dump(meta, open(f"{d}/metadata.json", "w"))
    json.dump({"name": "link", "slug": "link", "version": "1.0.0", "runtimeVersion": "1.0.0"}, open(f"{d}/expoConfig.json", "w"))
    return f"{CDN}/{name}/"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


class Admin:
    def __init__(self):
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())

    def send(self, method, path, body=None, headers=None):
        req = urllib.request.Request(BASE + path, data=body, method=method, headers=headers or {})
        try:
            with self.op.open(req, timeout=300) as r:
                return r.status, r.headers, r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read()

    def ajax(self, path, data=None):
        st, hd, body = self.send("POST", path, urllib.parse.urlencode(data or {}).encode(),
                                 {"Content-Type": "application/x-www-form-urlencoded", "X-Requested-With": "XMLHttpRequest"})
        try:
            return json.loads(body)
        except ValueError:
            return {"status": st, "raw": body[:300]}

    def get_json(self, path):
        st, hd, body = self.send("GET", path, headers={"X-Requested-With": "XMLHttpRequest"})
        return json.loads(body)


def cdn_conns():
    with urllib.request.urlopen(CDN + "/__cdn", timeout=5) as r:
        return json.loads(r.read())["connections"]


def wait_read(adm, rid, snaps=None, limit=180):
    t0 = time.time()
    while time.time() - t0 < limit:
        s = adm.get_json(f"/admin/addUrl/read/{rid}")
        if snaps is not None:
            snaps.append(s)
        if not s.get("ok") or s.get("state") != "reading":
            return s
        time.sleep(0.4)
    return {"state": "timeout"}


if "--setup" in sys.argv:
    s0 = json.loads(urllib.request.urlopen(BASE + "/install/status").read())
    if not s0["installed"]:
        req = urllib.request.Request(BASE + "/install/submit", method="POST", headers={"Content-Type": "application/json"}, data=json.dumps({
            "db": {"type": "h2"}, "dataMode": "keep", "restrictDomains": False, "domains": [], "publicUrl": BASE,
            "displayZone": "Asia/Shanghai", "storage": {"type": "local"}, "adminUsername": "admin",
            "adminName": "管理员", "adminPassword": "admin-pass-123", "adminPassword2": "admin-pass-123"}).encode())
        urllib.request.urlopen(req, timeout=30).read()
        for _ in range(120):
            try:
                s = json.loads(urllib.request.urlopen(BASE + "/install/status").read())
                if s["installed"] and s["boot"] != s0["boot"]:
                    break
            except Exception:
                pass
            time.sleep(0.3)
    print("app", api("POST", "/api/v1/apps", {"slug": "link", "name": "外部链接测试"}).status)
    for n, kw in (("d1", {}), ("d2", {"missing": True}), ("d3", {"bundle_kb": 300})):
        print(make_dist(n, **kw))
    sys.exit(0)

adm = Admin()
adm.send("GET", "/login")
adm.send("POST", "/login", urllib.parse.urlencode({"username": "admin", "password": "admin-pass-123"}).encode(),
         {"Content-Type": "application/x-www-form-urlencoded"})
adm.send("GET", "/admin/use?app=link")
D1, D2, D3 = f"{CDN}/d1/", f"{CDN}/d2/", f"{CDN}/d3/"

section("填好地址就在后台读，能看到进度")
c0 = cdn_conns()
r = adm.ajax("/admin/addUrl/read", {"externalUrl": D1})
check("开始读，马上返回", r.get("ok") and r.get("id"), r)
snaps = []
t0 = time.time()
s = wait_read(adm, r["id"], snaps)
secs = time.time() - t0
check("读完了", s.get("state") == "done" and s.get("files") == 22, s)
mid = [x for x in snaps if x.get("state") == "reading" and x.get("total")]
check("读的过程中能看到读了几个、多少字节", any(0 < x["done"] < x["total"] and x["bytes"] > 0 for x in mid), [(x.get("done"), x.get("bytes")) for x in mid[:5]])
check("能看到正在下载的文件和它的进度", any(x.get("reading") and x["reading"][0]["size"] > 0 for x in mid), mid[:2])
check("同一个地址再点一次：接着用这次的结果，不重新读", adm.ajax("/admin/addUrl/read", {"externalUrl": D1})["id"] == r["id"])
conns = cdn_conns() - c0
check("连接复用：22 个文件没有每个都新建连接", conns <= 12, conns)
print(f"    慢链接读完用时 {secs:.1f} 秒，新建连接 {conns} 个")

section("读完再点添加：马上好")
t0 = time.time()
a = adm.ajax("/admin/addUrl", {"externalUrl": D1, "read": r["id"], "note": "先读好的", "activate": "true", "channel": "production"})
check("添加成功", a.get("ok") and "已添加外部链接版本" in a.get("msg", ""), a)
check("添加只用了几秒（只核对 metadata.json 有没有变）", time.time() - t0 < 4, round(time.time() - t0, 1))
check("用过的读取结果不能再用", adm.get_json(f"/admin/addUrl/read/{r['id']}").get("ok") is False)

section("重新读同一个链接：读过的文件不再下载")
r2 = adm.ajax("/admin/addUrl/read", {"externalUrl": D1})
t0 = time.time()
s2 = wait_read(adm, r2["id"])
check("第二次读很快（文件名带 MD5 的直接用上次算的）", s2.get("state") == "done" and time.time() - t0 < secs / 2, round(time.time() - t0, 1))
adm.ajax(f"/admin/addUrl/read/{r2['id']}/cancel")

section("换了地址：之前没读完的停掉")
ra = adm.ajax("/admin/addUrl/read", {"externalUrl": D3})
time.sleep(1.5)
rb = adm.ajax("/admin/addUrl/read", {"externalUrl": D2})
sa = wait_read(adm, ra["id"], limit=30)
check("之前那个停了", sa.get("state") == "failed" and "取消" in sa.get("msg", ""), sa)

section("有文件下不了：说清楚是哪个")
sb = wait_read(adm, rb["id"])
check("读失败，列出下不了的文件", sb.get("state") == "failed" and "0000000000" in sb.get("msg", "") and "404" in sb.get("msg", ""), sb)

section("读完以后链接里的 dist 换了：重新读")
r3 = adm.ajax("/admin/addUrl/read", {"externalUrl": D3})
s3 = wait_read(adm, r3["id"])
check("先读完", s3.get("state") == "done", s3)
meta = json.load(open(f"{ROOT}/d3/metadata.json"))
meta["bundler"] = "metro-changed"
json.dump(meta, open(f"{ROOT}/d3/metadata.json", "w"))
a3 = adm.ajax("/admin/addUrl", {"externalUrl": D3, "read": r3["id"]})
check("添加时发现变了：不用旧结果，让页面重新读", a3.get("ok") is False and a3.get("reread") is True, a3)

section("原来的用法照旧")
a4 = adm.ajax("/admin/addUrl", {"externalUrl": D3, "note": "不先读"})
check("不带 read：在这次请求里读完再添加（和以前一样）", a4.get("ok") is True, a4)
st, hd, body = adm.send("POST", "/admin/addUrl", urllib.parse.urlencode({"externalUrl": D3}).encode(), {"Content-Type": "application/x-www-form-urlencoded"})
check("表单提交：照旧跳回版本页", st == 302 and (hd.get("Location") or "").endswith("/admin/releases"), (st, hd.get("Location")))
r5 = adm.ajax("/admin/addUrl/read", {"externalUrl": "https://example.com/dist.zip"})
check("填的是压缩包：马上提示", r5.get("ok") is False and "压缩包" in r5.get("msg", ""), r5)
done()
