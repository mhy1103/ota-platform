"""确认太快：兑换后不到 2 秒就来确认的判为刷的，这条以后怎么确认都不算有效激活，
App 照常能用；后台激活记录单独标「确认太快」、不算待确认，操作日志记一条；代理那边显示「无效用户」（不给毫秒数）。
实例要带 --ota.min-confirm-ms=2000 启动"""
exec(open("/tmp/ota-e2e/e2e_subagent.py").read().split("P_TIERS = ")[0])


def used(code):
    return next(c["usedCount"] for c in mgmt("GET", "/api/v1/codes")[1] if c["code"] == code)


def rec(device):
    return next((r for r in mgmt("GET", "/api/v1/redemptions?app=one&limit=1000")[1] if r["deviceId"] == device), None)


def until(fn, timeout=8):
    t0 = time.time()
    while True:
        v = fn()
        if v or time.time() - t0 > timeout:
            return v
        time.sleep(0.3)


section("准备：App、代理、口令")
mgmt("POST", "/api/v1/apps", {"slug": "one", "name": "one"})
mgmt("POST", "/api/v1/apps/one/redemption", {"open": True})
KEYS = mgmt("GET", "/api/v1/apps/one/codes-key")[1]
adm, st, loc = login("admin", "admin-pass-123")
check("管理员登录", st == 302, loc)
adm.post("/admin/agents/create", agent_form("防刷代理", "fc1", password="pass-fc1-123"), "/admin/agents")
AID = admin_row("fc1")["agent"]["id"]
st, body = mgmt("POST", "/api/v1/codes", {"code": "FC-1", "maxUses": 10, "agentId": AID})
check("口令分给代理", st == 201 and body, body)

section("新接口：兑换完马上确认，不算有效激活")
r = client(KEYS, "/redeem", {"code": "FC-1", "deviceId": "fast-1"})
c = client(KEYS, "/confirm", {"grant": r["grant"]})
check("照常回 ok，不说为什么", c.get("ok") is True and c.get("confirmed") is False, c)
check("不算有效激活、不占口令次数", used("FC-1") == 0 and rec("fast-1")["confirmed"] is False, rec("fast-1"))
check("记下兑换后隔了多少毫秒", isinstance(rec("fast-1").get("fastMs"), int) and 0 <= rec("fast-1")["fastMs"] < 2000, rec("fast-1"))
time.sleep(2.3)
c2 = client(KEYS, "/confirm", {"grant": r["grant"]})
check("过了 2 秒再确认也不算", c2.get("confirmed") is False and used("FC-1") == 0 and rec("fast-1")["confirmed"] is False, (c2, rec("fast-1")))
check("App 照常能用：授权校验有效", client(KEYS, "/verify", {"grant": r["grant"]}).get("valid") is True)
r3 = client(KEYS, "/redeem", {"code": "FC-1", "deviceId": "fast-1"})
check("同一台设备再兑换：还是原来那条", r3.get("ok") is True and r3.get("existing") is True, r3)
time.sleep(2.1)
client(KEYS, "/confirm", {"grant": r3["grant"]})
check("拿新授权确认还是不算", used("FC-1") == 0 and rec("fast-1")["confirmed"] is False, rec("fast-1"))

section("新接口：正常速度（兑换后过了 2 秒才确认）照常算")
r = client(KEYS, "/redeem", {"code": "FC-1", "deviceId": "slow-1"})
time.sleep(2.2)
c = client(KEYS, "/confirm", {"grant": r["grant"]})
check("算有效激活", c.get("confirmed") is True and used("FC-1") == 1 and rec("slow-1").get("fastMs") is None, (c, rec("slow-1")))

section("后台：单独标「确认太快」，不算待确认")
m = adm.model("/admin/activations")
check("顶部：有效 1、待确认 0、确认太快 1", (m.get("confirmedTotal"), m.get("pendingTotal"), m.get("fastTotal")) == (1, 0, 1),
      (m.get("confirmedTotal"), m.get("pendingTotal"), m.get("fastTotal")))
fm = adm.model("/admin/activations?status=fast")
rows = fm["records"]["content"]
check("「确认太快」筛出这一条，带着毫秒数", sorted(x["deviceId"] for x in rows) == ["fast-1"] and all(x.get("fastMs") is not None for x in rows), rows)
check("筛选结果拆成已确认 1、待确认 0、确认太快 1", (fm.get("matched"), fm.get("matchedConfirmed"), fm.get("matchedPending"), fm.get("matchedFast")) == (2, 1, 0, 1),
      (fm.get("matched"), fm.get("matchedConfirmed"), fm.get("matchedPending"), fm.get("matchedFast")))
