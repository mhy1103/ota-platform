"""第 22 轮：扣量按比例分到每个二级代理；改比例只影响改了以后的激活（当天的、以前的都不变）；升级后按操作日志还原以前的比例；
二级代理能看公告、一级代理给二级代理发公告；大屏「待确认」只算今天提交的；代理概览选时间段。
实例要用 AUTO_SERVER 的 H2（测试里直接改库把激活挪到前几天）：
OTA_BASE=http://127.0.0.1:18866 OTA_DATA=/tmp/ota-e2e/reg66 OTA_JAR=/tmp/ota-e2e/ota-new.jar python3 e2e_round22.py"""
import subprocess

exec(open("/tmp/ota-e2e/e2e_subagent.py").read().split("P_TIERS = ")[0])

DATA = os.environ["OTA_DATA"]
JAR = os.environ.get("OTA_JAR", "/tmp/ota-e2e/ota-new.jar")
PORT = BASE.rsplit(":", 1)[1]
JAVA = os.path.expanduser("~/Library/Caches/ota-build/jdk-17.0.21+9/Contents/Home/bin/java")
H2 = "/tmp/ota-e2e/h2/h2-2.2.224.jar"
URL = f"jdbc:h2:file:{DATA}/db/ota;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"


def sql(q):
    r = subprocess.run([JAVA, "-cp", H2, "org.h2.tools.Shell", "-url", URL, "-user", "sa", "-password", "", "-sql", q],
                       capture_output=True, text=True, timeout=120)
    out = r.stdout + r.stderr
    if "Exception" in out or "Error" in out:
        print("SQL 出错：", q, out[:600])
    return out


def restart():
    pids = subprocess.run(["lsof", "-t", "-nP", f"-iTCP:{PORT}", "-sTCP:LISTEN"], capture_output=True, text=True).stdout.split()
    for p in pids:
        subprocess.run(["kill", p])
    for _ in range(60):
        if not subprocess.run(["lsof", "-t", "-nP", f"-iTCP:{PORT}", "-sTCP:LISTEN"], capture_output=True, text=True).stdout.strip():
            break
        time.sleep(0.5)
    log = open(f"/tmp/ota-e2e/logs/r22-{PORT}.log", "a")
    subprocess.Popen([JAVA, "-Duser.timezone=Asia/Shanghai", "-jar", JAR, f"--server.port={PORT}", f"--ota.data-dir={DATA}",
                      "--ota.bootstrap-token=test-admin-token", f"--spring.datasource.url={URL}", "--ota.min-confirm-ms=0"],
                     stdout=log, stderr=log, cwd="/tmp/ota-e2e", start_new_session=True)
    for _ in range(120):
        try:
            if plain("GET", "/login").status == 200:
                return True
        except Exception:
            pass
        time.sleep(0.5)
    return False


_ips = [0]


def client_ip(keys, path, body):
    """每台设备一个 IP：兑换接口按 IP 每分钟限 20 次"""
    _ips[0] += 1
    raw = json.dumps(body)
    ts, nonce = str(int(time.time() * 1000)), uuid.uuid4().hex
    sig = hmac.new(keys["secret"].encode(), f"{ts}\n{nonce}\n{raw}".encode(), hashlib.sha256).hexdigest()
    h = {"Content-Type": "application/json", "X-App-Key": keys["appKey"], "X-Timestamp": ts, "X-Nonce": nonce, "X-Signature": sig,
         "X-Forwarded-For": f"10.22.{_ips[0] // 250}.{_ips[0] % 250 + 1}"}
    return json.loads(plain("POST", "/v1/codes" + path, headers=h, data=raw.encode()).body)


def activate(code, device):
    r = client_ip(KEYS, "/redeem", {"code": code, "deviceId": device})
    return r.get("ok") is True and client_ip(KEYS, "/confirm", {"grant": r["grant"]}).get("confirmed") is True


def code(c, agent_id, max_uses=0):
    st, body = mgmt("POST", "/api/v1/codes", {"code": c, "maxUses": max_uses, "agentId": agent_id})
    return body[0]["id"] if st == 201 and body else None


def acts(c, prefix, n):
    return all(activate(c, f"{prefix}-{i}") for i in range(n))


def total_of(s):
    return s.model("/agent").get("total")


section("准备：App、管理员")
mgmt("POST", "/api/v1/apps", {"slug": "r22", "name": "r22"})
mgmt("POST", "/api/v1/apps/r22/redemption", {"open": True})
KEYS = mgmt("GET", "/api/v1/apps/r22/codes-key")[1]
check("App 和口令密钥", KEYS and KEYS.get("appKey"), KEYS)
adm, st, loc = login("admin", "admin-pass-123")
check("管理员登录", st == 302, loc)

