<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'signing'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'签名配置'"/>
    <main class="content">

        <div class="page-head">
            <div class="page-title"><div>
                <h1>签名配置</h1>
                <p>{{ $h.s($h.cat($h.p(m.app, 'name'), ' · 更新清单用私钥签名，App 用内置证书验证')) }}</p>
            </div></div>
            <div class="page-actions" v-if="$h.t(m.canSigning)">
                <button class="btn" type="button" data-open="signing-dialog"><svg class="i"><use href="#i-upload"></use></svg>上传签名文件</button>
            </div>
        </div>

        <section class="card section-gap sign-status" data-guide="sign-status">
            <div :class="['hero', $h.av(($h.t(!$h.eq($h.p(m.s, 'level'), 'ok')) ? $h.p(m.s, 'level') : null))]">
                <div class="hero-icon"><svg class="i"><use :href.attr="$h.ak(($h.t($h.eq($h.p(m.s, 'level'), 'ok')) ? '#i-tick' : '#i-alert'))"></use></svg></div>
                <div>
                    <h2>{{ $h.s($h.p(m.s, 'headline')) }}</h2>
                    <p>{{ $h.s($h.p(m.s, 'detail')) }}</p>
                    <p class="small" style="margin-top:6px" v-if="!$h.t($h.lists.isEmpty(m.sharedWith))">
                        和 <b>{{ $h.s($h.strings.listJoin(m.sharedWith, '、')) }}</b> 用的是同一对密钥（证书公钥指纹相同），这些 App 可以内置同一张证书
                    </p>
                </div>
            </div>
            <ul class="sign-checks">
                <template v-for="[L_c, L_cStat] in $h.each($h.p(m.s, 'checks'))"><template v-if="L_cStat.index">
                </template><li :class="$h.av($h.p(L_c, 'state'))">
                    <span :class="['st', $h.av($h.add('st-', $h.p(L_c, 'state')))]">
                        <svg class="i" v-if="$h.t($h.eq($h.p(L_c, 'state'), 'ok'))"><use href="#i-tick"></use></svg>
                        <svg class="i" v-if="$h.t($h.eq($h.p(L_c, 'state'), 'error'))"><use href="#i-x"></use></svg>
                        <template v-if="$h.t($h.eq($h.p(L_c, 'state'), 'warn'))">!</template>
                    </span>
                    <span class="sc-text"><b>{{ $h.s($h.p(L_c, 'label')) }}</b><small :title.attr="$h.av($h.p(L_c, 'text'))">{{ $h.s($h.p(L_c, 'text')) }}</small></span>
                </li></template>
            </ul>
        </section>

        <section class="card section-gap" data-guide="sign-use">
            <div class="card-head">
                <h2>在 App 里使用</h2>
                <span class="muted small">App 用内置的证书验证每个更新，keyid 和算法要和 app.json 一致</span>
            </div>
            <div class="sign-use">
                <div class="sign-use-side">
                    <ol class="steps" style="margin-top:0">
                        <li>下载证书，放到 App 项目的 <code>certs/certificate.pem</code></li>
                        <li>按右边改 <code>app.json</code> 的 <code>updates</code>，重新打包安装</li>
                        <li>之后发布的更新都会用这里的私钥签名</li>
                    </ol>
                    <dl class="kv sign-kv">
                        <dt>keyid</dt><dd><span class="mono strong">{{ $h.s($h.p(m.s, 'keyId')) }}</span><button class="icon-btn" type="button" :data-copy.attr="$h.av($h.p(m.s, 'keyId'))" title="复制 keyid" aria-label="复制 keyid"><svg class="i i-sm"><use href="#i-copy"></use></svg></button></dd>
                        <dt>算法</dt><dd><span class="mono">{{ $h.s($h.p(m.s, 'algorithm')) }}</span><button class="icon-btn" type="button" :data-copy.attr="$h.av($h.p(m.s, 'algorithm'))" title="复制算法" aria-label="复制算法"><svg class="i i-sm"><use href="#i-copy"></use></svg></button></dd>
                    </dl>
                    <div class="btn-row" v-if="$h.t($h.p(m.s, 'certOk'))">
                        <a class="btn" :href.attr="$h.ak('/admin/signing/certificate.pem')"><svg class="i"><use href="#i-download"></use></svg>下载证书</a>
                        <a class="btn btn-secondary" :href.attr="$h.ak('/admin/signing/public-key.pem')" title="服务端或其他工具验签时用"><svg class="i"><use href="#i-download"></use></svg>下载公钥</a>
                    </div>
                </div>
                <div class="code sign-code">
                    <div class="code-bar"><span>app.json · expo.updates</span><button class="code-copy" type="button" data-copy-pre>复制</button></div>
