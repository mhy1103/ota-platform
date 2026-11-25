<template>
<FAdminLayoutSidebar :m="m" :c="c" :P_active="'docs'"/>
<div class="main">
    <FAdminLayoutTopbar :m="m" :c="c" :P_crumb="'API 文档'"/>
    <main class="content">

        <div class="page-head">
            <div class="page-title"><div>
                <h1>API 文档</h1>
                <p>热更新、口令、管理接口和命令行</p>
            </div></div>
            <div class="page-actions">
                <button class="btn btn-secondary" type="button" data-docs-md :data-name.attr="$h.av(m.slug)" :data-title.attr="$h.av(($h.t(!$h.eq(m.app, null)) ? $h.p(m.app, 'name') : ''))"><svg class="i"><use href="#i-download"></use></svg>下载 Markdown</button>
            </div>
        </div>

        <button class="docs-jump" type="button" data-docs-jump aria-haspopup="dialog" aria-expanded="false" aria-label="跳转到章节"><svg class="i i-sm"><use href="#i-file"></use></svg><span class="dj-group" data-role="group">开始</span><span class="dj-name" data-role="name">概述</span><span class="dj-caret"><svg class="i i-sm"><use href="#i-chevron-down"></use></svg></span></button>
        <div class="docs">
            <nav class="toc" id="toc" aria-label="文档目录" data-guide="docs-toc">
                <div class="toc-group">
                    <div class="toc-title">开始</div>
                    <a href="#overview">概述</a>
                    <a href="#integrate">接入热更新</a>
                    <a href="#publish-flow">发布流程</a>
                </div>
                <div class="toc-group">
                    <div class="toc-title">热更新接口</div>
                    <a href="#manifest"><span class="verb get">GET</span>更新清单</a>
                    <a href="#launch"><span class="verb get">GET</span>启动包</a>
                    <a href="#assets"><span class="verb get">GET</span>资源文件</a>
                </div>
                <div class="toc-group">
                    <div class="toc-title">口令接口</div>
                    <a href="#codes-sign">请求签名</a>
                    <a href="#redeem"><span class="verb post">POST</span>兑换口令</a>
                    <a href="#verify"><span class="verb post">POST</span>校验授权</a>
                    <a href="#confirm"><span class="verb post">POST</span>确认激活</a>
                    <a href="#codes-errors">错误码</a>
                </div>
                <div class="toc-group">
                    <div class="toc-title">通知</div>
                    <a href="#webhook">Webhook</a>
                    <a href="#bot">群机器人</a>
                </div>
                <div class="toc-group">
                    <div class="toc-title">管理接口</div>
                    <a href="#mgmt-auth">认证</a>
                    <a href="#mgmt-apps">App 和渠道</a>
                    <a href="#mgmt-upload">上传与登记版本</a>
                    <a href="#mgmt-deploy">发布与回滚</a>
                    <a href="#mgmt-schedules">计划任务</a>
                    <a href="#mgmt-releases">版本查询与校验</a>
                    <a href="#mgmt-installers">安装包</a>
                    <a href="#mgmt-testers">测试设备与诊断</a>
                    <a href="#mgmt-codes">口令管理</a>
                    <a href="#cli">命令行工具</a>
                </div>
                <div class="toc-group">
                    <div class="toc-title">附录</div>
                    <a href="#limits">限制</a>
                </div>
            </nav>

            <div class="doc">

                
                <section class="card doc-sec" id="overview">
                    <div class="doc-head"><h2>概述</h2></div>
                    <div class="doc-body">
                        <p>服务端兼容 Expo Updates 协议（v0、v1），App 只需修改 <code>app.json</code> 即可接入，不用改代码。口令是一组独立的接口，可以单独使用，也可以和热更新配合。</p>
                        <div class="facts" style="margin-top:14px">
                            <div class="fact">
                                <span>服务地址</span>
                                <b><span>{{ $h.s(m.baseUrl) }}</span>
                                    <button class="icon-btn" type="button" :data-copy.attr="$h.av(m.baseUrl)" title="复制" aria-label="复制服务地址"><svg class="i i-sm"><use href="#i-copy"></use></svg></button></b>
                            </div>
                            <div class="fact">
                                <span>当前 App 的清单地址</span>
                                <b><span>{{ $h.s($h.add($h.add(m.baseUrl, '/m/'), m.slug)) }}</span>
                                    <button class="icon-btn" type="button" :data-copy.attr="$h.av($h.add($h.add(m.baseUrl, '/m/'), m.slug))" title="复制" aria-label="复制清单地址"><svg class="i i-sm"><use href="#i-copy"></use></svg></button></b>
                            </div>
                            <div class="fact">
                                <span>签名 keyid</span>
                                <b>{{ $h.s(m.keyId) }}</b>
                            </div>
                        </div>
                        <p class="small muted" style="margin-top:8px">服务地址取自配置项 <code>ota.public-url</code>，清单里的下载地址都以它为前缀，必须是 App 能访问到的地址。</p>

                        <h4>接口分类</h4>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>类别</th><th>路径</th><th>调用方</th><th>鉴权</th></tr></thead>
                                <tbody>
                                <tr><td class="plain nw">热更新</td><td><code>/m/{app}</code> <code>/l/{sha256}</code> <code>/a/{sha256}</code></td><td class="nw">expo-updates</td><td>无。清单由 App 的私钥签名，App 用内置证书验签</td></tr>
                                <tr><td class="plain nw">口令</td><td><code>/v1/codes/*</code></td><td class="nw">App</td><td>App Key + HMAC-SHA256 签名</td></tr>
                                <tr><td class="plain nw">管理</td><td><code>/api/v1/*</code></td><td class="nw">命令行、CI、脚本</td><td><code>Authorization: Bearer</code> 令牌</td></tr>
                                </tbody>
                            </table>
                        </div>

                        <h4>通用约定</h4>
                        <div class="ptable">
                            <table>
                                <tbody>
                                <tr><td class="plain nw"><strong>传输</strong></td><td>HTTPS。iOS 和 Android 默认拦截明文 HTTP。</td></tr>
                                <tr><td class="plain nw"><strong>编码</strong></td><td>UTF-8。请求体和响应体都是 JSON（文件上传除外），请求头 <code>Content-Type: application/json</code>。</td></tr>
                                <tr><td class="plain nw"><strong>错误</strong></td><td>管理接口和口令接口出错时返回 4xx / 5xx 和 <code>{"error": "错误码", "message": "说明"}</code>。</td></tr>
                                <tr><td class="plain nw"><strong>时间</strong></td><td>响应里的时间是 ISO 8601（UTC）；口令接口的时间戳是毫秒级 Unix 时间戳。</td></tr>
                                <tr><td class="plain nw"><strong>客户端 IP</strong></td><td>依次取 <code>X-Forwarded-For</code> 的第一个地址、<code>X-Real-IP</code>、TCP 连接地址。反向代理要传这两个请求头，限流和激活记录里的 IP 才准确。操作日志不记录 IP。</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </section>

                
                <section class="card doc-sec" id="integrate">
                    <div class="doc-head"><h2>接入热更新</h2></div>
                    <div class="doc-body">
                        <ol>
                            <li>在「所有 App」新建 App。新 App 选自动生成密钥；已经发布、内置了证书的 App 选导入现有密钥对。</li>
                            <li>下载证书放到 App 的 <code>certs/certificate.pem</code>，按下面修改 <code>app.json</code>，重新打包。</li>
                            <li>用命令行或后台发布版本。App 下次检查更新时下载。</li>
                        </ol>
                        <div class="code">
                            <div class="code-bar"><span>app.json</span><button class="code-copy" type="button">复制</button></div>
<pre>{
  "expo": {
    "runtimeVersion": { "policy": "appVersion" },
    "updates": {
      "url": "<span>{{ $h.s(m.baseUrl) }}</span>/m/<span>{{ $h.s(m.slug) }}</span>",
      "requestHeaders": { "expo-channel-name": "production" },
      "codeSigningCertificate": "./certs/certificate.pem",
      "codeSigningMetadata": { "keyid": "<span>{{ $h.s(m.keyId) }}</span>", "alg": "rsa-v1_5-sha256" }
    }
  }
}</pre>
                        </div>
                        <h4>渠道和 Runtime</h4>
                        <p>设备拿到哪个版本由三样东西决定：请求头 <code>expo-channel-name</code>（不带时是 <code>production</code>）、安装包的 Runtime 版本、设备 ID。同一渠道、同一 Runtime 只有一个线上版本；灰度期间按设备 ID 稳定分组，组外的设备继续用上一版本。还在用安装包内置版本的设备（新装的、正在输入口令激活的）组外时同样拿上一版本，没有上一版本就拿线上版本，所以灰度不会让口令激活失败。</p>
                        <p>Runtime 版本决定原生代码是否兼容。原生代码有改动时要提高 Runtime（<code>appVersion</code> 策略下就是提高 App 版本号），重新打包，已经装好的安装包不会收到新 Runtime 的更新。</p>
                        <h4>常规更新和口令更新</h4>
                        <p>每个渠道（每个 Runtime）可以发两种包：<b>常规更新</b>就是线上版本，已激活的设备检查更新时拿它，可以灰度、回滚；<b>口令更新</b>是另外设的一个包，新装 App 输入口令激活时下载它，不灰度，也不受回退内置版本影响。没单独设口令更新包时，激活时也拿常规更新包，和以前完全一样。</p>
                        <p>检查更新的地址和 App 里的代码都不用改：服务器看设备现在运行的版本，请求头 <code>expo-current-update-id</code> 等于 <code>expo-embedded-update-id</code>（或者没有这个头）就是还在用安装包内置版本，也就是新装、正在激活的。口令更新包没有这个平台的文件时，照旧拿常规更新包。后台「版本发布」页发布时选「口令更新」，或者在线上版本每一行下面的「口令更新」里点「单独设置」。</p>
                        <div class="note">App 只装比自己新的版本：口令更新包要比安装包里的版本新。重新打包安装包以后，记得换成新上传的版本，安装包检查里的「更新新旧」会提醒。</div>
                        <div class="note warn">热更新只能替换 JS 代码和资源，不能修改原生代码。按应用商店的规定，不能通过热更新改变 App 的主要用途。</div>
                    </div>
                </section>

                
                <section class="card doc-sec" id="publish-flow">
                    <div class="doc-head"><h2>发布流程</h2></div>
                    <div class="doc-body">
                        <p>命令行 <code>ota publish</code> 按下面的顺序调用管理接口，自己写脚本时照做即可。</p>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>#</th><th>请求</th><th>说明</th></tr></thead>
                                <tbody>
                                <tr><td>1</td><td class="nw">—</td><td>运行 <code>npx expo export</code>，读取 <code>metadata.json</code> 和 <code>expoConfig.json</code>，计算每个文件的 SHA-256。</td></tr>
                                <tr><td>2</td><td class="nw"><a href="#mgmt-upload"><code>POST …/assets/missing</code></a></td><td>传入所有文件的哈希，服务端返回还没有的。和之前版本相同的文件不用再传。</td></tr>
                                <tr><td>3</td><td class="nw"><a href="#mgmt-upload"><code>PUT …/assets/{sha256}</code></a></td><td>逐个上传缺少的文件，服务端核对哈希。</td></tr>
                                <tr><td>4</td><td class="nw"><a href="#mgmt-upload"><code>POST …/releases</code></a></td><td>登记版本：metadata、expoConfig 和「路径 → 哈希」。</td></tr>
                                <tr><td>5</td><td class="nw"><a href="#mgmt-deploy"><code>POST …/channels/{channel}/deploy</code></a></td><td>发布到渠道，可以指定灰度比例。发布后，后台为最近几个版本生成启动包补丁。</td></tr>
                                </tbody>
                            </table>
                        </div>
                        <p>后台的「上传更新包」和「添加外部链接」做的是同样的事。外部链接版本的文件不进服务器，App 直接从链接下载。</p>
                    </div>
                </section>

                
                <section class="card doc-sec" id="manifest">
                    <div class="doc-head"><span class="method get">GET</span><span class="ep-path">/m/{app}</span><span class="ep-meta">无需登录 · 响应带签名</span></div>
                    <div class="doc-body">
                        <h3>更新清单</h3>
                        <p>expo-updates 检查更新时请求这个地址（即 <code>updates.url</code>）。服务端按渠道、Runtime 和设备 ID 选出版本，用 App 的私钥签名后返回。下面的请求头 expo-updates 都会自动带上，App 里不用写代码；服务端用它们统计推送、安装、启动失败和启动错误。</p>
                        <h4>请求头</h4>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>请求头</th><th>必填</th><th>说明</th></tr></thead>
                                <tbody>
                                <tr><td>expo-platform</td><td class="nw">是</td><td><code>ios</code> 或 <code>android</code></td></tr>
                                <tr><td>expo-runtime-version</td><td class="nw">是</td><td>安装包的 Runtime 版本</td></tr>
                                <tr><td>expo-protocol-version</td><td class="nw">否</td><td><code>1</code> 返回签名的指令；<code>0</code> 或不带时，无更新返回 204</td></tr>
                                <tr><td>expo-channel-name</td><td class="nw">否</td><td>渠道，默认 <code>production</code></td></tr>
                                <tr><td>expo-current-update-id</td><td class="nw">否</td><td>设备正在运行的版本 ID。和线上版本相同时返回「无更新」；也用于版本分布统计</td></tr>
                                <tr><td>eas-client-id</td><td class="nw">否</td><td>设备 ID，用于灰度分组和统计。不带时不进灰度。在测试设备名单里的设备改用名单里的渠道</td></tr>
                                <tr><td>expo-embedded-update-id</td><td class="nw">否</td><td>安装包内置版本的 ID，用来判断设备是否还在用内置版本</td></tr>
                                <tr><td>expo-recent-failed-update-ids</td><td class="nw">否</td><td>设备最近启动失败、已经退回的版本，计入版本的「启动失败」，失败多时发告警</td></tr>
                                <tr><td>expo-fatal-error</td><td class="nw">否</td><td>上次启动崩溃的错误信息（最多 1024 字），记入「启动错误」。iOS 的错误信息带换行，系统发不出这个请求头；Android 只在 expo-updates 自己出错时带</td></tr>
                                <tr><td>expo-extra-params</td><td class="nw">否</td><td>App 用 expo-updates 的 setExtraParamAsync 设的参数：ota-launch-error 是崩溃的错误信息（UTF-8 后 base64url 编码），ota-launch-error-update 是崩溃的版本 ID，都记入「启动错误」</td></tr>
                                </tbody>
                            </table>
                        </div>
                        <h4>响应</h4>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>情况</th><th>响应</th></tr></thead>
                                <tbody>
                                <tr><td class="plain nw">有更新</td><td><code>multipart/mixed</code>，<code>manifest</code> 段是清单 JSON，带 <code>expo-signature: sig="…", keyid="…"</code>；另有 <code>extensions</code> 段</td></tr>
                                <tr><td class="plain nw">无更新</td><td>协议 1：签名的 <code>{"type":"noUpdateAvailable"}</code> 指令；协议 0：<code>204</code></td></tr>
                                <tr><td class="plain nw">回退内置版本</td><td>协议 1：签名的 <code>{"type":"rollBackToEmbedded","parameters":{"commitTime":"…"}}</code>；协议 0 不支持指令，返回 204</td></tr>
                                <tr><td class="plain nw">App 不存在</td><td><code>404</code></td></tr>
                                <tr><td class="plain nw">缺少平台或 Runtime</td><td><code>400</code></td></tr>
                                </tbody>
                            </table>
                        </div>
                        <h4>清单字段</h4>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>字段</th><th>说明</th></tr></thead>
                                <tbody>
                                <tr><td>id</td><td>版本 ID（UUID）</td></tr>
                                <tr><td>createdAt</td><td>版本创建时间，毫秒精度的 ISO 时间。App 只接受比当前版本新的版本，所以回滚是复制成新版本再发布</td></tr>
                                <tr><td>launchAsset / assets</td><td>每个文件的 <code>hash</code>（SHA-256 的 base64url）、<code>key</code>（MD5）、<code>contentType</code>、<code>url</code>。启动包地址是 <a class="link-strong" href="#launch"><code>/l/{sha256}</code></a>，外部链接版本的地址直接指向 CDN</td></tr>
                                <tr><td>extra.ota</td><td><code>updateId</code>（短 ID）、<code>note</code>（版本说明）、<code>forceUpdate</code>（下载后是否立即重启）</td></tr>
                                <tr><td>extra.expoClient</td><td>导出时的 <code>expoConfig.json</code></td></tr>
                                </tbody>
                            </table>
                        </div>
                        <div class="code">
                            <div class="code-bar"><span>cURL</span><button class="code-copy" type="button">复制</button></div>
<pre>curl -i <span>{{ $h.s(m.baseUrl) }}</span>/m/<span>{{ $h.s(m.slug) }}</span> \
  -H 'expo-platform: ios' -H 'expo-runtime-version: 1.0.0' \
  -H 'expo-protocol-version: 1' -H 'expo-channel-name: production'</pre>
                        </div>
                    </div>
                </section>

                
                <section class="card doc-sec" id="launch">
                    <div class="doc-head"><span class="method get">GET</span><span class="ep-path">/l/{sha256}</span><span class="ep-meta">无需登录</span></div>
                    <div class="doc-body">
                        <h3>启动包（增量更新）</h3>
                        <p>expo-updates 下载启动包时带上 <code>A-IM: bsdiff</code> 和 <code>Expo-Current-Update-ID</code>（设备正在运行的版本）。服务端有从那个版本到目标文件的补丁时，只返回补丁；App 打完补丁按哈希校验，失败会自动改下完整文件。</p>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>情况</th><th>响应</th></tr></thead>
                                <tbody>
                                <tr><td class="plain nw">有补丁</td><td><code>226 IM Used</code>，响应头 <code>IM: bsdiff</code>、<code>expo-base-update-id: 当前版本 ID</code>，响应体是 BSDIFF40 格式的补丁</td></tr>
                                <tr><td class="plain nw">没有补丁</td><td>本机存储返回 <code>200</code> 和完整文件；对象存储返回 <code>302</code>，跳转到 CDN 地址</td></tr>
                                </tbody>
                            </table>
                        </div>
                        <p>每次发布后，服务端为同一 Runtime 的最近 <span>{{ $h.s(m.patchBases) }}</span> 个版本和渠道的上一版本生成补丁。补丁超过完整文件 <span>{{ $h.s(m.patchRatio) }}</span>% 时不用。从安装包内置版本更新时，服务端没有内置的启动包，下载完整文件。增量更新可以在「App 设置」里按 App 关闭。</p>
                    </div>
                </section>

                
                <section class="card doc-sec" id="assets">
                    <div class="doc-head"><span class="method get">GET</span><span class="ep-path">/a/{sha256}</span><span class="ep-meta">无需登录</span></div>
                    <div class="doc-body">
                        <h3>资源文件</h3>
                        <p>按内容寻址：地址里是文件内容的 SHA-256（十六进制）。内容永远不变，响应带 <code>Cache-Control: public, max-age=31536000, immutable</code>，可以放在 CDN 后面。使用对象存储时，清单里的地址直接指向 CDN，这个接口只做跳转。</p>
                    </div>
                </section>

                
                <section class="card doc-sec" id="codes-sign">
                    <div class="doc-head"><h2>口令接口 · 请求签名</h2></div>
                    <div class="doc-body">
                        <p>每个 App 有一对 App Key 和签名密钥，在「App 设置 → 口令接口」里查看。每个请求带四个请求头：</p>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>请求头</th><th>说明</th></tr></thead>
                                <tbody>
                                <tr><td>X-App-Key</td><td>App Key</td></tr>
                                <tr><td>X-Timestamp</td><td>毫秒时间戳，和服务器相差不能超过 5 分钟</td></tr>
                                <tr><td>X-Nonce</td><td>每次请求随机生成，8–64 位。同一个随机数 5 分钟内只能用一次</td></tr>
                                <tr><td>X-Signature</td><td><code>hex(HMAC-SHA256(签名密钥, 时间戳 + "\n" + 随机数 + "\n" + 请求体))</code>，请求体按发送的原文计算</td></tr>
                                </tbody>
                            </table>
                        </div>
                        <h4>示例</h4>
                        <p>用下面的示例值可以核对自己的实现，结果应该与 <code>X-Signature</code> 一致。</p>
                        <div class="code">
                            <div class="code-bar"><span>示例值</span><button class="code-copy" type="button">复制</button></div>
<pre>签名密钥    <span>{{ $h.s(m.exSecret) }}</span>
X-Timestamp <span>{{ $h.s(m.exTs) }}</span>
X-Nonce     <span>{{ $h.s(m.exNonce) }}</span>
请求体      <span>{{ $h.s(m.exBody) }}</span>
X-Signature <span>{{ $h.s(m.exSig) }}</span></pre>
                        </div>
                        <div class="code">
                            <div class="code-bar"><span>JavaScript（React Native 里可以用 react-native-quick-crypto 或 crypto-js）</span><button class="code-copy" type="button">复制</button></div>
<pre>import CryptoJS from 'crypto-js';

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
}</pre>
                        </div>
                        <div class="note">签名密钥会写进 App，签名用来防止接口被随意调用，并不能代替口令本身的保密。口令的使用上限、有效期、停用和兑换开关都在服务端判断。</div>
                    </div>
                </section>

                
                <section class="card doc-sec" id="redeem">
                    <div class="doc-head"><span class="method post">POST</span><span class="ep-path">/v1/codes/redeem</span><span class="ep-meta">需要签名</span></div>
                    <div class="doc-body">
                        <h3>兑换口令</h3>
                        <p>口令有效时返回授权 <code>grant</code>。这时记一条「待确认」的激活记录，App 用授权成功启动后调用 <a class="link-strong" href="#confirm">confirm</a> 才计入有效激活。同一台设备只算一次激活：再兑换同一个口令，或者已经用别的口令激活过，都不新增记录、不占口令用量，重新签发原来的授权（existing 为 true，channel 也按原来的口令）。</p>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
                                <tbody>
                                <tr><td>code</td><td class="t">string</td><td>用户输入的口令，去掉首尾空白后匹配</td></tr>
                                <tr><td>deviceId</td><td class="t">string</td><td>设备 ID，用于去重。卸载重装后最好不变（iOS 存钥匙串）</td></tr>
                                </tbody>
                            </table>
                        </div>
                        <div class="code">
                            <div class="code-bar"><span>响应</span><button class="code-copy" type="button">复制</button></div>
<pre><span class="cm">// 成功</span>
{ "ok": true, "grant": "g1.MTI6NDU6MTc5NDM2MjQwMDAwMDo2RjFD….9b3e…", "channel": "vip", "existing": false }

<span class="cm">// 失败（HTTP 200）</span>
{ "ok": false, "error": "CODE_EXHAUSTED", "message": "口令已达到使用上限" }</pre>
                        </div>
                        <p><code>channel</code> 是口令绑定的渠道，没绑定或者这个 App 没有这个渠道时不返回。App 可以用 <code>Updates.setUpdateRequestHeadersOverride({ 'expo-channel-name': channel })</code> 切到这个渠道，之后收到这个渠道的版本。同一 IP 对同一 App 每分钟最多兑换 20 次，超出返回 <code>429</code>。</p>
                    </div>
                </section>

                
                <section class="card doc-sec" id="verify">
                    <div class="doc-head"><span class="method post">POST</span><span class="ep-path">/v1/codes/verify</span><span class="ep-meta">需要签名</span></div>
                    <div class="doc-body">
                        <h3>校验授权</h3>
                        <p>请求体 <code>{"grant": "…"}</code>，返回 <code>{"valid": true, "code": "88888"}</code> 或 <code>{"valid": false}</code>。授权签发后长期有效，停用或删除口令不影响已经签发的授权。授权用每个 App 自己的授权密钥签名，这把密钥在 App 备份里，换服务器恢复后已签发的授权照样有效。</p>
                        <p>可以带上 <code>init</code>（<code>/var/mobile</code> 的创建时间，设备初始化时间，毫秒）和 <code>model</code>（手机型号，比如 iPhone15,2）。服务端用它们算出设备指纹，补写到这条激活记录上——升级前就激活的老用户打开一次就补上，之后同一台手机去别的 App 激活时才认得出是同一台。</p>
                    </div>
                </section>

                
                <section class="card doc-sec" id="confirm">
                    <div class="doc-head"><span class="method post">POST</span><span class="ep-path">/v1/codes/confirm</span><span class="ep-meta">需要签名</span></div>
                    <div class="doc-body">
                        <h3>确认激活</h3>
                        <p>App 用授权成功启动后调用，请求体 <code>{"grant": "…"}</code>，可以带上 <code>init</code> 和 <code>model</code>（同 verify）。服务端把对应的激活记录标为「已确认」，计入口令用量和代理数据，并发送 <code>redemption.confirmed</code> 通知。重复调用没有副作用，返回 <code>{"ok": true, "confirmed": false}</code>。</p>
                        <p>开了「按设备去重」（App 设置里的开关，默认关）的 App：这台手机的设备指纹（<code>init</code> + <code>model</code>，跨 App 跨账号一样）如果别处已经算过一次有效激活，这条记成「重复」，不计入，返回 <code>{"ok": true, "confirmed": false}</code>；<code>init</code> 读不到就用公网 IP + <code>model</code> 兜底，再不行按同一个开发者账号的设备号认。关着去重的 App 照常各算各的。</p>
                    </div>
                </section>

                
                <section class="card doc-sec" id="codes-errors">
                    <div class="doc-head"><h2>口令接口 · 错误码</h2></div>
                    <div class="doc-body">
                        <div class="ptable">
                            <table>
                                <thead><tr><th>错误码</th><th>HTTP</th><th>说明</th></tr></thead>
                                <tbody>
                                <tr><td>REDEMPTION_PAUSED</td><td class="nw">200</td><td>App 处于 A 面（审核模式），暂停兑换</td></tr>
                                <tr><td>CODE_NOT_FOUND</td><td class="nw">200</td><td>口令不存在</td></tr>
                                <tr><td>CODE_DISABLED</td><td class="nw">200</td><td>口令已停用</td></tr>
                                <tr><td>CODE_EXPIRED</td><td class="nw">200</td><td>口令已过期</td></tr>
                                <tr><td>CODE_EXHAUSTED</td><td class="nw">200</td><td>已确认的激活数达到上限</td></tr>
                                <tr><td>MISSING_AUTH · BAD_APP_KEY · BAD_TIMESTAMP · STALE_TIMESTAMP · BAD_NONCE · BAD_SIGNATURE · REPLAYED</td><td class="nw">401</td><td>签名相关，见 <a class="link-strong" href="#codes-sign">请求签名</a></td></tr>
                                <tr><td>RATE_LIMITED</td><td class="nw">429</td><td>兑换太频繁</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </section>

                
                <section class="card doc-sec" id="webhook">
                    <div class="doc-head"><h2>Webhook</h2></div>
                    <div class="doc-body">
                        <p>在「App 设置 → 通知」里配置 Webhook 地址后，下面的事件发生时服务端 POST 一个 JSON 到这个地址。对方返回 2xx 算成功，否则 5 秒和 30 秒后各重试一次。</p>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>事件</th><th>时机</th></tr></thead>
                                <tbody>
                                <tr><td>redemption.created</td><td>新的兑换（待确认）。同一设备重复兑换不发</td></tr>
                                <tr><td>redemption.confirmed</td><td>激活确认，计入有效激活</td></tr>
                                <tr><td>release.deployed</td><td>发布版本或调整灰度。<code>data</code>：<code>app</code>、<code>by</code>（操作人）、<code>channel</code>、<code>release</code>（<code>id</code>、<code>runtimeVersion</code>、<code>message</code>、<code>forceUpdate</code>）、<code>rolloutPercent</code>、<code>republished</code></td></tr>
                                <tr><td>release.rolledback</td><td>回滚到上一版本，<code>release</code> 是回滚时复制出来发布的新版本</td></tr>
                                <tr><td>release.embedded</td><td>渠道回退到安装包内置版本，带 <code>channel</code>、<code>runtimeVersion</code></td></tr>
                                <tr><td>release.activation_deployed</td><td>把一个版本设成了渠道的口令更新包，带 <code>channel</code>、<code>release</code></td></tr>
                                <tr><td>release.activation_cleared</td><td>取消了渠道的口令更新包，带 <code>channel</code>、<code>runtimeVersion</code></td></tr>
                                <tr><td>release.launch_failures</td><td>版本启动失败偏多（至少 3 台、失败率 5% 以上），每个版本只发一次。<code>data</code> 带 <code>release</code>、<code>offered</code>、<code>failed</code>、<code>failRate</code></td></tr>
                                <tr><td>release.auto_rollback</td><td>开了自动回滚（版本发布页「自动回滚」）的 App，线上版本启动失败达到设定值，已经自动回滚到上一版本。<code>data</code> 带 <code>channel</code>、<code>release</code>（出问题的版本）、<code>rolledBackTo</code>（回滚后发布的版本）、<code>offered</code>、<code>failed</code>、<code>failRate</code>、<code>threshold</code></td></tr>
                                <tr><td>mode.switched</td><td>切换 A/B 面，<code>data.redemptionOpen</code> 为 <code>true</code> 即 B 面</td></tr>
                                <tr><td colspan="2" class="sub">计划任务到时间执行时，照常发 <code>release.deployed</code> 或 <code>mode.switched</code>，<code>data.by</code> 是「计划任务」</td></tr>
                                <tr><td>ping</td><td>后台点「发送测试」</td></tr>
                                </tbody>
                            </table>
                        </div>
                        <div class="code">
                            <div class="code-bar"><span>请求</span><button class="code-copy" type="button">复制</button></div>
<pre>POST {通知地址}
X-OTA-Event: redemption.confirmed
X-OTA-Timestamp: 1794362400000
X-OTA-Signature: sha256=hex(HMAC-SHA256(Webhook 签名密钥, 时间戳 + "." + 请求体))

{
  "id": "5b0c…", "event": "redemption.confirmed", "createdAt": "2026-11-11T10:00:00Z",
  "data": { "app": "<span>{{ $h.s(m.slug) }}</span>", "code": "88888", "codeId": 12, "agentId": 3, "channel": null,
            "recordId": 981, "deviceId": "6F1C…", "ip": "203.0.113.7", "confirmed": true,
            "usedCount": 41, "maxUses": 100 }
}</pre>
                        </div>
                        <p>接收方先用 <code>X-OTA-Timestamp</code> 和原始请求体算签名、和 <code>X-OTA-Signature</code> 比对，再按 <code>id</code> 去重（重试时 <code>id</code> 不变）。</p>
                    </div>
                </section>

                
                <section class="card doc-sec" id="bot">
                    <div class="doc-head"><h2>群机器人</h2></div>
                    <div class="doc-body">
                        <p>在「App 设置 → 通知」里填钉钉、飞书或企业微信群机器人的 Webhook 地址，发布、回滚、回退内置版本、切换 A/B 面和启动失败告警会以文字消息发到群里，开头是「【OTA · App 名】」。按地址自动识别类型：</p>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>地址</th><th>类型</th></tr></thead>
                                <tbody>
                                <tr><td>oapi.dingtalk.com</td><td>钉钉自定义机器人。开了关键词安全设置的，关键词填 <code>OTA</code></td></tr>
                                <tr><td>open.feishu.cn · open.larksuite.com</td><td>飞书 / Lark 自定义机器人</td></tr>
                                <tr><td>qyapi.weixin.qq.com</td><td>企业微信群机器人</td></tr>
                                <tr><td>其他地址</td><td>POST <code>{"text": "…"}</code></td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </section>

                
                <section class="card doc-sec" id="mgmt-auth">
                    <div class="doc-head"><h2>管理接口 · 认证</h2></div>
                    <div class="doc-body">
                        <p>所有 <code>/api/v1/*</code> 请求都带 <code>Authorization: Bearer 令牌</code>。发布令牌在「App 设置 → 发布令牌」里创建，管理令牌在「系统配置」里创建，明文只显示一次，服务端只存哈希。</p>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>令牌</th><th>能做什么</th></tr></thead>
                                <tbody>
                                <tr><td class="plain nw">发布令牌</td><td>只能操作所属的 App：上传文件、登记版本、发布、回滚、查询、校验、统计。给 CI 用</td></tr>
                                <tr><td class="plain nw">管理令牌</td><td>所有 App 的所有操作，包括新建 App、渠道、令牌、口令管理和兑换开关。首次启动时生成，或用 <code>OTA_BOOTSTRAP_TOKEN</code> 指定</td></tr>
                                </tbody>
                            </table>
                        </div>
                        <p>缺少或无效的令牌返回 <code>401</code>，权限不够返回 <code>403</code>。所有写操作都记入操作日志，操作人记为「令牌：名称」。</p>
                        <p>出错时返回对应的 HTTP 状态码和 JSON：<code>error</code> 是给程序判断的错误码，<code>message</code> 是给人看的说明，可以直接显示或写进 CI 日志。</p>
                        <div class="code">
                            <div class="code-bar"><span>错误响应</span><button class="code-copy" type="button">复制</button></div>
<pre>HTTP/1.1 400
{ "error": "RELEASE_IN_USE", "message": "这是 production 的线上版本，不能删除" }</pre>
                        </div>
                        <p>后台账号的权限另外管理：超级管理员在「管理账号」里给成员分配 App，按 App 勾选发布版本、切换 A/B 面、App 设置、签名与备份；口令与激活、管理代理不分 App，单独勾选。只有超级管理员能新建管理令牌；发布令牌要有对应 App 的「发布版本」权限才能新建。</p>
                    </div>
                </section>

                
                <section class="card doc-sec" id="mgmt-apps">
                    <div class="doc-head"><h2>App 和渠道</h2></div>
                    <div class="doc-body">
                        <ul class="ep-list">
                            <li><span class="method post">POST</span><code>/api/v1/apps</code><span>新建 App（管理令牌）。<code>{slug, name, downloadUrl, keyId, privateKeyPem, certificatePem}</code>，<code>name</code> 填 App 的真实名字，<code>downloadUrl</code> 是下载链接（可不传），私钥和证书不传时自动生成</span></li>
                            <li><span class="method patch">PATCH</span><code>/api/v1/apps/{app}</code><span>修改 <code>{name, downloadUrl}</code>，只改传了的字段，<code>downloadUrl</code> 传空字符串是清空（管理令牌）</span></li>
                            <li><span class="method get">GET</span><code>/api/v1/apps</code><span>App 列表</span></li>
                            <li><span class="method get">GET</span><code>/api/v1/apps/{app}</code><span>App 详情，含各渠道的线上版本</span></li>
                            <li><span class="method get">GET</span><code>/api/v1/apps/{app}/certificate</code><span>App 内置用的证书（PEM）</span></li>
                            <li><span class="method delete">DELETE</span><code>/api/v1/apps/{app}?confirm={app}</code><span>删除 App 和它的版本、渠道、激活记录、发布令牌（管理令牌），口令所有 App 通用，不删。<code>confirm</code> 要填 App 标识，防止误删</span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/channels</code><span>新建渠道 <code>{name}</code></span></li>
                            <li><span class="method delete">DELETE</span><code>/api/v1/apps/{app}/channels/{channel}</code><span>删除渠道和它的发布记录，版本保留。<code>production</code> 不能删</span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/tokens</code><span>新建发布令牌 <code>{name}</code>，返回明文</span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/redemption</code><span>兑换开关 <code>{open: true}</code> 即 B 面，<code>false</code> 即 A 面</span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/dedupe</code><span>按设备去重开关 <code>{open: true}</code> 开、<code>false</code> 关（开了同一台手机在别的 App 算过就不再算）</span></li>
                            <li><span class="method get">GET</span><code>/api/v1/apps/{app}/stats</code><span>近 30 天检查过更新的设备，按正在运行的版本和平台统计；<code>embedded</code> 表示还在用安装包内置的版本</span></li>
                            <li><span class="method get">GET</span><code>/api/v1/storage</code><span>存储用量：版本文件数、字节数、补丁数（管理令牌）</span></li>
                            <li><span class="method post">POST</span><code>/api/v1/storage/clean?minAgeHours=24</code><span>清理没有版本在用的文件和补丁，<code>minAgeHours</code> 小时内登记的不动（管理令牌）</span></li>
                            <li><span class="method get">GET</span><code>/healthz</code><span>健康检查，不需要令牌。正常返回 200 <code>{"status":"ok"}</code>，数据库连不上返回 503</span></li>
                            <li><span class="method get">GET</span><code>/api/metrics</code><span>Prometheus 监控指标（文本格式），请求头带监控令牌 <code>Authorization: Bearer otm_…</code>（「系统配置 → 运行信息」里生成，只能读指标，管理令牌不能用）。没生成令牌时返回 404</span></li>
                        </ul>
                    </div>
                </section>

                
                <section class="card doc-sec" id="mgmt-upload">
                    <div class="doc-head"><h2>上传与登记版本</h2></div>
                    <div class="doc-body">
                        <ul class="ep-list">
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/assets/missing</code><span><code>{"sha256": ["…", …]}</code> → <code>{"missing": ["…"]}</code></span></li>
                            <li><span class="method put">PUT</span><code>/api/v1/apps/{app}/assets/{sha256}</code><span>请求体是文件原始内容。哈希不一致返回 <code>HASH_MISMATCH</code></span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/releases</code><span>登记版本，返回版本信息</span></li>
                        </ul>
                        <h4>登记版本的请求体</h4>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead>
                                <tbody>
                                <tr><td>runtimeVersion</td><td class="t">string</td><td>1–64 位，字母、数字和 <code>. _ : + -</code></td></tr>
                                <tr><td>metadata</td><td class="t">object</td><td><code>metadata.json</code> 的内容，只取 ios 和 android</td></tr>
                                <tr><td>expoConfig</td><td class="t">object</td><td><code>expoConfig.json</code> 的内容，可不传</td></tr>
                                <tr><td>files</td><td class="t">object</td><td>metadata 里每个文件的「路径 → SHA-256（十六进制）」，文件都要已经上传</td></tr>
                                <tr><td>message</td><td class="t">string</td><td>版本说明，最多 500 字，会出现在清单的 <code>extra.ota.note</code></td></tr>
                                <tr><td>forceUpdate</td><td class="t">boolean</td><td>下载后是否立即重启</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </section>

                
                <section class="card doc-sec" id="mgmt-deploy">
                    <div class="doc-head"><h2>发布与回滚</h2></div>
                    <div class="doc-body">
                        <ul class="ep-list">
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/channels/{channel}/deploy</code><span><code>{releaseId, rolloutPercent, as?}</code>，灰度比例 1–100，默认 100。重新调用可以调整比例<br>带上 <code>"as": "activation"</code> 时设成口令更新包，不灰度，不看灰度比例；不带是常规更新</span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/channels/{channel}/activation/clear</code><span><code>{runtimeVersion}</code>，取消口令更新包，输入口令激活时改回拿常规更新包</span></li>
                            <li><span class="method get">GET</span><code>/api/v1/apps/{app}/history</code><span><code>?limit=30</code>，发布记录：每个渠道的发布、调整灰度、回滚、回退内置版本<br>设、取消口令更新包也会记录，action 分别是 <code>activation</code> 和 <code>activation_off</code></span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/channels/{channel}/rollback</code><span><code>{runtimeVersion, to?}</code>，回滚到上一版本；<code>to</code> 填版本 ID（或 6 位以上前缀）回滚到更早的任意版本</span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/channels/{channel}/rollback-embedded</code><span><code>{runtimeVersion}</code>，让设备回到安装包内置版本</span></li>
                        </ul>
                        <p>App 只接受比当前版本新的版本。发布一个比线上版本旧的版本、或者回滚时，服务端把它复制成新版本（新 ID、新时间、同样的文件）再发布，响应里 <code>republished</code> 为 <code>true</code>。这样已经装了新版本的设备也会换回去。</p>
                        <p>要发布的就是线上的内容（线上是它复制出来的副本，或者它是线上那个副本的原版）时不再复制，只按 <code>rolloutPercent</code> 调整灰度：<code>republished</code> 为 <code>false</code>，<code>releaseId</code> 是线上那个版本。</p>
                        <p>回退内置版本后，再发布任意版本即恢复正常下发。</p>
                        <p>设成口令更新包时，响应里的 <code>activationReleaseId</code> 是口令更新包的版本 ID，<code>"changed": false</code> 表示它本来就是口令更新包。渠道设了口令更新包时，App 详情里这个渠道的 <code>deployments</code> 也会带上 <code>activationReleaseId / activationBy / activationAt</code>，没设时和以前一样。</p>
                    </div>
                </section>

                
<section class="card doc-sec" id="mgmt-schedules">
                    <div class="doc-head"><h2>计划任务</h2></div>
                    <div class="doc-body">
                        <p>约好时间自动执行：定时调整某个渠道的灰度，或定时切换 A/B 面（比如审核通过后约好时间上线）。到时间服务端自动执行（每 30 秒检查一次），照常记操作日志、发通知；和在后台「版本发布」「概览」里设的是同一份计划。</p>
                        <ul class="ep-list">
                            <li><span class="method get">GET</span><code>/api/v1/apps/{app}/schedules</code><span><code>{pending, recent}</code>：等执行的，和最近执行过、取消的</span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/schedules</code><span>新建计划，见下表</span></li>
                            <li><span class="method delete">DELETE</span><code>/api/v1/apps/{app}/schedules/{id}</code><span>取消还没执行的计划；已经执行过的返回 <code>409 NOT_PENDING</code></span></li>
                        </ul>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>字段</th><th>说明</th></tr></thead>
                                <tbody>
                                <tr><td>type</td><td><code>rollout</code> 调整灰度（发布令牌也可以），<code>mode</code> 切换 A/B 面（要管理令牌）</td></tr>
                                <tr><td>runAt</td><td>执行时间，ISO 8601，比如 <code>2026-11-13T10:00:00+08:00</code>；不带时区时按后台时区。要在 1 分钟后、90 天内</td></tr>
                                <tr><td>channel · runtimeVersion · percent</td><td><code>rollout</code> 用：渠道、Runtime、调整到的比例（1–100）</td></tr>
                                <tr><td>face</td><td><code>mode</code> 用：<code>A</code>（审核模式）或 <code>B</code>（上线模式）</td></tr>
                                </tbody>
                            </table>
                        </div>
                        <div class="code">
                            <div class="code-bar"><span>示例</span><button class="code-copy" type="button">复制</button></div>
<pre>POST /api/v1/apps/<span>{{ $h.s(m.slug) }}</span>/schedules
{ "type": "rollout", "channel": "production", "runtimeVersion": "1.0.0", "percent": 50, "runAt": "2026-11-13T10:00:00+08:00" }

{ "id": 7, "type": "rollout", "what": "production 灰度调整到 50%", "channel": "production", "runtimeVersion": "1.0.0",
  "percent": 50, "releaseId": "3f9a2c1d-…", "runAt": "2026-11-13T10:00+08:00", "status": "pending", "result": null,
  "createdBy": "令牌 CI", "createdAt": "…", "doneAt": null }</pre>
                        </div>
                        <p>定时调整灰度会记下设计划时的线上版本（<code>releaseId</code>）。到时间如果这个渠道的线上版本已经换了，就不执行，<code>status</code> 为 <code>failed</code>，<code>result</code> 写明原因，免得把新版本直接放量。<code>status</code>：pending（等执行）、done（已执行）、failed（没执行）、cancelled（已取消）。</p>
                    </div>
                </section>

                
                <section class="card doc-sec" id="mgmt-releases">
                    <div class="doc-head"><h2>版本查询与校验</h2></div>
                    <div class="doc-body">
                        <ul class="ep-list">
                            <li><span class="method get">GET</span><code>/api/v1/apps/{app}/releases?limit=20</code><span>最近的版本，每个带 <code>stats</code></span></li>
                            <li><span class="method get">GET</span><code>/api/v1/apps/{app}/releases/{id}</code><span>版本详情，含文件列表和 <code>stats</code></span></li>
                            <li><span class="method patch">PATCH</span><code>/api/v1/apps/{app}/releases/{id}</code><span>修改 <code>{message, forceUpdate}</code>，只改传了的字段。已经装上这个版本的设备不受影响</span></li>
                            <li><span class="method get">GET</span><code>/api/v1/apps/{app}/releases/{id}/structure?base=</code><span>更新包结构：每个平台的全部文件（启动包和资源文件，外部链接版本也有）、大小和 SHA-256。带 <code>base</code>（另一个版本的 ID）时每个文件标出 <code>status</code>（added 新增、changed 有变化、same 没变、moved 换了位置），<code>kind</code> 为 <code>patch</code> 的启动包从 base 更新时走增量补丁，<code>download</code> 是从 base 更新过来要下载的大小</span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/releases/{id}/verify</code><span>校验文件、哈希、Runtime 和签名，外部链接版本会逐个下载；同 Runtime、同平台上传过安装包时一起检查安装包</span></li>
                            <li><span class="method delete">DELETE</span><code>/api/v1/apps/{app}/releases/{id}</code><span>删除版本。还有设备在拿的不能删（返回 <code>RELEASE_IN_USE</code>）：渠道的线上版本、灰度没到 100% 时其余设备拿的上一版本。只是留着回滚的上一版本可以删，之后回滚时选别的版本</span></li>
                        </ul>
                        <h4>stats 字段</h4>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>字段</th><th>说明</th></tr></thead>
                                <tbody>
                                <tr><td>offered</td><td>检查更新时服务器把这个版本发给了多少台设备（同一台设备只算一次）</td></tr>
                                <tr><td>installed</td><td>重启后已经运行这个版本的设备</td></tr>
                                <tr><td>failed</td><td>启动这个版本失败、自动退回之前版本的设备</td></tr>
                                <tr><td>patchDownloads · fullDownloads</td><td>启动包按增量补丁、完整文件下载的次数</td></tr>
                                <tr><td>bytesSaved</td><td>增量补丁比完整文件少发的字节数</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </section>

                
                <section class="card doc-sec" id="mgmt-installers">
                    <div class="doc-head"><h2>安装包</h2></div>
                    <div class="doc-body">
                        <p>可以把打好的 APK / IPA 传上来（可选）：服务端读出包名、版本、Runtime、更新地址、渠道和内置证书，检查装了这个包的设备能不能收到这个 App 的更新，校验版本时也一起检查。发布令牌只能操作所属的 App。</p>
                        <ul class="ep-list">
                            <li><span class="method get">GET</span><code>/api/v1/apps/{app}/installers</code><span>安装包列表，每个带读出来的信息、<code>level</code>（ok / warn / error）和逐项 <code>checks</code></span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/installers</code><span>一次上传（multipart：<code>file</code>、<code>note</code>），适合小文件</span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/installers/uploads</code><span>分片上传第 1 步：<code>{fileName, size}</code> → <code>{id, chunkSize}</code></span></li>
                            <li><span class="method put">PUT</span><code>/api/v1/apps/{app}/installers/uploads/{id}?offset=0</code><span>第 2 步：按顺序传每一片，请求体是文件内容，<code>offset</code> 是这一片在文件里的位置，返回 <code>{received}</code>。同一片可以重传</span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/installers/uploads/{id}/complete</code><span>第 3 步：<code>{note}</code>，传完后读包、检查，返回和列表里一样的信息</span></li>
                            <li><span class="method delete">DELETE</span><code>/api/v1/apps/{app}/installers/{id}</code><span>删除安装包，文件没有别处在用时一起删掉</span></li>
                        </ul>
                        <p>反向代理一般限制单个请求的大小（宝塔的 Nginx 默认 50MB），大的 IPA 用分片上传：每片几 MB，一个 1GB 以内的包都能传。命令行 <code>ota installer upload</code> 和后台的上传都是这样传的。分片上传一小时没动静会被清掉。</p>
                        <p>用了宝塔 WAF 时，分片里的二进制内容可能被当成攻击拦掉，返回「网站防火墙」页面。把 <code>/api/v1/apps/*/installers/uploads</code> 和 <code>/admin/installers/upload</code> 加进 WAF 的 URL 白名单。</p>
                        <h4>检查项</h4>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>label</th><th>检查什么</th></tr></thead>
                                <tbody>
                                <tr><td>更新开关</td><td>安装包里有没有关掉 expo-updates</td></tr>
                                <tr><td>更新地址</td><td>指向这个 App 的清单地址，域名是对外地址或绑定的接口域名</td></tr>
                                <tr><td>运行版本</td><td>这个 Runtime 有没有发布过更新</td></tr>
                                <tr><td>渠道</td><td>请求头里的渠道（没写按 production）存在、有线上版本</td></tr>
                                <tr><td>签名</td><td>内置证书和 App 的签名证书是同一把密钥，keyid 一致（没写 keyid 按 root 算），签名算法是 rsa-v1_5-sha256</td></tr>
                                <tr><td>更新新旧</td><td>渠道上的线上版本是不是比安装包打包时间晚；不晚的话 App 会认为没有新更新</td></tr>
                                <tr><td>检查时机</td><td>启动时什么时候检查更新；NEVER 时只能靠 App 里调用 fetchUpdateAsync</td></tr>
                                <tr><td>传输安全</td><td>更新地址是不是 HTTPS；明文 HTTP 会被系统拦掉，除非安装包允许明文流量</td></tr>
                                <tr><td>内置更新</td><td>安装包里有没有内置更新，回退内置版本要靠它</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </section>

                
                <section class="card doc-sec" id="mgmt-testers">
                    <div class="doc-head"><h2>测试设备与诊断</h2></div>
                    <div class="doc-body">
                        <ul class="ep-list">
                            <li><span class="method get">GET</span><code>/api/v1/apps/{app}/test-devices</code><span>测试设备列表</span></li>
                            <li><span class="method post">POST</span><code>/api/v1/apps/{app}/test-devices</code><span><code>{clientId, name, channel}</code>，添加或修改（同一台设备再加一次就是修改）。可以多传 <code>rolloutFirst: true</code> 放进灰度白名单（渠道灰度没到 100% 时也先拿新版本），不传就不改。管理令牌</span></li>
                            <li><span class="method delete">DELETE</span><code>/api/v1/apps/{app}/test-devices/{id}</code><span>移除测试设备，管理令牌</span></li>
                            <li><span class="method get">GET</span><code>/api/v1/apps/{app}/diagnose?device=&amp;platform=ios&amp;runtime=1.0.0&amp;channel=production</code><span>模拟检查更新：返回 <code>verdict</code>（结论）、<code>level</code>（ok / warn / error）、<code>releaseId</code> 和每一步的 <code>steps</code></span></li>
                        </ul>
                        <p>测试设备检查更新时改用名单里的渠道，不用给测试机单独打包。常见流程：新版本先发到 <code>beta</code> 渠道，测试机验证好，在后台「推到其他渠道」原样发到 <code>production</code>。设备 ID 是 expo-updates 生成的 <code>eas-client-id</code>，在「设备统计」里能看到。</p>
                    </div>
                </section>

                
                <section class="card doc-sec" id="mgmt-codes">
                    <div class="doc-head"><h2>口令管理</h2></div>
                    <div class="doc-body">
                        <ul class="ep-list">
                            <li><span class="method get">GET</span><code>/api/v1/codes?agentId=</code><span>口令列表</span></li>
                            <li><span class="method post">POST</span><code>/api/v1/codes</code><span>新建口令，见下表</span></li>
                            <li><span class="method patch">PATCH</span><code>/api/v1/codes/{id}</code><span>修改 <code>enabled</code>、<code>maxUses</code>、<code>agentId</code>、<code>channel</code>、<code>remark</code>、<code>expiresAt</code>，只改传了的字段</span></li>
                            <li><span class="method delete">DELETE</span><code>/api/v1/codes/{id}</code><span>删除口令，激活记录保留</span></li>
                            <li><span class="method get">GET</span><code>/api/v1/redemptions?app=&amp;status=&amp;code=&amp;limit=</code><span>激活记录，<code>app</code> 填 App 标识只看这个 App 的，<code>status</code> 为 all / confirmed / pending / fast（非正常：兑换后不到 2 秒就来确认，不算有效激活，<code>fastMs</code> 是隔了多少毫秒）/ dup（重复：别的 App 已经算过这台手机，<code>dupOf</code> 是算过的那条记录，<code>dupBy</code> 是 fp / ip / device）</span></li>
                            <li><span class="method get">GET</span><code>/api/v1/apps/{app}/codes-key</code><span>口令接口的 App Key 和签名密钥</span></li>
                        </ul>
                        <h4>新建口令的请求体</h4>
                        <div class="ptable">
                            <table>
                                <thead><tr><th>字段</th><th>说明</th></tr></thead>
                                <tbody>
                                <tr><td>code</td><td>指定口令内容，建一个。不传时按下面三项随机生成</td></tr>
                                <tr><td>count · prefix · length</td><td>数量（1–1000）、前缀（可不填）、随机数字位数（4–16，默认 6）</td></tr>
                                <tr><td>maxUses</td><td>最多可确认的激活数，0 不限</td></tr>
                                <tr><td>expiresAt</td><td>过期时间，ISO 格式，比如 <code>2026-12-31T23:59:59+08:00</code></td></tr>
                                <tr><td>agentId · channel · remark</td><td>归属代理、绑定渠道、备注。代理只能填一级代理，二级代理的口令由上级在代理后台分</td></tr>
                                </tbody>
                            </table>
                        </div>
                        <p>口令所有 App 通用。管理接口需要管理令牌。</p>
                    </div>
                </section>

                
