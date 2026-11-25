// 列出页面、交互脚本和服务端提示里还没翻译的中文：node tools/i18n-extract.mjs
// 输出「原文」一行一条，翻好后加进 src/i18n/en.js（键是原文，值是英文）
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const here = new URL('..', import.meta.url).pathname;
const CJK = /[\u4e00-\u9fff]/;
const dict = (await import(join(here, 'src/i18n/en.js'))).default;
const found = new Set();

function walk(dir, exts, out = []) {
    for (const name of readdirSync(dir)) {
        const p = join(dir, name);
        if (statSync(p).isDirectory()) walk(p, exts, out);
        else if (exts.some((e) => p.endsWith(e))) out.push(p);
    }
    return out;
}
function add(s) {
    const t = s.trim();
    if (t && CJK.test(t) && t.length <= 400 && !t.includes('$h.') && !t.includes('<')) found.add(t);
}

for (const f of walk(join(here, 'src/views'), ['.vue'])) {
    const tpl = readFileSync(f, 'utf8').split('<script')[0];
    for (const m of tpl.matchAll(/>([^<>]+)</g)) for (const part of m[1].replace(/\{\{[\s\S]*?\}\}/g, '\0').split('\0')) add(part);
    for (const m of tpl.matchAll(/'([^'\\\n]*[\u4e00-\u9fff][^'\\\n]*)'/g)) add(m[1]);
    for (const m of tpl.matchAll(/\s(?:placeholder|title|aria-label|data-confirm(?:-body|-ok)?|data-tip)="([^"]*[\u4e00-\u9fff][^"]*)"/g)) add(m[1]);
}
for (const f of [...walk(join(here, 'src/behaviors'), ['.js']), join(here, 'src/main.js'), join(here, 'src/runtime/h.js')]) {
    const src = readFileSync(f, 'utf8').replace(/^\s*\/\/[^\n]*/gm, '');
    for (const m of src.matchAll(/'([^'\\\n]*[\u4e00-\u9fff][^'\\\n]*)'/g)) add(m[1]);
}
for (const f of walk(join(here, '../src/main/java'), ['.java'])) {
    const src = readFileSync(f, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
    for (const line of src.split('\n')) {
        if (/log\.|System\.(out|err)/.test(line)) continue;
        if (!/addFlashAttribute|ApiException\.|result\(|new Todo\(|Map\.entry|error = |msg/.test(line)) continue;
        for (const m of line.matchAll(/"([^"\\\n]*[\u4e00-\u9fff][^"\\\n]*)"/g)) add(m[1]);
    }
}
const missing = [...found].filter((s) => dict[s] === undefined).sort((a, b) => a.length - b.length);
for (const s of missing) console.log(s);
console.error(`${found.size} 条中文，${missing.length} 条还没翻译`);
