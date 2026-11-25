<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'agents'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="$h.elvis($h.p(m.agent, 'name'), '代理详情')"/>
    <template v-for="L_a in [m.agent]"><template v-for="L_r in [m.row]"><main class="content ax ax-detail agent-detail">

        <a class="back-link" :href.attr="$h.ak($h.add('/admin/agents#agent-', $h.p(L_a, 'id')))"><svg class="i i-sm"><use href="#i-left"></use></svg>代理管理</a>
        <div class="page-head">
            <div class="page-title">
                <span class="who-ava who-ava-lg" :data-hue.attr="$h.av($h.mod($h.p(L_a, 'id'), 6))">{{ $h.s(($h.t($h.strings.isEmpty($h.p(L_a, 'name'))) ? '?' : $h.strings.substring($h.p(L_a, 'name'), 0, 1))) }}</span>
                <div>
                    <h1>{{ $h.s($h.p(L_a, 'name')) }}<span :class="['badge', $h.av(($h.t($h.p(L_a, 'enabled')) ? 'badge-solid' : 'badge-line'))]"><span class="dot"></span><span>{{ $h.s(($h.t($h.p(L_a, 'enabled')) ? '启用' : '停用')) }}</span></span></h1>
                    <p><span class="mono">{{ $h.s($h.p(L_a, 'username')) }}</span>{{ $h.s($h.cat(' · 创建于 ', $h.ui.date($h.p(L_a, 'createdAt')), ' · ', ($h.t(!$h.eq($h.p(L_a, 'lastLoginAt'), null)) ? $h.cat('最近登录 ', $h.ui.ago($h.p(L_a, 'lastLoginAt'))) : '还没登录过'))) }}</p>
                </div>
            </div>
            <div class="page-actions">
                <a class="btn btn-ghost ax-pv" :href.attr="$h.ak($h.link('/admin/agents/{id}/preview', [['id', $h.p(L_a, 'id')]]))" title="预览代理后台"><svg class="i"><use href="#i-eye"></use></svg><span>预览代理后台</span></a>
                <a class="btn btn-secondary" :href.attr="$h.ak($h.link('/admin/agents', [['edit', $h.p(L_a, 'id')]]))"><svg class="i"><use href="#i-edit"></use></svg>编辑</a>
                <a class="btn" :href.attr="$h.ak($h.add($h.link('/admin/codes', [['agent', $h.p(L_a, 'id')]]), '#new'))"><svg class="i"><use href="#i-plus"></use></svg>分配口令</a>
                <div class="pop" data-pop>
                    <button class="btn btn-secondary btn-only" type="button" data-pop-toggle aria-haspopup="true" aria-expanded="false" aria-label="更多操作" title="更多操作"><svg class="i"><use href="#i-more"></use></svg></button>
                    <div class="menu" data-pop-menu hidden>
                        <a class="menu-item" :href.attr="$h.ak($h.link('/admin/agents/{id}/active', [['id', $h.p(L_a, 'id')]]))"><svg class="i i-sm"><use href="#i-activity"></use></svg>日活与留存</a>
                        <a class="menu-item" :href.attr="$h.ak($h.link('/admin/agents/{id}/preview/retention', [['id', $h.p(L_a, 'id')]]))"><svg class="i i-sm"><use href="#i-target"></use></svg>每天的 7 日留存</a>
                        <a class="menu-item" :href.attr="$h.ak($h.link('/admin/agents/{id}/export', [['id', $h.p(L_a, 'id')]]))" title="TA 名下所有口令的激活记录，所有 App"><svg class="i i-sm"><use href="#i-download"></use></svg>导出激活记录</a>
                        <button class="menu-item" type="button" :data-copy.attr="$h.av($h.cat('登录地址：{origin}/login\n账号：', $h.p(L_a, 'username')))" data-copy-label="已复制登录信息"><svg class="i i-sm"><use href="#i-copy"></use></svg>复制登录信息</button>
                        <div class="menu-sep"></div>
                        <form method="post" :action.attr="$h.ak($h.link('/admin/agents/{id}/toggle', [['id', $h.p(L_a, 'id')]]))" :data-confirm.attr="$h.av(($h.t($h.p(L_a, 'enabled')) ? $h.cat('停用代理 ', $h.p(L_a, 'name'), '？') : null))" data-confirm-body="停用后 TA 不能登录，已登录的会马上退出。TA 名下的口令照常能用。" data-confirm-ok="停用">
                            <input type="hidden" name="back" :value.attr="'detail'">
                            <button class="menu-item" type="submit"><svg class="i i-sm"><use :href.attr="$h.av(($h.t($h.p(L_a, 'enabled')) ? '#i-lock' : '#i-check'))"></use></svg>{{ $h.s(($h.t($h.p(L_a, 'enabled')) ? '停用' : '启用')) }}</button>
                        </form>
                    </div>
                </div>
            </div>
        </div>

        <section class="kpis k5">
            <div class="kpi lead">
                <div class="kpi-k"><span class="pulse"></span>今日有效激活</div>
                <div class="kpi-v">{{ $h.s($h.numbers.formatInteger($h.p(L_r, 'today'), 1, 'COMMA')) }}</div>
                <div class="kpi-f"><span :class="['chg', $h.av($h.z(m.todayDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.todayDelta, null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.todayDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.todayDelta, 'text')) }}</span><span>{{ $h.s($h.cat('昨天 ', $h.numbers.formatInteger($h.p(L_r, 'yesterday'), 1, 'COMMA'))) }}</span></div>
            </div>
            <div class="kpi">
                <div class="kpi-k">近 7 天</div>
                <div class="kpi-v">{{ $h.s($h.numbers.formatInteger($h.p(L_r, 'week'), 1, 'COMMA')) }}</div>
                <div class="kpi-f"><span :class="['chg', $h.av($h.z(m.weekDelta, 'dir'))]" v-if="$h.t(!$h.eq(m.weekDelta, null))"><svg class="i"><use :href.attr="$h.av($h.cat('#i-trend-', $h.z(m.weekDelta, 'dir')))"></use></svg>{{ $h.s($h.z(m.weekDelta, 'text')) }}</span><span>{{ $h.s($h.cat('前 7 天 ', $h.numbers.formatInteger($h.p(L_r, 'prevWeek'), 1, 'COMMA'), ' · 近 30 天 ', $h.numbers.formatInteger($h.p(L_r, 'month'), 1, 'COMMA'))) }}</span></div>
            </div>
            <div class="kpi">
                <div class="kpi-k">累计有效激活</div>
                <div class="kpi-v">{{ $h.s($h.numbers.formatInteger($h.p(L_r, 'total'), 1, 'COMMA')) }}</div>
                <div class="kpi-f">{{ $h.s($h.cat('口令 ', $h.p(L_r, 'codeCount'), ' 个，还能用 ', $h.p(L_r, 'usable'), ' 个')) }}</div>
            </div>
            <div class="kpi">
                <div class="kpi-k">代理端看到的</div>
                <div class="kpi-v">{{ $h.s($h.numbers.formatInteger($h.p(L_r, 'visible'), 1, 'COMMA')) }}</div>
                <div class="kpi-f">{{ $h.s($h.cat($h.ui.dataMode($h.p(L_a, 'dataMode')), ($h.t(!$h.eq($h.p(L_r, 'cutoff'), null)) ? $h.cat(' · 截至 ', $h.ui.dt($h.p(L_r, 'cutoff'))) : ''), ($h.t($h.eq($h.p(L_r, 'deductionDesc'), '不扣量')) ? '' : ' · 有扣量'))) }}</div>
            </div>
            <a class="kpi" :href.attr="$h.ak($h.link('/admin/agents/{id}/preview/retention', [['id', $h.p(L_a, 'id')]]))" title="每天新激活的设备，第 1 到 7 天的留存">
                <div class="kpi-k">7 日留存<small>近 30 天</small><svg class="i"><use href="#i-arrow"></use></svg></div>
                <div :class="['kpi-v', $h.av(($h.t($h.eq($h.p(L_r, 'd7'), null)) ? 'none' : null))]">{{ $h.s(($h.t(!$h.eq($h.p(L_r, 'd7'), null)) ? $h.p(L_r, 'd7') : '—')) }}<small v-if="$h.t(!$h.eq($h.p(L_r, 'd7'), null))">%</small></div>
                <div class="kpi-f">{{ $h.s(($h.t(!$h.eq($h.p(L_r, 'd7'), null)) ? '第 7 天还打开 App 的占比 · 不扣量' : '激活满 8 天以后才有')) }}</div>
            </a>
        </section>

        <div class="grid detail-grid">
            <section class="card">
                <div class="card-head">
                    <h2>近 30 天有效激活</h2>
                    <span class="tail muted small">{{ $h.s($h.cat('合计 ', $h.numbers.formatInteger($h.p(L_r, 'month'), 1, 'COMMA'), ' 台')) }}</span>
                </div>
                <div class="vchart range-30">
                    <div class="vc-plot">
                        <template v-for="[L_d, L_dStat] in $h.each(m.bars)"><div :class="['vc-col', $h.av(($h.t($h.z(L_d, 'today')) ? 'last' : null)), $h.av(($h.t($h.eq($h.z(L_d, 'value'), 0)) ? 'zero' : null))]" :style="$h.av($h.cat('--h:', $h.z(L_d, 'px'), 'px;--i:', L_dStat.index))">
                            <span class="vc-bar"></span>
                            <span class="vc-tip">{{ $h.s($h.cat($h.z(L_d, 'date'), '：', $h.z(L_d, 'value'), ' 台')) }}</span>
                        </div></template>
                    </div>
                    <div class="vc-x"><template v-for="[L_d, L_dStat] in $h.each(m.bars)"><span :class="$h.av(($h.t($h.z(L_d, 'today')) ? 'last' : null))">{{ $h.s($h.z(L_d, 'label')) }}</span></template></div>
                </div>
            </section>
            <section class="card">
                <div class="card-head"><h2>设置</h2><a class="tail link" :href.attr="$h.ak($h.link('/admin/agents', [['edit', $h.p(L_a, 'id')]]))">修改<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <dl class="kvs">
                    <dt>申请口令</dt>
                    <dd>{{ $h.s(($h.t($h.eq($h.p(L_a, 'requestMode'), 'REVIEW')) ? '需要审核' : ($h.t($h.eq($h.p(L_a, 'requestMode'), 'AUTO')) ? '自动通过' : '不能申请'))) }}<small v-if="$h.t(!$h.eq($h.p(L_a, 'requestMode'), 'OFF'))">{{ $h.s(($h.t($h.gt($h.p(L_a, 'dailyRequestLimit'), 0)) ? $h.cat('每天最多 ', $h.p(L_a, 'dailyRequestLimit'), ' 个') : '不限个数')) }}</small><a class="mini-tag warn" href="/admin/agents#requests" v-if="$h.t($h.gt(m.pendingReqs, 0))">{{ $h.s($h.cat(m.pendingReqs, ' 条待审核')) }}</a></dd>
                    <dt>看到的数据</dt>
                    <dd>{{ $h.s($h.ui.dataMode($h.p(L_a, 'dataMode'))) }}<span :class="['mini-tag', $h.av(($h.t($h.p(L_a, 'canViewRecords')) ? 'on' : null))]">{{ $h.s(($h.t($h.p(L_a, 'canViewRecords')) ? '可查看记录' : '仅看数量')) }}</span></dd>
                    <dt>扣量</dt>
                    <dd :class="$h.av(($h.t($h.eq($h.p(L_r, 'deductionDesc'), '不扣量')) ? null : 'warn-text'))">{{ $h.s($h.p(L_r, 'deductionDesc')) }}</dd>
                    <dt>二级代理</dt>
                    <dd>{{ $h.s(($h.t($h.p(L_a, 'canManageSubs')) ? ($h.t($h.gt($h.p(L_a, 'subAgentLimit'), 0)) ? $h.cat('可以开，最多 ', $h.p(L_a, 'subAgentLimit'), ' 个') : '可以开，不限个数') : '不能开')) }}<a class="mini-tag on" href="#subs" v-if="!$h.t($h.lists.isEmpty(m.subs))">{{ $h.s($h.cat('已开 ', $h.lists.size(m.subs), ' 个')) }}</a></dd>
                    <dt>备注</dt>
                    <dd>{{ $h.s(($h.t($h.strings.isEmpty($h.p(L_a, 'remark'))) ? '—' : $h.p(L_a, 'remark'))) }}</dd>
                </dl>
            </section>
        </div>

        <section class="card section-gap" id="subs" v-if="$h.t(($h.t($h.p(L_a, 'canManageSubs')) || $h.t(!$h.t($h.lists.isEmpty(m.subs)))))">
            <div class="card-head">
                <h2>二级代理</h2>
                <span class="muted small">{{ $h.s(($h.t($h.p(L_a, 'canManageSubs')) ? '代理在代理后台自己开、自己分口令，激活都算在 TA 名下' : '二级代理权限已关，下面的二级代理暂时不能登录，重新打开就恢复')) }}</span>
                <a class="tail link" :href.attr="$h.ak($h.link('/admin/agents/{id}/preview/subs', [['id', $h.p(L_a, 'id')]]))" v-if="$h.t($h.p(L_a, 'canManageSubs'))">在代理后台里看<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
            </div>
            <div class="empty compact" v-if="$h.t($h.lists.isEmpty(m.subs))"><b>还没开二级代理</b>TA 在代理后台的「二级代理」里开，开了会列在这里</div>
            <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty(m.subs))">
                <table class="compact sub-table">
                    <thead><tr><th>二级代理</th><th class="right">可用口令</th><th class="right" title="分给 TA 的口令的真实有效激活">实际激活</th><th class="right" title="上级代理在代理后台看到的（按上级的扣量）">上级看到</th><th class="right" title="二级代理自己看到的（再按 TA 的扣量）">TA 看到</th><th>TA 的设置</th><th class="right">操作</th></tr></thead>
                    <tbody data-collapse="10" data-collapse-unit="个二级代理"><template v-for="[L_s, L_sStat] in $h.each(m.subs)"><template v-for="L_b in [$h.p(L_s, 'agent')]"><tr>
                        <td class="cell-primary"><span class="sub-who"><span class="who-ava" :data-hue.attr="$h.av($h.mod($h.p(L_b, 'id'), 6))">{{ $h.s(($h.t($h.strings.isEmpty($h.p(L_b, 'name'))) ? '?' : $h.strings.substring($h.p(L_b, 'name'), 0, 1))) }}</span><span class="sub-who-text"><b>{{ $h.s($h.p(L_b, 'name')) }}</b><small :title.attr="$h.av($h.ui.full($h.p(L_b, 'lastLoginAt')))"><span class="mono">{{ $h.s($h.p(L_b, 'username')) }}</span>{{ $h.s($h.add(' · ', ($h.t(!$h.eq($h.p(L_b, 'lastLoginAt'), null)) ? $h.cat($h.ui.ago($h.p(L_b, 'lastLoginAt')), ' 登录') : '从未登录'))) }}</small></span><span :class="['badge', $h.av(($h.t($h.p(L_s, 'active')) ? 'badge-solid' : 'badge-line'))]" :title.attr="$h.av(($h.t($h.p(L_s, 'active')) ? null : ($h.t($h.p(L_b, 'enabled')) ? '上级停用了或没有二级代理权限，暂时不能登录' : '停用')))"><span class="dot"></span><span>{{ $h.s(($h.t($h.p(L_s, 'active')) ? '启用' : ($h.t($h.p(L_b, 'enabled')) ? '暂停' : '停用'))) }}</span></span></span></td>
                        <td class="num right">{{ $h.s($h.p(L_s, 'usable')) }}<small class="muted">{{ $h.s($h.add(' / ', $h.p(L_s, 'codeCount'))) }}</small></td>
                        <td class="num right"><b>{{ $h.s($h.numbers.formatInteger($h.p(L_s, 'real'), 1, 'COMMA')) }}</b></td>
                        <td class="num right">{{ $h.s($h.numbers.formatInteger($h.p(L_s, 'total'), 1, 'COMMA')) }}</td>
                        <td class="num right">{{ $h.s($h.numbers.formatInteger($h.p(L_s, 'seen'), 1, 'COMMA')) }}</td>
                        <td><span class="nowrap" :title.attr="$h.av($h.ui.full($h.p(L_s, 'cutoff')))">{{ $h.s($h.cat($h.ui.dataMode($h.p(L_s, 'mode')), ($h.t($h.eq($h.p(L_s, 'cutoff'), null)) ? '' : $h.add(' · 截至 ', $h.ui.dt($h.p(L_s, 'cutoff')))))) }}</span><small class="muted-line">{{ $h.s($h.cat($h.p(L_s, 'deductionDesc'), ' · ', ($h.t($h.p(L_b, 'canViewRecords')) ? '可查看记录' : '仅看数量'))) }}</small></td>
                        <td class="right"><div class="btn-row">
                            <a class="icon-btn" :href.attr="$h.ak($h.link('/admin/agents/{id}/preview', [['id', $h.p(L_b, 'id')]]))" title="以 TA 的视角查看" aria-label="以 TA 的视角查看"><svg class="i"><use href="#i-eye"></use></svg></a>
                            <form method="post" :action.attr="$h.ak($h.link('/admin/agents/{id}/toggle', [['id', $h.p(L_b, 'id')]]))" :data-confirm.attr="$h.av(($h.t($h.p(L_b, 'enabled')) ? $h.cat('停用二级代理 ', $h.p(L_b, 'name'), '？') : null))" data-confirm-body="停用后 TA 不能登录，已登录的会马上退出。分给 TA 的口令照常能用。" data-confirm-ok="停用"><input type="hidden" name="back" :value.attr="'detail'"><button class="icon-btn" type="submit" :title.attr="$h.av(($h.t($h.p(L_b, 'enabled')) ? '停用' : '启用'))" :aria-label.attr="$h.av(($h.t($h.p(L_b, 'enabled')) ? '停用' : '启用'))"><svg class="i"><use :href.attr="$h.av(($h.t($h.p(L_b, 'enabled')) ? '#i-lock' : '#i-check'))"></use></svg></button></form>
                            <form method="post" :action.attr="$h.ak($h.link('/admin/agents/{id}/delete', [['id', $h.p(L_b, 'id')]]))" :data-confirm.attr="$h.av($h.cat('删除二级代理 ', $h.p(L_b, 'name'), '？'))" :data-confirm-body.attr="$h.av(($h.t($h.gt($h.p(L_s, 'codeCount'), 0)) ? $h.cat('账号删除后不能再登录。分给 TA 的 ', $h.p(L_s, 'codeCount'), ' 个口令收回给 ', $h.p(L_a, 'name'), '，激活记录都保留。') : '账号删除后不能再登录。'))" data-confirm-ok="删除" data-confirm-danger><button class="icon-btn danger-text" type="submit" title="删除" aria-label="删除"><svg class="i"><use href="#i-trash"></use></svg></button></form>
                        </div></td>
                    </tr></template></template></tbody>
                </table>
            </div>
        </section>

        <div class="grid grid-2 section-gap">
            <section class="card">
                <div class="card-head"><h2>按月</h2><span class="muted small">最近 6 个月</span></div>
                <div class="table-wrap">
                    <table class="compact" data-no-cards>
                        <thead><tr><th>月份</th><th class="right">提交</th><th class="right">有效激活</th><th class="right">确认率</th></tr></thead>
                        <tbody><template v-for="[L_m, L_mStat] in $h.each(m.months)"><tr>
                            <td class="mono">{{ $h.s($h.z(L_m, 'label')) }}</td>
                            <td class="num right">{{ $h.s($h.numbers.formatInteger($h.z(L_m, 'submitted'), 1, 'COMMA')) }}</td>
                            <td class="num right"><b>{{ $h.s($h.numbers.formatInteger($h.z(L_m, 'confirmed'), 1, 'COMMA')) }}</b></td>
                            <td class="num right muted">{{ $h.s(($h.t($h.gt($h.z(L_m, 'submitted'), 0)) ? $h.add($h.z(L_m, 'rate'), '%') : '—')) }}</td>
                        </tr></template></tbody>
                    </table>
                </div>
            </section>
            <section class="card">
                <div class="card-head"><h2>用在哪些 App</h2><span class="muted small">口令所有 App 通用</span></div>
                <div class="empty compact" v-if="$h.t($h.lists.isEmpty(m.appStats))"><b>还没有有效激活</b>TA 的口令在哪个 App 里激活，就会算到哪个 App</div>
                <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty(m.appStats))">
                    <table class="compact" data-no-cards>
                        <thead><tr><th>App</th><th class="right">有效激活</th><th class="right">占比</th><th class="right">今天</th></tr></thead>
                        <tbody data-collapse="10" data-collapse-unit="个 App"><template v-for="[L_s, L_sStat] in $h.each(m.appStats)"><tr>
                            <td><b>{{ $h.s($h.z(L_s, 'name')) }}</b></td>
                            <td class="num right"><b>{{ $h.s($h.numbers.formatInteger($h.z(L_s, 'confirmed'), 1, 'COMMA')) }}</b></td>
                            <td class="num right muted">{{ $h.s($h.add($h.z(L_s, 'share'), '%')) }}</td>
                            <td class="num right">{{ $h.s(($h.t($h.gt($h.z(L_s, 'today'), 0)) ? $h.add('+', $h.z(L_s, 'today')) : '—')) }}</td>
                        </tr></template></tbody>
                    </table>
                </div>
            </section>
        </div>

        <div class="grid grid-2 section-gap">
            <section class="card">
                <div class="card-head"><h2>口令</h2><span class="muted small">激活最多的</span><a class="tail link" :href.attr="$h.ak($h.link('/admin/codes', [['agent', $h.p(L_a, 'id')]]))" v-if="$h.t($h.gt($h.p(L_r, 'codeCount'), 0))">{{ $h.s($h.cat('全部 ', $h.p(L_r, 'codeCount'), ' 个')) }}<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="empty compact" v-if="$h.t($h.lists.isEmpty(m.topCodes))"><b>还没有口令</b><a class="link" :href.attr="$h.ak($h.add($h.link('/admin/codes', [['agent', $h.p(L_a, 'id')]]), '#new'))">给 TA 分配口令</a></div>
                <ul class="line-list" v-if="!$h.t($h.lists.isEmpty(m.topCodes))"><template v-for="[L_k, L_kStat] in $h.each(m.topCodes)"><li>
                    <span class="ll-main"><a class="mono link-strong" :href.attr="$h.ak($h.link('/admin/codes', [['q', $h.z(L_k, 'code')]]))">{{ $h.s($h.z(L_k, 'code')) }}</a><small v-if="!$h.t($h.strings.isEmpty($h.z(L_k, 'remark')))">{{ $h.s($h.z(L_k, 'remark')) }}</small></span>
                    <span :class="['code-st', $h.av($h.z(L_k, 'status'))]">{{ $h.s(($h.t($h.eq($h.z(L_k, 'status'), 'usable')) ? '可用' : ($h.t($h.eq($h.z(L_k, 'status'), 'exhausted')) ? '已用完' : ($h.t($h.eq($h.z(L_k, 'status'), 'expired')) ? '已过期' : '停用')))) }}</span>
                    <span class="ll-num"><b>{{ $h.s($h.z(L_k, 'confirmed')) }}</b><small>{{ $h.s(($h.t($h.gt($h.z(L_k, 'maxUses'), 0)) ? $h.cat(' / ', $h.z(L_k, 'maxUses')) : ' / 不限')) }}</small><small class="ll-seen" v-if="$h.t(($h.t(!$h.eq($h.z(L_k, 'seen'), null)) &amp;&amp; $h.t(!$h.eq($h.z(L_k, 'seen'), $h.z(L_k, 'confirmed')))))">{{ $h.s($h.cat(' · 代理看到 ', $h.z(L_k, 'seen'))) }}</small></span>
                </li></template></ul>
            </section>
            <section class="card">
                <div class="card-head"><h2>最近激活</h2><a class="tail link" :href.attr="$h.ak($h.link('/admin/activations', [['agent', $h.p(L_a, 'id')]]))" v-if="!$h.t($h.lists.isEmpty(m.recent))">激活记录<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="empty compact" v-if="$h.t($h.lists.isEmpty(m.recent))"><b>还没有激活</b>TA 的口令被用户提交后会出现在这里</div>
                <ul class="line-list" v-if="!$h.t($h.lists.isEmpty(m.recent))"><template v-for="[L_e, L_eStat] in $h.each(m.recent)"><li>
                    <span class="ll-main"><span class="mono">{{ $h.s($h.z(L_e, 'code')) }}</span><small>{{ $h.s($h.cat($h.z(L_e, 'appName'), ' · 设备 ', $h.z(L_e, 'device'))) }}</small></span>
                    <span :class="['code-st', $h.av(($h.t($h.z(L_e, 'confirmed')) ? 'usable' : ($h.t($h.z(L_e, 'fast')) ? 'rejected' : ($h.t($h.z(L_e, 'dup')) ? 'exhausted' : 'pending'))))]">{{ $h.s(($h.t($h.z(L_e, 'confirmed')) ? '已确认' : ($h.t($h.z(L_e, 'fast')) ? '非正常' : ($h.t($h.z(L_e, 'dup')) ? '重复' : '待确认')))) }}</span>
                    <span class="ll-time" :title.attr="$h.av($h.ui.full($h.z(L_e, 'at')))">{{ $h.s($h.ui.ago($h.z(L_e, 'at'))) }}</span>
                </li></template></ul>
            </section>
        </div>

        <div class="grid grid-2 section-gap">
            <section class="card">
                <div class="card-head"><h2>口令申请</h2><a class="tail link" :href.attr="$h.ak('/admin/agents#requests')" v-if="$h.t($h.gt(m.pendingReqs, 0))">{{ $h.s($h.cat('去审核 ', m.pendingReqs, ' 条')) }}<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="empty compact" v-if="$h.t($h.lists.isEmpty(m.reqs))"><b>还没申请过</b>{{ $h.s(($h.t($h.eq($h.p(L_a, 'requestMode'), 'OFF')) ? '现在设置的是不能申请口令' : 'TA 在代理后台申请口令后会出现在这里')) }}</div>
                <ul class="line-list" v-if="!$h.t($h.lists.isEmpty(m.reqs))"><template v-for="[L_q, L_qStat] in $h.each(m.reqs)"><li>
                    <span class="ll-main"><b>{{ $h.s(($h.t($h.z(L_q, 'custom')) ? $h.cat('自定义 ', $h.z(L_q, 'count'), ' 个') : $h.cat('随机 ', $h.z(L_q, 'count'), ' 个'))) }}</b><small>{{ $h.s(($h.t($h.strings.isEmpty($h.z(L_q, 'reviewNote'))) ? ($h.t(!$h.eq($h.z(L_q, 'codeCount'), null)) ? $h.cat('生成了 ', $h.z(L_q, 'codeCount'), ' 个口令') : '') : $h.z(L_q, 'reviewNote'))) }}</small></span>
                    <span :class="['code-st', $h.av($h.strings.toLowerCase($h.z(L_q, 'status')))]">{{ $h.s($h.z(L_q, 'statusLabel')) }}</span>
                    <span class="ll-time" :title.attr="$h.av($h.ui.full($h.z(L_q, 'at')))">{{ $h.s($h.ui.ago($h.z(L_q, 'at'))) }}</span>
                </li></template></ul>
            </section>
            <section class="card">
                <div class="card-head"><h2>最近动态</h2><span class="muted small">TA 自己的操作</span></div>
                <div class="empty compact" v-if="$h.t($h.lists.isEmpty(m.logs))"><b>还没有动态</b>登录、申请口令、改密码、导出记录都会记在这里</div>
                <ul class="line-list" v-if="!$h.t($h.lists.isEmpty(m.logs))"><template v-for="[L_g, L_gStat] in $h.each(m.logs)"><li>
                    <span class="ll-main"><span>{{ $h.s($h.p(L_g, 'detail')) }}</span></span>
                    <span class="ll-time" :title.attr="$h.av($h.ui.full($h.p(L_g, 'createdAt')))">{{ $h.s($h.ui.ago($h.p(L_g, 'createdAt'))) }}</span>
                </li></template></ul>
            </section>
        </div>

    </main></template></template>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add($h.elvis($h.p(m.agent, 'name'), '代理详情'), $h.add(' · ', $h.elvis(m.brandName, '管理后台'))),
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
