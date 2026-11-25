<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'settings'"/>
<template v-for="L_owner in [$h.t($h.access.owner())]"><div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="($h.t(L_owner) ? '系统配置' : '外观')"/>
    <main class="content">

        <div class="page-head">
            <div class="page-title"><div>
                <h1>{{ $h.s(($h.t(L_owner) ? '系统配置' : '外观')) }}</h1>
                <p v-if="$h.t(L_owner)">外观、登录安全、令牌、存储和域名，对所有 App 生效。账号权限在 <a class="link" :href.attr="$h.ak('/admin/admins')">管理账号</a>，自己的密码在 <a class="link" :href.attr="$h.ak('/admin/account')">我的账号</a></p>
                <p v-if="!$h.t(L_owner)">主题色、侧栏、圆角和页面样式，选一套自己喜欢的，只对你自己生效</p>
            </div></div>
        </div>

        <nav class="tabs" data-tabs v-if="$h.t(L_owner)">
            <a href="#appearance" data-tab="appearance">外观</a>
            <a href="#security" data-tab="security">登录安全</a>
            <a href="#tokens" data-tab="tokens">管理令牌 <span class="count">{{ $h.s($h.lists.size(m.adminTokens)) }}</span></a>
            <a href="#storage" data-tab="storage">存储</a>
            <a href="#backup" data-tab="backup">备份 <span class="count" v-if="!$h.t($h.lists.isEmpty(m.backupStored))">{{ $h.s($h.lists.size(m.backupStored)) }}</span></a>
            <a href="#install" data-tab="install">安装与域名</a>
            <a href="#system" data-tab="system">运行信息</a>
        </nav>

        <section data-tab-panel="appearance">
        <section class="card">
            <div class="card-head">
                <h2><svg class="i"><use href="#i-palette"></use></svg>{{ $h.s(($h.t(L_owner) ? '外观' : '我的外观')) }}</h2>
                <span class="tail muted small" v-if="$h.t(L_owner)">这是默认外观：代理后台、登录页和没自己选过的成员都用它</span>
                <span :class="['tail', 'mini-tag', $h.av(($h.t(m.ownLook) ? 'on' : null))]" v-if="!$h.t(L_owner)">{{ $h.s(($h.t(m.ownLook) ? '用的是自己选的' : '跟着默认外观')) }}</span>
            </div>
            <form method="post" :action.attr="$h.ak('/admin/settings/appearance')" id="appearance-form" :data-default.attr="$h.av(m.defaultLook)" :data-member.attr="$h.t(L_owner) ? null : ''">
                <input type="hidden" name="follow" :value.attr="'0'" data-follow>
                <div class="card-body appearance">
                    <div class="appearance-pick">
                        <template v-if="$h.t(L_owner)">
                        <div class="section-label">后台名称</div>
                        <div class="brand-field">
                            <input class="input" name="brand" :value.attr="$h.ak(m.brandName)" maxlength="20" placeholder="OTA 管理后台" aria-label="后台名称" data-brand-input>
                            <span class="hint">显示在侧栏、登录页和浏览器标签上，最多 20 个字，留空用默认</span>
                        </div>

                        <div class="section-label" style="margin-top:22px">登录页标语</div>
                        <div class="login-words">
                            <input class="input" name="loginTitle" :value.attr="$h.ak(m.loginTitle)" maxlength="30" placeholder="热更新 · 口令 · 代理" aria-label="登录页标题">
                            <input class="input" name="loginText" :value.attr="$h.ak(m.loginText)" maxlength="60" placeholder="管理员与代理统一登录入口" aria-label="登录页说明">
                            <span class="hint">登录页左边的大标题和一句说明，比如公司名、口号；留空用默认</span>
                        </div>
                        </template>

                        <div class="section-label" :style="$h.av(($h.t(L_owner) ? 'margin-top:22px' : null))">一键风格</div>
                        <div class="presets">
                            <template v-for="[L_p, L_pStat] in $h.each(m.presets)"><button class="preset" type="button" :data-pick.attr="$h.av($h.p(L_p, 'pickJson'))">
                                <i class="preset-sw" :style="$h.av($h.cat('--sw:', $h.p(L_p, 'accent'), ';--sw-side:', $h.p(L_p, 'sideBottom')))"></i>
                                <span><b>{{ $h.s($h.p(L_p, 'name')) }}</b><small>{{ $h.s($h.p(L_p, 'desc')) }}</small></span>
                            </button></template>
                        </div>
                        <span class="hint" style="display:block;margin-top:8px">点一下把下面的主题色、侧栏、圆角和样式一起选好，还能再单独改，保存后才生效</span>

                        <div class="section-label" style="margin-top:22px">主题色</div>
                        <div class="swatches">
                            <template v-for="[L_t, L_tStat] in $h.each(m.themes)"><label class="swatch" :title.attr="$h.av($h.p(L_t, 'name'))">
                                <input type="radio" name="theme" :value.attr="$h.ak($h.p(L_t, 'id'))" :checked.attr="$h.t($h.eq($h.p(L_t, 'id'), m.currentTheme)) ? 'checked' : null" :data-accent.attr="$h.av($h.p(L_t, 'accent'))" :data-hover.attr="$h.av($h.p(L_t, 'accentHover'))" :data-accent-dark.attr="$h.av($h.p(L_t, 'darkAccent'))" :data-side.attr="$h.av($h.cat($h.p(L_t, 'sideTop'), ' ', $h.p(L_t, 'sideBottom'), ' ', $h.p(L_t, 'sidePill'), ' ', $h.p(L_t, 'sideMenu')))" :data-side-dark.attr="$h.av($h.cat($h.p(L_t, 'sideTopDark'), ' ', $h.p(L_t, 'sideBottomDark'), ' ', $h.p(L_t, 'sideMenuDark')))">
                                <i class="sw" :style="$h.av($h.cat('--sw:', $h.p(L_t, 'accent'), ';--sw-side:', $h.p(L_t, 'sideBottom')))"></i>
                                <span>{{ $h.s($h.p(L_t, 'name')) }}</span>
                            </label></template>
                        </div>
                        <span class="hint" style="display:block;margin-top:8px">按色系排：鲜明的冷色、暖色，浓郁的深色，柔和的莫兰迪色和中性色</span>

                        <div class="section-label" style="margin-top:22px">圆角</div>
                        <div class="radii">
                            <template v-for="[L_r, L_rStat] in $h.each(m.radii)"><label class="radius-opt">
                                <input type="radio" name="radius" :value.attr="$h.ak($h.p(L_r, 'id'))" :checked.attr="$h.t($h.eq($h.p(L_r, 'id'), m.currentRadius)) ? 'checked' : null" :data-card.attr="$h.av($h.p(L_r, 'card'))" :data-large.attr="$h.av($h.p(L_r, 'large'))" :data-control.attr="$h.av($h.p(L_r, 'control'))" :data-small.attr="$h.av($h.p(L_r, 'small'))">
                                <i class="shape" :style="$h.av($h.cat('border-top-left-radius:', $h.mul($h.p(L_r, 'card'), 2), 'px'))"></i>
                                <span>{{ $h.s($h.p(L_r, 'name')) }}</span>
                            </label></template>
                        </div>
                        <div class="section-label" style="margin-top:22px">侧栏</div>
                        <div class="side-opts">
                            <template v-for="[L_s, L_sStat] in $h.each(m.sidebars)"><label class="side-opt">
                                <input type="radio" name="sidebar" :value.attr="$h.ak($h.p(L_s, 'id'))" :checked.attr="$h.t($h.eq($h.p(L_s, 'id'), m.currentSidebar)) ? 'checked' : null">
                                <i :class="['side-pic', $h.av($h.cat('side-pic-', $h.p(L_s, 'id')))]"><b></b><s></s><s></s><s></s></i>
                                <span><b>{{ $h.s($h.p(L_s, 'name')) }}</b><small>{{ $h.s($h.p(L_s, 'desc')) }}</small></span>
                            </label></template>
                        </div>

                        <div class="section-label" style="margin-top:22px">页面宽度</div>
                        <div class="look-opts">
                            <template v-for="[L_l, L_lStat] in $h.each(m.lookWidths)"><label class="look-opt">
                                <input type="radio" name="width" :value.attr="$h.ak($h.p(L_l, 'id'))" :checked.attr="$h.t($h.eq($h.p(L_l, 'id'), m.currentWidth)) ? 'checked' : null" :data-vars.attr="$h.av($h.p(L_l, 'varsJson'))" :data-vars-dark.attr="$h.av($h.p(L_l, 'darkVarsJson'))">
                                <i :class="['look-pic', $h.av($h.cat('lp-width-', $h.p(L_l, 'id')))]"><b></b><s></s></i>
                                <span><b>{{ $h.s($h.p(L_l, 'name')) }}</b><small>{{ $h.s($h.p(L_l, 'desc')) }}</small></span>
                            </label></template>
                        </div>
                        <div class="section-label" style="margin-top:22px">卡片</div>
                        <div class="look-opts">
                            <template v-for="[L_l, L_lStat] in $h.each(m.lookCards)"><label class="look-opt">
                                <input type="radio" name="cards" :value.attr="$h.ak($h.p(L_l, 'id'))" :checked.attr="$h.t($h.eq($h.p(L_l, 'id'), m.currentCards)) ? 'checked' : null" :data-vars.attr="$h.av($h.p(L_l, 'varsJson'))" :data-vars-dark.attr="$h.av($h.p(L_l, 'darkVarsJson'))">
                                <i :class="['look-pic', $h.av($h.cat('lp-cards-', $h.p(L_l, 'id')))]"><b></b><s></s></i>
                                <span><b>{{ $h.s($h.p(L_l, 'name')) }}</b><small>{{ $h.s($h.p(L_l, 'desc')) }}</small></span>
                            </label></template>
                        </div>
                        <div class="section-label" style="margin-top:22px">页面背景</div>
                        <div class="look-opts">
                            <template v-for="[L_l, L_lStat] in $h.each(m.lookBgs)"><label class="look-opt">
                                <input type="radio" name="bg" :value.attr="$h.ak($h.p(L_l, 'id'))" :checked.attr="$h.t($h.eq($h.p(L_l, 'id'), m.currentBg)) ? 'checked' : null" :data-vars.attr="$h.av($h.p(L_l, 'varsJson'))" :data-vars-dark.attr="$h.av($h.p(L_l, 'darkVarsJson'))">
                                <i :class="['look-pic', $h.av($h.cat('lp-bg-', $h.p(L_l, 'id')))]"><b></b><s></s></i>
                                <span><b>{{ $h.s($h.p(L_l, 'name')) }}</b><small>{{ $h.s($h.p(L_l, 'desc')) }}</small></span>
                            </label></template>
                        </div>
                        <div class="section-label" style="margin-top:22px">按钮</div>
                        <div class="look-opts">
                            <template v-for="[L_l, L_lStat] in $h.each(m.lookButtons)"><label class="look-opt">
                                <input type="radio" name="buttons" :value.attr="$h.ak($h.p(L_l, 'id'))" :checked.attr="$h.t($h.eq($h.p(L_l, 'id'), m.currentButtons)) ? 'checked' : null" :data-vars.attr="$h.av($h.p(L_l, 'varsJson'))" :data-vars-dark.attr="$h.av($h.p(L_l, 'darkVarsJson'))">
                                <i :class="['look-pic', $h.av($h.cat('lp-btn-', $h.p(L_l, 'id')))]"><b></b><s></s></i>
                                <span><b>{{ $h.s($h.p(L_l, 'name')) }}</b><small>{{ $h.s($h.p(L_l, 'desc')) }}</small></span>
                            </label></template>
                        </div>
                        <div class="section-label" style="margin-top:22px">表格</div>
                        <div class="look-opts">
                            <template v-for="[L_l, L_lStat] in $h.each(m.lookTables)"><label class="look-opt">
                                <input type="radio" name="tables" :value.attr="$h.ak($h.p(L_l, 'id'))" :checked.attr="$h.t($h.eq($h.p(L_l, 'id'), m.currentTables)) ? 'checked' : null" :data-vars.attr="$h.av($h.p(L_l, 'varsJson'))" :data-vars-dark.attr="$h.av($h.p(L_l, 'darkVarsJson'))">
                                <i :class="['look-pic', $h.av($h.cat('lp-tbl-', $h.p(L_l, 'id')))]"><b></b><s></s></i>
                                <span><b>{{ $h.s($h.p(L_l, 'name')) }}</b><small>{{ $h.s($h.p(L_l, 'desc')) }}</small></span>
                            </label></template>
                        </div>
                    </div>

                </div>
                <div class="card-foot">
                    <span class="hint" id="appearance-hint"></span>
                    <button class="btn btn-secondary btn-sm" type="button" id="appearance-reset" :title.attr="$h.av(($h.t(L_owner) ? '回到刚装好时的样子，保存后生效' : '选回超级管理员设的默认外观，保存后以后跟着它走'))">{{ $h.s(($h.t(L_owner) ? '恢复默认' : '用默认外观')) }}</button>
                    <button class="btn btn-sm" type="submit">保存外观</button>
                </div>
            </form>
        </section>
        </section>

        <template v-if="$h.t(L_owner)">
        <section data-tab-panel="security" hidden>
        <section class="card">
            <div class="card-head">
                <h2><svg class="i"><use href="#i-shield"></use></svg>登录安全</h2>
                <span class="tail muted small">登录会话保存在数据库里，重启服务不用重新登录</span>
            </div>
            <form method="post" :action.attr="$h.ak('/admin/settings/security')" id="security-form">
                <div class="pref-row">
                    <div class="pref-text"><b>「记住我」保持登录</b><span>勾了「记住我」，这段时间内关浏览器、重启服务都不用重新登录</span></div>
                    <select class="select" name="rememberDays" aria-label="记住我保持登录的天数">
                        <template v-for="[L_d, L_dStat] in $h.each(m.rememberOptions)"><template v-if="L_dStat.index">
                        </template><option :value.attr="$h.ak(L_d)" :selected.attr="$h.t($h.eq(L_d, m.rememberDays)) ? 'selected' : null">{{ $h.s($h.add(L_d, ' 天')) }}</option></template>
                    </select>
                </div>
                <div class="pref-row">
                    <div class="pref-text"><b>不勾选「记住我」时</b><span>关浏览器就退出，一直没操作超过这个时间也会退出</span></div>
                    <select class="select" name="idleMinutes" aria-label="空闲多久自动退出">
                        <template v-for="[L_m, L_mStat] in $h.each(m.idleOptions)"><template v-if="L_mStat.index">
                        </template><option :value.attr="$h.ak(L_m)" :selected.attr="$h.t($h.eq(L_m, m.idleMinutes)) ? 'selected' : null">{{ $h.s($h.add($h.add('空闲 ', $h.ui.minutes(L_m)), '退出')) }}</option></template>
                    </select>
                </div>
                <div class="pref-row">
                    <div class="pref-text"><b>每个管理员最多同时登录</b><span>满了再登录时，让他选退出哪几台设备。演示账号大家共用，不限台数</span></div>
                    <select class="select" name="maxDevices" aria-label="最多同时登录几台">
                        <template v-for="[L_v, L_vStat] in $h.each(m.deviceOptions)"><option :value.attr="$h.ak(L_v)" :selected.attr="$h.t($h.eq(L_v, m.maxDevices)) ? 'selected' : null">{{ $h.s($h.cat(L_v, ' 台')) }}</option></template>
                    </select>
                </div>
                <div class="pref-row">
                    <div class="pref-text"><b>操作日志保留</b><span>超过这个时间的操作日志每天凌晨自动清掉，保存时也会清一次；要留底先到操作日志页导出</span></div>
                    <select class="select" name="logKeepDays" aria-label="操作日志保留多久">
                        <template v-for="[L_k, L_kStat] in $h.each(m.logKeepOptions)"><option :value.attr="$h.ak(L_k)" :selected.attr="$h.t($h.eq(L_k, m.logKeepDays)) ? 'selected' : null">{{ $h.s(($h.t($h.eq(L_k, 0)) ? '一直保留' : $h.cat('保留 ', L_k, ' 天'))) }}</option></template>
                    </select>
                </div>
                <div class="pref-row">
                    <div class="pref-text">
                        <b>要求所有管理员开启两步验证</b>
                        <span>打开后，没开两步验证的管理员只能进「我的账号」，开好才能用别的功能。代理不受影响</span>
                        <span class="muted-line" v-if="$h.t(!$h.t($h.lists.isEmpty(m.without2fa)))">{{ $h.s($h.add('还没开启：', $h.strings.listJoin($h.proj(m.without2fa, ($e1) => $h.p($e1, 'name')), '、'))) }}</span>
                        <span class="muted-line" v-if="$h.t($h.lists.isEmpty(m.without2fa))">所有启用的管理员都已开启两步验证</span>
                    </div>
                    <label class="switch"><input type="checkbox" name="require2fa" :value.attr="'true'" :checked.attr="$h.t(m.require2fa) ? 'checked' : null"><span class="sw-track"><span class="sw-thumb"></span></span><span class="sr-only">要求所有管理员开启两步验证</span></label>
                </div>
                <div class="pref-row">
                    <div class="pref-text"><b>密码输错暂停登录</b><span>{{ $h.s($h.cat('同一个账号连续输错到次数就暂停登录；同一 IP 换着账号试，错 ', m.lockPerIp, ' 次也暂停')) }}</span></div>
                    <div class="pref-pair">
                        <select class="select" name="lockFails" aria-label="输错几次暂停">
                            <template v-for="[L_f, L_fStat] in $h.each(m.lockFailOptions)"><option :value.attr="$h.ak(L_f)" :selected.attr="$h.t($h.eq(L_f, m.lockFails)) ? 'selected' : null">{{ $h.s($h.cat('错 ', L_f, ' 次')) }}</option></template>
                        </select>
                        <select class="select" name="lockMinutes" aria-label="暂停多久">
                            <template v-for="[L_n, L_nStat] in $h.each(m.lockMinuteOptions)"><option :value.attr="$h.ak(L_n)" :selected.attr="$h.t($h.eq(L_n, m.lockMinutes)) ? 'selected' : null">{{ $h.s($h.cat('暂停 ', L_n, ' 分钟')) }}</option></template>
                        </select>
                    </div>
                </div>
                <div class="card-foot">
                    <span class="hint">改了以后新登录按新设置，已经登录的不变</span>
                    <button class="btn btn-sm" type="submit">保存</button>
                </div>
            </form>
        </section>
                    <section class="card section-gap demo-card" v-if="$h.t(!$h.eq(m.demoEnabled, null))">
                <div class="card-head">
                    <h2><svg class="i"><use href="#i-play"></use></svg>演示账号</h2>
                    <span class="muted small">给客户、同事看后台用：登录后什么都能点，但操作都在单独的演示数据里，不碰正式数据</span>
                    <span :class="['tail', 'mini-tag', $h.av(($h.t(m.demoRunning) ? 'on' : null))]">{{ $h.s(($h.t(m.demoEnabled) ? ($h.t(m.demoRunning) ? '有人在用' : '已开启') : '已关闭')) }}</span>
                </div>
                <form method="post" :action.attr="$h.ak('/admin/settings/demo')" class="card-body" id="demo-form">
                    <label class="switch-line"><span class="switch"><input type="checkbox" name="enabled" :value.attr="'true'" :checked.attr="$h.t(m.demoEnabled) ? 'checked' : null"><span class="sw-track"><span class="sw-thumb"></span></span></span><span><b>允许用演示账号登录</b><small>在登录页输演示账号和密码就进演示环境；不用时 30 分钟自动停掉，数据每天恢复默认</small></span></label>
                    <div class="row-2">
                        <div class="field">
                            <label for="demo-user">演示账号</label>
                            <input class="input mono" id="demo-user" name="username" :value.attr="$h.ak(m.demoUser)" maxlength="32" required>
                        </div>
                        <div class="field">
                            <label for="demo-pwd">演示密码</label>
                            <input class="input" id="demo-pwd" name="password" type="text" minlength="6" placeholder="不填就不改" autocomplete="off">
                        </div>
                    </div>
                </form>
                <div class="card-foot">
                    <span class="hint">{{ $h.s(($h.t($h.eq(m.demoCreatedAt, null)) ? '还没人用过，第一次登录时准备演示数据（几秒钟）' : $h.cat('现在的演示数据准备于 ', m.demoCreatedAt))) }}</span>
                    <button class="btn btn-ghost btn-sm" type="submit" form="demo-reset-form"><svg class="i i-sm"><use href="#i-history"></use></svg>恢复默认演示数据</button>
                    <button class="btn btn-sm" type="submit" form="demo-form">保存</button>
                </div>
                <form method="post" :action.attr="$h.ak('/admin/settings/demo/reset')" id="demo-reset-form" data-confirm="恢复默认演示数据？" data-confirm-body="演示环境里别人改过、上传过的东西都会清掉，正在用演示账号的人要重新登录。" data-confirm-ok="恢复默认" hidden></form>
            </section>
        </section>

        <section data-tab-panel="tokens" hidden>
        <section class="card section-gap">
            <div class="card-head">
                <h2>管理令牌</h2>
                <span class="muted small">能管理所有 App，只给自己的运维脚本用</span>
                <span class="tail"><button class="btn btn-sm" type="button" data-open="token-new-dialog"><svg class="i i-sm"><use href="#i-plus"></use></svg>新建管理令牌</button></span>
            </div>
            <div class="note warn" style="margin:14px 18px 0"><span>管理令牌相当于管理员，能操作所有 App。CI 发布用「App 设置 → 发布令牌」，只能动一个 App。</span></div>
            <div class="table-wrap" style="margin-top:14px">
                <table>
                    <thead><tr><th>名称</th><th>令牌</th><th>创建时间</th><th>最近使用</th><th class="right">操作</th></tr></thead>
                    <tbody data-collapse="8" data-collapse-unit="个令牌">
                    <template v-for="[L_t, L_tStat] in $h.each(m.adminTokens)"><template v-if="L_tStat.index">
                    </template><tr>
                        <td class="strong">{{ $h.s($h.p(L_t, 'name')) }}</td>
                        <td class="mono small muted" :title.attr="$h.av(($h.t($h.eq($h.p(L_t, 'tokenPrefix'), null)) ? '启动参数指定的，或者在这个版本之前创建的，没有记录开头几位' : null))">{{ $h.s(($h.t(!$h.eq($h.p(L_t, 'tokenPrefix'), null)) ? $h.add($h.p(L_t, 'tokenPrefix'), '…') : '—')) }}</td>
                        <td class="muted nowrap">{{ $h.s($h.ui.dt($h.p(L_t, 'createdAt'))) }}</td>
                        <td class="muted nowrap">{{ $h.s(($h.t(!$h.eq($h.p(L_t, 'lastUsedAt'), null)) ? $h.ui.ago($h.p(L_t, 'lastUsedAt')) : '从未使用')) }}</td>
                        <td class="actions">
                            <form method="post" :action.attr="$h.ak($h.link('/admin/apps/token/{id}/revoke', [['id', $h.p(L_t, 'id')]]))" :data-confirm.attr="$h.av($h.cat('撤销管理令牌 ', $h.p(L_t, 'name'), '？'))" data-confirm-body="用这个令牌的脚本会立即失效，不能恢复。最后一个管理令牌不能撤销。" data-confirm-ok="撤销" data-confirm-danger>
                                <button class="btn btn-ghost btn-sm" type="submit">撤销</button>
                            </form>
                        </td>
                    </tr></template>
                    </tbody>
                </table>
            </div>
            <div class="card-foot"><span class="hint">令牌只在创建时显示一次，列表里只显示开头几位</span></div>
        </section>
        <template v-for="L_slug in [$h.elvis($h.p(m.app, 'slug'), 'my-app')]"><section class="card">
            <div class="card-head"><h2>怎么用</h2><span class="muted small">命令行和脚本里带上管理令牌，能管理所有 App</span><a class="tail link" :href.attr="$h.ak('/admin/docs#mgmt-auth')">API 文档<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
            <div class="card-body">
                <div class="code-pair">
                    <div class="code" style="margin:0">
                        <div class="code-bar"><span>终端 · ota 命令行</span><button class="code-copy" type="button" data-copy-pre>复制</button></div>
