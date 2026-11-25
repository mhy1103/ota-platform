// 分享出去的数据大屏要输访问密码：显示密码、提交时按钮转圈
export default function () {
    var form = document.getElementById('share-form');
    if (!form) return;
    var pw = form.querySelector('[data-pw]'), eye = form.querySelector('[data-eye]');
    if (pw && eye) {
        eye.addEventListener('click', function () {
            var show = pw.type === 'password';
            pw.type = show ? 'text' : 'password';
            eye.setAttribute('aria-label', show ? '隐藏密码' : '显示密码');
            eye.title = eye.getAttribute('aria-label');
            pw.focus();
        });
    }
    form.addEventListener('submit', function () {
        var b = form.querySelector('.sh-go');
        if (b) b.classList.add('busy');
    });
    if (pw && window.matchMedia && matchMedia('(pointer:fine)').matches) pw.focus();
}
