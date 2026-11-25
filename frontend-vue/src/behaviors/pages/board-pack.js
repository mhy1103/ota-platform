// 看板排版（只做计算，不碰页面）：卡片按顺序一行一行往下排，一行 12 列。
// 每一行都在接下来的几张卡片里挑一种最好的排法：放哪几张、哪几张叠成一列（高卡片旁边叠两三张矮的）、要不要跳过一两张留给下面的行、
// 没排满时把叠起来的那一列拉宽。好不好按「代价」比：空着的列、叠起来的卡片、被拉宽的卡片、被跳过的卡片、没拉过高度的卡片被拉高了多少、
// 拉过高度的卡片离它的高度差多少、正在拉的那张离指针差多少，加起来越少越好。
// 同一行所有列的底边对齐；每张卡片的高度都在自己的范围里（最矮是内容刚好显示完整，最高按种类和宽度定）。
// 卡片：span 占几列，min / max 高度范围，want 想要的高度，fixed 拉过高度，pin 正在拉高度的那张，lock 拉的时候同一列在它上面的卡片保持的高度，
// hint 上次看到的排法（'/' 这张开始新的一行，'+' 叠在前一张下面，'~' 和前一张同一行、另起一列，'' 还没排过）：
// 记着排法的行打开、刷新、换设备都原样拼出来；正在改的行重新挑，和上次一样的排法便宜一些，真的更好（比如拉得比旁边的卡片能跟上的高出一截）才换。

var DEEP = 3;   // 一列最多叠几张
var WIN = 6;    // 一行最多在接下来几张里挑
var SKIPS = 2;  // 一行最多跳过几张
var COST = {
    gap: 60,        // 每空一列
    stack: 34,      // 每叠一张
    wide: 5,        // 卡片每被拉宽一列
    skip: 45,       // 每跳过一张
    stretch: 0.9,   // 没拉过高度的卡片每被拉高 1%
    dev: 2,         // 拉过高度的卡片每差 1px
    pin: 2.6,       // 正在拉的那张每差 1px
    pos: 5000,      // 正在拉的那张挪了位置
    stay: 70        // 和上次的排法一样，便宜这么多
};
var hinted = false;
// 这一行的排法和上次一样吗：每张卡片的位置和它记着的一样，这一行后面那张是新的一行
function same(groups, rest, k) {
    if (!hinted) return false;
    for (var c = 0, j = 0; c < groups.length; c++) {
        for (var i = 0; i < groups[c].members.length; i++, j++) {
            var e = rest[j], want = c === 0 && i === 0 ? '/' : i > 0 ? '+' : '~';
            if (!e || groups[c].members[i].el !== e.el) return false;
            if (e.hint !== want) return false;
        }
    }
    return k >= rest.length || rest[k].hint === '/';
}

function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }
function top(m) { return Math.max(m.min, m.max); }
// 正在拉的那张叠在别的卡片下面时，同一列在它上面的卡片高度不变（它的上边不跟着动）
function rigid(m, c) {
    if (m.lock == null || !c) return false;
    for (var j = c.members.indexOf(m) + 1; j < c.members.length; j++) if (c.members[j].pin) return true;
    return false;
}
function low(m, c) { return rigid(m, c) ? clamp(m.lock, m.min, top(m)) : m.min; }
function high(m, c) { return rigid(m, c) ? clamp(m.lock, m.min, top(m)) : top(m); }
function wanted(m, c) { return rigid(m, c) ? clamp(m.lock, m.min, top(m)) : clamp(m.want, m.min, top(m)); }
// 一列补高度时先动没拉过的，再动拉过的，最后才动正在拉的那张
function tier(m, c) { return rigid(m, c) || m.pin ? 2 : m.fixed ? 1 : 0; }
function sum(c, f, g) {
    var t = g * (c.members.length - 1);
    for (var i = 0; i < c.members.length; i++) t += f(c.members[i], c);
    return t;
}
// 这一行能取的高度：每一列都放得下
function band(groups, g) {
    var lo = 0, hi = Infinity;
    groups.forEach(function (c) {
        lo = Math.max(lo, sum(c, low, g));
        hi = Math.min(hi, sum(c, high, g));
    });
    return { lo: lo, hi: hi };
}
// 这一行想要多高：正在拉的那一列说了算；没有就按拉过高度的；都没有就按内容最高的那一列
function target(groups, g) {
    var pin = -1, fix = -1, all = 0;
    groups.forEach(function (c) {
        var s = sum(c, wanted, g);
        all = Math.max(all, s);
        if (c.members.some(function (m) { return m.pin; })) pin = Math.max(pin, s);
        else if (c.members.some(function (m) { return m.fixed; })) fix = Math.max(fix, s);
    });
    return pin >= 0 ? pin : fix >= 0 ? fix : all;
}

