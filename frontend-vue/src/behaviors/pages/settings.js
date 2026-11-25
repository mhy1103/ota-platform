export default function () {
    var root = document.documentElement.style;
    var form = document.getElementById('appearance-form');
    var hint = document.getElementById('appearance-hint');
    var SIDE_VARS = ['', '-bg', '-2', '-3', '-line', '-ink', '-ink-2', '-hi', '-active', '-active-ink', '-shadow', '-mark', '-mark-in', '-menu', '-menu-line'];
    var touched = {};

    // 预览只改这台浏览器上 <html> 的内联变量；没保存就离开页面时全部撤掉
    function set(name, value) { touched[name] = 1; root.setProperty(name, value); }
    window.addEventListener('ota:leave', function () { Object.keys(touched).forEach(function (n) { root.removeProperty(n); }); });

    function hexToRgb(hex) {
        var v = parseInt(hex.slice(1), 16);
        return ((v >> 16) & 255) + ',' + ((v >> 8) & 255) + ',' + (v & 255);
    }
    function applyTheme(input) {
        var dark = document.documentElement.getAttribute('data-mode') === 'dark';
        var a = input.getAttribute('data-accent'), h = input.getAttribute('data-hover'), rgb = hexToRgb(a);
        var side = input.getAttribute('data-side').split(' '), sideDark = input.getAttribute('data-side-dark').split(' ');
        if (dark) { a = h = input.getAttribute('data-accent-dark'); rgb = hexToRgb(a); }
        set('--accent', a);
        set('--accent-2', h);
        set('--accent-ink', a);
        set('--accent-soft', 'rgba(' + rgb + (dark ? ',.22)' : ',.14)'));
        set('--accent-tint', 'rgba(' + rgb + (dark ? ',.1)' : ',.07)'));
        set('--accent-line', 'rgba(' + rgb + (dark ? ',.4)' : ',.32)'));
        set('--side-top', dark ? sideDark[0] : side[0]);
        set('--side-bottom', dark ? sideDark[1] : side[1]);
        set('--side-pill-ink', side[2]);
        set('--side-menu-solid', dark ? sideDark[2] : side[3]);
    }
    function applySidebar(input) {
        var p = input.value === 'dark' ? '--sbd' : input.value === 'light' ? '--sbl' : '--sbt';
        SIDE_VARS.forEach(function (v) { set('--side' + v, 'var(' + p + v + ')'); });
    }
    function applyRadius(input) {
        set('--r', input.getAttribute('data-card') + 'px');
        set('--r-lg', input.getAttribute('data-large') + 'px');
        set('--r-sm', input.getAttribute('data-control') + 'px');
        set('--r-xs', input.getAttribute('data-small') + 'px');
    }
    // 卡片、背景、按钮、表格：先撤掉这一组所有选项用到的变量，再设上选中的（深色模式有另给的值就用深色的）
    var LOOKS = { width: '版面', cards: '卡片', bg: '背景', buttons: '按钮', tables: '表格' };
    function vars(input, attr) {
        try { return JSON.parse(input.getAttribute(attr) || '{}'); } catch (e) { return {}; }
    }
    function applyLook(input) {
        var dark = document.documentElement.getAttribute('data-mode') === 'dark';
        Array.prototype.forEach.call(form.querySelectorAll('input[name=' + input.name + ']'), function (r) {
            Object.keys(vars(r, 'data-vars')).concat(Object.keys(vars(r, 'data-vars-dark'))).forEach(function (k) { root.removeProperty(k); });
        });
        var v = vars(input, 'data-vars'), d = dark ? vars(input, 'data-vars-dark') : {};
        Object.keys(v).forEach(function (k) { set(k, d[k] || v[k]); });
        Object.keys(d).forEach(function (k) { if (!(k in v)) set(k, d[k]); });
    }
    function picked(name) { return form.querySelector('input[name=' + name + ']:checked'); }
    function describe() {
        var names = ['theme', 'radius', 'sidebar', 'width', 'cards', 'bg', 'buttons', 'tables'].map(function (n) {
            var i = picked(n), label = i && i.parentNode.querySelector('span');
            return label ? (label.querySelector('b') || label).textContent + (n === 'sidebar' ? '侧栏' : LOOKS[n] || '') : '';
        });
        hint.textContent = '当前选择：' + names.filter(Boolean).join(' · ');
        Array.prototype.forEach.call(form.querySelectorAll('[data-pick]'), function (b) {
            var p = vars(b, 'data-pick');
            b.classList.toggle('on', Object.keys(p).every(function (n) { var i = picked(n); return i && i.value === p[n]; }));
        });
    }
    form.addEventListener('click', function (e) {
        var b = e.target.closest('[data-pick]');
        if (!b) return;
        var p = vars(b, 'data-pick');
        Object.keys(p).forEach(function (n) {
            var i = form.querySelector('input[name=' + n + '][value=' + p[n] + ']');
            if (!i || i.checked) return;
            i.checked = true;
            i.dispatchEvent(new Event('change', { bubbles: true }));
        });
        describe();
    });
    // 保存后自己这台浏览器也跟着系统设置走，不然以前在外观菜单里选过的侧栏会盖住刚保存的
    form.addEventListener('submit', function () {
        try {
            var u = JSON.parse(localStorage.getItem('ota.ui') || '{}') || {};
            delete u.sidebar;
            delete u.sv;
            localStorage.setItem('ota.ui', JSON.stringify(u));
        } catch (e) {}
        if (window.otaPrefs) window.otaPrefs({ sidebar: '' });
        document.documentElement.removeAttribute('data-sidebar');
    });
    // 成员：点了「用默认外观」就不单独存，以后跟着超级管理员改；之后又自己改了哪项就还是自己的
    var follow = form.querySelector('[data-follow]');
    form.addEventListener('change', function (e) {
        if (e.target.name === 'theme') applyTheme(e.target);
        if (e.target.name === 'radius') applyRadius(e.target);
        if (e.target.name === 'sidebar') applySidebar(e.target);
        if (LOOKS[e.target.name]) applyLook(e.target);
        if (follow) follow.value = '0';
        describe();
    });
    // 恢复默认：超级管理员回到刚装好时的样子，成员选回超级管理员设的默认外观
    document.getElementById('appearance-reset').addEventListener('click', function () {
        var def = vars(form, 'data-default');
        ['theme', 'radius', 'sidebar', 'width', 'cards', 'bg', 'buttons', 'tables'].forEach(function (n) {
            var i = (def[n] && form.querySelector('input[name=' + n + '][value="' + def[n] + '"]')) || form.querySelector('input[name=' + n + ']');
            if (!i) return;
            i.checked = true;
            if (n === 'theme') applyTheme(i);
            else if (n === 'radius') applyRadius(i);
            else if (n === 'sidebar') applySidebar(i);
            else applyLook(i);
        });
        if (follow && form.hasAttribute('data-member')) follow.value = '1';
        describe();
    });
    describe();
    var brandInput = form.querySelector('[data-brand-input]'), brandPreview = form.querySelector('[data-brand-preview]');
    if (brandInput && brandPreview) brandInput.addEventListener('input', function () { brandPreview.textContent = brandInput.value.trim() || brandInput.placeholder; });

    // 访问域名：添加、删除行；一行都没有时留一个空行直接填
    var domList = document.querySelector('[data-dom-list]');
    var domRow = document.querySelector('template[data-dom-row]');
    if (domList && domRow) {
        var addRow = function () {
            domList.appendChild(domRow.content.firstElementChild.cloneNode(true));
            return domList.lastElementChild;
        };
        if (!domList.children.length) addRow();
        // 接口对外地址没手动改过时，跟着第一个接口域名走（带上端口）
        var pub = document.querySelector('[data-dom-public]');
        var derive = function () {
            var rows = domList.querySelectorAll('.dom-row');
            for (var i = 0; i < rows.length; i++) {
                var host = rows[i].querySelector('input').value.trim().toLowerCase().replace(/^https?:\/\//, '').replace(/\/.*$/, '');
                var use = rows[i].querySelector('select[name=use]').value;
                if (host && host.indexOf('*.') !== 0 && use !== 'admin') {
                    return (rows[i].querySelector('select[name=scheme]').value === 'http' ? 'http://' : 'https://') + host;
                }
            }
            return '';
        };
        // 只差端口（反向代理把端口去掉了、以前漏写了）也当作没改过，打开时直接补上
        var bare = function (u) { return String(u || '').toLowerCase().replace(/\/+$/, '').replace(/^(https?:\/\/[^/:]+):\d+/, '$1'); };
        var pubFollow = pub && (!pub.value || pub.value === derive() || (derive() && bare(pub.value) === bare(derive())));
        if (pubFollow && pub && derive()) pub.value = derive();
        var dlg = document.getElementById('domains-dialog');
        if (pub) {
            pub.addEventListener('input', function () { pubFollow = !pub.value; });
            dlg.addEventListener('input', function (e) { if (pubFollow && e.target !== pub) pub.value = derive(); });
            dlg.addEventListener('change', function (e) { if (pubFollow && e.target !== pub) pub.value = derive(); });
        }
        dlg.addEventListener('click', function (e) {
            if (e.target.closest('[data-dom-add]')) addRow().querySelector('input').focus();
            var rm = e.target.closest('[data-dom-remove]');
            if (!rm) return;
            rm.closest('.dom-row').remove();
            if (!domList.children.length) addRow();
            if (pubFollow && pub) pub.value = derive();
        });
    }

    // 文件存储：测试连接、开始切换，切换时后台复制文件，这里轮询进度
    var sform = document.querySelector('[data-storage-form]');
    var run = document.querySelector('[data-storage-run]');
    if (sform && run) {
        var fields = sform.querySelector('[data-s3-fields]');
        var result = sform.querySelector('[data-storage-result]');
        var r$ = function (role) { return run.querySelector('[data-role="' + role + '"]'); };
        var type = function () { var c = sform.querySelector('input[name=type]:checked'); return c ? c.value : 'local'; };
        var syncType = function () { fields.hidden = type() !== 's3'; result.hidden = true; };
        var show = function (ok, msg) {
            result.hidden = false;
            result.className = 'storage-result ' + (ok ? 'ok' : 'err');
            result.textContent = msg;
        };
        var post = function (url) {
            return fetch(url, {
                method: 'POST',
                body: new URLSearchParams(new FormData(sform)),
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            }).then(function (r) {
                if (r.redirected && r.url.indexOf('/login') >= 0) throw new Error('登录已过期，请刷新页面后重新登录');
                return r.json();
            });
        };
        var size = function (b) {
            if (b >= 1073741824) return (b / 1073741824).toFixed(2) + ' GB';
            if (b >= 1048576) return (b / 1048576).toFixed(1) + ' MB';
            if (b >= 1024) return (b / 1024).toFixed(1) + ' KB';
            return b + ' B';
        };
        // live：这次切换是在这个页面上看着跑的，换好了刷新页面；不是的话只把上次的结果摆出来
        var seenKey = 'ota.storageSeen';
        var askClear = function (s) {
            var go = function () {
                var b = r$('clear');
                b.classList.add('loading');
                fetch('/admin/settings/storage/clear', { method: 'POST', headers: { 'X-Requested-With': 'XMLHttpRequest' } })
                    .then(function (r) { return r.json(); })
                    .then(function (r) {
                        b.classList.remove('loading');
                        if (!r.ok) { r$('text').textContent = r.msg; return; }
                        poll(true);
                    }, function () { b.classList.remove('loading'); });
            };
            var title = '原来的存储连不上，要清空版本才能切换';
            var body = (s.error ? s.error + '\n\n' : '') + s.note;
            if (window.otaAsk) window.otaAsk(title, body, '清空并切换', go);
            else if (window.confirm(title + '\n\n' + body)) go();
        };
        var last = null;
        var poll = function (live) {
            fetch('/admin/settings/storage/status', { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
                .then(function (r) { return r.json(); })
                .then(function (s) {
                    if (!live && String(s.finishedAt) === localStorage.getItem(seenKey)) return;
                    last = s;
                    var running = s.state === 'running', done = s.state === 'done', failed = s.state === 'failed', confirm = s.state === 'confirm';
                    run.hidden = false;
                    run.classList.toggle('done', done && !s.missing && !s.error);
                    run.classList.toggle('warn', confirm || done && (s.missing > 0 || !!s.error));
                    run.classList.toggle('failed', failed);
                    run.setAttribute('data-finished', s.finishedAt || '');
                    var pct = s.total > 0 ? Math.round(s.done * 100 / s.total) : 0;
                    r$('bar').style.width = (done || confirm ? 100 : pct) + '%';
                    r$('count').textContent = s.total && (running || failed) ? s.done + ' / ' + s.total + ' 个文件' : '';
                    var box = confirm ? s.note : done ? s.error : null;
                    r$('note').hidden = !box;
                    r$('note').textContent = box || '';
                    r$('clear').hidden = !confirm;
                    r$('dismiss').hidden = !(!live && (done || confirm));
                    r$('actions').hidden = r$('clear').hidden && r$('dismiss').hidden;
                    if (running && s.step === 'check') {
                        r$('title').textContent = '正在检查原来的存储';
                        r$('text').textContent = '看看原来的存储能不能连上，连得上就开始复制';
                    } else if (running && s.step === 'verify') {
                        r$('title').textContent = '原来的存储连不上，正在核对新存储里的文件';
                        r$('text').textContent = s.error || '';
                    } else if (running && s.step === 'clear') {
                        r$('title').textContent = '正在清空版本，然后切换到' + s.target;
                        r$('text').textContent = s.error || '';
                    } else if (running) {
                        r$('title').textContent = '正在复制到' + s.target;
                        r$('text').textContent = '已复制 ' + s.copied + ' 个文件（' + size(s.copiedBytes) + '），新存储里已有的直接跳过。复制期间可以正常使用';
                    } else if (confirm) {
                        r$('title').textContent = '原来的存储连不上，还没切换';
                        r$('text').textContent = s.error || '';
                        if (live) askClear(s);
                    } else if (done) {
                        r$('title').textContent = '已切换到' + s.target;
                        r$('text').textContent = (s.note || '复制了 ' + s.copied + ' 个文件（' + size(s.copiedBytes) + '）' + (s.missing ? '，原存储里本来就缺 ' + s.missing + ' 个' : ''))
                            + (live ? '。正在刷新…' : '');
                        if (live) setTimeout(function () { window.otaNavigate ? window.otaNavigate(location.pathname + '#storage') : location.reload(); }, 1500);
                    } else if (failed) {
                        r$('title').textContent = '切换失败，还在用原来的存储';
                        r$('text').textContent = s.error || '';
                    }
                    if (running) setTimeout(function () { poll(live); }, 1000);
                })
                .catch(function () { setTimeout(function () { poll(live); }, 3000); });
        };
        r$('dismiss').addEventListener('click', function () {
            try { localStorage.setItem(seenKey, run.getAttribute('data-finished') || ''); } catch (e) { /* 存不了就只关这一次 */ }
            run.hidden = true;
        });
        r$('clear').addEventListener('click', function () { if (last) askClear(last); });
        sform.addEventListener('change', function (e) { if (e.target.name === 'type') syncType(); });
        sform.querySelector('[data-storage-test]').addEventListener('click', function (e) {
            var b = e.currentTarget;
            b.classList.add('loading');
            post('/admin/settings/storage/test').then(function (r) { show(r.ok, r.msg); }, function (err) { show(false, err.message); })
                .then(function () { b.classList.remove('loading'); });
        });
        sform.addEventListener('submit', function (e) {
            e.preventDefault();
            var b = sform.querySelector('[data-storage-start]');
            b.classList.add('loading');
            post('/admin/settings/storage/switch').then(function (r) {
                b.classList.remove('loading');
                if (!r.ok) { show(false, r.msg); return; }
                document.getElementById('storage-dialog').close();
                poll(true);
            }, function (err) { b.classList.remove('loading'); show(false, err.message); });
        });
        syncType();
        if (run.getAttribute('data-busy') === 'true') poll(true);
        else if (run.getAttribute('data-last') === 'true') poll(false);
    }

    // ---------- 备份和恢复的请求：超时、登录过期、被反向代理或网站防火墙拦下（返回的是网页）都换成看得懂的提示 ----------
    function pageError(status, text) {
        if (status === 413 || text.indexOf('Nginx缓冲区溢出') >= 0) return '请求太大，被反向代理拦下了（HTTP ' + status + '）';
        if (/防火墙|firewall|waf/i.test(text)) return '请求被网站防火墙拦截了（HTTP ' + status + '）';
        if (status >= 500) return '服务器没有正常响应（HTTP ' + status + '），稍后再试';
        return '请求失败（HTTP ' + status + '）';
    }
    function askJson(url, body, ms) {
        var ctl = window.AbortController ? new AbortController() : null;
        var timer = ctl ? setTimeout(function () { ctl.abort(); }, ms || 30000) : null;
        var opts = { method: body ? 'POST' : 'GET', credentials: 'same-origin', cache: 'no-store', headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' } };
        if (body) opts.body = body;
        if (ctl) opts.signal = ctl.signal;
        var lost = function (err) {
            clearTimeout(timer);
            throw new Error(err && err.name === 'AbortError' ? '服务器太久没有响应，请检查网络后重试' : '连不上服务器，请检查网络后重试');
        };
        return fetch(url, opts).then(function (r) {
            if (r.redirected && r.url.indexOf('/login') >= 0) throw new Error('登录已过期，请刷新页面后重新登录');
            return r.text().then(function (t) {
                clearTimeout(timer);
                try { return JSON.parse(t); } catch (e) { throw new Error(pageError(r.status, t)); }
            }, lost);
        }, lost);
    }

    // ---------- 备份下载、在服务器上备份一份：服务器在后台打包（按钮上显示进度），打包好再下载，文件大小是实际大小 ----------
    function linkDownload(f) {
        f.addEventListener('submit', function (e) {
            if (!f.hasAttribute('data-no-swap')) return;
            e.preventDefault();
            var btn = e.submitter || f.querySelector('button[type=submit]'), label = btn ? btn.innerHTML : '';
            if (btn && btn.disabled) return;
            var busy = f.getAttribute('data-busy-text') || '正在打包';
            var show = function (text) {
                if (!btn) return;
                btn.disabled = text != null;
                if (text == null) btn.innerHTML = label; else btn.textContent = text;
            };
            var fail = function (msg) {
                show(null);
                if (window.otaToast) window.otaToast(msg || '下载失败，请检查网络后重试', 'err');
            };
            show(busy + '…');
            askJson(f.getAttribute('action'), new URLSearchParams(new FormData(f))).then(function (d) {
                if (!d || !d.ok || !d.status) return fail(d && d.msg);
                var misses = 0;
                var poll = function () {
                    askJson(d.status, null, 15000).then(function (s) {
                        misses = 0;
                        if (!s.ok) return fail(s.msg);
                        if (s.state !== 'ready') {
                            show(s.written ? busy + ' ' + s.written : busy + '…');
                            setTimeout(poll, 800);
                            return;
                        }
                        if (!s.url) {
                            sessionStorage.setItem('ota.toast', JSON.stringify({ msg: s.msg, type: 'ok' }));
                            if (window.otaNavigate) window.otaNavigate(location.pathname + '#backup'); else location.reload();
                            return;
                        }
                        show(null);
                        var dlg = f.closest('dialog');
                        if (dlg && dlg.open) dlg.close();
                        f.reset();
                        if (s.msg && window.otaToast) window.otaToast(s.msg);
                        // 真正让浏览器打开这个地址（不走页面切换），浏览器按附件存成文件
                        var a = document.createElement('a');
                        a.href = s.url;
                        a.setAttribute('data-no-swap', '');
                        document.body.appendChild(a);
                        a.click();
                        a.remove();
                    }, function (err) {
                        if (++misses > 5) return fail(err && err.message);
                        setTimeout(poll, 2000);
                    });
                };
                poll();
            }, function (err) { fail(err && err.message); });
        });
    }
    var nowForm = document.querySelector('form[data-backup-now]');
    if (nowForm) linkDownload(nowForm);
    Array.prototype.forEach.call(document.querySelectorAll('form[data-close-on-submit]'), function (f) {
        if (f.hasAttribute('data-no-swap')) { linkDownload(f); return; }
        f.addEventListener('submit', function () {
            var d = f.closest('dialog');
            setTimeout(function () { if (d && d.open) d.close(); f.reset(); }, 300);
        });
    });

    // 服务器上的备份：下载（.zip 要设备份密码加密）、恢复（.otaproj 要输备份密码）
    var bdlg = document.getElementById('backup-stored-dialog'), bform = document.getElementById('backup-stored-form');
    if (bdlg && bform) {
        var brole = function (r) { return bform.querySelector('[data-role=' + r + ']'); };
        var reveal = function (el, on) {
            el.hidden = !on;
            Array.prototype.forEach.call(el.querySelectorAll('input'), function (i) { i.disabled = !on; i.required = on; });
        };
        document.addEventListener('click', function (e) {
            var b = e.target.closest('[data-backup-act]');
            if (!b) return;
            e.preventDefault();
            var act = b.getAttribute('data-backup-act'), name = b.getAttribute('data-name'), enc = b.getAttribute('data-enc') === 'true';
            bform.reset();
            bform.setAttribute('action', '/admin/settings/backup/stored/' + encodeURIComponent(name) + '/' + act);
            var dl = act === 'download';
            bform.toggleAttribute('data-no-swap', dl);
            reveal(brole('new-pwd'), dl && !enc);
            reveal(brole('old-pwd'), !dl && enc);
            brole('title').textContent = dl ? '下载备份' : '从这份备份恢复';
            brole('sub').textContent = name;
            brole('ok').textContent = dl ? '下载' : '恢复并重启';
            brole('ok').className = dl ? 'btn' : 'btn btn-danger';
            brole('ok').disabled = false;
            brole('warn').textContent = dl ? (enc ? '这份已经加密过，原样下载' : '下载时用你设的备份密码加密') : '整个后台会换成这份备份里的数据。先检查备份，通过了服务自动重启，所有人要重新登录';
            bdlg.showModal();
        });
        linkDownload(bform);
        bform.addEventListener('submit', function (e) {
            if (bform.hasAttribute('data-no-swap')) return;
            e.preventDefault();
            var ok = brole('ok'), label = ok.textContent;
            var fail = function (msg) {
                bdlg.removeAttribute('data-busy');
                ok.disabled = false;
                ok.textContent = label;
                if (window.otaToast) window.otaToast(msg || '恢复失败，请重试', 'err');
            };
            bdlg.setAttribute('data-busy', '');
            ok.disabled = true;
            ok.textContent = '正在检查备份…';
            askJson(bform.getAttribute('action'), new URLSearchParams(new FormData(bform))).then(function (r) {
                if (!r.ok) return fail(r.msg);
                watchCheck(r.check, fail);
            }, function (err) { fail(err.message); });
        });
    }

    // 有一份准备好的恢复：输登录密码后立即重启
    var applyForm = document.querySelector('.pc-apply');
    if (applyForm) applyForm.addEventListener('submit', function (e) {
        e.preventDefault();
        var b = applyForm.querySelector('button[type=submit]'), label = b.textContent;
        var fail = function (msg) { b.disabled = false; b.textContent = label; if (window.otaToast) window.otaToast(msg, 'err'); };
        b.disabled = true;
        b.textContent = '正在重启…';
        askJson(applyForm.getAttribute('action'), new URLSearchParams(new FormData(applyForm))).then(function (r) {
            if (!r.ok) return fail(r.msg);
            waitRestart(r.boot);
        }, function (err) { fail(err.message); });
    });

    // 服务器在后台解密、检查备份：通过了服务自己重启，接着等重启。onTick 每次轮询到「还在检查」时调用
    function watchCheck(url, fail, onTick) {
        var boot = null, misses = 0;
        // 重启很快（一两秒），可能没轮询到「restarting」新服务就起来了，它不认识这次检查、或者要重新登录。
        // 问不到结果时先看服务是不是已经换了一次启动
        var restarted = function (orElse) {
            if (!boot) return orElse();
            askJson('/install/status', null, 5000).then(function (st) {
                if (st.boot && st.boot !== boot) waitRestart(boot); else orElse();
            }, function () { waitRestart(boot); });
        };
        var poll = function () {
            askJson(url, null, 10000).then(function (s) {
                misses = 0;
                if (!s.ok) return restarted(function () { fail(s.msg); });
                boot = s.boot || boot;
                if (s.state === 'restarting') return waitRestart(boot);
                if (onTick) onTick();
                setTimeout(poll, 1000);
            }, function (err) {
                misses++;
                restarted(function () { if (misses >= 8) fail(err.message); else setTimeout(poll, 1500); });
            });
        };
        poll();
    }

    // 恢复后服务重启：盖一层提示，看到服务换了一次启动（boot 变了）就去登录页。
    // 备份大的话启动前解压要一会儿：显示等了多久（关了动画也看得出没卡住），久了给出排查办法，一直接着等
    function waitRestart(boot) {
        if (document.querySelector('.restart-veil')) return;
        Array.prototype.forEach.call(document.querySelectorAll('dialog[open]'), function (d) { d.removeAttribute('data-busy'); d.close(); });
        var veil = document.createElement('div');
        veil.className = 'restart-veil';
        veil.innerHTML = '<div><i class="spin"></i><b>正在恢复，服务重启中</b><span>备份大的话要等一会儿，好了会自动跳到登录页</span><small data-role="time">已等待 0 秒</small>'
            + '<p class="rv-help" hidden>等得有点久了。备份有几个 GB 时解压要几分钟；一直不好的话，看看服务器日志里有没有「重启失败」，或者在宝塔里重启这个 Java 项目。'
            + '<button class="btn btn-sm btn-secondary" type="button" data-role="login">打开登录页看看</button></p></div>';
        document.body.appendChild(veil);
        var started = Date.now(), down = false;
        var time = veil.querySelector('[data-role=time]'), help = veil.querySelector('.rv-help');
        veil.querySelector('[data-role=login]').addEventListener('click', function () { location.href = '/login'; });
        setInterval(function () {
            var s = Math.round((Date.now() - started) / 1000);
            time.textContent = '已等待 ' + (s < 60 ? s + ' 秒' : Math.floor(s / 60) + ' 分 ' + (s % 60) + ' 秒');
            if (s >= 90) help.hidden = false;
        }, 1000);
        var tick = function () {
            askJson('/install/status', null, 5000).then(function (s) {
                var back = boot && s.boot ? s.boot !== boot : down || Date.now() - started > 20000;
                if (back && s.installed && !s.restarting) { location.href = '/login'; return; }
                setTimeout(tick, 1500);
            }, function () { down = true; setTimeout(tick, 1500); });
        };
        setTimeout(tick, 1500);
    }

    // 上传备份文件恢复：切成几 MB 一片按顺序传（反向代理、宝塔网站防火墙限制单个请求的大小）。
    // 嫌一片太大（413、「Nginx缓冲区溢出」）就减半；断网、5xx、一分钟没动静就重传这一片；传完服务器在后台检查，通过了自己重启
    var rform = document.getElementById('restore-form');
    if (rform) {
        var R = function (r) { return rform.querySelector('[data-role=' + r + ']'); };
        var rfile = document.getElementById('restore-file'), rpick = rform.querySelector('.restore-pick');
        var go = R('go'), stop = R('stop'), prog = R('progress'), bar = R('bar'), phase = R('phase'), pct = R('pct'), text = R('text');
        var name0 = R('name').textContent, meta0 = R('meta').textContent;
        var MAX = 8 * 1024 * 1024 * 1024, IDLE = 60000, WL = '/admin/settings/backup/restore';
        var cur = null;
        var fmtSize = function (b) {
            if (b >= 1073741824) return (b / 1073741824).toFixed(2) + ' GB';
            if (b >= 1048576) return (b / 1048576).toFixed(1) + ' MB';
            return Math.max(1, Math.round(b / 1024)) + ' KB';
        };
        var clock = function (s) { s = Math.max(1, Math.round(s)); return s < 60 ? s + ' 秒' : Math.floor(s / 60) + ' 分 ' + (s % 60) + ' 秒'; };
        var pick = function () {
            var f = rfile.files[0];
            if (f && !/\.otaproj$/i.test(f.name)) {
                window.otaToast(/\.otabak$/i.test(f.name) ? '这是 App 的备份，到 App 设置 → 备份与恢复 里恢复' : '请选择 .otaproj 结尾的项目备份文件', 'err');
                rfile.value = '';
                f = null;
            } else if (f && f.size > MAX) {
                window.otaToast('备份文件最大 8 GB', 'err');
                rfile.value = '';
                f = null;
            }
            rpick.classList.remove('hint-pick');
            rpick.classList.toggle('picked', !!f);
            R('name').textContent = f ? f.name : name0;
            R('meta').textContent = f ? fmtSize(f.size) + ' · 点这里可以换一个' : meta0;
        };
        rfile.addEventListener('change', pick);
        ['dragenter', 'dragover'].forEach(function (ev) { rpick.addEventListener(ev, function (e) { e.preventDefault(); if (!cur) rpick.classList.add('over'); }); });
        ['dragleave', 'drop'].forEach(function (ev) { rpick.addEventListener(ev, function (e) { e.preventDefault(); rpick.classList.remove('over'); }); });
        rpick.addEventListener('drop', function (e) {
            if (cur || !e.dataTransfer || !e.dataTransfer.files.length) return;
            rfile.files = e.dataTransfer.files;
            pick();
        });
        var lock = function (on) {
            go.hidden = on;
            stop.hidden = !on;
            prog.hidden = !on;
            rfile.disabled = on;
            rpick.classList.toggle('locked', on);
            Array.prototype.forEach.call(rform.querySelectorAll('input[type=password]'), function (i) { i.readOnly = on; });
        };
        var fail = function (msg) {
            cur = null;
            lock(false);
            window.otaToast(msg || '上传失败，请重试', 'err');
        };
        // sent 是服务器收下的字节，moved 是实际传出去的（被代理退回重传的也算），速度按 moved 算
        var paint = function (sent, total, moved, t0) {
            var p = Math.min(100, Math.floor(sent * 100 / total)), secs = (Date.now() - t0) / 1000, speed = secs >= 1 ? moved / secs : 0;
            bar.style.width = p + '%';
            pct.textContent = p + '%';
            text.textContent = fmtSize(sent) + ' / ' + fmtSize(total) + (speed > 0 ? ' · ' + fmtSize(speed) + '/秒' + (sent < total ? ' · 还要约 ' + clock((total - sent) / speed) : '') : '');
        };
        // 发一个请求：IDLE 内既没有上传进度也没等到响应，就当卡住了，中断它
        var send = function (job, url, body, onSent) {
            return new Promise(function (resolve) {
                var xhr = new XMLHttpRequest(), timer = null, stalled = false;
                var arm = function () { clearTimeout(timer); timer = setTimeout(function () { stalled = true; xhr.abort(); }, IDLE); };
                var end = function (kind) {
                    clearTimeout(timer);
                    var res = null;
                    try { res = JSON.parse(xhr.responseText); } catch (err) {}
                    resolve({ xhr: xhr, res: res, kind: stalled ? 'stall' : kind });
                };
                job.xhr = xhr;
                xhr.open('POST', url);
                xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
                if (body instanceof Blob) {
                    xhr.setRequestHeader('Content-Type', 'application/octet-stream');
                    xhr.upload.onprogress = function (ev) { arm(); if (onSent) onSent(ev.loaded); };
                    xhr.upload.onload = arm;
                }
                xhr.onload = function () { end('load'); };
                xhr.onerror = function () { end('error'); };
                xhr.onabort = function () { end('abort'); };
                arm();
                xhr.send(body);
            });
        };
        var why = function (r) {
            var t = r.xhr.responseText || '';
            if (r.kind === 'stall') return '上传卡住了，一分钟没有任何进展。检查一下网络，或者看看是不是被网站防火墙拦了（把 ' + WL + ' 加进 URL 白名单）';
            if (r.kind === 'error') return '网络断了，上传失败，请检查网络后重试';
            if (r.xhr.responseURL && r.xhr.responseURL.indexOf('/login') >= 0) return '登录已过期，请刷新页面后重新登录';
            if (r.res && r.res.msg) return r.res.msg;
            if (r.xhr.status === 413 || t.indexOf('Nginx缓冲区溢出') >= 0) return '上传被反向代理拦下了：单次请求太大（HTTP ' + r.xhr.status + '）。把 Nginx 的 client_max_body_size 调到 10m 以上后重试';
            if (/^\s*</.test(t)) return '上传被网站防火墙拦截了（HTTP ' + r.xhr.status + '）。把 ' + WL + ' 加进防火墙的 URL 白名单后重试';
            return '上传失败（HTTP ' + r.xhr.status + '）';
        };
        rform.addEventListener('submit', function (e) {
            e.preventDefault();
            if (cur) return;
            var f = rfile.files[0];
            if (!f) {
                rpick.classList.add('hint-pick');
                window.otaToast('先选择备份文件（.otaproj）', 'err');
                return;
            }
            var pwd = document.getElementById('restore-pwd').value, admin = document.getElementById('restore-admin').value;
            var job = cur = { xhr: null, stopped: false };
            var chunk = 4 * 1024 * 1024, sent = 0, moved = 0, id = null, tries = 0, t0 = Date.now();
            lock(true);
            phase.textContent = '正在上传';
            pct.textContent = '0%';
            bar.style.width = '0';
            text.textContent = '准备上传…';
            var finish = function () {
                stop.hidden = true;
                phase.textContent = '检查备份和密码';
                pct.textContent = '';
                bar.style.width = '100%';
                var c0 = Date.now();
                var tick = function () { text.textContent = '已上传 ' + fmtSize(f.size) + '，服务器正在解密和检查（' + clock((Date.now() - c0) / 1000) + '），备份大的话要一会儿'; };
                tick();
                askJson('/admin/settings/backup/restore/' + id + '/finish', new URLSearchParams({ adminPassword: admin, backupPassword: pwd }), 60000).then(function (r) {
                    if (job.stopped) return;
                    if (!r.ok) return fail(r.msg);
                    watchCheck(r.check, fail, tick);
                }, function (err) { if (!job.stopped) fail(err.message); });
            };
            var next = function () {
                if (job.stopped) return;
                if (sent >= f.size) return finish();
                var from = sent, out = 0;
                send(job, '/admin/settings/backup/restore/' + id + '?offset=' + from, f.slice(from, Math.min(f.size, from + chunk)), function (n) { out = n; paint(from + n, f.size, moved + n, t0); }).then(function (r) {
                    if (job.stopped) return;
                    moved += out;
                    if (r.res && r.res.ok) {
                        if (r.res.received > from) { sent = r.res.received; tries = 0; paint(sent, f.size, moved, t0); next(); return; }
                        if (++tries <= 2) { setTimeout(next, 1000); return; }
                        return fail('服务器没收到上传的数据，多半是被反向代理或网站防火墙拦下了。把 ' + WL + ' 加进防火墙的 URL 白名单后重试');
                    }
                    var t = r.xhr.responseText || '';
                    if ((r.xhr.status === 413 || t.indexOf('Nginx缓冲区溢出') >= 0) && chunk > 256 * 1024) { chunk = Math.floor(chunk / 2); next(); return; }
                    if (r.kind === 'stall' && chunk > 512 * 1024) chunk = Math.floor(chunk / 2);
                    var flaky = r.kind === 'error' || r.kind === 'stall' || (r.xhr.status >= 500 && !r.res);
                    if (flaky && ++tries <= 4) {
                        text.textContent = '网络不稳，' + (tries * 2) + ' 秒后重传这一片（第 ' + tries + ' 次）…';
                        setTimeout(next, 2000 * tries);
                        return;
                    }
                    fail(why(r));
                });
            };
            send(job, '/admin/settings/backup/restore/start', new URLSearchParams({ fileName: f.name, size: String(f.size) })).then(function (r) {
                if (job.stopped) return;
                if (!(r.res && r.res.ok)) return fail(why(r));
                id = r.res.id;
                next();
            });
        });
        var halt = function () {
            if (!cur) return false;
            cur.stopped = true;
            if (cur.xhr) cur.xhr.abort();
            cur = null;
            lock(false);
            return true;
        };
        stop.addEventListener('click', function () { if (halt()) window.otaToast('已取消上传，数据没有动'); });
        window.addEventListener('ota:leave', halt);
        window.addEventListener('beforeunload', function (e) { if (cur && !stop.hidden) { e.preventDefault(); e.returnValue = ''; } });
    }
}
