<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'app'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'App 设置'"/>
    <main class="content">

        <div class="page-head">
            <div class="page-title"><div>
                <h1>{{ $h.s($h.add($h.p(m.app, 'name'), ' · 设置')) }}</h1>
                <p>
                    标识 <span class="mono">{{ $h.s($h.p(m.app, 'slug')) }}</span> · keyid <span class="mono">{{ $h.s($h.p(m.app, 'keyId')) }}</span>
                    · 创建于 <span>{{ $h.s($h.ui.dt($h.p(m.app, 'createdAt'))) }}</span>
                </p>
            </div></div>
            <div class="page-actions">
                <a class="btn btn-secondary" :href.attr="$h.ak('/admin/apps')"><svg class="i"><use href="#i-package"></use></svg>所有 App</a>
            </div>
        </div>

        <nav class="tabs" data-tabs>
            <a href="#basic" data-tab="basic">接入与设置</a>
            <a href="#channels" data-tab="channels">渠道与测试设备 <span class="count">{{ $h.s($h.lists.size(m.channelRows)) }}</span></a>
            <a href="#tokens" data-tab="tokens">发布令牌 <span class="count">{{ $h.s($h.lists.size(m.tokens)) }}</span></a>
            <a href="#codes-api" data-tab="codes-api">口令接口</a>
            <a href="#webhook" data-tab="webhook">通知</a>
            <a href="#backup" data-tab="backup">备份与恢复</a>
            <a href="#delete" data-tab="delete" class="danger" v-if="$h.t(m.owner)">删除 App</a>
        </nav>

        
        <section data-tab-panel="basic">
            <div class="grid grid-wide">
                <section class="card">
                    <div class="card-head">
                        <h2>接入配置</h2>
                        <a class="tail link" :href.attr="$h.ak('/admin/signing/certificate.pem')"><svg class="i i-sm"><use href="#i-download"></use></svg>下载证书</a>
                    </div>
                    <div class="card-body">
                        <ol class="steps" style="margin-top:0">
                            <li>下载证书，放到 App 项目的 <code>certs/certificate.pem</code></li>
                            <li>按下面修改 <code>app.json</code>，重新打包安装</li>
                            <li>在「版本发布」上传更新包或用命令行发布，App 下次启动就会更新</li>
                        </ol>
                        <div class="code" style="margin:0">
                            <div class="code-bar"><span>app.json</span><button class="code-copy" type="button" data-copy-pre>复制</button></div>