// 卡片放进更宽的一列时内容排得更开、要的高度更少：有 fit 就按那一列的宽度算（base 是按自己宽度的那份）
var fitAt = null;
function sized(e, span) {
    var b = e.base || e;
    if (!fitAt || span === b.span) return b;
    var cache = b._sz || (b._sz = {});
    if (cache[span]) return cache[span];
    var f = fitAt(b, span), o = {};
    for (var k in b) if (k !== '_sz') o[k] = b[k];
    if (f) {
        o.min = f.min;
        o.max = f.max;
        o.want = f.want;
    }
    o.base = b;
    cache[span] = o;
    return o;
}

// 一种排法的代价；对不齐返回 null
function evaluate(groups, g, ctx) {
    var b = band(groups, g);
    if (b.lo > b.hi + 0.5) return null;
    var H = Math.round(Math.max(b.lo, Math.min(b.hi, target(groups, g))));
    var cost = 0, x = 0;
    for (var k = 0; k < groups.length; k++) {
        var c = groups[k], n = c.members.length, hs = spread(c, H - g * (n - 1));
        cost += COST.stack * (n - 1);
        for (var i = 0; i < n; i++) {
            var m = c.members[i], h = hs[i];
            if (m.span < c.span) cost += COST.wide * (c.span - m.span);
            if (rigid(m, c)) continue;
            if (m.pin) {
                cost += COST.pin * Math.abs(h - wanted(m, c));
                if (ctx && ctx.pos && (x !== ctx.pos.x || i !== ctx.pos.i)) cost += COST.pos;
            } else if (m.fixed) {
                cost += COST.dev * Math.abs(h - wanted(m, c));
            } else {
                cost += COST.stretch * Math.max(0, h - m.min) * 100 / Math.max(1, m.min);
            }
        }
        x += c.span;
    }
    return cost + COST.gap * Math.max(0, 12 - x);
}

