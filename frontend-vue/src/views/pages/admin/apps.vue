<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'apps'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'所有 App'"/>
    <main class="content">

        <div class="page-head">
            <div class="page-title"><div>
                <h1>所有 App</h1>
                <p>{{ $h.s(($h.t($h.lists.isEmpty(m.cards)) ? '每个 App 有自己的签名密钥、版本和口令' : $h.cat('共 ', $h.lists.size(m.cards), ' 个 App，点「进入」切换'))) }}</p>
            </div></div>
            <div class="page-actions" v-if="$h.t(m.owner)">
                <button class="btn btn-secondary" type="button" data-open="restore-dialog"><svg class="i"><use href="#i-refresh"></use></svg>从备份恢复</button>
                <button class="btn" type="button" data-open="app-dialog"><svg class="i"><use href="#i-plus"></use></svg>新建 App</button>
            </div>
        </div>

        <section class="metrics" v-if="!$h.t($h.lists.isEmpty(m.cards))">
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-package"></use></svg></span>App</div>
                <div class="metric-value">{{ $h.s($h.lists.size(m.cards)) }}</div>
                <div class="metric-foot">{{ $h.s(($h.t(m.owner) ? '全部 App' : '分配给你的 App')) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-phone"></use></svg></span>活跃设备</div>
                <div class="metric-value">{{ $h.s($h.numbers.formatInteger(m.sumDevices, 1, 'COMMA')) }}</div>
                <div class="metric-foot">近 30 天检查过更新</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-zap"></use></svg></span>今日有效激活</div>
                <div class="metric-value">{{ $h.s($h.numbers.formatInteger(m.sumToday, 1, 'COMMA')) }}</div>
                <div class="metric-foot">{{ $h.s($h.add($h.add('近 7 天发布 ', m.sumWeek), ' 个版本')) }}</div>
            </div>
            <div class="metric">
                <div class="metric-label"><span class="metric-icon"><svg class="i"><use href="#i-alert"></use></svg></span>启动崩溃</div>
                <div :class="['metric-value', $h.av(($h.t($h.gt(m.sumErrors, 0)) ? 'danger-text' : null))]">{{ $h.s(m.sumErrors) }}</div>
                <div class="metric-foot">近 24 小时设备上报</div>
            </div>
        </section>

        <section class="card section-gap" v-if="!$h.t($h.lists.isEmpty(m.notices))">
            <div class="card-head"><h2>需要注意</h2><span class="muted small">{{ $h.s($h.add($h.lists.size(m.notices), ' 条')) }}</span></div>
            <ul class="notices" data-collapse="5">
                <template v-for="[L_n, L_nStat] in $h.each(m.notices)"><template v-if="L_nStat.index">
                </template><li>
                    <span :class="['lvl', $h.av($h.z(L_n, 'level'))]"></span>
                    <span class="text"><b>{{ $h.s($h.p($h.z(L_n, 'app'), 'name')) }}</b><span>{{ $h.s($h.z(L_n, 'text')) }}</span></span>
                    <a class="btn btn-secondary btn-sm" :href.attr="$h.ak($h.z(L_n, 'link'))">{{ $h.s($h.z(L_n, 'action')) }}</a>
                </li></template>
            </ul>
        </section>

        <section class="card" v-if="$h.t(($h.t($h.lists.isEmpty(m.cards)) &amp;&amp; $h.t(!$h.t(m.owner))))">
            <div class="empty">
                <div class="empty-icon"><svg class="i"><use href="#i-lock"></use></svg></div>
                <b>还没有分配给你的 App</b>请联系超级管理员在「管理员」里给你分配 App 和权限
            </div>
        </section>

        <section class="card" v-if="$h.t(($h.t($h.lists.isEmpty(m.cards)) &amp;&amp; $h.t(m.owner)))">
            <div class="empty">
                <div class="empty-icon"><svg class="i"><use href="#i-package"></use></svg></div>
                <b>还没有 App</b>
                新建一个 App，下载证书放进 App 的 certs 目录、按接入配置改 app.json，就能收到这里发布的更新。<br>
                换服务器时，用之前下载的备份恢复，签名密钥和口令接口密钥都和原来一样。
                <div class="btn-row" style="justify-content:center">
                    <button class="btn btn-secondary" type="button" data-open="restore-dialog"><svg class="i"><use href="#i-refresh"></use></svg>从备份恢复</button>
                    <button class="btn" type="button" data-open="app-dialog"><svg class="i"><use href="#i-plus"></use></svg>新建 App</button>
                </div>
            </div>
        </section>

        <div class="list-search bare apps-tools" v-if="!$h.t($h.lists.isEmpty(m.cards))">
            <label class="search" v-if="$h.t($h.gt($h.lists.size(m.cards), 6))"><svg class="i i-sm"><use href="#i-search"></use></svg><input class="input" type="search" placeholder="搜索 App 名称或标识" aria-label="搜索 App" data-filter="#app-cards" data-filter-item=".app-card" data-app-search></label>
            <span class="muted small">{{ $h.s($h.cat('共 ', $h.lists.size(m.cards), ' 个 App')) }}</span>
            <div class="seg seg-sm tail" role="group" aria-label="显示方式" data-app-view>
                <button type="button" data-v="cards" title="卡片"><svg class="i i-sm"><use href="#i-grid"></use></svg>卡片</button>
                <button type="button" data-v="list" title="列表"><svg class="i i-sm"><use href="#i-rows"></use></svg>列表</button>
            </div>
        </div>
        <div class="app-cards" id="app-cards" data-collapse="9" data-collapse-unit="个 App" v-if="!$h.t($h.lists.isEmpty(m.cards))">
            <template v-for="[L_c, L_cStat] in $h.each(m.cards)"><template v-if="L_cStat.index">
            </template><template v-for="L_a in [$h.p(L_c, 'app')]"><template v-for="L_cur in [($h.t(!$h.eq(m.app, null)) &amp;&amp; $h.t($h.eq($h.p(L_a, 'id'), $h.p(m.app, 'id'))))]"><article :class="['app-card', $h.av(($h.t(L_cur) ? 'current' : null))]">
                <div class="app-card-head">
                    <span :class="['app-icon', $h.av(($h.t(!$h.eq($h.p(L_a, 'iconVersion'), null)) ? 'has-icon' : null))]"><img :src.attr="$h.ak($h.cat('/admin/apps/', $h.p(L_a, 'id'), '/icon?v=', $h.p(L_a, 'iconVersion')))" alt="" v-if="!$h.eq($h.p(L_a, 'iconVersion'), null)"><template v-if="$h.eq($h.p(L_a, 'iconVersion'), null)">{{ $h.s($h.strings.substring($h.p(L_a, 'name'), 0, 1)) }}</template></span>
                    <div class="app-card-title">
                        <b>{{ $h.s($h.p(L_a, 'name')) }}</b>
                        <small>{{ $h.s($h.p(L_a, 'slug')) }}</small>
                    </div>
                    <span :class="['badge', $h.av(($h.t($h.p(L_a, 'redeemOpen')) ? 'badge-solid' : 'badge-line'))]"><span class="dot"></span><span>{{ $h.s(($h.t($h.p(L_a, 'redeemOpen')) ? 'B 面' : 'A 面')) }}</span></span>
                </div>
                <div class="app-stats">
                    <div class="app-stat" :title.attr="$h.av(($h.t(!$h.eq($h.p(L_c, 'live'), null)) ? $h.add('production · Runtime ', $h.p($h.p(L_c, 'live'), 'runtime')) : '还没有发布到 production'))">
                        <span>线上版本</span>
                        <b :class="['mono', $h.av(($h.t($h.eq($h.p(L_c, 'live'), null)) ? 'muted' : null))]">{{ $h.s(($h.t($h.eq($h.p(L_c, 'live'), null)) ? '未发布' : ($h.t($h.p($h.p(L_c, 'live'), 'rollbackToEmbedded')) ? '内置版本' : $h.ui.rid($h.p($h.p($h.p(L_c, 'live'), 'release'), 'id'))))) }}</b>
                        <small v-if="$h.t(($h.t(($h.t(!$h.eq($h.p(L_c, 'live'), null)) &amp;&amp; $h.t(!$h.t($h.p($h.p(L_c, 'live'), 'rollbackToEmbedded'))))) &amp;&amp; $h.t($h.lt($h.p($h.p(L_c, 'live'), 'rollout'), 100))))">{{ $h.s($h.add($h.add('灰度 ', $h.p($h.p(L_c, 'live'), 'rollout')), '%')) }}</small>
                    </div>
                    <div class="app-stat" title="近 30 天检查过更新的设备"><span>设备 · 30 天</span><b>{{ $h.s($h.numbers.formatInteger($h.p(L_c, 'devices'), 1, 'COMMA')) }}</b></div>
                    <div class="app-stat"><span>今天激活</span><b>{{ $h.s($h.numbers.formatInteger($h.p(L_c, 'today'), 1, 'COMMA')) }}</b></div>
                    <div class="app-stat"><span>有效激活</span><b>{{ $h.s($h.numbers.formatInteger($h.p(L_c, 'confirmed'), 1, 'COMMA')) }}</b></div>
                </div>
                <div class="app-card-meta">
                    <span class="tag" v-if="!$h.t($h.p(L_a, 'patchEnabled'))">增量更新已关闭</span>
                    <span class="tag" v-if="$h.t(!$h.eq($h.p(L_a, 'webhookUrl'), null))">Webhook</span>
                    <span>{{ $h.s($h.add($h.add($h.p(L_c, 'releases'), ' 个版本 · 创建于 '), $h.ui.dt($h.p(L_a, 'createdAt')))) }}</span>
                </div>
                <div class="app-card-foot">
                    <a class="btn btn-sm" :href.attr="$h.ak($h.link('/admin/use', [['app', $h.p(L_a, 'slug')], ['back', '/admin']]))">进入</a>
                    <a class="btn btn-secondary btn-sm" :href.attr="$h.ak($h.link('/admin/use', [['app', $h.p(L_a, 'slug')], ['back', '/admin/releases']]))">版本</a>
                    <a class="btn btn-ghost btn-sm" :href.attr="$h.ak($h.link('/admin/use', [['app', $h.p(L_a, 'slug')], ['back', '/admin/app']]))"><svg class="i i-sm"><use href="#i-sliders"></use></svg>设置</a>
                    <span class="muted small" v-if="$h.t(L_cur)">当前 App</span>
                </div>
            </article></template></template></template>
            <button class="app-new-tile" type="button" data-open="app-dialog" v-if="$h.t(m.owner)">
                <span class="ant-ic"><svg class="i"><use href="#i-plus"></use></svg></span>
                <b>新建 App</b>
                <small>自动生成签名密钥，或导入安装包内置证书对应的密钥对</small>
            </button>
        </div>
        <section class="card app-list" v-if="!$h.t($h.lists.isEmpty(m.cards))">
            <div class="alh"><span>App</span><span>线上版本</span><span class="r">今天激活</span><span class="r" title="累计有效激活">有效激活</span><span class="r" title="近 30 天检查过更新的设备">设备 · 30 天</span><span>模式</span><span></span></div>
            <div class="alr-body" id="app-rows" data-collapse="20" data-collapse-unit="个 App">
            <template v-for="[L_c, L_cStat] in $h.each(m.cards)"><template v-for="L_a in [$h.p(L_c, 'app')]"><template v-for="L_live in [$h.p(L_c, 'live')]"><template v-for="L_cur in [($h.t(!$h.eq(m.app, null)) &amp;&amp; $h.t($h.eq($h.p(L_a, 'id'), $h.p(m.app, 'id'))))]"><article :class="['alr', $h.av(($h.t(L_cur) ? 'current' : null))]" data-app-row>
                <a class="alr-title" :href.attr="$h.ak($h.link('/admin/use', [['app', $h.p(L_a, 'slug')], ['back', '/admin']]))">
                    <span :class="['app-icon', $h.av(($h.t(!$h.eq($h.p(L_a, 'iconVersion'), null)) ? 'has-icon' : null))]"><img :src.attr="$h.ak($h.cat('/admin/apps/', $h.p(L_a, 'id'), '/icon?v=', $h.p(L_a, 'iconVersion')))" alt="" v-if="!$h.eq($h.p(L_a, 'iconVersion'), null)"><template v-if="$h.eq($h.p(L_a, 'iconVersion'), null)">{{ $h.s($h.strings.substring($h.p(L_a, 'name'), 0, 1)) }}</template></span>
                    <span class="alr-tx"><b><span class="alr-nm">{{ $h.s($h.p(L_a, 'name')) }}</span><em class="mini-tag on" v-if="$h.t(L_cur)">当前 App</em></b><small>{{ $h.s($h.p(L_a, 'slug')) }}</small></span>
                </a>
                <div class="alr-data">
                    <div class="alr-ver" :title.attr="$h.av(($h.t(!$h.eq(L_live, null)) ? $h.add('production · Runtime ', $h.p(L_live, 'runtime')) : '还没有发布到 production'))"><span :class="['mono', $h.av(($h.t($h.eq(L_live, null)) ? 'muted' : null))]">{{ $h.s(($h.t($h.eq(L_live, null)) ? '未发布' : ($h.t($h.p(L_live, 'rollbackToEmbedded')) ? '内置版本' : $h.ui.rid($h.p($h.p(L_live, 'release'), 'id'))))) }}</span><em class="alr-ro" v-if="$h.t(($h.t(($h.t(!$h.eq(L_live, null)) &amp;&amp; $h.t(!$h.t($h.p(L_live, 'rollbackToEmbedded'))))) &amp;&amp; $h.t($h.lt($h.p(L_live, 'rollout'), 100))))">{{ $h.s($h.add($h.add('灰度 ', $h.p(L_live, 'rollout')), '%')) }}</em></div>
                    <div :class="['alr-n', $h.av(($h.t($h.eq($h.p(L_c, 'today'), 0)) ? 'zero' : null))]" data-k="今天">{{ $h.s($h.numbers.formatInteger($h.p(L_c, 'today'), 1, 'COMMA')) }}</div>
                    <div :class="['alr-n', $h.av(($h.t($h.eq($h.p(L_c, 'confirmed'), 0)) ? 'zero' : null))]" data-k="有效">{{ $h.s($h.numbers.formatInteger($h.p(L_c, 'confirmed'), 1, 'COMMA')) }}</div>
                    <div :class="['alr-n', $h.av(($h.t($h.eq($h.p(L_c, 'devices'), 0)) ? 'zero' : null))]" data-k="设备">{{ $h.s($h.numbers.formatInteger($h.p(L_c, 'devices'), 1, 'COMMA')) }}</div>
                </div>
                <span :class="['badge', 'alr-mode', $h.av(($h.t($h.p(L_a, 'redeemOpen')) ? 'badge-solid' : 'badge-line'))]"><span class="dot"></span><span>{{ $h.s(($h.t($h.p(L_a, 'redeemOpen')) ? 'B 面' : 'A 面')) }}</span></span>
                <div class="alr-act ac-menu">
                    <span class="app-row-more" data-pop>
                        <button class="icon-btn" type="button" data-pop-toggle aria-label="更多操作" title="更多操作"><svg class="i"><use href="#i-more"></use></svg></button>
                        <div class="menu menu-float" data-pop-menu hidden>
                            <a class="menu-item" :href.attr="$h.ak($h.link('/admin/use', [['app', $h.p(L_a, 'slug')], ['back', '/admin']]))"><svg class="i i-sm"><use href="#i-arrow"></use></svg>进入</a>
                            <a class="menu-item" :href.attr="$h.ak($h.link('/admin/use', [['app', $h.p(L_a, 'slug')], ['back', '/admin/releases']]))"><svg class="i i-sm"><use href="#i-package"></use></svg>版本</a>
                            <a class="menu-item" :href.attr="$h.ak($h.link('/admin/use', [['app', $h.p(L_a, 'slug')], ['back', '/admin/app']]))"><svg class="i i-sm"><use href="#i-sliders"></use></svg>设置</a>
                        </div>
                    </span>
                </div>
            </article></template></template></template></template>
            </div>
            <button class="app-new-row" type="button" data-open="app-dialog" v-if="$h.t(m.owner)"><svg class="i i-sm"><use href="#i-plus"></use></svg>新建 App</button>
        </section>
        <div class="list-none" id="app-cards-none" hidden>没有匹配的 App</div>

        <dialog id="restore-dialog" class="wide" data-hash="restore" v-if="$h.t(m.owner)">
            <form method="post" :action.attr="$h.ak('/admin/apps/restore')" enctype="multipart/form-data" autocomplete="off">
                <input type="hidden" name="from" :value.attr="'apps'">
                <div class="dlg-head">
                    <h3>从备份恢复 App</h3>
                    <p>App 不存在就按备份新建（换服务器时用）。已经存在的，勾选覆盖才会把密钥改回备份时的样子，缺的渠道和口令会补上。</p>
                </div>
                <div class="dlg-body">
                    <label class="picker" for="rs-file" style="margin-bottom:16px">
                        <span class="drop-icon"><svg class="i"><use href="#i-upload"></use></svg></span>
                        <span><b>备份文件 .otabak</b><small data-role="name">点击选择文件</small></span>
                        <input type="file" id="rs-file" name="file" accept=".otabak,.json" hidden>
                    </label>
                    <div class="row-2">
                        <div class="field">
                            <label for="rs-admin">你的登录密码</label>
                            <input class="input" id="rs-admin" name="adminPassword" type="password" required autocomplete="current-password">
                        </div>
                        <div class="field">
                            <label for="rs-pass">备份密码</label>
                            <input class="input" id="rs-pass" name="backupPassword" type="password" required autocomplete="off">
                            <span class="hint">下载备份时设置的密码</span>
                        </div>
                    </div>
                    <label class="check" style="margin-top:4px"><input type="checkbox" name="overwrite" :value.attr="'true'">App 已存在时，用备份里的密钥覆盖</label>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">恢复</button>
                </div>
            </form>
        </dialog>

        <dialog id="app-dialog" class="wide" data-hash="new" v-if="$h.t(m.owner)">
            <form method="post" :action.attr="$h.ak('/admin/apps/create')" enctype="multipart/form-data" id="app-form">
                <div class="dlg-head">
                    <h3>新建 App</h3>
                    <p>新 App 自动生成签名密钥。已经上架、内置了证书的 App，导入当时那对密钥。</p>
                </div>
                <div class="dlg-body">
                    <div class="row-2">
                        <div class="field">
                            <label for="app-slug">标识</label>
                            <input class="input mono" id="app-slug" name="slug" required maxlength="40" pattern="[a-z0-9][a-z0-9\-]{1,38}[a-z0-9]" placeholder="例如 my-app">
                            <span class="hint">小写字母、数字和 -，3–40 位，清单地址是 /m/标识，建好后不能改</span>
                        </div>
                        <div class="field">
                            <label for="app-name">App 名称</label>
                            <input class="input" id="app-name" name="name" maxlength="100" placeholder="App 的真实名字，例如 尚礼记" required>
                            <span class="hint">用户手机上看到的名字，后台和代理后台都显示它</span>
                        </div>
                    </div>
                    <div class="field">
                        <label for="app-download">下载链接</label>
                        <input class="input" id="app-download" name="downloadUrl" type="url" maxlength="500" placeholder="可不填：App Store、应用商店或官网下载页，例如 https://apps.apple.com/app/id…">
                        <span class="hint">以后可以在 App 设置里修改和复制</span>
                    </div>
                    <div class="field">
                        <label>签名密钥</label>
                        <div class="opt-grid three">
                            <label class="opt"><input type="radio" name="keyMode" :value.attr="'generate'" :checked.attr="''">
                                <span><b>自动生成</b><small>新 App 用，建好后下载证书放进 App</small></span></label>
                            <label class="opt"><input type="radio" name="keyMode" :value.attr="'import'">
                                <span><b>导入密钥对</b><small>App 已经内置了证书时用</small></span></label>
                            <label class="opt"><input type="radio" name="keyMode" :value.attr="'reuse'" :disabled.attr="$h.t($h.lists.isEmpty(m.reuseApps)) ? 'disabled' : null">
                                <span><b>复用其他 App 的</b><small>同一套代码打出多个 App、内置同一张证书时用</small></span></label>
                        </div>
                    </div>
                    <div class="field" data-role="reuse" hidden>
                        <label for="app-reuse">复用哪个 App 的签名密钥</label>
                        <select class="select" id="app-reuse" name="reuseFrom" style="height:38px">
                            <template v-for="[L_a, L_aStat] in $h.each(m.reuseApps)"><template v-if="L_aStat.index">
                            </template><option :value.attr="$h.ak($h.p(L_a, 'slug'))">{{ $h.s($h.add($h.add($h.add($h.add($h.add($h.p(L_a, 'name'), '（'), $h.p(L_a, 'slug')), '，keyid '), $h.p(L_a, 'keyId')), '）')) }}</option></template>
                        </select>
                        <span class="hint">复制一份私钥和证书，之后两个 App 各自独立；keyid 也沿用</span>
                    </div>
                    <div data-role="import" hidden>
                        <label class="picker" for="app-key">
                            <span class="drop-icon"><svg class="i"><use href="#i-key"></use></svg></span>
                            <span><b>私钥 private-key.pem</b><small data-role="name">点击选择文件</small></span>
                            <input type="file" id="app-key" name="privateKey" accept=".pem,.key,.txt" hidden>
                        </label>
                        <label class="picker" for="app-cert">
                            <span class="drop-icon"><svg class="i"><use href="#i-shield"></use></svg></span>
                            <span><b>证书 certificate.pem</b><small data-role="name">点击选择文件</small></span>
                            <input type="file" id="app-cert" name="certificate" accept=".pem,.crt,.cer,.txt" hidden>
                        </label>
                        <div class="field" style="margin-top:12px">
                            <label for="app-keyid">keyid</label>
                            <input class="input mono" id="app-keyid" name="keyId" :value.attr="'main'" maxlength="40">
                            <span class="hint">和 App 里 codeSigningMetadata.keyid 一致</span>
                        </div>
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit">创建</button>
                </div>
            </form>
        </dialog>

    </main>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('所有 App'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
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
