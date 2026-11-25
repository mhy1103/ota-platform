<template><div class="agent-top">
    <div class="preview-strip" v-if="$h.t(m.preview)">
        <span>{{ $h.s($h.cat(($h.t($h.p(m.agent, 'sub')) ? '正在以二级代理「' : '正在以代理「'), $h.p(m.agent, 'name'), '」的视角预览，这就是 TA 登录后看到的页面')) }}</span>
        <a :href.attr="$h.ak($h.elvis(m.previewBack, '/admin/agents'))"><svg class="i i-sm"><use href="#i-left"></use></svg>{{ $h.s($h.elvis(m.previewBackLabel, '返回代理管理')) }}</a>
    </div>
    <header class="agentbar">
        <div class="agentbar-inner">
            <a class="agent-brand" :href.attr="$h.ak(m.base)"><svg class="brand-logo"><use href="#i-logo"></use></svg><span>代理后台</span></a>
            <nav class="agent-tabs">
                <a :href.attr="$h.ak(m.base)" :class="$h.av(($h.t($h.eq(P_active, 'overview')) ? 'on' : null))">数据概览</a>
                <a :href.attr="$h.ak($h.add(m.base, '/requests'))" :class="$h.av(($h.t($h.eq(P_active, 'requests')) ? 'on' : null))" v-if="$h.t($h.p(m.agent, 'canRequest'))">申请口令<span class="tab-dot" :title.attr="$h.av($h.cat(m.pendingRequests, ' 个待审核'))" v-if="$h.t($h.gt(m.pendingRequests, 0))">{{ $h.s(m.pendingRequests) }}</span></a>
                <a :href.attr="$h.ak($h.add(m.base, '/records'))" :class="$h.av(($h.t($h.eq(P_active, 'records')) ? 'on' : null))" v-if="$h.t($h.p(m.agent, 'canViewRecords'))">激活记录</a>
                <a :href.attr="$h.ak($h.add(m.base, '/retention'))" :class="$h.av(($h.t($h.eq(P_active, 'retention')) ? 'on' : null))">留存</a>
                <a :href.attr="$h.ak($h.add(m.base, '/subs'))" :class="$h.av(($h.t($h.eq(P_active, 'subs')) ? 'on' : null))" v-if="$h.t($h.p(m.agent, 'canManageSubs'))">二级代理</a>
                <a :href.attr="$h.ak($h.add(m.base, '/notices'))" :class="$h.av(($h.t($h.eq(P_active, 'notices')) ? 'on' : null))">公告<span class="tab-count" :title.attr="$h.av($h.cat(m.noticeUnread, ' 条没看过'))" v-if="$h.t($h.gt(m.noticeUnread, 0))">{{ $h.s(m.noticeUnread) }}</span></a>
            </nav>
            <div class="agent-right">
                <span class="chip" :title.attr="$h.av(($h.t($h.eq(m.cutoff, null)) ? '数据实时更新' : $h.add('数据统计到 ', $h.ui.full(m.cutoff))))">
                    <svg class="live-ring" viewBox="0 0 20 20" aria-hidden="true" data-live-ring v-if="$h.t($h.eq(m.cutoff, null))"><circle class="bg" cx="10" cy="10" r="7"></circle><circle class="fg" cx="10" cy="10" r="7" transform="rotate(-90 10 10)"></circle></svg>
                    <svg class="i i-sm" v-if="!$h.t($h.eq(m.cutoff, null))"><use href="#i-clock"></use></svg>
                    <span>{{ $h.s(($h.t($h.eq(m.cutoff, null)) ? '实时数据' : $h.add('数据截至 ', $h.ui.dt(m.cutoff)))) }}</span>
                </span>
                <button class="top-btn lang-btn" type="button" data-lang-toggle title="切换语言 · Language" aria-label="切换语言 · Language"><svg class="i"><use href="#i-globe"></use></svg><span class="lang-to only-zh">EN</span><span class="lang-to only-en">中</span></button>
                <FAdminLayoutLookMenu :m="m" :c="c"/>
                <button class="top-btn" type="button" data-guide-run="agent" title="使用引导" aria-label="使用引导"><svg class="i"><use href="#i-help"></use></svg></button>
                <a :class="['agent-user', $h.av(($h.t($h.eq(P_active, 'account')) ? 'on' : null))]" :href.attr="$h.ak($h.add(m.base, '/account'))" title="我的账号">
                    <span class="avatar light"><svg class="i i-sm"><use href="#i-user"></use></svg></span>
                    <span>{{ $h.s($h.p(m.agent, 'name')) }}</span>
                </a>
                <form method="post" :action.attr="$h.ak('/logout')" v-if="!$h.t(m.preview)">
                    <button class="icon-btn" type="submit" title="退出登录" aria-label="退出登录"><svg class="i"><use href="#i-logout"></use></svg></button>
                </form>
            </div>
        </div>
    </header>
</div></template>

<script setup>
import FAdminLayoutLookMenu from '../frags/admin-layout--lookMenu.vue';
defineProps({ m: Object, c: Object, P_active: null });
</script>