section("扣量按比例分到每个二级代理")
adm.post("/admin/agents/create", agent_form("分摊代理", "ra1", tiers=[(1, "", 50)], subs=True, password="pass-ra1-123"), "/admin/agents")
A = admin_row("ra1")["agent"]["id"]
a = logged_in("ra1", "pass-ra1-123")
for i in (1, 2, 3):
    a.post("/agent/subs/create", sub_form(f"分{i}", f"ra1s{i}", password=f"pass-s{i}-123"), "/agent/subs")
m = a.model("/agent/subs")
SUB = {i: row_of(m, f"ra1s{i}")["agent"]["id"] for i in (1, 2, 3)}
C = {i: code(f"RA-{i}", A) for i in (1, 2, 3, 4)}
for i in (1, 2, 3):
    a.post(f"/agent/subs/{SUB[i]}/codes", {"give": [C[i]]}, "/agent/subs")
check("2 / 4 / 6 台分别用了三个二级代理的口令", acts("RA-1", "ra-1", 2) and acts("RA-2", "ra-2", 4) and acts("RA-3", "ra-3", 6))
rows = {i: row_of(a.model("/agent/subs"), f"ra1s{i}") for i in (1, 2, 3)}
check("当天 12 台扣 50%：甲看到 6", total_of(a) == 6, total_of(a))
check("三个二级代理各扣一半：1 / 2 / 3", [rows[i]["total"] for i in (1, 2, 3)] == [1, 2, 3], [rows[i]["total"] for i in (1, 2, 3)])
s1 = logged_in("ra1s1", "pass-s1-123")
s3 = logged_in("ra1s3", "pass-s3-123")
check("二级代理自己看到的一样：分1 是 1、分3 是 3", total_of(s1) == 1 and total_of(s3) == 3, (total_of(s1), total_of(s3)))
check("再用 1 台甲自己的口令", acts("RA-4", "ra-4", 1))
rows = {i: row_of(a.model("/agent/subs"), f"ra1s{i}") for i in (1, 2, 3)}
check("13 台扣 6 台：二级代理还是各扣一半（1 / 2 / 3），零头没落在谁一个人身上", [rows[i]["total"] for i in (1, 2, 3)] == [1, 2, 3] and total_of(a) == 7,
      ([rows[i]["total"] for i in (1, 2, 3)], total_of(a)))
det = adm.model(f"/admin/agents/{A}")
d3 = row_of(det, "ra1s3")
check("管理员详情：分3 真实 6 / 甲看到 3", (d3["real"], d3["total"]) == (6, 3), d3)
ov = a.model("/agent?range=7")
names = [x["name"] for x in ov.get("subShares", [])]
check("概览上「二级代理带来的」按数排、最后是自己手里的", names[:3] == ["分3", "分2", "分1"] and ov["subShares"][-1]["own"] is True, ov.get("subShares"))

section("当天改比例：改之前的不变，之后的按新比例")
adm.post("/admin/agents/create", agent_form("改比例代理", "rb1", tiers=[(1, "", 40)], password="pass-rb1-123"), "/admin/agents")
B = admin_row("rb1")["agent"]["id"]
code("RB-1", B)
b = logged_in("rb1", "pass-rb1-123")
check("用了 10 台", acts("RB-1", "rb", 10))
check("按 40% 扣：看到 6", total_of(b) == 6, total_of(b))
st, loc, msg, err = adm.post(f"/admin/agents/{B}/update", agent_form("改比例代理", "rb1", tiers=[(1, "", 50)]), "/admin/agents")
check("改成 50%：提示从现在起算、扣过的不变", msg and "从现在起算" in msg and not err, (msg, err))
check("改了以后还是 6（那 10 台已经按 40% 扣过了）", total_of(b) == 6, total_of(b))
check("又来 4 台", acts("RB-1", "rb2", 4))
check("新来的 4 台按 50% 扣 2 台：看到 8", total_of(b) == 8, total_of(b))
st, loc, msg, err = adm.post(f"/admin/agents/{B}/update", agent_form("改比例代理", "rb1", tiers=[(1, "", 50)], view=False), "/admin/agents")
check("只改别的设置：不提示扣量", msg and "从现在起算" not in msg, msg)
adm.post(f"/admin/agents/{B}/update", agent_form("改比例代理", "rb1", tiers=[(1, "", 40)]), "/admin/agents")
check("改回 40% 还是 8", total_of(b) == 8, total_of(b))
check("再来 5 台", acts("RB-1", "rb3", 5))
check("这 5 台按 40% 扣 2 台：看到 11", total_of(b) == 11, total_of(b))
check("管理员列表里代理端看到的也是 11", admin_row("rb1")["visible"] == 11, admin_row("rb1"))

