<template>
<div class="page">
    <FLoginSide :m="m" :c="c"/>

    <main class="main">
        <button class="mode lang-mode" type="button" data-lang-toggle title="切换语言 · Language" aria-label="切换语言 · Language"><svg class="i"><use href="#i-globe"></use></svg><span class="only-zh">EN</span><span class="only-en">中</span></button>
        <button class="mode" type="button" data-mode-toggle aria-label="切换深浅色" title="切换深浅色"><svg class="i"><use href="#i-moon" data-mode-icon></use></svg></button>
        <div class="form">
            <div class="brand"><svg class="logo"><use href="#i-logo"></use></svg>{{ $h.s($h.elvis(m.brandName, '管理后台')) }}</div>
            <div class="shield"><svg class="i"><use href="#i-shield"></use></svg></div>
            <h1>两步验证</h1>
            <p class="sub" data-otp-tip>{{ $h.s($h.cat('打开手机上的身份验证器，输入 ', m.username, ' 的 6 位动态码')) }}</p>

            <div class="msg err shake" v-if="$h.t(m.error)"><svg class="i"><use href="#i-alert"></use></svg><span>{{ $h.s(m.error) }}</span></div>

            <form method="post" :action.attr="$h.ak('/login/2fa')" id="login-form" autocomplete="off">
                <input type="hidden" name="recovery" :value.attr="$h.ak(($h.t($h.eq(m.recovery, true)) ? 'true' : 'false'))">
                <label class="field">
                    <span class="lbl" data-otp-label>动态码</span>
                    <span class="ctl otp">
                        <input type="text" name="code" data-otp inputmode="numeric" maxlength="6" placeholder="000000" autocomplete="one-time-code" autocapitalize="off" spellcheck="false" autofocus required>
                    </span>
                </label>
                <button class="submit" type="submit"><span>验证并登录</span><svg class="i"><use href="#i-arrow"></use></svg></button>
            </form>
            <div class="otp-links">
                <button type="button" class="link-btn" data-otp-toggle>手机不在身边？用恢复码</button>
                <a class="link-btn" :href.attr="$h.ak('/login')">换个账号登录</a>
            </div>
            <div class="form-foot"><svg class="i"><use href="#i-lock"></use></svg>动态码每 30 秒更新一次</div>
        </div>
    </main>
</div>

<FLoginSprite :m="m" :c="c"/>
<svg width="0" height="0" style="position:absolute" aria-hidden="true">
    <symbol id="i-shield" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path><polyline points="9 12 11 14 15 10"></polyline></symbol>
</svg>


</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add('两步验证 · ', $h.elvis(m.brandName, '管理后台')),
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