<pre>{
  "expo": {
    "runtimeVersion": { "policy": "appVersion" },
    "updates": {
      "url": "<span>{{ $h.s(m.manifestUrl) }}</span>",
      "requestHeaders": { "expo-channel-name": "production" },
      "codeSigningCertificate": "./certs/certificate.pem",
      "codeSigningMetadata": { "keyid": "<span>{{ $h.s($h.p(m.app, 'keyId')) }}</span>", "alg": "rsa-v1_5-sha256" }
    }
  }
}</pre>
                        </div>
                    </div>
                </section>

                <section class="card">
                    <div class="card-head"><h2>基本设置</h2></div>
                    <div class="card-body">
                        <dl class="kv-lines" style="grid-template-columns:84px minmax(0,1fr)">
                            <dt>App 图标</dt>
                            <dd class="icon-row">
                                <span :class="['icon-preview', $h.av(($h.t(!$h.eq($h.p(m.app, 'iconVersion'), null)) ? 'has-icon' : null))]"><img :src.attr="$h.ak($h.cat('/admin/apps/', $h.p(m.app, 'id'), '/icon?v=', $h.p(m.app, 'iconVersion')))" alt="" v-if="!$h.eq($h.p(m.app, 'iconVersion'), null)"><template v-if="$h.eq($h.p(m.app, 'iconVersion'), null)">{{ $h.s($h.strings.substring($h.p(m.app, 'name'), 0, 1)) }}</template></span>
                                <form method="post" enctype="multipart/form-data" :action.attr="$h.ak('/admin/apps/icon')" v-if="$h.t(m.canSettings)">
                                    <label class="btn btn-secondary btn-sm"><input type="file" name="file" accept="image/png,image/jpeg,image/webp" hidden data-autosubmit><svg class="i i-sm"><use href="#i-upload"></use></svg>{{ $h.s(($h.t($h.eq($h.p(m.app, 'iconVersion'), null)) ? '上传图标' : '换一张')) }}</label>
                                </form>
                                <form method="post" :action.attr="$h.ak('/admin/apps/icon/delete')" v-if="$h.t(($h.t(m.canSettings) &amp;&amp; $h.t(!$h.eq($h.p(m.app, 'iconVersion'), null))))">
                                    <button class="btn btn-ghost btn-sm" type="submit">去掉</button>
                                </form>
                            </dd>
                            <dt>App 名称</dt>
                            <dd>
                                <form class="inline-form grow" method="post" :action.attr="$h.ak('/admin/apps/rename')" v-if="$h.t(m.canSettings)">
                                    <input class="input" name="name" :value.attr="$h.ak($h.p(m.app, 'name'))" maxlength="100" required title="App 的真实名字" aria-label="App 名称">
                                    <button class="btn btn-secondary btn-sm" type="submit">保存</button>
                                </form>
                                <span v-if="!$h.t(m.canSettings)">{{ $h.s($h.p(m.app, 'name')) }}</span>
                            </dd>
                            <dt>下载链接</dt>
                            <dd>
                                <form class="inline-form grow" method="post" :action.attr="$h.ak('/admin/apps/download-url')" v-if="$h.t(m.canSettings)">
                                    <span class="input-adorn">
                                        <input class="input" name="url" type="url" :value.attr="$h.ak($h.p(m.app, 'downloadUrl'))" maxlength="500" placeholder="App Store 或官网下载页，可不填" aria-label="下载链接">
                                        <button class="icon-btn" type="button" :data-copy.attr="$h.av($h.p(m.app, 'downloadUrl'))" data-copy-label="已复制下载链接" title="复制" aria-label="复制下载链接" v-if="!$h.t($h.strings.isEmpty($h.p(m.app, 'downloadUrl')))"><svg class="i i-sm"><use href="#i-copy"></use></svg></button>
                                    </span>
                                    <button class="btn btn-secondary btn-sm" type="submit">保存</button>
                                </form>
                                <span class="mono small" v-if="$h.t(($h.t(!$h.t(m.canSettings)) &amp;&amp; $h.t(!$h.t($h.strings.isEmpty($h.p(m.app, 'downloadUrl'))))))">{{ $h.s($h.p(m.app, 'downloadUrl')) }}</span>
                                <span class="muted" v-if="$h.t(($h.t(!$h.t(m.canSettings)) &amp;&amp; $h.t($h.strings.isEmpty($h.p(m.app, 'downloadUrl')))))">—</span>
                                <button class="icon-btn" type="button" :data-copy.attr="$h.av($h.p(m.app, 'downloadUrl'))" data-copy-label="已复制下载链接" title="复制" aria-label="复制下载链接" v-if="$h.t(($h.t(!$h.t(m.canSettings)) &amp;&amp; $h.t(!$h.t($h.strings.isEmpty($h.p(m.app, 'downloadUrl'))))))"><svg class="i i-sm"><use href="#i-copy"></use></svg></button>
                            </dd>
                            <dt>标识</dt><dd class="mono">{{ $h.s($h.p(m.app, 'slug')) }}</dd>
                            <dt>清单地址</dt>
                            <dd><span class="mono small">{{ $h.s(m.manifestUrl) }}</span>
                                <button class="icon-btn" type="button" :data-copy.attr="$h.av(m.manifestUrl)" title="复制" aria-label="复制清单地址"><svg class="i i-sm"><use href="#i-copy"></use></svg></button></dd>
                            <dt>运行模式</dt>
                            <dd><span :class="['badge', $h.av(($h.t($h.p(m.app, 'redeemOpen')) ? 'badge-solid' : 'badge-line'))]"><span class="dot"></span><span>{{ $h.s(($h.t($h.p(m.app, 'redeemOpen')) ? 'B 面 · 口令可以兑换' : 'A 面 · 审核模式')) }}</span></span>
                                <a class="link" :href.attr="$h.ak('/admin')" v-if="$h.t($h.access.can('mode'))">去切换<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></dd>
                            <dt>增量更新</dt>
                            <dd>
                                <span :class="['badge', $h.av(($h.t($h.p(m.app, 'patchEnabled')) ? 'badge-solid' : 'badge-line'))]"><span class="dot"></span><span>{{ $h.s(($h.t($h.p(m.app, 'patchEnabled')) ? '已开启' : '已关闭')) }}</span></span>
                                <form method="post" :action.attr="$h.ak('/admin/apps/patch')" v-if="$h.t(m.canSettings)">
                                    <input type="hidden" name="enabled" :value.attr="$h.ak(!$h.t($h.p(m.app, 'patchEnabled')))">
                                    <button class="btn btn-ghost btn-sm" type="submit">{{ $h.s(($h.t($h.p(m.app, 'patchEnabled')) ? '关闭增量更新' : '开启增量更新')) }}</button>
                                </form>
                            </dd>
                            <dt>按设备去重</dt>
                            <dd class="dedupe-dd">
                                <span :class="['badge', $h.av(($h.t($h.p(m.app, 'dedupeDevices')) ? 'badge-solid' : 'badge-line'))]"><span class="dot"></span><span>{{ $h.s(($h.t($h.p(m.app, 'dedupeDevices')) ? '已开启' : '已关闭')) }}</span></span>
                                <form method="post" :action.attr="$h.ak('/admin/apps/dedupe')" v-if="$h.t(m.canSettings)">
                                    <input type="hidden" name="enabled" :value.attr="$h.ak(!$h.t($h.p(m.app, 'dedupeDevices')))">
                                    <button class="btn btn-ghost btn-sm" type="submit">{{ $h.s(($h.t($h.p(m.app, 'dedupeDevices')) ? '关闭按设备去重' : '开启按设备去重')) }}</button>
                                </form>
                                <template v-for="L_keep in [$h.p(m.app, 'fingerprintKeepDays')]"><template v-if="$h.t($h.p(m.app, 'dedupeDevices'))">
                                    <div class="pop" data-pop v-if="$h.t(m.canSettings)">
                                        <button class="btn btn-secondary btn-sm fp-btn" type="button" data-pop-toggle aria-haspopup="true" aria-expanded="false" title="设备多久没打开 App 就清掉它的指纹">
                                            <span>指纹保留</span><b>{{ $h.s(($h.t($h.eq(L_keep, 0)) ? '一直留着' : $h.cat(L_keep, ' 天'))) }}</b><svg class="i i-sm"><use href="#i-chevron-down"></use></svg>
                                        </button>
                                        <div class="menu menu-float fp-menu" data-pop-menu hidden>
                                            <div class="menu-label">设备多久没打开 App 就清掉指纹</div>
                                            <form method="post" :action.attr="$h.ak('/admin/apps/dedupe-keep')">
                                                <template v-for="[L_k, L_kStat] in $h.each(m.fpKeepOptions)"><button :class="['menu-item', $h.av(($h.t($h.eq(L_k, L_keep)) ? 'on' : null))]" type="submit" name="days" :value.attr="$h.ak(L_k)"><span>{{ $h.s(($h.t($h.eq(L_k, 0)) ? '一直留着' : $h.cat(L_k, ' 天'))) }}</span><small class="fp-def" v-if="$h.t($h.eq(L_k, m.fpKeepDefault))">默认</small><svg class="i i-sm tail" v-if="$h.t($h.eq(L_k, L_keep))"><use href="#i-tick"></use></svg></button></template>
                                            </form>
                                            <p class="fp-menu-note">清掉后它再激活会重新算一次；改短了马上清掉超出的，之后每天凌晨也会清</p>
                                        </div>
                                    </div>
                                    <span class="fp-ro" v-if="!$h.t(m.canSettings)"><span>指纹保留</span><b>{{ $h.s(($h.t($h.eq(L_keep, 0)) ? '一直留着' : $h.cat(L_keep, ' 天'))) }}</b></span>
                                </template></template>
                            </dd>
                            <dt>签名</dt>
                            <dd><a class="link" :href.attr="$h.ak('/admin/signing')">{{ $h.s(($h.t(!$h.eq(m.signing, null)) ? $h.p(m.signing, 'chip') : '签名配置')) }}</a></dd>
                        </dl>
                        <p class="small muted" style="margin-top:14px">开着增量更新，设备只下载和上一版的差异，一般能省 70% 以上的流量。</p>
                        <p class="small muted" style="margin-top:8px">开着按设备去重，同一台手机在你别的 App 里已经算过一次有效激活，这个 App 就不再算。想让不同 App 各算各的就关掉。</p>
                    </div>
                </section>
            </div>
        </section>

        
        <section data-tab-panel="channels" hidden>
            <section class="card">
                <div class="card-head">
                    <h2>渠道</h2>
                    <span class="tail muted small">App 用请求头 expo-channel-name 选择渠道，默认 production</span>
                </div>
                <div class="table-wrap">
                    <table>
                        <thead><tr><th>渠道名</th><th>线上版本</th><th>创建时间</th><th class="right">操作</th></tr></thead>
                        <tbody data-collapse="8" data-collapse-unit="个渠道">
                        <template v-for="[L_row, L_rowStat] in $h.each(m.channelRows)"><template v-if="L_rowStat.index">
                        </template><template v-for="L_c in [$h.p(L_row, 'channel')]"><tr>
                            <td>
                                <span class="mono strong">{{ $h.s($h.p(L_c, 'name')) }}</span>
                                <span class="pill line" style="margin-left:6px" v-if="$h.t($h.p(L_row, 'defaultChannel'))">默认</span>
                            </td>
                            <td>
                                <span class="muted" v-if="$h.t($h.lists.isEmpty($h.p(L_row, 'live')))">还没有发布</span>
                                <template v-for="[L_l, L_lStat] in $h.each($h.p(L_row, 'live'))"><template v-if="L_lStat.index">
                                </template><div class="small">
                                    <span class="rt-tag">{{ $h.s($h.p(L_l, 'runtime')) }}</span>
                                    <span v-if="$h.t($h.p(L_l, 'rollbackToEmbedded'))">安装包内置版本</span>
                                    <span class="rid" v-if="$h.t(($h.t(!$h.t($h.p(L_l, 'rollbackToEmbedded'))) &amp;&amp; $h.t(!$h.eq($h.p(L_l, 'release'), null))))">{{ $h.s($h.ui.rid($h.p($h.p(L_l, 'release'), 'id'))) }}</span>
                                    <span class="muted" v-if="$h.t(($h.t(($h.t(!$h.t($h.p(L_l, 'rollbackToEmbedded'))) &amp;&amp; $h.t(!$h.eq($h.p(L_l, 'release'), null)))) &amp;&amp; $h.t($h.lt($h.p(L_l, 'rollout'), 100))))">{{ $h.s($h.add($h.add('灰度 ', $h.p(L_l, 'rollout')), '%')) }}</span>
                                </div></template>
                            </td>
                            <td class="muted nowrap">{{ $h.s($h.ui.dt($h.p(L_c, 'createdAt'))) }}</td>
                            <td class="actions">
                                <span class="muted small" v-if="$h.t($h.p(L_row, 'defaultChannel'))">不能删除</span>
                                <form method="post" :action.attr="$h.ak('/admin/apps/channel/delete')" :data-confirm.attr="$h.av($h.cat('删除渠道 ', $h.p(L_c, 'name'), '？'))" data-confirm-body="渠道和发布记录会删掉，版本保留。还在用这个渠道的设备会拿到「没有更新」，口令绑定的渠道不会自动改。" data-confirm-ok="删除渠道" data-confirm-danger v-if="$h.t(($h.t(!$h.t($h.p(L_row, 'defaultChannel'))) &amp;&amp; $h.t(m.canSettings)))">
                                    <input type="hidden" name="name" :value.attr="$h.ak($h.p(L_c, 'name'))">
                                    <button class="btn btn-ghost btn-sm" type="submit"><svg class="i i-sm"><use href="#i-trash"></use></svg>删除</button>
                                </form>
                            </td>
                        </tr></template></template>
                        </tbody>
                    </table>
                </div>
                <form class="card-foot" method="post" :action.attr="$h.ak('/admin/apps/channel')" v-if="$h.t(m.canSettings)">
                    <span class="hint">比如 beta、staging。口令可以绑定渠道，兑换后 App 就用那个渠道更新</span>
                    <input class="input mono" name="name" required maxlength="40" pattern="[a-z0-9][a-z0-9._\-]{0,39}" placeholder="渠道名" style="width:140px;height:30px">
                    <button class="btn btn-secondary btn-sm" type="submit"><svg class="i i-sm"><use href="#i-plus"></use></svg>新建渠道</button>
                </form>
            </section>
            <section class="card" style="margin-top:20px">
                <div class="card-head">
                    <h2><svg class="i"><use href="#i-flask"></use></svg>测试设备</h2>
                    <span class="muted small">这些设备改用指定的渠道检查更新，不用重新打包。先发测试渠道，验证好再发 production；勾了灰度白名单的，灰度没到 100% 时也先拿新版本</span>
                </div>
                <div class="empty" style="padding:26px 20px" v-if="$h.t($h.lists.isEmpty(m.testers))">
                    还没有测试设备。在「设备统计」里点设备旁边的「设为测试设备」最方便，也可以在下面直接填设备 ID
                </div>
                <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty(m.testers))">
                    <table class="compact">
                        <thead><tr><th>设备</th><th>设备 ID</th><th>用渠道</th><th>添加时间</th><th class="right">操作</th></tr></thead>
                        <tbody data-collapse="8" data-collapse-unit="台测试设备">
                        <template v-for="[L_t, L_tStat] in $h.each(m.testers)"><template v-if="L_tStat.index">
                        </template><tr>
                            <td class="strong">{{ $h.s($h.elvis($h.p(L_t, 'name'), '—')) }}</td>
                            <td class="mono small" :title.attr="$h.av($h.p(L_t, 'clientId'))">{{ $h.s($h.p(L_t, 'clientId')) }}</td>
                            <td><span class="pill line">{{ $h.s($h.p(L_t, 'channel')) }}</span><span class="pill" style="margin-left:6px" title="灰度白名单：渠道灰度没到 100% 时也先拿新版本" v-if="$h.t($h.p(L_t, 'rolloutFirst'))">灰度白名单</span></td>
                            <td class="muted nowrap">{{ $h.s($h.ui.dt($h.p(L_t, 'createdAt'))) }}</td>
                            <td class="actions">
                                <form method="post" :action.attr="$h.ak('/admin/apps/testers/delete')" :data-confirm.attr="$h.av($h.cat('移除测试设备 ', $h.elvis($h.p(L_t, 'name'), $h.p(L_t, 'clientId')), '？'))" data-confirm-body="移除后这台设备恢复用 App 里写的渠道。" data-confirm-ok="移除" v-if="$h.t(m.canSettings)">
                                    <input type="hidden" name="id" :value.attr="$h.ak($h.p(L_t, 'id'))">
                                    <button class="btn btn-ghost btn-sm" type="submit">移除</button>
                                </form>
                            </td>
                        </tr></template>
                        </tbody>
                    </table>
                </div>
                <form class="card-foot" method="post" :action.attr="$h.ak('/admin/apps/testers')" v-if="$h.t(m.canSettings)">
                    <input class="input mono" name="clientId" required maxlength="100" placeholder="设备 ID（eas-client-id）" style="width:250px;height:30px">
                    <input class="input" name="name" maxlength="50" placeholder="名字，例如 张三的 iPhone" style="width:200px;height:30px">
                    <select class="select" name="channel" style="height:30px" aria-label="用哪个渠道">
                        <template v-for="[L_row, L_rowStat] in $h.each(m.channelRows)"><template v-if="L_rowStat.index">
                        </template><option :value.attr="$h.ak($h.p($h.p(L_row, 'channel'), 'name'))" :selected.attr="$h.t(!$h.t($h.p(L_row, 'defaultChannel'))) ? 'selected' : null">{{ $h.s($h.p($h.p(L_row, 'channel'), 'name')) }}</option></template>
                    </select>
                    <label class="check" title="渠道灰度没到 100% 时，这台设备也先拿新版本"><input type="checkbox" name="rolloutFirst" :value.attr="'true'">灰度白名单</label>
                    <button class="btn btn-secondary btn-sm" type="submit"><svg class="i i-sm"><use href="#i-plus"></use></svg>添加</button>
                </form>
            </section>
        </section>

        
        <section data-tab-panel="tokens" hidden>
            <section class="card section-gap">
                <div class="card-head">
                    <h2>发布令牌</h2>
                    <span class="muted small">命令行和 CI 用，只能给这个 App 上传、发布、回滚</span>
                    <span class="tail" v-if="$h.t(m.canRelease)"><button class="btn btn-sm" type="button" data-open="token-new-dialog"><svg class="i i-sm"><use href="#i-plus"></use></svg>新建发布令牌</button></span>
                </div>
                <div class="empty" v-if="$h.t($h.lists.isEmpty(m.tokens))">
                    <div class="empty-icon"><svg class="i"><use href="#i-key"></use></svg></div>
                    <b>还没有发布令牌</b>命令行和 CI 发布要用发布令牌。每个用途单独建一个，以后可以单独撤销
                    <div v-if="$h.t(m.canRelease)"><button class="btn" type="button" data-open="token-new-dialog"><svg class="i"><use href="#i-plus"></use></svg>新建发布令牌</button></div>
                </div>
                <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty(m.tokens))">
                    <table>
                        <thead><tr><th>名称</th><th>令牌</th><th>创建时间</th><th>最近使用</th><th class="right">操作</th></tr></thead>
                        <tbody data-collapse="8" data-collapse-unit="个令牌">
                        <template v-for="[L_t, L_tStat] in $h.each(m.tokens)"><template v-if="L_tStat.index">
                        </template><tr>
                            <td class="strong">{{ $h.s($h.p(L_t, 'name')) }}</td>
                            <td class="mono small muted" :title.attr="$h.av(($h.t($h.eq($h.p(L_t, 'tokenPrefix'), null)) ? '在这个版本之前创建的，没有记录开头几位' : null))">{{ $h.s(($h.t(!$h.eq($h.p(L_t, 'tokenPrefix'), null)) ? $h.add($h.p(L_t, 'tokenPrefix'), '…') : '—')) }}</td>
                            <td class="muted nowrap">{{ $h.s($h.ui.dt($h.p(L_t, 'createdAt'))) }}</td>
                            <td class="muted nowrap">{{ $h.s(($h.t(!$h.eq($h.p(L_t, 'lastUsedAt'), null)) ? $h.ui.ago($h.p(L_t, 'lastUsedAt')) : '从未使用')) }}</td>
                            <td class="actions">
                                <form method="post" :action.attr="$h.ak($h.link('/admin/apps/token/{id}/revoke', [['id', $h.p(L_t, 'id')]]))" :data-confirm.attr="$h.av($h.cat('撤销令牌 ', $h.p(L_t, 'name'), '？'))" data-confirm-body="用这个令牌的命令行和 CI 会立即失效，不能恢复。" data-confirm-ok="撤销" data-confirm-danger v-if="$h.t(m.canRelease)">
                                    <button class="btn btn-ghost btn-sm" type="submit">撤销</button>
                                </form>
                            </td>
                        </tr></template>
                        </tbody>
                    </table>
                </div>
                <div class="card-foot"><span class="hint">令牌只在创建时显示一次，列表里只显示开头几位</span></div>
            </section>

            <section class="card">
                <div class="card-head"><h2>用命令行发布</h2><a class="tail link" :href.attr="$h.ak('/admin/docs')">API 文档<svg class="i i-sm"><use href="#i-arrow"></use></svg></a></div>
                <div class="card-body">
                    <p class="small sub" style="margin-bottom:10px">在 App 项目目录里运行：导出、只传有变化的文件、发布到渠道。</p>
                    <div class="code" style="margin:0">
                        <div class="code-bar"><span>终端</span><button class="code-copy" type="button" data-copy-pre>复制</button></div>