section("以前的日子：改了比例也不变")
adm.post("/admin/agents/create", agent_form("昨天代理", "rc1", tiers=[(1, "", 40)], password="pass-rc1-123"), "/admin/agents")
CID = admin_row("rc1")["agent"]["id"]
RC = code("RC-1", CID)
check("用了 10 台", acts("RC-1", "rc", 10))
sql(f"update activation_record set activated_at = dateadd('DAY', -1, activated_at), confirmed_at = dateadd('DAY', -1, confirmed_at) where code_id = {RC}")
sql(f"update agent set created_at = dateadd('DAY', -3, created_at) where id = {CID}")
sql(f"update agent_settlement set settled_to = dateadd('DAY', -3, settled_to), stat_day = dateadd('DAY', -3, stat_day) where agent_id = {CID}")
cc = logged_in("rc1", "pass-rc1-123")
o = cc.model("/agent")
check("挪到昨天：按 40% 看到 6，今天 0", o["total"] == 6 and o["refCount"] == 0, (o["total"], o["refCount"]))
adm.post(f"/admin/agents/{CID}/update", agent_form("昨天代理", "rc1", tiers=[(1, "", 50)]), "/admin/agents")
check("今天改成 50%：昨天的还是 6", total_of(cc) == 6, total_of(cc))
check("今天来 2 台", acts("RC-1", "rc2", 2))
o = cc.model("/agent")
check("今天的按 50% 扣 1 台：今天 1、累计 7", o["refCount"] == 1 and o["total"] == 7, (o["refCount"], o["total"]))

section("升级：旧版扣过的数原样写死")
check("新装的库里没有档位历史表了", "not found" in sql("select count(*) from agent_tier_log").lower())
adm.post("/admin/agents/create", agent_form("老代理", "rd1", tiers=[(1, "", 40)], password="pass-rd1-123"), "/admin/agents")
DID = admin_row("rd1")["agent"]["id"]
RD = code("RD-1", DID)
check("用了 10 台", acts("RD-1", "rd", 10))
sql(f"update activation_record set activated_at = dateadd('DAY', -2, activated_at), confirmed_at = dateadd('DAY', -2, confirmed_at) where code_id = {RD}")
sql(f"update agent set created_at = dateadd('DAY', -3, created_at) where id = {DID}")
adm.post("/admin/agents/create", agent_form("老分摊", "re1", tiers=[(1, "", 50)], subs=True, password="pass-re1-123"), "/admin/agents")
EID = admin_row("re1")["agent"]["id"]
e = logged_in("re1", "pass-re1-123")
for i in (1, 2, 3):
    e.post("/agent/subs/create", sub_form(f"老{i}", f"re1s{i}", password=f"pass-e{i}-123"), "/agent/subs")
ESUB = {i: row_of(e.model("/agent/subs"), f"re1s{i}")["agent"]["id"] for i in (1, 2, 3)}
EC = {i: code(f"RE-{i}", EID) for i in (1, 2, 3)}
for i in (1, 2, 3):
    e.post(f"/agent/subs/{ESUB[i]}/codes", {"give": [EC[i]]}, "/agent/subs")
check("2 / 4 / 6 台，三号的最晚", acts("RE-1", "re-1", 2) and acts("RE-2", "re-2", 4) and acts("RE-3", "re-3", 6))
check("新版按比例平分：1 / 2 / 3", [row_of(e.model("/agent/subs"), f"re1s{i}")["total"] for i in (1, 2, 3)] == [1, 2, 3])
FAMILY = ",".join(str(x) for x in [DID, EID] + list(ESUB.values()))
sql(f"delete from agent_settlement where agent_id in ({FAMILY})")

section("结算：每天 0 点写死前一天的")
adm.post("/admin/agents/create", agent_form("零点代理", "rf1", tiers=[(1, "", 50)], password="pass-rf1-123"), "/admin/agents")
FID = admin_row("rf1")["agent"]["id"]
RF = code("RF-1", FID)
check("用了 10 台", acts("RF-1", "rf", 10))
f = logged_in("rf1", "pass-rf1-123")
check("今天的还没结算，按 50% 看到 5", total_of(f) == 5, total_of(f))
sql(f"update activation_record set activated_at = dateadd('DAY', -1, activated_at), confirmed_at = dateadd('DAY', -1, confirmed_at) where code_id = {RF}")
sql(f"update agent set created_at = dateadd('DAY', -1, created_at) where id = {FID}")
sql(f"update agent_settlement set settled_to = dateadd('DAY', -1, settled_to), stat_day = dateadd('DAY', -1, stat_day) where agent_id = {FID}")

