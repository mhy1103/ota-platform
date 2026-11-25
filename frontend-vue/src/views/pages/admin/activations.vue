<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'activations'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'激活记录'"/>
    <main class="content">

        <div class="page-head">
            <div class="page-title"><div>
                <h1>激活记录</h1>
                <p>{{ $h.s($h.cat('有效激活 ', m.confirmedTotal, ' 台，待确认 ', m.pendingTotal, ' 台', ($h.t($h.gt(m.fastTotal, 0)) ? $h.cat('，非正常 ', m.fastTotal, ' 台') : ''), ($h.t($h.gt(m.dupTotal, 0)) ? $h.cat('，重复 ', m.dupTotal, ' 台') : ''))) }}</p>
            </div></div>
            <div class="page-actions">
                <a :class="['btn btn-secondary', $h.av(($h.t($h.eq($h.p(m.records, 'totalElements'), 0)) ? 'disabled' : null))]" :href.attr="$h.ak($h.ui.url('/admin/activations/export', 'status', m.status, 'agent', m.agent, 'app', m.appFilter, 'code', m.code, 'q', m.q, 'range', m.range, 'from', m.from, 'to', m.to))" :title.attr="$h.av(($h.t(m.filtered) ? '导出当前筛选出的记录' : '导出全部记录'))" v-if="$h.t($h.access.can('codes'))">
                    <svg class="i"><use href="#i-download"></use></svg>导出 CSV</a>
            </div>
        </div>

        <section class="card">
            <form class="toolbar" method="get" :action.attr="$h.ak('/admin/activations')" id="filter-form">
                <div class="seg">
                    <a :href.attr="$h.ak($h.ui.url('/admin/activations', 'agent', m.agent, 'app', m.appFilter, 'code', m.code, 'q', m.q, 'range', m.range, 'from', m.from, 'to', m.to))" :class="$h.av(($h.t(($h.t(!$h.eq(m.status, 'confirmed')) &amp;&amp; $h.t(!$h.eq(m.status, 'pending')) &amp;&amp; $h.t(!$h.eq(m.status, 'fast')) &amp;&amp; $h.t(!$h.eq(m.status, 'dup')))) ? 'on' : null))">全部</a>
                    <a :href.attr="$h.ak($h.ui.url('/admin/activations', 'status', 'confirmed', 'agent', m.agent, 'app', m.appFilter, 'code', m.code, 'q', m.q, 'range', m.range, 'from', m.from, 'to', m.to))" :class="$h.av(($h.t($h.eq(m.status, 'confirmed')) ? 'on' : null))">已确认</a>
                    <a :href.attr="$h.ak($h.ui.url('/admin/activations', 'status', 'pending', 'agent', m.agent, 'app', m.appFilter, 'code', m.code, 'q', m.q, 'range', m.range, 'from', m.from, 'to', m.to))" :class="$h.av(($h.t($h.eq(m.status, 'pending')) ? 'on' : null))">待确认</a>
                    <a :href.attr="$h.ak($h.ui.url('/admin/activations', 'status', 'fast', 'agent', m.agent, 'app', m.appFilter, 'code', m.code, 'q', m.q, 'range', m.range, 'from', m.from, 'to', m.to))" :class="$h.av(($h.t($h.eq(m.status, 'fast')) ? 'on' : null))" title="兑换后不到 2 秒就来确认的，真手机做不到，判为刷的，不算有效激活">非正常</a>
                    <a :href.attr="$h.ak($h.ui.url('/admin/activations', 'status', 'dup', 'agent', m.agent, 'app', m.appFilter, 'code', m.code, 'q', m.q, 'range', m.range, 'from', m.from, 'to', m.to))" :class="$h.av(($h.t($h.eq(m.status, 'dup')) ? 'on' : null))" title="这台手机在别的 App 已经算过一次（设备指纹：初始化时间+机型，或者同一个设备号），所有 App 加起来只算一次">重复</a>
                </div>
                <input type="hidden" name="status" :value.attr="$h.ak(m.status)" v-if="$h.t(($h.t($h.eq(m.status, 'confirmed')) || $h.t($h.eq(m.status, 'pending')) || $h.t($h.eq(m.status, 'fast')) || $h.t($h.eq(m.status, 'dup'))))">
                <input type="hidden" name="code" :value.attr="$h.ak(m.code)" v-if="!$h.t($h.strings.isEmpty(m.code))">
                <select class="select" name="agent" data-autosubmit aria-label="按代理筛选" v-if="!$h.t($h.lists.isEmpty(m.agents))">
                    <option :value.attr="''">全部代理</option>
                    <option :value.attr="'none'" :selected.attr="$h.t($h.eq(m.agent, 'none')) ? 'selected' : null">未分配</option>
                    <template v-for="[L_a, L_aStat] in $h.each(m.agents)"><template v-if="L_aStat.index">
                    </template><option :value.attr="$h.ak($h.p(L_a, 'id'))" :selected.attr="$h.t($h.eq(m.agent, $h.add('', $h.p(L_a, 'id')))) ? 'selected' : null">{{ $h.s($h.p(L_a, 'name')) }}</option></template>
                </select>
                <select class="select" name="app" data-autosubmit aria-label="按 App 筛选" v-if="$h.t($h.gt($h.lists.size(m.recordApps), 1))">
                    <option :value.attr="''">全部 App</option>
                    <template v-for="[L_o, L_oStat] in $h.each(m.recordApps)"><option :value.attr="$h.ak($h.p(L_o, 'id'))" :selected.attr="$h.t($h.eq(m.appFilter, $h.add('', $h.p(L_o, 'id')))) ? 'selected' : null">{{ $h.s($h.p(L_o, 'name')) }}</option></template>
                </select>
                <select class="select" name="range" aria-label="提交时间" data-role="range">
                    <option :value.attr="'all'" :selected.attr="$h.t($h.eq(m.range, 'all')) ? 'selected' : null">全部时间</option>
                    <option :value.attr="'today'" :selected.attr="$h.t($h.eq(m.range, 'today')) ? 'selected' : null">今天</option>
                    <option :value.attr="'yesterday'" :selected.attr="$h.t($h.eq(m.range, 'yesterday')) ? 'selected' : null">昨天</option>
                    <option :value.attr="'7d'" :selected.attr="$h.t($h.eq(m.range, '7d')) ? 'selected' : null">近 7 天</option>
                    <option :value.attr="'30d'" :selected.attr="$h.t($h.eq(m.range, '30d')) ? 'selected' : null">近 30 天</option>
                    <option :value.attr="'custom'" :selected.attr="$h.t($h.eq(m.range, 'custom')) ? 'selected' : null">自定义…</option>
                </select>
                <span class="date-range" data-role="dates" :hidden.attr="$h.t(!$h.eq(m.range, 'custom')) ? 'hidden' : null">
                    <input class="input" type="date" name="from" :value.attr="$h.ak(m.from)" aria-label="开始日期">
                    <span class="muted">至</span>
                    <input class="input" type="date" name="to" :value.attr="$h.ak(m.to)" aria-label="结束日期">
                </span>
                <label class="search">
                    <svg class="i"><use href="#i-search"></use></svg>
                    <input class="input" type="search" name="q" :value.attr="$h.ak(m.q)" placeholder="设备 ID、IP 或口令" aria-label="搜索设备 ID、IP 或口令">
                </label>
                <button class="btn btn-secondary" type="submit">筛选</button>
                <a class="link" :href.attr="$h.ak('/admin/activations')" v-if="$h.t(m.filtered)">清除筛选</a>
                <span class="tail muted" v-if="!$h.t(m.filtered)">{{ $h.s($h.cat('共 ', $h.numbers.formatInteger($h.p(m.records, 'totalElements'), 1, 'COMMA'), ' 条')) }}</span>
            </form>

            <div class="result-sum" v-if="$h.t(($h.t(m.filtered) || $h.t(!$h.t($h.strings.isEmpty(m.code)))))">
                <span class="filter-chip" v-if="!$h.t($h.strings.isEmpty(m.code))">口令 <b class="mono">{{ $h.s(m.code) }}</b>
                    <a :href.attr="$h.ak($h.ui.url('/admin/activations', 'status', m.status, 'agent', m.agent, 'app', m.appFilter, 'q', m.q, 'range', m.range, 'from', m.from, 'to', m.to))" title="去掉这个条件" aria-label="去掉口令条件"><svg class="i i-sm"><use href="#i-x"></use></svg></a></span>
                <span v-if="$h.t(!$h.eq(m.matched, null))">筛选结果 <b>{{ $h.s($h.numbers.formatInteger(m.matched, 1, 'COMMA')) }}</b> 条</span>
                <span v-if="$h.t(!$h.eq(m.matched, null))">已确认 <b>{{ $h.s($h.numbers.formatInteger(m.matchedConfirmed, 1, 'COMMA')) }}</b></span>
                <span v-if="$h.t(!$h.eq(m.matched, null))">待确认 <b>{{ $h.s($h.numbers.formatInteger(m.matchedPending, 1, 'COMMA')) }}</b></span>
                <span v-if="$h.t(($h.t(!$h.eq(m.matched, null)) &amp;&amp; $h.t($h.gt(m.matchedFast, 0))))">非正常 <b>{{ $h.s($h.numbers.formatInteger(m.matchedFast, 1, 'COMMA')) }}</b></span>
                <span v-if="$h.t(($h.t(!$h.eq(m.matched, null)) &amp;&amp; $h.t($h.gt(m.matchedDup, 0))))">重复 <b>{{ $h.s($h.numbers.formatInteger(m.matchedDup, 1, 'COMMA')) }}</b></span>
            </div>

            <div class="empty" v-if="$h.t($h.eq($h.p(m.records, 'totalElements'), 0))">
                <div class="empty-icon"><svg class="i"><use href="#i-inbox"></use></svg></div>
                <b>{{ $h.s(($h.t(m.filtered) ? '没有符合条件的记录' : '暂无激活记录')) }}</b>
                <span>{{ $h.s(($h.t(m.filtered) ? '换个筛选条件试试' : 'B 面上线模式下，用户提交口令后会出现在这里')) }}</span>
            </div>

            <div class="table-wrap" data-guide="act-table" v-if="!$h.t($h.eq($h.p(m.records, 'totalElements'), 0))">
                <table>
                    <thead>
                    <tr><th>提交时间</th><th v-if="$h.t($h.gt($h.lists.size(m.recordApps), 1))">App</th><th>口令</th><th>代理</th><th>设备 ID</th><th>IP</th><th>状态</th><th>确认时间</th></tr>
                    </thead>
                    <tbody>
                    <template v-for="[L_r, L_rStat] in $h.each($h.p(m.records, 'content'))"><template v-if="L_rStat.index">
                    </template><template v-for="L_owner in [$h.c(m.codeAgent, 'get', [$h.p(L_r, 'codeId')])]"><tr>
                        <td class="nowrap num" :title.attr="$h.av($h.ui.full($h.p(L_r, 'activatedAt')))">{{ $h.s($h.ui.dts($h.p(L_r, 'activatedAt'))) }}</td>
                        <td class="nowrap" v-if="$h.t($h.gt($h.lists.size(m.recordApps), 1))">{{ $h.s($h.elvis($h.c(m.appNames, 'get', [$h.p(L_r, 'appId')]), '已删除的 App')) }}</td>
                        <td><a class="mono link-strong" :href.attr="$h.ak($h.ui.url('/admin/activations', 'status', m.status, 'app', m.appFilter, 'code', $h.p(L_r, 'codeText'), 'range', m.range, 'from', m.from, 'to', m.to))" title="只看这个口令的记录">{{ $h.s($h.p(L_r, 'codeText')) }}</a></td>
                        <td :class="['nowrap', $h.av(($h.t($h.eq(L_owner, null)) ? 'muted' : null))]">{{ $h.s($h.elvis(L_owner, '未分配')) }}</td>
                        <td>
                            <span class="cell-code">
                                <a class="mono sub small dev-link" :href.attr="$h.ak($h.ui.url('/admin/activations', 'q', $h.p(L_r, 'deviceId')))" title="只看这台设备的激活记录">{{ $h.s($h.p(L_r, 'deviceId')) }}</a>
                                <button class="icon-btn" type="button" :data-copy.attr="$h.av($h.p(L_r, 'deviceId'))" title="复制设备 ID" aria-label="复制设备 ID"><svg class="i i-sm"><use href="#i-copy"></use></svg></button>
                            </span>
                        </td>
                        <td class="mono muted small nowrap">{{ $h.s($h.elvis($h.p(L_r, 'deviceIp'), '—')) }}</td>
                        <td><FAdminLayoutRecordStatus :m="m" :c="c" :P_r="L_r"/></td>
                        <td class="muted nowrap num" :title.attr="$h.av($h.ui.full($h.p(L_r, 'confirmedAt')))">{{ $h.s($h.ui.dts($h.p(L_r, 'confirmedAt'))) }}</td>
                    </tr></template></template>
                    </tbody>
                </table>
            </div>

            <FAdminLayoutPager :m="m" :c="c" :P_p="m.records" :P_base="$h.ui.url('/admin/activations', 'status', m.status, 'agent', m.agent, 'app', m.appFilter, 'code', m.code, 'q', m.q, 'range', m.range, 'from', m.from, 'to', m.to)"/>
        </section>

    </main>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('激活记录'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
    css: ["app/ui.css"],
    js: ["app/ui.js"],
    theme: { attr: true, css: (m, c, $h) => m.themeCss }
};
</script>

<script setup>
import FAdminLayoutOverlays from '../../frags/admin-layout--overlays.vue';
import FAdminLayoutPager from '../../frags/admin-layout--pager.vue';
import FAdminLayoutRecordStatus from '../../frags/admin-layout--recordStatus.vue';
import FAdminLayoutSidebar from '../../frags/admin-layout--sidebar.vue';
import FAdminLayoutTopbar from '../../frags/admin-layout--topbar.vue';
defineProps({ m: Object, c: Object });
</script>
