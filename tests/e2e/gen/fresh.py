"""起一个全新的测试实例（AUTO_SERVER 的 H2）并走完安装：python3 fresh.py 端口 数据目录 [jar]；EXTRA 环境变量是多加的启动参数"""
import os
import shutil
import subprocess
import sys
import time
import urllib.request

port, data = sys.argv[1], sys.argv[2]
jar = sys.argv[3] if len(sys.argv) > 3 else "/tmp/ota-e2e/ota-new.jar"
java = os.path.expanduser("~/Library/Caches/ota-build/jdk-17.0.21+9/Contents/Home/bin/java")


def pids():
    return subprocess.run(["lsof", "-t", "-nP", f"-iTCP:{port}", "-sTCP:LISTEN"], capture_output=True, text=True).stdout.split()


for p in pids():
    subprocess.run(["kill", p])
for _ in range(60):
    if not pids():
        break
    time.sleep(0.5)
shutil.rmtree(data, ignore_errors=True)
os.makedirs(data)
log = open(f"/tmp/ota-e2e/logs/fresh-{port}.log", "w")
url = f"jdbc:h2:file:{data}/db/ota;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"
extra = os.environ.get("EXTRA", "").split()
# 测试脚本兑换完马上确认，默认关掉「确认太快」；要测这条规则的在 EXTRA 里自己给 --ota.min-confirm-ms
if not any(a.startswith("--ota.min-confirm-ms=") for a in extra):
    extra.append("--ota.min-confirm-ms=0")
subprocess.Popen([java, "-Duser.timezone=Asia/Shanghai", "-jar", jar, f"--server.port={port}", f"--ota.data-dir={data}",
                  "--ota.bootstrap-token=test-admin-token", f"--spring.datasource.url={url}"] + extra,
                 stdout=log, stderr=log, cwd="/tmp/ota-e2e", start_new_session=True)
for _ in range(240):
    try:
        if urllib.request.urlopen(f"http://127.0.0.1:{port}/install/status", timeout=2).status == 200:
            break
    except Exception:
        pass
    time.sleep(0.5)
if os.environ.get("NOINSTALL"):
    print("没安装，起好了")
    sys.exit(0)
env = dict(os.environ, OTA_BASE=f"http://127.0.0.1:{port}")
sys.exit(subprocess.run(["python3", "/tmp/ota-e2e/install_fresh.py"], env=env, cwd="/tmp/ota-e2e").returncode)