check("重启（升级，也过了 0 点）", restart())
adm, _, _ = login("admin", "admin-pass-123")
d = logged_in("rd1", "pass-rd1-123")
check("老代理：前天那 10 台按旧版的 40% 看到 6", total_of(d) == 6, total_of(d))
e = logged_in("re1", "pass-re1-123")
rows = [row_of(e.model("/agent/subs"), f"re1s{i}")["total"] for i in (1, 2, 3)]
check("老分摊：旧版扣的是当天最晚的 6 台（都是三号的），升级后还是 2 / 4 / 0", rows == [2, 4, 0] and total_of(e) == 6, (rows, total_of(e)))
leg = sql(f"select count(*) from agent_settlement where legacy = true and agent_id in ({FAMILY})")
check("结算表里这一家都是按旧版写的", "\n5\n" in leg.replace(" ", ""), leg)
adm.post(f"/admin/agents/{DID}/update", agent_form("老代理", "rd1", tiers=[(1, "", 50)]), "/admin/agents")
check("升级后改成 50%：前天的还是 6", total_of(d) == 6, total_of(d))
check("老分摊的一号又来 2 台", acts("RE-1", "re-1b", 2))
rows = [row_of(e.model("/agent/subs"), f"re1s{i}")["total"] for i in (1, 2, 3)]
check("只有新来的按新规则：当天 14 台扣 7，已经扣的 6 台不动，再扣 1 台落在一号：3 / 4 / 0", rows == [3, 4, 0] and total_of(e) == 7, (rows, total_of(e)))
yday = sql(f"select real_count, shown_count, legacy from agent_settlement where agent_id = {FID} and stat_day = dateadd('DAY', -1, current_date)")
check("零点代理：过了 0 点，昨天的 10 台写进结算表，看到 5", "10 | 5 | FALSE" in " ".join(yday.upper().split()), yday)
adm.post(f"/admin/agents/{FID}/update", agent_form("零点代理", "rf1", tiers=[(1, "", 20)]), "/admin/agents")
f = logged_in("rf1", "pass-rf1-123")
check("改成 20%：昨天的还是 5", total_of(f) == 5, total_of(f))
check("今天来 5 台", acts("RF-1", "rf2", 5))
o = f.model("/agent")
check("今天的按 20% 扣 1 台：今天 4、累计 9", o["refCount"] == 4 and o["total"] == 9, (o["refCount"], o["total"]))
check("别的代理没动：改比例代理还是 11", total_of(logged_in("rb1", "pass-rb1-123")) == 11)
a = logged_in("ra1", "pass-ra1-123")
s1 = logged_in("ra1s1", "pass-s1-123")
check("分摊代理也没动：1 / 2 / 3、自己看到 7", [row_of(a.model("/agent/subs"), f"ra1s{i}")["total"] for i in (1, 2, 3)] == [1, 2, 3] and total_of(a) == 7)

section("结算：手动更新时写死到更新的时刻")
adm.post("/admin/agents/create", agent_form("手动代理", "rg1", mode="MANUAL", tiers=[(1, "", 50)], password="pass-rg1-123"), "/admin/agents")
GID = admin_row("rg1")["agent"]["id"]
code("RG-1", GID)
check("用了 10 台", acts("RG-1", "rg", 10))
g = logged_in("rg1", "pass-rg1-123")
check("还没更新：看到 0", total_of(g) == 0, total_of(g))
adm.post(f"/admin/agents/{GID}/refresh", {}, "/admin/agents")
check("更新到现在：按 50% 看到 5", total_of(g) == 5, total_of(g))
rows = sql(f"select real_count, shown_count from agent_settlement where agent_id = {GID} and real_count > 0")
check("更新时就写进结算表：10 台看到 5", "10 | 5" in " ".join(rows.split()), rows)
adm.post(f"/admin/agents/{GID}/update", agent_form("手动代理", "rg1", mode="MANUAL", tiers=[(1, "", 20)]), "/admin/agents")
check("改成 20%：还是 5", total_of(g) == 5, total_of(g))
check("又来 10 台", acts("RG-1", "rg2", 10))
check("没更新还是 5", total_of(g) == 5, total_of(g))
adm.post(f"/admin/agents/{GID}/refresh", {}, "/admin/agents")
check("更新后新来的 10 台按 20% 扣 2：看到 13", total_of(g) == 13, total_of(g))
adm.post(f"/admin/agents/{GID}/delete", {"purge": "false"}, "/admin/agents")
check("删掉代理：结算记录一起删", "\n0\n" in sql(f"select count(*) from agent_settlement where agent_id = {GID}").replace(" ", ""))

