// 后台入口。页面数据从同一个地址带 X-OTA-Data 头取回来（服务端只发页面上用到的字段），用 views 里的组件渲染；
// 弹窗、引导、标签栏这些交互在 behaviors/ui.js，表单提交、切页、前进后退在这里。
import { createApp, h, shallowRef, nextTick } from 'vue';
import $h, { setContext } from './runtime/h.js';
import { start as i18nStart, current as langNow, setLang } from './i18n/index.js';
import { revive } from './runtime/java.js';
import { views as pubViews } from './views/pub.js';
import loginInit from './behaviors/login.js';
import errorInit from './behaviors/error.js';
import installInit from './behaviors/install.js';
import shareInit from './behaviors/share.js';
import uiCss from './css/ui.css?url';
import loginCss from './css/login.css?url';
import installCss from './css/install.css?url';
import errorCss from './css/error.css?url';
import shareCss from './css/share.css?url';

var INITS = { 'pub/login.js': loginInit, 'pub/error.js': errorInit, 'pub/install.js': installInit, 'pub/share.js': shareInit };
var STYLES = { 'app/ui.css': uiCss, 'pub/login.css': loginCss, 'pub/install.css': installCss, 'pub/error.css': errorCss, 'pub/share.css': shareCss };
var PREFETCH_FRESH_MS = 20 * 1000;
var root = document.documentElement;
var current = shallowRef(null);
var appViews = null, behaviors = null, family = null;
var renderSeq = 0, navSeq = 0, navKey = '';
var scrolls = new Map(), prefetched = new Map(), styles = new Map();
var themeEl = null;

var Root = {
    setup: function () {
        return function () {
            var s = current.value;
            return s ? h(s.entry.component, { m: s.data.model, c: s.data.ctx, key: s.seq }) : null;
        };
    }
};

// ---------- 加载壳（服务端 VueUi 给的）：第一次渲染好之前显示 ----------

var boot = document.getElementById('ota-boot');
var bootBar = boot && boot.querySelector('.ob-bar i');
var bootMsg = boot && boot.querySelector('.ob-msg');
var revealed = false;

function bootProgress(p) {
    if (!bootBar || revealed) return;
    boot.classList.add('p');
    bootBar.style.width = Math.round(Math.max(0.04, Math.min(1, p)) * 100) + '%';
}

function reveal() {
    if (revealed) return;
    bootProgress(1);
    revealed = true;
    if (!boot) return;
    setTimeout(function () {
        boot.classList.add('out');
        setTimeout(function () { if (boot.parentNode) boot.parentNode.removeChild(boot); }, 450);
    }, 120);
}

function fail(text) {
    if (!bootMsg || revealed) return;
    boot.classList.remove('p');
    bootMsg.innerHTML = '';
    bootMsg.appendChild(document.createTextNode(text));
    var b = document.createElement('button');
    b.type = 'button';
    b.textContent = '重试';
    b.addEventListener('click', function () { location.reload(); });
    bootMsg.appendChild(b);
}

// ---------- 取页面数据 ----------

/** quiet：后台自己发的请求（定时刷新看板、预取），连着 /app/live 时从那里走，失败了再用 fetch */
function request(url, init) {
    init = init || {};
    var get = (init.method || 'GET') === 'GET';
    var tunneled = init.quiet && get ? live.get(url, 'page') : null;
    var sent = tunneled ? tunneled.catch(function () { return fetchPage(url, init); }) : fetchPage(url, init);
    return sent.then(function (res) {
        noteBuild(res.headers.get('X-OTA-Build'));
        return res;
    });
}

function fetchPage(url, init) {
    return fetch(url, {
        method: init.method || 'GET',
        body: init.body,
        credentials: 'same-origin',
        headers: { 'X-OTA-Data': '1', 'Accept': 'text/html' }
    });
}

