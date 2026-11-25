<template>
<div :class="['scr', isAll ? 'is-all' : 'is-app', { on, intro, light, shared, 'no-rank': !m.scAgentsOn, 'no-hot': !m.scCodesOn }]">
    <div class="sc-glow" aria-hidden="true"><i></i><i></i></div>

    <header class="sc-top">
        <Clock :skew="skew"/>
        <div class="sc-brand">
            <a class="sc-up" href="/admin/screen?scope=all" title="全部 App" aria-label="全部 App" v-if="many && !isAll"><svg class="i"><use href="#i-layers"></use></svg></a>
            <button class="sc-title" type="button" :disabled="!many" :title="many ? '切换 App（/）' : null" aria-haspopup="dialog" @click="openPick">
                <Ava :all="isAll" :id="m.scAppId" :icon="m.scIcon" :name="m.scName"/>
                <span class="sc-title-tx"><b v-if="isAll">全部 App</b><b v-if="!isAll" translate="no">{{ $h.s(m.scName) }}</b><small>数据大屏</small></span>
                <svg class="i sc-chev" v-if="many"><use href="#i-updown"></use></svg>
            </button>
        </div>
        <div class="sc-tools">
            <span :class="['sc-chip', 'sc-face', m.scFace === 'B' ? 'ok' : 'warn']" v-if="!isAll"><i></i><span>{{ m.scFace === 'B' ? 'B 面 · 上线中' : 'A 面 · 审核中' }}</span></span>
            <span :class="['sc-chip', 'sc-live', 'live-tag', { bad: lost && !live.login, out: live.login }]" data-live="10" title="开着时每 10 秒自动取一次数据">
                <svg class="sc-count" viewBox="0 0 20 20" aria-hidden="true"><circle class="bg" cx="10" cy="10" r="7"></circle><circle class="fg" ref="countFg" cx="10" cy="10" r="7" transform="rotate(-90 10 10)"></circle></svg>
                <span v-if="!lost && !live.login">实时</span><span v-if="lost && !live.login">连接中断</span><span v-if="live.login">登录已失效</span>
            </span>
            <a class="sc-chip sc-relogin" :href="here" data-no-swap v-if="live.login && !shared"><span>重新登录</span></a>
            <button class="sc-btn sc-share-btn" type="button" title="分享" aria-label="分享" aria-haspopup="dialog" @click="openShare" v-if="!shared && !m.demoMode"><svg class="i"><use href="#i-share"></use></svg></button>
            <button class="sc-btn sc-share-btn" type="button" title="把这个链接发给别人" aria-label="分享这个链接" @click="shareLink" v-if="shared"><svg class="i"><use href="#i-share"></use></svg></button>
            <button class="sc-btn sc-theme" type="button" :title="light ? '深色主题' : '浅色主题'" :aria-label="light ? '深色主题' : '浅色主题'" @click="toggleTheme"><svg class="i"><use :href="light ? '#i-moon' : '#i-sun'"></use></svg></button>
            <button class="sc-btn sc-fs" type="button" :title="full ? '退出全屏' : '全屏（F）'" :aria-label="full ? '退出全屏' : '全屏'" @click="toggleFull"><svg class="i"><use :href="full ? '#i-shrink' : '#i-expand'"></use></svg></button>
            <a class="sc-btn" :href="$h.ak(m.scBack)" title="返回后台" aria-label="返回后台" v-if="!shared"><svg class="i"><use href="#i-x"></use></svg></a>
        </div>
    </header>

    <section class="sc-kpis">
        <div v-for="(k, i) in kpis" :key="k.key" :class="['sc-kpi', k.tone]" :style="{ '--i': i }" v-flash="k.value">
            <span class="sc-kpi-l"><span>{{ k.label }}</span><svg class="i"><use :href="'#' + k.icon"></use></svg></span>
            <b :class="['sc-kpi-v', { long: k.long }]"><Num :value="k.value" :on="on"/><i v-if="k.of !== null">/ {{ num(k.of) }}</i><Bump :value="k.value"/></b>
            <small :class="k.subTone"><em v-if="k.delta" :class="['sc-d', k.delta.dir]">{{ k.delta.text }}</em><span v-if="k.note">{{ k.note }}</span><b v-if="k.extra !== null">{{ k.extra }}</b></small>
        </div>
    </section>

    <main class="sc-main">
        <section class="sc-pane sc-hero">
            <div class="sc-hero-in">
                <div :class="['sc-ring', { ahead: fx.ahead, beat: fx.beat, surge, bloom: blooming }]" :style="{ '--heat': heat }" :title="fx.tip">
                    <svg viewBox="0 0 240 240" aria-hidden="true">
                        <defs>
                            <linearGradient id="sc-arc-grad" x1="0" y1="0" x2="1" y2="1">
                                <stop offset="0" class="sc-stop-hi"></stop>
                                <stop offset="1" class="sc-stop"></stop>
                            </linearGradient>
                            <linearGradient id="sc-tail-grad" gradientUnits="userSpaceOnUse" x1="120" y1="26" :x2="tail.x" :y2="tail.y">
                                <stop offset="0" class="sc-tail-hi"></stop>
                                <stop offset=".4" class="sc-tail-mid"></stop>
                                <stop offset="1" class="sc-tail-lo"></stop>
                            </linearGradient>
                            <filter id="sc-soft" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="3"></feGaussianBlur></filter>
                        </defs>
                        <g class="sc-orbit"><circle cx="120" cy="120" r="117"></circle></g>
                        <g class="sc-ticks"><line v-for="(t, i) in TICKS" :key="i" :x1="t.x1" :y1="t.y1" :x2="t.x2" :y2="t.y2" :class="{ lit: i < ring.lit }" :style="{ transitionDelay: (i * 12) + 'ms' }"></line></g>
                        <circle class="sc-arc-bg" cx="120" cy="120" r="94"></circle>
                        <circle class="sc-aura" cx="120" cy="120" r="94" v-if="ring.base > 0"></circle>
                        <circle class="sc-arc" cx="120" cy="120" r="94" :stroke-dasharray="ring.arc" transform="rotate(-90 120 120)"></circle>
                        <circle class="sc-lap done" cx="120" cy="120" r="94" v-if="ring.laps > 2"></circle>
                        <circle class="sc-lap" :key="'lap' + ring.laps" cx="120" cy="120" r="94" :stroke-dasharray="ring.lap" transform="rotate(-90 120 120)" v-if="ring.laps > 1"></circle>
                        <g class="sc-pace" :style="{ transform: 'rotate(' + pace + 'deg)' }" v-if="pace !== null"><line x1="120" y1="14.5" x2="120" y2="20.5"></line></g>
                        <g class="sc-knob" :style="knob" v-if="ring.base > 0">
                            <path class="sc-comet" :d="tail.d" :style="{ d: pathOf(tail.d) }" filter="url(#sc-soft)" v-if="tail.d"></path>
                        </g>
                    </svg>
                    <svg class="sc-fx" viewBox="0 0 240 240" aria-hidden="true" v-if="ring.base > 0">
                        <defs>
                            <linearGradient id="sc-grain-grad" gradientUnits="userSpaceOnUse" x1="120" y1="26" :x2="tail.x" :y2="tail.y">
                                <stop offset="0" class="sc-grain-hi"></stop>
                                <stop offset=".55" class="sc-grain-mid"></stop>
                                <stop offset="1" class="sc-grain-lo"></stop>
                            </linearGradient>
                            <linearGradient id="sc-grain-grad-a" gradientUnits="userSpaceOnUse" x1="120" y1="26" :x2="tail.x" :y2="tail.y">
                                <stop offset="0" class="sc-grain-a-hi"></stop>
                                <stop offset=".55" class="sc-grain-a-mid"></stop>
                                <stop offset="1" class="sc-grain-a-lo"></stop>
                            </linearGradient>
                            <linearGradient id="sc-spray-grad" gradientUnits="userSpaceOnUse" x1="120" y1="26" x2="154" y2="32">
                                <stop offset="0" class="sc-grain-hi"></stop>
                                <stop offset=".5" class="sc-grain-mid"></stop>
                                <stop offset="1" class="sc-grain-lo"></stop>
                            </linearGradient>
                            <linearGradient id="sc-spray-grad-a" gradientUnits="userSpaceOnUse" x1="120" y1="26" x2="154" y2="32">
                                <stop offset="0" class="sc-grain-a-hi"></stop>
                                <stop offset=".5" class="sc-grain-a-mid"></stop>
                                <stop offset="1" class="sc-grain-a-lo"></stop>
                            </linearGradient>
                        </defs>
                        <g class="sc-knob" :style="knob">
                            <g class="sc-grains" v-if="tail.d"><path v-for="(l, i) in grains" :key="i" :class="['sc-grain', 'k' + i]" :d="l.d" :style="{ d: pathOf(l.d), opacity: l.op, animationDuration: l.dur }" :stroke-width="l.w" :stroke-dasharray="'0.01 ' + l.gap"></path></g>
                            <g class="sc-spray" v-if="fx.ahead"><path v-for="(l, i) in spray" :key="i" :class="'k' + i" :d="l.d" :style="{ opacity: l.op, animationDuration: l.dur }" :stroke-width="l.w" :stroke-dasharray="'0.01 ' + l.gap"></path></g>
                            <g class="sc-streak" :key="'s' + hits" v-if="surge && streak"><path class="glow" :d="streak" pathLength="100" filter="url(#sc-soft)"></path><path class="core" :d="streak" pathLength="100"></path></g>
                        </g>
                    </svg>
                    <svg class="sc-fx" viewBox="0 0 240 240" aria-hidden="true" v-if="ring.base > 0">
                        <g class="sc-knob" :style="knob">
                            <circle class="sc-dot-glow" cx="120" cy="26" r="8" filter="url(#sc-soft)"></circle>
                            <circle class="sc-dot-core" cx="120" cy="26" r="4.2"></circle>
                        </g>
                    </svg>
                    <div class="sc-ring-in">
                        <span>今日有效激活</span>
                        <b><Num :value="today" :on="on"/><Bump :value="today"/></b>
                        <small v-if="ring.base > 0" :class="{ win: fx.beat }"><svg class="i" v-if="fx.beat"><use href="#i-tick"></use></svg><span>{{ ring.yday ? '已达昨天' : '已达 7 日均' }}</span> {{ ring.pct }}%</small>
                        <small v-if="!(ring.base > 0)"><span>昨天没有激活</span></small>
                        <em class="sc-times" v-if="fx.double">×{{ fx.times }}</em>
                    </div>
                </div>
                <div class="sc-stats">
                    <div class="sc-stat">
                        <span>比昨天同时段</span>
                        <b :class="['sc-d', vsToday ? vsToday.dir : 'flat']">{{ vsToday ? vsToday.text : '—' }}</b>
                        <small><span>昨天此刻</span> {{ num(m.scSameTime) }}</small>
                    </div>
                    <div class="sc-stat">
                        <span>今日提交激活</span>
                        <b><Num :value="+m.scSubmitted || 0" :on="on"/></b>
                        <small><span>待确认</span> {{ num(m.scPending) }}</small>
                    </div>
                    <div class="sc-stat">
                        <span>近 7 天确认率</span>
                        <b>{{ rate7 === null ? '—' : rate7 }}<i v-if="rate7 !== null">%</i></b>
                        <small><span>有效</span> {{ num(m.scWeek) }} · <span>提交</span> {{ num(m.scSub7) }}</small>
                    </div>
                    <div class="sc-stat">
                        <span>昨天全天</span>
                        <b><Num :value="+m.scYesterday || 0" :on="on"/></b>
                        <small><span>近 7 天日均</span> {{ num(ring.avg) }}</small>
                    </div>
                </div>
                <div class="sc-today">
                    <div class="sc-head">
                        <h2>今日分时</h2>
                        <div class="sc-legend top"><span class="cur"><i></i>今天</span><span class="prev"><i></i>昨天</span></div>
                        <span class="sc-meta" v-if="hours.peak !== null"><span>高峰</span> {{ hours.peak }}:00 · {{ num(hours.peakV) }}</span>
                    </div>
                    <div class="sc-body sc-chartbox">
                        <div class="sc-hours">
                            <div class="sc-hbars"><i v-for="x in hours.list" :key="x.h" :class="{ now: x.now, later: x.later }" :style="{ height: x.pct + '%', transitionDelay: (x.h * 14) + 'ms' }"><span class="sc-tip"><b>{{ x.h }}:00</b>{{ num(x.v) }}<small><span>昨天</span> {{ num(x.y) }}</small></span></i></div>
                            <svg class="sc-yday" viewBox="0 0 240 100" preserveAspectRatio="none" aria-hidden="true"><path :d="hours.yline" :style="{ d: pathOf(hours.yline) }"></path></svg>
                            <div class="sc-empty" v-if="hours.empty">今天还没有有效激活</div>
                        </div>
                        <div class="sc-xs"><span v-for="x in HOUR_MARKS" :key="x" :style="{ left: ((x + 0.5) * 100 / 24) + '%' }">{{ x }}</span></div>
                    </div>
                </div>
            </div>
        </section>

        <section class="sc-pane sc-p-rank" v-if="m.scAgentsOn">
            <div class="sc-head"><h2>代理排行</h2><span class="sc-sub">今日</span><span class="sc-meta"><span>合计</span> <b>{{ num(m.scAgentToday) }}</b></span></div>
            <TransitionGroup tag="div" name="sc-mv" class="sc-body sc-list" v-fit:rank="2.2"><div v-for="(r, i) in rankRows" :key="r.key" :class="['sc-row', { off: r.off }]" v-flash="r.today">
                    <span :class="['no', { top: i < 3 }]">{{ two(i + 1) }}<i v-if="r.move" :class="['mv', r.move]"></i></span>
                    <span class="nm"><b translate="no">{{ $h.s(r.name) }}</b><em class="sc-tag" v-if="r.off">停用</em></span>
                    <small><span>近 7 天</span> {{ num(r.week) }}</small>
                    <span class="sc-meter"><span class="fill"><i :style="{ width: r.w + '%' }"></i></span><b>{{ num(r.today) }}<Bump :value="r.today"/></b></span>
                </div></TransitionGroup>
            <div class="sc-none" v-if="!ranks.length">{{ m.scAgentCount > 0 ? '今天还没有代理激活' : '还没有代理' }}</div>
        </section>

        <section class="sc-pane sc-p-trend">
            <div class="sc-head">
                <h2>激活趋势</h2><span class="sc-sub">近 30 天</span>
                <span class="sc-meta"><span>日均</span> <b>{{ num(trend.avg) }}</b> · <span>最高</span> <b>{{ num(trend.peak) }}</b></span>
            </div>
            <div class="sc-headline">
                <b><Num :value="+m.scMonth || 0" :on="on"/></b>
                <em v-if="vsMonth" :class="['sc-d', vsMonth.dir]">{{ vsMonth.text }}</em>
                <span>比上一周期</span>
                <div class="sc-legend tail"><span class="cur"><i></i>近 30 天</span><span class="prev"><i></i>上一周期</span></div>
            </div>
            <div class="sc-body sc-chartbox">
                <div class="sc-chart">
                    <div class="sc-yaxis"><span v-for="t in trend.ticks" :key="t">{{ num(t) }}</span></div>
                    <div class="sc-plot">
                        <svg viewBox="0 0 1000 300" preserveAspectRatio="none" aria-hidden="true">
                            <defs>
                                <linearGradient id="sc-area-grad" x1="0" y1="0" x2="0" y2="1">
                                    <stop offset="0" class="sc-stop" stop-opacity=".3"></stop>
                                    <stop offset=".7" class="sc-stop" stop-opacity=".04"></stop>
                                    <stop offset="1" class="sc-stop" stop-opacity="0"></stop>
                                </linearGradient>
                                <linearGradient id="sc-line-grad" x1="0" y1="0" x2="1" y2="0">
                                    <stop offset="0" class="sc-stop"></stop>
                                    <stop offset="1" class="sc-stop-hi"></stop>
                                </linearGradient>
                            </defs>
                            <g class="sc-grid"><line x1="0" x2="1000" y1="0.5" y2="0.5"></line><line x1="0" x2="1000" y1="150" y2="150"></line><line x1="0" x2="1000" y1="299.5" y2="299.5"></line></g>
                            <path class="pv" :d="trend.prev" :style="{ d: pathOf(trend.prev) }"></path>
                            <path class="ar" :d="trend.area" :style="{ d: pathOf(trend.area) }"></path>
                            <path class="ln" :d="trend.line" :style="{ d: pathOf(trend.line) }"></path>
                        </svg>
                        <i class="sc-dot" v-if="!trend.empty" :style="{ left: '100%', top: trend.dotY + '%' }"></i>
                        <div class="sc-hit" :style="{ left: -trend.half + '%', right: -trend.half + '%' }"><i v-for="(p, i) in trend.cols" :key="i" :class="{ edge: i > trend.cols.length - 6, start: i < 4 }" :style="{ '--y': p.y + '%' }"><span class="sc-tip"><b>{{ p.label }}</b>{{ num(p.v) }}<small><span>上一周期</span> {{ num(p.p) }}</small></span></i></div>
                        <div class="sc-empty" v-if="trend.empty">近 30 天还没有有效激活</div>
                    </div>
                    <div class="sc-xaxis"><span v-for="x in trend.xs" :key="x.i" :style="{ left: x.at + '%' }">{{ x.label }}</span></div>
                </div>
            </div>
        </section>

        <section class="sc-pane sc-p-dev" v-if="!isAll">
            <div class="sc-head"><h2>活跃设备</h2><span class="sc-sub">近 14 天</span><span class="sc-meta"><span>今日新设备</span> <b>{{ num(m.scDevNew) }}</b></span></div>
            <div class="sc-body sc-chartbox">
                <div class="sc-dbars">
                    <div v-for="(d, i) in dev.days" :key="i" :class="['col', { today: d.today }]">
                        <div class="bar" :style="{ height: d.pct + '%', transitionDelay: (i * 28) + 'ms' }"><i class="ios" :style="{ flexGrow: d.ios }"></i><i class="and" :style="{ flexGrow: d.android }"></i><i class="oth" :style="{ flexGrow: d.other }"></i></div>
                        <em v-if="d.mark">{{ d.label }}</em>
                        <span class="sc-tip"><b>{{ d.label }}</b>{{ num(d.total) }}<small><span>新设备</span> {{ num(d.fresh) }}</small></span>
                    </div>
                    <div class="sc-empty" v-if="dev.empty">近 14 天没有设备检查更新</div>
                </div>
            </div>
            <div class="sc-legend"><span class="ios"><i></i>iOS</span><span class="and"><i></i>Android</span><span class="oth" v-if="dev.hasOther"><i></i>其他</span></div>
        </section>

        <section class="sc-pane sc-p-hot" v-if="m.scCodesOn">
            <div class="sc-head"><h2>热门口令</h2><span class="sc-sub">近 7 天</span><span class="sc-meta"><span>共</span> <b>{{ num(m.scCodeActs7) }}</b> <span>次激活</span></span></div>
            <TransitionGroup tag="div" name="sc-mv" class="sc-body sc-list" v-fit:hot="2.2"><div v-for="(x, i) in hotRows" :key="x.code" class="sc-row" :title="x.tip" v-flash="x.count">
                    <span :class="['no', { top: i < 3 }]">{{ two(i + 1) }}<i v-if="x.move" :class="['mv', x.move]"></i></span>
                    <span class="nm"><b class="mono">{{ $h.s(x.code) }}</b></span>
                    <small><span v-if="!x.agent">未分配</span><span v-if="x.agent" translate="no">{{ $h.s(x.agent) }}</span></small>
                    <span class="sc-meter"><span class="fill"><i :style="{ width: x.w + '%' }"></i></span><b>{{ num(x.count) }}<Bump :value="x.count"/></b></span>
                </div></TransitionGroup>
            <div class="sc-none" v-if="!hot.length">近 7 天没有口令被用过</div>
        </section>

        <section class="sc-pane sc-p-cover" v-if="!isAll">
            <div class="sc-head"><h2>更新覆盖</h2><span class="sc-sub">近 30 天</span><span class="sc-meta"><span>设备</span> <b>{{ num(cover.total) }}</b></span></div>
            <div class="sc-body sc-split">
                <div class="sc-donut">
                    <svg viewBox="0 0 100 100" aria-hidden="true">
                        <circle class="bg" cx="50" cy="50" r="40"></circle>
                        <circle v-for="s in cover.segs" :key="s.key" :class="['seg', s.key]" cx="50" cy="50" r="40" :stroke-dasharray="s.len + ' 999'" :stroke-dashoffset="-s.at"></circle>
                    </svg>
                    <div><b>{{ cover.latestPct }}<i>%</i></b><span>最新版</span></div>
                </div>
                <div class="sc-keys">
                    <div v-for="s in cover.segs" :key="s.key" class="sc-key"><i :class="s.key"></i><span>{{ s.label }}</span><b>{{ num(s.v) }}</b><em>{{ s.pct }}%</em></div>
                </div>
            </div>
        </section>

        <section class="sc-pane sc-p-mix" v-if="!isAll">
            <div class="sc-head"><h2>设备构成</h2><span class="sc-sub">近 30 天</span></div>
            <div class="sc-body sc-mix">
                <div class="sc-plat">
                    <div class="sc-plat-bar"><i class="ios" :style="{ width: plat.iosW + '%' }"></i><i class="and" :style="{ width: plat.andW + '%' }"></i></div>
                    <div class="sc-plat-keys">
                        <span><i class="ios"></i>iOS<b>{{ num(plat.ios) }}</b><em>{{ plat.iosPct }}%</em></span>
                        <span><i class="and"></i>Android<b>{{ num(plat.android) }}</b><em>{{ plat.androidPct }}%</em></span>
                    </div>
                </div>
                <div class="sc-cols2">
                    <div v-for="g in shares" :key="g.key" class="sc-share">
                        <div class="sc-cap">{{ g.title }}</div>
                        <div class="sc-list" v-fit:[g.fit]="2"><div v-for="(r, i) in g.shown" :key="i" class="sc-row plain" :title="r.pct + '%'">
                                <span :class="['nm', { mono: g.key === 'rt' }]"><b translate="no">{{ $h.s(r.label) }}</b></span>
                                <span class="sc-meter sm"><span class="fill"><i :style="{ width: r.w + '%' }"></i></span><b>{{ num(r.count) }}</b><em>{{ r.pct }}%</em></span>
                            </div><div class="sc-none" v-if="!g.rows.length">还没有设备上报</div></div>
                    </div>
                </div>
            </div>
        </section>

        <section class="sc-pane sc-p-lanes" v-if="!isAll">
            <div class="sc-head"><h2>线上版本</h2><span class="sc-meta bad" v-if="m.scAlarms > 0"><span>告警</span> <b>{{ num(m.scAlarms) }}</b></span><span class="sc-meta" v-if="!(m.scAlarms > 0) && lanes.length"><span>渠道</span> <b>{{ num(lanes.length) }}</b></span></div>
            <div class="sc-body sc-list sc-lanes" v-fit:lanes="3.5"><div v-for="(l, i) in laneRows" :key="i" :class="['sc-lane', { alarm: l.alarm }]">
                    <div class="l1">
                        <b translate="no">{{ $h.s(l.channel) }}</b>
                        <span class="mono" v-if="l.release">{{ $h.s(l.release) }}</span>
                        <em class="sc-tag bad" v-if="l.embedded">回退内置版本</em>
                        <span class="msg" translate="no">{{ $h.s(l.message) }}</span>
                        <em :class="['pct', { part: l.part }]" v-if="!l.embedded && l.release"><span v-if="l.part">灰度</span> {{ l.rollout }}%</em>
                    </div>
                    <span class="sc-track"><i :style="{ width: l.w + '%' }"></i></span>
                    <div class="l3">
                        <span>Runtime {{ $h.s(l.runtime) }}</span>
                        <span><span>装上</span> {{ num(l.installed) }} / {{ num(l.offered) }}</span>
                        <span :class="{ bad: l.alarm }"><span>启动失败</span> {{ l.failRate }}%</span>
                    </div>
                </div><div class="sc-none" v-if="!lanes.length">还没有发布</div></div>
        </section>

        <section :class="['sc-pane', 'sc-p-apps', { two: appTable.cols === 2 }]" v-if="isAll">
            <div class="sc-head">
                <h2>App 排行</h2><span class="sc-sub">今日有效激活</span>
                <span class="sc-meta"><span>共</span> <b>{{ num(m.scAppCount) }}</b> <span>个 App</span> · <span>今日有激活</span> <b>{{ num(m.scAppBusy) }}</b></span>
            </div>
            <div class="sc-cols" aria-hidden="true">
                <div v-for="col in appTable.cols" :key="col" class="hd"><span class="no">#</span><span class="app">App</span><span class="today">今日激活</span><span class="share">占比</span><span class="vs">比昨天同时段</span><span class="week">近 7 天</span><span class="spark">近 14 天</span><span class="devs">今日设备</span><span class="cov">最新版</span><span class="err">启动失败</span></div>
            </div>
            <TransitionGroup tag="div" name="sc-mv" :class="['sc-body', 'sc-list', 'sc-apps', { cols2: appTable.cols === 2 }]" v-fit:apps="2.3"><a v-for="(a, i) in appTable.cells" :key="a.key" :class="['sc-app', { quiet: !a.today, alarm: a.alarm, rest: a.rest }]" :href="shared ? null : a.href" :title="a.rest ? (shared ? null : '打开切换 App 看全部') : a.name" @click="onCell($event, a)" v-flash="a.today">
                    <span :class="['no', { top: i < 3 && a.today > 0, none: !a.rest && !(a.today > 0) }]"><span v-if="!a.rest && a.today > 0">{{ two(i + 1) }}</span><span v-if="a.rest">…</span><i v-if="a.move" :class="['mv', a.move]"></i></span>
                    <span class="app"><Ava size="sm" :all="a.rest" :id="a.id" :icon="a.icon" :name="a.name"/><b v-if="!a.rest" translate="no">{{ $h.s(a.name) }}</b><b v-if="a.rest"><span>其余</span> {{ num(a.count) }} <span>个 App</span></b><em class="sc-tag warn" v-if="!a.rest && !a.live">审核中</em><em class="sc-tag bad" v-if="a.alarm">告警</em></span>
                    <span class="today sc-meter"><span class="fill"><i :style="{ width: a.w + '%' }"></i></span><b>{{ num(a.today) }}<Bump :value="a.today"/></b></span>
                    <span class="share">{{ a.share }}%</span>
                    <span :class="['vs', 'sc-d', a.vs ? a.vs.dir : 'none']">{{ a.vs ? a.vs.text : '—' }}</span>
                    <span class="week">{{ num(a.week) }}</span>
                    <span class="spark"><svg viewBox="0 0 60 16" preserveAspectRatio="none" aria-hidden="true"><path :d="a.spark" :style="{ d: pathOf(a.spark) }"></path></svg></span>
                    <span class="devs">{{ num(a.devToday) }}</span>
                    <span class="cov"><i v-if="a.cover >= 0"><i :style="{ width: (on ? a.cover : 0) + '%' }"></i></i><b v-if="a.cover >= 0">{{ a.cover }}%</b><b v-if="a.cover < 0">—</b></span>
                    <span :class="['err', { bad: a.errors > 0 }]">{{ a.errors > 0 ? num(a.errors) : '—' }}</span>
                </a></TransitionGroup>
            <button class="sc-more" type="button" v-if="appTable.more" @click="moreApps = true"><span>显示全部</span> {{ apps.length }} <span>个</span></button>
        </section>

        <section class="sc-pane sc-p-feed">
            <div class="sc-head"><h2>最新动态</h2><span class="sc-pulse"></span></div>
            <TransitionGroup tag="div" name="sc-ev" class="sc-body sc-list sc-feed" v-fit:feed="2.15"><div v-for="e in feedRows" :key="e.key" :class="['sc-ev', e.kind]">
                    <i class="dot"></i>
                    <div class="tx">
                        <em class="app" v-if="e.app" translate="no">{{ $h.s(e.app) }}</em>
                        <template v-if="e.act">
                            <b class="mono" v-if="e.main">{{ $h.s(e.main) }}</b>
                            <span>{{ e.kind === 'confirm' ? '有效激活' : '提交激活' }}</span>
                            <small v-if="e.sub"><span>设备</span> {{ $h.s(e.sub) }}</small>
                        </template>
                        <template v-if="!e.act">
                            <b translate="no">{{ $h.s(e.main) }}</b>
                            <span>{{ VERBS[e.kind] || '发布' }}</span>
                            <small v-if="e.sub" class="mono">{{ $h.s(e.sub) }}</small>
                            <small v-if="e.kind === 'deploy' || e.kind === 'rollout'">{{ e.rollout }}%</small>
                        </template>
                    </div>
                    <time>{{ e.ago }}</time>
                </div></TransitionGroup>
            <div class="sc-none" v-if="!events.length">还没有动态</div>
        </section>
    </main>

    <footer class="sc-bottom">
        <div class="sc-todo" v-if="todos.length">
            <b><svg class="i"><use href="#i-alert"></use></svg><span>待处理</span> {{ $h.s(m.scTodoCount) }}</b>
            <component :is="shared ? 'span' : 'a'" v-for="(t, i) in todos" :key="i" :class="t.level" :href="shared ? null : $h.ak(t.href)"><em v-if="t.app" translate="no">{{ $h.s(t.app) }}</em><span>{{ $h.s(t.title) }}</span></component>
        </div>
        <div class="sc-todo ok" v-if="!todos.length"><b><svg class="i"><use href="#i-tick"></use></svg><span>一切正常，没有待处理的事</span></b></div>
        <span class="sc-stamp"><span>{{ lost ? '数据停在' : '数据更新于' }}</span> {{ stamp }} · <span>每 10 秒自动刷新</span><template v-if="shared"> · <span>{{ m.scShareUntil ? '分享有效至 ' + $h.ui.dt(m.scShareUntil) : '长期有效的分享' }}</span></template></span>
    </footer>

    <dialog class="sc-pick" ref="pickEl" aria-label="切换 App" @close="pick.open = false">
        <label class="sp-search">
            <svg class="i"><use href="#i-search"></use></svg>
            <input ref="pickInput" type="search" v-model="pick.q" placeholder="搜索 App 名称或标识" autocomplete="off" spellcheck="false" aria-label="搜索 App" @keydown="onPickKey" @input="pick.index = 0">
            <kbd>esc</kbd>
        </label>
        <div class="sp-list" ref="pickList" v-if="pick.open">
            <a v-for="(o, i) in options" :key="o.key" :class="['sp-item', { on: o.on, cur: i === pick.index, all: o.all }]" :href="o.href" @click="choose($event, o)" @mousemove="pick.index = i">
                <Ava :all="o.all" :id="o.id" :icon="o.icon" :name="o.name"/>
                <span class="sp-tx">
                    <b v-if="o.all">全部 App</b><b v-if="!o.all" translate="no">{{ $h.s(o.name) }}</b>
                    <small v-if="o.all"><span>{{ picks.length }}</span> <span>个 App 合在一起看</span></small>
                    <small v-if="!o.all"><span class="mono">{{ $h.s(o.slug) }}</span><i :class="{ b: o.live }"></i><span>{{ o.live ? 'B 面' : 'A 面' }}</span></small>
                </span>
                <span class="sp-n"><b>{{ num(o.today) }}</b><small>今日激活</small></span>
                <svg class="i sp-tick" v-if="o.on"><use href="#i-tick"></use></svg>
            </a>
            <div class="sp-none" v-if="!options.length">没有匹配的 App</div>
        </div>
        <div class="sp-foot"><span><kbd>↑</kbd><kbd>↓</kbd><span>选择</span></span><span><kbd>↵</kbd><span>打开</span></span><span><kbd>esc</kbd><span>关闭</span></span></div>
    </dialog>
    <FScreenShare ref="shareDlg" :all="isAll" :slug="m.scSlug" :name="m.scName" :app-id="m.scAppId" :icon="m.scIcon" :count="picks.length" :light="light" v-if="!shared &amp;&amp; !m.demoMode"/>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.cat(m.scName ? m.scName : '全部 App', ' · 数据大屏 · ', $h.elvis(m.brandName, '管理后台')),
    css: ["app/ui.css"],
    js: ["app/ui.js"],
    theme: { attr: true, css: (m, c, $h) => m.themeCss },
    // 定时刷新时页面组件留着、只换数据（main.js 的 patch）：这里的数字、图形都是跟着数据变的，过渡交给 CSS
    live: true
};
</script>