section("口令次数按代理看到的算：看不出扣了量")


def until(fn, timeout=6):
    """后台重算代理看到的次数是在激活确认以后异步做的，等它一下"""
    t0 = time.time()
    while True:
        v = fn()
        if v or time.time() - t0 > timeout:
            return v
        time.sleep(0.3)


def code_row(s, path, text):
    return next((x for x in s.model(path).get("codes", []) if x["code"] == text), None)


def admin_code(text, status="all"):
    c = adm.model(f"/admin/codes?status={status}&q={text}")["codes"]
    return next((x for x in (c["content"] if isinstance(c, dict) else c) if x["code"] == text), None)


adm.post("/admin/agents/create", agent_form("次数代理", "rl1", tiers=[(1, "", 50)], password="pass-rl1-123"), "/admin/agents")
LID = admin_row("rl1")["agent"]["id"]
code("LIM-4", LID, 4)
check("可激活 4 次的口令，用了 4 台", acts("LIM-4", "lim", 4))
lv = logged_in("rl1", "pass-rl1-123")
c = until(lambda: (lambda x: x if x and x["usedCount"] == 2 else None)(code_row(lv, "/agent", "LIM-4")))
o = lv.model("/agent")
check("扣 50%：代理看到 2 台，口令上也是用了 2 次、还能用", o["total"] == 2 and c and o["usableCount"] == 1, (o["total"], c, o.get("usableCount")))
check("代理的页面数据里没有实际用了几次", c and set(c) <= {"id", "code", "enabled", "expiresAt", "maxUses", "usedCount", "subAgentId"}, c)
n = 4
while n < 20 and activate("LIM-4", f"lim-more-{n}"):
    n += 1
check("一直能用到代理看到的满 4 次：实际一共 7 台", n == 7, n)
r = client_ip(KEYS, "/redeem", {"code": "LIM-4", "deviceId": "lim-last"})
check("再兑换：口令已达到使用上限", r.get("ok") is False and (r.get("error") or r.get("code")) == "CODE_EXHAUSTED", r)
c = until(lambda: (lambda x: x if x and x["usedCount"] == 4 else None)(code_row(lv, "/agent", "LIM-4")))
o = lv.model("/agent")
check("代理那边：看到 4 台、口令用了 4 / 4、没有可用的了，对得上", c and o["total"] == 4 and o["usableCount"] == 0, (c, o["total"], o.get("usableCount")))
ac = until(lambda: (lambda x: x if x and x.get("seenCount") == 4 else None)(admin_code("LIM-4")))
check("管理员：实际用了 7 次、代理看到 4 次", ac and ac["usedCount"] == 7 and ac["seenCount"] == 4, ac)
check("管理员按状态筛：在「已用完」里、不在「可用」里", admin_code("LIM-4", "exhausted") is not None and admin_code("LIM-4", "usable") is None)
tc = next((x for x in adm.model(f"/admin/agents/{LID}").get("topCodes", []) if x["code"] == "LIM-4"), None)
check("代理详情：实际 7、标着代理看到 4、已用完", tc and tc["confirmed"] == 7 and tc["seen"] == 4 and tc["status"] == "exhausted", tc)

adm.post("/admin/agents/create", agent_form("次数上级", "rm1", subs=True, password="pass-rm1-123"), "/admin/agents")
MID = admin_row("rm1")["agent"]["id"]
mv = logged_in("rm1", "pass-rm1-123")
mv.post("/agent/subs/create", sub_form("次数下级", "rm1s", password="pass-rm1s-123", tiers=[(1, "", 50)]), "/agent/subs")
MS = row_of(mv.model("/agent/subs"), "rm1s")["agent"]["id"]
SC = code("SUBLIM-4", MID, 4)
mv.post(f"/agent/subs/{MS}/codes", {"give": [SC]}, "/agent/subs")
n = 0
while n < 20 and activate("SUBLIM-4", f"sublim-{n}"):
    n += 1
