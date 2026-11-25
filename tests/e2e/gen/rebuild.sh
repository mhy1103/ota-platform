#!/usr/bin/env bash
# 开发时打包：前端 vite build + mvn package，输出 target/ota-platform.jar。--java 只打后端（前端没改时快一些）
# 用 scripts/setup-toolchain.sh 装的 JDK、Maven、Node，没装就用系统的；Maven 先离线打，缺依赖再联网下载
set -euo pipefail
T=~/Library/Caches/ota-build
J=$(ls -d "$T"/jdk-17*/Contents/Home 2>/dev/null | tail -1 || true)
M=$(ls -d "$T"/apache-maven-* 2>/dev/null | tail -1 || true)
N=$(ls -d "$T"/tools/node-v* 2>/dev/null | tail -1 || true)
if [ -n "$J" ]; then export JAVA_HOME="$J" PATH="$J/bin:$PATH"; fi
if [ -n "$M" ]; then export PATH="$M/bin:$PATH"; fi
if [ -n "$N" ]; then export PATH="$N/bin:$PATH"; fi
cd /Users/chuzu/shangjia/ota-platform
if [ "${1:-}" != "--java" ]; then
  (cd frontend-vue && { [ -d node_modules ] || npm ci --no-audit --no-fund; } && node tools/fields.mjs && node node_modules/vite/bin/vite.js build) >/tmp/ota-e2e/logs/vite.log 2>&1 || { tail -40 /tmp/ota-e2e/logs/vite.log; exit 1; }
fi
mvn -q -o clean package -DskipTests >/tmp/ota-e2e/logs/mvn.log 2>&1 || mvn -q clean package -DskipTests 2>&1 | tail -40
ls -la target/ota-platform.jar | awk '{print $5, $6, $7, $8}'