// ---------- 后台请求不从页面发 ----------
// 安卓上的 UC、QQ、夸克、华为、小米浏览器和微信里，页面每发一个 fetch，地址栏下面都会走一遍加载进度。
// 定时查新版本、待处理、看板刷新这些后台请求，优先走一条 WebSocket（/app/live，服务端带着这个页面的 Cookie 在本机代为请求，
// 登录状态和权限跟直接请求完全一样）；连不上时交给 Worker 去发。两样都不会让地址栏走进度，也不用改服务器配置
var live = (function () {
    var ws = null, ready = false, seq = 0, waits = {}, retry = 0, timer = 0, fails = 0, worker = null, workerBroken = false;
    function open() {
        clearTimeout(timer);
        if (ws || fails >= 3 || !window.WebSocket || document.hidden || family !== 'app') return;
        var opened = false;
        try {
            ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/app/live');
        } catch (e) {
            ws = null;
            fails = 3;
            return;
        }
        ws.onopen = function () {
            opened = true;
            ready = true;
            retry = 0;
            fails = 0;
        };
        ws.onmessage = function (e) {
            var m;
            try { m = JSON.parse(e.data); } catch (err) { return; }
            if (m.t === 'hi') noteBuild(m.b);
            else if (m.t === 'r' && waits[m.i]) {
                var w = waits[m.i];
                delete waits[m.i];
                w.done(m);
            }
        };
        ws.onclose = function () {
            ws = null;
            ready = false;
            Object.keys(waits).forEach(function (id) {
                if (waits[id].via !== 'ws') return;
                var w = waits[id];
                delete waits[id];
                w.done(null);
            });
            if (!opened) fails++;
            if (fails < 3 && !document.hidden) timer = setTimeout(open, Math.min(30000, 1000 * Math.pow(2, retry++)));
        };
    }
    function useWorker() {
        if (worker || workerBroken || !window.Worker) return worker;
        try {
            worker = new Worker(new URL('./live-worker.js', import.meta.url));
        } catch (e) {
            workerBroken = true;
            return null;
        }
        worker.onmessage = function (e) {
            var m = e.data || {};
            var w = waits[m.i];
            if (!w) return;
            delete waits[m.i];
            w.done(m);
        };
        worker.onerror = function () {
            workerBroken = true;
            worker = null;
            Object.keys(waits).forEach(function (id) {
                if (waits[id].via !== 'worker') return;
                var w = waits[id];
                delete waits[id];
                w.done(null);
            });
        };
        return worker;
    }
    /** 返回 null 表示两条路都走不了，调用的地方照常用 fetch */
    function get(path, kind) {
        var via = ready && ws && ws.readyState === 1 ? 'ws' : useWorker() ? 'worker' : null;
        if (!via) return null;
        return new Promise(function (resolve, reject) {
            var id = ++seq;
            var t = setTimeout(function () {
                if (!waits[id]) return;
                delete waits[id];
                reject(new Error('live timeout'));
            }, 20000);
            waits[id] = {
                via: via,
                done: function (m) {
                    clearTimeout(t);
                    if (m && m.s >= 200 && m.s < 599) resolve(response(m));
                    else reject(new Error('live failed'));
                }
            };
            try {
                if (via === 'ws') ws.send(JSON.stringify({ i: id, u: path, k: kind }));
                else worker.postMessage({ i: id, u: path, k: kind });
            } catch (e) {
                delete waits[id];
                clearTimeout(t);
                reject(e);
            }
        });
    }
    function response(m) {
        var empty = m.s === 204 || m.s === 205 || m.s === 304;
        var r = new Response(empty ? null : (m.d || ''), { status: m.s, headers: { 'Content-Type': m.ct || 'text/plain', 'X-OTA-Build': m.b || '' } });
        try { Object.defineProperty(r, 'url', { value: location.origin + (m.u || '/') }); } catch (e) {}
        return r;
    }
    document.addEventListener('visibilitychange', function () { if (!document.hidden) open(); });
    return { open: open, get: get, ready: function () { return ready; } };
})();

// ---------- 新版本：服务端换了 jar，开着的页面提示重新加载 ----------
// 页面数据响应头带着版本号。连着 /app/live 时，服务端重启后重新连上会先发版本号过来，不用再问；
// 没连上时页面在前台每 10 秒问一次。管理后台每 30 秒顺便更新待处理

var build = null, updating = false;

function noteBuild(b) {
    if (!b) return;
    if (build === null) build = b;
    else if (b !== build) promptUpdate();
}

