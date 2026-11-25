import { appRestore } from './app-restore.js';

export default function () {
    var rs = document.getElementById('rs-file');
    if (rs) appRestore(rs.form);

    // 下载 App 备份：两次备份密码不一样就不提交；开始下载后把填的密码清掉
    var bk = document.getElementById('bk-pass');
    if (bk) bk.form.addEventListener('submit', function (e) {
        if (bk.value !== bk.form.elements.backupPassword2.value) {
            e.preventDefault();
            bk.form.elements.backupPassword2.focus();
            window.otaToast('两次输入的备份密码不一样', 'err');
            return;
        }
        var f = bk.form;
        setTimeout(function () { f.reset(); }, 1500);
    });

    var slug = document.getElementById('del-slug');
    var del = document.querySelector('#delete-form [data-role=delete]');
    if (slug) slug.addEventListener('input', function () { del.disabled = slug.value.trim() !== slug.getAttribute('data-expect'); });
}
