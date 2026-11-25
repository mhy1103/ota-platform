<template>
<div class="page">
    <aside class="side">
        <div class="brand"><svg class="logo"><use href="#i-logo"></use></svg>{{ $h.s($h.elvis(m.brandName, '管理后台')) }}</div>
        <div>
            <div class="show" aria-hidden="true">
                <div class="glass g1">
                    <div class="dots"><i></i><i></i><i></i></div>
                    <div class="metric"><span class="ln hi"></span><span class="chip"></span></div>
                    <div class="bars">
                        <i style="--h:34%;--n:0"></i><i style="--h:46%;--n:1"></i><i style="--h:40%;--n:2"></i><i style="--h:58%;--n:3"></i>
                        <i style="--h:52%;--n:4"></i><i style="--h:66%;--n:5"></i><i style="--h:61%;--n:6"></i><i style="--h:74%;--n:7"></i>
                        <i style="--h:70%;--n:8"></i><i style="--h:83%;--n:9"></i><i style="--h:78%;--n:10"></i><i style="--h:96%;--n:11"></i>
                    </div>
                </div>
                <div class="glass g2">
                    <div class="row"><span class="dot"></span><span class="ln" style="width:70%"></span></div>
                    <div class="row"><span class="dot"></span><span class="ln" style="width:52%"></span></div>
                    <div class="row"><span class="dot w"></span><span class="ln" style="width:62%"></span></div>
                </div>
                <div class="glass g3">
                    <svg class="ring" viewBox="0 0 46 46"><circle class="track" cx="23" cy="23" r="19"></circle><circle class="val" cx="23" cy="23" r="19"></circle></svg>
                    <div style="flex:1"><span class="ln hi" style="width:70%;height:10px"></span><span class="ln" style="width:90%;margin-top:8px"></span></div>
                </div>
            </div>
            <div class="hero">
                <h2>{{ $h.s($h.elvis(m.loginTitle, '热更新 · 口令 · 代理')) }}</h2>
                <p>{{ $h.s($h.elvis(m.loginText, '管理员与代理统一登录入口')) }}</p>
            </div>
        </div>
        <div class="foot"><svg class="i"><use href="#i-lock"></use></svg>仅限授权人员访问</div>
    </aside>

    <main class="main">
        <button class="mode lang-mode" type="button" data-lang-toggle title="切换语言 · Language" aria-label="切换语言 · Language"><svg class="i"><use href="#i-globe"></use></svg><span class="only-zh">EN</span><span class="only-en">中</span></button>
        <button class="mode" type="button" data-mode-toggle aria-label="切换深浅色" title="切换深浅色"><svg class="i"><use href="#i-moon" data-mode-icon></use></svg></button>
        <div class="form">
            <div class="brand"><svg class="logo"><use href="#i-logo"></use></svg>{{ $h.s($h.elvis(m.brandName, '管理后台')) }}</div>
            <h1>登录</h1>
            <p class="sub">请输入管理员或代理账号</p>

            <div class="msg err shake" v-if="$h.t($h.p(c.param, 'error'))"><svg class="i"><use href="#i-alert"></use></svg><span>账号或密码错误</span></div>
            <div class="msg err" v-if="$h.t($h.p(c.param, 'disabled'))"><svg class="i"><use href="#i-alert"></use></svg><span>账号已停用，请联系管理员</span></div>
            <div class="msg err" v-if="$h.t($h.p(c.param, 'locked'))"><svg class="i"><use href="#i-clock"></use></svg><span>{{ $h.s($h.cat('密码错误次数太多，已暂停登录，请 ', $h.elvis(m.lockMinutes, 15), ' 分钟后再试')) }}</span></div>
            <div class="msg" v-if="$h.t($h.p(c.param, 'logout'))"><svg class="i"><use href="#i-check"></use></svg><span>已安全退出</span></div>
            <div class="msg" v-if="$h.t($h.p(c.param, 'changed'))"><svg class="i"><use href="#i-check"></use></svg><span>账号或密码已修改，请用新的账号密码登录</span></div>
            <div class="msg" v-if="$h.t($h.p(c.param, 'expired'))"><svg class="i"><use href="#i-info"></use></svg><span>登录已失效（账号被修改或删除），请重新登录</span></div>
            <div class="msg" v-if="$h.t($h.p(c.param, 'revoked'))"><svg class="i"><use href="#i-info"></use></svg><span>这台设备已被退出登录，请重新登录</span></div>
            <div class="msg err" v-if="$h.t($h.p(c.param, 'locked2fa'))"><svg class="i"><use href="#i-alert"></use></svg><span>验证码错误次数太多，请重新登录</span></div>
            <div class="msg" v-if="$h.t($h.p(c.param, 'expired2fa'))"><svg class="i"><use href="#i-clock"></use></svg><span>验证超时，请重新登录</span></div>

            <form method="post" :action.attr="$h.ak('/login')" id="login-form">
                <label class="field">
                    <span class="lbl">账号</span>
                    <span class="ctl">
                        <svg class="i"><use href="#i-user"></use></svg>
                        <input type="text" name="username" placeholder="请输入账号" autocomplete="username" autocapitalize="off" spellcheck="false" autofocus required>
                    </span>
                </label>
                <label class="field">
                    <span class="lbl">密码</span>
                    <span class="ctl">
                        <svg class="i"><use href="#i-lock"></use></svg>
                        <input type="password" name="password" placeholder="请输入密码" autocomplete="current-password" required data-pw>
                        <button class="eye" type="button" data-eye aria-label="显示密码" title="显示密码"><svg class="i"><use href="#i-eye"></use></svg></button>
                    </span>
                    <span class="caps" data-caps hidden><svg class="i"><use href="#i-alert"></use></svg>大写锁定已打开</span>
                </label>
                <label class="remember">
                    <input type="checkbox" name="remember-me" :value.attr="'true'" :checked.attr="''">
                    <span class="box" aria-hidden="true"><svg class="i"><use href="#i-tick"></use></svg></span>
                    <span>{{ $h.s($h.cat('记住我，', $h.elvis(m.rememberDays, 30), ' 天内免登录')) }}</span>
                </label>
                <button class="submit" type="submit"><span>登录</span><svg class="i"><use href="#i-arrow"></use></svg></button>
            </form>
            <div class="form-foot"><svg class="i"><use href="#i-lock"></use></svg>仅限授权人员访问</div>
            <div class="lang-line"><span class="lang-switch"><button type="button" data-lang="zh">中文</button><button type="button" data-lang="en">English</button></span></div>
        </div>
    </main>
