"""在慢核上启动几次，量从启动到能打开登录页的时间：python3 gen/boottime.py 名字 [多加的参数…]"""
import subprocess, os, sys, time, urllib.request
java = os.path.expanduser("~/Library/Caches/ota-build/jdk-17.0.21+9/Contents/Home/bin/java")
name, extra = sys.argv[1], sys.argv[2:]
jvm = [a for a in extra if a.startswith("-X") or a.startswith("-D")]
app = [a for a in extra if a.startswith("--")]
jar = os.environ.get("JAR", "/tmp/ota-e2e/ota-settle.jar")
data = "/tmp/ota-e2e/boot94"
times = []
for i in range(int(os.environ.get("RUNS", "2"))):
    log = open(f"/tmp/ota-e2e/logs/boottime-{name}.log", "w")
    t0 = time.time()
    if os.environ.get("EXPLODED"):
        main = ["-cp", os.environ.get("CP", "BOOT-INF/classes:BOOT-INF/lib/*"), "com.ota.platform.OtaPlatformApplication"]
        cwd = os.environ["EXPLODED"]
    else:
        main = ["-jar", jar]
        cwd = "/tmp/ota-e2e"
    p = subprocess.Popen(["taskpolicy", "-b", java, "-Duser.timezone=Asia/Shanghai"] + jvm + main + ["--server.port=18894", f"--ota.data-dir={data}"] + app,
                         stdout=log, stderr=log, cwd=cwd, start_new_session=True)
    ok = False
    for _ in range(1200):
        try:
            if urllib.request.urlopen("http://127.0.0.1:18894/login", timeout=2).status == 200:
                ok = True
                break
        except Exception:
            pass
        if p.poll() is not None:
            break
        time.sleep(0.1)
    times.append(time.time() - t0 if ok else -1)
    p.terminate()
    p.wait(timeout=60)
print(f"{name:<28} " + "  ".join(f"{t:5.1f}s" for t in times))