<script setup>
import { computed, defineComponent, getCurrentInstance, h, nextTick, onBeforeUnmount, onMounted, reactive, ref, TransitionGroup, watch } from 'vue';
import FAdminLayoutOverlays from '../../frags/admin-layout--overlays.vue';
import FScreenShare from '../../frags/admin-layout--screenShare.vue';

const props = defineProps({ m: Object, c: Object });
const $h = getCurrentInstance().appContext.config.globalProperties.$h;

// ---------- 小工具 ----------
const fmt = new Intl.NumberFormat('en-US');
const num = (v) => v === null || v === undefined || v === '' ? '—' : fmt.format(Math.round(+v));
const two = (n) => String(n).padStart(2, '0');
const arr = (v) => Array.isArray(v) ? v : [];
const nums = (v) => arr(v).map((x) => +x || 0);
const pct = (v, total) => $h.ui.pctOf(v, total);
const r1 = (v) => Math.round(v * 10) / 10;
/** 和上一段时间比的变化（服务端算好的 Delta） */
const delta = (d) => d ? { dir: $h.p(d, 'dir'), text: $h.p(d, 'text') } : null;
/** 曲线换了数据时形状平滑地变过去（CSS 的 d 属性；不支持的浏览器直接换） */
const pathOf = (d) => d ? 'path("' + d + '")' : null;