</div>

<svg width="0" height="0" style="position:absolute" aria-hidden="true">
    <defs><mask id="logo-cut"><rect width="24" height="24" fill="#fff"></rect><path d="M13.35 7.48A5.2 5.2 0 1 0 17.18 12.95" fill="none" stroke="#000" stroke-width="2.2" stroke-linecap="round"></path><circle cx="16.6" cy="9.3" r="1.6" fill="#000"></circle></mask></defs>
    <symbol id="i-logo" viewBox="0 0 24 24"><rect x="1" y="1" width="22" height="22" rx="7" fill="currentColor" stroke="none" mask="url(#logo-cut)"></rect></symbol>
    <symbol id="i-user" viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"></circle><path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1"></path></symbol>
    <symbol id="i-lock" viewBox="0 0 24 24"><rect x="4" y="11" width="16" height="10" rx="2"></rect><path d="M8 11V7a4 4 0 0 1 8 0v4"></path></symbol>
    <symbol id="i-eye" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></symbol>
    <symbol id="i-eye-off" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"></path><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"></path><path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></symbol>
    <symbol id="i-tick" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"></polyline></symbol>
    <symbol id="i-arrow" viewBox="0 0 24 24"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></symbol>
    <symbol id="i-alert" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></symbol>
    <symbol id="i-check" viewBox="0 0 24 24"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></symbol>
    <symbol id="i-info" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></symbol>
    <symbol id="i-clock" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></symbol>
    <symbol id="i-sun" viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"></circle><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"></path></symbol>
    <symbol id="i-globe" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></symbol>
    <symbol id="i-moon" viewBox="0 0 24 24"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path></symbol>
</svg>


</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add('登录 · ', $h.elvis(m.brandName, '管理后台')),
    css: ["pub/login.css"],
    js: ["pub/login.js"],
    theme: { attr: false, css: (m, c, $h) => m.themeCss }
};
</script>

<script setup>
defineProps({ m: Object, c: Object });
</script>
