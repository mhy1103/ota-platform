export default function () {
    var form = document.getElementById('signing-form');
    if (!form) return;
    var submit = form.querySelector('[data-role=submit]');
    var inputs = form.querySelectorAll('input[type=file]');
    function sync() {
        var any = false;
        Array.prototype.forEach.call(inputs, function (input) {
            var picker = input.closest('.picker');
            var name = picker.querySelector('[data-role=name]');
            var f = input.files[0];
            if (f) {
                any = true;
                picker.classList.add('picked');
                name.textContent = f.name + ' · ' + Math.max(1, Math.round(f.size / 1024)) + ' KB';
            } else {
                picker.classList.remove('picked');
                name.textContent = '点击选择文件，不换可不选';
            }
        });
        submit.disabled = !any;
    }
    Array.prototype.forEach.call(inputs, function (input) { input.addEventListener('change', sync); });
    form.closest('dialog').addEventListener('close', function () {
        form.reset();
        sync();
        Array.prototype.forEach.call(form.querySelectorAll('.picker.hint-pick'), function (p) { p.classList.remove('hint-pick'); });
    });
    // 「换私钥」「换证书」：打开弹窗的同时直接选那个文件
    document.addEventListener('click', function (e) {
        var b = e.target.closest && e.target.closest('[data-pick]');
        var input = b && document.getElementById(b.getAttribute('data-pick'));
        if (!input) return;
        input.closest('.picker').classList.add('hint-pick');
        input.click();
    });
}
