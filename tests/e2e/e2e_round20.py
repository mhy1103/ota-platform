"""第二十轮：口令更新包。同一个检查更新地址，还在用内置版本的设备（输入口令激活的）拿单独设的口令更新包，
装过热更新的设备照常拿线上版本；没设时和以前完全一样，原来的接口参数、返回字段不变"""
import http.cookiejar
import io
import re
import threading
import uuid
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from lib import *

T = "/tmp/ota-e2e/r20"
os.makedirs(T, exist_ok=True)
HOOK_PORT = 18898
EMB = "0e0e0e0e-1111-4222-8333-444455556666"
got = []


class Hook(BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        try:
            got.append(json.loads(body))
        except ValueError:
            got.append({"raw": body.decode("utf-8", "replace")})
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")

    def log_message(self, *a):
        pass


hook = ThreadingHTTPServer(("127.0.0.1", HOOK_PORT), Hook)
threading.Thread(target=hook.serve_forever, daemon=True).start()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


class Admin:
    def __init__(self):
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())

    def send(self, method, path, body=None, headers=None):
        req = urllib.request.Request(BASE + path, data=body, method=method, headers=headers or {})
        try:
            with self.op.open(req, timeout=60) as r:
                return r.status, r.headers.get("Location"), r.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            return e.code, e.headers.get("Location"), e.read().decode("utf-8", "replace")

    def call(self, method, path, data=None):
        body = urllib.parse.urlencode(data or {}, doseq=True).encode() if method == "POST" else None
        return self.send(method, path, body)

    def json(self, path):
        return json.loads(self.send("GET", path, headers={"X-Requested-With": "XMLHttpRequest", "Accept": "application/json"})[2])

    def upload(self, root, fields):
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w") as z:
            for d, _, fs in os.walk(root):
                for f in fs:
                    z.write(os.path.join(d, f), os.path.relpath(os.path.join(d, f), root))
        b = "----e2e" + uuid.uuid4().hex
        out = io.BytesIO()
        for k, v in fields.items():
            out.write(f'--{b}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
        out.write(f'--{b}\r\nContent-Disposition: form-data; name="file"; filename="dist.zip"\r\nContent-Type: application/zip\r\n\r\n'.encode())
        out.write(buf.getvalue() + f"\r\n--{b}--\r\n".encode())
        st, _, body = self.send("POST", "/admin/upload", out.getvalue(), {
            "Content-Type": "multipart/form-data; boundary=" + b, "X-Requested-With": "XMLHttpRequest"})
        return json.loads(body) if st == 200 else {"status": st, "body": body[:300]}


def register(app, root, token, message=None):
    meta = json.load(open(f"{root}/metadata.json"))
    cfg = json.load(open(f"{root}/expoConfig.json"))
    files = {}
    for p, e in meta["fileMetadata"].items():
        files[e["bundle"]] = sha256(open(f"{root}/{e['bundle']}", "rb").read())
        for a in e["assets"]:
            files[a["path"]] = sha256(open(f"{root}/{a['path']}", "rb").read())
    missing = api("POST", f"/api/v1/apps/{app}/assets/missing", {"sha256": list(set(files.values()))}, token=token).json()["missing"]
    for p, s in files.items():
        if s in missing:
            api("PUT", f"/api/v1/apps/{app}/assets/{s}", raw=open(f"{root}/{p}", "rb").read(), token=token)
            missing.remove(s)
    return api("POST", f"/api/v1/apps/{app}/releases", {"runtimeVersion": cfg["runtimeVersion"], "message": message,
                                                        "metadata": meta, "expoConfig": cfg, "files": files}, token=token).json()


def deploy(release_id, token, rollout=100, **extra):
    return api("POST", "/api/v1/apps/act/channels/production/deploy", dict({"releaseId": release_id, "rolloutPercent": rollout}, **extra), token=token)


NONE = (None, "directive:noUpdateAvailable")


def pick(platform="ios", current=None, embedded=None, client=None, runtime="1.0.0"):
    h = {"expo-platform": platform, "expo-runtime-version": runtime, "expo-protocol-version": "1"}
    if client:
        h["eas-client-id"] = client
    if current:
        h["expo-current-update-id"] = current
    if embedded:
        h["expo-embedded-update-id"] = embedded
    parts = parse_multipart(plain("GET", "/m/act", headers=h))
    if "manifest" in parts:
        return json.loads(parts["manifest"][1])["id"]
    if "directive" in parts:
        return "directive:" + json.loads(parts["directive"][1])["type"]
    return None


def history(n=5):
    return api("GET", f"/api/v1/apps/act/history?limit={n}").json()


def deployment(runtime="1.0.0"):
    app = api("GET", "/api/v1/apps/act").json()
    ch = next(c for c in app["channels"] if c["name"] == "production")
    return next((d for d in ch["deployments"] if d["runtimeVersion"] == runtime), None)


def wait_event(name, rounds=20):
    for _ in range(rounds):
        ev = next((x for x in got if x.get("event") == name), None)
        if ev:
            return ev
        time.sleep(0.5)
    return None


OLD_DEPLOY_KEYS = {"runtimeVersion", "releaseId", "rolloutPercent", "previousReleaseId", "rollbackToEmbedded", "updatedBy", "updatedAt",
                   "channel", "republished"}

section("准备")
check("新建 App act", api("POST", "/api/v1/apps", {"slug": "act", "name": "口令更新包测试"}).status == 201)
token = api("POST", "/api/v1/apps/act/tokens", {"name": "CI"}).json()["token"]
for v in ("v1", "v2", "v3", "v5", "v6"):
    make_export(f"{T}/{v}", bundle_text="act " + v + " {p}")
make_export(f"{T}/v4", bundle_text="act v4 ios only {p}", platforms=("ios",))
make_export(f"{T}/v7", bundle_text="act v7 runtime 2 {p}", runtime="2.0.0")
v1 = register("act", f"{T}/v1", token, "v1")
deploy(v1["id"], token)
v2 = register("act", f"{T}/v2", token, "v2")
dep = deploy(v2["id"], token)
check("发布 v1、v2，线上是 v2", pick() == v2["id"])
check("发布接口不传 as：返回字段和以前完全一样", dep.status == 200 and set(dep.json()) == OLD_DEPLOY_KEYS, sorted(dep.json()))
adm = Admin()
adm.call("GET", "/login")
st, loc, _ = adm.call("POST", "/login", {"username": "admin", "password": "admin-pass-123"})
check("管理员登录后台", st == 302 and loc and "/admin" in loc, loc)
adm.call("GET", "/admin/use?app=act")
check("配置 Webhook", adm.call("POST", "/admin/apps/webhook", {"url": f"http://127.0.0.1:{HOOK_PORT}/hook"})[0] == 302)

section("没设口令更新包：和以前一样")
check("新装的设备（没有当前版本）拿线上版本 v2", pick() == v2["id"])
check("还在内置版本的设备（当前版本 = 内置版本）拿 v2", pick(current=EMB, embedded=EMB) == v2["id"])
check("在 v1 的设备拿 v2", pick(current=v1["id"], embedded=EMB) == v2["id"])
check("已经在 v2 的设备没有更新", pick(current=v2["id"], embedded=EMB) in NONE)
check("App 详情里渠道的 deployments 不多带字段", "activationReleaseId" not in deployment(), deployment())

section("管理接口设口令更新包（as: activation）")
r = api("POST", "/api/v1/apps/act/channels/production/deploy", {"releaseId": v1["id"], "as": "activation"}, token=token)
j = r.json()
check("返回 200，as=activation、changed=true", r.status == 200 and j.get("as") == "activation" and j.get("changed") is True, j)
check("返回里口令更新包是 v1，热更新包还是 v2", j.get("activationReleaseId") == v1["id"] and j.get("releaseId") == v2["id"], j)
check("新装的设备拿口令更新包 v1", pick() == v1["id"])
check("还在内置版本的 iOS 设备拿 v1", pick(current=EMB, embedded=EMB) == v1["id"])
check("还在内置版本的 Android 设备拿 v1", pick(platform="android", current=EMB, embedded=EMB) == v1["id"])
check("已经装过热更新（在 v2）的设备不受影响：没有更新", pick(current=v2["id"], embedded=EMB) in NONE)
check("激活后（在 v1）下次检查按热更新走：拿 v2", pick(current=v1["id"], embedded=EMB) == v2["id"])
r2 = api("POST", "/api/v1/apps/act/channels/production/deploy", {"releaseId": v1["id"], "as": "activation"}, token=token).json()
h = history()
check("再设一次同一个版本：changed=false，不重复记发布记录", r2.get("changed") is False and [x["action"] for x in h].count("activation") == 1, (r2, [x["action"] for x in h]))
check("发布记录 action=activation、版本 v1、操作人是令牌名", h[0]["action"] == "activation" and h[0]["releaseId"] == v1["id"] and h[0]["by"] == "CI", h[0])
check("App 详情里渠道多带 activationReleaseId", deployment().get("activationReleaseId") == v1["id"], deployment())
ev = wait_event("release.activation_deployed")
check("Webhook 发 release.activation_deployed，带渠道和版本",
      ev is not None and ev["data"].get("channel") == "production" and ev["data"].get("release", {}).get("id") == v1["id"], ev)

section("灰度、回退内置版本不影响口令更新包")
v3 = register("act", f"{T}/v3", token, "v3")
deploy(v3["id"], token, rollout=10)
outs = {pick(current=v2["id"], embedded=EMB, client=f"r-{i}") for i in range(120)}
check("热更新灰度 10%：装过热更新的设备有的拿 v3、有的不更新", v3["id"] in outs and bool(outs & set(NONE)) and outs <= {v3["id"], *NONE}, outs)
check("新装的设备不看灰度，全拿口令更新包 v1", {pick(client=f"f-{i}") for i in range(40)} == {v1["id"]})
api("POST", "/api/v1/apps/act/channels/production/rollback-embedded", {"runtimeVersion": "1.0.0"}, token=token)
check("回退内置版本：装过热更新的设备收到回退指令", pick(current=v3["id"], embedded=EMB, client="r-1") == "directive:rollBackToEmbedded")
check("回退内置版本：输入口令激活的设备照样拿 v1", pick() == v1["id"] and pick(current=EMB, embedded=EMB) == v1["id"])
deploy(v3["id"], token)
hot = deployment()["releaseId"]
check("恢复发布 v3（全量）：装过热更新的设备拿热更新包", pick(current=v2["id"], embedded=EMB) == hot)

section("口令更新包没有这个平台的文件")
v4 = register("act", f"{T}/v4", token, "v4 只有 iOS")
api("POST", "/api/v1/apps/act/channels/production/deploy", {"releaseId": v4["id"], "as": "activation"}, token=token)
check("iOS 新装设备拿口令更新包 v4", pick() == v4["id"])
check("Android 新装设备照旧拿线上版本（不会激活失败）", pick(platform="android") == hot)

section("模拟检查更新")
dg = api("GET", "/api/v1/apps/act/diagnose?device=new-phone&platform=ios&runtime=1.0.0&channel=production").json()
check("新装的 iOS 设备：会收到口令更新包，步骤里写明", v4["id"][:8] in dg.get("verdict", "") and "口令更新包" in json.dumps(dg, ensure_ascii=False), dg)
dg = api("GET", "/api/v1/apps/act/diagnose?device=new-phone&platform=android&runtime=1.0.0&channel=production").json()
check("新装的 Android 设备：口令更新包没有 Android 文件，拿线上版本", hot[:8] in dg.get("verdict", "") and "没有 Android 的文件" in json.dumps(dg, ensure_ascii=False), dg)
pick(current=hot, embedded=EMB, client="hot-phone")
txt = ""
for _ in range(30):
    time.sleep(0.5)
    dg = api("GET", "/api/v1/apps/act/diagnose?device=hot-phone&platform=ios&runtime=1.0.0&channel=production").json()
    txt = json.dumps(dg, ensure_ascii=False)
    if "已经激活过" in txt:
        break
check("已激活的设备：写明口令更新包只给新装 App 激活时用，它拿常规更新包", "已经激活过" in txt and "常规更新包" in txt and dg.get("verdict") == "已经是最新版本", dg)

section("删除和取消")
r = api("DELETE", f"/api/v1/apps/act/releases/{v4['id']}")
check("口令更新包不能删除，写明原因", r.status == 400 and "口令更新包" in r.text, (r.status, r.text[:200]))
r = api("POST", "/api/v1/apps/act/channels/production/activation/clear", {"runtimeVersion": "1.0.0"}, token=token)
check("取消口令更新包 → 200，返回里不再带 activationReleaseId", r.status == 200 and "activationReleaseId" not in r.json(), r.json())
check("取消后新装的设备改回拿线上版本", pick() == hot and pick(current=EMB, embedded=EMB) == hot)
h = history()
check("发布记录 action=activation_off", h[0]["action"] == "activation_off" and h[0].get("releaseId") is None, h[0])
ev = wait_event("release.activation_cleared")
check("Webhook 发 release.activation_cleared", ev is not None and ev["data"].get("runtimeVersion") == "1.0.0", ev)
r = api("POST", "/api/v1/apps/act/channels/production/activation/clear", {"runtimeVersion": "1.0.0"}, token=token)
check("没有口令更新包时再取消 → 400", r.status == 400 and "没有单独的口令更新包" in r.text, (r.status, r.text[:200]))
check("取消后 v4 可以删除了", api("DELETE", f"/api/v1/apps/act/releases/{v4['id']}").status == 200)

section("参数校验")
r = api("POST", "/api/v1/apps/act/channels/production/deploy", {"releaseId": v1["id"], "as": "weird"}, token=token)
check("as 不认识 → 400", r.status == 400, (r.status, r.text[:200]))
r = api("POST", "/api/v1/apps/act/channels/production/deploy", {"releaseId": "nope", "as": "activation"}, token=token)
check("版本不存在 → 404", r.status == 404, r.status)
r = api("POST", "/api/v1/apps/act/channels/production/deploy", {"releaseId": hot, "rolloutPercent": 100, "as": "hot"}, token=token)
check("as=hot 就是平常的发布，返回字段和以前一样", r.status == 200 and set(r.json()) == OLD_DEPLOY_KEYS, (r.status, r.text[:300]))

section("后台操作")
st, loc, _ = adm.call("POST", "/admin/deploy", {"releaseId": v2["id"], "channel": "production", "as": "activation", "rollout": "100"})
check("发布弹窗选「口令更新」→ 设为口令更新包", st == 302 and pick() == v2["id"], (st, loc))
page = adm.call("GET", "/admin/releases")[2]
check("跳回的页面提示已设为口令更新包", "设为 production 的口令更新包" in page)
check("页面数据：版本列表里 v2 标着 production 口令更新包", re.search(r'"activationIn":\["production"\]', page) is not None)
d = adm.json(f"/admin/release/{v2['id']}")
check("版本详情：activationIn 是 production", d.get("activationIn") == ["production"], d.get("activationIn"))
st, _, _ = adm.call("POST", "/admin/delete", {"releaseId": v2["id"]})
check("后台删除口令更新包被拦下", st == 302 and pick() == v2["id"] and adm.json(f"/admin/release/{v2['id']}").get("id") == v2["id"])
st, _, _ = adm.call("POST", "/admin/activation/clear", {"channel": "production", "runtime": "1.0.0"})
check("线上版本那一行「取消」→ 新装设备改回拿线上版本", st == 302 and pick() == hot)
res = adm.upload(f"{T}/v5", {"note": "v5 激活包", "runtime": "", "activate": "true", "channel": "production", "as": "activation"})
check("上传时选「作为口令更新包」", res.get("ok") is True and "口令更新包" in res.get("msg", ""), res)
v5 = deployment().get("activationReleaseId")
check("上传的版本成了口令更新包，热更新包不变", v5 not in (None, hot) and pick() == v5 and deployment()["releaseId"] == hot, deployment())
res = adm.upload(f"{T}/v6", {"note": "v6", "runtime": "", "activate": "true", "channel": "production"})
v6 = deployment()["releaseId"]
check("上传时不选（默认热更新）：和以前一样发成线上版本", res.get("ok") is True and "并已发布到 production" in res.get("msg", "") and v6 != hot, res)
check("口令更新包不受影响，还是 v5；装过热更新的设备拿 v6", pick() == v5 and pick(current=hot, embedded=EMB) == v6)
logs = adm.call("GET", "/admin/logs")[2]
check("操作日志记了设、取消口令更新包", "设口令更新包" in logs and "取消口令更新包" in logs)

section("只有口令更新包、没有线上版本的 Runtime")
v7 = register("act", f"{T}/v7", token, "v7 runtime 2")
api("POST", "/api/v1/apps/act/channels/production/deploy", {"releaseId": v7["id"], "as": "activation"}, token=token)
check("Runtime 2.0.0 新装的设备拿口令更新包", pick(runtime="2.0.0") == v7["id"] and pick(runtime="2.0.0", current=EMB, embedded=EMB) == v7["id"])
check("Runtime 2.0.0 装过热更新的设备没有更新", pick(runtime="2.0.0", current="99999999-0000-4000-8000-000000000000", embedded=EMB) in NONE)
dg = api("GET", "/api/v1/apps/act/diagnose?device=hot-phone&platform=ios&runtime=2.0.0&channel=production").json()
check("模拟检查更新正常", dg.get("verdict") in ("没有更新", "会收到版本 " + v7["id"][:8]), dg.get("verdict"))
bad = [p for p in ("/admin", "/admin/releases", "/admin/devices", "/admin/apps", "/admin/screen", "/admin/logs") if adm.call("GET", p)[0] >= 500]
check("后台各页都正常打开", not bad, bad)
check("App 详情接口正常", api("GET", "/api/v1/apps/act").status == 200)

hook.shutdown()
done()
