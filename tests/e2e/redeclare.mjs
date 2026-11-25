// 找同一个函数里重复声明的 var（后台页面脚本里容易误用同名变量）：node redeclare.mjs <js 文件…>
import { parse } from '/Users/chuzu/shangjia/ota-platform/frontend-vue/node_modules/@babel/parser/lib/index.js';
import fs from 'fs';
import path from 'path';
const files = process.argv.slice(2);
const FN = new Set(['FunctionDeclaration', 'FunctionExpression', 'ArrowFunctionExpression', 'ObjectMethod', 'ClassMethod']);
function scan(fnNode, file) {
  const seen = new Map();
  (function walk(n, top) {
    if (!n || typeof n.type !== 'string') return;
    if (n !== fnNode && FN.has(n.type)) { scan(n, file); return; }
    if (n.type === 'VariableDeclaration' && n.kind === 'var') {
      for (const d of n.declarations) if (d.id.type === 'Identifier') {
        const list = seen.get(d.id.name) || [];
        list.push(d.loc.start.line);
        seen.set(d.id.name, list);
      }
    }
    if (n.type === 'FunctionDeclaration' && n !== fnNode && n.id) { /* handled by FN branch */ }
    for (const k of Object.keys(n)) {
      if (k === 'loc' || k === 'start' || k === 'end') continue;
      const v = n[k];
      if (Array.isArray(v)) v.forEach(c => walk(c, false)); else if (v && typeof v.type === 'string') walk(v, false);
    }
  })(fnNode.body, true);
  // function declarations directly in this scope count too
  for (const [name, lines] of seen) if (lines.length > 1) console.log(`${path.basename(file)}: var ${name} 重复声明于第 ${lines.join(', ')} 行`);
}
for (const f of files) {
  const ast = parse(fs.readFileSync(f, 'utf8'), { sourceType: 'module', errorRecovery: true });
  (function find(n) {
    if (!n || typeof n.type !== 'string') return;
    if (FN.has(n.type)) { scan(n, f); return; }
    for (const k of Object.keys(n)) { const v = n[k]; if (Array.isArray(v)) v.forEach(find); else if (v && typeof v.type === 'string') find(v); }
  })(ast.program);
}
