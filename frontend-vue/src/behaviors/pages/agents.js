import tierEditor from '../tiers.js';

export default function () {
    var dlg = document.getElementById('agent-dialog');
    // 代理详情页也挂在「代理管理」下面，那里没有这些弹窗
    if (!dlg) return;
    var form = document.getElementById('agent-form');
    var title = dlg.querySelector('[data-role=title]');
    var pwd = form.elements.password;
    var hint = form.querySelector('[data-role=pwd-hint]');

    var tiers = tierEditor(document.getElementById('tier-rows'), document.getElementById('tier-empty'),
        document.getElementById('tier-example'), document.getElementById('tier-add'), '代理');

    function check(name, value) {
        var el = form.querySelector('input[name="' + name + '"][value="' + value + '"]');
        if (el) el.checked = true;
    }
    var limitField = form.querySelector('[data-role=limit-field]');
    function syncLimit() {
        var m = form.querySelector('input[name=requestMode]:checked');
        limitField.hidden = !m || m.value === 'OFF';
    }
    var subLimitField = form.querySelector('[data-role=sub-limit-field]');
    function syncSubs() {
        var s = form.querySelector('input[name=subAgents]:checked');
        subLimitField.hidden = !s || s.value !== 'true';
    }
    form.addEventListener('change', function (e) {
        if (e.target.name === 'requestMode') syncLimit();
        if (e.target.name === 'subAgents') syncSubs();
    });
    function open(d) {
        var edit = !!d;
        form.reset();
        form.setAttribute('action', edit ? '/admin/agents/' + d.id + '/update' : '/admin/agents/create');
        title.textContent = edit ? '编辑代理' : '新建代理';
        form.elements.name.value = edit ? d.name || '' : '';
        form.elements.username.value = edit ? d.username || '' : '';
        form.elements.remark.value = edit ? d.remark || '' : '';
        check('canViewRecords', edit && d.view === 'true' ? 'true' : 'false');
        check('dataMode', edit && d.mode ? d.mode : 'REALTIME');
        check('requestMode', edit && d.request ? d.request : 'OFF');
        form.elements.dailyLimit.value = edit ? (d.limit && d.limit !== '0' ? d.limit : '') : '100';
        syncLimit();
        check('subAgents', edit && d.subs === 'true' ? 'true' : 'false');
        form.elements.subLimit.value = edit && d.subLimit && d.subLimit !== '0' ? d.subLimit : '';
        syncSubs();
        tiers.load(edit ? d.tiers : '[]');
        pwd.required = !edit;
        pwd.placeholder = edit ? '留空则不修改' : '至少 6 位';
        hint.textContent = edit ? '填了就重置密码，不填保持不变' : '把密码告诉代理，TA 用它登录';
        dlg.showModal();
        form.elements.name.focus();
    }

    // ---------- 勾选后批量操作 ----------
    function picked(group) {
        return Array.prototype.filter.call(document.querySelectorAll('[data-check]'), function (b) {
            return b.checked && (b.getAttribute('data-check') || '') === group;
        }).map(function (b) { return b.value; });
    }
    function hidden(f, name, value) {
        var i = document.createElement('input');
        i.type = 'hidden';
        i.name = name;
        i.value = value;
        i.setAttribute('data-picked', '');
        f.appendChild(i);
    }
    function setPicked(f, ids, extra) {
        Array.prototype.forEach.call(f.querySelectorAll('input[data-picked]'), function (i) { i.remove(); });
        ids.forEach(function (id) { hidden(f, 'ids', id); });
        Object.keys(extra || {}).forEach(function (k) { hidden(f, k, extra[k]); });
    }
    // 走统一的提交流程（确认弹窗、fetch 提交）；Safari 15 没有 requestSubmit
    function send(f) {
        if (f.requestSubmit) { f.requestSubmit(); return; }
        if (f.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))) f.submit();
    }

    var bulk = document.getElementById('agent-bulk');
    var reqDlg = document.getElementById('agent-request-dialog'), reqForm = document.getElementById('agent-request-form');
    var delDlg = document.getElementById('agent-delete-dialog'), delForm = document.getElementById('agent-delete-form');
    var delPwd = delForm.querySelector('[data-role=pwd]');
    function syncPurge() {
        var purge = delForm.querySelector('input[name=purge]:checked').value === 'true';
        delPwd.hidden = !purge;
        delForm.elements.password.required = purge;
        delForm.querySelector('[data-role=ok]').textContent = purge ? '连口令一起删除' : '删除';
    }
    delForm.addEventListener('change', syncPurge);
    function openDelete(ids, single) {
        delForm.reset();
        if (single) {
            delForm.setAttribute('action', '/admin/agents/' + single.id + '/delete');
            setPicked(delForm, []);
        } else {
            delForm.setAttribute('action', '/admin/agents/bulk');
            setPicked(delForm, ids, { action: 'delete' });
        }
        var codes = single ? parseInt(single.codes, 10) || 0 : null;
        delDlg.querySelector('[data-role=title]').textContent = single ? '删除代理 ' + single.name + '？' : '删除勾选的 ' + ids.length + ' 个代理？';
        delDlg.querySelector('[data-role=sub]').textContent = '账号会被删除，无法再登录。';
        delDlg.querySelector('[data-role=keep]').textContent = single
            ? (codes > 0 ? codes + ' 个口令改为未分配，照常能用，激活记录保留' : 'TA 名下没有口令')
            : '口令改为未分配，照常能用，激活记录保留';
        delDlg.querySelector('[data-role=purge]').textContent = single
            ? (codes > 0 ? codes + ' 个口令、激活记录和申请全部删除，不能恢复' : '口令申请记录一起删除')
            : 'TA 们的口令、激活记录和申请全部删除，不能恢复';
        syncPurge();
        delDlg.showModal();
    }

    var reqBulk = document.getElementById('req-bulk');
    var apDlg = document.getElementById('req-approve-dialog'), apForm = document.getElementById('req-approve-form');
    var rjDlg = document.getElementById('req-reject-dialog'), rjForm = document.getElementById('req-reject-form');
    // 自定义口令的申请：勾了几个、按钮写清楚会怎样
    function syncPick() {
        var boxes = apDlg.querySelectorAll('input[name=codes]:not(:disabled)');
        var n = Array.prototype.filter.call(boxes, function (b) { return b.checked; }).length;
        apDlg.querySelector('[data-role=pick-count]').textContent = boxes.length ? '已选 ' + n + ' / ' + boxes.length : '';
        apForm.querySelector('button[type=submit]').textContent = !boxes.length ? '同意并生成' : n ? '同意 ' + n + ' 个' : '一个都不同意（拒绝）';
    }
    apDlg.addEventListener('change', function (e) { if (e.target.name === 'codes') syncPick(); });

    document.addEventListener('click', function (e) {
        if (e.target.closest('[data-agent-new]')) { e.preventDefault(); open(null); return; }
        var ed = e.target.closest('[data-agent-edit]');
        if (ed) { e.preventDefault(); open(ed.dataset); return; }
        var del = e.target.closest('[data-agent-delete]');
        if (del) { e.preventDefault(); openDelete([], del.dataset); return; }

        var b = e.target.closest('[data-agent-bulk]');
        if (b) {
            var ids = picked('');
            if (!ids.length) return;
            var act = b.getAttribute('data-agent-bulk');
            if (act === 'request') {
                reqForm.reset();
                setPicked(reqForm, ids);
                reqDlg.querySelector('[data-role=sub]').textContent = '给勾选的 ' + ids.length + ' 个代理设置';
                reqDlg.showModal();
            } else if (act === 'delete') {
                openDelete(ids, null);
            } else {
                bulk.elements.action.value = act;
                if (act === 'disable') {
                    bulk.setAttribute('data-confirm', '停用勾选的 ' + ids.length + ' 个代理？');
                    bulk.setAttribute('data-confirm-body', '停用后不能登录，已登录的马上退出，口令不受影响。');
                    bulk.setAttribute('data-confirm-ok', '停用');
                } else {
                    bulk.removeAttribute('data-confirm');
                }
                send(bulk);
            }
            return;
        }

        var ap = e.target.closest('[data-req-approve]');
        if (ap) {
            var d = ap.dataset;
            apForm.reset();
            apForm.setAttribute('action', '/admin/agents/requests/' + d.id + '/approve');
            apForm.elements.count.value = d.count;
            apForm.elements.maxUses.value = d.max;
            var items = [];
            try { items = JSON.parse(d.items || '[]') || []; } catch (err) { items = []; }
            var custom = items.length > 0;
            var codesField = apDlg.querySelector('[data-role=codes-field]');
            var list = apDlg.querySelector('[data-role=code-list]');
            codesField.hidden = !custom;
            apDlg.querySelector('[data-role=pick]').disabled = !custom;
            apDlg.querySelector('[data-role=count-field]').hidden = custom;
            apForm.elements.count.disabled = custom;
            list.innerHTML = '';
            items.forEach(function (it) {
                var row = document.createElement('label');
                row.className = 'pick-item' + (it.status === 'PENDING' ? '' : ' off');
                var box = document.createElement('input');
                box.type = 'checkbox';
                box.name = 'codes';
                box.value = it.code;
                box.checked = it.status === 'PENDING';
                box.disabled = it.status !== 'PENDING';
                var code = document.createElement('span');
                code.className = 'mono';
                code.textContent = it.code;
                row.appendChild(box);
                row.appendChild(code);
                if (it.reason) {
                    var why = document.createElement('small');
                    why.textContent = it.reason;
                    row.title = it.code + '：' + it.reason;
                    row.appendChild(why);
                }
                list.appendChild(row);
            });
            syncPick();
            apDlg.querySelector('[data-role=note]').textContent = custom
                ? '勾的口令直接归属这个代理，备注「代理申请 #' + d.id + '」；生成前会再查一遍，已经有了的跳过。'
                : '口令随机生成 8 位数字，直接归属这个代理，备注「代理申请 #编号」。';
            apDlg.querySelector('[data-role=title]').textContent = '同意 ' + d.agent + ' 的申请';
            apDlg.querySelector('[data-role=sub]').textContent = '申请 #' + d.id + (custom ? '。可以只同意其中几个口令' : '。个数和次数可以改');
            apDlg.showModal();
            return;
        }
        var pickAll = e.target.closest('[data-role=pick-all], [data-role=pick-none]');
        if (pickAll && apDlg.contains(pickAll)) {
            var on = pickAll.getAttribute('data-role') === 'pick-all';
            Array.prototype.forEach.call(apDlg.querySelectorAll('input[name=codes]:not(:disabled)'), function (b) { b.checked = on; });
            syncPick();
            return;
        }
        var rj = e.target.closest('[data-req-reject]');
        if (rj) {
            rjForm.reset();
            rjForm.setAttribute('action', '/admin/agents/requests/' + rj.dataset.id + '/reject');
            setPicked(rjForm, []);
            rjDlg.querySelector('[data-role=title]').textContent = '拒绝 ' + rj.dataset.agent + ' 的申请';
            rjDlg.querySelector('[data-role=sub]').textContent = '申请 #' + rj.dataset.id;
            rjDlg.showModal();
            return;
        }
        var rb = e.target.closest('[data-req-bulk]');
        if (rb) {
            var rids = picked('req');
            if (!rids.length) return;
            if (rb.getAttribute('data-req-bulk') === 'approve') {
                reqBulk.elements.action.value = 'approve';
                reqBulk.setAttribute('data-confirm', '同意勾选的 ' + rids.length + ' 条申请？');
                reqBulk.setAttribute('data-confirm-body', '按每条申请生成口令，归属对应的代理。');
                reqBulk.setAttribute('data-confirm-ok', '同意');
                send(reqBulk);
            } else {
                rjForm.reset();
                rjForm.setAttribute('action', '/admin/agents/requests/bulk');
                setPicked(rjForm, rids, { action: 'reject' });
                rjDlg.querySelector('[data-role=title]').textContent = '拒绝勾选的 ' + rids.length + ' 条申请';
                rjDlg.querySelector('[data-role=sub]').textContent = '原因会显示给每个代理';
                rjDlg.showModal();
            }
        }
    });
    if (location.hash === '#new') {
        open(null);
        history.replaceState(null, '', location.pathname + location.search);
    }

    // ---------- 代理列表：搜索、筛选、排序、显示更多 ----------
    var grid = document.getElementById('agent-cards');
    if (grid) {
        var cards = Array.prototype.slice.call(grid.querySelectorAll('[data-agent-card]'));
        var none = document.getElementById('agent-cards-none');
        var more = document.querySelector('[data-agent-more]');
        var search = document.querySelector('[data-agent-search]'), sortSel = document.querySelector('[data-agent-sort]');
        var fseg = document.querySelector('[data-agent-filter]');
        var LIMIT = 24, expanded = false, filterBy = 'all', pref = {};
        try { pref = JSON.parse(localStorage.getItem('ota.agents') || '{}') || {}; } catch (e) { pref = {}; }
        var save = function () {
            try { localStorage.setItem('ota.agents', JSON.stringify(pref)); } catch (e) { /* 存不了就算了 */ }
            if (window.otaPrefs) window.otaPrefs({ agentSort: pref.sort || '' });
        };
        var num = function (c, k) { return parseFloat(c.getAttribute('data-' + k)) || 0; };
        var str = function (c, k) { return c.getAttribute('data-' + k) || ''; };
        var apply = function () {
            var q = (search && search.value || '').trim().toLowerCase();
            var key = sortSel ? sortSel.value : 'today';
            var list = cards.slice().sort(function (a, b) {
                if (key === 'name') return str(a, 'name').localeCompare(str(b, 'name'), 'zh-CN');
                if (key === 'login' || key === 'created') return str(b, key).localeCompare(str(a, key));
                return num(b, key) - num(a, key) || num(b, 'total') - num(a, 'total');
            });
            var shown = 0;
            list.forEach(function (c) {
                grid.appendChild(c);
                var on = c.getAttribute('data-on') === 'true';
                var hit = (!q || str(c, 'search').indexOf(q) >= 0) && (filterBy === 'all' || (filterBy === 'on' && on)
                    || (filterBy === 'off' && !on) || (filterBy === 'active' && num(c, 'today') > 0));
                c.hidden = !hit;
                if (hit) shown++;
                c.classList.toggle('more-hidden', hit && !expanded && shown > LIMIT);
            });
            var tile = grid.querySelector('.agent-new-tile');
            if (tile) grid.appendChild(tile);
            if (none) none.hidden = shown > 0;
            if (more) {
                more.hidden = shown <= LIMIT;
                more.innerHTML = (expanded ? '收起' : '显示全部 ' + shown + ' 个代理') + '<svg class="i i-sm"><use href="#i-chevron-down"/></svg>';
                more.classList.toggle('open', expanded);
            }
        };
        if (search) search.addEventListener('input', apply);
        if (sortSel) {
            if (pref.sort && sortSel.querySelector('option[value="' + pref.sort + '"]')) sortSel.value = pref.sort;
            sortSel.addEventListener('change', function () { pref.sort = sortSel.value; save(); apply(); });
        }
        if (fseg) fseg.addEventListener('click', function (e) {
            var b = e.target.closest('[data-f]');
            if (!b) return;
            filterBy = b.getAttribute('data-f');
            Array.prototype.forEach.call(fseg.querySelectorAll('[data-f]'), function (x) { x.classList.toggle('on', x === b); });
            apply();
        });
        if (more) more.addEventListener('click', function () {
            expanded = !expanded;
            apply();
        });
        // ---------- 选择模式：勾选框默认不显示，⋯ 菜单点「选择」才出来；「取消」或 Esc 退出，刷新后复原 ----------
        var board = grid.closest('.agent-board');
        var checkAll = board && board.querySelector('[data-check-all]');
        var selecting = function () { return !!board && board.classList.contains('selecting'); };
        var fire = function (el) { el.dispatchEvent(new Event('change', { bubbles: true })); };
        var exitSelect = function () {
            if (!selecting()) return;
            Array.prototype.forEach.call(grid.querySelectorAll('[data-check]'), function (b) { b.checked = false; });
            if (checkAll) { checkAll.checked = false; fire(checkAll); }
            board.classList.remove('selecting');
        };
        document.addEventListener('click', function (e) {
            var s = e.target.closest('[data-agent-select]');
            if (s && board && board.isConnected) {
                e.preventDefault();
                board.classList.add('selecting');
                var id = s.getAttribute('data-id');
                var box = Array.prototype.filter.call(grid.querySelectorAll('[data-check]'), function (b) { return b.value === id; })[0];
                if (box) {
                    if (!box.checked) { box.checked = true; fire(box); }
                    box.focus({ preventScroll: true });
                }
                return;
            }
            if (e.target.closest('[data-agent-select-done]')) { e.preventDefault(); exitSelect(); }
        });
        // 捕获阶段先看：菜单、弹窗开着时 Esc 只关它们；在输入框里按 Esc 也不算
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape' || !selecting() || !board.isConnected) return;
            if (document.querySelector('[data-pop-menu]:not([hidden]), dialog[open]')) return;
            if (e.target.closest && e.target.closest('input:not([type=checkbox]), select, textarea, [contenteditable]')) return;
            exitSelect();
        }, true);
        // 点卡片空白处进详情（勾选框、按钮、链接、菜单除外）；选择模式下改成勾选 / 取消勾选这一行
        grid.addEventListener('click', function (e) {
            if (e.target.closest('a, button, input, label, form, [data-pop], .menu')) return;
            var c = e.target.closest('[data-agent-card]');
            if (c && selecting()) {
                var box = c.querySelector('[data-check]');
                if (box) { box.checked = !box.checked; fire(box); }
                return;
            }
            var link = c && c.querySelector('.ac-name');
            if (link) link.click();
        });
        apply();
    }

    // ---------- 公告：发布、编辑 ----------
    var nDlg = document.getElementById('notice-dialog'), nForm = document.getElementById('notice-form');
    if (nDlg && nForm) {
        var openNotice = function (d) {
            nForm.reset();
            nForm.setAttribute('action', d ? '/admin/agents/notices/' + d.id + '/update' : '/admin/agents/notices');
            nDlg.querySelector('[data-role=title]').textContent = d ? '编辑公告' : '发布公告';
            nForm.querySelector('[data-role=ok]').textContent = d ? '保存' : '发布';
            if (d) {
                nForm.elements.title.value = d.title || '';
                nForm.elements.body.value = d.body || '';
                nForm.elements.pinned.checked = d.pinned === 'true';
                var lv = nForm.querySelector('input[name=level][value="' + (d.level === 'important' ? 'important' : 'normal') + '"]');
                if (lv) lv.checked = true;
                var au = nForm.querySelector('input[name=audience][value="' + (d.audience === 'top' ? 'top' : 'all') + '"]');
                if (au) au.checked = true;
            }
            ['title', 'body'].forEach(function (n) { nForm.elements[n].dispatchEvent(new Event('input', { bubbles: true })); });
            nDlg.showModal();
            nForm.elements.title.focus();
        };
        document.addEventListener('click', function (e) {
            if (e.target.closest('[data-notice-new]')) { e.preventDefault(); openNotice(null); return; }
            var ne = e.target.closest('[data-notice-edit]');
            if (ne) { e.preventDefault(); openNotice(ne.dataset); }
        });
    }

    // 从代理详情点「编辑」：/admin/agents?edit=代理ID
    var editId = new URLSearchParams(location.search).get('edit');
    if (editId) {
        var eb = document.querySelector('[data-agent-edit][data-id="' + editId + '"]');
        if (eb) {
            open(eb.dataset);
            var back = document.createElement('input');
            back.type = 'hidden';
            back.name = 'back';
            back.value = 'detail';
            back.setAttribute('data-picked', '');
            form.appendChild(back);
            dlg.addEventListener('close', function () { back.remove(); }, { once: true });
        }
    }
}
