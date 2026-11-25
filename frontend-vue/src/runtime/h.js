// 模板里的 $h：生成的代码只通过它取值、调用后台的工具方法（@ui、@access、#lists……）
import {
    JMap, JNum, JTime, JEnum, str, text, attrValue, attrKeep, truthy, eq, lt, le, gt, ge, elvis, dflt,
    add, sub, mul, mod, neg, div, tdiv, double, formatFixed, halfEven, escQuery, escSegment, formEncode
} from './java.js';

// ---------- 这次渲染的上下文（服务端给的时区、当前时间、权限） ----------

var ctx = { zone: 'Asia/Shanghai', now: Date.now(), access: {}, param: {} };
var nowBase = { server: Date.now(), local: Date.now() };

export function setContext(c) {
    ctx = c || ctx;
    nowBase = { server: ctx.now || Date.now(), local: Date.now() };
}

function now() {
    return nowBase.server + (Date.now() - nowBase.local);
}

/** 口令算在次数上限里的：代理看到的（seenCount，扣量后的），没有就是实际用的 */
function limitUsed(cd) {
    var seen = p(cd, 'seenCount');
    return +(seen === null || seen === undefined ? p(cd, 'usedCount') || 0 : seen);
}

// ---------- 取值 ----------

function isPage(o) {
    return o !== null && typeof o === 'object' && Array.isArray(o.content) && 'totalPages' in o;
}

function entries(m) {
    var out = [];
    m.forEach(function (value, key) { out.push({ key: key, value: value }); });
    return out;
}

/** a.b：Map 按键取，其他取属性 */
function p(o, k) {
    if (o === null || o === undefined) return undefined;
    if (o instanceof JMap) return o.get(k);
    return o[k];
}

/** a.b()：没有参数的方法 */
function z(o, name) {
    if (o === null || o === undefined) return undefined;
    if (typeof o === 'string') {
        switch (name) {
            case 'length': return o.length;
            case 'isEmpty': return o.length === 0;
            case 'isBlank': return o.trim().length === 0;
            case 'trim': case 'strip': return o.trim();
            case 'toLowerCase': return o.toLowerCase();
            case 'toUpperCase': return o.toUpperCase();
            case 'toString': case 'name': return o;
        }
        return undefined;
    }
    if (Array.isArray(o)) {
        switch (name) {
            case 'size': return o.length;
            case 'isEmpty': return o.length === 0;
            case 'getFirst': return o[0];
            case 'getLast': return o[o.length - 1];
            case 'toString': return str(o);
        }
        return undefined;
    }
    if (o instanceof JMap) {
        switch (name) {
            case 'size': return o.size;
            case 'isEmpty': return o.size === 0;
            case 'keySet': return Array.from(o.keys());
            case 'values': return Array.from(o.values());
            case 'entrySet': return entries(o);
            case 'toString': return str(o);
        }
        return undefined;
    }
    if (o instanceof JNum) {
        if (name === 'intValue' || name === 'longValue') return Math.trunc(o.v);
        if (name === 'doubleValue') return o;
        if (name === 'toString') return o.toString();
    }
    if (o instanceof JEnum && (name === 'name' || name === 'toString')) return name === 'name' ? o.$e : o.toString();
    if (o instanceof JTime && name === 'toString') return o.s;
    if (typeof o !== 'object') return name === 'toString' ? str(o) : undefined;
    if (isPage(o)) {
        switch (name) {
            case 'hasNext': return o.number + 1 < o.totalPages;
            case 'hasPrevious': return o.number > 0;
            case 'hasContent': return o.content.length > 0;
            case 'getContent': return o.content;
            case 'isFirst': return o.first;
            case 'isLast': return o.last;
            case 'getTotalPages': return o.totalPages;
            case 'getTotalElements': return o.totalElements;
            case 'getNumber': return o.number;
            case 'getSize': return o.size;
        }
    }
    if (name in o) return o[name];
    var g = /^(get|is)([A-Z].*)$/.exec(name);
    if (g) {
        var prop = g[2].charAt(0).toLowerCase() + g[2].slice(1);
        if (prop in o) return o[prop];
    }
    if (name === 'toString') return str(o);
    return undefined;
}

