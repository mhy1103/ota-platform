"""文件存储切换：本机 → S3 兼容对象存储（本地模拟，严格校验签名）→ 本机。
测试连接、切换、切换后 App 拿到的下载地址能不能下载，配置写错时的提示"""
import http.cookiejar
import sys

from lib import *

S3 = os.environ.get("S3_BASE", "http://127.0.0.1:18860")
T = "/tmp/ota-e2e/st/files"
os.makedirs(T, exist_ok=True)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


class Admin:
    def __init__(self):
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())

    def send(self, method, path, body=None, headers=None):
        req = urllib.request.Request(BASE + path, data=body, method=method, headers=headers or {})
        try:
            with self.op.open(req, timeout=120) as r:
                return r.status, r.headers, r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read()

    def form(self, path, data):
        return self.send("POST", path, urllib.parse.urlencode(data).encode(),
                         {"Content-Type": "application/x-www-form-urlencoded", "X-Requested-With": "XMLHttpRequest"})

    def ajax(self, path, data):
        st, hd, body = self.form(path, data)
        try:
            return json.loads(body)
        except ValueError:
            return {"status": st, "raw": body[:300]}

    def get_json(self, path):
        return json.loads(self.send("GET", path, headers={"X-Requested-With": "XMLHttpRequest", "Accept": "application/json"})[2])


def s3mode(**kw):
    with urllib.request.urlopen(S3 + "/__s3?" + urllib.parse.urlencode(kw), timeout=5) as r:
        return json.loads(r.read())


def status():
    with urllib.request.urlopen(BASE + "/install/status", timeout=5) as r:
        return json.loads(r.read())


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


def manifest_urls():
    h = {"expo-platform": "ios", "expo-runtime-version": "1.0.0", "expo-protocol-version": "1"}
    parts = parse_multipart(plain("GET", "/m/stor", headers=h))
    m = json.loads(parts["manifest"][1])
    return [m["launchAsset"]["url"]] + [a["url"] for a in m.get("assets", [])]


def fetch(url):
    try:
        with urllib.request.urlopen(url, timeout=10) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:
        return 0, str(e).encode()


def switch(adm, form):
    r = adm.ajax("/admin/settings/storage/switch", form)
    if not r.get("ok"):
        return r
    for _ in range(200):
        s = adm.get_json("/admin/settings/storage/status")
        if s["state"] in ("done", "failed", "confirm"):
            return s
        time.sleep(0.2)
    return {"state": "timeout"}


GOOD = {"type": "s3", "endpoint": S3, "bucket": "ota-bucket", "region": "", "accessKey": "TESTAK", "secretKey": "testsecret123",
        "prefix": "ota/", "publicUrl": "", "pathStyle": "true"}

if "--setup" in sys.argv:
    s0 = status()
    if not s0["installed"]:
        req = urllib.request.Request(BASE + "/install/submit", method="POST", headers={"Content-Type": "application/json"}, data=json.dumps({
            "db": {"type": "h2"}, "dataMode": "keep", "restrictDomains": False, "domains": [], "publicUrl": BASE,
            "displayZone": "Asia/Shanghai", "storage": {"type": "local"}, "adminUsername": "admin",
            "adminName": "管理员", "adminPassword": "admin-pass-123", "adminPassword2": "admin-pass-123"}).encode())
        urllib.request.urlopen(req, timeout=30).read()
        for _ in range(120):
            try:
                s = status()
                if s["installed"] and s["boot"] != s0["boot"]:
                    break
            except Exception:
                pass
            time.sleep(0.3)
    api("POST", "/api/v1/apps", {"slug": "stor", "name": "存储测试"})
    token = api("POST", "/api/v1/apps/stor/tokens", {"name": "CI"}).json()["token"]
    make_export(f"{T}/v1", bundle_text="st v1 {p}")
    rel = register("stor", f"{T}/v1", token)
    api("POST", "/api/v1/apps/stor/channels/production/deploy", {"releaseId": rel["id"], "rolloutPercent": 100}, token=token)
    print("setup ok", rel["id"])
    sys.exit(0)

adm = Admin()
adm.send("GET", "/login")
adm.send("POST", "/login", urllib.parse.urlencode({"username": "admin", "password": "admin-pass-123"}).encode(),
         {"Content-Type": "application/x-www-form-urlencoded"})

