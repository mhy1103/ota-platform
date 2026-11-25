// 安装引导：分步填写，最后一次提交；提交后服务会重启，前端轮询到装好为止
export default function () {
    'use strict';
    var root = document.getElementById('ins');
    if (!root) return;
    var form = root.querySelector('.ins-form');
    var cfg = {};
    try { cfg = JSON.parse(root.getAttribute('data-config') || '{}') || {}; } catch (e) { cfg = {}; }
    var reinstall = root.getAttribute('data-reinstall') === 'true';
    // 浏览器地址栏里的才是真的：反向代理（宝塔 Nginx 默认 Host $host）会把端口去掉，服务端看到的 Host 不带端口
    var host = location.host || root.getAttribute('data-host');
    var https = location.protocol === 'https:' || root.getAttribute('data-https') === 'true';
    var STEPS = ['欢迎', '数据库', '访问域名', '存储与高级', '管理员', '确认安装'];
    var step = 0, dbState = null, publicEdited = false;
    var $ = function (s, el) { return (el || root).querySelector(s); };
    var $$ = function (s, el) { return Array.prototype.slice.call((el || root).querySelectorAll(s)); };
    var field = function (name) { return form.elements[name]; };
    var val = function (name) { var f = field(name); return f ? (f.type === 'checkbox' ? f.checked : String(f.value).trim()) : ''; };
    var radio = function (name) { var c = form.querySelector('input[name="' + name + '"]:checked'); return c ? c.value : ''; };
    var esc = function (s) { return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); };

    form.addEventListener('submit', function (e) { e.preventDefault(); });

    function api(path, body) {
        return fetch(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
            body: JSON.stringify(body || {})
        }).then(function (r) {
            return r.json().catch(function () { return {}; }).then(function (j) {
                if (!r.ok) { var err = new Error(j.message || j.msg || ('请求失败（HTTP ' + r.status + '）')); err.code = j.error; throw err; }
                return j;
            });
        });
    }

    // ---------- 预填 ----------
    (function prefill() {
        var db = cfg.db || {};
        // 新装默认 MySQL；重新安装时沿用原来的数据库类型
        var dbType = reinstall && db.type === 'h2' ? 'h2' : 'mysql';
        form.querySelector('input[name=dbType][value=' + dbType + ']').checked = true;
        if (db.host) field('dbHost').value = db.host;
        if (db.port) field('dbPort').value = db.port;
        if (db.name) field('dbName').value = db.name;
        if (db.username) field('dbUser').value = db.username;
        passHint();
        field('restrictDomains').checked = !!cfg.restrictDomains;
        if (cfg.publicUrl) { field('publicUrl').value = cfg.publicUrl; publicEdited = reinstall; }
        if (cfg.displayZone) field('zone').value = cfg.displayZone;
        var st = cfg.storage || {};
        if (st.type === 's3') field('storageType')[1].checked = true;
        field('s3Endpoint').value = st.endpoint || '';
        field('s3Bucket').value = st.bucket || '';
        field('s3Region').value = st.region || '';
        field('s3Access').value = st.accessKey || '';
        if (st.hasSecret) field('s3Secret').placeholder = '已保存，不改就留空';
        field('s3Prefix').value = st.prefix == null ? 'ota/' : st.prefix;
        field('s3Public').value = st.publicUrl || '';
        field('s3PathStyle').checked = !!st.pathStyle;
        if (reinstall) {
            field('adminUser').value = '';
            field('adminUser').placeholder = '不修改就留空';
            $('[data-admin-lead]').textContent = '保留数据时可以不填，现有管理员不动。填了就新建这个超级管理员，或者重置同名账号的密码。清空数据、换数据库时必须填。';
        }
        var list = cfg.domains && cfg.domains.length ? cfg.domains : [{ host: host, admin: true, api: true, https: https }];
        list.forEach(function (d) { addDomain(d); });
        if (!field('publicUrl').value) autoPublic();
        sync();
    })();

    // ---------- 步骤 ----------
    function go(n, back) {
        if (n < 0 || n >= STEPS.length) return;
        step = n;
        $$('.ins-panel').forEach(function (p) {
            var on = +p.getAttribute('data-panel') === n;
            p.hidden = !on;
            if (on) { p.classList.toggle('back', !!back); p.style.animation = 'none'; void p.offsetWidth; p.style.animation = ''; }
        });
        $$('.ins-steps li').forEach(function (li) {
            var i = +li.getAttribute('data-step');
            li.classList.toggle('on', i === n);
            li.classList.toggle('done', i < n);
        });
        $('[data-prev]').hidden = n === 0;
        var next = $('[data-next]');
        next.innerHTML = (n === 0 ? '开始' : n === STEPS.length - 1 ? (reinstall ? '保存并重启' : '开始安装') : '下一步') + '<svg><use href="#arrow"/></svg>';
        $('[data-count]').textContent = '第 ' + (n + 1) + ' 步，共 ' + STEPS.length + ' 步';
        $('[data-mobile-step]').textContent = (n + 1) + ' / ' + STEPS.length + ' · ' + STEPS[n];
        $('.ins-mobile-bar b').style.width = ((n + 1) / STEPS.length * 100) + '%';
        if (n === STEPS.length - 1) summary();
        hideError();
        form.scrollTop = 0;
        window.scrollTo(0, 0);
    }
    function showError(msg) {
        var box = $('[data-error]');
        box.querySelector('span').textContent = msg;
        box.hidden = false;
        box.style.animation = 'none';
        void box.offsetWidth;
        box.style.animation = '';
        box.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
    function hideError() { $('[data-error]').hidden = true; }
    function invalid(name, msg) {
        var f = field(name);
        if (f && f.classList) { f.classList.add('invalid'); f.focus(); }
        showError(msg);
        return false;
    }
    form.addEventListener('input', function (e) {
        if (e.target.classList) e.target.classList.remove('invalid');
        if (/^db/.test(e.target.name || '')) resetDb();
        if (e.target.name === 'dbHost') passHint();
        if (e.target.name === 'publicUrl') publicEdited = true;
        if (e.target.classList.contains('host')) autoPublic();
        if (e.target.name === 'adminPass') meter();
    });
    form.addEventListener('change', function (e) {
        if (e.target.name === 'dbType') resetDb();
        if (e.target.matches('[data-role]')) autoPublic();
        sync();
    });
    function sync() {
        $$('[data-when]').forEach(function (el) {
            var w = el.getAttribute('data-when');
            el.hidden = !(radio('dbType') === w || radio('storageType') === w || radio('dataMode') === w);
        });
    }

    // ---------- 环境检查 ----------
    api('/install/check').then(function (r) {
        var html = r.items.map(function (it) {
            return '<li class="' + (it.ok ? '' : 'bad') + '"><i>' + (it.ok ? '<svg><use href="#tick"/></svg>' : '!') + '</i><b>' + esc(it.name) + '</b><span>' + esc(it.value)
                + (it.warn ? '<small>' + esc(it.warn) + '</small>' : '') + '</span></li>';
        }).join('');
        $('[data-env]').innerHTML = html;
        var s = $('[data-env-status]');
        var bad = r.items.filter(function (i) { return !i.ok; }).length;
        s.textContent = bad ? bad + ' 项需要注意' : '全部正常';
        s.className = 'ins-pill ' + (bad ? 'warn' : 'ok');
    }, function (e) {
        $('[data-env]').innerHTML = '<li class="bad"><i>!</i><span>' + esc(e.message) + '</span></li>';
    });

    // ---------- 数据库 ----------
    function dbForm() {
        return { type: radio('dbType'), host: val('dbHost'), port: parseInt(val('dbPort'), 10) || 3306, name: val('dbName'), username: val('dbUser'), password: field('dbPass').value };
    }
    // 保存的密码只给原来那台数据库服务器用，换了地址要重新填
    function passHint() {
        var db = cfg.db || {};
        if (!db.hasPassword) return;
        field('dbPass').placeholder = val('dbHost').toLowerCase() === String(db.host || '').trim().toLowerCase() ? '已保存，不改就留空' : '换了数据库地址，要重新填写密码';
    }
    function resetDb() {
        dbState = null;
        $('[data-db-result]').hidden = true;
        $('[data-existing]').hidden = true;
    }
    function testDb() {
        var btn = $('[data-test-db]');
        btn.classList.add('loading');
        return api('/install/db', dbForm()).then(function (r) {
            dbState = r;
            var box = $('[data-db-result]');
            box.className = 'ins-result ' + (r.ok ? 'ok' : 'bad');
            box.innerHTML = '<svg><use href="#' + (r.ok ? 'tick' : 'alert') + '"/></svg><span>' + esc(r.message) + '</span>';
            box.hidden = false;
            $('[data-existing]').hidden = !(r.ok && r.hasData);
            if (r.ok && r.hasData) $('[data-db-summary]').textContent = r.summary;
            sync();
            return r;
        }, function (e) {
            dbState = { ok: false, message: e.message };
            var box = $('[data-db-result]');
            box.className = 'ins-result bad';
            box.innerHTML = '<svg><use href="#alert"/></svg><span>' + esc(e.message) + '</span>';
            box.hidden = false;
            return dbState;
        }).then(function (r) { btn.classList.remove('loading'); return r; });
    }
    $('[data-test-db]').addEventListener('click', testDb);

    // ---------- 域名 ----------
    function addDomain(d) {
        d = d || { host: '', admin: true, api: true, https: true };
        var row = document.createElement('div');
        row.className = 'ins-domain';
        row.innerHTML = '<input class="host" placeholder="例如 admin.example.com" spellcheck="false" autocomplete="off" value="' + esc(d.host || '') + '">'
            + '<span class="ins-roles">'
            + '<label class="ins-chip"><input type="checkbox" data-role="admin"' + (d.admin ? ' checked' : '') + '><span>后台</span></label>'
            + '<label class="ins-chip"><input type="checkbox" data-role="api"' + (d.api ? ' checked' : '') + '><span>接口</span></label>'
            + '<label class="ins-chip"><input type="checkbox" data-role="https"' + (d.https !== false ? ' checked' : '') + '><span>HTTPS</span></label>'
            + '</span><button type="button" class="ins-x" data-remove aria-label="删除这个域名"><svg><use href="#x"/></svg></button>';
        $('[data-domains]').appendChild(row);
        return row;
    }
    function domains() {
        return $$('.ins-domain').map(function (r) {
            return { host: r.querySelector('.host').value.trim().toLowerCase().replace(/^https?:\/\//, '').replace(/\/.*$/, ''),
                     admin: r.querySelector('[data-role=admin]').checked, api: r.querySelector('[data-role=api]').checked, https: r.querySelector('[data-role=https]').checked };
        }).filter(function (d) { return d.host; });
    }
    function autoPublic() {
        if (publicEdited) return;
        var api = domains().filter(function (d) { return d.api; })[0];
        field('publicUrl').value = api ? (api.https ? 'https://' : 'http://') + api.host : '';
    }
    $('[data-add-domain]').addEventListener('click', function () { addDomain({ host: '', admin: false, api: true, https: https }).querySelector('.host').focus(); });
    $('[data-domains]').addEventListener('click', function (e) {
        var x = e.target.closest('[data-remove]');
        if (!x) return;
        var row = x.closest('.ins-domain');
        row.style.opacity = '0';
        row.style.transform = 'translateX(8px)';
        row.style.transition = 'opacity .18s, transform .18s';
        setTimeout(function () { row.remove(); autoPublic(); }, 180);
    });

    // ---------- 密码强度 ----------
    function meter() {
        var p = field('adminPass').value, score = 0;
        if (p.length >= 8) score++;
        if (p.length >= 12) score++;
        if (/[a-z]/.test(p) && /[A-Z]/.test(p)) score++;
        if (/\d/.test(p)) score++;
        if (/[^A-Za-z0-9]/.test(p)) score++;
        var levels = [['0%', 'var(--err)', '至少 8 位，建议字母、数字、符号混合'], ['25%', 'var(--err)', '太弱'], ['50%', 'var(--warn)', '一般'],
                      ['75%', 'var(--warn)', '还行'], ['90%', 'var(--ok)', '强'], ['100%', 'var(--ok)', '很强']];
        var lv = p ? levels[Math.min(score, 5)] : levels[0];
        var b = $('[data-meter] b');
        b.style.width = lv[0];
        b.style.background = lv[1];
        $('[data-meter-text]').textContent = p && p.length < 8 ? '至少 8 位' : lv[2];
    }

    // ---------- 校验 ----------
    var HOST = /^(\*\.)?[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*(:\d{1,5})?$/i;
    function validate(n) {
        hideError();
        if (n === 1) {
            if (radio('dbType') === 'mysql') {
                if (!val('dbHost')) return Promise.resolve(invalid('dbHost', '请填写数据库地址'));
                if (!val('dbName')) return Promise.resolve(invalid('dbName', '请填写数据库名'));
                if (!val('dbUser')) return Promise.resolve(invalid('dbUser', '请填写数据库账号'));
            }
            var check = dbState ? Promise.resolve(dbState) : testDb();
            return check.then(function (r) {
                if (!r.ok) { showError(r.message || '数据库连不上'); return false; }
                if (r.hasData && radio('dataMode') === 'clear' && val('clearConfirm') !== '清空') return invalid('clearConfirm', '要清空数据，请在确认框里输入「清空」');
                return true;
            });
        }
        if (n === 2) {
            var list = domains(), bad = list.filter(function (d) { return !HOST.test(d.host); })[0];
            $$('.ins-domain .host').forEach(function (i) { i.classList.toggle('invalid', !!i.value.trim() && !HOST.test(i.value.trim().replace(/^https?:\/\//, '').replace(/\/.*$/, ''))); });
            if (bad) { showError('域名格式不对：' + bad.host); return Promise.resolve(false); }
            if (list.some(function (d) { return !d.admin && !d.api; })) { showError('每个域名至少要用于后台或接口其中一项'); return Promise.resolve(false); }
            if (val('restrictDomains')) {
                if (!list.some(function (d) { return d.admin; })) { showError('开了域名限制，至少要有一个后台域名'); return Promise.resolve(false); }
                if (!list.some(function (d) { return d.api; })) { showError('开了域名限制，至少要有一个接口域名，不然 App 访问不了'); return Promise.resolve(false); }
                var cur = location.hostname.toLowerCase();
                var local = cur === 'localhost' || cur === '127.0.0.1';
                var ok = list.some(function (d) {
                    if (!d.admin) return false;
                    var h = d.host.replace(/:\d+$/, '');
                    return h === cur || (h.indexOf('*.') === 0 && cur.slice(-(h.length - 1)) === h.slice(1));
                });
                if (!local && !ok) { showError('你现在用的 ' + cur + ' 不在后台域名里，保存后就打不开后台了。把它加进去，或者先别开域名限制'); return Promise.resolve(false); }
            }
            var pu = val('publicUrl');
            if (!/^https?:\/\/[^\s/]+(\/\S*)?$/i.test(pu)) return Promise.resolve(invalid('publicUrl', '接口对外地址要以 http:// 或 https:// 开头，比如 https://api.example.com'));
            return Promise.resolve(true);
        }
        if (n === 3) {
            if (radio('storageType') === 's3') {
                if (!val('s3Endpoint')) return Promise.resolve(invalid('s3Endpoint', '请填写对象存储的服务地址'));
                if (!val('s3Bucket')) return Promise.resolve(invalid('s3Bucket', '请填写存储桶'));
                if (!val('s3Access')) return Promise.resolve(invalid('s3Access', '请填写 Access Key'));
                if (!val('s3Secret') && !(cfg.storage && cfg.storage.hasSecret)) return Promise.resolve(invalid('s3Secret', '请填写 Secret Key'));
            }
            return Promise.resolve(true);
        }
        if (n === 4) {
            var needed = !reinstall || (dbState && (!dbState.current || (dbState.hasData && radio('dataMode') === 'clear'))) || val('adminUser') || field('adminPass').value;
            if (!needed) return Promise.resolve(true);
            if (!/^[A-Za-z0-9_.\-]{3,32}$/.test(val('adminUser'))) return Promise.resolve(invalid('adminUser', '登录账号要 3–32 位字母、数字或 _ . -'));
            var p = field('adminPass').value;
            if (p.length < 8) return Promise.resolve(invalid('adminPass', '密码至少 8 位'));
            if (p.length > 72) return Promise.resolve(invalid('adminPass', '密码最多 72 位'));
            if (p !== field('adminPass2').value) return Promise.resolve(invalid('adminPass2', '两次输入的密码不一致'));
            return Promise.resolve(true);
        }
        return Promise.resolve(true);
    }

    // ---------- 确认页 ----------
    function summary() {
        var db = dbForm(), rows = [];
        rows.push(['数据库', db.type === 'mysql' ? '<span class="mono">MySQL · ' + esc(db.username) + '@' + esc(db.host) + ':' + db.port + '/' + esc(db.name) + '</span>' : '内置数据库']);
        if (dbState && dbState.hasData) rows.push(['已有数据', radio('dataMode') === 'clear' ? '<span class="warn">清空：' + esc(dbState.summary) + '</span>' : '保留：' + esc(dbState.summary)]);
        var list = domains();
        rows.push(['访问域名', (val('restrictDomains') ? '只允许下面的域名' : '不限制（任何域名、IP 都能访问）') + '<br>' + (list.length ? list.map(function (d) {
            return '<span class="mono">' + esc(d.host) + '</span> ' + (d.admin ? '<span class="tag">后台</span>' : '') + (d.api ? '<span class="tag">接口</span>' : '');
        }).join('<br>') : '—')]);
        rows.push(['接口对外地址', '<span class="mono">' + esc(val('publicUrl')) + '</span>']);
        rows.push(['文件存储', radio('storageType') === 's3' ? '对象存储 · <span class="mono">' + esc(val('s3Bucket')) + '</span> @ ' + esc(val('s3Endpoint')) : '本机存储']);
        rows.push(['时区', esc(field('zone').options[field('zone').selectedIndex].text)]);
        rows.push(['超级管理员', val('adminUser') ? '<span class="mono">' + esc(val('adminUser')) + '</span>' + (val('adminName') ? '（' + esc(val('adminName')) + '）' : '') : '不修改']);
        $('[data-summary]').innerHTML = rows.map(function (r) { return '<dt>' + r[0] + '</dt><dd' + (r[1].indexOf('class="warn"') >= 0 ? '' : '') + '>' + r[1] + '</dd>'; }).join('');
    }

    // ---------- 提交和等待重启 ----------
    var STEP_OF = { DB_FAILED: 1, DATA_MODE: 1, CLEAR_CONFIRM: 1, BAD_DOMAIN: 2, LOCKOUT: 2, BAD_PUBLIC_URL: 2, BAD_STORAGE: 3, BAD_ZONE: 3, BAD_ADMIN: 4 };
    function submit() {
        var next = $('[data-next]');
        next.classList.add('loading');
        var body = {
            db: dbForm(), dataMode: radio('dataMode') || 'keep', clearConfirm: val('clearConfirm'),
            restrictDomains: val('restrictDomains'), domains: domains(), publicUrl: val('publicUrl'), displayZone: field('zone').value,
            storage: radio('storageType') === 's3' ? { type: 's3', endpoint: val('s3Endpoint'), region: val('s3Region'), bucket: val('s3Bucket'), accessKey: val('s3Access'),
                secretKey: field('s3Secret').value, pathStyle: val('s3PathStyle'), prefix: field('s3Prefix').value.trim(), publicUrl: val('s3Public') } : { type: 'local' },
            adminUsername: val('adminUser'), adminName: val('adminName'), adminPassword: field('adminPass').value, adminPassword2: field('adminPass2').value
        };
        api('/install/submit', body).then(function (j) {
            next.classList.remove('loading');
            run(j.boot);
        }, function (e) {
            next.classList.remove('loading');
            if (STEP_OF[e.code] != null && STEP_OF[e.code] !== step) go(STEP_OF[e.code], true);
            showError(e.message);
        });
    }
    // boot：提交时服务这次启动的时间，轮询到它变了就是重启好了（服务停下的那一下可能没轮询到）。
    // 每次请求最多等 5 秒，卡住的请求不会让页面一直停着；等久了给出排查办法，但一直接着等，服务起来了自动提示
    function run(boot) {
        var box = document.querySelector('[data-run]');
        box.hidden = false;
        var title = document.querySelector('[data-run-title]'), text = document.querySelector('[data-run-text]'), link = document.querySelector('[data-run-done]');
        var mark = function (name, cls) {
            document.querySelectorAll('[data-run-step]').forEach(function (li) {
                if (li.getAttribute('data-run-step') === name) { li.classList.remove('on', 'bad'); li.classList.add(cls); }
            });
        };
        var on = function (name) {
            var li = document.querySelector('[data-run-step="' + name + '"]');
            if (!li.classList.contains('done')) li.classList.add('on');
        };
        on('save');
        setTimeout(function () { mark('save', 'done'); on('restart'); }, 600);
        var started = Date.now(), wentDown = false, finished = false;
        var clock = setInterval(function () {
            var s = Math.round((Date.now() - started) / 1000);
            var waited = '已等待 ' + (s < 60 ? s + ' 秒' : Math.floor(s / 60) + ' 分 ' + (s % 60) + ' 秒');
            if (s >= 120) {
                if (link.hidden) {
                    mark('ready', 'bad');
                    title.textContent = '服务重启得有点慢';
                    link.innerHTML = '打开登录页看看';
                    link.classList.remove('primary');
                    link.hidden = false;
                }
                text.textContent = '配置已经保存，还在接着等（' + waited + '）。一直不好的话，看看服务器日志里有没有「重启失败」，或者手动重启服务（宝塔里重启这个 Java 项目）';
            } else {
                text.textContent = (s >= 40 ? '比平时慢一点，再等等' : '请不要关闭页面') + '（' + waited + '）';
            }
        }, 1000);
        (function poll() {
            if (finished) return;
            var ctl = window.AbortController ? new AbortController() : null;
            var timer = ctl ? setTimeout(function () { ctl.abort(); }, 5000) : null;
            fetch('/install/status', { cache: 'no-store', headers: { 'X-Requested-With': 'XMLHttpRequest' }, signal: ctl ? ctl.signal : undefined })
                .then(function (r) { return r.json(); })
                .then(function (s) {
                    clearTimeout(timer);
                    var back = boot && s.boot ? s.boot !== boot : wentDown || Date.now() - started > 15000;
                    if (back && s.installed && !s.restarting) return done();
                    setTimeout(poll, 800);
                }, function () {
                    clearTimeout(timer);
                    if (!wentDown) { wentDown = true; mark('restart', 'done'); on('ready'); }
                    setTimeout(poll, 1000);
                });
        })();
        function done() {
            finished = true;
            clearInterval(clock);
            mark('restart', 'done');
            mark('ready', 'done');
            box.classList.add('done');
            title.textContent = reinstall ? '已保存，服务已重启' : '安装完成';
            text.textContent = '用' + (val('adminUser') ? '刚才设置的' : '原来的') + '管理员账号登录后台';
            link.innerHTML = '进入后台<svg><use href="#arrow"/></svg>';
            link.classList.add('primary');
            link.hidden = false;
            link.focus();
        }
    }

    $('[data-next]').addEventListener('click', function () {
        var n = step;
        validate(n).then(function (ok) {
            if (!ok) return;
            if (n === STEPS.length - 1) submit(); else go(n + 1);
        });
    });
    $('[data-prev]').addEventListener('click', function () { go(step - 1, true); });
    form.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && e.target.tagName === 'INPUT' && !e.isComposing) { e.preventDefault(); $('[data-next]').click(); }
    });
    go(0);
}
