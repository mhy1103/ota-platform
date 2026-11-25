// 中英文切换：选了 English 时，页面上的中文在显示前换成英文（MutationObserver 在浏览器绘制前回调，不会闪一下中文）。
// 查法：整句词典 → 带数字的常见句式 → 词组替换（词组能覆盖七成以上的中文才换，名字、备注这类用户数据保持原样）。
// 中文用户不加载词典。

var KEY = 'ota.lang';
var CJK = /[\u4e00-\u9fff]/;
var CJK_ALL = /[\u4e00-\u9fff]/g;
var ATTRS = ['placeholder', 'title', 'aria-label', 'data-tip', 'data-confirm', 'data-confirm-body', 'data-confirm-ok', 'alt', 'data-copy-label', 'data-k'];
var PUNCT = { '，': ', ', '。': '. ', '：': ': ', '；': '; ', '（': ' (', '）': ') ', '、': ', ', '「': ' "', '」': '" ', '！': '! ', '？': '? ' };

// 操作日志详情里反复出现的片段
var ON = { '启用': 'Enabled', '停用': 'Disabled', '开启': 'turned on', '关闭': 'turned off' };
var BITS = {
    '超级管理员': 'super admin', '还没有分配 App': 'no App assigned', '可以管理口令': 'can manage codes', '可以管理代理': 'can manage agents',
    '已停用': 'disabled', '可看记录': 'can view records', '仅看数量': 'counts only', '实时': 'live data', '手动更新': 'manual updates',
    '每日 0 点': 'updated daily at 0:00', '重置密码': 'password reset', '不扣量': 'no deduction', '需要审核': 'needs review',
    '自动通过': 'auto-approved', '每天不限': 'no daily limit', '不能申请': 'not allowed'
};
var LOOK = { '侧栏': 'sidebar', '版面': 'layout', '卡片': 'cards', '背景': 'background', '按钮': 'buttons', '表格': 'tables' };
var NOTICE = { '发布': 'Published', '修改': 'Edited', '置顶': 'Pinned', '取消置顶': 'Unpinned', '删除': 'Deleted' };
var MONTH = { 1: 'Jan', 2: 'Feb', 3: 'Mar', 4: 'Apr', 5: 'May', 6: 'Jun', 7: 'Jul', 8: 'Aug', 9: 'Sep', 10: 'Oct', 11: 'Nov', 12: 'Dec' };
var WEEK = { '周一': 'Mon', '周二': 'Tue', '周三': 'Wed', '周四': 'Thu', '周五': 'Fri', '周六': 'Sat', '周日': 'Sun' };
var LOGS = { '后台操作日志': 'admin activity', '设备事件日志': 'device events', '全部日志': 'all logs' };
var SIGN = { '私钥和证书': 'private key and certificate', '私钥': 'private key', '证书': 'certificate' };

function bit(s) {
    return BITS[s] || t(s);
}
function limitOf(s) {
    return s === '不限' ? 'unlimited' : s;
}
function agentOf(s) {
    return s === '未分配' ? 'unassigned' : s;
}
function usesOf(s) {
    if (s === '不限次数') return 'unlimited uses';
    var n = /(\d+)/.exec(s);
    return n ? n[1] + (n[1] === '1' ? ' use' : ' uses') : t(s);
}
function plural(n, one) {
    return n + ' ' + one + (String(n) === '1' ? '' : 's');
}
function lower(s) {
    return s.charAt(0).toLowerCase() + s.slice(1);
}
// 「3 个口令 [A1、A2、A3 等]」
function codeList(n, list) {
    return plural(n, 'code') + ' [' + list.replace(/ 等$/, ', …').split('、').join(', ') + ']';
}
// 「张三、李四 等 7 个」
function names(s) {
    var more = / 等 (\d+) 个$/.exec(s);
    return s.replace(/ 等 \d+ 个$/, '').split('、').join(', ') + (more ? ' … (' + more[1] + ' in total)' : '');
}
function plan(p, by) {
    return p ? ' (scheduled' + (by ? ' by ' + by.replace(/^令牌 /, 'token ') : '') + ')' : '';
}
function grants(s) {
    return s.split('，').map(function (p) {
        var g = /^([\w-]+)（(.+)）$/.exec(p);
        return g ? g[1] + ' (' + g[2].split('、').map(t).join(', ') + ')' : bit(p);
    }).join(', ');
}
function requestRule(s) {
    return s.split('，').map(bit).join(', ');
}
function agentRule(s) {
    return s.split(' · ').map(function (p) {
        var g = /^扣量：(.+)$/.exec(p);
        if (g) return 'deduction: ' + g[1].split('；').map(function (x) {
            return x.replace(/^(\d+)(?:–(\d+)|\+)人 扣(\d+)%$/, function (m, a, b, r) { return a + (b ? '–' + b : '+') + ' people −' + r + '%'; });
        }).join('; ');
        g = /^申请口令：(.+)$/.exec(p);
        if (g) return 'code requests: ' + requestRule(g[1]);
        g = /^可开二级代理(?:（最多 (\d+) 个）)?$/.exec(p);
        if (g) return 'sub-agents allowed' + (g[1] ? ' (up to ' + g[1] + ')' : '');
        return bit(p);
    }).join(' · ');
}
function agentGone(s) {
    var subs = /，(\d+) 个二级代理一起删除$/.exec(s);
    if (subs) return agentGone(s.slice(0, subs.index)) + ', ' + plural(subs[1], 'sub-agent') + ' deleted too';
    if (s === '名下没有口令') return 'no codes owned';
    var g = /^名下 (\d+) 个口令和 (\d+) 条激活记录一起删除$/.exec(s);
    if (g) return plural(g[1], 'code') + ' and ' + plural(g[2], 'activation record') + ' deleted with it';
    g = /^(\d+) 个口令改为未分配$/.exec(s);
    return g ? plural(g[1], 'code') + ' unassigned' : t(s);
}
function looks(s) {
    return s.split(' · ').map(function (p) {
        if (p === '分隔线表格') return 'Lined tables';
        var g = /^(.+?)(侧栏|版面|卡片|背景|按钮|表格)$/.exec(p);
        return g ? t(g[1]) + ' ' + LOOK[g[2]] : t(p);
    }).join(' · ');
}

