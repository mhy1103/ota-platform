// 官网：中英文切换（和后台用同一个设置）、顶栏滚动变实、首屏产品演示（点发布 → 灰度 → 手机收到更新 → 全量，循环）、
// 产品功能演示（每个动画播完停 5 秒：自动轮到下一个；点过某一个就一直重播那一个）。
// 页面内容不先藏起来再出现：刷新、滚出去再滚回来都不会空白或闪一下；演示一直在播，不会因为滚动重新开始。
(function () {
    var root = document.documentElement;
    var each = function (sel, fn, scope) { Array.prototype.forEach.call((scope || document).querySelectorAll(sel), fn); };
    var brandEl = document.querySelector('.brand b'), brand = brandEl ? brandEl.textContent.trim() : '';
    var HOLD = 3000;    // 产品动画播完停多久再播

    // ---------- 语言：记在 ota.lang，进后台也是同一种语言 ----------
    var KEY = 'ota.lang', onLang = [];
    function stored() {
        try { return localStorage.getItem(KEY) === 'en' ? 'en' : 'zh'; } catch (e) { return 'zh'; }
    }
    function apply(lang) {
        var en = lang === 'en';
        root.setAttribute('lang', en ? 'en' : 'zh-CN');
        each('[data-en],[data-en-html]', function (el) {
            if (!el.hasAttribute('data-zh')) el.setAttribute('data-zh', el.innerHTML);
            if (!en) el.innerHTML = el.getAttribute('data-zh');
            else if (el.hasAttribute('data-en-html')) el.innerHTML = el.getAttribute('data-en-html');
            else el.textContent = el.getAttribute('data-en');
        });
        each('[data-en-label]', function (el) {
            if (!el.hasAttribute('data-zh-label')) el.setAttribute('data-zh-label', el.getAttribute('aria-label') || '');
            el.setAttribute('aria-label', en ? el.getAttribute('data-en-label') : el.getAttribute('data-zh-label'));
        });
        document.title = brand + (en ? ' · Self-hosted Expo update platform' : ' · 自托管的 Expo 热更新平台');
        onLang.forEach(function (fn) { fn(); });
    }
    var lang = stored();
    if (lang === 'en') apply('en');
    each('[data-lang-switch]', function (b) {
        b.addEventListener('click', function () {
            lang = lang === 'en' ? 'zh' : 'en';
            try { localStorage.setItem(KEY, lang); } catch (e) { /* 存不了就只对这次有效 */ }
            apply(lang);
        });
    });

    // 示例里的地址换成这个站自己的
    each('[data-origin]', function (el) { el.textContent = location.origin; });

    // ---------- 顶栏 ----------
    var nav = document.querySelector('[data-nav]');
    var onScroll = function () { if (nav) nav.classList.toggle('solid', window.scrollY > 8 || nav.classList.contains('open')); };
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
    var menu = document.querySelector('[data-menu]');
    if (menu && nav) {
        menu.addEventListener('click', function () {
            nav.classList.toggle('open');
            onScroll();
        });
        each('[data-links] a', function (a) { a.addEventListener('click', function () { nav.classList.remove('open'); onScroll(); }); });
        document.addEventListener('click', function (e) {
            if (nav.classList.contains('open') && !nav.contains(e.target)) { nav.classList.remove('open'); onScroll(); }
        });
    }

    // 点导航：滚到那一块，地址栏不加 #（页脚的链接也一样）
    document.addEventListener('click', function (e) {
        if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
        var a = e.target.closest('a[href^="#"]');
        if (!a) return;
        var id = a.getAttribute('href').slice(1);
        var el = id && document.getElementById(id);
        if (!el) return;
        e.preventDefault();
        if (nav && nav.classList.contains('open')) { nav.classList.remove('open'); onScroll(); }
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
    if (location.hash.length > 1) {
        var landed = document.getElementById(location.hash.slice(1));
        if (landed) landed.scrollIntoView({ block: 'start' });
        try { history.replaceState(null, '', location.pathname + location.search); } catch (err) { /* 地址改不了就留着 */ }
    }

    // 数字从 from 数到 to；ok() 不成立了（演示重新开始）就停
    function tween(el, from, to, dur, ok) {
        var t0 = performance.now();
        var step = function (t) {
            if (ok && !ok()) return;
            var k = Math.min(1, (t - t0) / dur), e = 1 - Math.pow(1 - k, 3);
            el.textContent = Math.round(from + (to - from) * e).toLocaleString('en-US');
            if (k < 1) requestAnimationFrame(step);
        };
        requestAnimationFrame(step);
    }

    // ---------- 首屏演示：光标点「发布」→ 灰度 20% → 手机灵动岛里下载、装好 → 首页换新 → 全量；播完停 5 秒再来 ----------
    var art = document.querySelector('[data-story]');
    if (art) {
        var cursor = art.querySelector('.cursor'), pub = art.querySelector('[data-pub]');
        var nums = Array.prototype.slice.call(art.querySelectorAll('[data-n]'));
        var STATES = ['c-on', 'press', 's-roll', 's-dl', 's-ok', 's-new', 's-full'];
        var timers = [], gen = 0, played = false;
        var at = function (ms, fn) { timers.push(setTimeout(fn, ms)); };
        var put = function (x, y, s) { cursor.style.transform = 'translate(' + x + 'px,' + y + 'px)' + (s ? ' scale(' + s + ')' : ''); };
        var aimAt = function () {
            var a = art.getBoundingClientRect(), b = pub.getBoundingClientRect();
            return { x: b.left - a.left + b.width * 0.52, y: b.top - a.top + b.height * 0.42 };
        };
        // 再来一遍：画面里的内容先淡出，复位时不播过渡，再淡入
        var run = function () {
            timers.forEach(clearTimeout);
            timers = [];
            if (played) {
                art.classList.add('fade');
                at(480, begin);
            } else {
                begin();
            }
            played = true;
        };
        var begin = function () {
            var mine = ++gen, live = function () { return mine === gen; };
            art.classList.add('reset');
            STATES.forEach(function (c) { art.classList.remove(c); });
            nums.forEach(function (n) { n.textContent = '0'; });
            var a = art.getBoundingClientRect(), home = { x: a.width * 0.94, y: a.height * 0.9 };
            put(home.x, home.y);
            void art.offsetWidth;
            art.classList.remove('reset');
            art.classList.remove('fade');
            at(600, function () { art.classList.add('c-on'); });
            at(750, function () { var t = aimAt(); put(t.x, t.y); });
            at(1950, function () { var t = aimAt(); art.classList.add('press'); put(t.x, t.y, 0.86); });
            at(2130, function () {
                var t = aimAt();
                art.classList.remove('press');
                put(t.x, t.y);
                art.classList.add('s-roll');
                tween(nums[0], 0, 2496, 1300, live);
                tween(nums[1], 0, 1873, 1500, live);
            });
            at(2800, function () { art.classList.add('s-dl'); });
            at(4700, function () { art.classList.add('s-ok'); });
            at(5700, function () { art.classList.remove('s-dl'); art.classList.add('s-new'); });
            at(6100, function () {
                art.classList.add('s-full');
                tween(nums[0], 2496, +nums[0].getAttribute('data-n'), 2400, live);
                tween(nums[1], 1873, +nums[1].getAttribute('data-n'), 2700, live);
            });
            at(6500, function () { put(home.x, home.y); art.classList.remove('c-on'); });
            // 最后一个数字 8.8 秒数完，再停 5 秒
            at(8800 + 5000, run);
        };
        run();
    }

    // ---------- 产品功能：每个动画播完停 5 秒，轮到下一个；点过某一个就一直重播那一个（同样播完停 5 秒） ----------
    var show = document.querySelector('[data-show]');
    if (show) {
        var items = Array.prototype.slice.call(show.querySelectorAll('.show-list li'));
        var panels = Array.prototype.slice.call(show.querySelectorAll('.pn'));
        var note = show.querySelector('[data-show-note]');
        var cur = 0, timer = 0, pinned = false, typing = [];
        var later = function (fn, ms) { typing.push(setTimeout(fn, ms)); };
        var noteText = function () { if (note && items[cur]) note.textContent = items[cur].querySelector('p').textContent; };
        onLang.push(noteText);
        var type = function (panel) {
            var el = panel.querySelector('[data-type]'), box = el && el.closest('.redeem');
            if (!el) return;
            var text = el.getAttribute('data-type'), i = 0;
            el.textContent = '';
            if (box) box.classList.remove('done');
            var next = function () {
                i++;
                el.textContent = text.slice(0, i);
                if (i < text.length) later(next, 70 + Math.random() * 50);
                else if (box) box.classList.add('done');
            };
            later(next, 450);
        };
        // 从头播这一个；again 为 true 时先把内容淡出再复位，不会一下子跳回开头
        var play = function (k, again) {
            typing.forEach(clearTimeout);
            typing = [];
            var p = panels[k];
            var start = function () {
                p.classList.remove('play');
                void p.offsetWidth;
                p.classList.add('play');
                p.classList.remove('rw');
                type(p);
            };
            if (again) {
                p.classList.add('rw');
                later(start, 260);
            } else {
                start();
            }
        };
        var dur = function (k) { return +panels[k].getAttribute('data-dur') || 3000; };
        var next = function (again) {
            clearTimeout(timer);
            var wait = dur(cur) + HOLD + (again ? 260 : 0);
            items[cur].style.setProperty('--t', wait + 'ms');
            show.classList.remove('run');
            void show.offsetWidth;
            if (!pinned) show.classList.add('run');
            timer = setTimeout(function () {
                if (pinned) {
                    play(cur, true);
                    next(true);
                } else {
                    go((cur + 1) % items.length);
                }
            }, wait);
        };
        var go = function (k) {
            cur = k;
            items.forEach(function (li, i) { li.classList.toggle('on', i === k); });
            panels.forEach(function (p, i) { p.classList.toggle('on', i === k); });
            noteText();
            play(k, false);
            next(false);
        };
        items.forEach(function (li, i) {
            li.querySelector('button').addEventListener('click', function () {
                pinned = true;
                go(i);
            });
        });
        go(0);
    }
})();
