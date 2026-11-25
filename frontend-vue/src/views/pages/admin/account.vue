<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'account'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'我的账号'"/>
    <main class="content acct">

        <div class="page-head">
            <div class="page-title">
                <span :class="['who-ava', 'who-ava-lg', $h.av(($h.t($h.z(m.me, 'owner')) ? 'owner' : null))]">{{ $h.s($h.strings.substring($h.p(m.user, 'name'), 0, 1)) }}</span>
                <div>
                    <h1>{{ $h.s($h.p(m.user, 'name')) }}<span :class="['role-badge', $h.av(($h.t($h.z(m.me, 'owner')) ? '' : 'member'))]">{{ $h.s($h.z(m.me, 'roleLabel')) }}</span></h1>
                    <p><span class="mono">{{ $h.s($h.p(m.user, 'username')) }}</span>{{ $h.s($h.cat(' · 上次登录 ', ($h.t(!$h.eq($h.p(m.user, 'lastLoginAt'), null)) ? $h.ui.ago($h.p(m.user, 'lastLoginAt')) : '—'))) }}</p>
                </div>
            </div>
        </div>

        <div class="force-2fa page-note" v-if="$h.t($h.p(m.user, 'mustChangePassword'))">
            <span class="rc-ic"><svg class="i"><use href="#i-key"></use></svg></span>
            <div><b>请先修改初始密码</b><span>这是管理员给你设的密码，改成自己的就能继续用后台。</span></div>
            <a class="btn btn-sm" href="#profile">去修改</a>
        </div>
        <div class="force-2fa page-note" v-if="$h.t(($h.t(m.require2fa) &amp;&amp; $h.t(!$h.t($h.p(m.user, 'totpEnabled')))))">
            <span class="rc-ic"><svg class="i"><use href="#i-shield"></use></svg></span>
            <div><b>系统要求开启两步验证</b><span>超级管理员要求开启两步验证，开好才能用后台的其他功能，一分钟就好。</span></div>
            <button class="btn btn-sm" type="button" data-tfa-setup>现在开启</button>
        </div>

        <section class="metrics">
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-shield"></use></svg></span>两步验证</div>
                <div :class="['metric-value', $h.av(($h.t($h.p(m.user, 'totpEnabled')) ? 'ok-text' : 'warn-text'))]"><span>{{ $h.s(($h.t($h.p(m.user, 'totpEnabled')) ? '已开启' : '未开启')) }}</span></div>
                <div class="metric-foot">{{ $h.s(($h.t($h.p(m.user, 'totpEnabled')) ? $h.cat('开启于 ', $h.ui.date($h.p(m.user, 'totpAt')), ' · 还剩 ', m.recoveryLeft, ' 个恢复码') : '登录时多输一个动态码，密码泄露也安全')) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-monitor"></use></svg></span>登录设备</div>
                <div class="metric-value"><span>{{ $h.s($h.lists.size(m.sessions)) }}</span><small>台</small></div>
                <div class="metric-foot">{{ $h.s(($h.t(m.demoMode) ? '演示账号大家共用，不限台数' : $h.cat('最多 ', m.maxDevices, ' 台同时登录'))) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-grid"></use></svg></span>能操作的 App</div>
                <div class="metric-value"><span>{{ $h.s($h.lists.size(m.myGrants)) }}</span><small>个</small></div>
                <div class="metric-foot">{{ $h.s(($h.t($h.z(m.me, 'owner')) ? '超级管理员，全部权限' : $h.cat('按 App 授权', ($h.t($h.z(m.me, 'canCodes')) ? '，能管理口令' : ''), ($h.t($h.z(m.me, 'canManageAgents')) ? '，能管理代理' : '')))) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-clock"></use></svg></span>这台设备</div>
                <div class="metric-value metric-text"><template v-for="[L_d, L_dStat] in $h.each(m.sessions)"><span v-if="$h.t($h.z(L_d, 'current'))">{{ $h.s($h.z(L_d, 'device')) }}</span></template></div>
                <div class="metric-foot"><template v-for="[L_d, L_dStat] in $h.each(m.sessions)"><span v-if="$h.t($h.z(L_d, 'current'))">{{ $h.s($h.cat('登录于 ', $h.ui.dt($h.z(L_d, 'since')))) }}</span></template></div>
            </div>
        </section>

        <nav class="tabs" data-tabs>
            <a href="#profile" data-tab="profile">资料和密码</a>
            <a href="#security" data-tab="security">登录与安全 <span class="count warn" v-if="!$h.t($h.p(m.user, 'totpEnabled'))">!</span></a>
            <a href="#perms" data-tab="perms">我的权限</a>
            <a href="#prefs" data-tab="prefs">偏好</a>
        </nav>

        <section data-tab-panel="profile">
            <form class="card acct-form" method="post" :action.attr="$h.ak('/admin/account')" autocomplete="off" data-guide="acc-form">
                <div class="card-head"><h2>资料和密码</h2><span class="tail muted small">改了账号或密码要重新登录</span></div>
                <div class="af-grid">
                    <div class="af-col">
                        <div class="af-title"><svg class="i i-sm"><use href="#i-user"></use></svg>资料</div>
                        <div class="field">
                            <label for="me-name">名字</label>
                            <input class="input" id="me-name" name="name" :value.attr="$h.ak($h.p(m.user, 'name'))" maxlength="50" required>
                            <span class="hint">后台和操作日志里显示的名字</span>
                        </div>
                        <div class="field">
                            <label for="me-username">登录账号</label>
                            <input class="input mono" id="me-username" name="username" :value.attr="$h.ak($h.p(m.user, 'username'))" maxlength="32" pattern="[A-Za-z0-9_.\-]{3,32}" title="3–32 位字母、数字或 _ . -" required>
                            <span class="hint">3–32 位字母、数字或 _ . -</span>
                        </div>
                    </div>
                    <div class="af-col">
                        <div class="af-title"><svg class="i i-sm"><use href="#i-key"></use></svg>修改密码<small>不改就留空</small></div>
                        <div class="field">
                            <label for="me-new">新密码</label>
                            <input class="input" id="me-new" name="newPassword" type="password" minlength="8" autocomplete="new-password" placeholder="至少 8 位">
                        </div>
                        <div class="field">
                            <label for="me-confirm">再输一次</label>
                            <input class="input" id="me-confirm" name="confirmPassword" type="password" autocomplete="new-password">
                        </div>
                    </div>
                </div>
                <div class="af-confirm">
                    <div class="field">
                        <label for="me-current">当前密码</label>
                        <input class="input" id="me-current" name="currentPassword" type="password" autocomplete="current-password" required>
                    </div>
                    <span class="hint">改任何信息都要先验证当前密码</span>
                    <button class="btn" type="submit">保存</button>
                </div>
            </form>
        </section>

        <section data-tab-panel="security" id="security" data-hash-target="security" data-guide="acc-security" hidden>
            <div class="rc-banner page-note" v-if="$h.t(!$h.eq(m.recoveryCodes, null))">
                <div class="rc-head">
                    <span class="rc-ic"><svg class="i"><use href="#i-key"></use></svg></span>
                    <div><b>保存好这 8 个恢复码</b><span>手机丢了或换了，用恢复码代替动态码登录，每个只能用一次。关掉就看不到了。</span></div>
                </div>
                <ol class="rc-list"><template v-for="[L_c, L_cStat] in $h.each(m.recoveryCodes)"><li>{{ $h.s(L_c) }}</li></template></ol>
                <div class="btn-row">
                    <button class="btn btn-secondary btn-sm" type="button" :data-copy.attr="$h.av($h.strings.listJoin(m.recoveryCodes, ' '))"><svg class="i i-sm"><use href="#i-copy"></use></svg>复制全部</button>
                    <button class="btn btn-secondary btn-sm" type="button" data-rc-download :data-codes.attr="$h.av($h.strings.listJoin(m.recoveryCodes, ' '))" :data-user.attr="$h.av($h.p(m.user, 'username'))"><svg class="i i-sm"><use href="#i-download"></use></svg>下载 .txt</button>
                </div>
            </div>

            <div class="card sec-card section-gap">
                <div class="sec-row">
                    <span :class="['sec-ic', $h.av(($h.t($h.p(m.user, 'totpEnabled')) ? 'on' : null))]"><svg class="i"><use href="#i-lock"></use></svg></span>
                    <div class="sec-text">
                        <b>两步验证
                            <span class="badge badge-solid" v-if="$h.t($h.p(m.user, 'totpEnabled'))"><span class="dot"></span>已开启</span>
                            <span class="badge badge-line" v-if="!$h.t($h.p(m.user, 'totpEnabled'))"><span class="dot"></span>未开启</span>
                        </b>
                        <span>登录时除了密码，还要输入手机验证器里的 6 位动态码。Google、微软验证器、1Password 都能用。</span>
                        <span class="muted-line" v-if="$h.t($h.p(m.user, 'totpEnabled'))">{{ $h.s($h.add($h.add($h.add($h.add('开启于 ', $h.ui.dt($h.p(m.user, 'totpAt'))), ' · 还剩 '), m.recoveryLeft), ' 个恢复码')) }}</span>
                    </div>
                    <div class="sec-actions">
                        <button class="btn btn-sm" type="button" data-tfa-setup v-if="!$h.t($h.p(m.user, 'totpEnabled'))"><svg class="i i-sm"><use href="#i-shield"></use></svg>开启</button>
                        <template v-if="$h.t($h.p(m.user, 'totpEnabled'))">
                            <button class="btn btn-secondary btn-sm" type="button" data-open="rc-dialog">重新生成恢复码</button>
                            <button class="btn btn-ghost btn-sm" type="button" data-open="tfa-off-dialog">关闭</button>
                        </template>
                    </div>
                </div>
            </div>

            <div class="sec-grid">
                <div class="card sec-card">
                    <div class="card-head">
                        <h2>登录设备</h2>
                        <form class="tail" method="post" :action.attr="$h.ak('/admin/account/sessions/revoke-others')" data-confirm="退出其他所有设备？" data-confirm-body="除了你现在用的这台，其他电脑和手机上登录的后台都会马上退出。" data-confirm-ok="全部退出" v-if="$h.t($h.gt($h.lists.size(m.sessions), 1))">
                            <button class="btn btn-secondary btn-sm" type="submit"><svg class="i i-sm"><use href="#i-logout"></use></svg>退出其他设备</button>
                        </form>
                    </div>
                    <div class="sec-block">
                        <p class="sec-note">{{ $h.s($h.cat($h.lists.size(m.sessions), ' 台设备正在登录', ($h.t(m.demoMode) ? '，演示账号大家共用，不限台数' : $h.cat('，最多 ', m.maxDevices, ' 台；满了再登录时会让你选退出哪台')))) }}</p>
                        <ul class="dev-list">
                            <template v-for="[L_d, L_dStat] in $h.each(m.sessions)"><template v-if="L_dStat.index">
                            </template><li :class="$h.av(($h.t($h.z(L_d, 'current')) ? 'current' : null))">
                                <span class="dev-ic"><svg class="i"><use :href.attr="$h.ak(($h.t($h.z(L_d, 'mobile')) ? '#i-phone' : '#i-monitor'))"></use></svg></span>
                                <span class="dev-text">
                                    <b>{{ $h.s($h.z(L_d, 'device')) }}</b>
                                    <small>{{ $h.s($h.add($h.add($h.add('登录于 ', $h.ui.dt($h.z(L_d, 'since'))), ' · '), ($h.t($h.z(L_d, 'current')) ? '正在使用' : $h.add('最近活动 ', $h.ui.ago($h.z(L_d, 'seen')))))) }}</small>
                                </span>
                                <span class="badge badge-solid" v-if="$h.t($h.z(L_d, 'current'))"><span class="dot"></span>当前设备</span>
                                <form method="post" :action.attr="$h.ak('/admin/account/sessions/revoke')" data-confirm="退出这台设备？" data-confirm-body="那台设备会马上退出，要重新登录。" data-confirm-ok="退出" v-if="!$h.t($h.z(L_d, 'current'))">
                                    <input type="hidden" name="id" :value.attr="$h.ak($h.z(L_d, 'key'))">
                                    <button class="btn btn-ghost btn-sm" type="submit">退出</button>
                                </form>
                            </li></template>
                        </ul>
                    </div>
                </div>

                <div class="card sec-card">
                    <div class="card-head"><h2>最近登录</h2><span class="tail muted small">最近 10 次，成功和失败都记，不记录 IP</span></div>
                    <div class="sec-block">
                        <div class="list-none" v-if="$h.t($h.lists.isEmpty(m.logins))">还没有登录记录</div>
                        <ul class="login-list" v-if="!$h.t($h.lists.isEmpty(m.logins))">
                            <template v-for="[L_r, L_rStat] in $h.each(m.logins)"><template v-if="L_rStat.index">
                            </template><li :class="$h.av(($h.t($h.p(L_r, 'success')) ? '' : 'fail'))">
                                <span class="ll-dot"></span>
                                <span class="ll-dev"><svg class="i i-sm"><use :href.attr="$h.ak(($h.t($h.p(L_r, 'mobile')) ? '#i-phone' : '#i-monitor'))"></use></svg><span>{{ $h.s($h.p(L_r, 'device')) }}</span></span>
                                <span class="ll-res">{{ $h.s(($h.t($h.p(L_r, 'success')) ? '登录成功' : $h.elvis($h.p(L_r, 'reason'), '登录失败'))) }}</span>
                                <span class="ll-at" :title.attr="$h.av($h.ui.full($h.p(L_r, 'at')))">{{ $h.s($h.ui.ago($h.p(L_r, 'at'))) }}</span>
                            </li></template>
                        </ul>
                    </div>
                </div>
            </div>
        </section>

        <section data-tab-panel="perms" hidden>
            <div class="card perm-card" data-guide="acc-perms">
                <div class="card-head"><h2>我的权限</h2><span class="tail muted small">{{ $h.s(($h.t($h.z(m.me, 'owner')) ? '超级管理员拥有全部权限' : '要调整请联系超级管理员')) }}</span></div>
                <div class="empty" v-if="$h.t($h.lists.isEmpty(m.myGrants))">
                    <div class="empty-icon"><svg class="i"><use href="#i-lock"></use></svg></div>
                    <b>还没有分配 App</b>请联系超级管理员在「管理员」里给你分配 App 和权限
                </div>
                <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty(m.myGrants))">
                    <table class="compact perm-matrix">
                        <thead><tr><th>App</th><template v-for="[L_p, L_pStat] in $h.each(m.perms)"><th class="nowrap" :title.attr="$h.av($h.p(L_p, 'desc'))">{{ $h.s($h.p(L_p, 'label')) }}</th></template></tr></thead>
                        <tbody data-collapse="8" data-collapse-unit="个 App">
                        <template v-for="[L_g, L_gStat] in $h.each(m.myGrants)"><template v-if="L_gStat.index">
                        </template><tr>
                            <td class="two-line"><b>{{ $h.s($h.p($h.z(L_g, 'app'), 'name')) }}</b><small class="mono">{{ $h.s($h.p($h.z(L_g, 'app'), 'slug')) }}</small></td>
                            <template v-for="[L_p, L_pStat] in $h.each(m.perms)"><template v-if="L_pStat.index">
                            </template><td style="text-align:center">
                                <span class="pm-yes" v-if="$h.t($h.c($h.z(L_g, 'perms'), 'contains', [L_p]))"><svg class="i i-sm"><use href="#i-tick"></use></svg></span>
                                <span class="muted" v-if="!$h.t($h.c($h.z(L_g, 'perms'), 'contains', [L_p]))">—</span>
                            </td></template>
                        </tr></template>
                        </tbody>
                    </table>
                </div>
                <div class="perm-global">
                    <div class="pg-title">全局权限</div>
                    <ul>
                        <li :class="$h.av(($h.t($h.z(m.me, 'canCodes')) ? null : 'off'))"><svg class="i i-sm"><use :href.attr="$h.av(($h.t($h.z(m.me, 'canCodes')) ? '#i-tick' : '#i-lock'))"></use></svg><span>口令与激活<small>新建、编辑、导入口令，导出激活记录，同意代理的口令申请，所有 App 通用</small></span></li>
                        <li :class="$h.av(($h.t($h.z(m.me, 'canManageAgents')) ? null : 'off'))"><svg class="i i-sm"><use :href.attr="$h.av(($h.t($h.z(m.me, 'canManageAgents')) ? '#i-tick' : '#i-lock'))"></use></svg><span>代理管理<small>新建代理、设置扣量、发公告</small></span></li>
                        <li :class="$h.av(($h.t($h.z(m.me, 'owner')) ? null : 'off'))"><svg class="i i-sm"><use :href.attr="$h.av(($h.t($h.z(m.me, 'owner')) ? '#i-tick' : '#i-lock'))"></use></svg><span>管理账号<small>添加成员，分配 App 和权限</small></span></li>
                        <li :class="$h.av(($h.t($h.z(m.me, 'owner')) ? null : 'off'))"><svg class="i i-sm"><use :href.attr="$h.av(($h.t($h.z(m.me, 'owner')) ? '#i-tick' : '#i-lock'))"></use></svg><span>系统配置<small>外观、登录安全、令牌、存储和完整备份</small></span></li>
                    </ul>
                </div>
            </div>
        </section>

        <section data-tab-panel="prefs" hidden>
            <div class="card">
                <div class="card-head"><h2>偏好</h2><span class="tail muted small">只对你自己生效</span></div>
                <div class="pref-row">
                    <div class="pref-text"><b>主题</b><span>浅色、深色，或者跟着系统的深浅色自动切换</span></div>
                    <div class="choice inline" data-look="mode">
                        <button type="button" data-v="light"><svg class="i"><use href="#i-sun"></use></svg>浅色</button>
                        <button type="button" data-v="dark"><svg class="i"><use href="#i-moon"></use></svg>深色</button>
                        <button type="button" data-v="system"><svg class="i"><use href="#i-monitor"></use></svg>跟随系统</button>
                    </div>
                </div>
                <div class="pref-row">
                    <div class="pref-text"><b>侧栏</b><span>只改你自己的；选带「默认」的就跟着系统配置走</span></div>
                    <div class="choice inline" data-look="sidebar">
                        <button type="button" data-v="theme"><svg class="i"><use href="#i-side-theme"></use></svg>主题色</button>
                        <button type="button" data-v="dark"><svg class="i"><use href="#i-side-dark"></use></svg>深色</button>
                        <button type="button" data-v="light"><svg class="i"><use href="#i-side-light"></use></svg>浅色</button>
                    </div>
                </div>
                <div class="pref-row">
                    <div class="pref-text"><b>信息密度</b><span>紧凑模式行距更小，一屏能看到更多数据</span></div>
                    <div class="choice inline" data-look="density">
                        <button type="button" data-v="comfortable"><svg class="i"><use href="#i-rows"></use></svg>标准</button>
                        <button type="button" data-v="compact"><svg class="i"><use href="#i-rows-tight"></use></svg>紧凑</button>
                    </div>
                </div>
                <div class="pref-row">
                    <div class="pref-text"><b>顶部标签栏</b><span>打开过的页面排在顶栏下面，方便来回切；用不上可以收起来，页面多出一行</span></div>
                    <div class="choice inline" data-look="tabs">
                        <button type="button" data-v="show"><svg class="i"><use href="#i-eye"></use></svg>显示</button>
                        <button type="button" data-v="hide"><svg class="i"><use href="#i-x"></use></svg>隐藏</button>
                    </div>
                </div>
                <div class="pref-row">
                    <div class="pref-text"><b>动画</b><span>关掉后切页、弹窗、图表都不再有动画，老电脑和远程桌面上更流畅</span></div>
                    <div class="choice inline" data-look="motion">
                        <button type="button" data-v="on"><svg class="i"><use href="#i-sparkle"></use></svg>有动画</button>
                        <button type="button" data-v="off"><svg class="i"><use href="#i-zap"></use></svg>无动画</button>
                    </div>
                </div>
                <div class="pref-row">
                    <div class="pref-text"><b>语言</b><span>只对这个浏览器生效，顶栏的地球图标也能切换</span></div>
                    <div class="choice inline" data-lang-choice>
                        <button type="button" data-lang="zh"><span class="lang-mark">中</span>中文</button>
                        <button type="button" data-lang="en"><span class="lang-mark">A</span>English</button>
                    </div>
                </div>
                <div class="pref-row">
                    <div class="pref-text"><b>引导</b><span>看过的引导不会再自动出现，可以在这里重新打开</span></div>
                    <form method="post" :action.attr="$h.ak('/admin/account/guide/reset')">
                        <button class="btn btn-secondary btn-sm" type="submit"><svg class="i i-sm"><use href="#i-refresh"></use></svg>重新显示引导</button>
                    </form>
                </div>
                <div class="card-foot"><span class="hint">偏好存在你的账号上，换电脑、换浏览器登录都一样</span></div>
            </div>
        </section>

        <dialog id="tfa-dialog" class="wide" :data-retry.attr="$h.av(($h.t($h.eq(m.totpRetry, true)) ? 'true' : null))">
            <form method="post" :action.attr="$h.ak('/admin/account/2fa/enable')" autocomplete="off">
                <div class="dlg-head"><h3>开启两步验证</h3><p>用手机验证器扫码，再输入它显示的 6 位动态码。</p></div>
                <div class="dlg-body">
                    <div class="tfa-setup">
                        <div class="tfa-qr" data-tfa-qr><span class="tfa-loading"></span></div>
                        <ol class="tfa-steps">
                            <li><b>安装身份验证器</b><span>Google Authenticator、Microsoft Authenticator、1Password 等都可以</span></li>
                            <li><b>扫描左边的二维码</b><span>扫不了就手动输入密钥：</span><span class="tfa-secret"><code data-tfa-secret>…</code><button class="icon-btn" type="button" data-copy="" data-copy-secret title="复制密钥" aria-label="复制密钥"><svg class="i i-sm"><use href="#i-copy"></use></svg></button></span></li>
                            <li><b>输入 6 位动态码</b>
                                <input class="input tfa-code" name="code" inputmode="numeric" maxlength="6" placeholder="000000" autocomplete="one-time-code" required pattern="\d{6}">
                            </li>
                        </ol>
                    </div>
                    <div class="dlg-error" data-tfa-error hidden></div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">确认开启</button>
                </div>
            </form>
        </dialog>

        <dialog id="tfa-off-dialog">
            <form method="post" :action.attr="$h.ak('/admin/account/2fa/disable')" autocomplete="off">
                <div class="dlg-head"><h3>关闭两步验证</h3><p>关闭后只用密码就能登录，账号安全性会降低。</p></div>
                <div class="dlg-body">
                    <div class="field"><label for="off-pw">登录密码</label><input class="input" id="off-pw" name="password" type="password" autocomplete="current-password" required></div>
                    <div class="field"><label for="off-code">动态码或恢复码</label><input class="input mono" id="off-code" name="code" autocomplete="one-time-code" required placeholder="6 位动态码，或 xxxxx-xxxxx"></div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn btn-danger" type="submit">关闭两步验证</button>
                </div>
            </form>
        </dialog>

        <dialog id="rc-dialog">
            <form method="post" :action.attr="$h.ak('/admin/account/2fa/recovery')" autocomplete="off">
                <div class="dlg-head"><h3>重新生成恢复码</h3><p>会生成 8 个新的恢复码，旧的全部作废。</p></div>
                <div class="dlg-body">
                    <div class="field"><label for="rc-pw">登录密码</label><input class="input" id="rc-pw" name="password" type="password" autocomplete="current-password" required></div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">重新生成</button>
                </div>
            </form>
        </dialog>

    </main>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('我的账号'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
    css: ["app/ui.css"],
    js: ["app/ui.js"],
    theme: { attr: true, css: (m, c, $h) => m.themeCss }
};
</script>

<script setup>
import FAdminLayoutOverlays from '../../frags/admin-layout--overlays.vue';
import FAdminLayoutSidebar from '../../frags/admin-layout--sidebar.vue';
import FAdminLayoutTopbar from '../../frags/admin-layout--topbar.vue';
defineProps({ m: Object, c: Object });
</script>
