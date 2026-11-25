"""同一台设备只算一次激活：用一个口令激活过以后，再兑换别的口令不新增记录、不占口令用量，授权和确认都落在原来那次激活上。
所有 App 加起来也只算一次：别的 App 里同一个设备号、或者同一个口令同一个公网 IP 已经算过的，照常给授权，记成重复激活、不算。"""
import hashlib
import hmac
import uuid

from lib import *


def mgmt(method, path, body=None):
    r = api(method, path, body)
    return r.json() if r.body else None


def make_app(slug):
    api("POST", "/api/v1/apps", {"slug": slug, "name": slug})
    api("POST", f"/api/v1/apps/{slug}/redemption", {"open": True})
    return mgmt("GET", f"/api/v1/apps/{slug}/codes-key")


def client(keys, path, body):
    raw = json.dumps(body)
    ts, nonce = str(int(time.time() * 1000)), uuid.uuid4().hex
    sig = hmac.new(keys["secret"].encode(), f"{ts}\n{nonce}\n{raw}".encode(), hashlib.sha256).hexdigest()
    h = {"Content-Type": "application/json", "X-App-Key": keys["appKey"], "X-Timestamp": ts, "X-Nonce": nonce, "X-Signature": sig}
    return json.loads(plain("POST", "/v1/codes" + path, headers=h, data=raw.encode()).body)


def used(code):
    return next(c["usedCount"] for c in mgmt("GET", "/api/v1/codes") if c["code"] == code)


def records(app, device=None):
    rows = mgmt("GET", f"/api/v1/redemptions?app={app}&limit=1000")
    return [r for r in rows if device is None or r.get("deviceId") == device]


section("准备：两个 App 都在 B 面，三个口令")
k1 = make_app("one")
k2 = make_app("two")
mgmt("POST", "/api/v1/apps/two/dedupe", {"open": True})  # App 乙开按设备去重（App 甲不开，就是尚礼记/月牙睡那种搭配）
codes = {}
for c in ("ONE-A1", "ONE-B2", "ONE-C3", "ONE-D4"):
    codes[c] = mgmt("POST", "/api/v1/codes", {"code": c, "maxUses": 10})[0]["id"]
check("口令建好了", len(codes) == 4 and all(codes.values()), codes)

section("新接口：同一台设备换口令")
r = client(k1, "/redeem", {"code": "ONE-A1", "deviceId": "dev-1"})
check("第一次兑换：新激活", r.get("ok") is True and r.get("existing") is False, r)
check("确认后计入", client(k1, "/confirm", {"grant": r["grant"]}).get("confirmed") is True and used("ONE-A1") == 1)
r2 = client(k1, "/redeem", {"code": "ONE-B2", "deviceId": "dev-1"})
check("换口令再兑换：成功，标成已激活过", r2.get("ok") is True and r2.get("existing") is True, r2)
v = client(k1, "/verify", {"grant": r2["grant"]})
check("拿到的授权还是第一次的口令", v.get("valid") is True and v.get("code") == "ONE-A1", v)
check("用这个授权确认：不重复计入", client(k1, "/confirm", {"grant": r2["grant"]}).get("confirmed") is False)
check("新口令没占用量", used("ONE-B2") == 0 and used("ONE-A1") == 1, (used("ONE-A1"), used("ONE-B2")))
rows = records("one", "dev-1")
check("这台设备只有一条激活记录", len(rows) == 1 and rows[0]["code"] == "ONE-A1", rows)
r3 = client(k1, "/redeem", {"code": "ONE-A1", "deviceId": "dev-1"})
check("再兑换原来的口令：照旧是已兑换过", r3.get("ok") is True and r3.get("existing") is True)

section("别的设备照常计入")
r = client(k1, "/redeem", {"code": "ONE-B2", "deviceId": "dev-2"})
check("新设备兑换：新激活", r.get("ok") is True and r.get("existing") is False, r)
check("确认后计入这个口令", client(k1, "/confirm", {"grant": r["grant"]}).get("confirmed") is True and used("ONE-B2") == 1)

section("只兑换没确认（没真正激活）的设备换口令")
pa = client(k1, "/redeem", {"code": "ONE-A1", "deviceId": "dev-3"})
pc = client(k1, "/redeem", {"code": "ONE-C3", "deviceId": "dev-3"})
check("还没激活过：换口令照常记录", pa.get("existing") is False and pc.get("ok") is True and pc.get("existing") is False, (pa, pc))
check("确认后计入新口令", client(k1, "/confirm", {"grant": pc["grant"]}).get("confirmed") is True and used("ONE-C3") == 1)
check("再拿之前的授权确认：不再计入，同一台设备只算一次",
      client(k1, "/confirm", {"grant": pa["grant"]}).get("confirmed") is False and used("ONE-A1") == 1)
