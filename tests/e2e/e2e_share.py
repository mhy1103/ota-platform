"""数据大屏分享：生成（有效期、访问密码、备注）、分享记录、取消；不用登录打开 /s/口令 看到和分享的人一样的大屏；
密码输对才能看、试错太多锁 10 分钟；过期、取消、分享的人没权限了都显示失效；后台页面脚本凭分享 Cookie 才取得到。
实例要用 AUTO_SERVER 的 H2（测试里直接改库让分享过期）：
OTA_BASE=http://127.0.0.1:18860 OTA_DATA=/tmp/ota-e2e/share60 python3 e2e_share.py"""
import glob
import subprocess

exec(open("/tmp/ota-e2e/e2e_subagent.py").read().split("P_TIERS = ")[0])

DATA = os.environ["OTA_DATA"]
JAVA = os.path.expanduser("~/Library/Caches/ota-build/jdk-17.0.21+9/Contents/Home/bin/java")
H2 = "/tmp/ota-e2e/h2/h2-2.2.224.jar"
URL = f"jdbc:h2:file:{DATA}/db/ota;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"
ASSET = "/vue/app/" + os.path.basename(sorted(glob.glob("/Users/chuzu/shangjia/ota-platform/src/main/resources/static/vue/app/app-*.js"))[0])


def sql(q):
    r = subprocess.run([JAVA, "-cp", H2, "org.h2.tools.Shell", "-url", URL, "-user", "sa", "-password", "", "-sql", q],
                       capture_output=True, text=True, timeout=120)
    out = r.stdout + r.stderr
    if "Exception" in out or "Error" in out:
        print("SQL 出错：", q, out[:600])
    return out


def jcall(s, method, path, data=None):
    st, loc, body = s.call(method, path, data)
    try:
        return st, json.loads(body)
    except Exception:
        return st, {"_raw": body[:200], "_loc": loc}


def share(s, **kw):
    d = {"scope": "app", "app": "share1", "hours": "24", "password": "", "note": "", "theme": "", "origin": BASE}
    d.update({k: str(v) for k, v in kw.items()})
    return jcall(s, "POST", "/admin/screen/shares", d)


def rows(s):
    st, d = jcall(s, "GET", "/admin/screen/shares")
    return d.get("shares", []) if st == 200 and d.get("ok") else None


def row(s, sid):
    return next((r for r in rows(s) or [] if r["id"] == sid), None)


def token_of(url):
    return url.rsplit("/s/", 1)[1]


def cookies(s):
    for h in s.op.handlers:
        if isinstance(h, urllib.request.HTTPCookieProcessor):
            return {c.name: c for c in h.cookiejar}
    return {}


def icon(s, token, app_id):
    """不登录、只带着这个会话的 Cookie 取分享页上的 App 图标：(状态码, 类型, 前 8 个字节)"""
    req = urllib.request.Request(f"{BASE}/s/{token}/icon/{app_id}?v=1")
    try:
        with s.op.open(req, timeout=30) as r:
            return r.status, r.headers.get("Content-Type") or "", r.read()[:8]
    except urllib.error.HTTPError as e:
        return e.code, "", b""


def raw(s, method, path, data=None, headers=None):
    body = urllib.parse.urlencode(data or {}).encode() if method == "POST" else None
    h = {"Content-Type": "application/x-www-form-urlencoded"} if method == "POST" else {}
    h.update(headers or {})
    req = urllib.request.Request(BASE + path, data=body, method=method, headers=h)
    try:
        with s.op.open(req, timeout=60) as r:
            return r.status, r.headers.get("Location") or "", r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Location") or "", e.read().decode("utf-8", "replace")


section("准备：两个 App、管理员登录")
for slug in ("share1", "share2"):
    mgmt("POST", "/api/v1/apps", {"slug": slug, "name": slug})
    mgmt("POST", f"/api/v1/apps/{slug}/redemption", {"open": True})
KEYS = mgmt("GET", "/api/v1/apps/share1/codes-key")[1]
adm, st, loc = login("admin", "admin-pass-123")
check("管理员登录", st == 302 and "/admin" in loc, loc)
st, body = mgmt("POST", "/api/v1/codes", {"code": "SH-C1", "maxUses": 100})
check("share1 有口令", st == 201, (st, body))
on = sum(1 for i in range(3) if activate("SH-C1", f"share-dev-{i}"))
check("share1 今天 3 台激活", on == 3, on)