/** 坐标轴的上限取个整，并且是偶数，中线的刻度也是整数 */
function nice(v) {
    if (v <= 4) return 4;
    const p = Math.pow(10, Math.floor(Math.log10(v)));
    for (const k of [1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10]) {
        const top = Math.round(k * p);
        if (top >= v && top % 2 === 0) return top;
    }
    return 20 * p;
}
/** 纵轴最上面：比最大值大一点的整数（两位有效数字、偶数，中间那条线也是整数），曲线最高能到顶 */
function snug(v) {
    if (v <= 4) return 4;
    const p = Math.pow(10, Math.max(0, Math.floor(Math.log10(v)) - 1));
    let top = Math.ceil(v / p) * p;
    if (top % 2) top += p;
    return top;
}
/** 单调三次曲线（Fritsch–Carlson）：过每个点、不会冲出相邻两点之间 */
function smooth(p) {
    const n = p.length;
    if (n === 0) return '';
    if (n === 1) return 'M' + r1(p[0][0]) + ',' + r1(p[0][1]);
    const dx = [], s = [];
    for (let i = 0; i < n - 1; i++) {
        dx[i] = p[i + 1][0] - p[i][0];
        s[i] = (p[i + 1][1] - p[i][1]) / dx[i];
    }
    const t = [s[0]];
    for (let i = 1; i < n - 1; i++) {
        t[i] = s[i - 1] * s[i] <= 0 ? 0 : 3 * (dx[i - 1] + dx[i]) / ((2 * dx[i] + dx[i - 1]) / s[i - 1] + (dx[i] + 2 * dx[i - 1]) / s[i]);
    }
    t[n - 1] = s[n - 2];
    let d = 'M' + r1(p[0][0]) + ',' + r1(p[0][1]);
    for (let i = 0; i < n - 1; i++) {
        const k = dx[i] / 3;
        d += 'C' + r1(p[i][0] + k) + ',' + r1(p[i][1] + k * t[i]) + ' ' + r1(p[i + 1][0] - k) + ',' + r1(p[i + 1][1] - k * t[i + 1])
            + ' ' + r1(p[i + 1][0]) + ',' + r1(p[i + 1][1]);
    }
    return d;
}
function points(values, max, w, hgt) {
    const n = values.length;
    return values.map((v, i) => [n === 1 ? 0 : i * w / (n - 1), hgt - (max > 0 ? v / max * hgt : 0)]);
}
/** App 排行里的迷你趋势线（60 × 16） */
function sparkline(values) {
    const n = values.length, max = Math.max(1, ...values);
    if (n < 2) return '';
    return values.map((v, i) => (i ? 'L' : 'M') + r1(i * 60 / (n - 1)) + ',' + r1(14.5 - v * 13 / max)).join('');
}