check("有效激活只有一条", len([x for x in records("one", "dev-3") if x.get("confirmed")]) == 1, records("one", "dev-3"))

section("口令本身不能用时照旧报错")
r = client(k1, "/redeem", {"code": "NOPE-0", "deviceId": "dev-1"})
check("不存在的口令", r.get("ok") is False and r.get("error") == "CODE_NOT_FOUND", r)
api("PATCH", f"/api/v1/codes/{codes['ONE-D4']}", {"enabled": False})
r = client(k1, "/redeem", {"code": "ONE-D4", "deviceId": "dev-1"})
check("停用的口令", r.get("ok") is False and r.get("error") == "CODE_DISABLED", r)

section("所有 App 加起来一台设备只算一次：同一个设备号（同一个开发者账号的 App）")
# 兑换只按本 App 的设备号建记录，去重放到确认时做
r = client(k2, "/redeem", {"code": "ONE-B2", "deviceId": "dev-1"})
check("另一个 App 里照常拿到授权", r.get("ok") is True and r.get("grant"), r)
check("能用：授权校验有效", client(k2, "/verify", {"grant": r["grant"]}).get("valid") is True)
check("确认不算、不占口令次数", client(k2, "/confirm", {"grant": r["grant"]}).get("confirmed") is False and used("ONE-B2") == 1, used("ONE-B2"))
row = records("two", "dev-1")
check("记成重复，按设备号认出来", len(row) == 1 and row[0]["confirmed"] is False and row[0].get("dupOf") == records("one", "dev-1")[0]["id"]
      and row[0].get("dupBy") == "device", row)


def actfp(keys, code, device, init, model=None):
    """兑换再确认，带设备指纹（初始化时间+机型）。返回（兑换结果，这次算没算）"""
    r = client(keys, "/redeem", {"code": code, "deviceId": device})
    if not r.get("grant"):
        return r, False
    body = {"grant": r["grant"]}
    if init:
        body["init"] = init
    if model:
        body["model"] = model
    return r, client(keys, "/confirm", body).get("confirmed") is True


section("不同开发者账号的 App（设备号不一样）：同一台手机（指纹）只算第一次")
for c in ("ONE-E5", "ONE-G7", "ONE-H8"):
    mgmt("POST", "/api/v1/codes", {"code": c, "maxUses": 10})
r, ok = actfp(k1, "ONE-E5", "acct1-dev", "1632467920111", "iPhone15,2")
check("App 甲先激活：算", ok and used("ONE-E5") == 1)
r, ok = actfp(k2, "ONE-E5", "acct2-dev", "1632467920111", "iPhone15,2")
check("App 乙同一台手机（同指纹）：拿到授权但不算", r.get("grant") and not ok and used("ONE-E5") == 1, (r, used("ONE-E5")))
check("App 乙里能用", client(k2, "/verify", {"grant": r["grant"]}).get("valid") is True)
check("记成重复，按指纹认出来", records("two", "acct2-dev")[0].get("dupOf") == records("one", "acct1-dev")[0]["id"]
      and records("two", "acct2-dev")[0].get("dupBy") == "fp", records("two", "acct2-dev"))
r, ok = actfp(k2, "ONE-E5", "other-phone", "1699999999222", "iPhone16,1")
check("另一台手机（初始化时间不一样）：算", ok and used("ONE-E5") == 2)
r, ok = actfp(k2, "ONE-E5", "same-init-diff-model", "1632467920111", "iPhone13,2")
check("同一个初始化时间、机型不一样（极少见）：当成两台，算", ok and used("ONE-E5") == 3)
r, ok = actfp(k2, "ONE-E5", "no-init", None)
check("读不到初始化时间（没传 init）的照常算", ok and used("ONE-E5") == 4)
check("管理接口按 dup 筛出重复", [x["deviceId"] for x in mgmt("GET", "/api/v1/redemptions?status=dup&limit=100")]
      == ["acct2-dev", "dev-1"], mgmt("GET", "/api/v1/redemptions?status=dup&limit=100"))

section("两个 App 先后兑换、后确认的：先确认的算，后确认的不算")
rb = client(k2, "/redeem", {"code": "ONE-G7", "deviceId": "race-b"})
check("App 乙先兑换：还没有别的 App 算过，是新激活", rb.get("ok") is True and rb.get("existing") is False, rb)
r, ok = actfp(k1, "ONE-G7", "race-a", "1650000000777", "iPhone15,2")
check("App 甲兑换并确认：算", ok and used("ONE-G7") == 1)
check("App 乙这时才确认（同指纹）：不算", client(k2, "/confirm", {"grant": rb["grant"], "init": "1650000000777", "model": "iPhone15,2"}).get("confirmed") is False and used("ONE-G7") == 1)
check("App 乙那条确认时补标成重复", records("two", "race-b")[0].get("dupOf") == records("one", "race-a")[0]["id"], records("two", "race-b"))

