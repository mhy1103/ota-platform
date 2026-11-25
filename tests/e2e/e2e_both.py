"""发布：同一份内容（同一个 dist 传了两次）不复制；发布弹窗能同时选常规更新和口令更新；发布前的预览和实际结果一致"""
import http.cookiejar

from lib import *

APP = os.environ.get("APP", "both")


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


def send(method, path, data=None, ajax=False):
    body = urllib.parse.urlencode(data, doseq=True).encode() if data is not None else None
    h = {"Content-Type": "application/x-www-form-urlencoded"} if body else {}
    if ajax:
        h["X-Requested-With"] = "XMLHttpRequest"
    try:
        with op.open(urllib.request.Request(BASE + path, data=body, method=method, headers=h), timeout=60) as r:
            return r.status, r.headers.get("Location"), r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Location"), e.read()


def plan(rid, ch="production"):
    return json.loads(send("GET", f"/admin/deploy/plan?releaseId={rid}&channel={ch}", ajax=True)[2])


def deployment(runtime="1.0.0"):
    app = api("GET", f"/api/v1/apps/{APP}").json()
    ch = next(c for c in app["channels"] if c["name"] == "production")
    return next((d for d in ch["deployments"] if d["runtimeVersion"] == runtime), {})


section("准备")
check("新建 App", api("POST", "/api/v1/apps", {"slug": APP, "name": "同时发布"}).status == 201)
tok = api("POST", f"/api/v1/apps/{APP}/tokens", {"name": "发布"}).json()["token"]
send("GET", "/login")
send("POST", "/login", {"username": "admin", "password": "admin-pass-123"})
send("GET", f"/admin/use?app={APP}")
make_export(f"/tmp/ota-e2e/{APP}/same", bundle_text=APP + " same {p}", platforms=("ios",))
make_export(f"/tmp/ota-e2e/{APP}/other", bundle_text=APP + " other {p}", platforms=("ios",))
dep = lambda rid, **kw: api("POST", f"/api/v1/apps/{APP}/channels/production/deploy", dict({"releaseId": rid, "rolloutPercent": 100}, **kw), token=tok).json()

section("同一个 dist 传了两次：旧的那份再发布不复制")
a = register(APP, f"/tmp/ota-e2e/{APP}/same", tok)
dep(a["id"], **{"as": "activation"})
time.sleep(1.1)
b = register(APP, f"/tmp/ota-e2e/{APP}/same", tok)
check("后传的那份发成常规更新", dep(b["id"])["releaseId"] == b["id"])
p = plan(a["id"])
check("发布前预览：同一份内容，只调灰度", p.get("mode") == "same" and p.get("live") == b["id"][:8], p)
d = dep(a["id"])
check("发布先传的那份：不复制，线上还是后传的那份", d["releaseId"] == b["id"] and d["republished"] is False, d)

section("内容不一样又更旧：照样复制")
time.sleep(1.1)
c = register(APP, f"/tmp/ota-e2e/{APP}/other", tok)
check("发布一个新内容 c", dep(c["id"])["releaseId"] == c["id"])
p = plan(a["id"])
check("预览：要复制，带上线上版本和两边的创建时间", p.get("mode") == "copy" and p.get("live") == c["id"][:8] and p.get("liveAt") and p.get("at"), p)
d = dep(a["id"])
check("发布更旧的 a：复制成新版本", d["republished"] is True and d["releaseId"] not in (a["id"], c["id"]), d)

section("发布弹窗同时选常规更新和口令更新")
p = plan(c["id"])
st, loc, _ = send("POST", "/admin/deploy", {"releaseId": c["id"], "channel": "production", "rollout": "100", "as": ["hot", "activation"]})
dpl = deployment()
check("提交成功", st == 302 and (loc or "").endswith("/admin/releases"), (st, loc))
check("常规更新和口令更新包都换成了它（线上比它新时用复制出来的那个）",
      dpl.get("releaseId") == dpl.get("activationReleaseId") and dpl.get("releaseId") is not None, dpl)
check("和预览说的一致", (p.get("mode") == "copy") == (dpl.get("releaseId") != c["id"]), (p, dpl))
st, loc, _ = send("POST", "/admin/deploy", {"releaseId": a["id"], "channel": "production", "as": "activation"})
check("只选口令更新：线上常规更新不动", deployment().get("activationReleaseId") == a["id"] and deployment().get("releaseId") == dpl.get("releaseId"), deployment())

section("上传时「作为常规更新和口令更新」")
make_export(f"/tmp/ota-e2e/{APP}/up", bundle_text=APP + " up {p}", platforms=("ios",))
import io
import zipfile
buf = io.BytesIO()
with zipfile.ZipFile(buf, "w") as z:
    for dd, _, fs in os.walk(f"/tmp/ota-e2e/{APP}/up"):
        for f in fs:
            z.write(os.path.join(dd, f), os.path.relpath(os.path.join(dd, f), f"/tmp/ota-e2e/{APP}/up"))
bnd = "----e2e" + uuid.uuid4().hex if "uuid" in dir() else "----e2eboundary"
out = io.BytesIO()
for k, v in {"activate": "true", "channel": "production", "as": "both"}.items():
    out.write(f'--{bnd}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
out.write(f'--{bnd}\r\nContent-Disposition: form-data; name="file"; filename="dist.zip"\r\nContent-Type: application/zip\r\n\r\n'.encode())
out.write(buf.getvalue() + f"\r\n--{bnd}--\r\n".encode())
try:
    with op.open(urllib.request.Request(BASE + "/admin/upload", data=out.getvalue(), method="POST", headers={
            "Content-Type": "multipart/form-data; boundary=" + bnd, "X-Requested-With": "XMLHttpRequest"}), timeout=60) as r:
        up = json.loads(r.read())
except urllib.error.HTTPError as e:
    up = {"status": e.code, "body": e.read()[:200]}
dpl = deployment()
check("上传成功", up.get("ok") is True, up)
check("常规更新和口令更新包都是刚上传的版本", dpl.get("releaseId") and dpl.get("releaseId") == dpl.get("activationReleaseId")
      and dpl.get("releaseId") not in (a["id"], b["id"], c["id"]), dpl)
done()