// ---------- 进场：挂载时先画成 0，两帧以后换成真实的值，CSS 过渡负责动；之后数据变了也是这些过渡接着动 ----------
const on = ref(false);
const intro = ref(true);
// 大屏的动画只听外观里的「动画」开关：不少电脑（远程桌面、关了系统动画效果的）会报「减少动态效果」，大屏要照样动
const calm = typeof window !== 'undefined' && document.documentElement.getAttribute('data-motion') === 'off';

/** 翻牌数字：每一位从原来的数滚到新的数 */
const DIGITS = '0123456789'.split('');
/**
 * 跳动的数字：每一位一条竖着的长条（最前面一格空白，后面 0–9 三遍），滚到要显示的那一格。
 * 变大往上滚、变小往下滚（个位 8 到 10 是 8→9→0 接着往上，不会倒着数成 18、17…）；多出来的位从空白滚进来。
 * 滚完悄悄挪回中间那一遍，下次往哪边滚都有地方
 */
const STRIP = [''].concat(DIGITS, DIGITS, DIGITS);
const HOME = 11;
const STEP = 100 / STRIP.length;
const Num = defineComponent({
    props: { value: { type: Number, default: 0 }, on: Boolean },
    setup(p) {
        const st = reactive({ at: {}, still: {} });
        let shown = null, settle = 0, raf1 = 0, raf2 = 0;
        const textOf = (v) => fmt.format(Math.round(v || 0));
        const digitsOf = (text) => {
            const n = text.length, out = {};
            for (let i = 0; i < n; i++) if (text[i] >= '0' && text[i] <= '9') out['d' + (n - i)] = +text[i];
            return out;
        };
        const home = (d) => HOME + d;
        const toward = (a, d, up) => up ? a + (d - (a - 1) % 10 + 10) % 10 : a - ((a - 1) % 10 - d + 10) % 10;
        function hold(v, zero) {
            const ds = digitsOf(textOf(v)), at = {}, still = {};
            for (const k in ds) {
                at[k] = zero ? HOME : home(ds[k]);
                still[k] = true;
            }
            st.at = at;
            st.still = still;
        }
        function go(v) {
            const ds = digitsOf(textOf(v)), up = shown === null || v >= shown;
            cancelAnimationFrame(raf1);
            cancelAnimationFrame(raf2);
            clearTimeout(settle);
            // 停在原位的直接滚；新冒出来的位先放在空白、还没归位的先悄悄挪回中间，下一帧再滚
            const at = {}, still = {}, later = {};
            for (const k in ds) {
                const cur = st.at[k], d = ds[k];
                if (!cur) {
                    at[k] = 0;
                    still[k] = true;
                    later[k] = 1 + d;
                } else if (cur !== home((cur - 1) % 10)) {
                    at[k] = home((cur - 1) % 10);
                    still[k] = true;
                    later[k] = toward(at[k], d, up);
                } else {
                    at[k] = toward(cur, d, up);
                }
            }
            shown = v;
            st.at = at;
            st.still = still;
            if (Object.keys(later).length) {
                raf1 = requestAnimationFrame(() => {
                    raf2 = requestAnimationFrame(() => {
                        st.at = Object.assign({}, st.at, later);
                        st.still = {};
                    });
                });
            }
            settle = setTimeout(() => hold(shown, false), 2100);
        }
        watch(() => [p.value, p.on], ([v, on], before) => {
            if (!on) {
                shown = null;
                hold(v, true);
                return;
            }
            if (before && before[1] && Math.round(v || 0) === Math.round(before[0] || 0)) return;
            go(v);
        }, { immediate: true });
        onBeforeUnmount(() => {
            cancelAnimationFrame(raf1);
            cancelAnimationFrame(raf2);
            clearTimeout(settle);
        });
        return () => {
            const text = textOf(p.value), n = text.length;
            return h('span', { class: 'odo', 'aria-label': text }, text.split('').map((ch, i) => {
                if (ch < '0' || ch > '9') return h('span', { class: 'odo-p', key: 'p' + (n - i) }, ch);
                const key = 'd' + (n - i), at = st.at[key] === undefined ? HOME + +ch : st.at[key], still = !!st.still[key];
                return h('span', { class: 'odo-c', key }, [h('span', {
                    class: ['odo-s', { still }],
                    style: { transform: 'translateY(' + (-at * STEP) + '%)', transitionDelay: still ? null : ((n - 1 - i) * 55) + 'ms' }
                }, STRIP.map((x, j) => h('span', { key: j }, x)))]);
            }));
        };
    }
});

