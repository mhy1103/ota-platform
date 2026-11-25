export default function () {
    // 列表：按角色、状态筛选，按名字、账号、App 搜索
    var list = document.getElementById('admin-list'), seg = document.querySelector('[data-admin-filter]');
    var q = document.querySelector('[data-admin-search]'), none = document.querySelector('[data-admin-none]'), mode = 'all';
    function filterList() {
        if (!list) return;
        var text = q ? q.value.trim().toLowerCase() : '', any = false;
        Array.prototype.forEach.call(list.querySelectorAll('.adm-card'), function (c) {
            var ok = (mode === 'all' || (mode === 'off' ? c.getAttribute('data-on') === '0' : c.getAttribute('data-kind') === mode))
                && (!text || c.textContent.toLowerCase().indexOf(text) >= 0);
            c.hidden = !ok;
            if (ok) any = true;
        });
        if (none) none.hidden = any;
    }
    if (seg) seg.addEventListener('click', function (e) {
        var b = e.target.closest('button[data-f]');
        if (!b) return;
        mode = b.getAttribute('data-f');
        Array.prototype.forEach.call(seg.querySelectorAll('button'), function (x) { x.classList.toggle('on', x === b); });
        filterList();
    });
    if (q) q.addEventListener('input', filterList);

    var dlg = document.getElementById('admin-dialog');
    var form = document.getElementById('admin-form');
    var memberBox = form.querySelector('[data-role=member]');
    var ownerInput = form.querySelector('[data-role=owner]');
    var ownerNote = form.querySelector('[data-role=owner-note]');
    var demote = form.querySelector('[data-role=demote]'), demoteBox = form.querySelector('[data-role=demote-box]');
    var rows = form.querySelectorAll('tr[data-app]');
    var enabledBox = form.querySelector('[data-role=enabled]');
    var enabledOff = form.querySelector('[data-role=enabled-off]');
    var PRESETS = { view: [], ops: ['mode'], dev: ['release'], all: ['release', 'mode', 'settings', 'signing'] };
    var search = form.querySelector('[data-role=perm-search]');
    var filterSeg = form.querySelector('[data-role=perm-filter]');
    var countEl = form.querySelector('[data-role=perm-count]');
    var emptyEl = form.querySelector('[data-role=perm-empty]');
    var onlyChecked = false;

    function shown() {
        return Array.prototype.filter.call(rows, function (tr) { return !tr.hidden; });
    }
    function applyFilter() {
        var q = search ? search.value.trim().toLowerCase() : '';
        var any = false;
        Array.prototype.forEach.call(rows, function (tr) {
            var match = (!q || tr.cells[0].textContent.toLowerCase().indexOf(q) >= 0)
                && (!onlyChecked || tr.querySelector('[data-access]').checked);
            tr.hidden = !match;
            if (match) any = true;
        });
        if (emptyEl) emptyEl.hidden = any || !rows.length;
    }
    function updateCount() {
        if (!countEl) return;
        var n = Array.prototype.filter.call(rows, function (tr) { return tr.querySelector('[data-access]').checked; }).length;
        countEl.textContent = '已分配 ' + n + ' / ' + rows.length + ' 个 App';
    }
    if (search) {
        search.addEventListener('input', applyFilter);
        search.addEventListener('keydown', function (e) { if (e.key === 'Enter') e.preventDefault(); });
    }
    if (filterSeg) filterSeg.addEventListener('click', function (e) {
        var a = e.target.closest('[data-filter]');
        if (!a) return;
        e.preventDefault();
        onlyChecked = a.getAttribute('data-filter') === 'on';
        Array.prototype.forEach.call(filterSeg.children, function (x) { x.classList.toggle('on', x === a); });
        applyFilter();
    });
    form.querySelector('.perm-table thead').addEventListener('click', function (e) {
        var th = e.target.closest('[data-col]');
        if (!th) return;
        var col = th.getAttribute('data-col');
        var list = shown();
        var boxes = list.map(function (tr) { return col === 'access' ? tr.querySelector('[data-access]') : tr.querySelector('[data-perm][value="' + col + '"]'); });
        var on = !boxes.every(function (b) { return b.checked; });
        list.forEach(function (tr, i) {
            boxes[i].checked = on;
            if (col === 'access' && !on) Array.prototype.forEach.call(tr.querySelectorAll('[data-perm]'), function (b) { b.checked = false; });
            if (col !== 'access' && on) tr.querySelector('[data-access]').checked = true;
            syncRow(tr);
        });
        updateCount();
    });

    function syncRow(tr) {
        var on = tr.querySelector('[data-access]').checked;
        tr.classList.toggle('off', !on);
    }
    // 超级管理员只有一个：新建的都是成员；编辑超级管理员时不显示 App 权限，以前多出来的超级管理员可以勾选改成成员
    function syncRole() {
        if (ownerNote.hidden) ownerInput.value = 'false';
        else ownerInput.value = demoteBox.checked ? 'false' : 'true';
        memberBox.hidden = ownerInput.value === 'true';
    }
    function clear(list) {
        Array.prototype.forEach.call(list || rows, function (tr) {
            Array.prototype.forEach.call(tr.querySelectorAll('input'), function (i) { i.checked = false; });
            syncRow(tr);
        });
        updateCount();
    }
    function apply(grants) {
        clear();
        Object.keys(grants).forEach(function (appId) {
            var tr = form.querySelector('tr[data-app="' + appId + '"]');
            if (!tr) return;
            tr.querySelector('[data-access]').checked = true;
            grants[appId].forEach(function (p) {
                var box = tr.querySelector('[data-perm][value="' + p + '"]');
                if (box) box.checked = true;
            });
            syncRow(tr);
        });
        updateCount();
    }
    form.addEventListener('change', function (e) {
        var t = e.target;
        if (t === demoteBox) syncRole();
        var tr = t.closest('tr[data-app]');
        if (!tr) return;
        if (t.hasAttribute('data-perm') && t.checked) tr.querySelector('[data-access]').checked = true;
        if (t.hasAttribute('data-access') && !t.checked) Array.prototype.forEach.call(tr.querySelectorAll('[data-perm]'), function (i) { i.checked = false; });
        syncRow(tr);
        updateCount();
    });
    form.addEventListener('click', function (e) {
        var b = e.target.closest('[data-preset]');
        if (!b) return;
        var preset = b.getAttribute('data-preset');
        if (preset === 'none') return clear(shown());
        shown().forEach(function (tr) {
            tr.querySelector('[data-access]').checked = true;
            Array.prototype.forEach.call(tr.querySelectorAll('[data-perm]'), function (i) { i.checked = PRESETS[preset].indexOf(i.value) >= 0; });
            syncRow(tr);
        });
        updateCount();
    });
    enabledBox.querySelector('input').addEventListener('change', function () { enabledOff.disabled = this.checked; });

    function open(mode, d) {
        form.reset();
        clear();
        onlyChecked = false;
        if (filterSeg) Array.prototype.forEach.call(filterSeg.children, function (x) { x.classList.toggle('on', x.getAttribute('data-filter') === 'all'); });
        applyFilter();
        var editing = mode === 'edit';
        form.action = editing ? '/admin/admins/' + d.id + '/update' : form.getAttribute('data-create');
        dlg.querySelector('[data-role=title]').textContent = editing ? '编辑 ' + d.name : '新建成员';
        var owner = editing && d.owner === 'true', me = d.me === 'true';
        ownerNote.hidden = !owner;
        demote.hidden = !owner || me;
        demoteBox.checked = false;
        form.querySelector('[data-role=owner-text]').textContent = me
            ? '你是超级管理员：所有 App、全部权限，还能管理账号和系统配置'
            : 'TA 现在也是超级管理员。超级管理员只能有一个，建议改成成员';
        form.querySelector('[data-role=submit]').textContent = editing ? '保存' : '新建';
        form.elements.password.required = !editing;
        form.elements.password.placeholder = editing ? '不改密码就留空' : '至少 8 位';
        form.querySelector('[data-role=pw-hint]').textContent = editing ? '填了就重置密码，TA 已登录的后台会退出' : '至少 8 位，建好后把账号和密码告诉 TA';
        enabledBox.hidden = !editing;
        if (editing) {
            form.elements.name.value = d.name || '';
            form.elements.username.value = d.username || '';
            form.elements.remark.value = d.remark || '';
            form.elements.manageAgents.checked = d.agents === 'true';
            form.elements.manageCodes.checked = d.codes === 'true';
            enabledBox.querySelector('input').checked = d.enabled === 'true';
            enabledOff.disabled = d.enabled === 'true';
            apply(JSON.parse(d.grants || '{}'));
        } else {
            enabledOff.disabled = true;
        }
        syncRole();
        dlg.showModal();
    }
    document.addEventListener('click', function (e) {
        if (e.target.closest('[data-admin-new]')) return open('new', {});
        var b = e.target.closest('[data-admin-edit]');
        if (b) open('edit', b.dataset);
    });
}