/** a.b(x, y)：有参数的方法，只支持模板里用到的这些 */
function c(o, name, a) {
    if (o === null || o === undefined) return undefined;
    switch (name) {
        case 'get':
            if (o instanceof JMap) return o.get(a[0]);
            if (Array.isArray(o)) return o[+a[0]];
            return o[a[0]];
        case 'containsKey':
            if (o instanceof JMap) return o.has(a[0]);
            return Object.prototype.hasOwnProperty.call(o, String(a[0]));
        case 'getOrDefault':
            if (o instanceof JMap) return o.has(a[0]) ? o.get(a[0]) : a[1];
            return Object.prototype.hasOwnProperty.call(o, String(a[0])) ? o[a[0]] : a[1];
        case 'contains':
            if (Array.isArray(o)) return o.some(function (x) { return eq(x, a[0]); });
            if (typeof o === 'string') return o.indexOf(str(a[0])) >= 0;
            if (o instanceof JMap) return Array.from(o.keys()).some(function (x) { return eq(x, a[0]); });
            return false;
        case 'equals': return eq(o, a[0]);
        case 'equalsIgnoreCase': return a[0] !== null && a[0] !== undefined && str(o).toLowerCase() === str(a[0]).toLowerCase();
        case 'startsWith': return str(o).startsWith(str(a[0]), a.length > 1 ? a[1] : 0);
        case 'endsWith': return str(o).endsWith(str(a[0]));
        case 'indexOf': return str(o).indexOf(str(a[0]), a.length > 1 ? a[1] : 0);
        case 'substring': return a.length > 1 ? str(o).substring(a[0], a[1]) : str(o).substring(a[0]);
        case 'charAt': return str(o).charAt(a[0]);
        case 'replace': return str(o).split(str(a[0])).join(str(a[1]));
        case 'concat': return str(o) + str(a[0]);
        case 'split': return str(o).split(new RegExp(a[0]));
        case 'matches': return new RegExp('^(?:' + a[0] + ')$').test(str(o));
        case 'compareTo': return lt(o, a[0]) ? -1 : gt(o, a[0]) ? 1 : 0;
    }
    if (typeof o[name] === 'function') return o[name].apply(o, a);
    throw new Error('模板里调用了没有实现的方法 ' + name);
}

/** a[k] */
function i(o, k) {
    if (o === null || o === undefined) return undefined;
    if (o instanceof JMap) return o.get(k);
    if (Array.isArray(o) || typeof o === 'string') return o[+k];
    return o[k];
}

/** th:each 的列表和状态变量（index、count、size、first、last、odd、even） */
function each(x) {
    var list;
    if (x === null || x === undefined) list = [];
    else if (Array.isArray(x)) list = x;
    else if (x instanceof JMap) list = entries(x);
    else if (isPage(x)) list = x.content;
    else list = [x];
    var n = list.length;
    return list.map(function (v, idx) {
        return [v, { index: idx, count: idx + 1, size: n, current: v, first: idx === 0, last: idx === n - 1,
            even: (idx + 1) % 2 === 0, odd: (idx + 1) % 2 === 1 }];
    });
}

function proj(o, fn) {
    if (o === null || o === undefined) return null;
    var list = o instanceof JMap ? entries(o) : Array.isArray(o) ? o : [o];
    return list.map(fn);
}

function sel(o, fn) {
    if (o === null || o === undefined) return null;
    var list = o instanceof JMap ? entries(o) : Array.isArray(o) ? o : [o];
    return list.filter(fn);
}

/** |...| 文字替换 */
function cat() {
    var out = '';
    for (var k = 0; k < arguments.length; k++) out += str(arguments[k]);
    return out;
}

/** @{/path/{id}(id=${x}, q=${y})} */
function link(path, params) {
    var pth = str(path), hash = '';
    var h = pth.indexOf('#');
    if (h >= 0) {
        hash = pth.slice(h);
        pth = pth.slice(0, h);
    }
    var q = [];
    params.forEach(function (kv) {
        var k = kv[0], v = kv[1], ph = '{' + k + '}';
        if (pth.indexOf(ph) >= 0) {
            pth = pth.split(ph).join(escSegment(v === null || v === undefined ? '' : str(v)));
            return;
        }
        if (v === null || v === undefined) {
            q.push(escQuery(k));
            return;
        }
        (Array.isArray(v) ? v : [v]).forEach(function (x) { q.push(escQuery(k) + '=' + escQuery(str(x))); });
    });
    return pth + (q.length ? (pth.indexOf('?') >= 0 ? '&' : '?') + q.join('&') : '') + hash;
}