var PATTERNS = [
    // 操作日志
    [/^(.+?) ?· 令牌 (.+)$/, function (m, a, b) { return t(a) + ' · token ' + b; }],
    [/^令牌：(.+)$/, 'Token: $1'],
    [/^创建口令 \[(.+?)\] 上限:(\S+) 代理:(.+?)(?: 渠道:(\S+))?(?: 备注:(.+))?$/, function (m, c, l, a, ch, r) {
        return 'Created code [' + c + '] · limit ' + limitOf(l) + ' · agent ' + agentOf(a) + (ch ? ' · channel ' + ch : '') + (r ? ' · note ' + r : '');
    }],
    [/^修改口令 \[(.+?)\] 上限:(\S+) 代理:(.+?)(?: 渠道:(\S+))?( 停用)?$/, function (m, c, l, a, ch, off) {
        return 'Edited code [' + c + '] · limit ' + limitOf(l) + ' · agent ' + agentOf(a) + (ch ? ' · channel ' + ch : '') + (off ? ' · disabled' : '');
    }],
    [/^批量生成 (\d+) 个口令（(?:前缀 (\S+)，)?(\d+) 位）上限:(\S+) 代理:(.+)$/, function (m, n, p, len, l, a) {
        return 'Generated ' + plural(n, 'code') + ' (' + (p ? 'prefix ' + p + ', ' : '') + len + ' characters) · limit ' + limitOf(l) + ' · agent ' + agentOf(a);
    }],
    [/^代理申请 #(\d+) 生成 (\d+) 个口令 上限:(\S+) 代理:(.+)$/, function (m, id, n, l, a) {
        return 'Generated ' + plural(n, 'code') + ' for request #' + id + ' · limit ' + limitOf(l) + ' · agent ' + agentOf(a);
    }],
    [/^导入 (\d+) 个口令(?:（(\d+) 个已存在，跳过）)? 上限:(\S+) 代理:(.+)$/, function (m, n, skip, l, a) {
        return 'Imported ' + plural(n, 'code') + (skip ? ' (' + skip + ' already existed, skipped)' : '') + ' · limit ' + limitOf(l) + ' · agent ' + agentOf(a);
    }],
    [/^口令 \[(.+)\] (启用|停用)$/, function (m, c, s) { return ON[s] + ' code [' + c + ']'; }],
    [/^删除口令 \[(.+)\]$/, 'Deleted code [$1]'],
    [/^口令\[(.+?)\] 设备:(\S+) \(待确认\) · ([\w-]+)$/, 'Code [$1] · device $2 (pending) · $3'],
    [/^口令\[(.+?)\] 设备:(\S+) 已用口令\[(.+?)\]激活过，不再记录 · ([\w-]+)$/, 'Code [$1] · device $2 was already activated with code [$3], not recorded again · $4'],
    [/^设备:(\S+) 口令ID:(\d+) · ([\w-]+)$/, 'Device $1 · code ID $2 · $3'],
    [/^批量(启用|停用) (\d+) 个口令 \[(.*)\]$/, function (m, s, n, l) { return ON[s] + ' ' + codeList(n, l); }],
    [/^批量分配给 (.+?)：(\d+) 个口令 \[(.*)\]$/, function (m, a, n, l) {
        return a === '未分配' ? 'Unassigned ' + codeList(n, l) : 'Assigned ' + codeList(n, l) + ' to ' + a;
    }],
    [/^批量删除 (\d+) 个口令 \[(.*)\]$/, function (m, n, l) { return 'Deleted ' + codeList(n, l); }],
    [/^批量改有效期为 (.+?)：(\d+) 个口令 \[(.*)\]$/, function (m, d, n, l) { return 'Set ' + codeList(n, l) + (d === '一直有效' ? ' to never expire' : ' to expire on ' + d); }],
    [/^批量改可激活次数为 (.+?)：(\d+) 个口令 \[(.*)\]$/, function (m, u, n, l) {
        return 'Set ' + codeList(n, l) + ' to ' + (u === '不限' ? 'unlimited uses' : usesOf(u));
    }],
    [/^批量改备注为「(.*)」：(\d+) 个口令 \[(.*)\]$/, function (m, r, n, l) { return 'Set the note of ' + codeList(n, l) + ' to "' + r + '"'; }],
    [/^代理 (.+?) 申请(?:自定义口令 (\d+) 个(?:（(\d+) 个重复，自动拒绝）)?|(\d+) 个口令)（每个(.+?)）(?:，用途：(.*?))? · 申请 #(\d+)$/, function (m, a, cn, dup, rn, u, why, id) {
        return 'Agent ' + a + ' requested ' + (cn ? plural(cn, 'custom code') + (dup ? ' (' + plural(dup, 'duplicate') + ' rejected automatically)' : '') : plural(rn, 'code'))
            + ' (' + usesOf(u) + ' each)' + (why ? ', purpose: ' + why : '') + ' · request #' + id;
    }],
    [/^代理 (.+) 撤回申请 #(\d+)$/, 'Agent $1 withdrew request #$2'],
    [/^代理 (.+) 的申请 #(\d+) 没有可以生成的口令（都已存在或没选）$/, 'Request #$2 from agent $1 had no codes to generate (all existed or none were picked)'],
    [/^(自动通过|同意)代理 (.+?) 的申请 #(\d+)，生成 (\d+) 个(自定义)?口令（每个(.+?)(?:，(\d{4}-\d\d-\d\d) 到期)?）(?:，(\d+) 个没同意或已存在)?$/, function (m, how, a, id, n, custom, u, exp, left) {
        return (how === '同意' ? 'Approved' : 'Auto-approved') + ' request #' + id + ' from agent ' + a + ', generated ' + plural(n, custom ? 'custom code' : 'code')
            + ' (' + usesOf(u) + ' each' + (exp ? ', expire ' + exp : '') + ')' + (left ? ', ' + left + ' not approved or already existed' : '');
    }],
    [/^拒绝代理 (.+?) 的申请 #(\d+)（(\d+) 个口令）(?:，原因：(.*))?$/, function (m, a, id, n, why) {
        return 'Rejected request #' + id + ' from agent ' + a + ' (' + plural(n, 'code') + ')' + (why ? ', reason: ' + why : '');
    }],
    [/^新建 App (.+)（([\w-]+)）(?:，(自动生成签名密钥|导入现有签名密钥|复用 (.+) 的签名密钥))?(?:，下载链接 (\S+))?$/, function (m, name, slug, how, from, url) {
        return 'Created App ' + name + ' (' + slug + ')' + (how ? ', ' + (from ? 'reusing the signing key of ' + from
            : how === '自动生成签名密钥' ? 'signing key generated' : 'existing signing key imported') : '') + (url ? ', download link ' + url : '');
    }],
    [/^删除 App (.+)（([\w-]+)）：版本 (\d+) 个、激活记录 (\d+) 条(?:、发布令牌 (\d+) 个)?$/, function (m, name, slug, r, a, tk) {
        return 'Deleted App ' + name + ' (' + slug + '): ' + plural(r, 'release') + ', ' + plural(a, 'activation record') + (tk ? ', ' + plural(tk, 'publish token') : '');
    }],
    [/^([\w-]+) (?:名称改为|改名为) (.+?)(?:，(清空下载链接|下载链接改为 \S+))?$/, function (m, s, name, dl) {
        return s + ' renamed to ' + name + (dl ? ', ' + (dl === '清空下载链接' ? 'download link cleared' : 'download link set to ' + dl.slice(7)) : '');
    }],
    [/^([\w-]+) 清空下载链接$/, '$1 cleared the download link'], [/^([\w-]+) 下载链接改为 (\S+)$/, '$1 set the download link to $2'],
    [/^([\w-]+) 换了图标$/, '$1 changed the icon'], [/^([\w-]+) 去掉了图标$/, '$1 removed the icon'],
    [/^([\w-]+) (开启|关闭)增量更新$/, function (m, s, on) { return s + ' ' + ON[on] + ' incremental updates'; }],
    [/^([\w-]+) 更换口令接口密钥$/, '$1 rotated the codes API secret'],
    [/^([\w-]+) 新建渠道 (\S+)$/, '$1 created channel $2'],
    [/^([\w-]+) 删除渠道 (\S+)(?:，移除 (\d+) 台测试设备)?$/, function (m, s, c, n) { return s + ' deleted channel ' + c + (n ? ', removed ' + plural(n, 'test device') : ''); }],
    [/^新建管理令牌 (.+)$/, 'Created admin token $1'], [/^([\w-]+) 新建发布令牌 (.+)$/, '$1 created publish token $2'],
    [/^撤销令牌 (.+)（(admin|publish)）$/, 'Revoked $2 token $1'],
    [/^([\w-]+) 关闭 Webhook$/, '$1 turned off the webhook'], [/^([\w-]+) Webhook 改为 (\S+)$/, '$1 set the webhook to $2'],
    [/^([\w-]+) 更换 Webhook 签名密钥$/, '$1 rotated the webhook signing secret'],
    [/^([\w-]+) 测试设备 (.+) → 渠道 ([^\s，]+)(，灰度白名单)?$/, function (m, s, d, c, w) { return s + ' test device ' + tidy(d) + ' → channel ' + c + (w ? ', rollout whitelist' : ''); }],
    [/^([\w-]+) 移除测试设备 (.+)$/, function (m, s, d) { return s + ' removed test device ' + tidy(d); }],
    [/^已设为测试设备：(.+) 检查更新时用渠道 ([^\s，]+)(，灰度没到 100% 时也先拿新版本)?，立即生效$/, function (m, d, c, w) {
        return 'Set as test device: ' + tidy(d) + ' now checks updates on channel ' + c + (w ? ' and gets new releases first during rollouts' : '') + ', effective immediately';
    }],
    [/^([\w-]+) 开启了自动回滚：启动失败率 ≥(\d+)% 且 ≥(\d+) 台 时回滚到上一版本$/, '$1 turned on auto rollback: roll back to the previous release at ≥$2% launch failures and ≥$3 devices'],
    [/^([\w-]+) 关闭了自动回滚$/, '$1 turned off auto rollback'],
    [/^([\w-]+) · (\S+) 的 (\w+) 启动失败 (\d+) 台（(\d+)%），达到设定的 (\d+)% 且 (\d+) 台，(.+)$/, function (m, s, c, v, n, p, r, k, rest) {
        var head = s + ' · ' + c + ': ' + v + ' failed to launch on ' + n + ' devices (' + p + '%), reaching the set ' + r + '% and ' + k + ' devices; ';
        var to = rest.match(/^自动回滚到 (\w+)（新版本 (\w+)，Runtime (\S+)）$/);
        if (to) return head + 'rolled back to ' + to[1] + ' (new release ' + to[2] + ', Runtime ' + to[3] + ')';
        if (rest === '但没有上一版本，没有自动回滚') return head + 'no previous release, so no auto rollback';
        var prev = rest.match(/^但上一版本 (\w+) 是之前自动回滚掉的，没有回滚过去$/);
        if (prev) return head + 'the previous release ' + prev[1] + ' was auto-rolled back before, so it was not restored';
        return head + t(rest);
    }],
    [/^自动回滚 · ≥(\d+)%$/, 'Auto rollback · ≥$1%'],
    [/^线上版本启动失败率 ≥(\d+)% 且 ≥(\d+) 台时，自动回滚到上一版本。点这里修改$/, 'Rolls back to the previous release when the live release fails to launch on ≥$1% and ≥$2 devices. Click to change'],
    [/^已开启自动回滚：线上版本启动失败率 ≥(\d+)% 且 ≥(\d+) 台时，自动回滚到上一版本$/, 'Auto rollback is on: when the live release fails to launch on ≥$1% and ≥$2 devices, it rolls back to the previous release'],
    [/^占 (\d+)%(.*)$/, function (m, p, rest) { return p + '%' + rest.replace(/ · 最近一次 (.+)$/, function (x, a) { return ' · last ' + t(a); }); }],
    [/^共 (\d+) 类，列出最多的 (\d+) 类。(.*)$/, function (m, a, b, rest) { return a + ' kinds, showing the top ' + b + '. ' + t(rest); }],
    [/^((?:今天|昨天|前天) \d\d?:\d\d|[\d\- :]+) 生成$/, function (m, a) { return 'created ' + t(a); }],
    [/^([\w-]+) 关闭群机器人$/, '$1 turned off the group bot'],
    [/^([\w-]+) 群机器人改为 (\S+)$/, function (m, s, k) { return s + ' set the group bot to ' + t(k); }],
    [/^([\w-]+) 群机器人不推任何消息$/, '$1 set the group bot to send nothing'],
    [/^([\w-]+) 群机器人只推：(.+)$/, function (m, s, l) { return s + ' set the group bot to only send: ' + l.split('、').map(t).join(', '); }],
    [/^([\w-]+) 下载备份（签名密钥、口令接口密钥、Webhook、渠道、(\d+) 个口令）$/, '$1 downloaded a backup (signing key, codes API secret, webhook, channels, $2 codes)'],
    [/^(从备份新建|用备份恢复) ([\w-]+)：签名密钥、口令接口密钥、Webhook 已恢复，渠道新增 (\d+) 个，口令新增 (\d+) 个(?:，(\d+) 个口令原来就有、保持不变)?$/, function (m, how, s, ch, c, kept) {
        return (how === '从备份新建' ? 'Created ' : 'Restored ') + s + ' from a backup: signing key, codes API secret and webhook restored, '
            + plural(ch, 'channel') + ' and ' + plural(c, 'code') + ' added' + (kept ? ', ' + kept + ' codes already existed and were kept' : '');
    }],
    [/^([\w-]+) (切换到|设为) ([AB])面（(?:上线|审核)）(（(?:(.+) 设置的)?计划任务）)?$/, function (m, s, how, f, p, by) {
        return s + ' switched to Side ' + f + (f === 'B' ? ' (live)' : ' (review)') + plan(p, by);
    }],
    [/^([\w-]+) 计划在 (\S+ \S+) (?:把 )?(.+)$/, function (m, s, at, what) { return s + ' scheduled for ' + at + ': ' + t(what); }],
    [/^取消计划：(\S+ \S+) (.+)$/, function (m, at, what) { return 'Cancelled the plan for ' + at + ': ' + t(what); }],
    [/^(\S+) 灰度调整到 (\d+)%$/, '$1 rollout to $2%'],
    [/^([\w-]+) (登记|上传)版本 (\w+)（Runtime ([^，]+)，([^，）]+)(?:，([^，）]+))?(，强制更新)?）$/, function (m, s, how, id, rt, plat, size, force) {
        return s + (how === '登记' ? ' registered' : ' uploaded') + ' release ' + id + ' (Runtime ' + rt + ', ' + plat + (size ? ', ' + size : '') + (force ? ', force update' : '') + ')';
    }],
    [/^([\w-]+) 修改版本 (\w+)(（强制更新）)?$/, function (m, s, id, f) { return s + ' edited release ' + id + (f ? ' (force update)' : ''); }],
    [/^([\w-]+) 修改版本 (\w+)：说明「(.*)」(，强制更新)?$/, function (m, s, id, msg, f) { return s + ' edited release ' + id + ': note "' + msg + '"' + (f ? ', force update' : ''); }],
    [/^([\w-]+) 删除版本 (\w+)(?:（Runtime (.+)）)?$/, function (m, s, id, rt) { return s + ' deleted release ' + id + (rt ? ' (Runtime ' + rt + ')' : ''); }],
    [/^([\w-]+) · (\S+) 发布 (\w+)（Runtime ([^，]+)，灰度 (\d+)%）( · 重新发布|，比线上版本旧，复制成新版本发布)?$/, function (m, s, ch, id, rt, p, re) {
        return s + ' · ' + ch + ' published ' + id + ' (Runtime ' + rt + ', rollout ' + p + '%)'
            + (re ? (re.charAt(0) === ' ' ? ' · republished' : ', older than live so it was copied as a new release') : '');
    }],
    [/^([\w-]+) · (\S+) Runtime (\S+) 灰度调整为 (\d+)%(（(?:(.+) 设置的)?计划任务）)?$/, function (m, s, ch, rt, pc, p, by) {
        return s + ' · ' + ch + ' Runtime ' + rt + ' rollout set to ' + pc + '%' + plan(p, by);
    }],
    [/^([\w-]+) · (\S+) 回滚到 (\w+)（(?:新版本 (\w+)，)?Runtime (.+)）$/, function (m, s, ch, from, nid, rt) {
        return s + ' · ' + ch + ' rolled back to ' + from + ' (' + (nid ? 'new release ' + nid + ', ' : '') + 'Runtime ' + rt + ')';
    }],
    [/^([\w-]+) · (\S+) 回退到内置版本（Runtime (.+)）$/, '$1 · $2 fell back to the embedded version (Runtime $3)'],
    [/^([\w-]+) · (\S+) 设口令更新包 (\w+)（Runtime (.+)）$/, '$1 · $2 set activation package $3 (Runtime $4)'],
    [/^([\w-]+) · (\S+) 取消口令更新包（Runtime (.+)）$/, '$1 · $2 cleared the activation package (Runtime $3)'],
    [/^([\w-]+) 校验 (\w+)：(.+)$/, function (m, s, id, h) { return s + ' verified ' + id + ': ' + t(h); }],
    [/^([\w-]+) 添加外部链接版本 (\w+) → (\S+)（(\d+) 个文件，(.+)）$/, '$1 added external release $2 → $3 ($4 files, $5)'],
    [/^([\w-]+) 上传安装包 (.+?)（(iOS|Android)(.*)，运行版本 ([^，）]+)）$/, function (m, s, f, plat, ver, rt) {
        return s + ' uploaded installer ' + f + ' (' + plat + (ver.trim() ? ' ' + tidy(ver) : '') + ', runtime ' + (rt === '未知' ? 'unknown' : rt) + ')';
    }],
    [/^([\w-]+) 删除安装包 (.+)$/, '$1 deleted installer $2'],
    [/^([\w-]+) 导出 (\d+) 台设备$/, '$1 exported $2 devices'],
    [/^清理存储：文件 (\d+) 个（(?:(\d+) 字节|([^）]+))）、补丁记录 (\d+) 条$/, function (m, f, b, h, p) {
        return 'Storage cleanup: ' + plural(f, 'file') + ' (' + (b ? b + ' bytes' : h) + '), ' + plural(p, 'patch record');
    }],
    [/^切换文件存储到(?:本机存储|对象存储 (.+))：复制 (\d+) 个文件（(.+?)）(?:，原存储里本来就缺 (\d+) 个)?$/, function (m, bucket, n, size, miss) {
        return 'Switched file storage to ' + (bucket ? 'object storage ' + bucket : 'local storage') + ': copied ' + plural(n, 'file') + ' (' + size + ')'
            + (miss ? ', ' + miss + ' were already missing from the old storage' : '');
    }],
    [/^账号 (.+) 密码错误次数过多，暂停登录 (\d+) 分钟$/, 'Account $1: too many wrong passwords, sign-in paused for $2 min'],
    [/^代理 (.+)（([^（）]+)）登录$/, 'Agent $1 ($2) signed in'],
    [/^管理员 (\S+) 登录(（两步验证）)?$/, function (m, u, f) { return 'Admin ' + u + ' signed in' + (f ? ' with two-step verification' : ''); }],
    [/^管理员 (\S+) 登录设备已满，登录时退出了 (\d+) 台设备$/, 'Admin $1 reached the device limit and signed out $2 devices to sign in'],
    [/^管理员 (\S+) 两步验证连续失败 (\d+) 次，需要重新登录$/, 'Admin $1 failed two-step verification $2 times in a row and must sign in again'],
    [/^管理员 (\S+) 用恢复码登录，还剩 (\d+) 个$/, 'Admin $1 signed in with a recovery code, $2 left'],
    [/^(\S+) 修改自己的账号(和密码)?$/, function (m, u, pw) { return u + ' updated their account' + (pw ? ' and password' : ''); }],
    [/^(\S+) (开启|关闭)两步验证$/, function (m, u, on) { return u + ' ' + ON[on] + ' two-step verification'; }],
    [/^(\S+) 重新生成恢复码$/, '$1 regenerated recovery codes'],
    [/^(\S+) 退出了一台登录设备$/, '$1 signed out a device'], [/^(\S+) 退出了其他 (\d+) 台登录设备$/, '$1 signed out $2 other devices'],
    [/^(\S+) 重新打开安装引导$/, '$1 reopened the setup guide'],
    [/^新建(超级管理员|成员) (.+?)（([^（）]+)）(?:：(.+))?$/, function (m, role, n, u, d) {
        return 'Created ' + (role === '成员' ? 'member ' : 'super admin ') + n + ' (' + u + ')' + (d ? ': ' + grants(d) : '');
    }],
    [/^修改管理员 (.+?)（([^（）]+)）：(.+)$/, function (m, n, u, d) { return 'Edited admin ' + n + ' (' + u + '): ' + grants(d); }],
    [/^(启用|停用)管理员 (.+)（([^（）]+)）$/, function (m, s, n, u) { return ON[s] + ' admin ' + n + ' (' + u + ')'; }],
    [/^让 (.+)（([^（）]+)）在所有设备上退出登录$/, 'Signed $1 ($2) out on all devices'],
    [/^删除管理员 (.+)（([^（）]+)）$/, 'Deleted admin $1 ($2)'],
    [/^重置 (.+)（([^（）]+)）的两步验证$/, 'Reset two-step verification for $1 ($2)'],
    [/^(新建|修改)代理 (.+?)（([^（）]+)） · (.+)$/, function (m, v, n, u, d) { return (v === '新建' ? 'Created' : 'Edited') + ' agent ' + n + ' (' + u + ') · ' + agentRule(d); }],
    [/^代理 (.+) (启用|停用)$/, function (m, n, s) { return ON[s] + ' agent ' + n; }],
    [/^更新代理 (.+) 的数据$/, 'Updated the data of agent $1'], [/^更新全部手动代理的数据（(\d+) 个）$/, 'Updated the data of all manual agents ($1)'],
    [/^删除代理 (.+?)（([^（）]+)），(.+)$/, function (m, n, u, d) { return 'Deleted agent ' + n + ' (' + u + '), ' + agentGone(d); }],
    // 二级代理：代理后台的提示、后台操作日志
    [/^代理 (.+?) (新建|修改)二级代理 (.+?)（([^（）]+)） · (.+)$/, function (m, p, v, n, u, d) {
        return 'Agent ' + p + (v === '新建' ? ' created' : ' edited') + ' sub-agent ' + n + ' (' + u + ') · ' + agentRule(d);
    }],
    [/^代理 (.+?) (启用|停用)二级代理 (.+)$/, function (m, p, s, n) { return 'Agent ' + p + (s === '启用' ? ' enabled' : ' disabled') + ' sub-agent ' + n; }],
    [/^代理 (.+?) 更新二级代理 (.+) 的数据，截至 (.+)$/, 'Agent $1 updated the data of sub-agent $2, as of $3'],
    [/^代理 (.+?) 更新全部 (\d+) 个手动二级代理的数据，截至 (.+)$/, 'Agent $1 updated the data of all $2 manual sub-agents, as of $3'],
    [/^代理 (.+?) 删除二级代理 (.+?)（([^（）]+)）(?:，(\d+) 个口令收回)?$/, function (m, p, n, u, c) {
        return 'Agent ' + p + ' deleted sub-agent ' + n + ' (' + u + ')' + (c ? ', ' + plural(c, 'code') + ' taken back' : '');
    }],
    [/^代理 (.+?) 给二级代理 (.+?)(?: 分了 (\d+) 个口令)?(?:，)?(?: 收回 (\d+) 个口令)?$/, function (m, p, n, g, tk) {
        return 'Agent ' + p + [g ? ' gave ' + plural(g, 'code') + ' to sub-agent ' + n : '', tk ? (g ? ' and' : '') + ' took back ' + plural(tk, 'code') + (g ? '' : ' from sub-agent ' + n) : ''].join('');
    }],
    [/^删除二级代理 (.+?)（([^（）]+)）(?:，TA 的 (\d+) 个口令收回给 (.+))?$/, function (m, n, u, c, p) {
        return 'Deleted sub-agent ' + n + ' (' + u + ')' + (c ? ', ' + plural(c, 'code') + ' returned to ' + p : '');
    }],
    [/^二级代理 (.+) (启用|停用)$/, function (m, n, s) { return ON[s] + ' sub-agent ' + n; }],
    [/^已开二级代理 (.+?)，登录账号 (\S+)，把账号和密码告诉 TA，在同一个登录页登录$/, 'Opened sub-agent $1, login account $2. Give them the account and password to sign in on the same sign-in page'],
    [/^已保存二级代理 (.+?)(，密码已重置)?$/, function (m, n, r) { return 'Saved sub-agent ' + n + (r ? ', password reset' : ''); }],
    [/^二级代理 (.+?) 已(启用|停用)(?:，(将无法登录|TA 不能登录了，分给 TA 的口令照常能用))?$/, function (m, n, s, why) {
        return 'Sub-agent ' + n + (s === '启用' ? ' enabled' : ' disabled')
            + (why === '将无法登录' ? ', can no longer sign in' : why ? '; they can no longer sign in, and codes given to them keep working' : '');
    }],
    [/^已更新二级代理 (.+) 的数据，截至 (.+)$/, 'Updated the data of sub-agent $1, as of $2'],
    [/^已更新 (\d+) 个手动二级代理的数据$/, 'Updated the data of $1 manual sub-agents'],
    [/^已删除二级代理 (.+?)(?:，TA 的 (\d+) 个口令(?:回到你手里|收回给 (.+)))?$/, function (m, n, c, p) {
        return 'Deleted sub-agent ' + n + (c ? ', ' + plural(c, 'code') + (p ? ' returned to ' + p : ' came back to you') : '');
    }],
    [/^已给 (.+?) 分了 (\d+) 个口令(?:，收回 (\d+) 个)?$/, function (m, n, g, tk) {
        return 'Gave ' + plural(g, 'code') + ' to ' + n + (tk ? ', took back ' + tk : '');
    }],
    [/^已从 (.+?) 那里收回 (\d+) 个口令$/, function (m, n, tk) { return 'Took back ' + plural(tk, 'code') + ' from ' + n; }],
    [/^最多开 (\d+) 个二级代理，已经开满了，要多开找管理员$/, 'You can open up to $1 sub-agents and the limit is reached; ask the admin for more'],
    [/^最多开 (\d+) 个，已经开满了，要多开找管理员$/, 'Up to $1, the limit is reached; ask the admin for more'],
    [/^(.+) 不是手动更新，不用更新$/, "$1 isn't on manual updates, nothing to update"],
    [/^口令 (.+) 已经分给别的二级代理了，先从 TA 那里收回$/, 'Code $1 is already given to another sub-agent; take it back from them first'],
    [/^数据更新不能比你自己的新：你是(.+?)，二级代理只能选(.+)$/, function (m, a, b) {
        return "Data updates can't be newer than yours: you are " + t(a) + ', sub-agents can only pick ' + b.split('、').map(t).join(', ');
    }],
    [/^(.+) 是二级代理，(设置由 TA 的上级代理在代理后台改|数据由 TA 的上级代理更新|TA 的口令由上级代理从自己的口令里分)$/, function (m, n, w) {
        return n + ' is a sub-agent; ' + { '设置由 TA 的上级代理在代理后台改': 'their parent agent changes the settings in the agent portal',
            '数据由 TA 的上级代理更新': 'their parent agent updates the data', 'TA 的口令由上级代理从自己的口令里分': 'their parent agent gives them codes from their own' }[w];
    }],
    [/^已保存代理 (.+?)(，密码已重置)?；关掉了二级代理，TA 的 (\d+) 个二级代理暂时不能登录，口令和数据都还在，重新打开就恢复$/, function (m, n, r, c) {
        return 'Saved agent ' + n + (r ? ', password reset' : '') + '; sub-agents turned off, their ' + plural(c, 'sub-agent') + " can't sign in for now. Codes and data are kept and come back when turned on again";
    }],
    [/^(\d+) 个二级代理，(\d+) 个启用(?: · 最多开 (\d+) 个)?$/, function (m, n, on, max) { return plural(n, 'sub-agent') + ', ' + on + ' enabled' + (max ? ' · up to ' + max : ''); }],
    [/^启用 (\d+) · (?:最多 (\d+) 个|不限个数)$/, function (m, on, max) { return on + ' enabled · ' + (max ? 'up to ' + max : 'no limit'); }],
    [/^已开 (\d+) 个(?:，最多 (\d+) 个|(，不限个数))?$/, function (m, n, max, free) { return n + ' opened' + (max ? ', up to ' + max : free ? ', no limit' : ''); }],
    [/^还能开 (\d+) 个$/, '$1 more allowed'], [/^你手里还有 (\d+) 个可用$/, 'You still have $1 usable'],
    [/^(\d+) 个「手动更新」的二级代理将看到截至(现在| .+?)的数据。$/, function (m, n, at) { return n + ' "manual" sub-agents will see data as of ' + (at === '现在' ? 'now' : at.trim()) + '.'; }],
    [/^(停用|删除)二级代理 (.+)？$/, function (m, v, n) { return (v === '停用' ? 'Disable' : 'Delete') + ' sub-agent ' + n + '?'; }],
    [/^账号删除后不能再登录。分给 TA 的 (\d+) 个口令(?:回到你手里，照常能用|收回给 (.+?))，激活记录都保留。$/, function (m, c, p) {
        return "The account can't sign in after deletion. The " + plural(c, 'code') + ' given to them ' + (p ? 'go back to ' + p : 'come back to you and keep working') + '; activation records are kept.';
    }],
    [/^分给了二级代理 (.+?)(，激活照常算在你名下|，激活仍算在上级名下)?$/, function (m, n, w) {
        return 'Given to sub-agent ' + n + (w ? (w.indexOf('你') >= 0 ? '; activations still count toward you' : '; activations still count toward the parent agent') : '');
    }],
    [/^给 (.+) 分口令$/, 'Share codes with $1'], [/^分给 TA (\d+) 个(?:，收回 (\d+) 个)?$/, function (m, g, tk) { return 'Give ' + g + (tk ? ', take back ' + tk : ''); }],
    [/^收回 (\d+) 个$/, 'Take back $1'],
    [/^批量(启用|停用)代理：(.+)$/, function (m, s, l) { return ON[s] + ' agents ' + names(l); }],
    [/^批量设置申请口令：(.+) · (.+)$/, function (m, l, d) { return 'Set code requests for ' + names(l) + ': ' + requestRule(d); }],
    [/^批量删除代理：(.+?)，(名下.+|\d+ 个口令改为未分配(?:，\d+ 个二级代理一起删除)?)$/, function (m, l, d) { return 'Deleted agents ' + names(l) + ', ' + agentGone(d); }],
    [/^代理 (.+)（([^（）]+)）导出激活记录 (\d+) 条$/, 'Agent $1 ($2) exported $3 activation records'],
    [/^代理 (.+)（([^（）]+)）修改了自己的密码$/, 'Agent $1 ($2) changed their password'],
    [/^导出 (\d+) 个代理$/, 'Exported $1 agents'], [/^导出代理 (.+) 的 (\d+) 条激活记录$/, 'Exported $2 activation records of agent $1'],
    [/^导出 (\d+) 条激活记录$/, 'Exported $1 activation records'], [/^导出 (\d+) 条操作日志$/, 'Exported $1 activity log entries'],
    [/^清空(后台操作日志|设备事件日志|全部日志)（(\d+) 条）$/, function (m, w, n) { return 'Cleared ' + LOGS[w] + ' (' + n + ')'; }],
    [/^(发布|修改|置顶|取消置顶|删除)公告「(.*)」$/, function (m, v, n) { return NOTICE[v] + ' notice "' + n + '"'; }],
    [/^([\w-]+) 复用 ([\w-]+) 的签名密钥（keyid (.+)），状态：(.+)$/, function (m, s, f, k, h) {
        return s + ' reused the signing key of ' + f + ' (keyid ' + k + '), status: ' + t(h);
    }],
    [/^([\w-]+) 更新签名(私钥和证书|私钥|证书)，公钥指纹 (\S+)，状态：(.+)$/, function (m, s, w, fp, h) {
        return s + ' updated the signing ' + SIGN[w] + ', public key fingerprint ' + fp + ', status: ' + t(h);
    }],
    [/^下载完整项目备份（数据和文件约 (.+)）$/, 'Downloaded a full project backup (data and files about $1)'],
    [/^开启自动备份：每天 (\d+) 点，保留 (\d+) 份$/, 'Turned on auto backup: daily at $1:00, keep $2'],
    [/^在服务器上备份了一份：(.+)（(.+?)）$/, 'Made a backup on the server: $1 ($2)'],
    [/^下载服务器上的备份 (.+)$/, 'Downloaded server backup $1'], [/^删除服务器上的备份 (.+)$/, 'Deleted server backup $1'],
    [/^从项目备份恢复（(.+)，备份时间 (.+?)），重启服务替换数据$/, function (m, f, at) {
        return 'Restoring from project backup (' + (t(f) === f ? f : lower(t(f))) + ', backed up ' + at + '), restarting the service to replace the data';
    }],
    [/^登录安全：记住我保持 (\d+) 天，不记住时空闲 (.+?) 退出，(要求|不要求)所有管理员开启两步验证，密码输错 (\d+) 次暂停登录 (\d+) 分钟，每人最多同时登录 (\d+) 台，操作日志(?:一直保留|保留 (\d+) 天)$/, function (m, d, idle, req, n, mins, dev, keep) {
        return 'Sign-in security: remember me for ' + d + ' days, otherwise sign out after ' + t(idle) + ' idle, ' + (req === '要求' ? 'require' : "don't require")
            + ' two-step verification for all admins, pause sign-in for ' + mins + ' min after ' + n + ' wrong passwords, up to ' + dev + ' devices per admin, keep activity logs '
            + (keep ? keep + ' days' : 'forever');
    }],
    [/^访问域名：(.+?)，(只允许这些域名访问|不限制访问域名)(?:，接口对外地址 (\S+))?$/, function (m, l, r, u) {
        return 'Domains: ' + (l === '未绑定域名' ? 'none' : l.split('、').join(', ')) + ', ' + (r === '不限制访问域名' ? 'any domain allowed' : 'only these domains allowed')
            + (u ? ', public API URL ' + u : '');
    }],
    [/^接口对外地址改为 (\S+)$/, 'Public API URL set to $1'],
    [/^没执行 · (.+)$/, function (m, a) { return 'Not run · ' + t(a); }],
    [/^高峰在 (\d+) 点$/, 'Peak at $1:00'],
    [/^(今天|明天|\d\d-\d\d) (\d\d?:\d\d) · (.+)$/, function (m, d, hm, rest) { return t(d) + ' ' + hm + ' · ' + t(rest); }],
    [/^(?:开启了演示账号 (\S+)|关闭了演示账号)( · 改了演示密码)?$/, function (m, u, pw) { return (u ? 'Turned on demo account ' + u : 'Turned off demo account') + (pw ? ' · demo password changed' : ''); }],
    [/^外观改为 (.+)$/, function (m, l) { return 'Appearance set to ' + looks(l); }],
    [/^已保存外观：(.+)$/, function (m, l) { return 'Appearance saved: ' + looks(l); }],
    [/^已保存默认外观：(.+)$/, function (m, l) { return 'Default appearance saved: ' + looks(l); }],
    [/^已保存你的外观：(.+)，只对你自己生效$/, function (m, l) { return 'Your appearance saved: ' + looks(l) + '. Only you see it'; }],
    // 备份下载
    [/^正在打包 (.+)$/, 'Packing $1'],
    [/^备份已打包好（(.+)），开始下载$/, 'Backup ready ($1), downloading'],
    [/^打包失败：(.+)$/, 'Packing failed: $1'],
    [/^正在备份 (.+)$/, 'Backing up $1'],
    [/^已备份：(\S+)（(.+)），存在服务器的 data\/backups 下$/, 'Backed up: $1 ($2), stored in data/backups on the server'],
    // 备份恢复：上传、检查、等重启
    [/^(\d+) 分 (\d+) 秒$/, '$1 min $2 s'],
    [/^已等待 (.+)$/, function (m, a) { return 'Waited ' + t(a); }],
    [/^(.+) · 点这里可以换一个$/, '$1 · click to choose another'],
    [/^(\S+ [KMG]B) \/ (\S+ [KMG]B)(?: · (\S+ [KMG]B)\/秒(?: · 还要约 (.+))?)?$/, function (m, a, b, sp, eta) {
        return a + ' / ' + b + (sp ? ' · ' + sp + '/s' + (eta ? ' · about ' + t(eta) + ' left' : '') : '');
    }],
    [/^已上传 (\S+ [KMG]B)，服务器正在解密和检查（(.+)），备份大的话要一会儿$/, function (m, a, c) {
        return 'Uploaded ' + a + '. The server is decrypting and checking it (' + t(c) + '); large backups take a while';
    }],
    [/^网络不稳，(\d+) 秒后重传这一片（第 (\d+) 次）…$/, 'Network unstable, resending this chunk in $1 s (attempt $2)…'],
    [/^请求太大，被反向代理拦下了（HTTP (\d+)）$/, 'The request is too large and was blocked by the reverse proxy (HTTP $1)'],
    [/^请求被网站防火墙拦截了（HTTP (\d+)）$/, 'The request was blocked by the website firewall (HTTP $1)'],
    [/^服务器没有正常响应（HTTP (\d+)），稍后再试$/, "The server didn't respond properly (HTTP $1). Try again later"],
    [/^请求失败（HTTP (\d+)）$/, 'Request failed (HTTP $1)'],
    [/^上传被反向代理拦下了：单次请求太大（HTTP (\d+)）。把 Nginx 的 client_max_body_size 调到 10m 以上后重试$/, 'Upload blocked by the reverse proxy: a single request is too large (HTTP $1). Raise Nginx client_max_body_size to 10m or more and try again'],
    [/^上传被反向代理拦下了：请求太大（HTTP (\d+)）$/, 'Upload blocked by the reverse proxy: the request is too large (HTTP $1)'],
    [/^上传被网站防火墙拦截了（HTTP (\d+)）。把 (\S+) 加进防火墙的 URL 白名单后重试$/, "Upload blocked by the website firewall (HTTP $1). Add $2 to the firewall's URL whitelist and try again"],
    [/^上传失败（HTTP (\d+)）$/, 'Upload failed (HTTP $1)'],
    [/^没有恢复：服务器返回 HTTP (\d+)$/, 'Not restored: the server returned HTTP $1'],
    [/^没有恢复：(.+)$/, function (m, a) { return 'Not restored: ' + t(a); }],
    [/^浏览器不让网页直接复制，已经帮你选中了，按 (⌘C|Ctrl C) 复制$/, "The browser doesn't allow direct copying. The text is selected, press $1 to copy"],
    // 文件存储：切换进度和结果，原来的存储连不上时先确认清空
    [/^(.+)。正在刷新…$/, function (m, a) { return t(a) + '. Refreshing…'; }],
    [/^复制了 (\d+) 个文件（(.+?)）(?:，原存储里本来就缺 (\d+) 个)?$/, function (m, n, size, miss) {
        return 'Copied ' + plural(n, 'file') + ' (' + size + ')' + (miss ? ', ' + miss + ' were already missing from the old storage' : '');
    }],
    [/^已复制 (\d+) 个文件（(.+?)），新存储里已有的直接跳过。复制期间可以正常使用$/, function (m, n, size) {
        return 'Copied ' + plural(n, 'file') + ' (' + size + '); files already in the new storage are skipped. You can keep working while copying';
    }],
    [/^(正在复制到|已切换到)(?:本机存储|对象存储 (.+))$/, function (m, a, b) {
        return (a === '已切换到' ? 'Switched to ' : 'Copying to ') + (b ? 'object storage ' + b : 'local storage');
    }],
    [/^正在清空版本，然后切换到(?:本机存储|对象存储 (.+))$/, function (m, b) { return 'Clearing versions, then switching to ' + (b ? 'object storage ' + b : 'local storage'); }],
    [/^切换的话会清空(?:所有版本（(\d+) 个）| (\d+) 个版本)?(?:(?:和 | )(\d+) 个安装包)?，然后换到(?:本机存储|对象存储 (.+?))。(?:这些版本的文件新存储里没有；另外 (\d+) 个版本新存储里文件齐全，留着。)?(?:外部链接的 (\d+) 个版本不受影响。)?清空的版本渠道上不再下发，App 继续用手机上已经下载的，重新上传就行。清空了不能恢复：如果只是密钥、权限或者网络的问题，先取消，修好了再切换$/, function (m, all, n, k, bucket, kept, ext) {
        var what = [];
        if (all) what.push('all ' + plural(all, 'version'));
        else if (n) what.push(plural(n, 'version'));
        if (k) what.push(plural(k, 'installer'));
        return 'Switching will clear ' + what.join(' and ') + ', then switch to ' + (bucket ? 'object storage ' + bucket : 'local storage') + '. '
            + (kept ? "These versions' files aren't in the new storage; the other " + plural(kept, 'version')
                + (kept === '1' ? ' has all its files there and is kept. ' : ' have all their files there and are kept. ') : '')
            + (ext ? plural(ext, 'external-link version') + (ext === '1' ? ' is' : ' are') + ' not affected. ' : '')
            + "Cleared versions are no longer delivered on channels; Apps keep using what's already downloaded on the phone until you upload again. "
            + "Clearing can't be undone: if it's only a key, permission or network problem, cancel and switch again after fixing it";
    }],
    [/^(.+)\n\n(切换的话会清空.+)$/, function (m, a, b) { return t(a) + '\n\n' + t(b); }],
    [/^原来的存储连不上，清空了(?: (\d+) 个版本)?(?:(?:和 | )(\d+) 个安装包)?后换了过来(?:，(\d+) 个文件齐全的版本留着)?(?:；顺便清理了 (\d+) 个用不上的文件)?$/, function (m, n, k, kept, cleaned) {
        var what = [];
        if (n) what.push(plural(n, 'version'));
        if (k) what.push(plural(k, 'installer'));
        return "The old storage can't be reached; cleared " + what.join(' and ') + ' and switched'
            + (kept ? ', kept ' + plural(kept, 'version') + ' with all files present' : '')
            + (cleaned ? '; also cleaned up ' + plural(cleaned, 'unused file') : '');
    }],
    [/^已经换过去了，但配置没保存上（(.+)），重启后会变回原来的存储，请再切换一次$/, function (m, a) {
        return "Switched, but the setting wasn't saved (" + t(a) + '). It will go back to the old storage after a restart; switch again';
    }],
    [/^已经换过去了，但收尾时出错：(.+)$/, function (m, a) { return 'Switched, but finishing up failed: ' + t(a); }],
    [/^切换文件存储到(?:本机存储|对象存储 (.+?))：原来的存储连不上（(.+)），(?:清空了(?: (\d+) 个版本)?(?:(?:和 | )(\d+) 个安装包)?|(版本和安装包的文件新存储里都有))$/, function (m, bucket, why, n, k, all) {
        var to = 'Switched file storage to ' + (bucket ? 'object storage ' + bucket : 'local storage') + ": the old storage can't be reached (" + t(why) + '), ';
        if (all) return to + 'all version and installer files were already in the new storage';
        var what = [];
        if (n) what.push(plural(n, 'version'));
        if (k) what.push(plural(k, 'installer'));
        return to + 'cleared ' + what.join(' and ');
    }],
    // 文件存储：测试连接、切换
    [/^(连接失败|测试失败|切换失败)：(.+)$/, function (m, a, b) { return { 连接失败: 'Connection failed', 测试失败: 'Test failed', 切换失败: 'Switch failed' }[a] + ': ' + t(b); }],
    [/^(上传失败|读取失败|删除失败)：(.+)$/, function (m, a, b) { return { 上传失败: 'Upload failed', 读取失败: 'Read failed', 删除失败: 'Delete failed' }[a] + ': ' + t(b); }],
    [/^Secret Key 不对（签名对不上），检查有没有复制全、多了空格$/, 'Wrong Secret Key (signature mismatch). Check that it was copied completely without extra spaces'],
    [/^Access Key 不对，对象存储不认识这个 Access Key$/, "Wrong Access Key; the object storage doesn't recognize it"],
    [/^Access Key 和 Secret Key 填反了：(\w+) 开头的那串要填在 Access Key，另一串填在 Secret Key$/, 'Access Key and Secret Key are swapped: the value starting with $1 goes in Access Key, the other one in Secret Key'],
    [/^Access Key 不对，对象存储不认识「(.+)」（(\d+) 位）。看看是不是填错了：腾讯云 COS 填 SecretId（AKID 开头），阿里云 OSS 填 AccessKey ID（LTAI 开头）$/, "Wrong Access Key: the object storage doesn't recognize \"$1\" ($2 characters). Check the value: Tencent COS uses the SecretId (starts with AKID), Aliyun OSS uses the AccessKey ID (starts with LTAI)"],
    [/^存储桶 (\S+) 不存在：检查存储桶名，以及服务地址是不是存储桶所在地域的$/, "Bucket $1 doesn't exist. Check the bucket name and that the endpoint is in the bucket's region"],
    [/^没有权限（AccessDenied）：给这个 Access Key 开通这个存储桶的读写权限$/, 'Access denied: grant this Access Key read/write access to the bucket'],
    [/^区域不对：区域要填 (\S+)$/, 'Wrong region: the region should be $1'],
    [/^服务器时间不准，和对象存储差太多：先校准服务器时间（开启 NTP 时间同步）$/, "The server clock is too far off from the object storage. Fix the server time first (turn on NTP sync)"],
    [/^这家对象存储不支持路径风格访问：取消勾选「路径风格访问」$/, "This object storage doesn't support path-style access; uncheck Path-style access"],
    [/^存储桶不在这个服务地址对应的地域：换成存储桶所在地域的服务地址$/, "The bucket isn't in this endpoint's region; use the endpoint of the bucket's region"],
    [/^找不到 (\S+)（域名解析失败），检查服务地址有没有写错$/, "Can't find $1 (DNS lookup failed). Check the endpoint for typos"],
    [/^连不上 (\S+)：检查服务地址、端口，以及服务器能不能访问这个地址$/, "Can't connect to $1. Check the endpoint and port, and whether the server can reach it"],
    [/^连接 (\S+) 超时：检查服务地址、端口和防火墙$/, 'Connecting to $1 timed out. Check the endpoint, port and firewall'],
    [/^和 (\S+) 的 HTTPS 连接失败：服务地址填地域节点，不要带存储桶名；自建存储没有 HTTPS 证书就用 http:\/\/ 开头$/, 'HTTPS to $1 failed. Use the region endpoint without the bucket name; for self-hosted storage without an HTTPS certificate, start with http://'],
    [/^服务地址格式不对：(.+)，要填成 https:\/\/oss-cn-hangzhou\.aliyuncs\.com 这样$/, 'Invalid endpoint: $1. It should look like https://oss-cn-hangzhou.aliyuncs.com'],
    [/^能写入、读取，但 App 下载不了：(\S+) 返回 HTTP (\d+)。把存储桶设为「公共读」，或者配好 CDN 回源后把 CDN 地址填到「下载地址」，再测试$/, "Writing and reading work, but Apps can't download: $1 returned HTTP $2. Make the bucket public-read, or set up CDN origin fetch and put the CDN address under Download URL, then test again"],
    // 外部链接：后台读取进度
    [/^已用 (\d+ 秒|\d+ 分 \d+ 秒)$/, function (m, a) { return 'Elapsed ' + t(a); }],
    [/^读完了：(\d+) 个文件，(.+)$/, 'Done: $1 files, $2'],
    [/^用了 (.+)，点「添加」马上就好$/, function (m, a) { return 'Took ' + t(a) + '. Click Add to finish right away'; }],
    [/^已下载 (\S+ [KM]B)(?: · (\S+ [KM]B)\/秒)? · 已用 (.+?)(?: · 正在下载 (\S+?)(?:（(\S+ [KM]B) \/ (\S+ [KM]B)）)?)?$/, function (m, got, sp, el, name, r, s) {
        return 'Downloaded ' + got + (sp ? ' · ' + sp + '/s' : '') + ' · elapsed ' + t(el) + (name ? ' · downloading ' + name + (r ? ' (' + r + ' / ' + s + ')' : '') : '');
    }],
    [/^这个链接下载很慢（约 (\S+ [KM]B)\/秒）。App 也是从这里下载更新的，设备那边一样会很慢，建议把 dist 放到 CDN 或对象存储上$/, "This link is very slow (about $1/s). Apps download updates from here too, so devices will be just as slow. Consider putting dist on a CDN or object storage"],
    // 发布：会不会复制成新版本（服务器算好的）
    [/^线上的 (\w+) 和它是同一份内容，不会再复制，只按上面的比例调整灰度$/, 'Live $1 has the same content, so nothing is copied; only the rollout above is adjusted'],
    [/^线上的 (\w+)(（副本）)? 是 (\S+ \S+) 建的，比这个版本（(\S+ \S+)）晚。App 只装比自己正在用的更新的版本，所以要复制成一个内容相同的新版本再发布，已经装了 (\w+) 的设备才会换过来$/, function (m, id, copy, at, mine, id2) {
        return 'Live ' + id + (copy ? ' (a copy)' : '') + ' was created at ' + at + ', later than this release (' + mine + '). The App only installs versions newer than the one it runs, so a copy with the same content is published and devices that have ' + id2 + ' switch to it';
    }],
    [/^(.+?)；口令更新包 (\w+) 也换成这个版本$/, function (m, a, id) { return t(a) + '; activation package ' + id + ' is switched to this release too'; }],
    [/^(.+?)；口令更新包也设成这个版本$/, function (m, a) { return t(a) + '; the activation package is set to this release too'; }],
    [/^(\S+) 现在的常规更新包是 (\w+)，就是这个版本的内容(?:；口令更新包 (\w+) 不受影响)?$/, function (m, ch, reg, act) {
        return ch + "'s regular package is " + reg + ', which already has this content' + (act ? '; activation package ' + act + ' is unaffected' : '');
    }],
    [/^它比线上的 (\w+) 旧，App 只装比自己新的版本，所以会复制成一个内容相同的新版本再发布，已经更新过的设备才会换过来$/, "It's older than live $1, and the App only installs versions newer than its own, so it will be copied as a new version with the same content; devices that already updated then switch to it"],
    [/^(.*)。线上本来就是这个版本的内容（(\w+)），没有再复制(.*)$/, function (m, a, id, b) { return t(a) + '. Live already had this content (' + id + '), so nothing was copied' + t(b); }],
    // 安装引导：等重启
    [/^(请不要关闭页面|比平时慢一点，再等等)（已等待 (.+)）$/, function (m, a, w) {
        return (a === '请不要关闭页面' ? "Please don't close this page" : 'Slower than usual, please wait') + ' (waited ' + t(w) + ')';
    }],
    [/^配置已经保存，还在接着等（已等待 (.+)）。一直不好的话，看看服务器日志里有没有「重启失败」，或者手动重启服务（宝塔里重启这个 Java 项目）$/, function (m, w) {
        return 'Settings saved, still waiting (' + t(w) + '). If it never comes back, check the server log for "重启失败" (restart failed), or restart the service manually (restart this Java project in BT Panel)';
    }],
    // 安装包检查
    [/^线上版本 (\w+) 比内置版本早 (.+?)，设备会认为已是最新。打完安装包后需重新发布一次$/, function (m, id, gap) {
        return 'Live release ' + id + ' is ' + t(gap) + ' older than the embedded build, so devices treat themselves as up to date. Publish again after building the installer';
    }],
    [/^线上版本 (\w+) 比内置版本晚 (.+?)，设备会下载$/, function (m, id, gap) {
        return 'Live release ' + id + ' is ' + t(gap) + ' newer than the embedded build; devices will download it';
    }],
    [/^口令更新包 (\w+) 比内置版本早 (.+?)，新装的设备输入口令激活时会认为已是最新、拿不到更新。打完安装包后需重新上传口令更新包$/, function (m, id, gap) {
        return 'Activation package ' + id + ' is ' + t(gap) + ' older than the embedded build, so new installs activating with a code treat themselves as up to date and get no update. Upload a new activation package after building the installer';
    }],
    [/^口令更新包 (\w+) 比内置版本晚 (.+?)，新装的设备输入口令激活时会下载$/, function (m, id, gap) {
        return 'Activation package ' + id + ' is ' + t(gap) + ' newer than the embedded build; new installs download it when activating with a code';
    }],
    [/^仅启动出错时检查（(\w+)），平时由 App 调用 fetchUpdateAsync 更新$/, 'Checks only after a launch error ($1); otherwise the App updates via fetchUpdateAsync'],
    [/^每次启动检查（(\w+)）$/, 'Checks on every launch ($1)'],
    [/^启动时检查（(\w+)）$/, 'Checks on launch ($1)'],
    [/^安装包的签名算法是 (\S+)，服务端用 rsa-v1_5-sha256 签名，App 会拒绝更新$/, "The installer's signing algorithm is $1, but the server signs with rsa-v1_5-sha256; the App will reject updates"],
    [/^证书一致，但 keyid 不一样（安装包 (\S+)，App (\S+)），App 会拒绝更新$/, 'Same certificate but a different keyid (installer $1, App $2); the App will reject updates'],
    [/^内置证书和 App 的签名证书一致，keyid「(.+)」$/, "The built-in certificate matches the App's signing certificate, keyid \"$1\""],
    [/^有内置更新(?: (\w+))?$/, function (m, id) { return 'Has an embedded update' + (id ? ' ' + id : ''); }],
    // 页面上的句式
    [/^代理手里 ([\d,]+) 个口令，还能用 ([\d,]+) 个$/, 'Agents hold $1 codes, $2 still usable'],
    [/^(.+) 已经在 (\d+) 台设备上登录，最多 (\d+) 台。选 (\d+) 台退出，就能在这台设备上登录。$/, '$1 is signed in on $2 devices (up to $3). Sign out $4 to sign in here.'],
    [/^(\d+) 个区域 · 约 1 分钟$/, '$1 areas · about 1 min'],
    [/^(\d\d-\d\d)：([\d,]+) 台 · iOS ([\d,]+) · Android ([\d,]+)$/, '$1: $2 devices · iOS $3 · Android $4'], [/^今天：([\d,]+) 台 · iOS ([\d,]+) · Android ([\d,]+)$/, 'Today: $1 devices · iOS $2 · Android $3'],
    [/^(\d\d-\d\d)：还没开始按天记录$/, '$1: not recorded yet'], [/^(\d+) 点：还没到$/, '$1:00: not yet'],
    [/^(\d\d-\d\d) 开始按天记录，之前的日子没有数据$/, 'Recorded daily since $1; no data before'],
    [/^近 30 天 · (\d\d-\d\d) 开始按天记录，之前的日子没有数据$/, 'Last 30 days · recorded daily since $1; no data before'],
    [/^近 30 天 · 日均 ([\d,]+) 台（近 7 天） · (\d\d-\d\d) 开始按天记录，之前的日子没有数据$/, 'Last 30 days · avg $1 a day (last 7 days) · recorded daily since $2'],
    [/^近 30 天 · 日均 ([\d,]+) 台（近 7 天）$/, 'Last 30 days · avg $1 a day (last 7 days)'],
    [/^每个管理员最多同时登录 (\d+) 台，满了再登录时选退出哪台$/, 'Each admin can be signed in on up to $1 devices; when full, pick which to sign out at login'],
    [/^Runtime (\S+) · 渠道 (\S+)$/, 'Runtime $1 · channel $2'], [/^内置版本 (.+)$/, 'Embedded $1'], [/^再选 (\d+) 台$/, 'Pick $1 more'], [/^退出 (\d+) 台并登录$/, 'Sign out $1 and sign in'], [/^至少选 (\d+) 台设备退出$/, 'Pick at least $1 devices to sign out'],
    [/^(\d+) 台设备正在登录，最多 (\d+) 台；满了再登录时会让你选退出哪台$/, '$1 devices signed in (up to $2); when full, you pick which to sign out at login'],
    [/^(\d+) 台设备正在登录，演示账号大家共用，不限台数$/, '$1 devices signed in; the shared demo account has no limit'],
    [/^最近使用 (.+) · 登录于 (.+)$/, function (m, a, b) { return 'Last used ' + t(a) + ' · signed in ' + t(b); }],
    [/^拿 (\w+)$/, 'get $1'], [/^(\w+) 的副本$/, 'copy of $1'], [/^(.*) · 仅 (iOS|Android)$/, '$1 · $2 only'], [/^其余用 (\w+)$/, 'the rest on $1'],
    [/^回退于 (.+?)，回退前是 (\w+)$/, 'Rolled back $1 (was $2)'], [/^装上 (\d+)%$/, 'installed $1%'], [/^· 失败 (\d+)$/, '· $1 failed'],
    [/^回滚或发布旧版本时复制出来的，内容和 (\w+) 一样$/, 'Copied when rolling back or republishing an older version; same content as $1'],
    [/^超级管理员 (\d+) · 成员 (\d+)$/, 'Super admins $1 · members $2'], [/^还有 (\d+) 位没开$/, '$1 not enabled yet'], [/^(\d+) 位还没登录过$/, '$1 never signed in'],
    [/^最多 (\d+) 台同时登录$/, 'Up to $1 devices at once'], [/^开启于 (.+) · 还剩 (\d+) 个恢复码$/, 'Enabled $1 · $2 recovery codes left'],
    [/^继续用上一版本 (\w+)$/, 'stay on previous $1'], [/^上一版本 (\w+)，可以回滚到它$/, 'Previous release $1, can roll back to it'],
    [/^回退于 (.+)，再发布任意版本即可恢复$/, function (m, a) { return 'Fell back ' + t(a) + ', publish any release to restore'; }],
    [/^(.+) 的上一版本（可以回滚到它）$/, '$1 previous release (can roll back to it)'], [/^灰度 (\d+)%$/, 'Rollout $1%'],
    // 口令更新包
    [/^已把 (\w+) 设为 (\S+) 的口令更新包：新装 App 输入口令激活时下载它，已激活的设备照常拿常规更新包$/, 'Set $1 as the activation package of $2. New installs download it when activating with a code; activated devices keep getting the regular package'],
    [/^(\w+) 本来就是 (\S+) 的口令更新包$/, '$1 is already the activation package of $2'],
    [/^已取消 (\S+) 的口令更新包，输入口令激活时改回拿常规更新包$/, 'Cleared the activation package of $1. Activation with a code gets the regular package again'],
    [/^已上传 (\w+)（Runtime ([^）]+)），(?:并已设为|它本来就是) (\S+) 的口令更新包$/, 'Uploaded $1 (Runtime $2) and set it as the activation package of $3'],
    [/^已添加外部链接版本 (\w+)（(\d+) 个文件，([^）]+)），(?:并已设为|它本来就是) (\S+) 的口令更新包。App 会直接从外部链接下载$/, 'Added external release $1 ($2 files, $3) and set it as the activation package of $4. The App downloads straight from the external link'],
    [/^(\S+) 在 Runtime (\S+) 下没有单独的口令更新包$/, '$1 has no separate activation package for Runtime $2'],
    [/^这是 (\S+) 的口令更新包，输入口令激活的设备在下载它，不能删除。先换一个或取消口令更新包$/, "This is the activation package of $1 and devices activating with a code download it, so it can't be deleted. Change or clear the activation package first"],
    [/^取消 (\S+) 的口令更新包？$/, 'Clear the activation package of $1?'],
    [/^(常规更新|口令更新)：(.+)$/, function (m, k, l) { return (k === '常规更新' ? 'Regular update: ' : 'Activation update: ') + l.split('、').join(', '); }],
    [/^(\S+) 口令更新$/, '$1 activation'], [/^口令更新 (\w+)$/, 'Activation $1'],
    [/^(\S+) 现在的口令更新包是 (\w+)，会换成这个版本$/, '$1 currently uses activation package $2; it will be replaced by this release'],
    [/^(\S+) 现在没单独设口令更新包，新装 App 激活时拿常规更新包(?: (\w+))?$/, function (m, ch, reg) { return ch + ' has no separate activation package; new installs get the regular package' + (reg ? ' ' + reg : '') + ' when activating'; }],
    [/^(\S+) (?:现在的常规更新包是 (\w+)，发布后它变成上一版本|在 Runtime (\S+) 下还没有常规更新包)；(?:口令更新包 (\w+) 不受影响|新装 App 激活时也拿这个版本)$/, function (m, ch, reg, rt, act) {
        return (reg ? ch + ' currently uses regular package ' + reg + ', which becomes the previous release' : ch + ' has no regular package for Runtime ' + rt + ' yet')
            + (act ? '; activation package ' + act + ' is not affected' : '; new installs also get this release when activating');
    }],
    [/^(\S*) ?(\d{4}-\d\d-\d\d \d\d:\d\d(?::\d\d)?) 设置$/, function (m, by, at) { return 'Set ' + (by ? 'by ' + by + ' ' : '') + 'at ' + at; }],
    [/^(取消|删除|发布)失败：(.+)$/, function (m, v, why) { return t(v + '失败：') + ' ' + t(why); }],
    [/^设备还在用安装包内置版本（新装的、正在输入口令激活的），渠道 (\S+) 单独设了口令更新包 (\w+)，直接拿它，不看灰度和回退内置版本$/, 'The device is still on the embedded version (a new install or activating with a code). Channel $1 has a separate activation package $2, so the device gets it directly, regardless of rollout and embedded fallback'],
    [/^口令更新包 (\w+) 没有 (iOS|Android) 的文件，照旧拿常规更新包$/, 'Activation package $1 has no $2 files, so the device gets the regular package as usual'],
    [/^渠道 (\S+) 单独设了口令更新包 (\w+)，只给新装 App 输入口令激活时用；这台设备已经激活过，拿常规更新包$/, 'Channel $1 has a separate activation package $2, used only when new installs activate with a code. This device is already activated, so it gets the regular package'],
    [/^口令 ([\d,]+) 个，还能用 ([\d,]+) 个$/, '$1 codes, $2 still usable'],
    [/^启用 ([\d,]+) · 今天有激活 ([\d,]+)$/, '$1 enabled · $2 active today'],
    [/^(.+) · 与实际一致$/, function (m, a) { return t(a) + ' · matches actual'; }],
    [/^申请需审核 · 每天 (\d+) 个$/, 'Review · $1/day'], [/^申请需审核 · 不限$/, 'Review · no limit'],
    [/^申请自动通过 · 每天 (\d+) 个$/, 'Auto-approve · $1/day'], [/^申请自动通过 · 不限$/, 'Auto-approve · no limit'],
    [/^自定义 (\d+) 个$/, 'Custom × $1'], [/^随机 (\d+) 个$/, 'Random × $1'],
    [/^(.+) · 自定义 (\d+) 个$/, '$1 · custom × $2'], [/^(.+) · 随机 (\d+) 个$/, '$1 · random × $2'],
    [/^合计 ([\d,]+) 台$/, '$1 total'], [/^· 每天最多 (\d+) 个$/, '· up to $1 per day'],
    [/^全部 ([\d,]+) 个$/, 'All $1'], [/^(.+) · 设备 (.+)$/, '$1 · device $2'], [/^昨天 ([\d,]+) 台$/, 'Yesterday $1'],
    [/^共 ([\d,]+) 条 · 第 (\d+) \/ (\d+) 页$/, '$1 total · page $2 of $3'], [/^测试 · (.+)$/, 'Test · $1'], [/^(.+)（已停用）$/, '$1 (disabled)'],
    [/^在用 ([\d,]+)( 台)?$/, 'In use: $1'], [/^还没开启：(.+)$/, function (m, a) { return 'Not enabled yet: ' + a.replace(/、/g, ', '); }], [/^最近 (\d+) 份$/, 'Keep last $1'],
    [/^共 (\d+) 个 App，点「进入」切换$/, '$1 Apps · click "Enter" to switch'], [/^(.+)（(.+)，keyid (.+)）$/, '$1 ($2, keyid $3)'],
    [/^(.*)RSA (\d+) 位$/, function (m, pre, n) { pre = pre.replace(/\s*·\s*$/, ''); return (pre ? t(pre) + ' · ' : '') + 'RSA ' + n + '-bit'; }],
    [/^(\d{4}-\d\d-\d\d) 至 (\d{4}-\d\d-\d\d)$/, '$1 to $2'], [/^(.+) · 设置$/, '$1 · Settings'],
    [/^(.+) 的版本 (\w+) 有 (\d+) 台设备启动失败（([\d.]+)%），建议回滚$/, function (m, ch, v, n, p) { return ch.replace(/、/g, ', ') + ': version ' + v + ' failed to launch on ' + n + ' devices (' + p + '%), consider rolling back'; }],
    [/^(\d+) 分钟前$/, '$1 min ago'], [/^(\d+) 小时前$/, '$1 h ago'], [/^(\d+) 天前$/, '$1 d ago'],
    [/^今天 (\d\d?:\d\d)$/, 'Today $1'], [/^昨天 (\d\d?:\d\d)$/, 'Yesterday $1'], [/^前天 (\d\d?:\d\d)$/, '2 days ago $1'],
    [/^(\d\d-\d\d \d\d:\d\d) 切换$/, 'switched $1'],
    // 代理后台：时间段、按月、和前一段比、二级代理带来的
    [/^近 (\d+) 天 ([\d,]+) 台$/, 'Last $1 days: $2'], [/^(\d+) 月 ([\d,]+) 台$/, function (m, mo, n) { return MONTH[mo] + ': ' + n; }],
    [/^(\d{4}) 年 (\d+) 月 ([\d,]+) 台$/, function (m, y, mo, n) { return MONTH[mo] + ' ' + y + ': ' + n; }],
    [/^(\d\d-\d\d|\d{4}-\d\d-\d\d) 至 (\d\d-\d\d|\d{4}-\d\d-\d\d)( ([\d,]+) 台)?$/, function (m, a, b, x, n) { return a + ' to ' + b + (n ? ': ' + n : ''); }],
    [/^(\d+) 月$/, function (m, mo) { return MONTH[mo]; }], [/^(\d{4}) 年 (\d+) 月$/, function (m, y, mo) { return MONTH[mo] + ' ' + y; }],
    [/^(\d+) 月累计$/, function (m, mo) { return MONTH[mo] + ' total'; }],
    [/^(\d+) 月：([\d,]+) 台$/, function (m, mo, n) { return MONTH[mo] + ': ' + n; }],
    [/^(\d{4}) 年 (\d+) 月：([\d,]+) 台$/, function (m, y, mo, n) { return MONTH[mo] + ' ' + y + ': ' + n; }],
    [/^(\d\d-\d\d|\d{4}-\d\d-\d\d) (周.)：([\d,]+) 台$/, function (m, d, w, n) { return d + ' ' + (WEEK[w] || w) + ': ' + n; }],
    [/^(\d\d-\d\d|今天|昨天)：([\d,]+) 台$/, function (m, d, n) { return t(d) + ': ' + n; }],
    [/^(\d\d-\d\d) 新激活的 ([\d,]+) 台，第 (\d+) 天（(\d\d-\d\d)）打开过的有 ([\d,]+) 台$/, '$2 activated on $1: $5 opened on day $3 ($4)'],
    [/^(\d\d-\d\d) 新激活的 ([\d,]+) 台，第 (\d+) 天（(\d\d-\d\d)）是今天，到现在打开过的有 ([\d,]+) 台，还会涨$/, '$2 activated on $1: day $3 ($4) is today, $5 opened so far and still rising'],
    [/^(\d\d-\d\d) 新激活的 ([\d,]+) 台，第 (\d+) 天（(\d\d-\d\d)）还没到$/, "$2 activated on $1: day $3 ($4) hasn't come yet"],
    [/^(\d\d-\d\d) 新激活的 ([\d,]+) 台，第 (\d+) 天（(\d\d-\d\d)）：那时还没开始统计日活$/, '$2 activated on $1: day $3 ($4) was before daily stats started'],
    [/^自定义：(.+)$/, function (m, r) { return 'Custom: ' + t(r); }],
    [/^设备 (\d+) 天没打开就清掉指纹，之后再激活重新算；([\d,]+) 台已经超过 \d+ 天没打开的设备，指纹现在就清掉$/,
        'Devices idle for $1 days get their fingerprint cleared and count again on their next activation. Clearing $2 devices idle longer than that now'],
    [/^设备 (\d+) 天没打开就清掉指纹，之后再激活重新算；现在没有超过 \d+ 天没打开的设备$/,
        'Devices idle for $1 days get their fingerprint cleared and count again on their next activation. No device has been idle that long'],
    [/^设备 (\d+) 天没打开就清掉指纹，之后再激活重新算（开了按设备去重以后生效）$/,
        'Devices idle for $1 days get their fingerprint cleared and count again on their next activation (takes effect once device dedupe is on)'],
    [/^已开启按设备去重：同一台手机在别的 App 算过有效激活，这个 App 就不再算；([\d,]+) 台超过 (\d+) 天没打开的设备，指纹现在就清掉$/,
        "Device dedupe is on: a phone already counted as a valid activation in another App won't count here. Clearing the fingerprints of $1 devices idle for over $2 days now"],
    [/^前 (\d+) 天$/, 'Previous $1 days'], [/^比前 (\d+) 天$/, 'vs previous $1 days'],
    [/^比(\d+) 月$/, function (m, mo) { return 'vs ' + MONTH[mo]; }], [/^比(\d{4}) 年 (\d+) 月$/, function (m, y, mo) { return 'vs ' + MONTH[mo] + ' ' + y; }],
    [/^比前 7 天 · 日均 ([\d.]+)$/, 'vs previous 7 days · avg $1 a day'],
    [/^名下口令 (\d+) 个 · 可用 (\d+) 个$/, function (m, a, b) { return plural(a, 'code') + ' · ' + b + ' usable'; }],
    [/^(.+) · 按你看到的算$/, function (m, r) { return t(r) + ' · as you see it'; }],
    [/^TA 们看到 ([\d,]+) · 累计 ([\d,]+)$/, 'They see $1 · $2 in total'],
    [/^(近 \d+ 天|\d+ 月|\d{4} 年 \d+ 月|\d\d-\d\d 至 \d\d-\d\d|\d{4}-\d\d-\d\d 至 \d{4}-\d\d-\d\d) · 累计 ([\d,]+)$/, function (m, r, n) { return t(r) + ' · ' + n + ' in total'; }],
    [/^今天已经是(昨天全天|近 7 天日均)的 (\d+) 倍多$/, function (m, w, n) { return 'Today is already over ' + n + '× ' + (w === '昨天全天' ? "yesterday's total" : 'the 7-day daily average'); }],
    [/^今天已经超过(昨天全天|近 7 天日均)$/, function (m, w) { return 'Today has passed ' + (w === '昨天全天' ? "yesterday's total" : 'the 7-day daily average'); }],
    [/^比昨天同一时刻多 ([\d,]+)$/, '$1 ahead of this time yesterday'],
    [/^代理 (.+?) 给二级代理发公告「(.*)」$/, 'Agent $1 sent sub-agents the notice "$2"'],
    [/^代理 (.+?) (修改|删除)给二级代理的公告「(.*)」$/, function (m, a, v, n) { return 'Agent ' + a + (v === '修改' ? ' edited' : ' deleted') + ' the sub-agent notice "' + n + '"'; }],
    [/^(发布|修改)公告「(.*)」 · (发给全部代理（含二级代理）|只发一级代理)$/, function (m, v, n, w) {
        return NOTICE[v] + ' notice "' + n + '" · ' + (w === '只发一级代理' ? 'top-level agents only' : 'all agents incl. sub-agents');
    }],
    [/^已发布公告「(.*)」，(你的二级代理|代理和二级代理|一级代理)登录后就能看到$/, function (m, n, w) {
        return 'Published notice "' + n + '"; ' + { '你的二级代理': 'your sub-agents', '代理和二级代理': 'agents and sub-agents', '一级代理': 'top-level agents' }[w] + ' will see it after signing in';
    }],
    [/^已保存(代理|二级代理) (.+?)(，密码已重置)?；新的扣量从现在起算，之前的激活还按原来的比例，已经扣过的不会变(；关掉了二级代理.*)?$/, function (m, k, n, r, more) {
        return 'Saved ' + (k === '代理' ? 'agent ' : 'sub-agent ') + n + (r ? ', password reset' : '')
            + '; the new deduction applies from now on. Earlier activations keep the old ratio, and what has been deducted stays as it is' + (more ? t(more) : '');
    }],
    [/^近 (\d+) 天$/, 'Last $1 days'], [/^近 (\d+) 天 (\d+) 台$/, 'Last $1 days: $2'], [/^(\d+) 天$/, '$1 days'], [/^(\d+) 天前 ?登录$/, 'signed in $1 d ago'],
    [/^共 ([\d,]+) 个$/, '$1 total'], [/^共 ([\d,]+) 条$/, '$1 total'], [/^共 ([\d,]+) 台$/, '$1 devices'],
    [/^([\d,]+) 个$/, '$1'], [/^([\d,]+) 条$/, '$1'], [/^([\d,]+) 台$/, '$1 devices'], [/^([\d,]+) 次$/, '$1 times'], [/^([\d,]+) 人$/, '$1 people'],
    [/^([\d,]+) 件$/, '$1 items'], [/^([\d,]+) 位$/, '$1 digits'], [/^([\d,]+) 份$/, '$1 copies'], [/^(\d+) 点$/, '$1:00'], [/^(\d+) 点：(\d+) 台$/, '$1:00: $2'],
    [/^今日 \+(\d+)$/, 'Today +$1'], [/^今天 \+(\d+)$/, 'Today +$1'], [/^已选 (\d+) 个$/, '$1 selected'], [/^已选 (\d+) \/ (\d+)$/, '$1 / $2 selected'],
    [/^第 (\d+) 页$/, 'Page $1'], [/^(\d+) 条待审核$/, '$1 pending'], [/^(\d+) 待审核$/, '$1 pending'], [/^(\d+) 个待审核$/, '$1 pending'],
    [/^每天 (\d+) 个$/, '$1 per day'], [/^每天最多 (\d+) 个$/, 'up to $1 per day'], [/^(\d+) \/ (\d+) 已读$/, '$1 / $2 read'],
    [/^(\d+) 分钟$/, '$1 min'], [/^(\d+) 小时$/, '$1 h'], [/^(\d+) 秒$/, '$1 s'],
    [/^来自 (.+)$/, 'From $1'], [/^代理：(.+)$/, 'Agent: $1'], [/^代理 ([^\s，。：（）]+)$/, 'Agent $1'], [/^渠道：(.+)$/, 'Channel: $1'],
    [/^操作人：(.+)$/, 'By $1'], [/^备注：(.+)$/, 'Note: $1'], [/^原因：(.+)$/, 'Reason: $1'],
    [/^错 (\d+) 次$/, '$1 failures'], [/^暂停 (\d+) 分钟$/, 'Pause $1 min'], [/^保留 (\d+) 天$/, 'Keep $1 days'],
    [/^保留 (\d+) 天，每天凌晨自动清理$/, 'Kept $1 days, cleaned up nightly'],
    [/^同一个账号连续输错到次数就暂停登录；同一 IP 换着账号试，错 (\d+) 次也暂停$/, 'Sign-in pauses after this many wrong passwords for one account; trying different accounts from one IP pauses after $1'],
    [/^同一账号连续输错 (\d+) 次、或同一 IP 错 (\d+) 次，暂停登录 (\d+) 分钟$/, '$1 wrong passwords for one account, or $2 from one IP, pause sign-in for $3 min'],
    [/^密码错误次数太多，已暂停登录，请 (\d+) 分钟后再试$/, 'Too many wrong passwords, sign-in paused, try again in $1 minutes'],
    [/^已用 (.+) \/ 最多 (.+)$/, '$1 used of $2'], [/^剩余 (.+) \/ 共 (.+)$/, '$1 free of $2'],
    [/^(\d+) 天 (\d+) 小时$/, '$1 d $2 h'], [/^(\d+) 小时 (\d+) 分钟$/, '$1 h $2 min'],
    [/^已保存，之后的登录按新设置生效；清掉了 (\d+) 条超过 (\d+) 天的操作日志$/, 'Saved; later sign-ins follow the new settings. Removed $1 activity log entries older than $2 days'],
    [/^已保存，群机器人只推：(.+)$/, function (m, a) { return 'Saved. The group bot only sends: ' + a.split('、').map(t).join(', '); }],
    [/^已启用 (.+)，TA 可以登录了$/, 'Enabled $1. They can sign in now'],
    [/^已停用 (.+)，TA 已登录的后台会马上退出$/, 'Disabled $1. Their open sessions end right away'],
    [/^停用 (.+)？$/, 'Disable $1?'],
    [/^让 (.+) 退出所有设备？$/, 'Sign $1 out everywhere?'],
    [/^([\d,]+) 个口令$/, '$1 codes'], [/^([\d,]+) 个自定义口令$/, '$1 custom codes'],
    [/^已添加 (\d+) 张卡片，排在最后$/, 'Added $1 cards at the end'],
    [/^已收起「(.+)」，在「添加卡片」里可以再打开$/, function (m, a) { return 'Hid "' + t(a) + '". Turn it back on under Add cards'; }],
    [/^已打开 (\d+) 张$/, '$1 on'],
    [/^一共 (\d+) 张，打开的显示在概览上，新加的排在最后。只对你自己生效$/, '$1 cards. Turned-on cards show on the overview and new ones go last. Only affects you'],
    [/^今天的目标定成 ([\d,]+) 台$/, "Today's goal is now $1"],
    [/^(四分之一行|三分之一行|半行|三分之二行|整行) · (\d+) 列$/, function (m, a, b) { return t(a) + ' · ' + b + ' columns'; }],
    [/^高度 (\d+)$/, 'Height $1'],
    [/^高度 (\d+) · 旁边的卡片最多这么高$/, 'Height $1 · the cards beside it stop here'],
    [/^高度 (\d+) · 旁边的卡片要这么高$/, 'Height $1 · the cards beside it need this much'],
    [/^最高 (\d+)$/, 'Max $1'],
    [/^最低 (\d+)$/, 'Min $1'],
    [/^最低 (\d+) · 内容要显示完整$/, 'Min $1 · so the content fits'],
    [/^上周同期 ([\d,]+) · 上周共 ([\d,]+)$/, 'Same days last week $1 · last week $2'],
    [/^周([一二三四五六日])：([\d,]+) 台，上周 ([\d,]+) 台$/, function (m, a, b, c) { return t(a) + ': ' + b + ', last week ' + c; }],
    [/^周([一二三四五六日])：上周 ([\d,]+) 台$/, function (m, a, b) { return t(a) + ': last week ' + b; }],
    [/^周([一二三四五六日])$/, function (m, a) { return t(a); }],
    [/^周([一二三四五六日]) (\d+) 点：([\d,]+) 台$/, function (m, a, b, c) { return t(a) + ' ' + b + ':00: ' + c; }],
    [/^周([一二三四五六日]) (\d+) 点最多，4 周共 ([\d,]+) 台$/, function (m, a, b, c) { return 'Busiest at ' + t(a) + ' ' + b + ':00, ' + c + ' in 4 weeks'; }],
    [/^近 7 天提交 ([\d,]+)，确认 ([\d,]+)，确认率 (\d+)%$/, 'Last 7 days: $1 submitted, $2 confirmed ($3%)'],
    [/^超出 ([\d,]+) 台$/, '$1 over'], [/^还差 ([\d,]+) 台$/, '$1 to go'],
    [/^今天已经有效激活 ([\d,]+) 台$/, '$1 valid activations so far today'],
    [/^(.+) · 用了 (\d+) \/ (\d+)$/, function (m, a, b, c) { return t(a) + ' · used ' + b + ' / ' + c; }],
    [/^(.+) · 用了 (\d+) 次$/, function (m, a, b) { return t(a) + ' · used ' + b + ' times'; }],
    [/^还剩 (\d+) 天$/, '$1 days left'], [/^到期时间 (.+)$/, 'Expires $1'],
    [/^([\d,]+) 个随机口令(?: · (.+))?$/, function (m, a, b) { return a + ' random codes' + (b ? ' · ' + b : ''); }],
    [/^([\d,]+) 个自定义口令(?: · (.+))?$/, function (m, a, b) { return a + ' custom codes' + (b ? ' · ' + b : ''); }],
    [/^(.+) · 置顶$/, function (m, a) { return t(a) + ' · pinned'; }],
    [/^([\d,]+) \/ ([\d,]+) 个启用的代理看过$/, '$1 of $2 active agents read it'],
    [/^推送 ([\d,]+) 台，装上 ([\d,]+) 台(?: · 其余设备用 (\w+))?$/, function (m, a, b, c) { return a + ' offered, ' + b + ' installed' + (c ? ' · others stay on ' + c : ''); }],
    [/^还没有设备拿到 · 其余设备用 (\w+)$/, 'No device has it yet · others stay on $1'],
    [/^(\d+)% 的设备会拿到这个版本$/, '$1% of devices get this release'],
    [/^(\d+) 小时(?: (\d+) 分)?后$/, function (m, a, b) { return 'in ' + a + 'h' + (b ? ' ' + b + 'm' : ''); }],
    [/^(\d+) 分钟后$/, 'in $1 min'], [/^(\d+) 天后$/, 'in $1 days'],
    [/^(今天|明天|\d\d-\d\d) 周([一二三四五六日])$/, function (m, a, b) { return t(a) + ' ' + t(b); }],
    [/^(\S+) 灰度调到 (\d+)%$/, '$1 rollout to $2%'],
    [/^(上线模式|审核模式|Runtime \S+) · (.+) 设的$/, function (m, a, b) { return t(a) + ' · set by ' + b; }],
    [/^没登上 · (.+)$/, function (m, a) { return 'Failed · ' + t(a); }],
    [/^剩 (.+) \/ 共 (.+)$/, '$1 free of $2'],
    [/^按 App 授权，能管理口令(，能管理代理)?$/, function (m, a) { return 'Granted per app, can manage codes' + (a ? ' and agents' : ''); }],
    [/^([\d,]+) 个口令，([\d,]+) 个可用 · 所有 App 通用$/, '$1 codes, $2 usable · all Apps'],
    [/^已生成 (\d+) 个口令，在「数据概览」的我的口令里(.*)$/, function (m, a, b) { return 'Generated ' + a + ' codes; see My codes in the overview' + (b ? t(b) : ''); }],
    [/^已同意 (.+) 的申请，生成 (\d+) 个口令，TA 在代理后台就能看到$/, 'Approved $1\'s request and generated $2 codes; they can see them in the agent portal'],
    [/^申请 #(\d+)：你没有「口令与激活」权限，不能生成口令$/, 'Request #$1: you need the Codes & activations permission to generate codes'],
    [/^申请 #(\d+)。可以只同意其中几个口令$/, 'Request #$1. You can approve only some of the codes'],
    [/^申请 #(\d+)。个数和次数可以改$/, 'Request #$1. You can change the count and uses'],
    [/^已删除 (.+)（(.+)）及它的版本和激活记录，口令所有 App 通用，没有动。版本文件可以在「系统配置 → 存储」里清理$/, 'Deleted $1 ($2) with its releases and activation records. Codes work in every App and were kept. Clean up release files under Settings → Storage'],
    [/^已让 (.+) 在所有设备上退出登录，TA 用原来的密码重新登录就行$/, '$1 was signed out everywhere; they can sign in again with the same password'],
    [/^(.+) 设置$/, function (m, a) { return 'set by ' + t(a); }]
];

function stored() {
    try {
        return localStorage.getItem(KEY) === 'en' ? 'en' : 'zh';
    } catch (e) {
        return 'zh';
    }
}

var lang = stored();
var dict = null, trie = null;
var cache = new Map();
// 浏览器自带翻译会把字改回去，同一段字被反复改就不再跟
var churn = new WeakMap(), given = new WeakSet();

export function current() {
    return lang;
}

export function setLang(v) {
    try {
        localStorage.setItem(KEY, v === 'en' ? 'en' : 'zh');
    } catch (e) { /* 存不了就只对这次有效 */ }
}

// 句式里一定出现的中文，句子里没有就不跑正则
var NEED = PATTERNS.map(function (p) {
    var src = p[0].source.replace(/\\./g, ' ').replace(/\[[^\]]*\]/g, ' ');
    while (/\([^()]*\)/.test(src)) src = src.replace(/\([^()]*\)/g, ' ');
    if (src.indexOf('|') >= 0) return null;
    var runs = src.replace(/[\u4e00-\u9fff](?=[?*{])/g, ' ').match(/[\u4e00-\u9fff]{2,}/g);
    return runs ? runs.sort(function (a, b) { return b.length - a.length; })[0] : null;
});

function phraseTrie() {
    if (trie) return trie;
    trie = { c: new Map(), w: null };
    Object.keys(dict).forEach(function (k) {
        if (k.length < 2 || !dict[k] || !CJK.test(k) || /^[\s,，.。、:：;；)）(（]/.test(k)) return;
        var node = trie;
        for (var i = 0; i < k.length; i++) {
            var next = node.c.get(k[i]);
            if (!next) { next = { c: new Map(), w: null }; node.c.set(k[i], next); }
            node = next;
        }
        node.w = k;
        node.n = (k.match(CJK_ALL) || []).length;
    });
    return trie;
}

function byPhrases(core) {
    var root = phraseTrie(), total = (core.match(CJK_ALL) || []).length, covered = 0, out = '', i = 0;
    while (i < core.length) {
        var node = root, j = i, end = -1, word = null, n = 0;
        while (j < core.length) {
            node = node.c.get(core[j]);
            if (!node) break;
            j++;
            if (node.w) { end = j; word = node.w; n = node.n; }
        }
        if (word) {
            out += ' ' + dict[word] + ' ';
            covered += n;
            i = end;
        } else {
            out += core[i];
            i++;
        }
    }
    if (covered < total * 0.7) return null;
    out = out.replace(/(^|\s)的(\s|$)/g, ' ').replace(/(\d)\s*个/g, '$1').replace(/(\d)\s*台/g, '$1 devices').replace(/(\d)\s*条/g, '$1').replace(/(\d)\s*次/g, '$1 times').replace(/(\d)\s*天/g, '$1 days');
    return tidy(out);
}

function tidy(s) {
    return s.replace(/[，。：；（）、「」！？]/g, function (m) { return PUNCT[m]; })
        .replace(/\s+/g, ' ').replace(/\s+([,.;:!?)%])/g, '$1').replace(/\(\s+/g, '(').replace(/"\s+"/g, '""').trim();
}

/** 翻不了返回 null */
function translateCore(core) {
    var hit = dict[core];
    if (hit !== undefined) return hit;
    for (var i = 0; i < PATTERNS.length; i++) {
        if (NEED[i] && core.indexOf(NEED[i]) < 0) continue;
        if (PATTERNS[i][0].test(core)) return core.replace(PATTERNS[i][0], PATTERNS[i][1]);
    }
    // 「3 小时前 登录」「今天 13:29 登录」
    if (core.length > 2 && core.slice(-2) === '登录') {
        var at = core.slice(0, -2).trim(), when = t(at);
        if (when !== at) return 'Signed in ' + when.charAt(0).toLowerCase() + when.slice(1);
    }
    return byPhrases(core);
}

/** 翻译一段文字；没有词典（中文）或者没有中文时原样返回 */
export function t(s) {
    if (!dict || !s || !CJK.test(s)) return s;
    var core = s.trim();
    var out = cache.get(core);
    if (out === undefined) {
        out = translateCore(core);
        if (cache.size > 20000) cache.clear();
        cache.set(core, out);
    }
    if (out === null) return s;
    if (core.length === s.length) return out;
    return s.match(/^\s*/)[0] + out + s.match(/\s*$/)[0];
}

function skip(el) {
    var tag = el.tagName;
    return tag === 'SCRIPT' || tag === 'STYLE' || tag === 'TEXTAREA' || tag === 'CODE' || tag === 'PRE'
        || (el !== document.documentElement && el.getAttribute('translate') === 'no');
}

function translateAttrs(el) {
    for (var i = 0; i < ATTRS.length; i++) {
        var a = el.getAttribute(ATTRS[i]);
        if (a && CJK.test(a)) {
            var n = t(a);
            if (n !== a) el.setAttribute(ATTRS[i], n);
        }
    }
    if (el.tagName === 'INPUT' && (el.type === 'button' || el.type === 'submit') && CJK.test(el.value)) el.value = t(el.value);
}

// 同一个词当按钮是动作、当标签是状态：「启用」按钮是 Enable，状态是 Enabled
var VERBS = { '启用': 'Enable', '停用': 'Disable' };

function translateText(node) {
    var v = node.nodeValue;
    if (!v || !CJK.test(v) || given.has(node)) return;
    var p = node.parentNode;
    if (p && p.nodeType === 1 && (skip(p) || (p.closest && p.closest('[translate=no]:not(html)')))) return;
    var verb = VERBS[v.trim()];
    var act = verb && p && p.closest && p.closest('button, .btn, .link-btn, option, [role=menuitem], .opt') && !p.closest('[data-f]');
    var n = act ? v.replace(v.trim(), verb) : t(v);
    if (n === v) return;
    var now = Date.now(), seen = churn.get(node);
    if (seen && seen.out === n && now - seen.at < 3000) {
        if (++seen.n > 4) { given.add(node); return; }
    } else churn.set(node, { out: n, n: 1, at: now });
    node.nodeValue = n;
}

export function translateTree(root) {
    if (!dict || !root) return;
    if (root.nodeType === 3) {
        translateText(root);
        return;
    }
    if (root.nodeType !== 1 && root.nodeType !== 9 && root.nodeType !== 11) return;
    if (root.nodeType === 1) {
        if (skip(root)) return;
        translateAttrs(root);
    }
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, {
        acceptNode: function (n) { return n.nodeType === 1 && skip(n) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT; }
    });
    for (var n = walker.nextNode(); n; n = walker.nextNode()) {
        if (n.nodeType === 3) translateText(n);
        else translateAttrs(n);
    }
}

var observer = null;

function watch() {
    if (observer || !window.MutationObserver) return;
    observer = new MutationObserver(function (records) {
        for (var i = 0; i < records.length; i++) {
            var r = records[i];
            if (r.type === 'childList') {
                for (var j = 0; j < r.addedNodes.length; j++) translateTree(r.addedNodes[j]);
            } else if (r.type === 'characterData') {
                translateText(r.target);
            } else if (r.type === 'attributes' && r.target.nodeType === 1) {
                translateAttrs(r.target);
            }
        }
    });
    observer.observe(document.documentElement, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ATTRS });
}

/** 选了 English 时加载词典、翻译现有内容并盯住之后的变化；返回加载完成的 Promise */
export function start() {
    if (lang !== 'en') return Promise.resolve();
    var html = document.documentElement;
    html.setAttribute('lang', 'en');
    // 不让浏览器再翻一遍
    html.setAttribute('translate', 'no');
    html.classList.add('notranslate');
    if (!document.querySelector('meta[name=google]')) {
        var meta = document.createElement('meta');
        meta.name = 'google';
        meta.content = 'notranslate';
        document.head.appendChild(meta);
    }
    return import('./en.js').then(function (mod) {
        dict = mod.default;
        translateTree(document.documentElement);
        if (CJK.test(document.title)) document.title = t(document.title);
        watch();
    }).catch(function () { /* 词典加载失败就显示中文 */ });
}