var todosAt = 0;
function pulse() {
    if (document.hidden || updating || navigator.onLine === false) return;
    var todos = family === 'app' && !!document.querySelector('.sidebar[data-page]') && Date.now() - todosAt > 29000;
    if (!todos && live.ready()) return;
    if (todos) todosAt = Date.now();
    var path = todos ? '/admin/pulse' : '/app/version';
    var tunneled = live.get(path, 'json');
    var sent = tunneled ? tunneled.catch(function () { return null; }) : fetch(path, { credentials: 'same-origin', cache: 'no-store', priority: 'low',
        headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' } });
    sent.then(function (r) { return r && r.ok && isJson(r) ? r.json() : null; })
        .then(function (d) {
            if (!d) return;
            noteBuild(d.build);
            if (todos && d.todo && behaviors && behaviors.todos) behaviors.todos(d.todo, d.requests);
            if (todos) window.dispatchEvent(new CustomEvent('ota:pulse', { detail: d }));
        })
        .catch(function () {});
}
setInterval(pulse, 10000);
document.addEventListener('visibilitychange', function () { if (!document.hidden) pulse(); });

var UPDATE_CSS = [
    '.ota-update{margin:auto;width:min(440px,calc(100vw - 32px));max-width:none;padding:0;border:0;border-radius:20px;overflow:hidden;background:var(--panel,#fff);color:var(--ink,#18181b);',
    'box-shadow:0 0 0 1px color-mix(in srgb,var(--ink,#000) 8%,transparent),0 40px 80px -24px rgba(0,0,0,.45),0 12px 28px -14px rgba(0,0,0,.2);',
    'font:14px/1.6 -apple-system,BlinkMacSystemFont,"SF Pro Text","PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;-webkit-font-smoothing:antialiased}',
    '.ota-update::backdrop{background:rgba(9,9,11,.45);-webkit-backdrop-filter:blur(6px) saturate(1.2);backdrop-filter:blur(6px) saturate(1.2)}',
    '.ota-update[open]{animation:ou-in .5s cubic-bezier(.16,1,.3,1)}',
    '@keyframes ou-in{from{opacity:0;transform:translateY(18px) scale(.96)}}',
    '@keyframes ou-spin{to{transform:rotate(360deg)}}',
    '@keyframes ou-ping{0%{transform:scale(.6);opacity:.7}80%,100%{transform:scale(1.9);opacity:0}}',
    '@keyframes ou-drift{to{transform:translate(60px,34px) scale(1.15)}}',
    '@keyframes ou-pop{from{opacity:0;transform:scale(.6)}}',
    '.ou-hero{position:relative;height:168px;display:grid;place-items:center;overflow:hidden;background:#0c0c0f;isolation:isolate}',
    '.ou-blob{position:absolute;width:240px;height:240px;border-radius:50%;filter:blur(46px);z-index:-1}',
    '.ou-blob.a{left:-50px;top:-120px;background:var(--accent,#4f46e5);opacity:.6;animation:ou-drift 12s ease-in-out infinite alternate}',
    '.ou-blob.b{right:-70px;bottom:-140px;background:color-mix(in srgb,var(--accent,#4f46e5) 55%,#7c8cff);opacity:.42;animation:ou-drift 14s ease-in-out infinite alternate-reverse}',
    '.ou-grid{position:absolute;inset:0;z-index:-1;background-image:radial-gradient(rgba(255,255,255,.15) 1px,transparent 1px);background-size:16px 16px;',
    '-webkit-mask-image:radial-gradient(circle at 50% 50%,#000,transparent 68%);mask-image:radial-gradient(circle at 50% 50%,#000,transparent 68%)}',
    '.ou-mark{position:relative;width:60px;height:60px;border-radius:18px;display:grid;place-items:center;color:#fff;animation:ou-pop .7s cubic-bezier(.34,1.56,.64,1) .08s both;',
    'background:linear-gradient(155deg,color-mix(in srgb,var(--accent,#4f46e5) 82%,#fff),var(--accent,#4f46e5) 55%,color-mix(in srgb,var(--accent,#4f46e5) 70%,#000));',
    'box-shadow:inset 0 1px 0 rgba(255,255,255,.35),inset 0 -1px 0 rgba(0,0,0,.2),0 16px 32px -10px color-mix(in srgb,var(--accent,#4f46e5) 75%,transparent),0 0 0 1px rgba(255,255,255,.08)}',
    '.ou-orbit{position:absolute;inset:-22px;border-radius:50%;border:1px solid rgba(255,255,255,.16);animation:ou-spin 12s linear infinite;pointer-events:none}',
    '.ou-orbit::after{content:"";position:absolute;top:50%;right:-3px;width:6px;height:6px;margin-top:-3px;border-radius:50%;background:#fff;',
    'box-shadow:0 0 12px 2px color-mix(in srgb,var(--accent,#4f46e5) 80%,#fff)}',
    '.ou-orbit.o2{inset:-40px;border-style:dashed;border-color:rgba(255,255,255,.09);animation-duration:26s;animation-direction:reverse}',
    '.ou-orbit.o2::after{width:4px;height:4px;margin-top:-2px;right:-2px;opacity:.7}',
    '.ou-icon{width:28px;height:28px;fill:none;stroke:currentColor;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round;filter:drop-shadow(0 2px 4px rgba(0,0,0,.18))}',
    '.ou-body{padding:20px 24px 0}',
    '.ou-kicker{display:inline-flex;align-items:center;gap:8px;font-size:12px;font-weight:600;letter-spacing:.02em;color:var(--accent,#4f46e5)}',
    '.ou-kicker i{position:relative;width:6px;height:6px;border-radius:50%;background:currentColor}',
    '.ou-kicker i::after{content:"";position:absolute;inset:-3px;border-radius:50%;border:1.5px solid currentColor;animation:ou-ping 1.8s ease-out infinite}',
    '.ou-title{display:block;margin-top:4px;font-size:19px;font-weight:700;letter-spacing:-.022em;line-height:1.35}',
    '.ou-text{margin:6px 0 0;color:var(--ink-2,#52525b);font-size:13.5px;line-height:1.7}',
    '.ou-meta{display:grid;grid-template-columns:1fr 1fr;margin-top:16px;border:1px solid var(--line,#e4e4e7);border-radius:12px;background:var(--panel-2,#fafafa);overflow:hidden}',
    '.ou-meta>span{display:flex;flex-direction:column;gap:2px;min-width:0;padding:9px 14px}',
    '.ou-meta>span+span{border-left:1px solid var(--line,#e4e4e7)}',
    '.ou-meta small{font-size:11.5px;color:var(--ink-3,#71717a)}',
    '.ou-meta b,.ou-meta code{font-size:13px;font-weight:600;color:var(--ink,#18181b);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-variant-numeric:tabular-nums}',
    '.ou-meta code{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-weight:560;letter-spacing:.02em}',
    '.ou-note{display:flex;gap:8px;align-items:flex-start;margin:12px 24px 0;padding:10px 12px;border-radius:12px;font-size:12.5px;line-height:1.55;',
    'background:color-mix(in srgb,var(--warn,#d97706) 10%,transparent);color:color-mix(in srgb,var(--warn,#b45309) 85%,var(--ink,#000))}',
    '.ou-note svg{flex-shrink:0;width:15px;height:15px;margin-top:2px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}',
    '.ou-foot{display:flex;align-items:center;gap:8px;margin-top:18px;padding:14px 24px 20px;border-top:1px solid var(--line-2,var(--line,#ececef))}',
    '.ou-foot button{display:inline-flex;align-items:center;justify-content:center;gap:8px;height:38px;border-radius:10px;font:inherit;font-size:13.5px;font-weight:600;cursor:pointer;',
    'transition:transform .15s,background-color .15s,color .15s,filter .15s}',
    '.ou-foot button:active{transform:scale(.98)}',
    '.ou-foot button:focus-visible,.ota-update-pill button:focus-visible{outline:2px solid var(--accent,#4f46e5);outline-offset:2px}',
    '.ou-later{padding:0 12px;border:0;background:transparent;color:var(--ink-3,#71717a)}',
    '.ou-later:hover{color:var(--ink,#18181b);background:var(--hover,#f4f4f5)}',
    '.ou-reload{margin-left:auto;padding:0 16px;border:0;background:var(--accent,#4f46e5);color:var(--on-accent,#fff);',
    'box-shadow:0 10px 20px -10px color-mix(in srgb,var(--accent,#4f46e5) 80%,transparent),inset 0 1px 0 rgba(255,255,255,.18)}',
    '.ou-reload:hover{filter:brightness(1.07)}',
    '.ou-reload kbd{padding:1px 6px;border-radius:5px;font:11px/1.4 inherit;background:rgba(255,255,255,.2);color:inherit}',
    '.ou-reload .ou-busy{display:none;width:15px;height:15px;border-radius:50%;border:2px solid currentColor;border-right-color:transparent;animation:ou-spin .7s linear infinite}',
    '.ou-reload.busy .ou-busy{display:inline-block}.ou-reload.busy kbd{display:none}',
    '.ota-update-pill{position:fixed;right:20px;bottom:20px;z-index:300;display:flex;align-items:center;gap:10px;height:46px;padding:0 6px 0 16px;border-radius:999px;',
    'border:1px solid var(--line,#e4e4e7);background:color-mix(in srgb,var(--panel,#fff) 88%,transparent);-webkit-backdrop-filter:blur(14px) saturate(1.4);backdrop-filter:blur(14px) saturate(1.4);',
    'color:var(--ink,#18181b);box-shadow:0 18px 40px -16px rgba(0,0,0,.35),0 2px 6px -2px rgba(0,0,0,.08);',
    'font:13px/1 -apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif;animation:ou-in .46s cubic-bezier(.16,1,.3,1)}',
    '.ota-update-pill .ou-dot{position:relative;width:8px;height:8px;flex-shrink:0;border-radius:50%;background:var(--accent,#4f46e5)}',
    '.ota-update-pill .ou-dot::after{content:"";position:absolute;inset:-4px;border-radius:50%;border:2px solid var(--accent,#4f46e5);animation:ou-ping 1.8s ease-out infinite}',
    '.ota-update-pill b{font-weight:600;white-space:nowrap}',
    '.ota-update-pill button{height:34px;padding:0 14px;border-radius:999px;border:0;font:inherit;font-weight:600;cursor:pointer;white-space:nowrap}',
    '.ota-update-pill .ou-go{background:var(--accent,#4f46e5);color:var(--on-accent,#fff)}',
    '.ota-update-pill .ou-x{width:30px;padding:0;background:transparent;color:var(--ink-3,#71717a);font-size:16px;line-height:1}',
    '.ota-update-pill .ou-x:hover{color:var(--ink,#18181b)}',
    '@media (max-width:560px){.ota-update{margin:auto auto 0;width:100vw;border-radius:20px 20px 0 0;padding-bottom:env(safe-area-inset-bottom)}',
    '.ota-update[open]{animation-name:ou-up}@keyframes ou-up{from{transform:translateY(100%)}}',
    '.ou-hero{height:150px}.ou-orbit.o2{inset:-36px}.ou-body{padding:18px 20px 0}.ou-note{margin:12px 20px 0}',
    '.ou-foot{padding:12px 20px 18px}.ou-reload kbd{display:none}',
    '.ota-update-pill{left:12px;right:12px;bottom:calc(12px + env(safe-area-inset-bottom));justify-content:space-between}}',
    'html[data-motion=off] .ota-update[open],html[data-motion=off] .ou-blob,html[data-motion=off] .ou-orbit,html[data-motion=off] .ou-mark,',
    'html[data-motion=off] .ou-kicker i::after,html[data-motion=off] .ota-update-pill,html[data-motion=off] .ota-update-pill .ou-dot::after{animation:none!important}'
].join('');

var UPDATE_ICON = '<svg class="ou-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12a9 9 0 0 1-15.36 6.36L3 16"/><path d="M3 12a9 9 0 0 1 15.36-6.36L21 8"/>'
    + '<polyline points="21 3 21 8 16 8"/><polyline points="3 21 3 16 8 16"/></svg>';

// 页面上有没保存的输入（改过的输入框、文本框、下拉框），提醒先保存
function unsaved() {
    var els = document.querySelectorAll('.content input, .content textarea, .content select, dialog[open] input, dialog[open] textarea, dialog[open] select');
    for (var i = 0; i < els.length; i++) {
        var el = els[i];
        if (el.type === 'hidden' || el.type === 'search' || el.disabled || el.closest('[data-no-dirty], .ota-update')) continue;
        if (el.type === 'checkbox' || el.type === 'radio') { if (el.checked !== el.defaultChecked && !el.closest('[data-check], [data-check-all], .check-col')) return true; }
        else if (el.tagName === 'SELECT') { for (var j = 0; j < el.options.length; j++) if (el.options[j].selected !== el.options[j].defaultSelected) return true; }
        else if (el.value !== el.defaultValue) return true;
    }
    return false;
}

function when(iso) {
    var d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    var pad = function (n) { return (n < 10 ? '0' : '') + n; };
    var hm = pad(d.getHours()) + ':' + pad(d.getMinutes()), now = new Date();
    if (d.toDateString() === now.toDateString()) return '今天 ' + hm;
    var y = new Date(now.getTime() - 86400000);
    if (d.toDateString() === y.toDateString()) return '昨天 ' + hm;
    return pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + hm;
}

function reloadNow(btn) {
    if (btn) btn.classList.add('busy');
    setTimeout(function () { location.reload(); }, 60);
}

function promptUpdate() {
    if (updating) return;
    updating = true;
    var style = document.createElement('style');
    style.textContent = UPDATE_CSS;
    document.head.appendChild(style);
    var d = document.createElement('dialog');
    d.className = 'ota-update';
    d.setAttribute('aria-labelledby', 'ou-title');
    d.innerHTML = '<div class="ou-hero"><span class="ou-blob a"></span><span class="ou-blob b"></span><span class="ou-grid"></span>'
        + '<span class="ou-mark"><span class="ou-orbit o2"></span><span class="ou-orbit"></span>' + UPDATE_ICON + '</span></div>'
        + '<div class="ou-body"><span class="ou-kicker"><i></i>有新版本</span><b class="ou-title" id="ou-title">后台已经更新</b>'
        + '<p class="ou-text">重新加载就能用上最新的页面和功能，只要一两秒。</p>'
        + '<div class="ou-meta" hidden><span><small>版本</small><code data-role="id"></code></span><span><small>发布于</small><b data-role="time"></b></span></div></div>'
        + (unsaved() ? '<div class="ou-note"><svg viewBox="0 0 24 24"><path d="M12 9v4M12 17h.01"/><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/></svg>'
            + '<span>页面上有还没保存的内容，先保存再重新加载，不然会丢。</span></div>' : '')
        + '<div class="ou-foot"><button type="button" class="ou-later">稍后再说</button>'
        + '<button type="button" class="ou-reload" autofocus><span class="ou-busy"></span>重新加载<kbd>↵</kbd></button></div>';
    document.body.appendChild(d);
    var reload = d.querySelector('.ou-reload');
    reload.addEventListener('click', function () { reloadNow(reload); });
    d.querySelector('.ou-later').addEventListener('click', function () {
        d.close();
        d.remove();
        var pill = document.createElement('div');
        pill.className = 'ota-update-pill';
        pill.setAttribute('role', 'status');
        pill.innerHTML = '<i class="ou-dot"></i><b>有新版本</b><button type="button" class="ou-go">重新加载</button>'
            + '<button type="button" class="ou-x" aria-label="关闭" title="关闭">×</button>';
        pill.querySelector('.ou-go').addEventListener('click', function () { reloadNow(null); });
        pill.querySelector('.ou-x').addEventListener('click', function () { pill.remove(); });
        document.body.appendChild(pill);
    });
    d.addEventListener('cancel', function (e) { e.preventDefault(); d.querySelector('.ou-later').click(); });
    if (d.showModal) d.showModal(); else d.setAttribute('open', '');
    reload.focus();
    fetch('/app/version', { cache: 'no-store', headers: { 'Accept': 'application/json' } })
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (v) {
            if (!v || !v.build) return;
            var meta = d.querySelector('.ou-meta');
            d.querySelector('[data-role=id]').textContent = v.build.slice(0, 4) + ' ' + v.build.slice(4, 8);
            d.querySelector('[data-role=time]').textContent = when(v.time) || '刚刚';
            meta.hidden = false;
        })
        .catch(function () {});
}

function parse(res) {
    return res.text().then(function (text) { return JSON.parse(text, revive); });
}

function isJson(res) {
    return (res.headers.get('Content-Type') || '').indexOf('application/json') >= 0;
}

function entryOf(view) {
    if (pubViews[view]) return Promise.resolve(pubViews[view]);
    if (appViews) return Promise.resolve(appViews[view] || null);
    return import('./views/app.js').then(function (mod) {
        appViews = mod.views;
        return appViews[view] || null;
    });
}

function familyOf(entry) {
    return entry.meta.js.indexOf('app/ui.js') >= 0 ? 'app' : 'pub';
}

function progressStart(wait) {
    if (behaviors) behaviors.progress.start(wait);
}

function progressDone() {
    if (behaviors) behaviors.progress.done();
}

// 改了数据以后，鼠标停留时预取的页面数据作废
function markStale() {
    prefetched.clear();
}

/** 后台定时刷新的结果告诉页面（ota:live 事件）：数据大屏据此显示数据是什么时候的、连不上或者登录失效时提示 */
function liveNote(ok, now, login) {
    try {
        window.dispatchEvent(new CustomEvent('ota:live', { detail: { ok: ok, now: now || Date.now(), login: !!login } }));
    } catch (e) { /* 页面照常用 */ }
}

/** 响应处理：页面数据就渲染；下载就保存文件；别的（不是这个后台的页面）整页打开 */
function receive(res, opts) {
    var finalUrl = new URL(res.url || opts.url.href, location.href);
    // 后台定时刷新碰上服务重启、网关报错、防火墙拦截（回来的是网页不是页面数据）：这次跳过，不整页刷新
    if (opts.idle && (!res.ok || !isJson(res))) {
        liveNote(false, 0, finalUrl.pathname === '/login');
        return null;
    }
    if (!family && !opts.post && !isJson(res)) {
        fail('页面没有加载出来');
        return null;
    }
    // 带 attachment 的就是要保存的文件，不管类型（App 备份本身是 JSON）
    if (opts.post && /^\s*attachment/i.test(res.headers.get('Content-Disposition') || '')) return download(res, opts.form);
    if (!isJson(res)) {
        if (opts.post && !/text\/html/.test(res.headers.get('Content-Type') || '')) return download(res, opts.form);
        location.href = finalUrl.href;
        return null;
    }
    return parse(res).then(function (data) {
        if (!data || !data.view) {
            if (opts.post) return reply(data, opts.form);
            if (!family) {
                fail('页面没有加载出来');
                return null;
            }
            location.href = finalUrl.href;
            return null;
        }
        return show(data, finalUrl, opts);
    });
}

// 后台刷新数据没变就不重画，最多隔 5 分钟重画一次，让「几分钟前」跟上
var renderedAt = 0;
function unchanged(data) {
    var s = current.value;
    return !!s && s.data.view === data.view && Date.now() - renderedAt < 300000
        && JSON.stringify(s.data.model) === JSON.stringify(data.model);
}

function show(data, finalUrl, opts) {
    if (opts.idle) liveNote(true, data.ctx && data.ctx.now);
    if (opts.idle && unchanged(data)) return Promise.resolve(null);
    var same = finalUrl.pathname === opts.url.pathname && finalUrl.search === opts.url.search;
    var hash = same ? (opts.hash || '') : finalUrl.hash;
    // 跳转到页面里的某个标签（提交表单后、切换 App 后）：fetch 跟随跳转拿不到 #，服务端放在页面数据里带回来（RedirectHash）
    if (!hash && data.ctx && data.ctx.hash) hash = data.ctx.hash;
    var url = finalUrl.pathname + finalUrl.search + hash;
    return entryOf(data.view).then(function (entry) {
        if (!entry) {
            location.href = url;
            return null;
        }
        var fam = familyOf(entry);
        // 登录、退出、从后台到错误页：整页打开，后台脚本按新的登录状态重新初始化
        if (family && fam !== family) {
            location.href = url;
            return null;
        }
        // 看板类页面（meta.live，比如数据大屏）定时刷新：页面组件留着，只把新数据交给它。
        // 数字和图形从原来的样子过渡到新的，滚动位置、正在播的动画、鼠标停着的提示都不受影响
        if (opts.idle && entry.meta.live && current.value && current.value.entry === entry
            && navKey === finalUrl.pathname + finalUrl.search && navKey === location.pathname + location.search) {
            return patch(entry, data);
        }
        var ready = fam === 'app' && !behaviors ? import('./behaviors.js') : Promise.resolve(null);
        return Promise.all([ready, css(entry.meta.css.map(function (p) { return STYLES[p] || p; }))]).then(function (got) {
            return render(entry, data, url, opts, fam, got[0] && got[0].default);
        });
    });
}

// ---------- 渲染 ----------

var shownTitle = '';

/** 只换数据（见 show 里的 meta.live）：不重建页面组件，也不重新跑页面初始化 */
function patch(entry, data) {
    setContext(data.ctx);
    theme(entry.meta.theme ? { attr: entry.meta.theme.attr, css: entry.meta.theme.css(data.model, data.ctx, $h) } : null);
    renderedAt = Date.now();
    current.value = { entry: entry, data: data, seq: current.value.seq };
    return nextTick().then(function () {
        var title = entry.meta.title(data.model, data.ctx, $h);
        if (title !== shownTitle) {
            shownTitle = title;
            document.title = title;
        }
    });
}

function render(entry, data, url, opts, fam, bootBehaviors) {
    var first = !family;
    setContext(data.ctx);
    bootProgress(0.6);
    theme(entry.meta.theme ? { attr: entry.meta.theme.attr, css: entry.meta.theme.css(data.model, data.ctx, $h) } : null);
    var keepY = window.scrollY;
    var to = new URL(url, location.href), key = to.pathname + to.search;
    var again = !first && navKey === key;
    // 同一个页面换筛选、翻页、切 7/14/30 天、提交表单后回到本页：停在原来的位置、不播进场动画，看起来只是内容变了。
    // 点的是别的地址、只是跳回了本页（比如切换 App）不算
    var stay = !first && !opts.pop && to.pathname === location.pathname
        && (opts.post || !opts.url || opts.url.pathname === to.pathname);
    var soft = again || stay;
    if (behaviors) behaviors.leave();
    var oldNav = document.querySelector('.sidebar .side-scroll');
    var navY = oldNav ? oldNav.scrollTop : 0;
    var bar = document.getElementById('tabbar');
    if (bar) bar.remove();

    function apply() {
        document.body.className = '';
        renderedAt = Date.now();
        current.value = { entry: entry, data: data, seq: ++renderSeq };
        if (first) mountApp();
        return nextTick().then(function () {
            nativeTemplates();
            var top = document.querySelector('.main > .topbar');
            if (bar && top) top.after(bar);
            var newNav = document.querySelector('.sidebar .side-scroll');
            if (newNav) newNav.scrollTop = navY;
            shownTitle = entry.meta.title(data.model, data.ctx, $h);
            document.title = shownTitle;
            // 提交表单后回到的还是这个地址：不多记一条历史，不然要按两次返回
            if (opts.push && key !== location.pathname + location.search) history.pushState({ ota: 1 }, '', url);
            else if (!opts.pop) history.replaceState({ ota: 1 }, '', url);
            navKey = location.pathname + location.search;
            family = fam;
            root.classList.remove('nav-instant');
            if (fam === 'app') {
                if (!behaviors) behaviors = bootBehaviors(host);
                behaviors.mount({ refresh: soft });
                live.open();
            } else {
                entry.meta.js.forEach(function (js) { if (INITS[js]) INITS[js](); });
            }
            window.scrollTo(0, opts.scroll != null ? opts.scroll : (opts.keep || stay) ? keepY : 0);
            if (opts.reveal) revealCard(opts.reveal);
        });
    }

    // 同一页重新渲染（换筛选、提交表单后、后台刷新数据）不再播放进场动画，不然整页会闪一下
    root.classList.toggle('render-same', soft);
    return Promise.resolve(apply()).then(function () {
        if (first) reveal();
    });
}

/** 翻页后列表要从头看：卡片顶已经滚出屏幕时，滚回卡片顶（让开吸顶的顶栏和标签栏） */
function revealCard(selector) {
    var el = document.querySelector(selector);
    var card = el && (el.closest('.card') || el);
    if (!card) return;
    var cover = 0;
    Array.prototype.forEach.call(document.querySelectorAll('.topbar, #tabbar, .agent-top'), function (bar) {
        if (getComputedStyle(bar).position === 'sticky') cover = Math.max(cover, bar.getBoundingClientRect().bottom);
    });
    var top = card.getBoundingClientRect().top;
    if (top < cover) window.scrollTo(0, Math.max(0, window.scrollY + top - cover - 12));
}

function mountApp() {
    var app = createApp(Root);
    app.config.globalProperties.$h = $h;
    root.classList.remove('ob-wait');
    app.mount(document.body);
    if (boot && !revealed) document.body.appendChild(boot);
}

/** Vue 把 <template> 的内容渲染成子节点，浏览器解析的 HTML 里它们在 template.content 里：挪过去 */
function nativeTemplates() {
    var list = document.body.querySelectorAll('template');
    for (var i = 0; i < list.length; i++) {
        var t = list[i];
        while (t.firstChild) t.content.appendChild(t.firstChild);
    }
}

function css(hrefs) {
    var waits = [];
    hrefs.forEach(function (href) {
        if (styles.has(href)) return;
        var l = document.createElement('link');
        l.rel = 'stylesheet';
        l.href = href;
        waits.push(new Promise(function (r) {
            l.onload = l.onerror = r;
            setTimeout(r, 5000);
        }));
        document.head.insertBefore(l, themeEl);
        styles.set(href, l);
    });
    return Promise.all(waits).then(function () {
        styles.forEach(function (l, href) {
            if (hrefs.indexOf(href) < 0) {
                l.remove();
                styles.delete(href);
            }
        });
    });
}

function theme(t) {
    if (!t) {
        if (themeEl) themeEl.remove();
        themeEl = null;
        return;
    }
    if (!themeEl) {
        themeEl = document.createElement('style');
        document.head.appendChild(themeEl);
    }
    if (t.attr) themeEl.setAttribute('data-theme', '');
    else themeEl.removeAttribute('data-theme');
    var text = t.css == null ? '' : String(t.css);
    if (themeEl.textContent !== text) themeEl.textContent = text;
}

// ---------- 切页（后台脚本里的链接、筛选表单、标签栏都走这里） ----------

function go(href, opts) {
    opts = opts || {};
    var u = new URL(href, location.href), key = u.pathname + u.search;
    if (!opts.pop) scrolls.set(navKey, window.scrollY);
    if (u.pathname === '/admin/use') prefetched.clear();
    var seq = ++navSeq;
    var hit = opts.reload ? null : prefetched.get(key);
    var nav = { url: u, hash: u.hash, push: !!opts.push, keep: !!opts.keep, scroll: opts.scroll, pop: !!opts.pop, idle: !!opts.idle, reveal: opts.reveal };
    prefetched.delete(key);
    if (hit && Date.now() - hit.at < PREFETCH_FRESH_MS) {
        return show(hit.data, hit.url, nav);
    }
    if (!opts.silent) progressStart(opts.soft ? 500 : 0);
    return request(key, { quiet: !!opts.silent }).then(function (res) {
        if (seq !== navSeq) return null;
        return receive(res, nav);
    }).catch(function (err) {
        if (window.console) console.error(err);
        if (opts.idle) liveNote(false);
        if (seq === navSeq && !opts.silent) location.href = u.href;
    }).then(function () {
        if (seq === navSeq && !opts.silent) progressDone();
    });
}

function prefetch(href) {
    var u = new URL(href, location.href), key = u.pathname + u.search;
    var hit = prefetched.get(key);
    if (hit && Date.now() - hit.at < PREFETCH_FRESH_MS) return;
    request(key, { quiet: true }).then(function (res) {
        if (!res.ok || !isJson(res)) return null;
        var finalUrl = new URL(res.url, location.href);
        return parse(res).then(function (data) {
            if (data && data.view) prefetched.set(key, { data: data, url: finalUrl, at: Date.now() });
        });
    }).catch(function () {});
}

var host = {
    go: go,
    prefetch: prefetch,
    unused: function () {}
};

window.addEventListener('popstate', function () {
    if (!family) return;
    var key = location.pathname + location.search;
    if (key === navKey) return;
    if (family === 'app' && /^\/(admin|agent)(\/|$)/.test(location.pathname)) go(location.href, { pop: true, scroll: scrolls.get(key) || 0 });
    else location.reload();
});

// ---------- 提交表单：POST 用 fetch 发，跟随跳转拿到下一页的数据渲染；下载文件照常保存 ----------

function postable(f) {
    return (f.getAttribute('method') || 'get').toLowerCase() === 'post' && !f.hasAttribute('data-no-swap')
        && (!f.getAttribute('target') || f.getAttribute('target') === '_self');
}

function post(f, submitter) {
    var action = new URL(f.getAttribute('action') || location.href, location.href);
    var fd = new FormData(f);
    if (submitter && submitter.name) fd.append(submitter.name, submitter.value);
    var multipart = (f.getAttribute('enctype') || '').toLowerCase() === 'multipart/form-data';
    var seq = ++navSeq;
    scrolls.set(navKey, window.scrollY);
    markStale();
    progressStart(400);
    return request(action.pathname + action.search, { method: 'POST', body: multipart ? fd : new URLSearchParams(fd) }).then(function (res) {
        if (seq !== navSeq) return null;
        return receive(res, { url: action, push: true, post: true, form: f });
    }).catch(function (err) {
        if (window.console) console.error(err);
        if (seq !== navSeq) return;
        idle(f);
        if (behaviors) behaviors.toast('提交失败，请检查网络后重试', 'error');
    }).then(function () {
        if (seq === navSeq) progressDone();
    });
}

function idle(f) {
    if (!f) return;
    Array.prototype.forEach.call(f.querySelectorAll('.btn.loading'), function (b) { b.classList.remove('loading'); });
}

function download(res, f) {
    return res.blob().then(function (blob) {
        var cd = res.headers.get('Content-Disposition') || '';
        var m = /filename\*=UTF-8''([^;]+)/i.exec(cd) || /filename="?([^";]+)"?/i.exec(cd);
        var a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = m ? decodeURIComponent(m[1]) : 'download';
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(function () { URL.revokeObjectURL(a.href); }, 60000);
        idle(f);
        if (f) f.reset();
    });
}

