// 扣量档位编辑器：管理员给代理设、代理给二级代理设，用的是同一套
export default function tierEditor(tierRows, tierEmpty, tierExample, addBtn, who) {
    function cell(name, value, opts) {
        var wrap = document.createElement('span');
        wrap.className = 'tier-cell';
        var input = document.createElement('input');
        input.className = 'input num';
        input.type = 'number';
        input.name = name;
        input.min = opts.min;
        if (opts.max) input.max = opts.max;
        if (opts.required) input.required = true;
        input.placeholder = opts.placeholder;
        if (value !== '' && value !== null && value !== undefined) input.value = value;
        var unit = document.createElement('em');
        unit.textContent = opts.unit;
        wrap.appendChild(input);
        wrap.appendChild(unit);
        return wrap;
    }
    function addTier(min, max, ratio) {
        var row = document.createElement('div');
        row.className = 'tier-row';
        var no = document.createElement('span');
        no.className = 'tier-no';
        row.appendChild(no);
        row.appendChild(cell('tierMin', min, { min: 1, required: true, placeholder: '1', unit: '人' }));
        row.appendChild(cell('tierMax', max, { min: 0, placeholder: '不限', unit: '人' }));
        row.appendChild(cell('tierRatio', ratio, { min: 0, max: 100, required: true, placeholder: '20', unit: '%' }));
        var del = document.createElement('button');
        del.type = 'button';
        del.className = 'icon-btn';
        del.title = '删除这一档';
        del.setAttribute('aria-label', '删除这一档');
        del.setAttribute('data-tier-remove', '');
        del.innerHTML = '<svg class="i i-sm"><use href="#i-x"/></svg>';
        row.appendChild(del);
        tierRows.appendChild(row);
        refreshTiers();
        return row;
    }
    function num(row, name) {
        var v = parseInt(row.querySelector('[name=' + name + ']').value, 10);
        return isNaN(v) ? null : v;
    }
    function readTiers() {
        return Array.prototype.map.call(tierRows.querySelectorAll('.tier-row'), function (row) {
            return { row: row, min: num(row, 'tierMin'), max: num(row, 'tierMax'), ratio: num(row, 'tierRatio') };
        });
    }
    function strong(text) {
        var b = document.createElement('b');
        b.textContent = text;
        return b;
    }
    function refreshTiers() {
        var tiers = readTiers();
        tiers.forEach(function (t, i) {
            t.row.querySelector('.tier-no').textContent = i + 1;
            var bad = t.min !== null && t.max ? t.max < t.min : false;
            t.row.querySelector('[name=tierMax]').setCustomValidity(bad ? '「到」不能小于「从」' : '');
        });
        tierEmpty.hidden = tiers.length > 0;

        var valid = tiers.filter(function (t) { return t.min && t.ratio !== null; })
                         .sort(function (a, b) { return a.min - b.min; });
        tierExample.innerHTML = '';
        if (!valid.length) { tierExample.hidden = true; return; }
        tierExample.hidden = false;

        var overlap = valid.some(function (t, i) {
            var prev = valid[i - 1];
            return prev && (!prev.max || t.min <= prev.max);
        });
        if (overlap) {
            tierExample.appendChild(document.createTextNode('有档位重叠了，重叠部分按「从」较小的那档算。'));
            return;
        }
        // 用落在最高比例那一档中间的人数举例
        var pick = valid.reduce(function (a, b) { return b.ratio > a.ratio ? b : a; });
        var sample = pick.max ? Math.round((pick.min + pick.max) / 2) : pick.min + 20;
        var hit = valid.filter(function (t) { return sample >= t.min && (!t.max || sample <= t.max); })[0];
        var ratio = hit ? hit.ratio : 0;
        var cut = Math.floor(sample * ratio / 100);
        tierExample.appendChild(document.createTextNode('示例：当日 '));
        tierExample.appendChild(strong(sample + ' 人'));
        tierExample.appendChild(document.createTextNode(' → 落在 ' + (hit.min + (hit.max ? '–' + hit.max : ' 人以上')) + (hit.max ? ' 人' : '') + '档 → 扣 '));
        tierExample.appendChild(strong(ratio + '%'));
        tierExample.appendChild(document.createTextNode('（' + cut + ' 人）→ ' + who + '看到 '));
        tierExample.appendChild(strong((sample - cut) + ' 人'));
    }
    function loadTiers(json) {
        tierRows.innerHTML = '';
        try {
            JSON.parse(json || '[]').forEach(function (t) { addTier(t.min, t.max, t.ratio); });
        } catch (e) {}
        refreshTiers();
    }
    // 新档位接着上一档往下排：上一档没填「到」时先给它补上
    addBtn.addEventListener('click', function () {
        var tiers = readTiers();
        var last = tiers[tiers.length - 1];
        var start = 1;
        if (last) {
            var lastMin = last.min || 1;
            if (!last.max) {
                last.max = lastMin + 9;
                last.row.querySelector('[name=tierMax]').value = last.max;
            }
            start = last.max + 1;
        }
        addTier(start, '', '').querySelector('[name=tierMax]').focus();
    });
    tierRows.addEventListener('click', function (e) {
        var b = e.target.closest('[data-tier-remove]');
        if (b) { b.closest('.tier-row').remove(); refreshTiers(); }
    });
    tierRows.addEventListener('input', refreshTiers);

    return { load: loadTiers };
}