check("分给二级代理（扣 50%）的口令按二级代理看到的算：实际 7 台才用完", n == 7, n)
sv = logged_in("rm1s", "pass-rm1s-123")
c = until(lambda: (lambda x: x if x and x["usedCount"] == 4 else None)(code_row(sv, "/agent", "SUBLIM-4")))
check("二级代理看到 4 台、口令用了 4 / 4", c and sv.model("/agent")["total"] == 4, c)

adm.post("/admin/agents/create", agent_form("不扣量代理", "rn1", password="pass-rn1-123"), "/admin/agents")
code("PLAIN-3", admin_row("rn1")["agent"]["id"], 3)
n = 0
while n < 10 and activate("PLAIN-3", f"plain-{n}"):
    n += 1
check("不扣量的代理：和以前一样，3 次就用完", n == 3, n)
pc = admin_code("PLAIN-3")
check("不扣量的口令没有「代理看到」的数", pc and pc.get("seenCount") is None and pc["usedCount"] == 3, pc)

section("公告：二级代理能看、一级代理给二级代理发")
adm.post("/admin/agents/notices", {"title": "全体公告", "body": "所有代理都看", "level": "normal", "audience": "all"}, "/admin/agents")
adm.post("/admin/agents/notices", {"title": "一级公告", "body": "只给一级代理", "level": "important", "audience": "top"}, "/admin/agents")
nl = adm.model("/admin/agents").get("notices", [])
byt = {x["notice"]["title"]: x for x in nl}
check("后台列表：全体公告发给的人比一级公告多（含二级代理）", byt["全体公告"]["audience"] > byt["一级公告"]["audience"], {k: v["audience"] for k, v in byt.items()})
check("后台列表标着发给谁", byt["全体公告"]["notice"]["forSubs"] is True and byt["一级公告"]["notice"]["forSubs"] is False)
st, loc, msg, err = a.post("/agent/notices", {"title": "给分销的", "body": "本月 25 号结算", "level": "important", "pinned": "true"}, "/agent/notices")
check("一级代理发公告给二级代理", msg and "给分销的" in msg and not err, (msg, err))
an = a.model("/agent/notices")
check("一级代理看到：收到 2 条、发出 1 条", len(an["notices"]) == 2 and len(an["sent"]) == 1 and an["canSend"] is True, (len(an["notices"]), len(an["sent"])))
OWN = an["sent"][0]["notice"]["id"]
sn = s1.model("/agent")
check("二级代理概览：有公告横幅（上级发的重要公告）", sn.get("noticeBanner") and sn["noticeBanner"]["title"] == "给分销的", sn.get("noticeBanner"))
check("顶栏有未读数 2", sn.get("noticeUnread") == 2, sn.get("noticeUnread"))
sp = s1.model("/agent/notices")
titles = [x["title"] for x in sp["notices"]]
check("二级代理的公告页：上级发的和全体公告，没有只发一级的", sorted(titles) == ["全体公告", "给分销的"] and sp["canSend"] is False, titles)
check("上级发的标着 fromParent", next(x for x in sp["notices"] if x["title"] == "给分销的")["fromParent"] is True)
check("看过以后未读 0", s1.model("/agent").get("noticeUnread") == 0)
check("一级代理那边：1 / 3 已读", a.model("/agent/notices")["sent"][0]["read"] == 1 and a.model("/agent/notices")["sent"][0]["audience"] == 3)
check("别的一级代理看不到 TA 发给二级代理的", "给分销的" not in [x["title"] for x in logged_in("rb1", "pass-rb1-123").model("/agent/notices")["notices"]])
st, loc, msg, err = s1.post("/agent/notices", {"title": "我也发", "body": "x"}, "/agent/notices")
check("二级代理不能发", len(a.model("/agent/notices")["sent"]) == 1 and "我也发" not in json.dumps(s1.model("/agent/notices"), ensure_ascii=False))
b = logged_in("rb1", "pass-rb1-123")
_, _, _, err = b.post(f"/agent/notices/{OWN}/delete", {}, "/agent/notices")
check("别人删不了", len(a.model("/agent/notices")["sent"]) == 1)
_, _, msg, err = a.post(f"/agent/notices/{OWN}/update", {"title": "给分销的（改）", "body": "改成 26 号", "level": "normal"}, "/agent/notices")
check("一级代理改自己的公告", msg and "改" in msg and a.model("/agent/notices")["sent"][0]["notice"]["title"] == "给分销的（改）", (msg, err))
pv = a.model(f"/agent/subs/{SUB[1]}/preview/notices")
check("以二级代理的视角预览公告页", pv.get("preview") is True and len(pv.get("notices", [])) == 2, pv.get("_status"))
_, _, msg, err = a.post(f"/agent/notices/{OWN}/delete", {}, "/agent/notices")
check("一级代理删自己的公告：二级代理那边也没了", msg and "已删除" in msg and "给分销的（改）" not in [x["title"] for x in s1.model("/agent/notices")["notices"]], (msg, err))
logs = json.dumps(adm.model("/admin/logs"), ensure_ascii=False)
check("日志里有给二级代理发公告", "SUB_NOTICE_CREATE" in logs and "SUB_NOTICE_DELETE" in logs)

