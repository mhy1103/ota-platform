<template>
<FAdminLayoutAgentbar :m="m" :c="c" :P_active="'records'"/>
<template v-for="L_path in [$h.add(m.base, '/records')]"><main class="content ap ap-rec">

    <div class="page-head" :data-live-refresh.attr="$h.av(($h.t($h.eq(m.cutoff, null)) ? 'on' : null))" :data-live.attr="$h.av(($h.t($h.eq(m.cutoff, null)) ? '10' : null))">
        <div class="page-title"><div>
            <h1>激活记录</h1>
            <p>{{ $h.s(($h.t($h.eq(m.cutoff, null)) ? '你的口令带来的每次激活，实时更新' : $h.add('你的口令带来的每次激活，统计到 ', $h.ui.dt(m.cutoff)))) }}</p>
        </div></div>
    </div>

    <section class="card">
        <form class="toolbar" method="get" :action.attr="$h.ak(L_path)">
            <div class="seg">
                <a :href.attr="$h.ak($h.ui.url(L_path, 'code', m.code))" :class="$h.av(($h.t(($h.t(!$h.eq(m.status, 'confirmed')) &amp;&amp; $h.t(!$h.eq(m.status, 'pending')) &amp;&amp; $h.t(!$h.eq(m.status, 'fast')) &amp;&amp; $h.t(!$h.eq(m.status, 'dup')))) ? 'on' : null))">全部</a>
                <a :href.attr="$h.ak($h.ui.url(L_path, 'status', 'confirmed', 'code', m.code))" :class="$h.av(($h.t($h.eq(m.status, 'confirmed')) ? 'on' : null))">已确认</a>
                <a :href.attr="$h.ak($h.ui.url(L_path, 'status', 'pending', 'code', m.code))" :class="$h.av(($h.t($h.eq(m.status, 'pending')) ? 'on' : null))">待确认</a>
                <a :href.attr="$h.ak($h.ui.url(L_path, 'status', 'fast', 'code', m.code))" :class="$h.av(($h.t($h.eq(m.status, 'fast')) ? 'on' : null))">非正常</a>
                <a :href.attr="$h.ak($h.ui.url(L_path, 'status', 'dup', 'code', m.code))" :class="$h.av(($h.t($h.eq(m.status, 'dup')) ? 'on' : null))">重复</a>
            </div>
            <input type="hidden" name="status" :value.attr="$h.ak(m.status)" v-if="$h.t(($h.t($h.eq(m.status, 'confirmed')) || $h.t($h.eq(m.status, 'pending')) || $h.t($h.eq(m.status, 'fast')) || $h.t($h.eq(m.status, 'dup'))))">
            <select class="select" name="code" data-autosubmit aria-label="按口令筛选" v-if="!$h.t($h.lists.isEmpty(m.codes))">
                <option :value.attr="''">全部口令</option>
                <template v-for="[L_c, L_cStat] in $h.each(m.codes)"><template v-if="L_cStat.index">
                </template><option :value.attr="$h.ak($h.p(L_c, 'code'))" :selected.attr="$h.t($h.eq($h.p(L_c, 'code'), m.code)) ? 'selected' : null">{{ $h.s($h.p(L_c, 'code')) }}</option></template>
            </select>
            <a class="link" :href.attr="$h.ak(L_path)" v-if="$h.t(m.filtered)">清除筛选</a>
            <span class="tail muted">{{ $h.s($h.cat('共 ', $h.p(m.records, 'totalElements'), ' 条')) }}</span>
            <a class="btn btn-secondary btn-sm" :href.attr="$h.ak($h.ui.url('/agent/records/export', 'status', m.status, 'code', m.code))" download data-no-swap v-if="$h.t(($h.t(!$h.t(m.preview)) &amp;&amp; $h.t($h.gt($h.p(m.records, 'totalElements'), 0))))"><svg class="i i-sm"><use href="#i-download"></use></svg>导出</a>
        </form>

        <div class="empty" v-if="$h.t($h.eq($h.p(m.records, 'totalElements'), 0))">
            <div class="empty-icon"><svg class="i"><use href="#i-inbox"></use></svg></div>
            <b>{{ $h.s(($h.t(m.filtered) ? '没有符合条件的记录' : '暂无激活记录')) }}</b>
            <span>{{ $h.s(($h.t(m.filtered) ? '换个筛选条件试试' : '用户提交你的口令后会出现在这里')) }}</span>
        </div>

        <div class="table-wrap" v-if="!$h.t($h.eq($h.p(m.records, 'totalElements'), 0))">
            <table class="agent-recs">
                <thead><tr><th>提交时间</th><th>口令</th><th>设备</th><th>IP</th><th>状态</th><th>激活时间</th></tr></thead>
                <tbody>
                <template v-for="[L_r, L_rStat] in $h.each($h.p(m.records, 'content'))"><template v-if="L_rStat.index">
                </template><template v-for="L_ok in [$h.agentPortal.confirmedAsOf(L_r, m.cutoff)]"><tr>
                    <td class="nowrap num" :title.attr="$h.av($h.ui.full($h.p(L_r, 'activatedAt')))">{{ $h.s($h.ui.dts($h.p(L_r, 'activatedAt'))) }}</td>
                    <td><template v-for="L_to in [$h.c(m.subOfCode, 'get', [$h.p(L_r, 'codeId')])]"><span class="mono strong">{{ $h.s($h.p(L_r, 'codeText')) }}</span><span class="mini-tag sub-tag" :title.attr="$h.av($h.cat('分给了二级代理 ', L_to))" v-if="$h.t(!$h.eq(L_to, null))"><svg class="i i-xs"><use href="#i-users"></use></svg>{{ $h.s(L_to) }}</span><span class="muted-line" v-if="$h.t(!$h.t($h.z(m.appNames, 'isEmpty')))">{{ $h.s($h.c(m.appNames, 'get', [$h.p(L_r, 'appId')])) }}</span></template></td>
                    <td class="mono sub small">{{ $h.s($h.ui.maskDevice($h.p(L_r, 'deviceId'))) }}</td>
                    <td class="mono muted small nowrap">{{ $h.s($h.ui.maskIp($h.p(L_r, 'deviceIp'))) }}</td>
                    <td>
                        <span class="badge badge-solid" v-if="$h.t(L_ok)"><span class="dot"></span>已确认</span>
                        <span class="badge badge-danger" v-if="$h.t(($h.t(!$h.t(L_ok)) &amp;&amp; $h.t($h.p(L_r, 'fast'))))"><span class="dot"></span>非正常</span>
                        <span class="badge badge-warn" v-if="$h.t(($h.t(!$h.t(L_ok)) &amp;&amp; $h.t(!$h.t($h.p(L_r, 'fast'))) &amp;&amp; $h.t($h.p(L_r, 'dup'))))"><span class="dot"></span>重复</span>
                        <span class="badge badge-line" v-if="$h.t(($h.t(!$h.t(L_ok)) &amp;&amp; $h.t(!$h.t($h.p(L_r, 'fast'))) &amp;&amp; $h.t(!$h.t($h.p(L_r, 'dup')))))"><span class="dot"></span>待确认</span>
                    </td>
                    <td class="muted nowrap num">{{ $h.s(($h.t(L_ok) ? $h.ui.dts($h.p(L_r, 'confirmedAt')) : '—')) }}</td>
                </tr></template></template>
                </tbody>
            </table>
        </div>

        <div class="pager" v-if="$h.t($h.gt($h.p(m.records, 'totalPages'), 1))">
            <span>{{ $h.s($h.cat('第 ', $h.add($h.p(m.records, 'number'), 1), ' / ', $h.p(m.records, 'totalPages'), ' 页')) }}</span>
            <div class="btn-row">
                <a :class="['btn btn-secondary btn-sm', $h.av(($h.t($h.p(m.records, 'first')) ? 'disabled' : null))]" :href.attr="$h.ak($h.ui.url(L_path, 'status', m.status, 'code', m.code, 'page', $h.p(m.records, 'number')))"><svg class="i i-sm"><use href="#i-left"></use></svg>上一页</a>
                <a :class="['btn btn-secondary btn-sm', $h.av(($h.t($h.p(m.records, 'last')) ? 'disabled' : null))]" :href.attr="$h.ak($h.ui.url(L_path, 'status', m.status, 'code', m.code, 'page', $h.add($h.p(m.records, 'number'), 2)))">下一页<svg class="i i-sm"><use href="#i-right"></use></svg></a>
            </div>
        </div>
    </section>

    <div class="note">
        <svg class="i"><use href="#i-info"></use></svg>
        <span>「待确认」是提交了口令还没激活成功，成功后变「已确认」；「非正常」「重复」是系统判定无效的，不算有效激活。设备号和 IP 已脱敏。</span>
    </div>

</main></template>
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
import FAdminLayoutAgentbar from '../../frags/admin-layout--agentbar.vue';
import FAdminLayoutOverlays from '../../frags/admin-layout--overlays.vue';
defineProps({ m: Object, c: Object });
</script>
