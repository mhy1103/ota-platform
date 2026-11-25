"""二级代理：管理员给代理开权限（可限个数），代理在代理后台开二级代理、分口令、设扣量和数据更新方式。
数字一层层算：管理员看真实的，一级代理按自己的扣量看，二级代理在上级看到的基础上再按自己的扣量看。
实例：全新安装（install_fresh.py），管理令牌 test-admin-token。OTA_BASE=... python3 e2e_subagent.py"""
import hashlib
import hmac
import http.cookiejar
import uuid

from lib import *


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


class Session:
    """网页会话：直接发表单，不经过页面渲染；json() 拿页面数据（view、model）"""

    def __init__(self):
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())

    def call(self, method, path, data=None):
        body = urllib.parse.urlencode(data or {}, doseq=True).encode() if method == "POST" else None
        h = {"Content-Type": "application/x-www-form-urlencoded"} if method == "POST" else {}
        req = urllib.request.Request(BASE + path, data=body, method=method, headers=h)
        try:
            with self.op.open(req, timeout=60) as r:
                return r.status, r.headers.get("Location") or "", r.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            return e.code, e.headers.get("Location") or "", e.read().decode("utf-8", "replace")

    def page(self, path):
        req = urllib.request.Request(BASE + path, headers={"X-Requested-With": "XMLHttpRequest", "Accept": "application/json"})
        try:
            with self.op.open(req, timeout=60) as r:
                return r.status, r.headers.get("Location") or "", json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            return e.code, e.headers.get("Location") or "", None

    def model(self, path):
        st, loc, d = self.page(path)
        return d["model"] if d and st == 200 else {"_status": st, "_loc": loc}

    def post(self, path, data, back):
        """提交表单，再打开跳回的页面读一次性提示"""
        st, loc, _ = self.call("POST", path, data)
        m = self.model(back)
        return st, loc, m.get("msg"), m.get("error")


def login(username, password):
    s = Session()
    s.call("GET", "/login")
    st, loc, _ = s.call("POST", "/login", {"username": username, "password": password})
    return s, st, loc


def logged_in(username, password):
    s, st, loc = login(username, password)
    return s if st == 302 and "/agent" in loc else None


def mgmt(method, path, body=None):
    r = api(method, path, body)
    return r.status, (r.json() if r.body else None)


def client(keys, path, body):
    raw = json.dumps(body)
    ts, nonce = str(int(time.time() * 1000)), uuid.uuid4().hex
    sig = hmac.new(keys["secret"].encode(), f"{ts}\n{nonce}\n{raw}".encode(), hashlib.sha256).hexdigest()
    h = {"Content-Type": "application/json", "X-App-Key": keys["appKey"], "X-Timestamp": ts, "X-Nonce": nonce, "X-Signature": sig}
    return json.loads(plain("POST", "/v1/codes" + path, headers=h, data=raw.encode()).body)


def activate(code, device):
    r = client(KEYS, "/redeem", {"code": code, "deviceId": device})
    return r.get("ok") is True and client(KEYS, "/confirm", {"grant": r["grant"]}).get("confirmed") is True


def agent_form(name, username, mode="REALTIME", view=True, tiers=(), subs=False, limit="", password=""):
    d = {"name": name, "username": username, "password": password, "requestMode": "OFF", "dailyLimit": "",
         "canViewRecords": "true" if view else "false", "dataMode": mode, "remark": "管理员写的备注-" + username,
         "subAgents": "true" if subs else "false", "subLimit": limit}
    if tiers:
        d["tierMin"] = [str(t[0]) for t in tiers]
        d["tierMax"] = [str(t[1]) for t in tiers]
        d["tierRatio"] = [str(t[2]) for t in tiers]
    return d


def sub_form(name, username, password="", mode="REALTIME", view=False, tiers=(), remark=""):
    d = {"name": name, "username": username, "password": password, "canViewRecords": "true" if view else "false",
         "dataMode": mode, "remark": remark}
    if tiers:
        d["tierMin"] = [str(t[0]) for t in tiers]
        d["tierMax"] = [str(t[1]) for t in tiers]
        d["tierRatio"] = [str(t[2]) for t in tiers]
    return d