section("救老用户：老记录升级前没指纹，B 包校验时补写，之后别的 App 才认得出")
r = client(k1, "/redeem", {"code": "ONE-H8", "deviceId": "no-fp-device"})
check("老用户激活了（老 B 包，确认时没带指纹）", client(k1, "/confirm", {"grant": r["grant"]}).get("confirmed") is True and used("ONE-H8") == 1)
r2 = client(k2, "/redeem", {"code": "ONE-H8", "deviceId": "other-app"})
ok2 = client(k2, "/confirm", {"grant": r2["grant"], "init": "1766000000888", "model": "iPhone12,1"}).get("confirmed") is True
check("这时别的 App 来（而且是这台手机自己的 App）：老记录没指纹，认不出，照常算", ok2 and used("ONE-H8") == 2)
# 老用户升级 B 包后打开，校验时把指纹补写到老记录上（后台异步补，等一下）
client(k1, "/verify", {"grant": r["grant"], "init": "1777000000999", "model": "iPhone12,1"})
time.sleep(2.0)
r3 = client(k2, "/redeem", {"code": "ONE-H8", "deviceId": "other-app-3"})
ok3 = client(k2, "/confirm", {"grant": r3["grant"], "init": "1777000000999", "model": "iPhone12,1"}).get("confirmed") is True
check("补了指纹以后，别的 App 同一台手机再激活：认出来，不算", not ok3 and used("ONE-H8") == 2
      and records("two", "other-app-3")[0].get("dupBy") == "fp", records("two", "other-app-3"))

section("开关：关了「按设备去重」的 App，同一台手机照常各算各的")
k3 = make_app("three")  # 默认不开去重
mgmt("POST", "/api/v1/codes", {"code": "ONE-M1", "maxUses": 10})
r = client(k3, "/redeem", {"code": "ONE-M1", "deviceId": "m-dev"})
ok = client(k3, "/confirm", {"grant": r["grant"], "init": "1632467920111", "model": "iPhone15,2"}).get("confirmed") is True
check("和 acct1-dev 同一台手机，但这个 App 没开去重：照常算", ok and used("ONE-M1") == 1 and records("three", "m-dev")[0].get("dupOf") is None, records("three", "m-dev"))
mgmt("POST", "/api/v1/apps/three/dedupe", {"open": True})
r = client(k3, "/redeem", {"code": "ONE-M1", "deviceId": "m-dev2"})
ok = client(k3, "/confirm", {"grant": r["grant"], "init": "1632467920111", "model": "iPhone15,2"}).get("confirmed") is True
check("开了去重之后，同一台手机再来：不算", not ok and used("ONE-M1") == 1 and records("three", "m-dev2")[0].get("dupBy") == "fp", records("three", "m-dev2"))

if os.environ.get("OTA_DATA"):
    import subprocess
    java = os.path.expanduser("~/Library/Caches/ota-build/jdk-17.0.21+9/Contents/Home/bin/java")
    url = f"jdbc:h2:file:{os.environ['OTA_DATA']}/db/ota;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"

    section("日活：别的 App 算过的设备打开，记在算过的那一条上")
    # acct2-dev 是 acct1-dev 的重复：它打开 App（校验）时，日活记在算过的那一条（acct1-dev）上
    g2 = client(k2, "/redeem", {"code": "ONE-E5", "deviceId": "acct2-dev"}).get("grant")
    client(k2, "/verify", {"grant": g2, "init": "1632467920111", "model": "iPhone15,2"})
    time.sleep(1.5)
    q = ("select r.device_id, count(*) from activation_day d join activation_record r on r.id = d.record_id "
         "where r.device_id in ('acct1-dev', 'acct2-dev') and d.stat_day = current_date group by r.device_id")
    out = subprocess.run([java, "-cp", "/tmp/ota-e2e/h2/h2-2.2.224.jar", "org.h2.tools.Shell", "-url", url, "-user", "sa", "-password", "",
                          "-sql", q], capture_output=True, text=True, timeout=60).stdout.replace(" ", "")
    check("别的 App 算过的设备打开：日活记在算过的那一条上，一天只算一次", "acct1-dev|1" in out and "acct2-dev" not in out, out)

done()
