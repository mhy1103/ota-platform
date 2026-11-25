<template>
<FAdminLayoutAgentbar :m="m" :c="c" :P_active="'requests'"/>
<template v-for="L_auto in [$h.eq($h.p(m.agent, 'requestMode'), 'AUTO')]"><main class="content ap req-page">

    <div class="page-head">
        <div class="page-title"><div>
            <h1>申请口令</h1>
            <p>{{ $h.s(($h.t(L_auto) ? '随机生成或填自己想要的口令，提交后马上生成' : '随机生成或填自己想要的口令，管理员同意后生成')) }}</p>
        </div></div>
    </div>

    <div class="req-layout">
        <section class="card req-new">
            <form method="post" :action.attr="$h.ak('/agent/requests')" id="request-form" :data-remaining.attr="$h.av(m.remaining)" :data-auto.attr="$h.av(L_auto)">
                <div class="req-step">
                    <div class="req-step-title"><span class="req-no">1</span>怎么生成</div>
                    <div class="req-modes">
                        <label class="req-mode"><input type="radio" name="mode" :value.attr="'random'" :checked.attr="''">
                            <span class="rm-box"><svg class="i rm-icon"><use href="#i-sparkle"></use></svg><span class="rm-text"><b>随机生成</b><small>填个数，口令是随机 8 位数字，最省事</small></span><i class="rm-tick"></i></span></label>
                        <label class="req-mode"><input type="radio" name="mode" :value.attr="'custom'">
                            <span class="rm-box"><svg class="i rm-icon"><use href="#i-edit"></use></svg><span class="rm-text"><b>自定义口令</b><small>填自己想要的，比如 88888、VIP2026，已经有的会自动拒绝</small></span><i class="rm-tick"></i></span></label>
                    </div>
                </div>

                <div class="req-step">
                    <div class="req-step-title"><span class="req-no">2</span>要多少</div>
                    <div class="req-form">
                        <div class="field" data-mode-field="random">
                            <label for="req-count">个数</label>
                            <div class="stepper">
                                <button type="button" class="st-btn" data-step="req-count" data-delta="-1" aria-label="减少">−</button>
                                <input class="input num" id="req-count" name="count" type="number" min="1" :max.attr="$h.av(($h.t(($h.t($h.ge(m.remaining, 0)) &amp;&amp; $h.t($h.lt(m.remaining, m.maxPerRequest)))) ? m.remaining : m.maxPerRequest))" :value.attr="'10'" required>
                                <button type="button" class="st-btn" data-step="req-count" data-delta="1" aria-label="增加">+</button>
                            </div>
                            <div class="quick-picks"><template v-for="L_q in [10, 20, 50, 100]"><button type="button" data-set="req-count" :data-v.attr="$h.av(L_q)">{{ $h.s(L_q) }}</button></template></div>
                            <span class="hint">{{ $h.s($h.cat('一次最多 ', m.maxPerRequest, ' 个')) }}</span>
                        </div>
                        <div class="field">
                            <label for="req-max">每个口令可激活</label>
                            <div class="stepper">
                                <button type="button" class="st-btn" data-step="req-max" data-delta="-1" aria-label="减少">−</button>
                                <input class="input num" id="req-max" name="maxUses" type="number" min="0" max="100000" :value.attr="'0'" required>
                                <button type="button" class="st-btn" data-step="req-max" data-delta="1" aria-label="增加">+</button>
                            </div>
                            <div class="quick-picks"><button type="button" data-set="req-max" data-v="0">不限</button><template v-for="L_q in [1, 5, 10]"><button type="button" data-set="req-max" :data-v.attr="$h.av(L_q)">{{ $h.s($h.cat(L_q, ' 次')) }}</button></template></div>
                            <span class="hint">一个口令能给几台设备用，0 表示不限</span>
                        </div>
                    </div>
                    <div class="field" data-mode-field="custom" hidden>
                        <label for="req-codes">要哪些口令 <span class="muted small" data-role="code-count"></span></label>
                        <textarea class="input mono req-codes-input" id="req-codes" name="codes" rows="5" placeholder="一行一个，也可以用空格、逗号隔开&#10;例如：&#10;88888&#10;VIP2026" spellcheck="false" disabled required></textarea>
                        <div class="code-preview" data-role="code-preview" hidden></div>
                        <span class="hint">字母、数字、_ 和 -，2–32 位，一次最多 500 个，重复的会自动拒绝</span>
                    </div>
                </div>

                <div class="req-step">
                    <div class="req-step-title"><span class="req-no">3</span>用途<small>可不填，方便以后对账</small></div>
                    <input class="input" id="req-note" name="note" maxlength="200" placeholder="例如：抖音直播间、老客户">
                </div>

                <div class="req-submit">
                    <div class="req-summary" data-role="req-summary"></div>
                    <button class="btn btn-lg" type="submit" :disabled.attr="$h.t(($h.t(m.preview) || $h.t($h.eq(m.remaining, 0)))) ? 'disabled' : null">{{ $h.s(($h.t(L_auto) ? '申请并生成' : '提交申请')) }}</button>
                </div>
                <p class="req-foot">{{ $h.s(($h.t(m.preview) ? '预览中不能提交' : ($h.t($h.eq(m.remaining, 0)) ? '今天的申请额度用完了，明天再来' : ($h.t(L_auto) ? '你的账号是自动通过，提交后马上生成，口令在「数据概览」里' : '提交后等管理员审核，结果会显示在右边的申请记录里')))) }}</p>
            </form>
        </section>

        <aside class="req-side">
            <section class="card quota-card">
                <div class="quota-head"><span>今天的额度</span><b v-if="$h.t($h.lt(m.remaining, 0))">不限</b></div>
                <template v-if="$h.t($h.ge(m.remaining, 0))">
                    <div class="quota-num"><b>{{ $h.s(m.remaining) }}</b><span>{{ $h.s($h.cat('/ ', $h.p(m.agent, 'dailyRequestLimit'), ' 个还能申请')) }}</span></div>
                    <div class="quota-bar"><i :style="$h.av($h.cat('width:', $h.ui.pctOf(m.requestedToday, $h.p(m.agent, 'dailyRequestLimit')), '%'))"></i></div>
                </template>
                <div class="quota-num" v-if="$h.t($h.lt(m.remaining, 0))"><b>{{ $h.s(m.requestedToday) }}</b><span>个今天已申请</span></div>
                <p class="quota-line">今天已申请 <b>{{ $h.s(m.requestedToday) }}</b> 个 · {{ $h.s(($h.t($h.lt(m.remaining, 0)) ? '每天不限' : $h.cat('还能申请 ', m.remaining, ' 个（每天最多 ', $h.p(m.agent, 'dailyRequestLimit'), ' 个）'))) }}</p>
                <ul class="quota-facts">
                    <li><span>审核方式</span><b>{{ $h.s(($h.t(L_auto) ? '自动通过' : '管理员审核')) }}</b></li>
                    <li><span>等审核的</span><b :class="$h.av(($h.t($h.gt(m.pendingRequests, 0)) ? 'warn-text' : null))">{{ $h.s($h.cat(m.pendingRequests, ' 条')) }}</b></li>
                </ul>
            </section>
            <section class="card tips-card">
                <b>小提示</b>
                <ul>
                    <li>随机口令是 8 位数字，不会和别人的重复</li>
                    <li>自定义口令先到先得，已经有人用的会被拒绝</li>
                    <li>{{ $h.s(($h.t(L_auto) ? '生成的口令会出现在「数据概览」的我的口令里' : '管理员同意后，口令出现在「数据概览」的我的口令里')) }}</li>
                </ul>
            </section>
        </aside>
    </div>

    <section class="card req-history">
        <div class="card-head">
            <h2>申请记录</h2>
            <span class="muted small">{{ $h.s(($h.t($h.gt(m.pendingRequests, 0)) ? $h.cat(m.pendingRequests, ' 条待审核 · 最近 100 条') : '最近 100 条')) }}</span>
            <div class="seg seg-sm tail" data-req-filter v-if="!$h.t($h.lists.isEmpty(m.requests))">
                <button type="button" class="on" data-f="all">全部</button>
                <button type="button" data-f="PENDING">待审核</button>
                <button type="button" data-f="APPROVED">已通过</button>
                <button type="button" data-f="REJECTED">没通过</button>
            </div>
        </div>
        <div class="empty" v-if="$h.t($h.lists.isEmpty(m.requests))">
            <div class="empty-icon"><svg class="i"><use href="#i-inbox"></use></svg></div>
            <b>还没有申请过</b>提交的申请会出现在这里
        </div>
        <ul class="req-list" v-if="!$h.t($h.lists.isEmpty(m.requests))"><template v-for="[L_q, L_qStat] in $h.each(m.requests)"><li :class="['req-item', $h.av($h.strings.toLowerCase($h.p(L_q, 'status')))]" :data-status.attr="$h.av($h.p(L_q, 'status'))">
            <span class="ri-dot"></span>
            <div class="ri-main">
                <div class="ri-head">
                    <b>{{ $h.s(($h.t($h.p(L_q, 'custom')) ? $h.cat('自定义 ', $h.lists.size($h.p(L_q, 'items')), ' 个') : $h.cat('随机 ', $h.p(L_q, 'count'), ' 个'))) }}</b>
                    <span class="ri-sub">{{ $h.s($h.cat(($h.t($h.gt($h.p(L_q, 'maxUses'), 0)) ? $h.cat('每个可激活 ', $h.p(L_q, 'maxUses'), ' 次') : '不限次数'), ($h.t($h.strings.isEmpty($h.p(L_q, 'note'))) ? '' : $h.cat(' · ', $h.p(L_q, 'note'))))) }}</span>
                    <span :class="['code-st', $h.av($h.strings.toLowerCase($h.p(L_q, 'status')))]">{{ $h.s($h.p(L_q, 'statusLabel')) }}</span>
                </div>
                <div class="ri-codes" v-if="$h.t($h.p(L_q, 'custom'))"><template v-for="[L_it, L_itStat] in $h.each($h.p(L_q, 'items'))"><span :class="['code-chip', $h.av($h.strings.toLowerCase($h.p(L_it, 'status')))]" :title.attr="$h.av(($h.t($h.eq($h.p(L_it, 'status'), 'APPROVED')) ? '已生成' : ($h.t($h.eq($h.p(L_it, 'status'), 'PENDING')) ? '等审核' : $h.elvis($h.p(L_it, 'reason'), '没通过'))))">{{ $h.s($h.p(L_it, 'code')) }}<small v-if="$h.t($h.eq($h.p(L_it, 'status'), 'REJECTED'))">{{ $h.s($h.elvis($h.p(L_it, 'reason'), '没通过')) }}</small></span></template></div>
                <div class="ri-result">
                    <span v-if="$h.t($h.eq($h.p(L_q, 'status'), 'APPROVED'))"><svg class="i i-sm"><use href="#i-tick"></use></svg>{{ $h.s($h.cat('已生成 ', $h.p(L_q, 'codeCount'), ' 个口令', ($h.t(!$h.eq($h.p(L_q, 'reviewedAt'), null)) ? $h.cat(' · ', $h.ui.dt($h.p(L_q, 'reviewedAt'))) : ''))) }}</span>
                    <span class="muted" v-if="$h.t($h.eq($h.p(L_q, 'status'), 'REJECTED'))">{{ $h.s(($h.t($h.strings.isEmpty($h.p(L_q, 'reviewNote'))) ? '管理员没写原因' : $h.cat('原因：', $h.p(L_q, 'reviewNote')))) }}</span>
                    <span class="muted" v-if="$h.t($h.eq($h.p(L_q, 'status'), 'PENDING'))">等管理员审核</span>
                </div>
            </div>
            <div class="ri-side">
                <span class="ri-time" :title.attr="$h.av($h.ui.full($h.p(L_q, 'createdAt')))">{{ $h.s($h.cat('#', $h.p(L_q, 'id'), ' · ', $h.ui.ago($h.p(L_q, 'createdAt')))) }}</span>
                <form method="post" :action.attr="$h.ak($h.link('/agent/requests/{id}/cancel', [['id', $h.p(L_q, 'id')]]))" :data-confirm.attr="$h.av($h.cat('撤回申请 #', $h.p(L_q, 'id'), '？'))" data-confirm-body="撤回后可以重新申请。" data-confirm-ok="撤回" v-if="$h.t(($h.t($h.eq($h.p(L_q, 'status'), 'PENDING')) &amp;&amp; $h.t(!$h.t(m.preview))))">
                    <button class="btn btn-ghost btn-sm" type="submit">撤回</button>
                </form>
            </div>
        </li></template></ul>
        <div class="list-none" data-req-none hidden>没有这种状态的申请</div>
    </section>

</main></template>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('申请口令'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
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
