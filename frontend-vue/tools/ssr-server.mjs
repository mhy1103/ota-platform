// 测试用的渲染服务：POST 页面数据（JSON），返回渲染好的 HTML。先 npm run build:ssr
import { createServer } from 'node:http';
import { render } from '../dist-ssr/ssr.js';

const port = Number(process.argv[2] || 18890);
createServer((req, res) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', async () => {
        try {
            const html = await render(Buffer.concat(chunks).toString('utf8'));
            res.writeHead(html == null ? 404 : 200, { 'Content-Type': 'text/html; charset=utf-8' });
            res.end(html == null ? '没有这个页面' : html);
        } catch (e) {
            res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
            res.end(String(e && e.stack || e));
        }
    });
}).listen(port, '127.0.0.1', () => console.log('渲染服务 http://127.0.0.1:' + port));