/** 数涨了：旁边冒一个「+N」，飘起来淡掉（第一次打开、数没变、数变小都不冒） */
const Bump = defineComponent({
    props: { value: { type: Number, default: 0 } },
    setup(p) {
        const st = reactive({ n: 0, k: 0 });
        let timer = 0;
        watch(() => p.value, (v, o) => {
            if (!on.value || !(v > o)) return;
            st.n = v - o;
            st.k++;
            clearTimeout(timer);
            timer = setTimeout(() => { st.n = 0; }, 2600);
        });
        onBeforeUnmount(() => clearTimeout(timer));
        return () => st.n > 0 ? h('i', { class: 'sc-bump', key: st.k, 'aria-hidden': 'true' }, '+' + fmt.format(st.n)) : null;
    }
});

/** App 的图标；没有图标用名字的第一个字，「全部 App」用叠起来的图标。分享出去的页面没登录后台，图标从分享的地址取 */
function iconSrc(id, v) {
    if (shared.value) return location.pathname.split('/').slice(0, 3).join('/') + '/icon/' + id + '?v=' + v;
    return '/admin/apps/' + id + '/icon?v=' + v;
}
const Ava = (p) => h('span', { class: ['sc-ava', p.size, { img: !!p.icon && !p.all }], 'aria-hidden': 'true' },
    p.all ? [h('svg', { class: 'i' }, [h('use', { href: '#i-layers' })])]
        : p.icon ? [h('img', { src: iconSrc(p.id, p.icon), alt: '', loading: 'lazy' })]
            : (Array.from(p.name || '?')[0] || '?'));
Ava.props = ['id', 'icon', 'name', 'size', 'all'];

