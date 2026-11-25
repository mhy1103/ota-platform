<template>
<FAdminLayoutAgentbar :m="m" :c="c" :P_active="'notices'"/>
<main class="content ap ap-notices">

    <div class="page-head">
        <div class="page-title"><div>
            <h1>公告</h1>
            <p>{{ $h.s(($h.t($h.p(m.agent, 'sub')) ? '管理员和上级代理发给你的通知' : ($h.t(m.canSend) ? '管理员发的通知；你也可以给自己的二级代理发公告' : '管理员发的活动、规则和维护通知'))) }}</p>
        </div></div>
        <div class="page-actions" v-if="$h.t(($h.t(m.canSend) &amp;&amp; $h.t(!$h.t(m.preview))))">
            <button class="btn" type="button" data-own-notice-new><svg class="i"><use href="#i-plus"></use></svg>给二级代理发公告</button>
        </div>
    </div>

    <nav class="tabs" data-tabs v-if="$h.t(m.canSend)">
        <a href="#inbox" data-tab="inbox">收到的 <span class="count" v-if="!$h.t($h.lists.isEmpty(m.notices))">{{ $h.s($h.lists.size(m.notices)) }}</span></a>
        <a href="#sent" data-tab="sent">发给二级代理 <span class="count" v-if="!$h.t($h.lists.isEmpty(m.sent))">{{ $h.s($h.lists.size(m.sent)) }}</span></a>
    </nav>

    <section data-tab-panel="inbox">
        <section class="card">
            <div class="empty" v-if="$h.t($h.lists.isEmpty(m.notices))">
                <div class="empty-icon"><svg class="i"><use href="#i-megaphone"></use></svg></div>
                <b>还没有公告</b>{{ $h.s(($h.t($h.p(m.agent, 'sub')) ? '管理员或上级代理发了公告，这里和概览顶部都会提醒你' : '有新的公告时，概览顶部也会提醒你')) }}
            </div>
            <ul class="notice-list agent-notices" v-if="!$h.t($h.lists.isEmpty(m.notices))"><template v-for="[L_n, L_nStat] in $h.each(m.notices)"><li :class="['notice-item', $h.av(($h.t($h.z(L_n, 'pinned')) ? 'pinned' : null)), $h.av(($h.t($h.z(L_n, 'important')) ? 'important' : null)), $h.av(($h.t($h.z(L_n, 'read')) ? null : 'unread'))]">
                <div class="ni-head">
                    <b>{{ $h.s($h.z(L_n, 'title')) }}</b>
                    <span class="mini-tag on" v-if="!$h.t($h.z(L_n, 'read'))">新</span>
                    <span class="mini-tag" v-if="$h.t($h.z(L_n, 'pinned'))"><svg class="i i-xs"><use href="#i-pin"></use></svg>置顶</span>
                    <span class="mini-tag warn" v-if="$h.t($h.z(L_n, 'important'))">重要</span>
                    <span class="mini-tag from-tag" v-if="$h.t($h.z(L_n, 'fromParent'))"><svg class="i i-xs"><use href="#i-users"></use></svg>上级代理</span>
                    <span class="ni-time" :title.attr="$h.av($h.ui.full($h.z(L_n, 'at')))">{{ $h.s($h.ui.ago($h.z(L_n, 'at'))) }}</span>
                </div>
                <p class="ni-body full">{{ $h.s($h.z(L_n, 'body')) }}</p>
            </li></template></ul>
        </section>
    </section>

    <section data-tab-panel="sent" hidden v-if="$h.t(m.canSend)">
        <section class="card">
            <div class="card-head">
                <h2>发给二级代理的公告</h2>
                <span class="muted small">{{ $h.s(($h.t($h.gt(m.subCount, 0)) ? '只有你自己的二级代理看得到，这里能看到多少人看过（停用的不算）' : '开了二级代理以后，TA 们登录就能看到')) }}</span>
            </div>
            <div class="empty" v-if="$h.t($h.lists.isEmpty(m.sent))">
                <div class="empty-icon"><svg class="i"><use href="#i-megaphone"></use></svg></div>
                <b>还没有发过</b>口令怎么用、结算时间、活动安排都可以发给二级代理，「重要」的会在 TA 的概览顶部提醒
                <div v-if="!$h.t(m.preview)"><button class="btn" type="button" data-own-notice-new><svg class="i"><use href="#i-plus"></use></svg>给二级代理发公告</button></div>
            </div>
            <ul class="notice-list" v-if="!$h.t($h.lists.isEmpty(m.sent))"><template v-for="[L_v, L_vStat] in $h.each(m.sent)"><template v-for="L_n in [$h.z(L_v, 'notice')]"><li :class="['notice-item', $h.av(($h.t($h.p(L_n, 'pinned')) ? 'pinned' : null)), $h.av(($h.t($h.p(L_n, 'important')) ? 'important' : null))]">
                <div class="ni-head">
                    <b>{{ $h.s($h.p(L_n, 'title')) }}</b>
                    <span class="mini-tag" v-if="$h.t($h.p(L_n, 'pinned'))"><svg class="i i-xs"><use href="#i-pin"></use></svg>置顶</span>
                    <span class="mini-tag warn" v-if="$h.t($h.p(L_n, 'important'))">重要</span>
                </div>
                <p class="ni-body">{{ $h.s($h.p(L_n, 'body')) }}</p>
                <div class="ni-foot">
                    <span class="ni-meta" :title.attr="$h.av($h.ui.full($h.p(L_n, 'createdAt')))">{{ $h.s($h.cat($h.ui.ago($h.p(L_n, 'createdAt')), ($h.t($h.eq($h.p(L_n, 'updatedAt'), null)) ? '' : ' · 修改过'))) }}</span>
                    <span class="ni-read" :title.attr="$h.av($h.cat($h.z(L_v, 'read'), ' / ', $h.z(L_v, 'audience'), ' 个启用中的二级代理看过'))"><span class="read-bar"><i :style="$h.av($h.cat('width:', $h.ui.pctOf($h.z(L_v, 'read'), $h.z(L_v, 'audience')), '%'))"></i></span>{{ $h.s($h.cat($h.z(L_v, 'read'), ' / ', $h.z(L_v, 'audience'), ' 已读')) }}</span>
                    <div class="btn-row" v-if="!$h.t(m.preview)">
                        <form method="post" :action.attr="$h.ak($h.link('/agent/notices/{id}/pin', [['id', $h.p(L_n, 'id')]]))"><button class="btn btn-ghost btn-sm" type="submit">{{ $h.s(($h.t($h.p(L_n, 'pinned')) ? '取消置顶' : '置顶')) }}</button></form>
                        <button class="icon-btn" type="button" title="编辑公告" aria-label="编辑公告" data-own-notice-edit :data-id.attr="$h.av($h.p(L_n, 'id'))" :data-title.attr="$h.av($h.p(L_n, 'title'))" :data-body.attr="$h.av($h.p(L_n, 'body'))" :data-pinned.attr="$h.av($h.p(L_n, 'pinned'))" :data-level.attr="$h.av($h.p(L_n, 'level'))"><svg class="i"><use href="#i-edit"></use></svg></button>
                        <form method="post" :action.attr="$h.ak($h.link('/agent/notices/{id}/delete', [['id', $h.p(L_n, 'id')]]))" :data-confirm.attr="$h.av($h.cat('删除公告「', $h.p(L_n, 'title'), '」？'))" data-confirm-body="二级代理那边也不再显示。" data-confirm-ok="删除" data-confirm-danger><button class="icon-btn" type="submit" title="删除公告" aria-label="删除公告"><svg class="i"><use href="#i-trash"></use></svg></button></form>
                    </div>
                </div>
            </li></template></template></ul>
        </section>
    </section>

    <dialog id="own-notice-dialog" class="wide" v-if="$h.t(($h.t(m.canSend) &amp;&amp; $h.t(!$h.t(m.preview))))">
        <form method="post" :action.attr="$h.ak('/agent/notices')" id="own-notice-form">
            <div class="dlg-head">
                <h3 data-role="title">给二级代理发公告</h3>
                <p>只发给你自己的二级代理，TA 们登录后在「公告」里看到；「重要」的会在 TA 的概览顶部提醒</p>
            </div>
            <div class="dlg-body">
                <div class="field">
                    <label for="own-notice-title">标题<span class="field-count" data-count-for="own-notice-title"></span></label>
                    <input class="input" id="own-notice-title" name="title" maxlength="60" required placeholder="比如：本月结算时间">
                </div>
                <div class="field">
                    <label for="own-notice-body">内容<span class="field-count" data-count-for="own-notice-body"></span></label>
                    <textarea class="input" id="own-notice-body" name="body" rows="6" maxlength="2000" required placeholder="写清楚要二级代理知道或者要做的事，可以换行"></textarea>
                </div>
                <div class="field">
                    <label>提醒程度</label>
                    <div class="seg seg-fill">
                        <label class="seg-opt"><input type="radio" name="level" value="normal" checked><span>普通</span></label>
                        <label class="seg-opt"><input type="radio" name="level" value="important"><span>重要</span></label>
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

</main>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('公告'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
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
