"""把 18870 摆到「原来的存储连不上，等确认清空」：切到对象存储、在对象存储上发一个新版本，然后存储桶「删掉」，再切回本机"""
import os, sys
sys.argv = [sys.argv[0]]
os.environ.setdefault("OTA_BASE", "http://127.0.0.1:18870")
exec(open("/tmp/ota-e2e/e2e_storage.py", encoding="utf-8").read().split("adm = Admin()")[0])
adm = Admin()
adm.send("GET", "/login")
adm.send("POST", "/login", urllib.parse.urlencode({"username": "admin", "password": "admin-pass-123"}).encode(),
         {"Content-Type": "application/x-www-form-urlencoded"})
s3mode(gone=0)
print("to s3:", switch(adm, GOOD).get("state"))
token = api("POST", "/api/v1/apps/stor/tokens", {"name": "CI3"}).json()["token"]
make_export(f"{T}/v3", bundle_text="st v3 {p}")
rel = register("stor", f"{T}/v3", token)
api("POST", "/api/v1/apps/stor/channels/production/deploy", {"releaseId": rel["id"], "rolloutPercent": 100}, token=token)
s3mode(gone=1)
r = switch(adm, {"type": "local"})
print(json.dumps(r, ensure_ascii=False, indent=1))
