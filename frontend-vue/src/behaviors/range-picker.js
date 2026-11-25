// 代理后台的「自定义」时间段：弹出日历选起止日期（最长 366 天，最晚到统计截止那天），点「查看」按这个时间段打开。
// 电脑上贴着按钮弹出、并排两个月；手机上是底部面板、一个月
var DAY = 86400000, MAX_SPAN = 366;
var WEEK = ['一', '二', '三', '四', '五', '六', '日'];

function pad(n) { return (n < 10 ? '0' : '') + n; }
function parse(s) {
    var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s || '');
    return m ? new Date(+m[1], +m[2] - 1, +m[3]) : null;
}
function iso(d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
function add(d, n) { return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n); }
function span(a, b) { return Math.round((b - a) / DAY); }
function monthOf(d, n) { return new Date(d.getFullYear(), d.getMonth() + (n || 0), 1); }
function same(a, b) { return !!a && !!b && a.getTime() === b.getTime(); }

export default function rangePicker(btn) {
    var max = parse(btn.getAttribute('data-max'));
    if (!max) {
        var t = new Date();
        max = new Date(t.getFullYear(), t.getMonth(), t.getDate());
    }
    var action = btn.getAttribute('data-action') || location.pathname;
    var dlg = null, a = null, b = null, hover = null, view = null, two = true;

    function label(d) {
        return (d.getFullYear() === max.getFullYear() ? '' : d.getFullYear() + '年') + (d.getMonth() + 1) + '月' + d.getDate() + '日';
    }

    function presets() {
        var mon = add(max, -((max.getDay() + 6) % 7));
        var list = [
            ['本周', mon, max],
            ['上周', add(mon, -7), add(mon, -1)],
            ['本月', monthOf(max), max],
            ['上月', monthOf(max, -1), add(monthOf(max), -1)],
            ['近 3 个月', monthOf(max, -2), max],
            ['今年', new Date(max.getFullYear(), 0, 1), max],
            ['去年', new Date(max.getFullYear() - 1, 0, 1), new Date(max.getFullYear() - 1, 11, 31)]
        ];
        return list.map(function (p) { return { label: p[0], from: p[1], to: p[2] > max ? max : p[2] }; });
    }

    function lo() { return a; }
    function hi() {
        if (b) return b;
        if (a && hover && hover >= a && span(a, hover) < MAX_SPAN) return hover;
        return a;
    }

    function monthHtml(m, i, count) {
        var first = (m.getDay() + 6) % 7, n = new Date(m.getFullYear(), m.getMonth() + 1, 0).getDate();
        var L = lo(), H = hi(), preview = !!(a && !b);
        var prev = i === 0, next = i === count - 1;
        var lastShown = monthOf(view, count - 1);
        var h = '<div class="rp-month">'
            + '<div class="rp-mhead">'
            + (prev ? '<button type="button" class="rp-nav" data-nav="-1" aria-label="上个月"><svg class="i"><use href="#i-left"></use></svg></button>' : '<span class="rp-nav-ph"></span>')
            + '<b>' + m.getFullYear() + ' 年 ' + (m.getMonth() + 1) + ' 月</b>'
            + (next ? '<button type="button" class="rp-nav" data-nav="1" aria-label="下个月"' + (lastShown >= monthOf(max) ? ' disabled' : '') + '><svg class="i"><use href="#i-right"></use></svg></button>' : '<span class="rp-nav-ph"></span>')
            + '</div><div class="rp-week">' + WEEK.map(function (w, k) { return '<span' + (k > 4 ? ' class="we"' : '') + '>' + w + '</span>'; }).join('') + '</div><div class="rp-days">';
        for (var k = 0; k < first; k++) h += '<span class="rp-blank"></span>';
        for (var d = 1; d <= n; d++) {
            var day = new Date(m.getFullYear(), m.getMonth(), d), col = (first + d - 1) % 7;
            var off = day > max || (a && !b && day > a && span(a, day) >= MAX_SPAN);
            var cls = ['rp-day'];
            if (L && H && day >= L && day <= H) {
                cls.push('in');
                if (preview) cls.push('pre');
                if (same(day, L)) cls.push('lo');
                if (same(day, H)) cls.push('hi');
                if (col === 0 || d === 1) cls.push('rs');
                if (col === 6 || d === n) cls.push('re');
            }
            if (same(day, max)) cls.push('cut');
            if (off) cls.push('off');
            h += '<button type="button" class="' + cls.join(' ') + '" data-d="' + iso(day) + '"' + (off ? ' disabled' : '')
                + (same(day, max) ? ' title="统计截止到这天"' : '') + ' aria-label="' + label(day) + '"'
                + (cls.indexOf('lo') >= 0 || cls.indexOf('hi') >= 0 ? ' aria-pressed="true"' : '') + '><span>' + d + '</span></button>';
        }
        return h + '</div></div>';
    }

    function paint() {
        if (!dlg) return;
        var count = two ? 2 : 1, months = '';
        for (var i = 0; i < count; i++) months += monthHtml(monthOf(view, i), i, count);
        dlg.querySelector('.rp-months').innerHTML = months;
        var L = lo(), H = b || null;
        var sum = dlg.querySelector('.rp-sum');
        if (!a) sum.innerHTML = '<span class="muted">点一下选开始日期</span>';
        else if (!H) sum.innerHTML = '<b>' + label(L) + '</b><span class="rp-dash">–</span><span class="muted">再点一下选结束日期</span>';
        else sum.innerHTML = '<b>' + label(L) + '</b>' + (same(L, H) ? '' : '<span class="rp-dash">–</span><b>' + label(H) + '</b>')
            + '<span class="rp-n">共 ' + (span(L, H) + 1) + ' 天</span>';
        dlg.querySelector('[data-rp-ok]').disabled = !a;
        var list = presets();
        Array.prototype.forEach.call(dlg.querySelectorAll('[data-preset]'), function (p, k) {
            var o = list[k];
            p.classList.toggle('on', !!(o && same(o.from, a) && same(o.to, b)));
        });
        if (dlg.open) place();
    }

    function place() {
        if (!dlg) return;
        var sheet = window.matchMedia && matchMedia('(max-width:560px)').matches;
        dlg.classList.toggle('anchored', !sheet);
        if (sheet || !btn.isConnected) {
            dlg.style.top = dlg.style.left = '';
            return;
        }
        var r = btn.getBoundingClientRect(), w = dlg.offsetWidth, h = dlg.offsetHeight;
        var top = r.bottom + 8;
        if (top + h > innerHeight - 8) top = Math.max(8, r.top - 8 - h);
        var left = Math.min(Math.max(8, r.right - w), innerWidth - w - 8);
        dlg.style.top = Math.round(top) + 'px';
        dlg.style.left = Math.round(Math.max(8, left)) + 'px';
    }

    function close() {
        if (dlg && dlg.open) dlg.close();
    }

    function apply() {
        if (!a) return;
        var from = a, to = b || a;
        var u = new URL(action, location.href);
        u.searchParams.set('range', 'custom');
        u.searchParams.set('from', iso(from));
        u.searchParams.set('to', iso(to));
        close();
        var href = u.pathname + u.search;
        if (window.otaNavigate) window.otaNavigate(href);
        else location.href = href;
    }

    function pick(day) {
        if (!a || b) {
            a = day;
            b = null;
        } else if (day < a) {
            a = day;
        } else {
            b = day;
        }
        hover = null;
        paint();
    }

    function onResize() {
        var want = !(window.matchMedia && matchMedia('(max-width:720px)').matches);
        if (want !== two) {
            two = want;
            view = two ? monthOf(b || a || max, -1) : monthOf(b || a || max);
            paint();
        }
        place();
    }

    function open() {
        if (dlg) return;
        var f = parse(btn.getAttribute('data-from')), t = parse(btn.getAttribute('data-to'));
        a = f && f <= max ? f : null;
        b = a && t ? (t > max ? max : t) : null;
        if (a && b && b < a) { var x = a; a = b; b = x; }
        hover = null;
        two = !(window.matchMedia && matchMedia('(max-width:720px)').matches);
        var end = b || max;
        view = two ? monthOf(end, -1) : monthOf(end);

        dlg = document.createElement('dialog');
        dlg.className = 'rp';
        dlg.setAttribute('aria-label', '自定义时间段');
        dlg.innerHTML = '<div class="rp-in"><div class="rp-side"><span class="rp-side-k">快速选择</span>'
            + presets().map(function (p, k) { return '<button type="button" data-preset="' + k + '"' + (p.from > max ? ' disabled' : '') + '>' + p.label + '</button>'; }).join('')
            + '</div><div class="rp-main"><div class="rp-months"></div>'
            + '<div class="rp-foot"><span class="rp-sum"></span><span class="rp-acts"><button type="button" class="btn btn-ghost btn-sm" data-rp-cancel>取消</button>'
            + '<button type="button" class="btn btn-sm" data-rp-ok>查看</button></span></div>'
            + '<p class="rp-note">最长 366 天，最晚到 ' + label(max) + '（统计截止）</p></div></div>';
        document.body.appendChild(dlg);

        dlg.addEventListener('click', function (e) {
            if (e.target === dlg) { close(); return; }
            var day = e.target.closest('.rp-day');
            if (day && !day.disabled) {
                var key = day.getAttribute('data-d');
                pick(parse(key));
                var again = dlg.querySelector('.rp-day[data-d="' + key + '"]');
                if (again) again.focus({ preventScroll: true });
                return;
            }
            var nav = e.target.closest('[data-nav]');
            if (nav && !nav.disabled) {
                view = monthOf(view, +nav.getAttribute('data-nav'));
                paint();
                var again = dlg.querySelector('[data-nav="' + nav.getAttribute('data-nav') + '"]');
                if (again && !again.disabled) again.focus();
                return;
            }
            var pre = e.target.closest('[data-preset]');
            if (pre && !pre.disabled) {
                var o = presets()[+pre.getAttribute('data-preset')];
                a = o.from;
                b = o.to;
                if (span(a, b) >= MAX_SPAN) a = add(b, -(MAX_SPAN - 1));
                view = two ? monthOf(b, -1) : monthOf(b);
                paint();
                return;
            }
            if (e.target.closest('[data-rp-cancel]')) { close(); return; }
            if (e.target.closest('[data-rp-ok]')) apply();
        });
        dlg.addEventListener('mouseover', function (e) {
            if (!a || b) return;
            var day = e.target.closest('.rp-day');
            var d = day && !day.disabled ? parse(day.getAttribute('data-d')) : null;
            if (same(d, hover)) return;
            hover = d;
            paint();
        });
        dlg.addEventListener('mouseleave', function () {
            if (hover) { hover = null; paint(); }
        });
        dlg.addEventListener('close', function () {
            window.removeEventListener('resize', onResize);
            window.removeEventListener('scroll', place, true);
            window.removeEventListener('ota:leave', close);
            btn.setAttribute('aria-expanded', btn.classList.contains('on') ? 'true' : 'false');
            var d = dlg;
            dlg = null;
            setTimeout(function () { d.remove(); }, 260);
        });
        window.addEventListener('resize', onResize);
        window.addEventListener('scroll', place, true);
        window.addEventListener('ota:leave', close);

        paint();
        dlg.showModal();
        btn.setAttribute('aria-expanded', 'true');
        place();
        var focus = dlg.querySelector('.rp-day.hi') || dlg.querySelector('.rp-day.cut') || dlg.querySelector('.rp-day:not([disabled])');
        if (focus && window.matchMedia && matchMedia('(pointer:fine)').matches) focus.focus();
    }

    btn.addEventListener('click', function (e) {
        e.preventDefault();
        if (dlg) close(); else open();
    });
}