def row_of(model, username):
    return next((r for r in model.get("subs", []) if r["agent"]["username"] == username), None)


def admin_row(username):
    rows = adm.model("/admin/agents").get("rows", [])
    return next((r for r in rows if r["agent"]["username"] == username), None)


def admin_codes():
    c = adm.model("/admin/codes")["codes"]
    return c["content"] if isinstance(c, dict) else c


P_TIERS = [(1, 10, 20)]

section("准备：App、管理员登录、三个一级代理")
mgmt("POST", "/api/v1/apps", {"slug": "subapp", "name": "subapp"})
mgmt("POST", "/api/v1/apps/subapp/redemption", {"open": True})
KEYS = mgmt("GET", "/api/v1/apps/subapp/codes-key")[1]
check("App 和口令密钥", KEYS and KEYS.get("appKey"), KEYS)
adm, st, loc = login("admin", "admin-pass-123")
check("管理员登录", st == 302 and "/admin" in loc, loc)

st, loc, msg, err = adm.post("/admin/agents/create", agent_form("甲代理", "ppp1", tiers=P_TIERS, subs=True, limit="2", password="pass-p1-123"), "/admin/agents")
check("新建代理甲：开二级代理、最多 2 个", st == 302 and msg and "甲代理" in msg and not err, (st, msg, err))
adm.post("/admin/agents/create", agent_form("乙代理", "qqq1", mode="DAILY", view=False, subs=True, password="pass-q1-123"), "/admin/agents")
adm.post("/admin/agents/create", agent_form("丙代理", "rrr1", password="pass-r1-123"), "/admin/agents")
P, Q, R = admin_row("ppp1"), admin_row("qqq1"), admin_row("rrr1")
check("三个代理都在列表里", P and Q and R, [r["agent"]["username"] for r in adm.model("/admin/agents").get("rows", [])])
PID, QID = P["agent"]["id"], Q["agent"]["id"]
check("列表里标着能开二级代理、最多 2 个", P["agent"].get("canManageSubs") is True and P["agent"].get("subAgentLimit") == 2
      and R["agent"].get("canManageSubs") is False, P["agent"])
st, _, _, err = adm.post("/admin/agents/create", agent_form("丁", "ddd1", subs=True, limit="5000", password="pass-d1-123"), "/admin/agents")
check("个数上限超出 1000：拒绝", err and "1000" in err and admin_row("ddd1") is None, err)

section("代理后台：有权限的才看得到「二级代理」")
p = logged_in("ppp1", "pass-p1-123")
q = logged_in("qqq1", "pass-q1-123")
r = logged_in("rrr1", "pass-r1-123")
check("三个代理都能登录", p and q and r)
m = p.model("/agent/subs")
check("甲打开二级代理页", m.get("subs") == [] and m.get("subLimit") == 2 and m.get("canView") is True
      and m.get("modes") == ["REALTIME", "DAILY", "MANUAL"], m)
check("页面数据里没有甲自己的扣量和管理员备注", "deductionTiers" not in m["agent"] and "remark" not in m["agent"], m["agent"])
check("顶栏能看到「二级代理」", m["agent"].get("canManageSubs") is True and m["agent"].get("sub") is False)
st, loc, _ = r.page("/agent/subs")
check("丙没开权限：打开二级代理页回到概览", st == 302 and loc.endswith("/agent"), (st, loc))
st, loc, _, err = r.post("/agent/subs/create", sub_form("丙的下级", "r1sub", password="pass-123456"), "/agent")
check("丙直接提交新建：拒绝", err and "没有给你开二级代理" in err and logged_in("r1sub", "pass-123456") is None, err)
check("乙是每日 0 点：二级代理只能选每日 0 点和手动", q.model("/agent/subs").get("modes") == ["DAILY", "MANUAL"])