<pre>"updates": {
  "url": "<span>{{ $h.s(m.manifestUrl) }}</span>",
  "codeSigningCertificate": "./certs/certificate.pem",
  "codeSigningMetadata": {
    "keyid": "<span>{{ $h.s($h.p(m.s, 'keyId')) }}</span>",
    "alg": "<span>{{ $h.s($h.p(m.s, 'algorithm')) }}</span>"
  }
}</pre>
                </div>
            </div>
            <div class="note sign-note">
                <svg class="i"><use href="#i-info"></use></svg>
                <span>证书编在 App 里，整套换密钥后已经安装的 App 会收不到更新，要用新证书重新打包。私钥用主密钥加密存在数据库里，记得备份数据目录里的 master.key。</span>
            </div>
        </section>

        <div class="grid grid-2">
            <section class="card">
                <div class="card-head">
                    <h2>私钥</h2>
                    <span class="badge badge-solid" v-if="$h.t($h.p(m.s, 'keyLoaded'))"><span class="dot"></span>已加密保存</span>
                    <span class="badge badge-line" v-if="!$h.t($h.p(m.s, 'keyLoaded'))">无法解开</span>
                </div>
                <div class="card-body">
                    <dl class="kv">
                        <dt>类型</dt><dd>{{ $h.s(($h.t($h.p(m.s, 'keyLoaded')) ? $h.add($h.add('RSA ', $h.p(m.s, 'keyBits')), ' 位') : '—')) }}</dd>
                        <dt>公钥指纹</dt><dd class="fp-row"><span class="fp">{{ $h.s($h.elvis($h.p(m.s, 'keyFingerprint'), '—')) }}</span><button class="icon-btn" type="button" :data-copy.attr="$h.av($h.p(m.s, 'keyFingerprint'))" title="复制指纹" aria-label="复制私钥的公钥指纹" v-if="$h.t(!$h.eq($h.p(m.s, 'keyFingerprint'), null))"><svg class="i i-sm"><use href="#i-copy"></use></svg></button></dd>
                        <dt>更新时间</dt><dd>{{ $h.s($h.p(m.s, 'keyUpdatedAt')) }}</dd>
                        <template v-if="$h.t(!$h.eq($h.p(m.s, 'keyError'), null))"><dt>错误</dt><dd>{{ $h.s($h.p(m.s, 'keyError')) }}</dd></template>
                    </dl>
                </div>
                <div class="card-foot">
                    <span class="hint"><svg class="i i-sm"><use href="#i-lock"></use></svg>私钥只能上传，不能从后台下载</span>
                    <button class="btn btn-secondary btn-sm" type="button" data-open="signing-dialog" data-pick="sig-key" v-if="$h.t(m.canSigning)"><svg class="i i-sm"><use href="#i-upload"></use></svg>换私钥</button>
                </div>
            </section>

            <section class="card">
                <div class="card-head">
                    <h2>证书</h2>
                    <span class="badge badge-solid" v-if="$h.t(($h.t($h.p(m.s, 'certOk')) &amp;&amp; $h.t($h.eq($h.p(m.s, 'matched'), true))))"><span class="dot"></span>与私钥配对</span>
                    <span class="badge badge-line" v-if="$h.t(($h.t($h.p(m.s, 'certOk')) &amp;&amp; $h.t($h.eq($h.p(m.s, 'matched'), false))))">与私钥不配对</span>
                    <span class="badge badge-line" v-if="!$h.t($h.p(m.s, 'certOk'))">无法解析</span>
                </div>
                <div class="card-body" v-if="$h.t($h.p(m.s, 'certOk'))">
                    <dl class="kv">
                        <dt>名称</dt><dd class="strong">{{ $h.s($h.p(m.s, 'commonName')) }}</dd>
                        <dt>颁发者</dt><dd>{{ $h.s(($h.t($h.p(m.s, 'selfSigned')) ? '自签名' : $h.p(m.s, 'issuer'))) }}</dd>
                        <dt>有效期</dt><dd>{{ $h.s($h.cat($h.p(m.s, 'notBefore'), ' 至 ', $h.p(m.s, 'notAfter'))) }}</dd>
                        <dt>用途</dt>
                        <dd>
                            <span :class="['badge', $h.av(($h.t($h.p(m.s, 'digitalSignature')) ? 'badge-solid' : 'badge-line'))]">{{ $h.s(($h.t($h.p(m.s, 'digitalSignature')) ? '数字签名' : '缺少数字签名')) }}</span>
                            <span :class="['badge', $h.av(($h.t($h.p(m.s, 'codeSigning')) ? 'badge-solid' : 'badge-line'))]">{{ $h.s(($h.t($h.p(m.s, 'codeSigning')) ? '代码签名' : '缺少代码签名')) }}</span>
                        </dd>
                        <dt>公钥指纹</dt><dd class="fp-row"><span class="fp">{{ $h.s($h.p(m.s, 'certKeyFingerprint')) }}</span><button class="icon-btn" type="button" :data-copy.attr="$h.av($h.p(m.s, 'certKeyFingerprint'))" title="复制指纹" aria-label="复制证书的公钥指纹"><svg class="i i-sm"><use href="#i-copy"></use></svg></button></dd>
                    </dl>
                </div>
                <div class="empty" style="padding:32px 20px" v-if="!$h.t($h.p(m.s, 'certOk'))">
                    <div class="empty-icon"><svg class="i"><use href="#i-shield"></use></svg></div>
                    <b>证书无法解析</b>
                    <span>{{ $h.s($h.p(m.s, 'certError')) }}</span>
                </div>
                <div class="card-foot">
                    <a class="btn btn-ghost btn-sm" :href.attr="$h.ak('/admin/signing/certificate.pem')" v-if="$h.t($h.p(m.s, 'certOk'))"><svg class="i i-sm"><use href="#i-download"></use></svg>下载证书</a>
                    <button class="btn btn-secondary btn-sm" type="button" data-open="signing-dialog" data-pick="sig-cert" style="margin-left:auto" v-if="$h.t(m.canSigning)"><svg class="i i-sm"><use href="#i-upload"></use></svg>换证书</button>
                </div>
            </section>
        </div>

        <section class="card section-gap" v-if="$h.t(($h.t(m.canSigning) &amp;&amp; $h.t(!$h.t($h.lists.isEmpty(m.sources)))))">
            <div class="card-head">
                <h2>复用其他 App 的密钥</h2>
                <span class="tail muted small">多个 App 内置同一张证书时用，复制过来后各自独立</span>
            </div>
            <div class="table-wrap">
                <table class="compact">
                    <thead><tr><th>App</th><th>keyid</th><th>和当前 App</th><th class="right">操作</th></tr></thead>
                    <tbody data-collapse="6" data-collapse-unit="个 App">
                    <template v-for="[L_src, L_srcStat] in $h.each(m.sources)"><template v-if="L_srcStat.index">
                    </template><tr>
                        <td class="two-line"><b>{{ $h.s($h.p($h.z(L_src, 'app'), 'name')) }}</b><small class="mono">{{ $h.s($h.p($h.z(L_src, 'app'), 'slug')) }}</small></td>
                        <td class="mono">{{ $h.s($h.p($h.z(L_src, 'app'), 'keyId')) }}</td>
                        <td>
                            <span class="badge badge-solid" v-if="$h.t($h.z(L_src, 'same'))"><span class="dot"></span>同一对密钥</span>
                            <span class="muted" v-if="!$h.t($h.z(L_src, 'same'))">不同</span>
                        </td>
                        <td class="actions">
                            <form method="post" :action.attr="$h.ak('/admin/signing/reuse')" :data-confirm.attr="$h.av($h.cat('改用 ', $h.p($h.z(L_src, 'app'), 'name'), ' 的签名密钥？'))" :data-confirm-body.attr="$h.av($h.cat($h.p(m.app, 'name'), ' 的私钥、证书和 keyid 会换成 ', $h.p($h.z(L_src, 'app'), 'name'), ' 的。已安装、内置旧证书的 ', $h.p(m.app, 'name'), ' 收不到之后的更新，要用新证书重新打包。'))" data-confirm-ok="改用" data-confirm-danger v-if="!$h.t($h.z(L_src, 'same'))">
                                <input type="hidden" name="source" :value.attr="$h.ak($h.p($h.z(L_src, 'app'), 'slug'))">
                                <button class="btn btn-ghost btn-sm" type="submit">改用这对密钥</button>
                            </form>
                        </td>
                    </tr></template>
                    </tbody>
                </table>
            </div>
        </section>

        <dialog id="signing-dialog" v-if="$h.t(m.canSigning)">
            <form method="post" :action.attr="$h.ak('/admin/signing/upload')" enctype="multipart/form-data" id="signing-form">
                <div class="dlg-head">
                    <h3>上传签名文件</h3>
                    <p>可以只传一个，保存前会检查私钥和证书是否配对。</p>
                </div>
                <div class="dlg-body">
                    <label class="picker" for="sig-key">
                        <span class="drop-icon"><svg class="i"><use href="#i-key"></use></svg></span>
                        <span><b>私钥 private-key.pem</b><small data-role="name">点击选择文件，不换可不选</small></span>
                        <input type="file" id="sig-key" name="privateKey" accept=".pem,.key,.txt" hidden>
                    </label>
                    <label class="picker" for="sig-cert">
                        <span class="drop-icon"><svg class="i"><use href="#i-shield"></use></svg></span>
                        <span><b>证书 certificate.pem</b><small data-role="name">点击选择文件，不换可不选</small></span>
                        <input type="file" id="sig-cert" name="certificate" accept=".pem,.crt,.cer,.txt" hidden>
                    </label>
                    <div class="note" style="margin-top:14px">
                        <svg class="i"><use href="#i-info"></use></svg>
                        <span>只传一个时，要和现在的另一个配对。整套更换就两个一起传。</span>
                    </div>
                </div>
                <div class="dlg-foot">
                    <button class="btn btn-secondary" type="button" data-close>取消</button>
                    <button class="btn" type="submit" data-role="submit" disabled>校验并保存</button>
                </div>
            </form>
        </dialog>

    </main>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('签名配置'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
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
