<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'releases'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'版本发布'"/>
    <template v-for="L_canRelease in [$h.access.can('release')]"><main class="content">

        <div class="page-head">
            <div class="page-title"><div>
                <h1>版本发布</h1>
                <p>{{ $h.s($h.cat($h.p(m.app, 'name'), ' · 近 30 天 ', m.activeDevices, ' 台设备检查过更新')) }}</p>
            </div></div>
            <div class="page-actions" v-if="$h.t(L_canRelease)">
                <button class="btn" type="button" data-open="upload-dialog" data-upload-mode="file"><svg class="i"><use href="#i-upload"></use></svg>上传更新包</button>
            </div>
        </div>

        <template v-for="[L_a, L_aStat] in $h.each(m.alerts)"><template v-if="L_aStat.index">

        </template><template v-for="L_st in [$h.z(L_a, 'stat')]"><div class="alert-bar">
            <svg class="i"><use href="#i-alert"></use></svg>
            <span class="grow">
                <b>{{ $h.s($h.cat($h.z(L_a, 'channels'), ' 的 ', $h.ui.rid($h.p($h.z(L_a, 'release'), 'id')), ' 启动失败偏多')) }}</b>：
                <span>{{ $h.s($h.cat('推送 ', $h.p(L_st, 'offered'), ' 台，装上 ', $h.p(L_st, 'installed'), ' 台，启动失败 ', $h.p(L_st, 'failed'), ' 台（', $h.p(L_st, 'failRate'), '%）。设备启动失败会自动退回之前的版本，建议先回滚再排查。')) }}</span>
            </span>
            <button class="btn btn-secondary btn-sm" type="button" data-detail :data-id.attr="$h.av($h.p($h.z(L_a, 'release'), 'id'))">看错误</button>
            <template v-for="[L_l, L_lStat] in $h.each($h.z(L_a, 'lives'))"><template v-if="$h.t(L_canRelease)">
                <button class="btn btn-danger btn-sm" type="button" data-rollback :data-channel.attr="$h.av($h.p(L_l, 'channel'))" :data-runtime.attr="$h.av($h.p(L_l, 'runtime'))" :data-current.attr="$h.av($h.elvis($h.p($h.p(L_l, 'release'), 'republishedFrom'), $h.p($h.p(L_l, 'release'), 'id')))" :data-previous.attr="$h.av($h.elvis($h.p($h.p(L_l, 'previous'), 'republishedFrom'), $h.p($h.p(L_l, 'previous'), 'id')))" v-if="$h.t(!$h.eq($h.p(L_l, 'previous'), null))"><svg class="i i-sm"><use href="#i-refresh"></use></svg><span>{{ $h.s(($h.t($h.gt($h.lists.size($h.z(L_a, 'lives')), 1)) ? $h.add('回滚 ', $h.p(L_l, 'channel')) : '回滚')) }}</span></button>
                <form method="post" :action.attr="$h.ak('/admin/rollback-embedded')" :data-confirm.attr="$h.av($h.cat('让 ', $h.p(L_l, 'channel'), ' 回到安装包内置版本？'))" data-confirm-body="这个渠道、这个 Runtime 的设备下次检查更新时会丢掉热更新，回到安装包里自带的版本。" data-confirm-ok="回退到内置版本" v-if="$h.t($h.eq($h.p(L_l, 'previous'), null))">
                    <input type="hidden" name="channel" :value.attr="$h.ak($h.p(L_l, 'channel'))">
                    <input type="hidden" name="runtime" :value.attr="$h.ak($h.p(L_l, 'runtime'))">
                    <button class="btn btn-danger btn-sm" type="submit">{{ $h.s(($h.t($h.gt($h.lists.size($h.z(L_a, 'lives')), 1)) ? $h.add($h.p(L_l, 'channel'), ' 回退内置版本') : '回退内置版本')) }}</button>
                </form>
            </template></template>
        </div></template></template>

        <nav class="tabs" data-tabs>
            <a href="#live" data-tab="live">渠道和版本</a>
            <a href="#history" data-tab="history">发布记录 <span class="count" v-if="!$h.t($h.lists.isEmpty(m.history))">{{ $h.s($h.lists.size(m.history)) }}</span></a>
            <a href="#installers" data-tab="installers">安装包 <span class="count" v-if="!$h.t($h.lists.isEmpty(m.installers))">{{ $h.s($h.lists.size(m.installers)) }}</span></a>
        </nav>

        <section data-tab-panel="live">
        <section class="card section-gap" data-guide="rel-live">
            <div class="card-head">
                <h2>线上版本</h2>
                <span :class="['badge', $h.av(($h.t(m.patchOn) ? 'badge-solid' : 'badge-line'))]" title="增量更新：设备只下载和当前版本不同的部分"><span class="dot"></span><span>{{ $h.s(($h.t(m.patchOn) ? '增量更新已开启' : '增量更新已关闭')) }}</span></span>
                <template v-for="L_guardOn in [$h.t($h.p(m.guard, 'on'))]"><button :class="['badge', 'badge-btn', $h.av(($h.t(L_guardOn) ? 'badge-solid' : 'badge-line'))]" type="button" data-open="guard-dialog" :title.attr="$h.av(($h.t(L_guardOn) ? $h.cat('线上版本启动失败率 ≥', $h.p(m.guard, 'rate'), '% 且 ≥', $h.p(m.guard, 'min'), ' 台时，自动回滚到上一版本。点这里修改') : '启动失败偏多时自动回滚到上一版本，点这里开启'))" v-if="$h.t(L_canRelease)"><span class="dot"></span><span>{{ $h.s(($h.t(L_guardOn) ? $h.cat('自动回滚 · ≥', $h.p(m.guard, 'rate'), '%') : '自动回滚未开启')) }}</span></button><span :class="['badge', $h.av(($h.t(L_guardOn) ? 'badge-solid' : 'badge-line'))]" v-if="!$h.t(L_canRelease)"><span class="dot"></span><span>{{ $h.s(($h.t(L_guardOn) ? $h.cat('自动回滚 · ≥', $h.p(m.guard, 'rate'), '%') : '自动回滚未开启')) }}</span></span></template>
                <span class="tail ch-url"><span class="muted small">更新地址</span><code class="mono">{{ $h.s(m.manifestUrl) }}</code><button class="icon-btn" type="button" :data-copy.attr="$h.av(m.manifestUrl)" title="复制更新地址" aria-label="复制更新地址"><svg class="i i-sm"><use href="#i-copy"></use></svg></button></span>
            </div>
            <div class="empty" v-if="$h.t($h.lists.isEmpty(m.live))">
                <div class="empty-icon"><svg class="i"><use href="#i-layers"></use></svg></div>
                <b>还没有发布到任何渠道</b>上传更新包时勾选「立即发布」，或在下面的版本列表里点「发布」
            </div>
            <div class="dep" v-if="!$h.t($h.lists.isEmpty(m.live))">
                <div class="dep-row dep-head" aria-hidden="true"><span>渠道</span><span>线上版本</span><span>灰度</span><span>设备</span><span>最近操作</span><span class="right">操作</span></div>
                <template v-for="[L_l, L_lStat] in $h.each(m.live)"><template v-if="L_lStat.index">
                </template><template v-for="L_on in [($h.t(!$h.t($h.p(L_l, 'rollbackToEmbedded'))) &amp;&amp; $h.t(!$h.eq($h.p(L_l, 'release'), null)))]"><template v-for="L_st in [($h.t(L_on) ? $h.i(m.stats, $h.p($h.p(L_l, 'release'), 'id')) : null)]"><template v-for="L_seen in [($h.t(!$h.eq(L_st, null)) &amp;&amp; $h.t(($h.t(($h.t($h.gt($h.p(L_st, 'offered'), 0)) || $h.t($h.gt($h.p(L_st, 'installed'), 0)))) || $h.t($h.gt($h.p(L_st, 'failed'), 0)))))]"><article :class="['dep-row', $h.av(($h.t($h.p(L_l, 'rollbackToEmbedded')) ? 'is-embedded' : null))]" :data-ch.attr="$h.av($h.p(L_l, 'channel'))" :data-rt.attr="$h.av($h.p(L_l, 'runtime'))" :data-reg.attr="$h.av(($h.t(L_on) ? $h.ui.rid($h.p($h.p(L_l, 'release'), 'id')) : ''))" :data-act.attr="$h.av(($h.t($h.eq($h.p(L_l, 'activation'), null)) ? '' : $h.ui.rid($h.p($h.p(L_l, 'activation'), 'id'))))">
                    <div class="dep-ch">
                        <span :class="['ch-dot', $h.av(($h.t($h.p(L_l, 'rollbackToEmbedded')) ? 'warn' : ($h.t(L_on) ? ($h.t($h.lt($h.p(L_l, 'rollout'), 100)) ? 'part' : null) : 'gray')))]"></span>
                        <div class="dep-name"><b>{{ $h.s($h.p(L_l, 'channel')) }}</b><small>{{ $h.s($h.add('Runtime ', $h.p(L_l, 'runtime'))) }}</small></div>
                    </div>
                    <div class="dep-ver">
                        <template v-if="$h.t(L_on)">
                            <div class="dep-line">
                                <span class="kind-chip reg" title="常规更新包：已激活的设备检查更新时拿它，可以灰度、回滚"><svg class="i"><use href="#i-zap"></use></svg>常规更新</span>
                                <a class="rid link-strong" href="#" data-detail :data-id.attr="$h.av($h.p($h.p(L_l, 'release'), 'id'))" title="查看版本详情">{{ $h.s($h.ui.rid($h.p($h.p(L_l, 'release'), 'id'))) }}</a>
                                <span class="dep-msg" :title.attr="$h.av($h.p($h.p(L_l, 'release'), 'message'))">{{ $h.s($h.elvis($h.p($h.p(L_l, 'release'), 'message'), $h.ui.source($h.p($h.p(L_l, 'release'), 'source')))) }}</span>
                            </div>
                            <small class="dep-sub">{{ $h.s(($h.t(!$h.eq($h.p(L_l, 'previous'), null)) ? $h.cat('上一版本 ', $h.ui.rid($h.p($h.p(L_l, 'previous'), 'id')), '，可以回滚到它') : '没有上一版本')) }}</small>
                        </template>
                        <template v-if="$h.t($h.p(L_l, 'rollbackToEmbedded'))">
                            <div class="dep-line"><span class="kind-chip reg"><svg class="i"><use href="#i-zap"></use></svg>常规更新</span><span class="pill warn"><svg class="i i-sm"><use href="#i-package"></use></svg>安装包内置版本</span></div>
                            <small class="dep-sub" :title.attr="$h.av($h.ui.full($h.p(L_l, 'rollbackAt')))">{{ $h.s($h.cat('回退于 ', $h.ui.dt($h.p(L_l, 'rollbackAt')), ($h.t(!$h.eq($h.p(L_l, 'release'), null)) ? $h.cat('，回退前是 ', $h.ui.rid($h.p($h.p(L_l, 'release'), 'id'))) : ''))) }}</small>
                        </template>
                        <template v-if="$h.t(($h.t(!$h.t($h.p(L_l, 'rollbackToEmbedded'))) &amp;&amp; $h.t($h.eq($h.p(L_l, 'release'), null))))">
                            <div class="dep-line"><span class="kind-chip reg"><svg class="i"><use href="#i-zap"></use></svg>常规更新</span><span class="muted">还没有版本</span></div>
                            <small class="dep-sub">在下面的版本列表里点「发布」</small>
                        </template>
                    </div>
                    <div class="dep-roll">
                        <template v-if="$h.t(L_on)">
                            <div class="ch-split-bar" role="img" :aria-label.attr="$h.av($h.cat('灰度 ', $h.p(L_l, 'rollout'), '%'))">
                                <span class="new" :style="$h.av($h.cat('width:', $h.p(L_l, 'rollout'), '%'))"></span>
                                <span class="old" :style="$h.av($h.cat('width:', $h.sub(100, $h.p(L_l, 'rollout')), '%'))" v-if="$h.t($h.lt($h.p(L_l, 'rollout'), 100))"></span>
                            </div>
                            <small class="dep-roll-text"><b>{{ $h.s($h.add($h.p(L_l, 'rollout'), '%')) }}</b><span>{{ $h.s(($h.t($h.lt($h.p(L_l, 'rollout'), 100)) ? ($h.t(!$h.eq($h.p(L_l, 'previous'), null)) ? $h.cat('其余用 ', $h.ui.rid($h.p($h.p(L_l, 'previous'), 'id'))) : '其余先不更新') : '全部设备')) }}</span></small>
                        </template>
                        <small class="dep-roll-text" v-if="$h.t($h.p(L_l, 'rollbackToEmbedded'))" title="设备下次检查更新时丢掉热更新，回到安装包里自带的版本">设备回到内置版本</small>
                        <span class="muted" v-if="$h.t(($h.t(!$h.t($h.p(L_l, 'rollbackToEmbedded'))) &amp;&amp; $h.t($h.eq($h.p(L_l, 'release'), null))))">—</span>
                    </div>
                    <div :class="['dep-health', $h.av(($h.t(L_on) ? null : 'is-empty'))]" title="装上 / 推送：检查更新时拿到这个版本的设备里，已经重启运行它的有多少">
                        <template v-if="$h.t(L_seen)">
                            <span class="dep-num"><b>{{ $h.s($h.p(L_st, 'installed')) }}</b><span class="muted" v-if="$h.t($h.gt($h.p(L_st, 'offered'), 0))">{{ $h.s($h.add(' / ', $h.p(L_st, 'offered'))) }}</span></span>
                            <small><span>{{ $h.s(($h.t($h.gt($h.p(L_st, 'offered'), 0)) ? $h.cat('装上 ', $h.p(L_st, 'installRate'), '%') : '台装上')) }}</span><span class="danger-text" v-if="$h.t($h.gt($h.p(L_st, 'failed'), 0))">{{ $h.s($h.cat(' · 失败 ', $h.p(L_st, 'failed'))) }}</span></small>
                        </template>
                        <small class="dep-wait" v-if="$h.t(($h.t(L_on) &amp;&amp; $h.t(!$h.t(L_seen))))"><svg class="i i-sm"><use href="#i-clock"></use></svg>等设备检查更新</small>
                        <span class="muted" v-if="!$h.t(L_on)">—</span>
                    </div>
                    <div class="dep-when" :title.attr="$h.av($h.ui.full($h.p(L_l, 'lastAt')))"><span>{{ $h.s($h.ui.ago($h.p(L_l, 'lastAt'))) }}</span><small>{{ $h.s($h.elvis($h.p(L_l, 'lastBy'), '—')) }}</small></div>
                    <div class="dep-acts" v-if="$h.t(L_canRelease)">
                        <button class="btn btn-secondary btn-sm" type="button" data-rollout-adjust :data-channel.attr="$h.av($h.p(L_l, 'channel'))" :data-runtime.attr="$h.av($h.p(L_l, 'runtime'))" :data-rollout.attr="$h.av($h.p(L_l, 'rollout'))" :data-short.attr="$h.av($h.ui.rid($h.p($h.p(L_l, 'release'), 'id')))" :data-prev.attr="$h.av(($h.t(!$h.eq($h.p(L_l, 'previous'), null)) ? $h.ui.rid($h.p($h.p(L_l, 'previous'), 'id')) : ''))" v-if="$h.t(L_on)">调整灰度</button>
                        <button :class="['btn', 'btn-sm', $h.av(($h.t($h.p(L_l, 'rollbackToEmbedded')) ? null : 'btn-ghost'))]" type="button" data-rollback :data-channel.attr="$h.av($h.p(L_l, 'channel'))" :data-runtime.attr="$h.av($h.p(L_l, 'runtime'))" :data-current.attr="$h.av(($h.t($h.p(L_l, 'rollbackToEmbedded')) ? '' : $h.elvis($h.p($h.p(L_l, 'release'), 'republishedFrom'), $h.p($h.p(L_l, 'release'), 'id'))))" :data-previous.attr="$h.av(($h.t(($h.t(!$h.eq($h.p(L_l, 'previous'), null)) &amp;&amp; $h.t(!$h.t($h.p(L_l, 'rollbackToEmbedded'))))) ? $h.elvis($h.p($h.p(L_l, 'previous'), 'republishedFrom'), $h.p($h.p(L_l, 'previous'), 'id')) : ''))" title="回滚到上一版本或更早的任意版本" v-if="$h.t(!$h.eq($h.p(L_l, 'release'), null))"><svg class="i i-sm"><use href="#i-refresh"></use></svg>{{ $h.s(($h.t($h.p(L_l, 'rollbackToEmbedded')) ? '恢复到一个版本' : '回滚')) }}</button>
                        <div class="row-menu" data-pop v-if="$h.t(L_on)">
                            <button class="icon-btn" type="button" data-pop-toggle aria-haspopup="true" aria-expanded="false" aria-label="更多操作" title="更多操作"><svg class="i"><use href="#i-more"></use></svg></button>
                            <div class="menu menu-float" data-pop-menu hidden>
                                <button class="menu-item" type="button" data-plan-rollout :data-channel.attr="$h.av($h.p(L_l, 'channel'))" :data-runtime.attr="$h.av($h.p(L_l, 'runtime'))" :data-rollout.attr="$h.av($h.p(L_l, 'rollout'))"><svg class="i i-sm"><use href="#i-calendar"></use></svg>定时调整灰度</button>
                                <button class="menu-item" type="button" data-deploy :data-id.attr="$h.av($h.p($h.p(L_l, 'release'), 'id'))" :data-short.attr="$h.av($h.ui.rid($h.p($h.p(L_l, 'release'), 'id')))" :data-runtime.attr="$h.av($h.p(L_l, 'runtime'))" :data-from.attr="$h.av($h.p(L_l, 'channel'))" title="测试渠道验证好了，原样发到其他渠道" v-if="$h.t($h.gt($h.lists.size(m.channels), 1))"><svg class="i i-sm"><use href="#i-send"></use></svg>推到其他渠道</button>                                <div class="menu-sep"></div>
                                <form method="post" :action.attr="$h.ak('/admin/rollback-embedded')" :data-confirm.attr="$h.av($h.cat('让 ', $h.p(L_l, 'channel'), ' 回到安装包内置版本？'))" data-confirm-body="这个渠道、这个 Runtime 的设备下次检查更新时会丢掉热更新，回到安装包里自带的版本。再发布任意版本即可恢复。" data-confirm-ok="回退到内置版本" data-confirm-danger>
                                    <input type="hidden" name="channel" :value.attr="$h.ak($h.p(L_l, 'channel'))">
                                    <input type="hidden" name="runtime" :value.attr="$h.ak($h.p(L_l, 'runtime'))">
                                    <button class="menu-item danger" type="submit" title="出了大问题时，让设备丢掉热更新，回到安装包自带的版本"><svg class="i i-sm"><use href="#i-package"></use></svg>回退内置版本</button>
                                </form>
                            </div>
                        </div>
                    </div>
                    <div class="dep-acts muted small" v-if="!$h.t(L_canRelease)">只读</div>
                    <template v-for="L_act in [$h.p(L_l, 'activation')]"><div :class="['dep-code', $h.av(($h.t($h.eq(L_act, null)) ? 'is-same' : null))]">
                        <span class="kind-chip act" title="口令更新包：新装 App 输入口令激活时下载，不灰度"><svg class="i"><use href="#i-key"></use></svg>口令更新</span>
                        <template v-if="$h.t(!$h.eq(L_act, null))">
                            <a class="rid link-strong" href="#" data-detail :data-id.attr="$h.av($h.p(L_act, 'id'))" title="查看版本详情">{{ $h.s($h.ui.rid($h.p(L_act, 'id'))) }}</a>
                            <span class="dep-msg" :title.attr="$h.av($h.cat($h.elvis($h.p(L_l, 'activationBy'), ''), ' ', $h.ui.full($h.p(L_l, 'activationAt')), ' 设置'))">{{ $h.s($h.elvis($h.p(L_act, 'message'), $h.ui.source($h.p(L_act, 'source')))) }}</span>
                            <small class="dep-code-hint">新装 App 输入口令激活时下载这个包 · 不灰度</small>
                        </template>
                        <template v-if="$h.t($h.eq(L_act, null))">
                            <span class="dep-code-same">跟常规更新一样</span>
                            <small class="dep-code-hint">新装 App 输入口令激活时也拿上面的常规更新包</small>
                        </template>
                        <span class="dep-code-ops" v-if="$h.t(L_canRelease)">
                            <button :class="['btn', 'btn-sm', $h.av(($h.t($h.eq(L_act, null)) ? 'btn-ghost' : 'btn-secondary'))]" type="button" data-activation :data-channel.attr="$h.av($h.p(L_l, 'channel'))" :data-runtime.attr="$h.av($h.p(L_l, 'runtime'))" :data-current.attr="$h.av(($h.t($h.eq(L_act, null)) ? '' : $h.elvis($h.p(L_act, 'republishedFrom'), $h.p(L_act, 'id'))))" :data-live.attr="$h.av(($h.t(L_on) ? $h.elvis($h.p($h.p(L_l, 'release'), 'republishedFrom'), $h.p($h.p(L_l, 'release'), 'id')) : ''))" :title.attr="$h.av(($h.t($h.eq(L_act, null)) ? '给输入口令激活的设备单独选一个版本，和常规更新分开' : '换一个版本做口令更新包'))"><svg class="i i-sm"><use href="#i-key"></use></svg>{{ $h.s(($h.t($h.eq(L_act, null)) ? '单独设置' : '更换')) }}</button>
                            <form method="post" :action.attr="$h.ak('/admin/activation/clear')" :data-confirm.attr="$h.av($h.cat('取消 ', $h.p(L_l, 'channel'), ' 的口令更新包？'))" data-confirm-body="新装 App 输入口令激活时改回拿常规更新包，和没单独设时一样。" data-confirm-ok="取消口令更新包" v-if="$h.t(!$h.eq(L_act, null))">
                                <input type="hidden" name="channel" :value.attr="$h.ak($h.p(L_l, 'channel'))">
                                <input type="hidden" name="runtime" :value.attr="$h.ak($h.p(L_l, 'runtime'))">
                                <button class="btn btn-ghost btn-sm" type="submit" title="输入口令激活时改回拿常规更新包">取消</button>
                            </form>
                        </span>
                    </div></template>
                </article></template></template></template></template>
            </div>
            <div class="plan-box" v-if="$h.t((!$h.t($h.lists.isEmpty(m.plans)) || !$h.t($h.lists.isEmpty(m.planLog))))">
                <div class="plan-title"><svg class="i i-sm"><use href="#i-calendar"></use></svg>计划任务<small>到时间自动执行，结果记在操作日志里</small></div>
                <ul class="plan-list"><template v-for="[L_p, L_pStat] in $h.each(m.plans)"><li>
                    <span class="code-st pending">等执行</span>
                    <span class="plan-what"><b :title.attr="$h.av($h.ui.full($h.p(L_p, 'runAt')))">{{ $h.s($h.ui.dt($h.p(L_p, 'runAt'))) }}</b>{{ $h.s($h.p(L_p, 'what')) }}<small>{{ $h.s($h.cat($h.elvis($h.p(L_p, 'createdBy'), ''), ' 设置')) }}</small></span>
                    <form method="post" :action.attr="$h.ak($h.link('/admin/schedules/{id}/cancel', [['id', $h.p(L_p, 'id')]]))" data-confirm="取消这个计划？" data-confirm-ok="取消计划"><input type="hidden" name="back" :value.attr="'/admin/releases'"><button class="link-btn" type="submit">取消</button></form>
                </li></template><template v-for="[L_p, L_pStat] in $h.each(m.planLog)"><li class="done">
                    <span :class="['code-st', $h.av(($h.t($h.eq($h.p(L_p, 'status'), 'DONE')) ? 'approved' : 'rejected'))]">{{ $h.s(($h.t($h.eq($h.p(L_p, 'status'), 'DONE')) ? '已执行' : '没执行')) }}</span>
                    <span class="plan-what"><b>{{ $h.s($h.ui.dt($h.p(L_p, 'runAt'))) }}</b>{{ $h.s($h.p(L_p, 'what')) }}<small>{{ $h.s($h.elvis($h.p(L_p, 'result'), '')) }}</small></span>
                </li></template></ul>
            </div>
        </section>

        <section class="card" v-if="$h.t($h.lists.isEmpty(m.groups))">
            <div class="empty">
                <div class="empty-icon"><svg class="i"><use href="#i-package"></use></svg></div>
                <b>还没有版本</b>用命令行一条命令发布，或者在这里上传 npx expo export 导出的 dist 目录压缩包、添加放在 CDN 上的 dist 目录链接
                <div class="btn-row" style="justify-content:center" v-if="$h.t(L_canRelease)">
                    <button class="btn btn-secondary" type="button" data-open="upload-dialog" data-upload-mode="link"><svg class="i"><use href="#i-link"></use></svg>添加外部链接</button>
                    <button class="btn" type="button" data-open="upload-dialog" data-upload-mode="file"><svg class="i"><use href="#i-upload"></use></svg>上传更新包</button>
                </div>
            </div>
        </section>

        <div class="stack" data-guide="rel-list">
            <template v-for="[L_g, L_gStat] in $h.each(m.groups)"><template v-if="L_gStat.index">
            </template><section class="card">
                <div class="card-head">
                    <h2>Runtime <span class="mono">{{ $h.s($h.p(L_g, 'runtime')) }}</span></h2>
                    <span class="muted small">{{ $h.s(($h.t($h.gt($h.p(L_g, 'hidden'), 0)) ? $h.cat('显示最近的 ', $h.lists.size($h.p(L_g, 'rows')), ' 个，共 ', $h.p(L_g, 'total'), ' 个版本') : $h.cat($h.p(L_g, 'total'), ' 个版本'))) }}</span>
                    <a class="tail link" :href.attr="$h.ak($h.link('/admin/releases', [['all', true]]))" v-if="$h.t($h.gt($h.p(L_g, 'hidden'), 0))">显示全部<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
                    <a class="tail link" :href.attr="$h.ak('/admin/releases')" v-if="$h.t(($h.t(m.all) &amp;&amp; $h.t($h.gt($h.p(L_g, 'total'), 20))))">只看最近 20 个</a>
                </div>
                <div class="table-wrap">
                    <table class="compact ver-table">
                        <thead>
                        <tr><th>版本</th><th>渠道</th><th title="装上 / 推送；在用：近 30 天正在运行这个版本的设备">设备</th><th>发布时间</th><th class="right">操作</th></tr>
                        </thead>
                        <tbody>
                        <template v-for="[L_row, L_rowStat] in $h.each($h.p(L_g, 'rows'))"><template v-if="L_rowStat.index">
                        </template><template v-for="L_r in [$h.p(L_row, 'release')]"><tr :class="$h.av(($h.t($h.p(L_row, 'live')) ? 'row-live' : null))">
                            <td class="ver-cell">
                                <div class="ver-line">
                                    <a class="rid link-strong" href="#" data-detail :data-id.attr="$h.av($h.p(L_r, 'id'))" :data-hash-click.attr="$h.av($h.add('rel-', $h.p(L_r, 'id')))" :title.attr="$h.av($h.add('查看详情 · ', $h.p(L_r, 'id')))">{{ $h.s($h.ui.rid($h.p(L_r, 'id'))) }}</a>
                                    <span class="pill warn" title="下载完立即重启生效" v-if="$h.t($h.p(L_r, 'forceUpdate'))">强制</span>
                                    <span class="pill pill-copy" :title.attr="$h.av($h.cat('回滚或发布旧版本时复制出来的，内容和 ', $h.ui.rid($h.p(L_r, 'republishedFrom')), ' 一样'))" v-if="$h.t(!$h.eq($h.p(L_r, 'republishedFrom'), null))">{{ $h.s($h.cat($h.ui.rid($h.p(L_r, 'republishedFrom')), ' 的副本')) }}</span>
                                </div>
                                <div :class="['ver-msg', $h.av(($h.t($h.strings.isEmpty($h.p(L_r, 'message'))) ? 'muted' : null))]" :title.attr="$h.av($h.p(L_r, 'message'))">{{ $h.s(($h.t($h.strings.isEmpty($h.p(L_r, 'message'))) ? '没有说明' : $h.p(L_r, 'message'))) }}</div>
                            </td>
                            <td>
                                <div class="ver-tags">
                                    <template v-for="[L_tag, L_tagStat] in $h.each($h.p(L_row, 'liveIn'))"><span class="badge badge-solid" title="常规更新包：已激活的设备检查更新时拿它"><span class="dot"></span><span>{{ $h.s(L_tag) }}</span></span></template>
                                    <template v-for="[L_tag, L_tagStat] in $h.each($h.p(L_row, 'previousIn'))"><span class="badge badge-line" title="可以一键回滚到它；灰度发布时，没在灰度里的设备继续用它"><span class="dot"></span><span>{{ $h.s(L_tag) }}</span></span></template>
                                    <template v-for="[L_tag, L_tagStat] in $h.each($h.p(L_row, 'activationIn'))"><span class="badge badge-act" title="口令更新包：新装 App 输入口令激活时下载它"><svg class="i"><use href="#i-key"></use></svg><span>{{ $h.s($h.add(L_tag, ' 口令更新')) }}</span></span></template>
                                    <span class="muted" v-if="$h.t(($h.t(($h.t(!$h.t($h.p(L_row, 'live'))) &amp;&amp; $h.t($h.lists.isEmpty($h.p(L_row, 'previousIn'))))) &amp;&amp; $h.t($h.lists.isEmpty($h.p(L_row, 'activationIn')))))">—</span>
                                </div>
                            </td>
                            <template v-for="L_st in [$h.i(m.stats, $h.p(L_r, 'id'))]"><td class="install-cell">
                                <template v-if="$h.t(($h.t(!$h.eq(L_st, null)) &amp;&amp; $h.t(($h.t(($h.t($h.gt($h.p(L_st, 'offered'), 0)) || $h.t($h.gt($h.p(L_st, 'installed'), 0)))) || $h.t($h.gt($h.p(L_st, 'failed'), 0))))))">
                                    <span class="usage">
                                        <span class="num"><b>{{ $h.s($h.p(L_st, 'installed')) }}</b><span class="muted" v-if="$h.t(($h.t($h.ge($h.p(L_st, 'offered'), $h.p(L_st, 'installed'))) &amp;&amp; $h.t($h.gt($h.p(L_st, 'offered'), 0))))">{{ $h.s($h.add(' / ', $h.p(L_st, 'offered'))) }}</span><span class="muted" v-if="!$h.t(($h.t($h.ge($h.p(L_st, 'offered'), $h.p(L_st, 'installed'))) &amp;&amp; $h.t($h.gt($h.p(L_st, 'offered'), 0))))"> 台装上</span></span>
                                        <span class="usage-bar" v-if="$h.t($h.gt($h.p(L_st, 'offered'), 0))"><span :style="$h.av($h.cat('width:', $h.p(L_st, 'installRate'), '%'))"></span></span>
                                    </span>
                                    <span class="muted-line">
                                        <span>{{ $h.s($h.add('在用 ', $h.p(L_row, 'devices'))) }}</span>
                                        <span class="danger-text" v-if="$h.t($h.gt($h.p(L_st, 'failed'), 0))">{{ $h.s($h.add('· 失败 ', $h.p(L_st, 'failed'))) }}</span>
                                    </span>
                                </template>
                                <template v-if="!$h.t(($h.t(!$h.eq(L_st, null)) &amp;&amp; $h.t(($h.t(($h.t($h.gt($h.p(L_st, 'offered'), 0)) || $h.t($h.gt($h.p(L_st, 'installed'), 0)))) || $h.t($h.gt($h.p(L_st, 'failed'), 0))))))">
                                    <span :class="['num', $h.av(($h.t($h.eq($h.p(L_row, 'devices'), 0)) ? 'muted' : null))]">{{ $h.s(($h.t($h.gt($h.p(L_row, 'devices'), 0)) ? $h.p(L_row, 'devices') : '—')) }}</span>
                                    <span class="muted-line" v-if="$h.t($h.gt($h.p(L_row, 'devices'), 0))">在用</span>
                                </template>
                            </td></template>
                            <td class="ver-when" :title.attr="$h.av($h.ui.full($h.p(L_r, 'createdAt')))">
                                <span>{{ $h.s($h.ui.dt($h.p(L_r, 'createdAt'))) }}</span>
                                <small>{{ $h.s($h.cat($h.elvis($h.p(L_r, 'createdBy'), $h.ui.source($h.p(L_r, 'source'))), ' · ', ($h.t($h.p(L_r, 'external')) ? $h.add('外部链接 ', $h.ui.host($h.p(L_r, 'externalBaseUrl'))) : $h.ui.size($h.p(L_r, 'totalBytes'))), ($h.t(($h.t($h.strings.isEmpty($h.p(L_r, 'platforms'))) || $h.t($h.strings.contains($h.p(L_r, 'platforms'), ',')))) ? '' : $h.add(' · 仅 ', ($h.t($h.eq($h.p(L_r, 'platforms'), 'ios')) ? 'iOS' : 'Android'))))) }}</small>
                            </td>
                            <td class="actions">
                                <div class="btn-row">
                                    <button class="btn btn-secondary btn-sm" type="button" data-deploy :data-id.attr="$h.av($h.p(L_r, 'id'))" :data-short.attr="$h.av($h.ui.rid($h.p(L_r, 'id')))" :data-runtime.attr="$h.av($h.p(L_r, 'runtimeVersion'))" v-if="$h.t(L_canRelease)">发布</button>
                                    <div class="row-menu" data-pop>
                                        <button class="icon-btn" type="button" data-pop-toggle aria-haspopup="true" aria-expanded="false" aria-label="更多操作" title="详情、校验、删除"><svg class="i"><use href="#i-more"></use></svg></button>
                                        <div class="menu menu-float" data-pop-menu hidden>
                                            <button class="menu-item" type="button" data-detail :data-id.attr="$h.av($h.p(L_r, 'id'))" title="文件清单、更新包结构、安装情况和启动错误">
                                                <svg class="i i-sm"><use href="#i-file"></use></svg>详情
                                            </button>
                                            <button class="menu-item" type="button" data-verify :data-id.attr="$h.av($h.p(L_r, 'id'))" :data-ext.attr="$h.av($h.p(L_r, 'external'))" :title.attr="$h.av(($h.t($h.p(L_r, 'external')) ? '从外部链接逐个下载文件，核对哈希和签名' : '核对文件、哈希、Runtime 和签名'))">
                                                <svg class="i i-sm"><use href="#i-check-file"></use></svg>校验
                                            </button>
                                            <div class="menu-sep" v-if="$h.t(L_canRelease)"></div>
                                            <form method="post" :action.attr="$h.ak('/admin/delete')" :data-confirm.attr="$h.av($h.cat('删除版本 ', $h.ui.rid($h.p(L_r, 'id')), '？'))" data-confirm-body="版本记录删了不能恢复。文件不删，别的版本可能也在用。" data-confirm-ok="删除版本" data-confirm-danger v-if="$h.t(($h.t(L_canRelease) &amp;&amp; $h.t($h.eq($h.p(L_row, 'lock'), null))))">
                                                <input type="hidden" name="releaseId" :value.attr="$h.ak($h.p(L_r, 'id'))">
                                                <button class="menu-item danger" type="submit"><svg class="i i-sm"><use href="#i-trash"></use></svg>删除版本</button>
                                            </form>
                                            <div class="menu-item is-locked" :title.attr="$h.av($h.p(L_row, 'lock'))" v-if="$h.t(($h.t(L_canRelease) &amp;&amp; $h.t(!$h.eq($h.p(L_row, 'lock'), null))))"><svg class="i i-sm"><use href="#i-lock"></use></svg><span>不能删除<small>{{ $h.s($h.p(L_row, 'lock')) }}</small></span></div>
                                        </div>
                                    </div>
                                </div>
                            </td>
                        </tr></template></template>
                        </tbody>
                    </table>
                </div>
            </section></template>
        </div>
        </section>

        <section data-tab-panel="history" hidden>
        <section class="card" data-guide="rel-history">
            <div class="card-head">
                <h2>发布记录</h2>
                <span class="muted small">每个渠道的发布、调整灰度、回滚、回退内置版本和口令更新包，最近 10 条</span>
            </div>
            <div class="empty" v-if="$h.t($h.lists.isEmpty(m.history))">
                <div class="empty-icon"><svg class="i"><use href="#i-history"></use></svg></div>
                <b>还没有发布记录</b>发布、调整灰度、回滚和回退内置版本都会记在这里
            </div>
            <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty(m.history))">
                <table class="compact">
                    <thead><tr><th>时间</th><th>渠道</th><th>操作</th><th>版本</th><th>灰度</th><th>操作人</th></tr></thead>
                    <tbody data-collapse="6" data-collapse-unit="条记录">
                    <template v-for="[L_e, L_eStat] in $h.each(m.history)"><template v-if="L_eStat.index">
                    </template><tr>
                        <td class="muted nowrap" :title.attr="$h.av($h.ui.full($h.p(L_e, 'at')))">{{ $h.s($h.ui.dt($h.p(L_e, 'at'))) }}</td>
                        <td class="nowrap"><span class="strong">{{ $h.s($h.p(L_e, 'channel')) }}</span><span class="muted-line">{{ $h.s($h.add('Runtime ', $h.p(L_e, 'runtime'))) }}</span></td>
                        <td><span :class="['ev-tag', $h.av($h.strings.toLowerCase($h.p(L_e, 'action')))]">{{ $h.s($h.p(L_e, 'actionLabel')) }}</span></td>
                        <td>
                            <span class="muted" v-if="$h.t($h.eq($h.p(L_e, 'releaseId'), null))">{{ $h.s(($h.t($h.eq($h.p(L_e, 'action'), 'ACTIVATION_OFF')) ? '改回常规更新' : '安装包内置版本')) }}</span>
                            <template v-if="$h.t(!$h.eq($h.p(L_e, 'releaseId'), null))">
                                <a class="rid link-strong" href="#" data-detail :data-id.attr="$h.av($h.p(L_e, 'releaseId'))" title="查看版本详情">{{ $h.s($h.ui.rid($h.p(L_e, 'originId'))) }}</a>
                                <span class="muted-line" title="旧版本复制成的新版本，内容和左边的版本相同" v-if="$h.t($h.p(L_e, 'copy'))">{{ $h.s($h.add('实际上线的副本 ', $h.ui.rid($h.p(L_e, 'releaseId')))) }}</span>
                            </template>
                        </td>
                        <td class="num">{{ $h.s(($h.t(($h.t($h.eq($h.p(L_e, 'releaseId'), null)) || $h.t($h.eq($h.p(L_e, 'action'), 'ACTIVATION')))) ? '—' : $h.add($h.p(L_e, 'rollout'), '%'))) }}</td>
                        <td class="muted nowrap">{{ $h.s($h.elvis($h.p(L_e, 'actor'), '—')) }}</td>
                    </tr></template>
                    </tbody>
                </table>
            </div>
        </section>
        </section>

        <section data-tab-panel="installers" id="installers" data-hash-target="installers" hidden>
        <button class="inst-drop" type="button" data-open="installer-dialog" v-if="$h.t(L_canRelease)">
            <span class="id-ic"><svg class="i"><use href="#i-upload"></use></svg></span>
            <span class="id-text"><b>上传 APK / IPA 检查一下</b><small>读出包里的更新地址、运行版本、渠道和签名证书，看装了它的设备能不能收到这个 App 的更新。最大 1 GB，自动分片上传</small></span>
            <span class="btn btn-secondary btn-sm">选择文件</span>
        </button>
        <div class="empty card" v-if="$h.t(($h.t($h.lists.isEmpty(m.installers)) &amp;&amp; $h.t(!$h.t(L_canRelease))))">
            <div class="empty-icon"><svg class="i"><use href="#i-phone"></use></svg></div>
            <b>还没有上传安装包</b>有「发布版本」权限的人可以上传 APK / IPA 检查配置
        </div>
        <div class="inst-list" data-collapse="6" data-collapse-unit="个安装包" v-if="!$h.t($h.lists.isEmpty(m.installers))">
            <template v-for="[L_p, L_pStat] in $h.each(m.installers)"><template v-if="L_pStat.index">
            </template><article :class="['inst-card', $h.av($h.add('lv-', $h.p(L_p, 'level')))]">
                <div class="ic-head">
                    <span :class="['ic-plat', $h.av(($h.t($h.eq($h.p(L_p, 'platform'), 'iOS')) ? 'ios' : 'android'))]"><svg class="i"><use :href.attr="$h.ak(($h.t($h.eq($h.p(L_p, 'platform'), 'iOS')) ? '#i-apple' : '#i-android'))"></use></svg></span>
                    <div class="ic-title">
                        <b>{{ $h.s($h.add($h.p(L_p, 'platform'), ($h.t(!$h.eq($h.p(L_p, 'version'), null)) ? $h.add(' ', $h.p(L_p, 'version')) : ''))) }}</b>
                        <small class="mono" v-if="$h.t(!$h.eq($h.p(L_p, 'bundleId'), null))">{{ $h.s($h.p(L_p, 'bundleId')) }}</small>
                    </div>
                    <span :class="['badge', $h.av(($h.t($h.eq($h.p(L_p, 'level'), 'ok')) ? 'badge-solid' : ($h.t($h.eq($h.p(L_p, 'level'), 'error')) ? 'badge-danger' : 'badge-warn')))]"><span class="dot"></span><span>{{ $h.s($h.p(L_p, 'headline')) }}</span></span>
                    <div class="btn-row ic-acts" v-if="$h.t(L_canRelease)">
                        <a class="icon-btn" :href.attr="$h.ak($h.link('/admin/installers/{id}/download', [['id', $h.p(L_p, 'id')]]))" data-no-swap title="下载" aria-label="下载安装包"><svg class="i i-sm"><use href="#i-download"></use></svg></a>
                        <form method="post" :action.attr="$h.ak($h.link('/admin/installers/{id}/delete', [['id', $h.p(L_p, 'id')]]))" data-confirm="删除这个安装包？" data-confirm-body="只删除上传的安装包文件和检查结果，不影响已经发布的版本和设备。" data-confirm-ok="删除" data-confirm-danger>
                            <button class="icon-btn" type="submit" title="删除" aria-label="删除安装包"><svg class="i i-sm"><use href="#i-trash"></use></svg></button>
                        </form>
                    </div>
                </div>
                <ul class="ic-checks">
                    <template v-for="[L_c, L_cStat] in $h.each($h.p(L_p, 'checks'))"><template v-if="L_cStat.index">
                    </template><li :class="$h.av($h.p(L_c, 'state'))">
                        <span :class="['st', $h.av($h.add('st-', $h.p(L_c, 'state')))]">
                            <svg class="i" v-if="$h.t($h.eq($h.p(L_c, 'state'), 'ok'))"><use href="#i-tick"></use></svg>
                            <svg class="i" v-if="$h.t($h.eq($h.p(L_c, 'state'), 'error'))"><use href="#i-x"></use></svg>
                            <template v-if="$h.t(!$h.t($h.lists.contains(['ok', 'error'], $h.p(L_c, 'state'))))">!</template>
                        </span>
                        <span class="icc-text"><b>{{ $h.s($h.p(L_c, 'label')) }}</b><small :title.attr="$h.av($h.p(L_c, 'text'))">{{ $h.s($h.p(L_c, 'text')) }}</small></span>
                    </li></template>
                </ul>
                <template v-for="[L_w, L_wStat] in $h.each($h.p(L_p, 'warnings'))"><template v-if="L_wStat.index">
                </template><p class="ic-warn"><svg class="i i-sm"><use href="#i-info"></use></svg>{{ $h.s(L_w) }}</p></template>
                <div class="ic-foot">
                    <span><svg class="i i-sm"><use href="#i-file"></use></svg>{{ $h.s($h.add($h.add($h.p(L_p, 'fileName'), ' · '), $h.p(L_p, 'size'))) }}</span>
                    <span>{{ $h.s($h.cat('Runtime ', $h.elvis($h.p(L_p, 'runtime'), '未知'), ' · 渠道 ', $h.elvis($h.p(L_p, 'channel'), 'production'))) }}</span>
                    <span v-if="$h.t(!$h.eq($h.p(L_p, 'embedded'), null))">{{ $h.s($h.add('内置版本 ', $h.p(L_p, 'embedded'))) }}</span>
                    <span v-if="$h.t(!$h.eq($h.p(L_p, 'note'), null))">{{ $h.s($h.p(L_p, 'note')) }}</span>
                    <span class="ic-when">{{ $h.s($h.cat($h.p(L_p, 'uploadedAt'), ' · ', $h.elvis($h.p(L_p, 'uploadedBy'), '—'))) }}</span>
                </div>
            </article></template>
        </div>
        </section>

        <dialog id="deploy-dialog">
            <form method="post" :action.attr="$h.ak('/admin/deploy')" id="deploy-form">
                <input type="hidden" name="releaseId">
                <div class="dlg-head">
                    <h3><span data-role="verb">发布</span> <span class="mono" data-role="short"></span></h3>
                    <p data-role="lead">发布后，这个渠道里 Runtime 相同的设备下次检查更新就会拿到。原来的版本记为上一版本，随时能回滚。</p>
                </div>
                <div class="dlg-body">
                    <div class="kind-pick" role="group" aria-label="发布为">
                        <label class="kind-opt">
                            <input type="checkbox" name="as" :value.attr="'hot'" :checked.attr="''">
                            <span class="kind-ico" aria-hidden="true"><svg class="i"><use href="#i-zap"></use></svg></span>
                            <span class="kind-txt"><b>常规更新</b><small>已激活的设备检查更新时拿到，可以灰度、回滚</small></span>
                        </label>
                        <label class="kind-opt">
                            <input type="checkbox" name="as" :value.attr="'activation'">
                            <span class="kind-ico" aria-hidden="true"><svg class="i"><use href="#i-key"></use></svg></span>
                            <span class="kind-txt"><b>口令更新</b><small>新装 App 输入口令激活时下载，不灰度</small></span>
                        </label>
                    </div>
                    <p class="kind-hint">两种可以都选：同一个版本既发成常规更新，又设成口令更新包</p>
                    <div class="row-2" data-role="fields">
                        <div class="field">
                            <label for="deploy-channel">渠道</label>
                            <select class="select" id="deploy-channel" name="channel" style="height:38px">
                                <template v-for="[L_ch, L_chStat] in $h.each(m.channels)"><template v-if="L_chStat.index">
                                </template><option :value.attr="$h.ak($h.p(L_ch, 'name'))">{{ $h.s($h.p(L_ch, 'name')) }}</option></template>
                            </select>
                        </div>
                        <div class="field" data-role="rollout-field">
                            <label for="deploy-rollout">灰度比例</label>
                            <input class="input" id="deploy-rollout" name="rollout" type="number" min="1" max="100" :value.attr="'100'" required>
                            <span class="hint">百分比，100 表示全部设备</span>
                        </div>
                    </div>
                    <p class="dlg-now" data-role="now" hidden></p>
                    <p class="dlg-now dlg-copy" data-role="copy" hidden></p>
                    <div class="note" data-role="note-hot"><svg class="i"><use href="#i-info"></use></svg><span>灰度按设备 ID 固定分组，不在灰度里的设备继续用上一版本。</span></div>
                    <div class="note" data-role="note-act" hidden><svg class="i"><use href="#i-info"></use></svg><span>已激活的设备不受影响，照常拿常规更新包。App 只装比自己新的版本：重新打包安装包以后，记得换成新上传的版本。</span></div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit" data-role="submit">发布</button>
                </div>
            </form>
        </dialog>

        <dialog id="rollout-dialog">
            <form method="post" :action.attr="$h.ak('/admin/rollout')" id="rollout-form">
                <input type="hidden" name="channel">
                <input type="hidden" name="runtime">
                <div class="dlg-head">
                    <h3>调整灰度 <span class="mono" data-role="title"></span></h3>
                    <p>按设备 ID 固定分组：调大时已经拿到新版本的设备不变，只是多放一些设备进来。</p>
                </div>
                <div class="dlg-body">
                    <div class="field">
                        <label for="ro-dlg">拿新版本的设备比例</label>
                        <div class="ro-pick">
                            <span class="input-unit ro-pct"><input class="input num" id="ro-dlg" name="rollout" type="number" min="1" max="100" required><em>%</em></span>
                            <span class="quick-picks"><template v-for="L_q in [10, 30, 50, 100]"><button type="button" data-set="ro-dlg" :data-v.attr="$h.av(L_q)">{{ $h.s($h.add(L_q, '%')) }}</button></template></span>
                        </div>
                    </div>
                    <div class="ro-preview">
                        <div class="ch-split-bar"><span class="new" data-role="bar-new"></span><span class="old" data-role="bar-old"></span></div>
                        <div class="ch-split-legend">
                            <span><i></i><b data-role="p-new"></b><span data-role="t-new"></span></span>
                            <span data-role="rest"><i class="old"></i><b data-role="p-old"></b><span data-role="t-old"></span></span>
                        </div>
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-ghost" type="button" data-plan-rollout data-role="plan"><svg class="i i-sm"><use href="#i-calendar"></use></svg>改成定时调整</button>
                    <span class="grow"></span>
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">调整</button>
                </div>
            </form>
        </dialog>

        <dialog id="rollback-dialog" class="wide">
            <form method="post" :action.attr="$h.ak('/admin/rollback')" id="rollback-form">
                <input type="hidden" name="channel">
                <input type="hidden" name="runtime">
                <div class="dlg-head">
                    <h3>回滚 <span class="mono" data-role="title"></span></h3>
                    <p>默认回到上一版本，也可以选更早的。会复制成新版本发布，已经更新的设备也会换回去。</p>
                </div>
                <div class="dlg-body scroll">
                    <div class="rb-list" role="radiogroup" aria-label="要回滚到的版本">
                        <template v-for="[L_e, L_eStat] in $h.each(m.choices)">
                            <template v-for="[L_c, L_cStat] in $h.each($h.p(L_e, 'value'))"><label class="rb-item" :data-runtime.attr="$h.av($h.p(L_e, 'key'))" :data-origin.attr="$h.av($h.z(L_c, 'origin'))" hidden>
                                <input type="radio" name="to" :value.attr="$h.ak($h.z(L_c, 'id'))">
                                <span class="rb-dot" aria-hidden="true"></span>
                                <span class="rb-main">
                                    <span class="rb-title"><b class="mono">{{ $h.s($h.ui.rid($h.z(L_c, 'id'))) }}</b><span class="pill" data-role="prev-tag" hidden>上一版本</span></span>
                                    <span class="rb-msg">{{ $h.s(($h.t($h.strings.isEmpty($h.z(L_c, 'message'))) ? '没有说明' : $h.z(L_c, 'message'))) }}</span>
                                </span>
                                <span class="rb-meta">
                                    <span :title.attr="$h.av($h.ui.full($h.z(L_c, 'at')))">{{ $h.s($h.ui.dt($h.z(L_c, 'at'))) }}</span>
                                    <span>{{ $h.s(($h.t($h.gt($h.z(L_c, 'devices'), 0)) ? $h.add($h.add('在用 ', $h.z(L_c, 'devices')), ' 台') : ($h.t($h.gt($h.z(L_c, 'copies'), 0)) ? $h.add($h.add('发布过 ', $h.add($h.z(L_c, 'copies'), 1)), ' 次') : ''))) }}</span>
                                </span>
                            </label></template>
                        </template>
                    </div>
                    <div class="list-none" data-role="none" hidden>这个 Runtime 没有更早的版本。要回到安装包自带的版本，用「回退内置版本」。</div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit"><svg class="i i-sm"><use href="#i-refresh"></use></svg>回滚到所选版本</button>
                </div>
            </form>
        </dialog>

        <dialog id="activation-dialog" class="wide">
            <form method="post" :action.attr="$h.ak('/admin/deploy')" id="activation-form">
                <input type="hidden" name="as" :value.attr="'activation'">
                <input type="hidden" name="channel">
                <div class="dlg-head">
                    <h3>口令更新包 <span class="mono" data-role="title"></span></h3>
                    <p>新装 App 输入口令激活时下载这个包，不灰度；已激活的设备照常拿常规更新包。</p>
                </div>
                <div class="dlg-body scroll">
                    <div class="rb-list" role="radiogroup" aria-label="做口令更新包的版本">
                        <template v-for="[L_e, L_eStat] in $h.each(m.choices)">
                            <template v-for="[L_c, L_cStat] in $h.each($h.p(L_e, 'value'))"><label class="rb-item" :data-runtime.attr="$h.av($h.p(L_e, 'key'))" :data-origin.attr="$h.av($h.z(L_c, 'origin'))" hidden>
                                <input type="radio" name="releaseId" :value.attr="$h.ak($h.z(L_c, 'id'))">
                                <span class="rb-dot" aria-hidden="true"></span>
                                <span class="rb-main">
                                    <span class="rb-title"><b class="mono">{{ $h.s($h.ui.rid($h.z(L_c, 'id'))) }}</b><span class="pill pill-act" data-role="cur-tag" hidden>现在的口令更新包</span><span class="pill" data-role="live-tag" hidden>常规更新包</span></span>
                                    <span class="rb-msg">{{ $h.s(($h.t($h.strings.isEmpty($h.z(L_c, 'message'))) ? '没有说明' : $h.z(L_c, 'message'))) }}</span>
                                </span>
                                <span class="rb-meta">
                                    <span :title.attr="$h.av($h.ui.full($h.z(L_c, 'at')))">{{ $h.s($h.ui.dt($h.z(L_c, 'at'))) }}</span>
                                    <span>{{ $h.s(($h.t($h.gt($h.z(L_c, 'devices'), 0)) ? $h.add($h.add('在用 ', $h.z(L_c, 'devices')), ' 台') : '')) }}</span>
                                </span>
                            </label></template>
                        </template>
                    </div>
                    <div class="list-none" data-role="none" hidden>这个 Runtime 还没有版本，先上传一个。</div>
                    <div class="note"><svg class="i"><use href="#i-info"></use></svg><span>App 只装比自己新的版本：口令更新包要比安装包里的版本新，重新打包安装包以后，记得换成新上传的版本。</span></div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit" data-role="submit"><svg class="i i-sm"><use href="#i-key"></use></svg>设为口令更新包</button>
                </div>
            </form>
        </dialog>

        <dialog id="upload-dialog" data-hash="upload">
            <div class="dlg-head">
                <h3>添加更新包</h3>
                <div class="seg" style="margin-top:12px" data-role="upload-modes">
                    <a href="#" class="on" data-upload-mode="file">上传 ZIP 包</a>
                    <a href="#" data-upload-mode="link">外部链接</a>
                </div>
            </div>
            <form method="post" :action.attr="$h.ak('/admin/upload')" enctype="multipart/form-data" id="upload-form" :data-done.attr="$h.av('/admin/releases')" data-upload-panel="file">
                <div class="dlg-body">
                    <p class="dlg-note">选 npx expo export 导出的 dist 目录压缩包（要有 metadata.json），Runtime 自动识别，相同的文件不会重复存。</p>
                    <label class="drop" for="upload-file">
                        <span class="drop-icon"><svg class="i"><use href="#i-upload"></use></svg></span>
                        <span class="drop-title" data-role="name">点击选择或拖拽 ZIP 文件到这里</span>
                        <span class="drop-sub" data-role="meta">仅支持 .zip，最大 200 MB</span>
                        <input type="file" id="upload-file" name="file" accept=".zip,application/zip" hidden>
                    </label>
                    <div class="progress"><span></span></div>
                    <div class="row-2" style="margin-top:16px">
                        <div class="field">
                            <label for="upload-note">说明</label>
                            <input class="input" id="upload-note" name="note" maxlength="200" placeholder="可不填，例如 修复登录页">
                        </div>
                        <div class="field">
                            <label for="upload-runtime">Runtime 版本</label>
                            <input class="input mono" id="upload-runtime" name="runtime" maxlength="64" placeholder="留空自动识别">
                            <span class="hint">包里没有 expoConfig.json 时按 1.0.0</span>
                        </div>
                    </div>
                    <div class="inline-form pub-now">
                        <label class="check"><input type="checkbox" name="activate" :value.attr="'true'" :checked.attr="''">上传后立即发布到</label>
                        <select class="select" name="channel" style="height:30px">
                            <template v-for="[L_ch, L_chStat] in $h.each(m.channels)"><template v-if="L_chStat.index">
                            </template><option :value.attr="$h.ak($h.p(L_ch, 'name'))">{{ $h.s($h.p(L_ch, 'name')) }}</option></template>
                        </select>
                        <select class="select" name="as" style="height:30px" aria-label="发布为" title="常规更新：已激活的设备检查更新时拿到；口令更新：新装 App 输入口令激活时下载">
                            <option :value.attr="'hot'">作为常规更新</option>
                            <option :value.attr="'activation'">作为口令更新</option>
                            <option :value.attr="'both'">作为常规更新和口令更新</option>
                        </select>
                    </div>
                    <label class="check" style="margin-top:10px"><input type="checkbox" name="forceUpdate" :value.attr="'true'">强制更新：下载完立即重启生效（默认下次启动生效）</label>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit" data-role="submit" disabled>上传</button>
                </div>
            </form>
            <form method="post" :action.attr="$h.ak('/admin/addUrl')" id="link-form" :data-done.attr="$h.av('/admin/releases')" data-upload-panel="link" hidden>
                <div class="dlg-body">
                    <div class="field">
                        <label for="link-url">dist 目录地址</label>
                        <input class="input mono" id="link-url" name="externalUrl" type="url" required autocomplete="off" placeholder="https://cdn.example.com/ota/v12/">
                        <span class="hint">把 dist 目录原样传到 CDN / 对象存储（公共读），填目录地址。浏览器能打开「地址/metadata.json」就对了</span>
                    </div>
                    <div class="field">
                        <label for="link-note">说明</label>
                        <input class="input" id="link-note" name="note" maxlength="200" placeholder="可不填，例如 修复登录页">
                    </div>
                    <ol class="steps">
                        <li>服务器会读一遍文件算哈希，不保存文件</li>
                        <li>App 检查更新时，服务器只返回签名后的清单</li>
                        <li>App 直接从这个地址下载文件并核对哈希，不经过本服务器</li>
                    </ol>
                    <div class="inline-form pub-now">
                        <label class="check"><input type="checkbox" name="activate" :value.attr="'true'" :checked.attr="''">添加后立即发布到</label>
                        <select class="select" name="channel" style="height:30px">
                            <template v-for="[L_ch, L_chStat] in $h.each(m.channels)"><template v-if="L_chStat.index">
                            </template><option :value.attr="$h.ak($h.p(L_ch, 'name'))">{{ $h.s($h.p(L_ch, 'name')) }}</option></template>
                        </select>
                        <select class="select" name="as" style="height:30px" aria-label="发布为" title="常规更新：已激活的设备检查更新时拿到；口令更新：新装 App 输入口令激活时下载">
                            <option :value.attr="'hot'">作为常规更新</option>
                            <option :value.attr="'activation'">作为口令更新</option>
                            <option :value.attr="'both'">作为常规更新和口令更新</option>
                        </select>
                    </div>
                    <label class="check" style="margin-top:10px"><input type="checkbox" name="forceUpdate" :value.attr="'true'">强制更新：下载完立即重启生效</label>
                    <div class="upload-progress link-read" data-role="read" hidden>
                        <div class="up-head"><b data-role="read-title">正在读取链接</b><span class="up-side"><span data-role="read-pct"></span><button class="btn btn-ghost btn-sm" type="button" data-role="read-stop" hidden>取消下载</button></span></div>
                        <span class="up-bar"><i data-role="read-bar"></i></span>
                        <small data-role="read-text">填好地址就开始读，读完再点添加马上就好</small>
                        <small class="link-slow" data-role="read-slow" hidden></small>
                    </div>
                    <div class="form-error" data-role="error" hidden></div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit" data-role="submit">添加</button>
                </div>
            </form>
        </dialog>

        <dialog id="installer-dialog" v-if="$h.t(L_canRelease)">
            <div class="dlg-head">
                <h3>上传安装包</h3>
                <p>选打好的 APK 或 IPA，只读里面的配置做检查，不改动，也不推给设备。</p>
            </div>
            <form method="post" :action.attr="$h.ak('/admin/installers')" enctype="multipart/form-data" id="installer-form" :data-done.attr="$h.av('/admin/releases#installers')">
                <div class="dlg-body">
                    <label class="drop" for="installer-file">
                        <span class="drop-icon"><svg class="i"><use href="#i-phone"></use></svg></span>
                        <span class="drop-title" data-role="name">点击选择或拖拽 APK / IPA 到这里</span>
                        <span class="drop-sub" data-role="meta">.apk 或 .ipa，最大 1 GB</span>
                        <input type="file" id="installer-file" name="file" accept=".apk,.ipa" hidden>
                    </label>
                    <div class="progress"><span></span></div>
                    <div class="field" style="margin-top:16px">
                        <label for="installer-note">备注</label>
                        <input class="input" id="installer-note" name="note" maxlength="500" placeholder="可不填，例如 应用商店 1.2.0 正式包">
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit" data-role="submit" disabled>上传并检查</button>
                </div>
            </form>
        </dialog>

        <dialog id="detail-dialog" class="xl">
            <div class="dlg-head">
                <h3>版本 <span class="mono" data-role="title"></span> <span class="pill warn" data-role="force" hidden>强制更新</span></h3>
                <p data-role="sub"></p>
            </div>
            <div class="dlg-body scroll">
                <div class="verify-loading" data-role="loading"><span class="spinner"></span><span>正在读取…</span></div>
                <div data-role="body" hidden>
                    <div class="mini-stats">
                        <div><span>Runtime</span><b class="mono" data-role="runtime"></b></div>
                        <div><span>平台</span><b data-role="platforms"></b></div>
                        <div><span>大小</span><b data-role="size"></b></div>
                        <div><span>设备 · 30 天</span><b data-role="devices"></b></div>
                    </div>
                    <dl class="kv" style="grid-template-columns:96px minmax(0,1fr);margin-bottom:18px">
                        <dt>版本 ID</dt><dd class="mono small" data-role="id"></dd>
                        <dt>线上</dt><dd data-role="live"></dd>
                        <dt>来源</dt><dd data-role="source"></dd>
                        <dt>外部链接</dt><dd class="mono small" data-role="external"></dd>
                    </dl>
                    <div class="section-label">安装情况</div>
                    <div class="funnel four" style="margin-bottom:10px">
                        <div title="检查更新时服务器把这个版本发给了多少台设备"><span>推送</span><b data-role="f-offered">0</b></div>
                        <div title="设备下载这个版本启动包的次数（增量补丁、完整下载、CDN 加起来），重试会多算"><span>下载</span><b><span data-role="f-downloads">0</span><small data-role="f-drate"></small></b></div>
                        <div title="重启后已经运行这个版本的设备"><span>装上</span><b><span data-role="f-installed">0</span><small data-role="f-rate"></small></b>
                            <div class="progress-line"><span data-role="f-bar" style="width:0"></span></div></div>
                        <div data-role="f-failed-box" title="启动这个版本失败、自动退回之前版本的设备"><span>启动失败</span><b><span data-role="f-failed">0</span><small data-role="f-frate"></small></b></div>
                    </div>
                    <p class="small muted" data-role="traffic" style="margin-bottom:18px"></p>
                    <div data-role="reasons-box" hidden style="margin-bottom:18px">
                        <div class="section-label">失败原因 <span class="muted" style="font-weight:400" data-role="reasons-sub">按错误的第一行归类，同一台设备同样的错误 6 小时内只算一次</span></div>
                        <ul class="reason-list" data-role="reasons"></ul>
                    </div>
                    <div data-role="errors-box" hidden style="margin-bottom:18px">
                        <div class="section-label">最近的启动错误 <span class="muted" style="font-weight:400">设备启动失败时上报的错误信息</span></div>
                        <ul class="err-list" data-role="errors"></ul>
                    </div>
                    <form method="post" :action.attr="$h.ak('/admin/release/edit')" class="card" style="margin-bottom:18px" data-role="edit">
                        <input type="hidden" name="releaseId">
                        <div class="card-body" style="padding:14px 16px">
                            <div class="field" style="margin-bottom:10px">
                                <label for="edit-note">说明</label>
                                <input class="input" id="edit-note" name="note" maxlength="500" placeholder="可不填，App 能拿到这段说明（extra.ota.note）">
                            </div>
                            <label class="check"><input type="checkbox" name="forceUpdate" :value.attr="'true'">强制更新：下载完立即重启生效</label>
                        </div>
                        <div class="card-foot" style="padding:10px 16px">
                            <span class="hint">已经装上的设备不受影响，之后检查更新的按新设置</span>
                            <button class="btn btn-sm" type="submit">保存</button>
                        </div>
                    </form>
                    <div class="struct-head">
                        <div class="section-label" data-role="files-title">包结构</div>
                        <label class="struct-base"><span>对比</span><select class="select" data-role="base" aria-label="拿来对比的版本"></select></label>
                    </div>
                    <p class="small muted struct-sum" data-role="struct-sum"></p>
                    <div class="file-table table-wrap">
                        <table>
                            <thead><tr><th>平台</th><th>文件</th><th>和对比版本比</th><th class="right">大小</th><th class="right">更新时下载</th></tr></thead>
                            <tbody data-role="files"></tbody>
                        </table>
                    </div>
                    <div data-role="patch-box" hidden>
                        <div class="section-label" style="margin-top:18px">启动包增量补丁</div>
                        <div class="file-table table-wrap">
                            <table>
                                <thead><tr><th>从（启动包哈希）</th><th>状态</th><th class="right">补丁大小</th><th class="right">占完整文件</th></tr></thead>
                                <tbody data-role="patches"></tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
            <div class="dlg-foot">
                <button class="btn btn-secondary" type="button" data-role="verify">校验</button>
                <button class="btn" type="button" data-close>关闭</button>
            </div>
        </dialog>

        <dialog id="verify-dialog" class="wide">
            <div class="dlg-head">
                <h3>校验版本 <span class="mono" data-role="title"></span></h3>
                <p data-role="sub">检查更新包是否完整、能否被 App 正确验签</p>
            </div>
            <div class="dlg-body" style="padding-top:12px">
                <div class="verify-loading" data-role="loading">
                    <span class="spinner"></span>
                    <span data-role="loading-text">正在校验…</span>
                </div>
                <div data-role="result" hidden>
                    <div class="hero" data-role="hero" style="padding:0 0 14px">
                        <div class="hero-icon"><svg class="i"><use href="#i-tick" data-role="hero-icon"></use></svg></div>
                        <div><h2 data-role="headline"></h2><p data-role="summary"></p></div>
                    </div>
                    <div class="card"><ul class="checks" data-role="checks"></ul></div>
                </div>
            </div>
            <div class="dlg-foot">
                <button class="btn btn-secondary" type="button" data-role="again" hidden>重新校验</button>
                <button class="btn" type="button" data-close>关闭</button>
            </div>
        </dialog>

        <dialog id="guard-dialog" v-if="$h.t(L_canRelease)">
            <form method="post" :action.attr="$h.ak('/admin/guard')">
                <div class="dlg-head">
                    <h3>启动失败自动回滚</h3>
                    <p>线上版本在设备上启动失败太多时，不用等人处理，自动回滚到上一版本。只对这个 App 生效。</p>
                </div>
                <div class="dlg-body">
                    <label class="check"><input type="checkbox" name="on" :value.attr="'true'" :checked.attr="$h.t($h.p(m.guard, 'on')) ? 'checked' : null">开启自动回滚</label>
                    <div class="row-2" style="margin-top:14px">
                        <div class="field">
                            <label for="gd-rate">启动失败率达到</label>
                            <select class="select" id="gd-rate" name="rate" style="height:38px">
                                <template v-for="[L_r, L_rStat] in $h.each(m.guardRates)"><template v-if="L_rStat.index">
                                </template><option :value.attr="$h.ak(L_r)" :selected.attr="$h.t($h.eq(L_r, $h.p(m.guard, 'rate'))) ? 'selected' : null">{{ $h.s($h.add(L_r, '%')) }}</option></template>
                            </select>
                        </div>
                        <div class="field">
                            <label for="gd-min">并且失败的设备至少</label>
                            <select class="select" id="gd-min" name="min" style="height:38px">
                                <template v-for="[L_n, L_nStat] in $h.each(m.guardMins)"><template v-if="L_nStat.index">
                                </template><option :value.attr="$h.ak(L_n)" :selected.attr="$h.t($h.eq(L_n, $h.p(m.guard, 'min'))) ? 'selected' : null">{{ $h.s($h.add(L_n, ' 台')) }}</option></template>
                            </select>
                        </div>
                    </div>
                    <ul class="guard-notes">
                        <li>和手动回滚一样：回到上一版本，复制成新版本发布，已经更新的设备下次检查更新时也会换回去</li>
                        <li>记在发布记录和操作日志里，操作人是「自动回滚」；群机器人发一条消息，Webhook 发 <code>release.auto_rollback</code></li>
                        <li>每个版本只自动回滚一次，回滚后又手动发布它就不再自动回滚</li>
                        <li>渠道没有上一版本时不会自动回退到安装包内置版本，照常只告警</li>
                    </ul>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">保存</button>
                </div>
            </form>
        </dialog>

        <dialog id="rollout-plan-dialog">
            <form method="post" :action.attr="$h.ak('/admin/schedules/rollout')" id="rollout-plan-form">
                <input type="hidden" name="channel" :value.attr="''">
                <input type="hidden" name="runtime" :value.attr="''">
                <div class="dlg-head">
                    <h3>定时调整灰度</h3>
                    <p data-role="sub"></p>
                </div>
                <div class="dlg-body">
                    <div class="field">
                        <label for="plan-percent">调整到（%）</label>
                        <div class="stepper">
                            <button type="button" class="st-btn" data-step="plan-percent" data-delta="-10" aria-label="减少">−</button>
                            <input class="input num" id="plan-percent" name="percent" type="number" min="1" max="100" :value.attr="'100'" required>
                            <button type="button" class="st-btn" data-step="plan-percent" data-delta="10" aria-label="增加">+</button>
                        </div>
                        <div class="quick-picks"><template v-for="L_q in [10, 30, 50, 100]"><button type="button" data-set="plan-percent" :data-v.attr="$h.av(L_q)">{{ $h.s($h.add(L_q, '%')) }}</button></template></div>
                    </div>
                    <div class="field">
                        <label for="plan-at">执行时间</label>
                        <input class="input" id="plan-at" name="runAt" type="datetime-local" required data-future>
                        <span class="hint">到时间如果线上版本已经换了，就不执行，免得把新版本直接放量</span>
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">设好</button>
                </div>
            </form>
        </dialog>

    </main></template>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('版本发布'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
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