// 从 rest 开头起挑这一行最好的排法：{ cost, groups, taken: rest 里用掉的下标 }。
// ctx.need 这一行必须有的那张（正在拉的），ctx.pos 它原来在这一行的第几列、这一列的第几张。
// 这一行最好的几种还要看看下一行会怎么样（别为了这一行省一点，让下一行空出一大块）
var LOOK = 5, MARGIN = 160, NEXT = 0.85;
// 记着排法的看板：重新挑排法的行只能用到下一个没动过的行之前（不从别的行里拉卡片过来，别的行不会被打乱）
// 开头那张不是一行的开头（上一行放不下挤出来的）：和下一行一起重新挑
function reach(rest, dirty) {
    if (!hinted || !rest.length) return rest.length;
    var skip = rest[0].hint === '/' ? 0 : 1;
    for (var j = 1; j < rest.length; j++) {
        if (rest[j].hint !== '/' || (dirty && dirty.has(rest[j].el))) continue;
        if (skip-- > 0) continue;
        return j;
    }
    return rest.length;
}
function bestRow(rest, g, ctx, shallow, dirty) {
    var need = ctx && ctx.need ? rest.indexOf(ctx.need) : -1;
    var n = Math.min(rest.length, Math.max(WIN, need + 1), Math.max(need + 1, reach(rest, dirty)));
    var cols = [], taken = [], skips = [], found = [], best = Infinity;
    var width = function () { var s = 0; cols.forEach(function (c) { s += c.span; }); return s; };
    var stacked = function () { var s = 0; cols.forEach(function (c) { s += c.members.length - 1; }); return s; };
    function consider() {
        if (!cols.length || (cols.length === 1 && cols[0].members.length > 1)) return;
        if (need >= 0 && taken.indexOf(need) < 0) return;
        var last = taken[taken.length - 1], sk = 0;
        skips.forEach(function (s) { if (s < last) sk++; });
        var used = width(), tries = [-1];
        if (used < 12 && cols.length > 1) {
            cols.forEach(function (c, k) {
                if (c.members.length > 1 && c.members.every(function (m) { return Math.min(12, m.span) * 2 >= c.span + 12 - used; })) tries.push(k);
            });
        }
        tries.forEach(function (w) {
            var groups = cols.map(function (c, k) {
                var span = c.span + (k === w ? 12 - used : 0);
                return { span: span, members: c.members.map(function (m) { return sized(m, span); }) };
            });
            var cost = evaluate(groups, g, ctx);
            if (cost == null) return;
            cost += COST.skip * sk;
            if (!skips.length && same(groups, rest, taken.length)) cost -= COST.stay;
            if (cost > best + MARGIN) return;
            best = Math.min(best, cost);
            found.push({ cost: cost, groups: groups, taken: taken.slice() });
        });
    }
    function dfs(j) {
        consider();
        if (j >= n) return;
        // 往下只会更贵（再叠、再跳）：已经比最好的贵出一截就不用试了
        if (COST.stack * stacked() + COST.skip * skips.length >= best + MARGIN) return;
        var e = rest[j], used = width(), last = cols[cols.length - 1], span = Math.min(12, e.span);
        if (used + span <= 12) {
            cols.push({ span: span, members: [e] });
            taken.push(j);
            dfs(j + 1);
            cols.pop();
            taken.pop();
        }
        if (last && last.members.length < DEEP && span < 12 && last.span < 12) {
            var w = Math.max(last.span, span);
            if (used - last.span + w <= 12 && span * 2 >= w && last.members.every(function (m) { return Math.min(12, m.span) * 2 >= w; })) {
                var old = last.span;
                last.span = w;
                last.members.push(e);
                taken.push(j);
                dfs(j + 1);
                last.members.pop();
                last.span = old;
                taken.pop();
            }
        }
        if (j > 0 && j !== need && skips.length < SKIPS) {
            skips.push(j);
            dfs(j + 1);
            skips.pop();
        }
    }
    dfs(0);
    if (!found.length) return null;
    found.sort(function (a, b) { return a.cost - b.cost; });
    found = found.filter(function (f) { return f.cost <= best + MARGIN; }).slice(0, shallow ? 1 : LOOK);
    if (shallow || hinted || found.length === 1) return found[0];
    var pick = null;
    found.forEach(function (f) {
        var left = rest.slice();
        cut(left, f.taken);
        var next = left.length ? bestRow(left, g, null, true) : null;
        var total = f.cost + (next ? next.cost * NEXT : 0);
        if (!pick || total < pick.total - 1e-6) pick = { total: total, f: f };
    });
    return pick.f;
}

