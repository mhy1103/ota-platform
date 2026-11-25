// 我的账号：开启两步验证（取二维码、输入动态码）、下载恢复码
export default function () {
    var dlg = document.getElementById('tfa-dialog');
    var dl = document.querySelector('[data-rc-download]');
    if (dl) {
        dl.addEventListener('click', function () {
            var brand = document.querySelector('.brand > span');
            var text = (brand ? brand.firstChild.textContent.trim() : '管理后台') + ' · 两步验证恢复码\n账号：' + dl.getAttribute('data-user') + '\n生成时间：' + new Date().toLocaleString()
                + '\n\n' + dl.getAttribute('data-codes').split(' ').join('\n') + '\n\n每个恢复码只能用一次。手机丢了或换了时，在登录页选「用恢复码」。\n';
            var a = document.createElement('a');
            a.href = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
            a.download = 'ota-recovery-codes.txt';
            document.body.appendChild(a);
            a.click();
            a.remove();
            setTimeout(function () { URL.revokeObjectURL(a.href); }, 1000);
        });
    }
    if (!dlg) return;
    var qr = dlg.querySelector('[data-tfa-qr]');
    var secret = dlg.querySelector('[data-tfa-secret]');
    var copySecret = dlg.querySelector('[data-copy-secret]');
    var err = dlg.querySelector('[data-tfa-error]');
    var code = dlg.querySelector('input[name=code]');

    function setup() {
        qr.innerHTML = '<span class="tfa-loading"></span>';
        secret.textContent = '…';
        err.hidden = true;
        fetch('/admin/account/2fa/setup', { method: 'POST', headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (!res.ok) { err.textContent = res.msg; err.hidden = false; return; }
                qr.innerHTML = res.qr;
                secret.textContent = res.secret;
                copySecret.setAttribute('data-copy', res.secret.replace(/\s/g, ''));
            })
            .catch(function () { err.textContent = '二维码没有生成出来，请关掉重试'; err.hidden = false; });
    }
    function open() {
        setup();
        code.value = '';
        window.otaOpen('tfa-dialog');
        setTimeout(function () { code.focus(); }, 320);
    }
    Array.prototype.forEach.call(document.querySelectorAll('[data-tfa-setup]'), function (b) { b.addEventListener('click', open); });
    code.addEventListener('input', function () {
        var v = code.value.replace(/\D/g, '').slice(0, 6);
        if (v !== code.value) code.value = v;
    });
    if (dlg.getAttribute('data-retry') === 'true') open();
}