<pre><span class="cm"># 第一次：保存服务地址和令牌</span>
ota login --server <span>{{ $h.s(m.baseUrl) }}</span> --token 发布令牌

<span class="cm"># 发布到 production（--rollout 30 灰度 30%，--message 写说明）</span>
ota publish --app <span>{{ $h.s($h.p(m.app, 'slug')) }}</span>

<span class="cm"># 出问题时回滚到上一版本</span>
ota rollback --app <span>{{ $h.s($h.p(m.app, 'slug')) }}</span> --runtime 1.0.0</pre>
                    </div>
                </div>
            </section>
        </section>

        
        <section data-tab-panel="codes-api" hidden>
            <section class="card section-gap">
                <div class="card-head">
                    <h2>口令接口</h2>
                    <span class="tail muted small">App 调用 /v1/codes/redeem、verify、confirm 时用</span>
                </div>
                <div class="card-body">
                    <dl class="kv-lines">
                        <dt>App Key</dt>
                        <dd><span class="secret">{{ $h.s($h.p(m.app, 'codeKey')) }}</span>
                            <button class="icon-btn" type="button" :data-copy.attr="$h.av($h.p(m.app, 'codeKey'))" title="复制" aria-label="复制 App Key"><svg class="i i-sm"><use href="#i-copy"></use></svg></button></dd>
                        <dt>签名密钥</dt>
                        <dd v-if="$h.t(m.canSettings)"><span class="secret">{{ $h.s(m.codeSecret) }}</span>
                            <button class="icon-btn" type="button" :data-copy.attr="$h.av(m.codeSecret)" title="复制" aria-label="复制签名密钥"><svg class="i i-sm"><use href="#i-copy"></use></svg></button></dd>
                        <dd v-if="!$h.t(m.canSettings)"><span class="lock-note"><svg class="i i-sm"><use href="#i-lock"></use></svg>需要「App 设置」权限才能查看</span></dd>
                        <dt>请求头</dt>
                        <dd class="small sub">X-App-Key、X-Timestamp（毫秒）、X-Nonce、X-Signature = HMAC-SHA256(签名密钥, 时间戳 + "\n" + Nonce + "\n" + 请求体)</dd>
                    </dl>
                    <div class="note" style="margin-top:14px"><svg class="i"><use href="#i-info"></use></svg>
                        <span>签名密钥写在 App 里，换之前先下载备份，换了以后已发布的 App 兑换都会失败。算法和示例见 <a class="link-strong" :href.attr="$h.ak('/admin/docs')">API 文档</a>。</span></div>
                </div>
                <div class="card-foot">
                    <span class="hint">更换后，用旧密钥的 App 版本都不能兑换</span>
                    <form method="post" :action.attr="$h.ak('/admin/apps/codes-key/rotate')" data-confirm="更换口令接口密钥？" data-confirm-body="已发布、用旧密钥签名的 App 兑换和校验都会失败，要用新密钥重新发版。建议先下载备份。" data-confirm-ok="更换" data-confirm-danger v-if="$h.t(m.canSettings)">
                        <button class="btn btn-ghost btn-sm" type="submit">更换密钥</button>
                    </form>
                </div>
            </section>
            <section class="card">
                <div class="card-head">
                    <h2>在 App 里调用</h2>
                    <a class="tail link" :href.attr="$h.ak('/admin/docs#codes-sign')">API 文档<svg class="i i-sm"><use href="#i-arrow"></use></svg></a>
                </div>
                <div class="card-body">
                    <ul class="ep-mini">
                        <li><span class="method post">POST</span><code>/v1/codes/redeem</code><span>用户输入口令后调用，返回授权，激活记为待确认</span></li>
                        <li><span class="method post">POST</span><code>/v1/codes/verify</code><span>App 启动时用授权校验，看口令还能不能用</span></li>
                        <li><span class="method post">POST</span><code>/v1/codes/confirm</code><span>新版本启动成功后调用，计入有效激活</span></li>
                    </ul>
                    <p class="muted small" style="margin:0 0 12px">兑换成功后 App 检查更新、下载新版本。想让输入口令激活的设备拿单独的包，在「<a class="link" :href.attr="$h.ak('/admin/releases')">版本发布</a>」里设口令更新包，接口和 App 代码都不用改。</p>
                    <div class="code" style="margin:0">
                        <div class="code-bar"><span>JavaScript · 带签名调用（React Native 里可以用 crypto-js）</span><button class="code-copy" type="button" data-copy-pre>复制</button></div>