section("开二级代理：校验")
st, loc, msg, err = p.post("/agent/subs/create", sub_form("一号", "sub1", password="sub-pass-1", view=True, remark="甲的备注"), "/agent/subs")
check("开一号（能看记录、实时、不扣量）", st == 302 and msg and "一号" in msg and "sub1" in msg and not err, (msg, err))
st, loc, msg, err = p.post("/agent/subs/create", sub_form("二号", "sub2", password="sub-pass-2", tiers=[(1, "", 50)]), "/agent/subs")
check("开二号（仅看数量、扣 50%）", msg and "二号" in msg and not err, (msg, err))
_, _, _, err = p.post("/agent/subs/create", sub_form("三号", "sub3", password="sub-pass-3"), "/agent/subs")
check("第三个：超出 2 个的上限", err and "最多开 2 个" in err, err)
check("没开出第三个", len(p.model("/agent/subs")["subs"]) == 2 and logged_in("sub3", "sub-pass-3") is None)
_, _, _, err = q.post("/agent/subs/create", sub_form("乙下", "qqq1a", password="pass-123456", mode="REALTIME"), "/agent/subs")
check("乙每日 0 点：开实时的二级代理被拒", err and "不能比你自己的新" in err, err)
_, _, _, err = q.post("/agent/subs/create", sub_form("乙下", "qqq1a", password="pass-123456", mode="DAILY", view=True), "/agent/subs")
check("乙看不了设备记录：二级代理也开不了", err and "设备记录" in err, err)
_, _, msg, err = q.post("/agent/subs/create", sub_form("乙下", "qqq1a", password="pass-123456", mode="DAILY"), "/agent/subs")
check("乙开一个每日 0 点、仅看数量的：成功", msg and not err, (msg, err))
for uname, why in (("ppp1", "和代理重名"), ("admin", "和管理员重名"), ("SUB1", "大小写不同也算重名")):
    _, _, _, err = q.post("/agent/subs/create", sub_form("重名", uname, password="pass-123456", mode="DAILY"), "/agent/subs")
    check(f"登录账号{why}：拒绝", err and "已被使用" in err, err)
_, _, _, err = q.post("/agent/subs/create", sub_form("短密码", "sub9", password="123", mode="DAILY"), "/agent/subs")
check("密码太短：拒绝", err and "6–64" in err, err)
_, _, _, err = q.post("/agent/subs/create", sub_form("", "sub9", password="pass-123456", mode="DAILY"), "/agent/subs")
check("没填名称：拒绝", err and "名称" in err, err)
check("乙那边只有成功开出的那一个", [x["agent"]["username"] for x in q.model("/agent/subs")["subs"]] == ["qqq1a"])

m = p.model("/agent/subs")
S1, S2 = row_of(m, "sub1"), row_of(m, "sub2")
S1ID, S2ID = S1["agent"]["id"], S2["agent"]["id"]
check("二级代理行带着自己的扣量和备注（给上级编辑用）", S1["note"] == "甲的备注" and S2["tiers"] and "50" in S2["tiers"] and S1["tiers"] == "[]", (S1["note"], S2["tiers"]))
check("二级代理对象本身不带扣量、备注字段", "deductionTiers" not in S1["agent"] and "remark" not in S1["agent"], S1["agent"])

section("分口令")
ids = {}
for c in ("SA-C1", "SA-C2", "SA-C3"):
    st, body = mgmt("POST", "/api/v1/codes", {"code": c, "maxUses": 100, "agentId": PID})
    ids[c] = body[0]["id"] if st == 201 and body else None