function reply(data, f) {
    idle(f);
    var msg = data && (data.msg || data.message);
    if (msg && behaviors) behaviors.toast(msg, data.ok === false || data.error ? 'error' : 'ok');
}

window.addEventListener('submit', function (e) {
    var f = e.target;
    if (e.defaultPrevented || !family || !(f instanceof HTMLFormElement) || !postable(f)) return;
    e.preventDefault();
    post(f, e.submitter);
});

var nativeSubmit = HTMLFormElement.prototype.submit;
HTMLFormElement.prototype.submit = function () {
    if (family && postable(this)) {
        post(this, null);
        return;
    }
    // 没有 requestSubmit 的老浏览器（Safari 15）调 submit() 不会触发 submit 事件：补发一个，让筛选表单也走无刷新切页
    if (family && (this.getAttribute('method') || 'get').toLowerCase() === 'get'
        && !this.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))) return;
    nativeSubmit.call(this);
};

// ---------- 第一次打开 ----------

if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
bootProgress(0.08);
// 账号偏好：加载壳的 meta 里带着存过的值和保存地址
var prefsMeta = document.querySelector('meta[name=ota-prefs]');
var prefsQueue = null, prefsWait = null, prefsSent = Promise.resolve();
function savePrefs(changes) {
    var url = prefsMeta && prefsMeta.getAttribute('data-save');
    if (!url) return Promise.resolve();
    prefsQueue = Object.assign(prefsQueue || {}, changes);
    if (!prefsWait) {
        prefsWait = new Promise(function (r) { setTimeout(r, 120); }).then(function () {
            var body = prefsQueue;
            prefsQueue = null;
            prefsWait = null;
            prefsSent = prefsSent.then(function () {
                return fetch(url, { method: 'POST', credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest' },
                    body: new URLSearchParams(body) }).catch(function () {});
            });
            return prefsSent;
        });
    }
    return prefsWait;
}
window.otaPrefs = savePrefs;
// 账号还没存过偏好时，用这台浏览器现在的选择
if (prefsMeta && prefsMeta.getAttribute('content') === 'null') {
    (function () {
        var u = {}, out = {};
        try { u = JSON.parse(localStorage.getItem('ota.ui') || '{}') || {}; } catch (e) {}
        ['mode', 'density', 'side', 'tabs', 'motion'].forEach(function (k) { if (u[k]) out[k] = u[k]; });
        if (u.sv === 3 && u.sidebar) out.sidebar = u.sidebar;
        if (langNow() === 'en') out.lang = 'en';
        try {
            var a = JSON.parse(localStorage.getItem('ota.agents') || '{}') || {};
            if (a.sort) out.agentSort = a.sort;
        } catch (e) {}
        prefsMeta.setAttribute('content', JSON.stringify(out));
        savePrefs(out);
    })();
}

