#!/usr/bin/env bash
# 打包：先构建后台前端（frontend-vue），再打 jar，输出 target/ota-platform.jar
# 需要 JDK 17、Maven 和 Node.js 18+：scripts/setup-toolchain.sh 装的会自动用上，没装就用系统里的。
# 额外参数原样传给 mvn，比如 ./build.sh -o
set -euo pipefail
cd "$(dirname "$0")"
T="$HOME/Library/Caches/ota-build"
J=$(ls -d "$T"/jdk-17*/Contents/Home 2>/dev/null | tail -1 || true)
M=$(ls -d "$T"/apache-maven-* 2>/dev/null | tail -1 || true)
N=$(ls -d "$T"/tools/node-v* 2>/dev/null | tail -1 || true)
if [ -n "$J" ]; then export JAVA_HOME="$J" PATH="$J/bin:$PATH"; fi
if [ -n "$M" ]; then export PATH="$M/bin:$PATH"; fi
if [ -n "$N" ]; then export PATH="$N/bin:$PATH"; fi
command -v node >/dev/null 2>&1 || { echo "需要 Node.js 18+（打包前端用）：先运行 scripts/setup-toolchain.sh" >&2; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo "需要 Maven 和 JDK 17：先运行 scripts/setup-toolchain.sh" >&2; exit 1; }
(cd frontend-vue && { [ -d node_modules ] || npm ci --no-audit --no-fund; } && npm run build)
mvn -q clean package -DskipTests "$@"
ls -lh target/ota-platform.jar