st, body = mgmt("POST", "/api/v1/codes", {"code": "SA-Q9", "maxUses": 100, "agentId": QID})
ids["SA-Q9"] = body[0]["id"] if body else None
check("给甲建 3 个口令、给乙 1 个", all(ids.values()), ids)
st, body = mgmt("POST", "/api/v1/codes", {"code": "SA-X", "maxUses": 1, "agentId": S1ID})
check("接口不能把口令直接分给二级代理", st == 400 and body and body.get("error") == "SUB_AGENT", (st, body))
_, _, msg, err = p.post(f"/agent/subs/{S1ID}/codes", {"give": [ids["SA-C1"]]}, "/agent/subs")
check("甲把 C1 分给一号", msg and "分了 1 个口令" in msg and not err, (msg, err))
_, _, msg, err = p.post(f"/agent/subs/{S2ID}/codes", {"give": [ids["SA-C2"]]}, "/agent/subs")
check("甲把 C2 分给二号", msg and not err, (msg, err))
_, _, _, err = p.post(f"/agent/subs/{S2ID}/codes", {"give": [ids["SA-C1"]]}, "/agent/subs")
check("C1 已经给了一号：不能再给二号", err and "已经分给别的二级代理" in err, err)
_, _, _, err = p.post(f"/agent/subs/{S2ID}/codes", {"give": [ids["SA-Q9"]]}, "/agent/subs")
check("别人的口令不能分", err and "没有选口令" in err, err)
_, _, _, err = q.post(f"/agent/subs/{S1ID}/codes", {"give": [ids["SA-Q9"]]}, "/agent/subs")
check("乙动不了甲的二级代理", err and "没有这个二级代理" in err, err)
m = p.model("/agent/subs")
check("页面数：已分出 2 个，手里还有 1 个", m["givenCount"] == 2 and m["freeUsable"] == 1 and [c["code"] for c in m["freeCodes"]] == ["SA-C3"], (m["givenCount"], m["freeUsable"]))
check("一号的口令只有 C1", [c["code"] for c in row_of(m, "sub1")["codes"]] == ["SA-C1"])
cm = adm.model("/admin/codes")
c1 = next(c for c in admin_codes() if c["code"] == "SA-C1")
check("管理员口令列表：C1 归甲、标着分给了一号", c1.get("agentId") == PID and c1.get("subAgentId") == S1ID, c1)
check("管理员口令页的代理下拉只有一级代理", {a["name"] for a in cm["agents"]} == {"甲代理", "乙代理", "丙代理"}, [a.get("name") for a in cm["agents"]])
po = p.model("/agent")
check("甲的概览：分给二级代理的口令标着给了谁", dict(po["subNames"]["$m"]).get(str(S1ID)) == "一号", po.get("subNames"))

section("激活：一层层扣量")
ok = all(activate("SA-C1", f"d{i}") for i in range(1, 5)) and all(activate("SA-C2", f"d{i}") for i in range(5, 9)) \
     and all(activate("SA-C3", f"d{i}") for i in range(9, 11))
check("C1×4、C2×4、C3×2 都激活确认了", ok)
check("管理员看到甲：真实 10", admin_row("ppp1")["total"] == 10, admin_row("ppp1"))
check("甲看到 8（当天 10 台扣 20%，扣的 2 台按比例分到一号、二号各 1 台）", p.model("/agent")["total"] == 8 and admin_row("ppp1")["visible"] == 8)
m = p.model("/agent/subs")
S1, S2 = row_of(m, "sub1"), row_of(m, "sub2")
check("甲的二级代理页：一号 今日 3 / 累计 3 / TA 看到 3（4 台按 20% 分到 1 台）", (S1["today"], S1["total"], S1["seen"]) == (3, 3, 3), S1)
check("二号 今日 3 / 累计 3 / TA 看到 2（再扣 50%）", (S2["today"], S2["total"], S2["seen"]) == (3, 3, 2), S2)
check("代理端页面数据里根本没有真实数", "real" not in S1 and "real" not in S2, sorted(S1))
check("汇总：今日 6、累计 6（甲自己手里的 C3 两台没扣）", m["subToday"] == 6 and m["subTotal"] == 6, (m["subToday"], m["subTotal"]))
po = p.model("/agent")
check("甲自己的 C3 两台都在", dict(po["totalByCode"]["$m"]).get(str(ids["SA-C3"])) == 2, po.get("totalByCode"))
s1 = logged_in("sub1", "sub-pass-1")
s2 = logged_in("sub2", "sub-pass-2")
check("一号、二号都能登录", s1 and s2)
o1 = s1.model("/agent")
check("一号概览：累计 3、只有 C1", o1["total"] == 3 and [c["code"] for c in o1["codes"]] == ["SA-C1"], (o1.get("total"), o1.get("codes")))
check("一号是二级代理：不能申请口令、没有公告提醒", o1["agent"]["sub"] is True and o1["agent"]["canRequest"] is False and o1.get("noticeBanner") is None)
check("一号的页面数据里没有扣量和备注", "deductionTiers" not in o1["agent"] and "remark" not in o1["agent"])
o2 = s2.model("/agent")
check("二号概览：累计 2", o2["total"] == 2, o2.get("total"))
rec = s1.model("/agent/records")
check("一号能看记录：3 条（扣掉的那台不列）", rec.get("records", {}).get("totalElements") == 3, rec.get("records", {}).get("totalElements"))
st, loc, _ = s2.page("/agent/records")
check("二号仅看数量：打不开记录", st == 302 and loc.endswith("/agent"), (st, loc))
st, loc, _ = s1.page("/agent/subs")
check("一号打不开 /agent/subs", st == 302 and loc.endswith("/agent"), (st, loc))
st, loc, _ = s1.page("/agent/notices")
check("一号能打开公告", st == 200, (st, loc))
_, _, _, err = s1.post("/agent/subs/create", sub_form("孙", "sub1x", password="pass-123456"), "/agent")
check("二级代理不能再开下级", err and "没有给你开二级代理" in err and logged_in("sub1x", "pass-123456") is None, err)
st, _, body = s1.call("GET", "/agent/records/export")
lines = [x for x in body.strip().splitlines() if x.strip()]
check("一号导出记录：表头 + 3 行", st == 200 and len(lines) == 4, (st, len(lines)))

