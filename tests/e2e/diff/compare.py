"""同一份数据的两个副本：旧 jar 在 18831，新 jar 在 18832。按同样的顺序发同样的请求，逐个比对响应。
没设口令更新包时，检查更新和管理接口的响应应该完全一样（multipart 分隔符、写操作的时间戳除外）"""
import json
import re
import sys
import time
import urllib.error
import urllib.request

OLD, NEW = "http://127.0.0.1:18831", "http://127.0.0.1:18832"
TOKEN = "test-admin-token"
EMB = "0e0e0e0e-1111-4222-8333-444455556666"
ids = json.load(open("/tmp/ota-e2e/diff/ids.json"))
KEEP = ("content-type", "expo-protocol-version", "expo-sfv-version", "cache-control", "expo-manifest-filters", "expo-server-defined-headers")
TS = re.compile(rb'"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?Z"')
AGO = re.compile(r"(\d+ ?(秒|分钟|小时|天)前|刚刚)")
total, diffs = 0, []


def send(base, method, path, headers=None, body=None):
    h = dict(headers or {})
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        h["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, dict(r.headers), r.read()
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read()


def norm(res, stamp=False, ago=False):
    status, headers, body = res
    if stamp:
        # 写操作在两台服务器上差几毫秒执行：回退指令里的 commitTime 和它的签名跟着不一样
        body = re.sub(rb'"commitTime":"[^"]+"', b'"commitTime":"TS"', body)
        if b'"rollBackToEmbedded"' in body:
            body = re.sub(rb'expo-signature: [^\r\n]+', b"expo-signature: SIG", body)
    hs = {k.lower(): v for k, v in headers.items() if k.lower() in KEEP}
    m = re.search(r'boundary="?([^";]+)"?', hs.get("content-type", ""))
    if m:
        body = body.replace(m.group(1).encode(), b"BOUNDARY")
        hs["content-type"] = hs["content-type"].replace(m.group(1), "BOUNDARY")
    body = re.sub(rb'"timestamp":"[^"]+"', b'"timestamp":"TS"', body)
    if stamp:
        body = TS.sub(b'"TS"', body)
    if ago:
        body = AGO.sub("N前", body.decode("utf-8")).encode("utf-8")
    return status, hs, body


def same(label, method, path, headers=None, body=None, stamp=False, ago=False):
    global total
    total += 1
    a = norm(send(OLD, method, path, headers, body), stamp, ago)
    b = norm(send(NEW, method, path, headers, body), stamp, ago)
    if a != b:
        diffs.append((label, method, path, headers, a[0], b[0], a[1], b[1], a[2].decode("utf-8", "replace"), b[2].decode("utf-8", "replace")))
    return a


def manifests(tag, apps=("dfa", "dfb", "dfc", "nope"), protocols=("1", "0"), stamp=False):
    for app in apps:
        rel = ids.get(app, {})
        currents = [None, EMB] + list(rel.values()) + ["99999999-0000-4000-8000-000000000000"]
        for proto in protocols:
            for platform in ("ios", "android"):
                for runtime in ("1.0.0", "2.0.0"):
                    for channel in (None, "production", "beta", "staging"):
                        for cur in currents:
                            for client in (None, "QA-1", "qa-2", "c-0", "c-1", "c-2", "c-3", "c-4", "c-5", "c-6"):
                                h = {"expo-platform": platform, "expo-runtime-version": runtime, "expo-protocol-version": proto}
                                if channel:
                                    h["expo-channel-name"] = channel
                                if cur:
                                    h["expo-current-update-id"] = cur
                                    h["expo-embedded-update-id"] = EMB
                                if client:
                                    h["eas-client-id"] = client
                                same(f"{tag} 清单", "GET", f"/m/{app}", h, stamp=stamp)
    same(f"{tag} 缺平台", "GET", "/m/dfa", {"expo-runtime-version": "1.0.0"})
    same(f"{tag} 平台不对", "GET", "/m/dfa", {"expo-platform": "web", "expo-runtime-version": "1.0.0"})
    same(f"{tag} 查询参数", "GET", "/m/dfa?platform=ios&runtime-version=1.0.0")


def mgmt(tag, stamp=False):
    auth = {"Authorization": "Bearer " + TOKEN}
    same(f"{tag} App 列表", "GET", "/api/v1/apps", auth, stamp=stamp)
    for app, rel in ids.items():
        same(f"{tag} App 详情", "GET", f"/api/v1/apps/{app}", auth, stamp=stamp)
        same(f"{tag} 版本列表", "GET", f"/api/v1/apps/{app}/releases?limit=50", auth, stamp=stamp)
        same(f"{tag} 发布记录", "GET", f"/api/v1/apps/{app}/history?limit=50", auth, stamp=stamp)
        same(f"{tag} 测试设备", "GET", f"/api/v1/apps/{app}/test-devices", auth, stamp=stamp)
        for rid in rel.values():
            same(f"{tag} 版本详情", "GET", f"/api/v1/apps/{app}/releases/{rid}", auth, stamp=stamp)
        for dev in ("", "QA-1", "c-3", "never-seen"):
            for plat in ("ios", "android"):
                for rt in ("1.0.0", "2.0.0"):
                    same(f"{tag} 模拟检查更新", "GET", f"/api/v1/apps/{app}/diagnose?device={dev}&platform={plat}&runtime={rt}&channel=production", auth, stamp=stamp, ago=True)


t0 = time.time()
manifests("初始")
time.sleep(11)
mgmt("初始")
auth = {"Authorization": "Bearer " + TOKEN}
same("调灰度 30→60", "POST", "/api/v1/apps/dfa/channels/production/deploy", auth, {"releaseId": ids["dfa"]["v3"], "rolloutPercent": 60}, stamp=True)
same("调灰度 50→100", "POST", "/api/v1/apps/dfb/channels/production/deploy", auth, {"releaseId": ids["dfb"]["v2"], "rolloutPercent": 100}, stamp=True)
manifests("调灰度后", apps=("dfa", "dfb"), protocols=("1",))
same("回退内置版本", "POST", "/api/v1/apps/dfa/channels/beta/rollback-embedded", auth, {"runtimeVersion": "1.0.0"}, stamp=True)
same("错误：渠道不存在", "POST", "/api/v1/apps/dfa/channels/nope/deploy", auth, {"releaseId": ids["dfa"]["v1"]})
same("错误：版本不存在", "POST", "/api/v1/apps/dfa/channels/production/deploy", auth, {"releaseId": "nope"})
same("错误：灰度比例不对", "POST", "/api/v1/apps/dfa/channels/production/deploy", auth, {"releaseId": ids["dfa"]["v3"], "rolloutPercent": 0})
same("错误：没有上一版本可回滚", "POST", "/api/v1/apps/dfc/channels/production/rollback", auth, {"runtimeVersion": "1.0.0"})
same("错误：线上版本不能删", "DELETE", f"/api/v1/apps/dfa/releases/{ids['dfa']['v3']}", auth)
manifests("回退后", apps=("dfa",), protocols=("1",), stamp=True)
time.sleep(11)
mgmt("最后", stamp=True)

json.dump([{"label": d[0], "method": d[1], "path": d[2], "headers": d[3], "old": [d[4], d[6], d[8]], "new": [d[5], d[7], d[9]]} for d in diffs],
          open("/tmp/ota-e2e/diff/diffs.json", "w"), ensure_ascii=False)
print(f"比对 {total} 个请求，用了 {time.time() - t0:.0f} 秒，不一样的 {len(diffs)} 个")
for d in diffs[:15]:
    print(" ·", d[0], d[1], d[2], d[3])
    print("    旧", d[4], d[6], d[8][:400])
    print("    新", d[5], d[7], d[9][:400])
sys.exit(1 if diffs else 0)