// ---------- 时间（和 AdminUi 一样：LocalDateTime 已经在服务端换成时间点，按展示时区格式化） ----------

var fmts = new Map();
function parts(ms) {
    var zone = ctx.zone || 'Asia/Shanghai';
    var f = fmts.get(zone);
    if (!f) {
        f = new Intl.DateTimeFormat('en-US', { timeZone: zone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', second: '2-digit' });
        fmts.set(zone, f);
    }
    var o = {};
    f.formatToParts(new Date(ms)).forEach(function (x) { o[x.type] = x.value; });
    var y = String(o.year);
    while (y.length < 4) y = '0' + y;
    return { y: y, M: o.month, d: o.day, H: o.hour === '24' ? '00' : o.hour, m: o.minute, s: o.second };
}

function local(t) {
    return t instanceof JTime ? parts(t.ms) : null;
}

function dayOf(z) {
    return z.y + '-' + z.M + '-' + z.d;
}

function prevDay(day) {
    var d = new Date(Date.UTC(+day.slice(0, 4), +day.slice(5, 7) - 1, +day.slice(8, 10)) - 86400000);
    var y = String(d.getUTCFullYear());
    while (y.length < 4) y = '0' + y;
    return y + '-' + String(d.getUTCMonth() + 1).padStart(2, '0') + '-' + String(d.getUTCDate()).padStart(2, '0');
}

var ACTION_LABELS = {
    SWITCH: '切换模式', UPLOAD: '上传更新包', ADD_URL: '添加外部链接', ACTIVATE: '发布版本', ROLLBACK: '回滚', DELETE: '删除版本',
    CODE_CREATE: '新建口令', CODE_TOGGLE: '启停口令', CODE_DELETE: '删除口令', CODE_SUBMIT: '提交口令', CONFIRMED: '激活确认', CONFIRM_FAST: '确认太快', CODE_DUP: '重复激活',
    CODE_UPDATE: '修改口令', AGENT_CREATE: '新建代理', AGENT_UPDATE: '修改代理', AGENT_TOGGLE: '启停代理', AGENT_DELETE: '删除代理',
    AGENT_REFRESH: '更新代理数据', AGENT_LOGIN: '代理登录', SIGNING_UPDATE: '更新签名', SETTINGS_APPEARANCE: '修改外观',
    SUB_AGENT_CREATE: '新建二级代理', SUB_AGENT_UPDATE: '修改二级代理', SUB_AGENT_TOGGLE: '启停二级代理', SUB_AGENT_DELETE: '删除二级代理',
    SUB_AGENT_CODES: '分配二级代理口令', SUB_AGENT_REFRESH: '更新二级代理数据',
    SCREEN_SHARE_CREATE: '分享数据大屏', SCREEN_SHARE_REVOKE: '取消大屏分享',
    SUB_NOTICE_CREATE: '给二级代理发公告', SUB_NOTICE_UPDATE: '修改二级代理公告', SUB_NOTICE_DELETE: '删除二级代理公告',
    SETTINGS_ACCOUNT: '修改管理员', LOG_CLEAR: '清空日志', VERIFY: '校验版本', APP_CREATE: '新建 App', APP_UPDATE: '修改 App',
    CHANNEL_CREATE: '新建渠道', TOKEN_CREATE: '新建令牌', TOKEN_REVOKE: '撤销令牌', WEBHOOK_UPDATE: '修改 Webhook',
    APP_BACKUP: '下载备份', APP_RESTORE: '恢复备份', APP_DELETE: '删除 App', CHANNEL_DELETE: '删除渠道', RELEASE_UPDATE: '修改版本',
    STORAGE_CLEAN: '清理存储', CODE_EXPORT: '导出口令', RECORD_EXPORT: '导出激活记录', ADMIN_LOGIN: '管理员登录',
    ADMIN_CREATE: '新建管理员', ADMIN_UPDATE: '修改管理员', ADMIN_DELETE: '删除管理员', ACCOUNT_UPDATE: '修改账号',
    TESTER_SAVE: '测试设备', TESTER_DELETE: '移除测试设备', NOTIFY_UPDATE: '修改群机器人', LOGIN_LOCKED: '登录锁定',
    LOGIN_2FA_LOCKED: '两步验证锁定', LOGIN_RECOVERY: '恢复码登录', TOTP_ENABLE: '开启两步验证', TOTP_DISABLE: '关闭两步验证',
    TOTP_RECOVERY: '重新生成恢复码', TOTP_RESET: '重置两步验证', SESSION_REVOKE: '下线登录设备', SECURITY_SETTINGS: '修改登录安全',
    LOG_EXPORT: '导出日志', INSTALL_REOPEN: '重开安装引导', DOMAINS_UPDATE: '修改访问域名', STORAGE_SWITCH: '切换文件存储',
    INSTALLER_UPLOAD: '上传安装包', INSTALLER_DELETE: '删除安装包', CODE_REQUEST: '代理申请口令', CODE_REQUEST_APPROVE: '同意口令申请',
    CODE_REQUEST_REJECT: '拒绝口令申请', CODE_REQUEST_CANCEL: '撤回口令申请', AGENT_PASSWORD: '代理改密码', AGENT_EXPORT: '代理导出记录', DEVICE_EXPORT: '导出设备', AGENTS_EXPORT: '导出代理列表', AGENT_RECORDS_EXPORT: '导出代理激活记录',
    SCHEDULE: '计划任务', NOTICE_CREATE: '发布公告', NOTICE_UPDATE: '修改公告', NOTICE_DELETE: '删除公告',
    PROJECT_BACKUP: '项目备份', PROJECT_RESTORE: '项目恢复', DEMO_RESET: '恢复演示数据', SETTINGS_DEMO: '演示账号设置',
    RELEASE_GUARD: '自动回滚设置', METRICS_TOKEN: '监控令牌', ACTIVATION_PACKAGE: '口令更新包'
};
var SOURCE_LABELS = { upload: '上传', cli: '命令行', external: '外部链接', republish: '重新发布' };
var DEVICE_EVENTS = ['CODE_SUBMIT', 'CONFIRMED', 'CONFIRM_FAST', 'CODE_DUP'];

function query(kv) {
    var out = [];
    for (var k = 0; k + 1 < kv.length; k += 2) {
        var v = kv[k + 1];
        if (v === null || v === undefined) continue;
        var s = str(v).trim();
        if (s === '' || s === 'all' || (kv[k] === 'page' && s === '1')) continue;
        out.push(kv[k] + '=' + formEncode(s));
    }
    return out.join('&');
}

function size(bytes) {
    if (bytes === null || bytes === undefined) return '—';
    var b = Math.trunc(+bytes);
    if (b < 1024) return b + ' B';
    if (b < 1048576) return formatFixed(b / 1024, 1) + ' KB';
    if (b < 1073741824) return formatFixed(b / 1048576, 1) + ' MB';
    if (b < 1099511627776) return formatFixed(b / 1073741824, 1) + ' GB';
    return formatFixed(b / 1099511627776, 1) + ' TB';
}

var ui = {
    dt: function (t) {
        var z = local(t);
        if (!z) return '—';
        var cur = parts(now());
        return (z.y === cur.y ? '' : z.y + '-') + z.M + '-' + z.d + ' ' + z.H + ':' + z.m;
    },
    dts: function (t) {
        var z = local(t);
        if (!z) return '—';
        var cur = parts(now());
        return (z.y === cur.y ? '' : z.y + '-') + z.M + '-' + z.d + ' ' + z.H + ':' + z.m + ':' + z.s;
    },
    full: function (t) {
        var z = local(t);
        return z ? z.y + '-' + z.M + '-' + z.d + ' ' + z.H + ':' + z.m + ':' + z.s : '';
    },
    date: function (t) {
        var z = local(t);
        return z ? dayOf(z) : '';
    },
    ago: function (t) {
        var z = local(t);
        if (!z) return '—';
        var n = now();
        var sec = Math.floor((n - t.ms) / 1000);
        if (sec < 60) return '刚刚';
        if (sec < 3600) return Math.floor(sec / 60) + ' 分钟前';
        var day = dayOf(z), today = dayOf(parts(n));
        if (day === today) return '今天 ' + z.H + ':' + z.m;
        if (day === prevDay(today)) return '昨天 ' + z.H + ':' + z.m;
        return ui.dt(t);
    },
    until: function (t) {
        if (!(t instanceof JTime)) return '—';
        var sec = Math.floor((t.ms - now()) / 1000);
        if (sec < 60) return '马上';
        if (sec < 3600) return Math.ceil(sec / 60) + ' 分钟后';
        if (sec < 86400) {
            var h = Math.floor(sec / 3600), m = Math.floor(sec % 3600 / 60);
            return h + ' 小时' + (m ? ' ' + m + ' 分' : '') + '后';
        }
        return Math.floor(sec / 86400) + ' 天后';
    },
    hm: function (t) {
        var z = local(t);
        return z ? z.H + ':' + z.m : '';
    },
    // 计划任务的日期块：今天、明天，或者 11-15
    dayLabel: function (t) {
        var z = local(t);
        if (!z) return '';
        var day = dayOf(z), today = dayOf(parts(now()));
        if (day === today) return '今天';
        if (prevDay(day) === today) return '明天';
        return z.M + '-' + z.d;
    },
    weekday: function (t) {
        var z = local(t);
        if (!z) return '';
        return '周' + '日一二三四五六'.charAt(new Date(Date.UTC(+z.y, +z.M - 1, +z.d)).getUTCDay());
    },
    today: function () {
        return dayOf(parts(now()));
    },
    zoneId: function () {
        return ctx.zone;
    },
    minutes: function (m) {
        m = +m;
        if (m % 1440 === 0) return (m / 1440) + ' 天';
        if (m % 60 === 0) return (m / 60) + ' 小时';
        return m + ' 分钟';
    },
    action: function (code) {
        return code === null || code === undefined ? '—' : (ACTION_LABELS[code] || str(code));
    },
    tone: function (code) {
        if (code === null || code === undefined) return '';
        code = str(code);
        var ends = function (s) { return code.slice(-s.length) === s; };
        if (code === 'DELETE' || ends('_DELETE') || code === 'ROLLBACK' || code === 'TOKEN_REVOKE' || code === 'LOG_CLEAR'
            || code === 'STORAGE_CLEAN') return 'tone-danger';
        if (code === 'SWITCH' || code === 'LOGIN_LOCKED' || code === 'LOGIN_2FA_LOCKED' || code === 'SIGNING_UPDATE'
            || code === 'APP_RESTORE' || code === 'TOTP_DISABLE' || code === 'TOTP_RESET' || code === 'STORAGE_SWITCH'
            || code === 'CODE_REQUEST_REJECT' || code === 'CONFIRM_FAST' || code === 'CODE_DUP') return 'tone-warn';
        if (code === 'LOGIN_RECOVERY') return '';
        if (code === 'ACTIVATE' || code === 'UPLOAD' || code === 'ADD_URL' || code === 'CONFIRMED'
            || code === 'CODE_REQUEST_APPROVE') return 'tone-ok';
        if (ends('_LOGIN') || code === 'CODE_SUBMIT' || code === 'VERIFY') return '';
        return 'tone-info';
    },
    deviceEvent: function (code) {
        return DEVICE_EVENTS.indexOf(str(code)) >= 0;
    },
    source: function (s) {
        return s === null || s === undefined ? '—' : (SOURCE_LABELS[s] || str(s));
    },
    codeStatus: function (cd) {
        if (p(cd, 'enabled') !== true) return '停用';
        var exp = p(cd, 'expiresAt');
        if (exp && now() > exp.ms) return '已过期';
        var max = +(p(cd, 'maxUses') || 0);
        if (max > 0 && limitUsed(cd) >= max) return '已用完';
        return '可用';
    },
    percent: function (cd) {
        var max = +(p(cd, 'maxUses') || 0);
        if (max <= 0) return 0;
        return Math.min(100, Math.trunc(limitUsed(cd) * 100 / max));
    },
    /** 选项列表里按 id 找名字（[{id, name}]），找不到是 null */
    optName: function (list, id) {
        var arr = list && list.toArray ? list.toArray() : list;
        if (!arr || id === null || id === undefined) return null;
        for (var i = 0; i < arr.length; i++) {
            var o = arr[i];
            if (o && String(o.id) === String(id)) return o.name;
        }
        return null;
    },
    /** 和服务端 Percent.of 一样：没到全部不写 100（99.7% 写 99），有一点就不写 0 */
    pctOf: function (part, total) {
        var t = +(total || 0), p = +(part || 0);
        if (t <= 0) return 0;
        var v = Math.round(p * 100 / t);
        if (v >= 100 && p < t) return 99;
        if (v <= 0 && p > 0) return 1;
        return Math.max(0, Math.min(100, v));
    },
    host: function (url) {
        if (url === null || url === undefined) return url;
        var s = str(url).trim();
        var m = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\/([^/?#]*)/.exec(s);
        if (!m) return url;
        // 端口保留（默认端口 80 / 443 去掉），和地址栏里看到的一样
        var auth = m[1].replace(/^.*@/, '').replace(/:$/, '');
        var scheme = s.slice(0, s.indexOf(':')).toLowerCase();
        if ((scheme === 'http' && /:80$/.test(auth)) || (scheme === 'https' && /:443$/.test(auth))) auth = auth.replace(/:\d+$/, '');
        return auth || url;
    },
    size: size,
    bytes: function (b) {
        if (b === null || b === undefined) return '—';
        var n = Math.trunc(+b);
        if (n >= 1073741824) return formatFixed(n / 1073741824, 2) + ' GB';
        return size(n);
    },
    dataMode: function (mode) {
        if (str(mode) === 'DAILY') return '每日 0 点';
        if (str(mode) === 'MANUAL') return '手动更新';
        return '实时';
    },
    maskIp: function (ip) {
        if (ip === null || ip === undefined || str(ip).trim() === '') return '—';
        ip = str(ip);
        var dot = ip.lastIndexOf('.');
        if (dot > 0 && ip.indexOf(':') < 0) return ip.slice(0, dot) + '.*';
        var segs = ip.split(':');
        return segs.length > 2 ? segs[0] + ':' + segs[1] + ':*' : '*';
    },
    shortId: function (id) {
        if (id === null || id === undefined) return '—';
        id = str(id);
        return id.length > 14 ? id.slice(0, 8) + '…' + id.slice(-4) : id;
    },
    maskDevice: function (id) {
        if (id === null || id === undefined || str(id) === '') return '—';
        id = str(id);
        if (id.length <= 4) return id.charAt(0) + '***';
        var keep = Math.max(1, Math.min(4, Math.floor(id.length / 4)));
        return id.slice(0, keep) + '****' + id.slice(-keep);
    },
    rid: function (id) {
        if (id === null || id === undefined) return '—';
        id = str(id);
        return id.length > 8 ? id.slice(0, 8) : id;
    },
    url: function (path) {
        var q = query(Array.prototype.slice.call(arguments, 1));
        return q ? path + '?' + q : path;
    },
    query: function () {
        return query(Array.prototype.slice.call(arguments));
    },
    page: function (base, page) {
        if (+page <= 1) return base;
        return base + (str(base).indexOf('?') >= 0 ? '&' : '?') + 'page=' + page;
    },
    pages: function (current, total) {
        current = +current;
        total = +total;
        var out = [1];
        var from = Math.max(2, current - 2), to = Math.min(total - 1, current + 2);
        if (from > 2) out.push(0);
        for (var n = from; n <= to; n++) out.push(n);
        if (to < total - 1) out.push(0);
        if (total > 1) out.push(total);
        return out;
    }
};

// ---------- 权限（@access）：服务端算好放在上下文里 ----------

var access = {
    me: function () { return ctx.access && ctx.access.me || null; },
    owner: function () { return !!(ctx.access && ctx.access.owner); },
    agents: function () { return !!(ctx.access && ctx.access.agents); },
    guide: function () { return ctx.access && ctx.access.guide || ''; },
    can: function (perm) { return !!(ctx.access && ctx.access.perms && ctx.access.perms[str(perm).toLowerCase()]); },
    canOn: function (appId, perm) {
        var a = ctx.access || {};
        if (a.owner) return true;
        var g = a.grants && a.grants.get ? a.grants.get(appId) : null;
        return !!(g && g.indexOf(str(perm).toUpperCase()) >= 0);
    }
};

var agentPortal = {
    confirmedAsOf: function (r, cutoff) {
        if (p(r, 'confirmed') !== true) return false;
        var at = p(r, 'confirmedAt');
        return cutoff === null || cutoff === undefined || at === null || at === undefined || !(+at > +cutoff);
    }
};

// ---------- Thymeleaf 的工具对象 ----------

function size0(x) {
    if (x === null || x === undefined) return 0;
    if (x instanceof JMap) return x.size;
    return x.length;
}

var lists = {
    isEmpty: function (x) { return size0(x) === 0; },
    size: size0,
    contains: function (x, v) { return Array.isArray(x) && x.some(function (y) { return eq(y, v); }); }
};

var strings = {
    isEmpty: function (s) { return s === null || s === undefined || str(s).trim().length === 0; },
    substring: function (s, a, b) { return s === null || s === undefined ? null : (b === undefined ? str(s).substring(a) : str(s).substring(a, b)); },
    listJoin: function (list, sep) { return list === null || list === undefined ? null : list.map(str).join(str(sep)); },
    toLowerCase: function (s) { return s === null || s === undefined ? null : str(s).toLowerCase(); },
    toUpperCase: function (s) { return s === null || s === undefined ? null : str(s).toUpperCase(); },
    startsWith: function (s, x) { return s === null || s === undefined ? null : str(s).startsWith(str(x)); },
    endsWith: function (s, x) { return s === null || s === undefined ? null : str(s).endsWith(str(x)); },
    contains: function (s, x) { return s === null || s === undefined ? null : str(s).indexOf(str(x)) >= 0; },
    replace: function (s, a, b) { return s === null || s === undefined ? null : str(s).split(str(a)).join(str(b)); },
    length: function (s) { return s === null || s === undefined ? 0 : str(s).length; },
    trim: function (s) { return s === null || s === undefined ? null : str(s).trim(); },
    defaultString: function (s, d) { return s === null || s === undefined ? d : s; },
    abbreviate: function (s, max) {
        if (s === null || s === undefined) return null;
        s = str(s);
        return s.length <= max ? s : s.substring(0, max - 3) + '...';
    }
};

function group(intPart) {
    return intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

var numbers = {
    formatInteger: function (n, minDigits, point) {
        if (n === null || n === undefined) return null;
        var s = halfEven(+n, 0);
        var negative = s.charAt(0) === '-';
        if (negative) s = s.slice(1);
        while (s.length < minDigits) s = '0' + s;
        if (point === 'COMMA') s = group(s);
        else if (point === 'POINT') s = group(s).replace(/,/g, '.');
        else if (point === 'WHITESPACE') s = group(s).replace(/,/g, ' ');
        return (negative ? '-' : '') + s;
    },
    formatDecimal: function (n, minInt, decimals) {
        if (n === null || n === undefined) return null;
        var s = halfEven(+n, decimals);
        var negative = s.charAt(0) === '-';
        if (negative) s = s.slice(1);
        var dot = s.indexOf('.');
        var ip = dot < 0 ? s : s.slice(0, dot), fp = dot < 0 ? '' : s.slice(dot);
        while (ip.length < minInt) ip = '0' + ip;
        return (negative ? '-' : '') + ip + fp;
    }
};

var $h = {
    t: truthy, s: text, av: attrValue, ak: attrKeep, eq: eq, lt: lt, le: le, gt: gt, ge: ge, elvis: elvis, dflt: dflt,
    add: add, sub: sub, mul: mul, mod: mod, neg: neg, div: div, tdiv: tdiv, d: double,
    p: p, z: z, c: c, i: i, each: each, proj: proj, sel: sel, cat: cat, link: link,
    ui: ui, access: access, agentPortal: agentPortal, lists: lists, strings: strings, numbers: numbers
};

export default $h;