section("生成分享：校验")
st, d = share(adm, hours=5)
check("有效期只能选给定的几档", st == 400 and not d.get("ok") and "有效期" in d.get("message", ""), (st, d))
st, d = share(adm, password="abc")
check("访问密码太短：拒绝", st == 400 and "4–32" in d.get("message", ""), (st, d))
st, d = share(adm, password="x" * 33)
check("访问密码太长：拒绝", st == 400 and "4–32" in d.get("message", ""), (st, d))
st, d = share(adm, note="备" * 41)
check("备注超过 40 个字：拒绝", st == 400 and "40" in d.get("message", ""), (st, d))
st, d = share(adm, app="nope")
check("没有这个 App：拒绝", d.get("ok") is False and "没有这个 App" in d.get("message", ""), (st, d))
check("校验没过的一个都没生成", rows(adm) == [], rows(adm))

section("生成分享")
st, A = share(adm, note="给老板看的")
check("share1、1 天、不要密码", st == 200 and A.get("ok") and A["url"].startswith(BASE + "/s/") and A["share"]["state"] == "active"
      and A["share"]["locked"] is False and A["share"]["note"] == "给老板看的" and A["share"]["appName"] == "share1", A)
left = (A["share"].get("expiresAt", 0) / 1000 - time.time()) / 3600 if A.get("share") else 0
check("1 天后失效", 23.9 < left <= 24.01, left)
check("带着二维码", A.get("qr", "").startswith("<svg") and "</svg>" in A.get("qr", ""), A.get("qr", "")[:80])
AT, AID = token_of(A["url"]), A["share"]["id"]
check("链接口令够长、只有网址安全字符", re.fullmatch(r"[A-Za-z0-9_-]{20,64}", AT) is not None, AT)
st, B = share(adm, app="share2", hours=0, password=" pass-1234 ", theme="light")
check("share2、长期、带密码（前后空格不算）", B.get("ok") and B["share"].get("expiresAt") is None and B["share"]["locked"] is True, B)
BT, BID = token_of(B["url"]), B["share"]["id"]
st, C = share(adm, scope="all", hours=1)
check("全部 App、1 小时", C.get("ok") and C["share"]["scope"] == "all" and C["share"].get("appName") is None, C)
CT, CID = token_of(C["url"]), C["share"]["id"]
st, D = share(adm, origin="https://evil.example.com")
check("传来的地址不是这个站点：链接按请求自己拼", D.get("ok") and D["url"].startswith(BASE + "/s/"), D.get("url"))
DID = D["share"]["id"]

section("分享记录")
st, d = jcall(adm, "GET", "/admin/screen/shares")
check("列出 4 个、新的在前、标着超级管理员", st == 200 and d.get("ok") and [r["id"] for r in d["shares"]] == [DID, CID, BID, AID]
      and d.get("owner") is True and d.get("hours") == [1, 24, 168, 720, 0], d)
ra = row(adm, AID)
check("记录里有链接、谁分享的、还没人打开", ra["path"] == "/s/" + AT and ra["createdBy"] == "admin" and ra["mine"] is True
      and ra["views"] == 0 and ra.get("lastViewedAt") is None, ra)
check("记录里不带密码", all("pass" not in json.dumps(r) for r in rows(adm)), rows(adm))

section("不用登录打开")
v = Session()
st, loc, page = v.page("/s/" + AT)
check("打开 share1 的分享：就是数据大屏", st == 200 and page and page["view"] == "admin/screen", (st, loc, page and page.get("view")))
vm = page["model"] if page else {}
check("标着是分享出去的、没有切换 App 的列表、没有回后台", vm.get("scShared") is True and vm.get("scPicks") == [] and not vm.get("scBack")
      and vm.get("scName") == "share1", {k: vm.get(k) for k in ("scShared", "scPicks", "scBack", "scName")})
