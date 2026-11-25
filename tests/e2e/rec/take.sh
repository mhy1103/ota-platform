#!/usr/bin/env bash
# 录一遍：先停掉别的录像进程、删掉上次加的激活，等页面数据回到今天 8 台再开录。take.sh 配置.json
set -uo pipefail
pkill -f "/tmp/ota-e2e/rec/record " 2>/dev/null; sleep 1
cd /tmp/ota-e2e
export OTA_BASE=http://127.0.0.1:18880 OTA_DATA=/tmp/ota-e2e/ring80
python3 ring_demo.py reset >/dev/null
for i in $(seq 1 30); do
  s=$(python3 ring_demo.py share)
  echo "$s" | grep -q "'scToday': 8," && break
  sleep 2
done
echo "开录前：$s"
/tmp/ota-e2e/rec/record "$1" 2>&1 | grep -E "刷新|第 .* 步|帧|写完|错"
