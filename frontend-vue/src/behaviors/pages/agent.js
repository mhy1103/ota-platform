// 代理后台：一键复制列表里可用的口令；申请口令的随机 / 自定义切换和个数统计；二级代理的新建、编辑、分口令
import tierEditor from '../tiers.js';
import rangePicker from '../range-picker.js';

export default function () {
    subAgents();
    var tbody = document.getElementById('my-codes');
    if (tbody) {
        // 复制的是搜索后看得到的（收在「显示全部」里的也算）；内容交给页面通用的 [data-copy] 复制
        var btn = document.querySelector('[data-copy-usable]');
        if (btn) {
            btn.addEventListener('click', function (e) {
                var codes = Array.prototype.filter.call(tbody.rows, function (tr) {
                    return !tr.hidden && tr.getAttribute('data-usable') === 'true';
                }).map(function (tr) { return tr.getAttribute('data-code'); });
                if (!codes.length) {
                    var given = Array.prototype.some.call(tbody.rows, function (tr) { return !tr.hidden && tr.hasAttribute('data-given'); });
                    btn.removeAttribute('data-copy');
                    e.stopPropagation();
                    window.otaToast(given ? '列表里没有可用的口令，分给二级代理的不算' : '列表里没有可用的口令', 'err');
                    return;
                }
                btn.setAttribute('data-copy', codes.join('\n'));
                btn.setAttribute('data-copy-label', '已复制 ' + codes.length + ' 个可用口令，一行一个');
            });
        }
    }

    var rf = document.getElementById('request-form');
    if (rf) {
        var ta = rf.elements.codes, out = rf.querySelector('[data-role=code-count]');
        var preview = rf.querySelector('[data-role=code-preview]'), summary = rf.querySelector('[data-role=req-summary]');
        var left = parseInt(rf.getAttribute('data-remaining'), 10);
        var auto = rf.getAttribute('data-auto') === 'true';
        var VALID = /^[A-Za-z0-9_-]{2,32}$/;
        var esc = function (t) { return String(t).replace(/[&<>"]/g, function (ch) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch]; }); };
        var mode = function () { return (rf.querySelector('input[name=mode]:checked') || {}).value || 'random'; };
        var parsed = function () {
            var list = (ta && !ta.disabled ? ta.value : '').split(/[\s,，;；、]+/).filter(Boolean);
            var seen = {}, uniq = [], bad = 0, dup = 0;
            list.forEach(function (x) {
                if (seen[x]) { dup++; return; }
                seen[x] = true;
                uniq.push(x);
                if (!VALID.test(x)) bad++;
            });
            return { all: list, uniq: uniq, bad: bad, dup: dup };
        };
        var paint = function () {
            var p = parsed(), custom = mode() === 'custom';
            if (out) {
                var n = p.uniq.length, over = left >= 0 && n > left;
                out.textContent = custom && n ? '共 ' + n + ' 个' + (p.dup ? '（重复的 ' + p.dup + ' 个只算一次）' : '') + (p.bad ? '，' + p.bad + ' 个格式不对' : '')
                    + (over ? '，超过今天还能申请的 ' + left + ' 个' : '') : '';
                out.classList.toggle('warn-text', over || p.bad > 0);
            }
            if (preview) {
                preview.hidden = !custom || !p.uniq.length;
                if (!preview.hidden) {
                    var shown = p.uniq.slice(0, 80).map(function (x) {
                        return '<span class="code-chip ' + (VALID.test(x) ? 'pending' : 'bad') + '" title="' + (VALID.test(x) ? '会提交这个口令' : '格式不对：只能用字母、数字、_ 和 -，2–32 位') + '">' + esc(x) + '</span>';
                    });
                    if (p.uniq.length > 80) shown.push('<span class="code-chip more">+' + (p.uniq.length - 80) + '</span>');
                    preview.innerHTML = shown.join('');
                }
            }
            if (summary) {
                var max = parseInt(rf.elements.maxUses.value, 10) || 0;
                var cnt = custom ? p.uniq.length : (parseInt(rf.elements.count && rf.elements.count.value, 10) || 0);
                summary.innerHTML = cnt > 0
                    ? '将' + (auto ? '生成' : '申请') + ' <b>' + cnt + '</b> 个' + (custom ? '自定义' : '随机') + '口令'
                        + '<span>·</span>' + (max > 0 ? '每个可激活 ' + max + ' 次' : '不限次数')
                    : (custom ? '在上面填要的口令' : '填要几个');
            }
        };
        // 只提交当前方式的字段：藏起来的输入框不提交、也不要求必填
        var sync = function () {
            var m = mode();
            Array.prototype.forEach.call(rf.querySelectorAll('[data-mode-field]'), function (f) {
                var on = f.getAttribute('data-mode-field') === m;
                f.hidden = !on;
                Array.prototype.forEach.call(f.querySelectorAll('input,textarea,button'), function (i) { i.disabled = !on; });
            });
            paint();
        };
        rf.addEventListener('change', function (e) { if (e.target.name === 'mode') sync(); else paint(); });
        rf.addEventListener('input', paint);
        sync();
    }

    // 申请记录按状态筛选
    var rfl = document.querySelector('[data-req-filter]');
    if (rfl) {
        var items = document.querySelectorAll('.req-item'), noneEl = document.querySelector('[data-req-none]');
        rfl.addEventListener('click', function (e) {
            var b = e.target.closest('[data-f]');
            if (!b) return;
            var f = b.getAttribute('data-f'), shown = 0;
            Array.prototype.forEach.call(rfl.querySelectorAll('[data-f]'), function (x) { x.classList.toggle('on', x === b); });
            Array.prototype.forEach.call(items, function (it) {
                var hit = f === 'all' || it.getAttribute('data-status') === f;
                it.hidden = !hit;
                if (hit) shown++;
            });
            if (noneEl) noneEl.hidden = shown > 0;
        });
    }

    // 时间段的「自定义」：弹出日历选起止日期
    var rc = document.querySelector('[data-range-custom]');
    if (rc) rangePicker(rc);

    // 一级代理给二级代理发公告：发布、编辑共用一个弹窗
    var nDlg = document.getElementById('own-notice-dialog'), nForm = document.getElementById('own-notice-form');
    if (nDlg && nForm) {
        var openNotice = function (d) {
            nForm.reset();
            nForm.setAttribute('action', d ? '/agent/notices/' + d.id + '/update' : '/agent/notices');
            nDlg.querySelector('[data-role=title]').textContent = d ? '编辑公告' : '给二级代理发公告';
            nForm.querySelector('[data-role=ok]').textContent = d ? '保存' : '发布';
            if (d) {
                nForm.elements.title.value = d.title || '';
                nForm.elements.body.value = d.body || '';
                nForm.elements.pinned.checked = d.pinned === 'true';
                var lv = nForm.querySelector('input[name=level][value="' + (d.level === 'important' ? 'important' : 'normal') + '"]');
                if (lv) lv.checked = true;
            }
            ['title', 'body'].forEach(function (n) { nForm.elements[n].dispatchEvent(new Event('input', { bubbles: true })); });
            nDlg.showModal();
            nForm.elements.title.focus();
        };
        document.addEventListener('click', function (e) {
            if (e.target.closest('[data-own-notice-new]')) { e.preventDefault(); openNotice(null); return; }
            var ne = e.target.closest('[data-own-notice-edit]');
            if (ne) { e.preventDefault(); openNotice(ne.dataset); }
        });
    }

    // 概览上的公告横幅：点「知道了」记为看过，横幅收起
    var banner = document.querySelector('[data-notice-banner]');
    var dismiss = banner && banner.querySelector('[data-notice-dismiss]');
    if (dismiss) {
        dismiss.addEventListener('click', function () {
            fetch('/agent/notices/' + banner.getAttribute('data-id') + '/read', { method: 'POST', headers: { 'X-Requested-With': 'XMLHttpRequest' } }).catch(function () {});
            banner.classList.add('leaving');
            setTimeout(function () { banner.remove(); }, 260);
            var count = document.querySelector('.agent-tabs .tab-count');
            if (count) {
                var n = (parseInt(count.textContent, 10) || 1) - 1;
                if (n > 0) count.textContent = n; else count.remove();
            }
        });
    }
}

// 「二级代理」页的两个弹窗：新建 / 编辑，分口令（分给 TA 和从 TA 那里收回放在一起）
function subAgents() {
    var each = function (list, fn) { Array.prototype.forEach.call(list, fn); };
    var dlg = document.getElementById('sub-dialog');
    var codesDlg = document.getElementById('sub-codes-dialog');
    if (!dlg && !codesDlg) return;

    var openSub = null;
    if (dlg) {
        var form = document.getElementById('sub-form');
        var tiers = tierEditor(document.getElementById('sub-tier-rows'), document.getElementById('sub-tier-empty'),
            document.getElementById('sub-tier-example'), document.getElementById('sub-tier-add'), '二级代理');
        var check = function (name, value) {
            var el = form.querySelector('input[name="' + name + '"][value="' + value + '"]');
            if (el) el.checked = true;
            return !!el;
        };
        openSub = function (d) {
            var edit = !!d;
            form.reset();
            form.setAttribute('action', edit ? '/agent/subs/' + d.id + '/update' : '/agent/subs/create');
            dlg.querySelector('[data-role=title]').textContent = edit ? '编辑二级代理' : '新建二级代理';
            form.elements.name.value = edit ? d.name || '' : '';
            form.elements.username.value = edit ? d.username || '' : '';
            form.elements.remark.value = edit ? d.remark || '' : '';
            check('canViewRecords', edit && d.view === 'true' ? 'true' : 'false');
            // 上级的数据更新方式改过以后，原来选的可能不能选了，就落到能选的第一个
            if (!(edit && d.mode && check('dataMode', d.mode))) {
                var first = form.querySelector('input[name=dataMode]');
                if (first) first.checked = true;
            }
            tiers.load(edit ? d.tiers : '[]');
            var pwd = form.elements.password;
            pwd.required = !edit;
            pwd.placeholder = edit ? '留空则不修改' : '至少 6 位';
            dlg.querySelector('[data-role=pwd-hint]').textContent = edit ? '填了就重置 TA 的密码，不填保持不变' : '把账号和密码告诉 TA，TA 用它登录';
            dlg.showModal();
            form.elements.name.focus();
        };
    }

    var openCodes = null;
    if (codesDlg) {
        var cf = document.getElementById('sub-codes-form');
        var freeList = cf.querySelector('[data-role=free-list]');
        var lists = cf.querySelectorAll('[data-sub-list]');
        var q = cf.querySelector('[data-role=q]');
        var mineEmpty = cf.querySelector('[data-role=mine-empty]');
        var summary = cf.querySelector('[data-role=summary]'), ok = cf.querySelector('[data-role=ok]');
        var current = null;
        var items = function (list) { return list ? Array.prototype.slice.call(list.querySelectorAll('.pick-item')) : []; };
        var checked = function (list) { return items(list).filter(function (it) { return it.querySelector('input').checked; }).length; };
        var paint = function () {
            var give = checked(freeList), take = checked(current);
            var freeCount = cf.querySelector('[data-role=pick-count]'), takeCount = cf.querySelector('[data-role=take-count]');
            if (freeCount) freeCount.textContent = give ? '已选 ' + give + ' 个' : '';
            if (takeCount) takeCount.textContent = take ? '收回 ' + take + ' 个' : '';
            summary.textContent = give || take
                ? [give ? '分给 TA ' + give + ' 个' : '', take ? '收回 ' + take + ' 个' : ''].filter(Boolean).join('，')
                : '勾选要分给 TA 或要收回的口令';
            ok.disabled = !give && !take;
        };
        var filter = function () {
            var k = q ? q.value.trim().toLowerCase() : '';
            [freeList, current].forEach(function (list) {
                items(list).forEach(function (it) {
                    it.hidden = !!k && it.textContent.toLowerCase().indexOf(k) < 0;
                });
            });
        };
        openCodes = function (d) {
            cf.reset();
            cf.setAttribute('action', '/agent/subs/' + d.id + '/codes');
            codesDlg.querySelector('[data-role=title]').textContent = '给 ' + d.name + ' 分口令';
            current = null;
            // 只提交这个二级代理的收回列表，别的藏起来也不提交
            each(lists, function (l) {
                var on = l.getAttribute('data-sub-list') === String(d.id);
                l.hidden = !on || !l.querySelector('.pick-item');
                each(l.querySelectorAll('input'), function (i) { i.disabled = !on; });
                if (on) current = l;
            });
            mineEmpty.hidden = !!(current && current.querySelector('.pick-item'));
            if (q) q.value = '';
            filter();
            paint();
            codesDlg.showModal();
        };
        cf.addEventListener('change', paint);
        if (q) q.addEventListener('input', filter);
        cf.addEventListener('click', function (e) {
            var b = e.target.closest('[data-role=give-usable], [data-role=give-none]');
            if (!b) return;
            var on = b.getAttribute('data-role') === 'give-usable';
            items(freeList).forEach(function (it) {
                if (it.hidden) return;
                it.querySelector('input').checked = on && it.getAttribute('data-usable') === 'true';
            });
            paint();
        });
    }

    document.addEventListener('click', function (e) {
        if (openSub && e.target.closest('[data-sub-new]')) { e.preventDefault(); openSub(null); return; }
        var ed = e.target.closest('[data-sub-edit]');
        if (ed && openSub) { e.preventDefault(); openSub(ed.dataset); return; }
        var cd = e.target.closest('[data-sub-codes]');
        if (cd && openCodes) { e.preventDefault(); openCodes(cd.dataset); }
    });
}
