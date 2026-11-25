<template>
<div class="ins" id="ins" :data-config.attr="$h.av(m.cfgJson)" :data-reinstall.attr="$h.av(m.reinstall)" :data-host.attr="$h.av(m.host)" :data-https.attr="$h.av(m.https)">
    <aside class="ins-rail">
        <div class="ins-brand">
            <svg class="ins-logo"><use href="#logo"></use></svg>
            <span>管理后台<small>{{ $h.s(($h.t(m.reinstall) ? '重新安装' : '安装引导')) }}</small></span>
        </div>
        <ol class="ins-steps">
            <li data-step="0"><i><svg><use href="#tick"></use></svg><em>1</em></i><span><b>欢迎</b><small>检查运行环境</small></span></li>
            <li data-step="1"><i><svg><use href="#tick"></use></svg><em>2</em></i><span><b>数据库</b><small>MySQL 或内置数据库</small></span></li>
            <li data-step="2"><i><svg><use href="#tick"></use></svg><em>3</em></i><span><b>访问域名</b><small>后台和接口的域名</small></span></li>
            <li data-step="3"><i><svg><use href="#tick"></use></svg><em>4</em></i><span><b>存储与高级</b><small>文件存储、时区</small></span></li>
            <li data-step="4"><i><svg><use href="#tick"></use></svg><em>5</em></i><span><b>管理员</b><small>超级管理员账号</small></span></li>
            <li data-step="5"><i><svg><use href="#tick"></use></svg><em>6</em></i><span><b>确认安装</b><small>检查一遍再开始</small></span></li>
        </ol>
        <div class="ins-rail-foot">
            <span>版本 <b>{{ $h.s(m.version) }}</b></span>
            <span>不用改配置文件，也不用加启动参数</span>
        </div>
    </aside>

    <main class="ins-main">
        <div class="ins-mobile-bar"><span data-mobile-step>1 / 6 · 欢迎</span><i><b></b></i></div>
        <form class="ins-form" autocomplete="off" novalidate>

            <section class="ins-panel" data-panel="0">
                <header class="ins-head">
                    <span class="ins-kicker">{{ $h.s(($h.t(m.reinstall) ? '重新安装' : '开始')) }}</span>
                    <h1>{{ $h.s(($h.t(m.reinstall) ? '修改安装配置' : '欢迎使用 OTA 管理后台')) }}</h1>
                    <p v-if="$h.t(!$h.t(m.reinstall))">几步设好数据库、域名和管理员账号。装好以后，服务器上只要 <code>java -jar ota-platform.jar</code> 就能启动。</p>
                    <p v-if="$h.t(m.reinstall)">可以换数据库、改域名和存储、重设超级管理员，数据可以保留。保存后服务会自动重启。</p>
                </header>
                <div class="ins-feats">
                    <div><svg><use href="#db"></use></svg><b>MySQL 数据库</b><span>正式使用接 MySQL，试用可以先用内置数据库</span></div>
                    <div><svg><use href="#globe"></use></svg><b>多域名</b><span>后台和接口可以绑不同的域名</span></div>
                    <div><svg><use href="#shield"></use></svg><b>密码加密保存</b><span>数据库密码、存储密钥用主密钥加密</span></div>
                </div>
                <div class="ins-card">
                    <div class="ins-card-head"><b>运行环境</b><span class="ins-pill" data-env-status>检查中</span></div>
                    <ul class="ins-checks" data-env>
                        <li class="loading"><i></i><span>正在检查……</span></li>
                    </ul>
                </div>
            </section>

            <section class="ins-panel" data-panel="1" hidden>
                <header class="ins-head">
                    <span class="ins-kicker">第 2 步</span>
                    <h1>数据库</h1>
                    <p>App、版本、口令和激活记录都存在这里。正式使用选 MySQL，试用可以先用内置数据库。</p>
                </header>
                <div class="ins-options">
                    <label class="ins-option">
                        <input type="radio" name="dbType" :value.attr="'mysql'" :checked.attr="''">
                        <span class="ins-option-body">
                            <span class="ins-option-icon"><svg><use href="#db"></use></svg></span>
                            <span><b>MySQL <em>推荐</em></b><small>MySQL 5.7 / 8.0。数据单独存放，方便备份、迁移和做主从。先建好空库（utf8mb4）</small></span>
                        </span>
                    </label>
                    <label class="ins-option">
                        <input type="radio" name="dbType" :value.attr="'h2'">
                        <span class="ins-option-body">
                            <span class="ins-option-icon"><svg><use href="#box"></use></svg></span>
                            <span><b>内置数据库</b><small>不用装数据库，适合试用和演示</small></span>
                        </span>
                    </label>
                </div>
                <div class="ins-grid" data-when="mysql">
                    <label class="ins-field span-2"><span>数据库地址</span><input name="dbHost" placeholder="127.0.0.1 或 rm-xxx.mysql.rds.aliyuncs.com" spellcheck="false"></label>
                    <label class="ins-field"><span>端口</span><input name="dbPort" inputmode="numeric" placeholder="3306" :value.attr="'3306'"></label>
                    <label class="ins-field"><span>数据库名</span><input name="dbName" placeholder="ota" spellcheck="false"></label>
                    <label class="ins-field"><span>账号</span><input name="dbUser" placeholder="ota" spellcheck="false" autocomplete="off"></label>
                    <label class="ins-field"><span>密码</span><input name="dbPass" type="password" autocomplete="new-password" data-keep-hint="已保存，不改就留空"></label>
                </div>
                <div class="ins-test">
                    <button class="ins-btn subtle" type="button" data-test-db><svg><use href="#plug"></use></svg>测试连接</button>
                    <div class="ins-result" data-db-result hidden></div>
                </div>
                <div class="ins-existing" data-existing hidden>
                    <div class="ins-note warn"><svg><use href="#alert"></use></svg><span>这个数据库里已经有数据：<b data-db-summary></b>。选一下怎么处理：</span></div>
                    <div class="ins-options two">
                        <label class="ins-option">
                            <input type="radio" name="dataMode" :value.attr="'keep'" :checked.attr="''">
                            <span class="ins-option-body"><span><b>保留数据</b><small>升级、换服务器、换域名时选这个。App、版本、口令、管理员都保留</small></span></span>
                        </label>
                        <label class="ins-option danger">
                            <input type="radio" name="dataMode" :value.attr="'clear'">
                            <span class="ins-option-body"><span><b>清空后全新安装</b><small>删除这个库里的全部数据，不能恢复</small></span></span>
                        </label>
                    </div>
                    <label class="ins-field" data-when="clear" hidden><span>确认清空：在下面输入「清空」两个字</span><input name="clearConfirm" placeholder="清空" autocomplete="off"></label>
                </div>
            </section>

            <section class="ins-panel" data-panel="2" hidden>
                <header class="ins-head">
                    <span class="ins-kicker">第 3 步</span>
                    <h1>访问域名</h1>
                    <p>后台和接口可以用一个域名，也可以分开，比如 admin.example.com 开后台，api.example.com 给 App 用。</p>
                </header>
                <label class="ins-switch">
                    <input type="checkbox" name="restrictDomains">
                    <span class="ins-switch-track"><span></span></span>
                    <span><b>只允许下面这些域名访问</b><small>开启后用错域名访问是 404，不开启时任何域名和 IP 都能访问</small></span>
                </label>
                <div class="ins-domains-head"><span>域名</span><span>用于</span><span></span></div>
                <div class="ins-domains" data-domains></div>
                <button class="ins-btn subtle add" type="button" data-add-domain><svg><use href="#plus"></use></svg>添加域名</button>
                <label class="ins-field"><span>接口对外地址</span><input name="publicUrl" placeholder="https://api.example.com" spellcheck="false"><small>App 下载更新包用的地址，默认是第一个接口域名（带端口），走 CDN 再改</small></label>
                <div class="ins-examples">
                    <div><b>一个域名</b><span>ota.example.com</span><small>后台 + 接口</small></div>
                    <div><b>后台、接口分开</b><span>admin.example.com</span><small>后台</small><span>api.example.com</span><small>接口</small></div>
                    <div><b>只有 IP</b><span>不开启限制</span><small>接口地址填 http://IP:端口</small></div>
                </div>
            </section>

            <section class="ins-panel" data-panel="3" hidden>
                <header class="ins-head">
                    <span class="ins-kicker">第 4 步</span>
                    <h1>文件存储</h1>
                    <p>更新包的文件存哪。本机最简单，用户多要走 CDN 就放对象存储。</p>
                </header>
                <div class="ins-options">
                    <label class="ins-option">
                        <input type="radio" name="storageType" :value.attr="'local'" :checked.attr="''">
                        <span class="ins-option-body"><span class="ins-option-icon"><svg><use href="#hdd"></use></svg></span><span><b>本机存储</b><small>存在服务器的数据目录里，由本服务直接提供下载</small></span></span>
                    </label>
                    <label class="ins-option">
                        <input type="radio" name="storageType" :value.attr="'s3'">
                        <span class="ins-option-body"><span class="ins-option-icon"><svg><use href="#cloud"></use></svg></span><span><b>对象存储</b><small>阿里云 OSS、腾讯云 COS、七牛、MinIO、AWS S3、Cloudflare R2 等 S3 兼容服务</small></span></span>
                    </label>
                </div>
                <div class="ins-grid" data-when="s3" hidden>
                    <label class="ins-field span-2"><span>服务地址（Endpoint）</span><input name="s3Endpoint" placeholder="https://oss-cn-hangzhou.aliyuncs.com" spellcheck="false"></label>
                    <label class="ins-field"><span>存储桶</span><input name="s3Bucket" placeholder="my-ota" spellcheck="false"></label>
                    <label class="ins-field"><span>区域</span><input name="s3Region" placeholder="us-east-1" spellcheck="false"></label>
                    <label class="ins-field"><span>Access Key</span><input name="s3Access" placeholder="SecretId / AccessKey ID" spellcheck="false" autocomplete="off"></label>
                    <label class="ins-field"><span>Secret Key</span><input name="s3Secret" type="password" autocomplete="new-password" placeholder="SecretKey / AccessKey Secret" data-keep-hint="已保存，不改就留空"></label>
                    <label class="ins-field"><span>对象名前缀</span><input name="s3Prefix" placeholder="ota/" :value.attr="'ota/'" spellcheck="false"></label>
                    <label class="ins-field"><span>下载地址（CDN，可不填）</span><input name="s3Public" placeholder="https://cdn.example.com" spellcheck="false"></label>
                    <label class="ins-check span-2"><input type="checkbox" name="s3PathStyle"><span>路径风格访问（MinIO 一般要勾选，OSS、COS 不勾）</span></label>
                </div>
                <details class="ins-advanced">
                    <summary><svg><use href="#sliders"></use></svg>高级设置<small>时区</small></summary>
                    <div class="ins-grid">
                        <label class="ins-field span-2"><span>后台显示时间用的时区</span>
                            <select name="zone">
                                <option :value.attr="'Asia/Shanghai'">中国（Asia/Shanghai）</option>
                                <option :value.attr="'Asia/Hong_Kong'">香港（Asia/Hong_Kong）</option>
                                <option :value.attr="'Asia/Taipei'">台北（Asia/Taipei）</option>
                                <option :value.attr="'Asia/Singapore'">新加坡（Asia/Singapore）</option>
                                <option :value.attr="'Asia/Tokyo'">东京（Asia/Tokyo）</option>
                                <option :value.attr="'Europe/London'">伦敦（Europe/London）</option>
                                <option :value.attr="'America/Los_Angeles'">洛杉矶（America/Los_Angeles）</option>
                                <option :value.attr="'UTC'">UTC</option>
                            </select>
                        </label>
                    </div>
                </details>
            </section>

            <section class="ins-panel" data-panel="4" hidden>
                <header class="ins-head">
                    <span class="ins-kicker">第 5 步</span>
                    <h1>超级管理员</h1>
                    <p data-admin-lead>用它登录后台，管理所有 App 和系统配置。同事的账号进后台再建。</p>
                </header>
                <div class="ins-grid">
                    <label class="ins-field"><span>登录账号</span><input name="adminUser" placeholder="admin" :value.attr="'admin'" spellcheck="false" autocomplete="username"></label>
                    <label class="ins-field"><span>显示名字</span><input name="adminName" placeholder="比如 张三" autocomplete="off"></label>
                    <label class="ins-field"><span>密码</span><input name="adminPass" type="password" autocomplete="new-password" placeholder="至少 8 位"><i class="ins-meter" data-meter><b></b></i><small data-meter-text>至少 8 位，建议字母、数字、符号混合</small></label>
                    <label class="ins-field"><span>再输一次</span><input name="adminPass2" type="password" autocomplete="new-password"></label>
                </div>
            </section>

            <section class="ins-panel" data-panel="5" hidden>
                <header class="ins-head">
                    <span class="ins-kicker">最后一步</span>
                    <h1>确认安装</h1>
                    <p>确认一下，安装会重启服务，大约十几秒。</p>
                </header>
                <dl class="ins-summary" data-summary></dl>
            </section>

            <div class="ins-error" data-error role="alert" hidden><svg><use href="#alert"></use></svg><span></span></div>
        </form>
        <footer class="ins-foot">
            <span class="ins-count" data-count>第 1 步，共 6 步</span>
            <button class="ins-btn ghost" type="button" data-prev hidden>上一步</button>
            <button class="ins-btn primary" type="button" data-next>开始<svg><use href="#arrow"></use></svg></button>
        </footer>
    </main>
