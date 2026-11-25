// 用 App 备份恢复（App 设置 → 备份与恢复、所有 App → 从备份恢复）：文件不大，一次传完。
// 用 XHR 提交，被反向代理、宝塔网站防火墙拦下时在页面上说清楚怎么办，不跳到拦截页；成功了再跳到恢复好的 App
export function appRestore(form) {
    var file = form.querySelector('input[type=file]'), picker = file.closest('.picker'), btn = form.querySelector('button[type=submit]');
    var name0 = picker.querySelector('[data-role=name]').textContent;
    var dlg = form.closest('dialog');
    var MAX = 50 * 1024 * 1024;
    var check = function (f) {
        if (!f) return '请选择备份文件（.otabak）';
        if (/\.otaproj$/i.test(f.name)) return '这是整个后台的项目备份，到 系统配置 → 备份 里恢复';
        if (!/\.(otabak|json)$/i.test(f.name)) return '请选择 .otabak 结尾的 App 备份文件';
        if (f.size > MAX) return '文件超过 50 MB，不像是 App 备份';
        return null;
    };
    var sync = function () {
        var f = file.files[0], bad = f && check(f);
        if (bad) { window.otaToast(bad, 'err'); file.value = ''; f = null; }
        picker.classList.remove('hint-pick');
        picker.classList.toggle('picked', !!f);
        picker.querySelector('[data-role=name]').textContent = f ? f.name + ' · ' + Math.max(1, Math.round(f.size / 1024)) + ' KB' : name0;
    };
    file.addEventListener('change', sync);
    ['dragenter', 'dragover'].forEach(function (ev) { picker.addEventListener(ev, function (e) { e.preventDefault(); picker.classList.add('over'); }); });
    ['dragleave', 'drop'].forEach(function (ev) { picker.addEventListener(ev, function (e) { e.preventDefault(); picker.classList.remove('over'); }); });
    picker.addEventListener('drop', function (e) {
        if (!e.dataTransfer || !e.dataTransfer.files.length || btn.disabled) return;
        file.files = e.dataTransfer.files;
        sync();
    });
    form.addEventListener('submit', function (e) {
        e.preventDefault();
        if (btn.disabled) return;
        var bad = check(file.files[0]);
        if (bad) {
            picker.classList.add('hint-pick');
            window.otaToast(bad, 'err');
            return;
        }
        var busy = function (on) {
            btn.disabled = on;
            btn.classList.toggle('loading', on);
            if (dlg) dlg.toggleAttribute('data-busy', on);
        };
        var fail = function (msg) {
            busy(false);
            window.otaToast(msg, 'err');
        };
        var xhr = new XMLHttpRequest();
        xhr.open('POST', form.getAttribute('action'));
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.timeout = 120000;
        xhr.onload = function () {
            var res = null, t = xhr.responseText || '';
            try { res = JSON.parse(t); } catch (err) {}
            if (res && res.ok) {
                sessionStorage.setItem('ota.toast', JSON.stringify({ msg: res.msg, type: 'ok' }));
                if (dlg) dlg.removeAttribute('data-busy');
                if (window.otaNavigate) window.otaNavigate(res.redirect); else location.href = res.redirect;
                return;
            }
            if (res && res.msg) return fail(res.msg);
            if (xhr.responseURL && xhr.responseURL.indexOf('/login') >= 0) return fail('登录已过期，请刷新页面后重新登录');
            if (xhr.status === 413 || t.indexOf('Nginx缓冲区溢出') >= 0) return fail('上传被反向代理拦下了：请求太大（HTTP ' + xhr.status + '）');
            if (/^\s*</.test(t)) return fail('上传被网站防火墙拦截了（HTTP ' + xhr.status + '）。把 /admin/apps/restore 加进防火墙的 URL 白名单后重试');
            fail('没有恢复：服务器返回 HTTP ' + xhr.status);
        };
        xhr.onerror = function () { fail('网络断了，没有恢复，请检查网络后重试'); };
        xhr.ontimeout = function () { fail('服务器太久没有响应。刷新页面看看是不是已经恢复了'); };
        busy(true);
        xhr.send(new FormData(form));
    });
}
