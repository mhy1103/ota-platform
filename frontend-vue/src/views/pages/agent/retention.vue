<template>
<FAdminLayoutAgentbar :m="m" :c="c" :P_active="'retention'"/>
<template v-for="L_path in [$h.add(m.base, '/retention')]"><main class="content ap ap-ret">

    <div class="page-head" :data-live-refresh.attr="$h.av(($h.t(($h.t(m.retStarted) &amp;&amp; $h.t($h.eq(m.cutoff, null)))) ? 'on' : null))" :data-live.attr="$h.av(($h.t(($h.t(m.retStarted) &amp;&amp; $h.t($h.eq(m.cutoff, null)))) ? '30' : null))">
        <div class="page-title"><div>
            <h1>留存</h1>
            <p>{{ $h.s(($h.t(m.retStarted) ? $h.cat('每天新激活的设备，之后第 1 到 7 天还打开过的占多少；从 ', m.retSince, ' 开始统计') : '每天新激活的设备，之后第 1 到 7 天还打开过的占多少')) }}</p>
        </div></div>
        <div class="page-actions range-pick" v-if="$h.t(m.retStarted)">
            <div class="seg seg-sm"><template v-for="[L_o, L_oStat] in $h.each(m.retRangeOpts)"><a :class="$h.av(($h.t($h.eq($h.z(L_o, 'key'), m.range)) ? 'on' : null))" :href.attr="$h.ak($h.ui.url(L_path, 'range', $h.z(L_o, 'key')))">{{ $h.s($h.z(L_o, 'label')) }}</a></template><button type="button" :class="$h.av(($h.t($h.eq(m.range, 'custom')) ? 'on' : null))" :title.attr="$h.av(($h.t($h.eq(m.range, 'custom')) ? $h.cat('自定义：', m.retRangeLabel) : '自定义时间段'))" aria-label="自定义时间段" aria-haspopup="dialog" data-range-custom :data-from.attr="$h.ak(m.rangeFrom)" :data-to.attr="$h.ak(m.rangeTo)" :data-max.attr="$h.ak(m.rangeMax)" :data-action.attr="$h.ak(L_path)" :aria-expanded.attr="$h.av(($h.t($h.eq(m.range, 'custom')) ? 'true' : 'false'))"><svg class="i i-sm"><use href="#i-calendar"></use></svg><span class="rc-tx">{{ $h.s(($h.t($h.eq(m.range, 'custom')) ? m.retRangeLabel : '自定义')) }}</span></button></div>
        </div>
    </div>

    <div class="note callout" v-if="!$h.t(m.retStarted)">
        <svg class="i"><use href="#i-info"></use></svg>
        <span>还没有留存数据。设备打开 App 后才开始统计，攒几天就有了。</span>
    </div>

    <section class="kpis k2" v-if="$h.t(m.retStarted)">
        <div class="kpi lead">
            <div class="kpi-k">7 日留存<small>{{ $h.s(m.retRangeLabel) }}</small></div>
            <div :class="['kpi-v', $h.av(($h.t($h.eq(m.retD7, null)) ? 'none' : null))]">{{ $h.s(($h.t(!$h.eq(m.retD7, null)) ? m.retD7 : '—')) }}<small v-if="$h.t(!$h.eq(m.retD7, null))">%</small></div>
            <div class="kpi-f">{{ $h.s(($h.t(!$h.eq(m.retD7, null)) ? $h.cat(m.retRangeLabel, '新激活、满 7 天的设备，第 7 天还打开的占比') : $h.cat(m.retRangeLabel, '里激活满 8 天以后才有'))) }}</div>
        </div>
        <div class="kpi">
            <div class="kpi-k">次日留存<small>{{ $h.s(m.retRangeLabel) }}</small></div>
            <div :class="['kpi-v', $h.av(($h.t($h.eq(m.retD1, null)) ? 'none' : null))]">{{ $h.s(($h.t(!$h.eq(m.retD1, null)) ? m.retD1 : '—')) }}<small v-if="$h.t(!$h.eq(m.retD1, null))">%</small></div>
            <div class="kpi-f">{{ $h.s($h.cat(m.retRangeLabel, '新激活的设备，第二天还打开的占比')) }}</div>
        </div>
    </section>

    <section class="card" v-if="$h.t(m.retStarted)">
        <div class="card-head"><h2>每天新激活的 7 日留存</h2><span class="muted small">{{ $h.s($h.cat(m.retRangeLabel, '，第 1 到 7 天还打开过的占当天新激活的多少；颜色越深留存越高')) }}</span></div>
        <div class="table-wrap">
            <table class="compact act-ret" data-no-cards>
                <thead><tr><th>激活日</th><template v-for="[L_g, L_gStat] in $h.each(m.retDays)"><th class="right">{{ $h.s(($h.t($h.eq(L_g, 1)) ? '次日' : $h.cat('第 ', L_g, ' 天'))) }}</th></template></tr></thead>
                <tbody><template v-for="[L_r, L_rStat] in $h.each(m.retRows)"><tr :class="$h.av(($h.t(L_rStat.first) ? 'sum' : null))">
                    <td class="nowrap">{{ $h.s($h.z(L_r, 'date')) }}</td>
                    <template v-for="[L_x, L_xStat] in $h.each($h.z(L_r, 'cells'))"><td :class="['num', 'right', 'act-cell', $h.av($h.z(L_x, 'state'))]" :style="$h.av(($h.t(!$h.eq($h.z(L_x, 'pct'), null)) ? $h.cat('--p:', $h.z(L_x, 'pct')) : null))" :title.attr="$h.av(($h.t($h.eq($h.z(L_x, 'state'), 'live')) ? '今天还没过完，数字会实时往上涨，不算进合计' : null))"><span>{{ $h.s(($h.t(!$h.eq($h.z(L_x, 'pct'), null)) ? $h.add($h.z(L_x, 'pct'), '%') : ($h.t($h.eq($h.z(L_x, 'state'), 'wait')) ? '…' : '—'))) }}</span></td></template>
                </tr></template></tbody>
            </table>
        </div>
    </section>

    <div class="note" v-if="$h.t(m.retStarted)">
        <svg class="i"><use href="#i-info"></use></svg>
        <span>{{ $h.s(($h.t($h.eq(m.cutoff, null)) ? '第一行「合计」是这段时间加起来的。虚线框的是今天的，还没过完、数字会实时往上涨，不算进合计和上面的留存率；「…」是还没到那一天；「—」是那天没有新激活或还没开始统计。只统计真实设备，不扣量。' : '第一行「合计」是这段时间加起来的。「…」是那一天还没到或数据还没更新到；「—」是那天没有新激活或还没开始统计。只统计真实设备，不扣量。')) }}</span>
    </div>

</main></template>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('留存'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
    css: ["app/ui.css"],
    js: ["app/ui.js"],
    theme: { attr: true, css: (m, c, $h) => m.themeCss }
};
</script>

<script setup>
import FAdminLayoutAgentbar from '../../frags/admin-layout--agentbar.vue';
import FAdminLayoutOverlays from '../../frags/admin-layout--overlays.vue';
defineProps({ m: Object, c: Object });
</script>
