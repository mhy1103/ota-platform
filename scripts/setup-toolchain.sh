#!/usr/bin/env bash
# 新电脑装开发环境：JDK 17、Maven、Node 20 下载到 ~/Library/Caches/ota-build（不用 sudo，不动系统里装的），
# build.sh 和测试脚本会自动用它们。先走国内镜像，不通再走官方地址；已经装过的跳过。
set -euo pipefail
T="$HOME/Library/Caches/ota-build"
mkdir -p "$T/tools"
case "$(uname -s)-$(uname -m)" in
  Darwin-arm64) JARCH=aarch64; NARCH=darwin-arm64 ;;
  Darwin-x86_64) JARCH=x64; NARCH=darwin-x64 ;;
  *) echo "这个脚本只管 macOS；其他系统自己装 JDK 17、Maven 3.9、Node 20" >&2; exit 1 ;;
esac
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# 依次试几个地址，下到一个就行
fetch() {
  local out=$1; shift
  for url in "$@"; do
    echo "下载 $url"
    if curl -fL --retry 2 --connect-timeout 15 -o "$out" "$url"; then return 0; fi
  done
  echo "都下载不了，检查网络后重试" >&2
  return 1
}

JDK=17.0.21_9
J=$(ls -d "$T"/jdk-17*/Contents/Home 2>/dev/null | tail -1 || true)
if [ -n "$J" ] && "$J/bin/java" -version >/dev/null 2>&1; then
  echo "JDK 17 已经有了"
else
  fetch "$TMP/jdk.tar.gz" \
    "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/$JARCH/mac/OpenJDK17U-jdk_${JARCH}_mac_hotspot_$JDK.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/17/ga/mac/$JARCH/jdk/hotspot/normal/eclipse"
  tar -xzf "$TMP/jdk.tar.gz" -C "$T"
fi

MVN=3.9.16
if ls -d "$T"/apache-maven-* >/dev/null 2>&1; then
  echo "Maven 已经有了"
else
  fetch "$TMP/maven.tar.gz" \
    "https://mirrors.tuna.tsinghua.edu.cn/apache/maven/maven-3/$MVN/binaries/apache-maven-$MVN-bin.tar.gz" \
    "https://archive.apache.org/dist/maven/maven-3/$MVN/binaries/apache-maven-$MVN-bin.tar.gz"
  tar -xzf "$TMP/maven.tar.gz" -C "$T"
fi

NODE=20.18.1
N=$(ls -d "$T"/tools/node-v* 2>/dev/null | tail -1 || true)
# 只解压了一部分的 Node 没有 npm，打包前端时装不了依赖：重新解压一遍补齐
if [ -n "$N" ] && PATH="$N/bin:$PATH" "$N/bin/npm" -v >/dev/null 2>&1; then
  echo "Node 已经有了"
else
  fetch "$TMP/node.tar.gz" \
    "https://registry.npmmirror.com/-/binary/node/v$NODE/node-v$NODE-$NARCH.tar.gz" \
    "https://nodejs.org/dist/v$NODE/node-v$NODE-$NARCH.tar.gz"
  tar -xzf "$TMP/node.tar.gz" -C "$T/tools"
fi

# Maven 依赖走阿里云镜像。已经有自己的 settings.xml 就不动
if [ ! -f "$HOME/.m2/settings.xml" ]; then
  mkdir -p "$HOME/.m2"
  cat > "$HOME/.m2/settings.xml" <<'EOF'
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF
  echo "写了 ~/.m2/settings.xml：Maven 依赖从阿里云镜像下载"
fi

J=$(ls -d "$T"/jdk-17*/Contents/Home | tail -1)
M=$(ls -d "$T"/apache-maven-* | tail -1)
N=$(ls -d "$T"/tools/node-v* | tail -1)
echo
"$J/bin/java" -version 2>&1 | head -1
JAVA_HOME="$J" "$M/bin/mvn" -v 2>/dev/null | head -1
echo "node $("$N/bin/node" -v)"
echo
echo "装好了。在项目根目录：./build.sh 打包，tests/run.sh 跑全量回归"
