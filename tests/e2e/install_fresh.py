"""全新实例走一遍安装引导（H2、本机存储、管理员 admin / admin-pass-123），等它重启好：OTA_BASE=... python3 install_fresh.py"""
from lib import *

s0 = json.loads(plain("GET", "/install/status").body)
if s0["installed"]:
    print("已经装好了")
    sys.exit(0)
body = {"db": {"type": "h2"}, "dataMode": "keep", "restrictDomains": False, "domains": [], "publicUrl": BASE,
        "displayZone": "Asia/Shanghai", "storage": {"type": "local"}, "adminUsername": "admin",
        "adminName": "管理员", "adminPassword": "admin-pass-123", "adminPassword2": "admin-pass-123"}
plain("POST", "/install/submit", headers={"Content-Type": "application/json"}, data=json.dumps(body).encode())
for _ in range(160):
    try:
        s = json.loads(plain("GET", "/install/status").body)
        if s["installed"] and s.get("boot") != s0.get("boot"):
            print("安装完成")
            sys.exit(0)
    except Exception:
        pass
    time.sleep(0.3)
print("等安装超时")
sys.exit(1)