<pre>import CryptoJS from 'crypto-js';

const APP_KEY = '<span>{{ $h.s($h.p(m.app, 'codeKey')) }}</span>';
const APP_SECRET = '<span>{{ $h.s(($h.t(m.canSettings) ? m.codeSecret : '签名密钥（要「App 设置」权限才能看）')) }}</span>';

async function call(path, payload) {
  const body = JSON.stringify(payload);
  const ts = Date.now().toString();
  const nonce = Math.random().toString(16).slice(2) + Date.now().toString(16);
  const sig = CryptoJS.HmacSHA256(ts + '\n' + nonce + '\n' + body, APP_SECRET).toString(CryptoJS.enc.Hex);
  const res = await fetch('<span>{{ $h.s(m.baseUrl) }}</span>' + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-App-Key': APP_KEY,
               'X-Timestamp': ts, 'X-Nonce': nonce, 'X-Signature': sig },
    body,
  });
  return res.json();
}

<span class="cm">// 用户输入口令</span>
const r = await call('/v1/codes/redeem', { code: '88888', deviceId });</pre>
                    </div>
                </div>
            </section>
        </section>

        
        <section data-tab-panel="webhook" hidden>
            <section class="card">
                <div class="card-head">
                    <h2><svg class="i"><use href="#i-link"></use></svg>Webhook</h2>
                    <span class="tail muted small">事件发生时 POST 一个 JSON 到你的服务器，带签名</span>
                </div>
                <div class="card-body">
                    <form class="field" method="post" :action.attr="$h.ak('/admin/apps/webhook')" v-if="$h.t(m.canSettings)">
                        <label for="wh-url">通知地址</label>
                        <div class="input-group">
                            <input class="input mono" id="wh-url" name="url" type="url" :value.attr="$h.ak($h.p(m.app, 'webhookUrl'))" placeholder="https://example.com/ota-webhook，留空关闭">
                            <button class="btn btn-secondary" type="submit">保存</button>
                        </div>
                    </form>
                    <p class="small sub" style="margin-top:10px" v-if="$h.t($h.eq($h.p(m.app, 'webhookUrl'), null))">填好以后，口令兑换和确认、发布和调灰度、回滚、回退内置版本、A/B 切换、启动失败偏多都会推送，带签名，失败 5 秒、30 秒后重试</p>
                    <dl class="kv-lines" v-if="$h.t(!$h.eq($h.p(m.app, 'webhookUrl'), null))">
                        <dt>签名密钥</dt>
                        <dd v-if="$h.t(m.canSettings)"><span class="secret">{{ $h.s(m.webhookSecret) }}</span>
                            <button class="icon-btn" type="button" :data-copy.attr="$h.av(m.webhookSecret)" title="复制" aria-label="复制 Webhook 签名密钥"><svg class="i i-sm"><use href="#i-copy"></use></svg></button></dd>
                        <dd v-if="!$h.t(m.canSettings)"><span class="lock-note"><svg class="i i-sm"><use href="#i-lock"></use></svg>需要「App 设置」权限才能查看</span></dd>
                        <dt>验签</dt>
                        <dd class="small sub">X-OTA-Signature: sha256=HMAC-SHA256(签名密钥, X-OTA-Timestamp + "." + 请求体)</dd>
                        <dt>最近一次</dt>
                        <dd>{{ $h.s(($h.t(!$h.eq($h.p(m.app, 'webhookLastAt'), null)) ? $h.add($h.add($h.ui.ago($h.p(m.app, 'webhookLastAt')), ' · '), $h.p(m.app, 'webhookLastStatus')) : '还没有发送过')) }}</dd>
                    </dl>
                    <div class="code" style="margin:14px 0 0" v-if="$h.t(($h.t(!$h.eq($h.p(m.app, 'webhookUrl'), null)) &amp;&amp; $h.t(m.canSettings)))">
                        <div class="code-bar"><span>Node.js · 收到推送先验签</span><button class="code-copy" type="button" data-copy-pre>复制</button></div>
