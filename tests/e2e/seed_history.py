"""给界面检查补数据：华东总代（hd01）的口令过去 60 天每天都有些激活（直接改库挪到以前的日子），再发几条公告。
实例要用 AUTO_SERVER 的 H2：OTA_BASE=http://127.0.0.1:18850 OTA_DATA=/tmp/ota-e2e/sub18850 python3 seed_history.py"""
import random
import subprocess

exec(open("/tmp/ota-e2e/e2e_subagent.py").read().split("P_TIERS = ")[0])

DATA = os.environ["OTA_DATA"]
JAVA = os.path.expanduser("~/Library/Caches/ota-build/jdk-17.0.21+9/Contents/Home/bin/java")
H2 = "/tmp/ota-e2e/h2/h2-2.2.224.jar"
URL = f"jdbc:h2:file:{DATA}/db/ota;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"
_ips = [0]


def sql(q):
    r = subprocess.run([JAVA, "-cp", H2, "org.h2.tools.Shell", "-url", URL, "-user", "sa", "-password", "", "-sql", q],
                       capture_output=True, text=True, timeout=300)
    return r.stdout + r.stderr


def client_ip(keys, path, body):
    _ips[0] += 1
    raw = json.dumps(body)
    ts, nonce = str(int(time.time() * 1000)), uuid.uuid4().hex
    sig = hmac.new(keys["secret"].encode(), f"{ts}\n{nonce}\n{raw}".encode(), hashlib.sha256).hexdigest()
    h = {"Content-Type": "application/json", "X-App-Key": keys["appKey"], "X-Timestamp": ts, "X-Nonce": nonce, "X-Signature": sig,
         "X-Forwarded-For": f"10.50.{_ips[0] // 250}.{_ips[0] % 250 + 1}"}
    return json.loads(plain("POST", "/v1/codes" + path, headers=h, data=raw.encode()).body)


def act(code, device):
    r = client_ip(KEYS, "/redeem", {"code": code, "deviceId": device})
    return r.get("ok") is True and client_ip(KEYS, "/confirm", {"grant": r["grant"]}).get("confirmed") is True


KEYS = mgmt("GET", "/api/v1/apps/demoapp/codes-key")[1]
random.seed(7)
n = 0
for d in range(1, 61):
    wave = 1 + 0.6 * (1 if d % 7 in (5, 6) else 0)
    for code, base in (("HD0001", 2.2), ("HD0002", 1.2), ("HD0003", 1.6), ("HD0004", 0.8), ("HD0005", 0.5), ("HD0006", 1.4), ("HD0007", 0.7)):
        k = max(0, int(random.gauss(base * wave * (1.25 if d < 30 else 1), 1.0)))
        for i in range(k):
            if act(code, f"hist-{d:02d}-{code}-{i}"):
                n += 1
print("激活", n)
out = sql("update activation_record set activated_at = dateadd('DAY', -cast(substring(device_id, 6, 2) as int), activated_at), "
          "confirmed_at = dateadd('DAY', -cast(substring(device_id, 6, 2) as int), confirmed_at) where device_id like 'hist-%'")
print(out[-200:])
sql("update agent set created_at = dateadd('DAY', -70, created_at)")
sql("update agent_tier_log set effective_at = dateadd('DAY', -70, effective_at)")
adm, _, _ = login("admin", "admin-pass-123")
adm.post("/admin/agents/notices", {"title": "11 月结算安排", "body": "本月 25 日统一结算，请各位代理核对口令数据。", "level": "important", "audience": "all", "pinned": "true"}, "/admin/agents")
adm.post("/admin/agents/notices", {"title": "一级代理政策调整", "body": "下月起申请口令每天上限改为 300 个。", "level": "normal", "audience": "top"}, "/admin/agents")
hd = logged_in("hd01", "pass-hd-123")
hd.post("/agent/notices", {"title": "分销结算提醒", "body": "大家 24 号前把本月的推广截图发我，25 号统一结算。", "level": "important"}, "/agent/notices")
print("ok")