am = adm.model("/admin/screen?app=share1")
same_keys = [k for k in am if k.startswith("sc") and k not in ("scPicks", "scBack")]
diff = [k for k in same_keys if json.dumps(am[k], sort_keys=True) != json.dumps(vm.get(k), sort_keys=True)]
check("数据和管理员在后台看到的一模一样", len(same_keys) > 15 and not diff, diff)
check("今天的激活数对得上", vm.get("scToday") == 3, vm.get("scToday"))
check("页面数据里没有登录的人的信息", "admin-pass" not in json.dumps(page) and not (page.get("ctx") or {}).get("user"), (page.get("ctx") or {}).keys())
ck = cookies(v)
check("发了取后台页面脚本用的 Cookie（只给 /vue/app）", "ota_sv" in ck and ck["ota_sv"].path == "/vue/app/", {k: c.path for k, c in ck.items()})
st, loc, _ = v.call("GET", ASSET)
check("凭分享 Cookie 取得到后台页面脚本", st == 200, (st, loc))
st, loc, _ = Session().call("GET", ASSET)
check("没登录也没分享 Cookie：取不到", st in (302, 401, 403), (st, loc))
st, loc, html = raw(Session(), "GET", "/s/" + AT, headers={"Accept": "text/html"})
check("浏览器直接打开：给加载壳", st == 200 and "<html" in html.lower(), (st, html[:120]))
st, loc, _ = v.call("GET", "/admin")
check("分享 Cookie 进不了后台", st == 302 and "/login" in loc, (st, loc))
st, _ = jcall(v, "GET", "/admin/screen/shares")
check("分享 Cookie 看不了分享记录", st in (302, 401, 403), st)
time.sleep(0.3)
r1 = row(adm, AID)
check("记了一次打开和最近打开时间", r1["views"] == 1 and r1.get("lastViewedAt"), r1)
v.page("/s/" + AT)
v.page("/s/" + AT)
check("同一个浏览器定时刷新不重复算", row(adm, AID)["views"] == 1, row(adm, AID))
Session().page("/s/" + AT)
check("换个浏览器再算一次", row(adm, AID)["views"] == 2, row(adm, AID))

section("全部 App 的分享")
st, _, pc = Session().page("/s/" + CT)
cm = pc["model"] if pc else {}
check("全部 App：两个 App 合在一起、带 App 排行", pc and pc["view"] == "admin/screen" and cm.get("scScope") == "all"
      and len(cm.get("scApps") or cm.get("scAppRank") or []) >= 2, {k: cm.get(k) for k in ("scScope",)})
check("全部 App 里今天也是 3 台", cm.get("scToday") == 3, cm.get("scToday"))

section("分享出去的页面：App 图标不用登录也取得到")
import struct
import zlib


def png32():
    rows_ = b"".join(b"\x00" + bytes([30, 144, 255]) * 32 for _ in range(32))
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 32, 32, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(rows_)) + chunk(b"IEND", b"")


IDS = {a["slug"]: a["id"] for a in adm.model("/admin/admins")["allApps"]}
for slug in ("share1", "share2"):
    sql(f"insert into ota_app_icon (app_id, content_type, data, updated_at) values ({IDS[slug]}, 'image/png', X'{png32().hex()}', current_timestamp)")
    sql(f"update ota_app set icon_version = 'ic{IDS[slug]}' where id = {IDS[slug]}")
st, _, pi = Session().page("/s/" + AT)
check("分享页带着图标版本（页面按它拼图标地址）", pi and pi["model"].get("scIcon") == f"ic{IDS['share1']}", pi and pi["model"].get("scIcon"))
st, ct, head = icon(Session(), AT, IDS["share1"])
check("没登录：从分享地址取得到 share1 的图标", st == 200 and ct.startswith("image/png") and head.startswith(b"\x89PNG"), (st, ct, head))
st, loc, _ = Session().call("GET", f"/admin/apps/{IDS['share1']}/icon?v=1")
check("后台的图标地址没登录还是取不到", st in (302, 401, 403, 404), (st, loc))
check("share1 的分享取不到 share2 的图标", icon(Session(), AT, IDS["share2"])[0] == 404)
check("全部 App 的分享：两个 App 的图标都取得到", icon(Session(), CT, IDS["share1"])[0] == 200 and icon(Session(), CT, IDS["share2"])[0] == 200)
check("带密码的分享没输密码：取不到", icon(Session(), BT, IDS["share2"])[0] == 404)
check("瞎写的分享口令：取不到", icon(Session(), "ThisTokenDoesNotExist12345", IDS["share1"])[0] == 404)

section("访问密码")
w = Session()
st, _, pb = w.page("/s/" + BT)
bm = pb["model"] if pb else {}
check("带密码的：先显示输密码", pb and pb["view"] == "share" and bm.get("shareState") == "locked" and bm.get("shareName") == "share2"
      and bm.get("shareBy") == "管理员", (pb and pb.get("view"), bm))
