// 页面的后台请求（查新版本、待处理、看板定时刷新）连不上 /app/live 时在这里发：
// 请求不是页面自己发的，安卓上的 UC、QQ、夸克、华为、小米浏览器和微信不会因为它在地址栏下面走加载进度
self.onmessage = function (e) {
    var m = e.data || {};
    var headers = m.k === 'json'
        ? { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' }
        : { 'X-OTA-Data': '1', 'Accept': 'text/html' };
    fetch(m.u, { credentials: 'same-origin', cache: 'no-store', headers: headers }).then(function (r) {
        return r.text().then(function (d) {
            var u = new URL(r.url || m.u, self.location.href);
            self.postMessage({ i: m.i, s: r.status, u: u.pathname + u.search, ct: r.headers.get('Content-Type') || '', b: r.headers.get('X-OTA-Build') || '', d: d });
        });
    }).catch(function () {
        self.postMessage({ i: m.i, s: 599 });
    });
};
