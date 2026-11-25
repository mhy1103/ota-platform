#!/usr/bin/env node
// OTA Platform 命令行：导出、上传、发布、回滚。Node.js 18+，无第三方依赖。
import { createHash } from 'node:crypto';
import { chmod, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { homedir, tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { spawn } from 'node:child_process';

const CONFIG_FILE = join(homedir(), '.ota', 'config.json');
const VERSION = '1.0.0';

const HELP = `用法：ota <命令> [参数]

  login     --server <地址> --token <令牌>        保存服务地址和令牌到 ~/.ota/config.json
  apps                                            列出能访问的 App
  publish   --app <标识> [--dir dist] [--platform all|ios|android] [--channel production]
            [--rollout 100] [--message 说明] [--force-update] [--runtime 1.0.0] [--no-deploy]
            [--as hot|activation]
                                                  导出并发布；不带 --dir 时在当前目录运行 npx expo export；
                                                  --as activation 发成口令更新（新装 App 输入口令激活时下载），默认是常规更新
  releases  --app <标识> [--limit 20]             最近的版本、各渠道的线上版本和口令更新包
  deploy    --app <标识> --release <ID> [--channel production] [--rollout 100] [--as hot|activation]
                                                  发布已登记的版本，或调整灰度比例；--as activation 发成口令更新
  activation clear --app <标识> --runtime <版本> [--channel production]
                                                  取消口令更新包，激活时改回拿常规更新包
  rollback  --app <标识> --runtime <版本> [--channel production] [--to <版本 ID>] [--embedded]
                                                  回滚到上一版本；--to 回滚到更早的任意版本（可以只写前 8 位）；
                                                  --embedded 回退到安装包内置版本
  verify    --app <标识> --release <ID>            校验文件、哈希、Runtime 和签名
  stats     --app <标识>                           近 30 天设备的版本分布
  cert      --app <标识> [--out certs/certificate.pem]  下载 App 内置用的证书
  installer upload --app <标识> <文件> [--note 说明]   上传 APK / IPA（自动分片），显示检查结果；有不通过的项退出码 2
  installer list   --app <标识>                       已上传的安装包和检查结果
  schedule list    --app <标识>                       等执行和最近执行过的计划任务
  schedule rollout --app <标识> --runtime <版本> --percent <比例> --at <时间> [--channel production]
                                                  到时间把灰度调到这个比例（时间如 "2026-11-13 10:00"，按后台时区）
  schedule mode    --app <标识> --face A|B --at <时间>  到时间切换 A/B 面（要管理令牌）
  schedule cancel  --app <标识> <计划 ID>              取消还没执行的计划

通用参数：--server、--token 覆盖已保存的配置；也可以用环境变量 OTA_SERVER、OTA_TOKEN。--json 输出 JSON。`;

// ==================== 入口 ====================

const args = parseArgs(process.argv.slice(2));
const command = args._[0];
const commands = { login, apps, publish, releases, deploy, activation, rollback, verify, stats, cert, installer, schedule };

if (!command || args.help || command === 'help') {
  console.log(HELP);
  process.exit(0);
}
if (args.version || command === 'version') {
  console.log(VERSION);
  process.exit(0);
}
if (!commands[command]) fail(`不认识的命令 ${command}，运行 ota help 查看用法`);
commands[command](args).catch((e) => fail(e.message));

// ==================== 命令 ====================

async function login(a) {
  const server = need(a.server, '--server').replace(/\/+$/, '');
  const token = need(a.token, '--token');
  const c = makeClient(server, token);
  const list = await c.call('GET', '/apps');
  await mkdir(dirname(CONFIG_FILE), { recursive: true });
  await writeFile(CONFIG_FILE, JSON.stringify({ server, token }, null, 2) + '\n');
  await chmod(CONFIG_FILE, 0o600).catch(() => {});
  log(`已登录 ${server}，能访问 ${list.length} 个 App：${list.map((x) => x.slug).join('、') || '无'}`);
}

async function apps(a) {
  const c = await client(a);
  const list = await c.call('GET', '/apps');
  if (a.json) return out(list);
  if (!list.length) return log('没有能访问的 App');
  table(['标识', '名称', 'keyid', '模式'], list.map((x) => [x.slug, x.name, x.keyId, x.redemptionOpen ? 'B 面' : 'A 面']));
}

async function publish(a) {
  const app = need(a.app, '--app');
  const c = await client(a);
  const platform = a.platform && a.platform !== true ? String(a.platform) : 'all';
  if (!['all', 'ios', 'android'].includes(platform)) fail('--platform 只能是 all、ios、android');

  const project = a.project && a.project !== true ? resolve(String(a.project)) : process.cwd();
  let dir = a.dir && a.dir !== true ? resolve(String(a.dir)) : null;
  let tmp = null;
  if (!dir) {
    tmp = await mkdtemp(join(tmpdir(), 'ota-export-'));
    log(`导出：npx expo export --output-dir ${tmp} --platform ${platform}`);
    await run('npx', ['expo', 'export', '--output-dir', tmp, '--platform', platform], project);
    dir = tmp;
  }
  try {
    const metadata = JSON.parse(await readFile(join(dir, 'metadata.json'), 'utf8').catch(() => fail(`${dir} 里没有 metadata.json，不是 npx expo export 导出的目录`)));
    let expoConfig = await readFile(join(dir, 'expoConfig.json'), 'utf8').then(JSON.parse, () => null);
    if (!expoConfig) expoConfig = await projectConfig(project);
    const runtime = a.runtime && a.runtime !== true ? String(a.runtime) : runtimeOf(expoConfig);
    if (!runtime) fail('识别不到 Runtime 版本（expoConfig.json 里没有 runtimeVersion 或 version），用 --runtime 指定');

    const meta = filterPlatforms(metadata, platform);
    const paths = listFiles(meta);
    if (!paths.length) fail('metadata.json 里没有 ios 或 android 的文件');
    const files = {};
    const bySha = new Map();
    let total = 0;
    for (const p of paths) {
      const buf = await readFile(join(dir, p)).catch(() => fail(`缺少文件 ${p}`));
      const sha = createHash('sha256').update(buf).digest('hex');
      files[p] = sha;
      if (!bySha.has(sha)) {
        bySha.set(sha, p);
        total += buf.length;
      }
    }
    const { missing } = await c.call('POST', `/apps/${app}/assets/missing`, { sha256: [...bySha.keys()] });
    log(`${paths.length} 个文件，共 ${size(total)}；服务端已有 ${bySha.size - missing.length} 个，需要上传 ${missing.length} 个`);
    let uploaded = 0;
    let bytes = 0;
    await pool(missing, 4, async (sha) => {
      const buf = await readFile(join(dir, bySha.get(sha)));
      await c.call('PUT', `/apps/${app}/assets/${sha}`, null, buf);
      uploaded++;
      bytes += buf.length;
      progress(`上传 ${uploaded}/${missing.length}（${size(bytes)}）`);
    });
    if (missing.length) progressEnd();

    const message = a.message && a.message !== true ? String(a.message) : undefined;
    const release = await c.call('POST', `/apps/${app}/releases`, {
      runtimeVersion: runtime, message, forceUpdate: !!a['force-update'], metadata: meta, expoConfig, files,
    });
    log(`已登记版本 ${release.id.slice(0, 8)}（Runtime ${runtime}，${release.platforms.join('、')}）`);
    let deployment = null;
    if (a.deploy !== false) {
      const channel = a.channel && a.channel !== true ? String(a.channel) : 'production';
      if (asActivation(a)) {
        deployment = await c.call('POST', `/apps/${app}/channels/${channel}/deploy`, { releaseId: release.id, as: 'activation' });
        log(`已设为 ${channel} 的口令更新包，新装 App 输入口令激活时下载它`);
      } else {
        const rollout = a.rollout ? Number(a.rollout) : 100;
        deployment = await c.call('POST', `/apps/${app}/channels/${channel}/deploy`, { releaseId: release.id, rolloutPercent: rollout });
        log(`已发布到 ${channel}${rollout < 100 ? `，灰度 ${rollout}%` : ''}`);
      }
    }
    if (a.json) out({ release, deployment, uploaded: missing.length });
  } finally {
    if (tmp) await rm(tmp, { recursive: true, force: true });
  }
}

async function releases(a) {
  const app = need(a.app, '--app');
  const c = await client(a);
  const limit = a.limit ? Number(a.limit) : 20;
  const [list, detail] = await Promise.all([c.call('GET', `/apps/${app}/releases?limit=${limit}`), c.call('GET', `/apps/${app}`)]);
  const live = new Map();
  for (const ch of detail.channels) {
    for (const d of ch.deployments) {
      if (d.activationReleaseId) live.set(d.activationReleaseId, [...(live.get(d.activationReleaseId) || []), `${ch.name} 口令更新包`]);
      if (d.rollbackToEmbedded || !d.releaseId) continue;
      const tag = ch.name + (d.rolloutPercent < 100 ? ` ${d.rolloutPercent}%` : '');
      live.set(d.releaseId, [...(live.get(d.releaseId) || []), tag]);
    }
  }
  if (a.json) return out({ releases: list, channels: detail.channels });
  if (!list.length) return log('还没有版本');
  table(['版本', 'Runtime', '平台', '来源', '时间', '线上', '说明'], list.map((r) => [
    r.id.slice(0, 8), r.runtimeVersion, r.platforms.join(','), r.source, fmtTime(r.createdAt),
    (live.get(r.id) || []).join(' ') || '—', r.message || '',
  ]));
}

async function deploy(a) {
  const app = need(a.app, '--app');
  const c = await client(a);
  const id = await resolveRelease(c, app, need(a.release, '--release'));
  const channel = a.channel && a.channel !== true ? String(a.channel) : 'production';
  if (asActivation(a)) {
    const d = await c.call('POST', `/apps/${app}/channels/${channel}/deploy`, { releaseId: id, as: 'activation' });
    if (a.json) return out(d);
    return log(d.changed ? `已把 ${id.slice(0, 8)} 设为 ${channel} 的口令更新包，新装 App 输入口令激活时下载它` : `${id.slice(0, 8)} 本来就是 ${channel} 的口令更新包`);
  }
  const rollout = a.rollout ? Number(a.rollout) : 100;
  const d = await c.call('POST', `/apps/${app}/channels/${channel}/deploy`, { releaseId: id, rolloutPercent: rollout });
  if (a.json) return out(d);
  log(`已发布 ${d.releaseId.slice(0, 8)} 到 ${channel}${rollout < 100 ? `，灰度 ${rollout}%` : ''}`
    + (d.republished ? `（它比线上版本旧，复制成了新版本 ${d.releaseId.slice(0, 8)}）` : ''));
}

function asActivation(a) {
  const v = a.as && a.as !== true ? String(a.as).trim().toLowerCase() : 'hot';
  if (v !== 'hot' && v !== 'activation') fail('--as 只能是 hot（常规更新，默认）或 activation（口令更新）');
  return v === 'activation';
}

async function activation(a) {
  const sub = a._[1];
  if (sub !== 'clear') fail('用法：ota activation clear --app <标识> --runtime <版本> [--channel production]');
  const app = need(a.app, '--app');
  const c = await client(a);
  const runtime = String(need(a.runtime, '--runtime'));
  const channel = a.channel && a.channel !== true ? String(a.channel) : 'production';
  const d = await c.call('POST', `/apps/${app}/channels/${channel}/activation/clear`, { runtimeVersion: runtime });
  if (a.json) return out(d);
  log(`已取消 ${channel} 的口令更新包（Runtime ${d.runtimeVersion}），激活时改回拿常规更新包`);
}

async function rollback(a) {
  const app = need(a.app, '--app');
  const c = await client(a);
  const runtime = String(need(a.runtime, '--runtime'));
  const channel = a.channel && a.channel !== true ? String(a.channel) : 'production';
  if (a.embedded) {
    const d = await c.call('POST', `/apps/${app}/channels/${channel}/rollback-embedded`, { runtimeVersion: runtime });
    if (a.json) return out(d);
    return log(`${channel}（Runtime ${runtime}）的设备下次检查更新时回到安装包内置版本；再发布任意版本即可恢复`);
  }
  const to = a.to && a.to !== true ? String(a.to) : undefined;
  const d = await c.call('POST', `/apps/${app}/channels/${channel}/rollback`, { runtimeVersion: runtime, to });
  if (a.json) return out(d);
  log(`已回滚 ${channel}（Runtime ${runtime}）${to ? `到 ${to.slice(0, 8)}` : '到上一版本'}`
    + (d.republished ? `，复制成新版本 ${d.releaseId.slice(0, 8)} 发布` : ''));
}

async function verify(a) {
  const app = need(a.app, '--app');
  const c = await client(a);
  const id = await resolveRelease(c, app, need(a.release, '--release'));
  const r = await c.call('POST', `/apps/${app}/releases/${id}/verify`, {});
  if (a.json) return out(r);
  log(`${r.headline}（${id.slice(0, 8)}）`);
  const mark = { ok: '✓', warn: '!', error: '✗', skip: '-' };
  for (const ch of r.checks) log(`  ${mark[ch.state] || '?'} ${ch.label}：${ch.text}`);
  if (r.level === 'error') process.exitCode = 1;
}

async function stats(a) {
  const app = need(a.app, '--app');
  const c = await client(a);
  const s = await c.call('GET', `/apps/${app}/stats`);
  if (a.json) return out(s);
  log(`近 30 天 ${s.activeDevices} 台设备检查过更新`);
  const rows = Object.entries(s.byUpdate).sort((x, y) => y[1] - x[1]).map(([id, n]) => [id ? id.slice(0, 8) : '（内置版本或未知）', String(n)]);
  if (rows.length) table(['正在运行的版本', '设备'], rows);
  const plat = Object.entries(s.byPlatform).map(([p, n]) => `${p} ${n}`).join('，');
  if (plat) log(`平台：${plat}`);
}

async function cert(a) {
  const app = need(a.app, '--app');
  const c = await client(a);
  const pem = await c.call('GET', `/apps/${app}/certificate`, undefined, undefined, true);
  if (!a.out || a.out === true) return process.stdout.write(pem);
  const file = resolve(String(a.out));
  await mkdir(dirname(file), { recursive: true });
  await writeFile(file, pem);
  log(`已保存证书到 ${file}`);
}

async function schedule(a) {
  const sub = a._[1] || 'list';
  const app = need(a.app, '--app');
  const c = await client(a);
  const when = (iso) => String(iso || '').slice(0, 16).replace('T', ' ');
  if (sub === 'list') {
    const r = await c.call('GET', `/apps/${app}/schedules`);
    if (a.json) return out(r);
    if (!r.pending.length && !r.recent.length) return log('没有计划任务');
    const st = { pending: '等执行', done: '已执行', failed: '没执行', cancelled: '已取消' };
    table(['ID', '时间', '内容', '状态', '结果'], [...r.pending, ...r.recent].map((t) => [String(t.id), when(t.runAt), t.what, st[t.status] || t.status,
      t.result || (t.createdBy ? `${t.createdBy} 设置` : '')]));
    return;
  }
  if (sub === 'cancel') {
    const t = await c.call('DELETE', `/apps/${app}/schedules/${need(a._[2], '计划 ID')}`);
    if (a.json) return out(t);
    return log(`已取消：${when(t.runAt)} ${t.what}`);
  }
  const at = String(need(a.at, '--at')).trim().replace(' ', 'T');
  const runAt = /T\d\d:\d\d$/.test(at) ? `${at}:00` : at;
  let body;
  if (sub === 'rollout') {
    body = { type: 'rollout', runAt, channel: a.channel && a.channel !== true ? String(a.channel) : 'production',
      runtimeVersion: String(need(a.runtime, '--runtime')), percent: Number(need(a.percent, '--percent')) };
  } else if (sub === 'mode') {
    body = { type: 'mode', runAt, face: String(need(a.face, '--face')).toUpperCase() };
  } else {
    fail('用法：ota schedule list|rollout|mode|cancel --app <标识>，运行 ota help 查看参数');
  }
  const t = await c.call('POST', `/apps/${app}/schedules`, body);
  if (a.json) return out(t);
  log(`已设好：${when(t.runAt)} ${t.what}（计划 ID ${t.id}），到时间自动执行`);
}

// ==================== 服务端 ====================

async function client(a) {
  const cfg = await readFile(CONFIG_FILE, 'utf8').then(JSON.parse, () => ({}));
  const server = String(a.server && a.server !== true ? a.server : process.env.OTA_SERVER || cfg.server || '').replace(/\/+$/, '');
  const token = String(a.token && a.token !== true ? a.token : process.env.OTA_TOKEN || cfg.token || '');
  if (!server || !token) fail('还没有登录：运行 ota login --server <地址> --token <令牌>，或设置环境变量 OTA_SERVER、OTA_TOKEN');
  return makeClient(server, token);
}

async function installer(a) {
  const sub = a._[1] || 'list';
  const app = need(a.app, '--app');
  const c = await client(a);
  const title = (x) => `${x.platform === 'ios' ? 'iOS' : 'Android'} ${x.version || ''}${x.buildNumber ? `（${x.buildNumber}）` : ''}`.trim();
  const mark = { ok: '✓', warn: '!', error: '✗' };
  if (sub === 'list') {
    const list = await c.call('GET', `/apps/${app}/installers`);
    if (a.json) return out(list);
    if (!list.length) return log('还没有上传安装包');
    table(['安装包', '文件', 'Runtime', '渠道', '检查'], list.map((x) => [title(x), x.fileName, x.runtimeVersion || '—', x.channel || 'production',
      x.checks.filter((ch) => ch.state !== 'ok').map((ch) => `${mark[ch.state] || '-'}${ch.label}`).join(' ') || '全部通过']));
    return;
  }
  if (sub !== 'upload') fail('用法：ota installer upload --app <标识> <文件>，或 ota installer list --app <标识>');
  const path = resolve(String(need(a._[2], '安装包文件路径')));
  const name = path.split(/[\\/]/).pop();
  if (!/\.(apk|ipa)$/i.test(name)) fail('只支持 .apk 和 .ipa 文件');
  const buf = await readFile(path).catch(() => fail(`读不到 ${path}`));
  // 分片上传：反向代理限制单个请求大小（宝塔 Nginx 默认 50MB），返回 413 时分片减半
  const started = await c.call('POST', `/apps/${app}/installers/uploads`, { fileName: name, size: buf.length });
  let chunk = started.chunkSize || 8 * 1024 * 1024;
  let sent = 0;
  while (sent < buf.length) {
    try {
      const r = await c.call('PUT', `/apps/${app}/installers/uploads/${started.id}?offset=${sent}`, null, buf.subarray(sent, Math.min(buf.length, sent + chunk)));
      sent = r.received;
    } catch (e) {
      // 宝塔 WAF 的「Nginx缓冲区溢出」和 413 一样是嫌单片太大
      if ((/HTTP 413/.test(e.message) || /Nginx缓冲区溢出/.test(e.message)) && chunk > 512 * 1024) {
        chunk = Math.floor(chunk / 2);
        continue;
      }
      throw e;
    }
    progress(`上传 ${size(sent)} / ${size(buf.length)}`);
  }
  progressEnd();
  const note = a.note && a.note !== true ? String(a.note) : undefined;
  const r = await c.call('POST', `/apps/${app}/installers/uploads/${started.id}/complete`, { note });
  if (a.json) out(r);
  else {
    log(`已上传 ${title(r)}，Runtime ${r.runtimeVersion || '未知'}，${r.bundleId || ''}`);
    for (const ch of r.checks) log(`  ${mark[ch.state] || '-'} ${ch.label}：${ch.text}`);
  }
  if (r.level === 'error') process.exitCode = 2;
}

function makeClient(server, token) {
  return {
    async call(method, path, body, raw, text = false) {
      const headers = { Authorization: `Bearer ${token}` };
      let payload;
      if (raw) {
        headers['Content-Type'] = 'application/octet-stream';
        payload = raw;
      } else if (body !== undefined && body !== null) {
        headers['Content-Type'] = 'application/json';
        payload = JSON.stringify(body);
      }
      let res;
      for (let attempt = 0; ; attempt++) {
        try {
          res = await fetch(`${server}/api/v1${path}`, { method, headers, body: payload });
          break;
        } catch (e) {
          if (attempt >= 2) throw new Error(`连不上 ${server}：${e.cause?.code || e.message}`);
          await new Promise((r) => setTimeout(r, 1000 * (attempt + 1)));
        }
      }
      const content = await res.text();
      if (!res.ok || (content && content.trimStart().startsWith('<') && !text)) {
        let msg = `HTTP ${res.status}`;
        try {
          const j = JSON.parse(content);
          msg = `${j.message || msg}${j.error ? `（${j.error}）` : ''}`;
        } catch {
          if (/Nginx缓冲区溢出/.test(content)) msg = 'Nginx缓冲区溢出';
          else if (content.trimStart().startsWith('<')) {
            msg = `请求 ${path.split('?')[0]} 被反向代理或网站防火墙拦截（HTTP ${res.status}），把它加进防火墙的 URL 白名单后重试`;
          }
        }
        throw new Error(msg);
      }
      if (text) return content;
      return content ? JSON.parse(content) : {};
    },
  };
}

/** 版本 ID 可以只写前几位 */
async function resolveRelease(c, app, prefix) {
  const p = String(prefix).toLowerCase();
  if (p.length === 36) return p;
  const list = await c.call('GET', `/apps/${app}/releases?limit=200`);
  const hits = list.filter((r) => r.id.startsWith(p));
  if (!hits.length) fail(`找不到以 ${p} 开头的版本`);
  if (hits.length > 1) fail(`${p} 对应多个版本，多写几位`);
  return hits[0].id;
}

// ==================== 导出内容 ====================

/** 新版 Expo 导出时不再带 expoConfig.json，从项目里读公开配置（和 EAS 一样），读不到返回 null */
async function projectConfig(project) {
  const pkg = await readFile(join(project, 'package.json'), 'utf8').then(JSON.parse, () => null);
  if (!pkg || !({ ...pkg.dependencies, ...pkg.devDependencies }).expo) return null;
  log('导出目录里没有 expoConfig.json，读取项目配置：npx expo config --type public --json');
  try {
    return JSON.parse(await capture('npx', ['expo', 'config', '--type', 'public', '--json'], project));
  } catch (e) {
    log(`读取项目配置失败（${e.message}），清单里不带 expoConfig`);
    return null;
  }
}

/** expoConfig 里的 Runtime：字符串直接用；appVersion 策略取 version；sdkVersion 策略取 exposdk:版本 */
function runtimeOf(cfg) {
  if (!cfg) return null;
  const rv = cfg.runtimeVersion;
  if (typeof rv === 'string' && rv) return rv;
  if (rv && typeof rv === 'object') {
    if (rv.policy === 'appVersion' && cfg.version) return cfg.version;
    if (rv.policy === 'sdkVersion' && cfg.sdkVersion) return `exposdk:${cfg.sdkVersion}`;
    return null;
  }
  return typeof cfg.version === 'string' ? cfg.version : null;
}

function platformEntries(metadata) {
  const fm = metadata.fileMetadata || {};
  if (Array.isArray(fm)) return fm.map((x) => [x.platform, x]);
  return Object.entries(fm);
}

function filterPlatforms(metadata, platform) {
  if (platform === 'all') return metadata;
  const fm = metadata.fileMetadata || {};
  const kept = Array.isArray(fm) ? fm.filter((x) => x.platform === platform) : { [platform]: fm[platform] };
  if (Array.isArray(kept) ? !kept.length : !kept[platform]) fail(`导出内容里没有 ${platform}`);
  return { ...metadata, fileMetadata: kept };
}

function listFiles(metadata) {
  const out = new Set();
  for (const [p, entry] of platformEntries(metadata)) {
    if (!['ios', 'android'].includes(p) || !entry?.bundle) continue;
    out.add(entry.bundle);
    for (const asset of entry.assets || []) if (asset.path) out.add(asset.path);
  }
  return [...out];
}

// ==================== 工具 ====================

function parseArgs(argv) {
  const res = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (!a.startsWith('--')) {
      res._.push(a);
      continue;
    }
    const eq = a.indexOf('=');
    const key = eq > 0 ? a.slice(2, eq) : a.slice(2);
    if (eq > 0) res[key] = a.slice(eq + 1);
    else if (key.startsWith('no-')) res[key.slice(3)] = false;
    else if (i + 1 < argv.length && !argv[i + 1].startsWith('--')) res[key] = argv[++i];
    else res[key] = true;
  }
  return res;
}

function run(cmd, argv, cwd) {
  return new Promise((ok, bad) => {
    const p = spawn(cmd, argv, { cwd, stdio: 'inherit', shell: process.platform === 'win32' });
    p.on('error', (e) => bad(new Error(`运行 ${cmd} 失败：${e.message}`)));
    p.on('exit', (code) => (code === 0 ? ok() : bad(new Error(`${cmd} ${argv.join(' ')} 退出码 ${code}`))));
  });
}

function capture(cmd, argv, cwd) {
  return new Promise((ok, bad) => {
    const p = spawn(cmd, argv, { cwd, stdio: ['ignore', 'pipe', 'pipe'], shell: process.platform === 'win32' });
    let stdout = '';
    let stderr = '';
    p.stdout.on('data', (d) => (stdout += d));
    p.stderr.on('data', (d) => (stderr += d));
    p.on('error', (e) => bad(new Error(e.message)));
    p.on('exit', (code) => (code === 0 ? ok(stdout) : bad(new Error(stderr.trim().split('\n').pop() || `退出码 ${code}`))));
  });
}

async function pool(items, n, fn) {
  let next = 0;
  const workers = Array.from({ length: Math.min(n, items.length) }, async () => {
    while (next < items.length) await fn(items[next++]);
  });
  await Promise.all(workers);
}

function need(v, name) {
  if (v === undefined || v === true || v === '') fail(`缺少 ${name}`);
  return v;
}

function size(n) {
  if (n < 1024) return `${n} B`;
  if (n < 1048576) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1048576).toFixed(1)} MB`;
}

function fmtTime(iso) {
  const d = new Date(iso);
  const pad = (x) => String(x).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function table(head, rows) {
  const width = (s) => [...String(s)].reduce((w, ch) => w + (ch.charCodeAt(0) > 0x2e80 ? 2 : 1), 0);
  const cols = head.map((h, i) => Math.max(width(h), ...rows.map((r) => width(r[i]))));
  const line = (r) => r.map((c, i) => String(c) + ' '.repeat(cols[i] - width(c))).join('  ').trimEnd();
  console.log(line(head));
  rows.forEach((r) => console.log(line(r)));
}

function progress(text) {
  if (process.stderr.isTTY) process.stderr.write(`\r${text}`);
}

function progressEnd() {
  if (process.stderr.isTTY) process.stderr.write('\n');
}

function log(text) {
  console.log(text);
}

function out(obj) {
  console.log(JSON.stringify(obj, null, 2));
}

function fail(message) {
  console.error(`错误：${message}`);
  process.exit(1);
}
