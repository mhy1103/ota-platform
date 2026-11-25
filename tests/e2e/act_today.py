"""今天在 demoapp 上激活 N 台：OTA_BASE=... python3 act_today.py N 前缀"""
exec(open("/tmp/ota-e2e/e2e_subagent.py").read().split("P_TIERS = ")[0])
_ips = [int(time.time()) % 200]
def client_ip(keys, path, body):
    _ips[0] += 1
    raw = json.dumps(body)
    ts, nonce = str(int(time.time() * 1000)), uuid.uuid4().hex
    sig = hmac.new(keys["secret"].encode(), f"{ts}\n{nonce}\n{raw}".encode(), hashlib.sha256).hexdigest()
    h = {"Content-Type": "application/json", "X-App-Key": keys["appKey"], "X-Timestamp": ts, "X-Nonce": nonce, "X-Signature": sig,
         "X-Forwarded-For": f"10.77.{_ips[0] // 250 % 250}.{_ips[0] % 250 + 1}"}
    return json.loads(plain("POST", "/v1/codes" + path, headers=h, data=raw.encode()).body)
KEYS = mgmt("GET", "/api/v1/apps/demoapp/codes-key")[1]
n, prefix = int(sys.argv[1]), sys.argv[2]
ok = 0
for i in range(n):
    code = ("HD0006", "HD0003", "HD0009")[i % 3]
    r = client_ip(KEYS, "/redeem", {"code": code, "deviceId": f"{prefix}-{i}"})
    if r.get("ok") and client_ip(KEYS, "/confirm", {"grant": r["grant"]}).get("confirmed"):
        ok += 1
print("激活", ok)