section("预览：上级和管理员看到的就是 TA 看到的")
pv = p.model(f"/agent/subs/{S2ID}/preview")
check("甲以二号视角预览：累计 2，返回二级代理", pv.get("total") == 2 and pv.get("preview") is True and pv.get("previewBack") == "/agent/subs", pv.get("previewBack"))
check("甲预览二号的账号页", p.model(f"/agent/subs/{S2ID}/preview/account").get("parentName") == "甲代理")
st, loc, _ = q.page(f"/agent/subs/{S1ID}/preview")
check("乙预览不了甲的二级代理", st == 302 and loc.endswith("/agent/subs"), (st, loc))
av = adm.model(f"/admin/agents/{S2ID}/preview")
check("管理员预览二号：累计 2，返回甲的详情", av.get("total") == 2 and av.get("previewBack") == f"/admin/agents/{PID}#subs", av.get("previewBack"))
asb = adm.model(f"/admin/agents/{PID}/preview/subs")
check("管理员预览甲的二级代理页", asb.get("preview") is True and len(asb.get("subs", [])) == 2)
det = adm.model(f"/admin/agents/{PID}")
d1, d2 = row_of(det, "sub1"), row_of(det, "sub2")
check("管理员详情：二号 真实 4 / 甲看到 3 / 二号看到 2", (d2["real"], d2["total"], d2["seen"]) == (4, 3, 2), d2)
check("管理员详情：一号 真实 4 / 3 / 3", (d1["real"], d1["total"], d1["seen"]) == (4, 3, 3), d1)
st, loc, _ = adm.page(f"/admin/agents/{S1ID}")
check("管理员打开二级代理的详情：跳到上级的", st == 302 and loc.endswith(f"/admin/agents/{PID}#subs"), (st, loc))
adm.page(loc.split("#")[0])
st, loc, _, err = adm.post(f"/admin/agents/{S1ID}/update", agent_form("改", "sub1"), f"/admin/agents/{PID}")
check("管理员不能直接改二级代理的设置", err and "是二级代理" in err and loc.endswith(f"/admin/agents/{PID}#subs"), (st, loc, err))
check("一号的设置没被改", row_of(adm.model(f"/admin/agents/{PID}"), "sub1")["agent"]["name"] == "一号")