check("输密码之前一点大屏数据都没有", not any(k.startswith("sc") for k in bm), [k for k in bm if k.startswith("sc")])
st, loc, _ = w.call("POST", "/s/" + BT, {"password": "wrong-one"})
check("密码不对：回到输密码、带着 wrong", st == 302 and loc.endswith("/s/" + BT + "?wrong"), (st, loc))
st, _, pw = w.page("/s/" + BT + "?wrong")
check("还是输密码的页面", pw and pw["view"] == "share" and pw["model"].get("shareState") == "locked")
st, loc, _ = w.call("POST", "/s/" + BT, {"password": "pass-1234"})
check("密码对了：回到分享地址", st == 302 and loc.endswith("/s/" + BT), (st, loc))
check("输过密码的浏览器：图标也取得到", icon(w, BT, IDS["share2"])[0] == 200, icon(w, BT, IDS["share2"]))
check("记住了这个浏览器输对过", any(n.startswith("ota_su") for n in cookies(w)), list(cookies(w)))
st, _, pw = w.page("/s/" + BT)
check("再打开直接看到大屏", pw and pw["view"] == "admin/screen" and pw["model"].get("scName") == "share2", pw and pw.get("view"))
check("分享时是浅色主题：带着", pw["model"].get("scShareTheme") == "light" and pw["model"].get("scShareUntil") is None, pw["model"].get("scShareTheme"))
x = Session()
for c in cookies(w).values():
    if c.name.startswith("ota_su"):
        forged = http.cookiejar.Cookie(0, c.name, c.value[:-3] + "AAA", None, False, c.domain, c.domain_specified, c.domain_initial_dot,
                                       c.path, c.path_specified, c.secure, c.expires, c.discard, None, None, {})
        [h for h in x.op.handlers if isinstance(h, urllib.request.HTTPCookieProcessor)][0].cookiejar.set_cookie(forged)
st, _, px = x.page("/s/" + BT)
check("改过的 Cookie 不认", px and px["view"] == "share" and px["model"].get("shareState") == "locked", px and px.get("view"))
y = Session()
ip = {"X-Forwarded-For": "203.0.113.77"}
outs = [raw(y, "POST", "/s/" + BT, {"password": f"bad-{i}"}, ip)[1] for i in range(8)]
check("同一个 IP 连错 8 次：锁住", all(o.endswith("?wrong") for o in outs[:7]) and outs[7].endswith("?locked"), outs)
st, loc, _ = raw(y, "POST", "/s/" + BT, {"password": "pass-1234"}, ip)
check("锁住时密码对了也不行", loc.endswith("?locked") and not any(n.startswith("ota_su") for n in cookies(y)), loc)
st, loc, _ = raw(Session(), "POST", "/s/" + BT, {"password": "pass-1234"}, {"X-Forwarded-For": "203.0.113.78"})
check("别的 IP 不受影响", loc.endswith("/s/" + BT), loc)
st, loc, _ = Session().call("POST", "/s/" + AT, {"password": "anything"})
check("不带密码的分享提交密码：直接回到分享", st == 302 and loc.endswith("/s/" + AT), (st, loc))

section("取消分享")
st, d = jcall(adm, "POST", f"/admin/screen/shares/{AID}/revoke")
check("取消 share1 的分享", st == 200 and d.get("ok") and d["share"]["state"] == "revoked" and d["share"].get("revokedAt"), d)
st, _, pg = v.page("/s/" + AT)
check("开着的大屏下次刷新：显示分享已取消", pg and pg["view"] == "share" and pg["model"].get("shareState") == "revoked", pg and pg.get("model"))
check("取消以后图标也取不到", icon(v, AT, IDS["share1"])[0] == 404)
st, loc, _ = v.call("GET", ASSET)
check("取消了：分享 Cookie 也取不到后台页面脚本了", st in (302, 401, 403), (st, loc))
st, d = jcall(adm, "POST", f"/admin/screen/shares/{AID}/revoke")
check("再取消一次：没事", st == 200 and d.get("ok") and d["share"]["state"] == "revoked", d)
st, d = jcall(adm, "POST", "/admin/screen/shares/999999/revoke")
check("没有这个分享", st == 404 and d.get("ok") is False, (st, d))
st, _, pn = Session().page("/s/ThisTokenDoesNotExist12345")
check("链接抄错了：分享链接不存在", pn and pn["view"] == "share" and pn["model"].get("shareState") == "gone", pn and pn.get("model"))
st, _, pn = Session().page("/s/bad.token")
check("口令格式不对：不是分享页", not (pn and pn.get("view") == "admin/screen"), (st, pn and pn.get("view")))

section("过期")
sql(f"update screen_share set expires_at = dateadd('MINUTE', -5, current_timestamp) where id = {CID}")
st, _, pe = Session().page("/s/" + CT)
check("过期了：显示分享已过期和到期时间", pe and pe["view"] == "share" and pe["model"].get("shareState") == "expired" and pe["model"].get("shareUntil"),
      pe and pe.get("model"))
