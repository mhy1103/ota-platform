// 登录页和两步验证页：显示密码、大写锁定提示、动态码输入、提交转圈、深浅色切换
export default function () {
    var root = document.documentElement;
    var form = document.getElementById('login-form');
    var pw = form.querySelector('[data-pw]');
    var eye = form.querySelector('[data-eye]');
    var caps = form.querySelector('[data-caps]');
    var modeBtn = document.querySelector('[data-mode-toggle]');
    var modeIcon = document.querySelector('[data-mode-icon]');

    if (pw && eye) {
        eye.addEventListener('click', function () {
            var show = pw.type === 'password';
            pw.type = show ? 'text' : 'password';
            eye.querySelector('use').setAttribute('href', show ? '#i-eye-off' : '#i-eye');
            eye.setAttribute('aria-label', show ? '隐藏密码' : '显示密码');
            eye.title = eye.getAttribute('aria-label');
            pw.focus();
        });
        ['keydown', 'keyup'].forEach(function (ev) {
            pw.addEventListener(ev, function (e) { if (e.getModifierState) caps.hidden = !e.getModifierState('CapsLock'); });
        });
        pw.addEventListener('blur', function () { caps.hidden = true; });
    }

    // 两步验证：输满 6 位自动提交；可以改用恢复码
    var otp = form.querySelector('[data-otp]');
    if (otp) {
        var rec = form.querySelector('[name=recovery]');
        var toggle = document.querySelector('[data-otp-toggle]');
        var tip = document.querySelector('[data-otp-tip]');
        var label = document.querySelector('[data-otp-label]');
        var tipCode = tip.textContent;
        var paint = function (focus) {
            var r = rec.value === 'true';
            otp.setAttribute('inputmode', r ? 'text' : 'numeric');
            otp.setAttribute('maxlength', r ? '11' : '6');
            otp.setAttribute('autocomplete', r ? 'off' : 'one-time-code');
            otp.placeholder = r ? 'xxxxx-xxxxx' : '000000';
            otp.parentNode.classList.toggle('recovery', r);
            label.textContent = r ? '恢复码' : '动态码';
            tip.textContent = r ? '输入开启两步验证时保存的恢复码，每个恢复码只能用一次' : tipCode;
            toggle.textContent = r ? '改用动态码' : '手机不在身边？用恢复码';
            if (focus) { otp.value = ''; otp.focus(); }
        };
        paint(false);
        toggle.addEventListener('click', function () { rec.value = rec.value === 'true' ? 'false' : 'true'; paint(true); });
        otp.addEventListener('input', function () {
            if (rec.value === 'true') return;
            var v = otp.value.replace(/\D/g, '').slice(0, 6);
            if (v !== otp.value) otp.value = v;
            if (v.length === 6) form.requestSubmit ? form.requestSubmit() : form.submit();
        });
    }

    // 登录设备已满：勾够数量才能提交
    var need = parseInt(form.getAttribute('data-need'), 10);
    if (need > 0) {
        var pickBtn = form.querySelector('[data-role=submit]'), pickLabel = form.querySelector('[data-role=label]');
        var syncPick = function () {
            var n = form.querySelectorAll('input[name=keys]:checked').length;
            pickBtn.disabled = n < need;
            pickLabel.textContent = n < need ? '再选 ' + (need - n) + ' 台' : '退出 ' + n + ' 台并登录';
        };
        form.addEventListener('change', syncPick);
        syncPick();
    }

    // 勾了记住我才记账号
    var user = form.querySelector('[name=username]'), remember = form.querySelector('[name=remember-me]');
    if (user && remember) {
        var last = '';
        try { last = localStorage.getItem('ota.lastUser') || ''; } catch (e) {}
        if (last && !user.value) {
            user.value = last;
            if (pw && document.activeElement !== pw) pw.focus();
        }
    }

    form.addEventListener('submit', function () {
        var b = form.querySelector('.submit');
        setTimeout(function () { b.classList.add('loading'); }, 0);
        if (user && remember) {
            try {
                if (remember.checked && user.value.trim()) localStorage.setItem('ota.lastUser', user.value.trim());
                else localStorage.removeItem('ota.lastUser');
            } catch (e) {}
        }
    });
    window.addEventListener('pageshow', function () { form.querySelector('.submit').classList.remove('loading'); });

    var paintIcon = function () { modeIcon.setAttribute('href', root.getAttribute('data-mode') === 'dark' ? '#i-sun' : '#i-moon'); };
    paintIcon();
    modeBtn.addEventListener('click', function () {
        var dark = root.getAttribute('data-mode') !== 'dark', u = {};
        try { u = JSON.parse(localStorage.getItem('ota.ui') || '{}') || {}; } catch (e) {}
        u.mode = dark ? 'dark' : 'light';
        try { localStorage.setItem('ota.ui', JSON.stringify(u)); } catch (e) {}
        root.setAttribute('data-mode', u.mode);
        paintIcon();
    });

    var err = document.querySelector('.msg.err');
    if (err && form.username && pw) {
        var last = sessionStorage.getItem('ota.login.user');
        if (last) form.username.value = last;
        if (form.username.value) pw.focus();
    }
    form.addEventListener('submit', function () {
        if (form.username) try { sessionStorage.setItem('ota.login.user', form.username.value); } catch (e) {}
    });
}
