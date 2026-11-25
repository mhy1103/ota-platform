<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'agents'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'日活与留存'"/>
    <main :class="['content', 'ax', 'act-page', $h.av($h.cat('act-', m.actKind))]">

        <a class="back-link" :href.attr="$h.ak(m.actBack)"><svg class="i i-sm"><use href="#i-left"></use></svg>{{ $h.s(m.actBackLabel) }}</a>
        <div class="page-head">
            <div class="page-title">
                <div>
                    <h1>日活与留存<span class="act-who">{{ $h.s(m.actTitle) }}</span><span class="act-scope" v-if="$h.t(!$h.eq(m.actScope, null))">{{ $h.s(m.actScope) }}<a :href.attr="$h.ak(m.actAll)" title="看这个代理的全部口令" aria-label="看这个代理的全部口令"><svg class="i i-sm"><use href="#i-x"></use></svg></a></span></h1>
                    <p>按激活过的设备算：App 启动时校验授权、启动成功确认激活，就算那天打开过。都是真实的数，不扣量{{ $h.s(($h.t(m.actStarted) ? $h.cat('；从 ', m.actSince, ' 开始统计') : '')) }}</p>
                </div>
            </div>
            <div class="page-actions">
                <a class="btn btn-ghost btn-sm" :href.attr="$h.ak(m.actRetHref)" title="每天新激活的设备第 1 到 7 天的留存，可选时间段看合计" v-if="$h.t(!$h.eq(m.actRetHref, null))"><svg class="i"><use href="#i-target"></use></svg>7 日留存</a>
                <div class="seg seg-sm"><template v-for="[L_o, L_oStat] in $h.each(m.actRangeOpts)"><a :class="$h.av(($h.t($h.z(L_o, 'on')) ? 'on' : null))" :href.attr="$h.ak($h.z(L_o, 'href'))">{{ $h.s($h.z(L_o, 'label')) }}</a></template></div>
            </div>
        </div>

        <div class="note act-note" v-if="!$h.t(m.actStarted)"><svg class="i"><use href="#i-info"></use></svg><span>还没有数据。新版上线以后，App 启动时校验授权就开始记：第二天能看到日活比前一天，第三天能看到次日留存。以前的日活没有记过，补不回来。</span></div>

        <section class="metrics">
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-activity"></use></svg></span>今日日活</div>
                <div class="metric-value">{{ $h.s($h.numbers.formatInteger(m.actToday, 1, 'COMMA')) }}</div>
                <div class="metric-foot">{{ $h.s($h.cat('今天新激活 ', $h.numbers.formatInteger(m.actFreshToday, 1, 'COMMA'), ' 台 · 今天还没过完')) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-calendar"></use></svg></span>昨日日活</div>
                <div class="metric-value"><span>{{ $h.s($h.numbers.formatInteger(m.actYesterday, 1, 'COMMA')) }}</span><span :class="['delta', $h.av($h.z(m.actDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.actDelta, null))" title="比前一天"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.actDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.actDelta, 'text')) }}</span></div>
                <div class="metric-foot">{{ $h.s($h.cat('前天 ', $h.numbers.formatInteger(m.actBefore, 1, 'COMMA'), ($h.t(!$h.eq(m.actRate, null)) ? $h.cat(' · 活跃率 ', m.actRate, '%') : ''))) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-history"></use></svg></span>次日留存</div>
                <div class="metric-value">{{ $h.s(($h.t(!$h.eq(m.actD1, null)) ? $h.add(m.actD1, '%') : '—')) }}</div>
                <div class="metric-foot">{{ $h.s(($h.t(!$h.eq(m.actD1, null)) ? $h.cat('近 7 天新激活 ', $h.numbers.formatInteger(m.actD1Size, 1, 'COMMA'), ' 台，第二天打开了 ', $h.numbers.formatInteger(m.actD1Back, 1, 'COMMA'), ' 台') : '统计满两天以后才有')) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-target"></use></svg></span>7 日留存</div>
                <div class="metric-value">{{ $h.s(($h.t(!$h.eq(m.actD7, null)) ? $h.add(m.actD7, '%') : '—')) }}</div>
                <div class="metric-foot">{{ $h.s(($h.t(!$h.eq(m.actD7, null)) ? $h.cat('激活满 7 天的 ', $h.numbers.formatInteger(m.actD7Size, 1, 'COMMA'), ' 台里，第 7 天打开了 ', $h.numbers.formatInteger(m.actD7Back, 1, 'COMMA'), ' 台') : '统计满 8 天以后才有')) }}</div>
            </div>
        </section>

        <section class="card section-gap">
            <div class="card-head">
                <h2>每天的日活</h2>
                <span class="act-legend"><i class="old"></i>之前激活的<i class="new"></i>当天新激活的</span>
                <span class="trend-sum">{{ $h.s(($h.t(!$h.eq(m.actAvg, null)) ? $h.cat('近 7 天日均 ', $h.numbers.formatInteger(m.actAvg, 1, 'COMMA'), ' · 累计激活 ', $h.numbers.formatInteger(m.actTotal, 1, 'COMMA'), ' 台') : $h.cat('累计激活 ', $h.numbers.formatInteger(m.actTotal, 1, 'COMMA'), ' 台'))) }}</span>
                <span :class="['delta', $h.av($h.z(m.actAvgDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.actAvgDelta, null))" title="比前 7 天的日均"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.actAvgDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.actAvgDelta, 'text')) }}</span>
            </div>
            <div :class="['chart', 'act-chart', $h.av(($h.t($h.gt($h.lists.size(m.actBars), 14)) ? 'range-30' : null))]">
                <template v-for="[L_b, L_bStat] in $h.each(m.actBars)"><div :class="['chart-col', $h.av(($h.t($h.z(L_b, 'last')) ? 'last' : null)), $h.av(($h.t($h.z(L_b, 'none')) ? 'none' : null))]" :title.attr="$h.av($h.z(L_b, 'tip'))">
                    <span class="chart-val">{{ $h.s(($h.t($h.gt($h.z(L_b, 'dau'), 0)) ? $h.numbers.formatInteger($h.z(L_b, 'dau'), 1, 'COMMA') : '')) }}</span>
                    <span class="chart-bar" :style="$h.av($h.cat('height:', $h.z(L_b, 'px'), 'px'))"><i :style="$h.av($h.cat('height:', $h.z(L_b, 'freshPx'), 'px'))" v-if="$h.t($h.gt($h.z(L_b, 'freshPx'), 0))"></i></span>
                </div></template>
            </div>
            <div class="chart-x"><template v-for="[L_b, L_bStat] in $h.each(m.actBars)"><span :class="$h.av(($h.t($h.z(L_b, 'last')) ? 'last' : null))">{{ $h.s($h.z(L_b, 'label')) }}</span></template></div>
        </section>

        <section class="card section-gap">
            <div class="card-head"><h2>新激活的留存</h2><span class="muted small">每天新激活的设备，之后第 N 天又打开了的占多少；虚线框是今天的，实时在涨，不算进合计</span></div>
            <div class="table-wrap">
                <table class="compact act-ret" data-no-cards>
                    <thead><tr><th>激活日</th><th class="right">新激活</th><template v-for="[L_g, L_gStat] in $h.each(m.actAges)"><th class="right">{{ $h.s(($h.t($h.eq(L_g, 1)) ? '次日' : $h.cat('第 ', L_g, ' 天'))) }}</th></template></tr></thead>
                    <tbody><template v-for="[L_r, L_rStat] in $h.each(m.actCohorts)"><tr :class="$h.av(($h.t(L_rStat.first) ? 'sum' : null))">
                        <td class="nowrap">{{ $h.s($h.z(L_r, 'date')) }}</td>
                        <td class="num right">{{ $h.s($h.numbers.formatInteger($h.z(L_r, 'size'), 1, 'COMMA')) }}</td>
                        <template v-for="[L_x, L_xStat] in $h.each($h.z(L_r, 'cells'))"><td :class="['num', 'right', 'act-cell', $h.av($h.z(L_x, 'state'))]" :style="$h.av(($h.t(!$h.eq($h.z(L_x, 'pct'), null)) ? $h.cat('--p:', $h.z(L_x, 'pct')) : null))" :title.attr="$h.av($h.z(L_x, 'tip'))"><span>{{ $h.s(($h.t(!$h.eq($h.z(L_x, 'pct'), null)) ? $h.add($h.z(L_x, 'pct'), '%') : ($h.t($h.eq($h.z(L_x, 'state'), 'wait')) ? '…' : '—'))) }}</span></td></template>
                    </tr></template></tbody>
                </table>
            </div>
        </section>

        <section class="card section-gap" v-if="$h.t(!$h.eq(m.actAgentRows, null))">
            <div class="card-head"><h2>按代理</h2><span class="muted small">点名字看这个代理的</span></div>
            <div class="empty compact" v-if="$h.t($h.lists.isEmpty(m.actAgentRows))"><b>还没有代理</b>新建代理、分了口令以后，这里按代理列出日活和留存</div>
            <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty(m.actAgentRows))">
                <table class="compact act-rows">
                    <thead><tr><th>代理</th><th class="right">累计激活</th><th class="right">今日日活</th><th class="right">昨日日活</th><th class="right">比前一天</th><th class="right" title="近 7 天新激活的设备第二天打开的比例">次日留存</th><th class="right" title="激活满 7 天的设备第 7 天打开的比例">7 日留存</th></tr></thead>
                    <tbody data-collapse="20" data-collapse-unit="个代理"><template v-for="[L_w, L_wStat] in $h.each(m.actAgentRows)"><tr>
                        <td class="cell-primary"><a class="link-strong" :href.attr="$h.ak($h.z(L_w, 'href'))">{{ $h.s($h.z(L_w, 'name')) }}</a><small class="muted mono act-note-inline">{{ $h.s($h.z(L_w, 'note')) }}</small><span class="mini-tag" v-if="$h.t($h.z(L_w, 'off'))">停用</span></td>
                        <td class="num right">{{ $h.s($h.numbers.formatInteger($h.z(L_w, 'total'), 1, 'COMMA')) }}</td>
                        <td class="num right">{{ $h.s($h.numbers.formatInteger($h.z(L_w, 'today'), 1, 'COMMA')) }}</td>
                        <td class="num right"><b>{{ $h.s($h.numbers.formatInteger($h.z(L_w, 'yesterday'), 1, 'COMMA')) }}</b></td>
                        <td class="num right"><span :class="['delta', $h.av($h.z($h.z(L_w, 'delta'), 'dir'))]" v-if="$h.t(!$h.eq($h.z(L_w, 'delta'), null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z($h.z(L_w, 'delta'), 'dir')))"></use></svg>{{ $h.s($h.z($h.z(L_w, 'delta'), 'text')) }}</span><span class="muted" v-if="$h.t($h.eq($h.z(L_w, 'delta'), null))">—</span></td>
                        <td class="num right">{{ $h.s(($h.t(!$h.eq($h.z(L_w, 'd1'), null)) ? $h.add($h.z(L_w, 'd1'), '%') : '—')) }}</td>
                        <td class="num right">{{ $h.s(($h.t(!$h.eq($h.z(L_w, 'd7'), null)) ? $h.add($h.z(L_w, 'd7'), '%') : '—')) }}</td>
                    </tr></template></tbody>
                </table>
            </div>
        </section>

        <section class="card section-gap" v-if="$h.t(!$h.eq(m.actSubRows, null))">
            <div class="card-head"><h2>按二级代理</h2><span class="muted small">分给二级代理的口令算在 TA 那一行</span></div>
            <div class="table-wrap">
                <table class="compact act-rows">
                    <thead><tr><th>二级代理</th><th class="right">累计激活</th><th class="right">今日日活</th><th class="right">昨日日活</th><th class="right">比前一天</th><th class="right" title="近 7 天新激活的设备第二天打开的比例">次日留存</th><th class="right" title="激活满 7 天的设备第 7 天打开的比例">7 日留存</th></tr></thead>
                    <tbody data-collapse="20" data-collapse-unit="个二级代理"><template v-for="[L_w, L_wStat] in $h.each(m.actSubRows)"><tr>
                        <td class="cell-primary"><a class="link-strong" :href.attr="$h.ak($h.z(L_w, 'href'))" v-if="$h.t(!$h.eq($h.z(L_w, 'href'), null))">{{ $h.s($h.z(L_w, 'name')) }}</a><b v-if="$h.t($h.eq($h.z(L_w, 'href'), null))">{{ $h.s($h.z(L_w, 'name')) }}</b><small class="muted act-note-inline">{{ $h.s($h.z(L_w, 'note')) }}</small><span class="mini-tag" v-if="$h.t($h.z(L_w, 'off'))">停用</span></td>
                        <td class="num right">{{ $h.s($h.numbers.formatInteger($h.z(L_w, 'total'), 1, 'COMMA')) }}</td>
                        <td class="num right">{{ $h.s($h.numbers.formatInteger($h.z(L_w, 'today'), 1, 'COMMA')) }}</td>
                        <td class="num right"><b>{{ $h.s($h.numbers.formatInteger($h.z(L_w, 'yesterday'), 1, 'COMMA')) }}</b></td>
                        <td class="num right"><span :class="['delta', $h.av($h.z($h.z(L_w, 'delta'), 'dir'))]" v-if="$h.t(!$h.eq($h.z(L_w, 'delta'), null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z($h.z(L_w, 'delta'), 'dir')))"></use></svg>{{ $h.s($h.z($h.z(L_w, 'delta'), 'text')) }}</span><span class="muted" v-if="$h.t($h.eq($h.z(L_w, 'delta'), null))">—</span></td>
                        <td class="num right">{{ $h.s(($h.t(!$h.eq($h.z(L_w, 'd1'), null)) ? $h.add($h.z(L_w, 'd1'), '%') : '—')) }}</td>
                        <td class="num right">{{ $h.s(($h.t(!$h.eq($h.z(L_w, 'd7'), null)) ? $h.add($h.z(L_w, 'd7'), '%') : '—')) }}</td>
                    </tr></template></tbody>
                </table>
            </div>
        </section>

        <section class="card section-gap" v-if="$h.t(!$h.eq(m.actCodeRows, null))">
            <div class="card-head"><h2>按口令</h2><span class="muted small">点口令单独看这个口令的</span></div>
            <div class="empty compact" v-if="$h.t($h.lists.isEmpty(m.actCodeRows))"><b>还没有口令</b>分了口令、有了激活以后，这里按口令列出日活和留存</div>
            <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty(m.actCodeRows))">
                <table class="compact act-rows">
                    <thead><tr><th>口令</th><th class="right">累计激活</th><th class="right">今日日活</th><th class="right">昨日日活</th><th class="right">比前一天</th><th class="right" title="近 7 天新激活的设备第二天打开的比例">次日留存</th><th class="right" title="激活满 7 天的设备第 7 天打开的比例">7 日留存</th></tr></thead>
                    <tbody data-collapse="20" data-collapse-unit="个口令"><template v-for="[L_w, L_wStat] in $h.each(m.actCodeRows)"><tr>
                        <td class="cell-primary"><a class="mono link-strong" :href.attr="$h.ak($h.z(L_w, 'href'))">{{ $h.s($h.z(L_w, 'name')) }}</a><small class="muted act-note-inline" v-if="!$h.t($h.strings.isEmpty($h.z(L_w, 'note')))">{{ $h.s($h.z(L_w, 'note')) }}</small><span class="mini-tag" v-if="$h.t($h.z(L_w, 'off'))">停用</span></td>
                        <td class="num right">{{ $h.s($h.numbers.formatInteger($h.z(L_w, 'total'), 1, 'COMMA')) }}</td>
                        <td class="num right">{{ $h.s($h.numbers.formatInteger($h.z(L_w, 'today'), 1, 'COMMA')) }}</td>
                        <td class="num right"><b>{{ $h.s($h.numbers.formatInteger($h.z(L_w, 'yesterday'), 1, 'COMMA')) }}</b></td>
                        <td class="num right"><span :class="['delta', $h.av($h.z($h.z(L_w, 'delta'), 'dir'))]" v-if="$h.t(!$h.eq($h.z(L_w, 'delta'), null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z($h.z(L_w, 'delta'), 'dir')))"></use></svg>{{ $h.s($h.z($h.z(L_w, 'delta'), 'text')) }}</span><span class="muted" v-if="$h.t($h.eq($h.z(L_w, 'delta'), null))">—</span></td>
                        <td class="num right">{{ $h.s(($h.t(!$h.eq($h.z(L_w, 'd1'), null)) ? $h.add($h.z(L_w, 'd1'), '%') : '—')) }}</td>
                        <td class="num right">{{ $h.s(($h.t(!$h.eq($h.z(L_w, 'd7'), null)) ? $h.add($h.z(L_w, 'd7'), '%') : '—')) }}</td>
                    </tr></template></tbody>
                </table>
            </div>
        </section>
    </main>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add('日活与留存 · ', $h.add($h.elvis(m.actTitle, '代理'), $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
    css: ["app/ui.css"],
    js: ["app/ui.js"],
    theme: { attr: true, css: (m, c, $h) => m.themeCss }
};
</script>

<script setup>
import FAdminLayoutOverlays from '../../frags/admin-layout--overlays.vue';
import FAdminLayoutSidebar from '../../frags/admin-layout--sidebar.vue';
import FAdminLayoutTopbar from '../../frags/admin-layout--topbar.vue';
defineProps({ m: Object, c: Object });
</script>
