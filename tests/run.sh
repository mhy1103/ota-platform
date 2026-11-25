#!/usr/bin/env bash
# 全量回归：10 套测试各起一个全新实例并行跑，一两分钟跑完。tests/run.sh [jar]，不给就用 target/ota-platform.jar
# 要空着端口 18860、18866–18874；实例数据和日志在工作目录（默认 /tmp/ota-e2e，用 OTA_WORK 改）
set -euo pipefail
REPO=$(cd "$(dirname "$0")/.." && pwd)
WORK=${OTA_WORK:-/tmp/ota-e2e}
JAR=${1:-$REPO/target/ota-platform.jar}
[ -f "$JAR" ] || { echo "没有 ${JAR}：先 ./build.sh 打包，或者 tests/run.sh <jar 路径>" >&2; exit 1; }
bash "$REPO/tests/sync.sh"
cp "$JAR" "$WORK/ota-regress.jar"
cd "$WORK"
bash gen/regress.sh "$WORK/ota-regress.jar"
