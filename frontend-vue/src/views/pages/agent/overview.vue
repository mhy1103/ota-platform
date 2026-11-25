<template>
<FAdminLayoutAgentbar :m="m" :c="c" :P_active="'overview'"/>
<main class="content ap ap-ov">

    <div :class="['notice-banner', $h.av(($h.t($h.p(m.noticeBanner, 'important')) ? 'important' : null))]" data-notice-banner :data-id.attr="$h.av($h.p(m.noticeBanner, 'id'))" v-if="$h.t(!$h.eq(m.noticeBanner, null))">
        <svg class="i nb-icon"><use href="#i-megaphone"></use></svg>
        <div class="nb-text"><b>{{ $h.s($h.p(m.noticeBanner, 'title')) }}</b><span>{{ $h.s($h.p(m.noticeBanner, 'body')) }}</span></div>
        <a class="btn btn-secondary btn-sm" :href.attr="$h.ak($h.add(m.base, '/notices'))">{{ $h.s(($h.t($h.gt(m.noticeUnread, 1)) ? $h.cat('查看全部 ', m.noticeUnread, ' 条') : '查看')) }}</a>
        <button class="icon-btn" type="button" data-notice-dismiss title="知道了" aria-label="知道了" v-if="!$h.t(m.preview)"><svg class="i"><use href="#i-x"></use></svg></button>
    </div>

    <div class="page-head">
        <div class="page-title"><div>
            <h1>数据概览<span class="live-tag" data-live="10" title="页面开着时每 10 秒自动刷新" v-if="$h.t($h.eq(m.cutoff, null))"><i></i>实时</span></h1>
            <p>{{ $h.s(($h.t($h.eq(m.cutoff, null)) ? '以下数据实时更新' : ($h.t($h.eq(m.mode, 'DAILY')) ? $h.add('以下数据每日 0 点更新，当前统计到 ', $h.ui.dt(m.cutoff)) : $h.add('以下数据更新到 ', $h.ui.dt(m.cutoff))))) }}</p>
        </div></div>
    </div>

    <section class="kpis k5">
        <div class="kpi lead">
            <div class="kpi-k"><span class="pulse" v-if="$h.t($h.eq(m.cutoff, null))"></span>{{ $h.s($h.add(m.refLabel, '新增')) }}</div>
            <div class="kpi-v">{{ $h.s($h.add('+', $h.numbers.formatInteger(m.refCount, 1, 'COMMA'))) }}</div>
            <div class="kpi-f"><span :class="['chg', $h.av($h.z(m.refDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.refDelta, null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.refDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.refDelta, 'text')) }}</span><span>{{ $h.s(m.refVsLabel) }}</span></div>
        </div>
        <div class="kpi">
            <div class="kpi-k">{{ $h.s($h.add(m.monthLabel, '累计')) }}</div>
            <div class="kpi-v">{{ $h.s($h.numbers.formatInteger(m.monthCount, 1, 'COMMA')) }}</div>
            <div class="kpi-f"><span :class="['chg', $h.av($h.z(m.monthDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.monthDelta, null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.monthDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.monthDelta, 'text')) }}</span><span>比上月同期</span></div>
        </div>
        <div class="kpi">
            <div class="kpi-k">近 7 天</div>
            <div class="kpi-v">{{ $h.s($h.numbers.formatInteger(m.weekCount, 1, 'COMMA')) }}</div>
            <div class="kpi-f"><span :class="['chg', $h.av($h.z(m.weekDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.weekDelta, null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.weekDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.weekDelta, 'text')) }}</span><span>{{ $h.s($h.cat('比前 7 天 · 日均 ', $h.numbers.formatDecimal($h.div(m.weekCount, $h.d('7.0')), 1, 1))) }}</span></div>
        </div>
        <a class="kpi" :href.attr="$h.ak($h.add(m.base, '/retention'))" title="每天新激活的设备，第 1 到 7 天的留存">
            <div class="kpi-k">7 日留存<small>近 30 天</small><svg class="i"><use href="#i-arrow"></use></svg></div>
            <div :class="['kpi-v', $h.av(($h.t($h.eq(m.retD7, null)) ? 'none' : null))]">{{ $h.s(($h.t(!$h.eq(m.retD7, null)) ? m.retD7 : '—')) }}<small v-if="$h.t(!$h.eq(m.retD7, null))">%</small></div>
            <div class="kpi-f">{{ $h.s(($h.t(!$h.eq(m.retD7, null)) ? '第 7 天还打开 App 的占比' : ($h.t(m.retStarted) ? '激活满 8 天以后才有' : '设备打开 App 以后开始统计'))) }}</div>
        </a>
        <div class="kpi">
            <div class="kpi-k">累计有效激活</div>
            <div class="kpi-v">{{ $h.s($h.numbers.formatInteger(m.total, 1, 'COMMA')) }}</div>
            <div class="kpi-f">{{ $h.s($h.cat('名下口令 ', $h.lists.size(m.codes), ' 个 · 可用 ', m.usableCount, ' 个')) }}</div>
        </div>
    </section>

    <section class="card trend-card">
        <div class="card-head">
            <h2>有效激活趋势</h2>
            <div class="range-pick tail">
                <div class="seg seg-sm"><template v-for="[L_o, L_oStat] in $h.each(m.rangeOpts)"><a :class="$h.av(($h.t($h.eq($h.p(L_o, 'key'), m.range)) ? 'on' : null))" :href.attr="$h.ak($h.link(m.base, [['range', $h.p(L_o, 'key')]]))">{{ $h.s($h.p(L_o, 'label')) }}</a></template><button type="button" :class="$h.av(($h.t($h.eq(m.range, 'custom')) ? 'on' : null))" :title.attr="$h.av(($h.t($h.eq(m.range, 'custom')) ? $h.cat('自定义：', m.rangeLabel) : '自定义时间段'))" aria-label="自定义时间段" aria-haspopup="dialog" data-range-custom :data-from.attr="$h.ak(m.rangeFrom)" :data-to.attr="$h.ak(m.rangeTo)" :data-max.attr="$h.ak(m.rangeMax)" :data-action.attr="$h.ak(m.base)" :aria-expanded.attr="$h.av(($h.t($h.eq(m.range, 'custom')) ? 'true' : 'false'))"><svg class="i i-sm"><use href="#i-calendar"></use></svg><span class="rc-tx">自定义</span></button></div>
            </div>
        </div>
        <div class="trend-top">
            <div class="trend-hl">
                <b>{{ $h.s($h.numbers.formatInteger(m.rangeCount, 1, 'COMMA')) }}</b><span class="u">台</span>
                <span :class="['chg', $h.av($h.z(m.rangeDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.rangeDelta, null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.rangeDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.rangeDelta, 'text')) }}</span>
                <span class="vs" v-if="$h.t(!$h.eq(m.rangeDelta, null))">{{ $h.s($h.add('比', m.rangePrevLabel)) }}</span>
            </div>
            <div class="factline">
                <span>{{ $h.s(m.rangeLabel) }}</span>
                <span>日均<b>{{ $h.s(m.rangeAvg) }}</b></span>
                <span>最高一天<b>{{ $h.s(m.rangePeak) }}<small v-if="$h.t($h.gt(m.rangePeak, 0))">{{ $h.s($h.add(' · ', m.rangePeakDay)) }}</small></b></span>
                <span>有激活的天<b>{{ $h.s(m.rangeActive) }}<small>{{ $h.s($h.cat(' / ', m.rangeDays)) }}</small></b></span>
            </div>
        </div>
        <div :class="['vchart', $h.av(m.trendClass)]">
            <div class="vc-plot">
                <template v-for="[L_b, L_bStat] in $h.each(m.trend)"><div :class="['vc-col', $h.av(($h.t($h.p(L_b, 'last')) ? 'last' : null)), $h.av(($h.t($h.eq($h.p(L_b, 'count'), 0)) ? 'zero' : null))]" :style="$h.av($h.cat('--h:', $h.p(L_b, 'px'), 'px;--i:', L_bStat.index))">
                    <span class="vc-bar"></span>
                    <span class="vc-tip">{{ $h.s($h.p(L_b, 'tip')) }}</span>
                </div></template>
            </div>
            <div class="vc-x"><template v-for="[L_b, L_bStat] in $h.each(m.trend)"><span :class="$h.av(($h.t($h.p(L_b, 'last')) ? 'last' : null))">{{ $h.s($h.p(L_b, 'label')) }}</span></template></div>
        </div>
    </section>

    <div class="grid ov-pair">
        <section class="card">
            <div class="card-head">
                <h2>按月统计</h2>
                <span class="tail muted small">每个月的有效激活</span>
            </div>
            <ul class="hbar">
                <template v-for="[L_r, L_rStat] in $h.each(m.months)"><li :class="$h.av(($h.t(L_rStat.first) ? 'cur' : null))">
                    <span class="hbar-k"><b>{{ $h.s($h.p(L_r, 'label')) }}</b><small v-if="$h.t($h.p(L_r, 'partial'))">{{ $h.s($h.p(L_r, 'note')) }}</small></span>
                    <span class="hbar-t"><i :style="$h.av($h.cat('width:', $h.p(L_r, 'w'), '%'))"></i></span>
                    <span class="hbar-v">{{ $h.s($h.numbers.formatInteger($h.p(L_r, 'count'), 1, 'COMMA')) }}</span>
                    <span :class="['hbar-d', $h.av(($h.t(!$h.eq($h.p(L_r, 'delta'), null)) ? $h.z($h.p(L_r, 'delta'), 'dir') : 'none'))]" :title.attr="$h.av(($h.t($h.p(L_r, 'partial')) ? '比上月同期' : '比上个月'))">{{ $h.s(($h.t(!$h.eq($h.p(L_r, 'delta'), null)) ? $h.z($h.p(L_r, 'delta'), 'text') : '—')) }}</span>
                </li></template>
            </ul>
        </section>

        <section class="card" v-if="!$h.t($h.lists.isEmpty(m.subShares))">
            <div class="card-head">
                <h2>二级代理带来的</h2>
                <span class="muted small">{{ $h.s($h.add(m.rangeLabel, ' · 按你看到的算')) }}</span>
                <a class="tail link" :href.attr="$h.ak('/agent/subs')" v-if="!$h.t(m.preview)">管理<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
            </div>
            <ul class="hbar hbar-share scroll">
                <template v-for="[L_s, L_sStat] in $h.each(m.subShares)"><li :class="[$h.av(($h.t($h.p(L_s, 'own')) ? 'own' : null)), $h.av(($h.t($h.p(L_s, 'off')) ? 'off' : null))]">
                    <span class="hbar-k"><b>{{ $h.s($h.p(L_s, 'name')) }}</b><em class="mini-tag" v-if="$h.t($h.p(L_s, 'off'))">停用</em></span>
                    <span class="hbar-t"><i :style="$h.av($h.cat('width:', $h.p(L_s, 'w'), '%'))"></i></span>
                    <span class="hbar-v">{{ $h.s($h.numbers.formatInteger($h.p(L_s, 'count'), 1, 'COMMA')) }}</span>
                    <span class="hbar-d">{{ $h.s($h.cat($h.p(L_s, 'pct'), '%')) }}</span>
                </li></template>
            </ul>
        </section>

        <section class="card" v-if="$h.t($h.lists.isEmpty(m.subShares))">
            <div class="card-head">
                <h2>最新公告</h2>
                <a class="tail link" :href.attr="$h.ak($h.add(m.base, '/notices'))" v-if="!$h.t($h.lists.isEmpty(m.latestNotices))">全部公告<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
            </div>
            <div class="empty slim" v-if="$h.t($h.lists.isEmpty(m.latestNotices))">
                <div class="empty-icon"><svg class="i"><use href="#i-megaphone"></use></svg></div>
                <b>还没有公告</b>{{ $h.s(($h.t($h.p(m.agent, 'sub')) ? '管理员或上级代理发了通知会出现在这里' : '管理员发了通知会出现在这里')) }}
            </div>
            <ul class="mini-notices" v-if="!$h.t($h.lists.isEmpty(m.latestNotices))">
                <template v-for="[L_n, L_nStat] in $h.each(m.latestNotices)"><li :class="[$h.av(($h.t($h.z(L_n, 'read')) ? null : 'unread')), $h.av(($h.t($h.z(L_n, 'important')) ? 'important' : null))]">
                    <a :href.attr="$h.ak($h.add(m.base, '/notices'))">
                        <b>{{ $h.s($h.z(L_n, 'title')) }}</b>
                        <span>{{ $h.s($h.z(L_n, 'body')) }}</span>
                    </a>
                    <time :title.attr="$h.av($h.ui.full($h.z(L_n, 'at')))">{{ $h.s($h.ui.ago($h.z(L_n, 'at'))) }}</time>
                </li></template>
            </ul>
        </section>
    </div>

    <section class="card">
        <div class="card-head">
            <h2>我的口令</h2>
            <span class="muted small">{{ $h.s($h.cat($h.lists.size(m.codes), ' 个 · 可用 ', m.usableCount, ' 个')) }}</span>
            <div class="head-tools" v-if="$h.t(($h.t($h.gt($h.lists.size(m.codes), 10)) || $h.t($h.gt(m.usableCount, 0))))">
                <label class="search" v-if="$h.t($h.gt($h.lists.size(m.codes), 10))"><svg class="i i-sm"><use href="#i-search"></use></svg><input class="input" type="search" placeholder="搜索口令" aria-label="搜索口令" data-filter="#my-codes" data-filter-item="tr"></label>
                <button class="btn btn-secondary btn-sm" type="button" data-copy-usable v-if="$h.t($h.gt(m.usableCount, 0))" :title.attr="$h.av(($h.t($h.lists.isEmpty(m.subNames)) ? '复制列表里所有可用的口令，一行一个' : '复制列表里可用的口令，一行一个；分给二级代理的不算'))"><svg class="i i-sm"><use href="#i-copy"></use></svg>复制可用口令</button>
            </div>
            <a class="tail link" :href.attr="$h.ak($h.add(m.base, '/records'))" v-if="$h.t(($h.t(!$h.t($h.lists.isEmpty(m.codes))) &amp;&amp; $h.t($h.p(m.agent, 'canViewRecords'))))">查看激活记录<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
        </div>
        <div class="empty" v-if="$h.t($h.lists.isEmpty(m.codes))">
            <div class="empty-icon"><svg class="i"><use href="#i-key"></use></svg></div>
            <b>还没有口令</b>{{ $h.s(($h.t($h.p(m.agent, 'sub')) ? '请联系上级代理给你分口令' : ($h.t($h.p(m.agent, 'canRequest')) ? '在「申请口令」里申请，或者联系管理员分配' : '请联系管理员为你分配口令'))) }}
        </div>
        <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty(m.codes))">
            <table class="agent-codes">
                <thead><tr><th>口令</th><th>状态</th><th>有效激活</th><th class="right">{{ $h.s(m.rangeLabel) }}</th><th class="right">{{ $h.s($h.add(m.refLabel, '新增')) }}</th></tr></thead>
                <tbody id="my-codes" data-collapse="10" data-collapse-unit="个口令">
                <template v-for="[L_c, L_cStat] in $h.each(m.codes)"><template v-for="L_st in [$h.ui.codeStatus(L_c)]"><template v-for="L_n in [$h.elvis($h.c(m.totalByCode, 'get', [$h.p(L_c, 'id')]), 0)]"><template v-for="L_d in [$h.elvis($h.c(m.refByCode, 'get', [$h.p(L_c, 'id')]), 0)]"><template v-for="L_rg in [$h.elvis($h.c(m.rangeByCode, 'get', [$h.p(L_c, 'id')]), 0)]"><template v-for="L_to in [($h.t(!$h.eq($h.p(L_c, 'subAgentId'), null)) ? $h.c(m.subNames, 'get', [$h.p(L_c, 'subAgentId')]) : null)]"><tr :data-code.attr="$h.av($h.p(L_c, 'code'))" :data-usable.attr="$h.av(($h.t($h.eq(L_st, '可用')) &amp;&amp; $h.t($h.eq(L_to, null))))" :data-given.attr="$h.av(($h.t(!$h.eq(L_to, null)) ? 'true' : null))">
                    <td>
                        <span class="cell-code">
                            <span class="mono">{{ $h.s($h.p(L_c, 'code')) }}</span>
                            <button class="icon-btn" type="button" :data-copy.attr="$h.av($h.p(L_c, 'code'))" title="复制口令" aria-label="复制口令"><svg class="i i-sm"><use href="#i-copy"></use></svg></button>
                            <span class="mini-tag sub-tag" :title.attr="$h.av($h.cat('分给了二级代理 ', L_to, '，激活照常算在你名下'))" v-if="$h.t(!$h.eq(L_to, null))"><svg class="i i-xs"><use href="#i-users"></use></svg>{{ $h.s(L_to) }}</span>
                        </span>
                    </td>
                    <td><span :class="['badge', $h.av(($h.t($h.eq(L_st, '可用')) ? 'badge-solid' : 'badge-line'))]"><span class="dot"></span><span>{{ $h.s(L_st) }}</span></span></td>
                    <td>
                        <div class="usage">
                            <span class="num"><b>{{ $h.s(L_n) }}</b><span class="muted">{{ $h.s(($h.t($h.gt($h.p(L_c, 'maxUses'), 0)) ? $h.add(' / ', $h.p(L_c, 'maxUses')) : '')) }}</span></span>
                            <span class="usage-bar" v-if="$h.t($h.gt($h.p(L_c, 'maxUses'), 0))"><span :style="$h.av($h.cat('width:', ($h.t($h.ge(L_n, $h.p(L_c, 'maxUses'))) ? 100 : $h.div($h.mul(L_n, 100), $h.p(L_c, 'maxUses'))), '%'))"></span></span>
                        </div>
                    </td>
                    <td :class="['num', 'right', $h.av(($h.t($h.eq(L_rg, 0)) ? 'muted' : null))]">{{ $h.s(($h.t($h.gt(L_rg, 0)) ? L_rg : '—')) }}</td>
                    <td :class="['num', 'right', $h.av(($h.t($h.eq(L_d, 0)) ? 'muted' : null))]">{{ $h.s(($h.t($h.gt(L_d, 0)) ? $h.add('+', L_d) : '—')) }}</td>
                </tr></template></template></template></template></template></template>
                </tbody>
            </table>
        </div>
        <div class="list-none" id="my-codes-none" hidden>没有匹配的口令</div>
    </section>

</main>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('数据概览'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
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
