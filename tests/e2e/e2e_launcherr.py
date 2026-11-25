"""启动错误补报：App 用 expo-updates 的 setExtraParamAsync 把崩溃内容放进 Expo-Extra-Params
（ota-launch-error：UTF-8 再 base64url；ota-launch-error-update：崩溃的版本 ID）。
清单照常返回，错误记进「启动错误」并算到对的版本上；解不开、太长、格式乱的都忽略，不影响检查更新"""
import base64
import http.cookiejar

from lib import *

T = "/tmp/ota-e2e/lerr-files"
os.makedirs(T, exist_ok=True)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


class Admin:
    def __init__(self):
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())

    def call(self, method, path, data=None, headers=None):
        body = urllib.parse.urlencode(data or {}).encode() if method == "POST" else None
        req = urllib.request.Request(BASE + path, data=body, method=method, headers=headers or {})
        try:
            with self.op.open(req, timeout=60) as r:
                return r.status, r.headers.get("Location"), r.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            return e.code, e.headers.get("Location"), e.read().decode("utf-8", "replace")

    def json(self, path):
        return json.loads(self.call("GET", path, headers={"X-Requested-With": "XMLHttpRequest", "Accept": "application/json"})[2])


def publish(app, root, token, message):
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
    rel = api("POST", f"/api/v1/apps/{app}/releases", {"runtimeVersion": cfg["runtimeVersion"], "message": message,
                                                      "metadata": meta, "expoConfig": cfg, "files": files}, token=token).json()
    api("POST", f"/api/v1/apps/{app}/channels/production/deploy", {"releaseId": rel["id"], "rolloutPercent": 100}, token=token)
    return rel


def b64(s):
    return base64.urlsafe_b64encode(s.encode("utf-8")).decode().rstrip("=")


def ask(client, platform="ios", current=None, failed=None, extra=None, fatal=None):
    h = {"expo-platform": platform, "expo-runtime-version": "1.0.0", "expo-protocol-version": "1", "eas-client-id": client}
    if current:
        h["expo-current-update-id"] = current
        h["expo-embedded-update-id"] = "00000000-0000-0000-0000-000000000000"
    if failed:
        h["expo-recent-failed-update-ids"] = '"' + failed + '"'
    if extra is not None:
        h["expo-extra-params"] = extra
    if fatal:
        h["expo-fatal-error"] = fatal
    return plain("GET", "/m/lerr", headers=h)


def errors(rid, want=None, wait=6.0):
    """版本详情里的启动错误；给了 want 就等到它出现（记录是异步写的）"""
    end = time.time() + wait
    while True:
        errs = adm.json(f"/admin/release/{rid}").get("errors", [])
        if want is None or any(want in e["message"] for e in errs) or time.time() > end:
            return errs
        time.sleep(0.25)


def messages(rid):
    return [e["message"] for e in errors(rid)]


section("准备：App 和两个版本，线上是 v2")
api("POST", "/api/v1/apps", {"slug": "lerr", "name": "补报测试"})
token = api("POST", "/api/v1/apps/lerr/tokens", {"name": "CI"}).json()["token"]
make_export(f"{T}/v1", bundle_text="lerr v1 {p}")
make_export(f"{T}/v2", bundle_text="lerr v2 {p}")
v1 = publish("lerr", f"{T}/v1", token, "v1")
v2 = publish("lerr", f"{T}/v2", token, "v2")
adm = Admin()
adm.call("GET", "/login")
st, loc, _ = adm.call("POST", "/login", {"username": "admin", "password": "admin-pass-123"})
check("管理员登录", st == 302 and loc and "/admin" in loc, loc)
check("还没有启动错误", errors(v1["id"]) == [] and errors(v2["id"]) == [])

section("iOS：崩溃版本 ID 和错误内容一起带上")
msg_a = "TypeError: undefined is not an object (evaluating 'e.user.avatar')\nActivityBanner@main.jsbundle:1:2345"
r = ask("dev-a", current=v1["id"], failed=v2["id"],
        extra=f'ota-launch-error="{b64(msg_a)}", ota-launch-error-update="{v2["id"].upper()}"')