<section class="card doc-sec" id="cli">
                    <div class="doc-head"><h2>命令行工具</h2></div>
                    <div class="doc-body">
                        <p>代码在仓库的 <code>cli/</code> 目录，Node.js 18 以上，没有其他依赖。服务地址和令牌也可以用环境变量 <code>OTA_SERVER</code>、<code>OTA_TOKEN</code> 传，CI 里不用登录。</p>
                        <div class="code">
                            <div class="code-bar"><span>常用命令</span><button class="code-copy" type="button">复制</button></div>
<pre><span class="cm"># 保存服务地址和令牌（写到 ~/.ota/config.json）</span>
ota login --server <span>{{ $h.s(m.baseUrl) }}</span> --token ota_xxx

<span class="cm"># 导出并发布到 production，只上传有变化的文件</span>
ota publish --app <span>{{ $h.s(m.slug) }}</span> --message "修复登录页"

<span class="cm"># 已经导出好的目录、指定渠道和灰度比例</span>
ota publish --app <span>{{ $h.s(m.slug) }}</span> --dir dist --channel staging --rollout 20

<span class="cm"># 查看版本、调整灰度、回滚、回退内置版本</span>
ota releases --app <span>{{ $h.s(m.slug) }}</span>
ota deploy --app <span>{{ $h.s(m.slug) }}</span> --release 3f9a2c1d --channel production --rollout 100
ota rollback --app <span>{{ $h.s(m.slug) }}</span> --runtime 1.0.0
ota rollback --app <span>{{ $h.s(m.slug) }}</span> --runtime 1.0.0 --to 4a55bc34
ota rollback --app <span>{{ $h.s(m.slug) }}</span> --runtime 1.0.0 --embedded

