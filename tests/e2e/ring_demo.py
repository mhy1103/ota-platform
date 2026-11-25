"""录圆环视频用的数据：OTA_BASE=http://127.0.0.1:18880 OTA_DATA=/tmp/ota-e2e/ring80 python3 ring_demo.py setup|add N|reset|state
setup：一个 App、两个代理和口令、近 60 天的历史、昨天 30 台（其中 12 台在凌晨，算「昨天同一时刻」）、今天 8 台，再生成一个分享链接
add N：现在再激活 N 台；reset：删掉 add 加的，回到今天 8 台"""
import random
import subprocess

exec(open("/tmp/ota-e2e/e2e_subagent.py").read().split("P_TIERS = ")[0])

DATA = os.environ["OTA_DATA"]
JAVA = os.path.expanduser("~/Library/Caches/ota-build/jdk-17.0.21+9/Contents/Home/bin/java")
H2 = "/tmp/ota-e2e/h2/h2-2.2.224.jar"
URL = f"jdbc:h2:file:{DATA}/db/ota;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"
CODES = ["HD01", "HD02", "HD03", "HD04", "HN01", "HN02", "HN03", "FREE1", "FREE2"]
_ip = [random.randint(0, 50000)]


def sql(q):
    r = subprocess.run([JAVA, "-cp", H2, "org.h2.tools.Shell", "-url", URL, "-user", "sa", "-password", "", "-sql", q],
                       capture_output=True, text=True, timeout=120)
    out = r.stdout + r.stderr
    if "Exception" in out or "Error" in out:
        print("SQL 出错：", q, out[:600])
    return out


def keys():
    return mgmt("GET", "/api/v1/apps/demo/codes-key")[1]


def act(k, device, code):
    _ip[0] += 1
    raw = json.dumps({"code": code, "deviceId": device})
    ts, nonce = str(int(time.time() * 1000)), uuid.uuid4().hex
    sig = hmac.new(k["secret"].encode(), f"{ts}\n{nonce}\n{raw}".encode(), hashlib.sha256).hexdigest()
    h = {"Content-Type": "application/json", "X-App-Key": k["appKey"], "X-Timestamp": ts, "X-Nonce": nonce, "X-Signature": sig,
         "X-Forwarded-For": f"10.{_ip[0] // 62500 % 250}.{_ip[0] // 250 % 250}.{_ip[0] % 250 + 1}"}
    r = json.loads(plain("POST", "/v1/codes/redeem", headers=h, data=raw.encode()).body)
    if not r.get("ok"):
        return False
    _ip[0] += 1
    raw = json.dumps({"grant": r["grant"]})
    ts, nonce = str(int(time.time() * 1000)), uuid.uuid4().hex
    sig = hmac.new(k["secret"].encode(), f"{ts}\n{nonce}\n{raw}".encode(), hashlib.sha256).hexdigest()
    h.update({"X-Timestamp": ts, "X-Nonce": nonce, "X-Signature": sig})
    return json.loads(plain("POST", "/v1/codes/confirm", headers=h, data=raw.encode()).body).get("confirmed") is True


def many(prefix, n, weights=None):
    k, ok = keys(), 0
    for i in range(n):
        code = random.choices(CODES, weights=weights or [5, 4, 3, 2, 4, 2, 1, 1, 1])[0]
        ok += act(k, f"{prefix}-{i}-{uuid.uuid4().hex[:6]}", code)
    return ok


def setup():
    mgmt("POST", "/api/v1/apps", {"slug": "demo", "name": "演示 App"})
    mgmt("POST", "/api/v1/apps/demo/redemption", {"open": True})
    adm, st, loc = login("admin", "admin-pass-123")
    adm.post("/admin/agents/create", agent_form("华东总代", "hd01", password="pass-hd-123"), "/admin/agents")
    adm.post("/admin/agents/create", agent_form("华南代理", "hn01", password="pass-hn-123"), "/admin/agents")
    rows = {r["agent"]["username"]: r["agent"]["id"] for r in adm.model("/admin/agents").get("rows", [])}
    for c in CODES:
        agent = rows["hd01"] if c.startswith("HD") else rows["hn01"] if c.startswith("HN") else None
        body = {"code": c, "maxUses": 100000}
        if agent:
            body["agentId"] = agent
        mgmt("POST", "/api/v1/codes", body)
    random.seed(7)
    # 近 60 天（昨天、今天另算）：每天 10–28 台，越近越多一点
    plan = {}
    for d in range(2, 61):
        plan[d] = random.randint(8, 18) + (12 if d < 30 else 0) * (30 - d) // 28 + random.randint(0, 6)
    total = sum(plan.values())
    print("历史", total, "台")
    for d, n in plan.items():
        many(f"d{d}", n)
    many("yb", 12)
    many("ya", 18)
    many("t0", 8)
    for d in plan:
        sql(f"update activation_record set confirmed_at = dateadd('MINUTE', 30 + mod(id * 37, 1260), dateadd('DAY', -{d}, cast(current_date as timestamp)))"
            f", activated_at = dateadd('MINUTE', 29 + mod(id * 37, 1260), dateadd('DAY', -{d}, cast(current_date as timestamp))) where device_id like 'd{d}-%'")
    sql("update activation_record set confirmed_at = dateadd('MINUTE', 5 + mod(id * 13, 135), dateadd('DAY', -1, cast(current_date as timestamp)))"
        ", activated_at = dateadd('MINUTE', 4 + mod(id * 13, 135), dateadd('DAY', -1, cast(current_date as timestamp))) where device_id like 'yb-%'")
    sql("update activation_record set confirmed_at = dateadd('MINUTE', 540 + mod(id * 29, 780), dateadd('DAY', -1, cast(current_date as timestamp)))"
        ", activated_at = dateadd('MINUTE', 539 + mod(id * 29, 780), dateadd('DAY', -1, cast(current_date as timestamp))) where device_id like 'ya-%'")
    sql("update activation_record set confirmed_at = dateadd('MINUTE', 10 + mod(id * 7, 120), cast(current_date as timestamp))"
        ", activated_at = dateadd('MINUTE', 9 + mod(id * 7, 120), cast(current_date as timestamp)) where device_id like 't0-%'")
    st, _, body = adm.call("POST", "/admin/screen/shares", {"scope": "app", "app": "demo", "hours": "0", "note": "录圆环视频", "origin": BASE})
    d = json.loads(body)
    print("分享", d.get("url"))
    open("/tmp/ota-e2e/ring-url.txt", "w").write(d.get("url", ""))


def state():
    adm, st, loc = login("admin", "admin-pass-123")
    m = adm.model("/admin/screen?app=demo")
    print({k: m.get(k) for k in ("scToday", "scYesterday", "scSameTime")})


cmd = sys.argv[1]
if cmd == "setup":
    setup()
    state()
elif cmd == "add":
    tag = os.environ.get("RUN", "x")
    print("加了", many("live-" + tag, int(sys.argv[2])))
elif cmd == "share":
    path = open("/tmp/ota-e2e/ring-url.txt").read().strip().split(BASE, 1)[1]
    st, loc, d = Session().page(path)
    m = (d or {}).get("model", {})
    print({k: m.get(k) for k in ("scToday", "scYesterday", "scSameTime")})
elif cmd == "reset":
    sql("delete from activation_record where device_id like 'live-%'")
    state()
elif cmd == "state":
    state()
