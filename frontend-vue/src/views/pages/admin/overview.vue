<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'overview'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'当前概览'"/>
    <main class="content">

        <div class="page-head">
            <div class="page-title"><div>
                <h1>当前概览</h1>
                <p>{{ $h.s($h.cat($h.p(m.app, 'name'), ' · 近 30 天 ', m.activeDevices, ' 台设备检查过更新')) }}</p>
            </div></div>
            <div class="page-actions">
                <a class="btn btn-ghost" :href.attr="$h.ak('/admin/screen')" title="全屏展示激活、设备、口令、发布和代理，适合投到大屏"><svg class="i"><use href="#i-monitor"></use></svg>数据大屏</a>
                <button class="btn btn-secondary" type="button" data-board-edit><svg class="i"><use href="#i-grid"></use></svg>调整看板</button>
            </div>
        </div>

        <div class="ov-alerts" v-if="!$h.t($h.lists.isEmpty(m.alerts))">
            <template v-for="[L_a, L_aStat] in $h.each(m.alerts)"><div class="alert-bar">
                <svg class="i"><use href="#i-alert"></use></svg>
                <span class="grow">
                    <b>{{ $h.s($h.cat($h.z(L_a, 'channels'), ' 的 ', $h.ui.rid($h.p($h.z(L_a, 'release'), 'id')), ' 启动失败偏多')) }}</b>：
                    <span>{{ $h.s($h.cat('推送 ', $h.p($h.z(L_a, 'stat'), 'offered'), ' 台，启动失败 ', $h.p($h.z(L_a, 'stat'), 'failed'), ' 台（', $h.p($h.z(L_a, 'stat'), 'failRate'), '%），失败的设备已自动退回之前的版本')) }}</span>
                </span>
                <a class="btn btn-danger btn-sm" :href.attr="$h.ak('/admin/releases')">去处理</a>
            </div></template>
        </div>

        <div class="board-bar" data-board-bar hidden>
            <span class="bb-ico"><svg class="i"><use href="#i-grid"></use></svg></span>
            <div class="bb-text"><b>正在调整看板</b><span class="bb-wide">拖动卡片换位置，别的卡片实时让开；拖右边缘改宽度；拖底边改高度，拉得比旁边的卡片高时，它们会叠成一列排在旁边；布局存在你的账号里</span><span class="bb-mid">按住卡片顶上的工具条拖动换位置，别的卡片实时让开；拖底边改高度；布局存在你的账号里</span><span class="bb-narrow">点 ↑ ↓ 或按住左边的把手拖动换位置，× 收起；布局存在你的账号里</span></div>
            <button class="btn btn-ghost btn-sm" type="button" data-board-auto>高度全部自动</button>
            <button class="btn btn-ghost btn-sm" type="button" data-board-reset>恢复默认</button>
            <button class="btn btn-secondary btn-sm" type="button" data-open="board-dialog"><svg class="i i-sm"><use href="#i-plus"></use></svg>添加卡片</button>
            <button class="btn btn-sm" type="button" data-board-done>完成</button>
        </div>

        <div class="board" data-board :data-layout.attr="$h.av(m.boardLayout)" :data-sig.attr="$h.av(m.boardSig)">
        <div class="bw" data-w="todo" data-name="待处理" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['todo']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['todo']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['todo'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['todo']))">
                <section class="card todo-card" data-guide="ov-todo">
                    <div class="card-head">
                        <h2>待处理</h2>
                        <span class="tail muted small">{{ $h.s(($h.t($h.lists.isEmpty(m.ovTodos)) ? '' : $h.cat($h.lists.size(m.ovTodos), ' 件'))) }}</span>
                    </div>
                    <div class="board-empty ok" v-if="$h.t($h.lists.isEmpty(m.ovTodos))"><svg class="i"><use href="#i-tick"></use></svg><b>都处理完了</b><span>{{ $h.s($h.cat('今天新增激活 ', m.confirmedToday, ' 台，', ($h.t(!$h.eq(m.latest, null)) ? $h.cat('线上版本 ', $h.ui.rid($h.p($h.p(m.latest, 'release'), 'id'))) : '还没有线上版本'))) }}</span></div>
                    <div class="todo-items" v-if="!$h.t($h.lists.isEmpty(m.ovTodos))"><template v-for="[L_t, L_tStat] in $h.each(m.ovTodos)"><a :class="['todo-item', $h.av($h.p(L_t, 'level'))]" :href.attr="$h.ak($h.p(L_t, 'href'))">
                        <i class="todo-dot"></i>
                        <span class="todo-text"><b>{{ $h.s($h.p(L_t, 'title')) }}</b><small>{{ $h.s(($h.t(!$h.eq($h.p(L_t, 'app'), null)) ? $h.cat($h.p(L_t, 'app'), ' · ', $h.p(L_t, 'detail')) : $h.p(L_t, 'detail'))) }}</small></span>
                        <span class="todo-go">{{ $h.s($h.p(L_t, 'action')) }}</span>
                    </a></template></div>
                </section>
        </div>
        <div class="bw" data-w="mode" data-name="运行模式" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['mode']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['mode']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['mode'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['mode']))">
                <section class="card" data-guide="ov-mode">
                    <div class="card-head">
                        <h2>运行模式</h2>
                        <span class="muted small" :title.attr="$h.av($h.ui.full(m.switchedAt))" v-if="$h.t(!$h.eq(m.switchedAt, null))">{{ $h.s($h.add($h.ui.ago(m.switchedAt), ' 切换')) }}</span>
                        <button class="tail link" type="button" data-open="mode-plan-dialog" v-if="$h.t($h.access.can('mode'))"><svg class="i i-sm"><use href="#i-calendar"></use></svg>定时切换</button>
                    </div>
                    <div class="card-body">
                        <div class="mode">
                            <div :class="['mode-letter', $h.av(($h.t($h.eq(m.face, 'B')) ? 'b' : null))]">{{ $h.s(m.face) }}</div>
                            <div>
                                <div class="mode-title">{{ $h.s(($h.t($h.eq(m.face, 'B')) ? 'B 面 · 上线模式' : 'A 面 · 审核模式')) }}</div>
                                <div class="mode-desc">{{ $h.s(($h.t($h.eq(m.face, 'B')) ? '用户提交有效口令就能进 B 面' : '所有用户看到 A 面，提交口令不会生效')) }}</div>
                            </div>
                        </div>
                        <div class="lock-note" style="margin-top:16px" v-if="!$h.t($h.access.can('mode'))"><svg class="i i-sm"><use href="#i-lock"></use></svg>需要「切换 A/B 面」权限才能切换</div>
                        <div class="mode-switch" v-if="$h.t($h.access.can('mode'))">
                            <form method="post" :action.attr="$h.ak('/admin/set')" data-confirm="切换到 A 面（审核模式）？" data-confirm-body="切换后所有用户看到 A 面，用户提交的口令将不再生效。" data-confirm-ok="切换到 A 面">
                                <input type="hidden" name="style" :value.attr="'1'">
                                <button type="submit" :class="['mode-opt', $h.av(($h.t($h.eq(m.face, 'A')) ? 'on' : null))]" :disabled.attr="$h.t($h.eq(m.face, 'A')) ? 'disabled' : null">
                                    <i class="radio"></i><span><b>A 面</b><span>审核模式</span></span>
                                </button>
                            </form>
                            <form method="post" :action.attr="$h.ak('/admin/set')" data-confirm="切换到 B 面（上线模式）？" data-confirm-body="切换后，用户提交有效口令就能进 B 面。" data-confirm-ok="切换到 B 面">
                                <input type="hidden" name="style" :value.attr="'2'">
                                <button type="submit" :class="['mode-opt', $h.av(($h.t($h.eq(m.face, 'B')) ? 'on' : null))]" :disabled.attr="$h.t($h.eq(m.face, 'B')) ? 'disabled' : null">
                                    <i class="radio"></i><span><b>B 面</b><span>上线模式</span></span>
                                </button>
                            </form>
                        </div>
                        <ul class="plan-list" v-if="!$h.t($h.lists.isEmpty(m.modePlans))"><template v-for="[L_p, L_pStat] in $h.each(m.modePlans)"><li>
                            <svg class="i i-sm"><use href="#i-clock"></use></svg>
                            <span class="plan-what"><b :title.attr="$h.av($h.ui.full($h.p(L_p, 'runAt')))">{{ $h.s($h.ui.dt($h.p(L_p, 'runAt'))) }}</b>{{ $h.s($h.p(L_p, 'what')) }}</span>
                            <form method="post" :action.attr="$h.ak($h.link('/admin/schedules/{id}/cancel', [['id', $h.p(L_p, 'id')]]))" data-confirm="取消这个定时切换？" data-confirm-ok="取消计划" v-if="$h.t($h.access.can('mode'))"><button class="link-btn" type="submit">取消</button></form>
                        </li></template></ul>
                    </div>
                </section>
        </div>
        <div class="bw" data-w="steps" data-name="开始使用" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['steps']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['steps']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['steps'])))" v-if="$h.t(($h.t($h.c(m.boardOrder, 'containsKey', ['steps'])) &amp;&amp; $h.t(($h.t($h.lt(m.stepsDone, $h.lists.size(m.steps))) &amp;&amp; $h.t($h.access.can('settings'))))))">
            <section class="card" data-guide="ov-steps">
                <div class="card-head">
                    <h2>开始使用</h2>
                    <span class="muted small">{{ $h.s($h.cat('已完成 ', m.stepsDone, ' / ', $h.lists.size(m.steps), ' 步，全部完成后这里就不显示了')) }}</span>
                </div>
                <ul class="checklist">
                    <template v-for="[L_s, L_it] in $h.each(m.steps)"><template v-if="L_it.index">
                    </template><li :class="$h.av(($h.t($h.z(L_s, 'done')) ? 'done' : null))">
                        <span class="step-no">
                            <svg class="i i-sm" style="stroke-width:3" v-if="$h.t($h.z(L_s, 'done'))"><use href="#i-tick"></use></svg>
                            <template v-if="!$h.t($h.z(L_s, 'done'))">{{ $h.s($h.p(L_it, 'count')) }}</template>
                        </span>
                        <span class="step-text"><b>{{ $h.s($h.z(L_s, 'title')) }}</b><span>{{ $h.s($h.z(L_s, 'text')) }}</span></span>
                        <span class="muted small" v-if="$h.t($h.z(L_s, 'done'))">已完成</span>
                        <a class="btn btn-secondary btn-sm" :href.attr="$h.ak($h.z(L_s, 'link'))" v-if="!$h.t($h.z(L_s, 'done'))">{{ $h.s($h.z(L_s, 'action')) }}</a>
                    </li></template>
                </ul>
            </section>
        </div>
        <div class="bw" data-w="today" data-name="今日有效激活" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['today']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['today']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['today'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['today']))">
            <section class="card today-card" data-guide="ov-codes">
                <div class="card-head"><h2>今日有效激活</h2><span class="live-tag" title="页面开着时每 30 秒自动刷新"><i></i>实时</span><a class="tail link" :href.attr="$h.ak($h.link('/admin/activations', [['status', 'confirmed']]))">激活记录<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="today-body">
                    <div class="today-main">
                        <div class="today-value"><span class="metric-value">{{ $h.s($h.add('+', $h.numbers.formatInteger(m.confirmedToday, 1, 'COMMA'))) }}</span><span :class="['delta', $h.av($h.z(m.todayDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.todayDelta, null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.todayDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.todayDelta, 'text')) }}</span></div>
                        <div class="today-sub">{{ $h.s($h.cat('昨天 ', m.yesterdayCount, ' · 提交 ', m.submittedToday, ' 次', ($h.t($h.gt(m.submittedToday, 0)) ? $h.cat(' · 确认率 ', $h.ui.pctOf(m.confirmedToday, m.submittedToday), '%') : ''))) }}</div>
                    </div>
                    <div class="today-hours">
                        <div class="hours-bars"><template v-for="[L_h, L_hStat] in $h.each(m.hours)"><i :class="$h.av(($h.t($h.z(L_h, 'later')) ? 'later' : ($h.t($h.gt($h.z(L_h, 'value'), 0)) ? ($h.t($h.z(L_h, 'now')) ? 'now' : 'on') : null)))" :style="$h.av($h.cat('height:', $h.z(L_h, 'px'), 'px'))" :title.attr="$h.av(($h.t($h.z(L_h, 'later')) ? $h.cat($h.z(L_h, 'hour'), ' 点：还没到') : $h.cat($h.z(L_h, 'hour'), ' 点：', $h.z(L_h, 'value'), ' 台')))"></i></template></div>
                        <div class="hours-x"><span>0 点</span><span>6 点</span><span>12 点</span><span>18 点</span><span>24 点</span></div>
                        <div class="hours-note">{{ $h.s(($h.t($h.eq(m.peakHour, null)) ? '今天还没有激活' : $h.cat('高峰在 ', m.peakHour, ' 点'))) }}</div>
                    </div>
                    <div class="today-stats">
                        <a class="ts" :href.attr="$h.ak('/admin/agents')" v-if="$h.t($h.access.agents())"><b>{{ $h.s(m.agentsActiveToday) }}<small>{{ $h.s($h.add('/ ', m.agentsEnabled)) }}</small></b><span>今天有激活的代理</span></a>
                        <a class="ts" :href.attr="$h.ak($h.link('/admin/codes', [['status', 'usable']]))" v-if="!$h.t($h.access.agents())"><b>{{ $h.s($h.numbers.formatInteger(m.codeUsable, 1, 'COMMA')) }}</b><span>还能用的口令</span></a>
                        <a class="ts" :href.attr="$h.ak('/admin/codes')"><b>{{ $h.s(m.codesUsedToday) }}</b><span>今天用到的口令</span></a>
                        <a class="ts" :href.attr="$h.ak($h.link('/admin/activations', [['status', 'pending']]))"><b>{{ $h.s($h.numbers.formatInteger(m.pendingTotal, 1, 'COMMA')) }}</b><span>提交了还没确认</span></a>
                        <a :class="['ts', $h.av(($h.t($h.gt(m.pendingRequests, 0)) ? 'warn' : null))]" :href.attr="$h.ak('/admin/agents#requests')" v-if="$h.t($h.access.agents())"><b>{{ $h.s(m.pendingRequests) }}</b><span>口令申请待审核</span></a>
                        <a class="ts" :href.attr="$h.ak($h.link('/admin/activations', [['status', 'confirmed']]))" v-if="!$h.t($h.access.agents())"><b>{{ $h.s($h.add('+', m.weekCount)) }}</b><span>近 7 天有效激活</span></a>
                    </div>
                </div>
            </section>
        </div>
        <div class="bw" data-w="devices" data-name="今日活跃设备" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['devices']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['devices']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['devices'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['devices']))">
            <section class="card today-card dev-card" data-guide="ov-devices">
                <div class="card-head"><h2>今日活跃设备</h2><a class="tail link" :href.attr="$h.ak('/admin/devices')">设备统计<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="today-body">
                    <div class="today-main">
                        <div class="today-value"><span class="metric-value">{{ $h.s($h.numbers.formatInteger($h.z(m.devToday, 'active'), 1, 'COMMA')) }}</span><span :class="['delta', $h.av($h.z(m.devDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.devDelta, null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.devDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.devDelta, 'text')) }}</span></div>
                        <div class="today-sub">{{ $h.s($h.cat('昨天 ', ($h.t($h.eq(m.devYesterday, null)) ? '—' : $h.z(m.devYesterday, 'active')), ' · 今天新设备 ', $h.z(m.devToday, 'fresh'), ($h.t($h.eq(m.devYesterday, null)) ? '' : $h.cat('（昨天 ', $h.z(m.devYesterday, 'fresh'), '）')))) }}</div>
                    </div>
                    <div class="today-hours">
                        <div class="hours-bars dev-bars"><template v-for="[L_d, L_dStat] in $h.each(m.devDays)"><i :class="$h.av(($h.t($h.lt(L_dStat.index, m.devGap)) ? 'none' : ($h.t($h.z(L_d, 'today')) ? 'now' : ($h.t($h.gt($h.z(L_d, 'value'), 0)) ? 'on' : null))))" :style="$h.av($h.cat('height:', $h.z(L_d, 'px'), 'px'))" :title.attr="$h.av(($h.t($h.lt(L_dStat.index, m.devGap)) ? $h.cat($h.z(L_d, 'date'), '：还没开始按天记录') : $h.cat($h.z(L_d, 'date'), '：', $h.z(L_d, 'value'), ' 台')))"></i></template></div>
                        <div class="dev-x"><template v-for="[L_d, L_dStat] in $h.each(m.devDays)"><span>{{ $h.s($h.z(L_d, 'label')) }}</span></template></div>
                        <div class="hours-note">{{ $h.s(($h.t(!$h.eq(m.devSince, null)) ? $h.cat(m.devSince, ' 开始按天记录，之前的日子没有数据') : ($h.t($h.eq(m.devPeakDay, null)) ? '近 14 天还没有设备检查更新' : $h.cat('近 14 天每天的活跃设备，最多是 ', m.devPeakDay)))) }}</div>
                    </div>
                    <div class="today-stats">
                        <a class="ts" :href.attr="$h.ak('/admin/devices')"><b>{{ $h.s($h.numbers.formatInteger(m.devWeek, 1, 'COMMA')) }}</b><span>近 7 天活跃</span></a>
                        <a class="ts" :href.attr="$h.ak('/admin/devices')"><b>{{ $h.s($h.numbers.formatInteger(m.devMonth, 1, 'COMMA')) }}</b><span>近 30 天活跃</span></a>
                        <div class="ts"><b>{{ $h.s(($h.t($h.eq(m.devAvg7, null)) ? '—' : $h.numbers.formatInteger(m.devAvg7, 1, 'COMMA'))) }}</b><span>近 7 天日均</span></div>
                        <div class="ts ts-plat"><b>{{ $h.s(m.devIosPct) }}<small>% iOS</small></b><span class="plat-bar" :title.attr="$h.av($h.cat('今天 iOS ', $h.z(m.devToday, 'ios'), ' 台 · Android ', $h.z(m.devToday, 'android'), ' 台'))"><i :style="$h.av($h.cat('width:', m.devIosPct, '%'))"></i></span></div>
                    </div>
                </div>
            </section>
        </div>
        <div class="bw" data-w="codes" data-name="口令" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['codes']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['codes']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['codes'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['codes']))">
                <section class="card sum-card">
                    <div class="card-head"><h2>口令</h2><a class="tail link" :href.attr="$h.ak('/admin/codes')">口令管理<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                    <div class="sum-body">
                        <div class="sum-value">{{ $h.s($h.numbers.formatInteger(m.codeTotal, 1, 'COMMA')) }}<small>个</small></div>
                        <div class="seg-bar" v-if="$h.t($h.gt(m.codeTotal, 0))"><i class="ok" :style="$h.av($h.cat('width:', $h.ui.pctOf(m.codeUsable, m.codeTotal), '%'))"></i><i class="used" :style="$h.av($h.cat('width:', $h.ui.pctOf(m.codeExhausted, m.codeTotal), '%'))"></i><i class="exp" :style="$h.av($h.cat('width:', $h.ui.pctOf(m.codeExpired, m.codeTotal), '%'))"></i><i class="off" :style="$h.av($h.cat('width:', $h.ui.pctOf(m.codeDisabled, m.codeTotal), '%'))"></i></div>
                        <ul class="sum-legend">
                            <li><a :href.attr="$h.ak($h.link('/admin/codes', [['status', 'usable']]))"><i class="ok"></i>可用<b>{{ $h.s(m.codeUsable) }}</b></a></li>
                            <li><a :href.attr="$h.ak($h.link('/admin/codes', [['status', 'exhausted']]))"><i class="used"></i>已用完<b>{{ $h.s(m.codeExhausted) }}</b></a></li>
                            <li><a :href.attr="$h.ak($h.link('/admin/codes', [['status', 'expired']]))"><i class="exp"></i>已过期<b>{{ $h.s(m.codeExpired) }}</b></a></li>
                            <li><a :href.attr="$h.ak($h.link('/admin/codes', [['status', 'disabled']]))"><i class="off"></i>停用<b>{{ $h.s(m.codeDisabled) }}</b></a></li>
                        </ul>
                        <div class="sum-foot warn" v-if="$h.t($h.gt(m.codeExpiring, 0))"><svg class="i i-sm"><use href="#i-clock"></use></svg>{{ $h.s($h.cat(m.codeExpiring, ' 个口令 7 天内到期')) }}</div>
                    </div>
                </section>
        </div>
        <div class="bw" data-w="agents" data-name="代理" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['agents']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['agents']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['agents'])))" v-if="$h.t(($h.t($h.c(m.boardOrder, 'containsKey', ['agents'])) &amp;&amp; $h.t($h.access.agents())))">
                <section class="card sum-card">
                    <div class="card-head"><h2>代理</h2><a class="tail link" :href.attr="$h.ak('/admin/agents')">代理管理<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                    <div class="sum-body">
                        <div class="sum-value">{{ $h.s(m.agentCount) }}<small>个</small></div>
                        <ul class="sum-list">
                            <li><span>启用中</span><b>{{ $h.s(m.agentsEnabled) }}</b></li>
                            <li><span>今天有激活</span><b>{{ $h.s(m.agentsActiveToday) }}</b></li>
                            <li><span>口令申请待审核</span><b :class="$h.av(($h.t($h.gt(m.pendingRequests, 0)) ? 'warn-text' : null))">{{ $h.s(m.pendingRequests) }}</b></li>
                        </ul>
                    </div>
                </section>
        </div>
        <div class="bw" data-w="total" data-name="累计有效激活" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['total']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['total']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['total'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['total']))">
                <section class="card sum-card">
                    <div class="card-head"><h2>累计有效激活</h2><a class="tail link" :href.attr="$h.ak($h.link('/admin/activations', [['status', 'confirmed']]))">激活记录<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                    <div class="sum-body">
                        <div class="sum-value"><span class="metric-value">{{ $h.s($h.numbers.formatInteger(m.confirmedTotal, 1, 'COMMA')) }}</span><small>台</small></div>
                        <div class="sum-delta"><span :class="['delta', $h.av($h.z(m.weekDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.weekDelta, null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.weekDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.weekDelta, 'text')) }}</span><span>{{ $h.s($h.cat('近 7 天 +', m.weekCount)) }}</span></div>
                        <svg class="sum-spark" viewBox="0 0 120 34" preserveAspectRatio="none" aria-hidden="true"><path class="area" :d.attr="$h.av(m.sparkArea)"></path><path class="line" :d.attr="$h.av(m.sparkLine)"></path></svg>
                    </div>
                </section>
        </div>
        <div class="bw" data-w="hot" data-name="热更新" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['hot']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['hot']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['hot'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['hot']))">
            <section class="card hot-card" data-guide="ov-hot">
                <div class="card-head"><h2>热更新</h2><a class="tail link" :href.attr="$h.ak('/admin/releases')">版本发布<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="metrics">
                    <template v-for="L_st in [m.latestStat]"><a class="metric" :href.attr="$h.ak('/admin/releases')">
                        <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-layers"></use></svg></span>{{ $h.s(($h.t(!$h.eq(m.latest, null)) ? $h.add('最新发布 · ', m.latestChannels) : '最新发布')) }}</div>
                        <div :class="['metric-value mono', $h.av(($h.t($h.eq(m.latest, null)) ? 'muted' : null))]">{{ $h.s(($h.t(!$h.eq(m.latest, null)) ? $h.ui.rid($h.p($h.p(m.latest, 'release'), 'id')) : '未发布')) }}</div>
                        <template v-if="$h.t(($h.t(($h.t(!$h.eq(m.latest, null)) &amp;&amp; $h.t(!$h.eq(L_st, null)))) &amp;&amp; $h.t($h.gt($h.p(L_st, 'offered'), 0))))">
                            <div class="progress-line"><span :style="$h.av($h.cat('width:', $h.p(L_st, 'installRate'), '%'))"></span></div>
                            <div class="metric-foot">{{ $h.s($h.cat('装上 ', $h.p(L_st, 'installed'), ' / 推送 ', $h.p(L_st, 'offered'), ' 台 · ', $h.p(L_st, 'installRate'), '%')) }}</div>
                        </template>
                        <div class="metric-foot" v-if="$h.t(($h.t(!$h.eq(m.latest, null)) &amp;&amp; $h.t(($h.t($h.eq(L_st, null)) || $h.t($h.eq($h.p(L_st, 'offered'), 0))))))">{{ $h.s($h.add($h.ui.ago($h.p(m.latest, 'updatedAt')), '发布，等设备检查更新')) }}</div>
                        <div class="metric-foot" v-if="$h.t($h.eq(m.latest, null))">上传更新包并发布到渠道</div>
                    </a></template>
                    <a class="metric" :href.attr="$h.ak('/admin/devices')">
                        <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-phone"></use></svg></span>版本覆盖</div>
                        <div class="metric-value"><span>{{ $h.s(($h.t($h.z(m.dist, 'hasLive')) ? $h.z(m.dist, 'upToDatePercent') : '—')) }}</span><small v-if="$h.t($h.z(m.dist, 'hasLive'))">%</small></div>
                        <div class="metric-foot">{{ $h.s(($h.t($h.z(m.dist, 'hasLive')) ? $h.cat($h.z(m.dist, 'upToDate'), ' / ', $h.z(m.dist, 'active'), ' 台已是线上版本') : '还没有线上版本')) }}</div>
                    </a>
                    <a class="metric" :href.attr="$h.ak('/admin/devices')">
                        <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-alert"></use></svg></span>启动崩溃 · 24 小时</div>
                        <div :class="['metric-value', $h.av(($h.t($h.gt(m.errors24h, 0)) ? 'danger-text' : null))]">{{ $h.s(m.errors24h) }}</div>
                        <div class="metric-foot">{{ $h.s(($h.t(($h.t(!$h.eq(m.latestStat, null)) &amp;&amp; $h.t($h.gt($h.p(m.latestStat, 'failed'), 0)))) ? $h.cat('最新版本启动失败 ', $h.p(m.latestStat, 'failed'), ' 台') : '最新版本没有启动失败')) }}</div>
                    </a>
                    <a class="metric" :href.attr="$h.ak('/admin/releases')">
                        <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-download"></use></svg></span>增量更新省流量</div>
                        <div class="metric-value"><span>{{ $h.s(($h.t($h.gt($h.add(m.patchDownloads, m.fullDownloads), 0)) ? m.savedPct : '—')) }}</span><small v-if="$h.t($h.gt($h.add(m.patchDownloads, m.fullDownloads), 0))">%</small></div>
                        <div class="metric-foot">{{ $h.s(($h.t($h.gt($h.add(m.patchDownloads, m.fullDownloads), 0)) ? $h.cat('省了 ', $h.ui.bytes(m.bytesSaved), ' · 补丁 ', m.patchDownloads, ' 次 / 完整 ', m.fullDownloads, ' 次') : '还没有设备下载启动包')) }}</div>
                    </a>
                </div>
            </section>
        </div>
        <div class="bw" data-w="trend" data-name="有效激活趋势" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['trend']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['trend']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['trend'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['trend']))">
                <section class="card">
                    <div class="card-head">
                        <h2><svg class="i"><use href="#i-chart"></use></svg>有效激活</h2>
                        <span class="trend-sum">{{ $h.s($h.cat('近 ', m.range, ' 天 ', m.trendTotal, ' 台')) }}<span :class="['delta', $h.av($h.z(m.trendDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.trendDelta, null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.trendDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.trendDelta, 'text')) }}</span></span>
                        <div class="seg seg-sm tail"><template v-for="[L_r, L_rStat] in $h.each(m.ranges)"><a :class="$h.av(($h.t($h.eq(L_r, m.range)) ? 'on' : null))" :href.attr="$h.ak($h.link('/admin', [['range', L_r]]))">{{ $h.s($h.cat(L_r, ' 天')) }}</a></template></div>
                    </div>
                    <div :class="['chart', $h.av($h.cat('range-', m.range))]">
                        <template v-for="[L_d, L_dStat] in $h.each(m.trend)"><template v-if="L_dStat.index">
                        </template><div :class="['chart-col', $h.av(($h.t($h.z(L_d, 'today')) ? 'last' : null))]" :title.attr="$h.av($h.cat($h.z(L_d, 'date'), '：', $h.z(L_d, 'value'), ' 台'))">
                            <span class="chart-val">{{ $h.s(($h.t($h.gt($h.z(L_d, 'value'), 0)) ? $h.z(L_d, 'value') : '')) }}</span>
                            <span class="chart-bar" :style="$h.av($h.cat('height:', $h.z(L_d, 'px'), 'px'))"></span>
                        </div></template>
                    </div>
                    <div class="chart-x"><template v-for="[L_d, L_dStat] in $h.each(m.trend)"><span :class="$h.av(($h.t($h.z(L_d, 'today')) ? 'last' : null))">{{ $h.s($h.z(L_d, 'label')) }}</span></template></div>
                </section>
        </div>
        <div class="bw" data-w="live" data-name="线上版本" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['live']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['live']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['live'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['live']))">
                <section class="card" data-guide="ov-live">
                    <div class="card-head">
                        <h2>线上版本</h2>
                        <a class="tail link" :href.attr="$h.ak('/admin/releases')">管理版本<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
                    </div>
                    <div class="board-empty" v-if="$h.t($h.lists.isEmpty(m.live))"><svg class="i"><use href="#i-package"></use></svg><b>还没有发布版本</b><span>上传更新包并发布到渠道后，这里显示每个渠道、每个 Runtime 的线上版本</span></div>
                    <div class="rt-list" v-if="!$h.t($h.lists.isEmpty(m.live))">
                        <template v-for="[L_l, L_lStat] in $h.each(m.live)"><template v-if="L_lStat.index">
                        </template><div class="rt-row">
                            <span class="rt-tag">{{ $h.s($h.add($h.add($h.p(L_l, 'channel'), ' · '), $h.p(L_l, 'runtime'))) }}</span>
                            <div class="rt-main" v-if="$h.t($h.p(L_l, 'rollbackToEmbedded'))">
                                <div class="rt-ver">安装包内置版本</div>
                                <div class="rt-meta">{{ $h.s($h.add('回退于 ', $h.ui.dt($h.p(L_l, 'rollbackAt')))) }}</div>
                                <div class="rt-meta rt-act" v-if="$h.t(!$h.eq($h.p(L_l, 'activation'), null))" title="口令更新包：新装 App 输入口令激活时下载"><svg class="i i-sm"><use href="#i-key"></use></svg>{{ $h.s($h.cat('口令更新 ', $h.ui.rid($h.p($h.p(L_l, 'activation'), 'id')))) }}</div>
                            </div>
                            <div class="rt-main" v-if="$h.t(($h.t(!$h.t($h.p(L_l, 'rollbackToEmbedded'))) &amp;&amp; $h.t(!$h.eq($h.p(L_l, 'release'), null))))">
                                <div class="rt-ver">{{ $h.s($h.add($h.ui.rid($h.p($h.p(L_l, 'release'), 'id')), ($h.t(!$h.eq($h.p($h.p(L_l, 'release'), 'message'), null)) ? $h.add(' · ', $h.p($h.p(L_l, 'release'), 'message')) : ''))) }}</div>
                                <div class="rt-meta">{{ $h.s($h.add($h.add($h.add($h.add($h.add($h.ui.source($h.p($h.p(L_l, 'release'), 'source')), ' · '), $h.ui.size($h.p($h.p(L_l, 'release'), 'totalBytes'))), ' · '), $h.ui.dt($h.p($h.p(L_l, 'release'), 'createdAt'))), ($h.t($h.p($h.p(L_l, 'release'), 'external')) ? $h.add(' · ', $h.ui.host($h.p($h.p(L_l, 'release'), 'externalBaseUrl'))) : ''))) }}</div>
                                <div class="rt-meta rt-act" v-if="$h.t(!$h.eq($h.p(L_l, 'activation'), null))" title="口令更新包：新装 App 输入口令激活时下载"><svg class="i i-sm"><use href="#i-key"></use></svg>{{ $h.s($h.cat('口令更新 ', $h.ui.rid($h.p($h.p(L_l, 'activation'), 'id')))) }}</div>
                            </div>
                            <div class="rt-main muted" v-if="$h.t(($h.t(!$h.t($h.p(L_l, 'rollbackToEmbedded'))) &amp;&amp; $h.t($h.eq($h.p(L_l, 'release'), null))))"><div class="rt-ver">没有线上版本</div>
                                <div class="rt-meta rt-act" v-if="$h.t(!$h.eq($h.p(L_l, 'activation'), null))" title="口令更新包：新装 App 输入口令激活时下载"><svg class="i i-sm"><use href="#i-key"></use></svg>{{ $h.s($h.cat('口令更新 ', $h.ui.rid($h.p($h.p(L_l, 'activation'), 'id')))) }}</div>
                            </div>
                            <span class="badge badge-line" v-if="$h.t($h.p(L_l, 'rollbackToEmbedded'))">内置版本</span>
                            <span class="badge badge-solid" v-if="$h.t(($h.t(!$h.t($h.p(L_l, 'rollbackToEmbedded'))) &amp;&amp; $h.t(!$h.eq($h.p(L_l, 'release'), null))))"><span class="dot"></span><span>{{ $h.s(($h.t($h.lt($h.p(L_l, 'rollout'), 100)) ? $h.add($h.add('灰度 ', $h.p(L_l, 'rollout')), '%') : '线上')) }}</span></span>
                        </div></template>
                    </div>
                </section>
        </div>
        <div class="bw" data-w="coverage" data-name="版本覆盖" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['coverage']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['coverage']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['coverage'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['coverage']))">
                <section class="card">
                    <div class="card-head">
                        <h2>版本覆盖</h2>
                        <span class="muted small" v-if="$h.t(($h.t($h.z(m.dist, 'hasLive')) &amp;&amp; $h.t($h.gt($h.z(m.dist, 'active'), 0))))">{{ $h.s($h.cat($h.z(m.dist, 'upToDatePercent'), '% 的设备已是线上版本')) }}</span>
                        <a class="tail link" :href.attr="$h.ak('/admin/devices')">设备统计<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
                    </div>
                    <div class="board-empty" v-if="$h.t($h.lists.isEmpty($h.z(m.dist, 'bars')))"><svg class="i"><use href="#i-phone"></use></svg><b>还没有设备检查过更新</b><span>App 接入后，这里显示每个版本有多少台设备在用</span></div>
                    <ul class="bars" v-if="!$h.t($h.lists.isEmpty($h.z(m.dist, 'bars')))">
                        <template v-for="[L_b, L_it] in $h.each($h.z(m.dist, 'bars'))"><template v-if="L_it.index">
                        </template><li :class="$h.av(($h.t($h.p(L_b, 'live')) ? 'live' : null))" v-if="$h.t($h.lt($h.p(L_it, 'index'), 5))">
                            <div class="bar-label">
                                <b>{{ $h.s($h.p(L_b, 'label')) }}</b>
                                <span :class="['pill', $h.av(($h.t($h.p(L_b, 'live')) ? '' : 'line'))]" style="margin-left:4px" v-if="$h.t(!$h.eq($h.p(L_b, 'tag'), null))">{{ $h.s($h.p(L_b, 'tag')) }}</span>
                                <small :title.attr="$h.av($h.p(L_b, 'sub'))">{{ $h.s($h.p(L_b, 'sub')) }}</small>
                            </div>
                            <div class="bar-track"><span :style="$h.av($h.cat('width:', ($h.t(($h.t($h.lt($h.p(L_b, 'percent'), 1)) &amp;&amp; $h.t($h.gt($h.p(L_b, 'count'), 0)))) ? 1 : $h.p(L_b, 'percent')), '%'))"></span></div>
                            <div class="bar-num"><b>{{ $h.s($h.numbers.formatInteger($h.p(L_b, 'count'), 1, 'COMMA')) }}</b><small>{{ $h.s($h.add($h.p(L_b, 'percent'), '%')) }}</small></div>
                        </li></template>
                    </ul>
                </section>
        </div>
        <div class="bw" data-w="ranking" data-name="代理排行" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['ranking']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['ranking']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['ranking'])))" v-if="$h.t(($h.t($h.c(m.boardOrder, 'containsKey', ['ranking'])) &amp;&amp; $h.t(($h.t($h.gt(m.agentCount, 0)) &amp;&amp; $h.t($h.access.agents())))))">
                <template v-for="L_top in [$h.p($h.i(m.ranking, 0), 'today')]"><section class="card">
                    <div class="card-head">
                        <h2>代理排行</h2>
                        <span class="muted small">按今日有效激活</span>
                        <a class="tail link" :href.attr="$h.ak('/admin/agents')">代理管理<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
                    </div>
                    <ul class="rank-list">
                        <template v-for="[L_r, L_it] in $h.each(m.ranking)"><template v-for="L_a in [$h.p(L_r, 'agent')]"><li>
                            <span class="rank-no">{{ $h.s($h.p(L_it, 'count')) }}</span>
                            <span class="who-ava">{{ $h.s(($h.t($h.strings.isEmpty($h.p(L_a, 'name'))) ? '?' : $h.strings.substring($h.p(L_a, 'name'), 0, 1))) }}</span>
                            <span class="rank-name"><b>{{ $h.s($h.p(L_a, 'name')) }}</b><small>{{ $h.s($h.cat('累计 ', $h.numbers.formatInteger($h.p(L_r, 'total'), 1, 'COMMA'), ' · 口令 ', $h.p(L_r, 'codeCount'), ' · ', ($h.t(!$h.eq($h.p(L_a, 'lastLoginAt'), null)) ? $h.add($h.ui.ago($h.p(L_a, 'lastLoginAt')), ' 登录') : '从未登录'))) }}</small></span>
                            <span class="usage-bar"><span :style="$h.av($h.cat('width:', ($h.t($h.gt(L_top, 0)) ? $h.div($h.mul($h.p(L_r, 'today'), 100), L_top) : 0), '%'))"></span></span>
                            <b class="rank-today num">{{ $h.s($h.add('+', $h.p(L_r, 'today'))) }}</b>
                        </li></template></template>
                    </ul>
                </section></template>
        </div>
        <div class="bw" data-w="records" data-name="最近激活" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['records']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['records']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['records'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['records']))">
                <section class="card">
                    <div class="card-head">
                        <h2>最近激活</h2>
                        <a class="tail link" :href.attr="$h.ak('/admin/activations')">全部记录<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
                    </div>
                    <div class="board-empty" v-if="$h.t($h.lists.isEmpty(m.recentRecords))"><svg class="i"><use href="#i-inbox"></use></svg><b>暂无激活记录</b><span>B 面上线模式下，用户提交口令后会出现在这里</span></div>
                    <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty(m.recentRecords))">
                        <table class="rec-table">
                            <thead><tr><th>时间</th><th>口令</th><th>设备</th><th>状态</th></tr></thead>
                            <tbody>
                            <template v-for="[L_r, L_rStat] in $h.each(m.recentRecords)"><template v-if="L_rStat.index">
                            </template><tr>
                                <td class="muted nowrap" :title.attr="$h.av($h.ui.full($h.p(L_r, 'activatedAt')))">{{ $h.s($h.ui.ago($h.p(L_r, 'activatedAt'))) }}</td>
                                <td><a class="mono link-strong" :href.attr="$h.ak($h.link('/admin/activations', [['code', $h.p(L_r, 'codeText')]]))">{{ $h.s($h.p(L_r, 'codeText')) }}</a></td>
                                <td class="mono muted small" :title.attr="$h.av($h.p(L_r, 'deviceId'))">{{ $h.s($h.ui.shortId($h.p(L_r, 'deviceId'))) }}</td>
                                <td><FAdminLayoutRecordStatus :m="m" :c="c" :P_r="L_r"/></td>
                            </tr></template>
                            </tbody>
                        </table>
                    </div>
                </section>
        </div>
        <div class="bw" data-w="ops" data-name="最近操作" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['ops']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['ops']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['ops'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['ops']))">
                <section class="card">
                    <div class="card-head">
                        <h2>最近操作</h2>
                        <a class="tail link" :href.attr="$h.ak($h.link('/admin/logs', [['type', 'admin'], ['app', $h.p(m.app, 'slug')]]))">全部日志<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
                    </div>
                    <div class="board-empty" v-if="$h.t($h.lists.isEmpty(m.recentOps))"><svg class="i"><use href="#i-file"></use></svg><b>暂无操作</b><span>这个 App 的切换、发布、口令变更会记录在这里</span></div>
                    <ul class="feed" v-if="!$h.t($h.lists.isEmpty(m.recentOps))">
                        <template v-for="[L_l, L_lStat] in $h.each(m.recentOps)"><template v-if="L_lStat.index">
                        </template><template v-for="L_who in [($h.t(($h.t(!$h.eq($h.p(L_l, 'operator'), null)) &amp;&amp; $h.t(!$h.t($h.strings.isEmpty($h.p(L_l, 'operator')))))) ? $h.elvis($h.c(m.operatorNames, 'get', [$h.p(L_l, 'operator')]), $h.p(L_l, 'operator')) : '系统')]"><li>
                            <span :class="['feed-ava', $h.av(($h.t(($h.t($h.eq($h.p(L_l, 'operator'), null)) || $h.t($h.strings.startsWith(L_who, '令牌')))) ? 'sys' : null))]">{{ $h.s(($h.t($h.strings.startsWith(L_who, '令牌')) ? '令' : $h.strings.substring(L_who, 0, 1))) }}</span>
                            <div class="feed-main">
                                <div class="feed-line">
                                    <b>{{ $h.s(L_who) }}</b>
                                    <span :class="['tag', $h.av($h.ui.tone($h.p(L_l, 'action')))]">{{ $h.s($h.ui.action($h.p(L_l, 'action'))) }}</span>
                                    <span class="feed-time" :title.attr="$h.av($h.ui.full($h.p(L_l, 'createdAt')))">{{ $h.s($h.ui.ago($h.p(L_l, 'createdAt'))) }}</span>
                                </div>
                                <div class="feed-text" :title.attr="$h.av($h.p(L_l, 'detail'))">{{ $h.s($h.p(L_l, 'detail')) }}</div>
                            </div>
                        </li></template></template>
                    </ul>
                </section>
        </div>
        <div class="bw" data-w="quick" data-name="快捷操作" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['quick']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['quick']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['quick'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['quick']))">
            <section class="card quick-card">
                <div class="card-head"><h2>快捷操作</h2></div>
                <div class="qk-grid">
                    <a class="qk" :href.attr="$h.ak('/admin/codes#new')" v-if="$h.t($h.access.can('codes'))"><svg class="i"><use href="#i-plus"></use></svg><b>新建口令</b><small>建一个指定的口令</small></a>
                    <a class="qk" :href.attr="$h.ak('/admin/codes#batch')" v-if="$h.t($h.access.can('codes'))"><svg class="i"><use href="#i-layers"></use></svg><b>批量生成</b><small>一次生成一批随机口令</small></a>
                    <a class="qk" :href.attr="$h.ak('/admin/releases#upload')" v-if="$h.t($h.access.can('release'))"><svg class="i"><use href="#i-upload"></use></svg><b>上传更新包</b><small>expo export 导出的包</small></a>
                    <a class="qk" :href.attr="$h.ak('/admin/agents#new')" v-if="$h.t($h.access.agents())"><svg class="i"><use href="#i-users"></use></svg><b>新建代理</b><small>开账号、分口令</small></a>
                    <a class="qk" :href.attr="$h.ak('/admin/agents#notices')" v-if="$h.t($h.access.agents())"><svg class="i"><use href="#i-megaphone"></use></svg><b>发公告</b><small>代理登录就能看到</small></a>
                    <a class="qk" :href.attr="$h.ak('/admin/devices#diagnose')"><svg class="i"><use href="#i-stethoscope"></use></svg><b>模拟检查更新</b><small>看一台设备会拿到哪个版本</small></a>
                    <a class="qk" :href.attr="$h.ak('/admin/activations')"><svg class="i"><use href="#i-activity"></use></svg><b>激活记录</b><small>按代理、App、时间查</small></a>
                    <a class="qk" :href.attr="$h.ak('/admin/app')"><svg class="i"><use href="#i-sliders"></use></svg><b>App 设置</b><small>渠道、测试设备、通知</small></a>
                </div>
            </section>
        </div>
        <div class="bw" data-w="note" data-name="便签" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['note']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['note']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['note'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['note']))">
            <section class="card note-card">
                <div class="card-head"><h2>便签</h2><span class="tail muted small" data-note-state>只有你看得到</span></div>
                <label class="note-paper"><textarea class="note-area" data-board-note maxlength="500" spellcheck="false" aria-label="便签" placeholder="记点要办的事，比如：周五 20 点切 B 面、给张三补 50 个口令" :value="$h.s(m.boardNote)"></textarea></label>
            </section>
        </div>
        <div class="bw" data-w="goal" data-name="今日目标" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['goal']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['goal']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['goal'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['goal']))">
            <template v-for="L_pct in [$h.ui.pctOf(m.confirmedToday, m.boardGoal)]"><section :class="['card goal-card', $h.av(($h.t(($h.t(!$h.eq(m.boardGoal, null)) &amp;&amp; $h.t($h.ge(m.confirmedToday, m.boardGoal)))) ? 'reached' : null))]">
                <div class="card-head"><h2>今日目标</h2><button class="link-btn tail" type="button" data-goal-edit v-if="$h.t(!$h.eq(m.boardGoal, null))">改目标</button></div>
                <div class="goal-body" v-if="$h.t(!$h.eq(m.boardGoal, null))">
                    <div :class="['ring', $h.av(($h.t($h.eq(L_pct, 0)) ? 'zero' : null))]" :style="$h.av($h.cat('--p:', L_pct))">
                        <svg viewBox="0 0 120 120" aria-hidden="true"><circle class="ring-bg" cx="60" cy="60" r="50"></circle><circle class="ring-fg" cx="60" cy="60" r="50" pathLength="100"></circle></svg>
                        <div class="ring-in"><b>{{ $h.s($h.add(L_pct, '%')) }}</b><small>{{ $h.s(($h.t($h.ge(m.confirmedToday, m.boardGoal)) ? '完成了' : '已完成')) }}</small></div>
                    </div>
                    <div class="goal-text">
                        <b>{{ $h.s($h.cat('+', $h.numbers.formatInteger(m.confirmedToday, 1, 'COMMA'))) }}<small>{{ $h.s($h.cat(' / ', $h.numbers.formatInteger(m.boardGoal, 1, 'COMMA'))) }}</small></b>
                        <span>{{ $h.s(($h.t($h.ge(m.confirmedToday, m.boardGoal)) ? $h.cat('超出 ', $h.sub(m.confirmedToday, m.boardGoal), ' 台') : $h.cat('还差 ', $h.sub(m.boardGoal, m.confirmedToday), ' 台'))) }}</span>
                    </div>
                </div>
                <div class="board-empty accent" v-if="$h.t($h.eq(m.boardGoal, null))"><svg class="i"><use href="#i-target"></use></svg><b>给今天定个目标</b><span>{{ $h.s($h.cat('今天已经有效激活 ', m.confirmedToday, ' 台')) }}</span><button class="btn btn-secondary btn-sm" type="button" data-goal-edit>设目标</button></div>
                <form class="goal-form" data-goal-form hidden>
                    <input class="input num" name="goal" type="number" min="1" max="1000000" required aria-label="今天的有效激活目标" placeholder="比如 200" :value.attr="$h.av(m.boardGoal)">
                    <button class="btn btn-sm" type="submit">保存</button>
                    <button class="btn btn-ghost btn-sm" type="button" data-goal-cancel>取消</button>
                </form>
            </section></template>
        </div>
        <div class="bw" data-w="week" data-name="本周对比" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['week']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['week']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['week'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['week']))">
            <section class="card week-card">
                <div class="card-head"><h2>本周</h2><span :class="['delta tail', $h.av($h.z(m.weekVs, 'dir'))]" v-if="$h.t(!$h.eq(m.weekVs, null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.weekVs, 'dir')))"></use></svg>{{ $h.s($h.z(m.weekVs, 'text')) }}</span></div>
                <div class="wk-body">
                    <div class="wk-num"><b>{{ $h.s($h.cat('+', $h.numbers.formatInteger(m.weekCur, 1, 'COMMA'))) }}</b><span>{{ $h.s($h.cat('上周同期 ', m.weekPrev, ' · 上周共 ', m.weekPrevAll)) }}</span></div>
                    <div class="wk-bars"><template v-for="[L_d, L_dStat] in $h.each(m.weekDays)"><div :class="['wk-col', $h.av(($h.t($h.p(L_d, 'today')) ? 'now' : ($h.t($h.p(L_d, 'later')) ? 'later' : null)))]" :title.attr="$h.av(($h.t($h.p(L_d, 'later')) ? $h.cat('周', $h.p(L_d, 'label'), '：上周 ', $h.p(L_d, 'prev'), ' 台') : $h.cat('周', $h.p(L_d, 'label'), '：', $h.p(L_d, 'cur'), ' 台，上周 ', $h.p(L_d, 'prev'), ' 台')))">
                        <div class="wk-plot"><i class="prev" :style="$h.av($h.cat('height:', $h.p(L_d, 'prevPx'), 'px'))"></i><i class="cur" :style="$h.av($h.cat('height:', $h.p(L_d, 'px'), 'px'))"></i></div>
                        <span>{{ $h.s($h.p(L_d, 'label')) }}</span>
                    </div></template></div>
                    <div class="wk-legend"><span><i class="cur"></i>本周</span><span><i class="prev"></i>上周</span></div>
                </div>
            </section>
        </div>
        <div class="bw" data-w="funnel" data-name="激活转化" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['funnel']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['funnel']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['funnel'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['funnel']))">
            <template v-for="L_rate in [$h.ui.pctOf(m.confirmedToday, m.submittedToday)]"><section class="card funnel-card">
                <div class="card-head"><h2>激活转化</h2><span class="tail muted small">今天</span></div>
                <div class="fn-body">
                    <div :class="['ring ring-sm', $h.av(($h.t($h.eq(L_rate, 0)) ? 'zero' : null))]" :style="$h.av($h.cat('--p:', L_rate))">
                        <svg viewBox="0 0 120 120" aria-hidden="true"><circle class="ring-bg" cx="60" cy="60" r="50"></circle><circle class="ring-fg" cx="60" cy="60" r="50" pathLength="100"></circle></svg>
                        <div class="ring-in"><b>{{ $h.s(($h.t($h.gt(m.submittedToday, 0)) ? $h.add(L_rate, '%') : '—')) }}</b><small>确认率</small></div>
                    </div>
                    <ul class="fn-rows">
                        <li><span><i class="sub"></i>已提交</span><b>{{ $h.s($h.numbers.formatInteger(m.submittedToday, 1, 'COMMA')) }}</b></li>
                        <li><span><i class="ok"></i>已确认</span><b>{{ $h.s($h.numbers.formatInteger(m.confirmedToday, 1, 'COMMA')) }}</b></li>
                    </ul>
                </div>
                <div class="fn-foot">{{ $h.s(($h.t($h.gt(m.funnelSubmitted7, 0)) ? $h.cat('近 7 天提交 ', m.funnelSubmitted7, '，确认 ', m.funnelConfirmed7, '，确认率 ', $h.ui.pctOf(m.funnelConfirmed7, m.funnelSubmitted7), '%') : '近 7 天还没有人提交口令')) }}</div>
            </section></template>
        </div>
        <div class="bw" data-w="heat" data-name="激活时段" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['heat']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['heat']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['heat'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['heat']))">
            <section class="card heat-card">
                <div class="card-head">
                    <h2>激活时段</h2>
                    <span class="muted small">近 4 周</span>
                    <span class="hm-legend tail" aria-hidden="true">少<i class="lv1"></i><i class="lv2"></i><i class="lv3"></i><i class="lv4"></i>多</span>
                </div>
                <div class="hm">
                    <template v-for="[L_r, L_rStat] in $h.each(m.heatRows)"><div class="hm-row">
                        <span class="hm-day">{{ $h.s($h.p(L_r, 'label')) }}</span>
                        <div class="hm-cells"><template v-for="[L_c, L_cStat] in $h.each($h.p(L_r, 'cells'))"><i :class="$h.av($h.cat('lv', $h.p(L_c, 'lv')))" :title.attr="$h.av($h.cat($h.p(L_r, 'label'), ' ', $h.p(L_c, 'hour'), ' 点：', $h.p(L_c, 'n'), ' 台'))"></i></template></div>
                    </div></template>
                    <div class="hm-row hm-x"><span class="hm-day"></span><div class="hm-hours"><span>0 点</span><span>6 点</span><span>12 点</span><span>18 点</span><span>23 点</span></div></div>
                </div>
                <div class="hm-foot">{{ $h.s($h.elvis(m.heatPeak, '近 4 周还没有有效激活')) }}</div>
            </section>
        </div>
        <div class="bw" data-w="topcodes" data-name="热门口令" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['topcodes']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['topcodes']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['topcodes'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['topcodes']))">
            <section class="card">
                <div class="card-head"><h2>热门口令</h2><span class="muted small">近 7 天</span><a class="tail link" :href.attr="$h.ak('/admin/codes')">口令管理<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="board-empty" v-if="$h.t($h.lists.isEmpty(m.boardTopCodes))"><svg class="i"><use href="#i-flame"></use></svg><b>近 7 天还没有有效激活</b><span>用户在这个 App 里激活后，这里按次数排出口令</span></div>
                <ol class="tc-list" v-if="!$h.t($h.lists.isEmpty(m.boardTopCodes))"><template v-for="[L_k, L_kStat] in $h.each(m.boardTopCodes)"><li>
                    <span :class="['tc-no', $h.av(($h.t($h.lt(L_kStat.index, 3)) ? 'top' : null))]">{{ $h.s(L_kStat.count) }}</span>
                    <span class="tc-main"><a class="mono" :href.attr="$h.ak($h.link('/admin/codes', [['q', $h.p(L_k, 'code')]]))">{{ $h.s($h.p(L_k, 'code')) }}</a><small>{{ $h.s($h.cat($h.elvis($h.p(L_k, 'agent'), '未分配'), ' · 用了 ', $h.p(L_k, 'usedCount'), ($h.t($h.gt($h.p(L_k, 'maxUses'), 0)) ? $h.cat(' / ', $h.p(L_k, 'maxUses')) : ' 次'))) }}</small></span>
                    <span class="tc-bar"><i :style="$h.av($h.cat('width:', $h.p(L_k, 'pct'), '%'))"></i></span>
                    <b class="tc-n">{{ $h.s($h.add('+', $h.p(L_k, 'count'))) }}</b>
                </li></template></ol>
            </section>
        </div>
        <div class="bw" data-w="expiring" data-name="快到期的口令" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['expiring']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['expiring']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['expiring'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['expiring']))">
            <section class="card">
                <div class="card-head"><h2>快到期的口令</h2><a class="tail link" :href.attr="$h.ak('/admin/codes')">口令管理<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="board-empty ok" v-if="$h.t($h.lists.isEmpty(m.boardExpiring))"><svg class="i"><use href="#i-tick"></use></svg><b>7 天内没有到期的口令</b><span>设了有效期的口令快到期时会排在这里</span></div>
                <ul class="mini-list" v-if="!$h.t($h.lists.isEmpty(m.boardExpiring))"><template v-for="[L_x, L_xStat] in $h.each(m.boardExpiring)"><li>
                    <span class="ml-main"><b class="mono">{{ $h.s($h.p(L_x, 'code')) }}</b><small>{{ $h.s($h.elvis($h.p(L_x, 'agent'), '未分配')) }}</small></span>
                    <span :class="['left-chip', $h.av(($h.t($h.le($h.p(L_x, 'daysLeft'), 1)) ? 'hot' : ($h.t($h.le($h.p(L_x, 'daysLeft'), 3)) ? 'warm' : null)))]" :title.attr="$h.av($h.cat('到期时间 ', $h.ui.full($h.p(L_x, 'expiresAt'))))">{{ $h.s(($h.t($h.le($h.p(L_x, 'daysLeft'), 0)) ? '今天到期' : $h.cat('还剩 ', $h.p(L_x, 'daysLeft'), ' 天'))) }}</span>
                </li></template></ul>
            </section>
        </div>
        <div class="bw" data-w="requests" data-name="口令申请" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['requests']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['requests']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['requests'])))" v-if="$h.t(($h.t($h.c(m.boardOrder, 'containsKey', ['requests'])) &amp;&amp; $h.t($h.access.agents())))">
            <section class="card">
                <div class="card-head"><h2>口令申请</h2><span class="count-pill" v-if="$h.t($h.gt(m.pendingRequests, 0))">{{ $h.s(m.pendingRequests) }}</span><a class="tail link" :href.attr="$h.ak('/admin/agents#requests')">去审核<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="board-empty ok" v-if="$h.t($h.lists.isEmpty(m.boardRequests))"><svg class="i"><use href="#i-tick"></use></svg><b>没有等审核的申请</b><span>代理在代理后台申请口令后会排在这里</span></div>
                <ul class="mini-list" v-if="!$h.t($h.lists.isEmpty(m.boardRequests))"><template v-for="[L_q, L_qStat] in $h.each(m.boardRequests)"><li>
                    <span class="who-ava">{{ $h.s(($h.t($h.strings.isEmpty($h.p(L_q, 'agent'))) ? '?' : $h.strings.substring($h.p(L_q, 'agent'), 0, 1))) }}</span>
                    <span class="ml-main"><b>{{ $h.s($h.p(L_q, 'agent')) }}</b><small>{{ $h.s($h.cat($h.p(L_q, 'count'), ($h.t($h.p(L_q, 'custom')) ? ' 个自定义口令' : ' 个随机口令'), ($h.t($h.strings.isEmpty($h.p(L_q, 'note'))) ? '' : $h.cat(' · ', $h.p(L_q, 'note'))))) }}</small></span>
                    <span class="ml-side muted" :title.attr="$h.av($h.ui.full($h.p(L_q, 'at')))">{{ $h.s($h.ui.ago($h.p(L_q, 'at'))) }}</span>
                </li></template></ul>
            </section>
        </div>
        <div class="bw" data-w="notices" data-name="代理公告" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['notices']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['notices']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['notices'])))" v-if="$h.t(($h.t($h.c(m.boardOrder, 'containsKey', ['notices'])) &amp;&amp; $h.t($h.access.agents())))">
            <section class="card">
                <div class="card-head"><h2>代理公告</h2><a class="tail link" :href.attr="$h.ak('/admin/agents#notices')">全部公告<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="board-empty" v-if="$h.t($h.lists.isEmpty(m.boardNotices))"><svg class="i"><use href="#i-megaphone"></use></svg><b>还没发过公告</b><span>活动、规则调整、维护时间都可以发给代理</span><a class="btn btn-secondary btn-sm" :href.attr="$h.ak('/admin/agents#notices')">发公告</a></div>
                <ul class="mini-list" v-if="!$h.t($h.lists.isEmpty(m.boardNotices))"><template v-for="[L_v, L_vStat] in $h.each(m.boardNotices)"><template v-for="L_n in [$h.p(L_v, 'notice')]"><li>
                    <span class="ml-main"><b><span class="mini-tag warn" v-if="$h.t($h.p(L_n, 'important'))">重要</span>{{ $h.s($h.p(L_n, 'title')) }}</b><small>{{ $h.s($h.cat($h.ui.ago($h.p(L_n, 'createdAt')), ($h.t($h.p(L_n, 'pinned')) ? ' · 置顶' : ''))) }}</small></span>
                    <span class="nt-read" :title.attr="$h.av($h.cat($h.p(L_v, 'read'), ' / ', $h.p(L_v, 'audience'), ' 个启用的代理看过'))"><span class="read-bar"><i :style="$h.av($h.cat('width:', $h.ui.pctOf($h.p(L_v, 'read'), $h.p(L_v, 'audience')), '%'))"></i></span><small>{{ $h.s($h.cat($h.p(L_v, 'read'), '/', $h.p(L_v, 'audience'))) }}</small></span>
                </li></template></template></ul>
            </section>
        </div>
        <div class="bw" data-w="rollout" data-name="灰度进度" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['rollout']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['rollout']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['rollout'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['rollout']))">
            <section class="card">
                <div class="card-head"><h2>灰度进度</h2><a class="tail link" :href.attr="$h.ak('/admin/releases')">版本发布<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="board-empty ok" v-if="$h.t($h.lists.isEmpty(m.boardRollouts))"><svg class="i"><use href="#i-tick"></use></svg><b>没有在灰度中的版本</b><span>发布时选一部分设备先更新，这里看推到了多少、装上了多少</span></div>
                <ul class="ro-list" v-if="!$h.t($h.lists.isEmpty(m.boardRollouts))"><template v-for="[L_o, L_oStat] in $h.each(m.boardRollouts)"><li>
                    <div class="ro-top"><span class="rt-tag">{{ $h.s($h.cat($h.p(L_o, 'channel'), ' · ', $h.p(L_o, 'runtime'))) }}</span><b class="mono">{{ $h.s($h.ui.rid($h.p(L_o, 'releaseId'))) }}</b><span class="ro-msg" v-if="!$h.t($h.strings.isEmpty($h.p(L_o, 'message')))">{{ $h.s($h.p(L_o, 'message')) }}</span><b class="ro-pct">{{ $h.s($h.add($h.p(L_o, 'rollout'), '%')) }}</b></div>
                    <div class="ro-track" :title.attr="$h.av($h.cat($h.p(L_o, 'rollout'), '% 的设备会拿到这个版本'))"><i :style="$h.av($h.cat('width:', $h.p(L_o, 'rollout'), '%'))"></i><em :style="$h.av($h.cat('width:', $h.ui.pctOf($h.mul($h.p(L_o, 'installed'), $h.p(L_o, 'rollout')), $h.mul($h.p(L_o, 'offered'), 100)), '%'))"></em></div>
                    <div class="ro-meta">{{ $h.s($h.cat(($h.t($h.gt($h.p(L_o, 'offered'), 0)) ? $h.cat('推送 ', $h.p(L_o, 'offered'), ' 台，装上 ', $h.p(L_o, 'installed'), ' 台') : '还没有设备拿到'), ($h.t(!$h.eq($h.p(L_o, 'previousId'), null)) ? $h.cat(' · 其余设备用 ', $h.ui.rid($h.p(L_o, 'previousId'))) : ''))) }}</div>
                </li></template></ul>
            </section>
        </div>
        <div class="bw" data-w="plans" data-name="计划任务" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['plans']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['plans']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['plans'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['plans']))">
            <section class="card plans-card">
                <div class="card-head">
                    <h2>计划任务</h2>
                    <span class="count-pill" v-if="!$h.t($h.lists.isEmpty(m.boardPlans))">{{ $h.s($h.lists.size(m.boardPlans)) }}</span>
                    <button class="tail link" type="button" data-open="mode-plan-dialog" v-if="$h.t($h.access.can('mode'))"><svg class="i i-sm"><use href="#i-calendar"></use></svg>定时切换</button>
                    <a class="tail link" :href.attr="$h.ak('/admin/releases')" v-if="!$h.t($h.access.can('mode'))">版本发布<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
                </div>
                <div class="board-empty" v-if="$h.t(($h.t($h.lists.isEmpty(m.boardPlans)) &amp;&amp; $h.t($h.lists.isEmpty(m.boardPlansDone))))">
                    <svg class="i"><use href="#i-calendar"></use></svg><b>没有等着执行的计划</b><span>约好时间自动切 A/B 面、调灰度，到点执行，照常记日志、发通知</span>
                    <span class="be-acts" v-if="$h.t(($h.t($h.access.can('mode')) || $h.t($h.access.can('release'))))"><button class="btn btn-secondary btn-sm" type="button" data-open="mode-plan-dialog" v-if="$h.t($h.access.can('mode'))">定时切换</button><a class="btn btn-secondary btn-sm" :href.attr="$h.ak('/admin/releases')" v-if="$h.t($h.access.can('release'))">定时调灰度</a></span>
                </div>
                <div class="ml-none" v-if="$h.t(($h.t($h.lists.isEmpty(m.boardPlans)) &amp;&amp; $h.t(!$h.t($h.lists.isEmpty(m.boardPlansDone)))))"><svg class="i i-sm"><use href="#i-calendar"></use></svg>没有等着执行的计划</div>
                <ul class="mini-list" v-if="!$h.t($h.lists.isEmpty(m.boardPlans))"><template v-for="[L_p, L_pStat] in $h.each(m.boardPlans)"><li>
                    <span :class="['ml-ic', $h.av($h.strings.toLowerCase($h.p(L_p, 'kind')))]"><svg class="i"><use :href.attr="$h.av(($h.t($h.eq($h.p(L_p, 'kind'), 'MODE')) ? '#i-zap' : '#i-sliders'))"></use></svg></span>
                    <span class="ml-main"><b>{{ $h.s($h.p(L_p, 'title')) }}</b><small :title.attr="$h.av($h.ui.full($h.p(L_p, 'runAt')))">{{ $h.s($h.cat($h.ui.dayLabel($h.p(L_p, 'runAt')), ' ', $h.ui.hm($h.p(L_p, 'runAt')), ' · ', $h.p(L_p, 'sub'))) }}</small></span>
                    <span class="ml-side ml-acts"><span :class="['when-chip', $h.av(($h.t(L_pStat.first) ? 'next' : null))]">{{ $h.s($h.ui.until($h.p(L_p, 'runAt'))) }}</span><form method="post" :action.attr="$h.ak($h.link('/admin/schedules/{id}/cancel', [['id', $h.p(L_p, 'id')]]))" data-confirm="取消这个计划？" data-confirm-body="取消后不会执行，操作日志里留着记录。" data-confirm-ok="取消计划" v-if="$h.t($h.p(L_p, 'canCancel'))"><button class="link-btn" type="submit">取消</button></form></span>
                </li></template></ul>
                <div class="ml-h" v-if="!$h.t($h.lists.isEmpty(m.boardPlansDone))">最近执行</div>
                <ul class="mini-list" v-if="!$h.t($h.lists.isEmpty(m.boardPlansDone))"><template v-for="[L_d, L_dStat] in $h.each(m.boardPlansDone)"><li>
                    <span :class="['lg-dot', $h.av(($h.t($h.eq($h.p(L_d, 'status'), 'DONE')) ? 'ok' : 'bad'))]"></span>
                    <span class="ml-main"><b>{{ $h.s($h.p(L_d, 'title')) }}</b><small :title.attr="$h.av($h.p(L_d, 'result'))">{{ $h.s(($h.t($h.eq($h.p(L_d, 'status'), 'DONE')) ? $h.elvis($h.p(L_d, 'result'), '已执行') : $h.cat('没执行 · ', $h.elvis($h.p(L_d, 'result'), '—')))) }}</small></span>
                    <span class="ml-side muted" :title.attr="$h.av($h.ui.full($h.p(L_d, 'doneAt')))">{{ $h.s($h.ui.ago($h.p(L_d, 'doneAt'))) }}</span>
                </li></template></ul>
            </section>
        </div>
        <div class="bw" data-w="deploys" data-name="发布记录" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['deploys']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['deploys']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['deploys'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['deploys']))">
            <section class="card">
                <div class="card-head"><h2>发布记录</h2><a class="tail link" :href.attr="$h.ak('/admin/releases#history')">全部记录<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="board-empty" v-if="$h.t($h.lists.isEmpty(m.boardDeploys))"><svg class="i"><use href="#i-history"></use></svg><b>还没有发布记录</b><span>发布、调灰度、回滚都会按时间记在这里</span></div>
                <ul class="dp-tl" v-if="!$h.t($h.lists.isEmpty(m.boardDeploys))"><template v-for="[L_e, L_eStat] in $h.each(m.boardDeploys)"><li>
                    <span :class="['dp-dot', $h.av($h.strings.toLowerCase($h.p(L_e, 'action')))]"></span>
                    <div class="dp-main">
                        <div class="dp-line"><span :class="['ev-tag', $h.av($h.strings.toLowerCase($h.p(L_e, 'action')))]">{{ $h.s($h.p(L_e, 'actionLabel')) }}</span><b class="mono" v-if="$h.t(!$h.eq($h.p(L_e, 'releaseId'), null))">{{ $h.s($h.ui.rid($h.p(L_e, 'originId'))) }}</b><b v-if="$h.t($h.eq($h.p(L_e, 'releaseId'), null))">{{ $h.s(($h.t($h.eq($h.p(L_e, 'action'), 'ACTIVATION_OFF')) ? '改回常规更新' : '内置版本')) }}</b><span class="muted" v-if="$h.t(($h.t(!$h.eq($h.p(L_e, 'releaseId'), null)) &amp;&amp; $h.t($h.lt($h.p(L_e, 'rollout'), 100))))">{{ $h.s($h.cat($h.p(L_e, 'rollout'), '%')) }}</span></div>
                        <small>{{ $h.s($h.cat($h.p(L_e, 'channel'), ' · ', $h.elvis($h.c(m.operatorNames, 'get', [$h.p(L_e, 'actor')]), $h.elvis($h.p(L_e, 'actor'), '系统')))) }}</small>
                    </div>
                    <span class="dp-at" :title.attr="$h.av($h.ui.full($h.p(L_e, 'at')))">{{ $h.s($h.ui.ago($h.p(L_e, 'at'))) }}</span>
                </li></template></ul>
            </section>
        </div>
        <div class="bw" data-w="errors" data-name="最近启动错误" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['errors']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['errors']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['errors'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['errors']))">
    <section class="card">
        <div class="card-head">
            <h2>最近启动错误</h2>
            <a class="tail link" :href.attr="$h.ak('/admin/devices')">设备统计<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
        </div>
        <div class="board-empty ok" v-if="$h.t($h.lists.isEmpty(m.boardErrors))"><svg class="i"><use href="#i-tick"></use></svg><b>最近 30 天没有启动错误</b><span>新版本在设备上启动失败、被退回时会列在这里</span></div>
        <ul class="err-list" v-if="!$h.t($h.lists.isEmpty(m.boardErrors))"><template v-for="[L_e, L_eStat] in $h.each(m.boardErrors)"><li>
            <div class="err-msg" :title.attr="$h.av($h.p(L_e, 'message'))">{{ $h.s($h.p(L_e, 'message')) }}</div>
            <div class="err-meta"><span>{{ $h.s(($h.t($h.eq($h.p(L_e, 'platform'), 'ios')) ? 'iOS' : ($h.t($h.eq($h.p(L_e, 'platform'), 'android')) ? 'Android' : $h.elvis($h.p(L_e, 'platform'), '—')))) }}</span><span>{{ $h.s(($h.t($h.eq($h.p(L_e, 'releaseId'), null)) ? '—' : ($h.t($h.eq($h.p(L_e, 'releaseId'), 'embedded')) ? '内置版本' : $h.ui.rid($h.p(L_e, 'releaseId'))))) }}</span><span :title.attr="$h.av($h.ui.full($h.p(L_e, 'createdAt')))">{{ $h.s($h.ui.ago($h.p(L_e, 'createdAt'))) }}</span></div>
        </li></template></ul>
    </section>
        </div>
        <div class="bw" data-w="installers" data-name="安装包" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['installers']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['installers']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['installers'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['installers']))">
            <section class="card">
                <div class="card-head"><h2>安装包</h2><a class="tail link" :href.attr="$h.ak('/admin/releases#installers')">全部安装包<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="board-empty" v-if="$h.t($h.lists.isEmpty(m.boardInstallers))"><svg class="i"><use href="#i-download"></use></svg><b>还没有上传安装包</b><span>传上 ipa、apk 后，这里列出最新的几个</span></div>
                <ul class="mini-list" v-if="!$h.t($h.lists.isEmpty(m.boardInstallers))"><template v-for="[L_i, L_iStat] in $h.each(m.boardInstallers)"><li>
                    <span :class="['pf-ic', $h.av($h.p(L_i, 'platform'))]"><svg class="i"><use :href.attr="$h.av(($h.t($h.eq($h.p(L_i, 'platform'), 'ios')) ? '#i-apple' : '#i-android'))"></use></svg></span>
                    <span class="ml-main"><b>{{ $h.s($h.cat($h.elvis($h.p(L_i, 'versionName'), '—'), ($h.t($h.strings.isEmpty($h.p(L_i, 'buildNumber'))) ? '' : $h.cat('（', $h.p(L_i, 'buildNumber'), '）')))) }}</b><small :title.attr="$h.av($h.p(L_i, 'fileName'))">{{ $h.s($h.cat($h.ui.size($h.p(L_i, 'size')), ' · ', $h.p(L_i, 'fileName'))) }}</small></span>
                    <span class="ml-side muted" :title.attr="$h.av($h.ui.full($h.p(L_i, 'uploadedAt')))">{{ $h.s($h.ui.ago($h.p(L_i, 'uploadedAt'))) }}</span>
                </li></template></ul>
            </section>
        </div>
        <div class="bw" data-w="mix" data-name="设备构成" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['mix']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['mix']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['mix'])))" v-if="$h.t($h.c(m.boardOrder, 'containsKey', ['mix']))">
    <section class="card dev-mix">
        <div class="card-head">
            <h2>设备构成</h2>
            <span class="muted small">近 30 天检查过更新的设备</span>
            <a class="tail link" :href.attr="$h.ak('/admin/devices')">设备统计<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
        </div>
        <template v-for="L_pt in [$h.add(m.mixIos, m.mixAndroid)]"><div class="mix-body">
            <div class="mix-block">
                <div class="mix-k">平台</div>
                <div class="mix-bar" v-if="$h.t($h.gt(L_pt, 0))">
                    <span class="ios" :style="$h.av($h.cat('width:', $h.ui.pctOf(m.mixIos, L_pt), '%'))" v-if="$h.t($h.gt(m.mixIos, 0))"></span>
                    <span class="and" :style="$h.av($h.cat('width:', $h.ui.pctOf(m.mixAndroid, L_pt), '%'))" v-if="$h.t($h.gt(m.mixAndroid, 0))"></span>
                </div>
                <div class="mix-bar" v-if="$h.t($h.eq(L_pt, 0))"></div>
                <div class="mix-legend">
                    <span><i class="ios"></i>iOS<b>{{ $h.s($h.numbers.formatInteger(m.mixIos, 1, 'COMMA')) }}</b><small>{{ $h.s($h.add($h.ui.pctOf(m.mixIos, L_pt), '%')) }}</small></span>
                    <span><i class="and"></i>Android<b>{{ $h.s($h.numbers.formatInteger(m.mixAndroid, 1, 'COMMA')) }}</b><small>{{ $h.s($h.add($h.ui.pctOf(m.mixAndroid, L_pt), '%')) }}</small></span>
                </div>
            </div>
            <div class="mix-block">
                <div class="mix-k">渠道</div>
                <ul class="mix-list">
                    <template v-for="[L_s, L_sStat] in $h.each(m.mixChannels)"><li><span class="mono" :title.attr="$h.av($h.p(L_s, 'label'))">{{ $h.s($h.p(L_s, 'label')) }}</span><span class="mix-track"><i :style="$h.av($h.cat('width:', $h.p(L_s, 'pct'), '%'))"></i></span><b>{{ $h.s($h.numbers.formatInteger($h.p(L_s, 'count'), 1, 'COMMA')) }}</b><small>{{ $h.s($h.add($h.p(L_s, 'pct'), '%')) }}</small></li></template>
                    <li class="muted" v-if="$h.t($h.lists.isEmpty(m.mixChannels))">还没有设备</li>
                </ul>
            </div>
            <div class="mix-block">
                <div class="mix-k">Runtime</div>
                <ul class="mix-list">
                    <template v-for="[L_s, L_sStat] in $h.each(m.mixRuntimes)"><li><span class="mono" :title.attr="$h.av($h.p(L_s, 'label'))">{{ $h.s($h.p(L_s, 'label')) }}</span><span class="mix-track"><i :style="$h.av($h.cat('width:', $h.p(L_s, 'pct'), '%'))"></i></span><b>{{ $h.s($h.numbers.formatInteger($h.p(L_s, 'count'), 1, 'COMMA')) }}</b><small>{{ $h.s($h.add($h.p(L_s, 'pct'), '%')) }}</small></li></template>
                    <li class="muted" v-if="$h.t($h.lists.isEmpty(m.mixRuntimes))">还没有设备</li>
                </ul>
            </div>
        </div></template>
    </section>
        </div>
        <div class="bw" data-w="system" data-name="运行状态" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['system']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['system']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['system'])))" v-if="$h.t(($h.t($h.c(m.boardOrder, 'containsKey', ['system'])) &amp;&amp; $h.t($h.access.owner())))">
            <template v-for="L_y in [m.boardSys]"><section class="card">
                <div class="card-head"><h2>运行状态</h2><a class="tail link" :href.attr="$h.ak('/admin/settings#about')">系统配置<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <dl class="sys-kv">
                    <div><dt>版本</dt><dd class="mono">{{ $h.s($h.p(L_y, 'version')) }}</dd></div>
                    <div><dt>已运行</dt><dd>{{ $h.s($h.p(L_y, 'uptime')) }}</dd></div>
                    <div class="sys-meter"><dt>内存</dt><dd><span :class="['meter', $h.av(($h.t($h.gt($h.p(L_y, 'memPct'), 85)) ? 'warn' : null))]"><i :style="$h.av($h.cat('width:', $h.p(L_y, 'memPct'), '%'))"></i></span><small>{{ $h.s($h.p(L_y, 'mem')) }}</small></dd></div>
                    <div class="sys-meter"><dt>磁盘</dt><dd><span :class="['meter', $h.av(($h.t($h.p(L_y, 'diskLow')) ? 'danger' : null))]"><i :style="$h.av($h.cat('width:', $h.p(L_y, 'diskPct'), '%'))"></i></span><small>{{ $h.s($h.p(L_y, 'disk')) }}</small></dd></div>
                    <div><dt>文件存储</dt><dd :title.attr="$h.av($h.p(L_y, 'storage'))">{{ $h.s($h.p(L_y, 'storage')) }}</dd></div>
                    <div><dt>数据库</dt><dd :title.attr="$h.av($h.p(L_y, 'database'))">{{ $h.s($h.p(L_y, 'database')) }}</dd></div>
                </dl>
            </section></template>
        </div>
        <div class="bw" data-w="logins" data-name="登录动态" :data-size.attr="$h.av($h.c(m.boardSize, 'get', ['logins']))" :data-sizes.attr="$h.av($h.c(m.boardSizes, 'get', ['logins']))" :style="$h.av($h.cat('order:', $h.c(m.boardOrder, 'get', ['logins'])))" v-if="$h.t(($h.t($h.c(m.boardOrder, 'containsKey', ['logins'])) &amp;&amp; $h.t($h.access.owner())))">
            <section class="card">
                <div class="card-head"><h2>登录动态</h2><a class="tail link" :href.attr="$h.ak('/admin/admins')">管理账号<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="board-empty" v-if="$h.t($h.lists.isEmpty(m.boardLogins))"><svg class="i"><use href="#i-shield"></use></svg><b>还没有登录记录</b></div>
                <ul class="mini-list" v-if="!$h.t($h.lists.isEmpty(m.boardLogins))"><template v-for="[L_l, L_lStat] in $h.each(m.boardLogins)"><li>
                    <span :class="['lg-dot', $h.av(($h.t($h.p(L_l, 'success')) ? 'ok' : 'bad'))]"></span>
                    <span class="ml-main"><b>{{ $h.s($h.p(L_l, 'name')) }}</b><small :title.attr="$h.av($h.p(L_l, 'device'))">{{ $h.s(($h.t($h.p(L_l, 'success')) ? $h.elvis($h.p(L_l, 'device'), '未知设备') : $h.cat('没登上 · ', $h.elvis($h.p(L_l, 'reason'), '密码不对')))) }}</small></span>
                    <span class="ml-side muted" :title.attr="$h.av($h.ui.full($h.p(L_l, 'at')))">{{ $h.s($h.ui.ago($h.p(L_l, 'at'))) }}</span>
                </li></template></ul>
            </section>
        </div>
        </div>

        <dialog id="board-dialog" class="wide board-dialog">
            <div class="dlg-head">
                <h3>添加卡片</h3>
                <p>{{ $h.s($h.cat('一共 ', m.boardCount, ' 张，打开的显示在概览上，新加的排在最后。只对你自己生效')) }}</p>
            </div>
            <div class="dlg-body">
                <template v-for="[L_g, L_gStat] in $h.each(m.boardGroups)"><div class="bp-group">
                    <div class="bp-group-h">{{ $h.s($h.p(L_g, 'name')) }}</div>
                    <ul class="board-pick"><template v-for="[L_w, L_wStat] in $h.each($h.p(L_g, 'cards'))"><li>
                        <label class="bp-row">
                            <span class="bp-ico"><svg class="i"><use :href.attr="$h.av($h.cat('#', $h.p(L_w, 'icon')))"></use></svg></span>
                            <span class="bp-text"><b>{{ $h.s($h.p(L_w, 'name')) }}</b><small>{{ $h.s($h.p(L_w, 'desc')) }}</small></span>
                            <span class="switch"><input type="checkbox" data-board-pick :value.attr="$h.av($h.p(L_w, 'id'))" :checked.attr="$h.t($h.p(L_w, 'on')) ? 'checked' : null"><span class="sw-track"><span class="sw-thumb"></span></span></span>
                        </label>
                    </li></template></ul>
                </div></template>
            </div>
            <div class="dlg-foot">
                <span class="hint" data-board-picked></span>
                <button class="btn btn-secondary" type="button" data-close>取消</button>
                <button class="btn" type="button" data-board-apply>保存</button>
            </div>
        </dialog>

        <dialog id="mode-plan-dialog" v-if="$h.t($h.access.can('mode'))">
            <form method="post" :action.attr="$h.ak('/admin/schedules/mode')">
                <div class="dlg-head">
                    <h3>定时切换 A/B 面</h3>
                    <p>到时间自动切换，切换后照常记日志、发通知。比如审核通过后约好时间上线</p>
                </div>
                <div class="dlg-body">
                    <div class="field">
                        <label>切换到</label>
                        <div class="seg seg-fill">
                            <label class="seg-opt"><input type="radio" name="face" value="B" :checked.attr="$h.t(!$h.eq(m.face, 'B')) ? 'checked' : null"><span>B 面 · 上线模式</span></label>
                            <label class="seg-opt"><input type="radio" name="face" value="A" :checked.attr="$h.t($h.eq(m.face, 'B')) ? 'checked' : null"><span>A 面 · 审核模式</span></label>
                        </div>
                    </div>
                    <div class="field">
                        <label for="mode-plan-at">执行时间</label>
                        <input class="input" id="mode-plan-at" name="runAt" type="datetime-local" required data-future>
                        <span class="hint">按后台时区，最多提前 90 天</span>
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">设好</button>
                </div>
            </form>
        </dialog>

    </main>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('当前概览'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
    css: ["app/ui.css"],
    js: ["app/ui.js"],
    theme: { attr: true, css: (m, c, $h) => m.themeCss }
};
</script>

<script setup>
import FAdminLayoutOverlays from '../../frags/admin-layout--overlays.vue';
import FAdminLayoutRecordStatus from '../../frags/admin-layout--recordStatus.vue';
import FAdminLayoutSidebar from '../../frags/admin-layout--sidebar.vue';
import FAdminLayoutTopbar from '../../frags/admin-layout--topbar.vue';
defineProps({ m: Object, c: Object });
</script>