<pre>const crypto = require('crypto');
const SECRET = '<span>{{ $h.s(m.webhookSecret) }}</span>';

app.post('/ota-webhook', express.raw({ type: 'application/json' }), (req, res) => {
  const ts = req.get('X-OTA-Timestamp'), sig = req.get('X-OTA-Signature');
  const want = 'sha256=' + crypto.createHmac('sha256', SECRET).update(ts + '.' + req.body).digest('hex');
  if (sig !== want) return res.status(401).end();
  const event = JSON.parse(req.body);   <span class="cm">// event.event：redemption.confirmed、release.deployed …</span>
  res.sendStatus(200);
});</pre>
                    </div>
                </div>
                <div class="card-foot" v-if="$h.t(($h.t(!$h.eq($h.p(m.app, 'webhookUrl'), null)) &amp;&amp; $h.t(m.canSettings)))">
                    <span class="hint">口令兑换和确认、发布和回滚、A/B 切换、启动失败告警都会推送，失败 5 秒、30 秒后重试</span>
                    <form method="post" :action.attr="$h.ak('/admin/apps/webhook/rotate')" data-confirm="更换 Webhook 签名密钥？" data-confirm-body="接收方要同步改成新密钥，否则验签会失败。" data-confirm-ok="更换" data-confirm-danger>
                        <button class="btn btn-ghost btn-sm" type="submit">更换密钥</button>
                    </form>
                    <form method="post" :action.attr="$h.ak('/admin/apps/webhook/test')">
                        <button class="btn btn-secondary btn-sm" type="submit">发送测试</button>
                    </form>
                </div>
            </section>

            <section class="card" style="margin-top:20px">
                <div class="card-head">
                    <h2><svg class="i"><use href="#i-bell"></use></svg>群机器人</h2>
                    <span class="tail muted small">钉钉、飞书、企业微信群里收消息：发布、回滚、A/B 切换、启动失败告警</span>
                </div>
                <div class="card-body">
                    <form class="field" method="post" :action.attr="$h.ak('/admin/apps/notify')" v-if="$h.t(m.canSettings)">
                        <label for="bot-url">机器人地址</label>
                        <div class="input-group">
                            <input class="input mono" id="bot-url" name="url" type="url" :value.attr="$h.ak($h.p(m.app, 'notifyUrl'))" placeholder="https://oapi.dingtalk.com/robot/send?access_token=…，留空关闭">
                            <button class="btn btn-secondary" type="submit">保存</button>
                        </div>
                        <span class="hint">在群设置里加「自定义机器人」，把 Webhook 地址填进来。开了关键词的，关键词填 OTA</span>
                    </form>
                    <dl class="kv-lines" v-if="$h.t(!$h.eq($h.p(m.app, 'notifyUrl'), null))">
                        <dt>类型</dt><dd>{{ $h.s(m.notifyKind) }}</dd>
                        <dt>最近一次</dt><dd>{{ $h.s($h.elvis($h.p(m.app, 'notifyLastStatus'), '还没有发送过')) }}</dd>
                    </dl>
                    <p class="small muted" v-if="$h.t(($h.t($h.eq($h.p(m.app, 'notifyUrl'), null)) &amp;&amp; $h.t(!$h.t(m.canSettings))))">还没有配置群机器人</p>
                    <form class="notify-events" method="post" :action.attr="$h.ak('/admin/apps/notify/events')" v-if="$h.t(($h.t(!$h.eq($h.p(m.app, 'notifyUrl'), null)) &amp;&amp; $h.t(m.canSettings)))">
                        <div class="section-label">推送哪些消息</div>
                        <div class="ev-grid">
                            <template v-for="[L_e, L_eStat] in $h.each(m.notifyEvents)"><label class="check ev-opt"><input type="checkbox" name="event" :value.attr="$h.ak($h.p(L_e, 'id'))" :checked.attr="$h.t($h.p(L_e, 'on')) ? 'checked' : null"><span><b>{{ $h.s($h.p(L_e, 'name')) }}</b><small>{{ $h.s($h.p(L_e, 'desc')) }}</small></span></label></template>
                        </div>
                        <button class="btn btn-secondary btn-sm" type="submit">保存推送范围</button>
                    </form>
                </div>
                <div class="card-foot" v-if="$h.t(($h.t(!$h.eq($h.p(m.app, 'notifyUrl'), null)) &amp;&amp; $h.t(m.canSettings)))">
                    <span class="hint">消息以【OTA · <span>{{ $h.s($h.p(m.app, 'name')) }}</span>】开头</span>
                    <form method="post" :action.attr="$h.ak('/admin/apps/notify/test')">
                        <button class="btn btn-secondary btn-sm" type="submit">发送测试</button>
                    </form>
                </div>
            </section>

            <section class="card" style="margin-top:20px" v-if="$h.t(m.canSettings)">
                <div class="card-head">
                    <h2><svg class="i"><use href="#i-clock"></use></svg>发送记录</h2>
                    <span class="tail muted small">最近 30 次，Webhook 记重试完的最终结果</span>
                </div>
                <div class="empty" v-if="$h.t($h.lists.isEmpty(m.deliveries))">
                    <div class="empty-icon"><svg class="i"><use href="#i-bell"></use></svg></div>
                    <b>还没有发送过</b>配好 Webhook 或群机器人后点「发送测试」，结果会出现在这里
                </div>
                <div class="table-wrap" v-if="!$h.t($h.lists.isEmpty(m.deliveries))">
                    <table class="compact">
                        <thead><tr><th>时间</th><th>方式</th><th>事件</th><th>发到</th><th>结果</th><th>说明</th></tr></thead>
                        <tbody>
                        <template v-for="[L_d, L_dStat] in $h.each(m.deliveries)"><tr>
                            <td class="nowrap muted small" :title.attr="$h.av($h.ui.full($h.p(L_d, 'createdAt')))">{{ $h.s($h.ui.ago($h.p(L_d, 'createdAt'))) }}</td>
                            <td class="nowrap">{{ $h.s(($h.t($h.eq($h.p(L_d, 'channel'), 'WEBHOOK')) ? 'Webhook' : '群机器人')) }}</td>
                            <td class="cell-clip" :title.attr="$h.av($h.p(L_d, 'event'))"><span class="mono small">{{ $h.s($h.p(L_d, 'event')) }}</span></td>
                            <td class="mono small muted">{{ $h.s($h.elvis($h.p(L_d, 'target'), '—')) }}</td>
                            <td class="nowrap">
                                <span class="badge badge-solid" v-if="$h.t($h.p(L_d, 'ok'))"><span class="dot"></span>成功</span>
                                <span class="badge badge-danger" v-if="!$h.t($h.p(L_d, 'ok'))"><span class="dot"></span>失败</span>
                                <span class="muted small" v-if="$h.t($h.gt($h.p(L_d, 'attempts'), 1))">{{ $h.s($h.cat(' 第 ', $h.p(L_d, 'attempts'), ' 次')) }}</span>
                            </td>
                            <td class="small sub cell-clip" style="max-width:280px" :title.attr="$h.av($h.p(L_d, 'result'))">{{ $h.s($h.p(L_d, 'result')) }}</td>
                        </tr></template>
                        </tbody>
                    </table>
                </div>
            </section>
        </section>

        
        <section data-tab-panel="backup" hidden>
            <div class="lock-note" style="padding:18px" v-if="!$h.t(m.canSigning)"><svg class="i"><use href="#i-lock"></use></svg>备份里有签名私钥，需要「签名与备份」权限才能下载和恢复</div>
            <div class="grid grid-2" v-if="$h.t(m.canSigning)">
                <section class="card">
                    <div class="card-head"><h2>下载备份</h2></div>
                    <form method="post" :action.attr="$h.ak('/admin/apps/backup')" autocomplete="off" data-no-loading>
                        <div class="card-body">
                            <p class="small sub" style="margin-bottom:14px">里面有签名密钥和证书、口令接口密钥、授权密钥、Webhook、渠道、图标和全部口令。服务器丢了、密钥换错了，都能用它原样恢复。版本文件和激活记录不在里面。<template v-if="$h.t($h.access.owner())">整个后台连数据库的备份在<a class="link-strong" :href.attr="$h.ak('/admin/settings#backup')">系统配置 → 备份</a></template></p>
                            <div class="field">
                                <label for="bk-admin">你的登录密码</label>
                                <input class="input" id="bk-admin" name="adminPassword" type="password" required autocomplete="current-password">
                            </div>
                            <div class="row-2" style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px">
                                <div class="field">
                                    <label for="bk-pass">备份密码</label>
                                    <input class="input" id="bk-pass" name="backupPassword" type="password" minlength="8" required autocomplete="new-password" placeholder="至少 8 位">
                                </div>
                                <div class="field">
                                    <label for="bk-pass2">再输一次</label>
                                    <input class="input" id="bk-pass2" name="backupPassword2" type="password" minlength="8" required autocomplete="new-password">
                                </div>
                            </div>
                            <div class="note" style="margin:4px 0 0"><svg class="i"><use href="#i-info"></use></svg><span>备份用你设的密码加密，忘了密码备份就作废。文件和密码分开存。</span></div>
                        </div>
                        <div class="card-foot">
                            <span class="hint">每次改密钥、批量加口令后，建议重新下载一份</span>
                            <button class="btn btn-sm" type="submit"><svg class="i i-sm"><use href="#i-download"></use></svg>下载备份</button>
                        </div>
                    </form>
                </section>

                <section class="card">
                    <div class="card-head"><h2>用备份恢复</h2></div>
                    <form method="post" :action.attr="$h.ak('/admin/apps/restore')" enctype="multipart/form-data" autocomplete="off" id="restore-form">
                        <input type="hidden" name="from" :value.attr="'app'">
                        <div class="card-body">
                            <p class="small sub" style="margin-bottom:14px">密钥和 Webhook 改回备份时的样子，补上缺的渠道和口令，已有的口令不动。恢复别的 App 去「所有 App → 从备份恢复」。</p>
                            <label class="picker" for="rs-file" style="margin-bottom:14px">
                                <span class="drop-icon"><svg class="i"><use href="#i-upload"></use></svg></span>
                                <span><b>备份文件 .otabak</b><small data-role="name">点击选择文件</small></span>
                                <input type="file" id="rs-file" name="file" accept=".otabak,.json" hidden>
                            </label>
                            <div class="row-2" style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px">
                                <div class="field">
                                    <label for="rs-admin">你的登录密码</label>
                                    <input class="input" id="rs-admin" name="adminPassword" type="password" required autocomplete="current-password">
                                </div>
                                <div class="field">
                                    <label for="rs-pass">备份密码</label>
                                    <input class="input" id="rs-pass" name="backupPassword" type="password" required autocomplete="off">
                                </div>
                            </div>
                            <input type="hidden" name="overwrite" :value.attr="'true'">
                        </div>
                        <div class="card-foot">
                            <span class="hint">会覆盖这个 App 现在的密钥</span>
                            <button class="btn btn-secondary btn-sm" type="submit"><svg class="i i-sm"><use href="#i-refresh"></use></svg>恢复</button>
                        </div>
                    </form>
                </section>
            </div>
        </section>

        
        <section data-tab-panel="delete" hidden v-if="$h.t(m.owner)">
            <section class="card danger">
                <div class="card-head"><h2><svg class="i"><use href="#i-alert"></use></svg>删除 <span>{{ $h.s($h.p(m.app, 'name')) }}</span></h2></div>
                <form method="post" :action.attr="$h.ak('/admin/apps/delete')" autocomplete="off" id="delete-form">
                    <div class="card-body">
                        <p class="sub" style="font-size:13.5px">删除后不能撤销，下面这些会一起删除：</p>
                        <ul class="danger-list">
                            <li>版本记录 <b>{{ $h.s(m.releaseCount) }}</b> 个，以及所有渠道和发布记录；装了这个 App 的设备检查更新会拿到 404</li>
                            <li>这个 App 的激活记录 <b>{{ $h.s(m.recordCount) }}</b> 条，代理也看不到这些数据了</li>
                            <li>发布令牌 <b>{{ $h.s($h.lists.size(m.tokens)) }}</b> 个、签名密钥、口令接口密钥、Webhook、设备统计（近 30 天 <b>{{ $h.s(m.deviceCount) }}</b> 台）和计划任务</li>
                        </ul>
                        <p class="small muted" style="margin-bottom:14px">口令所有 App 通用，不会删；代理账号和操作日志也保留，用不到的文件之后在「系统配置 → 存储」清理。</p>
                        <div class="note" style="margin:0 0 16px"><svg class="i"><use href="#i-info"></use></svg>
                            <span>删除前先在「备份与恢复」下载一份，以后能原样恢复（版本要重新发布）。</span></div>
                        <div class="row-2" style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px">
                            <div class="field">
                                <label for="del-slug">输入 <span class="mono">{{ $h.s($h.p(m.app, 'slug')) }}</span> 确认</label>
                                <input class="input mono" id="del-slug" name="confirmSlug" required autocomplete="off" :data-expect.attr="$h.av($h.p(m.app, 'slug'))">
                            </div>
                            <div class="field">
                                <label for="del-admin">你的登录密码</label>
                                <input class="input" id="del-admin" name="adminPassword" type="password" required autocomplete="current-password">
                            </div>
                        </div>
                    </div>
                    <div class="card-foot">
                        <span class="hint">删除后会回到「所有 App」</span>
                        <button class="btn btn-danger btn-sm" type="submit" data-role="delete" disabled><svg class="i i-sm"><use href="#i-trash"></use></svg>永久删除这个 App</button>
                    </div>
                </form>
            </section>
        </section>

        <dialog id="token-new-dialog">
            <form method="post" :action.attr="$h.ak('/admin/apps/token')">
                <input type="hidden" name="scope" :value.attr="'publish'">
                <div class="dlg-head">
                    <h3>新建发布令牌</h3>
                    <p>{{ $h.s($h.cat('发布令牌只能操作「', $h.p(m.app, 'name'), '」：上传、发布、回滚、查询版本，不能改设置、不能碰别的 App。')) }}</p>
                </div>
                <div class="dlg-body">
                    <div class="field">
                        <label for="token-name">名称</label>
                        <input class="input" id="token-name" name="name" maxlength="100" required placeholder="例如 GitHub Actions、张三的电脑">
                        <span class="hint">写清楚用在哪里，以后看列表就知道该撤销哪个</span>
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">创建并显示令牌</button>
                </div>
            </form>
        </dialog>

    </main>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('App 设置'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
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