<span class="cm"># 口令更新：新装 App 输入口令激活时单独拿这个包；取消后改回拿常规更新包</span>
ota publish --app <span>{{ $h.s(m.slug) }}</span> --as activation --message "激活用的包"
ota deploy --app <span>{{ $h.s(m.slug) }}</span> --release 3f9a2c1d --as activation
ota activation clear --app <span>{{ $h.s(m.slug) }}</span> --runtime 1.0.0

<span class="cm"># 校验版本、看设备分布、下载证书</span>
ota verify --app <span>{{ $h.s(m.slug) }}</span> --release 3f9a2c1d
ota stats --app <span>{{ $h.s(m.slug) }}</span>
ota cert --app <span>{{ $h.s(m.slug) }}</span> --out certs/certificate.pem

<span class="cm"># 上传安装包（APK / IPA，自动分片），看检查结果</span>
ota installer upload --app <span>{{ $h.s(m.slug) }}</span> ./build/app.ipa --note "应用商店 1.0.0"
ota installer list --app <span>{{ $h.s(m.slug) }}</span>

<span class="cm"># 计划任务：明天 10 点把 production 灰度调到 50%；审核通过后定时切到 B 面</span>
ota schedule rollout --app <span>{{ $h.s(m.slug) }}</span> --runtime 1.0.0 --percent 50 --at "2026-11-13 10:00"
ota schedule mode --app <span>{{ $h.s(m.slug) }}</span> --face B --at "2026-11-13 20:00"
ota schedule list --app <span>{{ $h.s(m.slug) }}</span>
ota schedule cancel --app <span>{{ $h.s(m.slug) }}</span> 7</pre>
                        </div>
                        <p><code>--release</code> 可以只写版本 ID 的前 8 位。<code>publish</code> 不带 <code>--dir</code> 时在当前目录运行 <code>npx expo export</code>。Runtime 和清单里的 <code>expoClient</code> 取自导出目录的 <code>expoConfig.json</code>；新版 Expo 不再导出这个文件，这时在项目目录运行 <code>npx expo config --type public</code> 读取（<code>--project</code> 指定项目目录）。<code>appVersion</code> 策略取 App 版本号，也可以用 <code>--runtime</code> 指定。</p>
                    </div>
                </section>

                
                <section class="card doc-sec" id="limits">
                    <div class="doc-head"><h2>限制</h2></div>
                    <div class="doc-body">
                        <div class="ptable">
                            <table>
                                <thead><tr><th>项目</th><th>限制</th></tr></thead>
                                <tbody>
                                <tr><td class="plain nw">单个文件</td><td>不超过 <span>{{ $h.s(m.maxAsset) }}</span>（<code>ota.max-asset-bytes</code>）</td></tr>
                                <tr><td class="plain nw">后台上传 ZIP</td><td>不超过 <span>{{ $h.s(m.maxUpload) }}</span>；解压后不超过 600 MB、20000 个文件；不能有 <code>../</code> 或绝对路径。经反向代理上传时还受 Nginx <code>client_max_body_size</code> 限制</td></tr>
                                <tr><td class="plain nw">外部链接</td><td>metadata.json 不超过 5 MB；单个文件不超过 150 MB；合计不超过 1 GB、5000 个文件；文件需要公开可读</td></tr>
                                <tr><td class="plain nw">Runtime 版本</td><td>1–64 位，由字母、数字和 <code>. _ : + -</code> 组成</td></tr>
                                <tr><td class="plain nw">口令兑换</td><td>同一 IP 对同一 App 每分钟 20 次</td></tr>
                                <tr><td class="plain nw">签名时间窗</td><td>口令接口的时间戳与服务器相差不超过 5 分钟</td></tr>
                                <tr><td class="plain nw">批量生成口令</td><td>一次 1–1000 个；导入已有口令一次最多 1000 个、每个最多 64 位；批量启用、停用、分配、删除一次最多 1000 个</td></tr>
                                <tr><td class="plain nw">导出 CSV</td><td>激活记录、操作日志一次最多 100000 行</td></tr>
                                <tr><td class="plain nw">后台登录</td><td>默认 15 分钟内同一 IP 同一账号密码错 5 次、或同一 IP 错 20 次，暂停登录 15 分钟；次数和时长在「系统配置 → 登录安全」里改</td></tr>
                                <tr><td class="plain nw">后台会话</td><td><span>{{ $h.s(m.sessionTimeout) }}</span>无操作后失效</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </section>

            </div>
        </div>
    </main>
</div>
<FAdminLayoutOverlays :m="m" :c="c"/>
</template>

<script>
export const meta = {
    title: (m, c, $h) => $h.add(('API 文档'), ($h.t(!$h.eq(m.base, null)) ? ' · 代理后台' : $h.add(' · ', $h.elvis(m.brandName, '管理后台')))),
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
