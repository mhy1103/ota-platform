// 后台公共脚本：弹窗、提示、外观、引导、搜索、页面标签。切页由 Vue 版的入口（main.js）负责，这里通过 host.go 交给它
import account from './pages/account.js';
import activations from './pages/activations.js';
import admins from './pages/admins.js';
import agent from './pages/agent.js';
import agents from './pages/agents.js';
import app from './pages/app.js';
import apps from './pages/apps.js';
import codes from './pages/codes.js';
import devices from './pages/devices.js';
import docs from './pages/docs.js';
import overview from './pages/overview.js';
import releases from './pages/releases.js';
import settings from './pages/settings.js';
import signing from './pages/signing.js';

var PAGES = { account: account, activations: activations, admins: admins, agent: agent, agents: agents, app: app, apps: apps, codes: codes, devices: devices,
    docs: docs, overview: overview, releases: releases, settings: settings, signing: signing };

export default function boot(host) {
        var root = document.documentElement;
        // 动画只看外观里的开关；系统的「减弱动态效果」不管（很多 Windows 电脑默认关了动画效果，整个后台就不动了）
        var reduceMotion = root.getAttribute('data-motion') === 'off';
        var each = function (list, fn) { Array.prototype.forEach.call(list, fn); };
        var sel = function (v) { return window.CSS && CSS.escape ? CSS.escape(v) : v.replace(/[^\w-]/g, ''); };

        // ---------- 页面挂载期间注册到 document / window 上的监听和定时器，离开页面时统一清掉 ----------
        var scope = { listeners: [], intervals: [] };
        function scoped(fn) {
            var s = scope, dAdd = document.addEventListener, wAdd = window.addEventListener, sI = window.setInterval;
            document.addEventListener = function (t, h, o) { s.listeners.push([document, t, h, o]); return dAdd.call(document, t, h, o); };
            window.addEventListener = function (t, h, o) { s.listeners.push([window, t, h, o]); return wAdd.call(window, t, h, o); };
            window.setInterval = function () { var id = sI.apply(window, arguments); s.intervals.push(id); return id; };
            try { fn(); } finally {
                delete document.addEventListener;
                delete window.addEventListener;
                window.setInterval = sI;
            }
        }
        function part(fn) {
            try { fn(); } catch (err) { if (window.console) console.error(err); }
        }
        function unmount() {
            scope.listeners.forEach(function (l) { l[0].removeEventListener(l[1], l[2], l[3]); });
            scope.intervals.forEach(function (id) { clearInterval(id); });
            scope = { listeners: [], intervals: [] };
        }

        // ---------- 弹窗：关闭时先播放收起动画 ----------
        (function () {
            var proto = window.HTMLDialogElement && HTMLDialogElement.prototype;
            if (!proto || proto.otaPatched || reduceMotion) return;
            var nativeClose = proto.close, nativeShow = proto.showModal;
            proto.close = function (value) {
                var d = this;
                if (!d.open || d.classList.contains('closing')) return;
                d.classList.add('closing');
                var done = false;
                var finish = function () {
                    if (done) return;
                    done = true;
                    d.classList.remove('closing');
                    nativeClose.call(d, value);
                };
                d.otaCancelClose = function () { done = true; d.classList.remove('closing'); };
                d.addEventListener('animationend', finish, { once: true });
                setTimeout(finish, 240);
            };
            proto.showModal = function () {
                if (this.classList.contains('closing') && this.otaCancelClose) { this.otaCancelClose(); return; }
                return nativeShow.call(this);
            };
            proto.otaPatched = true;
        })();

        // ---------- 提示条 ----------
        var ICON = { ok: '<svg class="i"><use href="#i-check"/></svg>', err: '<svg class="i"><use href="#i-alert"/></svg>' };

        function dismiss(el) {
            if (!el || el.classList.contains('hide')) return;
            el.classList.add('hide');
            setTimeout(function () { el.remove(); }, 260);
        }
        function arm(el) {
            var left = el.classList.contains('err') ? 9000 : 5000, started = Date.now();
            el.style.setProperty('--life', left + 'ms');
            var timer = setTimeout(function () { dismiss(el); }, left);
            el.addEventListener('mouseenter', function () { clearTimeout(timer); left -= Date.now() - started; });
            el.addEventListener('mouseleave', function () {
                started = Date.now();
                timer = setTimeout(function () { dismiss(el); }, Math.max(900, left));
            });
        }
        function toast(msg, type) {
            var box = document.getElementById('toasts');
            if (!box) return;
            var el = document.createElement('div');
            el.className = 'toast' + (type === 'err' ? ' err' : '');
            el.setAttribute('role', type === 'err' ? 'alert' : 'status');
            el.innerHTML = (type === 'err' ? ICON.err : ICON.ok) + '<span></span><button type="button" data-toast-close aria-label="关闭">&times;</button>';
            el.querySelector('span').textContent = msg;
            box.appendChild(el);
            arm(el);
        }
        window.otaToast = toast;

        // 手机上对话框是底部面板：按住顶部往下拉就关掉
        (function () {
            var drag = null;
            document.addEventListener('touchstart', function (e) {
                var d = e.target.closest && e.target.closest('dialog[open]:not(#cmdk)');
                if (!d || d.hasAttribute('data-busy') || d.scrollTop > 0 || !matchMedia('(max-width:560px)').matches) return;
                if (e.touches[0].clientY - d.getBoundingClientRect().top > 72) return;
                drag = { d: d, y: e.touches[0].clientY, dy: 0 };
            }, { passive: true });
            document.addEventListener('touchmove', function (e) {
                if (!drag) return;
                drag.dy = Math.max(0, e.touches[0].clientY - drag.y);
                drag.d.style.transition = 'none';
                drag.d.style.transform = 'translateY(' + drag.dy + 'px)';
            }, { passive: true });
            var release = function () {
                if (!drag) return;
                var d = drag.d, far = drag.dy > 90;
                drag = null;
                d.style.transition = 'transform .22s cubic-bezier(.16,1,.3,1)';
                if (!far) {
                    d.style.transform = '';
                    setTimeout(function () { d.style.transition = ''; }, 240);
                    return;
                }
                d.style.transform = 'translateY(100%)';
                d.addEventListener('close', function () { d.style.transform = ''; d.style.transition = ''; }, { once: true });
                setTimeout(function () { d.close(); }, 200);
            };
            document.addEventListener('touchend', release);
            document.addEventListener('touchcancel', release);
        })();

        function openDialog(id) {
            var d = document.getElementById(id);
            if (d && (!d.open || d.classList.contains('closing'))) d.showModal();
            return d;
        }
        window.otaOpen = openDialog;

        // 弹窗打开时聚焦第一个输入框，触屏不做（会弹出键盘）
        (function () {
            var proto = window.HTMLDialogElement && HTMLDialogElement.prototype;
            if (!proto || proto.otaFocus) return;
            var show = proto.showModal;
            proto.showModal = function () {
                show.apply(this, arguments);
                if (this.id === 'cmdk' || this.querySelector('[autofocus]') || !(window.matchMedia && matchMedia('(pointer:fine)').matches)) return;
                var f = this.querySelector('.dlg-body input:not([type=hidden]):not([type=checkbox]):not([type=radio]):not([type=file]):not([type=range]):not([type=color]):not([disabled]):not([readonly]), .dlg-body textarea:not([disabled]):not([readonly])');
                if (f && f.getClientRects().length) {
                    try { f.focus({ preventScroll: true }); } catch (err) { f.focus(); }
                }
            };
            proto.otaFocus = true;
        })();

        document.addEventListener('wheel', function (e) {
            var t = e.target;
            if (t && t.tagName === 'INPUT' && t.type === 'number' && document.activeElement === t) t.blur();
        }, { passive: true });

        // 搜索框 Esc 清空，搜过的重新提交
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape' || e.isComposing) return;
            var t = e.target;
            if (!t || t.tagName !== 'INPUT' || t.type !== 'search' || !t.value || t.closest('#cmdk')) return;
            e.preventDefault();
            e.stopPropagation();
            var f = t.form, searched = t.defaultValue !== '' && f && (f.getAttribute('method') || 'get').toLowerCase() === 'get';
            t.value = '';
            t.dispatchEvent(new Event('input', { bubbles: true }));
            if (searched) { if (f.requestSubmit) f.requestSubmit(); else f.submit(); }
        }, true);

        (function () {
            function tip(input, on) {
                var box = input.closest('.input-group, .pw-field') || input;
                var t = box.nextElementSibling;
                if (!t || !t.classList.contains('caps-tip')) {
                    if (!on) return;
                    t = document.createElement('span');
                    t.className = 'caps-tip';
                    t.innerHTML = '<svg class="i i-sm"><use href="#i-alert"/></svg>大写锁定已打开';
                    box.after(t);
                }
                t.hidden = !on;
            }
            ['keydown', 'keyup'].forEach(function (ev) {
                document.addEventListener(ev, function (e) {
                    var t = e.target;
                    if (t && t.tagName === 'INPUT' && t.type === 'password' && e.getModifierState) tip(t, e.getModifierState('CapsLock'));
                }, true);
            });
            document.addEventListener('focusout', function (e) {
                var t = e.target;
                if (t && t.tagName === 'INPUT' && t.type === 'password') tip(t, false);
            }, true);
        })();

        (function () {
            var bar = null;
            function offline(off) {
                if (off && !bar) {
                    bar = document.createElement('div');
                    bar.className = 'offline-bar';
                    bar.setAttribute('role', 'status');
                    bar.innerHTML = '<svg class="i i-sm"><use href="#i-alert"/></svg><span>网络断开了，恢复后会自动连上</span>';
                    document.body.appendChild(bar);
                } else if (!off && bar) {
                    bar.remove();
                    bar = null;
                    toast('网络已恢复');
                }
            }
            window.addEventListener('offline', function () { offline(true); });
            window.addEventListener('online', function () { offline(false); });
            if (navigator.onLine === false) offline(true);
        })();

        (function () {
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'to-top';
            btn.title = '回到顶部';
            btn.setAttribute('aria-label', '回到顶部');
            btn.innerHTML = '<svg class="i"><use href="#i-chevron-down"/></svg>';
            btn.hidden = true;
            document.body.appendChild(btn);
            var ticking = false;
            function check() {
                ticking = false;
                var on = window.scrollY > Math.max(600, innerHeight * 1.2) && !!document.querySelector('.sidebar, .agentbar');
                if (btn.hidden === on) btn.hidden = !on;
            }
            window.addEventListener('scroll', function () { if (!ticking) { ticking = true; requestAnimationFrame(check); } }, { passive: true });
            btn.addEventListener('click', function () { window.scrollTo({ top: 0, behavior: reduceMotion ? 'auto' : 'smooth' }); });
        })();

        // 离开前提醒没保存的改动，只算动过的表单
        var touched = new WeakSet();
        function touch(e) {
            var f = e.target && e.target.form;
            if (f && (f.getAttribute('method') || 'get').toLowerCase() === 'post' && !f.closest('dialog') && !f.hasAttribute('data-no-dirty')) touched.add(f);
        }
        document.addEventListener('input', touch, true);
        document.addEventListener('change', touch, true);
        document.addEventListener('submit', function (e) { touched.delete(e.target); }, true);
        function edited(f) {
            return Array.prototype.some.call(f.elements, function (el) {
                if (el.disabled || !el.name || el.type === 'hidden' || el.type === 'file' || el.type === 'password') return false;
                if (el.type === 'checkbox' || el.type === 'radio') return el.checked !== el.defaultChecked;
                if (el.tagName === 'SELECT') return Array.prototype.some.call(el.options, function (o) { return o.selected !== o.defaultSelected; });
                return el.value !== el.defaultValue;
            });
        }
        function unsaved() {
            var list = document.querySelectorAll('form');
            for (var i = 0; i < list.length; i++) if (touched.has(list[i]) && edited(list[i])) return list[i];
            return null;
        }
        function confirmLeave(run) {
            var f = unsaved();
            if (!f) return run();
            ask('有改动还没保存', '离开这个页面，刚才改的内容不会保存。', '仍然离开', function () { touched.delete(f); run(); });
        }
        window.addEventListener('beforeunload', function (e) {
            if (!unsaved()) return;
            e.preventDefault();
            e.returnValue = '';
        });

        document.addEventListener('click', function (e) {
            var t = e.target;
            var x = t.closest('[data-toast-close]');
            if (x) { dismiss(x.closest('.toast')); return; }
            var opener = t.closest('[data-open]');
            if (opener) { e.preventDefault(); openDialog(opener.getAttribute('data-open')); return; }
            var closer = t.closest('[data-close]');
            if (closer) { e.preventDefault(); closer.closest('dialog').close(); return; }
            var copyBtn = t.closest('[data-copy]');
            if (copyBtn) { copy(copyBtn.getAttribute('data-copy').split('{origin}').join(location.origin), copyBtn.getAttribute('data-copy-label'), copyBtn); return; }
            var preBtn = t.closest('.code-copy, [data-copy-pre]');
            var pre = preBtn && preBtn.closest('.code') && preBtn.closest('.code').querySelector('pre');
            if (pre) { copy(pre.innerText.replace(/\u00a0/g, ' '), '已复制', preBtn); return; }
            var rnd = t.closest('[data-random]');
            if (rnd) {
                var input = document.querySelector(rnd.getAttribute('data-random'));
                var buf = new Uint32Array(1);
                crypto.getRandomValues(buf);
                input.value = String(100000 + buf[0] % 900000);
                input.focus();
                return;
            }
            if (t.closest('[data-nav-toggle]')) { document.body.classList.toggle('nav-open'); return; }
            if (document.body.classList.contains('nav-open') && !t.closest('.sidebar')) document.body.classList.remove('nav-open');
        });

        // 危险操作统一走确认弹窗：<form data-confirm="标题" data-confirm-body="说明" data-confirm-ok="按钮">
        var pendingForm = null, pendingRun = null;
        function ask(title, body, ok, run) {
            var cd = document.getElementById('confirm-dialog');
            if (!cd) { if (window.confirm(title)) run(); return; }
            pendingForm = null;
            pendingRun = run;
            cd.querySelector('[data-role=title]').textContent = title;
            cd.querySelector('[data-role=body]').textContent = body;
            var okBtn = cd.querySelector('[data-role=ok]');
            okBtn.textContent = ok;
            okBtn.className = 'btn btn-danger';
            cd.showModal();
        }
        window.otaAsk = ask;
        document.addEventListener('submit', function (e) {
            var f = e.target;
            if (!f.hasAttribute('data-confirm')) return;
            e.preventDefault();
            var cd = document.getElementById('confirm-dialog');
            if (!cd) return;
            pendingRun = null;
            pendingForm = f;
            cd.querySelector('[data-role=title]').textContent = f.getAttribute('data-confirm');
            cd.querySelector('[data-role=body]').textContent = f.getAttribute('data-confirm-body') || '';
            var okBtn = cd.querySelector('[data-role=ok]');
            okBtn.textContent = f.getAttribute('data-confirm-ok') || '确定';
            okBtn.className = 'btn' + (f.hasAttribute('data-confirm-danger') ? ' btn-danger' : '');
            cd.showModal();
        });
        document.addEventListener('click', function (e) {
            if (!e.target.closest('#confirm-dialog [data-role=ok]')) return;
            if (pendingRun) {
                var run = pendingRun;
                pendingRun = null;
                document.getElementById('confirm-dialog').close();
                run();
                return;
            }
            if (!pendingForm) return;
            var f = pendingForm;
            pendingForm = null;
            var sb = f.querySelector('button[type=submit].btn, button:not([type]).btn');
            if (sb) sb.classList.add('loading');
            document.getElementById('confirm-dialog').close();
            touched.delete(f);
            f.submit();
        });

        // 普通表单提交后按钮转圈，防止连点重复提交
        document.addEventListener('submit', function (e) {
            if (e.defaultPrevented) return;
            var f = e.target;
            if ((f.getAttribute('method') || 'get').toLowerCase() !== 'post' || f.hasAttribute('data-no-loading')) return;
            var b = e.submitter;
            if (!b || !b.classList || !b.classList.contains('btn')) return;
            setTimeout(function () { b.classList.add('loading'); }, 0);
            setTimeout(function () { b.classList.remove('loading'); }, 8000);
        });
        window.addEventListener('pageshow', function () {
            each(document.querySelectorAll('.btn.loading'), function (b) { b.classList.remove('loading'); });
        });

        // 不是 HTTPS 时浏览器不给 Clipboard API，只能用 execCommand；开着弹窗时弹窗外的元素都选不中，临时输入框要放进弹窗里
        function copy(text, label, btn) {
            function done() {
                toast(label || '已复制 ' + text);
                if (btn && btn.hasAttribute('data-token-copy')) btn.innerHTML = '<svg class="i i-sm"><use href="#i-tick"/></svg>已复制';
                if (btn && btn.classList.contains('code-copy')) {
                    clearTimeout(btn.otaCopyTimer);
                    btn.textContent = '已复制';
                    btn.otaCopyTimer = setTimeout(function () { btn.textContent = '复制'; }, 1500);
                }
                var use = btn && btn.classList.contains('icon-btn') && btn.querySelector('use');
                if (use) {
                    if (!btn.hasAttribute('data-icon-was')) btn.setAttribute('data-icon-was', use.getAttribute('href'));
                    use.setAttribute('href', '#i-tick');
                    btn.classList.add('done');
                    clearTimeout(btn.otaCopyTimer);
                    btn.otaCopyTimer = setTimeout(function () {
                        use.setAttribute('href', btn.getAttribute('data-icon-was'));
                        btn.classList.remove('done');
                    }, 1400);
                }
            }
            // 都复制不了：把原文选中，让用户自己按快捷键
            function manual() {
                var src = btn && (btn.closest('.code') ? btn.closest('.code').querySelector('pre')
                    : btn.parentElement && btn.parentElement.querySelector('[data-token-value], code, .mono'));
                if (src) {
                    var range = document.createRange();
                    range.selectNodeContents(src);
                    var sel = window.getSelection();
                    sel.removeAllRanges();
                    sel.addRange(range);
                }
                var keys = /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent || '') ? '⌘C' : 'Ctrl C';
                toast(src ? '浏览器不让网页直接复制，已经帮你选中了，按 ' + keys + ' 复制' : '浏览器不让网页直接复制，请手动选中复制', 'err');
            }
            function legacy() {
                var host = (btn && btn.closest('dialog[open]')) || document.querySelector('dialog[open]') || document.body;
                var back = document.activeElement;
                var ta = document.createElement('textarea');
                ta.value = text;
                ta.setAttribute('readonly', '');
                ta.style.cssText = 'position:fixed;top:0;left:0;width:1px;height:1px;padding:0;border:0;opacity:0;pointer-events:none';
                host.appendChild(ta);
                ta.focus({ preventScroll: true });
                ta.select();
                ta.setSelectionRange(0, text.length);
                var ok = false;
                try { ok = document.execCommand('copy'); } catch (err) { ok = false; }
                ta.remove();
                if (back && back.focus) try { back.focus({ preventScroll: true }); } catch (err) { /* 焦点还不回去就算了 */ }
                if (ok) done(); else manual();
            }
            if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(text).then(done, legacy);
            else legacy();
        }
        window.otaCopy = copy;

        // ---------- 外观：浅色 / 深色 / 跟随系统、侧栏配色、信息密度。只存在这台电脑的浏览器里 ----------
        // 侧栏没选过就用系统配置里的默认（--side-style）；选了和默认一样的就是跟着系统走。以前版本存的选择都作废（sv 不是 3）
        var darkQuery = window.matchMedia ? matchMedia('(prefers-color-scheme: dark)') : null;
        function sideStyle() {
            var prev = root.getAttribute('data-sidebar');
            if (prev) root.removeAttribute('data-sidebar');
            var v = getComputedStyle(root).getPropertyValue('--side-style').trim() || 'theme';
            if (prev) root.setAttribute('data-sidebar', prev);
            return v;
        }
        function lookGet() {
            try { return JSON.parse(localStorage.getItem('ota.ui') || '{}') || {}; } catch (e) { return {}; }
        }
        function lookPaint() {
            var u = lookGet(), mode = u.mode === 'dark' || u.mode === 'system' ? u.mode : 'light';
            var dark = mode === 'dark' || (mode === 'system' && !!darkQuery && darkQuery.matches);
            root.setAttribute('data-mode', dark ? 'dark' : 'light');
            var side = u.sv === 3 ? u.sidebar : '';
            if (side === 'theme' || side === 'dark' || side === 'light') root.setAttribute('data-sidebar', side); else root.removeAttribute('data-sidebar');
            if (u.density === 'compact') root.setAttribute('data-density', 'compact'); else root.removeAttribute('data-density');
            if (u.side === 'mini') root.setAttribute('data-side', 'mini'); else root.removeAttribute('data-side');
            if (u.tabs === 'hide') root.setAttribute('data-tabbar', 'hide'); else root.removeAttribute('data-tabbar');
            if (u.motion === 'off') root.setAttribute('data-motion', 'off'); else root.removeAttribute('data-motion');
            reduceMotion = u.motion === 'off';
            var sideDefault = sideStyle();
            var cur = { mode: mode, sidebar: root.getAttribute('data-sidebar') || sideDefault,
                density: u.density === 'compact' ? 'compact' : 'comfortable',
                tabs: u.tabs === 'hide' ? 'hide' : 'show', motion: u.motion === 'off' ? 'off' : 'on' };
            each(document.querySelectorAll('[data-look=sidebar] [data-v]'), function (b) { b.classList.toggle('is-default', b.getAttribute('data-v') === sideDefault); });
            each(document.querySelectorAll('[data-look]'), function (g) {
                var v = cur[g.getAttribute('data-look')];
                each(g.querySelectorAll('[data-v]'), function (b) {
                    var on = b.getAttribute('data-v') === v;
                    b.classList.toggle('on', on);
                    b.setAttribute('aria-pressed', on ? 'true' : 'false');
                });
            });
            each(document.querySelectorAll('[data-look-icon]'), function (use) {
                use.setAttribute('href', mode === 'system' ? '#i-monitor' : dark ? '#i-moon' : '#i-sun');
            });
        }
        function lookSet(key, value, origin) {
            var u = lookGet();
            u[key] = value;
            if (key === 'sidebar') {
                if (value === sideStyle()) { delete u.sidebar; delete u.sv; } else u.sv = 3;
            }
            try { localStorage.setItem('ota.ui', JSON.stringify(u)); } catch (e) {}
            var change = {};
            change[key] = key === 'sidebar' ? (u.sidebar || '') : (value || '');
            if (window.otaPrefs) window.otaPrefs(change);
            var before = root.getAttribute('data-mode');
            var willDark = value === 'dark' || (value === 'system' && !!darkQuery && darkQuery.matches);
            if (key !== 'mode' || !origin || before === (willDark ? 'dark' : 'light') || !document.startViewTransition || reduceMotion) {
                lookPaint();
                return;
            }
            // 换深浅色时从点击的位置铺开一个圆
            var r = origin.getBoundingClientRect();
            var x = r.left + r.width / 2, y = r.top + r.height / 2;
            var end = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
            root.classList.add('theme-switch');
            var vt = document.startViewTransition(lookPaint);
            vt.ready.then(function () {
                root.animate({ clipPath: ['circle(0px at ' + x + 'px ' + y + 'px)', 'circle(' + end + 'px at ' + x + 'px ' + y + 'px)'] },
                    { duration: 560, easing: 'cubic-bezier(.65,0,.35,1)', pseudoElement: '::view-transition-new(root)' });
            }).catch(function () {});
            vt.finished.then(function () { root.classList.remove('theme-switch'); }, function () { root.classList.remove('theme-switch'); });
        }
        lookPaint();
        if (darkQuery) {
            var onScheme = function () { if (lookGet().mode === 'system') lookPaint(); };
            if (darkQuery.addEventListener) darkQuery.addEventListener('change', onScheme); else if (darkQuery.addListener) darkQuery.addListener(onScheme);
        }
        window.addEventListener('storage', function (e) { if (e.key === 'ota.ui') lookPaint(); });
        document.addEventListener('click', function (e) {
            var b = e.target.closest('[data-look] [data-v]');
            if (!b) return;
            lookSet(b.closest('[data-look]').getAttribute('data-look'), b.getAttribute('data-v'), b);
        });

        // ---------- 顶栏弹出菜单 ----------
        // 表格/卡片里的 menu-float 挂到 body，避免被下一行或 overflow 挡住点不到
        function popMenuOf(p) {
            return (p && p._popMenu) || (p && p.querySelector('[data-pop-menu]')) || null;
        }
        function popHome(m) {
            if (!m || !m._popHome) return;
            var home = m._popHome, owner = m._popOwner;
            if (home.parent && home.parent.isConnected) {
                if (home.next && home.next.parentNode === home.parent) home.parent.insertBefore(m, home.next);
                else home.parent.appendChild(m);
            } else if (m.parentNode) {
                m.parentNode.removeChild(m);
            }
            m.style.top = '';
            m.style.left = '';
            m._popHome = null;
            m._popOwner = null;
            if (owner && owner._popMenu === m) owner._popMenu = null;
        }
        function popClose(except) {
            each(document.querySelectorAll('[data-pop]'), function (p) {
                if (p === except) return;
                var m = popMenuOf(p), b = p.querySelector('[data-pop-toggle]');
                if (m && !m.hidden) {
                    m.hidden = true;
                    if (b) b.setAttribute('aria-expanded', 'false');
                    if (m.classList.contains('menu-float')) popHome(m);
                }
            });
            each(document.querySelectorAll('body > .menu-float[data-pop-menu]:not([hidden])'), function (m) {
                if (except && m._popOwner === except) return;
                m.hidden = true;
                popHome(m);
            });
        }
        document.addEventListener('click', function (e) {
            var tog = e.target.closest('[data-pop-toggle]');
            if (tog) {
                var p = tog.closest('[data-pop]'), m = popMenuOf(p);
                popClose(p);
                m.hidden = !m.hidden;
                tog.setAttribute('aria-expanded', m.hidden ? 'false' : 'true');
                if (!m.hidden && m.classList.contains('menu-float')) popPlace(tog, m, p);
                else if (m.hidden && m.classList.contains('menu-float')) popHome(m);
                return;
            }
            var inMenu = e.target.closest('[data-pop-menu]');
            if (!inMenu || e.target.closest('a, .menu-item')) popClose(null);
        });
        // 卡片、表格里的菜单（menu-float）挂到 body + fixed，贴着按钮；下面放不下就往上开
        function popPlace(tog, m, owner) {
            if (!m._popHome) {
                m._popHome = { parent: m.parentNode, next: m.nextSibling };
                m._popOwner = owner || tog.closest('[data-pop]');
                if (m._popOwner) m._popOwner._popMenu = m;
            }
            if (m.parentNode !== document.body) document.body.appendChild(m);
            var r = tog.getBoundingClientRect(), h = m.offsetHeight, w = m.offsetWidth;
            var top = r.bottom + 6;
            if (top + h > innerHeight - 8 && r.top - 6 - h > 8) top = r.top - 6 - h;
            m.style.top = Math.round(top) + 'px';
            m.style.left = Math.round(Math.max(8, Math.min(r.right - w, innerWidth - w - 8))) + 'px';
        }
        window.addEventListener('scroll', function () {
            if (document.querySelector('.menu-float[data-pop-menu]:not([hidden])')) popClose(null);
        }, true);
        window.addEventListener('resize', function () { popClose(null); });
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') return;
            var open = document.querySelector('[data-pop-menu]:not([hidden])');
            if (!open) return;
            var owner = open._popOwner || open.closest('[data-pop]');
            popClose(null);
            var b = owner && owner.querySelector('[data-pop-toggle]');
            if (b) b.focus();
        });

        // ---------- 引导 ----------
        var side = null, agentTop = null, page = null, seen = {}, seenLocal = {}, pageKey = null, welcomeTried = false, mountSeq = 0;
        function guidePage() {
            side = document.querySelector('.sidebar[data-page]');
            agentTop = document.querySelector('.agent-top');
            page = side ? side.getAttribute('data-page') : agentTop ? 'agent' : null;
            seen = {};
            if (side) {
                (side.getAttribute('data-guide-seen') || '').split(',').forEach(function (k) { if (k) seen[k] = true; });
            } else if (agentTop) {
                try { (localStorage.getItem('ota.guide.agent') || '').split(',').forEach(function (k) { if (k) seen[k] = true; }); } catch (e) {}
            }
            Object.keys(seenLocal).forEach(function (k) { seen[k] = true; });
            pageKey = page === 'agent' ? 'agent' : 'page:' + page;
        }

        var isMac = /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent || '');
        var MOD = isMac ? '⌘K' : 'Ctrl K';

        function brandName() { var b = document.querySelector('.brand > span'); return b && b.firstChild ? b.firstChild.textContent.trim() : '管理后台'; }
        var WELCOME = [
            { welcome: true, title: '欢迎使用 ' + brandName(), body: '热更新、口令和代理都在这里管。花一分钟认识一下各个区域。',
              feats: '<div class="gw-feats">'
                + '<div><span class="gw-ic"><svg class="i"><use href="#i-layers"/></svg></span><span><b>热更新</b><span>上传即发布，支持灰度、回滚和增量更新</span></span></div>'
                + '<div><span class="gw-ic"><svg class="i"><use href="#i-key"/></svg></span><span><b>口令激活</b><span>A / B 面一键切换，激活数据实时统计</span></span></div>'
                + '<div><span class="gw-ic"><svg class="i"><use href="#i-users"/></svg></span><span><b>多人协作</b><span>按 App 分配权限，每一步操作都有记录</span></span></div></div>' },
            { el: '[data-app-switch]', side: 'right', title: '先选 App', body: '左上角是当前 App，版本、设备这些页面只显示它的数据，口令所有 App 通用。点开可以搜索、切换或新建。' },
            { el: '.sidebar .nav', side: 'right', pad: 4, title: '菜单按工作分组', body: '运营管口令、激活和代理，发布管版本和设备，App 管设置和签名，系统里是所有 App、管理员和日志。' },
            { el: '.top-status', side: 'bottom', title: '状态栏', body: '在 A 面还是 B 面、签名正不正常，每个页面顶上都有，点一下就到对应设置。' },
            { el: '[data-guide=todo]', side: 'bottom', title: '待处理', body: '口令申请、启动失败这些要你处理的事，铃铛上会有数字。' },
            { el: '[data-guide=search]', side: 'bottom', title: '搜索', body: '按 ' + MOD + ' 跳页面、切 App、新建口令、上传更新包，不用一层层点菜单。' },
            { el: '[data-guide=look]', side: 'bottom', title: '外观', body: '浅色、深色或跟随系统，侧栏和密度也能调，只影响你自己。' },
            { el: '[data-guide=help]', side: 'bottom', title: '本页引导', body: '每个页面都有自己的引导，从这里打开，这段导览也能重看。' },
            { icon: 'i-check', title: '准备好了', body: '先看概览上的「待处理」和「开始使用」，照着做就行。', cta: '开始使用' }
        ];
        var TOURS = {
            overview: [
                { el: '[data-guide=ov-todo]', title: '待处理', body: '口令申请、启动失败、签名、备份这些要你处理的事都在这里，处理完自己消失。' },
                { el: '[data-guide=todo]', side: 'bottom', title: '铃铛', body: '在别的页面，点这里也能看到待处理。' },
                { el: '[data-guide=ov-mode]', title: '运行模式', body: 'A 面给审核看，B 面给用户用。切换前会再确认一次。' },
                { el: '[data-guide=ov-steps]', title: '开始使用', body: '新 App 照着这几步接入，做完就收起来了。' },
                { el: '[data-guide=ov-codes]', title: '今天', body: '今天的有效激活和每小时分布，旁边是活跃代理、用到的口令和待审核。页面开着会每 30 秒自动刷新。' },
                { el: '[data-guide=ov-hot]', title: '热更新', body: '最新版本装了多少、覆盖了多少设备、有没有崩溃。点卡片看明细。' },
                { el: '[data-guide=ov-live]', title: '线上版本', body: '每个渠道正在推的版本，灰度中会标出比例。' },
                { el: '[data-board-edit]', side: 'bottom', title: '调整看板', body: '拖动卡片换位置，拖右边缘改宽度，收起用不上的；「添加卡片」里还有计划任务、激活时段、今日目标、便签等 30 多张。只影响你自己。' }
            ],
            releases: [
                { el: '.page-head .page-actions', title: '发布新版本', body: '上传 npx expo export 导出的 dist 压缩包，或者填 CDN 上的 dist 目录。用命令行 ota publish 更省事。' },
                { el: '[data-guide=rel-live]', title: '线上版本', body: '每个渠道正在推的版本。可以调灰度、推到别的渠道，出问题马上回滚。' },
                { el: '[data-guide=rel-list]', title: '版本列表', body: '按 Runtime 分组。点「详情」看文件和启动错误，「安装」是装上 / 推送的设备数。' }
            ],
            codes: [
                { el: '.page-head .page-actions', title: '新建口令', body: '单个或批量建，可以设次数、有效期，直接分给代理。' },
                { el: '.content .toolbar', title: '筛选和搜索', body: '按状态、代理筛选，或者搜口令和备注。' },
                { el: '[data-guide=codes-table]', title: '口令列表', body: '「有效激活」是进了 B 面的设备数。勾选多行可以批量启用、停用、换代理或删除。' }
            ],
            activations: [
                { el: '.content .toolbar', title: '筛选记录', body: '按状态、代理、口令和时间筛选。' },
                { el: '[data-guide=act-table]', title: '激活记录', body: '按状态、代理、App、时间筛选，点设备 ID 只看这台设备。导出的 CSV 就是对账明细。' },
                { el: '.page-head .page-actions', title: '导出', body: '导出当前筛选的记录，Excel 直接打开，拿去和代理对账。' }
            ],
            agents: [
                { el: '.page-head .page-actions', title: '新建代理', body: '建好账号，口令可以你来分，也可以让 TA 自己申请。口令所有 App 通用。' },
                { el: '[data-tab=requests]', side: 'bottom', title: '口令申请', body: '代理提交的申请在这里审核，可以批量同意，也可以按口令挑着同意。' },
                { el: '[data-guide=agents-table]', title: '代理列表', body: '点行尾 ⋯ 里的「选择」可以勾选多个，一起设置 App、申请方式和启用停用。「预览」用代理的视角看 TA 的后台。' }
            ],
            devices: [
                { el: '.content .metrics', title: '设备概况', body: '近 30 天检查过更新的设备，有多少已经在线上版本。' },
                { el: '[data-guide=dev-dist]', title: '版本分布', body: '每台设备在用哪个版本，没进灰度的设备还在上一版。' },
                { el: '[data-guide=dev-table]', title: '设备列表', body: '搜设备 ID 看版本、渠道和最近一次检查。自己的手机可以设成测试设备，先收测试版本。' },
                { el: '.page-head .page-actions', title: '收不到更新？', body: '点「模拟检查更新」，按这台设备的情况走一遍，告诉你它会拿到哪个版本、为什么。' }
            ],
            app: [
                { el: '[data-tabs]', title: 'App 设置', body: '接入、渠道和测试设备、发布令牌、口令接口、通知、备份，按标签切换。' },
                { el: '[data-tabs] [data-tab=channels]', side: 'bottom', title: '渠道与测试设备', body: '设备默认走 production。登记成测试设备的手机走测试渠道，先验证再推给所有人。' },
                { el: '[data-tabs] [data-tab=tokens]', side: 'bottom', title: '发布令牌', body: '给 ota publish 和 CI 用，只在创建时显示一次。' },
                { el: '[data-tabs] [data-tab=webhook]', side: 'bottom', title: '通知', body: '发布、回滚、启动失败偏多时，推到企业微信、钉钉、飞书或你的 Webhook。' }
            ],
            signing: [
                { el: '[data-guide=sign-status]', title: '签名状态', body: '更新包用私钥签名，App 用内置证书验证，防篡改。这里看密钥和最近版本的签名对不对。' },
                { el: '[data-guide=sign-use]', title: '在 App 里使用', body: '下载证书放进 App 项目，复制 app.json 里的签名配置，重新打包。' },
                { el: '.page-head .page-actions', title: '配置密钥', body: '上传或生成签名密钥。私钥只能上传、不能下载，换密钥前先发带新证书的安装包。' }
            ],
            apps: [
                { el: '.content .metrics', title: '汇总', body: '你能管的所有 App 的设备数和激活数。' },
                { el: 'html:not([data-apps-view=list]) .app-cards, html[data-apps-view=list] .app-list', title: 'App 卡片', body: '每个 App 的模式、线上版本和最近数据，点「进入」切换过去。' },
                { el: '.page-head .page-actions', title: '新建或恢复', body: '新建 App，或者从备份恢复。换服务器时把备份传上来，App 标识和密钥都不变。' }
            ],
            admins: [
                { el: '.page-head .page-actions', title: '添加成员', body: '给同事建账号，按 App 授权：只看，或者能发布、切 A/B 面、改设置和签名。管口令、管代理另外勾。' },
                { el: '[data-guide=adm-table]', title: '成员和权限', body: '谁能操作哪些 App、能做什么都在这。改权限马上生效，停用的账号会马上退出。' }
            ],
            logs: [
                { el: '.content .toolbar', title: '筛选日志', body: '按类型、App 筛选，或者搜操作内容和操作人。' },
                { el: '[data-guide=logs-table]', title: '操作日志', body: '发布、回滚、切换模式、改口令、登录都会记下来。' }
            ],
            settings: [
                { el: '[data-tabs]', title: '系统配置', body: '外观、登录安全、管理令牌、存储和域名，对所有人生效。' },
                { el: '[data-tabs] [data-tab=appearance]', side: 'bottom', title: '主题色和圆角', body: '后台和代理后台一起换。深色模式这类个人偏好在右上角。' },
                { el: '[data-tabs] [data-tab=storage]', side: 'bottom', title: '文件存储', body: '默认存在服务器本地，也可以换阿里云 OSS、腾讯云 COS 这些对象存储，配 CDN 下载更快。' }
            ],
            docs: [
                { el: '[data-guide=docs-toc]', side: 'right', title: '目录', body: '接入步骤、清单协议、口令接口、管理接口和命令行，点了直接跳过去。' },
                { el: '.page-head .page-actions', title: '下载文档', body: '下载 Markdown 版发给 App 开发，地址和 App 标识都填好了。' }
            ],
            account: [
                { el: '[data-guide=acc-form]', title: '我的账号', body: '改名字、账号和密码，改了账号或密码要重新登录。' },
                { el: '.acct [data-tabs]', side: 'bottom', title: '分成几个标签', body: '登录与安全里开两步验证、管登录设备；我的权限看你在每个 App 上能做什么；偏好里换主题、侧栏、语言。' }
            ],
            agent: [
                { welcome: true, title: '欢迎使用代理后台', body: '你的口令带来的激活都在这里，花 20 秒认识一下。', cta: '看看' },
                { el: '.agent-tabs', side: 'bottom', title: '导航', body: '「数据概览」看汇总和趋势。开通了的话，「申请口令」能自己申请，「激活记录」能看每一条。' },
                { el: '.agent-tabs a[href$="/subs"]', side: 'bottom', title: '二级代理', body: '在这里给下级开账号、分口令，扣量和数据更新方式都能单独设。TA 带来的激活照常算在你名下。' },
                { el: '.content .kpis', title: '核心数据', body: '今天、本月、近 7 天的有效激活（进了 App 的设备数），还有结算看的 7 日留存。' },
                { el: '.content .vchart', title: '趋势', body: '每天的有效激活，鼠标停在柱子上看具体数字；右上角换时间段。' },
                { el: '.agent-right .chip', side: 'bottom', title: '数据时间', body: '数据是实时的，还是统计到某个时间点。' },
                { el: '[data-guide=look]', side: 'bottom', title: '深色模式', body: '晚上可以切到深色，只影响你自己。', cta: '知道了' }
            ]
        };

        function markSeen(key) {
            if (!key || seen[key]) return;
            seen[key] = true;
            seenLocal[key] = true;
            if (side) {
                fetch('/admin/account/guide', {
                    method: 'POST',
                    headers: { 'X-Requested-With': 'XMLHttpRequest' },
                    body: new URLSearchParams({ key: key })
                }).catch(function () {});
            } else {
                try { localStorage.setItem('ota.guide.agent', Object.keys(seen).join(',')); } catch (e) {}
            }
            syncGuideUi();
        }

        var guide = (function () {
            var wrap, dim, hole, pop, steps = null, idx = -1, key, onEnd, lastFocus, raf = 0, cur = null, tween = 0;
            function build() {
                wrap = document.createElement('div');
                wrap.className = 'guide';
                wrap.innerHTML = '<div class="guide-dim"></div><div class="guide-hole"></div><div class="guide-pop" role="dialog" aria-modal="true" aria-labelledby="guide-title"></div>';
                document.body.appendChild(wrap);
                dim = wrap.children[0];
                hole = wrap.children[1];
                pop = wrap.children[2];
                pop.addEventListener('click', function (e) {
                    var b = e.target.closest('[data-g]');
                    if (!b) return;
                    var a = b.getAttribute('data-g');
                    if (a === 'next') go(idx + 1);
                    else if (a === 'prev') go(idx - 1);
                    else end(a === 'done');
                });
            }
            function usable(s) {
                if (!s.el) return true;
                var t = document.querySelector(s.el);
                if (!t) return false;
                var r = t.getBoundingClientRect();
                return r.width >= 4 && r.height >= 4 && r.right > 0 && r.left < innerWidth;
            }
            function start(k, list, done) {
                if (steps) return;
                var picked = list.filter(usable);
                if (!picked.length || (picked.length === 1 && !picked[0].el && list.length > 1)) return;
                if (!wrap || !wrap.isConnected) build();
                steps = picked;
                key = k;
                onEnd = done;
                idx = -1;
                lastFocus = document.activeElement;
                document.body.classList.remove('nav-open');
                popClose(null);
                wrap.classList.add('on');
                document.addEventListener('keydown', keys, true);
                window.addEventListener('resize', reflow);
                window.addEventListener('scroll', reflow, true);
                go(0);
            }
            function go(i) {
                if (!steps || i < 0 || i >= steps.length) return;
                var first = idx === -1;
                idx = i;
                var s = steps[i], t = s.el ? document.querySelector(s.el) : null;
                render(s, i);
                if (t && innerWidth <= 560) {
                    var rm = t.getBoundingClientRect();
                    if (rm.top < 70 || rm.top > innerHeight * 0.42) window.scrollBy({ top: rm.top - 84, behavior: 'instant' });
                } else if (t) {
                    var r = t.getBoundingClientRect();
                    if (r.top < 108 || r.bottom > innerHeight - 24) {
                        t.scrollIntoView({ block: r.height > innerHeight * 0.6 ? 'start' : 'center', behavior: 'instant' });
                        if (r.height > innerHeight * 0.6) window.scrollBy(0, -114);
                    }
                }
                place(first);
            }
            function greet() {
                var h = new Date().getHours(), who = document.querySelector('.side-user-text b');
                var hi = h < 5 ? '夜深了' : h < 11 ? '早上好' : h < 13 ? '中午好' : h < 18 ? '下午好' : '晚上好';
                var name = who ? who.textContent.trim().replace(/[&<>"']/g, function (c) { return '&#' + c.charCodeAt(0) + ';'; }) : '';
                return hi + (name ? '，' + name : '');
            }
            function render(s, i) {
                var n = steps.length, last = i === n - 1;
                pop.classList.toggle('welcome', !!s.welcome);
                if (s.welcome) {
                    pop.innerHTML = '<div class="gw-hero"><span class="gw-blob a"></span><span class="gw-blob b"></span><span class="gw-grid"></span>'
                        + '<span class="gw-mark"><svg class="gw-logo"><use href="#i-logo"/></svg><span class="gw-orbit"></span></span></div>'
                        + '<div class="gw-body"><div class="gw-kicker">' + greet() + '</div><h4 id="guide-title">' + s.title + '</h4><p>' + s.body + '</p>' + (s.feats || '')
                        + '<div class="gw-foot"><button type="button" class="guide-skip" data-g="skip">以后再说</button>'
                        + (n > 1 ? '<span class="gw-time">' + (n - 1) + ' 个区域 · 约 1 分钟</span>' : '')
                        + '<button type="button" class="btn" data-g="' + (last ? 'done' : 'next') + '">' + (s.cta || '开始导览') + '<svg class="i i-sm"><use href="#i-arrow"/></svg></button></div></div>';
                } else {
                    var bars = '';
                    if (n > 1) for (var d = 0; d < n; d++) bars += '<i' + (d < i ? ' class="done"' : d === i ? ' class="on"' : '') + '></i>';
                    pop.innerHTML = '<div class="gs-top">' + (n > 1 ? '<div class="gs-bars">' + bars + '</div><span class="gs-count">' + (i + 1) + '/' + n + '</span>' : '<div class="gs-bars"></div>')
                        + '<button type="button" class="gs-x" data-g="skip" aria-label="退出引导" title="退出引导（Esc）"><svg class="i"><use href="#i-x"/></svg></button></div>'
                        + (s.icon ? '<span class="gs-icon"><svg class="i"><use href="#' + s.icon + '"/></svg></span>' : '')
                        + '<h4 id="guide-title">' + s.title + '</h4><p>' + s.body + '</p>'
                        + '<div class="guide-foot">'
                        + (i > 0 ? '<button type="button" class="btn btn-ghost btn-sm" data-g="prev"><svg class="i i-sm"><use href="#i-left"/></svg>上一步</button>'
                                 : (n > 1 ? '<span class="gs-keys"><kbd>←</kbd><kbd>→</kbd>切换</span>' : ''))
                        + '<button type="button" class="btn btn-sm" data-g="' + (last ? 'done' : 'next') + '">' + (last ? (s.cta || '完成') : '下一步<svg class="i i-sm"><use href="#i-arrow"/></svg>') + '</button></div>';
                }
                pop.classList.remove('swap');
                void pop.offsetWidth;
                pop.classList.add('swap');
                var primary = pop.querySelector('[data-g=next],[data-g=done]');
                if (primary) try { primary.focus({ preventScroll: true }); } catch (e) { primary.focus(); }
            }
            // 遮罩挖洞用 clip-path，超大 box-shadow 在部分浏览器里画不出来
            function paint(g) {
                hole.style.left = g.x + 'px';
                hole.style.top = g.y + 'px';
                hole.style.width = g.w + 'px';
                hole.style.height = g.h + 'px';
                hole.style.borderRadius = g.r + 'px';
                var W = innerWidth, H = innerHeight, d = 'M0 0H' + W + 'V' + H + 'H0Z';
                if (g.w > 1 && g.h > 1) {
                    var r = Math.min(g.r, g.w / 2, g.h / 2), x = g.x, y = g.y, w = g.w, h = g.h;
                    d += 'M' + (x + r) + ' ' + y + 'H' + (x + w - r) + 'A' + r + ' ' + r + ' 0 0 1 ' + (x + w) + ' ' + (y + r)
                        + 'V' + (y + h - r) + 'A' + r + ' ' + r + ' 0 0 1 ' + (x + w - r) + ' ' + (y + h)
                        + 'H' + (x + r) + 'A' + r + ' ' + r + ' 0 0 1 ' + x + ' ' + (y + h - r)
                        + 'V' + (y + r) + 'A' + r + ' ' + r + ' 0 0 1 ' + (x + r) + ' ' + y + 'Z';
                }
                dim.style.clipPath = dim.style.webkitClipPath = 'path(evenodd, "' + d + '")';
            }
            function box(x, y, w, h, rad, instant) {
                var to = { x: x, y: y, w: w, h: h, r: rad };
                cancelAnimationFrame(tween);
                if (instant || !cur || reduceMotion) { cur = to; paint(to); return; }
                var from = cur, t0 = 0;
                var step = function (t) {
                    if (!t0) t0 = t;
                    var k = Math.min(1, (t - t0) / 450), e = 1 - Math.pow(1 - k, 4), g = {};
                    for (var p in to) g[p] = from[p] + (to[p] - from[p]) * e;
                    cur = g;
                    paint(g);
                    if (k < 1) tween = requestAnimationFrame(step);
                };
                tween = requestAnimationFrame(step);
            }
            function place(instant) {
                if (!steps) return;
                var s = steps[idx], t = s.el ? document.querySelector(s.el) : null;
                if (instant) pop.style.transition = 'none';
                var vw = innerWidth, vh = innerHeight, m = 12, pw = pop.offsetWidth, ph = pop.offsetHeight;
                if (!t) {
                    hole.classList.add('center');
                    box(vw / 2, vh / 2, 0, 0, 12, instant);
                    pop.removeAttribute('data-side');
                    pop.style.left = Math.round((vw - pw) / 2) + 'px';
                    pop.style.top = Math.round(Math.max(m, (vh - ph) / 2 - vh * 0.04)) + 'px';
                } else {
                    hole.classList.remove('center');
                    var r = t.getBoundingClientRect(), pad = s.pad != null ? s.pad : 6;
                    var top = Math.max(4, r.top - pad), bottom = Math.min(vh - 4, r.bottom + pad);
                    var lft = Math.max(4, r.left - pad), rgt = Math.min(vw - 4, r.right + pad);
                    var rad = (parseFloat(getComputedStyle(t).borderTopLeftRadius) || 6) + pad / 2;
                    box(lft, top, Math.max(0, rgt - lft), Math.max(0, bottom - top), rad, instant);
                    var gap = 12, room = { bottom: vh - bottom, top: top, right: vw - rgt, left: lft };
                    var fits = function (sd) { return sd === 'bottom' || sd === 'top' ? room[sd] >= ph + gap + m : room[sd] >= pw + gap + m; };
                    var sd = vw <= 560 ? 'over' : s.side && fits(s.side) ? s.side : ['bottom', 'top', 'right', 'left'].filter(fits)[0] || 'over';
                    var x, y, cx = (lft + rgt) / 2, cy = (top + bottom) / 2;
                    if (sd === 'bottom' || sd === 'top') {
                        x = Math.max(m, Math.min(vw - pw - m, cx - pw / 2));
                        y = sd === 'bottom' ? bottom + gap : top - gap - ph;
                        pop.style.setProperty('--arrow', Math.max(14, Math.min(pw - 26, cx - x - 6)) + 'px');
                    } else if (sd === 'right' || sd === 'left') {
                        x = sd === 'right' ? rgt + gap : lft - gap - pw;
                        y = Math.max(m, Math.min(vh - ph - m, cy - ph / 2));
                        pop.style.setProperty('--arrow', Math.max(14, Math.min(ph - 26, cy - y - 6)) + 'px');
                    } else {
                        x = (vw - pw) / 2;
                        y = vh - ph - m - 8;
                    }
                    if (sd === 'over') pop.removeAttribute('data-side'); else pop.setAttribute('data-side', sd);
                    pop.style.left = Math.round(x) + 'px';
                    pop.style.top = Math.round(y) + 'px';
                }
                if (instant) { void pop.offsetWidth; pop.style.transition = ''; }
            }
            function reflow() {
                if (raf) return;
                raf = requestAnimationFrame(function () { raf = 0; place(true); });
            }
            function keys(e) {
                if (!steps) return;
                if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); end(false); }
                else if (e.key === 'ArrowRight') { e.preventDefault(); if (idx < steps.length - 1) go(idx + 1); else end(true); }
                else if (e.key === 'ArrowLeft') { e.preventDefault(); go(idx - 1); }
                else if (e.key === 'Tab') {
                    var f = pop.querySelectorAll('button');
                    if (!f.length) return;
                    var a = f[0], z = f[f.length - 1];
                    if (e.shiftKey && document.activeElement === a) { e.preventDefault(); z.focus(); }
                    else if (!e.shiftKey && document.activeElement === z) { e.preventDefault(); a.focus(); }
                    else if (!pop.contains(document.activeElement)) { e.preventDefault(); a.focus(); }
                }
            }
            function end(completed) {
                if (!steps) return;
                steps = null;
                cur = null;
                wrap.classList.remove('on');
                document.removeEventListener('keydown', keys, true);
                window.removeEventListener('resize', reflow);
                window.removeEventListener('scroll', reflow, true);
                markSeen(key);
                if (lastFocus && lastFocus.focus && lastFocus !== document.body) try { lastFocus.focus({ preventScroll: true }); } catch (e) {}
                if (onEnd) onEnd(completed);
            }
            // 切页时直接收起，不算看过
            function stop() {
                if (!steps) return;
                steps = null;
                cur = null;
                if (wrap) wrap.classList.remove('on');
                document.removeEventListener('keydown', keys, true);
                window.removeEventListener('resize', reflow);
                window.removeEventListener('scroll', reflow, true);
            }
            return { start: start, end: stop, active: function () { return !!steps; }, usable: usable };
        })();

        function pageTour() { return page && TOURS[page] ? TOURS[page] : null; }
        function pageTourUsable() {
            var t = pageTour();
            return !!t && t.filter(function (s) { return s.el && guide.usable(s); }).length > 0;
        }
        function startWelcome() { guide.start('welcome', WELCOME, function (completed) { if (completed) setTimeout(hintPage, 400); }); }
        function startPage() { var t = pageTour(); if (t) guide.start(pageKey, t); }
        function syncGuideUi() {
            var has = page !== 'agent' && pageTourUsable(), fresh = has && !seen[pageKey];
            each(document.querySelectorAll('[data-guide-run=page]'), function (b) { b.hidden = !has; });
            each(document.querySelectorAll('[data-guide-new]'), function (d) { d.hidden = !fresh; });
            each(document.querySelectorAll('[data-guide-dot]'), function (d) { d.hidden = !fresh || !seen.welcome; });
        }
        function hintPage() {
            if (page === 'agent' || !pageTourUsable() || seen[pageKey] || guide.active()) return;
            try { if (sessionStorage.getItem('ota.hint.' + page)) return; sessionStorage.setItem('ota.hint.' + page, '1'); } catch (e) {}
            var crumb = document.querySelector('.crumb b');
            var el = document.createElement('div');
            el.className = 'guide-hint';
            el.setAttribute('role', 'status');
            el.innerHTML = '<span class="gh-ic"><svg class="i"><use href="#i-compass"/></svg></span><div class="gh-title"></div>'
                + '<div class="gh-text">20 秒看完这一页的重点，以后从右上角的问号也能打开。</div>'
                + '<div class="btn-row"><button type="button" class="btn btn-ghost btn-sm" data-h="no">不用了</button><button type="button" class="btn btn-sm" data-h="go">看看引导</button></div>'
                + '<span class="gh-timer"></span>';
            el.querySelector('.gh-title').textContent = '第一次打开「' + (crumb ? crumb.textContent : '这一页') + '」';
            document.body.appendChild(el);
            var close = function () { el.classList.add('hide'); setTimeout(function () { el.remove(); }, 260); };
            el.addEventListener('click', function (e) {
                var b = e.target.closest('[data-h]');
                if (!b) return;
                close();
                if (b.getAttribute('data-h') === 'go') startPage(); else markSeen(pageKey);
            });
            el.querySelector('.gh-timer').addEventListener('animationend', function () { if (el.isConnected) close(); });
        }
        document.addEventListener('click', function (e) {
            var b = e.target.closest('[data-guide-run]');
            if (!b) return;
            var w = b.getAttribute('data-guide-run');
            popClose(null);
            var hint = document.querySelector('.guide-hint');
            if (hint) hint.remove();
            setTimeout(function () { if (w === 'welcome') startWelcome(); else startPage(); }, 60);
        });

        // ---------- 搜索和快捷操作（⌘K / Ctrl K） ----------
        var cmdk = (function () {
            var items = [], shown = [], index = 0;
            // 输入两个字以上时再去服务端搜数据（口令、代理、设备、版本、成员、App），停顿一下才发，只认最后一次的结果
            var remote = { q: '', groups: [], loading: '' }, remoteTimer = 0, remoteSeq = 0;
            var NAV_K = {
                '/admin': 'overview home dashboard current gailan dangqian 当前概览 概览 首页 仪表盘',
                '/admin/codes': 'codes kouling 口令',
                '/admin/activations': 'activations jihuo 激活 记录',
                '/admin/agents': 'agents daili 代理 扣量',
                '/admin/releases': 'releases version fabu banben 发布 版本 灰度 回滚',
                '/admin/devices': 'devices shebei 设备 崩溃 统计',
                '/admin/app': 'app settings shezhi 设置 渠道 webhook 通知 令牌',
                '/admin/signing': 'signing qianming 签名 证书 密钥',
                '/admin/apps': 'apps suoyou 所有 应用',
                '/admin/admins': 'admins guanliyuan zhanghao 管理账号 管理员 成员 权限',
                '/admin/logs': 'logs rizhi 操作 日志 审计',
                '/admin/settings': 'settings xitong 系统 配置 存储 主题',
                '/admin/docs': 'docs api wendang 文档 接口'
            };
            function els() {
                var d = document.getElementById('cmdk');
                return d ? { d: d, input: d.querySelector('input'), list: d.querySelector('.cmdk-list') } : null;
            }
            function esc(s) {
                return String(s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; });
            }
            function collect() {
                var out = [];
                each(document.querySelectorAll('.sidebar .nav a'), function (a) {
                    var use = a.querySelector('use');
                    var label = Array.prototype.filter.call(a.childNodes, function (n) { return n.nodeType === 3; }).map(function (n) { return n.nodeValue; }).join('').trim();
                    out.push({ group: '页面', title: label, href: a.getAttribute('href'), icon: use ? use.getAttribute('href') : '#i-right',
                        k: NAV_K[a.getAttribute('href')] || '', sub: a.classList.contains('active') ? '当前页面' : '' });
                });
                var tpl = document.getElementById('cmdk-actions');
                if (tpl && tpl.content) each(tpl.content.querySelectorAll('a'), function (a) {
                    out.push({ group: '快捷操作', title: a.textContent.trim(), href: a.getAttribute('href'), icon: '#' + a.getAttribute('data-icon'), k: a.getAttribute('data-k') || '' });
                });
                each(document.querySelectorAll('.app-opt'), function (a) {
                    var b = a.querySelector('b'), slug = a.querySelector('small span'), name = b ? b.textContent.trim() : '';
                    out.push({ group: '切换 App', title: name, href: a.getAttribute('href'), letter: name.slice(0, 1) || '?',
                        k: a.getAttribute('data-search') || '', sub: a.classList.contains('on') ? '当前' : (slug ? slug.textContent : '') });
                });
                var dark = root.getAttribute('data-mode') === 'dark', compact = root.getAttribute('data-density') === 'compact';
                out.push({ group: '偏好与帮助', title: dark ? '切换到浅色模式' : '切换到深色模式', icon: dark ? '#i-sun' : '#i-moon',
                    k: 'dark light theme mode night shense qianse 深色 浅色 夜间 暗色 主题 外观', run: function () { lookSet('mode', dark ? 'light' : 'dark', document.querySelector('[data-guide=look]')); } });
                out.push({ group: '偏好与帮助', title: compact ? '切换到标准密度' : '切换到紧凑密度', icon: compact ? '#i-rows' : '#i-rows-tight',
                    k: 'density compact comfortable jincou 紧凑 标准 密度', run: function () { lookSet('density', compact ? 'comfortable' : 'compact'); } });
                if (page !== 'agent' && pageTourUsable()) out.push({ group: '偏好与帮助', title: '本页引导', icon: '#i-compass', k: 'guide tour help yindao 引导 帮助 教程', run: startPage });
                if (side) out.push({ group: '偏好与帮助', title: '后台导览', icon: '#i-sparkle', k: 'guide tour welcome help daolan 导览 引导 新手', run: startWelcome });
                out.push({ group: '偏好与帮助', title: '快捷键', icon: '#i-keyboard', k: 'keyboard shortcuts kuaijiejian 快捷键 键盘', run: function () { openDialog('keys-dialog'); } });
                out.push({ group: '偏好与帮助', title: '退出登录', icon: '#i-logout', k: 'logout sign out tuichu 退出 登出', run: function () {
                    var f = document.querySelector('form[action$="/logout"]');
                    if (f) f.submit();
                } });
                return out;
            }
            function score(it, q) {
                if (!q) return 1;
                var t = it.title.toLowerCase(), s = (it.sub || '').toLowerCase(), k = it.k.toLowerCase(), i = t.indexOf(q);
                if (i === 0) return 100 - t.length;
                if (i > 0) return 70 - i;
                if (s.indexOf(q) >= 0) return 45;
                if (k.indexOf(q) >= 0) return 35;
                var j = 0;
                for (var c = 0; c < t.length && j < q.length; c++) if (t.charAt(c) === q.charAt(j)) j++;
                return j === q.length ? 10 : 0;
            }
            function hl(text, q) {
                var i = q ? text.toLowerCase().indexOf(q) : -1;
                if (i < 0) return esc(text);
                return esc(text.slice(0, i)) + '<mark>' + esc(text.slice(i, i + q.length)) + '</mark>' + esc(text.slice(i + q.length));
            }
            function itemHtml(it, i, q) {
                return '<button type="button" class="cmdk-item" role="option" data-i="' + i + '">'
                    + (it.letter ? '<span class="ico">' + esc(it.letter) + '</span>' : '<span class="ico"><svg class="i i-sm"><use href="' + esc(it.icon) + '"/></svg></span>')
                    + '<span class="cmdk-title">' + hl(it.title, q) + '</span>'
                    + (it.sub ? '<small>' + hl(it.sub, q) + '</small>' : '') + '</button>';
            }
            function fetchRemote(raw) {
                clearTimeout(remoteTimer);
                if (!side || raw.length < 2) { remote = { q: '', groups: [], loading: '' }; return; }
                if (remote.q === raw) return;
                remote.loading = raw;
                remoteTimer = setTimeout(function () {
                    var seq = ++remoteSeq;
                    fetch('/admin/search?q=' + encodeURIComponent(raw), { credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' } })
                        .then(function (r) { return r.ok ? r.json() : { groups: [] }; })
                        .catch(function () { return { groups: [] }; })
                        .then(function (data) {
                            if (seq !== remoteSeq) return;
                            remote = { q: raw, groups: (data && data.groups) || [], loading: '' };
                            var c = els();
                            if (c && c.d.open && c.input.value.trim() === raw) paint(true);
                        });
                }, 220);
            }
            function mark(scroll) {
                var c = els();
                if (!c) return;
                each(c.list.querySelectorAll('.cmdk-item'), function (b) {
                    var on = +b.getAttribute('data-i') === index;
                    b.classList.toggle('on', on);
                    b.setAttribute('aria-selected', on ? 'true' : 'false');
                    if (on && scroll) b.scrollIntoView({ block: 'nearest' });
                });
            }
            function paint(keep) {
                var c = els();
                if (!c) return;
                var was = index;
                var raw = c.input.value.trim(), q = raw.toLowerCase(), groups = {}, order = [];
                items.forEach(function (it, n) {
                    var sc = score(it, q);
                    if (sc <= 0) return;
                    if (!groups[it.group]) { groups[it.group] = []; order.push(it.group); }
                    groups[it.group].push({ it: it, sc: sc, n: n });
                });
                shown = [];
                var html = '';
                order.forEach(function (g) {
                    var arr = groups[g];
                    if (q) arr.sort(function (a, b) { return b.sc - a.sc || a.n - b.n; });
                    else if (g === '切换 App' && arr.length > 6) arr = arr.slice(0, 6);
                    html += '<div class="cmdk-group">' + esc(g) + '</div>';
                    arr.forEach(function (r) {
                        var it = r.it, i = shown.length;
                        shown.push(it);
                        html += itemHtml(it, i, q);
                    });
                });
                if (remote.q === raw) remote.groups.forEach(function (g) {
                    html += '<div class="cmdk-group">' + esc(g.title) + '</div>';
                    (g.items || []).forEach(function (r) {
                        var it = { title: r.title, sub: r.sub, href: r.href, icon: r.icon || '#i-search' }, i = shown.length;
                        shown.push(it);
                        html += itemHtml(it, i, q);
                    });
                });
                if (remote.loading === raw && raw) html += '<div class="cmdk-loading"><span class="spinner"></span>正在搜口令、代理、设备和版本…</div>';
                c.list.innerHTML = html || '<div class="cmdk-none">没有找到和「' + esc(raw) + '」相关的内容</div>';
                index = keep && was < shown.length ? was : 0;
                mark(false);
            }
            function run(i) {
                var c = els(), it = shown[i];
                if (!c || !it) return;
                c.d.close();
                if (it.run) setTimeout(it.run, 120);
                else navigate(it.href);
            }
            function open() {
                var c = els();
                if (!c || (c.d.open && !c.d.classList.contains('closing')) || guide.active()) return;
                if (document.querySelector('dialog[open]:not(#cmdk)')) return;
                popClose(null);
                items = collect();
                c.input.value = '';
                remote = { q: '', groups: [], loading: '' };
                paint();
                c.d.showModal();
                c.input.focus();
            }
            function bind() {
                var c = els();
                if (!c) return;
                c.input.addEventListener('input', function () { fetchRemote(c.input.value.trim()); paint(); });
                c.input.addEventListener('keydown', function (e) {
                    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                        e.preventDefault();
                        if (!shown.length) return;
                        index = (index + (e.key === 'ArrowDown' ? 1 : -1) + shown.length) % shown.length;
                        mark(true);
                    } else if (e.key === 'Enter') {
                        e.preventDefault();
                        if (!e.isComposing) run(index);
                    }
                });
                c.list.addEventListener('click', function (e) {
                    var b = e.target.closest('.cmdk-item');
                    if (b) run(+b.getAttribute('data-i'));
                });
                c.list.addEventListener('mousemove', function (e) {
                    var b = e.target.closest('.cmdk-item');
                    if (!b || +b.getAttribute('data-i') === index) return;
                    index = +b.getAttribute('data-i');
                    mark(false);
                });
            }
            return { open: open, bind: bind, els: els };
        })();
        document.addEventListener('click', function (e) {
            if (e.target.closest('[data-cmdk-open]')) { e.preventDefault(); cmdk.open(); }
        });
        document.addEventListener('keydown', function (e) {
            var c = cmdk.els();
            if (!c) return;
            if ((e.metaKey || e.ctrlKey) && !e.shiftKey && !e.altKey && (e.key === 'k' || e.key === 'K')) {
                e.preventDefault();
                if (c.d.open) c.d.close(); else cmdk.open();
                return;
            }
            var a = document.activeElement;
            if (a && (a.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(a.tagName))) return;
            if (e.metaKey || e.ctrlKey || e.altKey) return;
            if (e.key === '?') {
                if (document.querySelector('dialog[open]') || guide.active()) return;
                e.preventDefault();
                openDialog('keys-dialog');
            } else if (e.key === '/') {
                e.preventDefault();
                cmdk.open();
            }
        });

        // ---------- 无刷新切页：只换内容区，侧栏和顶栏不动；打开过的页面缓存起来，切回去立刻显示 ----------
        var nav = { cache: new Map(), seq: 0, ctl: null, scrolls: new Map(), key: '' };
        var CACHE_FRESH_MS = 5 * 60 * 1000, PREFETCH_FRESH_MS = 20 * 1000, CACHE_MAX = 14;

        function shellPage() { return !!document.querySelector('.sidebar[data-page]'); }
        // 代理后台（和管理员预览代理后台）只在自己的几个页面之间无刷新切换
        function agentBase() { var a = document.querySelector('.agentbar .agent-brand'); return a ? a.getAttribute('href') : null; }
        // 数据大屏没有侧栏，里面的链接（切换 App、回后台、待处理）也无刷新切页：全屏不会被打断
        function swapPage() { return shellPage() || !!agentBase() || !!document.querySelector('.scr'); }
        function keyOf(href) { var u = new URL(href, location.href); return u.pathname + u.search; }
        function swappable(u) {
            if (u.origin !== location.origin) return false;
            var base = agentBase();
            if (base) return u.pathname === base || ['/requests', '/records', '/subs', '/notices', '/account'].some(function (p) { return u.pathname === base + p; });
            if (!/^\/admin(\/|$)/.test(u.pathname)) return false;
            return !/(\/export|\.pem|\/preview(\/.*)?|\/backup)$/.test(u.pathname);
        }
        function navigate(href) {
            var u = new URL(href, location.href);
            confirmLeave(function () {
                if (swapPage() && swappable(u)) go(u.href, { push: true }); else location.href = u.href;
            });
        }
        window.otaNavigate = navigate;

        var progress = (function () {
            var bar = null, timer = 0, trickle = 0, w = 0, dim = 0;
            function el() {
                if (!bar || !bar.isConnected) {
                    bar = document.createElement('div');
                    bar.className = 'nav-progress';
                    document.documentElement.appendChild(bar);
                }
                return bar;
            }
            function set(v) { w = v; el().style.transform = 'scaleX(' + (v / 100) + ')'; }
            return {
                // wait：多久没好才露出进度条；同一页换筛选一般很快，等久一点，免得每次都闪一下
                start: function (wait) {
                    clearTimeout(timer);
                    clearTimeout(dim);
                    clearInterval(trickle);
                    dim = setTimeout(function () { root.classList.add('nav-loading'); }, 260);
                    timer = setTimeout(function () {
                        var b = el();
                        b.classList.remove('done');
                        b.style.transition = 'none';
                        set(0);
                        void b.offsetWidth;
                        b.style.transition = '';
                        b.classList.add('on');
                        set(28);
                        trickle = setInterval(function () { if (w < 88) set(w + (88 - w) * 0.12); }, 220);
                    }, wait || 120);
                },
                done: function () {
                    clearTimeout(timer);
                    clearTimeout(dim);
                    clearInterval(trickle);
                    root.classList.remove('nav-loading');
                    if (!bar || !bar.classList.contains('on')) return;
                    set(100);
                    bar.classList.add('done');
                    setTimeout(function () { if (bar) bar.classList.remove('on', 'done'); }, 380);
                }
            };
        })();

        // ---------- 浏览器里的页面缓存（按人、按版本分区）：下次打开、新开标签页直接显示，后台再刷新 ----------

        function go(href, opts) { return host.go(href, opts || {}); }
        window.otaRefresh = function () { return go(location.href, { reload: true, keep: true, silent: true }); };

        // 后台刷新出新内容时，用户正在操作（开着弹窗、在输入）就先不替换
        function quiet() {
            var a = document.activeElement;
            return !document.querySelector('dialog[open]') && !guide.active()
                && !(a && (a.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(a.tagName)));
        }

        // 侧栏收起成图标：按钮或者按 [ 键
        function toggleSide() {
            var mini = root.getAttribute('data-side') === 'mini';
            lookSet('side', mini ? '' : 'mini');
            hideTip();
            setTimeout(function () { window.dispatchEvent(new Event('resize')); }, 320);
        }
        document.addEventListener('click', function (e) { if (e.target.closest('[data-side-toggle]')) toggleSide(); });
        document.addEventListener('keydown', function (e) {
            if (e.key !== '[' || e.metaKey || e.ctrlKey || e.altKey || !shellPage()) return;
            var a = document.activeElement;
            if (a && (a.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(a.tagName))) return;
            if (document.querySelector('dialog[open]') || guide.active()) return;
            e.preventDefault();
            toggleSide();
        });

        // ---------- 图标按钮的提示：比浏览器自带的 title 快、也更好看；侧栏收起时给菜单图标显示名字 ----------
        var tipEl = null, tipTimer = 0, tipFor = null;
        var TIP_SEL = '[data-tip], .top-btn[aria-label], .tab-tool[aria-label], .dlg-x, .dark-btn[aria-label], .icon-btn[aria-label], .sidebar .nav a, .side-user, .app-current';
        function tipText(el) {
            if (el.matches('.sidebar .nav a, .side-user, .app-current')) {
                if (root.getAttribute('data-side') !== 'mini' || window.innerWidth <= 900) return '';
                if (el.matches('.side-user')) return '我的账号';
                if (el.matches('.app-current')) { var b = el.querySelector('b'); return b ? '当前 App：' + b.textContent.trim() : ''; }
                return Array.prototype.filter.call(el.childNodes, function (n) { return n.nodeType === 3; }).map(function (n) { return n.nodeValue; }).join('').trim();
            }
            return el.getAttribute('data-tip') || el.getAttribute('aria-label') || '';
        }
        function hideTip() {
            clearTimeout(tipTimer);
            if (tipFor && tipFor.hasAttribute('data-title-was')) { tipFor.setAttribute('title', tipFor.getAttribute('data-title-was')); tipFor.removeAttribute('data-title-was'); }
            tipFor = null;
            if (tipEl) tipEl.classList.remove('on');
        }
        function showTip(el) {
            var text = tipText(el);
            if (!text || !el.isConnected) return;
            if (!tipEl || !tipEl.isConnected) {
                tipEl = document.createElement('div');
                tipEl.className = 'tip';
                tipEl.setAttribute('role', 'tooltip');
                document.documentElement.appendChild(tipEl);
            }
            if (el.hasAttribute('title')) { el.setAttribute('data-title-was', el.getAttribute('title')); el.removeAttribute('title'); }
            var key = el.getAttribute('data-tip-key') || (el.matches('.dlg-x') ? 'Esc' : '');
            tipEl.textContent = text;
            if (key) { var k = document.createElement('kbd'); k.textContent = key; tipEl.appendChild(k); }
            var r = el.getBoundingClientRect(), w = tipEl.offsetWidth, h = tipEl.offsetHeight, x, y;
            if (el.closest('.sidebar')) { x = r.right + 10; y = r.top + r.height / 2 - h / 2; }
            else {
                x = Math.max(8, Math.min(innerWidth - w - 8, r.left + r.width / 2 - w / 2));
                y = r.bottom + 8 + h > innerHeight ? r.top - h - 8 : r.bottom + 8;
            }
            tipEl.style.left = Math.round(x) + 'px';
            tipEl.style.top = Math.round(y) + 'px';
            tipEl.classList.add('on');
        }
        document.addEventListener('mouseover', function (e) {
            var el = e.target.closest && e.target.closest(TIP_SEL);
            if (el === tipFor) return;
            hideTip();
            if (!el || (window.matchMedia && matchMedia('(hover: none)').matches)) return;
            tipFor = el;
            tipTimer = setTimeout(function () { if (tipFor === el) showTip(el); }, 380);
        });
        document.addEventListener('mousedown', hideTip, true);
        window.addEventListener('scroll', hideTip, true);
        window.addEventListener('blur', hideTip);

        // 离开当前页面：收起菜单、结束引导、清掉这一页注册的监听
        var liveTimer = null, liveMs = 0, liveRingAnim = null;
        // 实时数据的倒计时圆环（代理后台右上角）：每次取到数据从空走满。外观里关了动画时 CSS 动画全停，
        // 所以用 Web Animations 画，那时一秒走一格
        function liveRing(ms) {
            if (liveRingAnim) liveRingAnim.cancel();
            liveRingAnim = null;
            var fg = document.querySelector('[data-live-ring] .fg');
            if (!fg || !ms || !fg.animate) return;
            var calm = document.documentElement.getAttribute('data-motion') === 'off';
            liveRingAnim = fg.animate([{ strokeDashoffset: '44px' }, { strokeDashoffset: '0px' }],
                { duration: ms, easing: calm ? 'steps(' + Math.max(1, Math.round(ms / 1000)) + ', end)' : 'linear', fill: 'forwards' });
        }
        // 数据没变时后台刷新不重新渲染页面，圆环跟着每次取数结果重新走
        window.addEventListener('ota:live', function (e) { if (liveTimer && e.detail && e.detail.ok) liveRing(liveMs); });
        function leave() {
            clearInterval(liveTimer);
            liveTimer = null;
            liveRing(0);
            hideTip();
            popClose(null);
            guide.end();
            document.body.classList.remove('nav-open');
            window.dispatchEvent(new Event('ota:leave'));
            unmount();
        }

        if (swapPage()) {
            history.replaceState({ ota: 1 }, '', location.href);
            if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
            nav.key = keyOf(location.href);
        }

        document.addEventListener('click', function (e) {
            if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
            var a = e.target.closest('a[href]');
            if (!a || (a.target && a.target !== '_self') || a.hasAttribute('download') || a.hasAttribute('data-no-swap')) return;
            var href = a.getAttribute('href');
            if (!href || href.charAt(0) === '#' || /^(javascript|mailto|tel):/i.test(href)) return;
            var u = new URL(href, location.href);
            if (!swapPage() || !swappable(u)) return;
            if (u.pathname === location.pathname && u.search === location.search) {
                if (u.hash) return;
                e.preventDefault();
                confirmLeave(function () { go(u.href, { reload: true, keep: true }); });
                return;
            }
            e.preventDefault();
            // 同一页换筛选、翻页：分段按钮先亮起来，内容到了原地换掉；翻页时列表卡片顶已经滚出屏幕就滚回卡片顶
            var here = u.pathname === location.pathname, pager = !!a.closest('.pager'), seg = here && a.closest('.seg');
            confirmLeave(function () {
                if (seg) each(seg.children, function (x) { x.classList.toggle('on', x === a); });
                go(u.href, { push: true, soft: here, reveal: pager ? '.pager' : null });
            });
        });

        // 筛选、搜索这类 GET 表单也不整页刷新
        document.addEventListener('submit', function (e) {
            if (e.defaultPrevented) return;
            var f = e.target;
            if ((f.getAttribute('method') || 'get').toLowerCase() !== 'get' || f.hasAttribute('data-no-swap') || (f.target && f.target !== '_self')) return;
            var u = new URL(f.getAttribute('action') || location.pathname, location.href);
            if (!swapPage() || !swappable(u)) return;
            e.preventDefault();
            var data = new FormData(f);
            if (e.submitter && e.submitter.name) data.append(e.submitter.name, e.submitter.value);
            u.search = new URLSearchParams(data).toString();
            go(u.href, { push: true, soft: u.pathname === location.pathname });
        });

        // 鼠标停在侧栏菜单、标签上时提前取页面，点下去几乎是瞬间打开
        var hoverTimer = 0;
        document.addEventListener('mouseover', function (e) {
            var a = e.target.closest('.sidebar .nav a[href], .tabbar .tab-item[href], a.metric[href]');
            if (!a || !shellPage()) return;
            clearTimeout(hoverTimer);
            hoverTimer = setTimeout(function () {
                var u = new URL(a.getAttribute('href'), location.href);
                if (!swappable(u) || keyOf(u.href) === nav.key) return;
                var hit = nav.cache.get(keyOf(u.href));
                if (hit && Date.now() - hit.at < PREFETCH_FRESH_MS) return;
                host.prefetch(u.href);
            }, 70);
        });
        document.addEventListener('mouseout', function (e) {
            if (e.target.closest && e.target.closest('.sidebar .nav a[href], .tabbar .tab-item[href], a.metric[href]')) clearTimeout(hoverTimer);
        });

        // ---------- 页面标签栏：最多开 10 个，概览固定在最前面；切换时直接用缓存 ----------
        var TABS_KEY = 'ota.tabs', MAX_TABS = 10;
        // 触屏上拖动标签会把左右滑动变成拖拽（标签乱跳），只在鼠标设备上能拖动排序
        var tabDrag = !(window.matchMedia && matchMedia('(pointer: coarse)').matches);
        var tabs = [];
        try { tabs = JSON.parse(sessionStorage.getItem(TABS_KEY) || '[]') || []; } catch (e) { tabs = []; }
        function tabsSave() { try { sessionStorage.setItem(TABS_KEY, JSON.stringify(tabs)); } catch (e) {} }
        function tabIcon(path) {
            var a = document.querySelector('.sidebar .nav a[href="' + sel(path) + '"]');
            if (!a) {
                [].forEach.call(document.querySelectorAll('.sidebar .nav a[href^="/admin/"]'), function (x) {
                    var h = x.getAttribute('href');
                    if (path.indexOf(h + '/') === 0 && (!a || h.length > a.getAttribute('href').length)) a = x;
                });
            }
            var use = a && a.querySelector('use');
            return use ? use.getAttribute('href') : path === '/admin/account' ? '#i-user' : '#i-file';
        }
        function tabsTrack() {
            if (!shellPage()) return;
            var path = location.pathname, crumb = document.querySelector('.crumb b');
            if (!tabs.some(function (t) { return t.path === '/admin'; })) tabs.unshift({ path: '/admin', url: '/admin', title: '当前概览', icon: '#i-grid', pinned: true, t: 0 });
            var cur = null;
            tabs.forEach(function (t) { if (t.path === path) cur = t; });
            if (!cur) { cur = { path: path }; tabs.push(cur); }
            cur.url = path + location.search;
            cur.title = crumb ? (crumb.getAttribute('data-src') || crumb.textContent.trim()) : document.title.split(' · ')[0];
            cur.icon = tabIcon(path);
            cur.t = Date.now();
            while (tabs.length > MAX_TABS) {
                var victim = tabs.filter(function (t) { return !t.pinned && t.path !== path; }).sort(function (a, b) { return a.t - b.t; })[0];
                if (!victim) break;
                tabs.splice(tabs.indexOf(victim), 1);
            }
            tabsSave();
            tabsRender();
        }
        function tabbarEl() {
            var bar = document.getElementById('tabbar');
            if (bar) return bar;
            var top = document.querySelector('.main > .topbar');
            if (!top) return null;
            bar = document.createElement('div');
            bar.id = 'tabbar';
            bar.className = 'tabbar';
            bar.setAttribute('role', 'tablist');
            bar.innerHTML = '<div class="tabbar-scroll"></div><div class="tabbar-tools">'
                + '<button type="button" class="tab-tool" data-tab-act="refresh" title="刷新当前页" aria-label="刷新当前页"><svg class="i"><use href="#i-refresh"/></svg></button>'
                + '<button type="button" class="tab-tool" data-tab-act="menu" title="标签操作" aria-label="标签操作"><svg class="i"><use href="#i-chevron-down"/></svg></button></div>';
            top.after(bar);
            bindTabbar(bar);
            return bar;
        }
        function tabsRender() {
            var bar = tabbarEl();
            if (!bar) return;
            var box = bar.querySelector('.tabbar-scroll'), path = location.pathname;
            var esc = function (s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); };
            var known = {};
            each(box.querySelectorAll('.tab-item'), function (n) { known[n.getAttribute('data-path')] = true; });
            // 英文界面时存下的标题，切回中文后按侧栏的名字换回来
            if (document.documentElement.getAttribute('lang') !== 'en') tabs.forEach(function (t) {
                if (/[\u4e00-\u9fff]/.test(t.title)) return;
                var a = document.querySelector('.sidebar .nav a[href="' + sel(t.path) + '"]');
                var txt = a && [].filter.call(a.childNodes, function (n) { return n.nodeType === 3 && n.nodeValue.trim(); })[0];
                if (txt) t.title = txt.nodeValue.trim();
                else if (t.path === '/admin/account') t.title = '我的账号';
            });
            box.innerHTML = tabs.map(function (t) {
                var on = t.path === path;
                return '<a class="tab-item' + (on ? ' on' : '') + (t.pinned ? ' pinned' : '') + (known[t.path] ? '' : ' new') + '" href="' + esc(t.url) + '" data-path="' + esc(t.path)
                    + '" role="tab" aria-selected="' + on + '"' + (t.pinned || !tabDrag ? '' : ' draggable="true"') + ' title="' + esc(t.title) + '">'
                    + '<svg class="i i-sm"><use href="' + esc(t.icon) + '"/></svg><span>' + esc(t.title) + '</span>'
                    + (t.pinned ? '' : '<button type="button" class="tab-x" data-tab-close aria-label="关闭' + esc(t.title) + '"><svg class="i"><use href="#i-x"/></svg></button>') + '</a>';
            }).join('');
            var cur = box.querySelector('.tab-item.on');
            if (cur) {
                var l = cur.offsetLeft, r = l + cur.offsetWidth;
                if (l < box.scrollLeft + 8) box.scrollLeft = l - 8;
                else if (r > box.scrollLeft + box.clientWidth - 8) box.scrollLeft = r - box.clientWidth + 8;
            }
            bar.classList.toggle('scrolls', box.scrollWidth > box.clientWidth + 2);
        }
        function tabClose(path) {
            var i = -1;
            tabs.forEach(function (t, n) { if (t.path === path) i = n; });
            if (i < 0 || tabs[i].pinned) return;
            var closing = tabs.splice(i, 1)[0];
            nav.cache.delete(keyOf(closing.url));
            tabsSave();
            if (path === location.pathname) {
                var next = tabs[i] || tabs[i - 1] || tabs[0];
                if (next) go(next.url, { push: true });
            } else {
                tabsRender();
            }
        }
        function tabsCloseWhere(test) {
            var curPath = location.pathname;
            tabs = tabs.filter(function (t) { return t.pinned || !test(t); });
            tabsSave();
            if (!tabs.some(function (t) { return t.path === curPath; })) go(tabs[tabs.length - 1].url, { push: true }); else tabsRender();
        }
        function tabMenu(x, y, path) {
            closeTabMenu();
            var idx = -1;
            tabs.forEach(function (t, n) { if (t.path === path) idx = n; });
            var t = tabs[idx];
            var m = document.createElement('div');
            m.className = 'menu tab-menu';
            m.setAttribute('role', 'menu');
            m.innerHTML = '<button type="button" class="menu-item" data-m="refresh"><svg class="i"><use href="#i-refresh"/></svg>刷新</button>'
                + '<button type="button" class="menu-item" data-m="close"' + (t && t.pinned ? ' disabled' : '') + '><svg class="i"><use href="#i-x"/></svg>关闭</button>'
                + '<hr><button type="button" class="menu-item" data-m="others">关闭其他标签</button>'
                + '<button type="button" class="menu-item" data-m="right">关闭右侧标签</button>'
                + '<button type="button" class="menu-item" data-m="all">关闭全部标签</button>';
            document.body.appendChild(m);
            var w = m.offsetWidth, h = m.offsetHeight;
            m.style.left = Math.min(x, innerWidth - w - 8) + 'px';
            m.style.top = Math.min(y, innerHeight - h - 8) + 'px';
            m.addEventListener('click', function (e) {
                var b = e.target.closest('[data-m]');
                if (!b || b.disabled) return;
                var act = b.getAttribute('data-m');
                closeTabMenu();
                if (act === 'refresh') { if (path === location.pathname) go(location.href, { reload: true, keep: true }); else { nav.cache.delete(keyOf(t.url)); go(t.url, { push: true, reload: true }); } }
                else if (act === 'close') tabClose(path);
                else if (act === 'others') tabsCloseWhere(function (x) { return x.path !== path; });
                else if (act === 'right') tabsCloseWhere(function (x) { return tabs.indexOf(x) > idx; });
                else if (act === 'all') tabsCloseWhere(function () { return true; });
            });
        }
        function closeTabMenu() { each(document.querySelectorAll('.tab-menu'), function (m) { m.remove(); }); }
        document.addEventListener('mousedown', function (e) { if (!e.target.closest('.tab-menu')) closeTabMenu(); });
        document.addEventListener('keydown', function (e) { if (e.key === 'Escape') closeTabMenu(); });
        window.addEventListener('resize', function () { closeTabMenu(); var bar = document.getElementById('tabbar'); if (bar) bar.classList.toggle('scrolls', bar.firstChild.scrollWidth > bar.firstChild.clientWidth + 2); });

        function bindTabbar(bar) {
            var box = bar.querySelector('.tabbar-scroll'), dragging = null;
            bar.addEventListener('click', function (e) {
                var x = e.target.closest('[data-tab-close]');
                if (x) { e.preventDefault(); e.stopPropagation(); tabClose(x.closest('.tab-item').getAttribute('data-path')); return; }
                var tool = e.target.closest('[data-tab-act]');
                if (!tool) return;
                if (tool.getAttribute('data-tab-act') === 'refresh') { go(location.href, { reload: true, keep: true }); return; }
                var r = tool.getBoundingClientRect();
                tabMenu(r.right - 200, r.bottom + 6, location.pathname);
            }, true);
            bar.addEventListener('auxclick', function (e) {
                var it = e.target.closest('.tab-item');
                if (it && e.button === 1) { e.preventDefault(); tabClose(it.getAttribute('data-path')); }
            });
            bar.addEventListener('contextmenu', function (e) {
                var it = e.target.closest('.tab-item');
                if (!it) return;
                e.preventDefault();
                tabMenu(e.clientX, e.clientY, it.getAttribute('data-path'));
            });
            box.addEventListener('wheel', function (e) {
                if (Math.abs(e.deltaY) > Math.abs(e.deltaX) && box.scrollWidth > box.clientWidth) { box.scrollLeft += e.deltaY; e.preventDefault(); }
            }, { passive: false });
            box.addEventListener('dragstart', function (e) {
                var it = e.target.closest('.tab-item');
                if (!it || it.classList.contains('pinned')) { e.preventDefault(); return; }
                dragging = it;
                it.classList.add('dragging');
                e.dataTransfer.effectAllowed = 'move';
                try { e.dataTransfer.setData('text/plain', it.getAttribute('data-path')); } catch (err) {}
            });
            box.addEventListener('dragover', function (e) {
                if (!dragging) return;
                e.preventDefault();
                var it = e.target.closest('.tab-item');
                if (!it || it === dragging || it.classList.contains('pinned')) return;
                var r = it.getBoundingClientRect();
                box.insertBefore(dragging, e.clientX < r.left + r.width / 2 ? it : it.nextSibling);
            });
            box.addEventListener('dragend', function () {
                if (!dragging) return;
                dragging.classList.remove('dragging');
                dragging = null;
                var order = Array.prototype.map.call(box.querySelectorAll('.tab-item'), function (n) { return n.getAttribute('data-path'); });
                tabs.sort(function (a, b) { return order.indexOf(a.path) - order.indexOf(b.path); });
                tabsSave();
            });
        }

        // ---------- 长列表：只先显示前几条，其余收在「显示全部」里，页面不会被几百行撑得很长 ----------
        function collapseLists() {
            each(document.querySelectorAll('[data-collapse]'), function (box) {
                if (box.hasAttribute('data-collapse-done')) return;
                box.setAttribute('data-collapse-done', '');
                var n = parseInt(box.getAttribute('data-collapse'), 10) || 8;
                var items = Array.prototype.filter.call(box.children, function (c) { return !c.hidden && !c.hasAttribute('data-collapse-keep'); });
                if (items.length <= n + 2) return;
                items.slice(n).forEach(function (c) { c.classList.add('collapsed-item'); });
                var unit = box.getAttribute('data-collapse-unit') || '条';
                var btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'collapse-toggle';
                var paint = function () {
                    var open = box.classList.contains('expanded');
                    btn.innerHTML = (open ? '收起' : '显示全部 ' + items.length + ' ' + unit) + '<svg class="i i-sm"><use href="#i-chevron-down"/></svg>';
                    btn.classList.toggle('open', open);
                };
                paint();
                btn.addEventListener('click', function () {
                    box.classList.toggle('expanded');
                    paint();
                    if (!box.classList.contains('expanded')) {
                        var r = box.getBoundingClientRect();
                        if (r.top < 70) window.scrollBy(0, r.top - 90);
                    }
                });
                (box.closest('.table-wrap') || box).after(btn);
            });
        }

        // ---------- 手机上表格变卡片：每格带上表头文字，CSS 在窄屏把一行排成一张卡片 ----------
        function cardTables() {
            each(document.querySelectorAll('.table-wrap > table, .ptable > table'), function (t) {
                if (t.hasAttribute('data-cards-done') || t.hasAttribute('data-no-cards') || !t.tHead) return;
                var row = t.tHead.rows[t.tHead.rows.length - 1];
                if (!row) return;
                var heads = [];
                each(row.cells, function (th) { for (var i = 0; i < (th.colSpan || 1); i++) heads.push(th); });
                if (heads.length < (t.parentElement.classList.contains('ptable') ? 2 : 3)) return;
                t.setAttribute('data-cards-done', '');
                t.classList.add('cards');
                var label = function (tr) {
                    if (tr.hasAttribute('data-cards-row')) return;
                    tr.setAttribute('data-cards-row', '');
                    if (tr.cells.length === 1 && tr.cells[0].colSpan > 1) { tr.cells[0].classList.add('cell-full'); return; }
                    var col = 0, primary = false;
                    each(tr.cells, function (td) {
                        var th = heads[col];
                        col += td.colSpan || 1;
                        if (!th) return;
                        if (th.classList.contains('check-col')) { td.classList.add('cell-check'); return; }
                        if (td.querySelector('.btn-row') || td.classList.contains('actions')) {
                            td.classList.add(td.querySelector('a, button, input, select') ? 'cell-actions' : 'cell-void');
                            return;
                        }
                        if (!primary) { primary = true; td.classList.add('cell-primary'); return; }
                        if (!td.hasAttribute('data-label')) td.setAttribute('data-label', th.textContent.replace(/\s+/g, ' ').trim());
                        // 手机上变成卡片时，空着的格子（只有「—」）不占一行
                        var txt = td.textContent.replace(/\s+/g, '');
                        td.classList.toggle('cell-empty', (txt === '' || txt === '—' || txt === '-') && !td.querySelector('svg, img, input, select, button, a, i, .bar, .usage'));
                    });
                };
                each(t.tBodies, function (tb) {
                    each(tb.rows, label);
                    new MutationObserver(function () { each(tb.rows, label); }).observe(tb, { childList: true });
                });
            });
        }

        // ---------- 待处理：每 30 秒拿到的最新列表，更新铃铛和侧栏的待审核数（不刷新页面）；多出来的给个提示 ----------
        function escHtml(s) {
            return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; });
        }
        function todosUpdate(t, requests) {
            var pop = document.querySelector('.todo-pop');
            if (!pop || !t) return;
            var btn = pop.querySelector('[data-pop-toggle]'), menu = pop.querySelector('.todo-menu');
            var badge = btn.querySelector('.todo-count'), before = badge ? +badge.textContent || 0 : 0;
            if (t.count > 0) {
                if (!badge) { badge = document.createElement('i'); btn.appendChild(badge); }
                badge.className = 'todo-count ' + (t.level || '');
                badge.textContent = t.count;
            } else if (badge) badge.remove();
            btn.title = t.count ? '待处理：' + t.count + ' 件' : '待处理：没有';
            if (menu && menu.hidden) {
                var items = t.items || [], html = '<div class="todo-head"><b>待处理</b><span>' + (t.count ? t.count + ' 件' : '') + '</span></div>';
                if (!items.length) html += '<div class="todo-empty"><span class="todo-ok"><svg class="i"><use href="#i-tick"/></svg></span><b>都处理完了</b><span>新的口令申请、启动失败告警会出现在这里</span></div>';
                else html += '<div class="todo-items">' + items.map(function (it) {
                    return '<a class="todo-item ' + escHtml(it.level) + '" href="' + escHtml(it.href) + '"><i class="todo-dot"></i><span class="todo-text"><b>' + escHtml(it.title)
                        + '</b><small>' + escHtml(it.app ? it.app + ' · ' + it.detail : it.detail) + '</small></span><span class="todo-go">' + escHtml(it.action) + '</span></a>';
                }).join('') + '</div>';
                menu.innerHTML = html;
            }
            var nav = document.querySelector('.sidebar .nav a[href="/admin/agents"]');
            if (nav && requests != null) {
                var nb = nav.querySelector('.nav-badge');
                if (requests > 0) {
                    if (!nb) { nb = document.createElement('span'); nb.className = 'nav-badge'; nav.appendChild(nb); }
                    nb.textContent = requests;
                    nb.title = requests + ' 条口令申请待审核';
                } else if (nb) nb.remove();
            }
            if (t.count > before && t.items && t.items[0]) toast('新的待处理：' + t.items[0].title);
        }

        // ---------- 每个页面（首次打开、无刷新切过来）都要做的初始化 ----------
        function mount(opts) {
            opts = opts || {};
            var token = ++mountSeq;
            scoped(function () {
                var hash = location.hash.replace('#', '');
                part(function () {
        each(document.querySelectorAll('dialog'), function (d) {
            var head = d.querySelector(':scope > .dlg-head, :scope > form > .dlg-head');
            if (head && !d.hasAttribute('data-busy')) {
                var x = document.createElement('button');
                x.type = 'button';
                x.className = 'dlg-x';
                x.setAttribute('data-close', '');
                x.setAttribute('aria-label', '关闭');
                x.title = '关闭（Esc）';
                x.innerHTML = '<svg class="i"><use href="#i-x"/></svg>';
                head.appendChild(x);
            }
            d.addEventListener('click', function (e) { if (e.target === d && !d.hasAttribute('data-busy')) d.close(); });
            d.addEventListener('cancel', function (e) {
                e.preventDefault();
                if (!d.hasAttribute('data-busy')) d.close();
            });
        });
                });
                part(function () {
                    var n = 0;
                    each(document.querySelectorAll('.sidebar .nav-badge:not(.dot)'), function (b) { n += parseInt(b.textContent, 10) || 0; });
                    var base = document.title.replace(/^\(\d+\)\s*/, ''), next = n > 0 ? '(' + n + ') ' + base : base;
                    if (document.title !== next) document.title = next;
                });
                part(function () {
                    var box = document.getElementById('toasts');
                    if (box) each(box.children, arm);
                    var queued = sessionStorage.getItem('ota.toast');
                    if (queued) {
                        sessionStorage.removeItem('ota.toast');
                        queued = JSON.parse(queued);
                        toast(queued.msg, queued.type);
                    }
                });
                part(function () {
        if (hash) {
            var target = document.querySelector('dialog[data-hash="' + sel(hash) + '"]');
            var clicker = target ? null : document.querySelector('[data-hash-click="' + sel(hash) + '"]');
            if (target || clicker) history.replaceState(null, '', location.pathname + location.search);
            if (target) target.showModal();
            if (clicker) setTimeout(function () { clicker.click(); }, 80);
            var spot = target || clicker ? null : document.querySelector('[data-hash-target="' + sel(hash) + '"]');
            if (spot) setTimeout(function () {
                window.scrollTo({ top: Math.max(0, spot.getBoundingClientRect().top + window.scrollY - 118), behavior: reduceMotion ? 'auto' : 'smooth' });
                var hit = spot.closest('tr') || spot;
                hit.classList.remove('hash-hit');
                void hit.offsetWidth;
                hit.classList.add('hash-hit');
            }, 160);
        }
                });
                part(function () {
        // 页内标签：<div class="tabs" data-tabs><a data-tab="x"> 对应 <section data-tab-panel="x">，地址栏 #x 记住当前标签
        var tabBar = document.querySelector('[data-tabs]');
        if (tabBar) {
            var tabLinks = tabBar.querySelectorAll('[data-tab]');
            var showTab = function (name, remember) {
                var hit = false;
                each(tabLinks, function (a) {
                    var on = a.getAttribute('data-tab') === name;
                    a.classList.toggle('on', on);
                    if (on) hit = true;
                });
                if (!hit) return false;
                each(document.querySelectorAll('[data-tab-panel]'), function (p) {
                    p.hidden = p.getAttribute('data-tab-panel') !== name;
                });
                if (remember) history.replaceState(null, '', location.pathname + location.search + '#' + name);
                return true;
            };
            tabBar.addEventListener('click', function (e) {
                var a = e.target.closest('[data-tab]');
                if (!a) return;
                e.preventDefault();
                showTab(a.getAttribute('data-tab'), true);
            });
            window.addEventListener('hashchange', function () { showTab(location.hash.replace('#', ''), false); });
            if (!showTab(hash, false)) showTab(tabLinks[0].getAttribute('data-tab'), false);
        }
                });
                part(function () {
        // 标签下面的指示条跟着选中项滑动
        each(document.querySelectorAll('.tabs'), function (bar) {
            var ink = document.createElement('span');
            ink.className = 'tab-ink';
            bar.appendChild(ink);
            bar.classList.add('has-ink');
            // 手机上标签放不下时可以左右滑，两头渐隐提示还有，选中的滑到中间
            var edges = function () {
                var max = bar.scrollWidth - bar.clientWidth;
                bar.classList.toggle('fade-l', max > 2 && bar.scrollLeft > 2);
                bar.classList.toggle('fade-r', max > 2 && bar.scrollLeft < max - 2);
            };
            var shown = null;
            var move = function (instant) {
                var on = bar.querySelector('a.on');
                if (!on) { ink.style.opacity = '0'; return; }
                if (instant) ink.style.transition = 'none';
                ink.style.opacity = '1';
                ink.style.width = on.offsetWidth + 'px';
                ink.style.transform = 'translateX(' + on.offsetLeft + 'px)';
                ink.classList.toggle('danger', on.classList.contains('danger'));
                if (instant) { void ink.offsetWidth; ink.style.transition = ''; }
                // 只在选中项换了的时候滑到中间；用户自己左右滑、手机地址栏收起触发 resize 时都不动
                if (on !== shown && bar.scrollWidth > bar.clientWidth + 2) {
                    bar.scrollTo({ left: Math.max(0, on.offsetLeft - (bar.clientWidth - on.offsetWidth) / 2), behavior: instant ? 'auto' : 'smooth' });
                }
                shown = on;
                edges();
            };
            bar.addEventListener('scroll', edges, { passive: true });
            move(true);
            // 只看标签本身的 class（选中项变了）；两头渐隐是改 bar 自己的 class，不能再触发滚动
            if (window.MutationObserver) new MutationObserver(function (records) {
                if (records.some(function (r) { return r.target !== bar && r.target !== ink; })) move(false);
            }).observe(bar, { subtree: true, attributes: true, attributeFilter: ['class'] });
            window.addEventListener('resize', function () { move(true); });
            if (document.fonts && document.fonts.ready) document.fonts.ready.then(function () { move(true); });
        });
                });
                part(function () {
        // 侧栏切换 App
        var appSwitch = document.querySelector('[data-app-switch]');
        var appMenu = appSwitch && appSwitch.querySelector('[data-app-menu]');
        if (appMenu) {
            var appToggle = appSwitch.querySelector('[data-app-toggle]');
            var appSearch = appSwitch.querySelector('[data-app-search]');
            var appNone = appSwitch.querySelector('[data-app-none]');
            var filterApps = function (q) {
                var shown = 0;
                q = q.trim().toLowerCase();
                each(appMenu.querySelectorAll('.app-opt'), function (o) {
                    var hit = !q || o.getAttribute('data-search').toLowerCase().indexOf(q) >= 0;
                    o.hidden = !hit;
                    if (hit) shown++;
                });
                appNone.hidden = shown > 0;
            };
            var openApps = function (on) {
                appMenu.hidden = !on;
                appSwitch.classList.toggle('open', on);
                appToggle.setAttribute('aria-expanded', on ? 'true' : 'false');
                if (on && appSearch) { appSearch.value = ''; filterApps(''); appSearch.focus(); }
            };
            appToggle.addEventListener('click', function (e) { e.stopPropagation(); openApps(appMenu.hidden); });
            document.addEventListener('click', function (e) { if (!appMenu.hidden && !appSwitch.contains(e.target)) openApps(false); });
            document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && !appMenu.hidden) { openApps(false); appToggle.focus(); } });
            if (appSearch) {
                appSearch.addEventListener('input', function () { filterApps(appSearch.value); });
                appSearch.addEventListener('keydown', function (e) {
                    if (e.key !== 'Enter') return;
                    e.preventDefault();
                    var first = appMenu.querySelector('.app-opt:not([hidden])');
                    if (first) navigate(first.getAttribute('href'));
                });
            }
        }
                });
                part(function () {
        // 新令牌：倒计时结束前不能关闭，逼自己先复制保存
        var tokenDlg = document.getElementById('token-dialog');
        if (tokenDlg) {
            var left = parseInt(tokenDlg.getAttribute('data-countdown'), 10) || 10;
            var tokenClose = tokenDlg.querySelector('[data-role=close]');
            var tokenLeft = tokenDlg.querySelector('[data-role=left]');
            tokenDlg.showModal();
            var tokenTimer = setInterval(function () {
                left--;
                if (left > 0) { tokenLeft.textContent = left; return; }
                clearInterval(tokenTimer);
                tokenDlg.removeAttribute('data-busy');
                tokenClose.disabled = false;
                tokenClose.textContent = '我已保存，关闭';
                tokenDlg.querySelector('[data-role=hint]').textContent = '关闭后不能再查看这个令牌';
            }, 1000);
            tokenClose.addEventListener('click', function () { tokenDlg.close(); });
        }
                });
                part(function () {
        // 表格多选：表头 [data-check-all]，每行 [data-check]，选中后显示 [data-bulkbar]，数量写进 [data-check-count]；
        // 一个页面有几组时属性值写组名（比如 data-check-all="req"），不写是默认的一组
        each(document.querySelectorAll('[data-check-all]'), function (checkAll) {
            var group = checkAll.getAttribute('data-check-all') || '';
            var pick = function (attr) {
                return Array.prototype.filter.call(document.querySelectorAll('[' + attr + ']'), function (el) { return (el.getAttribute(attr) || '') === group; });
            };
            var boxes = pick('data-check'), bulkbars = pick('data-bulkbar'), checkCount = pick('data-check-count');
            var syncChecks = function () {
                var n = 0;
                each(boxes, function (b) {
                    if (b.checked) n++;
                    var tr = b.closest('tr');
                    if (tr) tr.classList.toggle('row-checked', b.checked);
                });
                checkAll.checked = n > 0 && n === boxes.length;
                checkAll.indeterminate = n > 0 && n < boxes.length;
                each(checkCount, function (el) { el.textContent = n; });
                each(bulkbars, function (bar) { bar.hidden = n === 0; });
            };
            checkAll.addEventListener('change', function () {
                each(boxes, function (b) { b.checked = checkAll.checked; });
                syncChecks();
            });
            each(boxes, function (b) { b.addEventListener('change', syncChecks); });
            syncChecks();
        });
                });
                part(function () {
                    // 列表筛选：data-filter 指向表格或卡片容器，data-filter-item 指定逐条筛选的元素
                    each(document.querySelectorAll('[data-filter]'), function (input) {
                        var target = document.querySelector(input.getAttribute('data-filter'));
                        if (!target) return;
                        var itemSel = input.getAttribute('data-filter-item') || 'tbody tr';
                        var rows = target.querySelectorAll(itemSel);
                        var none = document.querySelector(input.getAttribute('data-filter') + '-none');
                        var holder = target.matches('[data-collapse]') ? target : target.querySelector('[data-collapse]');
                        input.addEventListener('input', function () {
                            var q = input.value.trim().toLowerCase();
                            var shown = 0;
                            each(rows, function (r) {
                                var hit = !q || r.textContent.toLowerCase().indexOf(q) >= 0;
                                r.hidden = !hit;
                                if (hit) shown++;
                            });
                            if (holder) holder.classList.toggle('filtering', !!q);
                            if (none) none.hidden = shown > 0;
                        });
                    });
                    // 加减按钮 [data-step="输入框 id" data-delta="1"]、快捷数字 [data-set="输入框 id" data-v="10"]，按输入框的 min / max 限住
                    document.addEventListener('click', function (e) {
                        var st = e.target.closest('[data-step]'), qs = e.target.closest('[data-set]');
                        var input = st ? document.getElementById(st.getAttribute('data-step')) : qs ? document.getElementById(qs.getAttribute('data-set')) : null;
                        if (!input || input.disabled) return;
                        var min = parseInt(input.min, 10), max = parseInt(input.max, 10);
                        var v = st ? (parseInt(input.value, 10) || 0) + parseInt(st.getAttribute('data-delta'), 10) : parseInt(qs.getAttribute('data-v'), 10);
                        if (!isNaN(min)) v = Math.max(min, v);
                        if (!isNaN(max)) v = Math.min(max, v);
                        input.value = v;
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                    });
                    // 计划任务的时间框：不能选过去的时间，默认一小时后的整点
                    each(document.querySelectorAll('input[type=datetime-local][data-future]'), function (el) {
                        var pad = function (n) { return (n < 10 ? '0' : '') + n; };
                        var fmt = function (d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes()); };
                        var now = new Date();
                        el.min = fmt(now);
                        if (!el.value) {
                            var d = new Date(now.getTime() + 2 * 3600000);
                            d.setMinutes(0, 0, 0);
                            el.value = fmt(d);
                        }
                    });
                    // 输入框字数：<span data-count-for="输入框 id">，有 maxlength 时显示「已输入 / 最多」
                    each(document.querySelectorAll('[data-count-for]'), function (el) {
                        var f = document.getElementById(el.getAttribute('data-count-for'));
                        if (!f) return;
                        var max = parseInt(f.getAttribute('maxlength'), 10) || 0;
                        var paint = function () {
                            var n = f.value.length;
                            el.textContent = max ? n + ' / ' + max : String(n);
                            el.classList.toggle('near', max > 0 && n > max * 0.9);
                        };
                        f.addEventListener('input', paint);
                        paint();
                    });
                    each(document.querySelectorAll('[data-autosubmit]'), function (el) {
                        el.addEventListener('change', function () {
                            if (el.form.requestSubmit) el.form.requestSubmit(); else el.form.submit();
                        });
                    });
                });
                if (!opts.cached && !opts.refresh) part(function () {
        // 数字从 0 滚到实际值（只处理纯数字，版本号之类的不动）；提交表单后的同页刷新不滚
        if (!reduceMotion && window.requestAnimationFrame) {
            each(document.querySelectorAll('.metric-value:not(.mono), .app-stat b:not(.mono)'), function (el) {
                var node = null;
                for (var c = el.firstChild; c && !node; c = c.nextSibling) {
                    if (c.nodeType === 3 && c.nodeValue.trim()) node = c;
                    else if (c.nodeType === 1 && c.tagName === 'SPAN' && !c.children.length && c.textContent.trim()) node = c.firstChild;
                    else if (c.nodeType === 1) break;
                }
                if (!node) return;
                var m = /^(\s*)(\+?)([\d,]+)(\.\d+)?(\s*)$/.exec(node.nodeValue);
                if (!m) return;
                var target = parseFloat((m[3] + (m[4] || '')).replace(/,/g, ''));
                if (!(target >= 10) || !isFinite(target)) return;
                var dec = m[4] ? m[4].length - 1 : 0, comma = m[3].indexOf(',') >= 0;
                var fmt = function (v) {
                    var s = v.toFixed(dec);
                    if (comma) {
                        var p = s.split('.');
                        p[0] = p[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');
                        s = p.join('.');
                    }
                    return m[1] + m[2] + s + m[5];
                };
                var t0 = 0, dur = 900;
                var step = function (t) {
                    if (!t0) t0 = t;
                    var k = Math.min(1, (t - t0) / dur);
                    if (k >= 1) el.removeAttribute('data-roll');
                    node.nodeValue = fmt(k < 1 ? target * (1 - Math.pow(1 - k, 3)) : target);
                    if (k < 1) requestAnimationFrame(step);
                };
                el.setAttribute('data-roll', '');
                node.nodeValue = fmt(0);
                requestAnimationFrame(step);
            });
        }
                });
                part(function () {
        // 一段时间全是 0 的柱状图，给一句话说明，不然像是没加载出来
        each(document.querySelectorAll('.chart'), function (c) {
            var vals = c.querySelectorAll('.chart-val');
            if (!vals.length || Array.prototype.some.call(vals, function (v) { return v.textContent.trim(); })) return;
            c.classList.add('is-empty');
            var tip = document.createElement('div');
            tip.className = 'chart-empty';
            tip.innerHTML = '<span><svg class="i"><use href="#i-chart"/></svg>这段时间还没有数据</span>';
            c.appendChild(tip);
        });
                });
                part(function () {
        // 上传弹窗：XHR 上传以显示进度
        var uf = document.getElementById('upload-form');
        if (uf) {
            var dlg = uf.closest('dialog');
            var file = uf.querySelector('input[type=file]');
            var drop = uf.querySelector('.drop');
            var nameEl = uf.querySelector('[data-role=name]');
            var metaEl = uf.querySelector('[data-role=meta]');
            var submit = uf.querySelector('[data-role=submit]');
            var bar = uf.querySelector('.progress span');
            var MAX = 200 * 1024 * 1024;
            var initialName = nameEl.textContent, initialMeta = metaEl.textContent;

            function reset() {
                uf.classList.remove('uploading');
                dlg.removeAttribute('data-busy');
                bar.style.width = '0';
                submit.textContent = '上传';
                submit.disabled = !file.files.length;
            }
            function pick() {
                var f = file.files[0];
                if (!f) { drop.classList.remove('picked'); nameEl.textContent = initialName; metaEl.textContent = initialMeta; submit.disabled = true; return; }
                if (!/\.zip$/i.test(f.name)) { toast('请选择 .zip 文件', 'err'); file.value = ''; return pick(); }
                if (f.size > MAX) { toast('文件超过 200 MB 上限', 'err'); file.value = ''; return pick(); }
                drop.classList.add('picked');
                nameEl.textContent = f.name;
                metaEl.textContent = f.size < 1048576 ? Math.max(1, Math.round(f.size / 1024)) + ' KB' : (f.size / 1048576).toFixed(1) + ' MB';
                submit.disabled = false;
            }
            file.addEventListener('change', pick);
            ['dragenter', 'dragover'].forEach(function (ev) {
                drop.addEventListener(ev, function (e) { e.preventDefault(); drop.classList.add('over'); });
            });
            ['dragleave', 'drop'].forEach(function (ev) {
                drop.addEventListener(ev, function (e) { e.preventDefault(); drop.classList.remove('over'); });
            });
            drop.addEventListener('drop', function (e) {
                if (!e.dataTransfer.files.length) return;
                file.files = e.dataTransfer.files;
                pick();
            });
            dlg.addEventListener('close', function () {
                if (dlg.hasAttribute('data-busy')) return;
                file.value = '';
                pick();
            });
            uf.addEventListener('submit', function (e) {
                e.preventDefault();
                if (!file.files.length) return;
                var xhr = new XMLHttpRequest();
                xhr.open('POST', uf.getAttribute('action'));
                xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
                xhr.upload.onprogress = function (ev) {
                    if (!ev.lengthComputable) return;
                    var pct = Math.round(ev.loaded / ev.total * 100);
                    bar.style.width = pct + '%';
                    submit.textContent = pct < 100 ? '上传中 ' + pct + '%' : '服务器处理中…';
                };
                xhr.onload = function () {
                    var res = null;
                    try { res = JSON.parse(xhr.responseText); } catch (err) {}
                    if (res && res.ok) {
                        sessionStorage.setItem('ota.toast', JSON.stringify({ msg: res.msg, type: 'ok' }));
                        dlg.removeAttribute('data-busy');
                        dlg.close();
                        go(uf.getAttribute('data-done'), { push: true, reload: true });
                        return;
                    }
                    reset();
                    if (res) toast(res.msg, 'err');
                    else if (xhr.status === 413) toast('文件超过服务器上传限制（Nginx client_max_body_size）', 'err');
                    else if (xhr.responseURL && xhr.responseURL.indexOf('/login') >= 0) toast('登录已过期，请刷新页面后重新登录', 'err');
                    else toast('上传失败（HTTP ' + xhr.status + '）', 'err');
                };
                xhr.onerror = function () { reset(); toast('网络错误，上传失败', 'err'); };
                uf.classList.add('uploading');
                dlg.setAttribute('data-busy', '');
                submit.disabled = true;
                submit.textContent = '上传中 0%';
                xhr.send(new FormData(uf));
            });
        }

        // 上传弹窗的两种方式：上传 ZIP 包 / 外部链接
        var modes = document.querySelector('[data-role=upload-modes]');
        if (modes) {
            var mdlg = modes.closest('dialog');
            var setMode = function (mode) {
                each(modes.querySelectorAll('[data-upload-mode]'), function (a) {
                    a.classList.toggle('on', a.getAttribute('data-upload-mode') === mode);
                });
                each(mdlg.querySelectorAll('[data-upload-panel]'), function (p) {
                    p.hidden = p.getAttribute('data-upload-panel') !== mode;
                });
            };
            each(document.querySelectorAll('[data-upload-mode]'), function (el) {
                el.addEventListener('click', function (e) {
                    if (el.tagName === 'A') e.preventDefault();
                    if (mdlg.hasAttribute('data-busy')) return;
                    setMode(el.getAttribute('data-upload-mode'));
                });
            });
        }

        // 外部链接：服务器要把目录里的每个文件下载一遍算哈希，链接慢的时候要好几分钟。
        // 填好地址就开始在后台读，这里显示读到哪了；点「添加」时读完了马上就好，没读完就等读完自动添加
        var lf = document.getElementById('link-form');
        if (lf) {
            var ldlg = lf.closest('dialog'), lurl = lf.elements.externalUrl;
            var lr = function (r) { return lf.querySelector('[data-role=' + r + ']'); };
            var lsubmit = lr('submit'), lerror = lr('error'), lbox = lr('read'), lbar = lr('read-bar'), ltitle = lr('read-title'),
                lpct = lr('read-pct'), ltext = lr('read-text'), lslow = lr('read-slow'), lstop = lr('read-stop');
            // seq：每次开始、取消都加一，晚到的「开始读」响应据此认出自己已经作废
            var read = { id: null, url: '', state: '', timer: null, typing: null, add: false, seq: 0 };
            var lsize = function (b) { return b >= 1048576 ? (b / 1048576).toFixed(1) + ' MB' : Math.max(b ? 1 : 0, Math.round(b / 1024)) + ' KB'; };
            var lclock = function (s) { s = Math.round(s); return s < 60 ? s + ' 秒' : Math.floor(s / 60) + ' 分 ' + (s % 60) + ' 秒'; };
            var ljson = function (url, body) {
                return fetch(url, { method: body ? 'POST' : 'GET', body: body, cache: 'no-store', headers: { 'X-Requested-With': 'XMLHttpRequest' } }).then(function (r) {
                    if (r.redirected && r.url.indexOf('/login') >= 0) throw new Error('登录已过期，请刷新页面后重新登录');
                    if (r.status === 504) throw new Error('等太久了，反向代理断开了连接。过会儿刷新，看看版本加上没有');
                    if (!r.ok) throw new Error('请求失败（HTTP ' + r.status + '）');
                    return r.json();
                });
            };
            var lbusy = function (on, label) {
                ldlg.toggleAttribute('data-busy', on);
                lsubmit.disabled = on;
                lsubmit.textContent = label || '添加';
            };
            var lfail = function (msg) {
                read.add = false;
                lstop.hidden = true;
                lbusy(false, read.state === 'failed' ? '重新读取并添加' : '添加');
                lerror.textContent = msg;
                lerror.hidden = false;
            };
            var paint = function (s) {
                var secs = (s.elapsed || 0) / 1000, speed = secs >= 3 ? s.bytes / secs : 0;
                lbox.hidden = false;
                lstop.hidden = s.state === 'done' || s.state === 'failed';
                if (!s.total) {
                    ltitle.textContent = '正在读取 metadata.json';
                    lpct.textContent = '';
                    lbar.style.width = '0';
                    ltext.textContent = '已用 ' + lclock(secs);
                    return;
                }
                var p = Math.floor(s.done * 100 / s.total);
                ltitle.textContent = s.state === 'done' ? '读完了：' + s.files + ' 个文件，' + s.size : '正在读取链接里的文件';
                lpct.textContent = s.state === 'done' ? '' : s.done + ' / ' + s.total;
                lbar.style.width = (s.state === 'done' ? 100 : p) + '%';
                var now = (s.reading || [])[0];
                ltext.textContent = s.state === 'done' ? '用了 ' + lclock(secs) + '，点「添加」马上就好'
                    : '已下载 ' + lsize(s.bytes) + (speed ? ' · ' + lsize(speed) + '/秒' : '') + ' · 已用 ' + lclock(secs)
                      + (now ? ' · 正在下载 ' + now.path.split('/').pop().slice(0, 28) + (now.size > 0 ? '（' + lsize(now.read) + ' / ' + lsize(now.size) + '）' : '') : '');
                // App 也是从这个地址下载的，这么慢的话设备更新也会很慢
                var slow = s.state !== 'done' && secs >= 15 && speed < 100 * 1024;
                lslow.hidden = !slow;
                if (slow) lslow.textContent = '这个链接下载很慢（约 ' + lsize(speed) + '/秒）。App 也是从这里下载更新的，设备那边一样会很慢，建议把 dist 放到 CDN 或对象存储上';
            };
            var stop = function () {
                clearTimeout(read.timer);
                read.timer = null;
            };
            var poll = function () {
                var id = read.id;
                ljson('/admin/addUrl/read/' + encodeURIComponent(id)).then(function (s) {
                    if (read.id !== id) return;
                    if (!s.ok) { read.state = 'failed'; read.id = null; return lfail(s.msg); }
                    paint(s);
                    if (s.state === 'failed') { read.state = 'failed'; read.id = null; return lfail(s.msg); }
                    if (s.state === 'done') {
                        read.state = 'done';
                        if (read.add) submitAdd(); else lbusy(false);
                        return;
                    }
                    read.timer = setTimeout(poll, 800);
                }, function () {
                    if (read.id === id) read.timer = setTimeout(poll, 2000);
                });
            };
            var start = function (force) {
                var url = lurl.value.trim();
                if (!/^https?:\/\/[^\s/]+\/?\S*$/i.test(url)) return false;
                if (!force && url === read.url && read.state !== 'failed' && read.state !== '') {
                    if (read.state === 'reading' && !read.timer && read.id) poll();
                    return true;
                }
                stop();
                var seq = ++read.seq;
                read.url = url;
                read.id = null;
                read.state = 'reading';
                lerror.hidden = true;
                lslow.hidden = true;
                paint({ total: 0, elapsed: 0, bytes: 0 });
                ljson('/admin/addUrl/read', new URLSearchParams({ externalUrl: url })).then(function (r) {
                    if (read.seq !== seq) {
                        // 等它回来的时候已经点了取消：服务器那边刚开始读的也停掉
                        if (r.ok && r.id && read.state === 'cancelled') cancelRemote(r.id);
                        return;
                    }
                    if (!r.ok) { read.state = 'failed'; lbox.hidden = true; return lfail(r.msg); }
                    read.id = r.id;
                    poll();
                }, function (err) { if (read.seq === seq) { read.state = 'failed'; lfail(err.message); } });
                return true;
            };
            var cancelRemote = function (id) {
                ljson('/admin/addUrl/read/' + encodeURIComponent(id) + '/cancel', new URLSearchParams()).catch(function () {});
            };
            // 取消下载：服务器那边不再读这个链接；之后改了地址或者再点「添加」才重新读
            lstop.addEventListener('click', function () {
                var id = read.id;
                read.seq++;
                stop();
                read.id = null;
                read.state = 'cancelled';
                read.add = false;
                lbox.hidden = true;
                lerror.hidden = true;
                lbusy(false);
                if (id) cancelRemote(id);
                if (window.otaToast) window.otaToast('已取消下载');
            });
            var submitAdd = function () {
                lbusy(true, '正在添加…');
                var body = new URLSearchParams(new FormData(lf));
                body.set('read', read.id || '');
                ljson(lf.getAttribute('action'), body).then(function (res) {
                    if (!res.ok) {
                        if (res.reread) { read.state = ''; start(true); wait(); return; }
                        return lfail(res.msg);
                    }
                    read.id = null;
                    read.state = '';
                    read.url = '';
                    sessionStorage.setItem('ota.toast', JSON.stringify({ msg: res.msg, type: 'ok' }));
                    ldlg.removeAttribute('data-busy');
                    ldlg.close();
                    go(lf.getAttribute('data-done'), { push: true, reload: true });
                }, function (err) { lfail(err.message); });
            };
            // 等读完自动添加：只禁用按钮，不锁弹窗，链接慢的话可以先关掉，后台接着读，回来接着看进度
            var wait = function () {
                read.add = true;
                lsubmit.disabled = true;
                lsubmit.textContent = '读完自动添加…';
            };
            lurl.addEventListener('input', function () {
                clearTimeout(read.typing);
                read.typing = setTimeout(function () { start(false); }, 900);
            });
            lurl.addEventListener('change', function () { clearTimeout(read.typing); start(false); });
            lurl.addEventListener('focus', function () { if (lurl.value.trim()) start(false); });
            document.addEventListener('click', function (e) {
                if (e.target.closest('[data-upload-mode=link]') && lurl.value.trim()) setTimeout(function () { start(false); }, 0);
            });
            lf.addEventListener('submit', function (e) {
                e.preventDefault();
                lerror.hidden = true;
                if (read.state === 'done' && read.url === lurl.value.trim() && read.id) return submitAdd();
                if (!start(read.state === 'failed' || read.state === 'cancelled')) return lfail('请填写 dist 目录地址，以 http:// 或 https:// 开头');
                wait();
            });
            ldlg.addEventListener('close', function () {
                stop();
                read.add = false;
                if (!ldlg.hasAttribute('data-busy')) { lerror.hidden = true; lbusy(false); }
            });
            window.addEventListener('ota:leave', stop);
        }
                });
                part(collapseLists);
                part(cardTables);
                part(function () {
                    lookPaint();
                    if (!isMac) each(document.querySelectorAll('[data-kbd-mod]'), function (k) { k.textContent = MOD; });
                    cmdk.bind();
                });
                part(function () {
                    guidePage();
                    syncGuideUi();
                    if (!page || opts.refresh) return;
                    setTimeout(function () {
                        if (token !== mountSeq || document.querySelector('dialog[open]') || guide.active()) return;
                        if (side && !seen.welcome && !welcomeTried) { welcomeTried = true; startWelcome(); }
                        else if (page === 'agent' && !seen.agent && !document.querySelector('.preview-strip')) startPage();
                        else if (side) hintPage();
                    }, opts.cached ? 350 : 700);
                });
                part(tabsTrack);
                part(function () {
        // 看板（有「实时」标记的页面，或者不显示标记、只带 data-live-refresh 的）开着时定时悄悄刷新数据：默认 30 秒，
        // 标记上写了 data-live（秒）的按它来（数据大屏 10 秒）；页面在后台、开着弹窗菜单、正在输入时跳过
        clearInterval(liveTimer);
        liveTimer = null;
        liveRing(0);
        var liveTag = document.querySelector('.live-tag, [data-live-refresh]');
        if (!liveTag) return;
        var liveAt = location.pathname + location.search;
        var liveEvery = parseInt(liveTag.getAttribute('data-live'), 10);
        var liveTick = function () {
            if (location.pathname + location.search !== liveAt) { clearInterval(liveTimer); liveTimer = null; return; }
            if (document.hidden || !quiet() || document.querySelector('[data-pop-menu]:not([hidden]), [data-board].editing')) return;
            go(location.href, { reload: true, keep: true, silent: true, idle: true });
        };
        liveMs = liveEvery >= 5 ? liveEvery * 1000 : 30000;
        liveTimer = setInterval(liveTick, liveMs);
        liveRing(liveMs);
        // 自己定了间隔的页面（大屏）从后台回到前台时马上补一次，不用等下一轮
        if (liveEvery >= 5) document.addEventListener('visibilitychange', function () { if (!document.hidden && liveTimer) liveTick(); });
                });
                var fn = page && PAGES[page];
                if (fn) part(fn);
            });
        }

        return { mount: mount, leave: leave, toast: toast, progress: progress, quiet: quiet, navigate: navigate, todos: todosUpdate };
}
