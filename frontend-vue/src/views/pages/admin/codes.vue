<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'codes'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'口令管理'"/>
    <template v-for="L_canCodes in [$h.access.can('codes')]"><main class="content">

        <div class="page-head">
            <div class="page-title"><div>
                <h1>口令管理</h1>
                <p>{{ $h.s($h.cat(m.codeTotal, ' 个口令，', m.codeUsable, ' 个可用 · 所有 App 通用')) }}</p>
            </div></div>
            <div class="page-actions" v-if="$h.t(L_canCodes)">
                <button class="btn btn-secondary" type="button" data-open="batch-dialog"><svg class="i"><use href="#i-layers"></use></svg>批量添加</button>
                <button class="btn" type="button" data-open="code-dialog"><svg class="i"><use href="#i-plus"></use></svg>新建口令</button>
            </div>
        </div>

        <section class="card">
            <form class="toolbar" method="get" :action.attr="$h.ak('/admin/codes')">
                <div class="seg">
                    <a :href.attr="$h.ak($h.ui.url('/admin/codes', 'agent', m.agent, 'q', m.q))" :class="$h.av(($h.t($h.eq(m.status, 'all')) ? 'on' : null))">{{ $h.s($h.cat('全部 ', $h.c(m.counts, 'get', ['all']))) }}</a>
                    <a :href.attr="$h.ak($h.ui.url('/admin/codes', 'status', 'usable', 'agent', m.agent, 'q', m.q))" :class="$h.av(($h.t($h.eq(m.status, 'usable')) ? 'on' : null))">{{ $h.s($h.cat('可用 ', $h.c(m.counts, 'get', ['usable']))) }}</a>
                    <a :href.attr="$h.ak($h.ui.url('/admin/codes', 'status', 'exhausted', 'agent', m.agent, 'q', m.q))" :class="$h.av(($h.t($h.eq(m.status, 'exhausted')) ? 'on' : null))">{{ $h.s($h.cat('已用完 ', $h.c(m.counts, 'get', ['exhausted']))) }}</a>
                    <a :href.attr="$h.ak($h.ui.url('/admin/codes', 'status', 'disabled', 'agent', m.agent, 'q', m.q))" :class="$h.av(($h.t($h.eq(m.status, 'disabled')) ? 'on' : null))">{{ $h.s($h.cat('停用 ', $h.c(m.counts, 'get', ['disabled']))) }}</a>
                    <a :href.attr="$h.ak($h.ui.url('/admin/codes', 'status', 'expired', 'agent', m.agent, 'q', m.q))" :class="$h.av(($h.t($h.eq(m.status, 'expired')) ? 'on' : null))" v-if="$h.t(($h.t($h.gt($h.c(m.counts, 'get', ['expired']), 0)) || $h.t($h.eq(m.status, 'expired'))))">{{ $h.s($h.cat('已过期 ', $h.c(m.counts, 'get', ['expired']))) }}</a>
                </div>
                <input type="hidden" name="status" :value.attr="$h.ak(m.status)" v-if="!$h.t($h.eq(m.status, 'all'))">
                <select class="select" name="agent" data-autosubmit aria-label="按代理筛选" v-if="!$h.t($h.lists.isEmpty(m.agents))">
                    <option :value.attr="''">全部代理</option>
                    <option :value.attr="'none'" :selected.attr="$h.t($h.eq(m.agent, 'none')) ? 'selected' : null">未分配</option>
                    <template v-for="[L_a, L_aStat] in $h.each(m.agents)"><template v-if="L_aStat.index">
                    </template><option :value.attr="$h.ak($h.p(L_a, 'id'))" :selected.attr="$h.t($h.eq(m.agent, $h.add('', $h.p(L_a, 'id')))) ? 'selected' : null">{{ $h.s($h.p(L_a, 'name')) }}</option></template>
                </select>
                <label class="search">
                    <svg class="i"><use href="#i-search"></use></svg>
                    <input class="input" type="search" name="q" :value.attr="$h.ak(m.q)" placeholder="口令或备注" aria-label="搜索口令或备注">
                </label>
                <button class="btn btn-secondary" type="submit">筛选</button>
                <a class="link" :href.attr="$h.ak('/admin/codes')" v-if="$h.t(m.filtered)">清除筛选</a>
                <span class="tail muted">{{ $h.s($h.cat('共 ', $h.p(m.codes, 'totalElements'), ' 个')) }}</span>
            </form>

            <form class="bulkbar" method="post" :action.attr="$h.ak('/admin/code/bulk')" id="bulk-form" data-bulkbar hidden v-if="$h.t(L_canCodes)">
                <input type="hidden" name="back" :value.attr="$h.ak(m.back)">
                <span>已选 <b data-check-count>0</b> 个</span>
                <select class="select" name="action" aria-label="批量操作">
                    <option :value.attr="'enable'">启用</option>
                    <option :value.attr="'disable'">停用</option>
                    <option :value.attr="'assign'">分配给代理</option>
                    <option :value.attr="'expire'">改有效期</option>
                    <option :value.attr="'uses'">改可激活次数</option>
                    <option :value.attr="'remark'">改备注</option>
                    <option :value.attr="'delete'">删除</option>
                </select>
                <input class="input" type="date" name="expiresAt" data-role="bulk-expire" hidden disabled aria-label="有效期至，留空就是一直有效" title="留空就是一直有效">
                <span class="input-unit bulk-uses" data-role="bulk-uses" hidden><input class="input num" type="number" name="maxUses" min="0" max="100000" :value.attr="'0'" disabled aria-label="每个口令可激活几次，0 不限"><em>次</em></span>
                <input class="input" name="remark" maxlength="100" placeholder="新备注，留空就是清空" data-role="bulk-remark" hidden disabled aria-label="新备注">
                <select class="select" name="agentId" data-role="bulk-agent" hidden aria-label="分配给哪个代理">
                    <option :value.attr="''">取消分配</option>
                    <template v-for="[L_a, L_aStat] in $h.each(m.agents)"><template v-if="L_aStat.index">
                    </template><option :value.attr="$h.ak($h.p(L_a, 'id'))">{{ $h.s(($h.t($h.eq($h.p(L_a, 'enabled'), true)) ? $h.p(L_a, 'name') : $h.add($h.p(L_a, 'name'), '（已停用）'))) }}</option></template>
                </select>
                <button class="btn btn-sm" type="submit">执行</button>
                <button class="btn btn-secondary btn-sm" type="button" data-role="bulk-copy"><svg class="i i-sm"><use href="#i-copy"></use></svg>复制口令</button>
                <span class="muted small" data-role="bulk-hint">只作用于这一页勾选的口令</span>
            </form>

            <div class="empty" v-if="$h.t($h.eq($h.p(m.codes, 'totalElements'), 0))">
                <div class="empty-icon"><svg class="i"><use href="#i-key"></use></svg></div>
                <b>{{ $h.s(($h.t(m.filtered) ? '这个筛选下没有口令' : '还没有口令')) }}</b>
                <span>{{ $h.s(($h.t(m.filtered) ? '换个筛选条件，或者新建口令' : '新建口令分配给代理，用户在 App 里提交就能进 B 面')) }}</span>
                <div v-if="$h.t(L_canCodes)"><button class="btn" type="button" data-open="code-dialog"><svg class="i"><use href="#i-plus"></use></svg>新建口令</button></div>
            </div>
            <div class="table-wrap" data-guide="codes-table" v-if="!$h.t($h.eq($h.p(m.codes, 'totalElements'), 0))">
                <table id="code-table" class="compact">
                    <thead>
                    <tr>
                        <th class="check-col" v-if="$h.t(L_canCodes)"><input type="checkbox" data-check-all aria-label="全选这一页"></th>
                        <th>口令</th><th>代理</th><th>备注</th><th>有效激活</th><th>待确认</th><th>状态</th><th>最近使用</th><th>创建时间</th><th class="right">操作</th>
                    </tr>
                    </thead>
                    <tbody>
                    <template v-for="[L_c, L_cStat] in $h.each(m.codes)"><template v-if="L_cStat.index">
                    </template><template v-for="L_p in [$h.elvis($h.c(m.pending, 'get', [$h.p(L_c, 'id')]), 0)]"><template v-for="L_st in [$h.ui.codeStatus(L_c)]"><template v-for="L_last in [$h.c(m.lastUsed, 'get', [$h.p(L_c, 'id')])]"><template v-for="L_owner in [($h.t(!$h.eq($h.p(L_c, 'agentId'), null)) ? $h.c(m.agentNames, 'get', [$h.p(L_c, 'agentId')]) : null)]"><tr>
                        <td class="check-col" v-if="$h.t(L_canCodes)"><input type="checkbox" name="ids" :value.attr="$h.ak($h.p(L_c, 'id'))" form="bulk-form" data-check :data-code.attr="$h.av($h.p(L_c, 'code'))" :aria-label.attr="$h.av($h.add('选择口令 ', $h.p(L_c, 'code')))"></td>
                        <td>
                            <span class="cell-code">
                                <span class="mono strong" style="font-size:14.5px">{{ $h.s($h.p(L_c, 'code')) }}</span>
                                <button class="icon-btn" type="button" :data-copy.attr="$h.av($h.p(L_c, 'code'))" title="复制口令" aria-label="复制口令"><svg class="i i-sm"><use href="#i-copy"></use></svg></button>
                            </span>
                            <span class="muted-line" v-if="$h.t(!$h.eq($h.p(L_c, 'channel'), null))">{{ $h.s($h.add('绑定渠道 ', $h.p(L_c, 'channel'))) }}</span>
                        </td>
                        <td class="nowrap">
                            <a class="link-strong" :href.attr="$h.ak($h.link('/admin/codes', [['agent', $h.p(L_c, 'agentId')]]))" v-if="$h.t(!$h.eq(L_owner, null))">{{ $h.s(L_owner) }}</a><small class="muted sub-owner" :title.attr="$h.av($h.add($h.add('分给了二级代理 ', $h.c(m.agentNames, 'get', [$h.p(L_c, 'subAgentId')])), '，激活仍算在上级名下'))" v-if="$h.t(($h.t(!$h.eq(L_owner, null)) &amp;&amp; $h.t(!$h.eq($h.p(L_c, 'subAgentId'), null))))">{{ $h.s($h.add(' › ', $h.c(m.agentNames, 'get', [$h.p(L_c, 'subAgentId')]))) }}</small>
                            <span class="muted" v-if="$h.t($h.eq(L_owner, null))">未分配</span>
                        </td>
                        <td class="sub cell-clip" :title.attr="$h.av($h.p(L_c, 'remark'))">{{ $h.s(($h.t($h.strings.isEmpty($h.p(L_c, 'remark'))) ? '—' : $h.p(L_c, 'remark'))) }}</td>
                        <td>
                            <div class="usage">
                                <span class="num"><b>{{ $h.s($h.p(L_c, 'usedCount')) }}</b><span class="muted">{{ $h.s(($h.t($h.gt($h.p(L_c, 'maxUses'), 0)) ? $h.add(' / ', $h.p(L_c, 'maxUses')) : ' / 不限')) }}</span></span>
                                <span class="usage-bar" v-if="$h.t($h.gt($h.p(L_c, 'maxUses'), 0))"><span :style="$h.av($h.cat('width:', $h.ui.percent(L_c), '%'))"></span></span>
                            </div>
                            <span class="muted-line" title="扣量以后代理看到的次数，次数上限按它算" v-if="$h.t(($h.t(!$h.eq($h.p(L_c, 'seenCount'), null)) &amp;&amp; $h.t(!$h.eq($h.p(L_c, 'seenCount'), $h.p(L_c, 'usedCount')))))">{{ $h.s($h.cat('代理看到 ', $h.p(L_c, 'seenCount'))) }}</span>
                        </td>
                        <td class="num">
                            <a class="link-strong" :href.attr="$h.ak($h.link('/admin/activations', [['status', 'pending'], ['code', $h.p(L_c, 'code')]]))" v-if="$h.t($h.gt(L_p, 0))">{{ $h.s(L_p) }}</a>
                            <span class="muted" v-if="!$h.t($h.gt(L_p, 0))">—</span>
                        </td>
                        <td class="nowrap">
                            <span :class="['badge', $h.av(($h.t($h.eq(L_st, '可用')) ? 'badge-solid' : 'badge-line'))]"><span class="dot"></span><span>{{ $h.s(L_st) }}</span></span>
                            <span class="muted-line" :title.attr="$h.av($h.ui.full($h.p(L_c, 'expiresAt')))" v-if="$h.t(($h.t(!$h.eq($h.p(L_c, 'expiresAt'), null)) &amp;&amp; $h.t(!$h.eq(L_st, '已过期'))))">{{ $h.s($h.add('到期 ', $h.ui.date($h.p(L_c, 'expiresAt')))) }}</span>
                        </td>
                        <td class="muted nowrap" :title.attr="$h.av($h.ui.full(L_last))">{{ $h.s($h.ui.ago(L_last)) }}</td>
                        <td class="muted nowrap" :title.attr="$h.av($h.ui.full($h.p(L_c, 'createdAt')))">{{ $h.s($h.ui.dt($h.p(L_c, 'createdAt'))) }}</td>
                        <td class="actions">
                            <div class="btn-row">
                                <a class="btn btn-ghost btn-sm" :href.attr="$h.ak($h.link('/admin/activations', [['code', $h.p(L_c, 'code')]]))">记录</a>
                                <a class="btn btn-ghost btn-sm" :href.attr="$h.ak($h.link('/admin/agents/{id}/active', [['id', $h.p(L_c, 'agentId')], ['code', $h.p(L_c, 'id')]]))" title="这个口令激活的设备每天有多少在用、留住了多少" v-if="$h.t(($h.t(!$h.eq($h.p(L_c, 'agentId'), null)) &amp;&amp; $h.t($h.access.agents())))">日活</a>
                                <form method="post" :action.attr="$h.ak('/admin/code/toggle')" v-if="$h.t(L_canCodes)">
                                    <input type="hidden" name="id" :value.attr="$h.ak($h.p(L_c, 'id'))">
                                    <input type="hidden" name="back" :value.attr="$h.ak(m.back)">
                                    <button class="btn btn-ghost btn-sm" type="submit">{{ $h.s(($h.t($h.p(L_c, 'enabled')) ? '停用' : '启用')) }}</button>
                                </form>
                                <button class="icon-btn" type="button" title="编辑口令" aria-label="编辑口令" data-code-edit :data-id.attr="$h.av($h.p(L_c, 'id'))" :data-code.attr="$h.av($h.p(L_c, 'code'))" :data-remark.attr="$h.av($h.p(L_c, 'remark'))" :data-max.attr="$h.av($h.p(L_c, 'maxUses'))" :data-agent.attr="$h.av($h.p(L_c, 'agentId'))" :data-channel.attr="$h.av($h.p(L_c, 'channel'))" :data-expires.attr="$h.av($h.ui.date($h.p(L_c, 'expiresAt')))" v-if="$h.t(L_canCodes)">
                                    <svg class="i"><use href="#i-edit"></use></svg>
                                </button>
                                <form method="post" :action.attr="$h.ak('/admin/code/delete')" :data-confirm.attr="$h.av($h.cat('删除口令 ', $h.p(L_c, 'code'), '？'))" :data-confirm-body.attr="$h.av(($h.t(!$h.eq(L_owner, null)) ? $h.add($h.add('删除后该口令无法再提交，代理 ', L_owner), ' 也将看不到它的数据。如只是暂停使用，建议改为停用。') : '删除后该口令无法再提交，已有的激活记录会保留。'))" data-confirm-ok="删除口令" data-confirm-danger v-if="$h.t(L_canCodes)">
                                    <input type="hidden" name="id" :value.attr="$h.ak($h.p(L_c, 'id'))">
                                    <input type="hidden" name="back" :value.attr="$h.ak(m.back)">
                                    <button class="icon-btn" type="submit" title="删除口令" aria-label="删除口令"><svg class="i"><use href="#i-trash"></use></svg></button>
                                </form>
                            </div>
                        </td>
                    </tr></template></template></template></template></template>
                    </tbody>
                </table>
            </div>

            <FAdminLayoutPager :m="m" :c="c" :P_p="m.codes" :P_base="$h.ui.url('/admin/codes', 'agent', m.agent, 'status', m.status, 'q', m.q)"/>
        </section>

        <dialog id="code-dialog" data-hash="new">
            <form method="post" :action.attr="$h.ak('/admin/code/create')">
                <div class="dlg-head">
                    <h3>新建口令</h3>
                    <p>B 面上线模式下，用户在 App 里提交这个口令就能进 B 面。</p>
                </div>
                <div class="dlg-body">
                    <div class="field">
                        <label for="code-input">口令</label>
                        <div class="input-group">
                            <input class="input mono" id="code-input" name="code" placeholder="例如 88888" autocomplete="off" required maxlength="64">
                            <button class="btn btn-secondary" type="button" data-random="#code-input">随机生成</button>
                        </div>
                    </div>
                    <div class="field">
                        <label for="code-agent">所属代理</label>
                        <select class="select" id="code-agent" name="agentId" style="height:38px">
                            <option :value.attr="''">不分配</option>
                            <template v-for="[L_a, L_aStat] in $h.each(m.agents)"><template v-if="L_aStat.index">
                            </template><option :value.attr="$h.ak($h.p(L_a, 'id'))" :selected.attr="$h.t($h.eq(m.agent, $h.add('', $h.p(L_a, 'id')))) ? 'selected' : null">{{ $h.s(($h.t($h.eq($h.p(L_a, 'enabled'), true)) ? $h.p(L_a, 'name') : $h.add($h.p(L_a, 'name'), '（已停用）'))) }}</option></template>
                        </select>
                        <span class="hint">{{ $h.s(($h.t($h.lists.isEmpty(m.agents)) ? '还没有代理，可以先在「代理管理」里新建' : '代理登录后只能看到归属于自己的口令数据')) }}</span>
                    </div>
                    <div class="field">
                        <label for="code-channel">绑定渠道</label>
                        <select class="select" id="code-channel" name="channel" style="height:38px">
                            <option :value.attr="''">不绑定</option>
                            <template v-for="[L_ch, L_chStat] in $h.each(m.channels)"><template v-if="L_chStat.index">
                            </template><option :value.attr="$h.ak($h.p(L_ch, 'name'))">{{ $h.s($h.p(L_ch, 'name')) }}</option></template>
                        </select>
                        <span class="hint">兑换成功后 App 改用这个渠道检查更新；哪个 App 没有这个渠道，就照常用原来的</span>
                    </div>
                    <div class="row-2">
                        <div class="field">
                            <label for="code-max">使用上限</label>
                            <input class="input" id="code-max" name="maxUses" type="number" min="0" :value.attr="'0'">
                            <span class="hint">最多可激活的设备数，0 表示不限</span>
                        </div>
                        <div class="field">
                            <label for="code-remark">备注</label>
                            <input class="input" id="code-remark" name="remark" placeholder="仅后台可见" maxlength="100">
                        </div>
                    </div>
                    <div class="field">
                        <label for="code-expires">有效期至</label>
                        <input class="input" id="code-expires" name="expires" type="date">
                        <span class="hint">可不填，不填就一直有效；到期当天 24 点后不能再兑换</span>
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">创建口令</button>
                </div>
            </form>
        </dialog>

        <dialog id="code-edit-dialog">
            <form method="post" :action.attr="$h.ak('/admin/code/update')" id="code-edit-form">
                <input type="hidden" name="id">
                <input type="hidden" name="back" :value.attr="$h.ak(m.back)">
                <div class="dlg-head">
                    <h3>编辑口令 <span class="mono" data-role="code"></span></h3>
                    <p>口令本身不能改。换了代理，新代理能看到它的全部数据。</p>
                </div>
                <div class="dlg-body">
                    <div class="field">
                        <label for="edit-agent">所属代理</label>
                        <select class="select" id="edit-agent" name="agentId" style="height:38px">
                            <option :value.attr="''">不分配</option>
                            <template v-for="[L_a, L_aStat] in $h.each(m.agents)"><template v-if="L_aStat.index">
                            </template><option :value.attr="$h.ak($h.p(L_a, 'id'))">{{ $h.s(($h.t($h.eq($h.p(L_a, 'enabled'), true)) ? $h.p(L_a, 'name') : $h.add($h.p(L_a, 'name'), '（已停用）'))) }}</option></template>
                        </select>
                    </div>
                    <div class="field">
                        <label for="edit-channel">绑定渠道</label>
                        <select class="select" id="edit-channel" name="channel" style="height:38px">
                            <option :value.attr="''">不绑定</option>
                            <template v-for="[L_ch, L_chStat] in $h.each(m.channels)"><template v-if="L_chStat.index">
                            </template><option :value.attr="$h.ak($h.p(L_ch, 'name'))">{{ $h.s($h.p(L_ch, 'name')) }}</option></template>
                        </select>
                        <span class="hint">兑换成功后 App 改用这个渠道检查更新；哪个 App 没有这个渠道，就照常用原来的</span>
                    </div>
                    <div class="row-2">
                        <div class="field">
                            <label for="edit-max">使用上限</label>
                            <input class="input" id="edit-max" name="maxUses" type="number" min="0">
                            <span class="hint">0 表示不限</span>
                        </div>
                        <div class="field">
                            <label for="edit-remark">备注</label>
                            <input class="input" id="edit-remark" name="remark" maxlength="100">
                        </div>
                    </div>
                    <div class="field">
                        <label for="edit-expires">有效期至</label>
                        <input class="input" id="edit-expires" name="expires" type="date">
                        <span class="hint">清空就一直有效；已经激活的设备不受影响</span>
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">保存</button>
                </div>
            </form>
        </dialog>

        <dialog id="batch-dialog" class="wide" data-hash="batch">
            <form method="post" :action.attr="$h.ak('/admin/code/batch')" id="batch-form" :data-generate.attr="$h.av('/admin/code/batch')" :data-import.attr="$h.av('/admin/code/import')">
                <div class="dlg-head">
                    <h3>批量添加口令</h3>
                    <div class="seg" style="margin-top:12px">
                        <a href="#" class="on" data-batch-mode="generate">随机生成</a>
                        <a href="#" data-batch-mode="import">导入已有口令</a>
                    </div>
                </div>
                <div class="dlg-body">
                    <div data-batch-panel="generate">
                        <p class="dlg-note">前缀加随机数字，不会和已有口令重复。选了代理就直接归属 TA。</p>
                        <div class="row-2" style="grid-template-columns:repeat(3,minmax(0,1fr))">
                            <div class="field">
                                <label for="batch-count">数量</label>
                                <input class="input" id="batch-count" name="count" type="number" min="1" max="1000" :value.attr="'10'" required>
                            </div>
                            <div class="field">
                                <label for="batch-length">数字位数</label>
                                <input class="input" id="batch-length" name="length" type="number" min="4" max="16" :value.attr="'6'" required>
                            </div>
                            <div class="field">
                                <label for="batch-prefix">前缀</label>
                                <input class="input mono" id="batch-prefix" name="prefix" maxlength="16" placeholder="可不填，例如 VIP" pattern="[A-Za-z0-9_\-]{0,16}">
                            </div>
                        </div>
                    </div>
                    <div data-batch-panel="import" hidden>
                        <div class="field">
                            <label for="batch-text">口令列表</label>
                            <textarea class="input mono" id="batch-text" name="text" rows="6" style="height:auto;padding:10px 12px;resize:vertical" placeholder="每行一个，也可以用逗号或空格分开&#10;VIP0001&#10;VIP0002"></textarea>
                            <span class="hint">一次最多 1000 个，每个最多 64 位；已经有的口令会跳过</span>
                        </div>
                    </div>
                    <div class="row-2">
                        <div class="field">
                            <label for="batch-agent">所属代理</label>
                            <select class="select" id="batch-agent" name="agentId" style="height:38px">
                                <option :value.attr="''">不分配</option>
                                <template v-for="[L_a, L_aStat] in $h.each(m.agents)"><template v-if="L_aStat.index">
                                </template><option :value.attr="$h.ak($h.p(L_a, 'id'))" :selected.attr="$h.t($h.eq(m.agent, $h.add('', $h.p(L_a, 'id')))) ? 'selected' : null">{{ $h.s(($h.t($h.eq($h.p(L_a, 'enabled'), true)) ? $h.p(L_a, 'name') : $h.add($h.p(L_a, 'name'), '（已停用）'))) }}</option></template>
                            </select>
                        </div>
                        <div class="field">
                            <label for="batch-max">每个的使用上限</label>
                            <input class="input" id="batch-max" name="maxUses" type="number" min="0" :value.attr="'1'">
                            <span class="hint">最多激活几台设备，0 表示不限</span>
                        </div>
                    </div>
                    <div class="row-2">
                        <div class="field">
                            <label for="batch-channel">绑定渠道</label>
                            <select class="select" id="batch-channel" name="channel" style="height:38px">
                                <option :value.attr="''">不绑定</option>
                                <template v-for="[L_ch, L_chStat] in $h.each(m.channels)"><template v-if="L_chStat.index">
                                </template><option :value.attr="$h.ak($h.p(L_ch, 'name'))">{{ $h.s($h.p(L_ch, 'name')) }}</option></template>
                            </select>
                        </div>
                        <div class="field">
                            <label for="batch-expires">有效期至</label>
                            <input class="input" id="batch-expires" name="expires" type="date">
                            <span class="hint">可不填，不填就一直有效</span>
                        </div>
                    </div>
                    <div class="field">
                        <label for="batch-remark">备注</label>
                        <input class="input" id="batch-remark" name="remark" placeholder="仅后台可见，例如 张三 11 月第一批" maxlength="100">
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit" data-role="submit">生成</button>
                </div>
            </form>
        </dialog>

    </main></template>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('口令管理'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
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
