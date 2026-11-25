"""第二十一轮：备份恢复、安装重启、重新安装「留空不改」。
- 安装和重新安装后，前端靠 /install/status 的 boot 判断重启好了
- 重新安装时管理员留空不改
- 系统配置：现在备份一次（后台做、轮询进度）、下载完整备份、上传恢复（分片 + 后台检查 + 重启）、服务器上的备份恢复、准备好的恢复立即重启
- 原来的表单提交（不带 X-Requested-With）照旧是跳转 + 提示
- App 备份下载、恢复（XHR 返回 JSON；表单提交照旧）"""
import http.cookiejar
import io
import shutil
import uuid

from lib import *

DATA = "/tmp/ota-e2e/bk/data"
PWD = "admin-pass-123"
BPWD = "backup-pass-123"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


class Admin:
    def __init__(self):
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())

    def send(self, method, path, body=None, headers=None, timeout=60):
        req = urllib.request.Request(BASE + path, data=body, method=method, headers=headers or {})
        try:
            with self.op.open(req, timeout=timeout) as r:
                return r.status, r.headers, r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read()

    def form(self, path, data, ajax=False):
        h = {"Content-Type": "application/x-www-form-urlencoded"}
        if ajax:
            h["X-Requested-With"] = "XMLHttpRequest"
        st, hd, body = self.send("POST", path, urllib.parse.urlencode(data).encode(), h)
        return st, hd.get("Location"), body

    def ajax(self, path, data=None):
        st, loc, body = self.form(path, data or {}, ajax=True)
        try:
            return json.loads(body)
        except ValueError:
            return {"status": st, "loc": loc, "raw": body[:200]}

    def get_json(self, path):
        st, hd, body = self.send("GET", path, headers={"X-Requested-With": "XMLHttpRequest", "Accept": "application/json"})
        return json.loads(body)

    def page(self, path):
        return self.get_json(path)["model"]

    def raw(self, path, data, headers):
        st, hd, body = self.send("POST", path, data, headers)
        return st, body

    def login(self, user="admin", pwd=PWD):
        self.send("GET", "/login")
        st, loc, _ = self.form("/login", {"username": user, "password": pwd})
        return st, loc

    def multipart(self, path, fields, file_name, file_bytes, ajax):
        b = "----e2e" + uuid.uuid4().hex
        out = io.BytesIO()
        for k, v in fields.items():
            out.write(f'--{b}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
        out.write(f'--{b}\r\nContent-Disposition: form-data; name="file"; filename="{file_name}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode())
        out.write(file_bytes + f"\r\n--{b}--\r\n".encode())
        h = {"Content-Type": "multipart/form-data; boundary=" + b}
        if ajax:
            h["X-Requested-With"] = "XMLHttpRequest"
        st, hd, body = self.send("POST", path, out.getvalue(), h)
        return st, hd.get("Location"), body


def status():
    try:
        with urllib.request.urlopen(BASE + "/install/status", timeout=5) as r:
            return json.loads(r.read())
    except Exception:
        return None


def wait_boot(old, limit=120):
    """等服务换一次启动：boot 变了、已安装、不在重启"""
    t0 = time.time()
    down = False
    while time.time() - t0 < limit:
        s = status()
        if s is None:
            down = True
        elif s.get("boot") != old and s.get("installed") and not s.get("restarting"):
            return time.time() - t0, down, s
        time.sleep(0.3)
    return None, down, None


def install_body(**kw):
    b = {"db": {"type": "h2"}, "dataMode": "keep", "clearConfirm": "", "restrictDomains": False, "domains": [],
         "publicUrl": BASE, "displayZone": "Asia/Shanghai", "storage": {"type": "local"},
         "adminUsername": "", "adminName": "", "adminPassword": "", "adminPassword2": ""}
    b.update(kw)
    return b


def install_submit(adm, body):
    st, hd, raw = adm.send("POST", "/install/submit", json.dumps(body).encode(),
                           {"Content-Type": "application/json", "X-Requested-With": "XMLHttpRequest"})
    try:
        return st, json.loads(raw)
    except ValueError:
        return st, {"raw": raw[:200]}


def read_install():
    with open(os.path.join(DATA, "install.json"), encoding="utf-8") as f:
        return json.load(f)


section("首次安装：返回 boot，重启后 boot 变了")
adm = Admin()
s0 = status()
check("没安装时 /install/status 带 boot", s0 and s0.get("installed") is False and isinstance(s0.get("boot"), int), s0)
st, r = install_submit(adm, install_body(adminUsername="admin", adminName="管理员", adminPassword=PWD, adminPassword2=PWD))
check("提交安装返回 ok、restarting 和 boot", st == 200 and r.get("ok") and r.get("restarting") and r.get("boot") == s0["boot"], (st, r))
secs, down, s1 = wait_boot(r.get("boot"))
check("重启好了：boot 变了、已安装", secs is not None, (secs, down, s1))
print(f"    重启用时 {secs:.1f} 秒，期间{'有' if down else '没有'}连不上的时候")
st, loc = adm.login()
check("用安装时设的管理员登录", st == 302 and loc and "/admin" in loc and "/login" not in loc, (st, loc))

section("重新安装：管理员留空不改")
st, loc, _ = adm.form("/admin/settings/reinstall", {"password": PWD})
check("输密码重新打开安装引导", st == 302 and loc and loc.endswith("/install"), (st, loc))
st, r = install_submit(adm, install_body())
check("内置数据库、管理员留空：保存成功", st == 200 and r.get("ok"), (st, r))
secs, _, _ = wait_boot(r.get("boot"))
check("重启好了", secs is not None)
adm = Admin()
st, loc = adm.login()
check("管理员没被改：原来的密码还能登录", st == 302 and "/login" not in (loc or ""), (st, loc))

section("现在备份一次：页面发起的放到后台做，轮询进度；表单提交照旧")
r = adm.ajax("/admin/settings/backup/now")
check("XHR 提交马上返回进度地址", r.get("ok") and r.get("status", "").startswith("/admin/settings/backup/once/"), r)
fin = None
for _ in range(100):
    s = adm.get_json(r["status"])
    if not s.get("ok") or s.get("state") == "ready":
        fin = s
        break
    time.sleep(0.2)
check("备份做完了，提示存在哪", fin and fin.get("ok") and fin.get("state") == "ready" and "已备份：" in fin.get("msg", "") and "url" not in fin, fin)
stored = sorted(f for f in os.listdir(os.path.join(DATA, "backups")) if f.endswith(".zip"))
check("服务器上多了一份备份", len(stored) == 1, stored)
s = adm.get_json(r["status"])
check("做完的进度地址用过一次就没了", not s.get("ok"), s)
time.sleep(1.1)
st, loc, _ = adm.form("/admin/settings/backup/now", {})
check("不带 X-Requested-With 的表单提交：照旧跳回备份页", st == 302 and loc and loc.endswith("/admin/settings#backup"), (st, loc))

section("下载完整备份")
r = adm.ajax("/admin/settings/backup/download", {"adminPassword": PWD, "backupPassword": BPWD, "backupPassword2": BPWD})
check("开始打包", r.get("ok") and r.get("status"), r)
ready = None
for _ in range(100):
    s = adm.get_json(r["status"])
    if not s.get("ok") or s.get("state") == "ready":
        ready = s
        break
    time.sleep(0.2)
check("打包好，给了下载地址", ready and ready.get("url"), ready)
st, hd, proj = adm.send("GET", ready["url"])
check("下载到 .otaproj", st == 200 and len(proj) > 1000 and "otaproj" in (hd.get("Content-Disposition") or ""), (st, len(proj)))
with open("/tmp/ota-e2e/bk/full.otaproj", "wb") as f:
    f.write(proj)

section("改一点数据，用上传的备份恢复（分片 + 后台检查 + 重启）")
adm.form("/admin/settings/backup/auto", {"enabled": "true", "keep": "14", "hour": "5"})
m = adm.page("/admin/settings")
check("备份后改了设置：自动备份开着、保留 14 份", m.get("backupAuto") is True and m.get("backupKeep") == 14, (m.get("backupAuto"), m.get("backupKeep")))


def upload(adm, data, chunk=256 * 1024, name="full.otaproj"):
    r = adm.ajax("/admin/settings/backup/restore/start", {"fileName": name, "size": str(len(data))})
    if not r.get("ok"):
        return r
    up, sent = r["id"], 0
    while sent < len(data):
        st, body = adm.raw(f"/admin/settings/backup/restore/{up}?offset={sent}", data[sent:sent + chunk],
                           {"Content-Type": "application/octet-stream", "X-Requested-With": "XMLHttpRequest"})
        a = json.loads(body)
        if not a.get("ok") or a["received"] <= sent:
            return a
        sent = a["received"]
    return {"ok": True, "id": up}


u = upload(adm, proj)
check("分片传完", u.get("ok"), u)
r = adm.ajax(f"/admin/settings/backup/restore/{u['id']}/finish", {"adminPassword": "wrong-pass", "backupPassword": BPWD})
check("登录密码不对：马上拒绝", r.get("ok") is False and "登录密码不对" in r.get("msg", ""), r)
r = adm.ajax(f"/admin/settings/backup/restore/{u['id']}/finish", {"adminPassword": PWD, "backupPassword": "bad-backup-pass"})
check("备份密码不对：先交给后台检查", r.get("ok") and r.get("check", "").startswith("/admin/settings/backup/restore/check/"), r)
res = None
for _ in range(100):
    res = adm.get_json(r["check"])
    if not res.get("ok") or res.get("state") != "checking":
        break
    time.sleep(0.2)
check("检查结果：备份密码不对，没有恢复", res.get("ok") is False and "备份密码不对" in res.get("msg", ""), res)
check("密码不对时没有留下准备好的恢复", not os.path.exists(os.path.join(DATA, "restore", "PENDING.json")))
check("上传的临时文件删掉了", not os.listdir(os.path.join(DATA, "tmp", "uploads")), os.listdir(os.path.join(DATA, "tmp", "uploads")))

u = upload(adm, proj, chunk=1024 * 1024)
r = adm.ajax(f"/admin/settings/backup/restore/{u['id']}/finish", {"adminPassword": PWD, "backupPassword": BPWD})
check("密码都对：交给后台检查", r.get("ok") and r.get("check"), r)
boot0, res = None, None
for _ in range(100):
    res = adm.get_json(r["check"])
    boot0 = res.get("boot", boot0)
    if not res.get("ok") or res.get("state") != "checking":
        break
    time.sleep(0.2)
check("检查通过，服务马上重启，带着 boot", res.get("ok") and res.get("state") == "restarting" and isinstance(boot0, int), res)
secs, down, _ = wait_boot(boot0)
check("重启好了", secs is not None, (secs, down))
print(f"    恢复重启用时 {secs:.1f} 秒")
adm = Admin()
st, loc = adm.login()
check("恢复后重新登录", st == 302 and "/login" not in (loc or ""), (st, loc))
m = adm.page("/admin/settings")
check("数据换回备份时的样子：自动备份关着", m.get("backupAuto") is False, (m.get("backupAuto"), m.get("backupKeep")))
restored = dict((m.get("backupRestored") or {}).get("$m") or [])
check("页面提示上次恢复成功", restored.get("ok") is True and restored.get("before", "").startswith("restore/before-"), m.get("backupRestored"))

section("服务器上的备份：XHR 恢复走后台检查；表单提交照旧")
stored = sorted(f for f in os.listdir(os.path.join(DATA, "backups")) if f.endswith(".zip"))
adm.form("/admin/settings/backup/auto", {"enabled": "true", "keep": "3", "hour": "4"})
r = adm.ajax(f"/admin/settings/backup/stored/{stored[0]}/restore", {"adminPassword": "wrong", "backupPassword": ""})
check("登录密码不对：马上拒绝", r.get("ok") is False and "登录密码不对" in r.get("msg", ""), r)
r = adm.ajax("/admin/settings/backup/stored/ota-nope.zip/restore", {"adminPassword": PWD, "backupPassword": ""})
check("备份不存在：马上拒绝", r.get("ok") is False and "不存在" in r.get("msg", ""), r)
r = adm.ajax(f"/admin/settings/backup/stored/{stored[0]}/restore", {"adminPassword": PWD, "backupPassword": ""})
check("交给后台检查", r.get("ok") and r.get("check"), r)
boot0 = None
for _ in range(100):
    res = adm.get_json(r["check"])
    boot0 = res.get("boot", boot0)
    if not res.get("ok") or res.get("state") != "checking":
        break
    time.sleep(0.2)
check("检查通过，重启", res.get("ok") and res.get("state") == "restarting", res)
secs, _, _ = wait_boot(boot0)
check("重启好了", secs is not None)
adm = Admin()
adm.login()
m = adm.page("/admin/settings")
check("换回这份备份里的设置", m.get("backupAuto") is False, m.get("backupAuto"))
adm.form("/admin/settings/backup/auto", {"enabled": "true", "keep": "3", "hour": "4"})
boot0 = status()["boot"]
st, loc, _ = adm.form(f"/admin/settings/backup/stored/{stored[0]}/restore", {"adminPassword": PWD, "backupPassword": ""})
check("不带 X-Requested-With 的表单提交：照旧在请求里检查完、跳回备份页", st == 302 and loc and loc.endswith("/admin/settings#backup"), (st, loc))
secs, _, _ = wait_boot(boot0)
check("照旧自动重启", secs is not None)
adm = Admin()
adm.login()
check("也换回了备份里的设置", adm.page("/admin/settings").get("backupAuto") is False)

section("准备好的恢复：XHR 立即重启")
os.makedirs(os.path.join(DATA, "restore"), exist_ok=True)
shutil.copy(os.path.join(DATA, "backups", stored[0]), os.path.join(DATA, "restore", "pending.zip"))
with open(os.path.join(DATA, "restore", "PENDING.json"), "w") as f:
    json.dump({"createdAt": "2026-11-19T00:00:00+08:00", "appVersion": "test", "apps": 0, "stagedBy": "admin", "stagedAt": "2026-11-19T00:01"}, f)
m = adm.page("/admin/settings")
check("页面上显示有一份准备好的恢复", m.get("backupPending") is not None, m.get("backupPending"))
r = adm.ajax("/admin/settings/backup/restore/apply", {"adminPassword": "wrong"})
check("登录密码不对：不重启", r.get("ok") is False and "登录密码不对" in r.get("msg", ""), r)
r = adm.ajax("/admin/settings/backup/restore/apply", {"adminPassword": PWD})
check("立即重启，带着 boot", r.get("ok") and isinstance(r.get("boot"), int), r)
secs, _, _ = wait_boot(r.get("boot"))
check("重启好了", secs is not None)
adm = Admin()
adm.login()
m = adm.page("/admin/settings")
check("准备好的恢复用掉了", m.get("backupPending") is None, m.get("backupPending"))

section("App 备份：下载、XHR 恢复返回 JSON、表单提交照旧")
st, loc, _ = adm.form("/admin/apps/create", {"slug": "bkapp", "name": "备份测试", "keyMode": "generate"})
check("建一个 App", st == 302, (st, loc))
st, hd, otabak = adm.send("POST", "/admin/apps/backup", urllib.parse.urlencode(
    {"adminPassword": PWD, "backupPassword": BPWD, "backupPassword2": BPWD}).encode(), {"Content-Type": "application/x-www-form-urlencoded"})
check("下载 App 备份", st == 200 and "otabak" in (hd.get("Content-Disposition") or "") and len(otabak) > 100, (st, hd.get("Content-Disposition")))
st, loc, body = adm.multipart("/admin/apps/restore", {"adminPassword": PWD, "backupPassword": BPWD, "overwrite": "true", "from": "app"}, "bkapp.otabak", otabak, True)
r = json.loads(body)
check("XHR 恢复：返回 ok、提示和要跳去的页面", st == 200 and r.get("ok") and "已用备份恢复" in r.get("msg", "") and r.get("redirect") == "/admin/app", (st, r))
st, loc, body = adm.multipart("/admin/apps/restore", {"adminPassword": PWD, "backupPassword": "wrong-pass", "overwrite": "true", "from": "app"}, "bkapp.otabak", otabak, True)
r = json.loads(body)
check("XHR 恢复备份密码不对：ok=false 和原因", r.get("ok") is False and r.get("msg", "").startswith("没有恢复："), r)
st, loc, body = adm.multipart("/admin/apps/restore", {"adminPassword": "wrong", "backupPassword": BPWD, "overwrite": "true", "from": "app"}, "bkapp.otabak", otabak, True)
r = json.loads(body)
check("XHR 恢复登录密码不对", r.get("ok") is False and "登录密码不对" in r.get("msg", ""), r)
st, loc, body = adm.multipart("/admin/apps/restore", {"adminPassword": PWD, "backupPassword": BPWD, "overwrite": "true", "from": "app"}, "bkapp.otabak", otabak, False)
check("表单提交恢复：照旧跳到 App 页", st == 302 and loc and loc.endswith("/admin/app"), (st, loc))
st, loc, body = adm.multipart("/admin/apps/restore", {"adminPassword": PWD, "backupPassword": "wrong", "overwrite": "true", "from": "apps"}, "bkapp.otabak", otabak, False)
check("表单提交失败：照旧跳回所有 App 的恢复弹窗", st == 302 and loc and loc.endswith("/admin/apps#restore"), (st, loc))

done()
