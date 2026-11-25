<template>
<FAdminLayoutAgentbar :m="m" :c="c" :P_active="'account'"/>
<main class="content ap ap-acc">

    <div class="page-head">
        <div class="page-title"><div>
            <h1>我的账号</h1>
            <p>{{ $h.s($h.cat($h.p(m.agent, 'name'), ' · 登录账号 ', $h.p(m.agent, 'username'))) }}</p>
        </div></div>
    </div>

    <div class="grid grid-wide">
        <section class="card">
            <div class="card-head">
                <h2><svg class="i"><use href="#i-lock"></use></svg>修改密码</h2>
                <span class="tail muted small">改好后，其他设备上的登录会退出</span>
            </div>
            <form method="post" :action.attr="$h.ak('/agent/account/password')">
                <div class="card-body">
                    <div class="field">
                        <label for="ap-current">当前密码</label>
                        <input class="input" id="ap-current" name="current" type="password" autocomplete="current-password" required :disabled.attr="$h.t(m.preview) ? 'disabled' : null">
                    </div>
                    <div class="row-2">
                        <div class="field">
                            <label for="ap-new">新密码</label>
                            <input class="input" id="ap-new" name="password" type="password" minlength="8" maxlength="64" autocomplete="new-password" required data-pw :disabled.attr="$h.t(m.preview) ? 'disabled' : null">
                        </div>
                        <div class="field">
                            <label for="ap-confirm">再输一次</label>
                            <input class="input" id="ap-confirm" name="confirm" type="password" minlength="8" maxlength="64" autocomplete="new-password" required :disabled.attr="$h.t(m.preview) ? 'disabled' : null">
                        </div>
                    </div>
                    <span class="hint">至少 8 位，字母和数字混着用更安全</span>
                </div>
                <div class="card-foot">
                    <span class="hint">{{ $h.s(($h.t(m.preview) ? '预览中不能修改' : ($h.t($h.p(m.agent, 'sub')) ? '忘了密码找上级代理重置' : '忘了密码找管理员重置'))) }}</span>
                    <button class="btn btn-sm" type="submit" :disabled.attr="$h.t(m.preview) ? 'disabled' : null">保存新密码</button>
                </div>
            </form>
        </section>

        <section class="card">
            <div class="card-head"><h2><svg class="i"><use href="#i-user"></use></svg>账号信息</h2></div>
            <div class="card-body">
                <dl class="kv">
                    <dt>名称</dt><dd class="strong">{{ $h.s($h.p(m.agent, 'name')) }}</dd>
                    <dt>登录账号</dt><dd class="mono">{{ $h.s($h.p(m.agent, 'username')) }}</dd>
                    <template v-if="$h.t($h.p(m.agent, 'sub'))"><dt>上级代理</dt><dd class="strong">{{ $h.s($h.elvis(m.parentName, '—')) }}</dd></template>
                    <dt>我的口令</dt><dd>{{ $h.s($h.cat(m.codeCount, ' 个')) }}</dd>
                    <dt>申请口令</dt><dd>{{ $h.s(($h.t($h.p(m.agent, 'sub')) ? '口令由上级代理分配' : ($h.t($h.eq($h.p(m.agent, 'requestMode'), 'AUTO')) ? '可以申请，提交后马上生成' : ($h.t($h.eq($h.p(m.agent, 'requestMode'), 'REVIEW')) ? '可以申请，管理员同意后生成' : '不能申请，口令由管理员分配')))) }}</dd>
                    <template v-if="$h.t($h.p(m.agent, 'canManageSubs'))"><dt>二级代理</dt><dd>{{ $h.s($h.cat('已开 ', m.subCount, ' 个', ($h.t($h.gt($h.p(m.agent, 'subAgentLimit'), 0)) ? $h.cat('，最多 ', $h.p(m.agent, 'subAgentLimit'), ' 个') : '，不限个数'))) }}</dd></template>
                    <dt>激活明细</dt><dd>{{ $h.s(($h.t($h.p(m.agent, 'canViewRecords')) ? '可以查看每条激活记录' : '只能看数量')) }}</dd>
                    <dt>上次登录</dt><dd>{{ $h.s(($h.t(!$h.eq($h.p(m.agent, 'lastLoginAt'), null)) ? $h.ui.full($h.p(m.agent, 'lastLoginAt')) : '—')) }}</dd>
                    <dt>开通时间</dt><dd>{{ $h.s($h.ui.full($h.p(m.agent, 'createdAt'))) }}</dd>
                </dl>
                <div class="note" style="margin-top:16px"><svg class="i"><use href="#i-info"></use></svg><span>{{ $h.s(($h.t($h.p(m.agent, 'sub')) ? '名称和数据设置由上级代理管理，要改找上级代理。' : '名称和数据设置由管理员管理，要改找管理员。')) }}</span></div>
            </div>
        </section>
    </div>

</main>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('我的账号'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
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
