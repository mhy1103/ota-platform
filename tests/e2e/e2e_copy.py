"""发布时什么时候复制成新版本：比线上旧才复制；线上已经是这份内容（原版和它的副本）就不复制，只调灰度"""
from lib import *

APP = os.environ.get("APP", "copy2")


def register(app, root, token):
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
    return api("POST", f"/api/v1/apps/{app}/releases", {"runtimeVersion": cfg["runtimeVersion"], "metadata": meta,
                                                        "expoConfig": cfg, "files": files}, token=token).json()


def releases_count():
    return len(api("GET", f"/api/v1/apps/{APP}/releases?limit=100").json())


section("准备")
check("新建 App", api("POST", "/api/v1/apps", {"slug": APP, "name": "副本测试"}).status == 201)
tok = api("POST", f"/api/v1/apps/{APP}/tokens", {"name": "CI"}).json()["token"]
for v in ("v1", "v2"):
    make_export(f"/tmp/ota-e2e/{APP}/{v}", bundle_text=APP + " " + v + " {p}")
v1 = register(APP, f"/tmp/ota-e2e/{APP}/v1", tok)
time.sleep(1.1)
v2 = register(APP, f"/tmp/ota-e2e/{APP}/v2", tok)


def dep(rid, rollout=100):
    return api("POST", f"/api/v1/apps/{APP}/channels/production/deploy", {"releaseId": rid, "rolloutPercent": rollout}, token=tok).json()


section("比线上旧才复制")
d = dep(v2["id"])
check("发布最新的 v2：不复制", d["releaseId"] == v2["id"] and d["republished"] is False, d)
d = dep(v1["id"])
v1c = d["releaseId"]
check("回到旧的 v1：复制成新版本（已经装了 v2 的设备才会换回去）", v1c != v1["id"] and d["republished"] is True, d)
d = dep(v2["id"])
v2c = d["releaseId"]
check("再切回 v2：它比线上的 v1 副本旧，复制（这次是必要的）", v2c != v2["id"] and d["republished"] is True, d)

section("线上已经是这份内容：不再复制")
n = releases_count()
d = dep(v2["id"])
check("又点了 v2 原版：不复制，线上还是那个副本", d["releaseId"] == v2c and d["republished"] is False, d)
d = dep(v2["id"], 50)
check("再点一次并改灰度 50%：只调灰度", d["releaseId"] == v2c and d["republished"] is False and d["rolloutPercent"] == 50, d)
d = dep(v2c)
check("点线上副本本身：照旧不复制", d["releaseId"] == v2c and d["republished"] is False, d)
check("版本数没增加", releases_count() == n, (n, releases_count()))
h = api("GET", f"/api/v1/apps/{APP}/history?limit=3").json()
check("发布记录记成调整灰度", h and h[0].get("action") in ("ROLLOUT", "rollout"), h[:1])

section("别的情况照旧")
d = dep(v1c)
check("发布 v1 的旧副本：比线上旧，照样复制", d["releaseId"] not in (v1c, v1["id"]) and d["republished"] is True, d)
done()
