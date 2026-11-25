<template>
<main>
    <div class="icon">
        <svg viewBox="0 0 24 24" v-if="$h.eq(m.status, 404)"><circle cx="11" cy="11" r="7"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line><line x1="8.5" y1="11" x2="13.5" y2="11"></line></svg>
        <svg viewBox="0 0 24 24" v-if="$h.eq(m.status, 403) &amp;&amp; !($h.eq(m.status, 404))"><rect x="4" y="11" width="16" height="10" rx="2"></rect><path d="M8 11V7a4 4 0 0 1 8 0v4"></path></svg>
        <svg viewBox="0 0 24 24" v-if="$h.eq(m.status, 413) &amp;&amp; !($h.eq(m.status, 404)) &amp;&amp; !($h.eq(m.status, 403))"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
        <svg viewBox="0 0 24 24" v-if="!($h.eq(m.status, 404)) &amp;&amp; !($h.eq(m.status, 403)) &amp;&amp; !($h.eq(m.status, 413))"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
    </div>
    <div class="code">{{ $h.s(m.status) }}</div>
    <h1>{{ $h.s(($h.t($h.eq(m.status, 404)) ? '页面不存在' : ($h.t($h.eq(m.status, 403)) ? '没有访问权限' : ($h.t($h.eq(m.status, 413)) ? '上传的文件太大' : '服务器出错了')))) }}</h1>
    <p>{{ $h.s(($h.t($h.eq(m.status, 404)) ? '地址可能输错了，或者页面已被移除。' : ($h.t($h.eq(m.status, 403)) ? '请重新登录后再试。' : ($h.t($h.eq(m.status, 413)) ? '文件超过了服务器的上传限制，压缩后再试，或者调大上限。' : '请稍后重试，如果持续出现请查看服务器日志。')))) }}</p>
    <div><span class="path" v-if="$h.t(m.path)">{{ $h.s(m.path) }}</span></div>
    <div class="actions">
        <a class="secondary" href="/" data-back>返回上一页</a>
        <a href="/">回到首页</a>
    </div>
</main>

</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(m.status, $h.add(' · ', $h.elvis(m.brandName, '管理后台'))),
    css: ["pub/error.css"],
    js: ["pub/error.js"],
    theme: { attr: false, css: (m, c, $h) => m.themeCss }
};
</script>

<script setup>
defineProps({ m: Object, c: Object });
</script>
