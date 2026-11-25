<template>
<div class="sh">
    <div class="sh-glow" aria-hidden="true"><i></i><i></i></div>
    <main class="sh-card">
        <div :class="['sh-icon', $h.av(($h.t($h.eq(m.shareState, 'locked')) ? null : 'off'))]" aria-hidden="true">
            <svg viewBox="0 0 24 24" v-if="$h.t($h.eq(m.shareState, 'locked'))"><rect x="4.5" y="10.5" width="15" height="10" rx="2.6"></rect><path d="M8 10.5V7.8a4 4 0 0 1 8 0v2.7"></path><circle cx="12" cy="15.5" r="1.2"></circle></svg>
            <svg viewBox="0 0 24 24" v-if="$h.t($h.eq(m.shareState, 'expired'))"><circle cx="12" cy="12" r="8.5"></circle><path d="M12 7.5V12l3 2"></path></svg>
            <svg viewBox="0 0 24 24" v-if="$h.t(($h.t(!$h.eq(m.shareState, 'locked')) &amp;&amp; $h.t(!$h.eq(m.shareState, 'expired'))))"><circle cx="12" cy="12" r="8.5"></circle><path d="M6 6l12 12"></path></svg>
        </div>

        <template v-if="$h.t($h.eq(m.shareState, 'locked'))">
            <h1>{{ $h.s($h.cat(m.shareName, ' · 数据大屏')) }}</h1>
            <p class="sh-sub">{{ $h.s($h.cat(m.shareBy, ' 分享给你的，输入访问密码就能看')) }}</p>
            <div class="sh-msg" v-if="$h.t($h.p(c.param, 'wrong'))"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"></circle><path d="M12 7.5v5.5"></path><path d="M12 16.5h.01"></path></svg><span>访问密码不对，再试一次</span></div>
            <div class="sh-msg" v-if="$h.t($h.p(c.param, 'locked'))"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"></circle><path d="M12 7.5V12l3 2"></path></svg><span>试错太多次了，10 分钟后再试</span></div>
            <form method="post" class="sh-form" id="share-form">
                <label class="sh-field">
                    <span class="sh-ctl">
                        <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4.5" y="10.5" width="15" height="10" rx="2.6"></rect><path d="M8 10.5V7.8a4 4 0 0 1 8 0v2.7"></path></svg>
                        <input type="password" name="password" placeholder="访问密码" aria-label="访问密码" autocomplete="off" maxlength="32" required autofocus data-pw>
                        <button class="sh-eye" type="button" data-eye aria-label="显示密码" title="显示密码"><svg viewBox="0 0 24 24"><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"></path><circle cx="12" cy="12" r="2.8"></circle></svg></button>
                    </span>
                </label>
                <button class="sh-go" type="submit"><span>查看数据大屏</span><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14"></path><path d="M13 6l6 6-6 6"></path></svg></button>
            </form>
            <p class="sh-foot" v-if="$h.t(!$h.eq(m.shareUntil, null))">{{ $h.s($h.cat('分享有效期至 ', $h.ui.dt(m.shareUntil))) }}</p>
        </template>

        <template v-if="$h.t(!$h.eq(m.shareState, 'locked'))">
            <h1>{{ $h.s(($h.t($h.eq(m.shareState, 'expired')) ? '分享已过期' : ($h.t($h.eq(m.shareState, 'revoked')) ? '分享已取消' : ($h.t($h.eq(m.shareState, 'owner')) ? '分享已失效' : '分享链接不存在')))) }}</h1>
            <p class="sh-sub">{{ $h.s(($h.t($h.eq(m.shareState, 'expired')) ? $h.cat('这个数据大屏的分享在 ', $h.ui.dt(m.shareUntil), ' 到期了，找分享给你的人重新分享一个') : ($h.t($h.eq(m.shareState, 'revoked')) ? '分享的人已经取消了这个分享，找 TA 重新分享一个' : ($h.t($h.eq(m.shareState, 'owner')) ? '分享的人现在看不了这个数据大屏了，分享跟着失效' : '链接可能抄错了，或者分享已经删掉了')))) }}</p>
        </template>
    </main>
    <div class="sh-brand">{{ $h.s($h.elvis(m.brandName, '管理后台')) }}</div>
</div>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.cat(($h.t($h.eq(m.shareState, 'locked')) ? $h.cat(m.shareName, ' · 数据大屏') : '分享已失效'), ' · ', $h.elvis(m.brandName, '管理后台')),
    css: ["pub/share.css"],
    js: ["pub/share.js"],
    theme: { attr: false, css: (m, c, $h) => m.themeCss }
};
</script>

<script setup>
defineProps({ m: Object, c: Object });
</script>
