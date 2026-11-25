import { appRestore } from './app-restore.js';

export default function () {
    // ---------- 卡片 / 列表两种显示：记在这台浏览器（ota.apps.view），boot.js 在第一次绘制前就设好 html 的 data-apps-view ----------
    var root = document.documentElement;
    var pick = document.querySelector('[data-app-view]');
    var rowBox = document.getElementById('app-rows');
    var rows = rowBox ? Array.prototype.slice.call(rowBox.querySelectorAll('[data-app-row]')) : [];
    var none = document.getElementById('app-cards-none');
    var search = document.querySelector('[data-app-search]');
    var saved = '';
    try { saved = localStorage.getItem('ota.apps.view') || ''; } catch (e) { /* 读不了就用卡片 */ }
    var setView = function (v) {
        if (v === 'list') root.setAttribute('data-apps-view', 'list'); else root.removeAttribute('data-apps-view');
    };
    var view = function () { return root.getAttribute('data-apps-view') === 'list' ? 'list' : 'cards'; };
    var paint = function () {
        if (!pick) return;
        Array.prototype.forEach.call(pick.querySelectorAll('[data-v]'), function (b) {
            var on = b.getAttribute('data-v') === view();
            b.classList.toggle('on', on);
            b.setAttribute('aria-pressed', on ? 'true' : 'false');
        });
    };
    // 「没有匹配的 App」按当前显示的那种算（卡片那边由通用的 data-filter 筛）
    var syncNone = function () {
        if (!none) return;
        var q = search ? search.value.trim() : '';
        if (!q) { none.hidden = true; return; }
        none.hidden = !!document.querySelector(view() === 'list' ? '#app-rows > [data-app-row]:not([hidden])' : '#app-cards > .app-card:not([hidden])');
    };
    setView(saved === 'list' ? 'list' : 'cards');
    paint();
    if (pick) pick.addEventListener('click', function (e) {
        var b = e.target.closest('[data-v]');
        if (!b) return;
        var v = b.getAttribute('data-v') === 'list' ? 'list' : 'cards';
        setView(v);
        try { localStorage.setItem('ota.apps.view', v); } catch (err) { /* 存不了就只管这一次 */ }
        paint();
        syncNone();
    });
    if (search) search.addEventListener('input', function () {
        var q = search.value.trim().toLowerCase();
        rows.forEach(function (r) { r.hidden = !!q && r.textContent.toLowerCase().indexOf(q) < 0; });
        if (rowBox) rowBox.classList.toggle('filtering', !!q);
        // 通用筛选也会改「没有匹配」，等它跑完再按当前显示方式定
        setTimeout(syncNone, 0);
    });
    // 点列表行的空白处进入这个 App（按钮、链接、菜单除外）
    if (rowBox) rowBox.addEventListener('click', function (e) {
        if (e.target.closest('a, button, input, label, form, [data-pop], .menu')) return;
        var r = e.target.closest('[data-app-row]');
        var link = r && r.querySelector('.alr-title');
        if (link) link.click();
    });

    function sync(input) {
        var picker = input.closest('.picker');
        var f = input.files[0];
        picker.classList.toggle('picked', !!f);
        picker.querySelector('[data-role=name]').textContent = f ? f.name : '点击选择文件';
    }
    var form = document.getElementById('app-form');
    if (!form) return;
    var importBox = form.querySelector('[data-role=import]');
    var reuseBox = form.querySelector('[data-role=reuse]');
    function importing() {
        return form.querySelector('input[name=keyMode]:checked').value === 'import';
    }
    function mode() {
        var m = form.querySelector('input[name=keyMode]:checked').value;
        importBox.hidden = m !== 'import';
        reuseBox.hidden = m !== 'reuse';
        if (!importing()) Array.prototype.forEach.call(importBox.querySelectorAll('input[type=file]'), function (i) { i.value = ''; sync(i); });
    }
    form.addEventListener('change', function (e) {
        if (e.target.name === 'keyMode') mode();
        if (e.target.type === 'file') sync(e.target);
    });
    form.addEventListener('submit', function (e) {
        if (importing() && (!form.elements.privateKey.files.length || !form.elements.certificate.files.length)) {
            e.preventDefault();
            window.otaToast('导入现有密钥要同时选择私钥和证书', 'err');
        }
    });
    var rs = document.getElementById('rs-file');
    if (rs) appRestore(rs.form);
}
