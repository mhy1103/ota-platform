"""给界面检查造数据：一个开了二级代理的代理、三个二级代理（不同设置）、分好的口令和一些激活。
OTA_BASE=... python3 seed_subagent_ui.py"""
exec(open("/tmp/ota-e2e/e2e_subagent.py").read().split("P_TIERS = ")[0])

mgmt("POST", "/api/v1/apps", {"slug": "demoapp", "name": "演示 App"})
mgmt("POST", "/api/v1/apps/demoapp/redemption", {"open": True})
KEYS = mgmt("GET", "/api/v1/apps/demoapp/codes-key")[1]
adm, _, _ = login("admin", "admin-pass-123")
adm.post("/admin/agents/create", agent_form("华东总代", "hd01", tiers=[(1, 10, 20)], subs=True, limit="5", password="pass-hd-123"), "/admin/agents")
adm.post("/admin/agents/create", agent_form("华南代理", "hn01", password="pass-hn-123"), "/admin/agents")
HD = admin_row("hd01")["agent"]["id"]
hd = logged_in("hd01", "pass-hd-123")
hd.post("/agent/subs/create", sub_form("杭州分销", "hz01", password="pass-hz-123", view=True, remark="西湖区门店"), "/agent/subs")
hd.post("/agent/subs/create", sub_form("苏州分销", "sz01", password="pass-sz-123", mode="DAILY", tiers=[(1, 20, 30)]), "/agent/subs")
hd.post("/agent/subs/create", sub_form("宁波分销", "nb01", password="pass-nb-123", mode="MANUAL"), "/agent/subs")
m = hd.model("/agent/subs")
ids = {}
for i in range(1, 11):
    c = f"HD{i:04d}"
    st, body = mgmt("POST", "/api/v1/codes", {"code": c, "maxUses": 50 if i % 3 else 0, "agentId": HD})
    ids[c] = body[0]["id"]
hz, sz, nb = (row_of(m, u)["agent"]["id"] for u in ("hz01", "sz01", "nb01"))
hd.post(f"/agent/subs/{hz}/codes", {"give": [ids["HD0001"], ids["HD0002"]]}, "/agent/subs")
hd.post(f"/agent/subs/{sz}/codes", {"give": [ids["HD0003"], ids["HD0004"]]}, "/agent/subs")
hd.post(f"/agent/subs/{nb}/codes", {"give": [ids["HD0005"]]}, "/agent/subs")
n = 0
for code, k in (("HD0001", 5), ("HD0002", 2), ("HD0003", 3), ("HD0005", 1), ("HD0006", 3)):
    for _ in range(k):
        n += 1
        activate(code, f"ui-dev-{n}")
hd.post(f"/agent/subs/{nb}/toggle", {}, "/agent/subs")
print("ok", HD, hz, sz, nb, n)
