<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'devices'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'设备统计'"/>
    <template v-for="L_canSettings in [$h.access.can('settings')]"><main class="content">

        <div class="page-head">
            <div class="page-title"><div>
                <h1>设备统计</h1>
                <p>{{ $h.s($h.cat($h.p(m.app, 'name'), ' · 设备在用的版本和启动错误，收不到更新就点「模拟检查更新」')) }}</p>
            </div></div>
            <div class="page-actions">
                <a :class="['btn btn-secondary', $h.av(($h.t($h.eq($h.p(m.devices, 'totalElements'), 0)) ? 'disabled' : null))]" :href.attr="$h.ak($h.ui.url('/admin/devices/export', 'q', m.q, 'platform', m.platform, 'version', m.version))" :title.attr="$h.av(($h.t(m.filtered) ? '导出当前筛选出的设备' : '导出全部设备'))"><svg class="i"><use href="#i-download"></use></svg>导出 CSV</a>
                <button class="btn" type="button" data-diagnose data-hash-click="diagnose"><svg class="i"><use href="#i-stethoscope"></use></svg>模拟检查更新</button>
            </div>
        </div>

        <section class="metrics">
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-activity"></use></svg></span>今日活跃设备</div>
                <div class="metric-value"><span>{{ $h.s($h.numbers.formatInteger($h.z(m.devToday, 'active'), 1, 'COMMA')) }}</span><span :class="['delta', $h.av($h.z(m.devDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.devDelta, null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.devDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.devDelta, 'text')) }}</span></div>
                <div class="metric-foot">{{ $h.s(($h.t($h.eq(m.devYesterday, null)) ? '昨天 —' : $h.cat('昨天 ', $h.z(m.devYesterday, 'active'), ' 台'))) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-plus"></use></svg></span>今日新设备</div>
                <div class="metric-value"><span>{{ $h.s($h.numbers.formatInteger($h.z(m.devToday, 'fresh'), 1, 'COMMA')) }}</span><span :class="['delta', $h.av($h.z(m.devNewDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.devNewDelta, null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.devNewDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.devNewDelta, 'text')) }}</span></div>
                <div class="metric-foot">{{ $h.s($h.cat('第一次检查更新的设备 · 昨天 ', ($h.t($h.eq(m.devYesterday, null)) ? '—' : $h.z(m.devYesterday, 'fresh')))) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-phone"></use></svg></span>活跃设备</div>
                <div class="metric-value">{{ $h.s($h.numbers.formatInteger($h.z(m.dist, 'active'), 1, 'COMMA')) }}</div>
                <div class="metric-foot">{{ $h.s($h.cat('近 30 天检查过更新 · 近 7 天 ', m.devWeek, ' 台')) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-check"></use></svg></span>已是线上版本</div>
                <div class="metric-value"><span>{{ $h.s($h.z(m.dist, 'upToDatePercent')) }}</span><small>%</small></div>
                <div class="metric-foot">{{ $h.s(($h.t($h.z(m.dist, 'hasLive')) ? $h.cat($h.z(m.dist, 'upToDate'), ' 台正在运行渠道的线上版本') : '还没有线上版本')) }}</div>
            </div>
        </section>

        <section class="card section-gap dau-card" data-guide="dev-dau">
            <div class="card-head">
                <h2>每天的活跃设备</h2>
                <span class="muted small">{{ $h.s($h.cat('近 30 天', ($h.t($h.eq(m.devAvg7, null)) ? '' : $h.cat(' · 日均 ', m.devAvg7, ' 台（近 7 天）')), ($h.t(!$h.eq(m.devSince, null)) ? $h.cat(' · ', m.devSince, ' 开始按天记录，之前的日子没有数据') : ''))) }}</span>
                <span class="tail plat-legend"><i class="ios"></i>{{ $h.s($h.cat('iOS ', $h.numbers.formatInteger(m.ios, 1, 'COMMA'))) }}<i class="android"></i>{{ $h.s($h.cat('Android ', $h.numbers.formatInteger(m.android, 1, 'COMMA'))) }}<small>近 30 天</small></span>
            </div>
            <div class="card-body">
                <div class="hours-bars dev-bars dau-bars"><template v-for="[L_d, L_dStat] in $h.each(m.devDays)"><i :class="$h.av(($h.t($h.lt(L_dStat.index, m.devGap)) ? 'none' : ($h.t($h.z(L_d, 'today')) ? 'now' : ($h.t($h.gt($h.z(L_d, 'value'), 0)) ? 'on' : null))))" :style="$h.av($h.cat('--h:', $h.z(L_d, 'px'), ';--ios:', $h.z(L_d, 'iosPct'), '%'))" :title.attr="$h.av(($h.t($h.lt(L_dStat.index, m.devGap)) ? $h.cat($h.z(L_d, 'date'), '：还没开始按天记录') : $h.cat($h.z(L_d, 'date'), '：', $h.z(L_d, 'value'), ' 台 · iOS ', $h.z(L_d, 'ios'), ' · Android ', $h.z(L_d, 'android'))))"></i></template></div>
                <div class="dev-x"><template v-for="[L_d, L_dStat] in $h.each(m.devDays)"><span>{{ $h.s($h.z(L_d, 'label')) }}</span></template></div>
            </div>
        </section>

        <div class="dev-grid section-gap">
        <section class="card" data-guide="dev-dist">
            <div class="card-head">
                <h2>版本分布</h2>
                <span class="muted small">近 30 天每台设备在用的版本，没进灰度的设备还在上一版</span>
                <a class="tail link" :href.attr="$h.ak('/admin/releases')">版本发布<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
            </div>
            <div class="empty" v-if="$h.t($h.lists.isEmpty($h.z(m.dist, 'bars')))">
                <div class="empty-icon"><svg class="i"><use href="#i-chart"></use></svg></div>
                <b>还没有设备检查过更新</b>App 接入后，每次启动检查更新都会出现在这里
            </div>
            <ul class="bars" data-collapse="6" data-collapse-unit="个版本" v-if="!$h.t($h.lists.isEmpty($h.z(m.dist, 'bars')))">
                <template v-for="[L_b, L_bStat] in $h.each($h.z(m.dist, 'bars'))"><template v-if="L_bStat.index">
                </template><li :class="$h.av(($h.t($h.p(L_b, 'live')) ? 'live' : null))">
                    <div class="bar-label">
                        <a class="link-strong" :href.attr="$h.ak($h.ui.url('/admin/devices', 'version', $h.p(L_b, 'key')))"><b>{{ $h.s($h.p(L_b, 'label')) }}</b></a>
                        <span :class="['pill', $h.av(($h.t($h.p(L_b, 'live')) ? '' : 'line'))]" style="margin-left:4px" v-if="$h.t(!$h.eq($h.p(L_b, 'tag'), null))">{{ $h.s($h.p(L_b, 'tag')) }}</span>
                        <small :title.attr="$h.av($h.p(L_b, 'sub'))">{{ $h.s($h.p(L_b, 'sub')) }}</small>
                    </div>
                    <div class="bar-track"><span :style="$h.av($h.cat('width:', ($h.t(($h.t($h.lt($h.p(L_b, 'percent'), 1)) &amp;&amp; $h.t($h.gt($h.p(L_b, 'count'), 0)))) ? 1 : $h.p(L_b, 'percent')), '%'))"></span></div>
                    <div class="bar-num"><b>{{ $h.s($h.numbers.formatInteger($h.p(L_b, 'count'), 1, 'COMMA')) }}</b><small>{{ $h.s($h.add($h.p(L_b, 'percent'), '%')) }}</small></div>
                </li></template>
            </ul>
        </section>
        <section class="card dev-mix">
            <div class="card-head">
                <h2>设备构成</h2>
                <span class="muted small">近 30 天检查过更新的设备</span>
            </div>
            <template v-for="L_pt in [$h.add(m.ios, m.android)]"><div class="mix-body">
                <div class="mix-block">
                    <div class="mix-k">平台</div>
                    <div class="mix-bar" v-if="$h.t($h.gt(L_pt, 0))">
                        <span class="ios" :style="$h.av($h.cat('width:', $h.ui.pctOf(m.ios, L_pt), '%'))" v-if="$h.t($h.gt(m.ios, 0))"></span>
                        <span class="and" :style="$h.av($h.cat('width:', $h.ui.pctOf(m.android, L_pt), '%'))" v-if="$h.t($h.gt(m.android, 0))"></span>
                    </div>
                    <div class="mix-bar" v-if="$h.t($h.eq(L_pt, 0))"></div>
                    <div class="mix-legend">
                        <span><i class="ios"></i>iOS<b>{{ $h.s($h.numbers.formatInteger(m.ios, 1, 'COMMA')) }}</b><small>{{ $h.s($h.add($h.ui.pctOf(m.ios, L_pt), '%')) }}</small></span>
                        <span><i class="and"></i>Android<b>{{ $h.s($h.numbers.formatInteger(m.android, 1, 'COMMA')) }}</b><small>{{ $h.s($h.add($h.ui.pctOf(m.android, L_pt), '%')) }}</small></span>
                    </div>
                </div>
                <div class="mix-block">
                    <div class="mix-k">渠道</div>
                    <ul class="mix-list">
                        <template v-for="[L_s, L_sStat] in $h.each(m.byChannel)"><template v-if="L_sStat.index">
                        </template><li><span class="mono" :title.attr="$h.av($h.p(L_s, 'label'))">{{ $h.s($h.p(L_s, 'label')) }}</span><span class="mix-track"><i :style="$h.av($h.cat('width:', $h.p(L_s, 'pct'), '%'))"></i></span><b>{{ $h.s($h.numbers.formatInteger($h.p(L_s, 'count'), 1, 'COMMA')) }}</b><small>{{ $h.s($h.add($h.p(L_s, 'pct'), '%')) }}</small></li></template>
                        <li class="muted" v-if="$h.t($h.lists.isEmpty(m.byChannel))">还没有设备</li>
                    </ul>
                </div>
                <div class="mix-block">
                    <div class="mix-k">Runtime</div>
                    <ul class="mix-list">
                        <template v-for="[L_s, L_sStat] in $h.each(m.byRuntime)"><template v-if="L_sStat.index">
                        </template><li><span class="mono" :title.attr="$h.av($h.p(L_s, 'label'))">{{ $h.s($h.p(L_s, 'label')) }}</span><span class="mix-track"><i :style="$h.av($h.cat('width:', $h.p(L_s, 'pct'), '%'))"></i></span><b>{{ $h.s($h.numbers.formatInteger($h.p(L_s, 'count'), 1, 'COMMA')) }}</b><small>{{ $h.s($h.add($h.p(L_s, 'pct'), '%')) }}</small></li></template>
                        <li class="muted" v-if="$h.t($h.lists.isEmpty(m.byRuntime))">还没有设备</li>
                    </ul>
                </div>
            </div></template>
        </section>
        </div>

        <section class="card section-gap" v-if="!$h.t($h.lists.isEmpty(m.launchErrors))">
            <div class="card-head">
                <h2>最近的启动错误</h2>
                <span class="muted small">App 启动崩溃后，下次检查更新会带上错误。同样的错误 6 小时只记一次，保留 30 天</span>
            </div>
            <div class="table-wrap">
                <table class="compact">
                    <thead><tr><th>时间</th><th>设备</th><th>版本</th><th>错误</th></tr></thead>
                    <tbody data-collapse="5">
                    <template v-for="[L_e, L_eStat] in $h.each(m.launchErrors)"><template v-if="L_eStat.index">
                    </template><tr>
                        <td class="muted nowrap" :title.attr="$h.av($h.ui.full($h.p(L_e, 'createdAt')))">{{ $h.s($h.ui.ago($h.p(L_e, 'createdAt'))) }}</td>
                        <td class="nowrap">
                            <span class="mono small" :title.attr="$h.av($h.p(L_e, 'clientId'))">{{ $h.s($h.ui.shortId($h.p(L_e, 'clientId'))) }}</span>
                            <span class="muted-line">{{ $h.s($h.add($h.add(($h.t($h.eq($h.p(L_e, 'platform'), 'ios')) ? 'iOS' : ($h.t($h.eq($h.p(L_e, 'platform'), 'android')) ? 'Android' : $h.elvis($h.p(L_e, 'platform'), '—'))), ' · '), $h.elvis($h.p(L_e, 'runtimeVersion'), '—'))) }}</span>
                        </td>
                        <td class="nowrap">
                            <a class="rid link-strong" :href.attr="$h.ak($h.ui.url('/admin/devices', 'version', $h.p(L_e, 'releaseId')))" v-if="$h.t(($h.t(!$h.eq($h.p(L_e, 'releaseId'), null)) &amp;&amp; $h.t($h.c(m.labels, 'containsKey', [$h.p(L_e, 'releaseId')]))))">{{ $h.s($h.c(m.labels, 'get', [$h.p(L_e, 'releaseId')])) }}</a>
                            <span class="mono small muted" v-if="$h.t(($h.t(!$h.eq($h.p(L_e, 'releaseId'), null)) &amp;&amp; $h.t(!$h.t($h.c(m.labels, 'containsKey', [$h.p(L_e, 'releaseId')])))))">{{ $h.s(($h.t($h.eq($h.p(L_e, 'releaseId'), 'embedded')) ? '内置版本' : $h.ui.rid($h.p(L_e, 'releaseId')))) }}</span>
                            <span class="muted" v-if="$h.t($h.eq($h.p(L_e, 'releaseId'), null))">—</span>
                        </td>
                        <td><div class="err-msg">{{ $h.s($h.p(L_e, 'message')) }}</div></td>
                    </tr></template>
                    </tbody>
                </table>
            </div>
        </section>

        <section class="card" data-guide="dev-table">
            <form class="toolbar" method="get" :action.attr="$h.ak('/admin/devices')">
                <div class="seg">
                    <a :href.attr="$h.ak($h.ui.url('/admin/devices', 'q', m.q, 'version', m.version))" :class="$h.av(($h.t($h.eq(m.platform, 'all')) ? 'on' : null))">全部</a>
                    <a :href.attr="$h.ak($h.ui.url('/admin/devices', 'platform', 'ios', 'q', m.q, 'version', m.version))" :class="$h.av(($h.t($h.eq(m.platform, 'ios')) ? 'on' : null))">iOS</a>
                    <a :href.attr="$h.ak($h.ui.url('/admin/devices', 'platform', 'android', 'q', m.q, 'version', m.version))" :class="$h.av(($h.t($h.eq(m.platform, 'android')) ? 'on' : null))">Android</a>
                </div>
                <input type="hidden" name="platform" :value.attr="$h.ak(m.platform)" v-if="!$h.t($h.eq(m.platform, 'all'))">
                <select class="select" name="version" data-autosubmit aria-label="按版本筛选">
                    <option :value.attr="''">全部版本</option>
                    <template v-for="[L_b, L_bStat] in $h.each($h.z(m.dist, 'bars'))"><template v-if="L_bStat.index">
                    </template><option :value.attr="$h.ak($h.p(L_b, 'key'))" :selected.attr="$h.t($h.eq($h.p(L_b, 'key'), m.version)) ? 'selected' : null">{{ $h.s($h.p(L_b, 'label')) }}</option></template>
                </select>
                <label class="search">
                    <svg class="i"><use href="#i-search"></use></svg>
                    <input class="input" type="search" name="q" :value.attr="$h.ak(m.q)" placeholder="设备 ID" aria-label="搜索设备 ID">
                </label>
                <button class="btn btn-secondary" type="submit">筛选</button>
                <a class="link" :href.attr="$h.ak('/admin/devices')" v-if="$h.t(m.filtered)">清除筛选</a>
                <span class="tail muted">{{ $h.s($h.cat('共 ', $h.p(m.devices, 'totalElements'), ' 台')) }}</span>
            </form>

            <div class="empty" v-if="$h.t($h.eq($h.p(m.devices, 'totalElements'), 0))">
                <div class="empty-icon"><svg class="i"><use href="#i-phone"></use></svg></div>
                <b>{{ $h.s(($h.t(m.filtered) ? '没有符合条件的设备' : '还没有设备')) }}</b>
                <span>{{ $h.s(($h.t(m.filtered) ? '设备 ID 是 App 里 expo-updates 生成的 eas-client-id，可以在 App 的诊断信息里看到' : 'App 检查更新时会自动出现在这里')) }}</span>
            </div>

            <div class="table-wrap" v-if="!$h.t($h.eq($h.p(m.devices, 'totalElements'), 0))">
                <table>
                    <thead><tr><th>设备 ID</th><th>平台</th><th>Runtime</th><th>渠道</th><th>当前版本</th><th>最近检查</th><th>第一次出现</th><th class="right">操作</th></tr></thead>
                    <tbody>
                    <template v-for="[L_d, L_dStat] in $h.each(m.devices)"><template v-if="L_dStat.index">
                    </template><template v-for="L_cur in [$h.p(L_d, 'currentUpdateId')]"><template v-for="L_tester in [$h.c(m.testers, 'get', [$h.strings.toLowerCase($h.p(L_d, 'clientId'))])]"><tr>
                        <td>
                            <span class="cell-code">
                                <span class="mono small" :title.attr="$h.av($h.p(L_d, 'clientId'))">{{ $h.s($h.ui.shortId($h.p(L_d, 'clientId'))) }}</span>
                                <button class="icon-btn" type="button" :data-copy.attr="$h.av($h.p(L_d, 'clientId'))" title="复制设备 ID" aria-label="复制设备 ID"><svg class="i i-sm"><use href="#i-copy"></use></svg></button>
                            </span>
                            <span class="muted-line" v-if="$h.t(!$h.eq(L_tester, null))">
                                <span class="pill line" :title.attr="$h.av($h.cat('测试设备，检查更新时改用渠道 ', $h.p(L_tester, 'channel')))"><svg class="i i-sm"><use href="#i-flask"></use></svg><span>{{ $h.s($h.elvis($h.p(L_tester, 'name'), '测试设备')) }}</span></span>
                                <span class="pill" title="灰度白名单：渠道灰度没到 100% 时也先拿新版本" v-if="$h.t($h.p(L_tester, 'rolloutFirst'))">灰度白名单</span>
                            </span>
                        </td>
                        <td>{{ $h.s(($h.t($h.eq($h.p(L_d, 'platform'), 'ios')) ? 'iOS' : ($h.t($h.eq($h.p(L_d, 'platform'), 'android')) ? 'Android' : $h.p(L_d, 'platform')))) }}</td>
                        <td><span class="rt-tag">{{ $h.s($h.elvis($h.p(L_d, 'runtimeVersion'), '—')) }}</span></td>
                        <td class="mono small">
                            <span>{{ $h.s($h.elvis($h.p(L_d, 'channel'), 'production')) }}</span>
                            <span class="muted-line" v-if="$h.t(($h.t(!$h.eq(L_tester, null)) &amp;&amp; $h.t(!$h.eq($h.p(L_tester, 'channel'), $h.elvis($h.p(L_d, 'channel'), 'production')))))">{{ $h.s($h.add('→ ', $h.p(L_tester, 'channel'))) }}</span>
                        </td>
                        <td>
                            <span class="badge badge-line" v-if="$h.t($h.eq(L_cur, 'embedded'))"><span class="dot"></span>内置版本</span>
                            <template v-if="$h.t(($h.t(($h.t(!$h.eq(L_cur, null)) &amp;&amp; $h.t(!$h.eq(L_cur, 'embedded')))) &amp;&amp; $h.t($h.c(m.labels, 'containsKey', [L_cur]))))">
                                <span class="rid">{{ $h.s($h.c(m.labels, 'get', [L_cur])) }}</span>
                                <span :class="['pill', $h.av(($h.t($h.c($h.z(m.dist, 'liveIds'), 'contains', [L_cur])) ? '' : 'line'))]" style="margin-left:4px" v-if="$h.t($h.c($h.z(m.dist, 'tags'), 'containsKey', [L_cur]))">{{ $h.s($h.c($h.z(m.dist, 'tags'), 'get', [L_cur])) }}</span>
                            </template>
                            <span class="muted mono small" :title.attr="$h.av($h.add('不是这个 App 现有的版本：', L_cur))" v-if="$h.t(($h.t(($h.t(!$h.eq(L_cur, null)) &amp;&amp; $h.t(!$h.eq(L_cur, 'embedded')))) &amp;&amp; $h.t(!$h.t($h.c(m.labels, 'containsKey', [L_cur])))))">{{ $h.s($h.ui.rid(L_cur)) }}</span>
                            <span class="muted" v-if="$h.t($h.eq(L_cur, null))">未上报</span>
                        </td>
                        <td class="muted nowrap" :title.attr="$h.av($h.ui.full($h.p(L_d, 'lastSeen')))">{{ $h.s($h.ui.ago($h.p(L_d, 'lastSeen'))) }}</td>
                        <td class="muted nowrap" :title.attr="$h.av($h.ui.full($h.p(L_d, 'firstSeen')))">{{ $h.s($h.ui.dt($h.p(L_d, 'firstSeen'))) }}</td>
                        <td class="actions">
                            <div class="btn-row">
                                <button class="btn btn-ghost btn-sm" type="button" data-diagnose :data-device.attr="$h.av($h.p(L_d, 'clientId'))" :data-platform.attr="$h.av($h.p(L_d, 'platform'))" :data-runtime.attr="$h.av($h.p(L_d, 'runtimeVersion'))" :data-channel.attr="$h.av($h.elvis($h.p(L_d, 'channel'), 'production'))" title="模拟这台设备检查更新">诊断</button>
                                <button class="btn btn-ghost btn-sm" type="button" data-tester :data-device.attr="$h.av($h.p(L_d, 'clientId'))" :data-channel.attr="$h.av($h.elvis($h.p(L_d, 'channel'), 'production'))" title="让这台设备改用测试渠道" v-if="$h.t(($h.t(L_canSettings) &amp;&amp; $h.t($h.eq(L_tester, null))))">设为测试设备</button>
                                <form method="post" :action.attr="$h.ak('/admin/apps/testers/delete')" :data-confirm.attr="$h.av($h.cat('取消测试设备 ', $h.elvis($h.p(L_tester, 'name'), $h.ui.shortId($h.p(L_d, 'clientId'))), '？'))" data-confirm-body="这台设备恢复用 App 里写的渠道。" data-confirm-ok="取消测试设备" v-if="$h.t(($h.t(L_canSettings) &amp;&amp; $h.t(!$h.eq(L_tester, null))))">
                                    <input type="hidden" name="id" :value.attr="$h.ak($h.p(L_tester, 'id'))">
                                    <input type="hidden" name="back" :value.attr="'devices'">
                                    <button class="btn btn-ghost btn-sm" type="submit">取消测试设备</button>
                                </form>
                            </div>
                        </td>
                    </tr></template></template></template>
                    </tbody>
                </table>
            </div>

            <FAdminLayoutPager :m="m" :c="c" :P_p="m.devices" :P_base="$h.ui.url('/admin/devices', 'platform', m.platform, 'version', m.version, 'q', m.q)"/>
        </section>

        <dialog id="diagnose-dialog" class="wide">
            <form id="diagnose-form" autocomplete="off">
                <div class="dlg-head">
                    <h3>模拟检查更新</h3>
                    <p>按这台设备的情况走一遍服务器的判断，告诉你它会拿到哪个版本、为什么</p>
                </div>
                <div class="dlg-body scroll">
                    <div class="field">
                        <label for="dg-device">设备 ID</label>
                        <input class="input mono" id="dg-device" name="device" maxlength="100" placeholder="eas-client-id，可不填（不填时不进灰度）">
                    </div>
                    <div class="row-2" style="grid-template-columns:repeat(3,minmax(0,1fr))">
                        <div class="field">
                            <label for="dg-platform">平台</label>
                            <select class="select" id="dg-platform" name="platform" style="height:38px">
                                <option :value.attr="'ios'">iOS</option>
                                <option :value.attr="'android'">Android</option>
                            </select>
                        </div>
                        <div class="field">
                            <label for="dg-runtime">Runtime</label>
                            <input class="input mono" id="dg-runtime" name="runtime" list="dg-runtimes" maxlength="64" placeholder="例如 1.0.0" required>
                            <datalist id="dg-runtimes"><template v-for="[L_rt, L_rtStat] in $h.each(m.runtimes)"><option :value.attr="$h.ak(L_rt)"></option></template></datalist>
                        </div>
                        <div class="field">
                            <label for="dg-channel">渠道</label>
                            <select class="select" id="dg-channel" name="channel" style="height:38px">
                                <template v-for="[L_ch, L_chStat] in $h.each(m.channels)"><template v-if="L_chStat.index">
                                </template><option :value.attr="$h.ak($h.p(L_ch, 'name'))">{{ $h.s($h.p(L_ch, 'name')) }}</option></template>
                            </select>
                        </div>
                    </div>
                    <div data-role="result" hidden style="margin-top:6px">
                        <div class="hero" data-role="hero" style="padding:4px 0 14px">
                            <div class="hero-icon"><svg class="i"><use href="#i-tick" data-role="hero-icon"></use></svg></div>
                            <div><h2 data-role="verdict"></h2><p data-role="summary"></p></div>
                        </div>
                        <div class="card"><ul class="checks" data-role="steps"></ul></div>
                    </div>
                    <div class="verify-loading" data-role="loading" hidden><span class="spinner"></span><span>正在模拟…</span></div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>关闭</button>
                    <button class="btn" type="submit"><svg class="i"><use href="#i-stethoscope"></use></svg>开始诊断</button>
                </div>
            </form>
        </dialog>

        <dialog id="tester-dialog" v-if="$h.t(L_canSettings)">
            <form method="post" :action.attr="$h.ak('/admin/apps/testers')" autocomplete="off">
                <input type="hidden" name="back" :value.attr="'devices'">
                <input type="hidden" name="clientId">
                <div class="dlg-head">
                    <h3>设为测试设备</h3>
                    <p>测试设备改用下面的渠道检查更新，不用重新打包。新版本先发测试渠道，验证好再推 production</p>
                </div>
                <div class="dlg-body">
                    <div class="field">
                        <label>设备 ID</label>
                        <div class="mono small" data-role="device" style="padding:8px 0;word-break:break-all"></div>
                    </div>
                    <div class="row-2">
                        <div class="field">
                            <label for="ts-name">名字</label>
                            <input class="input" id="ts-name" name="name" maxlength="50" placeholder="例如 张三的 iPhone">
                        </div>
                        <div class="field">
                            <label for="ts-channel">改用渠道</label>
                            <select class="select" id="ts-channel" name="channel" style="height:38px">
                                <template v-for="[L_ch, L_chStat] in $h.each(m.channels)"><template v-if="L_chStat.index">
                                </template><option :value.attr="$h.ak($h.p(L_ch, 'name'))">{{ $h.s($h.p(L_ch, 'name')) }}</option></template>
                            </select>
                        </div>
                    </div>
                    <label class="check" style="margin-top:4px"><input type="checkbox" name="rolloutFirst" :value.attr="'true'">灰度白名单：渠道灰度没到 100% 时也先拿新版本</label>
                    <span class="hint" style="display:block;margin-top:4px">渠道选 production 再勾上，这台设备就能在灰度刚开始时先装上新版本验证</span>
                    <div class="note" v-if="$h.t($h.lt($h.lists.size(m.channels), 2))"><svg class="i"><use href="#i-info"></use></svg>
                        <span>现在只有一个渠道，先到「App 设置 → 渠道与测试设备」建一个测试渠道（比如 beta）</span></div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">保存</button>
                </div>
            </form>
        </dialog>

    </main></template>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('设备统计'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
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
