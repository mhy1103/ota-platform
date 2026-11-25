// 标签之间只有空白的文字节点（换行和缩进），Vue 编译时会删掉首尾的、把中间的压成一个空格；
// 行内元素之间少了空格，页面就会变样。编译前把这些空白写成插值，原样保留
export function whitespace() {
    return {
        name: 'ota-whitespace',
        enforce: 'pre',
        transform(code, id) {
            if (!id.endsWith('.vue')) return null;
            const open = code.indexOf('<template>');
            if (open < 0) return null;
            const scriptAt = code.search(/^<script/m);
            const close = code.lastIndexOf('</template>', scriptAt < 0 ? code.length : scriptAt);
            if (close < 0) return null;
            const start = open + '<template>'.length;
            const inner = code.slice(start, close).replace(/>(\s+)</g, (m, s) => ">{{'" + JSON.stringify(s).slice(1, -1) + "'}}<");
            return { code: code.slice(0, start) + inner + code.slice(close), map: null };
        }
    };
}