section("手动更新：数据停在上级更新的时间")
_, _, msg, err = p.post(f"/agent/subs/{S1ID}/update", sub_form("一号", "sub1", mode="MANUAL", view=True, remark="甲的备注"), "/agent/subs")
check("一号改成手动更新", msg and not err, (msg, err))
time.sleep(1.2)
check("再激活 2 台 C1", activate("SA-C1", "d11") and activate("SA-C1", "d12"))
check("甲看到 12（当天 12 台，不在档位里不扣）", p.model("/agent")["total"] == 12)
o1 = s1.model("/agent")
check("一号还是 3，数据停在改成手动的那一刻（那时当天 10 台、按 20% 扣）", o1["total"] == 3 and o1["mode"] == "MANUAL" and o1["cutoff"] is not None, (o1["total"], o1["mode"]))
check("甲那边：一号累计 6、TA 看到 3", (row_of(p.model("/agent/subs"), "sub1")["total"], row_of(p.model("/agent/subs"), "sub1")["seen"]) == (6, 3))
_, _, msg, err = p.post(f"/agent/subs/{S1ID}/refresh", {}, "/agent/subs")
check("甲点更新数据", msg and "一号" in msg and not err, (msg, err))
check("一号看到 6", s1.model("/agent")["total"] == 6)
_, _, msg, err = p.post(f"/agent/subs/{S2ID}/refresh", {}, "/agent/subs")
check("二号是实时的：不用更新", err and "不是手动更新" in err, err)
_, _, msg, err = p.post("/agent/subs/refresh-all", {}, "/agent/subs")
check("更新全部手动二级代理：1 个", msg and "1 个" in msg, (msg, err))

section("上级改成每日 0 点：下面跟着变")
adm.post(f"/admin/agents/{PID}/update", agent_form("甲代理", "ppp1", mode="DAILY", tiers=P_TIERS, subs=True, limit="2"), "/admin/agents")
o2 = s2.model("/agent")
check("二号自己是实时，实际按上级的每日 0 点：今天的都还看不到", o2["mode"] == "DAILY" and o2["total"] == 0 and o2["cutoff"] is not None, (o2["mode"], o2["total"]))
check("甲能给二级代理选的只剩每日 0 点和手动", p.model("/agent/subs")["modes"] == ["DAILY", "MANUAL"])
_, _, _, err = p.post(f"/agent/subs/{S2ID}/update", sub_form("二号", "sub2", mode="REALTIME", tiers=[(1, "", 50)]), "/agent/subs")
check("这时候二号再保存成实时：拒绝", err and "不能比你自己的新" in err, err)
p.post(f"/agent/subs/{S1ID}/refresh", {}, "/agent/subs")
o1 = s1.model("/agent")
check("一号手动更新最多只能到上级的今天 0 点", o1["total"] == 0, o1["total"])
adm.post(f"/admin/agents/{PID}/update", agent_form("甲代理", "ppp1", tiers=P_TIERS, subs=True, limit="2"), "/admin/agents")
check("甲改回实时：二号又是实时、看到 2", s2.model("/agent")["mode"] == "REALTIME" and s2.model("/agent")["total"] == 2)

section("停用、收回权限：二级代理登录不了")
_, _, msg, err = p.post(f"/agent/subs/{S2ID}/toggle", {}, "/agent/subs")
check("甲停用二号", msg and "已停用" in msg, (msg, err))
st, loc, _ = s2.page("/agent")
check("二号已登录的会话马上失效", st == 302 and "/login" in loc, (st, loc))
check("二号重新登录：不行", logged_in("sub2", "sub-pass-2") is None)
p.post(f"/agent/subs/{S2ID}/toggle", {}, "/agent/subs")
s2 = logged_in("sub2", "sub-pass-2")
check("甲启用二号：又能登录", s2 is not None)
_, _, msg, err = adm.post(f"/admin/agents/{PID}/update", agent_form("甲代理", "ppp1", tiers=P_TIERS, subs=False), "/admin/agents")
check("管理员关掉甲的二级代理权限：提示下面 2 个暂时不能登录", msg and "2 个二级代理暂时不能登录" in msg, msg)
st, loc, _ = s1.page("/agent")
check("一号会话失效", st == 302 and "/login" in loc, (st, loc))
check("一号登录不了", logged_in("sub1", "sub-pass-1") is None)
st, loc, _ = p.page("/agent/subs")
check("甲的二级代理页也关了", st == 302 and loc.endswith("/agent"), (st, loc))
det = adm.model(f"/admin/agents/{PID}")
check("管理员详情里还列着，状态是暂停", len(det["subs"]) == 2 and all(x["active"] is False for x in det["subs"]))
adm.post(f"/admin/agents/{PID}/update", agent_form("甲代理", "ppp1", tiers=P_TIERS, subs=True, limit="2"), "/admin/agents")
s1 = logged_in("sub1", "sub-pass-1")
o1 = s1.model("/agent") if s1 else {}
check("重新打开：一号能登录，口令还在（手动更新停在今天 0 点）", s1 is not None and [c["code"] for c in o1.get("codes", [])] == ["SA-C1"]
      and o1.get("total") == 0, (o1.get("total"), o1.get("codes")))