section("代理概览：时间段")
o = d.model("/agent?range=30")
check("近 30 天：30 根柱子", o["range"] == "30" and o["rangeLabel"] == "近 30 天" and len(o["trend"]) == 30, (o.get("range"), o.get("rangeLabel"), len(o.get("trend", []))))
check("前天那 6 台在近 30 天里", o["rangeCount"] == 6 and o["rangePeak"] == 6, (o.get("rangeCount"), o.get("rangePeak")))
o = d.model("/agent?range=90")
check("近 90 天：按天 90 根、类名 range-90", len(o["trend"]) == 90 and o["trendClass"] == "range-90", (len(o["trend"]), o.get("trendClass")))
o = d.model("/agent?range=month")
check("本月", o["range"] == "month" and o["rangeLabel"].endswith("月"), o.get("rangeLabel"))
o = d.model("/agent?range=custom&from=2025-01-01&to=2026-12-31")
check("自定义超过一年：截到一年、按月画", o["range"] == "custom" and o["rangeDays"] == 366 and o["trendClass"] == "by-month", (o.get("rangeDays"), o.get("trendClass")))
o = d.model("/agent?range=nonsense")
check("看不懂的回到近 14 天", o["range"] == "14" and len(o["trend"]) == 14, o.get("range"))
check("按月统计有这个月", len(o["months"]) >= 1 and o["months"][0]["count"] == 6, o.get("months"))
check("口令的时间段数", dict(o["rangeByCode"]["$m"]).get(str(RD)) == 6, o.get("rangeByCode"))
o = adm.model(f"/admin/agents/{DID}/preview?range=30")
check("管理员预览也能选时间段", o.get("range") == "30" and o.get("rangeCount") == 6, (o.get("range"), o.get("rangeCount")))

section("日活与留存：App 启动时校验授权就算那天打开过")
adm.post("/admin/agents/create", agent_form("日活代理", "ru1", tiers=[(1, "", 50)], subs=True, password="pass-ru1-123"), "/admin/agents")
UID = admin_row("ru1")["agent"]["id"]
uv = logged_in("ru1", "pass-ru1-123")
uv.post("/agent/subs/create", sub_form("日活下级", "ru1s", password="pass-ru1s-123"), "/agent/subs")
US = row_of(uv.model("/agent/subs"), "ru1s")["agent"]["id"]
U1, U2 = code("AC-1", UID), code("AC-2", UID)
uv.post(f"/agent/subs/{US}/codes", {"give": [U2]}, "/agent/subs")
GR = {}
r = client_ip(KEYS, "/redeem", {"code": "AC-1", "deviceId": "act-0"})
GR["act-0"] = r.get("grant")
client_ip(KEYS, "/verify", {"grant": GR["act-0"]})
time.sleep(1)
check("还没确认的激活来校验：不算日活", "\n0\n" in sql(f"select count(*) from activation_day where code_id = {U1}").replace(" ", ""))
client_ip(KEYS, "/confirm", {"grant": GR["act-0"]})
for i, c in ((1, "AC-1"), (2, "AC-1"), (3, "AC-1"), (4, "AC-2"), (5, "AC-2")):
    r = client_ip(KEYS, "/redeem", {"code": c, "deviceId": f"act-{i}"})
    GR[f"act-{i}"] = r.get("grant")
    client_ip(KEYS, "/confirm", {"grant": r.get("grant")})
for d in ("act-0", "act-1"):
    client_ip(KEYS, "/verify", {"grant": GR[d]})
page = f"/admin/agents/{UID}/active"
am = until(lambda: (lambda x: x if x.get("actToday") == 6 else None)(adm.model(page)))
check("今天新激活 6 台：今日日活 6、新激活 6、累计 6（同一天再校验不重复算）", am and am["actToday"] == 6 and am["actFreshToday"] == 6 and am["actTotal"] == 6,
      am and (am.get("actToday"), am.get("actFreshToday"), am.get("actTotal")))