<pre><span class="cm"># 第一次：保存服务地址和令牌（把 ota_xxx 换成新建时显示的令牌）</span>
ota login --server <span>{{ $h.s(m.publicUrl) }}</span> --token ota_xxx

<span class="cm"># 列出所有 App，看一个 App 的版本和各渠道线上版本</span>
ota apps
ota releases --app <span>{{ $h.s(L_slug) }}</span>

<span class="cm"># 审核通过后约好时间切到 B 面（只有管理令牌能设）</span>
ota schedule mode --app <span>{{ $h.s(L_slug) }}</span> --face B --at "2026-11-13 10:00"</pre>
                    </div>
                    <div class="code" style="margin:0">
                        <div class="code-bar"><span>HTTP · 管理接口</span><button class="code-copy" type="button" data-copy-pre>复制</button></div>
<pre><span class="cm"># 令牌放在请求头 Authorization: Bearer 里</span>
curl -H "Authorization: Bearer ota_xxx" \
     <span>{{ $h.s(m.publicUrl) }}</span>/api/v1/apps

<span class="cm"># 新建 App（私钥和证书不传时自动生成）</span>
curl -X POST -H "Authorization: Bearer ota_xxx" \
     -H "Content-Type: application/json" \
     -d '{"slug":"new-app","name":"New App"}' \
     <span>{{ $h.s(m.publicUrl) }}</span>/api/v1/apps</pre>
                    </div>
                </div>
                <p class="small muted" style="margin-top:12px">只发布一个 App 的 CI 用「App 设置 → 发布令牌」，泄露了也只影响那一个 App。</p>
            </div>
        </section></template>
        </section>

        <section data-tab-panel="storage" hidden>
        <section class="card section-gap">
            <div class="card-head">
                <h2>文件存储</h2>
                <span class="tail">
                    <span class="badge badge-line"><span class="dot"></span><span>{{ $h.s(($h.t(m.storageS3) ? '对象存储' : '本机存储')) }}</span></span>
                    <button class="btn btn-sm btn-secondary" type="button" data-open="storage-dialog" :disabled.attr="$h.t(($h.t(m.storageLocked) || $h.t(m.storageBusy))) ? 'disabled' : null"><svg class="i i-sm"><use href="#i-database"></use></svg>切换存储</button>
                </span>
            </div>
            <div class="card-body">
                <p class="small">{{ $h.s(m.storage) }}</p>
                <p class="small muted" style="margin-top:4px">版本文件、补丁和安装包都存在这里。切换会先复制并逐个核对，都到位才换过去，不用重启，原来的文件不删。原来的存储连不上时，会先问你要不要清空新存储里没有文件的版本再换。</p>
                <p class="small warn-text" style="margin-top:8px" v-if="$h.t(m.storageLocked)">文件存储是启动参数（--ota.storage.*）设的，在这里改了重启会变回去，要改先去掉启动参数。</p>
                <div class="storage-run" data-storage-run hidden :data-busy.attr="$h.av(m.storageBusy)" :data-last.attr="$h.av(m.storageLast)">
                    <div class="storage-run-head"><b data-role="title">正在切换</b><span class="muted small" data-role="count"></span></div>
                    <div class="progress-line"><span data-role="bar" style="width:0"></span></div>
                    <p class="small muted" data-role="text"></p>
                    <p class="storage-run-note" data-role="note" hidden></p>
                    <div class="storage-run-foot" data-role="actions" hidden>
                        <button class="btn btn-sm btn-danger" type="button" data-role="clear" hidden>清空并切换</button>
                        <button class="btn btn-sm btn-ghost" type="button" data-role="dismiss" hidden>知道了</button>
                    </div>
                </div>
            </div>
        </section>
        <section class="card">
            <div class="card-head">
                <h2>存储用量</h2>
            </div>
            <div class="card-body">
                <div class="mini-stats" style="margin-bottom:12px">
                    <div><span>版本文件</span><b>{{ $h.s($h.add($h.numbers.formatInteger($h.z(m.usage, 'files'), 1, 'COMMA'), ' 个')) }}</b></div>
                    <div><span>版本文件大小</span><b>{{ $h.s($h.ui.bytes($h.z(m.usage, 'bytes'))) }}</b></div>
                    <div><span>增量补丁</span><b>{{ $h.s($h.add($h.add($h.z(m.usage, 'patches'), ' 个 · '), $h.ui.bytes($h.z(m.usage, 'patchBytes')))) }}</b></div>
                    <div><span>合计</span><b>{{ $h.s($h.ui.bytes($h.z(m.usage, 'totalBytes'))) }}</b></div>
                </div>
                <p class="small muted">同样的文件只存一份，所有 App 共用，外部链接版本不算在内。删了版本或 App 后，没人用的文件可以清理。</p>
            </div>
            <div class="card-foot">
                <span class="hint">只清理 24 小时前登记、没有版本在用的文件</span>
                <form method="post" :action.attr="$h.ak('/admin/settings/storage/clean')" data-confirm="清理没有版本在用的文件？" data-confirm-body="会删除存储里没有任何版本引用的文件和用不上的增量补丁，删除后不能恢复。现有版本都不受影响。" data-confirm-ok="清理" data-confirm-danger>
                    <button class="btn btn-secondary btn-sm" type="submit"><svg class="i i-sm"><use href="#i-trash"></use></svg>清理无用文件</button>
                </form>
            </div>
        </section>
        </section>

        <section data-tab-panel="backup" hidden>
            <div :class="['restore-note', $h.av(($h.t($h.p(m.backupRestored, 'ok')) ? 'ok' : 'err'))]" v-if="$h.t(!$h.eq(m.backupRestored, null))">
                <svg class="i"><use :href.attr="$h.av(($h.t($h.p(m.backupRestored, 'ok')) ? '#i-tick' : '#i-alert'))"></use></svg>
                <span v-if="$h.t($h.p(m.backupRestored, 'ok'))">{{ $h.s($h.cat('上次从备份恢复成功（备份时间 ', $h.strings.substring($h.elvis($h.p(m.backupRestored, 'from'), ''), 0, 16), '）。恢复前的数据留在数据目录的 ', $h.p(m.backupRestored, 'before'), '，确认没问题可以删掉')) }}</span>
                <span v-if="!$h.t($h.p(m.backupRestored, 'ok'))">{{ $h.s($h.cat('上次恢复失败，已经换回原来的数据：', $h.p(m.backupRestored, 'error'))) }}</span>
            </div>

            <section class="card pending-card" v-if="$h.t(!$h.eq(m.backupPending, null))">
                <div class="pc-text">
                    <b>有一份准备好的恢复，还没生效</b>
                    <span>{{ $h.s($h.cat('备份时间 ', $h.strings.substring($h.elvis($h.p(m.backupPending, 'createdAt'), ''), 0, 16), ' · ', $h.p(m.backupPending, 'apps'), ' 个 App · ', $h.p(m.backupPending, 'stagedBy'), ' 准备的。重启服务时替换数据')) }}</span>
                </div>
                <form method="post" :action.attr="$h.ak('/admin/settings/backup/restore/apply')" class="pc-apply">
                    <input class="input" type="password" name="adminPassword" placeholder="你的登录密码" required autocomplete="current-password" aria-label="你的登录密码">
                    <button class="btn" type="submit">立即重启恢复</button>
                </form>
                <form method="post" :action.attr="$h.ak('/admin/settings/backup/restore/cancel')" data-confirm="取消这次恢复？" data-confirm-body="准备好的备份会删掉，数据保持现在这样。" data-confirm-ok="取消恢复"><button class="btn btn-ghost" type="submit">取消</button></form>
            </section>

            <div class="grid grid-2 backup-grid">
                <section class="card backup-hero">
                    <div class="card-head"><h2><svg class="i"><use href="#i-archive"></use></svg>完整备份</h2></div>
                    <div class="card-body">
                        <p class="backup-lead">整个后台打成一个文件：数据库（App、版本记录、口令、代理、成员、设置）、版本文件和图标、签名私钥、主密钥、安装配置。换服务器或者出了问题，用它整个恢复。</p>
                        <dl class="kv backup-kv">
                            <dt>数据库</dt><dd>{{ $h.s(($h.t(m.backupH2) ? '内置数据库，一起备份' : 'MySQL，请另外用 mysqldump 备份')) }}</dd>
                            <dt>文件</dt><dd>{{ $h.s(($h.t(m.storageS3) ? '在 S3 里，不放进备份' : '本机存储，一起备份')) }}</dd>
                            <dt>压缩前大小</dt><dd><span>{{ $h.s($h.ui.size(m.backupSize)) }}</span><span class="muted small" v-if="$h.t(m.backupH2)"> · <span>数据库</span> {{ $h.s($h.ui.size(m.backupDbSize)) }} · <span>文件</span> {{ $h.s($h.ui.size(m.backupFilesSize)) }}</span></dd>
                            <dt>上次备份</dt><dd :title.attr="$h.av($h.ui.full(m.backupLast))">{{ $h.s(($h.t($h.eq(m.backupLast, null)) ? '还没在服务器上备份过' : $h.ui.ago(m.backupLast))) }}</dd>
                        </dl>
                        <div class="backup-cta">
                            <button class="btn" type="button" data-open="backup-download-dialog"><svg class="i"><use href="#i-download"></use></svg>下载完整备份</button>
                            <span class="hint">用你设的备份密码加密，密码忘了就解不开；里面有签名私钥，存好</span>
                        </div>
                    </div>
                </section>
                <section class="card">
                    <div class="card-head"><h2><svg class="i"><use href="#i-clock"></use></svg>自动备份</h2><span :class="['tail', 'mini-tag', $h.av(($h.t(m.backupAuto) ? 'on' : null))]">{{ $h.s(($h.t(m.backupAuto) ? '已开启' : '没开')) }}</span></div>
                    <form method="post" :action.attr="$h.ak('/admin/settings/backup/auto')" class="card-body backup-auto">
                        <label class="switch-line"><span class="switch"><input type="checkbox" name="enabled" :value.attr="'true'" :checked.attr="$h.t(m.backupAuto) ? 'checked' : null"><span class="sw-track"><span class="sw-thumb"></span></span></span><span><b>每天自动备份到服务器</b><small>存在数据目录的 backups 下，超过保留份数自动删最旧的</small></span></label>
                        <div class="row-2">
                            <div class="field">
                                <label for="backup-hour">每天几点</label>
                                <select class="input" id="backup-hour" name="hour"><template v-for="L_h in [0, 1, 2, 3, 4, 5, 6, 12, 18, 22, 23]"><option :value.attr="$h.ak(L_h)" :selected.attr="$h.t($h.eq(L_h, m.backupHour)) ? 'selected' : null">{{ $h.s($h.cat(L_h, ' 点')) }}</option></template></select>
                            </div>
                            <div class="field">
                                <label for="backup-keep">保留</label>
                                <select class="input" id="backup-keep" name="keep"><template v-for="L_k in [3, 7, 14, 30]"><option :value.attr="$h.ak(L_k)" :selected.attr="$h.t($h.eq(L_k, m.backupKeep)) ? 'selected' : null">{{ $h.s($h.cat('最近 ', L_k, ' 份')) }}</option></template></select>
                            </div>
                        </div>
                        <div class="btn-row"><button class="btn btn-secondary" type="submit">保存</button></div>
                    </form>
                </section>
            </div>

            <section class="card section-gap">
                <div class="card-head">
                    <h2>服务器上的备份</h2>
                    <span class="muted small">在数据目录的 backups 下；把下载的 .otaproj 放进去也会出现在这里，可以直接恢复</span>
                    <form method="post" :action.attr="$h.ak('/admin/settings/backup/now')" class="tail" data-no-swap data-backup-now data-busy-text="正在备份"><button class="btn btn-secondary btn-sm" type="submit"><svg class="i i-sm"><use href="#i-archive"></use></svg>现在备份一次</button></form>
                </div>
                <div class="empty" v-if="$h.t($h.lists.isEmpty(m.backupStored))">
                    <div class="empty-icon"><svg class="i"><use href="#i-archive"></use></svg></div>
                    <b>还没有备份</b>开启自动备份，或者点「现在备份一次」
                </div>
                <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty(m.backupStored))">
                    <table class="compact">
                        <thead><tr><th>文件</th><th>时间</th><th class="right">大小</th><th class="right">操作</th></tr></thead>
                        <tbody><template v-for="[L_b, L_bStat] in $h.each(m.backupStored)"><tr>
                            <td><span class="mono">{{ $h.s($h.z(L_b, 'name')) }}</span> <span class="mini-tag" v-if="$h.t($h.z(L_b, 'encrypted'))">已加密</span></td>
                            <td class="muted nowrap" :title.attr="$h.av($h.ui.full($h.z(L_b, 'at')))">{{ $h.s($h.ui.dt($h.z(L_b, 'at'))) }}</td>
                            <td class="num right">{{ $h.s($h.ui.size($h.z(L_b, 'size'))) }}</td>
                            <td class="actions"><div class="btn-row">
                                <button class="btn btn-ghost btn-sm" type="button" data-backup-act="download" :data-name.attr="$h.av($h.z(L_b, 'name'))" :data-enc.attr="$h.av($h.z(L_b, 'encrypted'))"><svg class="i i-sm"><use href="#i-download"></use></svg>下载</button>
                                <button class="btn btn-ghost btn-sm" type="button" data-backup-act="restore" :data-name.attr="$h.av($h.z(L_b, 'name'))" :data-enc.attr="$h.av($h.z(L_b, 'encrypted'))"><svg class="i i-sm"><use href="#i-history"></use></svg>恢复</button>
                                <form method="post" :action.attr="$h.ak($h.add($h.add('/admin/settings/backup/stored/', $h.z(L_b, 'name')), '/delete'))" :data-confirm.attr="$h.av($h.cat('删除 ', $h.z(L_b, 'name'), '？'))" data-confirm-body="删了就找不回来了。" data-confirm-ok="删除" data-confirm-danger><button class="icon-btn" type="submit" title="删除" aria-label="删除"><svg class="i"><use href="#i-trash"></use></svg></button></form>
                            </div></td>
                        </tr></template></tbody>
                    </table>
                </div>
            </section>

            <section class="card section-gap">
                <div class="card-head"><h2><svg class="i"><use href="#i-history"></use></svg>从备份文件恢复</h2><span class="muted small">整个后台换成备份里的样子，现在的数据会先留一份</span></div>
                <form class="card-body restore-form" id="restore-form" data-no-swap>
                    <label class="picker restore-pick" for="restore-file">
                        <span class="drop-icon"><svg class="i"><use href="#i-upload"></use></svg></span>
                        <span class="rp-text"><b data-role="name">选择备份文件（.otaproj）</b><small data-role="meta">点这里选，或者把文件拖进来，最大 8 GB</small></span>
                        <input type="file" id="restore-file" accept=".otaproj" hidden>
                    </label>
                    <div class="row-2">
                        <div class="field">
                            <label for="restore-pwd">备份密码</label>
                            <input class="input" id="restore-pwd" type="password" required autocomplete="off" placeholder="下载备份时设的密码">
                        </div>
                        <div class="field">
                            <label for="restore-admin">你的登录密码</label>
                            <input class="input" id="restore-admin" type="password" required autocomplete="current-password">
                        </div>
                    </div>
                    <div class="upload-progress" data-role="progress" hidden>
                        <div class="up-head"><b data-role="phase">正在上传</b><span data-role="pct"></span></div>
                        <span class="up-bar"><i data-role="bar"></i></span>
                        <small data-role="text"></small>
                    </div>
                    <div class="restore-foot">
                        <button class="btn btn-danger" type="submit" data-role="go"><svg class="i i-sm"><use href="#i-upload"></use></svg>上传并恢复</button>
                        <button class="btn btn-ghost" type="button" data-role="stop" hidden>取消上传</button>
                        <span class="hint">传完先检查备份和密码，通过了服务自动重启，所有人要重新登录；恢复前的数据留在数据目录的 restore 下</span>
                    </div>
                </form>
            </section>
        </section>

        <section data-tab-panel="install" hidden>
        <div class="grid grid-2">
            <section class="card">
                <div class="card-head"><h2>安装信息</h2><span class="tail badge badge-solid"><span class="dot"></span>已安装</span></div>
                <div class="card-body">
                    <dl class="kv-lines">
                        <dt>安装方式</dt><dd>{{ $h.s(($h.t($h.p(m.inst, 'migrated')) ? '从旧版本升级（沿用原来的配置）' : '安装引导')) }}</dd>
                        <dt>安装时间</dt><dd>{{ $h.s($h.elvis(m.installedAt, '—')) }}</dd>
                        <dt>安装时版本</dt><dd class="mono">{{ $h.s($h.elvis($h.p(m.inst, 'version'), '—')) }}</dd>
                        <dt>数据库</dt><dd class="mono small">{{ $h.s(m.effectiveDb) }}</dd>
                        <dt>数据目录</dt><dd class="mono small">{{ $h.s(m.dataDir) }}</dd>
                        <dt>接口对外地址</dt><dd class="mono">{{ $h.s(m.publicUrl) }}</dd>
                        <template v-if="!$h.t($h.eq(m.publicUrlFix, null))"><dt></dt><dd>
                            <form class="port-fix" method="post" :action.attr="$h.ak('/admin/settings/public-url')">
                                <input type="hidden" name="publicUrl" :value.attr="$h.ak(m.publicUrlFix)">
                                <svg class="i i-sm"><use href="#i-alert"></use></svg>
                                <span>少了端口：接口域名带端口，这里也要带上，不然 App 下载文件的地址不对</span>
                                <button class="btn btn-sm" type="submit">{{ $h.s($h.add('改成 ', m.publicUrlFix)) }}</button>
                            </form>
                        </dd></template>
                        <dt>文件存储</dt><dd>{{ $h.s(m.storage) }}</dd>
                        <dt>时区</dt><dd>{{ $h.s(m.displayZone) }}</dd>
                    </dl>
                </div>
            </section>
            <section class="card">
                <div class="card-head">
                    <h2>访问域名</h2>
                    <span class="tail">
                        <span :class="['badge', $h.av(($h.t($h.p(m.inst, 'restrictDomains')) ? 'badge-solid' : 'badge-line'))]"><span class="dot"></span><span>{{ $h.s(($h.t($h.p(m.inst, 'restrictDomains')) ? '已限制' : '不限制')) }}</span></span>
                        <button class="btn btn-sm btn-secondary" type="button" data-open="domains-dialog"><svg class="i i-sm"><use href="#i-edit"></use></svg>修改</button>
                    </span>
                </div>
                <div class="empty" v-if="$h.t($h.lists.isEmpty($h.p(m.inst, 'domains')))">
                    <div class="empty-icon"><svg class="i"><use href="#i-link"></use></svg></div>
                    <b>没有绑定域名</b>任何域名和 IP 都能打开后台、调用接口。正式上线建议绑定域名，点右上角「修改」添加
                </div>
                <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty($h.p(m.inst, 'domains')))">
                    <table class="compact">
                        <thead><tr><th>域名</th><th>后台</th><th>接口</th><th>协议</th></tr></thead>
                        <tbody>
                        <template v-for="[L_d, L_dStat] in $h.each($h.p(m.inst, 'domains'))"><template v-if="L_dStat.index">
                        </template><tr>
                            <td class="mono">{{ $h.s($h.p(L_d, 'host')) }}</td>
                            <td><svg class="i i-sm" style="stroke-width:3" v-if="$h.t($h.p(L_d, 'admin'))"><use href="#i-tick"></use></svg><span class="muted" v-if="!$h.t($h.p(L_d, 'admin'))">—</span></td>
                            <td><svg class="i i-sm" style="stroke-width:3" v-if="$h.t($h.p(L_d, 'api'))"><use href="#i-tick"></use></svg><span class="muted" v-if="!$h.t($h.p(L_d, 'api'))">—</span></td>
                            <td class="muted">{{ $h.s(($h.t($h.p(L_d, 'https')) ? 'HTTPS' : 'HTTP')) }}</td>
                        </tr></template>
                        </tbody>
                    </table>
                </div>
                <div class="card-foot" v-if="$h.t($h.p(m.inst, 'restrictDomains'))"><span class="hint">用错域名访问是 404，本机地址 127.0.0.1 不受限制</span></div>
            </section>
        </div>
        <section class="card danger-card" style="margin-top:20px">
            <div class="card-head"><h2>重新运行安装引导</h2></div>
            <div class="card-body">
                <p class="muted" style="line-height:1.7">可以换数据库、改域名和存储、重设超级管理员，数据可以保留。保存后服务会重启十几秒，挑用户少的时候做。</p>
                <form class="reinstall-form" method="post" :action.attr="$h.ak('/admin/settings/reinstall')" autocomplete="off">
                    <label class="field" style="margin:0;flex:1">
                        <span class="sr-only">当前密码</span>
                        <input class="input" type="password" name="password" required placeholder="输入你的登录密码确认" autocomplete="current-password">
                    </label>
                    <button class="btn" type="submit"><svg class="i i-sm"><use href="#i-refresh"></use></svg>打开安装引导</button>
                </form>
            </div>
        </section>
        </section>

        <section data-tab-panel="system" hidden>
        <section class="card">
            <div class="card-head">
                <h2>运行信息</h2>
                <span class="tail muted small">在「安装与域名」重新运行安装引导来改，启动参数优先</span>
            </div>
            <div class="card-body">
                <dl class="kv-lines">
                    <dt>版本</dt><dd><span class="mono">{{ $h.s(m.version) }}</span><span class="muted small" v-if="$h.t(!$h.eq(m.builtAt, null))">{{ $h.s($h.add('构建于 ', m.builtAt)) }}</span></dd>
                    <dt>健康检查</dt><dd><span class="mono small">{{ $h.s($h.add(m.publicUrl, '/healthz')) }}</span><span class="muted small">给宝塔监控、负载均衡探活用，不需要登录</span></dd>
                    <dt>对外地址</dt><dd class="mono">{{ $h.s(m.publicUrl) }}</dd>
                    <dt>数据目录</dt><dd class="mono small">{{ $h.s(m.dataDir) }}</dd>
                    <dt>文件存储</dt><dd>{{ $h.s(m.storage) }}</dd>
                    <dt>已运行</dt><dd>{{ $h.s(m.uptime) }}</dd>
                    <dt>内存</dt><dd>{{ $h.s($h.cat('已用 ', $h.ui.size(m.memUsed), ' / 最多 ', $h.ui.size(m.memMax))) }}</dd>
                    <dt>磁盘</dt><dd><span>{{ $h.s($h.cat('剩余 ', $h.ui.size(m.diskFree), ' / 共 ', $h.ui.size(m.diskTotal))) }}</span><span class="warn-text small" v-if="$h.t(m.diskLow)">空间不多了，清理一下旧版本和备份</span></dd>
                    <dt>Java</dt><dd class="mono small">{{ $h.s(m.javaInfo) }}</dd>
                    <dt>增量更新</dt><dd>{{ $h.s(($h.t(m.patchEnabled) ? '已开启（每个 App 可以单独关闭）' : '已关闭（ota.patch.enabled）')) }}</dd>
                    <dt>登录保护</dt><dd><span>{{ $h.s($h.cat('同一账号连续输错 ', m.lockFails, ' 次、或同一 IP 错 ', m.lockPerIp, ' 次，暂停登录 ', m.lockMinutes, ' 分钟')) }}</span><a class="muted small" href="#security">在「登录安全」里改</a></dd>
                    <dt>登录设备</dt><dd><span>{{ $h.s($h.cat('每个管理员最多同时登录 ', m.maxDevices, ' 台，满了再登录时选退出哪台')) }}</span><a class="muted small" href="#security">在「登录安全」里改</a></dd>
                    <dt>操作日志</dt><dd>{{ $h.s(($h.t($h.eq(m.logKeepDays, 0)) ? '一直保留' : $h.cat('保留 ', m.logKeepDays, ' 天，每天凌晨自动清理'))) }}</dd>
                </dl>
            </div>
            <div class="card-foot">
                <span class="hint">找技术支持看问题时，把这些信息一起发过去（不含密码和密钥）</span>
                <button class="btn btn-secondary btn-sm" type="button" :data-copy.attr="$h.av(m.diagText)" data-copy-label="已复制诊断信息"><svg class="i i-sm"><use href="#i-copy"></use></svg>复制诊断信息</button>
            </div>
        </section>
        <section class="card section-gap" id="metrics">
            <div class="card-head">
                <h2>监控指标（Prometheus）</h2>
                <span :class="['badge', $h.av(($h.t(m.metricsOn) ? 'badge-solid' : 'badge-line'))]"><span class="dot"></span><span>{{ $h.s(($h.t(m.metricsOn) ? '已开启' : '未开启')) }}</span></span>
                <span class="tail muted small">给 Prometheus、Grafana 采集，配启动失败、激活掉量这些告警</span>
            </div>
            <div class="card-body">
                <dl class="kv-lines">
                    <dt>采集地址</dt><dd><span class="mono small">{{ $h.s(m.metricsUrl) }}</span><button class="icon-btn" type="button" :data-copy.attr="$h.av(m.metricsUrl)" title="复制采集地址" aria-label="复制采集地址"><svg class="i i-sm"><use href="#i-copy"></use></svg></button></dd>
                    <dt>监控令牌</dt><dd><template v-if="$h.t(m.metricsOn)"><span class="mono small">{{ $h.s($h.add(m.metricsPrefix, '…')) }}</span><span class="muted small" v-if="$h.t(!$h.eq(m.metricsCreated, null))">{{ $h.s($h.add($h.ui.dt(m.metricsCreated), ' 生成')) }}</span></template><span class="muted" v-if="!$h.t(m.metricsOn)">还没有生成，生成以后才开放</span></dd>
                    <dt>最近采集</dt><dd>{{ $h.s(($h.t(!$h.eq(m.metricsLast, null)) ? $h.ui.ago(m.metricsLast) : '这次启动以后还没有采集过')) }}</dd>
                    <dt>有哪些</dt><dd class="small">每个 App 的运行模式、近 30 天活跃设备、累计和今天的有效激活、近 24 小时启动错误；每个渠道线上版本的灰度比例、推送、装上、启动失败和失败率；待审核的口令申请</dd>
                </dl>
                <div class="code" style="margin:14px 0 0">
                    <div class="code-bar"><span>告警规则示例（Prometheus）</span><button class="code-copy" type="button" data-copy-pre>复制</button></div>
