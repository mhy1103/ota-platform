#!/usr/bin/env bash
# 全部回归：每套测试一个全新实例，装好就跑，跑完停掉。regress.sh <jar>
set -uo pipefail
JAR=${1:-/tmp/ota-e2e/ota-final-share.jar}
cd /tmp/ota-e2e
F="python3 gen/fresh.py"
$F 18860 /tmp/ota-e2e/share60 "$JAR" > logs/fresh-run-18860.txt 2>&1 &
$F 18866 /tmp/ota-e2e/reg66 "$JAR" > logs/fresh-run-18866.txt 2>&1 &
$F 18867 /tmp/ota-e2e/reg67 "$JAR" > logs/fresh-run-18867.txt 2>&1 &
$F 18868 /tmp/ota-e2e/reg68 "$JAR" > logs/fresh-run-18868.txt 2>&1 &
$F 18869 /tmp/ota-e2e/reg69 "$JAR" > logs/fresh-run-18869.txt 2>&1 &
$F 18870 /tmp/ota-e2e/reg70 "$JAR" > logs/fresh-run-18870.txt 2>&1 &
NOINSTALL=1 $F 18871 /tmp/ota-e2e/bk/data "$JAR" > logs/fresh-run-18871.txt 2>&1 &
$F 18872 /tmp/ota-e2e/reg72 "$JAR" > logs/fresh-run-18872.txt 2>&1 &
$F 18873 /tmp/ota-e2e/reg73 "$JAR" > logs/fresh-run-18873.txt 2>&1 &
EXTRA="--ota.min-confirm-ms=2000" $F 18874 /tmp/ota-e2e/reg74 "$JAR" > logs/fresh-run-18874.txt 2>&1 &
wait
run() { local name=$1 port=$2 script=$3; shift 3; env OTA_BASE=http://127.0.0.1:$port "$@" python3 $script > logs/t-$name.txt 2>&1; echo "$name $(tail -1 logs/t-$name.txt)"; }
run share 18860 e2e_share.py OTA_DATA=/tmp/ota-e2e/share60 &
run r22 18866 e2e_round22.py OTA_DATA=/tmp/ota-e2e/reg66 OTA_JAR="$JAR" &
run one 18867 e2e_onedevice.py OTA_DATA=/tmp/ota-e2e/reg67 &
run sub 18868 e2e_subagent.py &
run lerr 18869 e2e_launcherr.py &
run r20 18870 e2e_round20.py &
run r21 18871 e2e_round21.py &
run both 18872 e2e_both.py &
run gone 18873 e2e_livegone.py &
run fast 18874 e2e_fastconfirm.py &
wait
for p in 18860 18866 18867 18868 18869 18870 18871 18872 18873 18874; do for pid in $(lsof -t -nP -iTCP:$p -sTCP:LISTEN); do kill $pid; done; done
echo all-done