check("每台每天只记一行", "\n6\n" in sql(f"select count(*) from activation_day where code_id in ({U1}, {U2})").replace(" ", ""))
sql(f"update activation_record set activated_at = dateadd('DAY', -2, activated_at), confirmed_at = dateadd('DAY', -2, confirmed_at) where code_id in ({U1}, {U2})")
sql(f"update activation_day set stat_day = dateadd('DAY', -2, stat_day), act_day = dateadd('DAY', -2, act_day) where code_id in ({U1}, {U2})")
sql("insert into activation_day (record_id, app_id, code_id, stat_day, act_day, age) select id, app_id, code_id, dateadd('DAY', -1, current_date), "
    "dateadd('DAY', -2, current_date), 1 from activation_record where device_id in ('act-0', 'act-1', 'act-4')")
check("重启（清掉今天记过的）", restart())
adm, _, _ = login("admin", "admin-pass-123")
for d in ("act-0", "act-2"):
    client_ip(KEYS, "/verify", {"grant": GR[d]})
am = until(lambda: (lambda x: x if x.get("actToday") == 2 else None)(adm.model(page)))
check("前天新激活 6 台，昨天打开 3 台，今天又有 2 台", am and (am["actBefore"], am["actYesterday"], am["actToday"]) == (6, 3, 2),
      am and (am.get("actBefore"), am.get("actYesterday"), am.get("actToday")))
check("昨日日活比前一天少 50%", am and am.get("actDelta") == {"dir": "down", "text": "50%"}, am and am.get("actDelta"))
check("次日留存 50%（前天新激活 6 台，昨天打开 3 台）", am and (am["actD1"], am["actD1Size"], am["actD1Back"]) == (50, 6, 3),
      am and (am.get("actD1"), am.get("actD1Size"), am.get("actD1Back")))
coh = next((x for x in am.get("actCohorts", [])[1:] if x["size"] == 6), None) if am else None
check("留存表里前天那一行：新激活 6，次日 50%", coh and coh["cells"][0]["pct"] == 50, coh)
rows = {x["name"]: x for x in am.get("actCodeRows", [])} if am else {}
check("按口令：AC-1 累计 4、昨天 2、次日留存 50%；AC-2 累计 2、昨天 1、次日留存 50%",
      rows.get("AC-1", {}).get("total") == 4 and rows["AC-1"]["yesterday"] == 2 and rows["AC-1"]["d1"] == 50
      and rows.get("AC-2", {}).get("total") == 2 and rows["AC-2"]["yesterday"] == 1 and rows["AC-2"]["d1"] == 50, rows)
subs = {x["name"]: x for x in am.get("actSubRows", [])} if am else {}
check("按二级代理：日活下级昨天 1，自己手里的口令昨天 2", subs.get("日活下级", {}).get("yesterday") == 1 and subs.get("自己手里的口令", {}).get("yesterday") == 2, subs)
cm = adm.model(f"{page}?code={U2}")
check("单看 AC-2：昨天 1、累计 2、没有明细表", cm.get("actKind") == "code" and cm.get("actYesterday") == 1 and cm.get("actTotal") == 2
      and cm.get("actCodeRows") is None and "AC-2" in (cm.get("actScope") or ""), (cm.get("actKind"), cm.get("actYesterday"), cm.get("actScope")))
sm = adm.model(f"{page}?sub={US}")
check("单看二级代理：只有分给 TA 的 AC-2", sm.get("actKind") == "sub" and [x["name"] for x in sm.get("actCodeRows", [])] == ["AC-2"], sm.get("actCodeRows"))
al = adm.model("/admin/agents/active")
ag = next((x for x in al.get("actAgentRows", []) if x["name"] == "日活代理"), None)
check("全部代理：日活代理昨天 3、累计 6", ag and ag["yesterday"] == 3 and ag["total"] == 6, ag)
st, loc, _ = uv.call("GET", page)
check("代理后台看不到", st in (302, 401, 403), (st, loc))

section("数据大屏：待确认只算今天提交的")
r = client_ip(KEYS, "/redeem", {"code": "RB-1", "deviceId": "pend-old"})
check("提交 1 个不确认", r.get("ok") is True, r)
sql("update activation_record set activated_at = dateadd('DAY', -1, activated_at) where device_id = 'pend-old'")
for i in range(3):
    client_ip(KEYS, "/redeem", {"code": "RB-1", "deviceId": f"pend-new-{i}"})
sc = adm.model("/admin/screen?app=r22")
check("待确认 3（昨天提交的那个不算）", sc.get("scPending") == 3, sc.get("scPending"))

done()
