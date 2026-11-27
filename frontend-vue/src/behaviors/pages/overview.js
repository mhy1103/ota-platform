// 概览看板：一行 12 列，卡片宽 3 / 4 / 6 / 8 / 12 列，按顺序一行一行排（排法在 board-pack.js）：同一行顶边、底边对齐，
// 每张卡片的高度都在自己的范围里（最矮是内容刚好显示完整，最高按卡片种类和宽度定），高矮差太多的不排在一起，矮的叠在高的旁边。
// 位置在这里算好，用 transform 摆上去（不挪 DOM，Vue 重画不会乱）；拖动、改宽度、改高度时别的卡片实时滑到新位置。
// 拖底边只改这一张：虚线框跟着指针，卡片在这一行能对齐的范围里跟上（拉高时后面的矮卡片会叠进旁边），拉过头有阻力，松手弹回。
// 布局（顺序、宽度、高度）存在账号偏好里：换设备打开一样，同一浏览器开着的其他标签页马上跟着变，别的设备 30 秒内跟上，换的时候卡片滑过去。
import { packRows, packHold, place } from './board-pack.js';

var editing = false, keepEditing = false, onEscape = null;
var active = null, channel = null;
// 页面重画（刷新数据、别处改了布局、添加卡片）前每张卡片在哪：重画后从这里滑到新位置
var memo = null;

var LABEL = { 3: '¼', 4: '⅓', 6: '½', 8: '⅔', 12: '整' };
var NAME = { 3: '四分之一行', 4: '三分之一行', 6: '半行', 8: '三分之二行', 12: '整行' };
var EASE = 'cubic-bezier(.2,.8,.2,1)';
// 最高能拉到内容高度的几倍（内容高度按当时的宽度量）：数字卡片能拉到两张叠起来那么高，长列表和横着排的留得少一些
var GROW = {
    codes: 2.2, agents: 2.2, total: 2.2, goal: 2.2, funnel: 2.2, system: 2, note: 2.6,
    expiring: 1.8, requests: 1.8, notices: 1.8, installers: 1.8, logins: 1.8, topcodes: 1.8, rollout: 1.8, live: 1.8, ranking: 1.8, todo: 1.8,
    coverage: 1.7, plans: 1.7, week: 1.7, quick: 1.6, mix: 1.6, trend: 1.6, mode: 1.8, today: 1.5, devices: 1.5,
    heat: 1.4, hot: 1.5, records: 1.5, ops: 1.5, deploys: 1.5, errors: 1.5, steps: 1.3
};
// 列表类的卡片：内容少的时候最高也能拉到这么高（空出来的地方是列表下面的空白，不难看）
var LISTS = { todo: 1, ranking: 1, records: 1, ops: 1, live: 1, coverage: 1, deploys: 1, errors: 1, plans: 1, rollout: 1, requests: 1,
    notices: 1, installers: 1, logins: 1, topcodes: 1, expiring: 1, mix: 1 };
var LIST_TALL = 560;
// 手机上调整看板时每张卡片的高度：只剩工具条（和 ui.css 里 .bw-bar 的高度、虚线框一起）
var BAR_ONLY = 56;

// 同一浏览器的其他标签页存了新布局：这边没在调整就换成新的
function listen() {
    if (channel || !window.BroadcastChannel) return;
    try { channel = new BroadcastChannel('ota-board'); } catch (e) { channel = null; return; }
    channel.onmessage = function (e) { if (active) active.remote(e.data && e.data.sig); };
}

