"""用旧 jar 造一份数据：多渠道、灰度、测试设备（含灰度白名单）、回退内置版本、只有 iOS 的版本、没发布的 App"""
import sys

sys.path.insert(0, "/tmp/ota-e2e")
from lib import *

D = "/tmp/ota-e2e/diff/exports"


def register(app, root, token, message):
    meta = json.load(open(f"{root}/metadata.json"))
    cfg = json.load(open(f"{root}/expoConfig.json"))
    files = {}
    for p, e in meta["fileMetadata"].items():
        files[e["bundle"]] = sha256(open(f"{root}/{e['bundle']}", "rb").read())
        for a in e["assets"]:
            files[a["path"]] = sha256(open(f"{root}/{a['path']}", "rb").read())
    missing = api("POST", f"/api/v1/apps/{app}/assets/missing", {"sha256": list(set(files.values()))}, token=token).json()["missing"]
    for p, s in files.items():
        if s in missing:
            api("PUT", f"/api/v1/apps/{app}/assets/{s}", raw=open(f"{root}/{p}", "rb").read(), token=token)
            missing.remove(s)
    r = api("POST", f"/api/v1/apps/{app}/releases", {"runtimeVersion": cfg["runtimeVersion"], "message": message,
                                                     "metadata": meta, "expoConfig": cfg, "files": files}, token=token)
    assert r.status in (200, 201), r.text
    return r.json()["id"]


def deploy(app, channel, rid, token, rollout=100):
    r = api("POST", f"/api/v1/apps/{app}/channels/{channel}/deploy", {"releaseId": rid, "rolloutPercent": rollout}, token=token)
    assert r.status == 200, r.text


ids = {}
for slug in ("dfa", "dfb", "dfc"):
    assert api("POST", "/api/v1/apps", {"slug": slug, "name": "对比 " + slug}).status == 201
tok = {s: api("POST", f"/api/v1/apps/{s}/tokens", {"name": "CI"}).json()["token"] for s in ("dfa", "dfb", "dfc")}

make_export(f"{D}/d1v1", bundle_text="d1 v1 {p}")
make_export(f"{D}/d1v2", bundle_text="d1 v2 {p}", asset=b"PNG-d1-v2")
make_export(f"{D}/d1v3", bundle_text="d1 v3 {p}")
make_export(f"{D}/d1r2", bundle_text="d1 runtime2 {p}", runtime="2.0.0")
d1 = {k: register("dfa", f"{D}/d1{k}", tok["dfa"], "d1 " + k) for k in ("v1", "v2", "v3", "r2")}
deploy("dfa", "production", d1["v1"], tok["dfa"])
deploy("dfa", "production", d1["v2"], tok["dfa"])
deploy("dfa", "production", d1["v3"], tok["dfa"], 30)
assert api("POST", "/api/v1/apps/dfa/channels", {"name": "beta"}).status == 201
deploy("dfa", "beta", d1["v1"], tok["dfa"])
deploy("dfa", "production", d1["r2"], tok["dfa"])
assert api("POST", "/api/v1/apps/dfa/channels/production/rollback-embedded", {"runtimeVersion": "2.0.0"}, token=tok["dfa"]).status == 200
api("POST", "/api/v1/apps/dfa/test-devices", {"clientId": "QA-1", "name": "白名单机", "channel": "production", "rolloutFirst": True})
api("POST", "/api/v1/apps/dfa/test-devices", {"clientId": "QA-2", "name": "测试机", "channel": "beta"})
ids["dfa"] = d1

make_export(f"{D}/d2v1", bundle_text="d2 v1 {p}")
make_export(f"{D}/d2v2", bundle_text="d2 v2 ios {p}", platforms=("ios",))
d2 = {k: register("dfb", f"{D}/d2{k}", tok["dfb"], "d2 " + k) for k in ("v1", "v2")}
deploy("dfb", "production", d2["v1"], tok["dfb"])
deploy("dfb", "production", d2["v2"], tok["dfb"], 50)
ids["dfb"] = d2

make_export(f"{D}/d3v1", bundle_text="d3 v1 {p}")
ids["dfc"] = {"v1": register("dfc", f"{D}/d3v1", tok["dfc"], "d3 v1")}

json.dump(ids, open("/tmp/ota-e2e/diff/ids.json", "w"), indent=1)
print("seeded", json.dumps(ids))