</div>

<div class="ins-run" data-run hidden>
    <div class="ins-run-card">
        <div class="ins-run-icon"><svg class="ins-logo"><use href="#logo"></use></svg><span class="ring"></span></div>
        <h2 data-run-title>正在安装</h2>
        <p data-run-text>请不要关闭页面</p>
        <ol class="ins-run-steps">
            <li data-run-step="save"><i></i>保存配置</li>
            <li data-run-step="restart"><i></i>重启服务</li>
            <li data-run-step="ready"><i></i>等待服务就绪</li>
        </ol>
        <a class="ins-btn primary wide" href="/login" data-run-done hidden>进入后台<svg><use href="#arrow"></use></svg></a>
    </div>
</div>

<svg width="0" height="0" style="position:absolute" aria-hidden="true">
    <defs><mask id="logo-cut"><rect width="24" height="24" fill="#fff"></rect><path d="M13.35 7.48A5.2 5.2 0 1 0 17.18 12.95" fill="none" stroke="#000" stroke-width="2.2" stroke-linecap="round"></path><circle cx="16.6" cy="9.3" r="1.6" fill="#000"></circle></mask></defs>
    <symbol id="logo" viewBox="0 0 24 24"><rect x="1" y="1" width="22" height="22" rx="7" fill="currentColor" mask="url(#logo-cut)"></rect></symbol>
    <symbol id="tick" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"></polyline></symbol>
    <symbol id="arrow" viewBox="0 0 24 24"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></symbol>
    <symbol id="plus" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></symbol>
    <symbol id="x" viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></symbol>
    <symbol id="alert" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></symbol>
    <symbol id="db" viewBox="0 0 24 24"><ellipse cx="12" cy="5" rx="9" ry="3"></ellipse><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"></path><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"></path></symbol>
    <symbol id="box" viewBox="0 0 24 24"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path><polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline><line x1="12" y1="22.08" x2="12" y2="12"></line></symbol>
    <symbol id="globe" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></symbol>
    <symbol id="shield" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path><path d="m9 12 2 2 4-4"></path></symbol>
    <symbol id="plug" viewBox="0 0 24 24"><path d="M9 2v6M15 2v6M6 8h12v4a6 6 0 0 1-12 0V8zM12 18v4"></path></symbol>
    <symbol id="hdd" viewBox="0 0 24 24"><line x1="22" y1="12" x2="2" y2="12"></line><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"></path><line x1="6" y1="16" x2="6.01" y2="16"></line><line x1="10" y1="16" x2="10.01" y2="16"></line></symbol>
    <symbol id="cloud" viewBox="0 0 24 24"><path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"></path></symbol>
    <symbol id="sliders" viewBox="0 0 24 24"><line x1="4" y1="21" x2="4" y2="14"></line><line x1="4" y1="10" x2="4" y2="3"></line><line x1="12" y1="21" x2="12" y2="12"></line><line x1="12" y1="8" x2="12" y2="3"></line><line x1="20" y1="21" x2="20" y2="16"></line><line x1="20" y1="12" x2="20" y2="3"></line><line x1="1" y1="14" x2="7" y2="14"></line><line x1="9" y1="8" x2="15" y2="8"></line><line x1="17" y1="16" x2="23" y2="16"></line></symbol>
</svg>

</template>

<script>
export const meta = {
    title: (m, c, $h) => ($h.t(m.reinstall) ? '重新安装 · 管理后台' : '安装 · 管理后台'),
    css: ["pub/install.css"],
    js: ["pub/install.js"],
    theme: null
};
</script>

<script setup>
defineProps({ m: Object, c: Object });
</script>
