<template><div class="pager" v-if="$h.t($h.gt($h.p(P_p, 'totalPages'), 1))">
    <span>{{ $h.s($h.cat('共 ', $h.numbers.formatInteger($h.p(P_p, 'totalElements'), 1, 'COMMA'), ' 条 · 第 ', $h.add($h.p(P_p, 'number'), 1), ' / ', $h.p(P_p, 'totalPages'), ' 页')) }}</span>
    <nav class="pages" aria-label="分页">
        <a :class="['pg', $h.av(($h.t($h.p(P_p, 'first')) ? 'disabled' : null))]" :href.attr="$h.ak($h.ui.page(P_base, $h.p(P_p, 'number')))" aria-label="上一页" title="上一页"><svg class="i i-sm"><use href="#i-left"></use></svg></a>
        <template v-for="[L_n, L_nStat] in $h.each($h.ui.pages($h.add($h.p(P_p, 'number'), 1), $h.p(P_p, 'totalPages')))">
            <span class="pg gap" v-if="$h.t($h.eq(L_n, 0))">…</span>
            <a :class="['pg', $h.av(($h.t($h.eq(L_n, $h.add($h.p(P_p, 'number'), 1))) ? 'on' : null))]" :href.attr="$h.ak($h.ui.page(P_base, L_n))" v-if="$h.t($h.gt(L_n, 0))">{{ $h.s(L_n) }}</a>
        </template>
        <a :class="['pg', $h.av(($h.t($h.p(P_p, 'last')) ? 'disabled' : null))]" :href.attr="$h.ak($h.ui.page(P_base, $h.add($h.p(P_p, 'number'), 2)))" aria-label="下一页" title="下一页"><svg class="i i-sm"><use href="#i-right"></use></svg></a>
    </nav>
</div></template>

<script setup>
defineProps({ m: Object, c: Object, P_p: null, P_base: null });
</script>
