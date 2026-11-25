export default function () {
    var dlg = document.getElementById('code-edit-dialog');
    var form = document.getElementById('code-edit-form');
    document.addEventListener('click', function (e) {
        var b = e.target.closest('[data-code-edit]');
        if (!b) return;
        var d = b.dataset;
        form.elements.id.value = d.id;
        form.elements.agentId.value = d.agent || '';
        form.elements.maxUses.value = d.max || 0;
        form.elements.remark.value = d.remark || '';
        form.elements.channel.value = d.channel || '';
        form.elements.expires.value = d.expires || '';
        dlg.querySelector('[data-role=code]').textContent = d.code;
        dlg.showModal();
    });

    var bf = document.getElementById('batch-form');
    var setBatchMode = function (mode) {
        Array.prototype.forEach.call(bf.querySelectorAll('[data-batch-mode]'), function (a) { a.classList.toggle('on', a.getAttribute('data-batch-mode') === mode); });
        Array.prototype.forEach.call(bf.querySelectorAll('[data-batch-panel]'), function (p) {
            var on = p.getAttribute('data-batch-panel') === mode;
            p.hidden = !on;
            Array.prototype.forEach.call(p.querySelectorAll('input,textarea'), function (i) { i.disabled = !on; });
        });
        bf.action = bf.getAttribute(mode === 'import' ? 'data-import' : 'data-generate');
        bf.elements.text.required = mode === 'import';
        bf.querySelector('[data-role=submit]').textContent = mode === 'import' ? '导入' : '生成';
    };
    bf.addEventListener('click', function (e) {
        var a = e.target.closest('[data-batch-mode]');
        if (!a) return;
        e.preventDefault();
        setBatchMode(a.getAttribute('data-batch-mode'));
    });
    setBatchMode('generate');

    var bulk = document.getElementById('bulk-form');
    if (!bulk) return;
    var agentSel = bulk.querySelector('[data-role=bulk-agent]');
    // 勾选的口令一行一个复制出来，发给代理或者贴到表格里
    bulk.querySelector('[data-role=bulk-copy]').addEventListener('click', function () {
        var codes = Array.prototype.map.call(document.querySelectorAll('input[data-check][data-code]:checked'), function (c) { return c.getAttribute('data-code'); });
        if (!codes.length) return;
        window.otaCopy(codes.join('\n'), '已复制 ' + codes.length + ' 个口令，一行一个', this);
    });
    // 选了哪种批量操作就只显示、只提交那一个输入框
    var extras = { assign: agentSel, expire: bulk.querySelector('[data-role=bulk-expire]'), uses: bulk.querySelector('[data-role=bulk-uses]'), remark: bulk.querySelector('[data-role=bulk-remark]') };
    var hint = bulk.querySelector('[data-role=bulk-hint]'), hintBase = hint ? hint.textContent : '';
    var HINTS = { expire: '日期留空就是一直有效', uses: '0 表示不限次数', remark: '备注留空就是清空' };
    var syncBulk = function () {
        var act = bulk.elements.action.value;
        if (hint) hint.textContent = HINTS[act] ? HINTS[act] + ' · ' + hintBase : hintBase;
        Object.keys(extras).forEach(function (k) {
            var el = extras[k];
            if (!el) return;
            el.hidden = k !== act;
            Array.prototype.forEach.call(el.matches('input,select') ? [el] : el.querySelectorAll('input,select'), function (i) { i.disabled = k !== act; });
        });
    };
    bulk.elements.action.addEventListener('change', syncBulk);
    syncBulk();
    bulk.addEventListener('submit', function () {
        var n = bulk.querySelector('[data-check-count]').textContent;
        var act = bulk.elements.action.value;
        bulk.toggleAttribute('data-confirm-danger', act === 'delete');
        if (act === 'delete') {
            bulk.setAttribute('data-confirm', '删除勾选的 ' + n + ' 个口令？');
            bulk.setAttribute('data-confirm-body', '删了就不能再提交，代理也看不到数据，激活记录保留。只想暂停的话用停用。');
            bulk.setAttribute('data-confirm-ok', '删除');
        } else if (act === 'disable') {
            bulk.setAttribute('data-confirm', '停用勾选的 ' + n + ' 个口令？');
            bulk.setAttribute('data-confirm-body', '停用后提交这些口令会失败，已激活的设备不受影响。');
            bulk.setAttribute('data-confirm-ok', '停用');
        } else {
            bulk.removeAttribute('data-confirm');
        }
    });
}
