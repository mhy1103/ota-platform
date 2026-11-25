export default function () {
    var dlg = document.getElementById('diagnose-dialog');
    var form = document.getElementById('diagnose-form');
    var $ = function (role) { return dlg.querySelector('[data-role="' + role + '"]'); };
    var ICON = { ok: '#i-tick', error: '#i-x' };

    function render(data) {
        $('loading').hidden = true;
        $('result').hidden = false;
        var level = data.level || 'error';
        $('hero').className = 'hero' + (level === 'ok' ? '' : ' ' + level);
        $('hero-icon').setAttribute('href', level === 'ok' ? '#i-tick' : '#i-alert');
        $('verdict').textContent = data.verdict || data.msg || '诊断失败';
        $('summary').textContent = data.releaseId ? '版本 ID ' + data.releaseId : '';
        var list = $('steps');
        list.innerHTML = '';
        (data.steps || []).forEach(function (c) {
            var li = document.createElement('li');
            li.className = c.state;
            var st = document.createElement('span');
            st.className = 'st st-' + c.state;
            if (ICON[c.state]) st.innerHTML = '<svg class="i"><use href="' + ICON[c.state] + '"/></svg>';
            else st.textContent = c.state === 'warn' ? '!' : 'i';
            var body = document.createElement('span');
            var b = document.createElement('b');
            b.textContent = c.label;
            var t = document.createElement('span');
            t.className = 'sub';
            t.textContent = c.text;
            body.appendChild(b);
            body.appendChild(t);
            li.appendChild(st);
            li.appendChild(body);
            list.appendChild(li);
        });
    }
    function run() {
        if (!form.reportValidity()) return;
        $('result').hidden = true;
        $('loading').hidden = false;
        fetch('/admin/devices/diagnose?' + new URLSearchParams(new FormData(form)), { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (r) {
                if (r.redirected && r.url.indexOf('/login') >= 0) throw new Error('登录已过期，请刷新页面后重新登录');
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then(render)
            .catch(function (e) { render({ level: 'error', verdict: '诊断请求失败', steps: [{ label: '请求', state: 'error', text: e.message }] }); });
    }
    form.addEventListener('submit', function (e) { e.preventDefault(); run(); });
    document.addEventListener('click', function (e) {
        var b = e.target.closest('[data-diagnose]');
        if (!b) return;
        var dev = b.getAttribute('data-device') || '';
        form.elements.device.value = dev;
        if (b.getAttribute('data-platform')) form.elements.platform.value = b.getAttribute('data-platform');
        var rt = b.getAttribute('data-runtime');
        if (rt) form.elements.runtime.value = rt;
        else if (!form.elements.runtime.value) {
            var first = document.querySelector('#dg-runtimes option');
            if (first) form.elements.runtime.value = first.value;
        }
        var ch = b.getAttribute('data-channel');
        if (ch && Array.prototype.some.call(form.elements.channel.options, function (o) { return o.value === ch; })) form.elements.channel.value = ch;
        $('result').hidden = true;
        $('loading').hidden = true;
        dlg.showModal();
        if (dev) run();
    });

    var tdlg = document.getElementById('tester-dialog');
    if (!tdlg) return;
    document.addEventListener('click', function (e) {
        var b = e.target.closest('[data-tester]');
        if (!b) return;
        var f = tdlg.querySelector('form');
        f.reset();
        f.elements.clientId.value = b.getAttribute('data-device');
        tdlg.querySelector('[data-role=device]').textContent = b.getAttribute('data-device');
        var cur = b.getAttribute('data-channel');
        var pick = Array.prototype.find.call(f.elements.channel.options, function (o) { return o.value !== cur; });
        if (pick) f.elements.channel.value = pick.value;
        tdlg.showModal();
    });
}