<pre v-pre>groups:
  - name: ota
    rules:
      - alert: OtaLaunchFailures
        expr: ota_release_failure_ratio > 0.1 and ota_release_failed_total >= 5
        for: 10m
        annotations:
          summary: "{{ $labels.app }} 的 {{ $labels.channel }} 启动失败偏多"</pre>
                </div>
            </div>
            <div class="card-foot">
                <span class="hint">请求头带 Authorization: Bearer 监控令牌。令牌只能读指标，只在生成时显示一次</span>
                <form method="post" :action.attr="$h.ak('/admin/settings/metrics/off')" data-confirm="关闭监控指标？" data-confirm-body="监控令牌马上失效，Prometheus 再来采集会被拒绝。以后可以重新生成。" data-confirm-ok="关闭" data-confirm-danger v-if="$h.t(m.metricsOn)">
                    <button class="btn btn-ghost btn-sm" type="submit">关闭</button>
                </form>
                <form method="post" :action.attr="$h.ak('/admin/settings/metrics/token')" data-confirm="重新生成监控令牌？" data-confirm-body="旧令牌马上失效，要把 Prometheus 配置里的令牌换成新的。" data-confirm-ok="重新生成" v-if="$h.t(m.metricsOn)">
                    <button class="btn btn-secondary btn-sm" type="submit">重新生成令牌</button>
                </form>
                <form method="post" :action.attr="$h.ak('/admin/settings/metrics/token')" v-if="!$h.t(m.metricsOn)">
                    <button class="btn btn-sm" type="submit"><svg class="i i-sm"><use href="#i-key"></use></svg>生成监控令牌</button>
                </form>
            </div>
        </section>
        </section>

        <dialog id="storage-dialog" class="wide">
            <form data-storage-form method="post" :action.attr="$h.ak('/admin/settings/storage/switch')" autocomplete="off">
                <div class="dlg-head">
                    <h3>切换文件存储</h3>
                    <p>先复制文件并逐个核对，全部到位才换过去。复制时后台照常用，不用重启。</p>
                </div>
                <div class="dlg-body">
                    <div class="opt-grid two" style="margin-bottom:16px">
                        <label class="opt"><input type="radio" name="type" :value.attr="'local'" :checked.attr="$h.t(!$h.t(m.storageS3)) ? 'checked' : null">
                            <span><b>本机存储</b><small>文件存在服务器的数据目录里，App 从本服务器下载</small></span></label>
                        <label class="opt"><input type="radio" name="type" :value.attr="'s3'" :checked.attr="$h.t(m.storageS3) ? 'checked' : null">
                            <span><b>对象存储</b><small>阿里云 OSS、腾讯云 COS、七牛、MinIO 等 S3 兼容存储，可以配 CDN</small></span></label>
                    </div>
                    <div class="storage-fields" data-s3-fields>
                        <div class="field span-2">
                            <label for="st-endpoint">服务地址</label>
                            <input class="input mono" id="st-endpoint" name="endpoint" :value.attr="$h.ak($h.p(m.s3Form, 'endpoint'))" placeholder="https://oss-cn-hangzhou.aliyuncs.com" spellcheck="false">
                        </div>
                        <div class="field">
                            <label for="st-bucket">存储桶</label>
                            <input class="input mono" id="st-bucket" name="bucket" :value.attr="$h.ak($h.p(m.s3Form, 'bucket'))" placeholder="my-ota" spellcheck="false">
                        </div>
                        <div class="field">
                            <label for="st-region">区域</label>
                            <input class="input mono" id="st-region" name="region" :value.attr="$h.ak($h.p(m.s3Form, 'region'))" placeholder="一般不用填" spellcheck="false">
                        </div>
                        <div class="field">
                            <label for="st-access">Access Key</label>
                            <input class="input mono" id="st-access" name="accessKey" :value.attr="$h.ak($h.p(m.s3Form, 'accessKey'))" placeholder="SecretId / AccessKey ID" spellcheck="false" autocomplete="off" data-lpignore="true">
                        </div>
                        <div class="field">
                            <label for="st-secret">Secret Key</label>
                            <input class="input mono" id="st-secret" name="secretKey" type="password" autocomplete="new-password" :placeholder.attr="$h.av(($h.t($h.p(m.s3Form, 'hasSecret')) ? '已保存，不改就留空' : 'SecretKey / AccessKey Secret'))">
                        </div>
                        <div class="field">
                            <label for="st-prefix">文件前缀</label>
                            <input class="input mono" id="st-prefix" name="prefix" :value.attr="$h.ak($h.p(m.s3Form, 'prefix'))" placeholder="ota/" spellcheck="false">
                        </div>
                        <div class="field">
                            <label for="st-public">下载地址（CDN，可选）</label>
                            <input class="input mono" id="st-public" name="publicUrl" :value.attr="$h.ak($h.p(m.s3Form, 'publicUrl'))" placeholder="https://cdn.example.com" spellcheck="false">
                        </div>
                        <label class="check span-2"><input type="checkbox" name="pathStyle" :value.attr="'true'" :checked.attr="$h.t($h.p(m.s3Form, 'pathStyle')) ? 'checked' : null">路径风格访问（MinIO 等自建存储一般要勾上）</label>
                        <p class="hint span-2">服务地址填地域节点，不要带存储桶名，比如阿里云 OSS 填 https://oss-cn-hangzhou.aliyuncs.com，腾讯云 COS 填 https://cos.ap-guangzhou.myqcloud.com。App 直接从这里下载文件，存储桶要设成「公共读」，或者配好 CDN 回源后在「下载地址」填 CDN 地址。</p>                    </div>
                    <div class="storage-result" data-storage-result hidden></div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-storage-test>测试连接</button>
                    <button class="btn" type="submit" data-storage-start>开始切换</button>
                </div>
            </form>
        </dialog>

        <dialog id="domains-dialog" class="wide">
            <form method="post" :action.attr="$h.ak('/admin/settings/domains')" autocomplete="off">
                <div class="dlg-head">
                    <h3>访问域名</h3>
                    <p>后台域名用来打开后台，接口域名给 App 更新用，保存马上生效。</p>
                </div>
                <div class="dlg-body">
                    <div class="pref-row dom-restrict">
                        <div class="pref-text">
                            <b>只允许下面这些域名访问</b>
                            <span>打开后用错域名访问是 404，本机地址 127.0.0.1 不受限制</span>
                        </div>
                        <label class="switch"><input type="checkbox" name="restrict" :value.attr="'true'" :checked.attr="$h.t($h.p(m.inst, 'restrictDomains')) ? 'checked' : null"><span class="sw-track"><span class="sw-thumb"></span></span><span class="sr-only">只允许下面这些域名访问</span></label>
                    </div>
                    <div class="dom-head" aria-hidden="true"><span>域名</span><span>用途</span><span>协议</span><span></span></div>
                    <div class="dom-list" data-dom-list>
                        <template v-for="[L_d, L_dStat] in $h.each($h.p(m.inst, 'domains'))"><template v-if="L_dStat.index">
                        </template><div class="dom-row">
                            <input class="input mono" name="host" :value.attr="$h.ak($h.p(L_d, 'host'))" maxlength="253" placeholder="admin.example.com" aria-label="域名">
                            <select class="select" name="use" aria-label="用途">
                                <option :value.attr="'both'" :selected.attr="$h.t(($h.t($h.p(L_d, 'admin')) &amp;&amp; $h.t($h.p(L_d, 'api')))) ? 'selected' : null">后台和接口</option>
                                <option :value.attr="'admin'" :selected.attr="$h.t(($h.t($h.p(L_d, 'admin')) &amp;&amp; $h.t(!$h.t($h.p(L_d, 'api'))))) ? 'selected' : null">只用于后台</option>
                                <option :value.attr="'api'" :selected.attr="$h.t(($h.t(!$h.t($h.p(L_d, 'admin'))) &amp;&amp; $h.t($h.p(L_d, 'api')))) ? 'selected' : null">只用于接口</option>
                            </select>
                            <select class="select" name="scheme" aria-label="协议">
                                <option :value.attr="'https'" :selected.attr="$h.t($h.p(L_d, 'https')) ? 'selected' : null">HTTPS</option>
                                <option :value.attr="'http'" :selected.attr="$h.t(!$h.t($h.p(L_d, 'https'))) ? 'selected' : null">HTTP</option>
                            </select>
                            <button class="icon-btn" type="button" data-dom-remove aria-label="删除这个域名" title="删除"><svg class="i i-sm"><use href="#i-trash"></use></svg></button>
                        </div></template>
                    </div>
                    <template data-dom-row>
                        <div class="dom-row">
                            <input class="input mono" name="host" maxlength="253" placeholder="admin.example.com" aria-label="域名">
                            <select class="select" name="use" aria-label="用途">
                                <option :value.attr="'both'">后台和接口</option>
                                <option :value.attr="'admin'">只用于后台</option>
                                <option :value.attr="'api'">只用于接口</option>
                            </select>
                            <select class="select" name="scheme" aria-label="协议">
                                <option :value.attr="'https'">HTTPS</option>
                                <option :value.attr="'http'">HTTP</option>
                            </select>
                            <button class="icon-btn" type="button" data-dom-remove aria-label="删除这个域名" title="删除"><svg class="i i-sm"><use href="#i-trash"></use></svg></button>
                        </div>
                    </template>
                    <button class="btn btn-sm btn-secondary dom-add" type="button" data-dom-add><svg class="i i-sm"><use href="#i-plus"></use></svg>添加域名</button>
                    <span class="hint">只填域名，可以带端口，比如 app.example.com:8813；*.example.com 匹配所有子域名。最多 30 个</span>
                    <div class="field dom-public">
                        <label for="dom-public">接口对外地址</label>
                        <input class="input mono" id="dom-public" name="publicUrl" :value.attr="$h.ak(m.publicUrl)" placeholder="https://app.example.com:8813" spellcheck="false" data-dom-public>
                        <span class="hint">App 下载文件、API 文档和接入配置用的地址，留空就用第一个接口域名（带端口）</span>
                        <span class="hint warn-text" v-if="$h.t(m.publicUrlFromStartup)">启动参数里设了 --ota.public-url，以启动参数为准</span>
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">保存</button>
                </div>
            </form>
        </dialog>

        <dialog id="token-new-dialog">
            <form method="post" :action.attr="$h.ak('/admin/apps/token')">
                <input type="hidden" name="scope" :value.attr="'admin'">
                <div class="dlg-head">
                    <h3>新建管理令牌</h3>
                    <p>管理令牌能操作所有 App，只给自己的运维脚本用，别放进 App 也别发给别人。</p>
                </div>
                <div class="dlg-body">
                    <div class="field">
                        <label for="token-name">名称</label>
                        <input class="input" id="token-name" name="name" maxlength="100" required placeholder="例如 备份脚本、监控">
                        <span class="hint">写清楚用在哪里，以后看列表就知道该撤销哪个</span>
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">创建并显示令牌</button>
                </div>
            </form>
        </dialog>

        <dialog id="backup-download-dialog">
            <form method="post" :action.attr="$h.ak('/admin/settings/backup/download')" data-no-swap data-close-on-submit>
                <div class="dlg-head">
                    <h3>下载完整备份</h3>
                    <p>先输你的登录密码，再设一个备份密码，恢复时要用它</p>
                </div>
                <div class="dlg-body">
                    <div class="field"><label for="bd-admin">你的登录密码</label><input class="input" id="bd-admin" type="password" name="adminPassword" required autocomplete="current-password"></div>
                    <div class="row-2">
                        <div class="field"><label for="bd-pwd">备份密码</label><input class="input" id="bd-pwd" type="password" name="backupPassword" minlength="8" required autocomplete="new-password" placeholder="至少 8 位"></div>
                        <div class="field"><label for="bd-pwd2">再输一遍</label><input class="input" id="bd-pwd2" type="password" name="backupPassword2" minlength="8" required autocomplete="new-password"></div>
                    </div>
                    <p class="hint">先在服务器上打包完整备份，按钮上显示进度，打包好自动下载 .otaproj 文件</p>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit"><svg class="i"><use href="#i-download"></use></svg>开始下载</button>
                </div>
            </form>
        </dialog>

        <dialog id="backup-stored-dialog">
            <form method="post" id="backup-stored-form" :action.attr="'#'">
                <div class="dlg-head">
                    <h3 data-role="title">下载备份</h3>
                    <p data-role="sub"></p>
                </div>
                <div class="dlg-body">
                    <div class="field"><label for="bs-admin">你的登录密码</label><input class="input" id="bs-admin" type="password" name="adminPassword" required autocomplete="current-password"></div>
                    <div class="row-2" data-role="new-pwd">
                        <div class="field"><label for="bs-pwd">备份密码</label><input class="input" id="bs-pwd" type="password" name="backupPassword" minlength="8" autocomplete="new-password" placeholder="至少 8 位"></div>
                        <div class="field"><label for="bs-pwd2">再输一遍</label><input class="input" id="bs-pwd2" type="password" name="backupPassword2" minlength="8" autocomplete="new-password"></div>
                    </div>
                    <div class="field" data-role="old-pwd"><label for="bs-old">备份密码</label><input class="input" id="bs-old" type="password" name="backupPassword" autocomplete="off"></div>
                    <p class="hint warn-text" data-role="warn"></p>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit" data-role="ok">下载</button>
                </div>
            </form>
        </dialog>
        </template>

    </main>
</div></template>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(($h.t($h.access.owner()) ? '系统配置' : '外观'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
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