// 选了 English 时，词典和页面数据一起取，词典到了再渲染（不会先看到中文）
var i18nReady = i18nStart();
// 语言切换（顶栏的中 / EN、外观菜单、登录页）：记下来整页重新加载
document.addEventListener('click', function (e) {
    var t = e.target.closest && e.target.closest('[data-lang-toggle]');
    var b = !t && e.target.closest && e.target.closest('[data-lang]');
    if (!t && !b) return;
    e.preventDefault();
    var v = t ? (langNow() === 'en' ? 'zh' : 'en') : b.getAttribute('data-lang');
    if (v === langNow()) return;
    setLang(v);
    savePrefs({ lang: v === 'en' ? 'en' : '' }).then(function () { location.reload(); });
});
function paintLang() {
    Array.prototype.forEach.call(document.querySelectorAll('[data-lang]'), function (b) {
        var on = b.getAttribute('data-lang') === langNow();
        b.classList.toggle('on', on);
        b.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
}
new MutationObserver(function () { if (document.querySelector('[data-lang]:not([aria-pressed])')) paintLang(); })
    .observe(document.documentElement, { childList: true, subtree: true });
(function start() {
    var u = new URL(location.href);
    bootProgress(0.2);
    request(u.pathname + u.search).then(function (res) {
        bootProgress(0.45);
        return i18nReady.then(function () { return receive(res, { url: u, hash: u.hash }); });
    }).catch(function (err) {
        if (window.console) console.error(err);
        fail(navigator.onLine === false ? '网络断开了' : '页面没有加载出来');
    });
})();