// ---------- 时钟：按服务器的时间和时区走；自己一个组件，每秒只重画这几个字 ----------
const zone = (props.c && props.c.zone) || undefined;
const skew = props.c && props.c.now ? props.c.now - Date.now() : 0;
const fTime = new Intl.DateTimeFormat('en-GB', { timeZone: zone, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
const fDate = new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' });
const fWeek = new Intl.DateTimeFormat('zh-CN', { timeZone: zone, weekday: 'short' });
const Clock = defineComponent({
    props: { skew: { type: Number, default: 0 } },
    setup(p) {
        const at = ref(Date.now());
        let timer = 0;
        onMounted(() => { timer = setInterval(() => { at.value = Date.now(); }, 1000); });
        onBeforeUnmount(() => clearInterval(timer));
        return () => {
            const d = new Date(at.value + p.skew);
            return h('div', { class: 'sc-now' }, [h('b', fTime.format(d)), h('span', fDate.format(d)), h('span', fWeek.format(d))]);
        };
    }
});

// ---------- 分享出去的（/s/口令）：和后台看到的一样，只是不能切换 App、不能点进后台、不能再分享 ----------
const shared = computed(() => {
    const m = props.m;
    return !!m.scShared;
});
const shareDlg = ref(null);
function openShare() {
    if (shareDlg.value) shareDlg.value.open();
}
/** 分享出去的页面上：把这个链接发给别人（手机上调系统的分享，不支持的复制链接） */
function shareLink() {
    const url = location.href.split('#')[0];
    if (navigator.share) {
        navigator.share({ title: document.title, url }).catch(() => {});
        return;
    }
    const done = () => { if (window.otaToast) window.otaToast('链接已复制，发给别人就能看'); };
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(url).then(done, () => { if (window.otaCopy) window.otaCopy(url, '链接已复制，发给别人就能看'); });
    } else if (window.otaCopy) {
        window.otaCopy(url, '链接已复制，发给别人就能看');
    }
}

// ---------- 范围：一个 App 还是全部 App ----------
const isAll = computed(() => {
    const m = props.m;
    return m.scScope === 'all';
});
const picks = computed(() => {
    const m = props.m;
    return arr(m.scPicks).map((a) => ({ slug: $h.p(a, 'slug'), name: $h.p(a, 'name'), id: $h.p(a, 'id'), icon: $h.p(a, 'icon'),
        live: !!$h.p(a, 'live'), today: +$h.p(a, 'today') || 0 }));
});
const many = computed(() => picks.value.length > 1);
const here = typeof location !== 'undefined' ? location.pathname + location.search : '/admin/screen';

// ---------- 列表只放整行：一屏模式下按列表的高度算出放得下几行，就只列这几行，行高正好分完，不会有切掉一半的行，也不滚动、不自动翻页 ----------
const ONE_SCREEN = '(min-width:1200px) and (min-height:700px)';
const caps = reactive({});
const fitOf = new WeakMap();
function fitMeasure(el) {
    const st = fitOf.get(el);
    if (!st) return;
    const box = el.getBoundingClientRect();
    const whole = !!(window.matchMedia && matchMedia(ONE_SCREEN).matches);
    if (!whole || box.height < 8) {
        if (caps[st.key]) caps[st.key] = 0;
        el.style.removeProperty('--rh');
        el.style.removeProperty('--per');
        return;
    }
    const fs = parseFloat(getComputedStyle(el).fontSize) || 14;
    const per = Math.max(1, Math.floor((box.height + 0.5) / (st.min * fs)));
    el.style.setProperty('--rh', (box.height / per).toFixed(2) + 'px');
    el.style.setProperty('--per', String(per));
    if (caps[st.key] !== per) caps[st.key] = per;
    const w = Math.round(box.width / fs);
    if (caps[st.key + ':w'] !== w) caps[st.key + ':w'] = w;
}
const vFit = {
    mounted(el, b) {
        const st = { key: b.arg, min: +b.value || 2.2, ro: null };
        fitOf.set(el, st);
        if (typeof ResizeObserver !== 'undefined') {
            st.ro = new ResizeObserver(() => fitMeasure(el));
            st.ro.observe(el);
        }
        fitMeasure(el);
    },
    unmounted(el) {
        const st = fitOf.get(el);
        if (st && st.ro) st.ro.disconnect();
        fitOf.delete(el);
    }
};
/** 一屏模式下是算出来的行数；别的时候（平板、手机按内容往下排）用 dflt */
function cap(key, dflt) {
    const c = caps[key];
    return c > 0 ? c : dflt;
}

/** 名次变了：往上的标 ▲、往下的标 ▼，亮 12 秒；行本身由 TransitionGroup 滑到新位置 */
function useMoves(order) {
    const moves = reactive(new Map());
    let last = null, timer = 0;
    watch(order, (now) => {
        const list = now ? now.split('\n') : [];
        if (last) {
            let any = false;
            list.forEach((k, i) => {
                const was = last.get(k);
                if (was !== undefined && was !== i) {
                    moves.set(k, i < was ? 'up' : 'down');
                    any = true;
                }
            });
            if (any) {
                clearTimeout(timer);
                timer = setTimeout(() => moves.clear(), 12000);
            }
        }
        last = new Map(list.map((k, i) => [k, i]));
    }, { immediate: true });
    onBeforeUnmount(() => clearTimeout(timer));
    return moves;
}

// ---------- 图表 ----------
const HOUR_MARKS = [0, 6, 12, 18, 23];
const trend = computed(() => {
    const m = props.m;
    const cur = nums(m.scTrend), prev = nums(m.scTrendPrev), labels = arr(m.scDays);
    // 最高的那天离顶留一点点空
    const max = snug(Math.max(0, ...cur, ...prev) * 1.06);
    const pts = points(cur, max, 1000, 300);
    const line = smooth(pts);
    const n = cur.length;
    const xs = [];
    for (let i = 0; i < n; i += 6) xs.push({ i, at: n === 1 ? 0 : i * 100 / (n - 1), label: labels[i] });
    if (n && xs[xs.length - 1].i !== n - 1) {
        if (n - 1 - xs[xs.length - 1].i < 3) xs.pop();
        xs.push({ i: n - 1, at: 100, label: labels[n - 1] });
    }
    const sum = cur.reduce((a, b) => a + b, 0);
    const peak = Math.max(0, ...cur);
    return {
        line,
        area: line ? line + 'L1000,300L0,300Z' : '',
        prev: smooth(points(prev, max, 1000, 300)),
        ticks: [max, max / 2, 0],
        dotY: n ? r1(pts[n - 1][1] / 3) : 100,
        // 每天一格、格子中间正对那天的点：两头各多出半格
        half: n > 1 ? Math.round(50000 / (n - 1)) / 1000 : 0,
        cols: cur.map((v, i) => ({ v, p: prev[i] || 0, label: labels[i], y: r1(pts[i][1] / 3) })),
        xs,
        avg: n ? sum / n : 0,
        peak,
        empty: sum === 0
    };
});

const hours = computed(() => {
    const m = props.m;
    const hv = nums(m.scHours), y = nums(m.scHoursY), at = +m.scHour || 0;
    const max = Math.max(1, ...hv, ...y);
    let peak = null;
    hv.forEach((v, i) => { if (v > 0 && (peak === null || v > hv[peak])) peak = i; });
    return {
        list: hv.map((v, i) => ({ h: i, v, y: y[i] || 0, now: i === at, later: i > at,
            pct: !on.value ? 0 : i > at ? 1.5 : Math.max(v > 0 ? 3 : 1.5, v * 100 / max) })),
        yline: smooth(y.map((v, i) => [i * 10 + 5, 100 - v * 100 / max])),
        peak,
        peakV: peak === null ? 0 : hv[peak],
        empty: !hv.some((v) => v > 0) && !y.some((v) => v > 0)
    };
});

const dev = computed(() => {
    const m = props.m;
    const ios = nums(m.scDevIos), and = nums(m.scDevAndroid), oth = nums(m.scDevOther), fresh = nums(m.scDevFresh), labels = arr(m.scDevDays);
    const totals = ios.map((v, i) => v + (and[i] || 0) + (oth[i] || 0));
    const max = Math.max(1, ...totals), n = totals.length;
    return {
        days: totals.map((t, i) => ({ total: t, ios: ios[i], android: and[i] || 0, other: oth[i] || 0, fresh: fresh[i] || 0, label: labels[i],
            pct: !on.value ? 0 : t > 0 ? Math.max(3, t * 100 / max) : 1.5, today: i === n - 1, mark: (n - 1 - i) % 3 === 0 })),
        hasOther: oth.some((v) => v > 0),
        empty: !totals.some((v) => v > 0)
    };
});

const C40 = 2 * Math.PI * 40;
const COVER = { latest: '最新版', older: '旧版本', embedded: '安装包内置', unknown: '未上报' };
const cover = computed(() => {
    const m = props.m;
    const parts = arr(m.scCover).map((x) => ({ key: $h.p(x, 'key'), v: +$h.p(x, 'count') || 0 }));
    const total = parts.reduce((s, x) => s + x.v, 0);
    const shown = parts.filter((x) => x.v > 0).length;
    let at = 0;
    const segs = parts.map((x) => {
        const len = total && on.value ? x.v / total * C40 : 0;
        const seg = { key: x.key, label: COVER[x.key] || x.key, v: x.v, pct: pct(x.v, total), at: r1(at),
            len: r1(Math.max(0, len - (shown > 1 && len > 0 ? 1.6 : 0))) };
        at += len;
        return seg;
    });
    const latest = parts.find((x) => x.key === 'latest');
    return { segs, total, latestPct: pct(latest ? latest.v : 0, total) };
});

const plat = computed(() => {
    const m = props.m;
    const ios = +m.scIos || 0, android = +m.scAndroid || 0, total = ios + android;
    const iosPct = pct(ios, total), androidPct = total ? 100 - iosPct : 0;
    return { ios, android, iosPct, androidPct, iosW: on.value ? iosPct : 0, andW: on.value ? androidPct : 0 };
});

/** Runtime 和渠道两列：名称、占比、台数，台数底下一条按占比的色带 */
const shares = computed(() => {
    const m = props.m;
    const group = (key, title, list) => {
        const rows = arr(list).map((r) => {
            const p = Math.max(0, +$h.p(r, 'pct') || 0);
            return { label: $h.p(r, 'label'), count: +$h.p(r, 'count') || 0, pct: p, w: on.value ? p : 0 };
        });
        return { key, title, fit: 'mix-' + key, rows, shown: rows.slice(0, cap('mix-' + key, 6)) };
    };
    return [group('rt', 'Runtime', m.scRuntimes), group('ch', '渠道', m.scChannels)];
});

// ---------- 今日有效激活：圆环是今天已经到昨天全天（昨天没有就按近 7 天日均）的百分之多少，超过了再走第二圈 ----------
const C94 = 2 * Math.PI * 94;
const TICKS = [];
for (let i = 0; i < 72; i++) {
    const a = i / 72 * 2 * Math.PI - Math.PI / 2, c = Math.cos(a), s = Math.sin(a);
    TICKS.push({ x1: r1(120 + 106 * c), y1: r1(120 + 106 * s), x2: r1(120 + 111 * c), y2: r1(120 + 111 * s) });
}
const today = computed(() => {
    const m = props.m;
    return +m.scToday || 0;
});
const ring = computed(() => {
    const m = props.m;
    const yday = +m.scYesterday || 0;
    const days = nums(m.scTrend).slice(-8, -1);
    const avg = days.length ? days.reduce((s, x) => s + x, 0) / days.length : 0;
    const base = yday > 0 ? yday : avg;
    const ratio = base > 0 ? today.value / base : 0;
    // 最多画到第 10 圈；满一圈以后叠在上面的那一圈画到哪里（正好满的时候算满，不算新的一圈）
    const shown = on.value ? Math.min(ratio, 9.99) : 0;
    const p = Math.min(1, shown), whole = Math.floor(shown), frac = shown - whole;
    const laps = shown < 1 ? 1 : frac > 0.0005 ? whole + 1 : whole;
    const over = shown < 1 ? 0 : frac > 0.0005 ? frac : 1;
    return { arc: r1(p * C94) + ' ' + r1(C94), lap: r1(over * C94) + ' ' + r1(C94), laps,
        // 圆点一直往前转（过了一圈接着加），跨圈时不会倒着转回去
        rot: r1(shown * 360), lit: Math.round(p * 72), base, avg, yday: yday > 0, pct: Math.round(ratio * 100) };
});
/** 领先多少（0–1）：没比昨天同一时刻多是 0，多出昨天同一时刻的六成（至少 5 台）算满。领先的那一段亮多少、呼吸多快跟着它 */
const heat = computed(() => {
    const m = props.m;
    const same = +m.scSameTime || 0, t = today.value;
    if (!(t > same)) return 0;
    return Math.round(Math.min(1, (t - same) / Math.max(5, same * 0.6)) * 100) / 100;
});
/** 圆点身后的光尾：比昨天同一时刻少时短一点，领先时随领先的多少拉长（第一圈还没走到这么长时就到起点为止）；坐标在圆点那一组里，圆点在正上方 */
const tail = computed(() => {
    const deg = Math.min(fx.value.ahead ? 44 + 36 * heat.value : 34, ring.value.rot);
    if (!(deg >= 3)) return { d: '', x: 66, y: 43, deg: 0 };
    const a = deg * Math.PI / 180, x = r1(120 - 94 * Math.sin(a)), y = r1(120 - 94 * Math.cos(a));
    return { d: 'M120,26A94,94 0 0 0 ' + x + ',' + y, x, y, deg };
});
/**
 * 光尾里的颗粒：几条从圆点往后、越往后散得越开的线，颗粒顺着线冲向圆点，粗细、间隔、快慢各不一样，看起来像一股往前冲的颗粒流。
 * 每条：往外偏多少（越往后偏得越多，圆点处收成一点）、占光尾多长、颗粒间隔、颗粒大小、快慢倍数
 */
const GRAINS = [[0, 1, 6, 3.2, 1], [-4, .9, 7.5, 2.4, 1.2], [4, .85, 7, 2.6, 1.1], [-8.5, .72, 9, 1.9, 1.45], [8, .66, 8.5, 2, 1.35],
    [-13.5, .5, 11, 1.5, 1.8], [13, .46, 10, 1.6, 1.7]];
function grainOf(deg, spread, ahead) {
    const pts = [];
    for (let i = 0; i <= 12; i++) {
        const t = i / 12, a = (ahead ? -deg : deg) * t * Math.PI / 180, r = 94 + spread * t;
        pts.push(r1(120 - r * Math.sin(a)) + ',' + r1(120 - r * Math.cos(a)));
    }
    return 'M' + pts.join('L');
}
const r2 = (v) => Math.round(v * 100) / 100;
/**
 * 每条颗粒多亮、跑一格要多久：落后时只有中间三条、慢慢流；领先时都亮起来，越领先越亮越快（浅色主题外面几条淡一些）。
 * 算成数直接写在每条上，不靠 CSS 里 calc 套变量（有的浏览器算不出来，颗粒就不动或者不显示）
 */
function grainLook(i, sp) {
    const h = heat.value;
    if (!fx.value.ahead) return { op: i === 0 ? 0.6 : i < 3 ? 0.38 : 0, dur: r2(2.4 * sp) + 's' };
    const op = i < 3 ? 0.7 + h * 0.3 : i < 5 ? (light.value ? 0.35 + h * 0.35 : 0.45 + h * 0.45) : (light.value ? 0.2 + h * 0.3 : 0.3 + h * 0.5);
    return { op: r2(op), dur: r2((1 - h * 0.45) * sp) + 's' };
}
const grains = computed(() => GRAINS.map(([spread, len, gap, w, sp], i) => ({ d: grainOf(tail.value.deg * len, spread, false), gap, w, ...grainLook(i, sp) })));
/** 领先时圆点前面甩出去的几颗：从圆点往前飞一小段就淡掉 */
const SPRAY = [[0, 20, 5.5, 2.6, 1], [-3.5, 15, 6.5, 1.8, .85], [3.5, 16, 7, 1.9, .9], [-7, 11, 8, 1.3, .75], [7, 12, 8, 1.4, .8]].map(([spread, deg, gap, w, sp]) => ({ d: grainOf(deg, spread, true), gap, w, sp }));
const spray = computed(() => SPRAY.map((l) => ({ ...l, op: r2(0.5 + heat.value * 0.5), dur: r2((0.95 - heat.value * 0.4) * l.sp) + 's' })));
/** 圆点、光尾、颗粒一起转。圆环、颗粒、圆点各画在一层：颗粒一直在动，只重画颗粒那一层，圆环和圆点的光晕不用每一帧跟着重算 */
const knob = computed(() => ({ transform: 'rotate(' + ring.value.rot + 'deg)' }));
/** 昨天此刻：昨天到现在这个钟点占昨天全天的多少，在刻度里面标一道（超过昨天全天以后不标） */
const pace = computed(() => {
    const m = props.m;
    const yday = +m.scYesterday || 0, same = +m.scSameTime || 0;
    if (!on.value || !(yday > 0) || !(same > 0) || today.value >= yday) return null;
    return r1(Math.min(1, same / yday) * 360);
});
/** 数涨了的那一下，冲进圆点的那道光走的路：圆点身后最多 80° */
const streak = computed(() => {
    const deg = Math.min(80, ring.value.rot);
    if (!(deg >= 6)) return '';
    const a = deg * Math.PI / 180;
    return 'M120,26A94,94 0 0 0 ' + r1(120 - 94 * Math.sin(a)) + ',' + r1(120 - 94 * Math.cos(a));
});
/** 今天的数涨了：一道光顺着光尾冲进圆点，圆点亮一下 */
const surge = ref(false);
const hits = ref(0);
let surgeTimer = 0;
watch(today, (v, o) => {
    if (!on.value || !(v > o)) return;
    hits.value++;
    surge.value = false;
    clearTimeout(surgeTimer);
    requestAnimationFrame(() => {
        surge.value = true;
        surgeTimer = setTimeout(() => { surge.value = false; }, 1700);
    });
});

/** 圆环的状态：比昨天同一时刻多（ahead）、到了昨天全天（beat）、两倍以上（double，数字下面标 ×N） */
const fx = computed(() => {
    const m = props.m;
    const base = ring.value.base, ratio = base > 0 ? today.value / base : 0;
    const same = +m.scSameTime || 0;
    const ahead = today.value > 0 && today.value > same;
    const beat = base > 0 && ratio >= 1, double = base > 0 && ratio >= 2;
    const times = Math.floor(ratio);
    const tip = double ? '今天已经是' + (ring.value.yday ? '昨天全天' : '近 7 天日均') + '的 ' + times + ' 倍多'
        : beat ? '今天已经超过' + (ring.value.yday ? '昨天全天' : '近 7 天日均')
            : ahead ? '比昨天同一时刻多 ' + num(today.value - same) : null;
    return { ahead, beat, double, times, tip };
});
/** 每满一圈（到昨天全天、两倍、三倍…）圆环外面柔柔地亮一下，等圆环长到头再亮 */
const blooming = ref(false);
let bloomTimer = 0;
function bloom(delay) {
    clearTimeout(bloomTimer);
    bloomTimer = setTimeout(() => {
        if (calm || document.hidden) return;
        blooming.value = false;
        requestAnimationFrame(() => {
            blooming.value = true;
            bloomTimer = setTimeout(() => { blooming.value = false; }, 2300);
        });
    }, delay);
}
watch(() => fx.value.times, (now, before) => {
    if (on.value && now > before && fx.value.beat) bloom(1500);
});
const vsToday = computed(() => {
    const m = props.m;
    return delta(m.scTodayVs);
});
const vsMonth = computed(() => {
    const m = props.m;
    return delta(m.scMonthVs);
});
const rate7 = computed(() => {
    const m = props.m;
    const sub = +m.scSub7 || 0, conf = +m.scWeek || 0;
    return sub > 0 ? $h.ui.pctOf(conf, sub) : null;
});

// ---------- 排行 ----------
/** 色带的宽度：最多的那一行占满，其余按比例；有数的至少露一点 */
function bars(values) {
    const max = Math.max(0, ...values);
    return values.map((v) => !on.value || !(max > 0 && v > 0) ? 0 : Math.max(2, Math.round(v * 100 / max)));
}
const rankMoves = useMoves(() => {
    const m = props.m;
    return arr(m.scRanks).map((r) => 'a' + $h.p(r, 'id')).join('\n');
});
const ranks = computed(() => {
    const m = props.m;
    const rows = arr(m.scRanks).map((r) => ({ key: 'a' + $h.p(r, 'id'), name: $h.p(r, 'name'), today: +$h.p(r, 'today') || 0,
        week: +$h.p(r, 'week') || 0, off: !!$h.p(r, 'off') }));
    const w = bars(rows.map((r) => r.today));
    return rows.map((r, i) => ({ ...r, w: w[i], move: rankMoves.get(r.key) || '' }));
});
const rankRows = computed(() => ranks.value.slice(0, cap('rank', 8)));

const hotMoves = useMoves(() => {
    const m = props.m;
    return arr(m.scHotCodes).map((x) => $h.p(x, 'code')).join('\n');
});
const hot = computed(() => {
    const m = props.m;
    const rows = arr(m.scHotCodes).map((x) => {
        const used = +$h.p(x, 'used') || 0, max = +$h.p(x, 'max') || 0;
        return { code: $h.p(x, 'code'), agent: $h.p(x, 'agent'), count: +$h.p(x, 'count') || 0,
            tip: '已用 ' + num(used) + ' / ' + (max > 0 ? num(max) : '不限') };
    });
    const w = bars(rows.map((x) => x.count));
    return rows.map((x, i) => ({ ...x, w: w[i], move: hotMoves.get(x.code) || '' }));
});
const hotRows = computed(() => hot.value.slice(0, cap('hot', 8)));

const lanes = computed(() => {
    const m = props.m;
    return arr(m.scLanes).map((l) => {
        const installed = +$h.p(l, 'installed') || 0, offered = +$h.p(l, 'offered') || 0, rollout = +$h.p(l, 'rollout') || 0;
        return { channel: $h.p(l, 'channel'), runtime: $h.p(l, 'runtime'), release: $h.p(l, 'release'), message: $h.p(l, 'message'),
            rollout, embedded: !!$h.p(l, 'embedded'), installed, offered, failRate: +$h.p(l, 'failRate') || 0, alarm: !!$h.p(l, 'alarm'),
            part: rollout < 100, w: on.value && offered > 0 ? Math.min(100, Math.round(installed * 100 / offered)) : 0 };
    });
});
const laneRows = computed(() => lanes.value.slice(0, cap('lanes', 6)));

// ---------- 全部 App 的排行：放不下的合成最后一行「其余 N 个 App」；名次变了的行滑到新位置 ----------
const appMoves = useMoves(() => {
    const m = props.m;
    return arr(m.scApps).map((a) => $h.p(a, 'slug')).join('\n');
});
const apps = computed(() => {
    const m = props.m;
    const alarms = new Set(arr(m.scAlarmApps).map(String));
    const rows = arr(m.scApps).map((a) => ({ key: $h.p(a, 'slug'), slug: $h.p(a, 'slug'), name: $h.p(a, 'name'), id: $h.p(a, 'id'),
        icon: $h.p(a, 'icon'), live: !!$h.p(a, 'live'), today: +$h.p(a, 'today') || 0, vs: delta($h.p(a, 'vs')), week: +$h.p(a, 'week') || 0,
        days: nums($h.p(a, 'spark')), devToday: +$h.p(a, 'devToday') || 0, cover: +$h.p(a, 'cover'), errors: +$h.p(a, 'errors') || 0 }));
    const total = rows.reduce((s, a) => s + a.today, 0);
    const w = bars(rows.map((a) => a.today));
    return rows.map((a, i) => ({ ...a, href: '/admin/screen?app=' + encodeURIComponent(a.slug), spark: sparkline(a.days), w: w[i],
        share: pct(a.today, total), alarm: alarms.has(String(a.id)), move: appMoves.get(a.slug) || '', rest: false, count: 1 }));
});
const moreApps = ref(false);
function restOf(list, total) {
    const sum = (k) => list.reduce((s, a) => s + a[k], 0);
    const days = list.reduce((acc, a) => a.days.map((v, i) => v + (acc[i] || 0)), []);
    const today = sum('today');
    return { key: '__rest', rest: true, href: '#', name: '', count: list.length, today, w: 0, share: pct(today, total), vs: null,
        week: sum('week'), spark: sparkline(days), devToday: sum('devToday'), cover: -1, errors: sum('errors'), live: true, alarm: false, move: '' };
}
const appTable = computed(() => {
    const list = apps.value;
    const per = caps.apps || 0;
    if (!per) {
        const n = moreApps.value ? list.length : Math.min(list.length, 12);
        return { cols: 1, cells: list.slice(0, n), more: list.length > n };
    }
    const cols = list.length > per && (caps['apps:w'] || 0) >= 58 ? 2 : 1;
    const room = per * cols;
    if (list.length <= room) return { cols, cells: list, more: false };
    const total = list.reduce((s, a) => s + a.today, 0);
    return { cols, cells: list.slice(0, room - 1).concat([restOf(list.slice(room - 1), total)]), more: false };
});
function onCell(e, a) {
    if (shared.value) {
        e.preventDefault();
        return;
    }
    if (!a.rest) return;
    e.preventDefault();
    openPick();
}

// ---------- 指标 ----------
const kpis = computed(() => {
    const m = props.m;
    const weekVs = delta(m.scWeekVs), devVs = delta(m.scDevVs);
    const out = [
        { key: 'total', icon: 'i-tick', label: '累计有效激活', value: +m.scTotal || 0, note: '近 30 天', extra: num(m.scMonth) },
        { key: 'week', icon: 'i-trend-up', label: '近 7 天激活', value: +m.scWeek || 0, delta: weekVs, note: weekVs ? '比前 7 天' : '前 7 天没有激活' },
        { key: 'dev', icon: 'i-phone', label: '今日活跃设备', value: +m.scDevToday || 0, delta: devVs, note: devVs ? '比昨天' : '昨天没有记录' },
        { key: 'fresh', icon: 'i-zap', label: '今日新设备', value: +m.scDevNew || 0, note: '近 7 天活跃', extra: num(m.scDevWeek) },
        { key: 'month', icon: 'i-activity', label: '近 30 天活跃设备', value: +m.scDevMonth || 0, note: '最新版', extra: cover.value.latestPct + '%' }
    ];
    const codes = { key: 'usable', icon: 'i-key', label: '可用口令', value: +m.scCodeUsable || 0, of: +m.scCodeTotal || 0, note: '今日用掉', extra: num(m.scCodeUsedToday) };
    if (isAll.value) {
        out.push({ key: 'apps', icon: 'i-package', label: '上线中的 App', value: +m.scAppLive || 0, of: +m.scAppCount || 0, note: '今日有激活', extra: num(m.scAppBusy) });
    } else {
        out.push(codes);
    }
    if (m.scAgentsOn) {
        out.push({ key: 'agents', icon: 'i-users', label: '今日活跃代理', value: +m.scAgentActive || 0, of: +m.scAgentCount || 0,
            note: '待审核申请', extra: num(m.scRequests), subTone: m.scRequests > 0 ? 'warn' : '' });
    } else if (isAll.value) {
        out.push(codes);
    } else {
        out.push({ key: 'pending', icon: 'i-clock', label: '待确认激活', value: +m.scPending || 0, note: '今日提交', extra: num(m.scSubmitted) });
    }
    out.push({ key: 'errors', icon: 'i-alert', label: '启动失败 · 24 小时', value: +m.scErrors || 0,
        tone: m.scErrors > 0 || m.scAlarms > 0 ? 'bad' : '', note: m.scAlarms > 0 ? '告警版本' : '运行正常',
        extra: m.scAlarms > 0 ? num(m.scAlarms) : null, subTone: m.scAlarms > 0 ? 'bad' : '' });
    return out.map((k) => ({ tone: '', subTone: '', delta: null, note: '', extra: null, of: null, ...k,
        long: fmt.format(k.value).length + (k.of !== undefined && k.of !== null ? fmt.format(k.of).length + 2 : 0) > 8 }));
});

// ---------- 最新动态：新来的从上面滑进来，「几分钟前」每 20 秒跟一次 ----------
const VERBS = { deploy: '发布', rollout: '调整灰度', rollback: '回滚', embedded: '回退内置版本' };
const tick = ref(0);
const events = computed(() => {
    const m = props.m;
    void tick.value;
    return arr(m.scEvents).map((e) => {
        const kind = $h.p(e, 'kind'), main = $h.p(e, 'main'), sub = $h.p(e, 'sub'), at = $h.p(e, 'at'), app = $h.p(e, 'app');
        return { kind, app, main, sub, rollout: $h.p(e, 'rollout'), act: kind === 'confirm' || kind === 'submit',
            key: [kind, app, main, sub, +at].join('|'), ago: $h.ui.ago(at) };
    });
});
const feedRows = computed(() => events.value.slice(0, cap('feed', 10)));
const todos = computed(() => {
    const m = props.m;
    return arr(m.scTodos).map((t) => ({ level: $h.p(t, 'level'), app: $h.p(t, 'app'), title: $h.p(t, 'title'), href: $h.p(t, 'href') }));
});

// ---------- 实时：每 10 秒取一次数据（ui.js 按「实时」标记上的 data-live 定时），结果通过 ota:live 事件告诉这里 ----------
const live = reactive({ at: (props.c && props.c.now) || Date.now(), fails: 0, login: false, beat: 0 });
const lost = computed(() => live.fails >= 2);
const stamp = computed(() => fTime.format(new Date(live.at)));
function onLive(e) {
    const d = e.detail || {};
    if (d.ok) {
        live.at = d.now || Date.now();
        live.fails = 0;
        live.login = false;
        live.beat++;
        tick.value++;
    } else {
        live.fails++;
        if (d.login) live.login = true;
    }
}

// 倒计时圆环用 Web Animations 画：系统「减弱动态效果」、外观里「动画：关」会把 CSS 动画全关掉，它得照常走；那时一秒走一格
const countFg = ref(null);
let countAnim = null;
function runCount() {
    const el = countFg.value;
    if (countAnim) countAnim.cancel();
    countAnim = null;
    if (!el || !el.animate || lost.value || live.login) return;
    const calm = document.documentElement.getAttribute('data-motion') === 'off';
    countAnim = el.animate([{ strokeDashoffset: '44px' }, { strokeDashoffset: '0px' }],
        { duration: 10000, easing: calm ? 'steps(10, end)' : 'linear', fill: 'forwards' });
}
watch(() => [live.beat, lost.value, live.login], runCount);

// ---------- 切换 App：点标题、按 / 或 ⌘K 打开，可以搜索，上下键选、回车打开 ----------
const pickEl = ref(null), pickInput = ref(null), pickList = ref(null);
const pick = reactive({ open: false, q: '', index: 0 });
const options = computed(() => {
    const m = props.m;
    const q = pick.q.trim().toLowerCase();
    const out = [];
    if (many.value && (!q || '全部 app all quanbu'.indexOf(q) >= 0)) {
        out.push({ key: '*', all: true, href: '/admin/screen?scope=all', on: isAll.value, today: picks.value.reduce((s, a) => s + a.today, 0) });
    }
    for (const a of picks.value) {
        if (q && String(a.name).toLowerCase().indexOf(q) < 0 && String(a.slug).toLowerCase().indexOf(q) < 0) continue;
        out.push({ ...a, key: a.slug, all: false, href: '/admin/screen?app=' + encodeURIComponent(a.slug), on: !isAll.value && a.slug === m.scSlug });
    }
    return out;
});
function openPick() {
    const el = pickEl.value;
    if (!many.value || !el || el.open) return;
    pick.q = '';
    pick.open = true;
    pick.index = Math.max(0, options.value.findIndex((o) => o.on));
    el.showModal();
    nextTick(() => {
        // 触屏上不自动聚焦，免得键盘弹出来挡住列表
        if (window.matchMedia && matchMedia('(pointer:fine)').matches) pickInput.value && pickInput.value.focus();
        else if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
        reveal();
    });
}
function closePick() {
    const el = pickEl.value;
    if (el && el.open) el.close();
}
function reveal() {
    nextTick(() => {
        const cur = pickList.value && pickList.value.querySelector('.cur');
        if (cur && cur.scrollIntoView) cur.scrollIntoView({ block: 'nearest' });
    });
}
function go(o) {
    closePick();
    if (o.on) return;
    if (window.otaNavigate) window.otaNavigate(o.href);
    else location.href = o.href;
}
function choose(e, o) {
    if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || e.button) return;
    e.preventDefault();
    go(o);
}
function onPickKey(e) {
    if (e.isComposing) return;
    const n = options.value.length;
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        if (n) pick.index = (pick.index + (e.key === 'ArrowDown' ? 1 : n - 1)) % n;
        reveal();
    } else if (e.key === 'Enter') {
        e.preventDefault();
        const o = options.value[pick.index];
        if (o) go(o);
    }
}