check("「待确认」里没有它们", adm.model("/admin/activations?status=pending")["records"]["totalElements"] == 0)
check("管理接口按 fast 也能筛", sorted(x["deviceId"] for x in mgmt("GET", "/api/v1/redemptions?app=one&status=fast")[1]) == ["fast-1"]
      and mgmt("GET", "/api/v1/redemptions?app=one&status=pending")[1] == [])
st, _, csv = adm.call("GET", "/admin/activations/export")
check("导出里写着非正常", st == 200 and csv.count("非正常") == 1 and csv.count("已确认") == 1, csv[:400])
logs = [x for x in adm.model("/admin/logs")["logs"]["content"] if x.get("action") == "CONFIRM_FAST"]
check("操作日志记一条（再确认不重复记）", len(logs) == 1 and all("毫秒就来确认" in x.get("detail", "") for x in logs), logs)
recent = adm.model(f"/admin/agents/{AID}").get("recent", [])
check("代理详情的最近激活也标出来", sum(1 for x in recent if x.get("fast")) == 1 and sum(1 for x in recent if x.get("confirmed")) == 1, recent)

section("代理那边显示「非正常」")
ag = logged_in("fc1", "pass-fc1-123")
check("代理登录", ag is not None)
st, _, page = ag.page("/agent/records")
recs = page["model"]["records"]["content"] if st == 200 else []
check("一条非正常、一条已确认", sum(1 for x in recs if x.get("fast")) == 1 and sum(1 for x in recs if x.get("confirmed")) == 1, recs)
check("只给是不是，不给毫秒数和指纹", "fastMs" not in json.dumps(page, ensure_ascii=False) and "fingerprint" not in json.dumps(page, ensure_ascii=False))
check("按「非正常」筛出一条，「待确认」里没有", ag.model("/agent/records?status=fast")["records"]["totalElements"] == 1
      and ag.model("/agent/records?status=pending")["records"]["totalElements"] == 0)
st, _, csv = ag.call("GET", "/agent/records/export")
check("代理导出里写着非正常", st == 200 and csv.count("非正常") == 1 and "确认太快" not in csv, csv[:300])
st, _, home = ag.page("/agent")
check("概览的数据里也没有毫秒数", st == 200 and "fastMs" not in json.dumps(home, ensure_ascii=False), st)

section("日活不算刷的设备")
act = until(lambda: adm.model(f"/admin/agents/{AID}/active").get("actToday") == 1)
check("今天打开过的只算真激活的那一台（刷的那台校验过也不算）", act, adm.model(f"/admin/agents/{AID}/active").get("actToday"))

section("重复：别的 App 同一台手机（指纹）已经算过的，代理那边也是无效用户")
mgmt("POST", "/api/v1/apps", {"slug": "two", "name": "two"})
mgmt("POST", "/api/v1/apps/two/redemption", {"open": True})
mgmt("POST", "/api/v1/apps/two/dedupe", {"open": True})
K2 = mgmt("GET", "/api/v1/apps/two/codes-key")[1]
ra = client(KEYS, "/redeem", {"code": "FC-1", "deviceId": "x-a"})
time.sleep(2.2)
check("App 甲激活：算", client(KEYS, "/confirm", {"grant": ra["grant"], "init": "1631000000123", "model": "iPhone15,2"}).get("confirmed") is True and used("FC-1") == 2)
rb = client(K2, "/redeem", {"code": "FC-1", "deviceId": "x-b"})
time.sleep(2.2)
check("App 乙同一台手机（同指纹）：不算", client(K2, "/confirm", {"grant": rb["grant"], "init": "1631000000123", "model": "iPhone15,2"}).get("confirmed") is False and used("FC-1") == 2)
check("代理看到一条非正常、一条重复", ag.model("/agent/records?status=fast")["records"]["totalElements"] == 1
      and ag.model("/agent/records?status=dup")["records"]["totalElements"] == 1)
dm = adm.model("/admin/activations?status=dup")
check("管理员那边单独是「重复」，按指纹认出来", dm.get("dupTotal") == 1 and dm.get("fastTotal") == 1 and [x["deviceId"] for x in dm["records"]["content"]] == ["x-b"]
      and dm["records"]["content"][0].get("dupBy") == "fp", (dm.get("dupTotal"), dm["records"]["content"]))

done()
