// 生成 src/main/resources/vue-fields.json：每个页面用到的模型字段（m.xxx）和对象属性（$h.p / $h.z 的属性名），
// 服务端只把这些字段发给浏览器，没用到的（包括密码、密钥这类）不会出现在页面数据里。打包时自动运行
import { readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const VIEWS = join(ROOT, 'src/views');
const OUT = resolve(ROOT, '../src/main/resources/vue-fields.json');

// 这些工具方法在 runtime/h.js 里读对象的属性，字段也要发
const HELPER_PROPS = {
    '$h.ui.codeStatus(': ['enabled', 'expiresAt', 'maxUses', 'usedCount', 'seenCount'],
    '$h.ui.percent(': ['maxUses', 'usedCount', 'seenCount'],
    '$h.agentPortal.confirmedAsOf(': ['confirmed', 'confirmedAt']
};

function walk(dir) {
    return readdirSync(dir).flatMap((n) => {
        const p = join(dir, n);
        return statSync(p).isDirectory() ? walk(p) : p.endsWith('.vue') ? [p] : [];
    });
}

/** 从 i（左括号后面）找到配对的右括号，返回参数列表（只按最外层逗号分开） */
function args(text, i) {
    const out = [];
    let depth = 0, quote = null, from = i;
    for (let k = i; k < text.length; k++) {
        const c = text[k];
        if (quote) {
            if (c === '\\') k++;
            else if (c === quote) quote = null;
        } else if (c === "'" || c === '"' || c === '`') quote = c;
        else if (c === '(' || c === '[' || c === '{') depth++;
        else if (c === ')' || c === ']' || c === '}') {
            if (depth === 0) {
                out.push(text.slice(from, k).trim());
                return out;
            }
            depth--;
        } else if (c === ',' && depth === 0) {
            out.push(text.slice(from, k).trim());
            from = k + 1;
        }
    }
    return out;
}

function scan(file) {
    const text = readFileSync(file, 'utf8');
    const model = new Set(), props = new Set(), imports = [];
    for (const m of text.matchAll(/(?<![\w$.])m\.([A-Za-z_$][\w$]*)/g)) model.add(m[1]);
    for (const m of text.matchAll(/(?<![\w$.])m\[\s*'([^']+)'\s*\]/g)) model.add(m[1]);
    for (const call of ['$h.p(', '$h.z(']) {
        for (let i = text.indexOf(call); i >= 0; i = text.indexOf(call, i + 1)) {
            const a = args(text, i + call.length);
            const last = a[a.length - 1] || '';
            const lit = /^'([^']*)'$/.exec(last);
            if (lit) props.add(lit[1]);
        }
    }
    for (const [call, names] of Object.entries(HELPER_PROPS)) if (text.includes(call)) names.forEach((n) => props.add(n));
    for (const m of text.matchAll(/^import \w+ from '(\.[^']+\.vue)';$/gm)) imports.push(resolve(dirname(file), m[1]));
    return { model, props, imports };
}

const cache = new Map();
function closure(file, seen = new Set()) {
    if (!cache.has(file)) cache.set(file, scan(file));
    const s = cache.get(file);
    const model = new Set(s.model), props = new Set(s.props);
    for (const f of s.imports) {
        if (seen.has(f)) continue;
        seen.add(f);
        const c = closure(f, seen);
        c.model.forEach((x) => model.add(x));
        c.props.forEach((x) => props.add(x));
    }
    return { model, props };
}

const views = {};
for (const file of walk(join(VIEWS, 'pages')).sort()) {
    const name = relative(join(VIEWS, 'pages'), file).replace(/\\/g, '/').replace(/\.vue$/, '');
    const c = closure(file);
    views[name] = { model: [...c.model].sort(), props: [...c.props].sort() };
}
writeFileSync(OUT, JSON.stringify({ views }, null, 1) + '\n');
console.log('页面字段清单：' + Object.keys(views).length + ' 个页面 → ' + relative(resolve(ROOT, '..'), OUT));