// ---------- 深浅色：大屏自己的，存在账号偏好里（screen），和后台的深浅色互不相干；默认深色 ----------
const prefsTag = () => typeof document !== 'undefined' ? document.querySelector('meta[name=ota-prefs]') : null;
function storedPrefs() {
    try {
        const el = prefsTag();
        return (el && JSON.parse(el.getAttribute('content') || 'null')) || {};
    } catch (e) {
        return {};
    }
}
const SHARED_THEME = 'ota.screen.shared';
function sharedTheme() {
    try {
        const v = localStorage.getItem(SHARED_THEME);
        if (v === 'light' || v === 'dark') return v === 'light';
    } catch (e) { /* 存不了就用分享时的 */ }
    const m = props.m;
    return m.scShareTheme === 'light';
}
const light = ref(shared.value ? sharedTheme() : storedPrefs().screen === 'light');
function paintTheme() {
    const r = document.documentElement;
    r.classList.toggle('scr-light', light.value);
    r.classList.toggle('scr-dark', !light.value);
}
function toggleTheme() {
    const next = !light.value;
    const flip = () => {
        light.value = next;
        paintTheme();
        return nextTick();
    };
    // 浏览器支持就整屏淡过去，不然直接换
    if (document.startViewTransition && !calm) document.startViewTransition(flip);
    else flip();
    if (shared.value) {
        try { localStorage.setItem(SHARED_THEME, next ? 'light' : 'dark'); } catch (e) { /* 只是这次换 */ }
        return;
    }
    if (window.otaPrefs) window.otaPrefs({ screen: next ? 'light' : '' });
    // 加载壳里的偏好也跟着改：这次打开后台再回来还是这个颜色
    const el = prefsTag();
    if (el) {
        const v = storedPrefs();
        if (next) v.screen = 'light';
        else delete v.screen;
        el.setAttribute('content', JSON.stringify(v));
    }
}

