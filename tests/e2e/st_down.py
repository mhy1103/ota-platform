"""对象存储整个连不上（服务停了）：切回本机要先确认清空"""
import os, sys, subprocess
sys.argv = [sys.argv[0]]
exec(open("/tmp/ota-e2e/e2e_storage.py", encoding="utf-8").read().split("adm = Admin()")[0])
adm = Admin()
adm.send("GET", "/login")
adm.send("POST", "/login", urllib.parse.urlencode({"username": "admin", "password": "admin-pass-123"}).encode(),
         {"Content-Type": "application/x-www-form-urlencoded"})
s3mode(gone=0)
section("对象存储服务整个停了")
check("切到对象存储", switch(adm, GOOD).get("state") == "done")
token = api("POST", "/api/v1/apps/stor/tokens", {"name": "CI5"}).json()["token"]
make_export(f"{T}/v5", bundle_text="st v5 {p}")
rel = register("stor", f"{T}/v5", token)
api("POST", "/api/v1/apps/stor/channels/production/deploy", {"releaseId": rel["id"], "rolloutPercent": 100}, token=token)
subprocess.run(["pkill", "-f", "s3mock.py 18861"])
time.sleep(1)
t0 = time.time()
r = switch(adm, {"type": "local"})
check("先问，不直接换", r.get("state") == "confirm", r)
check("原因说清楚是连不上哪个地址", "连不上" in (r.get("error") or "") and "18861" in (r.get("error") or ""), r.get("error"))
check("几秒内就判断出来", time.time() - t0 < 15, time.time() - t0)
print("  error:", r.get("error"))
print("  note:", r.get("note"))
r = adm.ajax("/admin/settings/storage/clear", {})
for _ in range(200):
    s = adm.get_json("/admin/settings/storage/status")
    if s["state"] in ("done", "failed", "confirm"):
        break
    time.sleep(0.2)
check("确认后换到本机", s["state"] == "done" and adm.get_json("/admin/settings")["model"].get("storageS3") is False, s)
check("v5 清掉了", rel["id"] not in [x["id"] for x in api("GET", "/api/v1/apps/stor/releases?limit=50").json()])
print("  done note:", s.get("note"))
done()
