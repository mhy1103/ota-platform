// 错误页：有上一页就回上一页，没有就回首页
export default function () {
    var back = document.querySelector('[data-back]');
    if (!back) return;
    back.addEventListener('click', function (e) {
        if (history.length > 1 && document.referrer && document.referrer.indexOf(location.origin) === 0) {
            e.preventDefault();
            history.back();
        }
    });
}