function cut(rest, taken) {
    taken.slice().sort(function (a, b) { return b - a; }).forEach(function (i) { rest.splice(i, 1); });
}
// 按记着的排法原样拼出这一行（从 rest 开头到下一张「新的一行」之前）；拼不出来（放不下、对不齐、开头那张不是一行的开头）返回 null
function hintedRow(rest, g) {
    var e0 = rest[0];
    if (!hinted || !e0 || e0.hint !== '/') return null;
    var cols = [], k = 0;
    for (; k < rest.length && k < 12; k++) {
        var e = rest[k], span = Math.min(12, e.span);
        if (k > 0 && e.hint === '/') break;
        if (k > 0 && e.hint !== '+' && e.hint !== '~') return null;
        if (k > 0 && e.hint === '+') {
            var last = cols[cols.length - 1], w = Math.max(last.span, span);
            if (last.members.length >= DEEP || span >= 12 || last.span >= 12 || span * 2 < w || !last.members.every(function (m) { return Math.min(12, m.span) * 2 >= w; })) return null;
            last.span = w;
            last.members.push(e);
        } else {
            cols.push({ span: span, members: [e] });
        }
    }
    var used = 0;
    cols.forEach(function (c) { used += c.span; });
    if (used > 12 || (cols.length === 1 && cols[0].members.length > 1)) return null;
    var tries = [-1], best = null;
    if (used < 12 && cols.length > 1) {
        cols.forEach(function (c, i) {
            if (c.members.length > 1 && c.members.every(function (m) { return Math.min(12, m.span) * 2 >= c.span + 12 - used; })) tries.push(i);
        });
    }
    tries.forEach(function (w) {
        var groups = cols.map(function (c, i) {
            var span = c.span + (i === w ? 12 - used : 0);
            return { span: span, members: c.members.map(function (m) { return sized(m, span); }) };
        });
        var cost = evaluate(groups, g, null);
        if (cost != null && (!best || cost < best.cost)) best = { cost: cost, groups: groups };
    });
    if (!best) return null;
    var taken = [];
    for (var i = 0; i < k; i++) taken.push(i);
    best.taken = taken;
    return best;
}
// dirty：这些卡片打头的行（刚改过的）重新挑排法，别的行按记着的排法原样拼
function flow(rows, rest, g, one, dirty) {
    while (rest.length) {
        if (one) {
            rows.push({ groups: [{ span: 12, members: [sized(rest.shift(), 12)] }] });
            continue;
        }
        var best = dirty && dirty.has(rest[0].el) ? null : hintedRow(rest, g);
        if (!best) best = bestRow(rest, g, null, false, dirty);
        rows.push({ groups: best.groups });
        cut(rest, best.taken);
    }
    return rows;
}

function hints(list) {
    hinted = list.some(function (e) { return !!e.hint; });
}
/** list 按顺序排成一行一行：[{ groups: [{ span, members, hs, x, w }], H, y, limit }]；one 为 true 时一张一行；
 *  fit(e, span) 可选，返回这张卡片按 span 列宽时的 { min, max, want }；dirty 可选，这些卡片打头的行重新挑排法 */
export function packRows(list, g, one, fit, dirty) {
    fitAt = fit || null;
    hints(list);
    var rows = flow([], list.slice(), g, one, dirty);
    rows.forEach(function (r) { settle(r, g); });
    fitAt = null;
    return rows;
}

/** 正在拉高度：prev 是开始拉时的排法，list 里 pin 的那张是正在拉的。它上面的行原样不动；它这一行从同一张开始重新挑排法
 *  （它留在原来的位置，旁边的卡片可以叠起来给它让高度，也可以散开），下面的行接着排 */
