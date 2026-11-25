<template><aside class="sidebar" :data-page.attr="$h.av(P_active)" :data-guide-seen.attr="$h.av($h.access.guide())">
    <a class="brand" :href.attr="$h.ak('/admin')">
        <svg class="brand-logo"><use href="#i-logo"></use></svg>
        <span>{{ $h.s($h.elvis(m.brandName, 'OTA 管理后台')) }}<small>热更新 · 口令 · 代理</small></span>
    </a>
    <template v-for="L_back in [($h.t($h.eq(P_active, 'overview')) ? '/admin' : $h.add('/admin/', P_active))]"><div class="app-switch" data-app-switch>
        <template v-if="$h.t(($h.t(!$h.eq(m.apps, null)) &amp;&amp; $h.t(!$h.t($h.lists.isEmpty(m.apps)))))">
            <button class="app-current" type="button" aria-haspopup="true" aria-expanded="false" data-app-toggle :title.attr="$h.av(($h.t(!$h.eq(m.app, null)) ? $h.add($h.add('当前 App：', $h.p(m.app, 'name')), '，点击切换') : '选择 App'))">
                <span :class="['app-ava', $h.av(($h.t(($h.t(!$h.eq(m.app, null)) &amp;&amp; $h.t(!$h.eq($h.p(m.app, 'iconVersion'), null)))) ? 'has-icon' : null))]"><img :src.attr="$h.ak($h.cat('/admin/apps/', $h.p(m.app, 'id'), '/icon?v=', $h.p(m.app, 'iconVersion')))" alt="" v-if="$h.t(($h.t(!$h.eq(m.app, null)) &amp;&amp; $h.t(!$h.eq($h.p(m.app, 'iconVersion'), null))))"><template v-if="!$h.t(($h.t(!$h.eq(m.app, null)) &amp;&amp; $h.t(!$h.eq($h.p(m.app, 'iconVersion'), null))))">{{ $h.s(($h.t(!$h.eq(m.app, null)) ? $h.strings.substring($h.p(m.app, 'name'), 0, 1) : '?')) }}</template></span>
                <span class="app-current-text">
                    <small>当前 App</small>
                    <b>{{ $h.s(($h.t(!$h.eq(m.app, null)) ? $h.p(m.app, 'name') : '选择 App')) }}</b>
                </span>
                <svg class="i i-sm app-chev"><use href="#i-updown"></use></svg>
            </button>
            <div class="app-menu" data-app-menu hidden>
                <label class="app-menu-search" v-if="$h.t($h.gt($h.lists.size(m.apps), 5))">
                    <svg class="i i-sm"><use href="#i-search"></use></svg>
                    <input type="search" placeholder="搜索名称或标识" data-app-search aria-label="搜索 App">
                </label>
                <div class="app-menu-title">切换 App</div>
                <div class="app-menu-list">
                    <template v-for="[L_a, L_aStat] in $h.each(m.apps)"><a :class="['app-opt', $h.av(($h.t(($h.t(!$h.eq(m.app, null)) &amp;&amp; $h.t($h.eq($h.p(L_a, 'id'), $h.p(m.app, 'id'))))) ? 'on' : null))]" :href.attr="$h.ak($h.link('/admin/use', [['app', $h.p(L_a, 'slug')], ['back', L_back]]))" :data-search.attr="$h.av($h.add($h.add($h.p(L_a, 'name'), ' '), $h.p(L_a, 'slug')))">
                        <span :class="['app-ava sm', $h.av(($h.t(!$h.eq($h.p(L_a, 'iconVersion'), null)) ? 'has-icon' : null))]"><img :src.attr="$h.ak($h.cat('/admin/apps/', $h.p(L_a, 'id'), '/icon?v=', $h.p(L_a, 'iconVersion')))" alt="" v-if="!$h.eq($h.p(L_a, 'iconVersion'), null)"><template v-if="$h.eq($h.p(L_a, 'iconVersion'), null)">{{ $h.s($h.strings.substring($h.p(L_a, 'name'), 0, 1)) }}</template></span>
                        <span class="app-opt-text">
                            <b>{{ $h.s($h.p(L_a, 'name')) }}</b>
                            <small><span>{{ $h.s($h.p(L_a, 'slug')) }}</span><i :class="['mode-dot', $h.av(($h.t($h.p(L_a, 'redeemOpen')) ? 'b' : null))]"></i><span>{{ $h.s(($h.t($h.p(L_a, 'redeemOpen')) ? 'B 面' : 'A 面')) }}</span></small>
                        </span>
                        <svg class="i i-sm app-tick" v-if="$h.t(($h.t(!$h.eq(m.app, null)) &amp;&amp; $h.t($h.eq($h.p(L_a, 'id'), $h.p(m.app, 'id')))))"><use href="#i-tick"></use></svg>
                    </a></template>
                    <div class="app-menu-none" data-app-none hidden>没有匹配的 App</div>
                </div>
                <div class="app-menu-foot">
                    <a :href.attr="$h.ak('/admin/apps')"><svg class="i i-sm"><use href="#i-package"></use></svg>所有 App</a>
                    <a :href.attr="$h.ak('/admin/apps#new')"><svg class="i i-sm"><use href="#i-plus"></use></svg>新建 App</a>
                </div>
            </div>
        </template>
        <a class="app-switch-new" :href.attr="$h.ak('/admin/apps#new')" v-if="$h.t(($h.t($h.eq(m.apps, null)) || $h.t($h.lists.isEmpty(m.apps))))"><svg class="i i-sm"><use href="#i-plus"></use></svg>新建第一个 App</a>
    </div></template>
    <nav class="nav side-scroll">
        <a :href.attr="$h.ak('/admin')" :class="$h.av(($h.t($h.eq(P_active, 'overview')) ? 'active' : null))"><svg class="i"><use href="#i-grid"></use></svg>当前概览</a>
        <a :href.attr="$h.ak('/admin/screen')" v-if="$h.t(!$h.eq(m.app, null))"><svg class="i"><use href="#i-monitor"></use></svg>数据大屏</a>
        <div class="nav-label">运营</div>
        <a :href.attr="$h.ak('/admin/codes')" :class="$h.av(($h.t($h.eq(P_active, 'codes')) ? 'active' : null))"><svg class="i"><use href="#i-key"></use></svg>口令管理</a>
        <a :href.attr="$h.ak('/admin/activations')" :class="$h.av(($h.t($h.eq(P_active, 'activations')) ? 'active' : null))"><svg class="i"><use href="#i-activity"></use></svg>激活记录</a>
        <a :href.attr="$h.ak('/admin/agents')" :class="$h.av(($h.t($h.eq(P_active, 'agents')) ? 'active' : null))" v-if="$h.t($h.access.agents())"><svg class="i"><use href="#i-users"></use></svg>代理管理<span class="nav-badge" :title.attr="$h.av($h.cat(m.navRequests, ' 条口令申请待审核'))" v-if="$h.t(($h.t(!$h.eq(m.navRequests, null)) &amp;&amp; $h.t($h.gt(m.navRequests, 0))))">{{ $h.s(m.navRequests) }}</span></a>
        <div class="nav-label">发布</div>
        <a :href.attr="$h.ak('/admin/releases')" :class="$h.av(($h.t($h.eq(P_active, 'releases')) ? 'active' : null))"><svg class="i"><use href="#i-layers"></use></svg>版本发布<span class="nav-badge" :title.attr="$h.av($h.cat(m.navAlerts, ' 个线上版本启动失败偏多'))" v-if="$h.t(($h.t(!$h.eq(m.navAlerts, null)) &amp;&amp; $h.t($h.gt(m.navAlerts, 0))))">{{ $h.s(m.navAlerts) }}</span></a>
        <a :href.attr="$h.ak('/admin/devices')" :class="$h.av(($h.t($h.eq(P_active, 'devices')) ? 'active' : null))"><svg class="i"><use href="#i-phone"></use></svg>设备统计</a>
        <div class="nav-label">App</div>
        <a :href.attr="$h.ak('/admin/app')" :class="$h.av(($h.t($h.eq(P_active, 'app')) ? 'active' : null))"><svg class="i"><use href="#i-sliders"></use></svg>App 设置</a>
        <a :href.attr="$h.ak('/admin/signing')" :class="$h.av(($h.t($h.eq(P_active, 'signing')) ? 'active' : null))"><svg class="i"><use href="#i-shield"></use></svg>签名配置<span :class="['nav-badge dot', $h.av(($h.t($h.eq($h.p(m.signing, 'level'), 'error')) ? '' : 'warn'))]" :title.attr="$h.av($h.p(m.signing, 'headline'))" v-if="$h.t(($h.t(!$h.eq(m.signing, null)) &amp;&amp; $h.t(!$h.eq($h.p(m.signing, 'level'), 'ok'))))"></span></a>
        <div class="nav-label">系统</div>
        <a :href.attr="$h.ak('/admin/apps')" :class="$h.av(($h.t($h.eq(P_active, 'apps')) ? 'active' : null))"><svg class="i"><use href="#i-package"></use></svg>所有 App</a>
        <a :href.attr="$h.ak('/admin/admins')" :class="$h.av(($h.t($h.eq(P_active, 'admins')) ? 'active' : null))" v-if="$h.t($h.access.owner())"><svg class="i"><use href="#i-user-cog"></use></svg>管理账号</a>
        <a :href.attr="$h.ak('/admin/logs')" :class="$h.av(($h.t($h.eq(P_active, 'logs')) ? 'active' : null))"><svg class="i"><use href="#i-file"></use></svg>操作日志</a>
        <a :href.attr="$h.ak('/admin/settings')" :class="$h.av(($h.t($h.eq(P_active, 'settings')) ? 'active' : null))" v-if="$h.t($h.access.owner())"><svg class="i"><use href="#i-gear"></use></svg>系统配置</a>
        <a :href.attr="$h.ak('/admin/settings')" :class="$h.av(($h.t($h.eq(P_active, 'settings')) ? 'active' : null))" v-if="!$h.t($h.access.owner())"><svg class="i"><use href="#i-palette"></use></svg>外观</a>
        <a :href.attr="$h.ak('/admin/docs')" :class="$h.av(($h.t($h.eq(P_active, 'docs')) ? 'active' : null))"><svg class="i"><use href="#i-code"></use></svg>API 文档</a>
    </nav>
    <template v-for="L_me in [$h.access.me()]"><div class="side-foot">
        <a :class="['side-user', $h.av(($h.t($h.eq(P_active, 'account')) ? 'on' : null))]" :href.attr="$h.ak('/admin/account')" title="我的账号">
            <span class="avatar">{{ $h.s(($h.t(!$h.eq(L_me, null)) ? $h.strings.substring($h.z(L_me, 'name'), 0, 1) : '?')) }}</span>
            <span class="side-user-text"><b>{{ $h.s(($h.t(!$h.eq(L_me, null)) ? $h.z(L_me, 'name') : '')) }}</b><small>{{ $h.s(($h.t(!$h.eq(L_me, null)) ? $h.z(L_me, 'roleLabel') : '')) }}</small></span>
        </a>
        <form method="post" :action.attr="$h.ak('/logout')">
            <button class="dark-btn" type="submit" title="退出登录" aria-label="退出登录"><svg class="i"><use href="#i-logout"></use></svg></button>
        </form>
    </div></template>
</aside></template>

<script setup>
defineProps({ m: Object, c: Object, P_active: null });
</script>
