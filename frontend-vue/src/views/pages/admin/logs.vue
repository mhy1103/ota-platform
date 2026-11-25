<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'logs'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'操作日志'"/>
    <main class="content">

        <div class="page-head">
            <div class="page-title"><div>
                <h1>操作日志</h1>
                <p>{{ $h.s(($h.t(m.canClear) ? '谁在什么时候做了什么：后台操作和设备事件' : '你能看到的 App 的后台操作和设备事件')) }}</p>
            </div></div>
        </div>

        <section class="card">
            <div class="toolbar">
                <form class="toolbar-form" method="get" :action.attr="$h.ak('/admin/logs')">
                    <div class="seg">
                        <a :href.attr="$h.ak($h.ui.url('/admin/logs', 'q', m.q, 'area', m.area, 'app', m.appFilter, 'days', ($h.t($h.gt(m.days, 0)) ? m.days : '')))" :class="$h.av(($h.t(($h.t(!$h.eq(m.type, 'admin')) &amp;&amp; $h.t(!$h.eq(m.type, 'device')))) ? 'on' : null))">全部</a>
                        <a :href.attr="$h.ak($h.ui.url('/admin/logs', 'type', 'admin', 'q', m.q, 'area', m.area, 'app', m.appFilter, 'days', ($h.t($h.gt(m.days, 0)) ? m.days : '')))" :class="$h.av(($h.t($h.eq(m.type, 'admin')) ? 'on' : null))">后台操作</a>
                        <a :href.attr="$h.ak($h.ui.url('/admin/logs', 'type', 'device', 'q', m.q, 'area', m.area, 'app', m.appFilter, 'days', ($h.t($h.gt(m.days, 0)) ? m.days : '')))" :class="$h.av(($h.t($h.eq(m.type, 'device')) ? 'on' : null))">设备事件</a>
                    </div>
                    <input type="hidden" name="type" :value.attr="$h.ak(m.type)" v-if="$h.t(($h.t($h.eq(m.type, 'admin')) || $h.t($h.eq(m.type, 'device'))))">
                    <select class="select" name="app" data-autosubmit aria-label="按 App 筛选" v-if="$h.t($h.gt($h.lists.size(m.logApps), 1))">
                        <option :value.attr="''">{{ $h.s(($h.t(m.canClear) ? '全部 App 和系统' : '全部 App')) }}</option>
                        <template v-for="[L_a, L_aStat] in $h.each(m.logApps)"><template v-if="L_aStat.index">
                        </template><option :value.attr="$h.ak($h.p(L_a, 'slug'))" :selected.attr="$h.t($h.eq($h.p(L_a, 'slug'), m.appFilter)) ? 'selected' : null">{{ $h.s($h.p(L_a, 'name')) }}</option></template>
                    </select>
                    <input type="hidden" name="app" :value.attr="$h.ak(m.appFilter)" v-if="$h.t(($h.t($h.le($h.lists.size(m.logApps), 1)) &amp;&amp; $h.t(!$h.t($h.strings.isEmpty(m.appFilter)))))">
                    <select class="select" name="area" data-autosubmit aria-label="按类别筛选">
                        <option :value.attr="''">全部类别</option>
                        <template v-for="[L_r, L_rStat] in $h.each(m.logAreas)"><option :value.attr="$h.ak($h.p(L_r, 'key'))" :selected.attr="$h.t($h.eq($h.p(L_r, 'key'), m.area)) ? 'selected' : null">{{ $h.s($h.p(L_r, 'label')) }}</option></template>
                    </select>
                    <select class="select" name="days" data-autosubmit aria-label="时间范围">
                        <option :value.attr="'0'">全部时间</option>
                        <option :value.attr="'1'" :selected.attr="$h.t($h.eq(m.days, 1)) ? 'selected' : null">今天</option>
                        <option :value.attr="'7'" :selected.attr="$h.t($h.eq(m.days, 7)) ? 'selected' : null">近 7 天</option>
                        <option :value.attr="'30'" :selected.attr="$h.t($h.eq(m.days, 30)) ? 'selected' : null">近 30 天</option>
                        <option :value.attr="'90'" :selected.attr="$h.t($h.eq(m.days, 90)) ? 'selected' : null">近 90 天</option>
                    </select>
                    <label class="search">
                        <svg class="i"><use href="#i-search"></use></svg>
                        <input class="input" type="search" name="q" :value.attr="$h.ak(m.q)" placeholder="详情或操作人" aria-label="搜索日志">
                    </label>
                    <button class="btn btn-secondary" type="submit">搜索</button>
                    <a class="link" :href.attr="$h.ak($h.ui.url('/admin/logs', 'type', m.type))" v-if="!$h.t(($h.t(($h.t(($h.t($h.strings.isEmpty(m.q)) &amp;&amp; $h.t($h.strings.isEmpty(m.appFilter)))) &amp;&amp; $h.t($h.eq(m.days, 0)))) &amp;&amp; $h.t($h.strings.isEmpty(m.area))))">清除筛选</a>
                </form>
                <span class="tail">
                    <span class="muted">{{ $h.s($h.cat('共 ', $h.p(m.logs, 'totalElements'), ' 条')) }}</span>
                    <a class="btn btn-secondary btn-sm" data-no-swap download :href.attr="$h.ak($h.ui.url('/admin/logs/export', 'type', m.type, 'q', m.q, 'area', m.area, 'app', m.appFilter, 'days', ($h.t($h.gt(m.days, 0)) ? m.days : '')))" title="按当前筛选条件导出，最多 5 万条" v-if="$h.t($h.gt($h.p(m.logs, 'totalElements'), 0))"><svg class="i i-sm"><use href="#i-download"></use></svg>导出</a>
                    <template v-if="$h.t(($h.t(($h.t(($h.t(($h.t(($h.t(m.canClear) &amp;&amp; $h.t($h.gt($h.p(m.logs, 'totalElements'), 0)))) &amp;&amp; $h.t($h.strings.isEmpty(m.q)))) &amp;&amp; $h.t($h.strings.isEmpty(m.appFilter)))) &amp;&amp; $h.t($h.eq(m.days, 0)))) &amp;&amp; $h.t($h.strings.isEmpty(m.area))))"><template v-for="L_what in [($h.t($h.eq(m.type, 'admin')) ? '后台操作日志' : ($h.t($h.eq(m.type, 'device')) ? '设备事件日志' : '全部日志'))]"><form method="post" :action.attr="$h.ak('/admin/logs/clear')" :data-confirm.attr="$h.av($h.cat('清空', L_what, '？'))" :data-confirm-body.attr="$h.av($h.cat('将删除当前列表的 ', $h.p(m.logs, 'totalElements'), ' 条', L_what, '，无法恢复。', ($h.t($h.eq(m.type, 'device')) ? '激活记录本身不受影响。' : ''), '清空操作本身会留下一条记录。'))" data-confirm-ok="清空" data-confirm-danger>
                        <input type="hidden" name="type" :value.attr="$h.ak(($h.t(($h.t($h.eq(m.type, 'admin')) || $h.t($h.eq(m.type, 'device')))) ? m.type : 'all'))">
                        <button class="btn btn-secondary btn-sm" type="submit"><svg class="i i-sm"><use href="#i-trash"></use></svg>清空</button>
                    </form></template></template>
                </span>
            </div>

            <div class="empty" v-if="$h.t($h.eq($h.p(m.logs, 'totalElements'), 0))">
                <div class="empty-icon"><svg class="i"><use href="#i-file"></use></svg></div>
                <b>{{ $h.s(($h.t(($h.t(($h.t($h.strings.isEmpty(m.q)) &amp;&amp; $h.t($h.strings.isEmpty(m.appFilter)))) &amp;&amp; $h.t($h.strings.isEmpty(m.area)))) ? '暂无日志' : '没有找到相关日志')) }}</b>
            </div>

            <div class="table-wrap" data-guide="logs-table" v-if="!$h.t($h.eq($h.p(m.logs, 'totalElements'), 0))">
                <table>
                    <thead><tr><th>时间</th><th>类型</th><th>App</th><th>操作人</th><th>详情</th></tr></thead>
                    <tbody>
                    <template v-for="[L_l, L_lStat] in $h.each($h.p(m.logs, 'content'))"><template v-if="L_lStat.index">
                    </template><tr>
                        <td class="muted nowrap num" style="width:1%" :title.attr="$h.av($h.ui.full($h.p(L_l, 'createdAt')))">{{ $h.s($h.ui.dts($h.p(L_l, 'createdAt'))) }}</td>
                        <td style="width:1%"><span :class="['tag', $h.av(($h.t($h.ui.deviceEvent($h.p(L_l, 'action'))) ? 'tag-device' : $h.ui.tone($h.p(L_l, 'action'))))]" :title.attr="$h.av($h.p(L_l, 'action'))">{{ $h.s($h.ui.action($h.p(L_l, 'action'))) }}</span></td>
                        <td class="nowrap small" style="width:1%">
                            <span v-if="$h.t(!$h.eq($h.p(L_l, 'appId'), null))">{{ $h.s($h.elvis($h.c(m.appNames, 'get', [$h.p(L_l, 'appId')]), '已删除的 App')) }}</span>
                            <span class="muted" title="系统级操作，不属于某个 App" v-if="$h.t($h.eq($h.p(L_l, 'appId'), null))">系统</span>
                        </td>
                        <td class="nowrap small" style="width:1%">
                            <span :title.attr="$h.av($h.p(L_l, 'operator'))" v-if="$h.t(($h.t(!$h.eq($h.p(L_l, 'operator'), null)) &amp;&amp; $h.t($h.c(m.operatorNames, 'containsKey', [$h.p(L_l, 'operator')]))))">{{ $h.s($h.c(m.operatorNames, 'get', [$h.p(L_l, 'operator')])) }}</span>
                            <span v-if="$h.t(($h.t(!$h.eq($h.p(L_l, 'operator'), null)) &amp;&amp; $h.t(!$h.t($h.c(m.operatorNames, 'containsKey', [$h.p(L_l, 'operator')])))))">{{ $h.s($h.p(L_l, 'operator')) }}</span>
                            <span class="muted" v-if="$h.t($h.eq($h.p(L_l, 'operator'), null))">{{ $h.s(($h.t($h.ui.deviceEvent($h.p(L_l, 'action'))) ? '设备' : '—')) }}</span>
                        </td>
                        <td>{{ $h.s($h.p(L_l, 'detail')) }}</td>
                    </tr></template>
                    </tbody>
                </table>
            </div>

            <FAdminLayoutPager :m="m" :c="c" :P_p="m.logs" :P_base="$h.ui.url('/admin/logs', 'type', m.type, 'q', m.q, 'area', m.area, 'app', m.appFilter, 'days', ($h.t($h.gt(m.days, 0)) ? m.days : ''))"/>
        </section>

    </main>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('操作日志'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
    css: ["app/ui.css"],
    js: ["app/ui.js"],
    theme: { attr: true, css: (m, c, $h) => m.themeCss }
};
</script>

<script setup>
import FAdminLayoutOverlays from '../../frags/admin-layout--overlays.vue';
import FAdminLayoutPager from '../../frags/admin-layout--pager.vue';
import FAdminLayoutSidebar from '../../frags/admin-layout--sidebar.vue';
import FAdminLayoutTopbar from '../../frags/admin-layout--topbar.vue';
defineProps({ m: Object, c: Object });
</script>