check("清单照常返回", r.status == 200 and "manifest" in parse_multipart(r), r.status)
errs = errors(v2["id"], "evaluating 'e.user.avatar'")
check("错误记到崩溃的版本 v2，内容完整（含换行）", any(e["message"] == msg_a and e["platform"] == "ios" for e in errs), errs)

section("Android：没有版本 ID，按设备报告的启动失败版本归类")
msg_b = "java.lang.RuntimeException: Unhandled JS Exception: Error: 播放器初始化失败\n    at Player.init (index.android.bundle:3:99)"
ask("dev-b", platform="android", current=v1["id"], failed=v2["id"], extra=f'ota-launch-error="{b64(msg_b)}"')
check("记到 v2，中文不乱码", msg_b in [e["message"] for e in errors(v2["id"], "播放器初始化失败")])

section("启动后才崩溃：没有失败版本，记到正在运行的版本")
msg_c = "RangeError: Maximum call stack size exceeded"
ask("dev-c", current=v1["id"], extra=f'ota-launch-error="{b64(msg_c)}"')
check("记到设备当时运行的 v1", any(msg_c == e["message"] for e in errors(v1["id"], msg_c)))
check("没有误记到 v2", msg_c not in messages(v2["id"]))

section("expo-fatal-error 请求头还在用：两个都带时以请求头为准")
ask("dev-e", current=v1["id"], failed=v2["id"], fatal="from-header-only",
    extra=f'ota-launch-error="{b64("from-extra-param")}", ota-launch-error-update="{v2["id"]}"')
m2 = [e["message"] for e in errors(v2["id"], "from-header-only")]
check("记的是请求头里的内容", "from-header-only" in m2 and "from-extra-param" not in m2, m2)

section("请求头里有别的参数、参数里有逗号和分号")
msg_g = "Error: 混在别的参数里"
extra_g = f'channel="be,ta;x", n=5, flag, ota-launch-error="{b64(msg_g)}";v=2, other="a\\"b"'
r = ask("dev-g", current=v1["id"], failed=v2["id"], extra=extra_g)
check("清单照常返回", r.status == 200, r.status)
check("照样解出来", msg_g in [e["message"] for e in errors(v2["id"], msg_g)])

section("格式不对的都忽略，检查更新不受影响")
bad = [
    ("不是 base64", 'ota-launch-error="%%%not-base64%%%"'),
    ("不是 UTF-8", f'ota-launch-error="{base64.urlsafe_b64encode(bytes([0xff, 0xfe, 0xfd])).decode()}"'),
    ("太长", 'ota-launch-error="' + "A" * 7000 + '"'),
    ("引号没闭合", 'ota-launch-error="' + b64("unclosed")),
    ("空字符串", 'ota-launch-error=""'),
]
before = len(messages(v1["id"])) + len(messages(v2["id"]))
for i, (name, extra) in enumerate(bad):
    r = ask(f"dev-bad-{i}", current=v1["id"], failed=v2["id"], extra=extra)
    check(f"{name}：清单照常返回", r.status == 200, r.status)
time.sleep(1.5)
after = len(messages(v1["id"])) + len(messages(v2["id"]))
check("这些都没有记成启动错误", after == before, (before, after))

section("版本 ID 格式不对：不用它，按失败版本归类")
msg_i = "Error: 版本号是乱写的"
ask("dev-i", current=v1["id"], failed=v2["id"], extra=f'ota-launch-error="{b64(msg_i)}", ota-launch-error-update="../../etc"')
check("记到 v2", msg_i in [e["message"] for e in errors(v2["id"], msg_i)])

section("不带补报参数：和以前一样")
r = ask("dev-plain", current=v1["id"])
check("清单照常返回", r.status == 200 and "manifest" in parse_multipart(r), r.status)
r = ask("dev-old", current=v1["id"], failed=v2["id"], fatal="old-style-fatal")
check("老的 expo-fatal-error 照样记", "old-style-fatal" in [e["message"] for e in errors(v2["id"], "old-style-fatal")])

done()