section("本机存储时：App 下载正常")
urls = manifest_urls()
check("清单里的下载地址都能下载", all(fetch(u)[0] == 200 for u in urls), urls)

section("测试连接")
s3mode(public=1, head403=0, region="")
r = adm.ajax("/admin/settings/storage/test", GOOD)
check("配置都对：连接正常", r.get("ok") is True, r)
r = adm.ajax("/admin/settings/storage/test", dict(GOOD, secretKey="wrong-secret"))
check("Secret Key 不对：说清楚是密钥不对", r.get("ok") is False and "Secret Key" in r.get("msg", ""), r)
r = adm.ajax("/admin/settings/storage/test", dict(GOOD, accessKey="NOPE"))
check("Access Key 不对：说清楚", r.get("ok") is False and "Access Key" in r.get("msg", ""), r)
r = adm.ajax("/admin/settings/storage/test", dict(GOOD, accessKey="testsecret123", secretKey="AKID" + "x" * 32))
check("两个框填反了：直接说填反了，不露出填进 Access Key 的密钥", r.get("ok") is False and "填反了" in r.get("msg", "")
      and "AKID" in r.get("msg", "") and "testse" not in r.get("msg", ""), r)
r = adm.ajax("/admin/settings/storage/test", dict(GOOD, accessKey="AKID" + "y" * 32))
check("Access Key 像 SecretId 但不对：不误报填反了", r.get("ok") is False and "填反了" not in r.get("msg", "")
      and "AKIDyy" in r.get("msg", ""), r)
r = adm.ajax("/admin/settings/storage/test", dict(GOOD, bucket="no-such-bucket"))
check("存储桶不存在：说清楚", r.get("ok") is False and "存储桶" in r.get("msg", ""), r)
r = adm.ajax("/admin/settings/storage/test", dict(GOOD, pathStyle=""))
check("服务地址是 IP、没勾路径风格：自动按路径风格访问", r.get("ok") is True, r)
r = adm.ajax("/admin/settings/storage/test", dict(GOOD, endpoint="http://127.0.0.1:1"))
check("连不上：说清楚连不上哪个地址", r.get("ok") is False and "127.0.0.1:1" in r.get("msg", ""), r)
r = adm.ajax("/admin/settings/storage/test", dict(GOOD, endpoint="htp:/ bad url"))
check("服务地址格式不对：返回提示，不是 500", r.get("ok") is False and r.get("msg"), r)
s3mode(region="ap-guangzhou")
r = adm.ajax("/admin/settings/storage/test", GOOD)
check("区域不对：提示应该填哪个区域", r.get("ok") is False and "ap-guangzhou" in r.get("msg", ""), r)
r = adm.ajax("/admin/settings/storage/test", dict(GOOD, region="ap-guangzhou"))
check("填对区域：连接正常", r.get("ok") is True, r)
s3mode(region="", public=0)
r = adm.ajax("/admin/settings/storage/test", GOOD)
check("存储桶不是公共读：拦下来，说明 App 会下载失败", r.get("ok") is False and "公共读" in r.get("msg", ""), r)
r = switch(adm, GOOD)
check("存储桶不是公共读：不让切换", r.get("ok") is False and "公共读" in r.get("msg", ""), r)
s3mode(public=1)

section("切换到对象存储")
s3mode(head403=1)
r = switch(adm, GOOD)
check("切换完成（HEAD 被拒也能切：直接上传）", r.get("state") == "done", r)
check("文件都复制过去了", r.get("copied", 0) >= 2 and r.get("missing") == 0, r)
m = adm.get_json("/admin/settings")["model"]
check("页面显示用的是对象存储", m.get("storageS3") is True, m.get("storage"))
urls = manifest_urls()
check("清单里的下载地址指向对象存储（启动包经本服务器跳转过去）", all(u.startswith(S3) or "/l/" in u for u in urls), urls)
check("App 用这些地址能下载到文件", all(fetch(u)[0] == 200 for u in urls), [(u, fetch(u)[0]) for u in urls])
s3mode(head403=0)

section("切回本机存储")
r = switch(adm, {"type": "local"})
check("切回本机完成", r.get("state") == "done", r)
urls = manifest_urls()
check("下载地址回到本服务器，能下载", all(u.startswith(BASE) and fetch(u)[0] == 200 for u in urls), urls)