export default function () {
    var board = document.querySelector('[data-board]');
    if (!board) return;
    var bar = document.querySelector('[data-board-bar]');
    var dialog = document.getElementById('board-dialog');
    var root = document.documentElement;
    function mq(q) { return !!(window.matchMedia && matchMedia(q).matches); }
    var reduce = function () { return root.getAttribute('data-motion') === 'off'; };
    var toast = function (msg, type) { if (window.otaToast) window.otaToast(msg, type); };

    // ---------- 卡片和存的布局 ----------
    function cards() {
        return Array.prototype.filter.call(board.children, function (el) { return el.classList.contains('bw'); });
    }
    function sizeOf(el) { return +el.getAttribute('data-size') || 12; }
    function sizesOf(el) { return (el.getAttribute('data-sizes') || String(sizeOf(el))).split(',').map(Number); }
    function nameOf(el) { return el.getAttribute('data-name') || ''; }
    // 一项是排法、ID、宽度、高度：/records:6x480。排法 '/' 新的一行，'+' 叠在前一张下面，'~' 同一行另起一列，没有就是还没排过；
    // ID 里也有 r、x（records、mix），所以 ID 不贪心匹配；以前存的行数（r2）不管
    function parseEntry(e) {
        var m = /^([/+~])?([a-z]{2,16}?)(?::(\d{1,2}))?(?:r[1-3])?(?:x(\d{2,4}))?$/.exec(String(e).trim());
        return m ? { hint: m[1] || '', id: m[2], size: +m[3] || 0, h: +m[4] || 0 } : null;
    }
    function idOf(e) { var p = parseEntry(e); return p ? p.id : String(e); }
    function saved() { var v = board.getAttribute('data-layout') || ''; return v ? v.split(',') : []; }

    // 每张卡片：各个宽度下内容的高度（量过的记着），拉过的高度，上次的排法
    var items = new Map(), kept = {};
    saved().forEach(function (e) { var p = parseEntry(e); if (p) kept[p.id] = p; });
    cards().forEach(function (el) {
        var p = kept[el.getAttribute('data-w')] || {};
        items.set(el, { nat: {}, stored: p.h || 0, hint: p.hint || '' });
    });
    var order = cards().sort(function (a, b) { return (+a.style.order || 0) - (+b.style.order || 0); });

    // ---------- 尺寸 ----------
    function screen() { return { one: mq('(max-width:720px)'), mid: mq('(max-width:1240px)') }; }
    function spanOf(el, s) {
        s = s || screen();
        if (s.one) return 12;
        var n = sizeOf(el);
        if (s.mid) {
            if (n === 3 || n === 4) return 6;
            if (n === 8) return 12;
        }
        return n;
    }
    function geo() {
        var W = board.clientWidth, g = parseFloat(getComputedStyle(board).getPropertyValue('--gap')) || 20;
        return { W: W, g: g, cw: (W - 11 * g) / 12 };
    }
    function face(el) {
        for (var c = el.firstElementChild; c; c = c.nextElementSibling) {
            if (!c.classList.contains('bw-edit') && !c.classList.contains('bw-badge')) return c;
        }
        return null;
    }
    // 内容在 span 列宽时刚好显示完整要多高：把里面那张卡片临时设成这个宽度量（卡片自己是宽度查询的容器，窄屏排法跟着变），外层不动。
    // G 由调用方在一次排版里只取一次：每次都读宽度和间距会在改了卡片尺寸之后逼浏览器把整页重新排一遍
    function measureAt(pairs, G) {
        G = G || geo();
        if (!G.W) return;
        var passes = [], seen = {};
        pairs.forEach(function (p) {
            var it = items.get(p[0]), c = face(p[0]), key = p[0].getAttribute('data-w') + '@' + p[1];
            if (!it || !c || it.nat[p[1]] != null || seen[key]) return;
            seen[key] = 1;
            for (var i = 0; ; i++) {
                if (!passes[i]) passes[i] = new Map();
                if (!passes[i].has(c)) { passes[i].set(c, [it, p[1]]); break; }
            }
        });
        if (!passes.length) return;
        // 量的时候长列表只算前几条（待处理有几十条也不会把卡片撑得老高），摆好以后列表占满卡片、多的在卡片里滚动。
        // 一次排版里 pack 已经加上了 measuring 就不再加减：每加减一次整块看板的样式都要重算
        var own = !board.classList.contains('measuring');
        if (own) board.classList.add('measuring');
        passes.forEach(function (pass) {
            pass.forEach(function (t, c) {
                c.style.flex = 'none';
                c.style.height = 'auto';
                c.style.width = (t[1] * G.cw + (t[1] - 1) * G.g) + 'px';
            });
            pass.forEach(function (t, c) { t[0].nat[t[1]] = c.offsetHeight; });
            pass.forEach(function (t, c) { c.style.flex = ''; c.style.height = ''; c.style.width = ''; });
        });
        if (own) board.classList.remove('measuring');
    }
    function forget() { items.forEach(function (it) { it.nat = {}; }); }
    // 高度范围：最矮是内容高度，最高按种类；拉过的（fixed）按拉的高度；正在拉的（pin）按指针的高度（按它所在那一列的宽度算范围）
    function entry(src, span, s, key, pin) {
        var it = items.get(src) || { nat: {}, stored: 0, hint: '' };
        if (editing && s.one) return { el: key || src, src: src, span: span, min: BAR_ONLY, max: BAR_ONLY, fixed: false, want: BAR_ONLY, hint: key === ph ? '' : it.hint || '' };
        var id = src.getAttribute('data-w');
        var n = Math.max(60, it.nat[span] || 0), grow = GROW[id] || 1.6;
        var max = Math.max(n, Math.round(Math.min(n * grow, n + 560)));
        // 列表卡片只有一两条、或者是空的时候内容很矮：也能拉到和同一行的卡片一样高，不会因为矮被挤到别的卡片下面
        if (LISTS[id]) max = Math.max(max, LIST_TALL);
        var hint = key === ph ? '' : it.hint || '';
        if (pin != null) return { el: key || src, src: src, span: span, min: n, max: max, fixed: true, pin: true, pinV: pin, want: Math.max(n, Math.min(max, pin)), hint: hint };
        var h = s.one ? 0 : it.stored || 0;
        return { el: key || src, src: src, span: span, min: n, max: max, fixed: h > 0, want: h > 0 ? Math.max(n, h) : n, hint: hint };
    }
    // 这张卡片所在那一行打头的卡片（往前找到「新的一行」那张）：改了这一行，就让这一行重新挑排法
    function rowStart(list, el) {
        var i = list.indexOf(el);
        for (; i > 0; i--) {
            var x = list[i];
            if (x !== ph && (items.get(x) || {}).hint === '/') break;
        }
        return list[Math.max(0, i)];
    }
    // 改了这张卡片：它所在那一行重新挑排法；它是一行打头的，这一行剩下的卡片从它后面那张开始
    function dirtyOf(list, els) {
        var set = new Set();
        els.forEach(function (el) {
            if (!el) return;
            set.add(rowStart(list, el));
            var i = list.indexOf(el), next = list[i + 1];
            if (i >= 0 && next && (next === ph || (items.get(next) || {}).hint !== '/')) set.add(next);
        });
        return set;
    }

    // ---------- 排版 ----------
    var ph = null, phSrc = null;
    // hold：正在拉高度的那张（el）、拉到的高度（v）、开始拉时的排法（prev）、同一列在它上面的卡片保持的高度（locks）；
    // dirty：这些卡片打头的行重新挑排法（刚改过的），别的行按记着的排法原样拼
    function pack(list, hold, dirty) {
        var G = geo(), s = screen();
        if (!G.W) return null;
        var src = function (el) { return el === ph ? phSrc : el; };
        var own = !board.classList.contains('measuring');
        if (own) board.classList.add('measuring');
        try {
            measureAt(list.map(function (el) { return [src(el), spanOf(src(el), s)]; }), G);
            var fit = function (e, span) {
                measureAt([[e.src, span]], G);
                return entry(e.src, span, s, null, e.pin ? e.pinV : null);
            };
            var entries = list.map(function (el) {
                var e = entry(src(el), spanOf(src(el), s), s, el, hold && hold.el === el ? hold.v : null);
                if (hold && hold.locks && hold.locks.has(el)) e.lock = hold.locks.get(el);
                return e;
            });
            var rows = hold && hold.prev ? packHold(hold.prev, entries, G.g, s.one, fit) : packRows(entries, G.g, s.one, fit, dirty);
        } finally {
            if (own) board.classList.remove('measuring');
        }
        return { rows: rows, G: G };
    }

    // ---------- 摆到页面上 ----------
    var rows = [], rects = new Map(), drawn = new WeakMap();
    // instant：不播动画；其余平滑滑过去。没变的卡片不碰
    function paint(res, mode) {
        board.classList.toggle('instant', mode === 'instant');
        rects = res.rects;
        res.rects.forEach(function (r, el) {
            if (el.classList.contains('lifted')) return;
            var d = drawn.get(el);
            if (d && d.x === r.x && d.y === r.y && d.w === r.w && d.h === r.h) return;
            el.style.transform = 'translate(' + r.x + 'px,' + r.y + 'px)';
            el.style.width = r.w + 'px';
            el.style.height = r.h + 'px';
            drawn.set(el, r);
        });
        var bh = res.height + 'px';
        if (board.style.height !== bh) board.style.height = bh;
        if (mode === 'instant') {
            void board.offsetWidth;
            board.classList.remove('instant');
        }
    }
    function show(p, mode) {
        if (!p) return;
        rows = p.rows;
        paint(place(rows, p.G.cw, p.G.g), mode);
    }
    function layout(mode, dirty) { show(pack(order, null, dirty), mode); }
    function locate(el) {
        for (var r = 0; r < rows.length; r++) {
            for (var k = 0; k < rows[r].groups.length; k++) {
                var c = rows[r].groups[k];
                for (var i = 0; i < c.members.length; i++) if (c.members[i].el === el) return { row: rows[r], group: c, index: i, m: c.members[i] };
            }
        }
        return null;
    }

    // 内容变了（便签、目标表单、换成英文、字体加载完）重新量；拖动、拉伸时先记着，松手再量。
    // cards：只有这几张卡片的内容变了，只重量它们（别的卡片量过的高度还对）；不给就全部重量
    var stale = false, frame = 0, changed = new Set();
    function busy() { return !!((drag && drag.moved) || sizing); }
    function soon(remeasure, cards) {
        if (remeasure) {
            if (cards) cards.forEach(function (el) { changed.add(el); });
            else stale = true;
        }
        if (frame) return;
        frame = requestAnimationFrame(function () {
            frame = 0;
            if (!board.isConnected) return;
            if (busy()) {
                if (changed.size) stale = true;
                changed.clear();
                return;
            }
            if (stale) {
                stale = false;
                forget();
            } else {
                changed.forEach(function (el) { var it = items.get(el); if (it) it.nat = {}; });
            }
            changed.clear();
            layout('smooth');
        });
    }

    // ---------- 保存到账号，告诉其他标签页 ----------
    function entryOf(el) {
        var it = items.get(el) || {}, s = (it.hint || '') + el.getAttribute('data-w') + ':' + sizeOf(el), h = it.stored;
        return h ? s + 'x' + Math.round(h) : s;
    }
    // 记下现在看到的排法：顺序按看到的（一行一行、一列一列、从上到下），每张记着自己是新的一行、叠在前一张下面、还是同一行另起一列。
    // 窄屏上一行放不下那么多，看到的不是宽屏的排法，就不改记着的排法
    function adopt() {
        var s = screen();
        if (s.one || s.mid || !rows.length) return;
        var seq = [];
        rows.forEach(function (r) {
            r.groups.forEach(function (c, ci) {
                c.members.forEach(function (m, i) {
                    var it = items.get(m.el);
                    if (!it) return;
                    it.hint = ci === 0 && i === 0 ? '/' : i > 0 ? '+' : '~';
                    seq.push(m.el);
                });
            });
        });
        if (seq.length === order.length) order = seq;
    }
    // 没画出来的卡片（比如开始使用已经做完）在布局里留着原来的位置
    function commit() {
        adopt();
        var shown = order.map(entryOf), here = {}, queue = shown.slice(), out = [];
        shown.forEach(function (e) { here[idOf(e)] = true; });
        saved().forEach(function (e) { out.push(here[idOf(e)] ? queue.shift() : e); });
        queue.forEach(function (e) { out.push(e); });
        return save(out);
    }
    function send(body, keep) {
        return fetch('/admin/account/board', {
            method: 'POST', credentials: 'same-origin', keepalive: !!keep,
            headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' },
            body: new URLSearchParams({ cards: body })
        }).then(function (r) {
            if (!r.ok) throw new Error(String(r.status));
            return r.json().catch(function () { return null; });
        });
    }
    // 一次只发一个，连着改了几次只发最后一次；没存上隔一会儿再试
    var sending = false, queued = null, waiters = [], retry = 0, warned = false;
    function save(list) {
        var body = list.join(',');
        board.setAttribute('data-layout', body);
        queued = body;
        var p = new Promise(function (resolve, reject) { waiters.push({ ok: resolve, no: reject }); });
        flush();
        return p;
    }
    function flush() {
        if (queued == null || sending) return;
        var body = queued, ws = waiters;
        queued = null;
        waiters = [];
        sending = true;
        send(body, false).then(function (d) {
            retry = 0;
            warned = false;
            if (d && d.sig) board.setAttribute('data-sig', d.sig);
            if (channel) try { channel.postMessage({ sig: d && d.sig }); } catch (err) { /* 别的标签页收不到就等它下次问服务器 */ }
            ws.forEach(function (w) { w.ok(d); });
        }, function (err) {
            if (queued == null) queued = body;
            if (!warned) toast('布局没保存上，网络好了会自动再存', 'err');
            warned = true;
            if (retry < 6) setTimeout(flush, Math.min(30000, 2000 * Math.pow(2, retry++)));
            ws.forEach(function (w) { w.no(err); });
        }).then(function () {
            sending = false;
            if (queued != null && !retry) flush();
        });
    }
    function post(url, body) {
        return fetch(url, {
            method: 'POST', credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest' }, body: new URLSearchParams(body)
        }).then(function (r) {
            if (!r.ok) throw new Error(String(r.status));
        }).catch(function () {
            toast('没保存上，请稍后再试', 'err');
            throw new Error('save');
        });
    }
    function refresh() {
        keepEditing = editing;
        return Promise.resolve(window.otaRefresh ? window.otaRefresh() : null);
    }
    // 别处（其他标签页、其他设备）改了布局：这边没在调整、也没有没存完的，就重新取一遍（卡片会从原位置滑过去）；正在调整就等调完
    var heard = null, missed = false;
    var self = {
        remote: function (sig) {
            if (!board.isConnected) return;
            if (sig) heard = sig;
            if (!heard || heard === board.getAttribute('data-sig')) return;
            if (editing || drag || sizing || sending || queued != null) { missed = true; return; }
            missed = false;
            if (window.otaRefresh) window.otaRefresh();
        }
    };
    active = self;
    listen();

    // ---------- 进入、退出调整 ----------
    function tools(el) {
        if (el.querySelector(':scope > .bw-edit')) return;
        var sizes = sizesOf(el);
        var t = document.createElement('div');
        t.className = 'bw-edit';
        t.innerHTML = '<div class="bw-bar"><span class="bw-name" title="按住拖到别的位置"><svg class="i"><use href="#i-grip"/></svg><span></span></span>'
            + '<span class="bw-move" role="group" aria-label="挪位置"><button type="button" data-move="-1" title="往前挪" aria-label="往前挪"><svg class="i"><use href="#i-chevron-down"/></svg></button>'
            + '<button type="button" data-move="1" title="往后挪" aria-label="往后挪"><svg class="i"><use href="#i-chevron-down"/></svg></button></span>'
            + (sizes.length > 1 ? '<span class="bw-sizes" role="group" aria-label="宽度">' + sizes.map(function (n) {
                return '<button type="button" data-size-pick="' + n + '" title="' + NAME[n] + '" aria-label="' + NAME[n] + '">' + LABEL[n] + '</button>';
            }).join('') + '</span>' : '')
            + '<button class="bw-x" type="button" title="收起这张卡片" aria-label="收起这张卡片"><svg class="i"><use href="#i-x"/></svg></button></div>'
            + (sizes.length > 1 ? '<button class="bw-rs" type="button" title="左右拖动改宽度" aria-label="改宽度"></button>' : '')
            + '<button class="bw-rh" type="button" title="上下拖动改高度；双击回到自动高度" aria-label="改高度"></button>';
        t.querySelector('.bw-name span').textContent = nameOf(el);
        el.appendChild(t);
        el.setAttribute('tabindex', '0');
        el.setAttribute('aria-roledescription', '看板卡片');
        mark(el);
    }
    function mark(el) {
        var n = String(sizeOf(el));
        Array.prototype.forEach.call(el.querySelectorAll(':scope > .bw-edit [data-size-pick]'), function (b) {
            var on = b.getAttribute('data-size-pick') === n;
            b.classList.toggle('on', on);
            b.setAttribute('aria-pressed', on ? 'true' : 'false');
        });
    }
    // 手机上一列排：调整时卡片收成只剩工具条，整块看板变成一张短列表，挪起来不用拖过好几屏
    function enter(now) {
        editing = true;
        board.classList.add('editing');
        if (bar) bar.hidden = false;
        cards().forEach(tools);
        syncMoves();
        if (screen().one) layout(now ? 'instant' : 'smooth');
    }
    function exit() {
        editing = false;
        board.classList.remove('editing', 'guides', 'busy');
        if (bar) bar.hidden = true;
        cards().forEach(function (el) {
            Array.prototype.forEach.call(el.querySelectorAll(':scope > .bw-edit, :scope > .bw-badge'), function (x) { x.remove(); });
            el.removeAttribute('tabindex');
            el.removeAttribute('aria-roledescription');
        });
        if (screen().one) {
            board.classList.add('settling');
            layout('smooth');
            setTimeout(function () { board.classList.remove('settling'); }, 400);
        }
        flush();
        if (missed) self.remote();
    }
    // 第一张不能再往前、最后一张不能再往后
    function syncMoves() {
        order.forEach(function (el, i) {
            Array.prototype.forEach.call(el.querySelectorAll(':scope > .bw-edit [data-move]'), function (b) {
                b.disabled = +b.getAttribute('data-move') < 0 ? i === 0 : i === order.length - 1;
            });
        });
    }
    // 往前或往后挪一个位置；它原来所在的行和挪到的行重新挑排法
    function shift(el, delta) {
        var i = order.indexOf(el), j = i + delta;
        if (i < 0 || j < 0 || j >= order.length) return false;
        var dirty = dirtyOf(order, [el]), it = items.get(el);
        order.splice(i, 1);
        order.splice(j, 0, el);
        if (it) it.hint = '';
        dirtyOf(order, [el]).forEach(function (x) { dirty.add(x); });
        layout('smooth', dirty);
        syncMoves();
        commit().catch(function () {});
        return true;
    }
    // 挪完卡片要还在看得见的地方（让开吸顶的顶栏和底下的调整条）
    function reveal(el) {
        var r = rects.get(el);
        if (!r) return;
        var top = board.getBoundingClientRect().top + r.y, min = headerBottom() + 8, max = footerTop() - 8, dy = 0;
        if (top < min) dy = top - min;
        else if (top + r.h > max) dy = Math.min(top - min, top + r.h - max);
        if (!dy) return;
        try { window.scrollBy({ top: dy, behavior: reduce() ? 'instant' : 'smooth' }); } catch (err) { window.scrollBy(0, dy); }
    }

    function badge(el, text, edge) {
        var b = el.querySelector(':scope > .bw-badge');
        if (!b) {
            b = document.createElement('div');
            b.className = 'bw-badge';
            el.appendChild(b);
        }
        if (b.getAnimations) b.getAnimations().forEach(function (a) { a.cancel(); });
        if (b.textContent !== text) b.textContent = text;
        b.classList.toggle('edge', !!edge);
    }
    function unbadge(el, delay) {
        var b = el.querySelector(':scope > .bw-badge');
        if (!b) return;
        if (reduce()) { b.remove(); return; }
        b.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 180, delay: delay == null ? 280 : delay, fill: 'forwards' }).onfinish = function () { b.remove(); };
    }
    function flash(el, text) {
        badge(el, text);
        unbadge(el, 700);
    }

    function hide(el) {
        var id = el.getAttribute('data-w'), name = nameOf(el);
        board.setAttribute('data-layout', saved().filter(function (e) { return idOf(e) !== id; }).join(','));
        var dirty = dirtyOf(order, [el]);
        order = order.filter(function (x) { return x !== el; });
        items.delete(el);
        syncMoves();
        el.style.pointerEvents = 'none';
        layout('smooth', dirty);
        var gone = function () { el.remove(); };
        if (reduce()) gone();
        else el.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 180, easing: 'ease-in', fill: 'forwards' }).onfinish = gone;
        commit().catch(function () {});
        syncPicks();
        toast('已收起「' + name + '」，在「添加卡片」里可以再打开');
    }
    function setSize(el, n) {
        if (sizeOf(el) === n || sizesOf(el).indexOf(n) < 0) return false;
        el.setAttribute('data-size', n);
        mark(el);
        layout('smooth', dirtyOf(order, [el]));
        return true;
    }

    // ---------- 指针：按下就锁定在看板上，松手之前不会丢 ----------
    function grab(e) { try { board.setPointerCapture(e.pointerId); } catch (err) { /* 脚本发的事件没有真实指针 */ } }
    function letGo(id) { try { if (board.hasPointerCapture(id)) board.releasePointerCapture(id); } catch (err) { /* 同上 */ } }
    function on(move, up) {
        window.addEventListener('pointermove', move);
        window.addEventListener('pointerup', up);
        window.addEventListener('pointercancel', up);
    }
    function off(move, up) {
        window.removeEventListener('pointermove', move);
        window.removeEventListener('pointerup', up);
        window.removeEventListener('pointercancel', up);
    }
    // 指针靠近窗口上下边时自动滚：越靠边越快，有上限；顶上让开吸顶的顶栏和标签栏
    function headerBottom() {
        var y = 0;
        Array.prototype.forEach.call(document.querySelectorAll('.topbar, #tabbar'), function (b) {
            var r = b.getBoundingClientRect();
            if (r.height && getComputedStyle(b).position === 'sticky') y = Math.max(y, r.bottom);
        });
        return y;
    }
    // 手机上调整条固定在底部：底边从它的上沿算
    function footerTop() {
        if (!bar || bar.hidden || getComputedStyle(bar).position !== 'fixed') return innerHeight;
        return bar.getBoundingClientRect().top;
    }
    function scrollSpeed(y, top) {
        var zone = 72, fast = 22, d = 0, bottom = footerTop();
        if (y < top + zone) d = -Math.min(1, (top + zone - y) / zone);
        else if (y > bottom - zone) d = Math.min(1, (y - bottom + zone) / zone);
        if (!d) return 0;
        var end = document.documentElement.scrollHeight - innerHeight;
        if ((d < 0 && window.scrollY <= 0) || (d > 0 && window.scrollY >= end)) return 0;
        return Math.round(fast * d * Math.abs(d)) || (d < 0 ? -1 : 1);
    }
    function scrollStep(v) {
        try { window.scrollBy({ top: v, behavior: 'instant' }); } catch (err) { window.scrollBy(0, v); }
    }

    var drag = null, sizing = null;
    board.addEventListener('pointerdown', function (e) {
        if (!editing || e.button !== 0 || drag || sizing) return;
        if (e.target.closest('.bw-x, .bw-sizes, .bw-move')) return;
        var el = e.target.closest('.bw');
        if (!el || el.parentNode !== board || el.classList.contains('lifted')) return;
        if (e.target.closest('.bw-rh')) { startHeight(e, el); return; }
        if (e.target.closest('.bw-rs')) { startWidth(e, el); return; }
        // 触屏：平板上按住工具条拖；手机上卡片只剩工具条，按住左边的把手才拖，别处照常上下滑
        if (e.pointerType !== 'mouse' && !e.target.closest(screen().one ? '.bw-name .i' : '.bw-bar')) return;
        e.preventDefault();
        drag = { el: el, id: e.pointerId, x0: e.clientX, y0: e.clientY, px: e.clientX, py: e.clientY, moved: false, raf: 0, tick: 0, key: '', until: 0 };
        grab(e);
        on(dragMove, dragEnd);
    });

    // ---------- 拖动：卡片浮起来跟着指针，原来的位置换成占位框，别的卡片实时重新排 ----------
    // 指针事件一帧可能来好几个：只记下位置，每帧处理一次
    function dragMove(e) {
        if (!drag || e.pointerId !== drag.id) return;
        drag.px = e.clientX;
        drag.py = e.clientY;
        if (!drag.moved) {
            if (Math.abs(drag.px - drag.x0) + Math.abs(drag.py - drag.y0) < 6) return;
            drag.moved = true;
            lift();
        }
        if (!drag.tick) drag.tick = requestAnimationFrame(dragFrame);
    }
    function dragFrame() {
        if (!drag) return;
        drag.tick = 0;
        follow();
        aim();
        edge();
    }
    function lift() {
        var el = drag.el, r = el.getBoundingClientRect(), at = order.indexOf(el), cur = rects.get(el);
        drag.top = headerBottom();
        drag.start = order.slice();
        drag.hint = (items.get(el) || {}).hint || '';
        drag.src = dirtyOf(order, [el]);
        drag.base = order.filter(function (x) { return x !== el; });
        ph = document.createElement('div');
        ph.className = 'bw-ph';
        ph.innerHTML = '<span>放在这里</span>';
        ph.setAttribute('data-size', sizeOf(el));
        if (cur) {
            ph.style.transform = 'translate(' + cur.x + 'px,' + cur.y + 'px)';
            ph.style.width = cur.w + 'px';
            ph.style.height = cur.h + 'px';
        }
        board.appendChild(ph);
        phSrc = el;
        drag.list = drag.base.slice();
        drag.list.splice(at < 0 ? drag.list.length : at, 0, ph);
        el.classList.add('lifted');
        drawn.delete(el);
        el.style.transform = 'none';
        el.style.left = r.left + 'px';
        el.style.top = r.top + 'px';
        // 外层有 transform 时 fixed 会偏，量一下差多少；之后只改 transform 跟着指针走（不触发重新排版）
        var got = el.getBoundingClientRect();
        el.style.left = (2 * r.left - got.left) + 'px';
        el.style.top = (2 * r.top - got.top) + 'px';
        board.classList.add('guides', 'busy');
        root.classList.add('board-grab');
        show(pack(drag.list, null, moved(drag.list, ph)), 'smooth');
    }
    // 拖动时重新挑排法的行：卡片原来那一行，和占位框现在所在的那一行；别的行不动
    function moved(list, at) {
        var set = dirtyOf(list, [at]);
        drag.src.forEach(function (x) { set.add(x); });
        return set;
    }
    function follow() {
        drag.el.style.transform = 'translate3d(' + (drag.px - drag.x0) + 'px,' + (drag.py - drag.y0) + 'px,0)';
    }
    function inside(r, x, y) { return x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h; }
    function same(a, b) {
        if (a.length !== b.length) return false;
        for (var i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
        return true;
    }
    // 指针压在哪张卡片上，占位框就排到它前面或后面（叠着的、整行宽的按上下半分，其余按左右半分）；
    // 落在一行右边的空白处排到这一行最后，拖到最下面排到最后。用算好的位置判断，不受动画影响
    function aim() {
        var now = performance.now();
        if (now < drag.until) return;
        var b = board.getBoundingClientRect(), x = drag.px - b.left, y = drag.py - b.top, mine = rects.get(ph);
        if (mine && inside(mine, x, y)) return;
        var hit = null, before = false, one = screen().one;
        rows.forEach(function (row) {
            row.groups.forEach(function (c) {
                c.members.forEach(function (m) {
                    var r = rects.get(m.el);
                    if (hit || m.el === ph || !r || !inside(r, x, y)) return;
                    hit = m.el;
                    before = one || c.members.length > 1 || c.span >= 9 ? y < r.y + r.h / 2 : x < r.x + r.w / 2;
                });
            });
        });
        if (!hit) {
            var last = rows[rows.length - 1], row = null;
            rows.forEach(function (rw) { if (y >= rw.y && y < rw.y + rw.H + 20) row = rw; });
            var below = !!last && y >= last.y + last.H;
            if (!row && below) row = last;
            if (!row) return;
            var lg = row.groups[row.groups.length - 1];
            if (!below && x < lg.x + lg.w) return;
            hit = lg.members[lg.members.length - 1].el;
            if (hit === ph) return;
            before = false;
        }
        var key = hit.getAttribute('data-w') + (before ? '<' : '>');
        if (key === drag.key) return;
        var list = drag.base.slice(), at = list.indexOf(hit);
        if (at < 0) return;
        list.splice(at + (before ? 0 : 1), 0, ph);
        if (same(list, drag.list)) {
            drag.key = key;
            return;
        }
        // 刚换过去马上又要换回来：多挪一点再换，免得来回跳
        if (drag.prev && same(list, drag.prev) && now - drag.at < 450 && Math.abs(drag.px - drag.ax) + Math.abs(drag.py - drag.ay) < 28) return;
        drag.prev = drag.list;
        drag.at = now;
        drag.ax = drag.px;
        drag.ay = drag.py;
        drag.key = key;
        drag.list = list;
        drag.until = now + 80;
        show(pack(list, null, moved(list, ph)), 'smooth');
    }
    // 拖到窗口上下边附近：页面跟着滚，指针不动也一直滚，拖着的卡片留在指针下面
    function edge() {
        if (drag.raf) return;
        var step = function () {
            if (!drag || !drag.moved) return;
            var v = scrollSpeed(drag.py, drag.top);
            if (!v) { drag.raf = 0; return; }
            scrollStep(v);
            aim();
            drag.raf = requestAnimationFrame(step);
        };
        drag.raf = requestAnimationFrame(step);
    }
    function stopDrag() {
        off(dragMove, dragEnd);
        var d = drag;
        drag = null;
        letGo(d.id);
        if (d.raf) cancelAnimationFrame(d.raf);
        if (d.tick) cancelAnimationFrame(d.tick);
        return d;
    }
    function drop(d, cancel) {
        var el = d.el, from = el.getBoundingClientRect(), it = items.get(el), dirty = null;
        order = cancel ? d.start : d.list.map(function (x) { return x === ph ? el : x; });
        if (!cancel && it) {
            it.hint = '';
            dirty = dirtyOf(order, [el]);
            d.src.forEach(function (x) { dirty.add(x); });
        }
        ph.remove();
        ph = null;
        phSrc = null;
        el.classList.remove('lifted');
        el.classList.add('landing');
        el.style.left = '';
        el.style.top = '';
        drawn.delete(el);
        board.classList.remove('guides', 'busy');
        root.classList.remove('board-grab');
        layout('smooth', dirty);
        syncMoves();
        var r = rects.get(el);
        if (r && !reduce()) {
            var to = el.getBoundingClientRect(), dx = from.left - to.left, dy = from.top - to.top;
            var a = el.animate([
                { transform: 'translate(' + (r.x + dx) + 'px,' + (r.y + dy) + 'px)', width: from.width + 'px', height: from.height + 'px' },
                { transform: 'translate(' + r.x + 'px,' + r.y + 'px)', width: r.w + 'px', height: r.h + 'px' }
            ], { duration: 320, easing: EASE });
            a.onfinish = a.oncancel = function () { el.classList.remove('landing'); };
        } else {
            el.classList.remove('landing');
        }
        if (!cancel) commit().catch(function () {});
        if (stale) soon(true);
    }
    function dragEnd(e) {
        if (!drag || (e && e.pointerId !== drag.id)) return;
        var d = stopDrag();
        if (d.moved) drop(d, !!e && e.type === 'pointercancel');
    }

    // ---------- 拖右边缘改宽度：按列吸附到这张卡片能用的宽度 ----------
    function startWidth(e, el) {
        e.preventDefault();
        e.stopPropagation();
        var r = el.getBoundingClientRect(), G = geo();
        sizing = { kind: 'w', el: el, id: e.pointerId, x0: e.clientX, w0: r.width, step: G.cw + G.g, g: G.g, from: sizeOf(el) };
        el.classList.add('resizing');
        board.classList.add('guides', 'busy');
        root.classList.add('board-size-h');
        badge(el, NAME[sizing.from] + ' · ' + sizing.from + ' 列');
        grab(e);
        on(widthMove, widthEnd);
    }
    function widthMove(e) {
        var s = sizing;
        if (!s || e.pointerId !== s.id) return;
        var span = (s.w0 + e.clientX - s.x0 + s.g) / s.step, best = null;
        sizesOf(s.el).forEach(function (n) { if (best === null || Math.abs(n - span) < Math.abs(best - span)) best = n; });
        if (best !== null && setSize(s.el, best)) badge(s.el, NAME[best] + ' · ' + best + ' 列');
    }
    function widthEnd(e) {
        var s = sizing;
        if (!s || (e && e.pointerId !== s.id)) return;
        off(widthMove, widthEnd);
        sizing = null;
        letGo(s.id);
        s.el.classList.remove('resizing');
        board.classList.remove('guides', 'busy');
        root.classList.remove('board-size-h');
        unbadge(s.el);
        if (sizeOf(s.el) !== s.from) commit().catch(function () {});
        if (stale) soon(true);
    }

    // ---------- 拖底边改高度：只改这一张 ----------
    // 虚线框跟着指针（在这张卡片自己的范围里，拉过头有阻力）；卡片在这一行能对齐的范围里跟上：
    // 拉高时旁边的卡片在各自的范围里补齐，补不齐就把后面的矮卡片叠进旁边；拉矮时空出来的地方后面的矮卡片会补进来。松手后就是看到的样子
    function rubber(v, lo, hi) {
        var damp = function (d) { return 70 * (1 - 1 / (d / 70 * 0.55 + 1)); };
        return v > hi ? hi + damp(v - hi) : v < lo ? lo - damp(lo - v) : v;
    }
    // 开始拉的时候：现在的排法，和同一列在它上面的卡片（拉的时候高度不变）
    function holdOf(el) {
        var at = locate(el), locks = new Map();
        if (!at) return null;
        for (var j = 0; j < at.index; j++) locks.set(at.group.members[j].el, at.group.hs[j]);
        return { el: el, at: at, prev: rows, locks: locks, h0: at.group.hs[at.index], min: at.m.min, max: at.m.max };
    }
    // 拉到的高度旁边的卡片跟不上（被挡住了）：按挡住的高度再排一遍，看到的就是松手后的样子
    function hold(h, v) {
        var at = function (want) { return pack(order, { el: h.el, v: want, prev: h.prev, locks: h.locks }); };
        var want = Math.max(h.min, Math.min(h.max, v)), p = at(want);
        if (!p) return p;
        var got = heightIn(p, h.el);
        if (got != null && got < want - 2) {
            var q = at(got), again = heightIn(q, h.el);
            if (again != null && Math.abs(again - got) <= 2) return q;
        }
        return p;
    }
    function heightIn(p, el) {
        for (var r = 0; r < p.rows.length; r++) {
            for (var k = 0; k < p.rows[r].groups.length; k++) {
                var c = p.rows[r].groups[k];
                for (var i = 0; i < c.members.length; i++) if (c.members[i].el === el) return c.hs[i];
            }
        }
        return null;
    }
    function near1(a, b) { return !!a && !!b && Math.abs(a.x - b.x) <= 1 && Math.abs(a.y - b.y) <= 1 && Math.abs(a.w - b.w) <= 1 && Math.abs(a.h - b.h) <= 1; }
    function sameRects(a, b) {
        if (!a || !b || a.size !== b.size) return false;
        var ok = true;
        a.forEach(function (r, el) { if (ok && !near1(r, b.get(el))) ok = false; });
        return ok;
    }
    // 松手：存下看到的高度，重新排一遍和看到的一样才算数；不一样就把这一行看到的高度也存下（还不一样就按重新排的来）
    function release(h, got) {
        var it = items.get(h.el);
        if (!it) return;
        adopt();
        var shown = rects, auto = Math.abs(got - h.min) <= 2;
        var undo = [];
        var setH = function (el, v) {
            var x = items.get(el);
            if (!x) return;
            undo.push([x, x.stored]);
            x.stored = v;
        };
        var tryPack = function () {
            var p = pack(order);
            return p && sameRects(place(p.rows, p.G.cw, p.G.g).rects, shown) ? p : null;
        };
        setH(h.el, auto ? 0 : got);
        var p = tryPack();
        if (!p && auto) { setH(h.el, got); p = tryPack(); }
        if (!p && h.locks.size) {
            h.locks.forEach(function (v, el) { setH(el, (shown.get(el) || {}).h || v); });
            p = tryPack();
        }
        if (!p) {
            var row = null;
            rows.forEach(function (r) { r.groups.forEach(function (c) { c.members.forEach(function (m) { if (m.el === h.el) row = r; }); }); });
            if (row) row.groups.forEach(function (c) { c.members.forEach(function (m) { if (m.el !== h.el && shown.get(m.el)) setH(m.el, shown.get(m.el).h); }); });
            p = tryPack();
        }
        if (!p) {
            // 只留拉的那张的高度，按重新排的结果滑过去
            for (var i = undo.length - 1; i > 0; i--) undo[i][0].stored = undo[i][1];
            it.stored = got;
        }
        layout('smooth');
    }
    function startHeight(e, el) {
        e.preventDefault();
        e.stopPropagation();
        if (screen().one) return;
        var h = holdOf(el);
        if (!h) return;
        var s = { kind: 'h', el: el, id: e.pointerId, y0: e.clientY, py: e.clientY, sy: window.scrollY, h0: h.h0, hold: h,
            min: h.min, max: h.max, raf: 0, tick: 0, moved: false, top: headerBottom() };
        s.v = s.q = s.h0;
        s.ghost = document.createElement('div');
        s.ghost.className = 'bw-ghost';
        board.appendChild(s.ghost);
        sizing = s;
        el.classList.add('resizing');
        board.classList.add('guides', 'busy');
        root.classList.add('board-size-v');
        ghost(s, s.h0);
        heightBadge(s, s.h0, s.h0);
        grab(e);
        on(heightMove, heightEnd);
    }
    function ghost(s, h) {
        var r = rects.get(s.el);
        if (!r) return;
        var g = s.ghost.style;
        g.transform = 'translate(' + r.x + 'px,' + r.y + 'px)';
        g.width = r.w + 'px';
        g.height = Math.max(40, Math.round(h)) + 'px';
    }
    function heightBadge(s, want, got) {
        if (want > s.max + 1) badge(s.el, '最高 ' + s.max, true);
        else if (want < s.min - 1) badge(s.el, '最低 ' + s.min + ' · 内容要显示完整', true);
        else if (got < want - 3) badge(s.el, '高度 ' + got + ' · 旁边的卡片最多这么高', true);
        else if (got > want + 3) badge(s.el, '高度 ' + got + ' · 旁边的卡片要这么高', true);
        else badge(s.el, '高度 ' + got);
    }
    // 指针事件只记位置，每帧算一次
    function heightMove(e) {
        var s = sizing;
        if (!s || e.pointerId !== s.id) return;
        s.py = e.clientY;
        if (Math.abs(s.py - s.y0) > 2) s.moved = true;
        if (!s.tick) s.tick = requestAnimationFrame(function () {
            s.tick = 0;
            if (sizing !== s) return;
            track(s);
            near(s);
        });
    }
    function track(s) {
        var raw = s.h0 + (s.py - s.y0) + (window.scrollY - s.sy);
        s.v = Math.max(s.min, Math.min(s.max, raw));
        // 按 4px 一档重新排，指针抖一下不会来回换叠法
        var q = Math.round(s.v / 4) * 4;
        if (q !== s.q || !s.drawnOnce) {
            s.q = q;
            s.drawnOnce = true;
            show(hold(s.hold, q), 'smooth');
        }
        var r = rects.get(s.el), got = r ? r.h : s.v;
        ghost(s, rubber(raw, s.min, s.max));
        heightBadge(s, raw, got);
    }
    function near(s) {
        if (s.raf) return;
        var step = function () {
            if (sizing !== s) return;
            var v = scrollSpeed(s.py, s.top);
            if (!v) { s.raf = 0; return; }
            scrollStep(v);
            track(s);
            s.raf = requestAnimationFrame(step);
        };
        s.raf = requestAnimationFrame(step);
    }
    var lastTap = null;
    function heightEnd(e) {
        var s = sizing;
        if (!s || (e && e.pointerId !== s.id)) return;
        off(heightMove, heightEnd);
        sizing = null;
        letGo(s.id);
        if (s.raf) cancelAnimationFrame(s.raf);
        if (s.tick) cancelAnimationFrame(s.tick);
        s.el.classList.remove('resizing');
        board.classList.remove('guides', 'busy');
        root.classList.remove('board-size-v');
        var r = rects.get(s.el), got = r ? r.h : s.h0, it = items.get(s.el);
        // 虚线框收回到卡片现在的样子再消失
        var gh = s.ghost;
        if (reduce() || !r) gh.remove();
        else gh.animate([{ height: gh.style.height, opacity: 1 }, { height: got + 'px', opacity: 0 }], { duration: 260, easing: EASE }).onfinish = function () { gh.remove(); };
        unbadge(s.el, s.moved ? 420 : 0);
        if (!s.moved) {
            var now = Date.now();
            if (lastTap && lastTap.el === s.el && now - lastTap.t < 400) {
                lastTap = null;
                if (it && it.stored) {
                    it.stored = 0;
                    layout('smooth', dirtyOf(order, [s.el]));
                    flash(s.el, '自动高度');
                    commit().catch(function () {});
                }
            } else {
                lastTap = { el: s.el, t: now };
                if (s.drawnOnce) layout('smooth');
            }
            if (stale) soon(true);
            return;
        }
        lastTap = null;
        // 只动了几像素：当没拉过
        if (Math.abs(got - s.h0) < 6) {
            layout('smooth');
            if (stale) soon(true);
            return;
        }
        release(s.hold, got);
        commit().catch(function () {});
        if (stale) soon(true);
    }
    function nudge(el, delta) {
        if (screen().one) return;
        var h = holdOf(el);
        if (!h) return;
        show(hold(h, h.h0 + delta), 'smooth');
        var r = rects.get(el), got = r ? r.h : h.h0;
        flash(el, got === h.h0 ? (delta > 0 ? '最高 ' + got : '最低 ' + got) : '高度 ' + got);
        release(h, got);
        commit().catch(function () {});
    }

    board.addEventListener('click', function (e) {
        if (!editing) return;
        var x = e.target.closest('.bw-x');
        if (x) { hide(x.closest('.bw')); return; }
        var mv = e.target.closest('[data-move]');
        if (mv) {
            var card = mv.closest('.bw');
            if (shift(card, +mv.getAttribute('data-move'))) reveal(card);
            return;
        }
        var pick = e.target.closest('[data-size-pick]');
        if (pick) {
            var el = pick.closest('.bw'), n = +pick.getAttribute('data-size-pick');
            if (setSize(el, n)) {
                flash(el, NAME[n] + ' · ' + n + ' 列');
                commit().catch(function () {});
            }
        }
    });
    // 方向键挪位置，Shift + 左右改宽度，Shift + 上下改高度，Delete 收起
    board.addEventListener('keydown', function (e) {
        var el = e.target;
        if (!editing || !el.classList || !el.classList.contains('bw') || el.parentNode !== board) return;
        if (e.key === 'Delete' || e.key === 'Backspace') { e.preventDefault(); hide(el); return; }
        var back = e.key === 'ArrowLeft' || e.key === 'ArrowUp', fwd = e.key === 'ArrowRight' || e.key === 'ArrowDown';
        if (!back && !fwd) return;
        e.preventDefault();
        if (e.shiftKey && (e.key === 'ArrowUp' || e.key === 'ArrowDown')) { nudge(el, e.key === 'ArrowDown' ? 24 : -24); return; }
        if (e.shiftKey) {
            var sizes = sizesOf(el), k = sizes.indexOf(sizeOf(el)) + (back ? -1 : 1);
            if (k >= 0 && k < sizes.length && setSize(el, sizes[k])) {
                flash(el, NAME[sizes[k]] + ' · ' + sizes[k] + ' 列');
                commit().catch(function () {});
            }
            return;
        }
        if (shift(el, back ? -1 : 1)) el.focus({ preventScroll: true });
    });

    // ---------- 添加卡片 ----------
    function picks() {
        return dialog ? Array.prototype.slice.call(dialog.querySelectorAll('[data-board-pick]')) : [];
    }
    function syncPicks() {
        var on = {};
        saved().forEach(function (e) { on[idOf(e)] = true; });
        picks().forEach(function (c) { c.checked = !!on[c.value]; });
        count();
    }
    function count() {
        var out = dialog && dialog.querySelector('[data-board-picked]');
        if (out) out.textContent = '已打开 ' + picks().filter(function (c) { return c.checked; }).length + ' 张';
    }
    if (dialog) {
        dialog.addEventListener('close', syncPicks);
        dialog.addEventListener('change', count);
        count();
        var apply = dialog.querySelector('[data-board-apply]');
        if (apply) apply.addEventListener('click', function () {
            var checked = {};
            picks().forEach(function (c) { if (c.checked) checked[c.value] = true; });
            var out = saved().filter(function (e) { return checked[idOf(e)]; });
            var have = {};
            out.forEach(function (e) { have[idOf(e)] = true; });
            var added = picks().filter(function (c) { return c.checked && !have[c.value]; }).map(function (c) { return c.value; });
            out = out.concat(added);
            dialog.close();
            save(out).then(refresh).then(function () {
                if (!added.length) return;
                toast('已添加 ' + added.length + ' 张卡片，排在最后');
                var first = document.querySelector('[data-board] > .bw[data-w="' + added[0] + '"]');
                if (first) first.scrollIntoView({ behavior: reduce() ? 'auto' : 'smooth', block: 'center' });
            }).catch(function () {});
        });
    }

    var edit = document.querySelector('[data-board-edit]');
    if (edit) edit.addEventListener('click', function () { if (editing) exit(); else enter(); });
    var done = document.querySelector('[data-board-done]');
    if (done) done.addEventListener('click', exit);
    var reset = document.querySelector('[data-board-reset]');
    if (reset) reset.addEventListener('click', function () {
        queued = null;
        waiters.forEach(function (w) { w.no(new Error('reset')); });
        waiters = [];
        post('/admin/account/board', { reset: 'true' }).then(refresh).then(function () { toast('已恢复默认看板'); }).catch(function () {});
    });
    // 拉过的高度都不要了（顺序、宽度不变），每一行重新挑排法
    var auto = document.querySelector('[data-board-auto]');
    if (auto) auto.addEventListener('click', function () {
        var any = false;
        items.forEach(function (it) {
            if (it.stored) any = true;
            it.stored = 0;
        });
        if (!any) { toast('现在都是自动高度'); return; }
        layout('smooth', new Set(order));
        commit().catch(function () {});
        toast('高度都回到自动了');
    });

    onEscape = function () {
        if (drag) {
            var d = stopDrag();
            if (d.moved) drop(d, true);
            return;
        }
        if (sizing) {
            if (sizing.kind === 'h') heightEnd();
            else widthEnd();
            return;
        }
        if (done && board.classList.contains('editing')) done.click();
    };
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && editing && onEscape && !document.querySelector('dialog[open]')) onEscape();
    });

    // ---------- 便签：停下来 0.7 秒自动保存 ----------
    var note = board.querySelector('[data-board-note]');
    if (note) {
        var state = board.querySelector('[data-note-state]'), last = note.value, timer = 0, idle = 0;
        var tell = function (text, ok) {
            if (!state) return;
            state.textContent = text;
            state.classList.toggle('saved', !!ok);
        };
        var keep = function () {
            clearTimeout(timer);
            if (note.value === last) return;
            var value = note.value;
            post('/admin/account/board/data', { key: 'note', value: value }).then(function () {
                last = value;
                tell('已保存', true);
                clearTimeout(idle);
                idle = setTimeout(function () { tell('只有你看得到'); }, 1800);
            }).catch(function () { tell('没保存上'); });
        };
        note.addEventListener('input', function () {
            clearTimeout(timer);
            timer = setTimeout(keep, 700);
            tell('正在记…');
        });
        note.addEventListener('blur', keep);
    }

    // ---------- 今日目标 ----------
    var goalForm = board.querySelector('[data-goal-form]');
    if (goalForm) {
        board.addEventListener('click', function (e) {
            if (editing) return;
            if (e.target.closest('[data-goal-edit]')) {
                goalForm.hidden = false;
                goalForm.elements.goal.focus();
                goalForm.elements.goal.select();
            } else if (e.target.closest('[data-goal-cancel]')) {
                goalForm.hidden = true;
            }
        });
        goalForm.addEventListener('submit', function (e) {
            e.preventDefault();
            var n = parseInt(goalForm.elements.goal.value, 10);
            if (!(n > 0)) return;
            post('/admin/account/board/data', { key: 'goal', value: String(n) }).then(refresh).then(function () {
                toast('今天的目标定成 ' + n + ' 台');
            }).catch(function () {});
        });
    }

    // ---------- 开始：量好、摆好再显示（这时还没画到屏幕上，不会闪）；刚重画过就从原来的位置滑过去 ----------
    board.classList.add('abs');
    layout('instant');
    var was = memo;
    memo = null;
    var fresh = was && was.path === location.pathname && Date.now() - was.at < 4000;
    if (fresh && !reduce()) {
        rects.forEach(function (r, el) {
            var o = was.rects[el.getAttribute('data-w')], to = { transform: 'translate(' + r.x + 'px,' + r.y + 'px)', width: r.w + 'px', height: r.h + 'px' };
            if (!o) {
                el.animate([{ opacity: 0, transform: to.transform + ' scale(.97)' }, { opacity: 1, transform: to.transform }], { duration: 320, easing: EASE });
            } else if (Math.abs(o.x - r.x) + Math.abs(o.y - r.y) + Math.abs(o.w - r.w) + Math.abs(o.h - r.h) > 1) {
                el.animate([{ transform: 'translate(' + o.x + 'px,' + o.y + 'px)', width: o.w + 'px', height: o.h + 'px' }, to], { duration: 420, easing: EASE });
            }
        });
        var nowH = board.style.height;
        if (was.height && was.height !== nowH) board.animate([{ height: was.height }, { height: nowH }], { duration: 420, easing: EASE });
    }
    // 调整到一半页面重画了（别处改了布局、刷新数据）：接着调整
    if (keepEditing || (fresh && was.editing)) enter(true);
    else editing = false;
    keepEditing = false;

    var lastW = board.clientWidth, ro = null, mo = null;
    if (window.ResizeObserver) {
        ro = new ResizeObserver(function () {
            var w = board.clientWidth;
            if (!w || w === lastW) return;
            lastW = w;
            if (busy()) { stale = true; return; }
            forget();
            layout('instant');
        });
        ro.observe(board);
    }
    // 卡片里的内容变了才重新量；调整时盖上去的工具条、提示、占位框、虚线框不算
    function ours(n) {
        return n.nodeType === 1 && (n.classList.contains('bw-edit') || n.classList.contains('bw-badge') || n.classList.contains('bw-ph') || n.classList.contains('bw-ghost'));
    }
    function matters(r) {
        var t = r.target.nodeType === 1 ? r.target : r.target.parentElement;
        if (!t || t === board || t.closest('.bw-edit, .bw-badge, .bw-ph')) return false;
        // 数字正在从 0 滚到实际值（ui.js 里的 data-roll）：滚到最后一帧再量，中间每帧都量会让整页每帧重排
        if (r.type === 'characterData' && t.closest('[data-roll]')) return false;
        if (r.type !== 'childList') return true;
        var all = Array.prototype.slice.call(r.addedNodes).concat(Array.prototype.slice.call(r.removedNodes));
        return !all.length || !all.every(ours);
    }
    if (window.MutationObserver) {
        mo = new MutationObserver(function (list) {
            var cards = new Set();
            for (var i = 0; i < list.length; i++) {
                if (!matters(list[i])) continue;
                var t = list[i].target.nodeType === 1 ? list[i].target : list[i].target.parentElement;
                var card = t.closest('.bw');
                if (!card || card.parentNode !== board || !items.has(card)) { soon(true); return; }
                cards.add(card);
            }
            if (cards.size) soon(true, cards);
        });
        mo.observe(board, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ['hidden', 'open'] });
    }
    if (document.fonts && document.fonts.status !== 'loaded') document.fonts.ready.then(function () { if (board.isConnected) soon(true); });

    window.addEventListener('ota:pulse', function (e) {
        var d = e.detail || {};
        if (d.board) self.remote(d.board);
    });
    window.addEventListener('pagehide', function () {
        if (queued == null) return;
        send(queued, true).catch(function () {});
        queued = null;
    });
    // 要重画了（刷新数据、换布局、去别的页面）：记下每张卡片的位置，存掉没存完的
    window.addEventListener('ota:leave', function () {
        var snap = {};
        rects.forEach(function (r, el) { if (el !== ph) snap[el.getAttribute('data-w')] = r; });
        memo = { path: location.pathname, at: Date.now(), rects: snap, height: board.style.height, editing: editing };
        if (active === self) active = null;
        if (ro) ro.disconnect();
        if (mo) mo.disconnect();
        flush();
    });
}
