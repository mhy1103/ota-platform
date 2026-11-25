export default function () {
    // 定时调整灰度：带上渠道和运行版本，默认比现在多一档
    var pdlg = document.getElementById('rollout-plan-dialog'), pform = document.getElementById('rollout-plan-form');
    document.addEventListener('click', function (e) {
        var b = e.target.closest('[data-plan-rollout]');
        if (!b || !pform) return;
        e.preventDefault();
        var ch = b.getAttribute('data-channel'), rt = b.getAttribute('data-runtime'), cur = parseInt(b.getAttribute('data-rollout'), 10) || 0;
        var host = b.closest('dialog');
        if (host && host.open) host.close();
        pform.elements.channel.value = ch;
        pform.elements.runtime.value = rt;
        pform.elements.percent.value = cur < 10 ? 10 : cur < 30 ? 30 : cur < 50 ? 50 : 100;
        pdlg.querySelector('[data-role=sub]').textContent = ch + ' · Runtime ' + rt + '，现在灰度 ' + cur + '%';
        pdlg.showModal();
    });
    var ddlg = document.getElementById('deploy-dialog');
    var dform = document.getElementById('deploy-form');
    var deployFrom = null, deployRuntime = '', plans = {};
    var kinds = function () {
        var on = function (v) { var i = dform.querySelector('input[name=as][value=' + v + ']'); return !!(i && i.checked); };
        return { hot: on('hot'), act: on('activation') };
    };
    // 发之前问服务器会怎样：线上已经是这份内容只调灰度；比线上旧才复制成新版本（App 只装比自己新的）。
    // 和真正发布用同一套判断，同一个 dist 传了两次、线上是副本的原版这些情况都算得准
    function loadPlan() {
        var id = dform.elements.releaseId.value, ch = dform.elements.channel.value, key = id + '|' + ch;
        if (!id || !ch || plans[key]) return;
        plans[key] = { pending: true };
        fetch('/admin/deploy/plan?releaseId=' + encodeURIComponent(id) + '&channel=' + encodeURIComponent(ch), { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (r) { return r.json(); })
            .then(function (p) { plans[key] = p.ok ? p : { mode: '' }; paintDeploy(); }, function () { plans[key] = { mode: '' }; paintDeploy(); });
    }
    // 常规更新、口令更新可以都选：口令更新不灰度，只选它时灰度比例收起来；下面写明这个渠道现在两种更新各是哪个版本
    function paintDeploy() {
        var k = kinds(), hot = k.hot, act = k.act, both = hot && act;
        var role = function (r) { return ddlg.querySelector('[data-role=' + r + ']'); };
        role('rollout-field').hidden = !hot;
        role('fields').classList.toggle('one', !hot);
        dform.elements.rollout.disabled = !hot;
        role('note-hot').hidden = !hot;
        role('note-act').hidden = !act || hot;
        role('verb').textContent = both ? '发布并设口令更新包' : act ? '设口令更新包' : deployFrom ? '推到其他渠道' : '发布';
        role('lead').textContent = both
            ? '已激活的设备检查更新时拿到它（可以灰度、回滚）；新装 App 输入口令激活时也下载它。'
            : act
                ? '新装 App 输入口令激活时下载这个包，不灰度；已激活的设备照常拿常规更新包。'
                : deployFrom
                    ? '把 ' + deployFrom + ' 正在用的版本原样发到另一个渠道，不用重新上传。那个渠道原来的版本记为上一版本，随时能回滚。'
                    : '发布后，这个渠道里 Runtime 相同的设备下次检查更新就会拿到。原来的版本记为上一版本，随时能回滚。';
        var ch = dform.elements.channel.value, now = role('now'), copy = role('copy');
        var row = Array.prototype.find.call(document.querySelectorAll('.dep-row[data-ch]'), function (r) {
            return r.getAttribute('data-ch') === ch && r.getAttribute('data-rt') === deployRuntime;
        });
        var reg = row ? row.getAttribute('data-reg') : '', cur = row ? row.getAttribute('data-act') : '';
        var plan = plans[dform.elements.releaseId.value + '|' + ch] || {};
        var same = hot && plan.mode === 'same', older = hot && plan.mode === 'copy';
        var parts = [];
        if (hot) {
            parts.push(same ? ch + ' 现在的常规更新包是 ' + reg + '，就是这个版本的内容'
                : reg ? ch + ' 现在的常规更新包是 ' + reg + '，发布后它变成上一版本' : ch + ' 在 Runtime ' + deployRuntime + ' 下还没有常规更新包');
        }
        if (act) parts.push(cur ? (hot ? '口令更新包' : ch + ' 现在的口令更新包是 ') + (hot ? ' ' + cur + ' 也换成这个版本' : cur + '，会换成这个版本') : (hot ? '口令更新包也设成这个版本' : ch + ' 现在没单独设口令更新包，新装 App 激活时拿常规更新包' + (reg ? ' ' + reg : '')));
        else if (cur) parts.push('口令更新包 ' + cur + ' 不受影响');
        else if (!same) parts.push('新装 App 激活时也拿这个版本');
        now.textContent = parts.join('；');
        now.hidden = !ch || !parts.length;
        copy.textContent = same ? '线上的 ' + plan.live + ' 和它是同一份内容，不会再复制，只按上面的比例调整灰度'
            : older ? '线上的 ' + plan.live + (plan.liveCopy ? '（副本）' : '') + ' 是 ' + plan.liveAt + ' 建的，比这个版本（' + plan.at + '）晚。App 只装比自己正在用的更新的版本，所以要复制成一个内容相同的新版本再发布，已经装了 ' + plan.live + ' 的设备才会换过来' : '';
        copy.hidden = !(same || older);
        role('submit').textContent = !hot && !act ? '发布' : both ? (older ? '复制成新版本发布，并设口令更新包' : '发布并设为口令更新包')
            : act ? '设为口令更新包' : same ? '调整灰度' : older ? '复制成新版本发布' : '发布';
        role('submit').disabled = !hot && !act;
    }
    if (dform) dform.addEventListener('change', function (e) {
        if (e.target.name === 'as' || e.target.name === 'channel') { loadPlan(); paintDeploy(); }
    });
    if (dform) dform.addEventListener('submit', function (e) {
        var k = kinds();
        if (k.hot || k.act) return;
        e.preventDefault();
        window.otaToast('至少选一种：常规更新或口令更新', 'err');
    });
    document.addEventListener('click', function (e) {
        var b = e.target.closest('[data-deploy]');
        if (!b || !dform) return;
        var from = b.getAttribute('data-from');
        deployFrom = from;
        deployRuntime = b.getAttribute('data-runtime') || '';
        dform.elements.releaseId.value = b.getAttribute('data-id');
        dform.elements.rollout.value = 100;
        Array.prototype.forEach.call(dform.querySelectorAll('input[name=as]'), function (i) { i.checked = i.value === 'hot'; });
        plans = {};
        ddlg.querySelector('[data-role=short]').textContent = b.getAttribute('data-short') + ' · Runtime ' + deployRuntime;
        var sel = dform.elements.channel, picked = false;
        Array.prototype.forEach.call(sel.options, function (o) {
            o.disabled = !!from && o.value === from;
            if (!picked && !o.disabled && (from || o.index === 0)) { sel.value = o.value; picked = true; }
        });
        loadPlan();
        paintDeploy();
        ddlg.showModal();
    });

    // 调整灰度：弹窗里改比例，下面的条和说明跟着变
    var odlg = document.getElementById('rollout-dialog'), oform = document.getElementById('rollout-form');
    function paintRollout() {
        if (!oform) return;
        var raw = oform.elements.rollout.value, v = Math.max(0, Math.min(100, parseInt(raw, 10) || 0));
        var role = function (r) { return odlg.querySelector('[data-role=' + r + ']'); };
        role('bar-new').style.width = v + '%';
        role('bar-old').style.width = (100 - v) + '%';
        role('p-new').textContent = v + '%';
        role('p-old').textContent = (100 - v) + '%';
        role('rest').hidden = v >= 100;
        Array.prototype.forEach.call(odlg.querySelectorAll('.quick-picks button'), function (q) {
            q.classList.toggle('on', q.getAttribute('data-v') === String(v) && raw !== '');
        });
    }
    document.addEventListener('click', function (e) {
        var b = e.target.closest('[data-rollout-adjust]');
        if (!b || !oform) return;
        var ch = b.getAttribute('data-channel'), rt = b.getAttribute('data-runtime'), cur = b.getAttribute('data-rollout');
        var prev = b.getAttribute('data-prev');
        oform.elements.channel.value = ch;
        oform.elements.runtime.value = rt;
        oform.elements.rollout.value = cur;
        odlg.querySelector('[data-role=title]').textContent = ch + ' · Runtime ' + rt;
        odlg.querySelector('[data-role=t-new]').textContent = '拿 ' + b.getAttribute('data-short');
        odlg.querySelector('[data-role=t-old]').textContent = prev ? '继续用上一版本 ' + prev : '先不更新，新装的照样拿新版本';
        var plan = odlg.querySelector('[data-role=plan]');
        plan.setAttribute('data-channel', ch);
        plan.setAttribute('data-runtime', rt);
        plan.setAttribute('data-rollout', cur);
        paintRollout();
        odlg.showModal();
        setTimeout(function () { oform.elements.rollout.select(); }, 60);
    });
    document.addEventListener('input', function (e) {
        if (e.target.id === 'ro-dlg') paintRollout();
    });

    // 回滚：列出同一 Runtime 之前的版本，默认选上一版本；和现在线上内容相同的不列
    var rdlg = document.getElementById('rollback-dialog');
    var rform = document.getElementById('rollback-form');
    document.addEventListener('click', function (e) {
        var b = e.target.closest('[data-rollback]');
        if (!b || !rform) return;
        var runtime = b.getAttribute('data-runtime'), current = b.getAttribute('data-current') || '', prev = b.getAttribute('data-previous') || '';
        rform.elements.channel.value = b.getAttribute('data-channel');
        rform.elements.runtime.value = runtime;
        rdlg.querySelector('[data-role=title]').textContent = b.getAttribute('data-channel') + ' · Runtime ' + runtime;
        var first = null, chosen = null, shown = 0;
        Array.prototype.forEach.call(rdlg.querySelectorAll('.rb-item'), function (it) {
            var origin = it.getAttribute('data-origin');
            var ok = it.getAttribute('data-runtime') === runtime && origin !== current;
            var input = it.querySelector('input');
            it.hidden = !ok;
            input.disabled = !ok;
            input.checked = false;
            it.querySelector('[data-role=prev-tag]').hidden = !(ok && origin === prev);
            if (!ok) return;
            shown++;
            if (!first) first = input;
            if (origin === prev) chosen = input;
        });
        if (chosen || first) (chosen || first).checked = true;
        rdlg.querySelector('[data-role=none]').hidden = shown > 0;
        rform.querySelector('[type=submit]').disabled = shown === 0;
        rdlg.showModal();
        var picked = rdlg.querySelector('.rb-item input:checked');
        if (picked) setTimeout(function () { picked.closest('.rb-item').scrollIntoView({ block: 'nearest' }); picked.focus({ preventScroll: true }); }, 60);
    });

    // 口令更新包：列出同一 Runtime 的版本，默认选现在的口令更新包，没设时选线上版本；选的就是现在这个时不用提交
    var adlg = document.getElementById('activation-dialog'), aform = document.getElementById('activation-form');
    var actCurrent = '';
    function paintActivation() {
        var picked = aform.querySelector('.rb-item input:checked');
        aform.querySelector('[data-role=submit]').disabled = !picked || picked.closest('.rb-item').getAttribute('data-origin') === actCurrent;
    }
    if (aform) aform.addEventListener('change', paintActivation);
    document.addEventListener('click', function (e) {
        var b = e.target.closest('[data-activation]');
        if (!b || !aform) return;
        var runtime = b.getAttribute('data-runtime'), live = b.getAttribute('data-live') || '';
        actCurrent = b.getAttribute('data-current') || '';
        aform.elements.channel.value = b.getAttribute('data-channel');
        adlg.querySelector('[data-role=title]').textContent = b.getAttribute('data-channel') + ' · Runtime ' + runtime;
        var first = null, chosen = null, shown = 0;
        Array.prototype.forEach.call(adlg.querySelectorAll('.rb-item'), function (it) {
            var origin = it.getAttribute('data-origin');
            var ok = it.getAttribute('data-runtime') === runtime;
            var input = it.querySelector('input');
            it.hidden = !ok;
            input.disabled = !ok;
            input.checked = false;
            it.querySelector('[data-role=cur-tag]').hidden = !(ok && actCurrent && origin === actCurrent);
            it.querySelector('[data-role=live-tag]').hidden = !(ok && live && origin === live);
            if (!ok) return;
            shown++;
            if (!first) first = input;
            if (origin === (actCurrent || live)) chosen = input;
        });
        if (chosen || first) (chosen || first).checked = true;
        adlg.querySelector('[data-role=none]').hidden = shown > 0;
        paintActivation();
        adlg.showModal();
        var picked = adlg.querySelector('.rb-item input:checked');
        if (picked) setTimeout(function () { picked.closest('.rb-item').scrollIntoView({ block: 'nearest' }); picked.focus({ preventScroll: true }); }, 60);
    });

    var dlg = document.getElementById('verify-dialog');
    var $ = function (role) { return dlg.querySelector('[data-role="' + role + '"]'); };
    var current = null;
    var ICON = { ok: '#i-tick', error: '#i-x' };

    function render(data) {
        $('loading').hidden = true;
        $('result').hidden = false;
        $('again').hidden = false;
        var hero = $('hero');
        hero.className = 'hero' + (data.level === 'ok' ? '' : ' ' + data.level);
        $('hero-icon').setAttribute('href', data.level === 'ok' ? '#i-tick' : '#i-alert');
        $('headline').textContent = data.headline;
        var counts = { ok: 0, warn: 0, error: 0 };
        data.checks.forEach(function (c) { if (counts[c.state] !== undefined) counts[c.state]++; });
        $('summary').textContent = counts.ok + ' 项通过' + (counts.warn ? '，' + counts.warn + ' 项提醒' : '') + (counts.error ? '，' + counts.error + ' 项未通过' : '');
        var list = $('checks');
        list.innerHTML = '';
        data.checks.forEach(function (c) {
            var li = document.createElement('li');
            li.className = c.state;
            var st = document.createElement('span');
            st.className = 'st st-' + c.state;
            if (ICON[c.state]) st.innerHTML = '<svg class="i"><use href="' + ICON[c.state] + '"/></svg>';
            else if (c.state === 'warn') st.textContent = '!';
            var body = document.createElement('span');
            var b = document.createElement('b');
            b.textContent = c.label;
            var t = document.createElement('span');
            t.className = 'sub';
            t.textContent = c.text;
            body.appendChild(b);
            body.appendChild(t);
            li.appendChild(st);
            li.appendChild(body);
            list.appendChild(li);
        });
    }

    function run() {
        if (!current) return;
        $('loading').hidden = false;
        $('result').hidden = true;
        $('again').hidden = true;
        $('loading-text').textContent = current.ext ? '正在逐个下载并核对文件，文件多会慢一些…' : '正在校验…';
        var body = new URLSearchParams({ releaseId: current.id });
        fetch('/admin/verify', { method: 'POST', body: body, headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (r) {
                if (r.redirected && r.url.indexOf('/login') >= 0) throw new Error('登录已过期，请刷新页面后重新登录');
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then(render)
            .catch(function (e) {
                render({ level: 'error', headline: '校验请求失败', checks: [{ label: '请求', state: 'error', text: e.message }] });
            });
    }

    document.addEventListener('click', function (e) {
        var b = e.target.closest('[data-verify]');
        if (!b) return;
        current = { id: b.getAttribute('data-id'), ext: b.getAttribute('data-ext') === 'true' };
        $('title').textContent = current.id.substring(0, 8);
        $('sub').textContent = current.ext ? '外部链接：App 直接从这个地址下载，这里逐个下载核对哈希、Runtime 和签名'
                                           : '核对每个文件的哈希、Runtime 版本、签名和增量补丁';
        dlg.showModal();
        run();
    });
    $('again').addEventListener('click', run);

    // 版本详情
    var det = document.getElementById('detail-dialog');
    var d$ = function (role) { return det.querySelector('[data-role="' + role + '"]'); };
    var detailId = null, detailExt = false;
    function cell(tr, text, cls) {
        var td = document.createElement('td');
        if (cls) td.className = cls;
        td.textContent = text;
        tr.appendChild(td);
        return td;
    }
    // 包结构：每个文件和对比版本比是新增、有变化还是没变，更新时实际下载多少
    var STATUS_CLASS = { added: 'ok', changed: 'info', same: 'line', moved: 'line', deleted: 'err', none: 'line' };
    function renderStructure(s) {
        var tbody = d$('files');
        tbody.innerHTML = '';
        if (!s) return;
        d$('files-title').textContent = '包结构（' + s.files + ' 个文件）';
        d$('struct-sum').textContent = s.summary;
        s.rows.forEach(function (f) {
            var tr = document.createElement('tr');
            if (f.status === 'deleted') tr.className = 'struct-gone';
            cell(tr, f.platform);
            var p = cell(tr, f.path, 'path');
            p.title = f.sha256;
            if (f.launch) {
                var tag = document.createElement('span');
                tag.className = 'pill line';
                tag.style.marginLeft = '6px';
                tag.textContent = '启动包';
                p.appendChild(tag);
            }
            var st = cell(tr, '');
            if (f.status !== 'none') {
                var pill = document.createElement('span');
                pill.className = 'pill ' + (STATUS_CLASS[f.status] || 'line');
                pill.textContent = f.statusLabel;
                st.appendChild(pill);
            } else {
                st.textContent = '—';
            }
            cell(tr, f.size, 'right num nowrap');
            var dl = cell(tr, f.download, 'right num nowrap' + (f.kind === 'patch' ? ' struct-patch' : f.kind === 'none' ? ' muted' : ''));
            if (f.kind === 'patch') dl.title = '增量补丁：只下载和对比版本启动包的差异';
            tbody.appendChild(tr);
        });
    }
    d$('base').addEventListener('change', function (e) {
        if (!detailId) return;
        var sel = e.target;
        sel.disabled = true;
        fetch('/admin/release/' + encodeURIComponent(detailId) + '/structure?base=' + encodeURIComponent(sel.value), { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (r) { return r.json(); })
            .then(function (s) {
                if (!s.ok) throw new Error(s.msg || '读取失败');
                renderStructure(s);
            })
            .catch(function (err) { window.otaToast(err.message, 'err'); })
            .then(function () { sel.disabled = false; });
    });
    function showDetail(data) {
        d$('loading').hidden = true;
        d$('body').hidden = false;
        d$('force').hidden = !data.forceUpdate;
        d$('sub').textContent = (data.createdAt || '') + (data.createdBy ? ' · ' + data.createdBy : '') + (data.message ? ' · ' + data.message : '');
        d$('runtime').textContent = data.runtime;
        d$('platforms').textContent = data.platforms;
        d$('size').textContent = data.size;
        d$('devices').textContent = data.devices;
        d$('id').textContent = data.id;
        var where = [], live = d$('live');
        if (data.liveIn.length) where.push('常规更新：' + data.liveIn.join('、'));
        else if (data.previousIn && data.previousIn.length) where.push(data.previousIn.join('、') + ' 的上一版本（可以回滚到它）');
        if (data.activationIn && data.activationIn.length) where.push('口令更新：' + data.activationIn.join('、'));
        if (!where.length) where.push('没有发布到渠道');
        live.textContent = '';
        where.forEach(function (w) {
            var line = document.createElement('span');
            line.className = 'd-where';
            line.textContent = w;
            live.appendChild(line);
        });
        d$('source').textContent = data.source + (data.republishedFrom ? '，复制自 ' + data.republishedFrom : '');
        d$('external').textContent = data.externalBaseUrl || '—';
        detailExt = !!data.externalBaseUrl;
        var form = d$('edit');
        form.elements.releaseId.value = data.id;
        form.elements.note.value = data.message || '';
        form.elements.forceUpdate.checked = !!data.forceUpdate;
        var sel = d$('base');
        sel.innerHTML = '';
        (data.bases || []).forEach(function (b) {
            var o = document.createElement('option');
            o.value = b.id;
            o.textContent = b.label;
            sel.appendChild(o);
        });
        var none = document.createElement('option');
        none.value = '';
        none.textContent = (data.bases || []).length ? '不对比（全部文件）' : '没有更早的版本';
        sel.appendChild(none);
        sel.value = data.structure && data.structure.base ? data.structure.base : '';
        sel.disabled = !(data.bases || []).length;
        renderStructure(data.structure);
        var f = data.funnel || {};
        d$('f-offered').textContent = f.offered || 0;
        d$('f-downloads').textContent = f.downloads || 0;
        d$('f-drate').textContent = f.downloadRate >= 0 && f.downloads ? f.downloadRate + '%' : '';
        d$('f-installed').textContent = f.installed || 0;
        d$('f-rate').textContent = f.installRate >= 0 ? f.installRate + '%' : '';
        d$('f-bar').style.width = (f.installRate > 0 ? f.installRate : 0) + '%';
        d$('f-failed').textContent = f.failed || 0;
        d$('f-frate').textContent = f.failed ? f.failRate + '%' : '';
        d$('f-failed-box').classList.toggle('bad', !!f.failed);
        var dl = (f.patchDownloads || 0) + (f.fullDownloads || 0) + (f.cdnDownloads || 0);
        d$('traffic').textContent = dl
            ? '启动包下载 ' + dl + ' 次：增量补丁 ' + (f.patchDownloads || 0) + ' 次、完整 ' + (f.fullDownloads || 0) + ' 次' + (f.cdnDownloads ? '、CDN ' + f.cdnDownloads + ' 次' : '')
              + ' · 服务器发出 ' + f.served + (f.savedPct > 0 ? ' · 增量更新省了 ' + f.saved + '（' + f.savedPct + '%）' : '')
            : '还没有设备下载这个版本的启动包';
        var reasons = data.reasons || [];
        d$('reasons-box').hidden = !reasons.length;
        var rl = d$('reasons');
        rl.innerHTML = '';
        reasons.forEach(function (x) {
            var li = document.createElement('li');
            var head = document.createElement('div');
            head.className = 'rs-head';
            var msg = document.createElement('code');
            msg.className = 'rs-msg';
            msg.textContent = x.message || '（没有错误信息）';
            msg.title = x.message || '';
            var n = document.createElement('b');
            n.className = 'rs-n';
            n.textContent = x.count + ' 次';
            head.appendChild(msg);
            head.appendChild(n);
            var bar = document.createElement('div');
            bar.className = 'rs-bar';
            var fill = document.createElement('span');
            fill.style.width = Math.max(2, x.pct) + '%';
            bar.appendChild(fill);
            var meta = document.createElement('div');
            meta.className = 'rs-meta';
            meta.textContent = '占 ' + x.pct + '%' + (x.platforms ? ' · ' + x.platforms : '') + (x.last ? ' · 最近一次 ' + x.last : '');
            li.appendChild(head);
            li.appendChild(bar);
            li.appendChild(meta);
            rl.appendChild(li);
        });
        d$('reasons-sub').textContent = (data.reasonKinds > reasons.length ? '共 ' + data.reasonKinds + ' 类，列出最多的 ' + reasons.length + ' 类。' : '')
            + '按错误的第一行归类，同一台设备同样的错误 6 小时内只算一次';
        var errs = data.errors || [];
        d$('errors-box').hidden = !errs.length;
        var ul = d$('errors');
        ul.innerHTML = '';
        errs.forEach(function (x) {
            var li = document.createElement('li');
            var meta = document.createElement('div');
            meta.className = 'meta';
            meta.textContent = x.at + ' · ' + x.device + (x.platform ? ' · ' + x.platform : '');
            var msg = document.createElement('div');
            msg.className = 'err-msg';
            msg.textContent = x.message;
            li.appendChild(meta);
            li.appendChild(msg);
            ul.appendChild(li);
        });
        form.hidden = !data.canRelease;
        var pb = d$('patches');
        pb.innerHTML = '';
        d$('patch-box').hidden = !data.patches.length;
        data.patches.forEach(function (p) {
            var tr = document.createElement('tr');
            cell(tr, p.base + '…', 'mono');
            cell(tr, p.status);
            cell(tr, p.size, 'right num');
            cell(tr, p.ratio, 'right num');
            pb.appendChild(tr);
        });
    }
    document.addEventListener('click', function (e) {
        var a = e.target.closest('[data-detail]');
        if (!a) return;
        e.preventDefault();
        detailId = a.getAttribute('data-id');
        d$('title').textContent = detailId.substring(0, 8);
        d$('sub').textContent = '';
        d$('force').hidden = true;
        d$('loading').hidden = false;
        d$('body').hidden = true;
        det.showModal();
        fetch('/admin/release/' + encodeURIComponent(detailId), { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (r) {
                if (r.redirected && r.url.indexOf('/login') >= 0) throw new Error('登录已过期，请刷新页面后重新登录');
                return r.json();
            })
            .then(function (data) {
                if (!data.ok) throw new Error(data.msg || '读取失败');
                showDetail(data);
            })
            .catch(function (err) {
                det.close();
                window.otaToast(err.message, 'err');
            });
    });
    d$('verify').addEventListener('click', function () {
        if (!detailId) return;
        det.close();
        current = { id: detailId, ext: detailExt };
        $('title').textContent = detailId.substring(0, 8);
        $('sub').textContent = detailExt ? '外部链接：App 直接从这个地址下载，这里逐个下载核对哈希、Runtime 和签名'
                                         : '核对每个文件的哈希、Runtime 版本、签名和增量补丁';
        dlg.showModal();
        run();
    });

    // 上传安装包：XHR 上传显示进度，传完服务器读包、检查，刷新列表
    var inf = document.getElementById('installer-form');
    if (inf) {
        var idlg = inf.closest('dialog');
        var ifile = inf.querySelector('input[type=file]');
        var idrop = inf.querySelector('.drop');
        var iname = inf.querySelector('[data-role=name]');
        var imeta = inf.querySelector('[data-role=meta]');
        var isubmit = inf.querySelector('[data-role=submit]');
        var ibar = inf.querySelector('.progress span');
        var name0 = iname.textContent, meta0 = imeta.textContent;
        var IMAX = 1024 * 1024 * 1024;
        var ireset = function () {
            inf.classList.remove('uploading');
            idlg.removeAttribute('data-busy');
            ibar.style.width = '0';
            isubmit.textContent = '上传并检查';
            isubmit.disabled = !ifile.files.length;
        };
        var ipick = function () {
            var f = ifile.files[0];
            if (!f) { idrop.classList.remove('picked'); iname.textContent = name0; imeta.textContent = meta0; isubmit.disabled = true; return; }
            if (!/\.(apk|ipa)$/i.test(f.name)) { window.otaToast('请选择 .apk 或 .ipa 文件', 'err'); ifile.value = ''; ipick(); return; }
            if (f.size > IMAX) { window.otaToast('文件超过 1 GB 上限', 'err'); ifile.value = ''; ipick(); return; }
            idrop.classList.add('picked');
            iname.textContent = f.name;
            imeta.textContent = (f.size / 1048576).toFixed(1) + ' MB';
            isubmit.disabled = false;
        };
        ifile.addEventListener('change', ipick);
        ['dragenter', 'dragover'].forEach(function (ev) {
            idrop.addEventListener(ev, function (e) { e.preventDefault(); idrop.classList.add('over'); });
        });
        ['dragleave', 'drop'].forEach(function (ev) {
            idrop.addEventListener(ev, function (e) { e.preventDefault(); idrop.classList.remove('over'); });
        });
        idrop.addEventListener('drop', function (e) {
            if (!e.dataTransfer.files.length) return;
            ifile.files = e.dataTransfer.files;
            ipick();
        });
        idlg.addEventListener('close', function () {
            if (idlg.hasAttribute('data-busy')) return;
            ifile.value = '';
            ipick();
        });
        // 分片上传：反向代理（宝塔 nginx 默认 50MB）限制单个请求大小，大的 IPA 切成 8MB 一片按顺序传；
        // 代理限制更小（413）时分片减半，网络断了同一片重传
        inf.addEventListener('submit', function (e) {
            e.preventDefault();
            if (!ifile.files.length) return;
            var f = ifile.files[0], base = inf.getAttribute('action'), note = inf.elements.note.value;
            var chunk = 8 * 1024 * 1024, sent = 0, id = null, tries = 0;
            var show = function (n) {
                var pct = Math.min(100, Math.round(n / f.size * 100));
                ibar.style.width = pct + '%';
                isubmit.textContent = pct < 100 ? '上传中 ' + pct + '%' : '正在读取安装包…';
            };
            var post = function (url, body, cb) {
                var xhr = new XMLHttpRequest();
                xhr.open('POST', url);
                xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
                if (body instanceof Blob) {
                    xhr.setRequestHeader('Content-Type', 'application/octet-stream');
                    xhr.upload.onprogress = function (ev) { if (ev.lengthComputable) show(sent + ev.loaded); };
                }
                xhr.onload = function () {
                    var res = null;
                    try { res = JSON.parse(xhr.responseText); } catch (err) {}
                    cb(xhr, res, false);
                };
                xhr.onerror = function () { cb(xhr, null, true); };
                xhr.send(body);
            };
            var fail = function (xhr, res, neterr) {
                ireset();
                if (res) window.otaToast(res.msg, 'err');
                else if (neterr) window.otaToast('网络错误，上传失败', 'err');
                else if (xhr.responseURL && xhr.responseURL.indexOf('/login') >= 0) window.otaToast('登录已过期，请刷新页面后重新登录', 'err');
                else if (blocked(xhr)) window.otaToast('上传被网站防火墙拦截，把 /admin/installers/upload 加进防火墙的 URL 白名单后重试', 'err');
                else window.otaToast('上传失败（HTTP ' + xhr.status + '）', 'err');
            };
            var blocked = function (xhr) {
                return /^\s*</.test(xhr.responseText || '') && xhr.responseText.indexOf('Nginx缓冲区溢出') < 0;
            };
            var finish = function () {
                show(f.size);
                post(base + '/upload/' + id + '/finish', new URLSearchParams({ note: note }), function (xhr, res, neterr) {
                    if (!(res && res.ok)) return fail(xhr, res, neterr);
                    sessionStorage.setItem('ota.toast', JSON.stringify({ msg: res.msg, type: 'ok' }));
                    idlg.removeAttribute('data-busy');
                    idlg.close();
                    inf.reset();
                    if (window.otaNavigate) window.otaNavigate(inf.getAttribute('data-done')); else location.href = inf.getAttribute('data-done');
                });
            };
            var next = function () {
                if (sent >= f.size) return finish();
                post(base + '/upload/' + id + '?offset=' + sent, f.slice(sent, Math.min(f.size, sent + chunk)), function (xhr, res, neterr) {
                    if (res && res.ok) { sent = res.received; tries = 0; show(sent); next(); return; }
                    var tooBig = xhr.status === 413 || (xhr.responseText || '').indexOf('Nginx缓冲区溢出') >= 0;
                    if (tooBig && chunk > 512 * 1024) { chunk = Math.floor(chunk / 2); next(); return; }
                    if ((neterr || xhr.status >= 500) && ++tries <= 3) { setTimeout(next, 1000 * tries); return; }
                    fail(xhr, res, neterr);
                });
            };
            inf.classList.add('uploading');
            idlg.setAttribute('data-busy', '');
            isubmit.disabled = true;
            isubmit.textContent = '上传中 0%';
            post(base + '/upload/start', new URLSearchParams({ fileName: f.name, size: String(f.size) }), function (xhr, res, neterr) {
                if (!(res && res.ok)) return fail(xhr, res, neterr);
                id = res.id;
                next();
            });
        });
    }
}
