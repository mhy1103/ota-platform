export default function () {

    // 把页面上的文档转成 Markdown：标题按左侧目录分组，表格、代码、接口列表照原样转换
    function mdInline(node) {
        var out = '';
        Array.prototype.forEach.call(node.childNodes, function (n) {
            if (n.nodeType === 3) { out += n.nodeValue.replace(/\s+/g, ' '); return; }
            if (n.nodeType !== 1) return;
            var tag = n.tagName.toLowerCase();
            if (tag === 'button' || tag === 'svg') return;
            if (tag === 'code') {
                var c = n.textContent.replace(/\s+/g, ' ').trim();
                out += c.indexOf('`') >= 0 ? '`` ' + c + ' ``' : '`' + c + '`';
                return;
            }
            if (tag === 'strong' || tag === 'b') { var t = mdInline(n).trim(); if (t) out += '**' + t + '**'; return; }
            if (tag === 'a') {
                var href = n.getAttribute('href') || '', label = mdInline(n);
                out += href && href.charAt(0) !== '#' ? '[' + label + '](' + href + ')' : label;
                return;
            }
            if (tag === 'br') { out += '  \n'; return; }
            out += mdInline(n);
        });
        return out;
    }
    function mdCell(td) {
        return mdInline(td).trim().replace(/\|/g, '\\|') || ' ';
    }
    function mdBlock(el, out, skip) {
        Array.prototype.forEach.call(el.children, function (n) {
            if (n === skip) return;
            var tag = n.tagName.toLowerCase(), cls = n.classList;
            if (tag === 'h2' || tag === 'h3' || tag === 'h4') { out.push('#### ' + n.textContent.trim()); return; }
            if (tag === 'p') { var t = mdInline(n).trim(); if (t) out.push(t); return; }
            if (tag === 'ul' && cls.contains('ep-list')) {
                out.push(Array.prototype.map.call(n.children, function (li) {
                    var m = li.querySelector('.method'), c = li.querySelector('code'), d = li.querySelector(':scope > span:last-child');
                    return '- **' + (m ? m.textContent.trim() : '') + '** `' + (c ? c.textContent.trim() : '') + '`' + (d && d !== m ? ' — ' + mdInline(d).trim() : '');
                }).join('\n'));
                return;
            }
            if (tag === 'ol' || tag === 'ul') {
                out.push(Array.prototype.map.call(n.children, function (li, i) {
                    return (tag === 'ol' ? (i + 1) + '. ' : '- ') + mdInline(li).trim();
                }).join('\n'));
                return;
            }
            if (cls.contains('ptable')) {
                var heads = n.querySelectorAll('thead th'), rows = n.querySelectorAll('tbody tr');
                if (!heads.length) {
                    out.push(Array.prototype.map.call(rows, function (tr) {
                        var cells = Array.prototype.map.call(tr.children, mdCell);
                        return '- **' + cells[0].replace(/^\*\*|\*\*$/g, '') + '**：' + cells.slice(1).join('；');
                    }).join('\n'));
                    return;
                }
                var lines = ['| ' + Array.prototype.map.call(heads, mdCell).join(' | ') + ' |', '|' + Array.prototype.map.call(heads, function () { return ' --- '; }).join('|') + '|'];
                Array.prototype.forEach.call(rows, function (tr) { lines.push('| ' + Array.prototype.map.call(tr.children, mdCell).join(' | ') + ' |'); });
                out.push(lines.join('\n'));
                return;
            }
            if (cls.contains('code')) {
                var label = (n.querySelector('.code-bar span') || {}).textContent || '', pre = n.querySelector('pre');
                var l = label.toLowerCase();
                var lang = l.indexOf('json') >= 0 ? 'json' : l.indexOf('javascript') >= 0 ? 'js' : (l.indexOf('curl') >= 0 || label.indexOf('命令') >= 0) ? 'bash' : '';
                if (label) out.push('**' + label.trim() + '**');
                out.push('```' + lang + '\n' + (pre ? pre.textContent.replace(/\s+$/, '') : '') + '\n```');
                return;
            }
            if (cls.contains('note')) { out.push('> ' + mdInline(n).trim()); return; }
            if (cls.contains('facts')) {
                out.push(Array.prototype.map.call(n.querySelectorAll('.fact'), function (f) {
                    return '- ' + f.querySelector('span').textContent.trim() + '：' + f.querySelector('b').textContent.trim();
                }).join('\n'));
                return;
            }
            mdBlock(n, out, skip);
        });
    }
    function docsMarkdown(title) {
        var groups = {};
        Array.prototype.forEach.call(document.querySelectorAll('#toc .toc-group'), function (g) {
            var name = g.querySelector('.toc-title').textContent.trim();
            Array.prototype.forEach.call(g.querySelectorAll('a[href^="#"]'), function (a) { groups[a.getAttribute('href').slice(1)] = name; });
        });
        var now = new Date(), pad = function (x) { return (x < 10 ? '0' : '') + x; };
        var out = ['# API 文档' + (title ? ' · ' + title : ''),
                   '> 导出于 ' + now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate()) + ' ' + pad(now.getHours()) + ':' + pad(now.getMinutes())
                   + '，内容和后台「API 文档」页一致，地址、App 标识都是当前 App 的。'];
        var lastGroup = null;
        Array.prototype.forEach.call(document.querySelectorAll('.doc-sec'), function (sec) {
            var group = groups[sec.id];
            if (group && group !== lastGroup) { out.push('## ' + group); lastGroup = group; }
            var head = sec.querySelector('.doc-head'), body = sec.querySelector('.doc-body'), skip = null;
            var method = head.querySelector('.method');
            if (method) {
                skip = body.querySelector(':scope > h3');
                var meta = head.querySelector('.ep-meta');
                out.push('### ' + (skip ? skip.textContent.trim() : head.querySelector('.ep-path').textContent.trim()));
                out.push('`' + method.textContent.trim() + ' ' + head.querySelector('.ep-path').textContent.trim() + '`' + (meta ? ' · ' + meta.textContent.trim() : ''));
            } else {
                out.push('### ' + head.textContent.trim());
            }
            mdBlock(body, out, skip);
        });
        return out.join('\n\n') + '\n';
    }
    window.otaDocsMarkdown = docsMarkdown;
    var mdBtn = document.querySelector('[data-docs-md]');
    if (mdBtn) mdBtn.addEventListener('click', function () {
        var name = mdBtn.getAttribute('data-name') || 'ota';
        var blob = new Blob([docsMarkdown(mdBtn.getAttribute('data-title'))], { type: 'text/markdown;charset=utf-8' });
        var a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = name + '-API文档.md';
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(function () { URL.revokeObjectURL(a.href); }, 1000);
    });

    // 左侧目录跟着正文走：以顶栏、标签栏下沿为准线，最后一个越过准线的章节就是当前章节；
    // 点目录时先锁定点的那一项，滚动结束再恢复跟随（靠近页尾的短章节滚不到顶，也不会跳错）
    var toc = document.getElementById('toc');
    if (!toc) return;
    var links = {}, order = [];
    Array.prototype.forEach.call(toc.querySelectorAll('a[href^="#"]'), function (a) {
        var id = a.getAttribute('href').slice(1);
        links[id] = a;
        order.push(id);
    });
    var sections = order.map(function (id) { return document.getElementById(id); }).filter(Boolean);
    var current = null, locked = null, lockTimer = 0, raf = 0;

    function headerLine() {
        var bottom = 0;
        Array.prototype.forEach.call(document.querySelectorAll('.topbar, #tabbar'), function (el) {
            var r = el.getBoundingClientRect();
            if (r.height && getComputedStyle(el).position === 'sticky') bottom = Math.max(bottom, r.bottom);
        });
        return bottom + 16;
    }
    // 目录是 sticky 的，CSS 里的 max-height 按「已经吸顶」算（100vh - top - 18）；页面还没往下滚时目录在吸顶线下方，
    // 底部会超出窗口，再加上 overscroll-behavior:contain 滚轮带不动页面，最后几项（「附录 / 限制」）就够不着。
    // 这里按目录当前实际位置限高，保证整块目录始终在窗口内、可以滚到底
    function fitToc() {
        if (!toc.isConnected || !toc.getClientRects().length) return;
        var stick = parseFloat(getComputedStyle(toc).top) || 0;
        var top = Math.max(toc.getBoundingClientRect().top, stick);
        toc.style.maxHeight = Math.max(160, Math.floor(window.innerHeight - top - 18)) + 'px';
    }
    function setCurrent(id) {
        if (!id || id === current || !links[id]) return;
        if (current && links[current]) links[current].classList.remove('on');
        links[id].classList.add('on');
        current = id;
        var a = links[id];
        if (a.offsetTop < toc.scrollTop + 24 || a.offsetTop + a.offsetHeight > toc.scrollTop + toc.clientHeight - 24) {
            toc.scrollTo({ top: a.offsetTop - toc.clientHeight / 3, behavior: 'smooth' });
        }
        if (jump) label(id);
    }
    function spy() {
        raf = 0;
        fitToc();
        if (locked) return;
        var line = headerLine(), id = sections.length ? sections[0].id : null;
        for (var i = 0; i < sections.length; i++) {
            if (sections[i].getBoundingClientRect().top - line <= 1) id = sections[i].id; else break;
        }
        if (Math.ceil(window.innerHeight + window.scrollY) >= document.documentElement.scrollHeight - 4) {
            for (var j = sections.length - 1; j >= 0; j--) {
                if (sections[j].getBoundingClientRect().top < window.innerHeight - 80) { id = sections[j].id; break; }
            }
        }
        setCurrent(id);
    }
    function goTo(id, smooth) {
        var sec = document.getElementById(id);
        if (!sec) return;
        locked = id;
        setCurrent(id);
        clearTimeout(lockTimer);
        var y = sec.getBoundingClientRect().top + window.scrollY - headerLine() + 2;
        window.scrollTo({ top: Math.max(0, y), behavior: smooth ? 'smooth' : 'auto' });
        history.replaceState(history.state, '', location.pathname + location.search + '#' + id);
        lockTimer = setTimeout(function () { locked = null; }, smooth ? 900 : 120);
    }
    toc.addEventListener('click', function (e) {
        var a = e.target.closest('a[href^="#"]');
        if (!a) return;
        e.preventDefault();
        goTo(a.getAttribute('href').slice(1), true);
    });

    // 窄屏没有左侧目录：顶部一条显示当前章节，点开从这一条展开成毛玻璃的目录面板（面板挂在 body 上，离开页面时移除）
    var jump = document.querySelector('[data-docs-jump]'), sheet = null, sheetTimer = 0;
    Array.prototype.forEach.call(document.querySelectorAll('body > .toc-sheet'), function (s) { s.remove(); });
    function label(id) {
        var a = links[id], g = a.closest('.toc-group');
        jump.querySelector('[data-role=group]').textContent = g ? g.querySelector('.toc-title').textContent.trim() : '';
        jump.querySelector('[data-role=name]').innerHTML = a.innerHTML;
        if (sheet) Array.prototype.forEach.call(sheet.querySelectorAll('.ts-item'), function (b) { b.classList.toggle('on', b.getAttribute('data-id') === id); });
    }
    function buildSheet() {
        sheet = document.createElement('div');
        sheet.className = 'toc-sheet';
        sheet.hidden = true;
        sheet.innerHTML = '<div class="ts-veil"></div><div class="ts-panel" role="dialog" aria-modal="true" aria-label="文档目录" tabindex="-1">'
            + '<div class="ts-head"><svg class="i i-sm"><use href="#i-file"/></svg><b>目录</b>'
            + '<button class="ts-x" type="button" aria-label="关闭"><svg class="i i-sm"><use href="#i-x"/></svg></button></div>'
            + '<div class="ts-list"></div></div>';
        var list = sheet.querySelector('.ts-list');
        Array.prototype.forEach.call(toc.querySelectorAll('.toc-group'), function (g) {
            var box = document.createElement('div'), title = document.createElement('div');
            box.className = 'ts-group';
            title.className = 'ts-title';
            title.textContent = g.querySelector('.toc-title').textContent.trim();
            box.appendChild(title);
            Array.prototype.forEach.call(g.querySelectorAll('a[href^="#"]'), function (a) {
                var b = document.createElement('button');
                b.type = 'button';
                b.className = 'ts-item' + (a.getAttribute('href').slice(1) === current ? ' on' : '');
                b.setAttribute('data-id', a.getAttribute('href').slice(1));
                b.innerHTML = '<span class="ts-text">' + a.innerHTML + '</span><svg class="i i-sm ts-tick"><use href="#i-tick"/></svg>';
                box.appendChild(b);
            });
            list.appendChild(box);
        });
        sheet.addEventListener('click', function (e) {
            var it = e.target.closest('.ts-item');
            if (it) {
                closeSheet();
                goTo(it.getAttribute('data-id'), true);
            } else if (!e.target.closest('.ts-panel') || e.target.closest('.ts-x')) {
                closeSheet();
            }
        });
        // 面板开着时页面不跟着滑
        var hold = function (e) {
            var l = e.target.closest('.ts-list');
            if (!l || l.scrollHeight <= l.clientHeight) e.preventDefault();
        };
        sheet.addEventListener('touchmove', hold, { passive: false });
        sheet.addEventListener('wheel', hold, { passive: false });
        document.body.appendChild(sheet);
    }
    function place() {
        var r = jump.getBoundingClientRect(), panel = sheet.querySelector('.ts-panel');
        panel.style.setProperty('--top', r.top + 'px');
        panel.style.setProperty('--from', r.height + 'px');
        panel.style.left = r.left + 'px';
        panel.style.width = r.width + 'px';
    }
    function openSheet() {
        if (!sheet) buildSheet();
        clearTimeout(sheetTimer);
        place();
        sheet.classList.remove('out');
        sheet.hidden = false;
        jump.setAttribute('aria-expanded', 'true');
        var list = sheet.querySelector('.ts-list'), on = list.querySelector('.ts-item.on');
        list.scrollTop = on ? on.offsetTop - (list.clientHeight - on.offsetHeight) / 2 : 0;
        sheet.querySelector('.ts-panel').focus({ preventScroll: true });
    }
    function closeSheet() {
        if (!sheet || sheet.hidden || sheet.classList.contains('out')) return;
        sheet.classList.add('out');
        jump.setAttribute('aria-expanded', 'false');
        sheetTimer = setTimeout(function () {
            sheet.hidden = true;
            sheet.classList.remove('out');
        }, 220);
    }
    if (jump) {
        jump.addEventListener('click', openSheet);
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape' || !sheet || sheet.hidden) return;
            e.preventDefault();
            closeSheet();
        });
        window.addEventListener('resize', function () {
            if (!sheet || sheet.hidden) return;
            if (jump.getClientRects().length) place(); else closeSheet();
        });
        window.addEventListener('ota:leave', function () {
            if (sheet) sheet.remove();
            sheet = null;
        });
    }

    window.addEventListener('scroll', function () { if (!raf) raf = requestAnimationFrame(spy); }, { passive: true });
    window.addEventListener('resize', function () { if (!raf) raf = requestAnimationFrame(spy); });
    // 标签栏由 ui.js 稍后插入、或开关 data-tabbar 时，目录位置会变但不触发滚动，跟着页面尺寸变化重新限高
    if (window.ResizeObserver) {
        var ro = new ResizeObserver(function () { if (!toc.isConnected) { ro.disconnect(); return; } if (!raf) raf = requestAnimationFrame(spy); });
        ro.observe(document.body);
        window.addEventListener('ota:leave', function () { ro.disconnect(); });
    }
    fitToc();
    if (location.hash && document.getElementById(location.hash.slice(1))) {
        setTimeout(function () { goTo(location.hash.slice(1), false); }, 60);
    } else {
        spy();
    }
}
