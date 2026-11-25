<template>
<FAdminLayoutAgentbar :m="m" :c="c" :P_active="'subs'"/>
<template v-for="L_full in [($h.t($h.gt(m.subLimit, 0)) &amp;&amp; $h.t($h.ge($h.lists.size(m.subs), m.subLimit)))]"><main class="content ap ap-subs sub-page">

    <div class="page-head">
        <div class="page-title"><div>
            <h1>二级代理</h1>
            <p>{{ $h.s(($h.t($h.lists.isEmpty(m.subs)) ? '给下级开账号、分口令，TA 带来的激活照常算在你名下' : $h.cat($h.lists.size(m.subs), ' 个二级代理，', m.subEnabled, ' 个启用', ($h.t($h.gt(m.subLimit, 0)) ? $h.cat(' · 最多开 ', m.subLimit, ' 个') : '')))) }}</p>
        </div></div>
        <div class="page-actions" v-if="!$h.t(m.preview)">
            <form method="post" :action.attr="$h.ak('/agent/subs/refresh-all')" data-confirm="更新全部手动二级代理的数据？" :data-confirm-body.attr="$h.av($h.cat(m.manualCount, ' 个「手动更新」的二级代理将看到', ($h.t($h.eq(m.cutoff, null)) ? '截至现在' : $h.add('截至 ', $h.ui.dt(m.cutoff))), '的数据。'))" data-confirm-ok="全部更新" v-if="$h.t($h.gt(m.manualCount, 0))">
                <button class="btn btn-secondary" type="submit"><svg class="i"><use href="#i-refresh"></use></svg>更新全部手动二级代理</button>
            </form>
            <button class="btn" type="button" data-sub-new data-hash-click="new" :disabled.attr="$h.t(L_full) ? 'disabled' : null" :title.attr="$h.av(($h.t(L_full) ? $h.cat('最多开 ', m.subLimit, ' 个，已经开满了，要多开找管理员') : null))"><svg class="i"><use href="#i-plus"></use></svg>新建二级代理</button>
        </div>
    </div>

    <section class="kpis k4" v-if="!$h.t($h.lists.isEmpty(m.subs))">
        <div class="kpi">
            <div class="kpi-k">二级代理</div>
            <div class="kpi-v">{{ $h.s($h.lists.size(m.subs)) }}<small>个</small></div>
            <div class="kpi-f">{{ $h.s($h.cat('启用 ', m.subEnabled, ($h.t($h.gt(m.subLimit, 0)) ? $h.cat(' · 最多 ', m.subLimit, ' 个') : ' · 不限个数'))) }}</div>
        </div>
        <div class="kpi">
            <div class="kpi-k">{{ $h.s($h.add(m.refLabel, '新增')) }}</div>
            <div class="kpi-v">{{ $h.s($h.add('+', $h.numbers.formatInteger(m.subToday, 1, 'COMMA'))) }}</div>
            <div class="kpi-f">二级代理的口令带来的，按你看到的算</div>
        </div>
        <div class="kpi">
            <div class="kpi-k">{{ $h.s(m.rangeLabel) }}</div>
            <div class="kpi-v">{{ $h.s($h.numbers.formatInteger(m.subRange, 1, 'COMMA')) }}</div>
            <div class="kpi-f">{{ $h.s($h.cat('TA 们看到 ', $h.numbers.formatInteger(m.subSeenRange, 1, 'COMMA'), ' · 累计 ', $h.numbers.formatInteger(m.subTotal, 1, 'COMMA'))) }}</div>
        </div>
        <div class="kpi">
            <div class="kpi-k">已分出口令</div>
            <div class="kpi-v">{{ $h.s(m.givenCount) }}<small>个</small></div>
            <div class="kpi-f">{{ $h.s($h.cat('你手里还有 ', m.freeUsable, ' 个可用')) }}</div>
        </div>
    </section>

    <section class="card">
        <div class="empty" v-if="$h.t($h.lists.isEmpty(m.subs))">
            <div class="empty-icon"><svg class="i"><use href="#i-users"></use></svg></div>
            <b>还没有二级代理</b>{{ $h.s(($h.t(m.preview) ? 'TA 还没开二级代理' : '开个账号给下级，从你的口令里分一些给 TA，TA 带来的激活照常算在你名下')) }}
            <div v-if="!$h.t(m.preview)"><button class="btn" type="button" data-sub-new><svg class="i"><use href="#i-plus"></use></svg>新建二级代理</button></div>
        </div>

        <div class="agl-tools" v-if="!$h.t($h.lists.isEmpty(m.subs))">
            <span class="range-k">统计时间段</span>
            <div class="range-pick">
                <div class="seg seg-sm"><template v-for="[L_o, L_oStat] in $h.each(m.rangeOpts)"><a :class="$h.av(($h.t($h.eq($h.p(L_o, 'key'), m.range)) ? 'on' : null))" :href.attr="$h.ak($h.link($h.add(m.base, '/subs'), [['range', $h.p(L_o, 'key')]]))">{{ $h.s($h.p(L_o, 'label')) }}</a></template><button type="button" :class="$h.av(($h.t($h.eq(m.range, 'custom')) ? 'on' : null))" :title.attr="$h.av(($h.t($h.eq(m.range, 'custom')) ? $h.cat('自定义：', m.rangeLabel) : '自定义时间段'))" aria-label="自定义时间段" aria-haspopup="dialog" data-range-custom :data-from.attr="$h.ak(m.rangeFrom)" :data-to.attr="$h.ak(m.rangeTo)" :data-max.attr="$h.ak(m.rangeMax)" :data-action.attr="$h.ak($h.add(m.base, '/subs'))" :aria-expanded.attr="$h.av(($h.t($h.eq(m.range, 'custom')) ? 'true' : 'false'))"><svg class="i i-sm"><use href="#i-calendar"></use></svg><span class="rc-tx">自定义</span></button></div>
            </div>
            <span class="grow"></span>
            <label class="search" v-if="$h.t($h.gt($h.lists.size(m.subs), 6))"><svg class="i i-sm"><use href="#i-search"></use></svg><input class="input" type="search" placeholder="搜索名称或账号" aria-label="搜索二级代理" data-filter="#sub-cards" data-filter-item=".agl-row"></label>
        </div>

        <div class="agl agl-subs" v-if="!$h.t($h.lists.isEmpty(m.subs))">
            <div class="agl-head">
                <span>二级代理</span><span class="r">{{ $h.s(m.refLabel) }}</span><span class="r">{{ $h.s(m.rangeLabel) }}</span><span class="r">累计</span><span class="r" title="在你看到的基础上，再按 TA 的扣量和数据更新方式算">TA 看到的</span><span class="r">可用口令</span><span class="h-spark">近 14 天</span><span class="h-login">最近登录</span><span></span>
            </div>
            <div class="agl-body" id="sub-cards">
                <template v-for="[L_r, L_rStat] in $h.each(m.subs)"><template v-for="L_a in [$h.p(L_r, 'agent')]"><template v-for="L_view in [($h.t(m.preview) ? $h.cat('/admin/agents/', $h.p(L_a, 'id'), '/preview') : $h.cat('/agent/subs/', $h.p(L_a, 'id'), '/preview'))]"><article :class="['agl-row', $h.av(($h.t($h.p(L_r, 'active')) ? null : 'off'))]" :data-hash-target.attr="$h.av($h.add('sub-', $h.p(L_a, 'id')))">
                    <div class="agl-who">
                        <span class="who-ava" :data-hue.attr="$h.av($h.mod($h.p(L_a, 'id'), 6))">{{ $h.s(($h.t($h.strings.isEmpty($h.p(L_a, 'name'))) ? '?' : $h.strings.substring($h.p(L_a, 'name'), 0, 1))) }}</span>
                        <a class="ac-name" :href.attr="$h.ak(L_view)" title="以 TA 的视角查看">
                            <b>{{ $h.s($h.p(L_a, 'name')) }}<em class="mini-tag" v-if="!$h.t($h.p(L_a, 'enabled'))">停用</em></b>
                            <small><span class="mono">{{ $h.s($h.p(L_a, 'username')) }}</span><span class="sep" :title.attr="$h.av(($h.t($h.eq($h.p(L_r, 'cutoff'), null)) ? 'TA 随时看到最新数据' : $h.add('TA 的数据截至 ', $h.ui.full($h.p(L_r, 'cutoff')))))">{{ $h.s(($h.t($h.eq($h.p(L_r, 'cutoff'), null)) ? $h.ui.dataMode($h.p(L_r, 'mode')) : $h.cat($h.ui.dataMode($h.p(L_r, 'mode')), ' · 截至 ', $h.ui.dt($h.p(L_r, 'cutoff'))))) }}</span><span class="sep warn" :title.attr="$h.av($h.add('扣量：', $h.p(L_r, 'deductionDesc')))" v-if="!$h.t($h.eq($h.p(L_r, 'deductionDesc'), '不扣量'))">扣量</span><span class="sep" v-if="$h.t($h.p(L_a, 'canViewRecords'))">可查看记录</span><span class="sep" translate="no" :title.attr="$h.av($h.p(L_r, 'note'))" v-if="!$h.t($h.strings.isEmpty($h.p(L_r, 'note')))">{{ $h.s($h.p(L_r, 'note')) }}</span></small>
                        </a>
                    </div>
                    <div class="agl-nums">
                        <div :class="['agl-n', 'r', $h.av(($h.t($h.eq($h.p(L_r, 'today'), 0)) ? 'zero' : null))]" :data-k.attr="$h.av(m.refLabel)">{{ $h.s($h.numbers.formatInteger($h.p(L_r, 'today'), 1, 'COMMA')) }}</div>
                        <div :class="['agl-n', 'r', $h.av(($h.t($h.eq($h.p(L_r, 'inRange'), 0)) ? 'zero' : null))]" :data-k.attr="$h.av(m.rangeLabel)">{{ $h.s($h.numbers.formatInteger($h.p(L_r, 'inRange'), 1, 'COMMA')) }}</div>
                        <div :class="['agl-n', 'r', $h.av(($h.t($h.eq($h.p(L_r, 'total'), 0)) ? 'zero' : null))]" data-k="累计">{{ $h.s($h.numbers.formatInteger($h.p(L_r, 'total'), 1, 'COMMA')) }}</div>
                        <div class="agl-n r" data-k="TA 看到的" :title.attr="$h.av(($h.t($h.p(L_a, 'canViewRecords')) ? '能看到每条激活记录（设备号脱敏）' : '只能看到数量和趋势'))">{{ $h.s($h.numbers.formatInteger($h.p(L_r, 'seenInRange'), 1, 'COMMA')) }}<span class="sub2">{{ $h.s($h.cat('累计 ', $h.numbers.formatInteger($h.p(L_r, 'seen'), 1, 'COMMA'))) }}</span></div>
                        <div class="agl-n r" data-k="可用口令">
                            <button type="button" data-sub-codes :data-id.attr="$h.av($h.p(L_a, 'id'))" :data-name.attr="$h.av($h.p(L_a, 'name'))" title="分配口令" v-if="!$h.t(m.preview)">{{ $h.s($h.p(L_r, 'usable')) }}<small>{{ $h.s($h.add('/', $h.p(L_r, 'codeCount'))) }}</small></button>
                            <template v-if="$h.t(m.preview)">{{ $h.s($h.p(L_r, 'usable')) }}<small>{{ $h.s($h.add('/', $h.p(L_r, 'codeCount'))) }}</small></template>
                        </div>
                        <div class="agl-spark flat" title="近 14 天没有有效激活" v-if="$h.t($h.p(L_r, 'sparkEmpty'))"></div>
                        <div class="agl-spark" title="近 14 天每天的有效激活（你看到的）" v-if="!$h.t($h.p(L_r, 'sparkEmpty'))"><template v-for="[L_px, L_pxStat] in $h.each($h.p(L_r, 'spark'))"><i :class="$h.av(($h.t(L_pxStat.last) ? 'now' : ($h.t($h.gt(L_px, 2)) ? 'on' : null)))" :style="$h.av($h.cat('height:', L_px, 'px'))"></i></template></div>
                        <div class="agl-login" :title.attr="$h.av($h.ui.full($h.p(L_a, 'lastLoginAt')))">{{ $h.s(($h.t(!$h.eq($h.p(L_a, 'lastLoginAt'), null)) ? $h.cat($h.ui.ago($h.p(L_a, 'lastLoginAt')), ' 登录') : '从未登录')) }}</div>
                    </div>
                    <div class="ac-menu" data-pop v-if="!$h.t(m.preview)">
                        <button class="icon-btn" type="button" data-pop-toggle aria-label="更多操作" title="更多操作"><svg class="i"><use href="#i-more"></use></svg></button>
                        <div class="menu menu-float" data-pop-menu hidden>
                            <a class="menu-item" :href.attr="$h.ak(L_view)"><svg class="i i-sm"><use href="#i-eye"></use></svg>以 TA 的视角查看</a>
                            <button class="menu-item" type="button" :data-copy.attr="$h.av($h.cat('登录地址：{origin}/login\n账号：', $h.p(L_a, 'username')))" data-copy-label="已复制登录信息"><svg class="i i-sm"><use href="#i-copy"></use></svg>复制登录信息</button>
                            <button class="menu-item" type="button" data-sub-edit :data-id.attr="$h.av($h.p(L_a, 'id'))" :data-name.attr="$h.av($h.p(L_a, 'name'))" :data-username.attr="$h.av($h.p(L_a, 'username'))" :data-remark.attr="$h.av($h.p(L_r, 'note'))" :data-view.attr="$h.av($h.p(L_a, 'canViewRecords'))" :data-mode.attr="$h.av($h.p(L_a, 'dataMode'))" :data-tiers.attr="$h.av($h.p(L_r, 'tiers'))"><svg class="i i-sm"><use href="#i-edit"></use></svg>编辑</button>
                            <button class="menu-item" type="button" data-sub-codes :data-id.attr="$h.av($h.p(L_a, 'id'))" :data-name.attr="$h.av($h.p(L_a, 'name'))"><svg class="i i-sm"><use href="#i-key"></use></svg>分配口令</button>
                            <form method="post" :action.attr="$h.ak($h.link('/agent/subs/{id}/refresh', [['id', $h.p(L_a, 'id')]]))" v-if="$h.t($h.eq($h.p(L_a, 'dataMode'), 'MANUAL'))">
                                <button class="menu-item" type="submit"><svg class="i i-sm"><use href="#i-refresh"></use></svg>{{ $h.s(($h.t($h.eq(m.cutoff, null)) ? '把数据更新到现在' : $h.add('把数据更新到 ', $h.ui.dt(m.cutoff)))) }}</button>
                            </form>
                            <form method="post" :action.attr="$h.ak($h.link('/agent/subs/{id}/toggle', [['id', $h.p(L_a, 'id')]]))" :data-confirm.attr="$h.av(($h.t($h.p(L_a, 'enabled')) ? $h.cat('停用二级代理 ', $h.p(L_a, 'name'), '？') : null))" data-confirm-body="停用后 TA 不能登录，已登录的会马上退出。分给 TA 的口令照常能用。" data-confirm-ok="停用">
                                <button class="menu-item" type="submit"><svg class="i i-sm"><use :href.attr="$h.av(($h.t($h.p(L_a, 'enabled')) ? '#i-lock' : '#i-check'))"></use></svg>{{ $h.s(($h.t($h.p(L_a, 'enabled')) ? '停用' : '启用')) }}</button>
                            </form>
                            <div class="menu-sep"></div>
                            <form method="post" :action.attr="$h.ak($h.link('/agent/subs/{id}/delete', [['id', $h.p(L_a, 'id')]]))" :data-confirm.attr="$h.av($h.cat('删除二级代理 ', $h.p(L_a, 'name'), '？'))" :data-confirm-body.attr="$h.av(($h.t($h.gt($h.p(L_r, 'codeCount'), 0)) ? $h.cat('账号删除后不能再登录。分给 TA 的 ', $h.p(L_r, 'codeCount'), ' 个口令回到你手里，照常能用，激活记录都保留。') : '账号删除后不能再登录。'))" data-confirm-ok="删除" data-confirm-danger>
                                <button class="menu-item danger" type="submit"><svg class="i i-sm"><use href="#i-trash"></use></svg>删除</button>
                            </form>
                        </div>
                    </div>
                </article></template></template></template>
            </div>
        </div>
        <div class="list-none" id="sub-cards-none" hidden>没有符合条件的二级代理</div>
    </section>

    <div class="note sub-note" v-if="!$h.t($h.lists.isEmpty(m.subs))"><svg class="i"><use href="#i-info"></use></svg><span>列表里的数是你看到的；「TA 看到的」是在你看到的基础上，再按 TA 的扣量和数据更新方式算的，TA 看不到比你更新的数据。你的扣量按每个二级代理的激活数按比例分摊，谁也不会多扣；改了扣量比例，只影响改了以后的激活，已经扣过的不会变。</span></div>

    <dialog id="sub-dialog" class="wide" v-if="!$h.t(m.preview)">
        <form method="post" id="sub-form" :action.attr="$h.ak('/agent/subs/create')">
            <div class="dlg-head">
                <h3 data-role="title">新建二级代理</h3>
                <p>二级代理用这个账号在同一个登录页登录，只看得到你分给 TA 的口令和数据。</p>
            </div>
            <div class="dlg-body">
                <div class="row-2">
                    <div class="field">
                        <label for="sub-name">名称</label>
                        <input class="input" id="sub-name" name="name" placeholder="例如 李四" maxlength="64" required>
                    </div>
                    <div class="field">
                        <label for="sub-username">登录账号</label>
                        <input class="input mono" id="sub-username" name="username" placeholder="例如 lisi" maxlength="32" pattern="[A-Za-z0-9_.\-]{3,32}" title="3–32 位字母、数字或 _ . -" autocomplete="off" required>
                    </div>
                </div>
                <div class="field">
                    <label for="sub-password">登录密码</label>
                    <input class="input" id="sub-password" name="password" type="text" minlength="6" maxlength="64" autocomplete="new-password" placeholder="至少 6 位">
                    <span class="hint" data-role="pwd-hint">把账号和密码告诉 TA，TA 用它登录</span>
                </div>
                <div class="field">
                    <label>设备记录</label>
                    <div class="opt-grid two" v-if="$h.t(m.canView)">
                        <label class="opt"><input type="radio" name="canViewRecords" :value.attr="'true'">
                            <span><b>可查看设备记录</b><small>能看到每条激活的时间、口令，以及脱敏后的设备号和 IP</small></span></label>
                        <label class="opt"><input type="radio" name="canViewRecords" :value.attr="'false'" :checked.attr="''">
                            <span><b>仅查看数量</b><small>只能看到激活数量和趋势，看不到设备记录</small></span></label>
                    </div>
                    <div class="opt-grid" v-if="!$h.t(m.canView)">
                        <label class="opt"><input type="radio" name="canViewRecords" :value.attr="'false'" :checked.attr="''">
                            <span><b>仅查看数量</b><small>你自己只能看数量，二级代理也只能看数量</small></span></label>
                    </div>
                </div>
                <div class="field">
                    <label>数据更新</label>
                    <div :class="['opt-grid', $h.av(($h.t($h.eq($h.lists.size(m.modes), 3)) ? 'three' : ($h.t($h.eq($h.lists.size(m.modes), 2)) ? 'two' : null)))]">
                        <label class="opt" v-if="$h.t($h.lists.contains(m.modes, 'REALTIME'))"><input type="radio" name="dataMode" :value.attr="'REALTIME'">
                            <span><b>实时</b><small>TA 随时看到最新数据</small></span></label>
                        <label class="opt" v-if="$h.t($h.lists.contains(m.modes, 'DAILY'))"><input type="radio" name="dataMode" :value.attr="'DAILY'">
                            <span><b>每日 0 点</b><small>只统计到昨天 24 点，次日 0 点自动更新</small></span></label>
                        <label class="opt" v-if="$h.t($h.lists.contains(m.modes, 'MANUAL'))"><input type="radio" name="dataMode" :value.attr="'MANUAL'">
                            <span><b>手动更新</b><small>你点「更新数据」后 TA 才看到新数据</small></span></label>
                    </div>
                    <span class="hint" v-if="!$h.t($h.eq(m.upMode, 'REALTIME'))">{{ $h.s($h.cat('你的数据是「', $h.ui.dataMode(m.upMode), '」，二级代理看到的不会比你的新')) }}</span>
                </div>
                <div class="field">
                    <label>扣量档位</label>
                    <div class="tiers">
                        <div class="tier-head"><span>#</span><span>当日有效激活 从</span><span>到（含）</span><span>扣量比例</span><span></span></div>
                        <div id="sub-tier-rows"></div>
                        <div class="tiers-empty" id="sub-tier-empty">未设置档位，TA 看到的和你看到的一样</div>
                    </div>
                    <div class="tier-foot">
                        <span class="hint">在你看到的数上再扣：当天总数落在哪档就按那档的比例扣，「到」不填表示不限</span>
                        <button class="btn btn-secondary btn-sm" type="button" id="sub-tier-add"><svg class="i i-sm"><use href="#i-plus"></use></svg>添加档位</button>
                    </div>
                    <div class="tier-example" id="sub-tier-example" hidden></div>
                </div>
                <div class="field">
                    <label for="sub-remark">备注</label>
                    <input class="input" id="sub-remark" name="remark" placeholder="只有你看得到，可不填" maxlength="200">
                </div>
            </div>
            <div class="dlg-foot">
                <button class="btn btn-secondary" type="button" data-close>取消</button>
                <button class="btn" type="submit">保存</button>
            </div>
        </form>
    </dialog>

    <dialog id="sub-codes-dialog" class="wide" v-if="!$h.t(m.preview)">
        <form method="post" id="sub-codes-form" :action.attr="$h.ak('/agent/subs/0/codes')">
            <div class="dlg-head">
                <h3 data-role="title">分配口令</h3>
                <p>口令分给 TA 以后仍然归你，TA 带来的激活照常算在你名下。</p>
            </div>
            <div class="dlg-body">
                <label class="search sub-pick-search" v-if="$h.t($h.gt($h.lists.size(m.freeCodes), 12))"><svg class="i i-sm"><use href="#i-search"></use></svg><input class="input" type="search" placeholder="搜索口令" aria-label="搜索口令" data-role="q"></label>
                <div class="field">
                    <div class="pick-head"><label>从你手里分给 TA</label><button class="link small" type="button" data-role="give-usable" v-if="$h.t($h.gt(m.freeUsable, 0))">全选可用</button><button class="link small" type="button" data-role="give-none" v-if="!$h.t($h.lists.isEmpty(m.freeCodes))">全不选</button><span class="muted small" data-role="pick-count"></span></div>
                    <div class="pick-list" data-role="free-list" v-if="!$h.t($h.lists.isEmpty(m.freeCodes))"><template v-for="[L_c, L_cStat] in $h.each(m.freeCodes)"><template v-for="L_st in [$h.ui.codeStatus(L_c)]"><label :class="['pick-item', $h.av(($h.t($h.eq(L_st, '可用')) ? null : 'dim'))]" :data-usable.attr="$h.av($h.eq(L_st, '可用'))"><input type="checkbox" name="give" :value.attr="$h.ak($h.p(L_c, 'id'))"><span class="mono">{{ $h.s($h.p(L_c, 'code')) }}</span><small>{{ $h.s(($h.t($h.eq(L_st, '可用')) ? ($h.t($h.gt($h.p(L_c, 'maxUses'), 0)) ? $h.cat('已用 ', $h.elvis($h.p(L_c, 'usedCount'), 0), '/', $h.p(L_c, 'maxUses')) : '不限次数') : L_st)) }}</small></label></template></template></div>
                    <div class="pick-empty" v-if="$h.t($h.lists.isEmpty(m.freeCodes))">你手里的口令都分出去了</div>
                </div>
                <div class="field">
                    <div class="pick-head"><label>已经分给 TA 的</label><span class="muted small">勾上的收回到你手里</span><span class="muted small" data-role="take-count"></span></div>
                    <template v-for="[L_r, L_rStat] in $h.each(m.subs)"><div class="pick-list" :data-sub-list.attr="$h.av($h.p($h.p(L_r, 'agent'), 'id'))" hidden><template v-for="[L_c, L_cStat] in $h.each($h.p(L_r, 'codes'))"><template v-for="L_st in [$h.ui.codeStatus(L_c)]"><label class="pick-item take"><input type="checkbox" name="take" :value.attr="$h.ak($h.p(L_c, 'id'))"><span class="mono">{{ $h.s($h.p(L_c, 'code')) }}</span><small>{{ $h.s(($h.t($h.eq(L_st, '可用')) ? ($h.t($h.gt($h.p(L_c, 'maxUses'), 0)) ? $h.cat('已用 ', $h.elvis($h.p(L_c, 'usedCount'), 0), '/', $h.p(L_c, 'maxUses')) : '不限次数') : L_st)) }}</small></label></template></template></div></template>
                    <div class="pick-empty" data-role="mine-empty" hidden>还没有分给 TA 口令</div>
                </div>
                <p class="dlg-note">分出去的口令有过激活的话，TA 那边也会看到这些激活（按 TA 的扣量和数据更新方式算）。</p>
            </div>
            <div class="dlg-foot">
                <span class="hint" data-role="summary"></span>
                <button class="btn btn-secondary" type="button" data-close>取消</button>
                <button class="btn" type="submit" data-role="ok">保存</button>
            </div>
        </form>
    </dialog>

</main></template>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('二级代理'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
    css: ["app/ui.css"],
    js: ["app/ui.js"],
    theme: { attr: true, css: (m, c, $h) => m.themeCss }
};
</script>

<script setup>
import FAdminLayoutAgentbar from '../../frags/admin-layout--agentbar.vue';
import FAdminLayoutOverlays from '../../frags/admin-layout--overlays.vue';
defineProps({ m: Object, c: Object });
</script>