check("记录里标着已过期", row(adm, CID)["state"] == "expired", row(adm, CID))
st, d = jcall(adm, "GET", "/admin/screen/shares")
check("列表照样能取", st == 200 and len(d["shares"]) == 4)

section("成员：只看、只取消自己的；没权限了分享跟着失效")
apps = {a["slug"]: a["id"] for a in adm.model("/admin/admins")["allApps"]}
st, loc, msg, err = adm.post("/admin/admins/create", {"username": "member1", "name": "成员一", "password": "member-pass-1",
                                                       f"access_{apps['share1']}": "on"}, "/admin/admins")
check("新建成员：只能看 share1", msg and not err, (msg, err))
mem, st, loc = login("member1", "member-pass-1")
check("成员登录", st == 302 and "/admin" in loc, (st, loc))
st, d = share(mem, scope="all")
check("成员只看得到一个 App：不能分享全部", d.get("ok") is False and "一个 App" in d.get("message", ""), d)
st, d = share(mem, app="share2")
check("成员看不到 share2：不能分享", d.get("ok") is False and "没有这个 App" in d.get("message", ""), d)
st, M = share(mem, hours=168)
check("成员分享 share1、7 天", M.get("ok") and M["share"]["createdBy"] == "member1", M)
MT, MID = token_of(M["url"]), M["share"]["id"]
st, d = jcall(mem, "GET", "/admin/screen/shares")
check("成员的分享记录只有自己的", st == 200 and [r["id"] for r in d["shares"]] == [MID] and d.get("owner") is False, d)
st, d = jcall(mem, "POST", f"/admin/screen/shares/{BID}/revoke")
check("成员取消不了别人的", st == 404 and d.get("ok") is False, (st, d))
check("别人的还是有效", row(adm, BID)["state"] == "active")
rm = row(adm, MID)
check("超级管理员看得到成员的、标着不是自己的", rm and rm["mine"] is False and rm["createdName"] == "成员一", rm)
st, _, pm = Session().page("/s/" + MT)
check("成员的分享能打开", pm and pm["view"] == "admin/screen" and pm["model"].get("scName") == "share1")
mid = next(r["user"]["id"] for r in adm.model("/admin/admins")["rows"] if r["user"]["username"] == "member1")
adm.post(f"/admin/admins/{mid}/update", {"username": "member1", "name": "成员一", "password": "", f"access_{apps['share2']}": "on"}, "/admin/admins")
st, _, pm = Session().page("/s/" + MT)
check("成员看不了 share1 了：分享跟着失效", pm and pm["view"] == "share" and pm["model"].get("shareState") == "owner", pm and pm.get("model"))
adm.post(f"/admin/admins/{mid}/update", {"username": "member1", "name": "成员一", "password": "", f"access_{apps['share1']}": "on"}, "/admin/admins")
st, _, pm = Session().page("/s/" + MT)
check("权限给回来：分享又能看了", pm and pm["view"] == "admin/screen")
adm.post(f"/admin/admins/{mid}/toggle", {}, "/admin/admins")
st, _, pm = Session().page("/s/" + MT)
check("成员停用了：分享失效", pm and pm["view"] == "share" and pm["model"].get("shareState") == "owner", pm and pm.get("model"))
st, d = jcall(adm, "POST", f"/admin/screen/shares/{MID}/revoke")
check("超级管理员能取消成员的", d.get("ok") and d["share"]["state"] == "revoked", d)

section("别的身份")
st, d = jcall(Session(), "POST", "/admin/screen/shares", {"scope": "app", "app": "share1", "hours": "24"})
check("没登录不能生成", st in (302, 401, 403) and not d.get("ok"), (st, d))
adm.post("/admin/agents/create", agent_form("分享代理", "shareag", password="pass-sa-123"), "/admin/agents")
ag = logged_in("shareag", "pass-sa-123")
st, d = jcall(ag, "GET", "/admin/screen/shares")
check("代理不能看分享记录", ag and st in (302, 401, 403) and not d.get("ok"), (st, d))

section("操作日志")
lm = adm.model("/admin/logs")
text = json.dumps(lm, ensure_ascii=False)
check("记了分享和取消分享", "SCREEN_SHARE_CREATE" in text and "SCREEN_SHARE_REVOKE" in text and "给老板看的" in text, text[:300])
check("取消两次只记一次", text.count("取消数据大屏分享（share1，管理员 分享的）") == 1, text.count("取消数据大屏分享（share1"))

done()
