// 服务端渲染（只给测试用）：把页面数据渲染成完整的 HTML（和浏览器里渲染好以后的文档一样，包括标题、图标和主题样式），
// 测试可以照常用文字和标签检查页面
import { createSSRApp, h } from 'vue';
import { renderToString } from 'vue/server-renderer';
import $h, { setContext } from './runtime/h.js';
import { revive } from './runtime/java.js';
import { views as pub } from './views/pub.js';
import { views as app } from './views/app.js';

const FAVICON = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Crect x='1' y='1' width='22' height='22' rx='7' fill='%230f0f11'/%3E"
    + "%3Cpath d='M13.35 7.48A5.2 5.2 0 1 0 17.18 12.95' fill='none' stroke='%23fff' stroke-width='2.2' stroke-linecap='round'/%3E%3Ccircle cx='16.6' cy='9.3' r='1.6' fill='%23fff'/%3E%3C/svg%3E";
const SVG_EMPTY = /<(use|path|circle|rect|line|polyline|polygon|ellipse|stop)\b([^>]*)><\/\1>/g;
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

export async function render(json) {
    const data = JSON.parse(json, revive);
    const entry = pub[data.view] || app[data.view];
    if (!entry) return null;
    setContext(data.ctx);
    const vapp = createSSRApp({ render: () => h(entry.component, { m: data.model, c: data.ctx }) });
    vapp.config.globalProperties.$h = $h;
    // 去掉 Vue 的占位注释；空的 class / style（绑定的值是 null，浏览器里不会有这个属性）；SVG 空元素写成自闭合，和组件源码一致
    const body = (await renderToString(vapp)).replace(/<!--[\s\S]*?-->/g, '').replace(/ (class|style)=""/g, '').replace(SVG_EMPTY, '<$1$2/>');
    const title = entry.meta.title(data.model, data.ctx, $h);
    const css = entry.meta.theme ? entry.meta.theme.css(data.model, data.ctx, $h) : null;
    const theme = css == null ? '' : '<style' + (entry.meta.theme.attr ? ' data-theme' : '') + '>' + css + '</style>';
    return '<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><title>' + esc(title) + '</title><link rel="icon" href="' + FAVICON + '">'
        + theme + '</head><body>' + body + '</body></html>';
}