p.post(f"/agent/subs/{S1ID}/refresh", {}, "/agent/subs")
check("甲再更新一次：一号看到 6", s1.model("/agent")["total"] == 6)
_, _, msg, _ = adm.post(f"/admin/agents/{PID}/toggle", {}, "/admin/agents")
check("管理员停用甲：提示二级代理也登录不了", msg and "2 个二级代理也登录不了" in msg, msg)
check("甲停用时一号登录不了", logged_in("sub1", "sub-pass-1") is None)
adm.post(f"/admin/agents/{PID}/toggle", {}, "/admin/agents")
st, _, _, err = adm.post(f"/admin/agents/{PID}/update", agent_form("甲代理", "ppp1", view=False, tiers=P_TIERS, subs=True, limit="2"), "/admin/agents")
s1 = logged_in("sub1", "sub-pass-1")
st, loc, _ = s1.page("/agent/records")
check("甲不能看记录了：一号也跟着不能看", st == 302 and loc.endswith("/agent"), (st, loc))
adm.post(f"/admin/agents/{PID}/update", agent_form("甲代理", "ppp1", tiers=P_TIERS, subs=True, limit="2"), "/admin/agents")

section("删除")
_, _, msg, err = p.post(f"/agent/subs/{S2ID}/delete", {}, "/agent/subs")
check("甲删掉二号：口令回到甲手里", msg and "1 个口令回到你手里" in msg, (msg, err))
m = p.model("/agent/subs")
check("C2 回到甲手里，二号不在了", sorted(c["code"] for c in m["freeCodes"]) == ["SA-C2", "SA-C3"] and row_of(m, "sub2") is None)
check("二号登录不了", logged_in("sub2", "sub-pass-2") is None)
check("甲的数据不变（激活记录都还在）", p.model("/agent")["total"] == 12)
st, loc, msg, err = adm.post(f"/admin/agents/{S1ID}/delete", {}, f"/admin/agents/{PID}")
check("管理员删掉一号：口令收回给甲，回到甲的详情", loc.endswith(f"/admin/agents/{PID}#subs") and msg and "收回给 甲代理" in msg, (loc, msg))
p.post("/agent/subs/create", sub_form("三号", "sub3", password="sub-pass-3"), "/agent/subs")
S3ID = row_of(p.model("/agent/subs"), "sub3")["agent"]["id"]
p.post(f"/agent/subs/{S3ID}/codes", {"give": [ids["SA-C1"]]}, "/agent/subs")
_, _, msg, err = adm.post(f"/admin/agents/{PID}/delete", {"purge": "false"}, "/admin/agents")
check("管理员删掉甲：二级代理一起删", msg and "1 个二级代理一起删除" in msg, (msg, err))
check("三号登录不了", logged_in("sub3", "sub-pass-3") is None)
c1 = next(c for c in admin_codes() if c["code"] == "SA-C1")
check("C1 变成未分配，也不再标着分给谁", c1.get("agentId") is None and c1.get("subAgentId") is None, c1)

section("操作日志")
logs = adm.model("/admin/logs")
text = json.dumps(logs, ensure_ascii=False)
for action in ("SUB_AGENT_CREATE", "SUB_AGENT_CODES", "SUB_AGENT_REFRESH", "SUB_AGENT_TOGGLE", "SUB_AGENT_DELETE", "SUB_AGENT_UPDATE"):
    check(f"日志有 {action}", action in text)
check("操作人记成「代理：甲代理」", "代理：甲代理" in text)

done()
