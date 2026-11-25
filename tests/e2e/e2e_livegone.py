"""线上版本：这个 Runtime 已经没有更新包（常规更新包、口令更新包都没有）时，线上版本里不再列这一行。
回退内置版本以后删掉最后一个更新包：页面上不列，但还在回退状态的设备照样收到回退指令（防止还没联网的设备留在删掉的版本上）"""
import http.cookiejar
import sys

from lib import *

APP = os.environ.get("APP", "gone")


def register(app, root, token):
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
    return api("POST", f"/api/v1/apps/{app}/releases", {"runtimeVersion": cfg["runtimeVersion"], "metadata": meta,
                                                        "expoConfig": cfg, "files": files}, token=token).json()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())


def page(path):
    req = urllib.request.Request(BASE + path, headers={"X-Requested-With": "XMLHttpRequest", "Accept": "application/json"})
    with op.open(req, timeout=30) as r:
        return json.loads(r.read())["model"]


def live_rows():
    out = []
    for l in page("/admin/releases").get("live") or []:
        rel = l.get("release")
        out.append((l.get("channel"), l.get("runtime"), rel.get("id")[:8] if rel else None, l.get("rollbackToEmbedded"),
                    (l.get("activation") or {}).get("id", "")[:8] or None))
    return out


def pick(runtime, current=None):
    h = {"expo-platform": "ios", "expo-runtime-version": runtime, "expo-protocol-version": "1"}
    if current:
        h["expo-current-update-id"] = current
        h["expo-embedded-update-id"] = "0e0e0e0e-1111-4222-8333-444455556666"
    parts = parse_multipart(plain("GET", f"/m/{APP}", headers=h))
    if "manifest" in parts:
        return "manifest:" + json.loads(parts["manifest"][1])["id"][:8]
    if "directive" in parts:
        return "directive:" + json.loads(parts["directive"][1])["type"]
    return None


section("准备")
check("新建 App", api("POST", "/api/v1/apps", {"slug": APP, "name": "线上版本清理"}).status == 201)
tok = api("POST", f"/api/v1/apps/{APP}/tokens", {"name": "CI"}).json()["token"]
make_export(f"/tmp/ota-e2e/{APP}/r1", bundle_text=APP + " r1 {p}", runtime="1.0.0")
make_export(f"/tmp/ota-e2e/{APP}/r2", bundle_text=APP + " r2 {p}", runtime="2.0.0")
make_export(f"/tmp/ota-e2e/{APP}/r3", bundle_text=APP + " r3 {p}", runtime="3.0.0")
a1 = register(APP, f"/tmp/ota-e2e/{APP}/r1", tok)
a2 = register(APP, f"/tmp/ota-e2e/{APP}/r2", tok)
a3 = register(APP, f"/tmp/ota-e2e/{APP}/r3", tok)
dep = lambda rid, **kw: api("POST", f"/api/v1/apps/{APP}/channels/production/deploy", dict({"releaseId": rid, "rolloutPercent": 100}, **kw), token=tok)
check("Runtime 1.0.0、2.0.0 发布常规更新，3.0.0 只设口令更新包", dep(a1["id"]).status == 200 and dep(a2["id"]).status == 200
      and dep(a3["id"], **{"as": "activation"}).status == 200)
def visit(req):
    try:
        op.open(req, timeout=30).read()
    except urllib.error.HTTPError as e:
        if e.code // 100 != 3:
            raise


visit(BASE + "/login")
visit(urllib.request.Request(BASE + "/login", data=urllib.parse.urlencode({"username": "admin", "password": "admin-pass-123"}).encode()))
visit(BASE + f"/admin/use?app={APP}")
rows = live_rows()
check("线上版本里有三行", sorted(r[1] for r in rows) == ["1.0.0", "2.0.0", "3.0.0"], rows)

section("回退内置版本后删掉 2.0.0 的更新包")
check("回退内置版本", api("POST", f"/api/v1/apps/{APP}/channels/production/rollback-embedded", {"runtimeVersion": "2.0.0"}, token=tok).status == 200)
check("删掉 2.0.0 唯一的更新包", api("DELETE", f"/api/v1/apps/{APP}/releases/{a2['id']}", token=tok).status in (200, 204))
rows = live_rows()
check("线上版本里不再列 2.0.0", "2.0.0" not in [r[1] for r in rows], rows)
check("装着删掉那个版本的设备照样收到回退内置版本的指令", pick("2.0.0", a2["id"]) == "directive:rollBackToEmbedded", pick("2.0.0", a2["id"]))

section("3.0.0 取消口令更新包以后什么都没了")
check("取消口令更新包", api("POST", f"/api/v1/apps/{APP}/channels/production/activation/clear", {"runtimeVersion": "3.0.0"}, token=tok).status == 200)
rows = live_rows()
check("线上版本里不再列 3.0.0", "3.0.0" not in [r[1] for r in rows], rows)
check("3.0.0 的设备没有更新", pick("3.0.0") in (None, "directive:noUpdateAvailable"), pick("3.0.0"))

section("别的照旧")
check("1.0.0 还在，线上是 r1", [r for r in rows if r[1] == "1.0.0" and r[2] == a1["id"][:8]], rows)
b2 = register(APP, f"/tmp/ota-e2e/{APP}/r2", tok)
check("2.0.0 重新上传、发布：又出现在线上版本里", dep(b2["id"]).status == 200 and "2.0.0" in [r[1] for r in live_rows()], live_rows())
check("2.0.0 的设备拿到新发布的版本", pick("2.0.0") == "manifest:" + b2["id"][:8], pick("2.0.0"))
done()