// ---------- 全屏：按钮或者按 F ----------
const full = ref(typeof document !== 'undefined' && !!document.fullscreenElement);
function toggleFull() {
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    else if (document.documentElement.requestFullscreen) document.documentElement.requestFullscreen().catch(() => {});
}
function onFull() {
    full.value = !!document.fullscreenElement;
}
function onKey(e) {
    if (e.isComposing || e.defaultPrevented) return;
    if ((e.metaKey || e.ctrlKey) && !e.shiftKey && !e.altKey && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault();
        if (pickEl.value && pickEl.value.open) closePick();
        else openPick();
        return;
    }
    const a = document.activeElement;
    if (e.metaKey || e.ctrlKey || e.altKey || document.querySelector('dialog[open]')
        || (a && (a.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(a.tagName)))) return;
    if (e.key === 'f' || e.key === 'F') {
        e.preventDefault();
        toggleFull();
    } else if (e.key === '/') {
        e.preventDefault();
        openPick();
    }
}

/** 数涨了：这一行（这一格）亮一下 */
const vFlash = {
    updated(el, b) {
        if (calm || !on.value || !(+b.value > +b.oldValue)) return;
        el.classList.remove('lit');
        void el.offsetWidth;
        el.classList.add('lit');
        clearTimeout(el._litTimer);
        el._litTimer = setTimeout(() => el.classList.remove('lit'), 2200);
    },
    unmounted(el) {
        clearTimeout(el._litTimer);
    }
};

// ---------- 挂载 ----------
let raf = 0, late = 0, introTimer = 0, tickTimer = 0;
onMounted(() => {
    // 页面上别的东西（提示条、确认弹窗）也跟着用深色
    document.documentElement.classList.add('scr-on');
    paintTheme();
    document.addEventListener('fullscreenchange', onFull);
    document.addEventListener('keydown', onKey);
    window.addEventListener('ota:live', onLive);
    runCount();
    tickTimer = setInterval(() => { tick.value++; }, 20000);
    const start = () => {
        if (on.value) return;
        on.value = true;
    };
    // 两帧以后再换成真实的值，保证 0 的样子先画出来；页面在后台不跑动画帧时用定时器兜底
    raf = requestAnimationFrame(() => { raf = requestAnimationFrame(start); });
    late = setTimeout(start, 90);
    introTimer = setTimeout(() => { intro.value = false; }, 2600);
    // 打开的时候已经到了昨天全天：圆环走完以后迸一次
    if (fx.value.beat) bloom(2100);
});
onBeforeUnmount(() => {
    document.documentElement.classList.remove('scr-on', 'scr-light', 'scr-dark');
    document.removeEventListener('fullscreenchange', onFull);
    document.removeEventListener('keydown', onKey);
    window.removeEventListener('ota:live', onLive);
    if (countAnim) countAnim.cancel();
    clearInterval(tickTimer);
    clearTimeout(bloomTimer);
    clearTimeout(surgeTimer);
    clearTimeout(late);
    clearTimeout(introTimer);
    cancelAnimationFrame(raf);
});
</script>
