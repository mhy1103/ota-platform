"""指纹保留：从 30 天改成 7 天时马上清掉超出的；刚确认、日活还没记上的不清。
用 18850 的演示数据（demoapp），直接改库造指纹和最近活跃：OTA_BASE=http://127.0.0.1:18850 python3 fp_keep_check.py"""
import subprocess

exec(open("/tmp/ota-e2e/e2e_subagent.py").read().split("P_TIERS = ")[0])

JAVA = os.path.expanduser("~/Library/Caches/ota-build/jdk-17.0.21+9/Contents/Home/bin/java")
URL = "jdbc:h2:file:/tmp/ota-e2e/sub18850/db/ota;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"


def sql(q):
    r = subprocess.run([JAVA, "-cp", "/tmp/ota-e2e/h2/h2-2.2.224.jar", "org.h2.tools.Shell", "-url", URL, "-user", "sa", "-password", "", "-sql", q],
                       capture_output=True, text=True, timeout=120)
    return r.stdout + r.stderr


def one(q):
    lines = [l for l in sql(q).splitlines() if l.strip() and not l.startswith("(")]
    return int(lines[-1].strip())


APP = one("select id from ota_app where slug = 'demoapp'")
sql(f"update ota_app set dedupe_devices = false, fingerprint_keep_days = 7 where id = {APP}")
# 每条有效激活都带上指纹，最近活跃就是确认那天（1 到 60 天前都有）；挑一条当成刚确认、日活还没记上
sql(f"update activation_record set fingerprint = concat('fp-test-', id), last_active = cast(confirmed_at as date) where app_id = {APP} and confirmed = true")
FRESH = one(f"select min(id) from activation_record where app_id = {APP} and confirmed = true")
sql(f"update activation_record set last_active = null, confirmed_at = now() where id = {FRESH}")
total = one(f"select count(*) from activation_record where app_id = {APP} and fingerprint is not null")
over30 = one(f"select count(*) from activation_record where app_id = {APP} and fingerprint is not null and last_active < dateadd('DAY', -30, current_date)")
over7 = one(f"select count(*) from activation_record where app_id = {APP} and fingerprint is not null and last_active < dateadd('DAY', -7, current_date)")
print(f"准备好了：{total} 条带指纹，超过 30 天没打开 {over30} 条，超过 7 天 {over7} 条")

adm, st, _ = login("admin", "admin-pass-123")
check("管理员登录", st == 302)
adm.call("GET", "/admin/use?app=demoapp&back=/admin/app")

section("去重关着时设 30 天：只保存，不清")
_, _, msg, err = adm.post("/admin/apps/dedupe-keep", {"days": "30"}, "/admin/app")
check("提示开了去重以后生效", msg and "开了按设备去重以后生效" in msg, (msg, err))
check("一条都没清", one(f"select count(*) from activation_record where app_id = {APP} and fingerprint is not null") == total)

section("开去重：按 30 天马上清")
_, _, msg, err = adm.post("/admin/apps/dedupe", {"enabled": "true"}, "/admin/app")
check("提示里说清了超过 30 天的台数", msg and f"{over30} 台超过 30 天没打开" in msg, (msg, err))
time.sleep(1.5)
left = one(f"select count(*) from activation_record where app_id = {APP} and fingerprint is not null")
check("超过 30 天的清掉了，别的留着", left == total - over30, (left, total, over30))

section("改成 7 天：马上清掉 7 到 30 天的")
_, _, msg, err = adm.post("/admin/apps/dedupe-keep", {"days": "7"}, "/admin/app")
check("提示里说清了多少台", msg and f"{over7 - over30} 台已经超过 7 天没打开" in msg, (msg, err))
time.sleep(1.5)
left = one(f"select count(*) from activation_record where app_id = {APP} and fingerprint is not null")
check("超过 7 天的都清掉了", left == total - over7, (left, total, over7))
check("没有超过 7 天还留着指纹的", one(f"select count(*) from activation_record where app_id = {APP} and fingerprint is not null and last_active < dateadd('DAY', -7, current_date)") == 0)
check("刚确认、日活还没记上的那条没被清", one(f"select count(*) from activation_record where id = {FRESH} and fingerprint is not null") == 1)

section("再点 7 天：没有可清的")
_, _, msg, err = adm.post("/admin/apps/dedupe-keep", {"days": "7"}, "/admin/app")
check("提示现在没有超过 7 天的", msg and "现在没有超过 7 天没打开的设备" in msg, (msg, err))

section("App 设置页的数据")
m = adm.model("/admin/app")
check("选项是 7 / 30 / 90 / 一直留", m.get("fpKeepOptions") == [7, 30, 90, 0], m.get("fpKeepOptions"))
check("现在是 7 天", (m.get("app") or {}).get("fingerprintKeepDays") == 7, m.get("app"))

done()
