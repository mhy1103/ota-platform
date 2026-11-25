"""升级前后代理看到的数一个不变：在旧版（ota-sub.jar）上造数据、记下每个代理看到的，换新版启动后再记一次，逐条比。
python3 legacy_cmp.py seed            （旧版实例上造数据）
python3 legacy_cmp.py capture 文件.json
python3 legacy_cmp.py compare 旧.json 新.json
OTA_BASE=http://127.0.0.1:18890 OTA_DATA=/tmp/ota-e2e/legacy90"""
import random
import subprocess
import sys

exec(open("/tmp/ota-e2e/e2e_subagent.py").read().split("P_TIERS = ")[0])

DATA = os.environ["OTA_DATA"]
JAVA = os.path.expanduser("~/Library/Caches/ota-build/jdk-17.0.21+9/Contents/Home/bin/java")
H2 = "/tmp/ota-e2e/h2/h2-2.2.224.jar"
URL = f"jdbc:h2:file:{DATA}/db/ota;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"


def sql(q):
    r = subprocess.run([JAVA, "-cp", H2, "org.h2.tools.Shell", "-url", URL, "-user", "sa", "-password", "", "-sql", q],
                       capture_output=True, text=True, timeout=300)
    return r.stdout + r.stderr


def script(lines):
    path = "/tmp/ota-e2e/logs/legacy_seed.sql"
    open(path, "w").write("\n".join(lines) + "\n")
    r = subprocess.run([JAVA, "-cp", H2, "org.h2.tools.RunScript", "-url", URL, "-user", "sa", "-password", "", "-script", path],
                       capture_output=True, text=True, timeout=300)
    if r.returncode != 0:
        print(r.stdout[-800:], r.stderr[-800:])


_ips = [0]


def client_ip(keys, path, body):
    _ips[0] += 1
    raw = json.dumps(body)
    ts, nonce = str(int(time.time() * 1000)), uuid.uuid4().hex
    sig = hmac.new(keys["secret"].encode(), f"{ts}\n{nonce}\n{raw}".encode(), hashlib.sha256).hexdigest()
    h = {"Content-Type": "application/json", "X-App-Key": keys["appKey"], "X-Timestamp": ts, "X-Nonce": nonce, "X-Signature": sig,
         "X-Forwarded-For": f"10.33.{_ips[0] // 250}.{_ips[0] % 250 + 1}"}
    return json.loads(plain("POST", "/v1/codes" + path, headers=h, data=raw.encode()).body)


FAMILIES = [
    # 账号, 方式, 档位, 二级代理 [(账号, 方式, 档位)], 口令 {名字: 归谁（None 是自己）}
    ("lgt1", "REALTIME", [(1, 10, 20), (11, "", 40)], [("lgt1a", "REALTIME", []), ("lgt1b", "DAILY", [(1, "", 30)]), ("lgt1c", "MANUAL", [(1, "", 50)])],
     {"T1-OWN-1": None, "T1-OWN-2": None, "T1A-1": "lgt1a", "T1A-2": "lgt1a", "T1B-1": "lgt1b", "T1C-1": "lgt1c"}),
    ("lgt2", "DAILY", [(1, "", 50)], [("lgt2a", "DAILY", [(1, "", 20)]), ("lgt2b", "MANUAL", [])],
     {"T2-OWN-1": None, "T2A-1": "lgt2a", "T2B-1": "lgt2b"}),
    ("lgt3", "MANUAL", [(1, 5, 0), (6, "", 35)], [("lgt3a", "MANUAL", [(1, "", 40)])],
     {"T3-OWN-1": None, "T3A-1": "lgt3a"}),
    ("lgt4", "REALTIME", [], [("lgt4a", "REALTIME", [(1, "", 50)])],
     {"T4-OWN-1": None, "T4A-1": "lgt4a"}),
    ("lgt5", "REALTIME", [(1, "", 50)], [],
     {"T5-1": None, "T5-2": None, "T5-3": None}),
]


