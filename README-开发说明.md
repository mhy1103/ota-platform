# 开发说明

在一台新电脑上把项目装好、打包、测试、部署，看这个文件。功能、配置和部署细节在 [README.md](README.md)。

## 目录结构

```
ota-platform/
├── README.md               功能、配置、部署说明
├── README-开发说明.md       本文件
├── build.sh                打包：前端 + 后端，输出 target/ota-platform.jar
├── pom.xml                 后端：Spring Boot 3.2、Java 17
├── src/main/java/          后端代码，按业务分包（每个包管什么见 README「开发 → 项目结构」）
├── src/main/resources/     application.yml、演示数据 demo/、前端打包结果 static/vue/（随代码一起保存）
├── frontend-vue/           后台前端：Vue 3 + Vite，打包到 src/main/resources/static/vue
├── cli/                    命令行 ota：发布更新包、上传安装包（只要 Node，没有依赖）
├── deploy/                 生产配置示例、docker/、sql/（要手动执行的数据库调整）
├── scripts/                setup-toolchain.sh：新电脑装开发环境
├── tests/                  全量回归和测试工具，见 tests/README.md
└── dist/                   打好的 jar（只在导出的压缩包里有，可以直接部署）
```

这些不在压缩包里，也不用带：`target/`（打包产物）、`frontend-vue/node_modules`（打包时自动装）、`data/`（在项目目录里直接运行时的本机数据）、`/tmp/ota-e2e`（测试的工作目录，跑测试时自动生成）。

## 新电脑上手

1. 解压，进到 `ota-platform` 目录。
2. 装开发环境：JDK 17、Maven、Node 20 下载到 `~/Library/Caches/ota-build`，不用 sudo，走国内镜像，几分钟：

   ```bash
   scripts/setup-toolchain.sh
   ```

   已经用 Homebrew 之类装好了 JDK 17、Maven、Node 18+ 的可以跳过，`build.sh` 会用系统里的。
3. 打包：`./build.sh`。第一次要联网下载前端依赖和 Maven 依赖，之后输出 `target/ota-platform.jar`。
4. 跑一遍全量回归，确认环境没问题：`tests/run.sh`，10 套 543 项全部通过就对了。
5. 想在本机打开后台看看：

   ```bash
   ~/Library/Caches/ota-build/jdk-17*/Contents/Home/bin/java -jar target/ota-platform.jar --ota.data-dir=/tmp/ota-dev
   ```

   浏览器打开 http://127.0.0.1:8812，安装引导里选内置数据库、本机存储就行。

在终端里直接用 `java`、`mvn`、`node`（比如只打后端）：

```bash
T=~/Library/Caches/ota-build
export JAVA_HOME=$(ls -d $T/jdk-17*/Contents/Home | tail -1)
export PATH="$JAVA_HOME/bin:$(ls -d $T/apache-maven-* | tail -1)/bin:$(ls -d $T/tools/node-v* | tail -1)/bin:$PATH"
```

## 日常开发

- **后端**：`src/main/java`。只改了 Java 时 `mvn -q clean package -DskipTests` 也能打包，前端打包结果已经在 `src/main/resources/static/vue` 里。
- **前端**：`frontend-vue/src`，改完 `./build.sh`（只打前端：`cd frontend-vue && npm run build`）。组件里用到新的页面字段要重新打包，`tools/fields.mjs` 会把字段统计到 `src/main/resources/vue-fields.json`。
- **文字和英文**：界面用中文写；英文在 `frontend-vue/src/i18n/en.js`（整句对照）和 `index.js`（带数字、名字的句式）。`cd frontend-vue && node tools/i18n-extract.mjs` 列出还没翻译的。
- **数据库**：表和字段启动时自动补齐，一般不用写 SQL。要手动执行的调整（比如删一个唯一索引）写成 SQL 放进 `deploy/sql/`，文件名带日期，开头写清楚执行顺序。
- **测试**：改完跑 `tests/run.sh`。新功能的测试写进 `tests/e2e/`，用法见 [tests/README.md](tests/README.md)。

## 部署到线上

线上是手动换 jar：

1. `./build.sh` 打包（或者直接用压缩包里的 `dist/ota-platform.jar`）。
2. 传到服务器，**先停服务，再换 jar，再启动**。服务运行时直接覆盖 jar 会出错，见 README「升级」。
3. 一般不用执行 SQL。`deploy/sql/` 里现在是 11-13「口令所有 App 通用」那次上线要执行的调整（MySQL 和内置 H2 各一份，重复执行没关系），留着备查。

服务器地址、宝塔面板、数据库、对象存储的账号密码都不在项目里，换电脑要自己带上。

## 压缩包里的 jar

`dist/ota-platform.jar` 是 2026-11-22 打的，包含到「原来的存储连不上时，先确认清空缺文件的版本再切换」为止的全部改动，全量回归 543 项、文件存储测试 43 项都通过。