export function packHold(prev, list, g, one, fit) {
    var pin = null;
    list.forEach(function (e) { if (e.pin) pin = e; });
    var at = -1, pos = null;
    if (pin && !one) {
        prev.forEach(function (r, ri) {
            var x = 0;
            r.groups.forEach(function (c) {
                c.members.forEach(function (m, i) { if (m.el === pin.el) { at = ri; pos = { x: x, i: i }; } });
                x += c.span;
            });
        });
    }
    if (at < 0) return packRows(list, g, one, fit);
    fitAt = fit || null;
    hints(list);
    var by = new Map(), done = new Set(), rows = [];
    list.forEach(function (e) { by.set(e.el, e); });
    for (var i = 0; i < at; i++) {
        rows.push({ groups: prev[i].groups.map(function (c) {
            return { span: c.span, members: c.members.map(function (m) { done.add(m.el); return sized(by.get(m.el) || m, c.span); }) };
        }) });
    }
    var rest = list.filter(function (e) { return !done.has(e.el); });
    var best = bestRow(rest, g, { need: pin, pos: pos }) || bestRow(rest, g, null);
    rows.push({ groups: best.groups });
    cut(rest, best.taken);
    flow(rows, rest, g, false);
    rows.forEach(function (r) { settle(r, g); });
    fitAt = null;
    return rows;
}

/** 这一行多高：想要的高度，限制在每一列都放得下的范围里；每一列按各自的范围补齐到这个高度 */
export function settle(row, g) {
    var b = band(row.groups, g);
    row.H = Math.round(Math.max(b.lo, Math.min(b.hi, target(row.groups, g))));
    row.limit = b;
    row.groups.forEach(function (c) { c.hs = spread(c, row.H - g * (c.members.length - 1)); });
}

// 把 total 分给一列里的几张：从各自想要的高度出发，先在没拉过的卡片里按比例加减，不够再动拉过的，最后动正在拉的那张；
// 每张不出自己的范围，加起来正好是 total（整数）
function spread(c, total) {
    var ms = c.members;
    var lo = ms.map(function (m) { return low(m, c); }), hi = ms.map(function (m) { return high(m, c); });
    var hs = ms.map(function (m, i) { return clamp(wanted(m, c), lo[i], hi[i]); });
    for (var t = 0; t <= 2; t++) {
        for (var k = 0; k < 6; k++) {
            var cur = 0;
            hs.forEach(function (h) { cur += h; });
            var diff = total - cur;
            if (Math.abs(diff) < 0.5) break;
            var open = [], w = 0;
            hs.forEach(function (h, i) {
                if (tier(ms[i], c) === t && (diff > 0 ? h < hi[i] - 0.01 : h > lo[i] + 0.01)) { open.push(i); w += Math.max(1, h); }
            });
            if (!open.length) break;
            open.forEach(function (i) { hs[i] = clamp(hs[i] + diff * Math.max(1, hs[i]) / w, lo[i], hi[i]); });
        }
    }
    // 四舍五入差的几像素给能动的最后一张；真的补不齐（对不齐的行）就留着，不把卡片拉过它的上限
    var out = hs.map(function (h) { return Math.round(h); }), acc = 0;
    out.forEach(function (h) { acc += h; });
    var rem = Math.round(total) - acc;
    if (out.length && Math.abs(rem) <= out.length) {
        for (var j = out.length - 1; j >= 0; j--) {
            if (out[j] + rem >= lo[j] - 0.5 && out[j] + rem <= hi[j] + 0.5) { out[j] += rem; rem = 0; break; }
        }
        if (rem) out[out.length - 1] += rem;
    }
    return out;
}

/** 算出每张卡片的位置：{ rects: Map(key → { x, y, w, h }), height } */
export function place(rows, cw, g) {
    var rects = new Map(), y = 0, step = cw + g;
    rows.forEach(function (row) {
        var col = 0;
        row.y = y;
        row.groups.forEach(function (c) {
            var x = Math.round(col * step), w = Math.round((col + c.span) * step - g) - x, at = y;
            c.x = x;
            c.w = w;
            c.members.forEach(function (m, i) {
                rects.set(m.el, { x: x, y: at, w: w, h: c.hs[i] });
                at += c.hs[i] + g;
            });
            col += c.span;
        });
        y += row.H + g;
    });
    return { rects: rects, height: Math.max(0, y - g) };
}
