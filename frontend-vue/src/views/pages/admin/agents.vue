<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'agents'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'代理管理'"/>
    <template v-for="L_pending in [$h.elvis($h.c(m.requestCounts, 'get', ['PENDING']), 0)]"><main class="content ax ax-list">

        <div class="page-head">
            <div class="page-title"><div>
                <h1>代理管理</h1>
                <p>{{ $h.s($h.cat($h.lists.size(m.rows), ' 个代理，', m.enabledCount, ' 个启用', ($h.t($h.gt(L_pending, 0)) ? $h.cat('，', L_pending, ' 条申请待审核') : ''))) }}</p>
            </div></div>
            <div class="page-actions">
                <a class="btn btn-ghost" :href.attr="$h.ak('/admin/agents/active')" title="代理激活的设备每天有多少在用、新激活的留住了多少"><svg class="i"><use href="#i-activity"></use></svg>日活与留存</a>
                <a :class="['btn btn-ghost', $h.av(($h.t($h.lists.isEmpty(m.rows)) ? 'disabled' : null))]" :href.attr="$h.ak('/admin/agents/export')" title="导出全部代理和他们的激活数"><svg class="i"><use href="#i-download"></use></svg>导出 CSV</a>
                <form method="post" :action.attr="$h.ak('/admin/agents/refresh-all')" data-confirm="更新全部手动代理的数据？" :data-confirm-body.attr="$h.av($h.cat(m.manualCount, ' 个「手动更新」代理将看到截至现在的数据。'))" data-confirm-ok="全部更新" v-if="$h.t($h.gt(m.manualCount, 0))">
                    <button class="btn btn-secondary" type="submit"><svg class="i"><use href="#i-refresh"></use></svg>更新全部手动代理</button>
                </form>
                <button class="btn" type="button" data-agent-new data-hash-click="new"><svg class="i"><use href="#i-plus"></use></svg>新建代理</button>
            </div>
        </div>

        <nav class="tabs" data-tabs>
            <a href="#agents" data-tab="agents">代理 <span class="count">{{ $h.s($h.lists.size(m.rows)) }}</span></a>
            <a href="#requests" data-tab="requests">口令申请 <span :class="['count', $h.av(($h.t($h.gt(L_pending, 0)) ? 'warn' : null))]" v-if="$h.t($h.gt(L_pending, 0))">{{ $h.s($h.cat(L_pending, ' 待审核')) }}</span></a>
            <a href="#notices" data-tab="notices">公告 <span class="count" v-if="!$h.t($h.lists.isEmpty(m.notices))">{{ $h.s($h.lists.size(m.notices)) }}</span></a>
        </nav>

        <section data-tab-panel="agents">
            <template v-for="L_sum in [m.sums]"><section class="kpis k4" v-if="!$h.t($h.lists.isEmpty(m.rows))">
                <div class="kpi lead">
                    <div class="kpi-k"><span class="pulse"></span>今日有效激活</div>
                    <div class="kpi-v">{{ $h.s($h.numbers.formatInteger($h.z(L_sum, 'today'), 1, 'COMMA')) }}</div>
                    <div class="kpi-f"><span :class="['chg', $h.av($h.z($h.z(L_sum, 'todayDelta'), 'dir'))]" v-if="$h.t(!$h.eq($h.z(L_sum, 'todayDelta'), null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z($h.z(L_sum, 'todayDelta'), 'dir')))"></use></svg>{{ $h.s($h.z($h.z(L_sum, 'todayDelta'), 'text')) }}</span><span>{{ $h.s($h.cat('昨天 ', $h.numbers.formatInteger($h.z(L_sum, 'yesterday'), 1, 'COMMA'))) }}</span></div>
                </div>
                <div class="kpi">
                    <div class="kpi-k">近 7 天</div>
                    <div class="kpi-v">{{ $h.s($h.numbers.formatInteger($h.z(L_sum, 'week'), 1, 'COMMA')) }}</div>
                    <div class="kpi-f"><span :class="['chg', $h.av($h.z($h.z(L_sum, 'weekDelta'), 'dir'))]" v-if="$h.t(!$h.eq($h.z(L_sum, 'weekDelta'), null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z($h.z(L_sum, 'weekDelta'), 'dir')))"></use></svg>{{ $h.s($h.z($h.z(L_sum, 'weekDelta'), 'text')) }}</span><span>{{ $h.s($h.cat('近 30 天 ', $h.numbers.formatInteger($h.z(L_sum, 'month'), 1, 'COMMA'), ' · 累计 ', $h.numbers.formatInteger($h.z(L_sum, 'total'), 1, 'COMMA'))) }}</span></div>
                </div>
                <div class="kpi">
                    <div class="kpi-k">代理</div>
                    <div class="kpi-v">{{ $h.s($h.lists.size(m.rows)) }}<small>个</small></div>
                    <div class="kpi-f">{{ $h.s($h.cat('启用 ', m.enabledCount, ' · 今天有激活 ', $h.z(L_sum, 'active'))) }}</div>
                </div>
                <a class="kpi" href="#requests">
                    <div class="kpi-k">待审核申请<svg class="i"><use href="#i-arrow"></use></svg></div>
                    <div :class="['kpi-v', $h.av(($h.t($h.gt(L_pending, 0)) ? 'warn' : null))]">{{ $h.s(L_pending) }}<small>条</small></div>
                    <div class="kpi-f">{{ $h.s($h.cat('代理手里 ', $h.z(L_sum, 'codes'), ' 个口令，还能用 ', $h.z(L_sum, 'usable'), ' 个')) }}</div>
                </a>
            </section></template>

            <section class="card agent-board">
                <div class="empty" v-if="$h.t($h.lists.isEmpty(m.rows))">
                    <div class="empty-icon"><svg class="i"><use href="#i-users"></use></svg></div>
                    <b>还没有代理</b>新建代理账号，口令可以分配给 TA，也可以让 TA 自己申请
                    <div><button class="btn" type="button" data-agent-new><svg class="i"><use href="#i-plus"></use></svg>新建代理</button></div>
                </div>

                <div class="agl-tools" v-if="!$h.t($h.lists.isEmpty(m.rows))">
                    <div class="seg seg-sm" data-agent-filter>
                        <button type="button" class="on" data-f="all">全部 <b>{{ $h.s($h.lists.size(m.rows)) }}</b></button>
                        <button type="button" data-f="on">启用</button>
                        <button type="button" data-f="off">停用</button>
                        <button type="button" data-f="active">今天有激活</button>
                    </div>
                    <label class="search"><svg class="i i-sm"><use href="#i-search"></use></svg><input class="input" type="search" placeholder="搜索代理、账号或备注" aria-label="搜索代理" data-agent-search></label>
                    <span class="grow"></span>
                    <label class="sort-pick"><svg class="i i-sm"><use href="#i-updown"></use></svg><select class="input input-sm" data-agent-sort aria-label="排序">
                        <option value="today">今日激活最多</option>
                        <option value="week">近 7 天最多</option>
                        <option value="total">累计最多</option>
                        <option value="d7">7 日留存最高</option>
                        <option value="login">最近登录</option>
                        <option value="created">最新创建</option>
                        <option value="name">按名称</option>
                    </select></label>
                </div>

                <form class="bulkbar sel-bar" method="post" :action.attr="$h.ak('/admin/agents/bulk')" id="agent-bulk" data-bulkbar hidden v-if="!$h.t($h.lists.isEmpty(m.rows))">
                    <input type="hidden" name="action" :value.attr="''">
                    <span class="sel-count"><span class="sel-ico"><svg class="i"><use href="#i-tick"></use></svg></span><span>已选 <b data-check-count>0</b> 个代理</span></span>
                    <span class="sel-acts">
                    <button class="btn btn-secondary btn-sm" type="button" data-agent-bulk="request"><svg class="i i-sm"><use href="#i-key"></use></svg>申请口令设置</button>
                    <button class="btn btn-secondary btn-sm" type="button" data-agent-bulk="enable">启用</button>
                    <button class="btn btn-secondary btn-sm" type="button" data-agent-bulk="disable">停用</button>
                    <button class="btn btn-secondary btn-sm danger-text" type="button" data-agent-bulk="delete"><svg class="i i-sm"><use href="#i-trash"></use></svg>删除</button>
                    </span>
                    <button class="btn btn-ghost btn-sm bulk-done" type="button" data-agent-select-done>取消</button>
                </form>

                <div class="agl agl-admin" data-guide="agents-table" v-if="!$h.t($h.lists.isEmpty(m.rows))">
                    <div class="agl-head">
                        <label class="check-all" title="全选"><input type="checkbox" data-check-all aria-label="全选代理"></label>
                        <span>代理</span><span class="r">今日</span><span class="r">近 7 天</span><span class="h-spark">近 14 天</span><span class="r">累计</span><span class="r" title="代理在代理后台看到的累计，扣量和数据更新方式算过以后的">代理端看到</span><span class="r" title="近 30 天新激活、满 7 天的设备，第 7 天还打开的占比；按真实设备算，不扣量">7 日留存</span><span class="r">可用口令</span><span class="h-login">最近登录</span><span></span>
                    </div>
                    <div class="agl-body" id="agent-cards">
                    <template v-for="[L_r, L_rStat] in $h.each(m.rows)"><template v-for="L_a in [$h.p(L_r, 'agent')]"><template v-for="L_manual in [$h.eq($h.p(L_a, 'dataMode'), 'MANUAL')]"><article :class="['agl-row', $h.av(($h.t($h.p(L_a, 'enabled')) ? null : 'off'))]" data-agent-card :data-hash-target.attr="$h.av($h.add('agent-', $h.p(L_a, 'id')))" :data-search.attr="$h.av($h.strings.toLowerCase($h.cat($h.p(L_a, 'name'), ' ', $h.p(L_a, 'username'), ' ', $h.elvis($h.p(L_a, 'remark'), ''))))" :data-on.attr="$h.av($h.p(L_a, 'enabled'))" :data-today.attr="$h.av($h.p(L_r, 'today'))" :data-week.attr="$h.av($h.p(L_r, 'week'))" :data-total.attr="$h.av($h.p(L_r, 'total'))" :data-d7.attr="$h.av($h.elvis($h.p(L_r, 'd7'), -1))" :data-login.attr="$h.av($h.ui.full($h.p(L_a, 'lastLoginAt')))" :data-created.attr="$h.av($h.ui.full($h.p(L_a, 'createdAt')))" :data-name.attr="$h.av($h.p(L_a, 'name'))">
                            <label class="ac-check"><input type="checkbox" name="ids" :value.attr="$h.ak($h.p(L_a, 'id'))" form="agent-bulk" data-check :aria-label.attr="$h.av($h.add('选择代理 ', $h.p(L_a, 'name')))"></label>
                            <div class="agl-who">
                                <span class="who-ava" :data-hue.attr="$h.av($h.mod($h.p(L_a, 'id'), 6))">{{ $h.s(($h.t($h.strings.isEmpty($h.p(L_a, 'name'))) ? '?' : $h.strings.substring($h.p(L_a, 'name'), 0, 1))) }}</span>
                                <a class="ac-name" :href.attr="$h.ak($h.add('/admin/agents/', $h.p(L_a, 'id')))">
                                    <b>{{ $h.s($h.p(L_a, 'name')) }}<em class="mini-tag" v-if="!$h.t($h.p(L_a, 'enabled'))">停用</em></b>
                                    <small><span class="mono">{{ $h.s($h.p(L_a, 'username')) }}</span><span class="sep warn" :title.attr="$h.av($h.add('扣量：', $h.p(L_r, 'deductionDesc')))" v-if="!$h.t($h.eq($h.p(L_r, 'deductionDesc'), '不扣量'))">扣量</span><span class="sep" v-if="!$h.t($h.eq($h.p(L_a, 'dataMode'), 'REALTIME'))">{{ $h.s($h.ui.dataMode($h.p(L_a, 'dataMode'))) }}</span><span class="sep" v-if="$h.t($h.p(L_a, 'canManageSubs'))">{{ $h.s($h.cat('二级代理 ', $h.p(L_r, 'subCount'))) }}</span><span class="sep" title="二级代理权限已关，TA 的二级代理暂时不能登录" v-if="$h.t(($h.t(!$h.t($h.p(L_a, 'canManageSubs'))) &amp;&amp; $h.t($h.gt($h.p(L_r, 'subCount'), 0))))">{{ $h.s($h.cat('二级代理 ', $h.p(L_r, 'subCount'), ' · 已停')) }}</span><span class="sep" v-if="$h.t($h.eq($h.p(L_a, 'requestMode'), 'REVIEW'))">申请需审核</span><span class="sep" v-if="$h.t($h.eq($h.p(L_a, 'requestMode'), 'AUTO'))">申请自动通过</span><span class="sep" translate="no" :title.attr="$h.av($h.p(L_a, 'remark'))" v-if="!$h.t($h.strings.isEmpty($h.p(L_a, 'remark')))">{{ $h.s($h.p(L_a, 'remark')) }}</span></small>
                                </a>
                            </div>
                            <div class="agl-nums">
                                <div :class="['agl-n', 'r', $h.av(($h.t($h.eq($h.p(L_r, 'today'), 0)) ? 'zero' : null))]" data-k="今日">{{ $h.s($h.numbers.formatInteger($h.p(L_r, 'today'), 1, 'COMMA')) }}</div>
                                <div :class="['agl-n', 'r', $h.av(($h.t($h.eq($h.p(L_r, 'week'), 0)) ? 'zero' : null))]" data-k="近 7 天">{{ $h.s($h.numbers.formatInteger($h.p(L_r, 'week'), 1, 'COMMA')) }}<em :class="['chg', $h.av($h.p(L_r, 'trend'))]" title="比前 7 天" v-if="$h.t(!$h.strings.isEmpty($h.p(L_r, 'trend')))"><svg class="i" v-if="$h.t(($h.t($h.eq($h.p(L_r, 'trend'), 'up')) || $h.t($h.eq($h.p(L_r, 'trend'), 'down'))))"><use :href.attr="$h.av($h.cat('#i-trend-', $h.p(L_r, 'trend')))"></use></svg>{{ $h.s($h.p(L_r, 'trendText')) }}</em></div>
                                <div class="agl-spark flat" title="近 14 天没有有效激活" v-if="$h.t($h.p(L_r, 'sparkEmpty'))"></div>
                                <div class="agl-spark" title="近 14 天每天的有效激活" v-if="!$h.t($h.p(L_r, 'sparkEmpty'))"><template v-for="[L_px, L_pxStat] in $h.each($h.p(L_r, 'spark'))"><i :class="$h.av(($h.t(L_pxStat.last) ? 'now' : ($h.t($h.gt(L_px, 2)) ? 'on' : null)))" :style="$h.av($h.cat('height:', L_px, 'px'))"></i></template></div>
                                <div :class="['agl-n', 'r', $h.av(($h.t($h.eq($h.p(L_r, 'total'), 0)) ? 'zero' : null))]" data-k="累计">{{ $h.s($h.numbers.formatInteger($h.p(L_r, 'total'), 1, 'COMMA')) }}</div>
                                <div :class="['agl-n', 'r', $h.av(($h.t($h.eq($h.p(L_r, 'visible'), 0)) ? 'zero' : null))]" data-k="代理端看到" :title.attr="$h.av(($h.t($h.p(L_a, 'canViewRecords')) ? '能看到每条激活记录（设备号脱敏）' : '只能看到数量和趋势'))">{{ $h.s($h.numbers.formatInteger($h.p(L_r, 'visible'), 1, 'COMMA')) }}<span class="sub2" :title.attr="$h.av($h.ui.full($h.p(L_r, 'cutoff')))" v-if="$h.t(!$h.eq($h.p(L_r, 'cutoff'), null))">{{ $h.s($h.add('截至 ', $h.ui.dt($h.p(L_r, 'cutoff')))) }}</span><span class="sub2" v-if="$h.t(($h.t($h.eq($h.p(L_r, 'cutoff'), null)) &amp;&amp; $h.t(!$h.eq($h.p(L_r, 'visible'), $h.p(L_r, 'total')))))">扣量后</span></div>
                                <div :class="['agl-n', 'r', $h.av(($h.t($h.eq($h.p(L_r, 'd7'), null)) ? 'zero' : null))]" data-k="7 日留存">{{ $h.s(($h.t(!$h.eq($h.p(L_r, 'd7'), null)) ? $h.add($h.p(L_r, 'd7'), '%') : '—')) }}</div>
                                <div :class="['agl-n', 'r', $h.av(($h.t($h.eq($h.p(L_r, 'usable'), 0)) ? 'zero' : null))]" data-k="可用口令"><a :href.attr="$h.ak($h.link('/admin/codes', [['agent', $h.p(L_a, 'id')]]))">{{ $h.s($h.p(L_r, 'usable')) }}<small>{{ $h.s($h.add('/', $h.p(L_r, 'codeCount'))) }}</small></a></div>
                                <div class="agl-login" :title.attr="$h.av($h.ui.full($h.p(L_a, 'lastLoginAt')))">{{ $h.s(($h.t(!$h.eq($h.p(L_a, 'lastLoginAt'), null)) ? $h.cat($h.ui.ago($h.p(L_a, 'lastLoginAt')), ' 登录') : '从未登录')) }}</div>
                            </div>
                            <div class="ac-menu" data-pop>
                                <button class="icon-btn" type="button" data-pop-toggle aria-label="更多操作" title="更多操作"><svg class="i"><use href="#i-more"></use></svg></button>
                                <div class="menu menu-float" data-pop-menu hidden>
                                    <a class="menu-item" :href.attr="$h.ak($h.add('/admin/agents/', $h.p(L_a, 'id')))"><svg class="i i-sm"><use href="#i-chart"></use></svg>详情和数据</a>
                                    <a class="menu-item" :href.attr="$h.ak($h.link('/admin/agents/{id}/active', [['id', $h.p(L_a, 'id')]]))"><svg class="i i-sm"><use href="#i-activity"></use></svg>日活与留存</a>
                                    <a class="menu-item" :href.attr="$h.ak($h.link('/admin/agents/{id}/preview', [['id', $h.p(L_a, 'id')]]))"><svg class="i i-sm"><use href="#i-eye"></use></svg>以代理视角查看</a>
                                    <button class="menu-item" type="button" :data-copy.attr="$h.av($h.cat('登录地址：{origin}/login\n账号：', $h.p(L_a, 'username')))" data-copy-label="已复制登录信息"><svg class="i i-sm"><use href="#i-copy"></use></svg>复制登录信息</button>
                                    <a class="menu-item" :href.attr="$h.ak($h.link('/admin/agents/{id}/export', [['id', $h.p(L_a, 'id')]]))"><svg class="i i-sm"><use href="#i-download"></use></svg>导出激活记录</a>
                                    <button class="menu-item" type="button" data-agent-edit :data-id.attr="$h.av($h.p(L_a, 'id'))" :data-name.attr="$h.av($h.p(L_a, 'name'))" :data-username.attr="$h.av($h.p(L_a, 'username'))" :data-remark.attr="$h.av($h.p(L_a, 'remark'))" :data-view.attr="$h.av($h.p(L_a, 'canViewRecords'))" :data-mode.attr="$h.av($h.p(L_a, 'dataMode'))" :data-tiers.attr="$h.av($h.elvis($h.p(L_a, 'deductionTiers'), '[]'))" :data-request.attr="$h.av($h.p(L_a, 'requestMode'))" :data-limit.attr="$h.av($h.p(L_a, 'dailyRequestLimit'))" :data-subs.attr="$h.av($h.p(L_a, 'canManageSubs'))" :data-sub-limit.attr="$h.av($h.p(L_a, 'subAgentLimit'))"><svg class="i i-sm"><use href="#i-edit"></use></svg>编辑</button>
                                    <a class="menu-item" :href.attr="$h.ak($h.add($h.link('/admin/codes', [['agent', $h.p(L_a, 'id')]]), '#new'))"><svg class="i i-sm"><use href="#i-key"></use></svg>分配口令</a>
                                    <form method="post" :action.attr="$h.ak($h.link('/admin/agents/{id}/refresh', [['id', $h.p(L_a, 'id')]]))" v-if="$h.t(L_manual)">
                                        <button class="menu-item" type="submit"><svg class="i i-sm"><use href="#i-refresh"></use></svg>把数据更新到现在</button>
                                    </form>
                                    <form method="post" :action.attr="$h.ak($h.link('/admin/agents/{id}/toggle', [['id', $h.p(L_a, 'id')]]))" :data-confirm.attr="$h.av(($h.t($h.p(L_a, 'enabled')) ? $h.cat('停用代理 ', $h.p(L_a, 'name'), '？') : null))" data-confirm-body="停用后 TA 不能登录，已登录的会马上退出。TA 名下的口令照常能用。" data-confirm-ok="停用">
                                        <button class="menu-item" type="submit"><svg class="i i-sm"><use :href.attr="$h.av(($h.t($h.p(L_a, 'enabled')) ? '#i-lock' : '#i-check'))"></use></svg>{{ $h.s(($h.t($h.p(L_a, 'enabled')) ? '停用' : '启用')) }}</button>
                                    </form>
                                    <div class="menu-sep"></div>
                                    <button class="menu-item" type="button" data-agent-select :data-id.attr="$h.av($h.p(L_a, 'id'))"><svg class="i i-sm"><use href="#i-tick"></use></svg>选择</button>
                                    <button class="menu-item danger" type="button" data-agent-delete :data-id.attr="$h.av($h.p(L_a, 'id'))" :data-name.attr="$h.av($h.p(L_a, 'name'))" :data-codes.attr="$h.av($h.p(L_r, 'codeCount'))"><svg class="i i-sm"><use href="#i-trash"></use></svg>删除</button>
                                </div>
                            </div>
                    </article></template></template></template>
                    </div>
                </div>
                <div class="list-none" id="agent-cards-none" hidden>没有符合条件的代理</div>
                <button class="collapse-toggle" type="button" data-agent-more hidden></button>
            </section>
        </section>

        <section data-tab-panel="notices" hidden>
            <section class="card">
                <div class="card-head">
                    <h2>公告</h2>
                    <span class="muted small">发给代理的通知，可以只发一级代理，也可以连二级代理一起发；这里能看到多少人看过</span>
                    <button class="btn btn-sm tail" type="button" data-notice-new v-if="!$h.t($h.lists.isEmpty(m.notices))"><svg class="i i-sm"><use href="#i-plus"></use></svg>发布公告</button>
                </div>
                <div class="empty" v-if="$h.t($h.lists.isEmpty(m.notices))">
                    <div class="empty-icon"><svg class="i"><use href="#i-megaphone"></use></svg></div>
                    <b>还没有公告</b>活动通知、规则调整、维护时间都可以发在这里，重要的会在代理后台顶部提醒
                    <div><button class="btn" type="button" data-notice-new><svg class="i"><use href="#i-plus"></use></svg>发布公告</button></div>
                </div>
                <ul class="notice-list" v-if="!$h.t($h.lists.isEmpty(m.notices))"><template v-for="[L_v, L_vStat] in $h.each(m.notices)"><template v-for="L_n in [$h.z(L_v, 'notice')]"><li :class="['notice-item', $h.av(($h.t($h.p(L_n, 'pinned')) ? 'pinned' : null)), $h.av(($h.t($h.p(L_n, 'important')) ? 'important' : null))]">
                    <div class="ni-head">
                        <b>{{ $h.s($h.p(L_n, 'title')) }}</b>
                        <span class="mini-tag" v-if="$h.t($h.p(L_n, 'pinned'))"><svg class="i i-xs"><use href="#i-pin"></use></svg>置顶</span>
                        <span class="mini-tag warn" v-if="$h.t($h.p(L_n, 'important'))">重要</span>
                        <span :class="['mini-tag', 'aud-tag', $h.av(($h.t($h.p(L_n, 'forSubs')) ? 'all' : null))]" :title.attr="$h.av(($h.t($h.p(L_n, 'forSubs')) ? '一级代理和二级代理都能看到' : '二级代理看不到'))">{{ $h.s(($h.t($h.p(L_n, 'forSubs')) ? '含二级代理' : '只发一级代理')) }}</span>
                    </div>
                    <p class="ni-body">{{ $h.s($h.p(L_n, 'body')) }}</p>
                    <div class="ni-foot">
                        <span class="ni-meta" :title.attr="$h.av($h.ui.full($h.p(L_n, 'createdAt')))">{{ $h.s($h.cat($h.elvis($h.c(m.operatorNames, 'get', [$h.p(L_n, 'createdBy')]), $h.p(L_n, 'createdBy')), ' · ', $h.ui.ago($h.p(L_n, 'createdAt')), ($h.t($h.eq($h.p(L_n, 'updatedAt'), null)) ? '' : ' · 修改过'))) }}</span>
                        <span class="ni-read" :title.attr="$h.av($h.cat($h.z(L_v, 'read'), ' / ', $h.z(L_v, 'audience'), ' 个启用中的代理看过'))"><span class="read-bar"><i :style="$h.av($h.cat('width:', $h.ui.pctOf($h.z(L_v, 'read'), $h.z(L_v, 'audience')), '%'))"></i></span>{{ $h.s($h.cat($h.z(L_v, 'read'), ' / ', $h.z(L_v, 'audience'), ' 已读')) }}</span>
                        <div class="btn-row">
                            <form method="post" :action.attr="$h.ak($h.link('/admin/agents/notices/{id}/pin', [['id', $h.p(L_n, 'id')]]))"><button class="btn btn-ghost btn-sm" type="submit">{{ $h.s(($h.t($h.p(L_n, 'pinned')) ? '取消置顶' : '置顶')) }}</button></form>
                            <button class="icon-btn" type="button" title="编辑公告" aria-label="编辑公告" data-notice-edit :data-id.attr="$h.av($h.p(L_n, 'id'))" :data-title.attr="$h.av($h.p(L_n, 'title'))" :data-body.attr="$h.av($h.p(L_n, 'body'))" :data-pinned.attr="$h.av($h.p(L_n, 'pinned'))" :data-level.attr="$h.av($h.p(L_n, 'level'))" :data-audience.attr="$h.av($h.p(L_n, 'audience'))"><svg class="i"><use href="#i-edit"></use></svg></button>
                            <form method="post" :action.attr="$h.ak($h.link('/admin/agents/notices/{id}/delete', [['id', $h.p(L_n, 'id')]]))" :data-confirm.attr="$h.av($h.cat('删除公告「', $h.p(L_n, 'title'), '」？'))" data-confirm-body="代理后台里也不再显示。" data-confirm-ok="删除" data-confirm-danger><button class="icon-btn" type="submit" title="删除公告" aria-label="删除公告"><svg class="i"><use href="#i-trash"></use></svg></button></form>
                        </div>
                    </div>
                </li></template></template></ul>
            </section>
        </section>

        <section data-tab-panel="requests" hidden>
            <section class="card">
                <div class="toolbar">
                    <div class="seg">
                        <a :href.attr="$h.ak('/admin/agents#requests')" :class="$h.av(($h.t($h.eq(m.reqFilter, 'PENDING')) ? 'on' : null))">{{ $h.s($h.cat('待审核 ', L_pending)) }}</a>
                        <a :href.attr="$h.ak('/admin/agents?req=APPROVED#requests')" :class="$h.av(($h.t($h.eq(m.reqFilter, 'APPROVED')) ? 'on' : null))">{{ $h.s($h.cat('已通过 ', $h.c(m.requestCounts, 'get', ['APPROVED']))) }}</a>
                        <a :href.attr="$h.ak('/admin/agents?req=REJECTED#requests')" :class="$h.av(($h.t($h.eq(m.reqFilter, 'REJECTED')) ? 'on' : null))">{{ $h.s($h.cat('已拒绝 ', $h.c(m.requestCounts, 'get', ['REJECTED']))) }}</a>
                        <a :href.attr="$h.ak('/admin/agents?req=ALL#requests')" :class="$h.av(($h.t($h.eq(m.reqFilter, 'ALL')) ? 'on' : null))">{{ $h.s($h.cat('全部 ', $h.c(m.requestCounts, 'get', ['ALL']))) }}</a>
                    </div>
                    <span class="tail muted small">{{ $h.s(($h.t($h.eq(m.reqFilter, 'PENDING')) ? '先申请的排在前面' : '最近 300 条')) }}</span>
                </div>

                <form class="bulkbar" method="post" :action.attr="$h.ak('/admin/agents/requests/bulk')" id="req-bulk" data-bulkbar="req" hidden v-if="$h.t($h.eq(m.reqFilter, 'PENDING'))">
                    <input type="hidden" name="action" :value.attr="''">
                    <input type="hidden" name="back" :value.attr="$h.ak(m.reqFilter)">
                    <input type="hidden" name="reason" :value.attr="''">
                    <span>已选 <b data-check-count="req">0</b> 条</span>
                    <button class="btn btn-sm" type="button" data-req-bulk="approve"><svg class="i i-sm"><use href="#i-tick"></use></svg>同意</button>
                    <button class="btn btn-secondary btn-sm" type="button" data-req-bulk="reject"><svg class="i i-sm"><use href="#i-x"></use></svg>拒绝</button>
                    <span class="muted small">同意时按每条申请的个数和次数生成口令</span>
                </form>

                <div class="empty" v-if="$h.t($h.lists.isEmpty(m.requests))">
                    <div class="empty-icon"><svg class="i"><use href="#i-inbox"></use></svg></div>
                    <b>{{ $h.s(($h.t($h.eq(m.reqFilter, 'PENDING')) ? '没有待审核的申请' : '没有申请记录')) }}</b>
                    <span>在代理的「编辑」里选好 App、打开申请口令，TA 就能在代理后台申请</span>
                </div>
                <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty(m.requests))">
                    <table class="compact req-table">
                        <thead>
                        <tr>
                            <th class="check-col" v-if="$h.t($h.eq(m.reqFilter, 'PENDING'))"><input type="checkbox" data-check-all="req" aria-label="全选申请"></th>
                            <th>申请</th><th>代理</th><th>要的口令</th><th>用途</th><th>申请时间</th><th>结果</th><th class="right">操作</th>
                        </tr>
                        </thead>
                        <tbody>
                        <template v-for="[L_q, L_qStat] in $h.each(m.requests)"><template v-if="L_qStat.index">
                        </template><tr>
                            <td class="check-col" v-if="$h.t($h.eq(m.reqFilter, 'PENDING'))"><input type="checkbox" name="ids" :value.attr="$h.ak($h.p(L_q, 'id'))" form="req-bulk" data-check="req" :aria-label.attr="$h.av($h.add('选择申请 #', $h.p(L_q, 'id')))"></td>
                            <td class="mono muted nowrap req-id">{{ $h.s($h.add('#', $h.p(L_q, 'id'))) }}</td>
                            <td class="nowrap req-agent"><b>{{ $h.s($h.p(L_q, 'agentName')) }}</b><span class="badge badge-line" style="margin-left:6px" v-if="!$h.t($h.p(L_q, 'agentEnabled'))">已停用</span></td>
                            <td class="req-codes">
                                <div><b>{{ $h.s(($h.t($h.p(L_q, 'custom')) ? $h.cat('自定义 ', $h.p(L_q, 'count'), ' 个') : $h.cat('随机 ', $h.p(L_q, 'count'), ' 个'))) }}</b><span class="muted small">{{ $h.s(($h.t($h.gt($h.p(L_q, 'maxUses'), 0)) ? $h.cat(' · 每个可激活 ', $h.p(L_q, 'maxUses'), ' 次') : ' · 不限次数')) }}</span></div>
                                <div class="code-peek" v-if="$h.t($h.p(L_q, 'custom'))"><template v-for="[L_it, L_itStat] in $h.each($h.p(L_q, 'items'))"><span :class="['code-chip', $h.av($h.strings.toLowerCase($h.p(L_it, 'status')))]" :title.attr="$h.av($h.p(L_it, 'reason'))" v-if="$h.t($h.lt(L_itStat.index, 6))">{{ $h.s($h.p(L_it, 'code')) }}</span></template><span class="code-chip more" v-if="$h.t($h.gt($h.lists.size($h.p(L_q, 'items')), 6))">{{ $h.s($h.add('+', $h.sub($h.lists.size($h.p(L_q, 'items')), 6))) }}</span></div>
                                <small class="warn-text" v-if="$h.t($h.gt($h.p(L_q, 'duplicates'), 0))">{{ $h.s($h.cat($h.p(L_q, 'duplicates'), ' 个重复，提交时已自动拒绝')) }}</small>
                            </td>
                            <td class="small req-note">{{ $h.s($h.elvis($h.p(L_q, 'note'), '—')) }}</td>
                            <td class="muted nowrap req-time" :title.attr="$h.av($h.ui.full($h.p(L_q, 'createdAt')))">{{ $h.s($h.ui.ago($h.p(L_q, 'createdAt'))) }}</td>
                            <td class="two-line req-result">
                                <span :class="['badge', $h.av(($h.t($h.eq($h.p(L_q, 'status'), 'APPROVED')) ? 'badge-solid' : ($h.t($h.eq($h.p(L_q, 'status'), 'PENDING')) ? 'badge-warn' : 'badge-line')))]"><span class="dot"></span><span>{{ $h.s($h.p(L_q, 'statusLabel')) }}</span></span>
                                <small v-if="$h.t($h.eq($h.p(L_q, 'status'), 'APPROVED'))">{{ $h.s($h.cat('生成 ', $h.p(L_q, 'codeCount'), ' 个', ($h.t($h.p(L_q, 'auto')) ? '' : $h.cat(' · ', $h.p(L_q, 'reviewedBy'))))) }}</small>
                                <small v-if="$h.t($h.eq($h.p(L_q, 'status'), 'REJECTED'))">{{ $h.s($h.cat($h.p(L_q, 'reviewedBy'), ($h.t($h.strings.isEmpty($h.p(L_q, 'reviewNote'))) ? '' : $h.cat('：', $h.p(L_q, 'reviewNote'))))) }}</small>
                            </td>
                            <td class="actions">
                                <div class="btn-row" v-if="$h.t($h.eq($h.p(L_q, 'status'), 'PENDING'))">
                                    <button class="btn btn-sm" type="button" data-req-approve :data-id.attr="$h.av($h.p(L_q, 'id'))" :data-agent.attr="$h.av($h.p(L_q, 'agentName'))" :data-count.attr="$h.av($h.p(L_q, 'count'))" :data-max.attr="$h.av($h.p(L_q, 'maxUses'))" :data-items.attr="$h.av($h.p(L_q, 'itemsJson'))" :disabled.attr="!$h.t($h.p(L_q, 'canApprove')) ? 'disabled' : null" :title.attr="$h.av(($h.t($h.p(L_q, 'canApprove')) ? null : '你没有「口令与激活」权限'))">同意</button>
                                    <button class="btn btn-secondary btn-sm" type="button" data-req-reject :data-id.attr="$h.av($h.p(L_q, 'id'))" :data-agent.attr="$h.av($h.p(L_q, 'agentName'))">拒绝</button>
                                </div>
                                <span class="muted small" v-if="!$h.t($h.eq($h.p(L_q, 'status'), 'PENDING'))">{{ $h.s(($h.t(!$h.eq($h.p(L_q, 'reviewedAt'), null)) ? $h.ui.dt($h.p(L_q, 'reviewedAt')) : '')) }}</span>
                            </td>
                        </tr></template>
                        </tbody>
                    </table>
                </div>
            </section>
            <div class="note" style="margin-top:16px">
                <svg class="i"><use href="#i-info"></use></svg>
                <span>同意后口令直接归属代理，口令管理里备注「代理申请 #编号」。能申请哪些 App、要不要审核、每天几个，在代理的「编辑」里设置，勾选多个代理可以一起改。</span>
            </div>
        </section>

        <dialog id="agent-dialog" class="wide">
            <form method="post" id="agent-form" :action.attr="$h.ak('/admin/agents/create')">
                <div class="dlg-head">
                    <h3 data-role="title">新建代理</h3>
                    <p>代理用这个账号登录，只看得到自己的口令和数据。</p>
                </div>
                <div class="dlg-body">
                    <div class="row-2">
                        <div class="field">
                            <label for="agent-name">代理名称</label>
                            <input class="input" id="agent-name" name="name" placeholder="例如 张三" maxlength="64" required>
                        </div>
                        <div class="field">
                            <label for="agent-username">登录账号</label>
                            <input class="input mono" id="agent-username" name="username" placeholder="例如 zhangsan" maxlength="32" pattern="[A-Za-z0-9_.\-]{3,32}" title="3–32 位字母、数字或 _ . -" autocomplete="off" required>
                        </div>
                    </div>
                    <div class="field">
                        <label for="agent-password">登录密码</label>
                        <input class="input" id="agent-password" name="password" type="text" minlength="6" maxlength="64" autocomplete="new-password" placeholder="至少 6 位">
                        <span class="hint" data-role="pwd-hint">把密码告诉代理，TA 用它登录</span>
                    </div>
                    <div class="field">
                        <label>申请口令</label>
                        <div class="opt-grid three">
                            <label class="opt"><input type="radio" name="requestMode" :value.attr="'OFF'" :checked.attr="''">
                                <span><b>不能申请</b><small>只能用管理员分配的口令</small></span></label>
                            <label class="opt"><input type="radio" name="requestMode" :value.attr="'REVIEW'">
                                <span><b>需要审核</b><small>在代理后台提交申请，你同意后生成口令</small></span></label>
                            <label class="opt"><input type="radio" name="requestMode" :value.attr="'AUTO'">
                                <span><b>自动通过</b><small>提交后马上生成口令，受每天上限约束</small></span></label>
                        </div>
                    </div>
                    <div class="field" data-role="limit-field">
                        <label for="agent-limit">每天最多申请</label>
                        <div class="input-unit"><input class="input num" id="agent-limit" name="dailyLimit" type="number" min="0" max="100000" placeholder="不限"><em>个口令</em></div>
                        <span class="hint">待审核和已通过的都算，留空或 0 表示不限</span>
                    </div>
                    <div class="field">
                        <label>二级代理</label>
                        <div class="opt-grid two">
                            <label class="opt"><input type="radio" name="subAgents" :value.attr="'false'" :checked.attr="''">
                                <span><b>不能开</b><small>代理后台只有 TA 自己用</small></span></label>
                            <label class="opt"><input type="radio" name="subAgents" :value.attr="'true'">
                                <span><b>可以开二级代理</b><small>TA 自己开下级账号、分口令，扣量和数据更新跟你给代理设的一样</small></span></label>
                        </div>
                    </div>
                    <div class="field" data-role="sub-limit-field" hidden>
                        <label for="agent-sub-limit">最多开</label>
                        <div class="input-unit"><input class="input num" id="agent-sub-limit" name="subLimit" type="number" min="0" max="1000" placeholder="不限"><em>个二级代理</em></div>
                        <span class="hint">留空或 0 表示不限。二级代理的口令从 TA 手里分，激活仍算在 TA 名下</span>
                    </div>
                    <div class="field">
                        <label>设备记录</label>
                        <div class="opt-grid two">
                            <label class="opt"><input type="radio" name="canViewRecords" :value.attr="'true'">
                                <span><b>可查看设备记录</b><small>能看到每条激活的时间、口令，以及脱敏后的设备号和 IP</small></span></label>
                            <label class="opt"><input type="radio" name="canViewRecords" :value.attr="'false'" :checked.attr="''">
                                <span><b>仅查看数量</b><small>只能看到激活数量和趋势，看不到设备记录</small></span></label>
                        </div>
                    </div>
                    <div class="field">
                        <label>数据更新</label>
                        <div class="opt-grid three">
                            <label class="opt"><input type="radio" name="dataMode" :value.attr="'REALTIME'" :checked.attr="''">
                                <span><b>实时</b><small>代理随时看到最新数据</small></span></label>
                            <label class="opt"><input type="radio" name="dataMode" :value.attr="'DAILY'">
                                <span><b>每日 0 点</b><small>只统计到昨天 24 点，次日 0 点自动更新</small></span></label>
                            <label class="opt"><input type="radio" name="dataMode" :value.attr="'MANUAL'">
                                <span><b>手动更新</b><small>你点「更新数据」后代理才看到新数据</small></span></label>
                        </div>
                    </div>
                    <div class="field">
                        <label>扣量档位</label>
                        <div class="tiers">
                            <div class="tier-head"><span>#</span><span>当日有效激活 从</span><span>到（含）</span><span>扣量比例</span><span></span></div>
                            <div id="tier-rows"></div>
                            <div class="tiers-empty" id="tier-empty">未设置档位，代理看到的是真实数据</div>
                        </div>
                        <div class="tier-foot">
                            <span class="hint">当天总数落在哪档就按那档的比例扣，「到」不填表示不限</span>
                            <button class="btn btn-secondary btn-sm" type="button" id="tier-add"><svg class="i i-sm"><use href="#i-plus"></use></svg>添加档位</button>
                        </div>
                        <div class="tier-example" id="tier-example" hidden></div>
                    </div>
                    <div class="field">
                        <label for="agent-remark">备注</label>
                        <input class="input" id="agent-remark" name="remark" placeholder="仅后台可见，可不填" maxlength="200">
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">保存</button>
                </div>
            </form>
        </dialog>

        <dialog id="agent-request-dialog">
            <form method="post" :action.attr="$h.ak('/admin/agents/bulk')" id="agent-request-form">
                <input type="hidden" name="action" :value.attr="'request'">
                <div class="dlg-head">
                    <h3>申请口令设置</h3>
                    <p data-role="sub">给勾选的代理设置</p>
                </div>
                <div class="dlg-body">
                    <div class="field">
                        <div class="opt-grid three">
                            <label class="opt"><input type="radio" name="requestMode" :value.attr="'OFF'">
                                <span><b>不能申请</b><small>只能用分配的口令</small></span></label>
                            <label class="opt"><input type="radio" name="requestMode" :value.attr="'REVIEW'" :checked.attr="''">
                                <span><b>需要审核</b><small>你同意后生成口令</small></span></label>
                            <label class="opt"><input type="radio" name="requestMode" :value.attr="'AUTO'">
                                <span><b>自动通过</b><small>提交后马上生成</small></span></label>
                        </div>
                    </div>
                    <div class="field">
                        <label for="bulk-limit">每天最多申请</label>
                        <div class="input-unit"><input class="input num" id="bulk-limit" name="dailyLimit" type="number" min="0" max="100000" :value.attr="'100'" placeholder="不限"><em>个口令</em></div>
                        <span class="hint">留空或 0 表示不限</span>
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">确定</button>
                </div>
            </form>
        </dialog>

        <dialog id="agent-delete-dialog">
            <form method="post" :action.attr="$h.ak('/admin/agents/bulk')" id="agent-delete-form">
                <div class="dlg-head">
                    <h3 data-role="title">删除代理</h3>
                    <p data-role="sub">账号会被删除，无法再登录。</p>
                </div>
                <div class="dlg-body">
                    <div class="opt-grid">
                        <label class="opt"><input type="radio" name="purge" :value.attr="'false'" :checked.attr="''">
                            <span><b>只删账号</b><small data-role="keep">口令改为未分配，照常能用，激活记录保留</small></span></label>
                        <label class="opt danger"><input type="radio" name="purge" :value.attr="'true'">
                            <span><b>连口令和激活记录一起删</b><small data-role="purge">TA 的口令、激活记录和申请全部删除，不能恢复</small></span></label>
                    </div>
                    <div class="field" data-role="pwd" style="margin-top:14px" hidden>
                        <label for="agent-delete-pwd">你的登录密码</label>
                        <input class="input" id="agent-delete-pwd" name="password" type="password" autocomplete="current-password" placeholder="确认是你本人操作">
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn btn-danger" type="submit" data-role="ok">删除</button>
                </div>
            </form>
        </dialog>

        <dialog id="req-approve-dialog">
            <form method="post" :action.attr="$h.ak('/admin/agents/requests/0/approve')" id="req-approve-form">
                <input type="hidden" name="back" :value.attr="$h.ak(m.reqFilter)">
                <div class="dlg-head">
                    <h3 data-role="title">同意申请</h3>
                    <p data-role="sub"></p>
                </div>
                <div class="dlg-body">
                    <input type="hidden" name="pickCodes" :value.attr="'true'" data-role="pick" disabled>
                    <div class="field" data-role="codes-field" hidden>
                        <div class="pick-head"><label>同意哪些口令</label><span class="muted small" data-role="pick-count"></span><button class="link small" type="button" data-role="pick-all">全选</button><button class="link small" type="button" data-role="pick-none">全不选</button></div>
                        <div class="pick-list" data-role="code-list"></div>
                        <span class="hint">没勾的口令会拒绝；已经重复的提交时就拒绝了，这里不能选</span>
                    </div>
                    <div class="row-2">
                        <div class="field" data-role="count-field">
                            <label for="req-count">生成个数</label>
                            <input class="input num" id="req-count" name="count" type="number" min="1" max="1000" required>
                        </div>
                        <div class="field">
                            <label for="req-max">每个口令可激活</label>
                            <div class="input-unit"><input class="input num" id="req-max" name="maxUses" type="number" min="0" max="100000" required><em>次</em></div>
                            <span class="hint">0 表示不限次数</span>
                        </div>
                    </div>
                    <div class="field">
                        <label for="req-expires">有效期至</label>
                        <input class="input" id="req-expires" name="expires" type="date">
                        <span class="hint">留空一直有效</span>
                    </div>
                    <p class="dlg-note" data-role="note">口令随机生成 8 位数字，直接归属这个代理，备注「代理申请 #编号」。</p>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">同意并生成</button>
                </div>
            </form>
        </dialog>

        <dialog id="notice-dialog" class="wide">
            <form method="post" :action.attr="$h.ak('/admin/agents/notices')" id="notice-form">
                <div class="dlg-head">
                    <h3 data-role="title">发布公告</h3>
                    <p>代理登录后在「公告」里看到；「重要」的会在概览顶部提醒</p>
                </div>
                <div class="dlg-body">
                    <div class="field">
                        <label for="notice-title">标题<span class="field-count" data-count-for="notice-title"></span></label>
                        <input class="input" id="notice-title" name="title" maxlength="60" required placeholder="比如：双十一活动口令加量">
                    </div>
                    <div class="field">
                        <label for="notice-body">内容<span class="field-count" data-count-for="notice-body"></span></label>
                        <textarea class="input" id="notice-body" name="body" rows="6" maxlength="2000" required placeholder="写清楚要代理知道或者要做的事，可以换行"></textarea>
                    </div>
                    <div class="row-2">
                        <div class="field">
                            <label>发给</label>
                            <div class="seg seg-fill">
                                <label class="seg-opt"><input type="radio" name="audience" value="all" checked><span>全部代理（含二级）</span></label>
                                <label class="seg-opt"><input type="radio" name="audience" value="top"><span>只发一级代理</span></label>
                            </div>
                        </div>
                        <div class="field">
                            <label>提醒程度</label>
                            <div class="seg seg-fill">
                                <label class="seg-opt"><input type="radio" name="level" value="normal" checked><span>普通</span></label>
                                <label class="seg-opt"><input type="radio" name="level" value="important"><span>重要</span></label>
                            </div>
                        </div>
                    </div>
                    <label class="check"><input type="checkbox" name="pinned" value="true">置顶，一直排在最前面</label>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit" data-role="ok">发布</button>
                </div>
            </form>
        </dialog>

        <dialog id="req-reject-dialog">
            <form method="post" :action.attr="$h.ak('/admin/agents/requests/0/reject')" id="req-reject-form">
                <input type="hidden" name="back" :value.attr="$h.ak(m.reqFilter)">
                <div class="dlg-head">
                    <h3 data-role="title">拒绝申请</h3>
                    <p data-role="sub"></p>
                </div>
                <div class="dlg-body">
                    <div class="field">
                        <label for="req-reason">原因</label>
                        <input class="input" id="req-reason" name="reason" maxlength="200" placeholder="可不填，代理在代理后台能看到">
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn btn-danger" type="submit">拒绝</button>
                </div>
            </form>
        </dialog>

    </main></template>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('代理管理'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
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
