# 测试

全量回归和平时排查用的测试脚本，Python 3 和 Node 写的，不用另外装依赖。

脚本是按「工作目录」写的：`tests/sync.sh` 把 `tests/e2e/` 复制到 `/tmp/ota-e2e`（用环境变量 `OTA_WORK` 可以换），顺便把脚本里原来那台电脑的路径（项目位置、JDK、工作目录）换成这台的。测试实例的数据、日志、测试用的 jar 都在工作目录里，不进项目。`/tmp` 重启电脑会清空，所以**改脚本只改 `tests/e2e/` 里的**，改完再 sync。

## 全量回归

```bash
./build.sh          # 先打包，得到 target/ota-platform.jar
tests/run.sh        # 也可以指定 jar：tests/run.sh dist/ota-platform.jar
```

10 套测试共 543 项，每套起一个全新实例（内置 H2 数据库，走一遍安装引导）并行跑，一两分钟跑完。每行是一套的结果，最后一行 `all-done`：

```
share 通过 85 项，全部通过
sub 通过 112 项，全部通过
…
```

要空着端口 18860、18866–18874。哪套没过就看 `/tmp/ota-e2e/logs/t-<名字>.txt`（测试输出）和 `/tmp/ota-e2e/logs/fresh-<端口>.log`（实例日志）。

| 名字 | 脚本 | 测什么 |
| --- | --- | --- |
| share | `e2e_share.py` | 数据大屏分享 |
| r22 | `e2e_round22.py` | 二级代理扣量比例，升级后按操作日志还原 |
| one | `e2e_onedevice.py` | 同一台设备只算一次激活 |
| sub | `e2e_subagent.py` | 二级代理：开权限、分口令、扣量、数据更新方式 |
| lerr | `e2e_launcherr.py` | 启动错误补报 |
| r20 | `e2e_round20.py` | 口令更新包 |
| r21 | `e2e_round21.py` | 备份恢复、安装重启、重新安装 |
| both | `e2e_both.py` | 发布：同一份内容不复制，常规更新和口令更新一起发 |
| gone | `e2e_livegone.py` | 线上版本列表 |
| fast | `e2e_fastconfirm.py` | 兑换后确认太快判为非正常 |

## 单独跑一套

```bash
tests/sync.sh
cd /tmp/ota-e2e
python3 gen/fresh.py 18875 /tmp/ota-e2e/t75 /path/to/ota-platform.jar   # 起一个装好的全新实例
OTA_BASE=http://127.0.0.1:18875 python3 e2e_round20.py
```

测试实例的管理员是 admin / admin-pass-123，管理令牌 test-admin-token（只在测试实例里有）。

## 不在全量回归里的

文件存储切换 `e2e_storage.py`（43 项）：要一个模拟的对象存储和一个还没安装过的实例。

```bash
tests/sync.sh
cd /tmp/ota-e2e
python3 s3mock.py 18861 TESTAK testsecret123 ota-bucket &
java -jar /path/to/ota-platform.jar --server.port=18875 --ota.data-dir=/tmp/ota-e2e/st75 --ota.bootstrap-token=test-admin-token &
export OTA_BASE=http://127.0.0.1:18875 S3_BASE=http://127.0.0.1:18861
python3 e2e_storage.py --setup && python3 e2e_storage.py
python3 st_down.py      # 对象存储整个停了的情况，会把 s3mock 停掉，放最后跑
```

另外几个：`e2e_link.py`（外部链接版本，配合 `slowcdn.py`）、`e2e_copy.py`（发布时什么时候复制成新版本）、`fp_keep_check.py`（指纹保留改短时马上清理，在已有数据的实例上跑，见脚本开头）。

## 英文翻译检查

```bash
tests/sync.sh
node /tmp/ota-e2e/i18n_v2_check.mjs         # 新界面的文字
node /tmp/ota-e2e/i18n_sub_check.mjs        # 二级代理
node /tmp/ota-e2e/i18n_storage_check.mjs    # 文件存储切换
(cd frontend-vue && node tools/i18n-extract.mjs)   # 统计还没翻译的中文
```

## 其他文件

| 文件 | 用途 |
| --- | --- |
| `lib.py` | 公共函数：服务地址和管理令牌从环境变量读，`check` / `section` / `done` 统计结果 |
| `install_fresh.py`、`gen/fresh.py` | 起全新实例并走完安装引导 |
| `gen/regress.sh` | 全量回归（`tests/run.sh` 调它） |
| `gen/rebuild.sh` | 开发时打包，`--java` 只打后端 |
| `gen/boottime.py` | 量启动时间 |
| `s3mock.py`、`slowcdn.py`、`proxy.py` | 模拟对象存储（校验签名）、慢 CDN、宝塔 Nginx / WAF 反向代理 |
| `prod_manifest_check.py` | 像 App 一样请求线上的更新清单，看下载地址落在哪（不计入统计） |
| `legacy_cmp.py`、`diff/` | 升级前后的数据、接口响应逐条对比 |
| `seed_history.py`、`seed_subagent_ui.py`、`act_today.py`、`ring_demo.py` | 给界面检查、录视频造数据 |
| `st_confirm.py` | 把实例摆到「原来的存储连不上，等确认清空」，看界面用 |
| `shots/` | 浏览器截图（CDP 返回的 JSON）解码成图片 |
| `rec/` | 录网页视频、取帧、拼图（Swift，只能在 macOS 上编译） |
| `hdrtest/` | 检查请求头能不能发出去 |
| `redeclare.mjs` | 找页面脚本里同一个函数重复声明的 var |
