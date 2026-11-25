#!/usr/bin/env bash
# 把 tests/e2e 放进工作目录（默认 /tmp/ota-e2e，用 OTA_WORK 改），脚本里写死的原来那台电脑的路径
# （项目位置、工作目录、JDK）换成这台的。测试都在工作目录里跑，实例数据、日志、jar 都在那里，不进项目。
set -euo pipefail
REPO=$(cd "$(dirname "$0")/.." && pwd)
WORK=${OTA_WORK:-/tmp/ota-e2e}
mkdir -p "$WORK/logs" "$WORK/h2"
cp -R "$REPO/tests/e2e/." "$WORK/"

# JDK 17：先找 scripts/setup-toolchain.sh 装的，再看 JAVA_HOME，最后用 PATH 里的
JAVA=$(ls -d ~/Library/Caches/ota-build/jdk-17*/Contents/Home/bin/java 2>/dev/null | tail -1 || true)
if [ -z "$JAVA" ] && [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVA="$JAVA_HOME/bin/java"; fi
if [ -z "$JAVA" ]; then JAVA=$(command -v java || true); fi
if [ -z "$JAVA" ]; then echo "找不到 Java 17：先运行 scripts/setup-toolchain.sh" >&2; exit 1; fi

# H2 命令行：有几个测试直接改测试库。Maven 打过包本地仓库里就有，没有就从阿里云镜像下载
H2="$WORK/h2/h2-2.2.224.jar"
if [ ! -s "$H2" ]; then
  M2="$HOME/.m2/repository/com/h2database/h2/2.2.224/h2-2.2.224.jar"
  if [ -s "$M2" ]; then
    cp "$M2" "$H2"
  else
    curl -fsSL -o "$H2" https://maven.aliyun.com/repository/public/com/h2database/h2/2.2.224/h2-2.2.224.jar
  fi
fi

cd "$REPO/tests/e2e"
find . -type f \( -name '*.py' -o -name '*.sh' -o -name '*.mjs' \) | while read -r f; do
  OLD_REPO=/Users/chuzu/shangjia/ota-platform NEW_REPO="$REPO" \
  OLD_JAVA='~/Library/Caches/ota-build/jdk-17.0.21+9/Contents/Home/bin/java' NEW_JAVA="$JAVA" \
  OLD_WORK=/tmp/ota-e2e NEW_WORK="$WORK" \
    perl -pi -e 's/\Q$ENV{OLD_REPO}\E/$ENV{NEW_REPO}/g; s/\Q$ENV{OLD_JAVA}\E/$ENV{NEW_JAVA}/g; s/\Q$ENV{OLD_WORK}\E/$ENV{NEW_WORK}/g' "$WORK/$f"
done
echo "测试脚本放好了：${WORK}（项目 ${REPO}，Java ${JAVA}）"