def seed():
    global adm
    mgmt("POST", "/api/v1/apps", {"slug": "lgapp", "name": "lgapp"})
    mgmt("POST", "/api/v1/apps/lgapp/redemption", {"open": True})
    keys = mgmt("GET", "/api/v1/apps/lgapp/codes-key")[1]
    adm, st, _ = login("admin", "admin-pass-123")
    print("管理员登录", st)
    for top, mode, tiers, subs, codes in FAMILIES:
        adm.post("/admin/agents/create", agent_form(top, top, mode=mode, tiers=tiers, subs=bool(subs), password=f"pass-{top}-123"), "/admin/agents")
        tid = admin_row(top)["agent"]["id"]
        p = logged_in(top, f"pass-{top}-123")
        sid = {}
        for name, smode, stiers in subs:
            _, _, msg, err = p.post("/agent/subs/create", sub_form(name, name, password=f"pass-{name}-123", mode=smode, view=True, tiers=stiers), "/agent/subs")
            sid[name] = row_of(p.model("/agent/subs"), name)["agent"]["id"]
            print("  开二级代理", name, msg or err)
        for c, owner in codes.items():
            st, body = mgmt("POST", "/api/v1/codes", {"code": c, "maxUses": 0, "agentId": tid})
            if owner:
                p.post(f"/agent/subs/{sid[owner]}/codes", {"give": [body[0]["id"]]}, "/agent/subs")
        print("代理", top, tid, sid)
    rnd = random.Random(20261121)
    all_codes = [c for f in FAMILIES for c in f[4]]
    n = 0
    for c in all_codes:
        for i in range(rnd.randint(18, 40)):
            r = client_ip(keys, "/redeem", {"code": c, "deviceId": f"{c}-d{i}"})
            if r.get("ok") and rnd.random() > 0.04:
                client_ip(keys, "/confirm", {"grant": r["grant"]})
            n += 1
    print("激活", n)
    # 把激活撒到前 25 天和今天：有的天多、有的天少，今天的都在现在以前
    rows = [l.split("|") for l in sql("select id from activation_record order by id").splitlines()[1:] if l.strip() and l.strip()[0].isdigit()]
    now = time.time()
    lines = []
    import datetime
    today0 = datetime.datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
    for (rid,) in rows:
        back = rnd.choice([0, 0, 0, 1, 1, 1, 1, 2, 2, 3, 4, 5, 6, 7, 9, 12, 15, 20, 25])
        day = today0 - datetime.timedelta(days=back)
        limit = (datetime.datetime.now() - day).total_seconds() - 120 if back == 0 else 86399
        sec = rnd.randint(0, int(limit))
        t = day + datetime.timedelta(seconds=sec)
        a = t - datetime.timedelta(seconds=rnd.randint(1, 50))
        lines.append(f"update activation_record set activated_at = TIMESTAMP '{a:%Y-%m-%d %H:%M:%S}', "
                     f"confirmed_at = case when confirmed then TIMESTAMP '{t:%Y-%m-%d %H:%M:%S}' else null end where id = {rid.strip()};")
    # 三条老记录：确认了但没有确认时间
    lines.append("update activation_record set confirmed_at = null where id in (select id from activation_record where confirmed = true order by id limit 3);")
    y = today0 - datetime.timedelta(days=1)
    lines.append(f"update agent set snapshot_at = TIMESTAMP '{datetime.datetime.now() - datetime.timedelta(minutes=80):%Y-%m-%d %H:%M:%S}' where username = 'lgt1c';")
    lines.append(f"update agent set snapshot_at = TIMESTAMP '{y + datetime.timedelta(hours=18):%Y-%m-%d %H:%M:%S}' where username = 'lgt3';")
    lines.append(f"update agent set snapshot_at = TIMESTAMP '{y - datetime.timedelta(hours=12):%Y-%m-%d %H:%M:%S}' where username = 'lgt3a';")
    lines.append(f"update agent set snapshot_at = TIMESTAMP '{y + datetime.timedelta(hours=9):%Y-%m-%d %H:%M:%S}' where username = 'lgt2b';")
    lines.append(f"update agent set created_at = TIMESTAMP '{today0 - datetime.timedelta(days=30):%Y-%m-%d %H:%M:%S}';")
    script(lines)
    print(sql("select count(*), sum(case when confirmed then 1 else 0 end) from activation_record"))


def capture(out):
    global adm
    adm, st, _ = login("admin", "admin-pass-123")
    agents = [l.split("|") for l in sql("select id, username from agent order by id").splitlines()[1:] if l.strip() and l.strip()[0].isdigit()]
    res = {"visible": {r["agent"]["username"]: r["visible"] for r in adm.model("/admin/agents")["rows"]}}
    for aid, user in agents:
        aid, user = aid.strip(), user.strip()
        o = adm.model(f"/admin/agents/{aid}/preview")
        recs = []
        page = 1
        while True:
            m = adm.model(f"/admin/agents/{aid}/preview/records?status=confirmed&page={page}")
            rs = m.get("records") or {}
            content = rs.get("content", []) if isinstance(rs, dict) else rs
            for r in content:
                recs.append(f"{r.get('codeText')}|{r.get('activatedAt')}|{r.get('confirmed')}")
            if not content or len(content) < 50:
                break
            page += 1
        sub = {}
        if user in ("lgt1", "lgt2", "lgt3", "lgt4"):
            sm = adm.model(f"/admin/agents/{aid}/preview/subs")
            for r in sm.get("subs", []):
                sub[r["agent"]["username"]] = {"today": r.get("today"), "total": r.get("total"), "seen": r.get("seen")}
        res[user] = {"total": o.get("total"), "refCount": o.get("refCount"), "records": sorted(recs), "subs": sub,
                     "trend": [b.get("count") for b in o.get("trend", [])]}
        print(user, "看到", o.get("total"), "记录", len(recs), "今天/统计日", o.get("refCount"))
    json.dump(res, open(out, "w"), ensure_ascii=False, indent=1)


def compare(a, b):
    x, y = json.load(open(a)), json.load(open(b))
    bad = 0
    for k in x:
        if k == "visible":
            for u in x[k]:
                if x[k][u] != y[k].get(u):
                    print("后台列表不一样", u, x[k][u], y[k].get(u))
                    bad += 1
            continue
        for f in ("total", "refCount", "records", "subs", "trend"):
            if x[k][f] != y[k][f]:
                bad += 1
                if f == "records":
                    print(k, "记录不一样：少了", sorted(set(x[k][f]) - set(y[k][f]))[:5], "多了", sorted(set(y[k][f]) - set(x[k][f]))[:5])
                else:
                    print(k, f, "旧", x[k][f], "新", y[k][f])
    print("一样" if bad == 0 else f"有 {bad} 处不一样")


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "seed":
        seed()
    elif cmd == "capture":
        capture(sys.argv[2])
    else:
        compare(sys.argv[2], sys.argv[3])