def manifest_parts():
    h = {"expo-platform": "ios", "expo-runtime-version": "1.0.0", "expo-protocol-version": "1"}
    return parse_multipart(plain("GET", "/m/stor", headers=h))


def release_ids():
    return [x["id"] for x in api("GET", "/api/v1/apps/stor/releases?limit=50").json()]


def wait_done(adm):
    for _ in range(200):
        s = adm.get_json("/admin/settings/storage/status")
        if s["state"] in ("done", "failed", "confirm"):
            return s
        time.sleep(0.2)
    return {"state": "timeout"}


section("原来的存储连不上（存储桶删了）：先问，确认后清空缺文件的版本再换")
v1 = release_ids()
r = switch(adm, GOOD)
check("再切到对象存储", r.get("state") == "done", r)
token = api("POST", "/api/v1/apps/stor/tokens", {"name": "CI2"}).json()["token"]
make_export(f"{T}/v2", bundle_text="st v2 {p}")
rel2 = register("stor", f"{T}/v2", token)
api("POST", "/api/v1/apps/stor/channels/production/deploy", {"releaseId": rel2["id"], "rolloutPercent": 100}, token=token)
check("v2 发布到 production，文件只在对象存储里", manifest_parts().get("manifest") and rel2["id"] in manifest_parts()["manifest"][1])
s3mode(gone=1)
check("存储桶删了：App 下载失败", any(fetch(u)[0] != 200 for u in manifest_urls()))
r = switch(adm, {"type": "local"})
check("不直接换，等确认", r.get("state") == "confirm", r)
check("说清楚连不上的原因：存储桶不存在", "不存在" in (r.get("error") or ""), r)
note = r.get("note") or ""
check("说清楚会清空什么：1 个版本，另外 1 个文件齐全的留着", "会清空 1 个版本" in note and "另外 1 个版本新存储里文件齐全" in note, note)
check("提醒只是密钥、权限、网络的问题先取消", "先取消" in note, note)
m = adm.get_json("/admin/settings")["model"]
check("没确认之前还在用对象存储", m.get("storageS3") is True, m.get("storage"))
check("刷新页面也会把这个提示摆出来", m.get("storageLast") is True, m.get("storageLast"))
check("两个版本都还在", rel2["id"] in release_ids() and all(i in release_ids() for i in v1), release_ids())
r = adm.ajax("/admin/settings/storage/clear", {})
check("确认清空并切换", r.get("ok") is True, r)
r = wait_done(adm)
check("切换完成", r.get("state") == "done", r)
check("说清楚清空了什么", "清空了 1 个版本" in (r.get("note") or "") and "1 个文件齐全的版本留着" in (r.get("note") or ""), r)
m = adm.get_json("/admin/settings")["model"]
check("现在用本机存储", m.get("storageS3") is False, m.get("storage"))
ids = release_ids()
check("缺文件的 v2 删掉了，文件齐全的 v1 留着", rel2["id"] not in ids and all(i in ids for i in v1), ids)
parts = manifest_parts()
check("production 没有线上版本了：App 收到没有更新，继续用手机上的", "noUpdateAvailable" in parts.get("directive", ({}, ""))[1], parts)
r = adm.ajax("/admin/settings/storage/clear", {})
check("没有等着确认的切换时再点清空：说清楚", r.get("ok") is False and "没有等着确认" in r.get("msg", ""), r)

section("原来的存储连不上，但新存储里文件都在：不用清空，直接换")
api("POST", "/api/v1/apps/stor/channels/production/deploy", {"releaseId": v1[0], "rolloutPercent": 100}, token=token)
s3mode(gone=0)
r = switch(adm, GOOD)
check("切到对象存储", r.get("state") == "done", r)
s3mode(gone=1)
r = switch(adm, {"type": "local"})
check("直接换回本机，不用确认", r.get("state") == "done", r)
check("说清楚是直接换的", "直接换了过来" in (r.get("note") or ""), r)
check("版本一个没删", all(i in release_ids() for i in v1), release_ids())
urls = manifest_urls()
check("App 能从本服务器下载", all(u.startswith(BASE) and fetch(u)[0] == 200 for u in urls), urls)
s3mode(gone=0)
done()
