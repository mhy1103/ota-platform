<template>
<div class="page">
    <FLoginSide :m="m" :c="c"/>

    <main class="main">
        <button class="mode lang-mode" type="button" data-lang-toggle title="切换语言 · Language" aria-label="切换语言 · Language"><svg class="i"><use href="#i-globe"></use></svg><span class="only-zh">EN</span><span class="only-en">中</span></button>
        <button class="mode" type="button" data-mode-toggle aria-label="切换深浅色" title="切换深浅色"><svg class="i"><use href="#i-moon" data-mode-icon></use></svg></button>
        <div class="form form-wide">
            <div class="brand"><svg class="logo"><use href="#i-logo"></use></svg>{{ $h.s($h.elvis(m.brandName, '管理后台')) }}</div>
            <div class="shield"><svg class="i"><use href="#i-devices"></use></svg></div>
            <h1>登录设备已满</h1>
            <p class="sub">{{ $h.s($h.cat(m.username, ' 已经在 ', $h.lists.size(m.devices), ' 台设备上登录，最多 ', m.max, ' 台。选 ', m.need, ' 台退出，就能在这台设备上登录。')) }}</p>

            <div class="msg err shake" v-if="$h.t(m.error)"><svg class="i"><use href="#i-alert"></use></svg><span>{{ $h.s(m.error) }}</span></div>

            <form method="post" :action.attr="$h.ak('/login/devices')" id="login-form" :data-need.attr="$h.av(m.need)">
                <ul class="dev-pick">
                    <template v-for="[L_d, L_dStat] in $h.each(m.devices)"><template v-if="L_dStat.index">
                    </template><li><label>
                        <input type="checkbox" name="keys" :value.attr="$h.ak($h.p(L_d, 'key'))">
                        <span class="dp-ic"><svg class="i"><use :href.attr="$h.ak(($h.t($h.p(L_d, 'mobile')) ? '#i-phone' : '#i-monitor'))"></use></svg></span>
                        <span class="dp-text"><b>{{ $h.s($h.p(L_d, 'device')) }}</b><small>{{ $h.s($h.cat('最近使用 ', $h.ui.ago($h.p(L_d, 'seen')), ' · 登录于 ', $h.ui.dt($h.p(L_d, 'since')))) }}</small></span>
                        <span class="dp-check"><svg class="i"><use href="#i-check"></use></svg></span>
                    </label></li></template>
                </ul>
                <button class="submit" type="submit" data-role="submit" disabled><span data-role="label">{{ $h.s($h.cat('再选 ', m.need, ' 台')) }}</span><svg class="i"><use href="#i-arrow"></use></svg></button>
            </form>
            <div class="otp-links">
                <span class="hint-text">退出的设备下次打开页面会回到登录页</span>
                <a class="link-btn" :href.attr="$h.ak('/login?cancel')">不登录了</a>
            </div>
        </div>
    </main>
</div>

<FLoginSprite :m="m" :c="c"/>
<svg width="0" height="0" style="position:absolute" aria-hidden="true">
    <symbol id="i-devices" viewBox="0 0 24 24"><rect x="2" y="4" width="14" height="10" rx="2"></rect><path d="M6 18h6"></path><rect x="17" y="8" width="5" height="12" rx="1.5"></rect></symbol>
    <symbol id="i-monitor" viewBox="0 0 24 24"><rect x="2" y="3" width="20" height="14" rx="2"></rect><path d="M8 21h8M12 17v4"></path></symbol>
    <symbol id="i-phone" viewBox="0 0 24 24"><rect x="6" y="2" width="12" height="20" rx="2.5"></rect><path d="M11 18h2"></path></symbol>
    <symbol id="i-check" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"></polyline></symbol>
</svg>


</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add('选择退出的设备 · ', $h.elvis(m.brandName, '管理后台')),
    css: ["pub/login.css"],
    js: ["pub/login.js"],
    theme: { attr: false, css: (m, c, $h) => m.themeCss }
};
</script>

<script setup>
import FLoginSide from '../frags/login--side.vue';
import FLoginSprite from '../frags/login--sprite.vue';
defineProps({ m: Object, c: Object });
</script>
